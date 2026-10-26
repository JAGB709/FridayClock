package com.jarvis.clock

import com.jarvis.clock.server.NetUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetBindTest {
    @Test fun `sin solo Tailscale se escucha en todas las interfaces`() {
        assertNull(NetUtil.bindHost(false, null))
        assertNull(NetUtil.bindHost(false, "100.64.0.9"))
    }

    @Test fun `solo Tailscale con IP enlaza a ella`() {
        assertEquals("100.64.0.9", NetUtil.bindHost(true, "100.64.0.9"))
    }

    @Test fun `solo Tailscale sin IP nunca cae a todas las interfaces`() {
        assertEquals("127.0.0.1", NetUtil.bindHost(true, null))
    }
}
