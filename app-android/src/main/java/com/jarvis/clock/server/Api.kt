package com.jarvis.clock.server

import android.content.Context
import com.jarvis.clock.BuildConfig
import com.jarvis.clock.Events
import com.jarvis.clock.Prefs
import com.jarvis.clock.alarm.AlarmRingService
import com.jarvis.clock.alarm.AlarmScheduler
import com.jarvis.clock.alarm.AlarmStore
import com.jarvis.clock.alarm.Stopwatch
import com.jarvis.clock.alarm.TimerBook
import com.jarvis.clock.alarm.Sounds
import com.jarvis.clock.assistant.Actions
import com.jarvis.clock.assistant.Assistant
import com.jarvis.clock.design.Backgrounds
import com.jarvis.clock.design.FaceData
import com.jarvis.clock.weather.Weather
import com.jarvis.clock.server.HttpServer.Request
import com.jarvis.clock.server.HttpServer.Response
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Endpoints HTTP del reloj. Todos (salvo /health) exigen el token. */
class Api(private val ctx: Context) {

    /** Comprobación previa a leer el cuerpo: null si puede seguir. */
    fun preAuth(r: Request): Response? {
        if (r.path == "/health") return null
        // Sin token configurado, el servidor no atiende nada (evita el reloj "abierto").
        if (Prefs.authToken.isEmpty()) {
            return Response.error(503, "reloj sin token configurado; ponlo en Ajustes o por ADB")
        }
        if (!authorized(r)) return Response.error(401, "token inválido")
        return null
    }

    fun handle(r: Request): Response {
        if (r.path == "/health") return Response.ok()
        preAuth(r)?.let { return it }
        if (r.method == "DELETE" && r.path.startsWith("/sounds/")) {
            val name = try {
                java.net.URLDecoder.decode(r.path.removePrefix("/sounds/"), "UTF-8")
            } catch (e: IllegalArgumentException) {
                return Response.error(400, "nombre no válido")
            }
            return if (Sounds.delete(ctx, name)) Response.ok() else Response.error(404, "no existe el sonido $name")
        }
        if (r.method in setOf("GET", "DELETE") && r.path.startsWith("/backgrounds/")) {
            val name = try {
                java.net.URLDecoder.decode(r.path.removePrefix("/backgrounds/"), "UTF-8")
            } catch (e: IllegalArgumentException) {
                return Response.error(400, "nombre no válido")
            }
            if (r.method == "DELETE") {
                return if (Backgrounds.delete(ctx, name)) Response.ok() else Response.error(404, "no existe el fondo $name")
            }
            val f = Backgrounds.fileFor(Backgrounds.dir(ctx), name) ?: return Response.error(404, "no existe el fondo $name")
            return Response(200, f.readBytes(), Backgrounds.mimeOf(f.name))
        }
        return when (r.method to r.path) {
            "GET" to "/status" -> Response.ok(status())
            "GET" to "/alarms" -> Response.ok(JSONObject().put("alarms", alarms()))
            "POST" to "/alarm" -> alarm(r.json())
            "POST" to "/timer" -> timer(r.json())
            "GET" to "/timers" -> Response.ok(timersState())
            "GET" to "/stopwatch" -> Response.ok(JSONObject().put("stopwatch", Stopwatch.json()))
            "POST" to "/stopwatch" -> stopwatch(r.json())
            "POST" to "/display" -> {
                val j = r.json()
                if (j.optString("action") == "clear") Events.post(Events.Event.DisplayClear) else Events.display(j)
                Response.ok()
            }
            "POST" to "/speak" -> {
                val text = r.json().optString("text")
                if (text.isBlank()) return Response.error(400, "falta text")
                Assistant.get(ctx).speak(text)
                Response.ok()
            }
            "POST" to "/ask" -> {
                val j = r.json()
                val text = j.optString("text")
                if (text.isBlank()) return Response.error(400, "falta text")
                val res = Assistant.get(ctx).ask(text, speak = j.optBoolean("speak", true))
                Response.ok(JSONObject().put("reply", res.reply))
            }
            "POST" to "/listen" -> { Assistant.get(ctx).listen(); Response.ok() }
            "POST" to "/briefing" -> { Assistant.get(ctx).briefing(); Response.ok() }
            "POST" to "/volume" -> Response.ok(JSONObject().put("volume", Actions.setVolume(ctx, r.json().optInt("level", 50), 0)))
            "POST" to "/bluetooth" -> Response.ok(Actions.bluetooth(ctx, r.json()))
            "POST" to "/brightness" -> {
                Events.post(Events.Event.Brightness(r.json().optInt("level", 50).coerceIn(1, 100)))
                Response.ok()
            }
            "POST" to "/actions" -> Response.ok(JSONObject().put("results", Actions.executeAll(ctx, r.json().optJSONArray("actions"))))
            "GET" to "/settings" -> result(Actions.execute(ctx, JSONObject().put("type", "get_settings")))
            "PUT" to "/settings" -> result(Actions.execute(ctx, JSONObject().put("type", "set_settings").put("settings", r.json())))
            "GET" to "/sounds" -> Response.ok(JSONObject().put("sounds", Sounds.list(ctx)))
            "POST" to "/sounds" -> try {
                Response.ok(JSONObject().put("id", Sounds.save(ctx, r.query["name"].orEmpty(), r.body)))
            } catch (e: IllegalArgumentException) {
                Response.error(400, e.message ?: "sonido no válido")
            }
            "POST" to "/sounds/preview" -> try {
                val id = r.json().optString("id").ifEmpty { null }
                if (Sounds.preview(ctx, id)) Response.ok() else Response.error(409, "hay una alarma sonando")
            } catch (e: IllegalArgumentException) {
                Response.error(400, e.message ?: "no se pudo reproducir")
            }
            "GET" to "/design" -> result(Actions.execute(ctx, JSONObject().put("type", "get_design")))
            "PUT" to "/design" -> result(Actions.execute(ctx, JSONObject().put("type", "set_design").put("design", r.json())))
            "POST" to "/design/reset" -> result(Actions.execute(ctx, JSONObject().put("type", "reset_design")))
            "POST" to "/design/preview" -> {
                if (AlarmRingService.ringing) Response.error(409, "hay una alarma sonando")
                else {
                    val j = r.json()
                    result(Actions.execute(ctx, JSONObject().put("type", "preview_design")
                        .put("design", j.optJSONObject("design")).put("seconds", j.optInt("seconds", 30))))
                }
            }
            "DELETE" to "/design/preview" -> { Events.post(Events.Event.DesignPreviewCancel); Response.ok() }
            "GET" to "/backgrounds" -> Response.ok(JSONObject().put("backgrounds", Backgrounds.list(ctx)))
            "POST" to "/backgrounds" -> try {
                Response.ok(JSONObject().put("name", Backgrounds.save(ctx, r.query["name"].orEmpty(), r.body)))
            } catch (e: IllegalArgumentException) {
                Response.error(400, e.message ?: "imagen no válida")
            }
            "GET" to "/face/data" -> { Weather.get(); Response.ok(FaceData.build(ctx)) }
            else -> Response.error(404, "no existe ${r.method} ${r.path}")
        }
    }

    private fun alarm(j: JSONObject): Response {
        val action = j.optString("action", "set")
        if (action == "list") return Response.ok(JSONObject().put("alarms", alarms()))
        val type = when (action) {
            "set" -> "set_alarm"
            "update" -> "update_alarm"
            "cancel", "delete" -> "cancel_alarm"
            "enable" -> "enable_alarm"
            "disable" -> "disable_alarm"
            "stop", "dismiss" -> "stop_alarm"
            "snooze" -> "snooze_alarm"
            else -> return Response.error(400, "action desconocida: $action")
        }
        if (action == "update" && AlarmStore.get(ctx, j.optInt("id", -1)) == null) {
            return Response.error(404, "no existe la alarma ${j.optInt("id")}")
        }
        // require()/error() de Actions y Alarm son errores del cliente, no del reloj.
        return try {
            result(Actions.execute(ctx, JSONObject(j.toString()).put("type", type)))
        } catch (e: IllegalArgumentException) {
            Response.error(400, e.message ?: "petición no válida")
        } catch (e: IllegalStateException) {
            Response.error(400, e.message ?: "petición no válida")
        }
    }

    private fun timersState() = JSONObject().put("timers", TimerBook.json(ctx)).put("now", System.currentTimeMillis())

    private fun stopwatch(j: JSONObject): Response {
        val action = j.optString("action", "status")
        if (action !in Stopwatch.ACTIONS) return Response.error(400, "action debe ser ${Stopwatch.ACTIONS.joinToString(", ")}")
        return result(Actions.execute(ctx, JSONObject().put("type", "stopwatch").put("action", action)))
    }

    private fun timer(j: JSONObject): Response {
        // pause/resume/cancel con id (o pid, para los pausados); cancel sin id cancela todos.
        val control = when (j.optString("action")) {
            "pause" -> "pause_timer"
            "resume" -> "resume_timer"
            "cancel" -> "cancel_timer"
            else -> null
        }
        if (control != null) {
            val a = JSONObject().put("type", control)
            for (k in listOf("id", "pid")) if (j.has(k)) a.put(k, j.optInt(k, -1))
            return try {
                result(Actions.execute(ctx, a))
            } catch (e: IllegalStateException) {
                Response.error(404, e.message ?: "no existe el temporizador")
            }
        }
        val secs = j.optLong("seconds", 0) + j.optLong("minutes", 0) * 60 + j.optLong("hours", 0) * 3600
        return result(Actions.execute(ctx, JSONObject().put("type", "set_timer").put("seconds", secs).put("label", j.optString("label", "Temporizador"))))
    }

    private fun result(o: JSONObject): Response =
        if (o.optBoolean("ok")) Response.json(200, o) else Response.json(400, o)

    private fun alarms() = JSONArray().apply { AlarmStore.all(ctx).forEach { put(it.toJson()) } }

    private fun status() = JSONObject().apply {
        put("version", BuildConfig.VERSION_NAME)
        put("face_version", FaceData.faceVersion(ctx))
        put("time", System.currentTimeMillis())
        put("ringing", AlarmRingService.ringing)
        AlarmRingService.currentLabel?.let { put("ringing_label", it) }
        put("assistant_state", Assistant.get(ctx).state.name.lowercase())
        put("backend_configured", Prefs.backendUrl.isNotEmpty())
        put("bluetooth", com.jarvis.clock.bt.BluetoothControl.status(ctx))
        AlarmScheduler.nextAlarm(ctx)?.let { (a, at) -> put("next_alarm", a.toJson().put("at", at)) }
        put("alarms", alarms())
        put("timers", TimerBook.json(ctx))
        put("stopwatch", Stopwatch.json())
    }

    private fun authorized(r: Request): Boolean {
        val given = r.header("X-Auth-Token")
            ?: r.header("Authorization")?.removePrefix("Bearer ")?.trim()
            ?: r.query["token"]
            ?: return false
        val expected = Prefs.authToken
        return expected.isNotEmpty() && MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())
    }
}
