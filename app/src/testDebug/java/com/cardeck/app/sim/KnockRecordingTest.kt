package com.cardeck.app.sim

import com.cardeck.app.explorer.Explorer
import com.cardeck.app.explorer.ExplorerData
import com.cardeck.app.explorer.ExplorerListener
import com.cardeck.app.obd.Elm327
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Recherche du cliquetis sur la voiture simulée : le bloc principal du moteur doit être lu à chaque tour. */
class KnockRecordingTest {
    private object NoUi : ExplorerListener {
        override fun step(index: Int, total: Int, name: String) {}
        override fun detail(text: String?, progress: Float?) {}
        override fun line(text: String) {}
    }

    /** Lance la recherche pendant [ms] à l'instant [atS] du cycle de conduite ; renvoie le nombre de lectures par bloc. */
    private fun record(atS: Long, ms: Long): Map<String, Int> = runBlocking {
        val start = 1_000_000L
        var now = start
        val car = SimCar { now }
        now = start + atS * 1000
        val e = Elm327(SimTransport(car, latency = false))
        e.init("6"); e.probe()
        e.readOnly = true
        val dir = Files.createTempDirectory("knock").toFile()
        try {
            val ex = Explorer(e, ExplorerData(), dir, NoUi)
            ex.prepare()
            withTimeoutOrNull(ms) { ex.knock("test", "Swift") }
            val csv = File(dir, "cliquetis").listFiles()!!.single().resolve("blocs.csv").readLines().drop(1)
            csv.groupingBy { it.split(',')[2] }.eachCount()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun blocPrincipalAChaqueTour() {
        // Croisière (charge 36 %) : le bloc 21 00 à chaque tour, plus un autre bloc à tour de rôle.
        val cruise = record(atS = 40, ms = 3000)
        val total = cruise.values.sum()
        val main = cruise["2100"] ?: 0
        assertTrue("lectures : $cruise", total > 10 && main * 2 >= total - 1)
        assertTrue("bloc figé lu de loin en loin : $cruise", (cruise["2120"] ?: 0) in 1..main / 4)
        // Pleine charge (92 %) : le bloc 21 00 seul.
        val pull = record(atS = 15, ms = 3000)
        assertTrue("lectures en charge : $pull", (pull["2100"] ?: 0) > 10 && pull.keys == setOf("2100"))
    }
}
