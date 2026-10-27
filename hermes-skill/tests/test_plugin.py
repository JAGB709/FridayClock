"""Tests del plugin tipado de Hermes contra el reloj de mentira."""

import importlib.util
import json
import pathlib
import threading

import pytest

import mock_clock_server as mock

PLUGIN = pathlib.Path(__file__).resolve().parent.parent / "hermes-plugin" / "mi_smart_clock" / "__init__.py"


class Ctx:
    def __init__(self):
        self.tools = {}

    def register_tool(self, name, toolset, schema, handler, **kw):
        assert toolset == "mi-smart-clock"
        self.tools[name] = handler


def _load():
    spec = importlib.util.spec_from_file_location("mi_smart_clock_plugin", PLUGIN)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    ctx = Ctx()
    mod.register(ctx)
    return ctx


@pytest.fixture()
def tools(monkeypatch):
    mock.reset()
    server = mock.make_server(port=0, token="secreto")
    host, port = server.server_address
    threading.Thread(target=server.serve_forever, daemon=True).start()
    monkeypatch.setenv("CLOCK_URL", f"http://{host}:{port}")
    monkeypatch.setenv("CLOCK_TOKEN", "secreto")
    ctx = _load()
    try:
        yield lambda n, **a: json.loads(ctx.tools[n](a))
    finally:
        server.shutdown()


def test_registra_solo_tools_del_reloj():
    ctx = _load()
    assert ctx.tools and all(n.startswith("clock_") for n in ctx.tools)


def test_alarma_crear_y_listar(tools):
    assert tools("clock_alarm_set", time="07:30", label="Trabajo")["ok"]
    assert len(tools("clock_alarms")["alarms"]) == 1


def test_hora_invalida(tools):
    assert tools("clock_alarm_set", time="7:3")["ok"] is False


def test_cancelar_exige_confirmacion(tools):
    tools("clock_alarm_set", time="07:30")
    assert tools("clock_alarm_cancel", all=True)["needs_confirmation"] is True
    assert len(tools("clock_alarms")["alarms"]) == 1
    assert tools("clock_alarm_cancel", all=True, confirm=True)["ok"]
    assert tools("clock_alarms")["alarms"] == []


def test_ajustes_exigen_confirmacion(tools):
    assert tools("clock_settings_set", settings={"snooze_min": 10})["needs_confirmation"]
    assert tools("clock_settings_set", settings={"snooze_min": 10}, confirm=True)["ok"]


def test_display_rechaza_html_y_http(tools):
    assert tools("clock_display", type="html", content="<b>x</b>")["ok"] is False
    assert tools("clock_display", type="image", content="http://x/y.png")["ok"] is False
    assert tools("clock_display", type="text", content="Hola")["ok"]


def test_sin_configuracion(monkeypatch, tools):
    monkeypatch.delenv("CLOCK_URL")
    monkeypatch.setenv("HERMES_HOME", "/nonexistent")
    monkeypatch.setenv("HOME", "/nonexistent")
    assert tools("clock_status")["ok"] is False
