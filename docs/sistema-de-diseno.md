# Sistema de diseño

Una sola fuente de verdad para colores, tipografía e iconos, compartida por la app del reloj, el motor de pantallas y el panel.

- **Tokens**: `design-system/tokens.json` define colores, espaciados, radios, tamaños de texto y duraciones. `python tools/gen-tokens.py` genera `tokens.css` (y los equivalentes nativos); `python tools/gen-tokens.py --check` verifica que lo generado está al día.
- **Tipografía**: Inter (Light, Regular y SemiBold, licencia OFL) en `app-android/src/main/assets/face/fonts/`; el panel la sirve desde `/face/fonts/`.
- **Iconos**: Lucide, en `design-system/icons/`. `python tools/gen-icons.py` genera `icons.js` y los recursos nativos; `--check` verifica que están al día.
- **Copia al panel**: tras regenerar, ejecuta `bash tools/sync-face.sh` (`--check` lo verifica). La CI ejecuta los tres `--check`.
- **Cero emojis**: se aplica al reloj, al motor y al sistema de diseño (interfaz, datos y textos hablados; todo icono es un icono Lucide por nombre). También rige para el panel web: ningún emoji ni pictograma en `homelab/friday-panel/static/`; los iconos son Lucide por nombre.
- **Contrato de `/face/data`**: el campo `icon` (por ejemplo en `weather`) pasa a ser un nombre de icono (`sun`, `cloud-rain`...) que el motor dibuja con Lucide; antes era un emoji.
- **Temas**: el diseño trae por defecto dos temas, Obsidiana Día y Obsidiana Noche, basados en los tokens.
- **Regla de brillo**: por defecto manda el brillo del panel (el del perfil activo). Si se cambia el brillo en el reloj (Ajustes o `POST /brightness`), ese valor manual manda y se conserva hasta que cambie el perfil activo o hasta que el panel cambie el brillo de un perfil. El botón "Volver al brillo del panel" en Ajustes del reloj borra el valor manual. `POST /brightness` aplica exactamente la misma regla que el deslizador. Una vista previa no borra el valor manual: solo lo ignora mientras el perfil previsualizado difiere del programado. Con un perfil de noche, tocar la pantalla la despierta al brillo del primer perfil que no sea de noche, salvo que haya un valor manual vigente para el perfil de noche. La rampa de amanecer de la alarma no cambia.


## Pruebas que protegen el diseño

`homelab/friday-panel/tests/test_design_system.py` comprueba que los tokens e iconos generados están al día, el contraste mínimo de los pares de color declarados (los del reloj y los del panel), que `static/` (icono incluido) no contiene emojis ni pictogramas (incluso escapados), que el CSS solo usa tokens de color y que todo icono usado existe en el sprite. La rueda de hora tiene sus pruebas en `homelab/friday-panel/tests-js/wheel-core.test.js`. Todo se ejecuta en la CI.
