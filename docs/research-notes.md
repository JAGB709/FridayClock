# Notas de investigación (Fase 0)

Todo lo de aquí sale de **leer** los tres repos de referencia (clonados el 2026-09-27)
y la documentación oficial. Cada dato lleva su fuente. Lo que no se puede confirmar
sin el reloj delante está marcado como **[verificar en dispositivo]**.

## Dispositivo

- Xiaomi Mi Smart Clock **global X04G** (la china es **LX04**). Mismo hardware; cambia la ROM.
  Fuente: `octathorp/x04g_tools/README.md`.
- SoC **MediaTek MT8167** (4×Cortex-A35), **1 GB RAM**, pantalla táctil **800×480 4"**,
  micro doble, altavoz mono, botones físicos vol+/vol−/mute, microUSB datos.
  Fuente: `octathorp/x04g_tools/README.md`.
- La global viene con **Android Go 10** y Google Assistant. Fuente: ídem.

## Cómo entrar en modo BROM/preloader

- mtkclient espera el dispositivo; **se conecta el USB con vol+ pulsado** y lo detecta.
  Fuente: `octathorp/x04g_tools/howtos/BackupFlash.md` y `AyraHikari/.../0-backup-partition.md`.
- **`--noreconnect` es obligatorio en Windows**, no en Linux/macOS.
  Fuente: `AyraHikari/.../convert-lx04-to-x04g.md`.
- Para entrar en **fastboot** (tras desbloquear): con el equipo desconectado, pulsar los
  tres botones de arriba para reconectar; a los 5 s soltar **mute** dejando los otros dos
  hasta "No command"; mantener mute y pulsar vol+ una vez → menú recovery → "Reboot to bootloader".
  Fuente: `octathorp/.../howtos/FlashCustomImages.md`.

## mtkclient (versión actual)

- Repo `bkerler/mtkclient`, commit clonado `cd25cf9` (2026-09-12). Sintaxis vigente
  confirmada leyendo `mtk.py` (subcomandos en `common_cmds`):
  - `python mtk.py rl <dir>` — leer **todas** las particiones a un directorio (backup).
  - `python mtk.py r <part> <fichero>` — leer una partición (`--offset`, `--length`).
  - `python mtk.py w <part> <fichero>` — escribir una partición (`--gpt_file` opcional).
  - `python mtk.py wl <dir>` / `wf <img>` — escribir por lote / imagen completa.
  - `python mtk.py e <part>` — borrar particiones.
  - `python mtk.py printgpt` — tabla de particiones.
  - `python mtk.py da seccfg unlock` — desbloquear bootloader (`da seccfg lock` revierte).
  - `python mtk.py reset` — reset.
  Fuente: `bkerler/mtkclient/mtk.py` (líneas de `add_subparsers`/`common_cmds` y `da_subs`).
- Dependencias: Python, libusb; en Linux **reglas udev**; en Windows **UsbDk**.
  Fuente: README de mtkclient. `vbmeta.img.empty` vive en la raíz de mtkclient.
  Fuente: `octathorp/.../FlashCustomImages.md`.

## Tabla de particiones X04G (de `gpt_x04g.bin` de Ayra)

Leída con un parser de GPT sobre `AyraHikari/.../files/gpt_x04g.bin` (128 entradas, 128 B c/u).
Particiones y tamaño:

| Partición | Tamaño | Notas |
|---|---|---|
| proinfo | 3 MiB | **única del dispositivo** (IDs) |
| nvram | 5 MiB | **única** (calibración RF) |
| persist | 48 MiB | **única** (sensores, MAC en algunos) |
| seccfg | 0.25 MiB | estado de bloqueo (unlock aquí) |
| lk, lk2 | 0.5 MiB | bootloader |
| **boot** | 16 MiB | kernel + ramdisk (aquí va Magisk) |
| recovery | 16 MiB | |
| logo, dtbo, expdb, frp | | |
| tee1, tee2 | 5 MiB | |
| nvdata | 4 MiB | **única** (datos de calibración) |
| vbmeta, vbmeta_system, vbmeta_vendor | 4 MiB | verificación AVB |
| super | 1536 MiB | lógicas: system/vendor/product (GSI aquí) |
| metadata, cache | | |
| userdata | ~1916 MiB | |

**Nunca** flashear desde otro dispositivo: `proinfo`, `nvram`, `nvdata`, `persist`,
`seccfg`, `frp` (contienen IDs, MAC y calibración). Restauración solo desde el
backup del **propio** reloj. Fuente: cruce de `gpt_x04g.bin` + guías de Ayra
(paso 8 restaura `nvram`/`proinfo` propios) + práctica MTK habitual.

## `boot.img` de Ayra: `boot_global_magisk.bin`

Analizado el header Android y la ramdisk del fichero
`AyraHikari/.../files/boot_global_magisk.bin`:

- `sha256 = cadd9bc1308fa03f60d63a89d439a0e191319061b4c29f5f1a4389a9b3110a26`
- Header boot **v2**, page 2048, **os_version 10.0.0**, **security patch 2021-06**,
  cmdline `bootopt=64S3,32N2,32N2 buildvariant=user`.
- Ramdisk **parcheada con Magisk**: contiene `.backup/.magisk`
  (`KEEPVERITY=false`, `KEEPFORCEENCRYPT=false`, `PATCHVBMETAFLAG=false`),
  `overlay.d/sbin/magisk32.xz`, y un `overlay.d/init.adb.rc` que:
  - pone `setenforce 0` (SELinux permisivo),
  - desactiva la autenticación de ADB (`ro.secure 0`, `ro.adb.secure 0`, `ro.debuggable 1`),
  - **arranca ADB automáticamente** y abre un **root shell por netcat en el puerto 1337**
    (`rootshell.sh`: `toybox netcat -s 0.0.0.0 -p 1337 -L /system/bin/sh`).
- El fstab de la ramdisk quita `avb` de system/vendor/product y `fileencryption` de userdata.

⚠️ **Riesgos de este boot de Ayra**:
1. Está hecho para una **build con parche de seguridad 2021-06**. Si tu reloj trae otra
   build, el kernel puede no casar → cuelgue. **[verificar en dispositivo]** la build real
   antes de decidir (Ajustes del reloj / `getprop ro.build.fingerprint`).
2. Abre un **root shell sin contraseña en la red** (puerto 1337) y desactiva la
   autenticación ADB. Para un reloj expuesto solo por Tailscale es asumible, pero
   conviene **bloquear el 1337** o rehacer el boot sin ese `rootshell` (opción B).

Por estos dos motivos, el plan por defecto es la **opción B** (parchear el boot propio).

## Root, deshabitar Google

- Guía de Ayra para rootear: desbloquear bootloader → flashear `boot_global_magisk.bin`
  a `boot` → reiniciar. Fuente: `AyraHikari/.../root-adb-debug.md`.
- Desbloqueo alternativo (octathorp): `python mtk e metadata,userdata,md_udc` y luego
  `python mtk da seccfg unlock` + `python mtk reset`; aparece "Orange State".
  Fuente: `octathorp/.../FlashCustomImages.md`. (Borra userdata: hacerlo **antes** de configurar.)
- Google/Assistant se **deshabilitan** con `pm disable-user --user 0 <paquete>`
  (reversible), nunca `pm uninstall`. Los paquetes concretos se **descubren en el
  dispositivo** con `pm list packages | grep -Ei 'google|gms|assistant|velvet'`
  **[verificar en dispositivo]**.

## GSI (opcional)

- Se puede instalar GSI (Treble, Android 10). Hay que **meter el system.img dentro de
  super** (las lógicas no se exponen por fastboot) y **desactivar SELinux** en el cmdline
  del boot: cambiar `buildvariant=user` por
  `buildvariant=userdebug enforcing=0 androidboot.selinux=permissive`.
  Fuente: `octathorp/.../WorkingWithGSI.md`. Scripts `unpack_gsi_super.sh` / `repack_gsi_super.sh`.
- Para el objetivo (quitar Google) **no hace falta GSI**: basta rootear la ROM global y
  deshabilitar los paquetes de Google. GSI queda como opción avanzada.

## Tailscale en Android 10

- La app de Tailscale requiere **Android 8.0+**, así que el X04G (Android 10) **es compatible**.
  APK desde F-Droid (`com.tailscale.ipn`, build reciente sep-2026) o el APK oficial.
  Fuente: [F-Droid](https://f-droid.org/packages/com.tailscale.ipn/),
  [Tailscale Docs](https://tailscale.com/docs/install/android). Sin bloqueo.

## Hermes: tools/skills y voz

- Hermes expone API **OpenAI-compatible** en `127.0.0.1:8642` (`API_SERVER_ENABLED=true`,
  `API_SERVER_KEY`). Usamos `POST /v1/responses` con `conversation` (conversación con
  nombre) e `instructions` (persona). Fuente: `NousResearch/hermes-agent`
  `website/docs/user-guide/features/api-server.md`.
- Las **skills** son carpetas en `~/.hermes/skills/<nombre>/SKILL.md` (formato agentskills.io,
  con `references/`, `scripts/`…). Cada skill es también un slash-command. Requieren toolsets
  (p.ej. `terminal`). Fuente: `.../features/skills.md`.
- **Voz de Hermes** (para otros canales, p.ej. notas de voz de Telegram):
  - STT: `stt.provider` = `nous` (Tool Gateway) u `openai` (Whisper, `VOICE_TOOLS_OPENAI_KEY`).
  - TTS: `tts.provider` = `nous` (voces OpenAI vía Gateway) o BYOK (ElevenLabs…).
  - Fuente: `.../features/tool-gateway.md`, `.../messaging/telegram.md`, `.../configuration.md`.
- **Voz del reloj**: la resolvemos en el **homelab** con Whisper (STT) y Piper (TTS)
  **locales**, sin depender del Gateway de Nous, a través de `homelab/clock-bridge`.

## Preguntas abiertas (verificar con el reloj)

1. Build/fingerprint exacta instalada → decide opción A vs B del boot.
2. Lista real de paquetes Google a deshabilitar.
3. Si el desbloqueo pide o no tocar `vbmeta` en esta unidad (usar `vbmeta.img.empty`).
