package com.cardeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripFuelTest {
    /** Mesures toutes les [stepMs] pendant [seconds] s, valeur constante. */
    private fun steady(v: Double, seconds: Int, stepMs: Long = 500) = (0..seconds * 1000 / stepMs.toInt()).map { Sample(it * stepMs, v) }

    @Test fun debitAirConstant() {
        // 10 g/s d'air pendant 1 h, SP98 : 10 / 14,7 / 745 × 3600 = 3,287 L.
        val l = TripFuel.liters(mapOf("std10" to steady(10.0, 3600)), FuelType.SP98)!!
        assertEquals(3.287, l, 0.001)
    }

    @Test fun debitCarburantPrioritaire() {
        // PID 5E (6 L/h pendant 30 min) préféré au débit d'air.
        val l = TripFuel.liters(mapOf("std5E" to steady(6.0, 1800), "std10" to steady(50.0, 1800)), FuelType.SP98)!!
        assertEquals(3.0, l, 0.001)
    }

    @Test fun trouComble() {
        // 10 min de mesures, puis 2 min sans liaison, puis 10 min : le trou est compté au débit moyen.
        val a = steady(5.0, 600)
        val b = steady(5.0, 600).map { Sample(it.t + 720_000, it.v) }
        val l = TripFuel.liters(mapOf("std5E" to a + b), FuelType.E10)!!
        assertEquals(5.0 * 1320 / 3600, l, 0.001)
    }

    @Test fun sansDonnees() {
        assertNull(TripFuel.liters(emptyMap(), FuelType.SP98))
        assertNull(TripFuel.liters(mapOf("std10" to listOf(Sample(0, 10.0))), FuelType.SP98))
    }
}
