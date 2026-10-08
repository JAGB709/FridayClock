import json
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[3]
TOKENS = ROOT / "design-system" / "tokens.json"


def _lin(c):
    c = c / 255
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4


def luminance(hex_color):
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * _lin(r) + 0.7152 * _lin(g) + 0.0722 * _lin(b)


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def _resolve(tokens, path):
    node = tokens
    for part in path.split("."):
        node = node[part]
    return node


def test_contrast_known_values():
    assert round(contrast("#000000", "#FFFFFF"), 1) == 21.0
    assert contrast("#777777", "#777777") == 1.0


def test_all_declared_pairs_meet_their_minimum_contrast():
    tokens = json.loads(TOKENS.read_text(encoding="utf-8"))
    for p in tokens["pairs"]:
        ratio = contrast(_resolve(tokens, p["fg"]), _resolve(tokens, p["bg"]))
        assert ratio >= p["min"], f"{p['fg']} sobre {p['bg']}: {ratio:.2f} < {p['min']}"


def test_generated_tokens_are_up_to_date():
    r = subprocess.run([sys.executable, str(ROOT / "tools" / "gen-tokens.py"), "--check"], capture_output=True, text=True)
    assert r.returncode == 0, r.stdout + r.stderr


def test_inter_fonts_are_bundled_with_license():
    fonts = ROOT / "app-android/src/main/assets/face/fonts"
    for name in ("Inter-Light.ttf", "Inter-Regular.ttf", "Inter-SemiBold.ttf", "OFL.txt"):
        f = fonts / name
        assert f.is_file() and f.stat().st_size > 1000, name
    for name in ("Inter-Light.ttf", "Inter-Regular.ttf", "Inter-SemiBold.ttf"):
        assert (fonts / name).read_bytes()[:4] in (b"\x00\x01\x00\x00", b"true", b"OTTO")


ICONS = ("bell mic settings sun cloud cloud-sun cloud-rain cloud-snow cloud-lightning cloud-fog moon clock "
         "alert-triangle check x trash-2 plus play pause volume-2 image upload chevron-right "
         "house layout-template chevron-left chevron-down chevrons-up chevrons-down arrow-up arrow-down layers wifi "
         "wifi-off pencil copy info music refresh-cw save rotate-ccw").split()


def test_icon_sources_and_generated_files_exist():
    for n in ICONS:
        assert (ROOT / "design-system/icons" / f"{n}.svg").is_file(), n
        assert (ROOT / "app-android/src/main/res/drawable" / f"ic_{n.replace('-', '_')}.xml").is_file(), n


def test_generated_icons_are_up_to_date():
    r = subprocess.run([sys.executable, str(ROOT / "tools" / "gen-icons.py"), "--check"], capture_output=True, text=True)
    assert r.returncode == 0, r.stdout + r.stderr


def test_no_emojis_in_clock_engine_and_design_system():
    import re
    rx = re.compile("[⌀-⏿■-➿⤀-⥿⬀-⯿️\U0001F000-\U0001FFFF]")
    roots = [ROOT / "app-android/src/main", ROOT / "design-system"]
    hits = []
    for base in roots:
        for f in base.rglob("*"):
            if not f.is_file() or f.suffix in (".ttf", ".png", ".webp", ".jpg", ".apk"):
                continue
            try:
                text = f.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            for i, line in enumerate(text.split("\n"), 1):
                if rx.search(line):
                    hits.append(f"{f.relative_to(ROOT)}:{i}")
    assert not hits, "emojis encontrados: " + ", ".join(hits)


PANEL = ROOT / "homelab/friday-panel/static"


def _panel_files():
    for f in sorted(PANEL.iterdir()):
        if f.is_file() and f.suffix in (".html", ".js", ".css", ".json", ".svg") and f.name != "icon.svg":
            yield f


def test_no_emojis_or_pictographs_in_panel():
    import re
    rx = re.compile("[⌀-⏿■-➿⤀-⥿⬀-⯿←-⇿＋‹›️\U0001F000-\U0001FFFF]")
    allowed = set("↑↓")  # flechas arriba y abajo permitidas como texto
    hits = []
    for f in _panel_files():
        for i, line in enumerate(f.read_text(encoding="utf-8").split("\n"), 1):
            if any(ch not in allowed for ch in rx.findall(line)):
                hits.append(f"{f.name}:{i}")
    assert not hits, "símbolos pictográficos en el panel: " + ", ".join(hits)


def test_panel_css_uses_only_tokens_for_colors():
    import re
    css = (PANEL / "styles.css").read_text(encoding="utf-8")
    bad = []
    for i, line in enumerate(css.split("\n"), 1):
        if "@font-face" in line:
            continue
        decl = line.split("{", 1)[-1]  # lo anterior a "{" es el selector (p. ej. "#fab" es un id)
        if re.search(r"#[0-9a-fA-F]{3,8}\b", decl) and "#000" not in decl:
            bad.append(i)
        if re.search(r"rgba?\((?!\s*0\s*,\s*0\s*,\s*0)", decl):
            bad.append(i)
    assert not bad, f"colores literales en styles.css, líneas: {bad}"


def test_every_icon_used_by_the_panel_exists_in_the_sprite():
    import re
    sprite = (ROOT / "app-android/src/main/assets/face/icons.js").read_text(encoding="utf-8")
    known = set(re.findall(r'^  "([a-z0-9-]+)": "', sprite, re.M))
    used = set()
    for f in _panel_files():
        if f.suffix != ".js":
            continue
        # sin comentarios de línea completa, que citan ejemplos
        text = "\n".join(l for l in f.read_text(encoding="utf-8").split("\n") if not l.lstrip().startswith(("/*", "//", "*")))
        used |= set(re.findall(r'\bicon(?:Button)?\(\s*"([a-z0-9-]+)"', text))
        # btn(etiqueta, accion, "clases", "icono"[, opciones]): clases e icono seguidos
        used |= set(re.findall(r',\s*"(?:primary|ghost|danger|small|back|fab|\s)*",\s*"([a-z0-9-]+)"\s*[,)]', text))
    used |= set(re.findall(r'data-icon="([a-z0-9-]+)"', (PANEL / "index.html").read_text(encoding="utf-8")))
    assert known, "no se pudo leer el sprite"
    assert used - known == set(), f"iconos inexistentes: {sorted(used - known)}"
