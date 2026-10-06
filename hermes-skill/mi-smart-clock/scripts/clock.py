#!/usr/bin/env python3
"""CLI para controlar el Mi Smart Clock (app Jarvis Clock) desde Hermes. Solo stdlib.

Configuración (la primera que exista):
  - variables de entorno CLOCK_URL y CLOCK_TOKEN
  - ~/.config/mi-smart-clock.json  {"url": "http://100.x.y.z:8080", "token": "..."}
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def config():
    url, token = os.getenv("CLOCK_URL"), os.getenv("CLOCK_TOKEN")
    path = os.path.expanduser("~/.config/mi-smart-clock.json")
    if (not url or not token) and os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            c = json.load(f)
        url, token = url or c.get("url"), token or c.get("token")
    if not url or not token:
        sys.exit("Falta CLOCK_URL/CLOCK_TOKEN (o ~/.config/mi-smart-clock.json)")
    return url.rstrip("/"), token


def call(method, path, body=None):
    url, token = config()
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        url + path, data=data, method=method,
        headers={"X-Auth-Token": token, "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            out = json.load(r)
    except urllib.error.HTTPError as e:
        out = json.loads(e.read() or b"{}") or {"ok": False, "error": f"HTTP {e.code}"}
    except OSError as e:
        out = {"ok": False, "error": f"reloj inalcanzable: {e}"}
    print(json.dumps(out, ensure_ascii=False, indent=2))
    return 0 if out.get("ok") else 1


def main():
    p = argparse.ArgumentParser(prog="clock.py", description=__doc__)
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("status", help="hora, próxima alarma, si está sonando, alarmas")
    sub.add_parser("alarms", help="lista de alarmas y temporizadores")

    a = sub.add_parser("alarm-set", help="crear alarma")
    a.add_argument("time", help="HH:MM en 24h")
    a.add_argument("--label", default="Alarma")
    a.add_argument("--days", default="", help="weekdays | weekend | daily | mon,wed,fri (vacío = una vez)")

    c = sub.add_parser("alarm-cancel", help="borrar alarma(s)")
    g = c.add_mutually_exclusive_group(required=True)
    g.add_argument("--id", type=int)
    g.add_argument("--time", help="HH:MM")
    g.add_argument("--all", action="store_true")

    for name in ("alarm-enable", "alarm-disable"):
        e = sub.add_parser(name)
        e.add_argument("id", type=int)

    u = sub.add_parser("alarm-update", help="editar alarma (solo lo indicado)")
    u.add_argument("id", type=int)
    u.add_argument("--time")
    u.add_argument("--label")
    u.add_argument("--days")
    u.add_argument("--max-volume", type=int)
    u.add_argument("--snooze-min", type=int)

    sub.add_parser("alarm-stop", help="apagar la alarma que está sonando")
    sub.add_parser("alarm-snooze", help="posponer la alarma que está sonando")

    t = sub.add_parser("timer", help="temporizador")
    t.add_argument("--seconds", type=int, default=0)
    t.add_argument("--minutes", type=int, default=0)
    t.add_argument("--hours", type=int, default=0)
    t.add_argument("--label", default="Temporizador")
    sub.add_parser("timer-cancel")

    s = sub.add_parser("say", help="decir un texto por el altavoz")
    s.add_argument("text")

    d = sub.add_parser("show", help="mostrar en pantalla")
    d.add_argument("content", help="texto, HTML, URL de imagen o URL web")
    d.add_argument("--type", choices=["text", "html", "image", "url"], default="text")
    d.add_argument("--seconds", type=int, default=20)
    sub.add_parser("show-clear")

    v = sub.add_parser("volume")
    v.add_argument("level", type=int, help="0-100")
    b = sub.add_parser("brightness")
    b.add_argument("level", type=int, help="1-100")

    bt = sub.add_parser("bluetooth", help="modo altavoz: enciende BT y hazlo visible para emparejar el móvil")
    bt.add_argument("action", nargs="?", default="speaker", choices=["speaker", "on", "off", "status"])

    sub.add_parser("briefing", help="buenos días: hora, tiempo y resumen por voz")
    sub.add_parser("listen", help="abrir el micro del reloj")

    sub.add_parser("settings", help="ver ajustes y su esquema")
    st = sub.add_parser("settings-set", help="cambiar ajustes: clave=valor ...")
    st.add_argument("pairs", nargs="+", help="p.ej. snooze_min=10 wake_word=false city=Sevilla")
    sub.add_parser("sounds", help="sonidos de alarma disponibles")

    sub.add_parser("design", help="ver el diseño de pantallas del reloj")
    ds = sub.add_parser("design-set", help="guardar un diseño desde un archivo JSON")
    ds.add_argument("file")
    dp = sub.add_parser("design-preview", help="probar un diseño (archivo JSON) en el reloj sin guardarlo")
    dp.add_argument("file")
    dp.add_argument("--seconds", type=int, default=30)
    sub.add_parser("design-preview-cancel", help="cancelar la prueba de diseño en curso")
    sub.add_parser("design-reset", help="restaurar el diseño inicial")
    sub.add_parser("backgrounds", help="fondos de pantalla subidos")

    x = p.parse_args()
    if x.cmd == "status":
        return call("GET", "/status")
    if x.cmd == "alarms":
        return call("GET", "/alarms")
    if x.cmd == "alarm-set":
        return call("POST", "/alarm", {"action": "set", "time": x.time, "label": x.label, "days": x.days})
    if x.cmd == "alarm-cancel":
        body = {"action": "cancel"}
        if x.id is not None:
            body["id"] = x.id
        elif x.time:
            body["time"] = x.time
        else:
            body["all"] = True
        return call("POST", "/alarm", body)
    if x.cmd in ("alarm-enable", "alarm-disable"):
        return call("POST", "/alarm", {"action": x.cmd.split("-")[1], "id": x.id})
    if x.cmd == "alarm-update":
        body = {"action": "update", "id": x.id}
        for k, v in (("time", x.time), ("label", x.label), ("days", x.days),
                     ("max_volume", x.max_volume), ("snooze_min", x.snooze_min)):
            if v is not None:
                body[k] = v
        return call("POST", "/alarm", body)
    if x.cmd == "settings":
        return call("GET", "/settings")
    if x.cmd == "settings-set":
        patch = {}
        for pair in x.pairs:
            k, _, v = pair.partition("=")
            try:
                patch[k] = json.loads(v)      # números, true/false
            except ValueError:
                patch[k] = v                  # texto
        return call("PUT", "/settings", patch)
    if x.cmd == "sounds":
        return call("GET", "/sounds")
    if x.cmd == "design":
        return call("GET", "/design")
    if x.cmd in ("design-set", "design-preview"):
        try:
            with open(x.file, encoding="utf-8") as f:
                design = json.load(f)
        except (OSError, ValueError) as e:
            print(json.dumps({"ok": False, "error": f"no se pudo leer {x.file}: {e}"}, ensure_ascii=False, indent=2))
            return 1
        if x.cmd == "design-set":
            return call("PUT", "/design", design)
        return call("POST", "/design/preview", {"design": design, "seconds": x.seconds})
    if x.cmd == "design-preview-cancel":
        return call("DELETE", "/design/preview")
    if x.cmd == "design-reset":
        return call("POST", "/design/reset", {})
    if x.cmd == "backgrounds":
        return call("GET", "/backgrounds")
    if x.cmd == "alarm-stop":
        return call("POST", "/alarm", {"action": "stop"})
    if x.cmd == "alarm-snooze":
        return call("POST", "/alarm", {"action": "snooze"})
    if x.cmd == "timer":
        return call("POST", "/timer", {"seconds": x.seconds, "minutes": x.minutes, "hours": x.hours, "label": x.label})
    if x.cmd == "timer-cancel":
        return call("POST", "/timer", {"action": "cancel"})
    if x.cmd == "say":
        return call("POST", "/speak", {"text": x.text})
    if x.cmd == "show":
        return call("POST", "/display", {"type": x.type, "content": x.content, "duration_ms": x.seconds * 1000})
    if x.cmd == "show-clear":
        return call("POST", "/display", {"action": "clear"})
    if x.cmd == "volume":
        return call("POST", "/volume", {"level": x.level})
    if x.cmd == "brightness":
        return call("POST", "/brightness", {"level": x.level})
    if x.cmd == "bluetooth":
        return call("POST", "/bluetooth", {"action": x.action})
    if x.cmd == "briefing":
        return call("POST", "/briefing", {})
    if x.cmd == "listen":
        return call("POST", "/listen", {})
    return 2


if __name__ == "__main__":
    sys.exit(main())
