package com.jarvis.clock.assistant

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lista blanca de acciones que se aceptan desde la respuesta del puente/LLM. El texto que lee
 * el LLM (web, correo, calendario) no es de fiar, así que aquí no entra nada destructivo ni
 * que cambie la configuración: borrado masivo de alarmas, parar la que suena, mostrar URL o
 * HTML, ajustes, diseño y Bluetooth visible.
 */
object ActionPolicy {
    const val MAX_ACTIONS = 10

    class Filtered(val allowed: JSONArray, val rejected: List<String>)

    private val SIMPLE = setOf(
        "set_alarm", "enable_alarm", "disable_alarm", "update_alarm",
        "set_timer", "cancel_timer", "snooze_alarm",
        "clear_display", "set_volume", "set_brightness",
    )
    private val BT_ACTIONS = setOf("on", "off", "status")

    fun filter(actions: JSONArray?): Filtered {
        val ok = JSONArray()
        val no = ArrayList<String>()
        if (actions == null) return Filtered(ok, no)
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: continue
            val type = a.optString("type")
            when {
                ok.length() >= MAX_ACTIONS -> break
                allowed(type, a) -> ok.put(a)
                else -> no.add(type.ifEmpty { "?" })
            }
        }
        return Filtered(ok, no)
    }

    private fun allowed(type: String, a: JSONObject): Boolean = when (type) {
        in SIMPLE -> true
        "cancel_alarm", "delete_alarm" -> !a.optBoolean("all")
        "bluetooth" -> a.optString("action") in BT_ACTIONS
        else -> false
    }
}
