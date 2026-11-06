package com.cardeck.app.explorer

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Explorateur : outils purs (sans Android) repris des scripts de rétro-ingénierie de la Swift,
 * rendus universels. Réassemblage ISO-TP vérifié, contrôle des réponses, décodage OBD,
 * décodage par corrélation, cartes moteur et rapport.
 */

/** État d'une réponse : ok, refus (réponse négative), attente (0x78 sans suite), rien, incomplet, erreur. */
enum class AnswerState { Ok, Refus, Attente, Rien, Incomplet, Erreur }

class Answer(val state: AnswerState, val payload: IntArray = IntArray(0)) {
    val ok get() = state == AnswerState.Ok
    /** Code de refus (NRC) d'une réponse négative 7F, sinon null. */
    val nrc: Int? get() = if (state == AnswerState.Refus && payload.size >= 3) payload[2] else null
}

/** Calculateur découvert. [key] = « 7E0 », « 18DA10F1 » (29 bits) ou « K10 » (ligne K). */
data class Ecu(val key: String, val bus: Bus, val tx: Int, val rx: Int, var proto: String) {
    val label get() = when (bus) {
        Bus.Can11 -> "%03X".format(tx)
        Bus.Can29 -> "%08X".format(tx)
        Bus.K -> "K %02X".format(tx)
    }
}

enum class Bus { Can11, Can29, K }

object Explore {
    private val FRAME11 = Regex("^([0-9A-F]{3}) ((?:[0-9A-F]{2} ?)+)$")
    private val FRAME29 = Regex("^([0-9A-F]{2} ?[0-9A-F]{2} ?[0-9A-F]{2} ?[0-9A-F]{2}) ((?:[0-9A-F]{2} ?)+)$")

    val ELM_ERRORS = listOf("CAN ERROR", "BUS ERROR", "BUS BUSY", "BUFFER FULL", "FB ERROR", "DATA ERROR", "RX ERROR", "LV RESET", "STOPPED", "UNABLE TO CONNECT")

    /** Octets d'identifiant que le calculateur répète dans sa réponse (contrôle des réponses tardives). */
    private val ECHO = mapOf(0x01 to 1, 0x02 to 1, 0x06 to 1, 0x09 to 1, 0x10 to 1, 0x19 to 1, 0x1A to 1, 0x21 to 1, 0x22 to 2, 0x24 to 2, 0x3E to 1)

    fun hex(b: IntArray) = b.joinToString("") { "%02X".format(it) }

    fun bytes(h: String): IntArray = h.replace(" ", "").chunked(2).mapNotNull { it.toIntOrNull(16) }.toIntArray()

    /**
     * Recolle les messages ISO-TP (en-têtes affichés). Vérifie le numéro de séquence de chaque trame de suite :
     * un message troué est écarté plutôt que recollé, car il ressemblerait à une donnée valide.
     * Renvoie les messages par identifiant et les messages incomplets (identifiant → raison).
     */
    fun reassemble(lines: List<String>, wide: Boolean = false): Pair<Map<Int, List<IntArray>>, Map<Int, String>> {
        val re = if (wide) FRAME29 else FRAME11
        val messages = LinkedHashMap<Int, MutableList<IntArray>>()
        val running = HashMap<Int, Triple<Int, MutableList<Int>, Int>>()
        val missed = HashMap<Int, String>()
        for (line in lines) {
            val m = re.matchEntire(line.trim().uppercase()) ?: continue
            val id = m.groupValues[1].replace(" ", "").toLong(16).toInt()
            val o = bytes(m.groupValues[2])
            if (o.isEmpty()) continue
            when (o[0] shr 4) {
                0 -> {
                    val n = o[0] and 0x0F
                    if (n in 1..o.size - 1) messages.getOrPut(id) { mutableListOf() } += o.copyOfRange(1, 1 + n)
                }
                1 -> if (o.size >= 2) {
                    running[id] = Triple(((o[0] and 0x0F) shl 8) or o[1], o.drop(2).toMutableList(), 1)
                    missed.remove(id)
                }
                2 -> {
                    val (want, data, seq) = running[id] ?: continue
                    if ((o[0] and 0x0F) != (seq and 0x0F)) {
                        running.remove(id)
                        missed[id] = "trame ${seq and 0x0F} manquante"
                        continue
                    }
                    data += o.drop(1)
                    if (data.size >= want) {
                        running.remove(id)
                        messages.getOrPut(id) { mutableListOf() } += data.take(want).toIntArray()
                    } else running[id] = Triple(want, data, seq + 1)
                }
            }
        }
        for ((id, r) in running) missed[id] = "${r.second.size}/${r.first} octets reçus"
        return messages to missed
    }

    /** Vrai si [msg] est bien la réponse à [req] (et pas une réponse tardive à la requête précédente). */
    fun correspond(msg: IntArray, req: IntArray): Boolean {
        if (msg.isEmpty() || req.isEmpty()) return false
        if (msg[0] == 0x7F) return msg.size >= 3 && msg[1] == req[0]
        if (msg[0] != (req[0] + 0x40) and 0xFF) return false
        var n = ECHO[req[0]] ?: 0
        if (req[0] == 0x01 && req.size > 2) n = 0 // requête groupée : ordre libre
        if (n > 0 && req.size >= 1 + n) return msg.size >= 1 + n && (1..n).all { msg[it] == req[it] }
        return true
    }

    fun pending(msg: IntArray) = msg.size >= 3 && msg[0] == 0x7F && msg[2] == 0x78

    /** État de la réponse du calculateur [rx] à [req]. */
    fun classify(messages: Map<Int, List<IntArray>>, rx: Int, req: IntArray): Answer {
        val list = messages[rx].orEmpty().filter { correspond(it, req) }
        val finals = list.filter { !pending(it) }
        if (finals.isNotEmpty()) {
            val m = finals.last()
            return Answer(if (m[0] == 0x7F) AnswerState.Refus else AnswerState.Ok, m)
        }
        return Answer(if (list.isNotEmpty()) AnswerState.Attente else AnswerState.Rien)
    }

    /** Ligne K (KWP2000, en-têtes affichés) : « 83 F1 40 5A 8A 31 cs » → (adresse source, données). */
    fun kDecode(line: String): Pair<Int, IntArray>? {
        val o = runCatching { bytes(line) }.getOrNull() ?: return null
        if (o.size < 4 || o[0] and 0x80 == 0) return null
        val (n, h) = if (o[0] and 0x3F != 0) (o[0] and 0x3F) to 3 else o[3] to 4
        if (n == 0 || o.size < h + n) return null
        return o[2] to o.copyOfRange(h, h + n)
    }

    // ------------------------------------------------------------------ PIDs OBD

    /** PID : nom, nombre d'octets, unité, formule. */
    class Pid(val name: String, val len: Int, val unit: String, val f: ((IntArray) -> Double)?)

    private fun u16(d: IntArray) = ((d[0] shl 8) or d[1]).toDouble()

    val PIDS: Map<Int, Pid> = mapOf(
        0x00 to Pid("supportes_01_20", 4, "", null),
        0x01 to Pid("etat_controles", 4, "", null),
        0x03 to Pid("etat_boucle_carburant", 2, "", { it[0].toDouble() }),
        0x04 to Pid("charge_calculee", 1, "%", { it[0] * 100.0 / 255 }),
        0x05 to Pid("temp_eau", 1, "°C", { it[0] - 40.0 }),
        0x06 to Pid("correction_court_terme", 1, "%", { (it[0] - 128) * 100.0 / 128 }),
        0x07 to Pid("correction_long_terme", 1, "%", { (it[0] - 128) * 100.0 / 128 }),
        0x0B to Pid("pression_admission", 1, "kPa", { it[0].toDouble() }),
        0x0C to Pid("regime", 2, "tr/min", { u16(it) / 4 }),
        0x0D to Pid("vitesse", 1, "km/h", { it[0].toDouble() }),
        0x0E to Pid("avance_allumage", 1, "°", { it[0] / 2.0 - 64 }),
        0x0F to Pid("temp_air_admission", 1, "°C", { it[0] - 40.0 }),
        0x10 to Pid("debit_air", 2, "g/s", { u16(it) / 100 }),
        0x11 to Pid("position_papillon", 1, "%", { it[0] * 100.0 / 255 }),
        0x13 to Pid("sondes_o2", 1, "", { it[0].toDouble() }),
        0x14 to Pid("o2_amont_tension", 2, "V", { it[0] / 200.0 }),
        0x15 to Pid("o2_aval_tension", 2, "V", { it[0] / 200.0 }),
        0x1C to Pid("norme_obd", 1, "", { it[0].toDouble() }),
        0x1F to Pid("temps_depuis_demarrage", 2, "s", { u16(it) }),
        0x20 to Pid("supportes_21_40", 4, "", null),
        0x21 to Pid("distance_avec_voyant", 2, "km", { u16(it) }),
        0x23 to Pid("pression_rail", 2, "kPa", { u16(it) * 10 }),
        0x2E to Pid("purge_canister", 1, "%", { it[0] * 100.0 / 255 }),
        0x2F to Pid("niveau_carburant", 1, "%", { it[0] * 100.0 / 255 }),
        0x30 to Pid("mises_en_temperature", 1, "", { it[0].toDouble() }),
        0x31 to Pid("distance_depuis_effacement", 2, "km", { u16(it) }),
        0x33 to Pid("pression_atmospherique", 1, "kPa", { it[0].toDouble() }),
        0x40 to Pid("supportes_41_60", 4, "", null),
        0x42 to Pid("tension_calculateur", 2, "V", { u16(it) / 1000 }),
        0x43 to Pid("charge_absolue", 2, "%", { u16(it) * 100 / 255 }),
        0x45 to Pid("papillon_relatif", 1, "%", { it[0] * 100.0 / 255 }),
        0x46 to Pid("temp_exterieure", 1, "°C", { it[0] - 40.0 }),
        0x47 to Pid("papillon_absolu_b", 1, "%", { it[0] * 100.0 / 255 }),
        0x49 to Pid("pedale_d", 1, "%", { it[0] * 100.0 / 255 }),
        0x4A to Pid("pedale_e", 1, "%", { it[0] * 100.0 / 255 }),
        0x4C to Pid("papillon_commande", 1, "%", { it[0] * 100.0 / 255 }),
        0x5C to Pid("temp_huile", 1, "°C", { it[0] - 40.0 }),
    )

    /** Repères relevés à chaque instantané, pour décoder les blocs constructeur par corrélation. */
    val REFERENCE_GROUPS = listOf(listOf(0x05, 0x0F, 0x0C, 0x42, 0x1F, 0x33), listOf(0x0D, 0x04, 0x31))

    /** Groupes enregistrés en roulant (chacun contient la charge, pour les cartes moteur). */
    val DRIVE_GROUPS = listOf(
        listOf(0x0C, 0x0B, 0x04, 0x0E, 0x0F, 0x33),
        listOf(0x0C, 0x04, 0x06, 0x07, 0x23, 0x14),
        listOf(0x0C, 0x04, 0x0D, 0x11, 0x49, 0x10),
    ) + REFERENCE_GROUPS

    /** Redécoupe une réponse du service 01, groupée ou non, dans n'importe quel ordre. */
    fun decodeMode01(payload: IntArray, wanted: Collection<Int>): Map<String, Double> {
        if (payload.isEmpty() || payload[0] != 0x41) return emptyMap()
        val out = LinkedHashMap<String, Double>()
        var i = 1
        while (i < payload.size) {
            val pid = payload[i]
            val len = PIDS[pid]?.len ?: com.cardeck.app.obd.Elm327.PID_LEN[pid] ?: break
            if (i + 1 + len > payload.size) break
            val p = PIDS[pid]
            if (pid in wanted && p?.f != null) runCatching { p.f.invoke(payload.copyOfRange(i + 1, i + 1 + len)) }.getOrNull()?.let { out[p.name] = it }
            i += 1 + len
        }
        return out
    }

    // ------------------------------------------------------------------ Analyse

    fun readable(b: IntArray) = b.joinToString("") { if (it in 32..126) it.toChar().toString() else "·" }

    /** Au moins 4 caractères imprimables et au moins 70 % de l'ensemble. */
    fun isText(b: IntArray): Boolean {
        if (b.size < 4) return false
        val n = b.count { it in 32..126 }
        return n >= 4 && n >= 0.7 * b.size
    }

    /** Octets d'écho en tête de réponse (service + identifiant répétés). */
    fun echoSize(req: String) = when {
        req.startsWith("22") || req.startsWith("24") -> 3
        req.take(2) in setOf("1A", "21", "41", "49", "46") -> 2
        else -> 1
    }

    /** Codes de refus : libellé, et true si « banal » (l'identifiant n'existe pas ici). */
    val REFUSALS = mapOf(
        0x10 to ("refus général" to false), 0x11 to ("service inconnu de ce calculateur" to true),
        0x12 to ("identifiant inconnu" to true), 0x13 to ("longueur de requête incorrecte" to true),
        0x14 to ("réponse trop longue" to false), 0x21 to ("calculateur occupé" to false),
        0x22 to ("conditions non remplies" to false), 0x31 to ("identifiant hors plage" to true),
        0x33 to ("accès sécurisé requis" to false), 0x78 to ("réponse en préparation" to false),
        0x7E to ("service indisponible dans cette session" to false), 0x7F to ("service indisponible dans cette session" to false),
    )

    /** Noms des identifiants UDS d'identification (ISO 14229). */
    val DID_NAMES = mapOf(
        0xF197 to "nom du système", 0xF187 to "référence pièce constructeur", 0xF188 to "référence logicielle",
        0xF189 to "version logicielle", 0xF18A to "code fournisseur", 0xF18B to "date de fabrication",
        0xF18C to "numéro de série", 0xF190 to "VIN", 0xF191 to "référence matérielle",
        0xF192 to "réf. matérielle fournisseur", 0xF193 to "version matérielle", 0xF194 to "réf. logicielle fournisseur",
        0xF195 to "version logicielle fournisseur", 0xF199 to "date de programmation", 0xF19E to "nom du fichier ODX",
    )

    val SYSTEM_WORDS = listOf("SDM", "BCM", "EPS", "ABS", "ESP", "ECM", "AIRBAG", "METER", "STEER", "BODY", "TCM", "IMMO", "HVAC", "CLUSTER", "PAS", "ENGINE", "GATEWAY", "RADIO", "PARK")

    /** Ordre du balayage UDS (ISO 14229-1) : les zones les plus peuplées d'abord ; F100-F1FF (identification) à part. */
    val UDS_ORDER: List<IntRange> = listOf(
        0xF400..0xF8FF, 0xFD00..0xFEFF, 0x0100..0x0FFF, 0xF000..0xF0FF, 0xF200..0xF3FF,
        0xF900..0xFCFF, 0xFF00..0xFFFF, 0x1000..0xEFFF, 0x0000..0x00FF,
    )
    val UDS_TOTAL = UDS_ORDER.sumOf { it.last - it.first + 1 }

    /** N-ième identifiant UDS du balayage ordonné. */
    fun udsAt(n: Int): Int {
        var k = n
        for (r in UDS_ORDER) {
            val size = r.last - r.first + 1
            if (k < size) return r.first + k
            k -= size
        }
        error("hors plage")
    }

    data class Clue(val key: String, val position: String, val reference: String, val r: Double, val formula: String, val n: Int, val time: Boolean)

    private fun pearson(xs: List<Double>, ys: List<Double>): Triple<Double, Double, Double> {
        val n = xs.size
        val mx = xs.sum() / n
        val my = ys.sum() / n
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until n) { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) * (xs[i] - mx); syy += (ys[i] - my) * (ys[i] - my) }
        if (sxx == 0.0 || syy == 0.0) return Triple(0.0, 0.0, 0.0)
        val a = sxy / sxx
        return Triple(sxy / sqrt(sxx * syy), a, my - a * mx)
    }

    /** Nombre à 4 chiffres significatifs, sans zéros inutiles (« 40 », « 0.3922 »). */
    private fun g4(x: Double): String =
        java.math.BigDecimal(x).round(java.math.MathContext(4)).stripTrailingZeros().toPlainString()

    fun formula(a: Double, b: Double, variable: String = "octet"): String {
        val ra = a.roundToInt()
        val aTxt = if (abs(a - ra) < 0.02 && ra != 0) (if (ra == 1) "" else if (ra == -1) "−" else "$ra × ") else "${g4(a)} × "
        if (abs(b) < 0.05) return "$aTxt$variable"
        return "$aTxt$variable ${if (b > 0) "+" else "−"} ${g4(abs(b))}"
    }

    /**
     * Décodage par corrélation : un octet (ou deux) d'un bloc constructeur qui suit exactement une grandeur OBD connue
     * d'un instantané à l'autre est très probablement elle ; la droite de régression donne la formule.
     */
    fun correlate(history: List<Snapshot>, minPoints: Int = 6, threshold: Double = 0.97): List<Clue> {
        if (history.size < minPoints) return emptyList()
        val refNames = history.flatMap { it.refs.keys }.toSortedSet()
        val keys = history.flatMap { it.blocks.keys }.toSortedSet()
        val clues = mutableListOf<Clue>()
        for (key in keys) {
            val req = key.substringAfter(':')
            val points = history.mapNotNull { h -> h.blocks[key]?.let { h.refs to bytes(it) } }
            if (points.size < minPoints) continue
            val len = points.minOf { it.second.size }
            val candidates = mutableListOf<Pair<String, List<Double>>>()
            for (i in echoSize(req) until len) {
                candidates += "octet $i" to points.map { it.second[i].toDouble() }
                if (i + 1 < len) candidates += "octets $i-${i + 1} (16 bits)" to points.map { ((it.second[i] shl 8) or it.second[i + 1]).toDouble() }
            }
            val best = HashMap<Pair<Int, String>, Clue>()
            for ((position, xs) in candidates) {
                if (xs.toSet().size < 3) continue
                val scores = HashMap<String, Pair<Triple<Double, Double, Double>, Int>>()
                for (ref in refNames) {
                    val pairs = xs.zip(points).mapNotNull { (x, p) -> p.first[ref]?.let { x to it } }
                    if (pairs.size < minPoints || pairs.map { it.second }.toSet().size < 3) continue
                    scores[ref] = pearson(pairs.map { it.first }, pairs.map { it.second }) to pairs.size
                }
                val reliable = scores.filterValues { abs(it.first.first) >= threshold }
                if (reliable.isEmpty()) continue
                val ref = reliable.maxBy { abs(it.value.first.first) }.key
                val (rab, n) = reliable.getValue(ref)
                val t = scores["temps_depuis_demarrage"]
                val timeToo = ref != "temps_depuis_demarrage" && t != null && abs(t.first.first) >= threshold
                val octet = position.split(" ")[1].split("-")[0].toInt()
                val k = octet to ref
                val prev = best[k]
                if (prev != null && abs(prev.r) >= abs(rab.first)) continue
                best[k] = Clue(key, position, ref, rab.first, "$ref ≈ ${formula(rab.second, rab.third)}", n, timeToo)
            }
            clues += best.values
        }
        return clues.sortedWith(compareBy<Clue> { it.time }.thenByDescending { abs(it.r) })
    }

    // ------------------------------------------------------------------ Cartes moteur (en roulant)

    private val RPM_BINS = (500..7000 step 250).toList()
    private val LOAD_BINS = (0..100 step 5).toList()
    private val MAP_BINS = (30..220 step 10).toList()

    /** (valeur, titre, unité, axe vertical, cases, nom de l'axe) */
    private val MAPS = listOf(
        listOf("avance_allumage", "Avance à l'allumage, par pression d'admission", "°", "pression_admission", "MAP kPa"),
        listOf("avance_allumage", "Avance à l'allumage, par charge", "°", "charge_calculee", "charge %"),
        listOf("pression_admission", "Pression d'admission (absolue)", "kPa", "charge_calculee", "charge %"),
        listOf("correction_court_terme", "Correction carburant court terme", "%", "charge_calculee", "charge %"),
        listOf("correction_long_terme", "Correction carburant long terme", "%", "charge_calculee", "charge %"),
        listOf("pression_rail", "Pression rail carburant", "kPa", "charge_calculee", "charge %"),
        listOf("debit_air", "Débit d'air massique", "g/s", "charge_calculee", "charge %"),
    )

    private fun cell(v: Double?, bins: List<Int>): Int? {
        if (v == null || v < bins.first() || v > bins.last()) return null
        for (i in 0 until bins.size - 1) if (v >= bins[i] && v < bins[i + 1]) return i
        return bins.size - 2
    }

    /**
     * Cartes effectivement appliquées par le moteur, reconstruites depuis les mesures en roulant :
     * moyenne de chaque grandeur par case (régime × charge ou pression), au moins [min] mesures par case.
     */
    fun engineMaps(rows: List<Map<String, Double>>, min: Int = 3): String {
        val sb = StringBuilder()
        for ((value, title, unit, axis, axisName) in MAPS) {
            val bins = if (axis == "pression_admission") MAP_BINS else LOAD_BINS
            val sums = HashMap<Pair<Int, Int>, Pair<Double, Int>>()
            for (r in rows) {
                val v = r[value] ?: continue
                val x = cell(r["regime"], RPM_BINS) ?: continue
                val y = cell(r[axis], bins) ?: continue
                val (s, n) = sums[x to y] ?: (0.0 to 0)
                sums[x to y] = (s + v) to (n + 1)
            }
            val cells = sums.filterValues { it.second >= min }
            if (cells.isEmpty()) continue
            val xs = cells.keys.map { it.first }.toSortedSet()
            val ys = cells.keys.map { it.second }.distinct().sortedDescending()
            sb.append("\n### $title ($unit)\n\n")
            sb.append("| $axisName \\ tr/min | ").append(xs.joinToString(" | ") { RPM_BINS[it].toString() }).append(" |\n")
            sb.append("|---|").append(xs.joinToString("") { "---|" }).append('\n')
            for (y in ys) {
                sb.append("| ").append(bins[y]).append(" | ")
                sb.append(xs.joinToString(" | ") { x -> cells[x to y]?.let { "%.1f".format(it.first / it.second) } ?: "" }).append(" |\n")
            }
        }
        return sb.toString()
    }
}

/** Instantané : grandeurs OBD de référence et valeurs des blocs constructeur au même moment. */
data class Snapshot(val t: String, val moment: String, val refs: Map<String, Double>, val blocks: Map<String, String>)
