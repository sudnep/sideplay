package io.github.jqssun.airplay.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class VideoScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val playing = TvVideoState(title = "Clip", playing = true, buffering = false, pending = false, positionMs = 65_000, durationMs = 600_000)

    private class Calls { var toggles = 0; var seeks = mutableListOf<Long>(); var stops = 0 }

    private fun show(state: TvVideoState, calls: Calls) = rule.setContent {
        TvVideoScreen(state, surface = {}, onTogglePlay = { calls.toggles++ }, onPlay = {}, onPause = {},
            onSeekBy = { calls.seeks += it }, onStop = { calls.stops++ })
    }

    @Test fun centerTogglesPlayback() {
        val c = Calls(); show(playing, c)
        rule.onNodeWithTag("video_root").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, c.toggles)
    }

    @Test fun leftRightSeekByStep() {
        val c = Calls(); show(playing, c)
        rule.onNodeWithTag("video_root").performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight) }
        assertEquals(listOf(-SEEK_STEP_MS, SEEK_STEP_MS), c.seeks)
    }

    @Test fun backStops() {
        val c = Calls(); show(playing, c)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, c.stops)
    }

    // paused: controls never auto-hide, so the test can't race the 4 s timer under the auto-advancing test clock
    @Test fun controlsShowTitleAndTime() {
        show(playing.copy(playing = false), Calls())
        rule.onNodeWithTag("video_controls").assertIsDisplayed()
        rule.onNodeWithTag("video_time").assertTextContains("1:05 / 10:00")
    }

    @Test fun pendingShowsLoading() {
        show(playing.copy(pending = true, playing = false), Calls())
        rule.onNodeWithTag("video_loading").assertIsDisplayed()
    }
}
