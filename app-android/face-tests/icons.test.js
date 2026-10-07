const test = require("node:test");
const assert = require("node:assert");
const Icons = require("../src/main/assets/face/icons.js");

test("icons: sprite con todos los nombres y svg con trazo de 2", () => {
  assert.ok(Icons.names.includes("bell") && Icons.names.includes("cloud-rain"));
  const s = Icons.svg("bell", 24);
  assert.ok(s.startsWith("<svg") && s.includes('stroke="currentColor"') && s.includes('stroke-width="2"'));
});

test("icons: un nombre desconocido o vacio no falla", () => {
  assert.equal(Icons.svg("no-existe", 24), "");
  assert.equal(Icons.svg("", 24), "");
  assert.equal(Icons.svg(undefined, 24), "");
  assert.equal(Icons.node("no-existe", 24), null);
});

test("icons: el juego incluye los iconos del panel", () => {
  for (const n of ["house", "layout-template", "chevron-left", "chevrons-up", "arrow-down", "layers", "wifi-off", "pencil", "copy", "music", "save", "rotate-ccw"]) {
    assert.ok(Icons.names.includes(n), n);
    assert.ok(Icons.svg(n, 24).startsWith("<svg"), n);
  }
});
