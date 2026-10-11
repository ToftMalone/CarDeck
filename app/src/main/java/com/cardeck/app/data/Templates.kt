package com.cardeck.app.data

import androidx.compose.material.icons.Icons
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
    val decode: (IntArray) -> Double,
)

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
) {
    val fullName get() = "$brand $model $code".trim()
    val specs get() = "$engine · $power · $fuel"
}

val SWIFT_SPORT_ZC33S = VehicleTemplate(
    id = "suzuki-swift-sport-zc33s",
    brand = "Suzuki",
    model = "Swift Sport",
    code = "ZC33S",
    engine = "K14C 1.4 BoosterJet",
    power = "140 ch",
    fuel = "Essence",
    protocol = "ISO 15765-4 CAN 11 bits 500 kb/s",
    // Liste des PIDs Swift Sport à intégrer (fournie ultérieurement).
    pids = emptyList(),
    // Fonctions de codage Swift Sport à intégrer (données à fournir).
    codings = emptyList(),
)

val KIA_PICANTO_TA = VehicleTemplate(
    id = "kia-picanto-ta-g3la",
    brand = "Kia",
    model = "Picanto 1.0",
    code = "TA",
    engine = "G3LA 1.0 Kappa MPI",
    power = "69 ch",
    fuel = "Essence",
    protocol = "ISO 15765-4 CAN 11 bits 500 kb/s",
    // Liste des PIDs Picanto à intégrer (à fournir ultérieurement).
    pids = emptyList(),
    // Fonctions de codage Picanto à intégrer (données à fournir).
    codings = emptyList(),
)

/** Voiture virtuelle : boîtier ELM327 simulé, pour essayer toute l'appli sans véhicule. */
val SIMULATOR = VehicleTemplate(
    id = "cardeck-simulateur",
    brand = "CarDeck",
    model = "Simulateur",
    code = "",
    engine = "Moteur virtuel 1.4 turbo",
    power = "140 ch",
    fuel = "Essence",
    protocol = "ISO 15765-4 CAN 11 bits 500 kb/s (simulé)",
    pids = listOf(
        PidDef(
            id = "sim_boost", label = "Pression de suralimentation", unit = "bar", icon = Icons.Rounded.Speed,
            request = "221940", header = "7E0", decimals = 2, min = 0.0, max = 2.2, size = 2, category = "Moteur",
        ) { (it[0] * 256 + it[1]) / 1000.0 },
    ),
    codings = listOf(
        CodingDef(
            "sim_windows", "Confort", "Ouverture des vitres par appui long", "Maintenir « déverrouiller » sur la clé ouvre toutes les vitres.",
            "Boîtier de servitude (BCM)", "750", "758", 0x0101, 0, 0x01,
            listOf(CodingOption("Désactivé", 0x00), CodingOption("Activé", 0x01)),
        ),
        CodingDef(
            "sim_blink", "Éclairage", "Clignotants impulsionnels", "Nombre de clignotements après une impulsion sur la commande.",
            "Boîtier de servitude (BCM)", "750", "758", 0x0101, 1, 0x07,
            listOf(CodingOption("1", 0x01), CodingOption("3", 0x03), CodingOption("5", 0x05), CodingOption("7", 0x07)),
        ),
        CodingDef(
            "sim_beep", "Confort", "Bip de verrouillage", "Signal sonore lors du verrouillage à distance.",
            "Boîtier de servitude (BCM)", "750", "758", 0x0101, 0, 0x02,
            listOf(CodingOption("Désactivé", 0x00), CodingOption("Activé", 0x02)),
        ),
    ),
)

val TEMPLATES = listOf(SWIFT_SPORT_ZC33S, KIA_PICANTO_TA, SIMULATOR)

/** Retrouve une donnée moteur (standard ou propre à un modèle) par son identifiant. */
fun pidById(id: String): PidDef? = TEMPLATES.flatMap { it.pids }.find { it.id == id } ?: StdPids.byId(id)

fun templateById(id: String): VehicleTemplate = TEMPLATES.find { it.id == id } ?: SWIFT_SPORT_ZC33S
