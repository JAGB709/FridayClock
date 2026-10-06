package com.jarvis.clock

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Bus de eventos mínimo dentro del proceso (sin LocalBroadcastManager ni
 * librerías). Los listeners siempre se llaman en el hilo principal.
 */
object Events {

    sealed class Event {
        /** Mostrar contenido a pantalla completa (endpoint /display o acción del asistente). */
        data class Display(val type: String, val content: String, val durationMs: Long) : Event()
        object DisplayClear : Event()
        /** Estado del asistente para la UI. */
        data class AssistantState(val state: State, val text: String = "") : Event()
        data class MicLevel(val level: Float) : Event()
        object AlarmsChanged : Event()
        data class AlarmRinging(val alarmId: Int, val label: String) : Event()
        object AlarmStopped : Event()
        data class Brightness(val percent: Int) : Event()
        data class BluetoothDiscoverable(val seconds: Int) : Event()
        /** Ajustes cambiados por la API: la UI vuelve a aplicar lo que dependa de ellos. */
        data class SettingsChanged(val keys: Set<String>) : Event()
        /** El diseño de pantallas guardado cambió (o se restauró). */
        object DesignChanged : Event()
        /** Mostrar un diseño sin guardarlo durante [seconds] segundos. */
        data class DesignPreview(val design: JSONObject, val seconds: Int) : Event()
        object DesignPreviewCancel : Event()
    }

    enum class State { IDLE, LISTENING, THINKING, SPEAKING, HEARD, ERROR }

    fun interface Listener {
        fun onEvent(e: Event)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val main = Handler(Looper.getMainLooper())

    fun register(l: Listener) = listeners.add(l)
    fun unregister(l: Listener) = listeners.remove(l)

    fun post(e: Event) {
        if (Looper.myLooper() == Looper.getMainLooper()) dispatch(e) else main.post { dispatch(e) }
    }

    private fun dispatch(e: Event) {
        for (l in listeners) l.onEvent(e)
    }

    fun display(json: JSONObject) {
        post(
            Event.Display(
                type = json.optString("type", "text"),
                content = json.optString("content", ""),
                durationMs = json.optLong("duration_ms", 15_000L).coerceIn(1_000L, 3_600_000L),
            )
        )
    }
}
