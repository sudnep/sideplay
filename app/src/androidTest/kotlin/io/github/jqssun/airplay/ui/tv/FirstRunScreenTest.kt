package io.github.jqssun.airplay.ui.tv

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FirstRunScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun doneHasFocusAndKeepsSuggestedNameAndBootOn() {
        var result: Pair<String, Boolean>? = null
        rule.setContent { TvFirstRunScreen("Living Room TV") { n, b -> result = n to b } }
        rule.onNodeWithTag("firstrun_done").assertIsFocused().performClick()
        assertEquals("Living Room TV" to true, result)
    }

    @Test fun editedNameAndBootOffArePassed() {
        var result: Pair<String, Boolean>? = null
        rule.setContent { TvFirstRunScreen("Living Room TV") { n, b -> result = n to b } }
        rule.onNodeWithTag("firstrun_name").performTextReplacement("  Bedroom  ")
        rule.onNodeWithTag("firstrun_boot").performClick()
        rule.onNodeWithTag("firstrun_done").performClick()
        assertEquals("Bedroom" to false, result)
    }

    @Test fun blankNameDisablesDone() {
        rule.setContent { TvFirstRunScreen("Living Room TV") { _, _ -> } }
        rule.onNodeWithTag("firstrun_name").performTextReplacement(" ")
        rule.onNodeWithTag("firstrun_done").assertIsNotEnabled()
    }
}
