package com.cardeck.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cardeck.app.data.DEFAULT_VEHICLES
import com.cardeck.app.data.Live
import com.cardeck.app.data.Prefs
import com.cardeck.app.data.Vehicle
import com.cardeck.app.ui.Palette
import com.cardeck.app.ui.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface Screen {
    data object Welcome : Screen
    data object Pair : Screen
    data object AddVehicle : Screen
    data object Home : Screen
    data object Dash : Screen
    data object Diag : Screen
    data class Dtc(val code: String) : Screen
    data object Trips : Screen
    data class Trip(val id: Int) : Screen
    data object Settings : Screen
}

enum class ScanPhase { Idle, Scanning, Results, Clean }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    // ---- Navigation ----
    var onboarded by mutableStateOf(prefs.onboarded)
        private set
    val stack = mutableStateListOf<Screen>(if (prefs.onboarded) Screen.Home else Screen.Welcome)
    val screen: Screen get() = stack.last()
    var hud by mutableStateOf(false)

    fun go(s: Screen) { stack.add(s) }
    fun goDash(withHud: Boolean = false) { stack.add(Screen.Dash); hud = withHud }

    /** @return true si le retour a été consommé. */
    fun back(): Boolean {
        if (hud) { hud = false; return true }
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        return false
    }

    fun finishOnboarding() {
        prefs.onboarded = true
        onboarded = true
        stack.clear()
        stack.add(Screen.Home)
    }

    fun resetToWelcome() {
        prefs.onboarded = false
        onboarded = false
        stack.clear()
        stack.add(Screen.Welcome)
    }

    // ---- Apparence ----
    var themeMode by mutableStateOf(prefs.themeMode)
        private set
    var palette by mutableStateOf(prefs.palette)
        private set

    fun updateThemeMode(m: ThemeMode) { themeMode = m; prefs.themeMode = m }
    fun updatePalette(p: Palette) { palette = p; prefs.palette = p }

    // ---- Garage ----
    var vehicles by mutableStateOf(prefs.vehicles)
        private set
    var vehicleId by mutableStateOf(prefs.vehicleId)
        private set
    val vehicle: Vehicle get() = vehicles.find { it.id == vehicleId } ?: vehicles.firstOrNull() ?: DEFAULT_VEHICLES[0]

    fun selectVehicle(id: String) { vehicleId = id; prefs.vehicleId = id }

    fun addVehicle(name: String, plate: String, year: String, fuel: String) {
        val v = Vehicle("v" + System.currentTimeMillis(), name, plate, year, fuel)
        vehicles = vehicles + v
        prefs.vehicles = vehicles
        selectVehicle(v.id)
    }

    // ---- Boîtier OBD2 ----
    var deviceName by mutableStateOf(prefs.deviceName)
        private set

    fun setDevice(name: String) { deviceName = name; prefs.deviceName = name }

    var autoStart by mutableStateOf(prefs.autoStart)
        private set
    var autoStop by mutableStateOf(prefs.autoStop)
        private set
    var blackbox by mutableStateOf(prefs.blackbox)
        private set

    fun toggleAutoStart() { autoStart = !autoStart; prefs.autoStart = autoStart }
    fun toggleAutoStop() { autoStop = !autoStop; prefs.autoStop = autoStop }
    fun toggleBlackbox() { blackbox = !blackbox; prefs.blackbox = blackbox }

    // ---- Tableau de bord ----
    var widgets by mutableStateOf(prefs.widgets)
        private set

    fun toggleWidget(id: String) {
        widgets = if (id in widgets) widgets - id else widgets + id
        prefs.widgets = widgets
    }

    var mirror by mutableStateOf(prefs.hudMirror)
        private set

    fun toggleMirror() { mirror = !mirror; prefs.hudMirror = mirror }

    var live by mutableStateOf(Live())
        private set
    private var targetSpeed = 55.0

    /** Une itération de la simulation de données moteur (≈ 1 Hz tant que le tableau de bord est visible). */
    fun tick() {
        val l = live
        if (Random.nextDouble() < .22) targetSpeed = if (Random.nextDouble() < .12) 0.0 else 25 + Random.nextDouble() * 95
        val speed = (l.speed + (targetSpeed - l.speed) * .35 + (Random.nextDouble() - .5) * 5).coerceIn(0.0, 210.0)
        val acc = targetSpeed - speed
        val rpm = (850 + speed * 26 + acc * 18 + (Random.nextDouble() - .5) * 200).coerceIn(760.0, 6800.0)
        val load = (18 + acc * 1.4 + speed * .25 + Random.nextDouble() * 6).coerceIn(8.0, 98.0)
        live = l.copy(
            speed = speed,
            rpm = rpm,
            temp = l.temp + (91 - l.temp) * .15 + (Random.nextDouble() - .5) * 1.2,
            fuel = maxOf(4.0, l.fuel - .04),
            volt = 14.05 + Random.nextDouble() * .35,
            load = load,
            boost = maxOf(0.0, (load - 25) / 60 * 1.3),
            iat = 27 + Random.nextDouble() * 3,
            maf = rpm / 1000 * load / 100 * 18 + 2,
            conso = if (speed < 3) 0.0 else maxOf(1.2, 3.2 + load / 9 + (Random.nextDouble() - .5)),
        )
    }

    var refreshing by mutableStateOf(false)
        private set

    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            delay(1500)
            refreshing = false
            showSnack("Données actualisées")
        }
    }

    // ---- Diagnostic ----
    var phase by mutableStateOf(ScanPhase.Idle)
        private set
    var scanP by mutableStateOf(0f)
        private set
    var filter by mutableStateOf("all")
    var query by mutableStateOf("")
    var cleared by mutableStateOf(false)
        private set
    private var scanJob: Job? = null

    fun startScan() {
        scanJob?.cancel()
        query = ""
        phase = ScanPhase.Scanning
        scanP = 0f
        scanJob = viewModelScope.launch {
            while (scanP < 100f) {
                delay(80)
                scanP += 2.5f
            }
            filter = "all"
            phase = if (cleared) ScanPhase.Clean else ScanPhase.Results
        }
    }

    fun clearFaults() {
        phase = ScanPhase.Clean
        cleared = true
        showSnack("6 codes défaut effacés")
    }

    // ---- Trajets ----
    var tripFilter by mutableStateOf("all")
        private set
    var tripsLoading by mutableStateOf(false)
        private set
    private var tripJob: Job? = null

    fun loadTrips() {
        tripJob?.cancel()
        tripsLoading = true
        tripJob = viewModelScope.launch { delay(850); tripsLoading = false }
    }

    fun updateTripFilter(f: String) { tripFilter = f; loadTrips() }

    // ---- Snackbar ----
    var snack by mutableStateOf<String?>(null)
        private set
    private var snackJob: Job? = null

    fun showSnack(msg: String) {
        snack = msg
        snackJob?.cancel()
        snackJob = viewModelScope.launch { delay(3000); snack = null }
    }
}
