# Elementos superpuestos con capas y carrusel de fondo de tema (subproyecto 3d)

Fecha: 2026-10-07 · Estado: pendiente de revisión · Continúa `2026-10-07-fotos-gestos-design.md` (3c) y va **antes** del rediseño del panel (4c)

## Contexto y objetivo

Dos peticiones del usuario sobre las pantallas del reloj:

1. **Elementos que se solapan**, y poder enviar uno detrás o delante de otro. Hoy el esquema rechaza los solapes y el usuario no controla el orden de dibujo.
2. **Carrusel de fotos como fondo de tema.** Hoy el carrusel solo existe como elemento Fotos y el fondo de un tema es una sola imagen.

Afecta al esquema (`DesignSchema.kt`), al motor (`face.js`, `face-core.js`) y al editor del panel. No cambia el aspecto del panel: el rediseño visual (4c) va después y ya incluirá estos controles.

## Decisiones

- **El orden de capas es el orden de la lista de elementos:** el último elemento se dibuja delante. No se añade ningún campo nuevo al JSON, así que los diseños existentes siguen siendo válidos y se ven igual.
- **Se permite el solape.** Siguen validándose los límites de la rejilla (`x + w ≤ 8`, `y + h ≤ filas`) y el máximo de 12 elementos por pantalla.
- **El fondo carrusel reutiliza el motor del carrusel de elementos** (capas, precarga, fundido, orden).
- **Memoria:** cada carrusel conserva una sola foto descodificada (se libera la capa saliente al terminar el fundido).
- UI en español.

## Fuera de alcance

Un campo `z` numérico explícito; agrupar o bloquear elementos; carrusel con ajuste distinto por foto; vídeo; el rediseño del panel (4c).

## Modelo de datos (versión 1 del diseño, ampliada de forma compatible)

### Solapes

`DesignSchema.validateElements` elimina el error `"se solapa con <id>"` (y el tipo `Rect` y su bucle de comparación dejan de usarse). Todo lo demás queda igual: tipos, campos, rejilla, máximo de elementos, máximo de elementos `photos` por pantalla (2) y en alarma (1).

### Fondo de tema `photos`

`themes.<id>.bg` acepta `"type": "photos"` con:

| Campo | Valores | Por defecto |
|---|---|---|
| `images` | lista de 1 a 20 nombres de foto de la biblioteca (mismo formato que `bg.image`) | obligatorio |
| `interval_s` | entero 3 a 600 | 10 |
| `transition` | `fade` o `none` | `fade` |
| `order` | `sequential` o `random` | `sequential` |
| `fit`, `zoom`, `pos_x`, `pos_y` | como las fotos de 3c | `cover`, 1, 50, 50 |
| `dim` | entero 0 a 80 | 0 |
| `color` | color `#rrggbb` de respaldo si falta alguna foto | `#000000` |

Los campos de ajuste (`fit`, `zoom`, `pos_*`) y los nuevos solo se aceptan con `type: "photos"`; los de ajuste siguen aceptándose con `type: "image"`; con `color` o `gradient` se rechazan como hoy. Una foto de la lista que ya no existe se salta; si no queda ninguna, se ve el color de respaldo.

El límite total de 64 KB del diseño se mantiene (20 nombres por tema, máx. 12 temas, entra de sobra).

## Motor `face/`

- **Orden de capas:** `draw()` ya añade los elementos al DOM en el orden de la lista; el último queda delante (misma pila, sin `z-index`). Se documenta y se prueba.
- **Carrusel reutilizable:** se extrae de `builders.photos` la función `startCarousel(container, names, opts)` (`opts`: `interval_s`, `transition`, `order`, `fit`, `zoom`, `pos_x`, `pos_y`, `timers`), que crea las dos capas, precarga y alterna. La usan el elemento `photos` y el fondo.
- **Liberar memoria:** al terminar el fundido (`transitionend`, o inmediatamente si `transition: "none"`), la capa saliente pone `background-image: none`. Cada carrusel mantiene una sola imagen descodificada.
- **Fondo `photos`:** `applyTheme` crea `.bglayer` con `startCarousel` y la capa de oscurecido (`.bgdim`) por encima de las fotos, igual que con `type: "image"`. Sus temporizadores van en `S.bgTimers`, que se cancelan en `applyTheme` (cada `render`), no en `draw()`, para que el fondo no se reinicie al cambiar de pantalla del mismo perfil. `Core.bgCss` devuelve el color de respaldo (`color` o `#000000`) para este tipo.
- **Selección en edición con solapes:** al pulsar en la vista previa se calculan los elementos bajo el puntero (`document.elementsFromPoint`, filtrando `.el`); si el elemento seleccionado está entre ellos, la selección pasa al siguiente de la pila (el de debajo, y tras el último vuelve al de arriba); si no, al de más arriba. Función pura `Core.pickUnder(ids, selectedId)` (ids ordenados de arriba a abajo) probada con `node --test`.

## Panel (`homelab/friday-panel`)

- `EditorCore`: se añaden funciones puras `bringToFront(list, id)`, `sendToBack(list, id)`, `moveUp(list, id)` (una capa hacia delante) y `moveDown(list, id)` (una hacia atrás); devuelven la lista nueva y no tocan el resto. `collides` y `findFreeSpot` se conservan: un elemento nuevo busca un hueco libre y, si no lo hay, se coloca en `x:0, y:0` con su tamaño por defecto (solapando) en lugar de rechazarse.
- **Lista de elementos** de la pantalla: se muestra de delante hacia atrás (el último de la lista primero) con una etiqueta "delante" y "al fondo" en los extremos, y se puede elegir cualquiera, también uno tapado por otro.
- **Opciones del elemento** (`elementOptions`): cuatro botones "Traer al frente", "Enviar al fondo", "Subir una capa" y "Bajar una capa" (deshabilitados cuando no aplican).
- `moveElement`, `resizeElement` y `setRect` dejan de comprobar solapes; solo comprueban `EditorCore.inBounds`. El aviso "Se solapa con …" desaparece.
- **Fondo de tema:** el selector "Tipo de fondo" añade "Carrusel de fotos" (`photos`). Al elegirlo se crea `{type:"photos", images:[<primera foto de la biblioteca si existe>], interval_s:10, transition:"fade", order:"sequential", fit:"cover", zoom:1, pos_x:50, pos_y:50, dim:0, color:<color base>}`. El formulario reutiliza `photoListField` (miniaturas con casillas, subir fotos, quitar fotos ausentes, máx. 20), `photoFitBlock`, los campos de intervalo, transición y orden, el oscurecido (0 a 80) y el color de respaldo. `deleteBackground` avisa también de los temas con fondo carrusel que usan la foto.
- Cambiar entre tipos de fondo conserva el color base (como hoy).

## Errores y casos límite

- Diseños con elementos superpuestos: válidos; se ven en el orden de la lista.
- Reordenar con un solo elemento: los botones de capa están deshabilitados.
- Fondo carrusel sin fotos válidas: se ve `color`; el panel avisa.
- Fondo carrusel y dos elementos carrusel a la vez: memoria medida en el reloj real (objetivo: ≤ 100 MB sobre la línea base de 27 MB, hoy unos 97 MB con un fondo y un carrusel).
- Un reloj con el firmware anterior rechazará un diseño con `bg.type: "photos"` (error de esquema conocido); el panel y el reloj se despliegan juntos.

## Pruebas

- **JVM:** `DesignSchema`: solapes aceptados; fuera de rejilla sigue rechazado; fondo `photos` válido; límites de `images` e `interval_s`; campos de ajuste solo con `image` o `photos`; compatibilidad con los diseños antiguos.
- **`node --test`:** `Core.pickUnder`; `Core.bgCss` para `photos`; `EditorCore.bringToFront`, `sendToBack`, `moveUp`, `moveDown` (casos de extremos y de un solo elemento); orden de dibujo (los elementos de la lista aparecen en el DOM en ese orden).
- **Panel (`pytest`):** sigue pasando; `FACE_FILES` y versión del motor coherentes.
- **Manual en el reloj real (vista previa temporal, sin tocar el diseño del usuario):** dos elementos solapados en el orden esperado; cambiar el orden y verlo en la vista previa; fondo carrusel con fotos de prueba; memoria con un fondo carrusel y dos carruseles de elementos.
