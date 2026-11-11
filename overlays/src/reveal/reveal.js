// The competition intros, 20 seconds each, drawn on a 2D canvas (1920 x 1080) and rendered to video ahead of time
// (tools/reveal_render.js): particles burst, gather into the competition's logo as a glowing outline, a light streak
// circles it, the logo stands in rays, the name builds beside it, a clean white frame, then the particles gather into
// the Whitebrook Road Sports wordmark with MATCHDAY (RACE DAY, FIGHT NIGHT) and the fixture beneath.
// In the style of the Premier League intro the family made; the Premier League's own video takes the first ten seconds
// of its intro (mode 'tail': only the Whitebrook Road part, ten seconds, coming out of its white end frame).
// Deterministic: draw(t) draws second t from nothing carried between frames.
//   REVEAL.init(cfg) -> Promise; REVEAL.draw(t); REVEAL.LEN
// cfg: {mode: 'full'|'tail', name, logo (data URL, for dark), logoLight (data URL, for the white frame), emblem: 'logo'|
//       'football'|'starball'|'gloves', c1, c2 (the particles' colours, #rrggbb), bg (the darkness behind, #rrggbb), ink (the name on
//       white), title ('MATCHDAY'), fixture ('ARSENAL v EVERTON', '' for none: the app writes it over the video)}
(function () {
  const W = 1920, H = 1080;
  let C, g, cfg, LEN = 20, BR0 = 11.4;
  const S = {};          // everything made once: canvases, particles

  function rng(seed) { let s = (seed >>> 0) || 1; return () => { s ^= s << 13; s >>>= 0; s ^= s >> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; }; }
  const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
  const ss = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
  const eio = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
  const eo = (t) => 1 - Math.pow(1 - clamp(t), 3);
  const lerp = (a, b, t) => a + (b - a) * t;
  function mk(w, h) { const c = document.createElement('canvas'); c.width = Math.max(1, Math.ceil(w)); c.height = Math.max(1, Math.ceil(h)); return c; }
  const rgb = (h) => { const n = parseInt(h.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255].join(','); };
  function img(src) { return new Promise((res) => { if (!src) return res(null); const i = new Image(); i.onload = () => res(i); i.onerror = () => res(null); i.src = src; }); }

  // ---- pieces made once -----------------------------------------------------------------------------------------
  function sprite(col, size) {
    const c = mk(size, size), x = c.getContext('2d'), r = x.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
    r.addColorStop(0, 'rgba(255,255,255,1)'); r.addColorStop(0.16, `rgba(${col},0.95)`); r.addColorStop(0.42, `rgba(${col},0.3)`); r.addColorStop(1, `rgba(${col},0)`);
    x.fillStyle = r; x.fillRect(0, 0, size, size); return c;
  }
  function blob(col, size, a) {
    const c = mk(size, size), x = c.getContext('2d'), r = x.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
    r.addColorStop(0, `rgba(${col},${a})`); r.addColorStop(0.5, `rgba(${col},${a * 0.35})`); r.addColorStop(1, `rgba(${col},0)`);
    x.fillStyle = r; x.fillRect(0, 0, size, size); return c;
  }
  // a picture fitted into w x h, centred, with room around it
  function fitted(im, w, h, pad) {
    const k = Math.min(w / im.width, h / im.height), c = mk(im.width * k + pad * 2, im.height * k + pad * 2);
    c.getContext('2d').drawImage(im, pad, pad, im.width * k, im.height * k); return c;
  }
  function tinted(src, col, blur) {
    const c = mk(src.width, src.height), x = c.getContext('2d');
    if (blur) x.filter = `blur(${blur}px)`;
    x.drawImage(src, 0, 0); x.filter = 'none';
    x.globalCompositeOperation = 'source-in'; x.fillStyle = col; x.fillRect(0, 0, c.width, c.height);
    return c;
  }
  function reflection(src) {
    const c = mk(src.width, src.height), x = c.getContext('2d');
    x.translate(0, src.height); x.scale(1, -1); x.drawImage(src, 0, 0); x.setTransform(1, 0, 0, 1, 0, 0);
    x.globalCompositeOperation = 'destination-in';
    const gr = x.createLinearGradient(0, 0, 0, src.height); gr.addColorStop(0, 'rgba(0,0,0,0.38)'); gr.addColorStop(0.45, 'rgba(0,0,0,0)');
    x.fillStyle = gr; x.fillRect(0, 0, c.width, c.height); return c;
  }
  // points on a picture's outline (and some inside), centred on it: the particles' places
  function targets(src, n, seed, inner) {
    const w = src.width, h = src.height, d = src.getContext('2d').getImageData(0, 0, w, h).data;
    const A = (x, y) => (x < 0 || y < 0 || x >= w || y >= h ? 0 : d[(y * w + x) * 4 + 3]);
    const edge = [], fill = [];
    for (let y = 0; y < h; y += 1) for (let x = 0; x < w; x += 1) {
      if (A(x, y) < 140) continue;
      if (A(x - 2, y) < 140 || A(x + 2, y) < 140 || A(x, y - 2) < 140 || A(x, y + 2) < 140) edge.push([x - w / 2, y - h / 2]);
      else if ((x * 7 + y * 13) % 23 === 0) fill.push([x - w / 2, y - h / 2]);
    }
    const r = rng(seed), out = [];
    const ne = Math.round(n * (1 - inner));
    for (let i = 0; i < n; i++) {
      const from = i < ne || !fill.length ? edge : fill;
      out.push(from.length ? from[Math.floor(r() * from.length)] : [0, 0]);
    }
    return out;
  }
  function swarm(tg, seed, spread) {
    const r = rng(seed);
    return tg.map((p) => {
      const a = r() * Math.PI * 2, el = Math.acos(2 * r() - 1);
      return { tx: p[0], ty: p[1], dx: Math.cos(a) * Math.sin(el), dy: Math.sin(a) * Math.sin(el) * 0.72, r0: spread * (0.35 + 0.65 * Math.pow(r(), 0.4)),
        delay: r() * 0.65, size: 1.0 + Math.pow(r(), 3) * 3.0, spin: (r() - 0.5) * 2.4, c: r() < 0.62 ? 0 : 1, tw: r() * 6.28, keep: r() };
    });
  }

  // the stand-ins where a competition has no logo: a football, boxing gloves
  function football(size) {
    const c = mk(size, size), x = c.getContext('2d'), R = size * 0.46, cx = size / 2, cy = size / 2;
    const poly = (px, py, pr, rot) => { x.beginPath(); for (let i = 0; i < 5; i++) { const a = rot + i * Math.PI * 2 / 5; x.lineTo(px + Math.cos(a) * pr, py + Math.sin(a) * pr); } x.closePath(); };
    x.save(); x.beginPath(); x.arc(cx, cy, R, 0, Math.PI * 2); x.fillStyle = '#ffffff'; x.fill(); x.clip();
    const rc = R * 0.27, top = -Math.PI / 2;
    x.fillStyle = '#111318'; poly(cx, cy, rc, top); x.fill();
    x.strokeStyle = '#111318'; x.lineWidth = size * 0.011; x.lineCap = 'round';
    for (let i = 0; i < 5; i++) {
      const a = top + i * Math.PI * 2 / 5, m = a + Math.PI / 5;
      // the seam out from each corner, and the patch between each pair of seams near the rim
      x.beginPath(); x.moveTo(cx + Math.cos(a) * rc, cy + Math.sin(a) * rc); x.lineTo(cx + Math.cos(a) * R * 0.62, cy + Math.sin(a) * R * 0.62); x.stroke();
      poly(cx + Math.cos(m) * R * 0.86, cy + Math.sin(m) * R * 0.86, R * 0.27, m + Math.PI); x.fill();
      const l = a - Math.PI / 5 + Math.PI / 5;
      x.beginPath(); x.moveTo(cx + Math.cos(l) * R * 0.62, cy + Math.sin(l) * R * 0.62);
      x.lineTo(cx + Math.cos(l - 0.36) * R * 0.74, cy + Math.sin(l - 0.36) * R * 0.74); x.moveTo(cx + Math.cos(l) * R * 0.62, cy + Math.sin(l) * R * 0.62);
      x.lineTo(cx + Math.cos(l + 0.36) * R * 0.74, cy + Math.sin(l + 0.36) * R * 0.74); x.stroke();
    }
    // its roundness: light from the top left, shade to the bottom right
    const sh = x.createRadialGradient(cx - R * 0.35, cy - R * 0.4, R * 0.1, cx, cy, R * 1.05);
    sh.addColorStop(0, 'rgba(255,255,255,0.35)'); sh.addColorStop(0.55, 'rgba(255,255,255,0)'); sh.addColorStop(1, 'rgba(0,0,0,0.38)');
    x.fillStyle = sh; x.fillRect(0, 0, size, size);
    x.restore();
    return c;
  }
  // a match ball in the Champions League's style: white, with navy stars across it
  function starball(size) {
    const c = mk(size, size), x = c.getContext('2d'), R = size * 0.46, cx = size / 2, cy = size / 2;
    const star = (px, py, r, rot, sq) => {
      x.save(); x.translate(px, py); x.rotate(rot); x.scale(1, sq);
      x.beginPath();
      for (let i = 0; i < 10; i++) { const a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 ? r * 0.42 : r; x.lineTo(Math.cos(a) * rr, Math.sin(a) * rr); }
      x.closePath();
      const gr = x.createLinearGradient(-r, -r, r, r); gr.addColorStop(0, '#1d3a8f'); gr.addColorStop(1, '#0a1a4d');
      x.fillStyle = gr; x.fill();
      x.lineWidth = r * 0.07; x.strokeStyle = 'rgba(205,215,235,0.95)'; x.stroke();
      x.restore();
    };
    x.save(); x.beginPath(); x.arc(cx, cy, R, 0, Math.PI * 2); x.fillStyle = '#f7f8fb'; x.fill(); x.clip();
    // the stars: one in the middle, six round it, foreshortened towards the rim
    star(cx, cy, R * 0.34, 0.12, 1);
    for (let i = 0; i < 6; i++) {
      const a = i * Math.PI / 3 + 0.4, d = R * 0.78, k = 0.62;
      star(cx + Math.cos(a) * d, cy + Math.sin(a) * d, R * 0.3, a + Math.PI / 2, k);
    }
    // seams between them
    x.strokeStyle = 'rgba(150,160,185,0.55)'; x.lineWidth = size * 0.006;
    for (let i = 0; i < 6; i++) { const a = i * Math.PI / 3 + 0.4 + Math.PI / 6; x.beginPath(); x.moveTo(cx + Math.cos(a) * R * 0.36, cy + Math.sin(a) * R * 0.36); x.lineTo(cx + Math.cos(a) * R, cy + Math.sin(a) * R); x.stroke(); }
    const sh = x.createRadialGradient(cx - R * 0.35, cy - R * 0.4, R * 0.1, cx, cy, R * 1.05);
    sh.addColorStop(0, 'rgba(255,255,255,0.4)'); sh.addColorStop(0.55, 'rgba(255,255,255,0)'); sh.addColorStop(1, 'rgba(10,20,50,0.42)');
    x.fillStyle = sh; x.fillRect(0, 0, size, size);
    x.restore();
    x.beginPath(); x.arc(cx, cy, R, 0, Math.PI * 2); x.lineWidth = size * 0.01; x.strokeStyle = 'rgba(20,30,60,0.6)'; x.stroke();
    return c;
  }
  function gloves(size) {
    const c = mk(size, size), x = c.getContext('2d');
    const glove = (flip) => {
      x.save(); x.translate(size / 2, size / 2); x.scale(flip ? -1 : 1, 1); x.rotate(-0.32); x.translate(-size * 0.2, -size * 0.06);
      const k = size / 520;
      const red = x.createLinearGradient(-140 * k, -200 * k, 120 * k, 160 * k); red.addColorStop(0, '#ff5a4f'); red.addColorStop(1, '#a8100c');
      x.fillStyle = red;
      x.beginPath(); x.ellipse(0, -60 * k, 118 * k, 140 * k, 0, 0, Math.PI * 2); x.fill();
      x.beginPath(); x.ellipse(-104 * k, 20 * k, 46 * k, 74 * k, -0.4, 0, Math.PI * 2); x.fill();
      x.beginPath(); x.roundRect(-78 * k, 50 * k, 150 * k, 120 * k, 26 * k); x.fill();
      x.fillStyle = '#ffffff'; x.beginPath(); x.roundRect(-82 * k, 112 * k, 158 * k, 22 * k, 8 * k); x.fill();
      x.fillStyle = 'rgba(255,255,255,0.35)'; x.beginPath(); x.ellipse(-30 * k, -120 * k, 40 * k, 22 * k, -0.5, 0, Math.PI * 2); x.fill();
      x.strokeStyle = 'rgba(90,0,0,0.55)'; x.lineWidth = 7 * k; x.lineCap = 'round';
      x.beginPath(); x.arc(-10 * k, 10 * k, 92 * k, -2.6, -0.5); x.stroke();
      x.beginPath(); x.moveTo(-58 * k, 4 * k); x.quadraticCurveTo(-70 * k, 40 * k, -50 * k, 62 * k); x.stroke();
      x.strokeStyle = 'rgba(255,255,255,0.9)'; x.lineWidth = 5 * k;
      for (let j = 0; j < 3; j++) { x.beginPath(); x.moveTo(-20 * k, (70 + j * 14) * k); x.lineTo(20 * k, (78 + j * 14) * k); x.moveTo(20 * k, (70 + j * 14) * k); x.lineTo(-20 * k, (78 + j * 14) * k); x.stroke(); }
      x.restore();
    };
    glove(false); glove(true);
    return c;
  }

  // the Whitebrook Road Sports wordmark (as the line-ups, the pop-up and the matchday card draw it)
  const VIOLET = '#6D28F2', CORAL = '#FF6B6B', RED = '#E10A1E', RED_DEEP = '#B0061A';
  const HOUSE = 'M31 75 L31 50 L54 31 L77 50 L77 75 Z', PLAY = 'M49 53 L49 69 L63 61 Z';
  function font(x, w, s, ls) { x.font = `${w} ${s}px Outfit`; x.letterSpacing = `${ls || 0}px`; }
  function tile(x, X, Y, s) {
    x.save();
    const gr = x.createLinearGradient(X, Y, X + s, Y + s); gr.addColorStop(0, VIOLET); gr.addColorStop(1, CORAL);
    x.beginPath(); x.roundRect(X, Y, s, s, s * 0.24); x.fillStyle = gr; x.fill();
    const rg = x.createRadialGradient(X + 0.28 * s, Y + 0.18 * s, 0, X + 0.28 * s, Y + 0.18 * s, 0.6 * s);
    rg.addColorStop(0, 'rgba(255,255,255,0.2)'); rg.addColorStop(1, 'rgba(255,255,255,0)'); x.fillStyle = rg; x.fill();
    const wd = s * 0.62, k = wd / 51, mx = X + (s - wd) / 2, my = Y + (s - 49 * k) / 2;
    x.translate(mx - 28.5 * k, my - 28.5 * k); x.scale(k, k); x.lineJoin = 'round';
    const Hp = new Path2D(HOUSE), Pp = new Path2D(PLAY);
    x.fillStyle = '#fff'; x.strokeStyle = '#fff'; x.lineWidth = 5; x.fill(Hp); x.stroke(Hp);
    const gx0 = (X - mx) / k + 28.5, gy0 = (Y - my) / k + 28.5;
    const cg = x.createLinearGradient(gx0, gy0, gx0 + s / k, gy0 + s / k); cg.addColorStop(0, VIOLET); cg.addColorStop(1, CORAL);
    x.fillStyle = cg; x.strokeStyle = cg; x.lineWidth = 2.6; x.fill(Pp); x.stroke(Pp);
    x.restore();
  }
  function wordmark(Sz) {
    const p = mk(10, 10).getContext('2d');
    font(p, 800, Sz, 0); const w1 = p.measureText('whitebrook road').width;
    font(p, 800, Sz * 0.9, 0); const w2 = p.measureText('sports').width;
    const ts = Sz * 1.18, gap = Sz * 0.3, pillW = w2 + Sz * 0.52, pillH = Sz * 1.14, pad = Sz * 0.3;
    const c = mk(ts + gap + w1 + Sz * 0.3 + pillW + pad * 2, Math.max(ts, pillH) + pad * 2), x = c.getContext('2d'), cy = c.height / 2;
    tile(x, pad, cy - ts / 2, ts);
    const X = pad + ts + gap, base = cy + Sz * 0.33;
    font(x, 800, Sz, 0); x.textBaseline = 'alphabetic'; x.fillStyle = '#fff'; x.fillText('whitebrook road', X, base);
    const tx = X + w1 + Sz * 0.3, rg = x.createLinearGradient(0, cy - pillH / 2, 0, cy + pillH / 2); rg.addColorStop(0, RED); rg.addColorStop(1, RED_DEEP);
    x.beginPath(); x.roundRect(tx, cy - pillH / 2, pillW, pillH, Sz * 0.14); x.fillStyle = rg; x.fill();
    font(x, 800, Sz * 0.9, 0); x.fillStyle = '#fff'; x.fillText('sports', tx + Sz * 0.26, base - Sz * 0.03);
    return c;
  }
  function text(s, w, size, ls, col, pad) {
    const p = mk(10, 10).getContext('2d'); font(p, w, size, ls); const tw = p.measureText(s).width;
    const c = mk(tw + pad * 2, size * 1.5 + pad * 2), x = c.getContext('2d');
    font(x, w, size, ls); x.textBaseline = 'middle'; x.fillStyle = col; x.fillText(s, pad, c.height / 2 + size * 0.04);
    return c;
  }

  async function init(c) {
    cfg = Object.assign({ mode: 'full', name: '', emblem: 'logo', c1: '#a855f7', c2: '#e9d5ff', bg: '#12001f', ink: '#37003c', title: 'MATCHDAY', fixture: '' }, c || {});
    LEN = cfg.mode === 'tail' ? 10 : 20;
    BR0 = cfg.mode === 'tail' ? 0 : 11.4;
    C = document.getElementById('reveal') || document.body.appendChild(Object.assign(document.createElement('canvas'), { id: 'reveal' }));
    C.width = W; C.height = H; g = C.getContext('2d');
    if (document.fonts && document.fonts.load) await Promise.all(['800 150px Outfit', '700 74px Outfit', '600 40px Outfit'].map((f) => document.fonts.load(f))).catch(() => {});
    const c1 = rgb(cfg.c1), c2 = rgb(cfg.c2), bg = rgb(cfg.bg);
    S.sp = [sprite(c1, 64), sprite(c2, 64)]; S.flare = sprite(c2, 256); S.c1 = c1; S.c2 = c2;
    S.blobs = [blob(c1, 900, 0.22), blob(c2, 700, 0.12), blob(bg, 1200, 0.9)];
    // the darkness behind
    const b = mk(W, H), bx = b.getContext('2d'), rg = bx.createRadialGradient(W / 2, H * 0.48, 0, W / 2, H * 0.48, W * 0.62);
    const [r0, g0, b0] = cfg.bg.match(/\w\w/g).map((h) => parseInt(h, 16));
    rg.addColorStop(0, `rgb(${Math.min(255, r0 * 2.2 + 8)},${Math.min(255, g0 * 2.2 + 8)},${Math.min(255, b0 * 2.2 + 14)})`); rg.addColorStop(0.55, `rgb(${r0},${g0},${b0})`); rg.addColorStop(1, '#010103');
    bx.fillStyle = rg; bx.fillRect(0, 0, W, H); S.bg = b;
    // the white frame's light
    const wh = mk(W, H), wx = wh.getContext('2d'), wr = wx.createRadialGradient(W / 2, H * 0.45, 0, W / 2, H * 0.45, W * 0.7);
    wr.addColorStop(0, '#ffffff'); wr.addColorStop(0.6, '#eeeef2'); wr.addColorStop(1, '#d4d4dc'); wx.fillStyle = wr; wx.fillRect(0, 0, W, H); S.white = wh;
    if (cfg.mode !== 'tail') {
      // the competition: its logo for the dark and for the white frame (or a stand-in), and its name
      let dark = null, light = null;
      if (cfg.emblem === 'football') dark = light = football(560);
      else if (cfg.emblem === 'starball') dark = light = starball(560);
      else if (cfg.emblem === 'gloves') dark = light = gloves(600);
      else { dark = await img(cfg.logo); light = (await img(cfg.logoLight)) || dark; }
      if (!dark) dark = light = football(560);
      S.logo = fitted(dark, 600, 520, 40); S.logoLight = fitted(light, 600, 520, 40);
      S.glow = tinted(S.logo, `rgba(${c1},1)`, 34); S.depth = tinted(S.logo, 'rgba(0,0,0,0.85)', 0);
      S.refl = reflection(S.logo);
      S.lt = swarm(targets(S.logo, 2300, 7, 0.22), 11, 760);
      // the name: as large as fits beside the logo
      let size = 150; const p = mk(10, 10).getContext('2d');
      for (; size > 70; size -= 6) { font(p, 800, size, 0); if (p.measureText(cfg.name).width + S.logo.width * 0.62 + 60 < 1640) break; }
      S.nameSize = size;
      S.name = text(cfg.name, 800, size, 0, '#ffffff', 30);
      S.nameGlow = tinted(S.name, `rgba(${c1},1)`, 18);
      S.nameInk = text(cfg.name, 800, size, 0, cfg.ink, 30);
      S.nameRefl = reflection(S.name);
      const lw = S.logo.width * 0.62, total = lw + 40 + (S.name.width - 60);
      S.lockX = W / 2 - total / 2 + lw / 2; S.nameX = S.lockX + lw / 2 + 40 - 30 + S.name.width / 2;
    }
    // Whitebrook Road Sports
    S.wm = wordmark(112); S.wmGlow = tinted(S.wm, `rgba(${c1},1)`, 26);
    S.wt = swarm(targets(S.wm, 2000, 21, 0.18), 23, 900);
    S.letters = cfg.title.split('').map((ch) => text(ch, 700, 74, 0, '#ffffff', 16));
    const pr = mk(10, 10).getContext('2d'); font(pr, 700, 74, 0);
    S.adv = cfg.title.split('').map((ch) => pr.measureText(ch).width);
    S.fixture = cfg.fixture ? text(cfg.fixture.toUpperCase(), 600, 40, 5, 'rgba(214,220,240,0.95)', 16) : null;
    const dr = rng(99);
    S.dust = Array.from({ length: 140 }, () => ({ x: dr() * W, y: dr() * H, z: 0.3 + dr() * 0.7, s: 2 + dr() * 6, ph: dr() * 6.28 }));
    return { len: LEN };
  }

  // ---- drawing --------------------------------------------------------------------------------------------------
  function bg(t, k) {
    g.globalCompositeOperation = 'source-over'; g.globalAlpha = 1; g.drawImage(S.bg, 0, 0);
    g.globalCompositeOperation = 'lighter';
    g.globalAlpha = 0.55 * k; g.drawImage(S.blobs[0], W * 0.18 + Math.sin(t * 0.21) * 160 - 450, H * 0.35 + Math.cos(t * 0.17) * 90 - 450);
    g.globalAlpha = 0.5 * k; g.drawImage(S.blobs[1], W * 0.7 + Math.cos(t * 0.19) * 180 - 350, H * 0.55 + Math.sin(t * 0.23) * 80 - 350);
    g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
  }
  function dust(t, k) {
    if (k <= 0) return;
    g.globalCompositeOperation = 'lighter';
    for (const d of S.dust) {
      const x = (d.x + t * 18 * d.z) % W, y = (d.y - t * 26 * d.z + H * 4) % H, a = k * 0.22 * d.z * (0.6 + 0.4 * Math.sin(t * 1.3 + d.ph));
      g.globalAlpha = a; g.drawImage(S.sp[1], x - d.s * 2, y - d.s * 2, d.s * 4, d.s * 4);
    }
    g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
  }
  // the swarm at second u of its flight (0 burst, 1 in place) around (cx, cy) at scale sc
  function particles(P, cx, cy, sc, burstT, flyT, settle, k, sparkle) {
    g.globalCompositeOperation = 'lighter';
    const fly = (p, ft) => eio(clamp((ft - p.delay * 0.5) / 0.67));
    for (const p of P) {
      const b = eo(burstT), f = fly(p, flyT);
      const sx = p.dx * p.r0 * b, sy = p.dy * p.r0 * b;
      const ang = p.spin * (1 - f) * 1.6;
      const fx = lerp(sx, p.tx * sc, f), fy = lerp(sy, p.ty * sc, f);
      const ca = Math.cos(ang), sa = Math.sin(ang);
      let x = cx + fx * ca - fy * sa, y = cy + fx * sa + fy * ca;
      if (settle > 0) { x += Math.sin(p.tw + settle * 3) * 1.2; y += Math.cos(p.tw * 1.3 + settle * 2.6) * 1.2; }
      const tw = 0.65 + 0.35 * Math.sin(p.tw + (burstT + flyT + settle) * 7);
      const vis = sparkle == null ? 1 : (p.keep < sparkle ? 1 : 0);
      const a = k * tw * vis * (0.38 + 0.62 * f); if (a <= 0.01) continue;
      const s = p.size * (1 + 0.5 * (1 - f) * b) * 2.7;
      g.globalAlpha = Math.min(1, a); g.drawImage(S.sp[p.c], x - s, y - s, s * 2, s * 2);
      if (f > 0.02 && f < 0.98) { // a short trail
        const f2 = fly(p, flyT - 0.04), x2 = cx + lerp(sx, p.tx * sc, f2), y2 = cy + lerp(sy, p.ty * sc, f2);
        g.globalAlpha = Math.min(1, a * 0.35); g.drawImage(S.sp[p.c], x2 - s * 0.8, y2 - s * 0.8, s * 1.6, s * 1.6);
      }
    }
    g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
  }
  function ellipsePt(cx, cy, rx, ry, rot, a) {
    const x = Math.cos(a) * rx, y = Math.sin(a) * ry, c = Math.cos(rot), s = Math.sin(rot);
    return [cx + x * c - y * s, cy + x * s + y * c];
  }
  function streak(cx, cy, rx, ry, rot, head, span, k) {
    if (k <= 0) return;
    g.save(); g.globalCompositeOperation = 'lighter'; g.lineCap = 'round';
    const n = 48;
    for (let pass = 0; pass < 2; pass++) {
      for (let i = 0; i < n; i++) {
        const u0 = i / n, u1 = (i + 1) / n;
        const [x0, y0] = ellipsePt(cx, cy, rx, ry, rot, head - span * (1 - u0)), [x1, y1] = ellipsePt(cx, cy, rx, ry, rot, head - span * (1 - u1));
        const a = Math.pow(u1, 1.7) * k;
        g.strokeStyle = pass ? `rgba(255,255,255,${Math.min(1, a)})` : `rgba(${S.c1},${Math.min(1, a * 0.4)})`;
        g.lineWidth = pass ? 1.5 + 4.5 * u1 : 8 + 22 * u1;
        g.beginPath(); g.moveTo(x0, y0); g.lineTo(x1, y1); g.stroke();
      }
    }
    const [hx, hy] = ellipsePt(cx, cy, rx, ry, rot, head);
    g.globalAlpha = Math.min(1, k); g.drawImage(S.flare, hx - 70, hy - 70, 140, 140);
    g.restore();
  }
  function rays(cx, cy, k, t) {
    if (k <= 0) return;
    g.save(); g.globalCompositeOperation = 'lighter';
    for (let i = 0; i < 14; i++) {
      const a = t * 0.12 + i * Math.PI * 2 / 14 + Math.sin(i * 7.3) * 0.12, w = 0.035 + 0.03 * ((i * 5) % 3) / 2, len = 980;
      const gr = g.createRadialGradient(cx, cy, 0, cx, cy, len);
      gr.addColorStop(0, `rgba(${S.c2},${0.22 * k})`); gr.addColorStop(0.5, `rgba(${S.c1},${0.08 * k})`); gr.addColorStop(1, `rgba(${S.c1},0)`);
      g.fillStyle = gr; g.beginPath(); g.moveTo(cx, cy); g.lineTo(cx + Math.cos(a - w) * len, cy + Math.sin(a - w) * len); g.lineTo(cx + Math.cos(a + w) * len, cy + Math.sin(a + w) * len); g.closePath(); g.fill();
    }
    g.restore();
  }
  // a picture drawn centred at (x, y) at scale k
  function put(c, x, y, k, a, op) {
    if (a <= 0) return;
    g.globalAlpha = Math.min(1, a); if (op) g.globalCompositeOperation = op;
    g.drawImage(c, x - c.width * k / 2, y - c.height * k / 2, c.width * k, c.height * k);
    g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
  }
  // a bright band across a picture (its shine), at u from 0 to 1
  function sheen(c, x, y, k, u, a) {
    if (a <= 0 || u <= 0 || u >= 1) return;
    const t = S.sheenC || (S.sheenC = mk(1200, 700)), tx = t.getContext('2d');
    const w = c.width * k, h = c.height * k;
    tx.clearRect(0, 0, t.width, t.height);
    tx.globalCompositeOperation = 'source-over'; tx.drawImage(c, 0, 0, w, h);
    tx.globalCompositeOperation = 'source-in';
    const bx = lerp(-0.3, 1.3, u) * w, gr = tx.createLinearGradient(bx - 90, 0, bx + 90, h * 0.3);
    gr.addColorStop(0, 'rgba(255,255,255,0)'); gr.addColorStop(0.5, `rgba(255,255,255,${0.42 * a})`); gr.addColorStop(1, 'rgba(255,255,255,0)');
    tx.fillStyle = gr; tx.fillRect(0, 0, w, h);
    g.globalCompositeOperation = 'lighter'; g.drawImage(t, 0, 0, w, h, x - w / 2, y - h / 2, w, h); g.globalCompositeOperation = 'source-over';
  }

  // the competition's part: 0 to 11.4 s
  function competition(t) {
    bg(t, 1);
    dust(t, ss(0.5, 3, t));
    const CX = 960, CY = 515;
    // where the logo stands: centre, a slow push in, then left into the lock-up beside the name
    const lock = eio(ss(7.0, 8.4, t)), sc = lerp(lerp(1, 1.07, ss(4, 7.4, t)), 0.62, lock), lx = lerp(CX, S.lockX, lock);
    // the burst: a sphere of light from the middle
    if (t < 1.6) {
      const fl = Math.exp(-t * 3.2) * (t < 0.08 ? t / 0.08 : 1);
      put(S.flare, CX, CY, 3.2 + t * 2, fl, 'lighter');
    }
    rays(lx, CY, ss(3.6, 4.6, t) * (1 - ss(8.6, 9.8, t)) * 0.9, t);
    // the logo itself: its glow, its depth, the logo, its reflection on the floor
    const solid = ss(3.3, 4.3, t);
    if (solid > 0) {
      put(S.glow, lx, CY, sc * 1.04, solid * (0.75 + 0.25 * Math.sin(t * 2.2)), 'lighter');
      for (let i = 6; i >= 1; i--) put(S.depth, lx + i * 1.6 * sc, CY + i * 2.2 * sc, sc, solid * 0.18);
      put(S.logo, lx, CY, sc, solid);
      put(S.refl, lx, CY + S.logo.height * sc * 0.98, sc, solid * 0.5);
      sheen(S.logo, lx, CY, sc, (t - 5.2) / 1.3, solid);
    }
    // the particles: burst, fly into the outline, stay as sparkle
    const burstT = clamp(t / 1.3), flyT = clamp((t - 1.0) / 2.6);
    particles(S.lt, lx, CY, sc, burstT, flyT, Math.max(0, t - 3.6), 1 - 0.75 * ss(4.2, 6.5, t), t > 4.3 ? 0.35 : null);
    // the light streak circling it
    const sk = ss(1.3, 1.7, t) * (1 - ss(3.9, 4.4, t)) + ss(7.2, 7.5, t) * (1 - ss(9.2, 9.8, t));
    const head = t < 6 ? -1.2 + (t - 1.3) * 3.4 : 0.8 + (t - 7.2) * 2.6;
    streak(lx + (t < 6 ? 0 : (S.nameX - S.lockX) * 0.42 * lock), CY + 30, t < 6 ? 470 : 820, t < 6 ? 150 : 170, -0.2, head, 2.6, sk);
    // the name: an outline of light, then filled, a letter at a time from the left
    const nm = ss(7.6, 8.8, t);
    if (nm > 0) {
      const w = S.name.width, h = S.name.height, x0 = S.nameX - w / 2;
      const cut = lerp(0, w, eo(ss(7.6, 8.9, t)));
      g.save(); g.beginPath(); g.rect(x0, 0, cut, H); g.clip();
      put(S.nameGlow, S.nameX, CY, 1, nm * 0.9, 'lighter');
      put(S.name, S.nameX, CY, 1, ss(8.0, 9.0, t));
      put(S.nameRefl, S.nameX, CY + h * 0.9, 1, ss(8.0, 9.0, t) * 0.8);
      g.restore();
      sheen(S.name, S.nameX, CY, 1, (t - 9.0) / 0.8, 1);
    }
    // to white: the clean frame (their end card's), the logo and the name in ink
    const wf = ss(9.8, 10.05, t);
    if (wf > 0) {
      g.globalAlpha = wf; g.drawImage(S.white, 0, 0); g.globalAlpha = 1;
      const settle = 1 + 0.03 * (1 - eo(ss(10.0, 11.0, t)));
      put(S.logoLight, S.lockX, CY, 0.62 * settle, ss(9.95, 10.2, t));
      put(S.nameInk, S.nameX, CY, settle, ss(9.95, 10.2, t));
      // the flash into it
      const fl = Math.exp(-Math.max(0, t - 9.95) * 6) * ss(9.8, 9.95, t);
      g.globalAlpha = fl; g.fillStyle = '#fff'; g.fillRect(0, 0, W, H); g.globalAlpha = 1;
    }
  }

  // the Whitebrook Road Sports part: tb from 0 (coming out of white) to dur
  function brand(tb, dur) {
    bg(tb + 20, 1);
    dust(tb, 0.8);
    const CX = 960, CY = 450;
    // the swarm: bursts from the white, flies into the wordmark
    const burstT = clamp(tb / 0.9), flyT = clamp((tb - 0.7) / 2.3);
    put(S.flare, CX, CY, 4 - tb, Math.exp(-tb * 2.5) * 0.9, 'lighter');
    const solid = ss(2.4, 3.2, tb);
    if (solid > 0) {
      put(S.wmGlow, CX, CY, 1.02, solid * (0.55 + 0.2 * Math.sin(tb * 2)), 'lighter');
      put(S.wm, CX, CY, 1, solid);
      sheen(S.wm, CX, CY, 1, (tb - 4.9) / 1.0, 1);
    }
    particles(S.wt, CX, CY, 1, burstT, flyT, Math.max(0, tb - 3), 1 - 0.7 * ss(3, 4.5, tb), tb > 3.2 ? 0.3 : null);
    streak(CX, CY + 20, 760, 130, -0.12, -1.0 + tb * 3.1, 2.4, ss(0.9, 1.2, tb) * (1 - ss(2.8, 3.3, tb)));
    // MATCHDAY (RACE DAY, FIGHT NIGHT), its letters closing up into place, rules either side
    const SP = 0.5 * 74, n = S.letters.length, total = S.adv.reduce((a, b) => a + b, 0) + SP * (n - 1);
    let x = CX - total / 2;
    const ty = CY + 150;
    S.letters.forEach((L, i) => {
      const a = ss(3.0 + i * 0.06, 3.35 + i * 0.06, tb), spread = (1 - eo(ss(3.0, 3.9, tb))) * (i - (n - 1) / 2) * 26;
      put(L, x + S.adv[i] / 2 + spread, ty, 1, a);
      x += S.adv[i] + SP;
    });
    const rw = 300 * eo(ss(3.3, 4.0, tb));
    if (rw > 1) {
      g.globalAlpha = 0.85 * ss(3.3, 3.6, tb);
      for (const side of [-1, 1]) {
        const x0 = side < 0 ? CX - total / 2 - 40 - rw : CX + total / 2 + 40, gr = g.createLinearGradient(x0, 0, x0 + rw, 0);
        gr.addColorStop(side < 0 ? 0 : 1, 'rgba(255,255,255,0)'); gr.addColorStop(side < 0 ? 1 : 0, 'rgba(255,255,255,0.9)');
        g.fillStyle = gr; g.fillRect(x0, ty - 1, rw, 3);
      }
      g.globalAlpha = 1;
    }
    if (S.fixture) put(S.fixture, CX, ty + 90 + 10 * (1 - eo(ss(3.8, 4.5, tb))), 1, ss(3.8, 4.5, tb));
    // out: to black (the app fades the video itself, back to the channel)
    const out = ss(dur - 0.8, dur, tb);
    if (out > 0) { g.globalAlpha = out; g.fillStyle = '#000'; g.fillRect(0, 0, W, H); g.globalAlpha = 1; }
    // in: out of the white end frame
    const wi = 1 - eo(ss(0, 0.9, tb));
    if (wi > 0) { g.globalAlpha = wi; g.drawImage(S.white, 0, 0); g.globalAlpha = 1; }
  }

  function draw(t) {
    g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
    if (cfg.mode === 'tail') brand(clamp(t, 0, LEN), LEN);
    else if (t < BR0) competition(t);
    else brand(t - BR0, LEN - BR0);
    if (cfg.mode !== 'tail' && t < 0.25) { g.globalAlpha = 1 - t / 0.25; g.fillStyle = '#000'; g.fillRect(0, 0, W, H); g.globalAlpha = 1; }
  }

  window.REVEAL = { init, draw, get LEN() { return LEN; }, get BR0() { return BR0; } };
})();
