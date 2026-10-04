package io.github.jqssun.airplay.ui.tv

import android.view.KeyEvent

const val SEEK_STEP_MS = 10_000L

enum class VideoKeyAction { TOGGLE_PLAY, PLAY, PAUSE, SEEK_BACK, SEEK_FORWARD, SHOW_CONTROLS }

// back is left to BackHandler (stops playback)
fun videoKeyAction(keyCode: Int): VideoKeyAction? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> VideoKeyAction.TOGGLE_PLAY
    KeyEvent.KEYCODE_MEDIA_PLAY -> VideoKeyAction.PLAY
    KeyEvent.KEYCODE_MEDIA_PAUSE -> VideoKeyAction.PAUSE
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> VideoKeyAction.SEEK_BACK
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> VideoKeyAction.SEEK_FORWARD
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> VideoKeyAction.SHOW_CONTROLS
    else -> null
}
