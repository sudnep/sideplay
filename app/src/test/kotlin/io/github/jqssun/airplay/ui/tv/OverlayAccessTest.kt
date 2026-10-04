package io.github.jqssun.airplay.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAccessTest {
    @Test fun grantedWinsRegardlessOfSettingsScreen() {
        assertEquals(OverlayAccess.GRANTED, resolveOverlayAccess(canDraw = true, settingsScreenExists = false))
        assertEquals(OverlayAccess.GRANTED, resolveOverlayAccess(canDraw = true, settingsScreenExists = true))
    }
    @Test fun notGrantedButScreenExistsIsGrantable() =
        assertEquals(OverlayAccess.GRANTABLE, resolveOverlayAccess(canDraw = false, settingsScreenExists = true))
    @Test fun notGrantedAndNoScreenIsUnavailable() =
        assertEquals(OverlayAccess.UNAVAILABLE, resolveOverlayAccess(canDraw = false, settingsScreenExists = false))
}
