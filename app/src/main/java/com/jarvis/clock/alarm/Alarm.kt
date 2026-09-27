package com.jarvis.clock.alarm

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Una alarma. Tres sabores:
 *  - Repetitiva: [days] != 0 (bit 0 = lunes ... bit 6 = domingo).
 *  - Puntual: [days] == 0 y [triggerAt] == 0 -> suena la próxima vez que sean hh:mm y se desactiva.
 *  - Absoluta: [triggerAt] > 0 (temporizadores y "posponer"); se borra al sonar.
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val label: String = "Alarma",
    val enabled: Boolean = true,
    val triggerAt: Long = 0L,
    val kind: Kind = Kind.ALARM,
) {
    enum class Kind { ALARM, TIMER, SNOOZE }

    val isAbsolute get() = triggerAt > 0L
    val isRepeating get() = days != 0 && !isAbsolute

    /** Próximo instante (epoch ms) en que debe sonar, o null si no aplica. */
    fun nextTrigger(now: Long = System.currentTimeMillis()): Long? {
        if (!enabled) return null
        if (isAbsolute) return triggerAt.takeIf { it > now - 60_000 }
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        if (days == 0) return cal.timeInMillis
        repeat(8) {
            if (days and dayBit(cal.get(Calendar.DAY_OF_WEEK)) != 0) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    fun timeText(): String = "%02d:%02d".format(hour, minute)

    fun daysText(): String = when {
        isAbsolute -> when (kind) {
            Kind.TIMER -> "Temporizador"
            Kind.SNOOZE -> "Pospuesta"
            else -> "Una vez"
        }
        days == 0 -> "Una vez"
        days == ALL_DAYS -> "Todos los días"
        days == WEEKDAYS -> "Lunes a viernes"
        days == WEEKEND -> "Fines de semana"
        else -> DAY_SHORT.indices.filter { days and (1 shl it) != 0 }.joinToString(" ") { DAY_SHORT[it] }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("time", timeText())
        put("hour", hour)
        put("minute", minute)
        put("days", JSONArray(DAY_KEYS.indices.filter { days and (1 shl it) != 0 }.map { DAY_KEYS[it] }))
        put("label", label)
        put("enabled", enabled)
        put("kind", kind.name.lowercase())
        if (isAbsolute) put("trigger_at", triggerAt)
        nextTrigger()?.let { put("next", it) }
    }

    fun toStorage(): JSONObject = JSONObject().apply {
        put("id", id); put("h", hour); put("m", minute); put("d", days)
        put("l", label); put("e", enabled); put("t", triggerAt); put("k", kind.name)
    }

    companion object {
        const val ALL_DAYS = 0b1111111
        const val WEEKDAYS = 0b0011111
        const val WEEKEND = 0b1100000
        val DAY_KEYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        val DAY_SHORT = listOf("L", "M", "X", "J", "V", "S", "D")

        /** Calendar.DAY_OF_WEEK -> bit de [days]. */
        fun dayBit(calendarDay: Int): Int = 1 shl ((calendarDay + 5) % 7)

        fun fromStorage(o: JSONObject) = Alarm(
            id = o.getInt("id"),
            hour = o.getInt("h"),
            minute = o.getInt("m"),
            days = o.optInt("d"),
            label = o.optString("l", "Alarma"),
            enabled = o.optBoolean("e", true),
            triggerAt = o.optLong("t"),
            kind = runCatching { Kind.valueOf(o.optString("k")) }.getOrDefault(Kind.ALARM),
        )

        /** Acepta ["mon","fri"], "weekdays", "weekend", "daily", "L,M,X" ... */
        fun parseDays(v: Any?): Int = when (v) {
            null -> 0
            is JSONArray -> (0 until v.length()).fold(0) { acc, i -> acc or parseDays(v.optString(i)) }
            is Number -> v.toInt() and ALL_DAYS
            is String -> when (val s = v.trim().lowercase()) {
                "", "once", "none" -> 0
                "daily", "everyday", "all", "todos" -> ALL_DAYS
                "weekdays", "laborables", "entre semana" -> WEEKDAYS
                "weekend", "finde", "fin de semana" -> WEEKEND
                else -> if (s.contains(',') || s.contains(' ')) {
                    s.split(',', ' ').filter { it.isNotBlank() }.fold(0) { acc, p -> acc or parseDays(p) }
                } else {
                    val i = DAY_KEYS.indexOf(s.take(3)).takeIf { it >= 0 }
                        ?: DAY_SHORT.indexOfFirst { it.equals(s, ignoreCase = true) }
                    if (i >= 0) 1 shl i else 0
                }
            }
            else -> 0
        }
    }
}
