package com.cardeck.app

import com.cardeck.app.data.CATALOG
import com.cardeck.app.data.CATALOG_TEMPLATES
import com.cardeck.app.data.OTHER
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
}
