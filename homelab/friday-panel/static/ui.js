"use strict";
// Componentes del panel (sistema Obsidiana). Se carga antes que pantallas.js y app.js
// y deja sus funciones como globales. Todo se construye con h(); los colores, tamaños
// y animaciones vienen de styles.css (sobre /face/tokens.css), nunca de aquí.

function h(tag, attrs = {}, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else if (k === "class") el.className = v;
    else if (k === "checked" || k === "disabled" || k === "selected") el[k] = v;
    else if (k === "value" && v != null) el[k] = v;
    else if (v !== false && v != null) el.setAttribute(k, v === true ? "" : v);
  }
  for (const kid of kids.flat()) {
    if (kid == null || kid === false) continue;
    el.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
  }
  return el;
}

/* Icono Lucide del sprite de /face/icons.js. Un nombre que no existe devuelve un nodo de
   texto vacío: la vista sigue funcionando sin el dibujo. Siempre decorativo (aria-hidden);
   el nombre accesible lo pone el texto o el aria-label del control. */
function icon(name, size = 20) {
  const node = window.FridayIcons && window.FridayIcons.node(name, size);
  if (!node) return document.createTextNode("");
  node.setAttribute("class", "ico");
  node.setAttribute("aria-hidden", "true");
  return node;
}

/* Botón. `cls`: "", "primary", "ghost", "danger", "small" (combinables). El icono va antes
   del texto. Sin texto es un botón solo de icono y necesita opts["aria-label"]. */
function btn(label, onclick, cls = "", iconName = null, opts = {}) {
  const onlyIcon = label == null || label === "";
  const classes = [cls, onlyIcon ? "icon" : ""].filter(Boolean).join(" ");
  // Un botón nunca se queda sin nombre: sin texto ni aria-label, se usa el del icono.
  const ariaLabel = opts["aria-label"] || (onlyIcon ? (iconName || "Botón") : null);
  return h("button", { type: "button", ...opts, class: classes, onclick,
    "aria-label": ariaLabel, title: onlyIcon ? (opts.title || ariaLabel) : (opts.title || null) },
  iconName ? icon(iconName) : null,
  onlyIcon ? null : h("span", { class: "lbl" }, label));
}

/* Botón cuadrado de 48 px solo con icono, con nombre accesible y tooltip. */
function iconButton(iconName, label, onclick, cls = "ghost") {
  return btn("", onclick, cls, iconName, { "aria-label": label, title: label });
}

/* Escala el iframe del motor (800x480 fijo) al ancho del contenedor, manteniendo 16:10.
   Lo usan la vista previa de Pantallas y el reloj en vivo de Inicio. */
function fitFaceFrame(wrap, frame) {
  if (!wrap || !frame) return;
  const k = wrap.clientWidth / 800;
  frame.style.transform = "scale(" + k + ")";
  wrap.style.height = Math.round(480 * k) + "px";
}

/* Nombres legibles de las claves internas (alarma y ajustes) y de las rutas del diseño, para
   que ningún aviso ni error enseñe una clave en inglés. renderSettings registra las etiquetas
   del esquema con registerKeyLabels(). */
const KEY_LABELS = {
  time: "Hora", label: "Nombre", days: "Días", enabled: "Activa", sound: "Sonido",
  max_volume: "Volumen máximo", ramp_s: "Subida de volumen", snooze_min: "Minutos al posponer", briefing: "Buenos días",
};
const DESIGN_FIELDS = {
  x: "columna", y: "fila", w: "ancho", h: "alto", color: "color", align: "alineación", type: "tipo", id: "identificador",
  name: "nombre", theme: "tema", style: "estilo", format: "formato", mode: "modo", count: "cantidad", images: "imágenes",
  fg: "color del texto", muted: "color secundario", accent: "color de acento", font: "letra", bg: "fondo", zoom: "zoom",
  fit: "ajuste", pos_x: "posición horizontal", pos_y: "posición vertical", snooze_label: "texto de posponer",
  dismiss_label: "texto de apagar", seconds: "segundos", hour12: "formato de 12 horas", detail: "detalle", show_label: "mostrar nombre",
};
function registerKeyLabels(map) { Object.assign(KEY_LABELS, map); }

function humanKey(key) {
  key = String(key == null ? "" : key);
  if (Object.prototype.hasOwnProperty.call(KEY_LABELS, key)) return KEY_LABELS[key];
  const parts = key.split(".");
  const scope = { screens: "Pantalla", themes: "Tema", profiles: "Perfil" };
  const fieldName = (f) => DESIGN_FIELDS[f] || f.replace(/_/g, " ");
  if (parts.length > 1 && scope[parts[0]]) {
    const out = [`${scope[parts[0]]} "${parts[1]}"`];
    let i = 2;
    if (parts[0] === "screens" && parts[2] === "elements") {
      if (parts[3]) out.push(`elemento "${parts[3].replace(/\[(\d+)\]$/, " n.º $1")}"`);
      i = 4;
    }
    if (parts.slice(i).length) out.push(parts.slice(i).map(fieldName).join(", "));
    return out.join(", ");
  }
  if (parts[0] === "alarm_screen") {
    const out = ["Pantalla de alarma"];
    if (parts[1] === "elements" && parts[2]) out.push(`elemento "${parts[2]}"`);
    else if (parts[1]) out.push(fieldName(parts[1]));
    if (parts[1] === "elements" && parts[3]) out.push(fieldName(parts[3]));
    return out.join(", ");
  }
  if (parts.length > 1) return parts.map(fieldName).join(", ");
  const plain = { themes: "Temas", screens: "Pantallas", profiles: "Perfiles", version: "Versión", design: "Diseño" };
  return plain[key] || key.replace(/_/g, " ");
}

// Separa la clave interna al inicio de un mensaje ("max_volume debe estar entre 1 y 100").
function splitKeyed(msg) {
  const m = /^([a-z][a-z0-9_]*)(?::\s*|\s+)(.+)$/s.exec(String(msg));
  if (!m || !(Object.prototype.hasOwnProperty.call(KEY_LABELS, m[1]) || m[1].includes("_"))) return null;
  return { key: m[1], rest: m[2] };
}
// Error en línea: sin la clave (el campo ya está marcado) y con mayúscula inicial.
function errorBody(msg) {
  const k = splitKeyed(msg);
  const body = k ? k.rest : String(msg);
  return body.charAt(0).toUpperCase() + body.slice(1);
}
// Aviso: el mismo mensaje con la clave traducida ("Volumen máximo debe estar entre 1 y 100").
function humanError(msg) {
  const k = splitKeyed(msg);
  return k ? `${humanKey(k.key)} ${k.rest}` : String(msg);
}
// Detalle por campo de un error de varios campos, para el aviso.
function errorDetail(errors) {
  return Object.entries(errors || {}).map(([k, v]) => `${humanKey(k)}: ${v}`).join(" · ");
}

/* Estados de vista: vacío, cargando y error. Centrados, con icono y texto. */
function emptyState(iconName, text, action = null) {
  return h("div", { class: "state" },
    h("div", { class: "state-ico" }, icon(iconName, 28)),
    h("p", {}, text),
    action);
}

/* Aviso con borde ámbar: icono y texto (nunca solo color). Un único estilo para todo el panel. */
function warnBox(text, attrs = {}) {
  return h("div", { class: "warn", role: "status", ...attrs }, icon("alert-triangle", 20), h("span", {}, text));
}

function loadingState(text = "Cargando…") {
  return h("div", { class: "state loading", role: "status", "aria-live": "polite" },
    h("div", { class: "spinner", "aria-hidden": "true" }),
    h("p", {}, text));
}

function errorState(msg, onRetry, retryLabel = "Reintentar") {
  return h("div", { class: "state error", role: "alert" },
    h("div", { class: "state-ico" }, icon("alert-triangle", 28)),
    h("p", {}, msg),
    onRetry ? btn(retryLabel, onRetry, "ghost", "refresh-cw") : null);
}

/* Selector segmentado. options: [[valor, etiqueta, icono?]]. Flechas izquierda y derecha
   mueven la selección (patrón de pestañas). */
function segmented(options, value, onchange) {
  const bar = h("div", { class: "segmented", role: "tablist" });
  const select = (v, focus) => {
    const known = options.some((o) => String(o[0]) === String(v));
    for (const b of bar.children) {
      const on = b.dataset.value === String(v);
      b.setAttribute("aria-selected", on ? "true" : "false");
      // Si el valor no es de ninguna opción, la primera sigue siendo alcanzable con Tab.
      b.tabIndex = on || (!known && b === bar.firstElementChild) ? 0 : -1;
      b.classList.toggle("on", on);
      if (on && focus) b.focus();
    }
  };
  for (const [v, label, ic] of options) {
    bar.append(h("button", {
      type: "button", role: "tab", "data-value": String(v),
      onclick: () => { select(v); onchange(v); },
      onkeydown: (ev) => {
        if (ev.key !== "ArrowLeft" && ev.key !== "ArrowRight") return;
        ev.preventDefault();
        const i = options.findIndex((o) => String(o[0]) === String(v));
        const next = options[(i + (ev.key === "ArrowRight" ? 1 : options.length - 1)) % options.length][0];
        select(next, true); onchange(next);
      },
    }, ic ? icon(ic) : null, h("span", { class: "lbl" }, label)));
  }
  select(value);
  return bar;
}

/* Fila con interruptor (toda la fila es pulsable). `key` rellena data-key para markErrors. */
function switchRow(label, checked, onchange, key) {
  return h("label", { class: "switch", "data-key": key || null },
    h("span", {}, label),
    h("input", { type: "checkbox", role: "switch", checked: !!checked, onchange: (ev) => onchange(ev.target.checked) }));
}

/* Rueda de hora: dos columnas (horas 00-23 y minutos 00-59) con scroll-snap y filas de
   48 px; se ven 5 filas y la central (resaltada) es la elegida. La lógica de valores e
   índices vive en wheel-core.js. Avisa con onChange("HH:MM") cuando el scroll se detiene
   (80 ms sin eventos) o al usar el teclado: flechas +-1, RePág/AvPág +-5 (horas) o
   +-10 (minutos), Inicio/Fin a los extremos. Un valor no válido empieza en 07:00.
   flush() (o flushWheels() para todas) avisa ya del valor que marca el scroll, sin esperar
   al debounce: se llama antes de guardar, de salir del editor o de repintarlo. */
let wheelSeq = 0;
function timeWheel(value, onChange) {
  const W = window.WheelCore;
  const ROW = 48;
  const start = W.parseTime(value) || { h: 7, m: 0 };
  const uid = "wheel" + (++wheelSeq);
  let last = W.formatTime(start.h, start.m);
  const cols = {};

  const emit = () => {
    const v = W.formatTime(cols.hours.index, cols.minutes.index);
    if (v === last) return;
    last = v;
    if (onChange) onChange(v);
  };

  function column(kind, label, pageStep) {
    const count = kind === "hours" ? 24 : 60;
    // target: fila a la que va el scroll suave tras tocar una fila; mientras exista, el scroll
    // todavía no dice el valor (flush usa el objetivo, no la posición intermedia).
    const col = { index: kind === "hours" ? start.h : start.m, timer: null, target: null };
    const opts = W.valuesFor(kind).map((v) => h("div", {
      class: "wheel-opt", role: "option", id: `${uid}-${kind}-${v}`, "aria-selected": "false",
      onclick: () => { setIndex(v, true); },
    }, W.pad2(v)));
    const list = h("div", {
      class: "wheel-col", role: "listbox", tabindex: "0", "aria-label": label,
      onpointerdown: () => { col.target = null; },
      onwheel: () => { col.target = null; },
      onscroll: () => {
        if (col.target != null) {
          if (Math.abs(list.scrollTop - col.target * ROW) > 1) return; // animación en curso: sin filas intermedias
          col.target = null;
        }
        mark(W.nearestIndex(list.scrollTop, ROW, count));
        clearTimeout(col.timer);
        col.timer = setTimeout(emit, 80);
      },
      onkeydown: (ev) => {
        const step = { ArrowUp: -1, ArrowDown: 1, PageUp: -pageStep, PageDown: pageStep }[ev.key];
        let next = null;
        if (step != null) next = col.index + step;
        else if (ev.key === "Home") next = 0;
        else if (ev.key === "End") next = count - 1;
        if (next == null) return;
        ev.preventDefault();
        setIndex(W.valueAt(next, count), false);
        emit();
      },
    }, opts);
    // Marca la fila central (resaltada y anunciada) sin mover el scroll.
    function mark(i) {
      if (i === col.index && opts[i].classList.contains("on")) return;
      opts[col.index].classList.remove("on");
      opts[col.index].setAttribute("aria-selected", "false");
      col.index = i;
      opts[i].classList.add("on");
      opts[i].setAttribute("aria-selected", "true");
      list.setAttribute("aria-activedescendant", opts[i].id);
    }
    // Lleva la columna a un valor: con animación al tocar una fila, al instante con teclado.
    function setIndex(i, smooth) {
      i = W.indexFor(i, count);
      mark(i);
      const calm = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
      if (smooth && !calm && list.scrollTo) { col.target = i; list.scrollTo({ top: i * ROW, behavior: "smooth" }); }
      else { col.target = null; list.scrollTop = i * ROW; }
    }
    col.list = list;
    col.mark = mark;
    col.setIndex = setIndex;
    mark(col.index);
    return col;
  }

  cols.hours = column("hours", "Horas", 5);
  cols.minutes = column("minutes", "Minutos", 10);
  const root = h("div", { class: "wheel", role: "group", "aria-label": "Hora de la alarma" },
    h("div", { class: "wheel-band", "aria-hidden": "true" }),
    cols.hours.list,
    h("div", { class: "wheel-sep", "aria-hidden": "true" }, ":"),
    cols.minutes.list);

  // El scroll solo se puede colocar con el nodo ya en el DOM (tiene altura).
  let placed = false;
  const place = () => { cols.hours.setIndex(cols.hours.index, false); cols.minutes.setIndex(cols.minutes.index, false); placed = true; };
  requestAnimationFrame(place);

  // Lee el valor del scroll actual, para la columna en esa fila (corta la inercia) y avisa
  // ya. Sin colocar todavía, el scroll no dice nada: vale el valor inicial.
  root.flush = () => {
    if (!placed || !root.isConnected) return;
    for (const col of [cols.hours, cols.minutes]) {
      clearTimeout(col.timer);
      const count = col === cols.hours ? 24 : 60;
      col.setIndex(col.target != null ? col.target : W.nearestIndex(col.list.scrollTop, ROW, count), false);
    }
    emit();
  };
  return root;
}

function flushWheels() {
  document.querySelectorAll(".wheel").forEach((w) => { if (w.flush) w.flush(); });
}

/* Aviso breve arriba, con icono. #toast está siempre en el DOM como región viva vacía
   (clase is-empty, oculta sin display:none); el contenido se pone un fotograma después de
   mostrarla para que los lectores de pantalla lo anuncien. Los errores duran más. */
let toastTimer, toastFrame;
function toast(msg, bad = false) {
  const t = document.getElementById("toast");
  clearTimeout(toastTimer);
  cancelAnimationFrame(toastFrame);
  t.replaceChildren();
  t.classList.add("is-empty");
  toastFrame = requestAnimationFrame(() => {
    t.className = bad ? "bad" : "";
    t.replaceChildren(icon(bad ? "alert-triangle" : "check"), h("span", {}, msg));
    toastTimer = setTimeout(() => { t.replaceChildren(); t.className = "is-empty"; }, bad ? 6000 : 3000);
  });
}

/* Estado de la conexión en la cabecera: icono y texto, nunca solo color. #conn es una
   región viva: solo se toca cuando cambia, para no repetir el aviso en cada petición. */
let connLast = null;
function setConn(on, msg) {
  const text = on ? "Conectado" : (msg || "Sin conexión con el reloj");
  const key = (on ? "on:" : "off:") + text;
  if (key === connLast) return;
  connLast = key;
  const p = document.getElementById("conn");
  p.replaceChildren(icon(on ? "wifi" : "wifi-off", 16), h("span", {}, text));
  p.className = "pill " + (on ? "on" : "off");
  p.title = text;
}

/* Confirmación en la página: diálogo modal accesible. El foco empieza en Cancelar (la
   opción segura), Tab no sale del diálogo, Escape o tocar fuera cancelan y al cerrar el
   foco vuelve a donde estaba. */
function confirmInPage(msg, okLabel = "Aceptar") {
  return new Promise((resolve) => {
    const before = document.activeElement;
    // El resto de la página queda inerte mientras el diálogo está abierto.
    const inertEls = ["top", "view", "tabs", "savebar", "toast", "fab"].map((i) => document.getElementById(i))
      .filter((el) => el && !el.inert);
    const msgId = "dlg-msg-" + Date.now();
    const close = (v) => {
      document.removeEventListener("keydown", onKey, true);
      dlg.remove();
      inertEls.forEach((el) => { el.inert = false; });
      if (before && before.isConnected && before.focus) before.focus();
      resolve(v);
    };
    const cancel = h("button", { type: "button", class: "ghost", onclick: () => close(false) }, "Cancelar");
    const ok = h("button", { type: "button", class: "danger", onclick: () => close(true) }, okLabel);
    const onKey = (ev) => {
      if (ev.key === "Escape") { ev.preventDefault(); ev.stopPropagation(); close(false); }
      else if (ev.key === "Tab") {
        const first = cancel, last = ok;
        if (ev.shiftKey && document.activeElement === first) { ev.preventDefault(); last.focus(); }
        else if (!ev.shiftKey && document.activeElement === last) { ev.preventDefault(); first.focus(); }
        else if (document.activeElement !== first && document.activeElement !== last) { ev.preventDefault(); first.focus(); }
      }
    };
    const dlg = h("div", { class: "modal", onclick: (ev) => { if (ev.target === dlg) close(false); } },
      h("div", { class: "card", role: "dialog", "aria-modal": "true", "aria-labelledby": msgId },
        h("p", { id: msgId }, msg),
        h("div", { class: "row actions" }, cancel, ok)));
    document.body.append(dlg);
    inertEls.forEach((el) => { el.inert = true; });
    document.addEventListener("keydown", onKey, true);
    cancel.focus();
  });
}
