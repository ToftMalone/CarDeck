package com.cardeck.app

import com.cardeck.app.data.CATALOG
import com.cardeck.app.data.CATALOG_TEMPLATES
import com.cardeck.app.data.OTHER
import org.junit.Assert.assertNull
import com.cardeck.app.data.SWIFT_SPORT_ZC33S
import com.cardeck.app.data.TEMPLATES
import com.cardeck.app.data.searchCatalog
import com.cardeck.app.data.templateById
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test
    fun identifiantsUniques() {
        val ids = TEMPLATES.map { it.id }
        val dup = ids.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("Identifiants en double : $dup", dup.isEmpty())
    }

    @Test
    fun marquesTriees() {
        val names = CATALOG.map { it.name }
        assertEquals(OTHER, names.last())
        assertEquals(names.dropLast(1).sortedBy { it.lowercase() }, names.dropLast(1))
        assertTrue(CATALOG.all { b -> b.models.isNotEmpty() && b.models.all { m -> m.generations.all { it.variants.isNotEmpty() } } })
    }

    @Test
    fun swiftSportDansSuzukiSwift() {
        val swift = CATALOG.first { it.name == "Suzuki" }.models.first { it.name == "Swift" }
        assertTrue(swift.generations.any { g -> SWIFT_SPORT_ZC33S in g.variants })
        assertEquals(SWIFT_SPORT_ZC33S, templateById(SWIFT_SPORT_ZC33S.id))
        assertTrue(CATALOG_TEMPLATES.isNotEmpty())
    }

    @Test
    fun recherche() {
        assertTrue(searchCatalog("swift sport").any { it.template == SWIFT_SPORT_ZC33S })
        assertTrue(searchCatalog("clio v tce").all { it.model.name == "Clio" && it.generation.name == "V" })
        assertTrue(searchCatalog("mégane rs").isNotEmpty())
        assertTrue(searchCatalog("megane RS").isNotEmpty())
        assertTrue(searchCatalog("zzzz").isEmpty())
    }

    @Test
    fun retrouveParIdentifiant() {
        CATALOG_TEMPLATES.forEach { assertEquals(it, templateById(it.id)) }
    }

    // ------------------------------------------------------------ Reconnaissance par le VIN

    @Test
    fun vinNormalise() {
        assertEquals("VF15RJ00000000001".length, 17)
        assertEquals("VF15RJ00000000001", com.cardeck.app.data.Vin.normalize("vf1 5rj00000000001"))
        assertNull(com.cardeck.app.data.Vin.normalize("VF15RJ0000000000I")) // I interdit
        assertNull(com.cardeck.app.data.Vin.normalize("VF15RJ"))
        assertNull(com.cardeck.app.data.Vin.normalize(null))
    }

    @Test
    fun vinConstructeurEtAnnee() {
        // Renault ne code pas l'année au 10e caractère : aucune année n'est déduite.
        val renault = com.cardeck.app.data.Vin.decode("VF15RJ000K0000001", currentYear = 2026)!!
        assertEquals("Renault", renault.brand)
        assertNull(renault.year)
        assertNull(renault.templateId)
        // Constructeurs qui la codent : Volkswagen (ci-dessous), Kia, Hyundai, VIN nord-américains.
        assertEquals(2019, com.cardeck.app.data.Vin.decode("KNADB512AK6123456", 2026)!!.year)
        assertEquals(2019, com.cardeck.app.data.Vin.decode("1FADP3F20KL123456", 2026)!!.year)
        assertEquals("Peugeot", com.cardeck.app.data.Vin.decode("VF3ABCDEFK1234567", 2026)!!.brand)
        assertEquals("Citroën", com.cardeck.app.data.Vin.decode("VF7ABCDEFK1234567", 2026)!!.brand)
        assertEquals("Volkswagen", com.cardeck.app.data.Vin.decode("WVWZZZ1KZ6W123456", 2026)!!.brand)
        assertEquals(2006, com.cardeck.app.data.Vin.decode("WVWZZZ1KZ6W123456", 2026)!!.year)
        // Constructeur connu mais absent du catalogue : nom du constructeur, pas de marque.
        val tesla = com.cardeck.app.data.Vin.decode("5YJ3E1EA7KF317000", 2026)!!
        assertEquals("Tesla", tesla.maker)
        assertNull(tesla.brand)
        // Constructeur inconnu.
        assertNull(com.cardeck.app.data.Vin.decode("ZZZ3E1EA7KF317000", 2026)!!.maker)
    }

    @Test
    fun vinAnneeModele() {
        val y = { c: Char -> com.cardeck.app.data.Vin.modelYear("VF15RJ000${c}0000001", 2026) }
        assertEquals(2010, y('A'))
        assertEquals(2018, y('J'))
        assertEquals(2019, y('K'))
        assertEquals(2026, y('T'))
        assertEquals(2027, y('V')) // l'année suivante est plausible (millésime)
        assertEquals(2005, y('5'))
        assertEquals(2001, y('1'))
        assertNull(y('0'))
        // Un caractère trop récent pour 2026 retombe sur le cycle précédent.
        assertEquals(1981, com.cardeck.app.data.Vin.modelYear("VF15RJ000B0000001", 2000))
    }

    @Test
    fun vinSwiftSportReconnue() {
        val zc33 = com.cardeck.app.data.Vin.decode("TSMZC33S0K0123456", 2026)!!
        assertEquals("Suzuki", zc33.brand)
        assertEquals(com.cardeck.app.data.SWIFT_SPORT_ZC33S.id, zc33.templateId)
        assertNull(zc33.year) // Suzuki Hongrie : pas d'année au 10e caractère
        val zc32 = com.cardeck.app.data.Vin.decode("TSMZC32S0E0123456", 2026)!!
        assertTrue(templateById(zc32.templateId!!).fullName.contains("ZC32S"))
        // Autre code de modèle chez Suzuki : on garde la marque, sans deviner le modèle.
        assertNull(com.cardeck.app.data.Vin.decode("TSMZC83S0K0123456", 2026)!!.templateId)
    }

    @Test
    fun vinMarquesDuCatalogue() {
        val names = CATALOG.map { it.name }.toSet()
        assertTrue("Marques absentes du catalogue : ${com.cardeck.app.data.Vin.catalogBrands - names}", names.containsAll(com.cardeck.app.data.Vin.catalogBrands))
        // Toutes les marques « réelles » du catalogue sont reconnaissables par un VIN.
        val reconnaissables = com.cardeck.app.data.Vin.catalogBrands
        assertEquals(emptySet<String>(), names - reconnaissables - OTHER)
    }

    @Test
    fun vinModelesDeLAnnee() {
        val suzuki = CATALOG.first { it.name == "Suzuki" }
        val c2019 = com.cardeck.app.data.Vin.candidates(suzuki, 2019)
        assertTrue(c2019.any { it.first.name == "Swift" && it.second.any { g -> g.name == "V" } })
        assertTrue(c2019.none { it.first.name == "Swift" && it.second.any { g -> g.name == "IV" } })
        // Tolérance d'un an sur l'année modèle.
        assertTrue(com.cardeck.app.data.Vin.candidates(suzuki, 2025).any { it.first.name == "Swift" })
        assertEquals(2012 to 2017, com.cardeck.app.data.Vin.years(suzuki.models.first().generations.first { it.name == "Sport ZC32S" }))
        assertEquals(2018 to 2024, com.cardeck.app.data.Vin.years(suzuki.models.first().generations.first { it.name == "Sport ZC33S" }))
    }

    // ------------------------------------------------------------ Base de codes défaut

    @Test
    fun codesGeneriquesP0() {
        val p0 = com.cardeck.app.data.DtcCatalog.all.filter { Regex("P0\\d{3}").matches(it.code) }
        assertTrue("Seulement ${p0.size} codes P0xxx", p0.size >= 950)
        // Codes courants, avec leur sens.
        val titre = { c: String -> com.cardeck.app.data.DtcCatalog.info(c).title.lowercase() }
        assertTrue(titre("P0133").contains("réponse lente"))
        assertTrue(titre("P0141").contains("banc 1, sonde 2") && titre("P0141").contains("chauffage"))
        assertTrue(titre("P0171").contains("pauvre") && titre("P0172").contains("riche"))
        assertTrue(titre("P0174").contains("banc 2"))
        assertTrue(titre("P0420").contains("catalyseur"))
        assertTrue(titre("P0507").contains("supérieur"))
        assertTrue(titre("P0562").contains("basse") && titre("P0563").contains("tension"))
        assertTrue(titre("P0715").contains("vitesse"))
        assertTrue(titre("P0741").contains("convertisseur"))
        assertTrue(titre("P0301").contains("cylindre 1") && titre("P0312").contains("cylindre 12"))
        assertTrue(titre("P0515").contains("batterie"))
        // Les fiches détaillées écrites à la main restent prioritaires (avec causes et recommandations).
        assertTrue(com.cardeck.app.data.DtcCatalog.info("P0420").causes.isNotEmpty())
        // Gravité : un raté d'allumage est critique, une fuite EVAP mineure.
        assertEquals(com.cardeck.app.data.Severity.Crit, com.cardeck.app.data.DtcCatalog.info("P0302").sev)
        assertEquals(com.cardeck.app.data.Severity.Low, com.cardeck.app.data.DtcCatalog.info("P0442").sev)
    }

    @Test
    fun codesGeneriquesBienFormes() {
        val lines = CatalogTest::class.java.classLoader!!.getResourceAsStream("dtc_p0.txt")!!.bufferedReader().readLines().filter { it.isNotBlank() }
        val parsed = com.cardeck.app.data.DtcCatalog.parseGeneric(lines.joinToString("\n"))
        assertEquals("Lignes invalides", lines.size, parsed.size)
        assertEquals("Doublons", lines.size, parsed.map { it.code }.toSet().size)
        assertTrue(parsed.all { it.code.matches(Regex("P0[0-9A-F]{3}")) && it.title.length > 8 })
        // Les numéros suivent la norme : chiffres décimaux uniquement dans P00xx à P09xx.
        assertTrue(parsed.filter { it.code[2] in '0'..'9' && it.code[3] in '0'..'9' }.all { it.code.drop(1).all(Char::isDigit) })
    }

    // ------------------------------------------------------------ Clio V (relevé de l'explorateur)

    @Test
    fun vinClioV() {
        // VIN fictif au format relevé sur une Clio V (VF1 RJA …) ; le 10e caractère n'est pas une année chez Renault.
        val v = com.cardeck.app.data.Vin.decode("VF1RJA00512345678", 2026)!!
        assertEquals("Renault", v.brand)
        assertEquals("Clio", v.model)
        assertEquals("V", v.generation)
        assertNull(v.templateId) // plusieurs motorisations : on laisse choisir
        assertNull(v.year)
        assertNull(com.cardeck.app.data.Vin.decode("VF1RJB00512345678", 2026)!!.model) // autre code : pas deviné
    }

    @Test
    fun profilClioV() {
        val clio = com.cardeck.app.data.CLIO_V_TCE90
        // Même identifiant que la motorisation du catalogue : le profil dédié la remplace, à sa place.
        val gen = CATALOG.first { it.name == "Renault" }.models.first { it.name == "Clio" }.generations.first { it.name == "V" }
        assertTrue(clio in gen.variants)
        assertEquals(gen.variants.size, gen.variants.map { it.id }.toSet().size)
        assertEquals(clio, templateById("renault-clio-v-1-0-tce-90ch-essence"))
        assertTrue(clio.dedicated)
        assertEquals(mapOf("18DAF115" to "Moteur", "18DAF12D" to "ABS / ESP"), clio.ecuNames)
        assertEquals(15, clio.ecus.size)
        assertEquals(1, TEMPLATES.count { it.id == clio.id })
    }

    @Test
    fun listeApresEffacement() {
        val f = { c: String, ecu: String, st: com.cardeck.app.data.DtcStatus -> com.cardeck.app.data.FoundCode(c, st, ecu) }
        val S = com.cardeck.app.data.DtcStatus.Stored
        val codes = listOf(
            f("P0515", "Moteur", S), f("P0420", "Moteur", com.cardeck.app.data.DtcStatus.Permanent),
            f("C106E", "ABS / ESP", S), f("B1F34", "Calculateur 714", S),
        )
        val clear = com.cardeck.app.data.ClearedCodes::remaining
        // Simple : seuls les codes OBD (hors permanents) disparaissent.
        assertEquals(listOf("P0420", "C106E", "B1F34"), clear(codes, setOf("Moteur"), true, emptySet()).map { it.code })
        // Complet : ABS effacé, 714 a refusé.
        assertEquals(listOf("P0420", "B1F34"), clear(codes, setOf("Moteur"), true, setOf("ABS / ESP")).map { it.code })
        // Effacement OBD refusé : rien ne disparaît côté moteur.
        assertEquals(4, clear(codes, setOf("Moteur"), false, emptySet()).size)
        // Après un redémarrage de l'appli (calculateurs OBD inconnus) : devinés d'après leur nom.
        assertEquals(listOf("P0420", "C106E", "B1F34"), clear(codes, emptySet(), true, emptySet()).map { it.code })
    }
}
