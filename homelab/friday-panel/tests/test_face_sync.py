from pathlib import Path

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[2] / "app-android" / "src" / "main" / "assets" / "face"
STATIC = HERE.parent / "static" / "face"


def test_la_copia_del_motor_es_identica_a_la_del_apk():
    names = {p.relative_to(ASSETS).as_posix() for p in ASSETS.rglob("*") if p.is_file()}
    assert names == {p.relative_to(STATIC).as_posix() for p in STATIC.rglob("*") if p.is_file()}, "faltan o sobran archivos: ejecuta bash tools/sync-face.sh"
    for name in sorted(names):
        assert (ASSETS / name).read_bytes() == (STATIC / name).read_bytes(), f"{name} difiere: ejecuta bash tools/sync-face.sh"
