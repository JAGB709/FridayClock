package com.jarvis.clock.assistant

import android.content.Context
import android.media.AudioManager
import com.jarvis.clock.Events
import com.jarvis.clock.SettingsSchema
import com.jarvis.clock.alarm.Alarm
import com.jarvis.clock.alarm.AlarmRingService
import com.jarvis.clock.alarm.AlarmScheduler
import com.jarvis.clock.alarm.AlarmStore
import com.jarvis.clock.bt.BluetoothControl
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Acciones que el reloj sabe ejecutar. Las usan tanto las órdenes locales
 * como el backend (el LLM devuelve una lista de acciones) y la API HTTP,
 * así que el comportamiento es idéntico venga de donde venga.
 */
object Actions {

    /** Ejecuta una lista de acciones del backend. Devuelve un resumen por acción. */
    fun executeAll(ctx: Context, actions: JSONArray?): JSONArray {
        val out = JSONArray()
        if (actions == null) return out
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: continue
            out.put(runCatching { execute(ctx, a) }.getOrElse { JSONObject().put("ok", false).put("error", it.message) })
        }
        return out
    }

    fun execute(ctx: Context, a: JSONObject): JSONObject = when (a.optString("type")) {
        "set_alarm" -> {
            val (h, m) = parseTime(a.optString("time")) ?: error("time debe ser HH:MM")
            val alarm = AlarmScheduler.create(ctx, h, m, Alarm.parseDays(a.opt("days")), a.optString("label", "Alarma"))
            ok().put("alarm", alarm.toJson())
        }
        "cancel_alarm", "delete_alarm" -> {
            val n = when {
                a.optBoolean("all") -> AlarmStore.all(ctx).filter { !it.isAbsolute }.count { AlarmScheduler.delete(ctx, it.id) }
                a.has("id") -> if (AlarmScheduler.delete(ctx, a.getInt("id"))) 1 else 0
                a.has("time") -> {
                    val (h, m) = parseTime(a.getString("time")) ?: error("time debe ser HH:MM")
                    AlarmStore.all(ctx).filter { !it.isAbsolute && it.hour == h && it.minute == m }
                        .count { AlarmScheduler.delete(ctx, it.id) }
                }
                else -> 0
            }
            ok().put("deleted", n)
        }
        "enable_alarm", "disable_alarm" -> {
            val r = AlarmScheduler.setEnabled(ctx, a.getInt("id"), a.optString("type") == "enable_alarm")
                ?: error("no existe la alarma ${a.optInt("id")}")
            ok().put("alarm", r.toJson())
        }
        "set_timer" -> {
            val secs = a.optLong("seconds").takeIf { it > 0 } ?: error("seconds > 0")
            ok().put("alarm", AlarmScheduler.createTimer(ctx, secs, a.optString("label", "Temporizador")).toJson())
        }
        "cancel_timer" -> ok().put("deleted", cancelTimers(ctx))
        "stop_alarm" -> { AlarmRingService.dismiss(ctx); ok() }
        "snooze_alarm" -> { AlarmRingService.snooze(ctx); ok() }
        "display" -> { Events.display(a); ok() }
        "clear_display" -> { Events.post(Events.Event.DisplayClear); ok() }
        "set_volume" -> ok().put("volume", setVolume(ctx, a.optInt("level", 50), 0))
        "set_brightness" -> { Events.post(Events.Event.Brightness(a.optInt("level", 50).coerceIn(1, 100))); ok() }
        "bluetooth" -> bluetooth(ctx, a)
        "get_settings" -> ok().put("settings", SettingsSchema.valuesJson()).put("schema", SettingsSchema.schemaJson())
        "set_settings" -> when (val v = SettingsSchema.validate(a.optJSONObject("settings") ?: JSONObject())) {
            is SettingsSchema.Validation.Invalid ->
                JSONObject().put("ok", false).put("error", "ajustes no válidos").put("errors", JSONObject(v.errors))
            is SettingsSchema.Validation.Ok -> {
                SettingsSchema.apply(v.values)
                if (v.values.isNotEmpty()) Events.post(Events.Event.SettingsChanged(v.values.keys))
                ok().put("settings", SettingsSchema.valuesJson())
            }
        }
        else -> JSONObject().put("ok", false).put("error", "acción desconocida: ${a.optString("type")}")
    }

    /** Control del Bluetooth (modo altavoz). action: on|off|speaker|status. */
    fun bluetooth(ctx: Context, a: JSONObject): JSONObject = when (a.optString("action", "speaker")) {
        "on" -> ok().put("enabled", BluetoothControl.enable(ctx)).put("bt", BluetoothControl.status(ctx))
        "off" -> ok().put("enabled", !BluetoothControl.disable(ctx)).put("bt", BluetoothControl.status(ctx))
        "status" -> ok().put("bt", BluetoothControl.status(ctx))
        "speaker", "pair", "discoverable" -> {
            val secs = a.optInt("seconds", 300).coerceIn(30, 3600)
            val r = BluetoothControl.speakerMode(ctx, secs)
            if (r.needsUi) Events.post(Events.Event.BluetoothDiscoverable(secs))
            JSONObject().put("ok", true).put("message", r.message)
                .put("needs_confirmation", r.needsUi).put("bt", BluetoothControl.status(ctx))
        }
        else -> JSONObject().put("ok", false).put("error", "action BT desconocida")
    }

    fun cancelTimers(ctx: Context): Int =
        AlarmStore.all(ctx).filter { it.kind == Alarm.Kind.TIMER }.count { AlarmScheduler.delete(ctx, it.id) }

    /** Volumen de voz/multimedia en porcentaje. [delta] != 0 -> relativo. */
    fun setVolume(ctx: Context, percent: Int, delta: Int): Int {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
        val target = (if (delta != 0) cur + delta else percent).coerceIn(0, 100)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (target / 100f * max).roundToInt(), 0)
        return target
    }

    fun parseTime(s: String): Pair<Int, Int>? = Alarm.parseTime(s)

    private fun ok() = JSONObject().put("ok", true)
}
