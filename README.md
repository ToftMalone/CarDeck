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
La liste reste affichée même boîtier déconnecté (les valeurs apparaissent dès la connexion).

**Lecture en continu** : tant que le contact est mis, CarDeck lit les données en permanence, écran ouvert ou non, selon leur rythme.

| Rythme | Données |
|---|---|
| ⚡ **À chaque tour** | Vitesse, régime, charge moteur, avance à l'allumage, pression du collecteur, débit d'air, papillon, pression atmosphérique |
| 🔁 **1 tour sur 3** | Charge absolue ; couples demandé, réel et de frottement ; températures du liquide, d'admission et de surface du collecteur ; papillon relatif, B, C et commandé ; corrections carburant court et long terme (bancs 1 et 2) ; richesse commandée ; pressions de carburant et de rampe ; calage de l'injection ; consommation ; carburant par cylindre ; sondes lambda ; purge du canister |
| 🐢 **1 tour sur 6** | Niveau de carburant (en % et en litres estimés), taux d'éthanol, température extérieure, kilométrage, durée moteur tournant, distance voyant allumé, température d'huile |

Seules ces données sont lues, et seulement si ta voiture les fournit. La consommation instantanée, la pression du collecteur en bar
et la consommation et le coût d'un trajet sont **calculés** à partir de ces mesures.

**Explorateur** (dans Données OBD2) : un seul bouton, qui s'adapte à la voiture au moment où tu le lances, pour **toutes les voitures** et
**en lecture seule** (seuls les services de lecture partent, jamais d'effacement, d'écriture, de pilotage ni de déverrouillage) :
- 🅿️ **à l'arrêt, balayage complet** : recherche de tous les calculateurs en CAN 11 bits (de 0x600 à 0x7FF, quelle que soit leur adresse de réponse),
  en CAN 29 bits et sur la ligne K ; identification (références, noms de système), blocs de données constructeur KWP `21xx`, identifiants UDS `22xxxx`
  par tranches de 10 000, lecture mémoire, carte du bus CAN, codes défaut avant / après. Interrompu, il reprend là où il s'était arrêté ;
- 🚗 **en roulant, enregistrement** : mesures OBD, blocs constructeur (et nouveaux blocs découverts en route) et trafic du bus, horodatés ensemble.
  Il s'arrête quand tu le demandes ou au contact coupé.
- Résultat : un **rapport** (calculateurs, blocs vivants, réponses en clair, identifiants qui refusent, **pistes de décodage par corrélation**,
  **cartes moteur**) et toutes les données brutes, à partager en un fichier.

### 🩺 Diagnostic
Lecture des codes défaut (mémorisés, en attente, permanents) et des données figées, avec une base de codes **en français** :
description, causes possibles et recommandations. Recherche d'un code, effacement des défauts quand le modèle l'autorise.

**Diagnostic complet** : le boîtier est d'abord remis à zéro, puis CarDeck lit les codes OBD2 de tous les calculateurs, interroge aussi
le calculateur moteur directement, recherche tous les calculateurs du réseau CAN (moteur, ABS…) et lit les codes défaut de chacun
(UDS et KWP2000). Le scan prend 1 à 2 minutes, mais ne laisse rien de côté.

**Toutes les voitures** : le scan s'adapte au protocole détecté. En CAN 11 bits (la plupart des voitures depuis 2008), il fait
le diagnostic complet ci-dessus. En CAN 29 bits, en K-Line ou en J1850 (beaucoup de voitures plus anciennes), il lit les codes
OBD2 et les données figées de tous les calculateurs qui répondent. Un même code vu par plusieurs calculateurs apparaît pour chacun.

**Journal du scan** : chaque diagnostic enregistre toutes les questions posées à la voiture et ses réponses ; il se partage depuis
l'écran Diagnostic, pour comprendre un défaut qui ne serait pas trouvé.

### 🗺️ Trajets
Ouvre CarDeck (ou laisse-la se réveiller par Bluetooth, voir plus bas) : le trajet démarre **tout seul** dès que le moteur tourne.
Un moteur calé ne le coupe pas. Il se termine quand le contact est coupé, quand le moteur est arrêté et la voiture immobile pendant 1 minute,
ou quand la liaison avec le boîtier est perdue pendant 1 minute. CarDeck se met alors en veille, sans notification.
Si tu ouvres l'appli sans rouler, elle se met aussi en veille après 5 minutes sans trajet, dès que tu la quittes.

Pour chaque trajet : tracé GPS sur une carte OpenStreetMap, distance, durées, vitesses, **consommation moyenne**,
**coût du trajet** (au prix du carburant du jour près de l'arrivée, d'après prix-carburants.gouv.fr, carburant au choix pour chaque véhicule),
ainsi que les données du moteur relevées pendant le parcours.
Tu peux rejouer un trajet, filtrer l'historique par période et **exporter** un trajet complet (résumé, tracé GPX, données CSV).
Dans l'historique, un appui long sur un trajet lance la **multi-sélection** : tu peux **fusionner** plusieurs trajets en un seul (mis bout à bout, tracé et données repris) ou les **supprimer**.

### ⏰ Réveil automatique
Pour chaque véhicule, tu choisis si CarDeck sort de veille à la connexion Bluetooth à l'autoradio de ta voiture, ou jamais
(tu ouvres alors l'appli toi-même). Pour que le réveil fonctionne, Android doit autoriser CarDeck à ignorer l'optimisation de batterie :
l'appli te le demande quand tu actives l'option.

### 💾 Sauvegarde
Toutes tes données (véhicules, trajets, diagnostics, réglages) peuvent être sauvegardées dans un fichier **chiffré** que seule CarDeck
sait relire, puis restaurées depuis **Paramètres → Sauvegarde**.

### 🏠 Un garage pour plusieurs voitures
Chaque voiture a son profil (trajets, diagnostics, boîtier OBD2). Pour le créer, tu choisis ton véhicule **en trois listes** : la **marque**, le **modèle**, puis la **génération et la motorisation**.
Par exemple Suzuki › Swift › Sport ZC33S › K14C 1.4 BoosterJet. Un champ de recherche permet d'aller plus vite (« Clio V 1.0 TCe », « Golf GTI »…).
Les cartes de l'écran d'accueil se réorganisent par appui long, et chaque lancement t'accueille avec un message différent.

## Véhicules pris en charge

- **Une liste de véhicules courants en France**, de nombreuses marques (Peugeot, Renault, Citroën, Volkswagen, Toyota, Suzuki, BMW, Audi…) avec leurs modèles, générations et motorisations essence, diesel, hybrides et GPL.
- Ces véhicules utilisent les **données OBD2 standard** (régime, vitesse, températures, pressions, diagnostic des défauts, trajets et coût). Le protocole est détecté automatiquement à la connexion.
- Ton véhicule n'est pas dans la liste ? Choisis **Autre véhicule** : toute voiture essence depuis 2001 ou diesel depuis 2004 a une prise OBD2.
- Certains véhicules ont en plus un **profil dédié**, avec des données propres au modèle :

| Modèle | Profil dédié |
|---|---|
| Suzuki Swift Sport ZC33S (K14C 1.4 BoosterJet, 140 ch) | ✅ Lecture seule, huile, carburant, kilométrage |

La liste est un point de départ : elle n'a pas été testée sur chaque voiture, seule la Swift Sport l'a été.

## Boîtier OBD2

CarDeck fonctionne avec un boîtier **ELM327** en **Bluetooth classique**, **Bluetooth LE** ou **Wi-Fi**. Branche-le sur la prise diagnostic
(sous le volant), mets le contact, puis associe-le à ton véhicule depuis l'appli.

## Respect de ta voiture et de tes données

- 🔒 Sur la Swift Sport, CarDeck est en **lecture seule** : il regarde, il n'écrit rien dans la voiture.
- 📍 Les trajets et les diagnostics restent **sur ton téléphone**. Seuls en sortent : la demande du prix du carburant (avec la position
  d'arrivée du trajet), les cartes OpenStreetMap et la recherche de mises à jour.
- 🔐 Tes sauvegardes sont **chiffrées** : seule CarDeck sait les relire.
- 🔄 Les mises à jour sont proposées au lancement et installables depuis **Paramètres → Application**, où tu retrouves aussi le journal des nouveautés.

## Installer

Télécharge l'APK de la dernière version dans les [releases](https://github.com/ToftMalone/CarDeck/releases) et installe-le sur ton téléphone Android (8.0 ou plus récent).

## Soutenir CarDeck ☕

CarDeck est gratuite. Si elle t'est utile, tu peux soutenir son développement sur [Ko-fi](https://ko-fi.com/toftmalone).
