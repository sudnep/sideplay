package io.github.jqssun.airplay.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class TvFormatTest {
    @Test fun underAMinute() = assertEquals("0:05", formatPlaybackTime(5_400))
    @Test fun minutesAndSeconds() = assertEquals("12:03", formatPlaybackTime(723_000))
    @Test fun hours() = assertEquals("1:02:03", formatPlaybackTime(3_723_000))
    @Test fun negativeOrUnknownIsZero() = assertEquals("0:00", formatPlaybackTime(-1))

    @Test fun decoderSummaryBoth() = assertEquals(
        "H.264: OMX.MTK.VIDEO.DECODER.AVC · H.265: OMX.MTK.VIDEO.DECODER.HEVC",
        decoderSummary("OMX.MTK.VIDEO.DECODER.AVC", "OMX.MTK.VIDEO.DECODER.HEVC", h265Enabled = true))
    @Test fun decoderSummaryNoHevc() =
        assertEquals("H.264: A · H.265: not supported", decoderSummary("A", null, h265Enabled = true))
    @Test fun decoderSummaryHevcTurnedOff() =
        assertEquals("H.264: A · H.265: off", decoderSummary("A", "H", h265Enabled = false))
    @Test fun decoderSummaryNothingFound() = assertEquals("", decoderSummary(null, null, h265Enabled = true))
}
