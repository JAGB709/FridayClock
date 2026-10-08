"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const W = require("../static/wheel-core.js");

test("pad2 y formatTime", () => {
  assert.equal(W.pad2(0), "00"); assert.equal(W.pad2(7), "07"); assert.equal(W.pad2(23), "23");
  assert.equal(W.formatTime(7, 5), "07:05"); assert.equal(W.formatTime(23, 59), "23:59");
});
test("parseTime valida", () => {
  assert.deepEqual(W.parseTime("07:30"), { h: 7, m: 30 });
  assert.deepEqual(W.parseTime("00:00"), { h: 0, m: 0 });
  assert.deepEqual(W.parseTime("23:59"), { h: 23, m: 59 });
  for (const bad of ["24:00", "12:60", "7:5", "", null, undefined, "ab:cd", "12-30"]) assert.equal(W.parseTime(bad), null, String(bad));
});
test("valuesFor", () => {
  assert.equal(W.valuesFor("hours").length, 24); assert.equal(W.valuesFor("minutes").length, 60);
  assert.equal(W.valuesFor("hours")[23], 23); assert.equal(W.valuesFor("minutes")[59], 59);
});
test("indexFor y valueAt acotan", () => {
  assert.equal(W.indexFor(5, 24), 5); assert.equal(W.indexFor(-3, 24), 0); assert.equal(W.indexFor(99, 24), 23);
  assert.equal(W.valueAt(-1, 60), 0); assert.equal(W.valueAt(70, 60), 59); assert.equal(W.valueAt(12, 60), 12);
});
test("nearestIndex a partir del scroll", () => {
  assert.equal(W.nearestIndex(0, 48, 24), 0); assert.equal(W.nearestIndex(47, 48, 24), 1);
  assert.equal(W.nearestIndex(24, 48, 24), 1); assert.equal(W.nearestIndex(23, 48, 24), 0);
  assert.equal(W.nearestIndex(48 * 100, 48, 24), 23); assert.equal(W.nearestIndex(-10, 48, 24), 0);
});
