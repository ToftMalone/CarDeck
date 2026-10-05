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
                if (rec != null) {
                    if (lostSince == 0L) lostSince = System.currentTimeMillis()
                    if (System.currentTimeMillis() - lostSince > 90_000) endTrip()
                }
                status(if (recorder != null) "Trajet en cours · reconnexion au boîtier…" else "Recherche de ${adapter.name}…")
                if (obd.connect(adapter).isFailure) {
                    delay(if (recorder != null) 5000 else 30_000)
                    continue
                }
            }
            lostSince = 0L
            val snap = obd.use { e -> Triple(e.rpm(), e.speed(), e.voltage()) }
            if (snap == null) { delay(2000); continue }
            val (rpm, speed, volt) = snap
            obd.voltage.value = volt
            obd.ecuOnline.value = rpm != null
            val engineOn = rpm != null && rpm > 300
            if (engineOn) {
                engineOffSince = 0L
                if (recorder == null) startTrip(v.id)
                recorder?.onObdSpeed(speed)
            } else if (recorder != null) {
                if (engineOffSince == 0L) engineOffSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - engineOffSince > 20_000) endTrip()
            }
            val r = recorder
            status(
                when {
                    r != null -> "Trajet en cours · ${fr(r.distanceKm, 1)} km"
                    rpm != null -> "${v.nickname} · contact mis, moteur arrêté"
                    else -> "${v.nickname} · boîtier connecté, véhicule à l'arrêt"
                },
            )
            delay(if (recorder != null) 2000 else 10_000)
        }
    }

    private fun startTrip(vehicleId: Long) {
        if (!withLocation && Perms.locationOk(this)) goForeground("Trajet en cours")
        recorder = TripRecorder(this, cardeck.repo, vehicleId).also { it.begin() }
    }

    private fun endTrip() {
        recorder?.finish()
        recorder = null
    }

    companion object {
        private const val CHANNEL = "vehicle"
        private const val NOTIF_ID = 42

        /** Démarre le suivi si un véhicule existe (à appeler depuis le premier plan ou au démarrage du téléphone). */
        fun start(ctx: Context) {
            if (ctx.cardeck.repo.vehicles.value.isEmpty()) return
            if (Build.VERSION.SDK_INT >= 34 && !Perms.bluetoothOk(ctx) && !Perms.has(ctx, android.Manifest.permission.CHANGE_NETWORK_STATE)) return
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, TripService::class.java)) }
        }
    }
}
