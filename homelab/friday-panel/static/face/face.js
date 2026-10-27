/* Motor de pantallas de Friday: dibuja el diseño (JSON) en una rejilla de 8×6.
   Lo cargan el WebView del reloj y la vista previa del panel: el mismo código en los dos.
   JavaScript clásico (ES2017): el WebView del reloj es Chrome 74 (sin ?. ni ??). */
(function () {
  "use strict";
  var Core = window.FaceCore;
  var SVGNS = "http://www.w3.org/2000/svg";
  var COLS = Core.COLS;
  var PREVIEW = /[?&]preview=1/.test(location.search);

  var S = {
    design: null, opts: {}, data: {}, screens: [], index: 0, rows: 6, rotate: 0,
    W: 0, H: 0, updaters: [], rotateTimer: null, timers: [], bgTimers: [],
  };
  var rootEl, contentEl, resizeTimer, lastMinute = -1;
  var pending = []; // llamadas a render/setData recibidas antes de que exista el DOM
  // El script va al final de <body>: los elementos ya existen, así render() funciona antes de init().
  rootEl = document.getElementById("root");
  contentEl = document.getElementById("content");

  function native(name, arg) {
    var n = window.Native;
    if (!n || !n[name]) return;
    try { if (arg === undefined) n[name](); else n[name](String(arg)); } catch (e) { /* sin puente */ }
  }
  function post(msg) {
    if (window.parent && window.parent !== window) window.parent.postMessage(msg, location.origin);
  }
  function el(tag, cls) { var e = document.createElement(tag); if (cls) e.className = cls; return e; }
  function svg(tag, attrs) {
    var e = document.createElementNS(SVGNS, tag);
    for (var k in attrs) e.setAttribute(k, attrs[k]);
    return e;
  }
  function photoUrl(name) { return (S.opts.bgBase || "/bg/") + encodeURIComponent(name); }

  function stylePhoto(node, url, spec) {
    var st = Core.photoStyle(spec);
    node.style.backgroundImage = "url(\"" + url + "\")";
    node.style.backgroundSize = st.size;
    node.style.backgroundPosition = st.position;
    node.style.transform = st.transform;
    node.style.transformOrigin = st.origin;
  }

  // ------------------------------------------------------------------ elegir qué se dibuja

  function firstTheme(design) {
    var ids = Object.keys(design.themes || {});
    return ids.length ? design.themes[ids[0]]
      : { bg: { type: "color", color: "#000000" }, fg: "#ffffff", muted: "#90a4ae", accent: "#4fc3f7", font: "thin" };
  }
  function themeOf(design, id) { return (design.themes && design.themes[id]) || firstTheme(design); }

  function pick(design, opts) {
    if (opts.alarm) {
      var a = design.alarm_screen || {};
      return { screens: [{ elements: a.elements || [] }], rows: 4, rotate: 0, theme: themeOf(design, opts.theme || a.theme) };
    }
    var d = new Date();
    var profile = null;
    var profiles = design.profiles || [];
    for (var i = 0; i < profiles.length; i++) if (profiles[i].id === opts.profile) profile = profiles[i];
    if (!profile) profile = Core.activeProfile(profiles, d.getHours() * 60 + d.getMinutes());
    var list = [];
    if (opts.screen) {
      if (design.screens && design.screens[opts.screen]) list.push(design.screens[opts.screen]);
    } else if (profile) {
      for (var k = 0; k < (profile.screens || []).length; k++) {
        var sc = design.screens && design.screens[profile.screens[k]];
        if (sc) list.push(sc);
      }
    }
    return {
      screens: list, rows: 6, rotate: profile && !opts.screen ? (profile.rotate_s || 0) : 0,
      theme: themeOf(design, opts.theme || (profile && profile.theme)),
    };
  }

  function applyTheme(theme, bgBase) {
    for (var bt = 0; bt < S.bgTimers.length; bt++) clearInterval(S.bgTimers[bt]);
    S.bgTimers = [];
    var f = Core.fontStyle(theme.font);
    rootEl.style.background = Core.bgCss(theme.bg, bgBase);
    rootEl.style.setProperty("--fg", theme.fg);
    rootEl.style.setProperty("--muted", theme.muted);
    rootEl.style.setProperty("--accent", theme.accent);
    rootEl.style.setProperty("--ff", f.family);
    rootEl.style.setProperty("--fw", f.weight);
    rootEl.setAttribute("data-digital", f.digital ? "1" : "0");
    var old = rootEl.querySelector(".bglayer");
    if (old) rootEl.removeChild(old);
    var bg = theme.bg || {};
    if (bg.type === "image" && bg.image) {
      var layer = el("div", "bglayer");
      var pic = el("div", "pl on");
      stylePhoto(pic, photoUrl(bg.image), bg);
      layer.appendChild(pic);
      var dim = el("div", "bgdim");
      dim.style.background = "rgba(0,0,0," + ((bg.dim || 0) / 100) + ")";
      layer.appendChild(dim);
      rootEl.insertBefore(layer, contentEl);
    } else if (bg.type === "photos" && bg.images && bg.images.length) {
      var pl = el("div", "bglayer");
      startCarousel(pl, bg.images, bg, S.bgTimers);
      var pdim = el("div", "bgdim");
      pdim.style.background = "rgba(0,0,0," + ((bg.dim || 0) / 100) + ")";
      pl.appendChild(pdim);
      rootEl.insertBefore(pl, contentEl);
    }
  }

  // ------------------------------------------------------------------ dibujo

  function render(design, opts) {
    if (!rootEl || !contentEl) { pending.push(function () { render(design, opts); }); return; }
    S.design = design;
    S.opts = opts || {};
    var p = pick(design, S.opts);
    S.screens = p.screens;
    S.rows = p.rows;
    S.rotate = p.rotate;
    if (S.index >= S.screens.length || S.opts.screen || S.opts.alarm) S.index = 0;
    applyTheme(p.theme, S.opts.bgBase);
    draw();
    scheduleRotation();
  }

  function draw() {
    S.W = rootEl.clientWidth;
    S.H = rootEl.clientHeight;
    for (var t = 0; t < S.timers.length; t++) clearInterval(S.timers[t]);
    S.timers = [];
    contentEl.textContent = "";
    S.updaters = [];
    var screen = S.screens[S.index] || { elements: [] };
    var list = screen.elements || [];
    for (var i = 0; i < list.length; i++) {
      try { contentEl.appendChild(buildElement(list[i])); }
      catch (e) { native("error", "elemento " + list[i].id + ": " + e.message); }
    }
    if (S.opts.grid) buildGrid();
    updateAll();
  }

  function buildGrid() {
    for (var c = 1; c < COLS; c++) {
      var v = el("div", "gridv"); v.style.left = (S.W / COLS * c) + "px"; contentEl.appendChild(v);
    }
    for (var r = 1; r < S.rows; r++) {
      var h = el("div", "gridh"); h.style.top = (S.H / S.rows * r) + "px"; contentEl.appendChild(h);
    }
  }

  function updateAll() {
    var now = new Date();
    for (var i = 0; i < S.updaters.length; i++) S.updaters[i](now, S.data);
  }

  function buildElement(e) {
    var r = Core.cellRect(e, S.W, S.H, S.rows);
    var box = el("div", "el");
    box.setAttribute("data-id", e.id);
    box.style.left = r.left + "px"; box.style.top = r.top + "px";
    box.style.width = r.width + "px"; box.style.height = r.height + "px";
    box.style.color = "var(--" + (e.color || "fg") + ")";
    box.style.justifyContent = Core.justify(e.align);
    box.style.textAlign = e.align || "center";
    var build = builders[e.type];
    if (build) build(e, box, r);
    if (S.opts.edit) {
      box.classList.add("edit");
      if (S.opts.selected === e.id) { box.classList.add("sel"); box.appendChild(el("div", "handle")); }
    }
    return box;
  }

  function iconNode(name, size) {
    var n = window.FridayIcons ? window.FridayIcons.node(name, size || "1em") : null;
    if (!n) return null;
    n.setAttribute("class", "ico");
    return n;
  }

  // Un elemento de texto: calcula el texto, lo ajusta a su caja y solo toca el DOM si cambia.
  // getText devuelve una cadena, {icon, text} o null (vacio).
  function textBuilder(box, r, getText, mode, onNode) {
    var node = el("div", "txt" + (mode === "block" ? " wrap" : ""));
    box.appendChild(node);
    if (onNode) onNode(node);
    var last = null;
    S.updaters.push(function (now, data) {
      var v = getText(now, data);
      var icon = v && typeof v === "object" ? v.icon : "";
      var s = v === null || v === undefined ? "" : (typeof v === "string" ? v : v.text);
      var key = icon + "|" + s;
      if (key === last) return;
      last = key;
      node.textContent = "";
      var ic = icon ? iconNode(icon, "1em") : null;
      if (ic) node.appendChild(ic);
      node.appendChild(document.createTextNode(s));
      node.style.fontSize = Core.fitText(s.length + (ic ? 2 : 0), r.width, r.height, 9999, mode) + "px";
    });
  }

  var SEG = { 0: "abcdef", 1: "bc", 2: "abged", 3: "abgcd", 4: "fgbc", 5: "afgcd", 6: "afgedc", 7: "abc", 8: "abcdefg", 9: "abcdfg" };
  var RECTS = { a: [10, 0, 50, 12], b: [58, 10, 12, 46], c: [58, 64, 12, 46], d: [10, 108, 50, 12], e: [0, 64, 12, 46], f: [0, 10, 12, 46], g: [10, 54, 50, 12] };

  function sevenSeg(text) {
    var x = 0;
    var g = svg("g", {});
    for (var i = 0; i < text.length; i++) {
      var ch = text.charAt(i);
      if (ch === ":") {
        g.appendChild(svg("rect", { x: x + 14, y: 30, width: 12, height: 12, rx: 3, fill: "currentColor" }));
        g.appendChild(svg("rect", { x: x + 14, y: 78, width: 12, height: 12, rx: 3, fill: "currentColor" }));
        x += 40;
        continue;
      }
      if (ch === " ") { x += 40; continue; }
      var on = SEG[ch] || "";
      for (var seg in RECTS) {
        var q = RECTS[seg];
        g.appendChild(svg("rect", {
          x: x + q[0], y: q[1], width: q[2], height: q[3], rx: 3, fill: "currentColor",
          opacity: on.indexOf(seg) >= 0 ? 1 : 0.1,
        }));
      }
      x += 90;
    }
    var root = svg("svg", { viewBox: "0 0 " + Math.max(x - 20, 10) + " 120", preserveAspectRatio: "xMidYMid meet" });
    root.style.width = "100%";
    root.style.height = "100%";
    root.appendChild(g);
    return root;
  }

  function hand(parent, len, width) {
    var l = svg("line", { x1: 100, y1: 100, x2: 100, y2: 100 - len, stroke: "currentColor", "stroke-width": width, "stroke-linecap": "round" });
    parent.appendChild(l);
    return l;
  }

  function buildAnalog(e, box, r) {
    var size = Math.min(r.width, r.height);
    var s = svg("svg", { viewBox: "0 0 200 200", width: size, height: size });
    s.appendChild(svg("circle", { cx: 100, cy: 100, r: 94, fill: "none", stroke: "currentColor", "stroke-width": 3 }));
    for (var i = 0; i < 12; i++) {
      var big = i % 3 === 0;
      s.appendChild(svg("line", {
        x1: 100, y1: 8, x2: 100, y2: big ? 22 : 15, stroke: "currentColor",
        "stroke-width": big ? 4 : 2, transform: "rotate(" + (i * 30) + " 100 100)",
      }));
    }
    var hh = hand(s, 52, 6), mm = hand(s, 78, 4), ss = e.seconds ? hand(s, 86, 2) : null;
    box.appendChild(s);
    S.updaters.push(function (now) {
      var a = Core.analogAngles(now);
      hh.setAttribute("transform", "rotate(" + a.h + " 100 100)");
      mm.setAttribute("transform", "rotate(" + a.m + " 100 100)");
      if (ss) ss.setAttribute("transform", "rotate(" + a.s + " 100 100)");
    });
  }

  // Carrusel compartido (elemento de fotos y fondo de tema). Dos capas con fundido;
  // la capa saliente suelta su imagen al terminar el fundido para no acumular memoria.
  function startCarousel(container, names, opts, timers) {
    names = (names || []).slice();
    var fade = opts.transition !== "none";
    var layers = [el("div", "pl" + (fade ? " fade" : "")), el("div", "pl" + (fade ? " fade" : ""))];
    container.appendChild(layers[0]); container.appendChild(layers[1]);
    var cur = -1, top = 0, busy = false;
    var order = opts.order === "random" ? "random" : "sequential";
    function show(i, tries) {
      if (busy) return;
      if (i < 0 || !names.length) return;
      busy = true;
      var img = new Image();
      img.onload = function () {
        var out = layers[top];
        var next = layers[1 - top];
        stylePhoto(next, photoUrl(names[i]), opts);
        next.classList.add("on");
        out.classList.remove("on");
        // Memoria: la capa saliente suelta su imagen cuando termina el fundido.
        if (fade) setTimeout(function () { if (!out.classList.contains("on")) out.style.backgroundImage = "none"; }, 700);
        else out.style.backgroundImage = "none";
        top = 1 - top;
        cur = i;
        busy = false;
      };
      img.onerror = function () {
        busy = false;
        if (tries < names.length) show(Core.nextPhotoIndex(names.length, i, order), tries + 1);
      };
      img.src = photoUrl(names[i]);
    }
    show(Core.nextPhotoIndex(names.length, -1, "sequential"), 1);
    var iv = Math.max(3, Math.min(600, opts.interval_s || 10)) * 1000;
    if (names.length > 1) {
      timers.push(setInterval(function () { show(Core.nextPhotoIndex(names.length, cur, order), 1); }, iv));
    }
  }

  // Botón de control (cronómetro y temporizadores). Solo en el reloj: ni en la edición ni en la vista previa.
  // Frena pointerdown/up para que el toque no cuente como gesto de la carátula.
  function ctrlButton(name, label, size, kind, id) {
    var b = el("button", "ctl");
    b.type = "button";
    b.setAttribute("aria-label", label);
    b.style.width = b.style.height = size + "px";
    var ic = iconNode(name, "55%");
    if (ic) b.appendChild(ic);
    var stop = function (ev) { ev.stopPropagation(); };
    b.addEventListener("pointerdown", stop);
    b.addEventListener("pointerup", stop);
    b.addEventListener("click", function (ev) {
      ev.stopPropagation();
      var cmd = Core.controlCommand(kind, id);
      if (cmd) native("control", cmd);
    });
    return b;
  }
  function nowMs(now) { return now.getTime() + (S.offset || 0); }
  function showControls(e) { return e.controls !== false && !S.opts.edit && !PREVIEW; }

  var builders = {
    stopwatch: function (e, box, r) {
      var wrap = el("div", "sw");
      var controls = showControls(e);
      var bsize = Math.max(24, Math.min(r.height * 0.28, r.width / 6));
      var txt = el("div", "txt");
      txt.style.height = (controls ? r.height - bsize - 8 : r.height) + "px";
      wrap.appendChild(txt);
      var toggle = null;
      if (controls) {
        var row = el("div", "ctlrow");
        toggle = ctrlButton("play", "Iniciar o pausar el cronómetro", bsize, "stopwatch-toggle");
        row.appendChild(toggle);
        row.appendChild(ctrlButton("rotate-ccw", "Reiniciar el cronómetro", bsize, "stopwatch-reset"));
        wrap.appendChild(row);
      }
      box.appendChild(wrap);
      var last = "", lastRun = null;
      S.updaters.push(function (now, data) {
        var sw = data.stopwatch;
        var s = Core.formatDuration(Core.stopwatchMs(sw, nowMs(now)), true);
        if (s !== last) {
          last = s;
          txt.textContent = s;
          txt.style.fontSize = Core.fitText(s.length, r.width, parseFloat(txt.style.height), 9999, "clock") + "px";
        }
        var run = !!(sw && sw.running);
        if (toggle && run !== lastRun) {
          lastRun = run;
          toggle.textContent = "";
          var ic = iconNode(run ? "pause" : "play", "55%");
          if (ic) toggle.appendChild(ic);
        }
      });
    },
    timers: function (e, box, r) {
      var n = Math.max(1, Math.min(3, e.count || 2));
      var controls = showControls(e);
      var wrap = el("div", "tms");
      box.appendChild(wrap);
      var rowH = r.height / n;
      var structure = null, cells = [];
      S.updaters.push(function (now, data) {
        var items = Core.timerItems(data.timers, nowMs(now), n);
        var key = items.map(function (t) { return t.id + (t.paused ? "p" : "r") + t.label; }).join("|");
        if (key !== structure) {
          structure = key;
          cells = [];
          wrap.textContent = "";
          if (!items.length) {
            var none = el("div", "tmempty");
            none.textContent = "Sin temporizadores";
            none.style.fontSize = Core.fitText(19, r.width, r.height, 9999, "line") + "px";
            wrap.appendChild(none);
            return;
          }
          var bsize = Math.max(24, Math.min(rowH * 0.7, r.width / 8));
          for (var i = 0; i < items.length; i++) {
            var t = items[i];
            var row = el("div", "tmrow" + (t.paused ? " paused" : ""));
            row.style.height = rowH + "px";
            var lab = el("div", "tmlabel");
            lab.textContent = t.label;
            lab.style.fontSize = Math.max(10, Math.floor(rowH * 0.22)) + "px";
            var tm = el("div", "tmtime");
            tm.style.fontSize = Core.fitText(5, r.width * 0.5, rowH, 9999, "line") + "px";
            var info = el("div", "tminfo");
            info.appendChild(lab);
            info.appendChild(tm);
            row.appendChild(info);
            if (controls) {
              row.appendChild(ctrlButton(t.paused ? "play" : "pause", t.paused ? "Reanudar" : "Pausar", bsize,
                t.paused ? "timer-resume" : "timer-pause", t.id));
              row.appendChild(ctrlButton("x", "Cancelar el temporizador", bsize, "timer-cancel", t.id));
            }
            wrap.appendChild(row);
            cells.push(tm);
          }
        }
        for (var j = 0; j < items.length; j++) {
          if (cells[j].textContent !== items[j].text) cells[j].textContent = items[j].text;
        }
      });
    },
    photos: function (e, box, r) {
      var wrap = el("div", "photos");
      box.appendChild(wrap);
      startCarousel(wrap, e.images, e, S.timers);
    },
    clock: function (e, box, r) {
      if (e.style === "analog") { buildAnalog(e, box, r); return; }
      var node = el("div", "txt");
      box.appendChild(node);
      var digital = rootEl.getAttribute("data-digital") === "1";
      if (digital) { node.style.width = r.width + "px"; node.style.height = r.height + "px"; }
      var last = "";
      S.updaters.push(function (now) {
        var s = Core.formatClock(now, !!e.hour12, !!e.seconds);
        if (s === last) return;
        last = s;
        if (digital) { node.textContent = ""; node.appendChild(sevenSeg(s)); }
        else { node.textContent = s; node.style.fontSize = Core.fitText(s.length, r.width, r.height, 9999, "clock") + "px"; }
      });
    },
    date: function (e, box, r) {
      textBuilder(box, r, function (now) { return Core.formatDate(now, e.format || "long"); }, "line");
    },
    weather_now: function (e, box, r) {
      textBuilder(box, r, function (now, data) { return Core.weatherParts(data.weather, !!e.detail); }, "line");
    },
    next_alarm: function (e, box, r) {
      textBuilder(box, r, function (now, data) {
        if (data.exact_alarm_missing) return { icon: "alert-triangle", text: "Concede el permiso de alarmas exactas" };
        return Core.nextAlarmParts(data.next_alarm, e.show_label);
      }, "line", function (node) {
        node.addEventListener("click", function () { if (S.data.exact_alarm_missing) native("permission"); });
      });
    },
    text: function (e, box, r) {
      textBuilder(box, r, function (now, data) {
        if (e.mode === "alarm_label") return data.ringing_label || "";
        if (e.mode === "daily") return Core.dailyQuote(e.quotes, now);
        return e.text || "";
      }, "block");
    },
    forecast: function (e, box, r) {
      var wrap = el("div", "fc");
      box.appendChild(wrap);
      var last = "";
      S.updaters.push(function (now, data) {
        var items = Core.forecastItems(e.mode || "hours", e.count || 4, data.forecast);
        var key = JSON.stringify(items);
        if (key === last) return;
        last = key;
        wrap.textContent = "";
        if (!items.length) { wrap.textContent = "—"; wrap.style.fontSize = Core.fitText(1, r.width, r.height, 9999, "line") + "px"; return; }
        var colW = r.width / items.length;
        var longest = 3;
        for (var i = 0; i < items.length; i++) longest = Math.max(longest, items[i].text.length);
        var size = Core.fitText(longest, colW, r.height / 3, 9999, "clock");
        for (var j = 0; j < items.length; j++) {
          var c = el("div", "fcc");
          c.style.width = colW + "px";
          c.style.fontSize = size + "px";
          var a = el("div", "fcl"); a.textContent = items[j].label;
          var b = el("div"); var ic = iconNode(items[j].icon, "1.4em"); if (ic) b.appendChild(ic);
          var t = el("div"); t.textContent = items[j].text;
          c.appendChild(a); c.appendChild(b); c.appendChild(t);
          wrap.appendChild(c);
        }
      });
    },
  };

  // ------------------------------------------------------------------ rotación y deslizar

  function go(dir) {
    if (S.screens.length < 2) return;
    S.index = (S.index + dir + S.screens.length) % S.screens.length;
    contentEl.style.opacity = 0;
    setTimeout(function () { draw(); contentEl.style.opacity = 1; }, 220);
    scheduleRotation();
  }

  function scheduleRotation() {
    clearInterval(S.rotateTimer);
    if (!PREVIEW && S.rotate >= 5 && S.screens.length > 1) {
      S.rotateTimer = setInterval(function () { go(1); }, S.rotate * 1000);
    }
  }

  // ------------------------------------------------------------------ toques (reloj)

  var g = { down: false, moved: false, fired: false, x0: 0, y0: 0, lastTap: 0, longT: null, singleT: null };

  function gestureDown(ev) {
    g.down = true; g.moved = false; g.fired = false; g.x0 = ev.clientX; g.y0 = ev.clientY;
    clearTimeout(g.longT);
    g.longT = setTimeout(function () {
      if (g.down && !g.moved) { g.down = false; g.fired = true; native("longPress"); }
    }, 650);
  }
  function gestureMove(ev) {
    if (!g.down) return;
    if (Math.abs(ev.clientX - g.x0) > 12 || Math.abs(ev.clientY - g.y0) > 12) { g.moved = true; clearTimeout(g.longT); }
  }
  function gestureUp(ev) {
    clearTimeout(g.longT);
    if (g.fired) { g.fired = false; return; }
    if (!g.down) return;
    g.down = false;
    var dx = ev.clientX - g.x0, dy = ev.clientY - g.y0;
    var swipe = Core.classifySwipe(dx, dy);
    if (swipe === "left" || swipe === "right") { native("swipe", swipe); return; }
    if (swipe === "up" || swipe === "down") { go(swipe === "up" ? 1 : -1); return; }
    if (g.moved) return;
    var now = Date.now();
    if (now - g.lastTap < 350) { clearTimeout(g.singleT); g.lastTap = 0; native("doubleTap"); return; }
    g.lastTap = now;
    clearTimeout(g.singleT);
    g.singleT = setTimeout(function () { native("tap"); }, 360);
  }

  // ------------------------------------------------------------------ edición (vista previa)

  var drag = null;      // redimensionando: { id, box, el, w, h }
  var moving = null;    // moviendo: { id, box, el, x0, y0, cw, ch, dx, dy, moved }

  function findElement(id) {
    var list = (S.screens[S.index] || {}).elements || [];
    for (var i = 0; i < list.length; i++) if (list[i].id === id) return list[i];
    return null;
  }
  function cellAt(clientX, clientY) {
    var b = rootEl.getBoundingClientRect();
    var cx = Math.floor((clientX - b.left) / (b.width / COLS));
    var cy = Math.floor((clientY - b.top) / (b.height / S.rows));
    return { x: Math.max(0, Math.min(COLS - 1, cx)), y: Math.max(0, Math.min(S.rows - 1, cy)) };
  }

  // ids de los elementos bajo el punto, de delante hacia atrás según la lista del diseño.
  function idsAt(x, y) {
    var found = [];
    if (document.elementsFromPoint) {
      var stack = document.elementsFromPoint(x, y);
      for (var si = 0; si < stack.length; si++) {
        var cand = stack[si].closest ? stack[si].closest(".el") : null;
        var cid = cand ? cand.getAttribute("data-id") : null;
        if (cid && found.indexOf(cid) < 0) found.push(cid);
      }
    }
    var list = (S.screens[S.index] || {}).elements || [];
    var order = [];
    for (var i = 0; i < list.length; i++) order.push(list[i].id);
    return Core.sortFrontToBack(found, order);
  }

  function editDown(ev) {
    var t = ev.target;
    var handle = t.closest ? t.closest(".handle") : null;
    var box = t.closest ? t.closest(".el") : null;
    if (handle && box) {
      var data = findElement(box.getAttribute("data-id"));
      if (!data) return;
      drag = { id: data.id, box: box, el: data, w: data.w, h: data.h };
      try { handle.setPointerCapture(ev.pointerId); } catch (e) { /* ignorar */ }
      ev.preventDefault();
      return;
    }
    // Elementos solapados: se ordenan por la lista del diseño (el último va delante).
    var ids = idsAt(ev.clientX, ev.clientY);
    var selId = S.opts.selected;
    var pressed = null;
    if (selId) {
      var sb = contentEl.querySelector('.el[data-id="' + selId + '"]');
      if (sb) {
        var sr = sb.getBoundingClientRect();
        if (ev.clientX >= sr.left && ev.clientX <= sr.right && ev.clientY >= sr.top && ev.clientY <= sr.bottom) pressed = selId;
      }
    }
    if (!pressed && ids.length) pressed = ids[0];
    if (!pressed && box) pressed = box.getAttribute("data-id");
    if (pressed) {
      var pbox = contentEl.querySelector('.el[data-id="' + pressed + '"]');
      var item = findElement(pressed);
      if (!item || !pbox) return;
      var b = rootEl.getBoundingClientRect();
      moving = { id: item.id, box: pbox, el: item, x0: ev.clientX, y0: ev.clientY,
        cw: b.width / COLS, ch: b.height / S.rows, dx: 0, dy: 0, moved: false,
        ids: ids, wasSelected: pressed === selId };
      try { rootEl.setPointerCapture(ev.pointerId); } catch (e2) { /* ignorar */ }
      ev.preventDefault();
      return;
    }
    var c = cellAt(ev.clientX, ev.clientY);
    post({ type: "face-empty", x: c.x, y: c.y });
  }
  function editMove(ev) {
    if (moving) {
      var px = ev.clientX - moving.x0, py = ev.clientY - moving.y0;
      if (!moving.moved && Math.abs(px) < 8 && Math.abs(py) < 8) return;
      moving.moved = true;
      var snap = Core.snapMove(moving.el, Math.round(px / moving.cw), Math.round(py / moving.ch), S.rows);
      moving.dx = snap.x; moving.dy = snap.y;
      var r = Core.cellRect({ x: snap.x, y: snap.y, w: moving.el.w, h: moving.el.h }, S.W, S.H, S.rows);
      moving.box.classList.add("moving");
      moving.box.style.left = r.left + "px";
      moving.box.style.top = r.top + "px";
      return;
    }
    if (!drag) return;
    var c = cellAt(ev.clientX, ev.clientY);
    drag.w = Math.max(1, Math.min(COLS - drag.el.x, c.x - drag.el.x + 1));
    drag.h = Math.max(1, Math.min(S.rows - drag.el.y, c.y - drag.el.y + 1));
    var rr = Core.cellRect({ x: drag.el.x, y: drag.el.y, w: drag.w, h: drag.h }, S.W, S.H, S.rows);
    drag.box.style.width = rr.width + "px";
    drag.box.style.height = rr.height + "px";
  }
  function editUp() {
    if (moving) {
      var m = moving;
      moving = null;
      if (!m.moved) {
        // Clic sin mover: si ya estaba seleccionado, pasa al de debajo; si no, selecciona el pulsado.
        var target = m.id;
        if (m.wasSelected && m.ids.length > 1) target = Core.pickUnder(m.ids, S.opts.selected) || m.id;
        post({ type: "face-select", id: target });
        return;
      }
      post({ type: "face-move", id: m.id, x: m.dx, y: m.dy });
      return;
    }
    if (!drag) return;
    var d = drag;
    drag = null;
    post({ type: "face-resize", id: d.id, w: d.w, h: d.h });
  }

  // ------------------------------------------------------------------ API pública

  function setData(data) {
    if (!rootEl || !contentEl) { pending.push(function () { setData(data); }); return; }
    S.data = data || {};
    S.offset = Core.clockOffset(S.data, Date.now());
    updateAll();
  }

  function tick() {
    updateAll();
    var m = new Date().getMinutes();
    if (m !== lastMinute) {
      lastMinute = m;
      if (!PREVIEW) { // antidesgaste: la carátula se desplaza unos píxeles cada minuto
        contentEl.style.transform = "translate(" + (((m % 5) - 2) * 6) + "px," + ((Math.floor(m / 5) % 3 - 1) * 6) + "px)";
      }
    }
  }

  function init() {
    rootEl = document.getElementById("root");
    contentEl = document.getElementById("content");
    rootEl.addEventListener("pointerdown", function (ev) { if (S.opts.edit) editDown(ev); else gestureDown(ev); });
    rootEl.addEventListener("pointermove", function (ev) { if (S.opts.edit) editMove(ev); else gestureMove(ev); });
    rootEl.addEventListener("pointerup", function (ev) { if (S.opts.edit) editUp(); else gestureUp(ev); });
    rootEl.addEventListener("pointercancel", function () { g.down = false; drag = null; moving = null; clearTimeout(g.longT); });
    window.addEventListener("resize", function () {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(function () { if (S.design) draw(); }, 150);
    });
    window.addEventListener("message", function (ev) {
      if (ev.origin !== location.origin) return;
      var m = ev.data;
      if (!m || typeof m !== "object") return;
      if (m.type === "face-render") render(m.design, m.opts || {});
      else if (m.type === "face-data") setData(m.data || {});
    });
    setInterval(tick, 1000);
    var queued = pending;
    pending = [];
    for (var q = 0; q < queued.length; q++) queued[q]();
  }

  window.Face = { render: render, setData: setData };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
