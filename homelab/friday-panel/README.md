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
