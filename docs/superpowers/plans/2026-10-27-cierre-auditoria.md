# Plan: cierre de la auditoría

Ver el spec `docs/superpowers/specs/2026-10-27-cierre-auditoria-design.md`. Un commit pequeño por bloque:

1. Panel: pruebas rojas de subida troceada y `/api/bg`, luego `spool_body` y lectura acotada.
2. Docker, requirements, Actions por SHA y dependabot.
3. App: `CleartextPolicyTest` rojo, `CleartextPolicy`, logs y caché de audio, firma de release y doc.
4. Ajustes: tokens ocultos con Mostrar/Ocultar.
5. device-kit: `test_common.sh` rojo, `ASSUME_YES` restringido, mtkclient fijado, checksums, tokens por stdin.
6. CONTRIBUTING, plantillas, CODEOWNERS, SECURITY, README.

Verificación: pytest del panel, skill y puente; `./gradlew testDebugUnitTest assembleRelease`; node y `tools/sync-face.sh --check`; `bash device-kit/tests/test_common.sh`.
