package io.github.jqssun.airplay

/** Broadcasts that should bring the receiver up when "start when the TV turns on" is enabled. */
object StartupBroadcast {
    private val actions = setOf(
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.QUICKBOOT_POWERON",
        // an app update force-stops the process; sticky restart never fires after a force-stop
        "android.intent.action.MY_PACKAGE_REPLACED",
    )

    fun shouldStart(action: String?, autoStart: Boolean): Boolean = autoStart && action in actions
}
