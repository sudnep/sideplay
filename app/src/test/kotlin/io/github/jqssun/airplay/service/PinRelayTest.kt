package io.github.jqssun.airplay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinRelayTest {
    private val shown = mutableListOf<String?>()
    private val relay = PinRelay { shown += it }

    @Test fun repeatOfTheShownPinIsNotReemitted() {
        relay.display("1234"); relay.display("1234")
        assertEquals(listOf<String?>("1234"), shown)
    }

    // uxplay reuses one pin per server run; a retry after the tv dismissed it must show it again
    @Test fun samePinShowsAgainAfterAcknowledge() {
        relay.display("1234"); relay.acknowledge(); relay.display("1234")
        assertEquals(listOf<String?>("1234", "1234"), shown)
    }

    @Test fun acknowledgeDoesNotEmit() {
        relay.display("1234"); relay.acknowledge()
        assertEquals(listOf<String?>("1234"), shown)
    }

    @Test fun clearEmitsNull() {
        relay.display("1234"); relay.clear()
        assertEquals(listOf<String?>("1234", null), shown)
        assertNull(relay.current)
    }

    // the activity rebinding replays `current`; a dismissed pin must not come back
    @Test fun nothingToReplayAfterAcknowledge() {
        relay.display("1234"); relay.acknowledge()
        assertNull(relay.current)
    }
}
