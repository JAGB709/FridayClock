package com.jarvis.clock

import com.jarvis.clock.design.Backgrounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackgroundsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `sanea nombres y no deja salir del directorio`() {
        assertEquals("playa.jpg", Backgrounds.sanitizeName("playa.JPG"))
        assertEquals("x.png", Backgrounds.sanitizeName("../../x.png"))
        assertEquals("x.png", Backgrounds.sanitizeName("..\\..\\x.png"))
        assertEquals("mi_foto_1.webp", Backgrounds.sanitizeName("mi foto (1).webp"))
    }

    @Test fun `rechaza extensiones no admitidas o nombres vacios`() {
        assertNull(Backgrounds.sanitizeName("virus.exe"))
        assertNull(Backgrounds.sanitizeName("sin_extension"))
        assertNull(Backgrounds.sanitizeName(".png"))
        assertNull(Backgrounds.sanitizeName("???.jpg"))
        assertNull(Backgrounds.sanitizeName("animacion.gif"))
    }

    @Test fun `fileFor solo resuelve archivos que existen`() {
        val dir = tmp.newFolder("backgrounds")
        File(dir, "playa.jpg").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(File(dir, "playa.jpg"), Backgrounds.fileFor(dir, "playa.jpg"))
        assertNull(Backgrounds.fileFor(dir, "borrada.jpg"))   // borrada: el tema cae a su color
        assertNull(Backgrounds.fileFor(dir, null))
        assertEquals(File(dir, "playa.jpg"), Backgrounds.fileFor(dir, "../playa.jpg")) // se sanea al nombre base
    }

    @Test fun `tipo mime por extension`() {
        assertEquals("image/jpeg", Backgrounds.mimeOf("a.jpg"))
        assertEquals("image/jpeg", Backgrounds.mimeOf("a.jpeg"))
        assertEquals("image/png", Backgrounds.mimeOf("a.png"))
        assertEquals("image/webp", Backgrounds.mimeOf("a.webp"))
    }
}
