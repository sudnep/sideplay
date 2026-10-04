package io.github.jqssun.airplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupBroadcastTest {
    @Test fun bootStartsWhenEnabled() {
        assertTrue(StartupBroadcast.shouldStart("android.intent.action.BOOT_COMPLETED", autoStart = true))
        assertTrue(StartupBroadcast.shouldStart("android.intent.action.QUICKBOOT_POWERON", autoStart = true))
    }

    // an app update force-stops the process and android never restarts a force-stopped service
    @Test fun appUpdateStartsWhenEnabled() =
        assertTrue(StartupBroadcast.shouldStart("android.intent.action.MY_PACKAGE_REPLACED", autoStart = true))

    @Test fun nothingWhenDisabled() {
        assertFalse(StartupBroadcast.shouldStart("android.intent.action.BOOT_COMPLETED", autoStart = false))
        assertFalse(StartupBroadcast.shouldStart("android.intent.action.MY_PACKAGE_REPLACED", autoStart = false))
    }

    @Test fun otherBroadcastsIgnored() =
        assertFalse(StartupBroadcast.shouldStart("android.intent.action.SCREEN_ON", autoStart = true))
}
