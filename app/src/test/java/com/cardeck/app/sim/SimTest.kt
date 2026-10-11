package com.cardeck.app.sim

import com.cardeck.app.data.SIMULATOR
import com.cardeck.app.data.StdPids
import com.cardeck.app.obd.Elm327
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le vrai protocole ELM327 de l'appli dialogue avec le boîtier simulé. */
class SimTest {
    @Test fun parcoursComplet() = runBlocking {
        val e = Elm327(SimTransport())
        e.init()
        assertTrue(e.probe())
        SimCar.stopEngine()
        assertEquals(0.0, e.rpm()!!, 0.1)
        SimCar.startEngine()
        assertTrue(e.rpm()!! > 500)

        val sup = e.supportedPids()!!
        assertTrue(0x0C in sup && 0xA6 in sup)
        val values = e.readAll(StdPids.forSupported(sup)).toMap()
        assertNotNull(values["stdA6"])
        assertNotNull(values["std05"])

        // PID constructeur (en-tête 7E0)
        val boost = e.readAll(SIMULATOR.pids).toMap()
        assertNotNull(boost["sim_boost"])

        assertEquals("CARDECKSIM0000001", e.vin())

        SimCar.clearFaults()
        repeat(3) { SimCar.injectFault() }
        val (codes, ecus) = e.readDtcs("03")
        assertEquals(3, codes.size)
        assertEquals(setOf("7E8"), ecus)
        assertNotNull(e.freezeFrame())
        assertTrue(e.clearDtcs())
        assertTrue(e.readDtcs("03").first.isEmpty())
        SimCar.stopEngine()
    }

    @Test fun codage() = runBlocking {
        val e = Elm327(SimTransport())
        e.init()
        e.udsOpen("750", "758")
        val before = e.readDid(0x0101)
        val def = SIMULATOR.codings.first { it.id == "sim_windows" }
        val after = def.applyTo(before, 0x01)
        e.extendedSession()
        e.writeDid(0x0101, after)
        assertEquals(after.toList(), e.readDid(0x0101).toList())
        e.writeDid(0x0101, before)
        e.defaultSession()
        e.udsClose()
    }
}
