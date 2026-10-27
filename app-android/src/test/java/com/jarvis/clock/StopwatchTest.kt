package com.jarvis.clock

import com.jarvis.clock.alarm.StopwatchState
import com.jarvis.clock.design.FaceControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchTest {
    @Test fun `inicia, pausa y acumula`() {
        val s = StopwatchState()
        assertEquals(0, s.elapsed(1000))
        s.start(1000)
        assertEquals(500, s.elapsed(1500))
        s.pause(2000)
        assertFalse(s.running)
        assertEquals(1000, s.elapsed(99999))
        s.start(5000)
        assertEquals(1500, s.elapsed(5500))
    }

    @Test fun `iniciar dos veces no reinicia y reset deja a cero`() {
        val s = StopwatchState()
        s.start(1000); s.start(3000)
        assertEquals(1000, s.elapsed(2000))
        s.reset(4000)
        assertTrue(s.running)
        assertEquals(0, s.elapsed(4000))
        assertEquals(250, s.elapsed(4250))
        s.pause(5000); s.reset(6000)
        assertEquals(0, s.elapsed(7000))
    }

    @Test fun `toggle alterna y el json lleva los campos del motor`() {
        val s = StopwatchState()
        s.toggle(100)
        val j = s.toJson(300)
        assertTrue(j.getBoolean("running"))
        assertEquals(100L, j.getLong("since"))
        assertEquals(200L, j.getLong("elapsed_ms"))
        s.toggle(400)
        assertFalse(s.running)
    }

    @Test fun `las ordenes de la caratula solo traducen la lista cerrada`() {
        assertEquals("toggle", FaceControl.parse("stopwatch:toggle")!!.getString("action"))
        assertEquals("pause_timer", FaceControl.parse("timer:pause:7")!!.getString("type"))
        assertEquals(7, FaceControl.parse("timer:pause:7")!!.getInt("id"))
        assertEquals(3, FaceControl.parse("timer:resume:p3")!!.getInt("pid"))
        assertEquals(3, FaceControl.parse("timer:cancel:p3")!!.getInt("pid"))
        assertEquals(4, FaceControl.parse("timer:cancel:4")!!.getInt("id"))
        assertNull(FaceControl.parse("timer:pause:p3"))
        assertNull(FaceControl.parse("timer:resume:3"))
        assertNull(FaceControl.parse("timer:cancel:3;x"))
        assertNull(FaceControl.parse("set_design"))
        assertNull(FaceControl.parse("timer:cancel:99999999999"))
    }
}
