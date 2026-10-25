package com.cardeck.app.obd

import android.content.Context
import com.cardeck.app.cardeck
import com.cardeck.app.data.AdapterConfig
import com.cardeck.app.data.PidDef
import com.cardeck.app.data.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

sealed interface ObdState {
    data object Idle : ObdState
    data class Connecting(val adapter: AdapterConfig) : ObdState
    data class Connected(val adapter: AdapterConfig, val version: String) : ObdState
    data class Failed(val adapter: AdapterConfig, val message: String) : ObdState
}

/** Connexion unique au boîtier OBD2 du véhicule actif ; toutes les commandes passent par [use]. */
class ObdManager(private val ctx: Context) {
    private val mutex = Mutex()
    private var transport: ObdTransport? = null
    private var elm: Elm327? = null

    private val _state = MutableStateFlow<ObdState>(ObdState.Idle)
    val state: StateFlow<ObdState> = _state

    /** Au moins un calculateur répond (contact mis / moteur tournant). */
    val ecuOnline = MutableStateFlow(false)
    val voltage = MutableStateFlow<Double?>(null)
    val protocol = MutableStateFlow<String?>(null)

    /** PIDs standard déclarés par le véhicule (null tant qu'on ne les a pas lus). */
    val supported = MutableStateFlow<Set<Int>?>(null)

    /** Dernières valeurs lues (id de donnée → valeur), partagées avec l'écran Android Auto. */
    val live = MutableStateFlow<Map<String, Double>>(emptyMap())

    /** Instant (ms) de la dernière mesure de chaque donnée de [live]. */
    private val liveAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Nombre d'écrans qui lisent déjà les données en continu (le suivi des trajets s'appuie alors sur leurs mesures). */
    val watchers = java.util.concurrent.atomic.AtomicInteger(0)

    fun publish(values: List<Pair<String, Double>>) {
        if (values.isEmpty()) return
        val now = System.currentTimeMillis()
        values.forEach { liveAt[it.first] = now }
        live.value = live.value + values
    }

    /** Mesures prises après l'instant [since] (ms) : chaque mesure n'est enregistrée qu'une fois. */
    fun measuredSince(since: Long): List<Pair<String, Double>> =
        live.value.entries.filter { (liveAt[it.key] ?: 0L) > since }.map { it.key to it.value }

    /**
     * Lit en continu les données renvoyées par [defs] (selon les PIDs déclarés par le véhicule) et les publie dans [live],
     * tant que l'appelant est actif (écran affiché). Les données lentes ne sont relues qu'un cycle sur 15.
     */
    suspend fun watch(
        slow: (PidDef) -> Boolean = { it.slow },
        withVolt: (Set<Int>) -> Boolean = { false },
        defs: (Set<Int>) -> List<PidDef>,
    ) {
        var cycle = 0
        watchers.incrementAndGet()
        try { while (true) {
            if (_state.value !is ObdState.Connected) { delay(1000); continue }
            var sup = supported.value
            if (sup == null) {
                sup = withContext(Dispatchers.IO) { use { it.supportedPids() } }
                if (sup.isNullOrEmpty()) { delay(5000); continue }
                supported.value = sup
            }
            val list = defs(sup).filter { !slow(it) || cycle % 15 == 0 }
            val readVolt = withVolt(sup) && cycle % 10 == 0
            val out = withContext(Dispatchers.IO) {
                use { e -> e.readAll(list) + listOfNotNull(if (readVolt) e.voltage()?.let { "elm_volt" to it } else null) }
            }
            if (out != null) publish(out)
            cycle++
            delay(80)
        } } finally { watchers.decrementAndGet() }
    }

    /** Pendant l'appairage, le service de trajets laisse la main à l'écran d'appairage. */
    @Volatile var pairing = false

    val connectedAddress: String? get() = (_state.value as? ObdState.Connected)?.adapter?.address

    suspend fun connect(a: AdapterConfig): Result<String> = mutex.withLock {
        closeLocked()
        _state.value = ObdState.Connecting(a)
        val t = when (a.transport) {
            Transport.Classic -> ClassicTransport(ctx, a.address)
            Transport.Ble -> BleTransport(ctx, a.address)
            Transport.Wifi -> {
                val host = a.address.substringBefore(':')
                val port = a.address.substringAfter(':', "35000").toIntOrNull() ?: 35000
                WifiTransport(ctx, host, port)
            }
        }
        try {
            t.open()
            val e = Elm327(t)
            // Réglages du modèle du véhicule actif : protocole connu, lecture seule.
            val tpl = runCatching { ctx.cardeck.repo.active?.template }.getOrNull()
            e.readOnly = tpl?.readOnly == true
            e.init(tpl?.elmProtocol, tpl?.elmInit.orEmpty())
            transport = t
            elm = e
            _state.value = ObdState.Connected(a, e.version)
            Result.success(e.version)
        } catch (c: CancellationException) {
            runCatching { t.close() }
            _state.value = ObdState.Idle
            throw c
        } catch (x: Exception) {
            runCatching { t.close() }
            _state.value = ObdState.Failed(a, x.message ?: "Connexion impossible")
            Result.failure(x)
        }
    }

    /** Exécute un échange avec le boîtier. Renvoie null si non connecté ou si la liaison est perdue. */
    suspend fun <T> use(block: suspend (Elm327) -> T): T? = mutex.withLock {
        val e = elm ?: return@withLock null
        try {
            block(e).also { protocol.value = e.protocolName.ifBlank { protocol.value } }
        } catch (c: CancellationException) {
            throw c
        } catch (x: Exception) {
            val a = (_state.value as? ObdState.Connected)?.adapter
            closeLocked()
            _state.value = if (a != null) ObdState.Failed(a, x.message ?: "Connexion perdue") else ObdState.Idle
            null
        }
    }

    suspend fun disconnect() = mutex.withLock {
        closeLocked()
        _state.value = ObdState.Idle
    }

    private fun closeLocked() {
        runCatching { transport?.close() }
        transport = null
        elm = null
        ecuOnline.value = false
        voltage.value = null
        supported.value = null
        live.value = emptyMap()
        liveAt.clear()
    }
}
