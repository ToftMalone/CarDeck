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
    val sent = mutableListOf<String>()
    override suspend fun send(data: ByteArray) {
        val cmd = String(data).trim()
        sent += cmd
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

    @Test fun udsLectureDid() = runBlocking {
        val e = elm(mapOf("22B001" to "62 B0 01 12 34 56"))
        assertEquals(listOf(0x12, 0x34, 0x56), e.readDid(0xB001).toList())
    }

    @Test fun udsLectureDidMultiTrame() = runBlocking {
        val e = elm(mapOf("22B002" to "00B\r0: 62 B0 02 01 02 03\r1: 04 05 06 07 08 AA AA"))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), e.readDid(0xB002).toList())
    }

    @Test fun udsReponseNegative() = runBlocking {
        val e = elm(mapOf("2EB00101" to "7F 2E 33"))
        try { e.writeDid(0xB001, intArrayOf(1)); org.junit.Assert.fail() } catch (x: UdsError) { assertTrue(x.message!!.contains("sécurisé")) }
    }

    @Test fun udsReponseEnAttente() = runBlocking {
        val e = elm(mapOf("1003" to "7F 10 78\r50 03 00 32 01 F4"))
        e.extendedSession()
    }

    @Test fun udsEcritureLongueIsoTp() = runBlocking {
        val fake = FakeElm(base + mapOf(
            "100A2EB001010203" to "30 00 00 00 00 00 00 00",
            "2104050607000000" to "03 6E B0 01",
        ))
        val e = Elm327(fake)
        e.writeDid(0xB001, intArrayOf(1, 2, 3, 4, 5, 6, 7))
        assertTrue(fake.sent.containsAll(listOf("ATCAF0", "100A2EB001010203", "2104050607000000", "ATCAF1")))
    }

    @Test fun codageApplique() {
        val d = com.cardeck.app.data.CodingDef("t", "Confort", "Test", "", "BCM", "750", "758", 0xB001, 1, 0x0C,
            listOf(com.cardeck.app.data.CodingOption("A", 0x00), com.cardeck.app.data.CodingOption("B", 0x04)))
        val data = intArrayOf(0xFF, 0xF3, 0x00)
        assertEquals(0x00, d.valueIn(data))
        assertEquals(listOf(0xFF, 0xF7, 0x00), d.applyTo(data, 0x04).toList())
    }

    @Test fun categoriesPids() {
        val all = com.cardeck.app.data.StdPids.all
        assertTrue(all.all { it.category in com.cardeck.app.data.StdPids.CATEGORIES })
        assertEquals("std0C", com.cardeck.app.data.pidById("std0C")!!.id)
    }

    @Test fun catalogueComplet() {
        val S = com.cardeck.app.data.StdPids
        val defs = S.forSupported(setOf(0x01, 0x0C, 0x14, 0x20, 0x3C, 0x99))
        val ids = defs.map { it.id }
        assertTrue("std0C" in ids && "std01" in ids && "std01b" in ids && "std14" in ids && "std14b" in ids && "std3C" in ids)
        assertTrue("std20" !in ids)
        val raw = defs.first { it.id == "std99" }
        assertEquals(S.RAW, raw.category)
        assertEquals("std99", com.cardeck.app.data.pidById("std99")!!.id)
        assertEquals("gps_alt", com.cardeck.app.data.pidById("gps_alt")!!.id)
    }

    @Test fun lectureGroupee() = runBlocking {
        val fake = FakeElm(base + mapOf("0101" to "41 01 83 07 65 00", "0114" to "41 14 5A FF"))
        val e = Elm327(fake)
        val S = com.cardeck.app.data.StdPids
        val out = e.readAll(S.forSupported(setOf(0x01, 0x14))).toMap()
        assertEquals(1.0, out.getValue("std01"), 0.0)
        assertEquals(3.0, out.getValue("std01b"), 0.0)
        assertEquals(0.45, out.getValue("std14"), 0.001)
        assertTrue("std14b" !in out) // correction non utilisée (0xFF)
        assertEquals(1, fake.sent.count { it == "0101" })
    }

    @Test fun effacement() = runBlocking {
        assertTrue(elm(mapOf("04" to "44")).clearDtcs())
    }

    private fun std(vararg ids: String) = ids.map { id -> com.cardeck.app.data.StdPids.all.first { it.id == id } }

    @Test fun reponseRapideSiAcceptee() = runBlocking {
        val fake = FakeElm(base + mapOf("01001" to "41 00 BE 3F A8 13", "010C1" to "41 0C 1A F8"))
        val e = Elm327(fake)
        e.init(); e.probe()
        assertTrue(e.fast)
        assertEquals(1726.0, e.rpm()!!, 0.01)
        assertTrue("010C1" in fake.sent)
    }

    @Test fun pidsGroupesSansReponseRapide() = runBlocking {
        // Réponse multi-trames à une requête de 3 PIDs : 41 0C xx xx 0D xx 05 xx
        val fake = FakeElm(base + mapOf("01001" to "NO DATA", "010C0D05" to "008\r0: 41 0C 1A F8 0D 32\r1: 05 5A"))
        val e = Elm327(fake)
        e.init(); e.probe()
        val v = e.readAll(std("std0C", "std0D", "std05")).toMap()
        assertEquals(1726.0, v.getValue("std0C"), 0.01)
        assertEquals(50.0, v.getValue("std0D"), 0.01)
        assertEquals(50.0, v.getValue("std05"), 0.01)
        assertEquals(1, fake.sent.count { it.startsWith("010") && it != "0100" && it != "01001" })
    }

    @Test fun lectureSeuleBloqueEcritureEtEffacement() = runBlocking {
        val fake = FakeElm(base + mapOf("04" to "44"))
        val e = Elm327(fake)
        e.readOnly = true
        assertTrue(!e.clearDtcs())
        assertTrue("04" !in fake.sent)
        e.raw("2EB00101")
        e.raw("1003")
        assertTrue(fake.sent.none { it.startsWith("2E") || it == "1003" })
        e.raw("1081"); e.raw("2100"); e.raw("22F190")
        assertTrue(fake.sent.containsAll(listOf("1081", "2100", "22F190")))
    }

    @Test fun calculateur7F1JamaisInterroge() = runBlocking {
        val fake = FakeElm(base)
        val e = Elm327(fake)
        e.raw("ATSH7F1"); e.raw("ATCRA7F9"); e.raw("ATPP0CSV00")
        assertTrue(fake.sent.isEmpty())
    }

    @Test fun protocoleDuModeleRefuseRetourAuto() = runBlocking {
        // Avec ATSP6 le véhicule ne répond pas, puis répond après ATSP0.
        val answers = base.toMutableMap()
        val fake = object : ObdTransport {
            override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
            var auto = false
            override suspend fun open() {}
            override suspend fun send(data: ByteArray) {
                val cmd = String(data).trim()
                if (cmd == "ATSP0") auto = true
                val r = if (cmd == "0100" && !auto) "UNABLE TO CONNECT" else answers[cmd] ?: "OK"
                incoming.trySend("$r\r\r>".toByteArray())
            }
            override fun close() {}
        }
        val e = Elm327(fake)
        e.init("6")
        assertTrue(e.probe())
    }

    @Test fun huileSwiftParBlocConstructeur() = runBlocking {
        // Réponse 61 00 + données ; octet 10 de la réponse (écho compris) = 0x7D → 125 − 40 = 85 °C.
        val bloc = "00C\r0: 61 00 01 02 03 04\r1: 05 06 07 08 7D 0A 0B"
        val fake = FakeElm(base + mapOf("2100" to bloc, "1081" to "50 81"))
        val e = Elm327(fake)
        e.readOnly = true
        val oil = com.cardeck.app.data.SWIFT_SPORT_ZC33S.pids.first { it.id == "std5C" }
        val v = e.readAll(listOf(oil)).toMap()
        assertEquals(85.0, v.getValue("std5C"), 0.01)
        assertEquals(listOf("ATSH7E0", "1081", "2100", "ATSH7DF"), fake.sent)
    }
}
