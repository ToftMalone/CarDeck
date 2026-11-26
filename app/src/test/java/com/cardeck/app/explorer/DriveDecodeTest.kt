package com.cardeck.app.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.Random
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

class DriveDecodeTest {
    private fun speed(t: Double) = 50 + 40 * sin(t / 20)
    private fun rpm(t: Double) = 800 + 2500 * (0.5 + 0.5 * sin(t / 7))
    private fun temp(t: Double) = 20 + 70 * (1 - exp(-t / 200))
    private fun f(x: Double) = "%.3f".format(Locale.US, x)
    private fun hex(b: List<Int>) = b.joinToString("") { "%02X".format(it and 0xFF) }

    /**
     * Trajet de 10 minutes, comme l'écrit l'enregistrement en roulant :
     *  - bloc 7E0:2100 (écho « 61 00 ») : octet 0 = vitesse, 1 = bruit, 2-3 = régime × 4 (poids fort d'abord), 4 = compteur, 5 = constante ;
     *  - bus 3B9 : octet 0 = compteur, 1 = température + 40, 2-3 = vitesse × 100 (poids faible d'abord) avec 0,5 s de retard, 4 = bruit.
     */
    private fun drive(): File {
        val dir = Files.createTempDirectory("route").toFile()
        val rnd = Random(1)
        File(dir, "obd.csv").bufferedWriter().use { w ->
            w.write("t,hex,regime,temp_eau,vitesse\n")
            var t = 0.0
            var k = 0
            while (t < 600) {
                // Deux groupes lus à tour de rôle : régime + vitesse, puis température seule.
                if (k % 2 == 0) w.write("${f(t)},41,${f(rpm(t))},,${f(speed(t).roundToInt().toDouble())}\n")
                else w.write("${f(t)},41,,${f(temp(t).roundToInt().toDouble())},\n")
                t += 0.45; k++
            }
        }
        File(dir, "diag.csv").bufferedWriter().use { w ->
            w.write("t,calc,req,hex\n")
            var t = 0.3
            var k = 0
            while (t < 600) {
                val r4 = (rpm(t) * 4).roundToInt()
                w.write("${f(t)},7E0,2100,${hex(listOf(0x61, 0x00, speed(t).roundToInt(), rnd.nextInt(256), r4 shr 8, r4 and 0xFF, k and 0xFF, 0x7D))}\n")
                t += 1.3; k++
            }
        }
        File(dir, "bus.csv").bufferedWriter().use { w ->
            w.write("t,fenetre_s,id,hex\n")
            var start = 0.5
            var k = 0
            while (start < 600) {
                for (j in 0 until 8) {
                    val t = start + (j + 0.5) / 8 * 0.8
                    val v = (speed(t - 0.5) * 100).roundToInt()
                    w.write("${f(start)},0.8,3B9,${hex(listOf(k and 0xFF, temp(t).roundToInt() + 40, v and 0xFF, v shr 8, rnd.nextInt(256), 0, 0, 0))}\n")
                    k++
                }
                start += 2.1
            }
        }
        return dir
    }

    @Test fun pistesSurToutLeTrajet() {
        val dir = drive()
        val inputs = DriveDecode.load(dir)
        assertEquals(setOf("regime", "temp_eau", "vitesse"), inputs.refs.keys)
        assertEquals(setOf("7E0:2100", "bus 3B9"), inputs.sources.map { it.key }.toSet())
        // Écho retiré : 6 octets de données.
        assertEquals(6, inputs.sources.first { it.key == "7E0:2100" }.data.first().size)
        val clues = DriveDecode.analyze(inputs)
        fun find(src: String, ref: String) = clues.firstOrNull { it.source == src && it.ref == ref }

        val v = find("7E0:2100", "vitesse")!!
        assertEquals(DriveDecode.Field(0, 8), v.field)
        assertEquals("vitesse ≈ x", v.formula)

        // Régime sur deux octets : reconnu en 16 bits (pas seulement l'octet fort), avec le bon facteur.
        val r = find("7E0:2100", "regime")!!
        assertEquals(DriveDecode.Field(2, 16, bigEndian = true), r.field)
        assertEquals("regime ≈ x / 4", r.formula)

        // Bus : 16 bits poids faible d'abord, ÷ 100, retard d'environ 0,5 s retrouvé.
        val bv = find("bus 3B9", "vitesse")!!
        assertEquals(DriveDecode.Field(2, 16, bigEndian = false), bv.field)
        assertEquals("vitesse ≈ x / 100", bv.formula)
        // (sur un signal lent comme la vitesse, le décalage n'est connu qu'à quelques dixièmes près)
        assertTrue("décalage ${bv.lag}", bv.lag in 0.2..0.8)

        // Température : retrouvée, mais signalée car elle ne fait que monter, comme le temps.
        val bt = find("bus 3B9", "temp_eau")!!
        assertEquals(DriveDecode.Field(1, 8), bt.field)
        assertEquals("temp_eau ≈ x − 40", bt.formula)
        assertTrue(bt.time)
        assertFalse(v.time)

        // Compteurs, bruit et constantes : aucune piste.
        assertTrue(clues.none { it.source == "7E0:2100" && (4 in it.field.bytes || 1 in it.field.bytes || 5 in it.field.bytes) })
        assertTrue(clues.none { it.source == "bus 3B9" && (0 in it.field.bytes || 4 in it.field.bytes) })
        // Une seule piste par champ et pas d'octets partagés pour une même grandeur.
        for ((_, list) in clues.groupBy { it.source to it.ref }) {
            val bytes = list.flatMap { it.field.bytes }
            assertEquals(bytes.size, bytes.toSet().size)
        }
        assertTrue(DriveDecode.markdown(clues, "x").contains("| `7E0:2100` | octets 2-3 (16 bits) | **regime ≈ x / 4** |"))
        dir.deleteRecursively()
    }

    @Test fun rapportEtSauvegarde() {
        val dir = drive()
        val data = ExplorerData()
        data.drives += "trajet"
        data.driveClues["trajet"] = DriveDecode.analyze(DriveDecode.load(dir))
        data.driveClues["trajet2"] = data.driveClues.getValue("trajet").take(1)
        val report = data.report("Test", "0")
        assertTrue(report.contains("## Pistes de décodage en roulant"))
        assertTrue(report.contains("**vitesse ≈ x / 100**"))
        assertTrue(report.contains("vu sur 2 trajets"))
        // Relu à l'identique.
        val file = File(dir, "d.json")
        data.save(file)
        assertEquals(data.driveClues, ExplorerData.load(file).driveClues)
        dir.deleteRecursively()
    }

    @Test fun trajetVideOuAbime() {
        val dir = Files.createTempDirectory("vide").toFile()
        assertTrue(DriveDecode.analyze(DriveDecode.load(dir)).isEmpty())
        File(dir, "obd.csv").writeText("t,hex,vitesse\nabc,41,\n1.0,41,zz\n")
        File(dir, "diag.csv").writeText("t,calc,req,hex\n1.0,7E0,2100,XYZ\n2.0,7E0\n")
        File(dir, "bus.csv").writeText("t,fenetre_s,id,hex\n")
        assertTrue(DriveDecode.analyze(DriveDecode.load(dir)).isEmpty())
        dir.deleteRecursively()
    }

    @Test fun outils() {
        // Compteur sur l'octet entier, sur le quartet bas, puis une valeur qui monte lentement (pas un compteur).
        val data = (0 until 50).map { intArrayOf(it and 0xFF, (it and 0x0F) or 0x50, it / 10) }
        assertEquals(setOf(0, 1), DriveDecode.counters(data))
        // Valeur sur deux octets qui passe 0x00FF → 0x0100 : retenue reconnue.
        val ramp = (0 until 2000 step 7).map { intArrayOf(it shr 8, it and 0xFF) }
        assertTrue(DriveDecode.carries(ramp, DriveDecode.Field(0, 16)))
        assertFalse(DriveDecode.carries(ramp, DriveDecode.Field(0, 16, bigEndian = false)))
        // Facteurs propres.
        assertEquals("x / 4", DriveDecode.niceFactor(0.2502)!!.second)
        assertEquals("x × 100 / 255", DriveDecode.niceFactor(0.3925)!!.second)
        assertEquals("−x", DriveDecode.niceFactor(-1.004)!!.second)
        assertEquals("3 × x", DriveDecode.niceFactor(2.99)!!.second)
        assertNull(DriveDecode.niceFactor(0.7))
        // Interpolation : entre deux lectures proches, sinon la plus proche à moins de 0,5 s.
        val s = DriveDecode.Series(doubleArrayOf(0.0, 1.0, 10.0), doubleArrayOf(0.0, 10.0, 100.0))
        assertEquals(5.0, DriveDecode.at(s, 0.5), 1e-9)
        assertEquals(10.0, DriveDecode.at(s, 1.3), 1e-9)
        assertTrue(DriveDecode.at(s, 5.0).isNaN())
        assertTrue(DriveDecode.at(s, 11.0).isNaN())
        assertNotNull(DriveDecode.Field(0, 16).raw(intArrayOf(1, 2)))
        assertEquals(-2.0, DriveDecode.Field(0, 8, signed = true).raw(intArrayOf(0xFE))!!, 0.0)
        assertEquals(0x0201.toDouble(), DriveDecode.Field(0, 16, bigEndian = false).raw(intArrayOf(1, 2))!!, 0.0)
        assertNull(DriveDecode.Field(1, 16).raw(intArrayOf(1, 2)))
    }
}
