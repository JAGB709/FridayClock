#!/usr/bin/env bash
# Instala en TU ordenador lo necesario para flashear el reloj: mtkclient, adb y
# (en Linux) las reglas udev. No toca el dispositivo.
source "$(dirname "$0")/lib/common.sh"
banner "setup_host — preparar el ordenador"

OS="$(uname -s)"
info "Sistema: $OS"

if [ "$OS" = "Linux" ]; then
  info "Instala dependencias (ejemplo Debian/Ubuntu):"
  echo "  sudo apt install -y python3 python3-pip android-tools-adb libusb-1.0-0"
elif [ "$OS" = "Darwin" ]; then
  info "Instala dependencias (Homebrew):"
  echo "  brew install python android-platform-tools libusb"
else
  warn "Windows: instala Python, 'platform-tools' (adb) y UsbDk. Usa NORECONNECT=1."
fi

# mtkclient en vendor/, fijado a un commit concreto (manifests/mtkclient.commit).
MTK_COMMIT="$(grep -v '^#' "$KIT_DIR/manifests/mtkclient.commit" | head -1 | tr -d '[:space:]')"
if [ ! -f "$MTK_PY" ]; then
  warn "No encuentro mtkclient en $MTK_PY"
  info "Clonando mtkclient en el commit fijado $MTK_COMMIT"
  run git clone https://github.com/bkerler/mtkclient "$VENDOR_DIR/mtkclient"
  run git -C "$VENDOR_DIR/mtkclient" checkout --detach "$MTK_COMMIT"
fi
if [ -f "$MTK_PY" ]; then
  actual="$(git -C "$VENDOR_DIR/mtkclient" rev-parse HEAD 2>/dev/null || echo desconocido)"
  [ "$actual" = "$MTK_COMMIT" ] || die "mtkclient está en $actual y debería estar en $MTK_COMMIT (git -C vendor/mtkclient checkout --detach $MTK_COMMIT)"
  info "mtkclient en el commit fijado ($MTK_COMMIT)"
  run pip3 install -r "$VENDOR_DIR/mtkclient/requirements.txt"
fi

if [ "$OS" = "Linux" ]; then
  info "Instalando reglas udev de mtkclient (requiere sudo):"
  rules=("$VENDOR_DIR/mtkclient/mtkclient/Setup/Linux/"*.rules)
  if [ -e "${rules[0]}" ]; then
    run sudo cp "${rules[@]}" /etc/udev/rules.d/
    run sudo udevadm control --reload-rules
    run sudo usermod -aG plugdev "$USER" || true
  else
    warn "No encuentro las reglas udev de mtkclient; revísalas en su repo."
  fi
fi

info "Listo. Ejecuta ./00_preflight.sh a continuación."
