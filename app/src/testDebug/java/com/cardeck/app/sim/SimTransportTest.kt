package com.cardeck.app.sim

import com.cardeck.app.data.DtcStatus
import com.cardeck.app.data.Readiness
import com.cardeck.app.data.ReadinessVerdict
import com.cardeck.app.obd.Elm327
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le simulateur dialogue avec le vrai [Elm327] de CarDeck : chaque réponse doit être comprise comme celle d'une vraie voiture. */
class SimTransportTest {
    private var now = 1_000_000L
    private val car = SimCar { now }

    private fun elm() = Elm327(SimTransport(car, latency = false))

    @Test fun connexionEtMesures() = runBlocking {
        val e = elm()
        e.init("6")
        assertTrue(e.probe())
        assertEquals("ELM327 v1.5", e.version)
        assertTrue(e.isCan && e.is11bit)
        now += 15_000 // en pleine accélération
        val rpm = e.rpm()!!
        assertTrue("régime $rpm", rpm in 1300.0..6200.0)
        assertTrue((e.speed() ?: 0) > 0)
        val sup = e.supportedPids()!!
        assertTrue(sup.containsAll(listOf(0x0C, 0x0D, 0x05, 0x10, 0x31, 0x42, 0x5C)))
        assertTrue(e.voltage()!! > 13.5)
    }

    @Test fun lectureGroupee() = runBlocking {
        val e = elm()
        e.init("6"); e.probe()
        // Plusieurs PIDs dans une requête : réponse sur plusieurs trames, réassemblée.
        val defs = com.cardeck.app.data.StdPids.forSupported(setOf(0x04, 0x05, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F))
        val values = e.readAll(defs).toMap()
        assertTrue(values.keys.containsAll(listOf("std0C", "std0D", "std05")))
    }

    @Test fun vinEtBlocConstructeur() = runBlocking {
        val e = elm()
        e.init("6"); e.probe()
        assertEquals(car.vin, e.vin())
        // Huile de la Swift : octet 8 du bloc 21 00 du moteur.
        val b = e.query("2100", "7E0")!!
        assertEquals(210, b.size)
        assertTrue(b[8] - 40 in 40..130)
    }

    @Test fun defautsEtEffacement() = runBlocking {
        val e = elm()
        e.init("6"); e.probe()
        val (stored, ecus) = e.readDtcs("03")
        assertEquals(listOf("P0420"), stored.map { it.code })
        assertEquals("Moteur", stored.first().ecu)
        assertTrue("7E8" in ecus)
        assertEquals(listOf("P0171"), e.readDtcs("07").first.map { it.code })
        assertEquals(DtcStatus.Pending, e.readDtcs("07").first.first().status)
        assertEquals("P0420", e.freezeFrame()!!.first)
        // Voyant allumé : pas prêt pour le contrôle technique.
        assertEquals(ReadinessVerdict.MilOn, Readiness.from(e.pid01All(0x01))!!.verdict)
        // Effacement : plus de codes, tests antipollution à refaire.
        assertTrue(e.clearDtcs())
        assertTrue(e.readDtcs("03").first.isEmpty())
        val r = Readiness.from(e.pid01All(0x01))!!
        assertEquals(ReadinessVerdict.NotReady, r.verdict)
    }

    @Test fun calculateursEtAbs() = runBlocking {
        val e = elm()
        e.init("6"); e.probe()
        val found = e.findEcus()
        assertTrue(found.contains(0x7E0 to 0x7E8))
        assertTrue(found.contains(0x7B0 to 0x7B8))
        val abs = e.ecuDtcs(0x7B0, 0x7B8, "ABS")
        assertNotNull(abs)
        assertEquals(listOf("C0035"), abs!!.map { it.code })
        assertEquals(com.cardeck.app.obd.ClearResult.Cleared, e.clearEcu(0x7B0, 0x7B8).result)
        assertTrue(e.ecuDtcs(0x7B0, 0x7B8, "ABS")!!.isEmpty())
    }

    @Test fun ecouteDuBus() = runBlocking {
        val e = elm()
        e.init("6"); e.probe()
        val frames = e.monitor(listOf(listOf(0x3B9, 0x3BA) to 400L))
        assertTrue(frames[0x3B9].orEmpty().isNotEmpty())
    }

    @Test fun cliquetisEnCharge() {
        now += 14_000 // reprise en charge du cycle
        val st = car.state()
        assertTrue(st.load > 85)
        now = 1_000_000L + 40_000 // croisière
        assertTrue(car.state().knock.all { it == 0 })
    }

    @Test fun arretMoteurCoupe() = runBlocking {
        val e = elm()
        e.init("6"); e.probe()
        now = 1_000_000L + 380_000 // après 6 minutes de route : moteur coupé, contact mis
        assertEquals(0.0, e.rpm()!!, 0.1)
        assertEquals(0, e.speed())
        now = 1_000_000L + 450_000 // redémarré
        assertTrue(e.rpm()!! > 500)
    }
}
