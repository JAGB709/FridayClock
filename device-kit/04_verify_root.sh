#!/usr/bin/env bash
# Comprueba que el reloj arranca, hay ADB y root.
source "$(dirname "$0")/lib/common.sh"
banner "04_verify_root — comprobar arranque y root"

info "Esperando a que el reloj aparezca por ADB…"
run adb wait-for-device
run adb devices
info "Fingerprint de la build:"
run adb shell getprop ro.build.fingerprint
info "Comprobando root (su):"
if [ "$DRY_RUN" = 0 ]; then
  if adb shell 'su -c id' 2>/dev/null | grep -q 'uid=0'; then
    info "ROOT OK"
  else
    err "No hay root. Si hay bootloop usa 99_rollback.sh nivel 2."
    exit 1
  fi
else
  log "(dry-run) adb shell su -c id"
fi
