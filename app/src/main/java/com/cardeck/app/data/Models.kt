package com.cardeck.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.TurnSharpRight
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.NumberFormat
import java.util.Locale

/** Formatage à la française : 2 140 / 6,4 */
fun fr(n: Number, decimals: Int = 0): String {
    val f = NumberFormat.getNumberInstance(Locale.FRANCE)
    f.minimumFractionDigits = decimals
    f.maximumFractionDigits = decimals
    return f.format(n)
}

enum class Severity(val label: String) { Crit("Critique"), Mid("Moyen"), Low("Mineur") }

data class Dtc(
    val code: String,
    val sev: Severity,
    val found: Boolean,
    val title: String,
    val sys: String,
    val sysIcon: ImageVector,
    val desc: String,
    val causes: List<String>,
    val recos: List<String>,
    val ff: List<String>?,
)

val DTC_DB: List<Dtc> = listOf(
    Dtc(
        "P0301", Severity.Crit, true, "Raté d'allumage détecté, cylindre 1", "Moteur · Allumage", Icons.Rounded.LocalFireDepartment,
        "Le calculateur moteur a mesuré des combustions incomplètes répétées dans le cylindre 1. Le moteur peut trembler au ralenti et perdre de la puissance à l'accélération.",
        listOf("Bougie d'allumage usée ou encrassée", "Bobine d'allumage défectueuse", "Injecteur du cylindre 1 obstrué"),
        listOf("Évitez les fortes accélérations et les longs trajets", "Faites contrôler bougies et bobines rapidement", "Un raté prolongé peut endommager le catalyseur"),
        listOf("2 140 tr/min", "64 km/h", "91 °C", "47 %"),
    ),
    Dtc(
        "U0100", Severity.Crit, true, "Perte de communication avec le calculateur moteur", "Réseau CAN", Icons.Rounded.Lan,
        "Un ou plusieurs calculateurs n'ont plus reçu de messages du calculateur moteur pendant plus de 500 ms. Des voyants peuvent s'allumer simultanément.",
        listOf("Connecteur du calculateur oxydé ou desserré", "Faisceau CAN endommagé", "Tension batterie trop faible au démarrage"),
        listOf("Vérifiez la batterie et les cosses", "Ne roulez pas si le moteur se coupe", "Diagnostic du réseau en atelier conseillé"),
        listOf("0 tr/min", "0 km/h", "22 °C", "0 %"),
    ),
    Dtc(
        "P0420", Severity.Mid, true, "Efficacité du catalyseur sous le seuil, banc 1", "Échappement · Antipollution", Icons.Rounded.FilterAlt,
        "La sonde lambda aval détecte une variation d'oxygène trop proche de la sonde amont : le catalyseur ne traite plus correctement les gaz d'échappement.",
        listOf("Catalyseur usé ou colmaté", "Sonde lambda aval défaillante", "Fuite à l'échappement"),
        listOf("Le véhicule peut échouer au contrôle technique", "Contrôlez les sondes avant de remplacer le catalyseur"),
        listOf("2 600 tr/min", "92 km/h", "89 °C", "38 %"),
    ),
    Dtc(
        "P0171", Severity.Mid, true, "Mélange trop pauvre, banc 1", "Moteur · Alimentation", Icons.Rounded.LocalGasStation,
        "Le calculateur doit enrichir le mélange au-delà de sa plage de correction. Trop d'air ou pas assez de carburant arrive dans les cylindres.",
        listOf("Prise d'air après le débitmètre", "Débitmètre d'air encrassé", "Pression de carburant insuffisante"),
        listOf("Inspectez les durites d'admission", "Nettoyez le débitmètre d'air"),
        listOf("820 tr/min", "0 km/h", "88 °C", "21 %"),
    ),
    Dtc(
        "P0442", Severity.Low, true, "Petite fuite détectée, système EVAP", "Réservoir · Vapeurs", Icons.Rounded.WaterDrop,
        "Le test d'étanchéité du circuit de récupération des vapeurs d'essence a mesuré une fuite de faible diamètre.",
        listOf("Bouchon de réservoir mal serré", "Joint du bouchon fissuré", "Durite EVAP poreuse"),
        listOf("Resserrez le bouchon jusqu'au clic", "Effacez le code et surveillez sa réapparition"),
        listOf("760 tr/min", "0 km/h", "84 °C", "18 %"),
    ),
    Dtc(
        "B1318", Severity.Low, true, "Tension batterie faible", "Carrosserie · Électrique", Icons.Rounded.BatteryAlert,
        "Le boîtier de servitude a enregistré une tension inférieure à 11,5 V pendant le démarrage.",
        listOf("Batterie en fin de vie", "Consommateur resté allumé", "Alternateur faible"),
        listOf("Testez la batterie à l'arrêt et moteur tournant", "Remplacez-la si elle a plus de 5 ans"),
        listOf("0 tr/min", "0 km/h", "12 °C", "0 %"),
    ),
    Dtc(
        "P0128", Severity.Mid, false, "Température de liquide sous le seuil du thermostat", "Moteur · Refroidissement", Icons.Rounded.DeviceThermostat,
        "Le moteur met trop de temps à atteindre sa température de fonctionnement.",
        listOf("Thermostat bloqué ouvert", "Sonde de température défaillante"), listOf("Remplacez le thermostat"), null,
    ),
    Dtc(
        "P0500", Severity.Mid, false, "Capteur de vitesse véhicule", "Transmission", Icons.Rounded.Settings,
        "Le signal de vitesse du véhicule est absent ou incohérent.",
        listOf("Capteur de vitesse défectueux", "Câblage endommagé"), listOf("Contrôlez le capteur et son connecteur"), null,
    ),
    Dtc(
        "P0113", Severity.Low, false, "Température d'air d'admission, signal haut", "Moteur · Admission", Icons.Rounded.Thermostat,
        "La sonde de température d'air renvoie une tension trop élevée.",
        listOf("Sonde débranchée", "Faisceau coupé"), listOf("Vérifiez le connecteur de la sonde"), null,
    ),
)

val SYSTEMS = listOf("Moteur", "Boîte de vitesses", "ABS / ESP", "Airbags", "Climatisation", "Carrosserie", "Réseau CAN", "Antipollution")

/** Valeurs moteur en direct. */
data class Live(
    val speed: Double = 52.0, val rpm: Double = 2140.0, val temp: Double = 88.0, val fuel: Double = 64.0,
    val volt: Double = 14.2, val load: Double = 34.0, val boost: Double = .4, val iat: Double = 28.0,
    val maf: Double = 14.0, val conso: Double = 6.4,
)

data class WidgetDef(
    val id: String, val label: String, val icon: ImageVector, val unit: String,
    val value: (Live) -> String, val pct: (Live) -> Double,
)

val WIDGETS: List<WidgetDef> = listOf(
    WidgetDef("volt", "Batterie", Icons.Rounded.BatteryChargingFull, "V", { fr(it.volt, 1) }, { (it.volt - 11) / 4 }),
    WidgetDef("conso", "Conso. instantanée", Icons.Rounded.LocalGasStation, "L/100 km", { if (it.conso > 0) fr(it.conso, 1) else "—" }, { it.conso / 20 }),
    WidgetDef("load", "Charge moteur", Icons.Rounded.QueryStats, "%", { fr(it.load) }, { it.load / 100 }),
    WidgetDef("boost", "Pression turbo", Icons.Rounded.Air, "bar", { fr(it.boost, 2) }, { it.boost / 1.5 }),
    WidgetDef("iat", "Temp. admission", Icons.Rounded.Thermostat, "°C", { fr(it.iat) }, { it.iat / 80 }),
    WidgetDef("maf", "Débit d'air", Icons.Rounded.Waves, "g/s", { fr(it.maf, 1) }, { it.maf / 60 }),
)

val DEFAULT_WIDGETS = setOf("volt", "conso", "load", "boost")

data class ObdDevice(val id: String, val name: String, val sub: String, val signal: Int, val wifi: Boolean)

val BT_DEVICES = listOf(
    ObdDevice("mx", "OBDLink MX+", "Bluetooth 5.0 · Signal fort", 3, false),
    ObdDevice("vg", "Vgate iCar Pro", "Bluetooth LE · Signal moyen", 2, false),
    ObdDevice("elm", "OBDII ELM327 v1.5", "Bluetooth 2.0 · Signal faible", 1, false),
)
val WIFI_DEVICES = listOf(
    ObdDevice("wf1", "WiFi_OBDII", "192.168.0.10:35000 · Signal fort", 3, true),
    ObdDevice("wf2", "Vgate iCar 2 Wi-Fi", "192.168.0.10:35000 · Signal moyen", 2, true),
)

data class Vehicle(val id: String, val name: String, val plate: String, val year: String, val fuel: String) {
    val sub get() = "$plate · $year · $fuel"
}

val DEFAULT_VEHICLES = listOf(
    Vehicle("308", "Peugeot 308", "GH-482-KT", "2019", "Diesel"),
    Vehicle("clio", "Renault Clio V", "FT-219-PL", "2021", "Essence"),
    Vehicle("yaris", "Toyota Yaris", "EK-703-RB", "2020", "Hybride"),
)

val BRANDS = listOf("Peugeot", "Renault", "Citroën", "Volkswagen", "Toyota", "Dacia", "BMW", "Autre")

enum class EventType(val label: String, val icon: ImageVector) {
    Brake("Freinage brusque", Icons.Rounded.TrendingDown),
    Accel("Accélération vive", Icons.Rounded.TrendingUp),
    Turn("Virage serré", Icons.Rounded.TurnSharpRight),
}

data class TripEvent(val type: EventType, val pathIdx: Int, val time: String, val where: String, val value: String)

data class Trip(
    val id: Int, val day: Int, val date: String, val start: String, val end: String,
    val from: String, val to: String, val dur: String, val dist: String, val avg: String,
    val vmax: String, val conso: String, val score: Int, val path: Int, val events: List<TripEvent>,
)

/** Tracés (viewBox 400×480) utilisés pour dessiner la carte stylisée du trajet. */
val TRIP_PATHS: List<List<Pair<Float, Float>>> = listOf(
    listOf(56f to 400f, 56f to 318f, 150f to 306f, 162f to 228f, 262f to 216f, 262f to 132f, 340f to 126f, 340f to 64f),
    listOf(340f to 410f, 262f to 400f, 262f to 300f, 120f to 290f, 110f to 190f, 200f to 126f, 210f to 60f),
    listOf(56f to 80f, 150f to 126f, 170f to 200f, 300f to 210f, 318f to 300f, 230f to 380f),
)

val TRIPS: List<Trip> = listOf(
    Trip(1, 0, "Aujourd'hui", "08:12", "08:47", "Domicile", "Bureau · La Défense", "35 min", "18,4", "32", "88 km/h", "6,1", 86, 0, listOf(
        TripEvent(EventType.Brake, 2, "08:21", "Av. Charles de Gaulle", "−0,48 g"),
        TripEvent(EventType.Turn, 4, "08:30", "Rond-point de la Défense", "0,41 g"),
        TripEvent(EventType.Accel, 6, "08:39", "Bd Circulaire", "+0,36 g"),
    )),
    Trip(2, 0, "Aujourd'hui", "12:30", "12:44", "Bureau", "Restaurant Le Central", "14 min", "4,2", "18", "50 km/h", "7,4", 78, 2, listOf(
        TripEvent(EventType.Accel, 3, "12:37", "Rue Jean Jaurès", "+0,39 g"),
    )),
    Trip(3, 1, "Hier", "18:05", "18:52", "Bureau · La Défense", "Domicile", "47 min", "19,1", "24", "84 km/h", "6,6", 72, 1, listOf(
        TripEvent(EventType.Brake, 1, "18:11", "Quai de Dion-Bouton", "−0,52 g"),
        TripEvent(EventType.Brake, 3, "18:29", "Pont de Neuilly", "−0,45 g"),
        TripEvent(EventType.Accel, 4, "18:40", "Av. du Général Leclerc", "+0,33 g"),
    )),
    Trip(4, 3, "Jeudi 1 oct.", "09:40", "11:15", "Domicile", "Chartres", "1 h 35", "92,7", "59", "131 km/h", "5,4", 91, 2, emptyList()),
    Trip(5, 9, "Vendredi 25 sept.", "17:20", "17:41", "Domicile", "Supermarché", "21 min", "7,8", "22", "70 km/h", "6,9", 88, 1, listOf(
        TripEvent(EventType.Turn, 2, "17:29", "Rue de la Gare", "0,44 g"),
    )),
    Trip(6, 20, "Lundi 14 sept.", "06:05", "06:52", "Domicile", "Aéroport d'Orly", "47 min", "38,5", "49", "112 km/h", "5,8", 83, 0, listOf(
        TripEvent(EventType.Brake, 4, "06:31", "A86", "−0,50 g"),
    )),
)

val TRIP_FILTERS = listOf("all" to "Tous", "today" to "Aujourd'hui", "7" to "7 jours", "30" to "30 jours")
