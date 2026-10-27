package com.jarvis.clock.design

import org.json.JSONObject

/**
 * Órdenes que el motor de pantallas puede enviar al reloj al tocar un botón (cronómetro y
 * temporizadores). Lista cerrada: cualquier otra cosa se descarta. Devuelve la acción de
 * [com.jarvis.clock.assistant.Actions] equivalente.
 */
object FaceControl {
    private val ID = Regex("^p?\\d{1,9}$")

    fun parse(cmd: String): JSONObject? {
        val parts = cmd.split(":")
        return when {
            cmd == "stopwatch:toggle" -> JSONObject().put("type", "stopwatch").put("action", "toggle")
            cmd == "stopwatch:reset" -> JSONObject().put("type", "stopwatch").put("action", "reset")
            parts.size == 3 && parts[0] == "timer" && ID.matches(parts[2]) -> {
                val isPaused = parts[2].startsWith("p")
                val n = parts[2].removePrefix("p").toInt()
                when (parts[1]) {
                    "pause" -> if (isPaused) null else JSONObject().put("type", "pause_timer").put("id", n)
                    "resume" -> if (isPaused) JSONObject().put("type", "resume_timer").put("pid", n) else null
                    "cancel" -> JSONObject().put("type", "cancel_timer").put(if (isPaused) "pid" else "id", n)
                    else -> null
                }
            }
            else -> null
        }
    }
}
