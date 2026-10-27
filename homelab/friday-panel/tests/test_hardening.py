"""Endurecimiento del panel: login, sesiones, Host/Origin y cabeceras."""
import app as panel


def test_login_limita_intentos(make_client):
    c = make_client(password="abrete")
    codes = [c.post("/login", json={"password": "mal"}).status_code for _ in range(6)]
    assert codes[:5] == [401] * 5 and codes[5] == 429
    # bloqueado también con la contraseña buena mientras dure la ventana
    assert c.post("/login", json={"password": "abrete"}).status_code == 429


def test_login_ventana_expira(make_client, monkeypatch):
    t = [1000.0]
    monkeypatch.setattr(panel.time, "time", lambda: t[0])
    c = make_client(password="abrete")
    for _ in range(5):
        c.post("/login", json={"password": "mal"})
    assert c.post("/login", json={"password": "abrete"}).status_code == 429
    t[0] += 61
    assert c.post("/login", json={"password": "abrete"}).status_code == 200


def test_sesion_aleatoria_en_servidor(make_client):
    a, b = make_client(password="abrete"), make_client(password="abrete")
    a.post("/login", json={"password": "abrete"})
    b.post("/login", json={"password": "abrete"})
    ca, cb = a.cookies.get("friday_panel"), b.cookies.get("friday_panel")
    assert ca and cb and ca != cb and len(ca) >= 32
    assert a.get("/api/status").status_code == 200
    # una cookie inventada o derivada de la contraseña no vale
    a.cookies.set("friday_panel", panel._session_value("abrete") if hasattr(panel, "_session_value") else "x" * 40)
    assert a.get("/api/status").status_code == 401


def test_sesion_caduca_en_servidor(make_client, monkeypatch):
    t = [1000.0]
    monkeypatch.setattr(panel.time, "time", lambda: t[0])
    c = make_client(password="abrete")
    c.post("/login", json={"password": "abrete"})
    assert c.get("/api/status").status_code == 200
    t[0] += panel.COOKIE_MAX_AGE + 1
    assert c.get("/api/status").status_code == 401


def test_host_no_permitido(make_client):
    c = make_client(allowed_hosts="panel.local,100.1.2.3")
    assert c.get("/health", headers={"Host": "evil.attacker.com"}).status_code == 400
    assert c.get("/health", headers={"Host": "panel.local:8770"}).status_code == 200


def test_host_libre_por_defecto(make_client):
    assert make_client().get("/health", headers={"Host": "cualquiera.net"}).status_code == 200


def test_origin_ajeno_en_post(make_client):
    c = make_client()
    r = c.post("/api/do/stop", headers={"Origin": "http://evil.com"})
    assert r.status_code == 403
    ok = c.post("/api/do/stop", headers={"Origin": "http://testserver"})
    assert ok.status_code == 200
    # GET no comprueba Origin
    assert c.get("/health", headers={"Origin": "http://evil.com"}).status_code == 200


def test_cabeceras_de_seguridad(client):
    h = client.get("/health").headers
    assert "frame-ancestors 'none'" in h["content-security-policy"]
    assert h["x-frame-options"] == "DENY"
    assert h["x-content-type-options"] == "nosniff"
    assert h["referrer-policy"] == "no-referrer"
    f = client.get("/face/index.html").headers
    assert "frame-ancestors 'self'" in f["content-security-policy"]
    assert f["x-frame-options"] == "SAMEORIGIN"
    # también en errores de otros middlewares
    assert client.post("/login", headers={"X-Friday": "0"}).headers["x-frame-options"] == "DENY"


def test_subida_sin_content_length_se_corta_al_pasar_el_tope(make_client):
    c = make_client()

    def trozos():
        for _ in range(7):  # 7 MB en trozos, sin Content-Length
            yield b"x" * (1024 * 1024)
    r = c.post("/api/sounds", params={"name": "grande.mp3"}, content=trozos())
    assert r.status_code == 413


def test_subida_valida_grande_pasa_por_disco(make_client):
    c = make_client()
    r = c.post("/api/backgrounds", params={"name": "ok.jpg"}, content=b"\xff\xd8" + b"x" * (3 * 1024 * 1024))
    assert r.status_code == 200
    assert c.delete("/api/backgrounds/ok.jpg").status_code == 200


def test_api_bg_limita_el_tamano_de_la_respuesta(make_client, monkeypatch):
    c = make_client()
    c.post("/api/backgrounds", params={"name": "a.jpg"}, content=b"\xff\xd8" + b"x" * 100)
    assert c.get("/api/bg/a.jpg").status_code == 200
    monkeypatch.setattr(panel, "MAX_BG_BYTES", 50)
    assert c.get("/api/bg/a.jpg").status_code == 502
