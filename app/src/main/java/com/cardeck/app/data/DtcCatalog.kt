package com.cardeck.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

enum class Severity(val label: String) { Crit("Critique"), Mid("Moyen"), Low("Mineur") }

enum class DtcSystem(val label: String, val icon: ImageVector) {
    Ignition("Moteur · Allumage", Icons.Rounded.LocalFireDepartment),
    Fuel("Moteur · Alimentation", Icons.Rounded.LocalGasStation),
    Intake("Moteur · Admission", Icons.Rounded.Air),
    Boost("Moteur · Suralimentation", Icons.Rounded.Speed),
    Cooling("Moteur · Refroidissement", Icons.Rounded.DeviceThermostat),
    Timing("Moteur · Distribution variable", Icons.Rounded.Sync),
    Sensors("Moteur · Capteurs", Icons.Rounded.Sensors),
    Exhaust("Échappement · Antipollution", Icons.Rounded.FilterAlt),
    Evap("Réservoir · Vapeurs", Icons.Rounded.WaterDrop),
    Electrical("Électrique", Icons.Rounded.BatteryAlert),
    Ecu("Calculateur moteur", Icons.Rounded.Memory),
    Transmission("Transmission", Icons.Rounded.Settings),
    Network("Réseau CAN", Icons.Rounded.Lan),
    Body("Carrosserie / Châssis", Icons.Rounded.DirectionsCar),
}

data class DtcInfo(
    val code: String,
    val title: String,
    val sys: DtcSystem,
    val sev: Severity,
    val known: Boolean = true,
    val desc: String? = null,
    val causes: List<String> = emptyList(),
    val recos: List<String> = emptyList(),
)

/** Base française des codes défaut OBD2 génériques (SAE J2012) les plus courants. */
object DtcCatalog {
    private val map = LinkedHashMap<String, DtcInfo>()

    private fun d(code: String, title: String, sys: DtcSystem, sev: Severity = Severity.Mid, desc: String? = null, causes: List<String> = emptyList(), recos: List<String> = emptyList()) {
        map[code] = DtcInfo(code, title, sys, sev, true, desc, causes, recos)
    }

    private val misfireCauses = listOf("Bougie d'allumage usée ou encrassée", "Bobine d'allumage défectueuse", "Injecteur obstrué ou défaillant", "Prise d'air ou compression insuffisante")
    private val misfireRecos = listOf("Évitez les fortes accélérations et les longs trajets", "Faites contrôler bougies et bobines rapidement", "Un raté prolongé peut endommager le catalyseur")
    private val evapCauses = listOf("Bouchon de réservoir mal serré ou joint abîmé", "Durite EVAP poreuse ou débranchée", "Électrovanne de purge canister défaillante")
    private val evapRecos = listOf("Resserrez le bouchon de réservoir jusqu'au clic", "Effacez le code et surveillez sa réapparition")

    init {
        // Distribution variable
        d("P0010", "Circuit de l'actionneur d'arbre à cames d'admission (banc 1)", DtcSystem.Timing)
        d("P0011", "Calage de l'arbre à cames d'admission trop avancé / performances (banc 1)", DtcSystem.Timing, causes = listOf("Niveau ou qualité d'huile moteur", "Électrovanne de distribution variable (OCV) encrassée", "Chaîne de distribution détendue"), recos = listOf("Contrôlez le niveau d'huile et la date de vidange", "Faites vérifier l'électrovanne OCV"))
        d("P0012", "Calage de l'arbre à cames d'admission trop retardé (banc 1)", DtcSystem.Timing)
        d("P0013", "Circuit de l'actionneur d'arbre à cames d'échappement (banc 1)", DtcSystem.Timing)
        d("P0014", "Calage de l'arbre à cames d'échappement trop avancé (banc 1)", DtcSystem.Timing)
        d("P0015", "Calage de l'arbre à cames d'échappement trop retardé (banc 1)", DtcSystem.Timing)
        d("P0016", "Corrélation vilebrequin / arbre à cames d'admission (banc 1)", DtcSystem.Timing, Severity.Crit,
            "Les signaux des capteurs de vilebrequin et d'arbre à cames ne sont plus synchronisés comme prévu.",
            listOf("Chaîne de distribution allongée ou ayant sauté une dent", "Capteur d'arbre à cames ou de vilebrequin défaillant", "Huile moteur inadaptée ou trop basse"),
            listOf("Faites contrôler la distribution sans tarder", "Évitez les hauts régimes"))
        d("P0017", "Corrélation vilebrequin / arbre à cames d'échappement (banc 1)", DtcSystem.Timing, Severity.Crit)
        // Sondes lambda (chauffage)
        d("P0030", "Circuit de chauffage de la sonde lambda amont (B1S1)", DtcSystem.Exhaust)
        d("P0031", "Chauffage de la sonde lambda amont, signal bas (B1S1)", DtcSystem.Exhaust)
        d("P0032", "Chauffage de la sonde lambda amont, signal haut (B1S1)", DtcSystem.Exhaust)
        d("P0036", "Circuit de chauffage de la sonde lambda aval (B1S2)", DtcSystem.Exhaust, Severity.Low)
        d("P0037", "Chauffage de la sonde lambda aval, signal bas (B1S2)", DtcSystem.Exhaust, Severity.Low)
        d("P0038", "Chauffage de la sonde lambda aval, signal haut (B1S2)", DtcSystem.Exhaust, Severity.Low)
        d("P0068", "Corrélation pression collecteur / débit d'air / position papillon", DtcSystem.Intake)
        // Pression carburant (injection directe)
        d("P0087", "Pression de rampe d'injection trop basse", DtcSystem.Fuel, Severity.Mid,
            "La pression mesurée dans la rampe d'injection est inférieure à la consigne du calculateur.",
            listOf("Pompe à carburant haute pression usée", "Filtre à carburant colmaté", "Capteur de pression de rampe défaillant", "Pompe de gavage (réservoir) faible"),
            listOf("Évitez les fortes charges moteur", "Faites contrôler la pression d'alimentation"))
        d("P0088", "Pression de rampe d'injection trop élevée", DtcSystem.Fuel)
        d("P0089", "Performances du régulateur de pression de carburant", DtcSystem.Fuel)
        d("P0090", "Circuit du régulateur de pression de carburant", DtcSystem.Fuel)
        d("P0091", "Circuit du régulateur de pression de carburant, signal bas", DtcSystem.Fuel)
        d("P0092", "Circuit du régulateur de pression de carburant, signal haut", DtcSystem.Fuel)
        // Admission / capteurs
        d("P0100", "Circuit du débitmètre d'air", DtcSystem.Intake)
        d("P0101", "Débitmètre d'air : plage / performances", DtcSystem.Intake, Severity.Mid,
            "La valeur de débit d'air mesurée ne correspond pas à celle attendue pour le régime et la charge.",
            listOf("Débitmètre encrassé", "Prise d'air après le débitmètre", "Filtre à air colmaté"),
            listOf("Contrôlez le filtre et les durites d'admission", "Nettoyez le débitmètre avec un produit adapté"))
        d("P0102", "Circuit du débitmètre d'air, signal bas", DtcSystem.Intake)
        d("P0103", "Circuit du débitmètre d'air, signal haut", DtcSystem.Intake)
        d("P0105", "Circuit du capteur de pression collecteur (MAP)", DtcSystem.Intake)
        d("P0106", "Capteur de pression collecteur (MAP) : plage / performances", DtcSystem.Intake)
        d("P0107", "Circuit du capteur de pression collecteur, signal bas", DtcSystem.Intake)
        d("P0108", "Circuit du capteur de pression collecteur, signal haut", DtcSystem.Intake)
        d("P0110", "Circuit de la sonde de température d'air d'admission", DtcSystem.Intake, Severity.Low)
        d("P0111", "Sonde de température d'air d'admission : plage / performances", DtcSystem.Intake, Severity.Low)
        d("P0112", "Sonde de température d'air d'admission, signal bas", DtcSystem.Intake, Severity.Low)
        d("P0113", "Sonde de température d'air d'admission, signal haut", DtcSystem.Intake, Severity.Low)
        d("P0115", "Circuit de la sonde de température du liquide de refroidissement", DtcSystem.Cooling)
        d("P0116", "Sonde de température du liquide de refroidissement : plage / performances", DtcSystem.Cooling)
        d("P0117", "Sonde de température du liquide de refroidissement, signal bas", DtcSystem.Cooling)
        d("P0118", "Sonde de température du liquide de refroidissement, signal haut", DtcSystem.Cooling)
        d("P0119", "Sonde de température du liquide de refroidissement, signal intermittent", DtcSystem.Cooling)
        d("P0120", "Circuit du capteur de position papillon A", DtcSystem.Intake)
        d("P0121", "Capteur de position papillon A : plage / performances", DtcSystem.Intake)
        d("P0122", "Capteur de position papillon A, signal bas", DtcSystem.Intake)
        d("P0123", "Capteur de position papillon A, signal haut", DtcSystem.Intake)
        d("P0125", "Température de liquide insuffisante pour la régulation en boucle fermée", DtcSystem.Cooling, Severity.Low)
        d("P0128", "Température de liquide sous le seuil du thermostat", DtcSystem.Cooling, Severity.Mid,
            "Le moteur met trop de temps à atteindre sa température de fonctionnement.",
            listOf("Thermostat bloqué ouvert", "Sonde de température de liquide défaillante", "Niveau de liquide de refroidissement bas"),
            listOf("Vérifiez le niveau de liquide de refroidissement", "Faites remplacer le thermostat si le défaut persiste"))
        // Sondes lambda
        d("P0130", "Circuit de la sonde lambda amont (B1S1)", DtcSystem.Exhaust)
        d("P0131", "Sonde lambda amont, tension basse (B1S1)", DtcSystem.Exhaust)
        d("P0132", "Sonde lambda amont, tension haute (B1S1)", DtcSystem.Exhaust)
        d("P0133", "Sonde lambda amont, réponse lente (B1S1)", DtcSystem.Exhaust)
        d("P0134", "Sonde lambda amont, aucune activité (B1S1)", DtcSystem.Exhaust)
        d("P0136", "Circuit de la sonde lambda aval (B1S2)", DtcSystem.Exhaust, Severity.Low)
        d("P0137", "Sonde lambda aval, tension basse (B1S2)", DtcSystem.Exhaust, Severity.Low)
        d("P0138", "Sonde lambda aval, tension haute (B1S2)", DtcSystem.Exhaust, Severity.Low)
        d("P0139", "Sonde lambda aval, réponse lente (B1S2)", DtcSystem.Exhaust, Severity.Low)
        d("P0140", "Sonde lambda aval, aucune activité (B1S2)", DtcSystem.Exhaust, Severity.Low)
        d("P0171", "Mélange trop pauvre (banc 1)", DtcSystem.Fuel, Severity.Mid,
            "Le calculateur doit enrichir le mélange au-delà de sa plage de correction : trop d'air ou pas assez de carburant arrive dans les cylindres.",
            listOf("Prise d'air après le débitmètre ou au collecteur", "Débitmètre d'air encrassé", "Pression de carburant insuffisante", "Injecteur partiellement obstrué"),
            listOf("Inspectez les durites d'admission et de dépression", "Nettoyez le débitmètre d'air"))
        d("P0172", "Mélange trop riche (banc 1)", DtcSystem.Fuel, Severity.Mid,
            causes = listOf("Injecteur qui fuit", "Pression de carburant trop élevée", "Capteur de débit d'air ou sonde lambda faussés"),
            recos = listOf("Surveillez la consommation", "Faites contrôler injecteurs et sondes"))
        // Injecteurs (4 cylindres)
        for (i in 1..4) d("P020$i", "Circuit de l'injecteur du cylindre $i", DtcSystem.Fuel)
        mapOf(1 to (61 to 62), 2 to (64 to 65), 3 to (67 to 68), 4 to (70 to 71)).forEach { (cyl, p) ->
            d("P02${p.first}", "Circuit de l'injecteur du cylindre $cyl, signal bas", DtcSystem.Fuel)
            d("P02${p.second}", "Circuit de l'injecteur du cylindre $cyl, signal haut", DtcSystem.Fuel)
        }
        d("P0217", "Surchauffe moteur", DtcSystem.Cooling, Severity.Crit,
            "La température du liquide de refroidissement a dépassé le seuil de sécurité.",
            listOf("Niveau de liquide de refroidissement trop bas", "Motoventilateur ou son relais défaillant", "Pompe à eau ou thermostat défaillant"),
            listOf("Arrêtez-vous dès que possible et coupez le moteur", "N'ouvrez pas le vase d'expansion à chaud"))
        d("P0219", "Survitesse moteur (régime excessif)", DtcSystem.Sensors, Severity.Crit)
        // Turbo (K14C BoosterJet)
        d("P0234", "Suralimentation excessive (overboost)", DtcSystem.Boost, Severity.Crit,
            "La pression de suralimentation a dépassé la limite autorisée par le calculateur.",
            listOf("Wastegate bloquée fermée", "Électrovanne de commande de wastegate défaillante", "Capteur de pression de suralimentation faussé"),
            listOf("Roulez sans solliciter le turbo", "Faites contrôler la commande de wastegate"))
        d("P0235", "Circuit du capteur de pression de suralimentation A", DtcSystem.Boost)
        d("P0236", "Capteur de pression de suralimentation A : plage / performances", DtcSystem.Boost)
        d("P0237", "Capteur de pression de suralimentation A, signal bas", DtcSystem.Boost)
        d("P0238", "Capteur de pression de suralimentation A, signal haut", DtcSystem.Boost)
        d("P0243", "Circuit de l'électrovanne de wastegate A", DtcSystem.Boost)
        d("P0245", "Circuit de l'électrovanne de wastegate A, signal bas", DtcSystem.Boost)
        d("P0246", "Circuit de l'électrovanne de wastegate A, signal haut", DtcSystem.Boost)
        d("P0299", "Pression de suralimentation insuffisante (underboost)", DtcSystem.Boost, Severity.Mid,
            "La pression de suralimentation reste inférieure à la consigne : le moteur manque de puissance.",
            listOf("Fuite sur les durites ou l'échangeur (intercooler)", "Wastegate bloquée ouverte", "Turbo usé"),
            listOf("Vérifiez l'état et le serrage des durites de suralimentation", "Faites contrôler le turbo et sa commande"))
        // Ratés d'allumage
        d("P0300", "Ratés d'allumage aléatoires / multiples cylindres", DtcSystem.Ignition, Severity.Crit,
            "Le calculateur moteur a détecté des combustions incomplètes sur plusieurs cylindres.", misfireCauses, misfireRecos)
        for (i in 1..4) d("P030$i", "Raté d'allumage détecté, cylindre $i", DtcSystem.Ignition, Severity.Crit,
            "Le calculateur moteur a mesuré des combustions incomplètes répétées dans le cylindre $i. Le moteur peut trembler au ralenti et perdre de la puissance.",
            misfireCauses, misfireRecos)
        d("P0325", "Circuit du capteur de cliquetis 1", DtcSystem.Sensors)
        d("P0326", "Capteur de cliquetis 1 : plage / performances", DtcSystem.Sensors)
        d("P0327", "Capteur de cliquetis 1, signal bas", DtcSystem.Sensors)
        d("P0328", "Capteur de cliquetis 1, signal haut", DtcSystem.Sensors)
        d("P0335", "Circuit du capteur de position du vilebrequin", DtcSystem.Sensors, Severity.Crit,
            "Le signal du capteur de vilebrequin est absent ou incohérent ; le moteur peut caler ou ne pas démarrer.",
            listOf("Capteur de vilebrequin défaillant", "Connecteur ou faisceau endommagé", "Cible du capteur abîmée"),
            listOf("Faites contrôler le capteur rapidement", "Risque d'arrêt moteur en roulant"))
        d("P0336", "Capteur de position du vilebrequin : plage / performances", DtcSystem.Sensors, Severity.Crit)
        d("P0340", "Circuit du capteur de position d'arbre à cames (banc 1)", DtcSystem.Sensors)
        d("P0341", "Capteur de position d'arbre à cames : plage / performances (banc 1)", DtcSystem.Sensors)
        d("P0365", "Circuit du capteur de position d'arbre à cames d'échappement (banc 1)", DtcSystem.Sensors)
        for (i in 1..4) d("P035$i", "Circuit primaire de la bobine d'allumage $i", DtcSystem.Ignition, Severity.Crit)
        d("P0400", "Débit de recirculation des gaz d'échappement (EGR)", DtcSystem.Exhaust)
        d("P0401", "Débit de recirculation des gaz d'échappement (EGR) insuffisant", DtcSystem.Exhaust)
        d("P0420", "Efficacité du catalyseur sous le seuil (banc 1)", DtcSystem.Exhaust, Severity.Mid,
            "La sonde lambda aval détecte une variation d'oxygène trop proche de la sonde amont : le catalyseur ne traite plus correctement les gaz d'échappement.",
            listOf("Catalyseur usé ou colmaté", "Sonde lambda aval défaillante", "Fuite à l'échappement"),
            listOf("Le véhicule peut échouer au contrôle technique", "Contrôlez les sondes avant de remplacer le catalyseur"))
        d("P0421", "Efficacité du catalyseur de démarrage sous le seuil (banc 1)", DtcSystem.Exhaust)
        // EVAP
        d("P0440", "Système de récupération des vapeurs de carburant (EVAP)", DtcSystem.Evap, Severity.Low, causes = evapCauses, recos = evapRecos)
        d("P0441", "Débit de purge incorrect, système EVAP", DtcSystem.Evap, Severity.Low, causes = evapCauses, recos = evapRecos)
        d("P0442", "Petite fuite détectée, système EVAP", DtcSystem.Evap, Severity.Low,
            "Le test d'étanchéité du circuit de récupération des vapeurs d'essence a mesuré une fuite de faible diamètre.", evapCauses, evapRecos)
        d("P0443", "Circuit de l'électrovanne de purge EVAP", DtcSystem.Evap, Severity.Low)
        d("P0446", "Circuit de commande de mise à l'air EVAP", DtcSystem.Evap, Severity.Low)
        d("P0455", "Fuite importante détectée, système EVAP", DtcSystem.Evap, Severity.Low, causes = evapCauses, recos = evapRecos)
        d("P0456", "Très petite fuite détectée, système EVAP", DtcSystem.Evap, Severity.Low, causes = evapCauses, recos = evapRecos)
        d("P0460", "Circuit du capteur de niveau de carburant", DtcSystem.Evap, Severity.Low)
        d("P0461", "Capteur de niveau de carburant : plage / performances", DtcSystem.Evap, Severity.Low)
        d("P0462", "Capteur de niveau de carburant, signal bas", DtcSystem.Evap, Severity.Low)
        d("P0463", "Capteur de niveau de carburant, signal haut", DtcSystem.Evap, Severity.Low)
        d("P0480", "Circuit de commande du motoventilateur 1", DtcSystem.Cooling)
        // Vitesse / ralenti
        d("P0500", "Capteur de vitesse du véhicule", DtcSystem.Transmission)
        d("P0501", "Capteur de vitesse du véhicule : plage / performances", DtcSystem.Transmission)
        d("P0505", "Système de régulation du ralenti", DtcSystem.Intake)
        d("P0506", "Régime de ralenti inférieur à la consigne", DtcSystem.Intake, Severity.Low)
        d("P0507", "Régime de ralenti supérieur à la consigne", DtcSystem.Intake, Severity.Low)
        d("P0520", "Circuit du capteur / contacteur de pression d'huile", DtcSystem.Sensors)
        d("P0524", "Pression d'huile moteur trop basse", DtcSystem.Sensors, Severity.Crit,
            "La pression d'huile mesurée est insuffisante pour lubrifier correctement le moteur.",
            listOf("Niveau d'huile trop bas", "Pompe à huile ou crépine défaillante", "Contacteur de pression défaillant"),
            listOf("Arrêtez le moteur immédiatement et vérifiez le niveau d'huile", "Ne roulez pas tant que la cause n'est pas identifiée"))
        // Électrique / calculateur
        d("P0560", "Tension du système électrique", DtcSystem.Electrical)
        d("P0562", "Tension du système électrique trop basse", DtcSystem.Electrical, Severity.Mid,
            "Le calculateur a mesuré une tension d'alimentation inférieure au seuil normal.",
            listOf("Batterie faible ou en fin de vie", "Alternateur défaillant", "Cosses oxydées ou desserrées"),
            listOf("Faites tester batterie et charge", "Contrôlez le serrage des cosses"))
        d("P0563", "Tension du système électrique trop élevée", DtcSystem.Electrical)
        d("P0571", "Circuit du contacteur de frein A", DtcSystem.Electrical, Severity.Low)
        d("P0600", "Liaison de communication série du calculateur", DtcSystem.Ecu)
        d("P0601", "Erreur de mémoire interne du calculateur (somme de contrôle)", DtcSystem.Ecu, Severity.Crit)
        d("P0602", "Erreur de programmation du calculateur", DtcSystem.Ecu, Severity.Crit)
        d("P0603", "Erreur de mémoire KAM du calculateur", DtcSystem.Ecu)
        d("P0604", "Erreur de mémoire vive (RAM) du calculateur", DtcSystem.Ecu, Severity.Crit)
        d("P0605", "Erreur de mémoire morte (ROM) du calculateur", DtcSystem.Ecu, Severity.Crit)
        d("P0606", "Défaut du processeur du calculateur moteur", DtcSystem.Ecu, Severity.Crit)
        d("P0620", "Circuit de commande de l'alternateur", DtcSystem.Electrical)
        d("P0627", "Circuit de commande de la pompe à carburant", DtcSystem.Fuel)
        d("P0628", "Circuit de commande de la pompe à carburant, signal bas", DtcSystem.Fuel)
        d("P0641", "Tension de référence capteurs A", DtcSystem.Sensors)
        d("P0645", "Circuit du relais d'embrayage du compresseur de climatisation", DtcSystem.Electrical, Severity.Low)
        // Transmission
        d("P0700", "Défaut du système de commande de la transmission", DtcSystem.Transmission)
        d("P0705", "Circuit du capteur de position du levier de vitesses", DtcSystem.Transmission)
        d("P0715", "Circuit du capteur de vitesse de turbine", DtcSystem.Transmission)
        d("P0720", "Circuit du capteur de vitesse de sortie de boîte", DtcSystem.Transmission)
        d("P0850", "Circuit du contacteur de point mort / embrayage", DtcSystem.Transmission, Severity.Low)
        // Papillon motorisé / richesse
        d("P2101", "Circuit de commande du moteur de papillon : plage / performances", DtcSystem.Intake, Severity.Crit)
        d("P2135", "Corrélation des capteurs de position papillon A / B", DtcSystem.Intake, Severity.Crit)
        d("P2138", "Corrélation des capteurs de position de pédale d'accélérateur D / E", DtcSystem.Intake, Severity.Crit)
        d("P2177", "Mélange trop pauvre hors ralenti (banc 1)", DtcSystem.Fuel)
        d("P2187", "Mélange trop pauvre au ralenti (banc 1)", DtcSystem.Fuel)
        d("P2195", "Sonde lambda amont bloquée en pauvre (B1S1)", DtcSystem.Exhaust)
        d("P2196", "Sonde lambda amont bloquée en riche (B1S1)", DtcSystem.Exhaust)
        d("P2261", "Vanne de décharge du turbo (bypass) : défaut mécanique", DtcSystem.Boost)
        d("P2263", "Performances du système de suralimentation", DtcSystem.Boost)
        d("P2610", "Minuterie interne d'arrêt moteur du calculateur", DtcSystem.Ecu, Severity.Low)
        // Réseau
        d("U0001", "Bus de communication CAN haute vitesse", DtcSystem.Network, Severity.Crit)
        d("U0073", "Bus de communication A désactivé", DtcSystem.Network, Severity.Crit)
        d("U0100", "Perte de communication avec le calculateur moteur", DtcSystem.Network, Severity.Crit,
            "Un ou plusieurs calculateurs n'ont plus reçu de messages du calculateur moteur. Des voyants peuvent s'allumer simultanément.",
            listOf("Connecteur du calculateur oxydé ou desserré", "Faisceau CAN endommagé", "Tension batterie trop faible au démarrage"),
            listOf("Vérifiez la batterie et les cosses", "Ne roulez pas si le moteur se coupe", "Diagnostic du réseau en atelier conseillé"))
        d("U0101", "Perte de communication avec le calculateur de boîte de vitesses", DtcSystem.Network)
        d("U0121", "Perte de communication avec le calculateur ABS", DtcSystem.Network)
        d("U0140", "Perte de communication avec le boîtier de servitude (BCM)", DtcSystem.Network)
        d("U0151", "Perte de communication avec le calculateur d'airbags", DtcSystem.Network)
        d("U0155", "Perte de communication avec le combiné d'instruments", DtcSystem.Network)
    }

    val all: Collection<DtcInfo> get() = map.values

    /** Informations sur un code ; renvoie une fiche générique si le code n'est pas répertorié. */
    fun info(code: String): DtcInfo = map[code] ?: unknown(code)

    private fun unknown(code: String): DtcInfo {
        val manufacturer = code.length >= 2 && (code[1] == '1' || code[1] == '3')
        val sys = when (code.firstOrNull()) {
            'U' -> DtcSystem.Network
            'B', 'C' -> DtcSystem.Body
            else -> DtcSystem.Sensors
        }
        val title = if (manufacturer) "Code spécifique au constructeur" else "Code non répertorié dans la base"
        return DtcInfo(code, title, sys, Severity.Mid, known = false)
    }
}
