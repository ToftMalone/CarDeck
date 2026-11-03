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

class TripMergeTest {
    private fun trip(id: Long, start: Long, minutes: Int, km: Double, max: Double, from: String, to: String, price: Double? = null, vehicle: Long = 1) =
        Trip(id, vehicle, start, start + minutes * 60_000L, km, km / (minutes / 60.0), max, from, to, if (price != null) FuelType.SP98 else null, price, if (price != null) "station" else null)

    @Test fun fusionBoutABout() {
        val a = trip(1, 1_000_000, 60, 60.0, 130.0, "Reims", "Charleville", price = 2.0)
        val b = trip(2, 1_000_000 + 5 * 3_600_000L, 30, 40.0, 150.0, "Charleville", "Sedan", price = 2.4)
        val p = TripMerge.plan(listOf(b, a))!!
        val m = p.merged
        assertEquals(listOf(a, b), p.sources)
        assertEquals(1_000_000L, m.start)
        assertEquals(90 * 60_000L, m.end - m.start)
        assertEquals(100.0, m.distanceKm, 0.001)
        assertEquals(100.0 / 1.5, m.avgKmh, 0.001)
        assertEquals(150.0, m.maxKmh, 0.001)
        assertEquals("Reims", m.from)
        assertEquals("Sedan", m.to)
        // Prix moyen pondéré par la distance : (2,0 × 60 + 2,4 × 40) / 100.
        assertEquals(2.16, m.fuelPrice!!, 0.0001)
        assertEquals("moyenne des prix des trajets fusionnés", m.fuelPriceInfo)
        // Le second trajet est ramené juste après le premier : plus de pause entre les deux.
        assertEquals(listOf(0L, -(5 * 3_600_000L - 60 * 60_000L)), p.shifts)
    }

    @Test fun refusSiImpossible() {
        val a = trip(1, 0, 10, 5.0, 50.0, "A", "B")
        assertNull(TripMerge.plan(listOf(a)))
        assertNull(TripMerge.plan(listOf(a, trip(2, 100_000_000, 10, 5.0, 50.0, "B", "C", vehicle = 2))))
    }

    @Test fun memePrixGarde() {
        val p = TripMerge.plan(listOf(trip(1, 0, 10, 5.0, 50.0, "A", "B", price = 2.1), trip(2, 10_000_000, 10, 5.0, 60.0, "B", "C", price = 2.1)))!!
        assertEquals("station", p.merged.fuelPriceInfo)
        assertNull(TripMerge.plan(listOf(trip(1, 0, 10, 5.0, 50.0, "A", "B"), trip(2, 10_000_000, 10, 5.0, 60.0, "B", "C")))!!.merged.fuelPrice)
    }
}
