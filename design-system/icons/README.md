# Iconos

Juego Lucide (https://lucide.dev), licencia ISC. SVG descargados de `lucide-static@0.469.0`.
`alert-triangle.svg` es `triangle-alert.svg` de esa versión (el nombre se conserva por el plan).

Regenerar los VectorDrawable del reloj y el sprite del motor tras cambiar un SVG:

    python tools/gen-icons.py          # escribe
    python tools/gen-icons.py --check  # verifica (CI)
    bash tools/sync-face.sh            # copia el motor al panel
