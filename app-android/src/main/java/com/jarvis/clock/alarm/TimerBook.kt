package com.jarvis.clock.alarm

import android.content.Context
import com.jarvis.clock.Events
import org.json.JSONArray
import org.json.JSONObject

/**
 * Temporizadores pausados. Un temporizador en marcha es una alarma absoluta (AlarmManager); al
 * pausarlo se borra esa alarma y aquí queda lo que faltaba. Al reanudarlo se crea una nueva.
 * Los pausados viven en memoria: si la app se reinicia se pierden (los que corren no).
 */
object TimerBook {
    const val MAX_PAUSED = 5

    data class Paused(val pid: Int, val label: String, val remainingMs: Long)

    private val paused = ArrayList<Paused>()
    private var nextPid = 1

    /** Pausa el temporizador [id]; null si no existe o no es un temporizador. */
    @Synchronized fun pause(ctx: Context, id: Int, now: Long = System.currentTimeMillis()): Paused? {
        val a = AlarmStore.get(ctx, id) ?: return null
        if (a.kind != Alarm.Kind.TIMER) return null
        require(paused.size < MAX_PAUSED) { "hay demasiados temporizadores en pausa" }
        val p = Paused(nextPid++, a.label, (a.triggerAt - now).coerceAtLeast(1000))
        AlarmScheduler.delete(ctx, id)
        paused.add(p)
        Events.post(Events.Event.AlarmsChanged)
        return p
    }

    /** Reanuda el pausado [pid] creando un temporizador con lo que faltaba. */
    @Synchronized fun resume(ctx: Context, pid: Int): Alarm? {
        val p = paused.firstOrNull { it.pid == pid } ?: return null
        paused.remove(p)
        return AlarmScheduler.createTimer(ctx, (p.remainingMs + 999) / 1000, p.label)
    }

    @Synchronized fun cancelPaused(pid: Int): Boolean {
        val ok = paused.removeAll { it.pid == pid }
        if (ok) Events.post(Events.Event.AlarmsChanged)
        return ok
    }

    @Synchronized fun clearPaused(): Int = paused.size.also { paused.clear() }

    /** Temporizadores para la carátula: los que corren (con `at`) y los pausados (con `remaining_ms`). */
    @Synchronized fun json(ctx: Context): JSONArray {
        val out = JSONArray()
        AlarmStore.all(ctx).filter { it.kind == Alarm.Kind.TIMER && it.isAbsolute }.sortedBy { it.triggerAt }.forEach {
            out.put(JSONObject().put("id", it.id.toString()).put("label", it.label).put("at", it.triggerAt).put("paused", false))
        }
        paused.forEach {
            out.put(JSONObject().put("id", "p${it.pid}").put("label", it.label).put("remaining_ms", it.remainingMs).put("paused", true))
        }
        return out
    }
}
