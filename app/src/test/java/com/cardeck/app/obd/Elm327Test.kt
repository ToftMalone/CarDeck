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

/** Boîtier factice dont les réponses dépendent de l'en-tête courant (ATSH) : un calculateur par adresse. */
private class BusFake(private val ecus: Map<String, Map<String, String>>) : ObdTransport {
    override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    override suspend fun open() {}
    val sent = mutableListOf<String>()
    private var header = "7DF"
    override suspend fun send(data: ByteArray) {
        val cmd = String(data).trim()
        sent += cmd
        if (cmd.startsWith("ATSH")) header = cmd.removePrefix("ATSH")
        val r = when {
            cmd == "ATDPN" -> "A6"
            cmd.startsWith("AT") -> "OK"
            else -> ecus[header]?.get(cmd) ?: "NO DATA"
        }
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
        // 0x20 : bitmap de support, 0x99 : inconnu du catalogue, 0x01 / 0x03 / 0x13 / 0x1C / 0x42 / 0x67 / 0x49 : plus lus.
        val defs = S.forSupported(setOf(0x01, 0x0C, 0x14, 0x20, 0x3C, 0x99, 0x03, 0x13, 0x1C, 0x42, 0x67, 0x49, 0x63))
        val ids = defs.map { it.id }
        assertEquals(listOf("std0C", "std14", "std14b"), ids)
        assertTrue("std2E" in S.forSupported(setOf(0x2E)).map { it.id })
        // Données retirées ou inconnues : jamais lues, jamais retrouvées.
        for (id in listOf("std42", "std99", "std01", "std63", "std30", "std2C", "elm_volt", "std5B", "std3C")) assertEquals(id, null, com.cardeck.app.data.pidById(id))
        assertEquals("std0C", com.cardeck.app.data.pidById("std0C")!!.id)
    }

    @Test fun donneesLues() {
        val S = com.cardeck.app.data.StdPids
        val all = S.all.map { it.id }.toSet() + com.cardeck.app.data.SWIFT_SPORT_ZC33S.pids.map { it.id }
        val wanted = setOf(
            // À chaque tour
            "std0D", "std0C", "std04", "std0E", "std0B", "std10", "std11", "std33",
            // 1 tour sur 3
            "std43", "std61", "std62", "std8E", "std05", "std0F", "std84", "std45", "std47", "std48", "std4C",
            "std06", "std07", "std08", "std09", "std44", "std0A", "std22", "std23", "std59", "std5D", "std5E", "stdA2", "std2E",
            // 1 tour sur 6
            "std2F", "std52", "std46", "stdA6", "std1F", "std21", "std5C",
        )
        assertTrue("Données manquantes : ${wanted - all}", all.containsAll(wanted))
        // Sondes lambda : toutes (à tension, large bande, corrections secondaires).
        assertTrue(all.containsAll(listOf("std14", "std14b", "std24", "std24b", "std34", "std34b", "std55")))
        val extra = all - wanted - setOf("swift_fuel_l", "swift_odo") - all.filter { Regex("^std(1[4-9A-B]|2[4-9A-B]|3[4-9A-B]|5[5-8])b?$").matches(it) }.toSet()
        assertEquals("Données en trop : $extra", emptySet<String>(), extra)
        val slow = com.cardeck.app.data.StdPids.all.filter { it.slow }.map { it.id }.toSet() + com.cardeck.app.data.SWIFT_SPORT_ZC33S.pids.filter { it.slow }.map { it.id }
        assertEquals(setOf("std2F", "std52", "std46", "stdA6", "std1F", "std21", "std5C", "swift_fuel_l", "swift_odo"), slow)
    }

    @Test fun lectureGroupee() = runBlocking {
        val fake = FakeElm(base + mapOf("0114" to "41 14 5A FF"))
        val e = Elm327(fake)
        val S = com.cardeck.app.data.StdPids
        val out = e.readAll(S.forSupported(setOf(0x14))).toMap()
        assertEquals(0.45, out.getValue("std14"), 0.001)
        assertTrue("std14b" !in out) // correction non utilisée (0xFF)
        assertEquals(1, fake.sent.count { it == "0114" })
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

    @Test fun adressesReserveesJamaisInterrogees() = runBlocking {
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

    @Test fun groupesDuBus() {
        // 0x1AC + 0x1B8 : exemple éprouvé ATCF1A8 / ATCM7EB.
        assertEquals(0x7EB, Elm327.busMask(listOf(0x1AC, 0x1B8)))
        assertEquals(listOf(listOf(0x1AC, 0x1B8), listOf(0x3B9)), Elm327.busGroups(listOf(0x3B9, 0x1B8, 0x1AC)))
    }

    @Test fun carburantParEcouteDuBus() = runBlocking {
        val fake = FakeElm(base + mapOf("ATMA" to "3B9 00 00 00 00 F0 C0"))
        val e = Elm327(fake)
        e.init(); e.probe()
        val swift = com.cardeck.app.data.SWIFT_SPORT_ZC33S.pids
        val v = e.readAll(swift.filter { it.id == "std2F" }).toMap()
        assertEquals(80.0, v.getValue("std2F"), 0.01)
        assertTrue("ATH0" == fake.sent.last())
    }

    @Test fun balayageDesCalculateurs() = runBlocking {
        val bus = BusFake(mapOf(
            "7E0" to mapOf("3E00" to "7E8 03 7F 3E 12"),
            "79B" to mapOf("3E00" to "7AB 02 7E 00"),
            "7AB" to mapOf("3E00" to "7BB 02 7E 00"),
            "798" to mapOf("1001" to "7A8 06 50 01 00 32 01 F4"),
        ))
        val e = Elm327(bus)
        e.init(); e.probe()
        assertEquals(listOf(0x798 to 0x7A8, 0x79B to 0x7AB, 0x7E0 to 0x7E8), e.findEcus())
        // Jamais les adresses réservées, ni une adresse de réponse déjà trouvée.
        assertTrue(bus.sent.none { it == "ATSH7F1" || it == "ATSH7F9" || it == "ATSH7AB" || it == "ATSH7E8" })
    }

    @Test fun codesDunCalculateurUds() = runBlocking {
        val r = "00F\r0: 59 02 FF 90 01 23 09\r1: C1 23 00 08 81 11 22\r2: 40 00 00"
        val e = Elm327(BusFake(mapOf("79B" to mapOf("1902FF" to r))))
        val codes = e.ecuDtcs(0x79B, 0x7AB, "Module")!!
        assertEquals(listOf("B1001", "U0123"), codes.map { it.code })
        assertTrue(codes.all { it.ecu == "Module" && it.status == DtcStatus.Stored })
    }

    @Test fun codesDunCalculateurKwp() = runBlocking {
        val e = Elm327(BusFake(mapOf("7E0" to mapOf("1802FF00" to "06 58 02 05 15 E0 0A 5A 20".split(" ").drop(1).joinToString(" ")))))
        val codes = e.ecuDtcs(0x7E0, 0x7E8, "Moteur")!!
        assertEquals(listOf("P0515", "P0A5A"), codes.map { it.code })
        assertEquals(listOf(DtcStatus.Stored, DtcStatus.Pending), codes.map { it.status })
        assertNull(Elm327(BusFake(emptyMap())).ecuDtcs(0x776, 0x786, "Radar"))
    }

    @Test fun codesSansEnTetes() = runBlocking {
        // Boîtier qui ignore ATH1 : réponse brute, sans en-tête ni octet de longueur.
        val e = elm(mapOf("03" to "43 02 05 15 0A 5A"))
        e.init(); e.probe()
        val (codes, ecus) = e.readDtcs("03")
        assertEquals(listOf("P0515", "P0A5A"), codes.map { it.code })
        assertEquals("Moteur", codes[0].ecu)
        assertEquals(setOf("7E8"), ecus)
    }

    @Test fun codesSansNombreDeCodes() = runBlocking {
        // Calculateur qui omet le nombre de codes après 43.
        val e = elm(mapOf("03" to "7E8 05 43 05 15 0A 5A"))
        e.init(); e.probe()
        assertEquals(listOf("P0515", "P0A5A"), e.readDtcs("03").first.map { it.code })
    }

    @Test fun codesMoteurAdresseDirectement() = runBlocking {
        // Le moteur ne répond qu'aux demandes qui lui sont adressées (7E0), pas à la diffusion (7DF).
        val bus = BusFake(mapOf("7E0" to mapOf("0100" to "41 00 BE 3F A8 13", "03" to "7E8 06 43 02 05 15 0A 5A")))
        val e = Elm327(bus)
        assertTrue(e.readDtcs("03").first.isEmpty())
        val (codes, _) = e.readDtcs("03", 0x7E0, 0x7E8)
        assertEquals(listOf("P0515", "P0A5A"), codes.map { it.code })
        assertEquals("ATSH7DF", bus.sent.last { it.startsWith("ATSH") })
    }

    @Test fun journalDuScan() = runBlocking {
        val e = elm(mapOf("03" to "7E8 06 43 02 05 15 0A 5A"))
        val log = StringBuilder()
        e.trace = log
        assertTrue(e.reset())
        e.readDtcs("03")
        e.trace = null
        assertTrue(log.contains("> ATZ"))
        assertTrue(log.contains("> 03\n7E8 06 43 02 05 15 0A 5A"))
        assertTrue(log.contains("# 03 (diffusé) : P0515 (Moteur), P0A5A (Moteur)"))
    }

    @Test fun codesKwpMemorisesFiltresParEtat() = runBlocking {
        // 18 00 FF 00 : codes mémorisés ; un code d'état nul (non présent, non mémorisé) est ignoré.
        val bus = BusFake(mapOf("7E0" to mapOf("1800FF00" to "58 02 05 15 E0 0A 5A 00")))
        val codes = Elm327(bus).ecuDtcs(0x7E0, 0x7E8, "Moteur")!!
        assertEquals(listOf("P0515"), codes.map { it.code })
    }

    // ------------------------------------------------------------------ Diagnostic : tous protocoles

    private fun proto(num: String, extra: Map<String, String>): Pair<Elm327, FakeElm> {
        val f = FakeElm(base + mapOf("ATDPN" to num, "01001" to "NO DATA") + extra)
        return Elm327(f) to f
    }

    @Test fun codesKLineSansEnTeteCan() = runBlocking {
        // ISO 9141-2 (K-Line) : en-tête 48 6B 10, trois codes par trame, octet de contrôle final.
        val (e, f) = proto("A3", mapOf("03" to "48 6B 10 43 01 33 05 15 00 00 7A"))
        e.init(); assertTrue(e.probe())
        val (codes, ecus) = e.readDtcs("03")
        assertEquals(listOf("P0133", "P0515"), codes.map { it.code })
        assertTrue(codes.all { it.ecu == "Moteur" })
        assertEquals(setOf("10"), ecus)
        // Jamais d'en-tête CAN 11 bits sur un protocole K-Line.
        assertTrue(f.sent.none { it.startsWith("ATSH") || it.startsWith("ATCRA") })
        // Pas d'adressage direct, pas de balayage CAN, pas d'interrogation UDS/KWP.
        assertTrue(e.readDtcs("03", 0x7E0, 0x7E8).first.isEmpty())
        assertTrue(e.findEcus().isEmpty())
        assertNull(e.ecuDtcs(0x7E0, 0x7E8, "Moteur"))
        assertTrue(f.sent.none { it.startsWith("ATSH") || it == "3E00" || it.startsWith("19") })
    }

    @Test fun codesKLineSansNomDeCalculateur() = runBlocking {
        // Boîtier qui ignore ATH1 sur K-Line : réponse sans en-tête.
        val (e, _) = proto("A4", mapOf("07" to "47 03 00 00 00 00 00"))
        e.init(); e.probe()
        val (codes, _) = e.readDtcs("07")
        assertEquals(listOf("P0300"), codes.map { it.code })
        assertEquals("Moteur", codes[0].ecu)
    }

    @Test fun codesCan29Bits() = runBlocking {
        val r = "18 DA F1 10 06 43 02 01 33 05 15\r18 DA F1 18 02 43 00"
        val (e, f) = proto("A7", mapOf("03" to r))
        e.init(); e.probe()
        val (codes, ecus) = e.readDtcs("03")
        assertEquals(listOf("P0133", "P0515"), codes.map { it.code })
        assertEquals("Moteur", codes[0].ecu)
        assertEquals(setOf("18DAF110", "18DAF118"), ecus)
        assertTrue(f.sent.none { it.startsWith("ATSH") })
        assertTrue(e.findEcus().isEmpty())
    }

    @Test fun memeCodeDansPlusieursCalculateurs() = runBlocking {
        val (e, _) = proto("A6", mapOf("03" to "7E8 04 43 01 C1 00\r7E9 04 43 01 C1 00\r7EA 04 43 01 C1 00"))
        e.init(); e.probe()
        val codes = e.readDtcs("03").first
        assertEquals(listOf("Moteur", "Boîte de vitesses", "Calculateur 7E2"), codes.map { it.ecu })
        assertTrue(codes.all { it.code == "U0100" })
    }

    @Test fun nomsDesCalculateursIdentiques() {
        // Même nom, que le calculateur réponde à une demande diffusée (réception seule) ou adressée (émission connue).
        assertEquals(Elm327.ecuLabel(null, 0x7E8), Elm327.ecuLabel(0x7E0, 0x7E8))
        assertEquals(Elm327.ecuLabel(null, 0x7E9), Elm327.ecuLabel(0x7E1, 0x7E9))
        assertEquals(Elm327.ecuLabel(null, 0x7EA), Elm327.ecuLabel(0x7E2, 0x7EA))
        assertEquals("Calculateur 79B", Elm327.ecuLabel(0x79B, 0x7AB))
    }

    @Test fun controleDeFluxVersLeCalculateurInterroge() = runBlocking {
        // Réponse à 7AB (pas 79B + 8) : le contrôle de flux doit partir vers 79B, puis le boîtier revient au mode automatique.
        val r = "00F\r0: 59 02 FF 90 01 23 09\r1: C1 23 00 08 81 11 22\r2: 40 00 00"
        val bus = BusFake(mapOf("79B" to mapOf("1902FF" to r)))
        val e = Elm327(bus)
        e.init(); e.probe()
        e.ecuDtcs(0x79B, 0x7AB, "Calculateur 79B")
        val i = bus.sent.indexOf("1902FF")
        assertTrue(bus.sent.subList(0, i).containsAll(listOf("ATFCSH79B", "ATFCSD300000", "ATFCSM1", "ATCRA7AB")))
        assertEquals("ATFCSM0", bus.sent.last { it.startsWith("ATFCSM") })
    }

    @Test fun controleDeFluxSwiftConserve() = runBlocking {
        // Swift : réglages du modèle (FCSD300005, FCSM1 vers 7E0) rejoués après chaque calculateur.
        val swift = com.cardeck.app.data.SWIFT_SPORT_ZC33S
        val bus = BusFake(mapOf("7B0" to mapOf("1902FF" to "59 02 FF")))
        val e = Elm327(bus)
        e.readOnly = true
        e.init(swift.elmProtocol, swift.elmInit)
        e.probe()
        assertTrue(e.ecuDtcs(0x7B0, 0x7B8, "Calculateur 7B0")!!.isEmpty())
        assertTrue("ATFCSD300005" in bus.sent && "ATFCSD300000" !in bus.sent)
        assertEquals(swift.elmInit, bus.sent.takeLast(3))
    }

    @Test fun scanSwiftLectureSeule() = runBlocking {
        // Scan complet en lecture seule : aucune commande hors liste blanche n'est envoyée.
        val swift = com.cardeck.app.data.SWIFT_SPORT_ZC33S
        val bus = BusFake(mapOf(
            "7DF" to mapOf("0100" to "41 00 BE 3F A8 13", "03" to "NO DATA", "07" to "NO DATA"),
            "7E0" to mapOf("03" to "7E8 02 43 00", "3E00" to "7E8 03 7F 3E 11", "1800FF00" to "58 02 05 15 E0 0A 5A 20"),
        ))
        val e = Elm327(bus)
        e.readOnly = true
        e.init(swift.elmProtocol, swift.elmInit)
        assertTrue(e.reset())
        e.readDtcs("03"); e.readDtcs("07"); e.readDtcs("0A")
        e.readDtcs("03", 0x7E0, 0x7E8)
        e.freezeFrame()
        val found = e.findEcus()
        assertEquals(listOf(0x7E0 to 0x7E8), found)
        val codes = e.ecuDtcs(0x7E0, 0x7E8, Elm327.ecuLabel(0x7E0, 0x7E8))!!
        assertEquals(listOf("P0515", "P0A5A"), codes.map { it.code })
        assertTrue(codes.all { it.ecu == "Moteur" })
        val services = bus.sent.filter { !it.startsWith("AT") }.map { it.take(2) }.toSet()
        assertTrue("Services envoyés : $services", services.all { it in setOf("01", "02", "03", "07", "0A", "10", "18", "19", "3E") })
        assertTrue(bus.sent.none { it == "04" || it.startsWith("14") || it.startsWith("2E") || it.startsWith("31") || it == "1003" })
        assertTrue(bus.sent.none { it == "ATSH7F1" || it == "ATCRA7F9" })
    }

    @Test fun ficheDuCodeBatterie() {
        val d = com.cardeck.app.data.DtcCatalog.info("P0515")
        assertTrue(d.known)
        assertEquals("Code spécifique au constructeur", com.cardeck.app.data.DtcCatalog.info("B1234").title)
        assertEquals("Code spécifique au constructeur", com.cardeck.app.data.DtcCatalog.info("U2100").title)
        assertEquals("Code non répertorié dans la base", com.cardeck.app.data.DtcCatalog.info("B0001").title)
    }

    @Test fun calculateursClioV29Bits() = runBlocking {
        // Voiture OBD en CAN 29 bits (Clio V) : ABS en 18DA2DF1 (réponse en plusieurs trames), calculateur 11 bits 714.
        val bus = object : ObdTransport {
            override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
            override suspend fun open() {}
            val sent = mutableListOf<String>()
            var header = ""
            override suspend fun send(data: ByteArray) {
                val cmd = String(data).trim()
                sent += cmd
                if (cmd.startsWith("ATSH")) header = cmd.removePrefix("ATSH")
                val r = when {
                    cmd == "ATDPN" -> "A7"
                    cmd.startsWith("AT") -> "OK"
                    cmd == "0100" -> "41 00 BE 3F A8 13"
                    header == "DA2DF1" && cmd == "1902FF" -> "00B\r0: 59 02 09 50 6E 54\r1: 08 E1 54 87 08"
                    header == "714" && cmd == "1902FF" -> "59 02 39 9F 34 87 28"
                    else -> "NO DATA"
                }
                incoming.trySend("$r\r\r>".toByteArray())
            }
            override fun close() {}
        }
        val e = Elm327(bus)
        e.init(); e.probe()
        val clio = com.cardeck.app.data.CLIO_V_TCE90
        e.ecuNames = clio.ecuNames
        val abs = clio.ecus.first { it.name == "ABS / ESP" }
        val codes = e.ecuDtcs(abs.tx, abs.rx, abs.label, abs.wide)!!
        assertEquals(listOf("C106E", "U2154"), codes.map { it.code })
        assertTrue(codes.all { it.ecu == "ABS / ESP" && it.status == DtcStatus.Stored })
        // En-têtes 29 bits, sans changer de protocole, puis retour à l'en-tête de diffusion OBD 29 bits.
        assertTrue(bus.sent.containsAll(listOf("ATCP18", "ATSHDA2DF1", "ATCRA18DAF12D", "ATFCSH18DA2DF1")))
        assertTrue(bus.sent.none { it.startsWith("ATSP6") || it == "ATSH7DF" })
        assertEquals("ATSHDB33F1", bus.sent.last { it.startsWith("ATSH") })
        // Calculateur 11 bits sur cette voiture : passage en CAN 11 bits (même débit) puis retour au 29 bits.
        bus.sent.clear()
        val c714 = clio.ecus.first { it.tx == 0x714 }
        assertEquals(listOf("B1F34"), e.ecuDtcs(c714.tx, c714.rx, c714.label, c714.wide)!!.map { it.code })
        assertEquals(listOf("ATSP6", "ATSP7"), bus.sent.filter { it.startsWith("ATSP") })
        assertTrue(bus.sent.indexOf("ATSP6") < bus.sent.indexOf("ATSH714"))
        assertEquals("ATSHDB33F1", bus.sent.last { it.startsWith("ATSH") })
        // Codes OBD diffusés : le moteur 18DAF115 porte son nom.
        val fake = FakeElm(base + mapOf("ATDPN" to "A7", "03" to "18 DA F1 15 04 43 01 01 33"))
        val e2 = Elm327(fake)
        e2.init(); e2.probe()
        e2.ecuNames = clio.ecuNames
        assertEquals(listOf("Moteur"), e2.readDtcs("03").first.map { it.ecu })
    }
}
