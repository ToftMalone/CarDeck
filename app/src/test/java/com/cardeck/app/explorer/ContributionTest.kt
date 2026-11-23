package com.cardeck.app.explorer

import com.cardeck.app.data.CLIO_V_TCE90
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionTest {
    private val vin = "VF1RJA00512345678"
    private fun hex(s: String) = s.map { "%02X".format(it.code) }.joinToString("")

    @Test
    fun vinMasque() {
        assertEquals("VF1RJA0051*******", Contribution.maskVin(vin))
    }

    @Test
    fun vinEnClairRetire() {
        val out = Contribution.scrub("""{"vin":"$vin","autre":"x"}""")
        assertFalse(out.contains("2345678"))
        assertTrue(out.contains("VF1RJA00510000000"))
    }

    @Test
    fun vinEnOctetsRetire() {
        // Réponse au service 09 02 : écho « 49 02 01 » collé au VIN, puis des octets qui suivent.
        val payload = "490201" + hex(vin) + "FF"
        val out = Contribution.scrub("""{"hex":"$payload"}""")
        assertFalse(out.contains(hex("2345678")))
        assertTrue(out.contains("490201" + hex("VF1RJA0051") + hex("0000000") + "FF"))
        // Écrit avec des espaces (journal brut).
        val spaced = (listOf("62", "F1", "90") + vin.map { "%02X".format(it.code) }).joinToString(" ")
        assertFalse(Contribution.scrub(spaced).contains(hex("5678").chunked(2).joinToString(" ")))
    }

    @Test
    fun donneesOrdinairesIntactes() {
        val s = """{"hex":"6101A5003C0F0000FFFF1234","ref":"8200123456","t":"2026-11-23 10:06"}"""
        assertEquals(s, Contribution.scrub(s))
    }

    @Test
    fun surnomRemplace() {
        assertEquals("Rapport — Renault Clio V", Contribution.scrub("Rapport — La Clio de Tom", nickname = "La Clio de Tom", replacement = "Renault Clio V"))
    }

    @Test
    fun vinLuParLExplorateur() {
        val d = ExplorerData()
        d.answers["7E0:0902"] = Resp("490201" + hex(vin), "t", "t")
        d.answers["7E0:1A80"] = Resp(hex("ABCDEFGH123456789"), "t", "t") // référence, pas une demande de VIN
        assertEquals(setOf(vin), Contribution.vinsIn(d))
    }

    @Test
    fun ficheDuVehicule() {
        val ecus = listOf(Ecu("E0", Bus.Can29, 0x18DA15F1, 0x18DAF115, "uds"))
        val f = Contribution.Findings(ecus, mapOf("E0" to "Moteur"), 12, 3, 4, 0, 0)
        val s = Contribution.Sheet(CLIO_V_TCE90, "2021", vin, "0.6", "Android 14 (API 34)", "OBDII · Bluetooth", "ISO 15765-4 CAN 29/500", setOf(0x0C, 0x05), mapOf("regime" to 812.0), "23/11/2026 à 10:20")
        val md = Contribution.sheetMarkdown(s, f)
        assertTrue(md.contains("| Marque | Renault |") && md.contains("| Moteur | 1.0 TCe |") && md.contains("| Année | 2021 |"))
        assertTrue(md.contains("05 0C") && md.contains("`regime` | 812.00"))
        assertFalse(md.contains("2345678"))
        val js = Contribution.sheetJson(s, f)
        assertTrue(js.contains("\"marque\":\"Renault\"") && js.contains("\"pid01_fournis\":[\"05\",\"0C\"]") && js.contains("\"nom\":\"Moteur\""))
        assertTrue(js.contains("\"vin_debut\":\"VF1RJA0051*******\""))
        org.json.JSONObject(js) // JSON valide
    }

    @Test
    fun ticketPrerempli() {
        val ecus = (0 until 400).map { Ecu("E$it", Bus.Can11, 0x700 + it, 0x708 + it, "uds") }
        val f = Contribution.Findings(ecus, mapOf("E0" to "Moteur"), 1200, 30, 45, 2, 0)
        val url = Contribution.issueUrl(CLIO_V_TCE90, "2021", vin, "0.6", f, "CarDeck_renault-clio-v.zip")
        assertTrue(url.startsWith("https://github.com/ToftMalone/CarDeck/issues/new?title="))
        assertTrue(url.length <= Contribution.MAX_URL)
        assertFalse(url.contains("2345678"))
        assertTrue(url.contains("CarDeck_renault-clio-v.zip"))
        val body = Contribution.body(CLIO_V_TCE90, "2021", vin, "0.6", f, "x.zip", 2)
        assertTrue(body.contains("VF1RJA0051*******"))
        assertTrue(body.contains("398 autres"))
    }
}
