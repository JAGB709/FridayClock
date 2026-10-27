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

  /* Cronómetro y temporizadores. Los datos traen tiempos absolutos del reloj (epoch en ms) y su
     propia hora `now`: el motor calcula la diferencia con la hora local para no depender de que
     el reloj y el navegador del panel estén en hora. */
  function clockOffset(data, localNow) {
    return data && typeof data.now === "number" ? data.now - localNow : 0;
  }

  function stopwatchMs(sw, nowMs) {
    if (!sw) return 0;
    var base = typeof sw.base_ms === "number" ? sw.base_ms : 0;
    var extra = sw.running && typeof sw.since === "number" ? Math.max(0, nowMs - sw.since) : 0;
    return Math.max(0, base + extra);
  }

  /* "mm:ss" y, desde la hora, "h:mm:ss". `down` redondea hacia abajo (cronómetro); si no, hacia arriba (cuenta atrás). */
  function formatDuration(ms, down) {
    var total = Math.max(0, down ? Math.floor(ms / 1000) : Math.ceil(ms / 1000));
    var h = Math.floor(total / 3600), m = Math.floor(total / 60) % 60, s = total % 60;
    return h > 0 ? h + ":" + pad(m) + ":" + pad(s) : pad(m) + ":" + pad(s);
  }

  /* Filas de temporizadores: primero los que antes acaban, luego los pausados. */
  function timerItems(timers, nowMs, count) {
    var n = Math.max(1, Math.min(3, count || 2));
    var items = (timers || []).map(function (t) {
      var paused = !!t.paused;
      var left = paused ? (t.remaining_ms || 0) : Math.max(0, (t.at || 0) - nowMs);
      return { id: t.id, label: t.label || "Temporizador", paused: paused, left: left, text: formatDuration(left, false) };
    });
    items.sort(function (a, b) { return ((a.paused ? 1 : 0) - (b.paused ? 1 : 0)) || (a.left - b.left); });
    return items.slice(0, n);
  }

  /* Órdenes que el motor puede mandar al reloj (lista cerrada); null si no vale. */
  function controlCommand(kind, id) {
    if (kind === "stopwatch-toggle") return "stopwatch:toggle";
    if (kind === "stopwatch-reset") return "stopwatch:reset";
    if (/^timer-(pause|resume|cancel)$/.test(kind) && /^p?\d{1,9}$/.test(String(id))) return "timer:" + kind.slice(6) + ":" + id;
    return null;
  }

  return {
    clockOffset: clockOffset, stopwatchMs: stopwatchMs, formatDuration: formatDuration,
    timerItems: timerItems, controlCommand: controlCommand,
    COLS: COLS, cellRect: cellRect, minutesOf: minutesOf, activeProfile: activeProfile,
    dayOfYear: dayOfYear, dailyQuote: dailyQuote, formatClock: formatClock, formatDate: formatDate,
    analogAngles: analogAngles, fitText: fitText, weatherParts: weatherParts, nextAlarmParts: nextAlarmParts,
    forecastItems: forecastItems, bgCss: bgCss, fontStyle: fontStyle, justify: justify,
    photoStyle: photoStyle, nextPhotoIndex: nextPhotoIndex, classifySwipe: classifySwipe, snapMove: snapMove,
    pickUnder: pickUnder, sortFrontToBack: sortFrontToBack,
  };
});
