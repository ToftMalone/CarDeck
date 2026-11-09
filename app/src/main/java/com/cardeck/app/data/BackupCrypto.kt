package com.cardeck.app.data

import java.io.DataInputStream
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
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement des sauvegardes : AES-256-GCM par blocs de 64 Ko, en flux (aucune sauvegarde n'est chargée entière en mémoire).
 * Clé propre à chaque fichier = HMAC-SHA256(clé de l'application, sel aléatoire). Chaque bloc est authentifié avec son numéro
 * et une marque de fin : un bloc modifié, déplacé ou retiré (fichier tronqué) est détecté.
 * En-tête : « CDBK », version, identifiant de clé ([KEY_OFFICIAL] ou [KEY_TEST]), sel de 32 octets.
 */
object BackupCrypto {
    const val KEY_TEST = 0
    const val KEY_OFFICIAL = 1

    private val MAGIC = "CDBK".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val CHUNK = 64 * 1024
    private const val TAG = 16
    private const val HEADER = 4 + 1 + 1 + 32

    class BadBackup(message: String) : Exception(message)

    /** Clé dérivée d'un secret (même calcul que le script de compilation pour la clé officielle). */
    fun keyFromSecret(secret: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(("CarDeck sauvegarde v1\u0000$secret").toByteArray(Charsets.UTF_8))

    /** Flux chiffrant : tout ce qui y est écrit est chiffré vers [out] ; [OutputStream.close] écrit le dernier bloc. */
    fun encrypt(master: ByteArray, keyId: Int, out: OutputStream): OutputStream {
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val header = MAGIC + byteArrayOf(VERSION.toByte(), keyId.toByte()) + salt
        out.write(header)
        val key = fileKey(master, salt)
        return object : FilterOutputStream(out) {
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
    }

    /** Flux déchiffrant ; [keys] : clés connues de l'application, par identifiant. Lève [BadBackup] si le fichier est invalide. */
    fun decrypt(keys: Map<Int, ByteArray>, input: InputStream): InputStream {
        val inp = DataInputStream(input)
        val header = ByteArray(HEADER)
        try { inp.readFully(header) } catch (_: EOFException) { throw BadBackup("Ce fichier n'est pas une sauvegarde CarDeck") }
        if (!header.copyOfRange(0, 4).contentEquals(MAGIC)) throw BadBackup("Ce fichier n'est pas une sauvegarde CarDeck")
        if (header[4].toInt() != VERSION) throw BadBackup("Sauvegarde d'une version plus récente de CarDeck")
        val keyId = header[5].toInt()
        val master = keys[keyId] ?: throw BadBackup(
            if (keyId == KEY_OFFICIAL) "Sauvegarde officielle : à restaurer avec une version publiée de CarDeck" else "Sauvegarde chiffrée avec une clé inconnue",
        )
        val key = fileKey(master, header.copyOfRange(6, HEADER))
        return object : InputStream() {
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
                            throw BadBackup("Sauvegarde endommagée ou modifiée")
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
}
