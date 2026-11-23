package com.cardeck.app.ai

import com.cardeck.app.data.CLIO_V_TCE90
import com.cardeck.app.data.DtcScan
import com.cardeck.app.data.DtcStatus
import com.cardeck.app.data.FoundCode
import com.cardeck.app.data.I18n
import com.cardeck.app.data.Lang
import com.cardeck.app.data.Trip
import com.cardeck.app.screens.plainAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class AssistantTest {
    companion object {
        @BeforeClass @JvmStatic
        fun load() = I18n.load(File("src/main/assets/i18n/strings.tsv").readText())
    }

    private val now = 1_800_000_000_000L

    private fun snap(connected: Boolean = true, live: Map<String, Double> = emptyMap()) = Assistant.Snapshot(
        template = CLIO_V_TCE90, year = "2021", connected = connected, live = live,
        lastScan = DtcScan(1, 1, now - 2 * 3_600_000, 3, listOf(FoundCode("P0171", DtcStatus.Stored, "Moteur")), null, null),
        readiness = null,
        trips = listOf(Trip(1, 1, now - 3_000_000, now - 600_000, 32.4, 48.0, 112.0, "A", "B")),
        now = now,
    )

    @Test
    fun appreciations() {
        assertEquals("normal", Assistant.assess("std05", 90.0, true))
        assertTrue(Assistant.assess("std05", 115.0, true)!!.startsWith("OVERHEATING"))
        assertTrue(Assistant.assess("std05", 50.0, true)!!.contains("cold"))
        assertTrue(Assistant.assess("std07", 14.0, true)!!.contains("lean"))
        assertTrue(Assistant.assess("std07", 24.0, true)!!.startsWith("very"))
        assertEquals("normal", Assistant.assess("std07", 3.0, true))
    }

    @Test
    fun faitsEnAnglais() {
        val f = Assistant.facts(snap(live = mapOf("std0C" to 820.0, "std05" to 91.0, "std07" to 13.3)))
        assertTrue(f, f.contains("Vehicle: Renault Clio V (1.0 TCe, 90 ch, Petrol), 2021"))
        assertTrue(f, f.contains("engine running"))
        assertTrue(f, f.contains("Coolant temp.: 91 °C [normal]"))
        assertTrue(f, f.contains("Fuel trim LT bank 1: 13.3 % [high (engine compensating a lean mixture)]"))
        assertTrue(f, f.contains("P0171 (stored, Engine): System too lean (bank 1) [severity: medium]"))
        assertTrue(f, f.contains("2 hours ago"))
        assertTrue(f, f.contains("Last trip: less than an hour ago, 32.4 km in 40 min"))
        assertTrue(f, f.contains("Emissions readiness: not checked yet."))
    }

    @Test
    fun sansBoitier() {
        val f = Assistant.facts(snap(connected = false))
        assertTrue(f.contains("adapter not connected"))
        assertFalse(f.contains("Live data (adapter connected"))
    }

    @Test
    fun consigne() {
        val p = Assistant.prompt(snap(), "Comment va mon moteur ?", Lang.Fr, "Avant ?" to "Réponse")
        assertTrue(p.contains("Answer in French."))
        assertTrue(p.contains("FACTS:\nVehicle:"))
        assertTrue(p.contains("PREVIOUS QUESTION: Avant ?"))
        assertTrue(p.endsWith("QUESTION: Comment va mon moteur ?"))
        assertTrue(Assistant.prompt(snap(), "Hi", Lang.Ru).contains("Answer in Russian."))
    }

    @Test
    fun reponseNettoyee() {
        assertEquals("Bilan\n• ok en gras\n• liste", plainAnswer("## Bilan\n* **ok** en gras\n- liste"))
    }
}
