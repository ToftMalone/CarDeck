package com.cardeck.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Langues de l'application. Le français est la langue de référence : les textes du code sont en français. */
enum class Lang(val code: String, val label: String, val locale: Locale) {
    Fr("fr", "Français", Locale.FRANCE),
    En("en", "English", Locale.UK),
    De("de", "Deutsch", Locale.GERMANY),
    Ru("ru", "Русский", Locale("ru", "RU")),
    Es("es", "Español", Locale("es", "ES"));

    companion object {
        fun of(code: String?): Lang? = entries.firstOrNull { it.code == code }

        /** Langue du téléphone si elle est proposée, l'anglais sinon. */
        fun system(): Lang = of(Locale.getDefault().language) ?: En
    }
}

/**
 * Traductions, chargées depuis assets/i18n/strings.tsv : une ligne par texte français, puis sa traduction dans chaque
 * langue (colonnes en, de, ru, es). Une clé peut contenir {} : la partie variable (nombre, nom, autre texte) est reprise
 * telle quelle, elle-même traduite si elle figure dans la table. Un texte absent de la table reste en français.
 */
object I18n {
    /** Langue affichée (lue par l'interface : la changer redessine tout). */
    var lang by mutableStateOf(Lang.Fr)

    val locale: Locale get() = lang.locale

    private class Pattern(val parts: List<String>, val regex: Regex, val values: Array<String?>) {
        /** Commence par du texte fixe (« Étape {}… ») : plus précis qu'un gabarit qui commence par {}. */
        val anchored get() = parts.first().isNotEmpty()
        val fixed get() = parts.sumOf { it.length }
    }

    private val exact = HashMap<String, Array<String?>>()
    private val patterns = ArrayList<Pattern>()
    private val cache = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 4000
    }

    private const val COLUMNS = 4 // en, de, ru, es

    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\t", "\t")

    /** Charge la table (texte du fichier strings.tsv). */
    fun load(tsv: String) {
        exact.clear(); patterns.clear(); synchronized(cache) { cache.clear() }
        for (raw in tsv.lineSequence()) {
            if (raw.isBlank() || raw.startsWith("#")) continue
            val cols = raw.split('\t')
            val key = unescape(cols[0])
            val values = Array(COLUMNS) { i -> cols.getOrNull(i + 1)?.takeIf { it.isNotBlank() }?.let(::unescape) }
            if ("{}" in key) {
                val parts = key.split("{}")
                val re = parts.joinToString("(.*?)") { Regex.escape(it) } // vide permis : suffixe du pluriel ("" ou "s")
                patterns += Pattern(parts, Regex(re, RegexOption.DOT_MATCHES_ALL), values)
            } else exact[key] = values
        }
        // Les gabarits les plus précis d'abord : ceux qui commencent par du texte fixe, puis le plus de texte fixe.
        patterns.sortWith(compareByDescending<Pattern> { it.anchored }.thenByDescending { it.fixed })
    }

    private fun column(l: Lang) = l.ordinal - 1

    /** Traduction de [s] dans la langue affichée. */
    fun tr(s: String): String {
        val l = lang
        if (l == Lang.Fr || s.isEmpty() || s.none { it.isLetter() }) return s
        val key = "${l.ordinal}\u0000$s"
        synchronized(cache) { cache[key] }?.let { return it }
        val r = lookup(s, column(l), 0) ?: s
        synchronized(cache) { cache[key] = r }
        return r
    }

    private fun lookup(s: String, col: Int, depth: Int): String? {
        exact[s]?.let { v -> v[col]?.let { return it } }
        if (s.none { it.isLetter() }) return s
        // Espaces ou ponctuation autour d'un texte connu (« Moteur : », « (vide) »…).
        val trimmed = s.trim()
        if (trimmed.length != s.length && trimmed.isNotEmpty()) {
            exact[trimmed]?.get(col)?.let { return s.substring(0, s.indexOf(trimmed)) + it + s.substring(s.indexOf(trimmed) + trimmed.length) }
        }
        if (depth < 3) {
            // D'abord un gabarit dont toutes les parties variables sont connues, puis les textes assemblés,
            // enfin un gabarit dont une partie reste telle quelle (un nom de véhicule, par exemple).
            matchPattern(s, col, depth, strict = true)?.let { return it }
            for (sep in listOf("\n", " · ", " — ", " : ", ", ")) {
                if (sep !in s) continue
                val pieces = s.split(sep)
                val out = pieces.map { lookup(it, col, depth + 1) }
                // « : » sans espace avant hors du français.
                val join = if (sep == " : ") ": " else sep
                if (out.any { it != null }) return pieces.indices.joinToString(join) { out[it] ?: pieces[it] }
            }
            matchPattern(s, col, depth, strict = false)?.let { return it }
        }
        return null
    }

    private fun matchPattern(s: String, col: Int, depth: Int, strict: Boolean): String? {
        for (p in patterns) {
            val v = p.values[col] ?: continue
            if (!s.startsWith(p.parts.first()) || !s.endsWith(p.parts.last())) continue
            val m = p.regex.matchEntire(s) ?: continue
            val raw = m.groupValues.drop(1)
            val groups = raw.map { g -> lookup(g, col, depth + 1) }
            // Strict : chaque partie variable est traduite, sans lettres, ou un suffixe de pluriel (« s », « x »).
            if (strict && raw.indices.any { groups[it] == null && raw[it].length > 2 && raw[it].any(Char::isLetter) }) continue
            return fill(v, raw.indices.map { groups[it] ?: raw[it] })
        }
        return null
    }

    /** Remplace les {} de la traduction par les valeurs, dans l'ordre (ou {1}, {2}… pour changer l'ordre). */
    private fun fill(template: String, values: List<String>): String {
        if (Regex("\\{\\d}").containsMatchIn(template)) return Regex("\\{(\\d)}").replace(template) { values.getOrElse(it.groupValues[1].toInt() - 1) { "" } }
        val sb = StringBuilder()
        var i = 0
        var k = 0
        while (i < template.length) {
            if (template.startsWith("{}", i)) { sb.append(values.getOrElse(k++) { "" }); i += 2 } else { sb.append(template[i]); i++ }
        }
        return sb.toString()
    }
}

/** Raccourci : texte traduit dans la langue affichée. */
fun tr(s: String): String = I18n.tr(s)
