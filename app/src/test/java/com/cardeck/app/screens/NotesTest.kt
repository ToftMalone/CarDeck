package com.cardeck.app.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class NotesTest {
    private val md = "## Changes\n\n- English line\n\n<!-- lang:fr\n## Changements\n\n- Ligne française\n-->\n<!-- lang:de\n- Deutsche Zeile\n-->\n"

    @Test
    fun traductionDeLaLangue() {
        assertEquals("## Changements\n\n- Ligne française", localizedNotes(md, "fr"))
        assertEquals("- Deutsche Zeile", localizedNotes(md, "de"))
    }

    @Test
    fun anglaisSansTraduction() {
        assertEquals("## Changes\n\n- English line", localizedNotes(md, "es"))
        // Anciennes notes, sans traductions : telles quelles.
        assertEquals("## Changements\n\n- Plus de dock", localizedNotes("## Changements\n\n- Plus de dock", "en"))
    }
}
