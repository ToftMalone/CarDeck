package com.cardeck.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsDistanceTest {
    @Test fun derivePendantUnArret() {
        // Arrêté (0,2 m/s), précision 8 m : la position saute de 5 m, ce n'est pas un déplacement.
        assertFalse(GpsDistance.counts(5f, 0.2f, 8f))
        assertFalse(GpsDistance.counts(9f, 0f, 4f))
    }

    @Test fun deplacementReel() {
        assertTrue(GpsDistance.counts(14f, 13.9f, 5f)) // 50 km/h
        assertTrue(GpsDistance.counts(3f, null, null)) // vitesse inconnue : comportement d'origine
        // Manœuvre lente : comptée une fois sortie de la dérive possible.
        assertTrue(GpsDistance.counts(12f, 0.5f, 6f))
        assertFalse(GpsDistance.counts(1.5f, 20f, 3f))
    }
}
