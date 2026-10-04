package io.github.jqssun.airplay.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectLaunchTest {
    @Test fun visibleUiNeedsNothing() {
        assertEquals(ConnectLaunch.NONE, decideConnectLaunch(uiVisible = true, canDrawOverlays = false))
        assertEquals(ConnectLaunch.NONE, decideConnectLaunch(uiVisible = true, canDrawOverlays = true))
    }
    @Test fun overlayAllowedLaunchesDirectly() =
        assertEquals(ConnectLaunch.LAUNCH, decideConnectLaunch(uiVisible = false, canDrawOverlays = true))
    // android 10+ blocks the background start; ask the user instead
    @Test fun otherwisePrompt() =
        assertEquals(ConnectLaunch.PROMPT, decideConnectLaunch(uiVisible = false, canDrawOverlays = false))
}
