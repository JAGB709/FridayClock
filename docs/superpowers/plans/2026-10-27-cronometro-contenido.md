# Plan: cronometro, temporizadores y vista de contenido (4d)

1. Nucleo del motor: `face-core.js` (`stopwatchMs`, `formatDuration`, `timerItems`, `clockOffset`, `controlCommand`) con tests node.
2. Esquema: `stopwatch` y `timers` en `DesignSchema.kt` con `DesignTimersTest`.
3. Estado en el reloj: `Stopwatch`, `TimerBook`, `FaceControl`, acciones, API y `FaceData`; tests `StopwatchTest`.
4. Motor: builders en `face.js`, estilos en `face.css`, `Native.control`, `tools/sync-face.sh`.
5. Skill y mock: `clock_tools.py`, `clock.py`, `SKILL.md`, `mock_clock_server.py` y tests.
6. Panel: endpoints, tarjetas en Inicio, `EditorCore.TYPES` y datos de ejemplo, con tests.
7. Contenido del agente: `SafeMarkdown`, `DisplayView`, `title` en `display`, con `SafeMarkdownTest`.
8. Documentacion: `docs/api.md`, `docs/pantallas-y-temas.md`.
Verificacion: gradle `testDebugUnitTest`, pytest de hermes-skill y panel, `node --test`, `tools/sync-face.sh --check`.
