# Plan: elementos conectados entre sí (3b)

Spec: `docs/superpowers/specs/2026-10-27-elementos-conectados-design.md`. Todo aditivo para facilitar la fusión.

1. Motor: `anchorPos` y `resolveAnchors` en `face-core.js` con pruebas en `app-android/face-tests/core.test.js`; `face.js` dibuja la lista resuelta y añade `data-group` y `data-anchor`.
2. Validación: `group` y `anchor` en `DesignSchema.kt` (`validateLinks`) con pruebas en `DesignSchemaTest.kt`.
3. Editor: funciones puras en `editor-core.js` con pruebas en `tests-js/editor-core.test.js`.
4. Panel: `pantallas.js` usa `applyResult`, `moveGroup` y `resizeWithAnchors`; bloque de relaciones en las opciones del elemento; estilos con tokens en `styles.css`.
5. `bash tools/sync-face.sh` y `--check`; pruebas node, pytest del panel y `testDebugUnitTest`.
6. Documentación: `docs/pantallas-y-temas.md` y una línea en `CLAUDE.md`.
