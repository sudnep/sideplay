package io.github.jqssun.airplay.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PinScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun backDismissesPin() {
        var dismissed = 0
        rule.setContent { TvPinScreen("4821", onDismiss = { dismissed++ }) }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, dismissed)
    }
}
