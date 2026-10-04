package io.github.jqssun.airplay.ui.tv

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanAddressTest {
    private fun ip(vararg b: Int) = InetAddress.getByAddress(b.map { it.toByte() }.toByteArray())

    @Test fun picksSiteLocalIpv4() =
        assertEquals("192.168.1.248", LanAddress.pick(listOf(ip(127, 0, 0, 1), ip(192, 168, 1, 248))))

    @Test fun skipsLinkLocalAndIpv6() =
        assertEquals("10.0.0.5", LanAddress.pick(listOf(
            ip(169, 254, 3, 4),
            ip(0xfe, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            ip(10, 0, 0, 5),
        )))

    @Test fun prefersSiteLocalOverPublic() =
        assertEquals("192.168.0.2", LanAddress.pick(listOf(ip(8, 8, 8, 8), ip(192, 168, 0, 2))))

    @Test fun publicAddressUsedWhenNothingElse() =
        assertEquals("8.8.8.8", LanAddress.pick(listOf(ip(8, 8, 8, 8))))

    @Test fun nothingUsableIsNull() = assertNull(LanAddress.pick(listOf(ip(127, 0, 0, 1))))
}
