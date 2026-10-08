"use strict";
// Panel de Friday. Sin librerías: vistas que se pintan con h(), un formulario
// "sucio" a la vez (barra Guardar) y todo pasa por /api (el panel habla con el reloj).

const $ = (sel, el = document) => el.querySelector(sel);
const view = $("#view");
// replaceChildren convierte null en el texto "null": se descartan los huecos.
// Tras cambiar de sección, la primera vista con contenido entra con una animación breve
// (los refrescos periódicos de la misma vista no se animan).
let enterNext = false;
const setView = (...kids) => {
  view.replaceChildren(...kids.flat().filter((k) => k != null && k !== false));
  if (!enterNext) return;
  enterNext = false;
  view.classList.remove("view-in");
  void view.offsetWidth; // reinicia la animación
  view.classList.add("view-in");
};
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

async function api(method, path, body, raw) {
  const opt = { method, headers: { "X-Friday": "1" }, credentials: "same-origin" };
  if (raw !== undefined) { opt.body = raw; opt.headers["Content-Type"] = "application/octet-stream"; }
  else if (body !== undefined) { opt.body = JSON.stringify(body); opt.headers["Content-Type"] = "application/json"; }
  let res;
  try { res = await fetch(path, opt); } catch {
    setConn(false, "Sin conexión con el panel");
    return { ok: false, status: 0, data: { error: "Sin conexión con el panel" } };
  }
  const data = await res.json().catch(() => ({}));
  if (res.status === 401 && data.login_required) { showLogin(); }
  if (res.status === 502) setConn(false); else if (res.ok) setConn(true);
  return { ok: res.ok && data.ok !== false, status: res.status, data };
}

const errText = (r) => r.data.login_required ? "Inicia sesión" : (r.data.error || `Error ${r.status}`);

// Si hace falta login, la pantalla de login manda: no la tapamos con un error.
function showError(r) {
  if (r.data.login_required) return;
  setView(errorState(errText(r), () => render(route)));
}

const daysText = (days) => {
  const set = new Set(days || []);
  for (const [name, list] of PRESETS) if (list.length === set.size && list.every((d) => set.has(d))) return name === "Una vez" ? "Una vez" : name;
  return DAYS.filter(([k]) => set.has(k)).map(([, l]) => l).join(" ");
};

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

// La rueda de hora avisa 80 ms después de pararse: antes de guardar se recoge ya su valor
// (también al apoyar el dedo en Guardar, por si sigue con inercia).
$("#save").addEventListener("pointerdown", () => flushWheels());
$("#save").addEventListener("click", async () => {
  flushWheels();
  if (!form || !dirty()) return;
  const b = $("#save");
  const lbl = b.querySelector(".lbl");
  b.disabled = true; b.setAttribute("aria-busy", "true"); lbl.textContent = "Guardando…";
  const r = await form.onSave(form.isNew ? form.current : changes());
  b.disabled = false; b.removeAttribute("aria-busy"); lbl.textContent = "Guardar";
  if (r.ok) {
    toast("Aplicado en el reloj");
    form = null; updateSaveBar();
    render(r.next || route);
  } else {
    let errs = r.data.errors || {};
    const detail = Object.entries(errs).map(([k, v]) => `${k}: ${v}`).join(" · ");
    toast(errText(r) + (detail ? ` — ${detail}` : ""), true);
    // Las alarmas devuelven un solo "error" que empieza por el campo ("max_volume debe…"):
    // se marca ese campo igual que con la lista de errores de Ajustes.
    const k = !Object.keys(errs).length && /^([a-z_]+) /.exec(r.data.error || "");
    if (k && form && k[1] in form.current) errs = { [k[1]]: r.data.error };
    if (form) { form.errors = errs; markErrors(); }
  }
});

$("#discard").addEventListener("click", () => {
  const eraNueva = form && form.isNew;
  form = null; updateSaveBar();
  render(eraNueva ? { tab: "alarms" } : route);
});

function markErrors() {
  document.querySelectorAll("[data-key]").forEach((el) => {
    const msg = form && form.errors[el.dataset.key];
    el.classList.toggle("error", !!msg);
    el.querySelector(".err")?.remove();
    if (el.classList.contains("switch") && el.nextElementSibling?.classList.contains("err")) el.nextElementSibling.remove();
    if (msg) {
      const err = h("div", { class: "err" }, icon("alert-triangle", 16), h("span", {}, msg));
      if (el.classList.contains("switch")) el.after(err); else el.append(err);
      // Un campo con error dentro de "Opciones" plegadas se despliega para que se vea.
      const fold = el.closest("details");
      if (fold && !fold.open) fold.open = true;
    }
  });
}

async function go(next) {
  flushWheels(); // un valor pendiente de la rueda también cuenta como cambio sin guardar
  if (dirty() && !(await confirmInPage("Tienes cambios sin guardar. ¿Descartarlos?", "Descartar"))) return;
  form = null; updateSaveBar();
  render(next);
}

const TITLES = { home: "Inicio", alarms: "Alarmas", design: "Pantallas", settings: "Ajustes" };

function titleFor(r) {
  if (r.tab === "alarms" && r.edit != null) return r.edit === "new" ? "Nueva alarma" : "Editar alarma";
  return TITLES[r.tab] || "Friday";
}

function setTitle(text) {
  $("#title").textContent = text;
  document.title = text === "Friday" ? "Friday" : `${text} · Friday`;
}

// Los iconos de la barra inferior y de la barra de guardado salen del sprite: se ponen una vez.
let chromeReady = false;
function fillChrome() {
  if (chromeReady) return;
  chromeReady = true;
  document.querySelectorAll("#tabs button").forEach((b) => b.prepend(icon(b.dataset.icon, 24)));
  $("#discard").prepend(icon("rotate-ccw"));
  $("#try").prepend(icon("play"));
  $("#save").prepend(icon("save"));
}

function render(next) {
  flushWheels();
  route = next;
  fillChrome();
  if (loginShown) return; // la pantalla de login manda hasta que se entra
  document.body.classList.remove("login");
  setTitle(titleFor(route));
  $("#try").hidden = route.tab !== "design";
  document.querySelectorAll("#tabs button").forEach((b) => {
    const on = b.dataset.tab === route.tab;
    b.classList.toggle("active", on);
    if (on) b.setAttribute("aria-current", "page"); else b.removeAttribute("aria-current");
  });
  window.scrollTo(0, 0);
  setFab(null);
  view.replaceChildren(loadingState());
  enterNext = true;
  if (route.tab === "home") return renderHome();
  if (route.tab === "alarms") return route.edit != null ? renderAlarmEdit(route.edit) : renderAlarms();
  if (route.tab === "design") return renderDesign();
  if (route.tab === "settings") return renderSettings();
}

document.querySelectorAll("#tabs button").forEach((b) => b.addEventListener("click", () => go({ tab: b.dataset.tab })));

// ------------------------------------------------------------------ login

function showLogin() {
  if (loginShown) return; // no borrar la contraseña a medio escribir
  loginShown = true;
  form = null; updateSaveBar();
  fillChrome();
  setFab(null);
  document.body.classList.add("login");
  setTitle("Friday");
  const pass = h("input", { id: "pass", type: "password", autocomplete: "current-password", required: true });
  const field = h("label", { class: "field" }, h("span", {}, "Contraseña del panel"), pass);
  const go_ = h("button", { type: "submit", class: "primary" }, icon("chevron-right"), h("span", { class: "lbl" }, "Entrar"));
  const submit = async (ev) => {
    ev.preventDefault();
    go_.disabled = true;
    const r = await api("POST", "/login", { password: pass.value });
    go_.disabled = false;
    if (r.ok) { loginShown = false; render(route); return; }
    field.classList.add("error");
    field.querySelector(".err")?.remove();
    field.append(h("div", { class: "err" }, icon("alert-triangle", 16), h("span", {}, errText(r))));
    pass.select();
  };
  enterNext = true;
  setView(h("form", { class: "card login-card", onsubmit: submit },
    h("div", { class: "login-ico" }, icon("clock", 28)),
    h("h2", {}, "Entrar"),
    h("p", { class: "muted" }, "Escribe la contraseña del panel de Friday."),
    field, go_));
  pass.focus();
}

// ------------------------------------------------------------------ Inicio

// Estado de la vista de Inicio. El iframe del reloj vive aquí y no se recrea en los refrescos:
// solo se reenvían los datos (face-data) y se repintan las tarjetas de alarma.
let home = null; // { root, ring, next, live }
let homeSeq = 0;
const HOME_LOAD_MS = 8000; // si el motor no carga en este tiempo, se enseña el respaldo

async function doAction(name, body) {
  const r = await api("POST", `/api/do/${name}`, body);
  toast(r.ok ? "Hecho" : errText(r), !r.ok);
  if (route.tab === "home") renderHome();
  return r.ok;
}

const timeText = (ms) => new Date(ms).toLocaleTimeString("es-ES", { hour: "2-digit", minute: "2-digit" });

// "hoy a las 08:30", "mañana a las 08:30" o "mié a las 08:30".
function whenLabel(ms) {
  const at = new Date(ms);
  const day0 = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const diff = Math.round((day0(at) - day0(new Date())) / 86400000);
  const hour = at.toLocaleTimeString("es-ES", { hour: "2-digit", minute: "2-digit" });
  if (diff === 0) return `hoy a las ${hour}`;
  if (diff === 1) return `mañana a las ${hour}`;
  return `${at.toLocaleDateString("es-ES", { weekday: "short" }).replace(".", "")} a las ${hour}`;
}

// Tarjeta del reloj en vivo: el motor en un iframe de solo lectura (sin edición ni rejilla), con el
// diseño guardado y los datos reales. Si no hay diseño o el motor no carga, enseña la hora del reloj.
function liveClockCard(design, data, ms) {
  const live = { frame: null, ready: false, data, design, ms };
  const wrap = h("div", { class: "face-wrap" });
  const fallback = h("div", { class: "live-fallback", hidden: true },
    h("div", { class: "clock" }, timeText(ms)),
    h("div", { class: "muted live-msg" }, icon("alert-triangle", 16), h("span", {}, "No se pudo cargar la vista en vivo")));
  const card = h("div", { class: "card face-card live-card" }, wrap, fallback);
  const showFallback = (on) => { wrap.hidden = on; fallback.hidden = !on; };
  live.fallback = fallback;
  live.showFallback = showFallback;
  live.fit = () => fitFaceFrame(wrap, live.frame);
  live.push = () => {
    if (!live.ready || !live.frame || !live.frame.isConnected || !live.frame.contentWindow) return;
    live.frame.contentWindow.postMessage({ type: "face-data", data: live.data }, location.origin);
  };
  if (!design) { showFallback(true); return { card, live }; }

  const frame = h("iframe", { class: "face-frame", src: "/face/index.html?preview=1", scrolling: "no", tabindex: "-1", title: "Reloj en vivo" });
  live.frame = frame;
  // Si `load` nunca llega, a los 8 s se enseña el respaldo (y no si ya se salió de Inicio).
  live.timer = setTimeout(() => { if (!live.ready && frame.isConnected) showFallback(true); }, HOME_LOAD_MS);
  frame.addEventListener("load", () => {
    clearTimeout(live.timer);
    // Con 404, 500 o sin red Chrome también dispara `load`: se comprueba que el motor esté vivo (window.Face).
    let alive = false;
    try { alive = !!frame.contentWindow && typeof frame.contentWindow.Face === "object"; } catch { alive = false; }
    if (!alive) { live.ready = false; showFallback(true); return; }
    live.ready = true;
    showFallback(false);
    live.fit();
    frame.contentWindow.postMessage({ type: "face-render", design, opts: { bgBase: "/api/bg/" } }, location.origin);
    live.push();
  });
  wrap.append(frame);
  if (window.ResizeObserver) new ResizeObserver(() => live.fit()).observe(wrap);
  return { card, live };
}

function ringingCard(s) {
  if (!s.ringing) return null;
  return h("div", { class: "card ring stack" },
    h("div", { class: "ring-head" }, icon("bell", 24), h("strong", {}, `Sonando: ${s.ringing_label || "Alarma"}`)),
    h("div", { class: "row ring-actions" }, btn("Apagar", () => doAction("stop"), "", "x"), btn("Posponer", () => doAction("snooze"), "ghost", "clock")));
}

function nextAlarmCard(s) {
  const a = s.next_alarm;
  if (!a) {
    return h("div", { class: "card" }, emptyState("bell", "No hay alarmas activas",
      btn("Crear alarma", () => go({ tab: "alarms", edit: "new" }), "", "plus")));
  }
  const open = () => go(a.id != null ? { tab: "alarms", edit: a.id } : { tab: "alarms" });
  return h("div", {
    class: "card alarm-card", role: "button", tabindex: "0",
    onclick: open,
    onkeydown: (ev) => { if (ev.key === "Enter" || ev.key === " ") { ev.preventDefault(); open(); } },
  },
  h("div", {},
    h("div", { class: "muted next-kicker" }, "Próxima alarma"),
    h("div", { class: "t" }, a.time),
    h("div", { class: "muted" }, `${a.label || "Alarma"} · ${whenLabel(a.at)}`)),
  icon("chevron-right", 24));
}

function quickActionsCard() {
  const say = h("input", { id: "say", type: "text", placeholder: "Decir en el reloj…", maxlength: "300", "aria-label": "Texto para decir en el reloj", enterkeyhint: "send" });
  const send = async (ev) => {
    ev.preventDefault();
    if (!say.value.trim()) return;
    if (await doAction("speak", { text: say.value })) say.value = "";
  };
  return h("div", { class: "card stack" }, h("h2", {}, "Acciones rápidas"),
    btn("Probar alarma en 10 s", () => doAction("test"), "", "play"),
    btn("Buenos días", () => doAction("briefing"), "ghost", "sun"),
    h("form", { class: "row say-row", onsubmit: send }, say, btn("Decir", null, "", "mic", { type: "submit" })));
}

function paintHomeCards(s) {
  home.ring.replaceChildren(...[ringingCard(s)].filter(Boolean));
  home.next.replaceChildren(nextAlarmCard(s));
  if (home.live) {
    home.live.ms = s.time;
    home.live.fallback.querySelector(".clock").textContent = timeText(s.time);
  }
}

async function renderHome() {
  // Con la vista ya montada solo se refrescan estado y datos: el iframe y el campo de texto se conservan.
  if (home && home.root.isConnected) return refreshHome();
  const mine = ++homeSeq;
  if (home && home.live) clearTimeout(home.live.timer);
  const [r, rd, rf] = await Promise.all([api("GET", "/api/status"), api("GET", "/api/design"), api("GET", "/api/face-data")]);
  if (route.tab !== "home" || mine !== homeSeq) return;
  if (!r.ok) { showError(r); return; }
  const s = r.data;
  const design = rd.ok && rd.data.design ? rd.data.design : null;
  const data = rf.ok ? rf.data : (typeof SAMPLE_DATA !== "undefined" ? SAMPLE_DATA : {});
  const { card, live } = liveClockCard(design, data, s.time);
  home = { root: h("div", { class: "home" }), ring: h("div", {}), next: h("div", {}), live };
  home.root.append(card, home.ring, home.next, quickActionsCard());
  paintHomeCards(s);
  setView(home.root);
  live.fit();
}

async function refreshHome() {
  const mine = homeSeq;
  const [r, rf] = await Promise.all([api("GET", "/api/status"), api("GET", "/api/face-data")]);
  if (route.tab !== "home" || mine !== homeSeq || !home || !home.root.isConnected) return;
  if (rf.ok) { home.live.data = rf.data; home.live.push(); }
  if (r.ok) paintHomeCards(r.data);
}

setInterval(() => {
  if (document.visibilityState === "visible" && route.tab === "home" && !form && !loginShown) renderHome();
}, 10_000);

// ------------------------------------------------------------------ Alarmas

// El botón + flotante vive fuera de #view (la animación de entrada de la vista lo
// desplazaría). Solo hay uno; render() y el login lo quitan.
function setFab(node) {
  document.getElementById("fab")?.remove();
  if (node) document.body.append(node);
}

const DAY_NAMES = { mon: "Lunes", tue: "Martes", wed: "Miércoles", thu: "Jueves", fri: "Viernes", sat: "Sábado", sun: "Domingo" };

// Estado de una alarma en su tarjeta: icono y texto, nunca solo color.
const statusNode = (on) => h("span", { class: "status" }, icon(on ? "check" : "x", 16), h("span", {}, on ? "Activa" : "Apagada"));

function alarmCard(a) {
  const open = () => go({ tab: "alarms", edit: a.id });
  const label = a.label || "Alarma";
  const status = h("span", {}, statusNode(a.enabled));
  const toggle = h("input", {
    type: "checkbox", role: "switch", checked: a.enabled,
    "aria-label": `Activar ${label} de las ${a.time}`,
    onchange: async (ev) => {
      const on = ev.target.checked;
      ev.target.disabled = true;
      const r = await api("PUT", `/api/alarms/${a.id}`, { enabled: on });
      ev.target.disabled = false;
      if (!r.ok) { ev.target.checked = !on; toast(errText(r), true); return; }
      a.enabled = on;
      card.classList.toggle("off", !on);
      status.replaceChildren(statusNode(on));
      toast(on ? "Alarma activada" : "Alarma desactivada");
    },
  });
  const card = h("div", { class: "card alarm-card" + (a.enabled ? "" : " off"), onclick: open },
    h("button", { type: "button", class: "alarm-open", "aria-label": `Editar ${label}, ${a.time}, ${daysText(a.days)}`, onclick: (ev) => { ev.stopPropagation(); open(); } },
      h("span", { class: "t" }, a.time),
      h("span", { class: "name" }, label),
      h("span", { class: "meta" }, daysText(a.days))),
    h("div", { class: "alarm-side", onclick: (ev) => ev.stopPropagation() },
      h("label", { class: "switch" }, toggle),
      status));
  return card;
}

async function renderAlarms() {
  const r = await api("GET", "/api/alarms");
  if (route.tab !== "alarms" || route.edit != null) return;
  if (!r.ok) { showError(r); return; }
  const all = r.data.alarms || [];
  const alarms = all.filter((a) => (a.kind || "alarm") === "alarm" && !a.trigger_at);
  const others = all.filter((a) => !alarms.includes(a));
  const create = () => go({ tab: "alarms", edit: "new" });
  const active = alarms.filter((a) => a.enabled).length;

  setView(
    alarms.length ? h("p", { class: "list-head muted" }, `${active === 1 ? "1 activa" : `${active} activas`} de ${alarms.length}`) : null,
    alarms.length ? alarms.map(alarmCard)
      : h("div", { class: "card" }, emptyState("bell", "No hay alarmas",
        h("p", { class: "muted fab-hint" }, "Pulsa ", h("span", { class: "plus-ico" }, icon("plus", 16)), " para crear una"))),
    others.length ? h("div", { class: "card" }, h("h2", {}, "Temporizadores y pospuestas"),
      others.map((a) => h("div", { class: "row between others-row" },
        h("span", {}, `${a.label || "Temporizador"} `, h("span", { class: "t" }, a.time ? `· ${a.time}` : "")),
        btn("Cancelar", async () => {
          const r2 = await api("DELETE", `/api/alarms/${a.id}`);
          toast(r2.ok ? "Cancelado" : errText(r2), !r2.ok); renderAlarms();
        }, "small ghost", "x", { "aria-label": `Cancelar ${a.label || "temporizador"}` })))) : null,
  );
  setFab(h("button", { id: "fab", type: "button", class: "fab", "aria-label": "Nueva alarma", title: "Nueva alarma", onclick: create }, icon("plus", 28)));
}

// Opción numérica por alarma: "Ajuste general" (null, manda el de Ajustes) o un valor propio.
function optionNumber(key, label, min, max, unit, fallback) {
  const general = form.current[key] == null;
  const input = h("input", {
    type: "number", min, max, step: "1", inputmode: "numeric", "aria-label": `${label} (${min}–${max} ${unit})`,
    value: general ? "" : form.current[key], disabled: general, placeholder: "General",
    oninput: (ev) => { setField(key, ev.target.value === "" ? "" : Number(ev.target.value)); },
  });
  // La pista del rango solo tiene sentido con valor propio; en general se dice de dónde sale.
  const hint = h("div", { class: "muted hint" });
  const paintHint = (gen) => { hint.textContent = gen ? "Usa el valor general" : `Valor propio entre ${min} y ${max} ${unit}`; };
  paintHint(general);
  const check = h("input", {
    type: "checkbox", checked: general, "aria-label": `${label}: usar el ajuste general`,
    onchange: (ev) => {
      input.disabled = ev.target.checked;
      paintHint(ev.target.checked);
      if (ev.target.checked) { input.value = ""; setField(key, null); }
      else { input.value = fallback; setField(key, fallback); input.focus(); }
    },
  });
  return h("div", { class: "opt", "data-key": key },
    h("div", { class: "opt-head" }, h("span", {}, label), h("label", { class: "general" }, h("span", {}, "General"), check)),
    h("div", { class: "unit-input" }, input, h("span", { class: "unit" }, unit)),
    hint);
}

// Buenos días al apagarla: mismo patrón (general o valor propio), con interruptor.
function optionBriefing() {
  const general = form.current.briefing == null;
  const sw = switchRow("Buenos días del asistente", form.current.briefing === true, (v) => setField("briefing", v));
  const input = sw.querySelector("input");
  input.disabled = general;
  const check = h("input", {
    type: "checkbox", checked: general, "aria-label": "Al apagarla: usar el ajuste general",
    onchange: (ev) => {
      input.disabled = ev.target.checked;
      if (ev.target.checked) { input.checked = false; setField("briefing", null); }
      else { input.checked = true; setField("briefing", true); }
    },
  });
  return h("div", { class: "opt", "data-key": "briefing" },
    h("div", { class: "opt-head" }, h("span", {}, "Al apagarla"), h("label", { class: "general" }, h("span", {}, "General"), check)),
    sw);
}

function soundPicker(sounds, reload) {
  const known = new Set(sounds.map((s) => s.id));
  const cur = form.current.sound;
  const select = h("select", { onchange: (ev) => setField("sound", ev.target.value || null) },
    h("option", { value: "", selected: cur == null }, "Tono por defecto"),
    sounds.map((s) => h("option", { value: s.id, selected: s.id === cur }, `${s.name} (${s.kind === "file" ? "archivo" : "tono"})`)));
  const file = h("input", { type: "file", accept: "audio/*,.mp3,.ogg,.wav,.m4a", hidden: true,
    onchange: async (ev) => {
      const f = ev.target.files[0];
      if (!f) return;
      if (f.size > 5 * 1024 * 1024) { toast("El sonido supera 5 MB", true); return; }
      toast("Subiendo…");
      const r = await api("POST", `/api/sounds?name=${encodeURIComponent(f.name)}`, undefined, f);
      if (!r.ok) { toast(errText(r), true); return; }
      toast("Sonido subido al reloj");
      setField("sound", r.data.id);
      reload();
    } });
  return h("div", { class: "opt", "data-key": "sound" },
    h("label", { class: "field" }, h("span", { class: "section-label" }, icon("music", 16), "Sonido"), select),
    cur != null && !known.has(cur) ? h("div", { class: "warn" }, icon("alert-triangle", 16), h("span", {}, "Ese sonido ya no está en el reloj: sonará el tono por defecto.")) : null,
    h("div", { class: "row sound-actions" },
      btn("Escuchar 5 s", async () => {
        const r = await api("POST", "/api/sounds/preview", { id: form.current.sound });
        if (!r.ok) toast(errText(r), true);
      }, "small ghost", "play"),
      btn("Subir sonido…", () => file.click(), "small ghost", "upload"), file));
}

async function renderAlarmEdit(id) {
  const [ra, rs] = await Promise.all([api("GET", "/api/alarms"), api("GET", "/api/sounds")]);
  if (route.edit !== id) return;
  if (!ra.ok) { showError(ra); return; }
  const isNew = id === "new";
  const found = (ra.data.alarms || []).find((a) => a.id === id);
  if (!isNew && !found) { setView(errorState("Esa alarma ya no existe.", () => render({ tab: "alarms" }), "Volver a Alarmas")); return; }
  const src = isNew ? NEW_ALARM : found;
  const fields = ["time", "label", "days", "enabled", "sound", "max_volume", "ramp_s", "snooze_min", "briefing"];
  const original = Object.fromEntries(fields.map((k) => [k, src[k] ?? NEW_ALARM[k]]));
  // La rueda solo sabe de HH:MM válidas: si el reloj trae otra cosa, se parte de 07:00.
  if (!WheelCore.parseTime(original.time)) original.time = NEW_ALARM.time;
  startForm(original, async (c) => {
    const r = isNew ? await api("POST", "/api/alarms", c) : await api("PUT", `/api/alarms/${id}`, c);
    return { ...r, next: { tab: "alarms" } };
  }, isNew);
  const sounds = rs.ok ? rs.data.sounds || [] : [];
  paintAlarmEdit(id, isNew, sounds);
}

function paintAlarmEdit(id, isNew, sounds) {
  flushWheels(); // al repintar, un valor de la rueda aún sin avisar no se pierde
  const c = form.current;
  const sameDays = (a, b) => a.length === b.length && a.every((d) => b.includes(d));

  const days = h("div", { class: "days", role: "group", "aria-label": "Días de la semana" });
  const presets = h("div", { class: "presets", role: "group", "aria-label": "Atajos de días" });
  const helper = h("p", { class: "muted days-help" });
  const paintDays = () => {
    days.replaceChildren(...DAYS.map(([k, l]) => h("button", {
      type: "button", class: "day", "aria-pressed": c.days.includes(k) ? "true" : "false", "aria-label": DAY_NAMES[k], title: DAY_NAMES[k],
      onclick: () => {
        setField("days", c.days.includes(k) ? c.days.filter((d) => d !== k) : DAYS.map((d) => d[0]).filter((d) => d === k || c.days.includes(d)));
        paintDays();
        days.querySelector(`[aria-label="${DAY_NAMES[k]}"]`).focus();
      } }, h("span", { class: "dot", "aria-hidden": "true" }, l))));
    presets.replaceChildren(...PRESETS.map(([name, list]) => h("button", {
      type: "button", class: "chip", "aria-pressed": sameDays(c.days, list) ? "true" : "false",
      onclick: () => { setField("days", [...list]); paintDays(); presets.querySelector(`[data-name="${name}"]`)?.focus(); },
      "data-name": name,
    }, name)));
    helper.textContent = c.days.length ? "" : "Sin días: suena una sola vez";
    helper.hidden = c.days.length > 0;
  };
  paintDays();

  const reloadSounds = async () => {
    const rs = await api("GET", "/api/sounds");
    if (rs.ok && form) paintAlarmEdit(id, isNew, rs.data.sounds || []);
  };
  const custom = ["max_volume", "ramp_s", "snooze_min", "briefing"].filter((k) => c[k] != null).length;
  const options = h("details", { class: "card opts", open: custom > 0 || null },
    h("summary", {}, icon("settings"), h("span", {}, "Opciones"),
      h("span", { class: "hint" }, custom ? (custom === 1 ? "1 propia" : `${custom} propias`) : "Ajuste general"),
      h("span", { class: "chev" }, icon("chevron-down"))),
    h("div", { class: "opts-body" },
      optionNumber("max_volume", "Volumen máximo", 1, 100, "%", 80),
      optionNumber("ramp_s", "Subida de volumen", 0, 600, "s", 20),
      optionNumber("snooze_min", "Minutos al posponer", 1, 60, "min", 5),
      optionBriefing()));

  setView(
    h("div", { class: "edit-head" },
      btn("Alarmas", () => go({ tab: "alarms" }), "ghost back", "chevron-left", { "aria-label": "Volver a Alarmas" })),
    h("div", { class: "card wheel-card", "data-key": "time" },
      timeWheel(c.time, (v) => setField("time", v))),
    h("div", { class: "card days-card", "data-key": "days" },
      h("span", { class: "section-label" }, icon("clock", 16), "Repetir"),
      days, presets, helper),
    h("div", { class: "card" },
      h("label", { class: "field", "data-key": "label" }, h("span", {}, "Nombre"),
        h("input", { type: "text", value: c.label, maxlength: "40", autocomplete: "off", oninput: (ev) => setField("label", ev.target.value) })),
      switchRow("Activa", c.enabled, (v) => setField("enabled", v), "enabled")),
    h("div", { class: "card" }, soundPicker(sounds, reloadSounds)),
    options,
    isNew ? null : h("div", { class: "card delete-card" }, btn("Borrar alarma", async () => {
      if (!(await confirmInPage("¿Borrar esta alarma? No se puede deshacer.", "Borrar"))) return;
      const r = await api("DELETE", `/api/alarms/${id}`);
      if (r.ok) { form = null; updateSaveBar(); toast("Alarma borrada"); render({ tab: "alarms" }); }
      else toast(errText(r), true);
    }, "danger", "trash-2")),
  );
  markErrors();
}

// ------------------------------------------------------------------ Ajustes

// Unidad de cada ajuste numérico; el esquema del reloj trae la unidad dentro de la etiqueta.
const SETTING_UNITS = {
  snooze_min: "min", alarm_ramp_s: "s", alarm_auto_snooze_min: "min", night_start: "h", night_end: "h",
  night_brightness: "%", day_brightness: "%", tts_rate: "×", lat: "°", lon: "°",
};

function settingInput(f) {
  const v = form.current[f.key];
  const label = String(f.label).replace(/\s*\([^)]*\)\s*$/, "");
  if (f.type === "bool") return switchRow(label, v, (on) => setField(f.key, on), f.key);
  const isNum = f.type === "int" || f.type === "float";
  const unit = SETTING_UNITS[f.key] || "";
  const input = h("input", isNum
    ? { type: "number", min: f.min, max: f.max, step: f.type === "int" ? "1" : "any", inputmode: f.type === "int" ? "numeric" : "decimal", value: v,
        oninput: (ev) => { setField(f.key, ev.target.value === "" ? "" : Number(ev.target.value)); } }
    : { type: "text", maxlength: f.max_len, value: v, autocomplete: "off", oninput: (ev) => setField(f.key, ev.target.value) });
  const hint = isNum ? `Entre ${f.min} y ${f.max}${unit ? " " + unit : ""}` : `Hasta ${f.max_len} caracteres`;
  return h("label", { class: "field setting", "data-key": f.key },
    h("span", { class: "setting-label" }, label),
    h("span", { class: "setting-control" }, input, unit ? h("span", { class: "unit", "aria-hidden": "true" }, unit) : null),
    h("span", { class: "hint" }, hint));
}

const fileSize = (bytes) => bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round((bytes || 0) / 1024))} KB`;

function connectionCard(status, panelVersion) {
  const online = !!(status && status.ok);
  const clockVersion = online ? status.data.face_version : null;
  const differs = clockVersion && panelVersion && clockVersion !== panelVersion;
  return h("div", { class: "card conn-card" },
    h("div", { class: "info-row " + (online ? "ok" : "bad") },
      icon(online ? "wifi" : "wifi-off", 20),
      h("span", {}, online ? "Conectado al reloj" : "Sin conexión con el reloj")),
    h("div", { class: "info-row" },
      icon("info", 20),
      h("span", {}, "Versión del motor"),
      h("span", { class: "info-val" }, clockVersion ? `reloj ${clockVersion}` : "reloj sin datos", panelVersion ? ` · panel ${panelVersion}` : "")),
    differs ? h("div", { class: "warn", role: "status" }, icon("alert-triangle", 16),
      h("span", {}, "El motor del reloj y el del panel son distintos: actualiza el panel o el reloj.")) : null);
}

function soundsCard(files, reload) {
  const body = files.length
    ? h("div", { class: "sound-list" }, files.map((s) => h("div", { class: "sound-row" },
      icon("music", 20),
      h("div", { class: "sound-name" }, h("span", {}, s.name), h("span", { class: "muted" }, fileSize(s.size))),
      btn("Borrar", async () => {
        if (!(await confirmInPage(`¿Borrar ${s.name}? Las alarmas que lo usen sonarán con el tono por defecto.`, "Borrar"))) return;
        const r2 = await api("DELETE", `/api/sounds/${encodeURIComponent(s.name)}`);
        toast(r2.ok ? "Sonido borrado" : errText(r2), !r2.ok);
        if (r2.ok) reload();
      }, "small ghost", "trash-2", { "aria-label": `Borrar ${s.name}` }))))
    : emptyState("music", "Ningún sonido subido todavía: súbelos desde la ficha de una alarma.");
  return h("div", { class: "card" }, h("h2", {}, "Sonidos subidos"), body);
}

async function renderSettings() {
  const [r, rs, st, rv] = await Promise.all([
    api("GET", "/api/settings"), api("GET", "/api/sounds"), api("GET", "/api/status"), api("GET", "/api/face-version")]);
  if (route.tab !== "settings") return;
  if (!r.ok) { showError(r); return; }
  startForm(r.data.settings, (c) => api("PUT", "/api/settings", c));
  const groups = new Map();
  for (const f of r.data.schema) { if (!groups.has(f.group)) groups.set(f.group, []); groups.get(f.group).push(f); }
  const soundsHost = h("div", {});
  const paintSounds = (res) => {
    const files = res.ok ? (res.data.sounds || []).filter((x) => x.kind === "file") : [];
    soundsHost.replaceChildren(soundsCard(files, async () => { if (route.tab === "settings") paintSounds(await api("GET", "/api/sounds")); }));
  };
  paintSounds(rs);
  setView(
    connectionCard(st, rv.ok ? rv.data.face_version : null),
    ...[...groups].map(([g, fs]) => h("div", { class: "card group" }, h("h2", {}, g), fs.map(settingInput))),
    soundsHost,
  );
  markErrors();
}

// ------------------------------------------------------------------ arranque

window.addEventListener("beforeunload", (ev) => { if (dirty()) { ev.preventDefault(); ev.returnValue = ""; } });
render(route);
