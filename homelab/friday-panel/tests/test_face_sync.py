from pathlib import Path

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[2] / "app-android" / "src" / "main" / "assets" / "face"
STATIC = HERE.parent / "static" / "face"


def test_la_copia_del_motor_es_identica_a_la_del_apk():
    names = {p.name for p in ASSETS.iterdir()}
    assert names == {p.name for p in STATIC.iterdir()}, "faltan o sobran archivos: ejecuta bash tools/sync-face.sh"
    for name in sorted(names):
        assert (ASSETS / name).read_bytes() == (STATIC / name).read_bytes(), f"{name} difiere: ejecuta bash tools/sync-face.sh"
