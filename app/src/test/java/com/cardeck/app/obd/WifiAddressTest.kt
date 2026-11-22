package com.cardeck.app.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiAddressTest {
    @Test fun adressesLocales() {
        listOf("192.168.0.10", "10.0.0.1", "172.16.0.5", "172.31.255.1", "169.254.1.1").forEach { assertTrue(it, WifiAddress.isLocal(it)) }
    }

    @Test fun adressesRefusees() {
        listOf(
            "pirate.example.com", "8.8.8.8", "172.32.0.1", "192.169.0.1", "localhost", "127.0.0.1", "0.0.0.0",
            "192.168.0", "192.168.0.10.5", "192.168.0.256", "192.168.0.-1", "", "::1", "0x0A.0.0.1", "1922.168.0.1",
        ).forEach { assertFalse(it, WifiAddress.isLocal(it)) }
    }

    @Test fun portEtAdresse() {
        assertEquals("192.168.0.10" to 35000, WifiAddress.parse("192.168.0.10:35000"))
        assertEquals("192.168.0.10" to 35000, WifiAddress.parse("192.168.0.10"))
        assertNull(WifiAddress.parse("192.168.0.10:0"))
        assertNull(WifiAddress.parse("192.168.0.10:70000"))
        assertNull(WifiAddress.parse("pirate.example.com:35000"))
    }
}
