package com.jarvis.clock

import com.jarvis.clock.assistant.ActionPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lista blanca de acciones que pueden venir del puente/LLM. */
class ActionPolicyTest {
    private fun a(vararg o: String) = JSONArray(o.map { JSONObject(it) })
    private fun tipos(r: ActionPolicy.Filtered) = (0 until r.allowed.length()).map { r.allowed.getJSONObject(it).getString("type") }

    @Test fun `deja pasar alarmas, temporizadores y volumen`() {
        val r = ActionPolicy.filter(a(
            """{"type":"set_alarm","time":"07:00"}""", """{"type":"set_timer","seconds":60}""",
            """{"type":"set_volume","level":30}""", """{"type":"snooze_alarm"}""",
        ))
        assertEquals(listOf("set_alarm", "set_timer", "set_volume", "snooze_alarm"), tipos(r))
        assertTrue(r.rejected.isEmpty())
    }

    @Test fun `bloquea display, ajustes, diseno y parar alarma`() {
        val r = ActionPolicy.filter(a(
            """{"type":"display","url":"http://x"}""", """{"type":"display","html":"<b>"}""",
            """{"type":"set_settings","settings":{}}""", """{"type":"set_design","design":{}}""",
            """{"type":"reset_design"}""", """{"type":"stop_alarm"}""", """{"type":"preview_design"}""",
            """{"type":"get_settings"}""",
        ))
        assertEquals(0, r.allowed.length())
        assertEquals(8, r.rejected.size)
    }

    @Test fun `bloquea el borrado masivo de alarmas pero deja borrar una`() {
        val r = ActionPolicy.filter(a(
            """{"type":"cancel_alarm","all":true}""", """{"type":"delete_alarm","all":true}""",
            """{"type":"cancel_alarm","id":3}""",
        ))
        assertEquals(listOf("cancel_alarm"), tipos(r))
        assertEquals(3, r.allowed.getJSONObject(0).getInt("id"))
        assertEquals(2, r.rejected.size)
    }

    @Test fun `bluetooth solo on, off y status, nunca visible`() {
        val r = ActionPolicy.filter(a(
            """{"type":"bluetooth","action":"status"}""", """{"type":"bluetooth","action":"off"}""",
            """{"type":"bluetooth","action":"speaker"}""", """{"type":"bluetooth","action":"discoverable","seconds":3600}""",
            """{"type":"bluetooth"}""",
        ))
        assertEquals(2, r.allowed.length())
        assertEquals(3, r.rejected.size)
    }

    @Test fun `limita el numero de acciones y tolera basura`() {
        val muchas = JSONArray((1..50).map { JSONObject("""{"type":"set_volume","level":$it}""") })
        assertEquals(ActionPolicy.MAX_ACTIONS, ActionPolicy.filter(muchas).allowed.length())
        assertEquals(0, ActionPolicy.filter(null).allowed.length())
        assertEquals(0, ActionPolicy.filter(JSONArray("""["x",1,null,{}]""")).allowed.length())
    }
}
