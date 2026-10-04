package io.github.jqssun.airplay.service

import org.junit.Assert.assertEquals
import org.junit.Test

class StartPolicyTest {
    private val start = "io.github.jqssun.airplay.START_SERVER"

    @Test fun explicitStartRunsAndStaysAlive() =
        assertEquals(StartPolicy.Decision(startServer = true, sticky = true), StartPolicy.decide(start, start, runInBackground = true))

    // android restarts a killed sticky service with a null intent
    @Test fun systemRestartAfterKillStartsServer() =
        assertEquals(StartPolicy.Decision(startServer = true, sticky = true), StartPolicy.decide(null, start, runInBackground = true))

    @Test fun noBackgroundModeMeansNoRestart() {
        assertEquals(StartPolicy.Decision(startServer = true, sticky = false), StartPolicy.decide(start, start, runInBackground = false))
        assertEquals(StartPolicy.Decision(startServer = false, sticky = false), StartPolicy.decide(null, start, runInBackground = false))
    }

    @Test fun unknownActionDoesNothing() =
        assertEquals(StartPolicy.Decision(startServer = false, sticky = false), StartPolicy.decide("other", start, runInBackground = true))

    // stopServer() calls stopSelf(); a stopped service is never restarted regardless of the sticky flag,
    // so the policy only needs to avoid starting on non-start intents
    @Test fun explicitStopIsNotRestarted() =
        assertEquals(false, StartPolicy.decide("other", start, runInBackground = true).startServer)
}
