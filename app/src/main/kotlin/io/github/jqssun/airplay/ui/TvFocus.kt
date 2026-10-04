package io.github.jqssun.airplay.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

// focus can only land once the target is composed; retry across frames
suspend fun FocusRequester.requestFocusUntilLanded(
    attempts: Int = 10,
    isFocused: (() -> Boolean)? = null,
): Boolean {
    repeat(attempts) {
        val requested = runCatching { requestFocus() }.isSuccess
        if (isFocused?.invoke() ?: requested) return true
        withFrameNanos { }
        if (isFocused?.invoke() == true) return true
    }
    return false
}
