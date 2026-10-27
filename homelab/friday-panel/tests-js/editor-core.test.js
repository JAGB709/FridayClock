"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const E = require("../static/editor-core.js");

const el = (id, x, y, w, h) => ({ id, type: "text", x, y, w, h });

test("overlaps e inBounds", () => {
  assert.equal(E.overlaps({ x: 0, y: 0, w: 2, h: 2 }, { x: 1, y: 1, w: 2, h: 2 }), true);
  assert.equal(E.overlaps({ x: 0, y: 0, w: 2, h: 2 }, { x: 2, y: 0, w: 2, h: 2 }), false);
  assert.equal(E.inBounds({ x: 6, y: 0, w: 2, h: 1 }, 6), true);
  assert.equal(E.inBounds({ x: 7, y: 0, w: 2, h: 1 }, 6), false);
  assert.equal(E.inBounds({ x: 0, y: 5, w: 1, h: 2 }, 6), false);
  assert.equal(E.inBounds({ x: 0, y: 3, w: 1, h: 2 }, 4), false); // zona de la alarma: 8x4
  assert.equal(E.inBounds({ x: 0, y: 0, w: 0, h: 1 }, 6), false);
});

test("collides ignora el propio elemento", () => {
  const list = [el("a", 0, 0, 4, 3), el("b", 4, 0, 4, 3)];
  assert.equal(E.collides(list, { x: 0, y: 0, w: 5, h: 3 }, "a").id, "b");
  assert.equal(E.collides(list, { x: 0, y: 0, w: 4, h: 3 }, "a"), null);
  assert.equal(E.collides(list, { x: 0, y: 3, w: 8, h: 1 }), null);
});

test("findFreeSpot busca el primer hueco y devuelve null si no cabe", () => {
  const list = [el("a", 0, 0, 8, 3)];
  assert.deepEqual(E.findFreeSpot(list, 4, 1, 6), { x: 0, y: 3 });
  assert.deepEqual(E.findFreeSpot([], 8, 6, 6), { x: 0, y: 0 });
  const llena = [el("a", 0, 0, 8, 6)];
  assert.equal(E.findFreeSpot(llena, 1, 1, 6), null);
  assert.equal(E.findFreeSpot([], 8, 3, 2), null); // más alto que la rejilla
});

test("slug y uniqueId", () => {
  assert.equal(E.slug("Previsión del día"), "prevision_del_dia");
  assert.equal(E.slug("   "), "x");
  assert.equal(E.slug("a".repeat(40)).length, 24);
  assert.equal(E.uniqueId("Hora", []), "hora");
  assert.equal(E.uniqueId("Hora", ["hora"]), "hora_2");
  assert.equal(E.uniqueId("Hora", ["hora", "hora_2"]), "hora_3");
  assert.ok(E.uniqueId("a".repeat(40), ["a".repeat(24)]).length <= 24);
});

test("newElement toma los valores por defecto del tipo", () => {
  const e = E.newElement("clock", "hora", { x: 1, y: 2 });
  assert.deepEqual(e, { id: "hora", type: "clock", x: 1, y: 2, w: 4, h: 3, style: "digital", seconds: false, hour12: false });
  const t = E.newElement("text", "frase", { x: 0, y: 5 }, { w: 1, h: 1 });
  assert.equal(t.w, 1);
  assert.equal(t.mode, "fixed");
  assert.throws(() => E.newElement("foto", "x", { x: 0, y: 0 }));
});

test("todos los tipos tienen etiqueta, tamaño y campos", () => {
  for (const t of Object.keys(E.TYPES)) {
    const d = E.TYPES[t];
    assert.ok(d.label && d.w >= 1 && d.h >= 1 && Array.isArray(d.fields), t);
  }
  assert.deepEqual(Object.keys(E.TYPES).sort(), ["clock", "date", "forecast", "next_alarm", "photos", "stopwatch", "text", "timers", "weather_now"]);
});

test("downscaleSize: reduce el lado largo y no amplia", () => {
  assert.deepEqual(E.downscaleSize(4000, 3000, 1600), { w: 1600, h: 1200 });
  assert.deepEqual(E.downscaleSize(3000, 4000, 1600), { w: 1200, h: 1600 });
  assert.deepEqual(E.downscaleSize(800, 480, 1600), { w: 800, h: 480 });
  assert.deepEqual(E.downscaleSize(20000, 5, 1600), { w: 1600, h: 1 });
});

test("photos: plantilla por defecto", () => {
  const e = E.newElement("photos", "galeria", { x: 0, y: 0 });
  assert.equal(e.type, "photos");
  assert.deepEqual(e.images, []);
  assert.equal(e.interval_s, 10);
  assert.equal(e.transition, "fade");
  assert.equal(e.order, "sequential");
  assert.equal(e.fit, "cover");
  assert.equal(e.zoom, 1);
  assert.equal(e.w, 4);
  assert.equal(e.h, 3);
});

const ids = (l) => l.map((e) => e.id).join(",");
const L = () => [{ id: "a" }, { id: "b" }, { id: "c" }];

test("capas: traer al frente, enviar al fondo, subir y bajar", () => {
  assert.equal(ids(E.bringToFront(L(), "a")), "b,c,a");
  assert.equal(ids(E.bringToFront(L(), "c")), "a,b,c");
  assert.equal(ids(E.sendToBack(L(), "c")), "c,a,b");
  assert.equal(ids(E.sendToBack(L(), "a")), "a,b,c");
  assert.equal(ids(E.moveUp(L(), "a")), "b,a,c");
  assert.equal(ids(E.moveUp(L(), "c")), "a,b,c");
  assert.equal(ids(E.moveDown(L(), "c")), "a,c,b");
  assert.equal(ids(E.moveDown(L(), "a")), "a,b,c");
});

test("capas: no mutan la entrada y toleran ids desconocidos o un solo elemento", () => {
  const l = L();
  E.bringToFront(l, "a"); E.sendToBack(l, "c"); E.moveUp(l, "a"); E.moveDown(l, "c");
  assert.equal(ids(l), "a,b,c");
  assert.equal(ids(E.bringToFront(l, "zz")), "a,b,c");
  assert.equal(ids(E.moveUp([{ id: "solo" }], "solo")), "solo");
  assert.equal(ids(E.moveDown([], "x")), "");
});

// ---- 3b: grupos, anclajes, alinear, distribuir, duplicar, copiar
const T = (id, x, y, w, h, extra) => Object.assign({ id, type: "text", x, y, w, h }, extra || {});
const pos = (l, id) => { const e = l.find((o) => o.id === id); return [e.x, e.y]; };

test("agrupar necesita 2 y desagrupar disuelve; quitar un miembro deja el grupo en uno y lo disuelve", () => {
  const l = [T("a", 0, 0, 2, 1), T("b", 2, 0, 2, 1), T("c", 4, 0, 2, 1)];
  assert.ok(E.groupElements(l, ["a"]).error);
  const g = E.groupElements(l, ["a", "b"]);
  assert.equal(g.list[0].group, g.list[1].group);
  assert.equal(l[0].group, undefined); // no muta
  assert.equal(E.groupMembers(g.list, "a").length, 2);
  assert.equal(E.groupMembers(g.list, "c").length, 1);
  assert.equal(E.removeElement(g.list, "a").find((e) => e.id === "b").group, undefined);
  assert.equal(E.ungroup(g.list, "b").some((e) => e.group), false);
  const g2 = E.groupElements(g.list, ["b", "c"]); // b pasa al grupo nuevo y a queda solo
  assert.equal(g2.list.find((e) => e.id === "a").group, undefined);
});

test("moveGroup mueve todo el grupo, respeta la rejilla y no cambia nada si falla", () => {
  const l = E.groupElements([T("a", 0, 0, 2, 1), T("b", 2, 0, 2, 1), T("c", 0, 3, 1, 1)], ["a", "b"]).list;
  const r = E.moveGroup(l, "a", 1, 2, 6);
  assert.deepEqual(pos(r.list, "a"), [1, 2]);
  assert.deepEqual(pos(r.list, "b"), [3, 2]);
  assert.deepEqual(pos(r.list, "c"), [0, 3]);
  assert.ok(E.moveGroup(l, "a", 5, 0, 6).error); // b se saldría
  assert.deepEqual(pos(l, "b"), [2, 0]);
});

test("setAnchor coloca el elemento, rechaza ciclos, sí mismo y rejilla; seguirlo al mover el destino", () => {
  const l = [T("a", 0, 0, 2, 1), T("b", 5, 4, 2, 1), T("c", 5, 5, 1, 1)];
  const r = E.setAnchor(l, "b", "a", "right", 1, 6);
  assert.deepEqual(pos(r.list, "b"), [3, 0]);
  assert.deepEqual(r.list[1].anchor, { to: "a", side: "right", gap: 1 });
  assert.ok(E.setAnchor(l, "a", "a", "right", 0, 6).error);
  assert.ok(E.setAnchor(r.list, "a", "b", "left", 0, 6).error); // ciclo
  assert.ok(E.setAnchor(l, "b", "a", "right", 9, 6).error === undefined); // gap se acota a 3
  assert.ok(E.setAnchor([T("a", 5, 0, 3, 1), T("b", 0, 3, 3, 1)], "b", "a", "right", 0, 6).error); // se sale
  const mov = E.moveGroup(r.list, "a", 0, 2, 6);
  assert.deepEqual(pos(mov.list, "b"), [3, 2]); // el anclado sigue a su destino
  const res = E.resizeWithAnchors(r.list, "a", 4, 1, 6);
  assert.deepEqual(pos(res.list, "b"), [5, 0]);
});

test("mover un elemento anclado suelta su ancla; quitar el destino también", () => {
  const r = E.setAnchor([T("a", 0, 0, 2, 1), T("b", 4, 4, 2, 1)], "b", "a", "below", 0, 6).list;
  const m = E.moveGroup(r, "b", 6, 5, 6);
  assert.equal(m.list[1].anchor, undefined);
  assert.equal(E.removeElement(r, "a")[0].anchor, undefined);
  assert.equal(E.clearAnchor(r, "b")[1].anchor, undefined);
});

test("alinear: izquierda, derecha, centro, arriba, abajo y medio", () => {
  const l = [T("a", 1, 0, 2, 1), T("b", 4, 2, 3, 1), T("c", 0, 4, 1, 2)];
  assert.deepEqual(pos(E.alignElements(l, ["a", "b", "c"], "left", 6).list, "b"), [0, 2]);
  assert.deepEqual(pos(E.alignElements(l, ["a", "b"], "right", 6).list, "a"), [5, 0]);
  assert.deepEqual(pos(E.alignElements(l, ["a", "b"], "center", 6).list, "a"), [3, 0]);
  assert.deepEqual(pos(E.alignElements(l, ["a", "b"], "top", 6).list, "b"), [4, 0]);
  assert.deepEqual(pos(E.alignElements(l, ["a", "c"], "bottom", 6).list, "a"), [1, 5]);
  assert.deepEqual(pos(E.alignElements(l, ["a", "c"], "middle", 6).list, "a"), [1, 2]);
  assert.ok(E.alignElements(l, ["a"], "left", 6).error);
  assert.ok(E.alignElements(l, ["a", "b"], "nada", 6).error);
  assert.deepEqual(pos(l, "b"), [4, 2]); // no muta
});

test("distribuir reparte huecos iguales y deja los extremos", () => {
  const l = [T("a", 0, 0, 1, 1), T("b", 1, 0, 1, 1), T("c", 7, 0, 1, 1)];
  const r = E.distributeElements(l, ["a", "b", "c"], "h", 6).list;
  assert.deepEqual([pos(r, "a")[0], pos(r, "b")[0], pos(r, "c")[0]], [0, 3, 7]);
  const v = E.distributeElements([T("a", 0, 0, 1, 1), T("b", 0, 1, 1, 1), T("c", 0, 4, 1, 2)], ["a", "b", "c"], "v", 6).list;
  assert.deepEqual([pos(v, "a")[1], pos(v, "b")[1], pos(v, "c")[1]], [0, 2, 4]);
  assert.ok(E.distributeElements(l, ["a", "b"], "h", 6).error);
  assert.ok(E.distributeElements([T("a", 0, 0, 4, 1), T("b", 1, 0, 4, 1), T("c", 2, 0, 2, 1)], ["a", "b", "c"], "h", 6).error);
});

test("duplicar: ids nuevos, hueco libre, grupo y anclajes internos remapeados", () => {
  const base = E.setAnchor(E.groupElements([T("a", 0, 0, 2, 1), T("b", 4, 0, 2, 1)], ["a", "b"]).list, "b", "a", "right", 1, 6).list;
  const r = E.duplicateElements(base, "a", 6, 2);
  assert.equal(r.list.length, 4);
  const [c, d] = r.list.slice(2);
  assert.notEqual(c.group, base[0].group);
  assert.equal(c.group, d.group);
  assert.deepEqual(d.anchor, { to: c.id, side: "right", gap: 1 });
  assert.deepEqual(pos(r.list, d.id), [c.x + 3, c.y]);
  assert.equal(E.collides(base, c), null);
  assert.equal(base.length, 2);
});

test("duplicar respeta el máximo de 12 y de fotos, y suelta anclajes hacia fuera", () => {
  const doce = Array.from({ length: 12 }, (_, i) => T("e" + i, i % 8, Math.floor(i / 8), 1, 1));
  assert.ok(E.duplicateElements(doce, "e0", 6, 2).error);
  const p = (id, x) => ({ id, type: "photos", x, y: 0, w: 1, h: 1, images: ["a.jpg"] });
  assert.ok(E.duplicateElements([p("p1", 0), p("p2", 1)], "p1", 6, 2).error);
  assert.ok(E.duplicateElements([p("p1", 0)], "p1", 4, 1).error);
  const anc = E.setAnchor([T("a", 0, 0, 2, 1), T("b", 4, 4, 2, 1)], "b", "a", "below", 0, 6).list;
  assert.equal(E.duplicateElements(anc, "b", 6, 2).list[2].anchor, undefined);
});

test("copyToScreen: mantiene la posición si cabe, valida la rejilla de destino y renueva ids", () => {
  const src = [T("hora", 0, 0, 8, 3), T("nombre", 0, 3, 8, 1)];
  const dst = [T("hora", 0, 0, 8, 3)];
  const r = E.copyToScreen(src, "nombre", dst, 6, 2);
  assert.deepEqual(pos(r.list, r.ids[0]), [0, 3]);
  assert.ok(E.copyToScreen([T("f", 0, 0, 8, 5)], "f", [], 4, 1).error); // no cabe en la alarma (8x4)
  const r2 = E.copyToScreen(src, "hora", dst, 6, 2);
  assert.notEqual(r2.ids[0], "hora");
  assert.deepEqual(pos(r2.list, r2.ids[0]), [0, 3]); // ocupado: va al primer hueco
});

test("stopwatch y timers se crean con sus valores por defecto", () => {
  const sw = E.newElement("stopwatch", "cr", { x: 0, y: 0 });
  assert.deepEqual([sw.type, sw.w, sw.h, sw.controls], ["stopwatch", 4, 3, true]);
  const tm = E.newElement("timers", "tm", { x: 4, y: 0 });
  assert.deepEqual([tm.type, tm.count, tm.controls], ["timers", 2, true]);
});
