"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const C = require("../src/main/assets/face/face-core.js");

test("cellRect reparte la rejilla 8x6 y la de la alarma 8x4", () => {
  assert.deepEqual(C.cellRect({ x: 2, y: 1, w: 3, h: 2 }, 800, 480, 6), { left: 200, top: 80, width: 300, height: 160 });
  assert.deepEqual(C.cellRect({ x: 0, y: 0, w: 8, h: 4 }, 800, 480, 4), { left: 0, top: 0, width: 800, height: 480 });
});

test("activeProfile: el de mayor from <= hora, con ciclo de medianoche", () => {
  const ps = [{ id: "dia", from: "07:00" }, { id: "noche", from: "23:00" }];
  assert.equal(C.activeProfile(ps, 3 * 60).id, "noche");
  assert.equal(C.activeProfile(ps, 7 * 60).id, "dia");
  assert.equal(C.activeProfile(ps, 22 * 60 + 59).id, "dia");
  assert.equal(C.activeProfile(ps, 23 * 60).id, "noche");
  assert.equal(C.activeProfile(ps, 0).id, "noche");
  assert.equal(C.activeProfile([], 100), null);
});

test("dayOfYear y frase del dia", () => {
  assert.equal(C.dayOfYear(new Date(2026, 0, 1)), 1);
  assert.equal(C.dayOfYear(new Date(2026, 9, 6)), 279);
  assert.equal(C.dailyQuote(["a", "b", "c"], new Date(2026, 0, 1)), "b");
  assert.equal(C.dailyQuote([], new Date(2026, 0, 1)), "");
  assert.equal(C.dailyQuote(undefined, new Date(2026, 0, 1)), "");
});

test("formatClock 24 h, 12 h y segundos", () => {
  assert.equal(C.formatClock(new Date(2026, 9, 6, 7, 5, 9), false, false), "07:05");
  assert.equal(C.formatClock(new Date(2026, 9, 6, 15, 4, 9), true, false), "3:04");
  assert.equal(C.formatClock(new Date(2026, 9, 6, 0, 0, 0), true, false), "12:00");
  assert.equal(C.formatClock(new Date(2026, 9, 6, 7, 5, 9), false, true), "07:05:09");
});

test("formatDate largo, corto y numerico", () => {
  const d = new Date(2026, 9, 6);
  assert.equal(C.formatDate(d, "long"), "Martes, 6 de octubre");
  assert.equal(C.formatDate(d, "short"), "mar 6 oct");
  assert.equal(C.formatDate(d, "numeric"), "06/10/2026");
  assert.equal(C.formatDate(d, "raro"), "Martes, 6 de octubre");
});

test("analogAngles", () => {
  const a = C.analogAngles(new Date(2026, 9, 6, 3, 15, 30));
  assert.ok(Math.abs(a.h - 97.5) < 1e-9);
  assert.ok(Math.abs(a.m - 93) < 1e-9);
  assert.equal(a.s, 180);
});

test("fitText: reloj, linea y bloque", () => {
  assert.equal(C.fitText(5, 800, 240, 9999, "clock"), 196);
  assert.equal(C.fitText(20, 800, 80, 9999, "line"), 40);
  assert.equal(C.fitText(19, 800, 160, 9999, "block"), 72);
  assert.equal(C.fitText(0, 800, 80, 9999, "line"), 40); // texto vacío: no revienta
  assert.equal(C.fitText(5000, 100, 80, 9999, "block"), 8); // nunca baja de 8 px
  assert.equal(C.fitText(5, 800, 240, 100, "clock"), 100); // respeta el máximo
  const size = C.fitText(200, 800, 160, 9999, "block");
  const perLine = Math.max(1, Math.floor(800 / (size * 0.55)));
  assert.ok(Math.ceil(200 / perLine) * size * 1.2 <= 160 || size === 8);
});

test("weatherLine y nextAlarmLine", () => {
  const w = { temp: 19, icon: "☀", description: "Despejado", max: 22, min: 12, rain: 10 };
  assert.equal(C.weatherLine(w, false), "☀ 19°");
  assert.equal(C.weatherLine(w, true), "☀ 19° · Despejado · ↑22° ↓12°");
  assert.equal(C.weatherLine({ temp: 8, icon: "☂", description: "Lluvia", max: 10, min: 6, rain: 80 }, true), "☂ 8° · Lluvia · ↑10° ↓6° · lluvia 80%");
  assert.equal(C.weatherLine(null, true), "—");
  const na = { label: "Trabajo", when: "mañana", time: "07:30" };
  assert.equal(C.nextAlarmLine(na, true), "⏰ Trabajo · mañana 07:30");
  assert.equal(C.nextAlarmLine(na, false), "⏰ mañana 07:30");
  assert.equal(C.nextAlarmLine(null, true), "");
});

test("forecastItems por horas y por dias, y sin datos", () => {
  const f = {
    hours: [{ h: 9, temp: 18, icon: "☀" }, { h: 11, temp: 21, icon: "☀" }, { h: 13, temp: 23, icon: "⛅" }],
    days: [{ d: "mié", min: 11, max: 22, icon: "☁" }],
  };
  assert.deepEqual(C.forecastItems("hours", 2, f), [{ label: "09h", icon: "☀", text: "18°" }, { label: "11h", icon: "☀", text: "21°" }]);
  assert.deepEqual(C.forecastItems("days", 5, f), [{ label: "mié", icon: "☁", text: "22°/11°" }]);
  assert.deepEqual(C.forecastItems("hours", 3, null), []);
  assert.equal(C.forecastItems("hours", 99, f).length, 3); // count se acota a 2..5
});

test("bgCss: color, degradado e imagen con color de respaldo", () => {
  assert.equal(C.bgCss({ type: "color", color: "#112233" }), "#112233");
  assert.equal(C.bgCss({ type: "gradient", from: "#000000", to: "#ffffff", angle: 90 }), "linear-gradient(90deg, #000000, #ffffff)");
  const img = C.bgCss({ type: "image", image: "mi playa.jpg", dim: 40, color: "#102030" }, "/bg/");
  assert.ok(img.indexOf('url("/bg/mi%20playa.jpg")') >= 0);
  assert.ok(img.indexOf("rgba(0,0,0,0.4)") >= 0);
  assert.ok(img.endsWith(", #102030")); // si la imagen falta, se ve el color
  assert.ok(C.bgCss({ type: "image", image: "x.jpg" }).endsWith(", #000000"));
  assert.equal(C.bgCss(undefined), "#000000");
});

test("fontStyle y justify", () => {
  assert.equal(C.fontStyle("thin").weight, 100);
  assert.equal(C.fontStyle("bold").weight, 700);
  assert.equal(C.fontStyle("digital").digital, true);
  assert.equal(C.fontStyle("desconocida").weight, 400);
  assert.equal(C.justify("left"), "flex-start");
  assert.equal(C.justify("right"), "flex-end");
  assert.equal(C.justify(undefined), "center");
});
