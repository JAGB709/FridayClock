package com.jarvis.clock

import com.jarvis.clock.assistant.Actions
import com.jarvis.clock.assistant.LocalIntents
import com.jarvis.clock.assistant.LocalIntents.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests JVM del intérprete de órdenes en español y utilidades sin Android. */
class IntentTest {

    @Test fun `alarma con hora en cifras`() {
        val i = LocalIntents.parse("pon una alarma a las 7:30") as Intent.SetAlarm
        assertEquals(7, i.hour); assertEquals(30, i.minute)
    }

    @Test fun `alarma en palabras entre semana`() {
        val i = LocalIntents.parse("despiértame a las siete y media entre semana") as Intent.SetAlarm
        assertEquals(7, i.hour); assertEquals(30, i.minute); assertEquals(0b0011111, i.days)
    }

    @Test fun `ocho menos cuarto de la noche`() {
        val i = LocalIntents.parse("alarma a las ocho menos cuarto de la noche") as Intent.SetAlarm
        assertEquals(19, i.hour); assertEquals(45, i.minute)
    }

    @Test fun `temporizador en minutos con etiqueta`() {
        val i = LocalIntents.parse("pon un temporizador de 5 minutos para la pasta") as Intent.SetTimer
        assertEquals(300L, i.seconds); assertEquals("Pasta", i.label)
    }

    @Test fun `temporizador hora y media`() {
        assertEquals(5400L, (LocalIntents.parse("temporizador de una hora y media") as Intent.SetTimer).seconds)
    }

    @Test fun `cancelar por hora`() {
        val i = LocalIntents.parse("quita la alarma de las 7:30") as Intent.CancelAlarm
        assertEquals(7, i.hour); assertEquals(30, i.minute)
    }

    @Test fun `preguntas basicas`() {
        assertTrue(LocalIntents.parse("¿qué hora es?") is Intent.Time)
        assertTrue(LocalIntents.parse("qué tiempo hace") is Intent.Weather)
        assertTrue(LocalIntents.parse("qué alarmas tengo") is Intent.ListAlarms)
    }

    @Test fun `no confundir tiempo de manana con hoy`() {
        assertNull(LocalIntents.parse("qué tiempo hará mañana"))
    }

    @Test fun `charla libre va al LLM`() {
        assertNull(LocalIntents.parse("cuéntame un chiste"))
    }

    @Test fun `volumen absoluto y relativo`() {
        assertEquals(40, (LocalIntents.parse("volumen al 40") as Intent.Volume).percent)
        assertEquals(15, (LocalIntents.parse("sube el volumen") as Intent.Volume).delta)
    }

    @Test fun `activar bluetooth`() {
        val i = LocalIntents.parse("Friday activa el bluetooth") as Intent.Bluetooth
        assert(i.on && i.speaker)
    }

    @Test fun `modo altavoz`() {
        val i = LocalIntents.parse("pon el modo altavoz") as Intent.Bluetooth
        assert(i.on)
    }

    @Test fun `apagar bluetooth`() {
        val i = LocalIntents.parse("apaga el bluetooth") as Intent.Bluetooth
        assert(!i.on)
    }

    @Test fun `parseTime valida rango`() {
        assertEquals(7 to 30, Actions.parseTime("07:30"))
        assertNull(Actions.parseTime("25:00"))
        assertNull(Actions.parseTime("basura"))
    }
}

