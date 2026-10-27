# Skill y tools de Hermes para el Mi Smart Clock (Friday)

Da a **Friday** (tu agente en Hermes) el control del reloj de la mesilla desde
cualquier canal: Telegram, un cron, la web o el propio reloj.

## Contenido

- `mi-smart-clock/` — **skill de Hermes** (formato agentskills.io). Cópiala a
  `~/.hermes/skills/`. Usa `mi-smart-clock/scripts/clock.py` (solo stdlib).
- `clock_tools.py` — las mismas acciones como **funciones Python** importables,
  por si prefieres registrarlas como tools nativas del agente en vez de la skill.
- `mock_clock_server.py` — un reloj de mentira para probar sin el dispositivo.
- `tests/` — `pytest` de las tools contra el mock.

## Configuración

URL y token del reloj por entorno (nunca en el código):

```bash
export CLOCK_URL="http://<ip-tailscale-reloj>:8080"
export CLOCK_TOKEN="<token del reloj>"   # Ajustes del reloj > Token, o por ADB
```

Los recordatorios (`reminder-add`, `reminder-list`, `reminder-delete`; funciones `reminder_*`) hablan con el puente del homelab, no con el reloj, y necesitan `BRIDGE_URL` (por ejemplo `http://127.0.0.1:8765`) y `BRIDGE_TOKEN`. Ver [`docs/recordatorios.md`](../docs/recordatorios.md).

La skill acepta además `~/.config/mi-smart-clock.json`:

```json
{"url": "http://100.x.y.z:8080", "token": "..."}
```

## Instalar la skill en Hermes

```bash
cp -r mi-smart-clock ~/.hermes/skills/
python3 ~/.hermes/skills/mi-smart-clock/scripts/clock.py status   # prueba
```

Necesita el toolset `terminal` de Hermes. Cada skill es también un slash-command:
`/mi-smart-clock pon una alarma a las 7 mañana`.

## Probar sin el reloj

```bash
pip install pytest
pytest -q          # levanta el mock y prueba las tools
```

O a mano:

```bash
python3 mock_clock_server.py --port 8080 --token secreto &
CLOCK_URL=http://127.0.0.1:8080 CLOCK_TOKEN=secreto \
  python3 -c "import clock_tools as c; print(c.clock_set_alarm('07:30', days='weekdays'))"
```

## Usar las tools desde tu propio código

```python
import clock_tools
clock_tools.clock_say("La cena está lista")
clock_tools.clock_set_alarm("06:45", label="Gym", days="mon,wed,fri")
```

Todas devuelven `{"ok": bool, ...}`; con el reloj apagado o el token mal,
devuelven `{"ok": False, "error": ...}` en vez de lanzar excepción.
