package com.jarvis.clock

import com.jarvis.clock.alarm.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
