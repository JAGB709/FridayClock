package com.jarvis.clock

import com.jarvis.clock.design.FaceData
import com.jarvis.clock.weather.Weather
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FaceDataTest {
    private val saved = TimeZone.getDefault()
    @Before fun setUp() { TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam")) }
    @After fun tearDown() { TimeZone.setDefault(saved) }

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, mo - 1, d, h, mi) }.timeInMillis

    @Test fun `hoy, mañana y dia de la semana`() {
        val now = at(2026, 10, 6, 22, 0)
        assertEquals("hoy", FaceData.dayWord(now, at(2026, 10, 6, 23, 30)))
        assertEquals("mañana", FaceData.dayWord(now, at(2026, 10, 7, 6, 0)))
        assertEquals("vie", FaceData.dayWord(now, at(2026, 10, 9, 7, 0)))
    }

    @Test fun `mañana cruza el fin de año`() {
        assertEquals("mañana", FaceData.dayWord(at(2026, 12, 31, 22, 0), at(2027, 1, 1, 7, 0)))
    }

    @Test fun `alarma siguiente como json`() {
        val j = FaceData.nextAlarmJson("Trabajo", "07:30", at(2026, 10, 6, 22, 0), at(2026, 10, 7, 7, 30))
        assertEquals("Trabajo", j.getString("label"))
        assertEquals("mañana", j.getString("when"))
        assertEquals("07:30", j.getString("time"))
    }

    @Test fun `sin datos del tiempo son null`() {
        assertEquals(JSONObject.NULL, FaceData.weatherJson(null))
        assertEquals(JSONObject.NULL, FaceData.forecastJson(null))
    }

    @Test fun `tiempo y previsión como json`() {
        val r = Weather.Report(
            temp = 19, code = 0, max = 22, min = 12, rainProb = 10, fetchedAt = 0L,
            hours = listOf(Weather.Hour(15, 20, 0)), days = listOf(Weather.Day("mié", 12, 23, 3)),
        )
        val w = FaceData.weatherJson(r) as JSONObject
        assertEquals(19, w.getInt("temp")); assertEquals("sun", w.getString("icon")); assertEquals("Despejado", w.getString("description"))
        assertEquals(22, w.getInt("max")); assertEquals(12, w.getInt("min")); assertEquals(10, w.getInt("rain"))
        val f = FaceData.forecastJson(r) as JSONObject
        assertEquals(15, f.getJSONArray("hours").getJSONObject(0).getInt("h"))
        assertEquals("mié", f.getJSONArray("days").getJSONObject(0).getString("d"))
        assertTrue(f.getJSONArray("days").getJSONObject(0).getString("icon").isNotEmpty())
    }

    @Test fun `la version del motor es sha-256 abreviado`() {
        assertEquals("ba7816bf8f01", FaceData.versionOf(listOf("abc".toByteArray())))
        assertEquals("ba7816bf8f01", FaceData.versionOf(listOf("a".toByteArray(), "bc".toByteArray())))
    }
}
