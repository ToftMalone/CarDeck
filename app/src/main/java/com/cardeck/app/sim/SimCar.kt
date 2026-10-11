package com.cardeck.app.sim

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Voiture virtuelle utilisée par le boîtier simulé : moteur, conduite, circuit GPS,
 * codes défaut et module de codage. Tout est piloté depuis l'appli (démarrer / couper, injecter un défaut).
 */
object SimCar {
    data class State(
        val engineOn: Boolean,
        val rpm: Double,
        val speedKmh: Double,
        val accel: Double,
        val load: Double,
        val throttle: Double,
        val coolant: Double,
        val oil: Double,
        val iat: Double,
        val ambient: Double,
        val map: Double,
        val maf: Double,
        val fuelPct: Double,
        val battery: Double,
        val odometerKm: Double,
        val runSeconds: Int,
        val distanceSinceClearKm: Double,
        val catalyst: Double,
        val lat: Double,
        val lon: Double,
        val heading: Double,
    )

    private val rnd = Random(System.nanoTime())
    private var last = System.currentTimeMillis()

    @Volatile var engineOn = false
        private set
    private var engineStart = 0L
    private var speed = 0.0          // m/s
    private var target = 0.0         // m/s
    private var targetUntil = 0L
    private var accel = 0.0          // m/s²
    private var coolant = 18.0
    private var oil = 18.0
    private var fuel = 72.0
    private var odometer = 48_215.0
    private var sinceClear = 1_250.0
    private var catalyst = 20.0
    private var s = 0.0              // position sur le circuit (m)

    // Circuit fermé autour d'un centre (rectangle aux angles arrondis ~ 6 km).
    private var centerLat = 49.2583
    private var centerLon = 4.0317
    private var route: List<Pair<Double, Double>> = buildRoute()
    private var routeLen = routeLength()

    /** Codes défaut mémorisés par le calculateur moteur simulé. */
    val dtcs = mutableListOf<String>()
    /** Données figées : code + octets bruts (régime, vitesse, liquide, charge) au moment du défaut. */
    var freeze: Pair<String, State>? = null
        private set

    /** Données de codage du boîtier de servitude simulé (DID → octets). */
    val coding = mutableMapOf(0x0101 to intArrayOf(0x00, 0x03))

    private val faultPool = listOf("P0301", "P0171", "P0420", "P0442", "P0128", "P0299", "U0100", "P0562")

    @Synchronized
    fun setCenter(lat: Double, lon: Double) {
        if (engineOn) return
        centerLat = lat; centerLon = lon
        route = buildRoute(); routeLen = routeLength(); s = 0.0
    }

    @Synchronized
    fun startEngine() {
        update()
        if (engineOn) return
        engineOn = true
        engineStart = System.currentTimeMillis()
        targetUntil = 0L
    }

    @Synchronized
    fun stopEngine() {
        update()
        engineOn = false
        speed = 0.0; target = 0.0; accel = 0.0
    }

    /** Ajoute un défaut (avec données figées) ; renvoie le code ajouté. */
    @Synchronized
    fun injectFault(): String {
        val st = state()
        val code = faultPool.firstOrNull { it !in dtcs } ?: faultPool.random(rnd)
        if (code !in dtcs) dtcs += code
        if (freeze == null) freeze = code to st
        return code
    }

    @Synchronized
    fun clearFaults() {
        dtcs.clear(); freeze = null; sinceClear = 0.0
    }

    /** Fait avancer la simulation jusqu'à maintenant. */
    @Synchronized
    fun update() {
        val now = System.currentTimeMillis()
        var dt = (now - last) / 1000.0
        last = now
        while (dt > 0) {
            val step = min(dt, 0.2)
            stepPhysics(step, now)
            dt -= step
        }
    }

    private fun stepPhysics(dt: Double, now: Long) {
        val ambient = 17.0
        if (engineOn) {
            if (now >= targetUntil) {
                // Nouvelle consigne : arrêt, ville, route ou voie rapide.
                val kmh = listOf(0, 30, 50, 50, 70, 80, 90, 110, 130).random(rnd)
                target = kmh / 3.6
                targetUntil = now + (if (kmh == 0) 6_000L else 12_000L + rnd.nextLong(15_000))
            }
            val harsh = rnd.nextDouble() < 0.004
            val wanted = when {
                harsh -> -5.5
                target > speed -> min(2.6, (target - speed) * 0.6 + 0.4)
                target < speed -> max(-3.2, (target - speed) * 0.5 - 0.3)
                else -> 0.0
            }
            accel += (wanted - accel) * min(1.0, dt * 2.5)
            speed = max(0.0, speed + accel * dt)
            coolant += (90.0 - coolant) * dt / 120.0
            oil += (98.0 - oil) * dt / 240.0
            catalyst += ((350 + speed * 4) - catalyst) * dt / 60.0
            fuel = max(3.0, fuel - (0.0006 + speed * 0.00002) * dt)
        } else {
            accel = 0.0
            coolant += (ambient - coolant) * dt / 900.0
            oil += (ambient - oil) * dt / 1200.0
            catalyst += (ambient - catalyst) * dt / 300.0
        }
        val dKm = speed * dt / 1000.0
        odometer += dKm
        sinceClear += dKm
        s = (s + speed * dt) % routeLen
    }

    private fun gearRatio(kmh: Double): Double = when {
        kmh < 18 -> 120.0
        kmh < 35 -> 68.0
        kmh < 55 -> 46.0
        kmh < 75 -> 35.0
        kmh < 100 -> 28.0
        else -> 23.5
    }

    @Synchronized
    fun state(): State {
        update()
        val kmh = speed * 3.6
        val rpm = when {
            !engineOn -> 0.0
            kmh < 3 -> 780.0 + rnd.nextDouble(-15.0, 15.0)
            else -> max(950.0, kmh * gearRatio(kmh)) + rnd.nextDouble(-20.0, 20.0)
        }
        val load = if (!engineOn) 0.0 else (22.0 + max(0.0, accel) * 22 + kmh * 0.18 + rnd.nextDouble(0.0, 3.0)).coerceIn(15.0, 100.0)
        val throttle = if (!engineOn) 0.0 else (load * 0.85).coerceIn(0.0, 100.0)
        val map = if (!engineOn) 101.0 else (30.0 + load * 1.6).coerceAtMost(210.0)
        val maf = if (!engineOn) 0.0 else rpm / 1000.0 * load / 100.0 * 22 + 1.8
        val (lat, lon, hdg) = position()
        return State(
            engineOn = engineOn, rpm = rpm, speedKmh = kmh, accel = accel, load = load, throttle = throttle,
            coolant = coolant, oil = oil, iat = 24.0 + load * 0.08, ambient = 17.0, map = map, maf = maf, fuelPct = fuel,
            battery = if (engineOn) 14.15 + rnd.nextDouble(-0.05, 0.05) else 12.55, odometerKm = odometer,
            runSeconds = if (engineOn) ((System.currentTimeMillis() - engineStart) / 1000).toInt() else 0,
            distanceSinceClearKm = sinceClear, catalyst = catalyst, lat = lat, lon = lon, heading = hdg,
        )
    }

    // ---- Circuit ----

    private fun buildRoute(): List<Pair<Double, Double>> {
        // Rectangle 2 km × 1 km aux angles arrondis, en mètres autour du centre.
        val pts = mutableListOf<Pair<Double, Double>>()
        val w = 1000.0; val h = 500.0; val r = 200.0
        val corners = listOf(Triple(w - r, h - r, 0.0), Triple(-(w - r), h - r, 90.0), Triple(-(w - r), -(h - r), 180.0), Triple(w - r, -(h - r), 270.0))
        for ((cx, cy, a0) in corners) {
            for (k in 0..8) {
                val a = (a0 + k * 90.0 / 8) * PI / 180
                pts += (cx + r * cos(a)) to (cy + r * sin(a))
            }
        }
        val mLat = 111_320.0
        val mLon = 111_320.0 * cos(centerLat * PI / 180)
        return pts.map { (x, y) -> (centerLat + y / mLat) to (centerLon + x / mLon) }
    }

    private fun segLen(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val mLat = 111_320.0
        val mLon = 111_320.0 * cos(centerLat * PI / 180)
        val dy = (b.first - a.first) * mLat
        val dx = (b.second - a.second) * mLon
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun routeLength(): Double = route.indices.sumOf { segLen(route[it], route[(it + 1) % route.size]) }

    private fun position(): Triple<Double, Double, Double> {
        var rest = s
        for (i in route.indices) {
            val a = route[i]
            val b = route[(i + 1) % route.size]
            val l = segLen(a, b)
            if (rest <= l) {
                val f = if (l > 0) rest / l else 0.0
                val lat = a.first + (b.first - a.first) * f
                val lon = a.second + (b.second - a.second) * f
                val hdg = (atan2((b.second - a.second) * cos(centerLat * PI / 180), b.first - a.first) * 180 / PI + 360) % 360
                return Triple(lat, lon, hdg)
            }
            rest -= l
        }
        return Triple(route[0].first, route[0].second, 0.0)
    }

    fun clampByte(v: Double): Int = v.roundToInt().coerceIn(0, 255)
}
