package com.jarvis.clock.assistant

import android.content.Context
import android.util.Base64
import com.jarvis.clock.Prefs
import com.jarvis.clock.alarm.AlarmStore
import com.jarvis.clock.weather.Weather
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Cliente del puente del homelab (homelab/clock-bridge en el repo), que hace de
 * oído (Whisper local) y de boca (Piper local) y pasa el texto a Hermes.
 *
 *   POST {backend}/v1/stt     body: audio/wav              -> { "text" }
 *   POST {backend}/v1/assist  { "text"?, "audio_b64"?, "mode": "chat"|"briefing", "context" }
 *                             -> { "transcript", "reply", "actions"?, "audio_b64"?, "expect_reply"? }
 */
class Backend(private val ctx: Context) {

    data class Reply(
        val transcript: String,
        val reply: String,
        val actions: JSONArray?,
        val audio: ByteArray?,
        val expectReply: Boolean,
    )

    val configured get() = Prefs.backendUrl.startsWith("http")

    fun assist(text: String? = null, wav: ByteArray? = null, mode: String = "chat"): Reply {
        val body = JSONObject().apply {
            put("mode", mode)
            put("device", "mi-smart-clock")
            text?.let { put("text", it) }
            wav?.let { put("audio_b64", Base64.encodeToString(it, Base64.NO_WRAP)) }
            put("context", context())
        }
        val o = post("/v1/assist", body)
        return Reply(
            transcript = o.optString("transcript", text ?: ""),
            reply = o.optString("reply", ""),
            actions = o.optJSONArray("actions"),
            audio = o.optString("audio_b64", "").takeIf { it.isNotEmpty() }?.let { Base64.decode(it, Base64.DEFAULT) },
            expectReply = o.optBoolean("expect_reply", false),
        )
    }

    /** Voz -> texto (Whisper en el homelab). */
    fun transcribe(wav: ByteArray): String =
        request("/v1/stt", wav, "audio/wav").optString("text").trim()

    data class Wake(val called: Boolean, val text: String, val command: String)

    /** Escucha continua: ¿este fragmento de voz llama a Friday? Devuelve además la orden que lo acompaña. */
    fun wake(wav: ByteArray): Wake {
        val o = request("/v1/wake", wav, "audio/wav", readTimeoutMs = 20_000)
        return Wake(o.optBoolean("wake"), o.optString("text"), o.optString("command").trim())
    }

    /** Estado del reloj que el LLM necesita para razonar ("¿a qué hora suena mañana?"). */
    fun context(): JSONObject {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
        return JSONObject().apply {
            put("now", iso)
            put("weekday", SimpleDateFormat("EEEE", Locale("es", "ES")).format(Date()))
            put("timezone", TimeZone.getDefault().id)
            put("assistant_name", Prefs.assistantName)
            put("location", JSONObject().put("city", Prefs.city).put("lat", Prefs.latitude).put("lon", Prefs.longitude))
            put("alarms", JSONArray().apply { AlarmStore.all(ctx).forEach { put(it.toJson()) } })
            Weather.cached()?.let {
                put("weather", JSONObject().put("temp", it.temp).put("description", it.description)
                    .put("max", it.max).put("min", it.min).put("rain_prob", it.rainProb))
            }
        }
    }

    private fun post(path: String, body: JSONObject): JSONObject =
        request(path, body.toString().toByteArray(), "application/json; charset=utf-8")

    private fun request(path: String, body: ByteArray, contentType: String, readTimeoutMs: Int = 90_000): JSONObject {
        if (!CleartextPolicy.allowed(Prefs.backendUrl)) {
            error("URL del puente no permitida: usa https o una dirección de Tailscale/LAN")
        }
        val c = URL(Prefs.backendUrl + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 5_000
            c.readTimeout = readTimeoutMs // Hermes puede tardar si usa herramientas
            c.doOutput = true
            c.setFixedLengthStreamingMode(body.size)
            c.setRequestProperty("Content-Type", contentType)
            if (Prefs.backendToken.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer ${Prefs.backendToken}")
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val txt = stream?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) error("backend HTTP $code: ${txt.take(200)}")
            return JSONObject(txt)
        } finally {
            c.disconnect()
        }
    }
}
