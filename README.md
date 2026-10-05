# CarDeck 🚗

Application Android de diagnostic OBD2 et de suivi de conduite (Kotlin · Jetpack Compose · Material 3).

Écrans : bienvenue, appairage du boîtier OBD2 (Bluetooth / Wi-Fi), ajout de véhicule, accueil, tableau de bord temps réel
(+ widgets personnalisables et mode HUD), diagnostic (scan, codes défaut, recherche, effacement), historique et détail des trajets,
paramètres (garage, boîtier, thème clair/sombre/système, 4 couleurs dynamiques).

> État actuel : les données moteur, le scan, l'appairage et les trajets sont **simulés** (aucune communication ELM327 réelle pour l'instant).

## Compiler

Prérequis : JDK 17+ et Android SDK (platform 35).

```bash
./gradlew assembleDebug        # APK debug -> app/build/outputs/apk/debug/app-debug.apk (version 0.1)
```

- Les APK **debug** portent toujours la version **0.1**.
- Une **release** est volontairement bloquée : elle exige une version explicite (`-PappVersion=X.Y`) et l'accord du propriétaire du projet.
