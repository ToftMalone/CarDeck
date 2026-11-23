package com.cardeck.app.data

import java.text.Normalizer

/**
 * Catalogue des véhicules connus, pour la sélection Marque › Modèle › Génération › Motorisation.
 * Les véhicules sans profil dédié utilisent les données OBD2 standard (protocole détecté automatiquement).
 * Les identifiants des motorisations sont dérivés des libellés : ne pas renommer une entrée existante
 * (les profils déjà créés la retrouvent par son identifiant).
 */
data class CatalogBrand(val name: String, val models: List<CatalogModel>)

data class CatalogModel(val name: String, val generations: List<CatalogGeneration>)

/** Génération (ou version) d'un modèle : [name] = « V », « 8 », « Sport ZC33S »… ; [years] = « 2019– ». */
data class CatalogGeneration(val name: String, val years: String, val variants: List<VehicleTemplate>)

/** Énergie d'une motorisation. */
enum class Energy(val label: String, val fuel: FuelType) {
    Essence("Essence", FuelType.E10),
    Diesel("Diesel", FuelType.Gazole),
    Hybride("Hybride essence", FuelType.E10),
    Rechargeable("Hybride rechargeable", FuelType.E10),
    Gpl("Essence / GPL", FuelType.GPLc),
    E85("Superéthanol E85", FuelType.E85),
}

private val P = Energy.Essence
private val D = Energy.Diesel
private val H = Energy.Hybride
private val R = Energy.Rechargeable
private val G = Energy.Gpl
private val F = Energy.E85

/** Marque regroupant les véhicules absents de la liste (OBD2 générique). */
const val OTHER = "Autre véhicule"

internal fun slug(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')

private class Engine(val name: String, val hp: Int, val energy: Energy)

private class GenBuilder(val name: String, val years: String) {
    val engines = mutableListOf<Engine>()
    val dedicated = mutableListOf<VehicleTemplate>()

    /** Motorisation : libellé, puissance (ch), énergie. */
    fun e(name: String, hp: Int, energy: Energy) { engines += Engine(name, hp, energy) }
}

private class ModelBuilder(val name: String) {
    val gens = mutableListOf<GenBuilder>()

    fun gen(name: String, years: String, block: GenBuilder.() -> Unit) { gens += GenBuilder(name, years).apply(block) }
}

private class BrandBuilder(val name: String) {
    val models = mutableListOf<ModelBuilder>()

    fun model(name: String, block: ModelBuilder.() -> Unit) { models += ModelBuilder(name).apply(block) }
}

private fun generic(brand: String, model: String, gen: GenBuilder, e: Engine) = VehicleTemplate(
    id = slug("$brand $model ${gen.name} ${e.name} ${e.hp}ch ${e.energy.name}"),
    brand = if (brand == OTHER) "" else brand,
    model = if (brand == OTHER) "Véhicule OBD2" else model,
    code = gen.name,
    engine = e.name,
    power = if (e.hp > 0) "${e.hp} ch" else "",
    fuel = e.energy.label,
    defaultFuel = e.energy.fuel,
    protocol = "OBD2 standard · protocole détecté automatiquement",
    pids = emptyList(),
)

/** Clé de tri « naturelle » : sans accents ni majuscules, nombres comparés comme des nombres (« 2008 » après « 208 », « Série 2 » avant « Série 10 »). */
internal fun sortKey(s: String): List<Comparable<*>> =
    Regex("\\d+|\\D+").findAll(slug(s).replace('-', ' ')).map { m -> m.value.toIntOrNull() ?: m.value }.toList()

internal val NATURAL: Comparator<String> = Comparator { a, b ->
    val x = sortKey(a)
    val y = sortKey(b)
    var r = 0
    for (i in 0 until minOf(x.size, y.size)) {
        val p = x[i]
        val q = y[i]
        r = when {
            p is Int && q is Int -> p.compareTo(q)
            p is Int -> -1 // un nombre avant du texte
            q is Int -> 1
            else -> (p as String).compareTo(q as String)
        }
        if (r != 0) break
    }
    if (r != 0) r else x.size.compareTo(y.size)
}

private fun brand(name: String, block: BrandBuilder.() -> Unit): CatalogBrand {
    val b = BrandBuilder(name).apply(block)
    return CatalogBrand(
        b.name,
        // Modèles par ordre alphabétique, générations de la plus ancienne à la plus récente (sans année : à la fin).
        b.models.sortedWith(compareBy(NATURAL) { it.name }).map { m ->
            // Une motorisation qui a un profil dédié (même identifiant) est remplacée par lui, à la même place dans la liste.
            CatalogModel(m.name, m.gens.sortedBy { g -> Regex("\\d{4}").find(g.years)?.value?.toInt() ?: Int.MAX_VALUE }
                .map { g -> CatalogGeneration(g.name, g.years, g.dedicated + g.engines.map { generic(b.name, m.name, g, it) }.map { t -> DEDICATED.firstOrNull { it.id == t.id } ?: t }) })
        },
    )
}

/** Toutes les marques, par ordre alphabétique ; « Autre véhicule » en dernier. */
val CATALOG: List<CatalogBrand> by lazy {
    listOf(
        brand("Alfa Romeo") {
            model("147") {
                gen("147", "2000–2010") { e("1.6 TS", 105, P); e("1.6 TS", 120, P); e("2.0 TS", 150, P); e("3.2 V6 GTA", 250, P); e("1.9 JTD", 115, D); e("1.9 JTDm", 150, D) }
            }
            model("159") {
                gen("159", "2005–2011") { e("1.9 JTS", 160, P); e("2.2 JTS", 185, P); e("1750 TBi", 200, P); e("1.9 JTDm", 120, D); e("1.9 JTDm", 150, D); e("2.0 JTDm", 170, D); e("2.4 JTDm", 200, D) }
            }
            model("MiTo") {
                gen("MiTo", "2008–2018") { e("1.4", 78, P); e("1.4 TB", 120, P); e("1.4 MultiAir", 135, P); e("1.4 MultiAir QV", 170, P); e("1.3 JTDm", 85, D); e("1.3 JTDm", 95, D) }
            }
            model("Giulietta") {
                gen("Giulietta", "2010–2020") { e("1.4 TB", 120, P); e("1.4 MultiAir", 150, P); e("1.4 MultiAir", 170, P); e("1750 TBi", 240, P); e("1.6 JTDm", 120, D); e("2.0 JTDm", 150, D); e("2.0 JTDm", 175, D) }
            }
            model("Giulia") {
                gen("Giulia", "2016–") { e("2.0 Turbo", 200, P); e("2.0 Turbo", 280, P); e("2.9 V6 Quadrifoglio", 510, P); e("2.2 Diesel", 160, D); e("2.2 Diesel", 190, D); e("2.2 Diesel", 210, D) }
            }
            model("Stelvio") {
                gen("Stelvio", "2017–") { e("2.0 Turbo", 200, P); e("2.0 Turbo", 280, P); e("2.9 V6 Quadrifoglio", 510, P); e("2.2 Diesel", 160, D); e("2.2 Diesel", 190, D); e("2.2 Diesel", 210, D) }
            }
            model("Tonale") {
                gen("Tonale", "2022–") { e("1.5 Hybrid", 130, H); e("1.5 Hybrid", 160, H); e("1.3 Plug-in Q4", 280, R); e("1.6 Diesel", 130, D) }
            }
        },
        brand("Audi") {
            model("A1") {
                gen("8X", "2010–2018") { e("1.0 TFSI", 95, P); e("1.2 TFSI", 86, P); e("1.4 TFSI", 122, P); e("1.4 TFSI", 150, P); e("1.4 TFSI", 185, P); e("2.0 TFSI S1", 231, P); e("1.6 TDI", 90, D); e("1.6 TDI", 105, D); e("1.6 TDI", 116, D) }
                gen("GB", "2018–") { e("25 TFSI", 95, P); e("30 TFSI", 110, P); e("35 TFSI", 150, P); e("40 TFSI", 200, P) }
            }
            model("A3") {
                gen("8P", "2003–2012") { e("1.6", 102, P); e("1.4 TFSI", 125, P); e("1.8 TFSI", 160, P); e("2.0 TFSI", 200, P); e("1.9 TDI", 105, D); e("1.6 TDI", 105, D); e("2.0 TDI", 140, D); e("2.0 TDI", 170, D) }
                gen("8V", "2012–2020") { e("1.0 TFSI", 116, P); e("1.2 TFSI", 110, P); e("1.4 TFSI", 125, P); e("1.4 TFSI", 150, P); e("1.5 TFSI", 150, P); e("1.8 TFSI", 180, P); e("2.0 TFSI S3", 310, P); e("1.6 TDI", 110, D); e("2.0 TDI", 150, D); e("2.0 TDI", 184, D); e("1.4 TFSI e-tron", 204, R) }
                gen("8Y", "2020–") { e("30 TFSI", 110, P); e("35 TFSI", 150, P); e("S3 2.0 TFSI", 310, P); e("30 TDI", 116, D); e("35 TDI", 150, D); e("40 TFSI e", 204, R) }
            }
            model("A4") {
                gen("B8", "2008–2015") { e("1.8 TFSI", 120, P); e("1.8 TFSI", 170, P); e("2.0 TFSI", 211, P); e("2.0 TDI", 143, D); e("2.0 TDI", 177, D); e("3.0 TDI", 245, D) }
                gen("B9", "2015–2024") { e("35 TFSI", 150, P); e("40 TFSI", 204, P); e("45 TFSI", 265, P); e("30 TDI", 136, D); e("35 TDI", 163, D); e("40 TDI", 204, D); e("50 TDI", 286, D) }
            }
            model("A5") {
                gen("8T", "2007–2016") { e("1.8 TFSI", 170, P); e("2.0 TFSI", 211, P); e("2.0 TDI", 177, D); e("3.0 TDI", 245, D) }
                gen("F5", "2016–2024") { e("40 TFSI", 204, P); e("45 TFSI", 265, P); e("35 TDI", 163, D); e("40 TDI", 204, D) }
            }
            model("A6") {
                gen("C7", "2011–2018") { e("2.0 TFSI", 252, P); e("2.0 TDI", 190, D); e("3.0 TDI", 218, D); e("3.0 TDI", 272, D) }
                gen("C8", "2018–") { e("45 TFSI", 245, P); e("40 TDI", 204, D); e("50 TDI", 286, D); e("55 TFSI e", 367, R) }
            }
            model("Q2") {
                gen("GA", "2016–") { e("30 TFSI", 116, P); e("35 TFSI", 150, P); e("SQ2 2.0 TFSI", 300, P); e("30 TDI", 116, D); e("35 TDI", 150, D) }
            }
            model("Q3") {
                gen("F3", "2018–") { e("35 TFSI", 150, P); e("40 TFSI", 190, P); e("35 TDI", 150, D); e("40 TDI", 200, D); e("45 TFSI e", 245, R) }
            }
            model("Q5") {
                gen("8R", "2008–2017") { e("2.0 TFSI", 211, P); e("2.0 TDI", 150, D); e("2.0 TDI", 177, D); e("3.0 TDI", 240, D) }
                gen("FY", "2017–") { e("40 TFSI", 204, P); e("40 TDI", 204, D); e("50 TFSI e", 299, R) }
            }
            model("TT") {
                gen("8J", "2006–2014") { e("1.8 TFSI", 160, P); e("2.0 TFSI", 200, P); e("2.5 TFSI RS", 340, P); e("2.0 TDI", 170, D) }
                gen("8S", "2014–2023") { e("1.8 TFSI", 180, P); e("2.0 TFSI", 230, P); e("2.0 TFSI S", 310, P); e("2.0 TDI", 184, D) }
            }
        },
        brand("BMW") {
            model("Série 1") {
                gen("E87", "2004–2011") { e("116i", 122, P); e("118i", 143, P); e("120i", 170, P); e("118d", 143, D); e("120d", 177, D) }
                gen("F20", "2011–2019") { e("116i", 136, P); e("118i", 136, P); e("120i", 184, P); e("M135i", 326, P); e("M140i", 340, P); e("116d", 116, D); e("118d", 150, D); e("120d", 190, D) }
                gen("F40", "2019–2024") { e("118i", 136, P); e("128ti", 265, P); e("M135i xDrive", 306, P); e("116d", 116, D); e("118d", 150, D); e("120d", 190, D) }
            }
            model("Série 3") {
                gen("E90", "2005–2012") { e("318i", 143, P); e("320i", 170, P); e("325i", 218, P); e("330i", 272, P); e("318d", 143, D); e("320d", 184, D); e("330d", 245, D) }
                gen("F30", "2012–2019") { e("316i", 136, P); e("320i", 184, P); e("330i", 252, P); e("335i", 306, P); e("316d", 116, D); e("318d", 150, D); e("320d", 190, D); e("330d", 258, D); e("330e", 252, R) }
                gen("G20", "2019–") { e("318i", 156, P); e("320i", 184, P); e("330i", 258, P); e("M340i", 374, P); e("318d", 150, D); e("320d", 190, D); e("330d", 286, D); e("330e", 292, R) }
            }
            model("Série 2") {
                gen("F22 Coupé", "2014–2021") { e("218i", 136, P); e("220i", 184, P); e("M240i", 340, P); e("218d", 150, D); e("220d", 190, D) }
                gen("F45 Active Tourer", "2014–2021") { e("218i", 136, P); e("216d", 116, D); e("218d", 150, D); e("225xe", 224, R) }
            }
            model("Série 5") {
                gen("F10", "2010–2017") { e("520i", 184, P); e("528i", 245, P); e("535i", 306, P); e("520d", 184, D); e("525d", 218, D); e("530d", 258, D) }
                gen("G30", "2017–2023") { e("520i", 184, P); e("530i", 252, P); e("540i", 340, P); e("520d", 190, D); e("530d", 265, D); e("530e", 252, R) }
            }
            model("X1") {
                gen("E84", "2009–2015") { e("sDrive18i", 150, P); e("sDrive20i", 184, P); e("sDrive18d", 143, D); e("xDrive20d", 184, D) }
                gen("F48", "2015–2022") { e("sDrive18i", 140, P); e("sDrive20i", 192, P); e("sDrive18d", 150, D); e("xDrive20d", 190, D); e("xDrive25e", 220, R) }
            }
            model("X3") {
                gen("F25", "2010–2017") { e("xDrive20i", 184, P); e("xDrive28i", 245, P); e("sDrive18d", 150, D); e("xDrive20d", 190, D); e("xDrive30d", 258, D) }
                gen("G01", "2017–2024") { e("xDrive20i", 184, P); e("xDrive30i", 252, P); e("M40i", 360, P); e("xDrive20d", 190, D); e("xDrive30d", 286, D); e("xDrive30e", 292, R) }
            }
        },
        brand("BYD") {
            model("Seal U DM-i") {
                gen("I", "2024–") { e("1.5 DM-i", 218, R); e("1.5 DM-i AWD", 324, R) }
            }
            model("Seal 6 DM-i") {
                gen("I", "2025–") { e("1.5 DM-i", 212, R) }
            }
        },
        brand("Cadillac") {
            model("ATS") {
                gen("I", "2013–2019") { e("2.0T", 276, P) }
            }
            model("Escalade") {
                gen("IV", "2015–2020") { e("6.2 V8", 426, P) }
            }
        },
        brand("Chevrolet") {
            model("Spark") {
                gen("M300", "2010–2015") { e("1.0", 68, P); e("1.2", 82, P) }
            }
            model("Aveo") {
                gen("T300", "2011–2015") { e("1.2", 86, P); e("1.4", 100, P); e("1.3 VCDi", 95, D) }
            }
            model("Cruze") {
                gen("J300", "2009–2015") { e("1.6", 124, P); e("1.8", 141, P); e("2.0 VCDi", 163, D) }
            }
            model("Captiva") {
                gen("C140", "2011–2015") { e("2.4", 167, P); e("2.2 VCDi", 163, D); e("2.2 VCDi", 184, D) }
            }
        },
        brand("Chrysler") {
            model("300C") {
                gen("II", "2011–2015") { e("3.6 V6", 286, P); e("3.0 V6 CRD", 239, D) }
            }
            model("Grand Voyager") {
                gen("V", "2008–2016") { e("3.6 V6", 283, P); e("2.8 CRD", 163, D) }
            }
        },
        brand("Citroën") {
            model("C2") {
                gen("I", "2003–2009") { e("1.1", 60, P); e("1.4", 75, P); e("1.6 VTS", 125, P); e("1.4 HDi", 70, D) }
            }
            model("Saxo") {
                gen("Saxo", "1996–2003") { e("1.0", 50, P); e("1.1", 60, P); e("1.4", 75, P); e("1.6 VTS", 120, P); e("1.5 D", 57, D) }
            }
            model("Xsara") {
                gen("Xsara", "1997–2006") { e("1.4", 75, P); e("1.6 16V", 110, P); e("2.0 VTS", 167, P); e("1.9 D", 70, D); e("2.0 HDi", 90, D) }
            }
            model("C1") {
                gen("I", "2005–2014") { e("1.0", 68, P); e("1.4 HDi", 54, D) }
                gen("II", "2014–2022") { e("1.0 VTi", 72, P); e("1.2 PureTech", 82, P) }
            }
            model("C3") {
                gen("I", "2002–2009") { e("1.1", 60, P); e("1.4", 75, P); e("1.6 16V", 110, P); e("1.4 HDi", 70, D); e("1.6 HDi", 90, D) }
                gen("II", "2009–2016") { e("1.1", 60, P); e("1.4 VTi", 95, P); e("1.6 VTi", 120, P); e("1.2 PureTech", 82, P); e("1.4 HDi", 70, D); e("1.6 HDi", 90, D); e("1.6 BlueHDi", 100, D) }
                gen("III", "2016–2024") { e("1.2 PureTech", 68, P); e("1.2 PureTech", 83, P); e("1.2 PureTech", 110, P); e("1.6 BlueHDi", 75, D); e("1.5 BlueHDi", 100, D) }
            }
            model("C3 Aircross") {
                gen("I", "2017–2024") { e("1.2 PureTech", 82, P); e("1.2 PureTech", 110, P); e("1.2 PureTech", 130, P); e("1.5 BlueHDi", 110, D); e("1.5 BlueHDi", 120, D) }
            }
            model("C4") {
                gen("I", "2004–2010") { e("1.4", 90, P); e("1.6 16V", 110, P); e("2.0 VTS", 180, P); e("1.6 HDi", 90, D); e("1.6 HDi", 110, D); e("2.0 HDi", 136, D) }
                gen("II", "2010–2018") { e("1.4 VTi", 95, P); e("1.6 VTi", 120, P); e("1.6 THP", 155, P); e("1.2 PureTech", 130, P); e("1.6 HDi", 92, D); e("1.6 e-HDi", 115, D); e("1.6 BlueHDi", 120, D) }
                gen("III", "2020–") { e("1.2 PureTech", 100, P); e("1.2 PureTech", 130, P); e("1.2 PureTech", 155, P); e("1.5 BlueHDi", 110, D); e("1.5 BlueHDi", 130, D); e("1.2 Hybrid", 136, H) }
            }
            model("C4 Cactus") {
                gen("I", "2014–2020") { e("1.2 PureTech", 82, P); e("1.2 PureTech", 110, P); e("1.2 PureTech", 130, P); e("1.6 BlueHDi", 100, D); e("1.5 BlueHDi", 120, D) }
            }
            model("C4 Picasso") {
                gen("I", "2006–2013") { e("1.8 16V", 125, P); e("1.6 THP", 155, P); e("1.6 HDi", 110, D); e("2.0 HDi", 150, D) }
                gen("II", "2013–2018") { e("1.2 PureTech", 130, P); e("1.6 THP", 165, P); e("1.6 BlueHDi", 120, D); e("2.0 BlueHDi", 150, D) }
            }
            model("C5") {
                gen("X7", "2008–2017") { e("1.6 VTi", 120, P); e("1.6 THP", 156, P); e("1.6 HDi", 110, D); e("2.0 HDi", 140, D); e("2.0 HDi", 163, D); e("2.2 HDi", 204, D) }
                gen("X", "2021–") { e("1.2 PureTech", 130, P); e("1.6 Hybrid", 225, R) }
            }
            model("C5 Aircross") {
                gen("I", "2018–") { e("1.2 PureTech", 130, P); e("1.6 PureTech", 180, P); e("1.5 BlueHDi", 130, D); e("2.0 BlueHDi", 180, D); e("1.6 Hybrid", 225, R) }
            }
            model("Berlingo") {
                gen("I", "1996–2008") { e("1.4", 75, P); e("1.6 16V", 110, P); e("1.9 D", 70, D); e("2.0 HDi", 90, D) }
                gen("II", "2008–2018") { e("1.6 VTi", 120, P); e("1.6 HDi", 75, D); e("1.6 HDi", 90, D); e("1.6 BlueHDi", 100, D); e("1.6 BlueHDi", 120, D) }
                gen("III", "2018–") { e("1.2 PureTech", 110, P); e("1.5 BlueHDi", 75, D); e("1.5 BlueHDi", 100, D); e("1.5 BlueHDi", 130, D) }
            }
            model("Jumpy") {
                gen("III", "2016–") { e("1.5 BlueHDi", 120, D); e("2.0 BlueHDi", 145, D); e("2.0 BlueHDi", 180, D) }
            }
            model("Xsara Picasso") {
                gen("Xsara Picasso", "1999–2012") { e("1.6", 95, P); e("1.8 16V", 115, P); e("2.0 HDi", 90, D); e("1.6 HDi", 110, D) }
            }
        },
        brand("Dacia") {
            model("Sandero") {
                gen("I", "2008–2012") { e("1.2 16V", 75, P); e("1.4 MPI", 75, P); e("1.6 MPI", 90, P); e("1.5 dCi", 70, D); e("1.5 dCi", 85, D) }
                gen("II", "2012–2020") { e("1.2 16V", 75, P); e("1.0 SCe", 75, P); e("0.9 TCe", 90, P); e("0.9 TCe GPL", 90, G); e("1.5 dCi", 75, D); e("1.5 dCi", 90, D) }
                gen("III", "2020–") { e("1.0 SCe", 65, P); e("1.0 TCe", 90, P); e("1.0 TCe", 110, P); e("1.0 ECO-G", 100, G) }
            }
            model("Logan") {
                gen("I", "2004–2012") { e("1.4 MPI", 75, P); e("1.6 MPI", 90, P); e("1.6 16V", 105, P); e("1.5 dCi", 70, D); e("1.5 dCi", 85, D) }
                gen("II", "2012–2020") { e("1.2 16V", 75, P); e("0.9 TCe", 90, P); e("1.5 dCi", 90, D) }
            }
            model("Duster") {
                gen("I", "2010–2017") { e("1.6 16V", 105, P); e("1.6 SCe", 115, P); e("1.2 TCe", 125, P); e("1.5 dCi", 90, D); e("1.5 dCi", 110, D) }
                gen("II", "2017–2024") { e("1.6 SCe", 115, P); e("1.0 TCe", 100, P); e("1.0 ECO-G", 100, G); e("1.3 TCe", 130, P); e("1.3 TCe", 150, P); e("1.5 Blue dCi", 115, D) }
                gen("III", "2024–") { e("1.2 TCe mild hybrid", 130, P); e("1.0 ECO-G", 100, G); e("1.6 Hybrid", 140, H) }
            }
            model("Bigster") {
                gen("I", "2025–") { e("1.2 TCe mild hybrid", 140, P); e("1.2 ECO-G", 140, G); e("1.8 Hybrid", 155, H) }
            }
            model("Dokker") {
                gen("I", "2012–2021") { e("1.6 SCe", 102, P); e("1.2 TCe", 115, P); e("1.5 dCi", 75, D); e("1.5 dCi", 90, D) }
            }
            model("Jogger") {
                gen("I", "2022–") { e("1.0 TCe", 110, P); e("1.0 ECO-G", 100, G); e("1.6 Hybrid", 140, H) }
            }
            model("Lodgy") {
                gen("I", "2012–2022") { e("1.6 SCe", 102, P); e("1.2 TCe", 115, P); e("1.3 TCe", 130, P); e("1.5 dCi", 90, D); e("1.5 dCi", 110, D) }
            }
        },
        brand("Daihatsu") {
            model("Cuore") {
                gen("VII", "2003–2011") { e("1.0", 70, P) }
            }
            model("Materia") {
                gen("I", "2006–2011") { e("1.5", 103, P) }
            }
            model("Sirion") {
                gen("II", "2005–2013") { e("1.0", 69, P); e("1.3", 91, P) }
            }
            model("Terios") {
                gen("II", "2006–2013") { e("1.5", 105, P) }
            }
        },
        brand("Dodge") {
            model("Challenger") {
                gen("III", "2008–2023") { e("3.6 V6", 305, P); e("5.7 V8", 375, P); e("6.4 V8", 485, P) }
            }
            model("Charger") {
                gen("VII", "2011–2023") { e("3.6 V6", 300, P); e("5.7 V8", 370, P); e("6.4 V8", 485, P) }
            }
            model("Journey") {
                gen("I", "2008–2011") { e("2.4", 170, P); e("2.0 CRD", 140, D) }
            }
        },
        brand("DS") {
            model("DS 3") {
                gen("I", "2009–2019") { e("1.2 PureTech", 82, P); e("1.2 PureTech", 110, P); e("1.6 VTi", 120, P); e("1.6 THP", 165, P); e("1.6 THP Performance", 208, P); e("1.6 HDi", 92, D); e("1.6 BlueHDi", 120, D) }
            }
            model("DS 3 Crossback") {
                gen("I", "2019–2022") { e("1.2 PureTech", 100, P); e("1.2 PureTech", 130, P); e("1.2 PureTech", 155, P); e("1.5 BlueHDi", 110, D); e("1.5 BlueHDi", 130, D) }
            }
            model("DS 4") {
                gen("II", "2021–") { e("1.2 PureTech", 130, P); e("1.6 PureTech", 225, P); e("1.5 BlueHDi", 130, D); e("E-Tense", 225, R) }
            }
            model("DS 7") {
                gen("I", "2017–") { e("1.2 PureTech", 130, P); e("1.6 PureTech", 225, P); e("1.5 BlueHDi", 130, D); e("2.0 BlueHDi", 180, D); e("E-Tense", 225, R); e("E-Tense 4x4", 300, R) }
            }
        },
        brand("Ferrari") {
            model("458 Italia") {
                gen("458", "2009–2015") { e("4.5 V8", 570, P) }
            }
            model("488 GTB") {
                gen("488", "2015–2019") { e("3.9 V8", 670, P) }
            }
            model("F8 Tributo") {
                gen("F8", "2019–2023") { e("3.9 V8", 720, P) }
            }
            model("Portofino") {
                gen("I", "2017–2023") { e("3.9 V8", 600, P) }
            }
            model("Roma") {
                gen("I", "2020–") { e("3.9 V8", 620, P) }
            }
            model("812 Superfast") {
                gen("812", "2017–2023") { e("6.5 V12", 800, P) }
            }
        },
        brand("Fiat") {
            model("500") {
                gen("312", "2007–2024") { e("1.2 8V", 69, P); e("1.4 16V", 100, P); e("0.9 TwinAir", 85, P); e("1.0 Hybrid", 70, H); e("1.3 Multijet", 95, D) }
            }
            model("500 Abarth / 595") {
                gen("312", "2008–2024") { e("1.4 T-Jet", 135, P); e("1.4 T-Jet", 145, P); e("1.4 T-Jet", 165, P); e("1.4 T-Jet", 180, P) }
            }
            model("500X") {
                gen("334", "2014–") { e("1.6 E-torQ", 110, P); e("1.4 MultiAir", 140, P); e("1.0 FireFly", 120, P); e("1.3 FireFly", 150, P); e("1.5 Hybrid", 130, H); e("1.3 Multijet", 95, D); e("1.6 Multijet", 120, D) }
            }
            model("Doblò") {
                gen("II", "2010–2022") { e("1.4", 95, P); e("1.4 T-Jet", 120, P); e("1.3 Multijet", 90, D); e("1.6 Multijet", 105, D); e("2.0 Multijet", 135, D) }
            }
            model("Ducato") {
                gen("III", "2006–") { e("2.3 Multijet", 130, D); e("2.3 Multijet", 150, D); e("2.2 Multijet", 140, D); e("2.2 Multijet", 180, D) }
            }
            model("Panda") {
                gen("II", "2003–2012") { e("1.1", 54, P); e("1.2", 60, P); e("1.4 100HP", 100, P); e("1.3 Multijet", 75, D) }
                gen("III", "2012–") { e("1.2 8V", 69, P); e("0.9 TwinAir", 85, P); e("1.0 Hybrid", 70, H); e("1.3 Multijet", 80, D) }
            }
            model("Punto") {
                gen("Grande Punto / Evo", "2005–2018") { e("1.2 8V", 69, P); e("1.4 8V", 77, P); e("1.4 T-Jet", 120, P); e("1.3 Multijet", 75, D); e("1.3 Multijet", 95, D) }
            }
            model("Tipo") {
                gen("II", "2016–") { e("1.4 16V", 95, P); e("1.4 T-Jet", 120, P); e("1.0 FireFly", 100, P); e("1.5 Hybrid", 130, H); e("1.3 Multijet", 95, D); e("1.6 Multijet", 120, D) }
            }
        },
        brand("Ford") {
            model("B-Max") {
                gen("I", "2012–2017") { e("1.4", 90, P); e("1.0 EcoBoost", 100, P); e("1.0 EcoBoost", 120, P); e("1.5 TDCi", 75, D) }
            }
            model("Galaxy") {
                gen("III", "2015–2023") { e("2.5 Hybrid", 190, H); e("2.0 EcoBlue", 150, D) }
            }
            model("S-Max") {
                gen("I", "2006–2015") { e("2.0", 145, P); e("2.0 EcoBoost", 203, P); e("1.8 TDCi", 125, D); e("2.0 TDCi", 140, D); e("2.2 TDCi", 200, D) }
                gen("II", "2015–2023") { e("1.5 EcoBoost", 160, P); e("2.5 Hybrid", 190, H); e("2.0 TDCi", 150, D); e("2.0 TDCi", 180, D) }
            }
            model("Transit Connect") {
                gen("II", "2013–2022") { e("1.0 EcoBoost", 100, P); e("1.5 TDCi", 75, D); e("1.5 TDCi", 100, D); e("1.5 TDCi", 120, D) }
            }
            model("Fiesta") {
                gen("V", "2002–2008") { e("1.25", 75, P); e("1.4", 80, P); e("2.0 ST", 150, P); e("1.4 TDCi", 68, D); e("1.6 TDCi", 90, D) }
                gen("VI", "2008–2017") { e("1.25", 82, P); e("1.4", 96, P); e("1.6 Ti-VCT", 120, P); e("1.0 EcoBoost", 100, P); e("1.0 EcoBoost", 125, P); e("1.6 EcoBoost ST", 182, P); e("1.4 TDCi", 70, D); e("1.5 TDCi", 95, D); e("1.6 TDCi", 95, D) }
                gen("VII", "2017–2023") { e("1.1", 85, P); e("1.0 EcoBoost", 100, P); e("1.0 EcoBoost", 125, P); e("1.0 EcoBoost mHEV", 125, H); e("1.5 EcoBoost ST", 200, P); e("1.5 TDCi", 85, D) }
            }
            model("Focus") {
                gen("I", "1998–2004") { e("1.4", 75, P); e("1.6", 100, P); e("2.0 ST170", 173, P); e("1.8 TDDi", 90, D); e("1.8 TDCi", 115, D) }
                gen("II", "2004–2011") { e("1.6", 100, P); e("1.6 Ti-VCT", 115, P); e("1.8", 125, P); e("2.5 ST", 225, P); e("1.6 TDCi", 109, D); e("1.8 TDCi", 115, D); e("2.0 TDCi", 136, D); e("1.8 Flexifuel", 125, F) }
                gen("III", "2011–2018") { e("1.6 Ti-VCT", 105, P); e("1.0 EcoBoost", 100, P); e("1.0 EcoBoost", 125, P); e("1.5 EcoBoost", 150, P); e("2.0 EcoBoost ST", 250, P); e("2.3 EcoBoost RS", 350, P); e("1.5 TDCi", 120, D); e("1.6 TDCi", 115, D); e("2.0 TDCi", 150, D) }
                gen("IV", "2018–") { e("1.0 EcoBoost", 125, P); e("1.0 EcoBoost mHEV", 155, H); e("1.5 EcoBoost", 150, P); e("2.3 EcoBoost ST", 280, P); e("1.5 EcoBlue", 120, D); e("2.0 EcoBlue", 150, D) }
            }
            model("C-Max") {
                gen("II", "2010–2019") { e("1.6 Ti-VCT", 125, P); e("1.0 EcoBoost", 125, P); e("1.5 EcoBoost", 150, P); e("1.6 TDCi", 115, D); e("2.0 TDCi", 150, D) }
            }
            model("EcoSport") {
                gen("II", "2017–2022") { e("1.0 EcoBoost", 125, P); e("1.5 TDCi", 100, D) }
            }
            model("Ka / Ka+") {
                gen("II", "2008–2016") { e("1.2", 69, P); e("1.3 TDCi", 75, D) }
                gen("Ka+", "2016–2020") { e("1.2 Ti-VCT", 85, P); e("1.5 TDCi", 95, D) }
            }
            model("Mondeo") {
                gen("IV", "2007–2014") { e("2.0", 145, P); e("2.0 EcoBoost", 203, P); e("1.8 TDCi", 125, D); e("2.0 TDCi", 140, D) }
                gen("V", "2014–2022") { e("1.5 EcoBoost", 160, P); e("2.0 Hybrid", 187, H); e("2.0 TDCi", 150, D); e("2.0 TDCi", 180, D) }
            }
            model("Mustang") {
                gen("VI", "2015–2023") { e("2.3 EcoBoost", 317, P); e("5.0 V8", 450, P) }
            }
            model("Puma") {
                gen("II", "2019–") { e("1.0 EcoBoost", 125, P); e("1.0 EcoBoost mHEV", 125, H); e("1.0 EcoBoost mHEV", 155, H); e("1.5 EcoBoost ST", 200, P) }
            }
            model("Kuga") {
                gen("II", "2012–2019") { e("1.5 EcoBoost", 150, P); e("1.5 EcoBoost", 182, P); e("1.5 TDCi", 120, D); e("2.0 TDCi", 150, D); e("2.0 TDCi", 180, D) }
                gen("III", "2019–") { e("1.5 EcoBoost", 150, P); e("2.5 Hybrid", 190, H); e("2.5 PHEV", 225, R); e("1.5 EcoBlue", 120, D); e("2.0 EcoBlue mHEV", 150, D) }
            }
            model("Ranger") {
                gen("T6", "2011–2022") { e("2.2 TDCi", 160, D); e("3.2 TDCi", 200, D); e("2.0 EcoBlue", 213, D) }
            }
            model("Transit Custom") {
                gen("I", "2012–2023") { e("2.2 TDCi", 100, D); e("2.2 TDCi", 125, D); e("2.2 TDCi", 155, D); e("2.0 EcoBlue", 105, D); e("2.0 EcoBlue", 130, D); e("2.0 EcoBlue", 170, D) }
            }
        },
        brand("Honda") {
            model("Accord") {
                gen("VIII", "2008–2015") { e("2.0 i-VTEC", 156, P); e("2.4 i-VTEC", 201, P); e("2.2 i-DTEC", 150, D); e("2.2 i-DTEC", 180, D) }
            }
            model("CR-Z") {
                gen("I", "2010–2016") { e("1.5 Hybrid", 124, H) }
            }
            model("ZR-V") {
                gen("I", "2023–") { e("2.0 e:HEV", 184, H) }
            }
            model("Jazz") {
                gen("III", "2008–2015") { e("1.2 i-VTEC", 90, P); e("1.4 i-VTEC", 100, P); e("1.3 Hybrid", 88, H) }
                gen("IV", "2015–2020") { e("1.3 i-VTEC", 102, P) }
                gen("V", "2020–") { e("1.5 e:HEV", 109, H); e("1.5 e:HEV", 122, H) }
            }
            model("Civic") {
                gen("VII", "2001–2005") { e("1.4", 90, P); e("1.6", 110, P); e("2.0 Type R", 200, P); e("1.7 CTDi", 100, D) }
                gen("VIII", "2005–2012") { e("1.8 i-VTEC", 140, P); e("2.0 Type R", 201, P); e("1.3 Hybrid", 115, H); e("2.2 i-CTDi", 140, D) }
                gen("IX", "2012–2017") { e("1.4 i-VTEC", 100, P); e("1.8 i-VTEC", 142, P); e("2.0 Type R", 310, P); e("1.6 i-DTEC", 120, D); e("2.2 i-DTEC", 150, D) }
                gen("X", "2017–2022") { e("1.0 VTEC Turbo", 129, P); e("1.5 VTEC Turbo", 182, P); e("2.0 Type R", 320, P); e("1.6 i-DTEC", 120, D) }
                gen("XI", "2022–") { e("2.0 e:HEV", 184, H); e("2.0 Type R", 329, P) }
            }
            model("CR-V") {
                gen("IV", "2012–2018") { e("2.0 i-VTEC", 155, P); e("1.6 i-DTEC", 120, D); e("1.6 i-DTEC", 160, D); e("2.2 i-DTEC", 150, D) }
                gen("V", "2018–2023") { e("1.5 VTEC Turbo", 173, P); e("2.0 i-MMD Hybrid", 184, H) }
            }
            model("HR-V") {
                gen("II", "2015–2021") { e("1.5 i-VTEC", 130, P); e("1.5 VTEC Turbo", 182, P); e("1.6 i-DTEC", 120, D) }
            }
        },
        brand("Hyundai") {
            model("i10") {
                gen("II", "2013–2019") { e("1.0", 66, P); e("1.2", 87, P) }
                gen("III", "2019–") { e("1.0", 67, P); e("1.2", 84, P); e("1.0 T-GDi", 100, P) }
            }
            model("i20") {
                gen("II", "2014–2020") { e("1.2", 84, P); e("1.0 T-GDi", 100, P); e("1.0 T-GDi", 120, P); e("1.4 CRDi", 90, D) }
                gen("III", "2020–") { e("1.2", 84, P); e("1.0 T-GDi", 100, P); e("1.0 T-GDi mHEV", 100, H); e("1.6 T-GDi N", 204, P) }
            }
            model("Bayon") {
                gen("I", "2021–") { e("1.2", 84, P); e("1.0 T-GDi", 100, P); e("1.0 T-GDi mHEV", 100, H) }
            }
            model("i30") {
                gen("II", "2012–2017") { e("1.4", 100, P); e("1.6 GDi", 135, P); e("1.6 T-GDi", 186, P); e("1.4 CRDi", 90, D); e("1.6 CRDi", 110, D); e("1.6 CRDi", 128, D) }
                gen("III", "2017–") { e("1.0 T-GDi", 120, P); e("1.4 T-GDi", 140, P); e("1.5 T-GDi mHEV", 160, H); e("2.0 T-GDi N", 280, P); e("1.6 CRDi", 115, D); e("1.6 CRDi", 136, D) }
            }
            model("Kona") {
                gen("I", "2017–2023") { e("1.0 T-GDi", 120, P); e("1.6 T-GDi", 198, P); e("1.6 GDi Hybrid", 141, H); e("1.6 CRDi", 136, D) }
                gen("II", "2023–") { e("1.0 T-GDi", 120, P); e("1.6 GDi Hybrid", 141, H) }
            }
            model("ix35") {
                gen("I", "2010–2015") { e("1.6 GDi", 135, P); e("2.0", 163, P); e("1.7 CRDi", 115, D); e("2.0 CRDi", 136, D); e("2.0 CRDi", 184, D) }
            }
            model("Tucson") {
                gen("III", "2015–2020") { e("1.6 GDi", 132, P); e("1.6 T-GDi", 177, P); e("1.7 CRDi", 115, D); e("1.6 CRDi", 136, D); e("2.0 CRDi", 185, D) }
                gen("IV", "2020–") { e("1.6 T-GDi mHEV", 150, H); e("1.6 T-GDi Hybrid", 230, H); e("1.6 T-GDi Plug-in", 265, R); e("1.6 CRDi", 136, D) }
            }
        },
        brand("Infiniti") {
            model("Q30") {
                gen("I", "2015–2019") { e("1.6t", 122, P); e("1.6t", 156, P); e("2.0t", 211, P); e("1.5d", 109, D); e("2.2d", 170, D) }
            }
            model("Q50") {
                gen("I", "2013–") { e("2.0t", 211, P); e("3.0t Red Sport", 405, P); e("3.5 Hybrid", 364, H); e("2.2d", 170, D) }
            }
            model("QX30") {
                gen("I", "2016–2019") { e("2.0t", 211, P); e("2.2d", 170, D) }
            }
            model("QX70") {
                gen("I", "2013–2017") { e("3.7 V6", 320, P); e("3.0d", 238, D) }
            }
        },
        brand("Isuzu") {
            model("D-Max") {
                gen("II", "2012–2020") { e("2.5 TD", 163, D); e("1.9 Ddi", 164, D) }
                gen("III", "2020–") { e("1.9 Ddi", 163, D) }
            }
        },
        brand("Jaguar") {
            model("E-Pace") {
                gen("X540", "2017–") { e("P200", 200, P); e("P250", 249, P); e("D150", 150, D); e("D180", 180, D); e("P300e", 309, R) }
            }
            model("F-Pace") {
                gen("X761", "2016–") { e("2.0 i4", 250, P); e("3.0 V6 S", 380, P); e("2.0 D", 180, D); e("P400e", 404, R) }
            }
            model("XE") {
                gen("X760", "2015–2024") { e("2.0 i4", 200, P); e("2.0 i4", 250, P); e("2.0 D", 180, D) }
            }
            model("XF") {
                gen("X260", "2015–2024") { e("2.0 i4", 250, P); e("2.0 D", 163, D); e("2.0 D", 180, D) }
            }
        },
        brand("Jeep") {
            model("Avenger") {
                gen("I", "2023–") { e("1.2 Turbo", 100, P); e("1.2 e-Hybrid", 100, H) }
            }
            model("Cherokee") {
                gen("KL", "2014–2021") { e("3.2 V6", 272, P); e("2.0 MultiJet", 140, D); e("2.0 MultiJet", 170, D); e("2.2 MultiJet", 200, D) }
            }
            model("Renegade") {
                gen("BU", "2014–") { e("1.4 MultiAir", 140, P); e("1.0 T3", 120, P); e("1.3 T4", 150, P); e("1.6 MultiJet", 120, D); e("2.0 MultiJet", 140, D); e("1.3 4xe", 240, R) }
            }
            model("Compass") {
                gen("II", "2017–") { e("1.4 MultiAir", 140, P); e("1.3 T4", 150, P); e("1.6 MultiJet", 120, D); e("2.0 MultiJet", 140, D); e("1.3 4xe", 240, R) }
            }
            model("Wrangler") {
                gen("JL", "2018–") { e("2.0 Turbo", 272, P); e("2.2 MultiJet", 200, D); e("2.0 4xe", 380, R) }
            }
        },
        brand("Kia") {
            model("Picanto") {
                gen("II", "2011–2017") { e("1.0", 69, P); e("1.25", 85, P) }
                gen("III", "2017–") { e("1.0", 67, P); e("1.2", 84, P); e("1.0 T-GDi", 100, P) }
            }
            model("Rio") {
                gen("IV", "2017–2023") { e("1.25", 84, P); e("1.0 T-GDi", 100, P); e("1.0 T-GDi", 120, P); e("1.4 CRDi", 90, D) }
            }
            model("Ceed") {
                gen("II", "2012–2018") { e("1.4", 100, P); e("1.6 GDi", 135, P); e("1.0 T-GDi", 120, P); e("1.6 T-GDi GT", 204, P); e("1.4 CRDi", 90, D); e("1.6 CRDi", 110, D); e("1.6 CRDi", 128, D) }
                gen("III", "2018–") { e("1.0 T-GDi", 120, P); e("1.4 T-GDi", 140, P); e("1.5 T-GDi", 160, P); e("1.6 T-GDi GT", 204, P); e("1.6 CRDi", 115, D); e("1.6 CRDi", 136, D) }
            }
            model("Niro") {
                gen("I", "2016–2022") { e("1.6 GDi Hybrid", 141, H); e("1.6 GDi Plug-in", 141, R) }
                gen("II", "2022–") { e("1.6 GDi Hybrid", 141, H); e("1.6 GDi Plug-in", 183, R) }
            }
            model("Sportage") {
                gen("IV", "2016–2021") { e("1.6 GDi", 132, P); e("1.6 T-GDi", 177, P); e("1.7 CRDi", 115, D); e("1.6 CRDi", 136, D); e("2.0 CRDi", 185, D) }
                gen("V", "2021–") { e("1.6 T-GDi mHEV", 150, H); e("1.6 T-GDi Hybrid", 230, H); e("1.6 T-GDi Plug-in", 265, R); e("1.6 CRDi", 136, D) }
            }
            model("Stonic") {
                gen("I", "2017–") { e("1.2", 84, P); e("1.0 T-GDi", 100, P); e("1.0 T-GDi", 120, P); e("1.0 T-GDi mHEV", 100, H); e("1.6 CRDi", 110, D) }
            }
            model("XCeed") {
                gen("I", "2019–") { e("1.0 T-GDi", 120, P); e("1.5 T-GDi", 160, P); e("1.6 T-GDi", 204, P); e("1.6 CRDi", 136, D); e("1.6 GDi Plug-in", 141, R) }
            }
        },
        brand("Lada") {
            model("Granta") {
                gen("II", "2018–") { e("1.6", 87, P); e("1.6", 98, P); e("1.6", 106, P) }
            }
            model("Kalina") {
                gen("II", "2013–2018") { e("1.6", 87, P); e("1.6", 98, P) }
            }
            model("Niva / 4x4") {
                gen("2121", "1977–") { e("1.7", 83, P) }
            }
            model("Vesta") {
                gen("I", "2015–") { e("1.6", 106, P); e("1.8", 122, P) }
            }
        },
        brand("Lamborghini") {
            model("Huracán") {
                gen("LB724", "2014–2024") { e("5.2 V10", 610, P); e("5.2 V10", 640, P) }
            }
            model("Urus") {
                gen("I", "2018–") { e("4.0 V8", 650, P); e("4.0 V8 S", 666, P) }
            }
        },
        brand("Lancia") {
            model("Delta") {
                gen("III", "2008–2014") { e("1.4 T-Jet", 120, P); e("1.4 T-Jet", 150, P); e("1.6 Multijet", 120, D); e("2.0 Multijet", 165, D) }
            }
            model("Ypsilon") {
                gen("III", "2011–2024") { e("1.2", 69, P); e("0.9 TwinAir", 85, P); e("1.0 Hybrid", 70, H); e("1.3 Multijet", 95, D) }
            }
        },
        brand("Land Rover") {
            model("Defender") {
                gen("L663", "2020–") { e("P300", 300, P); e("D200", 200, D); e("D250", 249, D); e("P400e", 404, R) }
            }
            model("Discovery Sport") {
                gen("L550", "2014–") { e("Si4", 240, P); e("TD4", 150, D); e("TD4", 180, D); e("P300e", 309, R) }
            }
            model("Range Rover Evoque") {
                gen("I", "2011–2018") { e("Si4", 240, P); e("eD4", 150, D); e("TD4", 150, D); e("TD4", 180, D) }
                gen("II", "2019–") { e("P200", 200, P); e("P250", 249, P); e("D150", 150, D); e("D180", 180, D); e("P300e", 309, R) }
            }
            model("Range Rover Sport") {
                gen("II", "2013–2022") { e("3.0 V6 S", 340, P); e("SDV6", 306, D); e("P400e", 404, R) }
            }
        },
        brand("Lexus") {
            model("CT") {
                gen("I", "2011–2022") { e("CT 200h", 136, H) }
            }
            model("IS") {
                gen("III", "2013–") { e("IS 300h", 223, H) }
            }
            model("NX") {
                gen("I", "2014–2021") { e("NX 300h", 197, H) }
            }
            model("RX") {
                gen("IV", "2015–2022") { e("RX 450h", 313, H) }
            }
            model("UX") {
                gen("I", "2018–") { e("UX 250h", 184, H) }
            }
        },
        brand("Maserati") {
            model("Ghibli") {
                gen("M157", "2013–2023") { e("3.0 V6", 350, P); e("3.0 V6 S", 410, P); e("2.0 Hybrid", 330, H); e("3.0 V6 Diesel", 275, D) }
            }
            model("Grecale") {
                gen("I", "2022–") { e("GT 2.0 Hybrid", 300, H); e("Modena 2.0 Hybrid", 330, H); e("Trofeo 3.0 V6", 530, P) }
            }
            model("Levante") {
                gen("M161", "2016–") { e("3.0 V6", 350, P); e("3.0 V6 S", 430, P); e("2.0 Hybrid", 330, H); e("3.0 V6 Diesel", 275, D) }
            }
            model("Quattroporte") {
                gen("VI", "2013–") { e("3.0 V6 S", 410, P); e("3.8 V8 GTS", 530, P); e("3.0 V6 Diesel", 275, D) }
            }
        },
        brand("Mazda") {
            model("Mazda2") {
                gen("II", "2007–2014") { e("1.3", 75, P); e("1.5", 103, P); e("1.4 MZ-CD", 68, D) }
                gen("III", "2014–") { e("1.5 Skyactiv-G", 75, P); e("1.5 Skyactiv-G", 90, P); e("1.5 Skyactiv-G", 115, P); e("1.5 Skyactiv-D", 105, D) }
            }
            model("Mazda3") {
                gen("III", "2013–2019") { e("1.5 Skyactiv-G", 100, P); e("2.0 Skyactiv-G", 120, P); e("2.0 Skyactiv-G", 165, P); e("1.5 Skyactiv-D", 105, D); e("2.2 Skyactiv-D", 150, D) }
                gen("IV", "2019–") { e("2.0 e-Skyactiv G", 122, H); e("2.5 e-Skyactiv G", 140, H); e("2.0 e-Skyactiv X", 186, H); e("1.8 Skyactiv-D", 116, D) }
            }
            model("Mazda6") {
                gen("III", "2012–2023") { e("2.0 Skyactiv-G", 145, P); e("2.0 Skyactiv-G", 165, P); e("2.5 Skyactiv-G", 194, P); e("2.2 Skyactiv-D", 150, D); e("2.2 Skyactiv-D", 175, D) }
            }
            model("CX-3") {
                gen("I", "2015–2022") { e("2.0 Skyactiv-G", 121, P); e("2.0 Skyactiv-G", 150, P); e("1.5 Skyactiv-D", 105, D); e("1.8 Skyactiv-D", 115, D) }
            }
            model("CX-30") {
                gen("I", "2019–") { e("2.0 e-Skyactiv G", 122, H); e("2.5 e-Skyactiv G", 140, H); e("2.0 e-Skyactiv X", 186, H) }
            }
            model("CX-5") {
                gen("II", "2017–") { e("2.0 Skyactiv-G", 165, P); e("2.5 Skyactiv-G", 194, P); e("2.2 Skyactiv-D", 150, D); e("2.2 Skyactiv-D", 184, D) }
            }
            model("MX-5") {
                gen("NC", "2005–2015") { e("1.8", 126, P); e("2.0", 160, P) }
                gen("ND", "2015–") { e("1.5 Skyactiv-G", 132, P); e("2.0 Skyactiv-G", 184, P) }
            }
        },
        brand("Mercedes-Benz") {
            model("Classe A") {
                gen("W176", "2012–2018") { e("A 160", 102, P); e("A 180", 122, P); e("A 200", 156, P); e("A 250", 218, P); e("A 45 AMG", 381, P); e("A 180 d", 109, D); e("A 200 d", 136, D); e("A 220 d", 177, D) }
                gen("W177", "2018–") { e("A 180", 136, P); e("A 200", 163, P); e("A 250", 224, P); e("A 35 AMG", 306, P); e("A 180 d", 116, D); e("A 200 d", 150, D); e("A 220 d", 190, D); e("A 250 e", 218, R) }
            }
            model("Classe B") {
                gen("W246", "2011–2018") { e("B 180", 122, P); e("B 200", 156, P); e("B 180 CDI", 109, D); e("B 200 CDI", 136, D) }
                gen("W247", "2018–") { e("B 180", 136, P); e("B 200", 163, P); e("B 180 d", 116, D); e("B 200 d", 150, D); e("B 250 e", 218, R) }
            }
            model("Classe C") {
                gen("W204", "2007–2014") { e("C 180", 156, P); e("C 200", 184, P); e("C 250", 204, P); e("C 200 CDI", 136, D); e("C 220 CDI", 170, D); e("C 250 CDI", 204, D) }
                gen("W205", "2014–2021") { e("C 180", 156, P); e("C 200", 184, P); e("C 300", 258, P); e("C 200 d", 160, D); e("C 220 d", 194, D); e("C 300 e", 320, R) }
                gen("W206", "2021–") { e("C 200", 204, H); e("C 220 d", 200, D); e("C 300 e", 313, R) }
            }
            model("Classe E") {
                gen("W212", "2009–2016") { e("E 200", 184, P); e("E 220 CDI", 170, D); e("E 250 CDI", 204, D); e("E 350 CDI", 265, D) }
                gen("W213", "2016–2023") { e("E 200", 184, P); e("E 220 d", 194, D); e("E 300 de", 306, R); e("E 300 e", 320, R) }
            }
            model("CLA") {
                gen("C117", "2013–2019") { e("CLA 180", 122, P); e("CLA 200", 156, P); e("CLA 45 AMG", 381, P); e("CLA 200 CDI", 136, D); e("CLA 220 CDI", 177, D) }
            }
            model("GLA") {
                gen("X156", "2013–2019") { e("GLA 180", 122, P); e("GLA 200", 156, P); e("GLA 250", 211, P); e("GLA 200 d", 136, D); e("GLA 220 d", 177, D) }
            }
            model("GLC") {
                gen("X253", "2015–2022") { e("GLC 200", 197, P); e("GLC 300", 258, P); e("GLC 220 d", 194, D); e("GLC 300 de", 306, R) }
            }
            model("Vito") {
                gen("W447", "2014–") { e("111 CDI", 114, D); e("114 CDI", 136, D); e("116 CDI", 163, D); e("119 CDI", 190, D) }
            }
        },
        brand("MG") {
            model("HS") {
                gen("I", "2019–") { e("1.5 T-GDI", 162, P); e("1.5 Plug-in", 258, R) }
            }
            model("MG3") {
                gen("III", "2024–") { e("1.5 Hybrid+", 194, H) }
            }
            model("ZS") {
                gen("I", "2017–") { e("1.5 VTi-tech", 106, P); e("1.0 T-GDI", 111, P); e("1.5 Hybrid+", 197, H) }
            }
        },
        brand("Mini") {
            model("Clubman") {
                gen("F54", "2015–2024") { e("One 1.5", 102, P); e("Cooper 1.5", 136, P); e("Cooper S 2.0", 192, P); e("John Cooper Works 2.0", 306, P); e("Cooper D 2.0", 150, D) }
            }
            model("Mini 3 portes / 5 portes") {
                gen("R50 / R53", "2001–2006") { e("One 1.6", 90, P); e("Cooper 1.6", 115, P); e("Cooper S 1.6", 163, P); e("One D 1.4", 75, D) }
                gen("R56", "2006–2013") { e("One 1.6", 98, P); e("Cooper 1.6", 122, P); e("Cooper S 1.6", 184, P); e("John Cooper Works 1.6", 211, P); e("Cooper D 1.6", 112, D) }
                gen("F56 / F55", "2014–2024") { e("One 1.5", 102, P); e("Cooper 1.5", 136, P); e("Cooper S 2.0", 178, P); e("Cooper S 2.0", 192, P); e("John Cooper Works 2.0", 231, P); e("Cooper D 1.5", 116, D) }
            }
            model("Countryman") {
                gen("F60", "2017–2024") { e("One 1.5", 102, P); e("Cooper 1.5", 136, P); e("Cooper S 2.0", 178, P); e("Cooper SE ALL4", 224, R); e("Cooper D 2.0", 150, D) }
            }
        },
        brand("Mitsubishi") {
            model("Space Star") {
                gen("I", "2013–") { e("1.0 MIVEC", 71, P); e("1.2 MIVEC", 80, P) }
            }
            model("ASX") {
                gen("I", "2010–2023") { e("1.6 MIVEC", 117, P); e("1.6 DI-D", 114, D); e("1.8 DI-D", 150, D); e("2.2 DI-D", 150, D) }
            }
            model("Colt") {
                gen("VI", "2004–2012") { e("1.1", 75, P); e("1.3", 95, P); e("1.5 Turbo CZT", 150, P); e("1.5 DI-D", 95, D) }
            }
            model("Eclipse Cross") {
                gen("I", "2017–") { e("1.5 T-MIVEC", 163, P); e("2.4 PHEV", 188, R) }
            }
            model("L200") {
                gen("V", "2015–") { e("2.4 DI-D", 154, D); e("2.4 DI-D", 181, D) }
            }
            model("Outlander") {
                gen("III", "2012–2021") { e("2.0 MIVEC", 150, P); e("2.2 DI-D", 150, D); e("2.0 PHEV", 203, R); e("2.4 PHEV", 224, R) }
            }
        },
        brand("Nissan") {
            model("Micra") {
                gen("K12", "2003–2010") { e("1.2", 80, P); e("1.4", 88, P); e("1.5 dCi", 86, D) }
                gen("K13", "2010–2016") { e("1.2", 80, P); e("1.2 DIG-S", 98, P) }
                gen("K14", "2017–2023") { e("1.0", 71, P); e("0.9 IG-T", 90, P); e("1.0 IG-T", 100, P); e("1.5 dCi", 90, D) }
            }
            model("Juke") {
                gen("F15", "2010–2019") { e("1.6", 117, P); e("1.2 DIG-T", 115, P); e("1.6 DIG-T", 190, P); e("1.5 dCi", 110, D) }
                gen("F16", "2019–") { e("1.0 DIG-T", 114, P); e("1.6 Hybrid", 143, H) }
            }
            model("Qashqai") {
                gen("J10", "2007–2013") { e("1.6", 117, P); e("2.0", 141, P); e("1.5 dCi", 110, D); e("1.6 dCi", 130, D); e("2.0 dCi", 150, D) }
                gen("J11", "2013–2021") { e("1.2 DIG-T", 115, P); e("1.3 DIG-T", 140, P); e("1.3 DIG-T", 160, P); e("1.6 DIG-T", 163, P); e("1.5 dCi", 110, D); e("1.6 dCi", 130, D) }
                gen("J12", "2021–") { e("1.3 DIG-T mHEV", 140, H); e("1.3 DIG-T mHEV", 158, H); e("1.5 e-Power", 190, H) }
            }
            model("Navara") {
                gen("D23", "2015–") { e("2.3 dCi", 160, D); e("2.3 dCi", 190, D) }
            }
            model("Note") {
                gen("E12", "2013–2017") { e("1.2", 80, P); e("1.2 DIG-S", 98, P); e("1.5 dCi", 90, D) }
            }
            model("X-Trail") {
                gen("T32", "2014–2022") { e("1.6 DIG-T", 163, P); e("1.6 dCi", 130, D); e("2.0 dCi", 177, D) }
                gen("T33", "2022–") { e("1.5 e-Power", 204, H); e("1.5 e-Power e-4orce", 213, H) }
            }
        },
        brand("Opel") {
            model("Agila") {
                gen("B", "2008–2014") { e("1.0", 68, P); e("1.2", 94, P) }
            }
            model("Antara") {
                gen("I", "2006–2015") { e("2.4", 140, P); e("2.0 CDTI", 150, D); e("2.2 CDTI", 163, D) }
            }
            model("Vectra") {
                gen("C", "2002–2008") { e("1.8", 122, P); e("2.2", 155, P); e("1.9 CDTI", 120, D); e("1.9 CDTI", 150, D) }
            }
            model("Vivaro") {
                gen("B", "2014–2019") { e("1.6 CDTI", 120, D); e("1.6 CDTI", 145, D) }
            }
            model("Adam") {
                gen("I", "2013–2019") { e("1.2", 70, P); e("1.4", 87, P); e("1.4", 100, P); e("1.0 Turbo", 90, P); e("1.0 Turbo", 115, P); e("1.4 Turbo S", 150, P) }
            }
            model("Corsa") {
                gen("C", "2000–2006") { e("1.0", 58, P); e("1.2", 75, P); e("1.4", 90, P); e("1.8", 125, P); e("1.3 CDTI", 70, D); e("1.7 CDTI", 100, D) }
                gen("D", "2006–2014") { e("1.0", 65, P); e("1.2", 85, P); e("1.4", 100, P); e("1.6 Turbo OPC", 192, P); e("1.3 CDTI", 75, D); e("1.3 CDTI", 95, D) }
                gen("E", "2014–2019") { e("1.2", 70, P); e("1.4", 90, P); e("1.0 Turbo", 90, P); e("1.0 Turbo", 115, P); e("1.4 Turbo", 150, P); e("1.6 Turbo OPC", 207, P); e("1.3 CDTI", 75, D); e("1.3 CDTI", 95, D) }
                gen("F", "2019–") { e("1.2", 75, P); e("1.2 Turbo", 100, P); e("1.2 Turbo", 130, P); e("1.5 Diesel", 100, D); e("1.2 Hybrid", 100, H) }
            }
            model("Astra") {
                gen("H", "2004–2010") { e("1.4", 90, P); e("1.6", 105, P); e("1.6", 115, P); e("1.8", 140, P); e("2.0 Turbo OPC", 240, P); e("1.7 CDTI", 100, D); e("1.9 CDTI", 120, D); e("1.9 CDTI", 150, D) }
                gen("J", "2009–2015") { e("1.4", 100, P); e("1.4 Turbo", 140, P); e("1.6 Turbo", 180, P); e("2.0 Turbo OPC", 280, P); e("1.7 CDTI", 110, D); e("1.7 CDTI", 130, D); e("2.0 CDTI", 165, D) }
                gen("K", "2015–2021") { e("1.0 Turbo", 105, P); e("1.4 Turbo", 125, P); e("1.4 Turbo", 150, P); e("1.2 Turbo", 130, P); e("1.6 CDTI", 110, D); e("1.6 CDTI", 136, D); e("1.5 Diesel", 122, D) }
                gen("L", "2021–") { e("1.2 Turbo", 110, P); e("1.2 Turbo", 130, P); e("1.5 Diesel", 130, D); e("1.6 Hybrid", 180, R) }
            }
            model("Crossland") {
                gen("X", "2017–2024") { e("1.2", 83, P); e("1.2 Turbo", 110, P); e("1.2 Turbo", 130, P); e("1.5 Diesel", 110, D); e("1.5 Diesel", 120, D) }
            }
            model("Grandland") {
                gen("X", "2017–2024") { e("1.2 Turbo", 130, P); e("1.6 Turbo", 180, P); e("1.5 Diesel", 130, D); e("1.6 Hybrid", 225, R); e("1.6 Hybrid4", 300, R) }
            }
            model("Insignia") {
                gen("A", "2008–2017") { e("1.4 Turbo", 140, P); e("1.6 Turbo", 170, P); e("2.0 Turbo", 220, P); e("2.0 CDTI", 130, D); e("2.0 CDTI", 160, D) }
                gen("B", "2017–2022") { e("1.5 Turbo", 165, P); e("2.0 Turbo", 260, P); e("1.6 CDTI", 136, D); e("2.0 CDTI", 170, D) }
            }
            model("Meriva") {
                gen("A", "2003–2010") { e("1.6", 105, P); e("1.8", 125, P); e("1.3 CDTI", 75, D); e("1.7 CDTI", 100, D) }
                gen("B", "2010–2017") { e("1.4", 100, P); e("1.4 Turbo", 120, P); e("1.4 Turbo", 140, P); e("1.3 CDTI", 95, D); e("1.6 CDTI", 110, D); e("1.6 CDTI", 136, D) }
            }
            model("Mokka") {
                gen("B", "2020–") { e("1.2 Turbo", 100, P); e("1.2 Turbo", 130, P); e("1.5 Diesel", 110, D) }
            }
            model("Zafira") {
                gen("B", "2005–2014") { e("1.6", 105, P); e("1.8", 140, P); e("1.7 CDTI", 125, D); e("1.9 CDTI", 120, D); e("1.9 CDTI", 150, D) }
                gen("C Tourer", "2011–2019") { e("1.4 Turbo", 140, P); e("1.6 Turbo", 170, P); e("1.6 CDTI", 136, D); e("2.0 CDTI", 170, D) }
            }
        },
        brand("Peugeot") {
            model("106") {
                gen("106", "1991–2003") { e("1.0", 50, P); e("1.1", 60, P); e("1.4", 75, P); e("1.6 16V GTI", 118, P); e("1.5 D", 57, D) }
            }
            model("306") {
                gen("306", "1993–2002") { e("1.4", 75, P); e("1.6", 90, P); e("1.8 16V", 110, P); e("2.0 S16", 167, P); e("1.9 D", 70, D); e("2.0 HDi", 90, D) }
            }
            model("406") {
                gen("406", "1995–2004") { e("1.8 16V", 116, P); e("2.0 16V", 135, P); e("3.0 V6", 207, P); e("2.0 HDi", 110, D); e("2.2 HDi", 136, D) }
            }
            model("607") {
                gen("607", "2000–2010") { e("2.2 16V", 160, P); e("3.0 V6", 211, P); e("2.2 HDi", 136, D); e("2.7 HDi", 204, D) }
            }
            model("4008") {
                gen("I", "2012–2017") { e("2.0", 150, P); e("1.6 HDi", 115, D); e("1.8 HDi", 150, D) }
            }
            model("107 / 108") {
                gen("107", "2005–2014") { e("1.0", 68, P); e("1.4 HDi", 54, D) }
                gen("108", "2014–2022") { e("1.0 VTi", 72, P); e("1.2 PureTech", 82, P) }
            }
            model("206") {
                gen("206", "1998–2012") { e("1.1", 60, P); e("1.4", 75, P); e("1.6 16V", 110, P); e("2.0 S16", 136, P); e("2.0 RC", 177, P); e("1.4 HDi", 68, D); e("1.6 HDi", 110, D); e("2.0 HDi", 90, D) }
            }
            model("207") {
                gen("207", "2006–2014") { e("1.4", 75, P); e("1.4 VTi", 95, P); e("1.6 VTi", 120, P); e("1.6 THP", 150, P); e("1.6 THP RC", 175, P); e("1.4 HDi", 70, D); e("1.6 HDi", 90, D); e("1.6 HDi", 110, D) }
            }
            model("208") {
                gen("I", "2012–2019") { e("1.0 VTi", 68, P); e("1.2 PureTech", 82, P); e("1.2 PureTech", 110, P); e("1.6 THP", 156, P); e("1.6 THP GTi", 208, P); e("1.4 HDi", 68, D); e("1.6 BlueHDi", 75, D); e("1.6 BlueHDi", 100, D); e("1.6 BlueHDi", 120, D) }
                gen("II", "2019–") { e("1.2 PureTech", 75, P); e("1.2 PureTech", 100, P); e("1.2 PureTech", 130, P); e("1.5 BlueHDi", 100, D); e("1.2 Hybrid", 100, H) }
            }
            model("2008") {
                gen("I", "2013–2019") { e("1.2 PureTech", 82, P); e("1.2 PureTech", 110, P); e("1.2 PureTech", 130, P); e("1.6 BlueHDi", 100, D); e("1.6 BlueHDi", 120, D) }
                gen("II", "2019–") { e("1.2 PureTech", 100, P); e("1.2 PureTech", 130, P); e("1.2 PureTech", 155, P); e("1.5 BlueHDi", 110, D); e("1.5 BlueHDi", 130, D); e("1.2 Hybrid", 136, H) }
            }
            model("307") {
                gen("307", "2001–2008") { e("1.4", 75, P); e("1.6 16V", 110, P); e("2.0 16V", 136, P); e("1.6 HDi", 90, D); e("1.6 HDi", 110, D); e("2.0 HDi", 90, D); e("2.0 HDi", 136, D) }
            }
            model("308") {
                gen("I", "2007–2013") { e("1.4 VTi", 95, P); e("1.6 VTi", 120, P); e("1.6 THP", 156, P); e("1.6 HDi", 92, D); e("1.6 HDi", 110, D); e("2.0 HDi", 136, D) }
                gen("II", "2013–2021") { e("1.2 PureTech", 110, P); e("1.2 PureTech", 130, P); e("1.6 THP GT", 205, P); e("1.6 THP GTi", 270, P); e("1.6 BlueHDi", 120, D); e("1.5 BlueHDi", 130, D); e("2.0 BlueHDi", 150, D); e("2.0 BlueHDi", 180, D) }
                gen("III", "2021–") { e("1.2 PureTech", 110, P); e("1.2 PureTech", 130, P); e("1.5 BlueHDi", 130, D); e("1.6 Hybrid", 180, R); e("1.6 Hybrid", 225, R) }
            }
            model("3008") {
                gen("I", "2009–2016") { e("1.6 VTi", 120, P); e("1.6 THP", 156, P); e("1.6 HDi", 115, D); e("2.0 HDi", 163, D) }
                gen("II", "2016–2023") { e("1.2 PureTech", 130, P); e("1.6 PureTech", 180, P); e("1.5 BlueHDi", 130, D); e("2.0 BlueHDi", 180, D); e("1.6 Hybrid", 225, R); e("1.6 Hybrid4", 300, R) }
            }
            model("407") {
                gen("407", "2004–2011") { e("1.8 16V", 125, P); e("2.0 16V", 140, P); e("1.6 HDi", 110, D); e("2.0 HDi", 136, D); e("2.2 HDi", 170, D) }
            }
            model("408") {
                gen("II", "2022–") { e("1.2 PureTech", 130, P); e("1.6 Hybrid", 180, R); e("1.6 Hybrid", 225, R) }
            }
            model("5008") {
                gen("I", "2009–2017") { e("1.6 VTi", 120, P); e("1.6 THP", 156, P); e("1.6 HDi", 112, D); e("2.0 HDi", 150, D); e("1.6 BlueHDi", 120, D) }
                gen("II", "2017–2023") { e("1.2 PureTech", 130, P); e("1.6 PureTech", 180, P); e("1.5 BlueHDi", 130, D); e("2.0 BlueHDi", 180, D) }
            }
            model("508") {
                gen("I", "2010–2018") { e("1.6 THP", 156, P); e("1.6 HDi", 112, D); e("2.0 HDi", 140, D); e("2.0 HDi", 163, D); e("2.0 BlueHDi", 150, D); e("2.0 BlueHDi", 180, D); e("2.0 HYbrid4", 200, H) }
                gen("II", "2018–") { e("1.2 PureTech", 130, P); e("1.6 PureTech", 180, P); e("1.6 PureTech", 225, P); e("1.5 BlueHDi", 130, D); e("2.0 BlueHDi", 160, D); e("2.0 BlueHDi", 180, D); e("1.6 Hybrid", 225, R); e("PSE", 360, R) }
            }
            model("Boxer") {
                gen("III", "2006–") { e("2.2 HDi", 110, D); e("2.2 HDi", 130, D); e("2.2 HDi", 150, D); e("2.0 BlueHDi", 130, D); e("2.0 BlueHDi", 160, D); e("2.2 BlueHDi", 140, D); e("2.2 BlueHDi", 165, D) }
            }
            model("Expert") {
                gen("III", "2016–") { e("1.5 BlueHDi", 100, D); e("1.5 BlueHDi", 120, D); e("2.0 BlueHDi", 120, D); e("2.0 BlueHDi", 145, D); e("2.0 BlueHDi", 180, D) }
            }
            model("Partner / Rifter") {
                gen("Partner II", "2008–2018") { e("1.6 VTi", 120, P); e("1.6 HDi", 75, D); e("1.6 HDi", 90, D); e("1.6 BlueHDi", 100, D); e("1.6 BlueHDi", 120, D) }
                gen("Rifter", "2018–") { e("1.2 PureTech", 110, P); e("1.2 PureTech", 130, P); e("1.5 BlueHDi", 75, D); e("1.5 BlueHDi", 100, D); e("1.5 BlueHDi", 130, D) }
            }
        },
        brand("Porsche") {
            model("718 Boxster / Cayman") {
                gen("982", "2016–") { e("2.0", 300, P); e("S 2.5", 350, P); e("GTS 4.0", 400, P) }
            }
            model("911") {
                gen("997 GT3", "2006–2012") { e("GT3 3.6 MK1", 415, P); e("GT3 3.8 MK2", 435, P) }
                gen("991", "2011–2019") { e("Carrera 3.4", 350, P); e("Carrera 3.0", 370, P); e("Carrera S 3.8", 400, P); e("Carrera S 3.0", 420, P) }
                gen("992", "2019–") { e("Carrera", 385, P); e("Carrera S", 450, P) }
            }
            model("Cayenne") {
                gen("II", "2010–2017") { e("3.6 V6", 300, P); e("S 4.8 V8", 400, P); e("3.0 V6 Diesel", 245, D); e("S E-Hybrid", 416, R) }
                gen("III", "2017–") { e("3.0 V6", 340, P); e("S 2.9 V6", 440, P); e("E-Hybrid", 462, R) }
            }
            model("Macan") {
                gen("I", "2014–2024") { e("2.0", 252, P); e("S 3.0 V6", 354, P); e("GTS 2.9 V6", 380, P); e("S Diesel", 258, D) }
            }
        },
        brand("Renault") {
            model("Koleos") {
                gen("II", "2016–2023") { e("2.5", 171, P); e("1.3 TCe", 160, P); e("1.7 Blue dCi", 150, D); e("2.0 dCi", 175, D); e("2.0 Blue dCi", 190, D) }
            }
            model("Rafale") {
                gen("I", "2024–") { e("1.2 E-Tech", 200, H); e("1.2 E-Tech 4x4", 300, R) }
            }
            model("Twingo") {
                gen("I", "1993–2007") { e("1.2", 60, P); e("1.2 16V", 75, P) }
                gen("II", "2007–2014") { e("1.2", 75, P); e("1.2 TCe", 100, P); e("1.6 RS", 133, P); e("1.5 dCi", 85, D) }
                gen("III", "2014–2024") { e("1.0 SCe", 65, P); e("1.0 SCe", 70, P); e("0.9 TCe", 90, P) }
            }
            model("Clio") {
                gen("I", "1990–1998") { e("1.2", 55, P); e("1.2", 60, P); e("1.4", 75, P); e("1.8 16V", 135, P); e("1.9 D", 64, D) }
                gen("II", "1998–2012") { e("1.2", 60, P); e("1.2 16V", 75, P); e("1.4 16V", 98, P); e("1.6 16V", 110, P); e("2.0 RS", 172, P); e("2.0 RS", 182, P); e("1.5 dCi", 65, D); e("1.5 dCi", 82, D) }
                gen("III", "2005–2014") { e("1.2 16V", 75, P); e("1.2 TCe", 100, P); e("1.6 16V", 112, P); e("2.0 RS", 200, P); e("1.5 dCi", 70, D); e("1.5 dCi", 85, D); e("1.5 dCi", 105, D) }
                gen("IV", "2012–2019") { e("1.2 16V", 75, P); e("0.9 TCe", 90, P); e("1.2 TCe", 120, P); e("1.6 Turbo RS", 200, P); e("1.6 Turbo RS Trophy", 220, P); e("1.5 dCi", 75, D); e("1.5 dCi", 90, D); e("1.5 dCi", 110, D) }
                gen("V", "2019–") { e("1.0 SCe", 65, P); e("1.0 TCe", 90, P); e("1.0 TCe", 100, P); e("1.0 TCe GPL", 100, G); e("1.3 TCe", 130, P); e("1.6 E-Tech", 145, H); e("1.5 Blue dCi", 85, D); e("1.5 Blue dCi", 100, D) }
            }
            model("Captur") {
                gen("I", "2013–2019") { e("0.9 TCe", 90, P); e("1.2 TCe", 120, P); e("1.3 TCe", 130, P); e("1.3 TCe", 150, P); e("1.5 dCi", 90, D); e("1.5 dCi", 110, D) }
                gen("II", "2019–") { e("1.0 TCe", 90, P); e("1.0 TCe GPL", 100, G); e("1.3 TCe", 140, P); e("1.3 TCe", 160, P); e("1.6 E-Tech", 145, H); e("1.6 E-Tech Plug-in", 160, R); e("1.5 Blue dCi", 95, D); e("1.5 Blue dCi", 115, D) }
            }
            model("Mégane") {
                gen("I", "1995–2002") { e("1.4", 75, P); e("1.6", 90, P); e("1.6 16V", 107, P); e("2.0 16V", 140, P); e("1.9 dTi", 98, D); e("1.9 dCi", 102, D) }
                gen("II", "2002–2009") { e("1.4 16V", 100, P); e("1.6 16V", 115, P); e("2.0 16V", 135, P); e("2.0 T RS", 225, P); e("2.0 T RS", 230, P); e("1.5 dCi", 85, D); e("1.5 dCi", 105, D); e("1.9 dCi", 120, D); e("1.9 dCi", 130, D) }
                gen("III", "2008–2016") { e("1.6 16V", 110, P); e("1.2 TCe", 115, P); e("1.2 TCe", 130, P); e("2.0 TCe RS", 265, P); e("1.5 dCi", 90, D); e("1.5 dCi", 110, D); e("1.6 dCi", 130, D); e("1.9 dCi", 130, D) }
                gen("IV", "2016–2023") { e("1.2 TCe", 100, P); e("1.2 TCe", 130, P); e("1.3 TCe", 140, P); e("1.3 TCe", 160, P); e("1.8 TCe RS", 280, P); e("1.8 TCe RS Trophy", 300, P); e("1.5 Blue dCi", 115, D); e("1.6 dCi", 130, D); e("1.6 E-Tech Plug-in", 160, R) }
            }
            model("Kadjar") {
                gen("I", "2015–2022") { e("1.2 TCe", 130, P); e("1.3 TCe", 140, P); e("1.3 TCe", 160, P); e("1.5 dCi", 110, D); e("1.5 Blue dCi", 115, D); e("1.6 dCi", 130, D) }
            }
            model("Arkana") {
                gen("I", "2021–") { e("1.3 TCe mild hybrid", 140, H); e("1.3 TCe mild hybrid", 160, H); e("1.6 E-Tech", 145, H) }
            }
            model("Austral") {
                gen("I", "2022–") { e("1.3 TCe mild hybrid", 140, H); e("1.3 TCe mild hybrid", 160, H); e("1.2 E-Tech", 200, H) }
            }
            model("Kangoo") {
                gen("I", "1997–2008") { e("1.2", 60, P); e("1.4", 75, P); e("1.6 16V", 95, P); e("1.9 D", 64, D); e("1.5 dCi", 65, D); e("1.5 dCi", 85, D) }
                gen("II", "2008–2021") { e("1.6 16V", 105, P); e("1.2 TCe", 115, P); e("1.5 dCi", 75, D); e("1.5 dCi", 90, D); e("1.5 dCi", 110, D) }
                gen("III", "2021–") { e("1.3 TCe", 100, P); e("1.3 TCe", 130, P); e("1.5 Blue dCi", 95, D); e("1.5 Blue dCi", 115, D) }
            }
            model("Scénic") {
                gen("I", "1996–2003") { e("1.6 16V", 107, P); e("2.0 16V", 140, P); e("1.9 dCi", 102, D) }
                gen("II", "2003–2009") { e("1.6 16V", 115, P); e("2.0 16V", 135, P); e("1.5 dCi", 85, D); e("1.5 dCi", 105, D); e("1.9 dCi", 120, D) }
                gen("III", "2009–2016") { e("1.6 16V", 110, P); e("1.2 TCe", 115, P); e("1.2 TCe", 130, P); e("1.4 TCe", 130, P); e("1.5 dCi", 110, D); e("1.6 dCi", 130, D); e("1.9 dCi", 130, D) }
                gen("IV", "2016–2022") { e("1.2 TCe", 115, P); e("1.2 TCe", 130, P); e("1.3 TCe", 140, P); e("1.3 TCe", 160, P); e("1.5 dCi", 110, D); e("1.6 dCi", 130, D); e("1.6 dCi", 160, D); e("1.7 Blue dCi", 120, D); e("1.7 Blue dCi", 150, D) }
            }
            model("Espace") {
                gen("V", "2015–2023") { e("1.6 TCe", 200, P); e("1.8 TCe", 225, P); e("1.6 dCi", 130, D); e("1.6 dCi", 160, D); e("2.0 Blue dCi", 160, D); e("2.0 Blue dCi", 200, D) }
                gen("VI", "2023–") { e("1.2 E-Tech", 200, H) }
            }
            model("Laguna") {
                gen("II", "2001–2007") { e("1.6 16V", 110, P); e("1.8 16V", 120, P); e("2.0 16V", 135, P); e("1.9 dCi", 120, D); e("2.2 dCi", 150, D) }
                gen("III", "2007–2015") { e("2.0 16V", 140, P); e("2.0 TCe", 170, P); e("2.0 TCe", 205, P); e("1.5 dCi", 110, D); e("2.0 dCi", 130, D); e("2.0 dCi", 150, D); e("2.0 dCi", 175, D) }
            }
            model("Talisman") {
                gen("I", "2015–2022") { e("1.6 TCe", 150, P); e("1.6 TCe", 200, P); e("1.8 TCe", 225, P); e("1.3 TCe", 160, P); e("1.5 dCi", 110, D); e("1.6 dCi", 130, D); e("1.6 dCi", 160, D); e("2.0 Blue dCi", 160, D); e("2.0 Blue dCi", 200, D) }
            }
            model("Modus") {
                gen("I", "2004–2012") { e("1.2 16V", 75, P); e("1.6 16V", 112, P); e("1.5 dCi", 86, D) }
            }
            model("Symbioz") {
                gen("I", "2024–") { e("1.6 E-Tech", 145, H) }
            }
            model("Trafic") {
                gen("III", "2014–") { e("1.6 dCi", 95, D); e("1.6 dCi", 120, D); e("1.6 dCi", 145, D); e("2.0 Blue dCi", 110, D); e("2.0 Blue dCi", 130, D); e("2.0 Blue dCi", 150, D); e("2.0 Blue dCi", 170, D) }
            }
            model("Master") {
                gen("III", "2010–2024") { e("2.3 dCi", 110, D); e("2.3 dCi", 130, D); e("2.3 dCi", 150, D); e("2.3 dCi", 170, D) }
            }
        },
        brand("Saab") {
            model("9-3") {
                gen("II", "2002–2012") { e("1.8t", 150, P); e("2.0t", 175, P); e("2.8 V6 Aero", 250, P); e("2.0t BioPower", 175, F); e("1.9 TiD", 120, D); e("1.9 TiD", 150, D); e("1.9 TTiD", 180, D) }
            }
            model("9-5") {
                gen("I", "1997–2010") { e("2.0t", 150, P); e("2.3t", 185, P); e("1.9 TiD", 150, D) }
            }
        },
        brand("Seat") {
            model("Ibiza") {
                gen("IV (6J)", "2008–2017") { e("1.2", 70, P); e("1.2 TSI", 90, P); e("1.2 TSI", 105, P); e("1.4 TSI Cupra", 180, P); e("1.2 TDI", 75, D); e("1.6 TDI", 90, D); e("1.6 TDI", 105, D) }
                gen("V (KJ)", "2017–") { e("1.0 MPI", 80, P); e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("1.5 TSI", 150, P); e("1.0 TGI", 90, P); e("1.6 TDI", 95, D) }
            }
            model("Arona") {
                gen("I", "2017–") { e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("1.5 TSI", 150, P); e("1.6 TDI", 95, D); e("1.6 TDI", 115, D) }
            }
            model("Ateca") {
                gen("I", "2016–") { e("1.0 TSI", 110, P); e("1.5 TSI", 150, P); e("2.0 TSI", 190, P); e("1.6 TDI", 115, D); e("2.0 TDI", 150, D); e("2.0 TDI", 190, D) }
            }
            model("Leon") {
                gen("II", "2005–2012") { e("1.4 16V", 85, P); e("1.4 TSI", 125, P); e("1.8 TSI", 160, P); e("2.0 TFSI FR", 200, P); e("2.0 TFSI Cupra", 240, P); e("1.9 TDI", 105, D); e("1.6 TDI", 105, D); e("2.0 TDI", 140, D); e("2.0 TDI", 170, D) }
                gen("III", "2012–2020") { e("1.2 TSI", 110, P); e("1.4 TSI", 125, P); e("1.4 TSI", 150, P); e("1.5 TSI", 150, P); e("2.0 TSI Cupra", 300, P); e("1.6 TDI", 110, D); e("1.6 TDI", 115, D); e("2.0 TDI", 150, D); e("2.0 TDI", 184, D) }
                gen("IV", "2020–") { e("1.0 eTSI", 110, H); e("1.5 TSI", 130, P); e("1.5 TSI", 150, P); e("2.0 TDI", 115, D); e("2.0 TDI", 150, D); e("1.4 e-Hybrid", 204, R) }
            }
            model("Mii") {
                gen("I", "2011–2019") { e("1.0 MPI", 60, P); e("1.0 MPI", 75, P) }
            }
            model("Tarraco") {
                gen("I", "2018–") { e("1.5 TSI", 150, P); e("2.0 TSI", 190, P); e("2.0 TDI", 150, D); e("2.0 TDI", 200, D); e("1.4 e-Hybrid", 245, R) }
            }
        },
        brand("Skoda") {
            model("Citigo") {
                gen("I", "2011–2020") { e("1.0 MPI", 60, P); e("1.0 MPI", 75, P) }
            }
            model("Fabia") {
                gen("II", "2007–2014") { e("1.2", 70, P); e("1.2 TSI", 86, P); e("1.2 TSI", 105, P); e("1.4 TSI RS", 180, P); e("1.6 TDI", 90, D); e("1.6 TDI", 105, D) }
                gen("III", "2014–2021") { e("1.0 MPI", 75, P); e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("1.2 TSI", 90, P); e("1.2 TSI", 110, P); e("1.4 TDI", 90, D) }
                gen("IV", "2021–") { e("1.0 MPI", 80, P); e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("1.5 TSI", 150, P) }
            }
            model("Kamiq") {
                gen("I", "2019–") { e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("1.5 TSI", 150, P); e("1.6 TDI", 115, D) }
            }
            model("Octavia") {
                gen("II", "2004–2013") { e("1.6", 102, P); e("1.4 TSI", 122, P); e("1.8 TSI", 160, P); e("2.0 TFSI RS", 200, P); e("1.9 TDI", 105, D); e("1.6 TDI", 105, D); e("2.0 TDI", 140, D); e("2.0 TDI RS", 170, D) }
                gen("III", "2013–2020") { e("1.0 TSI", 115, P); e("1.2 TSI", 110, P); e("1.4 TSI", 150, P); e("1.5 TSI", 150, P); e("2.0 TSI RS", 245, P); e("1.6 TDI", 115, D); e("2.0 TDI", 150, D); e("2.0 TDI RS", 184, D) }
                gen("IV", "2020–") { e("1.0 eTSI", 110, H); e("1.5 TSI", 150, P); e("2.0 TSI RS", 245, P); e("2.0 TDI", 115, D); e("2.0 TDI", 150, D); e("1.4 iV", 204, R) }
            }
            model("Karoq") {
                gen("I", "2017–") { e("1.0 TSI", 115, P); e("1.5 TSI", 150, P); e("2.0 TDI", 115, D); e("2.0 TDI", 150, D) }
            }
            model("Kodiaq") {
                gen("I", "2016–2024") { e("1.4 TSI", 150, P); e("1.5 TSI", 150, P); e("2.0 TSI", 190, P); e("2.0 TSI RS", 245, P); e("2.0 TDI", 150, D); e("2.0 TDI", 190, D) }
            }
            model("Scala") {
                gen("I", "2019–") { e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("1.5 TSI", 150, P); e("1.6 TDI", 115, D) }
            }
            model("Superb") {
                gen("III", "2015–2023") { e("1.4 TSI", 150, P); e("1.5 TSI", 150, P); e("2.0 TSI", 220, P); e("2.0 TSI", 272, P); e("1.6 TDI", 120, D); e("2.0 TDI", 150, D); e("2.0 TDI", 190, D); e("1.4 iV", 218, R) }
            }
            model("Yeti") {
                gen("I", "2009–2017") { e("1.2 TSI", 105, P); e("1.4 TSI", 122, P); e("1.8 TSI", 160, P); e("2.0 TDI", 110, D); e("2.0 TDI", 140, D); e("2.0 TDI", 170, D) }
            }
        },
        brand("Smart") {
            model("ForFour") {
                gen("453", "2014–2019") { e("1.0", 61, P); e("1.0", 71, P); e("0.9 Turbo", 90, P) }
            }
            model("ForTwo") {
                gen("451", "2007–2014") { e("1.0", 61, P); e("1.0", 71, P); e("1.0 Turbo", 84, P); e("0.8 CDI", 54, D) }
                gen("453", "2014–2024") { e("1.0", 61, P); e("1.0", 71, P); e("0.9 Turbo", 90, P) }
            }
        },
        brand("SsangYong") {
            model("Korando") {
                gen("C300", "2019–") { e("1.5 T-GDI", 163, P); e("1.6 e-XDi", 136, D) }
            }
            model("Musso") {
                gen("Q200", "2018–") { e("2.2 e-XDi", 181, D) }
            }
            model("Rexton") {
                gen("G4", "2017–") { e("2.2 e-XDi", 181, D); e("2.2 e-XDi", 202, D) }
            }
            model("Tivoli") {
                gen("X100", "2015–") { e("1.6", 128, P); e("1.5 T-GDI", 163, P); e("1.6 e-XDi", 115, D); e("1.6 e-XDi", 136, D) }
            }
        },
        brand("Subaru") {
            model("BRZ") {
                gen("I", "2012–2020") { e("2.0", 200, P) }
            }
            model("Forester") {
                gen("IV", "2012–2018") { e("2.0", 150, P); e("2.0 XT", 240, P); e("2.0D", 147, D) }
                gen("V", "2019–") { e("2.0 e-Boxer", 150, H) }
            }
            model("Impreza") {
                gen("III", "2007–2011") { e("1.5", 107, P); e("2.0", 150, P); e("2.5 WRX STI", 300, P); e("2.0D Boxer", 150, D) }
            }
            model("Outback") {
                gen("V", "2015–2021") { e("2.5", 175, P); e("2.0D", 150, D) }
            }
            model("XV") {
                gen("I", "2012–2017") { e("1.6", 114, P); e("2.0", 150, P); e("2.0D", 147, D) }
                gen("II", "2018–2023") { e("1.6", 114, P); e("2.0", 156, P); e("2.0 e-Boxer", 150, H) }
            }
        },
        brand("Suzuki") {
            model("Swift") {
                gen("III", "2005–2010") { e("1.3 VVT", 92, P); e("1.5 VVT", 102, P); e("1.3 DDiS", 75, D) }
                gen("Sport ZC31S", "2006–2011") { e("1.6 VVT", 125, P) }
                gen("IV", "2010–2017") { e("1.2 VVT", 94, P); e("1.3 DDiS", 75, D) }
                gen("Sport ZC32S", "2012–2017") { e("1.6 VVT", 136, P) }
                gen("V", "2017–2024") { e("1.2 Dualjet", 90, P); e("1.0 BoosterJet", 111, P); e("1.2 Dualjet SHVS", 83, H); e("1.2 Dualjet SHVS", 90, H) }
                gen("Sport ZC33S", "2018–2024") { dedicated += SWIFT_SPORT_ZC33S }
                gen("VI", "2024–") { e("1.2 Dualjet Hybrid", 83, H) }
            }
            model("Alto") {
                gen("VII", "2009–2014") { e("1.0", 68, P) }
            }
            model("Baleno") {
                gen("II", "2016–2019") { e("1.2 Dualjet", 90, P); e("1.0 BoosterJet", 111, P); e("1.2 Dualjet SHVS", 90, H) }
            }
            model("Celerio") {
                gen("I", "2014–2020") { e("1.0 Dualjet", 68, P) }
            }
            model("Ignis") {
                gen("III", "2016–") { e("1.2 Dualjet", 90, P); e("1.2 Dualjet SHVS", 83, H) }
            }
            model("Vitara") {
                gen("IV", "2015–") { e("1.6 VVT", 120, P); e("1.0 BoosterJet", 111, P); e("1.4 BoosterJet", 140, P); e("1.4 BoosterJet mild hybrid", 129, H); e("1.5 Hybrid", 116, H); e("1.6 DDiS", 120, D) }
            }
            model("S-Cross") {
                gen("I", "2013–2021") { e("1.6 VVT", 120, P); e("1.0 BoosterJet", 111, P); e("1.4 BoosterJet", 140, P); e("1.6 DDiS", 120, D) }
                gen("II", "2021–") { e("1.4 BoosterJet mild hybrid", 129, H); e("1.5 Hybrid", 116, H) }
            }
            model("SX4") {
                gen("I", "2006–2014") { e("1.5 VVT", 112, P); e("1.6 VVT", 120, P); e("1.9 DDiS", 120, D); e("2.0 DDiS", 135, D) }
            }
            model("Jimny") {
                gen("III", "1998–2018") { e("1.3 VVT", 85, P); e("1.5 DDiS", 86, D) }
                gen("IV", "2018–") { e("1.5 VVT", 102, P) }
            }
            model("Swace") {
                gen("I", "2020–") { e("1.8 Hybride", 122, H) }
            }
            model("Across") {
                gen("I", "2020–") { e("2.5 Hybride rechargeable", 306, R) }
            }
        },
        brand("Toyota") {
            model("GT86") {
                gen("I", "2012–2021") { e("2.0", 200, P) }
            }
            model("Land Cruiser") {
                gen("J150", "2009–2023") { e("4.0 V6", 282, P); e("3.0 D-4D", 190, D); e("2.8 D-4D", 177, D) }
            }
            model("Prius+") {
                gen("I", "2012–2021") { e("1.8 Hybride", 136, H) }
            }
            model("Proace") {
                gen("II", "2016–") { e("1.5 D-4D", 120, D); e("2.0 D-4D", 150, D); e("2.0 D-4D", 180, D) }
            }
            model("Supra") {
                gen("A90", "2019–") { e("2.0", 258, P); e("3.0", 340, P) }
            }
            model("Aygo") {
                gen("I", "2005–2014") { e("1.0 VVT-i", 68, P); e("1.4 D-4D", 54, D) }
                gen("II", "2014–2022") { e("1.0 VVT-i", 72, P) }
                gen("Aygo X", "2022–") { e("1.0 VVT-i", 72, P) }
            }
            model("Yaris") {
                gen("I", "1999–2005") { e("1.0 VVT-i", 68, P); e("1.3 VVT-i", 87, P); e("1.4 D-4D", 75, D) }
                gen("II", "2005–2011") { e("1.0 VVT-i", 69, P); e("1.33 Dual VVT-i", 101, P); e("1.4 D-4D", 90, D) }
                gen("III", "2011–2020") { e("1.0 VVT-i", 72, P); e("1.33 Dual VVT-i", 99, P); e("1.5 Dual VVT-iE", 111, P); e("1.5 Hybride", 100, H); e("1.4 D-4D", 90, D) }
                gen("IV", "2020–") { e("1.0 VVT-i", 72, P); e("1.5 Dynamic Force", 125, P); e("1.5 Hybride", 116, H); e("1.5 Hybride", 130, H) }
                gen("GR Yaris", "2020–") { e("1.6 Turbo", 261, P); e("1.6 Turbo", 280, P) }
            }
            model("Yaris Cross") {
                gen("I", "2021–") { e("1.5 Dynamic Force", 125, P); e("1.5 Hybride", 116, H); e("1.5 Hybride", 130, H) }
            }
            model("Auris") {
                gen("I", "2007–2012") { e("1.33 Dual VVT-i", 101, P); e("1.6 Dual VVT-i", 124, P); e("1.8 Hybride", 136, H); e("1.4 D-4D", 90, D); e("2.0 D-4D", 126, D) }
                gen("II", "2012–2018") { e("1.33 Dual VVT-i", 99, P); e("1.2 T", 116, P); e("1.8 Hybride", 136, H); e("1.4 D-4D", 90, D); e("1.6 D-4D", 112, D) }
            }
            model("Corolla") {
                gen("E12", "2002–2007") { e("1.4 VVT-i", 97, P); e("1.6 VVT-i", 110, P); e("1.8 VVT-i TS", 192, P); e("1.4 D-4D", 90, D); e("2.0 D-4D", 116, D) }
                gen("E210", "2019–") { e("1.2 T", 116, P); e("1.8 Hybride", 122, H); e("1.8 Hybride", 140, H); e("2.0 Hybride", 184, H); e("2.0 Hybride", 196, H) }
            }
            model("C-HR") {
                gen("I", "2016–2023") { e("1.2 T", 116, P); e("1.8 Hybride", 122, H); e("2.0 Hybride", 184, H) }
                gen("II", "2023–") { e("1.8 Hybride", 140, H); e("2.0 Hybride", 197, H); e("2.0 Hybride rechargeable", 223, R) }
            }
            model("Avensis") {
                gen("II", "2003–2009") { e("1.8 VVT-i", 129, P); e("2.0 VVT-i", 147, P); e("2.0 D-4D", 116, D); e("2.2 D-4D", 150, D) }
                gen("III", "2009–2018") { e("1.6 Valvematic", 132, P); e("1.8 Valvematic", 147, P); e("2.0 D-4D", 126, D); e("2.2 D-4D", 150, D) }
            }
            model("Prius") {
                gen("III", "2009–2015") { e("1.8 Hybride", 136, H); e("1.8 Hybride rechargeable", 136, R) }
                gen("IV", "2015–2022") { e("1.8 Hybride", 122, H); e("1.8 Hybride rechargeable", 122, R) }
            }
            model("Hilux") {
                gen("VIII", "2015–") { e("2.4 D-4D", 150, D); e("2.8 D-4D", 204, D) }
            }
            model("RAV4") {
                gen("IV", "2013–2018") { e("2.0 Valvematic", 151, P); e("2.5 Hybride", 197, H); e("2.0 D-4D", 124, D); e("2.2 D-4D", 150, D) }
                gen("V", "2018–") { e("2.5 Hybride", 218, H); e("2.5 Hybride AWD-i", 222, H); e("2.5 Hybride rechargeable", 306, R) }
            }
        },
        brand("Volkswagen") {
            model("Amarok") {
                gen("I", "2010–2020") { e("2.0 TDI", 140, D); e("2.0 TDI", 180, D); e("3.0 V6 TDI", 224, D) }
            }
            model("Beetle") {
                gen("A5", "2011–2019") { e("1.2 TSI", 105, P); e("1.4 TSI", 160, P); e("2.0 TSI", 200, P); e("1.6 TDI", 105, D); e("2.0 TDI", 140, D) }
            }
            model("Scirocco") {
                gen("III", "2008–2017") { e("1.4 TSI", 125, P); e("1.4 TSI", 160, P); e("2.0 TSI", 210, P); e("2.0 TSI R", 265, P); e("2.0 TDI", 140, D) }
            }
            model("Sharan") {
                gen("II", "2010–2022") { e("1.4 TSI", 150, P); e("2.0 TDI", 140, D); e("2.0 TDI", 150, D); e("2.0 TDI", 184, D) }
            }
            model("Touareg") {
                gen("II", "2010–2018") { e("3.6 V6", 280, P); e("3.0 V6 Hybrid", 380, H); e("3.0 V6 TDI", 204, D); e("3.0 V6 TDI", 262, D) }
            }
            model("Up!") {
                gen("I", "2011–2023") { e("1.0 MPI", 60, P); e("1.0 MPI", 75, P); e("1.0 TSI", 90, P); e("1.0 TSI GTI", 115, P) }
            }
            model("Polo") {
                gen("III (6N)", "1994–2001") { e("1.0", 50, P); e("1.4", 60, P); e("1.6", 75, P); e("1.6 GTI", 125, P); e("1.9 SDI", 64, D); e("1.9 TDI", 90, D) }
                gen("IV (9N)", "2001–2009") { e("1.2", 55, P); e("1.2", 64, P); e("1.4 16V", 75, P); e("1.4 TDI", 70, D); e("1.4 TDI", 80, D); e("1.9 TDI", 100, D) }
                gen("V (6R/6C)", "2009–2017") { e("1.2", 70, P); e("1.2 TSI", 90, P); e("1.2 TSI", 105, P); e("1.0 TSI", 95, P); e("1.4 TSI GTI", 180, P); e("1.8 TSI GTI", 192, P); e("1.2 TDI", 75, D); e("1.6 TDI", 90, D); e("1.4 TDI", 90, D) }
                gen("VI (AW)", "2017–") { e("1.0 MPI", 80, P); e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("2.0 TSI GTI", 207, P); e("1.6 TDI", 95, D) }
            }
            model("T-Cross") {
                gen("I", "2019–") { e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("1.5 TSI", 150, P); e("1.6 TDI", 95, D) }
            }
            model("Taigo") {
                gen("I", "2021–") { e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("1.5 TSI", 150, P) }
            }
            model("Golf") {
                gen("III", "1991–1997") { e("1.4", 60, P); e("1.6", 75, P); e("1.8", 90, P); e("2.0 GTI", 115, P); e("2.8 VR6", 174, P); e("1.9 TDI", 90, D); e("1.9 TDI", 110, D) }
                gen("IV", "1997–2006") { e("1.4 16V", 75, P); e("1.6", 100, P); e("1.8 T GTI", 150, P); e("1.9 SDI", 68, D); e("1.9 TDI", 90, D); e("1.9 TDI", 110, D); e("1.9 TDI", 130, D) }
                gen("V", "2003–2008") { e("1.4", 80, P); e("1.6 FSI", 115, P); e("1.4 TSI", 140, P); e("2.0 TFSI GTI", 200, P); e("1.9 TDI", 105, D); e("2.0 TDI", 140, D) }
                gen("VI", "2008–2012") { e("1.2 TSI", 105, P); e("1.4 TSI", 122, P); e("1.4 TSI", 160, P); e("2.0 TSI GTI", 210, P); e("1.6 TDI", 105, D); e("2.0 TDI", 140, D); e("2.0 TDI GTD", 170, D) }
                gen("VII", "2012–2020") { e("1.0 TSI", 110, P); e("1.2 TSI", 110, P); e("1.4 TSI", 125, P); e("1.4 TSI", 150, P); e("1.5 TSI", 130, P); e("1.5 TSI", 150, P); e("2.0 TSI GTI", 230, P); e("2.0 TSI GTI", 245, P); e("2.0 TSI R", 300, P); e("1.6 TDI", 115, D); e("2.0 TDI", 150, D); e("2.0 TDI GTD", 184, D); e("1.4 TSI GTE", 204, R) }
                gen("VIII", "2019–") { e("1.0 eTSI", 110, H); e("1.5 TSI", 130, P); e("1.5 eTSI", 150, H); e("2.0 TSI GTI", 245, P); e("2.0 TSI R", 320, P); e("2.0 TDI", 115, D); e("2.0 TDI", 150, D); e("2.0 TDI GTD", 200, D); e("1.4 TSI GTE", 245, R) }
            }
            model("T-Roc") {
                gen("I", "2017–") { e("1.0 TSI", 110, P); e("1.5 TSI", 150, P); e("2.0 TSI", 190, P); e("2.0 TSI R", 300, P); e("1.6 TDI", 115, D); e("2.0 TDI", 150, D) }
            }
            model("Touran") {
                gen("II", "2015–") { e("1.2 TSI", 110, P); e("1.4 TSI", 150, P); e("1.5 TSI", 150, P); e("1.6 TDI", 115, D); e("2.0 TDI", 150, D); e("2.0 TDI", 190, D) }
            }
            model("Tiguan") {
                gen("I", "2007–2016") { e("1.4 TSI", 122, P); e("1.4 TSI", 150, P); e("2.0 TSI", 180, P); e("2.0 TDI", 110, D); e("2.0 TDI", 140, D); e("2.0 TDI", 170, D) }
                gen("II", "2016–2024") { e("1.4 TSI", 150, P); e("1.5 TSI", 130, P); e("1.5 TSI", 150, P); e("2.0 TSI", 190, P); e("2.0 TDI", 150, D); e("2.0 TDI", 190, D); e("1.4 TSI eHybrid", 245, R) }
            }
            model("Passat") {
                gen("B6", "2005–2010") { e("1.6 FSI", 115, P); e("2.0 FSI", 150, P); e("1.4 TSI", 122, P); e("1.8 TSI", 160, P); e("1.9 TDI", 105, D); e("2.0 TDI", 140, D); e("2.0 TDI", 170, D) }
                gen("B7", "2010–2014") { e("1.4 TSI", 122, P); e("1.8 TSI", 160, P); e("2.0 TSI", 210, P); e("1.6 TDI", 105, D); e("2.0 TDI", 140, D); e("2.0 TDI", 170, D) }
                gen("B8", "2014–2023") { e("1.4 TSI", 150, P); e("1.5 TSI", 150, P); e("2.0 TSI", 220, P); e("1.6 TDI", 120, D); e("2.0 TDI", 150, D); e("2.0 TDI", 190, D); e("1.4 TSI GTE", 218, R) }
            }
            model("Arteon") {
                gen("I", "2017–") { e("1.5 TSI", 150, P); e("2.0 TSI", 190, P); e("2.0 TSI R", 320, P); e("2.0 TDI", 150, D); e("2.0 TDI", 200, D); e("1.4 eHybrid", 218, R) }
            }
            model("Caddy") {
                gen("IV", "2015–2020") { e("1.0 TSI", 102, P); e("1.4 TSI", 125, P); e("2.0 TDI", 75, D); e("2.0 TDI", 102, D); e("2.0 TDI", 122, D); e("2.0 TDI", 150, D) }
                gen("V", "2020–") { e("1.5 TSI", 114, P); e("2.0 TDI", 75, D); e("2.0 TDI", 102, D); e("2.0 TDI", 122, D) }
            }
            model("Transporter") {
                gen("T6", "2015–") { e("2.0 TSI", 150, P); e("2.0 TDI", 102, D); e("2.0 TDI", 150, D); e("2.0 TDI", 204, D) }
            }
        },
        brand("Volvo") {
            model("S60") {
                gen("II", "2010–2018") { e("T3", 150, P); e("T4", 180, P); e("T5", 245, P); e("D2", 115, D); e("D3", 150, D); e("D4", 181, D) }
            }
            model("V40") {
                gen("II", "2012–2019") { e("T2", 122, P); e("T3", 152, P); e("T4", 190, P); e("T5", 245, P); e("D2", 120, D); e("D3", 150, D); e("D4", 190, D) }
            }
            model("V60") {
                gen("II", "2018–") { e("B4", 197, H); e("D3", 150, D); e("D4", 190, D); e("T6 Recharge", 340, R) }
            }
            model("V70") {
                gen("III", "2007–2016") { e("2.0", 145, P); e("T5", 240, P); e("D3", 163, D); e("D5", 215, D) }
            }
            model("XC40") {
                gen("I", "2017–") { e("T3", 163, P); e("T4", 190, P); e("B3", 163, H); e("B4", 197, H); e("D3", 150, D); e("D4", 190, D); e("T5 Recharge", 262, R) }
            }
            model("XC60") {
                gen("I", "2008–2017") { e("T5", 245, P); e("D3", 163, D); e("D4", 181, D); e("D5", 215, D) }
                gen("II", "2017–") { e("B4", 197, H); e("B5", 250, H); e("D4", 190, D); e("B4 Diesel", 197, D); e("T6 Recharge", 350, R); e("T8 Recharge", 455, R) }
            }
            model("XC90") {
                gen("II", "2015–") { e("B5", 235, H); e("D5", 235, D); e("T8 Recharge", 455, R) }
            }
        },
        brand(OTHER) {
            model("Véhicule OBD2 générique") {
                gen("Essence", "2001–") { e("Moteur essence", 0, P) }
                gen("Diesel", "2004–") { e("Moteur diesel", 0, D) }
                gen("Hybride", "") { e("Moteur hybride", 0, H) }
                gen("GPL", "") { e("Moteur essence / GPL", 0, G) }
                gen("E85", "") { e("Moteur flexfuel E85", 0, F) }
            }
        },
    )
}

/** Toutes les motorisations du catalogue. */
val CATALOG_TEMPLATES: List<VehicleTemplate> by lazy {
    CATALOG.flatMap { b -> b.models.flatMap { m -> m.generations.flatMap { it.variants } } }
}

/** Texte de recherche (sans accents ni majuscules) d'une motorisation. */
fun searchKey(b: CatalogBrand, m: CatalogModel, g: CatalogGeneration, t: VehicleTemplate): String =
    slug("${b.name} ${m.name} ${g.name} ${g.years} ${t.model} ${t.code} ${t.engine} ${t.power} ${t.fuel}").replace('-', ' ')

/** Une motorisation trouvée par la recherche, avec son emplacement dans le catalogue. */
data class CatalogHit(val brand: CatalogBrand, val model: CatalogModel, val generation: CatalogGeneration, val template: VehicleTemplate)

/** Recherche plein texte : chaque mot saisi doit être le début d'un mot (marque, modèle, génération, moteur, puissance…). */
fun searchCatalog(query: String, limit: Int = 60): List<CatalogHit> {
    val words = slug(query).split('-').filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val out = mutableListOf<CatalogHit>()
    for (b in CATALOG) for (m in b.models) for (g in m.generations) for (t in g.variants) {
        val tokens = searchKey(b, m, g, t).split(' ').filter { it.isNotEmpty() }
        if (words.all { w -> tokens.any { it.startsWith(w) } }) {
            out += CatalogHit(b, m, g, t)
            if (out.size >= limit) return out
        }
    }
    return out
}
