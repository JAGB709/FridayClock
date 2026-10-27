package com.jarvis.clock.assistant

import java.net.URI

/**
 * Política de tráfico en claro hacia el puente.
 *
 * El network_security_config de Android 10 no admite rangos CIDR (solo dominios o IP literales),
 * así que no puede limitar http a Tailscale o a la LAN. Esa restricción se aplica aquí, antes de
 * enviar el token: https vale siempre; http solo hacia localhost, rangos privados (10/8, 172.16/12,
 * 192.168/16), enlace local, Tailscale (100.64/10), *.ts.net, nombres sin punto y *.local.
 */
object CleartextPolicy {
    fun allowed(url: String): Boolean {
        val uri = try { URI(url.trim()) } catch (e: Exception) { return false }
        val host = uri.host?.removePrefix("[")?.removeSuffix("]")?.lowercase() ?: return false
        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> hostAllowed(host)
            else -> false
        }
    }

    private fun hostAllowed(host: String): Boolean {
        if (host == "localhost" || host == "::1") return true
        val ip = ipv4(host)
        if (ip != null) return privateV4(ip)
        if (host.contains(':')) return false // otras IPv6: no se admiten en claro
        return !host.contains('.') || host.endsWith(".ts.net") || host.endsWith(".local")
    }

    private fun ipv4(host: String): IntArray? {
        val p = host.split('.')
        if (p.size != 4) return null
        val n = p.map { it.toIntOrNull() ?: return null }
        return if (n.all { it in 0..255 }) n.toIntArray() else null
    }

    private fun privateV4(a: IntArray): Boolean = when {
        a[0] == 127 || a[0] == 10 -> true
        a[0] == 192 && a[1] == 168 -> true
        a[0] == 172 && a[1] in 16..31 -> true
        a[0] == 169 && a[1] == 254 -> true
        a[0] == 100 && a[1] in 64..127 -> true
        else -> false
    }
}
