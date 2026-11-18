package com.cardeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessTest {
    @Test fun essenceToutPret() {
        // A=00 (voyant éteint, 0 code), B=07 (3 moniteurs continus pris en charge et terminés), C=65 (catalyseur, EVAP, lambda, chauffage), D=00
        val r = Readiness.from(listOf(intArrayOf(0x00, 0x07, 0x65, 0x00)))!!
        assertFalse(r.diesel)
        assertEquals(ReadinessVerdict.Ready, r.verdict)
        assertEquals(listOf("Ratés d'allumage", "Système d'alimentation", "Composants du moteur", "Catalyseur", "Circuit des vapeurs d'essence", "Sondes lambda", "Chauffage des sondes lambda"), r.monitors.map { it.label })
    }

    @Test fun apresEffacement() {
        // Catalyseur et EVAP non terminés
        val r = Readiness.from(listOf(intArrayOf(0x00, 0x07, 0x65, 0x05)), kmSinceClear = 12, warmupsSinceClear = 1)!!
        assertEquals(ReadinessVerdict.NotReady, r.verdict)
        assertEquals(listOf("Catalyseur", "Circuit des vapeurs d'essence"), r.notReady.map { it.label })
        assertEquals(12, r.kmSinceClear)
    }

    @Test fun voyantAllume() {
        val r = Readiness.from(listOf(intArrayOf(0x82, 0x07, 0x01, 0x00)))!!
        assertTrue(r.mil)
        assertEquals(2, r.dtcCount)
        assertEquals(ReadinessVerdict.MilOn, r.verdict)
    }

    @Test fun dieselEtBoite() {
        // Moteur diesel (B bit 3) : filtre à particules (bit 6) non terminé ; la boîte répond sans moniteur non continu.
        val r = Readiness.from(listOf(intArrayOf(0x00, 0x00, 0x00, 0x00), intArrayOf(0x00, 0x0F, 0xC0, 0x40)))!!
        assertTrue(r.diesel)
        assertEquals(listOf("Filtre à particules"), r.notReady.map { it.label })
        assertTrue(r.monitors.any { it.label == "Recirculation des gaz (EGR / VVT)" && it.ready })
    }

    @Test fun continuIncomplet() {
        // B=17 : ratés d'allumage pris en charge (bit 0) et non terminés (bit 4)
        val r = Readiness.from(listOf(intArrayOf(0x00, 0x17, 0x00, 0x00)))!!
        assertEquals(listOf("Ratés d'allumage"), r.notReady.map { it.label })
    }

    @Test fun sansReponse() {
        assertNull(Readiness.from(emptyList()))
        assertNull(Readiness.from(listOf(intArrayOf(0x00, 0x07))))
    }
}
