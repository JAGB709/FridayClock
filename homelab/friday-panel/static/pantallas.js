"use strict";
// Pestaña Pantallas: vista previa fija arriba (el mismo motor que el reloj, en un iframe), un
// selector de 4 secciones debajo y el contenido de la sección, que hace scroll bajo la vista
// previa. Edita form.current (el diseño entero) y se guarda con la barra de siempre.

const SAMPLE_DATA = {
  weather: { temp: 19, icon: "sun", description: "Despejado", max: 22, min: 12, rain: 10 },
  forecast: {
    hours: [{ h: 15, temp: 20, icon: "sun" }, { h: 17, temp: 21, icon: "cloud-sun" }, { h: 19, temp: 19, icon: "cloud" }, { h: 21, temp: 17, icon: "cloud-rain" }, { h: 23, temp: 16, icon: "cloud" }],
    days: [{ d: "mié", min: 12, max: 23, icon: "cloud" }, { d: "jue", min: 10, max: 20, icon: "cloud-rain" }, { d: "vie", min: 10, max: 18, icon: "cloud-rain" }, { d: "sáb", min: 10, max: 20, icon: "cloud-sun" }, { d: "dom", min: 12, max: 21, icon: "sun" }],
  },
  next_alarm: { label: "Despertar", when: "mañana", time: "07:30" },
  // Ejemplo de cronómetro parado y de temporizadores en pausa (no corren en la vista previa).
  stopwatch: { running: false, base_ms: 754000, since: 0 },
  timers: [
    { id: "p1", label: "Pasta", paused: true, remaining_ms: 372000 },
    { id: "p2", label: "Horno", paused: true, remaining_ms: 1500000 },
  ],
};

// Secciones: [clave (data-sub), etiqueta, icono].
const SUBS = [["screen", "Elementos", "layers"], ["theme", "Tema", "image"], ["profiles", "Perfiles", "clock"], ["alarm", "Alarma", "bell"]];
// Icono de cada tipo de elemento en la lista (un tipo desconocido usa "layers").
const TYPE_ICONS = { clock: "clock", date: "layout-template", weather_now: "cloud-sun", forecast: "cloud-rain", next_alarm: "bell", stopwatch: "play", timers: "bell", photos: "image", text: "pencil" };
const PREVIEW_LOAD_MS = 8000; // si el motor no carga en este tiempo, se avisa
const dz = {
  sub: "screen", screenId: null, themeId: null, profileId: null, selected: null,
  frame: null, ready: false, data: SAMPLE_DATA, backgrounds: [], subEl: null,
  clockVersion: null, panelVersion: null, root: null, sticky: null, openProfiles: null,
};

// ------------------------------------------------------------------ widgets de formulario

const labeled = (label, input, cls = "") => h("label", { class: "field" + (cls ? " " + cls : "") }, h("span", {}, label), input);

const textField = (label, value, max, onInput) =>
  labeled(label, h("input", { type: "text", value, maxlength: max, autocomplete: "off", oninput: (ev) => onInput(ev.target.value) }));

const selectField = (label, options, value, onChange) =>
  labeled(label, h("select", { onchange: (ev) => onChange(ev.target.value) },
    options.map(([v, l]) => h("option", { value: v, selected: v === value }, l))));

const boolField = (label, value, onChange) => switchRow(label, value, onChange);

// Número con la unidad a la derecha del campo (s, %, …) si la hay.
function numberField(label, value, min, max, onChange, unit) {
  const input = h("input", { type: "number", min, max, step: "1", inputmode: "numeric", value,
    onchange: (ev) => onChange(ev.target.value === "" ? NaN : Number(ev.target.value)) });
  return labeled(label, unit ? h("span", { class: "unit-input" }, input, h("span", { class: "unit" }, unit)) : input);
}

// Fila de color: nombre a la izquierda, código y muestra (48 px) a la derecha; toda la fila abre el selector.
function colorField(label, value, onChange) {
  const hex = h("span", { class: "hex" }, String(value || "").toUpperCase());
  return h("label", { class: "color-field" }, h("span", { class: "color-name" }, label),
    hex,
    h("input", { type: "color", value, oninput: (ev) => { hex.textContent = ev.target.value.toUpperCase(); onChange(ev.target.value); } }));
}

// Deslizador con su valor (y unidad) visible a la derecha del título, en cifras tabulares.
function rangeField(label, val, min, max, step, onInput, unit = "", fmt = null) {
  const show = (v) => (fmt ? fmt(v) : String(v)) + (unit ? " " + unit : "");
  const out = h("output", { class: "range-val" }, show(val));
  const input = h("input", { type: "range", min, max, step, value: val, "aria-valuetext": show(val),
    oninput: (ev) => {
      const v = Number(ev.target.value);
      out.textContent = show(v);
      ev.target.setAttribute("aria-valuetext", show(v));
      onInput(v);
    } });
  return h("label", { class: "field range-field" }, h("span", { class: "range-head" }, h("span", {}, label), out), input);
}

// Tarjeta de un bloque con encabezado pequeño (icono y título) y, si hay, un dato a la derecha.
function block(iconName, title, aside, ...kids) {
  return h("section", { class: "card block" },
    h("h2", { class: "with-icon" }, iconName ? icon(iconName, 20) : null, h("span", {}, title),
      aside != null ? h("span", { class: "aside" }, aside) : null),
    ...kids);
}

const warnLine = (text) => warnBox(text);

// Actualiza el texto de la opción de un <select> mientras se escribe el nombre, sin repintar (no pierde el foco).
function setOptionText(select, value, text) {
  for (const o of select.options) if (o.value === value) o.textContent = text;
}

const themeSelect = (current, onChange) =>
  h("select", { onchange: (ev) => onChange(ev.target.value) },
    Object.keys(form.current.themes).map((id) => h("option", { value: id, selected: id === current }, form.current.themes[id].name)));

// ------------------------------------------------------------------ estado y vista previa

const currentRows = () => (dz.sub === "alarm" ? 4 : 6);
const currentElements = () =>
  dz.sub === "alarm" ? form.current.alarm_screen.elements : form.current.screens[dz.screenId].elements;

function touch() { updateSaveBar(); pushPreview(); }

function themeForPreview() {
  if (dz.sub === "theme") return dz.themeId;
  for (const p of form.current.profiles) if (p.screens.includes(dz.screenId)) return p.theme;
  return Object.keys(form.current.themes)[0];
}

function previewOpts() {
  const bgBase = "/api/bg/";
  if (dz.sub === "alarm") return { alarm: true, edit: true, selected: dz.selected, grid: true, bgBase };
  if (dz.sub === "profiles") return { profile: dz.profileId, bgBase };
  if (dz.sub === "theme") return { screen: dz.screenId, theme: dz.themeId, bgBase };
  return { screen: dz.screenId, theme: themeForPreview(), edit: true, selected: dz.selected, grid: true, bgBase };
}

// Tipos de elemento desconocidos (p. ej. de una versión más nueva del reloj) no deben romper el panel.
const typeInfo = (type) => EditorCore.TYPES[type] || { label: type, fields: [], defaults: {} };

function frameGone() { return !dz.frame || !dz.frame.isConnected || !dz.frame.contentWindow; }

function pushPreview() {
  if (frameGone()) return;
  if (!dz.ready || !form) return;
  dz.frame.contentWindow.postMessage({ type: "face-render", design: form.current, opts: previewOpts() }, location.origin);
}

function pushData() {
  if (frameGone()) return;
  if (!dz.ready) return;
  const data = Object.assign({}, dz.data, dz.sub === "alarm" ? { ringing_label: "Despertar" } : {});
  dz.frame.contentWindow.postMessage({ type: "face-data", data }, location.origin);
}

function fitFrame() {
  fitFaceFrame($("#facewrap"), dz.frame);
}
window.addEventListener("resize", fitFrame);

async function loadFaceData() {
  const r = await api("GET", "/api/face-data");
  dz.data = r.ok ? r.data : SAMPLE_DATA; // sin reloj, datos de ejemplo
  pushData();
}

window.addEventListener("message", (ev) => {
  if (ev.origin !== location.origin || route.tab !== "design" || !form) return;
  const m = ev.data;
  if (!m || typeof m !== "object") return;
  if (m.type === "face-select") selectElement(m.id);
  else if (m.type === "face-resize") resizeElement(m.id, m.w, m.h);
  else if (m.type === "face-move") moveElement(m.id, m.x, m.y);
  else if (m.type === "face-empty") { dz.selected = null; paintSub(); pushPreview(); }
});

/* Tarjeta de la vista previa. Si el motor no carga (404, 500, sin red o no llega `load` en
   8 s) se enseña un aviso con Reintentar en lugar del recuadro negro. */
function previewCard() {
  const wrap = h("div", { class: "face-wrap", id: "facewrap" });
  const src = "/face/index.html?preview=1";
  const frame = h("iframe", { class: "face-frame", src, scrolling: "no", title: "Vista previa del reloj" });
  let timer = null;
  const fallback = h("div", { class: "live-fallback", hidden: true, role: "alert" },
    h("div", { class: "live-msg" }, icon("alert-triangle", 20), h("span", {}, "No se pudo cargar la vista previa")),
    h("p", { class: "muted" }, "Puedes seguir editando y guardar; el reloj usará el diseño igualmente."),
    btn("Reintentar", () => start(true), "ghost small", "refresh-cw"));
  const showFallback = (on) => { wrap.hidden = on; fallback.hidden = !on; };
  const start = (reload) => {
    dz.ready = false;
    showFallback(false);
    clearTimeout(timer);
    timer = setTimeout(() => { if (!dz.ready && frame.isConnected) showFallback(true); }, PREVIEW_LOAD_MS);
    if (reload) frame.src = src;
  };
  frame.addEventListener("load", () => {
    clearTimeout(timer);
    // Con 404, 500 o sin red Chrome también dispara `load`: se comprueba que el motor esté vivo.
    let alive = false;
    try { alive = !!frame.contentWindow && typeof frame.contentWindow.Face === "object"; } catch { alive = false; }
    if (!alive) { dz.ready = false; showFallback(true); return; }
    dz.ready = true;
    showFallback(false);
    fitFrame(); pushPreview(); pushData();
  });
  dz.frame = frame;
  wrap.append(frame);
  start(false);
  if (window.ResizeObserver) new ResizeObserver(() => fitFrame()).observe(wrap);
  return h("div", { class: "card face-card" }, wrap, fallback);
}

// ------------------------------------------------------------------ teclado del móvil

/* Con el teclado abierto la vista previa fija se encoge a la mitad (sigue viéndose el cambio
   mientras se escribe) y el campo enfocado se coloca entre la vista previa y el borde de
   abajo de lo visible (visualViewport), nunca debajo de la vista previa ni del teclado. */
const kbd = { base: 0, width: 0 };
const TEXT_INPUTS = new Set(["text", "number", "time", "search", "password", "email", "url", "tel"]);
const typesText = (el) => !!el && (el.tagName === "TEXTAREA" || (el.tagName === "INPUT" && TEXT_INPUTS.has(el.type)));

function keyboardOpen() {
  const vv = window.visualViewport;
  if (!vv) return false;
  if (window.innerWidth !== kbd.width) { kbd.width = window.innerWidth; kbd.base = 0; }
  const seen = vv.height * vv.scale; // el zoom con dos dedos también encoge visualViewport
  kbd.base = Math.max(kbd.base, seen);
  return seen < kbd.base * 0.75 && typesText(document.activeElement);
}

function keepVisible(el) {
  if (!el || !dz.root || !dz.root.isConnected || !dz.root.contains(el) || el.closest(".face-sticky")) return;
  const vv = window.visualViewport;
  const vTop = vv ? vv.offsetTop : 0;
  const vH = vv ? vv.height : window.innerHeight;
  const stickyBottom = dz.sticky && dz.sticky.isConnected ? dz.sticky.getBoundingClientRect().bottom : 0;
  const top = Math.max(vTop, stickyBottom) + 8;
  let bottom = vTop + vH - 8;
  for (const id of ["savebar", "tabs"]) {
    const bar = document.getElementById(id);
    const r = bar && bar.getBoundingClientRect();
    if (r && r.height && r.top < bottom) bottom = Math.min(bottom, r.top - 8);
  }
  const r = el.getBoundingClientRect();
  if (r.top >= top && r.bottom <= bottom) return;
  // Centrado en el hueco libre; si no cabe entero, su parte de arriba justo bajo la vista previa.
  const want = r.height > bottom - top ? top : (top + bottom) / 2 - r.height / 2;
  window.scrollBy(0, r.top - want);
}

function onViewportChange() {
  if (!dz.root || !dz.root.isConnected || route.tab !== "design") { document.body.classList.remove("kbd-open"); return; }
  const open = keyboardOpen();
  document.body.classList.toggle("kbd-open", open);
  if (open) requestAnimationFrame(() => keepVisible(document.activeElement));
}
if (window.visualViewport) window.visualViewport.addEventListener("resize", onViewportChange);

function onDesignFocus(ev) {
  const el = ev.target;
  if (!(el instanceof HTMLElement) || !el.matches("input, select, textarea, button, [tabindex]")) return;
  requestAnimationFrame(() => keepVisible(el));
  // El teclado tarda en abrirse: se vuelve a mirar cuando ya ha cambiado el visualViewport.
  if (typesText(el)) setTimeout(() => { onViewportChange(); keepVisible(el); }, 350);
}

function onDesignBlur() {
  setTimeout(() => { if (!typesText(document.activeElement)) document.body.classList.remove("kbd-open"); }, 0);
}

// ------------------------------------------------------------------ entrada de la pestaña

async function renderDesign() {
  const [rd, rv, rb] = await Promise.all([api("GET", "/api/design"), api("GET", "/api/face-version"), api("GET", "/api/backgrounds")]);
  if (route.tab !== "design") return;
  if (!rd.ok) { showError(rd); return; }
  dz.backgrounds = rb.ok ? rb.data.backgrounds : [];
  bindTry();
  startForm(rd.data.design, () => api("PUT", "/api/design", form.current));
  dz.clockVersion = rd.data.face_version;
  dz.panelVersion = rv.ok ? rv.data.face_version : null;
  if (!dz.screenId || !form.current.screens[dz.screenId]) dz.screenId = Object.keys(form.current.screens)[0];
  if (!dz.themeId || !form.current.themes[dz.themeId]) dz.themeId = Object.keys(form.current.themes)[0];
  if (!dz.profileId || !form.current.profiles.some((p) => p.id === dz.profileId)) dz.profileId = form.current.profiles[0].id;
  dz.selected = null;
  dz.openProfiles = new Set([dz.profileId]);
  paintDesign();
  loadFaceData();
}

function paintDesign() {
  const warn = dz.clockVersion && dz.panelVersion && dz.clockVersion !== dz.panelVersion
    ? warnBox(`El reloj y el panel usan versiones distintas del motor (reloj ${dz.clockVersion}, panel ${dz.panelVersion}): la vista previa puede no ser idéntica. Actualiza la app del reloj o el panel.`)
    : null;
  const tabs = segmented(SUBS, dz.sub, switchSub);
  tabs.classList.add("sections");
  tabs.setAttribute("aria-label", "Secciones del editor de pantallas");
  for (const b of tabs.children) {
    b.dataset.sub = b.dataset.value;
    b.id = "dz-tab-" + b.dataset.value;
    b.setAttribute("aria-controls", "dz-panel");
  }
  dz.subEl = h("div", { id: "dz-panel", class: "design-body", role: "tabpanel" });
  dz.sticky = h("div", { class: "face-sticky" }, previewCard());
  dz.root = h("div", { class: "design", onfocusin: onDesignFocus, onfocusout: onDesignBlur,
    // Al escribir puede aparecer la barra de guardado: el campo no debe quedar debajo.
    oninput: (ev) => { if (document.body.classList.contains("kbd-open")) requestAnimationFrame(() => keepVisible(ev.target)); } },
    warn, dz.sticky, tabs, dz.subEl);
  document.body.classList.remove("kbd-open");
  keyboardOpen(); // anota la altura sin teclado como referencia
  setView(dz.root);
  paintSub();
  fitFrame();
}

function switchSub(k) {
  dz.sub = k;
  dz.selected = null;
  paintSub();
  pushPreview();
  pushData();
}

function paintSub() {
  if (!dz.subEl) return;
  const fn = { screen: subScreen, theme: subTheme, profiles: subProfiles, alarm: subAlarm }[dz.sub];
  dz.subEl.setAttribute("aria-labelledby", "dz-tab-" + dz.sub);
  // Al repintar se conserva el foco en el control equivalente (fila, botón de capa…).
  const had = document.activeElement;
  const key = had && dz.subEl.contains(had) ? had.dataset.focus : null;
  dz.subEl.replaceChildren(...fn().flat(Infinity).filter(Boolean));
  if (key) {
    let again = dz.subEl.querySelector(`[data-focus="${CSS.escape(key)}"]`);
    // Un botón de capa que se queda desactivado (ya está al frente) pasa el foco a otro de capa.
    if ((!again || again.disabled) && key.startsWith("layer:")) again = dz.subEl.querySelector('[data-focus^="layer:"]:not(:disabled)');
    if (again && !again.disabled) again.focus({ preventScroll: true });
  }
}

// ------------------------------------------------------------------ sección Elementos

function subScreen() {
  const d = form.current;
  const scr = d.screens[dz.screenId];
  const count = Object.keys(d.screens).length;
  const sel = h("select", { onchange: (ev) => { dz.screenId = ev.target.value; dz.selected = null; paintSub(); pushPreview(); } },
    Object.keys(d.screens).map((id) => h("option", { value: id, selected: id === dz.screenId }, d.screens[id].name)));
  return [
    block("layout-template", "Pantalla", `${count} de 8`,
      labeled("Pantalla que editas", sel),
      h("div", { class: "actions3" },
        btn("Nueva", addScreen, "ghost small", "plus", { "aria-label": "Nueva pantalla" }),
        btn("Duplicar", duplicateScreen, "ghost small", "copy", { "aria-label": "Duplicar pantalla" }),
        btn("Borrar", deleteScreen, "ghost small destructive", "trash-2", { "aria-label": "Borrar pantalla" })),
      textField("Nombre", scr.name, 30, (v) => { scr.name = v; setOptionText(sel, dz.screenId, v); updateSaveBar(); })),
    ...elementsBlocks(),
  ];
}

function addScreen() {
  const d = form.current;
  if (Object.keys(d.screens).length >= 8) { toast("Máximo 8 pantallas", true); return; }
  const id = EditorCore.uniqueId("pantalla", Object.keys(d.screens));
  d.screens[id] = { name: "Nueva pantalla", elements: [] };
  dz.screenId = id; dz.selected = null;
  touch(); paintSub();
}

function duplicateScreen() {
  const d = form.current;
  if (Object.keys(d.screens).length >= 8) { toast("Máximo 8 pantallas", true); return; }
  const src = d.screens[dz.screenId];
  const id = EditorCore.uniqueId(src.name, Object.keys(d.screens));
  d.screens[id] = { name: (src.name + " (copia)").slice(0, 30), elements: structuredClone(src.elements) };
  dz.screenId = id; dz.selected = null;
  touch(); paintSub();
}

async function deleteScreen() {
  const d = form.current;
  if (Object.keys(d.screens).length <= 1) { toast("Tiene que quedar al menos una pantalla", true); return; }
  const users = d.profiles.filter((p) => p.screens.includes(dz.screenId)).map((p) => p.name);
  if (users.length) { toast("La usan los perfiles: " + users.join(", "), true); return; }
  if (!(await confirmInPage("¿Borrar esta pantalla?", "Borrar"))) return;
  delete d.screens[dz.screenId];
  dz.screenId = Object.keys(d.screens)[0]; dz.selected = null;
  touch(); paintSub();
}

// ------------------------------------------------------------------ lista y opciones de elementos

// Lista de delante hacia atrás (el último del diseño se dibuja delante), añadir y, si hay uno
// elegido, sus opciones en una tarjeta aparte.
function elementsBlocks() {
  const list = currentElements();
  const sel = list.find((e) => e.id === dz.selected);
  const n = list.length;
  const rows = list.slice().reverse().map((e, i) => {
    const on = e.id === dz.selected;
    const info = typeInfo(e.type);
    const place = n > 1 && i === 0 ? ["chevrons-up", "Delante"] : n > 1 && i === n - 1 ? ["chevrons-down", "Al fondo"] : null;
    const showId = e.id.toLowerCase() !== String(info.label).toLowerCase();
    return h("button", { type: "button", class: "elrow" + (on ? " on" : ""), "aria-pressed": on ? "true" : "false", "data-focus": "row:" + e.id, onclick: () => selectElement(e.id) },
      h("span", { class: "el-ico" }, icon(TYPE_ICONS[e.type] || "layers")),
      h("span", { class: "el-text" },
        h("span", { class: "el-name" }, info.label, showId ? h("span", { class: "el-id" }, " · " + e.id) : null),
        h("span", { class: "el-meta" }, `${e.w}×${e.h} · columna ${e.x}, fila ${e.y}`)),
      place ? h("span", { class: "layer-chip" }, icon(place[0], 14), h("span", {}, place[1])) : null);
  });
  const T = EditorCore.TYPES;
  const typeSel = h("select", { "aria-label": "Tipo de elemento" }, Object.keys(T).map((t) => h("option", { value: t }, T[t].label)));
  return [
    block("layers", "De delante hacia atrás", `${n} de 12`,
      h("p", { class: "muted hint" }, "Toca un elemento en la vista previa o en la lista; vuelve a tocar el mismo sitio para elegir el de debajo. Arrastra su esquina azul para cambiar el tamaño."),
      n ? h("div", { class: "ellist" }, rows) : emptyState("layers", "Esta pantalla no tiene elementos. Añade el primero."),
      h("div", { class: "add-row" }, typeSel, btn("Añadir", () => addElement(typeSel.value), "", "plus", { "aria-label": "Añadir elemento", "data-focus": "add" }))),
    sel ? elementOptions(sel) : null,
  ];
}

function addElement(type) {
  const list = currentElements();
  const rows = currentRows();
  if (list.length >= 12) { toast("Máximo 12 elementos por pantalla", true); return; }
  const t = EditorCore.TYPES[type];
  const size = { w: t.w, h: Math.min(t.h, rows) };
  let spot = EditorCore.findFreeSpot(list, size.w, size.h, rows);
  if (!spot) spot = { x: 0, y: 0 }; // sin hueco libre: se coloca encima de lo que haya
  const id = EditorCore.uniqueId(t.label, list.map((e) => e.id));
  list.push(EditorCore.newElement(type, id, spot, size));
  if (type === "photos") list[list.length - 1].images = dz.backgrounds.slice(0, 1).map((b) => b.name);
  dz.selected = id;
  touch(); paintSub();
}

function selectElement(id) {
  dz.selected = id;
  paintSub();
  pushPreview();
}

function setRect(e, patch) {
  const cand = Object.assign({ x: e.x, y: e.y, w: e.w, h: e.h }, patch);
  if (!EditorCore.inBounds(cand, currentRows())) { toast("Se sale de la rejilla", true); return false; }
  Object.assign(e, patch);
  touch();
  paintSub(); // los números ya salieron del foco (onchange): se refresca la lista
  return true;
}

function resizeElement(id, w, hh) {
  const e = currentElements().find((x) => x.id === id);
  if (!e) return;
  const cand = { x: e.x, y: e.y, w, h: hh };
  if (EditorCore.inBounds(cand, currentRows())) {
    e.w = w; e.h = hh; touch();
  } else {
    toast("Se sale de la rejilla", true);
    pushPreview();
  }
  paintSub();
}

function moveElement(id, x, y) {
  if (!Number.isInteger(x) || !Number.isInteger(y)) { pushPreview(); return; }
  const e = currentElements().find((it) => it.id === id);
  if (!e) return;
  dz.selected = id;
  const cand = { x, y, w: e.w, h: e.h };
  if (EditorCore.inBounds(cand, currentRows())) {
    e.x = x; e.y = y; touch();
  } else {
    toast("Se sale de la rejilla", true);
    pushPreview();
  }
  paintSub();
}

function typedField(e, f) {
  const def = typeInfo(e.type).defaults[f.key];
  const val = e[f.key] === undefined ? def : e[f.key];
  if (f.when && (e[f.when.key] === undefined ? typeInfo(e.type).defaults[f.when.key] : e[f.when.key]) !== f.when.value) return null;
  if (f.kind === "select") return selectField(f.label, f.options, val, (v) => { e[f.key] = v; touch(); paintSub(); });
  if (f.kind === "bool") return boolField(f.label, val, (v) => { e[f.key] = v; touch(); });
  if (f.kind === "number") return numberField(f.label, val, f.min, f.max, (v) => { e[f.key] = v; touch(); });
  if (f.kind === "text") return textField(f.label, val || "", f.max, (v) => { e[f.key] = v; touch(); });
  if (f.kind === "lines") {
    return labeled(f.label, h("textarea", { rows: 5, oninput: (ev) => { e[f.key] = ev.target.value.split("\n").map((s) => s.trim()).filter(Boolean).slice(0, 20); touch(); } },
      (e[f.key] || []).join("\n")));
  }
  if (f.kind === "photofit") return h("div", { class: "subblock" }, h("h3", { class: "sub-head" }, "Encaje de la foto"), photoFitBlock(e));
  if (f.kind === "photolist") return h("div", { class: "subblock" }, photoListField(e, f));
  return null;
}

/* Miniaturas de las fotos del reloj con casilla (48 px de área táctil), aviso de las que
   faltan y botón para subir más. `spec[f.key]` es la lista elegida, en orden. */
function photoListField(spec, f) {
  const chosen = spec[f.key] || [];
  const known = new Set(dz.backgrounds.map((b) => b.name));
  const file = h("input", { type: "file", accept: "image/jpeg,image/png,image/webp", multiple: true, hidden: true,
    onchange: async (ev) => {
      const input = ev.target;
      const files = Array.from(input.files);
      input.value = "";
      for (const one of files) {
        if ((spec[f.key] || []).length >= 20) { toast("Máximo 20 fotos por carrusel", true); break; }
        const name = await uploadPhoto(one);
        if (name && !(spec[f.key] || []).includes(name)) spec[f.key] = (spec[f.key] || []).concat([name]);
      }
      touch(); paintSub();
    } });
  const thumbs = dz.backgrounds.map((b) => {
    const pos = chosen.indexOf(b.name);
    const on = pos >= 0;
    return h("label", { class: "thumb" + (on ? " on" : ""), title: b.name },
      h("img", { src: "/api/bg/" + encodeURIComponent(b.name), alt: "", loading: "lazy" }),
      on ? h("span", { class: "thumb-n", "aria-hidden": "true" }, String(pos + 1)) : null,
      h("input", { type: "checkbox", checked: on, "aria-label": `Usar ${b.name}` + (on ? ` (foto ${pos + 1})` : ""), onchange: (ev) => {
        const cur = (spec[f.key] || []).filter((n) => n !== b.name);
        if (ev.target.checked) {
          if (cur.length >= 20) { toast("Máximo 20 fotos por carrusel", true); ev.target.checked = false; return; }
          cur.push(b.name);
        }
        spec[f.key] = cur; touch(); paintSub();
      } }));
  });
  const missing = chosen.filter((n) => !known.has(n));
  return h("div", { class: "photo-list" },
    h("h3", { class: "sub-head" }, h("span", {}, f.label), h("span", { class: "aside" }, `${chosen.length} elegidas`)),
    dz.backgrounds.length ? h("div", { class: "thumbs" }, thumbs) : h("p", { class: "muted" }, "No hay fotos en el reloj: sube alguna."),
    missing.length ? warnLine("Estas fotos ya no están en el reloj y se saltarán:") : null,
    missing.map((n) => h("div", { class: "file-row" }, h("span", { class: "file-name" }, n),
      btn("Quitar", () => { spec[f.key] = (spec[f.key] || []).filter((x) => x !== n); touch(); paintSub(); }, "ghost small", "x", { "aria-label": `Quitar ${n}` }))),
    !chosen.length ? warnLine("Elige al menos una foto: sin ellas el diseño no se puede guardar.") : null,
    h("div", { class: "row" }, btn("Subir fotos…", () => file.click(), "ghost small", "upload"), file));
}

function reorder(fn) {
  const list = currentElements();
  const e = list.find((x) => x.id === dz.selected);
  if (!e) return;
  const next = fn(list, e.id);
  list.splice(0, list.length, ...next); // se reordena la misma lista del diseño
  touch(); paintSub();
}

function elementOptions(e) {
  const rows = currentRows();
  const els = currentElements();
  const n = els.length;
  const idx = els.indexOf(e);
  const isLast = idx === n - 1, isFirst = idx === 0, solo = n < 2;
  const info = typeInfo(e.type);
  // Botón de capa: icono y texto corto visibles; el nombre accesible completo contiene ese texto.
  const lbtn = (ic, short, full, fn, off) => {
    const b = btn(short, () => reorder(fn), "ghost layer-btn", ic, { "aria-label": full, title: full, "data-focus": "layer:" + ic });
    if (off) b.disabled = true;
    return b;
  };
  const pos = (key, label, min, max) => numberField(label, e[key], min, max, (v) => { if (!setRect(e, { [key]: v })) paintSub(); });
  const fields = info.fields.map((f) => typedField(e, f)).filter(Boolean);
  return h("section", { class: "card block el-opts", "aria-label": `Opciones de ${info.label}` },
    h("div", { class: "block-head" },
      h("span", { class: "el-ico" }, icon(TYPE_ICONS[e.type] || "layers")),
      h("div", { class: "block-title" }, h("h2", {}, info.label),
        e.id.toLowerCase() !== String(info.label).toLowerCase() ? h("span", { class: "muted" }, e.id) : null),
      iconButton("x", "Cerrar las opciones del elemento", () => selectElement(null))),
    h("h3", { class: "sub-head" }, h("span", {}, "Capa"), h("span", { class: "aside" }, solo ? "única" : `${n - idx} de ${n} desde delante`)),
    h("div", { class: "layers" },
      lbtn("chevrons-up", "Al frente", "Traer al frente", EditorCore.bringToFront, solo || isLast),
      lbtn("arrow-up", "Subir", "Subir una capa", EditorCore.moveUp, solo || isLast),
      lbtn("arrow-down", "Bajar", "Bajar una capa", EditorCore.moveDown, solo || isFirst),
      lbtn("chevrons-down", "Al fondo", "Enviar al fondo", EditorCore.sendToBack, solo || isFirst)),
    h("h3", { class: "sub-head" }, h("span", {}, "Posición y tamaño"), h("span", { class: "aside" }, `rejilla de 8×${rows}`)),
    h("div", { class: "grid4" }, pos("x", "Columna", 0, 7), pos("y", "Fila", 0, rows - 1), pos("w", "Ancho", 1, 8), pos("h", "Alto", 1, rows)),
    h("div", { class: "grid2" },
      selectField("Color", EditorCore.COLOR_OPTIONS, e.color || "fg", (v) => { e.color = v; touch(); }),
      selectField("Alineación", [["left", "Izquierda"], ["center", "Centro"], ["right", "Derecha"]], e.align || "center", (v) => { e.align = v; touch(); })),
    fields.length ? h("h3", { class: "sub-head" }, h("span", {}, "Contenido")) : null,
    fields,
    h("div", { class: "block-foot" }, btn("Borrar elemento", () => {
      const list = currentElements();
      // "Descartar lo recupera" solo es cierto si el elemento ya estaba en el diseño guardado.
      const saved = dz.sub === "alarm" ? form.original.alarm_screen : form.original.screens[dz.screenId];
      const wasSaved = !!saved && saved.elements.some((x) => x.id === e.id);
      list.splice(list.indexOf(e), 1);
      dz.selected = null;
      touch(); paintSub();
      if (wasSaved && dirty()) toast("Elemento borrado. Descartar lo recupera.");
    }, "danger", "trash-2")));
}

// ------------------------------------------------------------------ sección Alarma

function subAlarm() {
  const a = form.current.alarm_screen;
  return [
    block("bell", "Pantalla de alarma sonando", null,
      h("p", { class: "muted hint" }, "Diseñas la zona de arriba (rejilla de 8×4). Los botones de abajo son del reloj y funcionan siempre."),
      labeled("Tema", themeSelect(a.theme, (v) => { a.theme = v; touch(); })),
      textField("Texto de Posponer (usa {min} para los minutos)", a.snooze_label === undefined ? "Posponer {min} min" : a.snooze_label, 20, (v) => { a.snooze_label = v; touch(); }),
      textField("Texto de Descartar", a.dismiss_label === undefined ? "Descartar" : a.dismiss_label, 20, (v) => { a.dismiss_label = v; touch(); })),
    ...elementsBlocks(),
  ];
}

// ------------------------------------------------------------------ Probar en el reloj

function bindTry() {
  if (dz.tryBound) return;
  dz.tryBound = true;
  $("#try").addEventListener("click", async () => {
    if (route.tab !== "design" || !form) return;
    const b = $("#try");
    b.disabled = true; b.setAttribute("aria-busy", "true");
    const r = await api("POST", "/api/design/preview", { design: form.current, seconds: 30 });
    b.disabled = false; b.removeAttribute("aria-busy");
    if (r.ok) { toast("Mostrándose 30 s en el reloj"); return; }
    const detail = errorDetail(r.data.errors);
    toast(errText(r) + (detail ? " — " + detail : ""), true);
  });
}

// ------------------------------------------------------------------ sección Tema

const FONT_OPTIONS = [["inter", "Inter (fino, recomendado)"], ["thin", "Fina"], ["normal", "Normal"], ["bold", "Negrita"], ["mono", "Monoespaciada"], ["digital", "Digital (7 segmentos)"]];
const BG_TYPES = [["color", "Color liso"], ["gradient", "Degradado"], ["image", "Imagen"], ["photos", "Carrusel de fotos"]];

function subTheme() {
  const t = form.current.themes[dz.themeId];
  const count = Object.keys(form.current.themes).length;
  const themeSel = themeSelect(dz.themeId, (v) => { dz.themeId = v; paintSub(); pushPreview(); });
  return [
    block("image", "Tema", `${count} de 12`,
      labeled("Tema que editas", themeSel),
      h("div", { class: "actions2" },
        btn("Duplicar", duplicateTheme, "ghost small", "copy", { "aria-label": "Duplicar tema" }),
        btn("Borrar", deleteTheme, "ghost small destructive", "trash-2", { "aria-label": "Borrar tema" })),
      textField("Nombre", t.name, 30, (v) => { t.name = v; setOptionText(themeSel, dz.themeId, v); updateSaveBar(); })),
    block("pencil", "Letra", null,
      selectField("Letra de la hora y los textos", FONT_OPTIONS, t.font, (v) => { t.font = v; touch(); })),
    block("sun", "Colores", null,
      h("div", { class: "color-list" },
        colorField("Principal", t.fg, (v) => { t.fg = v; touch(); }),
        colorField("Secundario", t.muted, (v) => { t.muted = v; touch(); }),
        colorField("Acento", t.accent, (v) => { t.accent = v; touch(); }))),
    ...bgBlocks(t),
    block("rotate-ccw", "Diseño inicial", null,
      h("p", { class: "muted hint" }, "Vuelve al diseño que traía el reloj (borra el que tienes guardado)."),
      btn("Restaurar diseño inicial", restoreInitial, "ghost wide", "rotate-ccw")),
  ];
}

function bgBlocks(t) {
  const bg = t.bg;
  const kind = selectField("Tipo de fondo", BG_TYPES, bg.type, (v) => {
    const base = bg.color || bg.from || "#000000";
    if (v === "color") t.bg = { type: "color", color: base };
    else if (v === "gradient") t.bg = { type: "gradient", from: base, to: bg.to || "#ffffff", angle: bg.angle === undefined ? 160 : bg.angle };
    else if (v === "photos") t.bg = { type: "photos", images: dz.backgrounds.slice(0, 1).map((b) => b.name), interval_s: 10,
      transition: "fade", order: "sequential", fit: "cover", zoom: 1, pos_x: 50, pos_y: 50, dim: 0, color: base };
    else t.bg = { type: "image", image: dz.backgrounds.length ? dz.backgrounds[0].name : "", dim: 30, color: base };
    touch(); paintSub();
  });
  if (bg.type === "color") return [block("layers", "Fondo", null, kind, h("div", { class: "color-list" }, colorField("Color", bg.color, (v) => { bg.color = v; touch(); })))];
  if (bg.type === "gradient") {
    return [block("layers", "Fondo", null, kind,
      h("div", { class: "color-list" },
        colorField("Desde", bg.from, (v) => { bg.from = v; touch(); }),
        colorField("Hasta", bg.to, (v) => { bg.to = v; touch(); })),
      rangeField("Ángulo", bg.angle === undefined ? 160 : bg.angle, 0, 359, 1, (v) => { bg.angle = v; touch(); }, "grados"))];
  }
  if (bg.type === "photos") {
    return [
      block("layers", "Fondo", null, kind,
        numberField("Segundos entre fotos (3–600)", bg.interval_s === undefined ? 10 : bg.interval_s, 3, 600, (v) => { bg.interval_s = v; touch(); }, "s"),
        h("div", { class: "grid2" },
          selectField("Transición", [["fade", "Fundido"], ["none", "Corte directo"]], bg.transition || "fade", (v) => { bg.transition = v; touch(); }),
          selectField("Orden", [["sequential", "En orden"], ["random", "Al azar"]], bg.order || "sequential", (v) => { bg.order = v; touch(); }))),
      h("section", { class: "card block" }, photoListField(bg, { key: "images", label: "Fotos del carrusel" })),
      block("image", "Encaje de las fotos", null,
        photoFitBlock(bg),
        rangeField("Oscurecer las fotos", bg.dim === undefined ? 0 : bg.dim, 0, 80, 1, (v) => { bg.dim = v; touch(); }, "%"),
        h("div", { class: "color-list" }, colorField("Color si falta la foto", bg.color || "#000000", (v) => { bg.color = v; touch(); }))),
    ];
  }
  return [
    block("layers", "Fondo", null, kind),
    imagePicker(bg),
  ];
}

async function refreshBackgrounds() {
  const r = await api("GET", "/api/backgrounds");
  dz.backgrounds = r.ok ? r.data.backgrounds : [];
}

function loadBitmap(file) {
  if (window.createImageBitmap) return createImageBitmap(file);
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => { URL.revokeObjectURL(url); resolve(img); };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error("no se pudo leer la imagen")); };
    img.src = url;
  });
}

function canvasBlob(canvas, type, quality) {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}

const BLOB_EXT = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" };
const MAX_UPLOAD = 8 * 1024 * 1024;

/* Reduce la foto en el navegador (lado largo 1600) y la sube. Si no se puede decodificar,
   la manda tal cual si pesa menos de 8 MB. Devuelve el nombre guardado en el reloj o null. */
async function uploadPhoto(file) {
  toast("Preparando la foto…");
  let blob = file;
  let name = file.name;
  try {
    const bmp = await loadBitmap(file);
    try {
      const size = EditorCore.downscaleSize(bmp.width, bmp.height, EditorCore.PHOTO_SIDE);
      if (size.w !== bmp.width || size.h !== bmp.height || file.size > MAX_UPLOAD) {
        const canvas = document.createElement("canvas");
        canvas.width = size.w; canvas.height = size.h;
        canvas.getContext("2d").drawImage(bmp, 0, 0, size.w, size.h);
        const want = file.type === "image/png" ? "image/png" : file.type === "image/webp" ? "image/webp" : "image/jpeg";
        let out = await canvasBlob(canvas, want, 0.85);
        if (out && out.size > MAX_UPLOAD && want !== "image/jpeg") out = await canvasBlob(canvas, "image/jpeg", 0.85);
        if (out && BLOB_EXT[out.type]) {
          blob = out;
          name = name.replace(/\.[^.]*$/, "") + "." + BLOB_EXT[out.type];
        }
      }
    } finally { if (bmp.close) bmp.close(); }
  } catch (err) {
    if (file.size > MAX_UPLOAD) { toast("No se pudo leer la imagen y supera 8 MB", true); return null; }
  }
  if (blob.size > MAX_UPLOAD) { toast("La imagen supera 8 MB", true); return null; }
  toast("Subiendo…");
  const r = await api("POST", `/api/backgrounds?name=${encodeURIComponent(name)}`, undefined, blob);
  if (!r.ok) { toast(errText(r), true); return null; }
  toast("Foto subida al reloj");
  await refreshBackgrounds();
  return r.data.name;
}

/* Encaje, zoom y posición de una foto (fondo de tema o carrusel); `spec` es el objeto del diseño. */
function photoFitBlock(spec) {
  return h("div", { class: "fit-block" },
    selectField("Encaje", [["cover", "Rellenar (recorta lo que sobra)"], ["contain", "Entera (con bandas)"], ["fill", "Estirar"]],
      spec.fit || "cover", (v) => { spec.fit = v; touch(); }),
    rangeField("Zoom", spec.zoom === undefined ? 1 : spec.zoom, 1, 3, 0.1, (v) => { spec.zoom = v; touch(); }, "",
      (v) => v.toFixed(1).replace(".", ",") + "×"),
    rangeField("Posición horizontal", spec.pos_x === undefined ? 50 : spec.pos_x, 0, 100, 1, (v) => { spec.pos_x = v; touch(); }, "%"),
    rangeField("Posición vertical", spec.pos_y === undefined ? 50 : spec.pos_y, 0, 100, 1, (v) => { spec.pos_y = v; touch(); }, "%"));
}

function imagePicker(bg) {
  const known = new Set(dz.backgrounds.map((b) => b.name));
  const select = h("select", { onchange: (ev) => { bg.image = ev.target.value; touch(); paintSub(); } },
    h("option", { value: "", selected: !bg.image }, "(ninguna)"),
    bg.image && !known.has(bg.image) ? h("option", { value: bg.image, selected: true }, `(falta: ${bg.image})`) : null,
    dz.backgrounds.map((b) => h("option", { value: b.name, selected: b.name === bg.image }, `${b.name} · ${Math.round(b.size / 1024)} KB`)));
  const file = h("input", { type: "file", accept: "image/jpeg,image/png,image/webp", hidden: true,
    onchange: async (ev) => {
      const input = ev.target;
      const f = input.files[0];
      input.value = "";
      if (!f) return;
      const name = await uploadPhoto(f);
      if (!name) return;
      bg.image = name;
      touch(); paintSub();
    } });
  return [
    block("image", "Imagen", null,
      labeled("Imagen de fondo", select),
      !bg.image ? h("p", { class: "muted hint" }, "Sube una imagen para usar este fondo.") : null,
      bg.image && !known.has(bg.image) ? warnLine("Esa imagen ya no está en el reloj: se verá el color de respaldo.") : null,
      h("div", { class: "row" }, btn("Subir imagen…", () => file.click(), "ghost small", "upload"), file),
      bg.image ? h("div", { class: "subblock" }, h("h3", { class: "sub-head" }, "Encaje de la imagen"), photoFitBlock(bg)) : null,
      rangeField("Oscurecer la imagen", bg.dim === undefined ? 0 : bg.dim, 0, 80, 1, (v) => { bg.dim = v; touch(); }, "%"),
      h("div", { class: "color-list" }, colorField("Color si falta la imagen", bg.color || "#000000", (v) => { bg.color = v; touch(); }))),
    dz.backgrounds.length ? block("upload", "Fotos guardadas en el reloj", `${dz.backgrounds.length}`,
      dz.backgrounds.map((b) => h("div", { class: "file-row" },
        h("span", { class: "file-name" }, b.name, h("span", { class: "muted" }, ` · ${Math.round(b.size / 1024)} KB`)),
        iconButton("trash-2", `Borrar ${b.name}`, () => deleteBackground(b.name), "ghost destructive")))) : null,
  ];
}

async function deleteBackground(name) {
  const used = Object.values(form.current.themes).filter((t) => t.bg.type === "image" && t.bg.image === name).map((t) => t.name);
  const bgCarousels = Object.values(form.current.themes).filter((t) => t.bg.type === "photos" && (t.bg.images || []).includes(name)).map((t) => t.name);
  const carousels = [];
  const scanPhotos = (label, els) => (els || []).forEach((el) => { if (el.type === "photos" && (el.images || []).includes(name)) carousels.push(`${label} (${el.id})`); });
  Object.values(form.current.screens).forEach((sc) => scanPhotos(sc.name, sc.elements));
  scanPhotos("pantalla de alarma", form.current.alarm_screen && form.current.alarm_screen.elements);
  let msg = `¿Borrar ${name}?`;
  if (used.length) msg += ` Lo usan los temas: ${used.join(", ")}. Se verá su color de respaldo.`;
  if (bgCarousels.length) msg += ` Lo usan los carruseles de fondo de: ${bgCarousels.join(", ")}. Se saltará su foto.`;
  if (carousels.length) msg += ` Lo usan los carruseles de las pantallas: ${carousels.join(", ")}. Se saltará su foto.`;
  if (!(await confirmInPage(msg, "Borrar"))) return;
  const r = await api("DELETE", `/api/backgrounds/${encodeURIComponent(name)}`);
  toast(r.ok ? "Imagen borrada" : errText(r), !r.ok);
  await refreshBackgrounds();
  paintSub(); pushPreview();
}

function duplicateTheme() {
  const d = form.current;
  if (Object.keys(d.themes).length >= 12) { toast("Máximo 12 temas", true); return; }
  const src = d.themes[dz.themeId];
  const id = EditorCore.uniqueId(src.name, Object.keys(d.themes));
  d.themes[id] = Object.assign(structuredClone(src), { name: (src.name + " (copia)").slice(0, 30) });
  dz.themeId = id;
  touch(); paintSub();
}

async function deleteTheme() {
  const d = form.current;
  if (Object.keys(d.themes).length <= 1) { toast("Tiene que quedar al menos un tema", true); return; }
  const users = d.profiles.filter((p) => p.theme === dz.themeId).map((p) => p.name);
  if (d.alarm_screen.theme === dz.themeId) users.push("pantalla de alarma");
  if (users.length) { toast("Lo usan: " + users.join(", "), true); return; }
  if (!(await confirmInPage("¿Borrar este tema?", "Borrar"))) return;
  delete d.themes[dz.themeId];
  dz.themeId = Object.keys(d.themes)[0];
  touch(); paintSub();
}

async function restoreInitial() {
  if (!(await confirmInPage("Se borrará el diseño guardado y volverá el inicial. ¿Seguir?", "Restaurar"))) return;
  const r = await api("POST", "/api/design/reset");
  if (!r.ok) { toast(errText(r), true); return; }
  toast("Diseño inicial restaurado");
  form = null; updateSaveBar();
  renderDesign();
}

// ------------------------------------------------------------------ sección Perfiles

const ROTATE_OPTIONS = [[0, "Solo deslizando"], [10, "Cada 10 s"], [30, "Cada 30 s"], [60, "Cada minuto"], [300, "Cada 5 minutos"]];

function subProfiles() {
  const n = form.current.profiles.length;
  return [
    h("p", { class: "muted list-head" }, "Cada perfil empieza a su hora y dura hasta el siguiente. Abre uno para verlo en la vista previa."),
    ...form.current.profiles.map(profileCard),
    btn("Nuevo perfil", addProfile, "ghost wide add-wide", "plus", { "aria-label": `Nuevo perfil (${n} de 4)` }),
  ];
}

// Marca qué perfil se ve en la vista previa sin repintar (los perfiles abiertos siguen abiertos).
function markPreviewProfile() {
  if (!dz.subEl) return;
  dz.subEl.querySelectorAll("[data-profile]").forEach((card) => {
    const on = card.dataset.profile === dz.profileId;
    card.classList.toggle("ring", on);
    card.querySelector(".preview-chip").hidden = !on;
    card.querySelector(".see-btn").hidden = on;
  });
}

function showProfile(p) {
  dz.profileId = p.id;
  markPreviewProfile();
  pushPreview();
}

function profileCard(p) {
  const d = form.current;
  const rotate = p.rotate_s === undefined ? 0 : p.rotate_s;
  const options = ROTATE_OPTIONS.some(([v]) => v === rotate) ? ROTATE_OPTIONS : [...ROTATE_OPTIONS, [rotate, `Cada ${rotate} s`]];
  const current = p.id === dz.profileId;
  const name = h("span", { class: "p-name" }, p.name);
  const from = h("span", { class: "hint" }, `desde ${p.from}`);
  const card = h("details", {
    class: "card profile" + (current ? " ring" : ""), "data-profile": p.id, open: dz.openProfiles.has(p.id) || null,
    ontoggle: () => {
      // `toggle` también llega al pintar un perfil ya abierto: solo cuenta si cambia algo.
      if (card.open === dz.openProfiles.has(p.id)) return;
      if (card.open) { dz.openProfiles.add(p.id); showProfile(p); }
      else dz.openProfiles.delete(p.id);
    },
  },
  h("summary", {}, icon("clock"),
    h("span", { class: "p-title" }, name, from),
    h("span", { class: "preview-chip", hidden: !current || null }, icon("layout-template", 14), h("span", {}, "En la vista previa")),
    h("span", { class: "chev" }, icon("chevron-down"))),
  h("div", { class: "opts-body" },
    btn("Ver en la vista previa", () => showProfile(p), "ghost small see-btn", "layout-template", { hidden: current || null }),
    textField("Nombre", p.name, 30, (v) => { p.name = v; name.textContent = v; updateSaveBar(); }),
    labeled("Empieza a las", h("input", { type: "time", value: p.from, onchange: (ev) => { p.from = ev.target.value; from.textContent = `desde ${p.from}`; touch(); } })),
    labeled("Tema", themeSelect(p.theme, (v) => { p.theme = v; touch(); })),
    h("div", { class: "field" },
      h("span", {}, "Pantallas de este perfil"),
      h("div", { class: "switch-list" },
        Object.keys(d.screens).map((id) => boolField(d.screens[id].name, p.screens.includes(id), (on) => {
          const ids = Object.keys(d.screens).filter((x) => (x === id ? on : p.screens.includes(x)));
          if (!ids.length) { toast("Cada perfil necesita al menos una pantalla", true); paintSub(); return; }
          p.screens = ids;
          touch();
        })))),
    selectField("Cambio de pantalla", options.map(([v, l]) => [String(v), l]), String(rotate), (v) => { p.rotate_s = Number(v); touch(); }),
    rangeField("Brillo", p.brightness === undefined ? 70 : p.brightness, 1, 100, 1, (v) => { p.brightness = v; touch(); }, "%"),
    h("div", { class: "switch-list" },
      boolField("Modo noche (se atenúa y tocar la pantalla la ilumina un momento)", p.night, (v) => { p.night = v; touch(); }),
      boolField("Mostrar la barra de botones", p.show_bar !== false, (v) => { p.show_bar = v; touch(); })),
    d.profiles.length > 1 ? h("div", { class: "block-foot" }, btn("Borrar perfil", () => deleteProfile(p), "danger", "trash-2")) : null));
  return card;
}

function freeHour() {
  const used = new Set(form.current.profiles.map((p) => p.from));
  for (let hr = 0; hr < 24; hr++) {
    const s = String(hr).padStart(2, "0") + ":00";
    if (!used.has(s)) return s;
  }
  return "12:30";
}

function addProfile() {
  const d = form.current;
  if (d.profiles.length >= 4) { toast("Máximo 4 perfiles", true); return; }
  const id = EditorCore.uniqueId("perfil", d.profiles.map((p) => p.id));
  d.profiles.push({
    id, name: "Nuevo perfil", from: freeHour(), theme: Object.keys(d.themes)[0],
    screens: [Object.keys(d.screens)[0]], rotate_s: 0, brightness: 70, night: false, show_bar: true,
  });
  dz.profileId = id;
  dz.openProfiles.add(id);
  touch(); paintSub();
}

async function deleteProfile(p) {
  const d = form.current;
  if (d.profiles.length <= 1) { toast("Tiene que quedar al menos un perfil", true); return; }
  if (!(await confirmInPage(`¿Borrar el perfil «${p.name}»?`, "Borrar"))) return;
  d.profiles.splice(d.profiles.indexOf(p), 1);
  dz.openProfiles.delete(p.id);
  if (dz.profileId === p.id) dz.profileId = d.profiles[0].id;
  touch(); paintSub();
}
