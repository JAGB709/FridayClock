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
  run adb shell am broadcast -a com.jarvis.clock.CONFIGURE -n "$pkg/.ConfigReceiver" \
    --es token "$CLOCK_TOKEN" \
    ${BACKEND_URL:+--es backend_url "$BACKEND_URL"} \
    ${BACKEND_TOKEN:+--es backend_token "$BACKEND_TOKEN"} \
    ${ASSISTANT_NAME:+--es assistant_name "$ASSISTANT_NAME"} \
    --ez bind_tailscale_only true
else
  warn "CLOCK_TOKEN sin definir: configúralo luego en Ajustes del reloj."
fi

# Fijar Friday como launcher (HOME).
info "Fijando Friday como launcher predeterminado…"
run adb shell cmd package set-home-activity "$pkg/.MainActivity" || \
  warn "No se pudo fijar por ADB; hazlo en el reloj (botón Home > Friday > Siempre)."

info "Inicia sesión en Tailscale en el reloj para unirlo a tu tailnet."
