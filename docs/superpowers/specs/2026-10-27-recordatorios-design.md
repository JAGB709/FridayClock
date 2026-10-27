# Recordatorios con aviso hablado y Telegram

## Objetivo

Que Friday avise de un recordatorio hablando en el reloj, escribiendo por Telegram (con el bot de Hermes) o ambas cosas. Se crean desde el panel, desde Hermes (skill) o con una llamada directa al puente.

## Diseño

- **Puente (`homelab/clock-bridge`)**: módulo nuevo `reminders.py` (modelo, validación, almacén y cálculo de repeticiones, sin red) y rutas en `bridge.py`:
  - `POST /v1/notify` `{text, channel}`: aviso inmediato. `channel` es `clock`, `telegram` o `both`.
  - `GET /v1/reminders`, `POST /v1/reminders`, `DELETE /v1/reminders/{id}`.
  - Todas exigen el `BRIDGE_TOKEN` (503 sin configurar, 401 con token erróneo).
- **Planificador**: un hilo del puente que despierta cada 15 s, busca los recordatorios vencidos y los envía por su canal.
- **Persistencia**: un JSON (`/data/reminders.json`, volumen del contenedor) escrito de forma atómica (archivo temporal, `fsync`, `os.replace`).
- **Reloj**: el puente llama a la API que el reloj ya tiene (`POST /speak` y `POST /display`). No hace falta ningún endpoint nuevo ni tocar el APK. La voz la genera el `Tts` del reloj, que ya usa el audio del puente (Kokoro, Piper o Magpie) y cae al TTS del sistema.
- **Telegram**: llamada directa a `sendMessage` de la API de bots de Telegram con el mismo bot que usa Hermes. El chat y el token salen del entorno (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`), nunca del repositorio.
- **Panel**: tarjeta "Recordatorios" en la pestaña Alarmas (crear, listar, borrar), que pasa por `/api/reminders` del panel hacia el puente (`BRIDGE_URL`, `BRIDGE_TOKEN`).
- **Skill de Hermes**: `clock.py reminder-add|reminder-list|reminder-delete` y `clock_tools.reminder_*`, contra el puente.

## Modelo

`{id, text, at, repeat, channel, created}`. `at` es la próxima vez que debe sonar (ISO 8601 con desfase). `repeat`: `none`, `daily`, `weekdays`, `weekly`. Al crear se acepta `at` (`AAAA-MM-DDTHH:MM` en la hora local de `REMINDER_TZ`, o con desfase) o `in_minutes`.

## Seguridad

- Token obligatorio en todas las rutas (la comprobación en tiempo constante que ya existe).
- Cuerpo máximo de 8 KB leído en streaming (Content-Length se puede falsear), texto máximo de 300 caracteres, máximo de 200 recordatorios, `in_minutes` acotado.
- El texto de un recordatorio es dato no fiable: se limpian los caracteres de control, va a Telegram como texto plano (sin `parse_mode`), se habla con un prefijo fijo y **nunca se manda a Hermes ni a un LLM** (evita la inyección de instrucciones).
- Los logs INFO llevan solo ids, canal y longitudes. El logger de `httpx` sube a WARNING porque imprimiría la URL de Telegram, que lleva el token del bot.
- Los errores de envío no incluyen URLs ni cuerpos de respuesta.

## Decisiones

- Telegram directo y no a través del agente de Hermes: es determinista, rápido y sin riesgo de inyección; se reutiliza el bot de Hermes (mismo token y chat).
- Planificador en el puente y no en el panel: el panel no guarda estado y el puente ya es el servicio con estado.
- Un recordatorio de una sola vez que venció con el puente parado se envía igualmente; uno repetido se salta si lleva más de `REMINDER_GRACE_MIN` (10) minutos vencido y pasa a su próxima ocurrencia.
- Si un envío falla se reintenta en los ciclos siguientes (3 intentos) y después se descarta o avanza, para no repetir sin fin.
- Los de una sola vez se borran al sonar.
- Sin iconos nuevos: se usan `bell`, `clock`, `trash-2` y `plus`, que ya existen.
