package com.cardeck.app.data

/**
 * Lecture du calculateur d'airbags de la Swift ZC33S (UDS 793, lecture seule).
 *  - 22 1000 : 10 valeurs entre 0x15 et 0x1B, probablement les résistances des circuits d'allumage
 *    (airbags, prétensionneurs) en 0,1 Ω ; plage normale 2,1 à 2,7 Ω. Hypothèse : affichée « estimé ».
 *  - 22 1F00 : octets 10-11 de la réponse = secondes depuis la mise du contact (confirmé) ;
 *    octet 3 = tension / 12,8 (probable). Octets comptés écho « 62 1F 00 » compris.
 */
data class AirbagReport(val t: Long, val circuits: List<Double>, val secondsOn: Int?, val voltage: Double?) {
    companion object {
        /** Plage de résistance considérée comme normale (Ω). */
        val NORMAL = 1.8..3.2

        fun from(r: Map<String, List<Int>>, t: Long): AirbagReport {
            val circuits = r["221000"].orEmpty().filter { it in 0x0A..0x40 }.map { it / 10.0 }
            val st = r["221F00"].orEmpty()
            val secs = if (st.size >= 9) st[7] * 256 + st[8] else null
            val volt = st.getOrNull(0)?.let { it / 12.8 }
            return AirbagReport(t, circuits, secs, volt)
        }
    }
}
