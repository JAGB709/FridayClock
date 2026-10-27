# Pantallas y temas

Este documento describe el diseño (JSON) que dibuja la carátula y la pantalla de alarma del reloj, y todo lo que se puede configurar desde la pestaña **Pantallas** del panel web.


La carátula y la pantalla de alarma se describen en un diseño (JSON): **temas** (colores, letra,
fondo liso, degradado o imagen), **pantallas** en una rejilla de 8×6 con hora, fecha, tiempo,
previsión, próxima alarma y texto o frase del día, y **perfiles** por hora (Mañana, Día, Noche…)
con su tema, sus pantallas, rotación y brillo. Se edita desde la pestaña **Pantallas** del panel
web, con una vista previa idéntica a la del reloj y un botón **Probar** que lo muestra 30 s en el
reloj sin guardarlo. Si el `WebView` del reloj fallase, vuelve la carátula nativa de siempre; las
alarmas no dependen de él.

**Fotos.** Un fondo de imagen y el elemento `photos` aceptan los campos de ajuste `fit`
(`cover`, `contain` o `fill`), `zoom` (1 a 3) y `pos_x`/`pos_y` (0 a 100, punto de la foto que
queda centrado). El elemento `photos` es un carrusel: `images` (1 a 20 nombres de fotos subidas),
`interval_s` (3 a 600 s), `transition` (`fade` o `none`), `order` (`sequential` o `random`) y los
campos de ajuste; cada pantalla admite como máximo 2 elementos `photos` (1 en la pantalla de alarma). Las fotos se suben desde el
panel, que las reduce en el navegador antes de enviarlas; el reloj las deja en 1600 px como mucho
(subida ≤ 8 MB, biblioteca ≤ 60 fotos). En el editor los elementos se mueven arrastrándolos.

**Capas y solapes.** Los elementos de una pantalla pueden solaparse: el orden de la lista de
elementos es el orden de dibujo (el último queda delante). En el editor, el panel de opciones del
elemento tiene los botones **Traer al frente**, **Subir una capa**, **Bajar una capa** y **Enviar al
fondo**. Al tocar de nuevo el punto de un elemento ya seleccionado se selecciona el que hay debajo
(se va rotando entre los solapados); arrastrar el elemento seleccionado lo mueve.

**Fondo de fotos.** Además de liso, degradado e imagen, el fondo de un tema puede ser
`{"type":"photos"}`: un carrusel a pantalla completa con `images` (1 a 20 fotos subidas),
`interval_s` (3 a 600 s), `transition` (`fade` o `none`), `order` (`sequential` o `random`), los
campos de ajuste `fit`, `zoom`, `pos_x` y `pos_y`, `dim` (0 a 80, oscurece la foto para que se lea el
texto) y `color` (color de reserva bajo las fotos). Cada carrusel, de fondo o
elemento `photos`, mantiene en reposo una sola foto decodificada en memoria; durante el fundido de 0,6 s
sujeta dos, y libera la saliente justo después, para cuidar el reloj (1 GB de RAM).

**Grupos y anclajes.** Cualquier elemento admite dos campos opcionales (un diseño sin ellos se
comporta igual que antes). `group` (identificador) reúne 2 o más elementos que se mueven juntos en
el editor. `anchor` es `{"to": id, "side": "right|left|below|above", "gap": 0 a 3}`: deja el
elemento a `gap` casillas del destino, alineado con su borde. El `x` e `y` guardados deben coincidir
con lo que dicta el ancla; si no, o si hay un ciclo, un destino inexistente o una salida de la
rejilla, el diseño se rechaza con la ruta exacta (por ejemplo `screens.principal.elements.fecha.anchor`).
En el editor, las opciones del elemento tienen **Grupo** (agrupar, desagrupar, alinear el grupo a la
izquierda, centro, derecha, arriba, medio o abajo, y repartir en horizontal o vertical con 3 o más),
**Anclaje** (destino, lado y separación; los anclados siguen a su destino al moverlo o cambiarlo de
tamaño, y mover a mano un elemento anclado lo suelta) y **Duplicar y copiar** (duplica el elemento o
su grupo, o lo copia a otra pantalla o a la de alarma, respetando el máximo de 12 elementos y de fotos
y la rejilla de destino).

**Gestos.** En la carátula, deslizar de izquierda a derecha abre Alarmas y de derecha a
izquierda abre Ajustes; para cambiar de pantalla del perfil se desliza en vertical. En la pantalla de
alarma sonando, deslizar no hace nada.
