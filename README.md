# CarDeck 🚗

**CarDeck est l'application Android qui lit ce que ta voiture a à dire.**
Branche un petit boîtier OBD2 sur la prise diagnostic, ouvre l'appli, et retrouve en direct le régime, la vitesse, les températures,
la pression du turbo, le niveau de carburant… ainsi que l'historique de tous tes trajets, avec leur consommation et leur coût.

## Ce que fait CarDeck

### 📊 Tableau de bord
Deux tableaux de bord en direct, que tu bascules d'un geste :
- **Standard** : vitesse, régime, température du liquide et de l'huile, niveau de carburant, consommation.
- **Sport** : température d'admission, pression du collecteur et de suralimentation (en bar), avance à l'allumage.

### 📟 Données OBD2
Les valeurs du moteur que ton véhicule fournit, rangées par catégories, avec l'état de la liaison avec le boîtier.

**Lecture en continu** : tant que le contact est mis, CarDeck lit les données en permanence, écran ouvert ou non, selon leur rythme.

| Rythme | Données |
|---|---|
| ⚡ **À chaque tour** | Vitesse, régime, charge moteur, avance à l'allumage, pression du collecteur, débit d'air, papillon, pression atmosphérique |
| 🔁 **1 tour sur 3** | Charge absolue ; couples demandé, réel et de frottement ; températures du liquide, d'admission et de surface du collecteur ; papillon relatif, B, C et commandé ; corrections carburant court et long terme (bancs 1 et 2) ; richesse commandée ; pressions de carburant et de rampe ; calage de l'injection ; consommation ; carburant par cylindre ; sondes lambda ; purge du canister |
| 🐢 **1 tour sur 6** | Niveau de carburant (en % et en litres estimés), taux d'éthanol, température extérieure, kilométrage, durée moteur tournant, distance voyant allumé, température d'huile |

Seules ces données sont lues, et seulement si ta voiture les fournit. La consommation instantanée, la pression du collecteur en bar
et la consommation et le coût d'un trajet sont **calculés** à partir de ces mesures.

### 🩺 Diagnostic
Lecture des codes défaut (mémorisés, en attente, permanents) et des données figées, avec une base de codes **en français** :
description, causes possibles et recommandations. Recherche d'un code, effacement des défauts quand le modèle l'autorise.

**Diagnostic complet** : après les codes OBD2, CarDeck recherche tous les calculateurs du réseau CAN (moteur, ABS…)
et lit les codes défaut de chacun. Le scan prend plus de temps, mais ne laisse rien de côté.

### 🗺️ Trajets
Le trajet démarre **tout seul** dès que le moteur tourne. Une fois terminé (contact coupé, ou moteur arrêté et voiture immobile pendant 1 minute), CarDeck se met en veille, sans notification. Elle en sort quand tu l'ouvres, ou automatiquement à la connexion Bluetooth à ta voiture, au choix pour chaque véhicule. Pour chaque trajet : tracé GPS sur une carte OpenStreetMap, distance, durées,
vitesses, **consommation moyenne**, **coût du trajet** (au prix du carburant du jour près de l'arrivée, d'après prix-carburants.gouv.fr),
ainsi que les données du moteur relevées pendant le parcours.
Tu peux rejouer un trajet, filtrer l'historique par période et **exporter** un trajet complet (résumé, tracé GPX, données CSV).
Dans l'historique, un appui long sur un trajet lance la **multi-sélection** : tu peux **fusionner** plusieurs trajets en un seul (mis bout à bout, tracé et données repris) ou les **supprimer**.

### 🔧 Codage
Les réglages du véhicule modifiables par diagnostic (à la manière de Bimmercode), avec sauvegarde automatique du codage d'origine,
vérification par relecture et restauration en un geste. Disponible selon les modèles : certaines voitures ne s'y prêtent pas, et l'appli
te dit pourquoi.

### ⏰ Réveil automatique
Pour chaque véhicule, tu choisis si CarDeck sort de veille à la connexion Bluetooth à l'autoradio de ta voiture, ou jamais
(tu ouvres alors l'appli toi-même).

### 💾 Sauvegarde
Toutes tes données (véhicules, trajets, diagnostics, réglages) peuvent être sauvegardées dans un fichier **chiffré** que seule CarDeck
sait relire, puis restaurées depuis **Paramètres → Sauvegarde**.

### 🏠 Un garage pour plusieurs voitures
Chaque voiture a son profil (trajets, diagnostics, boîtier OBD2). Tu choisis un modèle, l'appli y apporte ses fonctions dédiées.
Les cartes de l'écran d'accueil se réorganisent par appui long, et chaque lancement t'accueille avec un message différent.

## Véhicules pris en charge

| Modèle | État |
|---|---|
| Suzuki Swift Sport ZC33S (K14C 1.4 BoosterJet, 140 ch) | ✅ Disponible : lecture seule, données moteur, huile, carburant, kilométrage |

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
