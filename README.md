<div align="center">

# Friday

### Un despertador y asistente de voz propio, sin Google, en un Xiaomi Mi Smart Clock

![Android](https://img.shields.io/badge/Android-10-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-3776AB?style=flat-square&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-bridge%20y%20panel-009688?style=flat-square&logo=fastapi&logoColor=white)
![Tailscale](https://img.shields.io/badge/Tailscale-red%20privada-242424?style=flat-square&logo=tailscale&logoColor=white)
![Dependencias](https://img.shields.io/badge/dependencias%20en%20la%20app-0-FFB454?style=flat-square)
![Idioma](https://img.shields.io/badge/idioma-espa%C3%B1ol-FFB454?style=flat-square)

<br>

<img src="docs/images/reloj-pantallas.png" alt="Tres pantallas de la carátula de Friday: día, previsión del tiempo y noche" width="100%">

<br>

**Despertador a prueba de fallos, asistente de voz que corre en tu casa y un panel web para el móvil.**
El cerebro es Hermes Agent con Qwen (Ollama) en tu homelab. Reloj y homelab se hablan por Tailscale.

</div>

---

## Índice

- [Qué es Friday](#qué-es-friday)
- [Capturas](#capturas)
- [Arquitectura](#arquitectura)
- [Cómo se ha construido](#cómo-se-ha-construido)
- [Herramientas y tecnologías](#herramientas-y-tecnologías)
- [Guía de instalación](#guía-de-instalación)
  - [Qué necesitas](#qué-necesitas)
  - [Parte A: montar el cerebro en el homelab](#parte-a-montar-el-cerebro-en-el-homelab)
  - [Parte B: liberar el reloj e instalar Friday](#parte-b-liberar-el-reloj-e-instalar-friday)
  - [Parte C: conectarlo todo y probar](#parte-c-conectarlo-todo-y-probar)
- [Qué hace Friday](#qué-hace-friday)
- [Seguridad y privacidad](#seguridad-y-privacidad)
- [Desarrollo y pruebas](#desarrollo-y-pruebas)
- [Solución de problemas](#solución-de-problemas)
- [Hoja de ruta](#hoja-de-ruta)
- [Documentación](#documentación)
- [Créditos y licencias](#créditos-y-licencias)

---

## Qué es Friday

El **Xiaomi Mi Smart Clock (X04G)** es un reloj de mesilla con pantalla táctil de 4 pulgadas, micrófono, altavoz y Android 10, pero atado a Google Assistant y a la nube de Xiaomi. **Friday** lo convierte en un aparato completamente tuyo:

| | |
|---|---|
| **Un despertador que no falla** | Las alarmas viven en el sistema (`AlarmManager.setAlarmClock()`), no en la red. Si se cae el homelab o el wifi, suenan igual. Aguantan reinicios y cambios de hora. |
| **Un asistente de voz privado** | La voz se transcribe (Whisper) y se sintetiza (Piper, Kokoro o Magpie) en tu propio servidor. Nada va a Google ni a ninguna nube de voz. |
| **Un cerebro que tú eliges** | Hermes Agent con un modelo Qwen local (Ollama). Entiende órdenes libres, usa herramientas y controla el reloj desde cualquier canal: Telegram, un cron o el propio reloj. |
| **Una carátula personalizable** | Temas, pantallas en rejilla, perfiles por hora, fotos, carruseles, capas y gestos. Se diseña desde el móvil con una vista previa idéntica a la del reloj. |
| **Un panel web para el móvil** | Alarmas con rueda de hora, ajustes, editor del diseño y el reloj en vivo. Mismo sistema de diseño que el reloj: negro, ámbar, Inter e iconos Lucide. |
| **Ligero a propósito** | La app no tiene ninguna dependencia de runtime (ni AppCompat, ni Compose, ni OkHttp, ni Play Services): solo el framework de Android, para ir bien con 1 GB de RAM. |

Principios de diseño que guían todo el proyecto:

1. **El despertador no depende de nada externo.** Lo demás puede fallar; despertarte, no.
2. **Lo importante se entiende en el propio reloj.** Las órdenes de alarmas, temporizadores, hora, tiempo y volumen se interpretan en español y sin modelo de lenguaje. Solo el resto va a Hermes.
3. **Privacidad por defecto.** Voz local, API protegida por token y escuchando solo por la red privada.
4. **Todo es reversible.** Los paquetes de Google se deshabilitan, nunca se desinstalan; hay backup completo y rollback.
5. **Si algo falla, algo más lo cubre.** Si el WebView de la carátula falla, vuelve la carátula nativa. Si la voz del puente no está, el reloj usa su propio TTS. Si la GSI no trae tonos, la alarma suena con pitidos.

---

## Capturas

El panel web, pensado primero para el móvil. De izquierda a derecha: Inicio (con el reloj en vivo), lista de alarmas, editor de alarma con la rueda de hora, editor de pantallas con la vista previa fija y Ajustes.

<img src="docs/images/panel-movil.png" alt="Cinco pantallas del panel web de Friday en un móvil" width="100%">

> [!NOTE]
> Las capturas usan datos de ejemplo de un reloj simulado. La carátula del reloj real muestra el diseño que tú elijas, con tus fotos si quieres.

---

## Arquitectura

Cuatro piezas independientes que se hablan por HTTP.

```mermaid
flowchart LR
    subgraph RELOJ["Mi Smart Clock (app Friday)"]
        direction TB
        A1["Carátula y modo noche"]
        A2["Alarmas (AlarmManager)"]
        A3["Órdenes sin red: alarmas, temporizadores, hora, tiempo, volumen"]
        A4["Micrófono, VAD y palabra de activación"]
        A5["Servidor HTTP :8080 con token"]
    end

    subgraph HOMELAB["Homelab (red privada Tailscale)"]
        direction TB
        B1["clock-bridge :8765<br/>Whisper (voz a texto)<br/>Piper, Kokoro o Magpie (texto a voz)"]
        B2["Hermes Agent :8642<br/>skill mi-smart-clock"]
        B3["Ollama :11434<br/>Qwen"]
        B4["friday-panel :8770<br/>panel web para el móvil"]
    end

    MOVIL["Móvil"]

    A4 -- "audio WAV" --> B1
    B1 -- "texto" --> B2
    B2 --> B3
    B1 -- "voz sintetizada" --> A3
    B2 -- "clock.py, API HTTP" --> A5
    B4 -- "API HTTP con token" --> A5
    MOVIL -- "navegador" --> B4
```

| Pieza | Dónde vive | Qué hace |
|---|---|---|
| **App Friday** (`app-android/`) | En el reloj | Launcher, carátula, alarmas, asistente, servidor HTTP. Kotlin, UI construida por código. |
| **Motor de pantallas** (`app-android/src/main/assets/face/`) | Dentro de la app (WebView) y copiado al panel | Dibuja la carátula y la pantalla de alarma a partir de un diseño JSON. La misma vista previa en el reloj y en el panel. |
| **clock-bridge** (`homelab/clock-bridge/`) | Homelab | Oídos y boca del reloj: Whisper, TTS, puerta de la palabra de activación y enrutado de las preguntas. |
| **Skill de Hermes** (`hermes-skill/`) | Homelab (en Hermes) | Da a Hermes el control del reloj: alarmas, hablar, mostrar, Bluetooth, ajustes y diseño. Incluye un plugin de tools tipadas para el canal del reloj, sin terminal ([`docs/hermes-seguro.md`](docs/hermes-seguro.md)). |
| **Panel web** (`homelab/friday-panel/`) | Homelab | Interfaz de móvil primero. El token del reloj no sale del servidor. |
| **Kit de dispositivo** (`device-kit/`) | Tu ordenador | Scripts numerados para liberar el reloj con backup y rollback. |

### El flujo de una pregunta por voz

```mermaid
sequenceDiagram
    participant T as Tú
    participant R as Reloj
    participant P as clock-bridge
    participant H as Hermes y Qwen
    T->>R: "Friday, qué tiempo hace"
    R->>R: Órdenes locales (alarma, hora, tiempo...)
    alt Lo entiende el reloj
        R-->>T: Responde al momento, sin red
    else Necesita al cerebro
        R->>P: Audio WAV
        P->>P: Whisper transcribe
        P->>H: Texto (o respuesta rápida directa a Ollama)
        H-->>P: Respuesta y acciones
        P->>P: Sintetiza la voz
        P-->>R: Texto, audio y acciones
        R-->>T: Habla y ejecuta las acciones
    end
```

### Una sola capa de acciones, tres puertas de entrada

En la app, `Actions.kt` es el único sitio que ejecuta acciones del reloj (alarmas, temporizadores, hablar, mostrar, volumen, Bluetooth, diseño...). Lo reutilizan:

- **Órdenes locales** (`LocalIntents.kt`): interpretan alarmas, temporizadores, hora, tiempo y volumen en español, sin red y sin modelo.
- **Respuestas del cerebro** (`Backend.kt`): lo que no entiende el reloj va al puente y a Hermes, que devuelve texto y una lista de acciones.
- **API HTTP** (`Api.kt`): lo que usan Hermes, el panel y `clock.py`. Documentada en [`docs/api.md`](docs/api.md).

---

## Cómo se ha construido

### 1. El punto de partida: liberar el hardware

El reloj es un **MediaTek MT8167** de cuatro núcleos Cortex-A35 con **1 GB de RAM**, pantalla táctil de 800x480, doble micrófono, altavoz mono y tres botones físicos. De fábrica corre Android Go 10 con el asistente de Google.

El camino elegido, con los porqués en [`docs/decisions.md`](docs/decisions.md) y las notas de investigación en [`docs/research-notes.md`](docs/research-notes.md):

- **Backup completo y verificado** de todas las particiones antes de tocar nada, con `mtkclient` en modo BROM.
- **Desbloquear el bootloader** y flashear un **`boot` parcheado con Magisk** a partir del `boot` propio del reloj (no el de terceros, que trae un root shell abierto en el puerto 1337).
- **Deshabilitar Google, nunca desinstalarlo** (`pm disable-user`), de forma reversible.
- Convertir a **Friday en el launcher** del reloj.
- Todo con scripts numerados (`00_preflight` a `07_smoke_test`, más `99_rollback`) que arrancan en **modo simulacro** (`DRY_RUN=1`) y piden escribir una palabra para los pasos destructivos.

### 2. La app del reloj

Kotlin 1.9 con `minSdk 26` y `targetSdk 34`, **sin dependencias de runtime**. La interfaz nativa se construye por código (`ui/Ui.kt`), sin layouts XML. Piezas principales:

- **Alarmas** (`alarm/`): `AlarmManager.setAlarmClock()`, servicio de sonido con volumen progresivo y pantalla de amanecer, `BootReceiver` que reprograma tras reiniciar. Nunca dependen de la red.
- **Asistente** (`assistant/`): grabación con detección de voz (VAD), palabra de activación "Friday", conversación continua, TTS del puente o del sistema.
- **Servidor HTTP** (`server/`): un servidor mínimo propio, con token. Por defecto escucha en todas las interfaces; activa en Ajustes "solo Tailscale" para limitarlo.
- **Carátula**: un motor HTML y JavaScript dentro de un `WebView`, con respaldo nativo si falla.
- **Clima** con Open-Meteo (sin clave de API), **Bluetooth** como altavoz del móvil y **brillo** con una regla propia entre panel y reloj.

### 3. El motor de pantallas

La carátula y la pantalla de alarma se describen en un **diseño JSON** validado de forma estricta (todo o nada) por `DesignSchema.kt`: temas, pantallas en una rejilla de 8 por 6, perfiles por hora y pantalla de alarma. El mismo motor, escrito en JavaScript clásico (el `WebView` del reloj es Chrome 74), corre en el reloj y, copiado con `tools/sync-face.sh`, en la vista previa del panel. Una versión (hash de los archivos del motor) permite avisar si reloj y panel difieren.

Tiene redes de seguridad a propósito: comprobación de que el motor arrancó, límite de tiempo de carga, recuperación si muere el proceso de dibujo y vuelta a la carátula nativa. **Las alarmas nunca dependen del WebView.**

### 4. El cerebro y la voz en el homelab

- **`clock-bridge`** (FastAPI): `/v1/stt` con faster-whisper, `/v1/wake` como puerta de la palabra de activación, `/v1/assist` para preguntar. Enruta cada pregunta: una calculadora resuelve aritmética hablada al instante, los temas que necesitan herramientas van a **Hermes**, y el resto va directo a **Ollama** con un prompt corto y sin razonamiento (de 9 s a 0,4 s en la medición del proyecto con una GTX 1060).
- **Hermes Agent** con **Qwen** local como modelo, y la **skill `mi-smart-clock`** para controlar el reloj desde cualquier canal.
- **Voz de salida** configurable con tres motores que se encadenan como respaldo: Magpie (NVIDIA), Kokoro y Piper.

### 5. El panel web

Una aplicación de **móvil primero**, en JavaScript vanilla sin librerías y con FastAPI como proxy: el token del reloj se queda en el servidor, las peticiones llevan una cabecera anti-CSRF y la contraseña es opcional. Tiene cuatro pestañas (Inicio con el reloj en vivo, Alarmas con rueda de hora, Pantallas con la vista previa fija y Ajustes) y pruebas automáticas que impiden que se cuelen emojis, colores sueltos o iconos inexistentes.

### 6. El sistema de diseño "Obsidiana"

Una sola fuente de verdad (`design-system/tokens.json`) genera los colores del reloj, del motor y del panel: negro verdadero (apto para OLED), un único acento ámbar, tipografía **Inter** con números tabulares e iconos **Lucide** con trazo uniforme. Los contrastes de todos los pares de color se comprueban en las pruebas. Más en [`docs/sistema-de-diseno.md`](docs/sistema-de-diseno.md).

### 7. El proceso de desarrollo

El proyecto se ha hecho por fases y subproyectos, cada uno con su especificación, su plan y sus pruebas, que se conservan en [`docs/superpowers/`](docs/superpowers/):

| Etapa | Contenido |
|---|---|
| Fases 0 a 4 | Investigación, app, skill, kit de dispositivo y ejecución en el reloj |
| Panel web | Alarmas personalizables, ajustes remotos y sonidos |
| Pantallas y temas | Motor de pantallas, editor y vista previa en vivo |
| Fotos y gestos | Fotos ajustables, carrusel, arrastrar elementos y deslizar para abrir Alarmas o Ajustes |
| Capas y fondo | Elementos superpuestos con orden de capas y carrusel como fondo de tema |
| Rediseño | Sistema de diseño Obsidiana, iconos Lucide, brillo y rediseño completo del panel |

Cada tarea pasó por revisión de especificación y de calidad, y cada subproyecto por una revisión final antes de integrarse. La integración continua ejecuta todas las pruebas y las comprobaciones de que lo generado está al día.

---

## Herramientas y tecnologías

| Área | Qué se ha usado |
|---|---|
| **Hardware** | Xiaomi Mi Smart Clock X04G (MediaTek MT8167, 1 GB de RAM, Android 10) |
| **Liberar el reloj** | `mtkclient` (modo BROM), Magisk (boot parcheado), `adb`, `fastboot`, scripts de shell con `shellcheck` |
| **App del reloj** | Kotlin 1.9, Android Gradle Plugin 8.5, Gradle, JDK 17, `AlarmManager`, `WebView`, JUnit 4 |
| **Motor de pantallas** | HTML, CSS y JavaScript clásico (ES2017, Chrome 74), `node --test` |
| **Cerebro** | Hermes Agent (Nous Research), Ollama, modelos Qwen |
| **Voz** | faster-whisper (STT), Piper, Kokoro y NVIDIA Magpie (TTS) |
| **Servicios del homelab** | Python 3.12, FastAPI, Uvicorn, httpx, Docker Compose |
| **Panel web** | JavaScript vanilla, CSS con variables, FastAPI |
| **Diseño** | Tokens propios en JSON, tipografía Inter (SIL OFL), iconos Lucide (ISC) |
| **Red** | Tailscale (red privada reloj, homelab y móvil) |
| **Clima** | Open-Meteo (gratis y sin clave) |
| **Calidad** | pytest, JUnit, `node --test`, Chrome sin cabeza para comprobación visual, GitHub Actions |
| **Asistente de desarrollo** | Claude Code (Anthropic), con flujo de especificación, plan, implementación y revisión |

---

## Guía de instalación

### Qué necesitas

**Hardware**

- Un **Xiaomi Mi Smart Clock X04G** (variante global).
- Un **servidor o PC en tu casa** (el "cerebro"). Para Qwen en local va **mucho mejor con GPU**; en CPU funciona con un Qwen pequeño. Linux recomendado.
- Un ordenador con **microUSB de datos** para liberar el reloj (Linux o macOS mejor).

**Cuentas y software**

- Una cuenta gratuita de **Tailscale** para la red privada reloj, homelab y móvil.
- **Ollama** (modelos locales) y **Hermes Agent** (el agente). Nada de Google y nada de nube de voz.
- **Docker** y **Git** en el homelab. **Docker Compose** es lo más cómodo.

**Tiempo estimado:** de una a dos horas el homelab y de una a dos horas el reloj, flasheo incluido.

> [!WARNING]
> La Parte B **modifica el firmware del reloj**. Hazla con calma y con el backup completo hecho y verificado. Es una modificación no oficial que puede anular la garantía y, si se hace mal, dejar el aparato inservible. Los scripts arrancan en modo simulacro y existe un rollback, pero la responsabilidad es tuya.

---

### Parte A: montar el cerebro en el homelab

Todo esto se hace en tu servidor de casa. Al final tendrás a Qwen respondiendo por Hermes, con voz y accesible por Tailscale.

#### A1. Ollama y el modelo Qwen

```bash
# 1. Instala Ollama
curl -fsSL https://ollama.com/install.sh | sh

# 2. Descarga un modelo Qwen (elige según tu máquina)
ollama pull qwen2.5:7b-instruct       # equilibrado (GPU de 8 a 12 GB, o CPU con paciencia)
# ollama pull qwen2.5:14b-instruct    # mejor calidad, unos 16 GB de VRAM
# ollama pull qwen2.5:3b-instruct     # ligero, para CPU o poca RAM
```

> [!IMPORTANT]
> **Contexto.** Hermes necesita **al menos 64.000 tokens** de contexto o se niega a arrancar, y Ollama usa por defecto muy poco (4.096 con menos de 24 GB de VRAM). Sube el contexto y haz que escuche en la red para que Tailscale llegue.

```bash
sudo systemctl edit ollama    # crea un override; añade lo siguiente
```

```ini
[Service]
Environment="OLLAMA_CONTEXT_LENGTH=65536"
Environment="OLLAMA_HOST=0.0.0.0:11434"
```

> [!WARNING]
> Ollama no tiene autenticación. Si lo haces escuchar en `0.0.0.0`, limita el puerto 11434 con el cortafuegos a Tailscale o a la red de Docker.

```bash
sudo systemctl restart ollama
curl http://localhost:11434/api/tags     # comprueba que responde y lista Qwen
```

En CPU, un contexto de 64k con un modelo de 7B puede ir justo de RAM: si te quedas corto, usa `qwen2.5:3b-instruct`. La calidad baja, pero Friday sigue funcionando. (El proyecto se ha probado también con Qwen 3.5 de 4B en una GTX 1060.)

#### A2. Instalar Hermes Agent

```bash
# Instalación solo CLI (sin la app de escritorio)
curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash
exec $SHELL -l          # recarga el PATH para tener el comando `hermes`
hermes --version
```

#### A3. Conectar Hermes a Qwen (Ollama local)

Lo más rápido, de forma interactiva:

```bash
hermes setup            # elige "Blank Slate" o "Full Setup" y activa al menos
                        # los toolsets Terminal y File Operations
hermes model            # elige "Custom Endpoint (OpenAI compatible)"
                        #   URL:     http://localhost:11434/v1
                        #   API key: (vacío, Ollama no la pide)
                        #   Modelo:  qwen2.5:7b-instruct
```

O directamente en `~/.hermes/config.yaml`:

```yaml
model:
  default: qwen2.5:7b-instruct
  provider: custom
  base_url: http://localhost:11434/v1
  context_length: 64000
```

Comprueba que Qwen responde:

```bash
hermes chat -q "Di 'hola' en una frase."
```

#### A4. Activar la API de Hermes (para que el reloj le hable)

En `~/.hermes/.env`:

```bash
API_SERVER_ENABLED=true
API_SERVER_KEY=pon-aqui-una-clave-larga   # la reutiliza el puente
```

Arranca el gateway (deja el proceso corriendo o instálalo como servicio):

```bash
hermes gateway          # escucha en 127.0.0.1:8642
# prueba en otra terminal:
curl http://localhost:8642/v1/models -H "Authorization: Bearer pon-aqui-una-clave-larga"
```

#### A5. La voz de Friday: el puente Whisper y TTS (`homelab/clock-bridge`)

Es el oído (Whisper) y la boca (Piper, Kokoro o Magpie) del reloj, en local. Habla con Hermes por su API.

```bash
git clone https://github.com/JAGB709/Mi-Smart-Clock.git
cd Mi-Smart-Clock/homelab/clock-bridge
```

Crea un archivo `.env` con el contenido siguiente (ajusta los valores):

```bash
BRIDGE_TOKEN=otro-token-largo            # lo pondrás también en el reloj
HERMES_URL=http://127.0.0.1:8642
HERMES_API_KEY=pon-aqui-una-clave-larga  # igual que API_SERVER_KEY de arriba
HERMES_MODEL=hermes-agent
WHISPER_MODEL=small                      # tiny, base, small o medium
WHISPER_DEVICE=cpu                       # con GPU NVIDIA: cuda
WHISPER_COMPUTE=int8                     # con GPU: float16
LANGUAGE=es
# TTS_ENGINE=piper                       # piper (por defecto), kokoro o magpie
# PIPER_MODEL=/models/es_ES-sharvard-medium.onnx
```

**Voz en español.** Descarga una voz de Piper (elígela escuchando las [muestras de Piper](https://rhasspy.github.io/piper-samples/), filtrando por Spanish, Spain) a `homelab/clock-bridge/models/` y apunta `PIPER_MODEL` a ella. El paso a paso completo, con Kokoro y la personalidad de Friday, está en [`docs/voz-friday.md`](docs/voz-friday.md). Sin motor de voz en el puente, el reloj usa su propio TTS.

| Motor | Variable | Notas |
|---|---|---|
| **Piper** | `TTS_ENGINE=piper` y `PIPER_MODEL` | Neuronal y offline. Voces multihablante con `PIPER_SPEAKER` (en `sharvard`, 0 es hombre y 1 es mujer). |
| **Kokoro** | `TTS_ENGINE=kokoro`, `KOKORO_VOICE=ef_dora` | Más natural. Los modelos van en `./models`. |
| **Magpie** (NVIDIA) | `TTS_ENGINE=magpie`, `MAGPIE_URL`, `MAGPIE_KEY`, `MAGPIE_VOICE`, `MAGPIE_LANG` | Servido con `nemo-speech serve`. Usa el **índice** del hablante, no el nombre. Ocupa cerca de 1 GB de VRAM. |

Cada motor cae en el siguiente si falla. Levántalo:

```bash
docker compose up -d --build     # recomendado
# o sin Docker:
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
set -a; . ./.env; set +a; python bridge.py
curl http://localhost:8765/health
```

#### A6. Tailscale en el homelab

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
tailscale ip -4          # apunta esta IP (100.x.y.z): la usará el reloj
```

#### A7. Instalar la skill del reloj en Hermes

Deja que Friday controle el reloj (alarmas, hablar, mostrar, Bluetooth, ajustes y diseño) desde cualquier canal:

```bash
cp -r ~/Mi-Smart-Clock/hermes-skill/mi-smart-clock ~/.hermes/skills/
# la configuración del reloj (rellenarás la IP y el token en la Parte C)
cat > ~/.config/mi-smart-clock.json <<'JSON'
{"url": "http://<ip-tailscale-reloj>:8080", "token": "<token-del-reloj>"}
JSON
```

La skill usa el toolset `terminal` de Hermes.

#### A8. El panel web para el móvil (`homelab/friday-panel`)

Para configurar alarmas, ajustes y el diseño del reloj desde el móvil. El token del reloj se queda en el homelab.

| Variable (`.env`) | Valor |
|---|---|
| `CLOCK_URL` | Obligatoria. `http://100.x.y.z:8080` (IP Tailscale del reloj). |
| `CLOCK_TOKEN` | Obligatoria. El token del reloj. |
| `PANEL_BIND` | Por defecto `127.0.0.1`. Pon la IP de Tailscale del homelab (`tailscale ip -4`). |
| `PANEL_PORT` | `8770` |
| `PANEL_PASSWORD` | Opcional. Si la pones, el panel pide contraseña. |
| `CLOCK_TIMEOUT_S` | `5` |

```bash
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
```

Abre `http://<ip-tailscale-homelab>:8770` en el móvil. Puedes añadirlo a la pantalla de inicio como una aplicación. Si el reloj está apagado o sin red, el panel lo dice y no guarda nada; las alarmas siguen sonando igual.

**Fin de la Parte A.** Ya tienes cerebro (Qwen y Hermes), voz (Whisper y TTS) y red (Tailscale).

---

### Parte B: liberar el reloj e instalar Friday

> [!WARNING]
> Esto modifica el firmware. El detalle, con puntos de control y rollback, está en [`RUNBOOK.md`](RUNBOOK.md). Los porqués están en [`docs/research-notes.md`](docs/research-notes.md) y [`docs/decisions.md`](docs/decisions.md).

#### B1. Preparar el ordenador de flasheo

```bash
cd ~/Mi-Smart-Clock
./device-kit/setup_host.sh                      # instala mtkclient, adb y udev
git clone https://github.com/bkerler/mtkclient device-kit/vendor/mtkclient
pip3 install -r device-kit/vendor/mtkclient/requirements.txt
```

#### B2. Backup completo (imprescindible) y root

Los scripts van en **modo simulacro por defecto** (no escriben nada). Repites con `DRY_RUN=0` cuando lo veas claro. Resumen (detalle en `RUNBOOK.md`):

```bash
./device-kit/00_preflight.sh
DRY_RUN=0 ./device-kit/01_backup_full.sh        # copia TODAS las particiones y las verifica
DRY_RUN=0 ./device-kit/02_unlock.sh             # desbloquea el bootloader (borra los datos)
DRY_RUN=0 ./device-kit/03_flash_boot.sh         # boot rooteado con Magisk (opción B)
DRY_RUN=0 ./device-kit/04_verify_root.sh        # comprueba arranque y root
DRY_RUN=0 ./device-kit/05_debloat.sh            # DESHABILITA Google (reversible)
```

Si algo va mal: `DRY_RUN=0 LEVEL=2 ./device-kit/99_rollback.sh` restaura el boot original.

#### B3. Obtener el APK de Friday

Elige una de las tres formas:

1. **Descargar el release** (lo más fácil): baja `Friday-clock-v2.0.apk` desde la sección **Releases** del repositorio.
2. **Descargar el artefacto de la CI:** en **GitHub Actions, Build APK, Artifacts**, el artefacto `friday-apk`.
3. **Compilarlo tú** con JDK 17 y el SDK de Android:
   ```bash
   ./gradlew assembleRelease
   # APK en app-android/build/outputs/apk/release/app-android-release.apk
   ```

> [!NOTE]
> El APK se firma con la clave de depuración a propósito, para poder instalarlo sin tener un keystore propio. Para actualizar una instalación existente con `adb install -r` el APK debe estar firmado con la misma clave que el instalado; si no, desinstala primero.

#### B4. Instalar Friday y Tailscale en el reloj

Descarga el **APK de Tailscale** de [F-Droid](https://f-droid.org/packages/com.tailscale.ipn/) (el reloj es Android 10, compatible) y déjalo en `device-kit/vendor/`. Luego:

```bash
DRY_RUN=0 CLOCK_TOKEN="$(head -c16 /dev/urandom | base64 | tr -dc a-z0-9 | head -c20)" \
  BACKEND_URL="http://<ip-tailscale-homelab>:8765" \
  BACKEND_TOKEN="<BRIDGE_TOKEN de A5>" \
  ASSISTANT_NAME="Friday" \
  ./device-kit/06_install_apps.sh
```

Ese script instala ambas aplicaciones, concede permisos, fija **Friday como launcher** y configura el token por ADB. **Apunta el `CLOCK_TOKEN`** que se ha generado: lo necesita Hermes y el panel.

En el reloj, abre **Tailscale** e inicia sesión para unirlo a tu red.

#### B5. La voz del reloj (motor TTS)

Una GSI limpia puede venir **sin motor de voz**. Dos opciones:

- Usar el TTS del **puente** (Parte A5): el reloj solo reproduce el audio. Es lo recomendado.
- O instalar en el reloj **RHVoice** o **eSpeak NG** (F-Droid) con voz española, para que hable aunque el puente no mande audio. En los Ajustes del reloj hay acceso a la voz del sistema y a la velocidad.

#### B6. El altavoz Bluetooth (usar el reloj como altavoz del móvil)

Ya está integrado: dile a Friday **"activa el bluetooth"** (o Ajustes, **Modo altavoz Bluetooth**), empareja el móvil buscando **"Friday"** y su música sonará por el reloj. Que el audio se enrute de verdad depende de que tu GSI traiga el perfil **A2DP sink**; cómo comprobarlo y activarlo está en [`docs/altavoz-bluetooth.md`](docs/altavoz-bluetooth.md).

---

### Parte C: conectarlo todo y probar

1. **Dile a Hermes la IP y el token del reloj.** Edita `~/.config/mi-smart-clock.json` (Parte A7) con la IP de Tailscale del reloj (`tailscale ip -4` en el reloj o el panel de Tailscale) y el `CLOCK_TOKEN` de B4. Pon el mismo valor en el `.env` del panel (A8) si todavía no lo hiciste.

2. **Prueba el reloj desde el homelab, por Tailscale:**
   ```bash
   python3 ~/.hermes/skills/mi-smart-clock/scripts/clock.py status
   python3 ~/.hermes/skills/mi-smart-clock/scripts/clock.py say "Hola, soy Friday"
   ```

3. **Prueba la voz completa:** en el reloj, pulsa el micro (o doble toque en la hora) y di *"Friday, ¿qué tiempo hace?"*. Debe transcribir (Whisper), pensar (Qwen y Hermes) y responder por el altavoz.

4. **Prueba el despertador sin red:** *"pon una alarma dentro de 2 minutos"* y comprueba que suena aunque apagues el homelab.

5. **Prueba que Friday controla el reloj:** por Telegram u otro canal de Hermes, *"muéstrame 'Hola' en el reloj y pon una alarma a las 7"*.

6. **Abre el panel** en el móvil y comprueba que ves el reloj en vivo, tus alarmas y el editor de pantallas. En Ajustes, la tarjeta de conexión debe mostrar la **misma versión del motor** en el reloj y en el panel.

Si algo no responde, empieza por `clock.py status` y por `curl .../health` del puente y de Hermes. Consulta también la [solución de problemas](#solución-de-problemas).

---

## Qué hace Friday

### Despertador

- Alarmas únicas o repetitivas (L M X J V S D, entre semana, fines de semana), cada una con **su sonido, su volumen máximo, su subida de volumen y su tiempo de posponer**.
- **Volumen progresivo** y pantalla de **amanecer**: el brillo sube con el volumen.
- Posponer configurable; si nadie la toca, se pospone sola. Pitidos de reserva si la GSI no trae tonos: nunca se queda muda.
- **Buenos días** al descartar: hora, tiempo, agenda (si Hermes la tiene) y próxima alarma.
- Temporizadores con etiqueta y sonidos propios (mp3, ogg, wav o m4a) subidos desde el panel.

### Asistente

- Micro por botón, **doble toque** en la carátula, **palabra de activación "Friday"** o `POST /listen`. Un detector de voz corta al callar.
- Voz a texto con Whisper local y texto a voz con Piper, Kokoro, Magpie o el TTS del reloj.
- **Modo conversación**: si Friday pregunta, el reloj vuelve a escuchar solo.
- **Respuestas rápidas**: la aritmética hablada se resuelve al instante y las preguntas simples van directo a Ollama sin pasar por el agente.

### Órdenes que funcionan sin red (y sin Hermes)

| Dices | Hace |
|---|---|
| "pon una alarma a las siete y media entre semana" | alarma 07:30 de lunes a viernes |
| "despiértame a las 6:45", "a las diez de la noche para la pastilla" | alarma puntual con etiqueta |
| "temporizador de 5 minutos para la pasta", "avísame en media hora" | temporizador |
| "quita la alarma de las 7:30", "cancela todas las alarmas" | borra |
| "¿qué alarmas tengo?" | las lee y dice la próxima |
| "pospón", "apaga la alarma" | posponer o descartar |
| "¿qué hora es?", "¿qué día es?", "¿qué tiempo hace?" | responde al momento |
| "sube el volumen", "volumen al 40" | volumen |
| "activa el bluetooth", "modo altavoz" | modo altavoz Bluetooth |

Todo lo demás ("¿tengo algo mañana?", "recuérdame llamar a mamá el viernes"...) va a Hermes y a Qwen.

### Carátula, pantallas y temas

- Hora, fecha, tiempo (Open-Meteo, sin clave), previsión y próxima alarma, con **modo noche** (acentos cálidos tenues, brillo mínimo) y **anti-quemado**.
- **Temas, pantallas y perfiles por hora** que se diseñan desde el panel con una vista previa idéntica a la del reloj y un botón **Probar** que lo muestra en el reloj sin guardarlo.
- **Fotos** como fondo, **carruseles** como elemento o como fondo del tema, ajuste de encaje, zoom y posición, **capas** con orden y solapes, y arrastrar elementos para colocarlos.
- **Gestos**: deslizar de izquierda a derecha abre Alarmas, de derecha a izquierda abre Ajustes y en vertical cambia de pantalla del perfil.
- **Brillo** con una regla clara: manda el del panel, y si lo cambias en el reloj ese valor manda hasta que cambie el perfil o el panel.

Todo el detalle del diseño JSON, los límites y los campos está en [`docs/pantallas-y-temas.md`](docs/pantallas-y-temas.md).

### Altavoz Bluetooth y botones físicos

- Modo altavoz del móvil, con emparejamiento visible como "Friday".
- Los botones físicos funcionan: volumen más y menos en pasos del 5 por ciento, y el botón central silencia o activa el micrófono.

### API para otras herramientas

El reloj expone una API HTTP con token (alarmas, ajustes, sonidos, diseño, fotos, hablar, mostrar, volumen, brillo, Bluetooth y acciones en lote) que usan Hermes, el panel y `clock.py`. Está documentada en [`docs/api.md`](docs/api.md).

```bash
curl -X POST http://<ip-reloj>:8080/alarm -H "X-Auth-Token: <token>" \
  -H "Content-Type: application/json" \
  -d '{"action":"set","time":"07:30","label":"Despertar","days":"weekdays"}'
```

---

## Seguridad y privacidad

- **La voz es local.** Whisper y el motor de TTS corren en tu servidor; no hay nube de voz ni Google. El puente no registra lo que se dice cuando la palabra de activación no coincide.
- **La API del reloj exige token** (comparado en tiempo constante) y, sin token configurado, responde 503 a todo. Por defecto escucha en todas las interfaces del reloj (accesible desde tu red local). Activa **solo Tailscale** en Ajustes para que escuche únicamente en esa IP; si Tailscale no está activo en ese momento, vuelve a escuchar en todas. No abras el puerto a internet.
- **El token del reloj no sale del homelab:** el panel hace de proxy, el navegador nunca lo ve. Las peticiones del panel llevan una cabecera anti-CSRF, las subidas tienen límites de tamaño y se puede proteger con contraseña.
- **Todo se valida de forma estricta.** El diseño de pantallas se valida entero o no se guarda; las fotos se reducen y se comprueban; los nombres de archivo se sanean.
- **Reversible.** Los paquetes de Google solo se deshabilitan, hay backup completo previo y un script de rollback.

> [!WARNING]
> El `adb shell` del reloj liberado es root sin autenticación y SELinux queda permisivo. Es razonable para un reloj de mesilla en tu casa, pero no lo expongas a una red no confiable.
> El asistente (Hermes) puede ejecutar comandos en tu servidor y obedece a la voz que oye el reloj. No abras en el router los puertos 8080, 8642, 8765, 8770 ni 11434, usa token de al menos 16 caracteres, pon contraseña al panel y no dejes `hermes dashboard --insecure` accesible. El APK de release va firmado con la clave de depuración: verifica su SHA-256. Detalles en [SECURITY.md](SECURITY.md).

---

## Desarrollo y pruebas

El proyecto tiene unas **240 pruebas automáticas** (121 de JVM, 80 de Python y 39 de Node), unas 5.700 líneas de Kotlin en la app, unas 3.800 de HTML, CSS y JavaScript entre el motor y el panel, y unas 1.650 de Python.

```bash
# App del reloj (JDK 17 y SDK de Android)
./gradlew testDebugUnitTest
./gradlew assembleDebug assembleRelease        # APK en app-android/build/outputs/apk/

# Skill de Hermes (contra un reloj simulado, sin dispositivo)
cd hermes-skill && pip install pytest httpx && pytest -q

# Panel web
cd homelab/friday-panel && pip install -r requirements.txt pytest && pytest -q

# Puente de voz (módulos puros)
cd homelab/clock-bridge && pytest -q test_wake.py test_route.py test_calc.py

# Motor de pantallas y editor (Node 20 o superior)
node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js

# Sistema de diseño: lo generado debe estar al día
python tools/gen-tokens.py --check
python tools/gen-icons.py --check
bash tools/sync-face.sh --check
```

La integración continua (`.github/workflows/build-apk.yml`) ejecuta exactamente eso y publica los APK como artefactos.

### Estructura del repositorio

```text
app-android/                      App Friday del reloj (Kotlin, sin dependencias de runtime)
  src/main/assets/face/           Motor de pantallas (HTML, CSS y JS), copiado al panel
design-system/                    Tokens (JSON) e iconos Lucide: la fuente de verdad del diseño
tools/                            gen-tokens, gen-icons y sync-face (con --check)
homelab/
  clock-bridge/                   Whisper, TTS y enrutado de preguntas (FastAPI)
  friday-panel/                   Panel web para el móvil (FastAPI y JS vanilla)
hermes-skill/                     Skill y tools de Hermes, más un reloj simulado para pruebas
device-kit/                       Scripts para liberar el reloj (simulacro por defecto)
docs/                             API, pantallas y temas, diseño, voz, Bluetooth, decisiones, specs y planes
RUNBOOK.md                        Liberar el reloj paso a paso, con el reloj delante
PLAN.md                           El plan original por fases
```

---

## Solución de problemas

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| Hermes se niega a arrancar | Contexto de Ollama menor de 64.000 tokens | Sube `OLLAMA_CONTEXT_LENGTH=65536` (A1) y reinicia Ollama. |
| El reloj no habla | La GSI no trae motor de voz y el puente no manda audio | Configura el TTS del puente (A5) o instala RHVoice o eSpeak NG (B5). |
| Las alarmas no suenan tras un `am force-stop` | Android descarta los receptores de la app | Reinicia el reloj (`adb reboot`). No uses `force-stop`; para reiniciar la app basta `adb install -r`. |
| Tailscale no vuelve tras reiniciar | Falta la VPN siempre activa | `settings put secure always_on_vpn_app com.tailscale.ipn` y `appops set com.tailscale.ipn ACTIVATE_VPN allow`. |
| El micro oye poco o inventa textos | El micro digital es muy bajo y mete un pop al abrirse | La app ya normaliza y descarta el pop y los golpes. Habla cerca y sin golpear el aparato. |
| Los botones de volumen o silencio no responden | La propiedad `ro.mi.handle_volume_key_in_oem_app` sigue en `true` | El `boot` parcheado la pone en `false` (`overlay.d/init.adb.rc`). Reaplica el paso B2. |
| El audio Bluetooth no sale por el reloj | La GSI no trae el perfil A2DP sink | Sigue [`docs/altavoz-bluetooth.md`](docs/altavoz-bluetooth.md). |
| El panel avisa de versiones distintas del motor | Reloj y panel se actualizaron por separado | Instala el APK actual en el reloj o redespliega el panel. |
| La carátula vuelve a la versión nativa | El WebView falló o tardó en cargar | Es la red de seguridad prevista; las alarmas no se ven afectadas. Reinicia la app con `adb install -r`. |
| El panel dice que el reloj no responde | Reloj apagado, sin red o Tailscale caído | Comprueba `clock.py status` y que ambos estén en la tailnet. |

---

## Hoja de ruta

**Hecho**

- Liberación del reloj, app, alarmas a prueba de fallos, asistente de voz local con palabra de activación y conversación continua.
- Panel web, motor de pantallas, temas, perfiles, fotos, carruseles, capas y gestos.
- Sistema de diseño Obsidiana, iconos Lucide y rediseño completo del panel.
- Pantallas nativas del reloj (Alarmas, Ajustes, Bluetooth, alarma sonando y diálogos) con el sistema Obsidiana.
- Botón físico de silencio del micrófono, brillo con regla propia y altavoz Bluetooth.

**Pendiente**

- **Cronómetro y temporizadores en pantalla, panel de control con tarjetas genéricas y vista de contenido del agente.**
- **Recordatorios:** aviso hablado en el reloj y mensaje por Telegram a través del bot de Hermes.
- **Elementos conectados:** datos del agente dentro de las pantallas de la carátula.
- Reproductor de radio y podcasts por el altavoz, para escuchar sin Bluetooth.
- Que los errores de validación de alarmas lleguen como un mapa por campo, en vez de un texto.

---

## Documentación

| Documento | Para qué sirve |
|---|---|
| [`RUNBOOK.md`](RUNBOOK.md) | Liberar el reloj con puntos de control y rollback |
| [`docs/api.md`](docs/api.md) | Referencia completa de la API HTTP del reloj |
| [`docs/pantallas-y-temas.md`](docs/pantallas-y-temas.md) | Diseño JSON, fotos, carruseles, capas y gestos |
| [`docs/sistema-de-diseno.md`](docs/sistema-de-diseno.md) | Tokens, tipografía, iconos y regla de brillo |
| [`docs/voz-friday.md`](docs/voz-friday.md) | Ponerle voz a Friday: Whisper, Piper, Kokoro y Telegram |
| [`docs/altavoz-bluetooth.md`](docs/altavoz-bluetooth.md) | Usar el reloj como altavoz del móvil |
| [`docs/decisions.md`](docs/decisions.md) y [`docs/research-notes.md`](docs/research-notes.md) | Por qué cada decisión y lo investigado |
| [`hermes-skill/README.md`](hermes-skill/README.md) | La skill y las tools de Hermes |
| [`homelab/friday-panel/README.md`](homelab/friday-panel/README.md) | El panel web |
| [`docs/superpowers/`](docs/superpowers/) | Especificaciones y planes de cada subproyecto |

---

## Créditos y licencias

Friday se apoya en el trabajo de mucha gente. Gracias a:

- **Investigación del reloj:** [`AyraHikari/xiaomi-x04g-lx04`](https://github.com/AyraHikari/xiaomi-x04g-lx04) y [`octathorp/x04g_tools`](https://github.com/octathorp/x04g_tools).
- **Herramientas de liberación:** [`mtkclient`](https://github.com/bkerler/mtkclient) de bkerler y [Magisk](https://github.com/topjohnwu/Magisk).
- **Cerebro y modelos:** [Hermes Agent](https://hermes-agent.nousresearch.com/) de Nous Research, [Ollama](https://ollama.com/) y los modelos Qwen de Alibaba Cloud.
- **Voz:** [faster-whisper](https://github.com/SYSTRAN/faster-whisper), [Piper](https://github.com/rhasspy/piper) de Rhasspy, [Kokoro](https://github.com/thewh1teagle/kokoro-onnx) y NVIDIA Magpie.
- **Red y servicios:** [Tailscale](https://tailscale.com/), [FastAPI](https://fastapi.tiangolo.com/) y [Open-Meteo](https://open-meteo.com/).
- **Diseño:** iconos [Lucide](https://lucide.dev/) (licencia ISC) y tipografía [Inter](https://rsms.me/inter/) (licencia SIL Open Font).

El código se publica bajo [Apache-2.0](LICENSE); los componentes de terceros están en [NOTICE](NOTICE).

> [!NOTE]
> Este es un proyecto personal y no oficial, sin ninguna relación con Xiaomi, Google, Nous Research ni ninguna de las empresas citadas. Modificar el firmware de un dispositivo es responsabilidad de quien lo hace.

<div align="center">

<sub>Hecho con paciencia, un destornillador mental y mucho café, para despertar mejor.</sub>

</div>
