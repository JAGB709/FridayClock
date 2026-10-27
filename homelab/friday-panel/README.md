# friday-panel

Panel web del despertador Friday, para usar desde el móvil por Tailscale. Ver y
editar alarmas (con sonido, volumen, subida, posponer y buenos días propios), cambiar
ajustes, editor de **Pantallas** (temas, pantallas en rejilla, perfiles por hora y pantalla de alarma) con vista previa exacta y prueba de 30 s en el reloj, y acciones rápidas. Los cambios se aplican en el reloj al pulsar **Guardar**.

Variables de entorno (`.env`):

| Variable | Valor |
|---|---|
| `CLOCK_URL` | obligatoria, `http://100.x.y.z:8080` (IP Tailscale del reloj) |
| `CLOCK_TOKEN` | obligatoria, el token del reloj |
| `PANEL_BIND` | por defecto `127.0.0.1`; pon la IP de Tailscale del homelab (`tailscale ip -4`) |
| `PANEL_PORT` | `8770` |
| `PANEL_PASSWORD` | opcional; si la pones, el panel pide contraseña |
| `PANEL_ALLOWED_HOSTS` | opcional; nombres de Host aceptados, separados por comas (frena el DNS rebinding) |
| `PANEL_COOKIE_SECURE` | `1` si lo sirves por HTTPS (Tailscale Serve) |
| `BRIDGE_URL` | opcional, activa los recordatorios: `http://127.0.0.1:8765` (puente en el mismo equipo) |
| `BRIDGE_TOKEN` | opcional, el `BRIDGE_TOKEN` del puente; el navegador nunca lo ve. Ver [`docs/recordatorios.md`](../../docs/recordatorios.md) |
| `CLOCK_TIMEOUT_S` | `5` |

**Nota:** El motor de la vista previa (`static/face/`) es una copia del del APK: tras cambiar `app-android/src/main/assets/face/` ejecuta `bash tools/sync-face.sh`.

Crea el `.env` (rellena el token y las IPs):

    cat > .env <<'FIN'
    CLOCK_URL=http://100.x.y.z:8080
    CLOCK_TOKEN=
    PANEL_BIND=100.a.b.c
    PANEL_PORT=8770
    PANEL_PASSWORD=
    CLOCK_TIMEOUT_S=5
    FIN

    docker compose up -d --build
    curl http://<ip-tailscale-homelab>:8770/health    # {"ok":true,"clock":true}

En el móvil (con Tailscale activo) abre `http://<ip-tailscale-homelab>:8770` y
"Añadir a pantalla de inicio" para tenerlo como app.

Sin Docker: `pip install -r requirements.txt && python app.py` (lee el entorno).
Tests: `pip install pytest && pytest -q` (usan el reloj de mentira de `hermes-skill`).


## Estructura del panel

Es una aplicación de móvil primero con cuatro pestañas, todas con iconos Lucide y el sistema de diseño Obsidiana (tokens e Inter compartidos con el reloj, ver [`docs/sistema-de-diseno.md`](../../docs/sistema-de-diseno.md)):

- **Inicio**: la tarjeta del reloj en vivo, con la misma vista que muestra el reloj ahora mismo; cuando una alarma está sonando, una tarjeta propia con **Apagar** y **Posponer**; la tarjeta de la próxima alarma (hora, nombre y días; al tocarla se abre en el editor de alarmas); y las acciones rápidas: probar la alarma, el "buenos días" y el campo **Decir** para que el reloj hable.
- **Alarmas**: lista de tarjetas con interruptor, un botón flotante **+** para crear y, al crear o editar, una rueda de hora (`static/wheel-core.js`) para elegir la hora con el dedo.
- **Pantallas**: el editor del diseño con la vista previa fija arriba, que sigue visible mientras se edita, y cuatro secciones debajo (Elementos, Tema, Perfiles y Alarma).
- **Ajustes**: ajustes del reloj agrupados en tarjetas, estado de la conexión y sonidos subidos.

Los componentes comunes (pestañas, tarjetas, interruptores, avisos) están en `static/ui.js` y usan solo los tokens de `tokens.css`; en `styles.css` no hay más colores literales que el negro de la vista previa y de las sombras.
