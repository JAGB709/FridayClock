# Friday — Mi Smart Clock

Convierte el **Xiaomi Mi Smart Clock (X04G, MT8167, 1 GB RAM)** en un despertador y
asistente de voz propio llamado **Friday**, **sin Google**, cuyo cerebro es **Hermes
Agent + Qwen (Ollama)** en tu homelab. Reloj y homelab se hablan por **Tailscale**.

```
            Mi Smart Clock (app Friday)                          Homelab (Tailscale)
 ┌──────────────────────────────────────────────┐        ┌──────────────────────────────────────┐
 │ Carátula · modo noche · alarmas (AlarmManager)│        │ clock-bridge (:8765)                 │
 │ Órdenes offline: alarmas, temporizadores,     │  wav   │   /v1/stt    → faster-whisper (local)│
 │ hora, tiempo, volumen          ───────────────┼───────▶│   /v1/assist → Hermes /v1/responses  │
 │ Micro + VAD → puente → Hermes  ◀──────────────┼────────│                → Piper TTS (voz)     │
 │ Altavoz: voz de Friday + Bluetooth (móvil)    │ texto  │                                      │
 │ Servidor HTTP :8080 (token)    ◀──────────────┼────────│ Hermes Agent (:8642) ── Ollama :11434│
 └──────────────────────────────────────────────┘ clock.py│   skill mi-smart-clock          Qwen │
                                                           └──────────────────────────────────────┘
```

- **El despertador no depende de nada externo.** Las alarmas viven en
  `AlarmManager.setAlarmClock()`: si cae el homelab o la wifi, suenan igual, y
  aguantan reinicios y cambios de hora.
- **Las órdenes de despertador se entienden en el propio reloj**, en español y sin LLM.
  Solo lo demás va a Hermes.
- **Hermes controla el reloj** desde cualquier canal (Telegram, cron, el propio reloj…)
  con la skill `mi-smart-clock`.
- **Cero dependencias en la app**: nada de AppCompat, Compose, OkHttp ni Google Play
  Services. Solo el framework de Android, para ir bien con 1 GB de RAM.

> **Índice**
> 1. [Qué necesitas](#qué-necesitas)
> 2. [Parte A — Montar el cerebro en el homelab](#parte-a--montar-el-cerebro-en-el-homelab)
>    (Ollama+Qwen · Hermes · voz Whisper/Piper · Tailscale · skill · panel web)
> 3. [Parte B — Liberar el reloj e instalar Friday](#parte-b--liberar-el-reloj-e-instalar-friday)
>    (GSI/root · Tailscale · app · voz del reloj · Bluetooth)
> 4. [Parte C — Conectarlo todo y probar](#parte-c--conectarlo-todo-y-probar)
> 5. [Referencia: API HTTP](#api-http-del-reloj-puerto-8080) · [Estructura](#estructura-del-repositorio) · [Docs](#documentación)

---

## Qué necesitas

**Hardware**
- Un **Xiaomi Mi Smart Clock X04G** (variante global).
- Un **servidor/PC en el homelab** (el "cerebro"). Para Qwen en local va **mucho mejor
  con GPU**; en CPU funciona con un Qwen pequeño (ver más abajo). Linux recomendado.
- Un ordenador con **microUSB de datos** para flashear el reloj (Linux/macOS mejor).

**Cuentas / software**
- Una cuenta de **Tailscale** (gratis) para la red privada reloj↔homelab.
- **Ollama** (modelos locales) y **Hermes Agent** (el agente).
- Nada de Google. Nada de nube de voz: Whisper y Piper corren en tu homelab.

**Tiempo**: ~1–2 h el homelab, ~1–2 h el reloj (flasheo incluido).

---

## Parte A — Montar el cerebro en el homelab

Todo esto se hace en tu servidor de casa. Al final tendrás Qwen respondiendo por Hermes,
con voz, accesible por Tailscale.

### A1. Ollama + el modelo Qwen

```bash
# 1. Instala Ollama
curl -fsSL https://ollama.com/install.sh | sh

# 2. Descarga un modelo Qwen (elige según tu máquina):
ollama pull qwen2.5:7b-instruct       # equilibrado (GPU 8-12 GB o CPU con paciencia)
# ollama pull qwen2.5:14b-instruct    # mejor calidad, ~16 GB VRAM
# ollama pull qwen2.5:3b-instruct     # ligero, para CPU/poca RAM
```

**Importante — contexto:** Hermes necesita **≥ 64.000 tokens** de contexto o se niega a
arrancar, y Ollama por defecto usa muy poco (4.096 con <24 GB de VRAM). Arranca el
servidor de Ollama con el contexto subido y escuchando en la red (para que Tailscale
llegue):

```bash
sudo systemctl edit ollama    # crea un override; añade:
```
```ini
[Service]
Environment="OLLAMA_CONTEXT_LENGTH=65536"
Environment="OLLAMA_HOST=0.0.0.0:11434"
```
```bash
sudo systemctl restart ollama
curl http://localhost:11434/api/tags     # comprueba que responde y lista qwen
```

> En CPU, un contexto de 64k con 7B puede ir justo de RAM; si te quedas corto, usa
> `qwen2.5:3b-instruct`. La calidad baja, pero Friday sigue funcionando.

### A2. Instalar Hermes Agent

```bash
# Instalación solo-CLI (sin la app de escritorio)
curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash
exec $SHELL -l          # recarga el PATH para tener el comando `hermes`
hermes --version
```

### A3. Conectar Hermes a Qwen (Ollama local)

Lo más rápido, interactivo:

```bash
hermes setup            # elige "Blank Slate" o "Full Setup" y activa al menos
                        # los toolsets Terminal y File Operations
hermes model            # → "Custom Endpoint (OpenAI compatible)"
                        #   URL:   http://localhost:11434/v1
                        #   API key: (déjalo vacío, Ollama no la pide)
                        #   Modelo:  qwen2.5:7b-instruct
```

O directo en `~/.hermes/config.yaml`:

```yaml
model:
  default: qwen2.5:7b-instruct
  provider: custom
  base_url: http://localhost:11434/v1
  context_length: 64000
```

Prueba que Qwen responde:

```bash
hermes chat -q "Di 'hola' en una frase."
```

### A4. Activar la API de Hermes (para que el reloj le hable)

En `~/.hermes/.env`:

```bash
API_SERVER_ENABLED=true
API_SERVER_KEY=pon-aqui-una-clave-larga   # la reutiliza el puente
```

Arranca el gateway (deja este proceso corriendo; o instálalo como servicio):

```bash
hermes gateway          # escucha en 127.0.0.1:8642
# prueba en otra terminal:
curl http://localhost:8642/v1/models -H "Authorization: Bearer pon-aqui-una-clave-larga"
```

### A5. Voz de Friday: el puente Whisper + Piper (`homelab/clock-bridge`)

Es el oído (Whisper) y la boca (Piper) del reloj, en local. Habla con Hermes por su API.

```bash
git clone https://github.com/JAGB709/Mi-Smart-Clock.git
cd Mi-Smart-Clock/homelab/clock-bridge
cp .env.example .env
```

Edita `.env`:

```bash
BRIDGE_TOKEN=otro-token-largo            # lo pondrás también en el reloj
HERMES_URL=http://127.0.0.1:8642
HERMES_API_KEY=pon-aqui-una-clave-larga  # = API_SERVER_KEY de arriba
HERMES_MODEL=hermes-agent
WHISPER_MODEL=small                      # tiny/base/small/medium
WHISPER_DEVICE=cpu                       # con GPU NVIDIA: cuda
WHISPER_COMPUTE=int8                     # con GPU: float16
# PIPER_MODEL=/models/es_ES-<voz>-medium.onnx   # ver docs/voz-friday.md
```

**Voz en español (Piper):** descarga una voz — elígela escuchando las
[muestras de Piper](https://rhasspy.github.io/piper-samples/) (Spanish, Spain) — a
`homelab/clock-bridge/models/` y apunta `PIPER_MODEL` a ella. Paso a paso completo en
**[`docs/voz-friday.md`](docs/voz-friday.md)**. Sin Piper, el reloj usa su propio TTS.

Levántalo:

```bash
docker compose up -d --build     # recomendado
# o sin Docker:
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
set -a; . ./.env; set +a; python bridge.py
curl http://localhost:8765/health
```

### A6. Tailscale en el homelab

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
tailscale ip -4          # apunta esta IP (100.x.y.z): la usará el reloj
```

### A7. Instalar la skill del reloj en Hermes

Deja que Friday controle el reloj (alarmas, hablar, mostrar, Bluetooth) desde cualquier canal:

```bash
cp -r ~/Mi-Smart-Clock/hermes-skill/mi-smart-clock ~/.hermes/skills/
# la config del reloj (rellenarás la IP y el token en la Parte C):
cat > ~/.config/mi-smart-clock.json <<'JSON'
{"url": "http://<ip-tailscale-reloj>:8080", "token": "<token-del-reloj>"}
JSON
```

La skill usa el toolset `terminal` de Hermes.

### A8. Panel web para el móvil (`homelab/friday-panel`)

Para configurar alarmas y ajustes desde el móvil. El token del reloj se queda en el
homelab. Ver [`homelab/friday-panel/README.md`](homelab/friday-panel/README.md):

Variables de entorno (`.env`):

| Variable | Valor |
|---|---|
| `CLOCK_URL` | obligatoria, `http://100.x.y.z:8080` (IP Tailscale del reloj) |
| `CLOCK_TOKEN` | obligatoria, el token del reloj |
| `PANEL_BIND` | por defecto `127.0.0.1`; pon la IP de Tailscale del homelab (`tailscale ip -4`) |
| `PANEL_PORT` | `8770` |
| `PANEL_PASSWORD` | opcional; si la pones, el panel pide contraseña |
| `CLOCK_TIMEOUT_S` | `5` |

    cd homelab/friday-panel
    cat > .env <<'FIN'
    CLOCK_URL=http://100.x.y.z:8080
    CLOCK_TOKEN=
    PANEL_BIND=100.a.b.c
    PANEL_PORT=8770
    PANEL_PASSWORD=
    CLOCK_TIMEOUT_S=5
    FIN
    docker compose up -d --build

Abre `http://<ip-tailscale-homelab>:8770` en el móvil. Si el reloj está apagado o sin
red, el panel lo dice y no guarda nada; las alarmas siguen sonando igual.

**Fin de la Parte A.** Ya tienes cerebro (Qwen+Hermes), voz (Whisper+Piper) y red (Tailscale).

---

## Parte B — Liberar el reloj e instalar Friday

> Esto modifica el firmware del reloj. Hazlo con calma y **con backup**. El detalle con
> checkpoints y rollback está en **[`RUNBOOK.md`](RUNBOOK.md)**; los porqués, en
> [`docs/research-notes.md`](docs/research-notes.md) y [`docs/decisions.md`](docs/decisions.md).

### B1. Preparar el ordenador de flasheo

```bash
cd ~/Mi-Smart-Clock
./device-kit/setup_host.sh                      # instala mtkclient, adb, udev
git clone https://github.com/bkerler/mtkclient device-kit/vendor/mtkclient
pip3 install -r device-kit/vendor/mtkclient/requirements.txt
```

### B2. Backup completo (imprescindible) y root

Los scripts van en **DRY_RUN por defecto** (no escriben nada); repites con `DRY_RUN=0`
cuando lo veas claro. Resumen (detalle en `RUNBOOK.md`):

```bash
./device-kit/00_preflight.sh
DRY_RUN=0 ./device-kit/01_backup_full.sh        # copia TODAS las particiones + verifícalo
DRY_RUN=0 ./device-kit/02_unlock.sh             # desbloquea bootloader (borra userdata)
DRY_RUN=0 ./device-kit/03_flash_boot.sh         # boot rooteado con Magisk (opción B)
DRY_RUN=0 ./device-kit/04_verify_root.sh        # comprueba arranque + root
DRY_RUN=0 ./device-kit/05_debloat.sh            # DESHABILITA Google (reversible)
```

Si algo va mal: `DRY_RUN=0 LEVEL=2 ./device-kit/99_rollback.sh` (restaura el boot original).

### B3. Compilar/obtener el APK de Friday

- **Fácil:** descarga el artefacto `friday-apk` de **GitHub Actions → Build APK → Artifacts**.
- **Compilar tú** (JDK 17 + Android SDK):
  ```bash
  ./gradlew assembleRelease
  # APK en app-android/build/outputs/apk/release/app-android-release.apk
  ```

### B4. Instalar Friday y Tailscale en el reloj

Descarga el **APK de Tailscale** de [F-Droid](https://f-droid.org/packages/com.tailscale.ipn/)
(el reloj es Android 10, compatible) y déjalo en `device-kit/vendor/`. Luego:

```bash
DRY_RUN=0 CLOCK_TOKEN="$(head -c16 /dev/urandom | base64 | tr -dc a-z0-9 | head -c20)" \
  BACKEND_URL="http://<ip-tailscale-homelab>:8765" \
  BACKEND_TOKEN="<BRIDGE_TOKEN de A5>" \
  ASSISTANT_NAME="Friday" \
  ./device-kit/06_install_apps.sh
```

Ese script instala ambas apps, concede permisos, fija **Friday como launcher** y configura
el token por ADB. **Apunta el `CLOCK_TOKEN`** que se ha generado: lo necesita Hermes.

En el reloj: abre **Tailscale** e inicia sesión para unirlo a tu tailnet.

### B5. Voz del reloj (motor TTS)

Una GSI limpia puede venir **sin motor de voz**. Dos opciones:
- Usar **Piper** en el puente (Parte A5): el reloj solo reproduce el audio. Recomendado.
- O instalar en el reloj **RHVoice** / **eSpeak NG** (F-Droid) con voz española, para que
  hable aunque el puente no mande audio. En Ajustes del reloj hay acceso a la voz del
  sistema y a la velocidad.

### B6. Altavoz Bluetooth (usar el reloj como altavoz del móvil)

Ya integrado: dile a Friday **"activa el bluetooth"** (o Ajustes → *Modo altavoz Bluetooth*),
empareja el móvil buscando **"Friday"**, y su música sonará por el reloj. Que el audio se
enrute de verdad depende de que tu GSI traiga el perfil **A2DP sink**; cómo comprobarlo y
activarlo está en **[`docs/altavoz-bluetooth.md`](docs/altavoz-bluetooth.md)**.

---

## Parte C — Conectarlo todo y probar

1. **Dile a Hermes la IP y el token del reloj.** Edita `~/.config/mi-smart-clock.json`
   (Parte A7) con la IP de Tailscale del reloj (`tailscale ip -4` en el reloj o el panel
   de Tailscale) y el `CLOCK_TOKEN` de B4.

2. **Prueba el reloj desde el homelab (por Tailscale):**
   ```bash
   python3 ~/.hermes/skills/mi-smart-clock/scripts/clock.py status
   python3 ~/.hermes/skills/mi-smart-clock/scripts/clock.py say "Hola, soy Friday"
   ```

3. **Prueba la voz completa:** en el reloj, pulsa el micro (o doble toque en la hora) y di
   *"Friday, ¿qué tiempo hace?"*. Debe transcribir (Whisper), pensar (Qwen/Hermes) y
   responder por el altavoz (Piper o TTS del reloj).

4. **Prueba el despertador offline:** *"pon una alarma dentro de 2 minutos"* y comprueba que
   suena aunque apagues el homelab.

5. **Prueba que Friday controla el reloj:** por Telegram u otro canal de Hermes, *"muéstrame
   'Hola' en el reloj y pon una alarma a las 7"*.

Si algo no responde, empieza por `clock.py status` y por `curl .../health` del puente y de Hermes.

---

## Qué hace Friday

### Despertador
- Alarmas únicas o repetitivas (L M X J V S D, entre semana, fines de semana).
- **Volumen progresivo** y **pantalla "amanecer"**: el brillo sube con el volumen.
- Posponer configurable; si nadie la toca, se pospone sola. Pitidos de reserva si la GSI
  no trae tonos: nunca se queda muda. Sube el volumen de alarma si lo encuentra bajo.
- **Buenos días** al descartar: hora, tiempo, agenda (si Hermes la tiene) y próxima alarma.
- Temporizadores con etiqueta.

### Asistente
- Micro por botón, **doble toque** en la carátula o `POST /listen`; VAD que corta al callar.
- Voz→texto con Whisper local; texto→voz con Piper local o el TTS del reloj.
- **Modo conversación**: si Friday pregunta, el reloj vuelve a escuchar solo.

### Carátula
- Hora, fecha, tiempo (Open-Meteo, sin API key) y próxima alarma.
- **Modo noche** (rojo tenue, brillo mínimo) y anti-quemado.
- Capa `/display` para texto, HTML, imágenes o una web.

### Pantallas y temas

La carátula y la pantalla de alarma se describen en un diseño (JSON): **temas** (colores, letra,
fondo liso, degradado o imagen), **pantallas** en una rejilla de 8×6 con hora, fecha, tiempo,
previsión, próxima alarma y texto o frase del día, y **perfiles** por hora (Mañana, Día, Noche…)
con su tema, sus pantallas, rotación y brillo. Se edita desde la pestaña **Pantallas** del panel
web, con una vista previa idéntica a la del reloj y un botón **▶ Probar** que lo muestra 30 s en el
reloj sin guardarlo. Si el `WebView` del reloj fallase, vuelve la carátula nativa de siempre; las
alarmas no dependen de él.

### Órdenes que funcionan sin red (y sin Hermes)
| Dices | Hace |
|---|---|
| "pon una alarma a las siete y media entre semana" | alarma 07:30 L-V |
| "despiértame a las 6:45" / "a las diez de la noche para la pastilla" | alarma puntual con etiqueta |
| "temporizador de 5 minutos para la pasta", "avísame en media hora" | temporizador |
| "quita la alarma de las 7:30", "cancela todas las alarmas" | borra |
| "¿qué alarmas tengo?" | las lee y dice la próxima |
| "pospón", "apaga la alarma" | posponer / descartar |
| "¿qué hora es?", "¿qué día es?", "¿qué tiempo hace?" | responde al momento |
| "sube el volumen", "volumen al 40" | volumen |
| "activa el bluetooth", "modo altavoz" | modo altavoz Bluetooth |

Todo lo demás ("¿tengo algo mañana?", "recuérdame llamar a mamá el viernes"…) va a Hermes/Qwen.

---

## API HTTP del reloj (puerto 8080)

Autenticación: cabecera `X-Auth-Token: <token>` (o `Authorization: Bearer`, o `?token=`).
Sin token configurado, el servidor responde 503 a todo.

| Método y ruta | Cuerpo | Qué hace |
|---|---|---|
| `GET /health` | — | vivo (sin token) |
| `GET /status` | — | hora, próxima alarma, si suena, estado del asistente, Bluetooth, alarmas |
| `GET /alarms` | — | lista de alarmas y temporizadores |
| `POST /alarm` | `{"action":"set","time":"07:30","label":"Trabajo","days":"weekdays"}` | crear (`days`: `""`, `daily`, `weekdays`, `weekend`, `["mon","fri"]`) |
| `POST /alarm` | `{"action":"cancel","id":3}` · `{"time":"07:30"}` · `{"all":true}` | borrar |
| `POST /alarm` | `{"action":"enable"/"disable","id":3}` · `{"action":"stop"/"snooze"}` | activar, desactivar, apagar o posponer |
| `POST /alarm` | `{"action":"update","id":3,"time":"06:45","max_volume":60}` | editar solo lo enviado; opciones por alarma: `sound`, `max_volume` (1–100), `ramp_s` (0–600), `snooze_min` (1–60), `briefing`; `null` = ajuste general; `sound` = `file:<nombre>` o `system:content://…` (tonos del reloj; otras URLs se rechazan). El panel devuelve los errores como `{"ok":false,"error":…}` |
| `GET /settings` | — | ajustes (despertador, pantalla, voz, ubicación) y su esquema |
| `PUT /settings` | `{"snooze_min":10,"night_brightness":5}` | cambiar ajustes; todo o nada (`400` con `errors` por clave); se aplican al instante |
| `GET /sounds` | — | tonos del sistema y sonidos subidos |
| `POST /sounds?name=gallo.mp3` | audio en crudo (≤ 5 MB; mp3/ogg/wav/m4a) | subir un sonido de alarma |
| `DELETE /sounds/{nombre}` | — | borrar un sonido subido |
| `POST /sounds/preview` | `{"id":"file:gallo.mp3"}` | escuchar 5 s (`409` si suena una alarma) |
| `GET /design` | — | diseño de pantallas (temas, pantallas en rejilla 8×6, perfiles, pantalla de alarma) y `saved` |
| `PUT /design` | el diseño entero | validar y guardar (todo o nada: `400` con `errors` por ruta); se aplica al instante |
| `POST /design/reset` | — | restaurar el diseño inicial |
| `POST /design/preview` | `{"design":{…},"seconds":30}` | mostrar un diseño 5–120 s sin guardarlo (`409` si suena una alarma) |
| `DELETE /design/preview` | — | cancelar la prueba |
| `GET /backgrounds` · `POST /backgrounds?name=x.jpg` · `GET /backgrounds/{nombre}` · `DELETE /backgrounds/{nombre}` | imagen en crudo (≤ 2 MB, jpg/png/webp, ≤ 4096 px) | fondos de pantalla |
| `GET /face/data` | — | los datos que recibe el motor: tiempo, previsión y próxima alarma |
| `POST /timer` | `{"minutes":10,"label":"Pasta"}` · `{"action":"cancel"}` | temporizador |
| `POST /display` | `{"type":"text"/"html"/"image"/"url","content":"…","duration_ms":20000}` · `{"action":"clear"}` | pantalla completa |
| `POST /speak` | `{"text":"Hola"}` | decir en voz alta |
| `POST /ask` | `{"text":"¿qué tiempo hace?","speak":true}` | como si se lo dijeras al reloj; devuelve `reply` |
| `POST /listen` | — | abre el micro |
| `POST /briefing` | — | buenos días hablados |
| `POST /bluetooth` | `{"action":"speaker"/"on"/"off"/"status"}` | modo altavoz Bluetooth |
| `POST /volume` · `/brightness` | `{"level":40}` | volumen multimedia y brillo |
| `POST /actions` | `{"actions":[{"type":"set_alarm",…},…]}` | lote de acciones |

```bash
curl -X POST http://<ip-reloj>:8080/alarm -H "X-Auth-Token: <token>" \
  -H "Content-Type: application/json" \
  -d '{"action":"set","time":"07:30","label":"Despertar","days":"weekdays"}'
```

## Estructura del repositorio

```
app-android/     App Friday del reloj (Kotlin, sin dependencias de runtime)
app-android/src/main/assets/face/  motor de pantallas (HTML/JS), copiado a homelab/friday-panel/static/face/ con tools/sync-face.sh
hermes-skill/    Skill y tools de Hermes para controlar el reloj + tests
homelab/         clock-bridge: Whisper (STT) -> Hermes -> Piper (TTS)
homelab/friday-panel/  panel web (FastAPI + web móvil) para alarmas y ajustes
device-kit/      Scripts para rootear/liberar el reloj (DRY_RUN por defecto)
docs/            research-notes, decisions, voz-friday, altavoz-bluetooth
RUNBOOK.md       Liberar el reloj: pasos con el reloj delante
PLAN.md          El plan completo
```

## Documentación

- **Instalar todo:** esta guía (Partes A–C).
- **Liberar el reloj (root/GSI) con checkpoints:** [`RUNBOOK.md`](RUNBOOK.md) + `device-kit/`.
- **Ponerle voz a Friday (Whisper/Piper):** [`docs/voz-friday.md`](docs/voz-friday.md).
- **Altavoz Bluetooth (móvil→reloj):** [`docs/altavoz-bluetooth.md`](docs/altavoz-bluetooth.md).
- **Por qué cada decisión:** [`docs/decisions.md`](docs/decisions.md) y [`docs/research-notes.md`](docs/research-notes.md).
- **Cerebro (Hermes):** [`hermes-skill/README.md`](hermes-skill/README.md).

## Siguientes pasos

- **Palabra de activación** ("Oye Friday") sin tocar la pantalla: openWakeWord en el puente
  con el audio del reloj en streaming, o un modelo pequeño en el propio reloj.
- Botones físicos del reloj para abrir el micro.
- Reproductor de radio/podcasts por el altavoz (endpoint `/play`) para escuchar sin Bluetooth.
