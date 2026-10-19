package com.cardeck.app.sim

import com.cardeck.app.obd.ObdTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Boîtier ELM327 simulé : répond aux commandes AT et aux requêtes OBD2 / UDS
 * comme un vrai boîtier branché sur la voiture virtuelle [SimCar].
 */
class SimTransport : ObdTransport {
    override val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var headers = false
    private var caf = true
    private var tx = "7DF"
    private var session = 1

    override suspend fun open() { delay(400) }

    override suspend fun send(data: ByteArray) {
        val cmd = String(data, Charsets.US_ASCII).trim().uppercase().replace(" ", "")
        delay(if (cmd.startsWith("AT")) 5 else 25)
        incoming.trySend((respond(cmd) + "\r\r>").toByteArray(Charsets.US_ASCII))
    }

    override fun close() { incoming.close() }

    private fun respond(cmd: String): String {
        if (cmd.startsWith("AT")) return at(cmd.substring(2))
        if (!caf) return "NO DATA"
        // Un chiffre final impair = nombre de réponses attendues (réponse rapide) : ignoré.
        val req = (if (cmd.length % 2 == 1) cmd.dropLast(1) else cmd).chunked(2).mapNotNull { it.toIntOrNull(16) }
        if (req.isEmpty()) return "?"
        val (ecu, payload) = when (tx) {
            "7DF", "7E0" -> "7E8" to ecm(req)
            "750" -> "758" to bcm(req)
            else -> return "NO DATA"
        }
        return payload?.let { frame(ecu, it) } ?: "NO DATA"
    }

    private fun at(a: String): String = when {
        a == "Z" || a == "I" -> "ELM327 v1.5 (simulé)"
        a == "H1" -> { headers = true; "OK" }
        a == "H0" -> { headers = false; "OK" }
        a == "CAF0" -> { caf = false; "OK" }
        a == "CAF1" -> { caf = true; "OK" }
        a.startsWith("SH") -> { tx = a.substring(2); "OK" }
        a == "RV" -> "%.1fV".format(java.util.Locale.US, SimCar.state().battery)
        a == "DPN" -> "A6"
        a == "DP" -> "AUTO, ISO 15765-4 (CAN 11/500)"
        else -> "OK"
    }

    /** Mise en forme comme un ELM327 (trame simple ou multi-trames, avec ou sans en-têtes). */
    private fun frame(ecu: String, p: IntArray): String {
        fun hex(l: List<Int>) = l.joinToString(" ") { "%02X".format(it) }
        if (p.size <= 7) return if (headers) "$ecu %02X ${hex(p.toList())}".format(p.size) else hex(p.toList())
        val lines = mutableListOf<String>()
        val first = p.take(6)
        val rest = p.drop(6).chunked(7)
        if (headers) {
            lines += "$ecu 1%01X %02X ${hex(first)}".format(p.size shr 8, p.size and 0xFF)
            rest.forEachIndexed { i, c -> lines += "$ecu %02X ${hex(c)}".format(0x20 or ((i + 1) and 0x0F)) }
        } else {
            lines += "%03X".format(p.size)
            lines += "0: ${hex(first)}"
            rest.forEachIndexed { i, c -> lines += "%X: ${hex(c)}".format((i + 1) and 0x0F) }
        }
        return lines.joinToString("\r")
    }

    // ---------------------------------------------------------------- Calculateur moteur

    private val supported = setOf(
        0x01, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x14, 0x15, 0x1F,
        0x21, 0x2F, 0x31, 0x33, 0x3C, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x49, 0x4A, 0x4C, 0x5C, 0xA6,
    )

    private fun bitmap(base: Int): IntArray {
        val bits = IntArray(4)
        for (pid in supported + (if (supported.any { it > base + 0x20 }) setOf(base + 0x20) else emptySet())) {
            val n = pid - base - 1
            if (n in 0..31) bits[n / 8] = bits[n / 8] or (0x80 shr (n % 8))
        }
        return bits
    }

    private fun u16(v: Double) = v.roundToInt().coerceIn(0, 65535).let { intArrayOf(it shr 8, it and 0xFF) }
    private fun b(v: Double) = intArrayOf(SimCar.clampByte(v))

    private fun pidValue(pid: Int, st: SimCar.State): IntArray? = when (pid) {
        0x01 -> intArrayOf((if (SimCar.dtcs.isNotEmpty()) 0x80 else 0) or SimCar.dtcs.size, 0x07, 0x65, 0x00)
        0x04 -> b(st.load * 2.55)
        0x05 -> b(st.coolant + 40)
        0x06 -> b(128 + (if (st.engineOn) kotlin.math.sin(System.currentTimeMillis() / 700.0) * 4 else 0.0) * 1.28)
        0x07 -> b(128 + 2.3 * 1.28)
        0x0B -> b(st.map)
        0x0C -> u16(st.rpm * 4)
        0x0D -> b(st.speedKmh)
        0x0E -> b((if (st.engineOn) 12 + st.rpm / 400 else 0.0) * 2 + 128)
        0x0F -> b(st.iat + 40)
        0x10 -> u16(st.maf * 100)
        0x11 -> b(st.throttle * 2.55)
        0x14 -> intArrayOf(SimCar.clampByte(if (st.engineOn) (0.45 + 0.4 * kotlin.math.sin(System.currentTimeMillis() / 300.0)) * 200 else 0.0), SimCar.clampByte(128 + 1.5 * 1.28))
        0x15 -> intArrayOf(SimCar.clampByte(if (st.engineOn) 0.68 * 200 else 0.0), 0xFF)
        0x1F -> u16(st.runSeconds.toDouble())
        0x21 -> u16(0.0)
        0x2F -> b(st.fuelPct * 2.55)
        0x31 -> u16(st.distanceSinceClearKm)
        0x33 -> b(100.0)
        0x3C -> u16((st.catalyst + 40) * 10)
        0x42 -> u16(st.battery * 1000)
        0x43 -> u16(st.load * 2.55 * 0.9)
        0x44 -> u16(1.0 / 2 * 65536)
        0x45 -> b(st.throttle * 2.55 * 0.9)
        0x46 -> b(st.ambient + 40)
        0x47 -> b(st.throttle * 2.55 * 0.5 + 20)
        0x49 -> b(st.throttle * 2.55 * 0.8 + 35)
        0x4A -> b(st.throttle * 2.55 * 0.4 + 17)
        0x4C -> b(st.throttle * 2.55)
        0x5C -> b(st.oil + 40)
        0xA6 -> (st.odometerKm * 10).toLong().let { intArrayOf((it shr 24 and 0xFF).toInt(), (it shr 16 and 0xFF).toInt(), (it shr 8 and 0xFF).toInt(), (it and 0xFF).toInt()) }
        else -> null
    }

    private fun dtcBytes(code: String): List<Int> {
        val letter = "PCBU".indexOf(code[0])
        val a = (letter shl 6) or (code[1].digitToInt() shl 4) or code[2].digitToInt(16)
        return listOf(a, code.substring(3, 5).toInt(16))
    }

    private fun ecm(req: List<Int>): IntArray? {
        val st = SimCar.state()
        return when (req[0]) {
            0x01 -> {
                val pid = req.getOrNull(1) ?: return null
                if (req.size > 2) {
                    // Plusieurs PIDs dans une requête : « 41 PID données PID données… ».
                    val parts = req.drop(1).filter { it % 0x20 != 0 && it in supported }.mapNotNull { p -> pidValue(p, st)?.let { listOf(p) + it.toList() } }
                    return if (parts.isEmpty()) null else (listOf(0x41) + parts.flatten()).toIntArray()
                }
                if (pid % 0x20 == 0) intArrayOf(0x41, pid) + bitmap(pid)
                else if (pid in supported) pidValue(pid, st)?.let { intArrayOf(0x41, pid) + it } else null
            }
            0x02 -> {
                val pid = req.getOrNull(1) ?: return null
                val (code, ff) = SimCar.freeze ?: return null
                val data = if (pid == 0x02) dtcBytes(code).toIntArray() else pidValue(pid, ff) ?: return null
                intArrayOf(0x42, pid, 0x00) + data
            }
            0x03 -> (listOf(0x43, SimCar.dtcs.size) + SimCar.dtcs.flatMap { dtcBytes(it) }).toIntArray()
            0x07, 0x0A -> intArrayOf(req[0] + 0x40, 0x00)
            0x04 -> { SimCar.clearFaults(); intArrayOf(0x44) }
            0x09 -> if (req.getOrNull(1) == 0x02) (listOf(0x49, 0x02, 0x01) + "CARDECKSIM0000001".map { it.code }).toIntArray() else null
            0x10 -> { session = req.getOrElse(1) { 1 }; intArrayOf(0x50, session, 0x00, 0x32, 0x01, 0xF4) }
            0x22 -> {
                val did = (req.getOrElse(1) { 0 } shl 8) or req.getOrElse(2) { 0 }
                // PID constructeur simulé : pression de suralimentation (mbar absolus).
                if (did == 0x1940) intArrayOf(0x62, 0x19, 0x40) + u16(st.map * 10) else intArrayOf(0x7F, 0x22, 0x31)
            }
            0x3E -> intArrayOf(0x7E, 0x00)
            else -> intArrayOf(0x7F, req[0], 0x11)
        }
    }

    // ---------------------------------------------------------------- Boîtier de servitude (codage)

    private fun bcm(req: List<Int>): IntArray? = when (req[0]) {
        0x10 -> { session = req.getOrElse(1) { 1 }; intArrayOf(0x50, session, 0x00, 0x32, 0x01, 0xF4) }
        0x27 -> if (req.getOrElse(1) { 1 } % 2 == 1) intArrayOf(0x67, req[1], 0x00, 0x00) else intArrayOf(0x67, req[1])
        0x22 -> {
            val did = (req.getOrElse(1) { 0 } shl 8) or req.getOrElse(2) { 0 }
            SimCar.coding[did]?.let { intArrayOf(0x62, req[1], req[2]) + it } ?: intArrayOf(0x7F, 0x22, 0x31)
        }
        0x2E -> {
            val did = (req.getOrElse(1) { 0 } shl 8) or req.getOrElse(2) { 0 }
            when {
                session != 3 -> intArrayOf(0x7F, 0x2E, 0x7F)
                did !in SimCar.coding -> intArrayOf(0x7F, 0x2E, 0x31)
                else -> { SimCar.coding[did] = req.drop(3).toIntArray(); intArrayOf(0x6E, req[1], req[2]) }
            }
        }
        0x3E -> intArrayOf(0x7E, 0x00)
        else -> intArrayOf(0x7F, req[0], 0x11)
    }
}
