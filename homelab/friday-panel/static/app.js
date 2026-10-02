"use strict";
// Panel de Friday. Sin librerías: vistas que se pintan con h(), un formulario
// "sucio" a la vez (barra Guardar) y todo pasa por /api (el panel habla con el reloj).

const $ = (sel, el = document) => el.querySelector(sel);
const view = $("#view");
const DAYS = [["mon", "L"], ["tue", "M"], ["wed", "X"], ["thu", "J"], ["fri", "V"], ["sat", "S"], ["sun", "D"]];
const PRESETS = [
  ["Laborables", ["mon", "tue", "wed", "thu", "fri"]],
  ["Finde", ["sat", "sun"]],
  ["Todos", DAYS.map((d) => d[0])],
  ["Una vez", []],
];
const NEW_ALARM = {
  time: "07:00", label: "Alarma", days: [], enabled: true,
  sound: null, max_volume: null, ramp_s: null, snooze_min: null, briefing: null,
};

let route = { tab: "home" };
let loginShown = false;
let form = null; // { original, current, onSave, isNew, errors }

// ------------------------------------------------------------------ utilidades

function h(tag, attrs = {}, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else if (k === "class") el.className = v;
    else if (k === "checked" || k === "value" || k === "disabled" || k === "selected") el[k] = v;
    else if (v !== false && v != null) el.setAttribute(k, v === true ? "" : v);
  }
  for (const kid of kids.flat()) {
    if (kid == null || kid === false) continue;
    el.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
  }
  return el;
}

async function api(method, path, body, raw) {
  const opt = { method, headers: {}, credentials: "same-origin" };
  if (raw !== undefined) { opt.body = raw; opt.headers["Content-Type"] = "application/octet-stream"; }
  else if (body !== undefined) { opt.body = JSON.stringify(body); opt.headers["Content-Type"] = "application/json"; }
  let res;
  try { res = await fetch(path, opt); } catch {
    return { ok: false, status: 0, data: { error: "Sin conexión con el panel" } };
  }
  const data = await res.json().catch(() => ({}));
  if (res.status === 401 && data.login_required) { showLogin(); }
  if (res.status === 502) setConn(false); else if (res.ok) setConn(true);
  return { ok: res.ok && data.ok !== false, status: res.status, data };
}

const errText = (r) => r.data.error || `Error ${r.status}`;

// Si hace falta login, la pantalla de login manda: no la tapamos con un error.
function showError(r) {
  if (r.data.login_required) return;
  view.replaceChildren(h("div", { class: "card bad" }, errText(r)));
}

function setConn(on) {
  const p = $("#conn");
  p.textContent = on ? "● Conectado" : "● El reloj no responde";
  p.className = "pill " + (on ? "on" : "off");
}

let toastTimer;
function toast(msg, bad = false) {
  const t = $("#toast");
  t.textContent = msg; t.className = bad ? "bad" : ""; t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), bad ? 6000 : 3000);
}

function confirmInPage(msg, okLabel = "Aceptar") {
  return new Promise((resolve) => {
    const close = (v) => { dlg.remove(); resolve(v); };
    const dlg = h("div", { class: "modal" },
      h("div", { class: "card" }, h("p", {}, msg),
        h("div", { class: "row between" },
          h("button", { class: "ghost", onclick: () => close(false) }, "Cancelar"),
          h("button", { class: "danger", onclick: () => close(true) }, okLabel))));
    document.body.append(dlg);
  });
}

const btn = (label, onclick, cls = "") => h("button", { class: cls, onclick }, label);
const daysText = (days) => {
  const set = new Set(days || []);
  for (const [name, list] of PRESETS) if (list.length === set.size && list.every((d) => set.has(d))) return name === "Una vez" ? "Una vez" : name;
  return DAYS.filter(([k]) => set.has(k)).map(([, l]) => l).join(" ");
};
const whenText = (ms) => new Date(ms).toLocaleString("es-ES", { weekday: "short", hour: "2-digit", minute: "2-digit" });

// ------------------------------------------------------------------ formulario y Guardar

function startForm(original, onSave, isNew = false) {
  form = { original: structuredClone(original), current: structuredClone(original), onSave, isNew, errors: {} };
  updateSaveBar();
  return form;
}

function setField(key, value) {
  if (!form) return;
  form.current[key] = value;
  updateSaveBar();
}

function changes() {
  if (!form) return {};
  const out = {};
  for (const k of Object.keys(form.current)) {
    if (JSON.stringify(form.current[k]) !== JSON.stringify(form.original[k])) out[k] = form.current[k];
  }
  return out;
}

const dirty = () => !!form && (form.isNew || Object.keys(changes()).length > 0);

function updateSaveBar() { $("#savebar").hidden = !dirty(); }

$("#save").addEventListener("click", async () => {
  if (!form) return;
  const b = $("#save");
  b.disabled = true; b.textContent = "Guardando…";
  const r = await form.onSave(form.isNew ? form.current : changes());
  b.disabled = false; b.textContent = "Guardar";
  if (r.ok) {
    toast("✓ Aplicado en el reloj");
    form = null; updateSaveBar();
    render(r.next || route);
  } else {
    const errs = r.data.errors || {};
    const detail = Object.entries(errs).map(([k, v]) => `${k}: ${v}`).join(" · ");
    toast(errText(r) + (detail ? ` — ${detail}` : ""), true);
    if (form) { form.errors = errs; markErrors(); }
  }
});

$("#discard").addEventListener("click", () => { form = null; updateSaveBar(); render(route); });

function markErrors() {
  document.querySelectorAll("[data-key]").forEach((el) => {
    const msg = form && form.errors[el.dataset.key];
    el.classList.toggle("error", !!msg);
    el.querySelector(".err")?.remove();
    if (msg) el.append(h("div", { class: "err" }, msg));
  });
}

async function go(next) {
  if (dirty() && !(await confirmInPage("Tienes cambios sin guardar. ¿Descartarlos?", "Descartar"))) return;
  form = null; updateSaveBar();
  render(next);
}

function render(next) {
  route = next;
  document.querySelectorAll("#tabs button").forEach((b) => b.classList.toggle("active", b.dataset.tab === route.tab));
  window.scrollTo(0, 0);
  if (route.tab === "home") return renderHome();
  if (route.tab === "alarms") return route.edit != null ? renderAlarmEdit(route.edit) : renderAlarms();
  if (route.tab === "settings") return renderSettings();
}

document.querySelectorAll("#tabs button").forEach((b) => b.addEventListener("click", () => go({ tab: b.dataset.tab })));

// ------------------------------------------------------------------ login

function showLogin() {
  if (loginShown) return; // no borrar la contraseña a medio escribir
  loginShown = true;
  form = null; updateSaveBar();
  const pass = h("input", { type: "password", autocomplete: "current-password" });
  const submit = async (ev) => {
    ev.preventDefault();
    const r = await api("POST", "/login", { password: pass.value });
    if (r.ok) { loginShown = false; render(route); } else toast(errText(r), true);
  };
  view.replaceChildren(h("form", { class: "card stack", onsubmit: submit },
    h("h2", {}, "Entrar"), h("label", { class: "field" }, h("span", {}, "Contraseña del panel"), pass),
    h("button", { type: "submit" }, "Entrar")));
}

// ------------------------------------------------------------------ Inicio

async function doAction(name, body) {
  const r = await api("POST", `/api/do/${name}`, body);
  toast(r.ok ? "✓ Hecho" : errText(r), !r.ok);
  if (route.tab === "home") renderHome();
}

async function renderHome() {
  const r = await api("GET", "/api/status");
  if (route.tab !== "home") return;
  if (!r.ok) { showError(r); return; }
  const s = r.data;
  const time = new Date(s.time).toLocaleTimeString("es-ES", { hour: "2-digit", minute: "2-digit" });
  const next = s.next_alarm ? `${s.next_alarm.time} · ${s.next_alarm.label} (${whenText(s.next_alarm.at)})` : "ninguna";
  const say = h("input", { type: "text", placeholder: "Decir en el reloj…", maxlength: "300" });
  view.replaceChildren(
    h("div", { class: "card" }, h("div", { class: "clock" }, time), h("div", { class: "muted" }, "Próxima alarma: ", next)),
    s.ringing ? h("div", { class: "card ring stack" }, h("strong", {}, "🔔 Sonando: ", s.ringing_label || "Alarma"),
      h("div", { class: "row" }, btn("Apagar", () => doAction("stop")), btn("Posponer", () => doAction("snooze"), "ghost"))) : null,
    h("div", { class: "card stack" }, h("h2", {}, "Acciones rápidas"),
      btn("Probar alarma en 10 s", () => doAction("test")),
      btn("Buenos días", () => doAction("briefing"), "ghost"),
      h("div", { class: "row between" }, say, btn("Decir", () => { doAction("speak", { text: say.value }); say.value = ""; }, "small"))),
  );
}

setInterval(() => {
  if (document.visibilityState === "visible" && route.tab === "home" && !form && !loginShown) renderHome();
}, 10_000);

// ------------------------------------------------------------------ Alarmas

async function renderAlarms() {
  const r = await api("GET", "/api/alarms");
  if (route.tab !== "alarms" || route.edit != null) return;
  if (!r.ok) { showError(r); return; }
  const all = r.data.alarms || [];
  const alarms = all.filter((a) => (a.kind || "alarm") === "alarm" && !a.trigger_at);
  const others = all.filter((a) => !alarms.includes(a));

  const row = (a) => {
    const toggle = h("input", {
      type: "checkbox", checked: a.enabled,
      onclick: (ev) => ev.stopPropagation(),
      onchange: async (ev) => {
        const r2 = await api("PUT", `/api/alarms/${a.id}`, { enabled: ev.target.checked });
        if (!r2.ok) { ev.target.checked = !ev.target.checked; toast(errText(r2), true); }
        else toast(ev.target.checked ? "✓ Alarma activada" : "✓ Alarma desactivada");
      },
    });
    return h("div", { class: "alarm" + (a.enabled ? "" : " disabled"), onclick: () => go({ tab: "alarms", edit: a.id }) },
      h("div", {}, h("div", { class: "t" }, a.time), h("div", { class: "muted" }, `${a.label} · ${daysText(a.days)}`)),
      h("label", { class: "switch" }, toggle));
  };

  view.replaceChildren(
    h("div", { class: "card" }, h("h2", {}, "Alarmas"),
      alarms.length ? alarms.map(row) : h("p", { class: "muted" }, "No hay alarmas."),
      btn("＋ Nueva alarma", () => go({ tab: "alarms", edit: "new" }))),
    others.length ? h("div", { class: "card" }, h("h2", {}, "Temporizadores y pospuestas"),
      others.map((a) => h("div", { class: "row between" },
        h("span", {}, `${a.label} · ${a.time || ""}`),
        btn("Cancelar", async () => {
          const r2 = await api("DELETE", `/api/alarms/${a.id}`);
          toast(r2.ok ? "✓ Cancelado" : errText(r2), !r2.ok); renderAlarms();
        }, "small ghost")))) : null,
  );
}

function optionNumber(key, label, min, max, unit, fallback) {
  const general = form.current[key] == null;
  const input = h("input", {
    type: "number", min, max, step: "1", inputmode: "numeric",
    value: general ? "" : form.current[key], disabled: general,
    oninput: (ev) => { if (ev.target.value !== "") setField(key, Number(ev.target.value)); },
  });
  const check = h("input", {
    type: "checkbox", checked: general,
    onchange: (ev) => {
      input.disabled = ev.target.checked;
      if (ev.target.checked) { input.value = ""; setField(key, null); }
      else { input.value = fallback; setField(key, fallback); }
    },
  });
  return h("div", { class: "opt", "data-key": key },
    h("label", { class: "switch" }, h("span", {}, label), h("span", { class: "row" }, h("span", { class: "muted" }, "general"), check)),
    h("label", { class: "field" }, h("span", {}, `Valor (${min}–${max} ${unit})`), input));
}

function soundPicker(sounds, reload) {
  const known = new Set(sounds.map((s) => s.id));
  const cur = form.current.sound;
  const select = h("select", { onchange: (ev) => setField("sound", ev.target.value || null) },
    h("option", { value: "", selected: cur == null }, "Tono por defecto"),
    sounds.map((s) => h("option", { value: s.id, selected: s.id === cur }, (s.kind === "file" ? "📁 " : "🔔 ") + s.name)));
  const file = h("input", { type: "file", accept: "audio/*,.mp3,.ogg,.wav,.m4a", hidden: true,
    onchange: async (ev) => {
      const f = ev.target.files[0];
      if (!f) return;
      if (f.size > 5 * 1024 * 1024) { toast("El sonido supera 5 MB", true); return; }
      toast("Subiendo…");
      const r = await api("POST", `/api/sounds?name=${encodeURIComponent(f.name)}`, undefined, f);
      if (!r.ok) { toast(errText(r), true); return; }
      toast("✓ Sonido subido al reloj");
      setField("sound", r.data.id);
      reload();
    } });
  return h("div", { class: "opt", "data-key": "sound" },
    h("label", { class: "field" }, h("span", {}, "Sonido"), select),
    cur != null && !known.has(cur) ? h("div", { class: "warn" }, "⚠ Ese sonido ya no está en el reloj: sonará el tono por defecto.") : null,
    h("div", { class: "row" },
      btn("▶ Escuchar 5 s", async () => {
        const r = await api("POST", "/api/sounds/preview", { id: form.current.sound });
        if (!r.ok) toast(errText(r), true);
      }, "small ghost"),
      btn("Subir sonido…", () => file.click(), "small ghost"), file));
}

async function renderAlarmEdit(id) {
  const [ra, rs] = await Promise.all([api("GET", "/api/alarms"), api("GET", "/api/sounds")]);
  if (route.edit !== id) return;
  if (!ra.ok) { showError(ra); return; }
  const isNew = id === "new";
  const found = (ra.data.alarms || []).find((a) => a.id === id);
  if (!isNew && !found) { view.replaceChildren(h("div", { class: "card bad" }, "Esa alarma ya no existe.")); return; }
  const src = isNew ? NEW_ALARM : found;
  const fields = ["time", "label", "days", "enabled", "sound", "max_volume", "ramp_s", "snooze_min", "briefing"];
  const original = Object.fromEntries(fields.map((k) => [k, src[k] ?? NEW_ALARM[k]]));
  startForm(original, async (c) => {
    const r = isNew ? await api("POST", "/api/alarms", c) : await api("PUT", `/api/alarms/${id}`, c);
    return { ...r, next: { tab: "alarms" } };
  }, isNew);
  const sounds = rs.ok ? rs.data.sounds || [] : [];
  paintAlarmEdit(id, isNew, sounds);
}

function paintAlarmEdit(id, isNew, sounds) {
  const c = form.current;
  const chips = h("div", { class: "chips" });
  const paintChips = () => chips.replaceChildren(...DAYS.map(([k, l]) =>
    h("button", { class: "chip" + (c.days.includes(k) ? " on" : ""), type: "button",
      onclick: () => { setField("days", c.days.includes(k) ? c.days.filter((d) => d !== k) : DAYS.map((d) => d[0]).filter((d) => d === k || c.days.includes(d))); paintChips(); } }, l)));
  paintChips();
  const reloadSounds = async () => {
    const rs = await api("GET", "/api/sounds");
    if (rs.ok && form) paintAlarmEdit(id, isNew, rs.data.sounds || []);
  };
  const briefing = h("select", { onchange: (ev) => setField("briefing", ev.target.value === "" ? null : ev.target.value === "1") },
    h("option", { value: "", selected: c.briefing == null }, "Ajuste general"),
    h("option", { value: "1", selected: c.briefing === true }, "Sí"),
    h("option", { value: "0", selected: c.briefing === false }, "No"));

  view.replaceChildren(
    h("div", { class: "row between" }, btn("‹ Alarmas", () => go({ tab: "alarms" }), "ghost small"), h("h2", {}, isNew ? "Nueva alarma" : "Editar alarma")),
    h("div", { class: "card" },
      h("label", { class: "field", "data-key": "time" }, h("span", {}, "Hora"),
        h("input", { type: "time", value: c.time, required: true, oninput: (ev) => setField("time", ev.target.value) })),
      h("label", { class: "field", "data-key": "label" }, h("span", {}, "Nombre"),
        h("input", { type: "text", value: c.label, maxlength: "40", oninput: (ev) => setField("label", ev.target.value) })),
      h("div", { class: "field", "data-key": "days" }, h("span", { class: "muted" }, "Días"), chips,
        h("div", { class: "row", style: "margin-top:8px" }, PRESETS.map(([name, list]) =>
          btn(name, () => { setField("days", [...list]); paintChips(); }, "small ghost")))),
      h("label", { class: "switch" }, h("span", {}, "Activa"),
        h("input", { type: "checkbox", checked: c.enabled, onchange: (ev) => setField("enabled", ev.target.checked) }))),
    h("div", { class: "card" }, h("h2", {}, "Opciones de esta alarma"),
      soundPicker(sounds, reloadSounds),
      optionNumber("max_volume", "Volumen máximo", 1, 100, "%", 80),
      optionNumber("ramp_s", "Subida de volumen", 0, 600, "s", 20),
      optionNumber("snooze_min", "Minutos al posponer", 1, 60, "min", 5),
      h("div", { class: "opt", "data-key": "briefing" }, h("label", { class: "field" }, h("span", {}, "Buenos días al apagarla"), briefing))),
    isNew ? null : h("div", { class: "card" }, btn("Borrar alarma", async () => {
      if (!(await confirmInPage("¿Borrar esta alarma?", "Borrar"))) return;
      const r = await api("DELETE", `/api/alarms/${id}`);
      if (r.ok) { form = null; updateSaveBar(); toast("✓ Alarma borrada"); render({ tab: "alarms" }); }
      else toast(errText(r), true);
    }, "danger")),
  );
  markErrors();
}

// ------------------------------------------------------------------ Ajustes

function settingInput(f) {
  const v = form.current[f.key];
  if (f.type === "bool") {
    return h("label", { class: "switch", "data-key": f.key }, h("span", {}, f.label),
      h("input", { type: "checkbox", checked: v, onchange: (ev) => setField(f.key, ev.target.checked) }));
  }
  const isNum = f.type === "int" || f.type === "float";
  const input = h("input", isNum
    ? { type: "number", min: f.min, max: f.max, step: f.type === "int" ? "1" : "any", inputmode: f.type === "int" ? "numeric" : "decimal", value: v,
        oninput: (ev) => { if (ev.target.value !== "") setField(f.key, Number(ev.target.value)); } }
    : { type: "text", maxlength: f.max_len, value: v, oninput: (ev) => setField(f.key, ev.target.value) });
  const range = isNum ? ` (${f.min}–${f.max})` : "";
  return h("label", { class: "field", "data-key": f.key }, h("span", {}, f.label + range), input);
}

async function renderSettings() {
  const [r, rs] = await Promise.all([api("GET", "/api/settings"), api("GET", "/api/sounds")]);
  if (route.tab !== "settings") return;
  if (!r.ok) { showError(r); return; }
  startForm(r.data.settings, (c) => api("PUT", "/api/settings", c));
  const groups = new Map();
  for (const f of r.data.schema) { if (!groups.has(f.group)) groups.set(f.group, []); groups.get(f.group).push(f); }
  const files = rs.ok ? (rs.data.sounds || []).filter((s) => s.kind === "file") : [];
  view.replaceChildren(
    ...[...groups].map(([g, fs]) => h("div", { class: "card" }, h("h2", {}, g), fs.map(settingInput))),
    h("div", { class: "card" }, h("h2", {}, "Sonidos subidos"),
      files.length ? files.map((s) => h("div", { class: "row between" },
        h("span", {}, `${s.name} · ${Math.round((s.size || 0) / 1024)} KB`),
        btn("Borrar", async () => {
          if (!(await confirmInPage(`¿Borrar ${s.name}? Las alarmas que lo usen sonarán con el tono por defecto.`, "Borrar"))) return;
          const r2 = await api("DELETE", `/api/sounds/${encodeURIComponent(s.name)}`);
          toast(r2.ok ? "✓ Sonido borrado" : errText(r2), !r2.ok);
          if (r2.ok && !dirty()) renderSettings();
        }, "small ghost"))) : h("p", { class: "muted" }, "Ninguno. Súbelos desde la ficha de una alarma.")),
  );
}

// ------------------------------------------------------------------ arranque

window.addEventListener("beforeunload", (ev) => { if (dirty()) { ev.preventDefault(); ev.returnValue = ""; } });
render(route);
