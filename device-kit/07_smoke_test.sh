#!/usr/bin/env bash
# Prueba de humo: /status, /display, alarma a +2 min y persistencia tras reinicio.
source "$(dirname "$0")/lib/common.sh"
banner "07_smoke_test — prueba de humo"

: "${CLOCK_URL:?define CLOCK_URL=http://<ip-tailscale-reloj>:8080}"
: "${CLOCK_TOKEN:?define CLOCK_TOKEN}"
H=(-H "X-Auth-Token: $CLOCK_TOKEN" -H "Content-Type: application/json")

api() { # método path [json]
  local m="$1" p="$2" body="${3:-}"
  if [ "$DRY_RUN" = 0 ]; then
    if [ -n "$body" ]; then curl -fsS -X "$m" "${H[@]}" -d "$body" "$CLOCK_URL$p";
    else curl -fsS -X "$m" "${H[@]}" "$CLOCK_URL$p"; fi
    echo
  else
    log "(dry-run) curl -X $m $CLOCK_URL$p ${body:+-d '$body'}"
  fi
}

info "1) /health"; api GET /health
info "2) /status"; api GET /status
info "3) /display (mensaje de prueba 10 s)"
api POST /display '{"type":"text","content":"Friday en marcha","duration_ms":10000}'

# Alarma a +2 minutos.
when="$(date -d '+2 min' '+%H:%M' 2>/dev/null || date -v+2M '+%H:%M')"
info "4) Alarma de prueba a las $when"
api POST /alarm "{\"action\":\"set\",\"time\":\"$when\",\"label\":\"Prueba Friday\"}"

info "5) Reinicio del reloj y comprobación de que la alarma sobrevive"
run adb reboot
run adb wait-for-device
if [ "$DRY_RUN" = 0 ]; then sleep 25; fi
info "Alarmas tras el reinicio:"; api GET /alarms
info "Comprueba que la alarma de las $when sigue en la lista y que suena a su hora."
info "Prueba también desde el servidor de Madrid (por Tailscale) el mismo /status."
