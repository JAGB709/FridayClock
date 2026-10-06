/* Lógica pura del editor de pantallas (sin DOM): colisiones en la rejilla, hueco libre, ids y
   plantillas de elementos. Se prueba con node --test. */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.EditorCore = factory();
})(this, function () {
  "use strict";
  var COLS = 8;

  var COLOR_OPTIONS = [["fg", "Principal"], ["muted", "Secundario"], ["accent", "Acento"]];
  var TYPES = {
    clock: {
      label: "Hora", w: 4, h: 3, defaults: { style: "digital", seconds: false, hour12: false },
      fields: [
        { key: "style", kind: "select", label: "Estilo", options: [["digital", "Digital"], ["analog", "Agujas"]] },
        { key: "seconds", kind: "bool", label: "Mostrar segundos" },
        { key: "hour12", kind: "bool", label: "Formato de 12 horas" },
      ],
    },
    date: {
      label: "Fecha", w: 4, h: 1, defaults: { format: "long" },
      fields: [{ key: "format", kind: "select", label: "Formato", options: [["long", "Martes, 6 de octubre"], ["short", "mar 6 oct"], ["numeric", "06/10/2026"]] }],
    },
    weather_now: {
      label: "Tiempo ahora", w: 4, h: 1, defaults: { detail: true },
      fields: [{ key: "detail", kind: "bool", label: "Con descripción, máxima, mínima y lluvia" }],
    },
    forecast: {
      label: "Previsión", w: 8, h: 2, defaults: { mode: "hours", count: 4 },
      fields: [
        { key: "mode", kind: "select", label: "Qué mostrar", options: [["hours", "Próximas horas"], ["days", "Próximos días"]] },
        { key: "count", kind: "number", label: "Cuántos (2–5)", min: 2, max: 5 },
      ],
    },
    next_alarm: {
      label: "Próxima alarma", w: 4, h: 1, defaults: { show_label: true },
      fields: [{ key: "show_label", kind: "bool", label: "Mostrar el nombre de la alarma" }],
    },
    text: {
      label: "Texto", w: 8, h: 1, defaults: { mode: "fixed", text: "Hola" },
      fields: [
        { key: "mode", kind: "select", label: "Tipo de texto", options: [["fixed", "Texto fijo"], ["daily", "Frase del día"], ["alarm_label", "Nombre de la alarma sonando"]] },
        { key: "text", kind: "text", label: "Texto", max: 120, when: { key: "mode", value: "fixed" } },
        { key: "quotes", kind: "lines", label: "Frases (una por línea, hasta 20)", when: { key: "mode", value: "daily" } },
      ],
    },
  };

  function overlaps(a, b) {
    return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
  }

  function inBounds(r, rows) {
    return r.x >= 0 && r.y >= 0 && r.w >= 1 && r.h >= 1 && r.x + r.w <= COLS && r.y + r.h <= rows;
  }

  /* Primer elemento que choca con `cand` (ignorando el de id `ignoreId`), o null. */
  function collides(elements, cand, ignoreId) {
    for (var i = 0; i < elements.length; i++) {
      if (elements[i].id !== ignoreId && overlaps(elements[i], cand)) return elements[i];
    }
    return null;
  }

  function findFreeSpot(elements, w, h, rows) {
    for (var y = 0; y + h <= rows; y++) {
      for (var x = 0; x + w <= COLS; x++) {
        if (!collides(elements, { x: x, y: y, w: w, h: h })) return { x: x, y: y };
      }
    }
    return null;
  }

  function slug(name) {
    var s = String(name).normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase()
      .replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "").slice(0, 24);
    return s || "x";
  }

  function uniqueId(base, existing) {
    var b = slug(base);
    var id = b;
    var n = 2;
    while (existing.indexOf(id) >= 0) {
      var suffix = "_" + n;
      id = b.slice(0, 24 - suffix.length) + suffix;
      n++;
    }
    return id;
  }

  function newElement(type, id, spot, size) {
    var t = TYPES[type];
    if (!t) throw new Error("tipo desconocido: " + type);
    var e = { id: id, type: type, x: spot.x, y: spot.y, w: size ? size.w : t.w, h: size ? size.h : t.h };
    for (var k in t.defaults) e[k] = t.defaults[k];
    return e;
  }

  return {
    COLS: COLS, TYPES: TYPES, COLOR_OPTIONS: COLOR_OPTIONS, overlaps: overlaps, inBounds: inBounds,
    collides: collides, findFreeSpot: findFreeSpot, slug: slug, uniqueId: uniqueId, newElement: newElement,
  };
});
