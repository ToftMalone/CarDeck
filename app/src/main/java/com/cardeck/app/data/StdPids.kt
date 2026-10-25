package com.cardeck.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Cyclone
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * PIDs standard du service 01 (SAE J1979 / ISO 15031-5), lus sur tout véhicule OBD2 qui les déclare,
 * + données du téléphone et du boîtier enregistrées pendant les trajets.
 */
object StdPids {
    const val ENGINE = "Moteur"
    const val VEHICLE = "Véhicule"
    const val TEMPS = "Températures"
    const val INTAKE = "Admission et papillon"
    const val FUEL = "Carburant"
    const val LAMBDA = "Sondes lambda"
    const val EMISSIONS = "Antipollution"
    const val ELEC = "Électrique"
    const val RAW = "Autres données (brutes)"

    /** Données qui changent vite, relues à chaque tour pendant les trajets (l'ELM327 est limité à ~30 valeurs/s). */
    val FAST = setOf("std0C", "std0D", "std0B", "std04", "std43", "std10", "std0E", "std11", "std45", "std47", "std49", "std4A", "std4C", "std23", "std06", "std5E")

    /** Ordre d'affichage des catégories. */
    val CATEGORIES = listOf(ENGINE, VEHICLE, TEMPS, INTAKE, FUEL, LAMBDA, EMISSIONS, ELEC, RAW)

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

    private val catalyst: List<PidDef> = listOf(0x3C to "B1S1", 0x3D to "B2S1", 0x3E to "B1S2", 0x3F to "B2S2").map { (pid, s) ->
        p(pid, "Temp. catalyseur $s", "°C", Icons.Rounded.FilterAlt, EMISSIONS, 0.0, 1000.0, size = 2) { u16(it) / 10 - 40 }
    }

    private val secondaryO2: List<PidDef> = listOf(0x55 to "banc 1", 0x56 to "banc 3", 0x57 to "banc 2", 0x58 to "banc 4").map { (pid, b) ->
        p(pid, "Correction sonde O2 secondaire $b", "%", Icons.Rounded.Sensors, LAMBDA, -25.0, 25.0, decimals = 1) { trim(it[0]) }
    }

    val others: List<PidDef> = listOf(
        p(0x01, "Voyant moteur (MIL)", "", Icons.Rounded.Warning, EMISSIONS, 0.0, 1.0, slow = true) { if (it[0] and 0x80 != 0) 1.0 else 0.0 },
        p(0x01, "Codes défaut en mémoire", "", Icons.Rounded.Warning, EMISSIONS, 0.0, 10.0, slow = true, suffix = "b") { (it[0] and 0x7F).toDouble() },
        p(0x04, "Charge moteur", "%", Icons.Rounded.QueryStats, ENGINE, 0.0, 100.0) { pct(it[0]) },
        p(0x43, "Charge absolue", "%", Icons.Rounded.QueryStats, ENGINE, 0.0, 100.0, size = 2) { u16(it) * 100 / 255 },
        p(0x0E, "Avance à l'allumage", "°", Icons.Rounded.Sync, ENGINE, -20.0, 60.0, decimals = 1) { it[0] / 2.0 - 64 },
        p(0x61, "Couple demandé", "%", Icons.Rounded.Cyclone, ENGINE, -125.0, 130.0) { it[0] - 125.0 },
        p(0x62, "Couple réel", "%", Icons.Rounded.Cyclone, ENGINE, -125.0, 130.0) { it[0] - 125.0 },
        p(0x63, "Couple de référence", "N·m", Icons.Rounded.Cyclone, ENGINE, 0.0, 500.0, slow = true, size = 2) { u16(it) },
        p(0x8E, "Couple de frottement", "%", Icons.Rounded.Cyclone, ENGINE, -125.0, 130.0) { it[0] - 125.0 },
        p(0x1F, "Durée moteur tournant", "min", Icons.Rounded.Timer, ENGINE, 0.0, 120.0, slow = true, size = 2) { u16(it) / 60 },
        p(0x30, "Démarrages à chaud depuis effacement", "", Icons.Rounded.Timer, ENGINE, 0.0, 255.0, slow = true) { it[0].toDouble() },
        p(0x4D, "Durée voyant allumé", "min", Icons.Rounded.Timer, EMISSIONS, 0.0, 1000.0, slow = true, size = 2) { u16(it) },
        p(0x4E, "Durée depuis effacement des codes", "min", Icons.Rounded.Timer, EMISSIONS, 0.0, 10000.0, slow = true, size = 2) { u16(it) },
        p(0xA6, "Kilométrage", "km", Icons.Rounded.Route, VEHICLE, 0.0, 300000.0, decimals = 1, slow = true, size = 4) {
            (it[0].toLong() shl 24 or (it[1].toLong() shl 16) or (it[2].toLong() shl 8) or it[3].toLong()) / 10.0
        },
        p(0x21, "Distance voyant allumé", "km", Icons.Rounded.Route, VEHICLE, 0.0, 1000.0, slow = true, size = 2) { u16(it) },
        p(0x31, "Distance depuis effacement", "km", Icons.Rounded.Route, VEHICLE, 0.0, 5000.0, slow = true, size = 2) { u16(it) },
        p(0x0F, "Temp. admission", "°C", Icons.Rounded.Thermostat, TEMPS, -10.0, 80.0) { it[0] - 40.0 },
        p(0x68, "Temp. admission (capteur 1)", "°C", Icons.Rounded.Thermostat, TEMPS, -10.0, 80.0, size = 2) { it[1] - 40.0 },
        p(0x67, "Temp. liquide (capteur 1)", "°C", Icons.Rounded.DeviceThermostat, TEMPS, 40.0, 130.0, size = 2) { it[1] - 40.0 },
        p(0x5C, "Temp. huile", "°C", Icons.Rounded.DeviceThermostat, TEMPS, 20.0, 150.0) { it[0] - 40.0 },
        p(0x84, "Temp. surface collecteur", "°C", Icons.Rounded.Thermostat, TEMPS, 0.0, 150.0) { it[0] - 40.0 },
        p(0x46, "Temp. extérieure", "°C", Icons.Rounded.Thermostat, TEMPS, -20.0, 50.0, slow = true) { it[0] - 40.0 },
        p(0x11, "Papillon", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x45, "Papillon relatif", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x47, "Papillon B", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x48, "Papillon C", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x4C, "Papillon commandé", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x49, "Pédale d'accélérateur D", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x4A, "Pédale d'accélérateur E", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x4B, "Pédale d'accélérateur F", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x5A, "Pédale d'accélérateur relative", "%", Icons.Rounded.Tune, INTAKE, 0.0, 100.0) { pct(it[0]) },
        p(0x0B, "Pression collecteur (MAP)", "kPa", Icons.Rounded.Air, INTAKE, 0.0, 250.0) { it[0].toDouble() },
        p(0x10, "Débit d'air (MAF)", "g/s", Icons.Rounded.Waves, INTAKE, 0.0, 100.0, decimals = 1, size = 2) { u16(it) / 100 },
        p(0x33, "Pression atmosphérique", "kPa", Icons.Rounded.Air, INTAKE, 80.0, 110.0, slow = true) { it[0].toDouble() },
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
        p(0x2C, "EGR commandée", "%", Icons.Rounded.FilterAlt, EMISSIONS, 0.0, 100.0) { pct(it[0]) },
        p(0x2D, "Erreur EGR", "%", Icons.Rounded.FilterAlt, EMISSIONS, -100.0, 100.0, decimals = 1) { trim(it[0]) },
        p(0x2E, "Purge canister commandée", "%", Icons.Rounded.FilterAlt, EMISSIONS, 0.0, 100.0) { pct(it[0]) },
        p(0x32, "Pression vapeurs EVAP", "Pa", Icons.Rounded.FilterAlt, EMISSIONS, -8192.0, 8192.0, decimals = 1, size = 2) { s16(it) / 4 },
        p(0x53, "Pression vapeurs EVAP absolue", "kPa", Icons.Rounded.FilterAlt, EMISSIONS, 0.0, 330.0, decimals = 2, size = 2) { u16(it) / 200 },
        p(0x54, "Pression vapeurs EVAP", "Pa", Icons.Rounded.FilterAlt, EMISSIONS, -32767.0, 32768.0, size = 2) { u16(it) - 32767 },
        p(0x42, "Tension calculateur", "V", Icons.Rounded.BatteryChargingFull, ELEC, 11.0, 15.0, decimals = 2, size = 2) { u16(it) / 1000 },
        p(0x5B, "Batterie hybride restante", "%", Icons.Rounded.BatteryChargingFull, ELEC, 0.0, 100.0, slow = true) { pct(it[0]) },
    ) + o2Narrow + o2WideV + o2WideI + catalyst + secondaryO2

    /** Toutes les définitions connues. */
    val all: List<PidDef> = headline + others

    private val byPid: Map<Int, List<PidDef>> = all.groupBy { pidNumber(it) }

    /** Données enregistrées hors OBD2 (téléphone, boîtier) : jamais interrogées via une requête. */
    val extras: List<PidDef> = listOf(
        PidDef("elm_volt", "Tension batterie (prise OBD)", "V", Icons.Rounded.BatteryChargingFull, "ATRV", decimals = 1, min = 11.0, max = 15.0, category = ELEC) { 0.0 },
    )

    fun pidNumber(p: PidDef): Int = p.request.substring(2, 4).toInt(16)

    /** PID déclaré par le véhicule mais absent du catalogue : enregistré en valeur brute. */
    private fun raw(pid: Int) = PidDef(
        id = "std%02X".format(pid), label = "PID 01%02X (valeur brute)".format(pid), unit = "", icon = Icons.Rounded.Memory,
        request = "01%02X".format(pid), min = 0.0, max = 255.0, slow = true, category = RAW,
    ) { d -> d.take(4).fold(0L) { acc, b -> acc * 256 + b }.toDouble() }

    /**
     * Données retirées de l'application (ni affichées, ni enregistrées) : antipollution (sauf la purge canister commandée 0x2E),
     * tension du calculateur (0x42, la tension vient de la prise OBD), et les PIDs d'état 0x03 (état de la boucle carburant),
     * 0x13 (sondes O2 présentes) et 0x1C (norme OBD du véhicule).
     */
    val HIDDEN: Set<Int> = setOf(
        0x01, 0x03, 0x13, 0x1C, 0x2C, 0x2D, 0x32, 0x3C, 0x3D, 0x3E, 0x3F, 0x42, 0x4D, 0x4E, 0x53, 0x54,
    )

    /** Définitions de toutes les données à lire pour un ensemble de PIDs supportés. */
    fun forSupported(sup: Set<Int>): List<PidDef> =
        sup.filter { it % 0x20 != 0 && it != 0x02 && it !in HIDDEN }.sorted().flatMap { n -> byPid[n] ?: listOf(raw(n)) }

    /** Retrouve une définition par identifiant (y compris valeurs brutes et données téléphone). */
    fun byId(id: String): PidDef? {
        all.find { it.id == id }?.let { return if (pidNumber(it) in HIDDEN) null else it }
        extras.find { it.id == id }?.let { return it }
        val m = Regex("^std([0-9A-F]{2})$").find(id) ?: return null
        return m.groupValues[1].toInt(16).takeIf { it !in HIDDEN }?.let { raw(it) }
    }
}
