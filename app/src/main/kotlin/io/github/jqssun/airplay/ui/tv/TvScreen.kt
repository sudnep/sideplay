package io.github.jqssun.airplay.ui.tv

/** What the TV shows; derived from service state, never stored. */
sealed interface TvScreen {
    data object FirstRun : TvScreen
    data class Idle(val connecting: Boolean) : TvScreen
    data class Pin(val code: String) : TvScreen
    data object Mirroring : TvScreen
    data object Video : TvScreen
    data object Audio : TvScreen
}

data class TvSession(
    val firstRunDone: Boolean,
    val connectionCount: Int,
    val mirroringActive: Boolean,
    val audioOnly: Boolean,
    val videoPlaybackActive: Boolean,
    val videoSessionPending: Boolean,
    val pinCode: String?,
)

// pin first: pairing can't complete unless the code is visible. no connection check: the iphone
// closes the connection that requested the pin before the user types it (TvPinScreen times out instead)
fun resolveTvScreen(s: TvSession): TvScreen = when {
    s.pinCode != null -> TvScreen.Pin(s.pinCode)
    s.videoPlaybackActive || s.videoSessionPending -> TvScreen.Video
    s.mirroringActive && !s.audioOnly -> TvScreen.Mirroring
    s.audioOnly && s.connectionCount > 0 -> TvScreen.Audio
    !s.firstRunDone -> TvScreen.FirstRun
    else -> TvScreen.Idle(connecting = s.connectionCount > 0)
}
