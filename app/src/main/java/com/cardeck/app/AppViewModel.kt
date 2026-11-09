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
import com.cardeck.app.explorer.ExplorerUi
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
    data object Changelog : Screen
    data object About : Screen
    data object Explorer : Screen
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
        if (stack.size > 1) {
            // On quitte la création d'un profil : la lecture du VIN n'a plus d'objet.
            if (stack.last() == Screen.AddVehicle) clearVin()
            stack.removeAt(stack.lastIndex)
            return true
        }
        return false
    }

    // ---- Accueil ----
    /** Message d'accueil (un nouveau à chaque lancement). */
    var greeting by mutableStateOf("")
        private set

    fun newGreeting() {
        val all = com.cardeck.app.data.Greetings.all(ctx)
        val i = com.cardeck.app.data.Greetings.pick(all.size, prefs.lastGreeting)
        prefs.lastGreeting = i
        greeting = all[i]
    }

    // ---- Mises à jour (releases GitHub) ----
    /** Releases publiées (null = pas encore vérifié). */
    var releases by mutableStateOf<List<com.cardeck.app.data.Release>?>(null)
        private set
    var updateChecking by mutableStateOf(false)
        private set
    var updateError by mutableStateOf<String?>(null)
        private set
    /** Avancement du téléchargement (null = aucun en cours). */
    var updateProgress by mutableStateOf<Float?>(null)
        private set
    /** Proposition affichée au lancement (masquée par « Plus tard » jusqu'au prochain lancement). */
    var updatePrompt by mutableStateOf(false)

    /** Version plus récente que celle installée, avec un APK à télécharger. */
    val update: com.cardeck.app.data.Release?
        get() = releases?.firstOrNull { it.apkUrl != null && com.cardeck.app.data.Updater.compare(it.version, BuildConfig.VERSION_NAME) > 0 }

    fun checkUpdate(prompt: Boolean = false) {
        if (updateChecking) return
        updateChecking = true
        updateError = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { com.cardeck.app.data.Updater.releases() } }
            r.onSuccess { releases = it }.onFailure { updateError = it.message ?: "Vérification impossible" }
            updateChecking = false
            if (prompt && update != null) updatePrompt = true
        }
    }

    fun installUpdate() {
        val u = update ?: return
        if (updateProgress != null) return
        updatePrompt = false
        updateProgress = 0f
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching { com.cardeck.app.data.Updater.download(ctx, u) { p -> viewModelScope.launch { updateProgress = p } } }
            }
            updateProgress = null
            f.onSuccess { runCatching { com.cardeck.app.data.Updater.install(ctx, it) }.onFailure { e -> showSnack("Installation impossible : ${e.message}") } }
                .onFailure { showSnack(it.message ?: "Téléchargement impossible") }
        }
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
        // Boîtier déjà utilisé pour lire le VIN : inutile de l'appairer une deuxième fois.
        val adapter = vinAdapter
        if (adapter != null) {
            val vin = vinResult?.vin
            clearVin()
            finishPairing(adapter, vin)
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

    // ---- Reconnaissance du véhicule par son VIN ----
    /** L'écran d'appairage sert à lire le VIN (avant la création du profil). */
    var vinPairing by mutableStateOf(false)
        private set

    /** Boîtier utilisé pour lire le VIN : il sera associé au nouveau profil. */
    private var vinAdapter: AdapterConfig? = null

    /** Résultat de la lecture : null tant qu'elle n'a pas eu lieu. */
    var vinResult by mutableStateOf<com.cardeck.app.data.VinInfo?>(null)
        private set

    /** Lecture faite mais aucun VIN exploitable (contact coupé, VIN absent ou illisible). */
    var vinFailed by mutableStateOf(false)
        private set

    var vinBusy by mutableStateOf(false)
        private set

    /** Profil déjà au garage portant ce VIN, s'il existe. */
    val vinExisting: com.cardeck.app.data.VehicleProfile?
        get() = vinResult?.let { r -> repo.vehicles.value.firstOrNull { it.vin.equals(r.vin, ignoreCase = true) } }

    /**
     * « Je ne connais pas le modèle de ma voiture » : lit le VIN avec le boîtier. Si un boîtier est déjà connecté on
     * l'utilise tout de suite, sinon on ouvre l'appairage (le profil n'existe pas encore).
     */
    fun detectVin() {
        if (vinBusy) return
        val connected = (obd.state.value as? com.cardeck.app.obd.ObdState.Connected)?.adapter
        if (connected == null) {
            vinPairing = true
            pairVehicleId = null
            go(Screen.Pair)
            return
        }
        vinBusy = true
        viewModelScope.launch {
            val vin = withContext(Dispatchers.IO) { obd.use { e -> if (e.probe()) e.vin() else null } }
            vinAdapter = connected
            applyVin(vin)
            vinBusy = false
        }
    }

    private fun applyVin(raw: String?) {
        val info = raw?.let { com.cardeck.app.data.Vin.decode(it) }
        vinResult = info
        vinFailed = info == null
    }

    /** Fin de l'appairage en mode VIN : retour à la liste des véhicules avec le résultat. */
    fun finishVinPairing(adapter: AdapterConfig?, vin: String?) {
        obd.pairing = false
        vinPairing = false
        vinAdapter = adapter
        applyVin(vin)
        stack.removeAll { it == Screen.Pair }
    }

    fun cancelVinPairing() {
        obd.pairing = false
        vinPairing = false
        back()
    }

    fun clearVin() {
        vinResult = null; vinFailed = false; vinAdapter = null; vinBusy = false
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

    /** Niveau du diagnostic : complet (tous les calculateurs) ou simple (codes OBD classiques, quelques secondes). */
    var diagFull by mutableStateOf(prefs.diagFull)
        private set

    fun updateDiagFull(v: Boolean) { diagFull = v; prefs.diagFull = v }

    private val stepsSimple = listOf("Préparation du boîtier", "Codes mémorisés", "Codes en attente", "Codes permanents", "Calculateur moteur", "Données figées")
    private val stepsFull = stepsSimple + listOf("Recherche des calculateurs", "Codes de chaque calculateur")

    /** Étapes du scan en cours. */
    var scanSteps by mutableStateOf(stepsFull)
        private set

    /** Calculateurs dont les codes ont été lus en OBD classique au dernier scan (effacés par le service 04). */
    private var obdEcuNames: Set<String> = emptySet()

    /** Détail de l'étape en cours (adresse balayée, calculateur interrogé). */
    var scanDetail by mutableStateOf<String?>(null)
        private set

    /** Résultat d'un scan ; [noAnswer] = boîtier joignable mais aucun calculateur ne répond (contact coupé). */
    private class ScanOut(val ecus: Int, val codes: List<FoundCode>, val ff: Pair<String, List<String>>?, val noAnswer: Boolean = false)

    /** Journal du dernier diagnostic (tous les échanges avec le boîtier), à partager en cas de problème ; null s'il n'existe pas. */
    fun scanLog(): java.io.File? = java.io.File(java.io.File(ctx.cacheDir, "exports"), "CarDeck-diagnostic.txt").takeIf { it.exists() }

    fun startScan() {
        if (scanning) return
        if (obd.exploring) { showSnack("Exploration en cours : arrêtez-la avant de lancer un diagnostic"); return }
        val v = repo.active ?: return
        val full = diagFull
        scanning = true
        scanStep = 0
        scanSteps = if (full) stepsFull else stepsSimple
        query = ""
        viewModelScope.launch {
            val log = StringBuilder()
            val out = withContext(Dispatchers.IO) {
                obd.use { e ->
                    val date = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.FRANCE).format(java.util.Date())
                    log.append("CarDeck ${BuildConfig.VERSION_NAME} · diagnostic ${if (full) "complet" else "simple"} du $date\nVéhicule : ${v.nickname} (${v.template.fullName})\n\n")
                    e.trace = log
                    try {
                        // Boîtier remis à zéro : aucun réglage laissé par la lecture des données (filtres, en-têtes…) ne gêne le scan.
                        if (!e.reset()) {
                            e.note("Aucune réponse à 0100 : contact coupé ou boîtier mal branché")
                            return@use ScanOut(0, emptyList(), null, noAnswer = true)
                        }
                        e.note("Boîtier ${e.version} · protocole ${e.protocolName} (${e.protocolNum})")
                        scanStep = 1
                        val (stored, ecus) = e.readDtcs("03")
                        scanStep = 2
                        val (pending, _) = e.readDtcs("07")
                        scanStep = 3
                        val (permanent, _) = e.readDtcs("0A")
                        // Calculateur moteur interrogé directement aussi : certains ne répondent pas aux demandes diffusées.
                        scanStep = 4
                        val engine = if (e.is11bit) e.readDtcs("03", 0x7E0, 0x7E8).first + e.readDtcs("07", 0x7E0, 0x7E8).first else emptyList()
                        scanStep = 5
                        val ff = runCatching { e.freezeFrame() }.getOrNull()
                        val obdCodes = (stored + pending + permanent + engine).distinctBy { it.code to it.ecu }
                        obdEcuNames = obdCodes.map { it.ecu }.toSet()
                        if (!full) {
                            // Diagnostic simple : codes OBD classiques seulement.
                            scanEcus = emptyList()
                            e.note("Résultat : ${obdCodes.size} code(s) ${obdCodes.joinToString { "${it.code} (${it.ecu}, ${it.status.label})" }}")
                            return@use ScanOut(ecus.size.coerceAtLeast(1), obdCodes, ff)
                        }
                        // Tous les calculateurs, un par un (environ 1 à 2 minutes) : y compris ceux hors OBD2 (ABS…).
                        scanStep = 6
                        val found = e.findEcus { scanDetail = "Adresse %03X".format(it) }.toMutableList()
                        if (e.is11bit && found.none { it.first == 0x7E0 }) found.add(0, 0x7E0 to 0x7E8)
                        scanStep = 7
                        var answered = 0
                        // Calculateurs connus du modèle (relevés par l'explorateur), y compris en CAN 29 bits ou hors du protocole OBD.
                        val known = v.template.ecus
                        val targets = found.map { (tx, rx) -> com.cardeck.app.data.KnownEcu(tx, rx, name = known.firstOrNull { k -> !k.wide && k.tx == tx }?.name) } +
                            known.filter { k -> found.none { it.first == k.tx && !k.wide } }
                        val withCodes = mutableListOf<com.cardeck.app.data.KnownEcu>()
                        val more = targets.flatMap { k ->
                            val name = k.name ?: if (k.wide) k.label else com.cardeck.app.obd.Elm327.ecuLabel(k.tx, k.rx)
                            scanDetail = name
                            val c = e.ecuDtcs(k.tx, k.rx, name, k.wide)
                            if (c != null) answered++
                            if (!c.isNullOrEmpty()) withCodes += k.copy(name = name)
                            c.orEmpty()
                        }
                        scanEcus = withCodes
                        scanDetail = null
                        val all = (stored + pending + permanent + engine + more).distinctBy { it.code to it.ecu }
                        e.note("Résultat : ${all.size} code(s) ${all.joinToString { "${it.code} (${it.ecu}, ${it.status.label})" }}")
                        ScanOut(maxOf(ecus.size, found.size, answered).coerceAtLeast(1), all, ff)
                    } finally {
                        e.trace = null
                    }
                }
            }
            withContext(Dispatchers.IO) {
                runCatching { java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }.resolve("CarDeck-diagnostic.txt").writeText(log.toString()) }
            }
            if (out == null) {
                showSnack("Boîtier non connecté ou liaison perdue pendant le scan : reconnectez-le et relancez")
            } else if (out.noAnswer) {
                showSnack("Aucun calculateur ne répond : branchez le boîtier et mettez le contact")
            } else {
                withContext(Dispatchers.IO) { repo.saveScan(v.id, System.currentTimeMillis(), out.ecus, out.codes, out.ff?.first, out.ff?.second) }
                filter = "all"
            }
            scanDetail = null
            scanning = false
        }
    }

    // ---- Explorateur (balayage à l'arrêt / enregistrement en roulant) ----
    val explorer = ExplorerUi()

    private var explorerJob: Job? = null

    /** Dossier des découvertes d'un véhicule. */
    fun explorerDir(vehicleId: Long) = java.io.File(ctx.filesDir, "explorateur/$vehicleId")

    /**
     * Lance l'exploration du véhicule actif : enregistrement en roulant si la voiture roule à cet instant,
     * balayage à l'arrêt sinon. Tout en lecture seule, quel que soit le modèle.
     */
    fun startExplore() {
        if (explorer.running) return
        val v = repo.active ?: return
        val dir = explorerDir(v.id)
        explorer.reset()
        explorer.running = true
        explorerJob = viewModelScope.launch(Dispatchers.IO) {
            obd.exploring = true
            try {
                val r = obd.use { e ->
                    val wasReadOnly = e.readOnly
                    e.readOnly = true
                    val data = com.cardeck.app.explorer.ExplorerData.load(java.io.File(dir, "decouvertes.json"))
                    val ex = com.cardeck.app.explorer.Explorer(e, data, dir, explorer)
                    try {
                        explorer.step(0, 1, "Préparation du boîtier")
                        if (!ex.prepare()) return@use "Aucun calculateur ne répond : mettez le contact (moteur tournant de préférence)."
                        val speed = ex.speed()
                        val name = "${v.nickname} (${v.template.fullName})"
                        if (speed != null && speed >= 3) {
                            explorer.mode = "Enregistrement en roulant"
                            explorer.line("🚗 La voiture roule (${speed.toInt()} km/h) : enregistrement en roulant")
                            ex.drive(BuildConfig.VERSION_NAME, name)
                        } else {
                            explorer.mode = "Balayage à l'arrêt"
                            explorer.line("🅿️ La voiture est à l'arrêt : balayage complet")
                            ex.stationary(BuildConfig.VERSION_NAME, name)
                        }
                        null
                    } finally {
                        withContext(kotlinx.coroutines.NonCancellable) {
                            e.trace = null
                            e.readOnly = wasReadOnly
                            runCatching { e.reset() }
                        }
                    }
                }
                explorer.done = r ?: if (obd.state.value is com.cardeck.app.obd.ObdState.Connected) "Exploration terminée" else "Liaison avec le boîtier perdue : reconnectez-le et relancez, l'exploration reprendra où elle s'est arrêtée."
            } catch (c: kotlinx.coroutines.CancellationException) {
                explorer.done = "Exploration arrêtée : relancez pour reprendre où elle s'est arrêtée."
                throw c
            } finally {
                obd.exploring = false
                explorer.running = false
                explorer.refresh++
            }
        }
    }

    fun stopExplore() { explorerJob?.cancel() }

    /** Résultats de l'exploration à partager (dossier zippé, avec le rapport). */
    fun explorerZip(vehicleId: Long): java.io.File? {
        val dir = explorerDir(vehicleId)
        if (!dir.exists()) return null
        val v = repo.vehicles.value.find { it.id == vehicleId }
        val data = com.cardeck.app.explorer.ExplorerData.load(java.io.File(dir, "decouvertes.json"))
        runCatching { java.io.File(dir, "RAPPORT.md").writeText(data.report(v?.let { "${it.nickname} (${it.template.fullName})" } ?: "Véhicule", BuildConfig.VERSION_NAME)) }
        val out = java.io.File(java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }, "CarDeck_exploration_${v?.nickname?.replace(Regex("[^A-Za-z0-9]+"), "_") ?: vehicleId}.zip")
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(out)).use { zip ->
            dir.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
                zip.putNextEntry(java.util.zip.ZipEntry(f.relativeTo(dir).path))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out
    }

    fun clearExplorer(vehicleId: Long) {
        explorerDir(vehicleId).deleteRecursively()
        explorer.reset()
        explorer.refresh++
    }

    /** Calculateurs qui avaient des codes au dernier scan (ils seront effacés un par un). */
    private var scanEcus: List<com.cardeck.app.data.KnownEcu> = emptyList()

    var clearing by mutableStateOf(false)
        private set

    /**
     * Efface les codes défaut. Simple : codes OBD classiques (service 04 : moteur, voyant, données figées).
     * Complet : en plus, chaque calculateur qui avait des codes au dernier scan (UDS 14 / KWP 14) ; à défaut de scan
     * complet, les calculateurs connus du modèle. La liste de l'appli est mise à jour aussitôt : les codes des calculateurs
     * qui ont confirmé l'effacement disparaissent (les codes « permanents » OBD ne s'effacent pas par ce moyen :
     * ils disparaissent seuls après quelques trajets sans défaut).
     */
    fun clearFaults(full: Boolean) {
        if (clearing || scanning) return
        val v = repo.active ?: return
        val targets = if (!full) emptyList() else scanEcus.ifEmpty { v.template.ecus }
        clearing = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                obd.use { e ->
                    val obdOk = e.clearDtcs()
                    obdOk to targets.map { it.label to e.clearEcu(it.tx, it.rx, it.wide) }
                }
            }
            clearing = false
            if (r == null) { showSnack("Boîtier non connecté ou liaison perdue : reconnectez-le et réessayez"); return@launch }
            val (obdOk, per) = r
            val done = per.filter { it.second.result == com.cardeck.app.obd.ClearResult.Cleared }.map { it.first }
            val refused = per.filter { it.second.result == com.cardeck.app.obd.ClearResult.Refused }
            val cleared = (if (obdOk) listOf("moteur (OBD)") else emptyList()) + done
            // Liste de l'appli : on retire ce qui a été effacé.
            if (cleared.isNotEmpty()) withContext(Dispatchers.IO) {
                val last = repo.lastScan(v.id)
                if (last != null) {
                    val left = com.cardeck.app.data.ClearedCodes.remaining(last.codes, obdEcuNames, obdOk, done.toSet())
                    repo.saveScan(v.id, System.currentTimeMillis(), last.ecus, left, if (obdOk) null else last.ffCode, if (obdOk) null else last.ff)
                }
            }
            val parts = mutableListOf<String>()
            if (cleared.isNotEmpty()) parts += "Codes effacés : " + cleared.joinToString(", ")
            if (refused.isNotEmpty()) parts += "Refusé par " + refused.joinToString(", ") { (n, o) -> n + (o.nrc?.let { " (" + com.cardeck.app.data.nrcLabel(it).lowercase() + ")" } ?: "") }
            showSnack(
                if (cleared.isEmpty()) (if (parts.isEmpty()) "Effacement refusé : mettez le contact, moteur arrêté" else parts.joinToString(" · ") + " · contact mis, moteur arrêté")
                else parts.joinToString(" · "),
            )
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

    // ---- Sauvegarde ----
    /** Opération en cours (« Sauvegarde… », « Restauration… »), null sinon. */
    var backupBusy by mutableStateOf<String?>(null)
        private set

    /** Nom proposé pour le fichier de sauvegarde. */
    fun backupName(): String = "CarDeck-" + java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.FRANCE).format(java.util.Date()) + ".cdbak"

    fun backupTo(uri: android.net.Uri) {
        if (backupBusy != null) return
        backupBusy = "Sauvegarde en cours…"
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: error("Fichier inaccessible")
                    com.cardeck.app.data.Backup.write(ctx, repo, out)
                }
            }
            backupBusy = null
            r.onSuccess { showSnack("Sauvegarde chiffrée enregistrée · ${it.vehicles} véhicule${if (it.vehicles > 1) "s" else ""}, ${it.trips} trajet${if (it.trips > 1) "s" else ""}") }
                .onFailure {
                    runCatching { android.provider.DocumentsContract.deleteDocument(ctx.contentResolver, uri) }
                    showSnack("Sauvegarde impossible : ${it.message}")
                }
        }
    }

    fun restoreFrom(uri: android.net.Uri) {
        if (backupBusy != null) return
        if (com.cardeck.app.service.TripService.recording) { showSnack("Trajet en cours : restaurez une fois le moteur arrêté"); return }
        backupBusy = "Restauration en cours…"
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val inp = ctx.contentResolver.openInputStream(uri) ?: error("Fichier inaccessible")
                    com.cardeck.app.data.Backup.read(ctx, repo, inp)
                }
            }
            backupBusy = null
            r.onSuccess { s ->
                themeMode = prefs.themeMode
                palette = prefs.palette
                homeOrder = prefs.homeOrder
                dashSport = prefs.dashMode == "sport"
                obd.supported.value = null
                if (repo.vehicles.value.isNotEmpty()) {
                    prefs.onboarded = true
                    onboarded = true
                    repo.active?.let { repo.select(it.id) }
                    stack.clear(); stack.add(Screen.Home); stack.add(Screen.Settings)
                } else {
                    prefs.onboarded = false
                    onboarded = false
                    stack.clear(); stack.add(Screen.Welcome)
                }
                showSnack("Sauvegarde restaurée · ${s.vehicles} véhicule${if (s.vehicles > 1) "s" else ""}, ${s.trips} trajet${if (s.trips > 1) "s" else ""}")
            }.onFailure {
                showSnack(if (it is com.cardeck.app.data.BackupCrypto.BadBackup) it.message ?: "Sauvegarde illisible" else "Restauration impossible : ${it.message}")
            }
        }
    }

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
