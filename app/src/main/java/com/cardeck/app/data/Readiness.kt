package com.cardeck.app.data

/** Un test antipollution embarqué (moniteur OBD) et son état. */
data class Monitor(val label: String, val ready: Boolean, val continuous: Boolean)

/** Conclusion pour le contrôle technique. */
enum class ReadinessVerdict { Ready, NotReady, MilOn }

/**
 * État des tests antipollution embarqués (service 01, PID 01) : voyant moteur, nombre de codes et, pour chaque
 * moniteur pris en charge par la voiture, s'il a terminé son test depuis le dernier effacement des défauts.
 *
 * Après un effacement des codes ou un débranchement de la batterie, tous les moniteurs repassent « non prêts » :
 * il faut rouler plusieurs jours, en conditions variées, pour qu'ils terminent.
 */
data class Readiness(
    val mil: Boolean,
    val dtcCount: Int,
    val diesel: Boolean,
    val monitors: List<Monitor>,
    /** Distance parcourue depuis l'effacement des défauts (PID 31), en km, si la voiture la donne. */
    val kmSinceClear: Int? = null,
    /** Montées en température depuis l'effacement des défauts (PID 30), si la voiture les donne. */
    val warmupsSinceClear: Int? = null,
) {
    val notReady: List<Monitor> get() = monitors.filter { !it.ready }

    val verdict: ReadinessVerdict
        get() = when {
            mil -> ReadinessVerdict.MilOn
            notReady.isNotEmpty() -> ReadinessVerdict.NotReady
            else -> ReadinessVerdict.Ready
        }

    companion object {
        private val CONTINUOUS = listOf("Ratés d'allumage", "Système d'alimentation", "Composants du moteur")
        private val SPARK = listOf(
            "Catalyseur", "Catalyseur chauffé", "Circuit des vapeurs d'essence", "Injection d'air secondaire",
            "Fluide de climatisation", "Sondes lambda", "Chauffage des sondes lambda", "Recirculation des gaz (EGR / VVT)",
        )
        private val COMPRESSION = listOf(
            "Catalyseur NMHC", "Traitement des NOx (SCR / piège)", null, "Pression de suralimentation",
            null, "Capteurs des gaz d'échappement", "Filtre à particules", "Recirculation des gaz (EGR / VVT)",
        )

        /**
         * Combine les réponses au PID 01 de tous les calculateurs (4 octets chacune : A B C D).
         * Un moniteur est pris en charge si un calculateur le déclare, et prêt si aucun calculateur qui le prend en charge
         * ne le signale incomplet. Le voyant est allumé si un calculateur le dit. Null si aucune réponse exploitable.
         */
        fun from(payloads: List<IntArray>, kmSinceClear: Int? = null, warmupsSinceClear: Int? = null): Readiness? {
            val valid = payloads.filter { it.size >= 4 }
            if (valid.isEmpty()) return null
            val mil = valid.any { it[0] and 0x80 != 0 }
            val dtcCount = valid.sumOf { it[0] and 0x7F }
            // Bit 3 de l'octet B : moteur à allumage par compression (diesel). Seuls les calculateurs qui déclarent des moniteurs
            // non continus (le moteur, pas la boîte) décident du type ; à défaut, tous les calculateurs.
            val engines = valid.filter { it[2] != 0 }.ifEmpty { valid }
            val diesel = engines.any { it[1] and 0x08 != 0 }
            val monitors = mutableListOf<Monitor>()
            CONTINUOUS.forEachIndexed { bit, label ->
                val supported = valid.filter { it[1] and (1 shl bit) != 0 }
                if (supported.isNotEmpty()) monitors += Monitor(label, supported.none { it[1] and (0x10 shl bit) != 0 }, continuous = true)
            }
            (if (diesel) COMPRESSION else SPARK).forEachIndexed { bit, label ->
                if (label == null) return@forEachIndexed
                val supported = valid.filter { it[2] and (1 shl bit) != 0 }
                if (supported.isNotEmpty()) monitors += Monitor(label, supported.none { it[3] and (1 shl bit) != 0 }, continuous = false)
            }
            return Readiness(mil, dtcCount, diesel, monitors, kmSinceClear, warmupsSinceClear)
        }
    }
}
