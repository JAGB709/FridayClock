package com.jarvis.clock.design

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import com.jarvis.clock.alarm.AlarmScheduler
import com.jarvis.clock.weather.Weather
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Calendar

/** Los datos que recibe el motor de pantallas (los mismos en el reloj y en la vista previa del panel). */
object FaceData {
    private val DAY_SHORT = arrayOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")
    private val FILES = listOf("index.html", "face.css", "face-core.js", "face.js")

    private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** "hoy", "mañana" o el día de la semana abreviado. */
    fun dayWord(nowMs: Long, atMs: Long): String {
        val diff = Math.round((startOfDay(atMs) - startOfDay(nowMs)) / 86_400_000.0).toInt()
        return when (diff) {
            0 -> "hoy"
            1 -> "mañana"
            else -> DAY_SHORT[Calendar.getInstance().apply { timeInMillis = atMs }.get(Calendar.DAY_OF_WEEK) - 1]
        }
    }

    fun nextAlarmJson(label: String, timeText: String, nowMs: Long, atMs: Long): JSONObject =
        JSONObject().put("label", label).put("when", dayWord(nowMs, atMs)).put("time", timeText)

    fun weatherJson(r: Weather.Report?): Any =
        if (r == null) JSONObject.NULL else JSONObject()
            .put("temp", r.temp).put("icon", r.icon).put("description", r.description)
            .put("max", r.max).put("min", r.min).put("rain", r.rainProb)

    fun forecastJson(r: Weather.Report?): Any =
        if (r == null) JSONObject.NULL else JSONObject()
            .put("hours", JSONArray(r.hours.map { JSONObject().put("h", it.h).put("temp", it.temp).put("icon", it.icon) }))
            .put("days", JSONArray(r.days.map { JSONObject().put("d", it.d).put("min", it.min).put("max", it.max).put("icon", it.icon) }))

    /** SHA-256 de los archivos del motor, abreviado: el panel calcula el mismo para avisar si difieren. */
    fun versionOf(chunks: List<ByteArray>): String {
        val md = MessageDigest.getInstance("SHA-256")
        chunks.forEach { md.update(it) }
        return md.digest().joinToString("") { "%02x".format(it) }.take(12)
    }

    @Volatile private var version: String? = null

    fun faceVersion(ctx: Context): String =
        version ?: runCatching {
            versionOf(FILES.map { name -> ctx.assets.open("face/$name").use { it.readBytes() } })
        }.getOrDefault("?").also { version = it }

    /** Datos actuales: tiempo y previsión de la caché, próxima alarma y si falta el permiso de alarmas exactas. */
    fun build(ctx: Context, ringingLabel: String? = null): JSONObject {
        val now = System.currentTimeMillis()
        val w = Weather.cached()
        val next = AlarmScheduler.nextAlarm(ctx)
        val exactMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val o = JSONObject()
            .put("weather", weatherJson(w))
            .put("forecast", forecastJson(w))
            .put("next_alarm", next?.let { (a, at) -> nextAlarmJson(a.label, a.timeText(), now, at) } ?: JSONObject.NULL)
            .put("exact_alarm_missing", exactMissing)
        if (ringingLabel != null) o.put("ringing_label", ringingLabel)
        return o
    }
}
