import mock_clock_server as mock
from app import FACE_FILES, face_version


def test_get_design(client):
    r = client.get("/api/design")
    assert r.status_code == 200
    assert r.json()["design"]["version"] == 1 and r.json()["saved"] is False


def test_put_design_ok_then_saved(client):
    d = client.get("/api/design").json()["design"]
    d["themes"]["clasico"]["name"] = "Mío"
    assert client.put("/api/design", json=d).status_code == 200
    g = client.get("/api/design").json()
    assert g["saved"] is True and g["design"]["themes"]["clasico"]["name"] == "Mío"


def test_put_design_invalid_is_forwarded_and_changes_nothing(client):
    d = client.get("/api/design").json()["design"]
    d["screens"]["principal"]["elements"][1].update(x=0, y=0, w=8, h=1)
    r = client.put("/api/design", json=d)
    assert r.status_code == 400
    assert r.json()["errors"]["screens.principal.elements.fecha"] == "se solapa con hora"
    assert client.get("/api/design").json()["saved"] is False


def test_preview_and_cancel(client):
    d = client.get("/api/design").json()["design"]
    r = client.post("/api/design/preview", json={"design": d, "seconds": 30})
    assert r.status_code == 200 and mock.PREVIEW[0]["seconds"] == 30
    assert client.delete("/api/design/preview").status_code == 200
    assert mock.PREVIEW == []


def test_reset_design(client):
    d = client.get("/api/design").json()["design"]
    d["themes"]["clasico"]["name"] = "Otro"
    client.put("/api/design", json=d)
    r = client.post("/api/design/reset")
    assert r.status_code == 200 and r.json()["design"]["themes"]["clasico"]["name"] == "Clásico"


def test_face_data_is_proxied(client):
    r = client.get("/api/face-data")
    assert r.status_code == 200 and r.json()["next_alarm"]["time"] == "07:30"


def test_background_upload_list_serve_delete(client):
    assert client.post("/api/backgrounds", params={"name": "virus.exe"}, content=b"x").status_code == 400
    assert client.post("/api/backgrounds", params={"name": "playa.jpg"}, content=b"").status_code == 400
    r = client.post("/api/backgrounds", params={"name": "grande.jpg"}, content=b"x" * (8 * 1024 * 1024 + 1))
    assert r.status_code == 413 and "8 MB" in r.text
    r = client.post("/api/backgrounds", params={"name": "mediana.jpg"}, content=b"\xff\xd8" + b"x" * (3 * 1024 * 1024))
    assert r.status_code == 200
    assert client.delete("/api/backgrounds/mediana.jpg").status_code == 200
    r = client.post("/api/backgrounds", params={"name": "playa.jpg"}, content=b"\xff\xd8fake")
    assert r.status_code == 200 and r.json()["name"] == "playa.jpg"
    assert client.get("/api/backgrounds").json()["backgrounds"][0]["name"] == "playa.jpg"
    img = client.get("/api/bg/playa.jpg")
    assert img.status_code == 200 and img.content == b"\xff\xd8fake" and img.headers["content-type"] == "image/jpeg"
    assert client.get("/api/bg/nada.jpg").status_code == 404
    assert client.delete("/api/backgrounds/playa.jpg").status_code == 200
    assert client.delete("/api/backgrounds/playa.jpg").status_code == 404


def test_background_serve_rejects_bad_names_and_non_images(client):
    assert client.get("/api/bg/%2e%2e").status_code == 400
    assert client.get("/api/bg/sinextension").status_code == 400
    assert client.get("/api/bg/virus.exe").status_code == 400
    mock.BG_DATA["raro.jpg"] = b"<html><script>alert(1)</script>"
    r = client.get("/api/bg/raro.jpg")
    assert r.status_code == 200
    assert r.headers["content-type"] == "application/octet-stream"
    assert r.headers["x-content-type-options"] == "nosniff"


def test_background_serve_clock_down_is_502(make_client):
    c = make_client(clock_url="http://127.0.0.1:9")
    r = c.get("/api/bg/playa.jpg")
    assert r.status_code == 502 and "no responde" in r.json()["error"]


def test_background_serve_bad_token_is_translated(make_client):
    r = make_client(clock_token="otro").get("/api/bg/playa.jpg")
    assert r.status_code == 502 and "CLOCK_TOKEN" in r.json()["error"]


def test_face_version_endpoint_matches_static_copy(client):
    from pathlib import Path
    import app as panel
    expected = face_version(Path(panel.__file__).parent / "static" / "face")
    r = client.get("/api/face-version")
    assert r.status_code == 200 and r.json() == {"ok": True, "face_version": expected}


def test_face_version_is_sha256_of_the_files_in_order(tmp_path):
    # "abc" repartido en los seis archivos: debe dar el SHA-256 de la concatenación.
    parts = {"index.html": b"a", "tokens.css": b"b", "face.css": b"c", "icons.js": b"", "face-core.js": b"", "face.js": b""}
    for name in FACE_FILES:
        (tmp_path / name).write_bytes(parts[name])
    assert face_version(tmp_path) == "ba7816bf8f01"
