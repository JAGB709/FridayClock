# Cronometro, temporizadores y vista de contenido: subproyecto 4d

Fecha: 2026-10-27 · Estado: implementado · Continua el motor de pantallas (3a) y el sistema de diseño (4)

## Objetivo

1. Cronometro y temporizadores con interfaz propia en el reloj, controlables por toque, API y panel.
2. Tarjetas de control genericas en Inicio del panel para esos controles.
3. Vista bonita del contenido que el agente envia con `display` (texto o markdown seguro).

## Diseno

- Dos elementos nuevos del motor, `stopwatch` (`controls`) y `timers` (`count` 1 a 3, `controls`), validados en
  `DesignSchema.kt`, dibujados por `face.js` con logica pura en `face-core.js` (tests node), presentes en
  `EditorCore.TYPES` y copiados al panel con `tools/sync-face.sh`.
- Datos del motor (`/face/data`): `now`, `stopwatch {running, base_ms, since}` y `timers [{id, label, at | remaining_ms, paused}]`.
  Tiempos absolutos mas la hora del reloj: el motor calcula el desfase con la hora local.
- Botones del motor: `Native.control(cmd)` con una lista cerrada (`FaceControl.parse`); en la vista previa
  y en el editor no hay botones.
- Cronometro: `Stopwatch` (estado en memoria). Temporizadores: siguen siendo alarmas absolutas; `TimerBook` guarda los pausados.
- API: `POST/GET /stopwatch`, `GET /timers`, `POST /timer` con `pause`, `resume`, `cancel` por `id` o `pid`.
  Acciones `stopwatch`, `pause_timer`, `resume_timer`, y `cancel_timer` con `id`/`pid`.
- Panel: `/api/timer`, `/api/timers`, `/api/stopwatch`; tarjetas Cronometro y Temporizadores en Inicio con cuenta local por segundo.
- Contenido del agente: `display` acepta `type` `markdown` y `title`; `SafeMarkdown` (puro, con tests) produce bloques y
  `DisplayView` los pinta como spans. Sin HTML, enlaces ni imagenes activos.

## Decisiones

- Decision: el cronometro vive en memoria; reiniciar la app lo pone a cero (es una ayuda puntual, no un registro).
- Decision: los temporizadores pausados tambien viven en memoria (maximo 5); los que corren sobreviven porque son alarmas.
- Decision: la lista blanca del asistente (`ActionPolicy`) admite `stopwatch`, `pause_timer` y `resume_timer` (inofensivos, como
  `set_timer`); `display` sigue bloqueado y no se toca.
- Decision: sin decimas en el cronometro (el motor refresca cada segundo, para cuidar la CPU del reloj).
- Decision: no se anade un tipo de elemento `display`; la vista de contenido es una capa nativa como hasta ahora, solo que mas cuidada.
- Decision: no se cambia la pantalla de alarma ni las pantallas nativas (subproyecto 4b) ni los elementos conectados (3b).
