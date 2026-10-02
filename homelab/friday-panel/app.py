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
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote

import httpx
from fastapi import Body, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

STATIC = Path(__file__).parent / "static"
MAX_SOUND_BYTES = 5 * 1024 * 1024
SOUND_EXTS = {".mp3", ".ogg", ".wav", ".m4a"}
COOKIE = "friday_panel"
COOKIE_MAX_AGE = 30 * 24 * 3600

# Acciones rápidas de Inicio -> (ruta del reloj, cuerpo).
QUICK_ACTIONS = {
    "stop": ("/alarm", {"action": "stop"}),
    "snooze": ("/alarm", {"action": "snooze"}),
    "test": ("/timer", {"seconds": 10, "label": "Prueba de alarma"}),
    "briefing": ("/briefing", {}),
}


@dataclass
class Config:
    clock_url: str
    clock_token: str
    password: str = ""
    timeout_s: float = 5.0

    @classmethod
    def from_env(cls) -> "Config":
        url = os.getenv("CLOCK_URL", "").rstrip("/")
        token = os.getenv("CLOCK_TOKEN", "")
        if not url or not token:
            raise SystemExit("Faltan CLOCK_URL o CLOCK_TOKEN en el entorno (.env)")
        return cls(url, token, os.getenv("PANEL_PASSWORD", ""), float(os.getenv("CLOCK_TIMEOUT_S", "5")))


def _session_value(password: str) -> str:
    """Valor de la cookie: derivado de la contraseña, nunca la contraseña."""
    return hmac.new(password.encode(), b"friday-panel-session", hashlib.sha256).hexdigest()


def _error(status: int, msg: str) -> JSONResponse:
    return JSONResponse({"ok": False, "error": msg}, status_code=status)


def create_app(cfg: Config) -> FastAPI:
    app = FastAPI(title="friday-panel", docs_url=None, redoc_url=None, openapi_url=None)

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
        return _error(400, "Petición no válida")

    async def clock(method: str, path: str, *, json: dict | None = None,
                    content: bytes | None = None, params: dict | None = None) -> JSONResponse:
        headers = {"X-Auth-Token": cfg.clock_token}
        if content is not None:
            headers["Content-Type"] = "application/octet-stream"
        try:
            async with httpx.AsyncClient(base_url=cfg.clock_url, timeout=cfg.timeout_s) as c:
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

    @app.middleware("http")
    async def require_login(request: Request, call_next):
        if cfg.password and request.url.path.startswith("/api/"):
            given = request.cookies.get(COOKIE, "")
            if not hmac.compare_digest(given.encode(), _session_value(cfg.password).encode()):
                return JSONResponse({"ok": False, "error": "Inicia sesión", "login_required": True}, status_code=401)
        return await call_next(request)

    @app.post("/login")
    async def login(body: dict = Body(default_factory=dict)):
        if not cfg.password:
            return {"ok": True}
        if not hmac.compare_digest(str(body.get("password", "")).encode(), cfg.password.encode()):
            return _error(401, "Contraseña incorrecta")
        resp = JSONResponse({"ok": True})
        resp.set_cookie(COOKIE, _session_value(cfg.password), max_age=COOKIE_MAX_AGE, httponly=True, samesite="strict")
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
        data = await request.body()
        if not data:
            return _error(400, "Archivo vacío")
        if len(data) > MAX_SOUND_BYTES:
            return _error(413, "El sonido supera 5 MB")
        return await clock("POST", "/sounds", content=data, params={"name": name})

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

    # La web, al final para que no tape /api.
    app.mount("/", StaticFiles(directory=STATIC, html=True), name="static")
    return app


def main() -> None:
    import uvicorn

    cfg = Config.from_env()
    uvicorn.run(create_app(cfg), host=os.getenv("PANEL_BIND", "127.0.0.1"), port=int(os.getenv("PANEL_PORT", "8770")))


if __name__ == "__main__":
    main()
