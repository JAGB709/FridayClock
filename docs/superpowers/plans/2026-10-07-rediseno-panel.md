# Rediseño 4c: panel web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rediseño completo del panel web (`homelab/friday-panel`) con el sistema Obsidiana de 4a: móvil primero, sin emojis, iconos Lucide, Inter, estructura B (Inicio con el reloj en vivo), alarmas con tarjetas y rueda de hora, editor de pantallas con vista previa fija.

**Architecture:** JS vanilla sin dependencias. `static/ui.js` (nuevo, cargado primero) aloja `h()` y los componentes; `static/wheel-core.js` es la lógica pura de la rueda; `static/styles.css` se reescribe sobre `/face/tokens.css`; `app.js` y `pantallas.js` se adaptan vista a vista. La API (`/api/...`), `editor-core.js` y los tests existentes no cambian de contrato.

**Tech Stack:** JS vanilla ES2017+ (el panel corre en navegadores modernos, no en el WebView del reloj), CSS con variables, FastAPI (sin cambios), `node --test`, pytest, Chrome headless para comprobación visual.

**Spec:** `docs/superpowers/specs/2026-10-07-rediseno-panel-design.md`

## Global Constraints

- **Cero emojis ni símbolos pictográficos** en `homelab/friday-panel/static/` (HTML, JS, CSS): nada de `＋ ‹ › ▶ ✓ ⚠ ● ⏰ 🔔 🏠 🖼️ ⚙️ 📁 ↺ ✕` ni similares. Se permiten `· – — × ↑ ↓` como puntuación y `…`. Todo se sustituye por iconos Lucide (`icon("nombre")`) o por texto.
- Textos de interfaz y comentarios en español; commits en español, **sin** `Co-Authored-By` ni `Claude-Session` (regla del usuario, vale para subagentes).
- Sin dependencias de runtime nuevas (ni CDN ni librerías). El panel solo carga archivos propios y `/face/*`.
- Panel siempre oscuro (Obsidiana). Colores solo desde tokens (`var(--bg)`, `--surface`, `--surface-high`, `--border`, `--text`, `--text-muted`, `--accent`, `--on-accent`, `--error`, `--success`, `--warning`); espaciado `--space-1..7`, radios `--radius-sm|md|lg`, tipografía `--fs-1..7`, duraciones `--dur-fast|base|slow` y `--ease`. Prohibidos los colores literales (`#...`, `rgb(...)`) en `styles.css` salvo `#000` en la vista previa y sombras con `rgba(0,0,0,..)`.
- Tipografía: Inter 300 (horas grandes), 400 (cuerpo), 600 (títulos y botones); `font-variant-numeric: tabular-nums` en horas y valores.
- Objetivos táctiles de al menos 48x48 px (botones, interruptores, chips de días, filas pulsables, casillas de miniaturas), separación mínima de 8 px; foco visible (anillo de 2 px acento) y pulsado visible; el estado nunca solo con color (icono y texto además); `aria-label` en todo botón solo de icono; `aria-current="page"` en la pestaña activa; avisos con `role="status"`/`aria-live="polite"` y diálogos con `role="dialog"` + `aria-modal`.
- Animaciones de 150 a 250 ms (`--dur-*` con `--ease`); `@media (prefers-reduced-motion: reduce)` las desactiva.
- Mantener nombres de clase y ids que usan los tests de JS/pytest y el motor (`#view`, `#tabs`, `#savebar`, `#save`, `#discard`, `#try`, `#toast`, `#conn`, `#facewrap`, `.face-frame`, `data-tab`, `data-key`, `data-sub`) salvo que la tarea diga lo contrario y se actualicen los tests.
- No cambiar la API del reloj ni la del panel (`app.py`), ni el contrato de `EditorCore` (se pueden añadir funciones).
- Iconos disponibles (Lucide, `FridayIcons.names`): `bell mic settings sun cloud cloud-sun cloud-rain cloud-snow cloud-lightning cloud-fog moon clock alert-triangle check x trash-2 plus play pause volume-2 image upload chevron-right` más los de la Task 1: `house layout-template chevron-left chevron-down chevrons-up chevrons-down arrow-up arrow-down layers wifi wifi-off pencil copy info music refresh-cw save rotate-ccw`.
- Comandos de prueba: `node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js`; `cd homelab/friday-panel && .venv/bin/python -m pytest -q`; `python tools/gen-tokens.py --check && python tools/gen-icons.py --check && bash tools/sync-face.sh --check`; `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug`.
- Comprobación visual: Chrome headless (`google-chrome`) con un script CDP propio en el scratchpad contra el panel con el mock del reloj (ver `homelab/friday-panel/tests/conftest.py` y `hermes-skill/mock_clock_server.py`); a 390 px de ancho y a 1024 px. Nunca contra el reloj ni el homelab reales hasta la Task 7.
- Al terminar cada tarea que toque `app-android/src/main/assets/face/`, ejecutar `bash tools/sync-face.sh` e incluir la copia.

## Review Focus

- Ninguna funcionalidad se pierde: Inicio (apagar, posponer, probar alarma, buenos días, decir), alarmas (crear, editar, activar, borrar, sonidos, opciones por alarma con "general/valor propio", temporizadores), pantallas (elementos, capas, temas, perfiles, alarma, fotos, probar en el reloj, guardar/descartar, cambios sin guardar con confirmación), ajustes y sonidos, login.
- Cada `data-key` / validación de errores del servidor sigue marcando el campo correcto (`markErrors`), con icono y texto además del borde.
- Rueda de hora: valores límite (00:00, 23:59), minutos con `scroll-snap`, teclado, y que el valor guardado sea siempre `HH:MM` válido aunque el usuario no toque la rueda (hora inicial 07:00 en alarma nueva).
- Sin emojis ni símbolos pictográficos ni colores literales (la prueba automática y un `grep` lo comprueban); todos los iconos usados existen en el sprite (un nombre inexistente no rompe la vista: `icon()` devuelve vacío).
- Objetivos táctiles de 48 px y foco/pulsado visibles; contraste de los pares del panel (texto sobre superficie, acento sobre fondo, texto sobre acento, error sobre fondo).
- Vista previa fija de Pantallas: no tapa el contenido (el contenido hace scroll bajo ella), funciona con el teclado del móvil abierto y en ancho de escritorio; la vista en vivo de Inicio no puede romper la vista si el motor no carga.
- Estados de carga, vacío y error en todas las vistas, y la conexión caída (`setConn(false)`) con icono y texto.

---

### Task 1: Ampliar el juego de iconos

**Files:**
- Create: `design-system/icons/<nombre>.svg` para los 18 nuevos; modify: `design-system/icons/README.md`
- Modify (generados): `app-android/src/main/assets/face/icons.js`, `app-android/src/main/res/drawable/ic_*.xml`, `homelab/friday-panel/static/face/icons.js`
- Test: `homelab/friday-panel/tests/test_design_system.py`, `app-android/face-tests/icons.test.js`

**Interfaces:**
- Consumes: `tools/gen-icons.py` (4a).
- Produces: los 18 iconos nuevos en `FridayIcons.names` y como VectorDrawable.

- [ ] **Step 1: Pruebas que fallan.** En `test_design_system.py` ampliar la lista `ICONS` con `house layout-template chevron-left chevron-down chevrons-up chevrons-down arrow-up arrow-down layers wifi wifi-off pencil copy info music refresh-cw save rotate-ccw`. En `icons.test.js` añadir:

```js
test("icons: el juego incluye los iconos del panel", () => {
  for (const n of ["house", "layout-template", "chevron-left", "chevrons-up", "arrow-down", "layers", "wifi-off", "pencil", "copy", "music", "save", "rotate-ccw"]) {
    assert.ok(Icons.names.includes(n), n);
    assert.ok(Icons.svg(n, 24).startsWith("<svg"), n);
  }
});
```
Run: `cd homelab/friday-panel && .venv/bin/python -m pytest -q tests/test_design_system.py; node --test app-android/face-tests/icons.test.js` -> FAIL.

- [ ] **Step 2: Descargar los SVG** (misma versión pinneada que el resto, `lucide-static@0.469.0`): `curl -sL https://unpkg.com/lucide-static@0.469.0/icons/<nombre>.svg -o design-system/icons/<nombre>.svg`. Si `house` no existe en esa versión, usar `home` y guardarlo como `house.svg` (documentarlo en el README, como se hizo con `alert-triangle`). Si algún icono no se puede obtener, BLOCKED.

- [ ] **Step 3: Generar y comprobar**

Run: `python tools/gen-icons.py && bash tools/sync-face.sh && python tools/gen-icons.py --check && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q) && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug -q`
Expected: PASS (aapt acepta los 18 `VectorDrawable` nuevos).

- [ ] **Step 4: Commit**

```bash
git add design-system app-android/src/main/assets/face app-android/src/main/res/drawable app-android/face-tests homelab/friday-panel/static/face homelab/friday-panel/tests
git commit -m "Iconos: 18 iconos Lucide más para el panel (casa, capas, flechas, wifi, guardar…)"
```

---

### Task 2: Fundación — `ui.js`, CSS nuevo, estructura y cabecera

**Files:**
- Create: `homelab/friday-panel/static/ui.js`
- Rewrite: `homelab/friday-panel/static/styles.css`
- Modify: `homelab/friday-panel/static/index.html`, `app.js` (solo la parte de utilidades, navegación, barra de guardado, toast, confirmación, login), `manifest.json` (`theme_color` y `background_color` a `#000000`)

**Interfaces:**
- Consumes: `FridayIcons` (`/face/icons.js`), `/face/tokens.css`.
- Produces (globales, `static/ui.js` se carga **antes** de `pantallas.js` y `app.js`): `h(tag, attrs, ...kids)` (movido desde `app.js`, mismo comportamiento), `icon(name, size = 20)` -> nodo SVG con clase `ico` o nodo de texto vacío, `btn(label, onclick, cls = "", iconName = null)` (se mantiene la firma con clases `ghost`, `danger`, `small`, `primary`; el icono va antes del texto; si `label` es vacío es un botón solo de icono y exige `aria-label` vía `opts`), `iconButton(iconName, label, onclick, cls = "ghost")` (objetivo 48 px, `aria-label` y `title`), `emptyState(iconName, text, action = null)`, `loadingState(text = "Cargando…")`, `errorState(msg, onRetry)`, `segmented(options, value, onchange)` (`options`: `[[valor, etiqueta, icono?]]`; botones con `role="tab"`/`aria-selected`), `switchRow(label, checked, onchange, key)` (fila con interruptor de 48 px, `data-key` opcional), `toast(msg, bad = false)` con icono (`check` o `alert-triangle`) y `role="status"`; `setConn(on, msg)` actualiza `#conn` con icono (`wifi`/`wifi-off`) y texto; `confirmInPage(msg, okLabel)` como diálogo accesible (`role="dialog"`, `aria-modal`, foco en el primer botón, Escape cancela).

- [ ] **Step 1: `index.html`.** Estructura: `<header id="top">` con `<h1 id="title">` y `<span id="conn" class="pill">`; `<main id="view">`; `<div id="savebar" hidden>` con `#discard` (`rotate-ccw`), `#try` (`play`, "Probar en el reloj", hidden) y `#save` (`save`, "Guardar"); `<div id="toast" hidden role="status" aria-live="polite">`; `<nav id="tabs" aria-label="Secciones">` con 4 botones `data-tab` (`home` "Inicio" `house`, `alarms` "Alarmas" `bell`, `design` "Pantallas" `layout-template`, `settings` "Ajustes" `settings`) cuyos iconos se inyectan desde JS (`icon(...)`) para usar el sprite. Orden de scripts: `/face/icons.js`, `ui.js`, `editor-core.js`, `wheel-core.js` (se añade en la Task 4; dejarlo comentado fuera: crear el enlace en la Task 4), `pantallas.js`, `app.js`. Sin ningún emoji. Mantener `<link rel="stylesheet" href="/face/tokens.css">` antes de `styles.css`.

- [ ] **Step 2: `ui.js`.** Implementar las funciones de Interfaces con la API descrita. `h()` debe ser exactamente el actual de `app.js` (copiar tal cual y borrarlo de `app.js`). `icon()` usa `window.FridayIcons && window.FridayIcons.node(name, size)`; si devuelve `null`, devuelve `document.createTextNode("")`; añade `class="ico"` y `aria-hidden="true"`. Ningún componente usa colores ni emojis; las clases CSS son las del apartado "Clases" del Step 3.

- [ ] **Step 3: `styles.css`** (reescrito entero, solo con variables de los tokens). Debe contener: `@font-face` de Inter (como ahora), reset y `body` (`padding-bottom` para barra inferior y de guardado, `env(safe-area-inset-*)`), cabecera `#top` (altura 56 px, título 600 `--fs-4`), `.pill` con icono y texto, **Clases**: `.card` (superficie, borde, radio `--radius-md`, padding `--space-4`), `.card.ring` (alarma sonando, borde acento), `.card.bad`, `h2` (título de tarjeta `--fs-3` 600 sin mayúsculas forzadas), `.muted`, `.row`/`.row.between`/`.stack`, **botones** `button` (altura mínima 48 px, radio `--radius-sm`, 600; variantes `.ghost` (borde), `.danger` (fondo `--error`, texto `--on-accent`), `.small` (alto 48 px igualmente; solo menos padding), `.icon` (cuadrado 48 px), `:focus-visible` anillo 2 px acento con separación, `:active` escala .98 y opacidad, `:disabled`), campos (`input`, `select`, `textarea`: altura 48 px, fondo `--surface-high`, borde `--border`, `:focus-visible` anillo), `label.field`, `.err` con icono + texto, `.field.error`/`.opt.error`/`.switch.error` (borde `--error`), `.switch` (fila de 56 px con interruptor propio: `input[type=checkbox]` con `appearance: none` de 52x32 y pulgar, acento al marcar, foco visible; texto "Activa/Apagada" donde aplique), `.chips`/`.chip` (48 px, `.on` acento con texto `--on-accent`), `.alarm-card` (ver Task 4), `.fab` (flotante 56 px acento, `position: fixed` encima de la barra), `.segmented` (4 botones de 48 px, activo con fondo `--surface-high` y subrayado acento), `#tabs` (barra inferior 64 px, icono 24 + etiqueta 12 px, activa en acento con indicador superior de 3 px, `aria-current`), `#savebar` (sobre `#tabs`), `#toast` (arriba, con icono, ancho completo, borde `--success` o `--error`), `.modal` + `.card` (diálogo), `.state` (vacío/carga/error centrado con icono), `.face-card`, `.face-wrap`, `.face-frame` (como ahora), `.thumbs`/`.thumb` (84x56, casilla con área táctil de 48 px, borde acento si marcada), `.elrow` (fila de 56 px), `.layers` (botones de capa en rejilla), `.subtabs` -> reutiliza `.segmented`, `.grid4`, `input[type=range]` (pista y pulgar propios, pulgar de 28 px), `input[type=color]`, `.warn` (icono + `--warning`), `@media (prefers-reduced-motion: reduce)` (sin transiciones) y `@media (min-width: 720px)` (contenido centrado a 640 px, barra inferior y de guardado del mismo ancho). Transiciones de 150 a 250 ms solo sobre `opacity`, `transform`, `background-color`, `border-color`.

- [ ] **Step 4: `app.js` (utilidades y navegación).** Quitar `h` (ahora en `ui.js`), `toast`, `setConn`, `confirmInPage` y `btn` de `app.js` (usar los de `ui.js`); `setView` conserva su comportamiento y puede envolver el contenido en `.view-in` para una entrada de 200 ms. `render(next)` actualiza `#title` (Inicio, Alarmas / Nueva alarma / Editar alarma, Pantallas, Ajustes), marca la pestaña activa con `aria-current="page"` y rellena una sola vez los iconos de `#tabs` y de `#discard/#try/#save`. `showLogin()` usa `card`/`field` con el sistema nuevo y el título "Entrar". Los textos "✓ Aplicado en el reloj" pasan a "Aplicado en el reloj" (el icono lo pone `toast`). `showError(r)` y las vistas usan `errorState(...)`. No tocar aún Inicio, Alarmas, Ajustes ni Pantallas más allá de lo que exija que sigan funcionando con los nuevos helpers (los emojis y símbolos de esas vistas se eliminan en sus tareas).

- [ ] **Step 5: Pruebas y comprobación visual**

Run: `node --check homelab/friday-panel/static/ui.js homelab/friday-panel/static/app.js homelab/friday-panel/static/pantallas.js && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q)`
Expected: PASS. Chrome headless (390 px): el panel carga con la cabecera, la barra de 4 pestañas con iconos Lucide, el estado de conexión con icono y texto, el login (si el mock lo exige) y el aviso de error de conexión; sin errores en consola. Registrar capturas vistas en el informe.

- [ ] **Step 6: Commit**

```bash
git add homelab/friday-panel/static
git commit -m "Panel: sistema visual Obsidiana, componentes ui.js, cabecera y barra inferior con iconos"
```

---

### Task 3: Inicio con el reloj en vivo

**Files:**
- Modify: `homelab/friday-panel/static/app.js` (`renderHome` y `doAction`), `styles.css` (estilos de la vista, si faltan)
- Test: comprobación visual con el mock

**Interfaces:**
- Consumes: componentes de la Task 2; `/api/status`, `/api/design`, `/api/face-data`, `/api/do/*`; el iframe del motor (`/face/index.html?preview=1`) y su protocolo `postMessage` (`face-render`, `face-data`) ya usado en `pantallas.js` (`previewFrame()`, `fitFrame()`).
- Produces: `liveClockCard()` -> nodo de la tarjeta del reloj en vivo (iframe en solo lectura, 16:10, escalado a ancho completo, con respaldo si el motor no carga: texto con la hora del reloj); `renderHome()` con las tarjetas del spec.

- [ ] **Step 1: Implementar `renderHome`.** Orden: (1) tarjeta del reloj en vivo; (2) alarma sonando si `s.ringing` (tarjeta `.card.ring` con icono `bell`, "Sonando: <nombre>", botones Apagar y Posponer); (3) próxima alarma (hora grande en Inter 300, nombre, "mañana a las 08:30" con `whenText`; toda la tarjeta pulsable, abre su editor `go({tab:"alarms", edit:id})` si el estado trae `id`, si no abre Alarmas); (4) acciones rápidas: Probar alarma en 10 s (`play`), Buenos días (`sun`), y el campo "Decir en el reloj…" con botón Decir (`mic`). Reutilizar `doAction` (sin "✓ Hecho" literal: `toast("Hecho")`). `liveClockCard()`: crear el iframe con `?preview=1`, al cargar enviar `face-render` con el diseño guardado (`GET /api/design` -> `design`) y `opts: { bgBase: "/api/bg/" }` sin `edit` ni `grid`, y `face-data` con `GET /api/face-data` (con `SAMPLE_DATA` de `pantallas.js` si falla); escalar con la misma lógica que `fitFrame()` (extraer `fitFrame` a una función que acepte el contenedor si hoy depende de `#facewrap`; mantener `pantallas.js` funcionando). El refresco cada 10 s existente debe actualizar el estado y los datos del reloj en vivo sin recrear el iframe (reenvía `face-data`) y no interferir con el campo de texto (se conserva la guarda actual).
- [ ] **Step 2: Estados.** Mientras carga: `loadingState()`; si falla: `errorState(msg, reintentar)`; sin alarmas próximas: "No hay alarmas activas" con botón "Crear alarma". Sin emojis (`🔔` -> icono `bell`).
- [ ] **Step 3: Probar**

Run: `node --check homelab/friday-panel/static/app.js homelab/friday-panel/static/pantallas.js && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q)`
Expected: PASS. Chrome headless contra el panel con el mock a 390 y 1024 px: la tarjeta del reloj muestra el diseño del mock; con `ringing` en el mock se ve la tarjeta de alarma sonando con Apagar y Posponer; las acciones rápidas llaman a la API (ver el log del mock); si `/api/design` falla se ve el respaldo y no se rompe la vista. Registrar lo visto.
- [ ] **Step 4: Commit**

```bash
git add homelab/friday-panel/static
git commit -m "Panel: Inicio con el reloj en vivo, alarma sonando destacada y acciones rápidas"
```

---

### Task 4: Alarmas — tarjetas, botón + y rueda de hora

**Files:**
- Create: `homelab/friday-panel/static/wheel-core.js`
- Create: `homelab/friday-panel/tests-js/wheel-core.test.js`
- Modify: `homelab/friday-panel/static/index.html` (enlazar `wheel-core.js` antes de `pantallas.js`), `static/ui.js` (componente `timeWheel`), `static/app.js` (`renderAlarms`, `renderAlarmEdit`, `paintAlarmEdit`, `optionNumber`, `soundPicker`), `styles.css`

**Interfaces:**
- Consumes: componentes de las Tasks 2-3.
- Produces: `WheelCore` (UMD como `EditorCore`): `pad2(n)`, `valuesFor(kind)` (`"hours"` -> 24 valores `0..23`, `"minutes"` -> 60 valores `0..59`), `indexFor(value, count)` (entero acotado a `0..count-1`), `valueAt(index, count)` (acota), `parseTime("HH:MM") -> {h, m}` o `null` si no es válida, `formatTime(h, m) -> "HH:MM"`, `nearestIndex(scrollTop, itemHeight, count)` (índice más cercano acotado); `timeWheel(value, onChange)` en `ui.js` -> nodo con dos columnas `role="listbox"` (horas y minutos), filas de 48 px, `scroll-snap-type: y mandatory`, relleno superior e inferior para centrar, fila central resaltada con acento y ambas bandas de desvanecimiento, `aria-label` "Horas"/"Minutos", teclas ArrowUp/ArrowDown cambian en 1 y PageUp/PageDown en 5/10, `onChange("HH:MM")` al detenerse el scroll (evento `scroll` con debounce de 80 ms) o al usar el teclado.

- [ ] **Step 1: Pruebas que fallan** — `tests-js/wheel-core.test.js` (formato del archivo `editor-core.test.js`: `require("node:test")`, `assert`, `const W = require("../static/wheel-core.js")`):

```js
test("pad2 y formatTime", () => {
  assert.equal(W.pad2(0), "00"); assert.equal(W.pad2(7), "07"); assert.equal(W.pad2(23), "23");
  assert.equal(W.formatTime(7, 5), "07:05"); assert.equal(W.formatTime(23, 59), "23:59");
});
test("parseTime valida", () => {
  assert.deepEqual(W.parseTime("07:30"), { h: 7, m: 30 });
  assert.deepEqual(W.parseTime("00:00"), { h: 0, m: 0 });
  assert.deepEqual(W.parseTime("23:59"), { h: 23, m: 59 });
  for (const bad of ["24:00", "12:60", "7:5", "", null, undefined, "ab:cd", "12-30"]) assert.equal(W.parseTime(bad), null, String(bad));
});
test("valuesFor", () => {
  assert.equal(W.valuesFor("hours").length, 24); assert.equal(W.valuesFor("minutes").length, 60);
  assert.equal(W.valuesFor("hours")[23], 23); assert.equal(W.valuesFor("minutes")[59], 59);
});
test("indexFor y valueAt acotan", () => {
  assert.equal(W.indexFor(5, 24), 5); assert.equal(W.indexFor(-3, 24), 0); assert.equal(W.indexFor(99, 24), 23);
  assert.equal(W.valueAt(-1, 60), 0); assert.equal(W.valueAt(70, 60), 59); assert.equal(W.valueAt(12, 60), 12);
});
test("nearestIndex a partir del scroll", () => {
  assert.equal(W.nearestIndex(0, 48, 24), 0); assert.equal(W.nearestIndex(47, 48, 24), 1);
  assert.equal(W.nearestIndex(24, 48, 24), 1); assert.equal(W.nearestIndex(23, 48, 24), 0);
  assert.equal(W.nearestIndex(48 * 100, 48, 24), 23); assert.equal(W.nearestIndex(-10, 48, 24), 0);
});
```
Run: `node --test homelab/friday-panel/tests-js/wheel-core.test.js` -> FAIL.

- [ ] **Step 2: `wheel-core.js`**

```js
/* Lógica pura de la rueda de hora (sin DOM). Se prueba con node --test. */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.WheelCore = factory();
})(this, function () {
  "use strict";
  function pad2(n) { return (n < 10 ? "0" : "") + n; }
  function clamp(n, lo, hi) { return Math.max(lo, Math.min(hi, n)); }
  function valuesFor(kind) {
    var n = kind === "hours" ? 24 : 60, out = [];
    for (var i = 0; i < n; i++) out.push(i);
    return out;
  }
  function indexFor(value, count) { return clamp(Math.round(Number(value) || 0), 0, count - 1); }
  function valueAt(index, count) { return clamp(Math.round(Number(index) || 0), 0, count - 1); }
  function formatTime(h, m) { return pad2(h) + ":" + pad2(m); }
  function parseTime(s) {
    var m = /^(\d\d):(\d\d)$/.exec(typeof s === "string" ? s : "");
    if (!m) return null;
    var h = Number(m[1]), mi = Number(m[2]);
    return h <= 23 && mi <= 59 ? { h: h, m: mi } : null;
  }
  function nearestIndex(scrollTop, itemHeight, count) { return clamp(Math.round(scrollTop / itemHeight), 0, count - 1); }
  return { pad2: pad2, valuesFor: valuesFor, indexFor: indexFor, valueAt: valueAt, formatTime: formatTime, parseTime: parseTime, nearestIndex: nearestIndex };
});
```

- [ ] **Step 3: Vista de alarmas.** `renderAlarms`: título de sección "Alarmas"; lista de `alarm-card`s (hora grande `--fs-6` Inter 300 tabular, nombre y `daysText`, texto "Activa" (acento + icono `check`) o "Apagada" (atenuada, icono `x`) junto al interruptor de 48 px; toda la tarjeta abre el editor; el interruptor no); `emptyState("bell", "No hay alarmas", crear)` si no hay; botón flotante `.fab` (`plus`, `aria-label` "Nueva alarma") que abre `go({tab:"alarms", edit:"new"})`; tarjeta "Temporizadores y pospuestas" con Cancelar (`x`). Toasts sin símbolos ("Alarma activada", "Alarma desactivada", "Cancelado"). Cargando: `loadingState()`; error: `errorState`.
- [ ] **Step 4: Editor.** Cabecera de la vista: botón `iconButton("chevron-left", "Volver a Alarmas")` + título. Orden: `timeWheel(form.current.time, v => setField("time", v))` (hora inicial `07:00` en alarma nueva; si `parseTime(form.current.time)` es `null` usar `07:00`), días (círculos de 48 px con `aria-pressed` + atajos Laborables/Finde/Todos/Una vez como `chip`s de 48 px), nombre, sonido (`soundPicker` sin emojis: opciones "Tono por defecto" y nombres tal cual, con el tipo indicado en texto "(archivo)" o "(tono)"; botones `btn("Escuchar 5 s", ..., "small ghost", "play")`, `btn("Subir sonido…", ..., "small ghost", "upload")`; el aviso de sonido ausente con icono `alert-triangle`), y las opciones avanzadas dentro de un bloque plegable ("Opciones", `<details>` con resumen de 48 px y `chevron-down`) que contiene los `optionNumber` actuales y el interruptor de "Buenos días del asistente" con `switchRow`; Borrar (`btn("Borrar alarma", ..., "danger", "trash-2")`, confirmación). Los `data-key` de `time`, `label`, `days`, `sound`, `max_volume`, `ramp_s`, `snooze_min`, `briefing` se mantienen para `markErrors`.
- [ ] **Step 5: `timeWheel`** en `ui.js` como se describe en Interfaces (altura visible de 5 filas = 240 px, relleno de 2 filas arriba y abajo para centrar, posiciona el scroll en la hora inicial al montarse con `scrollTop = indexFor(...) * 48` tras insertarse en el DOM — usar `requestAnimationFrame` —, y sincroniza si `value` cambia desde fuera).
- [ ] **Step 6: Probar**

Run: `node --check homelab/friday-panel/static/ui.js homelab/friday-panel/static/wheel-core.js homelab/friday-panel/static/app.js && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q)`
Expected: PASS. Chrome headless a 390 px: lista con una alarma activa y otra apagada (mock), editor con la rueda en 07:00; simular scroll de la columna de horas a 08 (`scrollTop = 8 * 48`) y de minutos a 30: `form.current.time === "08:30"` y aparece la barra de guardado; guardar envía `PUT/POST /api/alarms` con `time: "08:30"` (log del mock); errores del servidor marcan el campo; 1024 px sin desbordes. Registrar lo visto.
- [ ] **Step 7: Commit**

```bash
git add homelab/friday-panel/static homelab/friday-panel/tests-js
git commit -m "Panel: alarmas con tarjetas y botón +, editor con rueda de hora y opciones plegables"
```

---

### Task 5: Pantallas — vista previa fija y secciones

**Files:**
- Modify: `homelab/friday-panel/static/pantallas.js` (todo el editor), `static/ui.js` (si falta algún componente), `styles.css`

**Interfaces:**
- Consumes: componentes de las Tasks 2-4; `EditorCore` (sin cambios de contrato).
- Produces: `renderDesign()` con la vista previa anclada y el selector `segmented` de 4 secciones; todos los controles sin emojis ni símbolos.

- [ ] **Step 1: Estructura.** `renderDesign()` pinta: tarjeta de la vista previa (`.face-card` con `position: sticky; top: 56px` bajo la cabecera, `z-index` bajo el toast/diálogo, fondo `--bg`; el iframe escalado con `fitFrame()`), debajo `segmented([["screen","Elementos","layers"],["theme","Tema","image"],["profiles","Perfiles","clock"],["alarm","Alarma","bell"]], dz.sub, switchSub)` (los `data-sub` actuales se conservan en los botones) y el contenido de la sección (`dz.subEl`). El contenido hace scroll bajo la vista previa fija; con el teclado abierto (`visualViewport`) la vista previa no tapa el campo enfocado (al enfocar un campo, `scrollIntoView({block:"center"})` con margen de la altura de la tarjeta sticky). En ancho de escritorio (>= 720 px) la vista previa sigue arriba a 640 px.
- [ ] **Step 2: Sustituir símbolos y emojis.** Recorrer `pantallas.js` completo: "＋ Añadir" -> `btn("Añadir", ..., "", "plus")`; "▶ Probar" y similares -> icono `play`; "‹" -> `chevron-left`; "⚠ ..." -> `h("div",{class:"warn"}, icon("alert-triangle"), "texto")`; "✓ ..." -> texto sin símbolo (el `toast` pone el icono); "📁/🔔" -> texto; botones de capa: Traer al frente (`chevrons-up`), Subir una capa (`arrow-up`), Bajar una capa (`arrow-down`), Enviar al fondo (`chevrons-down`); duplicar tema con `copy`; borrar con `trash-2`; subir fotos con `upload`; quitar con `x`; los avisos de "falta la foto" con `alert-triangle`. Ningún carácter de la lista prohibida debe quedar (`grep -nP "[^\x00-\x7F]" homelab/friday-panel/static/pantallas.js` solo debe mostrar letras acentuadas, `·`, `–`, `—`, `×`, `↑`, `↓`, `…` y comillas tipográficas).
- [ ] **Step 3: Estilos de las secciones.** Lista de elementos (`.elrow` de 56 px con etiqueta de capa "delante"/"al fondo" como `chip` pequeño con icono, no solo texto atenuado), botones de capa en rejilla de 4 columnas de 48 px con iconos y `aria-label`/`title` en español, campos de posición X/Y/W/H en rejilla de 4, `rangeField` con el valor visible a la derecha en tabular, miniaturas con casilla de 48 px de área táctil y borde acento si marcada, tarjetas por bloque (Color, Fuente, Fondo, Fotos…), perfiles con tarjetas colapsables si ya existen (si no, tarjetas apiladas).
- [ ] **Step 4: Estados.** Carga del diseño (`loadingState`), error (`errorState` con reintentar), vista previa sin motor (mensaje "No se pudo cargar la vista previa" con `alert-triangle`), `form` sucio -> barra de guardado con Descartar, Probar en el reloj y Guardar (ya en la Task 2).
- [ ] **Step 5: Probar**

Run: `node --check homelab/friday-panel/static/pantallas.js && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q)`
Expected: PASS. Chrome headless a 390 y 1024 px contra el panel con el mock: las 4 secciones con la vista previa fija visible al hacer scroll; añadir un elemento, solaparlo, cambiar su capa con los 4 botones y ver la vista previa; crear un tema con "Carrusel de fotos" y elegir fotos del mock; cambiar un perfil; la sección Alarma; guardar (mock) y descartar; capturas de cada sección; medir con `getBoundingClientRect` que todos los `button`, `select`, `input` y `.elrow` visibles tienen alto y ancho >= 48 px (excepto texto en línea). Registrar lo visto.
- [ ] **Step 6: Commit**

```bash
git add homelab/friday-panel/static
git commit -m "Panel: editor de pantallas con vista previa fija, secciones y controles con iconos"
```

---

### Task 6: Ajustes, estados comunes y escáner de emojis del panel

**Files:**
- Modify: `homelab/friday-panel/static/app.js` (`settingInput`, `renderSettings`), `styles.css`, `ui.js`
- Modify: `homelab/friday-panel/tests/test_design_system.py` (escáner de emojis y de colores literales del panel, iconos usados existen), `app-android/face-tests/icons.test.js` (si procede)

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: Ajustes con el sistema nuevo; pruebas automáticas que impiden regresiones.

- [ ] **Step 1: Pruebas que fallan** (en `test_design_system.py`, usando `ROOT`):

```python
PANEL = ROOT / "homelab/friday-panel/static"


def _panel_files():
    for f in sorted(PANEL.iterdir()):
        if f.is_file() and f.suffix in (".html", ".js", ".css", ".json", ".svg") and f.name != "icon.svg":
            yield f


def test_no_emojis_or_pictographs_in_panel():
    import re
    rx = re.compile("[⌀-⏿■-➿⤀-⥿⬀-⯿←-⇿＋‹›️\U0001F000-\U0001FFFF]")
    allowed = set("↑↓")  # flechas arriba y abajo permitidas como texto
    hits = []
    for f in _panel_files():
        for i, line in enumerate(f.read_text(encoding="utf-8").split("\n"), 1):
            if any(ch not in allowed for ch in rx.findall(line)):
                hits.append(f"{f.name}:{i}")
    assert not hits, "símbolos pictográficos en el panel: " + ", ".join(hits)


def test_panel_css_uses_only_tokens_for_colors():
    import re
    css = (PANEL / "styles.css").read_text(encoding="utf-8")
    bad = []
    for i, line in enumerate(css.split("\n"), 1):
        if "@font-face" in line:
            continue
        if re.search(r"#[0-9a-fA-F]{3,8}\b", line) and "#000" not in line:
            bad.append(i)
        if re.search(r"rgba?\((?!\s*0\s*,\s*0\s*,\s*0)", line):
            bad.append(i)
    assert not bad, f"colores literales en styles.css, líneas: {bad}"


def test_every_icon_used_by_the_panel_exists_in_the_sprite():
    import re
    names = set()
    sprite = (ROOT / "app-android/src/main/assets/face/icons.js").read_text(encoding="utf-8")
    known = set(re.findall(r'^  "([a-z0-9-]+)": "', sprite, re.M))
    used = set()
    for f in _panel_files():
        if f.suffix != ".js":
            continue
        text = f.read_text(encoding="utf-8")
        used |= set(re.findall(r'\bicon(?:Button)?\(\s*"([a-z0-9-]+)"', text))
        used |= set(re.findall(r'\bbtn\([^)]*?,\s*"[a-z ]*",\s*"([a-z0-9-]+)"\)', text))
    assert known, "no se pudo leer el sprite"
    assert used - known == set(), f"iconos inexistentes: {sorted(used - known)}"
```
(Si el sprite no usa ese formato de línea, ajustar la expresión para extraer las claves del objeto `BODY`; la prueba debe fallar de verdad si se pide un icono que no existe.) Run: `cd homelab/friday-panel && .venv/bin/python -m pytest -q tests/test_design_system.py` -> FAIL (quedan símbolos o colores en Ajustes/estilos si no se completó en tareas previas; arreglar lo que salga).

- [ ] **Step 2: Ajustes.** `renderSettings`: título "Ajustes"; un bloque de conexión arriba (icono `wifi`/`wifi-off`, "Conectado al reloj" o "Sin conexión", y "Versión del motor" con `info`; si la del panel difiere de la del reloj, aviso `alert-triangle` "El motor del reloj y el del panel son distintos: actualiza el panel o el reloj"), tarjetas por grupo con filas etiquetadas (`switchRow` para booleanos, campos de 48 px con rango y unidad), error por campo con icono (`markErrors` ya añade `.err`; el CSS pone el icono con `::before` usando una máscara SVG inline propia **sin emoji** o, mejor, el JS añade `icon("alert-triangle", 16)`), "Sonidos subidos" con nombre, tamaño y `btn("Borrar", ..., "small ghost", "trash-2")` con confirmación; estado vacío "Ningún sonido subido todavía: súbelos desde la ficha de una alarma." con `music`.
- [ ] **Step 3: Barrido final de símbolos y colores.** `grep -nP "[^\x00-\x7F]"` sobre `static/*.js|*.html|*.css` y limpiar cualquier símbolo pictográfico que quede (`✓ ⚠ ＋ ‹ ▶ ● 🔔 ⏰ 🏠 📁`...). Colores literales fuera de `styles.css` (estilos inline en JS: `style: "..."` con `#...`) -> clases o variables.
- [ ] **Step 4: Probar**

Run: `node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q) && python tools/gen-tokens.py --check && python tools/gen-icons.py --check && bash tools/sync-face.sh --check`
Expected: PASS (las 3 pruebas nuevas en verde). Chrome headless a 390 y 1024 px: Ajustes completa con el mock (todos los grupos), un error de validación (`PUT /api/settings` inválido en el mock) marcando el campo con icono y texto, sonidos vacíos y con archivos. Capturas de las 4 pestañas y del login.
- [ ] **Step 5: Commit**

```bash
git add homelab/friday-panel
git commit -m "Panel: Ajustes con el sistema nuevo y pruebas contra emojis, colores sueltos e iconos inexistentes"
```

---

### Task 7: Documentación, CI y verificación en el móvil real

**Files:**
- Modify: `README.md` (sección del panel: nueva estructura, rueda de hora, vista previa fija, sistema de diseño, regla de cero emojis también en el panel), `CLAUDE.md` (solo añadir una línea: el panel usa `static/ui.js` y los tokens; prueba de emojis cubre `static/`), `.github/workflows/build-apk.yml` (el trabajo del panel ya ejecuta pytest y `node --test`; añadir `node --check` de `ui.js` y `wheel-core.js` donde se comprueban los demás JS)

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: documentación y CI coherentes; verificación final.

- [ ] **Step 1: Documentar y CI.** README: describir la estructura del panel (4 pestañas, Inicio con el reloj en vivo, alarmas con rueda, editor con vista previa fija), el sistema de diseño compartido con el reloj y las pruebas que lo protegen. CI: añadir los dos archivos nuevos a los `node --check`. `grep -rn "✓\|＋\|▶\|⚠" README.md` solo para las líneas que describan el panel (limpiar las que sean emojis en texto nuevo).
- [ ] **Step 2: Pruebas completas (como la CI)**

Run: `python tools/gen-tokens.py --check && python tools/gen-icons.py --check && bash tools/sync-face.sh --check && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug && node --check homelab/friday-panel/static/ui.js && node --check homelab/friday-panel/static/wheel-core.js && node --check homelab/friday-panel/static/app.js && node --check homelab/friday-panel/static/pantallas.js && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q) && (cd hermes-skill && pytest -q) && python -m py_compile homelab/friday-panel/app.py`
Expected: todo PASS.
- [ ] **Step 3: Commit**

```bash
git add README.md CLAUDE.md .github
git commit -m "Docs y CI: rediseño del panel (estructura, sistema de diseño y pruebas)"
```
- [ ] **Step 4: Verificación en el panel real** (la hace el controlador, no un subagente; no guardar nada sobre el diseño ni las alarmas del usuario): desplegar al homelab (rsync + `docker compose up -d --build`, según la memoria `reloj-homelab-despliegue`), abrir `http://<ip-tailscale-homelab>:8770` con Chrome headless (390 px y 1024 px) y capturar Inicio, Alarmas (lista y editor con la rueda), las 4 secciones de Pantallas, Ajustes y el login si procede; comprobar contra el reloj real la vista en vivo de Inicio, la lista de alarmas y los ajustes (solo lectura), medir objetivos táctiles de 48 px y los contrastes con las capturas, y que `/api/face-version` coincide con el reloj. Cerrar con la lista de comprobación del encargo (legibilidad, contraste, tamaños táctiles, coherencia de iconos, cero emojis) y el resumen de qué cambió y por qué respecto al panel anterior.
