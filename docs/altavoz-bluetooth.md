# Usar el reloj como altavoz Bluetooth del móvil

Objetivo: emparejar tu móvil con el reloj y que la música del móvil **suene por el
altavoz del reloj**. Le dices a Friday *"activa el bluetooth"* y el reloj se pone en
modo altavoz y visible para emparejar.

## Cómo se usa

- **Por voz / Hermes:** "Friday, activa el bluetooth" o "modo altavoz". También
  `python3 ~/.hermes/skills/mi-smart-clock/scripts/clock.py bluetooth speaker`.
- **En el reloj:** Ajustes → *Modo altavoz Bluetooth* → "Activar modo altavoz".
- **Por API:** `POST /bluetooth {"action":"speaker"}` (o `on` / `off` / `status`).

Luego, en el **móvil**: Ajustes → Bluetooth → busca **"Friday"** y empareja. La música
que reproduzcas en el móvil saldrá por el reloj.

## Lo que hace la app (y lo que depende de tu ROM) — importante

La app se encarga de la parte que le corresponde:
- **Enciende** el Bluetooth.
- Pone el reloj **visible** para emparejar (si la app tiene privilegios de sistema lo
  hace sin diálogo; si no, muestra el diálogo del sistema "hacer visible", un toque).
- Muestra el **estado**: encendido, visible, emparejados y si hay un móvil conectado.

Pero que el audio del móvil **realmente salga por el altavoz** depende de que el sistema
del reloj tenga activado el **perfil A2DP en rol _sink_** (receptor). Android de fábrica
actúa como _source_ (emisor), no como _sink_. Esto **no lo puede forzar una app normal**:
es cosa del ROM/GSI. Por eso, tras liberar el reloj (root), hay que comprobarlo/activarlo.

### Comprobar si tu GSI soporta A2DP sink

Con el reloj rooteado y por ADB:

```bash
adb shell getprop | grep -iE "a2dp|bluetooth.profile"
```

Busca algo como `persist.bluetooth.a2dp_sink.enabled` o
`bluetooth.profile.a2dp.sink.enabled`.

### Activarlo (rooteado)

Prueba en caliente (se pierde al reiniciar):

```bash
adb shell su -c 'resetprop persist.bluetooth.a2dp_sink.enabled true'
adb shell su -c 'resetprop bluetooth.profile.a2dp.sink.enabled true'
adb shell su -c 'svc bluetooth disable; svc bluetooth enable'
```

Para que persista, añade esas líneas al `build.prop` o, mejor con Magisk, a
`/data/adb/modules/<tu-modulo>/system.prop` (o usa un módulo `resetprop` en
`post-fs-data`). Luego reinicia y vuelve a probar el emparejamiento.

### Si tu GSI no trae el sink

No todas las GSI incluyen el enrutado de audio A2DP sink. Alternativas:

1. **Otra GSI** que sí lo traiga (algunas builds de LineageOS/AOSP con
   `bluetooth.profile.a2dp.sink.enabled`).
2. Una app receptora de **A2DP Sink** de F-Droid que implemente el rol, si tu HAL lo
   permite.
3. Como plan B para "escuchar música con Friday" sin depender del sink, más adelante se
   puede añadir el reproductor de **URL/radio** (endpoint `/play`) que pediste como tercera
   opción: reproduce streams directos por el altavoz sin Bluetooth ni cuentas.

> Estado: la integración en la app y en Hermes está hecha y probada (encender, visibilidad,
> estado, emparejar). El enrutado del audio queda **por verificar en tu dispositivo** según
> soporte tu GSI el A2DP sink; está documentado arriba cómo activarlo.
