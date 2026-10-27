/* Lógica pura de los recordatorios del panel (sin DOM). Se prueba con node --test. */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.RemindersCore = factory();
})(this, function () {
  "use strict";
  var MAX_TEXT = 300;
  var MONTHS = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
  var REPEATS = [["none", "Una vez"], ["daily", "Cada día"], ["weekdays", "Laborables"], ["weekly", "Cada semana"]];
  var CHANNELS = [["clock", "Reloj"], ["telegram", "Telegram"], ["both", "Ambos"]];

  function pad2(n) { return (n < 10 ? "0" : "") + n; }

  /* Valor inicial del campo de fecha: la próxima hora en punto, en el formato de datetime-local. */
  function defaultAt(now) {
    var d = new Date(now.getTime());
    d.setMinutes(0, 0, 0);
    d.setHours(d.getHours() + 1);
    return d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate()) + "T" + pad2(d.getHours()) + ":" + pad2(d.getMinutes());
  }

  /* Error en español, o null si el formulario vale. El puente vuelve a validarlo todo. */
  function validate(text, at) {
    var t = String(text == null ? "" : text).replace(/\s+/g, " ").trim();
    if (!t) return "Escribe el texto del recordatorio";
    if (t.length > MAX_TEXT) return "El texto supera " + MAX_TEXT + " caracteres";
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(String(at || ""))) return "Elige la fecha y la hora";
    return null;
  }

  function labelOf(list, value) {
    for (var i = 0; i < list.length; i++) if (list[i][0] === value) return list[i][1];
    return String(value);
  }
  function repeatLabel(v) { return labelOf(REPEATS, v); }
  function channelLabel(v) { return labelOf(CHANNELS, v); }

  /* "2026-10-28T09:00+01:00" -> "28 oct, 09:00". La hora ya viene en la zona del puente. */
  function whenLabel(iso) {
    var m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(String(iso || ""));
    if (!m) return String(iso || "");
    return Number(m[3]) + " " + (MONTHS[Number(m[2]) - 1] || "") + ", " + m[4] + ":" + m[5];
  }

  return { MAX_TEXT: MAX_TEXT, REPEATS: REPEATS, CHANNELS: CHANNELS, defaultAt: defaultAt, validate: validate,
    repeatLabel: repeatLabel, channelLabel: channelLabel, whenLabel: whenLabel };
});
