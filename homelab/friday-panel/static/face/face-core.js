/* Lógica pura del motor de pantallas (sin DOM). La usan el WebView del reloj y la vista previa
   del panel. JavaScript clásico (ES2017): el WebView del reloj es Chrome 74 (sin ?. ni ??). */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.FaceCore = factory();
})(this, function () {
  "use strict";

  var COLS = 8;
  var DAYS = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
  var MONTHS = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
    "septiembre", "octubre", "noviembre", "diciembre"];

  function pad(n) { return (n < 10 ? "0" : "") + n; }

  function cellRect(el, width, height, rows) {
    var cw = width / COLS;
    var ch = height / rows;
    return { left: el.x * cw, top: el.y * ch, width: el.w * cw, height: el.h * ch };
  }

  function minutesOf(hm) {
    var m = /^(\d\d):(\d\d)$/.exec(hm || "");
    return m ? Number(m[1]) * 60 + Number(m[2]) : null;
  }

  function activeProfile(profiles, minuteOfDay) {
    var best = null, bestMin = -1, latest = null, latestMin = -1;
    for (var i = 0; i < (profiles || []).length; i++) {
      var m = minutesOf(profiles[i].from);
      if (m === null) continue;
      if (m > latestMin) { latest = profiles[i]; latestMin = m; }
      if (m <= minuteOfDay && m > bestMin) { best = profiles[i]; bestMin = m; }
    }
    return best || latest;
  }

  function dayOfYear(date) {
    var start = new Date(date.getFullYear(), 0, 0);
    var diff = date - start + (start.getTimezoneOffset() - date.getTimezoneOffset()) * 60000;
    return Math.floor(diff / 86400000);
  }

  function dailyQuote(quotes, date) {
    if (!quotes || !quotes.length) return "";
    return quotes[dayOfYear(date) % quotes.length];
  }

  function formatClock(date, hour12, seconds) {
    var h = date.getHours();
    if (hour12) { h = h % 12; if (h === 0) h = 12; }
    var s = (hour12 ? String(h) : pad(h)) + ":" + pad(date.getMinutes());
    if (seconds) s += ":" + pad(date.getSeconds());
    return s;
  }

  function formatDate(date, fmt) {
    var d = DAYS[date.getDay()];
    var mo = MONTHS[date.getMonth()];
    if (fmt === "short") return d.slice(0, 3) + " " + date.getDate() + " " + mo.slice(0, 3);
    if (fmt === "numeric") return pad(date.getDate()) + "/" + pad(date.getMonth() + 1) + "/" + date.getFullYear();
    return d.charAt(0).toUpperCase() + d.slice(1) + ", " + date.getDate() + " de " + mo;
  }

  function analogAngles(date) {
    var s = date.getSeconds(), m = date.getMinutes(), h = date.getHours() % 12;
    return { h: h * 30 + m * 0.5, m: m * 6 + s * 0.1, s: s * 6 };
  }

  /* Tamaño de letra (px) para que un texto de `len` caracteres quepa en la caja.
     "clock": una línea grande; "line": una línea; "block": varias líneas. Nunca baja de 8 px. */
  function fitText(len, boxW, boxH, maxPx, mode) {
    var n = Math.max(1, len | 0);
    var cap = maxPx || 9999;
    if (mode === "clock") return Math.max(8, Math.floor(Math.min(boxW / (n * 0.55), boxH * 0.82, cap)));
    if (mode === "line") return Math.max(8, Math.floor(Math.min(boxW / (n * 0.55), boxH * 0.5, cap)));
    var size = Math.floor(Math.min(boxH * 0.45, cap));
    while (size > 8) {
      var perLine = Math.max(1, Math.floor(boxW / (size * 0.55)));
      var lines = Math.ceil(n / perLine);
      if (lines * size * 1.2 <= boxH) break;
      size -= 1;
    }
    return Math.max(8, size);
  }

  // Icono (nombre Lucide) y texto por separado: el motor dibuja el SVG, nunca un emoji.
  function weatherParts(w, detail) {
    if (!w) return { icon: "", text: "—" };
    var s = w.temp + "°";
    if (detail) {
      s += " · " + w.description + " · ↑" + w.max + "° ↓" + w.min + "°";
      if (w.rain >= 30) s += " · lluvia " + w.rain + "%";
    }
    return { icon: w.icon || "", text: s };
  }

  function nextAlarmParts(na, showLabel) {
    if (!na) return null;
    return { icon: "bell", text: (showLabel !== false ? na.label + " · " : "") + na.when + " " + na.time };
  }

  function forecastItems(mode, count, f) {
    if (!f) return [];
    var n = Math.max(2, Math.min(5, count || 4));
    if (mode === "days") {
      return (f.days || []).slice(0, n).map(function (d) {
        return { label: d.d, icon: d.icon, text: d.max + "°/" + d.min + "°" };
      });
    }
    return (f.hours || []).slice(0, n).map(function (h) {
      return { label: pad(h.h) + "h", icon: h.icon, text: h.temp + "°" };
    });
  }

  function bgCss(bg, base) {
    bg = bg || {};
    var fallback = bg.color || "#000000";
    if (bg.type === "gradient") {
      return "linear-gradient(" + (bg.angle == null ? 160 : bg.angle) + "deg, " + bg.from + ", " + bg.to + ")";
    }
    return fallback;
  }

  function fontStyle(font) {
    if (font === "inter") return { family: "\"Inter\", sans-serif", weight: 300, digital: false };
    if (font === "thin") return { family: "\"sans-serif-thin\", sans-serif", weight: 100, digital: false };
    if (font === "bold") return { family: "sans-serif", weight: 700, digital: false };
    if (font === "mono") return { family: "monospace", weight: 400, digital: false };
    if (font === "digital") return { family: "monospace", weight: 400, digital: true };
    return { family: "sans-serif", weight: 400, digital: false };
  }

  function justify(align) {
    return align === "left" ? "flex-start" : align === "right" ? "flex-end" : "center";
  }

  function clamp(n, lo, hi) { return Math.max(lo, Math.min(hi, n)); }

  function photoStyle(spec) {
    spec = spec || {};
    var fit = spec.fit === "contain" ? "contain" : spec.fit === "fill" ? "100% 100%" : "cover";
    var zoom = clamp(typeof spec.zoom === "number" ? spec.zoom : 1, 1, 3);
    var px = clamp(typeof spec.pos_x === "number" ? spec.pos_x : 50, 0, 100);
    var py = clamp(typeof spec.pos_y === "number" ? spec.pos_y : 50, 0, 100);
    var pos = px + "% " + py + "%";
    return { size: fit, position: pos, transform: zoom === 1 ? "none" : "scale(" + zoom + ")", origin: pos };
  }

  function nextPhotoIndex(count, current, order, rnd) {
    if (!(count > 0)) return -1;
    if (count === 1) return 0;
    if (order === "random") {
      var r = Math.floor((rnd ? rnd() : Math.random()) * (count - 1));
      if (r >= current && current >= 0) r += 1;
      return Math.min(r, count - 1);
    }
    return current < 0 ? 0 : (current + 1) % count;
  }

  function classifySwipe(dx, dy) {
    var ax = Math.abs(dx), ay = Math.abs(dy);
    if (ax > 60 && ax > ay * 1.5) return dx > 0 ? "right" : "left";
    if (ay > 60 && ay > ax * 1.5) return dy > 0 ? "down" : "up";
    return null;
  }

  function snapMove(el, dxCells, dyCells, rows) {
    return {
      x: clamp(el.x + dxCells, 0, COLS - el.w),
      y: clamp(el.y + dyCells, 0, rows - el.h),
    };
  }

  /* ids ordenados de arriba a abajo: devuelve el que sigue al seleccionado (cicla); sin seleccionado, el de arriba. */
  function pickUnder(ids, selectedId) {
    if (!ids || !ids.length) return null;
    var i = ids.indexOf(selectedId);
    return i < 0 ? ids[0] : ids[(i + 1) % ids.length];
  }

  /* ids ordenados de delante hacia atrás según la lista del diseño (el último de la lista va delante); los desconocidos, al final. */
  function sortFrontToBack(ids, order) {
    var out = (ids || []).slice();
    function rank(id) { return (order || []).indexOf(id); }
    out.sort(function (a, b) {
      var ra = rank(a), rb = rank(b);
      if (ra < 0 && rb < 0) return 0;
      if (ra < 0) return 1;
      if (rb < 0) return -1;
      return rb - ra;
    });
    return out;
  }

  /* Posición de un elemento anclado a `target` (sin salirse de la rejilla se comprueba aparte).
     side: right|left|below|above; gap: casillas de separación. El otro eje se alinea con el borde del ancla. */
  function anchorPos(target, el, side, gap) {
    var g = gap || 0;
    if (side === "right") return { x: target.x + target.w + g, y: target.y };
    if (side === "left") return { x: target.x - g - el.w, y: target.y };
    if (side === "below") return { x: target.x, y: target.y + target.h + g };
    if (side === "above") return { x: target.x, y: target.y - g - el.h };
    return { x: el.x, y: el.y };
  }

  /* Copia de la lista con los elementos anclados puestos donde mandan sus anclas (en cadena).
     Un ancla que falta, un ciclo o una posición fuera de la rejilla dejan el elemento donde está:
     el dibujo nunca falla por un anclaje roto. Los elementos sin `anchor` salen intactos. */
  function resolveAnchors(list, rows) {
    var out = [], byId = {}, i;
    for (i = 0; i < (list || []).length; i++) { out.push(Object.assign({}, list[i])); byId[list[i].id] = out[i]; }
    var state = {};
    function place(e) {
      if (!e.anchor) return;
      if (state[e.id] === 2) return;
      if (state[e.id] === 1) return; // ciclo
      state[e.id] = 1;
      var t = byId[e.anchor.to];
      if (t && t !== e) {
        place(t);
        var p = anchorPos(t, e, e.anchor.side, e.anchor.gap);
        if (p.x >= 0 && p.y >= 0 && p.x + e.w <= COLS && p.y + e.h <= rows) { e.x = p.x; e.y = p.y; }
      }
      state[e.id] = 2;
    }
    for (i = 0; i < out.length; i++) place(out[i]);
    return out;
  }

  return {
    anchorPos: anchorPos, resolveAnchors: resolveAnchors,
    COLS: COLS, cellRect: cellRect, minutesOf: minutesOf, activeProfile: activeProfile,
    dayOfYear: dayOfYear, dailyQuote: dailyQuote, formatClock: formatClock, formatDate: formatDate,
    analogAngles: analogAngles, fitText: fitText, weatherParts: weatherParts, nextAlarmParts: nextAlarmParts,
    forecastItems: forecastItems, bgCss: bgCss, fontStyle: fontStyle, justify: justify,
    photoStyle: photoStyle, nextPhotoIndex: nextPhotoIndex, classifySwipe: classifySwipe, snapMove: snapMove,
    pickUnder: pickUnder, sortFrontToBack: sortFrontToBack,
  };
});
