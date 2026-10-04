package io.github.jqssun.airplay.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.ServerName
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val state = TvSettingsState("Living Room TV", requirePin = false, bootAutoStart = true, h265Enabled = true)

    private fun show(
        onRename: (String) -> Unit = {}, onPin: (Boolean) -> Unit = {}, onTs: () -> Unit = {}, onBack: () -> Unit = {},
    ) = rule.setContent {
        TvSettingsScreen(state, onRename, onPin, {}, {}, onTs, onBack)
    }

    @Test fun dpadDownReachesEveryRowInOrder() {
        show()
        rule.onNodeWithTag("setting_name").assertIsFocused()
        listOf("setting_pin", "setting_boot", "setting_h265", "setting_troubleshooting").forEach { tag ->
            rule.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            // focused but squashed or clipped off-screen is unusable on a tv
            rule.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        }
    }

    @Test fun toggleCallsBackWithNewValue() {
        var pin: Boolean? = null
        show(onPin = { pin = it })
        rule.onNodeWithTag("setting_pin").performClick()
        assertEquals(true, pin)
    }

    @Test fun troubleshootingOpens() {
        var opened = false
        show(onTs = { opened = true })
        rule.onNodeWithTag("setting_troubleshooting").performClick()
        assertEquals(true, opened)
    }

    @Test fun backLeaves() {
        var back = 0
        show(onBack = { back++ })
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, back)
    }

    @Test fun renameRejectsBlankAndTruncatesLong() {
        var renamed: String? = null
        show(onRename = { renamed = it })
        rule.onNodeWithTag("setting_name").performClick()
        rule.onNodeWithTag("rename_field").performTextReplacement("   ")
        rule.onNodeWithTag("rename_save").assertIsNotEnabled()
        rule.onNodeWithTag("rename_field").performTextReplacement("  " + "B".repeat(70) + " ")
        rule.onNodeWithTag("rename_save").performClick()
        assertEquals("B".repeat(ServerName.MAX_BYTES), renamed)
    }

    @Test fun troubleshootingShowsAddressDecoderAndDisclaimer() {
        rule.setContent {
            TvTroubleshootingScreen(
                TvTroubleshootingState("192.168.1.248", 7000, "0.1.0", "H.264 (OMX.MTK.VIDEO.DECODER.AVC)", listOf("line one")),
                onBack = {},
            )
        }
        rule.onNodeWithTag("ts_address").assertTextContains("192.168.1.248, port 7000", substring = true)
        rule.onNodeWithTag("ts_decoder").assertTextContains("OMX.MTK.VIDEO.DECODER.AVC", substring = true)
        // lazy items below the fold aren't composed until scrolled to
        rule.onNodeWithTag("ts_list").performScrollToNode(hasTestTag("ts_disclaimer"))
        rule.onNodeWithTag("ts_disclaimer").assertTextContains("Not affiliated with Apple Inc.", substring = true)
    }
}
