#!/usr/bin/env bash
# Descubre y DESHABILITA (no desinstala) los paquetes de Google/Assistant.
source "$(dirname "$0")/lib/common.sh"
banner "05_debloat — deshabilitar Google (reversible)"
run adb wait-for-device

info "Buscando paquetes de Google/Assistant instalados…"
pkgs=""
if [ "$DRY_RUN" = 0 ]; then
  pkgs="$(adb shell pm list packages | sed 's/package://' | tr -d '\r' \
    | grep -Ei 'com\.google|com\.android\.vending|gms|assistant|velvet|googlequicksearchbox' || true)"
else
  log "(dry-run) adb shell pm list packages | grep google/gms/assistant…"
  pkgs="com.google.android.googlequicksearchbox com.google.android.gms com.android.vending"
fi

[ -n "$pkgs" ] || { warn "No se encontraron paquetes de Google (¿ya deshabilitados?)"; exit 0; }
echo "Paquetes a deshabilitar:" | tee -a "$LOGFILE"
echo "$pkgs" | tr ' ' '\n' | sed '/^$/d' | tee -a "$LOGFILE"

confirm_word "DESHABILITAR" "Se deshabilitarán estos paquetes (reversible con 99_rollback.sh)."

: > "$LOG_DIR/disabled_packages.txt"
for p in $pkgs; do
  [ -n "$p" ] || continue
  if run adb shell pm disable-user --user 0 "$p"; then
    echo "$p" >> "$LOG_DIR/disabled_packages.txt"
  else
    warn "No se pudo deshabilitar $p (puede ser crítico; se deja)"
  fi
done
info "Lista guardada en $LOG_DIR/disabled_packages.txt"
info "Reinicia el reloj y comprueba que arranca sin la interfaz de Google."
