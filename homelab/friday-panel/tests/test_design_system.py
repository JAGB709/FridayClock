import json
import subprocess
import sys
from pathlib import Path

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
