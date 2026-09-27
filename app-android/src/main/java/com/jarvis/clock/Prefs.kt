package com.jarvis.clock

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/** Ajustes persistentes. Todo lo que antes estaba hardcodeado vive aquí. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (sp.getString(K_TOKEN, null).isNullOrEmpty()) {
            // Token aleatorio en el primer arranque: nunca "changeme".
            sp.edit().putString(K_TOKEN, randomToken()).apply()
        }
    }

    /** Token que deben enviar los clientes al servidor HTTP del reloj. */
    var authToken: String
        get() = sp.getString(K_TOKEN, "")!!
        set(v) = sp.edit().putString(K_TOKEN, v.trim()).apply()

    /** Enlazar el servidor solo a la IP de Tailscale si existe (si no, a todas con token). */
    var bindTailscaleOnly: Boolean
        get() = sp.getBoolean("bind_ts_only", false)
        set(v) = sp.edit().putBoolean("bind_ts_only", v).apply()

    var httpPort: Int
        get() = sp.getInt("http_port", 8080)
        set(v) = sp.edit().putInt("http_port", v).apply()

    /** URL base del backend del asistente en el homelab, p.ej. http://100.x.y.z:8765 */
    var backendUrl: String
        get() = sp.getString("backend_url", "")!!
        set(v) = sp.edit().putString("backend_url", v.trim().trimEnd('/')).apply()

    var backendToken: String
        get() = sp.getString("backend_token", "")!!
        set(v) = sp.edit().putString("backend_token", v.trim()).apply()

    var assistantName: String
        get() = sp.getString("assistant_name", "Friday")!!
        set(v) = sp.edit().putString("assistant_name", v.trim().ifEmpty { "Friday" }).apply()

    var city: String
        get() = sp.getString("city", "Madrid")!!
        set(v) = sp.edit().putString("city", v.trim()).apply()

    var latitude: Double
        get() = sp.getFloat("lat", 40.4168f).toDouble()
        set(v) = sp.edit().putFloat("lat", v.toFloat()).apply()

    var longitude: Double
        get() = sp.getFloat("lon", -3.7038f).toDouble()
        set(v) = sp.edit().putFloat("lon", v.toFloat()).apply()

    var snoozeMinutes: Int
        get() = sp.getInt("snooze_min", 5)
        set(v) = sp.edit().putInt("snooze_min", v.coerceIn(1, 60)).apply()

    /** Segundos que tarda la alarma en subir de volumen mínimo a máximo. */
    var alarmRampSeconds: Int
        get() = sp.getInt("alarm_ramp_s", 45)
        set(v) = sp.edit().putInt("alarm_ramp_s", v.coerceIn(0, 600)).apply()

    /** Minutos que suena la alarma antes de posponerse sola. */
    var alarmAutoSnoozeMinutes: Int
        get() = sp.getInt("alarm_auto_snooze_min", 10)
        set(v) = sp.edit().putInt("alarm_auto_snooze_min", v.coerceIn(1, 60)).apply()

    /** Al descartar la alarma, el asistente da los buenos días con hora y tiempo. */
    var morningBriefing: Boolean
        get() = sp.getBoolean("morning_briefing", true)
        set(v) = sp.edit().putBoolean("morning_briefing", v).apply()

    /** Hora (0-23) a la que empieza el modo noche (pantalla tenue y roja). */
    var nightStartHour: Int
        get() = sp.getInt("night_start", 23)
        set(v) = sp.edit().putInt("night_start", v.coerceIn(0, 23)).apply()

    var nightEndHour: Int
        get() = sp.getInt("night_end", 7)
        set(v) = sp.edit().putInt("night_end", v.coerceIn(0, 23)).apply()

    /** Brillo en modo noche, 1-100. */
    var nightBrightness: Int
        get() = sp.getInt("night_brightness", 3)
        set(v) = sp.edit().putInt("night_brightness", v.coerceIn(1, 100)).apply()

    var dayBrightness: Int
        get() = sp.getInt("day_brightness", 70)
        set(v) = sp.edit().putInt("day_brightness", v.coerceIn(1, 100)).apply()

    /** Tras una respuesta que acaba en pregunta, vuelve a escuchar sin pulsar. */
    var followUp: Boolean
        get() = sp.getBoolean("follow_up", true)
        set(v) = sp.edit().putBoolean("follow_up", v).apply()

    var ttsRate: Float
        get() = sp.getFloat("tts_rate", 1.0f)
        set(v) = sp.edit().putFloat("tts_rate", v.coerceIn(0.5f, 2f)).apply()

    fun isNight(hour: Int): Boolean {
        val s = nightStartHour
        val e = nightEndHour
        return if (s == e) false else if (s < e) hour in s until e else hour >= s || hour < e
    }

    private fun randomToken(): String {
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        return (1..20).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
    }

    private const val K_TOKEN = "auth_token"
}
