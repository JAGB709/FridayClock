# Pantallas nativas del reloj con el sistema Obsidiana (4b)

Estado: implementado · Continúa `2026-10-07-rediseno-fundamentos-design.md`

## Objetivo
Que Alarmas (lista y edición), Ajustes, Altavoz Bluetooth, la alarma sonando y todos los diálogos usen los tokens, Inter e iconos Lucide, igual que la carátula y el panel. Fondo negro, sin emojis, objetivos táctiles de al menos 48 dp y foco de D-pad siempre visible en 800x480.

## Diseño
- `ui/Widgets.kt`: constructores de botones (primario, secundario, discreto, peligro), botón de icono, chips de día, interruptor, campo de texto, deslizador, tarjeta y selector de cifra. Todo con colores de `Tokens` y fuentes de `Fonts`.
- `ui/Dialogs.kt`: diálogo propio (tarjeta Obsidiana) que sustituye a `AlertDialog`.
- Foco: cada control trae su propio estado `focused` (anillo de 3 dp, acento; blanco sobre fondos de acento) y desactiva `defaultFocusHighlightEnabled`, que pintaba un gris por encima. Se aplica también a los botones de barra de `MainActivity` (una línea).
- Alarmas: cada alarma es una tarjeta con hora Light de 40 sp, etiqueta, días, interruptor y papelera (la pulsación larga sigue borrando). Estado vacío con icono.
- Edición: la rueda `NumberPicker` se cambia por un selector de cifra con chevrones (toque: ±1; mantener: repite) porque `NumberPicker` no se puede tematizar sin recursos y no es operable con D-pad de forma clara.
- Ajustes: cabecera fija con atrás y Guardar, secciones como tarjetas, campos con foco de acento. El deslizador de brillo y la regla de brillo no cambian.
- Alarma sonando: misma estructura; botones con esquinas, icono y anillo de foco; el foco inicial va en Descartar. La rampa de brillo (amanecer) no cambia.

## Decisiones
- No se cambia la lógica de alarmas ni sus receivers.
- Errores de validación de alarma como mapa por campo: NO se hace. Cambia el contrato de la API (`Api.kt`, `docs/api.md`, skill, mock y tests del panel) y no es pequeño; queda pendiente en el README.
- No se añaden iconos nuevos (cambiaría `icons.js`, compartido con el motor); se amplía `Icons.kt` con los VectorDrawable ya generados.
- Los `Toast` del sistema se dejan (no son tematizables sin una vista propia).
- El color de acento de la alarma sonando sigue viniendo del tema del diseño guardado.

## Verificación
Pruebas JVM (`WidgetsTest`, lógica pura del selector) y `testDebugUnitTest`/`lintDebug`. Lo visual se comprueba a mano en el reloj.
