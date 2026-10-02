# friday-panel

Panel web del despertador Friday, para usar desde el móvil por Tailscale. Ver y
editar alarmas (con sonido, volumen, subida, posponer y buenos días propios), cambiar
ajustes y acciones rápidas. Los cambios se aplican en el reloj al pulsar **Guardar**.

    cp .env.example .env      # rellena CLOCK_URL, CLOCK_TOKEN y PANEL_BIND
    docker compose up -d --build
    curl http://$PANEL_BIND:8770/health    # {"ok":true,"clock":true}

En el móvil (con Tailscale activo) abre `http://<ip-tailscale-homelab>:8770` y
"Añadir a pantalla de inicio" para tenerlo como app.

Sin Docker: `pip install -r requirements.txt && python app.py` (lee el entorno).
Tests: `pip install pytest && pytest -q` (usan el reloj de mentira de `hermes-skill`).
