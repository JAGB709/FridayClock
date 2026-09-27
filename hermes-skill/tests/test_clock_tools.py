"""Tests de las tools de Hermes contra el reloj de mentira (sin dispositivo)."""

import importlib
import threading

import pytest

import mock_clock_server as mock

TOKEN = "secreto"


@pytest.fixture()
def clock(monkeypatch):
    mock.reset()
    server = mock.make_server(port=0, token=TOKEN)
    host, port = server.server_address
    t = threading.Thread(target=server.serve_forever, daemon=True)
    t.start()
    monkeypatch.setenv("CLOCK_URL", f"http://{host}:{port}")
    monkeypatch.setenv("CLOCK_TOKEN", TOKEN)
    monkeypatch.setenv("CLOCK_TIMEOUT", "5")
    tools = importlib.import_module("clock_tools")
    importlib.reload(tools)
    try:
        yield tools
    finally:
        server.shutdown()


def test_set_and_list_alarm(clock):
    r = clock.clock_set_alarm("07:30", label="Trabajo", days="weekdays")
    assert r["ok"] is True
    assert r["alarm"]["time"] == "07:30"
    alarms = clock.clock_list_alarms()
    assert alarms["ok"] and len(alarms["alarms"]) == 1


def test_bad_time_is_error_not_exception(clock):
    r = clock.clock_set_alarm("nope")
    assert r["ok"] is False and "HH:MM" in r["error"]


def test_cancel_by_time(clock):
    clock.clock_set_alarm("07:30")
    clock.clock_set_alarm("08:00")
    r = clock.clock_cancel_alarm(time="07:30")
    assert r["ok"] and r["deleted"] == 1
    assert len(clock.clock_list_alarms()["alarms"]) == 1


def test_cancel_all(clock):
    clock.clock_set_alarm("07:30")
    clock.clock_set_alarm("08:00")
    assert clock.clock_cancel_alarm(all=True)["deleted"] == 2


def test_timer_and_say_and_display(clock):
    assert clock.clock_set_timer(minutes=10, label="Pasta")["ok"]
    assert clock.clock_say("Hola")["ok"]
    assert clock.clock_display("Reunión", type="text", seconds=30)["ok"]


def test_wrong_token(clock, monkeypatch):
    monkeypatch.setenv("CLOCK_TOKEN", "malo")
    r = clock.clock_status()
    assert r["ok"] is False  # el reloj responde 401 con "token inválido"


def test_clock_off_is_handled(clock, monkeypatch):
    # Puerto donde no escucha nadie -> error comprensible, no excepción.
    monkeypatch.setenv("CLOCK_URL", "http://127.0.0.1:9")
    r = clock.clock_status()
    assert r["ok"] is False and "inalcanzable" in r["error"]


def test_missing_config():
    import clock_tools as ct
    importlib.reload(ct)
    import os
    old = {k: os.environ.pop(k, None) for k in ("CLOCK_URL", "CLOCK_TOKEN")}
    try:
        r = ct.clock_status()
        assert r["ok"] is False
    finally:
        for k, v in old.items():
            if v is not None:
                os.environ[k] = v
