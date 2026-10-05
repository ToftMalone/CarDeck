package com.cardeck.app.obd

import android.content.Context
import com.cardeck.app.data.AdapterConfig
import com.cardeck.app.data.Transport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            e.init()
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
    }
}
