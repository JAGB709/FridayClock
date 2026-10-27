# Elementos conectados entre sí: grupos y anclajes (subproyecto 3b)

Fecha: 2026-10-27 · Estado: pendiente de revisión · Continúa 3a (motor), 3c (fotos y gestos) y 3d (capas)

## Contexto y decisión de alcance

El spec 3a llamaba "3b" a los elementos que dependen de servicios externos (Immich, CalDAV, Home
Assistant...). Esos elementos no se tocan aquí. **Decisión:** este 3b se centra en los elementos
que se **relacionan entre sí** dentro del editor: agrupar, anclar, alinear, repartir, duplicar y
copiar entre pantallas. Los elementos de servicios externos siguen pendientes y se añadirán al
motor sin tocar estas reglas.

## Modelo (aditivo, versión 1 del diseño)

Dos campos opcionales en cualquier elemento, tanto en `screens.*.elements` como en
`alarm_screen.elements`. Un diseño sin ellos se lee y se dibuja exactamente igual que antes.

- `group`: identificador (`[a-z0-9_-]{1,24}`). Los elementos con el mismo `group` forman un grupo
  de **2 o más** miembros; se mueven juntos en el editor. No cambia cómo se dibujan.
- `anchor`: `{ "to": id, "side": "right|left|below|above", "gap": 0..3 }` (`gap` por defecto 0).
  Deja al elemento a `gap` casillas del borde del destino por ese lado, alineado con su borde
  superior (derecha, izquierda) o izquierdo (debajo, encima).

Posición derecha: `x = t.x + t.w + gap, y = t.y`; izquierda: `x = t.x - gap - w`; debajo:
`y = t.y + t.h + gap, x = t.x`; encima: `y = t.y - gap - h`.

## Validación (`DesignSchema.kt`, todo o nada)

- `group` con identificador no válido, o con un solo miembro: error en `...elements.<id>.group`.
- `anchor`: campos desconocidos, `to` inexistente en esa pantalla o igual al propio elemento,
  `side` o `gap` fuera de rango, ciclos entre anclas: errores en `...elements.<id>.anchor[.to|.side|.gap]`.
- **Decisión:** `x`/`y` guardados son la fuente y deben coincidir con lo que dicta el ancla; si no
  coinciden o la posición saldría de la rejilla, error en `...elements.<id>.anchor` con la posición
  esperada. Así reloj y panel dibujan lo mismo y los clientes antiguos que ignoran `anchor` también.
- Se mantienen el máximo de 12 elementos, el de fotos, la rejilla 8×6 (8×4 en alarma) y los solapes.

## Motor (`face/`)

`face-core.js`: `anchorPos` y `resolveAnchors(list, rows)`, que devuelve una copia con cada elemento
anclado puesto donde manda su ancla; un ancla rota, un ciclo o una posición fuera de la rejilla lo
dejan donde está (el dibujo nunca falla). `face.js` dibuja la lista resuelta y marca los nodos con
`data-group` y `data-anchor`.

## Editor (`EditorCore.js` y pestaña Pantallas)

Funciones puras, que no mutan la entrada y devuelven `{list}` o `{error}` en español:
`groupElements`, `ungroup`, `removeElement`, `moveGroup`, `resizeWithAnchors`, `setAnchor`,
`clearAnchor`, `applyAnchors`, `alignElements`, `distributeElements`, `duplicateElements`,
`copyToScreen`.

- Mover un elemento mueve su grupo; los elementos anclados a un miembro lo siguen.
  **Decisión:** mover a mano un elemento anclado (o alinearlo o repartirlo) suelta su ancla; los
  anclados entre miembros del mismo grupo la conservan.
- **Decisión:** no hay selección múltiple; alinear (izquierda, centro, derecha, arriba, medio, abajo)
  y repartir (horizontal o vertical, 3 o más) actúan sobre el grupo del elemento seleccionado.
- Duplicar y copiar renuevan ids y grupos, conservan los anclajes entre los copiados y sueltan los
  que apuntaban fuera; la copia va al primer hueco libre (o desplazada una casilla si no lo hay).
  Falla con aviso si supera 12 elementos, el máximo de fotos o la rejilla de la pantalla destino.
- Borrar un elemento suelta a quienes estaban anclados a él y disuelve grupos que queden con uno.
- UI: sección "Grupo", "Anclaje" y "Duplicar y copiar" en las opciones del elemento, con los
  componentes y tokens del sistema de diseño (sin emojis ni colores literales).

## Pruebas

`DesignSchemaTest` (válidos, rutas exactas de error, ciclo, rejilla, alarma), `core.test.js`
(`anchorPos`, `resolveAnchors`), `editor-core.test.js` (todas las operaciones), `sync-face.sh --check`.

## Fuera de alcance

Selección múltiple con el dedo, anclaje por centro o con desfase en el otro eje, redimensionar un
grupo entero, elementos de servicios externos, cronómetro (4d) y pantallas nativas (4b).
