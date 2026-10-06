#!/usr/bin/env bash
# Copia el motor de pantallas del APK al panel (la vista previa usa exactamente los mismos archivos).
#   bash tools/sync-face.sh           copia
#   bash tools/sync-face.sh --check   falla si las dos copias difieren (lo usa la CI)
set -euo pipefail
cd "$(dirname "$0")/.."
SRC=app-android/src/main/assets/face
DST=homelab/friday-panel/static/face
if [ "${1:-}" = "--check" ]; then
  diff -r "$SRC" "$DST"
  echo "face/ sincronizado"
  exit 0
fi
mkdir -p "$DST"
cp "$SRC"/* "$DST"/
echo "copiado $SRC -> $DST"
