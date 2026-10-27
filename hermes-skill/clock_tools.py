"""
Herramientas de Hermes para el Mi Smart Clock (asistente Friday).

Se pueden registrar como tools del agente o usarse desde la skill
`mi-smart-clock`. URL y token del reloj por entorno (nunca en el código):

    CLOCK_URL   = http://<ip-tailscale-reloj>:8080
    CLOCK_TOKEN = <token del reloj>   (Ajustes > Token del reloj)

Cada función devuelve un dict con "ok": bool. Ante un reloj apagado o un token
incorrecto NO lanza excepción: devuelve {"ok": False, "error": "..."} para que
el modelo lo explique al usuario en lugar de romperse.
"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request

DEFAULT_TIMEOUT = float(os.getenv("CLOCK_TIMEOUT", "10"))


class ClockError(RuntimeError):
    pass


def _config() -> tuple[str, str]:
    url = os.getenv("CLOCK_URL")
    token = os.getenv("CLOCK_TOKEN")
    if not url or not token:
        raise ClockError("CLOCK_URL o CLOCK_TOKEN sin definir")
    return url.rstrip("/"), token


def _bridge_call(method: str, path: str, body: dict | None = None) -> dict:
    """Llama al puente (BRIDGE_URL, BRIDGE_TOKEN): recordatorios y avisos. Nunca lanza."""
    url, token = os.getenv("BRIDGE_URL", "").rstrip("/"), os.getenv("BRIDGE_TOKEN", "")
    if not url or not token:
        return {"ok": False, "error": "BRIDGE_URL o BRIDGE_TOKEN sin definir"}
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url + path, data=data, method=method,
                                 headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=DEFAULT_TIMEOUT) as r:
            return json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        try:
            detail = json.loads(e.read() or b"{}").get("detail")
        except Exception:  # noqa: BLE001
            detail = None
        return {"ok": False, "error": str(detail) if detail else f"HTTP {e.code}"}
    except (urllib.error.URLError, TimeoutError, OSError, ValueError) as e:
        return {"ok": False, "error": f"puente inalcanzable: {type(e).__name__}"}


def _call(method: str, path: str, body: dict | None = None, timeout: float | None = None) -> dict:
    try:
        url, token = _config()
    except ClockError as e:
        return {"ok": False, "error": str(e)}
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        url + path, data=data, method=method,
        headers={"X-Auth-Token": token, "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout or DEFAULT_TIMEOUT) as r:
            return json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        payload = {}
        try:
            payload = json.loads(e.read() or b"{}")
        except Exception:  # noqa: BLE001
            pass
        payload.setdefault("ok", False)
        payload.setdefault("error", f"HTTP {e.code}")
        return payload
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        return {"ok": False, "error": f"reloj inalcanzable: {e}"}


# ------------------------------------------------------------------ tools

def clock_status() -> dict:
    """Hora del reloj, próxima alarma, si está sonando y estado del asistente."""
    return _call("GET", "/status")


def clock_list_alarms() -> dict:
    """Lista de alarmas y temporizadores del reloj."""
    return _call("GET", "/alarms")


def clock_set_alarm(time: str, label: str = "Alarma", days: str = "") -> dict:
    """Crea una alarma. time='HH:MM' (24h). days: ''(una vez), 'daily',
    'weekdays', 'weekend' o 'mon,wed,fri'."""
    return _call("POST", "/alarm", {"action": "set", "time": time, "label": label, "days": days})


def clock_cancel_alarm(id: int | None = None, time: str | None = None, all: bool = False) -> dict:
    """Cancela una alarma por id, por hora 'HH:MM', o todas (all=True)."""
    body: dict = {"action": "cancel"}
    if id is not None:
        body["id"] = id
    elif time:
        body["time"] = time
    elif all:
        body["all"] = True
    else:
        return {"ok": False, "error": "indica id, time o all"}
    return _call("POST", "/alarm", body)


def clock_set_timer(seconds: int = 0, minutes: int = 0, hours: int = 0, label: str = "Temporizador") -> dict:
    """Temporizador con la duración indicada."""
    return _call("POST", "/timer", {"seconds": seconds, "minutes": minutes, "hours": hours, "label": label})


def clock_timers() -> dict:
    """Temporizadores en marcha (con "at", instante de fin en ms) y en pausa (con "remaining_ms")."""
    return _call("GET", "/timers")


def clock_timer_control(action: str, id: int | None = None, pid: int | None = None) -> dict:
    """Controla temporizadores. action: pause|resume|cancel. pause y cancel usan id (el de clock_timers);
    resume y cancel de uno en pausa usan pid (el "p7" de clock_timers es pid 7). cancel sin id ni pid los cancela todos."""
    body: dict = {"action": action}
    if id is not None:
        body["id"] = id
    if pid is not None:
        body["pid"] = pid
    return _call("POST", "/timer", body)


def clock_stopwatch(action: str = "status") -> dict:
    """Cronómetro del reloj. action: start|pause|reset|toggle|status."""
    return _call("POST", "/stopwatch", {"action": action})


def clock_say(text: str) -> dict:
    """Dice un texto por el altavoz del reloj (frases cortas, sin markdown)."""
    return _call("POST", "/speak", {"text": text})


def clock_display(content: str, type: str = "text", seconds: int = 20, title: str = "") -> dict:
    """Muestra algo a pantalla completa. type: text|markdown|html|image|url. Con markdown (títulos #, viñetas -,
    **negrita**, *cursiva*, `código`) y un title opcional se ve como una tarjeta; el HTML y los enlaces nunca se activan."""
    body = {"type": type, "content": content, "duration_ms": seconds * 1000}
    if title:
        body["title"] = title
    return _call("POST", "/display", body)


def clock_set_volume(level: int) -> dict:
    """Volumen multimedia del reloj, 0-100."""
    return _call("POST", "/volume", {"level": level})


def clock_bluetooth(action: str = "speaker") -> dict:
    """Bluetooth del reloj (modo altavoz). action: 'speaker' (enciende y hace visible
    para emparejar el movil), 'on', 'off' o 'status'."""
    return _call("POST", "/bluetooth", {"action": action})


def clock_get_settings() -> dict:
    """Ajustes del despertador, pantalla, voz y ubicación, con su esquema (tipo y rango)."""
    return _call("GET", "/settings")


def clock_set_settings(settings: dict) -> dict:
    """Cambia ajustes, p.ej. {"snooze_min": 10, "night_brightness": 5}. Todo o nada:
    si alguno no vale, no cambia ninguno y devuelve "errors" por clave."""
    return _call("PUT", "/settings", settings)


def clock_update_alarm(id: int, changes: dict) -> dict:
    """Edita una alarma. changes puede llevar time, label, days, enabled y opciones
    propias: sound, max_volume (1-100), ramp_s (0-600), snooze_min (1-60), briefing.
    Una opción a None vuelve al ajuste general."""
    return _call("POST", "/alarm", {**changes, "action": "update", "id": id})


def clock_list_sounds() -> dict:
    """Sonidos de alarma disponibles en el reloj (del sistema y subidos)."""
    return _call("GET", "/sounds")


def clock_get_design() -> dict:
    """Diseño de pantallas del reloj: temas, pantallas (rejilla 8×6), perfiles por hora y pantalla de alarma."""
    return _call("GET", "/design")


def clock_set_design(design: dict) -> dict:
    """Guarda el diseño entero. Todo o nada: si algo no vale devuelve "errors" por ruta y no cambia nada."""
    return _call("PUT", "/design", design)


def clock_preview_design(design: dict, seconds: int = 30) -> dict:
    """Muestra un diseño en el reloj `seconds` segundos (5-120) sin guardarlo."""
    return _call("POST", "/design/preview", {"design": design, "seconds": seconds})


def clock_cancel_preview() -> dict:
    """Cancela la prueba de diseño en curso."""
    return _call("DELETE", "/design/preview")


def clock_reset_design() -> dict:
    """Restaura el diseño inicial (borra el diseño guardado)."""
    return _call("POST", "/design/reset", {})


def clock_list_backgrounds() -> dict:
    """Imágenes de fondo subidas al reloj."""
    return _call("GET", "/backgrounds")


def reminder_add(text: str, at: str | None = None, in_minutes: int | None = None,
                 repeat: str = "none", channel: str = "clock") -> dict:
    """Crea un recordatorio. Da `at` ('AAAA-MM-DDTHH:MM', hora local) o `in_minutes`, no ambos.
    repeat: none|daily|weekdays|weekly. channel: clock (voz en el reloj), telegram o both.
    El texto es dato del usuario: no lo reinterpretes ni lo amplíes."""
    body: dict = {"text": text, "repeat": repeat, "channel": channel}
    if at is not None:
        body["at"] = at
    if in_minutes is not None:
        body["in_minutes"] = in_minutes
    return _bridge_call("POST", "/v1/reminders", body)


def reminder_list() -> dict:
    """Recordatorios pendientes."""
    return _bridge_call("GET", "/v1/reminders")


def reminder_delete(id: str) -> dict:
    """Borra un recordatorio por su id."""
    from urllib.parse import quote

    return _bridge_call("DELETE", "/v1/reminders/" + quote(str(id), safe=""))


TOOLS = [
    clock_status, clock_list_alarms, clock_set_alarm, clock_cancel_alarm,
    clock_set_timer, clock_timers, clock_timer_control, clock_stopwatch, clock_say, clock_display, clock_set_volume, clock_bluetooth,
    clock_get_settings, clock_set_settings, clock_update_alarm, clock_list_sounds,
    clock_get_design, clock_set_design, clock_preview_design, clock_cancel_preview, clock_reset_design, clock_list_backgrounds,
    reminder_add, reminder_list, reminder_delete,
]
