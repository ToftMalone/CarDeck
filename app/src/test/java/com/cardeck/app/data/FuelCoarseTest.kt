package com.cardeck.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FuelCoarseTest {
    @Test fun positionArrondieAuKilometre() {
        assertEquals("48.86", FuelPrices.coarse(48.858370))
        assertEquals("2.29", FuelPrices.coarse(2.294481))
        assertEquals("-0.58", FuelPrices.coarse(-0.579541))
    }
}
