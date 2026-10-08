/* Lógica pura de la rueda de hora (sin DOM). Se prueba con node --test. */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.WheelCore = factory();
})(this, function () {
  "use strict";
  function pad2(n) { return (n < 10 ? "0" : "") + n; }
  function clamp(n, lo, hi) { return Math.max(lo, Math.min(hi, n)); }
  function valuesFor(kind) {
    var n = kind === "hours" ? 24 : 60, out = [];
    for (var i = 0; i < n; i++) out.push(i);
    return out;
  }
  function indexFor(value, count) { return clamp(Math.round(Number(value) || 0), 0, count - 1); }
  function valueAt(index, count) { return clamp(Math.round(Number(index) || 0), 0, count - 1); }
  function formatTime(h, m) { return pad2(h) + ":" + pad2(m); }
  function parseTime(s) {
    var m = /^(\d\d):(\d\d)$/.exec(typeof s === "string" ? s : "");
    if (!m) return null;
    var h = Number(m[1]), mi = Number(m[2]);
    return h <= 23 && mi <= 59 ? { h: h, m: mi } : null;
  }
  function nearestIndex(scrollTop, itemHeight, count) { return clamp(Math.round(scrollTop / itemHeight), 0, count - 1); }
  return { pad2: pad2, valuesFor: valuesFor, indexFor: indexFor, valueAt: valueAt, formatTime: formatTime, parseTime: parseTime, nearestIndex: nearestIndex };
});
