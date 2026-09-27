"""
Reloj de mentira para probar la skill sin el dispositivo. Imita la API HTTP del
Mi Smart Clock (los endpoints que usan las tools). Solo stdlib.

    python3 mock_clock_server.py --port 8080 --token secreto
"""

from __future__ import annotations

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = "secreto"
ALARMS: list[dict] = []
_next_id = [1]


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

    def _auth(self) -> bool:
        return self.headers.get("X-Auth-Token") == TOKEN

    def _read(self) -> dict:
        n = int(self.headers.get("Content-Length", 0))
        return json.loads(self.rfile.read(n) or b"{}") if n else {}

    def do_GET(self):
        if self.path == "/health":
            return self._send(200, {"ok": True})
        if not self._auth():
            return self._send(401, {"ok": False, "error": "token inválido"})
        if self.path == "/status":
            return self._send(200, {"ok": True, "time": int(time.time() * 1000), "ringing": False, "alarms": ALARMS})
        if self.path == "/alarms":
            return self._send(200, {"ok": True, "alarms": ALARMS})
        self._send(404, {"ok": False, "error": "no existe"})

    def do_POST(self):
        if not self._auth():
            return self._send(401, {"ok": False, "error": "token inválido"})
        body = self._read()
        if self.path == "/alarm":
            return self._alarm(body)
        if self.path == "/timer":
            aid = _next_id[0]; _next_id[0] += 1
            a = {"id": aid, "kind": "timer", "label": body.get("label", "Temporizador")}
            ALARMS.append(a)
            return self._send(200, {"ok": True, "alarm": a})
        if self.path in ("/speak", "/display", "/volume", "/brightness", "/listen", "/briefing"):
            return self._send(200, {"ok": True})
        self._send(404, {"ok": False, "error": "no existe"})

    def _alarm(self, body: dict):
        action = body.get("action", "set")
        if action == "set":
            t = body.get("time", "")
            if not (len(t) == 5 and t[2] == ":" and t.replace(":", "").isdigit()):
                return self._send(400, {"ok": False, "error": "time debe ser HH:MM"})
            aid = _next_id[0]; _next_id[0] += 1
            a = {"id": aid, "time": t, "label": body.get("label", "Alarma"), "enabled": True}
            ALARMS.append(a)
            return self._send(200, {"ok": True, "alarm": a})
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
