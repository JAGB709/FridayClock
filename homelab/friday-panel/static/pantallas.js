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

const SUBS = [["screen", "Pantalla"], ["alarm", "Alarma"]];
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

function pushPreview() {
  if (!dz.ready || !dz.frame || !form) return;
  dz.frame.contentWindow.postMessage({ type: "face-render", design: form.current, opts: previewOpts() }, location.origin);
}

function pushData() {
  if (!dz.ready || !dz.frame) return;
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
  const [rd, rv] = await Promise.all([api("GET", "/api/design"), api("GET", "/api/face-version")]);
  if (route.tab !== "design") return;
  if (!rd.ok) { showError(rd); return; }
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
  const fn = { screen: subScreen, alarm: subAlarm }[dz.sub];
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
      textField("Nombre", scr.name, 30, (v) => { scr.name = v; updateSaveBar(); })),
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
  const T = EditorCore.TYPES;
  return h("div", { class: "card" },
    h("h2", {}, "Elementos"),
    h("p", { class: "muted" }, "Toca un elemento en la vista previa o en la lista. Arrastra su esquina azul para cambiar el tamaño."),
    list.length
      ? list.map((e) => h("div", { class: "elrow" + (e.id === dz.selected ? " on" : ""), onclick: () => selectElement(e.id) },
        h("span", {}, T[e.type].label + " · " + e.id), h("span", { class: "muted" }, e.w + "×" + e.h + " en " + e.x + "," + e.y)))
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

function typedField(e, f) {
  const def = EditorCore.TYPES[e.type].defaults[f.key];
  const val = e[f.key] === undefined ? def : e[f.key];
  if (f.when && (e[f.when.key] === undefined ? EditorCore.TYPES[e.type].defaults[f.when.key] : e[f.when.key]) !== f.when.value) return null;
  if (f.kind === "select") return selectField(f.label, f.options, val, (v) => { e[f.key] = v; touch(); paintSub(); });
  if (f.kind === "bool") return boolField(f.label, val, (v) => { e[f.key] = v; touch(); });
  if (f.kind === "number") return numberField(f.label, val, f.min, f.max, (v) => { e[f.key] = v; touch(); });
  if (f.kind === "text") return textField(f.label, val || "", f.max, (v) => { e[f.key] = v; touch(); });
  if (f.kind === "lines") {
    return labeled(f.label, h("textarea", { rows: 5, oninput: (ev) => { e[f.key] = ev.target.value.split("\n").map((s) => s.trim()).filter(Boolean).slice(0, 20); touch(); } },
      (e[f.key] || []).join("\n")));
  }
  return null;
}

function elementOptions(e) {
  const T = EditorCore.TYPES;
  const rows = currentRows();
  const pos = (key, min, max) => numberField(key.toUpperCase(), e[key], min, max, (v) => { if (!setRect(e, { [key]: v })) paintSub(); });
  return h("div", { class: "opt" },
    h("h2", {}, T[e.type].label),
    h("div", { class: "grid4" }, pos("x", 0, 7), pos("y", 0, rows - 1), pos("w", 1, 8), pos("h", 1, rows)),
    selectField("Color", EditorCore.COLOR_OPTIONS, e.color || "fg", (v) => { e.color = v; touch(); }),
    selectField("Alineación", [["left", "Izquierda"], ["center", "Centro"], ["right", "Derecha"]], e.align || "center", (v) => { e.align = v; touch(); }),
    ...T[e.type].fields.map((f) => typedField(e, f)),
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
