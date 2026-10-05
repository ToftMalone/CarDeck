package com.cardeck.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cardeck.app.data.AdapterConfig
import com.cardeck.app.data.FoundCode
import com.cardeck.app.data.Prefs
import com.cardeck.app.data.SWIFT_SPORT_ZC33S
import com.cardeck.app.service.TripService
import com.cardeck.app.ui.Palette
import com.cardeck.app.ui.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Welcome : Screen
    data object AddVehicle : Screen
    data object Pair : Screen
    data object Home : Screen
    data object Dash : Screen
    data object Diag : Screen
    data class Dtc(val code: String) : Screen
    data object Trips : Screen
    data class Trip(val id: Long) : Screen
    data object Settings : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val prefs = Prefs(app)
    val repo = app.cardeck.repo
    val obd = app.cardeck.obd

    // ---- Navigation ----
    var onboarded by mutableStateOf(prefs.onboarded && repo.vehicles.value.isNotEmpty())
        private set
    val stack = mutableStateListOf<Screen>(if (onboarded) Screen.Home else Screen.Welcome)
    val screen: Screen get() = stack.last()

    init {
        // Onboarding interrompu : on repart d'un garage vide.
        if (!onboarded) repo.vehicles.value.forEach { repo.deleteVehicle(it.id) }
    }

    fun go(s: Screen) { stack.add(s) }

    /** @return true si le retour a été consommé. */
    fun back(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        return false
    }

    // ---- Apparence ----
    var themeMode by mutableStateOf(prefs.themeMode)
        private set
    var palette by mutableStateOf(prefs.palette)
        private set

    fun updateThemeMode(m: ThemeMode) { themeMode = m; prefs.themeMode = m }
    fun updatePalette(p: Palette) { palette = p; prefs.palette = p }

    // ---- Profils de véhicule ----
    /** Véhicule en cours d'appairage (création de profil ou changement de boîtier). */
    var pairVehicleId by mutableStateOf<Long?>(null)
        private set

    fun createVehicle(nickname: String, plate: String, year: String) {
        val id = repo.addVehicle(SWIFT_SPORT_ZC33S.id, nickname, plate, year)
        repo.select(id)
        pairVehicleId = id
        // Hors onboarding, le retour depuis l'appairage ne doit pas recréer le profil.
        if (onboarded) stack.remove(Screen.AddVehicle)
        go(Screen.Pair)
    }

    fun pairActive() {
        pairVehicleId = repo.active?.id ?: return
        go(Screen.Pair)
    }

    /** Fin de l'appairage ; [adapter] null = « plus tard ». */
    fun finishPairing(adapter: AdapterConfig?, vin: String?) {
        val id = pairVehicleId
        if (id != null && adapter != null) repo.setAdapter(id, adapter, vin)
        obd.pairing = false
        if (!onboarded) {
            prefs.onboarded = true
            onboarded = true
            stack.clear()
            stack.add(Screen.Home)
        } else {
            stack.removeAll { it == Screen.Pair || it == Screen.AddVehicle }
            if (stack.isEmpty()) stack.add(Screen.Home)
        }
        TripService.start(ctx)
    }

    /** Retour depuis l'appairage pendant l'onboarding : le profil à peine créé est abandonné. */
    fun cancelOnboardingPair() {
        obd.pairing = false
        pairVehicleId?.let { repo.deleteVehicle(it) }
        pairVehicleId = null
        back()
    }

    fun deleteVehicle(id: Long) {
        repo.deleteVehicle(id)
        if (repo.vehicles.value.isEmpty()) {
            prefs.onboarded = false
            onboarded = false
            stack.clear()
            stack.add(Screen.Welcome)
        }
    }

    fun connectNow() {
        val a = repo.active?.adapter ?: return
        viewModelScope.launch(Dispatchers.IO) { obd.connect(a) }
        TripService.start(ctx)
    }

    // ---- Diagnostic ----
    var scanning by mutableStateOf(false)
        private set
    var scanStep by mutableIntStateOf(0)
        private set
    var filter by mutableStateOf("all")
    var query by mutableStateOf("")

    val scanSteps = listOf("Communication avec le véhicule", "Codes mémorisés", "Codes en attente", "Codes permanents", "Données figées")

    private class ScanOut(val ecus: Int, val codes: List<FoundCode>, val ff: Pair<String, List<String>>?)

    fun startScan() {
        if (scanning) return
        val v = repo.active ?: return
        scanning = true
        scanStep = 0
        query = ""
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                obd.use { e ->
                    if (!e.probe()) return@use null
                    scanStep = 1
                    val (stored, ecus) = e.readDtcs("03")
                    scanStep = 2
                    val (pending, _) = e.readDtcs("07")
                    scanStep = 3
                    val (permanent, _) = e.readDtcs("0A")
                    scanStep = 4
                    val ff = runCatching { e.freezeFrame() }.getOrNull()
                    ScanOut(ecus.size, (stored + pending + permanent).distinctBy { it.code }, ff)
                }
            }
            if (out == null) {
                showSnack("Aucun calculateur ne répond : branchez le boîtier et mettez le contact")
            } else {
                withContext(Dispatchers.IO) { repo.saveScan(v.id, System.currentTimeMillis(), out.ecus, out.codes, out.ff?.first, out.ff?.second) }
                filter = "all"
            }
            scanning = false
        }
    }

    fun clearFaults() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { obd.use { it.clearDtcs() } } == true
            if (ok) {
                showSnack("Codes défaut effacés")
                startScan()
            } else {
                showSnack("Effacement refusé : mettez le contact, moteur arrêté")
            }
        }
    }

    // ---- Trajets ----
    var tripFilter by mutableStateOf("all")

    // ---- Snackbar ----
    var snack by mutableStateOf<String?>(null)
        private set
    private var snackJob: Job? = null

    fun showSnack(msg: String) {
        snack = msg
        snackJob?.cancel()
        snackJob = viewModelScope.launch { delay(3500); snack = null }
    }
}
