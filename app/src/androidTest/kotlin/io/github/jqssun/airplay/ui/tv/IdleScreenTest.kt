package io.github.jqssun.airplay.ui.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IdleScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(access: OverlayAccess = OverlayAccess.GRANTED, connecting: Boolean = false, ip: String? = "192.168.1.248",
                     onSettings: () -> Unit = {}, onAllow: () -> Unit = {}, error: Boolean = false, onRetry: () -> Unit = {}) = rule.setContent {
        TvIdleScreen("Living Room TV", ip, connecting, access, onSettings, onAllow, receiverError = error, onRetry = onRetry)
    }

    @Test fun receiverErrorShowsRetryInsteadOfReady() {
        var retries = 0
        show(error = true, onRetry = { retries++ })
        rule.onNodeWithTag("idle_title").assertTextContains("isn't running", substring = true)
        rule.onNodeWithTag("retry_receiver").assertIsDisplayed().performClick()
        assertEquals(1, retries)
    }

    @Test fun noRetryWhenRunning() {
        show()
        rule.onNodeWithTag("retry_receiver").assertDoesNotExist()
    }

    @Test fun showsNameAndAddress() {
        show()
        rule.onNodeWithTag("idle_title").assertTextContains("Living Room TV is ready")
        rule.onNodeWithTag("idle_ip").assertTextContains("192.168.1.248", substring = true)
    }

    @Test fun connectingChangesTitle() {
        show(connecting = true)
        rule.onNodeWithTag("idle_title").assertTextContains("Connecting to Living Room TV", substring = true)
    }

    @Test fun settingsButtonHasInitialFocus() {
        show()
        rule.onNodeWithTag("open_settings").assertIsFocused()
    }

    @Test fun grantableShowsAllowButton() {
        var allowed = false
        show(access = OverlayAccess.GRANTABLE, onAllow = { allowed = true })
        rule.onNodeWithTag("allow_auto_open").assertIsDisplayed().performClick()
        assertTrue(allowed)
    }

    @Test fun unavailableShowsKeepOpenNote() {
        show(access = OverlayAccess.UNAVAILABLE)
        rule.onNodeWithTag("keep_open_note").assertIsDisplayed()
        rule.onNodeWithTag("allow_auto_open").assertDoesNotExist()
    }

    @Test fun grantedShowsNeither() {
        show(access = OverlayAccess.GRANTED)
        rule.onNodeWithTag("allow_auto_open").assertDoesNotExist()
        rule.onNodeWithTag("keep_open_note").assertDoesNotExist()
    }

    @Test fun missingAddressHidesIt() {
        show(ip = null)
        rule.onNodeWithTag("idle_ip").assertDoesNotExist()
    }

    @Test fun pinScreenShowsCode() {
        rule.setContent { TvPinScreen("4821", onDismiss = {}) }
        rule.onNodeWithTag("pin_code").assertTextContains("4821")
    }

    @Test fun guidedShowsAppSettingsButtonAndSteps() {
        var opened = false
        show(access = OverlayAccess.GUIDED, onAllow = { opened = true })
        rule.onNodeWithTag("open_app_settings").assertIsDisplayed().performClick()
        assertTrue(opened)
        rule.onNodeWithTag("overlay_steps").assertTextContains("Special app access", substring = true)
    }

    // ios keeps an idle control connection open for minutes after a session ends
    @Test fun connectingLabelRevertsToReadyAfterAWhile() {
        rule.mainClock.autoAdvance = false
        show(connecting = true)
        rule.onNodeWithTag("idle_title").assertTextContains("Connecting to Living Room TV", substring = true)
        rule.mainClock.advanceTimeBy(CONNECTING_LABEL_MS + 1_000)
        rule.onNodeWithTag("idle_title").assertTextContains("Living Room TV is ready")
    }
}
