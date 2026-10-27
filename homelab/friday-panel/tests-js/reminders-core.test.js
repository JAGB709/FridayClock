"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const R = require("../static/reminders-core.js");

test("defaultAt es la siguiente hora en punto", () => {
  assert.equal(R.defaultAt(new Date(2026, 9, 27, 10, 20, 5)), "2026-10-27T11:00");
  assert.equal(R.defaultAt(new Date(2026, 9, 27, 23, 59)), "2026-10-28T00:00");
  assert.equal(R.defaultAt(new Date(2026, 11, 31, 23, 0)), "2027-01-01T00:00");
});

test("validate", () => {
  assert.equal(R.validate("Pastillas", "2026-10-28T09:00"), null);
  assert.match(R.validate("   ", "2026-10-28T09:00"), /texto/);
  assert.match(R.validate("x".repeat(301), "2026-10-28T09:00"), /300/);
  assert.match(R.validate("x", ""), /fecha/);
  assert.match(R.validate("x", "mañana"), /fecha/);
  assert.match(R.validate(null, "2026-10-28T09:00"), /texto/);
});

test("etiquetas", () => {
  assert.equal(R.repeatLabel("daily"), "Cada día");
  assert.equal(R.repeatLabel("none"), "Una vez");
  assert.equal(R.channelLabel("both"), "Ambos");
  assert.equal(R.channelLabel("raro"), "raro");
});

test("whenLabel", () => {
  assert.equal(R.whenLabel("2026-10-28T09:00+01:00"), "28 oct, 09:00");
  assert.equal(R.whenLabel("2026-01-05T18:30"), "5 ene, 18:30");
  assert.equal(R.whenLabel("basura"), "basura");
  assert.equal(R.whenLabel(null), "");
});
