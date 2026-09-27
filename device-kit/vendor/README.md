# vendor/ — binarios de terceros (no van en git)

Aquí colocas herramientas y ficheros que **no** se versionan (ver `.gitignore`),
por licencia y por contener a veces IDs. `00_preflight.sh` verifica sus checksums
contra `../manifests/checksums.sha256`.

- `mtkclient/` — `git clone https://github.com/bkerler/mtkclient`
- `magisk/boot_patch.sh` (+ `magiskboot`) — de la app/paquete Magisk, para la opción B.
- Opción A (solo si tu build coincide): `boot_global_magisk.bin` del repo de Ayra.
- APKs: `friday-*.apk` (compilado del proyecto) y `tailscale-*.apk` (F-Droid).
