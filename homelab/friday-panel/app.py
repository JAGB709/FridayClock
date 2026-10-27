"""
friday-panel: panel web del despertador Friday (móvil primero).

Sirve la web de static/ y hace de proxy hacia la API HTTP del reloj. El token del
reloj vive solo aquí (entorno/.env); el navegador nunca lo ve. No guarda estado:
el reloj es la fuente de verdad.

    CLOCK_URL=http://100.x.y.z:8080 CLOCK_TOKEN=... python app.py
"""

from __future__ import annotations

import hashlib
import hmac
import os
import re
import secrets
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote, urlparse

import httpx
from fastapi import Body, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, Response
from fastapi.staticfiles import StaticFiles

STATIC = Path(__file__).parent / "static"
MAX_SOUND_BYTES = 5 * 1024 * 1024
UPLOAD_TIMEOUT_S = 60.0  # subir un sonido tarda más que una petición normal
SOUND_EXTS = {".mp3", ".ogg", ".wav", ".m4a"}
COOKIE = "friday_panel"
COOKIE_MAX_AGE = 30 * 24 * 3600
LOGIN_MAX_FAILS = 5
LOGIN_WINDOW_S = 60
MAX_SESSIONS = 1000

CSP_BASE = ("default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; "
            "frame-src 'self'; base-uri 'none'; form-action 'self'; ")

# Acciones rápidas de Inicio -> (ruta del reloj, cuerpo).
QUICK_ACTIONS = {
    "stop": ("/alarm", {"action": "stop"}),
    "snooze": ("/alarm", {"action": "snooze"}),
    "test": ("/timer", {"seconds": 10, "label": "Prueba de alarma"}),
    "briefing": ("/briefing", {}),
}

FACE_FILES = ("index.html", "tokens.css", "face.css", "icons.js", "face-core.js", "face.js")
BG_EXTS = {".jpg", ".jpeg", ".png", ".webp"}
BG_MIME = {"image/jpeg", "image/png", "image/webp"}
MAX_BG_BYTES = 8 * 1024 * 1024
MAX_REMINDER_TEXT = 300


SPOOL_RAM_BYTES = 256 * 1024  # por encima de esto la subida va a un fichero temporal, no a RAM
CHUNK = 64 * 1024


async def spool_body(request: Request, limit: int):
    """Vuelca el cuerpo a un fichero temporal cortando al pasar `limit`.

    Devuelve (fichero, tamaño) o (None, tamaño) si se supera el tope. No confía en Content-Length.
    """
    f = tempfile.SpooledTemporaryFile(max_size=SPOOL_RAM_BYTES)
    size = 0
    async for chunk in request.stream():
        size += len(chunk)
        if size > limit:
            f.close()
            return None, size
        f.write(chunk)
    f.seek(0)
    return f, size


async def iter_file(f):
    try:
        while True:
            chunk = f.read(CHUNK)
            if not chunk:
                break
            yield chunk
    finally:
        f.close()


def face_version(face_dir: Path) -> str:
    """SHA-256 abreviado de los archivos del motor, igual que el que calcula el reloj sobre su APK."""
    h = hashlib.sha256()
    for name in FACE_FILES:
        h.update((face_dir / name).read_bytes())
    return h.hexdigest()[:12]


@dataclass
class Config:
    clock_url: str
    clock_token: str
    password: str = ""
    timeout_s: float = 5.0
    allowed_hosts: str = ""  # nombres de Host aceptados, separados por comas; vacío = cualquiera
    cookie_secure: bool = False
    bridge_url: str = ""    # puente del homelab (recordatorios); opcional
    bridge_token: str = ""

    @classmethod
    def from_env(cls) -> "Config":
        url = os.getenv("CLOCK_URL", "").rstrip("/")
        token = os.getenv("CLOCK_TOKEN", "")
        if not url or not token:
            raise SystemExit("Faltan CLOCK_URL o CLOCK_TOKEN en el entorno (.env)")
        if not url.startswith(("http://", "https://")):
            raise SystemExit("CLOCK_URL debe empezar por http:// o https:// (p. ej. http://100.x.y.z:8080)")
        return cls(url, token, os.getenv("PANEL_PASSWORD", ""), float(os.getenv("CLOCK_TIMEOUT_S", "5")),
                   os.getenv("PANEL_ALLOWED_HOSTS", ""), os.getenv("PANEL_COOKIE_SECURE", "") == "1",
                   os.getenv("BRIDGE_URL", "").rstrip("/"), os.getenv("BRIDGE_TOKEN", ""))


def _error(status: int, msg: str) -> JSONResponse:
    return JSONResponse({"ok": False, "error": msg}, status_code=status)


def create_app(cfg: Config) -> FastAPI:
    app = FastAPI(title="friday-panel", docs_url=None, redoc_url=None, openapi_url=None)

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
        return _error(400, "Petición no válida")

    allowed = {h.strip().lower() for h in cfg.allowed_hosts.split(",") if h.strip()}
    sessions: dict[str, float] = {}       # token aleatorio -> caducidad (solo en servidor)
    fails: dict[str, list[float]] = {}    # IP -> instantes de intentos fallidos

    def session_ok(token: str) -> bool:
        exp = sessions.get(token)
        if exp is None:
            return False
        if exp < time.time():
            sessions.pop(token, None)
            return False
        return True

    async def clock(method: str, path: str, *, json: dict | None = None,
                    content=None, params: dict | None = None,
                    timeout: float | None = None, length: int | None = None) -> JSONResponse:
        headers = {"X-Auth-Token": cfg.clock_token}
        if content is not None:
            headers["Content-Type"] = "application/octet-stream"
            if length is not None:  # el reloj no acepta Transfer-Encoding: chunked
                headers["Content-Length"] = str(length)
        try:
            async with httpx.AsyncClient(base_url=cfg.clock_url, timeout=timeout or cfg.timeout_s) as c:
                r = await c.request(method, path, json=json, content=content, params=params, headers=headers)
        except httpx.HTTPError:
            return _error(502, "El reloj no responde")
        if r.status_code == 401:
            return _error(502, "El token del reloj (CLOCK_TOKEN) no es válido")
        try:
            data = r.json()
        except ValueError:
            data = {"ok": r.is_success}
            if not r.is_success:
                data["error"] = f"El reloj respondió {r.status_code}"
        if not isinstance(data, dict):
            data = {"ok": r.is_success, "data": data}
        return JSONResponse(data, status_code=r.status_code)

    async def bridge(method: str, path: str, *, json: dict | None = None) -> JSONResponse:
        """Recordatorios: los guarda el puente (BRIDGE_URL). El token del puente tampoco llega al navegador."""
        if not cfg.bridge_url or not cfg.bridge_token:
            return JSONResponse({"ok": False, "configured": False,
                                 "error": "Los recordatorios no están configurados (BRIDGE_URL y BRIDGE_TOKEN)"},
                                status_code=503)
        try:
            async with httpx.AsyncClient(base_url=cfg.bridge_url, timeout=cfg.timeout_s) as c:
                r = await c.request(method, path, json=json, headers={"Authorization": f"Bearer {cfg.bridge_token}"})
        except httpx.HTTPError:
            return _error(502, "El puente de voz no responde")
        if r.status_code in (401, 503):
            return _error(502, "El token del puente (BRIDGE_TOKEN) no es válido o falta en el puente")
        try:
            data = r.json()
        except ValueError:
            data = {}
        if not isinstance(data, dict):
            data = {}
        if not r.is_success:
            detail = data.get("detail")
            return _error(r.status_code, detail if isinstance(detail, str) else f"El puente respondió {r.status_code}")
        return JSONResponse({**data, "ok": True})

    @app.middleware("http")
    async def host_origin(request: Request, call_next):
        # Frena el DNS rebinding (Host) y las peticiones de otro origen que cambian estado (Origin).
        host = request.headers.get("host", "")
        if allowed and host.rsplit(":", 1)[0].strip("[]").lower() not in allowed:
            return _error(400, "Host no permitido")
        origin = request.headers.get("origin")
        if origin is not None and request.method not in ("GET", "HEAD", "OPTIONS") \
                and urlparse(origin).netloc.lower() != host.lower():
            return _error(403, "Origen no permitido")
        return await call_next(request)

    @app.middleware("http")
    async def require_header(request: Request, call_next):
        # Anti-CSRF: un formulario de otra web no puede añadir cabeceras propias.
        p = request.url.path
        if request.method not in ("GET", "HEAD", "OPTIONS") and (p.startswith("/api/") or p == "/login") \
                and request.headers.get("x-friday") != "1":
            return _error(403, "Petición no permitida")
        return await call_next(request)

    @app.middleware("http")
    async def require_login(request: Request, call_next):
        if cfg.password and request.url.path.startswith("/api/"):
            if not session_ok(request.cookies.get(COOKIE, "")):
                return JSONResponse({"ok": False, "error": "Inicia sesión", "login_required": True}, status_code=401)
        return await call_next(request)

    @app.post("/login")
    async def login(request: Request, body: dict = Body(default_factory=dict)):
        if not cfg.password:
            return {"ok": True}
        now = time.time()
        ip = request.client.host if request.client else "?"
        recent = [t for t in fails.get(ip, []) if now - t < LOGIN_WINDOW_S]
        if len(recent) >= LOGIN_MAX_FAILS:
            fails[ip] = recent
            return _error(429, "Demasiados intentos: espera un minuto")
        if not hmac.compare_digest(str(body.get("password", "")).encode(), cfg.password.encode()):
            fails[ip] = [*recent, now]
            while len(fails) > MAX_SESSIONS:
                fails.pop(next(iter(fails)))
            return _error(401, "Contraseña incorrecta")
        fails.pop(ip, None)
        for tok in [t for t, exp in sessions.items() if exp < now]:
            del sessions[tok]
        while len(sessions) >= MAX_SESSIONS:
            sessions.pop(next(iter(sessions)))
        token = secrets.token_urlsafe(32)
        sessions[token] = now + COOKIE_MAX_AGE
        resp = JSONResponse({"ok": True})
        resp.set_cookie(COOKIE, token, max_age=COOKIE_MAX_AGE, httponly=True, samesite="strict",
                        secure=cfg.cookie_secure)
        return resp

    @app.get("/health")
    async def health():
        r = await clock("GET", "/health")
        return {"ok": True, "clock": r.status_code == 200}

    @app.get("/api/status")
    async def status():
        return await clock("GET", "/status")

    @app.get("/api/alarms")
    async def list_alarms():
        return await clock("GET", "/alarms")

    @app.post("/api/alarms")
    async def create_alarm(body: dict = Body(...)):
        return await clock("POST", "/alarm", json={**body, "action": "set"})

    @app.put("/api/alarms/{alarm_id}")
    async def update_alarm(alarm_id: int, body: dict = Body(...)):
        return await clock("POST", "/alarm", json={**body, "action": "update", "id": alarm_id})

    @app.delete("/api/alarms/{alarm_id}")
    async def delete_alarm(alarm_id: int):
        return await clock("POST", "/alarm", json={"action": "cancel", "id": alarm_id})

    @app.get("/api/timers")
    async def list_timers():
        return await clock("GET", "/timers")

    @app.post("/api/timer")
    async def timer(body: dict = Body(default_factory=dict)):
        # Crear (seconds/minutes/hours/label) o controlar (action pause|resume|cancel con id o pid).
        allowed = {"action", "seconds", "minutes", "hours", "label", "id", "pid"}
        return await clock("POST", "/timer", json={k: v for k, v in body.items() if k in allowed})

    @app.get("/api/stopwatch")
    async def get_stopwatch():
        return await clock("GET", "/stopwatch")

    @app.post("/api/stopwatch")
    async def stopwatch(body: dict = Body(default_factory=dict)):
        return await clock("POST", "/stopwatch", json={"action": str(body.get("action", "status"))})

    @app.get("/api/settings")
    async def get_settings():
        return await clock("GET", "/settings")

    @app.put("/api/settings")
    async def put_settings(body: dict = Body(...)):
        return await clock("PUT", "/settings", json=body)

    @app.get("/api/sounds")
    async def list_sounds():
        return await clock("GET", "/sounds")

    @app.post("/api/sounds")
    async def upload_sound(request: Request, name: str):
        if Path(name).suffix.lower() not in SOUND_EXTS:
            return _error(400, "Formato no admitido: usa mp3, ogg, wav o m4a")
        try:
            content_length = int(request.headers.get("content-length") or 0)
        except ValueError:
            return _error(400, "Content-Length no válido")
        if content_length > MAX_SOUND_BYTES:
            return _error(413, "El sonido supera 5 MB")
        f, size = await spool_body(request, MAX_SOUND_BYTES)
        if f is None:
            return _error(413, "El sonido supera 5 MB")
        if size == 0:
            f.close()
            return _error(400, "Archivo vacío")
        return await clock("POST", "/sounds", content=iter_file(f), length=size, params={"name": name},
                           timeout=UPLOAD_TIMEOUT_S)

    @app.delete("/api/sounds/{name}")
    async def delete_sound(name: str):
        return await clock("DELETE", f"/sounds/{quote(name, safe='')}")

    @app.post("/api/sounds/preview")
    async def preview_sound(body: dict = Body(default_factory=dict)):
        return await clock("POST", "/sounds/preview", json=body)

    @app.post("/api/do/{action}")
    async def quick_action(action: str, body: dict = Body(default_factory=dict)):
        if action == "speak":
            text = str(body.get("text", "")).strip()
            if not text:
                return _error(400, "Escribe qué quieres que diga")
            return await clock("POST", "/speak", json={"text": text})
        if action not in QUICK_ACTIONS:
            return _error(404, f"acción desconocida: {action}")
        path, payload = QUICK_ACTIONS[action]
        return await clock("POST", path, json=payload)

    @app.get("/api/reminders")
    async def list_reminders():
        return await bridge("GET", "/v1/reminders")

    @app.post("/api/reminders")
    async def create_reminder(body: dict = Body(...)):
        # Solo se reenvían los campos conocidos, con su tipo: el puente vuelve a validar todo.
        text = body.get("text")
        if not isinstance(text, str) or not text.strip():
            return _error(400, "Escribe el texto del recordatorio")
        if len(text) > MAX_REMINDER_TEXT:
            return _error(400, f"El texto supera {MAX_REMINDER_TEXT} caracteres")
        at = body.get("at")
        if not isinstance(at, str) or not at:
            return _error(400, "Elige la fecha y la hora")
        out = {"text": text, "at": at[:40], "repeat": str(body.get("repeat", "none"))[:20],
               "channel": str(body.get("channel", "clock"))[:20]}
        return await bridge("POST", "/v1/reminders", json=out)

    @app.delete("/api/reminders/{rid}")
    async def delete_reminder(rid: str):
        if not re.fullmatch(r"[0-9a-f]{12}", rid):
            return _error(404, "No existe ese recordatorio")
        return await bridge("DELETE", f"/v1/reminders/{rid}")

    panel_face_version = face_version(STATIC / "face")

    @app.get("/api/design")
    async def get_design():
        return await clock("GET", "/design")

    @app.put("/api/design")
    async def put_design(body: dict = Body(...)):
        return await clock("PUT", "/design", json=body)

    @app.post("/api/design/reset")
    async def reset_design():
        return await clock("POST", "/design/reset", json={})

    @app.post("/api/design/preview")
    async def preview_design(body: dict = Body(...)):
        return await clock("POST", "/design/preview", json=body)

    @app.delete("/api/design/preview")
    async def cancel_preview():
        return await clock("DELETE", "/design/preview")

    @app.get("/api/face-data")
    async def face_data():
        return await clock("GET", "/face/data")

    @app.get("/api/face-version")
    async def get_face_version():
        return {"ok": True, "face_version": panel_face_version}

    @app.get("/api/backgrounds")
    async def list_backgrounds():
        return await clock("GET", "/backgrounds")

    @app.post("/api/backgrounds")
    async def upload_background(request: Request, name: str):
        if Path(name).suffix.lower() not in BG_EXTS:
            return _error(400, "Formato no admitido: usa jpg, png o webp")
        try:
            content_length = int(request.headers.get("content-length") or 0)
        except ValueError:
            return _error(400, "Content-Length no válido")
        if content_length > MAX_BG_BYTES:
            return _error(413, "La imagen supera 8 MB")
        f, size = await spool_body(request, MAX_BG_BYTES)
        if f is None:
            return _error(413, "La imagen supera 8 MB")
        if size == 0:
            f.close()
            return _error(400, "Archivo vacío")
        return await clock("POST", "/backgrounds", content=iter_file(f), length=size, params={"name": name},
                           timeout=UPLOAD_TIMEOUT_S)

    @app.delete("/api/backgrounds/{name}")
    async def delete_background(name: str):
        return await clock("DELETE", f"/backgrounds/{quote(name, safe='')}")

    @app.get("/api/bg/{name}")
    async def get_background(name: str):
        if Path(name).name != name or Path(name).suffix.lower() not in BG_EXTS:
            return _error(400, "Nombre no válido")
        try:
            async with httpx.AsyncClient(base_url=cfg.clock_url, timeout=cfg.timeout_s) as c:
                async with c.stream("GET", f"/backgrounds/{quote(name, safe='')}",
                                    headers={"X-Auth-Token": cfg.clock_token}) as r:
                    if r.status_code == 401:
                        return _error(502, "El token del reloj (CLOCK_TOKEN) no es válido")
                    if r.status_code == 404:
                        return _error(404, "No existe ese fondo")
                    if r.status_code != 200:
                        return _error(502, "No se pudo leer el fondo")
                    declared = int(r.headers.get("content-length") or 0)
                    if declared > MAX_BG_BYTES:
                        return _error(502, "El fondo del reloj es demasiado grande")
                    body = bytearray()
                    async for chunk in r.aiter_bytes():
                        body += chunk
                        if len(body) > MAX_BG_BYTES:
                            return _error(502, "El fondo del reloj es demasiado grande")
                    ctype = r.headers.get("content-type", "").split(";")[0].strip().lower()
        except (httpx.HTTPError, ValueError):
            return _error(502, "El reloj no responde")
        if ctype not in BG_MIME:
            ctype = "application/octet-stream"
        return Response(content=bytes(body), media_type=ctype,
                        headers={"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"})

    @app.middleware("http")
    async def security_headers(request: Request, call_next):
        # Última en registrarse = más externa: también cubre las respuestas de error de las demás.
        resp = await call_next(request)
        embeddable = request.url.path.startswith("/face/")  # el panel incrusta el reloj en un iframe propio
        resp.headers["Content-Security-Policy"] = CSP_BASE + (
            "frame-ancestors 'self'" if embeddable else "frame-ancestors 'none'")
        resp.headers["X-Frame-Options"] = "SAMEORIGIN" if embeddable else "DENY"
        resp.headers["X-Content-Type-Options"] = "nosniff"
        resp.headers["Referrer-Policy"] = "no-referrer"
        return resp

    # La web, al final para que no tape /api.
    app.mount("/", StaticFiles(directory=STATIC, html=True), name="static")
    return app


def main() -> None:
    import uvicorn

    cfg = Config.from_env()
    uvicorn.run(create_app(cfg), host=os.getenv("PANEL_BIND", "127.0.0.1"), port=int(os.getenv("PANEL_PORT", "8770")),
                server_header=False)


if __name__ == "__main__":
    main()
