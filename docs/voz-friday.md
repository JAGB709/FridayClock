# Ponerle voz a Friday — paso a paso

Friday tiene **dos voces** que se configuran por separado. Puedes montar solo una o las dos:

1. **Voz en el reloj** (mesilla): hablas al reloj y te contesta por su altavoz.
   La conversión voz↔texto ocurre en **tu homelab**, sin Google ni nubes de voz.
2. **Voz de Friday en otros canales de Hermes** (p.ej. notas de voz en Telegram):
   la gestiona Hermes con su propio STT/TTS.

Todo el "cerebro" es Hermes; aquí solo le damos oídos y boca.

```
Tú hablas ─► Reloj (graba, VAD) ─► clock-bridge (homelab)
                                     │  Whisper  (voz→texto, local)
                                     ▼
                                   Hermes  (Friday razona y decide)
                                     │
                                     ▼
                                   Piper   (texto→voz, local)  ─► Reloj lo reproduce
```

---

## Parte A — Voz de Friday en el reloj (recomendada)

### A0. Requisitos

- El puente `homelab/clock-bridge` corriendo (ver `README.md`).
- Hermes con su API activada (`API_SERVER_ENABLED=true`, `hermes gateway`).
- El reloj con el permiso de **micrófono** concedido y la **URL del puente** puesta en Ajustes.

### A1. Oído: Whisper (voz → texto), en local

En `homelab/clock-bridge/.env`:

```bash
WHISPER_MODEL=small       # tiny/base/small/medium. small va bien en CPU
WHISPER_DEVICE=cpu        # con GPU NVIDIA: cuda
WHISPER_COMPUTE=int8      # con GPU: float16
LANGUAGE=es
```

El puente descarga el modelo la primera vez. Pruébalo:

```bash
cd homelab/clock-bridge
# graba 3 s de "hola Friday" en un wav de 16 kHz mono, o usa cualquier wav en español
curl -s -X POST "http://localhost:8765/v1/stt" \
  -H "Authorization: Bearer $BRIDGE_TOKEN" \
  --data-binary @prueba.wav
# -> {"text":"hola Friday"}
```

### A2. Boca: Piper (texto → voz), en local, **voz femenina española**

Piper es TTS neural offline. Las voces españolas viven en el repo
[`rhasspy/piper-voices`](https://huggingface.co/rhasspy/piper-voices/tree/main/es/es_ES)
y las puedes **escuchar antes** en las [muestras de Piper](https://rhasspy.github.io/piper-samples/)
(filtra por *Spanish, Spain*). Elige la que más te guste para Friday (hay voces de
mujer y de hombre) y fíjate en su nombre (p.ej. `sharvard`, `davefx`, `carlfm`,
`mls_10246`) y su calidad (`low`/`medium`/`high`).

Cada voz son **dos ficheros** con este patrón de nombre:

```bash
cd homelab/clock-bridge && mkdir -p models && cd models
VOZ=sharvard          # <- pon aquí la voz elegida en las muestras
CAL=medium            # low | medium | high (según exista para esa voz)
BASE="https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_ES/$VOZ/$CAL"
curl -L -o "es_ES-$VOZ-$CAL.onnx"      "$BASE/es_ES-$VOZ-$CAL.onnx?download=true"
curl -L -o "es_ES-$VOZ-$CAL.onnx.json" "$BASE/es_ES-$VOZ-$CAL.onnx.json?download=true"
```

> Comprueba en la página de muestras qué voces son femeninas (Friday lo es) y qué
> calidades existen para cada una; no todas tienen `medium`. El `.onnx` y su `.onnx.json`
> deben ir siempre juntos.

En `.env`:

```bash
PIPER_MODEL=/models/es_ES-sharvard-medium.onnx   # el nombre de la voz que descargaste; docker-compose monta ./models en /models
```

Reinicia el puente (`docker compose up -d` o relanza `bridge.py`). A partir de ahora el
puente devuelve el audio ya sintetizado y el reloj lo reproduce tal cual.

> **Sin Piper**, el reloj usa el **TTS del propio Android**. Como una GSI limpia puede venir
> sin motor, instala en el reloj **RHVoice** o **eSpeak NG** (F-Droid) con voz española, o
> monta Piper y así no dependes del reloj. En Ajustes del reloj hay un acceso a los ajustes
> de voz del sistema y un control de velocidad.

### A3. Personalidad de Friday

La persona ya va inyectada por el puente en cada petición a Hermes (fichero
`homelab/clock-bridge/bridge.py`, constante `VOICE_INSTRUCTIONS`): responde en español,
breve, sin markdown, y con el nombre del asistente que tenga el reloj. El nombre por
defecto es **Friday**; puedes cambiarlo en Ajustes del reloj o por ADB:

```bash
adb shell am broadcast -a com.jarvis.clock.CONFIGURE -n com.jarvis.clock/.ConfigReceiver \
  --es assistant_name "Friday"
```

Si quieres afinar el carácter (más seca, con humor, etc.), edita `VOICE_INSTRUCTIONS`.
Para darle memoria y contexto ("¿qué tengo mañana?"), configura en Hermes las skills y
conectores que quieras (calendario, notas…): Friday los usará al responder por el reloj.

### A4. Probar la conversación completa

En el reloj: pulsa el botón del micro (o doble toque en la hora) y di *"Friday, ¿qué
tiempo hace?"*. Deberías ver "Te escucho… / Pensando…" y oír la respuesta. Si Friday
acaba con una pregunta, el reloj vuelve a escuchar solo (configurable en Ajustes).

Desde fuera también puedes provocarla:

```bash
curl -s -X POST "http://<ip-reloj>:8080/ask" -H "X-Auth-Token: $CLOCK_TOKEN" \
  -H "Content-Type: application/json" -d '{"text":"cuéntame un chiste","speak":true}'
```

---

## Parte B — Voz de Friday en Telegram y otros canales de Hermes

Esto es independiente del reloj: es la voz de Friday cuando le hablas por Telegram, etc.

### B1. STT y TTS en Hermes

En `~/.hermes/config.yaml`:

```yaml
stt:
  provider: nous        # transcribe tus notas de voz (Whisper vía Nous)
tts:
  provider: nous        # Friday te contesta con nota de voz
```

- `provider: nous` usa el **Tool Gateway** de Nous (una suscripción, sin más claves).
- ¿Sin Nous? Usa BYOK: `stt.provider: openai` (con `VOICE_TOOLS_OPENAI_KEY`) para Whisper,
  y para TTS tu clave de ElevenLabs u OpenAI. (Piper no está integrado como proveedor de
  TTS de Hermes; para voz 100% local, la Parte A con el reloj es el camino.)

Aplica el picker interactivo si prefieres: `hermes tools`.

### B2. Probar

Envía una **nota de voz** a Friday por Telegram: la transcribe y, con `tts.provider`
puesto, te responde con otra nota de voz. Referencias:
`hermes-agent` → `messaging/telegram.md` y `features/tool-gateway.md`.

---

## Resumen de decisiones de voz

| Dónde | Oído (STT) | Boca (TTS) | Dónde corre |
|---|---|---|---|
| Reloj de la mesilla | Whisper (`clock-bridge`) | Piper (`clock-bridge`) o TTS de Android | Tu homelab / el reloj |
| Telegram, etc. | Hermes `stt.provider` | Hermes `tts.provider` | Nous o BYOK |

Con la Parte A tienes a Friday hablando **sin depender de Google ni de ninguna nube de voz**:
Whisper y Piper viven en tu homelab y solo el razonamiento pasa por Hermes.
