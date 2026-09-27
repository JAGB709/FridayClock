#!/usr/bin/env bash
# Comprobaciones previas: herramientas, checksums del vendor, espacio, APK y token.
source "$(dirname "$0")/lib/common.sh"
banner "00_preflight — comprobaciones previas"
fail=0

for c in python3 adb; do
  if command -v "$c" >/dev/null 2>&1; then info "OK comando $c"; else err "Falta $c"; fail=1; fi
done
[ -f "$MTK_PY" ] && info "OK mtkclient" || { err "Falta mtkclient ($MTK_PY)"; fail=1; }

# Espacio: >= 8 GB libres para los backups.
avail_kb="$(df -Pk "$KIT_DIR" | awk 'NR==2{print $4}')"
if [ "${avail_kb:-0}" -ge 8388608 ]; then info "OK espacio ($((avail_kb/1024/1024)) GB libres)"
else err "Menos de 8 GB libres ($((avail_kb/1024/1024)) GB)"; fail=1; fi

# Checksums del vendor (si hay manifiesto).
if [ -s "$KIT_DIR/manifests/checksums.sha256" ]; then
  info "Verificando checksums de vendor/"
  ( cd "$KIT_DIR" && if [ "$DRY_RUN" = 0 ]; then sha256sum -c manifests/checksums.sha256; else sha256sum -c manifests/checksums.sha256 || warn "checksums no coinciden (dry-run informativo)"; fi ) | tee -a "$LOGFILE" || fail=1
else
  warn "Sin manifests/checksums.sha256 (ok si aún no has puesto ficheros en vendor/)"
fi

# APK de Friday.
apk="$(ls -1 "$KIT_DIR"/vendor/*.apk "$KIT_DIR"/../app-android/build/outputs/apk/release/*.apk 2>/dev/null | head -1 || true)"
[ -n "$apk" ] && info "OK APK: $apk" || warn "No encuentro el APK de Friday (ponlo en vendor/ o compílalo)"

# Token del reloj.
[ -n "${CLOCK_TOKEN:-}" ] && info "OK CLOCK_TOKEN definido" || warn "CLOCK_TOKEN sin definir (lo pondrás en 06_install_apps.sh)"

[ "$fail" = 0 ] && info "Preflight OK" || die "Preflight con errores; corrígelos antes de seguir."
