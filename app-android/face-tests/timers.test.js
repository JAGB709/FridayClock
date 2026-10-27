"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const C = require("../src/main/assets/face/face-core.js");

test("cronometro: acumula lo guardado y suma el tiempo en marcha", () => {
  assert.equal(C.stopwatchMs(null, 5000), 0);
  assert.equal(C.stopwatchMs({ running: false, base_ms: 4000, since: 0 }, 99999), 4000);
  assert.equal(C.stopwatchMs({ running: true, base_ms: 4000, since: 10000 }, 12500), 6500);
  assert.equal(C.stopwatchMs({ running: true, base_ms: 0, since: 10000 }, 9000), 0);
});

test("formatDuration: mm:ss, con horas y redondeo", () => {
  assert.equal(C.formatDuration(0, true), "00:00");
  assert.equal(C.formatDuration(61999, true), "01:01");
  assert.equal(C.formatDuration(61001, false), "01:02");
  assert.equal(C.formatDuration(3600000, false), "1:00:00");
  assert.equal(C.formatDuration(-5, false), "00:00");
});

test("timerItems: ordena por lo que queda, pausados al final, limita y no baja de cero", () => {
  const ts = [
    { id: 1, label: "Pasta", at: 100000 },
    { id: "p2", label: "Horno", paused: true, remaining_ms: 5000 },
    { id: 3, label: "Te", at: 70000 },
    { id: 4, label: "Viejo", at: 1000 },
  ];
  const r = C.timerItems(ts, 60000, 3);
  assert.deepEqual(r.map((x) => x.id), [4, 3, 1]);
  assert.equal(r[0].text, "00:00");
  assert.equal(r[1].text, "00:10");
  assert.equal(C.timerItems(ts, 60000, 9).length, 3);
  assert.deepEqual(C.timerItems(null, 0, 2), []);
  assert.equal(C.timerItems(ts, 60000, 1).length, 1);
});

test("clockOffset usa la hora del reloj si viene en los datos", () => {
  assert.equal(C.clockOffset({ now: 1500 }, 1000), 500);
  assert.equal(C.clockOffset({}, 1000), 0);
  assert.equal(C.clockOffset(null, 1000), 0);
});

test("controlCommand solo acepta ordenes conocidas", () => {
  assert.equal(C.controlCommand("stopwatch-toggle"), "stopwatch:toggle");
  assert.equal(C.controlCommand("stopwatch-reset"), "stopwatch:reset");
  assert.equal(C.controlCommand("timer-pause", 7), "timer:pause:7");
  assert.equal(C.controlCommand("timer-resume", "p7"), "timer:resume:p7");
  assert.equal(C.controlCommand("timer-cancel", "7;x"), null);
  assert.equal(C.controlCommand("otra", 1), null);
});
