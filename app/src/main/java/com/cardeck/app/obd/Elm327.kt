package com.cardeck.app.obd

import com.cardeck.app.data.DtcStatus
import com.cardeck.app.data.FoundCode
import com.cardeck.app.data.fr
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlinx.coroutines.delay

/** Refus ou absence de réponse d'un calculateur en UDS (la liaison avec le boîtier reste valide). */
class UdsError(message: String) : Exception(message)

/** Dialogue avec un interpréteur ELM327 (commandes AT + requêtes OBD2). */
class Elm327(private val t: ObdTransport) {
    private val buf = StringBuilder()

    var version: String = "ELM327"
        private set

    /** Numéro de protocole OBD retourné par ATDPN (ex. « 6 » = ISO 15765-4 CAN 11 bits 500 kb/s). */
    var protocolNum: String = ""
        private set
    var protocolName: String = ""
        private set

    /** Lecture seule (modèle qui l'exige) : seuls les services de la liste blanche partent vers la voiture. */
    var readOnly = false

    /**
     * Réponse rapide : un « 1 » ajouté aux requêtes OBD dont la réponse tient dans une trame évite d'attendre
     * la fin du délai (~200 ms) prévu pour d'autres calculateurs. Activé seulement si le boîtier l'accepte.
     */
    var fast = false
        private set

    /** Protocole imposé par le modèle de véhicule (null = recherche automatique). */
    private var forcedProtocol: String? = null

    private val isCan get() = protocolNum.lastOrNull()?.let { it in '6'..'9' || it in 'A'..'C' } ?: true

    /** Envoie une commande et renvoie la réponse nettoyée (lignes non vides, sans écho ni « SEARCHING… »). */
    suspend fun raw(cmd: String, timeoutMs: Long = 8000): String {
        if (!allowed(cmd)) return BLOCKED
        while (t.incoming.tryReceive().isSuccess) { /* purge */ }
        buf.clear()
        t.send((cmd + "\r").toByteArray(Charsets.US_ASCII))
        val done = withTimeoutOrNull(timeoutMs) {
            while (buf.indexOf(">") < 0) {
                val chunk = t.incoming.receiveCatching().getOrNull() ?: throw IOException("Connexion au boîtier perdue")
                buf.append(String(chunk, Charsets.US_ASCII))
            }
        }
        if (done == null) throw IOException("Le boîtier ne répond pas ($cmd)")
        val text = buf.substring(0, buf.indexOf(">"))
        return text.replace('\r', '\n').lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(cmd, ignoreCase = true) && !it.startsWith("SEARCHING") && !it.startsWith("BUS INIT") }
            .joinToString("\n")
    }

    fun isError(r: String): Boolean {
        val u = r.uppercase()
        return r.isBlank() || listOf(BLOCKED, "NO DATA", "UNABLE", "ERROR", "STOPPED", "?", "BUFFER FULL", "BUS BUSY", "NOT CONNECTED").any { u.contains(it) }
    }

    /** [protocol] : protocole connu du modèle (ex. « 6 » = CAN 11 bits 500 kb/s), sinon recherche automatique. */
    suspend fun init(protocol: String? = null) {
        val z = raw("ATZ", 6000)
        version = z.lines().lastOrNull { it.contains("ELM", true) || it.contains("v", true) }?.trim() ?: "ELM327"
        forcedProtocol = protocol
        // Requêtes OBD en adressage fonctionnel (7DF) : certains moteurs refusent le service 01 adressé directement.
        raw("ATE0"); raw("ATL0"); raw("ATS1"); raw("ATH0"); raw("ATAT1"); raw("ATST32"); raw("ATSP${protocol ?: "0"}")
    }

    /** Interroge le véhicule (0100). Renvoie true si au moins un calculateur répond (contact mis). */
    suspend fun probe(): Boolean {
        var r = raw("0100", 15000)
        var ok = !isError(r) && tokens(r).windowed(2).any { it[0] == "41" && it[1] == "00" }
        if (!ok && forcedProtocol != null && !r.contains(BLOCKED)) {
            // Protocole du modèle refusé (boîtier ou véhicule différent) : retour à la recherche automatique.
            forcedProtocol = null
            raw("ATSP0")
            r = raw("0100", 15000)
            ok = !isError(r) && tokens(r).windowed(2).any { it[0] == "41" && it[1] == "00" }
        }
        if (ok) {
            protocolNum = raw("ATDPN").trim().uppercase()
            protocolName = raw("ATDP").trim()
            // Réponse rapide, testée une fois : en CAN, la réponse à 0100 tient dans une trame.
            fast = false
            if (isCan) {
                val f = raw("01001", 3000)
                fast = !isError(f) && tokens(f).windowed(2).any { it[0] == "41" && it[1] == "00" }
            }
        }
        return ok
    }

    /** « 1 » final (réponse rapide) si la réponse du PID tient dans une trame CAN. */
    private fun quick(pid: Int): String = if (fast && (PID_LEN[pid] ?: 99) <= 5) "1" else ""

    private fun tokens(r: String): List<String> = r.split(Regex("\\s+")).filter { it.length == 2 && it.all { c -> c.isDigit() || c.uppercaseChar() in 'A'..'F' } }.map { it.uppercase() }

    /** Octets de données d'un PID du service 01 (premier calculateur répondant), ou null. */
    suspend fun pid01(pid: Int): IntArray? {
        val p = "%02X".format(pid)
        val r = raw("01$p" + quick(pid))
        if (isError(r)) return null
        for (line in r.lines()) {
            val tk = tokens(line)
            val i = tk.windowed(2).indexOfFirst { it[0] == "41" && it[1] == p }
            if (i >= 0) return tk.drop(i + 2).map { it.toInt(16) }.toIntArray()
        }
        return null
    }

    /** Requête libre (PIDs spécifiques du modèle). Renvoie les octets suivant l'écho service+PID. */
    suspend fun query(request: String, header: String? = null): IntArray? {
        if (header != null) raw("ATSH$header")
        try {
            val single01 = header == null && request.length == 4 && request.startsWith("01")
            val r = raw(request + if (single01) quick(request.substring(2).toInt(16)) else "")
            if (isError(r)) return null
            val req = request.chunked(2).map { it.uppercase() }
            val echo = listOf("%02X".format(req[0].toInt(16) + 0x40)) + req.drop(1)
            val tk = tokens(r)
            val i = tk.windowed(echo.size).indexOfFirst { it == echo }
            return if (i >= 0) tk.drop(i + echo.size).map { it.toInt(16) }.toIntArray() else null
        } finally {
            if (header != null) raw("ATSH7DF")
        }
    }

    /** PIDs du service 01 que le véhicule déclare supporter (union des calculateurs), ou null si injoignable. */
    suspend fun supportedPids(): Set<Int>? {
        val out = mutableSetOf<Int>()
        var base = 0
        while (base <= 0xC0) {
            val p = "%02X".format(base)
            val r = raw("01$p", 10000)
            if (isError(r)) return if (base == 0) null else out
            var got = false
            for (line in r.lines()) {
                val tk = tokens(line)
                val i = tk.windowed(2).indexOfFirst { it[0] == "41" && it[1] == p }
                if (i < 0 || tk.size < i + 6) continue
                got = true
                for (b in 0..3) {
                    val v = tk[i + 2 + b].toInt(16)
                    for (bit in 0..7) if (v and (0x80 shr bit) != 0) out += base + b * 8 + bit + 1
                }
            }
            if (!got) return if (base == 0) null else out
            if ((base + 0x20) !in out) break
            base += 0x20
        }
        return out
    }

    /**
     * Lit chaque requête une seule fois puis décode toutes les valeurs qui en dépendent.
     * Sans réponse rapide, les PIDs standard sont groupés par 6 dans une même requête (un seul délai d'attente).
     */
    suspend fun readAll(defs: List<com.cardeck.app.data.PidDef>): List<Pair<String, Double>> {
        val byReq = defs.groupBy { it.request to it.header }
        fun decode(group: List<com.cardeck.app.data.PidDef>, bytes: IntArray) =
            group.mapNotNull { p -> if (bytes.size < p.size) null else runCatching { p.decode(bytes) }.getOrNull()?.takeIf { !it.isNaN() }?.let { p.id to it } }
        val groupable = if (fast || !isCan) emptyList() else byReq.keys.filter { (req, hdr) ->
            hdr == null && req.length == 4 && req.startsWith("01") && req.substring(2).toIntOrNull(16)?.let { it in PID_LEN && it % 0x20 != 0 } == true
        }
        val out = mutableListOf<Pair<String, Double>>()
        for (chunk in groupable.chunked(6)) {
            val pids = chunk.map { it.first.substring(2).toInt(16) }
            val got = multi01(pids)
            for (key in chunk) {
                val bytes = got[key.first.substring(2).toInt(16)] ?: query(key.first, null) ?: continue
                out += decode(byReq.getValue(key), bytes)
            }
        }
        for ((key, group) in byReq) {
            if (key in groupable) continue
            val bytes = query(key.first, key.second) ?: continue
            out += decode(group, bytes)
        }
        return out
    }

    /** Plusieurs PIDs du service 01 en une requête (ex. 010C0B040E0F33) ; réponse « 41 PID données PID données… ». */
    private suspend fun multi01(pids: List<Int>): Map<Int, IntArray> {
        if (pids.size == 1) return emptyMap()
        val r = raw("01" + pids.joinToString("") { "%02X".format(it) })
        if (isError(r)) return emptyMap()
        val out = HashMap<Int, IntArray>()
        for (m in udsMessages(r)) {
            if (m.isEmpty() || m[0] != 0x41) continue
            var i = 1
            while (i < m.size) {
                val pid = m[i]
                val len = PID_LEN[pid] ?: break
                if (pid !in pids || i + 1 + len > m.size) break
                out[pid] = m.copyOfRange(i + 1, i + 1 + len)
                i += 1 + len
            }
        }
        return out
    }

    suspend fun rpm(): Double? = pid01(0x0C)?.takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 4.0 }

    suspend fun speed(): Int? = pid01(0x0D)?.firstOrNull()

    /** Tension mesurée par le boîtier sur la prise OBD (≈ tension batterie). */
    suspend fun voltage(): Double? = Regex("([0-9]+[.,][0-9]+)").find(raw("ATRV"))?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    /** Numéro d'identification du véhicule (service 09, PID 02). */
    suspend fun vin(): String? {
        val r = raw("0902", 10000)
        if (isError(r)) return null
        val tk = tokens(r)
        val out = StringBuilder()
        var i = 0
        while (i < tk.size) {
            if (i + 1 < tk.size && tk[i] == "49" && tk[i + 1] == "02") { i += 3; continue }
            val v = tk[i].toInt(16)
            if (v in 0x30..0x5A) out.append(v.toChar())
            i++
        }
        val vin = out.toString().filter { it.isLetterOrDigit() }
        return if (vin.length >= 17) vin.takeLast(17) else null
    }

    /** Lecture des codes défaut. [mode] = « 03 » (mémorisés), « 07 » (en attente) ou « 0A » (permanents). */
    suspend fun readDtcs(mode: String): Pair<List<FoundCode>, Set<String>> {
        val status = when (mode) { "07" -> DtcStatus.Pending; "0A" -> DtcStatus.Permanent; else -> DtcStatus.Stored }
        val resp = mode.toInt(16) + 0x40
        raw("ATH1")
        val r = try { raw(mode, 12000) } finally { raw("ATH0") }
        if (isError(r)) return emptyList<FoundCode>() to emptySet()
        val messages = if (isCan) canMessages(r) else legacyMessages(r)
        val codes = mutableListOf<FoundCode>()
        for ((hdr, payload) in messages) {
            if (payload.isEmpty() || payload[0] != resp) continue
            val data = if (isCan) payload.drop(2) else payload.drop(1) // en CAN, l'octet 2 = nombre de codes
            data.chunked(2).filter { it.size == 2 && (it[0] != 0 || it[1] != 0) }.forEach {
                codes += FoundCode(decodeDtc(it[0], it[1]), status, ecuName(hdr))
            }
        }
        return codes.distinctBy { it.code } to messages.map { it.first }.toSet()
    }

    /** Réassemblage ISO-TP des réponses CAN (en-têtes affichés via ATH1), regroupées par calculateur. */
    private fun canMessages(r: String): List<Pair<String, List<Int>>> {
        val single = LinkedHashMap<String, List<Int>>()
        val multi = LinkedHashMap<String, MutableList<Int>>()
        val expected = HashMap<String, Int>()
        for (line in r.lines()) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2) continue
            val (hdr, rest) = when {
                parts[0].length == 3 -> parts[0] to parts.drop(1)
                parts.size > 5 && parts.take(4).all { it.length == 2 } && (protocolNum.endsWith("7") || protocolNum.endsWith("9")) -> parts.take(4).joinToString("") to parts.drop(4)
                else -> continue
            }
            val b = rest.mapNotNull { it.toIntOrNull(16) }
            if (b.isEmpty()) continue
            when (b[0] shr 4) {
                0 -> single[hdr] = b.drop(1).take(b[0] and 0x0F)
                1 -> if (b.size >= 2) { expected[hdr] = ((b[0] and 0x0F) shl 8) + b[1]; multi[hdr] = b.drop(2).toMutableList() }
                2 -> multi[hdr]?.addAll(b.drop(1))
            }
        }
        return single.map { it.key to it.value } + multi.map { (h, v) -> h to v.take(expected[h] ?: v.size) }
    }

    /** Protocoles non CAN : en-tête de 3 octets, puis service + 3 codes par trame. */
    private fun legacyMessages(r: String): List<Pair<String, List<Int>>> = r.lines().mapNotNull { line ->
        val b = tokens(line).map { it.toInt(16) }
        if (b.size < 5) null else "%02X".format(b[2]) to b.drop(3).dropLast(1)
    }

    private fun ecuName(hdr: String): String = when (hdr.uppercase()) {
        "7E8", "18DAF110" -> "Moteur"
        "7E9", "18DAF118" -> "Boîte de vitesses"
        else -> "Calculateur $hdr"
    }

    private fun decodeDtc(a: Int, b: Int): String {
        val letter = "PCBU"[a shr 6]
        return "$letter${(a shr 4) and 3}${"%X".format(a and 0x0F)}${"%02X".format(b)}"
    }

    /** Données figées (service 02, trame 0) : code ayant déclenché la capture + valeurs principales. */
    suspend fun freezeFrame(): Pair<String, List<String>>? {
        val dtc = mode02(0x02)?.takeIf { it.size >= 2 && (it[0] != 0 || it[1] != 0) } ?: return null
        val code = decodeDtc(dtc[0], dtc[1])
        val rpm = mode02(0x0C)?.takeIf { it.size >= 2 }?.let { fr(((it[0] * 256 + it[1]) / 4.0)) + " tr/min" } ?: "—"
        val speed = mode02(0x0D)?.firstOrNull()?.let { "$it km/h" } ?: "—"
        val temp = mode02(0x05)?.firstOrNull()?.let { "${it - 40} °C" } ?: "—"
        val load = mode02(0x04)?.firstOrNull()?.let { fr(it * 100 / 255.0) + " %" } ?: "—"
        return code to listOf(rpm, speed, temp, load)
    }

    private suspend fun mode02(pid: Int): IntArray? {
        val p = "%02X".format(pid)
        val r = raw("02${p}00")
        if (isError(r)) return null
        val tk = tokens(r)
        val i = tk.windowed(2).indexOfFirst { it[0] == "42" && it[1] == p }
        return if (i >= 0) tk.drop(i + 3).map { it.toInt(16) }.toIntArray() else null
    }

    /** Effacement des codes défaut et du voyant moteur (service 04). */
    suspend fun clearDtcs(): Boolean = tokens(raw("04", 12000)).contains("44")

    // ---------------------------------------------------------------- UDS (codage)

    private fun hex(b: IntArray) = b.joinToString("") { "%02X".format(it) }

    /** Ouvre un dialogue avec un calculateur précis (en-tête d'émission + filtre de réception). */
    suspend fun udsOpen(tx: String, rx: String?) {
        raw("ATSH$tx")
        if (rx != null) raw("ATCRA$rx")
        raw("ATSTFF")
    }

    /** Revient au dialogue OBD2 standard. */
    suspend fun udsClose() {
        raw("ATCRA")
        raw("ATSH7DF")
        raw("ATST32")
        raw("ATCAF1")
    }

    /**
     * Découpe une réponse (en-têtes masqués) en messages : trames simples ou messages multi-trames
     * affichés par l'ELM327 sous la forme « 00E » puis « 0: … », « 1: … ».
     */
    private fun udsMessages(r: String): List<IntArray> {
        val out = mutableListOf<IntArray>()
        var multi: MutableList<Int>? = null
        var len = 0
        fun flush() { multi?.let { out += it.take(len).toIntArray() }; multi = null }
        for (line in r.lines().map { it.trim() }) {
            when {
                Regex("^[0-9A-Fa-f]{3}$").matches(line) -> { flush(); len = line.toInt(16); multi = mutableListOf() }
                Regex("^[0-9A-Fa-f]:.*").matches(line) && multi != null -> multi!!.addAll(tokens(line.substringAfter(':')).map { it.toInt(16) })
                else -> { flush(); tokens(line).map { it.toInt(16) }.takeIf { it.isNotEmpty() }?.let { out += it.toIntArray() } }
            }
        }
        flush()
        return out
    }

    /** Analyse la réponse à un service UDS : renvoie la réponse positive ou lève [UdsError]. */
    private fun udsAnswer(service: Int, messages: List<IntArray>): IntArray {
        val final = messages.lastOrNull { !(it.size >= 3 && it[0] == 0x7F && it[2] == 0x78) }
            ?: throw UdsError("Le module n'a pas donné de réponse définitive")
        if (final[0] == 0x7F) throw UdsError("Refus du module : " + com.cardeck.app.data.nrcLabel(final.getOrElse(2) { 0 }))
        if (final[0] != service + 0x40) throw UdsError("Réponse inattendue du module")
        return final
    }

    /** Envoie une requête UDS tenant dans une trame (≤ 7 octets). */
    suspend fun uds(request: IntArray): IntArray {
        val r = raw(hex(request), 6000)
        if (isError(r)) throw UdsError("Le module ne répond pas")
        return udsAnswer(request[0], udsMessages(r))
    }

    /** Passe le calculateur en session étendue (nécessaire pour l'écriture). */
    suspend fun extendedSession() { uds(intArrayOf(0x10, 0x03)) }

    /** Retour en session par défaut (erreurs ignorées). */
    suspend fun defaultSession() { runCatching { uds(intArrayOf(0x10, 0x01)) } }

    /** SecurityAccess : demande la graine du [level] puis envoie la clé calculée par [keyFn]. */
    suspend fun securityAccess(level: Int, keyFn: ((Int, IntArray) -> IntArray)?) {
        val seed = uds(intArrayOf(0x27, level)).drop(2).toIntArray()
        if (seed.all { it == 0 }) return // déjà déverrouillé
        val fn = keyFn ?: throw UdsError("Ce module exige un accès sécurisé dont l'algorithme n'est pas encore intégré")
        uds(intArrayOf(0x27, level + 1) + fn(level, seed))
    }

    /** ReadDataByIdentifier (0x22) : données du DID. */
    suspend fun readDid(did: Int): IntArray {
        val a = uds(intArrayOf(0x22, did shr 8, did and 0xFF))
        if (a.size < 3 || a[1] != did shr 8 || a[2] != did and 0xFF) throw UdsError("Réponse inattendue du module")
        return a.drop(3).toIntArray()
    }

    /**
     * WriteDataByIdentifier (0x2E). Au-delà de 4 octets de données, l'envoi est découpé
     * manuellement en trames ISO-TP (première trame, contrôle de flux, trames consécutives).
     */
    suspend fun writeDid(did: Int, data: IntArray) {
        val payload = intArrayOf(0x2E, did shr 8, did and 0xFF) + data
        if (payload.size <= 7) { uds(payload); return }
        if (payload.size > 0xFFF) throw UdsError("Bloc de données trop long")
        raw("ATCAF0")
        try {
            val first = intArrayOf(0x10 or (payload.size shr 8), payload.size and 0xFF) + payload.copyOfRange(0, 6)
            val fc = rawFrames(raw(hex(first), 3000))
            val flow = fc.firstOrNull { it.isNotEmpty() && it[0] shr 4 == 3 } ?: throw UdsError("Le module refuse l'écriture longue")
            if (flow[0] and 0x0F == 2) throw UdsError("Le module refuse l'écriture longue (dépassement)")
            val stMin = flow.getOrElse(2) { 0 }.let { if (it in 1..0x7F) it.toLong() else 0L }
            var seq = 1
            var i = 6
            var last = ""
            while (i < payload.size) {
                val end = minOf(i + 7, payload.size)
                val frame = (intArrayOf(0x20 or (seq and 0x0F)) + payload.copyOfRange(i, end)).copyOf(8)
                last = raw(hex(frame), if (end == payload.size) 6000 else 3000)
                if (stMin > 0) delay(stMin)
                seq++
                i = end
            }
            // Réponse finale (trames brutes : octet PCI puis données).
            val answers = rawFrames(last).mapNotNull { f -> if (f.isNotEmpty() && f[0] shr 4 == 0) f.drop(1).take(f[0] and 0x0F).toIntArray() else null }
            if (answers.isEmpty()) throw UdsError("Pas de confirmation d'écriture du module")
            udsAnswer(0x2E, answers)
        } finally {
            raw("ATCAF1")
        }
    }

    /** Liste blanche : commandes du boîtier sans effet durable ; en lecture seule, services de lecture uniquement. */
    private fun allowed(cmd: String): Boolean {
        val c = cmd.uppercase().replace(" ", "")
        if (c.startsWith("AT")) {
            val a = c.substring(2)
            if (listOf("PP", "WM", "SW", "BRD").any { a.startsWith(it) }) return false
            // Interface ISO 26021 de neutralisation des airbags : jamais interrogée.
            if (Regex("^(SH|FCSH)0*7F1$").matches(a) || Regex("^CRA0*7F9$").matches(a)) return false
            return true
        }
        if (!readOnly) return true
        return when (val svc = c.take(2)) {
            "10" -> c.startsWith("1001") || c.startsWith("1081")
            else -> svc in READ_SERVICES
        }
    }

    companion object {
        /** Réponse à une commande refusée par la liste blanche (jamais envoyée). */
        const val BLOCKED = "BLOCKED"

        private val READ_SERVICES = setOf("01", "02", "03", "05", "06", "07", "09", "0A", "12", "13", "17", "18", "1A", "21", "19", "22", "23", "24", "3E")

        /** Nombre d'octets de données des PIDs du service 01 (SAE J1979), pour découper les réponses groupées. */
        val PID_LEN: Map<Int, Int> = buildMap {
            val four = listOf(0x00, 0x01, 0x20, 0x40, 0x4F, 0x50) + (0x24..0x2B) + (0x34..0x3B)
            val two = listOf(0x02, 0x03, 0x0C, 0x10, 0x1F, 0x21, 0x22, 0x23, 0x31, 0x32, 0x42, 0x43, 0x44, 0x4D, 0x4E, 0x53, 0x54, 0x59, 0x5D, 0x5E) +
                (0x14..0x1B) + (0x3C..0x3F) + (0x55..0x58)
            for (p in 0x00..0x5F) put(p, 1)
            four.forEach { put(it, 4) }
            two.forEach { put(it, 2) }
        }
    }

    private fun rawFrames(r: String): List<IntArray> = if (isError(r)) emptyList() else r.lines().map { l -> tokens(l).map { it.toInt(16) }.toIntArray() }.filter { it.isNotEmpty() }
}
