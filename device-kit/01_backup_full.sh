#!/usr/bin/env bash
# Backup COMPLETO de todas las particiones con mtkclient. Imprescindible.
source "$(dirname "$0")/lib/common.sh"
banner "01_backup_full — copia de seguridad completa"

dest="$BACKUP_DIR/$(date '+%Y%m%d-%H%M%S')"
info "Destino: $dest"
info "Conecta el reloj con VOL+ pulsado cuando mtkclient lo pida."
run mkdir -p "$dest"

# Volcado de todas las particiones a un directorio.
mtk rl "$dest/partitions"

# GPT aparte, por comodidad.
mtk printgpt

# Checksums de todo lo volcado.
if [ "$DRY_RUN" = 0 ]; then
  ( cd "$dest" && find . -type f ! -name '*.sha256' -exec sha256sum {} \; > checksums.sha256 )
  info "Checksums en $dest/checksums.sha256"
  # Comprobación simple de tamaño (la flash es ~4 GB).
  total=$(du -sm "$dest" | awk '{print $1}')
  info "Tamaño del backup: ${total} MB"
  [ "${total:-0}" -ge 500 ] || warn "El backup parece pequeño (${total} MB); revísalo."
fi

echo ""
info "Copia AHORA $dest a un segundo lugar (otro disco / la nube)."
confirm_word "COPIADO" "¿Has copiado el backup a un segundo sitio? Sin esto no se sigue."
run touch "$dest/VERIFIED"
info "Backup marcado como verificado. Ya puedes continuar."
