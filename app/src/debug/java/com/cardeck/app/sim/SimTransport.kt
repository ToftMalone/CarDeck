package com.cardeck.app.sim

import com.cardeck.app.obd.ObdTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

/**
 * Boîtier ELM327 simulé (version de développement uniquement), branché sur la [SimCar].
 *
 * Il répond comme un vrai boîtier en CAN 11 bits 500 kb/s : commandes AT (écho, espaces, en-têtes, filtres, protocole,
 * tension), réponses sur une ou plusieurs trames ISO-TP (en-têtes affichés ou non), « NO DATA », écoute du bus (ATMA).
 * Calculateurs : moteur (7E0 → 7E8 : OBD, KWP 21 00 et 1A 90, codes KWP) et ABS (7B0 → 7B8 : UDS).
 * [latency] : délais réalistes (désactivés dans les tests).
 */
class SimTransport(private val car: SimCar = SimCar(), private val latency: Boolean = true) : ObdTransport {
    override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val line = StringBuilder()
    @Volatile private var closed = false
    private var monitor: Job? = null

    // État du boîtier
    private var echo = true
    private var spaces = true
    private var headers = false
    private var tx = 0x7DF
    private var cra: Int? = null
    private var proto = "0"
    private var cf: Int? = null
    private var cm: Int? = null

    override suspend fun open() {
        if (latency) delay(400)
    }

    override suspend fun send(data: ByteArray) {
        if (closed) throw IOException("Simulateur arrêté")
        // Écoute du bus en cours : n'importe quel caractère l'arrête.
        monitor?.let { m ->
            m.cancel()
            monitor = null
            emit("\r>", 5)
            return
        }
        for (b in data) {
            val ch = (b.toInt() and 0xFF).toChar()
            when (ch) {
                '\r' -> { val cmd = line.toString(); line.clear(); handle(cmd) }
                '\n' -> {}
                else -> line.append(ch)
            }
        }
    }

    override fun close() {
        closed = true
        monitor?.cancel()
        scope.cancel()
        incoming.close()
    }

    private fun emit(text: String, ms: Long) {
        if (!latency) { incoming.trySend(text.toByteArray(Charsets.US_ASCII)); return }
        scope.launch {
            delay(ms)
            incoming.trySend(text.toByteArray(Charsets.US_ASCII))
        }
    }

    private fun reset() {
        echo = true; spaces = true; headers = false; tx = 0x7DF; cra = null; proto = "0"; cf = null; cm = null
    }

    private fun handle(raw: String) {
        val cmd = raw.trim().uppercase(Locale.US).replace(" ", "")
        val prefix = if (echo) raw.trim() + "\r" else ""
        if (cmd.isEmpty()) { emit("$prefix>", 2); return }
        if (cmd.startsWith("AT")) {
            val r = at(cmd.substring(2), prefix) ?: return
            emit(prefix + r + "\r\r>", 8)
            return
        }
        var hex = cmd
        // Chiffre final = nombre de réponses attendues (réponse rapide) : ignoré.
        if (hex.length % 2 == 1 && hex.last().isDigit()) hex = hex.dropLast(1)
        if (hex.isEmpty() || !hex.all { it.isDigit() || it in 'A'..'F' }) { emit("$prefix?\r\r>", 5); return }
        if (proto !in setOf("0", "6", "A6")) { emit("${prefix}NO DATA\r\r>", 60); return }
        val req = hex.chunked(2).map { it.toInt(16) }.toIntArray()
        val answers = ecusFor(tx).mapNotNull { ecu -> ecu.answer(req, tx == 0x7DF)?.let { ecu.rx to it } }.filter { cra == null || it.first == cra }
        if (answers.isEmpty()) emit("${prefix}NO DATA\r\r>", 35)
        else emit(prefix + answers.joinToString("") { (rx, p) -> format(rx, p) } + "\r>", 30L + 12L * answers.size + req.size)
    }

    /** Commande AT (sans « AT ») ; null pour l'écoute du bus (qui répond en continu). */
    private fun at(a: String, prefix: String): String? = when {
        a == "Z" -> { reset(); "\r\rELM327 v1.5" }
        a == "WS" || a == "D" -> { reset(); "OK" }
        a == "I" -> "ELM327 v1.5"
        a == "@1" -> "CarDeck Simulator"
        a == "DPN" -> "A6"
        a == "DP" -> "AUTO, ISO 15765-4 (CAN 11/500)"
        a == "RV" -> "%.1fV".format(Locale.US, car.state().volts)
        a == "FI" || a == "SI" -> "BUS INIT: ...ERROR"
        a == "MA" -> { startMonitor(prefix); null }
        a.length == 2 && a[0] == 'E' -> { echo = a[1] == '1'; "OK" }
        a.length == 2 && a[0] == 'S' && a[1] in "01" -> { spaces = a[1] == '1'; "OK" }
        a.length == 2 && a[0] == 'H' -> { headers = a[1] == '1'; "OK" }
        a.startsWith("SP") || a.startsWith("TP") -> { proto = a.substring(2).ifEmpty { "0" }; "OK" }
        a.startsWith("SH") -> { tx = a.substring(2).toIntOrNull(16) ?: tx; "OK" }
        a == "CRA" -> { cra = null; "OK" }
        a.startsWith("CRA") -> { cra = a.substring(3).toIntOrNull(16); "OK" }
        a.startsWith("CF") -> { cf = a.substring(2).toIntOrNull(16); "OK" }
        a.startsWith("CM") -> { cm = a.substring(2).toIntOrNull(16); "OK" }
        else -> "OK"
    }

    // ---------------------------------------------------------------- Mise en forme ELM327

    private fun hx(b: Int) = "%02X".format(b and 0xFF)

    /** Une réponse de [rx] : trame simple, ou première trame + trames consécutives (ISO-TP), comme les affiche l'ELM327. */
    private fun format(rx: Int, p: IntArray): String {
        val sep = if (spaces) " " else ""
        fun join(b: List<Int>) = b.joinToString(sep) { hx(it) }
        val hdr = "%03X".format(rx)
        val sb = StringBuilder()
        if (p.size <= 7) {
            if (headers) sb.append(hdr).append(sep).append(join(listOf(p.size) + p.toList())) else sb.append(join(p.toList()))
            return sb.append("\r").toString()
        }
        val frames = mutableListOf<List<Int>>()
        frames += listOf(0x10 or (p.size shr 8), p.size and 0xFF) + p.take(6)
        var i = 6
        var seq = 1
        while (i < p.size) {
            frames += listOf(0x20 or (seq and 0x0F)) + p.toList().subList(i, minOf(i + 7, p.size))
            i += 7; seq++
        }
        if (headers) frames.forEach { f -> sb.append(hdr).append(sep).append(join(f)).append("\r") }
        else {
            sb.append("%03X".format(p.size)).append("\r")
            frames.forEachIndexed { n, f -> sb.append("%X:".format(n and 0x0F)).append(sep).append(join(f.drop(if (n == 0) 2 else 1))).append("\r") }
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- Écoute du bus (ATMA)

    private fun startMonitor(prefix: String) {
        if (prefix.isNotEmpty()) emit(prefix, 0)
        monitor = scope.launch {
            var n = 0
            while (isActive) {
                delay(25)
                n++
                val st = car.state()
                val frames = mutableListOf<Pair<Int, IntArray>>()
                if (n % 4 == 0) {
                    frames += 0x3B9 to intArrayOf(0, 0, 0, 0, SimCar.byte(st.fuel / 100 * 230), SimCar.byte(st.fuel / 100 * 240), 0, 0)
                    frames += 0x3BA to intArrayOf(SimCar.byte((18 + 40) * 2.0), 0, 0, 0, 0, 0, 0, 0)
                }
                if (n % 40 == 0) {
                    val odo = (23_456 + st.km).toInt()
                    frames += 0x460 to intArrayOf((odo shr 16) and 0xFF, (odo shr 8) and 0xFF, odo and 0xFF, 0, 0, 0, 0, 0)
                }
                val shown = frames.filter { (id, _) -> cf == null || (id and (cm ?: 0x7FF)) == (cf!! and (cm ?: 0x7FF)) }
                if (shown.isEmpty()) continue
                val sep = if (spaces) " " else ""
                val text = shown.joinToString("") { (id, d) -> (if (headers) "%03X$sep".format(id) else "") + d.joinToString(sep) { hx(it) } + "\r" }
                incoming.trySend(text.toByteArray(Charsets.US_ASCII))
            }
        }
    }

    // ---------------------------------------------------------------- Calculateurs

    private abstract inner class Ecu(val rx: Int) {
        /** Réponse (positive ou refus 7F) à [req] ; null = pas de réponse. [broadcast] : demande diffusée (7DF). */
        abstract fun answer(req: IntArray, broadcast: Boolean): IntArray?

        fun neg(sid: Int, nrc: Int) = intArrayOf(0x7F, sid, nrc)
    }

    private val engine = Engine()
    private val abs = Abs()

    private fun ecusFor(tx: Int): List<Ecu> = when (tx) {
        0x7DF, 0x7E0 -> listOf(engine)
        0x7B0 -> listOf(abs)
        else -> emptyList()
    }

    private fun text(s: String) = s.map { it.code }.toIntArray()

    private inner class Engine : Ecu(0x7E8) {
        private val supported = setOf(
            0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x1C, 0x1F, 0x20,
            0x21, 0x2F, 0x30, 0x31, 0x33, 0x40, 0x42, 0x46, 0x5C,
        )

        override fun answer(req: IntArray, broadcast: Boolean): IntArray? {
            val sid = req[0]
            // En diffusion, le moteur ne répond qu'aux services OBD.
            if (broadcast && sid > 0x0A) return null
            return when (sid) {
                0x01 -> mode01(req.drop(1))
                0x02 -> mode02(req)
                0x03 -> dtcs(0x43, car.stored)
                0x07 -> dtcs(0x47, car.pending)
                0x0A -> dtcs(0x4A, car.permanentCodes())
                0x04 -> { car.clearObd(); intArrayOf(0x44) }
                0x09 -> when (req.getOrNull(1)) {
                    0x00 -> intArrayOf(0x49, 0x00, 0x54, 0x40, 0x00, 0x00)
                    0x02 -> intArrayOf(0x49, 0x02, 0x01) + text(car.vin)
                    else -> null
                }
                0x3E -> intArrayOf(0x7E, 0x00)
                0x10 -> when (req.getOrNull(1)) {
                    0x81 -> intArrayOf(0x50, 0x81)
                    0x01 -> intArrayOf(0x50, 0x01, 0x00, 0x32, 0x01, 0xF4)
                    else -> neg(0x10, 0x12)
                }
                0x1A -> if (req.getOrNull(1) == 0x90) intArrayOf(0x5A, 0x90) + text(car.vin) else neg(0x1A, 0x12)
                0x21 -> when (req.getOrNull(1)) {
                    0x00 -> block2100()
                    // Bloc d'étalonnage figé, comme la plupart des blocs de la vraie Swift Sport (que des FF).
                    0x20 -> intArrayOf(0x61, 0x20) + IntArray(64) { if (it < 4) 0x06 + it else 0xFF }
                    else -> neg(0x21, 0x12)
                }
                0x18 -> {
                    val codes = car.stored.map { it to 0x60 } + car.pending.map { it to 0x20 }
                    intArrayOf(0x58, codes.size) + codes.flatMap { (c, st) -> SimCar.dtcBytes(c).toList() + st }.toIntArray()
                }
                0x14 -> if (req.size == 3) { car.clearObd(); intArrayOf(0x54, 0xFF, 0x00) } else neg(0x14, 0x13)
                else -> neg(sid, 0x11)
            }
        }

        private fun bitmap(base: Int): IntArray = IntArray(4) { b ->
            var v = 0
            for (bit in 0..7) if ((base + b * 8 + bit + 1) in supported) v = v or (0x80 shr bit)
            v
        }

        private fun pid(p: Int, st: SimCar.State): IntArray? = when (p) {
            0x00, 0x20, 0x40 -> bitmap(p)
            0x01 -> car.readiness()
            0x03 -> intArrayOf(0x02, 0x00)
            0x04 -> intArrayOf(SimCar.byte(st.load * 2.55))
            0x05 -> intArrayOf(SimCar.byte(st.coolant + 40))
            0x06 -> intArrayOf(SimCar.byte(128 + kotlin.math.sin(st.runtime / 3.0) * 4))
            0x07 -> intArrayOf(if (car.pending.contains("P0171")) 138 else 129)
            0x0B -> intArrayOf(SimCar.byte(st.map))
            0x0C -> SimCar.u16(st.rpm * 4)
            0x0D -> intArrayOf(SimCar.byte(st.speed))
            0x0E -> intArrayOf(SimCar.byte((st.advance + 64) * 2))
            0x0F -> intArrayOf(SimCar.byte(st.intake + 40))
            0x10 -> SimCar.u16(st.maf * 100)
            0x11 -> intArrayOf(SimCar.byte(st.throttle * 2.55))
            0x1C -> intArrayOf(0x06)
            0x1F -> SimCar.u16(st.runtime.toDouble())
            0x21 -> SimCar.u16(if (car.mil) 37 + car.kmSinceClear.coerceAtLeast(0.0) else 0.0)
            0x2F -> intArrayOf(SimCar.byte(st.fuel * 2.55))
            0x30 -> intArrayOf(car.warmupsSinceClear.coerceIn(0, 255))
            0x31 -> SimCar.u16(car.kmSinceClear.coerceAtLeast(0.0))
            0x33 -> intArrayOf(101)
            0x42 -> SimCar.u16(st.volts * 1000)
            0x46 -> intArrayOf(18 + 40)
            0x5C -> intArrayOf(SimCar.byte(st.oil + 40))
            else -> null
        }

        /** Service 01, un ou plusieurs PIDs (jusqu'à 6) : « 41 pid données pid données… ». */
        private fun mode01(pids: List<Int>): IntArray? {
            if (pids.isEmpty()) return null
            val st = car.state()
            val out = mutableListOf(0x41)
            for (p in pids) pid(p, st)?.let { out += p; out += it.toList() }
            return if (out.size == 1) null else out.toIntArray()
        }

        /** Données figées (trame 00) du défaut qui a allumé le voyant. */
        private fun mode02(req: IntArray): IntArray? {
            val p = req.getOrNull(1) ?: return null
            val frame = req.getOrNull(2) ?: 0
            val code = car.stored.firstOrNull() ?: return intArrayOf(0x42, p, frame, 0x00, 0x00).takeIf { p == 0x02 }
            val data = when (p) {
                0x02 -> SimCar.dtcBytes(code)
                0x0C -> SimCar.u16(2450.0 * 4)
                0x0D -> intArrayOf(87)
                0x05 -> intArrayOf(88 + 40)
                0x04 -> intArrayOf(SimCar.byte(41 * 2.55))
                else -> return null
            }
            return intArrayOf(0x42, p, frame) + data
        }

        private fun dtcs(resp: Int, codes: List<String>) = intArrayOf(resp, codes.size) + codes.flatMap { SimCar.dtcBytes(it).toList() }.toIntArray()

        /**
         * Bloc constructeur KWP 21 00 (210 octets) : la plupart figés, quelques-uns vivants. Octet 8 = huile − 40 (comme la
         * vraie Swift), octets 40 à 43 = retard d'allumage dû au cliquetis par cylindre (degrés × 2).
         */
        private fun block2100(): IntArray {
            val st = car.state()
            val body = IntArray(210) { i -> (i * 37 + 11) and 0xFF }
            body[0] = SimCar.byte(st.rpm / 32)
            body[2] = SimCar.byte(st.speed)
            body[3] = SimCar.byte(st.coolant + 40)
            body[5] = SimCar.byte(st.load * 2.55)
            body[8] = SimCar.byte(st.oil + 40)
            body[12] = SimCar.byte(st.intake + 40)
            for (c in 0..3) body[40 + c] = st.knock[c]
            return intArrayOf(0x61, 0x00) + body
        }
    }

    private inner class Abs : Ecu(0x7B8) {
        override fun answer(req: IntArray, broadcast: Boolean): IntArray? {
            val sid = req[0]
            return when (sid) {
                0x3E -> intArrayOf(0x7E, 0x00)
                0x10 -> if (req.getOrNull(1) == 0x01) intArrayOf(0x50, 0x01, 0x00, 0x32, 0x01, 0xF4) else neg(0x10, 0x12)
                0x19 -> if (req.getOrNull(1) == 0x02) {
                    intArrayOf(0x59, 0x02, 0xFF) + car.absCodes.flatMap { SimCar.dtcBytes(it).toList() + listOf(0x00, 0x2F) }.toIntArray()
                } else neg(0x19, 0x12)
                0x14 -> if (req.size == 4) { car.clearAbs(); intArrayOf(0x54) } else neg(0x14, 0x13)
                0x22 -> when ((req.getOrNull(1) ?: 0) * 256 + (req.getOrNull(2) ?: 0)) {
                    0xF190 -> intArrayOf(0x62, 0xF1, 0x90) + text(car.vin)
                    0xF187 -> intArrayOf(0x62, 0xF1, 0x87) + text("SIM-ABS-0001")
                    0xF197 -> intArrayOf(0x62, 0xF1, 0x97) + text("ABS / ESP")
                    else -> neg(0x22, 0x31)
                }
                else -> neg(sid, 0x11)
            }
        }
    }
}
