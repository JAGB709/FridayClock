# Fotos ajustables, carrusel, arrastrar y deslizar (3c) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fotos sin restricción de tamaño y ajustables, elemento carrusel `photos`, mover elementos arrastrando en el editor web y deslizar la carátula del reloj para abrir Alarmas o Ajustes.

**Architecture:** El esquema (`DesignSchema.kt`) añade los campos de ajuste de foto y el elemento `photos`; `Backgrounds.kt` reduce las fotos al guardar; el motor `face/` (JS, copiado al panel) dibuja la capa de foto, el carrusel, distingue los deslizamientos y mueve elementos en modo edición; `FaceHost`/`MainActivity` reciben `swipe`; el panel reduce en el navegador, ajusta fotos y procesa `face-move`.

**Tech Stack:** Kotlin 1.9 sin dependencias de runtime (JUnit solo en test), JS ES2017 clásico (WebView Chrome 74: sin `?.`, `??`, `inset`, `gap` en flex), FastAPI + JS vanilla en el panel, `node --test`, pytest.

**Spec:** `docs/superpowers/specs/2026-10-07-fotos-gestos-design.md`

## Global Constraints

- Sin emojis nuevos en código, textos ni comentarios (el rediseño completo es el subproyecto 4; no tocar los emojis ya existentes).
- Texto de interfaz y comentarios en español; commits en español, **sin** líneas `Co-Authored-By` ni `Claude-Session` (regla del usuario, vale también para subagentes).
- App del reloj sin dependencias de runtime (solo framework Android + Kotlin stdlib).
- JS del motor en ES2017 clásico: sin `?.`, `??`, `inset`, `gap` en flex, `aspect-ratio`.
- Tras editar cualquier archivo de `app-android/src/main/assets/face/`, ejecutar `bash tools/sync-face.sh` (la CI usa `--check`).
- Límites: reducción de foto a lado largo 1600 px; subida máxima 8 MB (`8 * 1024 * 1024`); biblioteca máxima 60 fotos; `photos`: 1 a 20 imágenes, `interval_s` 3 a 600 (por defecto 10), `transition` `fade|none`, `order` `sequential|random`; máx. 2 elementos `photos` por pantalla y 1 en la pantalla de alarma; `fit` `cover|contain|fill` (por defecto `cover`), `zoom` 1 a 3 (por defecto 1), `pos_x`/`pos_y` enteros 0 a 100 (por defecto 50).
- Deslizar: izquierda a derecha = `Native.swipe("right")` = Alarmas; derecha a izquierda = `Native.swipe("left")` = Ajustes; vertical cambia de pantalla del perfil; umbral 60 px y el eje dominante debe superar 1,5 veces al otro.
- No tocar el diseño ni las fotos reales del usuario en el reloj al verificar (usar solo temas/fotos de prueba propios con vista previa `/design/preview`, nunca guardar sobre su diseño).
- Comandos de prueba: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest`; `node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js`; `cd homelab/friday-panel && .venv/bin/python -m pytest -q`; `cd hermes-skill && pytest -q`.

## Review Focus

- Carrusel con todas las fotos borradas o lista vacía en tiempo de ejecución: no debe fallar, se ve el color del tema (`Core.nextPhotoIndex` con 0 elementos devuelve -1).
- Diseño guardado antes de 3c (sin `fit/zoom/pos_*`, `bg.image` con `dim`) sigue siendo válido y se ve igual.
- Imagen enorme o corrupta subida al reloj: rechazo con mensaje claro, sin guardar nada ni agotar memoria (muestreo `inSampleSize`, tope de píxeles).
- Arrastrar un elemento a una casilla ocupada o fuera de la rejilla: vuelve a su sitio con aviso; el movimiento se limita a la rejilla (`Core.snapMove`).
- Gesto diagonal o corto (no supera el umbral o no domina un eje): no abre nada ni cambia de pantalla; un toque sigue siendo toque.
- Más de 2 elementos `photos` en una pantalla (o 2 en la de alarma): rechazo claro del diseño.

---

### Task 1: Esquema — ajuste de foto y elemento `photos`

**Files:**
- Modify: `app-android/src/main/java/com/jarvis/clock/design/DesignSchema.kt`
- Test: `app-android/src/test/java/com/jarvis/clock/DesignSchemaTest.kt`

**Interfaces:**
- Consumes: nada de tareas anteriores. Helpers privados existentes de `DesignSchema`: `Ctx.err`, `unknownKeys`, `int`, `oneOf`, `IMAGE`, `ELEMENT_PROPS`, `validateElements(c, arr, path, rows)`.
- Produces: `validate` acepta `fit`, `zoom`, `pos_x`, `pos_y` en `themes.<id>.bg` (solo `type: "image"`) y el tipo de elemento `photos` con `images`, `interval_s`, `transition`, `order`, `fit`, `zoom`, `pos_x`, `pos_y`. `validateElements` pasa a tomar `maxPhotos: Int` (2 en pantallas, 1 en `alarm_screen`).

- [ ] **Step 1: Escribir las pruebas que fallan**

Añadir al final de la clase `DesignSchemaTest` (usan los helpers `base()`, `invalid()` y `elements()` ya definidos al inicio de la clase):

```kotlin
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

    @Test fun `elemento photos valido con valores por defecto`() {
        val d = base()
        elements(d, "noche").put(photos("fotos", 0, 5, 2, 1)) // hueco libre a la derecha de la alarma? no: ver nota
        // la fila 5 de "noche" está ocupada por la alarma (w=8): se coloca en una pantalla nueva
    }
```

**Nota para el implementador:** el último test de arriba es solo un esqueleto de cómo montar el elemento; sustitúyelo por estos dos, que sí son completos (crean una pantalla propia con los elementos):

```kotlin
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
```

Borrar del archivo el test esqueleto `elemento photos valido con valores por defecto` (no debe quedar en el commit).

- [ ] **Step 2: Ejecutar para ver que fallan**

Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.jarvis.clock.DesignSchemaTest'`
Expected: FAIL (campos desconocidos / tipo desconocido).

- [ ] **Step 3: Implementar**

En `DesignSchema.kt`:

1. Constantes junto a `ALIGNS`:
```kotlin
    private val FITS = setOf("cover", "contain", "fill")
    private val PHOTO_FIT_KEYS = listOf("fit", "zoom", "pos_x", "pos_y")
```
2. En `ELEMENT_PROPS` añadir:
```kotlin
        "photos" to setOf("images", "interval_s", "transition", "order", "fit", "zoom", "pos_x", "pos_y"),
```
3. En `validateTheme`, la lista de claves permitidas de `bg` pasa a `setOf("type", "color", "from", "to", "angle", "image", "dim", "fit", "zoom", "pos_x", "pos_y")`. En la rama `"image"` añadir `photoFit(c, bg, p)` tras la validación de `dim`/`color`. Tras el `when (bg.opt("type"))`, añadir:
```kotlin
        if (bg.opt("type") != "image") for (k in PHOTO_FIT_KEYS) if (bg.has(k)) c.err("$p.$k", "solo se usa con fondo de imagen")
```
4. Función nueva (junto a las ayudas de validación):
```kotlin
    private fun photoFit(c: Ctx, o: JSONObject, path: String) {
        oneOf(c, o, "fit", path, FITS)
        if (o.has("zoom")) {
            val z = (o.opt("zoom") as? Number)?.toDouble()
            if (z == null || z < 1.0 || z > 3.0) c.err("$path.zoom", "debe ser un número entre 1 y 3")
        }
        if (o.has("pos_x")) int(c, o, "pos_x", path, 0, 100)
        if (o.has("pos_y")) int(c, o, "pos_y", path, 0, 100)
    }
```
5. `validateElements(c, arr, path, rows, maxPhotos: Int)`: las dos llamadas existentes pasan `2` (pantallas) y `1` (alarma). Dentro, contar `photos` (`var photoCount = 0`; incrementar cuando `type == "photos"`) y, tras el bucle de elementos y antes de los solapes, `if (photoCount > maxPhotos) c.err(path, "máximo $maxPhotos elementos de fotos")`.
6. En `validateProps` añadir la rama:
```kotlin
            "photos" -> {
                val imgs = e.optJSONArray("images")
                if (imgs == null || imgs.length() !in 1..20) c.err("$p.images", "debe ser una lista de 1 a 20 fotos")
                else for (k in 0 until imgs.length()) {
                    val s = imgs.opt(k)
                    if (s !is String || !IMAGE.matches(s)) c.err("$p.images[$k]", "nombre de imagen no válido (jpg, png o webp)")
                }
                if (e.has("interval_s")) int(c, e, "interval_s", p, 3, 600)
                oneOf(c, e, "transition", p, setOf("fade", "none"))
                oneOf(c, e, "order", p, setOf("sequential", "random"))
                photoFit(c, e, p)
            }
```

- [ ] **Step 4: Ejecutar y ver que pasan**

Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest`
Expected: PASS (todas las pruebas, incluidas las antiguas de `DesignSchemaTest`).

- [ ] **Step 5: Commit**

```bash
git add app-android/src/main/java/com/jarvis/clock/design/DesignSchema.kt app-android/src/test/java/com/jarvis/clock/DesignSchemaTest.kt
git commit -m "Esquema: ajuste de foto (fit, zoom, posición) y elemento photos"
```

---

### Task 2: Biblioteca de fotos — reducir al guardar, límites nuevos

**Files:**
- Modify: `app-android/src/main/java/com/jarvis/clock/design/Backgrounds.kt`
- Test: `app-android/src/test/java/com/jarvis/clock/BackgroundsTest.kt`

**Interfaces:**
- Consumes: nada de tareas anteriores.
- Produces: `Backgrounds.MAX_BYTES = 8 * 1024 * 1024`; `Backgrounds.MAX_FILES = 60`; `Backgrounds.TARGET_SIDE = 1600`; `Backgrounds.MAX_PIXELS = 100_000_000L`; `Backgrounds.sampleSize(w: Int, h: Int, target: Int = TARGET_SIDE): Int` (potencia de 2 ≥ 1 mayor tal que el lado largo muestreado sigue siendo ≥ `target`); `Backgrounds.targetSize(w: Int, h: Int, target: Int = TARGET_SIDE): Pair<Int, Int>` (no amplía; reduce conservando proporción, mínimo 1); `Backgrounds.save(ctx, rawName, bytes): String` mantiene su firma.

- [ ] **Step 1: Escribir las pruebas que fallan** (añadir a `BackgroundsTest`):

```kotlin
    @Test fun `sampleSize elige la mayor potencia de 2 que no baja del objetivo`() {
        assertEquals(1, Backgrounds.sampleSize(1600, 900))
        assertEquals(1, Backgrounds.sampleSize(3199, 2000))
        assertEquals(2, Backgrounds.sampleSize(3200, 2000))
        assertEquals(4, Backgrounds.sampleSize(6400, 4800))
        assertEquals(1, Backgrounds.sampleSize(500, 300))
        assertEquals(8, Backgrounds.sampleSize(12800, 3000))
    }

    @Test fun `targetSize reduce conservando proporcion y nunca amplia`() {
        assertEquals(1600 to 1200, Backgrounds.targetSize(4000, 3000))
        assertEquals(900 to 1600, Backgrounds.targetSize(2250, 4000))
        assertEquals(800 to 480, Backgrounds.targetSize(800, 480))
        assertEquals(1600 to 1, Backgrounds.targetSize(20000, 5))
    }

    @Test fun `limites nuevos`() {
        assertEquals(8 * 1024 * 1024, Backgrounds.MAX_BYTES)
        assertEquals(60, Backgrounds.MAX_FILES)
    }
```

- [ ] **Step 2: Ejecutar para ver que fallan**

Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.jarvis.clock.BackgroundsTest'`
Expected: FAIL (símbolos inexistentes).

- [ ] **Step 3: Implementar**

En `Backgrounds.kt`:

1. Constantes (sustituyendo `MAX_BYTES` y `MAX_SIDE`):
```kotlin
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_FILES = 60
    const val TARGET_SIDE = 1600
    const val MAX_PIXELS = 100_000_000L
```
2. Funciones puras:
```kotlin
    /** Potencia de 2 para `inSampleSize`: la mayor que deja el lado largo en `target` o más. */
    fun sampleSize(w: Int, h: Int, target: Int = TARGET_SIDE): Int {
        var s = 1
        val side = maxOf(w, h)
        while (side / (s * 2) >= target) s *= 2
        return s
    }

    /** Tamaño final: lado largo a `target` como máximo, misma proporción, nunca amplía. */
    fun targetSize(w: Int, h: Int, target: Int = TARGET_SIDE): Pair<Int, Int> {
        val side = maxOf(w, h)
        if (side <= target) return w to h
        val k = target.toDouble() / side
        return maxOf(1, Math.round(w * k).toInt()) to maxOf(1, Math.round(h * k).toInt())
    }
```
3. Sustituir `save` por esta versión (mismo contrato de error: `IllegalArgumentException` con mensaje en español; escritura atómica con archivo temporal):
```kotlin
    fun save(ctx: Context, rawName: String, bytes: ByteArray): String {
        val name = sanitizeName(rawName) ?: throw IllegalArgumentException("nombre no válido: usa jpg, png o webp")
        require(bytes.isNotEmpty()) { "archivo vacío" }
        require(bytes.size <= MAX_BYTES) { "la imagen supera 8 MB" }
        val dir = dir(ctx)
        val existing = File(dir, name).isFile
        val count = dir.listFiles()?.count { !it.name.startsWith(".") } ?: 0
        require(existing || count < MAX_FILES) { "la biblioteca está llena ($MAX_FILES fotos): borra alguna" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        require(w > 0 && h > 0) { "el reloj no puede leer esa imagen" }
        require(w.toLong() * h <= MAX_PIXELS) { "la imagen es demasiado grande" }
        val tmp = File(dir, ".$name.tmp")
        try {
            if (maxOf(w, h) <= TARGET_SIDE) {
                tmp.writeBytes(bytes)
            } else {
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(w, h) }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    ?: throw IllegalArgumentException("el reloj no puede leer esa imagen")
                val (tw, th) = targetSize(w, h)
                val scaled = if (decoded.width == tw && decoded.height == th) decoded
                    else Bitmap.createScaledBitmap(decoded, tw, th, true)
                if (scaled !== decoded) decoded.recycle()
                val (format, quality) = when (name.substringAfterLast('.')) {
                    "png" -> Bitmap.CompressFormat.PNG to 100
                    "webp" -> @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP to 90
                    else -> Bitmap.CompressFormat.JPEG to 85
                }
                tmp.outputStream().use { scaled.compress(format, quality, it) }
                scaled.recycle()
            }
            if (!tmp.renameTo(File(dir, name))) throw IllegalArgumentException("no se pudo guardar la imagen")
            return name
        } finally {
            tmp.delete()
        }
    }
```
Añadir `import android.graphics.Bitmap`. (Nota: `Bitmap.CompressFormat.WEBP` está en desuso desde API 30 pero es la única opción con minSdk 26.)

- [ ] **Step 4: Ejecutar y ver que pasan**

Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug`
Expected: PASS y la APK compila.

- [ ] **Step 5: Commit**

```bash
git add app-android/src/main/java/com/jarvis/clock/design/Backgrounds.kt app-android/src/test/java/com/jarvis/clock/BackgroundsTest.kt
git commit -m "Fotos: reducir a 1600 px al guardar, subir el límite a 8 MB y 60 fotos"
```

---

### Task 3: Lógica pura del motor — foto, carrusel, gestos y mover

**Files:**
- Modify: `app-android/src/main/assets/face/face-core.js`
- Test: `app-android/face-tests/core.test.js`

**Interfaces:**
- Consumes: nada.
- Produces (todo en `FaceCore`):
  - `photoStyle(spec) -> { size: string, position: string, transform: string, origin: string }` con `spec = {fit, zoom, pos_x, pos_y}` (todo opcional; por defecto `cover`, 1, 50, 50). `size`: `cover`, `contain` o `100% 100%`; `position`/`origin`: `"<x>% <y>%"`; `transform`: `"none"` si zoom 1, si no `"scale(<zoom>)"`.
  - `nextPhotoIndex(count, current, order, rnd) -> number`: `-1` si `count <= 0`; con `count === 1` siempre `0`; `sequential` = `(current + 1) % count` (si `current < 0`, `0`); `random` = índice aleatorio distinto de `current` (usa `rnd()` en [0,1)).
  - `classifySwipe(dx, dy) -> "left"|"right"|"up"|"down"|null`: `"right"` = el dedo fue de izquierda a derecha (`dx > 0`), `"up"` = `dy < 0`; devuelve el eje dominante solo si supera 60 px y 1,5 veces al otro eje.
  - `snapMove(el, dxCells, dyCells, rows) -> {x, y}` limitado a `0..COLS-el.w` y `0..rows-el.h`.
  - `bgCss(bg, base)` para `type: "image"` devuelve solo el color de respaldo (la foto se dibuja en una capa aparte, ver Task 4).

- [ ] **Step 1: Escribir las pruebas que fallan** (añadir a `core.test.js`; el archivo ya importa `C = require("../src/main/assets/face/face-core.js")` y `assert`/`test`; sigue el estilo del archivo) y **actualizar** el test existente `bgCss: color, degradado e imagen con color de respaldo` para que, en el caso `image`, espere `"#102030"` (color de respaldo) cuando hay `color` y `"#000000"` cuando no; elimina las aserciones sobre `url(`/`linear-gradient` de la imagen:

```js
test("photoStyle: valores por defecto y ajustes", () => {
  assert.deepEqual(C.photoStyle({}), { size: "cover", position: "50% 50%", transform: "none", origin: "50% 50%" });
  assert.deepEqual(C.photoStyle(undefined), { size: "cover", position: "50% 50%", transform: "none", origin: "50% 50%" });
  assert.deepEqual(C.photoStyle({ fit: "contain", zoom: 1.5, pos_x: 20, pos_y: 80 }),
    { size: "contain", position: "20% 80%", transform: "scale(1.5)", origin: "20% 80%" });
  assert.equal(C.photoStyle({ fit: "fill" }).size, "100% 100%");
  assert.equal(C.photoStyle({ fit: "raro" }).size, "cover");
  assert.equal(C.photoStyle({ zoom: 9, pos_x: -5, pos_y: 500 }).transform, "scale(3)");
  assert.equal(C.photoStyle({ zoom: 9, pos_x: -5, pos_y: 500 }).position, "0% 100%");
});

test("nextPhotoIndex: secuencial, aleatorio sin repetir y casos vacios", () => {
  assert.equal(C.nextPhotoIndex(0, -1, "sequential", Math.random), -1);
  assert.equal(C.nextPhotoIndex(1, 0, "random", Math.random), 0);
  assert.equal(C.nextPhotoIndex(3, -1, "sequential"), 0);
  assert.equal(C.nextPhotoIndex(3, 0, "sequential"), 1);
  assert.equal(C.nextPhotoIndex(3, 2, "sequential"), 0);
  for (let i = 0; i < 50; i++) {
    const n = C.nextPhotoIndex(4, 2, "random", Math.random);
    assert.ok(n >= 0 && n < 4 && n !== 2);
  }
  assert.equal(C.nextPhotoIndex(3, 1, "random", () => 0), 0);
  assert.equal(C.nextPhotoIndex(3, 1, "random", () => 0.999), 2);
  assert.equal(C.nextPhotoIndex(3, 1, "random", () => 0.5), 2);
});

test("classifySwipe: eje dominante, sentido y umbrales", () => {
  assert.equal(C.classifySwipe(120, 10), "right");
  assert.equal(C.classifySwipe(-120, 10), "left");
  assert.equal(C.classifySwipe(10, -120), "up");
  assert.equal(C.classifySwipe(10, 120), "down");
  assert.equal(C.classifySwipe(100, 100), null);   // diagonal
  assert.equal(C.classifySwipe(100, 70), null);    // no domina 1,5x
  assert.equal(C.classifySwipe(40, 0), null);      // corto
  assert.equal(C.classifySwipe(0, 0), null);
  assert.equal(C.classifySwipe(61, 40), "right");
});

test("snapMove: se limita a la rejilla", () => {
  const e = { x: 2, y: 1, w: 3, h: 2 };
  assert.deepEqual(C.snapMove(e, 1, 1, 6), { x: 3, y: 2 });
  assert.deepEqual(C.snapMove(e, 99, 99, 6), { x: 5, y: 4 });
  assert.deepEqual(C.snapMove(e, -99, -99, 6), { x: 0, y: 0 });
  assert.deepEqual(C.snapMove(e, 0, 0, 4), { x: 2, y: 1 });
});
```

- [ ] **Step 2: Ejecutar para ver que fallan**

Run: `node --test app-android/face-tests/*.test.js`
Expected: FAIL (funciones inexistentes; `bgCss` de imagen cambió).

- [ ] **Step 3: Implementar** en `face-core.js` (antes del `return`; añadir las cuatro funciones y exportarlas; sustituir la rama `image` de `bgCss` por `return fallback;`, borrando el uso de `dim`/`url` ahí):

```js
  function clamp(n, lo, hi) { return Math.max(lo, Math.min(hi, n)); }

  function photoStyle(spec) {
    spec = spec || {};
    var fit = spec.fit === "contain" ? "contain" : spec.fit === "fill" ? "100% 100%" : "cover";
    var zoom = clamp(typeof spec.zoom === "number" ? spec.zoom : 1, 1, 3);
    var px = clamp(typeof spec.pos_x === "number" ? spec.pos_x : 50, 0, 100);
    var py = clamp(typeof spec.pos_y === "number" ? spec.pos_y : 50, 0, 100);
    var pos = px + "% " + py + "%";
    return { size: fit, position: pos, transform: zoom === 1 ? "none" : "scale(" + zoom + ")", origin: pos };
  }

  function nextPhotoIndex(count, current, order, rnd) {
    if (!(count > 0)) return -1;
    if (count === 1) return 0;
    if (order === "random") {
      var r = Math.floor((rnd ? rnd() : Math.random()) * (count - 1));
      if (r >= current && current >= 0) r += 1;
      return Math.min(r, count - 1);
    }
    return current < 0 ? 0 : (current + 1) % count;
  }

  function classifySwipe(dx, dy) {
    var ax = Math.abs(dx), ay = Math.abs(dy);
    if (ax > 60 && ax > ay * 1.5) return dx > 0 ? "right" : "left";
    if (ay > 60 && ay > ax * 1.5) return dy > 0 ? "down" : "up";
    return null;
  }

  function snapMove(el, dxCells, dyCells, rows) {
    return {
      x: clamp(el.x + dxCells, 0, COLS - el.w),
      y: clamp(el.y + dyCells, 0, rows - el.h),
    };
  }
```
y exportar: `photoStyle: photoStyle, nextPhotoIndex: nextPhotoIndex, classifySwipe: classifySwipe, snapMove: snapMove,`.

Comprobar a mano los tres casos aleatorios del test: `count=3, current=1, rnd()=0` → `r=0` (0 < 1) → `0`; `rnd()=0.999` → `floor(0.999*2)=1` → `1 >= 1` → `2`; `rnd()=0.5` → `floor(1)=1` → `2`. Coinciden con las aserciones.

- [ ] **Step 4: Ejecutar y ver que pasan**

Run: `node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app-android/src/main/assets/face/face-core.js app-android/face-tests/core.test.js
git commit -m "Motor: lógica pura de foto, carrusel, gestos y mover elementos"
```

---

### Task 4: Motor — capa de foto, carrusel, deslizar y mover en edición

**Files:**
- Modify: `app-android/src/main/assets/face/face.js`
- Modify: `app-android/src/main/assets/face/face.css`
- Sync: `homelab/friday-panel/static/face/` (vía `bash tools/sync-face.sh`)

**Interfaces:**
- Consumes: `Core.photoStyle`, `Core.nextPhotoIndex`, `Core.classifySwipe`, `Core.snapMove`, `Core.bgCss` (de la Task 3); esquema de `photos` (Task 1).
- Produces: el motor dibuja el fondo de imagen en una capa (`.bglayer`) con ajuste y oscurecido; dibuja elementos `photos` (carrusel); `Native.swipe("left"|"right")` en el reloj; en modo edición envía `postMessage({type:"face-move", id, x, y})` al soltar tras arrastrar (y `face-select` solo si no se movió).

- [ ] **Step 1: CSS** — añadir al final de `face.css`:

```css
.bglayer { position: absolute; left: 0; top: 0; right: 0; bottom: 0; overflow: hidden; }
.bgdim { position: absolute; left: 0; top: 0; right: 0; bottom: 0; }
.pl { position: absolute; left: 0; top: 0; right: 0; bottom: 0; background-repeat: no-repeat; opacity: 0; }
.pl.fade { transition: opacity 0.6s; }
.pl.on { opacity: 1; }
.photos { position: absolute; left: 0; top: 0; right: 0; bottom: 0; overflow: hidden; }
.el.moving { opacity: 0.55; outline: 2px solid #4fc3f7; }
```

- [ ] **Step 2: Capa de foto de fondo** en `face.js`.

Añadir helpers (junto a `el`/`svg`):

```js
  function photoUrl(name) { return (S.opts.bgBase || "/bg/") + encodeURIComponent(name); }

  function stylePhoto(node, url, spec) {
    var st = Core.photoStyle(spec);
    node.style.backgroundImage = "url(\"" + url + "\")";
    node.style.backgroundSize = st.size;
    node.style.backgroundPosition = st.position;
    node.style.transform = st.transform;
    node.style.transformOrigin = st.origin;
  }
```

Reescribir `applyTheme` para que, además de lo existente, quite la capa anterior y, si `theme.bg.type === "image" && theme.bg.image`, cree la capa:

```js
    var old = rootEl.querySelector(".bglayer");
    if (old) rootEl.removeChild(old);
    var bg = theme.bg || {};
    if (bg.type === "image" && bg.image) {
      var layer = el("div", "bglayer");
      var pic = el("div", "pl on");
      stylePhoto(pic, photoUrl(bg.image), bg);
      layer.appendChild(pic);
      var dim = el("div", "bgdim");
      dim.style.background = "rgba(0,0,0," + ((bg.dim || 0) / 100) + ")";
      layer.appendChild(dim);
      rootEl.insertBefore(layer, contentEl);
    }
```
(`Core.bgCss` ya devuelve solo el color de respaldo para imágenes: no cambiar la línea `rootEl.style.background = Core.bgCss(...)`.) `S.opts` debe estar asignado antes de `applyTheme` (ya lo está en `render`).

- [ ] **Step 3: Elemento `photos`** — añadir el temporizador y el constructor.

En `S` añadir `timers: []`. En `draw()` antes de `contentEl.textContent = ""` añadir:
```js
    for (var t = 0; t < S.timers.length; t++) clearInterval(S.timers[t]);
    S.timers = [];
```
En `builders` añadir:

```js
    photos: function (e, box, r) {
      var names = (e.images || []).slice();
      var wrap = el("div", "photos");
      box.appendChild(wrap);
      var fade = e.transition !== "none";
      var layers = [el("div", "pl" + (fade ? " fade" : "")), el("div", "pl" + (fade ? " fade" : ""))];
      wrap.appendChild(layers[0]); wrap.appendChild(layers[1]);
      var cur = -1, top = 0, busy = false;
      var order = e.order === "random" ? "random" : "sequential";
      function show(i, tries) {
        if (busy) return;
        if (i < 0 || !names.length) return;
        busy = true;
        var img = new Image();
        img.onload = function () {
          var next = layers[1 - top];
          stylePhoto(next, photoUrl(names[i]), e);
          next.classList.add("on");
          layers[top].classList.remove("on");
          top = 1 - top;
          cur = i;
          busy = false;
        };
        img.onerror = function () {
          busy = false;
          if (tries < names.length) show(Core.nextPhotoIndex(names.length, i, order), tries + 1);
        };
        img.src = photoUrl(names[i]);
      }
      show(Core.nextPhotoIndex(names.length, -1, "sequential"), 1);
      var iv = Math.max(3, Math.min(600, e.interval_s || 10)) * 1000;
      if (names.length > 1) {
        S.timers.push(setInterval(function () { show(Core.nextPhotoIndex(names.length, cur, order), 1); }, iv));
      }
    },
```
(La imagen que no existe da `onerror` y se salta; si no queda ninguna, la caja queda vacía y se ve el color del tema. Con `transition: "none"` no hay clase `fade`, así que el cambio es instantáneo.)

- [ ] **Step 4: Deslizar** — en `gestureUp` sustituir la línea del deslizamiento horizontal por:

```js
    var swipe = Core.classifySwipe(dx, dy);
    if (swipe === "left" || swipe === "right") { native("swipe", swipe); return; }
    if (swipe === "up" || swipe === "down") { go(swipe === "up" ? 1 : -1); return; }
```
(`native("swipe", ...)` ya convierte el argumento a texto y no falla si no existe el puente, p. ej. en la vista previa.)

- [ ] **Step 5: Mover en modo edición** — sustituir `editDown`, `editMove`, `editUp` y el estado `drag` por esta versión (conserva el redimensionado con `.handle`; añade el arrastre del elemento):

```js
  var drag = null;      // redimensionando: { id, box, el, w, h }
  var moving = null;    // moviendo: { id, box, el, x0, y0, cell, dx, dy, moved }

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
    if (box) {
      var item = findElement(box.getAttribute("data-id"));
      if (!item) return;
      var b = rootEl.getBoundingClientRect();
      moving = { id: item.id, box: box, el: item, x0: ev.clientX, y0: ev.clientY,
        cw: b.width / COLS, ch: b.height / S.rows, dx: 0, dy: 0, moved: false };
      try { rootEl.setPointerCapture(ev.pointerId); } catch (e2) { /* ignorar */ }
      ev.preventDefault();
      return;
    }
    var c = cellAt(ev.clientX, ev.clientY);
    post({ type: "face-empty", x: c.x, y: c.y });
  }
  function editMove(ev) {
    if (moving) {
      var px = ev.clientX - moving.x0, py = ev.clientY - moving.y0;
      if (!moving.moved && Math.abs(px) < 8 && Math.abs(py) < 8) return;
      moving.moved = true;
      var snap = Core.snapMove(moving.el, Math.round(px / moving.cw), Math.round(py / moving.ch), S.rows);
      moving.dx = snap.x; moving.dy = snap.y;
      var r = Core.cellRect({ x: snap.x, y: snap.y, w: moving.el.w, h: moving.el.h }, S.W, S.H, S.rows);
      moving.box.classList.add("moving");
      moving.box.style.left = r.left + "px";
      moving.box.style.top = r.top + "px";
      return;
    }
    if (!drag) return;
    var c = cellAt(ev.clientX, ev.clientY);
    drag.w = Math.max(1, Math.min(COLS - drag.el.x, c.x - drag.el.x + 1));
    drag.h = Math.max(1, Math.min(S.rows - drag.el.y, c.y - drag.el.y + 1));
    var rr = Core.cellRect({ x: drag.el.x, y: drag.el.y, w: drag.w, h: drag.h }, S.W, S.H, S.rows);
    drag.box.style.width = rr.width + "px";
    drag.box.style.height = rr.height + "px";
  }
  function editUp() {
    if (moving) {
      var m = moving;
      moving = null;
      if (!m.moved) { post({ type: "face-select", id: m.id }); return; }
      post({ type: "face-move", id: m.id, x: m.dx, y: m.dy });
      return;
    }
    if (!drag) return;
    var d = drag;
    drag = null;
    post({ type: "face-resize", id: d.id, w: d.w, h: d.h });
  }
```
Y en el `pointercancel` de `init`: `g.down = false; drag = null; moving = null; clearTimeout(g.longT);`. (Si el movimiento se cancela, `S.design` se vuelve a dibujar en el siguiente `face-render` del panel; si no, `draw()` en el siguiente `resize`.)

- [ ] **Step 6: Sincronizar y probar**

Run: `bash tools/sync-face.sh && node --check app-android/src/main/assets/face/face.js && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && cd homelab/friday-panel && .venv/bin/python -m pytest -q`
Expected: PASS (la prueba de sincronización `test_face_sync.py` pasa porque ya se copió).

- [ ] **Step 7: Comprobación visual en el navegador** (sin tocar el reloj): arrancar el panel con el mock de reloj, o abrir `homelab/friday-panel/static/face/index.html?preview=1` con el script CDP del scratchpad (`cdp.js`) enviando un `face-render` con un tema de foto (cualquier jpg local servido en `/bg/`), un elemento `photos` con 2 fotos de prueba y `opts.edit=true`; comprobar con una captura que el fondo ajustado y el carrusel se ven y que, simulando punteros, arrastrar un elemento envía `face-move`. Registrar el resultado en el informe de la tarea.

- [ ] **Step 8: Commit**

```bash
git add app-android/src/main/assets/face homelab/friday-panel/static/face
git commit -m "Motor: capa de foto ajustable, carrusel, deslizar y mover elementos"
```

---

### Task 5: Reloj — puente `swipe` y apertura de Alarmas y Ajustes

**Files:**
- Modify: `app-android/src/main/java/com/jarvis/clock/ui/FaceHost.kt`
- Modify: `app-android/src/main/java/com/jarvis/clock/MainActivity.kt`

**Interfaces:**
- Consumes: el motor llama a `Native.swipe("left"|"right")` (Task 4).
- Produces: `FaceHost.Listener.onSwipe(direction: String)`; `MainActivity` abre `AlarmsActivity` con `"right"` y `SettingsActivity` con `"left"`, también desde la carátula nativa de respaldo con `onFling`. `AlarmActivity` no se toca (no tiene `Listener` de swipe: su `FaceHost` debe seguir compilando; ver Step 2).

- [ ] **Step 1: FaceHost** — añadir a `Listener`: `fun onSwipe(direction: String)`; y al `Bridge`:
```kotlin
        @JavascriptInterface fun swipe(direction: String) { main.post { listener.onSwipe(direction) } }
```

- [ ] **Step 2: Implementaciones del `Listener`.** Buscar con `grep -rn "FaceHost.Listener\|FaceHost.create" app-android/src/main` todas las implementaciones. En `AlarmActivity` (y en cualquier otra que no sea `MainActivity`) añadir `override fun onSwipe(direction: String) {}` (el deslizar no hace nada ahí).

- [ ] **Step 3: MainActivity.** En `faceListener` añadir:
```kotlin
        override fun onSwipe(direction: String) { handleSwipe(direction) }
```
y el método:
```kotlin
    /** Izquierda a derecha abre Alarmas; derecha a izquierda abre Ajustes. */
    private fun handleSwipe(direction: String) {
        when (direction) {
            "right" -> openAlarms()
            "left" -> startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
```
En el `GestureDetector` de la carátula nativa (`buildUi`, bloque "Gestos") añadir:
```kotlin
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (Math.abs(dx) < dp(60) || Math.abs(dx) < Math.abs(dy) * 1.5f) return false
                handleSwipe(if (dx > 0) "right" else "left")
                return true
            }
```
(`dp(...)` ya existe en `MainActivity`; si devuelve `Int`, comparar con `.toFloat()`.)

- [ ] **Step 4: Compilar y probar**

Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug`
Expected: PASS y APK generada.

- [ ] **Step 5: Commit**

```bash
git add app-android/src/main/java
git commit -m "Reloj: deslizar la carátula abre Alarmas (izquierda a derecha) o Ajustes (derecha a izquierda)"
```

---

### Task 6: Panel (servidor y lógica del editor) — límite de 8 MB, tipo `photos`, tamaño de reducción

**Files:**
- Modify: `homelab/friday-panel/app.py`
- Modify: `homelab/friday-panel/static/editor-core.js`
- Test: `homelab/friday-panel/tests/test_design.py`
- Test: `homelab/friday-panel/tests-js/editor-core.test.js`

**Interfaces:**
- Consumes: nada.
- Produces: `MAX_BG_BYTES = 8 * 1024 * 1024` y mensaje `"La imagen supera 8 MB"`; `EditorCore.TYPES.photos` con `label: "Fotos"`, `w: 4`, `h: 3`, `defaults: { images: [], interval_s: 10, transition: "fade", order: "sequential", fit: "cover", zoom: 1, pos_x: 50, pos_y: 50 }` y `fields` con los `kind` nuevos `"photolist"`, `"photofit"` y los estándar `number`/`select` (los `kind` nuevos los pinta la Task 7); `EditorCore.downscaleSize(w, h, maxSide) -> {w, h}` (no amplía; redondeo al entero más cercano, mínimo 1); `EditorCore.PHOTO_SIDE = 1600`.

- [ ] **Step 1: Pruebas que fallan.**

En `tests/test_design.py` sustituir el caso del límite de 2 MB (línea ~52, `content=b"x" * (2 * 1024 * 1024 + 1)`) por 8 MB (`8 * 1024 * 1024 + 1`) y su mensaje esperado, y añadir debajo un caso que acepte una subida de ~3 MB:
```python
    r = client.post("/api/backgrounds", params={"name": "mediana.jpg"}, content=b"\xff\xd8" + b"x" * (3 * 1024 * 1024))
    assert r.status_code == 200
```
(Mira el test existente para el formato exacto de las aserciones y del mock; el mock acepta cualquier contenido.)

En `tests-js/editor-core.test.js` añadir:
```js
test("downscaleSize: reduce el lado largo y no amplia", () => {
  assert.deepEqual(EditorCore.downscaleSize(4000, 3000, 1600), { w: 1600, h: 1200 });
  assert.deepEqual(EditorCore.downscaleSize(3000, 4000, 1600), { w: 1200, h: 1600 });
  assert.deepEqual(EditorCore.downscaleSize(800, 480, 1600), { w: 800, h: 480 });
  assert.deepEqual(EditorCore.downscaleSize(20000, 5, 1600), { w: 1600, h: 1 });
});

test("photos: plantilla por defecto", () => {
  const e = EditorCore.newElement("photos", "galeria", { x: 0, y: 0 });
  assert.equal(e.type, "photos");
  assert.deepEqual(e.images, []);
  assert.equal(e.interval_s, 10);
  assert.equal(e.transition, "fade");
  assert.equal(e.order, "sequential");
  assert.equal(e.fit, "cover");
  assert.equal(e.zoom, 1);
  assert.equal(e.w, 4);
  assert.equal(e.h, 3);
});
```
(Usa el mismo `require`/alias que ya tenga el archivo para `EditorCore`.)

- [ ] **Step 2: Ejecutar para ver que fallan**

Run: `node --test homelab/friday-panel/tests-js/*.test.js && cd homelab/friday-panel && .venv/bin/python -m pytest -q tests/test_design.py`
Expected: FAIL.

- [ ] **Step 3: Implementar.**

`app.py`: `MAX_BG_BYTES = 8 * 1024 * 1024` y cambiar las dos cadenas `"La imagen supera 2 MB"` por `"La imagen supera 8 MB"`.

`editor-core.js`: añadir a `TYPES` (antes de `text` o al final):
```js
    photos: {
      label: "Fotos", w: 4, h: 3,
      defaults: { images: [], interval_s: 10, transition: "fade", order: "sequential", fit: "cover", zoom: 1, pos_x: 50, pos_y: 50 },
      fields: [
        { key: "images", kind: "photolist", label: "Fotos del carrusel" },
        { key: "interval_s", kind: "number", label: "Segundos entre fotos (3–600)", min: 3, max: 600 },
        { key: "transition", kind: "select", label: "Transición", options: [["fade", "Fundido"], ["none", "Corte directo"]] },
        { key: "order", kind: "select", label: "Orden", options: [["sequential", "En orden"], ["random", "Al azar"]] },
        { key: "fit", kind: "photofit", label: "Ajuste de la foto" },
      ],
    },
```
y la función y su export:
```js
  var PHOTO_SIDE = 1600;

  function downscaleSize(w, h, maxSide) {
    var side = Math.max(w, h);
    if (side <= maxSide) return { w: w, h: h };
    var k = maxSide / side;
    return { w: Math.max(1, Math.round(w * k)), h: Math.max(1, Math.round(h * k)) };
  }
```
añadiendo `PHOTO_SIDE: PHOTO_SIDE, downscaleSize: downscaleSize` al objeto devuelto.

- [ ] **Step 4: Ejecutar y ver que pasan**

Run: `node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && cd homelab/friday-panel && .venv/bin/python -m pytest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add homelab/friday-panel/app.py homelab/friday-panel/static/editor-core.js homelab/friday-panel/tests homelab/friday-panel/tests-js
git commit -m "Panel: subida de fotos hasta 8 MB, tipo de elemento Fotos y tamaño de reducción"
```

---

### Task 7: Panel (interfaz) — reducir al subir, ajustar foto, carrusel y mover

**Files:**
- Modify: `homelab/friday-panel/static/pantallas.js`
- Modify: `homelab/friday-panel/static/styles.css` (solo estilos nuevos mínimos para miniaturas y deslizadores)

**Interfaces:**
- Consumes: `EditorCore.downscaleSize`, `EditorCore.PHOTO_SIDE`, `EditorCore.TYPES.photos` y los `kind` `"photolist"`/`"photofit"` (Task 6); mensaje `face-move` del motor (Task 4); ayudas ya existentes en el panel (`h`, `btn`, `toast`, `api`, `errText`, `labeled`, `selectField`, `numberField`, `boolField`, `colorField`, `touch`, `paintSub`, `pushPreview`, `dz`, `form`, `currentElements`, `currentRows`, `confirmInPage`).
- Produces: `uploadPhoto(file) -> Promise<string|null>` (reduce y sube, devuelve el nombre guardado o `null`); `photoFitBlock(spec)` (bloque de ajuste reutilizable); `moveElement(id, x, y)`; el selector de foto del fondo usa `uploadPhoto` y el bloque de ajuste; `typedField` pinta `photolist` y `photofit`.

- [ ] **Step 1: Reducir al subir.** Añadir en `pantallas.js` (cerca de `imagePicker`):

```js
function loadBitmap(file) {
  if (window.createImageBitmap) return createImageBitmap(file);
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => { URL.revokeObjectURL(url); resolve(img); };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error("no se pudo leer la imagen")); };
    img.src = url;
  });
}

function canvasBlob(canvas, type, quality) {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}

const BLOB_EXT = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" };
const MAX_UPLOAD = 8 * 1024 * 1024;

/* Reduce la foto en el navegador (lado largo 1600) y la sube. Si no se puede decodificar,
   la manda tal cual si pesa menos de 8 MB. Devuelve el nombre guardado en el reloj o null. */
async function uploadPhoto(file) {
  toast("Preparando la foto…");
  let blob = file;
  let name = file.name;
  try {
    const bmp = await loadBitmap(file);
    const size = EditorCore.downscaleSize(bmp.width, bmp.height, EditorCore.PHOTO_SIDE);
    if (size.w !== bmp.width || size.h !== bmp.height || file.size > MAX_UPLOAD) {
      const canvas = document.createElement("canvas");
      canvas.width = size.w; canvas.height = size.h;
      canvas.getContext("2d").drawImage(bmp, 0, 0, size.w, size.h);
      const want = file.type === "image/png" ? "image/png" : file.type === "image/webp" ? "image/webp" : "image/jpeg";
      let out = await canvasBlob(canvas, want, 0.85);
      if (out && out.size > MAX_UPLOAD && want !== "image/jpeg") out = await canvasBlob(canvas, "image/jpeg", 0.85);
      if (out && BLOB_EXT[out.type]) {
        blob = out;
        name = name.replace(/\.[^.]*$/, "") + "." + BLOB_EXT[out.type];
      }
    }
    if (bmp.close) bmp.close();
  } catch (err) {
    if (file.size > MAX_UPLOAD) { toast("No se pudo leer la imagen y supera 8 MB", true); return null; }
  }
  if (blob.size > MAX_UPLOAD) { toast("La imagen supera 8 MB", true); return null; }
  toast("Subiendo…");
  const r = await api("POST", `/api/backgrounds?name=${encodeURIComponent(name)}`, undefined, blob);
  if (!r.ok) { toast(errText(r), true); return null; }
  toast("Foto subida al reloj");
  await refreshBackgrounds();
  return r.data.name;
}
```
(Sin emojis nuevos en los textos. Si `api(..., body)` espera un `File`, comprobar que acepta `Blob`; si usa `body.size`/`body.type` funciona igual.)

- [ ] **Step 2: Bloque de ajuste y deslizador.**

```js
function rangeField(label, val, min, max, step, onInput) {
  const out = h("span", { class: "muted" }, String(val));
  const input = h("input", { type: "range", min, max, step, value: val,
    oninput: (ev) => { const v = Number(ev.target.value); out.textContent = String(v); onInput(v); } });
  return labeled(label, h("div", { class: "row" }, input, out));
}

/* Encaje, zoom y posición de una foto (fondo de tema o carrusel); `spec` es el objeto del diseño. */
function photoFitBlock(spec) {
  return h("div", { class: "opt" },
    selectField("Encaje", [["cover", "Rellenar (recorta lo que sobra)"], ["contain", "Entera (con bandas)"], ["fill", "Estirar"]],
      spec.fit || "cover", (v) => { spec.fit = v; touch(); }),
    rangeField("Zoom", spec.zoom === undefined ? 1 : spec.zoom, 1, 3, 0.1, (v) => { spec.zoom = v; touch(); }),
    rangeField("Posición horizontal", spec.pos_x === undefined ? 50 : spec.pos_x, 0, 100, 1, (v) => { spec.pos_x = v; touch(); }),
    rangeField("Posición vertical", spec.pos_y === undefined ? 50 : spec.pos_y, 0, 100, 1, (v) => { spec.pos_y = v; touch(); }));
}
```
(`touch()` repinta la vista previa en vivo; ahí no se llama a `paintSub` para no perder el foco del deslizador.)

- [ ] **Step 3: Fondo de tema.** En `imagePicker(bg)`: quitar el control `f.size > 2 * 1024 * 1024` y la subida manual; el `onchange` del `<input type=file>` pasa a:
```js
    onchange: async (ev) => {
      const input = ev.target;
      const f = input.files[0];
      input.value = "";
      if (!f) return;
      const name = await uploadPhoto(f);
      if (!name) return;
      bg.image = name;
      touch(); paintSub();
    }
```
y al final del `h("div", { class: "opt" }, ...)` que devuelve `imagePicker` añadir `photoFitBlock(bg)` cuando `bg.image` esté definido (justo después de la lista de fotos con su botón Borrar). En la rama `"image"` del selector de tipo de fondo no hace falta cambiar nada más (los campos de ajuste son opcionales).

- [ ] **Step 4: Elemento Fotos.** En `typedField(e, f)` añadir antes del `return null` final:
```js
  if (f.kind === "photofit") return photoFitBlock(e);
  if (f.kind === "photolist") return photoListField(e, f);
```
y definir:
```js
function photoListField(e, f) {
  const chosen = new Set(e[f.key] || []);
  const known = new Set(dz.backgrounds.map((b) => b.name));
  const file = h("input", { type: "file", accept: "image/jpeg,image/png,image/webp", multiple: true, hidden: true,
    onchange: async (ev) => {
      const input = ev.target;
      const files = Array.from(input.files);
      input.value = "";
      for (const one of files) {
        if ((e.images || []).length >= 20) { toast("Máximo 20 fotos por carrusel", true); break; }
        const name = await uploadPhoto(one);
        if (name && !(e.images || []).includes(name)) e.images = (e.images || []).concat([name]);
      }
      touch(); paintSub();
    } });
  const thumbs = dz.backgrounds.map((b) => {
    const on = chosen.has(b.name);
    return h("label", { class: "thumb" + (on ? " on" : "") },
      h("img", { src: "/api/bg/" + encodeURIComponent(b.name), alt: b.name, loading: "lazy" }),
      h("input", { type: "checkbox", checked: on, onchange: (ev) => {
        const cur = (e.images || []).filter((n) => n !== b.name);
        if (ev.target.checked) {
          if (cur.length >= 20) { toast("Máximo 20 fotos por carrusel", true); ev.target.checked = false; return; }
          cur.push(b.name);
        }
        e.images = cur; touch(); paintSub();
      } }));
  });
  const missing = (e.images || []).filter((n) => !known.has(n));
  return h("div", { class: "opt" },
    h("h3", {}, f.label + " (" + (e.images || []).length + ")"),
    dz.backgrounds.length ? h("div", { class: "thumbs" }, thumbs) : h("div", { class: "muted" }, "No hay fotos en el reloj: sube alguna."),
    missing.length ? h("div", { class: "warn" }, "Estas fotos ya no están en el reloj y se saltarán: " + missing.join(", ")) : null,
    !(e.images || []).length ? h("div", { class: "warn" }, "Elige al menos una foto: sin ellas el diseño no se puede guardar.") : null,
    h("div", { class: "row" }, btn("Subir fotos…", () => file.click(), "small ghost"), file));
}
```
Estilos mínimos en `styles.css` (sin emojis; usar las variables de color que ya existan en el archivo):
```css
.thumbs { display: flex; flex-wrap: wrap; }
.thumb { position: relative; width: 84px; height: 56px; margin: 0 8px 8px 0; border: 2px solid transparent; border-radius: 6px; overflow: hidden; }
.thumb.on { border-color: #4fc3f7; }
.thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.thumb input { position: absolute; right: 4px; top: 4px; }
input[type=range] { width: 100%; }
```
(Nada de `gap` si el CSS del panel evita compatibilidad antigua; el panel corre en navegadores modernos, pero el margen es suficiente.)

Al añadir un elemento `photos` (`addElement`): si `type === "photos"` y `dz.backgrounds.length` precargar `images` con la primera foto de la biblioteca: tras `EditorCore.newElement(...)` añadir `if (type === "photos") list[list.length - 1].images = dz.backgrounds.slice(0, 1).map((b) => b.name);`. Si no hay fotos, el elemento se crea vacío y el aviso de `photoListField` lo indica.

- [ ] **Step 5: Mover.** En el manejador `message` añadir `else if (m.type === "face-move") moveElement(m.id, m.x, m.y);` y la función:
```js
function moveElement(id, x, y) {
  const e = currentElements().find((it) => it.id === id);
  if (!e) return;
  dz.selected = id;
  const cand = { x, y, w: e.w, h: e.h };
  if (EditorCore.inBounds(cand, currentRows()) && !EditorCore.collides(currentElements(), cand, id)) {
    e.x = x; e.y = y; touch();
  } else {
    toast("No cabe ahí: se solaparía", true);
    pushPreview();
  }
  paintSub();
}
```
(`touch()` ya llama a `pushPreview`, por lo que la vista previa se redibuja en la casilla nueva; en el caso rechazado también, y el elemento vuelve a su sitio.)

- [ ] **Step 6: Probar.**

Run: `node --check homelab/friday-panel/static/pantallas.js && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && cd homelab/friday-panel && .venv/bin/python -m pytest -q`
Expected: PASS.

- [ ] **Step 7: Comprobación visual con el mock.** Levantar el panel contra `hermes-skill/mock_clock_server.py` (como hacen los tests de `conftest.py`), abrir la pestaña de diseño con el script CDP del scratchpad y verificar con capturas: subir una foto grande reduce y aparece en la lista; el bloque de ajuste mueve la vista previa; añadir un elemento Fotos y elegir miniaturas; arrastrar un elemento en la vista previa lo mueve y uno a una casilla ocupada vuelve a su sitio con aviso. Registrar en el informe qué se vio.

- [ ] **Step 8: Commit**

```bash
git add homelab/friday-panel/static
git commit -m "Panel: reducir fotos al subir, ajustar foto, carrusel y mover elementos arrastrando"
```

---

### Task 8: Documentación, skill y verificación en el reloj real

**Files:**
- Modify: `README.md` (tabla de la API y sección del panel: límites de fotos, elemento `photos`, gestos)
- Modify: `CLAUDE.md` (solo añadir; el usuario edita este archivo desde otra sesión)
- Modify: `hermes-skill/mi-smart-clock/SKILL.md` (límites de fotos, elemento `photos`, campos de ajuste)
- Modify: `hermes-skill/mock_clock_server.py` solo si valida el esquema o el tamaño de las fotos (`grep -n "2 \* 1024\|2 MB\|photos" hermes-skill/*.py`); si no, no tocar.

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: documentación coherente con los límites finales; el `README.md` explica que deslizar de izquierda a derecha abre Alarmas y de derecha a izquierda Ajustes, y que los cambios de pantalla del perfil se hacen deslizando en vertical.

- [ ] **Step 1: Documentar.** Buscar con `grep -rn "2 MB\|2048\|2 MiB" README.md CLAUDE.md hermes-skill docs/superpowers/specs/2026-10-06-pantallas-temas-design.md homelab` las menciones antiguas del límite y corregirlas (README, SKILL.md; dejar el spec de 3a intacto salvo una nota "ver 3c" si ya hay sección de límites). Añadir al README: elemento `photos` (campos y límites), campos `fit/zoom/pos_x/pos_y`, límites 8 MB / 1600 px / 60 fotos y los gestos. Añadir una línea a `CLAUDE.md` bajo la descripción del motor: "Fotos: el reloj reduce a 1600 px al guardar (≤ 8 MB, ≤ 60); gestos: deslizar de izquierda a derecha abre Alarmas, de derecha a izquierda Ajustes, vertical cambia de pantalla."

- [ ] **Step 2: Pruebas completas.**

Run (todo, como la CI): `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && bash tools/sync-face.sh --check && (cd homelab/friday-panel && .venv/bin/python -m pytest -q) && (cd hermes-skill && pytest -q) && python -m py_compile homelab/friday-panel/app.py`
Expected: todo PASS.

- [ ] **Step 3: Commit de la documentación**

```bash
git add README.md CLAUDE.md hermes-skill
git commit -m "Docs: fotos ajustables, carrusel y gestos de deslizar"
```

- [ ] **Step 4: Verificación en el reloj real** (la hace el controlador, no un subagente; no se guarda nada sobre el diseño del usuario). Instalar la APK con `adb install -r` (nunca `force-stop`), desplegar el panel nuevo al homelab (rsync + `docker compose up -d --build`, según la memoria `reloj-homelab-despliegue`), y comprobar con `/design/preview` (temporal, 60 s) y gestos reales por `adb shell input swipe`: foto grande subida sin error y reducida (`adb shell su -c ls -l /data/data/com.jarvis.clock/files/backgrounds`); ajustes de zoom/posición visibles; carrusel con dos fotos de prueba cambiando; deslizar `input swipe 100 240 700 240` abre Alarmas y `700 240 100 240` abre Ajustes; deslizar en vertical cambia de pantalla del perfil (si hay más de una); con `pm disable-user --user 0 com.android.webview` el deslizar de la carátula nativa hace lo mismo (reactivar con `pm enable`); medir memoria (`dumpsys meminfo com.jarvis.clock`) con un fondo de foto y un carrusel a la vez (objetivo: ≤ 100 MB sobre la línea base de 27 MB). Borrar al terminar solo las fotos de prueba que se hayan subido.
