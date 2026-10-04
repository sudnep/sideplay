package io.github.jqssun.airplay.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTeardownTest {
    @Test fun musicStoppedLeavesAudioMode() =
        assertTrue(AudioTeardown.leavesAudioMode(mirroring = false, videoPlaying = false))

    @Test fun mirroringKeepsItsState() =
        assertFalse(AudioTeardown.leavesAudioMode(mirroring = true, videoPlaying = false))

    // the sender's audio stream can end while an airplay video still plays; the remote keys must keep working
    @Test fun videoPlaybackKeepsItsState() =
        assertFalse(AudioTeardown.leavesAudioMode(mirroring = false, videoPlaying = true))
}
