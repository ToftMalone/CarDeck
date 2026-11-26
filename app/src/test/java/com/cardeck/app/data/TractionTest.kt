package com.cardeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TractionTest {
    /** Rejoue des mesures (secondes, régime, charge, vitesse, papillon) comme le service de suivi les publie. */
    private fun play(vararg rows: DoubleArray): TractionDetector {
        val d = TractionDetector()
        for (r in rows) d.feed((r[0] * 1000).toLong(), mapOf("std0C" to r[1], "std04" to r[2], "std0D" to r[3], "std11" to r[4]))
        return d
    }

    private fun r(t: Double, rpm: Double, load: Double, speed: Double, thr: Double) = doubleArrayOf(t, rpm, load, speed, thr)

    @Test
    fun forteAccelerationSansPatinage() {
        // Reprise franche en 3e sans patinage : 12 à 13 km/h par seconde.
        val d = play(r(0.0, 2290.0, 99.0, 70.0, 84.7), r(0.9, 2716.0, 97.0, 81.0, 84.7), r(2.0, 2998.0, 95.0, 91.0, 84.7), r(2.8, 3263.0, 97.0, 100.0, 84.7))
        assertEquals(0, d.state.count)
        assertNull(d.state.last)
    }

    @Test
    fun patinageAvecAntipatinage() {
        // Vitesse qui bondit de 32 à 53 km/h en 0,4 s, puis papillon refermé à 43 % moteur en charge.
        val d = play(r(0.0, 1502.0, 74.5, 24.0, 84.7), r(0.7, 2192.0, 92.9, 32.0, 84.7), r(1.1, 3216.0, 89.0, 53.0, 42.8), r(1.6, 3286.0, 70.2, 47.0, 84.7))
        assertEquals(1, d.state.count)
        val e = d.state.last!!
        assertTrue("accélération ${e.accel}", e.accel > 40)
        assertTrue(e.intervention)
    }

    @Test
    fun patinageSansIntervention() {
        val d = play(r(0.0, 2300.0, 80.0, 35.0, 84.7), r(0.6, 2900.0, 82.0, 46.0, 84.7), r(1.2, 3300.0, 85.0, 52.0, 84.7))
        assertEquals(1, d.state.count)
        assertFalse(d.state.last!!.intervention)
    }

    @Test
    fun unSeulPatinageSurPlusieursMesures() {
        val d = play(r(0.0, 2192.0, 92.0, 32.0, 84.7), r(0.4, 3216.0, 89.0, 53.0, 84.7), r(1.0, 3286.0, 70.0, 47.0, 84.7), r(1.5, 4420.0, 89.0, 66.0, 84.7))
        assertEquals(1, d.state.count)
        // Puis un nouveau patinage plus tard : compté à part.
        d.feed(10_000, mapOf("std0C" to 2155.0, "std0D" to 31.0))
        d.feed(10_500, mapOf("std0C" to 2782.0, "std0D" to 48.0))
        assertEquals(2, d.state.count)
    }

    @Test
    fun capteurQuiSauteOuArret() {
        // Vitesse qui saute sans que le régime monte (capteur, embrayage enfoncé) : pas un patinage.
        assertEquals(0, play(r(0.0, 2500.0, 30.0, 40.0, 30.0), r(0.5, 2400.0, 30.0, 55.0, 30.0)).state.count)
        // Mesures trop rapprochées (moins de 0,3 s) : ignorées.
        assertEquals(0, play(r(0.0, 2000.0, 80.0, 30.0, 84.7), r(0.1, 2100.0, 80.0, 33.0, 84.7)).state.count)
        // Remise à zéro à la déconnexion.
        val d = play(r(0.0, 2300.0, 80.0, 35.0, 84.7), r(0.6, 2900.0, 82.0, 46.0, 84.7))
        d.reset()
        assertEquals(0, d.state.count)
    }
}
