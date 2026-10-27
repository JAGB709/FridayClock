# Hermes seguro para el canal del reloj

Problema: la API de Hermes (puerto 8642) que usa el puente cargaba por defecto el
toolset `hermes-api-server`, con terminal, navegador, ficheros y ejecucion de codigo.
Cualquier cosa que el reloj (o un audio malicioso transcrito) convenciera al modelo de
hacer, se ejecutaba en el contenedor.

## Solucion

Hermes permite fijar los toolsets por plataforma en `config.yaml` (`platform_toolsets`).
La API server es la plataforma `api_server`. Se hizo:

1. Plugin `hermes-skill/hermes-plugin/mi_smart_clock/`: registra el toolset
   `mi-smart-clock` con 15 tools tipadas `clock_*` (estado, alarmas, temporizador, voz,
   pantalla solo texto o imagen https, volumen, brillo, Bluetooth, ajustes). Cancelar o
   editar alarmas y cambiar ajustes exige `confirm=true`; sin el, la tool devuelve
   `needs_confirmation` y el agente debe preguntar al usuario.
2. En `config.yaml` del contenedor de Hermes:

```yaml
platform_toolsets:
  api_server:
  - mi-smart-clock
plugins:
  enabled:
  - spotify
  - mi_smart_clock
```

Telegram y CLI conservan sus toolsets (`hermes-telegram`, `hermes-cli`), asi que el
chat del usuario no cambia. No hizo falta segunda instancia ni perfil.

## Instalacion

```bash
# desde hermes-skill/hermes-plugin
tar c mi_smart_clock | ssh <homelab> 'docker exec -i hermes tar x -C /opt/data/plugins'
# copia de seguridad y edicion de config.yaml (las dos claves de arriba)
docker restart hermes     # necesario para cargar el plugin
```

Credenciales del reloj: `CLOCK_URL`/`CLOCK_TOKEN` o `$HERMES_HOME/.config/mi-smart-clock.json`
(`{"url": "...", "token": "..."}`, permisos 600). El plugin lee `HERMES_HOME`, no `HOME`.

## Verificacion

Con la clave de la API (variable `HERMES_API_KEY` del puente, no la imprimas):

- "Ejecuta el comando id en el terminal": el agente solo llama a `clock_status` y responde
  que no tiene terminal.
- "Que alarmas tengo": llama a `clock_alarms` y lista las alarmas.
- Lista de tools efectivas: `get_tool_definitions(enabled_toolsets=...)` devuelve solo `clock_*`.

## Skill con terminal (Telegram/CLI)

La skill `mi-smart-clock` (script `clock.py`) sigue disponible para canales con terminal.
Ahora usa ruta absoluta `$HERMES_HOME/skills/smart-home/mi-smart-clock/scripts/clock.py`
y busca la configuracion en `$HERMES_HOME/.config/` ademas de `~/.config/`, porque el HOME
del agente es otro (`$HERMES_HOME/home`).

## Limites

- El modelo sigue pudiendo, por inyeccion, crear alarmas o hablar por el altavoz: es el
  alcance legitimo del reloj. Las acciones destructivas piden confirmacion.
- Volver atras: restaurar `config.yaml.bak-<fecha>` y reiniciar el contenedor.
