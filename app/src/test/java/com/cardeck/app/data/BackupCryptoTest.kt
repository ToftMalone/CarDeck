package com.cardeck.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class BackupCryptoTest {
    private val key = BackupCrypto.keyFromSecret("secret de test")
    private val keys = mapOf(BackupCrypto.KEY_OFFICIAL to key)

    private fun seal(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(key, BackupCrypto.KEY_OFFICIAL, out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun open(file: ByteArray, k: Map<Int, ByteArray> = keys) = BackupCrypto.decrypt(k, ByteArrayInputStream(file)).use { it.readBytes() }

    private fun fails(block: () -> Unit): String? = try { block(); null } catch (e: BackupCrypto.BadBackup) { e.message }

    @Test fun allerRetour() {
        for (size in listOf(0, 1, 65_536, 65_537, 300_000)) {
            val data = Random(size).nextBytes(size)
            assertArrayEquals(data, open(seal(data)))
        }
    }

    @Test fun contenuIllisibleEnClair() {
        val data = "Swift Sport · trajet Paris-Lyon".repeat(50).toByteArray()
        val file = seal(data)
        assertFalse(String(file, Charsets.ISO_8859_1).contains("Swift"))
        // Deux sauvegardes identiques donnent deux fichiers différents (sel aléatoire).
        assertFalse(file.contentEquals(seal(data)))
    }

    @Test fun modificationDetectee() {
        val file = seal(Random(1).nextBytes(100_000))
        file[file.size / 2] = (file[file.size / 2].toInt() xor 1).toByte()
        assertEquals("Sauvegarde endommagée ou modifiée", fails { open(file) })
    }

    @Test fun troncatureDetectee() {
        val file = seal(Random(2).nextBytes(200_000))
        // Fichier coupé juste après un bloc complet : la marque de fin manque.
        val cut = 38 + 4 + 65_536 + 16
        assertEquals("Sauvegarde incomplète", fails { open(file.copyOf(cut)) })
    }

    @Test fun mauvaiseCleOuFichierEtranger() {
        val file = seal(byteArrayOf(1, 2, 3))
        assertTrue(fails { open(file, mapOf(BackupCrypto.KEY_OFFICIAL to BackupCrypto.keyFromSecret("autre"))) }!!.contains("modifiée"))
        assertTrue(fails { open(file, emptyMap()) }!!.contains("version publiée"))
        assertEquals("Ce fichier n'est pas une sauvegarde CarDeck", fails { open("PK\u0003\u0004 zip quelconque".toByteArray() + ByteArray(64)) })
    }
}
