package com.jarvis.clock.assistant

import java.text.Normalizer

/**
 * Comprensión de órdenes básicas en español SIN red ni LLM: el despertador
 * tiene que funcionar aunque el homelab esté caído. Solo interpreta; quien
 * ejecuta es [Assistant] a través de [Actions].
 */
object LocalIntents {

    sealed class Intent {
        data class SetAlarm(val hour: Int, val minute: Int, val days: Int, val label: String) : Intent()
        data class SetTimer(val seconds: Long, val label: String) : Intent()
        data class CancelAlarm(val all: Boolean, val hour: Int? = null, val minute: Int? = null) : Intent()
        object CancelTimers : Intent()
        object ListAlarms : Intent()
        object StopRinging : Intent()
        object Snooze : Intent()
        object Time : Intent()
        object Date : Intent()
        object Weather : Intent()
        data class Volume(val percent: Int?, val delta: Int) : Intent()
        object StopTalking : Intent()
    }

    fun parse(raw: String): Intent? {
        val t = norm(raw)
        if (t.isBlank()) return null

        if (Regex("^(para|basta|silencio|calla(te)?|stop|cancela|nada|olvidalo|da igual)$").matches(t)) {
            return Intent.StopTalking
        }
        if (Regex("\\bpospon|\\bdormir (un poco|cinco|diez) mas|\\bsnooze").containsMatchIn(t)) return Intent.Snooze
        if (Regex("^(apaga|para|quita|desactiva)( la)? (alarma|el despertador)$").matches(t) ||
            Regex("^(apagala|ya estoy despiert[oa]|ya me he despertado)$").matches(t)
        ) return Intent.StopRinging

        // Temporizadores
        val timerWord = Regex("\\b(temporizador|cuenta atras|timer|avisame en|recuerdame en|despiertame en|pon(me)? un aviso en)\\b")
        if (Regex("\\b(cancela|quita|borra|para|elimina|detén|deten)\\b.*\\b(temporizador|temporizadores|cuenta atras)").containsMatchIn(t)) {
            return Intent.CancelTimers
        }
        if (timerWord.containsMatchIn(t)) {
            val secs = parseDuration(t)
            if (secs != null && secs > 0) {
                val label = Regex("\\bpara (el |la |los |las )?(.+)$").find(t)?.groupValues?.get(2)
                    ?.takeIf { parseDuration(it) == null }?.replaceFirstChar { it.uppercase() } ?: "Temporizador"
                return Intent.SetTimer(secs, label)
            }
        }

        // Alarmas
        val mentionsAlarm = Regex("\\b(alarma|alarmas|despertador|despiertame|despertarme|levantame)\\b").containsMatchIn(t)
        if (mentionsAlarm && Regex("\\b(cancela|quita|borra|elimina|desactiva|anula)").containsMatchIn(t)) {
            val all = Regex("\\b(todas|las alarmas)\\b").containsMatchIn(t)
            val tm = parseClock(t)
            return Intent.CancelAlarm(all = all && tm == null, hour = tm?.first, minute = tm?.second)
        }
        if (mentionsAlarm && Regex("\\b(que alarmas|mis alarmas|cuales|tengo alguna|a que hora)").containsMatchIn(t) &&
            !Regex("\\b(pon|ponme|programa|crea|activa)\\b").containsMatchIn(t)
        ) return Intent.ListAlarms
        if (mentionsAlarm) {
            val tm = parseClock(t)
            if (tm != null) {
                val label = Regex("\\b(para|que diga|llamada|con el nombre) (.+)$").find(t)?.groupValues?.get(2)
                    ?.takeIf { parseClock(it) == null && !Regex("^(los|el|el dia|todos)").containsMatchIn(it) }
                    ?.replaceFirstChar { it.uppercase() } ?: "Alarma"
                return Intent.SetAlarm(tm.first, tm.second, parseDays(t), label)
            }
        }

        if (Regex("\\bque hora (es|son)\\b|\\bdime la hora\\b|^la hora$").containsMatchIn(t)) return Intent.Time
        if (Regex("\\b(que dia|que fecha|a que estamos|a cuanto estamos)\\b").containsMatchIn(t)) return Intent.Date
        if (Regex("\\b(que tiempo|el tiempo|temperatura|va a llover|llueve|hace frio|hace calor|prevision)\\b").containsMatchIn(t) &&
            !Regex("\\b(manana|pasado|semana|fin de semana|en [a-z]+ (hace|va))\\b").containsMatchIn(t)
        ) return Intent.Weather

        if (Regex("\\bvolumen\\b").containsMatchIn(t)) {
            val n = Regex("(\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: wordsToNumber(t.substringAfter("volumen"))
            return when {
                n != null && Regex("\\b(al|a|en)\\b").containsMatchIn(t) -> Intent.Volume(n.coerceIn(0, 100), 0)
                Regex("\\b(sube|subir|mas alto|mas)\\b").containsMatchIn(t) -> Intent.Volume(null, +15)
                Regex("\\b(baja|bajar|mas bajo|menos)\\b").containsMatchIn(t) -> Intent.Volume(null, -15)
                else -> null
            }
        }
        return null
    }

    // ---------------------------------------------------------------- utilidades

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("(?<=\\d)[.h](?=\\d{2}\\b)"), ":") // "7.30" / "7h30" -> "7:30"
        .replace(Regex("[¿?¡!.,;]|:(?!\\d)"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val UNITS = mapOf(
        "cero" to 0, "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
        "trece" to 13, "catorce" to 14, "quince" to 15, "dieciseis" to 16, "diecisiete" to 17,
        "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20, "veintiun" to 21, "veintiuno" to 21, "veintiuna" to 21,
        "veintidos" to 22, "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26,
        "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29, "media" to 30,
    )
    private val TENS = mapOf("treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60, "noventa" to 90)

    /** Número al principio de [s] en cifras o en palabras ("cuarenta y cinco"). */
    fun wordsToNumber(s: String): Int? {
        val w = s.trim().split(' ').filter { it.isNotEmpty() }
        if (w.isEmpty()) return null
        w[0].toIntOrNull()?.let { return it }
        TENS[w[0]]?.let { tens ->
            if (w.size >= 3 && w[1] == "y") UNITS[w[2]]?.takeIf { it < 10 }?.let { return tens + it }
            return tens
        }
        return UNITS[w[0]]?.takeIf { w[0] != "media" }
    }

    /** "5 minutos", "una hora y media", "media hora", "90 segundos", "1 hora 20 minutos"... en segundos. */
    fun parseDuration(t: String): Long? {
        var total = 0L
        var found = false
        if (Regex("\\bmedia hora\\b").containsMatchIn(t)) { total += 1800; found = true }
        if (Regex("\\bun cuarto de hora\\b").containsMatchIn(t)) { total += 900; found = true }
        val re = Regex("((?:\\d+)|(?:[a-z]+(?: y [a-z]+)?)) (horas?|minutos?|mins?|segundos?|segs?)\\b")
        for (m in re.findAll(t)) {
            val n = wordsToNumber(m.groupValues[1]) ?: wordsToNumber(m.groupValues[1].substringAfterLast(' ')) ?: continue
            val unit = m.groupValues[2]
            total += n * when {
                unit.startsWith("h") -> 3600L
                unit.startsWith("m") -> 60L
                else -> 1L
            }
            if (unit.startsWith("hora") && Regex("${Regex.escape(m.value)} y media").containsMatchIn(t)) total += 1800
            found = true
        }
        return if (found) total else null
    }

    /**
     * Hora de reloj: "7:30", "a las siete y media", "a las ocho menos cuarto",
     * "a las 10 de la noche", "a las seis y veinte de la mañana".
     */
    fun parseClock(t: String): Pair<Int, Int>? {
        Regex("\\b(\\d{1,2})[:h.](\\d{2})\\b").find(t)?.let { m ->
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            if (h in 0..23 && mi in 0..59) return adjustPeriod(h, mi, t)
        }
        val m = Regex("\\b(?:a las|a la|para las|para la)\\s+(.+)").find(t) ?: return null
        val rest = m.groupValues[1]
        val h = wordsToNumber(rest) ?: return null
        if (h !in 0..24) return null
        var minute = 0
        val after = rest.split(' ').drop(1).joinToString(" ")
        when {
            after.startsWith("y media") -> minute = 30
            after.startsWith("y cuarto") -> minute = 15
            after.startsWith("menos cuarto") -> return adjustPeriod((h + 23) % 24, 45, t)
            after.startsWith("menos ") -> {
                val n = wordsToNumber(after.removePrefix("menos ")) ?: 0
                return adjustPeriod((h + 23) % 24, (60 - n).coerceIn(0, 59), t)
            }
            after.startsWith("y ") -> minute = wordsToNumber(after.removePrefix("y "))?.takeIf { it in 0..59 } ?: 0
            after.startsWith("en punto") -> minute = 0
            else -> wordsToNumber(after)?.takeIf { it in 0..59 && !after.startsWith("de ") }?.let { minute = it }
        }
        return adjustPeriod(h % 24, minute, t)
    }

    private fun adjustPeriod(h: Int, m: Int, t: String): Pair<Int, Int> {
        var hour = h
        if (hour in 1..11 && Regex("\\bde la (tarde|noche)\\b|\\bpm\\b").containsMatchIn(t)) hour += 12
        if (hour == 12 && Regex("\\bde la (noche|madrugada)\\b").containsMatchIn(t)) hour = 0
        return hour to m
    }

    /** Días: "todos los días", "entre semana", "los lunes y miércoles", "fines de semana". */
    fun parseDays(t: String): Int {
        if (Regex("\\b(todos los dias|cada dia|diaria|a diario)\\b").containsMatchIn(t)) return 0b1111111
        if (Regex("\\b(entre semana|de lunes a viernes|dias laborables|laborables)\\b").containsMatchIn(t)) return 0b0011111
        if (Regex("\\b(fines? de semana|sabados y domingos)\\b").containsMatchIn(t)) return 0b1100000
        val names = listOf("lunes", "martes", "miercoles", "jueves", "viernes", "sabados?", "domingos?")
        if (!Regex("\\b(los|cada|todos los)\\b").containsMatchIn(t)) return 0
        var d = 0
        names.forEachIndexed { i, n -> if (Regex("\\b$n\\b").containsMatchIn(t)) d = d or (1 shl i) }
        return d
    }
}
