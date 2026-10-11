package com.cardeck.app.data

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
    val fullName get() = "$brand $model $code"
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

val TEMPLATES = listOf(SWIFT_SPORT_ZC33S, KIA_PICANTO_TA)

/** Retrouve une donnée moteur (standard ou propre à un modèle) par son identifiant. */
fun pidById(id: String): PidDef? = TEMPLATES.flatMap { it.pids }.find { it.id == id } ?: StdPids.byId(id)

fun templateById(id: String): VehicleTemplate = TEMPLATES.find { it.id == id } ?: SWIFT_SPORT_ZC33S
