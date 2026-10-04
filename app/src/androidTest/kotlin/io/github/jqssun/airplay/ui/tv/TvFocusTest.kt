package io.github.jqssun.airplay.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvFocusTest {
    @get:Rule val rule = createComposeRule()

    // a ring in the theme's primary colour vanishes on a primary-filled button
    @Test fun focusedFilledButtonShowsWhiteRing() {
        lateinit var focus: FocusRequester
        rule.setContent {
            TvTheme {
                focus = remember { FocusRequester() }
                // plain box: the button's merged semantics node reports its inner (clickable) bounds, excluding the ring
                Box(Modifier.testTag("b")) {
                    Button(onClick = {}, modifier = Modifier.focusRequester(focus).tvFocus()) { Text("Done") }
                }
            }
        }
        rule.runOnIdle { focus.requestFocus() }
        val px = rule.onNodeWithTag("b").captureToImage().toPixelMap()
        val edge = px[px.width / 2, 1]
        assertTrue("top edge pixel $edge", edge.red > 0.9f && edge.green > 0.9f && edge.blue > 0.9f)
    }
}
