# Contribuir a Friday

Gracias por ayudar. Esto es un proyecto personal para un único modelo de reloj, así que se agradecen
cambios pequeños y bien probados.

## Antes de empezar

- Lee `README.md` (instalación y arquitectura) y `CLAUDE.md` (convenciones y comandos de prueba).
- Textos de usuario, comentarios y documentación en español, sin emojis. La interfaz usa los tokens
  de `design-system/` (nada de colores literales; `tests/test_design_system.py` lo comprueba).
- Para algo más que una corrección menor, abre antes un issue para acordar el enfoque.

## Flujo

1. Crea una rama desde `main` y haz commits pequeños en español.
2. Escribe primero la prueba cuando haya código (TDD) y comprueba que pasan **todas** las de lo que toques:
   - App: `./gradlew testDebugUnitTest`
   - Skill: `cd hermes-skill && pytest -q`
   - Panel: `cd homelab/friday-panel && pytest -q`
   - Puente: `cd homelab/clock-bridge && pytest -q`
   - Motor de pantallas: `node --test app-android/face-tests/*.test.js homelab/friday-panel/tests-js/*.test.js` y `bash tools/sync-face.sh --check`
   - device-kit: `bash device-kit/tests/test_common.sh`
3. Abre un pull request rellenando la plantilla. La CI debe pasar.

## Reglas de seguridad

- No subas tokens, `.env`, keystores, APK ni volcados del reloj.
- Los fallos de seguridad se comunican en privado según [SECURITY.md](SECURITY.md).
- Las dependencias se fijan a versiones exactas y las Actions de CI por SHA con comentario de versión.
