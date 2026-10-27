#!/usr/bin/env python3
"""CLI para controlar el Mi Smart Clock (app Jarvis Clock) desde Hermes. Solo stdlib.

Configuración (la primera que exista):
  - variables de entorno CLOCK_URL y CLOCK_TOKEN
  - $HERMES_HOME/.config/mi-smart-clock.json o ~/.config/mi-smart-clock.json  {"url": "http://100.x.y.z:8080", "token": "..."}
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request


def config():
    url, token = os.getenv("CLOCK_URL"), os.getenv("CLOCK_TOKEN")
    candidatos = [os.path.expanduser("~/.config/mi-smart-clock.json")]
    if os.getenv("HERMES_HOME"):
        # El agente de Hermes puede tener otro HOME (p.ej. /opt/data/home)
        candidatos.insert(0, os.path.join(os.environ["HERMES_HOME"], ".config", "mi-smart-clock.json"))
    for path in candidatos:
        if (not url or not token) and os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                c = json.load(f)
            url, token = url or c.get("url"), token or c.get("token")
    if not url or not token:
        sys.exit("Falta CLOCK_URL/CLOCK_TOKEN (o ~/.config/mi-smart-clock.json)")
    return url.rstrip("/"), token


def bridge_config():
    url, token = os.getenv("BRIDGE_URL"), os.getenv("BRIDGE_TOKEN")
    path = os.path.expanduser("~/.config/mi-smart-clock.json")
    if (not url or not token) and os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            c = json.load(f)
        url, token = url or c.get("bridge_url"), token or c.get("bridge_token")
    if not url or not token:
        sys.exit("Falta BRIDGE_URL/BRIDGE_TOKEN (o bridge_url/bridge_token en ~/.config/mi-smart-clock.json)")
    return url.rstrip("/"), token


def bridge_call(method, path, body=None):
    """Recordatorios: los guarda y planifica el puente (homelab), no el reloj."""
    url, token = bridge_config()
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        url + path, data=data, method=method,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            out = json.load(r)
    except urllib.error.HTTPError as e:
        try:
            detail = json.loads(e.read() or b"{}").get("detail")
        except ValueError:
            detail = None
        out = {"ok": False, "error": str(detail) if detail else f"HTTP {e.code}"}
    except OSError as e:
        out = {"ok": False, "error": f"puente inalcanzable: {type(e).__name__}"}
    print(json.dumps(out, ensure_ascii=False, indent=2))
    return 0 if out.get("ok") else 1


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
    tc = sub.add_parser("timer-cancel", help="cancelar un temporizador (--id/--pid) o todos")
    tc.add_argument("--id", type=int)
    tc.add_argument("--pid", type=int, help="uno en pausa")
    sub.add_parser("timers", help="temporizadores en marcha y en pausa")
    tp = sub.add_parser("timer-pause")
    tp.add_argument("id", type=int)
    tr = sub.add_parser("timer-resume")
    tr.add_argument("pid", type=int, help="el pid del temporizador en pausa (p7 = 7)")
    sw = sub.add_parser("stopwatch", help="cronómetro del reloj")
    sw.add_argument("action", choices=["start", "pause", "reset", "toggle", "status"], nargs="?", default="status")

    s = sub.add_parser("say", help="decir un texto por el altavoz")
    s.add_argument("text")

    d = sub.add_parser("show", help="mostrar en pantalla")
    d.add_argument("content", help="texto, HTML, URL de imagen o URL web")
    d.add_argument("--type", choices=["text", "markdown", "html", "image", "url"], default="text")
    d.add_argument("--title", default="")
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

    ra = sub.add_parser("reminder-add", help="recordatorio con aviso hablado en el reloj y/o Telegram")
    ra.add_argument("text", help="texto del aviso (máx. 300 caracteres)")
    rg = ra.add_mutually_exclusive_group(required=True)
    rg.add_argument("--at", help="AAAA-MM-DDTHH:MM en hora local")
    rg.add_argument("--in-minutes", type=int, help="dentro de N minutos")
    ra.add_argument("--repeat", choices=["none", "daily", "weekdays", "weekly"], default="none")
    ra.add_argument("--channel", choices=["clock", "telegram", "both"], default="clock")
    sub.add_parser("reminder-list", help="recordatorios pendientes")
    rd = sub.add_parser("reminder-delete", help="borrar un recordatorio por id")
    rd.add_argument("id")

    x = p.parse_args()
    if x.cmd == "reminder-add":
        body = {"text": x.text, "repeat": x.repeat, "channel": x.channel}
        if x.at:
            body["at"] = x.at
        else:
            body["in_minutes"] = x.in_minutes
        return bridge_call("POST", "/v1/reminders", body)
    if x.cmd == "reminder-list":
        return bridge_call("GET", "/v1/reminders")
    if x.cmd == "reminder-delete":
        return bridge_call("DELETE", "/v1/reminders/" + urllib.parse.quote(x.id, safe=""))
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
        body = {"action": "cancel"}
        if x.id is not None:
            body["id"] = x.id
        if x.pid is not None:
            body["pid"] = x.pid
        return call("POST", "/timer", body)
    if x.cmd == "timers":
        return call("GET", "/timers")
    if x.cmd == "timer-pause":
        return call("POST", "/timer", {"action": "pause", "id": x.id})
    if x.cmd == "timer-resume":
        return call("POST", "/timer", {"action": "resume", "pid": x.pid})
    if x.cmd == "stopwatch":
        return call("POST", "/stopwatch", {"action": x.action})
    if x.cmd == "say":
        return call("POST", "/speak", {"text": x.text})
    if x.cmd == "show":
        body = {"type": x.type, "content": x.content, "duration_ms": x.seconds * 1000}
        if x.title:
            body["title"] = x.title
        return call("POST", "/display", body)
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
