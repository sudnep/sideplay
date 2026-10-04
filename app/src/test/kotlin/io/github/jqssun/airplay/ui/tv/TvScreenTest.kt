package io.github.jqssun.airplay.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class TvScreenTest {
    private val idle = TvSession(
        firstRunDone = true, connectionCount = 0, mirroringActive = false, audioOnly = false,
        videoPlaybackActive = false, videoSessionPending = false, pinCode = null,
    )

    @Test fun nothingHappeningIsIdle() = assertEquals(TvScreen.Idle(connecting = false), resolveTvScreen(idle))

    @Test fun connectionBeforeKindIsKnownIsIdleConnecting() =
        assertEquals(TvScreen.Idle(connecting = true), resolveTvScreen(idle.copy(connectionCount = 1)))

    @Test fun firstRunShownWhenNotDoneAndNoSession() =
        assertEquals(TvScreen.FirstRun, resolveTvScreen(idle.copy(firstRunDone = false)))

    @Test fun sessionPreemptsFirstRun() =
        assertEquals(TvScreen.Mirroring, resolveTvScreen(idle.copy(firstRunDone = false, connectionCount = 1, mirroringActive = true)))

    @Test fun mirroring() =
        assertEquals(TvScreen.Mirroring, resolveTvScreen(idle.copy(connectionCount = 1, mirroringActive = true)))

    @Test fun audioOnlySession() =
        assertEquals(TvScreen.Audio, resolveTvScreen(idle.copy(connectionCount = 1, audioOnly = true)))

    @Test fun audioOnlyWithoutConnectionIsIdle() =
        assertEquals(TvScreen.Idle(connecting = false), resolveTvScreen(idle.copy(audioOnly = true)))

    @Test fun videoPendingShowsVideo() =
        assertEquals(TvScreen.Video, resolveTvScreen(idle.copy(connectionCount = 1, videoSessionPending = true)))

    @Test fun videoWinsOverMirroring() =
        assertEquals(TvScreen.Video, resolveTvScreen(idle.copy(connectionCount = 1, mirroringActive = true, videoPlaybackActive = true)))

    @Test fun pinWinsOverEverything() =
        assertEquals(TvScreen.Pin("1234"), resolveTvScreen(idle.copy(connectionCount = 1, videoPlaybackActive = true, pinCode = "1234")))
}
