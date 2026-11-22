package com.cardeck.app.export

import com.cardeck.app.data.FuelType
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

    /**
     * Cellule de tableur. Un texte qui commence par = + - @ (ou une tabulation) serait pris pour une formule par Excel :
     * un nom de véhicule ou de rue piégé (« =HYPERLINK(…) ») s'exécuterait à l'ouverture. Il est précédé d'une apostrophe.
     */
    internal fun csvCell(s: String): String {
        val safe = if (s.isNotEmpty() && s[0] in "=+-@\t\r") "'$s" else s
        return if (safe.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Chaîne JSON : guillemets, barre oblique inverse et tous les caractères de contrôle échappés (RFC 8259). */
    internal fun json(s: String) = buildString {
        append('"')
        for (ch in s) when {
            ch == '"' -> append("\\\"")
            ch == '\\' -> append("\\\\")
            ch == '\n' -> append("\\n")
            ch == '\r' -> append("\\r")
            ch == '\t' -> append("\\t")
            ch < ' ' -> append("\\u%04x".format(ch.code))
            else -> append(ch)
        }
        append('"')
    }

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

    /** Toutes les données du trajet : une ligne par instant de mesure (à la milliseconde), une colonne par donnée. */
    fun dataCsv(trip: Trip, samples: Map<String, List<Sample>>): String = buildString {
        val order = StdPids.CATEGORIES
        val cols = samples.keys.sortedWith(
            compareBy<String>({ id -> pidById(id)?.category?.let { c -> order.indexOf(c).let { if (it < 0) order.size else it } } ?: order.size + 1 }, { pidById(it)?.label ?: it }),
        )
        val byTime = sortedMapOf<Long, MutableMap<String, Double>>()
        samples.forEach { (id, list) -> list.forEach { s -> byTime.getOrPut(s.t) { mutableMapOf() }[id] = s.v } }
        val fmt = local("yyyy-MM-dd HH:mm:ss.SSS")
        append(BOM) // BOM : accents corrects dans Excel
        append((listOf("Horodatage", "Temps écoulé (s)") + cols.map { id -> pidById(id)?.let { p -> if (p.unit.isBlank()) p.label else "${p.label} (${p.unit})" } ?: id }).joinToString(";") { csvCell(it) })
        append("\r\n")
        byTime.forEach { (t, row) ->
            append(fmt.format(Date(t))).append(';').append(num((t - trip.start) / 1000.0, 3))
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

    /** Une ligne de l'historique : le trajet, son véhicule, le carburant retenu et les litres consommés (null si non mesurés). */
    class HistoryRow(val trip: Trip, val vehicle: VehicleProfile?, val fuel: FuelType, val liters: Double?)

    /**
     * Historique des trajets pour un tableur (frais kilométriques, comptabilité) : une ligne par trajet, du plus ancien
     * au plus récent, puis une ligne de total. Mêmes calculs que l'écran du trajet (consommation, coût).
     */
    fun historyCsv(rows: List<HistoryRow>): String = buildString {
        val date = local("dd/MM/yyyy")
        val hour = local("HH:mm")
        append(BOM)
        append(
            listOf(
                "Véhicule", "Immatriculation", "Date", "Heure de départ", "Heure d'arrivée", "Durée (min)", "Départ", "Arrivée",
                "Distance (km)", "Vitesse moyenne (km/h)", "Vitesse max (km/h)", "Carburant", "Carburant consommé (L)",
                "Consommation (L/100 km)", "Prix du carburant (€/L)", "Coût (€)", "Origine du prix",
            ).joinToString(";") { csvCell(it) },
        ).append("\r\n")
        var km = 0.0; var minutes = 0.0; var liters = 0.0; var cost = 0.0
        for (r in rows.sortedBy { it.trip.start }) {
            val t = r.trip
            val conso = r.liters?.takeIf { t.distanceKm > 0.5 }?.let { it / t.distanceKm * 100 }
            val c = t.fuelPrice?.let { p -> r.liters?.let { it * p } }
            val min = (t.end - t.start) / 60_000.0
            km += t.distanceKm; minutes += min; r.liters?.let { liters += it }; c?.let { cost += it }
            append(
                listOf(
                    csvCell(r.vehicle?.nickname.orEmpty()), csvCell(r.vehicle?.plate.orEmpty()),
                    date.format(Date(t.start)), hour.format(Date(t.start)), hour.format(Date(t.end)), num(min, 0),
                    csvCell(t.from), csvCell(t.to), num(t.distanceKm, 1), num(t.avgKmh, 0), num(t.maxKmh, 0),
                    csvCell(r.fuel.label), r.liters?.let { num(it, 2) }.orEmpty(), conso?.let { num(it, 1) }.orEmpty(),
                    t.fuelPrice?.let { num(it, 3) }.orEmpty(), c?.let { num(it, 2) }.orEmpty(), csvCell(t.fuelPriceInfo.orEmpty()),
                ).joinToString(";"),
            ).append("\r\n")
        }
        append(
            listOf("Total (${rows.size} trajet${if (rows.size > 1) "s" else ""})", "", "", "", "", num(minutes, 0), "", "", num(km, 1), "", "", "",
                num(liters, 2), "", "", num(cost, 2), "").joinToString(";") { csvCell(it) },
        ).append("\r\n")
    }

    /** Écrit l'historique dans [dir] (anciens exports effacés) et renvoie le fichier. */
    fun writeHistory(dir: File, rows: List<HistoryRow>, label: String): File {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val safe = label.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "trajets" }
        val out = File(dir, "CarDeck_historique_${safe}_${local("yyyy-MM-dd").format(Date())}.csv")
        out.writeText(historyCsv(rows), Charsets.UTF_8)
        return out
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
