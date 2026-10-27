"""Plugin de Hermes: tools tipadas para el Mi Smart Clock.

Registra el toolset `mi-smart-clock`. Permite que el canal del reloj use Hermes
SIN terminal, navegador ni ejecucion de codigo: solo estas funciones, que hablan
con la API HTTP del reloj. URL y token: CLOCK_URL/CLOCK_TOKEN o
`<HERMES_HOME>/.config/mi-smart-clock.json` ({"url": ..., "token": ...}).

Las acciones destructivas o que cambian ajustes exigen `confirm=true`; sin el,
devuelven needs_confirmation para que el agente pregunte al usuario primero.
"""

from __future__ import annotations

import json
import os
import re
import urllib.error
import urllib.request

TOOLSET = "mi-smart-clock"
_TIME = re.compile(r"^([01]\d|2[0-3]):[0-5]\d$")


def _config():
    url, token = os.getenv("CLOCK_URL"), os.getenv("CLOCK_TOKEN")
    if not (url and token):
        home = os.getenv("HERMES_HOME") or os.path.expanduser("~/.hermes")
        for path in (os.path.join(home, ".config", "mi-smart-clock.json"),
                     os.path.expanduser("~/.config/mi-smart-clock.json")):
            try:
                with open(path, encoding="utf-8") as f:
                    c = json.load(f)
                url, token = c.get("url"), c.get("token")
                if url and token:
                    break
            except (OSError, ValueError):
                continue
    if not (url and token):
        return None
    return url.rstrip("/"), token


def _call(method, path, body=None):
    cfg = _config()
    if cfg is None:
        return {"ok": False, "error": "Falta configuracion del reloj (CLOCK_URL/CLOCK_TOKEN)"}
    url, token = cfg
    req = urllib.request.Request(
        url + path, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"X-Auth-Token": token, "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=float(os.getenv("CLOCK_TIMEOUT", "10"))) as r:
            return json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        try:
            payload = json.loads(e.read() or b"{}")
        except Exception:  # noqa: BLE001
            payload = {}
        payload.setdefault("ok", False)
        payload.setdefault("error", f"HTTP {e.code}")
        return payload
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        return {"ok": False, "error": f"reloj inalcanzable: {e}"}


def _need_confirm(args, what):
    if args.get("confirm") is True:
        return None
    return {"ok": False, "needs_confirmation": True,
            "message": f"Pide confirmacion al usuario antes de: {what}. Si acepta, repite con confirm=true."}


def _int(args, key, lo, hi):
    try:
        v = int(args.get(key))
    except (TypeError, ValueError):
        return None
    return v if lo <= v <= hi else None


def _bad(msg):
    return {"ok": False, "error": msg}


# ------------------------------------------------------------- handlers
def h_status(args, **kw): return _call("GET", "/status")
def h_alarms(args, **kw): return _call("GET", "/alarms")
def h_settings_get(args, **kw): return _call("GET", "/settings")
def h_briefing(args, **kw): return _call("POST", "/briefing", {})


def h_alarm_set(args, **kw):
    t = str(args.get("time") or "")
    if not _TIME.match(t):
        return _bad("time debe ser HH:MM en 24h")
    return _call("POST", "/alarm", {"action": "set", "time": t,
                                    "label": str(args.get("label") or "Alarma")[:60],
                                    "days": str(args.get("days") or "")})


def h_alarm_cancel(args, **kw):
    body = {"action": "cancel"}
    if args.get("id") is not None:
        body["id"] = args["id"]
    elif args.get("time"):
        body["time"] = args["time"]
    elif args.get("all") is True:
        body["all"] = True
    else:
        return _bad("indica id, time o all")
    what = "cancelar TODAS las alarmas" if body.get("all") else "cancelar una alarma"
    return _need_confirm(args, what) or _call("POST", "/alarm", body)


def h_alarm_update(args, **kw):
    if args.get("id") is None:
        return _bad("falta id")
    body = {"action": "update", "id": args["id"]}
    for k in ("time", "label", "days", "max_volume", "snooze_min", "sound"):
        if args.get(k) is not None:
            body[k] = args[k]
    if "time" in body and not _TIME.match(str(body["time"])):
        return _bad("time debe ser HH:MM en 24h")
    return _need_confirm(args, "modificar una alarma") or _call("POST", "/alarm", body)


def h_alarm_control(args, **kw):
    action = args.get("action")
    if action in ("enable", "disable"):
        if args.get("id") is None:
            return _bad("falta id")
        return _call("POST", "/alarm", {"action": action, "id": args["id"]})
    if action in ("stop", "snooze"):
        return _call("POST", "/alarm", {"action": action})
    return _bad("action: enable, disable, stop o snooze")


def h_timer(args, **kw):
    if args.get("action") == "cancel":
        return _call("POST", "/timer", {"action": "cancel"})
    parts = {}
    for k, hi in (("hours", 99), ("minutes", 5999), ("seconds", 86400)):
        try:
            parts[k] = max(0, min(int(args.get(k) or 0), hi))
        except (TypeError, ValueError):
            return _bad(f"{k} debe ser un entero")
    if not any(parts.values()):
        return _bad("indica hours, minutes o seconds")
    return _call("POST", "/timer", {**parts, "label": str(args.get("label") or "Temporizador")[:60]})


def h_say(args, **kw):
    text = str(args.get("text") or "").strip()
    return _call("POST", "/speak", {"text": text[:500]}) if text else _bad("falta text")


def h_display(args, **kw):
    if args.get("clear") is True:
        return _call("POST", "/display", {"action": "clear"})
    typ, content = args.get("type", "text"), str(args.get("content") or "")
    if typ not in ("text", "image") or not content:
        return _bad("type: text o image, y content (o clear=true)")
    if typ == "image" and not content.startswith("https://"):
        return _bad("las imagenes deben ser https://")
    secs = _int(args, "seconds", 1, 600) or 20
    return _call("POST", "/display", {"type": typ, "content": content[:2000], "duration_ms": secs * 1000})


def h_volume(args, **kw):
    v = _int(args, "level", 0, 100)
    return _call("POST", "/volume", {"level": v}) if v is not None else _bad("level 0-100")


def h_brightness(args, **kw):
    v = _int(args, "level", 1, 100)
    return _call("POST", "/brightness", {"level": v}) if v is not None else _bad("level 1-100")


def h_bluetooth(args, **kw):
    a = args.get("action", "status")
    if a not in ("speaker", "on", "off", "status"):
        return _bad("action: speaker, on, off o status")
    return _call("POST", "/bluetooth", {"action": a})


def h_settings_set(args, **kw):
    s = args.get("settings")
    if not isinstance(s, dict) or not s:
        return _bad("settings debe ser un objeto clave:valor")
    return _need_confirm(args, "cambiar ajustes del reloj") or _call("PUT", "/settings", s)


# --------------------------------------------------------------- schemas
def _s(name, desc, props=None, required=None):
    return {"name": name, "description": desc,
            "parameters": {"type": "object", "properties": props or {}, "required": required or []}}


_STR, _INT, _BOOL = {"type": "string"}, {"type": "integer"}, {"type": "boolean"}
_CONFIRM = {"type": "boolean", "description": "true solo tras confirmar con el usuario"}

_TOOLS = (
    ("clock_status", _s("clock_status", "Estado del reloj (hora, alarma sonando, bateria, etc.)."), h_status),
    ("clock_alarms", _s("clock_alarms", "Lista las alarmas del reloj."), h_alarms),
    ("clock_alarm_set", _s("clock_alarm_set", "Crea una alarma. time HH:MM 24h; days: vacio (una vez), daily, weekdays, weekend o mon,wed,fri.",
        {"time": _STR, "label": _STR, "days": _STR}, ["time"]), h_alarm_set),
    ("clock_alarm_cancel", _s("clock_alarm_cancel", "Cancela alarmas por id, por hora o todas (all). Requiere confirm=true tras preguntar.",
        {"id": _INT, "time": _STR, "all": _BOOL, "confirm": _CONFIRM}), h_alarm_cancel),
    ("clock_alarm_update", _s("clock_alarm_update", "Edita una alarma (time, label, days, max_volume, snooze_min, sound). Requiere confirm=true.",
        {"id": _INT, "time": _STR, "label": _STR, "days": _STR, "max_volume": _INT, "snooze_min": _INT, "sound": _STR, "confirm": _CONFIRM}, ["id"]), h_alarm_update),
    ("clock_alarm_control", _s("clock_alarm_control", "Activa/desactiva una alarma (enable/disable + id) o apaga/pospone la que suena (stop/snooze).",
        {"action": {"type": "string", "enum": ["enable", "disable", "stop", "snooze"]}, "id": _INT}, ["action"]), h_alarm_control),
    ("clock_timer", _s("clock_timer", "Crea un temporizador (hours/minutes/seconds) o lo cancela (action=cancel).",
        {"action": {"type": "string", "enum": ["set", "cancel"]}, "hours": _INT, "minutes": _INT, "seconds": _INT, "label": _STR}), h_timer),
    ("clock_say", _s("clock_say", "Dice un texto corto por el altavoz del reloj, sin markdown.", {"text": _STR}, ["text"]), h_say),
    ("clock_display", _s("clock_display", "Muestra texto o una imagen https en la pantalla (seconds 1-600), o la limpia con clear=true.",
        {"type": {"type": "string", "enum": ["text", "image"]}, "content": _STR, "seconds": _INT, "clear": _BOOL}), h_display),
    ("clock_volume", _s("clock_volume", "Volumen del reloj 0-100.", {"level": _INT}, ["level"]), h_volume),
    ("clock_brightness", _s("clock_brightness", "Brillo de la pantalla 1-100.", {"level": _INT}, ["level"]), h_brightness),
    ("clock_bluetooth", _s("clock_bluetooth", "Bluetooth del reloj (modo altavoz).",
        {"action": {"type": "string", "enum": ["speaker", "on", "off", "status"]}}), h_bluetooth),
    ("clock_briefing", _s("clock_briefing", "Buenos dias hablado: hora, tiempo y resumen."), h_briefing),
    ("clock_settings_get", _s("clock_settings_get", "Ajustes del reloj con su esquema."), h_settings_get),
    ("clock_settings_set", _s("clock_settings_set", "Cambia ajustes del reloj (objeto clave:valor). Requiere confirm=true tras preguntar.",
        {"settings": {"type": "object"}, "confirm": _CONFIRM}, ["settings"]), h_settings_set),
)


def _wrap(fn):
    def handler(args, **kw):
        try:
            return json.dumps(fn(args or {}, **kw), ensure_ascii=False)
        except Exception as e:  # noqa: BLE001
            return json.dumps({"ok": False, "error": f"{type(e).__name__}: {e}"})
    return handler


def register(ctx):
    for name, schema, fn in _TOOLS:
        ctx.register_tool(name=name, toolset=TOOLSET, schema=schema, handler=_wrap(fn),
                          description=schema["description"])
