# Temas y pantallas del reloj — subproyecto 3a: motor, editor y vista previa

Fecha: 2026-10-06 · Estado: pendiente de revisión

## Contexto y objetivo

Hoy la carátula del reloj está escrita a mano en `MainActivity.kt` (fondo negro, hora gigante,
fecha, tiempo, próxima alarma, modo noche rojo fijo). Quiero poder **diseñar** lo que muestra
el reloj desde el móvil: temas, varias pantallas, perfiles según la hora y la pantalla de
alarma, **viendo en la web una vista previa exacta** y pudiendo **probarla 30 s en el reloj**
sin guardarla. Los cambios se aplican en el reloj al pulsar **Guardar**.

Es el subproyecto 3a de cuatro (cada uno con su spec, plan e implementación):

1. Panel web + despertador — hecho (rama `panel-web`).
2. Recordatorios (voz + Telegram de Hermes) — pendiente.
3. **3a (este documento): motor de pantallas, temas, perfiles y editor con vista previa**, con
   elementos que no necesitan servicios nuevos: hora, fecha, tiempo, previsión, próxima alarma
   y texto/frase del día.
4. 3b: elementos conectados (marco de fotos con Immich o subidas, agenda por CalDAV/Nextcloud,
   estado de homelab/Home Assistant, música Bluetooth, recordatorios de hoy). Cada uno se añade
   al motor sin tocarlo.

## Decisiones

- **Un solo motor HTML para reloj y web** (opción 1): el reloj dibuja la carátula con un
  `WebView` que carga `face/` desde el APK; el panel usa **los mismos archivos** en un `iframe`
  de 800×480. Así la vista previa es fiel (mismo código, fuentes y rejilla) y los elementos
  nuevos se escriben una vez.
- **Composición en rejilla fija de 8×6 casillas.** Cada elemento ocupa un rectángulo; no se
  pueden solapar; el texto se ajusta solo a su caja.
- **El reloj es la fuente de verdad.** El diseño vive en el reloj y funciona sin homelab.
- **Editor con la vista previa fija arriba y pestañas debajo** (Pantalla · Tema · Perfiles ·
  Alarma), como Ajustes.
- **Las alarmas nunca dependen del `WebView`**: suenan desde el servicio nativo y los botones
  Posponer/Descartar siguen siendo nativos. Si el `WebView` falla, se muestra la carátula
  nativa actual (se conserva como respaldo).
- UI y textos en español.

## Fuera de alcance

Elementos de 3b; recordatorios (subproyecto 2); animaciones; elementos girados; perfiles
distintos por día de la semana; cambiar desde la web el token, el puerto o la URL del puente
(igual que en el subproyecto 1).

## Modelo de datos

Un único JSON `design` (≤ 64 KB), versión 1. Ejemplo reducido:

```json
{
  "version": 1,
  "themes": {
    "amanecer": {
      "name": "Amanecer",
      "bg": {"type": "gradient", "from": "#1a2a4a", "to": "#ff8a65", "angle": 160, "dim": 0},
      "fg": "#ffffff", "muted": "#cfd8dc", "accent": "#ffd180",
      "font": "thin"
    }
  },
  "screens": {
    "principal": {
      "name": "Principal",
      "elements": [
        {"id": "hora", "type": "clock", "x": 0, "y": 0, "w": 5, "h": 4,
         "style": "digital", "seconds": false, "hour12": false, "color": "fg", "align": "center"},
        {"id": "tiempo", "type": "weather_now", "x": 5, "y": 0, "w": 3, "h": 2, "color": "fg"},
        {"id": "frase", "type": "text", "x": 0, "y": 4, "w": 8, "h": 2,
         "mode": "daily", "quotes": ["Hoy es un buen día."], "color": "muted"}
      ]
    }
  },
  "profiles": [
    {"id": "manana", "name": "Mañana", "from": "06:00", "theme": "amanecer",
     "screens": ["principal"], "rotate_s": 0, "brightness": 70, "night": false, "show_bar": true}
  ],
  "alarm_screen": {"theme": "amanecer", "elements": [],
                   "snooze_label": "Posponer", "dismiss_label": "Descartar"}
}
```

### Límites y validación (todo o nada)

- Identificadores `[a-z0-9_-]{1,24}`, únicos dentro de su lista.
- Hasta 12 temas, 8 pantallas, 12 elementos por pantalla, 1–4 perfiles, 1–6 pantallas por perfil.
- Elementos: `0 ≤ x`, `x + w ≤ 8`, `0 ≤ y`, `y + h ≤ 6`, `w ≥ 1`, `h ≥ 1`; **sin solapes** dentro
  de una pantalla.
- Perfiles: `from` en `HH:MM`, únicos entre perfiles; el perfil activo es el de mayor `from`
  ≤ hora actual (si no hay ninguno, el de mayor `from`, del día anterior). `rotate_s` es `0`
  (sin rotación; se desliza a mano) o `5–600`. `brightness` 1–100. `theme` y `screens` deben
  existir.
- Colores: `#rrggbb`. Textos: ≤ 120 caracteres; hasta 20 frases. Fuente: `thin`, `normal`,
  `bold`, `mono` o `digital`.
- Fondo: `color`, `gradient` (`from`, `to`, `angle` 0–359) o `image` (nombre de archivo subido +
  `dim` 0–80 de oscurecimiento).
- El PUT devuelve `400` con `{"ok":false,"error":"diseño no válido","errors":{"<ruta>":"<motivo>"}}`
  (por ejemplo `screens.principal.elements.hora`: "se solapa con tiempo") y no cambia nada.

### Elementos de 3a

| `type` | Qué muestra | Opciones propias |
|---|---|---|
| `clock` | hora | `style` `digital`/`analog`, `seconds`, `hour12` |
| `date` | fecha | `format` `long` (martes, 6 de octubre) / `short` / `numeric` |
| `weather_now` | tiempo ahora: icono y temperatura | `detail` (añade descripción, máx/mín, lluvia) |
| `forecast` | previsión | `mode` `hours`/`days`, `count` 2–5 |
| `next_alarm` | próxima alarma | `show_label` |
| `text` | texto libre o frase del día | `mode` `fixed`/`daily`/`alarm_label`, `text`, `quotes` |

`alarm_label` muestra el nombre de la alarma que está sonando; fuera de la pantalla de alarma queda vacío.

Opciones comunes: `color` (`fg`, `muted` o `accent`, del tema) y `align`
(`left`/`center`/`right`).

## Arquitectura

```
 Móvil ──Tailscale/LAN──▶ friday-panel ──X-Auth-Token──▶ Reloj :8080
                          ├─ static/face/  (copia del motor, idéntica a la del APK)
                          └─ iframe de vista previa 800×480 usa ese motor
 Reloj: MainActivity ─ WebView ─ assets/face/{index.html, face.js, face.css, fuentes}
```

### Motor `face/` (fuente única: `app-android/src/main/assets/face/`)

- `index.html`, `face-core.js`, `face.js` y `face.css`. El estilo `digital` del tema dibuja la hora con segmentos SVG generados por el propio motor (sin archivo de fuente); en el resto de textos equivale a `mono`.
  JavaScript clásico **ES2017** sin `?.`, `??`, ni `aspect-ratio` (el `WebView` del reloj es
  Chrome 74); se comprueba en el reloj real.
- API del motor: `Face.render(design, opts)` (`opts`: `profile`, `screen`, `theme`, `alarm`, `edit`,
  `selected`, `grid`, `bgBase`) y `Face.setData(data)`.
  `data = {weather: {...}, forecast: {hours: [...], days: [...]}, next_alarm: {...}|null}`.
- La **hora y la fecha las calcula el motor** con el reloj del dispositivo. La rotación entre
  pantallas, el deslizar, el antidesgaste (desplazar unos píxeles cada minuto) y el ajuste del
  texto al tamaño de su caja son del motor.
- Avisa a Android de los toques con un puente mínimo (`Native.tap()`, `doubleTap()`,
  `longPress()`).
- Modo vista previa (`?preview=1`): sin puente, con datos reales que le pasa el panel.

### Reloj (`app-android`)

- `MainActivity` aloja un `WebView` a pantalla completa. Configuración: solo se carga
  `https://appassets.androidplatform.net/assets/face/`, servido por la propia app con
  `shouldInterceptRequest`; navegación externa bloqueada; los fondos se sirven desde su carpeta en
  `/bg/<nombre>`. `blockNetworkLoads` no se usa: cualquier petición ajena recibe 404 por interceptación.
- **Android decide** el perfil activo según la hora y se lo dice al motor; también pone el
  brillo del perfil, el modo noche (`night`: tocar la pantalla la ilumina un momento, la
  barra inferior queda atenuada) y ocultar la barra (`show_bar`).
- **Datos del tiempo:** `Weather` se amplía para pedir también la previsión por horas y por
  días en la misma consulta a Open-Meteo, con la caché de 20 minutos de hoy.
- `DesignStore`: `SharedPreferences "design"` con el JSON; fondos en `filesDir/backgrounds/`
  (≤ 2 MB cada uno, jpg/png/webp, nombre saneado, subida atómica como los sonidos).
- `DesignSchema` (Kotlin puro): validación todo o nada de lo anterior y diseño inicial.
- **Migración:** si no hay diseño guardado, se genera uno inicial que **se ve igual que hoy**
  (pantalla con hora, fecha, tiempo y próxima alarma; perfil Día con `dayBrightness`; perfil
  Noche entre `night_start` y `night_end` con `nightBrightness`, tema rojo oscuro). Incluye 5
  temas de serie (Clásico, Noche roja, Amanecer, Minimal, Digital verde) que se pueden editar o
  borrar; la web ofrece "Restaurar diseño inicial".
- **Vista previa en el reloj:** `POST /design/preview` aplica un diseño sin guardarlo durante
  N segundos (por defecto 30, máx. 120) y vuelve al anterior; `DELETE /design/preview` lo cancela.
  Si suena una alarma, la vista previa se cancela.
- **Alarma sonando:** `AlarmActivity` carga el motor con el diseño de `alarm_screen` en la
  parte superior; Posponer y Descartar son nativos, con los textos del diseño y los colores del
  tema. `snooze_label` admite `{min}`, que se sustituye por los minutos de posponer.
- **Respaldo:** si el `WebView` no se puede crear, el proceso de render muere, la página no carga
  o el diseño guardado no es válido, se muestra la carátula nativa actual. Los ajustes
  `night_start`, `night_end`, `night_brightness` y `day_brightness` pasan a gobernar solo esa
  carátula de respaldo (en Ajustes de la web, su grupo se llama "Carátula de respaldo").
- Tocar la pantalla en un perfil con `night` solo sube el brillo unos segundos (al del primer perfil que no sea de noche); el tema no cambia.
- Se mantiene: doble toque = hablar, pulsación larga = Ajustes, `POST /brightness` puntual.

### API nueva del reloj (token, 503 si no hay token)

| Método y ruta | Cuerpo | Qué hace |
|---|---|---|
| `GET /design` | — | diseño guardado y `face_version` |
| `PUT /design` | diseño entero | valida (todo o nada), guarda y aplica al instante |
| `POST /design/preview` | `{"design":{…},"seconds":30}` | mostrarlo sin guardar |
| `DELETE /design/preview` | — | cancelar la prueba |
| `GET /backgrounds` | — | fondos subidos |
| `POST /backgrounds?name=x.jpg` | imagen en crudo (≤ 2 MB) | subir un fondo |
| `DELETE /backgrounds/{nombre}` | — | borrar un fondo |
| `GET /face/data` | — | los datos que recibe el motor (tiempo, previsión, próxima alarma) |

`GET /status` añade `face_version`. Todo se refleja en `Actions`, la skill de Hermes
(`clock.py`, `clock_tools.py`, `SKILL.md`, reloj de mentira y tests) y la tabla de la API del README.

### Panel (`homelab/friday-panel`)

- `static/face/` es una copia del motor del APK; un test de CI falla si difieren. El panel avisa
  en pantalla si `face_version` del reloj no coincide con la suya.
- Rutas proxy nuevas: `/api/design`, `/api/design/preview`, `/api/backgrounds`, `/api/face-data`
  (las mutantes llevan `X-Friday: 1`, como las demás).
- Nueva pestaña **Pantallas** en la barra inferior, con la **vista previa fija arriba** (iframe
  del motor a escala) y cuatro sub-pestañas:
  - **Pantalla:** elige la pantalla; lista de elementos; tocar uno abre sus opciones y arrastrar
    las esquinas sobre la vista previa cambia su tamaño (con ajuste a la rejilla y rechazo de
    solapes); **＋ Añadir elemento**.
  - **Tema:** crear, duplicar, editar y borrar temas; colores, fuente, fondo (color, degradado o
    imagen subida).
  - **Perfiles:** lista con hora de inicio, tema, pantallas, rotación, brillo, modo noche y barra.
  - **Alarma:** diseño de la zona superior y textos de los botones.
- Barra fija con **Descartar · ▶ Probar en el reloj · Guardar**. *Guardar* envía el diseño entero
  y, tras aplicarse, vuelve a leerlo del reloj. *Probar* hace `POST /design/preview`.
- La vista previa funciona con el reloj apagado usando datos de ejemplo; con el reloj conectado,
  usa los datos reales de `/face/data`.

## Errores y casos límite

- Reloj sin red o apagado: la web lo dice y no guarda; el reloj sigue con su diseño.
- Fondo borrado que usa un tema: el tema se muestra con su color de respaldo (`bg.color` o
  negro) y la web avisa.
- Previsión no disponible (sin red): el elemento muestra "—"; el resto sigue funcionando.
- Diseño guardado corrupto o inválido: el reloj usa el diseño inicial (siempre válido) y `GET /design` devuelve `"saved": false` para poder repararlo desde la web.
- Memoria: se mide en el reloj real (objetivo: ≤ 100 MB extra con el `WebView` abierto); si se
  supera, se libera el `WebView` cuando la actividad pasa a segundo plano.

## Pruebas

- **JVM:** `DesignSchemaTest` (solapes, límites de la rejilla, referencias, rangos, tamaño,
  perfiles, migración desde los ajustes actuales); saneado de fondos.
- **`node --test`:** lógica pura del motor (colocación en la rejilla, elección de perfil,
  frase del día, ajuste del texto), sin dependencias; paso nuevo en CI.
- **Skill:** el reloj de mentira aprende las rutas nuevas, con tests.
- **Panel:** `pytest` de las rutas proxy, subida de fondos y comprobación de que `static/face/`
  es idéntica al motor del APK.
- **Manual en el reloj real:** el diseño inicial se ve igual que hoy; cambiar el tema y el
  perfil por la web; probar 30 s y comprobar que vuelve solo; alarma sonando con la pantalla
  diseñada y con el `WebView` roto (respaldo); medición de memoria y captura por ADB.
