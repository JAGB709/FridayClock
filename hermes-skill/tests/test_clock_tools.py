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


def test_bluetooth_speaker(clock):
    r = clock.clock_bluetooth("speaker")
    assert r["ok"] is True
    assert r["bt"]["enabled"] is True and r["bt"]["discoverable"] is True


def test_bluetooth_off(clock):
    r = clock.clock_bluetooth("off")
    assert r["ok"] is True and r["bt"]["enabled"] is False


def test_get_and_set_settings(clock):
    r = clock.clock_get_settings()
    assert r["ok"] and r["settings"]["snooze_min"] == 5
    assert any(f["key"] == "snooze_min" and f["max"] == 60 for f in r["schema"])
    r = clock.clock_set_settings({"snooze_min": 9, "wake_word": False})
    assert r["ok"] and r["settings"]["snooze_min"] == 9 and r["settings"]["wake_word"] is False


def test_set_settings_invalid_changes_nothing(clock):
    r = clock.clock_set_settings({"snooze_min": 9, "night_brightness": 0, "auth_token": "x"})
    assert r["ok"] is False
    assert set(r["errors"]) == {"night_brightness", "auth_token"}
    assert clock.clock_get_settings()["settings"]["snooze_min"] == 5


def test_update_alarm(clock):
    a = clock.clock_set_alarm("07:30", label="Trabajo")["alarm"]
    r = clock.clock_update_alarm(a["id"], {"time": "06:45", "max_volume": 60})
    assert r["ok"] and r["alarm"]["time"] == "06:45" and r["alarm"]["max_volume"] == 60
    assert r["alarm"]["label"] == "Trabajo"


def test_update_missing_alarm_is_error(clock):
    r = clock.clock_update_alarm(999, {"label": "x"})
    assert r["ok"] is False and "no existe" in r["error"]


def test_list_sounds(clock):
    r = clock.clock_list_sounds()
    assert r["ok"] and any(s["kind"] == "system" for s in r["sounds"])


def test_update_alarm_invalid_sound(clock):
    a = clock.clock_set_alarm("07:30", label="Test")["alarm"]
    r = clock.clock_update_alarm(a["id"], {"sound": "system:http://x/y.mp3"})
    assert r["ok"] is False
