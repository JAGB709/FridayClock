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
  const ariaLabel = opts["aria-label"] || null;
  return h("button", { type: "button", ...opts, class: classes, onclick,
    "aria-label": ariaLabel, title: onlyIcon ? (opts.title || ariaLabel) : (opts.title || null) },
  iconName ? icon(iconName) : null,
  onlyIcon ? null : h("span", { class: "lbl" }, label));
}

/* Botón cuadrado de 48 px solo con icono, con nombre accesible y tooltip. */
function iconButton(iconName, label, onclick, cls = "ghost") {
  return btn("", onclick, cls, iconName, { "aria-label": label, title: label });
}

/* Estados de vista: vacío, cargando y error. Centrados, con icono y texto. */
function emptyState(iconName, text, action = null) {
  return h("div", { class: "state" },
    h("div", { class: "state-ico" }, icon(iconName, 28)),
    h("p", {}, text),
    action);
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
    for (const b of bar.children) {
      const on = b.dataset.value === String(v);
      b.setAttribute("aria-selected", on ? "true" : "false");
      b.tabIndex = on ? 0 : -1;
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

/* Aviso breve arriba, con icono. Los errores duran más. */
let toastTimer;
function toast(msg, bad = false) {
  const t = document.getElementById("toast");
  t.replaceChildren(icon(bad ? "alert-triangle" : "check"), h("span", {}, msg));
  t.className = bad ? "bad" : "";
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), bad ? 6000 : 3000);
}

/* Estado de la conexión en la cabecera: icono y texto, nunca solo color. */
function setConn(on, msg) {
  const p = document.getElementById("conn");
  const text = on ? "Conectado" : (msg || "Sin conexión con el reloj");
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
    const msgId = "dlg-msg-" + Date.now();
    const close = (v) => {
      document.removeEventListener("keydown", onKey, true);
      dlg.remove();
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
      h("div", { class: "card", role: "dialog", "aria-modal": "true", "aria-describedby": msgId, "aria-label": "Confirmar" },
        h("p", { id: msgId }, msg),
        h("div", { class: "row actions" }, cancel, ok)));
    document.body.append(dlg);
    document.addEventListener("keydown", onKey, true);
    cancel.focus();
  });
}
