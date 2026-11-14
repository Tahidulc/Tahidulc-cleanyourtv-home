// Whitebrook Road Cinema: the pre-show before a film (MovieMode.java). One canvas, drawn frame by frame from the time into
// each part, so it plays the same on the TV and in a preview. The TV hands it one part at a time:
//   CINEMA.init(cfg)    pictures (data: URLs) and the size to draw at; AndroidCinema.ready() once fonts and pictures are in
//   CINEMA.play(part)   a timed part (welcome, ad, sport, card, between, phones, intro); AndroidCinema.done(kind) at its end
//   CINEMA.trailer(t)   see-through while the TV plays a trailer under it: a corner mark and, at first, its title
//   CINEMA.hint()       "Press Back again to skip to the film"
//   CINEMA.image(k, u)  a picture handed over later (crests, a trailer's picture) under its key
// Lights: AndroidCinema.lights('off') as the intro starts (the room goes dark for it). Sounds are its own (sfx/), made here.
(function () {
  'use strict';
  const W = 1920, H = 1080, PI = Math.PI, TAU = 2 * PI;
  const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
  const ramp = (a, b, t) => clamp((t - a) / (b - a));
  const lerp = (a, b, k) => a + (b - a) * k;
  const eOut3 = (k) => 1 - Math.pow(1 - k, 3);
  const eIn3 = (k) => k * k * k;
  const eInOut3 = (k) => (k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2);
  const eOutBack = (k, s = 1.7) => 1 + (s + 1) * Math.pow(k - 1, 3) + s * Math.pow(k - 1, 2);
  const inOut = (t, a, b, f = 0.5) => ramp(a, a + f, t) * (1 - ramp(b - f, b, t));   // in at a, out by b
  const VIOLET = '#6D28F2', CORAL = '#FF6B6B', GOLD = '#F4C152', GOLD_D = '#A97A22', CREAM = '#F6F0E4';
  const NATIVE = window.AndroidCinema || { ready() {}, done() {}, lights() {}, log() {} };

  // ---------------------------------------------------------------- the canvas
  const cv = document.getElementById('c');
  const g = cv.getContext('2d');
  let RES = 1;
  function size() {
    cv.width = Math.round(W * RES); cv.height = Math.round(H * RES);
  }
  const IMG = {};
  const img = (url) => new Promise((ok) => { if (!url) return ok(null); const i = new Image(); i.onload = () => ok(i); i.onerror = () => ok(null); i.src = url; });

  // a steady random (the same dust, stars and grain each time)
  function rnd(seed) { let s = seed >>> 0; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); }

  // ---------------------------------------------------------------- drawing
  function font(w, px, fam = 'Mont') { g.font = `${w} ${px}px ${fam}`; }
  function spaced(px) { g.letterSpacing = px + 'px'; }
  function txt(s, x, y, o = {}) {
    g.save();
    font(o.w || 700, o.size || 40, o.fam || 'Mont');
    spaced(o.ls || 0);
    g.textAlign = o.align || 'center'; g.textBaseline = o.base || 'alphabetic';
    g.globalAlpha *= o.a == null ? 1 : o.a;
    if (o.shadow) { g.shadowColor = o.shadow; g.shadowBlur = o.blur || 18; g.shadowOffsetY = o.dy || 4; }
    if (o.stroke) { g.lineWidth = o.stroke; g.strokeStyle = o.strokeStyle || '#000'; g.lineJoin = 'round'; g.strokeText(s, x, y); }
    g.fillStyle = o.color || '#fff';
    g.fillText(s, x, y);
    g.restore();
  }
  function width(s, w, px, fam = 'Mont', ls = 0) { g.save(); font(w, px, fam); spaced(ls); const v = g.measureText(s).width; g.restore(); return v; }

  // the family's mark: the house with the play button, violet to coral
  const HOUSE = new Path2D('M31 75 L31 50 L54 31 L77 50 L77 75 Z');
  const PLAY = new Path2D('M49 53 L49 69 L63 61 Z');
  function tile(x, y, s, shine) {
    g.save();
    const gr = g.createLinearGradient(x, y, x + s, y + s); gr.addColorStop(0, VIOLET); gr.addColorStop(1, CORAL);
    g.beginPath(); g.roundRect(x, y, s, s, s * 0.24);
    g.shadowColor = 'rgba(0,0,0,0.45)'; g.shadowBlur = s * 0.3; g.shadowOffsetY = s * 0.06;
    g.fillStyle = gr; g.fill();
    g.shadowColor = 'transparent';
    if (shine > 0 && shine < 1) {
      g.save(); g.clip();
      const sx = x - s + shine * 3 * s;
      const sg = g.createLinearGradient(sx, y, sx + s * 0.6, y + s * 0.4);
      sg.addColorStop(0, 'rgba(255,255,255,0)'); sg.addColorStop(0.5, 'rgba(255,255,255,0.6)'); sg.addColorStop(1, 'rgba(255,255,255,0)');
      g.fillStyle = sg; g.fillRect(x, y, s, s);
      g.restore();
    }
    const wd = s * 0.62, k = wd / 51, mx = x + (s - wd) / 2, my = y + (s - 49 * k) / 2;
    g.translate(mx - 28.5 * k, my - 28.5 * k); g.scale(k, k);
    g.lineJoin = 'round';
    g.fillStyle = '#fff'; g.strokeStyle = '#fff'; g.lineWidth = 5; g.fill(HOUSE); g.stroke(HOUSE);
    const gx0 = (x - mx) / k + 28.5, gy0 = (y - my) / k + 28.5;
    const cg = g.createLinearGradient(gx0, gy0, gx0 + s / k, gy0 + s / k); cg.addColorStop(0, VIOLET); cg.addColorStop(1, CORAL);
    g.fillStyle = cg; g.strokeStyle = cg; g.lineWidth = 2.6; g.fill(PLAY); g.stroke(PLAY);
    g.restore();
  }
  // whitebrook road cinema: the mark, the name, and "cinema" on gold; k 0..1 builds it (mark, name, the gold pill)
  function logo(cx, cy, s, k = 1, a = 1, shine = 0) {
    if (a <= 0.01) return;
    const name = 'whitebrook road', px = s * 0.56;
    const nw = width(name, 800, px), pw = width('cinema', 800, px * 0.82) + s * 0.5;
    const total = s + s * 0.32 + nw + s * 0.26 + pw;
    let x = cx - total / 2;
    g.save(); g.globalAlpha *= a;
    const kt = eOutBack(ramp(0, 0.35, k), 2.2);
    if (kt > 0) { g.save(); g.translate(x + s / 2, cy); g.scale(kt, kt); tile(-s / 2, -s / 2, s, shine); g.restore(); }
    x += s + s * 0.32;
    // the name typed in, a letter at a time
    const kn = ramp(0.25, 0.75, k), n = Math.round(name.length * kn);
    if (n > 0) txt(name.slice(0, n), x, cy + px * 0.36, { w: 800, size: px, align: 'left', color: '#fff', shadow: 'rgba(0,0,0,0.5)', blur: 12 });
    x += nw + s * 0.26;
    const kp = eOutBack(ramp(0.72, 1, k), 2.6);
    if (kp > 0) {
      g.save();
      const ph = s * 0.82, py = cy - ph / 2;
      g.translate(x + pw / 2, cy); g.scale(kp, kp); g.translate(-(x + pw / 2), -cy);
      const gg = g.createLinearGradient(0, py, 0, py + ph); gg.addColorStop(0, '#FFE08A'); gg.addColorStop(0.5, GOLD); gg.addColorStop(1, GOLD_D);
      g.beginPath(); g.roundRect(x, py, pw, ph, ph * 0.22);
      g.shadowColor = 'rgba(244,193,82,0.55)'; g.shadowBlur = s * 0.5;
      g.fillStyle = gg; g.fill(); g.shadowColor = 'transparent';
      txt('cinema', x + pw / 2, cy + px * 0.82 * 0.36, { w: 800, size: px * 0.82, color: '#2a1a00' });
      g.restore();
    }
    g.restore();
  }

  // red velvet curtains, open 0 (closed) to 1 (drawn back to the sides)
  function curtains(open, a = 1) {
    if (a <= 0.01) return;
    g.save(); g.globalAlpha *= a;
    for (const side of [-1, 1]) {
      const wd = lerp(W / 2 + 40, 150, eInOut3(open));
      const x0 = side < 0 ? 0 : W - wd;
      const gr = g.createLinearGradient(x0, 0, x0 + wd, 0);
      const folds = 9;
      for (let i = 0; i <= folds; i++) {
        const f = i / folds;
        gr.addColorStop(f, i % 2 ? '#5c0711' : '#a3121f');
      }
      g.fillStyle = gr;
      g.beginPath();
      if (side < 0) { g.moveTo(0, 0); g.lineTo(wd, 0); g.quadraticCurveTo(wd - 40 * open, H * 0.55, wd + 30 * open, H); g.lineTo(0, H); }
      else { g.moveTo(W, 0); g.lineTo(W - wd, 0); g.quadraticCurveTo(W - wd + 40 * open, H * 0.55, W - wd - 30 * open, H); g.lineTo(W, H); }
      g.closePath(); g.fill();
      // the light on the folds from above
      const sh = g.createLinearGradient(0, 0, 0, H); sh.addColorStop(0, 'rgba(255,190,170,0.18)'); sh.addColorStop(0.5, 'rgba(0,0,0,0)'); sh.addColorStop(1, 'rgba(0,0,0,0.45)');
      g.fillStyle = sh; g.fill();
    }
    // the pelmet along the top
    const pg = g.createLinearGradient(0, 0, 0, 90); pg.addColorStop(0, '#3a0309'); pg.addColorStop(1, '#7c0d18');
    g.fillStyle = pg; g.fillRect(0, 0, W, 70);
    g.fillStyle = GOLD_D; g.fillRect(0, 70, W, 4);
    g.restore();
  }
  // the projector's beam from the back of the room, with dust drifting in it
  const DUST = (() => { const r = rnd(7); return Array.from({ length: 140 }, () => [r(), r(), r(), r()]); })();
  function beam(a, t) {
    if (a <= 0.01) return;
    g.save(); g.globalAlpha *= a;
    const ox = W / 2, oy = -60;
    const bg = g.createLinearGradient(0, oy, 0, H);
    bg.addColorStop(0, 'rgba(255,248,225,0.55)'); bg.addColorStop(0.5, 'rgba(255,240,210,0.10)'); bg.addColorStop(1, 'rgba(255,240,210,0)');
    g.fillStyle = bg;
    g.beginPath(); g.moveTo(ox - 30, oy); g.lineTo(ox + 30, oy); g.lineTo(ox + 760, H); g.lineTo(ox - 760, H); g.closePath();
    g.globalCompositeOperation = 'lighter'; g.fill();
    for (const [u, v, s, p] of DUST) {
      const y = ((v * H + t * (12 + s * 30)) % H);
      const spread = 30 + (y - oy) / (H - oy) * 730;
      const x = ox + (u * 2 - 1) * spread * 0.9 + Math.sin(t * (0.4 + p) + p * 9) * 12;
      const al = 0.25 + 0.5 * Math.abs(Math.sin(t * (0.7 + s) + p * 20));
      g.fillStyle = `rgba(255,245,220,${(al * (1 - y / H)).toFixed(3)})`;
      g.beginPath(); g.arc(x, y, 1 + s * 2.2, 0, TAU); g.fill();
    }
    g.restore();
  }
  // film grain and a flicker, as a projector's picture has
  const GRAIN = [];
  function grainBake() {
    for (let i = 0; i < 4; i++) {
      const c = document.createElement('canvas'); c.width = 320; c.height = 180;
      const x = c.getContext('2d'), d = x.createImageData(320, 180), r = rnd(91 + i * 13);
      for (let p = 0; p < d.data.length; p += 4) { const v = r() * 255; d.data[p] = d.data[p + 1] = d.data[p + 2] = v; d.data[p + 3] = 26; }
      x.putImageData(d, 0, 0); GRAIN.push(c);
    }
  }
  function grain(a, t) {
    if (a <= 0.01 || !GRAIN.length) return;
    g.save(); g.globalAlpha = a; g.globalCompositeOperation = 'overlay';
    g.drawImage(GRAIN[Math.floor(t * 24) % GRAIN.length], 0, 0, W, H);
    g.restore();
  }
  function vignette(a = 0.7) {
    const v = g.createRadialGradient(W / 2, H / 2, H * 0.35, W / 2, H / 2, H * 0.95);
    v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, `rgba(0,0,0,${a})`);
    g.fillStyle = v; g.fillRect(0, 0, W, H);
  }
  function black(a = 1) { g.save(); g.globalAlpha = a; g.fillStyle = '#000'; g.fillRect(0, 0, W, H); g.restore(); }
  // a marquee board: a frame of bulbs that chase round it
  function marquee(x, y, w, h, t, a) {
    if (a <= 0.01) return;
    g.save(); g.globalAlpha *= a;
    const fg = g.createLinearGradient(0, y, 0, y + h); fg.addColorStop(0, '#1b1206'); fg.addColorStop(1, '#0c0803');
    g.beginPath(); g.roundRect(x, y, w, h, 26); g.fillStyle = fg; g.fill();
    g.lineWidth = 6; g.strokeStyle = GOLD_D; g.stroke();
    const step = 44, per = 2 * (w + h), n = Math.floor(per / step);
    for (let i = 0; i < n; i++) {
      let d = i * step, bx, by;
      if (d < w) { bx = x + d; by = y; } else if ((d -= w) < h) { bx = x + w; by = y + d; } else if ((d -= h) < w) { bx = x + w - d; by = y + h; } else { d -= w; bx = x; by = y + h - d; }
      const on = (i + Math.floor(t * 8)) % 3 === 0;
      const rg = g.createRadialGradient(bx, by, 0, bx, by, on ? 22 : 12);
      rg.addColorStop(0, on ? 'rgba(255,240,190,1)' : 'rgba(255,220,150,0.55)'); rg.addColorStop(0.4, on ? 'rgba(255,200,90,0.8)' : 'rgba(150,100,30,0.4)'); rg.addColorStop(1, 'rgba(255,190,80,0)');
      g.fillStyle = rg; g.beginPath(); g.arc(bx, by, on ? 22 : 12, 0, TAU); g.fill();
    }
    g.restore();
  }
  // a line of words that fits a width
  function fit(s, w, px, maxW, fam = 'Mont', ls = 0) { let p = px; while (p > 12 && width(s, w, p, fam, ls) > maxW) p -= 2; return p; }
  function emoji(ch, x, y, px, a = 1, rot = 0, sc = 1) {
    if (a <= 0.01 || sc <= 0.01) return;
    g.save(); g.globalAlpha *= a; g.translate(x, y); g.rotate(rot); g.scale(sc, sc);
    g.font = `${px}px 'Noto Color Emoji', 'Apple Color Emoji', 'Segoe UI Emoji', sans-serif`;
    g.textAlign = 'center'; g.textBaseline = 'middle';
    g.shadowColor = 'rgba(0,0,0,0.35)'; g.shadowBlur = px * 0.12; g.shadowOffsetY = px * 0.05;
    g.fillText(ch, 0, 0);
    g.restore();
  }
  function cover(im, a = 1, zoom = 1, dark = 0.55, blur = 0) {
    if (!im || a <= 0.01) return;
    g.save(); g.globalAlpha *= a;
    const k = Math.max(W / im.width, H / im.height) * zoom, w = im.width * k, h = im.height * k;
    if (blur > 0) g.filter = `blur(${blur}px)`;
    g.drawImage(im, (W - w) / 2, (H - h) / 2, w, h);
    g.filter = 'none';
    g.fillStyle = `rgba(0,0,0,${dark})`; g.fillRect(0, 0, W, H);
    g.restore();
  }

  // ---------------------------------------------------------------- the parts, each a picture for t seconds in
  const PARTS = {
    // the curtains part, the beam comes on, the house mark and the name build, then tonight's film on a marquee
    welcome: {
      len: 16,
      cues: [[0, 'cin-whoosh', 0.8], [0.2, 'cin-projector', 0.35], [4.5, 'cin-ding', 0.7], [6.0, 'cin-pop', 0.6]],
      draw(p, t) {
        black();
        beam(ramp(1.2, 3, t) * (1 - ramp(15, 16, t)), t);
        const lk = ramp(2.4, 5, t), la = 1 - ramp(15, 16, t);
        const ly = lerp(H / 2 - 20, 300, eInOut3(ramp(5.6, 6.6, t)));
        logo(W / 2, ly, lerp(150, 116, eInOut3(ramp(5.6, 6.6, t))), lk, la, ramp(4.6, 5.6, t));
        // tonight on screen 1
        const mk = eOutBack(ramp(6.0, 6.8, t), 1.4), ma = ramp(6.0, 6.5, t) * la;
        if (mk > 0) {
          g.save(); g.translate(W / 2, 640); g.scale(1, mk); g.translate(-W / 2, -640);
          marquee(W / 2 - 640, 470, 1280, 340, t, ma);
          txt('TONIGHT ON SCREEN 1', W / 2, 545, { w: 700, size: 30, ls: 10, color: GOLD, a: ma });
          const title = (p.film && p.film.title || 'THE FEATURE PRESENTATION').toUpperCase();
          txt(title, W / 2, 660, { w: 800, size: fit(title, 800, 96, 1120), color: CREAM, a: ma, shadow: 'rgba(255,200,90,0.45)', blur: 24, dy: 0 });
          txt('Seats: the sofa  ·  Snacks: compulsory  ·  Dress code: pyjamas', W / 2, 745, { w: 600, size: 30, color: '#cdbb94', a: ma });
          g.restore();
        }
        txt('Sit back. Get comfy. Pass the popcorn.', W / 2, 920, { w: 600, size: 38, color: '#fff', a: inOut(t, 10.5, 15.5, 0.6) });
        curtains(ramp(0.2, 2.6, t), 1 - ramp(14.5, 16, t) * 0);
        grain(0.5, t);
        vignette(0.75);
      },
    },
    // a cheeky advert for something nobody can buy
    ad: {
      len: 14,
      cues: [[0, 'cin-whoosh', 0.7], [1.6, 'cin-jingle', 0.75], [7.0, 'cin-ding', 0.8], [13.2, 'cin-whoosh', 0.5]],
      draw(p, t) {
        const c1 = p.bg1 || '#ff5a5f', c2 = p.bg2 || '#ffb347';
        const wipe = eOut3(ramp(0, 0.6, t)), out = eIn3(ramp(13.2, 14, t));
        black();
        g.save();
        g.beginPath(); g.rect(0, 0, W * wipe, H); g.clip();
        const bg = g.createLinearGradient(0, 0, W, H); bg.addColorStop(0, c1); bg.addColorStop(1, c2);
        g.fillStyle = bg; g.fillRect(0, 0, W, H);
        // sunburst stripes turning slowly behind the product
        g.save(); g.translate(560, 560); g.rotate(t * 0.08);
        for (let i = 0; i < 24; i++) { g.rotate(TAU / 24); if (i % 2) continue; g.fillStyle = 'rgba(255,255,255,0.10)'; g.beginPath(); g.moveTo(0, 0); g.lineTo(1600, -210); g.lineTo(1600, 210); g.closePath(); g.fill(); }
        g.restore();
        txt('AN ADVERT FOR SOMETHING YOU CAN\'T BUY', 90, 110, { w: 700, size: 24, ls: 6, align: 'left', color: 'rgba(255,255,255,0.85)' });
        // the product, bouncing in
        const pk = eOutBack(ramp(0.5, 1.4, t), 2.0), wob = Math.sin(t * 2.2) * 0.06;
        emoji(p.emoji || '🍿', 560, 560 + Math.sin(t * 1.6) * 14, 420, 1, wob, pk);
        // its name slammed in, then the words
        const nk = eOutBack(ramp(1.6, 2.2, t), 3);
        if (nk > 0) {
          g.save(); g.translate(1340, 360); g.scale(nk, nk); g.rotate(-0.03);
          const name = (p.product || '').toUpperCase();
          txt(name, 0, 0, { w: 800, size: fit(name, 800, 118, 900, 'BarlowC'), fam: 'BarlowC', color: '#fff', stroke: 10, strokeStyle: 'rgba(40,10,40,0.85)', shadow: 'rgba(0,0,0,0.4)', blur: 0, dy: 8 });
          g.restore();
        }
        const l1 = p.line1 || '', l2 = p.line2 || '';
        txt(l1, 1340, 520, { w: 700, size: fit(l1, 700, 52, 880), color: '#fff', a: ramp(3.0, 3.6, t), shadow: 'rgba(0,0,0,0.35)', blur: 8, dy: 3 });
        txt(l2, 1340, 590, { w: 700, size: fit(l2, 700, 52, 880), color: '#fff', a: ramp(4.2, 4.8, t), shadow: 'rgba(0,0,0,0.35)', blur: 8, dy: 3 });
        // the price, a starburst spun in
        const sk = eOutBack(ramp(6.8, 7.5, t), 2.4);
        if (sk > 0 && p.price) {
          g.save(); g.translate(1360, 800); g.rotate(-0.18 + (1 - sk) * 2); g.scale(sk, sk);
          g.beginPath();
          for (let i = 0; i < 32; i++) { const r = i % 2 ? 170 : 205, a = (i / 32) * TAU; g.lineTo(Math.cos(a) * r * 1.35, Math.sin(a) * r * 0.78); }
          g.closePath(); g.fillStyle = '#ffe14d'; g.shadowColor = 'rgba(0,0,0,0.35)'; g.shadowBlur = 20; g.shadowOffsetY = 8; g.fill();
          g.shadowColor = 'transparent'; g.lineWidth = 6; g.strokeStyle = '#e03a1e'; g.stroke();
          txt(p.price, 0, 22, { w: 800, size: fit(p.price, 800, 66, 380, 'BarlowC'), fam: 'BarlowC', color: '#d42a12' });
          g.restore();
        }
        txt(p.small || '', W / 2, 1035, { w: 500, size: 24, color: 'rgba(255,255,255,0.9)', a: ramp(9, 9.6, t) });
        g.restore();
        black(out);
        grain(0.25, t);
      },
    },
    // what the family's teams play next, as a cinema would sell a big night
    sport: {
      len: 18,
      cues: [[0, 'cin-whoosh', 0.7], [1.0, 'cin-boom', 0.5], [3.2, 'cin-pop', 0.6], [4.4, 'cin-pop', 0.6], [5.6, 'cin-pop', 0.6], [17.2, 'cin-whoosh', 0.5]],
      draw(p, t) {
        const out = eIn3(ramp(17.2, 18, t));
        const bg = g.createLinearGradient(0, 0, 0, H); bg.addColorStop(0, '#040a1c'); bg.addColorStop(0.62, '#0a1c3d'); bg.addColorStop(1, '#0d3b1e');
        g.fillStyle = bg; g.fillRect(0, 0, W, H);
        // floodlights sweeping
        for (let i = 0; i < 4; i++) {
          const ox = [180, 640, 1280, 1740][i], ang = Math.sin(t * 0.5 + i * 1.7) * 0.25 + (i < 2 ? 0.35 : -0.35);
          g.save(); g.translate(ox, -40); g.rotate(ang);
          const lg = g.createLinearGradient(0, 0, 0, H * 1.2); lg.addColorStop(0, 'rgba(210,230,255,0.38)'); lg.addColorStop(1, 'rgba(210,230,255,0)');
          g.fillStyle = lg; g.globalCompositeOperation = 'lighter';
          g.beginPath(); g.moveTo(-12, 0); g.lineTo(12, 0); g.lineTo(230, H * 1.2); g.lineTo(-230, H * 1.2); g.closePath(); g.fill();
          g.restore();
        }
        // the pitch at the foot, in perspective
        g.save();
        g.beginPath(); g.moveTo(0, H); g.lineTo(W, H); g.lineTo(W - 330, 830); g.lineTo(330, 830); g.closePath(); g.clip();
        for (let i = 0; i < 12; i++) { g.fillStyle = i % 2 ? '#1f7a36' : '#25883d'; g.fillRect(i * W / 12, 830, W / 12, 260); }
        g.restore();
        const hk = eOutBack(ramp(0.6, 1.3, t), 2);
        if (hk > 0) {
          g.save(); g.translate(W / 2, 200); g.scale(hk, hk);
          txt('LIVE ON YOUR SOFA', 0, 0, { w: 700, size: 120, fam: 'KhandC', ls: 4, color: '#fff', shadow: 'rgba(80,150,255,0.6)', blur: 30, dy: 0 });
          g.restore();
        }
        txt(p.sub || 'COMING UP THIS WEEK', W / 2, 270, { w: 700, size: 30, ls: 12, color: GOLD, a: ramp(1.6, 2.2, t) });
        const fx = (p.fixtures || []).slice(0, 3);
        fx.forEach((f, i) => {
          const k = eOut3(ramp(3.0 + i * 1.2, 3.7 + i * 1.2, t)), y = 390 + i * 150;
          if (k <= 0) return;
          g.save(); g.globalAlpha *= k; g.translate((1 - k) * (i % 2 ? 300 : -300), 0);
          g.beginPath(); g.roundRect(W / 2 - 720, y - 58, 1440, 116, 22);
          g.fillStyle = 'rgba(8,16,36,0.78)'; g.fill(); g.lineWidth = 2; g.strokeStyle = 'rgba(140,180,255,0.55)'; g.stroke();
          // each crest on a light plate (a dark crest would be lost on the night sky)
          for (const [im, cx] of [[IMG[f.homeCrest], W / 2 - 646], [IMG[f.awayCrest], W / 2 - 426]]) {
            const pg = g.createRadialGradient(cx - 14, y - 16, 4, cx, y, 54); pg.addColorStop(0, '#ffffff'); pg.addColorStop(1, '#d9e1f0');
            g.beginPath(); g.arc(cx, y, 50, 0, TAU); g.fillStyle = pg; g.fill();
            g.lineWidth = 3; g.strokeStyle = 'rgba(140,180,255,0.8)'; g.stroke();
            if (!im) continue;
            const k = 70 / Math.max(im.width, im.height);
            g.drawImage(im, cx - im.width * k / 2, y - im.height * k / 2, im.width * k, im.height * k);
          }
          txt('v', W / 2 - 536, y + 14, { w: 700, size: 40, fam: 'KhandC', color: '#9fb6e8' });
          const names = (f.home + ' v ' + f.away).toUpperCase();
          txt(names, W / 2 - 340, y + 2, { w: 700, size: fit(names, 700, 56, 700, 'KhandC'), fam: 'KhandC', align: 'left', color: '#fff' });
          txt((f.comp || '').toUpperCase(), W / 2 - 340, y + 40, { w: 600, size: 22, ls: 4, align: 'left', color: '#9fb6e8' });
          txt(f.when || '', W / 2 + 690, y - 4, { w: 700, size: 40, fam: 'KhandC', align: 'right', color: GOLD });
          txt(f.channel || '', W / 2 + 690, y + 38, { w: 600, size: 24, align: 'right', color: '#cfd8ee' });
          g.restore();
        });
        txt(p.tagline || 'Same seat. Same snacks. Same shouting at the telly.', W / 2, 1010, { w: 600, size: 36, color: '#fff', a: inOut(t, 8.5, 17.4, 0.6) });
        vignette(0.6);
        black(out);
      },
    },
    // before a trailer: an old film leader counting down, then the title over its picture
    card: {
      len: 7,
      cues: [[0.05, 'cin-beep', 0.5], [0.65, 'cin-beep', 0.5], [1.25, 'cin-beep', 0.5], [1.85, 'cin-beep', 0.5], [2.6, 'cin-whoosh', 0.6]],
      draw(p, t) {
        black();
        if (t < 2.6) {
          // the leader: grey disc, the sweep, the number
          const n = 4 - Math.floor(t / 0.6), f = (t % 0.6) / 0.6;
          g.save();
          g.fillStyle = '#8c8c8c'; g.fillRect(0, 0, W, H);
          g.fillStyle = '#6e6e6e'; g.beginPath(); g.moveTo(W / 2, H / 2); g.arc(W / 2, H / 2, 900, -PI / 2, -PI / 2 + f * TAU); g.closePath(); g.fill();
          g.strokeStyle = '#1b1b1b'; g.lineWidth = 6;
          g.beginPath(); g.moveTo(0, H / 2); g.lineTo(W, H / 2); g.moveTo(W / 2, 0); g.lineTo(W / 2, H); g.stroke();
          for (const r of [330, 400]) { g.beginPath(); g.arc(W / 2, H / 2, r, 0, TAU); g.lineWidth = r === 330 ? 10 : 6; g.strokeStyle = r === 330 ? '#f4f4f4' : '#1b1b1b'; g.stroke(); }
          txt(String(Math.max(1, n)), W / 2, H / 2 + 150, { w: 800, size: 420, fam: 'BarlowC', color: '#151515' });
          g.restore();
          grain(0.9, t);
          vignette(0.85);
          return;
        }
        const k = ramp(2.6, 3.4, t), out = ramp(6.4, 7, t);
        cover(IMG[p.backdrop], 1 - out, lerp(1.08, 1.0, eOut3(ramp(2.6, 7, t))), 0.5);
        const lab = (p.label || 'COMING SOON TO YOUR SOFA').toUpperCase();
        txt(lab, W / 2, 400, { w: 700, size: 32, ls: 12, color: GOLD, a: k * (1 - out) });
        const title = (p.title || '').toUpperCase();
        txt(title, W / 2, 540, { w: 800, size: fit(title, 800, 120, 1600), color: '#fff', a: eOut3(ramp(2.9, 3.6, t)) * (1 - out), shadow: 'rgba(0,0,0,0.7)', blur: 30, dy: 0 });
        txt(p.sub || '', W / 2, 620, { w: 600, size: 34, color: '#e8e2d6', a: ramp(3.4, 4, t) * (1 - out) });
        txt('TRAILER ' + (p.n || 1) + ' OF ' + (p.of || 3), W / 2, 980, { w: 600, size: 24, ls: 8, color: 'rgba(255,255,255,0.7)', a: ramp(3.6, 4.2, t) * (1 - out) });
        grain(0.35, t);
        vignette(0.7);
      },
    },
    // a cheeky line between the trailers
    between: {
      len: 8,
      cues: [[0.1, 'cin-pop', 0.6], [7.4, 'cin-whoosh', 0.4]],
      draw(p, t) {
        black();
        beam(0.55, t + 20);
        const k = eOutBack(ramp(0.1, 0.8, t), 2.2), a = 1 - ramp(7.3, 8, t);
        emoji(p.emoji || '🍿', W / 2, 400 + Math.sin(t * 3) * 10, 220, a, Math.sin(t * 2) * 0.08, k);
        const l1 = p.line1 || '', l2 = p.line2 || '';
        txt(l1, W / 2, 660, { w: 700, size: fit(l1, 700, 64, 1700), color: '#fff', a: ramp(0.8, 1.4, t) * a });
        txt(l2, W / 2, 750, { w: 700, size: fit(l2, 700, 64, 1700), color: GOLD, a: ramp(2.4, 3.0, t) * a });
        logo(W / 2, 960, 52, 1, 0.75 * ramp(1, 1.6, t) * a);
        grain(0.4, t);
        vignette(0.75);
      },
    },
    // phones away, from the management
    phones: {
      len: 14,
      cues: [[0.1, 'cin-ding', 0.6], [5.0, 'cin-pop', 0.5], [8.0, 'cin-pop', 0.5], [13.3, 'cin-whoosh', 0.4]],
      draw(p, t) {
        black();
        beam(0.5, t + 40);
        const a = 1 - ramp(13.3, 14, t);
        txt('A WORD FROM THE MANAGEMENT', W / 2, 150, { w: 700, size: 34, ls: 12, color: GOLD, a: ramp(0.2, 0.8, t) * a });
        // the phone, dimming and nodding off
        const sleep = ramp(2.2, 3.4, t);
        g.save(); g.globalAlpha *= a;
        g.translate(W / 2, 470); g.rotate(-0.08 + Math.sin(t * 1.4) * 0.03);
        g.beginPath(); g.roundRect(-110, -190, 220, 380, 36);
        g.fillStyle = '#151821'; g.fill(); g.lineWidth = 8; g.strokeStyle = '#3a4152'; g.stroke();
        g.beginPath(); g.roundRect(-92, -160, 184, 320, 18);
        const sg = g.createLinearGradient(0, -160, 0, 160); sg.addColorStop(0, `rgba(120,170,255,${(1 - sleep).toFixed(3)})`); sg.addColorStop(1, `rgba(60,90,200,${(1 - sleep).toFixed(3)})`);
        g.fillStyle = '#06070b'; g.fill(); g.fillStyle = sg; g.fill();
        g.restore();
        for (let i = 0; i < 3; i++) {
          const zt = (t - 3 - i * 0.6) % 2.4;
          if (t < 3 + i * 0.6 || zt < 0) continue;
          const zk = zt / 2.4;
          txt('z', W / 2 + 150 + zk * 140 + i * 12, 360 - zk * 120 - i * 6, { w: 800, size: 40 + i * 18, color: '#cfd9ff', a: (1 - zk) * a });
        }
        txt('Phones on silent. Yes, you.', W / 2, 760, { w: 700, size: 60, color: '#fff', a: ramp(1.0, 1.6, t) * a });
        txt('Whispering is allowed, but only to say “I knew it”.', W / 2, 850, { w: 600, size: 40, color: '#e8e2d6', a: ramp(4.8, 5.4, t) * a });
        txt('The pause button is for emergencies. Snacks count.', W / 2, 920, { w: 600, size: 40, color: '#e8e2d6', a: ramp(7.8, 8.4, t) * a });
        grain(0.4, t);
        vignette(0.75);
      },
    },
    // the lights go out; the words, spaced wide, one line at a time, as the big screen's own intro; then the name
    intro: {
      len: 52,
      cues: [[0.2, 'cin-drone', 0.9], [33.0, 'cin-boom', 1.0], [33.3, 'cin-shimmer', 0.7]],
      draw(p, t) {
        black();
        const lines = p.lines || [
          [3.0, 9.5, 'FEEL THE INTENSITY OF SITTING', 0], [5.0, 9.5, 'ON ONE SOFA, IN TOTAL SILENCE, TOGETHER', 1],
          [11.0, 15.8, '(CRUNCHING IS ALLOWED)', 0],
          [17.0, 21.8, 'NO PHONES. NO SPOILERS.', 0],
          [23.0, 28.6, 'NO PAUSING TO ASK WHO THAT ACTOR IS', 0],
        ];
        g.save();
        // a gentle slant and drift, as words on a big screen seen from the seats
        g.translate(W / 2, H / 2); g.rotate(-0.045); g.translate(-W / 2, -H / 2);
        for (const [a0, a1, s, row] of lines) {
          const k = inOut(t, a0, a1, 1.1);
          if (k <= 0.005) continue;
          const drift = (t - a0) * 6;
          g.save();
          if (k < 1) g.filter = `blur(${((1 - k) * 6).toFixed(1)}px)`;
          txt(s, W / 2 + drift, H / 2 - 18 + row * 72, { w: 600, size: 44, ls: 18, color: '#f2f2f2', a: k, shadow: 'rgba(255,255,255,0.35)', blur: 16, dy: 0 });
          g.restore();
        }
        g.restore();
        // the name, out of the dark with the boom, a light sweeping over it
        const lk = ramp(33, 35.2, t), la = ramp(33, 33.6, t) * (1 - ramp(44, 45.5, t));
        if (la > 0) {
          const fl = Math.exp(-Math.max(0, t - 33) * 2.2);
          const rg = g.createRadialGradient(W / 2, H / 2, 0, W / 2, H / 2, 900);
          rg.addColorStop(0, `rgba(255,235,190,${(0.35 * fl).toFixed(3)})`); rg.addColorStop(1, 'rgba(255,235,190,0)');
          g.fillStyle = rg; g.fillRect(0, 0, W, H);
          logo(W / 2, H / 2 - 40, 150, lk, la, ramp(36, 37.4, t));
          txt('ENJOY THE FILM', W / 2, H / 2 + 140, { w: 600, size: 34, ls: 16, color: '#d9d4c8', a: ramp(38, 39.2, t) * la });
        }
        const title = (p.film && p.film.title || '').toUpperCase();
        if (title) txt(title, W / 2, H / 2 + 12, { w: 500, size: fit(title, 500, 64, 1600, 'Mont', 20), ls: 20, color: '#fff', a: inOut(t, 46, 51.2, 1.2) });
        grain(0.18, t);
      },
    },
  };

  // ---------------------------------------------------------------- the trailer's marks, over the TV's own player
  let TR = null, trAt = 0;
  function trailerFrame(t) {
    g.clearRect(0, 0, W, H);
    if (!TR) return;
    const k = ramp(0.2, 0.8, t);
    // the corner mark, all the time
    g.save(); g.globalAlpha = 0.85 * k;
    tile(60, 52, 54, 0);
    txt('whitebrook road cinema', 130, 90, { w: 800, size: 26, align: 'left', color: '#fff', shadow: 'rgba(0,0,0,0.6)', blur: 10, dy: 2 });
    g.restore();
    txt('TRAILER ' + (TR.n || 1) + ' OF ' + (TR.of || 3), W - 64, 90, { w: 700, size: 22, ls: 6, align: 'right', color: '#fff', a: 0.75 * k, shadow: 'rgba(0,0,0,0.6)', blur: 10, dy: 2 });
    // its title, at first, low on the left
    const lt = inOut(t, 1.0, 8.0, 0.6);
    if (lt > 0) {
      g.save(); g.globalAlpha = lt;
      const lg = g.createLinearGradient(0, 860, 0, 1080); lg.addColorStop(0, 'rgba(0,0,0,0)'); lg.addColorStop(1, 'rgba(0,0,0,0.7)');
      g.fillStyle = lg; g.fillRect(0, 860, W, 220);
      txt((TR.label || '').toUpperCase(), 80, 950, { w: 700, size: 24, ls: 8, align: 'left', color: GOLD });
      txt(TR.title || '', 80, 1010, { w: 800, size: 54, align: 'left', color: '#fff' });
      g.restore();
    }
  }

  // ---------------------------------------------------------------- playing
  let cur = null, curAt = 0, raf = 0, hintAt = 0, hintOn = false;
  const AUD = {};
  function sound(name, vol) {
    if (window.CINEMA_SILENT) return;
    try {
      let a = AUD[name];
      if (!a) { a = AUD[name] = new Audio('sfx/' + name + '.ogg'); a.preload = 'auto'; }
      const b = a.paused ? a : a.cloneNode();
      b.volume = clamp(vol == null ? 1 : vol); b.currentTime = 0; b.play().catch(() => {});
    } catch (e) { /* no sound, the picture goes on */ }
  }
  function stopSounds() { for (const k in AUD) { try { AUD[k].pause(); } catch (e) { /* gone */ } } }
  function hintFrame(now) {
    if (!hintOn) return;
    const t = (now - hintAt) / 1000, k = inOut(t, 0, 2.2, 0.25);
    if (k <= 0) { hintOn = false; return; }
    g.save(); g.globalAlpha = k;
    const w = 760, x = W / 2 - w / 2, y = H - 150;
    g.beginPath(); g.roundRect(x, y, w, 76, 38); g.fillStyle = 'rgba(10,10,14,0.82)'; g.fill();
    g.lineWidth = 2; g.strokeStyle = 'rgba(244,193,82,0.8)'; g.stroke();
    txt('Press Back again to skip to the film', W / 2, y + 50, { w: 600, size: 32, color: '#fff' });
    g.restore();
  }
  function loop(now) {
    raf = requestAnimationFrame(loop);
    g.setTransform(RES, 0, 0, RES, 0, 0);
    if (TR) {
      trailerFrame((now - trAt) / 1000);
    } else if (cur) {
      const part = PARTS[cur.kind], t = (now - curAt) / 1000;
      // the sounds due by now
      while (cur._cue < part.cues.length && part.cues[cur._cue][0] <= t) { const [, n, v] = part.cues[cur._cue++]; sound(n, v); }
      part.draw(cur, Math.min(t, part.len));
      if (t >= part.len) { const k = cur.kind; cur = null; NATIVE.done(k); }
    }
    hintFrame(now);
  }

  window.CINEMA = {
    async init(c) {
      const cfg = typeof c === 'string' ? JSON.parse(c) : (c || {});
      RES = cfg.res > 0 && cfg.res <= 1 ? cfg.res : 1;
      size();
      grainBake();
      for (const [k, url] of Object.entries(cfg.images || {})) IMG[k] = await img(url);
      for (const w of [500, 600, 700, 800]) await document.fonts.load(`${w} 40px Mont`);
      for (const w of [600, 700]) await document.fonts.load(`${w} 40px KhandC`);
      for (const w of [700, 800]) await document.fonts.load(`${w} 40px BarlowC`);
      await document.fonts.ready;
      for (const n of ['cin-whoosh', 'cin-ding', 'cin-pop', 'cin-beep', 'cin-jingle', 'cin-boom', 'cin-shimmer', 'cin-drone', 'cin-projector']) {
        if (!AUD[n]) { AUD[n] = new Audio('sfx/' + n + '.ogg'); AUD[n].preload = 'auto'; }
      }
      if (!raf) raf = requestAnimationFrame(loop);
      NATIVE.ready();
    },
    play(part) {
      const p = typeof part === 'string' ? JSON.parse(part) : part;
      if (!PARTS[p.kind]) { NATIVE.done(p.kind); return; }
      TR = null; document.body.classList.remove('see');
      p._cue = 0; cur = p; curAt = performance.now();
      if (p.kind === 'intro') NATIVE.lights('off');
    },
    trailer(info) {
      const p = typeof info === 'string' ? JSON.parse(info) : info;
      cur = null; TR = p; trAt = performance.now();
      document.body.classList.add('see');
    },
    hint() { hintOn = true; hintAt = performance.now(); },
    // a picture handed over later (a crest, a trailer's picture), under its key
    image(key, url) { img(url).then((i) => { if (i) IMG[key] = i; }); },
    stop() { cur = null; TR = null; stopSounds(); g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, cv.width, cv.height); },
    // for a preview: the part's picture at t, and the sounds it plays (when, which, how loud)
    frame(part, t) {
      g.setTransform(RES, 0, 0, RES, 0, 0);
      if (part.kind === 'trailer') { TR = part; trailerFrame(t); TR = null; return; }
      PARTS[part.kind].draw(part, t);
    },
    len(kind) { return PARTS[kind] ? PARTS[kind].len : 0; },
    cues(kind) { return PARTS[kind] ? PARTS[kind].cues : []; },
  };
})();
