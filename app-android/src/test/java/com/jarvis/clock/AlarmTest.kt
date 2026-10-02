package com.jarvis.clock

import com.jarvis.clock.alarm.Alarm
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Tests JVM puros (sin dispositivo) de la lógica de alarmas. */
class AlarmTest {

    // nextTrigger usa la zona por defecto del dispositivo; en el reloj es la suya.
    // En los tests fijamos Europe/Amsterdam (donde vive el reloj) para reproducirlo.
    private val saved = TimeZone.getDefault()
    @Before fun setUp() { TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam")) }
    @After fun tearDown() { TimeZone.setDefault(saved) }

    private fun at(tz: String, y: Int, mo: Int, d: Int, h: Int, mi: Int): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone(tz))
        c.set(y, mo - 1, d, h, mi, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun field(tz: String, at: Long, f: Int): Int {
        val c = Calendar.getInstance(TimeZone.getTimeZone(tz)); c.timeInMillis = at; return c.get(f)
    }

    @Test fun `proxima ocurrencia hoy si aun no ha pasado`() {
        val now = at("Europe/Amsterdam", 2026, 6, 1, 6, 0)
        val t = Alarm(1, 7, 30).nextTrigger(now)!!
        assertEquals(7, field("Europe/Amsterdam", t, Calendar.HOUR_OF_DAY))
        assertEquals(30, field("Europe/Amsterdam", t, Calendar.MINUTE))
        assertEquals(1, field("Europe/Amsterdam", t, Calendar.DAY_OF_MONTH))
    }

    @Test fun `proxima ocurrencia manana si ya paso`() {
        val now = at("Europe/Amsterdam", 2026, 6, 1, 8, 0)
        val t = Alarm(1, 7, 30).nextTrigger(now)!!
        assertEquals(2, field("Europe/Amsterdam", t, Calendar.DAY_OF_MONTH))
    }

    @Test fun `alarma entre semana salta el fin de semana`() {
        // Sábado 2026-06-06 08:00 -> siguiente lunes.
        val sat = at("Europe/Amsterdam", 2026, 6, 6, 8, 0)
        val t = Alarm(1, 7, 0, Alarm.WEEKDAYS).nextTrigger(sat)!!
        assertEquals(Calendar.MONDAY, field("Europe/Amsterdam", t, Calendar.DAY_OF_WEEK))
        assertEquals(8, field("Europe/Amsterdam", t, Calendar.DAY_OF_MONTH))
    }

    @Test fun `cambio a horario de verano en Amsterdam`() {
        // DST 2026: reloj salta 02:00->03:00 el domingo 29-mar. Una alarma a las 02:30
        // ese día no existe; debe programarse sin reventar (Calendar la desplaza).
        val sat = at("Europe/Amsterdam", 2026, 3, 28, 12, 0)
        val t = Alarm(1, 2, 30).nextTrigger(sat)
        assertTrue(t != null && t > sat)
    }

    @Test fun `cambio a horario de invierno mantiene la hora local`() {
        // 25-oct-2026 el reloj retrasa 03:00->02:00. Alarma 08:00 debe seguir a las 08:00.
        val before = at("Europe/Amsterdam", 2026, 10, 24, 12, 0)
        val t = Alarm(1, 8, 0).nextTrigger(before)!!
        assertEquals(8, field("Europe/Amsterdam", t, Calendar.HOUR_OF_DAY))
        assertEquals(25, field("Europe/Amsterdam", t, Calendar.DAY_OF_MONTH))
    }

    @Test fun `alarma deshabilitada no dispara`() {
        assertNull(Alarm(1, 7, 0, enabled = false).nextTrigger(at("UTC", 2026, 6, 1, 6, 0)))
    }

    @Test fun `parseo de dias`() {
        assertEquals(0b0011111, Alarm.parseDays("weekdays"))
        assertEquals(0b1100000, Alarm.parseDays("weekend"))
        assertEquals(0b1111111, Alarm.parseDays("daily"))
        assertEquals(0b0000101, Alarm.parseDays("mon,wed"))
        assertEquals(0, Alarm.parseDays(""))
    }

    @Test fun `serializacion ida y vuelta`() {
        val a = Alarm(7, 6, 45, Alarm.WEEKDAYS, "Trabajo", true, kind = Alarm.Kind.ALARM)
        val b = Alarm.fromStorage(a.toStorage())
        assertEquals(a, b)
    }

    @Test fun `bit de dia por Calendar`() {
        assertEquals(1 shl 0, Alarm.dayBit(Calendar.MONDAY))
        assertEquals(1 shl 6, Alarm.dayBit(Calendar.SUNDAY))
    }

    // ------------------------------------------------------------ opciones por alarma

    @Test fun `alarma antigua sin opciones se lee con opciones nulas`() {
        val old = JSONObject("""{"id":4,"h":7,"m":15,"d":31,"l":"Trabajo","e":true,"t":0,"k":"ALARM"}""")
        val a = Alarm.fromStorage(old)
        assertEquals(7, a.hour); assertEquals(Alarm.WEEKDAYS, a.days); assertEquals("Trabajo", a.label)
        assertNull(a.sound); assertNull(a.maxVolume); assertNull(a.rampSeconds)
        assertNull(a.snoozeMinutes); assertNull(a.briefing)
    }

    @Test fun `opciones sobreviven al guardado`() {
        val a = Alarm(2, 6, 45, sound = "file:gallo.mp3", maxVolume = 60, rampSeconds = 30, snoozeMinutes = 9, briefing = false)
        assertEquals(a, Alarm.fromStorage(a.toStorage()))
    }

    @Test fun `toJson expone opciones nulas como null`() {
        val j = Alarm(1, 7, 0).toJson()
        assertTrue(j.has("sound") && j.isNull("sound"))
        assertTrue(j.isNull("max_volume")); assertTrue(j.isNull("ramp_s"))
        assertTrue(j.isNull("snooze_min")); assertTrue(j.isNull("briefing"))
        assertEquals(60, Alarm(1, 7, 0, maxVolume = 60).toJson().getInt("max_volume"))
    }

    @Test fun `withPatch cambia solo lo enviado`() {
        val a = Alarm(3, 7, 0, Alarm.WEEKDAYS, "Trabajo", maxVolume = 80)
        val b = a.withPatch(JSONObject("""{"time":"06:30","snooze_min":10}"""))
        assertEquals(6, b.hour); assertEquals(30, b.minute)
        assertEquals(10, b.snoozeMinutes)
        assertEquals("Trabajo", b.label); assertEquals(Alarm.WEEKDAYS, b.days); assertEquals(80, b.maxVolume)
    }

    @Test fun `withPatch con null vuelve al ajuste general`() {
        val a = Alarm(3, 7, 0, sound = "file:x.mp3", maxVolume = 80, briefing = true)
        val b = a.withPatch(JSONObject("""{"sound":null,"max_volume":null,"briefing":null}"""))
        assertNull(b.sound); assertNull(b.maxVolume); assertNull(b.briefing)
    }

    @Test fun `withPatch dias, nombre y activa`() {
        val b = Alarm(3, 7, 0).withPatch(JSONObject("""{"days":["sat","sun"],"label":"  Finde ","enabled":false}"""))
        assertEquals(Alarm.WEEKEND, b.days); assertEquals("Finde", b.label); assertFalse(b.enabled)
    }

    @Test fun `withPatch rechaza valores fuera de rango o de tipo`() {
        val a = Alarm(3, 7, 0)
        val e = assertThrows(IllegalArgumentException::class.java) { a.withPatch(JSONObject("""{"max_volume":150}""")) }
        assertEquals("max_volume debe estar entre 1 y 100", e.message)
        assertThrows(IllegalArgumentException::class.java) { a.withPatch(JSONObject("""{"snooze_min":5.5}""")) }
        assertThrows(IllegalArgumentException::class.java) { a.withPatch(JSONObject("""{"snooze_min":"5"}""")) }
        assertThrows(IllegalArgumentException::class.java) { a.withPatch(JSONObject("""{"time":"25:00"}""")) }
        assertThrows(IllegalArgumentException::class.java) { a.withPatch(JSONObject("""{"label":""}""")) }
        assertThrows(IllegalArgumentException::class.java) { a.withPatch(JSONObject("""{"enabled":"true"}""")) }
        assertThrows(IllegalArgumentException::class.java) { a.withPatch(JSONObject("""{"briefing":1}""")) }
        assertThrows(IllegalArgumentException::class.java) { a.withPatch(JSONObject("""{"sound":"http://x/y.mp3"}""")) }
        assertThrows(IllegalArgumentException::class.java) { a.withPatch(JSONObject("""{"sound":"system:http://homelab/a.mp3"}""")) }
        assertThrows(IllegalArgumentException::class.java) { a.withPatch(JSONObject("""{"sound":"file:"}""")) }
        assertEquals("system:content://media/internal/audio/media/1",
            a.withPatch(JSONObject("""{"sound":"system:content://media/internal/audio/media/1"}""")).sound)
    }

    @Test fun `withOptions ignora hora y nombre`() {
        val b = Alarm(3, 7, 0, label = "A").withOptions(JSONObject("""{"time":"09:00","label":"B","ramp_s":0}"""))
        assertEquals(7, b.hour); assertEquals("A", b.label); assertEquals(0, b.rampSeconds)
    }

    @Test fun `la pospuesta hereda las opciones`() {
        val src = Alarm(3, 7, 0, sound = "file:gallo.mp3", maxVolume = 40, rampSeconds = 5, snoozeMinutes = 7, briefing = false)
        val snooze = Alarm(9, 7, 7, triggerAt = 123L, kind = Alarm.Kind.SNOOZE).withOptionsOf(src)
        assertEquals("file:gallo.mp3", snooze.sound); assertEquals(40, snooze.maxVolume)
        assertEquals(5, snooze.rampSeconds); assertEquals(7, snooze.snoozeMinutes); assertEquals(false, snooze.briefing)
        assertEquals(Alarm.Kind.SNOOZE, snooze.kind); assertEquals(9, snooze.id)
    }

    @Test fun `parseTime acepta formatos y rechaza horas imposibles`() {
        assertEquals(7 to 30, Alarm.parseTime("07:30"))
        assertEquals(7 to 30, Alarm.parseTime("7.30"))
        assertNull(Alarm.parseTime("24:00"))
        assertNull(Alarm.parseTime("nope"))
    }
}
