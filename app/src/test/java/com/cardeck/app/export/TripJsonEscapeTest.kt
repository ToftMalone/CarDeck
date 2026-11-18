package com.cardeck.app.export

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TripJsonEscapeTest {
    @Test fun caracteresDeControle() {
        val s = "Ma \"Swift\"\t\\ 2\r\n\u0001fin"
        val json = TripExport.json(s)
        assertEquals(s, JSONObject("{\"v\":$json}").getString("v"))
    }
}
