package com.cardeck.app.explorer

import java.util.Locale

/**
 * Recherche du cliquetis : quels octets des blocs de données du moteur se comportent comme un retard d'allumage
 * dû au cliquetis ? Ce n'est qu'un classement de candidats, à confirmer (plusieurs enregistrements, SP95 contre SP98…).
 *
 * Un tel octet :
 *  - reste à sa valeur de repos (souvent 0) hors charge : pas de cliquetis en roulant tranquille ;
 *  - s'en écarte surtout pendant les reprises en charge, là où le moteur cliquette ;
 *  - quand il s'en écarte, l'avance à l'allumage (PID 0E) est plus faible qu'aux mêmes moments sans écart ;
 *  - ne prend que peu de valeurs différentes (ce n'est pas une mesure continue comme un régime ou une pression).
 */
object KnockAnalysis {
    /** Mesure OBD de référence, [t] en secondes. Valeurs null si la voiture ne les a pas données. */
    class Obd(val t: Double, val rpm: Double?, val load: Double?, val advance: Double?, val map: Double?)

    /** Lecture d'un bloc : [key] « calculateur:requête », [data] sans l'écho de la requête. */
    class Block(val t: Double, val key: String, val data: IntArray)

    class Candidate(
        val key: String,
        /** Position dans les données du bloc, l'écho de la requête exclu (0 = premier octet de données). */
        val index: Int,
        val rest: Int,
        val distinct: Int,
        val samples: Int,
        /** Part des lectures en charge où l'octet n'est pas au repos. */
        val loaded: Double,
        /** Part des lectures hors charge où l'octet n'est pas au repos. */
        val light: Double,
        /** Avance moyenne perdue (°) quand l'octet s'écarte du repos, à régime et charge égaux ; null si on ne peut pas la mesurer. */
        val advanceDrop: Double?,
        val maxSeen: Int,
        val score: Double,
    )

    class Result(
        val obdSamples: Int,
        val blockSamples: Int,
        val loadedSamples: Int,
        /** Nombre de reprises en charge (passages de « hors charge » à « en charge »). */
        val pulls: Int,
        val candidates: List<Candidate>,
        /** Suites d'octets voisins candidats d'un même bloc (un octet par cylindre ?). */
        val groups: List<String>,
    )

    /** Écart de temps maximal entre une lecture de bloc et la mesure OBD qui lui sert de référence. */
    private const val MAX_GAP_S = 1.0

    /** En charge : charge calculée ≥ 60 % (ou, sans elle, pression d'admission ≥ 120 kPa), entre 1 200 et 6 500 tr/min. */
    fun isLoaded(o: Obd): Boolean {
        val rpm = o.rpm ?: return false
        if (rpm !in 1200.0..6500.0) return false
        return o.load?.let { it >= 60.0 } ?: o.map?.let { it >= 120.0 } ?: false
    }

    /** Case régime × charge (500 tr/min × 10 %) pour comparer l'avance à conditions égales ; null sans régime ou charge. */
    private fun cell(o: Obd): Long? {
        val rpm = o.rpm ?: return null
        val load = o.load ?: o.map ?: return null
        return (rpm / 500).toLong() * 1000 + (load / 10).toLong()
    }

    fun analyze(obd: List<Obd>, blocks: List<Block>): Result {
        val ref = obd.sortedBy { it.t }
        val times = DoubleArray(ref.size) { ref[it].t }
        var pulls = 0
        var was = false
        for (o in ref) { val l = isLoaded(o); if (l && !was) pulls++; was = l }

        // Lecture de bloc → mesure OBD la plus proche dans le temps.
        fun nearest(t: Double): Obd? {
            if (ref.isEmpty()) return null
            var i = java.util.Arrays.binarySearch(times, t)
            if (i < 0) i = -i - 1
            val best = listOfNotNull(ref.getOrNull(i - 1), ref.getOrNull(i)).minByOrNull { kotlin.math.abs(it.t - t) } ?: return null
            return best.takeIf { kotlin.math.abs(it.t - t) <= MAX_GAP_S }
        }

        class Row(val o: Obd, val data: IntArray)
        val byKey = LinkedHashMap<String, MutableList<Row>>()
        var used = 0
        for (b in blocks) {
            val o = nearest(b.t) ?: continue
            byKey.getOrPut(b.key) { mutableListOf() } += Row(o, b.data)
            used++
        }

        val out = mutableListOf<Candidate>()
        for ((key, rows) in byKey) {
            val width = rows.minOf { it.data.size }
            for (i in 0 until width) {
                val values = rows.map { it.data[i] }
                val loaded = rows.indices.filter { isLoaded(rows[it].o) }
                val light = rows.indices.filter { !isLoaded(rows[it].o) && (rows[it].o.rpm ?: 0.0) > 400 }
                if (loaded.size < 5 || light.size < 10) continue
                val distinct = values.toSet().size
                if (distinct < 2 || distinct > 64) continue
                // Repos : valeur la plus fréquente hors charge.
                val rest = light.groupingBy { values[it] }.eachCount().maxByOrNull { it.value }!!.key
                val lightMoves = light.count { values[it] != rest }.toDouble() / light.size
                if (lightMoves > 0.10) continue
                val moved = loaded.filter { values[it] != rest }
                if (moved.size < 2) continue
                val loadedMoves = moved.size.toDouble() / loaded.size
                // Avance perdue : quand l'octet s'écarte, avance comparée à l'avance habituelle (octet au repos) au même régime
                // et à la même charge. Un octet qui ne fait que suivre la charge n'a pas de repos à ces endroits : pas de mesure.
                val usual = HashMap<Long, MutableList<Double>>()
                for (j in rows.indices) if (values[j] == rest) cell(rows[j].o)?.let { c -> rows[j].o.advance?.let { usual.getOrPut(c) { mutableListOf() } += it } }
                val residuals = moved.mapNotNull { j ->
                    val o = rows[j].o
                    val ref = cell(o)?.let { usual[it] }?.takeIf { it.size >= 2 } ?: return@mapNotNull null
                    o.advance?.let { ref.average() - it }
                }
                val drop = if (residuals.size >= 2) residuals.average() else null
                // Sans mesure de l'avance perdue, l'octet peut ne faire que suivre la charge : score divisé par deux.
                val score = (loadedMoves - lightMoves) * (1 + (drop ?: 0.0).coerceIn(0.0, 10.0) / 5) * (if (drop == null) 0.5 else 1.0)
                if (score <= 0.05) continue
                out += Candidate(key, i, rest, distinct, rows.size, loadedMoves, lightMoves, drop, values.max(), score)
            }
        }
        val ranked = out.sortedByDescending { it.score }
        val groups = ranked.groupBy { it.key }.flatMap { (key, list) ->
            val idx = list.map { it.index }.sorted()
            val runs = mutableListOf<IntRange>()
            var s = -1; var p = -2
            for (x in idx) { if (x != p + 1) { if (s >= 0 && p - s >= 2) runs += s..p; s = x }; p = x }
            if (s >= 0 && p - s >= 2) runs += s..p
            runs.map { "$key octets ${it.first} à ${it.last}" }
        }
        return Result(ref.size, used, ref.count { isLoaded(it) }, pulls, ranked.take(25), groups)
    }

    /** Compte rendu lisible (Markdown), joint aux résultats partagés. */
    fun report(r: Result, vehicle: String, version: String, minutes: Double, folder: String): String = buildString {
        fun a(s: String = "") = append(s).append('\n')
        fun pct(x: Double) = "%.0f %%".format(Locale.FRANCE, x * 100)
        a("# Recherche du cliquetis — $vehicle")
        a()
        a("CarDeck $version, en lecture seule. Enregistrement `cliquetis/$folder` : ${"%.1f".format(Locale.FRANCE, minutes)} min, " +
            "${r.obdSamples} mesures OBD, ${r.blockSamples} lectures de blocs, ${r.pulls} reprise(s) en charge, ${r.loadedSamples} mesures en charge.")
        a()
        if (r.pulls < 3) {
            a("⚠️ Moins de 3 reprises en charge : refaites un enregistrement avec plusieurs reprises franches (3e ou 4e, de 1 500 à 4 000 tr/min).")
            a()
        }
        a("En charge = charge calculée ≥ 60 % (ou pression d'admission ≥ 120 kPa) entre 1 200 et 6 500 tr/min. " +
            "Position = rang de l'octet dans les données du bloc, l'écho de la requête exclu (0 = premier octet).")
        a()
        a("## Candidats")
        a()
        if (r.candidates.isEmpty()) {
            a("Aucun octet ne se comporte comme un retard dû au cliquetis dans les blocs lus. Soit le moteur n'a pas cliqueté (bon carburant, " +
                "conditions douces), soit l'information est dans un autre bloc : lancez d'abord un balayage à l'arrêt pour trouver tous les blocs du moteur.")
        } else {
            a("| Bloc | Position | Repos | Valeurs | Écarté en charge | Écarté hors charge | Avance perdue | Max | Score |")
            a("|---|---|---|---|---|---|---|---|---|")
            for (c in r.candidates) {
                a("| `${c.key}` | ${c.index} | ${c.rest} | ${c.distinct} | ${pct(c.loaded)} | ${pct(c.light)} | " +
                    "${c.advanceDrop?.let { "%.1f°".format(Locale.FRANCE, it) } ?: "—"} | ${c.maxSeen} | ${"%.2f".format(Locale.FRANCE, c.score)} |")
            }
        }
        if (r.groups.isNotEmpty()) {
            a()
            a("Suites d'octets voisins (un par cylindre ?) : " + r.groups.joinToString(" · "))
        }
        a()
        a("Un candidat n'est pas une preuve : à confirmer avec d'autres enregistrements. Données brutes : `mesures.csv` et `blocs.csv`.")
    }

    /** Ligne CSV des candidats. */
    fun candidatesCsv(r: Result): String = buildString {
        append("bloc,position,repos,valeurs,ecart_en_charge,ecart_hors_charge,avance_perdue,max,score\n")
        for (c in r.candidates) {
            append(listOf(c.key, c.index, c.rest, c.distinct, "%.3f".format(Locale.US, c.loaded), "%.3f".format(Locale.US, c.light),
                c.advanceDrop?.let { "%.2f".format(Locale.US, it) }.orEmpty(), c.maxSeen, "%.3f".format(Locale.US, c.score)).joinToString(",")).append('\n')
        }
    }
}
