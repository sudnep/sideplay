package io.github.jqssun.airplay.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvAppContentTest {
    @get:Rule val rule = createComposeRule()

    @Composable private fun tag(t: String) = Box(Modifier.testTag(t)) { Text(t) }

    private fun show(
        screen: TvScreen, route: TvRoute = TvRoute.HOME, inPip: Boolean = false,
        mirror: @Composable () -> Unit = { tag("mirror_surface") },
    ) = rule.setContent {
        TvAppContent(
            screen = screen, route = route, inPip = inPip,
            mirrorSurface = mirror,
            playbackSurface = { tag("playback_surface") },
            firstRun = { tag("s_firstrun") },
            idle = { connecting -> tag(if (connecting) "s_idle_connecting" else "s_idle") },
            pin = { code -> tag("s_pin_$code") },
            mirroring = { tag("s_mirroring") },
            video = { tag("s_video") },
            audio = { tag("s_audio") },
            settings = { tag("s_settings") },
            troubleshooting = { tag("s_troubleshooting") },
        )
    }

    @Test fun idleHome() { show(TvScreen.Idle(false)); rule.onNodeWithTag("s_idle").assertIsDisplayed() }
    @Test fun idleConnecting() { show(TvScreen.Idle(true)); rule.onNodeWithTag("s_idle_connecting").assertIsDisplayed() }
    @Test fun idleSettingsRoute() { show(TvScreen.Idle(false), TvRoute.SETTINGS); rule.onNodeWithTag("s_settings").assertIsDisplayed() }
    @Test fun idleTroubleshootingRoute() { show(TvScreen.Idle(false), TvRoute.TROUBLESHOOTING); rule.onNodeWithTag("s_troubleshooting").assertIsDisplayed() }
    @Test fun pin() { show(TvScreen.Pin("1234")); rule.onNodeWithTag("s_pin_1234").assertIsDisplayed() }
    @Test fun firstRun() { show(TvScreen.FirstRun); rule.onNodeWithTag("s_firstrun").assertIsDisplayed() }
    @Test fun audio() { show(TvScreen.Audio); rule.onNodeWithTag("s_audio").assertIsDisplayed() }

    @Test fun sessionOverridesSettingsRoute() {
        show(TvScreen.Mirroring, TvRoute.SETTINGS)
        rule.onNodeWithTag("s_mirroring").assertIsDisplayed()
        rule.onNodeWithTag("s_settings").assertDoesNotExist()
    }

    @Test fun mirrorSurfacePresentExceptDuringVideo() {
        show(TvScreen.Idle(false))
        rule.onNodeWithTag("mirror_surface").assertExists()
    }

    // portrait iphones produce a narrow surface; it must sit in the middle of the tv
    @Test fun mirrorSurfaceIsCentred() {
        show(TvScreen.Mirroring, mirror = { Box(Modifier.size(100.dp).testTag("mirror_surface")) })
        val root = rule.onRoot().getBoundsInRoot()
        val surface = rule.onNodeWithTag("mirror_surface").getBoundsInRoot()
        assertEquals(((root.left + root.right) / 2).value, ((surface.left + surface.right) / 2).value, 1f)
    }

    @Test fun pipShowsOnlyMirrorSurface() {
        show(TvScreen.Mirroring, inPip = true)
        rule.onNodeWithTag("mirror_surface").assertExists()
        rule.onNodeWithTag("s_mirroring").assertDoesNotExist()
    }

    @Test fun pipDuringVideoShowsOnlyPlaybackSurface() {
        show(TvScreen.Video, inPip = true)
        rule.onNodeWithTag("playback_surface").assertExists()
        rule.onNodeWithTag("s_video").assertDoesNotExist()
    }

    @Test fun videoReplacesMirrorSurface() {
        show(TvScreen.Video)
        rule.onNodeWithTag("s_video").assertIsDisplayed()
        rule.onNodeWithTag("mirror_surface").assertDoesNotExist()
    }
}
