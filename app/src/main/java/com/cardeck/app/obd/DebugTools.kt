package com.cardeck.app.obd

/**
 * Outils réservés à la version de développement. Le code principal ne contient que ce point d'accroche, vide :
 * le simulateur de boîtier vit dans le dossier « debug » du projet et ne s'y branche qu'en version de débogage
 * (déclaré par le manifeste de debug). La version publiée n'en contient pas une ligne.
 */
object DebugTools {
    /** Fabrique d'un boîtier OBD2 simulé ; null dans la version publiée. */
    @Volatile var simulator: (() -> ObdTransport)? = null

    val simulatorAvailable: Boolean get() = simulator != null
}
