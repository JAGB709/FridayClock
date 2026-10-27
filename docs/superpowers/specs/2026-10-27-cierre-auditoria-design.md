# Cierre de la auditoría (hallazgos pendientes)

Objetivo: cerrar lo que quedaba de la auditoría tras el puente, el panel, la app y el servidor HTTP.

## Alcance y decisiones

1. Panel. Las subidas de sonido y fondo se vuelcan en streaming a un fichero temporal (`SpooledTemporaryFile`, 256 KB en RAM) cortando al pasar el tope real, sin fiarse de `Content-Length`; se reenvían al reloj con `Content-Length` explícito. `/api/bg` lee la respuesta del reloj en streaming y responde 502 si supera `MAX_BG_BYTES`.
2. Docker y CI. Usuario no root (uid 10001), base `python:3.12-slim-bookworm` fijada por digest, `requirements.txt` con versiones exactas, `no-new-privileges` y `cap_drop: ALL` en el puente, Actions fijadas por SHA con comentario de versión y `dependabot.yml`.
   Decisión: versiones exactas de las dependencias directas, no hashes de todo el árbol; se documenta `pip-compile --generate-hashes` como siguiente paso. El volumen de caché de Whisper pasa a `/home/app/.cache` (se vuelve a descargar el modelo una vez).
   Los SHA de las Actions son los de las etiquetas mayores v4/v5 en la fecha del cambio.
3. App.
   - F05 tráfico en claro. Decisión: el `network_security_config` de Android 10 no admite rangos CIDR (solo dominios o IP literales), así que no puede limitar http a Tailscale o a la LAN sin romper las URL con IP. Se mantiene `cleartextTrafficPermitted` y la restricción se aplica en código (`CleartextPolicy`): http solo a localhost, 10/8, 172.16/12, 192.168/16, enlace local, 100.64/10, `*.ts.net`, `*.local` y nombres sin punto; el resto exige https. Se comprueba antes de enviar el token.
   - F07. No se registran transcripciones ni textos de wake word; se elimina el WAV de depuración `wake_N.wav` (y se borran los antiguos al arrancar) y `reply.audio` se borra tras reproducirse.
   - F08. `build.gradle.kts` firma el release con el keystore de `FRIDAY_KEYSTORE*` (entorno o `local.properties`); si falta, cae a la clave debug con un aviso. Guía en `docs/firma-release.md`. No se genera ni se commitea ninguna clave.
4. C14. En Ajustes, los dos tokens salen ocultos con botón Mostrar/Ocultar (cambio mínimo en `SettingsActivity`).
5. device-kit. `ASSUME_YES=1` solo omite `DESHABILITAR` (reversible) y exige `ASSUME_YES_ACK=SI_ASUMO_EL_RIESGO`; mtkclient se clona en el commit de `manifests/mtkclient.commit` (v2.1.4.1, sin probar en hardware) y el preflight lo verifica; `manifests/apks.sha256` opcional para los APK; los tokens van por stdin (`adb shell`, `curl -K -`) y la salida se redacta.
6. Repositorio. `CONTRIBUTING.md`, plantillas de issue y PR, `CODEOWNERS`, `SECURITY.md` actualizado.
7. README. Ollama en `127.0.0.1` por defecto, Hermes en `127.0.0.1:8642`, cortafuegos si se abre, y aviso de `hermes dashboard --insecure`.

## Pruebas

Panel: `tests/test_hardening.py` (subida troceada sin Content-Length, subida grande válida, `/api/bg` con tope). App: `CleartextPolicyTest`. device-kit: `device-kit/tests/test_common.sh`.
