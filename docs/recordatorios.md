# Recordatorios y avisos

Friday puede avisarte de un recordatorio **hablando en el reloj**, **escribiéndote por Telegram** (con el bot de Hermes) o con ambas cosas. Los guarda y los planifica el puente (`homelab/clock-bridge`); se crean desde el panel (pestaña Alarmas), desde Hermes (`clock.py reminder-add`) o llamando a la API del puente.

```text
panel / Hermes -> puente /v1/reminders -> reminders.json (volumen /data)
                  planificador (cada 15 s), por cada recordatorio vencido:
                    canal reloj    -> POST /speak y /display del reloj (voz con el audio del puente)
                    canal telegram -> sendMessage del bot de Hermes
```

No hay ningún endpoint nuevo en el reloj: se usan `POST /speak` y `POST /display`, que ya existen.

## Variables de entorno

Todas van en el `.env` de cada servicio (nunca en el repositorio; el chat y los tokens no se guardan en ningún archivo versionado). Si tu `.env` ya existe, añade las líneas que falten.

### Puente (`homelab/clock-bridge/.env`)

| Variable | Obligatoria | Qué es |
|---|---|---|
| `BRIDGE_TOKEN` | sí | Token de la API del puente (mínimo 16 caracteres). Ya lo usa el reloj. |
| `CLOCK_URL` | para el canal reloj | `http://100.x.y.z:8080`, IP Tailscale del reloj. |
| `CLOCK_TOKEN` | para el canal reloj | Token del reloj (Ajustes del reloj, Token). |
| `TELEGRAM_BOT_TOKEN` | para el canal Telegram | Token del bot de Hermes (el mismo que Hermes usa en su `~/.hermes/.env`). |
| `TELEGRAM_CHAT_ID` | para el canal Telegram | Identificador del chat al que escribe el bot (el de tu conversación con él). |
| `REMINDERS_FILE` | no | Ruta del JSON. Por defecto `/data/reminders.json` (volumen `./data` del compose). |
| `REMINDER_TZ` | no | Zona horaria de las fechas sin desfase. Por defecto `Europe/Madrid`. |
| `REMINDER_GRACE_MIN` | no | Minutos de margen: un recordatorio repetido que lleva más vencido se salta. Por defecto 10. |
| `REMINDER_TICK_S` | no | Cada cuántos segundos mira el planificador. Por defecto 15. |
| `REMINDERS_ENABLED` | no | `0` apaga el planificador (la API sigue). Por defecto `1`. |

Un canal sin configurar no rompe nada: ese canal devuelve `false` y se registra. Con `channel: both` el recordatorio cuenta como enviado si llegó por alguno, para no duplicar al reintentar.

### Panel (`homelab/friday-panel/.env`)

| Variable | Qué es |
|---|---|
| `BRIDGE_URL` | `http://127.0.0.1:8765` si el panel y el puente están en el mismo equipo (ambos usan `network_mode: host`). |
| `BRIDGE_TOKEN` | El mismo `BRIDGE_TOKEN` del puente. El navegador nunca lo ve. |

Sin estas dos el panel sigue funcionando y la tarjeta Recordatorios explica qué falta.

### Hermes y `clock.py`

`BRIDGE_URL` y `BRIDGE_TOKEN` en el entorno de Hermes, o `bridge_url` y `bridge_token` en `~/.config/mi-smart-clock.json`.

## API del puente

Todas las rutas piden `Authorization: Bearer <BRIDGE_TOKEN>` (503 si el puente no tiene token, 401 si no coincide).

| Ruta | Cuerpo | Qué hace |
|---|---|---|
| `POST /v1/notify` | `{"text":"...","channel":"clock\|telegram\|both"}` | Aviso inmediato. Devuelve `{"ok":true,"results":{"clock":true,"telegram":true}}`. |
| `GET /v1/reminders` | sin cuerpo | Lista, ordenada por la próxima vez que suenan. |
| `POST /v1/reminders` | `{"text","at"\|"in_minutes","repeat","channel"}` | Crea. `at` es `AAAA-MM-DDTHH:MM` (hora local de `REMINDER_TZ`) o ISO con desfase; `in_minutes` va de 1 a 525600; da uno de los dos. `repeat`: `none`, `daily`, `weekdays`, `weekly`. `channel` por defecto `clock`. |
| `DELETE /v1/reminders/{id}` | sin cuerpo | Borra (404 si no existe). |

Los de una sola vez se borran al sonar; los repetidos pasan a su próxima ocurrencia conservando la hora local (también al cambiar de horario de verano).

```bash
curl -X POST http://127.0.0.1:8765/v1/reminders -H "Authorization: Bearer $BRIDGE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"text":"Sacar la pasta","in_minutes":20,"channel":"both"}'
```

## Límites y seguridad

- Token obligatorio en todo. Cuerpo máximo de 8 KB (leído en streaming), texto de 300 caracteres, 200 recordatorios, `in_minutes` acotado.
- **El texto es dato no fiable.** Se le quitan los caracteres de control, se habla con el prefijo fijo "Recordatorio." y se envía a Telegram como texto plano (sin Markdown ni HTML). No pasa nunca por Hermes ni por un modelo, así que no puede darle órdenes a nadie.
- Los logs INFO solo llevan ids, canal, resultado y longitudes, nunca el texto (distinto de `LOG_TRANSCRIPTS`, que no afecta a esto). El logger de `httpx` está en WARNING porque registraría la URL de Telegram con el token del bot.
- Los fallos de envío se reintentan en 3 ciclos y después se descartan (o el repetido avanza), para no insistir sin fin.
- La skill de Hermes avisa de que el texto no debe salir de correos, webs o mensajes de terceros ni llevar secretos.
- Persistencia: JSON escrito de forma atómica (archivo temporal, `fsync`, `os.replace`); si el archivo está corrupto se aparta como `reminders.json.corrupto` y se empieza vacío.

## Probar a mano

1. Con el `.env` del puente rellenado: `docker compose up -d --build` en `homelab/clock-bridge`.
2. `curl -X POST http://127.0.0.1:8765/v1/notify -H "Authorization: Bearer $BRIDGE_TOKEN" -H "Content-Type: application/json" -d '{"text":"Prueba","channel":"both"}'`: el reloj lo dice y llega el mensaje a Telegram.
3. Crea un recordatorio para dentro de 2 minutos (panel o `curl`) y espera.
4. Reinicia el puente con uno pendiente y comprueba que sigue en la lista (`GET /v1/reminders`).

## Tests

```bash
cd homelab/clock-bridge && pytest -q test_reminders.py test_notify.py
cd hermes-skill && pytest -q tests/test_reminders.py
cd homelab/friday-panel && pytest -q tests/test_reminders.py && node --test tests-js/reminders-core.test.js
```
