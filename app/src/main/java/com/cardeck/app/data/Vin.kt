package com.cardeck.app.data

import java.util.Calendar

/**
 * Ce qu'un numéro de série (VIN) permet de savoir sans connexion : le constructeur (3 premiers caractères, WMI),
 * l'année modèle (10e caractère) et, pour quelques modèles connus, le code du modèle (caractères 4 à 8).
 * Le modèle exact n'est pas dans le VIN de façon universelle : on propose alors les modèles de la marque
 * produits cette année-là.
 */
data class VinInfo(
    val vin: String,
    /** Constructeur d'après le WMI, même s'il n'est pas dans le catalogue (Tesla, Porsche…). */
    val maker: String?,
    /** Nom de la marque dans le catalogue, si elle y figure. */
    val brand: String?,
    /** Année modèle, seulement pour les constructeurs qui la codent au 10e caractère (sinon null). */
    val year: Int?,
    val wmi: String,
    /** Code du modèle (caractères 4 et suivants), pour les modèles dont il est connu. */
    val modelCode: String?,
    /** Motorisation identifiée avec certitude par le code du modèle (identifiant de profil). */
    val templateId: String?,
    /** Modèle et génération du catalogue identifiés par le code du modèle (la motorisation reste à choisir). */
    val model: String? = null,
    val generation: String? = null,
)

object Vin {
    private val VALID = Regex("^[A-HJ-NPR-Z0-9]{17}$")

    /** VIN valide (17 caractères, sans I, O ni Q) en majuscules, ou null. */
    fun normalize(raw: String?): String? = raw?.uppercase()?.filter { it.isLetterOrDigit() }?.takeIf { VALID.matches(it) }

    // Constructeur par WMI (3 caractères) ou préfixe de 2 caractères. Libellé du constructeur, nom de marque du catalogue (ou null).
    private val WMI: Map<String, Pair<String, String?>> = buildMap {
        fun add(maker: String, brand: String?, vararg codes: String) { codes.forEach { put(it, maker to brand) } }
        add("Renault", "Renault", "VF1", "VFA", "VFB", "KNM")
        add("Dacia", "Dacia", "UU1", "UU3", "UU6")
        add("Peugeot", "Peugeot", "VF3", "VR3")
        add("Citroën", "Citroën", "VF7", "VR7", "VS7")
        add("DS", "DS", "VR1")
        add("Opel", "Opel", "W0L", "W0V", "VXK", "VSX", "XUF")
        add("Volkswagen", "Volkswagen", "WVW", "WVG", "WV1", "WV2", "WV3", "3VW", "9BW", "AAV", "VWV", "1VW", "2V4")
        add("Audi", "Audi", "WAU", "WA1", "TRU", "93U")
        add("Skoda", "Skoda", "TMB", "TMP")
        add("Seat", "Seat", "VSS", "VS5")
        add("BMW", "BMW", "WBA", "WBS", "WBY", "WBX", "4US", "5UX", "5UM")
        add("Mini", "Mini", "WMW", "WMZ")
        add("Mercedes-Benz", "Mercedes-Benz", "WDB", "WDC", "WDD", "WDF", "WD1", "WD2", "WD3", "WD4", "W1K", "W1N", "W1V", "W1W", "W1X", "W1Y", "4JG", "55S")
        add("Smart", null, "WME")
        add("Porsche", null, "WP0", "WP1")
        add("Ford", "Ford", "WF0", "WFO", "VS6", "NM0", "MAJ", "1FA", "1FB", "1FC", "1FD", "1FM", "1FT", "3FA", "3FM", "2FM", "SFA")
        add("Fiat", "Fiat", "ZFA", "ZFC", "ZFB", "3C3")
        add("Alfa Romeo", "Alfa Romeo", "ZAR")
        add("Lancia", null, "ZLA")
        add("Ferrari", null, "ZFF")
        add("Maserati", null, "ZAM")
        add("Lamborghini", null, "ZHW")
        add("Jeep", "Jeep", "1J4", "1J8", "1C4", "ZAC", "3C4")
        add("Volvo", "Volvo", "YV1", "YV4", "LVV")
        add("Toyota", "Toyota", "JTD", "JTE", "JTM", "JTN", "JTK", "JT1", "JT2", "JT3", "JT4", "JT6", "JT7", "JT8", "SB1", "VNK", "NMT", "MR0", "AHT", "4T1", "5TD", "2T1", "TW1", "6T1")
        add("Lexus", null, "JTH", "JTJ", "2T2")
        add("Honda", "Honda", "JHM", "JHL", "JHG", "SHH", "SHS", "1HG", "2HG", "MRH", "NLA", "93H")
        add("Nissan", "Nissan", "JN1", "JN8", "SJN", "5N1", "3N1", "MNT", "VSK", "SJK")
        add("Infiniti", null, "JNK", "JNR")
        add("Mazda", "Mazda", "JMZ", "JM1", "JM3", "JM6", "4F2", "3MZ")
        add("Mitsubishi", "Mitsubishi", "JMB", "JMY", "JA3", "JA4", "JA7", "MMB", "MMC", "XMC", "4A3")
        add("Suzuki", "Suzuki", "JS2", "JS3", "JSA", "TSM", "MA3", "TSZ")
        add("Hyundai", "Hyundai", "KMH", "KMF", "TMA", "NLH", "MAL", "5NP", "5NM", "KMC")
        add("Kia", "Kia", "KNA", "KNC", "KND", "KNE", "KNJ", "U5Y", "U6Y", "5XY")
        add("Tesla", null, "5YJ", "7SA", "LRW", "XP7", "SFZ")
        add("Land Rover", null, "SAL")
        add("Jaguar", null, "SAJ")
        add("Subaru", null, "JF1", "JF2", "4S3", "4S4")
        add("Saab", null, "YS3")
        add("Lada", null, "XTA")
        add("Chevrolet", null, "1G1", "1GC", "KL1", "3G1")
        add("Chrysler", null, "1C3", "2C3")
        add("Dodge", null, "1B3", "2B3")
        add("Cadillac", null, "1G6")
        add("Isuzu", null, "JAA", "JAL")
        add("Daihatsu", null, "JDA", "ACV")
        add("MG", null, "LSJ", "SDL")
        add("BYD", null, "LGX")
        add("Geely / Polestar", null, "LB3", "LPS", "YSM")
        add("Smart / Mercedes", null, "WMX")
    }

    /** Constructeur d'après le WMI : (libellé, marque du catalogue ou null), ou null s'il est inconnu. */
    fun maker(wmi: String): Pair<String, String?>? = WMI[wmi.take(3)]

    /** Toutes les marques du catalogue citées par la table (contrôlé par un test). */
    val catalogBrands: Set<String> get() = WMI.values.mapNotNull { it.second }.toSet()

    /** Code de modèle connu : début des caractères 4 et suivants du VIN, chez un constructeur donné. */
    private class ModelCode(val maker: String, val prefix: String, val brand: String, val model: String, val generation: String)

    /** Codes relevés sur de vraies voitures (VIN lus par CarDeck). */
    private val MODEL_CODES = listOf(
        ModelCode("Suzuki", "ZC33S", "Suzuki", "Swift", "Sport ZC33S"),
        ModelCode("Suzuki", "ZC32S", "Suzuki", "Swift", "Sport ZC32S"),
        ModelCode("Renault", "RJA", "Renault", "Clio", "V"),
    )

    /**
     * Constructeurs dont le 10e caractère du VIN est bien l'année modèle. Ce n'est pas le cas chez tous les constructeurs
     * européens (Renault, PSA, Fiat, Mercedes, BMW…), où ce caractère a un autre sens : on n'en déduit alors pas d'année.
     */
    private val YEAR_MAKERS = setOf("Volkswagen", "Audi", "Skoda", "Seat", "Hyundai", "Kia", "Volvo", "Tesla", "Subaru", "Chevrolet", "Chrysler", "Dodge", "Cadillac", "Jeep")

    /** L'année modèle est fiable : VIN nord-américain (1 à 5) ou coréen (K), ou constructeur qui la code partout. */
    fun yearReliable(wmi: String, maker: String?): Boolean = wmi.first() in '1'..'5' || wmi.first() == 'K' || maker in YEAR_MAKERS

    /** Année modèle d'après le 10e caractère (cycle de 30 ans : on prend la plus plausible, jamais dans le futur lointain). */
    fun modelYear(vin: String, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)): Int? {
        val c = vin.getOrNull(9) ?: return null
        val letters = "ABCDEFGHJKLMNPRSTVWXY"
        val i = letters.indexOf(c)
        return when {
            i >= 0 -> (2010 + i).takeIf { it <= currentYear + 1 } ?: (1980 + i)
            c in '1'..'9' -> 2000 + (c - '0')
            else -> null
        }
    }

    fun decode(raw: String, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)): VinInfo? {
        val vin = normalize(raw) ?: return null
        val wmi = vin.take(3)
        val m = maker(wmi)
        val rest = vin.substring(3)
        val hint = m?.first?.let { maker -> MODEL_CODES.firstOrNull { it.maker == maker && rest.startsWith(it.prefix) } }
        val tpl = hint?.let { templateFor(it.brand, it.model, it.generation) }
        val year = if (yearReliable(wmi, m?.first)) modelYear(vin, currentYear) else null
        return VinInfo(vin, m?.first, m?.second, year, wmi, hint?.prefix, tpl, hint?.model, hint?.generation)
    }

    /** Motorisation unique d'une génération du catalogue, si elle n'en a qu'une (sinon null). */
    private fun templateFor(brand: String, model: String, gen: String): String? =
        CATALOG.firstOrNull { it.name == brand }?.models?.firstOrNull { it.name == model }
            ?.generations?.firstOrNull { it.name == gen }?.variants?.singleOrNull()?.id

    /** Années de production d'une génération : « 2012–2017 » → 2012..2017, « 2019– » → 2019..(ouvert), vide → null. */
    fun years(g: CatalogGeneration): Pair<Int, Int?>? {
        val start = Regex("\\d{4}").find(g.years)?.value?.toIntOrNull() ?: return null
        val end = Regex("–\\s*(\\d{4})").find(g.years)?.groupValues?.get(1)?.toIntOrNull()
        return start to end
    }

    /** La génération a été produite l'année [year] (à un an près : l'année modèle n'est pas l'année de fabrication). */
    fun matches(g: CatalogGeneration, year: Int): Boolean {
        val (start, end) = years(g) ?: return false
        return year >= start - 1 && (end == null || year <= end + 1)
    }

    /** Modèles de la marque dont une génération correspond à [year] : (modèle, générations correspondantes). */
    fun candidates(brand: CatalogBrand, year: Int): List<Pair<CatalogModel, List<CatalogGeneration>>> =
        brand.models.mapNotNull { m -> m.generations.filter { matches(it, year) }.takeIf { it.isNotEmpty() }?.let { m to it } }
}
