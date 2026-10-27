# Plan: recordatorios

1. `reminders.py` y `test_reminders.py`: validación, repeticiones, almacén atómico, vencidos (TDD).
2. `bridge.py` y `test_notify.py`: `/v1/notify`, `/v1/reminders`, planificador, logs sin secretos.
3. Dockerfile, compose y requirements del puente (volumen `/data`, `tzdata`).
4. Mock, `clock_tools.reminder_*`, `clock.py reminder-*`, SKILL.md y tests.
5. Panel: proxy `/api/reminders` y configuración del puente con tests; tarjeta en Alarmas con `ui.js` y tests de diseño.
6. Documentación: `docs/recordatorios.md`, README del puente y del panel, README raíz, `docs/api.md`.
