package com.cardeck.app.data

/**
 * Patinage des roues motrices et intervention de l'antipatinage, déduits des données OBD standard.
 *
 * Patinage : la vitesse lue par le moteur vient des roues ; quand les roues motrices patinent, elle grimpe plus vite que ne peut
 * accélérer la voiture entière (relevé sur une Swift Sport : 13,5 km/h par seconde au plus sans patinage, 20 à 44 en patinant).
 * Antipatinage : dans la foulée, le calculateur coupe le couple en refermant le papillon alors que le moteur reste en charge
 * (et en retardant l'allumage : la combustion finit dans l'échappement, d'où les détonations).
 */
class TractionDetector(
    /** Accélération au-delà de laquelle les roues patinent forcément (km/h par seconde). */
    private val spinKmhPerS: Double = 18.0,
) {
    /** Un patinage : instant, accélération mesurée, et intervention de l'antipatinage constatée ou non. */
    data class Event(val at: Long, val accel: Double, val intervention: Boolean = false)

    /** Patinages depuis la connexion, et le dernier. */
    data class State(val count: Int = 0, val last: Event? = null)

    private class Sample(val t: Long, val speed: Double, val rpm: Double?)

    private val samples = ArrayDeque<Sample>()
    private var lastRpm: Double? = null
    private var throttle: Pair<Long, Double>? = null
    private var load: Pair<Long, Double>? = null

    var state = State()
        private set

    fun reset() {
        samples.clear(); lastRpm = null; throttle = null; load = null
        state = State()
    }

    /** Nouvelles valeurs lues à l'instant [t] (ms) ; renvoie l'état, mis à jour s'il y a lieu. */
    fun feed(t: Long, values: Map<String, Double>): State {
        values["std0C"]?.let { lastRpm = it }
        values["std11"]?.let { throttle = t to it }
        values["std04"]?.let { load = t to it }
        values["std0D"]?.let { speed -> onSpeed(t, speed, values["std0C"] ?: lastRpm) }
        checkIntervention(t)
        return state
    }

    private fun onSpeed(t: Long, speed: Double, rpm: Double?) {
        samples.addLast(Sample(t, speed, rpm))
        while (samples.isNotEmpty() && t - samples.first().t > WINDOW_MAX) samples.removeFirst()
        // Accélération la plus forte par rapport aux mesures de 0,3 à 1,5 s plus tôt (une seule mesure ne suffit pas : bruit).
        var best = 0.0
        for (s in samples) {
            val dt = (t - s.t) / 1000.0
            if (dt * 1000 < WINDOW_MIN) continue
            val revving = rpm == null || s.rpm == null || rpm > s.rpm // régime qui monte : pas un capteur qui saute
            if (!revving) continue
            best = maxOf(best, (speed - s.speed) / dt)
        }
        if (speed < 5 || best < spinKmhPerS) return
        val last = state.last
        state = if (last != null && t - last.at < MERGE) {
            // Même patinage, qui continue : on garde la plus forte accélération.
            state.copy(last = last.copy(accel = maxOf(last.accel, best)))
        } else State(state.count + 1, Event(t, best))
    }

    private fun checkIntervention(t: Long) {
        val e = state.last ?: return
        if (e.intervention || t - e.at > MERGE) return
        val th = throttle ?: return
        val ld = load ?: return
        // Mesures récentes (prises après le patinage) : pied au plancher mais papillon refermé, moteur toujours en charge.
        if (th.first >= e.at && ld.first >= e.at - 500 && th.second <= 60.0 && ld.second >= 70.0) {
            state = state.copy(last = e.copy(intervention = true))
        }
    }

    private companion object {
        const val WINDOW_MIN = 300L
        const val WINDOW_MAX = 1500L
        /** Mesures à moins de 2 s l'une de l'autre : un seul et même patinage. */
        const val MERGE = 2000L
    }
}
