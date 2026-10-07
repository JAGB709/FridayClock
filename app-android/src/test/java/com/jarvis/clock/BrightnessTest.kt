package com.jarvis.clock

import com.jarvis.clock.design.Brightness
import com.jarvis.clock.design.Brightness.Manual
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrightnessTest {
    private fun design(vararg p: Triple<String, Int, Boolean>): JSONObject {
        val arr = JSONArray()
        for ((id, b, _) in p) arr.put(JSONObject().put("id", id).put("brightness", b))
        return JSONObject().put("profiles", arr)
    }

    @Test fun `sin valor manual manda el brillo del perfil`() {
        assertEquals(70, Brightness.effective(70, null, "dia"))
        assertEquals(3, Brightness.effective(3, null, "noche"))
    }

    @Test fun `el valor manual manda en su perfil y se acota`() {
        assertEquals(40, Brightness.effective(70, Manual(40, "dia", 1), "dia"))
        assertEquals(100, Brightness.effective(70, Manual(500, "dia", 1), "dia"))
        assertEquals(1, Brightness.effective(70, Manual(-3, "dia", 1), "dia"))
    }

    @Test fun `el valor manual de otro perfil no se aplica`() {
        assertEquals(3, Brightness.effective(3, Manual(40, "dia", 1), "noche"))
    }

    @Test fun `el sello cambia solo si cambia un brillo de perfil o el conjunto de perfiles`() {
        val a = design(Triple("dia", 70, false), Triple("noche", 3, true))
        val sameBrightness = design(Triple("dia", 70, false), Triple("noche", 3, true)).also { it.put("themes", JSONObject()) }
        val b = design(Triple("dia", 80, false), Triple("noche", 3, true))
        val c = design(Triple("dia", 70, false))
        assertEquals(Brightness.stampOf(a), Brightness.stampOf(sameBrightness))
        assertNotEquals(Brightness.stampOf(a), Brightness.stampOf(b))
        assertNotEquals(Brightness.stampOf(a), Brightness.stampOf(c))
    }

    @Test fun `valid conserva el manual mientras perfil y sello coinciden`() {
        val d = design(Triple("dia", 70, false), Triple("noche", 3, true))
        val m = Manual(40, "dia", Brightness.stampOf(d))
        assertEquals(m, Brightness.valid(m, "dia", d))
    }

    @Test fun `valid borra el manual al cambiar el perfil programado`() {
        val d = design(Triple("dia", 70, false), Triple("noche", 3, true))
        assertNull(Brightness.valid(Manual(40, "dia", Brightness.stampOf(d)), "noche", d))
    }

    @Test fun `valid borra el manual si el panel cambia un brillo de perfil`() {
        val d = design(Triple("dia", 70, false), Triple("noche", 3, true))
        val m = Manual(40, "dia", Brightness.stampOf(d))
        val changed = design(Triple("dia", 65, false), Triple("noche", 3, true))
        assertNull(Brightness.valid(m, "dia", changed))
    }

    @Test fun `valid sin manual devuelve nulo`() {
        assertNull(Brightness.valid(null, "dia", design(Triple("dia", 70, false))))
    }
}
