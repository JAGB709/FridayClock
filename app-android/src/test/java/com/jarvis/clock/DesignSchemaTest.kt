package com.jarvis.clock

import com.jarvis.clock.design.DesignSchema
import com.jarvis.clock.design.DesignSchema.Result
import com.jarvis.clock.design.darken
import com.jarvis.clock.design.parseHex
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validación del diseño de pantallas: todo o nada, sin tocar el dispositivo. */
class DesignSchemaTest {

    private fun base(): JSONObject = DesignSchema.initial(70, 3, 23, 7)
    private fun invalid(d: JSONObject): Map<String, String> = (DesignSchema.validate(d) as Result.Invalid).errors
    private fun elements(d: JSONObject, screen: String = "principal"): JSONArray =
        d.getJSONObject("screens").getJSONObject(screen).getJSONArray("elements")
    private fun profileId(p: JSONObject?) = p?.getString("id")

    @Test fun `el diseno inicial es valido`() {
        assertTrue(DesignSchema.validate(base()) is Result.Ok)
    }

    @Test fun `el diseno inicial usa los temas Obsidiana y conserva los antiguos`() {
        val d = base()
        val themes = d.getJSONObject("themes")
        for (id in listOf("obsidiana_dia", "obsidiana_noche", "clasico", "noche_roja", "amanecer", "minimal", "digital_verde")) {
            assertTrue(id, themes.has(id))
        }
        val dia = themes.getJSONObject("obsidiana_dia")
        assertEquals("Obsidiana Día", dia.getString("name")); assertEquals("inter", dia.getString("font"))
        assertEquals("#000000", dia.getJSONObject("bg").getString("color")); assertEquals("#FFB454", dia.getString("accent"))
        val noche = themes.getJSONObject("obsidiana_noche")
        assertEquals("#9A5B12", noche.getString("accent")); assertEquals("#B8A89A", noche.getString("fg"))
        val p = d.getJSONArray("profiles")
        assertEquals("obsidiana_dia", p.getJSONObject(0).getString("theme"))
        assertEquals("obsidiana_noche", p.getJSONObject(1).getString("theme"))
        assertEquals("obsidiana_dia", d.getJSONObject("alarm_screen").getString("theme"))
        assertTrue(DesignSchema.validate(d) is Result.Ok)
    }

    @Test fun `la fuente inter es valida y las antiguas siguen siendolo`() {
        for (f in listOf("inter", "thin", "normal", "bold", "mono", "digital")) {
            val d = base()
            d.getJSONObject("themes").getJSONObject("clasico").put("font", f)
            assertTrue(f, DesignSchema.validate(d) is Result.Ok)
        }
        val d = base()
        d.getJSONObject("themes").getJSONObject("clasico").put("font", "comic")
        assertTrue(invalid(d).containsKey("themes.clasico.font"))
    }

    @Test fun `el diseno inicial copia el modo noche de los ajustes`() {
        val p = base().getJSONArray("profiles")
        assertEquals(2, p.length())
        val dia = p.getJSONObject(0)
        val noche = p.getJSONObject(1)
        assertEquals("07:00", dia.getString("from")); assertEquals(70, dia.getInt("brightness")); assertFalse(dia.getBoolean("night"))
        assertEquals("23:00", noche.getString("from")); assertEquals(3, noche.getInt("brightness")); assertTrue(noche.getBoolean("night"))
    }

    @Test fun `sin modo noche solo hay perfil dia desde las 00 00`() {
        val d = DesignSchema.initial(70, 3, 7, 7)
        assertEquals(1, d.getJSONArray("profiles").length())
        assertEquals("00:00", d.getJSONArray("profiles").getJSONObject(0).getString("from"))
        assertTrue(DesignSchema.validate(d) is Result.Ok)
    }

    @Test fun `el inicial acota valores raros de los ajustes`() {
        val d = DesignSchema.initial(500, -4, 99, -1)
        assertTrue(DesignSchema.validate(d) is Result.Ok)
    }

    @Test fun `los elementos pueden solaparse y conservan el orden de la lista`() {
        val d = base()
        elements(d).getJSONObject(1).put("x", 0).put("y", 0).put("w", 8).put("h", 1) // la fecha sobre la hora
        assertTrue(DesignSchema.validate(d) is Result.Ok)
        assertEquals("hora", elements(d).getJSONObject(0).getString("id"))
        assertEquals("fecha", elements(d).getJSONObject(1).getString("id"))
    }

    private fun fondo(d: JSONObject, bg: JSONObject): JSONObject {
        d.getJSONObject("themes").getJSONObject("clasico").put("bg", bg)
        return d
    }
    private fun fotos(vararg extra: Pair<String, Any>): JSONObject {
        val o = JSONObject().put("type", "photos").put("images", JSONArray().put("a.jpg").put("b.png"))
        for ((k, v) in extra) o.put(k, v)
        return o
    }

    @Test fun `fondo de fotos valido con todos los campos`() {
        val bg = fotos("interval_s" to 5, "transition" to "none", "order" to "random", "fit" to "contain",
            "zoom" to 1.5, "pos_x" to 20, "pos_y" to 80, "dim" to 40, "color" to "#101010")
        assertTrue(DesignSchema.validate(fondo(base(), bg)) is Result.Ok)
        assertTrue(DesignSchema.validate(fondo(base(), fotos())) is Result.Ok) // solo lo obligatorio
    }

    @Test fun `fondo de fotos exige entre 1 y 20 imagenes validas`() {
        assertTrue(invalid(fondo(base(), fotos().put("images", JSONArray()))).containsKey("themes.clasico.bg.images"))
        val muchas = JSONArray(); for (i in 0..20) muchas.put("f$i.jpg")
        assertTrue(invalid(fondo(base(), fotos().put("images", muchas))).containsKey("themes.clasico.bg.images"))
        assertTrue(invalid(fondo(base(), fotos().put("images", JSONArray().put("../x.jpg")))).containsKey("themes.clasico.bg.images[0]"))
        assertTrue(invalid(fondo(base(), fotos().apply { remove("images") })).containsKey("themes.clasico.bg.images"))
    }

    @Test fun `fondo de fotos valida intervalo transicion orden y oscurecido`() {
        val e = invalid(fondo(base(), fotos("interval_s" to 2, "transition" to "zoom", "order" to "alfabetico", "dim" to 90)))
        for (k in listOf("interval_s", "transition", "order", "dim")) assertTrue(k, e.containsKey("themes.clasico.bg.$k"))
    }

    @Test fun `los campos del carrusel de fondo solo valen con type photos`() {
        val bg = JSONObject().put("type", "color").put("color", "#000000").put("interval_s", 10).put("images", JSONArray().put("a.jpg"))
        val e = invalid(fondo(base(), bg))
        assertTrue(e.containsKey("themes.clasico.bg.interval_s")); assertTrue(e.containsKey("themes.clasico.bg.images"))
        val img = JSONObject().put("type", "image").put("image", "a.jpg").put("order", "random")
        assertTrue(invalid(fondo(base(), img)).containsKey("themes.clasico.bg.order"))
    }

    @Test fun `un tipo de fondo desconocido lo nombra`() {
        val e = invalid(fondo(base(), JSONObject().put("type", "video")))
        assertEquals("debe ser color, gradient, image o photos", e["themes.clasico.bg.type"])
    }

    @Test fun `los fondos antiguos siguen siendo validos`() {
        assertTrue(DesignSchema.validate(base()) is Result.Ok)
        assertTrue(DesignSchema.validate(fondo(base(), JSONObject().put("type", "image").put("image", "a.jpg").put("dim", 30))) is Result.Ok)
        assertTrue(DesignSchema.validate(fondo(base(), JSONObject().put("type", "gradient").put("from", "#000000").put("to", "#ffffff"))) is Result.Ok)
    }

    @Test fun `elemento fuera de la rejilla`() {
        val d = base()
        elements(d).getJSONObject(0).put("x", 6).put("w", 3)
        assertEquals("se sale de la rejilla (x + w ≤ 8)", invalid(d)["screens.principal.elements.hora.w"])
        val d2 = base()
        elements(d2).getJSONObject(0).put("y", 5).put("h", 2)
        assertEquals("se sale de la rejilla (y + h ≤ 6)", invalid(d2)["screens.principal.elements.hora.h"])
    }

    @Test fun `tamano y posicion deben ser enteros en rango`() {
        val d = base()
        elements(d).getJSONObject(0).put("w", 0)
        assertEquals("debe ser un entero entre 1 y 8", invalid(d)["screens.principal.elements.hora.w"])
        val d2 = base()
        elements(d2).getJSONObject(0).put("x", 1.5)
        assertEquals("debe ser un entero entre 0 y 7", invalid(d2)["screens.principal.elements.hora.x"])
        val d3 = base()
        elements(d3).getJSONObject(0).put("y", "2")
        assertTrue(invalid(d3).containsKey("screens.principal.elements.hora.y"))
    }

    @Test fun `la zona de la alarma es de 8 por 4`() {
        val d = base()
        d.getJSONObject("alarm_screen").getJSONArray("elements").getJSONObject(0).put("y", 3).put("h", 2)
        assertEquals("se sale de la rejilla (y + h ≤ 4)", invalid(d)["alarm_screen.elements.hora.h"])
    }

    @Test fun `tipo de elemento y propiedades desconocidas`() {
        val d = base()
        elements(d).getJSONObject(0).put("type", "foto")
        assertEquals("tipo desconocido", invalid(d)["screens.principal.elements.hora.type"])
        val d2 = base()
        elements(d2).getJSONObject(0).put("brillo", 3)
        assertEquals("campo desconocido", invalid(d2)["screens.principal.elements.hora.brillo"])
        val d3 = base()
        d3.put("extra", 1)
        assertEquals("campo desconocido", invalid(d3)["extra"])
    }

    @Test fun `opciones de cada elemento`() {
        val d = base()
        val e = elements(d)
        e.getJSONObject(0).put("style", "romano")
        e.getJSONObject(1).put("format", "larguisimo")
        e.getJSONObject(3).put("color", "rojo")
        val errors = invalid(d)
        assertTrue(errors.containsKey("screens.principal.elements.hora.style"))
        assertTrue(errors.containsKey("screens.principal.elements.fecha.format"))
        assertTrue(errors.containsKey("screens.principal.elements.alarma.color"))
    }

    @Test fun `frases del texto`() {
        val d = base()
        val e = elements(d)
        e.put(JSONObject().put("id", "frase").put("type", "text").put("x", 0).put("y", 5).put("w", 8).put("h", 1)
            .put("mode", "daily").put("quotes", JSONArray(List(21) { "frase $it" })))
        // y = 5 está ocupada por la alarma: quitamos la alarma para aislar el error de las frases
        e.remove(3)
        assertEquals("debe ser una lista de hasta 20 frases", invalid(d)["screens.principal.elements.frase.quotes"])
        e.getJSONObject(3).put("quotes", JSONArray(listOf("a".repeat(121))))
        assertTrue(invalid(d).containsKey("screens.principal.elements.frase.quotes[0]"))
    }

    @Test fun `el texto con modo alarm_label es valido`() {
        val d = base()
        assertEquals("alarm_label", d.getJSONObject("alarm_screen").getJSONArray("elements").getJSONObject(1).getString("mode"))
        assertTrue(DesignSchema.validate(d) is Result.Ok)
    }

    @Test fun `colores y fuentes del tema`() {
        val d = base()
        val t = d.getJSONObject("themes").getJSONObject("clasico")
        t.put("fg", "blanco").put("font", "gotica")
        val errors = invalid(d)
        assertEquals("debe ser un color #rrggbb", errors["themes.clasico.fg"])
        assertTrue(errors.containsKey("themes.clasico.font"))
    }

    @Test fun `fondos degradado e imagen`() {
        val d = base()
        d.getJSONObject("themes").getJSONObject("amanecer").getJSONObject("bg").put("angle", 400)
        assertEquals("debe ser un entero entre 0 y 359", invalid(d)["themes.amanecer.bg.angle"])
        val d2 = base()
        d2.getJSONObject("themes").getJSONObject("minimal")
            .put("bg", JSONObject().put("type", "image").put("image", "../../x.jpg"))
        assertEquals("nombre de imagen no válido (jpg, png o webp)", invalid(d2)["themes.minimal.bg.image"])
        val d3 = base()
        d3.getJSONObject("themes").getJSONObject("minimal")
            .put("bg", JSONObject().put("type", "image").put("image", "playa.jpg").put("dim", 40).put("color", "#112233"))
        assertTrue(DesignSchema.validate(d3) is Result.Ok)
    }

    @Test fun `referencias a temas y pantallas que no existen`() {
        val d = base()
        val p = d.getJSONArray("profiles").getJSONObject(0)
        p.put("theme", "fantasma").put("screens", JSONArray(listOf("principal", "nada")))
        val errors = invalid(d)
        assertEquals("el tema no existe", errors["profiles.dia.theme"])
        assertEquals("la pantalla no existe", errors["profiles.dia.screens[1]"])
        val d2 = base()
        d2.getJSONObject("alarm_screen").put("theme", "fantasma")
        assertEquals("el tema no existe", invalid(d2)["alarm_screen.theme"])
    }

    @Test fun `perfiles rechazan horas repetidas y rotacion rara`() {
        val d = base()
        d.getJSONArray("profiles").getJSONObject(1).put("from", "07:00")
        assertEquals("hora repetida en otro perfil", invalid(d)["profiles.noche.from"])
        val d2 = base()
        d2.getJSONArray("profiles").getJSONObject(0).put("rotate_s", 3)
        assertEquals("debe ser 0 o un entero entre 5 y 600", invalid(d2)["profiles.dia.rotate_s"])
        val d3 = base()
        d3.getJSONArray("profiles").getJSONObject(0).put("rotate_s", 30).put("brightness", 0)
        assertTrue(invalid(d3).containsKey("profiles.dia.brightness"))
        val d4 = base()
        d4.getJSONArray("profiles").getJSONObject(0).put("rotate_s", 0)
        assertTrue(DesignSchema.validate(d4) is Result.Ok)
        val d5 = base()
        d5.getJSONArray("profiles").getJSONObject(0).put("from", "7:00")
        assertEquals("debe ser HH:MM", invalid(d5)["profiles.dia.from"])
    }

    @Test fun `limites de cantidad`() {
        val d = base()
        val e = elements(d)
        for (i in 0 until 10) e.put(JSONObject().put("id", "x$i").put("type", "text").put("x", 0).put("y", 0).put("w", 1).put("h", 1))
        assertEquals("máximo 12 elementos", invalid(d)["screens.principal.elements"])
        val d2 = base()
        d2.put("profiles", JSONArray())
        assertEquals("debe haber entre 1 y 4 perfiles", invalid(d2)["profiles"])
    }

    @Test fun `un diseno de mas de 64 KB se rechaza`() {
        val d = base()
        val big = JSONArray()
        for (i in 0 until 3000) big.put("y".repeat(40))
        d.put("padding", big)
        assertEquals("supera 64 KB", invalid(d)["design"])
    }

    @Test fun `version y estructura obligatoria`() {
        assertEquals("debe ser 1", invalid(base().put("version", 2))["version"])
        val d = base()
        d.remove("themes")
        assertEquals("falta", invalid(d)["themes"])
    }

    @Test fun `perfil activo con ciclo de medianoche`() {
        val d = base() // Día 07:00, Noche 23:00
        assertEquals("noche", profileId(DesignSchema.activeProfile(d, 3 * 60)))      // madrugada: aún es la noche anterior
        assertEquals("dia", profileId(DesignSchema.activeProfile(d, 7 * 60)))
        assertEquals("dia", profileId(DesignSchema.activeProfile(d, 22 * 60 + 59)))
        assertEquals("noche", profileId(DesignSchema.activeProfile(d, 23 * 60)))
        assertEquals("noche", profileId(DesignSchema.activeProfile(d, 23 * 60 + 59)))
        assertEquals("noche", profileId(DesignSchema.activeProfile(d, 0)))
    }

    @Test fun `minutesOf lee HH MM`() {
        assertEquals(7 * 60 + 5, DesignSchema.minutesOf("07:05"))
        assertNull(DesignSchema.minutesOf("7:05"))
        assertNull(DesignSchema.minutesOf("24:00"))
    }

    @Test fun `colores hexadecimales`() {
        assertEquals(0xFF112233.toInt(), parseHex("#112233"))
        assertEquals(0xFF0A141E.toInt(), darken(0xFF285078.toInt(), 0.25f))
    }

    private fun photos(id: String, x: Int, y: Int, w: Int = 2, h: Int = 1, vararg extra: Pair<String, Any>): JSONObject {
        val o = JSONObject().put("id", id).put("type", "photos").put("x", x).put("y", y).put("w", w).put("h", h)
            .put("images", JSONArray().put("a.jpg").put("b.png"))
        for ((k, v) in extra) o.put(k, v)
        return o
    }

    @Test fun `un diseno anterior a las fotos ajustables sigue siendo valido`() {
        val d = base()
        d.getJSONObject("themes").getJSONObject("clasico").put("bg",
            JSONObject().put("type", "image").put("image", "playa.jpg").put("dim", 30).put("color", "#000000"))
        assertTrue(DesignSchema.validate(d) is Result.Ok)
    }

    @Test fun `fondo de imagen acepta fit zoom y posicion`() {
        val d = base()
        d.getJSONObject("themes").getJSONObject("clasico").put("bg",
            JSONObject().put("type", "image").put("image", "playa.jpg").put("fit", "contain")
                .put("zoom", 1.5).put("pos_x", 20).put("pos_y", 80))
        assertTrue(DesignSchema.validate(d) is Result.Ok)
    }

    @Test fun `ajuste de foto fuera de rango se rechaza con su ruta`() {
        val d = base()
        d.getJSONObject("themes").getJSONObject("clasico").put("bg",
            JSONObject().put("type", "image").put("image", "playa.jpg").put("fit", "raro")
                .put("zoom", 4).put("pos_x", 101).put("pos_y", 1.5))
        val e = invalid(d)
        assertTrue(e.containsKey("themes.clasico.bg.fit"))
        assertTrue(e.containsKey("themes.clasico.bg.zoom"))
        assertTrue(e.containsKey("themes.clasico.bg.pos_x"))
        assertTrue(e.containsKey("themes.clasico.bg.pos_y"))
    }

    @Test fun `el ajuste de foto no vale en fondos de color`() {
        val d = base()
        d.getJSONObject("themes").getJSONObject("clasico").getJSONObject("bg").put("zoom", 2)
        assertTrue(invalid(d).containsKey("themes.clasico.bg.zoom"))
    }

    private fun withScreen(d: JSONObject, vararg els: JSONObject): JSONObject {
        val arr = JSONArray()
        for (e in els) arr.put(e)
        d.getJSONObject("screens").put("fotos", JSONObject().put("name", "Fotos").put("elements", arr))
        return d
    }

    @Test fun `elemento photos valido`() {
        val d = withScreen(base(), photos("galeria", 0, 0, 4, 3, "interval_s" to 5, "transition" to "none", "order" to "random", "fit" to "cover", "zoom" to 2, "pos_x" to 10, "pos_y" to 90))
        assertTrue(DesignSchema.validate(d) is Result.Ok)
    }

    @Test fun `photos exige entre 1 y 20 imagenes con nombre valido`() {
        val vacio = withScreen(base(), photos("galeria", 0, 0, 4, 3).put("images", JSONArray()))
        assertTrue(invalid(vacio).containsKey("screens.fotos.elements.galeria.images"))
        val mala = withScreen(base(), photos("galeria", 0, 0, 4, 3).put("images", JSONArray().put("../x.jpg")))
        assertTrue(invalid(mala).containsKey("screens.fotos.elements.galeria.images[0]"))
        val muchas = JSONArray(); for (i in 0..20) muchas.put("f$i.jpg")
        assertTrue(invalid(withScreen(base(), photos("galeria", 0, 0, 4, 3).put("images", muchas))).containsKey("screens.fotos.elements.galeria.images"))
        val sin = withScreen(base(), photos("galeria", 0, 0, 4, 3).apply { remove("images") })
        assertTrue(invalid(sin).containsKey("screens.fotos.elements.galeria.images"))
    }

    @Test fun `photos valida intervalo transicion y orden`() {
        val d = withScreen(base(), photos("galeria", 0, 0, 4, 3, "interval_s" to 2, "transition" to "zoom", "order" to "alfabetico"))
        val e = invalid(d)
        assertTrue(e.containsKey("screens.fotos.elements.galeria.interval_s"))
        assertTrue(e.containsKey("screens.fotos.elements.galeria.transition"))
        assertTrue(e.containsKey("screens.fotos.elements.galeria.order"))
    }

    @Test fun `maximo 2 photos por pantalla y 1 en la de alarma`() {
        val tres = withScreen(base(), photos("a", 0, 0), photos("b", 2, 0), photos("c", 4, 0))
        assertTrue(invalid(tres).containsKey("screens.fotos.elements"))
        val dos = withScreen(base(), photos("a", 0, 0), photos("b", 2, 0))
        assertTrue(DesignSchema.validate(dos) is Result.Ok)
        val alarma = base()
        alarma.getJSONObject("alarm_screen").put("elements", JSONArray().put(photos("a", 0, 0)).put(photos("b", 2, 0)))
        assertTrue(invalid(alarma).containsKey("alarm_screen.elements"))
    }

    // ---- 3b: grupos y anclajes entre elementos

    private fun el(d: JSONObject, id: String): JSONObject {
        val a = elements(d)
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") == id) return a.getJSONObject(i)
        throw AssertionError(id)
    }
    private fun anchor(to: String, side: String, gap: Int? = null) =
        JSONObject().put("to", to).put("side", side).also { if (gap != null) it.put("gap", gap) }

    @Test fun `un diseno sin grupos ni anclajes sigue siendo valido`() {
        val d = base()
        assertTrue(d.toString().indexOf("\"group\"") < 0 && d.toString().indexOf("\"anchor\"") < 0)
        assertTrue(DesignSchema.validate(d) is Result.Ok)
    }

    @Test fun `un grupo valido necesita al menos dos elementos`() {
        val d = base()
        el(d, "fecha").put("group", "texto"); el(d, "tiempo").put("group", "texto")
        assertTrue(DesignSchema.validate(d) is Result.Ok)
        val solo = base()
        el(solo, "fecha").put("group", "texto")
        assertEquals("un grupo necesita al menos 2 elementos", invalid(solo)["screens.principal.elements.fecha.group"])
        val mal = base()
        el(mal, "fecha").put("group", "Mal Id!");
        assertTrue(invalid(mal).containsKey("screens.principal.elements.fecha.group"))
    }

    @Test fun `un anclaje coherente es valido`() {
        val d = base()
        el(d, "fecha").put("anchor", anchor("hora", "below"))
        el(d, "tiempo").put("anchor", anchor("fecha", "below", 0))
        assertTrue(DesignSchema.validate(d) is Result.Ok)
    }

    @Test fun `el anclaje comprueba destino, lado, separacion y posicion con ruta exacta`() {
        val noExiste = base(); el(noExiste, "fecha").put("anchor", anchor("fantasma", "below"))
        assertTrue(invalid(noExiste).containsKey("screens.principal.elements.fecha.anchor.to"))
        val aSiMismo = base(); el(aSiMismo, "fecha").put("anchor", anchor("fecha", "below"))
        assertTrue(invalid(aSiMismo).containsKey("screens.principal.elements.fecha.anchor.to"))
        val lado = base(); el(lado, "fecha").put("anchor", anchor("hora", "diagonal"))
        assertTrue(invalid(lado).containsKey("screens.principal.elements.fecha.anchor.side"))
        val gap = base(); el(gap, "fecha").put("anchor", anchor("hora", "below", 9))
        assertTrue(invalid(gap).containsKey("screens.principal.elements.fecha.anchor.gap"))
        val extra = base(); el(extra, "fecha").put("anchor", anchor("hora", "below").put("otro", 1))
        assertTrue(invalid(extra).containsKey("screens.principal.elements.fecha.anchor.otro"))
        val desviado = base(); el(desviado, "fecha").put("anchor", anchor("hora", "below", 1))
        assertTrue(invalid(desviado)["screens.principal.elements.fecha.anchor"]!!.contains("x=0, y=4"))
    }

    @Test fun `los anclajes en ciclo se rechazan`() {
        val d = base()
        el(d, "hora").put("anchor", anchor("fecha", "above"))
        el(d, "fecha").put("anchor", anchor("hora", "below"))
        val e = invalid(d)
        assertTrue(e.values.any { it.contains("ciclo") })
    }

    @Test fun `el anclaje tambien vale en la pantalla de alarma y no sale de la rejilla`() {
        val d = base()
        val a = d.getJSONObject("alarm_screen").getJSONArray("elements")
        a.getJSONObject(1).put("anchor", anchor("hora", "below"))
        assertTrue(DesignSchema.validate(d) is Result.Ok)
        a.getJSONObject(1).put("y", 4).put("anchor", anchor("hora", "below", 1))
        assertTrue(invalid(d).isNotEmpty())
    }
}
