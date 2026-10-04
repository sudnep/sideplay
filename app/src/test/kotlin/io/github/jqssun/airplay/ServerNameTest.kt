package io.github.jqssun.airplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerNameTest {
    @Test fun usesDeviceNameWhenSet() =
        assertEquals("Living Room TV", ServerName.choose("Living Room TV", "Hisense", "SmartTV 4K FFM"))

    @Test fun trimsDeviceName() =
        assertEquals("Living Room TV", ServerName.choose("  Living Room TV \n", "Hisense", "X"))

    @Test fun blankDeviceNameFallsBackToManufacturerAndModel() =
        assertEquals("Hisense SmartTV 4K FFM", ServerName.choose("   ", "Hisense", "SmartTV 4K FFM"))

    @Test fun modelAlreadyContainingManufacturerIsNotDuplicated() =
        assertEquals("Hisense 55U8", ServerName.choose(null, "hisense", "Hisense 55U8"))

    @Test fun nothingKnownGivesFallback() =
        assertEquals(ServerName.FALLBACK, ServerName.choose(null, " ", null))

    @Test fun longAsciiNameIsCappedAtMaxBytes() {
        val name = ServerName.choose("A".repeat(80), null, null)
        assertEquals(ServerName.MAX_BYTES, name.toByteArray(Charsets.UTF_8).size)
    }

    @Test fun truncationNeverSplitsMultiByteCharacters() {
        // each "📺" is 4 UTF-8 bytes (a surrogate pair in Kotlin); 20 of them = 80 bytes
        val name = ServerName.choose("📺".repeat(20), null, null)
        val bytes = name.toByteArray(Charsets.UTF_8)
        assertTrue(bytes.size <= ServerName.MAX_BYTES)
        assertEquals("📺".repeat(12), name) // 48 bytes; a 13th would be 52
    }

    @Test fun truncationDropsTrailingSpace() =
        assertEquals("A".repeat(49), ServerName.choose("A".repeat(49) + " " + "B".repeat(10), null, null))

    @Test fun suffixAppended() =
        assertEquals("Living Room TV (AirPlay)", ServerName.withAirPlaySuffix("Living Room TV"))

    @Test fun suffixedNameFitsByTrimmingTheBase() {
        val name = ServerName.withAirPlaySuffix("A".repeat(50))
        assertEquals(ServerName.MAX_BYTES, name.toByteArray(Charsets.UTF_8).size)
        assertTrue(name.endsWith(ServerName.SUFFIX))
    }

    @Test fun suffixTrimNeverSplitsMultiByteCharacters() {
        val name = ServerName.withAirPlaySuffix("📺".repeat(20))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= ServerName.MAX_BYTES)
        assertEquals("📺".repeat(10) + ServerName.SUFFIX, name) // 40 + 10 bytes
    }

    @Test fun suffixNotDoubled() =
        assertEquals("Den (AirPlay)", ServerName.withAirPlaySuffix("Den (AirPlay)"))
}
