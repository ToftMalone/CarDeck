package com.cardeck.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class I18nTest {
    companion object {
        @BeforeClass @JvmStatic
        fun load() = I18n.load(File("src/main/assets/i18n/strings.tsv").readText())
    }

    @After
    fun reset() { I18n.lang = Lang.Fr }

    private fun en(s: String): String { I18n.lang = Lang.En; return tr(s) }

    @Test
    fun francaisInchange() {
        I18n.lang = Lang.Fr
        assertEquals("Tableau de bord", tr("Tableau de bord"))
    }

    @Test
    fun textesSimples() {
        assertEquals("Dashboard", en("Tableau de bord"))
        I18n.lang = Lang.De; assertEquals("Armaturenbrett", tr("Tableau de bord"))
        I18n.lang = Lang.Ru; assertEquals("Приборная панель", tr("Tableau de bord"))
        I18n.lang = Lang.Es; assertEquals("Panel", tr("Tableau de bord"))
        // Absent de la table : reste en français ; sans lettres : tel quel.
        assertEquals("Texte inconnu", en("Texte inconnu"))
        assertEquals("812", en("812"))
    }

    @Test
    fun gabarits() {
        assertEquals("Step 3/13 · ECUs on 11-bit CAN", en("Étape 3/13 · Calculateurs CAN 11 bits"))
        assertEquals("Trip in progress · 12,5 km", en("Trajet en cours · 12,5 km"))
        // Ordre changé ({1}, {3}…) : le suffixe du pluriel français est ignoré.
        I18n.lang = Lang.De
        assertEquals("3 Code(s) · Scan heute", tr("3 codes · scan heute"))
    }

    @Test
    fun pluriels() {
        assertEquals("Backup CarDeck.cdbak saved · 2 vehicles, 1 trip", en("Sauvegarde CarDeck.cdbak enregistrée · 2 véhicules, 1 trajet"))
        I18n.lang = Lang.Ru
        assertEquals("Резервная копия CarDeck.cdbak сохранена · автомобилей: 2, поездок: 1", tr("Sauvegarde CarDeck.cdbak enregistrée · 2 véhicules, 1 trajet"))
        // Nom choisi par l'utilisateur : repris tel quel.
        I18n.lang = Lang.Es
        assertEquals("¿Eliminar «Ma petite Clio»?", tr("Supprimer « Ma petite Clio » ?"))
        assertEquals("1 dispositivo(s) encontrado(s)", tr("1 appareil trouvé"))
    }

    @Test
    fun textesAssembles() {
        assertEquals("Engine · Ignition", en("Moteur · Allumage"))
        assertEquals("Misfire detected, cylinder 3", en("Raté d'allumage détecté, cylindre 3"))
        assertEquals("Fuel level sensor A: signal low", en("Capteur de niveau de carburant A : signal bas"))
    }

    @Test
    fun tousLesCodesDefautTraduits() {
        val titles = File("src/main/resources/dtc_p0.txt").readLines().filter { it.isNotBlank() }.map { it.split('|')[1] }
        for (l in Lang.entries.drop(1)) {
            I18n.lang = l
            val missing = titles.filter { tr(it) == it }
            assertTrue("${l.code} : ${missing.size} titres non traduits, ex. ${missing.take(5)}", missing.isEmpty())
            // Aucun morceau resté en français (repéré par ses mots courants).
            val mixed = titles.map { tr(it) }.filter { Regex("\\b(signal bas|circuit de|défaut|capteur)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            assertTrue("${l.code} : ${mixed.take(5)}", mixed.isEmpty())
        }
    }

    @Test
    fun codesDetaillesTraduits() {
        val info = DtcCatalog.info("P0420")
        I18n.lang = Lang.En
        assertTrue(tr(info.title) != info.title)
        assertTrue(info.causes.all { tr(it) != it })
        assertTrue(info.recos.all { tr(it) != it })
        assertTrue(tr(info.sys.label) != info.sys.label)
    }

    @Test
    fun messagesDuServeurSmbTraduits() {
        fun api(st: com.hierynomus.mserref.NtStatus) = com.hierynomus.mssmb2.SMBApiException(st.value, com.hierynomus.mssmb2.SMB2MessageCommandCode.SMB2_SESSION_SETUP, null)
        val fr = listOf(
            SmbBackup.describe(api(com.hierynomus.mserref.NtStatus.STATUS_LOGON_FAILURE)),
            SmbBackup.describe(api(com.hierynomus.mserref.NtStatus.STATUS_BAD_NETWORK_NAME)),
            SmbBackup.describe(api(com.hierynomus.mserref.NtStatus.STATUS_ACCESS_DENIED)),
            SmbBackup.describe(api(com.hierynomus.mserref.NtStatus.STATUS_DISK_FULL)),
            SmbBackup.describe(java.net.UnknownHostException("nas")),
            SmbBackup.describe(java.net.ConnectException("refused")),
            SmbBackup.describe(java.net.SocketTimeoutException()),
            SmbServer.problem("", null, "P", "", "u")!!, SmbServer.problem("nas", 0, "P", "", "u")!!, SmbServer.problem("nas", null, "", "", "u")!!,
            SmbServer.problem("nas", null, "P", "..", "u")!!, SmbServer.problem("nas", null, "P", "", "")!!,
            "Connexion réussie. 3 sauvegardes sur le serveur.", "Connexion réussie. 1 sauvegarde sur le serveur.",
            "Sauvegarde envoyée sur le serveur · 2 véhicules, 1 trajet",
        )
        for (l in Lang.entries.drop(1)) {
            I18n.lang = l
            val missing = fr.filter { tr(it) == it }
            assertTrue("${l.code} : $missing", missing.isEmpty())
        }
        // Phrases assemblées : le préfixe et le message du serveur sont traduits séparément.
        assertEquals("Backup to the server failed: Access denied: this account is not allowed to write to this folder",
            en("Sauvegarde sur le serveur impossible : Accès refusé : ce compte n'a pas le droit d'écrire dans ce dossier"))
        assertEquals("Server saved: \\\\nas\\Partage", en("Serveur enregistré : \\\\nas\\Partage"))
        assertEquals("Connection successful. 3 backup(s) on the server.", en("Connexion réussie. 3 sauvegardes sur le serveur."))
    }

    @Test
    fun textesVouvoyesTraduits() {
        val fr = listOf(
            "Sélectionnez un 2e trajet pour fusionner", "Choisissez une autre période pour retrouver vos trajets.",
            "Connectez le boîtier OBD2 pour vérifier.", "Celles de votre fond d'écran (Material You), ou une palette fixe",
            "Au moment de sauvegarder, vous choisissez la protection : aucune ou chiffrée par CarDeck.",
            "Mettez le contact. CarDeck interroge le véhicule pour savoir quelles données il fournit.",
            "Content de vous revoir. On démarre ?", "Patinages", "Autre véhicule", "Pistes de décodage en roulant", "Modifier le véhicule", "Changer de modèle", "Changer de boîtier", "Dissocier le boîtier ?", "Modèle modifié",
            "Ma Swift n'aura plus de boîtier OBD2 : plus de connexion ni d'enregistrement automatique des trajets tant qu'un autre n'est pas associé.",
            "Explorez Ma Swift : l'explorateur découvre tous ses calculateurs et tout ce qu'ils acceptent de donner, puis vous pouvez exporter les résultats. Voiture à l'arrêt, c'est le balayage complet ; voiture qui roule, c'est l'enregistrement en roulant.",
            "🔎 route/2026-11-26_10-27 : 12 piste(s) de décodage (PISTES.md)", "ℹ️ Ligne K 0x03 : réveil à 0x03, messages à 0x28, réponses de 0x28",
            "Recherche de l'adresse des messages de 0x03 · 0x28 (1/20)",
        )
        for (l in Lang.entries.drop(1)) {
            I18n.lang = l
            val missing = fr.filter { tr(it) == it }
            assertTrue("${l.code} : $missing", missing.isEmpty())
        }
    }

    @Test
    fun langueDuTelephone() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY); assertEquals(Lang.De, Lang.system())
            java.util.Locale.setDefault(java.util.Locale.JAPAN); assertEquals(Lang.En, Lang.system())
            java.util.Locale.setDefault(java.util.Locale.FRANCE); assertEquals(Lang.Fr, Lang.system())
        } finally { java.util.Locale.setDefault(saved) }
    }
}
