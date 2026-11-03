package com.cardeck.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Évènement du journal du tableau de bord (texte traduit à l'affichage). */
enum class DriveEvent { Connected, Disconnected, ShiftAlert, SessionStart, SessionStop }

data class DriveLog(val t: Long, val event: DriveEvent, val value: Double? = null)

/**
 * État du tableau de bord, gardé par le ViewModel pour survivre aux changements d'écran :
 * historique d'une minute des données principales, journal et session de mesure (pointes).
 */
class DriveSession {
    /** Dernières valeurs, une par seconde, pour les courbes (60 points au maximum). */
    val history = mutableStateMapOf<String, List<Float>>()
    val log = mutableStateListOf<DriveLog>()

    var running by mutableStateOf(false)
        private set
    var startedAt by mutableLongStateOf(0L)
        private set
    var stoppedAt by mutableLongStateOf(0L)
        private set

    /** Pointes mesurées pendant la session (identifiant de donnée -> valeur maximale). */
    val peaks = mutableStateMapOf<String, Double>()

    private var wasConnected: Boolean? = null
    private var aboveShift = false

    fun sample(now: Long, values: Map<String, Double>, connected: Boolean, shiftRpm: Int) {
        if (connected != wasConnected) {
            if (wasConnected != null || connected) add(DriveLog(now, if (connected) DriveEvent.Connected else DriveEvent.Disconnected))
            wasConnected = connected
        }
        if (!connected) return
        TRACKED.forEach { id ->
            val v = values[id]?.takeUnless { it.isNaN() } ?: return@forEach
            history[id] = ((history[id] ?: emptyList()) + v.toFloat()).takeLast(SLOTS)
            if (running) peaks[id] = maxOf(peaks[id] ?: v, v)
        }
        val rpm = values["std0C"]
        if (rpm != null) {
            val above = rpm >= shiftRpm
            if (above && !aboveShift) add(DriveLog(now, DriveEvent.ShiftAlert, rpm))
            aboveShift = above
        }
    }

    fun start(now: Long) {
        peaks.clear()
        running = true
        startedAt = now
        add(DriveLog(now, DriveEvent.SessionStart))
    }

    fun stop(now: Long) {
        running = false
        stoppedAt = now
        add(DriveLog(now, DriveEvent.SessionStop))
    }

    fun clearPeaks() = peaks.clear()

    private fun add(l: DriveLog) {
        log.add(0, l)
        while (log.size > MAX_LOG) log.removeAt(log.lastIndex)
    }

    companion object {
        const val SLOTS = 60
        const val MAX_LOG = 30

        /** Données suivies dans l'historique : vitesse, régime, charge, pression collecteur, liquide de refroidissement. */
        val TRACKED = listOf("std0D", "std0C", "std04", "std0B", "std05")
    }
}
