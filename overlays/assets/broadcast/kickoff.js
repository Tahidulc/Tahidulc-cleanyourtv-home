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
  function mk(w, h) { const c = document.createElement('canvas'); c.width = w; c.height = h; return c; }
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
    const K = TEAMS[side];
    const rim = o.rim === undefined ? Math.max(1.5, r * 0.075) : o.rim;
    g.save();
    shadowOn(g, o.shadow === undefined ? r * 0.5 : o.shadow, 0.5, r * 0.1);
    g.beginPath(); g.arc(cx, cy, r + rim, 0, TAU); g.fillStyle = K.c1; g.fill();
    g.shadowColor = rgba(hex(K.c1), 0.65); g.shadowBlur = o.glow === undefined ? r * 0.45 : o.glow; g.shadowOffsetY = 0;
    g.fill();
    g.restore();
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

  // ================================================================ the sequence
  // ================================================================ the kick-off caption (this mock-up's own part)
  // Modelled on the old broadcast caption in the user's recording: both crests fly in, the competition's trophy rises
  // behind them, a bar opens out with the teams, a tab above it changes, then it all closes back to the middle.
  // Whitebrook Road Sports' own drawing: no broadcaster's lettering or emblem; the trophies are our own drawings.
  const POP = window.POP;
  const MATCH = POP.match;
  const CY = 918, BAR_H = 88, R_CREST = 94, TAB_H = 52;   // the bar 88 px tall, as in the recording (60 px at 720p)
  const OPEN_L = 411, OPEN_R = 1509, BAR_L = 466, BAR_R = 1454;
  const BC = "'KhandM'";
  // The lettering, matched to the recording by measurement: Khand (free, SIL Open Font Licence) is the closest free
  // match to the broadcaster's own typeface, which isn't available to use. Weight 600 has the recording's stroke
  // (0.18 of the capital height); the capitals are 54% of the bar's height and the letters are set open (0.1 cap).
  const NAME = { w: 600, s: 70, ls: 4.8 };   // team names, stadium, scorer: capitals 48 px on the 88 px bar
  const TABT = { w: 600, s: 44, ls: 2.4 };   // the tab's words: capitals 30 px on the 52 px tab
  const capOf = (s) => s * 0.685;            // Khand's capital height
  const RED = '#E10A1E', RED_DEEP = '#B0061A';

  function fontBC(g, w, s, ls) { g.font = `${w} ${s}px ${BC}`; g.letterSpacing = `${ls || 0}px`; }
  function fontOut(g, w, s, ls) { g.font = `${w} ${s}px Outfit`; g.letterSpacing = `${ls || 0}px`; }

  // ---------------------------------------------------------------- the picture behind: the teams set for kick-off
  function placeTeams(mirror, kicksSide) {
    FIGS.length = 0;
    const R = mulberry(mirror ? 515 : 404);
    for (const side of ['home', 'away']) {
      const team = DATA[side], K = TEAMS[side];
      const left = (side === 'home') !== !!mirror; // this side's own half is on the left of the picture
      const dir = left ? -1 : 1;
      const kicks = side === (kicksSide || 'home');
      let striker = 0;
      for (const p of team.xi) {
        let X = dir * (PL - p.y * 59);
        let Z = (left ? 0.5 - p.x : p.x - 0.5) * 50 + (R() - 0.5) * 1.2;
        if (kicks && p.y > 0.8) { // the kick-off: one at the ball, the other beside it
          X = striker === 0 ? dir * 0.4 : dir * 0.6; Z = striker === 0 ? 0.4 : -2.2; striker++;
        } else if (Math.abs(X) < 10.5) {
          X = dir * (kicks ? Math.max(1.5, Math.abs(X)) : 10.5 + R() * 1.5);
        }
        FIGS.push({ x: X + (R() - 0.5) * 0.8, z: Z, kit: p.gk ? K.gk : K.kit, skin: SKIN[(R() * 6) | 0], ph: R() * 6 });
      }
    }
    FIGS.push({ x: 4.5, z: -7.5, kit: REF_KIT, skin: SKIN[1], ph: 1.3 });
    FIGS.push({ x: -16, z: -PW - 1.2, kit: REF_KIT, skin: SKIN[0], ph: 2.1 });
  }
  let curKey = null;
  function broadcastCam(t) {
    const d = 0.9 * Math.sin(t * 0.11);
    return makeCam([d, 22, 63], [d * 0.9, 0, -3], 60);
  }

  // ---------------------------------------------------------------- metal
  const SILVER = [[0, '#3f454c'], [0.1, '#aab2bb'], [0.24, '#ffffff'], [0.36, '#8e97a1'], [0.52, '#e6eaee'], [0.7, '#69717a'], [0.86, '#c7cdd3'], [1, '#353a40']];
  const GOLD = [[0, '#5e3604'], [0.14, '#c98d22'], [0.28, '#fff0a6'], [0.42, '#c1831b'], [0.58, '#f6cd5c'], [0.78, '#8a5a12'], [1, '#4f2e03']];
  function metal(g, x0, x1, stops, y0, y1) {
    const gr = g.createLinearGradient(x0, y0 || 0, x1, y1 || 0);
    for (const [o, c] of stops) gr.addColorStop(o, c);
    return gr;
  }
  function edge(g, a = 0.4, w = 1.2) { g.strokeStyle = `rgba(20,22,26,${a})`; g.lineWidth = w; g.stroke(); }
  function sym(pts) { // a closed outline from its left half (x <= 0), mirrored
    const p = new Path2D();
    p.moveTo(pts[0][0], pts[0][1]);
    for (let i = 1; i < pts.length; i++) p.lineTo(pts[i][0], pts[i][1]);
    for (let i = pts.length - 1; i >= 0; i--) p.lineTo(-pts[i][0], pts[i][1]);
    p.closePath();
    return p;
  }
  // a smooth body of revolution's outline: [y, halfWidth] pairs, filled with a metal across its width
  function body(g, rows, stops, u, o = {}) {
    const pts = [];
    for (let y = rows[0][0]; y <= rows[rows.length - 1][0] + 1e-6; y += 0.25) {
      let i = 0; while (i < rows.length - 2 && rows[i + 1][0] < y) i++;
      const [ya, wa] = rows[i], [yb, wb] = rows[i + 1];
      const k = smooth(clamp((y - ya) / (yb - ya || 1)));
      pts.push([-lerp(wa, wb, k) * u, y * u]);
    }
    const p = sym(pts);
    const wmax = Math.max(...rows.map((r) => r[1])) * u;
    g.fillStyle = metal(g, -wmax, wmax, stops);
    g.fill(p);
    if (o.shade !== false) { // darker towards the bottom of each part, lighter at the top
      const yb0 = rows[0][0] * u, yb1 = rows[rows.length - 1][0] * u;
      const sh = g.createLinearGradient(0, yb0, 0, yb1);
      sh.addColorStop(0, 'rgba(255,255,255,0.10)'); sh.addColorStop(0.55, 'rgba(0,0,0,0)'); sh.addColorStop(1, 'rgba(0,0,0,0.28)');
      g.fillStyle = sh; g.fill(p);
    }
    g.save(); g.strokeStyle = 'rgba(25,27,31,0.45)'; g.lineWidth = 1.1; g.stroke(p); g.restore();
    return p;
  }
  function ellipseLip(g, y, rx, ry, stops, u) {
    g.beginPath(); g.ellipse(0, y * u, rx * u, ry * u, 0, 0, TAU);
    g.fillStyle = metal(g, -rx * u, rx * u, stops); g.fill();
    g.strokeStyle = 'rgba(20,22,26,0.5)'; g.lineWidth = 1.1; g.stroke();
    g.beginPath(); g.ellipse(0, (y - ry * 0.25) * u, rx * 0.92 * u, ry * 0.55 * u, 0, PI, TAU);
    g.strokeStyle = 'rgba(255,255,255,0.55)'; g.lineWidth = 1.2; g.stroke();
  }
  function streak(g, x, y0, y1, w, a) { // a soft vertical highlight on a polished surface
    const gr = g.createLinearGradient(x - w, 0, x + w, 0);
    gr.addColorStop(0, 'rgba(255,255,255,0)'); gr.addColorStop(0.5, `rgba(255,255,255,${a})`); gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(x - w, y0, 2 * w, y1 - y0);
  }
  function handle(g, pts, width, stops, u) { // a cast handle: a thick stroked curve with a lit edge
    const p = new Path2D();
    p.moveTo(pts[0][0] * u, pts[0][1] * u);
    for (let i = 1; i + 2 < pts.length + 1; i += 3) p.bezierCurveTo(pts[i][0] * u, pts[i][1] * u, pts[i + 1][0] * u, pts[i + 1][1] * u, pts[i + 2][0] * u, pts[i + 2][1] * u);
    g.save(); g.lineCap = 'round'; g.lineJoin = 'round';
    g.strokeStyle = 'rgba(15,16,18,0.55)'; g.lineWidth = width * u + 2.2; g.stroke(p);
    const xs = pts.map((q) => q[0] * u); const x0 = Math.min(...xs), x1 = Math.max(...xs);
    g.strokeStyle = metal(g, x0 - 4, x1 + 4, stops, -6, 6); g.lineWidth = width * u; g.stroke(p);
    g.strokeStyle = 'rgba(255,255,255,0.45)'; g.lineWidth = Math.max(1, width * u * 0.22); g.translate(-width * u * 0.18, -width * u * 0.18); g.stroke(p);
    g.restore();
  }

  // ---------------------------------------------------------------- the trophies (our own drawings), one canvas each
  const TROPHY = {};
  const T_H = 520, T_TOP = 598, T3_H = 540;
  // per trophy [scale, dy, glint scale, top's drop]: each shows the Premier League trophy's amount of silver above the bar
  // (measured: 40k px) with its top level with the PL trophy's; the Carabao Cup, a wide bowl, stands 36 px lower so its
  // foot stays behind the bar, and the Europa League trophy is a touch smaller so its marble foot stays hidden behind the bar.
  const T3_FIT = { pl: [1.0, 0.0, 1.0, 0], facup: [0.96, -12.4, 0.94, 0], leaguecup: [0.504, 36.3, 0.52, 36], ucl: [0.751, -11.1, 0.74, 0], uel: [0.649, -3.2, 0.65, 0], uecl: [0.83, 4.3, 0.84, 0], generic: [0.643, 2.9, 0.66, 0] };
  function trophyCanvas(kind) {
    if (TROPHY[kind]) return TROPHY[kind];
    const c = mk(560, T_H + 20), g = c.getContext('2d');
    const u = T_H / 100;
    g.translate(280, 10);
    const draw = TROPHY_DRAW[kind] || TROPHY_DRAW.generic;
    ribbons(g, u, kind);
    draw(g, u);
    TROPHY[kind] = c;
    return c;
  }
  const malachite = (g, u, y0, y1, w0, w1) => {
    const p = sym([[-w0 * u, y0 * u], [-w1 * u, y1 * u]]);
    const gr = g.createLinearGradient(-w1 * u, 0, w1 * u, 0);
    gr.addColorStop(0, '#06261a'); gr.addColorStop(0.3, '#17614a'); gr.addColorStop(0.5, '#1f7a5c'); gr.addColorStop(0.7, '#0f4a37'); gr.addColorStop(1, '#041a12');
    g.fillStyle = gr; g.fill(p);
    g.save(); g.clip(p);
    const R = mulberry(9);
    for (let i = 0; i < 14; i++) { // the stone's bands
      g.beginPath(); const cx = (R() - 0.5) * w1 * 2 * u, cy = lerp(y0, y1, R()) * u, r = (3 + R() * 9) * u;
      g.ellipse(cx, cy, r * 1.8, r, R() * 0.6, 0, TAU);
      g.strokeStyle = `rgba(0,0,0,${0.12 + R() * 0.18})`; g.lineWidth = 1 + R() * 2.5; g.stroke();
      g.strokeStyle = `rgba(120,220,180,${0.05 + R() * 0.08})`; g.lineWidth = 1; g.stroke();
    }
    const sh = g.createLinearGradient(0, y0 * u, 0, y1 * u); sh.addColorStop(0, 'rgba(255,255,255,0.12)'); sh.addColorStop(1, 'rgba(0,0,0,0.35)');
    g.fillStyle = sh; g.fillRect(-w1 * u, y0 * u, 2 * w1 * u, (y1 - y0) * u);
    g.restore();
    g.strokeStyle = 'rgba(0,0,0,0.5)'; g.lineWidth = 1.2; g.stroke(p);
  };
  const plinth = (g, u, y0, y1, w0, w1, col) => {
    const p = sym([[-w0 * u, y0 * u], [-w1 * u, y1 * u]]);
    const gr = g.createLinearGradient(-w1 * u, 0, w1 * u, 0);
    gr.addColorStop(0, '#050506'); gr.addColorStop(0.32, col || '#2a2c31'); gr.addColorStop(0.5, '#3a3d43'); gr.addColorStop(0.75, '#141518'); gr.addColorStop(1, '#030304');
    g.fillStyle = gr; g.fill(p); g.strokeStyle = 'rgba(0,0,0,0.6)'; g.lineWidth = 1.2; g.stroke(p);
  };
  const band = (g, u, y0, y1, w0, w1, stops) => body(g, [[y0, w0], [y1, w1]], stops || SILVER, u, { shade: false });

  function ribbons(g, u, kind) {
    const top = kind === 'ucl' ? 66 : kind === 'uel' ? 58 : kind === 'pl' ? 45 : 52;
    const R = mulberry(31);
    for (const s of [-1, 1]) {
      [['#d10a1c', '#7d0410', 0], ['#ffffff', '#b9bec6', 1], ['#d10a1c', '#7d0410', 2]].forEach(([c1, c2, k]) => {
        const x0 = s * (14 + k * 3.2), w = 3.4;
        const p = new Path2D();
        const pts = []; for (let y = top; y <= 104; y += 2) pts.push([x0 + s * (k * 1.6 + (y - top) * (0.22 + k * 0.06)) + Math.sin(y * 0.21 + k * 1.7 + s) * 1.6, y]);
        p.moveTo(pts[0][0] * u, pts[0][1] * u);
        for (const [x, y] of pts) p.lineTo(x * u, y * u);
        for (let i = pts.length - 1; i >= 0; i--) { const [x, y] = pts[i]; p.lineTo((x + s * w * Math.cos(y * 0.21 + k)) * u, (y + 0.6) * u); }
        p.closePath();
        const gr = g.createLinearGradient((x0 - 4) * u, 0, (x0 + 8 * s) * u, 0); gr.addColorStop(0, c1); gr.addColorStop(0.5, c2); gr.addColorStop(1, c1);
        g.fillStyle = gr; g.fill(p); g.strokeStyle = 'rgba(0,0,0,0.25)'; g.lineWidth = 1; g.stroke(p);
      });
    }
    void R;
  }
  const TROPHY_DRAW = {
    // Premier League: silver cup with lion handles and a gold crown, on a green stone base
    pl(g, u) {
      malachite(g, u, 73, 98, 18.5, 23.5);
      band(g, u, 71.5, 74, 19.5, 19.5); band(g, u, 97, 100, 24.2, 24.6);
      body(g, [[63, 6.5], [67, 9], [71.6, 16.5]], SILVER, u);
      body(g, [[55, 4.6], [64, 4.4]], SILVER, u);
      body(g, [[58, 7.2], [59.6, 7.6], [61.2, 7]], GOLD, u);
      // handles (lions) behind the bowl's edge
      for (const s of [-1, 1]) {
        handle(g, [[s * 17, 28.5], [s * 28, 26], [s * 31, 37], [s * 23, 42], [s * 19.5, 45], [s * 17, 46.5], [s * 14.5, 47]], 3.1, GOLD, u);
      }
      const bowl = body(g, [[24.5, 19], [28, 20.4], [36, 19.6], [46, 14], [53, 7.6], [56.5, 5]], SILVER, u);
      g.save(); g.clip(bowl);
      for (let i = -6; i <= 6; i++) { // fluting on the lower bowl
        g.beginPath(); g.moveTo(i * 3.2 * u, 39 * u); g.quadraticCurveTo(i * 2.3 * u, 49 * u, i * 0.7 * u, 56 * u);
        g.strokeStyle = 'rgba(30,34,38,0.38)'; g.lineWidth = 1.4; g.stroke();
        g.beginPath(); g.moveTo((i * 3.2 + 1.1) * u, 39 * u); g.quadraticCurveTo((i * 2.3 + 0.8) * u, 49 * u, (i * 0.7 + 0.3) * u, 56 * u);
        g.strokeStyle = 'rgba(255,255,255,0.28)'; g.lineWidth = 1; g.stroke();
      }
      g.beginPath(); g.moveTo(-20 * u, 37.5 * u); g.lineTo(20 * u, 37.5 * u); g.strokeStyle = 'rgba(40,44,50,0.5)'; g.lineWidth = 1.5; g.stroke();
      streak(g, -9 * u, 24 * u, 56 * u, 3.2 * u, 0.55); streak(g, 12 * u, 26 * u, 50 * u, 1.6 * u, 0.25);
      g.restore();
      // lions' heads where the handles meet the rim: a full mane, a muzzle, two eyes
      for (const s of [-1, 1]) {
        const hx = s * 25 * u, hy = 27.4 * u;
        g.save(); g.translate(hx, hy);
        const mane = new Path2D();
        for (let k = 0; k <= 28; k++) { const a = (k / 28) * TAU, rr = (k % 2 ? 4.3 : 5.2) * u; k ? mane.lineTo(Math.cos(a) * rr, Math.sin(a) * rr * 1.08) : mane.moveTo(Math.cos(a) * rr, Math.sin(a) * rr * 1.08); }
        mane.closePath();
        g.fillStyle = metal(g, -5.5 * u, 5.5 * u, GOLD, -5.5 * u, 5.5 * u); g.fill(mane); g.strokeStyle = 'rgba(80,45,5,0.55)'; g.lineWidth = 1; g.stroke(mane);
        g.beginPath(); g.ellipse(0, 0.3 * u, 3.1 * u, 3.4 * u, 0, 0, TAU); g.fillStyle = metal(g, -3 * u, 3 * u, [[0, '#a8711a'], [0.4, '#ffe08a'], [1, '#8a5a12']], -3 * u, 3 * u); g.fill();
        g.beginPath(); g.ellipse(0, 1.7 * u, 1.6 * u, 1.2 * u, 0, 0, TAU); g.fillStyle = 'rgba(110,65,10,0.55)'; g.fill();
        g.beginPath(); g.arc(-1.1 * u, -0.6 * u, 0.5 * u, 0, TAU); g.arc(1.1 * u, -0.6 * u, 0.5 * u, 0, TAU); g.fillStyle = 'rgba(55,30,4,0.85)'; g.fill();
        g.restore();
      }
      ellipseLip(g, 24.5, 19.6, 2.1, SILVER, u);
      body(g, [[17.5, 12.6], [20, 14.6], [23.4, 15.6]], SILVER, u); // lid
      // crown: red cap, gold band, five points with pearls
      g.beginPath(); g.ellipse(0, 12.5 * u, 11.5 * u, 9 * u, 0, PI, TAU); g.fillStyle = metal(g, -12 * u, 12 * u, [[0, '#4a0309'], [0.4, '#b3162a'], [0.6, '#d0233a'], [1, '#3d0207']]); g.fill();
      const tips = [[-12, 5.6], [-6.2, 3.2], [0, 0.8], [6.2, 3.2], [12, 5.6]];
      for (const [x, y] of tips) {
        const p = new Path2D(); p.moveTo((x - 2.6) * u, 13.6 * u); p.quadraticCurveTo((x - 2.2) * u, (y + 4) * u, x * u, (y + 1.4) * u); p.quadraticCurveTo((x + 2.2) * u, (y + 4) * u, (x + 2.6) * u, 13.6 * u); p.closePath();
        g.fillStyle = metal(g, (x - 3) * u, (x + 3) * u, GOLD, 0, 0); g.fill(p); g.strokeStyle = 'rgba(70,40,4,0.6)'; g.lineWidth = 1; g.stroke(p);
        g.beginPath(); g.arc(x * u, y * u, 1.45 * u, 0, TAU); g.fillStyle = metal(g, (x - 1.5) * u, (x + 1.5) * u, [[0, '#f6f1e2'], [0.5, '#ffffff'], [1, '#b9b2a0']], (y - 1.5) * u, (y + 1.5) * u); g.fill();
      }
      const bandP = sym([[-12.6 * u, 13 * u], [-13.4 * u, 18 * u]]);
      g.fillStyle = metal(g, -14 * u, 14 * u, GOLD); g.fill(bandP); g.strokeStyle = 'rgba(70,40,4,0.6)'; g.lineWidth = 1; g.stroke(bandP);
      [[-8.6, '#c1121f'], [-2.9, '#1a4fbf'], [2.9, '#c1121f'], [8.6, '#1a4fbf']].forEach(([x, c]) => { g.beginPath(); g.ellipse(x * u, 15.5 * u, 1.3 * u, 1.6 * u, 0, 0, TAU); g.fillStyle = c; g.fill(); g.strokeStyle = 'rgba(255,240,190,0.7)'; g.lineWidth = 0.8; g.stroke(); });
    },
    // FA Cup: the classic silver cup with a lid, two big looped handles, on a black base
    facup(g, u) {
      plinth(g, u, 78, 99, 17, 21);
      band(g, u, 77, 79, 17.6, 17.6); band(g, u, 97.5, 100, 21.4, 21.6);
      body(g, [[69, 5.5], [73, 9.5], [77.2, 15]], SILVER, u);
      body(g, [[61, 4], [69.5, 4.2]], SILVER, u);
      for (const s of [-1, 1]) handle(g, [[s * 17, 33], [s * 34, 30], [s * 37, 46], [s * 27, 51], [s * 22, 53], [s * 19, 54], [s * 16, 54]], 2.9, SILVER, u);
      const bowl = body(g, [[31, 18.5], [36, 21], [45, 20.5], [55, 13], [61.5, 5]], SILVER, u);
      g.save(); g.clip(bowl); streak(g, -8 * u, 31 * u, 61 * u, 3 * u, 0.55); streak(g, 10 * u, 33 * u, 56 * u, 1.5 * u, 0.25); g.restore();
      ellipseLip(g, 31, 19.2, 2.1, SILVER, u);
      body(g, [[19, 8.5], [24, 13.5], [30.5, 17]], SILVER, u); // lid
      body(g, [[8, 2.2], [11, 3.6], [14, 2.4], [16.5, 4.2], [19.5, 3]], SILVER, u); // finial
      g.beginPath(); g.arc(0, 6.6 * u, 2.6 * u, 0, TAU); g.fillStyle = metal(g, -3 * u, 3 * u, SILVER, -3 * u, 3 * u); g.fill(); g.strokeStyle = 'rgba(20,22,26,0.5)'; g.lineWidth = 1; g.stroke();
    },
    // League Cup: three handles, a tall lid with a ball on top, on a black base
    leaguecup(g, u) {
      plinth(g, u, 80, 99, 15, 18.5);
      band(g, u, 79, 81, 15.5, 15.5); band(g, u, 97.5, 100, 19, 19.2);
      body(g, [[70, 5], [75, 9], [79.3, 13.5]], SILVER, u);
      body(g, [[62, 3.6], [70.5, 3.8]], SILVER, u);
      for (const s of [-1, 1]) handle(g, [[s * 16, 36], [s * 30, 33], [s * 31, 48], [s * 21, 52], [s * 18, 54], [s * 16, 55], [s * 14, 55]], 2.6, SILVER, u);
      const bowl = body(g, [[34, 16.5], [40, 18.5], [50, 16], [58, 9], [62.5, 4.5]], SILVER, u);
      g.save(); g.clip(bowl); streak(g, -7 * u, 34 * u, 62 * u, 2.8 * u, 0.55); g.restore();
      handle(g, [[0, 38], [7, 40], [7, 50], [0, 51], [-7, 50], [-7, 40], [0, 38]], 1.8, SILVER, u); // the front handle
      ellipseLip(g, 34, 17, 2, SILVER, u);
      body(g, [[14, 6], [20, 10], [27, 13.5], [33.6, 15.5]], SILVER, u);
      g.beginPath(); g.arc(0, 10.5 * u, 4 * u, 0, TAU); g.fillStyle = metal(g, -4 * u, 4 * u, SILVER, -4 * u, 4 * u); g.fill(); g.strokeStyle = 'rgba(20,22,26,0.5)'; g.lineWidth = 1; g.stroke();
    },
    // Champions League: a tall silver vase with very large handles
    ucl(g, u) {
      body(g, [[88, 10], [94, 12], [100, 12.6]], SILVER, u);
      plinth(g, u, 92, 99, 11.6, 12.4, '#2c2e35');
      body(g, [[72, 4.6], [80, 4], [88.3, 9.6]], SILVER, u);
      for (const s of [-1, 1]) handle(g, [[s * 13, 20], [s * 46, 18], [s * 46, 62], [s * 22, 68], [s * 15, 70], [s * 11, 70], [s * 8, 69]], 4.6, SILVER, u);
      const v = body(g, [[16, 16.5], [24, 11], [34, 11.5], [50, 16.5], [62, 13], [72.5, 5]], SILVER, u);
      g.save(); g.clip(v); streak(g, -6 * u, 16 * u, 72 * u, 2.8 * u, 0.6); streak(g, 9 * u, 30 * u, 66 * u, 1.4 * u, 0.25); g.restore();
      ellipseLip(g, 16, 17, 2.2, SILVER, u);
    },
    // Europa League: no handles; a wide fluted cup on a slim stem and a stepped base
    uel(g, u) {
      body(g, [[84, 12], [88, 12.6]], SILVER, u);
      body(g, [[88, 15], [93, 16]], SILVER, u);
      body(g, [[93, 17.6], [100, 18.4]], SILVER, u);
      body(g, [[58, 4.6], [70, 3.6], [84.2, 6]], SILVER, u);
      const cup = body(g, [[9, 27], [15, 24], [30, 17], [45, 10], [58.3, 5]], SILVER, u);
      g.save(); g.clip(cup);
      for (let i = -9; i <= 9; i++) { g.beginPath(); g.moveTo(i * 3 * u, 9 * u); g.quadraticCurveTo(i * 2 * u, 34 * u, i * 0.5 * u, 58 * u); g.strokeStyle = i % 2 ? 'rgba(30,34,38,0.35)' : 'rgba(255,255,255,0.3)'; g.lineWidth = 1.3; g.stroke(); }
      streak(g, -7 * u, 9 * u, 58 * u, 3 * u, 0.5); g.restore();
      ellipseLip(g, 9, 27.4, 2.6, SILVER, u);
    },
    // any other competition: a two-handled cup on a black base
    generic(g, u) {
      plinth(g, u, 78, 99, 15, 19);
      band(g, u, 77, 79, 15.4, 15.4);
      body(g, [[66, 5], [72, 8.5], [77.3, 13]], SILVER, u);
      body(g, [[58, 4], [66.5, 4.2]], SILVER, u);
      for (const s of [-1, 1]) handle(g, [[s * 18, 30], [s * 33, 28], [s * 34, 44], [s * 22, 48], [s * 19, 50], [s * 16, 51], [s * 13, 52]], 2.8, SILVER, u);
      const bowl = body(g, [[26, 20], [32, 21.5], [44, 17], [54, 9], [58.5, 4.5]], SILVER, u);
      g.save(); g.clip(bowl); streak(g, -8 * u, 26 * u, 58 * u, 3 * u, 0.55); g.restore();
      ellipseLip(g, 26, 20.5, 2.3, SILVER, u);
    },
  };
  TROPHY_DRAW.uecl = TROPHY_DRAW.generic;

  // ---------------------------------------------------------------- crest medallions (drawn once a side)
  const MEDAL = {};
  // the crest's own bounds (its drawn pixels) and its main colour, to fill the circle edge to edge
  function crestLook(im) {
    const c0 = mk(im.width, im.height), g0 = c0.getContext('2d'); g0.drawImage(im, 0, 0);
    const d = g0.getImageData(0, 0, im.width, im.height).data;
    let x0 = im.width, y0 = im.height, x1 = 0, y1 = 0;
    const bins = new Map();
    for (let y = 0; y < im.height; y++) for (let x = 0; x < im.width; x++) {
      const i = (y * im.width + x) * 4;
      if (d[i + 3] < 128) continue;
      if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
      const r = d[i], gg = d[i + 1], bb = d[i + 2], mx = Math.max(r, gg, bb), mn = Math.min(r, gg, bb);
      if (mx < 45 || mx - mn < 60) continue; // leave out black, white and greys: the crest's colour
      const k = ((r >> 5) << 6) | ((gg >> 5) << 3) | (bb >> 5);
      const e = bins.get(k) || [0, 0, 0, 0]; e[0]++; e[1] += r; e[2] += gg; e[3] += bb; bins.set(k, e);
    }
    let best = null; for (const e of bins.values()) if (!best || e[0] > best[0]) best = e;
    const colour = best ? `rgb(${(best[1] / best[0]) | 0},${(best[2] / best[0]) | 0},${(best[3] / best[0]) | 0})` : '#20305a';
    return { x0, y0, w: x1 - x0 + 1, h: y1 - y0 + 1, colour };
  }
  // the crest big in a plain white circle, in its own shape (never stretched), like the recording's: scaled evenly so the
  // crest sits snug in the circle, its outermost corners just tucked under the rim; no ring and no edge
  function medallion(side) {
    if (MEDAL[side]) return MEDAL[side];
    const r = R_CREST, pad = 30, sz = 2 * (r + pad);
    const c = mk(sz, sz), g = c.getContext('2d');
    const cx = sz / 2, cy = sz / 2;
    g.save(); g.shadowColor = 'rgba(0,0,0,0.38)'; g.shadowBlur = 20; g.shadowOffsetY = 6;
    const fg = g.createRadialGradient(cx - r * 0.25, cy - r * 0.3, r * 0.1, cx, cy, r);
    fg.addColorStop(0, '#ffffff'); fg.addColorStop(0.75, '#f6f8fb'); fg.addColorStop(1, '#e9eef4');
    g.beginPath(); g.arc(cx, cy, r, 0, TAU); g.fillStyle = fg; g.fill(); g.restore();
    const im = IMG[side];
    if (im) {
      const L = crestLook(im);
      const k = ((2 * r) / Math.hypot(L.w, L.h)) * 1.13; // a little past fitting corner to corner
      const dw = L.w * k, dh = L.h * k;
      g.save(); g.beginPath(); g.arc(cx, cy, r - 0.5, 0, TAU); g.clip();
      g.shadowColor = 'rgba(0,0,0,0.2)'; g.shadowBlur = 5; g.shadowOffsetY = 2;
      g.imageSmoothingQuality = 'high';
      g.drawImage(im, L.x0, L.y0, L.w, L.h, cx - dw / 2, cy - dh / 2, dw, dh);
      g.restore();
    }
    MEDAL[side] = c;
    return c;
  }
  // tilt: the circle turned about its upright axis (radians); glow: the gold light behind and over it (0..1);
  // sheen: a gold glint passing across (0..1 as it goes, 0 when none)
  function drawMedal(g, side, x, y, scale, alpha, sheen, tilt = 0, glow = 0) {
    if (alpha <= 0.001 || scale <= 0.01) return;
    const c = medallion(side), sz = c.width * scale, r = R_CREST * scale;
    g.save(); g.globalAlpha = alpha;
    if (glow > 0.01) { // a warm golden halo round the circle
      g.save(); g.globalCompositeOperation = 'lighter';
      const hg = g.createRadialGradient(x, y, r * 0.7, x, y, r * 1.55);
      hg.addColorStop(0, `rgba(255,205,90,${(0.55 * glow).toFixed(3)})`); hg.addColorStop(0.45, `rgba(255,170,40,${(0.28 * glow).toFixed(3)})`); hg.addColorStop(1, 'rgba(255,150,20,0)');
      g.fillStyle = hg; g.fillRect(x - r * 1.6, y - r * 1.6, r * 3.2, r * 3.2);
      g.restore();
    }
    g.translate(x, y);
    const ct = Math.max(0.08, Math.cos(tilt));
    g.transform(ct, Math.sin(tilt) * 0.16, 0, 1, 0, 0); // turned: narrower, with a slight lean as it would in perspective
    g.imageSmoothingQuality = 'high';
    g.drawImage(c, -sz / 2, -sz / 2, sz, sz);
    // the gold over the logo: a warm wash from the light's side, and a gold glint passing across
    g.beginPath(); g.arc(0, 0, r - 0.5, 0, TAU); g.clip();
    const lx = -r * 0.6 - Math.sin(tilt) * r * 0.8, ly = -r * 0.7;
    const wg = g.createRadialGradient(lx, ly, 0, lx, ly, r * 2.1);
    wg.addColorStop(0, `rgba(255,214,120,${(0.18 + 0.4 * glow).toFixed(3)})`); wg.addColorStop(0.55, `rgba(255,190,70,${(0.06 + 0.16 * glow).toFixed(3)})`); wg.addColorStop(1, 'rgba(255,180,60,0)');
    g.globalCompositeOperation = 'soft-light'; g.fillStyle = wg; g.fillRect(-r, -r, 2 * r, 2 * r);
    g.globalCompositeOperation = 'lighter';
    const wg2 = g.createRadialGradient(lx, ly, 0, lx, ly, r * 1.6);
    wg2.addColorStop(0, `rgba(255,200,90,${(0.1 + 0.3 * glow).toFixed(3)})`); wg2.addColorStop(1, 'rgba(255,180,60,0)');
    g.fillStyle = wg2; g.fillRect(-r, -r, 2 * r, 2 * r);
    if (sheen > 0 && sheen < 1) {
      const sx = -r * 1.7 + sheen * r * 3.4;
      const sg = g.createLinearGradient(sx - r * 0.22, -r * 0.6, sx + r * 0.22, r * 0.6);
      sg.addColorStop(0, 'rgba(255,200,80,0)'); sg.addColorStop(0.5, 'rgba(255,224,140,0.55)'); sg.addColorStop(1, 'rgba(255,200,80,0)');
      g.fillStyle = sg; g.fillRect(-r, -r, 2 * r, 2 * r);
    }
    g.restore();
  }

  // ---------------------------------------------------------------- light
  function flare(g, x, y, size, a, warm = true) {
    if (a <= 0.003) return;
    g.save(); g.globalCompositeOperation = 'lighter';
    const c0 = warm ? [255, 236, 190] : [235, 245, 255], c1 = warm ? [255, 176, 60] : [140, 190, 255];
    const rg = g.createRadialGradient(x, y, 0, x, y, size);
    rg.addColorStop(0, `rgba(${c0},${a})`); rg.addColorStop(0.18, `rgba(${c1},${a * 0.55})`); rg.addColorStop(1, `rgba(${c1},0)`);
    g.fillStyle = rg; g.fillRect(x - size, y - size, size * 2, size * 2);
    for (const [w, h] of [[size * 3.2, size * 0.035], [size * 0.035, size * 1.6]]) {
      const lg = g.createLinearGradient(x - w, y, x + w, y);
      if (h > w) { const vg = g.createLinearGradient(x, y - h, x, y + h); vg.addColorStop(0, `rgba(${c0},0)`); vg.addColorStop(0.5, `rgba(${c0},${a * 0.85})`); vg.addColorStop(1, `rgba(${c0},0)`); g.fillStyle = vg; g.fillRect(x - w, y - h, 2 * w, 2 * h); }
      else { lg.addColorStop(0, `rgba(${c0},0)`); lg.addColorStop(0.5, `rgba(${c0},${a * 0.85})`); lg.addColorStop(1, `rgba(${c0},0)`); g.fillStyle = lg; g.fillRect(x - w, y - h, 2 * w, 2 * h); }
    }
    g.restore();
  }
  function sparkle(g, x, y, s, a) { // a small four-pointed glint
    if (a <= 0.01) return;
    g.save(); g.globalCompositeOperation = 'lighter'; g.translate(x, y);
    const rg = g.createRadialGradient(0, 0, 0, 0, 0, s * 0.5); rg.addColorStop(0, `rgba(255,255,255,${a})`); rg.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = rg; g.fillRect(-s * 0.5, -s * 0.5, s, s);
    g.fillStyle = `rgba(255,250,235,${a})`;
    for (const r of [0, PI / 2]) { g.save(); g.rotate(r + PI / 4 * 0.15); g.beginPath(); g.moveTo(-s, 0); g.lineTo(0, -s * 0.05); g.lineTo(s, 0); g.lineTo(0, s * 0.05); g.closePath(); g.fill(); g.restore(); }
    g.restore();
  }

  // ---------------------------------------------------------------- the tab's and the bar's contents
  const LOGO = {};
  const COMP_NAME = { pl: 'PREMIER LEAGUE', facup: 'FA CUP', leaguecup: 'CARABAO CUP', ucl: 'CHAMPIONS LEAGUE', uel: 'EUROPA LEAGUE', uecl: 'CONFERENCE LEAGUE' };
  function logoCanvas(comp) { // the logo trimmed to what is drawn in it
    const key = 'logo:' + comp;
    if (LOGO[key]) return LOGO[key];
    const im = LOGO[comp]; if (!im) return null;
    const c0 = mk(im.width, im.height), g0 = c0.getContext('2d'); g0.drawImage(im, 0, 0);
    const d = g0.getImageData(0, 0, im.width, im.height).data;
    let x0 = im.width, y0 = im.height, x1 = 0, y1 = 0;
    for (let y = 0; y < im.height; y++) for (let x = 0; x < im.width; x++) if (d[(y * im.width + x) * 4 + 3] > 16) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
    const c = mk(x1 - x0 + 1, y1 - y0 + 1); c.getContext('2d').drawImage(im, -x0, -y0);
    LOGO[key] = c;
    return c;
  }
  // one item of the tab: { w, draw(g, x, cy) }
  function tabItems(g, kind, m) {
    const items = [];
    const text = (s, w, size, ls, col) => { fontBC(g, w, size, ls); const tw = g.measureText(s).width - ls; return { w: tw, draw: (x, cy) => { fontBC(g, w, size, ls); g.textAlign = 'left'; g.textBaseline = 'alphabetic'; g.fillStyle = col || '#fff'; g.fillText(s, x, cy + capOf(size) / 2); } }; };
    const house = (s) => ({ w: s, draw: (x, cy) => tile(g, x, cy - s / 2, s) });
    if (kind === 'comp') {
      const lc = logoCanvas(m.comp || MATCH.comp);
      if (lc) { const h = 42, w = (lc.width * h) / lc.height; items.push({ w, draw: (x, cy) => { g.imageSmoothingQuality = 'high'; g.drawImage(lc, x, cy - h / 2, w, h); } }); }
      items.push(text((MATCH.compName || COMP_NAME[m.comp || MATCH.comp] || '').toUpperCase(), TABT.w, TABT.s, TABT.ls));
    } else if (kind === 'brand') {
      items.push(house(34));
      fontOut(g, 800, 29, 0); const w1 = g.measureText('whitebrook road').width;
      items.push({ w: w1, draw: (x, cy) => { fontOut(g, 800, 29, 0); g.textAlign = 'left'; g.textBaseline = 'middle'; g.fillStyle = '#fff'; g.fillText('whitebrook road', x, cy + 1); } });
      fontOut(g, 800, 25, 0); const w2 = g.measureText('sports').width + 18;
      items.push({ w: w2, gap: 8, draw: (x, cy) => { g.beginPath(); g.roundRect(x, cy - 16, w2, 32, 5); const rg = g.createLinearGradient(0, cy - 16, 0, cy + 16); rg.addColorStop(0, RED); rg.addColorStop(1, RED_DEEP); g.fillStyle = rg; g.fill(); fontOut(g, 800, 25, 0); g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillStyle = '#fff'; g.fillText('sports', x + w2 / 2, cy + 0.5); } });
    } else if (kind === 'live') { items.push(house(30)); items.push(text('LIVE FROM', TABT.w, TABT.s, TABT.ls)); }
    else if (kind === 'goal') { items.push(text('GOAL', 700, TABT.s, TABT.ls, '#ffd75a')); items.push(text(m.goalMin || '', TABT.w, TABT.s, TABT.ls)); }
    else if (kind === 'second') { items.push(house(30)); items.push(text('SECOND HALF', TABT.w, TABT.s, TABT.ls)); }
    return items;
  }
  function tabWidth(items) { let w = 0; items.forEach((it, i) => { w += it.w + (i ? (it.gap || 14) : 0); }); return Math.max(300, w + 56); }
  function drawTabContent(g, items, cy, a, blur) {
    if (a <= 0.01) return;
    let w = 0; items.forEach((it, i) => { w += it.w + (i ? (it.gap || 14) : 0); });
    let x = 960 - w / 2;
    g.save(); g.globalAlpha *= a; if (blur > 0.2) g.filter = `blur(${blur.toFixed(1)}px)`;
    items.forEach((it, i) => { if (i) x += it.gap || 14; it.draw(x, cy); x += it.w; });
    g.restore();
  }
  function barText(g, kind, m, cy, skew) {
    g.save();
    g.translate(960, cy); g.transform(1, 0, skew, 1, 0, 0); g.translate(-960, -cy);
    g.textBaseline = 'alphabetic'; g.fillStyle = '#fff';
    g.shadowColor = 'rgba(0,0,0,0.55)'; g.shadowBlur = 4; g.shadowOffsetY = 2;
    fontBC(g, NAME.w, NAME.s, NAME.ls);
    // a long name: a little narrower first (to 78%), then smaller as well, always within its room and centred on the bar
    const fit = (s, room) => { const tw = g.measureText(s).width - NAME.ls, k = tw > room ? room / tw : 1, kx = Math.max(0.78, k); return [kx, k / kx]; };
    const put = (s, x, room, align, dx) => {
      const [kx, ks] = fit(s, room);
      g.save(); g.translate(x, cy - 3); g.scale(kx * ks, ks); g.translate(0, capOf(NAME.s) / 2);
      g.textAlign = align; g.fillText(s, dx, 0);
      g.restore();
    };
    if (kind === 'vs' || kind === 'score') {
      const gap = kind === 'vs' ? 78 : 100;   // the names stand 38 px clear of the centre box
      const room = 960 - gap - (OPEN_L + R_CREST + 22);
      put(MATCH.home, 960 - gap, room, 'right', NAME.ls);
      put(MATCH.away, 960 + gap, room, 'left', 0);
    } else {
      const s = kind === 'stadium' ? MATCH.stadium : (m.scorer || '');
      put(s, 960, OPEN_R - OPEN_L - 2 * (R_CREST + 22), 'center', NAME.ls / 2);
    }
    g.restore();
  }
  function centreBox(g, kind, m, cy, sc) { // "V" between the teams, or the score
    if (sc <= 0.01 || (kind !== 'vs' && kind !== 'score')) return;
    const score = kind === 'score';
    const w = score ? 124 : 76, h = BAR_H + 2;   // the recording's box: 77 x 90 at 1080p
    g.save(); g.translate(960, cy); g.scale(sc, sc);
    g.shadowColor = 'rgba(0,0,0,0.45)'; g.shadowBlur = 8; g.shadowOffsetY = 2;
    g.beginPath(); g.roundRect(-w / 2, -h / 2, w, h, 4);
    const bg = g.createLinearGradient(0, -h / 2, 0, h / 2); bg.addColorStop(0, '#ffffff'); bg.addColorStop(0.5, '#e9edf2'); bg.addColorStop(0.52, '#d3dae3'); bg.addColorStop(1, '#f4f6f9');
    g.fillStyle = bg; g.fill(); g.shadowColor = 'transparent';
    g.strokeStyle = 'rgba(10,20,50,0.35)'; g.lineWidth = 1.2; g.stroke();
    const fsz = score ? 60 : 52, fls = score ? 2 : 0;   // the V's capitals 36 px, as in the recording
    fontBC(g, 700, fsz, fls); g.textAlign = 'center'; g.textBaseline = 'alphabetic'; g.fillStyle = '#0b1d4d';
    g.fillText(score ? `${m.score[0]}-${m.score[1]}` : 'V', fls / 2, capOf(fsz) / 2 + 0.5);
    g.restore();
  }
  function drawBar(g, x0, x1, cy) {
    if (x1 - x0 < 2) return;
    const top = cy - BAR_H / 2;
    g.save();
    g.beginPath(); g.rect(x0, top - 2, x1 - x0, BAR_H + 4); g.clip();
    g.shadowColor = 'rgba(0,0,0,0.5)'; g.shadowBlur = 16; g.shadowOffsetY = 5;
    const bg = g.createLinearGradient(0, top, 0, top + BAR_H);
    bg.addColorStop(0, '#2d55a6'); bg.addColorStop(0.07, '#1b3a80'); bg.addColorStop(0.45, '#0d2159'); bg.addColorStop(0.55, '#0a1a49'); bg.addColorStop(1, '#030b28');
    g.fillStyle = bg; g.fillRect(BAR_L, top, BAR_R - BAR_L, BAR_H);
    g.shadowColor = 'transparent';
    const ends = g.createLinearGradient(BAR_L, 0, BAR_R, 0); // lighter towards the middle
    ends.addColorStop(0, 'rgba(0,0,0,0.25)'); ends.addColorStop(0.5, 'rgba(90,140,255,0.12)'); ends.addColorStop(1, 'rgba(0,0,0,0.25)');
    g.fillStyle = ends; g.fillRect(BAR_L, top, BAR_R - BAR_L, BAR_H);
    g.fillStyle = 'rgba(190,215,255,0.65)'; g.fillRect(BAR_L, top, BAR_R - BAR_L, 2);
    g.fillStyle = 'rgba(0,0,0,0.6)'; g.fillRect(BAR_L, top + BAR_H - 2, BAR_R - BAR_L, 2);
    g.restore();
  }
  function drawTab(g, w, cy, rise) { // the dark tab on top of the bar; rise 0..1 slides it up from behind the bar
    const bot = cy - BAR_H / 2, top = bot - TAB_H + (1 - rise) * TAB_H;
    if (rise <= 0.01) return null;
    g.save();
    g.beginPath(); g.rect(0, 0, W, bot); g.clip();
    g.shadowColor = 'rgba(0,0,0,0.45)'; g.shadowBlur = 10; g.shadowOffsetY = -1;
    g.beginPath(); g.roundRect(960 - w / 2, top, w, TAB_H + 6, [7, 7, 0, 0]);
    const bg = g.createLinearGradient(0, top, 0, top + TAB_H);
    bg.addColorStop(0, '#4a4e57'); bg.addColorStop(0.08, '#2a2d34'); bg.addColorStop(0.5, '#15171c'); bg.addColorStop(1, '#0a0b0e');
    g.fillStyle = bg; g.fill(); g.shadowColor = 'transparent';
    g.fillStyle = 'rgba(255,255,255,0.35)'; g.fillRect(960 - w / 2 + 6, top + 1, w - 12, 1.5);
    g.restore();
    return { top, mid: top + TAB_H / 2, clipBottom: bot };
  }

  // ---------------------------------------------------------------- one pop-up, T seconds after it starts
  const KINDS = {
    kickoff: { pages: [['comp', 'vs'], ['brand', 'vs'], ['live', 'stadium']], steps: [[2.9, 'tab'], [4.2, 'flip']], close: 6.6 },
    restart: { pages: [['comp', 'score'], ['goal', 'scorer']], steps: [[2.3, 'flip']], close: 4.4 },
    second: { pages: [['comp', 'score'], ['second', 'score']], steps: [[2.3, 'tab']], close: 4.4 },
    still: { pages: [['comp', 'vs']], steps: [], close: 9 },
  };
  function popLen(m) { return KINDS[m.kind].close + 0.62; }
  function drawPopup(g, m, T) {
    const K = KINDS[m.kind], tc = K.close;
    // ---- where things are
    const inK = eOutBack(ramp(0.06, 0.42, T), 1.3);
    const sIn = lerp(1.9, 1, inK);
    const openK = eInOut3(ramp(0.56, 0.84, T));
    const closeK = eIn3(ramp(tc + 0.12, tc + 0.36, T));
    const together = R_CREST * 0.97;
    const spread = clamp(openK - closeK, 0, 1);
    const sc = T < 0.42 ? sIn : 1;
    const lx = lerp(960 - together * sc, OPEN_L, spread), rx = lerp(960 + together * sc, OPEN_R, spread);
    const endK = ramp(tc + 0.36, tc + 0.6, T);
    const medScale = sc * lerp(1, 0.72, eIn3(endK));
    const medAlpha = ramp(0.06, 0.14, T) * (1 - endK);
    const rise = (1 - eOut3(ramp(0.14, 0.62, T))) * 300 + eIn3(ramp(tc + 0.15, tc + 0.55, T)) * 260;
    const trA = ramp(0.14, 0.3, T) * (1 - ramp(tc + 0.3, tc + 0.55, T));
    // ---- trophy, behind everything: the 3D model rises turning in, sways a little, and turns away as it sinks
    const kind3 = m.trophy || MATCH.trophy || MATCH.comp;
    if (trA > 0.01) {
      g.save(); g.globalAlpha = trA;
      if (window.TROPHY3D) {
        const turnIn = 1 - eOut3(ramp(0.14, 0.8, T));
        const rotY = -1.25 * turnIn + 0.13 * Math.sin(T * 0.75) + 1.0 * eIn3(ramp(tc + 0.12, tc + 0.55, T));
        const tc3 = window.TROPHY3D.render(kind3, rotY, 0, T);
        const fit = T3_FIT[kind3] || T3_FIT.generic;
        const k = (T3_H * fit[0]) / (tc3.height * 0.9);
        const dw = tc3.width * k, dh = tc3.height * k;
        g.imageSmoothingQuality = 'high';
        g.drawImage(tc3, 960 - dw / 2, T_TOP - tc3.height * 0.08 * k + fit[1] + rise, dw, dh);
      } else {
        g.drawImage(tr, 960 - tr.width / 2, T_TOP - 10 + rise);
      }
      g.restore();
      // glints on the trophy's top and shoulders
      const gl = [[-62, 18, 1.2], [0, 4, 2.4], [62, 18, 3.6], [-48, 140, 5.0], [55, 175, 0.6]];
      const gf = T3_FIT[kind3] || T3_FIT.generic;   // the glints follow the trophy's size and its top
      for (const [x, y, ph] of gl) { const a = Math.pow(Math.max(0, Math.sin(T * 2.1 + ph * 1.7)), 14) * 0.9 * trA; sparkle(g, 960 + x * gf[2], T_TOP + gf[3] + y * gf[2] + rise, 26 * Math.max(0.7, gf[2]), a); }
    }
    // the light that comes with it
    flare(g, 960, CY - 30, 220, 0.85 * Math.sin(PI * ramp(0, 0.5, T)));
    const beam = Math.sin(PI * ramp(0.28, 0.72, T));
    if (beam > 0.01) {
      g.save(); g.globalCompositeOperation = 'lighter';
      const bx = 960 + 64, bg = g.createLinearGradient(bx - 16, 0, bx + 16, 0);
      bg.addColorStop(0, 'rgba(255,255,255,0)'); bg.addColorStop(0.5, `rgba(255,255,255,${0.75 * beam})`); bg.addColorStop(1, 'rgba(255,255,255,0)');
      const vg = g.createLinearGradient(0, 600 + rise * 0.3, 0, 960); vg.addColorStop(0, 'rgba(0,0,0,1)'); vg.addColorStop(1, 'rgba(0,0,0,0)');
      g.fillStyle = bg; g.fillRect(bx - 16, 600, 32, 330);
      g.globalCompositeOperation = 'source-over';
      const cg = g.createLinearGradient(0, 600, 0, 930); cg.addColorStop(0, 'rgba(255,255,255,0)'); cg.addColorStop(0.35, `rgba(255,255,255,${0.85 * beam})`); cg.addColorStop(1, 'rgba(255,255,255,0)');
      g.fillStyle = cg; g.fillRect(bx - 2.5, 600, 5, 330);
      g.restore();
      flare(g, bx, 640 + rise * 0.6, 120, 0.6 * beam, true);
    }
    // ---- what the tab and the bar say now
    let page = 0, flipK = -1, swapK = -1;
    K.steps.forEach(([ts, ty], i) => {
      const dur = ty === 'flip' ? 0.4 : 0.42;
      if (T >= ts + dur) page = i + 1;
      else if (T >= ts) { page = i; if (ty === 'flip') flipK = (T - ts) / dur; else swapK = (T - ts) / dur; }
    });
    const last = K.pages.length - 1;
    const cur = Math.min(flipK >= 0.5 ? page + 1 : page, last);
    const [tabKind, mainKind] = K.pages[cur];
    // the flip turns the bar (and its tab) over about its middle line
    let sy = 1, skew = 0, shade = 0;
    if (flipK >= 0) { sy = Math.abs(Math.cos(PI * flipK)); skew = -0.32 * Math.sin(PI * flipK); shade = Math.sin(PI * flipK); }
    const tabRise = eOut3(ramp(0.8, 1.0, T));
    const tabGone = ramp(tc, tc + 0.15, T);
    g.save();
    g.translate(0, CY); g.scale(1, Math.max(0.02, sy)); g.translate(0, -CY);
    // the tab: on a change its words go, a light sweeps across, the new words come in out of a blur
    const items = tabItems(g, tabKind, m);
    let tw = tabWidth(items), oldItems = null, newItems = null;
    if (swapK >= 0) {
      oldItems = items; newItems = tabItems(g, K.pages[Math.min(page + 1, last)][0], m);
      tw = lerp(tabWidth(oldItems), tabWidth(newItems), eInOut3(clamp((swapK - 0.25) / 0.5)));
    }
    if (tabGone < 1 && openK > 0.6) {
      const tb = drawTab(g, tw, CY, tabRise * (1 - eIn3(tabGone)));
      if (tb) {
        g.save(); g.beginPath(); g.rect(0, 0, W, tb.clipBottom); g.clip();
        if (swapK >= 0) {
          if (swapK < 0.38) drawTabContent(g, oldItems, tb.mid, 1 - swapK / 0.38, 0);
          else { const k = (swapK - 0.38) / 0.62; drawTabContent(g, newItems, tb.mid, k, 8 * (1 - k)); }
          const sx = lerp(960 - tw / 2 - 80, 960 + tw / 2 + 80, clamp(swapK / 0.5));
          const sg = g.createLinearGradient(sx - 60, 0, sx + 60, 0); sg.addColorStop(0, 'rgba(255,255,255,0)'); sg.addColorStop(0.5, 'rgba(255,255,255,0.35)'); sg.addColorStop(1, 'rgba(255,255,255,0)');
          g.globalCompositeOperation = 'lighter'; g.fillStyle = sg; g.fillRect(960 - tw / 2, tb.top, tw, TAB_H);
        } else drawTabContent(g, items, tb.mid, 1 - tabGone, 6 * tabGone);
        g.restore();
      }
    }
    // bar between the crests
    const bx0 = Math.max(BAR_L, lx + R_CREST * 0.35), bx1 = Math.min(BAR_R, rx - R_CREST * 0.35);
    const barA = clamp(spread * 6) * (1 - endK);
    g.save(); g.globalAlpha *= barA;
    drawBar(g, bx0, bx1, CY);
    g.restore();
    if (bx1 - bx0 > 4 && barA > 0.01) {
      g.save(); g.beginPath(); g.rect(bx0, CY - BAR_H / 2, bx1 - bx0, BAR_H); g.clip();
      const ta = (1 - ramp(tc + 0.08, tc + 0.24, T)) * barA;
      g.globalAlpha = ta;
      barText(g, mainKind, m, CY, skew);
      if (shade > 0) { g.fillStyle = `rgba(0,0,20,${0.45 * shade})`; g.fillRect(bx0, CY - BAR_H / 2, bx1 - bx0, BAR_H); }
      const sw = ramp(0.86, 1.3, T); // a sheen along the bar as it opens
      if (sw > 0 && sw < 1) {
        const sx = lerp(bx0 - 200, bx1 + 200, sw), sg = g.createLinearGradient(sx - 120, 0, sx + 120, 0);
        sg.addColorStop(0, 'rgba(255,255,255,0)'); sg.addColorStop(0.5, 'rgba(160,200,255,0.28)'); sg.addColorStop(1, 'rgba(255,255,255,0)');
        g.globalCompositeOperation = 'lighter'; g.fillStyle = sg; g.fillRect(bx0, CY - BAR_H / 2, bx1 - bx0, BAR_H);
      }
      g.restore();
    }
    // the V (or score) between the crests
    const vIn = eOutBack(ramp(0.44, 0.6, T), 1.6) * (1 - ramp(tc + 0.05, tc + 0.2, T));
    centreBox(g, mainKind, m, CY, vIn);
    g.restore();
    // crests, in front: they turn in as they fly in, lean as they slide, rock gently while open, turn as they meet
    const inTilt = (1 - eOutBack(ramp(0.06, 0.62, T), 1.4)) * 1.15;
    const slide = Math.sin(PI * clamp(openK)) * 0.42 * (1 - closeK) + Math.sin(PI * clamp(closeK)) * 0.5;
    const rock = 0.3 * Math.sin(T * 1.25) * ramp(0.9, 1.5, T) * (1 - ramp(tc, tc + 0.2, T));
    const outTilt = eIn3(endK) * 1.2;
    const tiltL = -inTilt - slide + rock - outTilt, tiltR = inTilt + slide - rock * 0.9 + outTilt;
    const glow = 0.95 * Math.sin(PI * ramp(0.0, 0.75, T)) + 0.42 + 0.1 * Math.sin(T * 2.3) + 0.9 * Math.sin(PI * ramp(tc + 0.15, tc + 0.6, T));
    const sheenL = ramp(1.35, 2.1, T) + ramp(5.0, 5.75, T), sheenR = ramp(1.5, 2.25, T) + ramp(5.15, 5.9, T);
    drawMedal(g, 'home', lx, CY, medScale, medAlpha, sheenL % 1 === 0 ? 0 : sheenL % 1, tiltL, Math.min(1.2, glow));
    drawMedal(g, 'away', rx, CY, medScale, medAlpha, sheenR % 1 === 0 ? 0 : sheenR % 1, tiltR, Math.min(1.2, glow));
    // flashes: as they arrive, and where they meet when it closes
    flare(g, 960, CY, 150, 0.8 * Math.sin(PI * ramp(0.04, 0.3, T)));
    const meet = Math.sin(PI * ramp(tc + 0.28, tc + 0.56, T));
    flare(g, 960, CY - 10, 230, 0.95 * meet);
    flare(g, 960, CY - 70, 90, 0.7 * Math.sin(PI * ramp(tc + 0.1, tc + 0.4, T)));
  }

  // ---------------------------------------------------------------- the frame
  const scene = mk(W, H), sctx = scene.getContext('2d');
  const quarter = mk(W / 4, H / 4), qctx = quarter.getContext('2d');
  function bloom(g) {
    if (!(CFG.bloom > 0)) return;
    qctx.filter = 'none'; qctx.fillStyle = '#000'; qctx.fillRect(0, 0, W / 4, H / 4);
    qctx.filter = 'brightness(0.5) contrast(5) blur(7px)';
    qctx.drawImage(scene, 0, 0, W / 4, H / 4);
    qctx.filter = 'none';
    g.save(); g.globalCompositeOperation = 'screen'; g.globalAlpha = CFG.bloom * 0.6;
    g.drawImage(quarter, 0, 0, W, H);
    g.restore();
  }
  function caption(g, text, n, a) {
    if (a <= 0.01) return;
    g.save(); g.globalAlpha = a;
    fontOut(g, 600, 26, 0.3); const w = g.measureText(text).width + 92;
    g.beginPath(); g.roundRect(56, 52, w, 54, 14); g.fillStyle = 'rgba(8,10,20,0.62)'; g.fill();
    g.strokeStyle = 'rgba(255,255,255,0.16)'; g.lineWidth = 1.5; g.stroke();
    g.beginPath(); g.arc(56 + 30, 79, 15, 0, TAU); g.fillStyle = RED; g.fill();
    fontOut(g, 800, 18, 0); g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillStyle = '#fff'; g.fillText(String(n), 56 + 30, 80);
    fontOut(g, 600, 26, 0.3); g.textAlign = 'left'; g.fillText(text, 56 + 56, 80);
    g.restore();
  }
  function momentAt(t) {
    let cur = POP.moments[0];
    for (const m of POP.moments) if (t >= m.t0 - (m.lead || 1.2)) cur = m;
    return cur;
  }
  function drawFrame(t) {
    ctx.save();
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over'; ctx.filter = 'none';
    const mom = momentAt(t);
    const mirror = !!(mom && mom.mirror), kicks = (mom && mom.kicksOff) || 'home';
    if (POP.background !== 'none' && `${mirror}|${kicks}` !== curKey) { placeTeams(mirror, kicks); curKey = `${mirror}|${kicks}`; }
    if (POP.background !== 'none') {
      renderScene(sctx, broadcastCam(t), t); grade(sctx); bloom(sctx);
      ctx.drawImage(scene, 0, 0);
    } else { ctx.clearRect(0, 0, W, H); }
    // between moments: a quick dip, as a cut in the mock-up
    for (const m of POP.moments) {
      const T = t - m.t0;
      if (T >= -0.001 && T <= popLen(m)) drawPopup(ctx, m, T);
      if (m.label && POP.captions !== false) {
        const a = ramp(m.t0 - 1.1, m.t0 - 0.7, t) * (1 - ramp(m.t0 + popLen(m) + 0.2, m.t0 + popLen(m) + 0.6, t));
        caption(ctx, m.label, m.n, a);
      }
    }
    const dip = POP.cuts ? Math.max(0, ...POP.cuts.map((c) => 1 - Math.abs(t - c) / 0.25)) : 0;
    if (dip > 0) { ctx.fillStyle = `rgba(0,0,0,${dip.toFixed(3)})`; ctx.fillRect(0, 0, W, H); }
    ctx.restore();
  }

  function grade(g) {
    if (CFG.grade === 'night') { g.fillStyle = 'rgba(20,40,70,0.07)'; g.globalCompositeOperation = 'screen'; g.fillRect(0, 0, W, H); g.globalCompositeOperation = 'source-over'; }
  }

  async function init() {
    const t0 = performance.now();
    for (const w of [500, 600, 700, 800]) await document.fonts.load(`${w} 40px Outfit`);
    for (const w of [600, 700, 800]) await document.fonts.load(`${w} 40px 'Barlow Condensed'`);
    for (const w of [600, 700]) await document.fonts.load(`${w} 40px 'KhandM'`, 'AZaz09&ØÖÉ');
    await document.fonts.ready;
    const A = window.ASSET_URLS || {};
    // a crest or logo that will not come is left out (a plain coin, no logo) rather than stopping the pop-up
    const img = async (url) => { if (!url) return null; try { const im = new Image(); im.src = url; await im.decode(); return im; } catch (e) { return null; } };
    for (const side of ['home', 'away']) { const im = await img(A[side]); if (im) IMG[side] = im; }
    for (const [k, url] of Object.entries(A.logos || {})) { const im = await img(url); if (im) LOGO[k] = im; }
    if (POP.background !== 'none') { GL = groundLight(); buildCrowd(); buildSky(); }   // the live overlay draws no ground
    window.RIBBON_COLORS = [TEAMS.home.c1, TEAMS.away.c1];
    for (let i = 0; i < 50 && !window.TROPHY3D_READY; i++) await new Promise((r) => setTimeout(r, 100));
    if (!window.TROPHY3D) for (const k of Object.keys(TROPHY_DRAW)) trophyCanvas(k);
    medallion('home'); medallion('away');
    window.DUR = POP.dur;
    window.SOL = SOL;
    window.INIT_MS = performance.now() - t0;
    window.drawFrame = drawFrame;
    window.READY = true;
    if (window.onBroadcastReady) window.onBroadcastReady();
  }
  init().catch((e) => { window.INIT_ERR = String(e && e.stack || e); if (window.onBroadcastFailed) window.onBroadcastFailed(window.INIT_ERR); });
})();
