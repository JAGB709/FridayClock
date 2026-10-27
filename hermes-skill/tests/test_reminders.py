"""Recordatorios de la skill (reminder-add/list/delete) contra el puente de mentira."""
import importlib
import json
import os
import subprocess
import sys
import threading
from pathlib import Path

import pytest

import mock_clock_server as mock

BRIDGE_TOKEN = "token-del-puente-1234"
CLOCK_PY = Path(__file__).resolve().parents[1] / "mi-smart-clock" / "scripts" / "clock.py"


@pytest.fixture()
def tools(monkeypatch):
    mock.reset()
    server = mock.make_server(port=0, token="secreto")  # el mock sirve también /v1/* como el puente
    host, port = server.server_address
    threading.Thread(target=server.serve_forever, daemon=True).start()
    monkeypatch.setenv("BRIDGE_URL", f"http://{host}:{port}")
    monkeypatch.setenv("BRIDGE_TOKEN", BRIDGE_TOKEN)
    monkeypatch.setenv("CLOCK_TIMEOUT", "5")
    mod = importlib.import_module("clock_tools")
    importlib.reload(mod)
    try:
        yield mod
    finally:
        server.shutdown()


def test_add_list_delete(tools):
    r = tools.reminder_add("Sacar la pasta", in_minutes=20, channel="both")
    assert r["ok"] and r["reminder"]["channel"] == "both"
    rid = r["reminder"]["id"]
    assert [x["id"] for x in tools.reminder_list()["reminders"]] == [rid]
    assert tools.reminder_delete(rid)["ok"] is True
    assert tools.reminder_list()["reminders"] == []
    assert tools.reminder_delete(rid)["ok"] is False


def test_errores_no_lanzan(tools, monkeypatch):
    assert tools.reminder_add("x")["ok"] is False
    assert tools.reminder_add("x", in_minutes=5, channel="sms")["ok"] is False
    monkeypatch.setenv("BRIDGE_TOKEN", "mal")
    r = tools.reminder_list()
    assert r["ok"] is False and "token" in r["error"]
    monkeypatch.delenv("BRIDGE_URL")
    assert "BRIDGE_URL" in tools.reminder_list()["error"]


def test_puente_apagado(tools, monkeypatch):
    monkeypatch.setenv("BRIDGE_URL", "http://127.0.0.1:9")
    r = tools.reminder_list()
    assert r["ok"] is False and "puente" in r["error"]


def test_id_raro_no_cambia_la_ruta(tools):
    assert tools.reminder_delete("../alarms")["ok"] is False


def test_tools_registradas(tools):
    assert {"reminder_add", "reminder_list", "reminder_delete"} <= {f.__name__ for f in tools.TOOLS}


def run_cli(*args, **env):
    full = {**os.environ, "HOME": "/nonexistent", **env}
    return subprocess.run([sys.executable, str(CLOCK_PY), *args], capture_output=True, text=True, env=full, timeout=20)


def test_cli_flujo(tools):
    env = {"BRIDGE_URL": os.environ["BRIDGE_URL"], "BRIDGE_TOKEN": BRIDGE_TOKEN}
    a = run_cli("reminder-add", "Pastillas", "--at", "2099-01-01T09:00", "--repeat", "daily", "--channel", "both", **env)
    assert a.returncode == 0, a.stdout + a.stderr
    rid = json.loads(a.stdout)["reminder"]["id"]
    assert rid in run_cli("reminder-list", **env).stdout
    assert run_cli("reminder-delete", rid, **env).returncode == 0
    assert run_cli("reminder-delete", rid, **env).returncode == 1


def test_cli_exige_fecha_y_token_valido(tools):
    env = {"BRIDGE_URL": os.environ["BRIDGE_URL"], "BRIDGE_TOKEN": BRIDGE_TOKEN}
    assert run_cli("reminder-add", "x", **env).returncode == 2
    bad = run_cli("reminder-list", **{**env, "BRIDGE_TOKEN": "mal"})
    assert bad.returncode == 1 and "token" in bad.stdout


def test_skill_documenta_los_comandos():
    text = (CLOCK_PY.parents[1] / "SKILL.md").read_text(encoding="utf-8")
    for cmd in ("reminder-add", "reminder-list", "reminder-delete", "BRIDGE_TOKEN"):
        assert cmd in text
