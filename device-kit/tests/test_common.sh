#!/usr/bin/env bash
# Pruebas de lib/common.sh sin tocar ningun dispositivo:  bash device-kit/tests/test_common.sh
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export LOG_DIR; LOG_DIR="$(mktemp -d)"
fails=0
check() { # descripcion comando...
  local d="$1"; shift
  if "$@"; then echo "ok   $d"; else echo "FALLA $d"; fails=$((fails+1)); fi
}

# 1) ASSUME_YES=1 solo no basta: hay que aceptar el riesgo explicitamente.
t_assume_sin_aceptar() { ( export ASSUME_YES=1; source "$HERE/../lib/common.sh"; echo "no" | confirm_word DESHABILITAR "x" ) >/dev/null 2>&1; [ $? -ne 0 ]; }
check "ASSUME_YES=1 sin ASSUME_YES_ACK no omite la confirmacion" t_assume_sin_aceptar

t_assume_con_aceptar() { ( export ASSUME_YES=1 ASSUME_YES_ACK=SI_ASUMO_EL_RIESGO; source "$HERE/../lib/common.sh"; confirm_word DESHABILITAR "x" ) >/dev/null 2>&1; }
check "ASSUME_YES=1 con ASSUME_YES_ACK omite la confirmacion" t_assume_con_aceptar

# 2) Las palabras irreversibles (flasheo, desbloqueo, rollback, backup) nunca se omiten.
t_irreversible() { ( export ASSUME_YES=1 ASSUME_YES_ACK=SI_ASUMO_EL_RIESGO; source "$HERE/../lib/common.sh"; echo "no" | confirm_word DESBLOQUEAR "x" ) >/dev/null 2>&1; [ $? -ne 0 ]; }
check "DESBLOQUEAR exige teclearse siempre" t_irreversible

# 3) La confirmacion correcta pasa.
t_correcta() { ( source "$HERE/../lib/common.sh"; echo "DESHABILITAR" | confirm_word DESHABILITAR "x" ) >/dev/null 2>&1; }
check "confirmacion tecleada correcta" t_correcta

# 4) redact oculta tokens.
t_redact() { ( source "$HERE/../lib/common.sh"; out="$(printf '%s' 'adb --es token abc123 --es backend_token zzz' | redact)"; [[ "$out" != *abc123* && "$out" != *zzz* ]] ); }
check "redact oculta token y backend_token" t_redact

# 5) shq entrecomilla de forma segura y redact_all oculta tokens en la salida.
t_shq() { ( source "$HERE/../lib/common.sh"; [ "$(shq "a'b c")" = "'a'\\''b c'" ] ); }
check "shq escapa comillas simples" t_shq
t_redact_all() { ( source "$HERE/../lib/common.sh"; out="$(printf '%s' "--es token 'abc123'" | redact_all)"; [[ "$out" != *abc123* ]] ); }
check "redact_all oculta el token en la salida" t_redact_all

rm -rf "$LOG_DIR"
[ "$fails" = 0 ] && echo "TODO OK" || { echo "$fails fallos"; exit 1; }
