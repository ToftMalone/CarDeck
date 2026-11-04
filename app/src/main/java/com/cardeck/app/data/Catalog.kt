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

private fun brand(name: String, block: BrandBuilder.() -> Unit): CatalogBrand {
    val b = BrandBuilder(name).apply(block)
    return CatalogBrand(
        b.name,
        b.models.map { m ->
            CatalogModel(m.name, m.gens.map { g -> CatalogGeneration(g.name, g.years, g.dedicated + g.engines.map { generic(b.name, m.name, g, it) }) })
        },
    )
}

/** Toutes les marques, par ordre alphabétique ; « Autre véhicule » en dernier. */
val CATALOG: List<CatalogBrand> by lazy {
    listOf(
        brand("Alfa Romeo") {
            model("MiTo") {
                gen("MiTo", "2008–2018") { e("1.4", 78, P); e("1.4 TB", 120, P); e("1.4 MultiAir", 135, P); e("1.4 MultiAir QV", 170, P); e("1.3 JTDm", 85, D); e("1.3 JTDm", 95, D) }
            }
            model("Giulietta") {
                gen("Giulietta", "2010–2020") { e("1.4 TB", 120, P); e("1.4 MultiAir", 150, P); e("1.4 MultiAir", 170, P); e("1750 TBi", 240, P); e("1.6 JTDm", 120, D); e("2.0 JTDm", 150, D); e("2.0 JTDm", 175, D) }
            }
            model("Giulia") {
                gen("Giulia", "2016–") { e("2.0 Turbo", 200, P); e("2.0 Turbo", 280, P); e("2.9 V6 Quadrifoglio", 510, P); e("2.2 Diesel", 160, D); e("2.2 Diesel", 190, D); e("2.2 Diesel", 210, D) }
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
            model("Q3") {
                gen("F3", "2018–") { e("35 TFSI", 150, P); e("40 TFSI", 190, P); e("35 TDI", 150, D); e("40 TDI", 200, D); e("45 TFSI e", 245, R) }
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
            model("X1") {
                gen("F48", "2015–2022") { e("sDrive18i", 140, P); e("sDrive20i", 192, P); e("sDrive18d", 150, D); e("xDrive20d", 190, D); e("xDrive25e", 220, R) }
            }
        },
        brand("Citroën") {
            model("C1") {
                gen("I", "2005–2014") { e("1.0", 68, P); e("1.4 HDi", 54, D) }
                gen("II", "2014–2022") { e("1.0 VTi", 72, P); e("1.2 PureTech", 82, P) }
            }
            model("C3") {
                gen("II", "2009–2016") { e("1.1", 60, P); e("1.4 VTi", 95, P); e("1.6 VTi", 120, P); e("1.2 PureTech", 82, P); e("1.4 HDi", 70, D); e("1.6 HDi", 90, D); e("1.6 BlueHDi", 100, D) }
                gen("III", "2016–2024") { e("1.2 PureTech", 68, P); e("1.2 PureTech", 83, P); e("1.2 PureTech", 110, P); e("1.6 BlueHDi", 75, D); e("1.5 BlueHDi", 100, D) }
            }
            model("C3 Aircross") {
                gen("I", "2017–2024") { e("1.2 PureTech", 82, P); e("1.2 PureTech", 110, P); e("1.2 PureTech", 130, P); e("1.5 BlueHDi", 110, D); e("1.5 BlueHDi", 120, D) }
            }
            model("C4") {
                gen("II", "2010–2018") { e("1.4 VTi", 95, P); e("1.6 VTi", 120, P); e("1.6 THP", 155, P); e("1.2 PureTech", 130, P); e("1.6 HDi", 92, D); e("1.6 e-HDi", 115, D); e("1.6 BlueHDi", 120, D) }
                gen("III", "2020–") { e("1.2 PureTech", 100, P); e("1.2 PureTech", 130, P); e("1.2 PureTech", 155, P); e("1.5 BlueHDi", 110, D); e("1.5 BlueHDi", 130, D); e("1.2 Hybrid", 136, H) }
            }
            model("C5 Aircross") {
                gen("I", "2018–") { e("1.2 PureTech", 130, P); e("1.6 PureTech", 180, P); e("1.5 BlueHDi", 130, D); e("2.0 BlueHDi", 180, D); e("1.6 Hybrid", 225, R) }
            }
            model("Berlingo") {
                gen("II", "2008–2018") { e("1.6 VTi", 120, P); e("1.6 HDi", 75, D); e("1.6 HDi", 90, D); e("1.6 BlueHDi", 100, D); e("1.6 BlueHDi", 120, D) }
                gen("III", "2018–") { e("1.2 PureTech", 110, P); e("1.5 BlueHDi", 75, D); e("1.5 BlueHDi", 100, D); e("1.5 BlueHDi", 130, D) }
            }
        },
        brand("Dacia") {
            model("Sandero") {
                gen("II", "2012–2020") { e("1.2 16V", 75, P); e("1.0 SCe", 75, P); e("0.9 TCe", 90, P); e("0.9 TCe GPL", 90, G); e("1.5 dCi", 75, D); e("1.5 dCi", 90, D) }
                gen("III", "2020–") { e("1.0 SCe", 65, P); e("1.0 TCe", 90, P); e("1.0 TCe", 110, P); e("1.0 ECO-G", 100, G) }
            }
            model("Logan") {
                gen("II", "2012–2020") { e("1.2 16V", 75, P); e("0.9 TCe", 90, P); e("1.5 dCi", 90, D) }
            }
            model("Duster") {
                gen("I", "2010–2017") { e("1.6 16V", 105, P); e("1.6 SCe", 115, P); e("1.2 TCe", 125, P); e("1.5 dCi", 90, D); e("1.5 dCi", 110, D) }
                gen("II", "2017–2024") { e("1.6 SCe", 115, P); e("1.0 TCe", 100, P); e("1.0 ECO-G", 100, G); e("1.3 TCe", 130, P); e("1.3 TCe", 150, P); e("1.5 Blue dCi", 115, D) }
                gen("III", "2024–") { e("1.2 TCe mild hybrid", 130, P); e("1.0 ECO-G", 100, G); e("1.6 Hybrid", 140, H) }
            }
            model("Jogger") {
                gen("I", "2022–") { e("1.0 TCe", 110, P); e("1.0 ECO-G", 100, G); e("1.6 Hybrid", 140, H) }
            }
        },
        brand("DS") {
            model("DS 3") {
                gen("I", "2009–2019") { e("1.2 PureTech", 82, P); e("1.2 PureTech", 110, P); e("1.6 VTi", 120, P); e("1.6 THP", 165, P); e("1.6 THP Performance", 208, P); e("1.6 HDi", 92, D); e("1.6 BlueHDi", 120, D) }
            }
            model("DS 7") {
                gen("I", "2017–") { e("1.2 PureTech", 130, P); e("1.6 PureTech", 225, P); e("1.5 BlueHDi", 130, D); e("2.0 BlueHDi", 180, D); e("E-Tense", 225, R); e("E-Tense 4x4", 300, R) }
            }
        },
        brand("Fiat") {
            model("500") {
                gen("312", "2007–2024") { e("1.2 8V", 69, P); e("1.4 16V", 100, P); e("0.9 TwinAir", 85, P); e("1.0 Hybrid", 70, H); e("1.3 Multijet", 95, D) }
            }
            model("500 Abarth / 595") {
                gen("312", "2008–2024") { e("1.4 T-Jet", 135, P); e("1.4 T-Jet", 145, P); e("1.4 T-Jet", 165, P); e("1.4 T-Jet", 180, P) }
            }
            model("Panda") {
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
            model("Fiesta") {
                gen("VI", "2008–2017") { e("1.25", 82, P); e("1.4", 96, P); e("1.6 Ti-VCT", 120, P); e("1.0 EcoBoost", 100, P); e("1.0 EcoBoost", 125, P); e("1.6 EcoBoost ST", 182, P); e("1.4 TDCi", 70, D); e("1.5 TDCi", 95, D); e("1.6 TDCi", 95, D) }
                gen("VII", "2017–2023") { e("1.1", 85, P); e("1.0 EcoBoost", 100, P); e("1.0 EcoBoost", 125, P); e("1.0 EcoBoost mHEV", 125, H); e("1.5 EcoBoost ST", 200, P); e("1.5 TDCi", 85, D) }
            }
            model("Focus") {
                gen("II", "2004–2011") { e("1.6", 100, P); e("1.6 Ti-VCT", 115, P); e("1.8", 125, P); e("2.5 ST", 225, P); e("1.6 TDCi", 109, D); e("1.8 TDCi", 115, D); e("2.0 TDCi", 136, D); e("1.8 Flexifuel", 125, F) }
                gen("III", "2011–2018") { e("1.6 Ti-VCT", 105, P); e("1.0 EcoBoost", 100, P); e("1.0 EcoBoost", 125, P); e("1.5 EcoBoost", 150, P); e("2.0 EcoBoost ST", 250, P); e("2.3 EcoBoost RS", 350, P); e("1.5 TDCi", 120, D); e("1.6 TDCi", 115, D); e("2.0 TDCi", 150, D) }
                gen("IV", "2018–") { e("1.0 EcoBoost", 125, P); e("1.0 EcoBoost mHEV", 155, H); e("1.5 EcoBoost", 150, P); e("2.3 EcoBoost ST", 280, P); e("1.5 EcoBlue", 120, D); e("2.0 EcoBlue", 150, D) }
            }
            model("Puma") {
                gen("II", "2019–") { e("1.0 EcoBoost", 125, P); e("1.0 EcoBoost mHEV", 125, H); e("1.0 EcoBoost mHEV", 155, H); e("1.5 EcoBoost ST", 200, P) }
            }
            model("Kuga") {
                gen("II", "2012–2019") { e("1.5 EcoBoost", 150, P); e("1.5 EcoBoost", 182, P); e("1.5 TDCi", 120, D); e("2.0 TDCi", 150, D); e("2.0 TDCi", 180, D) }
                gen("III", "2019–") { e("1.5 EcoBoost", 150, P); e("2.5 Hybrid", 190, H); e("2.5 PHEV", 225, R); e("1.5 EcoBlue", 120, D); e("2.0 EcoBlue mHEV", 150, D) }
            }
        },
        brand("Honda") {
            model("Jazz") {
                gen("III", "2008–2015") { e("1.2 i-VTEC", 90, P); e("1.4 i-VTEC", 100, P); e("1.3 Hybrid", 88, H) }
                gen("IV", "2015–2020") { e("1.3 i-VTEC", 102, P) }
                gen("V", "2020–") { e("1.5 e:HEV", 109, H); e("1.5 e:HEV", 122, H) }
            }
            model("Civic") {
                gen("IX", "2012–2017") { e("1.4 i-VTEC", 100, P); e("1.8 i-VTEC", 142, P); e("2.0 Type R", 310, P); e("1.6 i-DTEC", 120, D); e("2.2 i-DTEC", 150, D) }
                gen("X", "2017–2022") { e("1.0 VTEC Turbo", 129, P); e("1.5 VTEC Turbo", 182, P); e("2.0 Type R", 320, P); e("1.6 i-DTEC", 120, D) }
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
            model("i30") {
                gen("III", "2017–") { e("1.0 T-GDi", 120, P); e("1.4 T-GDi", 140, P); e("1.5 T-GDi mHEV", 160, H); e("2.0 T-GDi N", 280, P); e("1.6 CRDi", 115, D); e("1.6 CRDi", 136, D) }
            }
            model("Kona") {
                gen("I", "2017–2023") { e("1.0 T-GDi", 120, P); e("1.6 T-GDi", 198, P); e("1.6 GDi Hybrid", 141, H); e("1.6 CRDi", 136, D) }
            }
            model("Tucson") {
                gen("III", "2015–2020") { e("1.6 GDi", 132, P); e("1.6 T-GDi", 177, P); e("1.7 CRDi", 115, D); e("1.6 CRDi", 136, D); e("2.0 CRDi", 185, D) }
                gen("IV", "2020–") { e("1.6 T-GDi mHEV", 150, H); e("1.6 T-GDi Hybrid", 230, H); e("1.6 T-GDi Plug-in", 265, R); e("1.6 CRDi", 136, D) }
            }
        },
        brand("Jeep") {
            model("Renegade") {
                gen("BU", "2014–") { e("1.4 MultiAir", 140, P); e("1.0 T3", 120, P); e("1.3 T4", 150, P); e("1.6 MultiJet", 120, D); e("2.0 MultiJet", 140, D); e("1.3 4xe", 240, R) }
            }
            model("Compass") {
                gen("II", "2017–") { e("1.4 MultiAir", 140, P); e("1.3 T4", 150, P); e("1.6 MultiJet", 120, D); e("2.0 MultiJet", 140, D); e("1.3 4xe", 240, R) }
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
                gen("III", "2018–") { e("1.0 T-GDi", 120, P); e("1.4 T-GDi", 140, P); e("1.5 T-GDi", 160, P); e("1.6 T-GDi GT", 204, P); e("1.6 CRDi", 115, D); e("1.6 CRDi", 136, D) }
            }
            model("Niro") {
                gen("I", "2016–2022") { e("1.6 GDi Hybrid", 141, H); e("1.6 GDi Plug-in", 141, R) }
            }
            model("Sportage") {
                gen("IV", "2016–2021") { e("1.6 GDi", 132, P); e("1.6 T-GDi", 177, P); e("1.7 CRDi", 115, D); e("1.6 CRDi", 136, D); e("2.0 CRDi", 185, D) }
                gen("V", "2021–") { e("1.6 T-GDi mHEV", 150, H); e("1.6 T-GDi Hybrid", 230, H); e("1.6 T-GDi Plug-in", 265, R); e("1.6 CRDi", 136, D) }
            }
        },
        brand("Mazda") {
            model("Mazda2") {
                gen("III", "2014–") { e("1.5 Skyactiv-G", 75, P); e("1.5 Skyactiv-G", 90, P); e("1.5 Skyactiv-G", 115, P); e("1.5 Skyactiv-D", 105, D) }
            }
            model("Mazda3") {
                gen("III", "2013–2019") { e("1.5 Skyactiv-G", 100, P); e("2.0 Skyactiv-G", 120, P); e("2.0 Skyactiv-G", 165, P); e("1.5 Skyactiv-D", 105, D); e("2.2 Skyactiv-D", 150, D) }
                gen("IV", "2019–") { e("2.0 e-Skyactiv G", 122, H); e("2.5 e-Skyactiv G", 140, H); e("2.0 e-Skyactiv X", 186, H); e("1.8 Skyactiv-D", 116, D) }
            }
            model("CX-5") {
                gen("II", "2017–") { e("2.0 Skyactiv-G", 165, P); e("2.5 Skyactiv-G", 194, P); e("2.2 Skyactiv-D", 150, D); e("2.2 Skyactiv-D", 184, D) }
            }
            model("MX-5") {
                gen("ND", "2015–") { e("1.5 Skyactiv-G", 132, P); e("2.0 Skyactiv-G", 184, P) }
            }
        },
        brand("Mercedes-Benz") {
            model("Classe A") {
                gen("W176", "2012–2018") { e("A 160", 102, P); e("A 180", 122, P); e("A 200", 156, P); e("A 250", 218, P); e("A 45 AMG", 381, P); e("A 180 d", 109, D); e("A 200 d", 136, D); e("A 220 d", 177, D) }
                gen("W177", "2018–") { e("A 180", 136, P); e("A 200", 163, P); e("A 250", 224, P); e("A 35 AMG", 306, P); e("A 180 d", 116, D); e("A 200 d", 150, D); e("A 220 d", 190, D); e("A 250 e", 218, R) }
            }
            model("Classe C") {
                gen("W204", "2007–2014") { e("C 180", 156, P); e("C 200", 184, P); e("C 250", 204, P); e("C 200 CDI", 136, D); e("C 220 CDI", 170, D); e("C 250 CDI", 204, D) }
                gen("W205", "2014–2021") { e("C 180", 156, P); e("C 200", 184, P); e("C 300", 258, P); e("C 200 d", 160, D); e("C 220 d", 194, D); e("C 300 e", 320, R) }
            }
            model("GLA") {
                gen("X156", "2013–2019") { e("GLA 180", 122, P); e("GLA 200", 156, P); e("GLA 250", 211, P); e("GLA 200 d", 136, D); e("GLA 220 d", 177, D) }
            }
        },
        brand("Mini") {
            model("Mini 3 portes / 5 portes") {
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
        },
        brand("Nissan") {
            model("Micra") {
                gen("K12", "2003–2010") { e("1.2", 80, P); e("1.4", 88, P); e("1.5 dCi", 86, D) }
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
        },
        brand("Opel") {
            model("Corsa") {
                gen("D", "2006–2014") { e("1.0", 65, P); e("1.2", 85, P); e("1.4", 100, P); e("1.6 Turbo OPC", 192, P); e("1.3 CDTI", 75, D); e("1.3 CDTI", 95, D) }
                gen("E", "2014–2019") { e("1.2", 70, P); e("1.4", 90, P); e("1.0 Turbo", 90, P); e("1.0 Turbo", 115, P); e("1.4 Turbo", 150, P); e("1.6 Turbo OPC", 207, P); e("1.3 CDTI", 75, D); e("1.3 CDTI", 95, D) }
                gen("F", "2019–") { e("1.2", 75, P); e("1.2 Turbo", 100, P); e("1.2 Turbo", 130, P); e("1.5 Diesel", 100, D); e("1.2 Hybrid", 100, H) }
            }
            model("Astra") {
                gen("J", "2009–2015") { e("1.4", 100, P); e("1.4 Turbo", 140, P); e("1.6 Turbo", 180, P); e("2.0 Turbo OPC", 280, P); e("1.7 CDTI", 110, D); e("1.7 CDTI", 130, D); e("2.0 CDTI", 165, D) }
                gen("K", "2015–2021") { e("1.0 Turbo", 105, P); e("1.4 Turbo", 125, P); e("1.4 Turbo", 150, P); e("1.2 Turbo", 130, P); e("1.6 CDTI", 110, D); e("1.6 CDTI", 136, D); e("1.5 Diesel", 122, D) }
                gen("L", "2021–") { e("1.2 Turbo", 110, P); e("1.2 Turbo", 130, P); e("1.5 Diesel", 130, D); e("1.6 Hybrid", 180, R) }
            }
            model("Crossland") {
                gen("X", "2017–2024") { e("1.2", 83, P); e("1.2 Turbo", 110, P); e("1.2 Turbo", 130, P); e("1.5 Diesel", 110, D); e("1.5 Diesel", 120, D) }
            }
            model("Mokka") {
                gen("B", "2020–") { e("1.2 Turbo", 100, P); e("1.2 Turbo", 130, P); e("1.5 Diesel", 110, D) }
            }
        },
        brand("Peugeot") {
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
            model("308") {
                gen("I", "2007–2013") { e("1.4 VTi", 95, P); e("1.6 VTi", 120, P); e("1.6 THP", 156, P); e("1.6 HDi", 92, D); e("1.6 HDi", 110, D); e("2.0 HDi", 136, D) }
                gen("II", "2013–2021") { e("1.2 PureTech", 110, P); e("1.2 PureTech", 130, P); e("1.6 THP GT", 205, P); e("1.6 THP GTi", 270, P); e("1.6 BlueHDi", 120, D); e("1.5 BlueHDi", 130, D); e("2.0 BlueHDi", 150, D); e("2.0 BlueHDi", 180, D) }
                gen("III", "2021–") { e("1.2 PureTech", 110, P); e("1.2 PureTech", 130, P); e("1.5 BlueHDi", 130, D); e("1.6 Hybrid", 180, R); e("1.6 Hybrid", 225, R) }
            }
            model("3008") {
                gen("I", "2009–2016") { e("1.6 VTi", 120, P); e("1.6 THP", 156, P); e("1.6 HDi", 115, D); e("2.0 HDi", 163, D) }
                gen("II", "2016–2023") { e("1.2 PureTech", 130, P); e("1.6 PureTech", 180, P); e("1.5 BlueHDi", 130, D); e("2.0 BlueHDi", 180, D); e("1.6 Hybrid", 225, R); e("1.6 Hybrid4", 300, R) }
            }
            model("5008") {
                gen("II", "2017–2023") { e("1.2 PureTech", 130, P); e("1.6 PureTech", 180, P); e("1.5 BlueHDi", 130, D); e("2.0 BlueHDi", 180, D) }
            }
        },
        brand("Renault") {
            model("Twingo") {
                gen("II", "2007–2014") { e("1.2", 75, P); e("1.2 TCe", 100, P); e("1.6 RS", 133, P); e("1.5 dCi", 85, D) }
                gen("III", "2014–2024") { e("1.0 SCe", 65, P); e("1.0 SCe", 70, P); e("0.9 TCe", 90, P) }
            }
            model("Clio") {
                gen("III", "2005–2014") { e("1.2 16V", 75, P); e("1.2 TCe", 100, P); e("1.6 16V", 112, P); e("2.0 RS", 200, P); e("1.5 dCi", 70, D); e("1.5 dCi", 85, D); e("1.5 dCi", 105, D) }
                gen("IV", "2012–2019") { e("1.2 16V", 75, P); e("0.9 TCe", 90, P); e("1.2 TCe", 120, P); e("1.6 Turbo RS", 200, P); e("1.6 Turbo RS Trophy", 220, P); e("1.5 dCi", 75, D); e("1.5 dCi", 90, D); e("1.5 dCi", 110, D) }
                gen("V", "2019–") { e("1.0 SCe", 65, P); e("1.0 TCe", 90, P); e("1.0 TCe", 100, P); e("1.0 TCe GPL", 100, G); e("1.3 TCe", 130, P); e("1.6 E-Tech", 145, H); e("1.5 Blue dCi", 85, D); e("1.5 Blue dCi", 100, D) }
            }
            model("Captur") {
                gen("I", "2013–2019") { e("0.9 TCe", 90, P); e("1.2 TCe", 120, P); e("1.3 TCe", 130, P); e("1.3 TCe", 150, P); e("1.5 dCi", 90, D); e("1.5 dCi", 110, D) }
                gen("II", "2019–") { e("1.0 TCe", 90, P); e("1.0 TCe GPL", 100, G); e("1.3 TCe", 140, P); e("1.3 TCe", 160, P); e("1.6 E-Tech", 145, H); e("1.6 E-Tech Plug-in", 160, R); e("1.5 Blue dCi", 95, D); e("1.5 Blue dCi", 115, D) }
            }
            model("Mégane") {
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
                gen("II", "2008–2021") { e("1.6 16V", 105, P); e("1.2 TCe", 115, P); e("1.5 dCi", 75, D); e("1.5 dCi", 90, D); e("1.5 dCi", 110, D) }
                gen("III", "2021–") { e("1.3 TCe", 100, P); e("1.3 TCe", 130, P); e("1.5 Blue dCi", 95, D); e("1.5 Blue dCi", 115, D) }
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
            model("Leon") {
                gen("III", "2012–2020") { e("1.2 TSI", 110, P); e("1.4 TSI", 125, P); e("1.4 TSI", 150, P); e("1.5 TSI", 150, P); e("2.0 TSI Cupra", 300, P); e("1.6 TDI", 110, D); e("1.6 TDI", 115, D); e("2.0 TDI", 150, D); e("2.0 TDI", 184, D) }
                gen("IV", "2020–") { e("1.0 eTSI", 110, H); e("1.5 TSI", 130, P); e("1.5 TSI", 150, P); e("2.0 TDI", 115, D); e("2.0 TDI", 150, D); e("1.4 e-Hybrid", 204, R) }
            }
        },
        brand("Skoda") {
            model("Fabia") {
                gen("II", "2007–2014") { e("1.2", 70, P); e("1.2 TSI", 86, P); e("1.2 TSI", 105, P); e("1.4 TSI RS", 180, P); e("1.6 TDI", 90, D); e("1.6 TDI", 105, D) }
                gen("III", "2014–2021") { e("1.0 MPI", 75, P); e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("1.2 TSI", 90, P); e("1.2 TSI", 110, P); e("1.4 TDI", 90, D) }
                gen("IV", "2021–") { e("1.0 MPI", 80, P); e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("1.5 TSI", 150, P) }
            }
            model("Octavia") {
                gen("III", "2013–2020") { e("1.0 TSI", 115, P); e("1.2 TSI", 110, P); e("1.4 TSI", 150, P); e("1.5 TSI", 150, P); e("2.0 TSI RS", 245, P); e("1.6 TDI", 115, D); e("2.0 TDI", 150, D); e("2.0 TDI RS", 184, D) }
                gen("IV", "2020–") { e("1.0 eTSI", 110, H); e("1.5 TSI", 150, P); e("2.0 TSI RS", 245, P); e("2.0 TDI", 115, D); e("2.0 TDI", 150, D); e("1.4 iV", 204, R) }
            }
            model("Karoq") {
                gen("I", "2017–") { e("1.0 TSI", 115, P); e("1.5 TSI", 150, P); e("2.0 TDI", 115, D); e("2.0 TDI", 150, D) }
            }
        },
        brand("Suzuki") {
            model("Swift") {
                gen("IV", "2010–2017") { e("1.2 VVT", 94, P); e("1.3 DDiS", 75, D) }
                gen("Sport ZC32S", "2012–2017") { e("1.6 VVT", 136, P) }
                gen("V", "2017–2024") { e("1.2 Dualjet", 90, P); e("1.0 BoosterJet", 111, P); e("1.2 Dualjet SHVS", 83, H); e("1.2 Dualjet SHVS", 90, H) }
                gen("Sport ZC33S", "2018–2024") { dedicated += SWIFT_SPORT_ZC33S }
                gen("VI", "2024–") { e("1.2 Dualjet Hybrid", 83, H) }
            }
            model("Ignis") {
                gen("III", "2016–") { e("1.2 Dualjet", 90, P); e("1.2 Dualjet SHVS", 83, H) }
            }
            model("Vitara") {
                gen("IV", "2015–") { e("1.6 VVT", 120, P); e("1.0 BoosterJet", 111, P); e("1.4 BoosterJet", 140, P); e("1.4 BoosterJet mild hybrid", 129, H); e("1.5 Hybrid", 116, H); e("1.6 DDiS", 120, D) }
            }
            model("S-Cross") {
                gen("I", "2013–2021") { e("1.6 VVT", 120, P); e("1.0 BoosterJet", 111, P); e("1.4 BoosterJet", 140, P); e("1.6 DDiS", 120, D) }
            }
            model("Jimny") {
                gen("IV", "2018–") { e("1.5 VVT", 102, P) }
            }
        },
        brand("Toyota") {
            model("Aygo") {
                gen("I", "2005–2014") { e("1.0 VVT-i", 68, P); e("1.4 D-4D", 54, D) }
                gen("II", "2014–2022") { e("1.0 VVT-i", 72, P) }
                gen("Aygo X", "2022–") { e("1.0 VVT-i", 72, P) }
            }
            model("Yaris") {
                gen("II", "2005–2011") { e("1.0 VVT-i", 69, P); e("1.33 Dual VVT-i", 101, P); e("1.4 D-4D", 90, D) }
                gen("III", "2011–2020") { e("1.0 VVT-i", 72, P); e("1.33 Dual VVT-i", 99, P); e("1.5 Dual VVT-iE", 111, P); e("1.5 Hybride", 100, H); e("1.4 D-4D", 90, D) }
                gen("IV", "2020–") { e("1.0 VVT-i", 72, P); e("1.5 Dynamic Force", 125, P); e("1.5 Hybride", 116, H); e("1.5 Hybride", 130, H) }
                gen("GR Yaris", "2020–") { e("1.6 Turbo", 261, P); e("1.6 Turbo", 280, P) }
            }
            model("Auris") {
                gen("II", "2012–2018") { e("1.33 Dual VVT-i", 99, P); e("1.2 T", 116, P); e("1.8 Hybride", 136, H); e("1.4 D-4D", 90, D); e("1.6 D-4D", 112, D) }
            }
            model("Corolla") {
                gen("E210", "2019–") { e("1.2 T", 116, P); e("1.8 Hybride", 122, H); e("1.8 Hybride", 140, H); e("2.0 Hybride", 184, H); e("2.0 Hybride", 196, H) }
            }
            model("C-HR") {
                gen("I", "2016–2023") { e("1.2 T", 116, P); e("1.8 Hybride", 122, H); e("2.0 Hybride", 184, H) }
            }
            model("RAV4") {
                gen("V", "2018–") { e("2.5 Hybride", 218, H); e("2.5 Hybride AWD-i", 222, H); e("2.5 Hybride rechargeable", 306, R) }
            }
        },
        brand("Volkswagen") {
            model("Up!") {
                gen("I", "2011–2023") { e("1.0 MPI", 60, P); e("1.0 MPI", 75, P); e("1.0 TSI", 90, P); e("1.0 TSI GTI", 115, P) }
            }
            model("Polo") {
                gen("V (6R/6C)", "2009–2017") { e("1.2", 70, P); e("1.2 TSI", 90, P); e("1.2 TSI", 105, P); e("1.0 TSI", 95, P); e("1.4 TSI GTI", 180, P); e("1.8 TSI GTI", 192, P); e("1.2 TDI", 75, D); e("1.6 TDI", 90, D); e("1.4 TDI", 90, D) }
                gen("VI (AW)", "2017–") { e("1.0 MPI", 80, P); e("1.0 TSI", 95, P); e("1.0 TSI", 110, P); e("2.0 TSI GTI", 207, P); e("1.6 TDI", 95, D) }
            }
            model("Golf") {
                gen("V", "2003–2008") { e("1.4", 80, P); e("1.6 FSI", 115, P); e("1.4 TSI", 140, P); e("2.0 TFSI GTI", 200, P); e("1.9 TDI", 105, D); e("2.0 TDI", 140, D) }
                gen("VI", "2008–2012") { e("1.2 TSI", 105, P); e("1.4 TSI", 122, P); e("1.4 TSI", 160, P); e("2.0 TSI GTI", 210, P); e("1.6 TDI", 105, D); e("2.0 TDI", 140, D); e("2.0 TDI GTD", 170, D) }
                gen("VII", "2012–2020") { e("1.0 TSI", 110, P); e("1.2 TSI", 110, P); e("1.4 TSI", 125, P); e("1.4 TSI", 150, P); e("1.5 TSI", 130, P); e("1.5 TSI", 150, P); e("2.0 TSI GTI", 230, P); e("2.0 TSI GTI", 245, P); e("2.0 TSI R", 300, P); e("1.6 TDI", 115, D); e("2.0 TDI", 150, D); e("2.0 TDI GTD", 184, D); e("1.4 TSI GTE", 204, R) }
                gen("VIII", "2019–") { e("1.0 eTSI", 110, H); e("1.5 TSI", 130, P); e("1.5 eTSI", 150, H); e("2.0 TSI GTI", 245, P); e("2.0 TSI R", 320, P); e("2.0 TDI", 115, D); e("2.0 TDI", 150, D); e("2.0 TDI GTD", 200, D); e("1.4 TSI GTE", 245, R) }
            }
            model("T-Roc") {
                gen("I", "2017–") { e("1.0 TSI", 110, P); e("1.5 TSI", 150, P); e("2.0 TSI", 190, P); e("2.0 TSI R", 300, P); e("1.6 TDI", 115, D); e("2.0 TDI", 150, D) }
            }
            model("Tiguan") {
                gen("II", "2016–2024") { e("1.4 TSI", 150, P); e("1.5 TSI", 130, P); e("1.5 TSI", 150, P); e("2.0 TSI", 190, P); e("2.0 TDI", 150, D); e("2.0 TDI", 190, D); e("1.4 TSI eHybrid", 245, R) }
            }
            model("Passat") {
                gen("B8", "2014–2023") { e("1.4 TSI", 150, P); e("1.5 TSI", 150, P); e("2.0 TSI", 220, P); e("1.6 TDI", 120, D); e("2.0 TDI", 150, D); e("2.0 TDI", 190, D); e("1.4 TSI GTE", 218, R) }
            }
        },
        brand("Volvo") {
            model("V40") {
                gen("II", "2012–2019") { e("T2", 122, P); e("T3", 152, P); e("T4", 190, P); e("T5", 245, P); e("D2", 120, D); e("D3", 150, D); e("D4", 190, D) }
            }
            model("XC40") {
                gen("I", "2017–") { e("T3", 163, P); e("T4", 190, P); e("B3", 163, H); e("B4", 197, H); e("D3", 150, D); e("D4", 190, D); e("T5 Recharge", 262, R) }
            }
            model("XC60") {
                gen("II", "2017–") { e("B4", 197, H); e("B5", 250, H); e("D4", 190, D); e("B4 Diesel", 197, D); e("T6 Recharge", 350, R); e("T8 Recharge", 455, R) }
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
