package com.cardeck.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Cyclone
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.ui.graphics.vector.ImageVector

/** PIDs standard du service 01 (SAE J1979), lus sur tout véhicule OBD2 qui les déclare. */
object StdPids {
    private fun u16(d: IntArray) = (d[0] * 256 + d[1]).toDouble()

    private fun p(
        pid: Int, label: String, unit: String, icon: ImageVector, min: Double, max: Double,
        decimals: Int = 0, slow: Boolean = false, size: Int = 1, decode: (IntArray) -> Double,
    ) = PidDef(
        id = "std%02X".format(pid), label = label, unit = unit, icon = icon, request = "01%02X".format(pid),
        decimals = decimals, min = min, max = max, slow = slow, size = size, decode = decode,
    )

    /** Grandeurs affichées en grandes jauges (si le véhicule les supporte). */
    val headline: List<PidDef> = listOf(
        p(0x0C, "Régime", "tr/min", Icons.Rounded.Cyclone, 0.0, 7000.0, size = 2) { u16(it) / 4 },
        p(0x0D, "Vitesse", "km/h", Icons.Rounded.Speed, 0.0, 220.0) { it[0].toDouble() },
        p(0x05, "Temp. liquide", "°C", Icons.Rounded.DeviceThermostat, 40.0, 130.0) { it[0] - 40.0 },
        p(0x2F, "Carburant", "%", Icons.Rounded.LocalGasStation, 0.0, 100.0, slow = true) { it[0] * 100 / 255.0 },
    )

    val others: List<PidDef> = listOf(
        p(0x04, "Charge moteur", "%", Icons.Rounded.QueryStats, 0.0, 100.0) { it[0] * 100 / 255.0 },
        p(0x43, "Charge absolue", "%", Icons.Rounded.QueryStats, 0.0, 100.0, size = 2) { u16(it) * 100 / 255 },
        p(0x11, "Papillon", "%", Icons.Rounded.Tune, 0.0, 100.0) { it[0] * 100 / 255.0 },
        p(0x45, "Papillon relatif", "%", Icons.Rounded.Tune, 0.0, 100.0) { it[0] * 100 / 255.0 },
        p(0x4C, "Papillon commandé", "%", Icons.Rounded.Tune, 0.0, 100.0) { it[0] * 100 / 255.0 },
        p(0x49, "Pédale d'accélérateur", "%", Icons.Rounded.Tune, 0.0, 100.0) { it[0] * 100 / 255.0 },
        p(0x0B, "Pression collecteur (MAP)", "kPa", Icons.Rounded.Air, 0.0, 250.0) { it[0].toDouble() },
        p(0x10, "Débit d'air (MAF)", "g/s", Icons.Rounded.Waves, 0.0, 100.0, decimals = 1, size = 2) { u16(it) / 100 },
        p(0x0F, "Temp. admission", "°C", Icons.Rounded.Thermostat, -10.0, 80.0) { it[0] - 40.0 },
        p(0x0E, "Avance à l'allumage", "°", Icons.Rounded.Sync, -20.0, 60.0, decimals = 1) { it[0] / 2.0 - 64 },
        p(0x06, "Correction carburant CT", "%", Icons.Rounded.LocalGasStation, -25.0, 25.0, decimals = 1) { (it[0] - 128) * 100 / 128.0 },
        p(0x07, "Correction carburant LT", "%", Icons.Rounded.LocalGasStation, -25.0, 25.0, decimals = 1) { (it[0] - 128) * 100 / 128.0 },
        p(0x44, "Richesse (λ commandé)", "λ", Icons.Rounded.LocalGasStation, 0.5, 1.5, decimals = 2, size = 2) { u16(it) * 2 / 65536 },
        p(0x0A, "Pression carburant", "kPa", Icons.Rounded.LocalGasStation, 0.0, 765.0) { it[0] * 3.0 },
        p(0x23, "Pression de rampe", "kPa", Icons.Rounded.LocalGasStation, 0.0, 20000.0, size = 2) { u16(it) * 10 },
        p(0x5E, "Consommation", "L/h", Icons.Rounded.LocalGasStation, 0.0, 30.0, decimals = 1, size = 2) { u16(it) / 20 },
        p(0x5C, "Temp. huile", "°C", Icons.Rounded.DeviceThermostat, 20.0, 150.0) { it[0] - 40.0 },
        p(0x33, "Pression atmosphérique", "kPa", Icons.Rounded.Air, 80.0, 110.0, slow = true) { it[0].toDouble() },
        p(0x46, "Temp. extérieure", "°C", Icons.Rounded.Thermostat, -20.0, 50.0, slow = true) { it[0] - 40.0 },
        p(0x42, "Tension calculateur", "V", Icons.Rounded.BatteryChargingFull, 11.0, 15.0, decimals = 1, size = 2) { u16(it) / 1000 },
        p(0x1F, "Durée moteur tournant", "min", Icons.Rounded.Timer, 0.0, 120.0, slow = true, size = 2) { u16(it) / 60 },
        p(0x21, "Distance voyant allumé", "km", Icons.Rounded.Route, 0.0, 1000.0, slow = true, size = 2) { u16(it) },
        p(0x31, "Distance depuis effacement", "km", Icons.Rounded.Route, 0.0, 5000.0, slow = true, size = 2) { u16(it) },
    )

    val all: List<PidDef> = headline + others

    fun pidNumber(p: PidDef): Int = p.request.substring(2, 4).toInt(16)
}
