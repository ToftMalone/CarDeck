package com.cardeck.app.ai

import com.cardeck.app.data.DtcCatalog
import com.cardeck.app.data.DtcScan
import com.cardeck.app.data.I18n
import com.cardeck.app.data.Lang
import com.cardeck.app.data.Readiness
import com.cardeck.app.data.Trip
import com.cardeck.app.data.VehicleTemplate
import com.cardeck.app.data.pidById
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Assistant de CarDeck (Gemini Nano, sur l'appareil). Le modèle ne lit pas la voiture et ne connaît pas ses valeurs
 * normales : CarDeck fait l'analyse lui-même (règles fiables ci-dessous) et donne au modèle un résumé de faits, en anglais
 * (la langue où il est le plus fiable). Le modèle répond dans la langue du téléphone, à partir de ces faits seulement.
 */
object Assistant {
    /** Ce que CarDeck sait de la voiture au moment de la question. */
    class Snapshot(
        val template: VehicleTemplate,
        val year: String,
        /** Boîtier connecté et voiture qui répond. */
        val connected: Boolean,
        /** Dernières valeurs lues (identifiant de donnée → valeur). */
        val live: Map<String, Double>,
        val lastScan: DtcScan?,
        val readiness: Readiness?,
        /** Trajets, le plus récent d'abord. */
        val trips: List<Trip>,
        val now: Long = System.currentTimeMillis(),
    )

    /** Questions toutes prêtes (en français : traduites à l'affichage). */
    val SUGGESTIONS = listOf(
        "Comment va mon moteur ?",
        "Mes codes défaut sont-ils graves ?",
        "Ma voiture passera-t-elle le contrôle technique ?",
        "Mon dernier trajet était-il économique ?",
        "Que dois-je surveiller avant un long trajet ?",
    )

    private val LANGUAGE = mapOf(Lang.Fr to "French", Lang.En to "English", Lang.De to "German", Lang.Ru to "Russian", Lang.Es to "Spanish")

    private fun en(s: String) = I18n.trIn(s, Lang.En)

    private fun num(v: Double, decimals: Int = 0) = String.format(Locale.US, "%.${decimals}f", v)

    private fun ago(ms: Long): String {
        val h = ms / 3_600_000
        return when {
            h < 1 -> "less than an hour ago"
            h < 24 -> "$h hours ago"
            else -> "${h / 24} days ago"
        }
    }

    /** Appréciation d'une valeur moteur (null = rien à signaler en particulier). */
    internal fun assess(id: String, v: Double, running: Boolean): String? = when (id) {
        "std05" -> when {
            v >= 112 -> "OVERHEATING: stop the engine as soon as safely possible"
            v >= 105 -> "high"
            v < 60 && running -> "engine still cold: avoid high revs"
            v < 75 && running -> "warming up"
            else -> "normal"
        }
        "std5C" -> when {
            v >= 140 -> "very high"
            v >= 130 -> "high"
            v < 60 && running -> "oil still cold: avoid high revs"
            else -> "normal"
        }
        "std07", "std09" -> when {
            abs(v) >= 20 -> "very ${if (v > 0) "high (engine running lean: air leak or fuel delivery problem likely)" else "low (engine running rich)"}"
            abs(v) >= 10 -> if (v > 0) "high (engine compensating a lean mixture)" else "low (engine compensating a rich mixture)"
            else -> "normal"
        }
        "std06", "std08" -> if (abs(v) >= 15) "large short-term correction" else null
        "std0F" -> if (v >= 60) "high intake air temperature" else null
        "std2F" -> if (v < 12) "low fuel level" else null
        else -> null
    }

    /** Données moteur reprises dans le résumé (les plus parlantes), dans cet ordre. */
    private val KEY_PIDS = listOf(
        "std0C", "std0D", "std05", "std5C", "std0F", "std46", "std04", "std0B", "std10", "std11",
        "std06", "std07", "std08", "std09", "std0E", "std2F", "std5E", "std1F", "std21", "stdA6",
    )

    /** Résumé des faits, en anglais, pour le modèle. */
    fun facts(s: Snapshot): String {
        val sb = StringBuilder()
        fun a(x: String) = sb.append(x).append('\n')
        val t = s.template
        a("Vehicle: ${listOf(t.brand, t.model, t.code).filter { it.isNotBlank() }.joinToString(" ")}" +
            listOf(t.engine, t.power, en(t.fuel)).filter { it.isNotBlank() }.joinToString(", ", " (", ")") +
            if (s.year.isNotBlank()) ", ${s.year}" else "")

        // Données en direct.
        if (!s.connected) a("Live data: OBD2 adapter not connected, no live data right now.")
        else {
            val running = (s.live["std0C"] ?: 0.0) > 300
            a("Live data (adapter connected, engine ${if (running) "running" else "not running, ignition on"}):")
            val ids = KEY_PIDS.filter { it in s.live } + s.live.keys.filter { it !in KEY_PIDS }.sorted().take(10)
            if (ids.isEmpty()) a("- no value read yet")
            for (id in ids) {
                val v = s.live[id] ?: continue
                val def = pidById(id) ?: continue
                val label = en(def.label)
                val value = if (def.states != null) en(def.text(v)) else "${num(v, def.decimals)} ${en(def.unit)}".trim()
                val note = assess(id, v, running)
                a("- $label: $value" + (note?.let { " [$it]" } ?: ""))
            }
        }

        // Codes défaut du dernier diagnostic.
        val scan = s.lastScan
        if (scan == null) a("Fault codes: no diagnosis scan done yet.")
        else {
            a("Last diagnosis scan: ${ago(s.now - scan.t)}, ${scan.ecus} ECU(s) answered.")
            if (scan.codes.isEmpty()) a("- no fault code")
            for (c in scan.codes.take(12)) {
                val info = DtcCatalog.info(c.code)
                val sev = when (info.sev.name) { "Crit" -> "critical"; "Low" -> "minor"; else -> "medium" }
                a("- ${c.code} (${en(c.status.label).lowercase()}, ${en(c.ecu)}): ${en(info.title)} [severity: $sev]")
            }
            if (scan.codes.size > 12) a("- and ${scan.codes.size - 12} more code(s)")
        }

        // Tests antipollution (contrôle technique).
        val r = s.readiness
        if (r == null) a("Emissions readiness: not checked yet.")
        else {
            val notReady = r.monitors.filter { !it.ready }
            a("Emissions readiness: check engine light ${if (r.mil) "ON" else "off"}, ${r.dtcCount} emission code(s) reported, " +
                (if (notReady.isEmpty()) "all self-tests completed" else "${notReady.size} self-test(s) not completed: ${notReady.joinToString { en(it.label) }}") +
                (r.kmSinceClear?.let { ", $it km since codes were cleared" } ?: "") + ".")
        }

        // Trajets.
        if (s.trips.isEmpty()) a("Trips: none recorded yet.")
        else {
            val last = s.trips.first()
            val min = ((last.end - last.start) / 60_000.0).roundToInt()
            a("Last trip: ${ago(s.now - last.end)}, ${num(last.distanceKm, 1)} km in $min min, average ${num(last.avgKmh)} km/h, max ${num(last.maxKmh)} km/h" +
                (last.fuelPrice?.let { p -> ", fuel price ${num(p, 3)} €/L" } ?: "") + ".")
            val week = s.trips.filter { s.now - it.start < 7 * 86_400_000L }
            if (week.size > 1) a("Last 7 days: ${week.size} trips, ${num(week.sumOf { it.distanceKm }, 0)} km.")
        }
        return sb.toString().trim()
    }

    /** Consigne complète envoyée au modèle. [previous] = échange précédent (question, réponse), pour une question de suite. */
    fun prompt(s: Snapshot, question: String, lang: Lang, previous: Pair<String, String>? = null): String = buildString {
        append("You are the assistant of CarDeck, a car diagnostic app that reads the car through its OBD2 port.\n")
        append("Rules:\n")
        append("- Answer ONLY from the FACTS below. Never invent values, fault codes or history.\n")
        append("- If the facts do not answer the question, say so and suggest what to do in CarDeck (connect the adapter, run a diagnosis scan, check the emissions readiness).\n")
        append("- Values marked in [brackets] are CarDeck's own assessment: trust them.\n")
        append("- Be short (at most 120 words), friendly and concrete, with short bullet points when useful.\n")
        append("- You are not a mechanic: for anything serious or unclear, advise seeing a professional. If something is dangerous (overheating, oil pressure, critical code), say it first.\n")
        append("- Never tell the driver to use the phone while driving.\n")
        append("- Answer in ${LANGUAGE[lang] ?: "English"}.\n\n")
        append("FACTS:\n").append(facts(s)).append("\n\n")
        if (previous != null) append("PREVIOUS QUESTION: ${previous.first}\nPREVIOUS ANSWER: ${previous.second.take(600)}\n\n")
        append("QUESTION: ").append(question.trim())
    }
}
