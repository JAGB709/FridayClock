# Jarvis Clock

App Android (Kotlin, sin Compose para no gastar los 1GB de RAM del MT8167)
pensada para reemplazar el launcher de Google en el Xiaomi Mi Smart Clock
(X04G) una vez tengas la GSI limpia flasheada.

## Qué hace

- Muestra la hora en reposo (carátula por defecto).
- Expone un servidor HTTP local (puerto 8080) con dos endpoints para que
  Hermes (u otro cliente en tu tailnet) controle el reloj:
  - `POST /display` — pinta HTML/texto/imagen en pantalla completa y
    vuelve sola a la hora pasado `duration_ms`.
  - `POST /alarm` — programa o cancela alarmas.
- Las alarmas viven en `AlarmManager` nativo de Android: si el homelab
  o la red caen, el reloj sigue sonando igualmente. Sobreviven a un
  reinicio del dispositivo (se re-programan en el `BOOT_COMPLETED`).
- Pantalla de alarma a pantalla completa, con "Descartar" y "Posponer
  5 min" (táctil, ya que el X04G tiene pantalla táctil).

## Requisitos para compilar

- Android Studio (Koala o superior) o Gradle 8.x + JDK 17.
- El proyecto NO incluye el gradle wrapper binario (`gradle-wrapper.jar`);
  ábrelo directamente en Android Studio y te lo genera él solo al
  sincronizar. Si prefieres CLI, ejecuta `gradle wrapper` una vez dentro
  de la carpeta del proyecto con un Gradle instalado localmente.

## Compilar e instalar

```bash
# Desde Android Studio: Build > Build Bundle(s)/APK(s) > Build APK(s)
# o por CLI una vez tengas el wrapper:
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Tras instalar, pulsa el botón Home del reloj y elige "Jarvis Clock" como
launcher predeterminado ("Siempre").

## Antes de usarlo en serio

1. **Cambia el token de autenticación.** Por defecto es `"changeme"`
   (ver `DisplayServer.kt`, `authToken`). Edítalo ahí y recompila, o
   mejor aún, añade una pequeña pantalla de ajustes que lo guarde en
   `SharedPreferences` — de momento está hardcodeado a propósito para
   que veas dónde tocar. Aunque solo sea alcanzable por Tailscale,
   conviene no dejarlo en el valor por defecto.
2. **Permiso de alarmas exactas.** Desde Android 12, `SCHEDULE_EXACT_ALARM`
   no se concede solo con declararlo en el manifest para todas las apps;
   puede que tengas que activarlo a mano en
   Ajustes > Apps > Jarvis Clock > Alarmas y recordatorios.
3. **Instalar Tailscale en el reloj** (APK de Tailscale para Android)
   para que Hermes, viviendo en Madrid, pueda alcanzar el `POST /display`
   y `POST /alarm` sin exponer nada a internet.

## Endpoints — ejemplos con curl

```bash
# Mostrar un mensaje 20 segundos
curl -X POST http://<ip-tailscale-reloj>:8080/display \
  -H "X-Auth-Token: <tu-token>" \
  -H "Content-Type: application/json" \
  -d '{"type":"text","content":"Hola Jose","duration_ms":20000}'

# Programar una alarma diaria a las 07:30
curl -X POST http://<ip-tailscale-reloj>:8080/alarm \
  -H "X-Auth-Token: <tu-token>" \
  -H "Content-Type: application/json" \
  -d '{"action":"set","time":"07:30","label":"Despertar"}'

# Cancelar la alarma con id 1
curl -X POST http://<ip-tailscale-reloj>:8080/alarm \
  -H "X-Auth-Token: <tu-token>" \
  -H "Content-Type: application/json" \
  -d '{"action":"cancel","id":1}'

# Ver estado
curl http://<ip-tailscale-reloj>:8080/status -H "X-Auth-Token: <tu-token>"
```

## Skill de ejemplo para Hermes Agent (Python)

Esto es la función que registrarías como "tool" en Hermes para que el
propio modelo decida cuándo llamarla:

```python
import requests

CLOCK_URL = "http://<ip-tailscale-reloj>:8080"
CLOCK_TOKEN = "<tu-token>"

def clock_display(content: str, content_type: str = "text", duration_ms: int = 15000):
    """Muestra contenido en la pantalla del reloj de la mesita de noche."""
    resp = requests.post(
        f"{CLOCK_URL}/display",
        json={"type": content_type, "content": content, "duration_ms": duration_ms},
        headers={"X-Auth-Token": CLOCK_TOKEN},
        timeout=5,
    )
    resp.raise_for_status()
    return resp.json()

def clock_set_alarm(time: str, label: str = "Alarma"):
    """Programa una alarma en el reloj. time en formato HH:MM (24h)."""
    resp = requests.post(
        f"{CLOCK_URL}/alarm",
        json={"action": "set", "time": time, "label": label},
        headers={"X-Auth-Token": CLOCK_TOKEN},
        timeout=5,
    )
    resp.raise_for_status()
    return resp.json()
```

## Siguientes pasos sugeridos

- Wake word local (openWakeWord) + captura de audio -> STT en el
  homelab, para no depender solo de peticiones HTTP manuales.
- Pantalla de ajustes real (token, IP del homelab) en vez de valores
  hardcodeados.
- Icono/launcher propio en vez del icono por defecto de Android.
