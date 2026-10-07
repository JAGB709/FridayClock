package com.jarvis.clock

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ajustes que se pueden leer y cambiar por la API (panel web, Hermes). Lista blanca:
 * token, puerto, bind y puente NO están, porque un error dejaría el reloj inaccesible.
 * La validación es pura (testeable en JVM); solo [apply] y [valuesJson] tocan Prefs.
 */
object SettingsSchema {

    enum class Type { INT, FLOAT, BOOL, STRING }

    class Field(
        val key: String,
        val type: Type,
        val group: String,
        val label: String,
        val min: Double = 0.0,
        val max: Double = 0.0,
        val maxLen: Int = 0,
        val get: () -> Any,
        val set: (Any) -> Unit,
    ) {
        fun problem(): String = when (type) {
            Type.INT -> "debe ser un entero entre ${min.toInt()} y ${max.toInt()}"
            Type.FLOAT -> "debe ser un número entre $min y $max"
            Type.BOOL -> "debe ser true o false"
            Type.STRING -> "debe ser un texto de 1 a $maxLen caracteres"
        }

        /** Valor normalizado (Int, Double, Boolean o String) o null si no vale. */
        fun coerce(raw: Any?): Any? = when (type) {
            Type.INT -> (raw as? Number)?.toDouble()?.takeIf { it % 1.0 == 0.0 && it in min..max }?.toInt()
            Type.FLOAT -> (raw as? Number)?.toDouble()?.takeIf { !it.isNaN() && it in min..max }
            Type.BOOL -> raw as? Boolean
            Type.STRING -> (raw as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= maxLen }
        }
    }

    private const val G_ALARM = "Despertador"
    private const val G_SCREEN = "Carátula de respaldo"
    private const val G_VOICE = "Voz y asistente"
    private const val G_PLACE = "Ubicación"

    private fun int(key: String, group: String, label: String, min: Int, max: Int, get: () -> Int, set: (Int) -> Unit) =
        Field(key, Type.INT, group, label, min.toDouble(), max.toDouble(), get = get, set = { set(it as Int) })

    private fun float(key: String, group: String, label: String, min: Double, max: Double, get: () -> Double, set: (Double) -> Unit) =
        Field(key, Type.FLOAT, group, label, min, max, get = get, set = { set(it as Double) })

    private fun bool(key: String, group: String, label: String, get: () -> Boolean, set: (Boolean) -> Unit) =
        Field(key, Type.BOOL, group, label, get = get, set = { set(it as Boolean) })

    private fun string(key: String, group: String, label: String, maxLen: Int, get: () -> String, set: (String) -> Unit) =
        Field(key, Type.STRING, group, label, maxLen = maxLen, get = get, set = { set(it as String) })

    val fields: List<Field> = listOf(
        int("snooze_min", G_ALARM, "Minutos al posponer", 1, 60, { Prefs.snoozeMinutes }, { Prefs.snoozeMinutes = it }),
        int("alarm_ramp_s", G_ALARM, "Segundos de subida de volumen", 0, 600, { Prefs.alarmRampSeconds }, { Prefs.alarmRampSeconds = it }),
        int("alarm_auto_snooze_min", G_ALARM, "Minutos sonando antes de posponer sola", 1, 60, { Prefs.alarmAutoSnoozeMinutes }, { Prefs.alarmAutoSnoozeMinutes = it }),
        bool("morning_briefing", G_ALARM, "Buenos días al descartar", { Prefs.morningBriefing }, { Prefs.morningBriefing = it }),
        int("night_start", G_SCREEN, "Modo noche desde (hora)", 0, 23, { Prefs.nightStartHour }, { Prefs.nightStartHour = it }),
        int("night_end", G_SCREEN, "Modo noche hasta (hora)", 0, 23, { Prefs.nightEndHour }, { Prefs.nightEndHour = it }),
        int("night_brightness", G_SCREEN, "Brillo de noche (%)", 1, 100, { Prefs.nightBrightness }, { Prefs.nightBrightness = it }),
        int("day_brightness", G_SCREEN, "Brillo de día (%)", 1, 100, { Prefs.dayBrightness }, { Prefs.dayBrightness = it }),
        string("assistant_name", G_VOICE, "Nombre del asistente", 30, { Prefs.assistantName }, { Prefs.assistantName = it }),
        float("tts_rate", G_VOICE, "Velocidad de voz", 0.5, 2.0, { Prefs.ttsRate.toDouble() }, { Prefs.ttsRate = it.toFloat() }),
        bool("wake_word", G_VOICE, "Activar al oír su nombre", { Prefs.wakeWord }, { Prefs.wakeWord = it }),
        bool("follow_up", G_VOICE, "Seguir escuchando si pregunta algo", { Prefs.followUp }, { Prefs.followUp = it }),
        string("city", G_PLACE, "Ciudad", 60, { Prefs.city }, { Prefs.city = it }),
        float("lat", G_PLACE, "Latitud", -90.0, 90.0, { Prefs.latitude }, { Prefs.latitude = it }),
        float("lon", G_PLACE, "Longitud", -180.0, 180.0, { Prefs.longitude }, { Prefs.longitude = it }),
    )

    private val byKey = fields.associateBy { it.key }

    sealed class Validation {
        data class Ok(val values: Map<String, Any>) : Validation()
        data class Invalid(val errors: Map<String, String>) : Validation()
    }

    /** Todo o nada: si una clave falla, no se devuelve ningún valor. */
    fun validate(patch: JSONObject): Validation {
        val values = LinkedHashMap<String, Any>()
        val errors = LinkedHashMap<String, String>()
        for (key in patch.keys()) {
            val f = byKey[key]
            if (f == null) { errors[key] = "ajuste desconocido"; continue }
            val v = f.coerce(patch.opt(key))
            if (v == null) errors[key] = f.problem() else values[key] = v
        }
        return if (errors.isEmpty()) Validation.Ok(values) else Validation.Invalid(errors)
    }

    fun apply(values: Map<String, Any>) = values.forEach { (k, v) -> byKey.getValue(k).set(v) }

    fun valuesJson(): JSONObject = JSONObject().apply { fields.forEach {
        val v = it.get()
        // Los Float de Prefs salen como 1.100000023841858: se redondea a 4 decimales.
        put(it.key, if (it.type == Type.FLOAT && v is Double) Math.round(v * 10_000) / 10_000.0 else v)
    } }

    fun schemaJson(): JSONArray = JSONArray().apply {
        fields.forEach { f ->
            put(JSONObject().apply {
                put("key", f.key); put("type", f.type.name.lowercase())
                put("group", f.group); put("label", f.label)
                when (f.type) {
                    Type.INT -> { put("min", f.min.toInt()); put("max", f.max.toInt()) }
                    Type.FLOAT -> { put("min", f.min); put("max", f.max) }
                    Type.STRING -> put("max_len", f.maxLen)
                    Type.BOOL -> Unit
                }
            })
        }
    }
}
