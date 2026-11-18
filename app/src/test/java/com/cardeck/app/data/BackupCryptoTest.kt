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
    private val device = BackupLock.Device(BackupCrypto.keyFromSecret("appareil de test"), "Autoradio Test X1")
    private val pw = "123456"

    /** Peu d'itérations : tests rapides (le format enregistre le nombre utilisé). */
    private fun seal(data: ByteArray, lock: BackupLock = device): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(lock, out, iterations = 1000).use { it.write(data) }
        return out.toByteArray()
    }

    private fun open(file: ByteArray, password: String? = pw, secret: ByteArray? = device.secret, legacy: Map<Int, ByteArray> = emptyMap()) =
        BackupCrypto.decrypt(ByteArrayInputStream(file), legacy, password?.toCharArray(), secret).use { it.readBytes() }

    private fun fails(block: () -> Unit): BackupCrypto.BadBackup? = try { block(); null } catch (e: BackupCrypto.BadBackup) { e }

    @Test fun allerRetourTroisModes() {
        for (lock in listOf(BackupLock.None, BackupLock.Password(pw.toCharArray()), device)) {
            for (size in listOf(0, 1, 65_536, 65_537, 300_000)) {
                val data = Random(size).nextBytes(size)
                assertArrayEquals(data, open(seal(data, lock)))
            }
        }
    }

    @Test fun enTete() {
        assertEquals(BackupHeader(BackupCrypto.MODE_DEVICE, "Autoradio Test X1"), BackupCrypto.header(ByteArrayInputStream(seal(byteArrayOf(1)))))
        assertTrue(BackupCrypto.header(ByteArrayInputStream(seal(byteArrayOf(1), BackupLock.Password(pw.toCharArray())))).needsPassword)
        assertEquals(BackupCrypto.MODE_PLAIN, BackupCrypto.header(ByteArrayInputStream(seal(byteArrayOf(1), BackupLock.None))).mode)
    }

    @Test fun enClairLisible() {
        val data = "Swift Sport · trajet Paris-Lyon".repeat(50).toByteArray()
        assertTrue(String(seal(data, BackupLock.None), Charsets.ISO_8859_1).contains("Swift Sport"))
    }

    @Test fun chiffreIllisible() {
        val data = "Swift Sport · trajet Paris-Lyon".repeat(50).toByteArray()
        for (lock in listOf(BackupLock.Password(pw.toCharArray()), device)) {
            val file = seal(data, lock)
            assertFalse(String(file, Charsets.ISO_8859_1).contains("Swift"))
            // Deux sauvegardes identiques donnent deux fichiers différents (sel aléatoire).
            assertFalse(file.contentEquals(seal(data, lock)))
        }
    }

    @Test fun motDePasse() {
        val file = seal(Random(3).nextBytes(1000), BackupLock.Password(pw.toCharArray()))
        assertTrue(fails { open(file, password = null) } is BackupCrypto.PasswordNeeded)
        assertTrue(fails { open(file, password = "1234567") } is BackupCrypto.WrongPassword)
        assertEquals(1000, open(file).size)
    }

    @Test fun autreAppareil() {
        val file = seal(Random(4).nextBytes(1000))
        val e = fails { open(file, secret = BackupCrypto.keyFromSecret("autre appareil")) }
        assertTrue(e is BackupCrypto.WrongDevice)
        assertTrue(e!!.message!!.contains("Autoradio Test X1"))
        assertTrue(fails { open(file, secret = null) } is BackupCrypto.WrongDevice)
    }

    @Test fun modificationDetectee() {
        val file = seal(Random(1).nextBytes(100_000))
        file[file.size / 2] = (file[file.size / 2].toInt() xor 1).toByte()
        assertEquals("Sauvegarde endommagée ou modifiée", fails { open(file) }?.message)
    }

    @Test fun enTeteModifieDetecte() {
        // Changer le libellé de l'appareil (authentifié avec chaque bloc) rend le fichier illisible.
        val file = seal(Random(5).nextBytes(1000))
        val i = String(file, Charsets.ISO_8859_1).indexOf("X1")
        file[i] = 'Y'.code.toByte()
        assertEquals("Sauvegarde endommagée ou modifiée", fails { open(file) }?.message)
    }

    @Test fun troncatureDetectee() {
        val file = seal(Random(2).nextBytes(200_000))
        // Fichier coupé juste après un bloc complet : la marque de fin manque.
        val header = 4 + 1 + 1 + 32 + 4 + 16 + 2 + "Autoradio Test X1".length
        val cut = header + 4 + 65_536 + 16
        assertEquals("Sauvegarde incomplète", fails { open(file.copyOf(cut)) }?.message)
    }

    @Test fun ancienneSauvegardeRelue() {
        val key = BackupCrypto.keyFromSecret("clé officielle")
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptV1(key, BackupCrypto.KEY_OFFICIAL, out).use { it.write(byteArrayOf(7, 8, 9)) }
        val file = out.toByteArray()
        assertEquals(BackupCrypto.MODE_LEGACY, BackupCrypto.header(ByteArrayInputStream(file)).mode)
        assertArrayEquals(byteArrayOf(7, 8, 9), open(file, legacy = mapOf(BackupCrypto.KEY_OFFICIAL to key)))
        assertTrue(fails { open(file) }!!.message!!.contains("version publiée"))
        // Une version publiée refuse la clé de test (publique).
        val t = ByteArrayOutputStream()
        BackupCrypto.encryptV1(BackupCrypto.keyFromSecret("CarDeck test"), BackupCrypto.KEY_TEST, t).use { it.write(byteArrayOf(1)) }
        assertTrue(fails { open(t.toByteArray(), legacy = mapOf(BackupCrypto.KEY_OFFICIAL to key)) }!!.message!!.contains("développement"))
    }

    @Test fun fichierEtranger() {
        assertEquals("Ce fichier n'est pas une sauvegarde CarDeck", fails { open("PK\u0003\u0004 zip quelconque".toByteArray() + ByteArray(64)) }?.message)
    }

    @Test fun forceDuMotDePasse() {
        assertEquals(0, BackupCrypto.passwordStrength("123456"))
        assertEquals(0, BackupCrypto.passwordStrength("aaaaaaaaaaaa"))
        assertTrue(BackupCrypto.passwordStrength("Garage-Swift-2024!") >= 3)
        assertTrue(BackupCrypto.passwordStrength("motdepasse") <= 1)
    }
}
