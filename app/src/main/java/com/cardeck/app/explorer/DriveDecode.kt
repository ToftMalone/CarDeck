package com.cardeck.app.explorer

import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Décodage par corrélation sur tout un enregistrement en roulant (route/<date>/), méthode reprise de CANExplorer
 * (github.com/AKJ7/CANExplorer, licence MIT) et adaptée aux relevés d'un boîtier ELM327 :
 *  - chaque champ candidat (octet, ou deux octets dans les deux ordres, signé ou non) des blocs constructeur (diag.csv)
 *    et des trames du bus (bus.csv) est comparé, à chacune de ses mesures, à la valeur des grandeurs OBD (obd.csv)
 *    au même instant (interpolée entre deux lectures proches) ;
 *  - les compteurs (octet qui avance d'un pas fixe à chaque trame) sont écartés avant ;
 *  - pour les meilleurs, le décalage dans le temps (± 1,5 s) qui donne la meilleure correspondance est cherché ;
 *  - une droite (moindres carrés) donne la formule, avec un facteur « propre » (× 4, ÷ 256, × 100 / 255…) quand il colle ;
 *  - une piste qui suit aussi le temps écoulé (comme une température qui ne fait que monter) est signalée : ambiguë.
 * Les octets sont comptés à partir de 0, après l'écho de la requête pour les blocs. Ce sont des hypothèses.
 */
object DriveDecode {
    /** Grandeur OBD de référence : instants (s) triés et valeurs. */
    class Series(val t: DoubleArray, val v: DoubleArray)

    /** Données à décoder : bloc constructeur (« 7E0:2100 », écho retiré) ou identifiant du bus (« bus 3B9 »), dans l'ordre du temps. */
    class Source(val key: String, val t: DoubleArray, val data: List<IntArray>)

    class Inputs(val refs: Map<String, Series>, val sources: List<Source>)

    /** Champ candidat : octet de départ, 8 ou 16 bits, ordre des octets (poids fort d'abord ou non), signé ou non. */
    data class Field(val start: Int, val bits: Int, val bigEndian: Boolean = true, val signed: Boolean = false) {
        val bytes: List<Int> get() = if (bits == 8) listOf(start) else listOf(start, start + 1)

        fun raw(d: IntArray): Double? {
            if (start + bits / 8 > d.size) return null
            val u = if (bits == 8) d[start] else if (bigEndian) (d[start] shl 8) or d[start + 1] else (d[start + 1] shl 8) or d[start]
            val v = if (signed && u >= 1 shl (bits - 1)) u - (1 shl bits) else u
            return v.toDouble()
        }

        fun describe(): String = buildString {
            if (bits == 8) append("octet $start") else append("octets $start-${start + 1} (16 bits${if (bigEndian) "" else ", poids faible d'abord"})")
            if (signed) append(", signé")
        }
    }

    /** Une piste : [field] de [source] ≈ [a] × x + [b] donne [ref] (corrélation [r] sur [n] mesures, décalage [lag] s). */
    data class Clue(
        val source: String, val field: Field, val ref: String, val r: Double, val a: Double, val b: Double, val n: Int,
        val lag: Double, val time: Boolean, val formula: String, val also: String? = null,
    )

    /** Seuil de corrélation d'une piste retenue. */
    const val THRESHOLD = 0.95
    private const val PRE = 0.90
    private const val MIN_N = 30
    private const val MAX_OBS = 4000
    /** Mesures gardées au plus par source à la lecture (trajet de plusieurs heures) : au-delà, une sur deux est retirée. */
    private const val MAX_LOADED = 40_000
    /** Écart maximal entre deux lectures OBD pour interpoler entre elles (s) ; au-delà, seulement une lecture à moins de 0,5 s. */
    private const val MAX_GAP = 3.0
    private const val NEAR = 0.5
    private val LAGS = (-15..15).map { it / 10.0 }

    // ------------------------------------------------------------------ Lecture des fichiers

    /** Lit un enregistrement en roulant ; fichiers absents ou lignes abîmées ignorés. */
    fun load(folder: File): Inputs {
        val refs = HashMap<String, Pair<MutableList<Double>, MutableList<Double>>>()
        File(folder, "obd.csv").takeIf { it.exists() }?.useLines { lines ->
            var names: List<String> = emptyList()
            for ((i, line) in lines.withIndex()) {
                val c = line.split(',')
                if (i == 0) { names = c; continue }
                val t = c.getOrNull(0)?.toDoubleOrNull() ?: continue
                for (j in 2 until minOf(c.size, names.size)) {
                    val v = c[j].toDoubleOrNull() ?: continue
                    if (!v.isFinite()) continue
                    val (ts, vs) = refs.getOrPut(names[j]) { mutableListOf<Double>() to mutableListOf() }
                    ts += t; vs += v
                }
            }
        }
        val series = refs.mapValues { (_, p) ->
            val order = p.first.indices.sortedBy { p.first[it] }
            Series(DoubleArray(order.size) { p.first[order[it]] }, DoubleArray(order.size) { p.second[order[it]] })
        }

        val raw = LinkedHashMap<String, MutableList<Pair<Double, IntArray>>>()
        /** Ajoute une mesure ; une source trop fournie est éclaircie (une mesure sur deux), ce qui garde l'ordre et les compteurs reconnaissables. */
        fun add(key: String, t: Double, d: IntArray) {
            val list = raw.getOrPut(key) { mutableListOf() }
            list += t to d
            if (list.size > MAX_LOADED) {
                val kept = list.filterIndexed { i, _ -> i % 2 == 0 }
                list.clear(); list += kept
            }
        }
        File(folder, "diag.csv").takeIf { it.exists() }?.useLines { lines ->
            for ((i, line) in lines.withIndex()) {
                if (i == 0) continue
                val c = line.split(',')
                if (c.size < 4) continue
                val t = c[0].toDoubleOrNull() ?: continue
                val req = c[2]
                val bytes = runCatching { Explore.bytes(c[3]) }.getOrNull() ?: continue
                val body = bytes.drop(Explore.echoSize(req)).toIntArray()
                if (body.isEmpty()) continue
                add("${c[1]}:$req", t, body)
            }
        }
        // Bus : trames d'une même fenêtre d'écoute réparties régulièrement dans la fenêtre (l'heure exacte n'est pas relevée).
        File(folder, "bus.csv").takeIf { it.exists() }?.useLines { lines ->
            var window: Triple<Double, Double, String>? = null
            val pending = mutableListOf<IntArray>()
            fun flush() {
                val w = window ?: return
                pending.forEachIndexed { k, d -> add("bus ${w.third}", w.first + (k + 0.5) / pending.size * w.second, d) }
                pending.clear()
            }
            for ((i, line) in lines.withIndex()) {
                if (i == 0) continue
                val c = line.split(',')
                if (c.size < 4) continue
                val t = c[0].toDoubleOrNull() ?: continue
                val len = c[1].toDoubleOrNull() ?: continue
                val d = runCatching { Explore.bytes(c[3]) }.getOrNull() ?: continue
                val w = Triple(t, len, c[2])
                if (w != window) { flush(); window = w }
                pending += d
            }
            flush()
        }
        val sources = raw.map { (k, list) ->
            list.sortBy { it.first }
            Source(k, DoubleArray(list.size) { list[it].first }, list.map { it.second })
        }
        return Inputs(series, sources)
    }

    // ------------------------------------------------------------------ Analyse

    /** Valeur de [s] à l'instant [x] : interpolée entre deux lectures proches, sinon la lecture à moins de 0,5 s, sinon NaN. */
    internal fun at(s: Series, x: Double): Double {
        val t = s.t
        if (t.isEmpty() || x < t[0] - NEAR || x > t[t.size - 1] + NEAR) return Double.NaN
        var lo = 0; var hi = t.size // premier indice > x
        while (lo < hi) { val m = (lo + hi) ushr 1; if (t[m] <= x) lo = m + 1 else hi = m }
        val i1 = lo; val i0 = lo - 1
        if (i0 < 0) return if (t[0] - x <= NEAR) s.v[0] else Double.NaN
        if (i1 >= t.size) return if (x - t[i0] <= NEAR) s.v[i0] else Double.NaN
        val gap = t[i1] - t[i0]
        if (gap <= 0.0) return s.v[i0]
        if (gap <= MAX_GAP) return s.v[i0] + (s.v[i1] - s.v[i0]) * (x - t[i0]) / gap
        return when {
            x - t[i0] <= NEAR -> s.v[i0]
            t[i1] - x <= NEAR -> s.v[i1]
            else -> Double.NaN
        }
    }

    /** Octets qui sont des compteurs : à au moins 60 % des trames, ils avancent d'un même pas (octet entier ou un de ses quartets). */
    internal fun counters(data: List<IntArray>): Set<Int> {
        val len = data.maxOfOrNull { it.size } ?: return emptySet()
        val out = HashSet<Int>()
        for (i in 0 until len) {
            val full = IntArray(256); val low = IntArray(16)
            var pairs = 0
            for (k in 1 until data.size) {
                val a = data[k - 1]; val b = data[k]
                if (i >= a.size || i >= b.size) continue
                val d = (b[i] - a[i]) and 0xFF
                pairs++
                full[d]++
                low[d and 0x0F]++
            }
            if (pairs < 10) continue
            fun steady(h: IntArray) = (1 until h.size).any { h[it] >= 0.6 * pairs }
            // Compteur sur le quartet haut : pas de 0x10, 0x20… avec le quartet bas inchangé.
            if (steady(full) || steady(low) || (1 until 16).any { full[it shl 4] >= 0.6 * pairs }) out += i
        }
        return out
    }

    /** Deux octets qui forment une seule valeur : quand l'octet fort change d'un cran, l'octet faible repart de l'autre bout. */
    internal fun carries(data: List<IntArray>, f: Field): Boolean {
        if (f.bits != 16) return false
        val hiI = if (f.bigEndian) f.start else f.start + 1
        val loI = if (f.bigEndian) f.start + 1 else f.start
        var events = 0; var carry = 0
        for (k in 1 until data.size) {
            val a = data[k - 1]; val b = data[k]
            if (maxOf(hiI, loI) >= minOf(a.size, b.size)) continue
            val dh = b[hiI] - a[hiI]
            val dl = b[loI] - a[loI]
            if (dh == 1 || dh == -1) {
                events++
                if ((dh == 1 && dl < -100) || (dh == -1 && dl > 100)) carry++
            }
        }
        return events >= 3 && carry >= 0.6 * events
    }

    /**
     * Deux octets qui forment une seule valeur, vu autrement : une fois la grandeur expliquée par l'octet fort seul, ce qui
     * reste suit l'octet faible. Un octet faible sans rapport (bruit, constante, autre donnée) n'explique rien du reste.
     */
    internal fun lowByteHelps(data: List<IntArray>, f: Field, ys: DoubleArray): Boolean {
        if (f.bits != 16) return false
        val hiI = if (f.bigEndian) f.start else f.start + 1
        val loI = if (f.bigEndian) f.start + 1 else f.start
        val hi = DoubleArray(data.size) { val d = data[it]; if (maxOf(hiI, loI) < d.size) d[hiI].toDouble() else Double.NaN }
        val lo = DoubleArray(data.size) { val d = data[it]; if (maxOf(hiI, loI) < d.size) d[loI].toDouble() else Double.NaN }
        val st = fit(hi, ys) ?: return false
        val rest = DoubleArray(data.size) { if (hi[it].isNaN() || ys[it].isNaN()) Double.NaN else ys[it] - (st.a * hi[it] + st.b) }
        val r = fit(lo, rest) ?: return false
        return r.r * (if (st.a < 0) -1 else 1) >= maxOf(0.3, 4 / sqrt(r.n.toDouble()))
    }

    private class Stat(val r: Double, val a: Double, val b: Double, val n: Int)

    /** Corrélation et droite de régression y ≈ a·x + b sur les paires valides. */
    private fun fit(xs: DoubleArray, ys: DoubleArray): Stat? {
        var n = 0; var sx = 0.0; var sy = 0.0
        for (i in xs.indices) if (!xs[i].isNaN() && !ys[i].isNaN()) { n++; sx += xs[i]; sy += ys[i] }
        if (n < MIN_N) return null
        val mx = sx / n; val my = sy / n
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in xs.indices) if (!xs[i].isNaN() && !ys[i].isNaN()) {
            val dx = xs[i] - mx; val dy = ys[i] - my
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        if (sxx <= 0.0 || syy <= 0.0) return null
        val a = sxy / sxx
        return Stat(sxy / sqrt(sxx * syy), a, my - a * mx, n)
    }

    /** Assez de valeurs différentes pour qu'une corrélation veuille dire quelque chose. */
    private fun varied(xs: DoubleArray, min: Int = 5): Boolean {
        val seen = HashSet<Double>()
        for (x in xs) if (!x.isNaN() && seen.add(x) && seen.size >= min) return true
        return false
    }

    private fun thin(s: Source): Source {
        if (s.t.size <= MAX_OBS) return s
        val idx = (0 until MAX_OBS).map { (it.toLong() * s.t.size / MAX_OBS).toInt() }
        return Source(s.key, DoubleArray(idx.size) { s.t[idx[it]] }, idx.map { s.data[it] })
    }

    private fun fields(len: Int): List<Field> = buildList {
        for (i in 0 until len) {
            add(Field(i, 8)); add(Field(i, 8, signed = true))
            if (i + 1 < len) for (big in listOf(true, false)) { add(Field(i, 16, big)); add(Field(i, 16, big, signed = true)) }
        }
    }

    /** Pistes de décodage de tout l'enregistrement, les plus sûres d'abord. */
    fun analyze(inputs: Inputs): List<Clue> {
        val refs = inputs.refs.filter { (_, s) -> s.t.size >= MIN_N && varied(s.v) }
        if (refs.isEmpty()) return emptyList()
        val clues = mutableListOf<Clue>()
        for (full in inputs.sources) {
            if (full.t.size < MIN_N) continue
            val skip = counters(full.data)
            val s = thin(full)
            val len = s.data.maxOf { it.size }
            val refAt = refs.mapValues { (_, r) -> DoubleArray(s.t.size) { at(r, s.t[it]) } }.filterValues { varied(it) }
            if (refAt.isEmpty()) continue
            val time = s.t

            class Cand(val f: Field, val ref: String, val st: Stat, val lag: Double, val ys: DoubleArray, val also: String?, val score: Double)
            val cands = mutableListOf<Cand>()
            for (f in fields(len)) {
                if (f.bytes.any { it in skip }) continue
                val xs = DoubleArray(s.data.size) { f.raw(s.data[it]) ?: Double.NaN }
                if (!varied(xs)) continue
                // Meilleure grandeur sans décalage, puis décalage affiné pour celle-ci.
                val ranked = refAt.mapNotNull { (name, ys) -> fit(xs, ys)?.let { name to it } }.sortedByDescending { abs(it.second.r) }
                val (name, first) = ranked.firstOrNull() ?: continue
                if (abs(first.r) < PRE) continue
                var best = first; var bestLag = 0.0; var bestYs = refAt.getValue(name)
                val ref = refs.getValue(name)
                for (lag in LAGS) {
                    if (lag == 0.0) continue
                    val ys = DoubleArray(time.size) { at(ref, time[it] - lag) }
                    val st = fit(xs, ys) ?: continue
                    if (abs(st.r) > abs(best.r) + 1e-4) { best = st; bestLag = lag; bestYs = ys }
                }
                if (abs(best.r) < THRESHOLD) continue
                val second = ranked.getOrNull(1)?.takeIf { abs(it.second.r) >= PRE }?.let { "${it.first} (r = ${"%+.2f".format(java.util.Locale.US, it.second.r)})" }
                // 16 bits : retenus devant un octet seul s'ils forment vraiment une valeur (retenue d'un octet sur l'autre, ou
                // octet faible qui explique ce que l'octet fort laisse), sinon seulement s'ils collent nettement mieux ;
                // à égalité, l'octet seul (un octet constant ou sans rapport à côté ne change presque rien).
                val bonus = when {
                    f.bits == 8 -> 0.0
                    carries(full.data, f) || lowByteHelps(s.data, f, bestYs) -> 0.01
                    else -> -0.005
                } + if (f.signed) -0.001 else 0.0
                cands += Cand(f, name, best, bestLag, bestYs, second, abs(best.r) + bonus)
            }
            // Par grandeur : les meilleurs champs, sans octets en commun, 3 au plus.
            for ((_, list) in cands.groupBy { it.ref }) {
                val used = HashSet<Int>()
                var kept = 0
                for (c in list.sortedByDescending { it.score }) {
                    if (kept >= 3) break
                    if (c.f.bytes.any { it in used }) continue
                    used += c.f.bytes
                    kept++
                    val xs = DoubleArray(s.data.size) { c.f.raw(s.data[it]) ?: Double.NaN }
                    val ys = c.ys
                    val rt = fit(xs, time)?.r ?: 0.0
                    val yt = fit(ys, time)?.r ?: 0.0
                    val range = ys.filter { !it.isNaN() }.let { v -> (v.maxOrNull() ?: 0.0) - (v.minOrNull() ?: 0.0) }
                    val (a, b, text) = formula(c.st, xs, ys, range)
                    clues += Clue(full.key, c.f, c.ref, c.st.r, a, b, c.st.n, c.lag, abs(rt) >= 0.9 && abs(yt) >= 0.9, "${c.ref} ≈ $text", c.also)
                }
            }
        }
        return clues.sortedWith(compareBy<Clue> { it.time }.thenByDescending { abs(it.r) }.thenBy { it.source })
    }

    // ------------------------------------------------------------------ Formule

    private fun g4(x: Double): String = BigDecimal(x).round(MathContext(4)).stripTrailingZeros().toPlainString()

    /** Facteur « propre » à moins de 1 % : entier, 1 / n, ou 100 / 255 (pourcentage sur un octet) ; texte et valeur. */
    internal fun niceFactor(a: Double): Pair<Double, String>? {
        if (a == 0.0 || !a.isFinite()) return null
        fun ok(v: Double) = abs(v - a) <= 0.01 * abs(a)
        val sign = if (a < 0) "−" else ""
        val k = a.roundToInt()
        if (k != 0 && ok(k.toDouble())) return k.toDouble() to when (k) { 1 -> "x"; -1 -> "−x"; else -> "$k × x" }
        val n = (1 / abs(a)).roundToInt()
        if (n in 2..100_000 && ok((if (a < 0) -1.0 else 1.0) / n)) return (if (a < 0) -1.0 else 1.0) / n to "${sign}x / $n"
        if (ok(100.0 / 255)) return 100.0 / 255 to "x × 100 / 255"
        if (ok(-100.0 / 255)) return -100.0 / 255 to "−x × 100 / 255"
        return null
    }

    /** Formule lisible ; avec un facteur propre, le décalage est recalculé pour lui et arrondi à la précision de la grandeur. */
    private fun formula(st: Stat, xs: DoubleArray, ys: DoubleArray, range: Double): Triple<Double, Double, String> {
        val nice = niceFactor(st.a)
        val a = nice?.first ?: st.a
        var b = st.b
        if (nice != null) {
            var sx = 0.0; var sy = 0.0; var n = 0
            for (i in xs.indices) if (!xs[i].isNaN() && !ys[i].isNaN()) { sx += xs[i]; sy += ys[i]; n++ }
            if (n > 0) b = sy / n - a * sx / n
        }
        val step = if (range > 0) 10.0.pow(floor(log10(range / 50))) else 1.0
        val rb = (b / step).roundToInt() * step
        val base = nice?.second ?: "${g4(a)} × x"
        val text = if (abs(rb) < step / 2) base else "$base ${if (rb > 0) "+" else "−"} ${g4(abs(rb))}"
        return Triple(a, rb, text)
    }

    // ------------------------------------------------------------------ Rapport

    /** Tableau Markdown des pistes (pour PISTES.md et le rapport). */
    fun table(clues: List<Clue>, drives: Map<Clue, Int> = emptyMap()): String = buildString {
        append("| Source | Champ | Formule | r | Mesures | Remarque |\n|---|---|---|---|---|---|\n")
        for (c in clues) {
            val notes = listOfNotNull(
                if (c.time) "⚠️ suit aussi le temps écoulé" else null,
                if (abs(c.lag) >= 0.2) "décalé de ${"%+.1f".format(java.util.Locale.US, c.lag)} s" else null,
                c.also?.let { "aussi : $it" },
                drives[c]?.takeIf { it > 1 }?.let { "vu sur $it trajets" },
            )
            append("| `${c.source}` | ${c.field.describe()} | **${c.formula}** | ${"%+.3f".format(java.util.Locale.US, c.r)} | ${c.n} | ${notes.joinToString(" · ")} |\n")
        }
    }

    fun markdown(clues: List<Clue>, folder: String): String = buildString {
        append("# Pistes de décodage — route/$folder\n\n")
        append("Chaque octet (ou paire d'octets, dans les deux ordres, signés ou non) des blocs constructeur et des trames du bus est comparé ")
        append("aux mesures OBD prises au même moment, sur tout le trajet. Les compteurs sont écartés. Octets comptés à partir de 0, ")
        append("après l'écho de la requête pour les blocs ; x = valeur brute du champ.\n\n")
        append("**Ce sont des hypothèses** (corrélation d'au moins ${THRESHOLD}) : à confirmer, surtout celles qui suivent aussi le temps écoulé.\n\n")
        if (clues.isEmpty()) append("Aucune piste sûre sur ce trajet : il faut plus de mesures qui bougent (accélérations, arrêts, montée en température).\n")
        else append(table(clues))
    }
}
