package com.cardeck.app.explorer

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeMap

/** Réponse positive enregistrée. */
class Resp(var hex: String, val seen: String, var last: String, var changed: Boolean = false)

/** Identifiant actif sur le bus CAN. */
class BusId(val seen: Int, val hz: Double, val last: String)

/**
 * Mémoire des découvertes d'un véhicule (reprise après interruption), équivalent de « decouvertes.json ».
 * Clé d'une réponse : « calculateur:requête » (ex. « 7E0:2101 », « OBD:010C »).
 */
class ExplorerData {
    val ecus = LinkedHashMap<String, Ecu>()
    val answers = TreeMap<String, Resp>()
    val refusals = TreeMap<String, String>()
    val progress = HashMap<String, Int>()
    val flags = TreeMap<String, String>()
    val bus = TreeMap<Int, BusId>()
    val faults = HashMap<String, Map<String, String>>()
    val history = ArrayList<Snapshot>()
    val drives = ArrayList<String>()

    /** Enregistre une réponse ; renvoie « ok », « refus! » (refus qui dit que l'identifiant existe) ou l'état. */
    fun note(ecu: String, req: String, a: Answer, now: String = stamp()): String {
        val key = "$ecu:$req"
        if (a.ok) {
            val h = Explore.hex(a.payload)
            val e = answers[key]
            if (e == null) answers[key] = Resp(h, now, now)
            else { if (e.hex != h) e.changed = true; e.hex = h; e.last = now }
            return "ok"
        }
        val nrc = a.nrc
        if (nrc != null) {
            val (label, banal) = Explore.REFUSALS[nrc] ?: ("code 0x%02X".format(nrc) to false)
            if (!banal) { refusals[key] = label; return "refus!" }
        }
        return a.state.name.lowercase()
    }

    /** Blocs de données constructeur qui ont répondu (hors identification) : (calculateur, requête). */
    fun dataBlocks(ecu: String? = null): List<Pair<String, String>> = answers.keys.mapNotNull { k ->
        val c = k.substringBefore(':')
        val req = k.substringAfter(':')
        if (c !in ecus || (ecu != null && c != ecu)) null
        else if (req.startsWith("21") || (req.startsWith("22") && !req.startsWith("22F1"))) c to req else null
    }

    fun addSnapshot(s: Snapshot) {
        history += s
        while (history.size > 400) history.removeAt(0)
    }

    /** Noms lisibles des calculateurs (identification en texte), sans les textes communs à plusieurs (VIN…). */
    fun names(): Map<String, String> {
        val texts = HashMap<String, MutableList<String>>()
        for ((k, v) in answers) {
            val c = k.substringBefore(':')
            val req = k.substringAfter(':')
            if (c !in ecus || !(req.startsWith("1A") || req.startsWith("22F1"))) continue
            val body = Explore.bytes(v.hex).drop(Explore.echoSize(req)).toIntArray()
            if (Explore.isText(body)) texts.getOrPut(c) { mutableListOf() } += Explore.readable(body).trim(' ', '·')
        }
        val count = texts.values.flatMap { it.toSet() }.groupingBy { it }.eachCount()
        return texts.mapNotNull { (c, list) ->
            val own = list.filter { (count[it] ?: 0) == 1 && it.isNotBlank() }
            val speaking = own.firstOrNull { n -> Explore.SYSTEM_WORDS.any { n.uppercase().contains(it) } }
            (speaking ?: own.firstOrNull())?.let { c to it }
        }.toMap()
    }

    // ------------------------------------------------------------------ Rapport

    fun report(vehicle: String, version: String, driveRows: List<Map<String, Double>> = emptyList()): String {
        val sb = StringBuilder()
        fun a(s: String = "") = sb.append(s).append('\n')
        a("# Rapport d'exploration — $vehicle")
        a()
        a("Généré le ${SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE).format(Date())} par CarDeck $version, en lecture seule.")
        a()
        a("## Calculateurs")
        a()
        if (ecus.isEmpty()) a("Aucun calculateur trouvé pour l'instant (lancez le balayage à l'arrêt).")
        else {
            val names = names()
            a("| Adresse | Répond en | Liaison | Protocole | Ce qu'on en sait |")
            a("|---|---|---|---|---|")
            for ((k, e) in ecus) {
                val rx = when (e.bus) { Bus.Can11 -> "%03X".format(e.rx); Bus.Can29 -> "%08X".format(e.rx); Bus.K -> "%02X".format(e.rx) }
                val link = when (e.bus) { Bus.Can11 -> "CAN 11 bits"; Bus.Can29 -> "CAN 29 bits"; Bus.K -> "ligne K" }
                a("| `$k` | `$rx` | $link | ${e.proto.uppercase()} | ${names[k]?.let { "**$it**" } ?: "*à identifier*"} |")
            }
        }
        flags["artefacts"]?.takeIf { it.isNotBlank() }?.let { a(); a("Adresses fonctionnelles / échos écartés : $it") }
        a()
        a("Recherche : CAN 11 bits ${done("can11")} · CAN 29 bits ${done("can29")} · ligne K ${done("k")}")

        a(); a("## Ce que chaque calculateur accepte de donner"); a()
        val perEcu = answers.keys.groupBy { it.substringBefore(':') }
        a("| Calculateur | Réponses positives | Services |")
        a("|---|---|---|")
        for ((c, keys) in perEcu.toSortedMap()) a("| `$c` | ${keys.size} | ${keys.map { it.substringAfter(':').take(2) }.toSortedSet().joinToString(", ") { "`$it`" }} |")

        val blocks = dataBlocks()
        if (blocks.isNotEmpty()) {
            val distinct = HashMap<String, MutableSet<String>>()
            for (h in history) for ((k, v) in h.blocks) distinct.getOrPut(k) { mutableSetOf() } += v
            a(); a("## Blocs de données constructeur — ${blocks.size} trouvés"); a()
            a("Ce que les calculateurs savent et qu'aucun PID standard ne donne. « Vivant » = a pris plusieurs valeurs.")
            a()
            a("| Bloc | Taille | État | Dernière valeur |")
            a("|---|---|---|---|")
            for ((c, req) in blocks) {
                val k = "$c:$req"
                val e = answers.getValue(k)
                val body = Explore.bytes(e.hex).drop(Explore.echoSize(req))
                val n = ((distinct[k] ?: mutableSetOf()) + e.hex).size
                val state = if (n > 1 || e.changed) "🟢 vivant ($n valeurs)" else "figé (pour l'instant)"
                a("| `$k` | ${body.size} | $state | `${body.joinToString(" ") { "%02X".format(it) }.take(47)}` |")
            }
        }

        val clues = Explore.correlate(history)
        a(); a("## Pistes de décodage par corrélation"); a()
        if (clues.isNotEmpty()) {
            a("Sur ${history.size} instantanés. **Ce sont des hypothèses** : un octet qui suit la température d'eau est très probablement elle, mais à confirmer.")
            a()
            for (p in clues.take(40)) a("- `${p.key}` ${p.position} → **${p.formula}** (r = ${"%+.3f".format(p.r)}, ${p.n} relevés)${if (p.time) " ⚠️ *suit aussi le temps écoulé*" else ""}")
        } else a("${history.size} instantané(s) pour l'instant : il en faut au moins 6 avec des valeurs qui bougent. Le plus efficace : lancer l'exploration en roulant.")

        val texts = answers.mapNotNull { (k, v) ->
            val req = k.substringAfter(':')
            val body = Explore.bytes(v.hex).drop(Explore.echoSize(req)).toIntArray()
            if (Explore.isText(body)) k to Explore.readable(body).trim() else null
        }
        if (texts.isNotEmpty()) {
            a(); a("## Réponses lisibles en clair"); a()
            a("| Requête | Contenu |"); a("|---|---|")
            texts.forEach { (k, t) -> a("| `$k` | `$t` |") }
        }
        if (refusals.isNotEmpty()) {
            a(); a("## Identifiants qui existent mais refusent"); a()
            a("Ils ne disent pas « inconnu » : ils existent et demandent autre chose."); a()
            a("| Requête | Réponse |"); a("|---|---|")
            refusals.forEach { (k, v) -> a("| `$k` | $v |") }
        }
        val s23 = flags.filterKeys { it.startsWith("s23:") }
        if (s23.isNotEmpty()) {
            a(); a("## Lecture mémoire (service 23)"); a()
            s23.forEach { (k, v) -> a("- `${k.removePrefix("s23:")}` : $v") }
        }
        if (bus.isNotEmpty()) {
            a(); a("## Bus CAN — ${bus.size} identifiants actifs"); a()
            a("| Identifiant | Fréquence | Dernière valeur |"); a("|---|---|---|")
            bus.forEach { (id, b) -> a("| `0x%03X` | ${b.hz} Hz | `${b.last}` |".format(id)) }
        }
        val before = faults["avant"]
        val after = faults["apres"]
        if (before != null && after != null) {
            a(); a("## Codes défaut avant / après le balayage"); a()
            // Comparaison sur ce qui a été lu les deux fois (au 1er passage, les calculateurs sont trouvés entre les deux).
            val changed = faultChanges()
            if (changed.isEmpty()) a("✅ Identiques : le balayage n'a rien créé.")
            else {
                a("⚠️ Différence détectée :"); a()
                for (k in changed) a("- `$k` : `${before[k]}` → `${after[k]}`")
            }
        }
        if (driveRows.size > 200) {
            val maps = Explore.engineMaps(driveRows)
            if (maps.isNotBlank()) {
                a(); a("## Cartes moteur (dernier enregistrement en roulant)"); a()
                a("Valeurs effectivement appliquées, moyennées par case (au moins 3 mesures).")
                sb.append(maps)
            }
        }
        if (drives.isNotEmpty()) {
            a(); a("## Enregistrements en roulant"); a()
            drives.forEach { a("- `route/$it` (obd.csv, diag.csv, bus.csv)") }
        }
        a(); a("## Ce qu'il reste à faire"); a()
        val uds = ecus.values.filter { it.proto == "uds" && it.bus != Bus.K && it.key !in UDS_SKIPPED }
        if (uds.isEmpty()) a("- Aucun calculateur UDS à balayer.")
        for (e in uds) {
            val n = progress["22:${e.key}"] ?: 0
            a("- Calculateur ${e.key} : $n/${Explore.UDS_TOTAL} identifiants UDS testés" + if (n < Explore.UDS_TOTAL) ". Relancez le balayage à l'arrêt pour continuer." else " ✅")
        }
        a("- Décodage : lancez l'exploration en roulant, les pistes de corrélation s'affinent à chaque trajet.")
        return sb.toString()
    }

    /** Sources de codes défaut lues avant et après le balayage dont la réponse a changé. */
    fun faultChanges(): List<String> {
        val before = faults["avant"] ?: return emptyList()
        val after = faults["apres"] ?: return emptyList()
        return before.keys.intersect(after.keys).filter { before[it] != after[it] }.sorted()
    }

    private fun done(k: String) = if (flags[k] != null) "✅" else "à faire"

    // ------------------------------------------------------------------ Sauvegarde (JSON)

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("ecus", JSONObject().also { o -> ecus.forEach { (k, e) -> o.put(k, JSONObject().put("bus", e.bus.name).put("tx", e.tx).put("rx", e.rx).put("proto", e.proto)) } })
        put("reponses", JSONObject().also { o -> answers.forEach { (k, r) -> o.put(k, JSONObject().put("hex", r.hex).put("vu_le", r.seen).put("dernier", r.last).put("a_change", r.changed)) } })
        put("refus_interessants", JSONObject(refusals as Map<*, *>))
        put("avancement", JSONObject(progress as Map<*, *>))
        put("config", JSONObject(flags as Map<*, *>))
        put("bus", JSONObject().also { o -> bus.forEach { (id, b) -> o.put("%03X".format(id), JSONObject().put("vus", b.seen).put("hz", b.hz).put("dernier", b.last)) } })
        put("defauts", JSONObject().also { o -> faults.forEach { (k, m) -> o.put(k, JSONObject(m as Map<*, *>)) } })
        put("historique", JSONArray().also { arr ->
            history.forEach { h -> arr.put(JSONObject().put("t", h.t).put("moment", h.moment).put("refs", JSONObject(h.refs as Map<*, *>)).put("blocs", JSONObject(h.blocks as Map<*, *>))) }
        })
        put("roulages", JSONArray(drives))
    }

    fun save(file: File) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(toJson().toString(1))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    companion object {
        /** Calculateurs UDS jamais balayés en entier : interface ISO 26021 (fin de vie des airbags). */
        val UDS_SKIPPED = setOf("7F1")

        fun stamp(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

        fun load(file: File): ExplorerData {
            val d = ExplorerData()
            if (!file.exists()) return d
            val o = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return d
            o.optJSONObject("ecus")?.let { e -> e.keys().forEach { k -> val x = e.getJSONObject(k); d.ecus[k] = Ecu(k, Bus.valueOf(x.getString("bus")), x.getInt("tx"), x.getInt("rx"), x.getString("proto")) } }
            o.optJSONObject("reponses")?.let { e -> e.keys().forEach { k -> val x = e.getJSONObject(k); d.answers[k] = Resp(x.getString("hex"), x.optString("vu_le"), x.optString("dernier"), x.optBoolean("a_change")) } }
            o.optJSONObject("refus_interessants")?.let { e -> e.keys().forEach { k -> d.refusals[k] = e.getString(k) } }
            o.optJSONObject("avancement")?.let { e -> e.keys().forEach { k -> d.progress[k] = e.getInt(k) } }
            o.optJSONObject("config")?.let { e -> e.keys().forEach { k -> d.flags[k] = e.getString(k) } }
            o.optJSONObject("bus")?.let { e -> e.keys().forEach { k -> val x = e.getJSONObject(k); d.bus[k.toInt(16)] = BusId(x.getInt("vus"), x.getDouble("hz"), x.getString("dernier")) } }
            o.optJSONObject("defauts")?.let { e -> e.keys().forEach { k -> val x = e.getJSONObject(k); d.faults[k] = x.keys().asSequence().associateWith { x.getString(it) } } }
            o.optJSONArray("historique")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val h = arr.getJSONObject(i)
                    val refs = h.getJSONObject("refs").let { r -> r.keys().asSequence().associateWith { r.getDouble(it) } }
                    val blocs = h.getJSONObject("blocs").let { r -> r.keys().asSequence().associateWith { r.getString(it) } }
                    d.history += Snapshot(h.optString("t"), h.optString("moment"), refs, blocs)
                }
            }
            o.optJSONArray("roulages")?.let { arr -> for (i in 0 until arr.length()) d.drives += arr.getString(i) }
            return d
        }
    }
}
