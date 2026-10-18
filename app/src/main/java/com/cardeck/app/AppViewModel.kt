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
import com.cardeck.app.data.CodingBackup
import com.cardeck.app.data.VehicleTemplate
import com.cardeck.app.data.fr
import com.cardeck.app.obd.Elm327
import com.cardeck.app.obd.UdsError
import com.cardeck.app.data.FoundCode
import com.cardeck.app.data.Prefs
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
    data object Coding : Screen
    data object ObdData : Screen
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

    var homeOrder by mutableStateOf(prefs.homeOrder)
        private set

    fun updateHomeOrder(order: List<String>) { homeOrder = order; prefs.homeOrder = order }

    var dashSport by mutableStateOf(prefs.dashMode == "sport")
        private set

    fun updateDashSport(sport: Boolean) { dashSport = sport; prefs.dashMode = if (sport) "sport" else "standard" }

    fun updateThemeMode(m: ThemeMode) { themeMode = m; prefs.themeMode = m }
    fun updatePalette(p: Palette) { palette = p; prefs.palette = p }

    // ---- Profils de véhicule ----
    /** Véhicule en cours d'appairage (création de profil ou changement de boîtier). */
    var pairVehicleId by mutableStateOf<Long?>(null)
        private set

    fun createVehicle(templateId: String, nickname: String, plate: String, year: String) {
        val id = repo.addVehicle(templateId, nickname, plate, year)
        repo.select(id)
        pairVehicleId = id
        if (templateId == com.cardeck.app.data.SIMULATOR.id) {
            finishPairing(AdapterConfig(com.cardeck.app.data.Transport.Sim, "sim", "Boîtier OBD2 simulé"), "CARDECKSIM0000001")
            return
        }
        // Hors onboarding, le retour depuis l'appairage ne doit pas recréer le profil.
        if (onboarded) stack.remove(Screen.AddVehicle)
        go(Screen.Pair)
    }

    /** Nom proposé pour un nouveau profil : « Swift Sport », puis « Swift Sport 2 »… */
    fun defaultName(t: VehicleTemplate): String {
        val n = repo.vehicles.value.count { it.templateId == t.id }
        return if (n == 0) t.model else "${t.model} ${n + 1}"
    }

    /** Création d'un profil depuis Android Auto (nom par défaut, boîtier associé ensuite). */
    fun createVehicleQuick(templateId: String): Long {
        val t = com.cardeck.app.data.templateById(templateId)
        val id = repo.addVehicle(templateId, defaultName(t), "", "")
        repo.select(id)
        if (templateId == com.cardeck.app.data.SIMULATOR.id) {
            repo.setAdapter(id, AdapterConfig(com.cardeck.app.data.Transport.Sim, "sim", "Boîtier OBD2 simulé"), "CARDECKSIM0000001")
        }
        if (!onboarded) {
            prefs.onboarded = true
            onboarded = true
            stack.clear()
            stack.add(Screen.Home)
        }
        TripService.start(ctx)
        return id
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

    // ---- Codage ----
    /** Étape en cours (null = inactif). */
    var codingBusy by mutableStateOf<String?>(null)
        private set
    /** Données lues par bloc (calculateur + DID). */
    var codingData by mutableStateOf<Map<String, IntArray>>(emptyMap())
        private set
    var codingErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    /** Valeurs choisies mais pas encore écrites (id du réglage → valeur). */
    var codingPending by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    /** Compte rendu de la dernière écriture / restauration. */
    var codingReport by mutableStateOf<Pair<Boolean, String>?>(null)
        private set
    private var codingVehicleId: Long? = null

    fun codingFor(vehicleId: Long) {
        if (codingVehicleId == vehicleId) return
        codingVehicleId = vehicleId
        codingData = emptyMap(); codingErrors = emptyMap(); codingPending = emptyMap(); codingReport = null
    }

    fun choose(id: String, value: Int, current: Int?) {
        codingPending = if (value == current) codingPending - id else codingPending + (id to value)
    }

    fun readCoding() {
        val v = repo.active ?: return
        val defs = v.template.codings
        if (defs.isEmpty() || codingBusy != null) return
        codingBusy = "Lecture du codage…"
        codingReport = null
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                obd.use { e ->
                    val data = mutableMapOf<String, IntArray>()
                    val errors = mutableMapOf<String, String>()
                    for ((key, group) in defs.groupBy { it.blockKey }) {
                        val d = group.first()
                        try {
                            e.udsOpen(d.txHeader, d.rxHeader)
                            data[key] = e.readDid(d.did)
                        } catch (x: UdsError) {
                            errors[key] = x.message ?: "Erreur de lecture"
                        } finally {
                            e.udsClose()
                        }
                    }
                    data to errors
                }
            }
            if (out == null) showSnack("Boîtier OBD2 non connecté")
            else { codingData = out.first; codingErrors = out.second; codingPending = emptyMap() }
            codingBusy = null
        }
    }

    /** Écrit un bloc complet puis le relit ; renvoie un message d'erreur ou null si tout est conforme. */
    private suspend fun writeBlock(e: Elm327, tpl: VehicleTemplate, tx: String, rx: String?, did: Int, module: String, data: IntArray): String? {
        val level = tpl.codings.filter { it.blockKey == com.cardeck.app.data.blockKey(tx, did) }.mapNotNull { it.securityLevel }.maxOrNull()
        return try {
            e.udsOpen(tx, rx)
            e.extendedSession()
            if (level != null) e.securityAccess(level, tpl.securityKey)
            e.writeDid(did, data)
            if (e.readDid(did).contentEquals(data)) null else "$module : la relecture ne correspond pas à l'écriture"
        } catch (x: UdsError) {
            "$module : ${x.message}"
        } finally {
            e.defaultSession()
            e.udsClose()
        }
    }

    /** Contrôles avant toute écriture : moteur arrêté, batterie suffisamment chargée. */
    private suspend fun preWriteCheck(e: Elm327): String? {
        val rpm = e.rpm()
        if (rpm != null && rpm > 300) return "Coupez le moteur (en laissant le contact mis) avant de coder."
        val volt = e.voltage()
        if (volt != null && volt < 12.0) return "Tension batterie trop faible (${fr(volt, 1)} V) : branchez un chargeur avant de coder."
        return null
    }

    fun applyCoding() {
        val v = repo.active ?: return
        val tpl = v.template
        val changed = tpl.codings.filter { it.id in codingPending }
        if (changed.isEmpty() || codingBusy != null) return
        codingBusy = "Écriture du codage…"
        codingReport = null
        val pending = codingPending
        val before = codingData
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                obd.use { e ->
                    preWriteCheck(e)?.let { return@use Triple(listOf(it), emptyMap<String, IntArray>(), false) }
                    val errors = mutableListOf<String>()
                    val written = mutableMapOf<String, IntArray>()
                    for ((key, group) in changed.groupBy { it.blockKey }) {
                        val d = group.first()
                        val orig = before[key] ?: continue
                        var data = orig
                        group.forEach { data = it.applyTo(data, pending.getValue(it.id)) }
                        if (data.contentEquals(orig)) continue
                        repo.saveCodingBackupIfAbsent(CodingBackup(v.id, d.txHeader, d.rxHeader, d.did, d.module, orig, System.currentTimeMillis()))
                        val err = writeBlock(e, tpl, d.txHeader, d.rxHeader, d.did, d.module, data)
                        if (err == null) written[key] = data else errors += err
                    }
                    Triple(errors, written, true)
                }
            }
            when {
                out == null -> codingReport = false to "Liaison avec le boîtier perdue pendant le codage. Relisez le codage avant de réessayer."
                !out.third -> codingReport = false to out.first.first()
                else -> {
                    codingData = codingData + out.second
                    codingPending = codingPending.filterKeys { id -> tpl.codings.find { it.id == id }?.blockKey !in out.second.keys }
                    codingReport = if (out.first.isEmpty()) true to "Codage appliqué. Coupez puis remettez le contact pour le prendre en compte."
                    else false to out.first.joinToString("\n")
                }
            }
            codingBusy = null
        }
    }

    fun restoreCoding() {
        val v = repo.active ?: return
        val tpl = v.template
        if (codingBusy != null) return
        codingBusy = "Restauration du codage d'origine…"
        codingReport = null
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                val backups = repo.codingBackups(v.id)
                if (backups.isEmpty()) return@withContext emptyList<String>()
                obd.use { e ->
                    preWriteCheck(e)?.let { return@use listOf(it) }
                    backups.mapNotNull { b -> writeBlock(e, tpl, b.txHeader, b.rxHeader, b.did, b.module, b.data) }
                }
            }
            codingReport = when {
                out == null -> false to "Liaison avec le boîtier perdue pendant la restauration."
                out.isEmpty() -> true to "Codage d'origine restauré. Coupez puis remettez le contact."
                else -> false to out.joinToString("\n")
            }
            if (out != null && out.isEmpty()) {
                withContext(Dispatchers.IO) { repo.deleteCodingBackups(v.id) }
                codingData = emptyMap(); codingPending = emptyMap()
            }
            codingBusy = null
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
