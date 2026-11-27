package com.cardeck.app.data

/** Épreuve chronométrée. [standing] : départ arrêté ; sinon départ lancé à [fromKmh]. */
enum class PerfKind(val label: String, val standing: Boolean, val fromKmh: Double, val toKmh: Double?, val meters: Double?, val timeoutMs: Long) {
    ZeroTo100("0 à 100 km/h", true, 0.0, 100.0, null, 30_000),
    From80To120("80 à 120 km/h", false, 80.0, 120.0, null, 25_000),
    Quarter400("400 m départ arrêté", true, 0.0, null, 400.0, 45_000);

    companion object {
        fun of(name: String?): PerfKind? = entries.find { it.name == name }
    }
}

/** Un chrono enregistré : temps (ms), vitesse au passage des 400 m, qualité de la mesure (mesures de vitesse par seconde). */
data class PerfRun(val id: Long, val vehicleId: Long, val kind: PerfKind, val at: Long, val timeMs: Long, val trapKmh: Double?, val hz: Double)

/**
 * Chronométrage à partir de la vitesse lue sur la voiture (PID 0D, en km/h entiers, une dizaine de fois par seconde) :
 * les instants de départ et d'arrivée tombent entre deux mesures, ils sont interpolés.
 *  - départ arrêté : armé à l'arrêt complet ; le départ est l'instant où la vitesse quitte 0, extrapolé depuis les deux
 *    premières mesures en mouvement (borné entre la dernière mesure à 0 et la première en mouvement) ;
 *  - départ lancé (80 à 120) : armé sous 80 km/h, départ au passage de 80, arrivée au passage de 120 ;
 *  - 400 m : distance intégrée depuis le départ (trapèzes), arrivée et vitesse de passage interpolées.
 * Un essai est abandonné si la voiture ralentit nettement, si les mesures s'espacent trop ou au-delà du temps maximal.
 * Les instants [t] sont en millisecondes, sur une horloge monotone.
 */
class PerfTimer(val kind: PerfKind) {
    enum class Phase { Waiting, Ready, Running }

    sealed interface Outcome {
        data class Done(val timeMs: Long, val trapKmh: Double?, val hz: Double) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    data class State(
        val phase: Phase = Phase.Waiting,
        val speed: Double? = null,
        /** Instant du départ (même horloge que les mesures), pendant un essai. */
        val startT: Long? = null,
        val distanceM: Double = 0.0,
        /** Dernier résultat, réussi ou abandonné (gardé jusqu'au résultat suivant). */
        val last: Outcome? = null,
        /** Mesures de vitesse par seconde, sur les 3 dernières secondes. */
        val hz: Double = 0.0,
    )

    var state = State()
        private set

    private var prevT: Long? = null
    private var prevV: Double? = null
    private var lastZeroT: Long? = null
    private var start: Double? = null // instant de départ, en ms (fractionnaire)
    private var firstMove: Pair<Long, Double>? = null
    private var dist = 0.0
    private var max = 0.0
    private val recent = ArrayDeque<Long>()
    private var runSamples = 0

    fun reset() {
        prevT = null; prevV = null; lastZeroT = null; start = null; firstMove = null; dist = 0.0; max = 0.0; runSamples = 0
        recent.clear()
        state = State()
    }

    private fun rate(t: Long): Double {
        recent.addLast(t)
        while (recent.size > 1 && t - recent.first() > 3000) recent.removeFirst()
        if (recent.size < 2) return 0.0
        return (recent.size - 1) * 1000.0 / (recent.last() - recent.first()).coerceAtLeast(1)
    }

    private fun idle(v: Double): Phase = when {
        kind.standing -> if (v <= 0.0) Phase.Ready else Phase.Waiting
        else -> if (v < kind.fromKmh) Phase.Ready else Phase.Waiting
    }

    private fun stop(outcome: Outcome?, v: Double, hz: Double) {
        start = null; firstMove = null; dist = 0.0; max = 0.0; runSamples = 0
        state = State(idle(v), v, null, 0.0, outcome, hz)
    }

    /** Nouvelle mesure de vitesse [v] (km/h) à l'instant [t] (ms) ; renvoie l'état. */
    fun feed(t: Long, v: Double): State {
        val pt = prevT
        val pv = prevV
        if (pt != null && t <= pt) return state // mesure en double ou dans le désordre
        val hz = rate(t)
        prevT = t; prevV = v
        if (v <= 0.0) lastZeroT = t

        val s0 = start
        if (s0 == null) {
            // Pas d'essai en cours : faut-il en commencer un ?
            val began = when {
                pt == null || pv == null -> false
                kind.standing -> pv <= 0.0 && v > 0.0 && state.phase == Phase.Ready
                else -> pv < kind.fromKmh && v >= kind.fromKmh && state.phase == Phase.Ready
            }
            if (!began) {
                state = state.copy(phase = idle(v), speed = v, startT = null, distanceM = 0.0, hz = hz)
                return state
            }
            val t0 = if (kind.standing) {
                // Provisoire : à mi-chemin entre la dernière mesure à l'arrêt et la première en mouvement (affinée à la mesure suivante).
                firstMove = t to v
                (pt!! + t) / 2.0
            } else pt!! + (kind.fromKmh - pv!!) / (v - pv) * (t - pt)
            start = t0
            max = v
            runSamples = 1
            // Distance depuis le départ : de 0 (ou de la vitesse de départ) jusqu'à cette mesure.
            dist = if (kind.standing) v / 3.6 * (t - t0) / 1000.0 / 2 else (kind.fromKmh + v) / 2 / 3.6 * (t - t0) / 1000.0
            state = State(Phase.Running, v, t0.toLong(), dist, state.last, hz)
            return state
        }

        // Essai en cours.
        runSamples++
        if (t - pt!! > 1500) { stop(Outcome.Failed("Mesures de vitesse trop espacées"), v, hz); return state }
        var t0 = s0
        val fm = firstMove
        if (fm != null && runSamples == 2) {
            // Départ affiné : droite passant par les deux premières mesures en mouvement, ramenée à 0 km/h.
            val (t1, v1) = fm
            val a = (v - v1) / (t - t1)
            val z = lastZeroT?.takeIf { it < t1 } ?: pt
            val est = if (a > 0) t1 - v1 / a else (z + t1) / 2.0
            t0 = est.coerceIn(z.toDouble(), t1.toDouble())
            start = t0
            dist = v1 / 3.6 * (t1 - t0) / 1000.0 / 2
            firstMove = null
        }
        val before = dist
        dist += (pv!! + v) / 2 / 3.6 * (t - pt) / 1000.0
        max = maxOf(max, v)

        val target = kind.toKmh
        val meters = kind.meters
        if (target != null && pv < target && v >= target) {
            val tEnd = pt + (target - pv) / (v - pv) * (t - pt)
            stop(Outcome.Done((tEnd - t0).toLong(), null, hz), v, hz)
            return state
        }
        if (meters != null && before < meters && dist >= meters) {
            val f = (meters - before) / (dist - before)
            val tEnd = pt + f * (t - pt)
            stop(Outcome.Done((tEnd - t0).toLong(), pv + f * (v - pv), hz), v, hz)
            return state
        }
        when {
            // Faux départ (quelques km/h puis arrêt) : annulé sans rien afficher ; sinon, essai abandonné.
            v <= 0.0 -> stop(if (max < 10) state.last else Outcome.Failed("Arrêt avant l'arrivée"), v, hz)
            v < max - 10 -> stop(Outcome.Failed("Accélération interrompue"), v, hz)
            !kind.standing && v < kind.fromKmh - 5 -> stop(Outcome.Failed("Vitesse retombée sous ${kind.fromKmh.toInt()} km/h"), v, hz)
            t - t0 > kind.timeoutMs -> stop(Outcome.Failed("Temps maximal dépassé"), v, hz)
            else -> state = State(Phase.Running, v, t0.toLong(), dist, state.last, hz)
        }
        return state
    }
}
