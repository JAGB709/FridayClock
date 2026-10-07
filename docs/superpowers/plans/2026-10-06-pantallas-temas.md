# Pantallas y temas del reloj (3a) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que la carátula del reloj y la pantalla de alarma se describan en un diseño JSON (temas, pantallas en rejilla 8×6, perfiles por hora), que se edite desde el panel web con vista previa exacta y botón "Probar en el reloj", y que se aplique al pulsar Guardar.

**Architecture:** Un motor HTML/JS (`face/`) vive en los assets del APK y se copia idéntico al panel. El reloj lo carga en un `WebView` (Android elige perfil, brillo y datos; el motor dibuja, rota pantallas y atiende los toques). El panel lo usa en un `iframe` de 800×480 para la vista previa. El reloj valida y guarda el diseño (`DesignSchema` + `DesignStore`) y expone `/design`, `/backgrounds` y `/face/data`; si el `WebView` falla, vuelve la carátula nativa actual.

**Tech Stack:** Kotlin 1.9 + framework Android (sin dependencias), JUnit 4 + org.json en tests; JavaScript ES2017 sin build (WebView Chrome 74), `node --test`; Python 3.12 + FastAPI + pytest.

**Spec:** `docs/superpowers/specs/2026-10-06-pantallas-temas-design.md`

## Global Constraints

- Docs, comentarios, mensajes de error y UI en **español**.
- App: **cero dependencias de runtime** (framework + Kotlin stdlib). `minSdk 26`, `targetSdk 34`. Nada de AppCompat/OkHttp/Compose ni librerías JS.
- Las alarmas **nunca** dependen del `WebView`: suenan desde el servicio nativo y los botones Posponer/Descartar son nativos. Si el `WebView` falla, carátula nativa de respaldo.
- Motor `face/`: JavaScript clásico **ES2017**, sin `?.`, sin `??`, y en CSS sin `inset`, `gap` en flex ni `aspect-ratio` (el WebView del reloj es **Chrome 74**).
- WebView: solo carga `https://appassets.androidplatform.net/assets/face/…` servido por la app (`shouldInterceptRequest`); cualquier otra petición responde 404; navegación externa bloqueada; el puente JS (`Native`) solo existe en esa página.
- Rejilla fija **8 columnas × 6 filas** (la zona superior de la pantalla de alarma: **8×4**). Elementos sin solapes, dentro de la rejilla.
- Límites del diseño: ≤ 64 KB; ≤ 12 temas; ≤ 8 pantallas; ≤ 12 elementos por pantalla; 1–4 perfiles; 1–6 pantallas por perfil; textos ≤ 120 caracteres; ≤ 20 frases; fondos ≤ 2 MB (jpg/png/webp, máx. 2048 px).
- Diseño: guardado todo o nada (`400` con `{"ok":false,"error":"diseño no válido","errors":{"<ruta>":"<motivo>"}}`).
- Cada vez que se cambie `app-android/src/main/assets/face/`, ejecutar `bash tools/sync-face.sh` y commitear también la copia `homelab/friday-panel/static/face/` (un test de CI falla si difieren).
- Capacidad nueva del reloj ⇒ `Actions` + `Api.kt` + skill (`clock.py`, `clock_tools.py`, `SKILL.md`, `mock_clock_server.py` + tests) + tabla de la API del README.
- Commits en español, **sin líneas `Co-Authored-By` ni `Claude-Session`** (regla del usuario; vale también para los subagentes).
- Comandos: app `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest` (desde la raíz del repo); skill `cd hermes-skill && python3 -m pytest -q`; panel `cd homelab/friday-panel && .venv/bin/python -m pytest -q`; JS `node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js`. No se pueden crear archivos `.env*`. No commitear `.venv`.

## Review Focus

1. **Diseño con elementos solapados o fuera de la rejilla** (también en la zona 8×4 de la alarma) → `400` con la ruta exacta del elemento y sin cambiar nada. Tests en Task 1.
2. **Diseño guardado corrupto o inválido** → el reloj arranca con el diseño inicial (nunca pantalla en blanco). Test en Task 4.
3. **Hora actual anterior a todos los `from` de los perfiles y cambio de medianoche** (p. ej. 03:00 con Día 07:00 / Noche 23:00 → Noche). Tests en Task 1 (Kotlin) y Task 2 (JS).
4. **Fondo de imagen borrado o inexistente / texto larguísimo o frase vacía** → el fondo cae a su color y el texto nunca baja de 8 px ni lanza error. Tests en Task 2.
5. **Reloj sin datos del tiempo ni previsión** (sin red) → los elementos muestran "—" y el resto sigue. Tests en Task 2 y Task 3.

## Mapa de archivos

| Archivo | Responsabilidad |
|---|---|
| `app-android/src/main/java/com/jarvis/clock/design/DesignSchema.kt` (nuevo) | validar el diseño (todo o nada), diseño inicial, perfil activo |
| `app-android/src/main/java/com/jarvis/clock/design/Colors.kt` (nuevo) | `parseHex`, `darken` (para los botones de la alarma) |
| `app-android/src/main/java/com/jarvis/clock/design/DesignStore.kt` (nuevo) | guardar y cargar el diseño; si no hay o es inválido, el inicial |
| `app-android/src/main/java/com/jarvis/clock/design/Backgrounds.kt` (nuevo) | fondos subidos: nombres, listar, guardar atómico, borrar |
| `app-android/src/main/java/com/jarvis/clock/design/FaceData.kt` (nuevo) | datos para el motor (tiempo, previsión, próxima alarma), versión del motor |
| `app-android/src/main/java/com/jarvis/clock/weather/Weather.kt` (mod) | previsión por horas y por días, `parse` testeable |
| `app-android/src/main/java/com/jarvis/clock/ui/FaceHost.kt` (nuevo) | el `WebView` del motor, su puente y su servidor de archivos |
| `app-android/src/main/java/com/jarvis/clock/MainActivity.kt` (mod) | alojar el motor, perfil activo, brillo, vista previa, respaldo nativo |
| `app-android/src/main/java/com/jarvis/clock/alarm/AlarmActivity.kt` (mod) | zona superior con el motor; botones nativos |
| `app-android/src/main/java/com/jarvis/clock/Events.kt`, `assistant/Actions.kt`, `server/Api.kt` (mod) | eventos, acciones y rutas nuevas |
| `app-android/src/main/assets/face/{index.html,face.css,face-core.js,face.js}` (nuevo) | el motor |
| `app-android/face-tests/core.test.js` (nuevo) | tests `node --test` de la lógica pura del motor |
| `tools/sync-face.sh` (nuevo) | copia el motor al panel (`--check` para CI) |
| `hermes-skill/*` (mod) | skill y reloj de mentira |
| `homelab/friday-panel/app.py` (mod) | rutas proxy de diseño, fondos y datos |
| `homelab/friday-panel/static/face/*` (nuevo, copia) | motor para la vista previa |
| `homelab/friday-panel/static/editor-core.js` (nuevo) | lógica pura del editor (colisiones, hueco libre, ids) |
| `homelab/friday-panel/static/pantallas.js` (nuevo) | pestaña Pantallas: vista previa y sub-pestañas |
| `homelab/friday-panel/tests-js/editor-core.test.js` (nuevo) | tests `node --test` del editor |
| `.github/workflows/build-apk.yml`, `README.md`, `CLAUDE.md` (mod) | CI y documentación |

Rutas Kotlin abreviadas: `app-android/.../` = `app-android/src/main/java/com/jarvis/clock/`; tests Kotlin en `app-android/src/test/java/com/jarvis/clock/`.

---

## Antes de empezar

La rama `panel-web` (subproyecto 1) aún no está integrada en `main`. Este plan **continúa sobre ella**:

```bash
git switch panel-web && git switch -c pantallas-temas
```

---

### Task 1: `DesignSchema`, colores y enmiendas de la especificación

**Files:**
- Create: `app-android/src/main/java/com/jarvis/clock/design/DesignSchema.kt`
- Create: `app-android/src/main/java/com/jarvis/clock/design/Colors.kt`
- Test: `app-android/src/test/java/com/jarvis/clock/DesignSchemaTest.kt`
- Modify: `docs/superpowers/specs/2026-10-06-pantallas-temas-design.md`

**Interfaces:**
- Produces:
  - `DesignSchema.COLS = 8`, `ROWS = 6`, `ALARM_ROWS = 4`, `MAX_BYTES = 64 * 1024`
  - `sealed class DesignSchema.Result { data class Ok(val design: JSONObject); data class Invalid(val errors: Map<String, String>) }`
  - `DesignSchema.validate(design: JSONObject): Result`
  - `DesignSchema.initial(dayBrightness: Int, nightBrightness: Int, nightStart: Int, nightEnd: Int): JSONObject` (siempre válido)
  - `DesignSchema.activeProfile(design: JSONObject, minuteOfDay: Int): JSONObject?`
  - `DesignSchema.minutesOf(hm: String): Int?`
  - `parseHex(s: String): Int` y `darken(color: Int, factor: Float): Int` (paquete `com.jarvis.clock.design`)
- Forma del diseño (versión 1): `{"version":1,"themes":{id:{name,bg:{type:"color"|"gradient"|"image",color?,from?,to?,angle?,image?,dim?},fg,muted,accent,font}},"screens":{id:{name,elements:[{id,type,x,y,w,h,color?,align?,…}]}},"profiles":[{id,name,from,theme,screens:[id],rotate_s?,brightness?,night?,show_bar?}],"alarm_screen":{theme,elements:[…],snooze_label?,dismiss_label?}}`. Tipos de elemento: `clock`, `date`, `weather_now`, `forecast`, `next_alarm`, `text`.

- [ ] **Step 1: Escribir los tests que fallan.** Crea `DesignSchemaTest.kt`:

```kotlin
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

    @Test fun `elementos solapados`() {
        val d = base()
        elements(d).getJSONObject(1).put("x", 0).put("y", 0).put("w", 8).put("h", 1) // la fecha sobre la hora
        assertEquals("se solapa con hora", invalid(d)["screens.principal.elements.fecha"])
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
}
```

- [ ] **Step 2: Ver que fallan.**
Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.jarvis.clock.DesignSchemaTest'`
Expected: FAIL de compilación (`DesignSchema`, `parseHex` no existen).

- [ ] **Step 3: Crear `Colors.kt`.**

```kotlin
package com.jarvis.clock.design

/** "#rrggbb" -> color ARGB opaco. No usa android.graphics.Color para poder probarlo en la JVM. */
fun parseHex(s: String): Int = (0xFF000000L or s.removePrefix("#").toLong(16)).toInt()

/** Oscurece un color multiplicando cada canal por [factor] (0..1). */
fun darken(color: Int, factor: Float): Int {
    val r = ((color shr 16) and 0xFF) * factor
    val g = ((color shr 8) and 0xFF) * factor
    val b = (color and 0xFF) * factor
    return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
}
```

- [ ] **Step 4: Crear `DesignSchema.kt`.**

```kotlin
package com.jarvis.clock.design

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Diseño de pantallas del reloj (JSON versión 1): temas, pantallas en rejilla 8×6, perfiles por
 * hora y pantalla de alarma. La validación es pura y de todo o nada: si algo falla se devuelve
 * cada ruta con su motivo y no se guarda nada.
 */
object DesignSchema {
    const val COLS = 8
    const val ROWS = 6
    const val ALARM_ROWS = 4
    const val MAX_BYTES = 64 * 1024

    private val SLUG = Regex("^[a-z0-9_-]{1,24}$")
    private val COLOR = Regex("^#[0-9a-fA-F]{6}$")
    private val HM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private val IMAGE = Regex("^[A-Za-z0-9_-]{1,60}\\.(jpg|jpeg|png|webp)$")
    private val FONTS = setOf("thin", "normal", "bold", "mono", "digital")
    private val ROLES = setOf("fg", "muted", "accent")
    private val ALIGNS = setOf("left", "center", "right")
    private val COMMON = setOf("id", "type", "x", "y", "w", "h", "color", "align")
    private val ELEMENT_PROPS = mapOf(
        "clock" to setOf("style", "seconds", "hour12"),
        "date" to setOf("format"),
        "weather_now" to setOf("detail"),
        "forecast" to setOf("mode", "count"),
        "next_alarm" to setOf("show_label"),
        "text" to setOf("mode", "text", "quotes"),
    )

    sealed class Result {
        data class Ok(val design: JSONObject) : Result()
        data class Invalid(val errors: Map<String, String>) : Result()
    }

    private class Ctx {
        val errors = LinkedHashMap<String, String>()
        fun err(path: String, msg: String) { if (path !in errors) errors[path] = msg }
    }

    private class Rect(val id: String, val x: Int, val y: Int, val w: Int, val h: Int)

    // ------------------------------------------------------------------ validación

    fun validate(design: JSONObject): Result {
        if (design.toString().toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            return Result.Invalid(mapOf("design" to "supera 64 KB"))
        }
        val c = Ctx()
        unknownKeys(c, design, setOf("version", "themes", "screens", "profiles", "alarm_screen"), "")
        if ((design.opt("version") as? Number)?.toInt() != 1) c.err("version", "debe ser 1")

        val themeIds = HashSet<String>()
        val themes = design.optJSONObject("themes")
        if (themes == null) c.err("themes", "falta") else {
            if (themes.length() !in 1..12) c.err("themes", "debe haber entre 1 y 12 temas")
            for (id in themes.keys()) {
                themeIds += id
                if (!SLUG.matches(id)) c.err("themes.$id", "identificador no válido")
                validateTheme(c, themes.optJSONObject(id), "themes.$id")
            }
        }

        val screenIds = HashSet<String>()
        val screens = design.optJSONObject("screens")
        if (screens == null) c.err("screens", "falta") else {
            if (screens.length() !in 1..8) c.err("screens", "debe haber entre 1 y 8 pantallas")
            for (id in screens.keys()) {
                screenIds += id
                val p = "screens.$id"
                if (!SLUG.matches(id)) c.err(p, "identificador no válido")
                val s = screens.optJSONObject(id)
                if (s == null) { c.err(p, "debe ser un objeto"); continue }
                unknownKeys(c, s, setOf("name", "elements"), p)
                text(c, s, "name", p, 1, 30, true)
                validateElements(c, s.optJSONArray("elements"), "$p.elements", ROWS)
            }
        }

        val profiles = design.optJSONArray("profiles")
        if (profiles == null) c.err("profiles", "falta") else {
            if (profiles.length() !in 1..4) c.err("profiles", "debe haber entre 1 y 4 perfiles")
            val ids = HashSet<String>()
            val froms = HashSet<String>()
            for (i in 0 until profiles.length()) validateProfile(c, profiles.optJSONObject(i), i, ids, froms, themeIds, screenIds)
        }

        val alarm = design.optJSONObject("alarm_screen")
        if (alarm == null) c.err("alarm_screen", "falta") else {
            unknownKeys(c, alarm, setOf("theme", "elements", "snooze_label", "dismiss_label"), "alarm_screen")
            val th = alarm.opt("theme") as? String
            if (th == null || th !in themeIds) c.err("alarm_screen.theme", "el tema no existe")
            validateElements(c, alarm.optJSONArray("elements"), "alarm_screen.elements", ALARM_ROWS)
            if (alarm.has("snooze_label")) text(c, alarm, "snooze_label", "alarm_screen", 1, 20, false)
            if (alarm.has("dismiss_label")) text(c, alarm, "dismiss_label", "alarm_screen", 1, 20, false)
        }
        return if (c.errors.isEmpty()) Result.Ok(design) else Result.Invalid(c.errors)
    }

    private fun validateTheme(c: Ctx, t: JSONObject?, path: String) {
        if (t == null) { c.err(path, "debe ser un objeto"); return }
        unknownKeys(c, t, setOf("name", "bg", "fg", "muted", "accent", "font"), path)
        text(c, t, "name", path, 1, 30, true)
        for (k in listOf("fg", "muted", "accent")) color(c, t, k, path, true)
        oneOf(c, t, "font", path, FONTS, required = true)
        val bg = t.optJSONObject("bg")
        if (bg == null) { c.err("$path.bg", "falta"); return }
        val p = "$path.bg"
        unknownKeys(c, bg, setOf("type", "color", "from", "to", "angle", "image", "dim"), p)
        when (bg.opt("type")) {
            "color" -> color(c, bg, "color", p, true)
            "gradient" -> {
                color(c, bg, "from", p, true)
                color(c, bg, "to", p, true)
                if (bg.has("angle")) int(c, bg, "angle", p, 0, 359)
            }
            "image" -> {
                val img = bg.opt("image")
                if (img !is String || !IMAGE.matches(img)) c.err("$p.image", "nombre de imagen no válido (jpg, png o webp)")
                if (bg.has("dim")) int(c, bg, "dim", p, 0, 80)
                if (bg.has("color")) color(c, bg, "color", p, false)
            }
            else -> c.err("$p.type", "debe ser color, gradient o image")
        }
    }

    private fun validateElements(c: Ctx, arr: JSONArray?, path: String, rows: Int) {
        if (arr == null) { c.err(path, "falta"); return }
        if (arr.length() > 12) c.err(path, "máximo 12 elementos")
        val ids = HashSet<String>()
        val rects = ArrayList<Rect>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i)
            if (e == null) { c.err("$path[$i]", "debe ser un objeto"); continue }
            val id = e.opt("id") as? String
            val validId = id != null && SLUG.matches(id)
            val p = if (validId) "$path.$id" else "$path[$i]"
            if (!validId) c.err("$p.id", "identificador no válido")
            else if (!ids.add(id!!)) c.err(p, "identificador repetido")
            val type = e.opt("type") as? String
            val props = ELEMENT_PROPS[type]
            if (type == null || props == null) { c.err("$p.type", "tipo desconocido"); continue }
            unknownKeys(c, e, COMMON + props, p)
            val x = int(c, e, "x", p, 0, COLS - 1)
            val y = int(c, e, "y", p, 0, rows - 1)
            val w = int(c, e, "w", p, 1, COLS)
            val h = int(c, e, "h", p, 1, rows)
            if (x != null && w != null && x + w > COLS) c.err("$p.w", "se sale de la rejilla (x + w ≤ $COLS)")
            else if (y != null && h != null && y + h > rows) c.err("$p.h", "se sale de la rejilla (y + h ≤ $rows)")
            else if (x != null && y != null && w != null && h != null && validId) rects += Rect(id!!, x, y, w, h)
            oneOf(c, e, "color", p, ROLES)
            oneOf(c, e, "align", p, ALIGNS)
            validateProps(c, e, type, p)
        }
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            val a = rects[i]
            val b = rects[j]
            if (a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h) {
                c.err("$path.${b.id}", "se solapa con ${a.id}")
            }
        }
    }

    private fun validateProps(c: Ctx, e: JSONObject, type: String, p: String) {
        when (type) {
            "clock" -> { oneOf(c, e, "style", p, setOf("digital", "analog")); bool(c, e, "seconds", p); bool(c, e, "hour12", p) }
            "date" -> oneOf(c, e, "format", p, setOf("long", "short", "numeric"))
            "weather_now" -> bool(c, e, "detail", p)
            "forecast" -> { oneOf(c, e, "mode", p, setOf("hours", "days")); if (e.has("count")) int(c, e, "count", p, 2, 5) }
            "next_alarm" -> bool(c, e, "show_label", p)
            "text" -> {
                oneOf(c, e, "mode", p, setOf("fixed", "daily", "alarm_label"))
                if (e.has("text")) text(c, e, "text", p, 0, 120, false)
                if (e.has("quotes")) {
                    val q = e.optJSONArray("quotes")
                    if (q == null || q.length() > 20) c.err("$p.quotes", "debe ser una lista de hasta 20 frases")
                    else for (k in 0 until q.length()) {
                        val s = q.opt(k)
                        if (s !is String || s.length !in 1..120) c.err("$p.quotes[$k]", "debe ser un texto de 1 a 120 caracteres")
                    }
                }
            }
        }
    }

    private fun validateProfile(
        c: Ctx, pr: JSONObject?, i: Int, ids: MutableSet<String>, froms: MutableSet<String>,
        themeIds: Set<String>, screenIds: Set<String>,
    ) {
        val base = "profiles[$i]"
        if (pr == null) { c.err(base, "debe ser un objeto"); return }
        unknownKeys(c, pr, setOf("id", "name", "from", "theme", "screens", "rotate_s", "brightness", "night", "show_bar"), base)
        val id = pr.opt("id") as? String
        val validId = id != null && SLUG.matches(id)
        val p = if (validId) "profiles.$id" else base
        if (!validId) c.err("$p.id", "identificador no válido")
        else if (!ids.add(id!!)) c.err(p, "identificador repetido")
        text(c, pr, "name", p, 1, 30, true)
        val from = pr.opt("from") as? String
        if (from == null || !HM.matches(from)) c.err("$p.from", "debe ser HH:MM")
        else if (!froms.add(from)) c.err("$p.from", "hora repetida en otro perfil")
        val theme = pr.opt("theme") as? String
        if (theme == null || theme !in themeIds) c.err("$p.theme", "el tema no existe")
        val sc = pr.optJSONArray("screens")
        if (sc == null || sc.length() !in 1..6) c.err("$p.screens", "debe tener entre 1 y 6 pantallas")
        else for (k in 0 until sc.length()) {
            val s = sc.opt(k)
            if (s !is String || s !in screenIds) c.err("$p.screens[$k]", "la pantalla no existe")
        }
        if (pr.has("rotate_s")) {
            val r = (pr.opt("rotate_s") as? Number)?.toDouble()
            if (r == null || r % 1.0 != 0.0 || !(r == 0.0 || (r >= 5 && r <= 600))) c.err("$p.rotate_s", "debe ser 0 o un entero entre 5 y 600")
        }
        if (pr.has("brightness")) int(c, pr, "brightness", p, 1, 100)
        bool(c, pr, "night", p)
        bool(c, pr, "show_bar", p)
    }

    // ------------------------------------------------------------------ ayudas de validación

    private fun unknownKeys(c: Ctx, o: JSONObject, allowed: Set<String>, path: String) {
        for (k in o.keys()) if (k !in allowed) c.err(if (path.isEmpty()) k else "$path.$k", "campo desconocido")
    }

    private fun color(c: Ctx, o: JSONObject, key: String, path: String, required: Boolean) {
        if (!o.has(key)) { if (required) c.err("$path.$key", "falta"); return }
        val v = o.opt(key)
        if (v !is String || !COLOR.matches(v)) c.err("$path.$key", "debe ser un color #rrggbb")
    }

    private fun int(c: Ctx, o: JSONObject, key: String, path: String, min: Int, max: Int): Int? {
        val d = (o.opt(key) as? Number)?.toDouble()
        if (d == null || d % 1.0 != 0.0 || d < min || d > max) {
            c.err("$path.$key", "debe ser un entero entre $min y $max")
            return null
        }
        return d.toInt()
    }

    private fun bool(c: Ctx, o: JSONObject, key: String, path: String) {
        if (o.has(key) && o.opt(key) !is Boolean) c.err("$path.$key", "debe ser true o false")
    }

    private fun oneOf(c: Ctx, o: JSONObject, key: String, path: String, allowed: Set<String>, required: Boolean = false) {
        if (!o.has(key)) { if (required) c.err("$path.$key", "falta"); return }
        val v = o.opt(key)
        if (v !is String || v !in allowed) c.err("$path.$key", "debe ser ${allowed.joinToString(", ")}")
    }

    private fun text(c: Ctx, o: JSONObject, key: String, path: String, min: Int, max: Int, required: Boolean) {
        if (!o.has(key)) { if (required) c.err("$path.$key", "falta"); return }
        val v = o.opt(key)
        if (v !is String || v.trim().length < min || v.length > max) c.err("$path.$key", "debe ser un texto de $min a $max caracteres")
    }

    // ------------------------------------------------------------------ perfil activo

    fun minutesOf(hm: String): Int? {
        if (!HM.matches(hm)) return null
        return hm.substring(0, 2).toInt() * 60 + hm.substring(3, 5).toInt()
    }

    /** El perfil de mayor `from` ≤ la hora; si no hay ninguno, el de mayor `from` (de la víspera). */
    fun activeProfile(design: JSONObject, minuteOfDay: Int): JSONObject? {
        val arr = design.optJSONArray("profiles") ?: return null
        var best: JSONObject? = null
        var bestMin = -1
        var latest: JSONObject? = null
        var latestMin = -1
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val m = minutesOf(p.optString("from")) ?: continue
            if (m > latestMin) { latest = p; latestMin = m }
            if (m <= minuteOfDay && m > bestMin) { best = p; bestMin = m }
        }
        return best ?: latest
    }

    // ------------------------------------------------------------------ diseño inicial

    /** Diseño que se ve igual que la carátula de siempre; lo usa quien no ha guardado ninguno. */
    fun initial(dayBrightness: Int, nightBrightness: Int, nightStart: Int, nightEnd: Int): JSONObject {
        val day = dayBrightness.coerceIn(1, 100)
        val night = nightBrightness.coerceIn(1, 100)
        val start = nightStart.coerceIn(0, 23)
        val end = nightEnd.coerceIn(0, 23)

        val themes = JSONObject()
            .put("clasico", theme("Clásico", solid("#000000"), "#ffffff", "#90a4ae", "#4fc3f7", "thin"))
            .put("noche_roja", theme("Noche roja", solid("#000000"), "#8b0000", "#8b0000", "#8b0000", "thin"))
            .put("amanecer", theme("Amanecer", gradient("#1a2a4a", "#ff8a65", 160), "#ffffff", "#cfd8dc", "#ffd180", "thin"))
            .put("minimal", theme("Minimal", solid("#f5f5f5"), "#111111", "#666666", "#0277bd", "normal"))
            .put("digital_verde", theme("Digital verde", solid("#000000"), "#39ff14", "#1f8f0a", "#39ff14", "digital"))

        val principal = JSONArray()
            .put(el("hora", "clock", 0, 0, 8, 3, "style" to "digital"))
            .put(el("fecha", "date", 0, 3, 8, 1, "format" to "long", "color" to "muted"))
            .put(el("tiempo", "weather_now", 0, 4, 8, 1, "detail" to true, "color" to "muted"))
            .put(el("alarma", "next_alarm", 0, 5, 8, 1, "color" to "accent"))
        val noche = JSONArray()
            .put(el("hora", "clock", 0, 0, 8, 4, "style" to "digital"))
            .put(el("fecha", "date", 0, 4, 8, 1, "format" to "long"))
            .put(el("alarma", "next_alarm", 0, 5, 8, 1))
        val screens = JSONObject()
            .put("principal", JSONObject().put("name", "Principal").put("elements", principal))
            .put("noche", JSONObject().put("name", "Noche").put("elements", noche))

        val profiles = JSONArray()
        profiles.put(profile("dia", "Día", if (start == end) "00:00" else hm(end), "clasico", "principal", day, false))
        if (start != end) profiles.put(profile("noche", "Noche", hm(start), "noche_roja", "noche", night, true))

        val alarmElements = JSONArray()
            .put(el("hora", "clock", 0, 0, 8, 3, "style" to "digital"))
            .put(el("nombre", "text", 0, 3, 8, 1, "mode" to "alarm_label", "color" to "accent"))
        val alarm = JSONObject().put("theme", "clasico").put("elements", alarmElements)
            .put("snooze_label", "Posponer {min} min").put("dismiss_label", "Descartar")

        return JSONObject().put("version", 1).put("themes", themes).put("screens", screens)
            .put("profiles", profiles).put("alarm_screen", alarm)
    }

    private fun hm(hour: Int) = String.format(Locale.US, "%02d:00", hour)

    private fun solid(c: String) = JSONObject().put("type", "color").put("color", c)

    private fun gradient(from: String, to: String, angle: Int) =
        JSONObject().put("type", "gradient").put("from", from).put("to", to).put("angle", angle)

    private fun theme(name: String, bg: JSONObject, fg: String, muted: String, accent: String, font: String) =
        JSONObject().put("name", name).put("bg", bg).put("fg", fg).put("muted", muted).put("accent", accent).put("font", font)

    private fun el(id: String, type: String, x: Int, y: Int, w: Int, h: Int, vararg extra: Pair<String, Any>): JSONObject {
        val o = JSONObject().put("id", id).put("type", type).put("x", x).put("y", y).put("w", w).put("h", h)
        for ((k, v) in extra) o.put(k, v)
        return o
    }

    private fun profile(id: String, name: String, from: String, theme: String, screen: String, brightness: Int, night: Boolean) =
        JSONObject().put("id", id).put("name", name).put("from", from).put("theme", theme)
            .put("screens", JSONArray().put(screen)).put("rotate_s", 0).put("brightness", brightness)
            .put("night", night).put("show_bar", true)
}
```

- [ ] **Step 5: Ver que pasan.**
Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.jarvis.clock.DesignSchemaTest'`
Expected: PASS. Si el test `colores hexadecimales` falla por el redondeo de `darken`, ajusta **el valor esperado del test** (0xFF285078 × 0.25 = 0x0A141E con truncado), no la función.

- [ ] **Step 6: Enmendar la especificación.** En `docs/superpowers/specs/2026-10-06-pantallas-temas-design.md`:
  1. En la tabla "Elementos de 3a", fila `text`, cambiar la columna de opciones a: `` `mode` `fixed`/`daily`/`alarm_label`, `text`, `quotes` `` y añadir justo debajo de la tabla: "`alarm_label` muestra el nombre de la alarma que está sonando; fuera de la pantalla de alarma queda vacío."
  2. En "Pantalla de alarma", donde se habla de `snooze_label`, añadir: "`snooze_label` admite `{min}`, que se sustituye por los minutos de posponer."
  3. En "Errores y casos límite", sustituir la viñeta "Diseño guardado corrupto…" por: "Diseño guardado corrupto o inválido: el reloj usa el diseño inicial (siempre válido) y `GET /design` devuelve `"saved": false` para poder repararlo desde la web."
  4. En "Respaldo", añadir: "Tocar la pantalla en un perfil con `night` solo sube el brillo unos segundos (al del primer perfil que no sea de noche); el tema no cambia."
  5. En "Motor `face/`", sustituir "y la fuente de 7 segmentos para el estilo `digital`" por "El estilo `digital` del tema dibuja la hora con segmentos SVG generados por el propio motor (sin archivo de fuente); en el resto de textos equivale a `mono`."

- [ ] **Step 7: Commit**

```bash
git add app-android/src/main/java/com/jarvis/clock/design/ app-android/src/test/java/com/jarvis/clock/DesignSchemaTest.kt docs/superpowers/specs/2026-10-06-pantallas-temas-design.md
git commit -m "Pantallas: DesignSchema (validación todo o nada, diseño inicial y perfil activo)"
```

---

### Task 2: Motor `face/` (lógica pura, render y tests con `node --test`)

**Files:**
- Create: `app-android/src/main/assets/face/index.html`
- Create: `app-android/src/main/assets/face/face.css`
- Create: `app-android/src/main/assets/face/face-core.js`
- Create: `app-android/src/main/assets/face/face.js`
- Create: `app-android/face-tests/core.test.js`
- Create: `tools/sync-face.sh`
- Create (copia): `homelab/friday-panel/static/face/*`

**Interfaces:**
- Produces (`window.FaceCore` en el navegador, `module.exports` en node):
  `cellRect(el, width, height, rows) → {left,top,width,height}`, `minutesOf(hm)`, `activeProfile(profiles, minuteOfDay)`, `dayOfYear(date)`, `dailyQuote(quotes, date)`, `formatClock(date, hour12, seconds)`, `formatDate(date, fmt)`, `analogAngles(date) → {h,m,s}`, `fitText(len, boxW, boxH, maxPx, mode)` (`mode`: `"clock"`, `"line"` o `"block"`), `weatherLine(w, detail)`, `nextAlarmLine(na, showLabel)`, `forecastItems(mode, count, forecast)`, `bgCss(bg, base)`, `fontStyle(font) → {family,weight,digital}`, `justify(align)`.
- Produces (`window.Face`): `render(design, opts)` con `opts = {profile?, screen?, theme?, alarm?, edit?, selected?, grid?, bgBase?}`, `setData(data)`. Datos: `{weather:{temp,icon,description,max,min,rain}|null, forecast:{hours:[{h,temp,icon}],days:[{d,min,max,icon}]}|null, next_alarm:{label,when,time}|null, ringing_label?, exact_alarm_missing?}`.
- Puente nativo opcional `window.Native` con `tap()`, `doubleTap()`, `longPress()`, `permission()` y `error(msg)`.
- Mensajes `postMessage` (solo vista previa): entrada `{type:"face-render",design,opts}`, `{type:"face-data",data}`; salida `{type:"face-select",id}`, `{type:"face-resize",id,w,h}`, `{type:"face-empty",x,y}`.

- [ ] **Step 1: Escribir los tests que fallan.** Crea `app-android/face-tests/core.test.js`:

```js
"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const C = require("../src/main/assets/face/face-core.js");

test("cellRect reparte la rejilla 8x6 y la de la alarma 8x4", () => {
  assert.deepEqual(C.cellRect({ x: 2, y: 1, w: 3, h: 2 }, 800, 480, 6), { left: 200, top: 80, width: 300, height: 160 });
  assert.deepEqual(C.cellRect({ x: 0, y: 0, w: 8, h: 4 }, 800, 480, 4), { left: 0, top: 0, width: 800, height: 480 });
});

test("activeProfile: el de mayor from <= hora, con ciclo de medianoche", () => {
  const ps = [{ id: "dia", from: "07:00" }, { id: "noche", from: "23:00" }];
  assert.equal(C.activeProfile(ps, 3 * 60).id, "noche");
  assert.equal(C.activeProfile(ps, 7 * 60).id, "dia");
  assert.equal(C.activeProfile(ps, 22 * 60 + 59).id, "dia");
  assert.equal(C.activeProfile(ps, 23 * 60).id, "noche");
  assert.equal(C.activeProfile(ps, 0).id, "noche");
  assert.equal(C.activeProfile([], 100), null);
});

test("dayOfYear y frase del dia", () => {
  assert.equal(C.dayOfYear(new Date(2026, 0, 1)), 1);
  assert.equal(C.dayOfYear(new Date(2026, 9, 6)), 279);
  assert.equal(C.dailyQuote(["a", "b", "c"], new Date(2026, 0, 1)), "b");
  assert.equal(C.dailyQuote([], new Date(2026, 0, 1)), "");
  assert.equal(C.dailyQuote(undefined, new Date(2026, 0, 1)), "");
});

test("formatClock 24 h, 12 h y segundos", () => {
  assert.equal(C.formatClock(new Date(2026, 9, 6, 7, 5, 9), false, false), "07:05");
  assert.equal(C.formatClock(new Date(2026, 9, 6, 15, 4, 9), true, false), "3:04");
  assert.equal(C.formatClock(new Date(2026, 9, 6, 0, 0, 0), true, false), "12:00");
  assert.equal(C.formatClock(new Date(2026, 9, 6, 7, 5, 9), false, true), "07:05:09");
});

test("formatDate largo, corto y numerico", () => {
  const d = new Date(2026, 9, 6);
  assert.equal(C.formatDate(d, "long"), "Martes, 6 de octubre");
  assert.equal(C.formatDate(d, "short"), "mar 6 oct");
  assert.equal(C.formatDate(d, "numeric"), "06/10/2026");
  assert.equal(C.formatDate(d, "raro"), "Martes, 6 de octubre");
});

test("analogAngles", () => {
  const a = C.analogAngles(new Date(2026, 9, 6, 3, 15, 30));
  assert.ok(Math.abs(a.h - 97.5) < 1e-9);
  assert.ok(Math.abs(a.m - 93) < 1e-9);
  assert.equal(a.s, 180);
});

test("fitText: reloj, linea y bloque", () => {
  assert.equal(C.fitText(5, 800, 240, 9999, "clock"), 196);
  assert.equal(C.fitText(20, 800, 80, 9999, "line"), 40);
  assert.equal(C.fitText(19, 800, 160, 9999, "block"), 72);
  assert.equal(C.fitText(0, 800, 80, 9999, "line"), 40); // texto vacío: no revienta
  assert.equal(C.fitText(5000, 100, 80, 9999, "block"), 8); // nunca baja de 8 px
  assert.equal(C.fitText(5, 800, 240, 100, "clock"), 100); // respeta el máximo
  const size = C.fitText(200, 800, 160, 9999, "block");
  const perLine = Math.max(1, Math.floor(800 / (size * 0.55)));
  assert.ok(Math.ceil(200 / perLine) * size * 1.2 <= 160 || size === 8);
});

test("weatherLine y nextAlarmLine", () => {
  const w = { temp: 19, icon: "☀", description: "Despejado", max: 22, min: 12, rain: 10 };
  assert.equal(C.weatherLine(w, false), "☀ 19°");
  assert.equal(C.weatherLine(w, true), "☀ 19° · Despejado · ↑22° ↓12°");
  assert.equal(C.weatherLine({ temp: 8, icon: "☂", description: "Lluvia", max: 10, min: 6, rain: 80 }, true), "☂ 8° · Lluvia · ↑10° ↓6° · lluvia 80%");
  assert.equal(C.weatherLine(null, true), "—");
  const na = { label: "Trabajo", when: "mañana", time: "07:30" };
  assert.equal(C.nextAlarmLine(na, true), "⏰ Trabajo · mañana 07:30");
  assert.equal(C.nextAlarmLine(na, false), "⏰ mañana 07:30");
  assert.equal(C.nextAlarmLine(null, true), "");
});

test("forecastItems por horas y por dias, y sin datos", () => {
  const f = {
    hours: [{ h: 9, temp: 18, icon: "☀" }, { h: 11, temp: 21, icon: "☀" }, { h: 13, temp: 23, icon: "⛅" }],
    days: [{ d: "mié", min: 11, max: 22, icon: "☁" }],
  };
  assert.deepEqual(C.forecastItems("hours", 2, f), [{ label: "09h", icon: "☀", text: "18°" }, { label: "11h", icon: "☀", text: "21°" }]);
  assert.deepEqual(C.forecastItems("days", 5, f), [{ label: "mié", icon: "☁", text: "22°/11°" }]);
  assert.deepEqual(C.forecastItems("hours", 3, null), []);
  assert.equal(C.forecastItems("hours", 99, f).length, 3); // count se acota a 2..5
});

test("bgCss: color, degradado e imagen con color de respaldo", () => {
  assert.equal(C.bgCss({ type: "color", color: "#112233" }), "#112233");
  assert.equal(C.bgCss({ type: "gradient", from: "#000000", to: "#ffffff", angle: 90 }), "linear-gradient(90deg, #000000, #ffffff)");
  const img = C.bgCss({ type: "image", image: "mi playa.jpg", dim: 40, color: "#102030" }, "/bg/");
  assert.ok(img.indexOf('url("/bg/mi%20playa.jpg")') >= 0);
  assert.ok(img.indexOf("rgba(0,0,0,0.4)") >= 0);
  assert.ok(img.endsWith(", #102030")); // si la imagen falta, se ve el color
  assert.ok(C.bgCss({ type: "image", image: "x.jpg" }).endsWith(", #000000"));
  assert.equal(C.bgCss(undefined), "#000000");
});

test("fontStyle y justify", () => {
  assert.equal(C.fontStyle("thin").weight, 100);
  assert.equal(C.fontStyle("bold").weight, 700);
  assert.equal(C.fontStyle("digital").digital, true);
  assert.equal(C.fontStyle("desconocida").weight, 400);
  assert.equal(C.justify("left"), "flex-start");
  assert.equal(C.justify("right"), "flex-end");
  assert.equal(C.justify(undefined), "center");
});
```

- [ ] **Step 2: Ver que fallan.**
Run: `node --test app-android/face-tests/*.test.js`
Expected: FAIL (`Cannot find module …/face-core.js`).

- [ ] **Step 3: Crear `face-core.js`.**

```js
/* Lógica pura del motor de pantallas (sin DOM). La usan el WebView del reloj y la vista previa
   del panel. JavaScript clásico (ES2017): el WebView del reloj es Chrome 74 (sin ?. ni ??). */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.FaceCore = factory();
})(this, function () {
  "use strict";

  var COLS = 8;
  var DAYS = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
  var MONTHS = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
    "septiembre", "octubre", "noviembre", "diciembre"];

  function pad(n) { return (n < 10 ? "0" : "") + n; }

  function cellRect(el, width, height, rows) {
    var cw = width / COLS;
    var ch = height / rows;
    return { left: el.x * cw, top: el.y * ch, width: el.w * cw, height: el.h * ch };
  }

  function minutesOf(hm) {
    var m = /^(\d\d):(\d\d)$/.exec(hm || "");
    return m ? Number(m[1]) * 60 + Number(m[2]) : null;
  }

  function activeProfile(profiles, minuteOfDay) {
    var best = null, bestMin = -1, latest = null, latestMin = -1;
    for (var i = 0; i < (profiles || []).length; i++) {
      var m = minutesOf(profiles[i].from);
      if (m === null) continue;
      if (m > latestMin) { latest = profiles[i]; latestMin = m; }
      if (m <= minuteOfDay && m > bestMin) { best = profiles[i]; bestMin = m; }
    }
    return best || latest;
  }

  function dayOfYear(date) {
    var start = new Date(date.getFullYear(), 0, 0);
    var diff = date - start + (start.getTimezoneOffset() - date.getTimezoneOffset()) * 60000;
    return Math.floor(diff / 86400000);
  }

  function dailyQuote(quotes, date) {
    if (!quotes || !quotes.length) return "";
    return quotes[dayOfYear(date) % quotes.length];
  }

  function formatClock(date, hour12, seconds) {
    var h = date.getHours();
    if (hour12) { h = h % 12; if (h === 0) h = 12; }
    var s = (hour12 ? String(h) : pad(h)) + ":" + pad(date.getMinutes());
    if (seconds) s += ":" + pad(date.getSeconds());
    return s;
  }

  function formatDate(date, fmt) {
    var d = DAYS[date.getDay()];
    var mo = MONTHS[date.getMonth()];
    if (fmt === "short") return d.slice(0, 3) + " " + date.getDate() + " " + mo.slice(0, 3);
    if (fmt === "numeric") return pad(date.getDate()) + "/" + pad(date.getMonth() + 1) + "/" + date.getFullYear();
    return d.charAt(0).toUpperCase() + d.slice(1) + ", " + date.getDate() + " de " + mo;
  }

  function analogAngles(date) {
    var s = date.getSeconds(), m = date.getMinutes(), h = date.getHours() % 12;
    return { h: h * 30 + m * 0.5, m: m * 6 + s * 0.1, s: s * 6 };
  }

  /* Tamaño de letra (px) para que un texto de `len` caracteres quepa en la caja.
     "clock": una línea grande; "line": una línea; "block": varias líneas. Nunca baja de 8 px. */
  function fitText(len, boxW, boxH, maxPx, mode) {
    var n = Math.max(1, len | 0);
    var cap = maxPx || 9999;
    if (mode === "clock") return Math.max(8, Math.floor(Math.min(boxW / (n * 0.55), boxH * 0.82, cap)));
    if (mode === "line") return Math.max(8, Math.floor(Math.min(boxW / (n * 0.55), boxH * 0.5, cap)));
    var size = Math.floor(Math.min(boxH * 0.45, cap));
    while (size > 8) {
      var perLine = Math.max(1, Math.floor(boxW / (size * 0.55)));
      var lines = Math.ceil(n / perLine);
      if (lines * size * 1.2 <= boxH) break;
      size -= 1;
    }
    return Math.max(8, size);
  }

  function weatherLine(w, detail) {
    if (!w) return "—";
    var s = w.icon + " " + w.temp + "°";
    if (detail) {
      s += " · " + w.description + " · ↑" + w.max + "° ↓" + w.min + "°";
      if (w.rain >= 30) s += " · lluvia " + w.rain + "%";
    }
    return s;
  }

  function nextAlarmLine(na, showLabel) {
    if (!na) return "";
    return "⏰ " + (showLabel !== false ? na.label + " · " : "") + na.when + " " + na.time;
  }

  function forecastItems(mode, count, f) {
    if (!f) return [];
    var n = Math.max(2, Math.min(5, count || 4));
    if (mode === "days") {
      return (f.days || []).slice(0, n).map(function (d) {
        return { label: d.d, icon: d.icon, text: d.max + "°/" + d.min + "°" };
      });
    }
    return (f.hours || []).slice(0, n).map(function (h) {
      return { label: pad(h.h) + "h", icon: h.icon, text: h.temp + "°" };
    });
  }

  function bgCss(bg, base) {
    bg = bg || {};
    var fallback = bg.color || "#000000";
    if (bg.type === "gradient") {
      return "linear-gradient(" + (bg.angle == null ? 160 : bg.angle) + "deg, " + bg.from + ", " + bg.to + ")";
    }
    if (bg.type === "image" && bg.image) {
      var dim = (bg.dim || 0) / 100;
      var url = (base || "/bg/") + encodeURIComponent(bg.image);
      return "linear-gradient(rgba(0,0,0," + dim + "), rgba(0,0,0," + dim + ")), url(\"" + url + "\") center / cover no-repeat, " + fallback;
    }
    return fallback;
  }

  function fontStyle(font) {
    if (font === "thin") return { family: "\"sans-serif-thin\", sans-serif", weight: 100, digital: false };
    if (font === "bold") return { family: "sans-serif", weight: 700, digital: false };
    if (font === "mono") return { family: "monospace", weight: 400, digital: false };
    if (font === "digital") return { family: "monospace", weight: 400, digital: true };
    return { family: "sans-serif", weight: 400, digital: false };
  }

  function justify(align) {
    return align === "left" ? "flex-start" : align === "right" ? "flex-end" : "center";
  }

  return {
    COLS: COLS, cellRect: cellRect, minutesOf: minutesOf, activeProfile: activeProfile,
    dayOfYear: dayOfYear, dailyQuote: dailyQuote, formatClock: formatClock, formatDate: formatDate,
    analogAngles: analogAngles, fitText: fitText, weatherLine: weatherLine, nextAlarmLine: nextAlarmLine,
    forecastItems: forecastItems, bgCss: bgCss, fontStyle: fontStyle, justify: justify,
  };
});
```

- [ ] **Step 4: Ver que pasan.**
Run: `node --test app-android/face-tests/*.test.js`
Expected: PASS (11 tests). Si falla `fitText(19, 800, 160, 9999, "block")`, comprueba el bucle (empieza en `floor(160*0.45)=72`, 20 caracteres por línea, 1 línea, 86,4 ≤ 160).

- [ ] **Step 5: Crear `index.html` y `face.css`.**

`index.html`:

```html
<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
  <title>Friday · carátula</title>
  <link rel="stylesheet" href="face.css">
</head>
<body>
  <div id="root"><div id="content"></div></div>
  <script src="face-core.js"></script>
  <script src="face.js"></script>
</body>
</html>
```

`face.css` (sin `inset`, sin `gap`):

```css
html, body { margin: 0; height: 100%; background: #000; overflow: hidden; }
body { -webkit-user-select: none; user-select: none; -webkit-tap-highlight-color: transparent; }
#root {
  position: absolute; left: 0; top: 0; right: 0; bottom: 0; overflow: hidden;
  color: var(--fg, #fff); font-family: var(--ff, sans-serif); font-weight: var(--fw, 400);
  touch-action: none;
}
#content { position: absolute; left: 0; top: 0; right: 0; bottom: 0; transition: opacity 0.2s; }
.el { position: absolute; display: flex; align-items: center; box-sizing: border-box; overflow: hidden; }
.txt { white-space: nowrap; line-height: 1.05; }
.txt.wrap { white-space: normal; line-height: 1.15; }
.el svg { display: block; }
.fc { display: flex; }
.fcc { text-align: center; line-height: 1.2; }
.fcl { opacity: 0.7; }
.el.edit { outline: 1px dashed rgba(79, 195, 247, 0.55); outline-offset: -1px; }
.el.sel { outline: 2px solid #4fc3f7; background: rgba(79, 195, 247, 0.12); }
.handle { position: absolute; right: 0; bottom: 0; width: 26px; height: 26px; background: #4fc3f7; border-radius: 6px 0 0 0; touch-action: none; }
.gridv, .gridh { position: absolute; background: rgba(79, 195, 247, 0.22); pointer-events: none; }
.gridv { top: 0; bottom: 0; width: 1px; }
.gridh { left: 0; right: 0; height: 1px; }
```

- [ ] **Step 6: Crear `face.js` (render, gestos, vista previa).**

```js
/* Motor de pantallas de Friday: dibuja el diseño (JSON) en una rejilla de 8×6.
   Lo cargan el WebView del reloj y la vista previa del panel: el mismo código en los dos.
   JavaScript clásico (ES2017): el WebView del reloj es Chrome 74 (sin ?. ni ??). */
(function () {
  "use strict";
  var Core = window.FaceCore;
  var SVGNS = "http://www.w3.org/2000/svg";
  var COLS = Core.COLS;
  var PREVIEW = /[?&]preview=1/.test(location.search);

  var S = {
    design: null, opts: {}, data: {}, screens: [], index: 0, rows: 6, rotate: 0,
    W: 0, H: 0, updaters: [], rotateTimer: null,
  };
  var rootEl, contentEl, resizeTimer, lastMinute = -1;

  function native(name, arg) {
    var n = window.Native;
    if (!n || !n[name]) return;
    try { if (arg === undefined) n[name](); else n[name](String(arg)); } catch (e) { /* sin puente */ }
  }
  function post(msg) {
    if (window.parent && window.parent !== window) window.parent.postMessage(msg, location.origin);
  }
  function el(tag, cls) { var e = document.createElement(tag); if (cls) e.className = cls; return e; }
  function svg(tag, attrs) {
    var e = document.createElementNS(SVGNS, tag);
    for (var k in attrs) e.setAttribute(k, attrs[k]);
    return e;
  }

  // ------------------------------------------------------------------ elegir qué se dibuja

  function firstTheme(design) {
    var ids = Object.keys(design.themes || {});
    return ids.length ? design.themes[ids[0]]
      : { bg: { type: "color", color: "#000000" }, fg: "#ffffff", muted: "#90a4ae", accent: "#4fc3f7", font: "thin" };
  }
  function themeOf(design, id) { return (design.themes && design.themes[id]) || firstTheme(design); }

  function pick(design, opts) {
    if (opts.alarm) {
      var a = design.alarm_screen || {};
      return { screens: [{ elements: a.elements || [] }], rows: 4, rotate: 0, theme: themeOf(design, opts.theme || a.theme) };
    }
    var d = new Date();
    var profile = null;
    var profiles = design.profiles || [];
    for (var i = 0; i < profiles.length; i++) if (profiles[i].id === opts.profile) profile = profiles[i];
    if (!profile) profile = Core.activeProfile(profiles, d.getHours() * 60 + d.getMinutes());
    var list = [];
    if (opts.screen) {
      if (design.screens && design.screens[opts.screen]) list.push(design.screens[opts.screen]);
    } else if (profile) {
      for (var k = 0; k < (profile.screens || []).length; k++) {
        var sc = design.screens && design.screens[profile.screens[k]];
        if (sc) list.push(sc);
      }
    }
    return {
      screens: list, rows: 6, rotate: profile && !opts.screen ? (profile.rotate_s || 0) : 0,
      theme: themeOf(design, opts.theme || (profile && profile.theme)),
    };
  }

  function applyTheme(theme, bgBase) {
    var f = Core.fontStyle(theme.font);
    rootEl.style.background = Core.bgCss(theme.bg, bgBase);
    rootEl.style.setProperty("--fg", theme.fg);
    rootEl.style.setProperty("--muted", theme.muted);
    rootEl.style.setProperty("--accent", theme.accent);
    rootEl.style.setProperty("--ff", f.family);
    rootEl.style.setProperty("--fw", f.weight);
    rootEl.setAttribute("data-digital", f.digital ? "1" : "0");
  }

  // ------------------------------------------------------------------ dibujo

  function render(design, opts) {
    S.design = design;
    S.opts = opts || {};
    var p = pick(design, S.opts);
    S.screens = p.screens;
    S.rows = p.rows;
    S.rotate = p.rotate;
    if (S.index >= S.screens.length || S.opts.screen || S.opts.alarm) S.index = 0;
    applyTheme(p.theme, S.opts.bgBase);
    draw();
    scheduleRotation();
  }

  function draw() {
    S.W = rootEl.clientWidth;
    S.H = rootEl.clientHeight;
    contentEl.textContent = "";
    S.updaters = [];
    var screen = S.screens[S.index] || { elements: [] };
    var list = screen.elements || [];
    for (var i = 0; i < list.length; i++) {
      try { contentEl.appendChild(buildElement(list[i])); }
      catch (e) { native("error", "elemento " + list[i].id + ": " + e.message); }
    }
    if (S.opts.grid) buildGrid();
    updateAll();
  }

  function buildGrid() {
    for (var c = 1; c < COLS; c++) {
      var v = el("div", "gridv"); v.style.left = (S.W / COLS * c) + "px"; contentEl.appendChild(v);
    }
    for (var r = 1; r < S.rows; r++) {
      var h = el("div", "gridh"); h.style.top = (S.H / S.rows * r) + "px"; contentEl.appendChild(h);
    }
  }

  function updateAll() {
    var now = new Date();
    for (var i = 0; i < S.updaters.length; i++) S.updaters[i](now, S.data);
  }

  function buildElement(e) {
    var r = Core.cellRect(e, S.W, S.H, S.rows);
    var box = el("div", "el");
    box.setAttribute("data-id", e.id);
    box.style.left = r.left + "px"; box.style.top = r.top + "px";
    box.style.width = r.width + "px"; box.style.height = r.height + "px";
    box.style.color = "var(--" + (e.color || "fg") + ")";
    box.style.justifyContent = Core.justify(e.align);
    box.style.textAlign = e.align || "center";
    var build = builders[e.type];
    if (build) build(e, box, r);
    if (S.opts.edit) {
      box.classList.add("edit");
      if (S.opts.selected === e.id) { box.classList.add("sel"); box.appendChild(el("div", "handle")); }
    }
    return box;
  }

  // Un elemento de texto: calcula el texto, lo ajusta a su caja y solo toca el DOM si cambia.
  function textBuilder(box, r, getText, mode, onNode) {
    var node = el("div", "txt" + (mode === "block" ? " wrap" : ""));
    box.appendChild(node);
    if (onNode) onNode(node);
    var last = null;
    S.updaters.push(function (now, data) {
      var s = getText(now, data);
      if (s === last) return;
      last = s;
      node.textContent = s;
      node.style.fontSize = Core.fitText(s.length, r.width, r.height, 9999, mode) + "px";
    });
  }

  var SEG = { 0: "abcdef", 1: "bc", 2: "abged", 3: "abgcd", 4: "fgbc", 5: "afgcd", 6: "afgedc", 7: "abc", 8: "abcdefg", 9: "abcdfg" };
  var RECTS = { a: [10, 0, 50, 12], b: [58, 10, 12, 46], c: [58, 64, 12, 46], d: [10, 108, 50, 12], e: [0, 64, 12, 46], f: [0, 10, 12, 46], g: [10, 54, 50, 12] };

  function sevenSeg(text) {
    var x = 0;
    var g = svg("g", {});
    for (var i = 0; i < text.length; i++) {
      var ch = text.charAt(i);
      if (ch === ":") {
        g.appendChild(svg("rect", { x: x + 14, y: 30, width: 12, height: 12, rx: 3, fill: "currentColor" }));
        g.appendChild(svg("rect", { x: x + 14, y: 78, width: 12, height: 12, rx: 3, fill: "currentColor" }));
        x += 40;
        continue;
      }
      if (ch === " ") { x += 40; continue; }
      var on = SEG[ch] || "";
      for (var seg in RECTS) {
        var q = RECTS[seg];
        g.appendChild(svg("rect", {
          x: x + q[0], y: q[1], width: q[2], height: q[3], rx: 3, fill: "currentColor",
          opacity: on.indexOf(seg) >= 0 ? 1 : 0.1,
        }));
      }
      x += 90;
    }
    var root = svg("svg", { viewBox: "0 0 " + Math.max(x - 20, 10) + " 120", preserveAspectRatio: "xMidYMid meet" });
    root.style.width = "100%";
    root.style.height = "100%";
    root.appendChild(g);
    return root;
  }

  function hand(parent, len, width) {
    var l = svg("line", { x1: 100, y1: 100, x2: 100, y2: 100 - len, stroke: "currentColor", "stroke-width": width, "stroke-linecap": "round" });
    parent.appendChild(l);
    return l;
  }

  function buildAnalog(e, box, r) {
    var size = Math.min(r.width, r.height);
    var s = svg("svg", { viewBox: "0 0 200 200", width: size, height: size });
    s.appendChild(svg("circle", { cx: 100, cy: 100, r: 94, fill: "none", stroke: "currentColor", "stroke-width": 3 }));
    for (var i = 0; i < 12; i++) {
      var big = i % 3 === 0;
      s.appendChild(svg("line", {
        x1: 100, y1: 8, x2: 100, y2: big ? 22 : 15, stroke: "currentColor",
        "stroke-width": big ? 4 : 2, transform: "rotate(" + (i * 30) + " 100 100)",
      }));
    }
    var hh = hand(s, 52, 6), mm = hand(s, 78, 4), ss = e.seconds ? hand(s, 86, 2) : null;
    box.appendChild(s);
    S.updaters.push(function (now) {
      var a = Core.analogAngles(now);
      hh.setAttribute("transform", "rotate(" + a.h + " 100 100)");
      mm.setAttribute("transform", "rotate(" + a.m + " 100 100)");
      if (ss) ss.setAttribute("transform", "rotate(" + a.s + " 100 100)");
    });
  }

  var builders = {
    clock: function (e, box, r) {
      if (e.style === "analog") { buildAnalog(e, box, r); return; }
      var node = el("div", "txt");
      box.appendChild(node);
      var digital = rootEl.getAttribute("data-digital") === "1";
      if (digital) { node.style.width = r.width + "px"; node.style.height = r.height + "px"; }
      var last = "";
      S.updaters.push(function (now) {
        var s = Core.formatClock(now, !!e.hour12, !!e.seconds);
        if (s === last) return;
        last = s;
        if (digital) { node.textContent = ""; node.appendChild(sevenSeg(s)); }
        else { node.textContent = s; node.style.fontSize = Core.fitText(s.length, r.width, r.height, 9999, "clock") + "px"; }
      });
    },
    date: function (e, box, r) {
      textBuilder(box, r, function (now) { return Core.formatDate(now, e.format || "long"); }, "line");
    },
    weather_now: function (e, box, r) {
      textBuilder(box, r, function (now, data) { return Core.weatherLine(data.weather, !!e.detail); }, "line");
    },
    next_alarm: function (e, box, r) {
      textBuilder(box, r, function (now, data) {
        if (data.exact_alarm_missing) return "⚠ Concede el permiso de alarmas exactas";
        return Core.nextAlarmLine(data.next_alarm, e.show_label);
      }, "line", function (node) {
        node.addEventListener("click", function () { if (S.data.exact_alarm_missing) native("permission"); });
      });
    },
    text: function (e, box, r) {
      textBuilder(box, r, function (now, data) {
        if (e.mode === "alarm_label") return data.ringing_label || "";
        if (e.mode === "daily") return Core.dailyQuote(e.quotes, now);
        return e.text || "";
      }, "block");
    },
    forecast: function (e, box, r) {
      var wrap = el("div", "fc");
      box.appendChild(wrap);
      var last = "";
      S.updaters.push(function (now, data) {
        var items = Core.forecastItems(e.mode || "hours", e.count || 4, data.forecast);
        var key = JSON.stringify(items);
        if (key === last) return;
        last = key;
        wrap.textContent = "";
        if (!items.length) { wrap.textContent = "—"; wrap.style.fontSize = Core.fitText(1, r.width, r.height, 9999, "line") + "px"; return; }
        var colW = r.width / items.length;
        var longest = 3;
        for (var i = 0; i < items.length; i++) longest = Math.max(longest, items[i].text.length);
        var size = Core.fitText(longest, colW, r.height / 3, 9999, "clock");
        for (var j = 0; j < items.length; j++) {
          var c = el("div", "fcc");
          c.style.width = colW + "px";
          c.style.fontSize = size + "px";
          var a = el("div", "fcl"); a.textContent = items[j].label;
          var b = el("div"); b.textContent = items[j].icon;
          var t = el("div"); t.textContent = items[j].text;
          c.appendChild(a); c.appendChild(b); c.appendChild(t);
          wrap.appendChild(c);
        }
      });
    },
  };

  // ------------------------------------------------------------------ rotación y deslizar

  function go(dir) {
    if (S.screens.length < 2) return;
    S.index = (S.index + dir + S.screens.length) % S.screens.length;
    contentEl.style.opacity = 0;
    setTimeout(function () { draw(); contentEl.style.opacity = 1; }, 220);
    scheduleRotation();
  }

  function scheduleRotation() {
    clearInterval(S.rotateTimer);
    if (!PREVIEW && S.rotate >= 5 && S.screens.length > 1) {
      S.rotateTimer = setInterval(function () { go(1); }, S.rotate * 1000);
    }
  }

  // ------------------------------------------------------------------ toques (reloj)

  var g = { down: false, moved: false, fired: false, x0: 0, y0: 0, lastTap: 0, longT: null, singleT: null };

  function gestureDown(ev) {
    g.down = true; g.moved = false; g.fired = false; g.x0 = ev.clientX; g.y0 = ev.clientY;
    clearTimeout(g.longT);
    g.longT = setTimeout(function () {
      if (g.down && !g.moved) { g.down = false; g.fired = true; native("longPress"); }
    }, 650);
  }
  function gestureMove(ev) {
    if (!g.down) return;
    if (Math.abs(ev.clientX - g.x0) > 12 || Math.abs(ev.clientY - g.y0) > 12) { g.moved = true; clearTimeout(g.longT); }
  }
  function gestureUp(ev) {
    clearTimeout(g.longT);
    if (g.fired) { g.fired = false; return; }
    if (!g.down) return;
    g.down = false;
    var dx = ev.clientX - g.x0, dy = ev.clientY - g.y0;
    if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) { go(dx < 0 ? 1 : -1); return; }
    if (g.moved) return;
    var now = Date.now();
    if (now - g.lastTap < 350) { clearTimeout(g.singleT); g.lastTap = 0; native("doubleTap"); return; }
    g.lastTap = now;
    clearTimeout(g.singleT);
    g.singleT = setTimeout(function () { native("tap"); }, 360);
  }

  // ------------------------------------------------------------------ edición (vista previa)

  var drag = null;

  function findElement(id) {
    var list = (S.screens[S.index] || {}).elements || [];
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return list[i];
    return null;
  }
  function cellAt(clientX, clientY) {
    var b = rootEl.getBoundingClientRect();
    var cx = Math.floor((clientX - b.left) / (b.width / COLS));
    var cy = Math.floor((clientY - b.top) / (b.height / S.rows));
    return { x: Math.max(0, Math.min(COLS - 1, cx)), y: Math.max(0, Math.min(S.rows - 1, cy)) };
  }

  function editDown(ev) {
    var t = ev.target;
    var handle = t.closest ? t.closest(".handle") : null;
    var box = t.closest ? t.closest(".el") : null;
    if (handle && box) {
      var data = findElement(box.getAttribute("data-id"));
      if (!data) return;
      drag = { id: data.id, box: box, el: data, w: data.w, h: data.h };
      try { handle.setPointerCapture(ev.pointerId); } catch (e) { /* ignorar */ }
      ev.preventDefault();
      return;
    }
    if (box) { post({ type: "face-select", id: box.getAttribute("data-id") }); return; }
    var c = cellAt(ev.clientX, ev.clientY);
    post({ type: "face-empty", x: c.x, y: c.y });
  }
  function editMove(ev) {
    if (!drag) return;
    var c = cellAt(ev.clientX, ev.clientY);
    drag.w = Math.max(1, Math.min(COLS - drag.el.x, c.x - drag.el.x + 1));
    drag.h = Math.max(1, Math.min(S.rows - drag.el.y, c.y - drag.el.y + 1));
    var r = Core.cellRect({ x: drag.el.x, y: drag.el.y, w: drag.w, h: drag.h }, S.W, S.H, S.rows);
    drag.box.style.width = r.width + "px";
    drag.box.style.height = r.height + "px";
  }
  function editUp() {
    if (!drag) return;
    var d = drag;
    drag = null;
    post({ type: "face-resize", id: d.id, w: d.w, h: d.h });
  }

  // ------------------------------------------------------------------ API pública

  function setData(data) { S.data = data || {}; updateAll(); }

  function tick() {
    updateAll();
    var m = new Date().getMinutes();
    if (m !== lastMinute) {
      lastMinute = m;
      if (!PREVIEW) { // antidesgaste: la carátula se desplaza unos píxeles cada minuto
        contentEl.style.transform = "translate(" + (((m % 5) - 2) * 6) + "px," + ((Math.floor(m / 5) % 3 - 1) * 6) + "px)";
      }
    }
  }

  function init() {
    rootEl = document.getElementById("root");
    contentEl = document.getElementById("content");
    rootEl.addEventListener("pointerdown", function (ev) { if (S.opts.edit) editDown(ev); else gestureDown(ev); });
    rootEl.addEventListener("pointermove", function (ev) { if (S.opts.edit) editMove(ev); else gestureMove(ev); });
    rootEl.addEventListener("pointerup", function (ev) { if (S.opts.edit) editUp(); else gestureUp(ev); });
    rootEl.addEventListener("pointercancel", function () { g.down = false; drag = null; clearTimeout(g.longT); });
    window.addEventListener("resize", function () {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(function () { if (S.design) draw(); }, 150);
    });
    window.addEventListener("message", function (ev) {
      if (ev.origin !== location.origin) return;
      var m = ev.data;
      if (!m || typeof m !== "object") return;
      if (m.type === "face-render") render(m.design, m.opts || {});
      else if (m.type === "face-data") setData(m.data || {});
    });
    setInterval(tick, 1000);
  }

  window.Face = { render: render, setData: setData };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
```

- [ ] **Step 7: Comprobar sintaxis y tests.**
Run: `node --check app-android/src/main/assets/face/face.js && node --check app-android/src/main/assets/face/face-core.js && node --test app-android/face-tests/*.test.js`
Expected: sin errores; tests PASS.

- [ ] **Step 8: Script de sincronización y primera copia.** Crea `tools/sync-face.sh`:

```bash
#!/usr/bin/env bash
# Copia el motor de pantallas del APK al panel (la vista previa usa exactamente los mismos archivos).
#   bash tools/sync-face.sh           copia
#   bash tools/sync-face.sh --check   falla si las dos copias difieren (lo usa la CI)
set -euo pipefail
cd "$(dirname "$0")/.."
SRC=app-android/src/main/assets/face
DST=homelab/friday-panel/static/face
if [ "${1:-}" = "--check" ]; then
  diff -r "$SRC" "$DST"
  echo "face/ sincronizado"
  exit 0
fi
mkdir -p "$DST"
cp "$SRC"/* "$DST"/
echo "copiado $SRC -> $DST"
```

Run: `chmod +x tools/sync-face.sh && bash tools/sync-face.sh && bash tools/sync-face.sh --check`
Expected: `copiado …` y `face/ sincronizado`.

- [ ] **Step 9: Commit**

```bash
git add app-android/src/main/assets/face app-android/face-tests tools/sync-face.sh homelab/friday-panel/static/face
git commit -m "Pantallas: motor face/ (rejilla, temas, gestos, vista previa) con tests node y copia para el panel"
```

---
### Task 3: Previsión del tiempo y `FaceData` (datos para el motor)

**Files:**
- Modify: `app-android/src/main/java/com/jarvis/clock/weather/Weather.kt`
- Create: `app-android/src/main/java/com/jarvis/clock/design/FaceData.kt`
- Test: `app-android/src/test/java/com/jarvis/clock/WeatherParseTest.kt`
- Test: `app-android/src/test/java/com/jarvis/clock/FaceDataTest.kt`

**Interfaces:**
- Produces:
  - `Weather.Hour(h: Int, temp: Int, code: Int)` y `Weather.Day(d: String, min: Int, max: Int, code: Int)`, ambos con `icon: String`.
  - `Weather.Report` gana `hours: List<Hour>` y `days: List<Day>` (por defecto vacías).
  - `Weather.parse(body: String, now: Long = System.currentTimeMillis()): Report` (puro, testeable).
  - `Weather.icon(code: Int): String` pasa a ser pública.
  - `FaceData.dayWord(nowMs: Long, atMs: Long): String` (`"hoy"`, `"mañana"` o `"lun"`…`"dom"`)
  - `FaceData.nextAlarmJson(label: String, timeText: String, nowMs: Long, atMs: Long): JSONObject` → `{label, when, time}`
  - `FaceData.weatherJson(r: Weather.Report?): Any`, `FaceData.forecastJson(r: Weather.Report?): Any` (`JSONObject.NULL` si no hay datos)
  - `FaceData.versionOf(chunks: List<ByteArray>): String` (SHA-256 hex, primeros 12) y `FaceData.faceVersion(ctx: Context): String` (sobre `index.html`, `face.css`, `face-core.js`, `face.js`, en ese orden)
  - `FaceData.build(ctx: Context, ringingLabel: String? = null): JSONObject` → `{weather, forecast, next_alarm, exact_alarm_missing, ringing_label?}`

- [ ] **Step 1: Tests que fallan.** `WeatherParseTest.kt`:

```kotlin
package com.jarvis.clock

import com.jarvis.clock.weather.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherParseTest {

    private val sample = """
    {"current":{"temperature_2m":19.4,"weather_code":1},
     "hourly":{
       "time":["2026-10-06T14:00","2026-10-06T15:00","2026-10-06T16:00","2026-10-06T17:00","2026-10-06T18:00","2026-10-06T19:00",
               "2026-10-06T20:00","2026-10-06T21:00","2026-10-06T22:00","2026-10-06T23:00","2026-10-07T00:00","2026-10-07T01:00"],
       "temperature_2m":[19.4,20.1,20.6,21.0,20.2,19.5,18.8,17.9,17.2,16.4,15.9,15.2],
       "weather_code":[1,1,2,2,3,3,3,61,61,3,3,2]},
     "daily":{
       "time":["2026-10-06","2026-10-07","2026-10-08","2026-10-09","2026-10-10","2026-10-11"],
       "temperature_2m_max":[22.2,23.0,19.6,18.0,20.4,21.1],
       "temperature_2m_min":[12.1,11.9,10.2,9.8,10.0,11.6],
       "precipitation_probability_max":[10,5,60,80,20,0],
       "weather_code":[1,3,61,63,2,0]}}
    """.trimIndent()

    @Test fun `lee el tiempo actual y el resumen de hoy`() {
        val r = Weather.parse(sample, now = 1234L)
        assertEquals(19, r.temp); assertEquals(1, r.code)
        assertEquals(22, r.max); assertEquals(12, r.min); assertEquals(10, r.rainProb)
        assertEquals(1234L, r.fetchedAt)
    }

    @Test fun `previsión por horas cada dos horas desde la siguiente`() {
        val h = Weather.parse(sample).hours
        assertEquals(listOf(15, 17, 19, 21, 23), h.map { it.h })
        assertEquals(listOf(20, 21, 20, 18, 16), h.map { it.temp })
        assertEquals(listOf(1, 2, 3, 61, 3), h.map { it.code })
        assertEquals("☀", h[0].icon)
    }

    @Test fun `previsión por días desde mañana con nombre corto`() {
        val d = Weather.parse(sample).days
        assertEquals(listOf("mié", "jue", "vie", "sáb", "dom"), d.map { it.d })
        assertEquals(listOf(12, 10, 10, 10, 12), d.map { it.min })
        assertEquals(listOf(23, 20, 18, 20, 21), d.map { it.max })
        assertEquals(listOf(3, 61, 63, 2, 0), d.map { it.code })
    }

    @Test fun `sin bloque hourly o sin weather_code no revienta`() {
        val sinHoras = """{"current":{"temperature_2m":10.0,"weather_code":0},
            "daily":{"time":["2026-10-06","2026-10-07"],"temperature_2m_max":[12.0,13.0],"temperature_2m_min":[5.0,6.0]}}"""
        val r = Weather.parse(sinHoras)
        assertTrue(r.hours.isEmpty())
        assertEquals(1, r.days.size)
        assertEquals(0, r.days[0].code)
    }
}
```

`FaceDataTest.kt`:

```kotlin
package com.jarvis.clock

import com.jarvis.clock.design.FaceData
import com.jarvis.clock.weather.Weather
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FaceDataTest {
    private val saved = TimeZone.getDefault()
    @Before fun setUp() { TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam")) }
    @After fun tearDown() { TimeZone.setDefault(saved) }

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, mo - 1, d, h, mi) }.timeInMillis

    @Test fun `hoy, mañana y dia de la semana`() {
        val now = at(2026, 10, 6, 22, 0)
        assertEquals("hoy", FaceData.dayWord(now, at(2026, 10, 6, 23, 30)))
        assertEquals("mañana", FaceData.dayWord(now, at(2026, 10, 7, 6, 0)))
        assertEquals("vie", FaceData.dayWord(now, at(2026, 10, 9, 7, 0)))
    }

    @Test fun `mañana cruza el fin de año`() {
        assertEquals("mañana", FaceData.dayWord(at(2026, 12, 31, 22, 0), at(2027, 1, 1, 7, 0)))
    }

    @Test fun `alarma siguiente como json`() {
        val j = FaceData.nextAlarmJson("Trabajo", "07:30", at(2026, 10, 6, 22, 0), at(2026, 10, 7, 7, 30))
        assertEquals("Trabajo", j.getString("label"))
        assertEquals("mañana", j.getString("when"))
        assertEquals("07:30", j.getString("time"))
    }

    @Test fun `sin datos del tiempo son null`() {
        assertEquals(JSONObject.NULL, FaceData.weatherJson(null))
        assertEquals(JSONObject.NULL, FaceData.forecastJson(null))
    }

    @Test fun `tiempo y previsión como json`() {
        val r = Weather.Report(
            temp = 19, code = 1, max = 22, min = 12, rainProb = 10, fetchedAt = 0L,
            hours = listOf(Weather.Hour(15, 20, 1)), days = listOf(Weather.Day("mié", 12, 23, 3)),
        )
        val w = FaceData.weatherJson(r) as JSONObject
        assertEquals(19, w.getInt("temp")); assertEquals("☀", w.getString("icon")); assertEquals("Despejado", w.getString("description"))
        assertEquals(22, w.getInt("max")); assertEquals(12, w.getInt("min")); assertEquals(10, w.getInt("rain"))
        val f = FaceData.forecastJson(r) as JSONObject
        assertEquals(15, f.getJSONArray("hours").getJSONObject(0).getInt("h"))
        assertEquals("mié", f.getJSONArray("days").getJSONObject(0).getString("d"))
        assertTrue(f.getJSONArray("days").getJSONObject(0).getString("icon").isNotEmpty())
    }

    @Test fun `la version del motor es sha-256 abreviado`() {
        assertEquals("ba7816bf8f01", FaceData.versionOf(listOf("abc".toByteArray())))
        assertEquals("ba7816bf8f01", FaceData.versionOf(listOf("a".toByteArray(), "bc".toByteArray())))
    }
}
```

- [ ] **Step 2: Ver que fallan.**
Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.jarvis.clock.WeatherParseTest' --tests 'com.jarvis.clock.FaceDataTest'`
Expected: FAIL de compilación (`Weather.parse`, `FaceData` no existen).

- [ ] **Step 3: Ampliar `Weather.kt`.** Sustituye la clase `Report`, `fetch()` y `icon()` (el resto, incluidos `get`, `cached` y `describe`, queda igual):

```kotlin
    data class Hour(val h: Int, val temp: Int, val code: Int) {
        val icon get() = Weather.icon(code)
    }

    data class Day(val d: String, val min: Int, val max: Int, val code: Int) {
        val icon get() = Weather.icon(code)
    }

    data class Report(
        val temp: Int,
        val code: Int,
        val max: Int,
        val min: Int,
        val rainProb: Int,
        val fetchedAt: Long,
        val hours: List<Hour> = emptyList(),
        val days: List<Day> = emptyList(),
    ) {
        val description get() = describe(code)
        val icon get() = icon(code)
        fun shortText() = "$icon $temp° · ${description} · ↑$max° ↓$min°" + if (rainProb >= 30) " · lluvia $rainProb%" else ""
        fun spoken(city: String) = buildString {
            append("En $city hace $temp grados, ${description.lowercase()}. ")
            append("Hoy máxima de $max y mínima de $min.")
            if (rainProb >= 40) append(" Hay un $rainProb por ciento de probabilidad de lluvia, coge paraguas.")
        }
    }
```

```kotlin
    private fun fetch(): Report {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m,weather_code&forecast_hours=12" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code&forecast_days=6" +
                "&timezone=auto",
            Prefs.latitude, Prefs.longitude,
        )
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            return parse(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    /**
     * Interpreta la respuesta de Open-Meteo. Las horas son las siguientes cada dos horas
     * (la primera entrada es la hora actual) y los días empiezan mañana.
     */
    fun parse(body: String, now: Long = System.currentTimeMillis()): Report {
        val o = JSONObject(body)
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val dTimes = d.getJSONArray("time")
        val dMax = d.getJSONArray("temperature_2m_max")
        val dMin = d.getJSONArray("temperature_2m_min")
        val dCode = d.optJSONArray("weather_code")
        val days = ArrayList<Day>()
        for (i in 1 until dTimes.length()) {
            days += Day(dayName(dTimes.getString(i)), dMin.getDouble(i).roundToInt(), dMax.getDouble(i).roundToInt(), dCode?.optInt(i) ?: 0)
        }
        val hours = ArrayList<Hour>()
        o.optJSONObject("hourly")?.let { hr ->
            val t = hr.getJSONArray("time")
            val temp = hr.getJSONArray("temperature_2m")
            val code = hr.optJSONArray("weather_code")
            var i = 1
            while (i < t.length() && hours.size < 5) {
                hours += Hour(t.getString(i).substring(11, 13).toInt(), temp.getDouble(i).roundToInt(), code?.optInt(i) ?: 0)
                i += 2
            }
        }
        return Report(
            temp = cur.getDouble("temperature_2m").roundToInt(),
            code = cur.getInt("weather_code"),
            max = dMax.getDouble(0).roundToInt(),
            min = dMin.getDouble(0).roundToInt(),
            rainProb = d.optJSONArray("precipitation_probability_max")?.optInt(0) ?: 0,
            fetchedAt = now,
            hours = hours,
            days = days,
        )
    }

    private val DAY_SHORT = arrayOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")

    /** "2026-10-07" -> "mié" (sin depender del idioma de la JVM). */
    private fun dayName(date: String): String {
        val (y, m, d) = date.split("-").map { it.toInt() }
        val dow = java.util.GregorianCalendar(y, m - 1, d).get(java.util.Calendar.DAY_OF_WEEK)
        return DAY_SHORT[dow - 1]
    }
```

Y cambia `private fun icon(code: Int): String` por `fun icon(code: Int): String` (pública).

- [ ] **Step 4: Crear `FaceData.kt`.**

```kotlin
package com.jarvis.clock.design

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import com.jarvis.clock.alarm.AlarmScheduler
import com.jarvis.clock.weather.Weather
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Calendar

/** Los datos que recibe el motor de pantallas (los mismos en el reloj y en la vista previa del panel). */
object FaceData {
    private val DAY_SHORT = arrayOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")
    private val FILES = listOf("index.html", "face.css", "face-core.js", "face.js")

    private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** "hoy", "mañana" o el día de la semana abreviado. */
    fun dayWord(nowMs: Long, atMs: Long): String {
        val diff = Math.round((startOfDay(atMs) - startOfDay(nowMs)) / 86_400_000.0).toInt()
        return when (diff) {
            0 -> "hoy"
            1 -> "mañana"
            else -> DAY_SHORT[Calendar.getInstance().apply { timeInMillis = atMs }.get(Calendar.DAY_OF_WEEK) - 1]
        }
    }

    fun nextAlarmJson(label: String, timeText: String, nowMs: Long, atMs: Long): JSONObject =
        JSONObject().put("label", label).put("when", dayWord(nowMs, atMs)).put("time", timeText)

    fun weatherJson(r: Weather.Report?): Any =
        if (r == null) JSONObject.NULL else JSONObject()
            .put("temp", r.temp).put("icon", r.icon).put("description", r.description)
            .put("max", r.max).put("min", r.min).put("rain", r.rainProb)

    fun forecastJson(r: Weather.Report?): Any =
        if (r == null) JSONObject.NULL else JSONObject()
            .put("hours", JSONArray(r.hours.map { JSONObject().put("h", it.h).put("temp", it.temp).put("icon", it.icon) }))
            .put("days", JSONArray(r.days.map { JSONObject().put("d", it.d).put("min", it.min).put("max", it.max).put("icon", it.icon) }))

    /** SHA-256 de los archivos del motor, abreviado: el panel calcula el mismo para avisar si difieren. */
    fun versionOf(chunks: List<ByteArray>): String {
        val md = MessageDigest.getInstance("SHA-256")
        chunks.forEach { md.update(it) }
        return md.digest().joinToString("") { "%02x".format(it) }.take(12)
    }

    @Volatile private var version: String? = null

    fun faceVersion(ctx: Context): String =
        version ?: runCatching {
            versionOf(FILES.map { name -> ctx.assets.open("face/$name").use { it.readBytes() } })
        }.getOrDefault("?").also { version = it }

    /** Datos actuales: tiempo y previsión de la caché, próxima alarma y si falta el permiso de alarmas exactas. */
    fun build(ctx: Context, ringingLabel: String? = null): JSONObject {
        val now = System.currentTimeMillis()
        val w = Weather.cached()
        val next = AlarmScheduler.nextAlarm(ctx)
        val exactMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val o = JSONObject()
            .put("weather", weatherJson(w))
            .put("forecast", forecastJson(w))
            .put("next_alarm", next?.let { (a, at) -> nextAlarmJson(a.label, a.timeText(), now, at) } ?: JSONObject.NULL)
            .put("exact_alarm_missing", exactMissing)
        if (ringingLabel != null) o.put("ringing_label", ringingLabel)
        return o
    }
}
```

- [ ] **Step 5: Ver que pasan (y el resto sigue bien).**
Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, todos los tests en verde.

- [ ] **Step 6: Commit**

```bash
git add app-android/src/main/java/com/jarvis/clock/weather/Weather.kt app-android/src/main/java/com/jarvis/clock/design/FaceData.kt app-android/src/test/java/com/jarvis/clock/WeatherParseTest.kt app-android/src/test/java/com/jarvis/clock/FaceDataTest.kt
git commit -m "Pantallas: previsión por horas y días y datos del motor (FaceData)"
```

---

### Task 4: `DesignStore`, fondos, eventos y API del diseño

**Files:**
- Create: `app-android/src/main/java/com/jarvis/clock/design/DesignStore.kt`
- Create: `app-android/src/main/java/com/jarvis/clock/design/Backgrounds.kt`
- Modify: `app-android/src/main/java/com/jarvis/clock/Events.kt`
- Modify: `app-android/src/main/java/com/jarvis/clock/MainActivity.kt` (solo ramas de `onEvent`)
- Modify: `app-android/src/main/java/com/jarvis/clock/assistant/Actions.kt`
- Modify: `app-android/src/main/java/com/jarvis/clock/server/Api.kt`
- Test: `app-android/src/test/java/com/jarvis/clock/DesignStoreTest.kt`
- Test: `app-android/src/test/java/com/jarvis/clock/BackgroundsTest.kt`

**Interfaces:**
- Consumes: `DesignSchema.validate/initial`, `FaceData.faceVersion/build`, `Weather.get`.
- Produces:
  - `DesignStore.Loaded(design: JSONObject, saved: Boolean)`; `DesignStore.decode(raw: String?, fallback: () -> JSONObject): Loaded` (puro); `DesignStore.load(ctx): Loaded`; `DesignStore.save(ctx, design)`; `DesignStore.reset(ctx)`; `DesignStore.initial(): JSONObject`.
  - `Backgrounds.sanitizeName(raw): String?`, `Backgrounds.fileFor(dir: File, name: String?): File?`, `Backgrounds.mimeOf(name: String): String`, `Backgrounds.dir(ctx): File`, `Backgrounds.list(ctx): JSONArray`, `Backgrounds.save(ctx, rawName, bytes): String` (lanza `IllegalArgumentException`), `Backgrounds.delete(ctx, name): Boolean`, `Backgrounds.MAX_BYTES`.
  - Eventos: `Events.Event.DesignChanged`, `Events.Event.DesignPreview(design: JSONObject, seconds: Int)`, `Events.Event.DesignPreviewCancel`.
  - Acciones: `get_design` → `{ok, design, saved, face_version}`; `set_design` (`{"design":{…}}`) → `{ok, design}` o `{ok:false, error:"diseño no válido", errors}`; `preview_design` (`{"design":{…},"seconds":30}`, 5–120 s) → `{ok, seconds}`; `reset_design` → `{ok, design}`.
  - HTTP: `GET/PUT /design`, `POST /design/reset`, `POST /design/preview` (`409` si suena una alarma), `DELETE /design/preview`, `GET/POST /backgrounds`, `GET /backgrounds/{nombre}` (bytes de la imagen), `DELETE /backgrounds/{nombre}`, `GET /face/data`; `GET /status` añade `face_version`.

- [ ] **Step 1: Tests que fallan.** `DesignStoreTest.kt`:

```kotlin
package com.jarvis.clock

import com.jarvis.clock.design.DesignSchema
import com.jarvis.clock.design.DesignStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignStoreTest {
    private val initial = { DesignSchema.initial(70, 3, 23, 7) }
    private fun themeName(l: DesignStore.Loaded) =
        l.design.getJSONObject("themes").getJSONObject("clasico").getString("name")

    @Test fun `sin nada guardado se usa el inicial`() {
        val l = DesignStore.decode(null, initial)
        assertFalse(l.saved)
        assertEquals("Clásico", themeName(l))
    }

    @Test fun `un diseno guardado corrupto o inválido cae al inicial`() {
        assertFalse(DesignStore.decode("esto no es json", initial).saved)
        assertFalse(DesignStore.decode("{\"version\":2}", initial).saved)
        assertFalse(DesignStore.decode("", initial).saved)
        val roto = initial().apply { getJSONArray("profiles").getJSONObject(0).put("theme", "fantasma") }
        val l = DesignStore.decode(roto.toString(), initial)
        assertFalse(l.saved)
        assertTrue(DesignSchema.validate(l.design) is DesignSchema.Result.Ok)
    }

    @Test fun `un diseno guardado valido se respeta`() {
        val mio = initial().apply { getJSONObject("themes").getJSONObject("clasico").put("name", "MiTema") }
        val l = DesignStore.decode(mio.toString(), initial)
        assertTrue(l.saved)
        assertEquals("MiTema", themeName(l))
    }
}
```

`BackgroundsTest.kt`:

```kotlin
package com.jarvis.clock

import com.jarvis.clock.design.Backgrounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackgroundsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `sanea nombres y no deja salir del directorio`() {
        assertEquals("playa.jpg", Backgrounds.sanitizeName("playa.JPG"))
        assertEquals("x.png", Backgrounds.sanitizeName("../../x.png"))
        assertEquals("x.png", Backgrounds.sanitizeName("..\\..\\x.png"))
        assertEquals("mi_foto_1.webp", Backgrounds.sanitizeName("mi foto (1).webp"))
    }

    @Test fun `rechaza extensiones no admitidas o nombres vacios`() {
        assertNull(Backgrounds.sanitizeName("virus.exe"))
        assertNull(Backgrounds.sanitizeName("sin_extension"))
        assertNull(Backgrounds.sanitizeName(".png"))
        assertNull(Backgrounds.sanitizeName("???.jpg"))
        assertNull(Backgrounds.sanitizeName("animacion.gif"))
    }

    @Test fun `fileFor solo resuelve archivos que existen`() {
        val dir = tmp.newFolder("backgrounds")
        File(dir, "playa.jpg").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(File(dir, "playa.jpg"), Backgrounds.fileFor(dir, "playa.jpg"))
        assertNull(Backgrounds.fileFor(dir, "borrada.jpg"))   // borrada: el tema cae a su color
        assertNull(Backgrounds.fileFor(dir, null))
        assertEquals(File(dir, "playa.jpg"), Backgrounds.fileFor(dir, "../playa.jpg")) // se sanea al nombre base
    }

    @Test fun `tipo mime por extension`() {
        assertEquals("image/jpeg", Backgrounds.mimeOf("a.jpg"))
        assertEquals("image/jpeg", Backgrounds.mimeOf("a.jpeg"))
        assertEquals("image/png", Backgrounds.mimeOf("a.png"))
        assertEquals("image/webp", Backgrounds.mimeOf("a.webp"))
    }
}
```

- [ ] **Step 2: Ver que fallan.**
Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.jarvis.clock.DesignStoreTest' --tests 'com.jarvis.clock.BackgroundsTest'`
Expected: FAIL de compilación.

- [ ] **Step 3: Crear `DesignStore.kt`.**

```kotlin
package com.jarvis.clock.design

import android.content.Context
import com.jarvis.clock.Prefs
import org.json.JSONObject

/** Guarda el diseño de pantallas. Si no hay ninguno, o el guardado no es válido, se usa el inicial. */
object DesignStore {
    private const val FILE = "design"
    private const val KEY = "json"

    data class Loaded(val design: JSONObject, val saved: Boolean)

    /** Puro: interpreta lo guardado; cualquier fallo devuelve el diseño de respaldo con saved=false. */
    fun decode(raw: String?, fallback: () -> JSONObject): Loaded {
        if (!raw.isNullOrBlank()) {
            val parsed = runCatching { JSONObject(raw) }.getOrNull()
            if (parsed != null && DesignSchema.validate(parsed) is DesignSchema.Result.Ok) return Loaded(parsed, true)
        }
        return Loaded(fallback(), false)
    }

    fun initial(): JSONObject =
        DesignSchema.initial(Prefs.dayBrightness, Prefs.nightBrightness, Prefs.nightStartHour, Prefs.nightEndHour)

    fun load(ctx: Context): Loaded = decode(sp(ctx).getString(KEY, null)) { initial() }

    fun save(ctx: Context, design: JSONObject) {
        sp(ctx).edit().putString(KEY, design.toString()).commit()
    }

    fun reset(ctx: Context) {
        sp(ctx).edit().remove(KEY).commit()
    }

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
```

- [ ] **Step 4: Crear `Backgrounds.kt`.**

```kotlin
package com.jarvis.clock.design

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Imágenes de fondo subidas desde el panel (en filesDir/backgrounds, así se ven sin red). */
object Backgrounds {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_SIDE = 2048
    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** Nombre seguro para disco (solo [A-Za-z0-9_-] + extensión admitida) o null. */
    fun sanitizeName(raw: String): String? {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val ext = base.substringAfterLast('.', "").lowercase()
        if (ext !in EXTENSIONS) return null
        val stem = base.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').take(60)
        return if (stem.isEmpty()) null else "$stem.$ext"
    }

    /** Archivo del fondo si existe en [dir]; null en cualquier otro caso (el tema cae a su color). */
    fun fileFor(dir: File, name: String?): File? {
        if (name == null) return null
        val safe = sanitizeName(name) ?: return null
        return File(dir, safe).takeIf { it.isFile }
    }

    fun mimeOf(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    fun dir(ctx: Context): File = File(ctx.filesDir, "backgrounds").apply { mkdirs() }

    fun list(ctx: Context): JSONArray {
        val out = JSONArray()
        dir(ctx).listFiles()?.filter { !it.name.startsWith(".") }?.sortedBy { it.name }?.forEach {
            out.put(JSONObject().put("name", it.name).put("size", it.length()))
        }
        return out
    }

    /** Guarda una imagen subida de forma atómica y comprueba que se puede leer. Devuelve su nombre. */
    fun save(ctx: Context, rawName: String, bytes: ByteArray): String {
        val name = sanitizeName(rawName) ?: throw IllegalArgumentException("nombre no válido: usa jpg, png o webp")
        require(bytes.isNotEmpty()) { "archivo vacío" }
        require(bytes.size <= MAX_BYTES) { "la imagen supera 2 MB" }
        val dir = dir(ctx)
        val tmp = File(dir, ".$name.tmp")
        try {
            tmp.writeBytes(bytes)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, o)
            require(o.outWidth > 0 && o.outHeight > 0) { "el reloj no puede leer esa imagen" }
            require(maxOf(o.outWidth, o.outHeight) <= MAX_SIDE) { "la imagen es demasiado grande (máx. $MAX_SIDE px)" }
            if (!tmp.renameTo(File(dir, name))) throw IllegalArgumentException("no se pudo guardar la imagen")
            return name
        } finally {
            tmp.delete()
        }
    }

    fun delete(ctx: Context, name: String): Boolean = fileFor(dir(ctx), name)?.delete() ?: false
}
```

- [ ] **Step 5: Eventos.** En `Events.kt`, dentro de `sealed class Event`, tras `SettingsChanged`:

```kotlin
        /** El diseño de pantallas guardado cambió (o se restauró). */
        object DesignChanged : Event()
        /** Mostrar un diseño sin guardarlo durante [seconds] segundos. */
        data class DesignPreview(val design: JSONObject, val seconds: Int) : Event()
        object DesignPreviewCancel : Event()
```

En `MainActivity.onEvent`, añade (se sustituirá en Task 5, pero el `when` debe seguir siendo exhaustivo):

```kotlin
            Event.DesignChanged, is Event.DesignPreview, Event.DesignPreviewCancel -> Unit
```

- [ ] **Step 6: Acciones.** En `Actions.kt` añade los imports `com.jarvis.clock.design.DesignSchema`, `com.jarvis.clock.design.DesignStore`, `com.jarvis.clock.design.FaceData` y, antes de la rama `else ->` de `execute`:

```kotlin
        "get_design" -> {
            val l = DesignStore.load(ctx)
            ok().put("design", l.design).put("saved", l.saved).put("face_version", FaceData.faceVersion(ctx))
        }
        "set_design" -> when (val v = DesignSchema.validate(a.optJSONObject("design") ?: JSONObject())) {
            is DesignSchema.Result.Invalid ->
                JSONObject().put("ok", false).put("error", "diseño no válido").put("errors", JSONObject(v.errors))
            is DesignSchema.Result.Ok -> {
                DesignStore.save(ctx, v.design)
                Events.post(Events.Event.DesignChanged)
                ok().put("design", v.design)
            }
        }
        "preview_design" -> when (val v = DesignSchema.validate(a.optJSONObject("design") ?: JSONObject())) {
            is DesignSchema.Result.Invalid ->
                JSONObject().put("ok", false).put("error", "diseño no válido").put("errors", JSONObject(v.errors))
            is DesignSchema.Result.Ok -> {
                val secs = a.optInt("seconds", 30).coerceIn(5, 120)
                Events.post(Events.Event.DesignPreview(v.design, secs))
                ok().put("seconds", secs)
            }
        }
        "reset_design" -> {
            DesignStore.reset(ctx)
            Events.post(Events.Event.DesignChanged)
            ok().put("design", DesignStore.load(ctx).design)
        }
```

- [ ] **Step 7: Rutas HTTP.** En `Api.kt` añade los imports `com.jarvis.clock.design.Backgrounds`, `com.jarvis.clock.design.FaceData`, `com.jarvis.clock.weather.Weather`. Tras el bloque `if (r.method == "DELETE" && r.path.startsWith("/sounds/")) { … }` y antes del `return when`:

```kotlin
        if (r.method in setOf("GET", "DELETE") && r.path.startsWith("/backgrounds/")) {
            val name = try {
                java.net.URLDecoder.decode(r.path.removePrefix("/backgrounds/"), "UTF-8")
            } catch (e: IllegalArgumentException) {
                return Response.error(400, "nombre no válido")
            }
            if (r.method == "DELETE") {
                return if (Backgrounds.delete(ctx, name)) Response.ok() else Response.error(404, "no existe el fondo $name")
            }
            val f = Backgrounds.fileFor(Backgrounds.dir(ctx), name) ?: return Response.error(404, "no existe el fondo $name")
            return Response(200, f.readBytes(), Backgrounds.mimeOf(f.name))
        }
```

Y dentro del `when (r.method to r.path)`, antes de `else`:

```kotlin
            "GET" to "/design" -> result(Actions.execute(ctx, JSONObject().put("type", "get_design")))
            "PUT" to "/design" -> result(Actions.execute(ctx, JSONObject().put("type", "set_design").put("design", r.json())))
            "POST" to "/design/reset" -> result(Actions.execute(ctx, JSONObject().put("type", "reset_design")))
            "POST" to "/design/preview" -> {
                if (AlarmRingService.ringing) Response.error(409, "hay una alarma sonando")
                else {
                    val j = r.json()
                    result(Actions.execute(ctx, JSONObject().put("type", "preview_design")
                        .put("design", j.optJSONObject("design")).put("seconds", j.optInt("seconds", 30))))
                }
            }
            "DELETE" to "/design/preview" -> { Events.post(Events.Event.DesignPreviewCancel); Response.ok() }
            "GET" to "/backgrounds" -> Response.ok(JSONObject().put("backgrounds", Backgrounds.list(ctx)))
            "POST" to "/backgrounds" -> try {
                Response.ok(JSONObject().put("name", Backgrounds.save(ctx, r.query["name"].orEmpty(), r.body)))
            } catch (e: IllegalArgumentException) {
                Response.error(400, e.message ?: "imagen no válida")
            }
            "GET" to "/face/data" -> { Weather.get(); Response.ok(FaceData.build(ctx)) }
```

En `status()` añade `put("face_version", FaceData.faceVersion(ctx))` junto a `put("version", …)`.

- [ ] **Step 8: Compilar y probar todo.**
Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app-android/src/main/java/com/jarvis/clock app-android/src/test/java/com/jarvis/clock/DesignStoreTest.kt app-android/src/test/java/com/jarvis/clock/BackgroundsTest.kt
git commit -m "Pantallas: DesignStore, fondos subidos y API /design, /backgrounds y /face/data"
```

---

### Task 5: `FaceHost` y la carátula del reloj con el motor

**Files:**
- Create: `app-android/src/main/java/com/jarvis/clock/ui/FaceHost.kt`
- Modify: `app-android/src/main/java/com/jarvis/clock/MainActivity.kt`

**Interfaces:**
- Consumes: `DesignSchema.activeProfile`, `DesignStore.load`, `FaceData.build`, `Backgrounds.fileFor/dir/mimeOf`, eventos de Task 4.
- Produces: `FaceHost.create(ctx: Context, listener: FaceHost.Listener): FaceHost?` (null si no hay WebView), `FaceHost.view: WebView`, `render(design: JSONObject, opts: JSONObject)`, `setData(data: JSONObject)`, `release()`; `FaceHost.Listener { onTap(); onDoubleTap(); onLongPress(); onPermission(); onFailure(reason: String) }`.

Esta tarea no tiene test JVM (necesita un `WebView` real): se comprueba compilando aquí y en el reloj en Task 11.

- [ ] **Step 1: Crear `FaceHost.kt`.**

```kotlin
package com.jarvis.clock.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jarvis.clock.design.Backgrounds
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.FileInputStream

/**
 * Aloja el motor de pantallas (assets/face) en un WebView. Solo se carga esa página, servida por
 * la propia app; cualquier otra petición responde 404 y la navegación externa está bloqueada.
 * Si el WebView no existe, se cae o no carga, avisa con [Listener.onFailure] para volver a la
 * carátula nativa.
 */
class FaceHost private constructor(private val ctx: Context, private val listener: Listener) {

    interface Listener {
        fun onTap()
        fun onDoubleTap()
        fun onLongPress()
        fun onPermission()
        fun onFailure(reason: String)
    }

    val view: WebView = WebView(ctx)
    private val main = Handler(Looper.getMainLooper())
    private val pending = ArrayList<String>()
    private var ready = false
    private var released = false

    init {
        configure()
        view.loadUrl(PAGE)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        view.setBackgroundColor(Color.BLACK)
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        view.overScrollMode = View.OVER_SCROLL_NEVER
        view.isHapticFeedbackEnabled = false
        view.setOnLongClickListener { true }
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        view.addJavascriptInterface(Bridge(), "Native")
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean = true
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse =
                intercept(request.url)
            override fun onPageFinished(v: WebView, url: String) {
                ready = true
                pending.forEach { v.evaluateJavascript(it, null) }
                pending.clear()
            }
            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) fail("carga: ${error.description}")
            }
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                fail("el proceso de dibujo murió")
                return true
            }
        }
    }

    private fun fail(reason: String) {
        if (!released) main.post { if (!released) listener.onFailure(reason) }
    }

    private fun intercept(uri: Uri): WebResourceResponse {
        if (uri.host == HOST) {
            val path = uri.path.orEmpty()
            if (path.startsWith("/assets/face/")) {
                val name = path.removePrefix("/assets/face/")
                if (name in FILES) {
                    val mime = when (name.substringAfterLast('.')) {
                        "html" -> "text/html"
                        "css" -> "text/css"
                        else -> "application/javascript"
                    }
                    return WebResourceResponse(mime, "utf-8", ctx.assets.open("face/$name"))
                }
            } else if (path.startsWith("/bg/")) {
                val f = Backgrounds.fileFor(Backgrounds.dir(ctx), path.removePrefix("/bg/"))
                if (f != null) return WebResourceResponse(Backgrounds.mimeOf(f.name), null, FileInputStream(f))
            }
        }
        return WebResourceResponse("text/plain", "utf-8", 404, "No encontrado", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    /** Puente hacia Android: solo existe en la página del motor. */
    inner class Bridge {
        @JavascriptInterface fun tap() { main.post { listener.onTap() } }
        @JavascriptInterface fun doubleTap() { main.post { listener.onDoubleTap() } }
        @JavascriptInterface fun longPress() { main.post { listener.onLongPress() } }
        @JavascriptInterface fun permission() { main.post { listener.onPermission() } }
        @JavascriptInterface fun error(msg: String) { Log.w(TAG, "motor: $msg") }
    }

    fun render(design: JSONObject, opts: JSONObject) =
        eval("Face.render(JSON.parse(${JSONObject.quote(design.toString())}), JSON.parse(${JSONObject.quote(opts.toString())}))")

    fun setData(data: JSONObject) = eval("Face.setData(JSON.parse(${JSONObject.quote(data.toString())}))")

    private fun eval(js: String) {
        if (released) return
        if (!ready) { pending.add(js); return }
        view.evaluateJavascript(js, null)
    }

    fun release() {
        if (released) return
        released = true
        view.stopLoading()
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    companion object {
        private const val TAG = "FaceHost"
        private const val HOST = "appassets.androidplatform.net"
        private const val PAGE = "https://$HOST/assets/face/index.html"
        private val FILES = setOf("index.html", "face.css", "face-core.js", "face.js")

        /** null si el dispositivo no tiene WebView o no se puede crear. */
        fun create(ctx: Context, listener: Listener): FaceHost? = try {
            FaceHost(ctx, listener)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo crear el WebView de la carátula", e)
            null
        }
    }
}
```

- [ ] **Step 2: `MainActivity` — imports.** Tras `import com.jarvis.clock.assistant.Assistant` añade:

```kotlin
import com.jarvis.clock.design.DesignSchema
import com.jarvis.clock.design.DesignStore
import com.jarvis.clock.design.FaceData
import com.jarvis.clock.ui.FaceHost
import org.json.JSONObject
```

- [ ] **Step 3: Campos y oyente.** Justo después de `private var brightnessOverride: Int? = null` añade:

```kotlin
    private var faceHost: FaceHost? = null
    private var cachedDesign: JSONObject? = null
    private var previewDesign: JSONObject? = null
    private var shownKey: String? = null

    private val faceListener = object : FaceHost.Listener {
        override fun onTap() { wakeScreen() }
        override fun onDoubleTap() { assistant.listen() }
        override fun onLongPress() { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        override fun onPermission() {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
        override fun onFailure(reason: String) {
            Log.w("Face", "El WebView de la carátula falló ($reason): vuelvo a la carátula nativa")
            useNativeFace()
        }
    }

    private val endPreview = Runnable {
        previewDesign = null
        shownKey = null
        applyNightMode()
    }
```

- [ ] **Step 4: Crear el motor en `buildUi()`.** Justo después de la línea `root.addView(face, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(56) })` añade:

```kotlin
        // El motor de pantallas va detrás de todo; si no hay WebView, se queda la carátula nativa.
        faceHost = FaceHost.create(this, faceListener)?.also { host ->
            root.addView(host.view, 0, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(56) })
            face.visibility = View.GONE
        }
```

- [ ] **Step 5: Ciclo de vida.** Sustituye `onResume`, `onPause` y `onDestroy`:

```kotlin
    override fun onResume() {
        super.onResume()
        Ui.immersive(window)
        faceHost?.view?.onResume()
        tickMinute()
        refreshWeather()
        refreshNextAlarm()
        pushFaceData()
        if (AlarmRingService.ringing) startActivity(AlarmActivity.intent(this, AlarmRingService.currentLabel ?: "Alarma"))
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        previewDesign = null // la prueba termina si sales de la carátula
        shownKey = null
        faceHost?.view?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        Events.unregister(listener)
        destroyWebView()
        faceHost?.release()
        faceHost = null
        super.onDestroy()
    }
```

- [ ] **Step 6: Modo noche por perfiles.** Renombra la función actual `applyNightMode()` a `applyNativeNightMode()` (cuerpo sin cambios) y añade, justo encima, las nuevas:

```kotlin
    private fun minuteOfDay(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private fun activeDesign(): JSONObject =
        previewDesign ?: cachedDesign ?: DesignStore.load(this).design.also { cachedDesign = it }

    /** Brillo al tocar la pantalla en un perfil de noche: el del primer perfil que no sea de noche. */
    private fun wakeBrightness(design: JSONObject): Int {
        val ps = design.optJSONArray("profiles") ?: return 70
        for (i in 0 until ps.length()) {
            val p = ps.optJSONObject(i) ?: continue
            if (!p.optBoolean("night", false)) return p.optInt("brightness", 70)
        }
        return 70
    }

    /** Perfil activo según la hora: brillo, modo noche, barra inferior y qué dibuja el motor. */
    private fun applyNightMode() {
        val host = faceHost
        if (host == null) { applyNativeNightMode(); return }
        val design = activeDesign()
        val profile = DesignSchema.activeProfile(design, minuteOfDay()) ?: return
        val profileNight = profile.optBoolean("night", false)
        val awake = System.currentTimeMillis() < wakeUntil
        night = profileNight && !awake
        val showBar = profile.optBoolean("show_bar", true)
        bar.visibility = if (showBar) View.VISIBLE else View.GONE
        bar.alpha = if (night) 0.15f else 1f
        (host.view.layoutParams as FrameLayout.LayoutParams).bottomMargin = if (showBar) Ui.dp(this, 56) else 0
        host.view.requestLayout()
        val pct = brightnessOverride ?: if (profileNight && awake) wakeBrightness(design) else profile.optInt("brightness", 70)
        window.attributes = window.attributes.apply { screenBrightness = pct.coerceIn(1, 100) / 100f }
        val key = profile.optString("id") + "|" + design.toString().hashCode()
        if (key != shownKey) {
            shownKey = key
            host.render(design, JSONObject().put("profile", profile.optString("id")))
            pushFaceData()
        }
    }

    private fun pushFaceData() {
        faceHost?.setData(FaceData.build(this))
    }

    private fun useNativeFace() {
        faceHost?.release()
        faceHost = null
        face.visibility = View.VISIBLE
        bar.visibility = View.VISIBLE
        bar.alpha = 1f
        tickMinute()
    }
```

- [ ] **Step 7: Datos al motor.** En `tickMinute()` añade `pushFaceData()` tras `refreshNextAlarm()`. En `refreshWeather()` cambia el callback por `runOnUiThread { weather.text = w.shortText(); pushFaceData() }`.

- [ ] **Step 8: Eventos.** En `onEvent` sustituye la línea de relleno de Task 4 y estas dos ramas:

```kotlin
            Event.AlarmsChanged -> { refreshNextAlarm(); pushFaceData() }
            is Event.AlarmRinging -> { endPreview.run(); runCatching { startActivity(AlarmActivity.intent(this, e.label)) } }
            Event.AlarmStopped -> { refreshNextAlarm(); pushFaceData() }
            Event.DesignChanged -> {
                handler.removeCallbacks(endPreview)
                previewDesign = null
                cachedDesign = null
                shownKey = null
                applyNightMode()
            }
            is Event.DesignPreview -> {
                previewDesign = e.design
                shownKey = null
                applyNightMode()
                handler.removeCallbacks(endPreview)
                handler.postDelayed(endPreview, e.seconds * 1000L)
            }
            Event.DesignPreviewCancel -> { handler.removeCallbacks(endPreview); endPreview.run() }
```

- [ ] **Step 9: Compilar y probar.**
Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL. Si falla `Unresolved reference: Log` o `Settings`, comprueba que `android.util.Log` y `android.provider.Settings` ya estaban importados (lo están).

- [ ] **Step 10: Commit**

```bash
git add app-android/src/main/java/com/jarvis/clock/ui/FaceHost.kt app-android/src/main/java/com/jarvis/clock/MainActivity.kt
git commit -m "Pantallas: la carátula del reloj usa el motor (perfiles, brillo, vista previa y respaldo nativo)"
```

---

### Task 6: Pantalla de alarma con el motor

**Files:**
- Modify (reescribir): `app-android/src/main/java/com/jarvis/clock/alarm/AlarmActivity.kt`
- Modify: `app-android/src/main/java/com/jarvis/clock/design/Colors.kt`
- Test: `app-android/src/test/java/com/jarvis/clock/ColorsTest.kt`

**Interfaces:**
- Consumes: `FaceHost`, `DesignStore.load`, `FaceData.build(ctx, ringingLabel)`, `parseHex`, `darken`.
- Produces: `readableOn(bg: Int): Int` (negro o blanco según la luminosidad del fondo).

- [ ] **Step 1: Test que falla.** `ColorsTest.kt`:

```kotlin
package com.jarvis.clock

import com.jarvis.clock.design.readableOn
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorsTest {
    @Test fun `texto negro sobre fondos claros y blanco sobre oscuros`() {
        assertEquals(0xFF000000.toInt(), readableOn(0xFFFFD180.toInt()))   // ámbar claro
        assertEquals(0xFFFFFFFF.toInt(), readableOn(0xFF0D47A1.toInt()))   // azul oscuro
        assertEquals(0xFFFFFFFF.toInt(), readableOn(0xFF000000.toInt()))
        assertEquals(0xFF000000.toInt(), readableOn(0xFFFFFFFF.toInt()))
    }
}
```

Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.jarvis.clock.ColorsTest'` → FAIL (`readableOn` no existe).

- [ ] **Step 2: Añadir a `Colors.kt`.**

```kotlin
/** Negro o blanco, el que se lea mejor sobre [bg]. */
fun readableOn(bg: Int): Int {
    val r = (bg shr 16) and 0xFF
    val g = (bg shr 8) and 0xFF
    val b = bg and 0xFF
    return if ((r * 299 + g * 587 + b * 114) / 1000 > 140) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}
```

Run el test otra vez → PASS.

- [ ] **Step 3: Reescribir `AlarmActivity.kt`** (mantiene todo lo de antes —amanecer de brillo, pantalla sobre el bloqueo, botones grandes— y pone el motor en la zona de arriba; si falla, la zona nativa de siempre):

```kotlin
package com.jarvis.clock.alarm

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.jarvis.clock.Events
import com.jarvis.clock.Prefs
import com.jarvis.clock.design.DesignStore
import com.jarvis.clock.design.FaceData
import com.jarvis.clock.design.darken
import com.jarvis.clock.design.parseHex
import com.jarvis.clock.design.readableOn
import com.jarvis.clock.ui.FaceHost
import com.jarvis.clock.ui.Ui
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de alarma. La zona de arriba la dibuja el motor de pantallas (diseño `alarm_screen`);
 * los botones son nativos y enormes para acertar medio dormido. El brillo sube poco a poco
 * (efecto amanecer) a la vez que el volumen. Si el WebView falla, vuelve la zona nativa de siempre.
 */
class AlarmActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var time: TextView? = null
    private var faceHost: FaceHost? = null
    private lateinit var label: String
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val startedAt = System.currentTimeMillis()

    private val listener = Events.Listener { e ->
        if (e is Events.Event.AlarmStopped) finish()
    }

    private val hostListener = object : FaceHost.Listener {
        override fun onTap() {}
        override fun onDoubleTap() {}
        override fun onLongPress() {}
        override fun onPermission() {}
        override fun onFailure(reason: String) { runOnUiThread { rebuildNative() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockscreen()
        Ui.immersive(window)
        label = intent.getStringExtra(EXTRA_LABEL) ?: "Alarma"
        setContentView(buildRoot(web = true))
        Events.register(listener)
        tick()
    }

    private fun rebuildNative() {
        faceHost?.release()
        faceHost = null
        setContentView(buildRoot(web = false))
    }

    private fun themeColor(o: JSONObject?, key: String, default: Int): Int =
        runCatching { parseHex(o!!.getString(key)) }.getOrDefault(default)

    private fun buildRoot(web: Boolean): LinearLayout {
        val dp = { v: Int -> Ui.dp(this, v) }
        val design = DesignStore.load(this).design
        val alarm = design.optJSONObject("alarm_screen")
        val theme = design.optJSONObject("themes")?.optJSONObject(alarm?.optString("theme").orEmpty())
        val accent = themeColor(theme, "accent", Ui.ACCENT)
        val bg = theme?.optJSONObject("bg")
        val rootBg = if (bg?.optString("type") == "color") themeColor(bg, "color", Color.BLACK) else Color.BLACK

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(rootBg)
            setPadding(0, 0, 0, dp(16))
        }

        val top: View = (if (web) FaceHost.create(this, hostListener) else null)?.let { host ->
            faceHost = host
            host.render(design, JSONObject().put("alarm", true))
            host.setData(FaceData.build(this, ringingLabel = label))
            host.view
        } ?: nativeTop()
        root.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))

        val snoozeText = alarm?.optString("snooze_label").orEmpty().ifEmpty { "Posponer {min} min" }
            .replace("{min}", Prefs.snoozeMinutes.toString())
        val dismissText = alarm?.optString("dismiss_label").orEmpty().ifEmpty { "Descartar" }
        val snoozeBg = darken(accent, 0.3f)
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(12), dp(24), 0)
        }
        buttons.addView(
            bigButton(snoozeText, snoozeBg, readableOn(snoozeBg)) { AlarmRingService.snooze(this); finish() },
            LinearLayout.LayoutParams(0, dp(96), 1f).apply { marginEnd = dp(12) },
        )
        buttons.addView(
            bigButton(dismissText, accent, readableOn(accent)) { AlarmRingService.dismiss(this); finish() },
            LinearLayout.LayoutParams(0, dp(96), 1f),
        )
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    /** La zona de arriba de siempre: hora grande y nombre de la alarma. */
    private fun nativeTop(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val t = TextView(this@AlarmActivity).apply {
            setTextColor(Color.WHITE)
            textSize = 96f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        time = t
        addView(t)
        addView(TextView(this@AlarmActivity).apply {
            text = label
            setTextColor(Ui.ACCENT)
            textSize = 30f
            gravity = Gravity.CENTER
        })
    }

    private fun bigButton(text: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 24f
        setTextColor(fg)
        setBackgroundColor(bg)
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun tick() {
        time?.text = fmt.format(Date())
        // Amanecer: el brillo de la pantalla sigue a la rampa de volumen.
        val ramp = (Prefs.alarmRampSeconds * 1000L).coerceAtLeast(1)
        val t = ((System.currentTimeMillis() - startedAt).toFloat() / ramp).coerceIn(0f, 1f)
        window.attributes = window.attributes.apply { screenBrightness = 0.05f + 0.95f * t }
        handler.postDelayed(::tick, 1000)
    }

    private fun showOverLockscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    override fun onResume() {
        super.onResume()
        faceHost?.view?.onResume()
        if (!AlarmRingService.ringing) finish()
    }

    override fun onPause() {
        faceHost?.view?.onPause()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Atrás no apaga la alarma: hay que elegir.
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Events.unregister(listener)
        faceHost?.release()
        faceHost = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_LABEL = "label"
        fun intent(ctx: Context, label: String): Intent =
            Intent(ctx, AlarmActivity::class.java)
                .putExtra(EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
```

- [ ] **Step 4: Compilar y probar.**
Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app-android/src/main/java/com/jarvis/clock/alarm/AlarmActivity.kt app-android/src/main/java/com/jarvis/clock/design/Colors.kt app-android/src/test/java/com/jarvis/clock/ColorsTest.kt
git commit -m "Pantallas: la pantalla de alarma usa el motor arriba y botones nativos con los colores del tema"
```

---
### Task 7: Skill de Hermes y reloj de mentira

**Files:**
- Modify: `hermes-skill/mock_clock_server.py`
- Modify: `hermes-skill/clock_tools.py`
- Modify: `hermes-skill/mi-smart-clock/scripts/clock.py`
- Modify: `hermes-skill/mi-smart-clock/SKILL.md`
- Test: `hermes-skill/tests/test_clock_tools.py`

**Interfaces:**
- Consumes: contrato HTTP de Task 4.
- Produces:
  - `clock_get_design()`, `clock_set_design(design: dict)`, `clock_preview_design(design: dict, seconds: int = 30)`, `clock_cancel_preview()`, `clock_reset_design()`, `clock_list_backgrounds()`.
  - Mock: `SAMPLE_DESIGN` (diseño válido con tema `clasico`, pantalla `principal` con elementos `hora` (0,0,8,3) y `fecha` (0,3,8,1), perfil `dia`, `alarm_screen`), `DESIGN`, `DESIGN_SAVED`, `BACKGROUNDS`, `BG_DATA`, `PREVIEW`, `FACE_VERSION = "mock"`, `FACE_DATA`; rutas `GET/PUT /design`, `POST /design/reset`, `POST/DELETE /design/preview`, `GET/POST /backgrounds`, `GET/DELETE /backgrounds/{nombre}`, `GET /face/data`; `/status` incluye `face_version`; `reset()` lo restablece todo. El panel de Task 8 reutiliza este mock.

- [ ] **Step 1: Tests que fallan.** Añade a `hermes-skill/tests/test_clock_tools.py`:

```python
def test_get_design_is_sample_and_not_saved(clock):
    r = clock.clock_get_design()
    assert r["ok"] and r["saved"] is False
    assert r["design"]["version"] == 1 and "clasico" in r["design"]["themes"]


def test_set_design_saves(clock):
    d = clock.clock_get_design()["design"]
    d["themes"]["clasico"]["name"] = "Mío"
    assert clock.clock_set_design(d)["ok"] is True
    g = clock.clock_get_design()
    assert g["saved"] is True and g["design"]["themes"]["clasico"]["name"] == "Mío"


def test_set_design_overlap_changes_nothing(clock):
    d = clock.clock_get_design()["design"]
    d["screens"]["principal"]["elements"][1].update(x=0, y=0, w=8, h=1)
    r = clock.clock_set_design(d)
    assert r["ok"] is False and "screens.principal.elements.fecha" in r["errors"]
    assert clock.clock_get_design()["saved"] is False


def test_preview_and_cancel(clock):
    import mock_clock_server as mock
    d = clock.clock_get_design()["design"]
    assert clock.clock_preview_design(d, 20)["ok"] is True
    assert mock.PREVIEW and mock.PREVIEW[0]["seconds"] == 20
    assert clock.clock_cancel_preview()["ok"] is True
    assert mock.PREVIEW == []


def test_reset_design(clock):
    d = clock.clock_get_design()["design"]
    d["themes"]["clasico"]["name"] = "Otro"
    clock.clock_set_design(d)
    r = clock.clock_reset_design()
    assert r["ok"] and r["design"]["themes"]["clasico"]["name"] != "Otro"
    assert clock.clock_get_design()["saved"] is False


def test_list_backgrounds_empty(clock):
    r = clock.clock_list_backgrounds()
    assert r["ok"] and r["backgrounds"] == []
```

- [ ] **Step 2: Ver que fallan.**
Run: `cd hermes-skill && python3 -m pytest -q -k design`
Expected: FAIL (`clock_get_design` no existe).

- [ ] **Step 3: Ampliar `mock_clock_server.py`.** Añade a nivel de módulo (tras las constantes de sonidos):

```python
FACE_VERSION = "mock"
SAMPLE_DESIGN = {
    "version": 1,
    "themes": {
        "clasico": {"name": "Clásico", "bg": {"type": "color", "color": "#000000"},
                    "fg": "#ffffff", "muted": "#90a4ae", "accent": "#4fc3f7", "font": "thin"},
        "noche_roja": {"name": "Noche roja", "bg": {"type": "color", "color": "#000000"},
                       "fg": "#8b0000", "muted": "#8b0000", "accent": "#8b0000", "font": "thin"},
    },
    "screens": {
        "principal": {"name": "Principal", "elements": [
            {"id": "hora", "type": "clock", "x": 0, "y": 0, "w": 8, "h": 3, "style": "digital"},
            {"id": "fecha", "type": "date", "x": 0, "y": 3, "w": 8, "h": 1, "format": "long", "color": "muted"},
        ]},
    },
    "profiles": [
        {"id": "dia", "name": "Día", "from": "07:00", "theme": "clasico", "screens": ["principal"],
         "rotate_s": 0, "brightness": 70, "night": False, "show_bar": True},
    ],
    "alarm_screen": {"theme": "clasico", "elements": [
        {"id": "hora", "type": "clock", "x": 0, "y": 0, "w": 8, "h": 3, "style": "digital"},
    ], "snooze_label": "Posponer {min} min", "dismiss_label": "Descartar"},
}
DESIGN: dict = copy.deepcopy(SAMPLE_DESIGN)
DESIGN_SAVED = [False]
BACKGROUNDS: list[dict] = []
BG_DATA: dict[str, bytes] = {}
PREVIEW: list[dict] = []
FACE_DATA = {
    "weather": {"temp": 19, "icon": "☀", "description": "Despejado", "max": 22, "min": 12, "rain": 10},
    "forecast": {"hours": [{"h": 15, "temp": 20, "icon": "☀"}], "days": [{"d": "mié", "min": 12, "max": 23, "icon": "☁"}]},
    "next_alarm": {"label": "Despertar", "when": "mañana", "time": "07:30"},
    "exact_alarm_missing": False,
}


def _validate_design(d) -> dict:
    """Validación mínima y fiel en la forma de los errores (el reloj real valida mucho más)."""
    try:
        if not isinstance(d, dict) or d.get("version") != 1:
            return {"version": "debe ser 1"}
        errors = {}
        for k in ("themes", "screens", "profiles", "alarm_screen"):
            if k not in d:
                errors[k] = "falta"
        for sid, s in (d.get("screens") or {}).items():
            els = s.get("elements", [])
            for i, a in enumerate(els):
                if a["x"] + a["w"] > 8:
                    errors[f"screens.{sid}.elements.{a['id']}.w"] = "se sale de la rejilla (x + w ≤ 8)"
                elif a["y"] + a["h"] > 6:
                    errors[f"screens.{sid}.elements.{a['id']}.h"] = "se sale de la rejilla (y + h ≤ 6)"
                for b in els[i + 1:]:
                    if a["x"] < b["x"] + b["w"] and b["x"] < a["x"] + a["w"] and a["y"] < b["y"] + b["h"] and b["y"] < a["y"] + a["h"]:
                        errors[f"screens.{sid}.elements.{b['id']}"] = f"se solapa con {a['id']}"
        for p in d.get("profiles", []):
            if p.get("theme") not in d.get("themes", {}):
                errors[f"profiles.{p.get('id')}.theme"] = "el tema no existe"
        return errors
    except (KeyError, TypeError, AttributeError):
        return {"design": "estructura no válida"}
```

En `Handler` añade el método `_send_bytes` junto a `_send`:

```python
    def _send_bytes(self, code: int, data: bytes, ctype: str):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
```

En `do_GET` (tras la comprobación de token) añade, y en el dict de `/status` incluye `"face_version": FACE_VERSION`:

```python
        if self._path == "/design":
            return self._send(200, {"ok": True, "design": DESIGN, "saved": DESIGN_SAVED[0], "face_version": FACE_VERSION})
        if self._path == "/backgrounds":
            return self._send(200, {"ok": True, "backgrounds": BACKGROUNDS})
        if self._path.startswith("/backgrounds/"):
            name = unquote(self._path.removeprefix("/backgrounds/"))
            if name not in BG_DATA:
                return self._send(404, {"ok": False, "error": f"no existe el fondo {name}"})
            ctype = "image/png" if name.endswith(".png") else "image/webp" if name.endswith(".webp") else "image/jpeg"
            return self._send_bytes(200, BG_DATA[name], ctype)
        if self._path == "/face/data":
            return self._send(200, {"ok": True, **FACE_DATA})
```

En `do_PUT`:

```python
        if self._path == "/design":
            body = self._read()
            errors = _validate_design(body)
            if errors:
                return self._send(400, {"ok": False, "error": "diseño no válido", "errors": errors})
            DESIGN.clear(); DESIGN.update(copy.deepcopy(body)); DESIGN_SAVED[0] = True
            return self._send(200, {"ok": True, "design": DESIGN})
```

En `do_DELETE`:

```python
        if self._path == "/design/preview":
            PREVIEW.clear()
            return self._send(200, {"ok": True})
        if self._path.startswith("/backgrounds/"):
            name = unquote(self._path.removeprefix("/backgrounds/"))
            if name not in BG_DATA:
                return self._send(404, {"ok": False, "error": f"no existe el fondo {name}"})
            del BG_DATA[name]
            BACKGROUNDS[:] = [b for b in BACKGROUNDS if b["name"] != name]
            return self._send(200, {"ok": True})
```

En `do_POST`, **antes** de la línea `body = self._read()` (los fondos llegan como bytes crudos):

```python
        if self._path == "/backgrounds":
            data = self._raw()
            name = self._query.get("name", "")
            if not data or not re.match(r"^[A-Za-z0-9_-]+\.(jpg|jpeg|png|webp)$", name, re.I):
                return self._send(400, {"ok": False, "error": "imagen no válida"})
            BG_DATA[name] = data
            BACKGROUNDS[:] = [b for b in BACKGROUNDS if b["name"] != name] + [{"name": name, "size": len(data)}]
            return self._send(200, {"ok": True, "name": name})
```

y **después** de `body = self._read()`:

```python
        if self._path == "/design/reset":
            DESIGN.clear(); DESIGN.update(copy.deepcopy(SAMPLE_DESIGN)); DESIGN_SAVED[0] = False
            return self._send(200, {"ok": True, "design": DESIGN})
        if self._path == "/design/preview":
            errors = _validate_design(body.get("design"))
            if errors:
                return self._send(400, {"ok": False, "error": "diseño no válido", "errors": errors})
            PREVIEW[:] = [{"design": body["design"], "seconds": body.get("seconds", 30)}]
            return self._send(200, {"ok": True, "seconds": body.get("seconds", 30)})
```

En `reset()` añade al final:

```python
    DESIGN.clear(); DESIGN.update(copy.deepcopy(SAMPLE_DESIGN)); DESIGN_SAVED[0] = False
    BACKGROUNDS.clear(); BG_DATA.clear(); PREVIEW.clear()
```

- [ ] **Step 4: Tools.** En `clock_tools.py`, antes de `TOOLS = [`:

```python
def clock_get_design() -> dict:
    """Diseño de pantallas del reloj: temas, pantallas (rejilla 8×6), perfiles por hora y pantalla de alarma."""
    return _call("GET", "/design")


def clock_set_design(design: dict) -> dict:
    """Guarda el diseño entero. Todo o nada: si algo no vale devuelve "errors" por ruta y no cambia nada."""
    return _call("PUT", "/design", design)


def clock_preview_design(design: dict, seconds: int = 30) -> dict:
    """Muestra un diseño en el reloj `seconds` segundos (5-120) sin guardarlo."""
    return _call("POST", "/design/preview", {"design": design, "seconds": seconds})


def clock_cancel_preview() -> dict:
    """Cancela la prueba de diseño en curso."""
    return _call("DELETE", "/design/preview")


def clock_reset_design() -> dict:
    """Restaura el diseño inicial (borra el diseño guardado)."""
    return _call("POST", "/design/reset", {})


def clock_list_backgrounds() -> dict:
    """Imágenes de fondo subidas al reloj."""
    return _call("GET", "/backgrounds")
```

y añade `clock_get_design, clock_set_design, clock_preview_design, clock_cancel_preview, clock_reset_design, clock_list_backgrounds` a `TOOLS`.

- [ ] **Step 5: CLI.** En `clock.py`, junto a los demás subparsers:

```python
    sub.add_parser("design", help="ver el diseño de pantallas del reloj")
    ds = sub.add_parser("design-set", help="guardar un diseño desde un archivo JSON")
    ds.add_argument("file")
    dp = sub.add_parser("design-preview", help="probar un diseño (archivo JSON) en el reloj sin guardarlo")
    dp.add_argument("file")
    dp.add_argument("--seconds", type=int, default=30)
    sub.add_parser("design-reset", help="restaurar el diseño inicial")
    sub.add_parser("backgrounds", help="fondos de pantalla subidos")
```

y en el despacho:

```python
    if x.cmd == "design":
        return call("GET", "/design")
    if x.cmd == "design-set":
        return call("PUT", "/design", json.load(open(x.file, encoding="utf-8")))
    if x.cmd == "design-preview":
        return call("POST", "/design/preview", {"design": json.load(open(x.file, encoding="utf-8")), "seconds": x.seconds})
    if x.cmd == "design-reset":
        return call("POST", "/design/reset", {})
    if x.cmd == "backgrounds":
        return call("GET", "/backgrounds")
```

Si `call()` no admite `DELETE`/`PUT`, comprueba que construye `urllib.request.Request(..., method=method)` (ya lo hace para `PUT`).

- [ ] **Step 6: `SKILL.md`.** Añade a la lista de comandos de `## Procedure`:

```markdown
- Diseño de las pantallas del reloj (temas, rejilla 8×6, perfiles por hora, pantalla de alarma):
  `clock.py design` · `clock.py design-set diseno.json` · `clock.py design-preview diseno.json --seconds 30`
  · `clock.py design-reset`. Guardar es todo o nada: si algo no vale, devuelve `errors` por ruta.
- Fondos de pantalla subidos: `clock.py backgrounds`.
```

- [ ] **Step 7: Ver que pasan.**
Run: `cd hermes-skill && python3 -m pytest -q && python3 -m py_compile mi-smart-clock/scripts/clock.py`
Expected: todos PASS.

- [ ] **Step 8: Commit**

```bash
git add hermes-skill
git commit -m "Skill: diseño de pantallas y fondos; reloj de mentira ampliado"
```

---

### Task 8: Panel — rutas del diseño, fondos y versión del motor

**Files:**
- Modify: `homelab/friday-panel/app.py`
- Test: `homelab/friday-panel/tests/test_design.py` (nuevo)
- Test: `homelab/friday-panel/tests/test_face_sync.py` (nuevo)

**Interfaces:**
- Consumes: el mock de Task 7; el motor copiado en `static/face/` (Task 2).
- Produces: `face_version(face_dir: Path) -> str`, `FACE_FILES`; rutas `GET/PUT /api/design`, `POST /api/design/reset`, `POST/DELETE /api/design/preview`, `GET /api/face-data`, `GET /api/face-version` (`{"ok":true,"face_version":…}`), `GET/POST /api/backgrounds` (cuerpo crudo ≤ 2 MB, `?name=`), `DELETE /api/backgrounds/{name}`, `GET /api/bg/{name}` (bytes de la imagen). Todas bajo el login/anti-CSRF existentes.

- [ ] **Step 1: Tests que fallan.** `tests/test_design.py`:

```python
import mock_clock_server as mock
from app import FACE_FILES, face_version


def test_get_design(client):
    r = client.get("/api/design")
    assert r.status_code == 200
    assert r.json()["design"]["version"] == 1 and r.json()["saved"] is False


def test_put_design_ok_then_saved(client):
    d = client.get("/api/design").json()["design"]
    d["themes"]["clasico"]["name"] = "Mío"
    assert client.put("/api/design", json=d).status_code == 200
    g = client.get("/api/design").json()
    assert g["saved"] is True and g["design"]["themes"]["clasico"]["name"] == "Mío"


def test_put_design_invalid_is_forwarded_and_changes_nothing(client):
    d = client.get("/api/design").json()["design"]
    d["screens"]["principal"]["elements"][1].update(x=0, y=0, w=8, h=1)
    r = client.put("/api/design", json=d)
    assert r.status_code == 400
    assert r.json()["errors"]["screens.principal.elements.fecha"] == "se solapa con hora"
    assert client.get("/api/design").json()["saved"] is False


def test_preview_and_cancel(client):
    d = client.get("/api/design").json()["design"]
    r = client.post("/api/design/preview", json={"design": d, "seconds": 30})
    assert r.status_code == 200 and mock.PREVIEW[0]["seconds"] == 30
    assert client.delete("/api/design/preview").status_code == 200
    assert mock.PREVIEW == []


def test_reset_design(client):
    d = client.get("/api/design").json()["design"]
    d["themes"]["clasico"]["name"] = "Otro"
    client.put("/api/design", json=d)
    r = client.post("/api/design/reset")
    assert r.status_code == 200 and r.json()["design"]["themes"]["clasico"]["name"] == "Clásico"


def test_face_data_is_proxied(client):
    r = client.get("/api/face-data")
    assert r.status_code == 200 and r.json()["next_alarm"]["time"] == "07:30"


def test_background_upload_list_serve_delete(client):
    assert client.post("/api/backgrounds", params={"name": "virus.exe"}, content=b"x").status_code == 400
    assert client.post("/api/backgrounds", params={"name": "playa.jpg"}, content=b"").status_code == 400
    r = client.post("/api/backgrounds", params={"name": "grande.jpg"}, content=b"x" * (2 * 1024 * 1024 + 1))
    assert r.status_code == 413
    r = client.post("/api/backgrounds", params={"name": "playa.jpg"}, content=b"\xff\xd8fake")
    assert r.status_code == 200 and r.json()["name"] == "playa.jpg"
    assert client.get("/api/backgrounds").json()["backgrounds"][0]["name"] == "playa.jpg"
    img = client.get("/api/bg/playa.jpg")
    assert img.status_code == 200 and img.content == b"\xff\xd8fake" and img.headers["content-type"] == "image/jpeg"
    assert client.get("/api/bg/nada.jpg").status_code == 404
    assert client.delete("/api/backgrounds/playa.jpg").status_code == 200
    assert client.delete("/api/backgrounds/playa.jpg").status_code == 404


def test_face_version_endpoint_matches_static_copy(client):
    from pathlib import Path
    import app as panel
    expected = face_version(Path(panel.__file__).parent / "static" / "face")
    r = client.get("/api/face-version")
    assert r.status_code == 200 and r.json() == {"ok": True, "face_version": expected}


def test_face_version_is_sha256_of_the_files_in_order(tmp_path):
    # "abc" repartido en los cuatro archivos: debe dar el SHA-256 de la concatenación.
    parts = {"index.html": b"a", "face.css": b"b", "face-core.js": b"c", "face.js": b""}
    for name in FACE_FILES:
        (tmp_path / name).write_bytes(parts[name])
    assert face_version(tmp_path) == "ba7816bf8f01"
```

`tests/test_face_sync.py`:

```python
from pathlib import Path

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[2] / "app-android" / "src" / "main" / "assets" / "face"
STATIC = HERE.parent / "static" / "face"


def test_la_copia_del_motor_es_identica_a_la_del_apk():
    names = {p.name for p in ASSETS.iterdir()}
    assert names == {p.name for p in STATIC.iterdir()}, "faltan o sobran archivos: ejecuta bash tools/sync-face.sh"
    for name in sorted(names):
        assert (ASSETS / name).read_bytes() == (STATIC / name).read_bytes(), f"{name} difiere: ejecuta bash tools/sync-face.sh"
```

- [ ] **Step 2: Ver que fallan.**
Run: `cd homelab/friday-panel && .venv/bin/python -m pytest -q tests/test_design.py tests/test_face_sync.py`
Expected: `test_face_sync` PASS (ya está copiado); los de `test_design` FAIL (`ImportError: face_version`).

- [ ] **Step 3: Implementar en `app.py`.** Añade a los imports `from fastapi.responses import JSONResponse, Response` (amplía el import actual) y, a nivel de módulo junto a las demás constantes:

```python
FACE_FILES = ("index.html", "face.css", "face-core.js", "face.js")
BG_EXTS = {".jpg", ".jpeg", ".png", ".webp"}
MAX_BG_BYTES = 2 * 1024 * 1024


def face_version(face_dir: Path) -> str:
    """SHA-256 abreviado de los archivos del motor, igual que el que calcula el reloj sobre su APK."""
    h = hashlib.sha256()
    for name in FACE_FILES:
        h.update((face_dir / name).read_bytes())
    return h.hexdigest()[:12]
```

Dentro de `create_app`, tras definir `clock(...)` y antes del `app.mount("/", …)`:

```python
    panel_face_version = face_version(STATIC / "face")

    @app.get("/api/design")
    async def get_design():
        return await clock("GET", "/design")

    @app.put("/api/design")
    async def put_design(body: dict = Body(...)):
        return await clock("PUT", "/design", json=body)

    @app.post("/api/design/reset")
    async def reset_design():
        return await clock("POST", "/design/reset", json={})

    @app.post("/api/design/preview")
    async def preview_design(body: dict = Body(...)):
        return await clock("POST", "/design/preview", json=body)

    @app.delete("/api/design/preview")
    async def cancel_preview():
        return await clock("DELETE", "/design/preview")

    @app.get("/api/face-data")
    async def face_data():
        return await clock("GET", "/face/data")

    @app.get("/api/face-version")
    async def get_face_version():
        return {"ok": True, "face_version": panel_face_version}

    @app.get("/api/backgrounds")
    async def list_backgrounds():
        return await clock("GET", "/backgrounds")

    @app.post("/api/backgrounds")
    async def upload_background(request: Request, name: str):
        if Path(name).suffix.lower() not in BG_EXTS:
            return _error(400, "Formato no admitido: usa jpg, png o webp")
        try:
            content_length = int(request.headers.get("content-length") or 0)
        except ValueError:
            return _error(400, "Content-Length no válido")
        if content_length > MAX_BG_BYTES:
            return _error(413, "La imagen supera 2 MB")
        data = await request.body()
        if not data:
            return _error(400, "Archivo vacío")
        if len(data) > MAX_BG_BYTES:
            return _error(413, "La imagen supera 2 MB")
        return await clock("POST", "/backgrounds", content=data, params={"name": name}, timeout=UPLOAD_TIMEOUT_S)

    @app.delete("/api/backgrounds/{name}")
    async def delete_background(name: str):
        return await clock("DELETE", f"/backgrounds/{quote(name, safe='')}")

    @app.get("/api/bg/{name}")
    async def get_background(name: str):
        try:
            async with httpx.AsyncClient(base_url=cfg.clock_url, timeout=cfg.timeout_s) as c:
                r = await c.get(f"/backgrounds/{quote(name, safe='')}", headers={"X-Auth-Token": cfg.clock_token})
        except httpx.HTTPError:
            return _error(502, "El reloj no responde")
        if r.status_code == 404:
            return _error(404, "No existe ese fondo")
        if r.status_code != 200:
            return _error(502, "No se pudo leer el fondo")
        return Response(content=r.content, media_type=r.headers.get("content-type", "image/jpeg"),
                        headers={"Cache-Control": "no-store"})
```

- [ ] **Step 4: Ver que pasan.**
Run: `cd homelab/friday-panel && .venv/bin/python -m pytest -q`
Expected: todos PASS (los 16 anteriores + los nuevos), salida sin avisos.

- [ ] **Step 5: Commit**

```bash
git add homelab/friday-panel/app.py homelab/friday-panel/tests
git commit -m "Panel: rutas del diseño, fondos y versión del motor"
```

---

### Task 9: Pestaña "Pantallas" — vista previa, editor de elementos y pantalla de alarma

**Files:**
- Create: `homelab/friday-panel/static/editor-core.js`
- Create: `homelab/friday-panel/static/pantallas.js`
- Test: `homelab/friday-panel/tests-js/editor-core.test.js`
- Modify: `homelab/friday-panel/static/index.html`, `static/styles.css`, `static/app.js` (`render` y `setView`)

**Interfaces:**
- Consumes: rutas de Task 8, motor `static/face/` (mensajes `face-render`/`face-data`/`face-select`/`face-resize`/`face-empty`), helpers de `app.js` (`h`, `api`, `btn`, `$`, `view`, `route`, `form`, `startForm`, `updateSaveBar`, `toast`, `errText`, `showError`, `confirmInPage`).
- Produces (`window.EditorCore`): `COLS`, `overlaps(a,b)`, `inBounds(r, rows)`, `collides(elements, cand, ignoreId) → elemento|null`, `findFreeSpot(elements, w, h, rows) → {x,y}|null`, `slug(name)`, `uniqueId(base, existingIds)`, `TYPES` (por tipo: `label`, `w`, `h`, `defaults`, `fields`), `newElement(type, id, spot, size)`.
- Produces (en `pantallas.js`): `renderDesign()`, `dz` (estado), `SUBS`, `touch()`, `paintSub()`, helpers de formulario `labeled`, `textField`, `selectField`, `boolField`, `numberField`, `colorField`, `themeSelect`. La Task 10 añade Tema y Perfiles.

- [ ] **Step 1: Tests que fallan.** `homelab/friday-panel/tests-js/editor-core.test.js`:

```js
"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const E = require("../static/editor-core.js");

const el = (id, x, y, w, h) => ({ id, type: "text", x, y, w, h });

test("overlaps e inBounds", () => {
  assert.equal(E.overlaps({ x: 0, y: 0, w: 2, h: 2 }, { x: 1, y: 1, w: 2, h: 2 }), true);
  assert.equal(E.overlaps({ x: 0, y: 0, w: 2, h: 2 }, { x: 2, y: 0, w: 2, h: 2 }), false);
  assert.equal(E.inBounds({ x: 6, y: 0, w: 2, h: 1 }, 6), true);
  assert.equal(E.inBounds({ x: 7, y: 0, w: 2, h: 1 }, 6), false);
  assert.equal(E.inBounds({ x: 0, y: 5, w: 1, h: 2 }, 6), false);
  assert.equal(E.inBounds({ x: 0, y: 3, w: 1, h: 2 }, 4), false); // zona de la alarma: 8x4
  assert.equal(E.inBounds({ x: 0, y: 0, w: 0, h: 1 }, 6), false);
});

test("collides ignora el propio elemento", () => {
  const list = [el("a", 0, 0, 4, 3), el("b", 4, 0, 4, 3)];
  assert.equal(E.collides(list, { x: 0, y: 0, w: 5, h: 3 }, "a").id, "b");
  assert.equal(E.collides(list, { x: 0, y: 0, w: 4, h: 3 }, "a"), null);
  assert.equal(E.collides(list, { x: 0, y: 3, w: 8, h: 1 }), null);
});

test("findFreeSpot busca el primer hueco y devuelve null si no cabe", () => {
  const list = [el("a", 0, 0, 8, 3)];
  assert.deepEqual(E.findFreeSpot(list, 4, 1, 6), { x: 0, y: 3 });
  assert.deepEqual(E.findFreeSpot([], 8, 6, 6), { x: 0, y: 0 });
  const llena = [el("a", 0, 0, 8, 6)];
  assert.equal(E.findFreeSpot(llena, 1, 1, 6), null);
  assert.equal(E.findFreeSpot([], 8, 3, 2), null); // más alto que la rejilla
});

test("slug y uniqueId", () => {
  assert.equal(E.slug("Previsión del día"), "prevision_del_dia");
  assert.equal(E.slug("   "), "x");
  assert.equal(E.slug("a".repeat(40)).length, 24);
  assert.equal(E.uniqueId("Hora", []), "hora");
  assert.equal(E.uniqueId("Hora", ["hora"]), "hora_2");
  assert.equal(E.uniqueId("Hora", ["hora", "hora_2"]), "hora_3");
  assert.ok(E.uniqueId("a".repeat(40), ["a".repeat(24)]).length <= 24);
});

test("newElement toma los valores por defecto del tipo", () => {
  const e = E.newElement("clock", "hora", { x: 1, y: 2 });
  assert.deepEqual(e, { id: "hora", type: "clock", x: 1, y: 2, w: 4, h: 3, style: "digital", seconds: false, hour12: false });
  const t = E.newElement("text", "frase", { x: 0, y: 5 }, { w: 1, h: 1 });
  assert.equal(t.w, 1);
  assert.equal(t.mode, "fixed");
  assert.throws(() => E.newElement("foto", "x", { x: 0, y: 0 }));
});

test("todos los tipos tienen etiqueta, tamaño y campos", () => {
  for (const t of Object.keys(E.TYPES)) {
    const d = E.TYPES[t];
    assert.ok(d.label && d.w >= 1 && d.h >= 1 && Array.isArray(d.fields), t);
  }
  assert.deepEqual(Object.keys(E.TYPES).sort(), ["clock", "date", "forecast", "next_alarm", "text", "weather_now"]);
});
```

- [ ] **Step 2: Ver que fallan.**
Run: `node --test homelab/friday-panel/tests-js/*.test.js`
Expected: FAIL (`Cannot find module …/editor-core.js`).

- [ ] **Step 3: Crear `static/editor-core.js`.**

```js
/* Lógica pura del editor de pantallas (sin DOM): colisiones en la rejilla, hueco libre, ids y
   plantillas de elementos. Se prueba con node --test. */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.EditorCore = factory();
})(this, function () {
  "use strict";
  var COLS = 8;

  var COLOR_OPTIONS = [["fg", "Principal"], ["muted", "Secundario"], ["accent", "Acento"]];
  var TYPES = {
    clock: {
      label: "Hora", w: 4, h: 3, defaults: { style: "digital", seconds: false, hour12: false },
      fields: [
        { key: "style", kind: "select", label: "Estilo", options: [["digital", "Digital"], ["analog", "Agujas"]] },
        { key: "seconds", kind: "bool", label: "Mostrar segundos" },
        { key: "hour12", kind: "bool", label: "Formato de 12 horas" },
      ],
    },
    date: {
      label: "Fecha", w: 4, h: 1, defaults: { format: "long" },
      fields: [{ key: "format", kind: "select", label: "Formato", options: [["long", "Martes, 6 de octubre"], ["short", "mar 6 oct"], ["numeric", "06/10/2026"]] }],
    },
    weather_now: {
      label: "Tiempo ahora", w: 4, h: 1, defaults: { detail: true },
      fields: [{ key: "detail", kind: "bool", label: "Con descripción, máxima, mínima y lluvia" }],
    },
    forecast: {
      label: "Previsión", w: 8, h: 2, defaults: { mode: "hours", count: 4 },
      fields: [
        { key: "mode", kind: "select", label: "Qué mostrar", options: [["hours", "Próximas horas"], ["days", "Próximos días"]] },
        { key: "count", kind: "number", label: "Cuántos (2–5)", min: 2, max: 5 },
      ],
    },
    next_alarm: {
      label: "Próxima alarma", w: 4, h: 1, defaults: { show_label: true },
      fields: [{ key: "show_label", kind: "bool", label: "Mostrar el nombre de la alarma" }],
    },
    text: {
      label: "Texto", w: 8, h: 1, defaults: { mode: "fixed", text: "Hola" },
      fields: [
        { key: "mode", kind: "select", label: "Tipo de texto", options: [["fixed", "Texto fijo"], ["daily", "Frase del día"], ["alarm_label", "Nombre de la alarma sonando"]] },
        { key: "text", kind: "text", label: "Texto", max: 120, when: { key: "mode", value: "fixed" } },
        { key: "quotes", kind: "lines", label: "Frases (una por línea, hasta 20)", when: { key: "mode", value: "daily" } },
      ],
    },
  };

  function overlaps(a, b) {
    return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
  }

  function inBounds(r, rows) {
    return r.x >= 0 && r.y >= 0 && r.w >= 1 && r.h >= 1 && r.x + r.w <= COLS && r.y + r.h <= rows;
  }

  /* Primer elemento que choca con `cand` (ignorando el de id `ignoreId`), o null. */
  function collides(elements, cand, ignoreId) {
    for (var i = 0; i < elements.length; i++) {
      if (elements[i].id !== ignoreId && overlaps(elements[i], cand)) return elements[i];
    }
    return null;
  }

  function findFreeSpot(elements, w, h, rows) {
    for (var y = 0; y + h <= rows; y++) {
      for (var x = 0; x + w <= COLS; x++) {
        if (!collides(elements, { x: x, y: y, w: w, h: h })) return { x: x, y: y };
      }
    }
    return null;
  }

  function slug(name) {
    var s = String(name).normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase()
      .replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 24);
    return s || "x";
  }

  function uniqueId(base, existing) {
    var b = slug(base);
    var id = b;
    var n = 2;
    while (existing.indexOf(id) >= 0) {
      var suffix = "_" + n;
      id = b.slice(0, 24 - suffix.length) + suffix;
      n++;
    }
    return id;
  }

  function newElement(type, id, spot, size) {
    var t = TYPES[type];
    if (!t) throw new Error("tipo desconocido: " + type);
    var e = { id: id, type: type, x: spot.x, y: spot.y, w: size ? size.w : t.w, h: size ? size.h : t.h };
    for (var k in t.defaults) e[k] = t.defaults[k];
    return e;
  }

  return {
    COLS: COLS, TYPES: TYPES, COLOR_OPTIONS: COLOR_OPTIONS, overlaps: overlaps, inBounds: inBounds,
    collides: collides, findFreeSpot: findFreeSpot, slug: slug, uniqueId: uniqueId, newElement: newElement,
  };
});
```

- [ ] **Step 4: Ver que pasan.**
Run: `node --test homelab/friday-panel/tests-js/*.test.js`
Expected: PASS (6 tests).

- [ ] **Step 5: `index.html`.** Sustituye el bloque `#tabs` y las etiquetas `<script>` finales:

```html
  <nav id="tabs">
    <button data-tab="home"><span class="ico">🏠</span>Inicio</button>
    <button data-tab="alarms"><span class="ico">⏰</span>Alarmas</button>
    <button data-tab="design"><span class="ico">🖼️</span>Pantallas</button>
    <button data-tab="settings"><span class="ico">⚙️</span>Ajustes</button>
  </nav>
  <script src="editor-core.js"></script>
  <script src="pantallas.js"></script>
  <script src="app.js"></script>
```

En `app.js`, dentro de `render(next)`, junto a las otras rutas:

```js
  if (route.tab === "design") return renderDesign();
```

**Corrección de un fallo que ya existía:** `view.replaceChildren(a, null, b)` convierte el `null` en el **texto "null"** (el DOM pasa los valores no nodo por `String()`), y `app.js` lo hace en Inicio, Alarmas y la ficha de alarma. Sustituye todas las llamadas por un ayudante que descarta los huecos:

```bash
sed -i 's/view\.replaceChildren(/setView(/g' homelab/friday-panel/static/app.js
```

y define el ayudante justo debajo de `const view = $("#view");`:

```js
// replaceChildren convierte null en el texto "null": se descartan los huecos.
const setView = (...kids) => view.replaceChildren(...kids.flat().filter((k) => k != null && k !== false));
```

- [ ] **Step 6: Estilos.** Añade al final de `static/styles.css`:

```css
.face-card { padding: 0; overflow: hidden; }
.face-wrap { position: relative; width: 100%; height: 0; background: #000; }
.face-frame { position: absolute; left: 0; top: 0; width: 800px; height: 480px; border: 0; transform-origin: 0 0; }
.subtabs { display: flex; margin: 12px 0 0; border-bottom: 1px solid var(--line); }
.subtab { flex: 1; background: none; color: var(--muted); border-radius: 0; padding: 10px 0; font-size: 14px; font-weight: 500; min-height: 40px; }
.subtab.on { color: var(--accent); border-bottom: 2px solid var(--accent); }
.elrow { display: flex; justify-content: space-between; align-items: center; padding: 10px 6px; border-top: 1px solid var(--line); cursor: pointer; }
.elrow.on { background: rgba(79, 195, 247, .12); border-radius: 8px; }
.grid4 { display: flex; }
.grid4 > label { flex: 1; margin: 6px 4px; }
.grid4 input { text-align: center; padding: 8px 4px; }
textarea { width: 100%; box-sizing: border-box; font: inherit; color: var(--text); background: #0f151c; border: 1px solid var(--line); border-radius: 10px; padding: 10px 12px; min-height: 90px; }
.card.warn { border-color: #ffb74d; color: #ffb74d; font-size: 14px; }
input[type=color] { width: 100%; height: 44px; padding: 2px; background: #0f151c; border: 1px solid var(--line); border-radius: 10px; }
```

- [ ] **Step 7: Crear `static/pantallas.js`.**

```js
"use strict";
// Pestaña Pantallas: vista previa fija arriba (el mismo motor que el reloj, en un iframe) y
// sub-pestañas debajo. Edita form.current (el diseño entero) y se guarda con la barra de siempre.

const SAMPLE_DATA = {
  weather: { temp: 19, icon: "☀", description: "Despejado", max: 22, min: 12, rain: 10 },
  forecast: {
    hours: [{ h: 15, temp: 20, icon: "☀" }, { h: 17, temp: 21, icon: "⛅" }, { h: 19, temp: 19, icon: "☁" }, { h: 21, temp: 17, icon: "☂" }, { h: 23, temp: 16, icon: "☁" }],
    days: [{ d: "mié", min: 12, max: 23, icon: "☁" }, { d: "jue", min: 10, max: 20, icon: "☂" }, { d: "vie", min: 10, max: 18, icon: "☂" }, { d: "sáb", min: 10, max: 20, icon: "⛅" }, { d: "dom", min: 12, max: 21, icon: "☀" }],
  },
  next_alarm: { label: "Despertar", when: "mañana", time: "07:30" },
};

const SUBS = [["screen", "Pantalla"], ["alarm", "Alarma"]];
const dz = {
  sub: "screen", screenId: null, themeId: null, profileId: null, selected: null,
  frame: null, ready: false, data: SAMPLE_DATA, backgrounds: [], subEl: null,
  clockVersion: null, panelVersion: null,
};

// ------------------------------------------------------------------ widgets de formulario

const labeled = (label, input) => h("label", { class: "field" }, h("span", {}, label), input);

const textField = (label, value, max, onInput) =>
  labeled(label, h("input", { type: "text", value, maxlength: max, oninput: (ev) => onInput(ev.target.value) }));

const selectField = (label, options, value, onChange) =>
  labeled(label, h("select", { onchange: (ev) => onChange(ev.target.value) },
    options.map(([v, l]) => h("option", { value: v, selected: v === value }, l))));

const boolField = (label, value, onChange) =>
  h("label", { class: "switch" }, h("span", {}, label),
    h("input", { type: "checkbox", checked: !!value, onchange: (ev) => onChange(ev.target.checked) }));

const numberField = (label, value, min, max, onChange) =>
  labeled(label, h("input", { type: "number", min, max, step: "1", inputmode: "numeric", value,
    onchange: (ev) => onChange(ev.target.value === "" ? NaN : Number(ev.target.value)) }));

const colorField = (label, value, onChange) =>
  labeled(label, h("input", { type: "color", value, oninput: (ev) => onChange(ev.target.value) }));

const themeSelect = (current, onChange) =>
  h("select", { onchange: (ev) => onChange(ev.target.value) },
    Object.keys(form.current.themes).map((id) => h("option", { value: id, selected: id === current }, form.current.themes[id].name)));

// ------------------------------------------------------------------ estado y vista previa

const currentRows = () => (dz.sub === "alarm" ? 4 : 6);
const currentElements = () =>
  dz.sub === "alarm" ? form.current.alarm_screen.elements : form.current.screens[dz.screenId].elements;

function touch() { updateSaveBar(); pushPreview(); }

function themeForPreview() {
  if (dz.sub === "theme") return dz.themeId;
  for (const p of form.current.profiles) if (p.screens.includes(dz.screenId)) return p.theme;
  return Object.keys(form.current.themes)[0];
}

function previewOpts() {
  const bgBase = "/api/bg/";
  if (dz.sub === "alarm") return { alarm: true, edit: true, selected: dz.selected, grid: true, bgBase };
  if (dz.sub === "profiles") return { profile: dz.profileId, bgBase };
  if (dz.sub === "theme") return { screen: dz.screenId, theme: dz.themeId, bgBase };
  return { screen: dz.screenId, theme: themeForPreview(), edit: true, selected: dz.selected, grid: true, bgBase };
}

function pushPreview() {
  if (!dz.ready || !dz.frame || !form) return;
  dz.frame.contentWindow.postMessage({ type: "face-render", design: form.current, opts: previewOpts() }, location.origin);
}

function pushData() {
  if (!dz.ready || !dz.frame) return;
  const data = Object.assign({}, dz.data, dz.sub === "alarm" ? { ringing_label: "Despertar" } : {});
  dz.frame.contentWindow.postMessage({ type: "face-data", data }, location.origin);
}

function fitFrame() {
  const wrap = $("#facewrap");
  if (!wrap || !dz.frame) return;
  const k = wrap.clientWidth / 800;
  dz.frame.style.transform = "scale(" + k + ")";
  wrap.style.height = Math.round(480 * k) + "px";
}
window.addEventListener("resize", fitFrame);

async function loadFaceData() {
  const r = await api("GET", "/api/face-data");
  dz.data = r.ok ? r.data : SAMPLE_DATA; // sin reloj, datos de ejemplo
  pushData();
}

window.addEventListener("message", (ev) => {
  if (ev.origin !== location.origin || route.tab !== "design" || !form) return;
  const m = ev.data;
  if (!m || typeof m !== "object") return;
  if (m.type === "face-select") selectElement(m.id);
  else if (m.type === "face-resize") resizeElement(m.id, m.w, m.h);
  else if (m.type === "face-empty") { dz.selected = null; paintSub(); pushPreview(); }
});

function previewFrame() {
  const wrap = h("div", { class: "face-wrap", id: "facewrap" });
  const frame = h("iframe", { class: "face-frame", src: "/face/index.html?preview=1", scrolling: "no", title: "Vista previa del reloj" });
  frame.addEventListener("load", () => { dz.ready = true; fitFrame(); pushPreview(); pushData(); });
  dz.frame = frame;
  dz.ready = false;
  wrap.append(frame);
  return wrap;
}

// ------------------------------------------------------------------ entrada de la pestaña

async function renderDesign() {
  const [rd, rv] = await Promise.all([api("GET", "/api/design"), api("GET", "/api/face-version")]);
  if (route.tab !== "design") return;
  if (!rd.ok) { showError(rd); return; }
  startForm(rd.data.design, () => api("PUT", "/api/design", form.current));
  dz.clockVersion = rd.data.face_version;
  dz.panelVersion = rv.ok ? rv.data.face_version : null;
  if (!dz.screenId || !form.current.screens[dz.screenId]) dz.screenId = Object.keys(form.current.screens)[0];
  if (!dz.themeId || !form.current.themes[dz.themeId]) dz.themeId = Object.keys(form.current.themes)[0];
  if (!dz.profileId || !form.current.profiles.some((p) => p.id === dz.profileId)) dz.profileId = form.current.profiles[0].id;
  dz.selected = null;
  paintDesign();
  loadFaceData();
}

function paintDesign() {
  const warn = dz.clockVersion && dz.panelVersion && dz.clockVersion !== dz.panelVersion
    ? h("div", { class: "card warn" }, `⚠ El reloj y el panel usan versiones distintas del motor (reloj ${dz.clockVersion}, panel ${dz.panelVersion}): la vista previa puede no ser idéntica. Actualiza la app del reloj o el panel.`)
    : null;
  dz.subEl = h("div", {});
  setView(
    warn,
    h("div", { class: "card face-card" }, previewFrame()),
    h("div", { class: "subtabs" }, SUBS.map(([k, label]) =>
      h("button", { class: "subtab" + (dz.sub === k ? " on" : ""), "data-sub": k, onclick: () => switchSub(k) }, label))),
    dz.subEl,
  );
  paintSub();
  fitFrame();
}

function switchSub(k) {
  dz.sub = k;
  dz.selected = null;
  document.querySelectorAll(".subtab").forEach((b) => b.classList.toggle("on", b.dataset.sub === k));
  paintSub();
  pushPreview();
  pushData();
}

function paintSub() {
  if (!dz.subEl) return;
  const fn = { screen: subScreen, alarm: subAlarm }[dz.sub];
  dz.subEl.replaceChildren(...fn());
}

// ------------------------------------------------------------------ sub-pestaña Pantalla

function subScreen() {
  const d = form.current;
  const scr = d.screens[dz.screenId];
  const sel = h("select", { onchange: (ev) => { dz.screenId = ev.target.value; dz.selected = null; paintSub(); pushPreview(); } },
    Object.keys(d.screens).map((id) => h("option", { value: id, selected: id === dz.screenId }, d.screens[id].name)));
  return [
    h("div", { class: "card" },
      labeled("Pantalla", sel),
      h("div", { class: "row" },
        btn("＋ Nueva", addScreen, "small ghost"),
        btn("Duplicar", duplicateScreen, "small ghost"),
        btn("Borrar", deleteScreen, "small danger")),
      textField("Nombre", scr.name, 30, (v) => { scr.name = v; updateSaveBar(); })),
    elementsPanel(),
  ];
}

function addScreen() {
  const d = form.current;
  if (Object.keys(d.screens).length >= 8) { toast("Máximo 8 pantallas", true); return; }
  const id = EditorCore.uniqueId("pantalla", Object.keys(d.screens));
  d.screens[id] = { name: "Nueva pantalla", elements: [] };
  dz.screenId = id; dz.selected = null;
  touch(); paintSub();
}

function duplicateScreen() {
  const d = form.current;
  if (Object.keys(d.screens).length >= 8) { toast("Máximo 8 pantallas", true); return; }
  const src = d.screens[dz.screenId];
  const id = EditorCore.uniqueId(src.name, Object.keys(d.screens));
  d.screens[id] = { name: (src.name + " (copia)").slice(0, 30), elements: structuredClone(src.elements) };
  dz.screenId = id; dz.selected = null;
  touch(); paintSub();
}

async function deleteScreen() {
  const d = form.current;
  if (Object.keys(d.screens).length <= 1) { toast("Tiene que quedar al menos una pantalla", true); return; }
  const users = d.profiles.filter((p) => p.screens.includes(dz.screenId)).map((p) => p.name);
  if (users.length) { toast("La usan los perfiles: " + users.join(", "), true); return; }
  if (!(await confirmInPage("¿Borrar esta pantalla?", "Borrar"))) return;
  delete d.screens[dz.screenId];
  dz.screenId = Object.keys(d.screens)[0]; dz.selected = null;
  touch(); paintSub();
}

// ------------------------------------------------------------------ lista y opciones de elementos

function elementsPanel() {
  const list = currentElements();
  const sel = list.find((e) => e.id === dz.selected);
  const T = EditorCore.TYPES;
  return h("div", { class: "card" },
    h("h2", {}, "Elementos"),
    h("p", { class: "muted" }, "Toca un elemento en la vista previa o en la lista. Arrastra su esquina azul para cambiar el tamaño."),
    list.length
      ? list.map((e) => h("div", { class: "elrow" + (e.id === dz.selected ? " on" : ""), onclick: () => selectElement(e.id) },
        h("span", {}, T[e.type].label + " · " + e.id), h("span", { class: "muted" }, e.w + "×" + e.h + " en " + e.x + "," + e.y)))
      : h("p", { class: "muted" }, "Sin elementos."),
    addMenu(),
    sel ? elementOptions(sel) : null);
}

function addMenu() {
  const T = EditorCore.TYPES;
  const sel = h("select", {}, Object.keys(T).map((t) => h("option", { value: t }, T[t].label)));
  return h("div", { class: "row", style: "margin-top:10px" }, sel, btn("＋ Añadir", () => addElement(sel.value), "small"));
}

function addElement(type) {
  const list = currentElements();
  const rows = currentRows();
  if (list.length >= 12) { toast("Máximo 12 elementos por pantalla", true); return; }
  const t = EditorCore.TYPES[type];
  let size = { w: t.w, h: t.h };
  let spot = EditorCore.findFreeSpot(list, size.w, size.h, rows);
  if (!spot) { size = { w: 1, h: 1 }; spot = EditorCore.findFreeSpot(list, 1, 1, rows); }
  if (!spot) { toast("No hay hueco libre en la rejilla", true); return; }
  const id = EditorCore.uniqueId(t.label, list.map((e) => e.id));
  list.push(EditorCore.newElement(type, id, spot, size));
  dz.selected = id;
  touch(); paintSub();
}

function selectElement(id) {
  dz.selected = id;
  paintSub();
  pushPreview();
}

function setRect(e, patch) {
  const cand = Object.assign({ x: e.x, y: e.y, w: e.w, h: e.h }, patch);
  if (!EditorCore.inBounds(cand, currentRows())) { toast("Se sale de la rejilla", true); return false; }
  const other = EditorCore.collides(currentElements(), cand, e.id);
  if (other) { toast("Se solapa con " + other.id, true); return false; }
  Object.assign(e, patch);
  touch();
  return true;
}

function resizeElement(id, w, hh) {
  const e = currentElements().find((x) => x.id === id);
  if (!e) return;
  const cand = { x: e.x, y: e.y, w, h: hh };
  if (EditorCore.inBounds(cand, currentRows()) && !EditorCore.collides(currentElements(), cand, id)) {
    e.w = w; e.h = hh; touch();
  } else {
    toast("No cabe ahí: se solaparía", true);
    pushPreview();
  }
  paintSub();
}

function typedField(e, f) {
  const def = EditorCore.TYPES[e.type].defaults[f.key];
  const val = e[f.key] === undefined ? def : e[f.key];
  if (f.when && (e[f.when.key] === undefined ? EditorCore.TYPES[e.type].defaults[f.when.key] : e[f.when.key]) !== f.when.value) return null;
  if (f.kind === "select") return selectField(f.label, f.options, val, (v) => { e[f.key] = v; touch(); paintSub(); });
  if (f.kind === "bool") return boolField(f.label, val, (v) => { e[f.key] = v; touch(); });
  if (f.kind === "number") return numberField(f.label, val, f.min, f.max, (v) => { e[f.key] = v; touch(); });
  if (f.kind === "text") return textField(f.label, val || "", f.max, (v) => { e[f.key] = v; touch(); });
  if (f.kind === "lines") {
    return labeled(f.label, h("textarea", { rows: 5, oninput: (ev) => { e[f.key] = ev.target.value.split("\n").map((s) => s.trim()).filter(Boolean).slice(0, 20); touch(); } },
      (e[f.key] || []).join("\n")));
  }
  return null;
}

function elementOptions(e) {
  const T = EditorCore.TYPES;
  const rows = currentRows();
  const pos = (key, min, max) => numberField(key.toUpperCase(), e[key], min, max, (v) => { if (!setRect(e, { [key]: v })) paintSub(); });
  return h("div", { class: "opt" },
    h("h2", {}, T[e.type].label),
    h("div", { class: "grid4" }, pos("x", 0, 7), pos("y", 0, rows - 1), pos("w", 1, 8), pos("h", 1, rows)),
    selectField("Color", EditorCore.COLOR_OPTIONS, e.color || "fg", (v) => { e.color = v; touch(); }),
    selectField("Alineación", [["left", "Izquierda"], ["center", "Centro"], ["right", "Derecha"]], e.align || "center", (v) => { e.align = v; touch(); }),
    ...T[e.type].fields.map((f) => typedField(e, f)),
    btn("Borrar elemento", () => {
      const list = currentElements();
      list.splice(list.indexOf(e), 1);
      dz.selected = null;
      touch(); paintSub();
    }, "danger small"));
}

// ------------------------------------------------------------------ sub-pestaña Alarma

function subAlarm() {
  const a = form.current.alarm_screen;
  return [
    h("div", { class: "card" },
      h("h2", {}, "Pantalla de alarma sonando"),
      h("p", { class: "muted" }, "Diseñas la zona de arriba (rejilla de 8×4). Los botones de abajo son del reloj y funcionan siempre."),
      labeled("Tema", themeSelect(a.theme, (v) => { a.theme = v; touch(); })),
      textField("Texto de Posponer (usa {min} para los minutos)", a.snooze_label === undefined ? "Posponer {min} min" : a.snooze_label, 20, (v) => { a.snooze_label = v; touch(); }),
      textField("Texto de Descartar", a.dismiss_label === undefined ? "Descartar" : a.dismiss_label, 20, (v) => { a.dismiss_label = v; touch(); })),
    elementsPanel(),
  ];
}
```

- [ ] **Step 8: Comprobar sintaxis y tests.**
Run: `node --check homelab/friday-panel/static/pantallas.js && node --check homelab/friday-panel/static/editor-core.js && node --check homelab/friday-panel/static/app.js && node --test homelab/friday-panel/tests-js/*.test.js && cd homelab/friday-panel && .venv/bin/python -m pytest -q`
Expected: sin errores de sintaxis; tests PASS.

- [ ] **Step 9: Commit**

```bash
git add homelab/friday-panel/static homelab/friday-panel/tests-js
git commit -m "Panel: pestaña Pantallas con vista previa fiel, editor de elementos y pantalla de alarma"
```

---
### Task 10: Pestaña "Pantallas" — Tema, Perfiles, fondos, "Probar en el reloj" y restaurar

**Files:**
- Modify: `homelab/friday-panel/static/pantallas.js`
- Modify: `homelab/friday-panel/static/index.html`, `static/styles.css`, `static/app.js` (solo `render`)

**Interfaces:**
- Consumes: todo lo de Task 9 (`dz`, `SUBS`, `paintSub`, `touch`, `pushPreview`, widgets de formulario) y las rutas de Task 8.
- Produces: `subTheme()`, `subProfiles()`, `refreshBackgrounds()`, `restoreInitial()`, `bindTry()`; botón fijo **▶ Probar** en la barra (`#try`).

- [ ] **Step 1: Botón Probar.** En `index.html`, dentro de `#savebar`, entre Descartar y Guardar:

```html
    <button id="try" class="ghost" hidden>▶ Probar</button>
```

En `styles.css`:

```css
#savebar #try { flex: 1.4; }
```

En `app.js`, dentro de `render(next)` (junto a la línea de `design`), para que solo se vea en esa pestaña:

```js
  $("#try").hidden = route.tab !== "design";
```

- [ ] **Step 2: Sub-pestañas y datos.** En `pantallas.js`:

1. Sustituye `SUBS` por:

```js
const SUBS = [["screen", "Pantalla"], ["theme", "Tema"], ["profiles", "Perfiles"], ["alarm", "Alarma"]];
```

2. En `paintSub`, sustituye el mapa por:

```js
  const fn = { screen: subScreen, theme: subTheme, profiles: subProfiles, alarm: subAlarm }[dz.sub];
```

3. En `renderDesign`, carga también los fondos y enlaza el botón. Sustituye sus cuatro primeras líneas (hasta `startForm(...)`) por:

```js
async function renderDesign() {
  const [rd, rv, rb] = await Promise.all([api("GET", "/api/design"), api("GET", "/api/face-version"), api("GET", "/api/backgrounds")]);
  if (route.tab !== "design") return;
  if (!rd.ok) { showError(rd); return; }
  dz.backgrounds = rb.ok ? rb.data.backgrounds : [];
  bindTry();
  startForm(rd.data.design, () => api("PUT", "/api/design", form.current));
```

- [ ] **Step 3: Añadir al final de `pantallas.js`** (Tema, fondos, Perfiles, Probar, restaurar):

```js
// ------------------------------------------------------------------ Probar en el reloj

function bindTry() {
  if (dz.tryBound) return;
  dz.tryBound = true;
  $("#try").addEventListener("click", async () => {
    if (route.tab !== "design" || !form) return;
    const b = $("#try");
    b.disabled = true;
    const r = await api("POST", "/api/design/preview", { design: form.current, seconds: 30 });
    b.disabled = false;
    if (r.ok) { toast("▶ Mostrándose 30 s en el reloj"); return; }
    const detail = Object.entries(r.data.errors || {}).map(([k, v]) => `${k}: ${v}`).join(" · ");
    toast(errText(r) + (detail ? " — " + detail : ""), true);
  });
}

// ------------------------------------------------------------------ sub-pestaña Tema

const FONT_OPTIONS = [["thin", "Fina"], ["normal", "Normal"], ["bold", "Negrita"], ["mono", "Monoespaciada"], ["digital", "Digital (7 segmentos)"]];

function subTheme() {
  const t = form.current.themes[dz.themeId];
  return [
    h("div", { class: "card" },
      labeled("Tema", themeSelect(dz.themeId, (v) => { dz.themeId = v; paintSub(); pushPreview(); })),
      h("div", { class: "row" }, btn("Duplicar", duplicateTheme, "small ghost"), btn("Borrar", deleteTheme, "small danger")),
      textField("Nombre", t.name, 30, (v) => { t.name = v; updateSaveBar(); }),
      selectField("Letra de la hora y los textos", FONT_OPTIONS, t.font, (v) => { t.font = v; touch(); }),
      colorField("Color principal", t.fg, (v) => { t.fg = v; touch(); }),
      colorField("Color secundario", t.muted, (v) => { t.muted = v; touch(); }),
      colorField("Color de acento", t.accent, (v) => { t.accent = v; touch(); })),
    h("div", { class: "card" }, h("h2", {}, "Fondo"), ...bgFields(t)),
    h("div", { class: "card" },
      h("p", { class: "muted" }, "Vuelve al diseño que traía el reloj (borra el que tienes guardado)."),
      btn("Restaurar diseño inicial", restoreInitial, "ghost")),
  ];
}

function bgFields(t) {
  const bg = t.bg;
  const kind = selectField("Tipo de fondo", [["color", "Color liso"], ["gradient", "Degradado"], ["image", "Imagen"]], bg.type, (v) => {
    const base = bg.color || bg.from || "#000000";
    if (v === "color") t.bg = { type: "color", color: base };
    else if (v === "gradient") t.bg = { type: "gradient", from: base, to: bg.to || "#ffffff", angle: bg.angle === undefined ? 160 : bg.angle };
    else t.bg = { type: "image", image: dz.backgrounds.length ? dz.backgrounds[0].name : "", dim: 30, color: base };
    touch(); paintSub();
  });
  if (bg.type === "color") return [kind, colorField("Color", bg.color, (v) => { bg.color = v; touch(); })];
  if (bg.type === "gradient") {
    return [kind,
      colorField("Desde", bg.from, (v) => { bg.from = v; touch(); }),
      colorField("Hasta", bg.to, (v) => { bg.to = v; touch(); }),
      numberField("Ángulo (0–359)", bg.angle === undefined ? 160 : bg.angle, 0, 359, (v) => { bg.angle = v; touch(); })];
  }
  return [kind, imagePicker(bg),
    numberField("Oscurecer la imagen (0–80 %)", bg.dim === undefined ? 0 : bg.dim, 0, 80, (v) => { bg.dim = v; touch(); }),
    colorField("Color si falta la imagen", bg.color || "#000000", (v) => { bg.color = v; touch(); })];
}

async function refreshBackgrounds() {
  const r = await api("GET", "/api/backgrounds");
  dz.backgrounds = r.ok ? r.data.backgrounds : [];
}

function imagePicker(bg) {
  const known = new Set(dz.backgrounds.map((b) => b.name));
  const select = h("select", { onchange: (ev) => { bg.image = ev.target.value; touch(); paintSub(); } },
    h("option", { value: "", selected: !bg.image }, "(ninguna)"),
    dz.backgrounds.map((b) => h("option", { value: b.name, selected: b.name === bg.image }, `${b.name} · ${Math.round(b.size / 1024)} KB`)));
  const file = h("input", { type: "file", accept: "image/jpeg,image/png,image/webp", hidden: true,
    onchange: async (ev) => {
      const f = ev.target.files[0];
      if (!f) return;
      if (f.size > 2 * 1024 * 1024) { toast("La imagen supera 2 MB", true); return; }
      toast("Subiendo…");
      const r = await api("POST", `/api/backgrounds?name=${encodeURIComponent(f.name)}`, undefined, f);
      if (!r.ok) { toast(errText(r), true); return; }
      toast("✓ Imagen subida al reloj");
      bg.image = r.data.name;
      await refreshBackgrounds();
      touch(); paintSub();
    } });
  return h("div", { class: "opt" },
    labeled("Imagen", select),
    bg.image && !known.has(bg.image) ? h("div", { class: "warn" }, "⚠ Esa imagen ya no está en el reloj: se verá el color de respaldo.") : null,
    h("div", { class: "row" }, btn("Subir imagen…", () => file.click(), "small ghost"), file),
    dz.backgrounds.map((b) => h("div", { class: "row between" }, h("span", {}, b.name), btn("Borrar", () => deleteBackground(b.name), "small ghost"))));
}

async function deleteBackground(name) {
  const used = Object.values(form.current.themes).filter((t) => t.bg.type === "image" && t.bg.image === name).map((t) => t.name);
  const msg = used.length ? `¿Borrar ${name}? Lo usan los temas: ${used.join(", ")}. Se verá su color de respaldo.` : `¿Borrar ${name}?`;
  if (!(await confirmInPage(msg, "Borrar"))) return;
  const r = await api("DELETE", `/api/backgrounds/${encodeURIComponent(name)}`);
  toast(r.ok ? "✓ Imagen borrada" : errText(r), !r.ok);
  await refreshBackgrounds();
  paintSub(); pushPreview();
}

function duplicateTheme() {
  const d = form.current;
  if (Object.keys(d.themes).length >= 12) { toast("Máximo 12 temas", true); return; }
  const src = d.themes[dz.themeId];
  const id = EditorCore.uniqueId(src.name, Object.keys(d.themes));
  d.themes[id] = Object.assign(structuredClone(src), { name: (src.name + " (copia)").slice(0, 30) });
  dz.themeId = id;
  touch(); paintSub();
}

async function deleteTheme() {
  const d = form.current;
  if (Object.keys(d.themes).length <= 1) { toast("Tiene que quedar al menos un tema", true); return; }
  const users = d.profiles.filter((p) => p.theme === dz.themeId).map((p) => p.name);
  if (d.alarm_screen.theme === dz.themeId) users.push("pantalla de alarma");
  if (users.length) { toast("Lo usan: " + users.join(", "), true); return; }
  if (!(await confirmInPage("¿Borrar este tema?", "Borrar"))) return;
  delete d.themes[dz.themeId];
  dz.themeId = Object.keys(d.themes)[0];
  touch(); paintSub();
}

async function restoreInitial() {
  if (!(await confirmInPage("Se borrará el diseño guardado y volverá el inicial. ¿Seguir?", "Restaurar"))) return;
  const r = await api("POST", "/api/design/reset");
  if (!r.ok) { toast(errText(r), true); return; }
  toast("✓ Diseño inicial restaurado");
  form = null; updateSaveBar();
  renderDesign();
}

// ------------------------------------------------------------------ sub-pestaña Perfiles

const ROTATE_OPTIONS = [[0, "Solo deslizando"], [10, "Cada 10 s"], [30, "Cada 30 s"], [60, "Cada minuto"], [300, "Cada 5 minutos"]];

function subProfiles() {
  return [
    ...form.current.profiles.map(profileCard),
    h("div", { class: "card" }, btn("＋ Nuevo perfil", addProfile, "ghost")),
  ];
}

function profileCard(p) {
  const d = form.current;
  const rotate = p.rotate_s === undefined ? 0 : p.rotate_s;
  const options = ROTATE_OPTIONS.some(([v]) => v === rotate) ? ROTATE_OPTIONS : [...ROTATE_OPTIONS, [rotate, `Cada ${rotate} s`]];
  const current = p.id === dz.profileId;
  return h("div", { class: "card" + (current ? " ring" : "") },
    h("div", { class: "row between" },
      h("h2", {}, p.name),
      current ? h("span", { class: "muted" }, "en la vista previa")
        : btn("Ver", () => { dz.profileId = p.id; paintSub(); pushPreview(); }, "small ghost")),
    textField("Nombre", p.name, 30, (v) => { p.name = v; updateSaveBar(); }),
    labeled("Empieza a las", h("input", { type: "time", value: p.from, onchange: (ev) => { p.from = ev.target.value; touch(); } })),
    labeled("Tema", themeSelect(p.theme, (v) => { p.theme = v; touch(); })),
    h("div", { class: "field" },
      h("span", { class: "muted" }, "Pantallas de este perfil"),
      Object.keys(d.screens).map((id) => boolField(d.screens[id].name, p.screens.includes(id), (on) => {
        const ids = Object.keys(d.screens).filter((x) => (x === id ? on : p.screens.includes(x)));
        if (!ids.length) { toast("Cada perfil necesita al menos una pantalla", true); paintSub(); return; }
        p.screens = ids;
        touch();
      }))),
    selectField("Cambio de pantalla", options.map(([v, l]) => [String(v), l]), String(rotate), (v) => { p.rotate_s = Number(v); touch(); }),
    numberField("Brillo (1–100)", p.brightness === undefined ? 70 : p.brightness, 1, 100, (v) => { p.brightness = v; touch(); }),
    boolField("Modo noche (se atenúa y tocar la pantalla la ilumina un momento)", p.night, (v) => { p.night = v; touch(); }),
    boolField("Mostrar la barra de botones", p.show_bar !== false, (v) => { p.show_bar = v; touch(); }),
    d.profiles.length > 1 ? btn("Borrar perfil", () => deleteProfile(p), "small danger") : null);
}

function freeHour() {
  const used = new Set(form.current.profiles.map((p) => p.from));
  for (let hr = 0; hr < 24; hr++) {
    const s = String(hr).padStart(2, "0") + ":00";
    if (!used.has(s)) return s;
  }
  return "12:30";
}

function addProfile() {
  const d = form.current;
  if (d.profiles.length >= 4) { toast("Máximo 4 perfiles", true); return; }
  const id = EditorCore.uniqueId("perfil", d.profiles.map((p) => p.id));
  d.profiles.push({
    id, name: "Nuevo perfil", from: freeHour(), theme: Object.keys(d.themes)[0],
    screens: [Object.keys(d.screens)[0]], rotate_s: 0, brightness: 70, night: false, show_bar: true,
  });
  dz.profileId = id;
  touch(); paintSub();
}

async function deleteProfile(p) {
  const d = form.current;
  if (d.profiles.length <= 1) { toast("Tiene que quedar al menos un perfil", true); return; }
  if (!(await confirmInPage(`¿Borrar el perfil «${p.name}»?`, "Borrar"))) return;
  d.profiles.splice(d.profiles.indexOf(p), 1);
  if (dz.profileId === p.id) dz.profileId = d.profiles[0].id;
  touch(); paintSub();
}
```

- [ ] **Step 4: Comprobar.**
Run: `node --check homelab/friday-panel/static/pantallas.js && node --check homelab/friday-panel/static/app.js && cd homelab/friday-panel && .venv/bin/python -m pytest -q`
Expected: sin errores; tests PASS.

- [ ] **Step 5: Prueba con el navegador (solo si hay uno disponible).** Arranca el reloj de mentira y el panel y abre `http://127.0.0.1:18770` con la ventana a 390 px de ancho:

```bash
python3 hermes-skill/mock_clock_server.py --port 18080 --token secreto &
CLOCK_URL=http://127.0.0.1:18080 CLOCK_TOKEN=secreto PANEL_PORT=18770 homelab/friday-panel/.venv/bin/python homelab/friday-panel/app.py &
```

Comprueba: pestaña **Pantallas** → la vista previa muestra la hora y la fecha; tocar un elemento lo selecciona; cambiar el color del tema cambia la vista previa al instante; Tema → Fondo → Degradado; Perfiles → Nuevo perfil; Alarma → añadir un texto; aparece la barra con **Descartar · ▶ Probar · Guardar**; Guardar da "✓ Aplicado en el reloj". Para los dos procesos al terminar (`kill` de sus PID). Si no hay navegador, anótalo en el informe: lo verifica el controlador en Task 12.

- [ ] **Step 6: Commit**

```bash
git add homelab/friday-panel/static
git commit -m "Panel: sub-pestañas Tema y Perfiles, fondos, Probar en el reloj y restaurar diseño inicial"
```

---

### Task 11: Grupo de ajustes, CI y documentación

**Files:**
- Modify: `app-android/src/main/java/com/jarvis/clock/SettingsSchema.kt`
- Modify: `hermes-skill/mock_clock_server.py` (grupo)
- Modify: `.github/workflows/build-apk.yml`
- Modify: `README.md`, `homelab/friday-panel/README.md`, `CLAUDE.md`

- [ ] **Step 1: Renombrar el grupo.** En `SettingsSchema.kt`: `private const val G_SCREEN = "Pantalla"` → `private const val G_SCREEN = "Carátula de respaldo"`. En `mock_clock_server.py`, las cuatro entradas de `SCHEMA` con `"group": "Pantalla"` pasan a `"group": "Carátula de respaldo"`. Comprueba que ningún test depende del nombre antiguo (`grep -rn '"Pantalla"' app-android/src/test hermes-skill/tests homelab/friday-panel/tests`) y corrígelo si lo hay.

- [ ] **Step 2: CI.** En `.github/workflows/build-apk.yml`, job `homelab`, añade tras `setup-python`:

```yaml
      - uses: actions/setup-node@v4
        with:
          node-version: "20"
```

y al final del job:

```yaml
      - name: Motor de pantallas y editor (node) y copia en el panel
        run: |
          node --check app-android/src/main/assets/face/face.js
          node --check homelab/friday-panel/static/pantallas.js
          node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js
          bash tools/sync-face.sh --check
```

- [ ] **Step 3: README raíz.** En la tabla "API HTTP del reloj" añade, antes de `POST /actions`:

```markdown
| `GET /design` | — | diseño de pantallas (temas, pantallas en rejilla 8×6, perfiles, pantalla de alarma) y `saved` |
| `PUT /design` | el diseño entero | validar y guardar (todo o nada: `400` con `errors` por ruta); se aplica al instante |
| `POST /design/reset` | — | restaurar el diseño inicial |
| `POST /design/preview` | `{"design":{…},"seconds":30}` | mostrar un diseño 5–120 s sin guardarlo (`409` si suena una alarma) |
| `DELETE /design/preview` | — | cancelar la prueba |
| `GET /backgrounds` · `POST /backgrounds?name=x.jpg` · `GET /backgrounds/{nombre}` · `DELETE /backgrounds/{nombre}` | imagen en crudo (≤ 2 MB, jpg/png/webp, ≤ 2048 px) | fondos de pantalla |
| `GET /face/data` | — | los datos que recibe el motor: tiempo, previsión y próxima alarma |
```

Y una sección nueva, tras "Carátula" en "Qué hace Friday":

```markdown
### Pantallas y temas

La carátula y la pantalla de alarma se describen en un diseño (JSON): **temas** (colores, letra,
fondo liso, degradado o imagen), **pantallas** en una rejilla de 8×6 con hora, fecha, tiempo,
previsión, próxima alarma y texto o frase del día, y **perfiles** por hora (Mañana, Día, Noche…)
con su tema, sus pantallas, rotación y brillo. Se edita desde la pestaña **Pantallas** del panel
web, con una vista previa idéntica a la del reloj y un botón **▶ Probar** que lo muestra 30 s en el
reloj sin guardarlo. Si el `WebView` del reloj fallase, vuelve la carátula nativa de siempre; las
alarmas no dependen de él.
```

En "Estructura del repositorio" añade: `app-android/src/main/assets/face/  motor de pantallas (HTML/JS), copiado a homelab/friday-panel/static/face/ con tools/sync-face.sh`.

- [ ] **Step 4: README del panel.** En `homelab/friday-panel/README.md`, añade a la descripción: "editor de **Pantallas** (temas, pantallas en rejilla, perfiles por hora y pantalla de alarma) con vista previa exacta y prueba de 30 s en el reloj". Añade una nota: "El motor de la vista previa (`static/face/`) es una copia del del APK: tras cambiar `app-android/src/main/assets/face/` ejecuta `bash tools/sync-face.sh`."

- [ ] **Step 5: `CLAUDE.md`.** En "Commands" añade:

```bash
node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js   # motor de pantallas y editor
bash tools/sync-face.sh           # copia el motor del APK al panel (--check lo verifica; la CI lo exige)
```

En "Architecture", tras el párrafo de "Android app", añade: "La carátula y la pantalla de alarma las dibuja un motor HTML/JS (`app-android/src/main/assets/face/`, ES2017 por el WebView Chrome 74) a partir de un diseño JSON validado por `design/DesignSchema.kt` y guardado en `design/DesignStore.kt`; el mismo motor, copiado a `homelab/friday-panel/static/face/`, da la vista previa del panel. `ui/FaceHost.kt` aloja el WebView; si falla, `MainActivity` vuelve a la carátula nativa. Las alarmas nunca dependen del WebView." Y en la línea de CI, añade: "y en el job de homelab los tests `node --test` del motor y del editor y `tools/sync-face.sh --check`".

- [ ] **Step 6: Comprobar todo.**
Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug && (cd hermes-skill && python3 -m pytest -q) && (cd homelab/friday-panel && .venv/bin/python -m pytest -q) && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && bash tools/sync-face.sh --check`
Expected: todo en verde.

- [ ] **Step 7: Commit**

```bash
git add app-android/src/main/java/com/jarvis/clock/SettingsSchema.kt hermes-skill/mock_clock_server.py .github/workflows/build-apk.yml README.md homelab/friday-panel/README.md CLAUDE.md
git commit -m "Pantallas: grupo Carátula de respaldo, CI del motor y documentación"
```

---

### Task 12: Verificación en el reloj real y en el homelab

No hay código nuevo: es la comprobación de punta a punta. La hace el controlador (no un subagente) porque necesita el reloj por ADB (`adb devices`) y el homelab por SSH (`friday@<ip-lan-homelab>`, con la clave del usuario). **Pide permiso al usuario antes de instalar el APK y antes de desplegar el panel.** Las alarmas del reloj no se tocan; si se crean alarmas de prueba, se borran al terminar.

- [ ] **Step 1: Instalar.** `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleRelease && adb install -r app-android/build/outputs/apk/release/app-android-release.apk` y abrir Friday. Lee el token del reloj sin mostrarlo (`adb shell su -c 'cat /data/data/com.jarvis.clock/shared_prefs/settings.xml'`).
- [ ] **Step 2: Se ve igual que antes.** Captura `adb exec-out screencap -p > /tmp/…/inicial.png` y míralo: hora grande y fina, fecha, tiempo y próxima alarma en el mismo sitio de siempre. Comprueba `GET /design` (`saved:false`, perfiles Día y Noche) y que las alarmas siguen en su sitio.
- [ ] **Step 3: Previsión.** `curl "https://api.open-meteo.com/v1/forecast?latitude=40.4168&longitude=-3.7038&hourly=temperature_2m&forecast_hours=12&timezone=auto"` y confirma que la **primera hora devuelta es la actual**; si no lo es, ajusta en `Weather.parse` desde qué índice se toman las horas.
- [ ] **Step 4: Aplicar y probar.** Con `PUT /design` cambia el tema de Día a Amanecer: se ve el degradado al instante. `POST /design/preview` con otro diseño: se ve y a los 30 s vuelve solo (captura antes y después). `POST /design/preview` con una alarma sonando → `409`.
- [ ] **Step 5: Perfiles y noche.** Cambia el `from` del perfil Noche a unos minutos después y comprueba que entra: brillo bajo, barra atenuada, y que tocar la pantalla sube el brillo unos segundos sin cambiar el tema.
- [ ] **Step 6: Alarma.** Crea una alarma para dentro de 1–2 minutos con el panel: la zona de arriba la dibuja el motor (hora y nombre), los botones tienen los colores del tema y Posponer/Descartar funcionan; la alarma sigue sonando con su sonido.
- [ ] **Step 7: Respaldo (opcional, reversible).** `adb shell pm disable-user --user 0 com.android.webview`, abre Friday: aparece la carátula nativa; `adb shell pm enable com.android.webview` para dejarlo como estaba. Solo con permiso del usuario.
- [ ] **Step 8: Memoria.** Con `adb shell dumpsys meminfo com.jarvis.clock` compara el "TOTAL PSS" con la versión anterior (la de la rama `panel-web`). Si el aumento supera **100 MB**, abre una tarea de corrección para liberar el WebView cuando la carátula pase a segundo plano (spec, "Errores y casos límite").
- [ ] **Step 9: Panel.** `rsync -a --exclude .venv --exclude __pycache__ --exclude tests --exclude tests-js homelab/friday-panel friday@<ip-lan-homelab>:~/` y `ssh friday@<ip-lan-homelab> 'cd ~/friday-panel && docker compose up -d --build'`. Con las herramientas del navegador abre `http://<ip-lan-homelab>:8770` a 390 px, ve a **Pantallas** y comprueba: la vista previa es idéntica a la captura del reloj; arrastrar la esquina de un elemento lo redimensiona y rechaza solapes; subir una imagen de fondo funciona y se ve en el reloj; **▶ Probar** la muestra 30 s; Guardar la deja fija. Toma capturas.
- [ ] **Step 10: Limpieza.** Borra las alarmas, los fondos y los diseños de prueba (`POST /design/reset` si procede) y deja anotado en el informe todo lo que no se pudo comprobar.

---
