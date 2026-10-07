"use strict";
// Pestaña Pantallas: vista previa fija arriba (el mismo motor que el reloj, en un iframe) y
// sub-pestañas debajo. Edita form.current (el diseño entero) y se guarda con la barra de siempre.

const SAMPLE_DATA = {
  weather: { temp: 19, icon: "☀", description: "Despejado", max: 22, min: 12, rain: 10 },
  forecast: {
    hours: [{ h: 15, temp: 20, icon: "☀" }, { h: 17, temp: 21, icon: "⛅" }, { h: 19, temp: 19, icon: "☁" }, { h: 21, temp: 17, icon: "☂" }, { h: 23, temp: 16, icon: "☁" }],
    days: [{ d: "mié", min: 12, max: 23, icon: "☁" }, { d: "jue", min: 10, max: 20, icon: "☂" }, { d: "vie", min: 10, max: 18, icon: "☂" }, { d: "sáb", min: 10, max: 20, icon: "⛅" }, { d: "dom", min: 12, max: 21, icon: "☀" }],
  },
  next_alarm: { label: "Despertar", when: "mañana", time: "07:30" },
};

const SUBS = [["screen", "Pantalla"], ["theme", "Tema"], ["profiles", "Perfiles"], ["alarm", "Alarma"]];
const dz = {
  sub: "screen", screenId: null, themeId: null, profileId: null, selected: null,
  frame: null, ready: false, data: SAMPLE_DATA, backgrounds: [], subEl: null,
  clockVersion: null, panelVersion: null,
};

// ------------------------------------------------------------------ widgets de formulario

const labeled = (label, input) => h("label", { class: "field" }, h("span", {}, label), input);

const textField = (label, value, max, onInput) =>
  labeled(label, h("input", { type: "text", value, maxlength: max, oninput: (ev) => onInput(ev.target.value) }));

const selectField = (label, options, value, onChange) =>
  labeled(label, h("select", { onchange: (ev) => onChange(ev.target.value) },
    options.map(([v, l]) => h("option", { value: v, selected: v === value }, l))));

const boolField = (label, value, onChange) =>
  h("label", { class: "switch" }, h("span", {}, label),
    h("input", { type: "checkbox", checked: !!value, onchange: (ev) => onChange(ev.target.checked) }));

const numberField = (label, value, min, max, onChange) =>
  labeled(label, h("input", { type: "number", min, max, step: "1", inputmode: "numeric", value,
    onchange: (ev) => onChange(ev.target.value === "" ? NaN : Number(ev.target.value)) }));

const colorField = (label, value, onChange) =>
  labeled(label, h("input", { type: "color", value, oninput: (ev) => onChange(ev.target.value) }));

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
  const wrap = $("#facewrap");
  if (!wrap || !dz.frame) return;
  const k = wrap.clientWidth / 800;
  dz.frame.style.transform = "scale(" + k + ")";
  wrap.style.height = Math.round(480 * k) + "px";
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

function previewFrame() {
  const wrap = h("div", { class: "face-wrap", id: "facewrap" });
  const frame = h("iframe", { class: "face-frame", src: "/face/index.html?preview=1", scrolling: "no", title: "Vista previa del reloj" });
  frame.addEventListener("load", () => { dz.ready = true; fitFrame(); pushPreview(); pushData(); });
  dz.frame = frame;
  dz.ready = false;
  wrap.append(frame);
  return wrap;
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
  paintDesign();
  loadFaceData();
}

function paintDesign() {
  const warn = dz.clockVersion && dz.panelVersion && dz.clockVersion !== dz.panelVersion
    ? h("div", { class: "card warn" }, `⚠ El reloj y el panel usan versiones distintas del motor (reloj ${dz.clockVersion}, panel ${dz.panelVersion}): la vista previa puede no ser idéntica. Actualiza la app del reloj o el panel.`)
    : null;
  dz.subEl = h("div", {});
  setView(
    warn,
    h("div", { class: "card face-card" }, previewFrame()),
    h("div", { class: "subtabs" }, SUBS.map(([k, label]) =>
      h("button", { class: "subtab" + (dz.sub === k ? " on" : ""), "data-sub": k, onclick: () => switchSub(k) }, label))),
    dz.subEl,
  );
  paintSub();
  fitFrame();
}

function switchSub(k) {
  dz.sub = k;
  dz.selected = null;
  document.querySelectorAll(".subtab").forEach((b) => b.classList.toggle("on", b.dataset.sub === k));
  paintSub();
  pushPreview();
  pushData();
}

function paintSub() {
  if (!dz.subEl) return;
  const fn = { screen: subScreen, theme: subTheme, profiles: subProfiles, alarm: subAlarm }[dz.sub];
  dz.subEl.replaceChildren(...fn());
}

// ------------------------------------------------------------------ sub-pestaña Pantalla

function subScreen() {
  const d = form.current;
  const scr = d.screens[dz.screenId];
  const sel = h("select", { onchange: (ev) => { dz.screenId = ev.target.value; dz.selected = null; paintSub(); pushPreview(); } },
    Object.keys(d.screens).map((id) => h("option", { value: id, selected: id === dz.screenId }, d.screens[id].name)));
  return [
    h("div", { class: "card" },
      labeled("Pantalla", sel),
      h("div", { class: "row" },
        btn("＋ Nueva", addScreen, "small ghost"),
        btn("Duplicar", duplicateScreen, "small ghost"),
        btn("Borrar", deleteScreen, "small danger")),
      textField("Nombre", scr.name, 30, (v) => { scr.name = v; setOptionText(sel, dz.screenId, v); updateSaveBar(); })),
    elementsPanel(),
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

function elementsPanel() {
  const list = currentElements();
  const sel = list.find((e) => e.id === dz.selected);
  return h("div", { class: "card" },
    h("h2", {}, "Elementos"),
    h("p", { class: "muted" }, "Toca un elemento en la vista previa o en la lista. Arrastra su esquina azul para cambiar el tamaño."),
    list.length
      ? list.map((e) => h("div", { class: "elrow" + (e.id === dz.selected ? " on" : ""), onclick: () => selectElement(e.id) },
        h("span", {}, typeInfo(e.type).label + " · " + e.id), h("span", { class: "muted" }, e.w + "×" + e.h + " en " + e.x + "," + e.y)))
      : h("p", { class: "muted" }, "Sin elementos."),
    addMenu(),
    sel ? elementOptions(sel) : null);
}

function addMenu() {
  const T = EditorCore.TYPES;
  const sel = h("select", {}, Object.keys(T).map((t) => h("option", { value: t }, T[t].label)));
  return h("div", { class: "row", style: "margin-top:10px" }, sel, btn("＋ Añadir", () => addElement(sel.value), "small"));
}

function addElement(type) {
  const list = currentElements();
  const rows = currentRows();
  if (list.length >= 12) { toast("Máximo 12 elementos por pantalla", true); return; }
  const t = EditorCore.TYPES[type];
  let size = { w: t.w, h: t.h };
  let spot = EditorCore.findFreeSpot(list, size.w, size.h, rows);
  if (!spot) { size = { w: 1, h: 1 }; spot = EditorCore.findFreeSpot(list, 1, 1, rows); }
  if (!spot) { toast("No hay hueco libre en la rejilla", true); return; }
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
  const other = EditorCore.collides(currentElements(), cand, e.id);
  if (other) { toast("Se solapa con " + other.id, true); return false; }
  Object.assign(e, patch);
  touch();
  paintSub(); // los números ya salieron del foco (onchange): se refresca la lista
  return true;
}

function resizeElement(id, w, hh) {
  const e = currentElements().find((x) => x.id === id);
  if (!e) return;
  const cand = { x: e.x, y: e.y, w, h: hh };
  if (EditorCore.inBounds(cand, currentRows()) && !EditorCore.collides(currentElements(), cand, id)) {
    e.w = w; e.h = hh; touch();
  } else {
    toast("No cabe ahí: se solaparía", true);
    pushPreview();
  }
  paintSub();
}

function moveElement(id, x, y) {
  const e = currentElements().find((it) => it.id === id);
  if (!e) return;
  dz.selected = id;
  const cand = { x, y, w: e.w, h: e.h };
  if (EditorCore.inBounds(cand, currentRows()) && !EditorCore.collides(currentElements(), cand, id)) {
    e.x = x; e.y = y; touch();
  } else {
    toast("No cabe ahí: se solaparía", true);
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
  if (f.kind === "photofit") return photoFitBlock(e);
  if (f.kind === "photolist") return photoListField(e, f);
  return null;
}

function photoListField(e, f) {
  const chosen = new Set(e[f.key] || []);
  const known = new Set(dz.backgrounds.map((b) => b.name));
  const file = h("input", { type: "file", accept: "image/jpeg,image/png,image/webp", multiple: true, hidden: true,
    onchange: async (ev) => {
      const input = ev.target;
      const files = Array.from(input.files);
      input.value = "";
      for (const one of files) {
        if ((e.images || []).length >= 20) { toast("Máximo 20 fotos por carrusel", true); break; }
        const name = await uploadPhoto(one);
        if (name && !(e.images || []).includes(name)) e.images = (e.images || []).concat([name]);
      }
      touch(); paintSub();
    } });
  const thumbs = dz.backgrounds.map((b) => {
    const on = chosen.has(b.name);
    return h("label", { class: "thumb" + (on ? " on" : "") },
      h("img", { src: "/api/bg/" + encodeURIComponent(b.name), alt: b.name, loading: "lazy" }),
      h("input", { type: "checkbox", checked: on, onchange: (ev) => {
        const cur = (e.images || []).filter((n) => n !== b.name);
        if (ev.target.checked) {
          if (cur.length >= 20) { toast("Máximo 20 fotos por carrusel", true); ev.target.checked = false; return; }
          cur.push(b.name);
        }
        e.images = cur; touch(); paintSub();
      } }));
  });
  const missing = (e.images || []).filter((n) => !known.has(n));
  return h("div", { class: "opt" },
    h("h3", {}, f.label + " (" + (e.images || []).length + ")"),
    dz.backgrounds.length ? h("div", { class: "thumbs" }, thumbs) : h("div", { class: "muted" }, "No hay fotos en el reloj: sube alguna."),
    missing.length ? h("div", { class: "warn" }, "Estas fotos ya no están en el reloj y se saltarán: " + missing.join(", ")) : null,
    !(e.images || []).length ? h("div", { class: "warn" }, "Elige al menos una foto: sin ellas el diseño no se puede guardar.") : null,
    h("div", { class: "row" }, btn("Subir fotos…", () => file.click(), "small ghost"), file));
}


function elementOptions(e) {
  const rows = currentRows();
  const pos = (key, min, max) => numberField(key.toUpperCase(), e[key], min, max, (v) => { if (!setRect(e, { [key]: v })) paintSub(); });
  return h("div", { class: "opt" },
    h("h2", {}, typeInfo(e.type).label),
    h("div", { class: "grid4" }, pos("x", 0, 7), pos("y", 0, rows - 1), pos("w", 1, 8), pos("h", 1, rows)),
    selectField("Color", EditorCore.COLOR_OPTIONS, e.color || "fg", (v) => { e.color = v; touch(); }),
    selectField("Alineación", [["left", "Izquierda"], ["center", "Centro"], ["right", "Derecha"]], e.align || "center", (v) => { e.align = v; touch(); }),
    ...typeInfo(e.type).fields.map((f) => typedField(e, f)),
    btn("Borrar elemento", () => {
      const list = currentElements();
      list.splice(list.indexOf(e), 1);
      dz.selected = null;
      touch(); paintSub();
    }, "danger small"));
}

// ------------------------------------------------------------------ sub-pestaña Alarma

function subAlarm() {
  const a = form.current.alarm_screen;
  return [
    h("div", { class: "card" },
      h("h2", {}, "Pantalla de alarma sonando"),
      h("p", { class: "muted" }, "Diseñas la zona de arriba (rejilla de 8×4). Los botones de abajo son del reloj y funcionan siempre."),
      labeled("Tema", themeSelect(a.theme, (v) => { a.theme = v; touch(); })),
      textField("Texto de Posponer (usa {min} para los minutos)", a.snooze_label === undefined ? "Posponer {min} min" : a.snooze_label, 20, (v) => { a.snooze_label = v; touch(); }),
      textField("Texto de Descartar", a.dismiss_label === undefined ? "Descartar" : a.dismiss_label, 20, (v) => { a.dismiss_label = v; touch(); })),
    elementsPanel(),
  ];
}

// ------------------------------------------------------------------ Probar en el reloj

function bindTry() {
  if (dz.tryBound) return;
  dz.tryBound = true;
  $("#try").addEventListener("click", async () => {
    if (route.tab !== "design" || !form) return;
    const b = $("#try");
    b.disabled = true;
    const r = await api("POST", "/api/design/preview", { design: form.current, seconds: 30 });
    b.disabled = false;
    if (r.ok) { toast("▶ Mostrándose 30 s en el reloj"); return; }
    const detail = Object.entries(r.data.errors || {}).map(([k, v]) => `${k}: ${v}`).join(" · ");
    toast(errText(r) + (detail ? " — " + detail : ""), true);
  });
}

// ------------------------------------------------------------------ sub-pestaña Tema

const FONT_OPTIONS = [["thin", "Fina"], ["normal", "Normal"], ["bold", "Negrita"], ["mono", "Monoespaciada"], ["digital", "Digital (7 segmentos)"]];

function subTheme() {
  const t = form.current.themes[dz.themeId];
  const themeSel = themeSelect(dz.themeId, (v) => { dz.themeId = v; paintSub(); pushPreview(); });
  return [
    h("div", { class: "card" },
      labeled("Tema", themeSel),
      h("div", { class: "row" }, btn("Duplicar", duplicateTheme, "small ghost"), btn("Borrar", deleteTheme, "small danger")),
      textField("Nombre", t.name, 30, (v) => { t.name = v; setOptionText(themeSel, dz.themeId, v); updateSaveBar(); }),
      selectField("Letra de la hora y los textos", FONT_OPTIONS, t.font, (v) => { t.font = v; touch(); }),
      colorField("Color principal", t.fg, (v) => { t.fg = v; touch(); }),
      colorField("Color secundario", t.muted, (v) => { t.muted = v; touch(); }),
      colorField("Color de acento", t.accent, (v) => { t.accent = v; touch(); })),
    h("div", { class: "card" }, h("h2", {}, "Fondo"), ...bgFields(t)),
    h("div", { class: "card" },
      h("p", { class: "muted" }, "Vuelve al diseño que traía el reloj (borra el que tienes guardado)."),
      btn("Restaurar diseño inicial", restoreInitial, "ghost")),
  ];
}

function bgFields(t) {
  const bg = t.bg;
  const kind = selectField("Tipo de fondo", [["color", "Color liso"], ["gradient", "Degradado"], ["image", "Imagen"]], bg.type, (v) => {
    const base = bg.color || bg.from || "#000000";
    if (v === "color") t.bg = { type: "color", color: base };
    else if (v === "gradient") t.bg = { type: "gradient", from: base, to: bg.to || "#ffffff", angle: bg.angle === undefined ? 160 : bg.angle };
    else t.bg = { type: "image", image: dz.backgrounds.length ? dz.backgrounds[0].name : "", dim: 30, color: base };
    touch(); paintSub();
  });
  if (bg.type === "color") return [kind, colorField("Color", bg.color, (v) => { bg.color = v; touch(); })];
  if (bg.type === "gradient") {
    return [kind,
      colorField("Desde", bg.from, (v) => { bg.from = v; touch(); }),
      colorField("Hasta", bg.to, (v) => { bg.to = v; touch(); }),
      numberField("Ángulo (0–359)", bg.angle === undefined ? 160 : bg.angle, 0, 359, (v) => { bg.angle = v; touch(); })];
  }
  return [kind, imagePicker(bg),
    numberField("Oscurecer la imagen (0–80 %)", bg.dim === undefined ? 0 : bg.dim, 0, 80, (v) => { bg.dim = v; touch(); }),
    colorField("Color si falta la imagen", bg.color || "#000000", (v) => { bg.color = v; touch(); })];
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
    if (bmp.close) bmp.close();
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

function rangeField(label, val, min, max, step, onInput) {
  const out = h("span", { class: "muted" }, String(val));
  const input = h("input", { type: "range", min, max, step, value: val,
    oninput: (ev) => { const v = Number(ev.target.value); out.textContent = String(v); onInput(v); } });
  return labeled(label, h("div", { class: "row" }, input, out));
}

/* Encaje, zoom y posición de una foto (fondo de tema o carrusel); `spec` es el objeto del diseño. */
function photoFitBlock(spec) {
  return h("div", { class: "opt" },
    selectField("Encaje", [["cover", "Rellenar (recorta lo que sobra)"], ["contain", "Entera (con bandas)"], ["fill", "Estirar"]],
      spec.fit || "cover", (v) => { spec.fit = v; touch(); }),
    rangeField("Zoom", spec.zoom === undefined ? 1 : spec.zoom, 1, 3, 0.1, (v) => { spec.zoom = v; touch(); }),
    rangeField("Posición horizontal", spec.pos_x === undefined ? 50 : spec.pos_x, 0, 100, 1, (v) => { spec.pos_x = v; touch(); }),
    rangeField("Posición vertical", spec.pos_y === undefined ? 50 : spec.pos_y, 0, 100, 1, (v) => { spec.pos_y = v; touch(); }));
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
  return h("div", { class: "opt" },
    labeled("Imagen", select),
    !bg.image ? h("div", { class: "muted" }, "Sube una imagen para usar este fondo") : null,
    bg.image && !known.has(bg.image) ? h("div", { class: "warn" }, "⚠ Esa imagen ya no está en el reloj: se verá el color de respaldo.") : null,
    h("div", { class: "row" }, btn("Subir imagen…", () => file.click(), "small ghost"), file),
    dz.backgrounds.map((b) => h("div", { class: "row between" }, h("span", {}, b.name), btn("Borrar", () => deleteBackground(b.name), "small ghost"))),
    bg.image ? photoFitBlock(bg) : null);
}

async function deleteBackground(name) {
  const used = Object.values(form.current.themes).filter((t) => t.bg.type === "image" && t.bg.image === name).map((t) => t.name);
  const msg = used.length ? `¿Borrar ${name}? Lo usan los temas: ${used.join(", ")}. Se verá su color de respaldo.` : `¿Borrar ${name}?`;
  if (!(await confirmInPage(msg, "Borrar"))) return;
  const r = await api("DELETE", `/api/backgrounds/${encodeURIComponent(name)}`);
  toast(r.ok ? "✓ Imagen borrada" : errText(r), !r.ok);
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
  toast("✓ Diseño inicial restaurado");
  form = null; updateSaveBar();
  renderDesign();
}

// ------------------------------------------------------------------ sub-pestaña Perfiles

const ROTATE_OPTIONS = [[0, "Solo deslizando"], [10, "Cada 10 s"], [30, "Cada 30 s"], [60, "Cada minuto"], [300, "Cada 5 minutos"]];

function subProfiles() {
  return [
    ...form.current.profiles.map(profileCard),
    h("div", { class: "card" }, btn("＋ Nuevo perfil", addProfile, "ghost")),
  ];
}

function profileCard(p) {
  const d = form.current;
  const rotate = p.rotate_s === undefined ? 0 : p.rotate_s;
  const options = ROTATE_OPTIONS.some(([v]) => v === rotate) ? ROTATE_OPTIONS : [...ROTATE_OPTIONS, [rotate, `Cada ${rotate} s`]];
  const current = p.id === dz.profileId;
  const title = h("h2", {}, p.name);
  return h("div", { class: "card" + (current ? " ring" : "") },
    h("div", { class: "row between" },
      title,
      current ? h("span", { class: "muted" }, "en la vista previa")
        : btn("Ver", () => { dz.profileId = p.id; paintSub(); pushPreview(); }, "small ghost")),
    textField("Nombre", p.name, 30, (v) => { p.name = v; title.textContent = v; updateSaveBar(); }),
    labeled("Empieza a las", h("input", { type: "time", value: p.from, onchange: (ev) => { p.from = ev.target.value; touch(); } })),
    labeled("Tema", themeSelect(p.theme, (v) => { p.theme = v; touch(); })),
    h("div", { class: "field" },
      h("span", { class: "muted" }, "Pantallas de este perfil"),
      Object.keys(d.screens).map((id) => boolField(d.screens[id].name, p.screens.includes(id), (on) => {
        const ids = Object.keys(d.screens).filter((x) => (x === id ? on : p.screens.includes(x)));
        if (!ids.length) { toast("Cada perfil necesita al menos una pantalla", true); paintSub(); return; }
        p.screens = ids;
        touch();
      }))),
    selectField("Cambio de pantalla", options.map(([v, l]) => [String(v), l]), String(rotate), (v) => { p.rotate_s = Number(v); touch(); }),
    numberField("Brillo (1–100)", p.brightness === undefined ? 70 : p.brightness, 1, 100, (v) => { p.brightness = v; touch(); }),
    boolField("Modo noche (se atenúa y tocar la pantalla la ilumina un momento)", p.night, (v) => { p.night = v; touch(); }),
    boolField("Mostrar la barra de botones", p.show_bar !== false, (v) => { p.show_bar = v; touch(); }),
    d.profiles.length > 1 ? btn("Borrar perfil", () => deleteProfile(p), "small danger") : null);
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
  touch(); paintSub();
}

async function deleteProfile(p) {
  const d = form.current;
  if (d.profiles.length <= 1) { toast("Tiene que quedar al menos un perfil", true); return; }
  if (!(await confirmInPage(`¿Borrar el perfil «${p.name}»?`, "Borrar"))) return;
  d.profiles.splice(d.profiles.indexOf(p), 1);
  if (dz.profileId === p.id) dz.profileId = d.profiles[0].id;
  touch(); paintSub();
}
