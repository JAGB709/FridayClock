package com.jarvis.clock

import com.jarvis.clock.alarm.Sounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SoundsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `sanea nombres y nunca deja salir del directorio`() {
        assertEquals("gallo.mp3", Sounds.sanitizeName("gallo.mp3"))
        assertEquals("x.mp3", Sounds.sanitizeName("../../x.mp3"))
        assertEquals("x.mp3", Sounds.sanitizeName("..\\..\\x.mp3"))
        assertEquals("a_b.mp3", Sounds.sanitizeName("a b?.MP3"))
        assertEquals("canci_n_de_cuna.ogg", Sounds.sanitizeName("canción de cuna.ogg")) // no ASCII -> _
    }

    @Test fun `rechaza extensiones no admitidas o nombres vacios`() {
        assertNull(Sounds.sanitizeName("virus.exe"))
        assertNull(Sounds.sanitizeName("sin_extension"))
        assertNull(Sounds.sanitizeName(".mp3"))
        assertNull(Sounds.sanitizeName("???.wav"))
    }

    @Test fun `system solo admite uris locales`() {
        assertTrue(Sounds.isLocalSystemUri("content://media/internal/audio/media/1"))
        assertTrue(Sounds.isLocalSystemUri("android.resource://x/1"))
        assertFalse(Sounds.isLocalSystemUri("http://homelab/a.mp3"))
        assertFalse(Sounds.isLocalSystemUri("content://"))
        assertFalse(Sounds.isLocalSystemUri(""))
    }

    @Test fun `fileFor solo resuelve archivos que existen dentro del directorio`() {
        val dir = tmp.newFolder("sounds")
        java.io.File(dir, "gallo.mp3").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(java.io.File(dir, "gallo.mp3"), Sounds.fileFor(dir, "file:gallo.mp3"))
        assertNull(Sounds.fileFor(dir, "file:borrado.mp3"))   // borrado -> fallback al sonar
        assertNull(Sounds.fileFor(dir, "system:content://x"))
        assertNull(Sounds.fileFor(dir, null))
        assertNull(Sounds.fileFor(dir, "file:../gallo.exe"))
    }
}
