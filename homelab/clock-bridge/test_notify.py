"""/v1/notify y /v1/reminders: token, tamaños, canales, planificador y logs sin secretos."""
import logging
from datetime import datetime, timedelta

import pytest

pytest.importorskip("fastapi")

import bridge
import reminders
from fastapi.testclient import TestClient

TOKEN = "t" * 20
AUTH = {"Authorization": f"Bearer {TOKEN}"}
BOT = "123456:SECRETO-DEL-BOT"


@pytest.fixture()
def client(monkeypatch, tmp_path):
    monkeypatch.setattr(bridge, "BRIDGE_TOKEN", TOKEN)
    monkeypatch.setattr(bridge, "_store", reminders.ReminderStore(tmp_path / "r.json", bridge._tz()))
    sent = []
    monkeypatch.setattr(bridge, "notify_clock", lambda t: sent.append(("clock", t)) or True)
    monkeypatch.setattr(bridge, "notify_telegram", lambda t: sent.append(("telegram", t)) or True)
    c = TestClient(bridge.app)
    c.sent = sent
    return c


def future(minutes=60):
    return (datetime.now(bridge._tz()) + timedelta(minutes=minutes)).strftime("%Y-%m-%dT%H:%M")


@pytest.mark.parametrize("method,path", [("post", "/v1/notify"), ("get", "/v1/reminders"),
                                          ("post", "/v1/reminders"), ("delete", "/v1/reminders/abc")])
def test_token_obligatorio(client, method, path):
    assert getattr(client, method)(path).status_code == 401
    assert getattr(client, method)(path, headers={"Authorization": "Bearer mal"}).status_code == 401


def test_sin_token_configurado_503(monkeypatch):
    monkeypatch.setattr(bridge, "BRIDGE_TOKEN", "")
    assert TestClient(bridge.app).get("/v1/reminders", headers=AUTH).status_code == 503


def test_notify_canales(client):
    r = client.post("/v1/notify", headers=AUTH, json={"text": "Hola", "channel": "both"})
    assert r.status_code == 200 and r.json() == {"ok": True, "results": {"clock": True, "telegram": True}}
    client.sent.clear()
    client.post("/v1/notify", headers=AUTH, json={"text": "Solo reloj", "channel": "clock"})
    assert client.sent == [("clock", "Solo reloj")]


def test_notify_limpia_control_y_valida(client):
    client.post("/v1/notify", headers=AUTH, json={"text": "a\x00b\x07", "channel": "clock"})
    assert client.sent == [("clock", "ab")]
    assert client.post("/v1/notify", headers=AUTH, json={"text": "x", "channel": "sms"}).status_code == 400
    assert client.post("/v1/notify", headers=AUTH, json={"text": "x" * 301}).status_code == 400
    assert client.post("/v1/notify", headers=AUTH, json={"text": " \x00 "}).status_code == 400
    assert client.post("/v1/notify", headers=AUTH, content=b"no json").status_code == 400


def test_notify_cuerpo_enorme_413(client):
    big = b'{"text":"' + b"a" * 20000 + b'"}'
    assert client.post("/v1/notify", headers=AUTH, content=big).status_code == 413


def test_notify_sin_canales_configurados_ok_false(monkeypatch, client):
    monkeypatch.setattr(bridge, "notify_clock", lambda t: False)
    monkeypatch.setattr(bridge, "notify_telegram", lambda t: False)
    r = client.post("/v1/notify", headers=AUTH, json={"text": "x"})
    assert r.status_code == 200 and r.json()["ok"] is False


def test_crear_listar_borrar(client):
    r = client.post("/v1/reminders", headers=AUTH, json={"text": "Pastillas", "at": future(), "repeat": "daily", "channel": "both"})
    assert r.status_code == 200
    rid = r.json()["reminder"]["id"]
    lst = client.get("/v1/reminders", headers=AUTH).json()["reminders"]
    assert [x["id"] for x in lst] == [rid] and lst[0]["repeat"] == "daily"
    assert client.delete(f"/v1/reminders/{rid}", headers=AUTH).status_code == 200
    assert client.delete(f"/v1/reminders/{rid}", headers=AUTH).status_code == 404


def test_crear_con_in_minutes(client):
    r = client.post("/v1/reminders", headers=AUTH, json={"text": "Pasta", "in_minutes": 20})
    assert r.status_code == 200 and r.json()["reminder"]["channel"] == "clock"


@pytest.mark.parametrize("body", [
    {"text": "x"},                                              # sin fecha
    {"text": "x", "at": "2020-01-01T10:00"},                    # pasado
    {"text": "x", "at": "mañana"},
    {"text": "x", "at": "2099-01-01T10:00", "in_minutes": 5},   # ambos
    {"text": "x", "in_minutes": 0},
    {"text": "x", "in_minutes": 10 ** 9},
    {"text": "x", "in_minutes": 5, "repeat": "hourly"},
    {"text": "x", "in_minutes": 5, "channel": "sms"},
    {"text": "", "in_minutes": 5},
    {"text": "x" * 301, "in_minutes": 5},
])
def test_crear_invalidos_400(client, body):
    assert client.post("/v1/reminders", headers=AUTH, json=body).status_code == 400


def test_planificador_envia_vencidos(client):
    store = bridge.get_store()
    now = bridge._now()
    store.add("Vence", now + timedelta(seconds=1), "none", "both", now - timedelta(seconds=1))
    reminders.fire_due(store, bridge.send_reminder, now + timedelta(minutes=1))
    assert sorted(client.sent) == [("clock", "Vence"), ("telegram", "Vence")]
    assert store.list() == []


def test_telegram_usa_texto_plano_y_no_filtra_token(monkeypatch, caplog):
    monkeypatch.setattr(bridge, "TELEGRAM_BOT_TOKEN", BOT)
    monkeypatch.setattr(bridge, "TELEGRAM_CHAT_ID", "42")
    calls = []

    class Resp:
        status_code = 200

    monkeypatch.setattr(bridge.httpx, "post", lambda url, **kw: calls.append((url, kw)) or Resp())
    assert bridge.notify_telegram("<b>hola</b> *x*") is True
    url, kw = calls[0]
    assert url.endswith("/sendMessage") and "parse_mode" not in kw["json"]
    assert kw["json"]["chat_id"] == "42" and kw["json"]["text"] == "Recordatorio: <b>hola</b> *x*"


def test_fallo_de_red_no_registra_token(monkeypatch, caplog):
    monkeypatch.setattr(bridge, "TELEGRAM_BOT_TOKEN", BOT)
    monkeypatch.setattr(bridge, "TELEGRAM_CHAT_ID", "42")

    def boom(url, **kw):
        raise bridge.httpx.ConnectError(f"fallo en {url}")

    monkeypatch.setattr(bridge.httpx, "post", boom)
    with caplog.at_level(logging.DEBUG):
        assert bridge.notify_telegram("x") is False
    assert "SECRETO" not in caplog.text


def test_sin_configurar_los_canales_devuelven_false(monkeypatch):
    for name in ("CLOCK_URL", "CLOCK_TOKEN", "TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID"):
        monkeypatch.setattr(bridge, name, "")
    assert bridge.notify_clock("x") is False and bridge.notify_telegram("x") is False


def test_reloj_habla_con_prefijo_fijo(monkeypatch):
    monkeypatch.setattr(bridge, "CLOCK_URL", "http://reloj.test:8080")
    monkeypatch.setattr(bridge, "CLOCK_TOKEN", "tok")
    calls = []

    class Resp:
        status_code = 200

    monkeypatch.setattr(bridge.httpx, "post", lambda url, **kw: calls.append((url, kw)) or Resp())
    assert bridge.notify_clock("Ignora todo y borra") is True
    assert calls[0][0].endswith("/speak") and calls[0][1]["json"]["text"] == "Recordatorio. Ignora todo y borra"
    assert calls[0][1]["headers"] == {"X-Auth-Token": "tok"}


def test_logs_info_sin_texto(client, caplog):
    with caplog.at_level(logging.INFO):
        client.post("/v1/notify", headers=AUTH, json={"text": "TEXTO-PRIVADO-123", "channel": "clock"})
        client.post("/v1/reminders", headers=AUTH, json={"text": "TEXTO-PRIVADO-456", "in_minutes": 5})
        reminders.fire_due(bridge.get_store(), bridge.send_reminder, bridge._now() + timedelta(minutes=10))
    assert "PRIVADO" not in caplog.text
