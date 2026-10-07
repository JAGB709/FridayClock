#!/usr/bin/env python3
"""Genera Tokens.kt y tokens.css desde design-system/tokens.json.

  python tools/gen-tokens.py           escribe los archivos
  python tools/gen-tokens.py --check   falla si lo escrito difiere (lo usa la CI)
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "design-system" / "tokens.json"
KT = ROOT / "app-android/src/main/java/com/jarvis/clock/ui/Tokens.kt"
CSS = ROOT / "app-android/src/main/assets/face/tokens.css"

HEADER_KT = "// Generado por tools/gen-tokens.py desde design-system/tokens.json. No editar a mano.\n"
HEADER_CSS = "/* Generado por tools/gen-tokens.py desde design-system/tokens.json. No editar a mano. */\n"


def argb(hexv):
    return "0xFF" + hexv.lstrip("#").upper() + ".toInt()"


def kotlin(t):
    c, n = t["color"], t["night"]
    lines = [HEADER_KT, "package com.jarvis.clock.ui\n", "object Tokens {", "    object Color {"]
    for k, v in c.items():
        lines.append(f"        val {k.upper()}: Int = {argb(v)}")
    lines += ["    }", "    object Night {"]
    for k, v in n.items():
        lines.append(f"        val {k.upper()}: Int = {argb(v)}")
    lines += ["    }"]
    lines.append("    val SPACE: IntArray = intArrayOf(" + ", ".join(map(str, t["space"])) + ")")
    for k, v in t["radius"].items():
        lines.append(f"    const val RADIUS_{k.upper()}: Int = {v}")
    lines.append("    val TYPE: IntArray = intArrayOf(" + ", ".join(map(str, t["type"])) + ")")
    for k in ("fast", "base", "slow"):
        lines.append(f"    const val MOTION_{k.upper()}: Int = {t['motion'][k]}")
    lines.append("}")
    return "\n".join(lines) + "\n"


def css(t):
    out = [HEADER_CSS, ":root {"]
    for k, v in t["color"].items():
        out.append(f"  --{k.replace('_', '-')}: {v};")
    for k, v in t["night"].items():
        out.append(f"  --night-{k.replace('_', '-')}: {v};")
    for i, v in enumerate(t["space"], 1):
        out.append(f"  --space-{i}: {v}px;")
    for k, v in t["radius"].items():
        out.append(f"  --radius-{k}: {v}px;")
    for i, v in enumerate(t["type"], 1):
        out.append(f"  --fs-{i}: {v}px;")
    out.append(f"  --dur-fast: {t['motion']['fast']}ms;")
    out.append(f"  --dur-base: {t['motion']['base']}ms;")
    out.append(f"  --dur-slow: {t['motion']['slow']}ms;")
    out.append(f"  --ease: {t['motion']['ease']};")
    out.append("}")
    return "\n".join(out) + "\n"


def main():
    t = json.loads(SRC.read_text(encoding="utf-8"))
    want = {KT: kotlin(t), CSS: css(t)}
    if "--check" in sys.argv:
        bad = [str(p) for p, text in want.items() if not p.exists() or p.read_text(encoding="utf-8") != text]
        if bad:
            print("desactualizado: " + ", ".join(bad) + " (ejecuta python tools/gen-tokens.py)")
            return 1
        print("tokens al día")
        return 0
    for p, text in want.items():
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding="utf-8")
        print("escrito", p)
    return 0


if __name__ == "__main__":
    sys.exit(main())
