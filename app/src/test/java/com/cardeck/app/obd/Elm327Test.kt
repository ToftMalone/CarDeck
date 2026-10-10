package com.cardeck.app.obd

import com.cardeck.app.data.DtcStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Boîtier factice : renvoie une réponse préenregistrée pour chaque commande. */
private class FakeElm(private val answers: Map<String, String>) : ObdTransport {
    override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    override suspend fun open() {}
    override suspend fun send(data: ByteArray) {
        val cmd = String(data).trim()
        val r = answers[cmd] ?: "OK"
        incoming.trySend("$r\r\r>".toByteArray())
    }
    override fun close() {}
}

class Elm327Test {
    private val base = mapOf("ATZ" to "\r\rELM327 v1.5", "0100" to "SEARCHING...\r41 00 BE 3F A8 13", "ATDPN" to "A6", "ATDP" to "AUTO, ISO 15765-4 (CAN 11/500)")

    private fun elm(extra: Map<String, String>) = Elm327(FakeElm(base + extra))

    @Test fun initEtProbe() = runBlocking {
        val e = elm(emptyMap())
        e.init()
        assertEquals("ELM327 v1.5", e.version)
        assertTrue(e.probe())
        assertEquals("A6", e.protocolNum)
    }

    @Test fun regimeEtVitesse() = runBlocking {
        val e = elm(mapOf("010C" to "41 0C 1A F8", "010D" to "41 0D 32"))
        assertEquals(1726.0, e.rpm()!!, 0.01)
        assertEquals(50, e.speed())
    }

    @Test fun contactCoupe() = runBlocking {
        val e = elm(mapOf("010C" to "UNABLE TO CONNECT"))
        assertNull(e.rpm())
    }

    @Test fun tension() = runBlocking {
        assertEquals(12.6, elm(mapOf("ATRV" to "12.6V")).voltage()!!, 0.001)
    }

    @Test fun vinMultiTrame() = runBlocking {
        val r = "014\r0: 49 02 01 54 53 4D\r1: 5A 43 33 33 53 30 30\r2: 30 31 32 33 34 35 36"
        assertEquals("TSMZC33S000123456", elm(mapOf("0902" to r)).vin())
    }

    @Test fun codesCanMonoTrame() = runBlocking {
        val e = elm(mapOf("03" to "7E8 06 43 02 03 01 04 20"))
        e.init(); e.probe()
        val (codes, ecus) = e.readDtcs("03")
        assertEquals(listOf("P0301", "P0420"), codes.map { it.code })
        assertEquals(DtcStatus.Stored, codes[0].status)
        assertEquals("Moteur", codes[0].ecu)
        assertEquals(setOf("7E8"), ecus)
    }

    @Test fun codesCanMultiTrameEtPlusieursCalculateurs() = runBlocking {
        val r = "7E8 10 0A 47 04 03 01 01 71\r7E8 21 C1 00 02 99 00 00 00\r7E9 02 47 00"
        val e = elm(mapOf("07" to r))
        e.init(); e.probe()
        val (codes, ecus) = e.readDtcs("07")
        assertEquals(listOf("P0301", "P0171", "U0100", "P0299"), codes.map { it.code })
        assertTrue(codes.all { it.status == DtcStatus.Pending })
        assertEquals(setOf("7E8", "7E9"), ecus)
    }

    @Test fun aucunCode() = runBlocking {
        val e = elm(mapOf("03" to "7E8 02 43 00"))
        e.init(); e.probe()
        assertTrue(e.readDtcs("03").first.isEmpty())
    }

    @Test fun donneesFigees() = runBlocking {
        val e = elm(mapOf("020200" to "42 02 00 03 01", "020C00" to "42 0C 00 21 70", "020D00" to "42 0D 00 40", "020500" to "42 05 00 83", "020400" to "42 04 00 78"))
        val (code, ff) = e.freezeFrame()!!
        assertEquals("P0301", code)
        assertEquals("64 km/h", ff[1])
        assertEquals("91 °C", ff[2])
    }

    @Test fun pidsSupportes() = runBlocking {
        val e = elm(mapOf("0120" to "41 20 80 00 00 01", "0140" to "41 40 00 00 00 00"))
        val sup = e.supportedPids()!!
        assertTrue(0x0C in sup && 0x0D in sup && 0x05 in sup)
        assertTrue(0x21 in sup)
        assertTrue(0x08 !in sup)
        assertTrue(0x40 in sup)
    }

    @Test fun pidsSupportesInjoignable() = runBlocking {
        val e = Elm327(FakeElm(mapOf("0100" to "UNABLE TO CONNECT")))
        assertNull(e.supportedPids())
    }

    @Test fun decodageStandard() {
        val rpm = com.cardeck.app.data.StdPids.all.first { it.request == "010C" }
        assertEquals(1726.0, rpm.decode(intArrayOf(0x1A, 0xF8)), 0.01)
        val temp = com.cardeck.app.data.StdPids.all.first { it.request == "0105" }
        assertEquals(91.0, temp.decode(intArrayOf(0x83)), 0.01)
        val maf = com.cardeck.app.data.StdPids.all.first { it.request == "0110" }
        assertEquals(12.34, maf.decode(intArrayOf(0x04, 0xD2)), 0.01)
        val trim = com.cardeck.app.data.StdPids.all.first { it.request == "0106" }
        assertEquals(0.0, trim.decode(intArrayOf(128)), 0.01)
    }

    @Test fun effacement() = runBlocking {
        assertTrue(elm(mapOf("04" to "44")).clearDtcs())
    }
}
