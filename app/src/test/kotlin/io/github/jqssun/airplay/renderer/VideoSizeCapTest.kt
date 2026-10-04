package io.github.jqssun.airplay.renderer

import io.github.jqssun.airplay.renderer.VideoSizeCap.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSizeCapTest {
    // hisense mt5895: every decoder declares 4096x2304
    private val hisense = listOf(Size(4096, 2304), Size(4096, 2304), Size(4096, 2304))

    @Test fun capIsLandscapeShaped() = assertEquals(Size(4096, 2304), VideoSizeCap.limit(hisense))

    private fun fits(cap: Size, w: Int, h: Int) = w <= cap.width && h <= cap.height

    @Test fun portrait1440pIsExcluded() = assertFalse(fits(VideoSizeCap.limit(hisense)!!, 1440, 2560))
    @Test fun portrait1080pIsAllowed() = assertTrue(fits(VideoSizeCap.limit(hisense)!!, 1080, 1920))
    @Test fun landscape4kIsAllowed() = assertTrue(fits(VideoSizeCap.limit(hisense)!!, 3840, 2160))

    @Test fun smallestDecoderWins() =
        assertEquals(Size(1920, 1088), VideoSizeCap.limit(listOf(Size(4096, 2304), Size(1920, 1088))))

    @Test fun noDecoderInfoMeansNoCap() = assertNull(VideoSizeCap.limit(emptyList()))

    @Test fun retryOnlyForVp9DecodeFailureOnce() {
        assertTrue(VideoSizeCap.shouldRetryWithoutVp9(true, "video/x-vnd.on2.vp9", alreadyRetried = false))
        assertFalse(VideoSizeCap.shouldRetryWithoutVp9(true, "video/x-vnd.on2.vp9", alreadyRetried = true))
        assertFalse(VideoSizeCap.shouldRetryWithoutVp9(true, "video/avc", alreadyRetried = false))
        assertFalse(VideoSizeCap.shouldRetryWithoutVp9(false, "video/x-vnd.on2.vp9", alreadyRetried = false))
    }
}
