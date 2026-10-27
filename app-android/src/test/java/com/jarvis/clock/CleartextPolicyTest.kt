package com.jarvis.clock

import com.jarvis.clock.assistant.CleartextPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextPolicyTest {
    @Test fun `https siempre vale`() {
        assertTrue(CleartextPolicy.allowed("https://ejemplo.com:8765"))
    }

    @Test fun `http a Tailscale, LAN y localhost vale`() {
        listOf(
            "http://100.64.0.9:8765", "http://100.127.255.1:8765", "http://192.168.1.20:8765",
            "http://10.0.0.5:8765", "http://172.16.0.2:8765", "http://172.31.9.9:8765",
            "http://127.0.0.1:8765", "http://localhost:8765", "http://[::1]:8765",
            "http://homelab.tail1234.ts.net:8765", "http://homelab:8765", "http://nas.local:8765",
        ).forEach { assertTrue(it, CleartextPolicy.allowed(it)) }
    }

    @Test fun `http a internet no vale`() {
        listOf(
            "http://8.8.8.8:8765", "http://100.128.0.1:8765", "http://100.63.0.1:8765",
            "http://172.32.0.1:8765", "http://192.169.0.1:8765", "http://ejemplo.com:8765",
            "http://evil.ts.net.ejemplo.com", "ftp://192.168.1.2", "no es una url", "",
        ).forEach { assertFalse(it, CleartextPolicy.allowed(it)) }
    }
}
