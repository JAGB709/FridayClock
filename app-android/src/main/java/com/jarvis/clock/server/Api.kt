package com.jarvis.clock.server

import android.content.Context
import com.jarvis.clock.BuildConfig
import com.jarvis.clock.Events
import com.jarvis.clock.Prefs
import com.jarvis.clock.alarm.AlarmRingService
import com.jarvis.clock.alarm.AlarmScheduler
import com.jarvis.clock.alarm.AlarmStore
import com.jarvis.clock.alarm.Sounds
import com.jarvis.clock.assistant.Actions
import com.jarvis.clock.assistant.Assistant
import com.jarvis.clock.server.HttpServer.Request
import com.jarvis.clock.server.HttpServer.Response
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Endpoints HTTP del reloj. Todos (salvo /health) exigen el token. */
class Api(private val ctx: Context) {

    fun handle(r: Request): Response {
        if (r.path == "/health") return Response.ok()
        // Sin token configurado, el servidor no atiende nada (evita el reloj "abierto").
        if (Prefs.authToken.isEmpty()) {
            return Response.error(503, "reloj sin token configurado; ponlo en Ajustes o por ADB")
        }
        if (!authorized(r)) return Response.error(401, "token inválido")
        if (r.method == "DELETE" && r.path.startsWith("/sounds/")) {
            val name = try {
                java.net.URLDecoder.decode(r.path.removePrefix("/sounds/"), "UTF-8")
            } catch (e: IllegalArgumentException) {
                return Response.error(400, "nombre no válido")
            }
            return if (Sounds.delete(ctx, name)) Response.ok() else Response.error(404, "no existe el sonido $name")
        }
        return when (r.method to r.path) {
            "GET" to "/status" -> Response.ok(status())
            "GET" to "/alarms" -> Response.ok(JSONObject().put("alarms", alarms()))
            "POST" to "/alarm" -> alarm(r.json())
            "POST" to "/timer" -> timer(r.json())
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

    private fun timer(j: JSONObject): Response {
        if (j.optString("action") == "cancel") return result(Actions.execute(ctx, JSONObject().put("type", "cancel_timer")))
        val secs = j.optLong("seconds", 0) + j.optLong("minutes", 0) * 60 + j.optLong("hours", 0) * 3600
        return result(Actions.execute(ctx, JSONObject().put("type", "set_timer").put("seconds", secs).put("label", j.optString("label", "Temporizador"))))
    }

    private fun result(o: JSONObject): Response =
        if (o.optBoolean("ok")) Response.json(200, o) else Response.json(400, o)

    private fun alarms() = JSONArray().apply { AlarmStore.all(ctx).forEach { put(it.toJson()) } }

    private fun status() = JSONObject().apply {
        put("version", BuildConfig.VERSION_NAME)
        put("time", System.currentTimeMillis())
        put("ringing", AlarmRingService.ringing)
        AlarmRingService.currentLabel?.let { put("ringing_label", it) }
        put("assistant_state", Assistant.get(ctx).state.name.lowercase())
        put("backend_configured", Prefs.backendUrl.isNotEmpty())
        put("bluetooth", com.jarvis.clock.bt.BluetoothControl.status(ctx))
        AlarmScheduler.nextAlarm(ctx)?.let { (a, at) -> put("next_alarm", a.toJson().put("at", at)) }
        put("alarms", alarms())
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
