'use strict';
/*
 * Pre-match line-ups mock-up: a procedural stadium (lit for the ground's place, date and kick-off time) with the
 * Whitebrook Road Sports line-up sequence over it. Deterministic: drawFrame(t) draws second t of the sequence.
 * window.PLAN (one entry of plans.json) and window.DATA (assets/data.json) are injected before load.
 */
(function () {
  const W = 1920, H = 1080;
  const cvs = document.getElementById('c');
  const ctx = cvs.getContext('2d');
  const PLAN = window.PLAN, DATA = window.DATA;
  const COND = PLAN.cond;
  const IS_NIGHT = COND === 'night', IS_SUNSET = COND === 'sunset';

  // ================================================================ helpers
  const PI = Math.PI, TAU = 2 * PI, DEG = PI / 180;
  const clamp = (x, a = 0, b = 1) => (x < a ? a : x > b ? b : x);
  const lerp = (a, b, k) => a + (b - a) * k;
  const smooth = (k) => k * k * (3 - 2 * k);
  const ramp = (a, b, x) => clamp((x - a) / (b - a));
  const sstep = (a, b, x) => smooth(ramp(a, b, x));
  const eOut3 = (k) => 1 - Math.pow(1 - k, 3);
  const eIn3 = (k) => k * k * k;
  const eInOut3 = (k) => (k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2);
  const eOutExpo = (k) => (k >= 1 ? 1 : 1 - Math.pow(2, -10 * k));
  const eOutBack = (k, s = 1.70158) => 1 + (s + 1) * Math.pow(k - 1, 3) + s * Math.pow(k - 1, 2);
  function mulberry(seed) {
    let a = seed >>> 0;
    return function () {
      a = (a + 0x6d2b79f5) >>> 0;
      let t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function hash(a, b) {
    let h = (Math.imul(a | 0, 374761393) + Math.imul(b | 0, 668265263)) | 0;
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
  }
  // every canvas made while the page gets ready, for settle() (then null)
  let MADE = [];
  function mk(w, h) { const c = document.createElement('canvas'); c.width = w; c.height = h; if (MADE) MADE.push(c); return c; }
  // Every part's drawing done now, on this TV's GPU, before the page says it is ready: the TV's WebView keeps a canvas's
  // drawing until something reads it, and the Streamer's PowerVR then spent seconds over the first frame seen. The
  // frames are run through once, unseen and unrendered (anything made on first use is made here, silently), then each
  // canvas made is read once, small, and one read back waits for the lot.
  function settle(frameAt, dur) {
    const s0 = performance.now();
    for (let t = 0; t < dur; t += 0.5) frameAt(t);
    frameAt(0);
    LK.hideAll();
    const sg = document.createElement('canvas').getContext('2d');
    sg.canvas.width = sg.canvas.height = 4;
    let n = 0;
    for (const c of (MADE || []).concat(LK.canvases())) if (c.width > 1 && c.height > 1) { sg.drawImage(c, 0, 0, 4, 4); n++; }
    sg.getImageData(0, 0, 1, 1);
    MADE = null;
    if (window.BX) BX.log('settled ' + n + ' parts in ' + (performance.now() - s0).toFixed(0) + ' ms');
  }
  const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
  const rgba = (c, a) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`;

  // value noise + fbm
  const PERM = new Uint8Array(512), GRD = new Float32Array(256);
  (function () {
    const R = mulberry(1234);
    for (let i = 0; i < 256; i++) { PERM[i] = i; GRD[i] = R(); }
    for (let i = 255; i > 0; i--) { const j = (R() * (i + 1)) | 0; const tt = PERM[i]; PERM[i] = PERM[j]; PERM[j] = tt; }
    for (let i = 0; i < 256; i++) PERM[i + 256] = PERM[i];
  })();
  function vnoise(x, y) {
    const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
    const X = xi & 255, Y = yi & 255;
    const a = GRD[PERM[PERM[X] + Y]], b = GRD[PERM[PERM[X + 1] + Y]], c = GRD[PERM[PERM[X] + Y + 1]], d = GRD[PERM[PERM[X + 1] + Y + 1]];
    const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
  }
  function fbm(x, y, oct) {
    let s = 0, a = 0.5, f = 1, n = 0;
    for (let i = 0; i < oct; i++) { s += a * vnoise(x * f + i * 17.31, y * f - i * 9.13); n += a; f *= 2.03; a *= 0.5; }
    return s / n;
  }

  // ================================================================ brand + teams
  const VIOLET = '#6D28F2', CORAL = '#FF6B6B', LILAC = '#C2ADFF', PEACH = '#FFA196', NIGHT = '#07090F';
  // The live overlays pass each side's colours (DATA.home.color, color2, short): the mock-up's own two clubs otherwise.
  const TEAMS = (function () {
    const T = {
    home: { c1: '#EF0107', c2: '#7A0004', ink: '#FFFFFF', short: 'ARS', kit: { shirt: [222, 12, 20], sleeve: [244, 244, 244], shorts: [244, 244, 244], socks: [222, 12, 20] }, gk: { shirt: [40, 190, 160], sleeve: [40, 190, 160], shorts: [30, 120, 104], socks: [40, 190, 160] } },
    away: { c1: '#F5A623', c2: '#8F5600', ink: '#121212', short: 'HUL', kit: { shirt: [246, 160, 30], sleeve: [246, 160, 30], shorts: [18, 18, 20], socks: [246, 160, 30] }, gk: { shirt: [60, 98, 228], sleeve: [60, 98, 228], shorts: [36, 60, 150], socks: [60, 98, 228] } },
    };
    const rgb = (h) => { const m = /^#?([0-9a-f]{6})$/i.exec(h || ''); if (!m) return null; const v = parseInt(m[1], 16); return [(v >> 16) & 255, (v >> 8) & 255, v & 255]; };
    const hex = (c) => '#' + c.map((v) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('');
    for (const side of ['home', 'away']) {
      const d = DATA && DATA[side], c = d && rgb(d.color);
      if (!c) continue;
      const c2 = rgb(d.color2) || [244, 244, 244];
      const light = (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / 255 > 0.6;
      T[side] = { c1: hex(c), c2: hex(c.map((v) => v * 0.5)), ink: light ? '#121212' : '#FFFFFF', short: (d.short || d.name || side).slice(0, 3).toUpperCase(),
        kit: { shirt: c, sleeve: c, shorts: c2, socks: c }, gk: T[side].gk };
    }
    return T;
  })();
  const REF_KIT = { shirt: [22, 24, 30], sleeve: [22, 24, 30], shorts: [18, 18, 22], socks: [22, 24, 30] };

  // ================================================================ sun for the ground, the date and the time
  function solar(ms, lat, lon) {
    const n = ms / 86400000 + 2440587.5 - 2451545.0;
    const L = (((280.46 + 0.9856474 * n) % 360) + 360) % 360;
    const g = ((((357.528 + 0.9856003 * n) % 360) + 360) % 360) * DEG;
    const lam = (L + 1.915 * Math.sin(g) + 0.02 * Math.sin(2 * g)) * DEG;
    const eps = (23.439 - 0.0000004 * n) * DEG;
    const ra = Math.atan2(Math.cos(eps) * Math.sin(lam), Math.cos(lam));
    const dec = Math.asin(Math.sin(eps) * Math.sin(lam));
    const gmst = (((18.697374558 + 24.06570982441908 * n) % 24) + 24) % 24;
    const ha = (gmst * 15 + lon) * DEG - ra;
    const la = lat * DEG;
    const alt = Math.asin(Math.sin(la) * Math.sin(dec) + Math.cos(la) * Math.cos(dec) * Math.cos(ha));
    const az = Math.atan2(-Math.sin(ha), Math.tan(dec) * Math.cos(la) - Math.sin(la) * Math.cos(ha));
    return { alt: alt / DEG, az: (((az / DEG) % 360) + 360) % 360 };
  }
  const KO = PLAN.ko.split(':').map(Number);
  // the walk-out, about three minutes before kick-off (November: UK time is UTC)
  const SCENE_MS = PLAN.at ? PLAN.at - 180000 : Date.UTC(2026, 10, 7, KO[0], KO[1] - 3, 0);   // live: the game's own kick-off
  const SOL = solar(SCENE_MS, DATA.lat, DATA.lon);
  const BEARING = -6; // compass bearing of the procedural ground's long axis (+x)
  const SPHI = (SOL.az - BEARING) * DEG;
  const SALT = SOL.alt * DEG;
  const SUN = [Math.cos(SPHI) * Math.cos(SALT), Math.sin(SALT), Math.sin(SPHI) * Math.cos(SALT)];
  const SUN_ON = !IS_NIGHT && SOL.alt > -0.4;

  // ================================================================ the look for the condition
  const CFG = {
    day: {
      sun: [2.75, 2.4, 1.9], sky: [0.44, 0.51, 0.66], bounce: [0.1, 0.12, 0.08], flood: 0.0, floodCol: [1, 1, 1],
      haze: [0.6, 0.68, 0.82], hazeK: 1 / 2600, expo: 1.8, pods: 0, rain: 0, grade: 'day', bloom: 0.22, standFlood: 1, pitchFlood: 1, camFlip: 0,
    },
    sunset: {
      sun: [2.6, 1.15, 0.48], sky: [0.2, 0.18, 0.26], bounce: [0.05, 0.045, 0.05], flood: 1.0, floodCol: [1.0, 0.97, 0.94],
      haze: [0.36, 0.28, 0.32], hazeK: 1 / 1700, expo: 1.7, pods: 0.85, rain: 0, grade: 'sunset', bloom: 0.45, standFlood: 0.45, pitchFlood: 0.55, camFlip: 1,
    },
    night: {
      sun: [0, 0, 0], sky: [0.022, 0.026, 0.04], bounce: [0.02, 0.02, 0.022], flood: 1.15, floodCol: [0.95, 0.99, 1.05],
      haze: [0.05, 0.055, 0.068], hazeK: 1 / 700, expo: 1.35, pods: 1.0, rain: 1, grade: 'night', bloom: 0.55, standFlood: 0.3, pitchFlood: 0.66, camFlip: 0,
    },
  }[COND];
  const TONE = new Uint8Array(4096);
  for (let i = 0; i < 4096; i++) { const v = (i / 4095) * 8; TONE[i] = Math.round(255 * Math.pow(1 - Math.exp(-v * CFG.expo), 1 / 2.2)); }
  const tn = (v) => TONE[v <= 0 ? 0 : v >= 8 ? 4095 : (v * 511.875) | 0];
  const toLin = (c) => [Math.pow(c[0] / 255, 2.2), Math.pow(c[1] / 255, 2.2), Math.pow(c[2] / 255, 2.2)];
  const strLin = (c) => `rgb(${tn(c[0])},${tn(c[1])},${tn(c[2])})`;
  const HAZE_D = [tn(CFG.haze[0]), tn(CFG.haze[1]), tn(CFG.haze[2])];

  // ================================================================ the ground: bowl, stands, roof
  const A0 = 60, B0 = 41, RC = 16; // inner edge of the bowl: a rounded rectangle
  const PL = 52.5, PW = 34; // the pitch
  function sdRR(x, z) {
    const qx = Math.abs(x) - (A0 - RC), qz = Math.abs(z) - (B0 - RC);
    const mx = qx > 0 ? qx : 0, mz = qz > 0 ? qz : 0;
    return Math.sqrt(mx * mx + mz * mz) + Math.min(Math.max(qx, qz), 0) - RC;
  }
  const RING = [];
  (function () {
    const sx = A0 - RC, sz = B0 - RC;
    const seg = (ax, az, bx, bz, nx, nz, n) => { for (let k = 0; k < n; k++) { const u = k / n; RING.push({ x: ax + (bx - ax) * u, z: az + (bz - az) * u, nx, nz }); } };
    const arc = (cx, cz, a0, n) => { for (let k = 0; k < n; k++) { const a = a0 + (PI / 2) * (k / n); RING.push({ x: cx + RC * Math.cos(a), z: cz + RC * Math.sin(a), nx: Math.cos(a), nz: Math.sin(a) }); } };
    seg(A0, -sz, A0, sz, 1, 0, 16); arc(sx, sz, 0, 10);
    seg(sx, B0, -sx, B0, 0, 1, 28); arc(-sx, sz, PI / 2, 10);
    seg(-A0, sz, -A0, -sz, -1, 0, 16); arc(-sx, -sz, PI, 10);
    seg(-sx, -B0, sx, -B0, 0, -1, 28); arc(sx, -sz, 1.5 * PI, 10);
    let s = 0;
    for (let i = 0; i < RING.length; i++) { RING[i].s = s; const q = RING[(i + 1) % RING.length]; s += Math.hypot(q.x - RING[i].x, q.z - RING[i].z); }
  })();
  const NR = RING.length;
  // the stand, outward from the inner edge (d) and up (y); each point names the stretch that ends there
  const ROOF = { dIn: 7, dOut: 49, yIn: 34.6, yOut: 37.4, th: 1.6 };
  const roofY = (d) => lerp(ROOF.yIn, ROOF.yOut, (d - ROOF.dIn) / (ROOF.dOut - ROOF.dIn));
  const PROF = [
    { d: 0, y: 0 },
    { d: 0, y: 1.3, m: 'wall' },
    { d: 1.6, y: 1.3, m: 'walk' },
    { d: 6.4, y: 3.6, m: 'seatL' },
    { d: 11.2, y: 5.9, m: 'seatL' },
    { d: 16.0, y: 8.2, m: 'seatL' },
    { d: 20.8, y: 10.5, m: 'seatL' },
    { d: 20.8, y: 13.4, m: 'fascia' },
    { d: 23.0, y: 13.4, m: 'walk' },
    { d: 27.6, y: 16.8, m: 'seatU' },
    { d: 32.2, y: 20.2, m: 'seatU' },
    { d: 36.8, y: 23.6, m: 'seatU' },
    { d: 41.4, y: 27.0, m: 'seatU' },
    { d: 46.0, y: 30.4, m: 'seatU' },
    { d: 46.0, y: roofY(46), m: 'back' },
  ];
  const STAND_T = new Float32Array(186);
  for (let i = 0; i < 186; i++) {
    const d = i * 0.25;
    let y = 0;
    for (let j = 1; j < PROF.length; j++) {
      const a = PROF[j - 1], b = PROF[j];
      if (d >= a.d && d <= b.d) { const yy = b.d === a.d ? Math.max(a.y, b.y) : lerp(a.y, b.y, (d - a.d) / (b.d - a.d)); if (yy > y) y = yy; }
    }
    STAND_T[i] = y;
  }

  // sunlight reaching a point (soft-edged), marching toward the sun over the bowl's stands and roof
  const LHX = Math.cos(SPHI), LHZ = Math.sin(SPHI), TANALT = Math.tan(Math.max(SALT, 0.0015));
  function sunVis(px, py, pz) {
    if (!SUN_ON) return 0;
    let minC = 1e9;
    for (let s = 0.9; s < 480; s += 1.25) {
      const h = py + TANALT * s;
      if (h > 41.2) break;
      const qx = px + LHX * s, qz = pz + LHZ * s;
      const d = sdRR(qx, qz);
      if (d > 49.6) break;
      if (d >= 0) {
        if (d < 46) { const c = h - STAND_T[(d * 4) | 0]; if (c < minC) minC = c; }
        else if (h < roofY(46)) return 0;
        if (d >= ROOF.dIn) { const yb = roofY(d); if (h > yb - 0.25 && h < yb + ROOF.th + 0.25) return 0; }
      }
    }
    return sstep(-0.45, 0.45, minC);
  }

  // ================================================================ materials and faces
  const MAT = {
    wall: { c: [30, 32, 38], flood: 0.3 },
    walk: { c: [96, 98, 102], flood: 0.3 },
    seatL: { c: [84, 20, 27], flood: 0.34, crowd: 1 },
    seatU: { c: [84, 20, 27], flood: 0.24, crowd: 1 },
    fascia: { c: [24, 26, 32], flood: 0.25 },
    back: { c: [40, 42, 48], flood: 0.12 },
    roofTop: { c: [206, 210, 216], flood: 0.0 },
    roofBot: { c: [128, 132, 140], flood: 0.2 },
    roofIn: { c: [46, 49, 56], flood: 0.06 },
    board: { c: [20, 20, 24], flood: 0.3 },
  };
  for (const k in MAT) MAT[k].lin = toLin(MAT[k].c);
  function skyOcc(mat, d) {
    if (mat === 'roofTop') return 1;
    if (mat === 'board') return 0.85;
    if (mat === 'roofBot') return 0.42;
    if (d < ROOF.dIn) return 0.8;
    return clamp(0.78 - (0.55 * (d - ROOF.dIn)) / (ROOF.dOut - ROOF.dIn), 0.2, 1);
  }
  function lightVec(nx, ny, nz, occ, floodK, vis) {
    const skyK = (0.55 + 0.45 * ny) * occ, bK = 0.5 - 0.5 * ny;
    const nl = Math.max(0, nx * SUN[0] + ny * SUN[1] + nz * SUN[2]) * vis;
    const fl = CFG.flood * floodK * (floodK > 1 ? CFG.pitchFlood : CFG.standFlood);
    return [
      CFG.sky[0] * skyK + CFG.bounce[0] * bK + CFG.sun[0] * nl + fl * CFG.floodCol[0],
      CFG.sky[1] * skyK + CFG.bounce[1] * bK + CFG.sun[1] * nl + fl * CFG.floodCol[1],
      CFG.sky[2] * skyK + CFG.bounce[2] * bK + CFG.sun[2] * nl + fl * CFG.floodCol[2],
    ];
  }

  const FACES = [];
  const vtx = (i, d, y) => { const p = RING[((i % NR) + NR) % NR]; return [p.x + p.nx * d, y, p.z + p.nz * d]; };
  function addQuad(v, mat, ex, extra) {
    const ax = v[1][0] - v[0][0], ay = v[1][1] - v[0][1], az = v[1][2] - v[0][2];
    const bx = v[3][0] - v[0][0], by = v[3][1] - v[0][1], bz = v[3][2] - v[0][2];
    let nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
    const nl = Math.hypot(nx, ny, nz);
    if (nl < 1e-9) return null;
    nx /= nl; ny /= nl; nz /= nl;
    if (nx * ex[0] + ny * ex[1] + nz * ex[2] < 0) { nx = -nx; ny = -ny; nz = -nz; }
    const c = [(v[0][0] + v[1][0] + v[2][0] + v[3][0]) / 4, (v[0][1] + v[1][1] + v[2][1] + v[3][1]) / 4, (v[0][2] + v[1][2] + v[2][2] + v[3][2]) / 4];
    const f = Object.assign({ v, n: [nx, ny, nz], c, mat }, extra);
    FACES.push(f);
    return f;
  }
  for (let i = 0; i < NR; i++) {
    const p = RING[i], q = RING[(i + 1) % NR];
    let onx = p.nx + q.nx, onz = p.nz + q.nz; const ol = Math.hypot(onx, onz); onx /= ol; onz /= ol;
    for (let j = 1; j < PROF.length; j++) {
      const a = PROF[j - 1], b = PROF[j], m = b.m;
      const v = [vtx(i, a.d, a.y), vtx(i + 1, a.d, a.y), vtx(i + 1, b.d, b.y), vtx(i, b.d, b.y)];
      const ex = m === 'wall' || m === 'fascia' || m === 'back' ? [-onx, 0, -onz] : m === 'walk' ? [0, 1, 0] : [-onx, 1, -onz];
      addQuad(v, m, ex, { ring: i, dMid: (a.d + b.d) / 2, onx, onz, band: j, s: p.s });
    }
    const bands = [ROOF.dIn, ROOF.dIn + 12, ROOF.dIn + 26, ROOF.dOut];
    for (let k = 0; k < 3; k++) {
      const d0 = bands[k], d1 = bands[k + 1];
      addQuad([vtx(i, d0, roofY(d0) + ROOF.th), vtx(i + 1, d0, roofY(d0) + ROOF.th), vtx(i + 1, d1, roofY(d1) + ROOF.th), vtx(i, d1, roofY(d1) + ROOF.th)], 'roofTop', [0, 1, 0], { ring: i, dMid: (d0 + d1) / 2, onx, onz, band: k, s: p.s });
      addQuad([vtx(i, d0, roofY(d0)), vtx(i + 1, d0, roofY(d0)), vtx(i + 1, d1, roofY(d1)), vtx(i, d1, roofY(d1))], 'roofBot', [0, -1, 0], { ring: i, dMid: (d0 + d1) / 2, onx, onz, band: k, s: p.s });
    }
    const r0 = ROOF.dIn;
    addQuad([vtx(i, r0, roofY(r0)), vtx(i + 1, r0, roofY(r0)), vtx(i + 1, r0, roofY(r0) + ROOF.th), vtx(i, r0, roofY(r0) + ROOF.th)], 'roofIn', [-onx, 0, -onz], { ring: i, dMid: r0, onx, onz, s: p.s });
  }
  // LED boards round the pitch (two-sided)
  (function () {
    const bx = PL + 4.5, bz = PW + 4.4, hgt = 0.95;
    let s = 0;
    const run = (x0, z0, x1, z1, n, nx, nz) => {
      for (let k = 0; k < n; k++) {
        const a = k / n, b = (k + 1) / n;
        const ax = lerp(x0, x1, a), az = lerp(z0, z1, a), cx = lerp(x0, x1, b), cz = lerp(z0, z1, b);
        addQuad([[ax, 0, az], [cx, 0, cz], [cx, hgt, cz], [ax, hgt, az]], 'board', [nx, 0, nz], { two: 1, s, dMid: -3 });
        s += Math.hypot(cx - ax, cz - az);
      }
    };
    run(-50, bz, 50, bz, 25, 0, -1);
    run(bx, 30, bx, -30, 15, -1, 0);
    run(50, -bz, -50, -bz, 25, 0, 1);
    run(-bx, -30, -bx, 30, 15, 1, 0);
  })();
  // light pods along the roof's inner edge
  const PODS = [];
  for (let i = 0; i < NR; i++) {
    const py = roofY(ROOF.dIn) + 0.45;
    const a = RING[i], b = RING[(i + 1) % NR];
    const px = (a.x + b.x) / 2 + ((a.nx + b.nx) / 2) * (ROOF.dIn - 0.05), pz = (a.z + b.z) / 2 + ((a.nz + b.nz) / 2) * (ROOF.dIn - 0.05);
    const tx = px * 0.25, tz = pz * 0.25;
    let ax = tx - px, ay = -py, az = tz - pz; const al = Math.hypot(ax, ay, az); ax /= al; ay /= al; az /= al;
    PODS.push({ x: px, y: py, z: pz, ax, ay, az });
  }

  // flat lighting per face
  for (const f of FACES) {
    const M = MAT[f.mat];
    let vis = 0;
    const pts = [f.c, f.v[0], f.v[1], f.v[2], f.v[3]];
    for (const p of pts) vis += sunVis(p[0] + f.n[0] * 0.25, p[1] + f.n[1] * 0.25, p[2] + f.n[2] * 0.25);
    vis /= pts.length;
    f.vis = vis;
    f.occ = skyOcc(f.mat, f.dMid);
    const lt = lightVec(f.n[0], f.n[1], f.n[2], f.occ, M.flood, vis);
    let alb = M.lin;
    if (f.mat === 'roofTop') { const rib = (f.ring % 2 === 0 ? 1.0 : 0.94) * (f.band === 0 ? 1.04 : 1); alb = [alb[0] * rib, alb[1] * rib, alb[2] * rib]; }
    if (f.mat === 'roofBot') { const rib = f.ring % 3 === 0 ? 0.8 : 1; alb = [alb[0] * rib, alb[1] * rib, alb[2] * rib]; }
    f.col = [alb[0] * lt[0], alb[1] * lt[1], alb[2] * lt[2]];
    if (f.mat === 'seatL' || f.mat === 'seatU') f.crowd = 1;
  }

  // ================================================================ the crowd, painted once into an atlas
  const TEX = 10; // px per metre in the atlas
  let ATLAS = null;
  const FAN_HOME = { cols: [[24, 24, 30], [36, 36, 44], [50, 48, 56], [20, 24, 40], [70, 70, 78], [96, 90, 84], [196, 20, 30], [214, 26, 36], [160, 16, 26], [232, 232, 232], [58, 40, 30], [30, 48, 70], [110, 112, 118], [150, 140, 128]], w: [12, 12, 9, 9, 7, 3, 6, 3, 3, 5, 4, 6, 5, 3] };
  const FAN_AWAY = { cols: [[245, 164, 34], [228, 146, 22], [20, 20, 22], [34, 34, 40], [50, 48, 56], [242, 172, 52], [14, 14, 14]], w: [12, 8, 9, 6, 4, 6, 5] };
  const SKIN = [[226, 184, 152], [200, 152, 116], [162, 112, 82], [112, 76, 54], [236, 202, 172], [70, 50, 40]];
  const HAIR = [[30, 25, 22], [62, 46, 36], [20, 20, 20], [124, 92, 62], [92, 92, 98]];
  function pick(R, P) { let tot = 0; for (const w of P.w) tot += w; let r = R() * tot; for (let i = 0; i < P.w.length; i++) { r -= P.w[i]; if (r <= 0) return P.cols[i]; } return P.cols[0]; }
  function isAway(f) { return f.mat === 'seatL' && f.ring >= 112 && f.ring <= 127; }
  function buildCrowd() {
    const seat = FACES.filter((f) => f.crowd);
    let x = 0, y = 0, rowH = 0;
    const AW = 4096;
    for (const f of seat) {
      const wid = Math.max(Math.hypot(f.v[1][0] - f.v[0][0], f.v[1][2] - f.v[0][2]), Math.hypot(f.v[2][0] - f.v[3][0], f.v[2][2] - f.v[3][2]));
      const hgt = Math.hypot(f.v[3][0] - f.v[0][0], f.v[3][1] - f.v[0][1], f.v[3][2] - f.v[0][2]);
      const tw = Math.min(110, Math.max(8, Math.ceil(wid * TEX))), th = Math.min(80, Math.max(8, Math.ceil(hgt * TEX)));
      if (x + tw > AW) { x = 0; y += rowH + 2; rowH = 0; }
      f.tile = { x, y, w: tw, h: th, wid, hgt };
      x += tw + 2;
      if (th > rowH) rowH = th;
    }
    ATLAS = mk(AW, y + rowH + 2);
    const a = ATLAS.getContext('2d');
    const R = mulberry(4242);
    for (const f of seat) paintTile(a, f, R);
    const soft = mk(ATLAS.width, ATLAS.height), sg = soft.getContext('2d');
    sg.filter = 'blur(0.45px)'; sg.drawImage(ATLAS, 0, 0); sg.filter = 'none';
    a.clearRect(0, 0, ATLAS.width, ATLAS.height); a.drawImage(soft, 0, 0);
    // the average colour of each tile, for the fill under it
    const img = a.getImageData(0, 0, ATLAS.width, ATLAS.height).data;
    for (const f of seat) {
      const T = f.tile; let r = 0, g = 0, b = 0, n = 0;
      for (let yy = T.y; yy < T.y + T.h; yy += 2) for (let xx = T.x; xx < T.x + T.w; xx += 2) { const o = (yy * ATLAS.width + xx) * 4; r += img[o]; g += img[o + 1]; b += img[o + 2]; n++; }
      f.avg = [r / n, g / n, b / n];
    }
  }
  function paintTile(a, f, R) {
    const T = f.tile, [v0, v1, v2, v3] = f.v;
    const P = (u, v) => [
      lerp(lerp(v0[0], v1[0], u), lerp(v3[0], v2[0], u), v),
      lerp(lerp(v0[1], v1[1], u), lerp(v3[1], v2[1], u), v),
      lerp(lerp(v0[2], v1[2], u), lerp(v3[2], v2[2], u), v),
    ];
    const M = MAT[f.mat];
    const seatLin = M.lin;
    // seats, in blocks so a shadow edge runs through the tile
    const bw = 5, bh = 5;
    for (let by = 0; by < T.h; by += bh) for (let bx = 0; bx < T.w; bx += bw) {
      const u = (bx + bw / 2) / T.w, v = 1 - (by + bh / 2) / T.h;
      const p = P(u, v);
      const vis = sunVis(p[0] + f.n[0] * 0.3, p[1] + f.n[1] * 0.3 + 0.2, p[2] + f.n[2] * 0.3);
      const lt = lightVec(f.n[0], f.n[1], f.n[2], f.occ, M.flood, vis);
      a.fillStyle = strLin([seatLin[0] * lt[0], seatLin[1] * lt[1], seatLin[2] * lt[2]]);
      a.fillRect(T.x + bx, T.y + by, Math.min(bw, T.w - bx), Math.min(bh, T.h - by));
    }
    // the row steps
    const rowPx = 0.85 * TEX * (T.h / (f.tile.hgt * TEX));
    const away = isAway(f);
    const occP = away ? 0.9 : 0.95;
    const facing = [-f.onx * 0.82, 0.45, -f.onz * 0.82];
    const aisle = f.ring % 6 === 0 ? [T.w / 2 - 4, T.w / 2 + 4] : null;
    if (aisle) {
      const p = P(0.5, 0.5);
      const vis = sunVis(p[0] + f.n[0] * 0.3, p[1] + 0.5, p[2] + f.n[2] * 0.3);
      const lt = lightVec(f.n[0], f.n[1], f.n[2], f.occ, M.flood, vis);
      const cl = toLin([92, 92, 96]);
      a.fillStyle = strLin([cl[0] * lt[0], cl[1] * lt[1], cl[2] * lt[2]]);
      a.fillRect(T.x + Math.round(aisle[0]), T.y, 8, T.h);
    }
    for (let ry = T.h - 2, row = 0; ry > 3; ry -= rowPx, row++) {
      a.fillStyle = 'rgba(0,0,0,0.32)';
      a.fillRect(T.x, T.y + Math.round(ry), T.w, 1);
      const off = row % 2 ? 2.5 : 0;
      for (let sx = 1.5 + off; sx < T.w - 1.5; sx += 5) {
        if (aisle && sx > aisle[0] - 2 && sx < aisle[1] + 2) continue;
        if (R() > occP) continue;
        const px = Math.round(sx + (R() - 0.5) * 1.6), py = Math.round(ry - (R() * 1.2));
        const u = px / T.w, v = 1 - py / T.h;
        const p = P(clamp(u), clamp(v));
        const vis = sunVis(p[0] + f.n[0] * 0.3, p[1] + 1.15, p[2] + f.n[2] * 0.3);
        const lt = lightVec(facing[0], facing[1], facing[2], Math.min(1, f.occ + 0.1), M.flood * 1.1, vis);
        const body = toLin(pick(R, away ? FAN_AWAY : FAN_HOME));
        a.fillStyle = strLin([body[0] * lt[0], body[1] * lt[1], body[2] * lt[2]]);
        a.fillRect(T.x + px - 2, T.y + py - 6, 4, 6);
        const hr = R();
        const headC = toLin(hr < 0.55 ? SKIN[(R() * SKIN.length) | 0] : hr < 0.85 ? HAIR[(R() * HAIR.length) | 0] : pick(R, away ? FAN_AWAY : FAN_HOME));
        a.fillStyle = strLin([headC[0] * lt[0], headC[1] * lt[1], headC[2] * lt[2]]);
        a.fillRect(T.x + px - 1, T.y + py - 8, 3, 2);
      }
    }
  }

  // ================================================================ pitch markings
  const LINES = [];
  function lineSeg(x0, z0, x1, z1, w = 0.12) {
    const dx = x1 - x0, dz = z1 - z0, l = Math.hypot(dx, dz), nx = (-dz / l) * w / 2, nz = (dx / l) * w / 2;
    LINES.push([[x0 + nx, z0 + nz], [x1 + nx, z1 + nz], [x1 - nx, z1 - nz], [x0 - nx, z0 - nz]]);
  }
  function lineArc(cx, cz, r, a0, a1, n, w = 0.12) {
    for (let k = 0; k < n; k++) {
      const t0 = a0 + ((a1 - a0) * k) / n, t1 = a0 + ((a1 - a0) * (k + 1)) / n, ri = Math.max(0, r - w / 2), ro = r + w / 2;
      LINES.push([[cx + ri * Math.cos(t0), cz + ri * Math.sin(t0)], [cx + ro * Math.cos(t0), cz + ro * Math.sin(t0)], [cx + ro * Math.cos(t1), cz + ro * Math.sin(t1)], [cx + ri * Math.cos(t1), cz + ri * Math.sin(t1)]]);
    }
  }
  lineSeg(-PL, -PW, PL, -PW); lineSeg(PL, -PW, PL, PW); lineSeg(PL, PW, -PL, PW); lineSeg(-PL, PW, -PL, -PW);
  lineSeg(0, -PW, 0, PW);
  lineArc(0, 0, 9.15, 0, TAU, 72);
  lineArc(0, 0, 0.11, 0, TAU, 8, 0.22);
  for (const sg of [-1, 1]) {
    const gx = sg * PL;
    lineSeg(gx, -20.16, gx - sg * 16.5, -20.16); lineSeg(gx - sg * 16.5, -20.16, gx - sg * 16.5, 20.16); lineSeg(gx - sg * 16.5, 20.16, gx, 20.16);
    lineSeg(gx, -9.16, gx - sg * 5.5, -9.16); lineSeg(gx - sg * 5.5, -9.16, gx - sg * 5.5, 9.16); lineSeg(gx - sg * 5.5, 9.16, gx, 9.16);
    const spx = gx - sg * 11, ang = Math.acos(5.5 / 9.15);
    lineArc(spx, 0, 0.11, 0, TAU, 8, 0.22);
    if (sg > 0) lineArc(spx, 0, 9.15, PI - ang, PI + ang, 26); else lineArc(spx, 0, 9.15, -ang, ang, 26);
  }
  lineArc(PL, PW, 1, PI, 1.5 * PI, 6); lineArc(-PL, PW, 1, -0.5 * PI, 0, 6); lineArc(PL, -PW, 1, 0.5 * PI, PI, 6); lineArc(-PL, -PW, 1, 0, 0.5 * PI, 6);

  // the teams lined up for the walk-out, facing the main stand (+z)
  const FIGS = [];
  (function () {
    const R = mulberry(77);
    const zl = 12.5;
    for (let i = 0; i < 11; i++) FIGS.push({ x: -13.2 + i * 1.12, z: zl + (R() - 0.5) * 0.25, kit: i === 0 ? TEAMS.home.gk : TEAMS.home.kit, skin: SKIN[(R() * 5) | 0], ph: R() * 6 });
    for (let i = 0; i < 3; i++) FIGS.push({ x: -1.15 + i * 1.15, z: zl + 0.1, kit: REF_KIT, skin: SKIN[(R() * 5) | 0], ph: R() * 6 });
    for (let i = 0; i < 11; i++) FIGS.push({ x: 2.1 + i * 1.12, z: zl + (R() - 0.5) * 0.25, kit: i === 10 ? TEAMS.away.gk : TEAMS.away.kit, skin: SKIN[(R() * 5) | 0], ph: R() * 6 });
  })();

  // ================================================================ camera
  function makeCam(pos, tgt, fov) {
    const fx = tgt[0] - pos[0], fy = tgt[1] - pos[1], fz = tgt[2] - pos[2];
    const fl = Math.hypot(fx, fy, fz);
    const f0 = fx / fl, f1 = fy / fl, f2 = fz / fl;
    let r0 = -f2, r2 = f0; const rl = Math.hypot(r0, r2); r0 /= rl; r2 /= rl;
    const u0 = -r2 * f1, u1 = r2 * f0 - r0 * f2, u2 = r0 * f1;
    return { px: pos[0], py: pos[1], pz: pos[2], f0, f1, f2, r0, r2, u0, u1, u2, F: W / 2 / Math.tan((fov * DEG) / 2), yaw: Math.atan2(f2, f0), pitch: Math.asin(f1), near: 0.6 };
  }
  const CAMP = { th0: 212, th1: 221, r0: 69, r1: 65.5, h0: 31.5, h1: 30.5, p0: 11.5, p1: 12.5, fov: 77 };
  function beautyCam(t) {
    const k = clamp(t / 42);
    const th = (lerp(CAMP.th0, CAMP.th1, k) + (CFG.camFlip ? 180 : 0)) * DEG;
    const r = lerp(CAMP.r0, CAMP.r1, k);
    const h = lerp(CAMP.h0, CAMP.h1, k) + 0.35 * Math.sin(t * 0.37);
    const pd = lerp(CAMP.p0, CAMP.p1, k) * DEG;
    const pos = [r * Math.cos(th), h, r * Math.sin(th)];
    const c = makeCam(pos, [pos[0] - Math.cos(th) * Math.cos(pd) * 100, h - Math.sin(pd) * 100, pos[2] - Math.sin(th) * Math.cos(pd) * 100], CAMP.fov);
    c.sky = true;
    return c;
  }
  function channelCam(u) {
    return makeCam([-4 + 0.32 * u, 20, 58], [-1.8 + 0.4 * u, 1.5, 4], 46);
  }
  const TMP = [0, 0, 0];
  function toCam(c, x, y, z) {
    const dx = x - c.px, dy = y - c.py, dz = z - c.pz;
    return [dx * c.r0 + dz * c.r2, dx * c.u0 + dy * c.u1 + dz * c.u2, dx * c.f0 + dy * c.f1 + dz * c.f2];
  }
  function proj(c, x, y, z) {
    const dx = x - c.px, dy = y - c.py, dz = z - c.pz;
    const zc = dx * c.f0 + dy * c.f1 + dz * c.f2;
    if (zc < c.near) return null;
    const xc = dx * c.r0 + dz * c.r2, yc = dx * c.u0 + dy * c.u1 + dz * c.u2;
    TMP[0] = W / 2 + (c.F * xc) / zc; TMP[1] = H / 2 - (c.F * yc) / zc; TMP[2] = zc;
    return TMP;
  }
  function clipProject(c, P3) {
    // P3: camera-space points; returns flat screen coords [x0,y0,x1,y1,...] after clipping at the near plane
    const n = c.near;
    let any = false, all = true;
    for (const p of P3) { if (p[2] >= n) any = true; else all = false; }
    if (!any) return null;
    let pts = P3;
    if (!all) {
      const out = [];
      for (let i = 0; i < P3.length; i++) {
        const a = P3[i], b = P3[(i + 1) % P3.length], ain = a[2] >= n, bin = b[2] >= n;
        if (ain) out.push(a);
        if (ain !== bin) { const k = (n - a[2]) / (b[2] - a[2]); out.push([a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, n]); }
      }
      pts = out;
    }
    const S = new Array(pts.length * 2);
    for (let i = 0; i < pts.length; i++) { S[2 * i] = W / 2 + (c.F * pts[i][0]) / pts[i][2]; S[2 * i + 1] = H / 2 - (c.F * pts[i][1]) / pts[i][2]; }
    S.clipped = !all;
    return S;
  }
  function groundPath(g, c, pts, y = 0) {
    const P3 = pts.map((p) => toCam(c, p[0], y, p[1]));
    const S = clipProject(c, P3);
    if (!S) return false;
    g.moveTo(S[0], S[1]);
    for (let i = 2; i < S.length; i += 2) g.lineTo(S[i], S[i + 1]);
    g.closePath();
    return true;
  }

  // ================================================================ sky (painted once for the drifting camera)
  const SKY_MX = 380, SKY_MY = 120, SKY_H = 720;
  let SKY = null, SKY_REF = null;
  function skyLin(dx, dy, dz) {
    const el = Math.asin(clamp(dy, -1, 1));
    const cg = clamp(dx * SUN[0] + dy * SUN[1] + dz * SUN[2], -1, 1);
    const gam = Math.acos(cg);
    const hl = Math.hypot(dx, dz) || 1;
    const sunSide = (1 + (dx / hl) * Math.cos(SPHI) + (dz / hl) * Math.sin(SPHI)) / 2;
    const elp = Math.max(el, 0);
    let r, g, b;
    let cloud = 0, cr = 0, cgc = 0, cb = 0;
    const tpl = dy > 0.01 ? 1500 / dy : 0;
    const cx = dx * tpl, cz = dz * tpl;
    if (COND === 'day') {
      const t = Math.pow(elp / (PI / 2), 0.36);
      r = lerp(0.40, 0.03, t); g = lerp(0.54, 0.12, t); b = lerp(0.82, 0.5, t);
      const glow = 0.55 * Math.exp(-gam / 0.14) + 0.2 * Math.exp(-gam / 0.7);
      r += 1.0 * glow; g += 0.82 * glow; b += 0.58 * glow;
      if (dy > 0.012) {
        const n = fbm(cx / 2500 + 11.3, cz / 2500 - 4.2, 6);
        const fade = Math.exp(-tpl / 45000);
        cloud = sstep(0.52, 0.68, n) * fade;
        const n2 = fbm((cx + Math.cos(SPHI) * 380) / 2500 + 11.3, (cz + Math.sin(SPHI) * 380) / 2500 - 4.2, 4);
        const lit = clamp(0.55 + (n - n2) * 5.5);
        const core = sstep(0.6, 0.82, n);
        const k = 1 - 0.32 * core;
        cr = lerp(0.5, 1.22, lit) * k; cgc = lerp(0.55, 1.18, lit) * k; cb = lerp(0.66, 1.1, lit) * k;
        const far = clamp(tpl / 30000);
        cr = lerp(cr, r * 1.08, far); cgc = lerp(cgc, g * 1.08, far); cb = lerp(cb, b * 1.05, far);
      }
    } else if (COND === 'sunset') {
      const t = Math.pow(elp / (PI / 2), 0.34);
      const ss = sunSide * sunSide;
      let hr = lerp(0.46, 1.25, ss), hg = lerp(0.3, 0.52, ss), hb = lerp(0.38, 0.2, ss);
      const belt = sstep(0.01, 0.06, el) * (1 - sstep(0.12, 0.3, el)) * (1 - ss);
      const shadowBand = (1 - sstep(0.0, 0.035, el)) * (1 - ss);
      hr = lerp(hr, 0.2, shadowBand * 0.7); hg = lerp(hg, 0.21, shadowBand * 0.7); hb = lerp(hb, 0.32, shadowBand * 0.7);
      r = lerp(hr, 0.03, t) + belt * 0.12; g = lerp(hg, 0.055, t) + belt * 0.04; b = lerp(hb, 0.2, t) + belt * 0.08;
      const glow = 0.9 * Math.exp(-gam / 0.16) + 0.3 * Math.exp(-gam / 0.6);
      r += 1.2 * glow; g += 0.5 * glow; b += 0.18 * glow;
      if (dy > 0.012) {
        const n = fbm(cx / 5200 + 3.3, cz / 1700 - 1.2, 6);
        const fade = Math.exp(-tpl / 50000);
        cloud = sstep(0.54, 0.74, n) * 0.92 * fade;
        const core = sstep(0.6, 0.8, n);
        const n2 = fbm((cx + Math.cos(SPHI) * 500) / 5200 + 3.3, (cz + Math.sin(SPHI) * 500) / 1700 - 1.2, 4);
        const rim = clamp(0.35 + (n - n2) * 6) * (0.4 + 0.6 * ss);
        const br = 0.26, bgc = 0.15, bb = 0.26;
        cr = lerp(br, 1.55, rim) * (1 - 0.25 * core); cgc = lerp(bgc, 0.62, rim) * (1 - 0.25 * core); cb = lerp(bb, 0.36, rim) * (1 - 0.2 * core);
        const far = clamp(tpl / 32000);
        cr = lerp(cr, r, far); cgc = lerp(cgc, g, far); cb = lerp(cb, b, far);
      }
    } else {
      const t = Math.pow(elp / (PI / 2), 0.32);
      r = lerp(0.035, 0.004, t); g = lerp(0.026, 0.006, t); b = lerp(0.024, 0.012, t);
      if (dy > 0.005) {
        const n = fbm(cx / 1800 + 5.1, cz / 1800 + 2.7, 5);
        cloud = 0.94;
        const lift = 1 + 2.4 * Math.exp(-el / 0.09);
        cr = 0.013 * (0.5 + 1.0 * n) * lift; cgc = 0.011 * (0.5 + 1.0 * n) * lift; cb = 0.013 * (0.5 + 1.0 * n) * lift;
      }
    }
    if (el < 0) { const k = sstep(0, -0.06, el); r = lerp(r, r * 0.7, k); g = lerp(g, g * 0.7, k); b = lerp(b, b * 0.72, k); }
    r = lerp(r, cr, cloud); g = lerp(g, cgc, cloud); b = lerp(b, cb, cloud);
    return [r, g, b];
  }
  function buildSky() {
    const ref = beautyCam(21);
    SKY_REF = ref;
    const SW = W + 2 * SKY_MX, SH = SKY_H + SKY_MY;
    const hw = SW >> 1, hh = SH >> 1;
    const small = mk(hw, hh);
    const sc = small.getContext('2d');
    const id = sc.createImageData(hw, hh);
    const D = id.data;
    for (let y = 0; y < hh; y++) {
      const Y = -SKY_MY + 2 * y + 1;
      const vy = (H / 2 - Y) / ref.F;
      for (let x = 0; x < hw; x++) {
        const X = -SKY_MX + 2 * x + 1;
        const vx = (X - W / 2) / ref.F;
        let dx = ref.f0 + vx * ref.r0 + vy * ref.u0, dy = ref.f1 + vy * ref.u1, dz = ref.f2 + vx * ref.r2 + vy * ref.u2;
        const l = Math.hypot(dx, dy, dz); dx /= l; dy /= l; dz /= l;
        const c = skyLin(dx, dy, dz);
        const o = (y * hw + x) * 4;
        D[o] = tn(c[0]); D[o + 1] = tn(c[1]); D[o + 2] = tn(c[2]); D[o + 3] = 255;
      }
    }
    sc.putImageData(id, 0, 0);
    SKY = mk(SW, SH);
    const g = SKY.getContext('2d');
    g.imageSmoothingQuality = 'high';
    g.drawImage(small, 0, 0, SW, SH);
  }
  function drawSky(g, c) {
    let dyaw = c.yaw - SKY_REF.yaw; while (dyaw > PI) dyaw -= TAU; while (dyaw < -PI) dyaw += TAU;
    const ox = -c.F * dyaw, oy = c.F * (c.pitch - SKY_REF.pitch);
    g.drawImage(SKY, -SKY_MX + ox, -SKY_MY + oy);
  }

  // ================================================================ the scene
  const GLOW = mk(128, 128);
  (function () {
    const g = GLOW.getContext('2d');
    const gr = g.createRadialGradient(64, 64, 0, 64, 64, 64);
    gr.addColorStop(0, 'rgba(255,252,240,1)'); gr.addColorStop(0.08, 'rgba(255,250,236,0.75)'); gr.addColorStop(0.3, 'rgba(235,240,255,0.18)'); gr.addColorStop(1, 'rgba(220,230,255,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 128, 128);
  })();
  const SOFT = mk(64, 64);
  (function () {
    const g = SOFT.getContext('2d');
    const gr = g.createRadialGradient(32, 32, 0, 32, 32, 32);
    gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 64, 64);
  })();

  const GRASS = { a: toLin([84, 162, 58]), b: toLin([62, 132, 44]) };
  const PITCH_N = [0, 1, 0];
  function groundLight() {
    const vis = sunVis(0, 0.05, 0);
    return lightVec(0, 1, 0, 0.88, 1.55, vis);
  }
  let GL = null;
  function drawGround(g, c, t) {
    const L = GL;
    const ca = strLin([GRASS.a[0] * L[0], GRASS.a[1] * L[1], GRASS.a[2] * L[2]]);
    const cb = strLin([GRASS.b[0] * L[0], GRASS.b[1] * L[1], GRASS.b[2] * L[2]]);
    g.beginPath();
    groundPath(g, c, RING.map((p) => [p.x + p.nx * 0.6, p.z + p.nz * 0.6]));
    g.fillStyle = cb; g.fill();
    g.beginPath();
    for (let k = -11; k <= 11; k++) {
      if ((k & 1) === 0) continue;
      const x0 = k * 5.25 - 2.625, x1 = x0 + 5.25;
      groundPath(g, c, [[x0, -41.5], [x1, -41.5], [x1, 41.5], [x0, 41.5]]);
    }
    g.fillStyle = ca; g.fill();
    // the light on the grass: brighter away from the stands, a soft sheen far off
    g.save();
    g.beginPath(); groundPath(g, c, RING.map((p) => [p.x, p.z])); g.clip();
    const cen = proj(c, 0, 0, 0);
    if (cen) {
      const cx = cen[0], cy = cen[1];
      const ell = proj(c, 0, 0, -40);
      const ry = ell ? Math.max(60, Math.abs(cy - ell[1]) * 1.6) : 400;
      g.translate(cx, cy); g.scale(2.6, 1);
      const gr = g.createRadialGradient(0, 0, 0, 0, 0, ry);
      if (CFG.flood > 0) { gr.addColorStop(0, 'rgba(255,255,250,0.10)'); gr.addColorStop(0.7, 'rgba(255,255,250,0.02)'); gr.addColorStop(1, 'rgba(0,0,0,0.12)'); }
      else { gr.addColorStop(0, 'rgba(255,255,255,0.04)'); gr.addColorStop(1, 'rgba(0,0,0,0.10)'); }
      g.fillStyle = gr; g.fillRect(-ry * 2, -ry * 2, ry * 4, ry * 4);
    }
    g.restore();
    // darker towards the foot of the stands
    for (const [d0, d1, al] of [[-6, -3, 0.08], [-3, 0.6, 0.16]]) {
      g.beginPath();
      groundPath(g, c, RING.map((p) => [p.x + p.nx * d1, p.z + p.nz * d1]));
      groundPath(g, c, RING.map((p) => [p.x + p.nx * d0, p.z + p.nz * d0]).reverse());
      g.fillStyle = `rgba(0,0,0,${al})`; g.fill('evenodd');
    }
    // markings
    g.beginPath();
    for (const q of LINES) groundPath(g, c, q, 0.01);
    const lw = toLin([238, 240, 242]);
    g.fillStyle = strLin([lw[0] * L[0] * 0.9, lw[1] * L[1] * 0.9, lw[2] * L[2] * 0.9]);
    g.globalAlpha = 0.9; g.fill(); g.globalAlpha = 1;
    // wet grass: the lights mirrored in it
    if (CFG.rain) {
      g.save(); g.globalCompositeOperation = 'lighter';
      g.beginPath(); groundPath(g, c, RING.map((p) => [p.x, p.z])); g.clip();
      for (let i = 0; i < PODS.length; i += 1) {
        const P = PODS[i];
        const q = proj(c, P.x, -P.y, P.z);
        if (!q) continue;
        const x = q[0], y = q[1], z = q[2];
        if (x < -200 || x > W + 200 || y < -100 || y > H + 300) continue;
        const k = podFacing(c, P);
        if (k < 0.05) continue;
        const w = (c.F * 3.6) / z, h = (c.F * 26) / z;
        g.globalAlpha = 0.16 * k;
        g.drawImage(SOFT, x - w, y - h * 0.65, w * 2, h * 1.3);
      }
      g.restore();
    }
  }
  function podFacing(c, P) {
    let vx = c.px - P.x, vy = c.py - P.y, vz = c.pz - P.z; const l = Math.hypot(vx, vy, vz); vx /= l; vy /= l; vz /= l;
    const k = Math.max(0, vx * P.ax + vy * P.ay + vz * P.az);
    return k * k * k;
  }

  function faceColour(f, dist, emis) {
    const hz = 1 - Math.exp(-dist * CFG.hazeK);
    const c = emis || f.col;
    const r = lerp(c[0], CFG.haze[0], hz), gg = lerp(c[1], CFG.haze[1], hz), b = lerp(c[2], CFG.haze[2], hz);
    return `rgb(${tn(r)},${tn(gg)},${tn(b)})`;
  }
  const LED = toLin([226, 16, 30]), LED_W = toLin([250, 250, 250]), LED_D = toLin([120, 6, 14]);
  function ledLin(s, t, kind) {
    if (kind === 'ribbon') {
      const ph = (((s - t * 46) % 140) + 140) % 140;
      const sweep = Math.max(0, 1 - Math.abs(ph - 70) / 9);
      const base = Math.floor((s + t * 3) / 22) % 2 ? LED : LED_D;
      const k = 1.25;
      return [lerp(base[0], LED_W[0], sweep) * k, lerp(base[1], LED_W[1], sweep) * k, lerp(base[2], LED_W[2], sweep) * k];
    }
    const blk = ((((s + t * 5.5) % 18) + 18) % 18) < 9;
    const ph = (((s + t * 30) % 110) + 110) % 110;
    const sweep = Math.max(0, 1 - Math.abs(ph - 55) / 6);
    const base = blk ? LED : LED_D;
    const k = 1.15;
    return [lerp(base[0], LED_W[0], sweep * 0.8) * k, lerp(base[1], LED_W[1], sweep * 0.8) * k, lerp(base[2], LED_W[2], sweep * 0.8) * k];
  }
  const lerp2 = (S, i, j, k) => [lerp(S[2 * i], S[2 * j], k), lerp(S[2 * i + 1], S[2 * j + 1], k)];
  function polyPath(g, pts) { g.beginPath(); g.moveTo(pts[0][0], pts[0][1]); for (let i = 1; i < pts.length; i++) g.lineTo(pts[i][0], pts[i][1]); g.closePath(); }

  function drawFaceItem(g, c, it, t) {
    const f = it.f, S = it.S;
    g.beginPath(); g.moveTo(S[0], S[1]); for (let i = 2; i < S.length; i += 2) g.lineTo(S[i], S[i + 1]); g.closePath();
    let col;
    if (f.mat === 'board' && it.front) col = faceColour(f, it.d, ledLin(f.s, t, 'board'));
    else if (f.crowd && f.avg) {
      const hz = 1 - Math.exp(-it.d * CFG.hazeK);
      col = `rgb(${lerp(f.avg[0], HAZE_D[0], hz) | 0},${lerp(f.avg[1], HAZE_D[1], hz) | 0},${lerp(f.avg[2], HAZE_D[2], hz) | 0})`;
    } else col = faceColour(f, it.d);
    g.fillStyle = col; g.fill();
    g.strokeStyle = col; g.lineWidth = 1; g.stroke();
    if (S.clipped) return;
    if (f.crowd && f.tile) {
      const T = f.tile;
      const p0x = S[0], p0y = S[1], p1x = S[2], p1y = S[3], p3x = S[6], p3y = S[7];
      g.setTransform((p1x - p0x) / T.w, (p1y - p0y) / T.w, (p0x - p3x) / T.h, (p0y - p3y) / T.h, p3x, p3y);
      g.drawImage(ATLAS, T.x + 0.5, T.y + 0.5, T.w - 1, T.h - 1, -0.7, -0.7, T.w + 1.4, T.h + 1.4);
      g.setTransform(1, 0, 0, 1, 0, 0);
      const hz = 1 - Math.exp(-it.d * CFG.hazeK);
      if (hz > 0.025) {
        g.beginPath(); g.moveTo(S[0], S[1]); for (let i = 2; i < S.length; i += 2) g.lineTo(S[i], S[i + 1]); g.closePath();
        g.fillStyle = `rgba(${HAZE_D[0]},${HAZE_D[1]},${HAZE_D[2]},${hz.toFixed(3)})`; g.fill();
      }
    } else if (f.mat === 'fascia') {
      // the LED ribbon round the club level
      const a = lerp2(S, 0, 3, 0.38), b = lerp2(S, 1, 2, 0.38), cc = lerp2(S, 1, 2, 0.78), d = lerp2(S, 0, 3, 0.78);
      polyPath(g, [a, b, cc, d]);
      const lc = faceColour(f, it.d, ledLin(f.s, t, 'ribbon'));
      g.fillStyle = lc; g.fill(); g.strokeStyle = lc; g.lineWidth = 0.8; g.stroke();
    } else if (f.mat === 'roofBot') {
      g.beginPath(); g.moveTo(S[0], S[1]); g.lineTo(S[6], S[7]);
      g.strokeStyle = 'rgba(20,22,28,0.45)'; g.lineWidth = Math.max(1, Math.min(3, 900 / it.d)); g.stroke();
    } else if (f.mat === 'roofIn') {
      const a = lerp2(S, 0, 1, 0.22), b = lerp2(S, 0, 1, 0.78);
      const a2 = [lerp(a[0], lerp(S[6], S[4], 0.22), 0.55), lerp(a[1], lerp(S[7], S[5], 0.22), 0.55)];
      const b2 = [lerp(b[0], lerp(S[6], S[4], 0.78), 0.55), lerp(b[1], lerp(S[7], S[5], 0.78), 0.55)];
      polyPath(g, [a, b, b2, a2]);
      g.fillStyle = CFG.pods > 0 ? `rgba(255,252,238,${0.55 + 0.45 * CFG.pods})` : 'rgb(92,96,104)';
      g.fill();
    }
  }

  function drawGoal(g, c, sg) {
    const x = sg * PL, xb = sg * (PL + 2.1);
    const P = (X, Y, Z) => { const q = proj(c, X, Y, Z); return q ? [q[0], q[1], q[2]] : null; };
    const a = P(x, 0, -3.66), b = P(x, 2.44, -3.66), cc = P(x, 2.44, 3.66), d = P(x, 0, 3.66);
    const e = P(xb, 0, -3.66), f = P(xb, 1.6, -3.66), gq = P(xb, 1.6, 3.66), h = P(xb, 0, 3.66);
    if (!a || !b || !cc || !d || !e || !f || !gq || !h) return;
    const net = (pts, al) => { polyPath(g, pts); g.fillStyle = `rgba(235,238,245,${al})`; g.fill(); };
    net([b, cc, gq, f], 0.16); net([e, f, gq, h], 0.2); net([a, b, f, e], 0.14); net([d, cc, gq, h], 0.14);
    const lw = Math.max(1, (c.F * 0.12) / a[2]);
    g.strokeStyle = CFG.flood > 0 ? 'rgba(250,250,250,0.95)' : 'rgba(225,228,232,0.95)';
    g.lineWidth = lw; g.lineCap = 'round';
    g.beginPath(); g.moveTo(a[0], a[1]); g.lineTo(b[0], b[1]); g.lineTo(cc[0], cc[1]); g.lineTo(d[0], d[1]); g.stroke();
    g.lineWidth = Math.max(0.6, lw * 0.4);
    g.strokeStyle = 'rgba(230,232,238,0.5)';
    g.beginPath(); g.moveTo(b[0], b[1]); g.lineTo(f[0], f[1]); g.lineTo(e[0], e[1]); g.moveTo(cc[0], cc[1]); g.lineTo(gq[0], gq[1]); g.lineTo(h[0], h[1]); g.moveTo(f[0], f[1]); g.lineTo(gq[0], gq[1]); g.stroke();
  }

  function figLight() { return CFG.flood > 0 ? 1.0 : 0.82; }
  function drawFig(g, c, F, t) {
    const ft = proj(c, F.x, 0, F.z); if (!ft) return;
    const fx = ft[0], fy = ft[1], z = ft[2];
    const hd = proj(c, F.x, 1.82, F.z); if (!hd) return;
    const hx = hd[0], hy = hd[1];
    const ht = fy - hy;
    if (ht < 2.5) return;
    const w = (c.F * 0.5) / z;
    const k = figLight();
    const col = (cc, m = 1) => `rgb(${Math.min(255, cc[0] * k * m) | 0},${Math.min(255, cc[1] * k * m) | 0},${Math.min(255, cc[2] * k * m) | 0})`;
    const sway = Math.sin(t * 1.7 + F.ph) * 0.012 * ht;
    // shadows
    g.fillStyle = 'rgba(0,0,0,0.30)';
    g.beginPath(); g.ellipse(fx, fy, w * 0.75, w * 0.24, 0, 0, TAU); g.fill();
    if (CFG.flood > 0) {
      g.fillStyle = 'rgba(0,0,0,0.12)';
      for (const ang of [0.6, 2.5, 3.7, 5.6]) { g.beginPath(); g.ellipse(fx + Math.cos(ang) * w * 0.9, fy + Math.sin(ang) * w * 0.25, w * 1.0, w * 0.18, ang * 0.15, 0, TAU); g.fill(); }
    }
    const X = (fr) => lerp(fx, hx, fr) + sway * fr;
    const Y = (fr) => lerp(fy, hy, fr);
    const K = F.kit;
    // legs, socks
    g.fillStyle = col(K.socks, 0.9);
    g.fillRect(X(0.1) - w * 0.32, Y(0.3), w * 0.26, Y(0) - Y(0.3));
    g.fillRect(X(0.1) + w * 0.06, Y(0.3), w * 0.26, Y(0) - Y(0.3));
    g.fillStyle = col(F.skin, 0.95);
    g.fillRect(X(0.4) - w * 0.3, Y(0.44), w * 0.24, Y(0.3) - Y(0.44));
    g.fillRect(X(0.4) + w * 0.06, Y(0.44), w * 0.24, Y(0.3) - Y(0.44));
    // shorts
    g.fillStyle = col(K.shorts, 0.92);
    g.fillRect(X(0.5) - w * 0.36, Y(0.56), w * 0.72, Y(0.43) - Y(0.56));
    // arms
    g.fillStyle = col(F.skin, 0.9);
    g.fillRect(X(0.66) - w * 0.56, Y(0.8), w * 0.17, Y(0.54) - Y(0.8));
    g.fillRect(X(0.66) + w * 0.39, Y(0.8), w * 0.17, Y(0.54) - Y(0.8));
    g.fillStyle = col(K.sleeve);
    g.fillRect(X(0.75) - w * 0.57, Y(0.83), w * 0.19, Y(0.7) - Y(0.83));
    g.fillRect(X(0.75) + w * 0.38, Y(0.83), w * 0.19, Y(0.7) - Y(0.83));
    // shirt
    g.fillStyle = col(K.shirt);
    g.beginPath(); g.roundRect(X(0.7) - w * 0.4, Y(0.84), w * 0.8, Y(0.54) - Y(0.84), w * 0.12); g.fill();
    // head
    const hr = Math.max(0.8, ht * 0.072);
    g.fillStyle = col(F.skin);
    g.beginPath(); g.arc(X(0.92), Y(0.92), hr, 0, TAU); g.fill();
    g.fillStyle = 'rgba(28,22,20,0.9)';
    g.beginPath(); g.arc(X(0.95), Y(0.95), hr * 0.9, PI, TAU); g.fill();
  }

  function drawGlows(g, c, t) {
    g.save(); g.globalCompositeOperation = 'lighter';
    const flick = IS_NIGHT ? 1 : 0.75;
    for (const P of PODS) {
      const q = proj(c, P.x, P.y, P.z); if (!q) continue;
      const x = q[0], y = q[1], z = q[2];
      if (x < -300 || x > W + 300 || y < -300 || y > H + 300) continue;
      const k = podFacing(c, P);
      if (k < 0.02) continue;
      const r = (c.F * (IS_NIGHT ? 6.5 : 5)) / z;
      g.globalAlpha = Math.min(1, CFG.pods * 0.6 * k * flick);
      g.drawImage(GLOW, x - r, y - r, 2 * r, 2 * r);
      const r2 = (c.F * (IS_NIGHT ? 26 : 18)) / z;
      g.globalAlpha = CFG.pods * (IS_NIGHT ? 0.045 : 0.035) * k;
      g.drawImage(SOFT, x - r2, y - r2 * 0.8, 2 * r2, 1.6 * r2);
    }
    g.restore();
  }

  function drawRain(g, c, t) {
    g.save(); g.globalCompositeOperation = 'lighter'; g.lineCap = 'round';
    const layers = [
      { n: 1100, len: 14, sp: 1250, a: 0.07, w: 1, seed: 3 },
      { n: 520, len: 28, sp: 1850, a: 0.1, w: 1.3, seed: 5 },
      { n: 120, len: 64, sp: 2700, a: 0.11, w: 2.2, seed: 9 },
    ];
    for (const L of layers) {
      g.strokeStyle = `rgba(200,214,236,${L.a})`; g.lineWidth = L.w; g.beginPath();
      for (let i = 0; i < L.n; i++) {
        const x0 = hash(i, L.seed) * (W + 300) - 150, y0 = hash(i + 9999, L.seed) * (H + 240);
        const y = ((y0 + t * L.sp) % (H + 240)) - 120;
        const x = x0 + y * 0.12;
        g.moveTo(x, y); g.lineTo(x + 0.12 * L.len, y + L.len);
      }
      g.stroke();
    }
    // drops landing on the pitch
    const fr = Math.floor(t * 30);
    g.fillStyle = 'rgba(220,230,245,0.35)';
    for (let i = 0; i < 260; i++) {
      const gx = (hash(i, fr) - 0.5) * 112, gz = (hash(i + 777, fr) - 0.5) * 74;
      const q = proj(c, gx, 0.02, gz); if (!q) continue;
      const s = Math.max(0.8, (c.F * 0.05) / q[2]);
      g.fillRect(q[0], q[1], s * 1.6, s * 0.7);
    }
    g.restore();
  }

  function renderScene(g, c, t) {
    if (c.sky) { g.fillStyle = '#7f93b0'; g.fillRect(0, 0, W, H); drawSky(g, c); }
    else { g.fillStyle = '#0b0d12'; g.fillRect(0, 0, W, H); }
    drawGround(g, c, t);
    const items = [];
    for (const f of FACES) {
      const vx = c.px - f.c[0], vy = c.py - f.c[1], vz = c.pz - f.c[2];
      const dot = f.n[0] * vx + f.n[1] * vy + f.n[2] * vz;
      if (dot <= 0 && !f.two) continue;
      const P3 = [toCam(c, f.v[0][0], f.v[0][1], f.v[0][2]), toCam(c, f.v[1][0], f.v[1][1], f.v[1][2]), toCam(c, f.v[2][0], f.v[2][1], f.v[2][2]), toCam(c, f.v[3][0], f.v[3][1], f.v[3][2])];
      const S = clipProject(c, P3);
      if (!S) continue;
      let minx = 1e9, maxx = -1e9, miny = 1e9, maxy = -1e9;
      for (let i = 0; i < S.length; i += 2) { if (S[i] < minx) minx = S[i]; if (S[i] > maxx) maxx = S[i]; if (S[i + 1] < miny) miny = S[i + 1]; if (S[i + 1] > maxy) maxy = S[i + 1]; }
      if (maxx < -4 || minx > W + 4 || maxy < -4 || miny > H + 4) continue;
      items.push({ k: 0, f, S, d: Math.sqrt(vx * vx + vy * vy + vz * vz), front: dot > 0 });
    }
    for (const sg of [-1, 1]) { const dx = c.px - sg * PL, dy = c.py - 1, dz = c.pz; items.push({ k: 1, sg, d: Math.sqrt(dx * dx + dy * dy + dz * dz) - 1.5 }); }
    for (const F of FIGS) { const dx = c.px - F.x, dy = c.py - 0.9, dz = c.pz - F.z; items.push({ k: 2, F, d: Math.sqrt(dx * dx + dy * dy + dz * dz) }); }
    items.sort((a, b) => b.d - a.d);
    for (const it of items) {
      if (it.k === 0) drawFaceItem(g, c, it, t);
      else if (it.k === 1) drawGoal(g, c, it.sg);
      else drawFig(g, c, it.F, t);
    }
    if (CFG.pods > 0) drawGlows(g, c, t);
    if (CFG.rain) drawRain(g, c, t);
  }

  // ================================================================ UI helpers
  const FONT = 'Outfit';
  function setFont(g, w, s, ls) { g.font = `${w} ${s}px ${FONT}`; g.letterSpacing = `${ls || 0}px`; }
  function txt(g, s, x, y, o = {}) {
    setFont(g, o.w || 700, o.s || 24, o.ls || 0);
    g.textAlign = o.al || 'left';
    g.textBaseline = o.bl || 'alphabetic';
    g.fillStyle = o.c || '#fff';
    if (o.al === 'center' && o.ls) x += o.ls / 2;
    if (o.al === 'right' && o.ls) x += o.ls;
    g.fillText(s, x, y);
    return measure(g, s, o.w || 700, o.s || 24, o.ls || 0);
  }
  function measure(g, s, w, sz, ls) { setFont(g, w, sz, ls); return g.measureText(s).width - (ls || 0); }
  function shadowOn(g, blur = 18, a = 0.5, oy = 4) { g.shadowColor = `rgba(0,0,0,${a})`; g.shadowBlur = blur; g.shadowOffsetX = 0; g.shadowOffsetY = oy; }
  function shadowOff(g) { g.shadowColor = 'transparent'; g.shadowBlur = 0; g.shadowOffsetY = 0; }

  let FROST = null; // the frame's background, blurred, for the glass
  function glass(g, x, y, w, h, r, o = {}) {
    g.save();
    shadowOn(g, 46, 0.42, 16);
    g.beginPath(); g.roundRect(x, y, w, h, r); g.fillStyle = 'rgba(5,7,14,0.3)'; g.fill();
    shadowOff(g);
    g.clip();
    g.drawImage(FROST, 0, 0, W, H);
    g.fillStyle = o.tint || 'rgba(8,10,20,0.66)'; g.fillRect(x, y, w, h);
    const gr = g.createLinearGradient(0, y, 0, y + h);
    gr.addColorStop(0, 'rgba(255,255,255,0.11)'); gr.addColorStop(0.3, 'rgba(255,255,255,0.03)'); gr.addColorStop(1, 'rgba(255,255,255,0.0)');
    g.fillStyle = gr; g.fillRect(x, y, w, h);
    g.restore();
    g.beginPath(); g.roundRect(x + 0.75, y + 0.75, w - 1.5, h - 1.5, r); g.strokeStyle = o.stroke || 'rgba(255,255,255,0.16)'; g.lineWidth = 1.5; g.stroke();
  }

  const HOUSE = new Path2D('M31 75 L31 50 L54 31 L77 50 L77 75 Z');
  const PLAY = new Path2D('M49 53 L49 69 L63 61 Z');
  function tile(g, x, y, s, o = {}) {
    g.save();
    const gr = g.createLinearGradient(x, y, x + s, y + s); gr.addColorStop(0, VIOLET); gr.addColorStop(1, CORAL);
    g.beginPath(); g.roundRect(x, y, s, s, s * 0.24);
    if (o.shadow) shadowOn(g, s * 0.35, 0.45, s * 0.08);
    g.fillStyle = gr; g.fill();
    shadowOff(g);
    const rg = g.createRadialGradient(x + 0.28 * s, y + 0.18 * s, 0, x + 0.28 * s, y + 0.18 * s, 0.6 * s);
    rg.addColorStop(0, 'rgba(255,255,255,0.2)'); rg.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = rg; g.fill();
    if (o.shine !== undefined && o.shine > 0 && o.shine < 1) {
      g.save(); g.clip();
      const sx = x - s + o.shine * 3 * s;
      const sg = g.createLinearGradient(sx, y, sx + s * 0.6, y + s * 0.4);
      sg.addColorStop(0, 'rgba(255,255,255,0)'); sg.addColorStop(0.5, 'rgba(255,255,255,0.55)'); sg.addColorStop(1, 'rgba(255,255,255,0)');
      g.fillStyle = sg; g.fillRect(x, y, s, s);
      g.restore();
    }
    const wd = s * 0.62, k = wd / 51;
    const mx = x + (s - wd) / 2, my = y + (s - 49 * k) / 2;
    g.translate(mx - 28.5 * k, my - 28.5 * k); g.scale(k, k);
    g.lineJoin = 'round';
    g.fillStyle = '#fff'; g.strokeStyle = '#fff'; g.lineWidth = 5; g.fill(HOUSE); g.stroke(HOUSE);
    const gx0 = (x - mx) / k + 28.5, gy0 = (y - my) / k + 28.5;
    const cg = g.createLinearGradient(gx0, gy0, gx0 + s / k, gy0 + s / k); cg.addColorStop(0, VIOLET); cg.addColorStop(1, CORAL);
    g.fillStyle = cg; g.strokeStyle = cg; g.lineWidth = 2.6; g.fill(PLAY); g.stroke(PLAY);
    g.restore();
  }

  const IMG = {};
  function crest(g, side, cx, cy, size, a = 1) {
    const im = IMG[side]; if (!im) return;
    g.save(); g.globalAlpha *= a;
    shadowOn(g, size * 0.12, 0.5, size * 0.03);
    const s = size / Math.max(im.width, im.height);
    g.drawImage(im, cx - (im.width * s) / 2, cy - (im.height * s) / 2, im.width * s, im.height * s);
    g.restore();
  }

  // ---- player and manager pictures: FotMob's headshots, cut into circles once at the size they are shown
  const PHOTO = {}, BADGES = {};
  const shade = (h, k) => { const c = hex(h); return `rgb(${(c[0] * k) | 0},${(c[1] * k) | 0},${(c[2] * k) | 0})`; };
  function photoCircle(side, id, px) {
    if (!id || !PHOTO[id]) return null;
    px = Math.max(8, Math.round(px));
    const key = side + ':' + id + ':' + px;
    if (BADGES[key]) return BADGES[key];
    let src = PHOTO[id], sw = src.width, sh = src.height;
    while (sw >= px * 2.4) { // halve in steps: a clean, sharp small picture
      const c2 = mk(Math.round(sw / 2), Math.round(sh / 2)), g2 = c2.getContext('2d');
      g2.imageSmoothingQuality = 'high'; g2.drawImage(src, 0, 0, c2.width, c2.height);
      src = c2; sw = c2.width; sh = c2.height;
    }
    const K = TEAMS[side];
    const c = mk(px, px), g = c.getContext('2d');
    g.imageSmoothingQuality = 'high';
    const bg = g.createLinearGradient(0, 0, 0, px); bg.addColorStop(0, shade(K.c1, 0.9)); bg.addColorStop(1, K.c2);
    g.beginPath(); g.arc(px / 2, px / 2, px / 2, 0, TAU); g.fillStyle = bg; g.fill();
    g.save(); g.clip();
    const rg = g.createRadialGradient(px * 0.5, px * 0.18, 0, px * 0.5, px * 0.18, px * 0.8);
    rg.addColorStop(0, 'rgba(255,255,255,0.24)'); rg.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = rg; g.fillRect(0, 0, px, px);
    const d = px * 1.06;
    g.drawImage(src, (px - d) / 2, (px - d) / 2 + px * 0.045, d, (d * sh) / sw);
    g.restore();
    BADGES[key] = c;
    return c;
  }
  const initials = (name) => name.split(/[\s-]+/).filter(Boolean).map((w) => w[0]).slice(0, 2).join('').toUpperCase();
  function standIn(g, side, person, cx, cy, r, useInitials) {
    const K = TEAMS[side];
    const gr = g.createLinearGradient(0, cy - r, 0, cy + r); gr.addColorStop(0, K.c1); gr.addColorStop(1, K.c2);
    g.beginPath(); g.arc(cx, cy, r, 0, TAU); g.fillStyle = gr; g.fill();
    const rg = g.createRadialGradient(cx, cy - r * 0.6, 0, cx, cy - r * 0.6, r * 1.5);
    rg.addColorStop(0, 'rgba(255,255,255,0.22)'); rg.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = rg; g.fill();
    const label = useInitials ? initials(person.name || person.last || '') : String(person.n);
    txt(g, label, cx, cy + r * 0.05, { w: 800, s: Math.max(9, Math.round(r * (useInitials ? 0.66 : 0.82))), al: 'center', bl: 'middle', c: K.ink });
  }
  // a round headshot with a soft rim in the club's colour; a neat stand-in in its colours when there is no picture
  function headshot(g, side, person, cx, cy, r, o = {}) {
    if (!(r >= 1)) return false; // the first instant of a pop: nothing to draw yet
    headRim(g, side, cx, cy, r, o);
    return headFace(g, side, person, cx, cy, r, o);
  }
  // its two parts, drawn apart where they fade apart (the rim and its glow; the picture or the stand-in, and its ring)
  function headRim(g, side, cx, cy, r, o = {}) {
    const K = TEAMS[side];
    const rim = o.rim === undefined ? Math.max(1.5, r * 0.075) : o.rim;
    g.save();
    shadowOn(g, o.shadow === undefined ? r * 0.5 : o.shadow, 0.5, r * 0.1);
    g.beginPath(); g.arc(cx, cy, r + rim, 0, TAU); g.fillStyle = K.c1; g.fill();
    g.shadowColor = rgba(hex(K.c1), 0.65); g.shadowBlur = o.glow === undefined ? r * 0.45 : o.glow; g.shadowOffsetY = 0;
    g.fill();
    g.restore();
  }
  function headFace(g, side, person, cx, cy, r, o = {}) {
    const pc = photoCircle(side, person.id, o.px || r * 2);
    if (pc) g.drawImage(pc, cx - r, cy - r, 2 * r, 2 * r);
    else standIn(g, side, person, cx, cy, r, o.initials);
    g.beginPath(); g.arc(cx, cy, Math.max(0.5, r - 0.4), 0, TAU); g.lineWidth = Math.max(0.8, r * 0.035); g.strokeStyle = 'rgba(255,255,255,0.4)'; g.stroke();
    return !!pc;
  }

  function iconWeather(g, kind, cx, cy, s) {
    g.save();
    g.lineCap = 'round'; g.lineJoin = 'round';
    const cloud = (x, y, k, fill) => {
      g.beginPath();
      g.arc(x - 0.32 * k, y + 0.06 * k, 0.24 * k, PI * 0.5, PI * 1.5);
      g.arc(x - 0.05 * k, y - 0.12 * k, 0.3 * k, PI * 1.05, PI * 1.85);
      g.arc(x + 0.3 * k, y + 0.02 * k, 0.26 * k, PI * 1.4, PI * 0.5);
      g.closePath();
      g.fillStyle = fill; g.fill();
    };
    if (kind === 'sunny' || kind === 'cloud') {
      const sx = kind === 'cloud' ? cx - 0.18 * s : cx, sy = kind === 'cloud' ? cy - 0.16 * s : cy;
      g.strokeStyle = '#FFC94A'; g.lineWidth = s * 0.07;
      for (let i = 0; i < 8; i++) { const a = (i * PI) / 4; g.beginPath(); g.moveTo(sx + Math.cos(a) * 0.3 * s, sy + Math.sin(a) * 0.3 * s); g.lineTo(sx + Math.cos(a) * 0.44 * s, sy + Math.sin(a) * 0.44 * s); g.stroke(); }
      g.fillStyle = '#FFC94A'; g.beginPath(); g.arc(sx, sy, 0.2 * s, 0, TAU); g.fill();
      if (kind === 'cloud') cloud(cx + 0.12 * s, cy + 0.12 * s, s * 0.85, '#E9ECF4');
    } else if (kind === 'rain') {
      cloud(cx, cy - 0.12 * s, s, '#DCE2EE');
      g.strokeStyle = '#8FC2FF'; g.lineWidth = s * 0.07;
      for (let i = -1; i <= 1; i++) { g.beginPath(); g.moveTo(cx + i * 0.24 * s, cy + 0.2 * s); g.lineTo(cx + i * 0.24 * s - 0.07 * s, cy + 0.42 * s); g.stroke(); }
    }
    g.restore();
  }
  function iconWhistle(g, cx, cy, s, col) {
    g.save(); g.fillStyle = col; g.strokeStyle = col; g.lineWidth = s * 0.08; g.lineCap = 'round';
    g.beginPath(); g.arc(cx - 0.12 * s, cy + 0.08 * s, 0.28 * s, 0, TAU); g.fill();
    g.beginPath(); g.roundRect(cx - 0.12 * s, cy - 0.2 * s, 0.56 * s, 0.2 * s, 0.05 * s); g.fill();
    g.fillStyle = 'rgba(7,9,15,0.9)'; g.beginPath(); g.arc(cx - 0.12 * s, cy + 0.08 * s, 0.1 * s, 0, TAU); g.fill();
    g.lineWidth = s * 0.06; g.beginPath(); g.arc(cx - 0.47 * s, cy - 0.02 * s, 0.1 * s, 0, TAU); g.stroke();
    g.restore();
  }

  // ================================================================ the ground's own photograph (cfg.ground)
  // The stadium the game is played in: its photo (usually taken by day) fitted to cover the screen and graded once for the
  // sun at kick-off, from the same sun as the drawn ground's (lat, lon, kick-off): by day as it is with a little contrast;
  // the golden hour (sun 0-6 degrees up) warm; dusk (0-6 below) cooler with a warm glow left over the roofs; night (below
  // 6) a good deal darker and blue, the floodlights glowing along the stands' roofs and the photo's own lights blooming.
  // A wet night has its rain over it, as the drawn ground has.
  function groundPhase() { const a = SOL.alt; return a > 6 ? 'day' : a > 0 ? 'golden' : a > -6 ? 'dusk' : 'night'; }
  function groundPhoto(im) {
    const c = mk(W, H), g = c.getContext('2d');
    const k = Math.max(W / im.width, H / im.height), dw = im.width * k, dh = im.height * k;
    const base = mk(W, H), bg = base.getContext('2d');
    bg.imageSmoothingQuality = 'high';
    bg.drawImage(im, (W - dw) / 2, (H - dh) / 2, dw, dh);
    const phase = groundPhase();
    const F = { day: 'contrast(1.07) saturate(1.06) brightness(0.95)', golden: 'contrast(1.06) saturate(1.12) brightness(0.97)',
      dusk: 'brightness(0.8) contrast(1.06) saturate(0.86)', night: 'brightness(0.6) contrast(1.12) saturate(0.8)' }[phase];
    g.filter = F; g.drawImage(base, 0, 0); g.filter = 'none';
    const vert = (stops) => { const gr = g.createLinearGradient(0, 0, 0, H); for (const [o, col] of stops) gr.addColorStop(o, col); return gr; };
    const over = (op, style) => { g.globalCompositeOperation = op; g.fillStyle = style; g.fillRect(0, 0, W, H); g.globalCompositeOperation = 'source-over'; };
    // the floodlights: a soft line of light along the roofs (about a fifth of the way down a ground photo), lamps in it
    const floods = (s) => {
      g.save(); g.globalCompositeOperation = 'screen';
      const band = g.createLinearGradient(0, H * 0.06, 0, H * 0.36);
      band.addColorStop(0, 'rgba(210,225,255,0)'); band.addColorStop(0.45, `rgba(225,235,255,${0.12 * s})`); band.addColorStop(1, 'rgba(210,225,255,0)');
      g.fillStyle = band; g.fillRect(0, 0, W, H * 0.45);
      for (let i = 0; i < 9; i++) {
        const x = 110 + i * 212 + (i % 2 ? 26 : -18), y = H * (0.17 + 0.03 * Math.sin(i * 1.7));
        g.save(); g.translate(x, y); g.scale(1, 0.38);
        const lg = g.createRadialGradient(0, 0, 0, 0, 0, 150);
        lg.addColorStop(0, `rgba(255,248,230,${0.42 * s})`); lg.addColorStop(0.22, `rgba(235,240,255,${0.13 * s})`); lg.addColorStop(1, 'rgba(220,230,255,0)');
        g.fillStyle = lg; g.fillRect(-150, -150, 300, 300);
        g.restore();
      }
      g.restore();
    };
    // the photo's own bright parts glowing (as the drawn ground's bloom)
    const bloom = (a) => {
      const q = mk(W / 4, H / 4), qg = q.getContext('2d');
      qg.fillStyle = '#000'; qg.fillRect(0, 0, W / 4, H / 4);
      qg.filter = 'brightness(0.5) contrast(5) blur(7px)'; qg.drawImage(c, 0, 0, W / 4, H / 4); qg.filter = 'none';
      g.save(); g.globalCompositeOperation = 'screen'; g.globalAlpha = a; g.drawImage(q, 0, 0, W, H); g.restore();
    };
    if (phase === 'day') {
      bloom(0.12);
    } else if (phase === 'golden') {
      over('soft-light', 'rgba(255,150,60,0.5)');
      over('screen', vert([[0, 'rgba(255,170,80,0.22)'], [0.45, 'rgba(255,150,70,0.06)'], [1, 'rgba(255,140,60,0)']]));
      bloom(0.22);
    } else if (phase === 'dusk') {
      over('multiply', 'rgba(150,170,225,0.55)');
      over('screen', vert([[0, 'rgba(255,135,80,0.26)'], [0.3, 'rgba(255,120,90,0.1)'], [0.55, 'rgba(0,0,0,0)']]));
      floods(0.35); bloom(0.32);
    } else {
      over('multiply', 'rgba(105,130,205,0.7)');
      over('source-over', vert([[0, 'rgba(4,8,22,0.45)'], [0.35, 'rgba(4,8,22,0.12)'], [0.7, 'rgba(4,8,22,0)'], [1, 'rgba(4,8,22,0.18)']]));
      floods(1); bloom(0.5);
    }
    window.GROUND_PHASE = phase;
    return c;
  }
  // the rain's streaks over the photo (the drawn ground's own, without its drops on the pitch)
  function rainStreaks(g, t) {
    g.save(); g.globalCompositeOperation = 'lighter'; g.lineCap = 'round';
    for (const L of [{ n: 1100, len: 14, sp: 1250, a: 0.07, w: 1, seed: 3 }, { n: 520, len: 28, sp: 1850, a: 0.1, w: 1.3, seed: 5 }, { n: 120, len: 64, sp: 2700, a: 0.11, w: 2.2, seed: 9 }]) {
      g.strokeStyle = `rgba(200,214,236,${L.a})`; g.lineWidth = L.w; g.beginPath();
      for (let i = 0; i < L.n; i++) {
        const x0 = hash(i, L.seed) * (W + 300) - 150, y0 = hash(i + 9999, L.seed) * (H + 240);
        const y = ((y0 + t * L.sp) % (H + 240)) - 120, x = x0 + y * 0.12;
        g.moveTo(x, y); g.lineTo(x + 0.12 * L.len, y + L.len);
      }
      g.stroke();
    }
    g.restore();
  }
  // its credit, very small in a corner on a faint dark backing (x, y: the line's right or left end and baseline)
  function groundCredit(text, x, y, align) {
    const s = String(text || '').slice(0, 140);
    if (!s) return null;
    setFont(ctx, 500, 15, 0.2);
    const tw = ctx.measureText(s).width, w = Math.ceil(tw) + 24, x0 = align === 'right' ? x - w + 12 : x - 12;
    const c = mk(w, 34), g = c.getContext('2d');
    g.translate(-x0, -(y - 22));
    g.beginPath(); g.roundRect(align === 'right' ? x - tw - 8 : x - 8, y - 16, tw + 16, 22, 6); g.fillStyle = 'rgba(0,0,0,0.32)'; g.fill();
    shadowOn(g, 6, 0.75, 1);
    txt(g, s, align === 'right' ? x : x, y, { w: 500, s: 15, ls: 0.2, al: align === 'right' ? 'right' : 'left', c: 'rgba(255,255,255,0.62)' });
    shadowOff(g);
    return { c, x: x0, y: y - 22 };
  }

  // ======================================================================================================================
  // The first design's pre-match line-ups, live (scratchpad lineups-mock/lineups.js): the ground drawn once on the device,
  // lit for the ground's place, date and kick-off, then the Whitebrook Road Sports ident, the title card, each eleven on a
  // formation board with every player's picture, the referee and a live countdown, and a brand wipe back to the channel.
  // It follows head.js (the shared ground, the same as the classic line-ups'): DATA and PLAN come from stadium-host.js.
  // ======================================================================================================================

  // ---- the sequence, made from what the game has: the officials' page only with a referee or a kick-off still to come
  const KO_AT = +PLAN.at || 0;
  const REF = String(DATA.referee || '');
  const countdownOn = () => KO_AT > Date.now() + 3000;
  const SEQ = (function () {
    const out = []; let t = 0;
    const add = (n, d) => { out.push({ n, a: t, b: t + d, d }); t += d; };
    add('ident', 3.6); add('title', 6.0); add('home', 12.0); add('away', 12.0);
    if (REF || countdownOn()) add('officials', 4.8);
    add('outro', 1.6);
    return out;
  })();
  const DUR = SEQ[SEQ.length - 1].b;
  const seg = (n) => SEQ.find((s) => s.n === n);
  function xiTimes(s) {
    const k = s.d / 12;
    return { boardIn: 0.3 * k, boardLand: 1.1 * k, pop0: 1.5 * k, popGap: 0.45 * k, mgr: 6.5 * k, subs: 7.0 * k, out0: s.d - 0.6, out1: s.d };
  }
  function posTag(p) { if (p.gk || p.pos === 11) return 'GK'; if (p.pos < 60) return 'DF'; if (p.pos < 100) return 'MF'; return 'FW'; }
  function lineOf(team) {
    const ys = [];
    for (const p of team.xi) if (!ys.some((y) => Math.abs(y - p.y) < 0.06)) ys.push(p.y);
    ys.sort((a, b) => a - b);
    return (p) => ys.findIndex((y) => Math.abs(y - p.y) < 0.06);
  }
  // a line of text no wider than max: smaller, down to min (the size it was drawn at)
  function fitSize(s, w, size, ls, max, min) {
    let z = size;
    while (z > min && measure(ctx, s, w, z, ls) > max) z -= 1;
    return z;
  }

  // ---- the kick-off, the weather and the sunset, for the title's bar
  const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  const hm = (ms) => { const d = new Date(ms); return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); };
  function sunsetMs() {
    if (!KO_AT) return 0;
    const d = new Date(KO_AT); d.setHours(12, 0, 0, 0);
    let prev = solar(d.getTime(), DATA.lat, DATA.lon).alt;
    for (let m = 5; m <= 12 * 60; m += 5) {
      const ms = d.getTime() + m * 60000, alt = solar(ms, DATA.lat, DATA.lon).alt;
      if (prev > -0.833 && alt <= -0.833) return ms;
      prev = alt;
    }
    return 0;
  }
  const SUNSET = sunsetMs();
  function weatherCell() {
    const w = DATA.weather;
    if (!w) return null;
    let temp = null, text = '', wind = '', icon = '';
    if (typeof w === 'object') { temp = w.temp; text = w.text || ''; wind = w.wind || ''; icon = w.icon || ''; }
    else {
      const s = String(w), m = /(-?\d+)\s*°/.exec(s);
      if (m) temp = +m[1];
      text = s.replace(/(-?\d+)\s*°\s*[CF]?/, '').replace(/^[\s·,-]+|[\s·,-]+$/g, '');
    }
    text = text ? text.charAt(0).toUpperCase() + text.slice(1) : '';
    if (!icon) icon = /rain|shower|drizzle|wet|storm/i.test(text) ? 'rain' : /sun|clear|bright/i.test(text) ? 'sunny' : 'cloud';
    if (temp == null && !text) return null;
    return { lab: 'WEATHER', val: temp == null ? '' : `${Math.round(temp)}°`, l1: text || 'At the ground', l2: wind, icon };
  }
  const CELLS = (function () {
    const out = [];
    const d = KO_AT ? new Date(KO_AT) : null;
    out.push({ lab: 'KICK-OFF', val: PLAN.ko, l1: d ? DAYS[d.getDay()] : 'Today', l2: d ? `${d.getDate()} ${MONTHS[d.getMonth()]}` : '' });
    const wc = weatherCell(); if (wc) out.push(wc);
    if (SUNSET && KO_AT) {
      const note = SUNSET <= KO_AT ? ['Under the', 'floodlights'] : SUNSET < KO_AT + 45 * 60000 ? ['Floodlights on', 'by half time']
        : SUNSET < KO_AT + 110 * 60000 ? ['Floodlights on', 'for the second half'] : ['Daylight', 'all game'];
      out.push({ lab: 'SUNSET', val: hm(SUNSET), l1: note[0], l2: note[1] });
    }
    return out;
  })();

  // the formation board: a pitch tilted back, in a little 3D of its own
  function boardProj(B, u, v) {
    const ca = Math.cos(B.a), sa = Math.sin(B.a);
    const Y = v * ca, Z = v * sa;
    const den = B.D + Z;
    return [B.cx + (B.F * u) / den, B.cy - (B.F * Y) / den, B.D / den];
  }
  const PHOTO_R = 37;
  const formUV = (p) => [(p.x - 0.5) * 68 * 0.95, -52.5 + (0.02 + p.y * 0.92) * 105];
  function boardCx(side) { return side === 'away' ? 1380 : 540; }
  const LABELS = {};
  function labelLayout(side) {
    if (LABELS[side]) return LABELS[side];
    const team = DATA[side], ln = lineOf(team);
    const B = { cx: boardCx(side), cy: 492, F: 1800, D: 220, a: 38 * DEG };
    const L = team.xi.map((p, i) => {
      const [pu, pv] = formUV(p);
      const q = boardProj(B, pu, pv);
      const size = Math.round(20 * q[2] + 1);
      return { i, line: ln(p), x: q[0], q: q[2], size, dy: 0, w: measure(ctx, p.last, 700, size, 0) };
    });
    const lines = {};
    for (const l of L) (lines[l.line] = lines[l.line] || []).push(l);
    for (const k in lines) {
      const row = lines[k].sort((a, b) => a.x - b.x);
      for (let j = 1; j < row.length; j++) {
        const a = row[j - 1], b = row[j];
        let guard = 0;
        while (b.x - a.x < (a.w + b.w) / 2 + 26 && guard++ < 8) {
          const big = a.w > b.w ? a : b;
          if (big.size <= 15) break;
          big.size -= 1; big.w = measure(ctx, team.xi[big.i].last, 700, big.size, 0);
        }
        if (b.x - a.x < (a.w + b.w) / 2 + 10 && a.dy === 0) b.dy = 22;
      }
    }
    const out = [];
    for (const l of L) out[l.i] = l;
    LABELS[side] = out;
    return out;
  }

  function drawBackHint(g, a) {
    if (a <= 0) return;
    g.save(); g.globalAlpha = a * 0.9;
    const tw2 = measure(g, 'Skip line-ups', 500, 16, 0.5);
    const kw = measure(g, 'BACK', 800, 12, 2.5) + 18;
    const x1 = 1824, y = 1010;
    shadowOn(g, 10, 0.6, 2);
    txt(g, 'Skip line-ups', x1, y, { w: 500, s: 16, ls: 0.5, al: 'right', c: 'rgba(255,255,255,0.78)' });
    shadowOff(g);
    const kx = x1 - tw2 - 12 - kw;
    g.beginPath(); g.roundRect(kx, y - 19, kw, 25, 6); g.strokeStyle = 'rgba(255,255,255,0.6)'; g.lineWidth = 1.5; g.stroke();
    txt(g, 'BACK', kx + kw / 2, y - 6, { w: 800, s: 12, ls: 2.5, al: 'center', bl: 'middle', c: 'rgba(255,255,255,0.85)' });
    g.restore();
  }

  // ================================================================ the background: the ground drawn once, then reused
  const BG = { ident: [0.35, 0.32], title: [0, 0.1], home: [1, 0.36], away: [1, 0.36], officials: [0.25, 0.14], outro: [0, 0] };
  function bgParams(t) {
    let i = SEQ.findIndex((s) => t >= s.a && t < s.b);
    if (i < 0) i = SEQ.length - 1;
    const s = SEQ[i];
    if (s.n === 'ident') { const k = sstep(0.2, 2.3, t - s.a); return { blur: lerp(1, 0.35, k), dim: lerp(0.5, 0.32, k) }; }
    const prev = i > 0 ? BG[SEQ[i - 1].n] : BG[s.n];
    const cur = BG[s.n];
    const k = sstep(0, 0.75, t - s.a);
    return { blur: lerp(prev[0], cur[0], k), dim: lerp(prev[1], cur[1], k) };
  }
  let BGSHARP = null, BGSOFT = null, BGCAM = null;           // the ground, sharp and blurred, each with its vignette
  let GROUND_PHOTO = false;                                   // the ground is the stadium's own photograph (cfg.ground)
  const quarterC = mk(W / 4, H / 4), qg = quarterC.getContext('2d');
  function bloomOn(g, src) {
    if (!(CFG.bloom > 0)) return;
    qg.filter = 'none'; qg.fillStyle = '#000'; qg.fillRect(0, 0, W / 4, H / 4);
    qg.filter = 'brightness(0.5) contrast(5) blur(7px)';
    qg.drawImage(src, 0, 0, W / 4, H / 4);
    qg.filter = 'none';
    g.save(); g.globalCompositeOperation = 'screen'; g.globalAlpha = CFG.bloom; g.drawImage(quarterC, 0, 0, W, H); g.restore();
  }
  function grade(g) {
    if (CFG.grade === 'night') { g.fillStyle = 'rgba(20,40,70,0.07)'; g.globalCompositeOperation = 'screen'; g.fillRect(0, 0, W, H); g.globalCompositeOperation = 'source-over'; }
    if (CFG.grade === 'sunset') { g.fillStyle = 'rgba(80,30,40,0.06)'; g.globalCompositeOperation = 'screen'; g.fillRect(0, 0, W, H); g.globalCompositeOperation = 'source-over'; }
  }
  function buildBackground(photo) {
    const sc = mk(W, H), sg = sc.getContext('2d');
    BGCAM = beautyCam(9);
    if (photo) {   // the stadium's own photograph, graded for the sun at kick-off (head.js), its rain over it on a wet night
      sg.drawImage(groundPhoto(photo), 0, 0);
      if (CFG.rain) rainStreaks(sg, 9);
      GROUND_PHOTO = true;
    } else {
      renderScene(sg, BGCAM, 9);
      grade(sg); bloomOn(sg, sc);
    }
    const vig = mk(W, H), vg = vig.getContext('2d');
    const gr = vg.createRadialGradient(W / 2, H * 0.48, H * 0.35, W / 2, H * 0.48, H * 1.05);
    gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(1, 'rgba(0,0,0,0.42)');
    vg.fillStyle = gr; vg.fillRect(0, 0, W, H);
    BGSHARP = mk(W, H);
    const s2 = BGSHARP.getContext('2d'); s2.drawImage(sc, 0, 0); s2.drawImage(vig, 0, 0);
    const half = mk(W / 2, H / 2), hg = half.getContext('2d'); hg.drawImage(sc, 0, 0, W / 2, H / 2);
    BGSOFT = mk(W / 2, H / 2);
    const so = BGSOFT.getContext('2d'); so.filter = 'blur(4.1px)'; so.drawImage(half, 0, 0); so.filter = 'none'; so.drawImage(vig, 0, 0, W / 2, H / 2);
    const fr = mk(W / 2, H / 2), fg = fr.getContext('2d');
    fg.filter = 'blur(16px)'; fg.fillStyle = '#000'; fg.fillRect(0, 0, W / 2, H / 2); fg.drawImage(half, 0, 0); fg.filter = 'none';
    FROST = fr;
  }
  // ================================================================ the frame, as compositor layers (layers.js)
  // Every part is drawn once, when the line-ups are made ready, into a canvas of its own with its shadow (a sprite), and a
  // frame only places the sprites by transform and opacity, as the strap page's elements are placed. What changes as it
  // plays goes in small canvases of its own, drawn only while it changes: the logo's shine, the board as it tilts down,
  // the rings as the pictures pop, the countdown (once a second), and the rain on a wet night.
  const Lmul = LK.mul, LT = LK.T;
  const SPR = {};
  const bake = (x, y, w, h, draw) => {
    const c = mk(Math.max(1, Math.ceil(w)), Math.max(1, Math.ceil(h))), g = c.getContext('2d');
    g.translate(-x, -y); draw(g);
    return { c, x, y };
  };
  const at = (s, M, a, blend, top) => { if (s) LK.put(s.c, Lmul(M, LT(s.x, s.y)), a, blend, top); };
  const fillAll = (col, a, blend) => LK.put(LK.fill(col), LK.rect(0, 0, W, H, 4, 4), a, blend);
  const dynClear = (c) => { const g = c._g; g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over'; g.clearRect(0, 0, c.width, c.height); return g; };

  // ---- the logo tile in three parts: its body (with its shadow), the shine across it, its mark (as tile() draws them)
  function tileBody(g, x, y, s, shadow) {
    g.save();
    const gr = g.createLinearGradient(x, y, x + s, y + s); gr.addColorStop(0, VIOLET); gr.addColorStop(1, CORAL);
    g.beginPath(); g.roundRect(x, y, s, s, s * 0.24);
    if (shadow) shadowOn(g, s * 0.35, 0.45, s * 0.08);
    g.fillStyle = gr; g.fill();
    shadowOff(g);
    const rg = g.createRadialGradient(x + 0.28 * s, y + 0.18 * s, 0, x + 0.28 * s, y + 0.18 * s, 0.6 * s);
    rg.addColorStop(0, 'rgba(255,255,255,0.2)'); rg.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = rg; g.fill();
    g.restore();
  }
  function tileShine(g, x, y, s, shine) {
    g.save();
    g.beginPath(); g.roundRect(x, y, s, s, s * 0.24); g.clip();
    const sx = x - s + shine * 3 * s;
    const sg = g.createLinearGradient(sx, y, sx + s * 0.6, y + s * 0.4);
    sg.addColorStop(0, 'rgba(255,255,255,0)'); sg.addColorStop(0.5, 'rgba(255,255,255,0.55)'); sg.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = sg; g.fillRect(x, y, s, s);
    g.restore();
  }
  function tileMark(g, x, y, s) {
    g.save();
    const wd = s * 0.62, k = wd / 51;
    const mx = x + (s - wd) / 2, my = y + (s - 49 * k) / 2;
    g.translate(mx - 28.5 * k, my - 28.5 * k); g.scale(k, k);
    g.lineJoin = 'round';
    g.fillStyle = '#fff'; g.strokeStyle = '#fff'; g.lineWidth = 5; g.fill(HOUSE); g.stroke(HOUSE);
    const gx0 = (x - mx) / k + 28.5, gy0 = (y - my) / k + 28.5;
    const cg = g.createLinearGradient(gx0, gy0, gx0 + s / k, gy0 + s / k); cg.addColorStop(0, VIOLET); cg.addColorStop(1, CORAL);
    g.fillStyle = cg; g.strokeStyle = cg; g.lineWidth = 2.6; g.fill(PLAY); g.stroke(PLAY);
    g.restore();
  }
  // a line of letters whose spacing opens and closes (the ident's TEAM LINE-UPS): each letter a sprite and its shadow
  // another (all the shadows under all the letters, as one drawing's would be), placed by the spacing of the moment
  function glyphRun(s, w, size, cx, y, lsRest, col, sh) {
    setFont(ctx, w, size, 0);
    const chars = Array.from(s), adv = [];
    let pre = '';
    for (const ch of chars) { adv.push(ctx.measureText(pre).width); pre += ch; }
    const G = { n: chars.length, adv, base: ctx.measureText(s).width, cx, list: [] };
    chars.forEach((ch, i) => {
      if (ch === ' ') return;
      const xr = glyphX(G, i, lsRest), x0 = Math.floor(xr) - 40, y0 = Math.floor(y - size * 1.2) - 30, gw = size * 1.6 + 80, gh = size * 1.6 + 60;
      const font = (g) => { setFont(g, w, size, 0); g.textAlign = 'left'; g.textBaseline = 'alphabetic'; g.fillStyle = col; };
      const fill = bake(x0, y0, gw, gh, (g) => { font(g); g.fillText(ch, xr, y); });
      const shad = bake(x0, y0, gw, gh, (g) => {
        font(g); g.shadowColor = `rgba(0,0,0,${sh[1]})`; g.shadowBlur = sh[0]; g.shadowOffsetX = 10000; g.shadowOffsetY = sh[2];
        g.fillText(ch, xr - 10000, y);
      });
      G.list.push({ i, xr, fill, shad });
    });
    return G;
  }
  function glyphX(G, i, ls) { return G.cx - G.base / 2 - ((G.n - 1) * ls) / 2 + G.adv[i] + i * ls; }
  function glyphLayers(G, ls, M, a) {
    for (const L of G.list) at(L.shad, Lmul(M, LT(glyphX(G, L.i, ls) - L.xr, 0)), a);
    for (const L of G.list) at(L.fill, Lmul(M, LT(glyphX(G, L.i, ls) - L.xr, 0)), a);
  }

  // ---- the ident
  function bakeIdent() {
    const cx = 960, cy = 488, ts = 158;
    const w1 = measure(ctx, 'WHITEBROOK ROAD', 800, 84, 3), w2 = measure(ctx, 'SPORTS', 300, 34, 22);
    const tb = Math.max(w1, w2), total = ts + 38 + tb, x0 = cx - total / 2, tx = x0 + ts + 38;
    const I = { cx, cy, ts, tb, total, x0, tx };
    I.glow = bake(200, 0, 1520, H, (g) => {
      const rg = g.createRadialGradient(cx, cy, 0, cx, cy, 760);
      rg.addColorStop(0, 'rgba(109,40,242,0.28)'); rg.addColorStop(0.5, 'rgba(255,107,107,0.08)'); rg.addColorStop(1, 'rgba(0,0,0,0)');
      g.fillStyle = rg; g.fillRect(200, 0, 1520, H);
    });
    I.body = bake(-ts / 2 - 90, -ts / 2 - 90, ts + 180, ts + 190, (g) => tileBody(g, -ts / 2, -ts / 2, ts, true));
    I.mark = bake(-ts / 2, -ts / 2, ts, ts, (g) => tileMark(g, -ts / 2, -ts / 2, ts));
    I.words = bake(tx - 60, cy - 130, tb + 140, 260, (g) => {
      shadowOn(g, 24, 0.45, 4);
      txt(g, 'WHITEBROOK ROAD', tx, cy + 4, { w: 800, s: 84, ls: 3 });
      txt(g, 'SPORTS', tx + 4, cy + 58, { w: 300, s: 34, ls: 22, c: LILAC });
      shadowOff(g);
    });
    I.rule = bake(cx - total / 2, cy + 128, total, 3, (g) => {
      const gr = g.createLinearGradient(cx - total / 2, 0, cx + total / 2, 0);
      gr.addColorStop(0, 'rgba(109,40,242,0)'); gr.addColorStop(0.25, VIOLET); gr.addColorStop(0.75, CORAL); gr.addColorStop(1, 'rgba(255,107,107,0)');
      g.fillStyle = gr; g.fillRect(cx - total / 2, cy + 128, total, 3);
    });
    I.lineups = glyphRun('TEAM LINE-UPS', 600, 24, cx, cy + 186, 10, 'rgba(255,255,255,0.9)', [14, 0.5, 2]);
    I.flare = bake(-520, cy - 19, 1040, 38, (g) => {
      g.translate(0, cy); g.scale(1, 0.035); g.translate(0, -cy);
      const fg = g.createRadialGradient(0, cy, 0, 0, cy, 520);
      fg.addColorStop(0, 'rgba(255,220,235,0.28)'); fg.addColorStop(1, 'rgba(255,220,235,0)');
      g.fillStyle = fg; g.fillRect(-520, cy - 520, 1040, 1040);
    });
    return I;
  }
  function identLayers(s, u) {
    const I = SPR.ident, D = s.d, cx = I.cx, cy = I.cy;
    const kout = eIn3(ramp(D - 0.7, D, u)), a = 1 - kout;
    if (a <= 0) return;
    const sc0 = 1 - 0.05 * kout, GI = [sc0, 0, 0, sc0, cx - cx * sc0, cy - kout * 18 - cy * sc0];
    const kg = sstep(0.1, 1.2, u) * a;
    if (kg > 0) at(I.glow, GI, kg);
    const kt = ramp(0.12, 0.85, u);
    if (kt > 0) {
      const sc = 0.55 + 0.45 * eOutBack(kt, 2.0), al = a * clamp(kt * 2.5);
      const M = Lmul(GI, Lmul(LT(I.x0 + I.ts / 2, cy), Lmul(LK.R((1 - eOut3(kt)) * -12 * DEG), [sc, 0, 0, sc, 0, 0])));
      at(I.body, M, al);
      const shine = ramp(0.8, 1.5, u);
      if (shine > 0 && shine < 1) {
        const c = LK.dyn('shine', I.ts, I.ts), g = dynClear(c);
        tileShine(g, 0, 0, I.ts, shine);
        LK.put(c, Lmul(M, LT(-I.ts / 2, -I.ts / 2)), al);
      }
      at(I.mark, M, al);
    }
    const kw = eOut3(ramp(0.45, 1.3, u));
    if (kw > 0) {
      LK.clip('ident', Lmul(GI, LT(I.tx - 12, cy - 130)), (I.tb + 60) * kw, 260);
      at(I.words, Lmul(GI, LT(-(1 - kw) * 34, 0)), a);
      LK.endClip();
    }
    const kr = eInOut3(ramp(0.9, 1.6, u));
    if (kr > 0) at(I.rule, Lmul(GI, [kr, 0, 0, 1, cx - cx * kr, 0]), a);
    const ks = eOut3(ramp(1.1, 1.8, u));
    if (ks > 0) glyphLayers(I.lineups, lerp(18, 10, ks), GI, a * ks);
    const kf = ramp(0.55, 1.5, u);
    if (kf > 0 && kf < 1) at(I.flare, Lmul(GI, LT(lerp(-200, W + 200, eInOut3(kf)), 0)), a, 'lighter');
  }

  // ---- the title card
  function bakeTitle() {
    const T = { cy: 418 };
    T.shade = bake(0, 560, W, H - 560, (g) => {
      const sh = g.createLinearGradient(0, 560, 0, H);
      sh.addColorStop(0, 'rgba(3,5,10,0)'); sh.addColorStop(1, 'rgba(3,5,10,0.55)');
      g.fillStyle = sh; g.fillRect(0, 560, W, H - 560);
    });
    if (DATA.league) {
      const label = DATA.league.toUpperCase(), lw = measure(ctx, label, 700, 18, 5), cw = lw + 56, cx0 = 960 - cw / 2;
      T.chip = bake(cx0 - 90, 150 - 80, cw + 180, 46 + 190, (g) => {
        glass(g, cx0, 150, cw, 46, 23);
        txt(g, label, 960, 150 + 23 + 1, { w: 700, s: 18, ls: 5, al: 'center', bl: 'middle' });
      });
    }
    for (const [side, x] of [['home', 676], ['away', 1244]]) {
      T['crest:' + side] = bake(x - 178, T.cy - 178, 356, 366, (g) => crest(g, side, x, T.cy, 236));
      const nm = DATA[side].name.toUpperCase(), z = fitSize(nm, 800, 60, 2, 520, 30);
      T['name:' + side] = bake(x - 330, 640 - 100, 660, 150, (g) => { shadowOn(g, 22, 0.6, 4); txt(g, nm, x, 640, { w: 800, s: z, ls: 2, al: 'center' }); shadowOff(g); });
    }
    T.v = bake(960 - 70, T.cy - 70, 140, 150, (g) => {
      g.translate(960, T.cy);
      g.beginPath(); g.arc(0, 0, 36, 0, TAU); g.lineWidth = 2;
      const vg = g.createLinearGradient(-36, -36, 36, 36); vg.addColorStop(0, VIOLET); vg.addColorStop(1, CORAL);
      g.strokeStyle = vg; g.stroke();
      g.fillStyle = 'rgba(7,9,15,0.45)'; g.fill();
      shadowOn(g, 10, 0.5, 2);
      txt(g, 'v', 0, -3, { w: 300, s: 44, al: 'center', bl: 'middle' });
      shadowOff(g);
    });
    if (DATA.stadium) {
      T.live = bake(0, 630, W, 180, (g) => {
        shadowOn(g, 16, 0.6, 3);
        txt(g, 'LIVE FROM', 960, 712, { w: 700, s: 17, ls: 7, al: 'center', c: LILAC });
        const st = DATA.stadium.toUpperCase();
        txt(g, st, 960, 760, { w: 700, s: fitSize(st, 700, 42, 3, 1500, 24), ls: 3, al: 'center' });
        shadowOff(g);
      });
    }
    const n = CELLS.length, bw = n === 3 ? 1240 : n === 2 ? 860 : 440, bx = 960 - bw / 2, by = 818, bh = 116, cw = bw / n;
    T.box = bake(bx - 90, by - 80, bw + 180, bh + 190, (g) => {
      glass(g, bx, by, bw, bh, 22);
      for (let i = 1; i < n; i++) { g.fillStyle = 'rgba(255,255,255,0.12)'; g.fillRect(bx + i * cw, by + 22, 1.5, bh - 44); }
    });
    T.cells = CELLS.map((c, i) => bake(bx + i * cw, by, cw, bh, (g) => {
      const x = bx + i * cw + 36;
      txt(g, c.lab, x, by + 38, { w: 700, s: 14, ls: 5, c: LILAC });
      let vx = x;
      if (c.icon) { iconWeather(g, c.icon, x + 22, by + 76, 46); vx = x + 56; }
      const vw = c.val ? txt(g, c.val, vx, by + 92, { w: 800, s: 46, ls: 0.5 }) : -18;
      const room = cw - (vx - x) - vw - 18 - 48;
      if (c.l2) {
        txt(g, c.l1, vx + vw + 18, by + 70, { w: 600, s: fitSize(c.l1, 600, 19, 0, room, 13), c: '#fff' });
        txt(g, c.l2, vx + vw + 18, by + 94, { w: 400, s: fitSize(c.l2, 400, 17, 0, room, 12), c: 'rgba(255,255,255,0.7)' });
      } else txt(g, c.l1, vx + vw + 18, by + 83, { w: 600, s: fitSize(c.l1, 600, 19, 0, room, 13), c: '#fff' });
    }));
    return T;
  }
  function titleLayers(s, u) {
    const T = SPR.title, D = s.d;
    const kout = eIn3(ramp(D - 0.55, D, u)), A = 1 - kout;
    if (A <= 0) return;
    at(T.shade, LK.I, A * sstep(0, 0.6, u));
    const kc = eOut3(ramp(0.15, 0.75, u));
    if (kc > 0 && T.chip) at(T.chip, LT(0, -(1 - kc) * 16), A * kc);
    const kx = eOutExpo(ramp(0.12, 1.05, u));
    const hx = lerp(560, 676, kx) - kout * 60, ax = lerp(1360, 1244, kx) + kout * 60;
    at(T['crest:home'], LT(hx - 676, 0), A * clamp(kx * 1.5));
    at(T['crest:away'], LT(ax - 1244, 0), A * clamp(kx * 1.5));
    const kv = eOutBack(ramp(0.5, 1.0, u), 2);
    if (kv > 0) at(T.v, [kv, 0, 0, kv, 960 - 960 * kv, T.cy - T.cy * kv], A * clamp(kv));
    const kn = eOut3(ramp(0.45, 1.15, u));
    if (kn > 0) { at(T['name:home'], LT(hx - 676, (1 - kn) * 26), A * kn); at(T['name:away'], LT(ax - 1244, (1 - kn) * 26), A * kn); }
    const kvn = eOut3(ramp(0.75, 1.4, u));
    if (kvn > 0 && T.live) at(T.live, LT(0, (1 - kvn) * 14), A * kvn);
    const kb = eOutExpo(ramp(0.95, 1.6, u));
    if (kb > 0) {
      const dy = (1 - kb) * 30 + kout * 20;
      at(T.box, LT(0, dy), A * clamp(kb * 1.4));
      CELLS.forEach((c, i) => { const ki = eOut3(ramp(1.05 + i * 0.12, 1.5 + i * 0.12, u)); at(T.cells[i], LT(0, dy + (1 - ki) * 10), A * ki); });
    }
  }

  // ---- a team's page: the board (tilting down onto its place, then still), the panel and its rows, the subs, the hint
  const landedBoard = (side) => ({ cx: boardCx(side), cy: 492, F: 1800, D: 220, a: 38 * DEG });
  // the board's face: its glass over the ground, the club's colour, the stripes, its edge; and its markings (alpha)
  function boardFace(g, side, B, marks) {
    const K = TEAMS[side];
    const corner = (u2, v2) => boardProj(B, u2, v2);
    const c00 = corner(-36.5, -55), c10 = corner(36.5, -55), c11 = corner(36.5, 55), c01 = corner(-36.5, 55);
    polyPath(g, [c00, c10, c11, c01]);
    g.fillStyle = 'rgba(6,9,18,0.62)'; g.fill();
    g.save(); polyPath(g, [c00, c10, c11, c01]); g.clip();
    g.drawImage(FROST, 0, 0, W, H);
    g.fillStyle = 'rgba(7,10,20,0.6)'; g.fillRect(0, 0, W, H);
    const c1 = hex(K.c1);
    const tg = g.createLinearGradient(0, c00[1], 0, c01[1]);
    tg.addColorStop(0, rgba(c1, 0.34)); tg.addColorStop(0.45, rgba(c1, 0.08)); tg.addColorStop(1, rgba(c1, 0.0));
    g.fillStyle = tg; g.fillRect(0, 0, W, H);
    for (let k = 0; k < 10; k += 2) {
      const v0 = -52.5 + k * 10.5, v1 = v0 + 10.5;
      polyPath(g, [corner(-34, v0), corner(34, v0), corner(34, v1), corner(-34, v1)]);
      g.fillStyle = 'rgba(255,255,255,0.028)'; g.fill();
    }
    g.restore();
    polyPath(g, [c00, c10, c11, c01]); g.strokeStyle = 'rgba(255,255,255,0.18)'; g.lineWidth = 1.5; g.stroke();
    if (marks > 0) boardMarks(g, B, marks);
  }
  function boardMarks(g, B, alpha) {
    const corner = (u2, v2) => boardProj(B, u2, v2);
    g.strokeStyle = `rgba(255,255,255,${alpha})`; g.lineWidth = 2; g.lineJoin = 'round';
    const seg2 = (pts) => { g.beginPath(); pts.forEach((p, i) => { const q = corner(p[0], p[1]); if (i) g.lineTo(q[0], q[1]); else g.moveTo(q[0], q[1]); }); g.stroke(); };
    const circ = (cu, cv, r, a0, a1) => { const pts = []; for (let i = 0; i <= 40; i++) { const a = a0 + ((a1 - a0) * i) / 40; pts.push([cu + r * Math.cos(a), cv + r * Math.sin(a)]); } seg2(pts); };
    seg2([[-34, -52.5], [34, -52.5], [34, 52.5], [-34, 52.5], [-34, -52.5]]);
    seg2([[-34, 0], [34, 0]]);
    circ(0, 0, 9.15, 0, TAU);
    for (const sg of [-1, 1]) {
      const gv = sg * 52.5;
      seg2([[-20.16, gv], [-20.16, gv - sg * 16.5], [20.16, gv - sg * 16.5], [20.16, gv]]);
      seg2([[-9.16, gv], [-9.16, gv - sg * 5.5], [9.16, gv - sg * 5.5], [9.16, gv]]);
      const ang = Math.acos(5.5 / 9.15);
      if (sg < 0) circ(0, gv + 11, 9.15, PI / 2 - ang, PI / 2 + ang); else circ(0, gv - 11, 9.15, -PI / 2 - ang, -PI / 2 + ang);
    }
  }
  function quadBox(B) {
    const pts = [boardProj(B, -36.5, -55), boardProj(B, 36.5, -55), boardProj(B, 36.5, 55), boardProj(B, -36.5, 55)];
    return { x0: Math.min(...pts.map((p) => p[0])), x1: Math.max(...pts.map((p) => p[0])), y0: Math.min(...pts.map((p) => p[1])), y1: Math.max(...pts.map((p) => p[1])), pts };
  }
  function bakeXI(side) {
    const team = DATA[side], K = TEAMS[side], X = {};
    const B = landedBoard(side), q = quadBox(B);
    // the board, landed: its shadow alone (cast by the face's fill), the face, the markings at full strength
    X.shadow = bake(q.x0 - 130, q.y0 - 110, q.x1 - q.x0 + 260, q.y1 - q.y0 + 260, (g) => {
      g.shadowColor = 'rgba(0,0,0,0.45)'; g.shadowBlur = 50; g.shadowOffsetX = 10000; g.shadowOffsetY = 18;
      g.translate(-10000, 0); polyPath(g, q.pts); g.fillStyle = 'rgba(6,9,18,0.62)'; g.fill();
    });
    X.face = bake(q.x0 - 4, q.y0 - 4, q.x1 - q.x0 + 8, q.y1 - q.y0 + 8, (g) => boardFace(g, side, B, 0));
    X.marks = bake(q.x0 - 4, q.y0 - 4, q.x1 - q.x0 + 8, q.y1 - q.y0 + 8, (g) => boardMarks(g, B, 1));
    X.qbox = q;
    // its players, far ones first: the picture with its badges, and the name under it
    X.players = team.xi.map((p, i) => {
      const [pu, pv] = formUV(p), pq = boardProj(B, pu, pv);
      return { p, i, x: pq[0], y: pq[1], q: pq[2] };
    }).sort((a, b) => a.y - b.y);
    for (const P of X.players) {
      const R0 = PHOTO_R * P.q, m = Math.ceil(R0 * 1.2 + 40);
      P.R0 = R0;
      // the rim, the picture and the badges apart: each fades on its own, as they were drawn
      const ho = { px: Math.round(2 * R0), rim: 3 * P.q, glow: 8 * P.q }, box = [Math.floor(P.x) - m, Math.floor(P.y) - m, 2 * m + 2, 2 * m + 2];
      P.rim = bake(...box, (g) => headRim(g, side, P.x, P.y, R0, ho));
      let hasPic = false;
      P.face = bake(...box, (g) => { hasPic = headFace(g, side, P.p, P.x, P.y, R0, ho); });
      P.pic = bake(...box, (g) => {
        if (hasPic && R0 >= 4) {
          const bx = P.x + R0 * 0.74, by = P.y + R0 * 0.74, br = 12 * P.q;
          g.beginPath(); g.arc(bx, by, br, 0, TAU); g.fillStyle = P.p.gk ? NIGHT : K.c1; g.fill();
          g.lineWidth = 1.6; g.strokeStyle = 'rgba(255,255,255,0.92)'; g.stroke();
          txt(g, P.p.n, bx, by + 0.5, { w: 800, s: Math.round(13 * P.q), al: 'center', bl: 'middle', c: P.p.gk ? '#fff' : K.ink });
        }
        if (P.p.cap && R0 >= 4) {
          const bx = P.x + R0 * 0.72, by = P.y - R0 * 0.72;
          g.beginPath(); g.arc(bx, by, 10.5 * P.q, 0, TAU); g.fillStyle = LILAC; g.fill(); g.lineWidth = 1.5; g.strokeStyle = NIGHT; g.stroke();
          txt(g, 'C', bx, by + 0.5, { w: 800, s: Math.round(13 * P.q), al: 'center', bl: 'middle', c: NIGHT });
        }
      });
      const LB = labelLayout(side)[P.i], ly = P.y + R0 + 23 * P.q + LB.dy;
      P.label = bake(Math.floor(P.x - LB.w / 2) - 30, Math.floor(ly - LB.size) - 24, LB.w + 62, LB.size + 50, (g) => {
        shadowOn(g, 8, 0.85, 2); txt(g, P.p.last, P.x, ly, { w: 700, s: LB.size, al: 'center' }); shadowOff(g);
      });
    }
    if (team.formation) {
      const top = boardProj(B, 0, 55);
      X.top = top;
      X.formation = bake(top[0] - 160, top[1] - 90, 320, 100, (g) => {
        shadowOn(g, 10, 0.6, 2);
        txt(g, 'FORMATION', top[0], top[1] - 52, { w: 700, s: 14, ls: 5, al: 'center', c: 'rgba(255,255,255,0.7)' });
        txt(g, team.formation, top[0], top[1] - 16, { w: 800, s: 34, ls: 2, al: 'center' });
        shadowOff(g);
      });
    }
    // the panel at rest: its glass and colour, the crest (scaled as it pops), the heading, each row and its flash
    const mir = side === 'away', x = mir ? 96 : 1040, y = 160, w = 784, h = 728;
    X.px = x; X.py = y;
    X.panel = bake(x - 90, y - 80, w + 180, h + 190, (g) => {
      glass(g, x, y, w, h, 24);
      g.save(); g.beginPath(); g.roundRect(x, y, w, h, 24); g.clip();
      const bg = g.createLinearGradient(x, 0, x + w, 0); bg.addColorStop(0, K.c1); bg.addColorStop(0.6, K.c2); bg.addColorStop(1, rgba(hex(K.c2), 0));
      g.fillStyle = bg; g.fillRect(x, y, w, 7);
      const hg = g.createLinearGradient(0, y, 0, y + 150); hg.addColorStop(0, rgba(hex(K.c1), 0.22)); hg.addColorStop(1, rgba(hex(K.c1), 0));
      g.fillStyle = hg; g.fillRect(x, y, w, 150);
      g.restore();
    });
    X.crest = bake(-80, -80, 160, 170, (g) => crest(g, side, 0, 0, 100));
    X.head = bake(x, y, w, 160, (g) => {
      let mgrW = 0;
      if (team.coach) {
        const cw = Math.max(measure(g, team.coach, 600, 25, 0), measure(g, 'MANAGER', 700, 13, 4));
        mgrW = 62 + 18 + cw;
        headRim(g, side, x + w - 30 - 31, y + 76, 31, { initials: true, glow: 8 });
        txt(g, 'MANAGER', x + w - 30 - 62 - 18, y + 63, { w: 700, s: 13, ls: 4, al: 'right', c: 'rgba(255,255,255,0.6)' });
        txt(g, team.coach, x + w - 30 - 62 - 18, y + 94, { w: 600, s: 25, al: 'right' });
      }
      const nameUp = team.name.toUpperCase();
      const nz = fitSize(nameUp, 800, 54, 1, w - 150 - 30 - (mgrW ? mgrW + 28 : 0), 30);
      txt(g, nameUp, x + 150, y + 84, { w: 800, s: nz, ls: 1 });
      txt(g, 'STARTING XI', x + 152, y + 116, { w: 700, s: 17, ls: 5, c: LILAC });
      g.fillStyle = 'rgba(255,255,255,0.12)'; g.fillRect(x + 28, y + 146, w - 56, 1.5);
    });
    if (team.coach) X.coach = bake(x + w - 30 - 62 - 4, y + 76 - 35, 70, 70, (g) => headFace(g, side, { id: team.coachId, name: team.coach }, x + w - 30 - 31, y + 76, 31, { initials: true, glow: 8 }));
    const y0 = y + 158, rh = 46.5;
    X.rows = team.xi.map((p, i) => {
      const yy = y0 + i * rh;
      const flash = bake(x + 14, yy + 3, w - 28, rh - 6, (g) => {
        const fg = g.createLinearGradient(x, 0, x + w, 0); fg.addColorStop(0, rgba(hex(K.c1), 0.42)); fg.addColorStop(1, rgba(hex(K.c1), 0));
        g.fillStyle = fg; g.fillRect(x + 14, yy + 3, w - 28, rh - 6);
      });
      const row = bake(x, yy - 6, w, rh + 14, (g) => {
        g.beginPath(); g.roundRect(x + 30, yy + 8, 48, rh - 16, 8);
        if (p.gk) { g.fillStyle = 'rgba(7,9,15,0.85)'; g.fill(); g.lineWidth = 2; g.strokeStyle = K.c1; g.stroke(); }
        else { g.fillStyle = K.c1; g.fill(); }
        txt(g, p.n, x + 54, yy + rh / 2 + 1, { w: 800, s: 21, al: 'center', bl: 'middle', c: p.gk ? '#fff' : K.ink });
        headRim(g, side, x + 105, yy + rh / 2, 19.5, { initials: true, rim: 1.5, glow: 4, shadow: 6 });
        const last = p.last || p.name;
        let first = p.name && p.name.endsWith(last) ? p.name.slice(0, p.name.length - last.length).trim() : '';
        if (p.name === last) first = '';
        const room = w - 137 - 32 - 50 - (p.cap ? 34 : 0);
        if (first && measure(g, first, 300, 26, 0) + 9 + measure(g, last.toUpperCase(), 700, 26, 0.5) > room) first = '';
        const lz = fitSize(last.toUpperCase(), 700, 26, 0.5, room, 18);
        let nx = x + 137;
        if (first) { const wf = txt(g, first, nx, yy + rh / 2 + 9, { w: 300, s: 26, c: 'rgba(255,255,255,0.82)' }); nx += wf + 9; }
        const wl = txt(g, last.toUpperCase(), nx, yy + rh / 2 + 9, { w: 700, s: lz, ls: 0.5 });
        nx += wl + 12;
        if (p.cap) {
          g.beginPath(); g.arc(nx + 11, yy + rh / 2, 11, 0, TAU); g.fillStyle = LILAC; g.fill();
          txt(g, 'C', nx + 11, yy + rh / 2 + 1, { w: 800, s: 14, al: 'center', bl: 'middle', c: NIGHT });
        }
        txt(g, posTag(p), x + w - 32, yy + rh / 2 + 6, { w: 600, s: 15, ls: 3, al: 'right', c: 'rgba(255,255,255,0.5)' });
        if (i < 10) { g.fillStyle = 'rgba(255,255,255,0.06)'; g.fillRect(x + 30, yy + rh - 0.5, w - 60, 1); }
      });
      const face = bake(x + 105 - 22, yy + rh / 2 - 22, 44, 44, (g) => headFace(g, side, p, x + 105, yy + rh / 2, 19.5, { initials: true, rim: 1.5, glow: 4, shadow: 6 }));
      return { flash, row, face };
    });
    // the subs' bar at rest, and each sub
    if (team.subs.length) {
      const sx = 96, sy = 918, sw = 1728, sh = 62;
      const lw = measure(ctx, 'SUBSTITUTES', 800, 15, 4);
      X.subsBar = bake(sx - 90, sy - 80, sw + 180, sh + 190, (g) => {
        glass(g, sx, sy, sw, sh, 18);
        g.beginPath(); g.roundRect(sx + 12, sy + 12, lw + 34, sh - 24, 10); g.fillStyle = K.c1; g.fill();
        txt(g, 'SUBSTITUTES', sx + 12 + (lw + 34) / 2, sy + sh / 2 + 1, { w: 800, s: 15, ls: 4, al: 'center', bl: 'middle', c: K.ink });
      });
      let sz = 23;
      const avail = sw - (lw + 34) - 60, PH = 32, PG = 9, GAP = 20;
      const widthAt = (z) => team.subs.reduce((acc, sb) => acc + PH + PG + measure(ctx, sb.n, 800, z) + 7 + measure(ctx, sb.last, 500, z) + GAP, 0) - GAP;
      while (widthAt(sz) > avail && sz > 13) sz -= 1;
      const gap = (avail - widthAt(sz)) / Math.max(1, team.subs.length - 1) + GAP;
      let ex = sx + 12 + lw + 34 + 22;
      X.subs = team.subs.map((sb) => {
        const w1 = measure(ctx, sb.n, 800, sz), w2 = measure(ctx, sb.last, 500, sz), ex0 = ex;
        const so = { initials: true, rim: 1.5, glow: 4, shadow: 5 }, sp = { id: sb.id, n: sb.n, name: sb.last };
        const face = bake(Math.floor(ex0) - 2, sy + sh / 2 - PH / 2 - 2, PH + 4, PH + 4, (g) => headFace(g, side, sp, ex0 + PH / 2, sy + sh / 2, PH / 2, so));
        const e = bake(Math.floor(ex0) - 14, sy - 6, PH + PG + w1 + 7 + w2 + 30, sh + 14, (g) => {
          headRim(g, side, ex0 + PH / 2, sy + sh / 2, PH / 2, so);
          txt(g, sb.n, ex0 + PH + PG, sy + sh / 2 + sz * 0.36, { w: 800, s: sz, c: side === 'away' ? K.c1 : '#FF5A5F' });
          txt(g, sb.last, ex0 + PH + PG + w1 + 7, sy + sh / 2 + sz * 0.36, { w: 500, s: sz, c: 'rgba(255,255,255,0.92)' });
        });
        ex += PH + PG + w1 + 7 + w2 + gap;
        return { e, face };
      });
    }
    return X;
  }
  function boardLayers(side, s, u, kout) {
    const X = SPR['xi:' + side], T = xiTimes(s);
    const ka = eOut3(ramp(T.boardIn, T.boardLand, u));
    const al = clamp(ka * 1.6) * (1 - kout);
    if (al <= 0) return;
    const shift = kout * (side === 'away' ? 60 : -60);
    const flash = Math.max(0, 1 - Math.abs(u - T.boardLand) / 0.35);
    let top = X.top;
    if (ka < 1) {
      // tilting down onto its place: drawn as it is now (the landed board's shadow under it, squeezed to its height)
      const B = { cx: boardCx(side) + shift, cy: 492 + (1 - ka) * 36, F: 1800, D: 220, a: lerp(80, 38, ka) * DEG };
      const q = quadBox(B), q0 = X.qbox, k = (q.y1 - q.y0) / (q0.y1 - q0.y0);
      at(X.shadow, [1, 0, 0, k, shift, q.y0 - q0.y0 * k], al);
      const c = LK.dyn('board:' + side, 900, 820), g = dynClear(c), ox = boardCx(side) - 450, oy = 150;
      g.translate(-ox, -oy);
      boardFace(g, side, B, 0.3 + 0.4 * flash);
      LK.put(c, LT(ox, oy), al);
      if (top) { const t2 = boardProj(B, 0, 55); top = [t2[0] - shift, t2[1]]; }
    } else {
      at(X.shadow, LT(shift, 0), al);
      at(X.face, LT(shift, 0), al);
      at(X.marks, LT(shift, 0), al * (0.3 + 0.4 * flash));
    }
    for (const P of X.players) {
      const tp = T.pop0 + P.i * T.popGap, kp = ramp(tp, tp + 0.42, u);
      if (kp <= 0) continue;
      const sc = eOutBack(kp, 2.0), aP = al * clamp(kp * 4);
      const kr = ramp(tp, tp + 0.7, u);
      if (kr < 1) {
        const rr = P.R0 * (1 + 0.8 * eOut3(kr)), e = Math.ceil(P.R0 * 1.8 + 6);
        const c = LK.dyn('ring:' + side + P.i, 2 * e, 2 * e), g = dynClear(c);
        const ox = Math.floor(P.x) - e, oy = Math.floor(P.y) - e;
        g.translate(-ox, -oy);
        g.beginPath(); g.arc(P.x, P.y, rr, 0, TAU); g.strokeStyle = `rgba(255,255,255,${0.5 * (1 - kr)})`; g.lineWidth = 2; g.stroke();
        LK.put(c, LT(ox + shift, oy), aP);
      }
      if (P.R0 * sc >= 1) for (const part of [P.rim, P.face, P.pic]) at(part, [sc, 0, 0, sc, P.x - P.x * sc + shift, P.y - P.y * sc], aP);
      const kn = eOut3(ramp(tp + 0.08, tp + 0.45, u));
      if (kn > 0) at(P.label, LT(shift, (1 - kn) * 8), al * kn);
    }
    if (X.formation) at(X.formation, LT(top[0] - X.top[0] + shift, top[1] - X.top[1]), al);
  }
  function panelLayers(side, s, u, kin, kout) {
    const X = SPR['xi:' + side], T = xiTimes(s), mir = side === 'away';
    const dx = (1 - kin) * (mir ? -110 : 110) + kout * (mir ? -70 : 70);
    const al = clamp(kin * 1.3) * (1 - kout);
    if (al <= 0) return;
    at(X.panel, LT(dx, 0), al);
    const kc = eOutBack(ramp(0.15, 0.75, u), 1.6);
    if (kc > 0) at(X.crest, [kc, 0, 0, kc, X.px + 82 + dx, X.py + 76], al);
    at(X.head, LT(dx, 0), al);
    if (X.coach) at(X.coach, LT(dx, 0), al);
    X.rows.forEach((R, i) => {
      const tp = T.pop0 + i * T.popGap, kr = eOut3(ramp(tp - 0.04, tp + 0.32, u));
      if (kr <= 0) return;
      const rx = dx + (1 - kr) * (mir ? -26 : 26), fl = Math.max(0, 1 - (u - tp) / 0.7);
      if (fl > 0) at(R.flash, LT(rx, 0), al * kr * fl);
      at(R.row, LT(rx, 0), al * kr);
      at(R.face, LT(rx, 0), al * kr);
    });
  }
  function subsLayers(side, s, u, kout) {
    const X = SPR['xi:' + side], T = xiTimes(s);
    if (!X.subs) return;
    const ks = eOutExpo(ramp(T.subs, T.subs + 0.7, u));
    if (ks <= 0) return;
    const dy = (1 - ks) * 40 + kout * 30, al = clamp(ks * 1.3) * (1 - kout);
    at(X.subsBar, LT(0, dy), al);
    X.subs.forEach((S, i) => { const ke = eOut3(ramp(T.subs + 0.15 + i * 0.05, T.subs + 0.5 + i * 0.05, u)); at(S.e, LT(0, dy), al * ke); at(S.face, LT(0, dy), al * ke); });
  }
  function xiLayers(s, u, side) {
    const T = xiTimes(s);
    const kin = eOutExpo(ramp(0, 0.85, u));
    const kout = eIn3(ramp(T.out0, T.out1, u));
    boardLayers(side, s, u, kout);
    panelLayers(side, s, u, kin, kout);
    subsLayers(side, s, u, kout);
    const ha = clamp(ramp(0.6, 1.2, u)) * (1 - kout);
    if (ha > 0) at(SPR.hint, LK.I, ha);
  }

  // ---- the officials, the countdown (a canvas of its own, written again each second)
  function bakeOfficials() {
    const w = 1160, h = 182, x = 960 - w / 2, y = 806, O = {};
    O.base = bake(x - 90, y - 80, w + 180, h + 190, (g) => {
      glass(g, x, y, w, h, 24);
      crest(g, 'home', x + 58, y + 50, 54);
      const hn = DATA.home.name.toUpperCase(), an = DATA.away.name.toUpperCase();
      txt(g, hn, x + 98, y + 63, { w: 800, s: fitSize(hn, 800, 32, 1, 960 - 40 - (x + 98), 18), ls: 1 });
      txt(g, 'v', 960, y + 61, { w: 300, s: 34, al: 'center', c: 'rgba(255,255,255,0.75)' });
      crest(g, 'away', x + w - 58, y + 50, 54);
      txt(g, an, x + w - 98, y + 63, { w: 800, s: fitSize(an, 800, 32, 1, x + w - 98 - (960 + 40), 18), ls: 1, al: 'right' });
      g.fillStyle = 'rgba(255,255,255,0.12)'; g.fillRect(x + 28, y + 98, w - 56, 1.5);
      let kx = x + 86;
      if (REF) {
        g.fillRect(960, y + 112, 1.5, 56);
        iconWhistle(g, x + 52, y + 140, 36, LILAC);
        txt(g, 'REFEREE', x + 86, y + 128, { w: 700, s: 13, ls: 5, c: LILAC });
        txt(g, REF, x + 86, y + 160, { w: 700, s: fitSize(REF, 700, 28, 0, 960 - 40 - (x + 86), 16) });
        kx = 960 + 40;
      }
      txt(g, 'KICK-OFF', kx, y + 128, { w: 700, s: 13, ls: 5, c: LILAC });
      O.cx = kx + txt(g, PLAN.ko, kx, y + 160, { w: 800, s: 30 }) + 16;
    });
    O.y = y;
    return O;
  }
  function officialsLayers(s, u) {
    const O = SPR.officials, D = s.d;
    const kin = eOutExpo(ramp(0, 0.85, u)), kout = eIn3(ramp(D - 0.5, D, u));
    const al = clamp(kin * 1.3) * (1 - kout);
    if (al <= 0) return;
    const dy = (1 - kin) * 50 + kout * 34;
    at(O.base, LT(0, dy), al);
    if (countdownOn()) {
      const left = Math.max(0, Math.round((KO_AT - Date.now()) / 1000));
      const str = `in ${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}`;
      const c = LK.dyn('countdown', 220, 44), ox = Math.floor(O.cx) - 4, oy = O.y + 160 - 32;
      if (c._k !== str) { c._k = str; const g = dynClear(c); g.translate(-ox, -oy); txt(g, str, O.cx, O.y + 160, { w: 600, s: 24, c: PEACH }); }
      LK.put(c, LT(ox, oy + dy), al);
    }
  }

  // ---- the corner brand, the hint, the brand wipe's band
  function bakeCorner() {
    const s = 50, x1 = 1824, y = 54;
    const w1 = measure(ctx, 'WHITEBROOK ROAD', 800, 21, 1.5), w2 = measure(ctx, 'SPORTS', 400, 12, 7);
    const x0 = x1 - Math.max(w1, w2) - 14 - s;
    SPR.bug = bake(x0 - 40, y - 30, x1 - x0 + 80, s + 80, (g) => {
      tile(g, x0, y, s, { shadow: true });
      shadowOn(g, 12, 0.55, 2);
      txt(g, 'WHITEBROOK ROAD', x0 + s + 14, y + 26, { w: 800, s: 21, ls: 1.5 });
      txt(g, 'SPORTS', x0 + s + 15, y + 44, { w: 400, s: 12, ls: 7, c: LILAC });
      shadowOff(g);
    });
    SPR.hint = bake(1400, 960, 470, 80, (g) => drawBackHint(g, 1));
    SPR.wipe = bake(-182, 0, 364, H, (g) => {
      const gr = g.createLinearGradient(-180, 0, 180, 0);
      gr.addColorStop(0, 'rgba(109,40,242,0)'); gr.addColorStop(0.42, VIOLET); gr.addColorStop(0.58, CORAL); gr.addColorStop(1, 'rgba(255,107,107,0)');
      g.fillStyle = gr;
      g.beginPath(); g.moveTo(40, 0); g.lineTo(180, 0); g.lineTo(-40, H); g.lineTo(-180, H); g.closePath(); g.fill();
    });
  }
  function bugLayers(t) {
    const ti = seg('title'), ou = seg('outro');
    if (!ti) return;
    const kin = eOut3(ramp(ti.a + 0.25, ti.a + 0.85, t));
    const kout = ou ? ramp(ou.a + 0.1, ou.a + 0.5, t) : 0;
    const a = kin * (1 - kout);
    if (a > 0) at(SPR.bug, LT((1 - kin) * 26, 0), a * 0.96);
  }

  // ---- the ground: sharp, its blurred copy faded over it, a dimming, the rain on a wet night
  function bgLayers(t) {
    const bp = bgParams(t);
    const k = clamp(t / DUR), z = 1.02 + 0.035 * k, dx = lerp(-14, 14, k);
    const x = (W - W * z) / 2 + dx, y = (H - H * z) / 2;
    LK.put(BGSHARP, [z, 0, 0, z, x, y]);
    if (bp.blur > 0.01) LK.put(BGSOFT, [(W * z) / BGSOFT.width, 0, 0, (H * z) / BGSOFT.height, x, y], clamp(bp.blur));
    if (bp.dim > 0) fillAll('rgb(4,6,12)', bp.dim);
    if (CFG.rain) {
      // the rain: its streaks falling (and, on the drawn ground, its drops landing), in a canvas of its own each frame.
      // Laid over the ground plainly rather than added to it: the added light needs a copy of the whole screen each frame
      // (44 frames a second in the headless test against 54), and on the dark night ground the faint streaks look the same.
      const c = LK.dyn('rain', W, H), g = dynClear(c);
      if (GROUND_PHOTO) rainStreaks(g, t); else drawRain(g, BGCAM, t);
      LK.put(c, LK.I, 1);
    }
    if (SPR.credit) at(SPR.credit, LK.I, 1);
  }

  function drawFrame(t) {
    LK.begin();
    const ou = seg('outro');
    const uo = ou ? t - ou.a : -1;
    const wipe = ou ? eInOut3(ramp(0.3, 1.1, uo)) : 0;
    if (wipe < 1) {
      bgLayers(t);
      for (const s of SEQ) {
        if (t < s.a - 0.001 || t > s.b + 0.001) continue;
        const u = t - s.a;
        if (s.n === 'ident') identLayers(s, u);
        else if (s.n === 'title') titleLayers(s, u);
        else if (s.n === 'home' || s.n === 'away') xiLayers(s, u, s.n);
        else if (s.n === 'officials') officialsLayers(s, u);
      }
      bugLayers(t);
      // back to the channel: the brand wipe uncovers its own picture
      if (wipe > 0) {
        const edge = lerp(-260, W + 260, wipe);
        LK.wipe(edge, 140);
        at(SPR.wipe, LT(edge, 0), 1, '', true);
      } else LK.wipe(null);
    } else LK.wipe(null);
    // live: up from the channel at the start (as the drawing always had it: its fade took the ground's dimming colour, so
    // it comes up from half way, not from nothing); Back: it all fades away to the channel at once
    const dim = bgParams(t).dim;
    let f = 1 - (1 - sstep(0, 0.9, t)) * (dim > 0 ? dim : 1);
    if (window.FADE_FROM != null) f *= 1 - clamp((t - window.FADE_FROM) / 0.5);
    LK.fade(f);
    LK.end();
  }

  // ================================================================ cues for the sound (stadium-host.js plays them)
  function buildCues() {
    const cues = [];
    for (const s of SEQ) {
      if (s.n === 'ident') cues.push({ t: s.a + 0.12, k: 'ident' });
      if (s.n === 'title') { cues.push({ t: s.a - 0.25, k: 'whoosh', pan: -0.4 }); cues.push({ t: s.a + 0.5, k: 'land' }); }
      if (s.n === 'home' || s.n === 'away') {
        const T = xiTimes(s), team = DATA[s.n], ln = lineOf(team);
        cues.push({ t: s.a, k: 'swell', team: s.n, pan: s.n === 'home' ? -0.15 : 0.15 });
        cues.push({ t: s.a + T.boardLand, k: 'board' });
        const cx = boardCx(s.n);
        team.xi.forEach((p, i) => {
          const [pu] = formUV(p);
          const x = cx + pu * 8.2;
          cues.push({ t: +(s.a + T.pop0 + i * T.popGap).toFixed(4), k: 'tick', pan: +(((x - 960) / 960) * 0.7).toFixed(3), line: Math.max(0, Math.min(4, ln(p))) });
        });
        if (team.coach) cues.push({ t: s.a + T.mgr, k: 'soft', pan: 0.2 });
        if (team.subs.length) cues.push({ t: s.a + T.subs, k: 'shimmer' });
        cues.push({ t: s.a + T.out0 - 0.25, k: 'whoosh', pan: s.n === 'home' ? 0.4 : -0.4 });
      }
      if (s.n === 'officials') cues.push({ t: s.a + 0.15, k: 'land' });
      if (s.n === 'outro') cues.push({ t: s.a + 0.2, k: 'wipe' });
    }
    cues.push({ t: DUR, k: 'end' });
    cues.sort((a, b) => a.t - b.t);
    return cues;
  }

  // ================================================================ start
  // a PL cut-out (the player waist up, see-through around him): the head and shoulders cut square for the round pictures
  function headOf(im) {
    const s = Math.round(im.width * 0.62), c = mk(s, s), g = c.getContext('2d');
    g.imageSmoothingQuality = 'high';
    g.drawImage(im, Math.round(im.width * 0.19), 0, s, s, 0, 0, s, s);
    return c;
  }
  async function init() {
    const t0 = performance.now();
    for (const w of [300, 400, 500, 600, 700, 800]) await document.fonts.load(`${w} 40px Outfit`);
    await document.fonts.ready;
    for (const side of ['home', 'away']) {
      // a crest that will not come is left out rather than stopping the line-ups
      const url = window.ASSET_URLS[side];
      if (url) { try { const im = new Image(); im.src = url; await im.decode(); if (im.width > 0) IMG[side] = im; } catch (e) { /* no crest */ } }
    }
    const faces = window.ASSET_URLS.photos || {}, cuts = window.ASSET_URLS.plphotos || {};
    for (const id of Object.keys(faces)) {
      const im = new Image(); im.src = faces[id];
      try { await im.decode(); if (im.width > 0) PHOTO[id] = im; } catch (e) { /* the stand-in */ }
    }
    for (const id of Object.keys(cuts)) {
      if (PHOTO[id]) continue;
      const im = new Image(); im.src = cuts[id];
      try { await im.decode(); if (im.width > 0) PHOTO[id] = headOf(im); } catch (e) { /* the stand-in */ }
    }
    window.PHOTO_COUNT = Object.keys(PHOTO).length;
    let photo = null;
    if (window.ASSET_URLS.ground) {
      try { const im = new Image(); im.src = window.ASSET_URLS.ground; await im.decode(); if (im.width > 0) photo = im; } catch (e) { /* the drawn ground */ }
    }
    if (!photo) { GL = groundLight(); buildCrowd(); buildSky(); }
    buildBackground(photo);
    window.GROUND_USED = !!photo;
    for (const side of ['home', 'away']) labelLayout(side);
    // every part drawn now, once: the frames only place them
    LK.mount();
    if (seg('ident')) SPR.ident = bakeIdent();
    if (seg('title')) SPR.title = bakeTitle();
    for (const side of ['home', 'away']) SPR['xi:' + side] = bakeXI(side);
    if (seg('officials')) SPR.officials = bakeOfficials();
    bakeCorner();
    if (photo) SPR.credit = groundCredit(window.GROUND_CREDIT, 28, H - 14, 'left');
    window.CUES = buildCues();
    window.DUR = DUR;
    window.SEQ = SEQ;
    window.SOL = SOL;
    settle(drawFrame, DUR);
    window.INIT_MS = performance.now() - t0;
    window.drawFrame = drawFrame;
    window.READY = true;
    if (window.onBroadcastReady) window.onBroadcastReady();
  }
  init().catch((e) => { window.INIT_ERR = String(e && e.stack || e); if (window.onBroadcastFailed) window.onBroadcastFailed(window.INIT_ERR); });
})();
