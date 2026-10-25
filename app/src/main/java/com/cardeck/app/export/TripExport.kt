package com.cardeck.app.export

import com.cardeck.app.data.StdPids
import com.cardeck.app.data.Sample
import com.cardeck.app.data.Trip
import com.cardeck.app.data.TripPoint
import com.cardeck.app.data.VehicleProfile
import com.cardeck.app.data.pidById
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Export complet d'un trajet : tracé GPX, données (CSV) et résumé (JSON), dans un ZIP. */
object TripExport {
    /** Marque d'ordre des octets UTF-8 : accents corrects à l'ouverture dans Excel. */
    private const val BOM = 0xFEFF.toChar()

    private fun local(fmt: String) = SimpleDateFormat(fmt, Locale.FRANCE)
    private fun utc() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** Nombre au format français pour tableur (virgule décimale, sans séparateur de milliers). */
    private fun num(v: Double, maxDecimals: Int = 3): String =
        if (v.isNaN() || v.isInfinite()) "" else BigDecimal(v).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

    private fun csvCell(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    fun fileName(trip: Trip): String = "CarDeck_trajet_" + local("yyyy-MM-dd_HH'h'mm").format(Date(trip.start)) + ".zip"

    /** Tracé GPS au format GPX 1.1 (vitesse en m/s dans les extensions). */
    fun gpx(trip: Trip, points: List<TripPoint>, name: String): String = buildString {
        val t = utc()
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="CarDeck" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("  <metadata><name>").append(xml(name)).append("</name><time>").append(t.format(Date(trip.start))).append("</time></metadata>\n")
        append("  <trk><name>").append(xml("${trip.from} → ${trip.to}")).append("</name><trkseg>\n")
        points.forEach { p ->
            append("    <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\"><time>").append(t.format(Date(p.t)))
                .append("</time><extensions><speed>").append(BigDecimal(p.speedKmh / 3.6).setScale(2, RoundingMode.HALF_UP).toPlainString()).append("</speed></extensions></trkpt>\n")
        }
        append("  </trkseg></trk>\n</gpx>\n")
    }

    /** Toutes les données du trajet : une ligne par instant de mesure, une colonne par donnée. */
    fun dataCsv(trip: Trip, samples: Map<String, List<Sample>>): String = buildString {
        val order = StdPids.CATEGORIES
        val cols = samples.keys.sortedWith(
            compareBy<String>({ id -> pidById(id)?.category?.let { c -> order.indexOf(c).let { if (it < 0) order.size else it } } ?: order.size + 1 }, { pidById(it)?.label ?: it }),
        )
        val byTime = sortedMapOf<Long, MutableMap<String, Double>>()
        samples.forEach { (id, list) -> list.forEach { s -> byTime.getOrPut(s.t) { mutableMapOf() }[id] = s.v } }
        val fmt = local("yyyy-MM-dd HH:mm:ss")
        append(BOM) // BOM : accents corrects dans Excel
        append((listOf("Horodatage", "Temps écoulé (s)") + cols.map { id -> pidById(id)?.let { p -> if (p.unit.isBlank()) p.label else "${p.label} (${p.unit})" } ?: id }).joinToString(";") { csvCell(it) })
        append("\r\n")
        byTime.forEach { (t, row) ->
            append(fmt.format(Date(t))).append(';').append(num((t - trip.start) / 1000.0, 1))
            cols.forEach { id -> append(';').append(row[id]?.let { num(it) } ?: "") }
            append("\r\n")
        }
    }

    fun summaryJson(trip: Trip, vehicle: VehicleProfile?, points: Int, samples: Map<String, List<Sample>>): String {
        val fmt = local("yyyy-MM-dd'T'HH:mm:ssXXX")
        val tpl = vehicle?.template
        val vehicleJson = if (vehicle == null) "null" else """{
    "nom": ${json(vehicle.nickname)},
    "modele": ${json(tpl!!.fullName)},
    "moteur": ${json(tpl.specs)},
    "immatriculation": ${json(vehicle.plate)},
    "annee": ${json(vehicle.year)},
    "vin": ${vehicle.vin?.let { json(it) } ?: "null"}
  }"""
        val data = samples.entries.joinToString(",\n    ") { (id, list) ->
            val p = pidById(id)
            """{"id": ${json(id)}, "libelle": ${json(p?.label ?: id)}, "unite": ${json(p?.unit ?: "")}, "categorie": ${json(p?.category ?: "")}, "mesures": ${list.size}, "min": ${list.minOf { it.v }}, "max": ${list.maxOf { it.v }}, "moyenne": ${list.sumOf { it.v } / list.size}}"""
        }
        return """{
  "application": "CarDeck",
  "vehicule": $vehicleJson,
  "trajet": {
    "depart": ${json(trip.from)},
    "arrivee": ${json(trip.to)},
    "debut": ${json(fmt.format(Date(trip.start)))},
    "fin": ${json(fmt.format(Date(trip.end)))},
    "duree_s": ${(trip.end - trip.start) / 1000},
    "distance_km": ${trip.distanceKm},
    "vitesse_moyenne_kmh": ${trip.avgKmh},
    "vitesse_max_kmh": ${trip.maxKmh},
    "points_gps": $points
  },
  "donnees": [
    $data
  ]
}
"""
    }

    /** Écrit l'archive complète et renvoie le fichier. */
    fun writeZip(dir: File, trip: Trip, vehicle: VehicleProfile?, points: List<TripPoint>, samples: Map<String, List<Sample>>): File {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // anciens exports
        val out = File(dir, fileName(trip))
        val name = (vehicle?.nickname?.let { "$it · " } ?: "") + local("d MMMM yyyy HH:mm").format(Date(trip.start))
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            fun put(entry: String, text: String) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("resume.json", summaryJson(trip, vehicle, points.size, samples))
            put("trace.gpx", gpx(trip, points, name))
            put("donnees.csv", dataCsv(trip, samples))
        }
        return out
    }
}
