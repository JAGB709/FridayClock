# Panel web de Friday — subproyecto 1: panel + despertador

Fecha: 2026-10-02 · Estado: pendiente de revisión

## Contexto y objetivo

Quiero configurar el despertador desde el móvil sin tocar el reloj: una web en el
homelab, accesible por Tailscale, donde ver el estado del reloj, gestionar alarmas
con opciones propias y cambiar los ajustes. **Los cambios se aplican en el reloj al
pulsar Guardar**, y no antes.

Es el primero de tres subproyectos, cada uno con su spec, plan e implementación:

1. **Panel web + despertador** (este documento).
2. **Recordatorios**: aviso hablado en el reloj + mensaje del bot de Telegram de
   Hermes (vía `POST /v1/notify` en el puente). Pestaña *Recordatorios* del panel.
3. **Temas y pantallas**: motor de carátulas declarativo (widgets en JSON), temas,
   rotación/deslizar entre pantallas, perfiles mañana/día/noche (sustituyen al modo
   noche actual), pantalla de alarma personalizable, editor con vista previa.

Este subproyecto deja preparada la estructura (pestañas, proxy, Guardar) para que 2 y 3
solo añadan pantallas y rutas.

## Decisiones

- **El reloj es la fuente de verdad** (opción 1). El panel no guarda estado propio: lee
  y escribe por la API HTTP del reloj. Así lo que se crea por voz aparece en la web y
  viceversa, y las alarmas siguen sin depender del homelab.
- Si el reloj no responde, el panel no puede guardar y lo dice claramente. No hay cola
  de cambios pendientes.
- El token del reloj vive solo en el homelab; el navegador nunca lo recibe.
- UI y textos en español, como el resto del proyecto.

## Fuera de alcance

Recordatorios, temas/pantallas (subproyectos 2 y 3). Cambiar desde la web el token, el
puerto HTTP, el bind a Tailscale, la URL o el token del puente (un error dejaría el reloj
inaccesible; siguen solo en *Ajustes* del reloj). Multiusuario. Acceso desde fuera de la
tailnet.

## Arquitectura

```
 Móvil (navegador / PWA) ──Tailscale──▶ friday-panel (homelab, :8770)
                                         ├─ GET /           → static/ (la web)
                                         └─ /api/*  ──X-Auth-Token──▶ Reloj :8080
```

### `homelab/friday-panel/` (nuevo)

Misma forma que `clock-bridge`: Python 3.12, FastAPI + uvicorn + httpx, `Dockerfile`,
`docker-compose.yml` con `network_mode: host`, `.env.example`.

Configuración (`.env`):

| Variable | Defecto | Uso |
|---|---|---|
| `CLOCK_URL` | — (obligatoria) | p.ej. `http://100.x.y.z:8080` |
| `CLOCK_TOKEN` | — (obligatoria) | token del reloj |
| `PANEL_BIND` | `127.0.0.1` | IP de escucha; en el homelab, la IP de Tailscale |
| `PANEL_PORT` | `8770` | puerto |
| `PANEL_PASSWORD` | vacío | si se define, pide contraseña (cookie firmada, 30 días) |
| `CLOCK_TIMEOUT_S` | `5` | timeout de cada llamada al reloj |

Rutas del panel (todas JSON; con `PANEL_PASSWORD`, todas salvo `/login` y `/health`
exigen la cookie):

| Ruta | Va al reloj |
|---|---|
| `GET /health` | — (vivo; incluye si el reloj responde) |
| `POST /login` | — (`{"password": …}` → cookie) |
| `GET /api/status` | `GET /status` |
| `GET /api/alarms` | `GET /alarms` |
| `POST /api/alarms` | `POST /alarm {"action":"set",…}` |
| `PUT /api/alarms/{id}` | `POST /alarm {"action":"update","id":…}` |
| `DELETE /api/alarms/{id}` | `POST /alarm {"action":"cancel","id":…}` |
| `GET /api/settings` | `GET /settings` |
| `PUT /api/settings` | `PUT /settings` |
| `GET /api/sounds` | `GET /sounds` |
| `POST /api/sounds` | `POST /sounds?name=…` (cuerpo crudo, `?name=`; se reenvía igual al reloj) |
| `DELETE /api/sounds/{name}` | `DELETE /sounds/{name}` |
| `POST /api/sounds/preview` | `POST /sounds/preview` |
| `POST /api/do/{stop,snooze,test,speak,briefing}` | `POST /alarm` stop/snooze, `POST /timer` 10 s, `POST /speak`, `POST /briefing` |

Errores: si el reloj no responde o hay timeout → `502 {"ok":false,"error":"El reloj no responde"}`;
si el reloj devuelve 4xx, se reenvía su código y su `error` tal cual (la web lo muestra).
El panel valida forma básica (tipos, tamaño de subida ≤ 5 MB, extensión mp3/ogg/wav/m4a)
antes de llamar al reloj; los rangos los valida el reloj.

### La web (`homelab/friday-panel/static/`)

`index.html`, `app.js`, `styles.css`, `manifest.json`, icono. JS sin dependencias ni
paso de build, móvil primero, tema oscuro. Instalable como PWA (sin service worker de
caché offline: sin reloj no hay nada que mostrar).

Barra inferior: **Inicio · Alarmas · Ajustes** (el subproyecto 2 añade *Recordatorios*,
el 3 *Pantallas*).

- **Inicio**: estado (conectado / "El reloj no responde"), hora del reloj, próxima
  alarma. Si suena: botones *Apagar* y *Posponer*. Además: *Probar alarma en 10 s*,
  *Decir en el reloj…* (campo de texto), *Buenos días*. Refresco cada 10 s mientras la
  pestaña está visible.
- **Alarmas**: lista (hora, nombre, días, interruptor activa). El interruptor se aplica
  al instante (es una acción, no un formulario). ＋ abre una ficha nueva; tocar una
  alarma abre su ficha. Temporizadores y pospuestas se listan aparte, solo con *Cancelar*.
- **Ficha de alarma**: hora, nombre, días (chips L M X J V S D + atajos *Laborables*,
  *Finde*, *Todos*, *Una vez*), activa. Sección *Opciones de esta alarma*, cada una con
  "Usar ajuste general" por defecto: sonido (selector con tonos del sistema y subidos,
  botón ▶ para escuchar 5 s en el reloj, botón *Subir sonido…*), volumen máximo (%),
  segundos de subida, minutos al posponer, buenos días sí/no. Botones *Guardar* y
  *Borrar* (con confirmación en la propia página, sin `confirm()` nativo).
- **Ajustes**: formulario generado desde `GET /settings` (tipo, rango, grupo, etiqueta),
  así los límites no se duplican. Grupos: *Despertador*, *Pantalla*, *Voz y asistente*,
  *Ubicación*. Gestión de sonidos subidos (listar, borrar).

**Guardar**: los formularios (Ajustes, ficha de alarma) llevan la cuenta de campos
modificados; con cambios aparece una barra fija *Guardar* (y *Descartar*). Guardar envía
solo lo modificado, muestra "Guardando…" y después "✓ Aplicado en el reloj" o el error.
Tras guardar se vuelve a leer del reloj y se pinta lo que ha quedado allí (si otra vía
cambió algo entretanto, gana la última escritura). Salir de un formulario con cambios
sin guardar pide confirmación en página.

## Cambios en el reloj (`app-android`)

Regla del CLAUDE.md: cada capacidad nueva se añade a `Actions`, a `Api.kt`, a la skill
(`clock.py`, `clock_tools.py`, `SKILL.md`, `mock_clock_server.py` + tests) y a la tabla
de la API del README.

### Ajustes: `GET /settings` y `PUT /settings`

Nuevo `SettingsSchema` (Kotlin puro, testeable en JVM) con la lista blanca de ajustes:
clave, tipo (`int`/`float`/`bool`/`string`), rango, grupo, etiqueta en español,
getter/setter sobre `Prefs`. Claves iniciales:

| Grupo | Claves |
|---|---|
| Despertador | `snooze_min` (1–60), `alarm_ramp_s` (0–600), `alarm_auto_snooze_min` (1–60), `morning_briefing` |
| Pantalla | `night_start` (0–23), `night_end` (0–23), `night_brightness` (1–100), `day_brightness` (1–100) |
| Voz y asistente | `assistant_name`, `tts_rate` (0.5–2), `wake_word`, `follow_up` |
| Ubicación | `city`, `lat` (−90–90), `lon` (−180–180) |

- `GET /settings` → `{"settings":{clave:valor,…},"schema":[{key,type,min,max,group,label},…]}`.
- `PUT /settings` con un objeto parcial. **Todo o nada**: si alguna clave es desconocida,
  del tipo equivocado o está fuera de rango → `400` con `{"ok":false,"errors":{clave:motivo}}`
  y no se cambia nada. Si todo es válido, se escribe, se publica
  `Events.Event.SettingsChanged(keys)` y se responde con los valores resultantes.
- Aplicación inmediata: `MainActivity` vuelve a calcular el modo noche y el brillo al
  recibir `SettingsChanged`; `ClockService`/`Assistant` arranca o para la escucha de la
  palabra de activación si cambió `wake_word`; el resto se lee de `Prefs` en cada uso y
  ya es inmediato.
- `Actions` gana `get_settings`/`set_settings` (para que Hermes también pueda).

### Opciones por alarma

`Alarm` gana campos opcionales (`null` = usar el ajuste general):
`sound: String?`, `maxVolume: Int?` (1–100), `rampSeconds: Int?` (0–600),
`snoozeMinutes: Int?` (1–60), `briefing: Boolean?`.

- Almacenamiento: claves cortas nuevas en `toStorage`/`fromStorage` (`snd`, `vol`, `rmp`,
  `snz`, `brf`), ausentes = `null`. Las alarmas ya guardadas se leen sin cambios.
- `toJson` expone `sound`, `max_volume`, `ramp_s`, `snooze_min`, `briefing` (o `null`).
- `sound`: `null` (tono por defecto), `"system:<uri>"` o `"file:<nombre>"`.
- `AlarmRingService`: usa la opción de la alarma o la general. `maxVolume` escala el
  volumen del reproductor y el `LoudnessEnhancer` (100 = comportamiento actual). Las
  pospuestas heredan las opciones de la alarma original.
- **Fallbacks**: si el sonido elegido no existe o falla al preparar → tono por defecto →
  pitidos. Una alarma nunca queda en silencio.

### Editar alarmas

`POST /alarm {"action":"update","id":3, …campos…}`: cambia solo los campos enviados
(`time`, `label`, `days`, `enabled`, y las opciones; enviar `null` en una opción vuelve
al ajuste general), valida, guarda y reprograma. `404` si no existe. Nuevo tipo
`update_alarm` en `Actions`. `set` acepta también las opciones.

### Sonidos

- `GET /sounds` → `{"sounds":[{"id":"system:…","name":"…","kind":"system"},{"id":"file:x.mp3","name":"x.mp3","kind":"file","size":…}]}`.
  Tonos del sistema vía `RingtoneManager` (tipo alarma; lista vacía si la GSI no trae).
- `POST /sounds?name=x.mp3` con el audio como cuerpo crudo (≤ 5 MB; `MAX_BODY` ya es
  8 MB). Nombre saneado (`[A-Za-z0-9._-]`, extensión mp3/ogg/wav/m4a), se guarda en
  `filesDir/sounds/`. Se comprueba que `MediaPlayer` lo puede preparar; si no → `400`
  y se borra.
- `DELETE /sounds/{nombre}`: borra el archivo. Las alarmas que lo usaban caen al
  fallback al sonar (y la web las marca como "sonido no disponible").
- `POST /sounds/preview {"id":…}`: suena 5 s por el stream de alarma; no actúa si una
  alarma está sonando.

## Pruebas

- **Reloj (JVM)**: `SettingsSchemaTest` (validación todo-o-nada, rangos, tipos, claves
  desconocidas); `AlarmTest` ampliado (round-trip de storage con y sin opciones,
  compatibilidad con alarmas antiguas, `update` parcial y `null` = general); saneado de
  nombres de sonido.
- **Skill**: `mock_clock_server.py` aprende `/settings`, `update` y `/sounds`; tests
  nuevos en `tests/test_clock_tools.py`.
- **Panel**: `pytest` + `httpx` contra el mock (reutilizado desde `hermes-skill`):
  proxy correcto, token nunca en respuestas, 502 con reloj caído, reenvío de 4xx,
  límite y extensión de subidas, login con `PANEL_PASSWORD`.
- **CI**: job `homelab` añade `py_compile` del panel y su `pytest`.
- **Manual en el reloj real**: cambiar brillo de noche y verlo aplicado sin reiniciar;
  alarma con MP3 subido y volumen 50 % suena con ese sonido; borrar el MP3 y comprobar
  que suena el tono por defecto; reloj desconectado → "El reloj no responde".

## Documentación

README: nueva sección del panel (instalación con Docker, `.env`, acceso por Tailscale)
y filas nuevas en la tabla de la API. `homelab/friday-panel/README.md` corto.
