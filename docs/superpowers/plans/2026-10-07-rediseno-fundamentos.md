# Rediseño 4a: fundamentos (tokens, tipografía, iconos, brillo) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sistema de diseño compartido (tokens, Inter, iconos Lucide) con la dirección Obsidiana, cero emojis en el reloj y el motor, temas por defecto nuevos y regla de brillo "panel por defecto, el reloj manda cuando lo cambias".

**Architecture:** `design-system/tokens.json` es la fuente de verdad; `tools/gen-tokens.py` genera `Tokens.kt` y `tokens.css` (en el motor); `tools/gen-icons.py` genera VectorDrawables y `icons.js` (en el motor) desde SVG de Lucide; el motor del reloj se copia al panel con `tools/sync-face.sh`, de modo que el panel reutiliza tokens, fuentes e iconos. El brillo vive en un objeto Kotlin puro `Brightness` con pruebas JVM.

**Tech Stack:** Kotlin 1.9 sin dependencias de runtime (JUnit en test), JS ES2017 clásico (WebView Chrome 74), Python 3 para generadores y pruebas (pytest del panel), `node --test`.

**Spec:** `docs/superpowers/specs/2026-10-07-rediseno-fundamentos-design.md`

## Global Constraints

- **Cero emojis** en interfaz, textos, etiquetas, código y comentarios nuevos del reloj, el motor y `design-system/`. Las flechas `↑ ↓` (U+2190 a U+21FF) y `·` están permitidas; `⚠ ⏰ ✓ ☀ ⛅ ☁ ☂ ❄ ⚡` y todo U+2300-23FF, U+2600-27BF, U+2B00-2BFF, U+FE0F y U+1F000+ no.
- Textos de interfaz y comentarios en español; commits en español, **sin** `Co-Authored-By` ni `Claude-Session` (regla del usuario, vale para subagentes).
- App del reloj sin dependencias de runtime (framework Android + Kotlin stdlib).
- JS del motor en ES2017 clásico (WebView Chrome 74): sin `?.`, `??`, `inset`, `gap` en flex, `aspect-ratio`.
- Tras editar cualquier archivo de `app-android/src/main/assets/face/` ejecutar `bash tools/sync-face.sh` (la CI usa `--check`). La copia del motor incluye ahora `tokens.css`, `icons.js` y `fonts/`.
- Lista de archivos del motor (en este orden, para la versión sha256 de 12 hex): `index.html`, `tokens.css`, `face.css`, `icons.js`, `face-core.js`, `face.js`. Debe coincidir en `FaceData.FILES` (Kotlin), `FACE_FILES` (`homelab/friday-panel/app.py`) y `FaceHost.FILES`. Las fuentes no entran en la versión.
- Paleta día: fondo `#000000`; superficie `#111111`; superficie elevada `#1A1A1A`; borde `#2A2A2A`; texto `#F2F2F2`; texto secundario `#8E8E93`; acento `#FFB454`; error `#FF6B6B`; éxito `#5AD19A`; aviso `#FFD166`. Paleta noche: texto `#B8A89A`; secundario `#6E625A`; acento `#9A5B12`.
- Tipografía: solo Inter (pesos 300, 400, 600, empaquetada en `assets/face/fonts/`); escala 12, 14, 16, 20, 28, 40, 56; números tabulares. Espaciado 4, 8, 12, 16, 24, 32, 48; radios 8, 12, 20; duraciones 150, 200, 250 ms; curva `cubic-bezier(0.2, 0, 0, 1)`; objetivos táctiles de al menos 48 px (barra del reloj 56 px); separación entre objetivos de al menos 8 px.
- Iconos: Lucide (ISC), trazo 2, rejilla 24. Juego: `bell mic settings sun cloud cloud-sun cloud-rain cloud-snow cloud-lightning cloud-fog moon clock alert-triangle check x trash-2 plus play pause volume-2 image upload chevron-right`.
- Nombres de icono de tiempo (`Weather.icon`): códigos 0 y 1 -> `sun`; 2 -> `cloud-sun`; 3 -> `cloud`; 45 y 48 -> `cloud-fog`; 51-67 y 80-82 -> `cloud-rain`; 71-77, 85, 86 -> `cloud-snow`; 95-99 -> `cloud-lightning`; resto -> `cloud`.
- Brillo: por defecto manda el del perfil activo; valor manual `{percent, profile_id, stamp}` en `Prefs`, persistente; se borra al cambiar el perfil programado o si cambia el conjunto de brillos de perfil del diseño guardado (`stamp`); botón "Volver al brillo del panel"; `POST /brightness` y `set_brightness` fijan el mismo valor manual.
- Comandos de prueba: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug`; `node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js`; `cd homelab/friday-panel && .venv/bin/python -m pytest -q`; `cd hermes-skill && pytest -q`.
- No tocar el diseño, los temas ni las fotos reales del usuario en el reloj (usar `/design/preview` temporal para verificar). No usar `force-stop` en el reloj.

## Review Focus

- Un diseño ya guardado con `font` `thin|normal|bold|mono|digital` y fondos de imagen sigue siendo válido y se ve igual (`inter` es un valor nuevo, no sustituye a los antiguos).
- `/face/data` con un nombre de icono desconocido, vacío o ausente: el motor no falla; se dibuja el texto sin icono.
- Brillo: un valor manual sobrevive a un reinicio, se aplica solo al perfil para el que se creó, no se borra al guardar un diseño que cambia otra cosa distinta del brillo, no se borra durante una vista previa, y se borra al cambiar de perfil programado o al cambiar un brillo de perfil.
- Fuentes: si una fuente da 404 o el WebView no la carga, el texto cae a `sans-serif` y `fitText`/layout no se rompen.
- Contraste AA de todos los pares declarados y el escáner de emojis atrapan una regresión (p. ej. reintroducir `⚠` en `face.js`).
- Un emoji o un nombre de icono nuevo no listado en el juego Lucide debe hacer fallar la prueba del sprite.

---

### Task 1: Tokens, generador y prueba de contraste

**Files:**
- Create: `design-system/tokens.json`
- Create: `tools/gen-tokens.py`
- Create (generados): `app-android/src/main/java/com/jarvis/clock/ui/Tokens.kt`, `app-android/src/main/assets/face/tokens.css`
- Create: `homelab/friday-panel/tests/test_design_system.py`

**Interfaces:**
- Consumes: nada.
- Produces: `Tokens` (Kotlin `object`) con `Tokens.Color.BG`, `SURFACE`, `SURFACE_HIGH`, `BORDER`, `TEXT`, `TEXT_MUTED`, `ACCENT`, `ON_ACCENT`, `ERROR`, `SUCCESS`, `WARNING` y `Tokens.Night.TEXT`, `TEXT_MUTED`, `ACCENT` (todos `Int` ARGB opacos), `Tokens.SPACE` (`IntArray`), `Tokens.RADIUS_SM/MD/LG`, `Tokens.TYPE` (`IntArray`), `Tokens.MOTION_FAST/BASE/SLOW` (ms, `Int`); `tokens.css` con las variables `--bg --surface --surface-high --border --text --text-muted --accent --on-accent --error --success --warning --night-text --night-text-muted --night-accent --space-1..7 --radius-sm --radius-md --radius-lg --fs-1..7 --dur-fast --dur-base --dur-slow --ease`; `python tools/gen-tokens.py [--check]`; funciones de prueba `contrast(hex_a, hex_b) -> float` en `test_design_system.py`.

- [ ] **Step 1: Crear `design-system/tokens.json`** exactamente con:

```json
{
  "color": {
    "bg": "#000000", "surface": "#111111", "surface_high": "#1A1A1A", "border": "#2A2A2A",
    "text": "#F2F2F2", "text_muted": "#8E8E93", "accent": "#FFB454", "on_accent": "#000000",
    "error": "#FF6B6B", "success": "#5AD19A", "warning": "#FFD166"
  },
  "night": { "text": "#B8A89A", "text_muted": "#6E625A", "accent": "#9A5B12" },
  "space": [4, 8, 12, 16, 24, 32, 48],
  "radius": { "sm": 8, "md": 12, "lg": 20 },
  "type": [12, 14, 16, 20, 28, 40, 56],
  "motion": { "fast": 150, "base": 200, "slow": 250, "ease": "cubic-bezier(0.2, 0, 0, 1)" },
  "pairs": [
    { "fg": "color.text", "bg": "color.bg", "min": 7.0 },
    { "fg": "color.text", "bg": "color.surface", "min": 7.0 },
    { "fg": "color.text_muted", "bg": "color.bg", "min": 4.5 },
    { "fg": "color.text_muted", "bg": "color.surface", "min": 4.5 },
    { "fg": "color.accent", "bg": "color.bg", "min": 4.5 },
    { "fg": "color.on_accent", "bg": "color.accent", "min": 4.5 },
    { "fg": "color.error", "bg": "color.bg", "min": 4.5 },
    { "fg": "color.success", "bg": "color.bg", "min": 4.5 },
    { "fg": "color.warning", "bg": "color.bg", "min": 4.5 },
    { "fg": "night.text", "bg": "color.bg", "min": 4.5 },
    { "fg": "night.text_muted", "bg": "color.bg", "min": 3.0, "note": "texto grande o secundario de noche" },
    { "fg": "night.accent", "bg": "color.bg", "min": 3.0, "note": "elementos grandes y gráficos de noche" }
  ]
}
```

- [ ] **Step 2: Escribir la prueba que falla** — `homelab/friday-panel/tests/test_design_system.py` (la ruta raíz del repositorio es `Path(__file__).resolve().parents[3]`):

```python
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
```

- [ ] **Step 3: Ejecutar para ver que falla**

Run: `cd homelab/friday-panel && .venv/bin/python -m pytest -q tests/test_design_system.py`
Expected: `test_generated_tokens_are_up_to_date` FALLA (no existe el generador); si algún par de contraste falla, sube la luminosidad del color mínimamente (no bajes el umbral salvo los dos pares de noche ya marcados con 3.0) y anótalo en el informe.

- [ ] **Step 4: Escribir `tools/gen-tokens.py`**

```python
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
```

- [ ] **Step 5: Generar y probar**

Run: `python tools/gen-tokens.py && cd homelab/friday-panel && .venv/bin/python -m pytest -q && cd ../.. && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug -q`
Expected: pytest PASS (incluidas las 3 pruebas nuevas) y la APK compila con `Tokens.kt`.

- [ ] **Step 6: Sincronizar y commit**

```bash
bash tools/sync-face.sh
git add design-system tools/gen-tokens.py app-android/src/main/java/com/jarvis/clock/ui/Tokens.kt app-android/src/main/assets/face/tokens.css homelab/friday-panel/static/face homelab/friday-panel/tests/test_design_system.py
git commit -m "Tokens de diseño Obsidiana: fuente única, generador y prueba de contraste"
```

---

### Task 2: Tipografía Inter empaquetada

**Files:**
- Create: `app-android/src/main/assets/face/fonts/Inter-Light.ttf`, `Inter-Regular.ttf`, `Inter-SemiBold.ttf`, `app-android/src/main/assets/face/fonts/OFL.txt`
- Create: `app-android/src/main/java/com/jarvis/clock/ui/Fonts.kt`
- Modify: `app-android/src/main/assets/face/face.css` (`@font-face`), `app-android/src/main/assets/face/index.html` (enlazar `tokens.css`), `app-android/src/main/java/com/jarvis/clock/ui/FaceHost.kt` (servir `fonts/`), `tools/sync-face.sh` (copia recursiva)
- Test: `homelab/friday-panel/tests/test_design_system.py`, `homelab/friday-panel/tests/test_face_sync.py` (si hace falta)

**Interfaces:**
- Consumes: `tokens.css` (Task 1).
- Produces: `Fonts.light(ctx)`, `Fonts.regular(ctx)`, `Fonts.semibold(ctx)` -> `Typeface` (con respaldo `Typeface.SANS_SERIF` si falla la carga) y `Fonts.tabular(tv: TextView)` que pone `fontFeatureSettings = "tnum"`; `@font-face` con `font-family: "Inter"` para pesos 300, 400, 600 apuntando a `fonts/Inter-*.ttf`; `FaceHost` sirve `/assets/face/fonts/<nombre>.ttf` (solo los tres nombres de la lista blanca, tipo `font/ttf`).

- [ ] **Step 1: Obtener las fuentes.** Descargar Inter 4.x (licencia SIL OFL) con `curl -L -o /tmp/inter.zip https://github.com/rsms/inter/releases/download/v4.0/Inter-4.0.zip`, extraer del zip los TTF estáticos `Inter-Light.ttf`, `Inter-Regular.ttf` e `Inter-SemiBold.ttf` (en `extras/ttf/` en esa versión; si la ruta difiere, buscarlos con `unzip -l`) y copiarlos a `app-android/src/main/assets/face/fonts/`, junto con el texto de la licencia como `OFL.txt` (`LICENSE.txt` del zip). Si no hay red o las rutas no existen, parar con estado BLOCKED y describir qué falta.

- [ ] **Step 2: Prueba que falla** — añadir a `test_design_system.py`:

```python
def test_inter_fonts_are_bundled_with_license():
    fonts = ROOT / "app-android/src/main/assets/face/fonts"
    for name in ("Inter-Light.ttf", "Inter-Regular.ttf", "Inter-SemiBold.ttf", "OFL.txt"):
        f = fonts / name
        assert f.is_file() and f.stat().st_size > 1000, name
    for name in ("Inter-Light.ttf", "Inter-Regular.ttf", "Inter-SemiBold.ttf"):
        assert (fonts / name).read_bytes()[:4] in (b"\x00\x01\x00\x00", b"true", b"OTTO")
```

Run: `cd homelab/friday-panel && .venv/bin/python -m pytest -q tests/test_design_system.py` -> FAIL antes de copiar las fuentes, PASS después.

- [ ] **Step 3: `tools/sync-face.sh`** — sustituir la línea `cp "$SRC"/* "$DST"/` por `cp -r "$SRC"/. "$DST"/` (copia recursiva incluida `fonts/`); `diff -r` del modo `--check` ya es recursivo.

- [ ] **Step 4: `face.css` e `index.html`.** En `index.html` añadir `<link rel="stylesheet" href="tokens.css">` antes del enlace a `face.css`. Al principio de `face.css`:

```css
@font-face { font-family: "Inter"; font-weight: 300; src: url("fonts/Inter-Light.ttf") format("truetype"); font-display: swap; }
@font-face { font-family: "Inter"; font-weight: 400; src: url("fonts/Inter-Regular.ttf") format("truetype"); font-display: swap; }
@font-face { font-family: "Inter"; font-weight: 600; src: url("fonts/Inter-SemiBold.ttf") format("truetype"); font-display: swap; }
```
y en la regla de `#root` añadir `font-variant-numeric: tabular-nums;`.

- [ ] **Step 5: `FaceHost.kt`** — en `interceptOrNull`, además de los `FILES` de la rama `/assets/face/`, servir fuentes: si `name` empieza por `fonts/` y el resto está en `FONT_FILES = setOf("Inter-Light.ttf", "Inter-Regular.ttf", "Inter-SemiBold.ttf")`, devolver `WebResourceResponse("font/ttf", null, ctx.assets.open("face/$name"))`. Cualquier otro nombre sigue dando 404. (Ampliar `FILES` con `tokens.css`, `icons.js` se hace en Task 4.) Añadir `tokens.css` ya aquí a `FILES` porque `index.html` lo enlaza.

- [ ] **Step 6: `Fonts.kt`**

```kotlin
package com.jarvis.clock.ui

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView

/** Inter empaquetada en assets; si no se puede cargar, la fuente sans-serif del sistema. */
object Fonts {
    private val cache = HashMap<String, Typeface>()

    private fun load(ctx: Context, file: String): Typeface = cache.getOrPut(file) {
        runCatching { Typeface.createFromAsset(ctx.applicationContext.assets, "face/fonts/$file") }
            .getOrDefault(Typeface.SANS_SERIF)
    }

    fun light(ctx: Context): Typeface = load(ctx, "Inter-Light.ttf")
    fun regular(ctx: Context): Typeface = load(ctx, "Inter-Regular.ttf")
    fun semibold(ctx: Context): Typeface = load(ctx, "Inter-SemiBold.ttf")

    /** Números de ancho fijo: la hora no baila al cambiar de cifra. */
    fun tabular(tv: TextView) { tv.fontFeatureSettings = "tnum" }
}
```

- [ ] **Step 7: Pruebas, sincronizar y commit**

Run: `python tools/gen-tokens.py --check && bash tools/sync-face.sh && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug -q && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q)`
Expected: todo PASS.

```bash
git add app-android/src/main/assets/face app-android/src/main/java/com/jarvis/clock/ui tools/sync-face.sh homelab/friday-panel/static/face homelab/friday-panel/tests
git commit -m "Tipografía: Inter empaquetada (300, 400, 600) con números tabulares"
```

---

### Task 3: Juego de iconos Lucide y generador

**Files:**
- Create: `design-system/icons/<nombre>.svg` (23 archivos) y `design-system/icons/README.md` (versión de Lucide, licencia ISC, cómo regenerar)
- Create: `tools/gen-icons.py`
- Create (generados): `app-android/src/main/res/drawable/ic_<nombre con guiones bajos>.xml` (23), `app-android/src/main/assets/face/icons.js`
- Test: `homelab/friday-panel/tests/test_design_system.py`, `app-android/face-tests/icons.test.js`

**Interfaces:**
- Consumes: nada.
- Produces: `R.drawable.ic_bell`, `ic_mic`, `ic_settings`, `ic_sun`, `ic_cloud`, `ic_cloud_sun`, `ic_cloud_rain`, `ic_cloud_snow`, `ic_cloud_lightning`, `ic_cloud_fog`, `ic_moon`, `ic_clock`, `ic_alert_triangle`, `ic_check`, `ic_x`, `ic_trash_2`, `ic_plus`, `ic_play`, `ic_pause`, `ic_volume_2`, `ic_image`, `ic_upload`, `ic_chevron_right` (VectorDrawable 24dp, trazo blanco para teñir en código); `icons.js` UMD con `FridayIcons.names` (lista), `FridayIcons.svg(name, size)` -> cadena SVG con `stroke="currentColor"` o `""` si el nombre no existe, y `FridayIcons.node(name, size)` -> elemento SVG o `null`; `python tools/gen-icons.py [--check]`.

- [ ] **Step 1: Obtener los SVG.** Para cada nombre del juego, `curl -sL https://unpkg.com/lucide-static@0.469.0/icons/<nombre>.svg -o design-system/icons/<nombre>.svg` (si esa versión no existe, usar la última publicada y anotarla). En `design-system/icons/README.md` anotar la versión, la licencia ISC de Lucide y `python tools/gen-icons.py` como regeneración. `alert-triangle` puede llamarse `triangle-alert` en versiones recientes: guardar el archivo como `alert-triangle.svg` igualmente. Si algún icono no se puede descargar, BLOCKED.

- [ ] **Step 2: Pruebas que fallan** — añadir a `test_design_system.py`:

```python
ICONS = ("bell mic settings sun cloud cloud-sun cloud-rain cloud-snow cloud-lightning cloud-fog moon clock "
         "alert-triangle check x trash-2 plus play pause volume-2 image upload chevron-right").split()


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
```
(`test_no_emojis...` fallará hasta la Task 4/5 porque aún quedan `⚠ ⏰ ✓ ☀…`; márcalo con `@pytest.mark.xfail(reason="se limpia en las tareas 4 y 5", strict=False)` ahora y quita la marca en la Task 5.)

Y `app-android/face-tests/icons.test.js`:

```js
const test = require("node:test");
const assert = require("node:assert");
const Icons = require("../src/main/assets/face/icons.js");

test("icons: sprite con todos los nombres y svg con trazo de 2", () => {
  assert.ok(Icons.names.includes("bell") && Icons.names.includes("cloud-rain"));
  const s = Icons.svg("bell", 24);
  assert.ok(s.startsWith("<svg") && s.includes('stroke="currentColor"') && s.includes('stroke-width="2"'));
});

test("icons: un nombre desconocido o vacio no falla", () => {
  assert.equal(Icons.svg("no-existe", 24), "");
  assert.equal(Icons.svg("", 24), "");
  assert.equal(Icons.svg(undefined, 24), "");
  assert.equal(Icons.node("no-existe", 24), null);
});
```

Run: `cd homelab/friday-panel && .venv/bin/python -m pytest -q tests/test_design_system.py; node --test app-android/face-tests/icons.test.js` -> FAIL.

- [ ] **Step 3: `tools/gen-icons.py`**

```python
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
```

- [ ] **Step 4: Ejecutar y comprobar**

Run: `python tools/gen-icons.py && bash tools/sync-face.sh && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q) && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug -q`
Expected: PASS y la APK compila con los 23 VectorDrawable (si Android rechaza algún `pathData`, corregir el convertidor, no el SVG).

- [ ] **Step 5: Commit**

```bash
git add design-system/icons tools/gen-icons.py app-android/src/main/res/drawable app-android/src/main/assets/face homelab/friday-panel/static/face homelab/friday-panel/tests app-android/face-tests/icons.test.js
git commit -m "Iconos Lucide: SVG fuente, VectorDrawable y sprite del motor generados"
```

---

### Task 4: Motor y datos — iconos en vez de emojis, fuente `inter`, movimiento reducido

**Files:**
- Modify: `app-android/src/main/assets/face/index.html`, `face.css`, `face-core.js`, `face.js`
- Modify: `app-android/src/main/java/com/jarvis/clock/weather/Weather.kt`, `design/FaceData.kt` (lista `FILES`), `design/DesignSchema.kt` (fuente `inter`), `ui/FaceHost.kt` (`FILES`)
- Modify: `homelab/friday-panel/app.py` (`FACE_FILES`), `homelab/friday-panel/static/pantallas.js` (`SAMPLE_DATA`, selector de fuente), `hermes-skill/mock_clock_server.py`
- Test: `app-android/face-tests/core.test.js`, `app-android/src/test/java/com/jarvis/clock/{WeatherParseTest,FaceDataTest,DesignSchemaTest}.kt`

**Interfaces:**
- Consumes: `FridayIcons.svg/node` y `names` (Task 3); `tokens.css` y fuentes (Tasks 1-2).
- Produces: `Weather.icon(code): String` devuelve nombres de icono (ver Global Constraints); `Weather.Report.shortText()` sin icono (solo texto: `"$temp° · $description · ↑$max° ↓$min°"` + lluvia); `/face/data` con `weather.icon` y los `icon` de previsión como nombres; `FaceCore.weatherParts(w, detail) -> {icon, text}`, `FaceCore.nextAlarmParts(na, showLabel) -> {icon, text}` (o `null` si no hay alarma), `FaceCore.fontStyle("inter") -> {family: '"Inter", sans-serif', weight: 300, digital: false}`; `DesignSchema` acepta `font: "inter"`; `FACE_FILES`/`FILES` = `index.html, tokens.css, face.css, icons.js, face-core.js, face.js`.

- [ ] **Step 1: Pruebas que fallan.**

`WeatherParseTest.kt`: la aserción `assertEquals("☀", h[0].icon)` pasa a `assertEquals("sun", h[0].icon)`; añadir:

```kotlin
    @Test fun `nombres de icono por codigo de tiempo`() {
        assertEquals("sun", Weather.icon(0)); assertEquals("sun", Weather.icon(1))
        assertEquals("cloud-sun", Weather.icon(2)); assertEquals("cloud", Weather.icon(3))
        assertEquals("cloud-fog", Weather.icon(45)); assertEquals("cloud-fog", Weather.icon(48))
        assertEquals("cloud-rain", Weather.icon(61)); assertEquals("cloud-rain", Weather.icon(81))
        assertEquals("cloud-snow", Weather.icon(73)); assertEquals("cloud-snow", Weather.icon(86))
        assertEquals("cloud-lightning", Weather.icon(95)); assertEquals("cloud", Weather.icon(1234))
    }
```
`FaceDataTest.kt`: `assertEquals("☀", w.getString("icon"))` pasa a `assertEquals("sun", w.getString("icon"))`.
`DesignSchemaTest.kt`: añadir

```kotlin
    @Test fun `la fuente inter es valida y las antiguas siguen siendolo`() {
        for (f in listOf("inter", "thin", "normal", "bold", "mono", "digital")) {
            val d = base()
            d.getJSONObject("themes").getJSONObject("clasico").put("font", f)
            assertTrue(f, DesignSchema.validate(d) is Result.Ok)
        }
        val d = base()
        d.getJSONObject("themes").getJSONObject("clasico").put("font", "comic")
        assertTrue(invalid(d).containsKey("themes.clasico.font"))
    }
```
`core.test.js`: sustituir los tests de `weatherLine`/`nextAlarmLine` por:

```js
test("weatherParts: icono y texto sin emojis", () => {
  const w = { temp: 19, icon: "sun", description: "Despejado", max: 22, min: 12, rain: 10 };
  assert.deepEqual(C.weatherParts(w, false), { icon: "sun", text: "19°" });
  assert.deepEqual(C.weatherParts(w, true), { icon: "sun", text: "19° · Despejado · ↑22° ↓12°" });
  assert.deepEqual(C.weatherParts({ ...w, rain: 50 }, true).text, "19° · Despejado · ↑22° ↓12° · lluvia 50%");
  assert.deepEqual(C.weatherParts(null, true), { icon: "", text: "—" });
});

test("nextAlarmParts: campana y texto, null sin alarma", () => {
  const na = { label: "Alarma", when: "mañana", time: "08:30" };
  assert.deepEqual(C.nextAlarmParts(na, true), { icon: "bell", text: "Alarma · mañana 08:30" });
  assert.deepEqual(C.nextAlarmParts(na, false), { icon: "bell", text: "mañana 08:30" });
  assert.equal(C.nextAlarmParts(null, true), null);
});

test("fontStyle inter", () => {
  assert.deepEqual(C.fontStyle("inter"), { family: "\"Inter\", sans-serif", weight: 300, digital: false });
  assert.equal(C.fontStyle("thin").weight, 100);
});
```
(Los datos de `forecastItems` de los tests existentes con `icon` emoji pasan a nombres; el motor solo los pasa tal cual.)

Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest -q; node --test app-android/face-tests/*.test.js` -> FAIL.

- [ ] **Step 2: Kotlin.** `Weather.icon`:

```kotlin
    fun icon(code: Int): String = when (code) {
        0, 1 -> "sun"
        2 -> "cloud-sun"
        45, 48 -> "cloud-fog"
        in 51..67, in 80..82 -> "cloud-rain"
        in 71..77, 85, 86 -> "cloud-snow"
        in 95..99 -> "cloud-lightning"
        else -> "cloud"
    }
```
`Report.shortText()` -> `"$temp° · ${description} · ↑$max° ↓$min°" + if (rainProb >= 30) " · lluvia $rainProb%" else ""` (sin icono; la carátula nativa pone el icono por separado en Task 5). `DesignSchema.FONTS` añade `"inter"`. `FaceData.FILES` = `listOf("index.html", "tokens.css", "face.css", "icons.js", "face-core.js", "face.js")`; `FaceHost.FILES` = `setOf("index.html", "tokens.css", "face.css", "icons.js", "face-core.js", "face.js")`; `app.py` `FACE_FILES = ("index.html", "tokens.css", "face.css", "icons.js", "face-core.js", "face.js")`.

- [ ] **Step 3: `index.html`** — cargar `icons.js` antes de `face-core.js`: `<script src="icons.js"></script><script src="face-core.js"></script><script src="face.js"></script>`.

- [ ] **Step 4: `face-core.js`.** Eliminar `weatherLine` y `nextAlarmLine` (y quitarlos del objeto exportado) y añadir:

```js
  function weatherParts(w, detail) {
    if (!w) return { icon: "", text: "—" };
    var s = w.temp + "°";
    if (detail) {
      s += " · " + w.description + " · ↑" + w.max + "° ↓" + w.min + "°";
      if (w.rain >= 30) s += " · lluvia " + w.rain + "%";
    }
    return { icon: w.icon || "", text: s };
  }

  function nextAlarmParts(na, showLabel) {
    if (!na) return null;
    return { icon: "bell", text: (showLabel !== false ? na.label + " · " : "") + na.when + " " + na.time };
  }
```
y en `fontStyle` la rama `if (font === "inter") return { family: "\"Inter\", sans-serif", weight: 300, digital: false };` antes del `return` final. Exportar `weatherParts`, `nextAlarmParts`.

- [ ] **Step 5: `face.js`.** Añadir el ayudante

```js
  function iconNode(name, size) {
    var n = window.FridayIcons ? window.FridayIcons.node(name, size || "1em") : null;
    if (!n) return null;
    n.setAttribute("class", "ico");
    return n;
  }
```
Reescribir `textBuilder` para que `getText` pueda devolver una cadena o `{icon, text}` (o `null` = vacío):

```js
  function textBuilder(box, r, getText, mode, onNode) {
    var node = el("div", "txt" + (mode === "block" ? " wrap" : ""));
    box.appendChild(node);
    if (onNode) onNode(node);
    var last = null;
    S.updaters.push(function (now, data) {
      var v = getText(now, data);
      var icon = v && typeof v === "object" ? v.icon : "";
      var s = v === null || v === undefined ? "" : (typeof v === "string" ? v : v.text);
      var key = icon + "|" + s;
      if (key === last) return;
      last = key;
      node.textContent = "";
      var ic = icon ? iconNode(icon, "1em") : null;
      if (ic) node.appendChild(ic);
      node.appendChild(document.createTextNode(s));
      node.style.fontSize = Core.fitText(s.length + (ic ? 2 : 0), r.width, r.height, 9999, mode) + "px";
    });
  }
```
Los constructores: `weather_now` -> `Core.weatherParts(data.weather, !!e.detail)`; `next_alarm` -> si `data.exact_alarm_missing` devuelve `{ icon: "alert-triangle", text: "Concede el permiso de alarmas exactas" }`, si no `Core.nextAlarmParts(data.next_alarm, e.show_label)` (que puede ser `null`); `text` y `date` siguen devolviendo cadenas. En `forecast`, la fila del icono (`var b = el("div"); b.textContent = items[j].icon;`) pasa a `var b = el("div"); var ic = iconNode(items[j].icon, "1.4em"); if (ic) b.appendChild(ic);`. Eliminar cualquier emoji restante en `face.js`/`face-core.js` (comentarios incluidos).

- [ ] **Step 6: `face.css`.** Añadir:

```css
.ico { display: inline-block; vertical-align: -0.14em; margin-right: 0.3em; }
@media (prefers-reduced-motion: reduce) {
  #content, .pl.fade { transition: none; }
}
```
y cambiar la transición de `#content` a `transition: opacity var(--dur-base) var(--ease);` y la de `.pl.fade` a `transition: opacity 0.6s var(--ease);` (el fundido del carrusel conserva sus 600 ms deliberados; los cambios de pantalla usan 200 ms).

- [ ] **Step 7: Datos de ejemplo, mock y panel.** `pantallas.js` `SAMPLE_DATA`: `icon: "sun"`, y en previsión `sun`, `cloud-sun`, `cloud`, `cloud-rain`, `cloud` (horas) y `cloud`, `cloud-rain`, `cloud-rain`, `cloud-sun`, `sun` (días). `hermes-skill/mock_clock_server.py`: `"icon": "sun"` y `"icon": "cloud"` donde estaban `☀`/`☁`. En el selector de fuente de tema de `pantallas.js` añadir `["inter", "Inter (fino, recomendado)"]` al principio de la lista de opciones.

- [ ] **Step 8: Sincronizar y probar**

Run: `bash tools/sync-face.sh && node --check app-android/src/main/assets/face/face.js && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug -q && (cd homelab/friday-panel && .venv/bin/python -m pytest -q) && (cd hermes-skill && pytest -q)`
Expected: todo PASS. Comprobación visual en Chrome headless (como en la tarea 4 de 3c): una carátula con `weather_now` (detail), `next_alarm` y `forecast`, y datos con un nombre de icono desconocido (`"icon": "xyz"`) y con `icon` ausente: se ven sin icono y sin errores en consola; fuente Inter aplicada con un tema `font: "inter"`. Registrar lo visto en el informe.

- [ ] **Step 9: Commit**

```bash
git add app-android homelab/friday-panel hermes-skill
git commit -m "Motor: iconos Lucide en lugar de emojis, fuente Inter y movimiento reducido"
```

---

### Task 5: Reloj nativo — tokens, Inter, iconos y fuera emojis

**Files:**
- Modify: `app-android/src/main/java/com/jarvis/clock/ui/Ui.kt`, `MainActivity.kt`, `ui/BluetoothActivity.kt`, `alarm/AlarmActivity.kt`, `ui/SettingsActivity.kt` (solo tipografía y colores de los títulos), `weather/Weather.kt` (nada más si ya está)
- Create: `app-android/src/main/java/com/jarvis/clock/ui/Icons.kt`
- Test: `homelab/friday-panel/tests/test_design_system.py` (quitar el `xfail` del escáner)

**Interfaces:**
- Consumes: `Tokens` (Task 1), `Fonts` (Task 2), `R.drawable.ic_*` (Task 3), `Weather.icon/shortText` (Task 4).
- Produces: `Icons.res(name: String): Int` (0 si no existe), `Icons.tinted(ctx, name, color): Drawable?`, `Ui.ACCENT/DIM/NIGHT` definidos desde `Tokens`; la carátula nativa y la barra inferior sin emojis; el escáner de emojis sin `xfail` y en verde.

- [ ] **Step 1: `Icons.kt`**

```kotlin
package com.jarvis.clock.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import com.jarvis.clock.R

/** Iconos Lucide (VectorDrawable) por nombre; un nombre desconocido devuelve 0 / null. */
object Icons {
    private val MAP = mapOf(
        "bell" to R.drawable.ic_bell, "mic" to R.drawable.ic_mic, "settings" to R.drawable.ic_settings,
        "sun" to R.drawable.ic_sun, "cloud" to R.drawable.ic_cloud, "cloud-sun" to R.drawable.ic_cloud_sun,
        "cloud-rain" to R.drawable.ic_cloud_rain, "cloud-snow" to R.drawable.ic_cloud_snow,
        "cloud-lightning" to R.drawable.ic_cloud_lightning, "cloud-fog" to R.drawable.ic_cloud_fog,
        "moon" to R.drawable.ic_moon, "clock" to R.drawable.ic_clock, "alert-triangle" to R.drawable.ic_alert_triangle,
        "check" to R.drawable.ic_check, "x" to R.drawable.ic_x, "trash-2" to R.drawable.ic_trash_2,
        "plus" to R.drawable.ic_plus, "play" to R.drawable.ic_play, "pause" to R.drawable.ic_pause,
        "volume-2" to R.drawable.ic_volume_2, "image" to R.drawable.ic_image, "upload" to R.drawable.ic_upload,
        "chevron-right" to R.drawable.ic_chevron_right,
    )

    fun res(name: String): Int = MAP[name] ?: 0

    fun tinted(ctx: Context, name: String, color: Int): Drawable? {
        val id = res(name)
        if (id == 0) return null
        return ctx.getDrawable(id)?.mutate()?.also { it.setColorFilter(color, PorterDuff.Mode.SRC_IN) }
    }
}
```

- [ ] **Step 2: `Ui.kt`.** `ACCENT = Tokens.Color.ACCENT`, `DIM = Tokens.Color.TEXT_MUTED`, `NIGHT = Tokens.Night.ACCENT`; añadir `val TEXT = Tokens.Color.TEXT`, `val SURFACE = Tokens.Color.SURFACE`, `val BORDER = Tokens.Color.BORDER`, `val ERROR = Tokens.Color.ERROR`.

- [ ] **Step 3: `MainActivity.kt`.** (a) En `buildUi`: la hora (`time`) usa `Fonts.light(this)` + `Fonts.tabular(time)`, la fecha y etiquetas `Fonts.regular(this)`; las etiquetas se crean con color de `Ui`. (b) La barra inferior: `iconButton(R.drawable.ic_bell)`, `ic_mic` y `ic_settings`, con objetivo de 56 dp (el micro 64 dp) y el micro con fondo circular `Tokens.Color.SURFACE_HIGH` y borde `Tokens.Color.ACCENT` de 2 dp; los iconos se tiñen con `Ui.TEXT` (y con `Ui.ACCENT` el del micro); cada botón tiene estado de pulsación visible (`alpha` 0,6 mientras está pulsado, con un `setOnTouchListener` que no consuma el evento) y `contentDescription` en español ("Alarmas", "Micrófono", "Ajustes"). (c) Quitar los emojis: la línea del tiempo muestra el icono con `weather.setCompoundDrawablesRelativeWithIntrinsicBounds(Icons.tinted(this, w.icon, Ui.DIM), null, null, null)` y `compoundDrawablePadding = dp(6)` (tamaño del icono ajustado a `dp(20)` con `setBounds`), texto `w.shortText()`; la próxima alarma pasa de `"⏰ ${a.label} · $day ${a.timeText()}"` a `"${a.label} · $day ${a.timeText()}"` con el icono `bell` como compound drawable teñido de `Ui.ACCENT`; el aviso `"⚠ Concede el permiso de alarmas exactas (toca aquí)"` pasa a `"Concede el permiso de alarmas exactas (toca aquí)"` con el icono `alert-triangle` teñido de `Tokens.Color.WARNING`. En modo noche (`applyNativeNightMode`) los iconos y el texto usan `Ui.NIGHT` y `Tokens.Night.TEXT_MUTED`. Hay que limpiar el compound drawable cuando no hay icono. (d) Los literales de color `#CC101820`, `#E6101820`, `#FF0D47A1` pasan a `Tokens.Color.SURFACE` con alfa (`ColorUtils`-equivalente a mano: `(0xCC shl 24) or (Tokens.Color.SURFACE and 0xFFFFFF)`) o `Tokens.Color.SURFACE_HIGH`.

- [ ] **Step 4: `BluetoothActivity.kt`** línea 76: `" · móvil conectado ✓"` pasa a `" · móvil conectado"`. **`AlarmActivity.kt`**: los colores por defecto y la tipografía de la hora usan `Tokens`/`Fonts.light` en lugar de `sans-serif-thin` (los colores del tema de alarma siguen mandando cuando hay tema). **`SettingsActivity.kt`**: títulos y etiquetas con `Fonts.regular/semibold` y colores `Ui.TEXT`/`Ui.DIM`/`Ui.ACCENT`; el botón Guardar con fondo `Tokens.Color.ACCENT` y texto `Tokens.Color.ON_ACCENT` (sin otros cambios de estructura).

- [ ] **Step 5: Escáner.** Quitar el `@pytest.mark.xfail` de `test_no_emojis_in_clock_engine_and_design_system`. Si el escáner encuentra más emojis, sustituirlos por texto o icono (nunca ampliar la lista de permitidos).

- [ ] **Step 6: Probar y compilar**

Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug -q && (cd homelab/friday-panel && .venv/bin/python -m pytest -q) && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js`
Expected: PASS, escáner en verde. (La verificación visual en el reloj real se hace en la Task 8.)

- [ ] **Step 7: Commit**

```bash
git add app-android homelab/friday-panel/tests
git commit -m "Reloj: tokens, Inter e iconos Lucide en la interfaz nativa, sin emojis"
```

---

### Task 6: Temas por defecto Obsidiana

**Files:**
- Modify: `app-android/src/main/java/com/jarvis/clock/design/DesignSchema.kt` (`initial`)
- Test: `app-android/src/test/java/com/jarvis/clock/DesignSchemaTest.kt`

**Interfaces:**
- Consumes: fuente `inter` válida (Task 4).
- Produces: `DesignSchema.initial(...)` incluye los temas `obsidiana_dia` ("Obsidiana Día") y `obsidiana_noche` ("Obsidiana Noche") además de los cinco existentes (`clasico`, `noche_roja`, `amanecer`, `minimal`, `digital_verde`); el perfil `dia` usa `obsidiana_dia`, el `noche` usa `obsidiana_noche`, y `alarm_screen.theme` es `obsidiana_dia`.

- [ ] **Step 1: Prueba que falla** (añadir a `DesignSchemaTest`):

```kotlin
    @Test fun `el diseno inicial usa los temas Obsidiana y conserva los antiguos`() {
        val d = base()
        val themes = d.getJSONObject("themes")
        for (id in listOf("obsidiana_dia", "obsidiana_noche", "clasico", "noche_roja", "amanecer", "minimal", "digital_verde")) {
            assertTrue(id, themes.has(id))
        }
        val dia = themes.getJSONObject("obsidiana_dia")
        assertEquals("Obsidiana Día", dia.getString("name")); assertEquals("inter", dia.getString("font"))
        assertEquals("#000000", dia.getJSONObject("bg").getString("color")); assertEquals("#FFB454", dia.getString("accent"))
        val noche = themes.getJSONObject("obsidiana_noche")
        assertEquals("#9A5B12", noche.getString("accent")); assertEquals("#B8A89A", noche.getString("fg"))
        val p = d.getJSONArray("profiles")
        assertEquals("obsidiana_dia", p.getJSONObject(0).getString("theme"))
        assertEquals("obsidiana_noche", p.getJSONObject(1).getString("theme"))
        assertEquals("obsidiana_dia", d.getJSONObject("alarm_screen").getString("theme"))
        assertTrue(DesignSchema.validate(d) is Result.Ok)
    }
```
(`assertEquals` ya está importado en el archivo.) Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.jarvis.clock.DesignSchemaTest'` -> FAIL.

- [ ] **Step 2: Implementar.** En `initial`, añadir antes de `clasico` en el `themes`:

```kotlin
            .put("obsidiana_dia", theme("Obsidiana Día", solid("#000000"), "#F2F2F2", "#8E8E93", "#FFB454", "inter"))
            .put("obsidiana_noche", theme("Obsidiana Noche", solid("#000000"), "#B8A89A", "#6E625A", "#9A5B12", "inter"))
```
y cambiar las referencias: el perfil `dia` pasa de `"clasico"` a `"obsidiana_dia"`, el `noche` de `"noche_roja"` a `"obsidiana_noche"`, y `alarm.put("theme", ...)` a `"obsidiana_dia"`. Los demás temas no cambian.

- [ ] **Step 3: Probar** — `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest` PASS (las pruebas antiguas que usan `clasico` siguen valiendo porque el tema se conserva); `cd homelab/friday-panel && .venv/bin/python -m pytest -q` PASS.

- [ ] **Step 4: Commit**

```bash
git add app-android/src
git commit -m "Temas por defecto Obsidiana Día y Obsidiana Noche"
```

---

### Task 7: Regla de brillo

**Files:**
- Create: `app-android/src/main/java/com/jarvis/clock/design/Brightness.kt`
- Modify: `app-android/src/main/java/com/jarvis/clock/Prefs.kt`, `MainActivity.kt`, `ui/SettingsActivity.kt`, `server/Api.kt` (sin cambio de ruta; solo comentarios si procede), `assistant/Actions.kt`, `Events.kt` (si cambia el evento)
- Test: `app-android/src/test/java/com/jarvis/clock/BrightnessTest.kt`

**Interfaces:**
- Consumes: `DesignSchema.activeProfile`, `DesignStore.load`, `Prefs`.
- Produces: `Brightness.Manual(percent: Int, profileId: String, stamp: Int)`; `Brightness.stampOf(design: JSONObject): Int` (hash de la lista `id:brightness` de los perfiles en orden, con 70 por defecto); `Brightness.valid(manual: Manual?, scheduledProfileId: String, savedDesign: JSONObject): Manual?` (nulo si `manual` es nulo, el perfil programado difiere o el `stamp` difiere); `Brightness.effective(profileBrightness: Int, manual: Manual?, profileId: String): Int` (el manual si existe y es del mismo perfil, si no el del perfil, siempre acotado a 1..100); `Prefs.manualBrightness: Brightness.Manual?` (get/set; set a `null` lo borra); `Brightness.setManual(ctx, percent)` (guarda con el perfil programado y el sello del diseño guardado) y `Brightness.clearManual()`; la pantalla Ajustes con deslizador y botón "Volver al brillo del panel".

- [ ] **Step 1: Pruebas que fallan** — `BrightnessTest.kt`:

```kotlin
package com.jarvis.clock

import com.jarvis.clock.design.Brightness
import com.jarvis.clock.design.Brightness.Manual
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrightnessTest {
    private fun design(vararg p: Triple<String, Int, Boolean>): JSONObject {
        val arr = JSONArray()
        for ((id, b, _) in p) arr.put(JSONObject().put("id", id).put("brightness", b))
        return JSONObject().put("profiles", arr)
    }

    @Test fun `sin valor manual manda el brillo del perfil`() {
        assertEquals(70, Brightness.effective(70, null, "dia"))
        assertEquals(3, Brightness.effective(3, null, "noche"))
    }

    @Test fun `el valor manual manda en su perfil y se acota`() {
        assertEquals(40, Brightness.effective(70, Manual(40, "dia", 1), "dia"))
        assertEquals(100, Brightness.effective(70, Manual(500, "dia", 1), "dia"))
        assertEquals(1, Brightness.effective(70, Manual(-3, "dia", 1), "dia"))
    }

    @Test fun `el valor manual de otro perfil no se aplica`() {
        assertEquals(3, Brightness.effective(3, Manual(40, "dia", 1), "noche"))
    }

    @Test fun `el sello cambia solo si cambia un brillo de perfil o el conjunto de perfiles`() {
        val a = design(Triple("dia", 70, false), Triple("noche", 3, true))
        val sameBrightness = design(Triple("dia", 70, false), Triple("noche", 3, true)).also { it.put("themes", JSONObject()) }
        val b = design(Triple("dia", 80, false), Triple("noche", 3, true))
        val c = design(Triple("dia", 70, false))
        assertEquals(Brightness.stampOf(a), Brightness.stampOf(sameBrightness))
        assertNotEquals(Brightness.stampOf(a), Brightness.stampOf(b))
        assertNotEquals(Brightness.stampOf(a), Brightness.stampOf(c))
    }

    @Test fun `valid conserva el manual mientras perfil y sello coinciden`() {
        val d = design(Triple("dia", 70, false), Triple("noche", 3, true))
        val m = Manual(40, "dia", Brightness.stampOf(d))
        assertEquals(m, Brightness.valid(m, "dia", d))
    }

    @Test fun `valid borra el manual al cambiar el perfil programado`() {
        val d = design(Triple("dia", 70, false), Triple("noche", 3, true))
        assertNull(Brightness.valid(Manual(40, "dia", Brightness.stampOf(d)), "noche", d))
    }

    @Test fun `valid borra el manual si el panel cambia un brillo de perfil`() {
        val d = design(Triple("dia", 70, false), Triple("noche", 3, true))
        val m = Manual(40, "dia", Brightness.stampOf(d))
        val changed = design(Triple("dia", 65, false), Triple("noche", 3, true))
        assertNull(Brightness.valid(m, "dia", changed))
    }

    @Test fun `valid sin manual devuelve nulo`() {
        assertNull(Brightness.valid(null, "dia", design(Triple("dia", 70, false))))
    }
}
```
Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.jarvis.clock.BrightnessTest'` -> FAIL (no existe `Brightness`).

- [ ] **Step 2: `Brightness.kt`**

```kotlin
package com.jarvis.clock.design

import android.content.Context
import com.jarvis.clock.Prefs
import org.json.JSONObject

/**
 * Regla del brillo: por defecto manda el del perfil activo (el del panel); si se cambia en el reloj,
 * ese valor manda para ese perfil hasta que el reloj cambie de perfil o el panel cambie un brillo de perfil.
 */
object Brightness {
    data class Manual(val percent: Int, val profileId: String, val stamp: Int)

    /** Hash de "id:brillo" de todos los perfiles, en orden: cambia solo si cambia un brillo o el conjunto de perfiles. */
    fun stampOf(design: JSONObject): Int {
        val ps = design.optJSONArray("profiles") ?: return 0
        val sb = StringBuilder()
        for (i in 0 until ps.length()) {
            val p = ps.optJSONObject(i) ?: continue
            sb.append(p.optString("id")).append(':').append(p.optInt("brightness", 70)).append(';')
        }
        return sb.toString().hashCode()
    }

    fun valid(manual: Manual?, scheduledProfileId: String, savedDesign: JSONObject): Manual? {
        if (manual == null) return null
        if (manual.profileId != scheduledProfileId) return null
        if (manual.stamp != stampOf(savedDesign)) return null
        return manual
    }

    fun effective(profileBrightness: Int, manual: Manual?, profileId: String): Int {
        val v = if (manual != null && manual.profileId == profileId) manual.percent else profileBrightness
        return v.coerceIn(1, 100)
    }

    /** Guarda un valor manual para el perfil programado ahora, con el sello del diseño guardado. */
    fun setManual(ctx: Context, percent: Int, minuteOfDay: Int) {
        val design = DesignStore.load(ctx).design
        val id = DesignSchema.activeProfile(design, minuteOfDay)?.optString("id").orEmpty()
        Prefs.manualBrightness = Manual(percent.coerceIn(1, 100), id, stampOf(design))
    }

    fun clearManual() { Prefs.manualBrightness = null }
}
```

- [ ] **Step 3: `Prefs.kt`** — añadir:

```kotlin
    /** Brillo fijado a mano en el reloj (persistente); ver [com.jarvis.clock.design.Brightness]. */
    var manualBrightness: com.jarvis.clock.design.Brightness.Manual?
        get() {
            if (!sp.contains("manual_brightness")) return null
            return com.jarvis.clock.design.Brightness.Manual(
                sp.getInt("manual_brightness", 70), sp.getString("manual_brightness_profile", "") ?: "",
                sp.getInt("manual_brightness_stamp", 0))
        }
        set(v) {
            val e = sp.edit()
            if (v == null) e.remove("manual_brightness").remove("manual_brightness_profile").remove("manual_brightness_stamp")
            else e.putInt("manual_brightness", v.percent.coerceIn(1, 100)).putString("manual_brightness_profile", v.profileId)
                .putInt("manual_brightness_stamp", v.stamp)
            e.apply()
        }
```
(Usar el mismo `sp` que el resto de `Prefs`.)

- [ ] **Step 4: `MainActivity.kt`.** Sustituir `brightnessOverride` por el valor manual de `Prefs`:
- En `applyNightMode()`: calcular el diseño guardado (`cachedDesign ?: DesignStore.load(this).design.also { cachedDesign = it }`, nunca el de la vista previa) y el perfil programado con `DesignSchema.activeProfile(saved, minuteOfDay())`; `val manual = Brightness.valid(Prefs.manualBrightness, scheduledId, saved)`; si `Prefs.manualBrightness != null && manual == null`, borrarlo (`Brightness.clearManual()`) **solo si no hay vista previa activa** (`previewDesign == null`); durante una vista previa no se borra ni se aplica a otro perfil. El porcentaje: `Brightness.effective(base, manual, profile.optString("id"))` donde `base` es `wakeBrightness(design)` si `profileNight && awake` y no hay manual válido para ese perfil, y `profile.optInt("brightness", 70)` en el resto. Un manual válido para el perfil de noche también manda con la pantalla despierta.
- En `applyNativeNightMode()`: `val pct = Prefs.manualBrightness?.percent ?: if (night) Prefs.nightBrightness else Prefs.dayBrightness`.
- En `onEvent`: `is Event.Brightness -> { Brightness.setManual(this, e.percent, minuteOfDay()); applyNightMode() }`; en `SettingsChanged`, si alguna clave termina en `brightness`, `Brightness.clearManual()`.
- En `onResume()` llamar a `applyNightMode()` (si no lo hace ya) para recoger el valor manual fijado en Ajustes.
- Eliminar el campo `brightnessOverride`.

- [ ] **Step 5: `SettingsActivity.kt`.** En la sección "Pantalla", antes de los campos numéricos de brillo, añadir una fila con título "Brillo actual", un `SeekBar` (1..100, alto táctil 48 dp, `progress` = brillo efectivo ahora) y el valor en texto; al soltar (`onStopTrackingTouch`) llamar a `Brightness.setManual(this, progress, minutoDelDía)`, aplicar al instante `window.attributes.screenBrightness` y refrescar el texto; debajo, un botón "Volver al brillo del panel" que llama a `Brightness.clearManual()`, recalcula el deslizador con el brillo del perfil y muestra el aviso "Brillo del panel restaurado". El brillo efectivo mostrado sale de `Brightness.effective(profileBrightness, Brightness.valid(Prefs.manualBrightness, id, saved), id)`. Usar `Tokens`/`Fonts` como en la Task 5.

- [ ] **Step 6: `Actions.kt`** — `"set_brightness"` sigue publicando `Events.Event.Brightness(...)`; no hay más cambios (MainActivity ya guarda el valor manual). Actualizar el comentario de `Events.Event.Brightness` ("valor manual de brillo: manda en el perfil actual hasta que cambie el perfil o el panel cambie un brillo").

- [ ] **Step 7: Probar**

Run: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug -q`
Expected: `BrightnessTest` y el resto PASS, APK compila.

- [ ] **Step 8: Commit**

```bash
git add app-android/src
git commit -m "Brillo: manda el del panel y, si se cambia en el reloj, el valor manual hasta cambiar de perfil o de brillo en el panel"
```

---

### Task 8: Panel mínimo, CI, documentación y verificación en el reloj real

**Files:**
- Modify: `homelab/friday-panel/static/index.html` (enlazar `/face/tokens.css`), `homelab/friday-panel/static/styles.css` (variables desde tokens, `@font-face` de Inter desde `/face/fonts/`), `.github/workflows/build-apk.yml` (`python tools/gen-tokens.py --check` y `python tools/gen-icons.py --check` en el trabajo homelab, antes de `sync-face.sh --check`), `README.md`, `CLAUDE.md` (solo añadir), `hermes-skill/mi-smart-clock/SKILL.md`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: panel que usa los tokens y Inter sin rediseño de pantallas; CI que valida generadores; documentación coherente; verificación en el reloj real.

- [ ] **Step 1: Panel.** En `index.html`, antes de `styles.css`: `<link rel="stylesheet" href="/face/tokens.css">`. En `styles.css`: añadir `@font-face` Inter (300, 400, 600) apuntando a `/face/fonts/Inter-*.ttf` y `font-display: swap`; redefinir las variables de `:root` existentes con los tokens (`--bg: var(--bg)` no sirve: usar nombres propios del panel `--bg`, `--card`, `--line`, `--text`, `--muted`, `--accent`, `--bad`, `--ok` asignados a `var(--surface)`, `var(--border)`, `var(--text)` etc. del archivo de tokens; renombrar si hay choque); `font: 16px/1.5 "Inter", system-ui, sans-serif`; color de texto de botón del acento `var(--on-accent)`. Sin cambios de estructura ni de textos; el rediseño real es 4c. Comprobar con una captura de Chrome headless (390 px) que el panel carga y se ve coherente; `pytest` del panel PASS.

- [ ] **Step 2: CI.** En `.github/workflows/build-apk.yml`, en el trabajo que ejecuta `tools/sync-face.sh --check`, añadir antes: `python tools/gen-tokens.py --check` y `python tools/gen-icons.py --check`. Comprobar que el YAML sigue siendo válido (`python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/build-apk.yml'))"` si PyYAML está disponible).

- [ ] **Step 3: Documentación.** `README.md`: sección "Sistema de diseño" (tokens, Inter, Lucide, cómo regenerar con los dos scripts, regla de cero emojis), el cambio del contrato de `/face/data` (`icon` pasa a nombre de icono), los dos temas Obsidiana y la regla de brillo (valor del panel por defecto; el reloj manda al cambiarlo; se borra al cambiar de perfil o al cambiar un brillo de perfil en el panel; botón "Volver al brillo del panel"; `POST /brightness` usa la misma regla). `hermes-skill/mi-smart-clock/SKILL.md`: `brightness` fija el brillo manual; `weather.icon` es un nombre. `CLAUDE.md`: añadir una línea sobre `design-system/`, `tools/gen-tokens.py`, `tools/gen-icons.py` y los comandos `--check` (solo añadir, otra sesión edita el archivo).

- [ ] **Step 4: Pruebas completas (como la CI)**

Run: `python tools/gen-tokens.py --check && python tools/gen-icons.py --check && bash tools/sync-face.sh --check && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest assembleDebug && node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js && (cd homelab/friday-panel && .venv/bin/python -m pytest -q) && (cd hermes-skill && pytest -q) && python -m py_compile homelab/friday-panel/app.py`
Expected: todo PASS.

- [ ] **Step 5: Commit**

```bash
git add homelab/friday-panel/static .github README.md CLAUDE.md hermes-skill
git commit -m "Panel con tokens e Inter, CI de generadores y documentación del sistema de diseño"
```

- [ ] **Step 6: Verificación en el reloj real** (la hace el controlador, no un subagente; sin tocar el diseño ni las fotos del usuario; sin `force-stop`). Instalar con `adb install -r`; comprobar con capturas: carátula nativa de respaldo (WebView desactivado y reactivado reiniciando con `adb install -r`) y carátula del motor con un diseño de prueba en `/design/preview` que use los temas Obsidiana día y noche, el tiempo y la próxima alarma con iconos Lucide y la fuente Inter; barra inferior con iconos nuevos y objetivos de 56 px; deslizador de brillo en Ajustes y "Volver al brillo del panel"; secuencia de brillo: cambiar el brillo en Ajustes, comprobar que se aplica y persiste tras `adb install -r`, que se borra al cambiar de perfil (probar con `/design/preview` de otro perfil no lo borra) y que guardar un brillo de perfil distinto lo borra (probar sin guardar el diseño del usuario: usar un diseño de prueba vía `PUT /design` **no está permitido**; verificar esta última transición con la prueba unitaria y, en el reloj, solo con `/settings` `day_brightness` que sí lo borra). Medir `dumpsys meminfo com.jarvis.clock` (objetivo: sin crecimiento apreciable respecto a 3c). Desplegar el panel al homelab (rsync + `docker compose up -d --build`, según la memoria `reloj-homelab-despliegue`) y comprobar `/api/face-version`. Cerrar con la lista de comprobación del encargo para esta parte (legibilidad a 2 m, modo noche, contraste, tamaños táctiles, iconos, cero emojis) y el resumen de qué cambió y por qué.
