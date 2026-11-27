package com.cardeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class PerfTimerTest {
    /**
     * Mesures toutes les [stepMs] d'une vitesse [speed] (km/h, fonction du temps en s), arrondies au km/h comme le PID 0D,
     * à partir de [fromS] ; renvoie le dernier état.
     */
    private fun run(timer: PerfTimer, fromS: Double, toS: Double, stepMs: Long = 90, offsetMs: Long = 0, speed: (Double) -> Double): PerfTimer.State {
        var t = (fromS * 1000).toLong() + offsetMs
        var st = timer.state
        while (t <= toS * 1000) {
            st = timer.feed(t, speed(t / 1000.0).coerceAtLeast(0.0).roundToInt().toDouble())
            t += stepMs
        }
        return st
    }

    /** Arrêt jusqu'à 2 s, puis accélération constante de [a] km/h par seconde. */
    private fun launch(a: Double): (Double) -> Double = { s -> if (s < 2.0) 0.0 else (s - 2.0) * a }

    @Test fun zeroACent() {
        val timer = PerfTimer(PerfKind.ZeroTo100)
        // 0 → 100 en 8,0 s exactement (12,5 km/h par seconde).
        val st = run(timer, 0.0, 12.0, speed = launch(12.5))
        val done = st.last as PerfTimer.Outcome.Done
        assertEquals(8000.0, done.timeMs.toDouble(), 120.0)
        assertTrue(done.hz in 10.0..12.0)
        assertEquals(PerfTimer.Phase.Waiting, st.phase) // à 50 km/h : il faut s'arrêter pour un nouvel essai
    }

    @Test fun precisionSelonLeMomentDesMesures() {
        // Quel que soit le décalage des mesures par rapport au départ réel, l'erreur reste petite.
        for (offset in listOf(0L, 23L, 45L, 70L)) {
            val st = run(PerfTimer(PerfKind.ZeroTo100), 0.0, 12.0, offsetMs = offset, speed = launch(12.5))
            assertEquals("décalage $offset", 8000.0, (st.last as PerfTimer.Outcome.Done).timeMs.toDouble(), 120.0)
        }
    }

    @Test fun reprise80a120() {
        val timer = PerfTimer(PerfKind.From80To120)
        // 60 km/h stables, puis +8 km/h par seconde à partir de 3 s : 80 atteint à 5,5 s, 120 à 10,5 s → 5,0 s.
        val st = run(timer, 0.0, 14.0) { s -> if (s < 3) 60.0 else 60 + (s - 3) * 8 }
        assertEquals(5000.0, (st.last as PerfTimer.Outcome.Done).timeMs.toDouble(), 100.0)
    }

    @Test fun repriseArmeeSeulementSous80() {
        val timer = PerfTimer(PerfKind.From80To120)
        // Déjà à 90 : pas armé, rien ne démarre.
        val st = run(timer, 0.0, 6.0) { s -> 90 + s * 5 }
        assertEquals(PerfTimer.Phase.Waiting, st.phase)
        assertNull(st.last)
    }

    @Test fun quatreCentsMetres() {
        val timer = PerfTimer(PerfKind.Quarter400)
        // Accélération constante a (m/s²) : 400 m en √(800 / a) s. a = 4 → 14,14 s, vitesse 56,6 m/s = 203,6 km/h.
        val a = 4.0 * 3.6 // km/h par seconde
        val st = run(timer, 0.0, 20.0, speed = launch(a))
        val done = st.last as PerfTimer.Outcome.Done
        assertEquals(14142.0, done.timeMs.toDouble(), 150.0)
        assertEquals(203.6, done.trapKmh!!, 2.0)
    }

    @Test fun abandons() {
        // Levée de pied avant 100 : abandon.
        val t1 = PerfTimer(PerfKind.ZeroTo100)
        val st1 = run(t1, 0.0, 10.0) { s -> if (s < 2) 0.0 else if (s < 6) (s - 2) * 15 else 60 - (s - 6) * 10 }
        assertEquals("Accélération interrompue", (st1.last as PerfTimer.Outcome.Failed).reason)
        // Faux départ (quelques km/h puis arrêt) : rien d'affiché, de nouveau prêt.
        val t2 = PerfTimer(PerfKind.ZeroTo100)
        val st2 = run(t2, 0.0, 6.0) { s -> if (s in 2.0..3.0) 4.0 else 0.0 }
        assertNull(st2.last)
        assertEquals(PerfTimer.Phase.Ready, st2.phase)
        // Mesures qui s'arrêtent pendant l'essai.
        val t3 = PerfTimer(PerfKind.ZeroTo100)
        run(t3, 0.0, 4.0, speed = launch(12.5))
        val st3 = t3.feed(7000, 70.0)
        assertEquals("Mesures de vitesse trop espacées", (st3.last as PerfTimer.Outcome.Failed).reason)
        // Mesure en double ignorée.
        val t4 = PerfTimer(PerfKind.ZeroTo100)
        t4.feed(1000, 0.0)
        assertEquals(t4.state, t4.feed(1000, 50.0))
    }

    @Test fun deuxEssaisDeSuite() {
        val timer = PerfTimer(PerfKind.ZeroTo100)
        run(timer, 0.0, 12.0, speed = launch(12.5))
        // Arrêt, puis deuxième essai plus rapide : 0 → 100 en 6,25 s (16 km/h par seconde).
        val st = run(timer, 12.09, 30.0) { s -> if (s < 20) maxOf(0.0, 50 - (s - 12) * 10) else (s - 22).coerceAtLeast(0.0) * 16 }
        assertEquals(6250.0, (st.last as PerfTimer.Outcome.Done).timeMs.toDouble(), 120.0)
    }

    @Test fun reprise50a90() {
        val timer = PerfTimer(PerfKind.From50To90)
        // 30 km/h, puis +10 km/h par seconde à partir de 2 s : 50 à 4 s, 90 à 8 s → 4,0 s.
        val st = run(timer, 0.0, 10.0) { s -> if (s < 2) 30.0 else 30 + (s - 2) * 10 }
        assertEquals(4000.0, (st.last as PerfTimer.Outcome.Done).timeMs.toDouble(), 100.0)
    }

    @Test fun freinage100a0() {
        val timer = PerfTimer(PerfKind.Brake100To0)
        // 120 km/h, puis freinage à 30 km/h par seconde (8,33 m/s²) à partir de 3 s : sous 100 à 3,67 s, arrêt à 7 s.
        val st = run(timer, 0.0, 9.0) { s -> if (s < 3) 120.0 else 120 - (s - 3) * 30 }
        val done = st.last as PerfTimer.Outcome.Done
        assertEquals(3333.0, done.timeMs.toDouble(), 100.0)
        // Distance d'arrêt depuis 100 km/h : v² / 2a = 27,78² / 16,67 = 46,3 m.
        assertEquals(46.3, done.distanceM!!, 1.5)
        assertEquals(PerfTimer.Phase.Waiting, st.phase) // à l'arrêt : il faut repasser au-dessus de 100
    }

    @Test fun freinageArmeAuDessusDe100() {
        val timer = PerfTimer(PerfKind.Brake100To0)
        // Jamais au-dessus de 100 : rien ne démarre.
        val st = run(timer, 0.0, 8.0) { s -> 90 - s * 10 }
        assertNull(st.last)
        // Freinage relâché puis réaccélération : abandon.
        val t2 = PerfTimer(PerfKind.Brake100To0)
        val st2 = run(t2, 0.0, 8.0) { s -> if (s < 2) 110.0 else if (s < 4) 110 - (s - 2) * 20 else 70 + (s - 4) * 10 }
        assertEquals("Freinage interrompu", (st2.last as PerfTimer.Outcome.Failed).reason)
    }

    @Test fun quatreCentsMetresDonneLaDistance() {
        val st = run(PerfTimer(PerfKind.Quarter400), 0.0, 20.0, speed = launch(4.0 * 3.6))
        assertEquals(400.0, (st.last as PerfTimer.Outcome.Done).distanceM!!, 0.001)
    }
}
