package io.github.jqssun.airplay.ui.tv

fun formatPlaybackTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** What the TV can decode, for Troubleshooting; empty when no hardware decoder was found. */
fun decoderSummary(avc: String?, hevc: String?, h265Enabled: Boolean): String {
    if (avc == null && hevc == null) return ""
    val h265 = when {
        !h265Enabled -> "off"
        hevc == null -> "not supported"
        else -> hevc
    }
    return "H.264: ${avc ?: "not supported"} · H.265: $h265"
}
