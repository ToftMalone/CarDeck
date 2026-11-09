package com.cardeck.app.explorer

import com.cardeck.app.obd.Elm327
import com.cardeck.app.obd.ObdTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Trames ISO-TP (en-têtes affichés) d'une réponse [payload] du calculateur [rx]. */
private fun frames(rx: String, payload: String): String {
    val b = payload.split(" ").filter { it.isNotEmpty() }
    if (b.size <= 7) return "$rx %02X ".format(b.size) + b.joinToString(" ")
    val out = mutableListOf("$rx 1%01X %02X ".format(b.size shr 8, b.size and 0xFF) + b.take(6).joinToString(" "))
    var seq = 1
    for (chunk in b.drop(6).chunked(7)) out += "$rx 2%01X ".format(seq++ and 0x0F) + chunk.joinToString(" ")
    return out.joinToString("\r")
}

private fun text(s: String) = s.map { "%02X".format(it.code) }.joinToString(" ")

/** Calculateur factice : réponses aux requêtes (charge utile, sans PCI). */
private class FakeEcu(val rx: String, val answers: Map<String, String>, val unknown: (String) -> String?)

/** Voiture factice façon Swift : calculateurs à +0x10 (sauf le moteur), KWP et UDS. */
private class FakeCar(private val ecus: Map<String, FakeEcu>, private val broadcast: Map<String, String>) : ObdTransport {
    override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    override suspend fun open() {}
    val sent = mutableListOf<String>()
    private var header = "7DF"
    override suspend fun send(data: ByteArray) {
        val cmd = String(data).trim()
        sent += cmd
        val r = when {
            cmd == "ATZ" -> "ELM327 v1.5"
            cmd == "ATDPN" -> "A6"
            cmd == "ATDP" -> "AUTO, ISO 15765-4 (CAN 11/500)"
            cmd == "ATFI" || cmd == "ATSI" -> "BUS INIT: ...ERROR"
            cmd.startsWith("ATSH") -> { header = cmd.removePrefix("ATSH"); "OK" }
            cmd.startsWith("AT") -> "OK"
            else -> {
                val req = if (cmd.length % 2 == 1) cmd.dropLast(1) else cmd
                if (header == "7DF") broadcast[req]?.let { frames("7E8", it) } ?: "NO DATA"
                else ecus[header]?.let { e -> (e.answers[req] ?: e.unknown(req))?.let { frames(e.rx, it) } } ?: "NO DATA"
            }
        }
        incoming.trySend("$r\r\r>".toByteArray())
    }
    override fun close() {}
}

private class NoUi : ExplorerListener {
    val lines = mutableListOf<String>()
    override fun step(index: Int, total: Int, name: String) {}
    override fun detail(text: String?, progress: Float?) {}
    override fun line(text: String) { lines += text }
}

class ExplorerTest {
    @Test fun reassemblageVerifieLesSequences() {
        val ok = listOf("7E8 10 0A 62 F1 90 41 42 43", "7E8 21 44 45 46 47 00 00 00")
        val (m, missed) = Explore.reassemble(ok)
        assertEquals("62F190414243444546 47".replace(" ", ""), Explore.hex(m.getValue(0x7E8)[0]))
        assertTrue(missed.isEmpty())
        // Trame 1 perdue : le message est écarté, pas recollé avec un trou.
        val (m2, missed2) = Explore.reassemble(listOf("7E8 10 14 62 F1 90 41 42 43", "7E8 22 44 45 46 47 48 49 4A"))
        assertTrue(m2[0x7E8].isNullOrEmpty())
        assertTrue(0x7E8 in missed2)
        // 29 bits
        val (m3, _) = Explore.reassemble(listOf("18 DA F1 10 03 7E 00 00"), wide = true)
        assertEquals("7E0000", Explore.hex(m3.getValue(0x18DAF110)[0]))
    }

    @Test fun reponseTardiveEcartee() {
        val req = Explore.bytes("22F187")
        assertTrue(Explore.correspond(Explore.bytes("62F18731"), req))
        assertTrue(!Explore.correspond(Explore.bytes("62F19031"), req)) // réponse à la requête précédente
        assertTrue(Explore.correspond(Explore.bytes("7F2231"), req))
        val a = Explore.classify(mapOf(0x7E8 to listOf(Explore.bytes("7F2278"), Explore.bytes("62F18731"))), 0x7E8, req)
        assertTrue(a.ok)
        assertEquals(AnswerState.Attente, Explore.classify(mapOf(0x7E8 to listOf(Explore.bytes("7F2278"))), 0x7E8, req).state)
        assertEquals(0x31, Explore.classify(mapOf(0x7E8 to listOf(Explore.bytes("7F2231"))), 0x7E8, req).nrc)
    }

    @Test fun ligneK() {
        val (src, d) = Explore.kDecode("83 F1 40 5A 8A 31 C0")!!
        assertEquals(0x40, src)
        assertEquals("5A8A31", Explore.hex(d))
        assertNull(Explore.kDecode("48 6B 10 41 0C"))
    }

    @Test fun obdGroupe() {
        val v = Explore.decodeMode01(Explore.bytes("41 0C 1A F8 0D 32 05 5A".replace(" ", "")), listOf(0x0C, 0x0D, 0x05))
        assertEquals(1726.0, v.getValue("regime"), 0.01)
        assertEquals(50.0, v.getValue("vitesse"), 0.01)
        assertEquals(50.0, v.getValue("temp_eau"), 0.01)
    }

    @Test fun cartesMoteur() {
        val rows = (0 until 300).map { i -> mapOf("regime" to 2000.0 + (i % 2) * 10, "charge_calculee" to 50.0, "avance_allumage" to 20.0 + (i % 3), "pression_admission" to 100.0) }
        val maps = Explore.engineMaps(rows)
        assertTrue(maps.contains("### Avance à l'allumage, par charge (°)"))
        assertTrue(maps.contains("| 50 | 21.0 |"))
    }

    @Test fun balayageUdsOrdonne() {
        assertEquals(0xF400, Explore.udsAt(0))
        assertEquals(65280, Explore.UDS_TOTAL)
        assertTrue((0 until Explore.UDS_TOTAL).map { Explore.udsAt(it) }.none { it in 0xF100..0xF1FF })
    }

    @Test fun correlationTrouveLaFormule() {
        // Octet 5 du bloc = température d'eau + 40 ; octet 6 = bruit.
        val hist = (0 until 10).map { i ->
            val temp = 20.0 + i * 7
            Snapshot("t$i", "", mapOf("temp_eau" to temp, "regime" to 800.0 + (i * 37 % 5) * 10), mapOf("7E0:2101" to "6101AABB%02X%02X".format((temp + 40).toInt(), (i * 53) % 256)))
        }
        val clues = Explore.correlate(hist)
        val c = clues.first { it.reference == "temp_eau" }
        assertEquals("7E0:2101", c.key)
        assertEquals("octet 4", c.position)
        assertEquals("temp_eau ≈ octet − 40", c.formula)
    }

    private fun swift(speed: Int = 0) = FakeCar(
            ecus = mapOf(
                // Moteur KWP2000 sur CAN (écart +8) : refuse 3E00 mais répond.
                "7E0" to FakeEcu("7E8", mapOf("3E00" to "7F 3E 11", "1081" to "50 81", "1A90" to "5A 90 " + text("TSMZC33S000123456"), "2100" to "61 00 01 02 03 04 05 06 07 08 7D 0A", "1800FF00" to "58 00")) { r ->
                    when { r.startsWith("21") -> "7F 21 12"; r.startsWith("1A") -> "7F 1A 12"; r.startsWith("23") -> "7F 23 11"; else -> null }
                },
                // Calculateur KWP à +0x10 (particularité Suzuki).
                "776" to FakeEcu("786", mapOf("3E00" to "7E 00", "1081" to "50 81", "2101" to "61 01 10 20 30")) { r ->
                    when { r.startsWith("21") -> "7F 21 12"; r.startsWith("1A") -> "7F 1A 12"; r.startsWith("23") -> "7F 23 11"; r.startsWith("18") -> "58 00"; else -> null }
                },
                // Airbag UDS à +0x10.
                "793" to FakeEcu("7A3", mapOf("3E00" to "7E 00", "1001" to "50 01 00 32 01 F4", "22F197" to "62 F1 97 " + text("SDM SUZUKI"), "22F190" to "62 F1 90 " + text("TSMZC33S000123456"), "22F400" to "62 F4 00 12 34", "1902FF" to "59 02 FF")) { r ->
                    when { r.startsWith("1081") -> "7F 10 12"; r.startsWith("22") -> "7F 22 31"; r.startsWith("23") -> "7F 23 33"; else -> null }
                },
            ),
            broadcast = mapOf(
                "0100" to "41 00 BE 3F A8 13", "010D" to "41 0D %02X".format(speed), "010C" to "41 0C 1A F8", "0104" to "41 04 80", "0105" to "41 05 5A",
                "03" to "43 00", "07" to "47 00", "0A" to "4A 00",
            ),
        )

    @Test fun balayageALArretVoitureFactice(): Unit = runBlocking {
        val car = swift()
        val e = Elm327(car)
        e.readOnly = true
        val dir = Files.createTempDirectory("explo").toFile()
        val data = ExplorerData()
        // Étapes longues en temps réel (écoute du bus, réveils de la ligne K) : marquées comme faites.
        data.flags["bus"] = "0"
        data.flags["k"] = "0"
        val ui = NoUi()
        val ex = Explorer(e, data, dir, ui)
        assertTrue(ex.prepare())
        assertEquals(0.0, ex.speed()!!, 0.01)
        ex.stationary("test", "Swift")

        assertEquals(setOf("7E0", "776", "793"), data.ecus.keys)
        assertEquals(0x786, data.ecus.getValue("776").rx)
        assertEquals("kwp", data.ecus.getValue("7E0").proto)
        assertEquals("kwp", data.ecus.getValue("776").proto)
        assertEquals("uds", data.ecus.getValue("793").proto)
        assertTrue(data.answers.containsKey("7E0:2100"))
        assertTrue(data.answers.containsKey("776:2101"))
        assertTrue(data.answers.containsKey("793:22F400"))
        assertEquals("SDM SUZUKI", data.names()["793"])
        assertEquals(10_000, data.progress["22:793"])
        assertTrue(data.faultChanges().isEmpty())
        assertEquals(setOf("7E0", "776", "793"), data.faults.getValue("apres").keys.filter { !it.startsWith("OBD") }.toSet())
        assertTrue(data.flags.getValue("s23:793").contains("verrouillé"))
        // Lecture seule : uniquement des services de lecture, jamais 7F1 / 7F9.
        val services = car.sent.filter { !it.startsWith("AT") }.map { it.take(2) }.toSet()
        assertTrue("Services : $services", services.all { it in setOf("01", "02", "03", "06", "07", "09", "0A", "10", "18", "19", "1A", "21", "22", "23", "3E") })
        assertTrue(car.sent.filter { it.startsWith("10") }.all { it.startsWith("1001") || it.startsWith("1081") })
        assertTrue(car.sent.none { it == "ATSH7F1" || it == "ATSH7F9" })
        // Découvertes et rapport enregistrés, puis relus à l'identique.
        assertTrue(File(dir, "RAPPORT.md").readText().contains("| `793` | `7A3` | CAN 11 bits | UDS | **SDM SUZUKI** |"))
        val back = ExplorerData.load(File(dir, "decouvertes.json"))
        assertEquals(data.ecus, back.ecus)
        assertEquals(data.answers.keys, back.answers.keys)
        assertEquals(data.progress, back.progress)
        assertEquals(data.history, back.history)
        assertEquals(data.flags, back.flags)

        // Deuxième passage : reprise, rien n'est refait sauf la tranche UDS suivante.
        val before = car.sent.size
        Explorer(e, back, dir, NoUi()).also { it.prepare() }.stationary("test", "Swift")
        assertEquals(20_000, back.progress["22:793"])
        assertTrue(car.sent.subList(before, car.sent.size).none { it.startsWith("ATSH6") }) // pas de nouvelle recherche des calculateurs
        dir.deleteRecursively()
    }

    @Test fun enregistrementEnRoulant(): Unit = runBlocking {
        val car = swift(speed = 50)
        val e = Elm327(car)
        e.readOnly = true
        val dir = Files.createTempDirectory("route").toFile()
        val data = ExplorerData()
        data.ecus["7E0"] = Ecu("7E0", Bus.Can11, 0x7E0, 0x7E8, "kwp")
        data.ecus["776"] = Ecu("776", Bus.Can11, 0x776, 0x786, "kwp")
        val ex = Explorer(e, data, dir, NoUi())
        assertTrue(ex.prepare())
        assertEquals(50.0, ex.speed()!!, 0.01)
        kotlinx.coroutines.withTimeoutOrNull(4000) { ex.drive("test", "Swift") }
        val folder = File(dir, "route/" + data.drives.single())
        val obd = folder.resolve("obd.csv").readLines()
        assertTrue(obd.size > 2)
        assertTrue(obd[0].split(",").containsAll(listOf("t", "hex", "regime", "vitesse")))
        val diag = folder.resolve("diag.csv").readText()
        assertTrue(diag.contains(",7E0,2100,") && diag.contains(",776,2101,"))
        assertTrue(data.answers.containsKey("7E0:2100"))
        assertTrue(File(dir, "RAPPORT.md").exists())
        // (une commande vide = l'espace qui arrête l'écoute du bus)
        val svc = car.sent.filter { !it.startsWith("AT") && it.isNotBlank() }.map { it.take(2) }.toSet()
        assertTrue("Services : $svc", svc.all { it in setOf("01", "10", "21") })
        dir.deleteRecursively()
    }
}
