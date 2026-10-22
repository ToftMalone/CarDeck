# CarDeck 🚗

**CarDeck est l'application Android qui lit ce que ta voiture a à dire.**
Branche un petit boîtier OBD2 sur la prise diagnostic, ouvre l'appli, et retrouve en direct le régime, la vitesse, les températures,
la pression du turbo, le niveau de carburant… ainsi que l'historique de tous tes trajets.

## Ce que fait CarDeck

### 📊 Tableau de bord
Deux tableaux de bord en direct, que tu bascules d'un geste :
- **Standard** : vitesse, régime, température du liquide et de l'huile, niveau de carburant, consommation, tension de la batterie.
- **Sport** : température d'admission, pression du collecteur et de suralimentation (en bar), avance à l'allumage, angle du volant.

### 📟 Données OBD2
Toutes les valeurs que ton véhicule fournit, rangées par catégories, avec l'état de la liaison, l'identification des calculateurs
(VIN, références, logiciels) et l'état de sécurité des airbags.

### 🩺 Diagnostic
Lecture des codes défaut (mémorisés, en attente, permanents) et des données figées, avec une base de codes **en français** :
description, causes possibles et recommandations. Recherche d'un code, effacement des défauts quand le modèle l'autorise.

### 🗺️ Trajets
Chaque trajet est enregistré **automatiquement** dès que le moteur tourne : tracé GPS sur une carte OpenStreetMap, distance, durées,
vitesses, freinages, accélérations et virages, ainsi que **toutes les données du moteur** relevées pendant le parcours.
Tu peux rejouer un trajet, filtrer l'historique par période et **exporter** un trajet complet (résumé, tracé GPX, données CSV).

### 🔧 Codage
Les réglages du véhicule modifiables par diagnostic (à la manière de Bimmercode), avec sauvegarde automatique du codage d'origine,
vérification par relecture et restauration en un geste. Disponible selon les modèles : certaines voitures ne s'y prêtent pas, et l'appli
te dit pourquoi.

### 🚘 Android Auto
Le même écran d'accueil et les mêmes fonctions sur l'écran de ta voiture. Dès que le véhicule roule, tout se grise sauf le tableau de bord.

### 🏠 Un garage pour plusieurs voitures
Chaque voiture a son profil (trajets, diagnostics, boîtier OBD2). Tu choisis un modèle, l'appli y apporte ses fonctions dédiées.
Les cartes de l'écran d'accueil se réorganisent par appui long, et chaque lancement t'accueille avec un message différent.

## Véhicules pris en charge

| Modèle | État |
|---|---|
| Suzuki Swift Sport ZC33S (K14C 1.4 BoosterJet, 140 ch) | ✅ Disponible : lecture seule, données moteur et châssis, huile, carburant, identification |
| Kia Picanto 1.0 TA (G3LA, 69 ch) | ⏳ Arrive bientôt |

D'autres modèles sont prévus.

## Boîtier OBD2

CarDeck fonctionne avec un boîtier **ELM327** en **Bluetooth classique**, **Bluetooth LE** ou **Wi-Fi**. Branche-le sur la prise diagnostic
(sous le volant), mets le contact, puis associe-le à ton véhicule depuis l'appli.

## Respect de ta voiture et de tes données

- 🔒 Sur la Swift Sport, CarDeck est en **lecture seule** : il regarde, il n'écrit rien dans la voiture.
- 📍 Les trajets et les diagnostics restent **sur ton téléphone**.
- 🔄 Les mises à jour sont proposées au lancement et installables depuis **Paramètres → Application**, où tu retrouves aussi le journal des nouveautés.

## Installer

Télécharge l'APK de la dernière version dans les [releases](https://github.com/ToftMalone/CarDeck/releases) et installe-le sur ton téléphone Android (8.0 ou plus récent).
