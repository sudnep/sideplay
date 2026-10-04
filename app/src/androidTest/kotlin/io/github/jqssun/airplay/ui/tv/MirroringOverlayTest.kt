package io.github.jqssun.airplay.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MirroringOverlayTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun toastShownThenHidden() {
        rule.mainClock.autoAdvance = false
        rule.setContent { TvMirroringOverlay(onDisconnect = {}) }
        rule.onNodeWithTag("connected_toast").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(5_000) // 3 s toast + fade-out
        rule.onNodeWithTag("connected_toast").assertDoesNotExist()
    }

    @Test fun backAsksThenConfirmDisconnects() {
        var disconnects = 0
        rule.setContent { TvMirroringOverlay(onDisconnect = { disconnects++ }) }
        pressBack()
        rule.onNodeWithTag("confirm_disconnect").assertIsDisplayed().performClick()
        assertEquals(1, disconnects)
        rule.onNodeWithTag("confirm_disconnect").assertDoesNotExist()
    }

    @Test fun dialogFocusesCancel() {
        rule.setContent { TvMirroringOverlay(onDisconnect = {}) }
        pressBack()
        rule.onNodeWithTag("cancel_disconnect").assertIsFocused()
    }

    @Test fun cancelKeepsSession() {
        var disconnects = 0
        rule.setContent { TvMirroringOverlay(onDisconnect = { disconnects++ }) }
        pressBack()
        rule.onNodeWithTag("cancel_disconnect").performClick()
        assertEquals(0, disconnects)
        rule.onNodeWithTag("confirm_disconnect").assertDoesNotExist()
    }

    @Test fun backOnDialogDismissesIt() {
        var disconnects = 0
        rule.setContent { TvMirroringOverlay(onDisconnect = { disconnects++ }) }
        pressBack()
        rule.onNodeWithTag("confirm_disconnect").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithTag("confirm_disconnect").assertDoesNotExist()
        assertEquals(0, disconnects)
    }
}
