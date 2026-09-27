#!/usr/bin/env bash
# Deshacer cambios en tres niveles.
#   LEVEL=1  re-habilitar los paquetes de Google (desde disabled_packages.txt)
#   LEVEL=2  reflashear el boot ORIGINAL del backup
#   LEVEL=3  restaurar TODAS las particiones del backup (excepto las únicas si no son de este aparato)
source "$(dirname "$0")/lib/common.sh"
banner "99_rollback — restaurar"
LEVEL="${LEVEL:-1}"

case "$LEVEL" in
  1)
    info "Nivel 1: re-habilitar paquetes de Google"
    list="$LOG_DIR/disabled_packages.txt"
    [ -f "$list" ] || die "No hay $list"
    run adb wait-for-device
    while read -r p; do [ -n "$p" ] && run adb shell pm enable "$p"; done < "$list"
    info "Paquetes re-habilitados."
    ;;
  2)
    backup="$(require_backup)"
    boot_src="$(ls -1 "$backup"/partitions/boot*.bin "$backup"/partitions/boot.img 2>/dev/null | head -1 || true)"
    [ -n "$boot_src" ] || die "No encuentro el boot original en $backup/partitions/"
    warn "Nivel 2: reflashear el boot ORIGINAL ($boot_src)"
    confirm_word "ROLLBACK-BOOT" "Vas a restaurar el boot original."
    mtk w boot "$boot_src"
    info "Boot original restaurado. Reinicia."
    ;;
  3)
    backup="$(require_backup)"
    warn "Nivel 3: restaurar TODAS las particiones del backup."
    warn "Se OMITEN las particiones únicas del dispositivo salvo que sean de ESTE aparato:"
    echo "  $UNIQUE_PARTITIONS"
    confirm_word "ROLLBACK-TOTAL" "Restauración completa desde $backup."
    for f in "$backup"/partitions/*; do
      [ -f "$f" ] || continue
      name="$(basename "$f")"; part="${name%%.*}"
      if is_unique_partition "$part" && [ "${RESTORE_UNIQUE:-0}" != 1 ]; then
        warn "Omito partición única: $part (usa RESTORE_UNIQUE=1 solo si es el backup de ESTE reloj)"
        continue
      fi
      mtk w "$part" "$f"
    done
    info "Restauración completa. Reinicia."
    ;;
  *) die "LEVEL debe ser 1, 2 o 3" ;;
esac
