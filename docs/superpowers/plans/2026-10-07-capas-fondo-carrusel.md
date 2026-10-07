# Elementos superpuestos con capas y carrusel de fondo (3d) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir elementos solapados con control del orden de capas (traer al frente, enviar al fondo, subir, bajar) y un fondo de tema tipo carrusel de fotos.

**Architecture:** El orden de la lista de elementos es el orden de capas (el último se dibuja delante); el esquema deja de rechazar solapes y gana un tipo de fondo `photos`; el motor extrae el carrusel a una función compartida (`startCarousel`) y libera la capa saliente tras el fundido; el editor del panel gana botones de capa, selección del elemento tapado y el carrusel de fondo.

**Tech Stack:** Kotlin 1.9 sin dependencias de runtime (JUnit en test), JS ES2017 clásico (WebView Chrome 74), FastAPI + JS vanilla en el panel, `node --test`, pytest.

**Spec:** `docs/superpowers/specs/2026-10-07-capas-fondo-carrusel-design.md`

## Global Constraints

- **Cero emojis** en lo nuevo (interfaz, textos, comentarios). Los emojis preexistentes del panel se limpian en 4c; no tocarlos aquí.
- Textos de interfaz y comentarios en español; commits en español, **sin** `Co-Authored-By` ni `Claude-Session` (regla del usuario, vale para subagentes).
- App del reloj sin dependencias de runtime. JS del motor en ES2017 clásico (Chrome 74): sin `?.`, `??`, `inset`, `gap` en flex, `aspect-ratio`.
- Tras editar `app-android/src/main/assets/face/` ejecutar `bash tools/sync-face.sh` (la CI usa `--check`) e incluir la copia en el commit. La lista de archivos del motor no cambia.
- Orden de capas = orden de la lista de elementos (el último delante). No se añade ningún campo `z`. Los diseños existentes siguen siendo válidos.
- Límites que se mantienen: 12 elementos por pantalla, rejilla 8x6 (alarma 8x4), máx. 2 elementos `photos` por pantalla y 1 en la de alarma.
- Fondo `photos`: `images` 1 a 20 nombres (patrón de imagen `^[A-Za-z0-9_-]{1,60}\.(jpg|jpeg|png|webp)$`), `interval_s` 3 a 600 (por defecto 10), `transition` `fade|none` (por defecto `fade`), `order` `sequential|random` (por defecto `sequential`), `fit` `cover|contain|fill`, `zoom` 1 a 3, `pos_x`/`pos_y` 0 a 100, `dim` 0 a 80, `color` `#rrggbb` de respaldo. Los campos `images`, `interval_s`, `transition`, `order` solo con `type: "photos"`; `fit/zoom/pos_*` con `image` o `photos`.
- Memoria: cada carrusel (elemento o fondo) conserva una sola imagen descodificada: tras el fundido (700 ms) la capa saliente pone `background-image: none`. Objetivo en el reloj real con un fondo carrusel y dos elementos carrusel: app ≤ 127 MB PSS (línea base 27 MB + 100).
- En modo edición el elemento seleccionado se dibuja delante (`z-index`) para que su esquina de tamaño siga alcanzable; fuera de edición no hay `z-index`.
- Comandos de prueba: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug`; `node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js`; `cd homelab/friday-panel && .venv/bin/python -m pytest -q`; `cd hermes-skill && pytest -q`; `python tools/gen-tokens.py --check && python tools/gen-icons.py --check && bash tools/sync-face.sh --check`.
- No tocar el diseño ni las fotos reales del usuario en el reloj (usar `/design/preview` temporal y fotos de prueba propias que se borran). No usar `force-stop`.

## Review Focus

- Un diseño con elementos superpuestos se valida, se guarda y se dibuja en el orden de la lista; un diseño antiguo (sin solapes, con fondos `color|gradient|image`) sigue válido y se ve igual.
- Fondo `photos` con todas las fotos ausentes o lista inválida: se ve el color de respaldo, el motor no falla y el esquema rechaza listas vacías, de más de 20, o nombres no válidos con la ruta exacta (`themes.<id>.bg.images[k]`).
- Las funciones de capas del editor no mutan la entrada, devuelven la misma ordenación ante un id desconocido o un solo elemento, y respetan los extremos.
- Memoria y temporizadores: la capa saliente libera su imagen; el fondo carrusel no se reinicia al cambiar de pantalla del mismo perfil y sus temporizadores se cancelan al cambiar de tema o de diseño (sin fugas).
- Selección con solapes: pulsar de nuevo en el mismo punto selecciona el elemento de debajo; la esquina de tamaño del seleccionado sigue accesible; mover y redimensionar solo comprueban la rejilla (nunca el solape) y nunca dejan el elemento fuera de ella.
- No queda ningún mensaje "se solapa" en el esquema, el mock del reloj, el panel ni las pruebas.

---

### Task 1: Esquema — solapes permitidos y fondo `photos`

**Files:**
- Modify: `app-android/src/main/java/com/jarvis/clock/design/DesignSchema.kt`
- Modify: `hermes-skill/mock_clock_server.py`
- Test: `app-android/src/test/java/com/jarvis/clock/DesignSchemaTest.kt`, `hermes-skill/tests/test_clock_tools.py`, `homelab/friday-panel/tests/test_design.py`

**Interfaces:**
- Consumes: nada.
- Produces: `DesignSchema.validate` acepta elementos solapados y temas con `bg.type == "photos"` (campos de la tabla de Global Constraints); mensajes de error nuevos: `themes.<id>.bg.images` ("debe ser una lista de 1 a 20 fotos"), `themes.<id>.bg.images[k]` ("nombre de imagen no válido (jpg, png o webp)"), `themes.<id>.bg.type` ("debe ser color, gradient, image o photos"), y `themes.<id>.bg.<campo>` ("solo se usa con fondo de fotos") para `images/interval_s/transition/order` en otros tipos. El mock del reloj deja de rechazar solapes.

- [ ] **Step 1: Pruebas que fallan.**

En `DesignSchemaTest.kt` sustituir la prueba `elementos solapados` por:

```kotlin
    @Test fun `los elementos pueden solaparse y conservan el orden de la lista`() {
        val d = base()
        elements(d).getJSONObject(1).put("x", 0).put("y", 0).put("w", 8).put("h", 1) // la fecha sobre la hora
        assertTrue(DesignSchema.validate(d) is Result.Ok)
        assertEquals("hora", elements(d).getJSONObject(0).getString("id"))
        assertEquals("fecha", elements(d).getJSONObject(1).getString("id"))
    }
```
y añadir (usan los helpers `base()`, `invalid()` ya definidos; `fondo` pone el `bg` del tema `clasico`):

```kotlin
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
```
`hermes-skill/tests/test_clock_tools.py`: sustituir `test_set_design_overlap_changes_nothing` por una prueba con otro fallo, para no perder cobertura de "diseño inválido no cambia nada":

```python
def test_set_design_out_of_grid_changes_nothing(clock):
    d = clock.clock_get_design()["design"]
    d["screens"]["principal"]["elements"][1].update(x=7, w=3)
    r = clock.clock_set_design(d)
    assert r["ok"] is False and "screens.principal.elements.fecha.w" in r["errors"]
    assert clock.clock_get_design()["saved"] is False


def test_set_design_overlap_is_accepted(clock):
    d = clock.clock_get_design()["design"]
    d["screens"]["principal"]["elements"][1].update(x=0, y=0, w=8, h=1)
    assert clock.clock_set_design(d)["ok"] is True
```
`homelab/friday-panel/tests/test_design.py`: la prueba `test_put_design_invalid_is_forwarded_and_changes_nothing` pasa a usar `update(x=7, w=3)` y a esperar `r.json()["errors"]["screens.principal.elements.fecha.w"] == "se sale de la rejilla (x + w ≤ 8)"`.

Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.jarvis.clock.DesignSchemaTest'; (cd hermes-skill && pytest -q); (cd homelab/friday-panel && .venv/bin/python -m pytest -q)`
Expected: FAIL.

- [ ] **Step 2: `DesignSchema.kt`.**
1. En `validateElements`: eliminar el bucle final que comprueba solapes y las estructuras que solo servían para eso (`private class Rect`, la lista `rects` y el `else if (...) rects += Rect(...)`); conservar la comprobación de rejilla (`if (x != null && w != null && x + w > COLS) ... else if (y != null && h != null && y + h > rows) ...`).
2. En `validateTheme`: la lista de claves permitidas de `bg` añade `"images", "interval_s", "transition", "order"`. En el `when (bg.opt("type"))` añadir la rama:
```kotlin
            "photos" -> {
                val imgs = bg.optJSONArray("images")
                if (imgs == null || imgs.length() !in 1..20) c.err("$p.images", "debe ser una lista de 1 a 20 fotos")
                else for (k in 0 until imgs.length()) {
                    val s = imgs.opt(k)
                    if (s !is String || !IMAGE.matches(s)) c.err("$p.images[$k]", "nombre de imagen no válido (jpg, png o webp)")
                }
                if (bg.has("interval_s")) int(c, bg, "interval_s", p, 3, 600)
                oneOf(c, bg, "transition", p, setOf("fade", "none"))
                oneOf(c, bg, "order", p, setOf("sequential", "random"))
                photoFit(c, bg, p)
                if (bg.has("dim")) int(c, bg, "dim", p, 0, 80)
                if (bg.has("color")) color(c, bg, "color", p, false)
            }
```
y cambiar el mensaje de la rama `else` a `"debe ser color, gradient, image o photos"`. Tras el `when`, el chequeo de campos de ajuste pasa a:
```kotlin
        val bgType = bg.opt("type")
        if (bgType != "image" && bgType != "photos") for (k in PHOTO_FIT_KEYS) if (bg.has(k)) c.err("$p.$k", "solo se usa con fondo de imagen o de fotos")
        if (bgType != "photos") for (k in listOf("images", "interval_s", "transition", "order")) if (bg.has(k)) c.err("$p.$k", "solo se usa con fondo de fotos")
```
(Si alguna prueba existente comprueba el texto antiguo "solo se usa con fondo de imagen", actualízala al nuevo.)

- [ ] **Step 3: Mock del reloj** `hermes-skill/mock_clock_server.py`: eliminar el bucle `for b in els[i + 1:]` que añade `"se solapa con …"` (y el `enumerate` si queda sin uso).

- [ ] **Step 4: Ejecutar y comprobar**

Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug -q && (cd hermes-skill && pytest -q) && (cd homelab/friday-panel && .venv/bin/python -m pytest -q)`
Expected: PASS. `grep -rn "se solapa" app-android/src hermes-skill homelab/friday-panel/tests` no devuelve nada.

- [ ] **Step 5: Commit**

```bash
git add app-android/src hermes-skill homelab/friday-panel/tests
git commit -m "Esquema: elementos superpuestos permitidos y fondo de tema de fotos (carrusel)"
```

---

### Task 2: Lógica pura — `pickUnder`, `bgCss` de fotos y funciones de capas

**Files:**
- Modify: `app-android/src/main/assets/face/face-core.js`
- Modify: `homelab/friday-panel/static/editor-core.js`
- Test: `app-android/face-tests/core.test.js`, `homelab/friday-panel/tests-js/editor-core.test.js`

**Interfaces:**
- Consumes: nada.
- Produces: `FaceCore.pickUnder(ids, selectedId) -> string|null` (`ids` de arriba a abajo; si `selectedId` está en la lista devuelve el siguiente, y tras el último vuelve al primero; si no está, el primero; lista vacía -> `null`); `FaceCore.bgCss(bg)` para `type: "photos"` devuelve `bg.color || "#000000"`; `EditorCore.bringToFront(list, id)`, `sendToBack(list, id)`, `moveUp(list, id)` (una capa hacia delante = una posición hacia el final), `moveDown(list, id)` (una hacia el principio): devuelven una lista nueva (misma si el id no existe o no hay cambio posible), sin mutar la entrada.

- [ ] **Step 1: Pruebas que fallan.** En `core.test.js` añadir:

```js
test("pickUnder: cicla por los elementos bajo el puntero", () => {
  assert.equal(C.pickUnder([], "a"), null);
  assert.equal(C.pickUnder(["a", "b", "c"], null), "a");
  assert.equal(C.pickUnder(["a", "b", "c"], "x"), "a");
  assert.equal(C.pickUnder(["a", "b", "c"], "a"), "b");
  assert.equal(C.pickUnder(["a", "b", "c"], "b"), "c");
  assert.equal(C.pickUnder(["a", "b", "c"], "c"), "a");
  assert.equal(C.pickUnder(["a"], "a"), "a");
});

test("bgCss: fondo de fotos devuelve el color de respaldo", () => {
  assert.equal(C.bgCss({ type: "photos", images: ["a.jpg"], color: "#102030" }), "#102030");
  assert.equal(C.bgCss({ type: "photos", images: ["a.jpg"] }), "#000000");
});
```
En `editor-core.test.js` añadir (el archivo usa `E` para `EditorCore`):

```js
const ids = (l) => l.map((e) => e.id).join(",");
const L = () => [{ id: "a" }, { id: "b" }, { id: "c" }];

test("capas: traer al frente, enviar al fondo, subir y bajar", () => {
  assert.equal(ids(E.bringToFront(L(), "a")), "b,c,a");
  assert.equal(ids(E.bringToFront(L(), "c")), "a,b,c");
  assert.equal(ids(E.sendToBack(L(), "c")), "c,a,b");
  assert.equal(ids(E.sendToBack(L(), "a")), "a,b,c");
  assert.equal(ids(E.moveUp(L(), "a")), "b,a,c");
  assert.equal(ids(E.moveUp(L(), "c")), "a,b,c");
  assert.equal(ids(E.moveDown(L(), "c")), "a,c,b");
  assert.equal(ids(E.moveDown(L(), "a")), "a,b,c");
});

test("capas: no mutan la entrada y toleran ids desconocidos o un solo elemento", () => {
  const l = L();
  E.bringToFront(l, "a"); E.sendToBack(l, "c"); E.moveUp(l, "a"); E.moveDown(l, "c");
  assert.equal(ids(l), "a,b,c");
  assert.equal(ids(E.bringToFront(l, "zz")), "a,b,c");
  assert.equal(ids(E.moveUp([{ id: "solo" }], "solo")), "solo");
  assert.equal(ids(E.moveDown([], "x")), "");
});
```
Run: `node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js` -> FAIL.

- [ ] **Step 2: `face-core.js`.** Añadir antes del `return`:

```js
  /* ids ordenados de arriba a abajo: devuelve el que sigue al seleccionado (cicla); sin seleccionado, el de arriba. */
  function pickUnder(ids, selectedId) {
    if (!ids || !ids.length) return null;
    var i = ids.indexOf(selectedId);
    return i < 0 ? ids[0] : ids[(i + 1) % ids.length];
  }
```
y exportar `pickUnder: pickUnder`. `bgCss` ya devuelve `fallback` para tipos distintos de `gradient`: no requiere cambio (la prueba lo fija).

- [ ] **Step 3: `editor-core.js`.** Añadir:

```js
  function indexOfId(list, id) {
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return i;
    return -1;
  }

  /* Devuelve una lista nueva con el elemento `id` movido a la posición `to` (0..n-1); si no cambia nada, la misma lista copiada. */
  function relocate(list, id, to) {
    var out = list.slice();
    var i = indexOfId(out, id);
    if (i < 0) return out;
    var t = Math.max(0, Math.min(out.length - 1, to));
    if (t === i) return out;
    var item = out.splice(i, 1)[0];
    out.splice(t, 0, item);
    return out;
  }

  function bringToFront(list, id) { return relocate(list, id, list.length - 1); }
  function sendToBack(list, id) { return relocate(list, id, 0); }
  function moveUp(list, id) { var i = indexOfId(list, id); return i < 0 ? list.slice() : relocate(list, id, i + 1); }
  function moveDown(list, id) { var i = indexOfId(list, id); return i < 0 ? list.slice() : relocate(list, id, i - 1); }
```
y exportar `bringToFront, sendToBack, moveUp, moveDown` en el objeto devuelto.

- [ ] **Step 4: Probar, sincronizar y commit**

Run: `bash tools/sync-face.sh && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q)`
Expected: PASS.

```bash
git add app-android/src/main/assets/face app-android/face-tests homelab/friday-panel/static homelab/friday-panel/tests-js
git commit -m "Lógica pura: elegir el elemento de debajo, fondo de fotos y funciones de capas"
```

---

### Task 3: Motor — carrusel compartido, memoria, fondo de fotos y selección con solapes

**Files:**
- Modify: `app-android/src/main/assets/face/face.js`, `app-android/src/main/assets/face/face.css`
- Sync: `homelab/friday-panel/static/face/`

**Interfaces:**
- Consumes: `Core.pickUnder`, `Core.nextPhotoIndex`, `Core.photoStyle` (3c), `stylePhoto`, `photoUrl`, `el`, `S` de `face.js`.
- Produces: `startCarousel(container, names, opts, timers)` (`opts`: `interval_s`, `transition`, `order`, `fit`, `zoom`, `pos_x`, `pos_y`; `timers`: array al que se añade el `setInterval`; devuelve nada), usado por `builders.photos` y por el fondo; `S.bgTimers` (cancelados en `applyTheme`); la capa saliente libera su imagen a los 700 ms (o al instante con `transition: "none"`); `.el.sel { z-index: 5 }` en modo edición; la selección en edición cicla con `Core.pickUnder`.

- [ ] **Step 1: `face.css`.** Añadir:

```css
.el.sel { z-index: 5; }
```

- [ ] **Step 2: `startCarousel`.** En `face.js`, antes de `var builders`, definir la función con el cuerpo que hoy tiene `builders.photos` (dos capas `.pl`, `show(i, tries)`, precarga con `Image`, salto de fotos que fallan, intervalo `Math.max(3, Math.min(600, interval_s || 10)) * 1000` solo si hay más de una foto, primera foto con `Core.nextPhotoIndex(n, -1, "sequential")`), parametrizada por `container`, `names`, `opts` y `timers`:

```js
  function startCarousel(container, names, opts, timers) {
    names = (names || []).slice();
    var fade = opts.transition !== "none";
    var layers = [el("div", "pl" + (fade ? " fade" : "")), el("div", "pl" + (fade ? " fade" : ""))];
    container.appendChild(layers[0]); container.appendChild(layers[1]);
    var cur = -1, top = 0, busy = false;
    var order = opts.order === "random" ? "random" : "sequential";
    function show(i, tries) {
      if (busy) return;
      if (i < 0 || !names.length) return;
      busy = true;
      var img = new Image();
      img.onload = function () {
        var out = layers[top];
        var next = layers[1 - top];
        stylePhoto(next, photoUrl(names[i]), opts);
        next.classList.add("on");
        out.classList.remove("on");
        // Memoria: la capa saliente suelta su imagen cuando termina el fundido.
        if (fade) setTimeout(function () { if (!out.classList.contains("on")) out.style.backgroundImage = "none"; }, 700);
        else out.style.backgroundImage = "none";
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
    var iv = Math.max(3, Math.min(600, opts.interval_s || 10)) * 1000;
    if (names.length > 1) {
      timers.push(setInterval(function () { show(Core.nextPhotoIndex(names.length, cur, order), 1); }, iv));
    }
  }
```
`builders.photos` pasa a:

```js
    photos: function (e, box, r) {
      var wrap = el("div", "photos");
      box.appendChild(wrap);
      startCarousel(wrap, e.images, e, S.timers);
    },
```

- [ ] **Step 3: Fondo de fotos.** Añadir `bgTimers: []` a `S`. En `applyTheme`, al principio (antes de crear la capa) cancelar los temporizadores del fondo anterior:

```js
    for (var bt = 0; bt < S.bgTimers.length; bt++) clearInterval(S.bgTimers[bt]);
    S.bgTimers = [];
```
y, tras la rama `bg.type === "image"`, añadir la de fotos (misma estructura que la de imagen: `.bglayer` con las capas y la capa `.bgdim` encima):

```js
    else if (bg.type === "photos" && bg.images && bg.images.length) {
      var pl = el("div", "bglayer");
      startCarousel(pl, bg.images, bg, S.bgTimers);
      var pdim = el("div", "bgdim");
      pdim.style.background = "rgba(0,0,0," + ((bg.dim || 0) / 100) + ")";
      pl.appendChild(pdim);
      rootEl.insertBefore(pl, contentEl);
    }
```
(El `if` existente de `type === "image"` pasa a `if ... else if ...` para encadenar.) `draw()` no toca `S.bgTimers`, así que el fondo no se reinicia al cambiar de pantalla del mismo perfil.

- [ ] **Step 4: Selección con solapes.** En `editDown`, sustituir el cálculo `var box = t.closest ? t.closest(".el") : null;` por:

```js
    var box = t.closest ? t.closest(".el") : null;
    if (box && !handle && document.elementsFromPoint) {
      var stack = document.elementsFromPoint(ev.clientX, ev.clientY);
      var ids = [];
      for (var si = 0; si < stack.length; si++) {
        var cand = stack[si].closest ? stack[si].closest(".el") : null;
        var cid = cand ? cand.getAttribute("data-id") : null;
        if (cid && ids.indexOf(cid) < 0) ids.push(cid);
      }
      var pick = Core.pickUnder(ids, S.opts.selected);
      if (pick) {
        var pbox = contentEl.querySelector('.el[data-id="' + pick + '"]');
        if (pbox) box = pbox;
      }
    }
```
Si hay `handle` (la esquina del seleccionado) el comportamiento de redimensionar no cambia. El resto de `editDown` (mover, `face-select` al soltar sin movimiento) usa `box` ya resuelto.

- [ ] **Step 5: Probar, sincronizar y comprobar visualmente**

Run: `bash tools/sync-face.sh && node --check app-android/src/main/assets/face/face.js && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q)`
Expected: PASS. Comprobación en Chrome headless (como en 3c, con 3 fotos sintéticas servidas en `/bg/` y un diseño de prueba): (a) un elemento reloj y un elemento fecha solapados se dibujan en el orden de la lista y, al invertir la lista, se invierte el orden visual; (b) un tema con `bg.type: "photos"` (3 fotos, intervalo 3 s, `dim: 30`) alterna las fotos con fundido y tras el fundido la capa saliente tiene `background-image: none` (comprobarlo con `getComputedStyle`); (c) cambiar de pantalla del mismo perfil no reinicia el fondo; (d) en modo edición, pulsar dos veces en un punto con dos elementos superpuestos envía `face-select` con el de arriba y luego con el de debajo; (e) un fondo `photos` con todas las fotos inexistentes muestra el color de respaldo sin errores en consola. Registrar lo visto en el informe.

- [ ] **Step 6: Commit**

```bash
git add app-android/src/main/assets/face homelab/friday-panel/static/face
git commit -m "Motor: carrusel compartido que libera memoria, fondo de fotos y selección entre elementos solapados"
```

---

### Task 4: Panel — capas, solapes y fondo carrusel en el editor

**Files:**
- Modify: `homelab/friday-panel/static/pantallas.js`, `homelab/friday-panel/static/styles.css` (solo estilos nuevos mínimos)
- Test: `homelab/friday-panel/tests-js/editor-core.test.js` (si procede), comprobación visual con el mock

**Interfaces:**
- Consumes: `EditorCore.bringToFront/sendToBack/moveUp/moveDown/inBounds/findFreeSpot` (Task 2); esquema de fondo `photos` (Task 1); ayudas de `pantallas.js` (`h`, `btn`, `toast`, `touch`, `paintSub`, `currentElements`, `currentRows`, `selectElement`, `elementOptions`, `addElement`, `setRect`, `resizeElement`, `moveElement`, `photoListField`, `photoFitBlock`, `rangeField`, `numberField`, `selectField`, `colorField`, `imagePicker`, `deleteBackground`, `dz`, `form`).
- Produces: botones de capa en `elementOptions`; lista de elementos de delante hacia atrás con etiquetas "delante" y "al fondo"; sin comprobación de solapes en `setRect`, `resizeElement`, `moveElement`; `addElement` coloca el elemento en un hueco libre y, si no hay, en `x:0, y:0` con su tamaño por defecto; tipo de fondo "Carrusel de fotos" con formulario completo; `deleteBackground` avisa también de fondos carrusel.

- [ ] **Step 1: Sin comprobación de solapes.** En `setRect`, `resizeElement` y `moveElement` eliminar las llamadas a `EditorCore.collides(...)` y el aviso "Se solapa con …" / "No cabe ahí: se solaparía"; dejar solo `EditorCore.inBounds(cand, currentRows())` (si no cabe: `toast("Se sale de la rejilla", true)`, y en `resizeElement`/`moveElement` además `pushPreview()` para devolver el elemento a su sitio). `moveElement` conserva la comprobación `Number.isInteger(x) && Number.isInteger(y)` de 3c.

- [ ] **Step 2: `addElement`.** Sustituir la búsqueda de hueco y el rechazo por:

```js
  let size = { w: t.w, h: t.h };
  let spot = EditorCore.findFreeSpot(list, size.w, size.h, rows);
  if (!spot) spot = { x: 0, y: 0 }; // sin hueco libre: se coloca encima de lo que haya
```
(Si el tamaño por defecto no cabe en las filas de la pantalla de alarma —4 filas—, reducir `size.h` a `Math.min(size.h, rows)`.) El elemento nuevo queda delante (se añade al final de la lista).

- [ ] **Step 3: Lista y botones de capa.** En `elementsPanel`, mostrar los elementos de delante hacia atrás (`list.slice().reverse()`), con el texto `typeInfo(e.type).label + " · " + e.id` y, a la derecha, una etiqueta muda "delante" para el último elemento de la lista y "al fondo" para el primero (si hay más de uno). Actualizar el párrafo de ayuda: "Toca un elemento en la vista previa o en la lista; vuelve a tocar el mismo sitio para elegir el de debajo. Arrastra su esquina azul para cambiar el tamaño." En `elementOptions(e)` añadir, tras los campos de posición y color, una fila de cuatro botones pequeños:

```js
    h("div", { class: "row layers" },
      btn("Traer al frente", () => reorder(EditorCore.bringToFront), "small ghost"),
      btn("Subir una capa", () => reorder(EditorCore.moveUp), "small ghost"),
      btn("Bajar una capa", () => reorder(EditorCore.moveDown), "small ghost"),
      btn("Enviar al fondo", () => reorder(EditorCore.sendToBack), "small ghost")),
```
con la función (definida junto a `elementOptions`):

```js
function reorder(fn) {
  const list = currentElements();
  const e = list.find((x) => x.id === dz.selected);
  if (!e) return;
  const next = fn(list, e.id);
  list.splice(0, list.length, ...next);   // se reordena la misma lista del diseño
  touch(); paintSub();
}
```
Los botones se deshabilitan (`disabled`) cuando no aplican: "Traer al frente" y "Subir" si `e` es el último; "Enviar al fondo" y "Bajar" si es el primero; todos si hay un solo elemento. Estilos mínimos en `styles.css` para `.layers` (los cuatro botones caben en una fila en 390 px: `flex-wrap: wrap`, margen de 8 px entre ellos, altura táctil de al menos 44 px).

- [ ] **Step 4: Fondo carrusel en el editor de tema.** En la función que pinta los campos de fondo del tema (la del `selectField("Tipo de fondo", ...)`), añadir la opción `["photos", "Carrusel de fotos"]` y su alta:

```js
    else if (v === "photos") t.bg = { type: "photos", images: dz.backgrounds.slice(0, 1).map((b) => b.name), interval_s: 10,
      transition: "fade", order: "sequential", fit: "cover", zoom: 1, pos_x: 50, pos_y: 50, dim: 0, color: base };
```
y la rama de formulario para `bg.type === "photos"`:

```js
  if (bg.type === "photos") {
    return [kind,
      photoListField(bg, { key: "images", label: "Fotos del carrusel" }),
      numberField("Segundos entre fotos (3–600)", bg.interval_s === undefined ? 10 : bg.interval_s, 3, 600, (v) => { bg.interval_s = v; touch(); }),
      selectField("Transición", [["fade", "Fundido"], ["none", "Corte directo"]], bg.transition || "fade", (v) => { bg.transition = v; touch(); }),
      selectField("Orden", [["sequential", "En orden"], ["random", "Al azar"]], bg.order || "sequential", (v) => { bg.order = v; touch(); }),
      photoFitBlock(bg),
      numberField("Oscurecer las fotos (0–80 %)", bg.dim === undefined ? 0 : bg.dim, 0, 80, (v) => { bg.dim = v; touch(); }),
      colorField("Color si falta la foto", bg.color || "#000000", (v) => { bg.color = v; touch(); })];
  }
```
(`photoListField(e, f)` usa `e[f.key]`, así que sirve tal cual para `bg.images`; si su implementación depende de otras propiedades de `e`, adaptarla mínimamente sin cambiar su comportamiento para el elemento Fotos.) Al cambiar de tipo de fondo se sigue conservando el color base.

- [ ] **Step 5: `deleteBackground`.** Además de los temas con `bg.type === "image"` que usan la foto, recoger los temas con `bg.type === "photos"` cuyo `bg.images` la incluye y añadirlos al mensaje ("Lo usan los temas: … y los carruseles de fondo de: …"); mantener el aviso sobre elementos Fotos.

- [ ] **Step 6: Probar y comprobar visualmente**

Run: `node --check homelab/friday-panel/static/pantallas.js && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q)`
Expected: PASS. Comprobación en Chrome headless contra el panel con el mock del reloj (`conftest.py` / `mock_clock_server.py`; nada del reloj ni del homelab reales): añadir dos elementos que se solapan sin aviso de error; los cuatro botones de capa reordenan la lista y la vista previa; la lista se muestra de delante hacia atrás; pulsar dos veces en el mismo punto de la vista previa cambia la selección al de debajo; arrastrar o redimensionar encima de otro elemento se acepta y fuera de la rejilla se rechaza con aviso; crear un tema con "Carrusel de fotos", elegir dos fotos y ver el carrusel en la vista previa; borrar una foto usada por un carrusel de fondo muestra el aviso. Registrar lo visto en el informe.

- [ ] **Step 7: Commit**

```bash
git add homelab/friday-panel/static
git commit -m "Panel: capas, elementos solapados y carrusel de fondo de tema en el editor"
```

---

### Task 5: Documentación, CI y verificación en el reloj real

**Files:**
- Modify: `README.md` (sección de pantallas y temas: solapes y orden de capas, fondo `photos` con sus campos y límites), `hermes-skill/mi-smart-clock/SKILL.md` (los diseños admiten solapes; fondo `photos`), `CLAUDE.md` (solo añadir una línea)

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: documentación coherente; verificación en el reloj real.

- [ ] **Step 1: Documentar.** README: en "Pantallas y temas", explicar que los elementos pueden solaparse y el orden de la lista decide las capas (el último, delante), los botones del editor, el fondo `photos` (campos de Global Constraints) y el cambio de memoria; actualizar cualquier frase que diga que los elementos no pueden solaparse (`grep -n -i "solap" README.md hermes-skill/mi-smart-clock/SKILL.md`). `SKILL.md`: los diseños admiten solapes y fondo `photos`. `CLAUDE.md`: añadir una línea en la sección del motor ("Capas: el orden de la lista de elementos es el orden de dibujo; el fondo de tema admite `photos`").

- [ ] **Step 2: Pruebas completas (como la CI)**

Run: `python tools/gen-tokens.py --check && python tools/gen-icons.py --check && bash tools/sync-face.sh --check && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q) && (cd hermes-skill && pytest -q) && python -m py_compile homelab/friday-panel/app.py`
Expected: todo PASS.

- [ ] **Step 3: Commit**

```bash
git add README.md CLAUDE.md hermes-skill
git commit -m "Docs: elementos superpuestos con capas y carrusel de fondo de tema"
```

- [ ] **Step 4: Verificación en el reloj real** (la hace el controlador, no un subagente; sin tocar el diseño ni las fotos del usuario; sin `force-stop`). Instalar con `adb install -r`; subir 3 fotos de prueba propias (`test3d_*.jpg`) y usar `/design/preview` temporal con: (a) dos elementos solapados en un orden y luego en el inverso; (b) un tema con `bg.type: "photos"` (3 fotos, intervalo 3 s) y dos elementos carrusel a la vez; capturar la pantalla a lo largo del tiempo para ver el fundido; medir `dumpsys meminfo com.jarvis.clock` (objetivo ≤ 127 MB PSS). Comprobar con `GET /design` (sin guardar) que el diseño del usuario no cambió. Borrar las fotos de prueba y las capturas. Desplegar el panel al homelab (rsync + `docker compose up -d --build`, según la memoria `reloj-homelab-despliegue`) y comprobar `/api/face-version` igual a la del reloj.
