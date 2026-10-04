package io.github.jqssun.airplay.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAccessTest {
    @Test fun grantedWinsRegardless() =
        assertEquals(OverlayAccess.GRANTED, resolveOverlayAccess(canDraw = true, overlayScreen = false, appDetailsScreen = false))
    @Test fun overlayScreenIsGrantable() =
        assertEquals(OverlayAccess.GRANTABLE, resolveOverlayAccess(canDraw = false, overlayScreen = true, appDetailsScreen = true))
    // android 10 tv settings: no overlay deep link, but the app page opens and special app access is one level up
    @Test fun onlyAppDetailsIsGuided() =
        assertEquals(OverlayAccess.GUIDED, resolveOverlayAccess(canDraw = false, overlayScreen = false, appDetailsScreen = true))
    @Test fun nothingReachableIsUnavailable() =
        assertEquals(OverlayAccess.UNAVAILABLE, resolveOverlayAccess(canDraw = false, overlayScreen = false, appDetailsScreen = false))
}
