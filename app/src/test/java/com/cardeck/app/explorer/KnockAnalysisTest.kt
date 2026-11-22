package com.cardeck.app.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnockAnalysisTest {
    /**
     * Trajet simulé de 5 min : croisière (charge 30 %, 2 000 tr/min, avance 25°) et une reprise en charge toutes les 30 s
     * pendant 6 s (charge 90 %, 2 500 tr/min, avance 10°). Pendant la 2e moitié de chaque reprise, le moteur cliquette :
     * l'octet 5 du bloc passe à 4 (retard) et l'avance tombe à 6°.
     * Autres octets : 0 = régime (mesure continue), 1 = constante, 2 = simple indicateur de charge (30 ou 90).
     */
    private fun simulate(): KnockAnalysis.Result {
        val obd = mutableListOf<KnockAnalysis.Obd>()
        val blocks = mutableListOf<KnockAnalysis.Block>()
        var t = 0.0
        while (t < 300) {
            val inPull = (t % 30) < 6
            val knocking = inPull && (t % 30) >= 3
            val rpm = if (inPull) 2500.0 else 2000.0 + (t % 7) * 10
            val load = if (inPull) 90.0 else 30.0
            val adv = when { knocking -> 6.0; inPull -> 10.0; else -> 25.0 }
            obd += KnockAnalysis.Obd(t, rpm, load, adv, null)
            val data = intArrayOf((rpm / 32).toInt(), 0x55, load.toInt(), 0, 0, if (knocking) 4 else 0, 0, 0)
            blocks += KnockAnalysis.Block(t + 0.1, "7E0:2100", data)
            t += 0.3
        }
        return KnockAnalysis.analyze(obd, blocks)
    }

    @Test fun trouveLeRetardDuCliquetis() {
        val r = simulate()
        assertEquals(10, r.pulls)
        val best = r.candidates.first()
        assertEquals("7E0:2100", best.key)
        assertEquals(5, best.index)
        assertEquals(0, best.rest)
        assertEquals(4.0, best.advanceDrop!!, 0.01)
        // L'indicateur de charge peut apparaître, mais derrière ; la constante et le régime, jamais.
        assertTrue(r.candidates.none { it.index == 1 || it.index == 0 })
        r.candidates.find { it.index == 2 }?.let { assertTrue(it.score < best.score) }
    }

    @Test fun rienSansCharge() {
        val obd = (0 until 500).map { KnockAnalysis.Obd(it * 0.3, 2000.0, 30.0, 25.0, null) }
        val blocks = (0 until 500).map { KnockAnalysis.Block(it * 0.3, "7E0:2100", intArrayOf(0, it % 3)) }
        val r = KnockAnalysis.analyze(obd, blocks)
        assertEquals(0, r.pulls)
        assertTrue(r.candidates.isEmpty())
        assertTrue(KnockAnalysis.report(r, "Swift", "0.4", 2.5, "x").contains("Moins de 3 reprises"))
    }

    @Test fun enChargeSansChargeCalculee() {
        assertTrue(KnockAnalysis.isLoaded(KnockAnalysis.Obd(0.0, 3000.0, null, null, 150.0)))
        assertTrue(!KnockAnalysis.isLoaded(KnockAnalysis.Obd(0.0, 900.0, 95.0, null, null)))
    }
}
