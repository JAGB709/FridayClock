# Plan: Friday — liberar el Xiaomi Mi Smart Clock (X04G)

> Estado: Fases 0–3 ejecutadas en este repo (código, tests y scripts en seco).
> La Fase 4 se ejecuta en local con el reloj conectado — ver [`RUNBOOK.md`](RUNBOOK.md).

Este documento tiene dos usos:
1. **Plan de ejecución** para las Fases 0–3, que no necesitan el reloj.
2. **Runbook local**: la Fase 4 la ejecutas tú, con el reloj por USB, usando lo
   preparado en `device-kit/` (detallado en `RUNBOOK.md`).

## Contexto

- Dispositivo: Xiaomi Mi Smart Clock, variante global **X04G** (MediaTek **MT8167**,
  1 GB RAM, Android 10, pantalla táctil 800x480).
- Objetivo: rootear la firmware global con un `boot.img` parcheado con Magisk,
  **deshabilitar (no desinstalar)** Google Assistant y servicios de Google, e instalar
  la app propia **Friday** como launcher. El "cerebro" (Hermes Agent) vive en un
  servidor en Madrid; el reloj está en Países Bajos. Se comunican por **Tailscale**.
- Repos de referencia (leídos, no asumidos): `AyraHikari/xiaomi-x04g-lx04`,
  `octathorp/x04g_tools`, `bkerler/mtkclient`. Hallazgos en `docs/research-notes.md`.

## Estructura del repo

```
app-android/      app Friday (Kotlin)
hermes-skill/     tools de Hermes + mock del reloj + tests
homelab/          clock-bridge (Whisper -> Hermes -> Piper)
device-kit/       scripts de flasheo con DRY_RUN, lib/, manifests/, vendor/
docs/             research-notes.md, decisions.md, voz-friday.md
RUNBOOK.md        Fase 4 con checkpoints
```

## Fase 0 — Investigación ✔

Leer los repos de referencia y documentar en `docs/research-notes.md` y
`docs/decisions.md`: modo BROM/preloader, `boot.img` de Ayra y su build,
`seccfg unlock`/`vbmeta`, particiones únicas del aparato, sintaxis de mtkclient,
registro de skills en Hermes, Tailscale en Android 10, y voz (Whisper/Piper).
**Salida:** notas con fuentes; dudas de dispositivo marcadas "verificar".

## Fase 1 — App Android ✔

Correcciones: alarma con **full-screen intent**; `targetSdk`/`FOREGROUND_SERVICE_SPECIAL_USE`;
sonido de alarma robusto con volumen y auto-silencio; **token configurable por ADB** y
**503 sin token**; superficie de red (bind a Tailscale, token en tiempo constante);
escape de HTML en `/display`; **alarmas recurrentes** (`repeat_days`); `GET /alarms`
legible; **tests JVM** (parseo, próxima ocurrencia, DST Amsterdam, serialización);
Gradle wrapper; **CI** que compila y publica el APK.
**Salida:** CI en verde, APK, tests pasando.

## Fase 2 — Skill de Hermes y mock ✔

`hermes-skill/clock_tools.py` (display, alarmas, listar…), `mock_clock_server.py`,
tests `pytest` (ok, token erróneo, timeout, reloj apagado) y README.
**Salida:** `pytest` en verde.

## Fase 3 — Kit de dispositivo y runbook ✔

`device-kit/` con `set -euo pipefail`, logging, **DRY_RUN=1 por defecto**, confirmaciones
escritas, precondiciones (03 exige backup verificado), `vendor/` + `checksums.sha256`.
Scripts `setup_host`, `00_preflight`…`07_smoke_test`, `99_rollback`. Probados con
`shellcheck` y en seco. `RUNBOOK.md` generado.

## Fase 4 — Ejecución local con el reloj (tú)

Ver [`RUNBOOK.md`](RUNBOOK.md). Resumen: preparar host → backup completo → desbloquear →
flashear boot rooteado → verificar root → deshabilitar Google → instalar Friday+Tailscale
y configurar → prueba de humo → desplegar Hermes + puente de voz.

## Riesgos y reglas no negociables

- Sin backup completo verificado, no se escribe nada en el dispositivo.
- Nunca flashear particiones únicas del aparato (`proinfo`, `nvram`, `nvdata`,
  `persist`, `seccfg`, `frp`) procedentes de otro dispositivo.
- No usar el boot de Ayra si la build no coincide exactamente: usar la opción B.
- Deshabilitar paquetes, no desinstalarlos.
- El token nunca va en el repo: variables de entorno y secrets.
