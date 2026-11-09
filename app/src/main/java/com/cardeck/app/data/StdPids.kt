package com.cardeck.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cyclone
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.ui.graphics.vector.ImageVector

/** PIDs standard du service 01 (SAE J1979 / ISO 15031-5) lus par CarDeck, si le véhicule les déclare. */
object StdPids {
    const val ENGINE = "Moteur"
    const val VEHICLE = "Véhicule"
    const val TEMPS = "Températures"
    const val INTAKE = "Admission et papillon"
    const val FUEL = "Carburant"
    const val LAMBDA = "Sondes lambda"
    const val EMISSIONS = "Antipollution"

    /** Ordre d'affichage des catégories. */
    val CATEGORIES = listOf(ENGINE, VEHICLE, TEMPS, INTAKE, FUEL, LAMBDA, EMISSIONS)

    private fun u16(d: IntArray, i: Int = 0) = (d[i] * 256 + d[i + 1]).toDouble()
    private fun s16(d: IntArray, i: Int = 0) = (d[i] * 256 + d[i + 1]).let { if (it >= 0x8000) it - 0x10000 else it }.toDouble()
    private fun pct(a: Int) = a * 100 / 255.0
    private fun trim(a: Int) = (a - 128) * 100 / 128.0

    private fun p(
        pid: Int, label: String, unit: String, icon: ImageVector, category: String, min: Double, max: Double,
        decimals: Int = 0, slow: Boolean = false, size: Int = 1, suffix: String = "", decode: (IntArray) -> Double,
    ) = PidDef(
        id = "std%02X".format(pid) + suffix, label = label, unit = unit, icon = icon, request = "01%02X".format(pid),
        decimals = decimals, min = min, max = max, slow = slow, size = size, category = category, decode = decode,
    )

    /** Grandeurs affichées en grandes jauges (si le véhicule les supporte). */
    val headline: List<PidDef> = listOf(
        p(0x0C, "Régime", "tr/min", Icons.Rounded.Cyclone, ENGINE, 0.0, 7000.0, size = 2) { u16(it) / 4 },
        p(0x0D, "Vitesse", "km/h", Icons.Rounded.Speed, VEHICLE, 0.0, 220.0) { it[0].toDouble() },
        p(0x05, "Temp. liquide", "°C", Icons.Rounded.DeviceThermostat, TEMPS, 40.0, 130.0) { it[0] - 40.0 },
        p(0x2F, "Carburant", "%", Icons.Rounded.LocalGasStation, FUEL, 0.0, 100.0, slow = true) { pct(it[0]) },
    )

    private val o2Narrow: List<PidDef> = (0..7).flatMap { n ->
        val pid = 0x14 + n
        val s = "B${n / 4 + 1}S${n % 4 + 1}"
        listOf(
            p(pid, "Sonde O2 $s · tension", "V", Icons.Rounded.Sensors, LAMBDA, 0.0, 1.275, decimals = 3, size = 1) { it[0] / 200.0 },
            p(pid, "Sonde O2 $s · correction", "%", Icons.Rounded.Sensors, LAMBDA, -25.0, 25.0, decimals = 1, size = 2, suffix = "b") { if (it[1] == 0xFF) Double.NaN else trim(it[1]) },
        )
    }

    private val o2WideV: List<PidDef> = (0..7).flatMap { n ->
        val pid = 0x24 + n
        listOf(
            p(pid, "Sonde large bande ${n + 1} · λ", "λ", Icons.Rounded.Sensors, LAMBDA, 0.5, 1.5, decimals = 3, size = 2) { u16(it) * 2 / 65536 },
            p(pid, "Sonde large bande ${n + 1} · tension", "V", Icons.Rounded.Sensors, LAMBDA, 0.0, 8.0, decimals = 2, size = 4, suffix = "b") { u16(it, 2) * 8 / 65536 },
        )
    }

    private val o2WideI: List<PidDef> = (0..7).flatMap { n ->
        val pid = 0x34 + n
        listOf(
            p(pid, "Sonde large bande ${n + 1} · λ (courant)", "λ", Icons.Rounded.Sensors, LAMBDA, 0.5, 1.5, decimals = 3, size = 2) { u16(it) * 2 / 65536 },
            p(pid, "Sonde large bande ${n + 1} · courant", "mA", Icons.Rounded.Sensors, LAMBDA, -5.0, 5.0, decimals = 2, size = 4, suffix = "b") { u16(it, 2) / 256 - 128 },
        )
    }

    private val secondaryO2: List<PidDef> = listOf(0x55 to "banc 1", 0x56 to "banc 3", 0x57 to "banc 2", 0x58 to "banc 4").map { (pid, b) ->
        p(pid, "Correction sonde O2 secondaire $b", "%", Icons.Rounded.Sensors, LAMBDA, -25.0, 25.0, decimals = 1) { trim(it[0]) }
    }

    val others: List<PidDef> = listOf(
        p(0x04, "Charge moteur", "%", Icons.Rounded.QueryStats, ENGINE, 0.0, 100.0) { pct(it[0]) },
        p(0x43, "Charge absolue", "%", Icons.Rounded.QueryStats, ENGINE, 0.0, 100.0, size = 2) { u16(it) * 100 / 255 },
        p(0x0E, "Avance à l'allumage", "°", Icons.Rounded.Sync, ENGINE, -20.0, 60.0, decimals = 1) { it[0] / 2.0 - 64 },
        p(0x61, "Couple demandé", "%", Icons.Rounded.Cyclone, ENGINE, -125.0, 130.0) { it[0] - 125.0 },
        p(0x62, "Couple réel", "%", Icons.Rounded.Cyclone, ENGINE, -125.0, 130.0) { it[0] - 125.0 },
        p(0x8E, "Couple de frottement", "%", Icons.Rounded.Cyclone, ENGINE, -125.0, 130.0) { it[0] - 125.0 },
        p(0x1F, "Durée moteur tournant", "min", Icons.Rounded.Timer, ENGINE, 0.0, 120.0, slow = true, size = 2) { u16(it) / 60 },
        p(0xA6, "Kilométrage", "km", Icons.Rounded.Route, VEHICLE, 0.0, 300000.0, decimals = 1, slow = true, size = 4) {
            (it[0].toLong() shl 24 or (it[1].toLong() shl 16) or (it[2].toLong() shl 8) or it[3].toLong()) / 10.0
        },
        p(0x21, "Distance voyant allumé", "km", Icons.Rounded.Route, VEHICLE, 0.0, 1000.0, slow = true, size = 2) { u16(it) },
        p(0x0F, "Temp. admission", "°C", Icons.Rounded.Thermostat, TEMPS, -10.0, 80.0) { it[0] - 40.0 },
        p(0x5C, "Temp. huile", "°C", Icons.Rounded.DeviceThermostat, TEMPS, 20.0, 150.0, slow = true) { it[0] - 40.0 },
        p(0x84, "Temp. surface collecteur", "°C", Icons.Rounded.Thermostat, TEMPS, 0.0, 150.0) { it[0] - 40.0 },
        p(0x46, "Temp. extérieure", "°C", Icons.Rounded.Thermostat, TEMPS, -20.0, 50.0, slow = true) { it[0] - 40.0 },
        p(0x11, "Papillon", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x45, "Papillon relatif", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x47, "Papillon B", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x48, "Papillon C", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x4C, "Papillon commandé", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x0B, "Pression collecteur (MAP)", "kPa", Icons.Rounded.Air, INTAKE, 0.0, 250.0) { it[0].toDouble() },
        p(0x10, "Débit d'air (MAF)", "g/s", Icons.Rounded.Waves, INTAKE, 0.0, 100.0, decimals = 1, size = 2) { u16(it) / 100 },
        p(0x33, "Pression atmosphérique", "kPa", Icons.Rounded.Air, INTAKE, 80.0, 110.0) { it[0].toDouble() },
        p(0x06, "Correction carburant CT banc 1", "%", Icons.Rounded.LocalGasStation, FUEL, -25.0, 25.0, decimals = 1) { trim(it[0]) },
        p(0x07, "Correction carburant LT banc 1", "%", Icons.Rounded.LocalGasStation, FUEL, -25.0, 25.0, decimals = 1) { trim(it[0]) },
        p(0x08, "Correction carburant CT banc 2", "%", Icons.Rounded.LocalGasStation, FUEL, -25.0, 25.0, decimals = 1) { trim(it[0]) },
        p(0x09, "Correction carburant LT banc 2", "%", Icons.Rounded.LocalGasStation, FUEL, -25.0, 25.0, decimals = 1) { trim(it[0]) },
        p(0x44, "Richesse (λ commandé)", "λ", Icons.Rounded.LocalGasStation, FUEL, 0.5, 1.5, decimals = 2, size = 2) { u16(it) * 2 / 65536 },
        p(0x0A, "Pression carburant", "kPa", Icons.Rounded.LocalGasStation, FUEL, 0.0, 765.0) { it[0] * 3.0 },
        p(0x22, "Pression de rampe (relative)", "kPa", Icons.Rounded.LocalGasStation, FUEL, 0.0, 6000.0, decimals = 1, size = 2) { u16(it) * 0.079 },
        p(0x23, "Pression de rampe", "kPa", Icons.Rounded.LocalGasStation, FUEL, 0.0, 20000.0, size = 2) { u16(it) * 10 },
        p(0x59, "Pression de rampe absolue", "kPa", Icons.Rounded.LocalGasStation, FUEL, 0.0, 20000.0, size = 2) { u16(it) * 10 },
        p(0x5D, "Calage de l'injection", "°", Icons.Rounded.LocalGasStation, FUEL, -210.0, 302.0, decimals = 1, size = 2) { u16(it) / 128 - 210 },
        p(0x5E, "Consommation", "L/h", Icons.Rounded.LocalGasStation, FUEL, 0.0, 30.0, decimals = 1, size = 2) { u16(it) / 20 },
        p(0xA2, "Carburant par cylindre", "mg/coup", Icons.Rounded.LocalGasStation, FUEL, 0.0, 100.0, decimals = 1, size = 2) { u16(it) / 32 },
        p(0x52, "Taux d'éthanol", "%", Icons.Rounded.LocalGasStation, FUEL, 0.0, 100.0, slow = true) { pct(it[0]) },
        p(0x2E, "Purge canister commandée", "%", Icons.Rounded.FilterAlt, EMISSIONS, 0.0, 100.0) { pct(it[0]) },
    ) + o2Narrow + o2WideV + o2WideI + secondaryO2

    /** Toutes les définitions connues. */
    val all: List<PidDef> = headline + others

    private val byPid: Map<Int, List<PidDef>> = all.groupBy { pidNumber(it) }

    fun pidNumber(p: PidDef): Int = p.request.substring(2, 4).toInt(16)

    /** Définitions des données à lire pour un ensemble de PIDs supportés : seules celles du catalogue sont lues. */
    fun forSupported(sup: Set<Int>): List<PidDef> = sup.sorted().flatMap { n -> byPid[n].orEmpty() }

    /** Retrouve une définition par identifiant. */
    fun byId(id: String): PidDef? = all.find { it.id == id }

    /** Identifiants de toutes les données que CarDeck peut enregistrer. */
    val ids: Set<String> get() = all.mapTo(HashSet()) { it.id }
}
