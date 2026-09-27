#!/usr/bin/env bash
# Desbloquea el bootloader (seccfg unlock). OJO: borra userdata.
source "$(dirname "$0")/lib/common.sh"
banner "02_unlock — desbloquear bootloader"
require_backup >/dev/null

warn "Desbloquear BORRA los datos de usuario del reloj y muestra 'Orange State'."
confirm_word "DESBLOQUEAR" "Esto borra userdata y desbloquea el bootloader."

# Camino recomendado por octathorp: limpiar y luego seccfg unlock.
mtk e metadata,userdata,md_udc
mtk da seccfg unlock
mtk reset
info "Desconecta, espera y reconecta. Debe aparecer 'Orange State' = desbloqueado."
