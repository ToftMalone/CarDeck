# CarDeck 🚗

Application Android de diagnostic OBD2 et de suivi de conduite (Kotlin · Jetpack Compose · Material 3).

## Fonctionnement

- **Profils de véhicule** : chaque voiture du garage est un profil créé à partir d'un *modèle* (pour l'instant :
  Suzuki Swift Sport ZC33S · K14C 1.4 BoosterJet · 140 ch ; Kia Picanto 1.0 TA · G3LA · 69 ch). Trajets, diagnostics et boîtier OBD2 sont rattachés au profil.
  Le véhicule sélectionné dans le garage est utilisé partout.
- **Boîtier OBD2 (ELM327)** : Bluetooth classique, Bluetooth LE ou Wi-Fi.
- **Trajets automatiques** : un service de premier plan se connecte au boîtier du véhicule actif ; dès que le moteur tourne,
  le trajet est enregistré (tracé GPS, distance, vitesses, freinages / accélérations / virages), puis clôturé à l'arrêt du moteur.
  Affichage sur une carte OpenStreetMap.
- **Diagnostic** : lecture des codes défaut (mémorisés, en attente, permanents), données figées, effacement,
  base française des codes génériques.
- **Tableau de bord** : état de la liaison + lecture de tous les PIDs standard OBD2 (service 01) que le véhicule déclare supporter (régime, vitesse, températures, charge, MAP/MAF, corrections carburant, etc.). Les PIDs propres au modèle s'ajoutent dans `data/Templates.kt`.

## Compiler

Prérequis : JDK 17+ et Android SDK (platform 35).

```bash
./gradlew assembleDebug         # APK debug -> app/build/outputs/apk/debug/app-debug.apk (version 0.1)
./gradlew testDebugUnitTest     # tests du protocole ELM327
```

- Les APK **debug** portent toujours la version **0.1**.
- Une **release** est volontairement bloquée : elle exige une version explicite (`-PappVersion=X.Y`) et l'accord du propriétaire du projet.
