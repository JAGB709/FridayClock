package com.jarvis.clock

import com.jarvis.clock.ui.SafeMarkdown
import com.jarvis.clock.ui.SafeMarkdown.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeMarkdownTest {
    @Test fun `titulos, parrafos, vinetas y citas`() {
        val b = SafeMarkdown.parse("# Hola\nprimera linea\nsegunda\n\n- uno\n* dos\n3. tres\n> cita")
        assertEquals(listOf(Kind.HEADING, Kind.PARAGRAPH, Kind.BULLET, Kind.BULLET, Kind.BULLET, Kind.QUOTE), b.map { it.kind })
        assertEquals(1, b[0].level)
        assertEquals("primera linea segunda", b[1].plain)
        assertEquals("•", b[2].marker)
        assertEquals("3.", b[4].marker)
        assertEquals("cita", b[5].plain)
    }

    @Test fun `negrita, cursiva y codigo`() {
        val r = SafeMarkdown.inline("a **fuerte** y *suave* con `x*y`")
        assertEquals("a fuerte y suave con x*y", r.joinToString("") { it.text })
        assertTrue(r.first { it.text == "fuerte" }.bold)
        assertTrue(r.first { it.text == "suave" }.italic)
        assertTrue(r.first { it.text == "x*y" }.code)
    }

    @Test fun `lo que no cierra queda como texto`() {
        assertEquals("2 * 3 = 6 y **abierta", SafeMarkdown.inline("2 * 3 = 6 y **abierta").joinToString("") { it.text })
    }

    @Test fun `los enlaces, imagenes y etiquetas HTML nunca son activos`() {
        val t = SafeMarkdown.parse("mira [esto](javascript:alert(1)) ![img](http://x/a.png) <script>alert(1)</script> ok")[0].plain
        assertFalse(t.contains("javascript"))
        assertFalse(t.contains("http"))
        assertFalse(t.contains("<"))
        assertTrue(t.startsWith("mira esto img"))
    }

    @Test fun `limita el tamano`() {
        val big = (1..200).joinToString("\n") { "- punto $it" }
        assertEquals(SafeMarkdown.MAX_BLOCKS, SafeMarkdown.parse(big).size)
        assertTrue(SafeMarkdown.parse("x".repeat(10_000)).sumOf { it.plain.length } <= SafeMarkdown.MAX_CHARS)
        assertTrue(SafeMarkdown.parse("").isEmpty())
    }
}
