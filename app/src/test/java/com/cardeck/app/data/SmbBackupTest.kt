package com.cardeck.app.data

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMBApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

class SmbBackupTest {
    @Test
    fun adressesCollees() {
        val a = SmbServer.parseAddress("smb://nas/Sauvegardes/CarDeck/2026")
        assertEquals(Triple("nas", "Sauvegardes", "CarDeck/2026"), Triple(a.host, a.share, a.folder))
        val b = SmbServer.parseAddress("\\\\192.168.1.20\\Partage")
        assertEquals(Triple("192.168.1.20", "Partage", null), Triple(b.host, b.share, b.folder))
        val c = SmbServer.parseAddress("  nas.local:4455 ")
        assertEquals(Triple("nas.local", 4455, null), Triple(c.host, c.port, c.share))
        assertEquals("nas", SmbServer.parseAddress("nas").host)
    }

    @Test
    fun saisieControlee() {
        val p = SmbServer::problem
        assertNull(p("nas", null, "Partage", "CarDeck", "tom"))
        assertNull(p("192.168.1.20", 445, "Partage", "", "tom")) // dossier vide = racine
        assertNotNull(p("", null, "Partage", "CarDeck", "tom"))
        assertNotNull(p("nas/partage", null, "Partage", "", "tom")) // chemin dans le champ du serveur
        assertNotNull(p("nas", 70000, "Partage", "", "tom"))
        assertNotNull(p("nas", null, "", "", "tom"))
        assertNotNull(p("nas", null, "Par:tage", "", "tom"))
        assertNotNull(p("nas", null, "Partage", "../etc", "tom")) // remonter dans l'arborescence : refusé
        assertNotNull(p("nas", null, "Partage", "a/b/../c", "tom"))
        assertNotNull(p("nas", null, "Partage", "CarDeck", "")) // pas de connexion anonyme
        assertNotNull(p("nas", null, "Partage", "x".repeat(81), "tom"))
    }

    @Test
    fun serveurDepuisLaSaisie() {
        val saved = SmbServer("nas", 445, "Partage", "CarDeck", "tom", "secret")
        // Mot de passe laissé vide : celui qui est enregistré est gardé (même serveur, même compte).
        assertEquals("secret", SmbServer.build("nas", "Partage", "CarDeck", "tom", "", "", saved).server!!.password)
        // Autre compte ou autre serveur : jamais de mot de passe repris.
        assertEquals("", SmbServer.build("nas", "Partage", "CarDeck", "anne", "", "", saved).server!!.password)
        assertEquals("", SmbServer.build("autre", "Partage", "CarDeck", "tom", "", "", saved).server!!.password)
        assertEquals("neuf", SmbServer.build("nas", "Partage", "CarDeck", "tom", "neuf", "", saved).server!!.password)
        // Adresse collée avec partage et dossier.
        val s = SmbServer.build("smb://nas:4455/Photos/Voiture/CarDeck", "", "", "tom", "x", "WORKGROUP", null).server!!
        assertEquals(listOf("nas", 4455, "Photos", "Voiture/CarDeck", "WORKGROUP"), listOf(s.host, s.port, s.share, s.folder, s.domain))
        assertNotNull(SmbServer.build("nas", "Partage", "..", "tom", "x", "", null).error)
    }

    @Test
    fun cheminCollePartagePlusDossier() {
        // Chemin de l'explorateur de fichiers collé dans le champ du partage : partage = premier élément, dossier = le reste.
        for (txt in listOf("//Stockage Thomas HUGO/Fichiers informatiques/Backup", "\\\\Stockage Thomas HUGO\\Fichiers informatiques\\Backup", "/Stockage Thomas HUGO/Fichiers informatiques/Backup/")) {
            val r = SmbServer.build("192.168.1.20", txt, "CarDeck", "thomas", "x", "", null)
            assertNull(txt, r.error)
            assertEquals("Stockage Thomas HUGO", r.server!!.share)
            assertEquals("Fichiers informatiques/Backup", r.server!!.folder)
            assertEquals("Fichiers informatiques\\Backup\\a.cdbak", r.server!!.pathOf("a.cdbak"))
        }
        // Partage seul (avec espaces) : le dossier du champ suivant est gardé.
        val s = SmbServer.build("nas", "Stockage Thomas HUGO", "Sauvegardes", "thomas", "x", "", null).server!!
        assertEquals("Stockage Thomas HUGO" to "Sauvegardes", s.share to s.folder)
        // Adresse complète dans le champ du serveur, champ du dossier laissé à sa valeur par défaut : le dossier de l'adresse l'emporte.
        val t = SmbServer.build("smb://nas/Photos/Voiture", "", "CarDeck", "thomas", "x", "", null).server!!
        assertEquals("Photos" to "Voiture", t.share to t.folder)
        // Toujours refusé : remonter dans l'arborescence.
        assertNotNull(SmbServer.build("nas", "Partage/../etc", "", "thomas", "x", "", null).error)
    }

    @Test
    fun cheminsEtLibelle() {
        val s = SmbServer("nas", 445, "Partage", "CarDeck/2026", "tom", "secret")
        assertEquals("CarDeck\\2026", s.folderPath)
        assertEquals("CarDeck\\2026\\a.cdbak", s.pathOf("a.cdbak"))
        assertEquals("\\\\nas\\Partage\\CarDeck\\2026", s.label)
        assertEquals("\\\\nas:4455\\P", SmbServer("nas", 4455, "P", "", "u").label)
        assertEquals("a.cdbak", SmbServer("nas", 445, "P", "", "u").pathOf("a.cdbak"))
        // Le mot de passe n'apparaît jamais dans un texte (journal, message d'erreur).
        assertFalse(s.toString().contains("secret"))
    }

    @Test
    fun nomsDeFichiersSurs() {
        assertTrue(SmbBackup.safeName("CarDeck-2026-11-23-1030.cdbak"))
        assertFalse(SmbBackup.safeName("../CarDeck.cdbak"))
        assertFalse(SmbBackup.safeName("dossier\\CarDeck.cdbak"))
        assertFalse(SmbBackup.safeName("dossier/CarDeck.cdbak"))
        assertFalse(SmbBackup.safeName(".cardeck-test"))
        assertFalse(SmbBackup.safeName(".cache.cdbak"))
        assertFalse(SmbBackup.safeName("CarDeck.txt"))
        assertFalse(SmbBackup.safeName(".cdbak"))
        assertFalse(SmbBackup.safeName("a:b.cdbak"))
        assertFalse(SmbBackup.safeName("a".repeat(130) + ".cdbak"))
    }

    @Test
    fun messagesLisibles() {
        fun api(st: NtStatus) = SMBApiException(st.value, SMB2MessageCommandCode.SMB2_SESSION_SETUP, null)
        assertTrue(SmbBackup.describe(api(NtStatus.STATUS_LOGON_FAILURE)).contains("mot de passe"))
        assertTrue(SmbBackup.describe(api(NtStatus.STATUS_BAD_NETWORK_NAME)).contains("Partage introuvable"))
        assertTrue(SmbBackup.describe(api(NtStatus.STATUS_ACCESS_DENIED)).contains("Accès refusé"))
        assertTrue(SmbBackup.describe(api(NtStatus.STATUS_DISK_FULL)).contains("place"))
        // Erreur réseau enveloppée dans une autre : on remonte à la cause.
        assertTrue(SmbBackup.describe(RuntimeException("x", IOException("y", UnknownHostException("nas")))).contains("introuvable"))
        assertTrue(SmbBackup.describe(IOException("x", ConnectException("refused"))).contains("injoignable"))
        assertEquals("Fichier trop gros", SmbBackup.describe(SmbProblem("Fichier trop gros")))
    }
}
