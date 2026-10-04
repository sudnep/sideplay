package io.github.jqssun.airplay.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PinScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    // a cancelled pairing leaves the code behind in the service; never strand the tv on it
    @Test fun pinDismissesItselfAfterTimeout() {
        rule.mainClock.autoAdvance = false
        var dismissed = 0
        rule.setContent { TvPinScreen("4821", onDismiss = { dismissed++ }) }
        rule.mainClock.advanceTimeBy(PIN_TIMEOUT_MS - 1_000)
        assertEquals(0, dismissed)
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals(1, dismissed)
    }

    @Test fun backDismissesPin() {
        var dismissed = 0
        rule.setContent { TvPinScreen("4821", onDismiss = { dismissed++ }) }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, dismissed)
    }
}
