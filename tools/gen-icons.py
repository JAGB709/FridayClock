#!/usr/bin/env python3
"""Genera los VectorDrawable del reloj y el sprite icons.js del motor desde design-system/icons/*.svg.

  python tools/gen-icons.py           escribe los archivos
  python tools/gen-icons.py --check   falla si lo escrito difiere (lo usa la CI)
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "design-system" / "icons"
DRAWABLE = ROOT / "app-android/src/main/res/drawable"
JS = ROOT / "app-android/src/main/assets/face/icons.js"
NS = "{http://www.w3.org/2000/svg}"


def num(v):
    s = ("%.4f" % float(v)).rstrip("0").rstrip(".")
    return s if s else "0"


def to_path(el):
    tag = el.tag.replace(NS, "")
    a = el.attrib
    f = lambda k, d=0: float(a.get(k, d))
    if tag == "path":
        return a["d"]
    if tag in ("circle", "ellipse"):
        cx, cy = f("cx"), f("cy")
        rx = f("r") if tag == "circle" else f("rx")
        ry = f("r") if tag == "circle" else f("ry")
        return (f"M{num(cx - rx)},{num(cy)}a{num(rx)},{num(ry)} 0 1,0 {num(2 * rx)},0"
                f"a{num(rx)},{num(ry)} 0 1,0 {num(-2 * rx)},0")
    if tag == "line":
        return f"M{num(f('x1'))},{num(f('y1'))}L{num(f('x2'))},{num(f('y2'))}"
    if tag in ("polyline", "polygon"):
        pts = [float(x) for x in re.split(r"[\s,]+", a["points"].strip()) if x]
        pairs = list(zip(pts[0::2], pts[1::2]))
        d = "M" + "L".join(f"{num(x)},{num(y)}" for x, y in pairs)
        return d + ("Z" if tag == "polygon" else "")
    if tag == "rect":
        x, y, w, h = f("x"), f("y"), f("width"), f("height")
        rx = f("rx", a.get("ry", 0))
        ry = f("ry", rx)
        if rx <= 0 and ry <= 0:
            return f"M{num(x)},{num(y)}H{num(x + w)}V{num(y + h)}H{num(x)}Z"
        return (f"M{num(x + rx)},{num(y)}H{num(x + w - rx)}a{num(rx)},{num(ry)} 0 0 1 {num(rx)},{num(ry)}"
                f"V{num(y + h - ry)}a{num(rx)},{num(ry)} 0 0 1 {num(-rx)},{num(ry)}"
                f"H{num(x + rx)}a{num(rx)},{num(ry)} 0 0 1 {num(-rx)},{num(-ry)}"
                f"V{num(y + ry)}a{num(rx)},{num(ry)} 0 0 1 {num(rx)},{num(-ry)}Z")
    raise ValueError("elemento SVG no admitido: " + tag)


def load(name):
    root = ET.parse(SRC / f"{name}.svg").getroot()
    shapes = [c for c in root if c.tag.replace(NS, "") in ("path", "circle", "ellipse", "line", "polyline", "polygon", "rect")]
    return shapes


def vector_xml(shapes):
    paths = "\n".join(
        '    <path android:pathData="%s" android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" '
        'android:strokeWidth="2" android:strokeLineCap="round" android:strokeLineJoin="round"/>' % to_path(s)
        for s in shapes)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generado por tools/gen-icons.py desde design-system/icons. No editar a mano. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
            f"{paths}\n</vector>\n")


def inner_svg(shapes):
    out = []
    for s in shapes:
        tag = s.tag.replace(NS, "")
        attrs = " ".join(f'{k}="{v}"' for k, v in s.attrib.items())
        out.append(f"<{tag} {attrs}/>")
    return "".join(out)


def sprite_js(names, bodies):
    data = json.dumps({n: bodies[n] for n in names}, ensure_ascii=True, indent=2, sort_keys=True)
    return f"""/* Generado por tools/gen-icons.py desde design-system/icons. No editar a mano.
   Juego Lucide (ISC). JavaScript clásico (ES2017): el WebView del reloj es Chrome 74. */
(function (root, factory) {{
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.FridayIcons = factory();
}})(this, function () {{
  "use strict";
  var BODY = {data};
  var NAMES = Object.keys(BODY);

  function svg(name, size) {{
    if (!name || !BODY.hasOwnProperty(name)) return "";
    var s = size || 24;
    return '<svg xmlns="http://www.w3.org/2000/svg" width="' + s + '" height="' + s + '" viewBox="0 0 24 24" ' +
      'fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" ' +
      'aria-hidden="true" focusable="false">' + BODY[name] + "</svg>";
  }}

  function node(name, size) {{
    var html = svg(name, size);
    if (!html || typeof document === "undefined") return null;
    var holder = document.createElement("span");
    holder.innerHTML = html;
    return holder.firstChild;
  }}

  return {{ names: NAMES, svg: svg, node: node }};
}});
"""


def main():
    names = sorted(p.stem for p in SRC.glob("*.svg"))
    want = {}
    bodies = {}
    for n in names:
        shapes = load(n)
        want[DRAWABLE / f"ic_{n.replace('-', '_')}.xml"] = vector_xml(shapes)
        bodies[n] = inner_svg(shapes)
    want[JS] = sprite_js(names, bodies)
    if "--check" in sys.argv:
        bad = [str(p) for p, t in want.items() if not p.exists() or p.read_text(encoding="utf-8") != t]
        if bad:
            print("desactualizado: " + ", ".join(bad) + " (ejecuta python tools/gen-icons.py)")
            return 1
        print("iconos al día")
        return 0
    for p, t in want.items():
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(t, encoding="utf-8")
    print(f"{len(names)} iconos generados")
    return 0


if __name__ == "__main__":
    sys.exit(main())
