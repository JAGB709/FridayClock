package com.jarvis.clock

import com.jarvis.clock.weather.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherParseTest {

    private val sample = """
    {"current":{"temperature_2m":19.4,"weather_code":1},
     "hourly":{
       "time":["2026-10-06T14:00","2026-10-06T15:00","2026-10-06T16:00","2026-10-06T17:00","2026-10-06T18:00","2026-10-06T19:00",
               "2026-10-06T20:00","2026-10-06T21:00","2026-10-06T22:00","2026-10-06T23:00","2026-10-07T00:00","2026-10-07T01:00"],
       "temperature_2m":[19.4,20.1,20.6,21.0,20.2,19.5,18.8,17.9,17.2,16.4,15.9,15.2],
       "weather_code":[1,1,2,2,3,3,3,61,61,3,3,2]},
     "daily":{
       "time":["2026-10-06","2026-10-07","2026-10-08","2026-10-09","2026-10-10","2026-10-11"],
       "temperature_2m_max":[22.2,23.0,19.6,18.0,20.4,21.1],
       "temperature_2m_min":[12.1,11.9,10.2,9.8,10.0,11.6],
       "precipitation_probability_max":[10,5,60,80,20,0],
       "weather_code":[1,3,61,63,2,0]}}
    """.trimIndent()

    @Test fun `lee el tiempo actual y el resumen de hoy`() {
        val r = Weather.parse(sample, now = 1234L)
        assertEquals(19, r.temp); assertEquals(1, r.code)
        assertEquals(22, r.max); assertEquals(12, r.min); assertEquals(10, r.rainProb)
        assertEquals(1234L, r.fetchedAt)
    }

    @Test fun `previsión por horas cada dos horas desde la siguiente`() {
        val h = Weather.parse(sample).hours
        assertEquals(listOf(15, 17, 19, 21, 23), h.map { it.h })
        assertEquals(listOf(20, 21, 20, 18, 16), h.map { it.temp })
        assertEquals(listOf(1, 2, 3, 61, 3), h.map { it.code })
        assertEquals("☀", h[0].icon)
    }

    @Test fun `previsión por días desde mañana con nombre corto`() {
        val d = Weather.parse(sample).days
        assertEquals(listOf("mié", "jue", "vie", "sáb", "dom"), d.map { it.d })
        assertEquals(listOf(12, 10, 10, 10, 12), d.map { it.min })
        assertEquals(listOf(23, 20, 18, 20, 21), d.map { it.max })
        assertEquals(listOf(3, 61, 63, 2, 0), d.map { it.code })
    }

    @Test fun `sin bloque hourly o sin weather_code no revienta`() {
        val sinHoras = """{"current":{"temperature_2m":10.0,"weather_code":0},
            "daily":{"time":["2026-10-06","2026-10-07"],"temperature_2m_max":[12.0,13.0],"temperature_2m_min":[5.0,6.0]}}"""
        val r = Weather.parse(sinHoras)
        assertTrue(r.hours.isEmpty())
        assertEquals(1, r.days.size)
        assertEquals(0, r.days[0].code)
    }
}
