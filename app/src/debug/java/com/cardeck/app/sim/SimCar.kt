package com.cardeck.app.sim

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Voiture simulée (version de développement uniquement) : une Swift Sport ZC33S qui roule 6 minutes (3 cycles de
 * 2 minutes : ralenti, accélération franche, croisière, dépassement, freinage), puis reste 80 s contact mis et moteur
 * coupé (le trajet se termine, un nouveau commence au redémarrage). Moteur qui chauffe, réservoir qui baisse,
 * codes défaut et tests antipollution qui se terminent en roulant après un effacement. Pendant les accélérations en
 * charge, le moteur cliquette : retard d'allumage par cylindre dans le bloc constructeur 21 00 (octets 40 à 43).
 */
class SimCar(private val clock: () -> Long = System::currentTimeMillis) {
    val vin = "TSMZC33S0R0731984"

    private val start = clock()
    private var lastT = start
    private var km = 0.0

    // ---- Défauts (codes en texte : « P0420 »…)
    val stored = mutableListOf("P0420")
    val pending = mutableListOf("P0171")
    val permanent = mutableListOf("P0420")
    val absCodes = mutableListOf("C0035")
    private var kmAtClear = -120.0
    private var warmupsAtClear = -14

    /** Montées en température depuis le départ : une toutes les 10 minutes de simulation. */
    private fun warmups() = ((clock() - start) / 600_000).toInt()

    /** Instantané des grandeurs du moteur. */
    class State(
        val speed: Double, val rpm: Double, val load: Double, val throttle: Double, val map: Double, val advance: Double,
        val coolant: Double, val oil: Double, val intake: Double, val maf: Double, val fuel: Double, val volts: Double,
        val runtime: Int, val km: Double, val knock: IntArray,
    )

    /** Vitesse (km/h) et charge (%) du cycle de conduite à l'instant [s] (secondes dans le cycle de 120 s). */
    private fun profile(s: Double): Pair<Double, Double> {
        fun ease(a: Double, b: Double, f: Double) = a + (b - a) * (0.5 - 0.5 * kotlin.math.cos(PI * f.coerceIn(0.0, 1.0)))
        return when {
            s < 8 -> 0.0 to 18.0
            s < 22 -> ease(0.0, 90.0, (s - 8) / 14) to 92.0
            s < 55 -> 90.0 to 36.0
            s < 63 -> ease(90.0, 125.0, (s - 55) / 8) to 96.0
            s < 85 -> 120.0 to 46.0
            s < 105 -> ease(120.0, 0.0, (s - 85) / 20) to 5.0
            else -> 0.0 to 18.0
        }
    }

    fun state(): State {
        val now = clock()
        val elapsed = (now - start) / 1000.0
        val inLoop = elapsed % LOOP_S
        if (inLoop >= DRIVE_S) {
            // À l'arrêt, contact mis, moteur coupé : le calculateur répond, régime nul.
            lastT = now
            val coolant = 60 + 30 * (1 - exp(-elapsed / 240))
            return State(
                speed = 0.0, rpm = 0.0, load = 0.0, throttle = 0.0, map = 101.0, advance = 0.0, coolant = coolant, oil = coolant - 4,
                intake = 30.0, maf = 0.0, fuel = (64 - km * 0.12).coerceIn(5.0, 100.0), volts = 12.4, runtime = 0, km = km, knock = IntArray(4),
            )
        }
        val s = inLoop % 120.0
        val (v0, load0) = profile(s)
        val wobble = sin(elapsed * 0.7) * 0.8
        val speed = max(0.0, v0 + if (v0 > 1) wobble else 0.0)
        val load = (load0 + sin(elapsed * 1.3) * 2).coerceIn(0.0, 100.0)
        // Distance parcourue (intégrée entre deux appels).
        km += speed / 3600.0 * ((now - lastT).coerceIn(0, 2000) / 1000.0)
        lastT = now
        // Régime : rapport engagé selon la vitesse, un rapport plus court quand on accélère fort.
        val ratios = doubleArrayOf(8.5, 15.0, 22.0, 29.0, 36.0, 43.0)
        var gear = when { speed < 15 -> 0; speed < 32 -> 1; speed < 55 -> 2; speed < 80 -> 3; speed < 105 -> 4; else -> 5 }
        if (load > 85 && gear > 1) gear--
        val rpm = if (speed < 1) 820 + sin(elapsed * 3) * 15 else (speed / ratios[gear] * 1000).coerceIn(1300.0, 6200.0)
        // Cliquetis : en charge à bas et moyen régime, retard par cylindre (octet = degrés × 2).
        val knocking = load > 85 && rpm < 4200
        val knock = IntArray(4) { i -> if (!knocking) 0 else ((sin(elapsed * 5.3 + i * 1.7) + 1) * 2.5).roundToInt().coerceIn(0, 6) }
        val retard = knock.average() / 2
        val advance = (10 + rpm / 6000 * 22 - load * 0.14 - retard).coerceIn(-5.0, 45.0)
        val coolant = 60 + 30 * (1 - exp(-elapsed / 240))
        val oil = coolant - 6 + load * 0.08
        return State(
            speed = speed, rpm = rpm, load = load, throttle = (10 + load * 0.85).coerceIn(0.0, 100.0),
            map = (32 + load * 1.6).coerceAtMost(250.0), advance = advance, coolant = coolant, oil = oil,
            intake = 27 + load * 0.08, maf = 2.5 + rpm / 1000 * load / 100 * 21, fuel = (64 - km * 0.12).coerceIn(5.0, 100.0),
            volts = 14.1 + sin(elapsed) * 0.05, runtime = elapsed.toInt(), km = km, knock = knock,
        )
    }

    // ---- Diagnostic

    val kmSinceClear: Double get() = km - kmAtClear
    val warmupsSinceClear: Int get() = warmups() - warmupsAtClear
    val mil: Boolean get() = stored.isNotEmpty()

    /** Effacement (service 04) : codes mémorisés et en attente, tests antipollution remis à zéro. */
    fun clearObd() {
        stored.clear(); pending.clear()
        kmAtClear = km
        warmupsAtClear = warmups()
    }

    fun clearAbs() = absCodes.clear()

    /** Les codes permanents disparaissent après 2 km sans défaut depuis l'effacement. */
    fun permanentCodes(): List<String> {
        if (stored.isEmpty() && kmSinceClear > 2) permanent.clear()
        return permanent
    }

    /**
     * Octets A B C D du PID 01 : voyant et nombre de codes, puis moteur à essence avec catalyseur, EVAP, sondes lambda et
     * leur chauffage. Après un effacement, chaque test se termine après quelques kilomètres.
     */
    fun readiness(): IntArray {
        val d = kmSinceClear
        val a = (if (mil) 0x80 else 0) or stored.size.coerceAtMost(0x7F)
        val b = 0x07 or (if (d < 0.2) 0x70 else 0)
        val c = 0x65
        var incomplete = 0
        if (d < 3) incomplete = incomplete or 0x01   // catalyseur
        if (d < 8) incomplete = incomplete or 0x04   // circuit des vapeurs d'essence
        if (d < 1.5) incomplete = incomplete or 0x20 // sondes lambda
        if (d < 0.3) incomplete = incomplete or 0x40 // chauffage des sondes
        return intArrayOf(a, b, c, incomplete)
    }

    companion object {
        /** Durée de conduite d'une boucle, puis durée totale (avec l'arrêt moteur coupé). */
        const val DRIVE_S = 360.0
        const val LOOP_S = 440.0

        /** Code texte (« P0420 ») → 2 octets OBD. */
        fun dtcBytes(code: String): IntArray {
            val kind = "PCBU".indexOf(code[0].uppercaseChar()).coerceAtLeast(0)
            val d1 = code[1].digitToInt(16)
            val rest = code.substring(2).toInt(16)
            return intArrayOf((kind shl 6) or (d1 shl 4) or (rest shr 8), rest and 0xFF)
        }

        fun u16(v: Double): IntArray { val x = v.roundToInt().coerceIn(0, 0xFFFF); return intArrayOf(x shr 8, x and 0xFF) }

        fun byte(v: Double): Int = v.roundToInt().coerceIn(0, 255)
    }
}
