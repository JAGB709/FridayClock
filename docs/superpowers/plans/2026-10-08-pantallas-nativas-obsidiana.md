# Plan: pantallas nativas Obsidiana (4b)

1. Prueba JVM de la lógica pura del selector de cifra (`Steps.wrap`) y de `Icons`.
2. `ui/Widgets.kt` y `ui/Dialogs.kt`; ampliar `Icons.kt`; tema (`colorAccent`).
3. Reescribir `AlarmsActivity` (lista y edición) con los widgets.
4. Reescribir `SettingsActivity` y `BluetoothActivity`.
5. Restilar `AlarmActivity` sin tocar su lógica.
6. `defaultFocusHighlightEnabled = false` en `MainActivity.iconButton`.
7. `testDebugUnitTest`, `lintDebug`, revisar emojis y colores literales; actualizar README y docs.
