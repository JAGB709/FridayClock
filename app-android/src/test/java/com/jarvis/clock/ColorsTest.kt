package com.jarvis.clock

import com.jarvis.clock.design.readableOn
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorsTest {
    @Test fun `texto negro sobre fondos claros y blanco sobre oscuros`() {
        assertEquals(0xFF000000.toInt(), readableOn(0xFFFFD180.toInt()))   // ámbar claro
        assertEquals(0xFFFFFFFF.toInt(), readableOn(0xFF0D47A1.toInt()))   // azul oscuro
        assertEquals(0xFFFFFFFF.toInt(), readableOn(0xFF000000.toInt()))
        assertEquals(0xFF000000.toInt(), readableOn(0xFFFFFFFF.toInt()))
    }
}
