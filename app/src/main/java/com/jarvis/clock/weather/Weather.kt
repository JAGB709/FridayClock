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

    data class Report(
        val temp: Int,
        val code: Int,
        val max: Int,
        val min: Int,
        val rainProb: Int,
        val fetchedAt: Long,
    ) {
        val description get() = describe(code)
        val icon get() = icon(code)
        fun shortText() = "$icon $temp° · ${description} · ↑$max° ↓$min°" + if (rainProb >= 30) " · lluvia $rainProb%" else ""
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
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=1",
            Prefs.latitude, Prefs.longitude,
        )
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val cur = o.getJSONObject("current")
            val d = o.getJSONObject("daily")
            return Report(
                temp = cur.getDouble("temperature_2m").roundToInt(),
                code = cur.getInt("weather_code"),
                max = d.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                min = d.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                rainProb = d.optJSONArray("precipitation_probability_max")?.optInt(0) ?: 0,
                fetchedAt = System.currentTimeMillis(),
            )
        } finally {
            c.disconnect()
        }
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

    private fun icon(code: Int): String = when (code) {
        0, 1 -> "☀"
        2 -> "⛅"
        3, 45, 48 -> "☁"
        in 51..67, in 80..82 -> "☂"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⚡"
        else -> "·"
    }
}
