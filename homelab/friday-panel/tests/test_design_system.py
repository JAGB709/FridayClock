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


# ---------------------------------------------------------------- símbolos y emojis

# Solo estos símbolos no alfabéticos se admiten como texto: flechas arriba/abajo, grados,
# multiplicación y la puntuación habitual (· – — … ¿ ¡ « »).
ALLOWED_SYMBOLS = set("↑↓°×·–—…¿¡«»")
EXTRA_FORBIDDEN = set("•‼⁉‹›\u200d\ufe0f\u20e3\uff0b\u2122\u00a9\u00ae")


def decode_escapes(text, suffix):
    """Resuelve \\uXXXX, \\u{...}, pares sustitutos, escapes CSS y entidades HTML antes de escanear."""
    import html
    import re

    text = re.sub(r"\\u\{([0-9a-fA-F]{1,6})\}", lambda m: chr(min(int(m.group(1), 16), 0x10FFFF)), text)
    text = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), text)
    if suffix == ".css":
        text = re.sub(r"\\([0-9a-fA-F]{1,6})[ ]?", lambda m: chr(min(int(m.group(1), 16), 0x10FFFF)), text)
    text = html.unescape(text)
    # Pares sustitutos escapados (\ud83d\udd14) quedan como dos unidades: se unen.
    return text.encode("utf-16", "surrogatepass").decode("utf-16", "replace")


def pictograph_hits(text, suffix=".js"):
    import unicodedata

    hits = []
    for i, line in enumerate(decode_escapes(text, suffix).split("\n"), 1):
        for ch in line:
            if ch in ALLOWED_SYMBOLS or ord(ch) < 0x80:
                continue
            if ch in EXTRA_FORBIDDEN or unicodedata.category(ch) in ("So", "Sk", "Sm", "Cf", "Cs", "Co") or 0x2100 <= ord(ch) <= 0x214F or ord(ch) >= 0x1F000:
                hits.append((i, ch))
    return hits


def test_no_emojis_or_pictographs_in_panel():
    hits = []
    for f in _panel_files():
        for i, ch in pictograph_hits(f.read_text(encoding="utf-8"), f.suffix):
            hits.append(f"{f.name}:{i} U+{ord(ch):04X}")
    assert not hits, "símbolos pictográficos en el panel: " + ", ".join(hits)


def test_pictograph_scanner_catches_escaped_and_odd_forms():
    bad = [
        ("a = '\\u2713';", ".js"), ("a = '\\u{1F514}';", ".js"), ("a = '\\ud83d\\udd14';", ".js"),
        ('x::before { content: "\\2713"; }', ".css"), ('x::before { content: "\\1F514"; }', ".css"),
        ("<p>&#x2713;</p>", ".html"), ("<p>&#10003;</p>", ".html"), ("<p>&check;</p>", ".html"),
        ("ℹ", ".js"), ("™", ".js"), ("©", ".js"), ("®", ".js"), ("•", ".js"), ("‼", ".js"),
        ("a\u200db", ".js"), ("a\ufe0fb", ".js"), ("\U0001F514", ".js"), ("✓", ".js"), ("＋", ".js"), ("‹", ".js"), ("←", ".js"),
    ]
    for text, suf in bad:
        assert pictograph_hits(text, suf), f"no detectado: {text!r}"
    for ok in ["Añadir · 5 × 3 – 2 — 1… ↑↓ 20° ¿qué? «sí»", "a = '\\n\\d+';", "áéíóúñÁÉÍÓÚÑüÜ"]:
        assert not pictograph_hits(ok, ".js"), f"falso positivo: {ok!r}"


# ---------------------------------------------------------------- colores en styles.css

NAMED_COLORS = set("""aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond blue blueviolet brown
burlywood cadetblue chartreuse chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan darkgoldenrod darkgray
darkgreen darkgrey darkkhaki darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon darkseagreen darkslateblue
darkslategray darkslategrey darkturquoise darkviolet deeppink deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite
forestgreen fuchsia gainsboro ghostwhite gold goldenrod gray green greenyellow grey honeydew hotpink indianred indigo ivory
khaki lavender lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan lightgoldenrodyellow lightgray lightgreen
lightgrey lightpink lightsalmon lightseagreen lightskyblue lightslategray lightslategrey lightsteelblue lightyellow lime
limegreen linen magenta maroon mediumaquamarine mediumblue mediumorchid mediumpurple mediumseagreen mediumslateblue
mediumspringgreen mediumturquoise mediumvioletred midnightblue mintcream mistyrose moccasin navajowhite navy oldlace olive
olivedrab orange orangered orchid palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink plum
powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown seagreen seashell sienna silver skyblue
slateblue slategray slategrey snow springgreen steelblue tan teal thistle tomato turquoise violet wheat white whitesmoke
yellow yellowgreen""".split())
COLOR_PROPS = (
    "color", "background", "background-color", "background-image", "border", "border-color", "outline", "outline-color",
    "fill", "stroke", "box-shadow", "text-shadow", "caret-color", "accent-color", "text-decoration-color",
)


def _blank_css_comments(css):
    import re
    return re.sub(r"/\*.*?\*/", lambda m: re.sub(r"[^\n]", " ", m.group(0)), css, flags=re.S)


def _split_decls(body):
    out, depth, cur = [], 0, ""
    for ch in body:
        depth += (ch == "(") - (ch == ")")
        if ch == ";" and depth == 0:
            out.append(cur); cur = ""
        else:
            cur += ch
    out.append(cur)
    return out


def literal_color_lines(css):
    """Líneas con colores literales en los VALORES de las declaraciones (no en selectores)."""
    import re

    css = _blank_css_comments(css)
    bad = set()
    for m in re.finditer(r"([^{}]*)\{([^{}]*)\}", css):
        if "@font-face" in m.group(1):
            continue
        base = m.start(2)
        for decl in _split_decls(m.group(2)):
            if ":" not in decl:
                continue
            prop, _, value = decl.partition(":")
            prop = prop.strip().lower()
            off = base + m.group(2).index(decl) + decl.index(":")
            line = css.count("\n", 0, off) + 1
            v = re.sub(r"url\(\s*#[^)]*\)", "", value)
            problem = False
            for h in re.finditer(r"#([0-9a-fA-F]+)\b", v):
                if h.group(1) not in ("000", "000000"):
                    problem = True
            if re.search(r"%23[0-9a-fA-F]{3,8}", v):
                problem = True
            for f in re.finditer(r"\b(rgba?|hsla?|hwb|lab|lch|oklab|oklch|color)\(", v):
                if not re.match(r"rgba?\(\s*0\s*,\s*0\s*,\s*0\s*[,)]", v[f.start():]):
                    problem = True
            if prop in COLOR_PROPS:
                words = re.sub(r"url\([^)]*\)", "", v)
                for w in re.finditer(r"(?<![\w-])([a-zA-Z]+)(?![\w(-])", words):
                    if w.group(1).lower() in NAMED_COLORS:
                        problem = True
            if problem:
                bad.add(line)
    return sorted(bad)


def test_panel_css_uses_only_tokens_for_colors():
    bad = literal_color_lines((PANEL / "styles.css").read_text(encoding="utf-8"))
    assert not bad, f"colores literales en styles.css, líneas: {bad}"


def test_color_scanner_catches_literals_and_spares_selectors():
    bad = [
        "a { color: #000; background: #ff0000; }", "a { background: #000fff; }", "a { color: #abc; }",
        "a { color: hsl(10, 50%, 50%); }", "a { color: hsla(10 50% 50% / .5); }", "a { color: red; }",
        "a { border: 1px solid white; }", "a { background: url(\"data:image/svg+xml,<svg fill='%23ff0000'/>\"); }",
        "a { box-shadow: 0 0 4px rgba(255, 0, 0, .5); }", "@media (x) { a { fill: gray; } }", "a { --x: #fff; }",
        "a {\n  color: var(--text);\n  outline: 2px solid rgb(1,2,3);\n}",
    ]
    for css in bad:
        assert literal_color_lines(css), f"no detectado: {css!r}"
    ok = [
        "#bad,\n#fab .x,\n.y { color: var(--text); }", "a { color: #000; background: #000000; }",
        "a { box-shadow: 0 1px 2px rgba(0, 0, 0, .4); }", "a { color: transparent; border: 0 none; outline: currentColor; }",
        "@font-face { src: url(x.woff2); color: #fff; }", "/* #ff0000 red */ a { color: var(--accent); }",
        "a { border: 1px solid var(--red-ish); background: url(red.png) none; }",
    ]
    for css in ok:
        assert not literal_color_lines(css), f"falso positivo: {css!r}"


# ---------------------------------------------------------------- iconos usados por el JS

def _blank_js_comments(src):
    """Quita comentarios (conserva saltos de línea y respeta cadenas y plantillas)."""
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if c in "\"'`":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            out.append(src[i:j + 1]); i = j + 1
        elif src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i)); i = j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("".join("\n" if ch == "\n" else " " for ch in src[i:j])); i = j
        else:
            out.append(c); i += 1
    return "".join(out)


def _args(src, pos):
    """Argumentos de nivel superior de la llamada cuyo '(' está antes de pos. [(texto, offset)]"""
    out, depth, start, i, n = [], 0, pos, pos, len(src)
    while i < n:
        c = src[i]
        if c in "\"'`":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            i = j + 1
            continue
        if c in "([{":
            depth += 1
        elif c in ")]}":
            if depth == 0:
                out.append((src[start:i], start)); return out
            depth -= 1
        elif c == "," and depth == 0:
            out.append((src[start:i], start)); start = i + 1
        i += 1
    return out


def _lit(arg):
    import re
    m = re.fullmatch(r"\s*([\"'`])([^\"'`$\\]*)\1\s*", arg)
    return m.group(2) if m and re.fullmatch(r"[a-z][a-z0-9-]*", m.group(2)) else None


def icon_names_used(src):
    """[(línea, nombre)] de todo literal que se usa como nombre de icono en el JS."""
    import re

    src = _blank_js_comments(src)
    line = lambda off: src.count("\n", 0, off) + 1
    found = []

    def add(arg, off):
        v = _lit(arg)
        if v:
            found.append((line(off + len(arg) - len(arg.lstrip())), v))

    first_arg_fns = {"icon", "iconButton", "emptyState", "block", "lbtn"}
    for m in re.finditer(r"(?<![\w$.])([A-Za-z_$][\w$]*)\s*\(", src):
        name = m.group(1)
        if name in ("function", "if", "for", "while", "switch", "catch", "return"):
            continue
        args = _args(src, m.end())
        if name in first_arg_fns and args:
            add(*args[0])
        if name == "btn" and len(args) > 3:
            add(*args[3])
        if "icon" in name.lower():  # red de seguridad: 1.º y 2.º argumento de cualquier función "*icon*"
            for a in args[:2]:
                add(*a)
        if name == "segmented" and args:
            _triples(args[0][0], args[0][1], src, add)
    for m in re.finditer(r"\bicon\s*:\s*([\"'`][^\"'`]*[\"'`])", src):
        add(m.group(1), m.start(1))
    # const X = [[..,..,"icono"], ..] pasado a segmented(X, ...) y mapas const *ICONS = {...}
    for m in re.finditer(r"\bsegmented\(\s*([A-Za-z_$][\w$]*)\s*,", src):
        d = re.search(r"\b(?:const|let|var)\s+" + re.escape(m.group(1)) + r"\s*=\s*\[", src)
        if d:
            _triples(src[d.end() - 1:], d.end() - 1, src, add)
    for m in re.finditer(r"\b(?:const|let|var)\s+\w*ICONS\w*\s*=\s*\{", src):
        body = _args(src, m.end())
        for part, off in body:
            if ":" in part:
                add(part.split(":", 1)[1], off + part.index(":") + 1)
    return sorted(set(found))


def _triples(text, off, src, add):
    """Tercer elemento de cada array de 3 elementos dentro de un literal de opciones."""
    import re
    for m in re.finditer(r"\[", text):
        parts = _args(text, m.end())
        if len(parts) == 3 and all(re.match(r"\s*[\"'`]", p) for p, _ in parts[:2]):
            add(parts[2][0], off + parts[2][1])


def _known_icons():
    import re
    sprite = (ROOT / "app-android/src/main/assets/face/icons.js").read_text(encoding="utf-8")
    return set(re.findall(r'^  "([a-z0-9-]+)": "', sprite, re.M))


def test_every_icon_used_by_the_panel_exists_in_the_sprite():
    import re
    known = _known_icons()
    assert known, "no se pudo leer el sprite"
    bad = []
    for f in _panel_files():
        if f.suffix == ".js":
            bad += [f"{f.name}:{n} {v!r}" for n, v in icon_names_used(f.read_text(encoding="utf-8")) if v not in known]
        if f.suffix == ".html":
            for i, l in enumerate(f.read_text(encoding="utf-8").split("\n"), 1):
                bad += [f"{f.name}:{i} {v!r}" for v in re.findall(r'data-icon="([a-z0-9-]+)"', l) if v not in known]
    assert not bad, "iconos inexistentes: " + ", ".join(bad)


def test_panel_uses_icons_so_the_scanner_is_not_blind():
    used = {v for f in _panel_files() if f.suffix == ".js" for _, v in icon_names_used(f.read_text(encoding="utf-8"))}
    assert len(used) >= 25, sorted(used)


def test_icon_scanner_covers_every_call_form():
    cases = [
        'icon("zz-no", 16)', "icon('zz-no')", "icon(`zz-no`, 24)", 'iconButton("zz-no", "Etiqueta", f)',
        'emptyState("zz-no", "Texto", null)', 'block("zz-no", "Título", null, x)', 'lbtn("zz-no", "a", "b", f, false)',
        'btn("x", () => go({a: 1}), "primary wide", "zz-no")', 'btn("x", f, "", "zz-no", { "aria-label": "x" })',
        'const o = { icon: "zz-no", d: 1 };', 'const SUBS = [["a", "A", "layers"], ["b", "B", "zz-no"]];\nsegmented(SUBS, v, f);',
        'segmented([["a", "A", "zz-no"]], v, f)', 'const TYPE_ICONS = { clock: "clock", text: "zz-no" };',
        'paintIcon(x, "zz-no")', 'myIcon("zz-no")',
    ]
    for c in cases:
        assert ("zz-no" in {v for _, v in icon_names_used(c)}), f"no detectado: {c!r}"
    names = {v for _, v in icon_names_used('// icon("zz-no")\n/* block("zz-no") */\nicon("plus", 16);\nicon(name, 16);')}
    assert names == {"plus"}
    assert icon_names_used('x;\ny;\nicon("a-b")') == [(3, "a-b")]
