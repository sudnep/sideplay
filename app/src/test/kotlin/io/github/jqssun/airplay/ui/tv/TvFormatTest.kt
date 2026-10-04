package io.github.jqssun.airplay.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class TvFormatTest {
    @Test fun underAMinute() = assertEquals("0:05", formatPlaybackTime(5_400))
    @Test fun minutesAndSeconds() = assertEquals("12:03", formatPlaybackTime(723_000))
    @Test fun hours() = assertEquals("1:02:03", formatPlaybackTime(3_723_000))
    @Test fun negativeOrUnknownIsZero() = assertEquals("0:00", formatPlaybackTime(-1))
}
