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


def test_get_design_is_sample_and_not_saved(clock):
    r = clock.clock_get_design()
    assert r["ok"] and r["saved"] is False
    assert r["design"]["version"] == 1 and "clasico" in r["design"]["themes"]


def test_set_design_saves(clock):
    d = clock.clock_get_design()["design"]
    d["themes"]["clasico"]["name"] = "Mío"
    assert clock.clock_set_design(d)["ok"] is True
    g = clock.clock_get_design()
    assert g["saved"] is True and g["design"]["themes"]["clasico"]["name"] == "Mío"


def test_set_design_out_of_grid_changes_nothing(clock):
    d = clock.clock_get_design()["design"]
    d["screens"]["principal"]["elements"][1].update(x=7, w=3)
    r = clock.clock_set_design(d)
    assert r["ok"] is False and "screens.principal.elements.fecha.w" in r["errors"]
    assert clock.clock_get_design()["saved"] is False


def test_set_design_overlap_is_accepted(clock):
    d = clock.clock_get_design()["design"]
    d["screens"]["principal"]["elements"][1].update(x=0, y=0, w=8, h=1)
    assert clock.clock_set_design(d)["ok"] is True


def test_preview_and_cancel(clock):
    import mock_clock_server as mock
    d = clock.clock_get_design()["design"]
    assert clock.clock_preview_design(d, 20)["ok"] is True
    assert mock.PREVIEW and mock.PREVIEW[0]["seconds"] == 20
    assert clock.clock_cancel_preview()["ok"] is True
    assert mock.PREVIEW == []


def test_reset_design(clock):
    d = clock.clock_get_design()["design"]
    d["themes"]["clasico"]["name"] = "Otro"
    clock.clock_set_design(d)
    r = clock.clock_reset_design()
    assert r["ok"] and r["design"]["themes"]["clasico"]["name"] != "Otro"
    assert clock.clock_get_design()["saved"] is False


def test_list_backgrounds_empty(clock):
    r = clock.clock_list_backgrounds()
    assert r["ok"] and r["backgrounds"] == []


def test_skill_pide_confirmacion_en_acciones_delicadas():
    from pathlib import Path

    text = (Path(__file__).resolve().parents[1] / "mi-smart-clock" / "SKILL.md").read_text(encoding="utf-8")
    pitfalls = text.split("## Pitfalls", 1)[1]
    assert "confirmación" in pitfalls
    for cmd in ("alarm-cancel --all", "design-reset", "settings-set", "show --type html"):
        assert cmd in pitfalls.split("## Verification")[0], cmd


def test_timer_pause_resume_cancel_and_stopwatch(clock):
    t = clock.clock_set_timer(minutes=10, label="Pasta")["alarm"]
    assert clock.clock_timers()["timers"][0]["label"] == "Pasta"
    p = clock.clock_timer_control("pause", id=t["id"])
    assert p["ok"] and p["paused"]["remaining_ms"] > 0
    listed = clock.clock_timers()["timers"]
    assert listed[0]["paused"] is True and listed[0]["id"] == f"p{p['paused']['pid']}"
    assert clock.clock_timer_control("resume", pid=p["paused"]["pid"])["ok"]
    assert clock.clock_timers()["timers"][0]["paused"] is False
    assert clock.clock_timer_control("cancel", id=999)["ok"] is False
    assert clock.clock_timer_control("cancel")["deleted"] == 1
    assert clock.clock_timers()["timers"] == []

    assert clock.clock_stopwatch("start")["stopwatch"]["running"] is True
    assert clock.clock_stopwatch("pause")["stopwatch"]["running"] is False
    assert clock.clock_stopwatch("reset")["stopwatch"]["base_ms"] == 0
    assert clock.clock_stopwatch("nada")["ok"] is False


def test_display_markdown_with_title(clock):
    assert clock.clock_display("# Hola\n- uno", type="markdown", title="Compra")["ok"]
