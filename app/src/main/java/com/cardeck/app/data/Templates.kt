package com.cardeck.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Thermostat
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
/**
 * Calculateur connu d'un modèle (relevé avec l'explorateur) : adresses de requête [tx] et de réponse [rx],
 * en CAN 29 bits si [wide]. [name] n'est renseigné que si le calculateur est identifié avec certitude.
 */
data class KnownEcu(val tx: Int, val rx: Int, val wide: Boolean = false, val name: String? = null) {
    /** En-tête de réponse tel que l'affiche le boîtier (« 734 », « 18DAF115 »). */
    val header get() = if (wide) "%08X".format(rx) else "%03X".format(rx)
    val label get() = name ?: "Calculateur " + if (wide) "%08X".format(tx) else "%03X".format(tx)
}

/** Modèle de véhicule : caractéristiques + fonctionnalités dédiées (PIDs…). Un profil est créé à partir d'un modèle. */
data class VehicleTemplate(
    val id: String,
    val brand: String,
    val model: String,
    val code: String,
    val engine: String,
    val power: String,
    val fuel: String,
    /** Carburant proposé par défaut pour le coût des trajets. */
    val defaultFuel: FuelType = FuelType.E10,
    val protocol: String,
    /** PIDs spécifiques au véhicule, affichés au tableau de bord. */
    val pids: List<PidDef>,
    /** Réglages de codage disponibles pour ce modèle. */
    val codings: List<CodingDef> = emptyList(),
    /** Calcul de la clé SecurityAccess (niveau, graine) → clé, si un module l'exige. */
    val securityKey: ((Int, IntArray) -> IntArray)? = null,
    /** Protocole imposé à l'ELM327 (ATSP), connu pour ce modèle : évite la recherche automatique. */
    val elmProtocol: String? = null,
    /** Lecture seule : seuls les services de lecture sont envoyés, plus l'effacement des codes défaut quand l'utilisateur le demande (jamais d'écriture). */
    val readOnly: Boolean = false,
    /** Commandes AT propres au modèle, envoyées après l'initialisation (contrôle de flux…). */
    val elmInit: List<String> = emptyList(),
    /** Raison pour laquelle le codage est impossible sur ce modèle (null = pas encore de fonctions, ou codage disponible). */
    val codingImpossible: String? = null,
    /** Pas encore sélectionnable (affiché « Arrive bientôt »). */
    val comingSoon: Boolean = false,
    /** Données du modèle ajoutées au tableau de bord Sport. */
    val dashSport: List<String> = emptyList(),
    /** Calculateurs connus du modèle : le diagnostic les interroge tous, y compris hors du protocole OBD de la voiture. */
    val ecus: List<KnownEcu> = emptyList(),
) {
    /** Noms des calculateurs identifiés, par en-tête de réponse. */
    val ecuNames: Map<String, String> get() = ecus.filter { it.name != null }.associate { it.header to it.name!! }

    val fullName get() = "$brand $model $code".trim()
    val specs get() = listOf(engine, power, fuel).filter { it.isNotBlank() }.joinToString(" · ")

    /** Profil dédié : données propres au modèle en plus des données OBD2 standard. */
    val dedicated get() = pids.isNotEmpty() || elmProtocol != null || ecus.isNotEmpty()
}

private fun u16(d: IntArray, i: Int) = (d[i] * 256 + d[i + 1]).toDouble()

private fun bus(
    id: String, label: String, unit: String, icon: ImageVector, can: Int, category: String, size: Int, window: Long,
    min: Double = 0.0, max: Double = 1.0, decimals: Int = 0, slow: Boolean = false, states: List<String>? = null, peak: Boolean = false,
    decode: (IntArray) -> Double,
) = PidDef(
    id, label, unit, icon, request = "", decimals = decimals, min = min, max = max, slow = slow, size = size,
    category = category, can = can, window = window, states = states, peak = peak, decode = decode,
)

private const val COUNTERS = "Compteurs"

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
)

val SWIFT_SPORT_ZC33S = VehicleTemplate(
    id = "suzuki-swift-sport-zc33s",
    brand = "Suzuki",
    model = "Swift Sport",
    code = "ZC33S",
    engine = "K14C 1.4 BoosterJet",
    power = "140 ch",
    fuel = "Essence",
    // K14C turbo : indice d'octane 95 minimum, SP98 conseillé.
    defaultFuel = FuelType.SP98,
    protocol = "ISO 15765-4 CAN 11 bits 500 kb/s",
    elmProtocol = "6",
    // Rétro-ingénierie ZC33S : lecture seule (liste blanche des services de lecture), sauf l'effacement des codes défaut demandé.
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
    // Fonctions de codage Swift Sport à intégrer (données à fournir).
    codings = emptyList(),
)

/**
 * Renault Clio V 1.0 TCe 90 : relevé de l'explorateur (balayage à l'arrêt, novembre 2026). OBD en CAN 29 bits.
 * Identifiés avec certitude : le moteur (18DA15F1, « ECM - EngineControl », Continental) et l'ABS / ESP
 * (18DA2DF1, Bosch, référence Renault 47660…). Les autres calculateurs répondent mais leur rôle n'est pas établi.
 */
val CLIO_V_TCE90 = VehicleTemplate(
    id = "renault-clio-v-1-0-tce-90ch-essence", // identifiant de la motorisation du catalogue : les profils existants en profitent
    brand = "Renault",
    model = "Clio",
    code = "V",
    engine = "1.0 TCe",
    power = "90 ch",
    fuel = "Essence",
    defaultFuel = FuelType.E10,
    protocol = "OBD2 en CAN 29 bits · protocole détecté automatiquement",
    pids = emptyList(),
    ecus = listOf(
        KnownEcu(0x18DA15F1, 0x18DAF115, wide = true, name = "Moteur"),
        KnownEcu(0x18DA2DF1, 0x18DAF12D, wide = true, name = "ABS / ESP"),
        KnownEcu(0x18DAD2F1, 0x18DAF1D2, wide = true),
        KnownEcu(0x714, 0x734), KnownEcu(0x723, 0x735), KnownEcu(0x742, 0x762), KnownEcu(0x743, 0x763),
        KnownEcu(0x744, 0x764), KnownEcu(0x745, 0x765), KnownEcu(0x747, 0x767), KnownEcu(0x74D, 0x76D),
        KnownEcu(0x74E, 0x76E), KnownEcu(0x752, 0x772), KnownEcu(0x7CA, 0x7DA), KnownEcu(0x7DC, 0x7DD),
    ),
)

/** Modèles avec un profil dédié (données constructeur, protocole connu, calculateurs relevés…). */
val DEDICATED = listOf(SWIFT_SPORT_ZC33S, CLIO_V_TCE90)

/** Tous les modèles : profils dédiés et motorisations du catalogue (données OBD2 standard). */
val TEMPLATES: List<VehicleTemplate> by lazy { DEDICATED + CATALOG_TEMPLATES.filter { t -> DEDICATED.none { it.id == t.id } } }

private val byId by lazy { TEMPLATES.associateBy { it.id } }

/** Retrouve une donnée moteur (standard ou propre à un modèle) par son identifiant. */
fun pidById(id: String): PidDef? = DEDICATED.flatMap { it.pids }.find { it.id == id } ?: StdPids.byId(id)

fun templateById(id: String): VehicleTemplate = byId[id] ?: SWIFT_SPORT_ZC33S
