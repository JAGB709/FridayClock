# Seguridad

Friday da root a un reloj y habla con un agente que puede ejecutar comandos. Léelo antes de instalarlo.

## Qué proteger

- El reloj acepta órdenes por HTTP (:8080) con un token. Quien tenga ese token controla el reloj.
- El puente (:8765) lleva al agente Hermes, que tiene terminal. Exige `BRIDGE_TOKEN` de al menos 16 caracteres y no lo expongas a internet.
- Ollama (:11434), Hermes (:8642), el panel (:8770) y el puente (:8765) deben quedar dentro de tu red o de Tailscale. No abras esos puertos en el router.
- Pon contraseña al panel (`PANEL_PASSWORD`).
- El APK de release va firmado con la clave de depuración. Instala solo APK construidos por ti o verifica su SHA-256 publicado en la release.

## Cómo avisar de un fallo

Abre un aviso privado en la pestaña Security del repositorio (GitHub private vulnerability reporting). No publiques tokens, IP ni capturas de tu casa en issues.

## Alcance conocido

Una voz en la habitación puede dar órdenes al asistente. Las acciones destructivas deberían pedir confirmación; ver el apartado de seguridad del README.
