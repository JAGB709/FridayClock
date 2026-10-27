# clock-bridge

Oídos y boca del reloj: Whisper (voz a texto), TTS, puerta de la palabra de activación, enrutado de preguntas hacia Hermes u Ollama y, además, avisos y recordatorios. La guía de instalación está en el [README de la raíz](../../README.md) (A5) y la voz en [`docs/voz-friday.md`](../../docs/voz-friday.md).

## Rutas

| Ruta | Qué hace |
|---|---|
| `GET /health` | vivo (sin token) |
| `POST /v1/stt` · `/v1/wake` · `/v1/assist` | voz del reloj |
| `POST /v1/notify` | aviso inmediato hablado en el reloj y/o por Telegram |
| `GET/POST /v1/reminders` · `DELETE /v1/reminders/{id}` | recordatorios con planificador y persistencia |

Todas, salvo `/health`, piden `Authorization: Bearer <BRIDGE_TOKEN>`.

## Variables de los recordatorios

Se añaden al `.env` del puente (el repositorio no contiene ninguno). Detalle y seguridad en [`docs/recordatorios.md`](../../docs/recordatorios.md).

| Variable | Qué es |
|---|---|
| `CLOCK_URL`, `CLOCK_TOKEN` | API del reloj (canal reloj: `POST /speak` y `/display`) |
| `TELEGRAM_BOT_TOKEN` | token del bot de Hermes (canal Telegram) |
| `TELEGRAM_CHAT_ID` | chat al que escribe el bot |
| `REMINDERS_FILE` | `/data/reminders.json` por defecto (volumen `./data`) |
| `REMINDER_TZ` | `Europe/Madrid` por defecto |
| `REMINDER_GRACE_MIN`, `REMINDER_TICK_S`, `REMINDERS_ENABLED` | margen (10), ciclo (15 s) e interruptor del planificador |

## Tests

```bash
pip install pytest fastapi httpx tzdata
pytest -q test_wake.py test_route.py test_calc.py test_reminders.py test_notify.py test_hardening.py
```
