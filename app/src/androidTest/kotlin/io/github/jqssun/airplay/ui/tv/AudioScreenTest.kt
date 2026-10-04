package io.github.jqssun.airplay.ui.tv

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class AudioScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsTrack() {
        rule.setContent { TvAudioScreen("Song", "Artist", "Album", null) }
        rule.onNodeWithTag("audio_title").assertTextContains("Song")
        rule.onNodeWithTag("audio_artist").assertTextContains("Artist · Album")
    }

    @Test fun blankTitleUsesFallback() {
        rule.setContent { TvAudioScreen("", "", "", null) }
        rule.onNodeWithTag("audio_title").assertTextContains("Playing audio from your device")
        rule.onNodeWithTag("audio_artist").assertDoesNotExist()
    }
}
