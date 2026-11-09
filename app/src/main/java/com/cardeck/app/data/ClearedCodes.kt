package com.cardeck.app.data

/** Codes qui restent dans la liste de l'appli après un effacement. */
object ClearedCodes {
    /** Nom d'un calculateur lu en OBD classique (diffusion) : moteur, boîte, 7E0 à 7E7, réponses 29 bits 18DAF1xx. */
    fun looksObd(ecu: String) = ecu == "Moteur" || ecu == "Boîte de vitesses" || Regex("Calculateur (7E[0-7]|18DAF1..)").matches(ecu)

    /**
     * [obdNames] : calculateurs lus en OBD classique au dernier scan (vide après un redémarrage de l'appli : on les devine).
     * [obdOk] : l'effacement OBD (service 04) a été accepté. [done] : calculateurs qui ont confirmé leur effacement.
     * Les codes « permanents » OBD restent : ils ne s'effacent pas par le service 04 mais seuls, après quelques trajets sans défaut.
     */
    fun remaining(codes: List<FoundCode>, obdNames: Set<String>, obdOk: Boolean, done: Set<String>): List<FoundCode> {
        val names = obdNames.ifEmpty { codes.map { it.ecu }.filter(::looksObd).toSet() }
        return codes.filter { c ->
            val byObd = obdOk && c.ecu in names && c.status != DtcStatus.Permanent
            !byObd && c.ecu !in done
        }
    }
}
