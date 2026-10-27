package com.jarvis.clock

import com.jarvis.clock.design.DesignSchema
import com.jarvis.clock.design.DesignSchema.Result
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Elementos `stopwatch` y `timers` del motor de pantallas. */
class DesignTimersTest {
    private fun withElement(el: String): JSONObject {
        val d = DesignSchema.initial(70, 3, 23, 7)
        d.getJSONObject("screens").getJSONObject("principal").put("elements", JSONArray().put(JSONObject(el)))
        return d
    }
    private fun errors(el: String) = (DesignSchema.validate(withElement(el)) as Result.Invalid).errors

    @Test fun `cronometro y temporizadores validos`() {
        assertTrue(DesignSchema.validate(withElement("""{"id":"cr","type":"stopwatch","x":0,"y":0,"w":4,"h":3,"controls":true}""")) is Result.Ok)
        assertTrue(DesignSchema.validate(withElement("""{"id":"tm","type":"timers","x":0,"y":0,"w":8,"h":3,"count":3,"controls":false,"color":"accent"}""")) is Result.Ok)
        assertTrue(DesignSchema.validate(withElement("""{"id":"tm","type":"timers","x":0,"y":0,"w":8,"h":3}""")) is Result.Ok)
    }

    @Test fun `rechaza campos no validos`() {
        assertEquals("debe ser un entero entre 1 y 3", errors("""{"id":"tm","type":"timers","x":0,"y":0,"w":8,"h":3,"count":4}""")["screens.principal.elements.tm.count"])
        assertEquals("debe ser true o false", errors("""{"id":"cr","type":"stopwatch","x":0,"y":0,"w":4,"h":3,"controls":"si"}""")["screens.principal.elements.cr.controls"])
        assertEquals("campo desconocido", errors("""{"id":"cr","type":"stopwatch","x":0,"y":0,"w":4,"h":3,"count":2}""")["screens.principal.elements.cr.count"])
        assertEquals("se sale de la rejilla (x + w ≤ 8)", errors("""{"id":"cr","type":"stopwatch","x":6,"y":0,"w":4,"h":3}""")["screens.principal.elements.cr.w"])
    }
}
