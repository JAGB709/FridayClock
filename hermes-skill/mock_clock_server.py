"""
Reloj de mentira para probar la skill y el panel sin el dispositivo. Imita la API
HTTP del Mi Smart Clock (los endpoints que usan las tools y el panel). Solo stdlib.

    python3 mock_clock_server.py --port 8080 --token secreto
"""

from __future__ import annotations

import argparse
import copy
import json
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse

TOKEN = "secreto"
ALARMS: list[dict] = []
_next_id = [1]

SCHEMA = [
    {"key": "snooze_min", "type": "int", "min": 1, "max": 60, "group": "Despertador", "label": "Minutos al posponer"},
    {"key": "alarm_ramp_s", "type": "int", "min": 0, "max": 600, "group": "Despertador", "label": "Segundos de subida de volumen"},
    {"key": "alarm_auto_snooze_min", "type": "int", "min": 1, "max": 60, "group": "Despertador", "label": "Minutos sonando antes de posponer sola"},
    {"key": "morning_briefing", "type": "bool", "group": "Despertador", "label": "Buenos días al descartar"},
    {"key": "night_start", "type": "int", "min": 0, "max": 23, "group": "Pantalla", "label": "Modo noche desde (hora)"},
    {"key": "night_end", "type": "int", "min": 0, "max": 23, "group": "Pantalla", "label": "Modo noche hasta (hora)"},
    {"key": "night_brightness", "type": "int", "min": 1, "max": 100, "group": "Pantalla", "label": "Brillo de noche (%)"},
    {"key": "day_brightness", "type": "int", "min": 1, "max": 100, "group": "Pantalla", "label": "Brillo de día (%)"},
    {"key": "assistant_name", "type": "string", "max_len": 30, "group": "Voz y asistente", "label": "Nombre del asistente"},
    {"key": "tts_rate", "type": "float", "min": 0.5, "max": 2.0, "group": "Voz y asistente", "label": "Velocidad de voz"},
    {"key": "wake_word", "type": "bool", "group": "Voz y asistente", "label": "Activar al oír su nombre"},
    {"key": "follow_up", "type": "bool", "group": "Voz y asistente", "label": "Seguir escuchando si pregunta algo"},
    {"key": "city", "type": "string", "max_len": 60, "group": "Ubicación", "label": "Ciudad"},
    {"key": "lat", "type": "float", "min": -90.0, "max": 90.0, "group": "Ubicación", "label": "Latitud"},
    {"key": "lon", "type": "float", "min": -180.0, "max": 180.0, "group": "Ubicación", "label": "Longitud"},
]
DEFAULT_SETTINGS = {
    "snooze_min": 5, "alarm_ramp_s": 20, "alarm_auto_snooze_min": 10, "morning_briefing": True,
    "night_start": 23, "night_end": 7, "night_brightness": 3, "day_brightness": 70,
    "assistant_name": "Friday", "tts_rate": 1.0, "wake_word": True, "follow_up": True,
    "city": "Madrid", "lat": 40.4168, "lon": -3.7038,
}
SETTINGS: dict = copy.deepcopy(DEFAULT_SETTINGS)
DEFAULT_SOUNDS = [{"id": "system:content://media/internal/audio/media/1", "name": "Amanecer", "kind": "system"}]
SOUNDS: list[dict] = copy.deepcopy(DEFAULT_SOUNDS)
OPTION_KEYS = ("sound", "max_volume", "ramp_s", "snooze_min", "briefing")
OPTION_RANGES = {"max_volume": (1, 100), "ramp_s": (0, 600), "snooze_min": (1, 60)}
TIME_RE = re.compile(r"^\d{2}:\d{2}$")

FACE_VERSION = "mock"
SAMPLE_DESIGN = {
    "version": 1,
    "themes": {
        "clasico": {"name": "Clásico", "bg": {"type": "color", "color": "#000000"},
                    "fg": "#ffffff", "muted": "#90a4ae", "accent": "#4fc3f7", "font": "thin"},
        "noche_roja": {"name": "Noche roja", "bg": {"type": "color", "color": "#000000"},
                       "fg": "#8b0000", "muted": "#8b0000", "accent": "#8b0000", "font": "thin"},
    },
    "screens": {
        "principal": {"name": "Principal", "elements": [
            {"id": "hora", "type": "clock", "x": 0, "y": 0, "w": 8, "h": 3, "style": "digital"},
            {"id": "fecha", "type": "date", "x": 0, "y": 3, "w": 8, "h": 1, "format": "long", "color": "muted"},
        ]},
    },
    "profiles": [
        {"id": "dia", "name": "Día", "from": "07:00", "theme": "clasico", "screens": ["principal"],
         "rotate_s": 0, "brightness": 70, "night": False, "show_bar": True},
    ],
    "alarm_screen": {"theme": "clasico", "elements": [
        {"id": "hora", "type": "clock", "x": 0, "y": 0, "w": 8, "h": 3, "style": "digital"},
    ], "snooze_label": "Posponer {min} min", "dismiss_label": "Descartar"},
}
DESIGN: dict = copy.deepcopy(SAMPLE_DESIGN)
DESIGN_SAVED = [False]
BACKGROUNDS: list[dict] = []
BG_DATA: dict[str, bytes] = {}
PREVIEW: list[dict] = []
FACE_DATA = {
    "weather": {"temp": 19, "icon": "☀", "description": "Despejado", "max": 22, "min": 12, "rain": 10},
    "forecast": {"hours": [{"h": 15, "temp": 20, "icon": "☀"}], "days": [{"d": "mié", "min": 12, "max": 23, "icon": "☁"}]},
    "next_alarm": {"label": "Despertar", "when": "mañana", "time": "07:30"},
    "exact_alarm_missing": False,
}


def _is_int(v) -> bool:
    return isinstance(v, (int, float)) and not isinstance(v, bool) and float(v).is_integer()


def _coerce(field: dict, v):
    t = field["type"]
    if t == "int" and _is_int(v) and field["min"] <= v <= field["max"]:
        return int(v)
    if t == "float" and isinstance(v, (int, float)) and not isinstance(v, bool) and field["min"] <= v <= field["max"]:
        return float(v)
    if t == "bool" and isinstance(v, bool):
        return v
    if t == "string" and isinstance(v, str) and 0 < len(v.strip()) <= field["max_len"]:
        return v.strip()
    return None


def _validate_settings(patch: dict) -> tuple[dict, dict]:
    by_key = {f["key"]: f for f in SCHEMA}
    values, errors = {}, {}
    for k, v in patch.items():
        f = by_key.get(k)
        if f is None:
            errors[k] = "ajuste desconocido"
            continue
        c = _coerce(f, v)
        if c is None:
            errors[k] = "valor no válido"
        else:
            values[k] = c
    return values, errors


def _sound_ok(v) -> bool:
    """file:<nombre> o system:content:// / android.resource:// con algo detrás."""
    if not isinstance(v, str):
        return False
    if v.startswith("file:"):
        return bool(v[5:])
    if v.startswith("system:"):
        rest = v[7:]
        return rest.startswith(("content://", "android.resource://")) and bool(rest.split("://", 1)[1])
    return False


DAY_KEYS = ("mon", "tue", "wed", "thu", "fri", "sat", "sun")


def _days_ok(v) -> bool:
    """Lista de mon..sun, o texto "", daily, weekdays, weekend o lista separada por comas."""
    if isinstance(v, list):
        return all(d in DAY_KEYS for d in v)
    if isinstance(v, str):
        s = v.strip().lower()
        return s in ("", "daily", "weekdays", "weekend") or all(p.strip() in DAY_KEYS for p in s.split(","))
    return False


def _validate_design(d) -> dict:
    """Validación mínima y fiel en la forma de los errores (el reloj real valida mucho más)."""
    try:
        if not isinstance(d, dict) or d.get("version") != 1:
            return {"version": "debe ser 1"}
        errors = {}
        for k in ("themes", "screens", "profiles", "alarm_screen"):
            if k not in d:
                errors[k] = "falta"
        for sid, s in (d.get("screens") or {}).items():
            els = s.get("elements", [])
            for i, a in enumerate(els):
                if a["x"] + a["w"] > 8:
                    errors[f"screens.{sid}.elements.{a['id']}.w"] = "se sale de la rejilla (x + w ≤ 8)"
                elif a["y"] + a["h"] > 6:
                    errors[f"screens.{sid}.elements.{a['id']}.h"] = "se sale de la rejilla (y + h ≤ 6)"
                for b in els[i + 1:]:
                    if a["x"] < b["x"] + b["w"] and b["x"] < a["x"] + a["w"] and a["y"] < b["y"] + b["h"] and b["y"] < a["y"] + a["h"]:
                        errors[f"screens.{sid}.elements.{b['id']}"] = f"se solapa con {a['id']}"
        for p in d.get("profiles", []):
            if p.get("theme") not in d.get("themes", {}):
                errors[f"profiles.{p.get('id')}.theme"] = "el tema no existe"
        return errors
    except (KeyError, TypeError, AttributeError):
        return {"design": "estructura no válida"}


def _apply_alarm_patch(a: dict, body: dict) -> str | None:
    """Aplica el parche sobre una copia; devuelve un error o None."""
    if "time" in body:
        if not (isinstance(body["time"], str) and TIME_RE.match(body["time"])):
            return "time debe ser HH:MM"
        a["time"] = body["time"]
    if "label" in body:
        if not (isinstance(body["label"], str) and 0 < len(body["label"].strip()) <= 40):
            return "label debe tener entre 1 y 40 caracteres"
        a["label"] = body["label"].strip()
    if "days" in body:
        if not _days_ok(body["days"]):
            return "days no válido: usa weekdays, weekend, daily o mon,tue…"
        a["days"] = body["days"]
    if "enabled" in body:
        if not isinstance(body["enabled"], bool):
            return "enabled debe ser true o false"
        a["enabled"] = body["enabled"]
    for k in OPTION_KEYS:
        if k not in body:
            continue
        v = body[k]
        if v is not None:
            if k in OPTION_RANGES:
                lo, hi = OPTION_RANGES[k]
                if not (_is_int(v) and lo <= v <= hi):
                    return f"{k} debe estar entre {lo} y {hi}"
            elif k == "briefing" and not isinstance(v, bool):
                return "briefing debe ser true, false o null"
            elif k == "sound" and not _sound_ok(v):
                return "sound debe ser file:<nombre> o system:content://…"
        a[k] = v
    return None


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):  # silencio en tests
        pass

    def _send(self, code: int, obj: dict):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_bytes(self, code: int, data: bytes, ctype: str):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _auth(self) -> bool:
        return self.headers.get("X-Auth-Token") == TOKEN

    def _raw(self) -> bytes:
        n = int(self.headers.get("Content-Length", 0))
        return self.rfile.read(n) if n else b""

    def _read(self) -> dict:
        raw = self._raw()
        return json.loads(raw or b"{}")

    @property
    def _path(self) -> str:
        return urlparse(self.path).path

    @property
    def _query(self) -> dict:
        return {k: v[0] for k, v in parse_qs(urlparse(self.path).query).items()}

    def do_GET(self):
        if self._path == "/health":
            return self._send(200, {"ok": True})
        if not self._auth():
            return self._send(401, {"ok": False, "error": "token inválido"})
        if self._path == "/status":
            return self._send(200, {"ok": True, "time": int(time.time() * 1000), "ringing": False, "alarms": ALARMS, "face_version": FACE_VERSION})
        if self._path == "/alarms":
            return self._send(200, {"ok": True, "alarms": ALARMS})
        if self._path == "/settings":
            return self._send(200, {"ok": True, "settings": SETTINGS, "schema": SCHEMA})
        if self._path == "/sounds":
            return self._send(200, {"ok": True, "sounds": SOUNDS})
        if self._path == "/design":
            return self._send(200, {"ok": True, "design": DESIGN, "saved": DESIGN_SAVED[0], "face_version": FACE_VERSION})
        if self._path == "/backgrounds":
            return self._send(200, {"ok": True, "backgrounds": BACKGROUNDS})
        if self._path.startswith("/backgrounds/"):
            name = unquote(self._path.removeprefix("/backgrounds/"))
            if name not in BG_DATA:
                return self._send(404, {"ok": False, "error": f"no existe el fondo {name}"})
            ctype = "image/png" if name.endswith(".png") else "image/webp" if name.endswith(".webp") else "image/jpeg"
            return self._send_bytes(200, BG_DATA[name], ctype)
        if self._path == "/face/data":
            return self._send(200, {"ok": True, **FACE_DATA})
        self._send(404, {"ok": False, "error": "no existe"})

    def do_PUT(self):
        if not self._auth():
            return self._send(401, {"ok": False, "error": "token inválido"})
        if self._path == "/settings":
            values, errors = _validate_settings(self._read())
            if errors:
                return self._send(400, {"ok": False, "error": "ajustes no válidos", "errors": errors})
            SETTINGS.update(values)
            return self._send(200, {"ok": True, "settings": SETTINGS})
        if self._path == "/design":
            body = self._read()
            errors = _validate_design(body)
            if errors:
                return self._send(400, {"ok": False, "error": "diseño no válido", "errors": errors})
            DESIGN.clear(); DESIGN.update(copy.deepcopy(body)); DESIGN_SAVED[0] = True
            return self._send(200, {"ok": True, "design": DESIGN})
        self._send(404, {"ok": False, "error": "no existe"})

    def do_DELETE(self):
        if not self._auth():
            return self._send(401, {"ok": False, "error": "token inválido"})
        if self._path.startswith("/sounds/"):
            name = unquote(self._path.removeprefix("/sounds/"))
            before = len(SOUNDS)
            SOUNDS[:] = [s for s in SOUNDS if s["id"] != f"file:{name}"]
            if len(SOUNDS) == before:
                return self._send(404, {"ok": False, "error": f"no existe el sonido {name}"})
            return self._send(200, {"ok": True})
        if self._path == "/design/preview":
            PREVIEW.clear()
            return self._send(200, {"ok": True})
        if self._path.startswith("/backgrounds/"):
            name = unquote(self._path.removeprefix("/backgrounds/"))
            if name not in BG_DATA:
                return self._send(404, {"ok": False, "error": f"no existe el fondo {name}"})
            del BG_DATA[name]
            BACKGROUNDS[:] = [b for b in BACKGROUNDS if b["name"] != name]
            return self._send(200, {"ok": True})
        self._send(404, {"ok": False, "error": "no existe"})

    def do_POST(self):
        if not self._auth():
            return self._send(401, {"ok": False, "error": "token inválido"})
        if self._path == "/sounds":
            data = self._raw()
            name = self._query.get("name", "")
            if not data or not re.match(r"^[A-Za-z0-9_.-]+\.(mp3|ogg|wav|m4a)$", name, re.I):
                return self._send(400, {"ok": False, "error": "sonido no válido"})
            sid = f"file:{name}"
            SOUNDS[:] = [s for s in SOUNDS if s["id"] != sid] + [{"id": sid, "name": name, "kind": "file", "size": len(data)}]
            return self._send(200, {"ok": True, "id": sid})
        if self._path == "/backgrounds":
            data = self._raw()
            name = self._query.get("name", "")
            if not data or not re.match(r"^[A-Za-z0-9_-]+\.(jpg|jpeg|png|webp)$", name, re.I):
                return self._send(400, {"ok": False, "error": "imagen no válida"})
            BG_DATA[name] = data
            BACKGROUNDS[:] = [b for b in BACKGROUNDS if b["name"] != name] + [{"name": name, "size": len(data)}]
            return self._send(200, {"ok": True, "name": name})
        body = self._read()
        if self._path == "/design/reset":
            DESIGN.clear(); DESIGN.update(copy.deepcopy(SAMPLE_DESIGN)); DESIGN_SAVED[0] = False
            return self._send(200, {"ok": True, "design": DESIGN})
        if self._path == "/design/preview":
            errors = _validate_design(body.get("design"))
            if errors:
                return self._send(400, {"ok": False, "error": "diseño no válido", "errors": errors})
            PREVIEW[:] = [{"design": body["design"], "seconds": body.get("seconds", 30)}]
            return self._send(200, {"ok": True, "seconds": body.get("seconds", 30)})
        if self._path == "/sounds/preview":
            return self._send(200, {"ok": True})
        if self._path == "/alarm":
            return self._alarm(body)
        if self._path == "/timer":
            aid = _next_id[0]; _next_id[0] += 1
            a = {"id": aid, "kind": "timer", "label": body.get("label", "Temporizador"), "trigger_at": int(time.time() * 1000)}
            ALARMS.append(a)
            return self._send(200, {"ok": True, "alarm": a})
        if self._path == "/bluetooth":
            act = body.get("action", "speaker")
            bt = {"available": True, "enabled": act != "off", "discoverable": act in ("speaker", "pair"),
                  "phone_connected": False, "paired": []}
            return self._send(200, {"ok": True, "bt": bt, "message": "ok"})
        if self._path in ("/speak", "/display", "/volume", "/brightness", "/listen", "/briefing"):
            return self._send(200, {"ok": True})
        self._send(404, {"ok": False, "error": "no existe"})

    def _find(self, aid) -> dict | None:
        return next((a for a in ALARMS if a.get("id") == aid), None)

    def _alarm(self, body: dict):
        action = body.get("action", "set")
        if action == "set":
            t = body.get("time", "")
            if not (isinstance(t, str) and TIME_RE.match(t)):
                return self._send(400, {"ok": False, "error": "time debe ser HH:MM"})
            aid = _next_id[0]
            a = {"id": aid, "time": t, "label": body.get("label", "Alarma"), "days": [],
                 "enabled": True, "kind": "alarm", **{k: None for k in OPTION_KEYS}}
            err = _apply_alarm_patch(a, {k: body[k] for k in ("days", "enabled", *OPTION_KEYS) if k in body})
            if err:
                return self._send(400, {"ok": False, "error": err})
            _next_id[0] += 1
            ALARMS.append(a)
            return self._send(200, {"ok": True, "alarm": a})
        if action == "update":
            cur = self._find(body.get("id"))
            if cur is None:
                return self._send(404, {"ok": False, "error": f"no existe la alarma {body.get('id')}"})
            new = copy.deepcopy(cur)
            err = _apply_alarm_patch(new, body)
            if err:
                return self._send(400, {"ok": False, "error": err})
            cur.clear(); cur.update(new)
            return self._send(200, {"ok": True, "alarm": cur})
        if action in ("enable", "disable"):
            cur = self._find(body.get("id"))
            if cur is None:
                return self._send(400, {"ok": False, "error": f"no existe la alarma {body.get('id')}"})
            cur["enabled"] = action == "enable"
            return self._send(200, {"ok": True, "alarm": cur})
        if action in ("stop", "snooze"):
            return self._send(200, {"ok": True})
        if action == "cancel":
            before = len(ALARMS)
            if body.get("all"):
                ALARMS.clear()
            elif "id" in body:
                ALARMS[:] = [a for a in ALARMS if a.get("id") != body["id"]]
            elif "time" in body:
                ALARMS[:] = [a for a in ALARMS if a.get("time") != body["time"]]
            return self._send(200, {"ok": True, "deleted": before - len(ALARMS)})
        return self._send(400, {"ok": False, "error": f"action desconocida: {action}"})


def reset():
    ALARMS.clear()
    _next_id[0] = 1
    SETTINGS.clear(); SETTINGS.update(copy.deepcopy(DEFAULT_SETTINGS))
    SOUNDS[:] = copy.deepcopy(DEFAULT_SOUNDS)
    DESIGN.clear(); DESIGN.update(copy.deepcopy(SAMPLE_DESIGN)); DESIGN_SAVED[0] = False
    BACKGROUNDS.clear(); BG_DATA.clear(); PREVIEW.clear()


def make_server(port: int = 0, token: str = "secreto") -> ThreadingHTTPServer:
    global TOKEN
    TOKEN = token
    return ThreadingHTTPServer(("127.0.0.1", port), Handler)


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--port", type=int, default=8080)
    p.add_argument("--token", default="secreto")
    x = p.parse_args()
    make_server(x.port, x.token).serve_forever()
