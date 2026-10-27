#!/usr/bin/env bash
# Utilidades comunes para el device-kit. Se hace `source` desde cada script.
# Reglas: DRY_RUN=1 por defecto (no escribe nada), logging, y confirmación
# escrita para lo destructivo.

set -euo pipefail

# Raíz del device-kit (este fichero vive en device-kit/lib/).
KIT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$KIT_DIR/backups}"
LOG_DIR="${LOG_DIR:-$KIT_DIR/logs}"
VENDOR_DIR="$KIT_DIR/vendor"
mkdir -p "$LOG_DIR"

# DRY_RUN=1 (por defecto): solo imprime lo que haría. DRY_RUN=0 ejecuta de verdad.
DRY_RUN="${DRY_RUN:-1}"

# mtkclient: variable MTK apunta al mtk.py. Por defecto vendor/mtkclient/mtk.py.
MTK_PY="${MTK_PY:-$VENDOR_DIR/mtkclient/mtk.py}"
# En Windows hace falta --noreconnect; se puede forzar con NORECONNECT=1.
NORECONNECT="${NORECONNECT:-0}"

_ts()   { date '+%Y-%m-%d %H:%M:%S'; }
LOGFILE="$LOG_DIR/$(date '+%Y%m%d-%H%M%S')-$(basename "${0:-kit}" .sh).log"

log()   { echo "[$(_ts)] $*" | tee -a "$LOGFILE"; }
info()  { log "INFO  $*"; }
warn()  { log "WARN  $*" >&2; }
err()   { log "ERROR $*" >&2; }
die()   { err "$*"; exit 1; }

banner() {
  echo "==================================================================" | tee -a "$LOGFILE"
  echo " $*" | tee -a "$LOGFILE"
  echo " modo: $([ "$DRY_RUN" = 0 ] && echo 'EJECUCIÓN REAL' || echo 'DRY-RUN (no escribe nada)')" | tee -a "$LOGFILE"
  echo "==================================================================" | tee -a "$LOGFILE"
}

# Ejecuta un comando, o solo lo imprime en dry-run.
# Oculta valores de tokens antes de escribir un comando en el log.
redact() { sed -E 's/(--es (token|backend_token) )[^ ]+/\1***/g'; }

# Entrecomilla un valor para un shell remoto: ab'c -> 'ab'\''c'
shq() { local s="$1" sq="'"; printf "'%s'" "${s//$sq/$sq\\$sq$sq}"; }

# Oculta cualquier valor tras token/backend_token en la salida de adb.
redact_all() { sed -E "s/(token|backend_token)( |=)('?)[^' ]+/\\1\\2\\3***/g"; }

run() {
  if [ "$DRY_RUN" = 0 ]; then
    log "\$ $(printf '%s' "$*" | redact)"
    "$@" 2>&1 | tee -a "$LOGFILE"
    return "${PIPESTATUS[0]}"
  else
    log "(dry-run) \$ $(printf '%s' "$*" | redact)"
  fi
}

# mtkclient con los flags correctos según SO.
mtk() {
  local args=("$@")
  [ "$NORECONNECT" = 1 ] && args+=("--noreconnect")
  run python3 "$MTK_PY" "${args[@]}"
}

# Confirmación destructiva: hay que teclear una palabra concreta.
confirm_word() {
  local word="$1" ; shift
  local prompt="${*:-Esta acción es IRREVERSIBLE}"
  # ASSUME_YES=1 solo existe para automatizar pasos REVERSIBLES (p. ej. DESHABILITAR) y exige
  # además ASSUME_YES_ACK=SI_ASUMO_EL_RIESGO. Las confirmaciones irreversibles (desbloqueo,
  # flasheo, rollback, copia del backup) siempre se teclean.
  case "$word" in
    DESHABILITAR) ;;
    *) ASSUME_YES=0 ;;
  esac
  if [ "${ASSUME_YES:-0}" = 1 ]; then
    if [ "${ASSUME_YES_ACK:-}" = "SI_ASUMO_EL_RIESGO" ]; then
      warn "ASSUME_YES=1: se omite la confirmación de '$word'"
      return 0
    fi
    warn "ASSUME_YES=1 ignorado: falta ASSUME_YES_ACK=SI_ASUMO_EL_RIESGO"
  fi
  echo ""
  echo ">>> $prompt"
  echo ">>> Para continuar escribe exactamente: $word"
  read -r -p ">>> " answer
  [ "$answer" = "$word" ] || die "Confirmación incorrecta (esperaba '$word'). Aborto."
}

require_cmd() { command -v "$1" >/dev/null 2>&1 || die "Falta el comando '$1'. Ejecuta setup_host.sh."; }

require_backup() {
  local latest
  latest="$(ls -1d "$BACKUP_DIR"/*/ 2>/dev/null | tail -1 || true)"
  [ -n "$latest" ] || die "No hay backup en $BACKUP_DIR. Ejecuta 01_backup_full.sh ANTES."
  [ -f "$latest/VERIFIED" ] || die "El backup $latest no está verificado (falta VERIFIED). Aborto."
  echo "${latest%/}"
}

# Particiones únicas del dispositivo: NUNCA escribir desde otro aparato.
UNIQUE_PARTITIONS="proinfo nvram nvdata persist seccfg frp"
is_unique_partition() {
  local p="$1"
  for u in $UNIQUE_PARTITIONS; do [ "$p" = "$u" ] && return 0; done
  return 1
}
