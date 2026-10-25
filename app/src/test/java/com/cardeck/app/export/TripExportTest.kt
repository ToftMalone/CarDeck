package com.cardeck.app.export

import com.cardeck.app.data.Sample
import com.cardeck.app.data.Trip
import com.cardeck.app.data.TripPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class TripExportTest {
    private val t0 = 1_760_000_000_000L
    private val trip = Trip(1, 1, t0, t0 + 60_000, 1.2, 72.0, 95.0, "Rue A, Reims", "Rue B; Reims")
    private val points = listOf(TripPoint(t0, 49.25, 4.03, 0.0), TripPoint(t0 + 1000, 49.2501, 4.0301, 36.0))
    private val samples = mapOf(
        "std0C" to listOf(Sample(t0, 800.0), Sample(t0 + 2000, 2140.5)),
        "std05" to listOf(Sample(t0, 88.0)),
        "gps_speed" to listOf(Sample(t0 + 1000, 36.0)),
    )

    @Test fun csv() {
        val csv = TripExport.dataCsv(trip, samples)
        val lines = csv.removePrefix("﻿").trim().split("\r\n")
        assertEquals(4, lines.size) // en-tête + 3 instants
        assertTrue(lines[0].contains("Régime (tr/min)") && lines[0].contains("Temp. liquide (°C)") && lines[0].contains("Vitesse GPS (km/h)"))
        assertTrue(lines[3].contains("2140,5"))
    }

    @Test fun gpxEtJson() {
        val gpx = TripExport.gpx(trip, points, "Test & co")
        assertEquals(2, Regex("<trkpt ").findAll(gpx).count())
        assertTrue(gpx.contains("Test &amp; co") && gpx.contains("<speed>10.00</speed>"))
        val json = TripExport.summaryJson(trip, null, points.size, samples)
        assertTrue(json.contains("\"distance_km\": 1.2") && json.contains("\"arrivee\": \"Rue B; Reims\""))
    }

    @Test fun archive() {
        val dir = File(System.getProperty("java.io.tmpdir"), "cardeck-export-test").apply { deleteRecursively() }
        val zip = TripExport.writeZip(dir, trip, null, points, samples)
        ZipFile(zip).use { z -> assertEquals(setOf("resume.json", "trace.gpx", "donnees.csv"), z.entries().toList().map { it.name }.toSet()) }
        dir.deleteRecursively()
    }
}
