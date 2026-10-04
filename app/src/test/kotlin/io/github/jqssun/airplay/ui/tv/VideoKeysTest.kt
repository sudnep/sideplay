package io.github.jqssun.airplay.ui.tv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoKeysTest {
    @Test fun centerEnterAndPlayPauseToggle() {
        listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            .forEach { assertEquals(VideoKeyAction.TOGGLE_PLAY, videoKeyAction(it)) }
    }
    @Test fun explicitPlayAndPause() {
        assertEquals(VideoKeyAction.PLAY, videoKeyAction(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(VideoKeyAction.PAUSE, videoKeyAction(KeyEvent.KEYCODE_MEDIA_PAUSE))
    }
    @Test fun leftAndRewindSeekBack() {
        assertEquals(VideoKeyAction.SEEK_BACK, videoKeyAction(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(VideoKeyAction.SEEK_BACK, videoKeyAction(KeyEvent.KEYCODE_MEDIA_REWIND))
    }
    @Test fun rightAndFastForwardSeekForward() {
        assertEquals(VideoKeyAction.SEEK_FORWARD, videoKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(VideoKeyAction.SEEK_FORWARD, videoKeyAction(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
    }
    @Test fun upDownOnlyShowControls() {
        assertEquals(VideoKeyAction.SHOW_CONTROLS, videoKeyAction(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(VideoKeyAction.SHOW_CONTROLS, videoKeyAction(KeyEvent.KEYCODE_DPAD_DOWN))
    }
    @Test fun backAndOtherKeysAreNotHandled() {
        assertNull(videoKeyAction(KeyEvent.KEYCODE_BACK))
        assertNull(videoKeyAction(KeyEvent.KEYCODE_VOLUME_UP))
    }
}
