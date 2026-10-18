package com.cardeck.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.graphics.vector.ImageVector

/** Une donnée affichée par un tableau de bord (téléphone ou Android Auto). */
data class Gauge(val def: PidDef, val value: Double?, val note: String?)

/** Tableaux de bord Standard et Sport : données lues et valeurs calculées. */
object DashGauges {
    /** Données lues pour chaque tableau de bord (identifiants des PIDs standard). */
    val STANDARD_IDS = listOf("std0D", "std0C", "std05", "std5C", "std2F", "std42", "std10", "std5E")
    val SPORT_IDS = listOf("std0F", "std0B", "std33", "std0E")

    /** Lus moins souvent (varient peu). */
    val SLOW_IDS = setOf("std2F", "std33")

    const val MISSING = "Non fourni par le véhicule"

    private const val BARO_DEFAULT_KPA = 101.3

    /** Essence : rapport air/carburant stœchiométrique et masse volumique (g/L). */
    private const val AFR = 14.7
    private const val FUEL_DENSITY = 745.0

    private fun pseudo(id: String, label: String, unit: String, icon: ImageVector, decimals: Int, min: Double, max: Double) =
        PidDef(id, label, unit, icon, "", decimals = decimals, min = min, max = max) { 0.0 }

    val MAP_BAR = pseudo("dash_map", "Pression collecteur (MAP)", "bar", Icons.Rounded.Air, 2, 0.0, 2.5)
    val BOOST_BAR = pseudo("dash_boost", "Pression de suralimentation", "bar", Icons.Rounded.Speed, 2, -1.0, 1.5)
    val VOLT = pseudo("dash_volt", "Tension batterie", "V", Icons.Rounded.BatteryChargingFull, 1, 11.0, 15.0)
    val CONSO_100 = pseudo("dash_conso", "Consommation", "L/100 km", Icons.Rounded.LocalGasStation, 1, 0.0, 20.0)
    val CONSO_H = pseudo("dash_conso_h", "Consommation", "L/h", Icons.Rounded.LocalGasStation, 1, 0.0, 5.0)

    /**
     * Jauges du tableau de bord à partir des dernières valeurs lues.
     * [sup] : PIDs déclarés par le véhicule (null = pas encore connus, aucune donnée n'est marquée absente).
     */
    fun gauges(sport: Boolean, values: Map<String, Double>, sup: Set<Int>?): List<Gauge> {
        fun std(id: String) = StdPids.all.first { it.id == id }
        fun has(id: String) = sup == null || StdPids.pidNumber(std(id)) in sup
        fun gauge(id: String) = Gauge(std(id), values[id], if (has(id)) null else MISSING)
        val list = if (!sport) {
            // Consommation : débit de carburant (PID 5E) ou calcul à partir du débit d'air (MAF).
            val speed = values["std0D"]
            val lph = values["std5E"] ?: values["std10"]?.let { it / AFR * 3600 / FUEL_DENSITY }
            val conso = when {
                lph == null -> Gauge(CONSO_100, null, if (has("std5E") || has("std10")) null else MISSING)
                speed != null && speed >= 5 -> Gauge(CONSO_100, lph / speed * 100, null)
                else -> Gauge(CONSO_H, lph, "À l'arrêt")
            }
            val volt = if (sup != null && 0x42 in sup) Gauge(VOLT, values["std42"], "Calculateur") else Gauge(VOLT, values["elm_volt"], "Prise OBD")
            STANDARD_IDS.take(5).map { gauge(it) } + listOf(conso, volt)
        } else {
            val map = values["std0B"]
            val baro = values["std33"] ?: BARO_DEFAULT_KPA
            listOf(
                gauge("std0F"),
                Gauge(MAP_BAR, map?.let { it / 100 }, if (has("std0B")) "Absolue" else MISSING),
                Gauge(BOOST_BAR, map?.let { (it - baro) / 100 }, if (has("std0B")) "Relative (MAP − pression atmosphérique)" else MISSING),
                gauge("std0E"),
            )
        }
        return list.map { if (it.value?.isNaN() == true) it.copy(value = null) else it }
    }
}
