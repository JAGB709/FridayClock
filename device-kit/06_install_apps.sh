#!/usr/bin/env bash
# Instala Tailscale y Friday, fija Friday como HOME y configura el token.
source "$(dirname "$0")/lib/common.sh"
banner "06_install_apps — Tailscale + Friday"
run adb wait-for-device

friday_apk="${FRIDAY_APK:-$(ls -1 "$KIT_DIR"/vendor/friday*.apk "$KIT_DIR"/../app-android/build/outputs/apk/release/*.apk 2>/dev/null | head -1 || true)}"
ts_apk="${TAILSCALE_APK:-$(ls -1 "$KIT_DIR"/vendor/tailscale*.apk 2>/dev/null | head -1 || true)}"

[ -n "$friday_apk" ] || die "No encuentro el APK de Friday (vendor/ o app-android/build/…)."
info "APK Friday: $friday_apk"
run adb install -r "$friday_apk"

if [ -n "$ts_apk" ]; then
  info "APK Tailscale: $ts_apk"
  run adb install -r "$ts_apk"
else
  warn "Sin APK de Tailscale en vendor/. Descárgalo de F-Droid e instálalo aparte."
fi

# Permisos por ADB (evita pelearse con la pantalla pequeña).
pkg="com.jarvis.clock"
for perm in RECORD_AUDIO POST_NOTIFICATIONS; do
  run adb shell pm grant "$pkg" "android.permission.$perm" || warn "No se pudo conceder $perm"
done
# Desactivar optimización de batería para que el servidor no muera.
run adb shell dumpsys deviceidle whitelist +$pkg || true

# Configurar el token (y opcionalmente backend/ciudad) por broadcast.
if [ -n "${CLOCK_TOKEN:-}" ]; then
  # Los tokens viajan por stdin hacia 'adb shell' (no aparecen en argv ni en 'ps' del ordenador).
  q() { shq "$1"; }   # entrecomilla para el shell del reloj
  cfg="am broadcast -a com.jarvis.clock.CONFIGURE -n $pkg/.ConfigReceiver --es token $(q "$CLOCK_TOKEN")"
  [ -n "${BACKEND_URL:-}" ] && cfg+=" --es backend_url $(q "$BACKEND_URL")"
  [ -n "${BACKEND_TOKEN:-}" ] && cfg+=" --es backend_token $(q "$BACKEND_TOKEN")"
  [ -n "${ASSISTANT_NAME:-}" ] && cfg+=" --es assistant_name $(q "$ASSISTANT_NAME")"
  cfg+=" --ez bind_tailscale_only true"
  if [ "$DRY_RUN" = 0 ]; then
    log "\$ adb shell (config por stdin, tokens ocultos)"
    printf '%s\nexit\n' "$cfg" | adb shell 2>&1 | redact_all | tee -a "$LOGFILE"
  else
    log "(dry-run) adb shell < broadcast CONFIGURE (tokens por stdin, ocultos)"
  fi
else
  warn "CLOCK_TOKEN sin definir: configúralo luego en Ajustes del reloj."
fi

# Fijar Friday como launcher (HOME).
info "Fijando Friday como launcher predeterminado…"
run adb shell cmd package set-home-activity "$pkg/.MainActivity" || \
  warn "No se pudo fijar por ADB; hazlo en el reloj (botón Home > Friday > Siempre)."

info "Inicia sesión en Tailscale en el reloj para unirlo a tu tailnet."
