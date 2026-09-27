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

# mtkclient en vendor/ (pinned). Si no está, se explica cómo traerlo.
if [ ! -f "$MTK_PY" ]; then
  warn "No encuentro mtkclient en $MTK_PY"
  info "Clónalo dentro de vendor/ (queda ignorado por git):"
  echo "  git clone https://github.com/bkerler/mtkclient '$VENDOR_DIR/mtkclient'"
  echo "  pip3 install -r '$VENDOR_DIR/mtkclient/requirements.txt'"
else
  info "mtkclient encontrado en $MTK_PY"
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
