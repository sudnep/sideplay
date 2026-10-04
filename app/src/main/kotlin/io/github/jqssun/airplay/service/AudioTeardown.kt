package io.github.jqssun.airplay.service

object AudioTeardown {
    // ios keeps its control connection open for minutes after ending audio, so audio mode is left on teardown;
    // mirroring and airplay video own the screen and the media session, so their state is kept
    fun leavesAudioMode(mirroring: Boolean, videoPlaying: Boolean): Boolean = !mirroring && !videoPlaying
}
