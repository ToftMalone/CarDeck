package com.cardeck.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.cardeck.app.Perms
import com.cardeck.app.data.EventType
import com.cardeck.app.data.Repo
import com.cardeck.app.data.TripEvent
import com.cardeck.app.data.TripPoint
import com.cardeck.app.data.scoreOf
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Enregistre un trajet : points GPS, distance, vitesses et événements de conduite
 * (freinages / accélérations / virages détectés à partir de la vitesse et du cap GPS).
 */
class TripRecorder(private val ctx: Context, private val repo: Repo, vehicleId: Long) {
    private val io = Executors.newSingleThreadExecutor()
    private val lm = ctx.getSystemService(LocationManager::class.java)
    val start = System.currentTimeMillis()
    val tripId: Long = repo.startTrip(vehicleId, start)

    @Volatile var gpsKm = 0.0
        private set
    @Volatile private var obdKm = 0.0
    @Volatile private var maxKmh = 0.0
    private var lastObdT = 0L
    private var last: Location? = null
    private var firstFix: Location? = null
    private val pending = mutableListOf<TripPoint>()
    private val events = mutableListOf<TripEvent>()
    private val lastEventAt = HashMap<EventType, Long>()

    val distanceKm: Double get() = if (gpsKm > 0.05) gpsKm else obdKm

    private val listener = LocationListener { onLocation(it) }

    @SuppressLint("MissingPermission")
    fun begin() {
        if (Perms.locationOk(ctx) && lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper()) }
        }
    }

    /** Vitesse lue sur le véhicule (PID 0D) : distance de secours sans GPS + vitesse max. */
    @Synchronized
    fun onObdSpeed(kmh: Int?) {
        val now = System.currentTimeMillis()
        if (kmh != null) {
            if (lastObdT > 0) obdKm += kmh / 3600.0 * ((now - lastObdT) / 1000.0)
            maxKmh = maxOf(maxKmh, kmh.toDouble())
        }
        lastObdT = now
    }

    @Synchronized
    private fun onLocation(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > 35f) return
        if (firstFix == null) firstFix = loc
        val prev = last
        val kmh = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0
        if (prev != null) {
            val d = prev.distanceTo(loc)
            if (d > 2f) gpsKm += d / 1000.0
            detectEvents(prev, loc)
        }
        last = loc
        pending += TripPoint(loc.time, loc.latitude, loc.longitude, kmh)
        if (pending.size >= 10) flush()
    }

    private fun detectEvents(prev: Location, loc: Location) {
        val dt = (loc.time - prev.time) / 1000.0
        if (dt !in 0.5..3.0 || !prev.hasSpeed() || !loc.hasSpeed()) return
        val v0 = prev.speed.toDouble()
        val v1 = loc.speed.toDouble()
        val a = (v1 - v0) / dt
        val g = 9.81
        if (a <= -0.35 * g && v0 > 4.2) emit(EventType.Brake, loc, a / g)
        else if (a >= 0.30 * g && v1 > 2.0) emit(EventType.Accel, loc, a / g)
        if (prev.hasBearing() && loc.hasBearing() && v1 > 5.5) {
            var db = abs(loc.bearing - prev.bearing).toDouble()
            if (db > 180) db = 360 - db
            val lateral = v1 * Math.toRadians(db) / dt
            if (lateral >= 0.40 * g) emit(EventType.Turn, loc, lateral / g)
        }
    }

    private fun emit(type: EventType, loc: Location, g: Double) {
        val last = lastEventAt[type] ?: 0L
        if (loc.time - last < 5000) return
        lastEventAt[type] = loc.time
        val e = TripEvent(loc.time, type, loc.latitude, loc.longitude, g)
        events += e
        io.execute { repo.addEvent(tripId, e) }
    }

    @Synchronized
    private fun flush() {
        if (pending.isEmpty()) return
        val batch = pending.toList()
        pending.clear()
        io.execute { repo.addPoints(tripId, batch) }
    }

    /** Termine le trajet ; les trajets de moins de 200 m sont supprimés. */
    @Synchronized
    fun finish() {
        runCatching { lm?.removeUpdates(listener) }
        flush()
        val end = System.currentTimeMillis()
        val dist = distanceKm
        val first = firstFix
        val lastLoc = last
        val evs = events.toList()
        val ptsMax = maxKmh
        io.execute {
            if (dist < 0.2) {
                repo.deleteTrip(tripId)
            } else {
                val hours = (end - start) / 3_600_000.0
                val from = first?.let { place(it) } ?: "Position inconnue"
                val to = lastLoc?.let { place(it) } ?: "Position inconnue"
                val max = maxOf(ptsMax, repo.points(tripId).maxOfOrNull { it.speedKmh } ?: 0.0)
                repo.finishTrip(tripId, end, dist, if (hours > 0) dist / hours else 0.0, max, scoreOf(evs), from, to)
            }
        }
        io.shutdown()
    }

    private fun place(l: Location): String = try {
        @Suppress("DEPRECATION")
        val a = Geocoder(ctx, Locale.FRANCE).getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()
        listOfNotNull(a?.thoroughfare, a?.locality).joinToString(", ").ifBlank { coords(l) }
    } catch (_: Exception) {
        coords(l)
    }

    private fun coords(l: Location) = "%.4f, %.4f".format(Locale.FRANCE, l.latitude, l.longitude)
}
