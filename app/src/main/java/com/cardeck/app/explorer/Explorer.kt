package com.cardeck.app.explorer

import com.cardeck.app.obd.Elm327
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ce que l'explorateur montre à l'écran pendant son travail. */
interface ExplorerListener {
    fun step(index: Int, total: Int, name: String)
    fun detail(text: String?, progress: Float? = null)
    fun line(text: String)
}

/**
 * Explorateur universel, en lecture seule : portage des scripts de rétro-ingénierie de la Swift.
 *  - à l'arrêt : recherche de tous les calculateurs (CAN 11 et 29 bits, ligne K), identification, blocs KWP 21xx,
 *    identifiants UDS 22xxxx par tranches, lecture mémoire, cartographie du bus, relevés pour la corrélation ;
 *  - en roulant : OBD de référence + blocs constructeur + écoute du bus, enregistrés en CSV, puis cartes moteur.
 * Tout passe par [Elm327.raw] avec la liste blanche de lecture ([Elm327.readOnly] forcé par l'appelant).
 */
class Explorer(
    private val e: Elm327,
    private val data: ExplorerData,
    private val dir: File,
    private val ui: ExplorerListener,
) {
    private val file get() = File(dir, "decouvertes.json")
    private var headers: Boolean? = null
    private var aim: String? = null
    private var proto = ""
    private var fast = true
    private val slow = HashSet<String>()
    private var lastSave = 0L

    /** Protocole du véhicule (après la remise à zéro) et protocoles CAN de même débit. */
    private var vehicleProto = "6"
    private val base11 get() = if (vehicleProto == "8" || vehicleProto == "9") "8" else "6"
    private val base29 get() = if (base11 == "8") "9" else "7"

    fun save(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSave < 5000) return
        lastSave = now
        runCatching { data.save(file) }
    }

    // ------------------------------------------------------------------ Liaison

    /** Boîtier remis à zéro, réglé pour l'exploration. Renvoie false si la voiture ne répond pas (contact coupé). */
    suspend fun prepare(): Boolean {
        val ok = e.reset()
        vehicleProto = e.protocolNum.lastOrNull()?.toString() ?: "6"
        proto = vehicleProto
        headers = false
        aim = null
        // Contrôle de flux : « envoie tout, 5 ms entre trames » (les longs blocs saturent sinon le Bluetooth).
        at("ATFCSD300005"); at("ATFCSM1"); at("ATAL")
        return ok
    }

    private suspend fun lines(cmd: String, timeout: Long = 5000, keepInit: Boolean = false): List<String> =
        e.raw(cmd, timeout, keepInit).lines().map { it.trim() }.filter { it.isNotEmpty() }

    private suspend fun at(c: String) { e.raw(c, 4000) }

    private suspend fun hdr(on: Boolean) {
        if (headers != on) { at(if (on) "ATH1" else "ATH0"); headers = on }
    }

    private suspend fun setProto(p: String) {
        if (proto != p) { at("ATSP$p"); proto = p; aim = null }
    }

    private suspend fun aimEcu(t: Ecu) {
        val key = "${t.bus}:${t.tx}:${t.rx}"
        if (aim == key) return
        when (t.bus) {
            Bus.Can11 -> {
                setProto(base11)
                at("ATSH%03X".format(t.tx)); at("ATCRA%03X".format(t.rx))
                // Le contrôle de flux part vers l'adresse de requête (pas « réponse − 8 »).
                at("ATFCSH%03X".format(t.tx))
            }
            Bus.Can29 -> {
                setProto(base29)
                at("ATCP%02X".format((t.tx ushr 24) and 0x1F)); at("ATSH%06X".format(t.tx and 0xFFFFFF))
                at("ATCRA%08X".format(t.rx)); at("ATFCSH%08X".format(t.tx))
            }
            Bus.K -> at("ATSH81%02XF1".format(t.tx))
        }
        aim = key
    }

    /** Interroge un calculateur précis ; réponse contrôlée (bon calculateur, bonne requête, message complet). */
    suspend fun ask(t: Ecu, hexReq: String): Answer {
        hdr(true)
        aimEcu(t)
        val req = Explore.bytes(hexReq)
        if (t.bus == Bus.K) {
            for (l in lines(hexReq, 4000)) {
                val (src, d) = Explore.kDecode(l) ?: continue
                if (src != t.tx || d.isEmpty()) continue
                if (d[0] == req[0] + 0x40) return Answer(AnswerState.Ok, d)
                if (d[0] == 0x7F && d.size >= 3 && d[1] == req[0] && d[2] != 0x78) return Answer(AnswerState.Refus, d)
            }
            return Answer(AnswerState.Rien)
        }
        val quick = fast && hexReq !in slow
        val l = lines(hexReq + if (quick) "1" else "")
        if (quick && l == listOf("?")) {
            // Boîtier qui ne connaît pas le suffixe « 1 » (réponse rapide) : on continue en mode normal.
            fast = false
            ui.line("ℹ️ Ce boîtier ne gère pas la réponse rapide : mode normal (plus lent)")
            return ask(t, hexReq)
        }
        if (l.any { x -> Explore.ELM_ERRORS.any { x.contains(it) } }) return Answer(AnswerState.Erreur)
        val (msgs, missed) = Explore.reassemble(l, t.bus == Bus.Can29)
        val a = Explore.classify(msgs, t.rx, req)
        if (quick && (t.rx in missed || (a.state in setOf(AnswerState.Attente, AnswerState.Rien) && msgs[t.rx].orEmpty().isNotEmpty()))) {
            // En réponse rapide, certains boîtiers rendent la main après la 1re trame d'un long message : on relit en mode normal.
            slow += hexReq
            delay(300)
            return ask(t, hexReq)
        }
        if (t.rx in missed && a.state == AnswerState.Rien) return Answer(AnswerState.Incomplet)
        return a
    }

    /** Découverte CAN 11 bits : envoie à [tx] et accepte toute réponse entre 0x600 et 0x7FF (sans supposer l'écart). */
    private suspend fun probe11(tx: Int, hexReq: String): Set<Int> {
        hdr(true)
        setProto(base11)
        val key = "range11:$tx"
        if (aim != key) {
            at("ATSH%03X".format(tx)); at("ATCRA"); at("ATCM600"); at("ATCF600"); at("ATFCSH%03X".format(tx))
            aim = key
        }
        val req = Explore.bytes(hexReq)
        val (msgs, _) = Explore.reassemble(lines(hexReq, 3000))
        return msgs.filter { (id, ms) -> id != tx && ms.any { Explore.correspond(it, req) } }.keys
    }

    /** Découverte CAN 29 bits : requête 18 DA <cible> F1, réponses 18 DA F1 xx. */
    private suspend fun probe29(target: Int, hexReq: String): Set<Int> {
        hdr(true)
        setProto(base29)
        val tx = 0x18DA00F1 or (target shl 8)
        val key = "range29:$tx"
        if (aim != key) {
            at("ATCP18"); at("ATSH%06X".format(tx and 0xFFFFFF)); at("ATCRA"); at("ATCM1FFFFF00"); at("ATCF18DAF100"); at("ATFCSH%08X".format(tx))
            aim = key
        }
        val req = Explore.bytes(hexReq)
        val (msgs, _) = Explore.reassemble(lines(hexReq, 3000), wide = true)
        return msgs.filter { (id, ms) -> id != tx && ms.any { Explore.correspond(it, req) } }.keys
    }

    /** Requête OBD diffusée (7DF / 18DB33F1, ou en-tête par défaut hors CAN) : réponses positives par calculateur. */
    suspend fun obd(hexReq: String): Map<Int, IntArray> {
        val req = Explore.bytes(hexReq)
        if (!e.isCan) {
            hdr(false)
            setProto(vehicleProto)
            val r = e.raw(hexReq, 6000)
            if (e.isError(r)) return emptyMap()
            return e.udsMessages(r).firstOrNull { Explore.correspond(it, req) && it[0] != 0x7F }?.let { mapOf(0x10 to it) } ?: emptyMap()
        }
        hdr(true)
        val wide = !e.is11bit
        if (aim != "obd") {
            if (wide) { setProto(base29); at("ATCP18"); at("ATSHDB33F1"); at("ATCRA"); at("ATFCSH18DA10F1") }
            else { setProto(base11); at("ATSH7DF"); at("ATCRA"); at("ATFCSH7E0") }
            aim = "obd"
        }
        val l = lines(hexReq, 6000)
        val (msgs, _) = Explore.reassemble(l, wide)
        return msgs.keys.mapNotNull { id -> Explore.classify(msgs, id, req).takeIf { it.ok }?.let { id to it.payload } }.toMap()
    }

    /** Réponse du moteur (ou du premier calculateur) à une requête OBD. */
    private suspend fun obdOne(hexReq: String): IntArray? {
        val m = obd(hexReq)
        return m[0x7E8] ?: m[0x18DAF110] ?: m.values.firstOrNull()
    }

    /** Lit des PIDs du service 01 (groupés si possible). */
    suspend fun obdValues(pids: List<Int>, grouped: Boolean = true): Map<String, Double> {
        if (grouped && e.isCan && pids.size > 1) {
            val p = obdOne("01" + pids.joinToString("") { "%02X".format(it) })
            val v = p?.let { Explore.decodeMode01(it, pids) }.orEmpty()
            if (v.size >= pids.count { Explore.PIDS[it]?.f != null }) return v
        }
        val out = LinkedHashMap<String, Double>()
        for (pid in pids) obdOne("01%02X".format(pid))?.let { out += Explore.decodeMode01(it, listOf(pid)) }
        return out
    }

    private suspend fun openSession(t: Ecu) = ask(t, if (t.proto == "kwp") "1081" else "1001")

    private suspend fun detectProto(t: Ecu): String {
        if (ask(t, "1081").ok) return "kwp"
        if (ask(t, "1001").ok) return "uds"
        val a = ask(t, "22F190")
        return if (a.ok || (a.nrc != null && a.nrc != 0x11)) "uds" else "kwp"
    }

    // ------------------------------------------------------------------ Balayage générique

    /**
     * Balaye [count] identifiants d'un calculateur (identifiant n = [idAt]), avec reprise ([key] = avancement)
     * et sans jamais bloquer : 20 silences de suite → réouverture de session, sinon calculateur suivant.
     */
    private suspend fun sweep(t: Ecu, service: Int, count: Int, width: Int, key: String, limit: Int? = null, idAt: (Int) -> Int = { it }): Int {
        val start = minOf(data.progress[key] ?: 0, count)
        val end = if (limit != null) minOf(count, start + limit) else count
        if (start >= end) { ui.line("   ✔ ${t.label} : déjà terminé"); return 0 }
        openSession(t)
        var found = 0
        var mute = 0
        val t0 = System.currentTimeMillis()
        for (n in start until end) {
            val req = "%02X%0${width * 2}X".format(service, idAt(n))
            val a = ask(t, req)
            when (data.note(t.key, req, a)) {
                "ok" -> { found++; ui.line("🟢 ${t.label} $req → ${Explore.hex(a.payload).chunked(2).joinToString(" ").take(48)}") }
                "refus!" -> ui.line("🟠 ${t.label} $req existe mais refuse : ${data.refusals["${t.key}:$req"]}")
            }
            mute = if (a.state == AnswerState.Rien || a.state == AnswerState.Erreur) mute + 1 else 0
            if (mute >= 20) {
                data.progress[key] = maxOf(0, n - 19)
                save(true)
                delay(1000)
                if (!openSession(t).ok) { ui.line("⚠️ ${t.label} ne répond plus : on passe au suivant (relancez pour reprendre)"); return found }
                mute = 0
            }
            val done = n - start + 1
            if (done % 25 == 0 || n == end - 1) {
                data.progress[key] = n + 1
                save()
                val rate = done * 1000.0 / maxOf(1, System.currentTimeMillis() - t0)
                val left = (end - n - 1) / maxOf(rate, 0.01) / 60
                ui.detail("${t.label} · $req · ${n + 1}/$count · ${"%.1f".format(rate)}/s · reste ${"%.0f".format(left)} min · trouvés $found", (n + 1f - start) / (end - start))
            }
        }
        data.progress[key] = end
        save(true)
        ui.line("   ✔ ${t.label} : ${end - start} testés, $found réponse(s)")
        return found
    }

    private val canEcus get() = data.ecus.values.filter { it.bus != Bus.K }

    // ------------------------------------------------------------------ Balayage à l'arrêt

    suspend fun stationary(version: String, vehicle: String) {
        val steps: List<Pair<String, suspend () -> Unit>> = listOf(
            "Codes défaut avant le balayage" to { faults("avant") },
            "Calculateurs CAN 11 bits" to { findCan11() },
            "Calculateurs CAN 29 bits" to { findCan29() },
            "Calculateurs sur la ligne K" to { findK() },
            "Mesures OBD standard" to { obdModes() },
            "Identification des calculateurs" to { identification() },
            "Relevé des blocs (début)" to { snapshot("début") },
            "Blocs de données constructeur (KWP)" to { kwpBlocks() },
            "Identifiants UDS (tranche de 10 000)" to { udsSweep() },
            "Lecture mémoire (service 23)" to { memoryRead() },
            "Cartographie du bus CAN" to { busMap() },
            "Relevé des blocs (fin)" to { snapshot("fin") },
            "Codes défaut après le balayage" to { faults("apres") },
        )
        try {
            for ((i, s) in steps.withIndex()) {
                ui.step(i + 1, steps.size, s.first)
                ui.detail(null)
                try {
                    s.second()
                } catch (c: CancellationException) {
                    throw c
                } catch (x: IOException) {
                    ui.line("⚠️ ${x.message} : remise à zéro du boîtier")
                    if (!runCatching { prepare() }.getOrDefault(false)) { ui.line("❌ Le boîtier ou la voiture ne répond plus : relancez, l'exploration reprendra ici."); break }
                }
                save(true)
            }
        } finally {
            withContext(NonCancellable) { finish(version, vehicle) }
        }
    }

    /** Écrit les découvertes et le rapport. */
    suspend fun finish(version: String, vehicle: String, rows: List<Map<String, Double>> = emptyList()) {
        save(true)
        runCatching { File(dir, "RAPPORT.md").writeText(data.report(vehicle, version, rows)) }
    }

    private suspend fun faults(moment: String) {
        if (moment == "avant") data.faults.remove("apres")
        val res = LinkedHashMap<String, String>()
        for ((mode, label) in listOf("03" to "mémorisés", "07" to "en attente", "0A" to "permanents")) {
            for ((id, p) in obd(mode)) {
                res["OBD $label %X".format(id)] = Explore.hex(p.drop(1).toIntArray())
                ui.line("OBD $label 0x%X → %s".format(id, Explore.hex(p.drop(1).toIntArray())))
            }
        }
        for (t in canEcus) {
            openSession(t)
            val a = ask(t, if (t.proto == "kwp") "1800FF00" else "1902FF")
            if (a.ok) { res[t.key] = Explore.hex(a.payload.drop(1).toIntArray()); ui.line("${t.label} → ${res[t.key]?.take(48)}") }
        }
        data.faults[moment] = res
    }

    private suspend fun findCan11() {
        if (!e.isCan) { ui.line("Voiture hors CAN : étape sans objet"); return }
        if (data.flags["can11"] != null) { ui.line("✔ Déjà fait : ${data.ecus.values.count { it.bus == Bus.Can11 }} calculateur(s)"); return }
        at("ATST19")
        val seen = LinkedHashMap<Int, List<Int>>()
        // On n'émet jamais sur une adresse de réponse connue (ce serait parler avec la voix d'un calculateur),
        // ni sur 7DF (diffusion) ni sur 7F1 / 7F9 (ISO 26021, fin de vie des airbags).
        val knownRx = (data.ecus.values.filter { it.bus == Bus.Can11 }.map { it.rx } + (0x7E8..0x7EF)).toMutableSet()
        try {
            for (tx in 0x600..0x7FF) {
                if (tx == 0x7DF || tx == 0x7F1 || tx == 0x7F9 || tx in knownRx) continue
                val rep = probe11(tx, "3E00").ifEmpty { probe11(tx, "1001") }.ifEmpty { probe11(tx, "1081") }
                if (rep.isNotEmpty()) {
                    seen[tx] = rep.sorted()
                    knownRx += rep
                    ui.line("🟢 0x%03X → %s".format(tx, rep.sorted().joinToString { "0x%03X".format(it) }))
                }
                if (tx % 16 == 15) ui.detail("Adresse 0x%03X · %d trouvée(s)".format(tx, seen.size), (tx - 0x600 + 1) / 512f)
            }
        } finally {
            at("ATST32")
            aim = null
        }
        // Une même adresse de réponse vue depuis plusieurs adresses : on garde celle à l'écart habituel (+8 ou +0x10).
        val byRx = HashMap<Int, MutableList<Int>>()
        for ((tx, rxs) in seen) for (rx in rxs) byRx.getOrPut(rx) { mutableListOf() } += tx
        val artefacts = mutableSetOf<Int>()
        for ((rx, txs) in byRx) if (txs.size > 1) {
            val canon = txs.filter { rx - it == 0x08 || rx - it == 0x10 }
            if (canon.isNotEmpty()) artefacts += txs - canon.toSet()
        }
        for ((tx, rxs) in seen) {
            if (tx in artefacts) continue
            val t = Ecu("%03X".format(tx), Bus.Can11, tx, rxs[0], "kwp")
            t.proto = detectProto(t)
            data.ecus[t.key] = t
            ui.line("   0x%03X → 0x%03X (écart +0x%02X) · %s".format(tx, rxs[0], (rxs[0] - tx) and 0x7FF, t.proto.uppercase()))
        }
        data.flags["can11"] = seen.size.toString()
        data.flags["artefacts"] = artefacts.sorted().joinToString { "%03X".format(it) }
        ui.line("✔ ${seen.size - artefacts.size} calculateur(s) en CAN 11 bits")
    }

    private suspend fun findCan29() {
        if (!e.isCan) { ui.line("Voiture hors CAN : étape sans objet"); return }
        if (data.flags["can29"] != null) { ui.line("✔ Déjà vérifié"); return }
        at("ATST19")
        var n = 0
        try {
            for (target in 0x00..0xFF) {
                if (target == 0xF1) continue // adresse du testeur
                val rep = probe29(target, "3E00").ifEmpty { probe29(target, "1001") }
                for (rx in rep) {
                    val tx = 0x18DA00F1 or (target shl 8)
                    val t = Ecu("%08X".format(tx), Bus.Can29, tx, rx, "uds")
                    t.proto = detectProto(t)
                    data.ecus[t.key] = t
                    n++
                    ui.line("🟢 29 bits : cible 0x%02X → 0x%08X · %s".format(target, rx, t.proto.uppercase()))
                }
                if (target % 16 == 15) ui.detail("Cible 0x%02X".format(target), (target + 1) / 256f)
            }
        } finally {
            at("ATST32")
            setProto(vehicleProto)
            aim = null
        }
        data.flags["can29"] = n.toString()
        ui.line(if (n > 0) "✔ $n calculateur(s) en 29 bits" else "✔ Rien en 29 bits")
    }

    /** Réveil d'un calculateur de la ligne K (rapide ISO 14230, ou lent à 5 bauds). */
    private suspend fun kInit(adr: Int, slowInit: Boolean): Pair<Boolean, String> {
        setProto(if (slowInit) "4" else "5")
        hdr(true)
        if (slowInit) at("ATIIA%02X".format(adr))
        at("ATSH81%02XF1".format(adr))
        aim = "${Bus.K}:$adr:$adr"
        val text = e.raw(if (slowInit) "ATSI" else "ATFI", 9000, keepInit = true).uppercase()
        return (text.contains("OK") && !text.contains("ERROR")) to text
    }

    private val K_FIRST = listOf(0x40, 0x41, 0x60, 0x61, 0x10, 0x11, 0x28, 0x29, 0x58, 0x59, 0xC0, 0xC1, 0x18, 0x20, 0x30, 0x50, 0x70, 0x80, 0x48, 0x68)

    private suspend fun findK() {
        if (data.flags["k"] != null) { ui.line("✔ Déjà fait : ${data.ecus.values.count { it.bus == Bus.K }} calculateur(s)"); return }
        val order = K_FIRST + (0x01..0xFF).filter { it !in K_FIRST && it != 0xF1 }
        try {
            for ((pass, slowInit, list) in listOf(Triple("rapide", false, order), Triple("lent", true, K_FIRST))) {
                val key = "k_$pass"
                for (i in (data.progress[key] ?: 0) until list.size) {
                    val adr = list[i]
                    ui.detail("Réveil $pass 0x%02X · %d/%d".format(adr, i + 1, list.size), (i + 1f) / list.size)
                    if (data.ecus.containsKey("K%02X".format(adr))) { data.progress[key] = i + 1; continue }
                    val (ok, text) = kInit(adr, slowInit)
                    if (!ok && i == 0 && text.contains("?")) { ui.line("Le boîtier ne sait pas réveiller la ligne K : étape sautée"); data.progress[key] = list.size; break }
                    if (ok) kExplore(adr, pass)
                    data.progress[key] = i + 1
                    save()
                }
            }
            data.flags["k"] = data.ecus.values.count { it.bus == Bus.K }.toString()
        } finally {
            // Retour au protocole de la voiture, réglages du modèle compris.
            prepare()
        }
    }

    /** Calculateur trouvé sur la ligne K : identification, blocs, défauts (lecture seule). */
    private suspend fun kExplore(adr: Int, pass: String) {
        val t = Ecu("K%02X".format(adr), Bus.K, adr, adr, "kwp")
        var a = ask(t, "3E")
        if (a.state == AnswerState.Rien) a = ask(t, "1A8A")
        if (a.state == AnswerState.Rien) return // réveil sans conversation : faux positif
        data.ecus[t.key] = t
        ui.line("🟢 Ligne K 0x%02X répond (réveil %s)".format(adr, pass))
        val reqs = (0x80..0x9F).map { "1A%02X".format(it) } + (0x00..0xFF).map { "21%02X".format(it) } + listOf("1800FF00", "1802FF00")
        var mute = 0
        for (req in reqs) {
            val r = ask(t, req)
            if (data.note(t.key, req, r) == "ok" && req.startsWith("1A")) {
                val body = r.payload.drop(2).toIntArray()
                if (Explore.isText(body)) ui.line("   $req → ${Explore.readable(body)}")
            }
            if (r.state == AnswerState.Rien) {
                if (++mute >= 8) {
                    mute = 0
                    if (!kInit(adr, pass == "lent").first) break // session retombée et pas de nouveau réveil
                }
            } else mute = 0
        }
    }

    private suspend fun obdModes() {
        for ((mode, label) in listOf(0x01 to "mesures temps réel", 0x09 to "infos véhicule", 0x06 to "résultats d'autotests")) {
            val supported = mutableListOf<Int>()
            var base = 0
            while (base <= 0xE0) {
                val p = obdOne("%02X%02X".format(mode, base)) ?: break
                if (p.size < 6) break
                val bits = (p[2].toLong() shl 24) or (p[3].toLong() shl 16) or (p[4].toLong() shl 8) or p[5].toLong()
                for (k in 0 until 32) if (bits and (1L shl (31 - k)) != 0L) supported += base + k + 1
                if (bits and 1L == 0L) break
                base += 0x20
            }
            val list = supported.filter { it % 0x20 != 0 }
            ui.line("Mode %02X (%s) : %d pris en charge".format(mode, label, list.size))
            for ((i, pid) in list.withIndex()) {
                val req = "%02X%02X".format(mode, pid)
                ui.detail("Mode %02X · PID %02X".format(mode, pid), (i + 1f) / list.size)
                val p = obdOne(req) ?: continue
                data.note("OBD", req, Answer(AnswerState.Ok, p))
                val pidDef = Explore.PIDS[pid]
                val v = if (mode == 0x01 && pidDef?.f != null) Explore.decodeMode01(p, listOf(pid))[pidDef.name] else null
                ui.line("   $req " + (v?.let { "${pidDef!!.name} = ${"%.2f".format(it)} ${pidDef.unit}" } ?: Explore.hex(p.drop(1).toIntArray()).take(40)))
            }
        }
    }

    private suspend fun identification() {
        for (t in canEcus) {
            ui.line("── ${t.label} (${t.proto.uppercase()})")
            if (t.proto == "kwp") sweep(t, 0x1A, 0x100, 1, "1A:${t.key}")
            else sweep(t, 0x22, 0x100, 2, "22id:${t.key}") { 0xF100 + it }
        }
        val names = data.names()
        for ((k, n) in names) ui.line("📝 $k → $n")
    }

    private suspend fun kwpBlocks() {
        val list = canEcus.filter { it.proto == "kwp" }
        if (list.isEmpty()) ui.line("Aucun calculateur KWP2000")
        for (t in list) { ui.line("── ${t.label}"); sweep(t, 0x21, 0x100, 1, "21:${t.key}") }
    }

    private suspend fun udsSweep() {
        val list = canEcus.filter { it.proto == "uds" && it.key !in ExplorerData.UDS_SKIPPED }
        if (list.isEmpty()) ui.line("Aucun calculateur UDS à balayer")
        for (t in list) {
            ui.line("── ${t.label} : ${data.progress["22:${t.key}"] ?: 0}/${Explore.UDS_TOTAL} déjà testés")
            sweep(t, 0x22, Explore.UDS_TOTAL, 2, "22:${t.key}", limit = 10_000) { Explore.udsAt(it) }
        }
    }

    private suspend fun memoryRead() {
        val formats = listOf<Pair<String, (Int, Int) -> String>>(
            "kwp 3 octets" to { a, n -> "23%06X%02X".format(a, n) },
            "uds 2+2" to { a, n -> "2324%04X%04X".format(a, n) },
            "uds 4+1" to { a, n -> "2314%08X%02X".format(a, n) },
            "uds 2+1" to { a, n -> "2312%04X%02X".format(a, n) },
        )
        for (t in canEcus) {
            if (data.flags["s23:${t.key}"] != null) continue
            openSession(t)
            var verdict = "aucun format accepté"
            for ((name, f) in formats) {
                val a = ask(t, f(0, 16))
                if (a.ok) { verdict = "🟢 OUVERT en $name → ${Explore.hex(a.payload.drop(1).toIntArray())}"; break }
                val nrc = a.nrc ?: continue
                if (nrc == 0x33) { verdict = "🔒 verrouillé (accès sécurisé requis)"; break }
                if (nrc == 0x11) { verdict = "service 23 inconnu de ce calculateur"; break }
                verdict = Explore.REFUSALS[nrc]?.first ?: "0x%02X".format(nrc)
            }
            data.flags["s23:${t.key}"] = verdict
            ui.line("${t.label} : $verdict")
        }
    }

    private suspend fun busMap() {
        if (!e.isCan || !e.is11bit) { ui.line("Bus CAN 11 bits non accessible sur cette voiture : étape sans objet"); return }
        if (data.flags["bus"] != null) { ui.line("✔ Déjà fait : ${data.bus.size} identifiants actifs"); return }
        setProto(base11)
        val all = mutableListOf<Pair<Int, IntArray>>()
        val ms = 1200L
        for (base in 0x000 until 0x800 step 0x10) {
            ui.detail("Écoute 0x%03X-0x%03X".format(base, base + 15), (base + 16) / 2048f)
            val frames = e.monitor(listOf((base until base + 0x10).toList() to ms))
            headers = false; aim = null
            for ((id, list) in frames) for (d in list) all += id to d
        }
        val byId = all.groupBy({ it.first }, { it.second })
        for ((id, list) in byId) {
            data.bus[id] = BusId(list.size, Math.round(list.size / (ms / 1000.0) * 100) / 100.0, Explore.hex(list.last()))
            val moving = list.first().indices.filter { i -> list.map { it.getOrNull(i) }.toSet().size > 1 }
            ui.line("0x%03X  %.1f Hz  %s  bougent : %s".format(id, list.size / (ms / 1000.0), Explore.hex(list.last()), moving.joinToString(" ").ifEmpty { "—" }))
        }
        data.flags["bus"] = byId.size.toString()
        runCatching {
            File(dir, "can").mkdirs()
            File(dir, "can/${stampFile()}_carto.csv").bufferedWriter().use { w ->
                w.write("id,hex\n")
                for ((id, d) in all) w.write("%03X,%s\n".format(id, Explore.hex(d)))
            }
        }
        ui.line("✔ ${byId.size} identifiants actifs sur le bus")
    }

    /** Instantané : tous les blocs constructeur + les références OBD, pour la corrélation. */
    private suspend fun snapshot(moment: String) {
        val blocks = data.dataBlocks().filter { data.ecus[it.first]?.bus != Bus.K }
        if (blocks.isEmpty()) { ui.line("Rien à relever pour l'instant (aucun bloc connu)"); return }
        val refs = LinkedHashMap<String, Double>()
        for (g in Explore.REFERENCE_GROUPS) refs += obdValues(g)
        val values = LinkedHashMap<String, String>()
        val opened = HashSet<String>()
        for ((c, req) in blocks) {
            val t = data.ecus.getValue(c)
            if (opened.add(c)) openSession(t)
            val a = ask(t, req)
            if (a.ok) { values["$c:$req"] = Explore.hex(a.payload); data.note(c, req, a) }
        }
        data.addSnapshot(Snapshot(ExplorerData.stamp(), moment, refs, values))
        ui.line("✔ ${values.size} blocs relevés · ${data.history.size} instantané(s) pour la corrélation")
    }

    // ------------------------------------------------------------------ Enregistrement en roulant

    /**
     * Tourne en boucle : OBD de référence (2 requêtes par tour), blocs constructeur (4 découvertes + 3 relectures),
     * une tranche du bus. Tout est horodaté sur la même horloge, dans route/<date>/. S'arrête quand on le demande,
     * ou quand la voiture ne répond plus pendant 20 s (contact coupé).
     */
    suspend fun drive(version: String, vehicle: String) {
        val folder = stampFile()
        val out = File(dir, "route/$folder").apply { mkdirs() }
        val t0 = System.currentTimeMillis()
        fun t() = "%.3f".format(Locale.US, (System.currentTimeMillis() - t0) / 1000.0)

        // OBD : lecture groupée si elle marche, sinon PID par PID ; on ne garde que ce qui répond.
        ui.step(1, 2, "Préparation de l'enregistrement")
        val groups = mutableListOf<List<Int>>()
        val seenPids = HashSet<Int>()
        for (g in Explore.DRIVE_GROUPS) {
            if (e.isCan) {
                val p = obdOne("01" + g.joinToString("") { "%02X".format(it) })
                if (p != null && Explore.decodeMode01(p, g).size >= g.count { Explore.PIDS[it]?.f != null }) { groups += g; seenPids += g; continue }
            }
            for (pid in g) if (pid !in seenPids && obdOne("01%02X".format(pid))?.let { Explore.decodeMode01(it, listOf(pid)) }?.isNotEmpty() == true) { groups += listOf(pid); seenPids += pid }
        }
        val names = Explore.DRIVE_GROUPS.flatten().mapNotNull { Explore.PIDS[it]?.name }.distinct().sorted()
        if (groups.isEmpty()) ui.line("⚠️ Aucune mesure OBD ne répond : on continue avec le bus et les blocs")

        val kwp = canEcus.filter { it.proto == "kwp" }
        val rotation = data.dataBlocks().filter { data.ecus[it.first]?.bus != Bus.K }.toMutableList()
        val known = rotation.toMutableSet()
        val refused = (data.flags["refus_21"] ?: "").split(',').filter { it.isNotBlank() }.toMutableSet()
        val discover = ArrayDeque((0x00..0xFF).flatMap { i -> kwp.map { it.key to "21%02X".format(i) } }.filter { it !in known && "${it.first}:${it.second}" !in refused })
        for (t in kwp) openSession(t)
        val mute = HashMap<String, Int>()

        // Bus : tranches actives connues, sinon découvertes au premier tour.
        val busOk = e.isCan && e.is11bit
        val active = data.bus.keys.map { it and 0x7F0 }.toSortedSet().toMutableList()
        val slices = if (!busOk) mutableListOf() else active.ifEmpty { (0x000 until 0x800 step 0x10).toMutableList() }
        var discoveringBus = busOk && active.isEmpty()

        val fObd = File(out, "obd.csv").bufferedWriter()
        val fDiag = File(out, "diag.csv").bufferedWriter()
        val fBus = File(out, "bus.csv").bufferedWriter()
        fObd.write((listOf("t", "hex") + names).joinToString(",") + "\n")
        fDiag.write("t,calc,req,hex\n")
        fBus.write("t,fenetre_s,id,hex\n")
        data.drives += folder
        ui.step(2, 2, "Enregistrement en roulant")
        ui.line("📝 route/$folder · OBD : ${groups.size} requêtes par tour · blocs connus : ${rotation.size}")

        val rows = ArrayList<Map<String, Double>>()
        val lastRefs = LinkedHashMap<String, Double>()
        val lastBlocks = LinkedHashMap<String, String>()
        var nObd = 0; var nDiag = 0; var nBus = 0; var nNew = 0
        var iObd = 0; var iRot = 0; var iBus = 0; var fails = 0
        var lastAnswer = System.currentTimeMillis()
        var lastSnap = System.currentTimeMillis()
        var lastFlush = System.currentTimeMillis()
        try {
            while (true) {
                try {
                    // 1) OBD : deux requêtes par tour, à tour de rôle
                    repeat(minOf(2, groups.size)) {
                        val g = groups[iObd++ % groups.size]
                        val p = if (g.size > 1) obdOne("01" + g.joinToString("") { "%02X".format(it) }) else obdOne("01%02X".format(g[0]))
                        val v = p?.let { Explore.decodeMode01(it, g) }.orEmpty()
                        if (v.isNotEmpty()) {
                            lastAnswer = System.currentTimeMillis()
                            lastRefs += v
                            rows += v
                            if (rows.size > 60_000) rows.subList(0, 10_000).clear()
                            fObd.write((listOf(t(), Explore.hex(p!!)) + names.map { n -> v[n]?.let { "%.3f".format(Locale.US, it) } ?: "" }).joinToString(",") + "\n")
                            nObd++
                        }
                    }
                    // 2) Diagnostic : 4 découvertes + 3 relectures par tour
                    val batch = mutableListOf<Pair<String, String>>()
                    repeat(4) { discover.removeFirstOrNull()?.let { batch += it } }
                    repeat(3) { if (rotation.isNotEmpty()) batch += rotation[iRot++ % rotation.size] }
                    for ((c, req) in batch) {
                        val t = data.ecus[c] ?: continue
                        val a = ask(t, req)
                        if (a.ok) {
                            lastAnswer = System.currentTimeMillis()
                            fDiag.write("${t()},$c,$req,${Explore.hex(a.payload)}\n")
                            data.note(c, req, a)
                            lastBlocks["$c:$req"] = Explore.hex(a.payload)
                            nDiag++
                            mute[c] = 0
                            if (known.add(c to req)) { rotation += c to req; nNew++; ui.line("🆕 $c:$req découvert en roulant") }
                        } else if (a.nrc?.let { it in setOf(0x11, 0x12, 0x31) } == true) {
                            refused += "$c:$req" // n'existe pas : on n'y revient plus
                            mute[c] = 0
                        } else if (a.state == AnswerState.Rien || a.state == AnswerState.Erreur) {
                            val m = (mute[c] ?: 0) + 1
                            mute[c] = if (m >= 10) { openSession(t); 0 } else m // session retombée ?
                        }
                    }
                    // 3) Bus : une tranche de 16 identifiants par tour
                    if (slices.isNotEmpty()) {
                        val base = slices[iBus++ % slices.size]
                        val ms = if (discoveringBus) 400L else 800L
                        val start = t()
                        val frames = e.monitor(listOf((base until base + 0x10).toList() to ms))
                        headers = false; aim = null
                        for ((id, list) in frames) for (d in list) { fBus.write("$start,${ms / 1000.0},%03X,%s\n".format(id, Explore.hex(d))); nBus++ }
                        if (discoveringBus) {
                            if (frames.isEmpty()) { slices.remove(base); iBus-- }
                            if (iBus >= slices.size) discoveringBus = false // 1er tour fini : on garde les tranches actives
                        }
                    }
                    fails = 0
                } catch (c: CancellationException) {
                    throw c
                } catch (x: IOException) {
                    // Un raté Bluetooth ne doit pas tuer le trajet.
                    fails++
                    if (fails >= 12) { ui.line("❌ Liaison perdue pour de bon : enregistrement arrêté"); break }
                    if (fails % 2 == 0) runCatching { prepare(); kwp.forEach { openSession(it) } }
                    delay(1000)
                    continue
                }
                val now = System.currentTimeMillis()
                if (now - lastAnswer > 20_000) { ui.line("⏹ La voiture ne répond plus depuis 20 s (contact coupé ?) : enregistrement terminé"); break }
                if (now - lastSnap > 10_000 && lastRefs.isNotEmpty() && lastBlocks.isNotEmpty()) {
                    data.addSnapshot(Snapshot(ExplorerData.stamp(), "route", LinkedHashMap(lastRefs), LinkedHashMap(lastBlocks)))
                    lastBlocks.clear()
                    lastSnap = now
                }
                if (now - lastFlush > 15_000) {
                    fObd.flush(); fDiag.flush(); fBus.flush()
                    data.flags["refus_21"] = refused.sorted().joinToString(",")
                    save(true)
                    lastFlush = now
                }
                ui.detail("%.1f min · OBD %d · blocs %d (+%d nouveaux) · bus %d · %s".format((now - t0) / 60000.0, nObd, nDiag, nNew, nBus,
                    lastRefs["vitesse"]?.let { "%.0f km/h".format(it) } ?: ""))
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { fObd.close() }; runCatching { fDiag.close() }; runCatching { fBus.close() }
                data.flags["refus_21"] = refused.sorted().joinToString(",")
                ui.line("💾 ${"%.1f".format((System.currentTimeMillis() - t0) / 60000.0)} min : $nObd lectures OBD, $nDiag blocs ($nNew découverts), $nBus trames bus")
                finish(version, vehicle, rows)
            }
        }
    }

    private fun stampFile() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    /** Vitesse actuelle (km/h), ou null si la voiture ne répond pas. */
    suspend fun speed(): Double? = obdValues(listOf(0x0D), grouped = false)["vitesse"]
}
