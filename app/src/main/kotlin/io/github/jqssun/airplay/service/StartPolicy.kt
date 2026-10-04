package io.github.jqssun.airplay.service

/** What onStartCommand does. A null action is Android restarting a sticky service it killed. */
object StartPolicy {
    data class Decision(val startServer: Boolean, val sticky: Boolean)

    fun decide(action: String?, startAction: String, runInBackground: Boolean): Decision = when (action) {
        startAction -> Decision(startServer = true, sticky = runInBackground)
        null -> Decision(startServer = runInBackground, sticky = runInBackground)
        else -> Decision(startServer = false, sticky = false)
    }

    // android 12+ refuses foreground promotion from some background starts (ForegroundServiceStartNotAllowedException,
    // an IllegalStateException); report it instead of crashing into a restart loop
    fun promoteSafely(promote: () -> Unit): Boolean = try {
        promote()
        true
    } catch (e: IllegalStateException) {
        false
    }
}
