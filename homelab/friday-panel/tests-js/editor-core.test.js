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
  assert.deepEqual(Object.keys(E.TYPES).sort(), ["clock", "date", "forecast", "next_alarm", "photos", "text", "weather_now"]);
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
