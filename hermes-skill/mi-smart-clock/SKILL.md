---
name: mi-smart-clock
description: Controla el reloj despertador Mi Smart Clock de la mesilla
version: 1.0.0
platforms: [linux, macos]
metadata:
  hermes:
    tags: [home, alarm, clock, voice]
    category: smart-home
    requires_toolsets: [terminal]
---

# Mi Smart Clock

Reloj despertador de la mesilla (Xiaomi Mi Smart Clock, app Friday, sin Google).
Tiene pantalla táctil pequeña, altavoz y micro. Se controla con `scripts/clock.py`
(ruta relativa a esta skill); cada comando imprime JSON con `"ok": true|false`.

## When to Use
- El usuario pide alarmas, despertarse, temporizadores o recordatorios con hora.
- Hay que avisar al usuario en casa: decir algo en voz alta o mostrarlo en la pantalla.
- Preguntan qué alarmas tiene o si el reloj está sonando.
- "Activa el bluetooth" / "modo altavoz": pon el reloj a recibir el audio del móvil.
- Rutinas programadas (p.ej. un cron que muestra la agenda al despertar).

## Procedure
1. Consulta el estado si hace falta contexto: `python3 scripts/clock.py status`
2. Alarmas (hora 24h `HH:MM`):
   - Crear: `python3 scripts/clock.py alarm-set 07:30 --label "Trabajo" --days weekdays`
     (`--days`: vacío = una vez, `weekdays`, `weekend`, `daily`, o `mon,wed,fri`)
   - Borrar: `alarm-cancel --id 3` | `alarm-cancel --time 07:30` | `alarm-cancel --all`
   - Activar/desactivar: `alarm-enable 3` / `alarm-disable 3`
   - Si suena: `alarm-stop` o `alarm-snooze`
3. Temporizadores: `timer --minutes 10 --label "Pasta"`; cancelar: `timer-cancel`
4. Voz: `say "Te ha llegado un paquete"` (frases cortas, sin markdown).
5. Pantalla: `show "Reunión a las 10" --seconds 30`;
   HTML: `show "<h1 style='color:white'>Hola</h1>" --type html`;
   imagen: `show https://.../img.png --type image`; limpiar: `show-clear`
6. Altavoz Bluetooth: `bluetooth speaker` enciende el BT del reloj y lo hace visible
   para que el usuario empareje su móvil y suene por el reloj; `bluetooth off` lo apaga;
   `bluetooth status` informa. Tras `speaker`, dile al usuario que busque "Friday" en el
   Bluetooth de su móvil y acepte el emparejamiento.
7. Otros: `volume 40`, `brightness 20`, `briefing` (buenos días hablado), `listen` (abre el micro).
7. Confirma al usuario lo que ha quedado programado usando el JSON devuelto (campo `next`
   es epoch ms del próximo disparo).

## Pitfalls
- `reloj inalcanzable`: el reloj está apagado o fuera de Tailscale. Dilo; no reintentes en bucle.
- `401 token inválido`: CLOCK_TOKEN no coincide con Ajustes > Token del reloj.
- Las horas son siempre 24h y en la zona horaria del reloj; convierte "7 de la tarde" a 19:00.
- Por la noche (modo noche) evita `say` salvo que sea urgente o lo pidan.
- Si la petición llega desde el propio reloj (conversación `mi-smart-clock-*`), responde
  en texto breve para voz: el reloj lee tu respuesta en voz alta.

## Verification
`python3 scripts/clock.py alarms` debe listar la alarma creada con `"enabled": true`.
