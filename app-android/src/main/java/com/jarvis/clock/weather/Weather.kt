package com.jarvis.clock.weather

import com.jarvis.clock.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tiempo actual y del día vía Open-Meteo (gratis, sin API key).
 * Caché de 20 minutos para no gastar red ni batería.
 */
object Weather {

    data class Hour(val h: Int, val temp: Int, val code: Int) {
        val icon get() = Weather.icon(code)
    }

    data class Day(val d: String, val min: Int, val max: Int, val code: Int) {
        val icon get() = Weather.icon(code)
    }

    data class Report(
        val temp: Int,
        val code: Int,
        val max: Int,
        val min: Int,
        val rainProb: Int,
        val fetchedAt: Long,
        val hours: List<Hour> = emptyList(),
        val days: List<Day> = emptyList(),
    ) {
        val description get() = describe(code)
        val icon get() = icon(code)
        fun shortText() = "$temp° · ${description} · ↑$max° ↓$min°" + if (rainProb >= 30) " · lluvia $rainProb%" else ""
        fun spoken(city: String) = buildString {
            append("En $city hace $temp grados, ${description.lowercase()}. ")
            append("Hoy máxima de $max y mínima de $min.")
            if (rainProb >= 40) append(" Hay un $rainProb por ciento de probabilidad de lluvia, coge paraguas.")
        }
    }

    @Volatile private var cache: Report? = null

    /** Bloqueante: llamar fuera del hilo principal. */
    fun get(force: Boolean = false): Report? {
        cache?.let { if (!force && System.currentTimeMillis() - it.fetchedAt < 20 * 60_000) return it }
        return runCatching { fetch() }.getOrNull()?.also { cache = it } ?: cache
    }

    fun cached(): Report? = cache

    private fun fetch(): Report {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m,weather_code&forecast_hours=12" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code&forecast_days=6" +
                "&timezone=auto",
            Prefs.latitude, Prefs.longitude,
        )
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            return parse(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    /**
     * Interpreta la respuesta de Open-Meteo. Las horas son las siguientes cada dos horas
     * (la primera entrada es la hora actual) y los días empiezan mañana.
     */
    fun parse(body: String, now: Long = System.currentTimeMillis()): Report {
        val o = JSONObject(body)
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val dTimes = d.getJSONArray("time")
        val dMax = d.getJSONArray("temperature_2m_max")
        val dMin = d.getJSONArray("temperature_2m_min")
        val dCode = d.optJSONArray("weather_code")
        // La previsión es opcional: una entrada nula solo pierde la previsión, no el tiempo actual.
        val days = runCatching {
            val l = ArrayList<Day>()
            for (i in 1 until dTimes.length()) {
                l += Day(dayName(dTimes.getString(i)), dMin.getDouble(i).roundToInt(), dMax.getDouble(i).roundToInt(), dCode?.optInt(i) ?: 0)
            }
            l.toList()
        }.getOrDefault(emptyList())
        val hours = runCatching {
            val l = ArrayList<Hour>()
            o.optJSONObject("hourly")?.let { hr ->
                val t = hr.getJSONArray("time")
                val temp = hr.getJSONArray("temperature_2m")
                val code = hr.optJSONArray("weather_code")
                var i = 1
                while (i < t.length() && l.size < 5) {
                    l += Hour(t.getString(i).substring(11, 13).toInt(), temp.getDouble(i).roundToInt(), code?.optInt(i) ?: 0)
                    i += 2
                }
            }
            l.toList()
        }.getOrDefault(emptyList())
        return Report(
            temp = cur.getDouble("temperature_2m").roundToInt(),
            code = cur.getInt("weather_code"),
            max = dMax.getDouble(0).roundToInt(),
            min = dMin.getDouble(0).roundToInt(),
            rainProb = d.optJSONArray("precipitation_probability_max")?.optInt(0) ?: 0,
            fetchedAt = now,
            hours = hours,
            days = days,
        )
    }

    private val DAY_SHORT = arrayOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")

    /** "2026-10-07" -> "mié" (sin depender del idioma de la JVM). */
    private fun dayName(date: String): String {
        val (y, m, d) = date.split("-").map { it.toInt() }
        val dow = java.util.GregorianCalendar(y, m - 1, d).get(java.util.Calendar.DAY_OF_WEEK)
        return DAY_SHORT[dow - 1]
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Despejado"
        1 -> "Casi despejado"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Niebla"
        51, 53, 55, 56, 57 -> "Llovizna"
        61, 63, 66 -> "Lluvia"
        65, 67 -> "Lluvia fuerte"
        71, 73, 75, 77 -> "Nieve"
        80, 81 -> "Chubascos"
        82 -> "Chubascos fuertes"
        85, 86 -> "Chubascos de nieve"
        95, 96, 99 -> "Tormenta"
        else -> "Variable"
    }

    /** Nombre del icono Lucide (lo dibuja el motor de pantallas; sin emojis). */
    fun icon(code: Int): String = when (code) {
        0, 1 -> "sun"
        2 -> "cloud-sun"
        45, 48 -> "cloud-fog"
        in 51..67, in 80..82 -> "cloud-rain"
        in 71..77, 85, 86 -> "cloud-snow"
        in 95..99 -> "cloud-lightning"
        else -> "cloud"
    }
}
