package com.jarvis.clock.alarm

import com.jarvis.clock.Events
import org.json.JSONObject

/**
 * Estado puro del cronómetro: tiempo acumulado más, si corre, lo transcurrido desde `since`.
 * Vive en memoria (un reinicio de la app lo pone a cero) y se pinta con tiempos absolutos.
 */
class StopwatchState {
    var running = false; private set
    var baseMs = 0L; private set
    var since = 0L; private set

    fun elapsed(now: Long): Long = baseMs + if (running) (now - since).coerceAtLeast(0) else 0

    fun start(now: Long) { if (!running) { running = true; since = now } }

    fun pause(now: Long) { if (running) { baseMs = elapsed(now); running = false; since = 0 } }

    fun reset(now: Long) { baseMs = 0; since = if (running) now else 0 }

    fun toggle(now: Long) { if (running) pause(now) else start(now) }

    fun toJson(now: Long): JSONObject =
        JSONObject().put("running", running).put("base_ms", baseMs).put("since", since).put("elapsed_ms", elapsed(now))
}

/** El cronómetro del reloj: lo mueven la API, el asistente y los botones de la carátula. */
object Stopwatch {
    private val state = StopwatchState()

    val ACTIONS = setOf("start", "pause", "reset", "toggle", "status")

    @Synchronized fun apply(action: String, now: Long = System.currentTimeMillis()): JSONObject {
        require(action in ACTIONS) { "action debe ser ${ACTIONS.joinToString(", ")}" }
        when (action) {
            "start" -> state.start(now)
            "pause" -> state.pause(now)
            "reset" -> state.reset(now)
            "toggle" -> state.toggle(now)
        }
        if (action != "status") Events.post(Events.Event.AlarmsChanged) // repinta la carátula
        return state.toJson(now)
    }

    @Synchronized fun json(now: Long = System.currentTimeMillis()): JSONObject = state.toJson(now)
}
