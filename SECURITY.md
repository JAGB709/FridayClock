# Seguridad

Friday da root a un reloj y habla con un agente que puede ejecutar comandos. Léelo antes de instalarlo.

## Versiones con soporte

Solo la última versión de `main` recibe correcciones de seguridad.

## Qué proteger

- El reloj acepta órdenes por HTTP (:8080) con un token. Quien tenga ese token controla el reloj. Activa "Escuchar solo en Tailscale" en Ajustes.
- El puente (:8765) lleva al agente Hermes, que tiene terminal. Exige `BRIDGE_TOKEN` de al menos 16 caracteres y no lo expongas a internet.
- Ollama (:11434) y Hermes (:8642) deben escuchar en `127.0.0.1` (o en la red de Docker con cortafuegos). El panel (:8770) y el puente (:8765) solo dentro de tu red o de Tailscale. No abras esos puertos en el router.
- No uses `hermes dashboard --insecure`: quita la autenticación del panel de Hermes.
- Pon contraseña al panel (`PANEL_PASSWORD`).
- Tráfico en claro: el reloj habla con el puente por HTTP. La app solo envía el token por http a localhost, LAN privada, Tailscale (100.64.0.0/10) y `*.ts.net`; a cualquier otro destino exige https. Dentro de Tailscale va cifrado por WireGuard.
- Firma del APK: por defecto el release usa la clave de depuración. Configura tu propio keystore ([docs/firma-release.md](docs/firma-release.md)) o verifica el SHA-256 del APK que instales.
- Privacidad: ni la app ni el puente registran lo que se dice, y la app no guarda copias del audio.
- Los contenedores Docker corren como usuario no root con imágenes y dependencias fijadas.
- `ASSUME_YES=1` en el device-kit solo omite confirmaciones reversibles y exige `ASSUME_YES_ACK=SI_ASUMO_EL_RIESGO`.

## Cómo avisar de un fallo

Abre un aviso privado en la pestaña Security del repositorio (GitHub private vulnerability reporting). No publiques tokens, IP ni capturas de tu casa en issues. Intentaré responder en una semana; es un proyecto personal sin plazos garantizados.

## Alcance conocido

Una voz en la habitación puede dar órdenes al asistente. Las acciones destructivas deberían pedir confirmación; ver el apartado de seguridad del README. El `adb shell` del reloj liberado es root sin autenticación: no lo expongas a una red no confiable.
