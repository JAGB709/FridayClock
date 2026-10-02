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
    // Opciones por alarma; null = usar el ajuste general de Prefs.
    val sound: String? = null,          // "system:<uri>" | "file:<nombre>"
    val maxVolume: Int? = null,         // 1-100 (100 = comportamiento de siempre)
    val rampSeconds: Int? = null,       // 0-600
    val snoozeMinutes: Int? = null,     // 1-60
    val briefing: Boolean? = null,
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
        put("sound", sound ?: JSONObject.NULL)
        put("max_volume", maxVolume ?: JSONObject.NULL)
        put("ramp_s", rampSeconds ?: JSONObject.NULL)
        put("snooze_min", snoozeMinutes ?: JSONObject.NULL)
        put("briefing", briefing ?: JSONObject.NULL)
        if (isAbsolute) put("trigger_at", triggerAt)
        nextTrigger()?.let { put("next", it) }
    }

    fun toStorage(): JSONObject = JSONObject().apply {
        put("id", id); put("h", hour); put("m", minute); put("d", days)
        put("l", label); put("e", enabled); put("t", triggerAt); put("k", kind.name)
        sound?.let { put("snd", it) }
        maxVolume?.let { put("vol", it) }
        rampSeconds?.let { put("rmp", it) }
        snoozeMinutes?.let { put("snz", it) }
        briefing?.let { put("brf", it) }
    }

    /**
     * Copia con los campos de [p] aplicados (lo que usa `update` de la API).
     * Campo ausente = sin cambios; null en una opción = volver al ajuste general.
     * Lanza IllegalArgumentException con un mensaje en español si algo no vale.
     */
    fun withPatch(p: JSONObject): Alarm {
        var a = this
        if (p.has("time")) {
            val (h, m) = parseTime(p.optString("time")) ?: throw IllegalArgumentException("time debe ser HH:MM")
            a = a.copy(hour = h, minute = m)
        }
        if (p.has("label")) {
            val l = (p.opt("label") as? String)?.trim().orEmpty()
            require(l.isNotEmpty() && l.length <= 40) { "label debe tener entre 1 y 40 caracteres" }
            a = a.copy(label = l)
        }
        if (p.has("days")) a = a.copy(days = parseDays(p.opt("days")))
        if (p.has("enabled")) {
            a = a.copy(enabled = p.opt("enabled") as? Boolean ?: throw IllegalArgumentException("enabled debe ser true o false"))
        }
        return a.withOptions(p)
    }

    /** Solo las opciones por alarma de [p]; ignora hora, nombre, días y activa. */
    fun withOptions(p: JSONObject): Alarm {
        var a = this
        if (p.has("sound")) a = a.copy(sound = optSound(p))
        if (p.has("max_volume")) a = a.copy(maxVolume = optIntIn(p, "max_volume", 1, 100))
        if (p.has("ramp_s")) a = a.copy(rampSeconds = optIntIn(p, "ramp_s", 0, 600))
        if (p.has("snooze_min")) a = a.copy(snoozeMinutes = optIntIn(p, "snooze_min", 1, 60))
        if (p.has("briefing")) {
            a = a.copy(
                briefing = if (p.isNull("briefing")) null
                else p.opt("briefing") as? Boolean ?: throw IllegalArgumentException("briefing debe ser true, false o null")
            )
        }
        return a
    }

    /** Las pospuestas suenan igual que la alarma de la que vienen. */
    fun withOptionsOf(o: Alarm) = copy(
        sound = o.sound, maxVolume = o.maxVolume, rampSeconds = o.rampSeconds,
        snoozeMinutes = o.snoozeMinutes, briefing = o.briefing,
    )

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
            sound = o.optString("snd").ifEmpty { null },
            maxVolume = if (o.has("vol")) o.getInt("vol") else null,
            rampSeconds = if (o.has("rmp")) o.getInt("rmp") else null,
            snoozeMinutes = if (o.has("snz")) o.getInt("snz") else null,
            briefing = if (o.has("brf")) o.getBoolean("brf") else null,
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

        fun parseTime(s: String): Pair<Int, Int>? {
            val m = Regex("^(\\d{1,2})[:.h](\\d{2})$").find(s.trim()) ?: return null
            val h = m.groupValues[1].toInt()
            val mi = m.groupValues[2].toInt()
            return if (h in 0..23 && mi in 0..59) h to mi else null
        }

        private fun optIntIn(p: JSONObject, key: String, min: Int, max: Int): Int? {
            if (p.isNull(key)) return null
            val d = (p.opt(key) as? Number)?.toDouble()
            if (d == null || d % 1.0 != 0.0) throw IllegalArgumentException("$key debe ser un número entero")
            require(d >= min && d <= max) { "$key debe estar entre $min y $max" }
            return d.toInt()
        }

        private fun optSound(p: JSONObject): String? {
            if (p.isNull("sound")) return null
            val s = (p.opt("sound") as? String)?.trim().orEmpty()
            require(s.startsWith("system:") || s.startsWith("file:")) { "sound debe empezar por system: o file:" }
            require(s.substringAfter(':').isNotEmpty()) { "sound vacío tras el prefijo" }
            require(!s.startsWith("system:") || Sounds.isLocalSystemUri(s.removePrefix("system:"))) {
                "sound system: debe ser un tono del reloj (content://)"
            }
            return s
        }
    }
}
