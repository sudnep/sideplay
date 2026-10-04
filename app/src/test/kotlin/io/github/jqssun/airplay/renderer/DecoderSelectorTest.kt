package io.github.jqssun.airplay.renderer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderSelectorTest {
    @Test fun hisenseMt5895IsMediatekTvSoc() =
        assertTrue(DecoderSelector.isMediatekTvSoc("mt5895", "mt5895"))

    @Test fun mt96xxTvChipsAreMediatekTvSoc() {
        assertTrue(DecoderSelector.isMediatekTvSoc("mt9612", ""))
        assertTrue(DecoderSelector.isMediatekTvSoc("", "MT9638"))
    }

    @Test fun boardAloneIsEnoughWhenHardwareIsGeneric() =
        assertTrue(DecoderSelector.isMediatekTvSoc("huangshan", "mt5862"))

    @Test fun mediatekPhoneChipsAreNotTvSoc() {
        assertFalse(DecoderSelector.isMediatekTvSoc("mt6765", "mt6765"))
        assertFalse(DecoderSelector.isMediatekTvSoc("mt6893", "mt6893"))
    }

    @Test fun nonMediatekAndEmptyAreNotTvSoc() {
        assertFalse(DecoderSelector.isMediatekTvSoc("qcom", "lahaina"))
        assertFalse(DecoderSelector.isMediatekTvSoc("", ""))
        assertFalse(DecoderSelector.isMediatekTvSoc(null, null))
    }

    @Test fun lookalikeNamesAreNotTvSoc() {
        assertFalse(DecoderSelector.isMediatekTvSoc("mt58", "mt5"))
        assertFalse(DecoderSelector.isMediatekTvSoc("xmt5895", "mt58950x"))
    }
}
