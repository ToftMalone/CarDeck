package com.cardeck.app.data

/** Une valeur possible d'un réglage de codage (déjà positionnée dans le masque). */
data class CodingOption(val label: String, val value: Int)

/**
 * Réglage codable dans un calculateur, par UDS : lecture ReadDataByIdentifier (0x22),
 * écriture WriteDataByIdentifier (0x2E) en session étendue (0x10 03).
 *
 * @param txHeader en-tête CAN de la requête (ex. « 750 »), [rxHeader] en-tête de la réponse (ex. « 758 »).
 * @param did identifiant de donnée (ex. 0xB001).
 * @param byteIndex octet concerné dans les données du DID, [mask] bits concernés.
 * @param securityLevel niveau SecurityAccess (0x27) requis pour écrire, null si aucun.
 */
data class CodingDef(
    val id: String,
    val category: String,
    val label: String,
    val description: String,
    val module: String,
    val txHeader: String,
    val rxHeader: String?,
    val did: Int,
    val byteIndex: Int,
    val mask: Int,
    val options: List<CodingOption>,
    val securityLevel: Int? = null,
) {
    /** Clé du bloc de données (calculateur + DID) auquel appartient ce réglage. */
    val blockKey: String get() = blockKey(txHeader, did)

    /** Valeur actuelle du réglage dans les données du bloc, ou null si l'octet n'existe pas. */
    fun valueIn(data: IntArray): Int? = data.getOrNull(byteIndex)?.let { it and mask }

    /** Données du bloc après application de [value]. */
    fun applyTo(data: IntArray, value: Int): IntArray = data.copyOf().also { it[byteIndex] = (it[byteIndex] and mask.inv() and 0xFF) or (value and mask) }
}

fun blockKey(txHeader: String, did: Int) = "${txHeader.uppercase()}:%04X".format(did)

/** Sauvegarde du codage d'origine d'un bloc, prise avant la première écriture. */
data class CodingBackup(val vehicleId: Long, val txHeader: String, val rxHeader: String?, val did: Int, val module: String, val data: IntArray, val t: Long) {
    val key get() = blockKey(txHeader, did)
}

fun IntArray.toHex(): String = joinToString("") { "%02X".format(it) }

fun hexToInts(s: String): IntArray = s.chunked(2).map { it.toInt(16) }.toIntArray()

/** Libellé des codes de réponse négative UDS (ISO 14229). */
fun nrcLabel(nrc: Int): String = when (nrc) {
    0x10 -> "refus général"
    0x11 -> "service non supporté par le module"
    0x12 -> "sous-fonction non supportée"
    0x13 -> "longueur de message incorrecte"
    0x21 -> "module occupé, réessayez"
    0x22 -> "conditions non remplies (contact mis, moteur arrêté ?)"
    0x24 -> "séquence de requêtes incorrecte"
    0x31 -> "identifiant ou valeur hors plage"
    0x33 -> "accès sécurisé refusé"
    0x35 -> "clé de sécurité invalide"
    0x36 -> "trop de tentatives de sécurité"
    0x37 -> "délai de sécurité non écoulé"
    0x72 -> "échec de l'écriture en mémoire"
    0x7E -> "sous-fonction non supportée dans cette session"
    0x7F -> "service non supporté dans cette session"
    else -> "refus du module (code 0x%02X)".format(nrc)
}
