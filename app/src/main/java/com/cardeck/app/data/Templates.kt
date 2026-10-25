package com.cardeck.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AirlineSeatReclineNormal
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * PID lisible sur le véhicule.
 * [request] = requête envoyée à l'ELM327 (ex. « 010C » ou « 221234 »), [header] = en-tête CAN optionnel (ATSH),
 * [decode] reçoit les octets de données qui suivent l'écho du service/PID.
 */
data class PidDef(
    val id: String,
    val label: String,
    val unit: String,
    val icon: ImageVector,
    val request: String,
    val header: String? = null,
    val decimals: Int = 0,
    val min: Double = 0.0,
    val max: Double = 100.0,
    /** Lu moins souvent (valeur qui varie peu). */
    val slow: Boolean = false,
    /** Nombre minimal d'octets de données attendus (réponse trop courte ignorée). */
    val size: Int = 1,
    /** Catégorie d'affichage (tableau de bord, historique des trajets). */
    val category: String = "Autres",
    /** Donnée lue par écoute du bus CAN : identifiant de la trame (null = requête de diagnostic). */
    val can: Int? = null,
    /** Durée d'écoute minimale (ms) pour voir passer la trame au moins une fois. */
    val window: Long = 200,
    /** Libellés des états (0, 1, 2…) pour une donnée tout-ou-rien ou à plusieurs positions. */
    val states: List<String>? = null,
    /** Garder la plus grande valeur vue pendant l'écoute (signal qui clignote). */
    val peak: Boolean = false,
    val decode: (IntArray) -> Double,
) {
    /** Valeur mise en forme pour l'affichage (état en clair ou nombre à la française), sans l'unité. */
    fun text(v: Double): String = states?.getOrNull(v.toInt()) ?: fr(v, decimals)

    /** Valeur avec son unité (« 92 km/h », « Enfoncée »). */
    fun withUnit(v: Double): String = if (states != null) text(v) else "${text(v)} $unit".trim()
}

/**
 * Calculateur interrogé pour l'identification (références, logiciels, VIN), en lecture seule.
 * [rx] : adresse de réponse ; [uds] : UDS (22 xxxx, session 10 01) sinon KWP2000 (1A xx, session 10 81).
 */
data class EcuIdent(val name: String, val tx: String, val rx: String, val uds: Boolean, val requests: List<String>)

/** Modèle de véhicule : caractéristiques + fonctionnalités dédiées (PIDs…). Un profil est créé à partir d'un modèle. */
data class VehicleTemplate(
    val id: String,
    val brand: String,
    val model: String,
    val code: String,
    val engine: String,
    val power: String,
    val fuel: String,
    val protocol: String,
    /** PIDs spécifiques au véhicule, affichés au tableau de bord. */
    val pids: List<PidDef>,
    /** Réglages de codage disponibles pour ce modèle. */
    val codings: List<CodingDef> = emptyList(),
    /** Calcul de la clé SecurityAccess (niveau, graine) → clé, si un module l'exige. */
    val securityKey: ((Int, IntArray) -> IntArray)? = null,
    /** Protocole imposé à l'ELM327 (ATSP), connu pour ce modèle : évite la recherche automatique. */
    val elmProtocol: String? = null,
    /** Lecture seule : seuls les services de lecture sont envoyés (ni effacement des défauts, ni écriture). */
    val readOnly: Boolean = false,
    /** Commandes AT propres au modèle, envoyées après l'initialisation (contrôle de flux…). */
    val elmInit: List<String> = emptyList(),
    /** Raison pour laquelle le codage est impossible sur ce modèle (null = pas encore de fonctions, ou codage disponible). */
    val codingImpossible: String? = null,
    /** Pas encore sélectionnable (affiché « Arrive bientôt »). */
    val comingSoon: Boolean = false,
    /** Données du modèle ajoutées au tableau de bord Sport. */
    val dashSport: List<String> = emptyList(),
    /** Calculateurs à interroger pour l'identification. */
    val idents: List<EcuIdent> = emptyList(),
    /** Calculateur d'airbags (page « État sécurité ») : 22 1000 = circuits d'allumage, 22 1F00 = contact / tension. */
    val airbag: EcuIdent? = null,
) {
    val fullName get() = "$brand $model $code".trim()
    val specs get() = "$engine · $power · $fuel"
}

private fun u16(d: IntArray, i: Int) = (d[i] * 256 + d[i + 1]).toDouble()
private fun bit(d: IntArray, i: Int, mask: Int) = if (d[i] and mask != 0) 1.0 else 0.0

private fun bus(
    id: String, label: String, unit: String, icon: ImageVector, can: Int, category: String, size: Int, window: Long,
    min: Double = 0.0, max: Double = 1.0, decimals: Int = 0, slow: Boolean = false, states: List<String>? = null, peak: Boolean = false,
    decode: (IntArray) -> Double,
) = PidDef(
    id, label, unit, icon, request = "", decimals = decimals, min = min, max = max, slow = slow, size = size,
    category = category, can = can, window = window, states = states, peak = peak, decode = decode,
)

private const val PEDALS = "Pédales et boîte"
private const val CABIN = "Habitacle"
private const val COUNTERS = "Compteurs"
private val ONOFF = listOf("Éteinte", "Allumée")

/**
 * Données diffusées sur le bus CAN de la Swift ZC33S (rétro-ingénierie, uniquement les mesures confirmées).
 * Fenêtres d'écoute selon la fréquence des trames : ~100/s → 40 ms, ~50/s → 60 ms, ~10/s → 200 ms, ~1/s → 1,2 s.
 */
private val SWIFT_BUS = listOf(
    // Carburant : octet 5 de 0x3B9 (lissé), 240 = plein ≈ 37 L. L'octet 4 (flotteur brut) n'est jamais utilisé.
    bus("std2F", "Carburant", "%", Icons.Rounded.LocalGasStation, 0x3B9, "Carburant", 6, 200, max = 100.0, slow = true) { (it[5] / 240.0 * 100).coerceAtMost(100.0) },
    bus("swift_fuel_l", "Carburant (litres, estimé)", "L", Icons.Rounded.LocalGasStation, 0x3B9, "Carburant", 6, 200, max = 37.0, decimals = 1, slow = true) { it[5] / 6.5 },
    bus("std46", "Temp. extérieure", "°C", Icons.Rounded.Thermostat, 0x3BA, "Températures", 1, 200, min = -20.0, max = 50.0, slow = true) { it[0] / 2.0 - 40 },
    bus("swift_odo", "Kilométrage", "km", Icons.Rounded.Speed, 0x460, COUNTERS, 3, 1200, max = 300_000.0, slow = true) { it[0] * 65536.0 + it[1] * 256 + it[2] },
    bus("swift_brake", "Pédale de frein", "", Icons.Rounded.Speed, 0x318, PEDALS, 1, 200, states = listOf("Relâchée", "Enfoncée")) { bit(it, 0, 0x04) },
    bus("swift_clutch", "Embrayage", "", Icons.Rounded.Speed, 0x1D0, PEDALS, 6, 40, states = listOf("Relâché", "Enfoncé")) { if (it[5] and 0xC0 == 0xC0) 1.0 else 0.0 },
    bus("swift_handbrake", "Frein à main", "", Icons.Rounded.Speed, 0x1D0, PEDALS, 6, 40, states = listOf("Desserré", "Serré")) { bit(it, 5, 0x10) },
    bus("swift_reverse", "Marche arrière", "", Icons.Rounded.Speed, 0x3A1, PEDALS, 2, 200, states = listOf("Non", "Engagée")) { bit(it, 1, 0x40) },
    bus("swift_door_d", "Porte conducteur", "", Icons.Rounded.DirectionsCar, 0x3D5, CABIN, 4, 200, states = listOf("Fermée", "Ouverte")) { bit(it, 3, 0x80) },
    bus("swift_door_p", "Porte passager", "", Icons.Rounded.DirectionsCar, 0x3D5, CABIN, 4, 200, states = listOf("Fermée", "Ouverte")) { bit(it, 3, 0x40) },
    bus("swift_trunk", "Coffre", "", Icons.Rounded.DirectionsCar, 0x3D5, CABIN, 4, 200, states = listOf("Fermé", "Ouvert")) { bit(it, 3, 0x04) },
    bus("swift_belt", "Ceinture conducteur", "", Icons.Rounded.AirlineSeatReclineNormal, 0x3D1, CABIN, 6, 200, states = listOf("Débouclée", "Bouclée")) { bit(it, 5, 0x80) },
    bus("swift_ac", "Climatisation", "", Icons.Rounded.AcUnit, 0x3D0, CABIN, 2, 200, states = ONOFF) { bit(it, 1, 0x10) },
    bus("swift_wipers", "Essuie-glaces", "", Icons.Rounded.WaterDrop, 0x3D4, CABIN, 4, 200, states = listOf("Arrêt", "Marche")) { bit(it, 3, 0x20) },
)

/** Identifiants KWP 1A essayés sur les calculateurs Suzuki (références, versions). */
private val KWP_IDS = listOf("1A8A", "1A8B", "1A8C", "1A8D", "1A8E", "1A9E", "1A9F")

val SWIFT_SPORT_ZC33S = VehicleTemplate(
    id = "suzuki-swift-sport-zc33s",
    brand = "Suzuki",
    model = "Swift Sport",
    code = "ZC33S",
    engine = "K14C 1.4 BoosterJet",
    power = "140 ch",
    fuel = "Essence",
    protocol = "ISO 15765-4 CAN 11 bits 500 kb/s",
    elmProtocol = "6",
    // Rétro-ingénierie ZC33S : lecture seule (liste blanche des services de lecture).
    readOnly = true,
    codingImpossible = "Le BCM (calculateur d'habitacle : vitres, verrouillage, clé) et le combiné d'instruments ne répondent à aucune demande " +
        "de diagnostic, ni sur le CAN ni sur la ligne K (balayage complet du 19/10). Sans eux, impossible de lire ou modifier une option " +
        "comme l'ouverture des vitres à la clé. Leurs informations restent lisibles de façon passive, sur le bus.",
    // Contrôle de flux vers le moteur, 5 ms entre trames : sans cela les longs blocs saturent le Bluetooth (BUFFER FULL).
    elmInit = listOf("ATFCSH7E0", "ATFCSD300005", "ATFCSM1"),
    pids = listOf(
        // Bloc constructeur KWP 21 00 du moteur (210 octets, ~230 ms) : l'huile n'existe pas en PID OBD (5C non supporté).
        // Octet 10 de la réponse (écho « 61 00 » compris) = huile − 40, confirmé sur bus 0x32F et démarrage à froid.
        PidDef(
            "std5C", "Temp. huile", "°C", Icons.Rounded.DeviceThermostat, request = "2100", header = "7E0",
            min = 20.0, max = 150.0, slow = true, size = 9, category = "Températures",
        ) { it[8] - 40.0 },
    ) + SWIFT_BUS,
    // Identification : le calculateur 7F1 (neutralisation des airbags, ISO 26021) n'est jamais interrogé.
    airbag = EcuIdent("Airbags", "793", "7A3", true, listOf("221000", "221F00")),
    idents = listOf(
        EcuIdent("Moteur", "7E0", "7E8", false, listOf("1A90", "1A8A", "1A8B", "1A8E", "1A9E", "1A9F", "1A98", "1A99")),
        EcuIdent("Airbags", "793", "7A3", true, listOf("22F190", "22F188", "22F101", "22F18C")),
        EcuIdent("ABS / ESP (estimé)", "798", "7A8", false, KWP_IDS),
        EcuIdent("Calculateur 776 (radar ?)", "776", "786", false, KWP_IDS),
        EcuIdent("Calculateur 778 (caméra ?)", "778", "788", false, KWP_IDS),
        EcuIdent("Calculateur 79A", "79A", "7AA", false, KWP_IDS),
    ),
    // Fonctions de codage Swift Sport à intégrer (données à fournir).
    codings = emptyList(),
)

val KIA_PICANTO_TA = VehicleTemplate(
    id = "kia-picanto-ta-g3la",
    brand = "Kia",
    model = "Picanto 1.0",
    code = "TA",
    comingSoon = true,
    engine = "G3LA 1.0 Kappa MPI",
    power = "69 ch",
    fuel = "Essence",
    protocol = "ISO 15765-4 CAN 11 bits 500 kb/s",
    elmProtocol = "6",
    // Liste des PIDs Picanto à intégrer (à fournir ultérieurement).
    pids = emptyList(),
    // Fonctions de codage Picanto à intégrer (données à fournir).
    codings = emptyList(),
)

val TEMPLATES = listOf(SWIFT_SPORT_ZC33S, KIA_PICANTO_TA)

/** Retrouve une donnée moteur (standard ou propre à un modèle) par son identifiant. */
fun pidById(id: String): PidDef? = TEMPLATES.flatMap { it.pids }.find { it.id == id } ?: StdPids.byId(id)

fun templateById(id: String): VehicleTemplate = TEMPLATES.find { it.id == id } ?: SWIFT_SPORT_ZC33S
