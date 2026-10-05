package com.cardeck.app.obd

import com.cardeck.app.data.DtcStatus
import com.cardeck.app.data.FoundCode
import com.cardeck.app.data.fr
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

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

    private val isCan get() = protocolNum.lastOrNull()?.let { it in '6'..'9' || it in 'A'..'C' } ?: true

    /** Envoie une commande et renvoie la réponse nettoyée (lignes non vides, sans écho ni « SEARCHING… »). */
    suspend fun raw(cmd: String, timeoutMs: Long = 8000): String {
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
        return r.isBlank() || listOf("NO DATA", "UNABLE", "ERROR", "STOPPED", "?", "BUFFER FULL", "BUS BUSY", "NOT CONNECTED").any { u.contains(it) }
    }

    suspend fun init() {
        val z = raw("ATZ", 6000)
        version = z.lines().lastOrNull { it.contains("ELM", true) || it.contains("v", true) }?.trim() ?: "ELM327"
        raw("ATE0"); raw("ATL0"); raw("ATS1"); raw("ATH0"); raw("ATAT1"); raw("ATSP0")
    }

    /** Interroge le véhicule (0100). Renvoie true si au moins un calculateur répond (contact mis). */
    suspend fun probe(): Boolean {
        val r = raw("0100", 15000)
        val ok = !isError(r) && tokens(r).windowed(2).any { it[0] == "41" && it[1] == "00" }
        if (ok) {
            protocolNum = raw("ATDPN").trim().uppercase()
            protocolName = raw("ATDP").trim()
        }
        return ok
    }

    private fun tokens(r: String): List<String> = r.split(Regex("\\s+")).filter { it.length == 2 && it.all { c -> c.isDigit() || c.uppercaseChar() in 'A'..'F' } }.map { it.uppercase() }

    /** Octets de données d'un PID du service 01 (premier calculateur répondant), ou null. */
    suspend fun pid01(pid: Int): IntArray? {
        val p = "%02X".format(pid)
        val r = raw("01$p")
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
            val r = raw(request)
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
}
