#!/usr/bin/env bash
# Flashea un boot con Magisk. Opción B por defecto: parchear el boot PROPIO.
#   MODE=B (defecto): usa el boot del backup + Magisk (magiskboot/boot_patch.sh).
#   MODE=A          : usa vendor/boot_global_magisk.bin de Ayra (solo si la build coincide).
source "$(dirname "$0")/lib/common.sh"
banner "03_flash_boot — instalar boot rooteado"
backup="$(require_backup)"
MODE="${MODE:-B}"

boot_out="$KIT_DIR/vendor/boot_patched.img"

if [ "$MODE" = "A" ]; then
  src="$KIT_DIR/vendor/boot_global_magisk.bin"
  [ -f "$src" ] || die "Falta $src (cópialo del repo de Ayra a vendor/)."
  warn "Opción A: boot de Ayra (patch 2021-06). Solo si TU build coincide."
  warn "Este boot abre un root shell por red en el puerto 1337; considera bloquearlo."
  confirm_word "OPCION-A" "Vas a flashear un boot ajeno; ¿la build coincide?"
  boot_out="$src"
else
  # Opción B: parchear el boot del propio dispositivo.
  boot_src="$(ls -1 "$backup"/partitions/boot*.bin "$backup"/partitions/boot.img 2>/dev/null | head -1 || true)"
  [ -n "$boot_src" ] || die "No encuentro el boot en el backup ($backup/partitions/)."
  magiskboot="${MAGISKBOOT:-$(command -v magiskboot || true)}"
  patch="${MAGISK_PATCH:-$VENDOR_DIR/magisk/boot_patch.sh}"
  info "Boot original: $boot_src"
  if [ -x "$patch" ]; then
    info "Parcheando con Magisk boot_patch.sh"
    run env KEEPVERITY=false KEEPFORCEENCRYPT=false "$patch" "$boot_src"
    run mv -f new-boot.img "$boot_out" 2>/dev/null || true
  elif [ -n "$magiskboot" ]; then
    warn "Usa magiskboot manualmente o instala Magisk app y usa 'Patchear un fichero'."
    info "Alternativa recomendada: copia $boot_src al reloj, párchealo con la app Magisk,"
    info "y guarda el resultado como $boot_out antes de re-ejecutar en DRY_RUN=0."
    [ -f "$boot_out" ] || die "Falta $boot_out (boot parcheado)."
  else
    die "No hay magiskboot ni boot_patch.sh. Parchea el boot con la app Magisk y deja $boot_out."
  fi
fi

info "Se flasheará SOLO la partición boot con: $boot_out"
confirm_word "FLASH-BOOT" "Vas a escribir la partición boot."
mtk w boot "$boot_out"
info "Reinicia el reloj (primer arranque puede tardar). Luego 04_verify_root.sh."
info "Si hay bootloop: 99_rollback.sh (nivel 2 restaura el boot original)."
