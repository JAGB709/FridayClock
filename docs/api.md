# API HTTP del reloj

El reloj expone una API REST en el puerto 8080. Es lo que usan Hermes, el panel web y `clock.py`. Está pensada para escucharse **solo por Tailscale** (`Prefs.bindTailscaleOnly`).


Autenticación: cabecera `X-Auth-Token: <token>` (o `Authorization: Bearer`, o `?token=`).
Sin token configurado, el servidor responde 503 a todo.

| Método y ruta | Cuerpo | Qué hace |
|---|---|---|
| `GET /health` | sin cuerpo | vivo (sin token) |
| `GET /status` | sin cuerpo | hora, próxima alarma, si suena, estado del asistente, Bluetooth, alarmas, `face_version` (versión del motor de pantallas) |
| `GET /alarms` | sin cuerpo | lista de alarmas y temporizadores |
| `POST /alarm` | `{"action":"set","time":"07:30","label":"Trabajo","days":"weekdays"}` | crear (`days`: `""`, `daily`, `weekdays`, `weekend`, `["mon","fri"]`) |
| `POST /alarm` | `{"action":"cancel","id":3}` · `{"time":"07:30"}` · `{"all":true}` | borrar |
| `POST /alarm` | `{"action":"enable"/"disable","id":3}` · `{"action":"stop"/"snooze"}` | activar, desactivar, apagar o posponer |
| `POST /alarm` | `{"action":"update","id":3,"time":"06:45","max_volume":60}` | editar solo lo enviado; opciones por alarma: `sound`, `max_volume` (1 a 100), `ramp_s` (0 a 600), `snooze_min` (1 a 60), `briefing`; `null` = ajuste general; `sound` = `file:<nombre>` o `system:content://…` (tonos del reloj; otras URLs se rechazan). El panel devuelve los errores como `{"ok":false,"error":…}` |
| `GET /settings` | sin cuerpo | ajustes (despertador, carátula de respaldo, voz, ubicación) y su esquema |
| `PUT /settings` | `{"snooze_min":10,"night_brightness":5}` | cambiar ajustes; todo o nada (`400` con `errors` por clave); se aplican al instante |
| `GET /sounds` | sin cuerpo | tonos del sistema y sonidos subidos |
| `POST /sounds?name=gallo.mp3` | audio en crudo (≤ 5 MB; mp3/ogg/wav/m4a) | subir un sonido de alarma |
| `DELETE /sounds/{nombre}` | sin cuerpo | borrar un sonido subido |
| `POST /sounds/preview` | `{"id":"file:gallo.mp3"}` | escuchar 5 s (`409` si suena una alarma) |
| `GET /design` | sin cuerpo | diseño de pantallas (temas, pantallas en rejilla 8×6, perfiles, pantalla de alarma) y `saved` |
| `PUT /design` | el diseño entero | validar y guardar (todo o nada: `400` con `errors` por ruta); se aplica al instante |
| `POST /design/reset` | sin cuerpo | restaurar el diseño inicial |
| `POST /design/preview` | `{"design":{…},"seconds":30}` | mostrar un diseño 5 a 120 s sin guardarlo (`409` si suena una alarma) |
| `DELETE /design/preview` | sin cuerpo | cancelar la prueba |
| `GET /backgrounds` · `POST /backgrounds?name=x.jpg` · `GET /backgrounds/{nombre}` · `DELETE /backgrounds/{nombre}` | imagen en crudo (≤ 8 MB, jpg/png/webp; el reloj la reduce a 1600 px al guardar; hasta 60 fotos); `GET /backgrounds/{nombre}` devuelve la imagen, no JSON | fondos de pantalla |
| `GET /face/data` | sin cuerpo | los datos que recibe el motor: tiempo, previsión, próxima alarma, `now`, `stopwatch` y `timers` (`icon` es un nombre de icono Lucide, no un emoji) |
| `POST /timer` | `{"minutes":10,"label":"Pasta"}` · `{"action":"pause","id":3}` · `{"action":"resume","pid":1}` · `{"action":"cancel","id":3}` (con `pid` cancela uno en pausa; sin id ni pid cancela todos) | temporizador: crear, pausar, reanudar y cancelar (`400` si falta lo necesario, `404` si no existe) |
| `GET /timers` | sin cuerpo | temporizadores en marcha (`at`, fin en ms) y en pausa (`id` "p1", `remaining_ms`) y la hora `now` del reloj |
| `GET /stopwatch` · `POST /stopwatch` | `{"action":"start"/"pause"/"reset"/"toggle"/"status"}` | cronómetro: `running`, `base_ms`, `since` y `elapsed_ms` |
| `POST /display` | `{"type":"text"/"markdown"/"html"/"image"/"url","content":"…","title":"Compra","duration_ms":20000}` · `{"action":"clear"}` | pantalla completa; `markdown` (títulos `#`, viñetas, `**negrita**`, `*cursiva*`, `` `código` ``) y `text` se ven como una tarjeta, solo texto con estilo: ni HTML ni enlaces activos |
| `POST /speak` | `{"text":"Hola"}` | decir en voz alta |
| `POST /ask` | `{"text":"¿qué tiempo hace?","speak":true}` | como si se lo dijeras al reloj; devuelve `reply` |
| `POST /listen` | sin cuerpo | abre el micro |
| `POST /briefing` | sin cuerpo | buenos días hablados |
| `POST /bluetooth` | `{"action":"speaker"/"on"/"off"/"status"}` | modo altavoz Bluetooth |
| `POST /volume` · `/brightness` | `{"level":40}` | volumen multimedia y brillo (el brillo fija el valor manual del reloj, ver "Sistema de diseño") |
| `POST /actions` | `{"actions":[{"type":"set_alarm",…},…]}` | lote de acciones |

Los recordatorios y avisos no pasan por el reloj: los sirve el puente de voz (`/v1/notify` y `/v1/reminders`, con `Authorization: Bearer <BRIDGE_TOKEN>`). Ver [`recordatorios.md`](recordatorios.md). El puente avisa al reloj con `POST /speak` y `POST /display` de esta misma API.

```bash
curl -X POST http://<ip-reloj>:8080/alarm -H "X-Auth-Token: <token>" \
  -H "Content-Type: application/json" \
  -d '{"action":"set","time":"07:30","label":"Despertar","days":"weekdays"}'
```
