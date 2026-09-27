package com.jarvis.clock.server

import java.net.Inet4Address
import java.net.NetworkInterface

/** Utilidades de red compartidas por el servidor y los ajustes. */
object NetUtil {

    /** IPv4 no locales, con la de Tailscale (100.64.0.0/10, CGNAT) primero. */
    fun ipv4(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .sortedByDescending { isTailscale(it) }
    }.getOrDefault(emptyList())

    /** IP de Tailscale (100.64.0.0/10) si el reloj está en la tailnet. */
    fun tailscaleAddress(): String? = ipv4().firstOrNull { isTailscale(it) }

    private fun isTailscale(ip: String): Boolean {
        val p = ip.split('.').mapNotNull { it.toIntOrNull() }
        return p.size == 4 && p[0] == 100 && p[1] in 64..127
    }
}
