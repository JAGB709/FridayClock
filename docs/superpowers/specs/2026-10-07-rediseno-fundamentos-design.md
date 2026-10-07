# Rediseño visual, parte 4a: fundamentos (sistema de diseño, iconos, brillo)

Fecha: 2026-10-07 · Estado: pendiente de revisión · Continúa `2026-10-07-fotos-gestos-design.md` (3c)

## Contexto y reparto del subproyecto 4

El rediseño visual se divide en partes independientes, cada una con su spec, su plan y su ejecución:

| Parte | Contenido | Estado |
|---|---|---|
| **4a** | Fundamentos: tokens, tipografía, iconos, temas por defecto nuevos, quitar emojis del reloj, regla del brillo | este documento |
| **4c** | Rediseño completo del **panel web** con el mismo sistema, móvil primero | siguiente; es donde está el grueso del cambio visual |
| 4b | Pantallas nativas del reloj (alarmas, alarma sonando, ajustes) | más adelante, si sigue interesando |
| 4d | Temporizadores y cronómetro, panel de control con tarjetas, vista de contenido del agente | más adelante; necesita decidir datos y API |

**Por qué el panel pesa más:** el usuario ya puede cambiar a su gusto lo que muestra el reloj (temas, pantallas, fotos); el panel es lo que usa a diario desde el móvil y donde el cambio visual más le interesa. En 4a el reloj solo recibe lo imprescindible.

**Dirección elegida: A, "Obsidiana"** (negro verdadero apto para OLED, un único acento ámbar cálido, una sola familia tipográfica).

## Reglas de todo el rediseño (del encargo del usuario)

Sin emojis en ningún sitio (interfaz, textos, etiquetas, código y comentarios nuevos); un único juego de iconos SVG coherente con trazo uniforme; como máximo 2 familias tipográficas, con escala definida y números tabulares; paleta limitada (neutros, un acento, semánticos de aviso, error y éxito) definida como tokens; objetivos táctiles de al menos 48 px; animaciones de 150 a 250 ms que respetan "reducir movimiento"; contraste WCAG AA, estados de foco y pulsación visibles, sin depender solo del color; modo noche de primera clase (negro verdadero, acentos cálidos tenues, automático según la hora); mitigación de quemado; todo el texto en español. Al cerrar cada parte: lista de comprobación (legibilidad a 2 m, modo noche, contraste, tamaños táctiles, coherencia de iconos, cero emojis) y resumen de qué cambió y por qué.

## 1. Tokens y tipografía

- **Fuente única de verdad:** `design-system/tokens.json` (color, espaciado, radios, sombras, escala tipográfica, duraciones). `tools/gen-tokens.py` genera de ahí `app-android/src/main/java/com/jarvis/clock/ui/Tokens.kt` (constantes para el reloj) y `design-system/tokens.css` (variables CSS `--...`) más sus copias para el motor y el panel. `tools/gen-tokens.py --check` falla si lo generado difiere; la CI lo ejecuta, como ya hace con `tools/sync-face.sh --check`.
- **Paleta (día):** fondo `#000000`; superficie `#111111`; superficie elevada `#1A1A1A`; borde `#2A2A2A`; texto `#F2F2F2`; texto secundario `#8E8E93`; acento `#FFB454`; error `#FF6B6B`; éxito `#5AD19A`; aviso `#FFD166`.
- **Paleta (noche):** mismo negro y superficies; texto `#B8A89A`; secundario `#6E625A`; acento `#9A5B12`. El modo noche del reloj ya existe por perfiles; estos son los colores del tema por defecto de noche.
- **Tipografía:** solo **Inter**, pesos 300, 400 y 600, **empaquetada** (el reloj no tiene red): fuentes `.ttf` en `app-android/src/main/assets/face/fonts/` y copia al panel con la copia del motor. Números tabulares con `font-variant-numeric: tabular-nums` (CSS) y `fontFeatureSettings = "tnum"` (nativo). Escala: 12, 14, 16, 20, 28, 40 y 56 px, y hora de pantalla completa ajustada a su caja. Interlineado 1,5 en cuerpo.
- **Espaciado y radios:** escala de 4 px (4, 8, 12, 16, 24, 32, 48); radios 8, 12 y 20; separación mínima entre objetivos táctiles de 8 px.
- **Duraciones:** 150 ms (micro), 200 ms (estándar), 250 ms (entrada de paneles); curva `cubic-bezier(0.2, 0, 0, 1)`.
- **Prueba de contraste:** una prueba con `node --test` calcula el contraste WCAG de todos los pares texto/fondo declarados en `tokens.json` (campo `pairs`) y falla por debajo de 4,5:1 (3:1 para texto grande).

## 2. Iconos

- **Juego:** Lucide (licencia ISC), trazo de 2 px en rejilla de 24, extremos redondos. Los SVG fuente viven en `design-system/icons/*.svg`; solo se incluyen los que se usan: `bell`, `mic`, `settings`, `sun`, `cloud`, `cloud-sun`, `cloud-rain`, `cloud-snow`, `cloud-lightning`, `cloud-fog`, `moon`, `clock`, `alert-triangle`, `check`, `x`, `trash-2`, `plus`, `play`, `pause`, `volume-2`, `image`, `upload`, `chevron-right`.
- **Reloj (nativo):** `tools/gen-icons.py` convierte cada SVG a un `VectorDrawable` en `app-android/src/main/res/drawable/ic_<nombre>.xml` (los primitivos `circle`, `line`, `rect` y `polyline` se pasan a `path`). Se tiñen con el token de color; sin dependencias nuevas.
- **Motor y panel:** un único `design-system/icons.js` (sprite de SVG en línea con la misma geometría) lo usan el motor del reloj y el panel; el motor lo recibe por la copia de `sync-face.sh`.
- **Emojis fuera del reloj:** el tiempo y la próxima alarma dejan de usar emojis. `FaceData` y `Weather` devuelven nombres de icono (`sun`, `cloud-rain`, …) en vez de emojis; el motor los dibuja con el sprite. Cambia el contrato de `/face/data` (campo `icon` pasa a nombre de icono); se actualizan el panel (datos de ejemplo), el mock y la skill. Los avisos con "⚠" y "⏰" del motor y la carátula nativa se sustituyen por `alert-triangle` y `bell`.
- **Escáner:** una prueba busca emojis y símbolos pictográficos en `app-android/src/main`, en el motor y en `design-system/`, y falla si encuentra alguno (en 4c se amplía al panel).

## 3. Temas por defecto y carátula

- `DesignSchema.initial(...)` cambia los temas por defecto a **Obsidiana Día** (fondo negro, texto `#F2F2F2`, acento ámbar, fuente fina) y **Obsidiana Noche** (negro, texto cálido tenue, acento ámbar apagado), mantiene los demás de la lista como opcionales y conserva los perfiles día/noche por hora. El diseño que el usuario ya guardó **no se toca**; solo afecta a instalaciones nuevas y a "restablecer diseño".
- La carátula nativa de respaldo y la barra inferior usan los tokens y los iconos `bell`, `mic` y `settings` con objetivos de 56 px, estado de pulsación y foco visible. La pantalla de alarma sonando conserva su estructura (se rediseña en 4b) pero usa los tokens de color y de tipografía.
- La hora sigue ajustándose a su caja; la fecha y como mucho 2 datos discretos (tiempo y próxima alarma) se mantienen. Anti-quemado: el desplazamiento por minuto se conserva y de noche el acento baja al ámbar apagado.
- Animaciones de cambio de pantalla de 200 ms; se desactivan con `prefers-reduced-motion` en el motor.

## 4. Brillo

- **Por defecto** manda el brillo del **perfil activo** del diseño (el del panel).
- **Ajuste manual en el reloj:** `Ajustes` incorpora un deslizador de brillo (1 a 100). Al moverlo se guarda un **valor manual** `{percent, profile_id, design_brightness_stamp}` en `Prefs`, que sobrevive a reinicios. Mientras exista, es el brillo efectivo del perfil con ese `profile_id`.
- **Se borra** en dos casos: (1) el reloj pasa a **otro perfil** (por la hora o por una vista previa); (2) **el panel guarda un diseño cuyo conjunto de brillos de perfil difiere** del que había al crear el valor manual (`design_brightness_stamp` = hash de los brillos de los perfiles). Cambiar otra cosa del diseño no lo borra.
- Botón **"Volver al brillo del panel"** en Ajustes: borra el valor manual.
- `POST /brightness` y la acción `set_brightness` (Hermes) pasan a fijar el mismo valor manual, para que haya una sola regla. La rampa de amanecer de la alarma sonando no cambia; al terminar la alarma se recupera el brillo efectivo.
- Con un perfil de noche, tocar la pantalla sigue despertándola al brillo del primer perfil que no sea de noche, salvo que haya un valor manual vigente para el perfil de noche.
- La lógica se aísla en un objeto puro `Brightness` (`effective(profile, manual)`, `onProfileChanged`, `onDesignSaved`) con pruebas JVM; `MainActivity` solo la llama.

## 5. Panel web (alcance mínimo en 4a)

Solo lo necesario para no romper: el panel recibe `tokens.css` y la fuente Inter y usa los tokens en sus variables existentes (sin rediseñar pantallas); los datos de ejemplo del tiempo usan nombres de icono. El rediseño real es 4c.

## Fuera de alcance de 4a

Rediseño de AlarmsActivity, SettingsActivity más allá del deslizador de brillo, BluetoothActivity y pantalla de alarma sonando (4b); cronómetro, tarjetas y vista del agente (4d); rediseño del panel (4c).

## Pruebas

- **JVM:** `Brightness` (por defecto, valor manual vigente, cambio de perfil, guardado del panel con y sin cambio de brillos, reinicio, `set_brightness`), `DesignSchema.initial` válido y con los temas nuevos, `FaceData` con nombres de icono.
- **node/Python:** generador de tokens (`--check`), contraste AA de todos los pares, escáner de emojis, el sprite contiene todos los iconos usados por el motor.
- **Panel (`pytest`):** sigue pasando con los datos de ejemplo nuevos.
- **Manual en el reloj real:** capturas de día y de noche; deslizador de brillo; cambio de perfil con valor manual; guardar brillo desde el panel; "Volver al brillo del panel".
