#!/usr/bin/env python3
"""Génère app/src/main/resources/dtc_p0.txt : codes défaut génériques P0xxx (SAE J2012), titres en français.

  python3 tools/dtc/gen_p0.py

Format d'une ligne : CODE|Titre|Système|Gravité   (Système = nom de DtcSystem, Gravité = C critique, M moyen, L mineur).
Les numéros suivent la norme : ce sont des chiffres décimaux (P0136 est suivi de P0137 ... P0139, P0140, P0141).
"""
import os
import re
import sys

codes = {}


def add(n, title, system, sev=None):
    code = n if isinstance(n, str) else "P%04d" % n
    assert code not in codes, "doublon " + code
    codes[code] = [title, system, sev]


CRLHI = ["défaut de circuit", "plage / performances", "signal bas", "signal haut", "signal intermittent"]
CRLH = CRLHI[:4]


def fam(start, noun, system, kinds=CRLHI, sev=None):
    for i, k in enumerate(kinds):
        add(start + i, f"{noun} : {k}", system, sev)


def many(start, system, titles, sev=None):
    for i, t in enumerate(titles):
        if t:
            add(start + i, t, system, sev)


# ----------------------------------------------------------------------------- P0000 - P0099
fam(1, "Régulateur de volume de carburant", "Fuel", ["défaut de circuit / circuit ouvert", "plage / performances", "signal bas", "signal haut"])
fam(5, "Électrovanne de coupure de carburant A", "Fuel", ["défaut de circuit / circuit ouvert", "signal bas", "signal haut"])
add(8, "Position du moteur (calage) : performances, banc 1", "Timing")
add(9, "Position du moteur (calage) : performances, banc 2", "Timing")
for bank, base in ((1, 10), (2, 20)):
    many(base, "Timing", [
        f"Actionneur d'arbre à cames A (admission) : défaut de circuit, banc {bank}",
        f"Calage de l'arbre à cames A (admission) trop avancé ou performances, banc {bank}",
        f"Calage de l'arbre à cames A (admission) trop retardé, banc {bank}",
        f"Actionneur d'arbre à cames B (échappement) : défaut de circuit, banc {bank}",
        f"Calage de l'arbre à cames B (échappement) trop avancé ou performances, banc {bank}",
        f"Calage de l'arbre à cames B (échappement) trop retardé, banc {bank}",
    ])
many(16, "Timing", [
    "Corrélation vilebrequin / arbre à cames A, banc 1", "Corrélation vilebrequin / arbre à cames B, banc 1",
    "Corrélation vilebrequin / arbre à cames A, banc 2", "Corrélation vilebrequin / arbre à cames B, banc 2",
])
add(26, "Électrovanne de commande des soupapes d'admission : plage / performances, banc 1", "Timing")
add(27, "Électrovanne de commande des soupapes d'échappement : plage / performances, banc 1", "Timing")
add(28, "Électrovanne de commande des soupapes d'admission : plage / performances, banc 2", "Timing")
add(29, "Électrovanne de commande des soupapes d'échappement : plage / performances, banc 2", "Timing")
heat = lambda b, s: f"Chauffage de la sonde lambda (banc {b}, sonde {s})"
fam(30, heat(1, 1), "Exhaust", ["défaut du circuit de commande", "circuit de commande bas", "circuit de commande haut"])
fam(33, "Vanne de décharge (bypass) du turbo / compresseur", "Boost", ["défaut du circuit de commande", "circuit de commande bas", "circuit de commande haut"])
fam(36, heat(1, 2), "Exhaust", ["défaut du circuit de commande", "circuit de commande bas", "circuit de commande haut"])
add(39, "Vanne de décharge (bypass) du turbo / compresseur : plage / performances", "Boost")
add(40, "Signaux des sondes lambda inversés (banc 1 sonde 1 / banc 2 sonde 1)", "Exhaust")
add(41, "Signaux des sondes lambda inversés (banc 1 sonde 2 / banc 2 sonde 2)", "Exhaust")
fam(42, heat(1, 3), "Exhaust", ["défaut du circuit de commande", "circuit de commande bas", "circuit de commande haut"])
fam(45, "Électrovanne de pression de suralimentation (turbo / compresseur)", "Boost", ["défaut de circuit / circuit ouvert", "plage / performances", "signal bas", "signal haut"])
add(49, "Turbo / compresseur : survitesse de la turbine", "Boost", "C")
fam(50, heat(2, 1), "Exhaust", ["défaut du circuit de commande", "circuit de commande bas", "circuit de commande haut"])
add(53, "Résistance du chauffage de la sonde lambda (banc 1, sonde 1)", "Exhaust")
add(54, "Résistance du chauffage de la sonde lambda (banc 1, sonde 2)", "Exhaust")
add(55, "Résistance du chauffage de la sonde lambda (banc 1, sonde 3)", "Exhaust")
fam(56, heat(2, 2), "Exhaust", ["défaut du circuit de commande", "circuit de commande bas", "circuit de commande haut"])
add(59, "Résistance du chauffage de la sonde lambda (banc 2, sonde 1)", "Exhaust")
add(60, "Résistance du chauffage de la sonde lambda (banc 2, sonde 2)", "Exhaust")
add(61, "Résistance du chauffage de la sonde lambda (banc 2, sonde 3)", "Exhaust")
fam(62, heat(2, 3), "Exhaust", ["défaut du circuit de commande", "circuit de commande bas", "circuit de commande haut"])
add(65, "Injecteur d'air assisté : plage / performances", "Fuel")
add(66, "Injecteur d'air assisté : circuit bas ou ouvert", "Fuel")
add(67, "Injecteur d'air assisté : circuit haut", "Fuel")
add(68, "Corrélation pression du collecteur (MAP) / débit d'air (MAF) / position du papillon", "Intake")
add(69, "Corrélation pression du collecteur (MAP) / pression atmosphérique", "Intake")
fam(70, "Capteur de température d'air ambiant", "Sensors")
for bank, base in ((1, 75), (2, 81)):
    fam(base, f"Électrovanne des soupapes d'admission (banc {bank})", "Timing", ["défaut de circuit", "circuit bas", "circuit haut"])
    fam(base + 3, f"Électrovanne des soupapes d'échappement (banc {bank})", "Timing", ["défaut de circuit", "circuit bas", "circuit haut"])
add(87, "Pression de rampe / du circuit de carburant trop basse", "Fuel")
add(88, "Pression de rampe / du circuit de carburant trop haute", "Fuel")
add(89, "Régulateur de pression de carburant 1 : performances", "Fuel")
fam(90, "Régulateur de pression de carburant 1", "Fuel", ["défaut du circuit de commande", "circuit de commande bas", "circuit de commande haut"])
add(93, "Fuite dans le circuit de carburant : fuite importante", "Fuel", "C")
add(94, "Fuite dans le circuit de carburant : fuite faible", "Fuel")
fam(95, "Capteur de température d'air d'admission 2", "Intake")

# ----------------------------------------------------------------------------- P0100 - P0199
fam(100, "Débitmètre d'air massique (MAF)", "Intake")
fam(105, "Capteur de pression du collecteur / pression atmosphérique (MAP)", "Intake")
fam(110, "Capteur de température d'air d'admission (IAT)", "Intake")
fam(115, "Capteur de température du liquide de refroidissement (ECT)", "Cooling")
fam(120, "Capteur de position de la pédale / du papillon A", "Intake")
add(125, "Température du liquide de refroidissement trop basse pour la régulation en boucle fermée", "Cooling")
add(126, "Température du liquide de refroidissement insuffisante pour un fonctionnement stable", "Cooling")
add(127, "Température d'air d'admission trop élevée", "Intake")
add(128, "Thermostat : température du liquide inférieure à la température de régulation", "Cooling")
add(129, "Pression atmosphérique trop basse", "Sensors")
for (bank, sensor), base in (((1, 1), 130), ((1, 2), 136), ((1, 3), 142), ((2, 1), 150), ((2, 2), 156), ((2, 3), 162)):
    n = f"Sonde lambda (banc {bank}, sonde {sensor})"
    many(base, "Exhaust", [
        f"{n} : défaut de circuit", f"{n} : tension basse", f"{n} : tension haute", f"{n} : réponse lente",
        f"{n} : aucune activité détectée", f"{n} : défaut du circuit de chauffage",
    ])
add(148, "Erreur de quantité de carburant injectée", "Fuel")
add(149, "Erreur de calage de l'injection", "Fuel")
add(168, "Température du carburant trop élevée", "Fuel")
add(169, "Composition du carburant incorrecte", "Fuel")
add(170, "Correction carburant : défaut, banc 1", "Fuel")
add(171, "Mélange trop pauvre, banc 1", "Fuel")
add(172, "Mélange trop riche, banc 1", "Fuel")
add(173, "Correction carburant : défaut, banc 2", "Fuel")
add(174, "Mélange trop pauvre, banc 2", "Fuel")
add(175, "Mélange trop riche, banc 2", "Fuel")
fam(176, "Capteur de composition du carburant", "Fuel", CRLH)
fam(180, "Capteur de température du carburant A", "Fuel")
fam(185, "Capteur de température du carburant B", "Fuel")
fam(190, "Capteur de pression de rampe de carburant", "Fuel")
fam(195, "Capteur de température d'huile moteur", "Sensors")

# ----------------------------------------------------------------------------- P0200 - P0299
add(200, "Injecteur : défaut de circuit", "Fuel")
for cyl in range(1, 13):
    add(200 + cyl, f"Injecteur du cylindre {cyl} : défaut de circuit", "Fuel")
add(213, "Injecteur de démarrage à froid 1 : défaut", "Fuel")
add(214, "Injecteur de démarrage à froid 2 : défaut", "Fuel")
add(215, "Électrovanne de coupure du moteur : défaut", "Ecu")
add(216, "Commande du calage de l'injection : défaut de circuit", "Fuel")
add(217, "Surchauffe du moteur", "Cooling", "C")
add(218, "Surchauffe de la boîte de vitesses", "Transmission", "C")
add(219, "Survitesse du moteur (surrégime)", "Ecu", "C")
fam(220, "Capteur de position de la pédale / du papillon B", "Intake")
fam(225, "Capteur de position de la pédale / du papillon C", "Intake")
add(230, "Pompe à carburant : défaut du circuit primaire", "Fuel")
add(231, "Pompe à carburant : circuit secondaire bas", "Fuel")
add(232, "Pompe à carburant : circuit secondaire haut", "Fuel")
add(233, "Pompe à carburant : circuit secondaire intermittent", "Fuel")
add(234, "Turbo / compresseur : surpression (overboost)", "Boost", "C")
fam(235, "Capteur de pression de suralimentation A", "Boost", CRLH)
fam(239, "Capteur de pression de suralimentation B", "Boost", CRLH)
fam(243, "Électrovanne de wastegate A", "Boost", ["défaut de circuit", "plage / performances", "signal bas", "signal haut"])
fam(247, "Électrovanne de wastegate B", "Boost", ["défaut de circuit", "plage / performances", "signal bas", "signal haut"])
fam(251, "Pompe d'injection : dosage du carburant A (came / rotor / injecteur)", "Fuel")
fam(256, "Pompe d'injection : dosage du carburant B (came / rotor / injecteur)", "Fuel")
for cyl in range(1, 13):
    b = 261 + 3 * (cyl - 1)
    add(b, f"Injecteur du cylindre {cyl} : circuit bas", "Fuel")
    add(b + 1, f"Injecteur du cylindre {cyl} : circuit haut", "Fuel")
    add(b + 2, f"Injecteur du cylindre {cyl} : contribution / équilibre défectueux", "Fuel")
add(297, "Vitesse du véhicule excessive", "Sensors")
add(298, "Surchauffe de l'huile moteur", "Cooling", "C")
add(299, "Turbo / compresseur : sous-pression (underboost)", "Boost")

# ----------------------------------------------------------------------------- P0300 - P0399
add(300, "Ratés d'allumage aléatoires / multiples détectés", "Ignition", "C")
for cyl in range(1, 13):
    add(300 + cyl, f"Raté d'allumage détecté, cylindre {cyl}", "Ignition", "C")
add(313, "Ratés d'allumage détectés avec un niveau de carburant bas", "Ignition")
add(314, "Raté d'allumage sur un cylindre (cylindre non précisé)", "Ignition", "C")
add(315, "Variation du système de position du vilebrequin non apprise", "Sensors")
add(316, "Ratés d'allumage détectés dans les 1 000 premiers tours du moteur", "Ignition")
add(317, "Capteur de route accidentée : matériel absent", "Sensors")
add(318, "Capteur de route accidentée A : défaut de circuit", "Sensors")
add(319, "Capteur de route accidentée B : défaut de circuit", "Sensors")
add(320, "Capteur de régime moteur (allumage / distributeur) : défaut de circuit", "Sensors")
add(321, "Capteur de régime moteur (allumage / distributeur) : plage / performances", "Sensors")
add(322, "Capteur de régime moteur (allumage / distributeur) : aucun signal", "Sensors")
add(323, "Capteur de régime moteur (allumage / distributeur) : signal intermittent", "Sensors")
add(324, "Système de contrôle du cliquetis : erreur", "Sensors")
fam(325, "Capteur de cliquetis 1 (banc 1)", "Sensors")
fam(330, "Capteur de cliquetis 2 (banc 2)", "Sensors")
fam(335, "Capteur de position du vilebrequin A", "Sensors")
fam(340, "Capteur de position de l'arbre à cames A (banc 1)", "Sensors")
fam(345, "Capteur de position de l'arbre à cames A (banc 2)", "Sensors")
add(350, "Bobine d'allumage : défaut du circuit primaire / secondaire", "Ignition")
for i, letter in enumerate("ABCDEFGHIJKL"):
    add(351 + i, f"Bobine d'allumage {letter} : défaut du circuit primaire / secondaire", "Ignition")
add(363, "Ratés d'allumage détectés : injection coupée", "Ignition")
fam(365, "Capteur de position de l'arbre à cames B (banc 1)", "Sensors")
for base, sig in ((370, "A"), (375, "B")):
    many(base, "Sensors", [
        f"Signal de référence de calage {sig} (haute résolution) : défaut", f"Signal de référence de calage {sig} : trop d'impulsions",
        f"Signal de référence de calage {sig} : pas assez d'impulsions", f"Signal de référence de calage {sig} : impulsions intermittentes / erratiques",
        f"Signal de référence de calage {sig} : aucune impulsion",
    ])
add(380, "Bougie de préchauffage / réchauffeur A : défaut de circuit", "Ignition")
add(381, "Témoin de préchauffage : défaut de circuit", "Ignition")
add(382, "Bougie de préchauffage / réchauffeur B : défaut de circuit", "Ignition")
fam(385, "Capteur de position du vilebrequin B", "Sensors")
fam(390, "Capteur de position de l'arbre à cames B (banc 2)", "Sensors")

# ----------------------------------------------------------------------------- P0400 - P0499
many(400, "Exhaust", [
    "Recirculation des gaz d'échappement (EGR) : défaut de débit", "EGR : débit insuffisant", "EGR : débit excessif",
    "Vanne EGR : défaut du circuit de commande", "Vanne EGR : plage / performances", "Capteur EGR A : signal bas",
    "Capteur EGR A : signal haut", "Capteur EGR B : signal bas", "Capteur EGR B : signal haut", "Capteur EGR A : défaut de circuit",
])
many(410, "Exhaust", [
    "Système d'air secondaire : défaut", "Système d'air secondaire : débit incorrect", "Vanne de commutation d'air secondaire A : défaut de circuit",
    "Vanne de commutation d'air secondaire A : circuit ouvert", "Vanne de commutation d'air secondaire A : court-circuit",
    "Vanne de commutation d'air secondaire B : défaut de circuit", "Vanne de commutation d'air secondaire B : circuit ouvert",
    "Vanne de commutation d'air secondaire B : court-circuit", "Relais de pompe à air secondaire A : défaut de circuit",
    "Relais de pompe à air secondaire B : défaut de circuit",
])
for bank, base in ((1, 420), (2, 430)):
    many(base, "Exhaust", [
        f"Rendement du catalyseur inférieur au seuil, banc {bank}", f"Catalyseur (montée en température) : rendement inférieur au seuil, banc {bank}",
        f"Catalyseur principal : rendement inférieur au seuil, banc {bank}", f"Catalyseur chauffé : rendement inférieur au seuil, banc {bank}",
        f"Catalyseur chauffé : température inférieure au seuil, banc {bank}",
    ])
fam(425, "Capteur de température du catalyseur (banc 1, sonde 1)", "Exhaust", CRLH)
add(429, "Chauffage du catalyseur (banc 1) : défaut du circuit de commande", "Exhaust")
fam(435, "Capteur de température du catalyseur (banc 2, sonde 1)", "Exhaust", CRLH)
add(439, "Chauffage du catalyseur (banc 2) : défaut du circuit de commande", "Exhaust")
many(440, "Evap", [
    "Système EVAP (vapeurs de carburant) : défaut", "EVAP : débit de purge incorrect", "EVAP : petite fuite détectée",
    "Électrovanne de purge EVAP : défaut du circuit de commande", "Électrovanne de purge EVAP : circuit ouvert", "Électrovanne de purge EVAP : court-circuit",
    "Vanne de mise à l'air EVAP : défaut du circuit de commande", "Vanne de mise à l'air EVAP : circuit ouvert", "Vanne de mise à l'air EVAP : court-circuit",
    "Vanne / électrovanne de mise à l'air EVAP : défaut de circuit",
])
fam(450, "Capteur / contacteur de pression EVAP", "Evap")
add(455, "EVAP : grosse fuite détectée", "Evap")
add(456, "EVAP : très petite fuite détectée", "Evap")
add(457, "EVAP : fuite détectée (bouchon de réservoir desserré ou absent)", "Evap")
add(458, "Électrovanne de purge EVAP : circuit bas", "Evap")
add(459, "Électrovanne de purge EVAP : circuit haut", "Evap")
fam(460, "Capteur de niveau de carburant A", "Fuel")
fam(465, "Capteur de débit de purge EVAP", "Evap")
fam(470, "Capteur de pression d'échappement A", "Exhaust")
fam(475, "Vanne de régulation de pression d'échappement", "Exhaust")
many(480, "Cooling", [
    "Ventilateur de refroidissement 1 : défaut du circuit de commande", "Ventilateur de refroidissement 2 : défaut du circuit de commande",
    "Ventilateur de refroidissement 3 : défaut du circuit de commande", "Ventilateur de refroidissement : contrôle de cohérence",
    "Ventilateur de refroidissement : surintensité", "Ventilateur de refroidissement : défaut d'alimentation / de masse",
])
add(486, "Capteur EGR B : défaut de circuit", "Exhaust")
add(487, "Papillon EGR : défaut du circuit de commande de position", "Exhaust")
add(488, "Papillon EGR : plage / performances", "Exhaust")
add(489, "Vanne EGR : circuit de commande bas", "Exhaust")
add(490, "Vanne EGR : circuit de commande haut", "Exhaust")
add(491, "Système d'air secondaire : débit insuffisant, banc 1", "Exhaust")
add(492, "Système d'air secondaire : débit insuffisant, banc 2", "Exhaust")
add(493, "Ventilateur de refroidissement : survitesse", "Cooling")
add(494, "Ventilateur de refroidissement : vitesse trop basse", "Cooling")
add(495, "Ventilateur de refroidissement : vitesse trop élevée", "Cooling")
add(496, "EVAP : débit de purge trop élevé", "Evap")
add(497, "EVAP : débit de purge trop faible", "Evap")
add(498, "Vanne de mise à l'air EVAP : circuit de commande bas", "Evap")
add(499, "Vanne de mise à l'air EVAP : circuit de commande haut", "Evap")

# ----------------------------------------------------------------------------- P0500 - P0599
many(500, "Sensors", [
    "Capteur de vitesse du véhicule A : défaut", "Capteur de vitesse du véhicule A : plage / performances", "Capteur de vitesse du véhicule A : signal bas",
    "Capteur de vitesse du véhicule A : signal intermittent / erratique / haut", "Corrélation des contacteurs de frein A / B",
])
many(505, "Intake", [
    "Régulation du ralenti : défaut", "Régime de ralenti inférieur à l'attendu", "Régime de ralenti supérieur à l'attendu",
    "Régulateur de ralenti : circuit bas", "Régulateur de ralenti : circuit haut", "Contacteur de papillon fermé : défaut",
    "Régulateur d'air de ralenti : défaut de circuit",
])
add(512, "Demande de démarreur : défaut de circuit", "Electrical")
add(513, "Clé d'antidémarrage incorrecte", "Ecu")
add(514, "Capteur de température de batterie : plage / performances", "Electrical")
add(515, "Capteur de température de batterie : défaut de circuit", "Electrical")
add(516, "Capteur de température de batterie : signal bas", "Electrical")
add(517, "Capteur de température de batterie : signal haut", "Electrical")
add(518, "Régulateur d'air de ralenti : signal intermittent", "Intake")
add(519, "Régulateur d'air de ralenti : performances", "Intake")
fam(520, "Capteur / contacteur de pression d'huile moteur", "Sensors", CRLH)
add(524, "Pression d'huile moteur trop basse", "Sensors", "C")
add(525, "Servo du régulateur de vitesse : plage / performances", "Sensors")
many(526, "Cooling", [
    "Capteur de vitesse du ventilateur : défaut de circuit", "Capteur de vitesse du ventilateur : plage / performances",
    "Capteur de vitesse du ventilateur : aucun signal", "Capteur de vitesse du ventilateur : signal intermittent",
])
fam(530, "Capteur de pression de fluide frigorigène de la climatisation A", "Cooling", CRLH)
add(534, "Climatisation : perte de fluide frigorigène", "Cooling")
fam(535, "Capteur de température d'évaporateur de la climatisation", "Cooling")
many(540, "Intake", [
    "Réchauffeur d'air d'admission A : défaut de circuit", "Réchauffeur d'air d'admission A : circuit bas",
    "Réchauffeur d'air d'admission A : circuit haut", "Réchauffeur d'air d'admission A : circuit ouvert",
])
fam(544, "Capteur de température des gaz d'échappement (banc 1, sonde 1)", "Exhaust", ["défaut de circuit", "signal bas", "signal haut"])
fam(547, "Capteur de température des gaz d'échappement (banc 2, sonde 1)", "Exhaust", ["défaut de circuit", "signal bas", "signal haut"])
fam(550, "Capteur de pression de direction assistée", "Sensors")
fam(555, "Capteur de pression du servofrein", "Sensors")
many(560, "Electrical", ["Tension du système : défaut", "Tension du système : instable", "Tension du système : trop basse", "Tension du système : trop haute"])
many(564, "Sensors", [
    "Régulateur de vitesse : entrée multifonction A, défaut de circuit", "Régulateur de vitesse : signal ON", "Régulateur de vitesse : signal OFF",
    "Régulateur de vitesse : signal RESUME (reprise)", "Régulateur de vitesse : signal SET (mémorisation)", "Régulateur de vitesse : signal COAST (décélération)",
    "Régulateur de vitesse : signal ACCEL (accélération)", "Régulateur de vitesse, contacteur de frein A : défaut de circuit",
    "Régulateur de vitesse, contacteur de frein A : circuit bas", "Régulateur de vitesse, contacteur de frein A : circuit haut",
    "Régulateur de vitesse : vitesse du véhicule trop élevée", "Régulateur de vitesse : défaut du circuit d'entrée",
    "Régulateur de vitesse : circuit d'entrée bas", "Régulateur de vitesse : circuit d'entrée haut",
    "Régulateur de vitesse : entrée multifonction A bloquée", "Régulateur de vitesse : entrée multifonction A, plage / performances",
    "Régulateur de vitesse : entrée multifonction A, circuit bas", "Régulateur de vitesse : entrée multifonction A, circuit haut",
    "Régulateur de vitesse : commande du vide, défaut / circuit ouvert", "Régulateur de vitesse : commande du vide, circuit bas",
    "Régulateur de vitesse : commande du vide, circuit haut", "Régulateur de vitesse : corrélation des entrées multifonction A / B",
    "Régulateur de vitesse : commande de mise à l'air, circuit ouvert", "Régulateur de vitesse : commande de mise à l'air, circuit bas",
    "Régulateur de vitesse : commande de mise à l'air, circuit haut", "Régulateur de vitesse : entrée multifonction B, défaut de circuit",
    "Régulateur de vitesse : entrée multifonction B bloquée", "Régulateur de vitesse : entrée multifonction B, plage / performances",
    "Régulateur de vitesse : entrée multifonction B, circuit bas", "Régulateur de vitesse : entrée multifonction B, circuit haut",
    "Servo du régulateur de vitesse : défaut du circuit de commande / ouvert", "Servo du régulateur de vitesse : circuit de commande bas",
    "Servo du régulateur de vitesse : circuit de commande haut",
])
add(597, "Réchauffeur de thermostat : défaut du circuit de commande / ouvert", "Cooling")
add(598, "Réchauffeur de thermostat : circuit de commande bas", "Cooling")
add(599, "Réchauffeur de thermostat : circuit de commande haut", "Cooling")

# ----------------------------------------------------------------------------- P0600 - P0699
many(600, "Ecu", [
    "Liaison de communication série : défaut", "Calculateur : erreur de somme de contrôle de la mémoire interne", "Calculateur : erreur de programmation",
    "Calculateur : erreur de la mémoire KAM", "Calculateur : erreur de mémoire RAM", "Calculateur : erreur de mémoire ROM", "Calculateur : erreur du processeur",
    "Calculateur : performances", "Calculateur : sortie de vitesse véhicule A, défaut", "Calculateur : sortie de vitesse véhicule B, défaut",
    "Calculateur : erreur des options du véhicule", "Module de commande des injecteurs (FICM) : performances", "Module de commande des injecteurs (FICM) : défaut du relais",
    "Calculateur de boîte (TCM) : erreur du processeur", "Calculateur moteur / boîte : incompatibles", "Relais de démarreur : défaut de circuit",
    "Relais de démarreur : circuit bas", "Relais de démarreur : circuit haut", "Module de carburant alternatif : erreur de mémoire KAM",
    "Module de carburant alternatif : erreur de mémoire RAM / ROM",
])
many(620, "Electrical", [
    "Alternateur : défaut du circuit de commande", "Alternateur : défaut du circuit du témoin L", "Alternateur : défaut du circuit d'excitation F",
    "Alternateur : défaut du circuit du témoin de charge",
])
add(624, "Témoin du bouchon de réservoir : défaut du circuit de commande", "Evap")
add(625, "Alternateur : borne d'excitation F, circuit bas", "Electrical")
add(626, "Alternateur : borne d'excitation F, circuit haut", "Electrical")
add(627, "Pompe à carburant A : défaut du circuit de commande / ouvert", "Fuel")
add(628, "Pompe à carburant A : circuit de commande bas", "Fuel")
add(629, "Pompe à carburant A : circuit de commande haut", "Fuel")
many(630, "Ecu", [
    "VIN non programmé ou incompatible (calculateur moteur)", "VIN non programmé ou incompatible (calculateur de boîte)",
    "Compteur kilométrique non programmé (calculateur moteur)", "Clé d'antidémarrage non programmée (calculateur moteur)",
    "Température interne du calculateur trop élevée",
])
add(635, "Direction assistée : défaut du circuit de commande", "Sensors")
add(636, "Direction assistée : circuit de commande bas", "Sensors")
add(637, "Direction assistée : circuit de commande haut", "Sensors")
add(638, "Commande du papillon motorisé : plage / performances, banc 1", "Intake")
add(639, "Commande du papillon motorisé : plage / performances, banc 2", "Intake")
add(640, "Réchauffeur d'air d'admission : défaut du circuit de commande", "Intake")
add(641, "Tension de référence des capteurs A : défaut de circuit / ouvert", "Electrical")
add(642, "Tension de référence des capteurs A : circuit bas", "Electrical")
add(643, "Tension de référence des capteurs A : circuit haut", "Electrical")
add(644, "Afficheur conducteur : défaut de la liaison série", "Network")
add(645, "Relais d'embrayage de climatisation : défaut du circuit de commande", "Cooling")
add(646, "Relais d'embrayage de climatisation : circuit de commande bas", "Cooling")
add(647, "Relais d'embrayage de climatisation : circuit de commande haut", "Cooling")
add(648, "Témoin d'antidémarrage : défaut du circuit de commande", "Ecu")
add(649, "Témoin du régulateur de vitesse : défaut du circuit de commande", "Ecu")
add(650, "Témoin de défaut moteur (MIL) : défaut du circuit de commande", "Ecu")
add(651, "Tension de référence des capteurs B : défaut de circuit / ouvert", "Electrical")
add(652, "Tension de référence des capteurs B : circuit bas", "Electrical")
add(653, "Tension de référence des capteurs B : circuit haut", "Electrical")
add(654, "Sortie du régime moteur : défaut de circuit", "Ecu")
add(655, "Témoin de surchauffe moteur : défaut du circuit de sortie", "Ecu")
add(656, "Jauge de carburant : défaut du circuit de sortie", "Fuel")
add(657, "Tension d'alimentation de l'actionneur A : défaut de circuit / ouvert", "Electrical")
add(658, "Tension d'alimentation de l'actionneur A : circuit bas", "Electrical")
add(659, "Tension d'alimentation de l'actionneur A : circuit haut", "Electrical")
for bank, base in ((1, 660), (2, 663)):
    fam(base, f"Vanne d'admission variable du collecteur (banc {bank})", "Intake", ["défaut du circuit de commande", "circuit de commande bas", "circuit de commande haut"])
add(666, "Capteur de température interne du calculateur : défaut de circuit", "Ecu")
add(667, "Capteur de température interne du calculateur : plage / performances", "Ecu")
add(668, "Capteur de température interne du calculateur : signal bas", "Ecu")
add(669, "Capteur de température interne du calculateur : signal haut", "Ecu")
add(670, "Module de bougies de préchauffage : défaut du circuit de commande", "Ignition")
for cyl in range(1, 13):
    add(670 + cyl, f"Bougie de préchauffage du cylindre {cyl} : défaut de circuit", "Ignition")
add(683, "Module de préchauffage : défaut de communication avec le calculateur moteur", "Ignition")
add(684, "Module de préchauffage : plage / performances de la communication", "Ignition")
add(685, "Relais d'alimentation du calculateur : défaut du circuit de commande / ouvert", "Electrical")
add(686, "Relais d'alimentation du calculateur : circuit de commande bas", "Electrical")
add(687, "Relais d'alimentation du calculateur : circuit de commande haut", "Electrical")
add(688, "Relais d'alimentation du calculateur : défaut du circuit de détection / ouvert", "Electrical")
add(689, "Relais d'alimentation du calculateur : circuit de détection bas", "Electrical")
add(690, "Relais d'alimentation du calculateur : circuit de détection haut", "Electrical")
for i, (n, kind) in enumerate([(1, "bas"), (1, "haut"), (2, "bas"), (2, "haut"), (3, "bas"), (3, "haut")]):
    add(691 + i, f"Ventilateur de refroidissement {n} : circuit de commande {kind}", "Cooling")
add(697, "Tension de référence des capteurs C : défaut de circuit / ouvert", "Electrical")
add(698, "Tension de référence des capteurs C : circuit bas", "Electrical")
add(699, "Tension de référence des capteurs C : circuit haut", "Electrical")

# ----------------------------------------------------------------------------- P0700 - P0799 (transmission)
T = "Transmission"
add(700, "Système de commande de la transmission : défaut", T)
add(701, "Système de commande de la transmission : plage / performances", T)
add(702, "Système de commande de la transmission : défaut électrique", T)
add(703, "Contacteur de frein B : défaut de circuit", T)
add(704, "Contacteur d'embrayage : défaut du circuit d'entrée", T)
fam(705, "Capteur de plage de la boîte de vitesses (entrée PRNDL)", T)
fam(710, "Capteur de température du liquide de boîte A", T)
many(715, T, ["Capteur de vitesse d'entrée / de turbine A : défaut de circuit", "Capteur de vitesse d'entrée / de turbine A : plage / performances",
              "Capteur de vitesse d'entrée / de turbine A : aucun signal", "Capteur de vitesse d'entrée / de turbine A : signal intermittent"])
add(719, "Contacteur de frein B : signal bas", T)
many(720, T, ["Capteur de vitesse de sortie de boîte : défaut de circuit", "Capteur de vitesse de sortie de boîte : plage / performances",
              "Capteur de vitesse de sortie de boîte : aucun signal", "Capteur de vitesse de sortie de boîte : signal intermittent"])
add(724, "Contacteur de frein B : signal haut", T)
many(725, T, ["Circuit d'entrée du régime moteur : défaut", "Circuit d'entrée du régime moteur : plage / performances",
              "Circuit d'entrée du régime moteur : aucun signal", "Circuit d'entrée du régime moteur : signal intermittent"])
add(729, "Rapport 6 incorrect", T)
add(730, "Rapport de transmission incorrect", T)
for g, n in enumerate(["1", "2", "3", "4", "5", "marche arrière"]):
    add(731 + g, f"Rapport {n} incorrect" if n != "marche arrière" else "Marche arrière : rapport incorrect", T)
add(737, "Circuit de sortie du régime moteur du calculateur de boîte : défaut", T)
add(738, "Circuit de sortie du régime moteur du calculateur de boîte : signal bas", T)
add(739, "Circuit de sortie du régime moteur du calculateur de boîte : signal haut", T)
SOL = ["défaut de circuit", "performances ou bloquée ouverte", "bloquée fermée", "défaut électrique", "signal intermittent"]
fam(740, "Embrayage de convertisseur de couple (verrouillage)", T, SOL)
fam(745, "Électrovanne de pression A", T, SOL)
fam(750, "Électrovanne de passage A", T, SOL)
fam(755, "Électrovanne de passage B", T, SOL)
fam(760, "Électrovanne de passage C", T, SOL)
fam(765, "Électrovanne de passage D", T, SOL)
fam(770, "Électrovanne de passage E", T, SOL)
fam(775, "Électrovanne de pression B", T, SOL)
add(780, "Défaut de passage de vitesse", T)
add(781, "Défaut de passage 1-2", T)
add(782, "Défaut de passage 2-3", T)
add(783, "Défaut de passage 3-4", T)
add(784, "Défaut de passage 4-5", T)
fam(785, "Électrovanne de passage / de temporisation", T)
add(790, "Contacteur de mode normal / performance : défaut de circuit", T)
many(791, T, ["Capteur de vitesse d'arbre intermédiaire A : défaut de circuit", "Capteur de vitesse d'arbre intermédiaire A : plage / performances",
              "Capteur de vitesse d'arbre intermédiaire A : aucun signal", "Capteur de vitesse d'arbre intermédiaire A : signal intermittent"])
fam(795, "Électrovanne de pression C", T, SOL)

# ----------------------------------------------------------------------------- P0800 - P0899
add(800, "Boîte de transfert (4x4) : demande de voyant de défaut", T)
add(801, "Inhibition de la marche arrière : défaut du circuit de commande", T)
add(802, "Demande de voyant de défaut moteur (boîte) : défaut de circuit", T)
add(803, "Électrovanne de passage 1-4 (saut de rapport) : défaut du circuit de commande", T)
add(804, "Témoin de passage 1-4 : défaut du circuit de commande", T)
fam(805, "Capteur de position d'embrayage", T)
add(810, "Position de l'embrayage : erreur de commande", T)
add(811, "Glissement excessif de l'embrayage", T)
add(812, "Entrée de marche arrière : défaut de circuit", T)
add(813, "Sortie de marche arrière : défaut de circuit", T)
add(814, "Affichage de la plage de boîte : défaut de circuit", T)
add(815, "Contacteur de passage au rapport supérieur : défaut de circuit", T)
add(816, "Contacteur de passage au rapport inférieur : défaut de circuit", T)
add(817, "Interdiction de démarrage : défaut de circuit", T)
add(818, "Contacteur de déconnexion de la transmission : défaut du circuit d'entrée", T)
add(819, "Corrélation des contacteurs montée / descente avec la plage de boîte", T)
add(820, "Capteur de position X-Y du levier de vitesses : défaut de circuit", T)
add(821, "Capteur de position X du levier de vitesses : défaut de circuit", T)
add(822, "Capteur de position Y du levier de vitesses : défaut de circuit", T)
add(823, "Capteur de position X du levier de vitesses : signal intermittent", T)
add(824, "Capteur de position Y du levier de vitesses : signal intermittent", T)
add(825, "Contacteur de levier de vitesses (poussé / tiré) : défaut", T)
add(826, "Contacteur de montée / descente de rapports : défaut de circuit", T)
add(827, "Contacteur de montée / descente de rapports : circuit bas", T)
add(828, "Contacteur de montée / descente de rapports : circuit haut", T)
add(829, "Passage 5-6 : défaut", T)
add(830, "Contacteur de pédale d'embrayage A : défaut de circuit", T)
add(831, "Contacteur de pédale d'embrayage A : circuit bas", T)
add(832, "Contacteur de pédale d'embrayage A : circuit haut", T)
add(833, "Contacteur de pédale d'embrayage B : défaut de circuit", T)
add(834, "Contacteur de pédale d'embrayage B : circuit bas", T)
add(835, "Contacteur de pédale d'embrayage B : circuit haut", T)
add(836, "Contacteur de quatre roues motrices : défaut de circuit", T)
add(837, "Contacteur de quatre roues motrices : plage / performances", T)
add(838, "Contacteur de quatre roues motrices : circuit bas", T)
add(839, "Contacteur de quatre roues motrices : circuit haut", T)
fam(840, "Capteur / contacteur de pression du liquide de boîte A", T)
fam(845, "Capteur / contacteur de pression du liquide de boîte B", T)
add(850, "Contacteur point mort / stationnement : défaut du circuit d'entrée", T)
add(851, "Contacteur point mort / stationnement : circuit d'entrée bas", T)
add(852, "Contacteur point mort / stationnement : circuit d'entrée haut", T)
add(853, "Contacteur de position Drive : défaut du circuit d'entrée", T)
add(854, "Contacteur de position Drive : circuit d'entrée bas", T)
add(855, "Contacteur de position Drive : circuit d'entrée haut", T)
add(856, "Anti-patinage : défaut du signal d'entrée", T)
add(857, "Anti-patinage : plage / performances du signal d'entrée", T)
add(858, "Anti-patinage : signal d'entrée bas", T)
add(859, "Anti-patinage : signal d'entrée haut", T)
add(860, "Module de passage des rapports : défaut du circuit de communication", T)
add(861, "Module de passage des rapports : circuit de communication bas", T)
add(862, "Module de passage des rapports : circuit de communication haut", T)
add(863, "Calculateur de boîte (TCM) : défaut du circuit de communication", T)
add(864, "Calculateur de boîte (TCM) : plage / performances de la communication", T)
add(865, "Calculateur de boîte (TCM) : circuit de communication bas", T)
add(866, "Calculateur de boîte (TCM) : circuit de communication haut", T)
add(867, "Pression du liquide de boîte : défaut", T)
add(868, "Pression du liquide de boîte : trop basse", T)
add(869, "Pression du liquide de boîte : trop haute", T)
fam(870, "Capteur / contacteur de pression du liquide de boîte C", T)
fam(875, "Capteur / contacteur de pression du liquide de boîte D", T)
fam(880, "Alimentation du calculateur de boîte (TCM)", T, ["défaut du signal d'entrée", "plage / performances", "signal bas", "signal haut", "signal intermittent"])
add(885, "Relais d'alimentation du TCM : défaut du circuit de commande / ouvert", T)
add(886, "Relais d'alimentation du TCM : circuit de commande bas", T)
add(887, "Relais d'alimentation du TCM : circuit de commande haut", T)
add(888, "Relais d'alimentation du TCM : défaut du circuit de détection", T)
add(889, "Relais d'alimentation du TCM : plage / performances du circuit de détection", T)
add(890, "Relais d'alimentation du TCM : circuit de détection bas", T)
add(891, "Relais d'alimentation du TCM : circuit de détection haut", T)
add(892, "Relais d'alimentation du TCM : circuit de détection intermittent", T)
add(893, "Plusieurs rapports enclenchés en même temps", T)
add(894, "Composant de la transmission : glissement", T)
add(895, "Temps de passage de rapport trop court", T)
add(896, "Temps de passage de rapport trop long", T)
add(897, "Liquide de boîte détérioré", T)
add(898, "Demande de voyant de défaut (boîte) : circuit bas", T)
add(899, "Demande de voyant de défaut (boîte) : circuit haut", T)

# ----------------------------------------------------------------------------- P0900 - P0999
fam(900, "Actionneur d'embrayage", T, ["défaut du circuit / ouvert", "plage / performances", "circuit bas", "circuit haut"])
fam(904, "Capteur de position de sélection de grille", T, ["défaut de circuit", "plage / performances", "signal bas", "signal haut", "signal intermittent"])
add(909, "Sélection de grille : erreur de commande", T)
fam(910, "Actionneur de sélection de grille", T, ["défaut du circuit / ouvert", "plage / performances", "circuit bas", "circuit haut"])
fam(914, "Capteur de position de passage de rapport", T, ["défaut de circuit", "plage / performances", "signal bas", "signal haut", "signal intermittent"])
add(919, "Position de passage de rapport : erreur de commande", T)
fam(920, "Actionneur de passage de rapport avant", T, ["défaut du circuit / ouvert", "plage / performances", "circuit bas", "circuit haut"])
fam(924, "Actionneur de passage de rapport (marche arrière)", T, ["défaut du circuit / ouvert", "plage / performances", "circuit bas", "circuit haut"])
fam(928, "Électrovanne de verrouillage du levier de vitesses", T, ["défaut du circuit de commande / ouvert", "plage / performances", "circuit bas", "circuit haut"])
fam(932, "Capteur de pression hydraulique", T, ["défaut de circuit", "plage / performances", "signal bas", "signal haut", "signal intermittent"])
fam(937, "Capteur de température de l'huile hydraulique", T, ["défaut de circuit", "plage / performances", "signal bas", "signal haut", "signal intermittent"])
add(942, "Groupe hydraulique : défaut", T)
add(943, "Groupe hydraulique : période de cycle trop courte", T)
add(944, "Perte de pression hydraulique", T)
fam(945, "Relais de pompe hydraulique", T, ["défaut du circuit / ouvert", "plage / performances", "circuit bas", "circuit haut"])
add(949, "Boîte manuelle automatisée : apprentissage adaptatif non effectué", T)
fam(950, "Commande de boîte manuelle automatisée", T, ["défaut du circuit de commande", "plage / performances", "circuit bas", "circuit haut", "signal intermittent"])
fam(955, "Mode de boîte manuelle automatisée", T, ["défaut de circuit", "plage / performances", "circuit bas", "circuit haut", "signal intermittent"])
fam(960, "Électrovanne de pression A", T, ["défaut du circuit de commande / ouvert", "plage / performances", "circuit bas", "circuit haut"])
fam(964, "Électrovanne de pression B", T, ["défaut du circuit de commande / ouvert", "plage / performances", "circuit bas", "circuit haut"])
fam(968, "Électrovanne de pression C", T, ["défaut du circuit de commande / ouvert", "plage / performances", "circuit bas", "circuit haut"])
for i, letter in enumerate("ABCDE"):
    fam(972 + 3 * i, f"Électrovanne de passage {letter}", T, ["plage / performances", "circuit de commande bas", "circuit de commande haut"])
fam(997, "Électrovanne de passage F", T, ["plage / performances", "circuit de commande bas", "circuit de commande haut"])
fam(987, "Capteur / contacteur de pression du liquide de boîte E", T)
fam(992, "Capteur / contacteur de pression du liquide de boîte F", T)

# ----------------------------------------------------------------------------- Hybrides (quelques codes sûrs)
add("P0A7F", "Batterie hybride : détérioration", "Electrical")
add("P0A80", "Remplacer la batterie hybride", "Electrical")
add("P0A94", "Convertisseur DC/DC : performances", "Electrical")
add("P0AA6", "Batterie hybride : défaut d'isolement du système haute tension", "Electrical", "C")


# ----------------------------------------------------------------------------- Gravité par règles
def severity(title, given):
    if given:
        return given
    t = title.lower()
    if "intermittent" in t or "régulateur de vitesse" in t or "climatisation" in t or "témoin" in t or "afficheur" in t or "bouchon" in t:
        return "L"
    if "fuite faible" in t or "petite fuite" in t or "très petite fuite" in t or "mémoire kam" in t:
        return "L"
    if "calculateur : erreur" in t and "mémoire" in t or "processeur" in t:
        return "C"
    return "M"


def main():
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "resources", "dtc_p0.txt")
    lines = []
    for code in sorted(codes):
        title, system, sev = codes[code]
        assert "|" not in title
        lines.append(f"{code}|{title}|{system}|{severity(title, sev)}")
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print(len(lines), "codes écrits dans", os.path.normpath(out))


if __name__ == "__main__":
    main()
