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
        recording = false
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
            val snap = obd.use { e -> e.rpm() to e.speed() }
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
            val (rpm, speed) = snap
            obd.ecuOnline.value = rpm != null
            obd.publish(listOfNotNull(rpm?.let { "std0C" to it }, speed?.let { "std0D" to it.toDouble() }))
            onEngine(v.id, rpm, speed)
            if (rpm == null) {
                // Pas de réponse du moteur : lecture ratée pendant un trajet (on réessaie vite), ou contact coupé.
                if (recorder != null) { delay(300); continue }
                status("${v.nickname} · boîtier connecté, contact coupé")
                delay(5_000)
                continue
            }
            readEverything(v, cycle++)
            val r = recorder
            status(if (r != null) "Trajet en cours · ${fr(r.distanceKm, 1)} km" else "${v.nickname} · contact mis, moteur arrêté")
            delay(30)
        }
    }

    private var engineOffSince = 0L

    /** Démarrage / fin automatique du trajet selon le régime. Renvoie true si le moteur tourne. */
    private fun onEngine(vehicleId: Long, rpm: Double?, speed: Int?): Boolean {
        val engineOn = rpm != null && rpm > 300
        if (engineOn) {
            engineOffSince = 0L
            if (recorder == null) startTrip(vehicleId)
            recorder?.onObdSpeed(speed)
        } else if (recorder != null) {
            // Régime à 0 : fin immédiate du trajet. Calculateur muet : courte tolérance (lecture ratée).
            if (rpm != null) endTrip()
            else {
                if (engineOffSince == 0L) engineOffSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - engineOffSince > NO_ANSWER_MS) endTrip()
            }
        }
        return engineOn
    }

    /**
     * Lecture permanente (contact mis, écran ouvert ou non), publiée pour les écrans et enregistrée pendant un trajet.
     * Les données moteur de [HOT_IDS] sont relues à chaque tour, et entre chaque paquet de 4 requêtes.
     * Les autres sont réparties sur 3 tours (chacune est lue un tour sur 3) ; les données lentes (compteurs,
     * kilométrage, huile, carburant…) sur 6 tours : elles ne bougent presque pas et certaines coûtent jusqu'à 1,2 s.
     */
    private suspend fun readEverything(v: com.cardeck.app.data.VehicleProfile, cycle: Int) {
        val obd = cardeck.obd
        if (obd.supported.value == null) obd.use { it.supportedPids() }?.let { obd.supported.value = it }
        val sup = obd.supported.value.orEmpty()
        val all = StdPids.forSupported(sup) + v.template.pids
        val hot = all.filter { it.id in HOT_IDS && it.can == null && it.header == null }
        val (slow, normal) = all.filter { it !in hot }.partition { it.slow }
        fun share(defs: List<com.cardeck.app.data.PidDef>, turns: Int) =
            defs.groupBy { it.request to it.header }.values.chunked(4).filterIndexed { i, _ -> i % turns == cycle % turns }
        val chunks = share(normal, 3) + share(slow, 6)

        // Lit un paquet, le publie et l'enregistre ; false si la liaison est perdue (le tour s'arrête).
        suspend fun read(defs: List<com.cardeck.app.data.PidDef>, volt: Boolean = false): Boolean {
            if (defs.isEmpty() && !volt) return true
            val now = System.currentTimeMillis()
            val values = obd.use { e -> e.readAll(defs) + listOfNotNull(if (volt) e.voltage()?.let { "elm_volt" to it } else null) } ?: return false
            values.firstOrNull { it.first == "elm_volt" }?.let { obd.voltage.value = it.second }
            obd.publish(values)
            recorder?.addSamples(now, values)
            // Régime relu dans la voie rapide : le trajet s'arrête dès que le moteur est coupé, sans attendre la fin du tour.
            val rpm = values.firstOrNull { it.first == "std0C" }?.second
            if (rpm != null) onEngine(v.id, rpm, values.firstOrNull { it.first == "std0D" }?.second?.toInt())
            return true
        }

        if (!read(hot, volt = true)) return
        for (chunk in chunks) {
            if (!read(chunk.flatten())) return
            if (!read(hot)) return
        }
    }

    private fun startTrip(vehicleId: Long) {
        if (!withLocation && Perms.locationOk(this)) goForeground("Trajet en cours")
        recorder = TripRecorder(this, cardeck.repo, vehicleId).also { it.begin() }
        recording = true
    }

    private fun endTrip() {
        recorder?.finish { saved, km ->
            lastNote.value = if (saved) "Dernier trajet enregistré · ${fr(km, 1)} km" else "Dernier trajet trop court (moins de 200 m) : non enregistré"
        }
        recorder = null
        recording = false
    }

    companion object {
        private const val CHANNEL = "vehicle"
        private const val NOTIF_ID = 42

        /** Délai sans réponse du moteur avant de clore le trajet. */
        private const val NO_ANSWER_MS = 3_000L

        /** Lues à chaque tour : régime, vitesse, charge, avance, MAP, débit d'air, papillon, pression atmosphérique. */
        private val HOT_IDS = setOf("std0C", "std0D", "std04", "std0E", "std0B", "std10", "std11", "std33")

        /** État du suivi pour l'interface (null = service arrêté). */
        val live = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

        /** Un trajet est en cours d'enregistrement. */
        @Volatile var recording = false
            private set

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
