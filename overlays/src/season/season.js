// The season intros: a league's clubs one after another, each in a scene of its own made from what is on its crest or
// what it is known by (Brighton's gulls, Leeds' white roses, Forest's tree, Arsenal's cannons), a bar each to the music
// (two seconds at 120 beats a minute), opened by the league's mark and the season and closed by all its crests together.
// After the family's link (a fan's season intro: each club's crest with its own emblems around it); drawn here, every
// picture our own or from the open Twemoji set (CC-BY 4.0), the crests the clubs' own. Rendered ahead
// (tools/season_render.js) into intros/season-<league>.mp4, the Whitebrook Road Sports ending (intros/tail.mp4) after.
// Deterministic: draw(t) draws second t from nothing carried between frames.
//   SEASON.init(cfg) -> Promise<{len}>; SEASON.draw(t)
// cfg: {season: '2026/27', mark (data URL: the league's mark, white), clubs: [{name, crest (data URL), bg: {t, c: [..]},
//       ink, accent, fx: [{m: 'e:<code>' | 's:<shape>', a: how it moves, n, tint, ol, alt, s, slow}]}],
//       pics: {'e:<code>': data URL}}
(function () {
  const W = 1920, H = 1080, BAR = 2.0, OPEN = 2 * BAR, FIN = 2 * BAR, WIPE = 0.22, PI = Math.PI, TAU = 2 * PI;
  const CX = 960, CY = 450, CREST = 360, NAME_Y = 978, OUTLINE = '#15121F';
  let C, g, cfg, N = 0, LEN = 0;
  const S = { clubs: [], pics: {} };

  // ---- small things --------------------------------------------------------------------------------------------
  function rng(seed) { let s = (seed >>> 0) || 1; return () => { s ^= s << 13; s >>>= 0; s ^= s >> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; }; }
  const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
  const ramp = (a, b, x) => clamp((x - a) / (b - a));
  const lerp = (a, b, t) => a + (b - a) * t;
  const eo = (t) => 1 - Math.pow(1 - clamp(t), 3);
  const ei = (t) => Math.pow(clamp(t), 3);
  const eio = (t) => { t = clamp(t); return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; };
  const eob = (t, k = 1.9) => { t = clamp(t); const c3 = k + 1; return 1 + c3 * Math.pow(t - 1, 3) + k * Math.pow(t - 1, 2); };
  const DEG = PI / 180;
  function mk(w, h) { const c = document.createElement('canvas'); c.width = Math.max(1, Math.ceil(w)); c.height = Math.max(1, Math.ceil(h)); return c; }
  function img(src) { return new Promise((res) => { if (!src) return res(null); const i = new Image(); i.onload = () => res(i); i.onerror = () => res(null); i.src = src; }); }
  const rgb = (h) => { const n = parseInt(h.slice(1, 7), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; };
  const rgba = (h, a) => { const [r, gg, b] = rgb(h); return `rgba(${r},${gg},${b},${a})`; };
  const shade = (h, k) => { const [r, gg, b] = rgb(h); const f = (v) => Math.round(clamp(k < 0 ? v * (1 + k) : v + (255 - v) * k, 0, 255)); return `rgb(${f(r)},${f(gg)},${f(b)})`; };
  const light = (h) => { const [r, gg, b] = rgb(h); return (0.299 * r + 0.587 * gg + 0.114 * b) / 255; };

  // the picture's drawn part only (transparent margins cut away)
  function trimmed(im) {
    const w = im.naturalWidth || im.width, h = im.naturalHeight || im.height, c0 = mk(w, h), x0 = c0.getContext('2d');
    x0.drawImage(im, 0, 0, w, h);
    const d = x0.getImageData(0, 0, w, h).data;
    let a = w, b = h, cc = 0, dd = 0;
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (d[(y * w + x) * 4 + 3] > 12) { if (x < a) a = x; if (x > cc) cc = x; if (y < b) b = y; if (y > dd) dd = y; }
    if (cc < a) return c0;
    const c = mk(cc - a + 1, dd - b + 1); c.getContext('2d').drawImage(c0, -a, -b); return c;
  }
  // the picture in one colour
  function silhouette(src, w, h, col) {
    const c = mk(w, h), x = c.getContext('2d');
    x.drawImage(src, 0, 0, w, h); x.globalCompositeOperation = 'source-in'; x.fillStyle = col; x.fillRect(0, 0, w, h);
    return c;
  }
  // a sticker: the picture (in one colour, when tinted) inside a thick outline, with a hard shadow down to the right;
  // {c, ax, ay}: drawn with its middle at (ax, ay)
  function sticker(src, size, o = {}) {
    const sw = src.naturalWidth || src.width, sh = src.naturalHeight || src.height, k = size / Math.max(sw, sh);
    const w = Math.max(2, Math.round(sw * k)), h = Math.max(2, Math.round(sh * k));
    const face = mk(w, h), fx = face.getContext('2d');
    fx.imageSmoothingQuality = 'high'; fx.drawImage(src, 0, 0, w, h);
    const top = o.tint ? silhouette(face, w, h, o.tint) : face;
    const ow = Math.max(3, Math.round(size * (o.ow || 0.034))), pad = ow + 2;
    const A = mk(w + 2 * pad, h + 2 * pad), ax = A.getContext('2d'), line = silhouette(top, w, h, o.ol || OUTLINE);
    for (let i = 0; i < 32; i++) { const a = (i / 32) * TAU; ax.drawImage(line, pad + Math.cos(a) * ow, pad + Math.sin(a) * ow); }
    ax.drawImage(line, pad, pad); ax.drawImage(top, pad, pad);
    const so = Math.round(size * 0.04), B = mk(A.width + so, A.height + so), bx = B.getContext('2d');
    bx.globalAlpha = o.shadow == null ? 0.3 : o.shadow; bx.drawImage(silhouette(A, A.width, A.height, '#000'), so * 0.8, so); bx.globalAlpha = 1;
    bx.drawImage(A, 0, 0);
    return { c: B, ax: A.width / 2, ay: A.height / 2 };
  }
  // a sticker drawn: middle at (x, y), size k (1: as made), turned rot, flipped when flip, squashed sy
  function put(s, x, y, k = 1, rot = 0, alpha = 1, flip = false, sy = 1) {
    if (!s || k <= 0.001 || alpha <= 0.002) return;
    g.save(); g.globalAlpha *= alpha; g.translate(x, y); if (rot) g.rotate(rot); g.scale(flip ? -k : k, k * sy);
    g.drawImage(s.c, -s.ax, -s.ay); g.restore();
  }

  // ---- shapes of our own (drawn into a canvas, then made stickers) ---------------------------------------------------
  const SHAPES = {
    cannon(x) {   // side on, facing right: the barrel on its carriage, a spoked wheel
      x.canvas.width = 640; x.canvas.height = 400;
      x.save(); x.translate(300, 170); x.rotate(-12 * DEG);
      const bg = x.createLinearGradient(0, -60, 0, 60); bg.addColorStop(0, '#9AA3B5'); bg.addColorStop(0.35, '#5A6273'); bg.addColorStop(1, '#262B35');
      x.fillStyle = bg; x.beginPath(); x.moveTo(-230, -62); x.lineTo(250, -40); x.lineTo(250, 40); x.lineTo(-230, 62); x.quadraticCurveTo(-290, 0, -230, -62); x.fill();
      x.fillStyle = '#3A404D'; x.fillRect(236, -52, 34, 104); x.fillRect(-120, -66, 26, 132); x.fillRect(80, -54, 22, 108);
      x.fillStyle = '#C9A24B'; x.fillRect(240, -48, 26, 96); x.beginPath(); x.arc(-262, 0, 26, 0, TAU); x.fill();
      x.fillStyle = '#11141A'; x.beginPath(); x.ellipse(268, 0, 10, 36, 0, 0, TAU); x.fill();
      x.restore();
      x.fillStyle = '#7A4B26'; x.beginPath(); x.moveTo(110, 200); x.lineTo(470, 230); x.lineTo(470, 290); x.lineTo(150, 300); x.closePath(); x.fill();
      x.fillStyle = '#5E3818'; x.fillRect(150, 280, 320, 20);
      const wx = 250, wy = 290, R = 98;
      x.lineWidth = 16; x.strokeStyle = '#D4A84B'; x.beginPath(); x.arc(wx, wy, R, 0, TAU); x.stroke();
      x.lineWidth = 9; for (let i = 0; i < 8; i++) { const a = (i / 8) * TAU; x.beginPath(); x.moveTo(wx, wy); x.lineTo(wx + Math.cos(a) * R, wy + Math.sin(a) * R); x.stroke(); }
      x.fillStyle = '#A07A2E'; x.beginPath(); x.arc(wx, wy, 22, 0, TAU); x.fill();
    },
    rose(x) {   // the white rose: five outer petals, five inner, a gold heart, green tips between
      x.canvas.width = 400; x.canvas.height = 400; x.translate(200, 200);
      x.fillStyle = '#3E8E3A';
      for (let i = 0; i < 5; i++) { x.save(); x.rotate((i + 0.5) / 5 * TAU); x.beginPath(); x.moveTo(0, -120); x.lineTo(26, -190); x.lineTo(-26, -190); x.closePath(); x.fill(); x.restore(); }
      const petal = (r, w, col, rot) => {
        for (let i = 0; i < 5; i++) {
          x.save(); x.rotate(rot + (i / 5) * TAU);
          const pg = x.createRadialGradient(0, -r * 0.55, 4, 0, -r * 0.55, r * 0.7); pg.addColorStop(0, '#FFFFFF'); pg.addColorStop(1, col);
          x.fillStyle = pg; x.beginPath(); x.moveTo(0, 0); x.bezierCurveTo(-w, -r * 0.25, -w * 1.05, -r * 1.02, 0, -r * 0.86); x.bezierCurveTo(w * 1.05, -r * 1.02, w, -r * 0.25, 0, 0); x.fill();
          x.strokeStyle = 'rgba(120,120,140,0.35)'; x.lineWidth = 3; x.stroke(); x.restore();
        }
      };
      petal(178, 108, '#E4E6EE', 0); petal(118, 70, '#ECEEF4', TAU / 10);
      x.fillStyle = '#F2C230'; x.beginPath(); x.arc(0, 0, 42, 0, TAU); x.fill();
      x.fillStyle = '#B8860B'; for (let i = 0; i < 12; i++) { const a = (i / 12) * TAU; x.beginPath(); x.arc(Math.cos(a) * 26, Math.sin(a) * 26, 6, 0, TAU); x.fill(); }
    },
    tower(x) {   // a round lock-up tower: stone, a band of windows, a dark conical roof
      x.canvas.width = 300; x.canvas.height = 620;
      const sg = x.createLinearGradient(40, 0, 260, 0); sg.addColorStop(0, '#B9B2A2'); sg.addColorStop(0.45, '#F3EFE4'); sg.addColorStop(1, '#9C9584');
      x.fillStyle = sg; x.fillRect(50, 200, 200, 400);
      x.fillStyle = '#8F887A'; for (let i = 0; i < 7; i++) x.fillRect(50, 250 + i * 50, 200, 4);
      x.fillStyle = '#1F2A4A'; x.beginPath(); x.moveTo(150, 10); x.lineTo(272, 210); x.lineTo(28, 210); x.closePath(); x.fill();
      x.fillStyle = '#E8E2D2'; x.fillRect(36, 196, 228, 26);
      x.fillStyle = '#2B2F3A'; for (const wx of [80, 135, 190]) { x.beginPath(); x.roundRect(wx, 270, 34, 70, [17, 17, 0, 0]); x.fill(); }
      x.beginPath(); x.roundRect(118, 470, 64, 130, [32, 32, 0, 0]); x.fill();
    },
    towers(x) {   // two tall brick towers, the second leaning
      x.canvas.width = 420; x.canvas.height = 720;
      const tw = (cx, w, h, lean) => {
        x.save(); x.translate(cx, 715); x.rotate(lean);
        const gr = x.createLinearGradient(-w / 2, 0, w / 2, 0); gr.addColorStop(0, '#8E3B26'); gr.addColorStop(0.5, '#C9644A'); gr.addColorStop(1, '#7A3020');
        x.fillStyle = gr; x.fillRect(-w / 2, -h, w, h);
        x.fillStyle = '#6E2A1B'; x.fillRect(-w / 2 - 8, -h - 18, w + 16, 22);
        x.fillStyle = '#3A1810'; for (let i = 0; i < 6; i++) x.fillRect(-8, -h + 60 + i * (h - 120) / 6, 16, 30);
        x.restore();
      };
      tw(135, 92, 690, 0); tw(290, 108, 420, 4 * DEG);
    },
    spires(x) {   // four tall spires, rounded tips, stone with dark slits
      x.canvas.width = 520; x.canvas.height = 720;
      const sp = (cx, h, w) => {
        const gr = x.createLinearGradient(cx - w / 2, 0, cx + w / 2, 0); gr.addColorStop(0, '#B39C72'); gr.addColorStop(0.5, '#EADBB8'); gr.addColorStop(1, '#A08A60');
        x.fillStyle = gr; x.beginPath(); x.moveTo(cx - w / 2, 715); x.lineTo(cx - w * 0.18, 715 - h); x.quadraticCurveTo(cx, 715 - h - w * 0.5, cx + w * 0.18, 715 - h); x.lineTo(cx + w / 2, 715); x.closePath(); x.fill();
        x.fillStyle = '#E8B53A'; x.beginPath(); x.arc(cx, 715 - h - w * 0.32, w * 0.16, 0, TAU); x.fill();
        x.fillStyle = 'rgba(60,40,20,0.75)'; for (let i = 0; i < 8; i++) { const yy = 715 - h * 0.85 + i * h * 0.1; x.fillRect(cx - 4, yy, 8, h * 0.05); }
      };
      sp(90, 520, 104); sp(205, 640, 116); sp(320, 640, 116); sp(435, 520, 104);
    },
    lighthouse(x) {   // red and white bands, a gallery, the lantern
      x.canvas.width = 300; x.canvas.height = 640;
      x.save(); x.beginPath(); x.moveTo(95, 160); x.lineTo(205, 160); x.lineTo(245, 630); x.lineTo(55, 630); x.closePath(); x.clip();
      for (let i = 0; i < 6; i++) { x.fillStyle = i % 2 ? '#FFFFFF' : '#D7263D'; x.fillRect(0, 160 + i * 80, 300, 80); }
      const sh = x.createLinearGradient(50, 0, 250, 0); sh.addColorStop(0, 'rgba(0,0,0,0.25)'); sh.addColorStop(0.5, 'rgba(255,255,255,0.15)'); sh.addColorStop(1, 'rgba(0,0,0,0.3)');
      x.fillStyle = sh; x.fillRect(0, 160, 300, 480); x.restore();
      x.fillStyle = '#2B2F3A'; x.fillRect(70, 140, 160, 22); x.fillRect(100, 70, 100, 72);
      x.fillStyle = '#FFE48A'; x.fillRect(112, 82, 76, 52);
      x.fillStyle = '#2B2F3A'; x.beginPath(); x.moveTo(90, 72); x.lineTo(150, 22); x.lineTo(210, 72); x.closePath(); x.fill();
      x.fillStyle = '#2B2F3A'; x.beginPath(); x.roundRect(128, 560, 44, 70, [22, 22, 0, 0]); x.fill();
    },
    submarine(x) {   // the yellow submarine: hull, tower, periscope, portholes
      x.canvas.width = 640; x.canvas.height = 320;
      x.fillStyle = '#FFD93B'; x.beginPath(); x.ellipse(310, 200, 270, 92, 0, 0, TAU); x.fill();
      x.fillStyle = '#F2B705'; x.beginPath(); x.ellipse(310, 240, 250, 48, 0, 0, PI); x.fill();
      x.fillStyle = '#FFD93B'; x.beginPath(); x.roundRect(230, 64, 150, 82, 18); x.fill();
      x.fillStyle = '#E0A800'; x.fillRect(300, 10, 16, 60); x.fillRect(300, 10, 50, 16);
      for (const px of [180, 280, 380, 470]) { x.fillStyle = '#1D4E89'; x.beginPath(); x.arc(px, 190, 26, 0, TAU); x.fill(); x.fillStyle = '#8FD3FF'; x.beginPath(); x.arc(px - 6, 184, 10, 0, TAU); x.fill(); }
      x.fillStyle = '#C98F00'; x.beginPath(); x.moveTo(40, 200); x.lineTo(4, 150); x.lineTo(4, 250); x.closePath(); x.fill();
    },
    pinecone(x) {   // the city's pine cone: rows of scales
      x.canvas.width = 260; x.canvas.height = 380;
      x.save(); x.beginPath(); x.ellipse(130, 200, 108, 170, 0, 0, TAU); x.clip();
      x.fillStyle = '#5E3A1A'; x.fillRect(0, 0, 260, 380);
      for (let r = 0; r < 9; r++) for (let c = 0; c < 6; c++) {
        const cx = 20 + c * 45 + (r % 2) * 22, cy = 40 + r * 40;
        const gr = x.createLinearGradient(cx, cy - 20, cx, cy + 24); gr.addColorStop(0, '#C98A44'); gr.addColorStop(1, '#7A4A1E');
        x.fillStyle = gr; x.beginPath(); x.moveTo(cx - 22, cy - 6); x.quadraticCurveTo(cx, cy + 34, cx + 22, cy - 6); x.quadraticCurveTo(cx, cy + 6, cx - 22, cy - 6); x.fill();
      }
      x.restore();
      x.fillStyle = '#3E7A2E'; x.beginPath(); x.moveTo(130, 6); x.lineTo(160, 40); x.lineTo(100, 40); x.closePath(); x.fill();
    },
    trident(x) {   // a long gold trident, barbed points
      x.canvas.width = 260; x.canvas.height = 700;
      const gd = x.createLinearGradient(0, 0, 260, 0); gd.addColorStop(0, '#B8860B'); gd.addColorStop(0.5, '#FFE27A'); gd.addColorStop(1, '#B8860B');
      x.fillStyle = gd; x.fillRect(116, 150, 28, 545);
      x.beginPath(); x.moveTo(30, 190); x.quadraticCurveTo(40, 250, 130, 260); x.quadraticCurveTo(220, 250, 230, 190); x.lineTo(212, 190);
      x.quadraticCurveTo(200, 232, 130, 236); x.quadraticCurveTo(60, 232, 48, 190); x.closePath(); x.fill();
      const prong = (cx, top) => { x.beginPath(); x.moveTo(cx - 13, 200); x.lineTo(cx - 13, top + 40); x.lineTo(cx - 34, top + 58); x.lineTo(cx, top); x.lineTo(cx + 34, top + 58); x.lineTo(cx + 13, top + 40); x.lineTo(cx + 13, 200); x.closePath(); x.fill(); };
      prong(39, 40); prong(130, 6); prong(221, 40);
      x.fillStyle = '#8B6508'; x.fillRect(110, 300, 40, 18); x.fillRect(110, 640, 40, 18);
    },
    eiffel(x) {   // the tower: four legs, the arch, two platforms, lattice
      x.canvas.width = 420; x.canvas.height = 760;
      const col = '#C7A46A', dk = '#7E6232';
      x.fillStyle = col; x.strokeStyle = dk; x.lineWidth = 6;
      x.beginPath(); x.moveTo(210, 10); x.lineTo(226, 120); x.lineTo(246, 300); x.quadraticCurveTo(300, 520, 400, 750); x.lineTo(300, 750);
      x.quadraticCurveTo(210, 600, 120, 750); x.lineTo(20, 750); x.quadraticCurveTo(120, 520, 174, 300); x.lineTo(194, 120); x.closePath(); x.fill(); x.stroke();
      x.fillStyle = dk; x.fillRect(150, 300, 120, 18); x.fillRect(100, 520, 220, 22); x.fillRect(196, 112, 28, 14);
      x.strokeStyle = 'rgba(80,60,30,0.6)'; x.lineWidth = 3;
      for (let i = 0; i < 14; i++) { const yy = 140 + i * 42, hw = 20 + i * i * 0.9; x.beginPath(); x.moveTo(210 - hw, yy); x.lineTo(210 + hw, yy + 42); x.moveTo(210 + hw, yy); x.lineTo(210 - hw, yy + 42); x.stroke(); }
    },
  };

  // ---- loading -------------------------------------------------------------------------------------------------
  async function init(c) {
    cfg = typeof c === 'string' ? JSON.parse(c) : c;
    C = document.getElementById('season'); C.width = W; C.height = H; g = C.getContext('2d');
    await document.fonts.load('800 80px BarlowC'); await document.fonts.load('900 80px Outfit'); await document.fonts.load('700 40px Outfit');
    const mark = await img(cfg.mark);
    S.mark = mark ? trimmed(mark) : null;
    for (const [k, u] of Object.entries(cfg.pics || {})) { const i = await img(u); if (i) S.pics[k] = i; }
    for (const [k, draw] of Object.entries(SHAPES)) { const cc = mk(10, 10); draw(cc.getContext('2d')); S.pics['s:' + k] = cc; }
    N = cfg.clubs.length;
    LEN = OPEN + N * BAR + FIN;
    let seed = 7;
    for (const cl of cfg.clubs) {
      const ci = await img(cl.crest);
      const crest = ci ? trimmed(ci) : null;
      const club = { ...cl, crest: crest ? sticker(crest, CREST, { ol: '#FFFFFF', ow: 0.045, shadow: 0.38 }) : null,
        mini: crest ? sticker(crest, 150, { ol: '#FFFFFF', ow: 0.05, shadow: 0.35 }) : null, fx: [], seed: seed++ };
      for (const f of cl.fx || []) {
        const pic = S.pics[f.m];
        const base = { roar: 700, grow: 640, flank: 400, sail: 300, run: 230, hop: 190, bloom: 130, orbit: 120, rain: 120, swarm: 92, fly: 150, stack: 210 }[f.a] || 160;
        const size = Math.round(base * (f.s || 1));
        const o = { tint: f.tint, ol: f.ol };
        const st = f.m === 's:gull' || f.m === 's:waves' || f.m === 's:rauten' || f.m === 's:musicians' ? null : pic ? sticker(pic, size, o) : null;
        const alt = f.alt && pic ? sticker(pic, size, { tint: f.alt, ol: f.tint || OUTLINE }) : null;
        club.fx.push({ ...f, st, alt, size });
      }
      if ((cl.fx || []).some((f) => f.m === 's:musicians')) {
        club.band = ['e:1facf', 'e:1f415', 'e:1f408', 'e:1f413'].map((k, i) => (S.pics[k] ? sticker(S.pics[k], [330, 250, 210, 180][i]) : null));
      }
      S.clubs.push(club);
    }
    // the drifting gold specks
    const r = rng(99); S.dots = [];
    for (let i = 0; i < 46; i++) S.dots.push({ x: r() * W, y: r() * (H + 200), v: 18 + r() * 46, r: 1.6 + r() * 4.6, p: r() * TAU, a: 0.3 + r() * 0.55 });
    return { len: LEN };
  }

  // ---- backgrounds ---------------------------------------------------------------------------------------------
  function radial(c1, c2, x = CX, y = CY) {
    const gr = g.createRadialGradient(x, y, 40, x, y, 1250); gr.addColorStop(0, c1); gr.addColorStop(1, c2);
    g.fillStyle = gr; g.fillRect(0, 0, W, H);
  }
  function background(cl, u) {
    const b = cl.bg || { t: 'radial', c: ['#333', '#000'] }, [c1, c2] = b.c;
    switch (b.t) {
      case 'stripes': {
        const n = b.n || 9, sw = W / n, off = (u * 24) % (2 * sw);
        g.fillStyle = c2; g.fillRect(0, 0, W, H);
        g.fillStyle = c1; for (let i = -2; i < n + 2; i += 2) g.fillRect(i * sw + off, 0, sw, H);
        break;
      }
      case 'split': {
        g.fillStyle = c2; g.fillRect(0, 0, W, H);
        g.fillStyle = c1; g.beginPath(); g.moveTo(0, 0); g.lineTo(1120 + u * 10, 0); g.lineTo(800 + u * 10, H); g.lineTo(0, H); g.closePath(); g.fill();
        break;
      }
      case 'sash': {
        g.fillStyle = c1; g.fillRect(0, 0, W, H);
        g.fillStyle = c2; g.beginPath(); g.moveTo(-200, -60); g.lineTo(120, -60); g.lineTo(W + 200, H + 60); g.lineTo(W - 120, H + 60); g.closePath(); g.fill();
        break;
      }
      case 'cross': {
        g.fillStyle = c1; g.fillRect(0, 0, W, H);
        g.fillStyle = c2; g.fillRect(CX - 110, 0, 220, H); g.fillRect(0, CY - 80, W, 160);
        break;
      }
      case 'band': {
        g.fillStyle = c1; g.fillRect(0, 0, W, H);
        g.fillStyle = c2; g.fillRect(0, CY - 120, W, 240);
        break;
      }
      case 'tiger': {
        g.fillStyle = c1; g.fillRect(0, 0, W, H);
        g.fillStyle = c2;
        for (let i = -2; i < 16; i++) {
          const x0 = i * 150 + (u * 30) % 150;
          g.beginPath(); g.moveTo(x0, -40);
          for (let y = -40; y <= H + 40; y += 60) g.lineTo(x0 + Math.sin(y / 90 + i) * 30 + y * 0.35, y);
          for (let y = H + 40; y >= -40; y -= 60) g.lineTo(x0 + 34 + Math.sin(y / 70 + i * 2) * 22 + y * 0.35, y);
          g.closePath(); g.fill();
        }
        break;
      }
      case 'tiles': {
        g.fillStyle = c2; g.fillRect(0, 0, W, H);
        const sz = 120;
        for (let y = 0; y < H; y += sz) for (let x = 0; x < W; x += sz) {
          const d = Math.hypot(x + sz / 2 - CX, y + sz / 2 - CY) / 1100, k = Math.cos(clamp((u + 0.2 - d * 0.5) / 0.35) * PI);
          g.fillStyle = ((x + y) / sz) % 2 ? c1 : shade(c1, -0.35);
          const ww = Math.abs(k) * (sz - 6);
          g.fillRect(x + (sz - ww) / 2, y + 3, ww, sz - 6);
        }
        break;
      }
      default: radial(c1, c2);
    }
    // light behind the crest, turning rays, the edges darker
    rays(cl.px || CX, cl.py || CY, u, light(c1) > 0.75 ? 0.05 : 0.075);
    const sp = g.createRadialGradient(cl.px || CX, cl.py || CY, 20, cl.px || CX, cl.py || CY, 520);
    sp.addColorStop(0, 'rgba(255,255,255,0.28)'); sp.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = sp; g.fillRect(0, 0, W, H);
    vignette(0.5);
  }
  function rays(x, y, u, a) {
    g.save(); g.translate(x, y); g.rotate(u * 0.07);
    g.fillStyle = `rgba(255,255,255,${a})`;
    for (let i = 0; i < 16; i++) { g.beginPath(); g.moveTo(0, 0); g.arc(0, 0, 1700, (i / 16) * TAU, (i / 16) * TAU + TAU / 32); g.closePath(); g.fill(); }
    g.restore();
  }
  function vignette(a) {
    const v = g.createRadialGradient(CX, H * 0.48, 380, CX, H * 0.48, 1150);
    v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, `rgba(0,0,0,${a})`);
    g.fillStyle = v; g.fillRect(0, 0, W, H);
  }

  // ---- what moves ----------------------------------------------------------------------------------------------
  // a gull: white, wings up and down (flap -1..1), dark tips, a yellow beak; dir 1 flying right
  function gull(x, y, s, flap, dir, alpha = 1, body = '#FFFFFF') {
    g.save(); g.globalAlpha *= alpha; g.translate(x, y); g.scale(dir * s / 100, s / 100);
    const wy = -flap * 38;
    g.lineJoin = 'round'; g.lineWidth = 7; g.strokeStyle = OUTLINE;
    g.fillStyle = body;
    g.beginPath(); g.moveTo(-8, 0); g.quadraticCurveTo(-40, wy - 20, -96, wy + 6); g.quadraticCurveTo(-46, wy + 4, -6, 14); g.closePath(); g.fill(); g.stroke();
    g.beginPath(); g.moveTo(8, 0); g.quadraticCurveTo(40, wy - 24, 92, wy - 2); g.quadraticCurveTo(46, wy + 4, 6, 14); g.closePath(); g.fill(); g.stroke();
    g.fillStyle = '#3A3F4B'; g.beginPath(); g.moveTo(-96, wy + 6); g.lineTo(-74, wy - 4); g.lineTo(-70, wy + 8); g.closePath(); g.fill();
    g.beginPath(); g.moveTo(92, wy - 2); g.lineTo(70, wy - 10); g.lineTo(68, wy + 4); g.closePath(); g.fill();
    g.fillStyle = body; g.beginPath(); g.ellipse(4, 6, 34, 15, -0.08, 0, TAU); g.fill(); g.stroke();
    g.beginPath(); g.arc(36, 0, 12, 0, TAU); g.fill(); g.stroke();
    g.fillStyle = '#F2B705'; g.beginPath(); g.moveTo(46, 0); g.lineTo(64, 5); g.lineTo(46, 8); g.closePath(); g.fill();
    g.fillStyle = OUTLINE; g.beginPath(); g.arc(40, -3, 2.6, 0, TAU); g.fill();
    g.restore();
  }
  // the waves along the foot: two behind (front false) or the one in front
  function waves(col, u, front) {
    const layers = front ? [[0.0, 905, 26, 1.0]] : [[0.5, 830, 22, 0.55], [0.25, 868, 24, 0.8]];
    for (const [ph, y0, amp, a] of layers) {
      g.fillStyle = front ? col : shade(col, 0.25 * (1 - a));
      g.globalAlpha = front ? 1 : 0.9;
      g.beginPath(); g.moveTo(0, H);
      for (let x = 0; x <= W; x += 20) g.lineTo(x, y0 + Math.sin(x / 140 + u * 3.2 + ph * 9) * amp + Math.sin(x / 61 - u * 2.1) * amp * 0.3);
      g.lineTo(W, H); g.closePath(); g.fill();
      g.strokeStyle = 'rgba(255,255,255,0.55)'; g.lineWidth = 5; g.stroke();
      g.globalAlpha = 1;
    }
  }
  const back = [], front = [];   // draw lists for the scene being drawn: [z, fn]
  function act(cl, f, u, i0) {
    const r = rng(cl.seed * 31 + i0 * 7), s = f.st, n = f.n || 1;
    const enter = (d) => eob(ramp(d, d + 0.3, u), 1.7);
    switch (f.a) {
      case 'fly': {
        for (let i = 0; i < n; i++) {
          const dir = f.m === 's:gull' ? (i % 3 === 2 ? -1 : 1) : (r() < 0.5 ? 1 : -1), sz = 0.65 + r() * 0.75, y = 120 + r() * 640, v = 520 + r() * 420;
          const x0 = (r() * (W + 600)) - 300, x = ((x0 + dir * v * (u + 0.5)) % (W + 700) + (W + 700)) % (W + 700) - 350;
          const fl = Math.sin(u * TAU * (2.2 + r()) + i), bob = Math.sin(u * 3 + i) * 14;
          const big = sz > 1.15, list = big ? front : back;
          if (f.m === 's:gull') list.push(() => gull(x, y + bob, 118 * sz, fl, dir, 1, f.tint || '#FFFFFF'));
          else { const st = f.alt && i % 2 ? f.alt : s; list.push(() => put(st, x, y + bob, sz, dir * 6 * DEG, 1, dir > 0, 0.82 + 0.18 * fl)); }
        }
        break;
      }
      case 'bloom': {
        for (let i = 0; i < n; i++) {
          const a = (i / n) * TAU + u * 0.25 - PI / 2, rx = 470, ry = 300;
          const x = (cl.px || CX) + Math.cos(a) * rx, y = (cl.py || CY) + 20 + Math.sin(a) * ry, k = enter(0.04 + i * 0.045);
          const pulse = 1 + 0.08 * Math.exp(-((u * 2) % 1) * 5);
          back.push(() => put(s, x, y, k * pulse * (0.9 + 0.2 * ((i * 37) % 10) / 10), Math.sin(u * 2.2 + i) * 10 * DEG));
        }
        break;
      }
      case 'grow': {
        if (f.m === 's:rauten') { back.push(() => rauten(u)); break; }
        if (f.m === 'e:1f680') {   // the rocket goes up past the crest, a trail of smoke behind
          const p = ramp(0.0, 1.9, u), x = lerp(520, 1500, eio(p)), y = lerp(1180, -160, eio(p));
          back.push(() => { for (let j = 0; j < 14; j++) { const q = p - j * 0.035; if (q < 0) continue; const px = lerp(520, 1500, eio(q)), py = lerp(1180, -160, eio(q)); g.fillStyle = `rgba(255,255,255,${0.5 - j * 0.03})`; g.beginPath(); g.arc(px - 30, py + 60, 26 + j * 5, 0, TAU); g.fill(); } put(s, x, y, 0.7, 40 * DEG); });
          break;
        }
        for (let i = 0; i < n; i++) {
          const spread = n === 1 ? [0] : n === 2 ? [-560, 560] : n === 3 ? [-560, 560, -300] : [-700, -360, 0, 360, 700];
          const dx = spread[i % spread.length], k = enter(0.02 + i * 0.07) * (n <= 2 ? 1 : i === 2 && n === 3 ? 0.62 : 0.78);
          const bx = f.side ? 600 : (cl.px || CX) + dx, by = f.side ? CY + 20 : n === 1 ? (cl.py || CY) - 30 : 610;
          const rise = (1 - eo(ramp(0, 0.35, u + i * 0.02))) * 300;
          if (f.m === 's:lighthouse') back.push(() => beam(bx + 0, by - 300 * k, u));
          back.push(() => put(s, bx, by + rise + (n === 1 ? 0 : 0), k * (1 + 0.015 * Math.sin(u * 4)), Math.sin(u * 1.6 + i) * 1.5 * DEG));
        }
        break;
      }
      case 'flank': {
        const flipL = !/^s:/.test(f.m) && !String(f.m).match(/1f981|1f531|1f3c1|2692|26cf|2693|1f98c/);   // most of the set face left
        for (let i = 0; i < 2; i++) {
          const side = i ? 1 : -1, k = enter(0.12);
          const tx = (cl.px || CX) + side * 560, x = lerp(tx + side * 900, tx, eo(ramp(0.05, 0.45, u))), y = (cl.py || CY) + 70 + Math.sin(u * 3 + i) * 8;
          let kick = 0;
          if (f.m === 's:cannon') for (const b of [0.55, 1.05, 1.55]) { const q = u - b; if (q > 0 && q < 0.3) kick = Math.sin(clamp(q / 0.3) * PI) * 26; }
          const flip = f.m === 's:cannon' ? side > 0 : flipL ? side < 0 : side > 0, lean = f.m === 's:trident' ? side * -18 * DEG : side * -4 * DEG;
          back.push(() => {
            put(s, x - side * kick * -1, y, k, lean + Math.sin(u * 2.4 + i) * 2 * DEG, 1, flip);
            if (f.m === 's:cannon') for (const b of [0.55, 1.05, 1.55]) { const q = u - b; if (q > 0 && q < 0.7) smoke(x - side * 210, y - 70, q); }
          });
        }
        break;
      }
      case 'swarm': {
        for (let i = 0; i < n; i++) {
          const A = 380 + r() * 520, B = 170 + r() * 260, w1 = 0.9 + r() * 1.4, w2 = 1.3 + r() * 1.6, p1 = r() * TAU, p2 = r() * TAU, sz = 0.7 + r() * 0.6;
          const x = (cl.px || CX) + A * Math.sin(w1 * u + p1), y = (cl.py || CY) + B * Math.sin(w2 * u + p2);
          const vx = A * w1 * Math.cos(w1 * u + p1), vy = B * w2 * Math.cos(w2 * u + p2);
          const ent = eo(ramp(-0.1 + i * 0.02, 0.35 + i * 0.02, u)), ex = lerp(x + Math.sign(vx || 1) * -1100, x, ent);
          const fl = f.m === 'e:1f987' ? 0.6 + 0.4 * Math.sin(u * 30 + i) : 1;
          (i % 3 === 0 ? front : back).push(() => put(s, ex, y, sz, clamp(vy / 900, -0.5, 0.5), 1, vx > 0, fl));
        }
        break;
      }
      case 'rain': {
        if (f.m === 'e:26a1') {   // lightning: strikes, each flashing on a beat
          for (let i = 0; i < n; i++) {
            const at = (i * 0.25) % 2, q = u - at, x = 200 + r() * 1520, y = 160 + r() * 520, rot = (r() - 0.5) * 0.4;
            if (q > 0 && q < 0.45) back.push(() => { const fl = q < 0.08 ? 1 : 0.75 + 0.25 * Math.sin(q * 60); put(s, x, y, 1.3 * eob(q / 0.12), rot, fl); });
          }
          break;
        }
        for (let i = 0; i < n; i++) {
          const x = 120 + ((i + r() * 0.8) / n) * (W - 240), v = 520 + r() * 420, y0 = -180 - r() * 900, spin = (r() - 0.5) * 3, sz = 0.75 + r() * 0.55;
          const y = y0 + v * (u + 0.3);
          if (y > H + 150) continue;
          back.push(() => put(s, x + Math.sin(u * 2 + i) * 30, y, sz, spin * u + i));
        }
        break;
      }
      case 'roar': {
        const k = eob(ramp(0, 0.32, u), 2.2), sh = u > 0 && u < 0.45 ? (1 - u / 0.45) * 16 : 0, j = rng(Math.floor(u * 30) + 5);
        const x = 600 + (j() - 0.5) * sh, y = CY + 40 + (j() - 0.5) * sh;
        back.push(() => {
          if (u > 0 && u < 0.7) burst(x, y, u);
          put(s, x, y, k * (1 + 0.02 * Math.sin(u * 4)), Math.sin(u * 2) * 2 * DEG);
        });
        break;
      }
      case 'sail': {
        if (f.m === 's:waves') break;   // drawn with the scene (behind and in front of the boats)
        for (let i = 0; i < n; i++) {
          const dir = f.m === 's:submarine' ? -1 : 1, x = dir > 0 ? lerp(-260 + i * 560, 520 + i * 760, eo(ramp(-0.1, 1.9, u))) : lerp(W + 300, 660, eo(ramp(-0.1, 1.6, u)));
          const y = 812 + Math.sin(u * 3.2 + i) * 10, rot = Math.sin(u * 2.6 + i) * 5 * DEG;
          back.push(() => {
            if (f.m === 's:submarine') for (let b = 0; b < 7; b++) { const q = (u * 0.9 + b / 7) % 1; g.strokeStyle = `rgba(255,255,255,${0.7 * (1 - q)})`; g.lineWidth = 4; g.beginPath(); g.arc(x + 180 + b * 9, y - 40 - q * 200, 8 + b * 2, 0, TAU); g.stroke(); }
            put(s, x, y, i ? 0.8 : 1, rot, 1, f.m !== 's:submarine' && dir < 0);
          });
        }
        break;
      }
      case 'run': {
        const slow = f.slow ? 0.18 : 1;
        for (let i = 0; i < n; i++) {
          const v = (430 + r() * 160) * slow, x = -260 + i * (f.slow ? 600 : 420) + v * (u + 0.4), gal = Math.abs(Math.sin(u * (f.slow ? 3 : 9) + i)) * (f.slow ? 4 : 30);
          const y = 818 - gal, flip = !/1f3ce/.test(f.m);
          back.push(() => put(s, x, y, 1 - i * 0.06, Math.sin(u * 9 + i) * (f.slow ? 1 : 4) * DEG, 1, flip));
        }
        break;
      }
      case 'orbit': {
        for (let i = 0; i < n; i++) {
          const a = (i / n) * TAU + u * 1.15, grow = eo(ramp(-0.05, 0.4, u)), rx = 470 * grow, ry = 170 * grow;
          const x = (cl.px || CX) + Math.cos(a) * rx, y = (cl.py || CY) + 30 + Math.sin(a) * ry, depth = Math.sin(a), k = 0.78 + 0.32 * (depth + 1) / 2;
          (depth > 0 ? front : back).push(() => put(s, x, y, k, f.m === 'e:2699' ? u * (i % 2 ? 2 : -2) : Math.cos(a) * 12 * DEG));
        }
        break;
      }
      case 'hop': {
        for (let i = 0; i < n; i++) {
          const ph = (u + 0.5 + i * 0.17) / 0.5, hop = Math.floor(ph), q = ph - hop;
          const x = -200 + i * 380 + (hop + eio(q)) * 240, y = 820 - Math.sin(q * PI) * 170, sq = q < 0.08 || q > 0.92 ? 0.82 : 1;
          if (x > W + 200) continue;
          back.push(() => put(s, x, y, 1 - i * 0.05, (q - 0.5) * -0.4, 1, true, sq));
        }
        break;
      }
      case 'stack': {
        const xs = 560, base = 800, hs = [270, 200, 165, 150];
        let y = base;
        cl.band && cl.band.forEach((b, i) => {
          const land = 0.08 + i * 0.22, q = u - land, drop = q < 0 ? -1400 : -Math.max(0, 1 - eob(clamp(q / 0.25), 2.4)) * 900;
          const yy = y + drop, sq = q > 0 && q < 0.12 ? 1 - 0.12 * Math.sin((q / 0.12) * PI) : 1;
          back.push(() => put(b, xs + (i % 2 ? 16 : -10), yy - (b ? b.ay * 0.4 : 0), 1, Math.sin(u * 3 + i) * 2 * DEG, q < 0 ? 0 : 1, true, sq));
          y -= hs[i];
        });
        break;
      }
      default: break;
    }
  }
  function smoke(x, y, q) {
    for (let j = 0; j < 5; j++) {
      const a = (1 - q / 0.7) * 0.8, rr = 20 + q * 140 + j * 12;
      g.fillStyle = `rgba(255,255,255,${a * (0.6 - j * 0.08)})`;
      g.beginPath(); g.arc(x + (j - 2) * 22 * (q * 4), y - j * 10 - q * 60, rr, 0, TAU); g.fill();
    }
    if (q < 0.1) { g.fillStyle = 'rgba(255,220,120,0.9)'; g.beginPath(); g.arc(x, y + 20, 50 * (1 - q * 8), 0, TAU); g.fill(); }
  }
  function burst(x, y, u) {
    g.save(); g.translate(x, y); g.strokeStyle = `rgba(255,255,255,${0.7 * (1 - u / 0.7)})`; g.lineWidth = 10; g.lineCap = 'round';
    for (let i = 0; i < 18; i++) { const a = (i / 18) * TAU, r0 = 260 + u * 600, r1 = r0 + 120; g.beginPath(); g.moveTo(Math.cos(a) * r0, Math.sin(a) * r0); g.lineTo(Math.cos(a) * r1, Math.sin(a) * r1); g.stroke(); }
    g.restore();
  }
  function beam(x, y, u) {
    g.save(); g.globalCompositeOperation = 'lighter'; g.translate(x, y); g.rotate(Math.sin(u * 1.4) * 0.9);
    const bg = g.createLinearGradient(0, 0, 1100, 0); bg.addColorStop(0, 'rgba(255,240,170,0.55)'); bg.addColorStop(1, 'rgba(255,240,170,0)');
    g.fillStyle = bg; g.beginPath(); g.moveTo(0, 0); g.lineTo(1100, -170); g.lineTo(1100, 170); g.closePath(); g.fill();
    g.rotate(PI); g.beginPath(); g.moveTo(0, 0); g.lineTo(1100, -170); g.lineTo(1100, 170); g.closePath(); g.fill();
    g.restore();
  }
  // Bavaria's lozenges: a band of them across, unfolding from the middle
  function rauten(u) {
    const k = eo(ramp(-0.1, 0.35, u)), hw = 1000 * k;
    g.save(); g.translate(CX, CY + 10); g.rotate(-14 * DEG);
    g.beginPath(); g.rect(-hw, -150, 2 * hw, 300); g.clip();
    g.fillStyle = '#FFFFFF'; g.fillRect(-1000, -150, 2000, 300);
    g.fillStyle = '#0066B2';
    const d = 64;
    for (let y = -150 - d; y < 150 + d; y += d) for (let x = -1100; x < 1100; x += d * 1.6) {
      const ox = ((y / d) % 2 ? d * 0.8 : 0) + (u * 40) % (d * 1.6);
      g.beginPath(); g.moveTo(x + ox, y); g.lineTo(x + ox + d * 0.8, y + d / 2); g.lineTo(x + ox, y + d); g.lineTo(x + ox - d * 0.8, y + d / 2); g.closePath(); g.fill();
    }
    g.restore();
    g.save(); g.translate(CX, CY + 10); g.rotate(-14 * DEG); g.strokeStyle = OUTLINE; g.lineWidth = 10; g.strokeRect(-hw, -150, 2 * hw, 300); g.restore();
  }

  // ---- a club's scene at u seconds from its downbeat (below 0: as the wipe brings it in) ----------------------------
  function layout(cl) {
    const roar = cl.fx.some((f) => f.a === 'roar' || f.a === 'stack' || f.side);
    cl.px = roar ? 1290 : CX; cl.py = CY;
  }
  function scene(i, u) {
    const cl = S.clubs[i]; layout(cl);
    background(cl, u);
    back.length = 0; front.length = 0;
    const wv = cl.fx.find((f) => f.m === 's:waves');
    cl.fx.forEach((f, j) => act(cl, f, u, j));
    if (wv) waves(wv.tint || '#0B2A55', u, false);
    for (const fn of back) fn();
    // the crest: in on the beat, a ring of light out from it, a small lift on every beat
    if (u >= 0 && cl.crest) {
      const pop = eob(ramp(0, 0.3, u), 2.3), beat = 1 + 0.03 * Math.exp(-((u * 2) % 1) * 6) * (u > 0.4 ? 1 : 0);
      if (u < 0.45) { g.save(); g.strokeStyle = `rgba(255,255,255,${0.85 * (1 - u / 0.45)})`; g.lineWidth = 16 * (1 - u / 0.45); g.beginPath(); g.arc(cl.px, cl.py, 150 + u * 640, 0, TAU); g.stroke(); g.restore(); }
      put(cl.crest, cl.px, cl.py, (0.3 + 0.7 * pop) * beat, (1 - eo(ramp(0, 0.45, u))) * -14 * DEG);
    }
    for (const fn of front) fn();
    if (wv) waves(wv.tint || '#0B2A55', u, true);
    nameplate(cl, u);
    if (u >= 0 && u < 0.12) { g.fillStyle = `rgba(255,255,255,${0.22 * (1 - u / 0.12)})`; g.fillRect(0, 0, W, H); }
  }
  function nameplate(cl, u) {
    const k = eo(ramp(0.16, 0.5, u)); if (k <= 0) return;
    const dark = light(cl.ink || '#FFFFFF') < 0.5;
    g.save(); g.globalAlpha = k; g.translate(0, (1 - k) * 50);
    g.font = '800 92px BarlowC'; g.letterSpacing = '4px'; g.textAlign = 'center'; g.textBaseline = 'alphabetic';
    let size = 92; while (g.measureText(cl.name).width > 1560 && size > 56) { size -= 4; g.font = `800 ${size}px BarlowC`; }
    const tw = g.measureText(cl.name).width;
    g.lineJoin = 'round'; g.lineWidth = 14; g.strokeStyle = dark ? 'rgba(255,255,255,0.8)' : 'rgba(10,8,20,0.55)'; g.strokeText(cl.name, CX, NAME_Y);
    g.fillStyle = cl.ink || '#FFFFFF'; g.fillText(cl.name, CX, NAME_Y);
    const lw = tw * 0.62 * eo(ramp(0.3, 0.62, u));
    g.fillStyle = cl.accent || '#FFFFFF'; g.fillRect(CX - lw / 2, NAME_Y + 20, lw, 9);
    g.restore();
  }

  // ---- the opening and the close -----------------------------------------------------------------------------------
  function dark(u) {
    radial('#16224D', '#03050F', CX, 470); rays(CX, 470, u, 0.035); vignette(0.55);
  }
  function markAt(x, y, maxW, maxH, alpha, shine) {
    if (!S.mark || alpha <= 0) return;
    const k = Math.min(maxW / S.mark.width, maxH / S.mark.height), w = S.mark.width * k, h = S.mark.height * k;
    g.save(); g.globalAlpha *= alpha; g.drawImage(S.mark, x - w / 2, y - h / 2, w, h); g.restore();
    if (shine > 0 && shine < 1) {
      const c = mk(w, h), x2 = c.getContext('2d'); x2.drawImage(S.mark, 0, 0, w, h); x2.globalCompositeOperation = 'source-atop';
      const sx = -w * 0.4 + shine * w * 1.8, gr = x2.createLinearGradient(sx - 120, 0, sx + 120, h);
      gr.addColorStop(0, 'rgba(255,255,255,0)'); gr.addColorStop(0.5, 'rgba(255,248,210,1)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
      x2.fillStyle = gr; x2.fillRect(0, 0, w, h);
      g.save(); g.globalAlpha *= alpha * 0.85; g.globalCompositeOperation = 'lighter'; g.drawImage(c, x - w / 2, y - h / 2); g.restore();
    }
  }
  function seasonText(x, y, size, alpha, glow) {
    g.save(); g.globalAlpha *= alpha; g.font = `900 ${size}px Outfit`; g.textAlign = 'center'; g.textBaseline = 'middle'; g.letterSpacing = '2px';
    g.shadowColor = `rgba(220,255,90,${0.85 * glow})`; g.shadowBlur = size * 0.32;
    const gr = g.createLinearGradient(0, y - size / 2, 0, y + size / 2); gr.addColorStop(0, '#FFFFFF'); gr.addColorStop(1, '#D9DEE8');
    g.fillStyle = gr; g.fillText(cfg.season || '', x, y); g.shadowBlur = size * 0.12; g.fillText(cfg.season || '', x, y);
    g.restore();
  }
  function opening(t) {
    const zoom = 1 + 0.28 * ei(ramp(3.5, 4.0, t)), j = rng(Math.floor(t * 30) + 1), sh = t > 2 && t < 2.4 ? (1 - (t - 2) / 0.4) * 22 : 0;
    g.save(); g.translate(CX + (j() - 0.5) * sh, 540 + (j() - 0.5) * sh); g.scale(zoom, zoom); g.translate(-CX, -540);
    dark(t);
    const mk1 = eo(ramp(0.15, 1.2, t));
    markAt(CX, lerp(450, 400, eio(ramp(1.8, 2.2, t))) + (1 - mk1) * 30, 900, 300, mk1, ramp(0.9, 1.8, t));
    if (t >= 2.0) {
      const k = eob(ramp(2.0, 2.28, t), 1.6), s0 = lerp(1.8, 1, k);
      g.save(); g.translate(CX, 790); g.scale(s0, s0); seasonText(0, 0, 200, clamp((t - 2.0) / 0.06), 1); g.restore();
      if (t < 2.5) { g.save(); g.strokeStyle = `rgba(230,255,120,${0.8 * (1 - (t - 2) / 0.5)})`; g.lineWidth = 14; g.beginPath(); g.ellipse(CX, 790, 300 + (t - 2) * 1500, 90 + (t - 2) * 450, 0, 0, TAU); g.stroke(); g.restore(); }
    }
    g.restore();
    const fl = ramp(3.78, 4.0, t); if (fl > 0) { g.fillStyle = `rgba(255,255,255,${fl})`; g.fillRect(0, 0, W, H); }
  }
  function closing(f) {
    dark(f);
    const cols = N > 18 ? 5 : 6, rows = Math.ceil(N / cols), cw = 230, ch = 168;
    const z = 1 + 0.05 * eio(ramp(2.2, 3.8, f));
    g.save(); g.translate(CX, 560); g.scale(z, z); g.translate(-CX, -560);
    for (let i = 0; i < N; i++) {
      const cl = S.clubs[i], c = i % cols, rr = Math.floor(i / cols);
      const tx = CX + (c - (cols - 1) / 2) * cw, ty = 560 + (rr - (rows - 1) / 2) * ch;
      const land = 0.02 + i * 0.033, k = eob(ramp(land, land + 0.34, f), 1.8);
      const y = lerp(H + 220, ty, eo(ramp(land, land + 0.3, f)));
      const wave = Math.exp(-Math.pow((f - 1.7 - (c + rr) * 0.07) / 0.09, 2)) * 0.16;
      put(cl.mini, tx, y - wave * 120, k * (1 + wave), 0);
    }
    g.restore();
    markAt(CX, 150, 620, 150, eo(ramp(0.3, 0.8, f)), ramp(1.2, 2.0, f));
    seasonText(CX, 985, 104, eo(ramp(0.5, 0.95, f)), 0.8);
    const out = ramp(3.4, 3.95, f); if (out > 0) { g.fillStyle = `rgba(8,3,16,${out})`; g.fillRect(0, 0, W, H); }
  }

  // ---- the frame -----------------------------------------------------------------------------------------------
  // the league's mark and the season in the top corners, through the clubs
  function corner(a) {
    if (a <= 0) return;
    g.save(); g.globalAlpha = a;
    g.fillStyle = 'rgba(8,10,24,0.42)'; g.beginPath(); g.roundRect(54, 44, 300, 84, 42); g.fill();
    markAt(204, 86, 250, 54, 1, 0);
    g.beginPath(); g.roundRect(W - 274, 44, 220, 84, 42); g.fill();
    g.font = '800 40px Outfit'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillStyle = '#FFFFFF'; g.fillText(cfg.season || '', W - 164, 88);
    g.restore();
  }
  function dots(t) {
    g.save(); g.globalCompositeOperation = 'lighter';
    for (const d of S.dots) {
      const y = ((d.y - t * d.v) % (H + 200) + (H + 200)) % (H + 200) - 100, x = d.x + Math.sin(t * 0.7 + d.p) * 18;
      const a = d.a * (0.8 + 0.2 * Math.sin(t * 1.4 + d.p * 3));
      const gr = g.createRadialGradient(x, y, 0, x, y, d.r * 3.2); gr.addColorStop(0, `rgba(255,236,150,${a})`); gr.addColorStop(1, 'rgba(255,220,120,0)');
      g.fillStyle = gr; g.fillRect(x - d.r * 3.2, y - d.r * 3.2, d.r * 6.4, d.r * 6.4);
    }
    g.restore();
  }
  // the wipe that brings scene b in over a: a slanted edge from left to right, white with the club's colour behind it
  function wipe(drawA, drawB, p, col) {
    drawA();
    const ex = (y) => lerp(-420, W + 420, eio(p)) + (y - H / 2) * 0.32;
    g.save(); g.beginPath(); g.moveTo(-500, 0); g.lineTo(ex(0), 0); g.lineTo(ex(H), H); g.lineTo(-500, H); g.closePath(); g.clip(); drawB(); g.restore();
    g.save(); g.fillStyle = col; g.beginPath(); g.moveTo(ex(0), 0); g.lineTo(ex(0) + 46, 0); g.lineTo(ex(H) + 46, H); g.lineTo(ex(H), H); g.closePath(); g.fill();
    g.strokeStyle = '#FFFFFF'; g.lineWidth = 10; g.beginPath(); g.moveTo(ex(0), 0); g.lineTo(ex(H), H); g.stroke(); g.restore();
  }
  function draw(t) {
    g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
    const T0 = OPEN, TF = OPEN + N * BAR;
    if (t < T0) {
      opening(t);
    } else if (t < TF) {
      const i = Math.min(N - 1, Math.floor((t - T0) / BAR)), u = t - T0 - i * BAR, nextAt = T0 + (i + 1) * BAR;
      if (t > nextAt - WIPE && i + 1 < N) wipe(() => scene(i, u), () => scene(i + 1, t - nextAt), (t - (nextAt - WIPE)) / WIPE, S.clubs[i + 1].accent || '#FFFFFF');
      else if (t > nextAt - WIPE) wipe(() => scene(i, u), () => closing(t - TF), (t - (nextAt - WIPE)) / WIPE, '#FFFFFF');
      else scene(i, u);
      corner(eo(ramp(T0 + 0.1, T0 + 0.4, t)) * (1 - ramp(TF - 0.2, TF, t)));
      if (i === 0 && u < 0.16) { g.fillStyle = `rgba(255,255,255,${1 - u / 0.16})`; g.fillRect(0, 0, W, H); }
    } else {
      closing(t - TF);
    }
    if (t < TF + 3.5) dots(t);
  }
  window.SEASON = { init, draw, get LEN() { return LEN; } };
})();
