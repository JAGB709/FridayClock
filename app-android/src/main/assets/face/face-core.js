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

  function weatherLine(w, detail) {
    if (!w) return "—";
    var s = w.icon + " " + w.temp + "°";
    if (detail) {
      s += " · " + w.description + " · ↑" + w.max + "° ↓" + w.min + "°";
      if (w.rain >= 30) s += " · lluvia " + w.rain + "%";
    }
    return s;
  }

  function nextAlarmLine(na, showLabel) {
    if (!na) return "";
    return "⏰ " + (showLabel !== false ? na.label + " · " : "") + na.when + " " + na.time;
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
    if (bg.type === "image" && bg.image) {
      var dim = (bg.dim || 0) / 100;
      var url = (base || "/bg/") + encodeURIComponent(bg.image);
      return "linear-gradient(rgba(0,0,0," + dim + "), rgba(0,0,0," + dim + ")), url(\"" + url + "\") center / cover no-repeat, " + fallback;
    }
    return fallback;
  }

  function fontStyle(font) {
    if (font === "thin") return { family: "\"sans-serif-thin\", sans-serif", weight: 100, digital: false };
    if (font === "bold") return { family: "sans-serif", weight: 700, digital: false };
    if (font === "mono") return { family: "monospace", weight: 400, digital: false };
    if (font === "digital") return { family: "monospace", weight: 400, digital: true };
    return { family: "sans-serif", weight: 400, digital: false };
  }

  function justify(align) {
    return align === "left" ? "flex-start" : align === "right" ? "flex-end" : "center";
  }

  return {
    COLS: COLS, cellRect: cellRect, minutesOf: minutesOf, activeProfile: activeProfile,
    dayOfYear: dayOfYear, dailyQuote: dailyQuote, formatClock: formatClock, formatDate: formatDate,
    analogAngles: analogAngles, fitText: fitText, weatherLine: weatherLine, nextAlarmLine: nextAlarmLine,
    forecastItems: forecastItems, bgCss: bgCss, fontStyle: fontStyle, justify: justify,
  };
});
