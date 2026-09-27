# Decisiones (Fase 0)

## D1 — boot.img: opción B (parchear el propio) por defecto

- **A)** usar `boot_global_magisk.bin` de Ayra solo si la build del reloj coincide con la
  suya (parche **2021-06**, confirmado en el header). Rápido, pero incluye un **root shell
  sin contraseña en el puerto 1337** y desactiva la autenticación ADB.
- **B)** volcar el `boot` propio del reloj (Fase 4) y parchearlo con Magisk
  (`magiskboot`/`boot_patch.sh`) en el ordenador. Sin sorpresas de build ni backdoor de red.

**Decisión: B es el plan por defecto.** Se preparan herramientas para ambas. Se usa A solo si
en la Fase 4 se confirma que la build coincide **y** se acepta (o se corta) el shell del 1337.

## D2 — Sin GSI para el objetivo principal

Para quitar Google basta rootear la ROM global y `pm disable-user` los paquetes de Google.
GSI (con super + SELinux permisivo) queda documentado como opción avanzada, no en el camino feliz.

## D3 — Deshabilitar, no desinstalar

`pm disable-user --user 0`. Reversible desde `logs/disabled_packages.txt`. Nunca `pm uninstall`.

## D4 — Voz del reloj en local (Whisper + Piper), Hermes como cerebro

El reloj no habla con Google ni con la nube de voz: el `clock-bridge` del homelab hace STT
(Whisper) y TTS (Piper) en local y pasa el texto a Hermes. La voz de Hermes en otros canales
(Telegram, etc.) puede ir por Nous o BYOK; es independiente.

## D5 — El asistente se llama Friday

Persona por defecto "Friday" (mujer, española), configurable en Ajustes del reloj y en las
`instructions` que el puente manda a Hermes.

## D6 — Tailscale sin bloqueo

Android 10 ≥ 8.0, compatible. Sin alternativa necesaria.

## D7 — targetSdk

La app es solo para este reloj (Android 10). Se mantiene `compileSdk 34` pero se declara
`FOREGROUND_SERVICE_SPECIAL_USE` con su `<property>` (ya presente). `minSdk 26`.
