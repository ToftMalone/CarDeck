package com.cardeck.app.data

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Protection d'une sauvegarde, choisie par l'utilisateur. */
sealed class BackupLock {
    /** Aucune : fichier lisible par quiconque le récupère. */
    data object None : BackupLock()

    /** Mot de passe choisi par l'utilisateur (aucune exigence de longueur ou de complexité). */
    class Password(val password: CharArray) : BackupLock()

    /** Lié à cet appareil : [secret] dérivé de l'appareil, [label] (marque et modèle) écrit en clair pour l'afficher à la restauration. */
    class Device(val secret: ByteArray, val label: String) : BackupLock()
}

/** En-tête d'une sauvegarde, lisible sans clé : sert à savoir quoi demander avant la restauration. */
data class BackupHeader(val mode: Int, val label: String) {
    val needsPassword get() = mode == BackupCrypto.MODE_PASSWORD
}

/**
 * Format des sauvegardes. Version 2 (actuelle) : « CDBK », 2, mode, sel de 32 octets, nombre d'itérations (mot de passe),
 * libellé (appareil) ; puis, sauf en clair, AES-256-GCM par blocs de 64 Ko, en flux (rien n'est chargé entier en mémoire).
 * Chaque bloc est authentifié avec l'en-tête complet, son numéro et une marque de fin : un bloc modifié, déplacé ou retiré
 * (fichier tronqué) est détecté.
 *
 * Version 1 (CarDeck 0.1) : clé intégrée à l'application ([KEY_OFFICIAL]) ou de test ([KEY_TEST]), encore relue.
 */
object BackupCrypto {
    // Version 1 : identifiant de clé.
    const val KEY_TEST = 0
    const val KEY_OFFICIAL = 1

    // Version 2 : mode de protection.
    const val MODE_PLAIN = 0
    const val MODE_PASSWORD = 2
    const val MODE_DEVICE = 3

    /** Sauvegarde de la version 1 (CarDeck 0.1), chiffrée avec la clé intégrée à l'application. */
    const val MODE_LEGACY = -1

    /** Itérations PBKDF2 : quelques dixièmes de seconde sur un téléphone, quelques secondes sur un autoradio d'entrée de gamme. */
    const val PASSWORD_ITERATIONS = 210_000

    private val MAGIC = "CDBK".toByteArray(Charsets.US_ASCII)
    private const val CHUNK = 64 * 1024
    private const val TAG = 16
    private const val V1_HEADER = 4 + 1 + 1 + 32

    open class BadBackup(message: String) : Exception(message)
    class WrongPassword : BadBackup("Mot de passe incorrect")
    class WrongDevice(label: String) : BadBackup(
        "Cette sauvegarde est liée à un autre appareil" + (if (label.isNotBlank()) " ($label)" else "") +
            ", ou cet appareil a été réinitialisé depuis : elle ne peut pas être restaurée ici.",
    )
    class PasswordNeeded : BadBackup("Cette sauvegarde est protégée par un mot de passe")

    /** Clé dérivée d'un secret (même calcul que le script de compilation pour la clé officielle de la version 1). */
    fun keyFromSecret(secret: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(("CarDeck sauvegarde v1\u0000$secret").toByteArray(Charsets.UTF_8))

    // ------------------------------------------------------------------ écriture (version 2)

    /** Flux protégé selon [lock] : tout ce qui y est écrit va vers [out] ; [OutputStream.close] termine le fichier. */
    fun encrypt(lock: BackupLock, out: OutputStream, iterations: Int = PASSWORD_ITERATIONS): OutputStream {
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val (mode, label) = when (lock) {
            BackupLock.None -> MODE_PLAIN to ""
            is BackupLock.Password -> MODE_PASSWORD to ""
            is BackupLock.Device -> MODE_DEVICE to lock.label
        }
        val master = when (lock) {
            BackupLock.None -> null
            is BackupLock.Password -> passwordKey(lock.password, salt, iterations)
            is BackupLock.Device -> lock.secret
        }
        val key = master?.let { fileKey(it, salt) }
        val header = headerV2(mode, salt, if (mode == MODE_PASSWORD) iterations else 0, key?.let { check(it) } ?: ByteArray(CHECK), label)
        out.write(header)
        return if (key == null) out else sealed(out, key, header)
    }

    private const val CHECK = 16

    /** Empreinte de vérification de la clé, dans l'en-tête : distingue un mauvais mot de passe (ou appareil) d'un fichier abîmé. */
    private fun check(key: SecretKeySpec): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.encoded, "HmacSHA256")) }
            .doFinal("CarDeck vérification".toByteArray(Charsets.UTF_8)).copyOf(CHECK)

    /** Écrit au format de la version 1 (CarDeck 0.1) : réservé aux tests de relecture des anciennes sauvegardes. */
    internal fun encryptV1(master: ByteArray, keyId: Int, out: OutputStream): OutputStream {
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val header = MAGIC + byteArrayOf(1, keyId.toByte()) + salt
        out.write(header)
        return sealed(out, fileKey(master, salt), header)
    }

    private fun headerV2(mode: Int, salt: ByteArray, iterations: Int, check: ByteArray, label: String): ByteArray {
        val b = ByteArrayOutputStream()
        DataOutputStream(b).apply {
            write(MAGIC); writeByte(2); writeByte(mode); write(salt); writeInt(iterations); write(check)
            val l = label.toByteArray(Charsets.UTF_8).let { if (it.size > 200) it.copyOf(200) else it }
            writeShort(l.size); write(l)
        }
        return b.toByteArray()
    }

    private fun sealed(out: OutputStream, key: SecretKeySpec, header: ByteArray): OutputStream = object : FilterOutputStream(out) {
        private val buf = ByteArray(CHUNK)
        private var n = 0
        private var counter = 0L
        private var closed = false

        override fun write(b: Int) {
            if (n == CHUNK) emit(false)
            buf[n++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var o = off
            var left = len
            while (left > 0) {
                if (n == CHUNK) emit(false)
                val k = minOf(left, CHUNK - n)
                System.arraycopy(b, o, buf, n, k)
                n += k; o += k; left -= k
            }
        }

        private fun emit(last: Boolean) {
            val c = cipher(Cipher.ENCRYPT_MODE, key, header, counter++, last).doFinal(buf, 0, n)
            out.write(ByteBuffer.allocate(4).putInt(c.size).array())
            out.write(c)
            n = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            emit(true)
            out.close()
        }
    }

    // ------------------------------------------------------------------ lecture

    private class Parsed(val header: ByteArray, val mode: Int, val salt: ByteArray, val iterations: Int, val label: String, val v1: Boolean, val check: ByteArray = ByteArray(0))

    private fun parse(inp: DataInputStream): Parsed {
        val start = ByteArray(5)
        try { inp.readFully(start) } catch (_: EOFException) { throw BadBackup("Ce fichier n'est pas une sauvegarde CarDeck") }
        if (!start.copyOfRange(0, 4).contentEquals(MAGIC)) throw BadBackup("Ce fichier n'est pas une sauvegarde CarDeck")
        try {
            when (start[4].toInt()) {
                1 -> {
                    val rest = ByteArray(V1_HEADER - 5).also { inp.readFully(it) }
                    val header = start + rest
                    return Parsed(header, header[5].toInt(), header.copyOfRange(6, V1_HEADER), 0, "", v1 = true)
                }
                2 -> {
                    val mode = inp.readUnsignedByte()
                    val salt = ByteArray(32).also { inp.readFully(it) }
                    val iterations = inp.readInt()
                    val check = ByteArray(CHECK).also { inp.readFully(it) }
                    val labelBytes = ByteArray(inp.readUnsignedShort()).also { inp.readFully(it) }
                    if (mode !in listOf(MODE_PLAIN, MODE_PASSWORD, MODE_DEVICE)) throw BadBackup("Sauvegarde d'une version plus récente de CarDeck")
                    if (mode == MODE_PASSWORD && iterations !in 1..10_000_000) throw BadBackup("Sauvegarde endommagée")
                    // En-tête tel qu'il est dans le fichier (authentifié avec chaque bloc).
                    val header = ByteArrayOutputStream().also { b ->
                        DataOutputStream(b).apply { write(start); writeByte(mode); write(salt); writeInt(iterations); write(check); writeShort(labelBytes.size); write(labelBytes) }
                    }.toByteArray()
                    return Parsed(header, mode, salt, iterations, String(labelBytes, Charsets.UTF_8), v1 = false, check = check)
                }
                else -> throw BadBackup("Sauvegarde d'une version plus récente de CarDeck")
            }
        } catch (_: EOFException) {
            throw BadBackup("Sauvegarde incomplète")
        }
    }

    /** Lit seulement l'en-tête de la sauvegarde (mode de protection, appareil) ; lève [BadBackup] si ce n'en est pas une. */
    fun header(input: InputStream): BackupHeader {
        val p = parse(DataInputStream(input))
        return if (p.v1) BackupHeader(MODE_LEGACY, "") else BackupHeader(p.mode, p.label)
    }

    /**
     * Flux de lecture du contenu. [legacyKeys] : clés de la version 1 par identifiant ; [password] pour une sauvegarde
     * protégée par mot de passe ; [deviceSecret] pour une sauvegarde liée à l'appareil.
     * Lève [PasswordNeeded], [WrongPassword], [WrongDevice] ou [BadBackup].
     */
    fun decrypt(input: InputStream, legacyKeys: Map<Int, ByteArray>, password: CharArray? = null, deviceSecret: ByteArray? = null): InputStream =
        open(input, legacyKeys, password, deviceSecret).second

    /** Comme [decrypt], avec l'en-tête lu. */
    fun open(input: InputStream, legacyKeys: Map<Int, ByteArray>, password: CharArray? = null, deviceSecret: ByteArray? = null): Pair<BackupHeader, InputStream> {
        val inp = DataInputStream(input)
        val p = parse(inp)
        val h = if (p.v1) BackupHeader(MODE_LEGACY, "") else BackupHeader(p.mode, p.label)
        return h to stream(inp, p, legacyKeys, password, deviceSecret)
    }

    private fun stream(inp: DataInputStream, p: Parsed, legacyKeys: Map<Int, ByteArray>, password: CharArray?, deviceSecret: ByteArray?): InputStream {
        if (p.v1) {
            val master = legacyKeys[p.mode] ?: throw BadBackup(
                when (p.mode) {
                    KEY_OFFICIAL -> "Sauvegarde officielle : à restaurer avec une version publiée de CarDeck"
                    KEY_TEST -> "Sauvegarde d'une version de développement : refusée par une version publiée de CarDeck"
                    else -> "Sauvegarde chiffrée avec une clé inconnue"
                },
            )
            return opened(inp, fileKey(master, p.salt), p.header) { BadBackup("Sauvegarde endommagée ou modifiée") }
        }
        return when (p.mode) {
            MODE_PLAIN -> inp
            MODE_PASSWORD -> {
                val pw = password ?: throw PasswordNeeded()
                verified(inp, p, fileKey(passwordKey(pw, p.salt, p.iterations), p.salt)) { WrongPassword() }
            }
            else -> {
                val secret = deviceSecret ?: throw WrongDevice(p.label)
                verified(inp, p, fileKey(secret, p.salt)) { WrongDevice(p.label) }
            }
        }
    }

    /** Vérifie la clé avec l'empreinte de l'en-tête ([wrongKey] si elle ne correspond pas), puis ouvre le contenu. */
    private fun verified(inp: DataInputStream, p: Parsed, key: SecretKeySpec, wrongKey: () -> BadBackup): InputStream {
        if (!MessageDigest.isEqual(check(key), p.check)) throw wrongKey()
        return opened(inp, key, p.header) { BadBackup("Sauvegarde endommagée ou modifiée") }
    }

    /** [firstBlockFailure] : erreur si le tout premier bloc ne s'authentifie pas. */
    private fun opened(inp: DataInputStream, key: SecretKeySpec, header: ByteArray, firstBlockFailure: () -> BadBackup): InputStream = object : InputStream() {
        private var buf = ByteArray(0)
        private var pos = 0
        private var counter = 0L
        private var ended = false

        /** Charge le bloc suivant ; false à la fin authentifiée du fichier. */
        private fun next(): Boolean {
            while (pos >= buf.size) {
                if (ended) return false
                val len = try { inp.readInt() } catch (_: EOFException) { throw BadBackup("Sauvegarde incomplète") }
                if (len < TAG || len > CHUNK + TAG) throw BadBackup("Sauvegarde endommagée")
                val c = ByteArray(len)
                try { inp.readFully(c) } catch (_: EOFException) { throw BadBackup("Sauvegarde incomplète") }
                buf = try {
                    cipher(Cipher.DECRYPT_MODE, key, header, counter, false).doFinal(c)
                } catch (_: AEADBadTagException) {
                    try {
                        cipher(Cipher.DECRYPT_MODE, key, header, counter, true).doFinal(c).also { ended = true }
                    } catch (_: AEADBadTagException) {
                        throw if (counter == 0L) firstBlockFailure() else BadBackup("Sauvegarde endommagée ou modifiée")
                    }
                }
                counter++
                pos = 0
                if (ended && inp.read() != -1) throw BadBackup("Sauvegarde endommagée ou modifiée")
            }
            return true
        }

        override fun read(): Int = if (next()) buf[pos++].toInt() and 0xFF else -1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!next()) return -1
            val k = minOf(len, buf.size - pos)
            System.arraycopy(buf, pos, b, off, k)
            pos += k
            return k
        }

        override fun close() = inp.close()
    }

    // ------------------------------------------------------------------ clés

    /** Clé maîtresse d'un mot de passe : PBKDF2-HMAC-SHA256 salé. */
    fun passwordKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun fileKey(master: ByteArray, salt: ByteArray): SecretKeySpec {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(master, "HmacSHA256"))
        return SecretKeySpec(mac.doFinal(salt), "AES")
    }

    private fun cipher(mode: Int, key: SecretKeySpec, header: ByteArray, counter: Long, last: Boolean): Cipher {
        val nonce = ByteBuffer.allocate(12).putInt(0).putLong(counter).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG * 8, nonce))
            updateAAD(header)
            updateAAD(ByteBuffer.allocate(9).putLong(counter).put(if (last) 1 else 0).array())
        }
    }

    // ------------------------------------------------------------------ force du mot de passe

    /** Force indicative d'un mot de passe : 0 très faible, 1 faible, 2 moyen, 3 fort, 4 très fort. Purement informatif. */
    fun passwordStrength(pw: String): Int {
        if (pw.isEmpty()) return 0
        val lower = pw.lowercase()
        val common = listOf("123456", "password", "motdepasse", "azerty", "qwerty", "000000", "111111", "abc123", "soleil", "cardeck")
        if (common.any { lower.contains(it) } && pw.length < 12) return 0
        val classes = listOf(pw.any { it.isLowerCase() }, pw.any { it.isUpperCase() }, pw.any { it.isDigit() }, pw.any { !it.isLetterOrDigit() }).count { it }
        val distinct = pw.toSet().size
        var score = when {
            pw.length >= 16 -> 3
            pw.length >= 12 -> 2
            pw.length >= 8 -> 1
            else -> 0
        }
        if (classes >= 3) score++
        if (distinct <= 3) score = minOf(score, 0)
        return score.coerceIn(0, 4)
    }
}
