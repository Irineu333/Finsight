package com.neoutils.finsight.mcp

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/** What the tests ask of the machine's own interfaces, so no test has to open a socket by hand. */
internal object Loopback {

    /** Whether a TCP connection to [address]`:`[port] is refused — nothing is listening there. */
    fun refusesConnection(port: Int, address: String = "127.0.0.1"): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS) }
        false
    } catch (_: IOException) {
        true
    }

    /**
     * The addresses of this machine that are **not** loopback — what a second machine would reach
     * it at, borrowed as a local stand-in for one.
     *
     * A container or a laptop with the network down offers none, and the test that uses this says
     * what it falls back to.
     */
    fun externalAddresses(): List<InetAddress> = NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filter { runCatching { it.isUp }.getOrDefault(false) }
        .flatMap { it.inetAddresses.asSequence() }
        .filterNot { it.isLoopbackAddress }
        .filterNot { it.isLinkLocalAddress }
        .filterNot { it.isMulticastAddress }
        .toList()

    private const val CONNECT_TIMEOUT_MILLIS = 1_000
}
