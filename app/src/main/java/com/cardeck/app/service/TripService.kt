package com.cardeck.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cardeck.app.MainActivity
import com.cardeck.app.Perms
import com.cardeck.app.R
import com.cardeck.app.cardeck
import com.cardeck.app.data.StdPids
import com.cardeck.app.data.fr
import com.cardeck.app.obd.ObdState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service de premier plan : maintient la connexion avec le boîtier du véhicule actif
 * et enregistre automatiquement un trajet tant que le moteur tourne.
 */
class TripService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var recorder: TripRecorder? = null
    private var withLocation = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Suivi du véhicule", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        if (!goForeground("Recherche du boîtier OBD2…")) stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (loop == null) loop = scope.launch { run() }
        return START_STICKY
    }

    override fun onDestroy() {
        live.value = null
        recorder?.finish()
        recorder = null
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(text: String): Boolean {
        withLocation = Perms.locationOk(this)
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or (if (withLocation) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        return try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification(text), types)
            true
        } catch (_: Exception) {
            withLocation = false
            runCatching { ServiceCompat.startForeground(this, NOTIF_ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) }.isSuccess
        }
    }

    private fun notification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle("CarDeck")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .build()
    }

    private var lastText = ""
    private fun status(text: String) {
        live.value = text
        if (text == lastText) return
        lastText = text
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    private suspend fun run() {
        val app = cardeck
        val repo = app.repo
        val obd = app.obd
        repo.recoverUnfinished()
        var engineOffSince = 0L
        var lostSince = 0L
        var cycle = 0
        while (scope.isActive) {
            val v = repo.active
            val adapter = v?.adapter
            if (v == null) {
                endTrip()
                stopSelf()
                return
            }
            if (adapter == null || obd.pairing) {
                endTrip()
                status(if (adapter == null) "Aucun boîtier associé à ${v.nickname}" else "Appairage en cours…")
                delay(5000)
                continue
            }
            val rec = recorder
            if (obd.connectedAddress != adapter.address || obd.state.value !is ObdState.Connected) {
                // Boîtier perdu (contact coupé, sortie du véhicule) : le trajet s'arrête aussitôt.
                if (rec != null) endTrip()
                status("Recherche de ${adapter.name}…")
                if (obd.connect(adapter).isFailure) {
                    delay(30_000)
                    continue
                }
            }
            val snap = obd.use { e -> Triple(e.rpm(), e.speed(), e.voltage()) }
            if (snap == null) {
                // Plus de réponse du boîtier : on ne trace pas sans données moteur.
                if (recorder != null) {
                    if (lostSince == 0L) lostSince = System.currentTimeMillis()
                    if (System.currentTimeMillis() - lostSince > NO_ANSWER_MS) endTrip()
                }
                delay(1000)
                continue
            }
            lostSince = 0L
            val (rpm, speed, volt) = snap
            obd.voltage.value = volt
            obd.ecuOnline.value = rpm != null
            val engineOn = rpm != null && rpm > 300
            val isSim = adapter.transport == com.cardeck.app.data.Transport.Sim
            if (engineOn) {
                engineOffSince = 0L
                if (recorder == null) startTrip(v.id)
                recorder?.onObdSpeed(speed)
            } else if (recorder != null) {
                // Régime à 0 : fin immédiate du trajet. Calculateur muet : courte tolérance (lecture ratée).
                if (rpm != null) endTrip()
                else {
                    if (engineOffSince == 0L) engineOffSince = System.currentTimeMillis()
                    if (System.currentTimeMillis() - engineOffSince > NO_ANSWER_MS) endTrip()
                }
            }
            // Historique : toutes les données déclarées par le véhicule + PIDs du modèle + tension du boîtier.
            recorder?.let { rec2 ->
                if (obd.supported.value == null) obd.use { it.supportedPids() }?.let { obd.supported.value = it }
                val sup = obd.supported.value.orEmpty()
                val pids = (StdPids.forSupported(sup) + v.template.pids).filter { !it.slow || cycle % 6 == 0 }
                val now = System.currentTimeMillis()
                val values = obd.use { e -> e.readAll(pids) }
                if (values != null) rec2.addSamples(now, values + listOfNotNull(volt?.let { "elm_volt" to it }))
                cycle++
            }
            val r = recorder
            status(
                when {
                    r != null -> "Trajet en cours · ${fr(r.distanceKm, 1)} km"
                    rpm != null -> "${v.nickname} · contact mis, moteur arrêté"
                    else -> "${v.nickname} · boîtier connecté, véhicule à l'arrêt"
                },
            )
            delay(if (recorder != null) 300 else if (isSim) 1_000 else 10_000)
        }
    }

    private fun startTrip(vehicleId: Long) {
        if (!withLocation && Perms.locationOk(this)) goForeground("Trajet en cours")
        val sim = cardeck.repo.active?.adapter?.transport == com.cardeck.app.data.Transport.Sim
        recorder = TripRecorder(this, cardeck.repo, vehicleId, sim).also { it.begin() }
    }

    private fun endTrip() {
        recorder?.finish { saved, km ->
            lastNote.value = if (saved) "Dernier trajet enregistré · ${fr(km, 1)} km" else "Dernier trajet trop court (moins de 200 m) : non enregistré"
        }
        recorder = null
    }

    companion object {
        private const val CHANNEL = "vehicle"
        private const val NOTIF_ID = 42

        /** Délai sans réponse du moteur avant de clore le trajet. */
        private const val NO_ANSWER_MS = 3_000L

        /** État du suivi pour l'interface (null = service arrêté). */
        val live = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

        /** Issue du dernier trajet (enregistré ou trop court). */
        val lastNote = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

        /** Démarre le suivi si un véhicule existe (à appeler depuis le premier plan ou au démarrage du téléphone). */
        fun start(ctx: Context) {
            if (ctx.cardeck.repo.vehicles.value.isEmpty()) return
            if (Build.VERSION.SDK_INT >= 34 && !Perms.bluetoothOk(ctx) && !Perms.has(ctx, android.Manifest.permission.CHANGE_NETWORK_STATE)) return
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, TripService::class.java)) }
        }
    }
}
