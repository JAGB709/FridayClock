# Rediseño visual, parte 4c: panel web

Fecha: 2026-10-07 · Estado: pendiente de revisión · Continúa `2026-10-07-rediseno-fundamentos-design.md` (4a) y `2026-10-07-capas-fondo-carrusel-design.md` (3d)

## Contexto y objetivo

El panel (`homelab/friday-panel`) es lo que el usuario usa a diario desde el móvil y donde más le interesa el cambio visual. Hoy tiene cuatro pestañas (Inicio, Alarmas, Pantallas, Ajustes), unas 1.300 líneas de JS vanilla (`app.js`, `pantallas.js`, `editor-core.js`) y un CSS de 89 líneas con emojis, símbolos (U+FF0B U+2039 U+25B6 U+2713 U+26A0) y colores sueltos. El objetivo es un rediseño completo con el sistema de 4a (Obsidiana: negro, ámbar, Inter con números tabulares, iconos Lucide), pensado primero para el móvil, sin cambiar la API ni los datos.

Decisiones del usuario: estructura **B** (Inicio con el reloj en vivo y barra inferior de 4 pestañas); pestaña **Alarmas** con tarjetas grandes, estado Activa o Apagada y botón + flotante; selector de hora **en rueda**; editor de pantallas con la **vista previa fija arriba** y selector de 4 secciones; los temas del usuario siguen siendo editables.

## Reglas heredadas del encargo del usuario

Cero emojis en toda la interfaz, textos, etiquetas, código y comentarios (también en el panel: se eliminan los existentes); un único juego de iconos SVG (Lucide) con trazo uniforme; una sola familia tipográfica (Inter) con escala definida y números tabulares; paleta limitada como tokens (neutros, un acento, aviso, error, éxito); objetivos táctiles de al menos 48 px; animaciones de 150 a 250 ms que respetan "reducir movimiento"; contraste WCAG AA, foco y pulsación visibles, el estado nunca solo con color; texto en español. Al terminar: lista de comprobación (legibilidad, contraste, tamaños táctiles, coherencia de iconos, cero emojis) y resumen de qué cambió y por qué.

## Fuera de alcance

Pantallas nativas del reloj (4b); cronómetro, tarjetas de control y vista del agente (4d); tema claro del panel (el panel es siempre oscuro); nuevos endpoints de la API del reloj; frameworks o dependencias de runtime (el panel sigue siendo JS vanilla sin librerías).

## 1. Sistema visual del panel

- **Tokens:** el panel ya carga `/face/tokens.css` (4a). Se reescribe `static/styles.css` entero sobre los tokens (`--bg`, `--surface`, `--surface-high`, `--border`, `--text`, `--text-muted`, `--accent`, `--on-accent`, `--error`, `--success`, `--warning`, `--space-*`, `--radius-*`, `--fs-*`, `--dur-*`, `--ease`). Sin colores literales salvo los de la vista previa del reloj.
- **Tipografía:** Inter (300 para horas grandes, 400 cuerpo, 600 títulos y botones), escala `--fs-1..7`, `font-variant-numeric: tabular-nums` en horas y valores.
- **Iconos:** `/face/icons.js` (sprite Lucide de 4a). Se amplía el juego con: `house`, `layout-template`, `chevron-left`, `chevron-down`, `chevrons-up`, `chevrons-down`, `arrow-up`, `arrow-down`, `layers`, `wifi`, `wifi-off`, `pencil`, `copy`, `info`, `music`, `refresh-cw`, `save`, `rotate-ccw`. Los usan el reloj (generador) y el panel (`FridayIcons.node`). Ningún emoji ni símbolo pictográfico (`U+FF0B U+2039 U+25B6 U+2713 U+26A0 U+25CF` incluidos) queda en el panel.
- **Componentes** (en `static/ui.js`, todos construidos con `h()`; los nombres son los que exporta el archivo): `icon(name, size)`, `btn(label, onclick, cls, icon, opts)` (clases `primary`, `ghost`, `danger` y `small`, combinables; sin texto es un botón solo de icono con `aria-label`), `iconButton(icon, label, onclick, cls)` (objetivo 48 px, `aria-label`), `switchRow(label, checked, onchange, key)`, `segmented(options, value, onchange)`, `timeWheel(value, onChange)` con `flushWheels()`, `emptyState(icon, text, action)`, `loadingState(text)`, `errorState(msg, retry)`, `warnBox(text)`, `fitFaceFrame(wrap, frame)`, `toast(msg, bad)` con icono (`check`, `alert-triangle`) y región viva, `setConn(on, msg)` para el estado de conexión, `confirmInPage(msg, okLabel)` (diálogo modal accesible, sustituye a `sheet/confirm`) y los ayudantes de texto `humanKey(key)`, `humanError(msg)`, `errorBody(msg)` y `errorDetail(errors)`. Las tarjetas (`.card`), los campos (`label.field`) y las fichas (`.layer-chip`) son clases de `styles.css`, no funciones.
- **Estados:** foco visible (anillo de 2 px acento), pulsado (opacidad/escala 150 ms), deshabilitado (opacidad y `aria-disabled`); el error se muestra con icono, texto y borde, nunca solo con color.
- **Movimiento:** transiciones de 150 a 250 ms con `--ease`; `@media (prefers-reduced-motion: reduce)` las desactiva.

## 2. Estructura

- Cabecera (`#top`): título de la sección y estado de conexión (icono `wifi`/`wifi-off` + texto "Conectado" o "Sin conexión con el reloj"), sin punto de color solo.
- Barra inferior (`#tabs`): 4 pestañas con icono y etiqueta: Inicio (`house`), Alarmas (`bell`), Pantallas (`layout-template`), Ajustes (`settings`); la activa en acento con indicador y `aria-current="page"`; respeta `env(safe-area-inset-bottom)`.
- Barra de guardado (`#savebar`) sobre la barra inferior cuando hay cambios: Descartar (`rotate-ccw`), Probar en el reloj (`play`, solo en Pantallas) y Guardar (`save`, primario).
- Manifest y `theme-color`: `#000000`.

## 3. Inicio

- **Reloj en vivo:** el motor (`/face/index.html?preview=1`, el mismo de la vista previa) en una tarjeta de 16:10 a ancho completo, con el diseño guardado y datos reales (`/api/face-data`), en solo lectura (sin rejilla ni edición) y refrescado con el estado cada 10 s (el reloj mismo avanza solo).
- **Tarjetas:** próxima alarma (hora grande, nombre y "mañana a las 08:30"; toca para abrir su editor); alarma sonando (destacada, con Apagar y Posponer); acciones rápidas (Probar alarma en 10 s, Buenos días, campo "Decir en el reloj" con botón Decir).
- Mantiene todas las acciones actuales (`/api/do/stop|snooze|test|briefing|speak`).

## 4. Alarmas

- **Lista:** tarjetas con hora grande (Inter 300, `--fs-6`), nombre y días ("Laborables", "Sáb y dom"), estado "Activa" o "Apagada" con interruptor de 48 px; las apagadas atenuadas con la palabra "Apagada" visible; botón + flotante (`plus`, 56 px). Debajo, la tarjeta "Temporizadores y pospuestas" con Cancelar.
- **Editor** (pantalla completa, `chevron-left` "Alarmas", título "Nueva alarma" o "Editar alarma"):
  - **Rueda de hora:** dos columnas (horas 00-23 y minutos 00-59) con `scroll-snap`, fila central resaltada de 48 px, números tabulares en 300; actualiza `form.current.time` ("HH:MM") al detenerse; accesible con teclado (flechas arriba y abajo cambian el valor) y con `aria-label`. La lógica pura (valor a índice, índice a valor y redondeo) vive en `static/wheel-core.js` y se prueba con `node --test`.
  - **Días:** círculos de 48 px (L M X J V S D) más atajos (Laborables, Finde, Todos, Una vez).
    Los 7 círculos de días miden 44 px con 4 px de separación en 390 px: 7x48+6x8 no cabe en una fila; se cumple WCAG 2.2 (24 px).
  - Nombre, sonido (selector con icono `music`, Escuchar con `play`, Subir con `upload`; sin emojis), y "Opciones" plegables (volumen máximo, subida de volumen, posponer, buenos días) con el patrón "general / valor propio" actual.
  - Borrar (`trash-2`, peligro, con confirmación) y Guardar vía barra de guardado.

## 5. Pantallas (editor del diseño del reloj)

- **Vista previa fija arriba:** la tarjeta del motor se ancla (`position: sticky; top: <cabecera>`) con la rejilla y la selección; debajo, un selector segmentado de 4 secciones (Elementos, Tema, Perfiles, Alarma) y el contenido de la sección con scroll.
- **Contenido** (funcionalidad actual, sin cambios de datos): Elementos (lista de delante hacia atrás, añadir, opciones por tipo, botones de capa con iconos `chevrons-up`, `arrow-up`, `arrow-down`, `chevrons-down`), Tema (colores, fuente incluida `inter`, fondo color, degradado, imagen o carrusel de fotos, miniaturas con casillas), Perfiles (hora, tema, pantallas, rotación, brillo, modo noche, barra), Alarma (tema, elementos, textos de los botones), duplicar tema (`copy`), borrar, etc.
- **Foco en móvil:** secciones en tarjetas con encabezados, campos de 48 px, `rangeField` con valor visible, miniaturas de 84x56 con casilla de 48 px de área táctil.

## 6. Ajustes

- Grupos del esquema del reloj como tarjetas con filas etiquetadas; interruptores de 48 px; campos numéricos con rango visible y unidad; error por campo con icono. "Sonidos subidos" (nombre, tamaño, Borrar con confirmación). Se muestra el estado de la conexión y la versión del motor (`/api/face-version` frente a la del reloj, con aviso `info` si difieren).

## 7. Estados comunes y login

- Carga (`loading`), vacío (icono y texto con acción) y error (icono, mensaje y "Reintentar") en todas las vistas.
- Login: tarjeta centrada con campo de contraseña y botón Entrar, mismo sistema.
- Confirmaciones y avisos con los componentes de `ui.js`; todos accesibles (`role="dialog"`/`aria-live`).

## 8. Organización del código

`static/index.html` (estructura y enlaces), `static/styles.css` (tokens y componentes), `static/ui.js` (componentes e iconos), `static/wheel-core.js` (lógica pura de la rueda), `static/editor-core.js` (sin cambios de contrato), `static/app.js` (utilidades de red, formulario y guardado, navegación, Inicio, Alarmas, Ajustes) y `static/pantallas.js` (editor de pantallas). Sin dependencias nuevas; las funciones públicas usadas por los tests de JS (`EditorCore`) no cambian. El generador de iconos (`tools/gen-icons.py`) y la prueba de emojis se amplían: `static/` del panel entra en el escáner (excluyendo `static/face/fonts` y los binarios).

## 9. Pruebas

- **`node --test`:** `wheel-core` (valor a índice, redondeo, límites, formato de dos cifras); `EditorCore` existente sin regresiones.
- **pytest del panel:** sigue pasando (la API no cambia); la prueba de emojis cubre `static/` (sin `face/fonts`); la prueba de contraste añade los pares del panel; la prueba del sprite comprueba que cada icono que usa `static/*.js` existe en el juego (grep de `icon("...")`).
- **Comprobación visual** (Chrome headless contra el panel con el mock del reloj) a 390 px y a 1024 px de cada pantalla y estado: Inicio (normal y alarma sonando), Alarmas (lista, vacía, editor con rueda), Pantallas (las 4 secciones con la vista previa fija), Ajustes, login, error de conexión; objetivos táctiles de al menos 48 px (medidos con `getBoundingClientRect` en los botones, interruptores y filas); `prefers-reduced-motion`.
- **Despliegue** en el homelab y comprobación en el móvil real del usuario (URL del panel), con la lista de comprobación del encargo y el resumen de cambios.
