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
