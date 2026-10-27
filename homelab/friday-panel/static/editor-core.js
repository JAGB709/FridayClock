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
    photos: {
      label: "Fotos", w: 4, h: 3,
      defaults: { images: [], interval_s: 10, transition: "fade", order: "sequential", fit: "cover", zoom: 1, pos_x: 50, pos_y: 50 },
      fields: [
        { key: "images", kind: "photolist", label: "Fotos del carrusel" },
        { key: "interval_s", kind: "number", label: "Segundos entre fotos (3–600)", min: 3, max: 600 },
        { key: "transition", kind: "select", label: "Transición", options: [["fade", "Fundido"], ["none", "Corte directo"]] },
        { key: "order", kind: "select", label: "Orden", options: [["sequential", "En orden"], ["random", "Al azar"]] },
        { key: "fit", kind: "photofit", label: "Ajuste de la foto" },
      ],
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

  var PHOTO_SIDE = 1600;

  function downscaleSize(w, h, maxSide) {
    var side = Math.max(w, h);
    if (side <= maxSide) return { w: w, h: h };
    var k = maxSide / side;
    return { w: Math.max(1, Math.round(w * k)), h: Math.max(1, Math.round(h * k)) };
  }

  function indexOfId(list, id) {
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return i;
    return -1;
  }

  /* Devuelve una lista nueva con el elemento `id` movido a la posición `to` (0..n-1); si no cambia nada, la misma lista copiada. */
  function relocate(list, id, to) {
    var out = list.slice();
    var i = indexOfId(out, id);
    if (i < 0) return out;
    var t = Math.max(0, Math.min(out.length - 1, to));
    if (t === i) return out;
    var item = out.splice(i, 1)[0];
    out.splice(t, 0, item);
    return out;
  }

  function bringToFront(list, id) { return relocate(list, id, list.length - 1); }
  function sendToBack(list, id) { return relocate(list, id, 0); }
  function moveUp(list, id) { var i = indexOfId(list, id); return i < 0 ? list.slice() : relocate(list, id, i + 1); }
  function moveDown(list, id) { var i = indexOfId(list, id); return i < 0 ? list.slice() : relocate(list, id, i - 1); }


  // ---------------------------------------------------------------- 3b: grupos, anclajes, alinear, duplicar

  var MAX_ELEMENTS = 12;
  var SIDES = [["right", "A la derecha"], ["left", "A la izquierda"], ["below", "Debajo"], ["above", "Encima"]];

  function copyList(list) { return list.map(function (e) { return Object.assign({}, e); }); }
  function byId(list, id) { var i = indexOfId(list, id); return i < 0 ? null : list[i]; }

  /* Misma regla que face-core.anchorPos y DesignSchema.kt. */
  function anchorPos(target, el, side, gap) {
    var g = gap || 0;
    if (side === "right") return { x: target.x + target.w + g, y: target.y };
    if (side === "left") return { x: target.x - g - el.w, y: target.y };
    if (side === "below") return { x: target.x, y: target.y + target.h + g };
    return { x: target.x, y: target.y - g - el.h };
  }

  /* Pone cada elemento anclado donde manda su ancla (en cadena). Devuelve {list} o {error} si alguno
     se saldría de la rejilla o hay un ciclo. No modifica la lista de entrada. */
  function applyAnchors(list, rows) {
    var out = copyList(list), state = {}, error = null;
    function place(e) {
      if (!e.anchor || state[e.id] === 2 || error) return;
      if (state[e.id] === 1) { error = "Los anclajes forman un ciclo"; return; }
      state[e.id] = 1;
      var t = byId(out, e.anchor.to);
      if (!t || t === e) { delete e.anchor; state[e.id] = 2; return; }
      place(t);
      if (error) return;
      var p = anchorPos(t, e, e.anchor.side, e.anchor.gap);
      if (!inBounds({ x: p.x, y: p.y, w: e.w, h: e.h }, rows)) { error = "El anclaje sacaría un elemento de la rejilla"; return; }
      e.x = p.x; e.y = p.y;
      state[e.id] = 2;
    }
    for (var i = 0; i < out.length; i++) place(out[i]);
    return error ? { error: error } : { list: out };
  }

  /* Ancla `id` a `to` por un lado con `gap` casillas (0..3). */
  function setAnchor(list, id, to, side, gap, rows) {
    var e = byId(list, id), t = byId(list, to);
    if (!e || !t) return { error: "El elemento no existe" };
    if (id === to) return { error: "Un elemento no puede anclarse a sí mismo" };
    var g = Math.max(0, Math.min(3, gap | 0));
    var next = copyList(list);
    byId(next, id).anchor = { to: to, side: side, gap: g };
    return applyAnchors(next, rows);
  }

  function clearAnchor(list, id) {
    var next = copyList(list), e = byId(next, id);
    if (e) delete e.anchor;
    return next;
  }

  /* Los miembros del grupo de `id` (o solo el elemento si no tiene grupo). */
  function groupMembers(list, id) {
    var e = byId(list, id);
    if (!e) return [];
    if (!e.group) return [e];
    return list.filter(function (o) { return o.group === e.group; });
  }

  function groupIds(list) {
    var seen = [];
    list.forEach(function (e) { if (e.group && seen.indexOf(e.group) < 0) seen.push(e.group); });
    return seen;
  }

  /* Un grupo de un solo miembro no es válido: se disuelve. */
  function dropLoneGroups(list) {
    var count = {};
    list.forEach(function (e) { if (e.group) count[e.group] = (count[e.group] || 0) + 1; });
    list.forEach(function (e) { if (e.group && count[e.group] < 2) delete e.group; });
    return list;
  }

  /* Agrupa los `ids` (2 o más) en un grupo nuevo; si alguno ya tenía grupo, pasa al nuevo. */
  function groupElements(list, ids) {
    var uniq = ids.filter(function (v, i) { return ids.indexOf(v) === i && byId(list, v); });
    if (uniq.length < 2) return { error: "Elige al menos 2 elementos para agrupar" };
    var name = uniqueId("grupo", groupIds(list));
    var next = copyList(list);
    uniq.forEach(function (v) { byId(next, v).group = name; });
    return { list: dropLoneGroups(next), group: name };
  }

  function ungroup(list, id) {
    var e = byId(list, id);
    var next = copyList(list);
    if (e && e.group) next.forEach(function (o) { if (o.group === e.group) delete o.group; });
    return next;
  }

  /* Quita un elemento: los anclados a él quedan sueltos y los grupos de un solo miembro se disuelven. */
  function removeElement(list, id) {
    var next = copyList(list.filter(function (e) { return e.id !== id; }));
    next.forEach(function (e) { if (e.anchor && e.anchor.to === id) delete e.anchor; });
    return dropLoneGroups(next);
  }

  /* Mueve a `id` (y a todo su grupo) a la posición (x, y). Los miembros anclados a algo fuera del grupo
     sueltan el ancla; los anclados dentro del grupo la conservan; los de fuera anclados a un miembro lo
     siguen. Devuelve {list} o {error}. */
  function moveGroup(list, id, x, y, rows) {
    var e = byId(list, id);
    if (!e) return { error: "El elemento no existe" };
    var inGroup = {};
    groupMembers(list, id).forEach(function (m) { inGroup[m.id] = true; });
    var dx = x - e.x, dy = y - e.y;
    var next = copyList(list);
    for (var i = 0; i < next.length; i++) {
      var m = next[i];
      if (!inGroup[m.id]) continue;
      if (m.anchor && (m.id === id || !inGroup[m.anchor.to])) delete m.anchor;
      m.x += dx; m.y += dy;
      if (!inBounds(m, rows)) return { error: "Se sale de la rejilla" };
    }
    return applyAnchors(next, rows);
  }

  /* Cambia ancho y alto de `id`; los que están anclados a él lo siguen. */
  function resizeWithAnchors(list, id, w, h, rows) {
    var next = copyList(list), e = byId(next, id);
    if (!e) return { error: "El elemento no existe" };
    e.w = w; e.h = h;
    if (!inBounds(e, rows)) return { error: "Se sale de la rejilla" };
    return applyAnchors(next, rows);
  }

  var ALIGN_MODES = ["left", "center", "right", "top", "middle", "bottom"];

  /* Alinea los `ids` (2 o más) respecto al conjunto. Un elemento alineado suelta su ancla. */
  function alignElements(list, ids, mode, rows) {
    var sel = list.filter(function (e) { return ids.indexOf(e.id) >= 0; });
    if (sel.length < 2) return { error: "Elige al menos 2 elementos para alinear" };
    if (ALIGN_MODES.indexOf(mode) < 0) return { error: "Alineación desconocida" };
    var x0 = Math.min.apply(null, sel.map(function (e) { return e.x; }));
    var x1 = Math.max.apply(null, sel.map(function (e) { return e.x + e.w; }));
    var y0 = Math.min.apply(null, sel.map(function (e) { return e.y; }));
    var y1 = Math.max.apply(null, sel.map(function (e) { return e.y + e.h; }));
    var next = copyList(list);
    next.forEach(function (e) {
      if (ids.indexOf(e.id) < 0) return;
      if (mode === "left") e.x = x0;
      else if (mode === "right") e.x = x1 - e.w;
      else if (mode === "center") e.x = Math.floor((x0 + x1 - e.w) / 2);
      else if (mode === "top") e.y = y0;
      else if (mode === "bottom") e.y = y1 - e.h;
      else e.y = Math.floor((y0 + y1 - e.h) / 2);
      delete e.anchor;
    });
    return applyAnchors(next, rows);
  }

  /* Reparte 3 o más elementos con huecos iguales (axis "h" o "v"); los extremos no se mueven. Con huecos
     no enteros, el sobrante se queda en los últimos. */
  function distributeElements(list, ids, axis, rows) {
    var sel = list.filter(function (e) { return ids.indexOf(e.id) >= 0; });
    if (sel.length < 3) return { error: "Elige al menos 3 elementos para distribuir" };
    var hor = axis === "h";
    var pos = hor ? "x" : "y", size = hor ? "w" : "h";
    sel.sort(function (a, b) { return a[pos] - b[pos]; });
    var start = sel[0][pos];
    var end = sel[sel.length - 1][pos] + sel[sel.length - 1][size];
    var total = 0;
    sel.forEach(function (e) { total += e[size]; });
    var free = end - start - total;
    if (free < 0) return { error: "No hay sitio para repartirlos sin solaparse" };
    var gaps = sel.length - 1, base = Math.floor(free / gaps), extra = free - base * gaps;
    var cursor = start, next = copyList(list);
    sel.forEach(function (s, i) {
      var t = byId(next, s.id);
      t[pos] = cursor;
      delete t.anchor;
      cursor += s[size] + base + (i >= gaps - extra ? 1 : 0);
    });
    return applyAnchors(next, rows);
  }

  function countType(list, type) { return list.filter(function (e) { return e.type === type; }).length; }

  /* Copia `source` (elementos, con sus grupos y los anclajes entre ellos) dentro de `dest`. Los ids y
     grupos se renuevan; los anclajes a elementos que no se copian se sueltan. La copia entera va al
     primer hueco libre (o, si no lo hay, 1 casilla en diagonal, que puede solapar). Falla si supera 12
     elementos, el máximo de fotos (`maxPhotos`: 2, o 1 en la alarma) o la rejilla. */
  function cloneInto(dest, source, rows, maxPhotos, sameScreen) {
    if (!source.length) return { error: "No hay nada que copiar" };
    if (dest.length + source.length > MAX_ELEMENTS) return { error: "Máximo " + MAX_ELEMENTS + " elementos por pantalla" };
    if (countType(dest, "photos") + countType(source, "photos") > maxPhotos) return { error: "Máximo " + maxPhotos + " elementos de fotos en esta pantalla" };
    var x0 = Math.min.apply(null, source.map(function (e) { return e.x; }));
    var y0 = Math.min.apply(null, source.map(function (e) { return e.y; }));
    var bw = Math.max.apply(null, source.map(function (e) { return e.x + e.w; })) - x0;
    var bh = Math.max.apply(null, source.map(function (e) { return e.y + e.h; })) - y0;
    if (bw > COLS || bh > rows) return { error: "No cabe en la rejilla de esa pantalla" };
    function freeAt(ox, oy) {
      return source.every(function (e) {
        return !collides(dest, { x: e.x - x0 + ox, y: e.y - y0 + oy, w: e.w, h: e.h });
      });
    }
    var ox = null, oy = null;
    if (!sameScreen && x0 + bw <= COLS && y0 + bh <= rows && freeAt(x0, y0)) { ox = x0; oy = y0; }
    for (var y = 0; ox === null && y + bh <= rows; y++) {
      for (var x = 0; ox === null && x + bw <= COLS; x++) if (freeAt(x, y)) { ox = x; oy = y; }
    }
    if (ox === null) { ox = Math.min(x0 + 1, COLS - bw); oy = Math.min(y0 + 1, rows - bh); }
    var ids = dest.map(function (e) { return e.id; });
    var groups = groupIds(dest);
    var idMap = {}, groupMap = {};
    var copies = source.map(function (e) {
      var c = Object.assign({}, e);
      c.id = uniqueId(e.id.replace(/_\d+$/, ""), ids); ids.push(c.id); idMap[e.id] = c.id;
      c.x = e.x - x0 + ox; c.y = e.y - y0 + oy;
      if (e.group) {
        if (!groupMap[e.group]) { groupMap[e.group] = uniqueId(e.group.replace(/_\d+$/, ""), groups); groups.push(groupMap[e.group]); }
        c.group = groupMap[e.group];
      }
      if (e.images) c.images = e.images.slice();
      if (e.quotes) c.quotes = e.quotes.slice();
      return c;
    });
    copies.forEach(function (c, i) {
      var a = source[i].anchor;
      if (a && idMap[a.to]) c.anchor = { to: idMap[a.to], side: a.side, gap: a.gap || 0 };
      else delete c.anchor;
    });
    return { list: dest.concat(copies), ids: copies.map(function (c) { return c.id; }) };
  }

  /* Duplica `id` (con su grupo) en la misma pantalla. */
  function duplicateElements(list, id, rows, maxPhotos) {
    var members = groupMembers(list, id);
    if (!members.length) return { error: "El elemento no existe" };
    return cloneInto(list, members, rows, maxPhotos, true);
  }

  /* Copia `id` (con su grupo) de una pantalla a otra. */
  function copyToScreen(srcList, id, destList, destRows, destMaxPhotos) {
    var members = groupMembers(srcList, id);
    if (!members.length) return { error: "El elemento no existe" };
    return cloneInto(destList, members, destRows, destMaxPhotos, false);
  }

  return {
    MAX_ELEMENTS: MAX_ELEMENTS, SIDES: SIDES, ALIGN_MODES: ALIGN_MODES, anchorPos: anchorPos,
    applyAnchors: applyAnchors, setAnchor: setAnchor, clearAnchor: clearAnchor, groupMembers: groupMembers,
    groupElements: groupElements, ungroup: ungroup, removeElement: removeElement, moveGroup: moveGroup,
    resizeWithAnchors: resizeWithAnchors, alignElements: alignElements, distributeElements: distributeElements,
    duplicateElements: duplicateElements, copyToScreen: copyToScreen,
    PHOTO_SIDE: PHOTO_SIDE, downscaleSize: downscaleSize,
    COLS: COLS, TYPES: TYPES, COLOR_OPTIONS: COLOR_OPTIONS, overlaps: overlaps, inBounds: inBounds,
    collides: collides, findFreeSpot: findFreeSpot, slug: slug, uniqueId: uniqueId, newElement: newElement,
    bringToFront: bringToFront, sendToBack: sendToBack, moveUp: moveUp, moveDown: moveDown,
  };
});
