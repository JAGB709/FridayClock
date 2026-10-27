"""Recordatorios en el panel: proxy hacia el puente, validación y token fuera del navegador."""
import pytest

BRIDGE_TOKEN = "token-del-puente-1234"


@pytest.fixture()
def rc(make_client, clock_url):
    return make_client(bridge_url=clock_url, bridge_token=BRIDGE_TOKEN)  # el mock hace también de puente


def test_sin_configurar_explica_que_falta(client):
    r = client.get("/api/reminders")
    assert r.status_code == 503 and r.json()["configured"] is False
    assert "BRIDGE_URL" in r.json()["error"]


def test_crear_listar_borrar(rc):
    r = rc.post("/api/reminders", json={"text": "Pastillas", "at": "2099-01-01T09:00", "repeat": "daily", "channel": "both"})
    assert r.status_code == 200 and r.json()["reminder"]["repeat"] == "daily"
    rid = r.json()["reminder"]["id"]
    assert [x["id"] for x in rc.get("/api/reminders").json()["reminders"]] == [rid]
    assert rc.delete(f"/api/reminders/{rid}").json() == {"ok": True}
    assert rc.delete(f"/api/reminders/{rid}").status_code == 404


def test_validacion_local(rc):
    assert rc.post("/api/reminders", json={"text": "  ", "at": "2099-01-01T09:00"}).status_code == 400
    assert rc.post("/api/reminders", json={"text": "x" * 301, "at": "2099-01-01T09:00"}).status_code == 400
    assert rc.post("/api/reminders", json={"text": "x"}).status_code == 400
    assert rc.post("/api/reminders", json={"text": 5, "at": "2099-01-01T09:00"}).status_code == 400


def test_error_del_puente_llega_como_error_legible(rc):
    r = rc.post("/api/reminders", json={"text": "x", "at": "2099-01-01T09:00", "channel": "sms"})
    assert r.status_code == 400 and r.json()["ok"] is False and r.json()["error"]


def test_id_no_valido_no_llega_al_puente(rc):
    assert rc.delete("/api/reminders/..%2Falarms").status_code in (404, 405)
    assert rc.delete("/api/reminders/xyz").status_code == 404


def test_token_del_puente_incorrecto_se_explica(make_client, clock_url):
    c = make_client(bridge_url=clock_url, bridge_token="otro")
    r = c.get("/api/reminders")
    assert r.status_code == 502 and "BRIDGE_TOKEN" in r.json()["error"]


def test_puente_apagado_es_502(make_client):
    c = make_client(bridge_url="http://127.0.0.1:9", bridge_token=BRIDGE_TOKEN)
    assert c.get("/api/reminders").status_code == 502


def test_el_token_del_puente_nunca_llega_al_navegador(rc):
    for r in (rc.get("/api/reminders"), rc.get("/api/status")):
        assert BRIDGE_TOKEN not in r.text and BRIDGE_TOKEN not in str(r.headers)


def test_exige_cabecera_anti_csrf(rc):
    from fastapi.testclient import TestClient

    bare = TestClient(rc.app)
    assert bare.post("/api/reminders", json={"text": "x", "at": "2099-01-01T09:00"}).status_code == 403
