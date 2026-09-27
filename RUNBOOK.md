# RUNBOOK — liberar el Mi Smart Clock (Fase 4, con el reloj delante)

Esto lo ejecutas **tú**, con el reloj conectado por **microUSB de datos** al ordenador
(Linux o macOS recomendado; Windows funciona con UsbDk y `NORECONNECT=1`). Los scripts
están en `device-kit/`. Todo va en **DRY_RUN=1 por defecto**: primero pruebas en seco,
y solo cuando estés seguro repites con `DRY_RUN=0`.

> Antes de nada lee `docs/research-notes.md` y `docs/decisions.md`. El plan por defecto
> del boot es la **opción B** (parchear el boot propio), más segura que el boot de Ayra.

## Convenciones

- **Dry-run:** `./device-kit/01_backup_full.sh` (no escribe nada, solo enseña qué haría).
- **Real:** `DRY_RUN=0 ./device-kit/01_backup_full.sh`.
- Los pasos destructivos piden escribir una palabra (p.ej. `DESBLOQUEAR`).
- Cada ejecución deja un log en `device-kit/logs/`.
- Windows: añade `NORECONNECT=1` a cada comando.

## Modo BROM / fastboot (del research)

- **BROM (mtkclient):** conecta el USB **con VOL+ pulsado**; mtkclient lo detecta.
- **fastboot** (tras desbloquear): con el reloj apagado y desconectado, mantén los **tres
  botones de arriba** y conecta; a los 5 s suelta **mute**; en "No command", con mute
  pulsado da a **VOL+** una vez → recovery → "Reboot to bootloader".

## Paso a paso

0. **Preparar el ordenador** (una vez):
   ```bash
   ./device-kit/setup_host.sh          # instala mtkclient/adb/udev (te guía)
   git clone https://github.com/bkerler/mtkclient device-kit/vendor/mtkclient
   pip3 install -r device-kit/vendor/mtkclient/requirements.txt
   ```
   Copia el APK de Friday a `device-kit/vendor/` (o compílalo, ver README) y el APK de
   Tailscale de F-Droid. Define el token del reloj:
   ```bash
   export CLOCK_TOKEN="$(head -c16 /dev/urandom | base64 | tr -dc a-z0-9 | head -c20)"
   echo "Tu token: $CLOCK_TOKEN"   # guárdalo, lo usa Hermes
   ```

1. **Preflight:**
   ```bash
   ./device-kit/00_preflight.sh
   ```

2. **Apunta la build** del reloj: Ajustes del reloj → información, o más tarde
   `adb shell getprop ro.build.fingerprint`. Decide opción A vs B (ver decisions.md).

3. **Backup COMPLETO** (imprescindible; hazlo real):
   ```bash
   DRY_RUN=0 ./device-kit/01_backup_full.sh
   ```
   Copia `device-kit/backups/<fecha>/` a un segundo sitio. El script te obliga a
   confirmar (`COPIADO`) y marca el backup como verificado.

4. **Desbloquear bootloader** (borra userdata):
   ```bash
   DRY_RUN=0 ./device-kit/02_unlock.sh
   ```
   Reconecta; debe salir "Orange State".

5. **Flashear boot rooteado** (opción B por defecto):
   ```bash
   DRY_RUN=0 ./device-kit/03_flash_boot.sh
   # opción A (solo si tu build coincide con la de Ayra):
   # DRY_RUN=0 MODE=A ./device-kit/03_flash_boot.sh
   ```
   Para la opción B, si no tienes `magiskboot`/`boot_patch.sh`: copia el `boot` del
   backup al reloj, párchealo con la **app Magisk** ("Parchear un fichero") y deja el
   resultado en `device-kit/vendor/boot_patched.img`; el script lo flashea.

6. **Verificar root:**
   ```bash
   DRY_RUN=0 ./device-kit/04_verify_root.sh
   ```
   Bootloop → `DRY_RUN=0 LEVEL=2 ./device-kit/99_rollback.sh`.

7. **Deshabilitar Google** (reversible):
   ```bash
   DRY_RUN=0 ./device-kit/05_debloat.sh
   ```
   Reinicia y comprueba que arranca sin la interfaz de Google.

8. **Instalar apps y configurar** (token, backend, launcher):
   ```bash
   DRY_RUN=0 CLOCK_TOKEN="$CLOCK_TOKEN" \
     BACKEND_URL="http://<ip-homelab>:8765" BACKEND_TOKEN="<token puente>" \
     ASSISTANT_NAME="Friday" ./device-kit/06_install_apps.sh
   ```
   Inicia sesión en **Tailscale** en el reloj.

9. **Prueba de humo** (desde el ordenador y desde Madrid por Tailscale):
   ```bash
   DRY_RUN=0 CLOCK_URL="http://<ip-tailscale-reloj>:8080" CLOCK_TOKEN="$CLOCK_TOKEN" \
     ./device-kit/07_smoke_test.sh
   ```

10. **Cerebro en el homelab:** levanta `homelab/clock-bridge` y despliega la skill
    `hermes-skill/` (ver README y `docs/voz-friday.md`). Prueba:
    ```bash
    CLOCK_URL=http://<ip-tailscale-reloj>:8080 CLOCK_TOKEN="$CLOCK_TOKEN" \
      python3 ~/.hermes/skills/mi-smart-clock/scripts/clock.py status
    ```
    Pide a Friday que muestre algo en el reloj y ponga una alarma a +3 min.

## Si algo va mal (rollback)

| Síntoma | Acción |
|---|---|
| Google molesta / algo dejó de ir tras el debloat | `DRY_RUN=0 LEVEL=1 ./device-kit/99_rollback.sh` |
| Bootloop tras flashear boot | `DRY_RUN=0 LEVEL=2 ./device-kit/99_rollback.sh` |
| Desastre total | `DRY_RUN=0 LEVEL=3 ./device-kit/99_rollback.sh` (restaura todo menos las particiones únicas) |

## Reglas de oro

- Sin backup completo **verificado**, no se escribe nada (los scripts lo exigen).
- Nunca flashear `proinfo`, `nvram`, `nvdata`, `persist`, `seccfg`, `frp` de **otro** reloj.
- Deshabilitar paquetes, no desinstalarlos.
- El token nunca en el repo: variable de entorno / Ajustes / ADB.
