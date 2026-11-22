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
        "std0D" to listOf(Sample(t0 + 1000, 36.0)),
    )

    @Test fun csv() {
        val csv = TripExport.dataCsv(trip, samples)
        val lines = csv.removePrefix("﻿").trim().split("\r\n")
        assertEquals(4, lines.size) // en-tête + 3 instants
        assertTrue(lines[0].contains("Régime (tr/min)") && lines[0].contains("Temp. liquide (°C)") && lines[0].contains("Vitesse (km/h)"))
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

    @Test fun historique() {
        val v = com.cardeck.app.data.VehicleProfile(1, "suzuki-swift-sport-zc33s", "=Ma Swift", "AB-123-CD", "2020", null, null)
        val priced = trip.copy(id = 2, start = t0 + 3_600_000, end = t0 + 3_600_000 + 1_800_000, distanceKm = 25.0, to = "Rue C", fuelType = com.cardeck.app.data.FuelType.SP98, fuelPrice = 1.9, fuelPriceInfo = "prix médian")
        val rows = listOf(
            TripExport.HistoryRow(priced, v, com.cardeck.app.data.FuelType.SP98, 1.5),
            TripExport.HistoryRow(trip, v, com.cardeck.app.data.FuelType.SP98, null),
        )
        val lines = TripExport.historyCsv(rows).removePrefix("\uFEFF").trim().split("\r\n")
        assertEquals(4, lines.size) // en-tête, 2 trajets, total
        assertTrue(lines[0].startsWith("Véhicule;Immatriculation;Date"))
        // Du plus ancien au plus récent ; nom piégé neutralisé (pas de formule à l'ouverture dans Excel).
        assertTrue(lines[1].startsWith("'=Ma Swift;AB-123-CD;"))
        assertTrue(lines[1].contains("\"Rue B; Reims\""))
        val c = lines[2].split(";")
        assertEquals("25", c[8]); assertEquals("1,5", c[12]); assertEquals("6", c[13]); assertEquals("1,9", c[14]); assertEquals("2,85", c[15])
        assertTrue(lines[3].startsWith("Total (2 trajets);"))
        assertTrue(lines[3].contains(";26,2;")) // 1,2 + 25 km
    }

    @Test fun formulesNeutralisees() {
        assertEquals("'=1+1", TripExport.csvCell("=1+1"))
        assertEquals("'+33 6", TripExport.csvCell("+33 6"))
        assertEquals("'@SUM(A1)", TripExport.csvCell("@SUM(A1)"))
        assertEquals("Rue A", TripExport.csvCell("Rue A"))
    }
}
