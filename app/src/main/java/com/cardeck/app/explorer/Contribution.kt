package com.cardeck.app.explorer

import com.cardeck.app.data.VehicleTemplate
import java.net.URLEncoder

/**
 * Envoi des découvertes de l'explorateur au développeur, pour intégrer complètement un véhicule : un ticket GitHub
 * prérempli (résumé lisible) et un fichier zip anonymisé que l'utilisateur joint au ticket.
 * Le ticket est public : le numéro de série du VIN (7 derniers caractères) et le surnom du véhicule sont retirés.
 */
object Contribution {
    const val REPO = "ToftMalone/CarDeck"

    /** Longueur maximale de l'adresse du ticket (au-delà, GitHub ou le navigateur la refusent). */
    const val MAX_URL = 7000

    /** Début du VIN conservé : constructeur, modèle, chiffre de contrôle et année (10 caractères). */
    private const val KEPT = 10

    private const val VIN_CHARS = "[A-HJ-NPR-Z0-9]"
    private val VIN_TEXT = Regex("(?<![A-Z0-9])$VIN_CHARS{17}(?![A-Z0-9])")

    // Suites d'octets en hexadécimal (réponses des calculateurs) : collés (« 4902013156… ») ou séparés par des espaces.
    private val HEX_RUN = Regex("(?<![0-9A-Za-z])(?:[0-9A-Fa-f]{2}){17,}(?![0-9A-Za-z])")
    private val HEX_SPACED = Regex("(?<![0-9A-Za-z])(?:[0-9A-Fa-f]{2} ){16,}[0-9A-Fa-f]{2}(?![0-9A-Za-z])")

    private fun vinChar(b: Int) = b in 0x30..0x39 || (b in 0x41..0x5A && b != 0x49 && b != 0x4F && b != 0x51)

    /** Masque le numéro de série de chaque VIN trouvé dans une suite d'octets (17 caractères de VIN à la suite). */
    private fun scrubBytes(b: IntArray): Boolean {
        var changed = false
        var i = 0
        while (i + 17 <= b.size) {
            val s = (i until i + 17).map { b[it].toChar() }.joinToString("")
            if ((i until i + 17).all { vinChar(b[it]) } && isVin(s)) {
                for (j in i + KEPT until i + 17) b[j] = 0x30
                changed = true
                i += 17
            } else i++
        }
        return changed
    }

    /** VIN masqué pour l'affichage : « VF1RJA005K******* ». */
    fun maskVin(vin: String): String = vin.take(KEPT) + "*".repeat((vin.length - KEPT).coerceAtLeast(0))

    /** VIN dont le numéro de série est remplacé par des zéros (même format, rien d'identifiant). */
    private fun anonymous(vin: String) = vin.take(KEPT) + "0".repeat(vin.length - KEPT)

    private fun isVin(s: String) = s.length == 17 && s.none { it in "IOQ" } && s.any { it.isLetter() } && s.any { it.isDigit() }

    /**
     * Retire les numéros de série d'un texte : VIN en clair, VIN écrit en octets hexadécimaux (réponses des
     * calculateurs) et [knownVins] ; remplace aussi [nickname] par [replacement].
     */
    fun scrub(text: String, knownVins: Set<String> = emptySet(), nickname: String? = null, replacement: String = "Véhicule"): String {
        var out = text
        for (v in knownVins) if (v.length == 17) out = out.replace(v, anonymous(v), ignoreCase = true)
        out = VIN_TEXT.replace(out) { m -> if (isVin(m.value)) anonymous(m.value) else m.value }
        for ((re, sep) in listOf(HEX_RUN to "", HEX_SPACED to " ")) out = re.replace(out) { m ->
            val b = m.value.replace(" ", "").chunked(2).map { it.toInt(16) }.toIntArray()
            if (!scrubBytes(b)) m.value
            else b.joinToString(sep) { "%02X".format(it) }.let { if (m.value.any { it.isLowerCase() }) it.lowercase() else it }
        }
        if (!nickname.isNullOrBlank() && nickname.length >= 3) out = out.replace(nickname, replacement)
        return out
    }

    /** Demandes qui renvoient le VIN : OBD 09 02, UDS F190, KWP 1A 90. */
    private val VIN_REQUESTS = setOf("0902", "22F190", "1A90")

    /** VIN lus par l'explorateur (réponses aux demandes de VIN). */
    fun vinsIn(d: ExplorerData): Set<String> = d.answers.filterKeys { it.substringAfter(':').uppercase() in VIN_REQUESTS }.values.flatMap { r ->
        val b = Explore.bytes(r.hex)
        (0..b.size - 17).mapNotNull { i ->
            if ((i until i + 17).all { vinChar(b[it]) }) (i until i + 17).map { b[it].toChar() }.joinToString("").takeIf(::isVin) else null
        }
    }.toSet()

    /** Tout ce qui décrit la voiture et la liaison, pour la fiche jointe au relevé. */
    class Sheet(
        val template: VehicleTemplate,
        val year: String,
        val vin: String?,
        val version: String,
        val android: String,
        val adapter: String?,
        val protocol: String?,
        /** PID du service 01 que la voiture déclare fournir (null = jamais lus). */
        val supported: Set<Int>?,
        /** Dernières valeurs lues (identifiant → valeur). */
        val live: Map<String, Double>,
        val generated: String,
    )

    private fun json(s: String?) = if (s == null) "null" else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    /** Fiche lisible du véhicule (VEHICULE.md), en tête du relevé. */
    fun sheetMarkdown(s: Sheet, f: Findings): String {
        val t = s.template
        val sb = StringBuilder()
        fun a(x: String = "") = sb.append(x).append('\n')
        a("# Fiche du véhicule — ${t.fullName}")
        a()
        a("Relevé préparé le ${s.generated} par CarDeck ${s.version} pour intégrer ce véhicule. Numéro de série du VIN et surnom retirés.")
        a()
        a("## Véhicule")
        a()
        a("| | |")
        a("|---|---|")
        a("| Marque | ${t.brand.ifBlank { "—" }} |")
        a("| Modèle | ${t.model} |")
        a("| Génération / code | ${t.code.ifBlank { "—" }} |")
        a("| Moteur | ${t.engine.ifBlank { "—" }} |")
        a("| Puissance | ${t.power.ifBlank { "—" }} |")
        a("| Énergie | ${t.fuel.ifBlank { "—" }} |")
        a("| Année | ${s.year.ifBlank { "—" }} |")
        a("| Début du VIN | ${s.vin?.let { "`${maskVin(it)}`" } ?: "non lu"} |")
        a("| Identifiant CarDeck | `${t.id}` |")
        a("| Rétro-ingénié | ${if (t.dedicated) "oui" else "non (données OBD2 standard)"} |")
        a()
        a("## Liaison")
        a()
        a("| | |")
        a("|---|---|")
        a("| Boîtier | ${s.adapter ?: "—"} |")
        a("| Protocole détecté | ${s.protocol ?: "—"} |")
        a("| Protocole connu du modèle | ${t.protocol} |")
        a("| Android | ${s.android} |")
        a()
        a("## Découvertes")
        a()
        a("- Calculateurs : ${f.ecus.size} · réponses positives : ${f.answers} · blocs constructeur : ${f.blocks} · identifiants sur le bus : ${f.busIds}")
        a("- Enregistrements en roulant : ${f.drives} · recherches du cliquetis : ${f.knocks}")
        a()
        a("## Données OBD2 standard fournies (service 01)")
        a()
        val sup = s.supported
        if (sup == null) a("Pas encore lues (connectez le boîtier, contact mis, avant d'envoyer).")
        else a(sup.sorted().joinToString(" ") { "%02X".format(it) }.ifEmpty { "aucune" })
        if (s.live.isNotEmpty()) {
            a()
            a("## Dernières valeurs lues")
            a()
            a("| Donnée | Valeur |")
            a("|---|---|")
            for ((k, v) in s.live.toSortedMap()) a("| `$k` | ${"%.2f".format(java.util.Locale.US, v)} |")
        }
        a()
        a("## Contenu du fichier")
        a()
        a("- `VEHICULE.md` / `vehicule.json` : cette fiche")
        a("- `RAPPORT.md` : rapport de l'explorateur (calculateurs, blocs, pistes de décodage)")
        a("- `decouvertes.json` : toutes les réponses des calculateurs")
        a("- `diagnostic.txt` : dernier diagnostic (codes défaut), s'il y en a un")
        a("- autres dossiers : enregistrements en roulant et recherches du cliquetis, s'il y en a")
        return sb.toString()
    }

    /** Même fiche, lisible par un programme (vehicule.json). */
    fun sheetJson(s: Sheet, f: Findings): String {
        val t = s.template
        val ecus = f.ecus.joinToString(",") { e ->
            "{\"adresse\":${json(e.label)},\"liaison\":${json(e.bus.name)},\"protocole\":${json(e.proto)},\"nom\":${json(f.names[e.key])}}"
        }
        val live = s.live.toSortedMap().entries.joinToString(",") { "${json(it.key)}:${if (it.value.isFinite()) it.value else "null"}" }
        return "{" + listOf(
            "\"format\":1",
            "\"genere\":${json(s.generated)}",
            "\"cardeck\":${json(s.version)}",
            "\"android\":${json(s.android)}",
            "\"vehicule\":{\"id\":${json(t.id)},\"marque\":${json(t.brand)},\"modele\":${json(t.model)},\"code\":${json(t.code)}," +
                "\"moteur\":${json(t.engine)},\"puissance\":${json(t.power)},\"energie\":${json(t.fuel)},\"annee\":${json(s.year)}," +
                "\"vin_debut\":${json(s.vin?.let(::maskVin))},\"profil_dedie\":${t.dedicated}}",
            "\"liaison\":{\"boitier\":${json(s.adapter)},\"protocole_detecte\":${json(s.protocol)},\"protocole_modele\":${json(t.protocol)}}",
            "\"pid01_fournis\":" + (s.supported?.sorted()?.joinToString(",", "[", "]") { json("%02X".format(it)) } ?: "null"),
            "\"valeurs\":{$live}",
            "\"calculateurs\":[$ecus]",
            "\"compteurs\":{\"reponses\":${f.answers},\"blocs\":${f.blocks},\"bus\":${f.busIds},\"roulage\":${f.drives},\"cliquetis\":${f.knocks}}",
        ).joinToString(",") + "}\n"
    }

    /** Titre du ticket. */
    fun title(t: VehicleTemplate, year: String): String =
        "Véhicule à intégrer : ${t.fullName}" + listOf(t.engine, t.power).filter { it.isNotBlank() }.joinToString(" ", " ") + (if (year.isNotBlank()) " ($year)" else "")

    /** Ce que l'explorateur a trouvé, pour le corps du ticket. */
    class Findings(
        val ecus: List<Ecu>,
        val names: Map<String, String>,
        val answers: Int,
        val blocks: Int,
        val busIds: Int,
        val drives: Int,
        val knocks: Int,
        /** Pistes de décodage trouvées en roulant (tous trajets réunis). */
        val clues: Int = 0,
    )

    fun findings(d: ExplorerData) = Findings(d.ecus.values.toList(), d.names(), d.answers.size, d.dataBlocks().size, d.bus.size, d.drives.size, d.knocks.size, d.mergedDriveClues().size)

    /** Corps du ticket (Markdown), raccourci si besoin pour que l'adresse reste sous [MAX_URL]. */
    fun body(t: VehicleTemplate, year: String, vin: String?, version: String, f: Findings, fileName: String, maxEcus: Int = f.ecus.size): String {
        val sb = StringBuilder()
        fun a(s: String = "") = sb.append(s).append('\n')
        a("### 🚗 Véhicule")
        a("- **Modèle choisi dans CarDeck** : ${t.fullName}")
        listOf(t.engine, t.power, t.fuel).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { a("- **Motorisation** : ${it.joinToString(" · ")}") }
        if (year.isNotBlank()) a("- **Année** : $year")
        if (vin != null) a("- **Début du VIN** : `${maskVin(vin)}` (numéro de série masqué)")
        a("- **CarDeck** : $version")
        a()
        a("### 🔎 Découvertes de l'explorateur")
        a("- Calculateurs : **${f.ecus.size}**" + listOfNotNull(
            f.ecus.count { it.bus == Bus.Can11 }.takeIf { it > 0 }?.let { "$it en CAN 11 bits" },
            f.ecus.count { it.bus == Bus.Can29 }.takeIf { it > 0 }?.let { "$it en CAN 29 bits" },
            f.ecus.count { it.bus == Bus.K }.takeIf { it > 0 }?.let { "$it sur la ligne K" },
        ).joinToString(", ").let { if (it.isEmpty()) "" else " ($it)" })
        a("- Réponses positives : **${f.answers}** · blocs constructeur : **${f.blocks}** · identifiants sur le bus : **${f.busIds}**")
        if (f.drives > 0 || f.knocks > 0) a("- Enregistrements en roulant : **${f.drives}** · recherches du cliquetis : **${f.knocks}**")
        if (f.clues > 0) a("- Pistes de décodage en roulant : **${f.clues}** (détail dans RAPPORT.md)")
        if (f.ecus.isNotEmpty() && maxEcus > 0) {
            a()
            a("| Adresse | Liaison | Protocole | Identifié |")
            a("|---|---|---|---|")
            for (e in f.ecus.take(maxEcus)) {
                val link = when (e.bus) { Bus.Can11 -> "CAN 11"; Bus.Can29 -> "CAN 29"; Bus.K -> "K" }
                a("| `${e.label}` | $link | ${e.proto.uppercase()} | ${f.names[e.key]?.replace("|", "/")?.take(40) ?: "—"} |")
            }
            if (maxEcus < f.ecus.size) a("| … | ${f.ecus.size - maxEcus} autres dans le fichier | | |")
        }
        a()
        a("### 📎 Fichier du relevé")
        a("CarDeck a enregistré **`$fileName`** dans le dossier **Téléchargements**.")
        a("👉 Joignez-le à ce ticket (glissez-le ici ou touchez la zone de texte puis « Paste, drop, or click to add files »), puis envoyez.")
        a()
        a("### 💬 Remarques (facultatif)")
        a("Options présentes sur la voiture, ce que vous aimeriez voir dans CarDeck…")
        return sb.toString()
    }

    /** Adresse de création du ticket, préremplie. */
    fun url(title: String, body: String): String =
        "https://github.com/$REPO/issues/new?title=${enc(title)}&body=${enc(body)}"

    /** Adresse du ticket, en réduisant la liste des calculateurs tant qu'elle est trop longue. */
    fun issueUrl(t: VehicleTemplate, year: String, vin: String?, version: String, f: Findings, fileName: String): String {
        val title = title(t, year)
        var n = f.ecus.size
        while (true) {
            val u = url(title, body(t, year, vin, version, f, fileName, n))
            if (u.length <= MAX_URL || n == 0) return u
            n = if (n > 10) n - 5 else n - 1
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
