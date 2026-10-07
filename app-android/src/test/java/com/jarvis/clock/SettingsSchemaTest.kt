package com.jarvis.clock

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validación de ajustes remotos: todo o nada, sin tocar Prefs. */
class SettingsSchemaTest {

    private fun ok(json: String) = SettingsSchema.validate(JSONObject(json)) as SettingsSchema.Validation.Ok
    private fun bad(json: String) = SettingsSchema.validate(JSONObject(json)) as SettingsSchema.Validation.Invalid

    @Test fun `valores validos se normalizan`() {
        val v = ok("""{"snooze_min":10,"tts_rate":1.25,"wake_word":false,"city":"  Sevilla ","lat":37}""").values
        assertEquals(10, v["snooze_min"])
        assertEquals(1.25, v["tts_rate"])
        assertEquals(false, v["wake_word"])
        assertEquals("Sevilla", v["city"])
        assertEquals(37.0, v["lat"])
    }

    @Test fun `parche vacio es valido y no cambia nada`() {
        assertTrue(ok("{}").values.isEmpty())
    }

    @Test fun `un error invalida todo el parche`() {
        val e = bad("""{"snooze_min":10,"night_brightness":0}""").errors
        assertEquals(setOf("night_brightness"), e.keys)
        assertEquals("debe ser un entero entre 1 y 100", e["night_brightness"])
    }

    @Test fun `tipos equivocados y claves desconocidas`() {
        val e = bad("""{"snooze_min":5.5,"day_brightness":"50","follow_up":"true","auth_token":"x","assistant_name":""}""").errors
        assertEquals(setOf("snooze_min", "day_brightness", "follow_up", "auth_token", "assistant_name"), e.keys)
        assertEquals("ajuste desconocido", e["auth_token"])
    }

    @Test fun `el esquema no expone token, puerto ni puente`() {
        val keys = SettingsSchema.fields.map { it.key }.toSet()
        assertTrue("auth_token" !in keys && "http_port" !in keys && "backend_url" !in keys && "backend_token" !in keys)
        assertEquals(
            setOf("snooze_min", "alarm_ramp_s", "alarm_auto_snooze_min", "morning_briefing",
                "night_start", "night_end", "night_brightness", "day_brightness",
                "assistant_name", "tts_rate", "wake_word", "follow_up", "city", "lat", "lon"),
            keys,
        )
    }

    @Test fun `schemaJson describe tipo, rango, grupo y etiqueta`() {
        val s = SettingsSchema.schemaJson()
        val snooze = (0 until s.length()).map { s.getJSONObject(it) }.first { it.getString("key") == "snooze_min" }
        assertEquals("int", snooze.getString("type"))
        assertEquals(1, snooze.getInt("min")); assertEquals(60, snooze.getInt("max"))
        assertEquals("Despertador", snooze.getString("group"))
        assertEquals("Minutos al posponer", snooze.getString("label"))
    }
}
