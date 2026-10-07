# Fotos ajustables, carrusel, arrastrar y deslizar — subproyecto 3c

Fecha: 2026-10-07 · Estado: pendiente de revisión · Continúa `2026-10-06-pantallas-temas-design.md` (3a)

## Contexto y objetivo

Con el motor de pantallas (3a) ya en el reloj, el uso real ha pedido cuatro mejoras:

1. **Fotos sin restricciones de tamaño y ajustables** (hoy se rechazan a más de 2048 px o 2 MB y solo
   se pueden poner centradas y recortadas).
2. **Arrastrar los elementos** en el editor web (hoy solo se les puede cambiar el tamaño).
3. **Carrusel de fotos** como elemento de la carátula.
4. **Deslizar la carátula del reloj**: de izquierda a derecha abre Alarmas; de derecha a izquierda,
   Ajustes.

Es el subproyecto 3c. El **rediseño visual completo** (sistema de diseño, iconos SVG, sin emojis,
modo noche, pantallas nuevas de cronómetro y tarjetas) es el subproyecto 4 y se hace **después**; este
documento no lo adelanta, solo usa la interfaz actual.

## Decisiones

- **Las fotos se reducen en dos sitios:** el navegador las reduce antes de subirlas (lado largo de
  1600 px) y el reloj vuelve a reducirlas al guardarlas, por si llega una más grande. Así el límite por
  tamaño de imagen desaparece y solo queda el del peso de lo que viaja por la red (8 MB).
- **Un mismo ajuste de foto** (`fit`, `zoom`, `pos_x`, `pos_y`) sirve para el fondo de un tema y para el
  carrusel, con vista previa en vivo.
- **Arrastrar = mover el elemento a otra casilla** de la rejilla (con ajuste a casillas); la esquina azul
  sigue cambiando el tamaño. Si el destino está ocupado o fuera de la rejilla, el elemento vuelve a su
  sitio y se avisa.
- **Carrusel = un tipo de elemento nuevo, `photos`**, que ocupa la caja que se quiera de la rejilla. Con
  una sola foto es un marco fijo.
- **Deslizar:** el gesto horizontal pasa a ser de los menús. Cambiar de pantalla del perfil se hace
  **deslizando hacia arriba o hacia abajo** (o con la rotación automática de siempre).
- Se mantienen: doble toque para hablar, pulsación larga para Ajustes, carátula nativa de respaldo.
- UI en español.

## Fuera de alcance

Formato HEIC (el navegador del móvil lo convierte a JPEG al subir); ajuste distinto por cada foto dentro
de un carrusel; pies de foto; vídeo; el rediseño visual (subproyecto 4).

## Modelo de datos (versión 1 del diseño, ampliada de forma compatible)

### Ajuste de foto

Campos opcionales, válidos en `themes.<id>.bg` cuando `type` es `image` y en los elementos `photos`:

| Campo | Valores | Por defecto |
|---|---|---|
| `fit` | `cover` (rellenar, recorta lo que sobra), `contain` (entera, con bandas), `fill` (estirar) | `cover` |
| `zoom` | número de 1 a 3 | 1 |
| `pos_x`, `pos_y` | entero de 0 a 100 (qué punto de la foto queda en el centro de la caja) | 50 |

Los diseños que ya existen siguen siendo válidos y se ven igual (los valores por defecto equivalen a
`center / cover`).

### Elemento `photos`

| Campo | Valores |
|---|---|
| `images` | lista de 1 a 20 nombres de foto de la biblioteca (mismo formato que `bg.image`) |
| `interval_s` | entero de 3 a 600 (por defecto 10) |
| `transition` | `fade` o `none` (por defecto `fade`) |
| `order` | `sequential` o `random` (por defecto `sequential`) |
| `fit`, `zoom`, `pos_x`, `pos_y` | como arriba |

Además de los campos comunes de elemento (`id`, `x`, `y`, `w`, `h`). `color` y `align` no se usan. Como
cada foto reducida ocupa unos 6 MB al dibujarse, **como máximo 2 elementos `photos` por pantalla** y 1 en
la pantalla de alarma. Una foto de la lista que ya no existe se salta; si no queda ninguna, se ve el color
del tema.

Todo se valida como hasta ahora (todo o nada, con la ruta exacta del error): rangos, tipos, límite de
elementos, rejilla y solapes.

### Biblioteca de fotos

Se mantiene la carpeta y la API de `/backgrounds`. Cambios:

- Se aceptan `jpg`, `jpeg`, `png` y `webp` de hasta **8 MB** (el límite de cuerpo del servidor del reloj);
  se elimina el rechazo por dimensiones y el de 2 MB.
- Al guardar, el reloj decodifica con submuestreo y **reduce a un lado largo máximo de 1600 px**,
  volviendo a codificar en el mismo formato (JPEG con calidad 85, PNG, WebP). Una imagen que no se puede
  decodificar se rechaza con un mensaje claro.
- **Máximo 60 fotos** en la biblioteca (aviso claro al llegar).

## Motor `face/`

- **Capa de foto común** (`makePhotoLayer`): un `div` con la imagen como fondo, dentro de una caja con
  `overflow: hidden`. `fit` decide el tamaño de fondo, `pos_x`/`pos_y` la posición y `zoom` una escala
  desde ese mismo punto. La función que calcula los estilos (`photoStyle`) es pura y se prueba en node.
- **Fondo de tema con foto:** la capa de foto se dibuja detrás de los elementos; el color de respaldo
  sigue siendo el fondo de la raíz.
- **Elemento `photos`:** dos capas que se alternan con fundido de 600 ms (o corte directo); la siguiente foto
  se precarga antes de mostrarse y, si falla, se salta. Con `order: random` no se repite la foto inmediata.
  Los temporizadores se cancelan al redibujar la pantalla o cambiar de pantalla.
- **Deslizar:** `gestureUp` distingue el eje. Horizontal (más de 60 px y dominante): llama al puente
  `Native.swipe("right")` (de izquierda a derecha) o `Native.swipe("left")`. Vertical (más de 60 px):
  cambia de pantalla del perfil como el deslizar de lado actual.
- **Modo edición — mover:** pulsar y arrastrar un elemento lo selecciona y lo mueve; mientras se arrastra se
  ve un contorno que salta de casilla en casilla; al soltar envía `{type:"face-move", id, x, y}`
  (limitado a la rejilla). Una pulsación sin movimiento solo selecciona, como hoy.

## Reloj (`app-android`)

- `Backgrounds.save`: nueva lógica de reducción (submuestreo con `BitmapFactory`, recodificación) y
  límite de 60 fotos; `MAX_BYTES` sube a 8 MB; se retira `MAX_SIDE` como rechazo (pasa a ser el lado al
  que se reduce, 1600).
- `DesignSchema`: campos de ajuste de foto, tipo `photos` y su límite por pantalla.
- `FaceHost`: nuevo método del puente `swipe(direction)` y `Listener.onSwipe(direction)`.
- `MainActivity`: `onSwipe("right")` abre `AlarmsActivity`, `onSwipe("left")` abre `SettingsActivity`; el
  deslizar en la **carátula nativa de respaldo** hace lo mismo con `onFling`, para que se comporte igual si
  el WebView falla. En `AlarmActivity` el deslizar no hace nada.
- Sin cambios de API HTTP más allá de los límites de fondos; el README y la skill reflejan los nuevos
  límites y el elemento `photos`.

## Panel (`homelab/friday-panel`)

- **Subida:** antes de enviar, el navegador reduce la imagen (lienzo, lado largo 1600 px, mismo formato,
  calidad 0,85); si no puede decodificarla, la envía tal cual si pesa menos de 8 MB. Se retira el aviso de
  "supera 2 MB". El panel acepta hasta 8 MB.
- **Ajustar foto:** bloque con selector de encaje, deslizador de zoom (1 a 3, de 0,1 en 0,1) y dos
  deslizadores de posición X e Y, con la vista previa en vivo. Se usa en el fondo de tema y en el elemento
  `photos`.
- **Elemento Fotos:** rejilla de miniaturas de la biblioteca con casilla para elegir las fotos del
  carrusel, intervalo, transición, orden y el bloque de ajuste; botón para subir fotos desde ahí.
- **Mover:** al recibir `face-move`, valida con `EditorCore` (rejilla y solapes) y aplica o revierte con
  aviso; los campos X, Y, W, H se mantienen.
- El elemento nuevo se añade a la lista de tipos del editor (`EditorCore.TYPES`).

## Errores y casos límite

- Foto que no se puede decodificar o supera 8 MB: rechazo con mensaje claro; no cambia nada.
- Biblioteca con 60 fotos: aviso al subir.
- Foto borrada que usa un tema o un carrusel: el tema cae a su color de respaldo; el carrusel la salta.
- Carrusel con todas las fotos borradas: se ve el color del tema; la web lo marca con un aviso.
- Deslizar durante la rotación automática: reinicia su cuenta atrás; con una sola pantalla en el perfil, el
  gesto vertical no hace nada.
- Si el WebView falla, la carátula nativa responde al deslizar igual que el motor.
- Memoria: la medición en el reloj real (objetivo: ≤ 100 MB extra, hoy unos 55 MB) se repite con un
  carrusel y un fondo de foto a la vez.

## Pruebas

- **JVM:** `DesignSchema` (ajuste de foto, `photos`, límites, compatibilidad con diseños antiguos); lógica de
  reducción de `Backgrounds` extraída a una función pura (tamaño de destino y submuestreo).
- **`node --test`:** `photoStyle`, elección de la siguiente foto (secuencial y al azar sin repetir), clasificación
  del gesto (horizontal/vertical y sentido) y `snapMove` del editor.
- **Panel (`pytest`):** límite de subida de 8 MB y conservación de las demás rutas.
- **Manual en el reloj real:** foto grande subida sin error; ajustar zoom y posición y ver el resultado;
  carrusel con varias fotos; arrastrar un elemento; deslizar a Alarmas y a Ajustes, y de arriba abajo entre
  pantallas; deslizar con el WebView desactivado; memoria.
