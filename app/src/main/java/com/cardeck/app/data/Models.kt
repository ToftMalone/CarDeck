package com.cardeck.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.TurnSharpRight
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Formatage à la française : 2 140 / 6,4 */
fun fr(n: Number, decimals: Int = 0): String {
    val f = NumberFormat.getNumberInstance(Locale.FRANCE)
    f.minimumFractionDigits = decimals
    f.maximumFractionDigits = decimals
    return f.format(n)
}

enum class Transport(val label: String) { Classic("Bluetooth"), Ble("Bluetooth LE"), Wifi("Wi-Fi") }

/** Boîtier OBD2 associé à un profil de véhicule. [address] = MAC Bluetooth ou « hôte:port » en Wi-Fi. */
data class AdapterConfig(val transport: Transport, val address: String, val name: String)

/** Profil de véhicule : tout (trajets, diagnostics, boîtier) y est rattaché. */
data class VehicleProfile(
    val id: Long,
    val templateId: String,
    val nickname: String,
    val plate: String,
    val year: String,
    val vin: String?,
    val adapter: AdapterConfig?,
) {
    val template: VehicleTemplate get() = templateById(templateId)
}

enum class EventType(val label: String, val icon: ImageVector) {
    Brake("Freinage brusque", Icons.Rounded.TrendingDown),
    Accel("Accélération vive", Icons.Rounded.TrendingUp),
    Turn("Virage serré", Icons.Rounded.TurnSharpRight),
}

data class TripEvent(val t: Long, val type: EventType, val lat: Double, val lon: Double, val g: Double)

data class TripPoint(val t: Long, val lat: Double, val lon: Double, val speedKmh: Double)

data class Trip(
    val id: Long,
    val vehicleId: Long,
    val start: Long,
    val end: Long,
    val distanceKm: Double,
    val avgKmh: Double,
    val maxKmh: Double,
    val score: Int,
    val from: String,
    val to: String,
    val events: List<TripEvent>,
)

enum class DtcStatus(val label: String) { Stored("Mémorisé"), Pending("En attente"), Permanent("Permanent") }

data class FoundCode(val code: String, val status: DtcStatus, val ecu: String)

data class DtcScan(
    val id: Long,
    val vehicleId: Long,
    val t: Long,
    val ecus: Int,
    val codes: List<FoundCode>,
    val ffCode: String?,
    val ff: List<String>?,
)

// ---- Dates ----

private fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

fun dayLabel(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val now = Calendar.getInstance()
    if (sameDay(c, now)) return "Aujourd'hui"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(c, now)) return "Hier"
    return SimpleDateFormat("EEEE d MMM", Locale.FRANCE).format(Date(ts)).replaceFirstChar { it.uppercase() }
}

fun timeHm(ts: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ts))

fun durationLabel(ms: Long): String {
    val min = (ms / 60000).toInt()
    return if (min < 60) "$min min" else "${min / 60} h ${"%02d".format(min % 60)}"
}

/** « il y a 3 jours », « aujourd'hui à 09:38 »… */
fun agoLabel(ts: Long): String {
    val d = dayLabel(ts)
    return if (d == "Aujourd'hui" || d == "Hier") "${d.lowercase()} à ${timeHm(ts)}" else "le ${d.lowercase()} à ${timeHm(ts)}"
}
