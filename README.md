# Friday — Mi Smart Clock

Convierte el **Xiaomi Mi Smart Clock (X04G, MT8167, 1 GB RAM)** en un
despertador y asistente de voz propio, **sin Google**, cuyo cerebro es
**Hermes Agent** en tu homelab.

```
            Mi Smart Clock (app Friday)                          Homelab (Tailscale)
 ┌──────────────────────────────────────────────┐        ┌──────────────────────────────────────┐
 │ Carátula · modo noche · alarmas (AlarmManager)│        │ clock-bridge (:8765)                 │
 │ Órdenes offline: alarmas, temporizadores,     │  wav   │   /v1/stt    → faster-whisper (local)│
 │ hora, tiempo, volumen          ───────────────┼───────▶│   /v1/assist → Hermes /v1/responses  │
 │ Micro + VAD → puente → Hermes  ◀──────────────┼────────│                → Piper TTS (opcional)│
 │ TTS local o audio de Piper                    │ texto  │                                      │
 │ Servidor HTTP :8080 (token)    ◀──────────────┼────────│ Hermes Agent (:8642)                 │
 └──────────────────────────────────────────────┘ clock.py│   skill mi-smart-clock → scripts/clock.py
                                                           └──────────────────────────────────────┘
```

- **El despertador no depende de nada externo.** Las alarmas viven en
  `AlarmManager.setAlarmClock()`: si cae el homelab o la wifi, suena igual.
  Aguantan reinicios y cambios de hora.
- **Las órdenes de despertador se entienden en el propio reloj**, en español
  y sin LLM. Solo lo demás va a Hermes.
- **Hermes controla el reloj** desde cualquier canal (Telegram, cron, el propio
  reloj…) con la skill `mi-smart-clock`.
- **Cero dependencias en la app**: nada de AppCompat, Compose, OkHttp ni Google
  Play Services. Solo el framework de Android, para ir bien con 1 GB de RAM.

## Qué hace

### Despertador
- Alarmas únicas o repetitivas (L M X J V S D, entre semana, fines de semana).
- **Volumen progresivo** (rampa configurable) y **pantalla "amanecer"**: el brillo
  sube a la vez que el volumen.
- Posponer (minutos configurables); si nadie la toca, se pospone sola.
- Si la GSI no trae tonos, suenan pitidos sintetizados: nunca se queda muda.
- Sube el volumen de alarma al 70 % si lo encuentra más bajo.
- **Buenos días** al descartar: Hermes (o el reloj, sin red) dice la hora, el tiempo,
  tu agenda si Hermes tiene acceso a ella, y la próxima alarma.
- Temporizadores con etiqueta ("temporizador de 10 minutos para la pasta").

### Asistente
- Botón de micro, **doble toque** en la carátula o `POST /listen`.
- VAD en el reloj: corta solo cuando dejas de hablar.
- Voz → texto con Whisper local en el homelab; texto → voz con Piper en el homelab o
  con el TTS del reloj.
- **Modo conversación**: si Hermes te pregunta algo, el reloj vuelve a escuchar solo.
- Contexto para Hermes: hora, zona horaria, ubicación, tiempo y alarmas actuales.

### Carátula
- Hora grande, fecha, tiempo actual (Open-Meteo, sin API key ni Google) y próxima alarma.
- **Modo noche**: texto rojo tenue y brillo al mínimo en las horas que elijas;
  un toque la ilumina 15 s.
- Anti-quemado: la carátula se desplaza unos píxeles cada minuto.
- Capa `/display` para texto, HTML, imágenes o una web. El WebView se destruye al
  cerrarse para liberar RAM.

### Órdenes que funcionan sin red (y sin Hermes)
| Dices | Hace |
|---|---|
| "pon una alarma a las siete y media entre semana" | alarma 07:30 L-V |
| "despiértame a las 6:45" / "a las diez de la noche para la pastilla" | alarma puntual con etiqueta |
| "temporizador de 5 minutos para la pasta", "avísame en media hora" | temporizador |
| "quita la alarma de las 7:30", "cancela todas las alarmas", "cancela el temporizador" | borra |
| "¿qué alarmas tengo?" | las lee y dice cuál es la próxima |
| "pospón", "apaga la alarma" | posponer / descartar |
| "¿qué hora es?", "¿qué día es?", "¿qué tiempo hace?" | responde al momento |
| "sube el volumen", "volumen al 40" | volumen |

Todo lo demás ("¿tengo algo mañana?", "recuérdame llamar a mamá el viernes",
"¿cómo va el servidor?"…) va a Hermes.

## 1. Instalar la app en el reloj

Requisitos: el reloj con una GSI limpia sin Google y `adb` operativo.

**Opción A: APK de GitHub Actions.** Cada push compila el APK en la pestaña
*Actions → Build APK → Artifacts*.

**Opción B: compilar tú.** JDK 17 y Android SDK (Android Studio lo trae):

```bash
./gradlew assembleRelease          # APK optimizado (firmado con la clave debug)
adb install -r app-android/build/outputs/apk/release/app-android-release.apk
```

Tras instalar:

1. Pulsa Home y elige **Friday** como launcher → "Siempre". También puedes
   ir a Ajustes → *Elegir launcher predeterminado*.
2. Acepta los permisos de **micrófono** y **notificaciones**.
3. En Android 12, si la carátula lo pide, concede **Alarmas y recordatorios**.
   En Android 13+ se concede solo.
4. **Voz del reloj.** Una GSI limpia puede venir sin motor de TTS. Instala
   **RHVoice** o **eSpeak NG** desde F-Droid, con voz en español. Otra opción es
   configurar Piper en el puente para que el reloj solo reproduzca el audio.
5. Instala **Tailscale** en el reloj para llegar al homelab sin abrir puertos.
6. En **Ajustes** (icono de engranaje o pulsación larga en la hora):
   - copia la **dirección** y el **token** del reloj; los necesitará Hermes;
   - pon la **URL del puente** (`http://<ip-tailscale-homelab>:8765`) y su token;
   - ajusta ciudad y coordenadas, modo noche, posponer y rampa;
   - pulsa **Probar conexión con Hermes** y **Probar alarma**.

El token del reloj se genera aleatorio en el primer arranque (adiós al `changeme`).

## 2. Hermes: activar su API

En `~/.hermes/.env`:

```bash
API_SERVER_ENABLED=true
API_SERVER_KEY=una-clave-larga
```

y arranca `hermes gateway` (escucha en `127.0.0.1:8642`).

## 3. Puente del homelab (`homelab/clock-bridge`)

Hace de oído y de boca: Whisper y Piper corren en tu máquina. Habla con Hermes
por su API `/v1/responses` usando una conversación con nombre por reloj. Si el
reloj lleva 15 min callado, empieza una conversación nueva.

```bash
cd homelab/clock-bridge
cp .env.example .env         # rellena BRIDGE_TOKEN y HERMES_API_KEY
docker compose up -d --build
# o sin Docker:
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
set -a; . ./.env; set +a; python bridge.py
```

- `WHISPER_MODEL=small` va bien en CPU. Con GPU: `medium` + `WHISPER_DEVICE=cuda`
  y `WHISPER_COMPUTE=float16`.
- Voz neural opcional: descarga una voz de Piper en español (por ejemplo
  `es_ES-davefx-medium.onnx` y su `.onnx.json`) a `./models` y pon `PIPER_MODEL`.

## 4. Skill de Hermes (`homelab/hermes-skill/mi-smart-clock`)

Con ella Hermes pone alarmas, habla o muestra cosas en el reloj desde cualquier
canal. Por ejemplo: "cuando llegue el paquete, avísame en el reloj", o un cron
que te enseñe la agenda al despertar.

```bash
cp -r homelab/hermes-skill/mi-smart-clock ~/.hermes/skills/
cat > ~/.config/mi-smart-clock.json <<'JSON'
{"url": "http://<ip-tailscale-reloj>:8080", "token": "<token del reloj>"}
JSON
python3 ~/.hermes/skills/mi-smart-clock/scripts/clock.py status   # prueba
```

La skill necesita el toolset `terminal` de Hermes. En lugar del JSON también
valen las variables `CLOCK_URL` y `CLOCK_TOKEN`.

## API HTTP del reloj (puerto 8080)

Autenticación: cabecera `X-Auth-Token: <token>` (o `Authorization: Bearer`, o `?token=`).

| Método y ruta | Cuerpo | Qué hace |
|---|---|---|
| `GET /health` | — | vivo (sin token) |
| `GET /status` | — | hora, próxima alarma, si suena, estado del asistente, alarmas |
| `GET /alarms` | — | lista de alarmas y temporizadores |
| `POST /alarm` | `{"action":"set","time":"07:30","label":"Trabajo","days":"weekdays"}` | crear (`days`: `""`, `daily`, `weekdays`, `weekend`, `["mon","fri"]`) |
| `POST /alarm` | `{"action":"cancel","id":3}` · `{"time":"07:30"}` · `{"all":true}` | borrar |
| `POST /alarm` | `{"action":"enable"/"disable","id":3}` · `{"action":"stop"/"snooze"}` | activar, desactivar, apagar o posponer |
| `POST /timer` | `{"minutes":10,"label":"Pasta"}` · `{"action":"cancel"}` | temporizador |
| `POST /display` | `{"type":"text"/"html"/"image"/"url","content":"…","duration_ms":20000}` · `{"action":"clear"}` | pantalla completa |
| `POST /speak` | `{"text":"Hola"}` | decir en voz alta |
| `POST /ask` | `{"text":"¿qué tiempo hace?","speak":true}` | como si se lo dijeras al reloj; devuelve `reply` |
| `POST /listen` | — | abre el micro |
| `POST /briefing` | — | buenos días hablados |
| `POST /volume` · `/brightness` | `{"level":40}` | volumen multimedia y brillo |
| `POST /actions` | `{"actions":[{"type":"set_alarm",…},…]}` | lote de acciones |

```bash
curl -X POST http://<ip-reloj>:8080/alarm -H "X-Auth-Token: <token>" \
  -H "Content-Type: application/json" \
  -d '{"action":"set","time":"07:30","label":"Despertar","days":"weekdays"}'
```

## Estructura

```
app-android/src/main/java/com/jarvis/clock/
  MainActivity.kt        launcher: carátula, modo noche, panel del asistente, /display
  ClockService.kt        servicio en primer plano con el servidor HTTP
  Prefs.kt               ajustes (token, puente, ciudad, noche, alarma…)
  alarm/                 modelo, persistencia, AlarmManager, sonido y pantalla de alarma
  assistant/             Assistant (orquestador), LocalIntents (español offline),
                         VoiceRecorder (VAD), Tts, Backend (puente), Actions
  server/                HttpServer mínimo + rutas de la API
  ui/                    Alarmas y Ajustes (sin XML)
  weather/               Open-Meteo
homelab/clock-bridge/    puente Whisper → Hermes → Piper (FastAPI)
homelab/hermes-skill/    skill de Hermes para controlar el reloj
```

## Siguientes pasos

- **Palabra de activación** ("Oye Jarvis") sin tocar la pantalla: openWakeWord en el
  puente con el audio del reloj en streaming, o un modelo pequeño en el propio reloj.
- Botones físicos del reloj: gesto en los de volumen para abrir el micro.
- Reproducir radio o podcasts al despertar (Music Assistant o Jellyfin vía Hermes).


## Estructura del repositorio

```
app-android/     App del reloj (Kotlin, sin dependencias de runtime)
hermes-skill/    Skill y tools de Hermes para controlar el reloj + tests
homelab/         clock-bridge: Whisper (STT) -> Hermes -> Piper (TTS)
device-kit/      Scripts para rootear/liberar el reloj (DRY_RUN por defecto)
docs/            research-notes.md, decisions.md y voz-friday.md
RUNBOOK.md       Fase 4: pasos con el reloj delante
PLAN.md          El plan completo
```

## Documentación

- **Rootear y quitar Google:** [`RUNBOOK.md`](RUNBOOK.md) + scripts en `device-kit/`.
- **Ponerle voz a Friday (paso a paso):** [`docs/voz-friday.md`](docs/voz-friday.md).
- **Por qué cada decisión:** [`docs/decisions.md`](docs/decisions.md) y
  [`docs/research-notes.md`](docs/research-notes.md).
- **Cerebro (Hermes):** [`hermes-skill/README.md`](hermes-skill/README.md).
