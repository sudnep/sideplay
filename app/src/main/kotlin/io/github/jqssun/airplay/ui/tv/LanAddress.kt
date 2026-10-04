package io.github.jqssun.airplay.ui.tv

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** The TV's LAN IPv4, shown on the idle screen for troubleshooting. */
object LanAddress {
    fun pick(addresses: List<InetAddress>): String? =
        addresses.filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .sortedByDescending { it.isSiteLocalAddress }
            .firstOrNull()?.hostAddress

    fun current(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
    }.getOrNull()?.let(::pick)
}
