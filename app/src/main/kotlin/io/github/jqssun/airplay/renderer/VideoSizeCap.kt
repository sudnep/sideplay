package io.github.jqssun.airplay.renderer

import android.media.MediaCodecList
import android.os.Build

/**
 * Decoders declare a landscape max (e.g. 4096x2304) but android lets either orientation through, so a
 * portrait 1440x2560 stream passes the capability check and then fails in the hardware decoder.
 */
object VideoSizeCap {
    data class Size(val width: Int, val height: Int)

    fun limit(decoders: List<Size>): Size? =
        if (decoders.isEmpty()) null else Size(decoders.minOf { it.width }, decoders.minOf { it.height })

    fun shouldRetryWithoutVp9(decodingFailed: Boolean, failedMime: String?, alreadyRetried: Boolean): Boolean =
        decodingFailed && !alreadyRetried && failedMime == VP9

    fun decoderLimits(): List<Size> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && (Build.VERSION.SDK_INT < 29 || it.isHardwareAccelerated) }
            .flatMap { info ->
                info.supportedTypes.filter { it.startsWith("video/") && it in VIDEO_TYPES }.mapNotNull { type ->
                    runCatching {
                        val caps = info.getCapabilitiesForType(type).videoCapabilities
                        val widest = caps.supportedWidths.upper
                        Size(widest, caps.getSupportedHeightsFor(widest).upper)
                    }.getOrNull()
                }
            }
    }.getOrDefault(emptyList())

    const val VP9 = "video/x-vnd.on2.vp9"
    private val VIDEO_TYPES = setOf("video/avc", "video/hevc", VP9)
}
