import mock_clock_server as mock

TOKEN = "secreto-del-reloj"  # el mismo que en conftest.py


def test_status_proxied_and_token_never_leaks(client):
    r = client.get("/api/status")
    assert r.status_code == 200 and r.json()["ok"] is True
    assert TOKEN not in r.text


def test_alarm_create_update_delete(client):
    r = client.post("/api/alarms", json={"time": "07:30", "label": "Trabajo", "days": ["mon"], "max_volume": 70})
    assert r.status_code == 200
    a = r.json()["alarm"]
    assert a["max_volume"] == 70
    r = client.put(f"/api/alarms/{a['id']}", json={"time": "06:45", "max_volume": None})
    assert r.status_code == 200 and r.json()["alarm"]["time"] == "06:45" and r.json()["alarm"]["max_volume"] is None
    assert client.delete(f"/api/alarms/{a['id']}").status_code == 200
    assert client.get("/api/alarms").json()["alarms"] == []


def test_clock_4xx_is_forwarded(client):
    r = client.put("/api/alarms/999", json={"label": "x"})
    assert r.status_code == 404 and "no existe" in r.json()["error"]
    r = client.post("/api/alarms", json={"time": "7:3"})
    assert r.status_code == 400


def test_settings_get_put_and_all_or_nothing(client):
    s = client.get("/api/settings").json()
    assert s["settings"]["snooze_min"] == 5 and s["schema"]
    r = client.put("/api/settings", json={"snooze_min": 12})
    assert r.status_code == 200 and r.json()["settings"]["snooze_min"] == 12
    r = client.put("/api/settings", json={"snooze_min": 3, "day_brightness": "50"})
    assert r.status_code == 400 and "day_brightness" in r.json()["errors"]
    assert mock.SETTINGS["snooze_min"] == 12


def test_clock_down_is_502_quickly(make_client):
    import time
    c = make_client(clock_url="http://127.0.0.1:9", timeout_s=1.0)
    t0 = time.monotonic()
    r = c.get("/api/status")
    assert r.status_code == 502 and r.json() == {"ok": False, "error": "El reloj no responde"}
    assert time.monotonic() - t0 < 3


def test_wrong_clock_token_is_explained(make_client):
    r = make_client(clock_token="malo").get("/api/status")
    assert r.status_code == 502 and "CLOCK_TOKEN" in r.json()["error"]


def test_sound_upload_validation(client):
    r = client.post("/api/sounds", params={"name": "virus.exe"}, content=b"x")
    assert r.status_code == 400
    r = client.post("/api/sounds", params={"name": "gallo.mp3"}, content=b"")
    assert r.status_code == 400
    r = client.post("/api/sounds", params={"name": "grande.mp3"}, content=b"x" * (5 * 1024 * 1024 + 1))
    assert r.status_code == 413
    r = client.post("/api/sounds", params={"name": "gallo.mp3"}, content=b"ID3fake")
    assert r.status_code == 200 and r.json()["id"] == "file:gallo.mp3"
    assert any(s["id"] == "file:gallo.mp3" for s in client.get("/api/sounds").json()["sounds"])
    assert client.delete("/api/sounds/gallo.mp3").status_code == 200
    assert client.delete("/api/sounds/gallo.mp3").status_code == 404


def test_do_actions(client):
    assert client.post("/api/do/stop").status_code == 200
    assert client.post("/api/do/test").status_code == 200
    assert any(a.get("label") == "Prueba de alarma" for a in mock.ALARMS)
    assert client.post("/api/do/speak", json={"text": "  "}).status_code == 400
    assert client.post("/api/do/speak", json={"text": "Hola"}).status_code == 200
    assert client.post("/api/do/nada").status_code == 404


def test_password_login(make_client):
    c = make_client(password="abrete")
    r = c.get("/api/status")
    assert r.status_code == 401 and r.json()["login_required"] is True
    assert c.post("/login", json={"password": "mal"}).status_code == 401
    assert c.post("/login", json={"password": "abrete"}).status_code == 200
    assert c.get("/api/status").status_code == 200
    assert "abrete" not in c.cookies.get("friday_panel", "")


def test_health_and_static(client):
    h = client.get("/health").json()
    assert h == {"ok": True, "clock": True}
    r = client.get("/")
    assert r.status_code == 200 and "Friday" in r.text
