package io.github.jqssun.airplay.ui.tv

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvThemeTest {
    @get:Rule val rule = createComposeRule()

    // the Hisense runs in light mode; tv screens draw on a dark background regardless
    @Test fun textColorsAreLightEvenWhenSystemIsLight() {
        var onSurface = Color.Unspecified
        rule.setContent { TvTheme { onSurface = MaterialTheme.colorScheme.onSurface } }
        rule.waitForIdle()
        assertTrue("onSurface luminance ${onSurface.luminance()}", onSurface.luminance() > 0.5f)
    }
}
