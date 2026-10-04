package io.github.jqssun.airplay.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistentPinTest {
    @Test fun storedPinIsReused() =
        assertEquals(PersistentPin.Result(4821, generated = false), PersistentPin.resolve(4821) { 1111 })

    @Test fun missingPinIsGeneratedOnce() =
        assertEquals(PersistentPin.Result(1111, generated = true), PersistentPin.resolve(-1) { 1111 })

    @Test fun outOfRangeStoredPinIsReplaced() =
        assertEquals(PersistentPin.Result(2222, generated = true), PersistentPin.resolve(123456) { 2222 })

    @Test fun zeroIsAValidPin() =
        assertEquals(PersistentPin.Result(0, generated = false), PersistentPin.resolve(0) { 9 })

    @Test fun formatKeepsLeadingZeros() {
        assertEquals("0042", PersistentPin.format(42))
        assertEquals("0000", PersistentPin.format(0))
    }
}
