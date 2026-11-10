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

  // =====================================================================================================================
  // Classic beam-in line-ups (after the old broadcast style the family sent): each team by position, the players beaming
  // in one by one in front of the ground, chrome position titles on a blue bar, a crest coin between the parts, the subs
  // together, then the formation on a tilted pitch. Whitebrook Road Sports' own marks; everything drawn here.
  // =====================================================================================================================
  // The lettering, matched by measurement to the user's two clips: Khand (free, SIL Open Font Licence) is the closest
  // free match to the broadcaster's own typeface, which isn't available to use. Name tabs in weight 600 (the clips'
  // stroke), the chrome titles in 700 widened by 22%, the team's name in 700 widened by 10% and slanted, as in the reel.
  const CF = 'KhandM';
  const capK = (w, fs) => fs * (w >= 700 ? 0.693 : 0.685);   // Khand's capital height
  const TITLE_SX = 1.22, BAND_SX = 1.1, BAND_SLANT = 0.21;
  // a tab's lettering: capitals 62% of the tab's height, the name set a little open
  function tabType(h) { return { fsN: (h * 0.62) / 0.693, fsT: (h * 0.62) / 0.685, lsT: h * 0.05 }; }
  function tabWidth(g, num, name, h) {
    const { fsN, fsT, lsT } = tabType(h);
    return Math.max(h * 0.92, cmeasure(g, num, 700, fsN, 0) + h * 0.34) + h * 0.07 + cmeasure(g, name, 600, fsT, lsT) + h * 0.52;
  }
  const BAR_Y = 918;
  const rnd = (i) => { const x = Math.sin(i * 12.9898 + 78.233) * 43758.5453; return x - Math.floor(x); };
  function cfont(g, w, s, ls, it) { g.font = `${it ? 'italic ' : ''}${w} ${s}px ${CF}`; g.letterSpacing = `${ls || 0}px`; }
  function ctxt(g, s, x, y, o = {}) {
    cfont(g, o.w || 700, o.s || 40, o.ls || 0, o.it);
    g.textAlign = o.al || 'left'; g.textBaseline = o.bl || 'alphabetic';
    g.fillStyle = o.c || '#fff';
    if (o.al === 'center' && o.ls) x += o.ls / 2;
    g.fillText(s, x, y);
  }
  function cmeasure(g, s, w, sz, ls, it) { cfont(g, w, sz, ls, it); return g.measureText(s).width - (ls || 0); }
  const rgbS = (c, k = 1, a = 1) => `rgba(${Math.max(0, Math.min(255, c[0] * k)) | 0},${Math.max(0, Math.min(255, c[1] * k)) | 0},${Math.max(0, Math.min(255, c[2] * k)) | 0},${a})`;

  // ---------------------------------------------------------------------------------------------------- names, groups
  function shortNames(side) {
    const team = DATA[side], all = team.xi.concat(team.subs), out = {};
    const count = {};
    for (const p of all) { const k = (p.last || p.name).toUpperCase(); count[k] = (count[k] || 0) + 1; }
    for (const p of all) {
      let k = (p.last || p.name).toUpperCase();
      if (count[k] > 1) k = (p.name || '').trim()[0].toUpperCase() + ' ' + k;
      out[p.id] = k;
    }
    return out;
  }
  const NAMES = { home: shortNames('home'), away: shortNames('away') };
  function groupsOf(side) {
    const team = DATA[side];
    const nums = team.formation.split('-').map(Number);
    const gk = team.xi.filter((p) => p.gk);
    const out = team.xi.filter((p) => !p.gk).slice().sort((a, b) => a.y - b.y || b.x - a.x);
    const nDef = nums[0], nAtt = nums[nums.length - 1];
    const byX = (arr) => arr.slice().sort((a, b) => b.x - a.x); // right-back first, on the left of the screen
    return [
      { key: 'gk', title: 'GOALKEEPER', players: gk },
      { key: 'def', title: 'DEFENCE', players: byX(out.slice(0, nDef)) },
      { key: 'mid', title: 'MIDFIELD', players: byX(out.slice(nDef, out.length - nAtt)) },
      { key: 'att', title: 'ATTACK', players: byX(out.slice(out.length - nAtt)) },
    ];
  }

  // ---------------------------------------------------------------------------------------------------- the figures
  // Each player is drawn once: the FotMob head on a kit body drawn here (the pictures stop at the neck), plus a blue
  // hologram copy and its glow for the beam-in. Units of P (the head picture's size); the neck's foot is the origin.
  const P0 = 228;
  const FIG = {};
  function trimOf(side, gk) {
    if (side === 'home') return gk ? [16, 92, 80] : [246, 246, 246];
    return gk ? [16, 30, 92] : [20, 20, 22];
  }
  function skinOf(im) {
    const c = mk(64, 64), g = c.getContext('2d');
    g.drawImage(im, 0, 0, 64, 64);
    const d = g.getImageData(26, 46, 12, 10).data;
    let r = 0, gg = 0, b = 0, n = 0;
    for (let i = 0; i < d.length; i += 4) if (d[i + 3] > 220) { r += d[i]; gg += d[i + 1]; b += d[i + 2]; n++; }
    return n > 6 ? [r / n, gg / n, b / n] : [198, 152, 122];
  }
  // the Premier League's own cut-outs (head to the waist, in the kit), keyed by FotMob id; known before the page loads
  const PLPHOTO = {};
  const PL_IDS = new Set(Object.keys((window.ASSET_URLS && window.ASSET_URLS.plphotos) || {}));
  const HD0 = 670;
  function buildPhotoFigure(side, person) {
    const key = side + ':' + person.id;
    const im = PLPHOTO[person.id], S0 = HD0;
    const c = mk(S0, S0), g = c.getContext('2d');
    g.imageSmoothingQuality = 'high';
    g.drawImage(im, 0, 0, S0, S0);
    // the sleeves run to the picture's edges: feather the sides; the very foot goes behind the bar anyway
    g.globalCompositeOperation = 'destination-in';
    const fx = g.createLinearGradient(0, 0, S0, 0);
    fx.addColorStop(0, 'rgba(0,0,0,0)'); fx.addColorStop(0.06, 'rgba(0,0,0,1)'); fx.addColorStop(0.94, 'rgba(0,0,0,1)'); fx.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = fx; g.fillRect(0, 0, S0, S0);
    const fy = g.createLinearGradient(0, S0 * 0.955, 0, S0);
    fy.addColorStop(0, 'rgba(0,0,0,1)'); fy.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = fy; g.fillRect(0, 0, S0, S0);
    g.globalCompositeOperation = 'source-over';
    // the hologram copy for the beam-in: the picture's shape in blue light with scan lines
    const h = mk(S0, S0), hg = h.getContext('2d');
    hg.drawImage(c, 0, 0);
    hg.globalCompositeOperation = 'source-atop';
    const hgr = hg.createLinearGradient(0, 0, 0, S0);
    hgr.addColorStop(0, 'rgb(140,185,250)'); hgr.addColorStop(0.4, 'rgb(55,110,225)'); hgr.addColorStop(1, 'rgb(20,55,190)');
    hg.fillStyle = hgr; hg.fillRect(0, 0, S0, S0);
    hg.globalCompositeOperation = 'luminosity'; hg.globalAlpha = 0.5; hg.drawImage(c, 0, 0); hg.globalAlpha = 1;
    hg.globalCompositeOperation = 'destination-out';
    hg.fillStyle = 'rgba(0,0,0,0.5)';
    for (let y = 0; y < S0; y += 4) hg.fillRect(0, y, S0, 1.6);
    hg.globalCompositeOperation = 'source-over';
    const gl = mk(Math.round(S0 / 2), Math.round(S0 / 2)), glg = gl.getContext('2d');
    glg.filter = 'blur(9px)'; glg.drawImage(h, 0, 0, gl.width, gl.height); glg.filter = 'none';
    glg.globalCompositeOperation = 'lighter'; glg.drawImage(gl, 0, 0);
    // the bust for the formation pitch: head, shoulders and the top of the chest, soft at the foot and sides
    const bw = Math.round(S0 * 0.8), bh = Math.round(S0 * 0.5);
    const bust = mk(bw, bh), bg2 = bust.getContext('2d');
    bg2.drawImage(c, S0 * 0.1, 0, bw, bh, 0, 0, bw, bh);
    bg2.globalCompositeOperation = 'destination-in';
    const byg = bg2.createLinearGradient(0, bh * 0.7, 0, bh); byg.addColorStop(0, 'rgba(0,0,0,1)'); byg.addColorStop(1, 'rgba(0,0,0,0)');
    bg2.fillStyle = byg; bg2.fillRect(0, 0, bw, bh);
    const bxg = bg2.createLinearGradient(0, 0, bw, 0); bxg.addColorStop(0, 'rgba(0,0,0,0)'); bxg.addColorStop(0.1, 'rgba(0,0,0,1)'); bxg.addColorStop(0.9, 'rgba(0,0,0,1)'); bxg.addColorStop(1, 'rgba(0,0,0,0)');
    bg2.fillStyle = bxg; bg2.fillRect(0, 0, bw, bh);
    bg2.globalCompositeOperation = 'source-over';
    return (FIG[key] = { kind: 'photo', c, holo: h, glow: gl, bust, P: S0 });
  }
  function buildFigure(side, person) {
    const key = side + ':' + person.id;
    if (FIG[key]) return FIG[key];
    if (PLPHOTO[person.id]) return buildPhotoFigure(side, person);
    const P = P0, ox = Math.round(1.0 * P), oy = Math.round(1.06 * P);
    const Wc = Math.round(2.0 * P), Hc = Math.round(3.5 * P);
    const S = (v) => v * P;
    const gk = !!person.gk;
    const im = PHOTO[person.id];
    // the silhouette: neck, sloping shoulders, arms by the sides down past the bar
    const body = new Path2D();
    body.moveTo(S(-0.165), S(-0.2));
    body.lineTo(S(-0.18), S(-0.07));
    body.quadraticCurveTo(S(-0.4), S(-0.035), S(-0.64), S(0.14));
    body.bezierCurveTo(S(-0.76), S(0.2), S(-0.83), S(0.34), S(-0.825), S(0.54));
    body.bezierCurveTo(S(-0.82), S(0.95), S(-0.8), S(1.45), S(-0.76), S(2.62));
    body.lineTo(S(0.76), S(2.62));
    body.bezierCurveTo(S(0.8), S(1.45), S(0.82), S(0.95), S(0.825), S(0.54));
    body.bezierCurveTo(S(0.83), S(0.34), S(0.76), S(0.2), S(0.64), S(0.14));
    body.quadraticCurveTo(S(0.4), S(-0.035), S(0.18), S(-0.07));
    body.lineTo(S(0.165), S(-0.2));
    const outline = new Path2D(); // the same edge without the line across the neck, which the face covers
    outline.moveTo(S(-0.165), S(-0.2));
    outline.lineTo(S(-0.18), S(-0.07));
    outline.quadraticCurveTo(S(-0.4), S(-0.035), S(-0.64), S(0.14));
    outline.bezierCurveTo(S(-0.76), S(0.2), S(-0.83), S(0.34), S(-0.825), S(0.54));
    outline.bezierCurveTo(S(-0.82), S(0.95), S(-0.8), S(1.45), S(-0.76), S(2.62));
    outline.moveTo(S(0.76), S(2.62));
    outline.bezierCurveTo(S(0.8), S(1.45), S(0.82), S(0.95), S(0.825), S(0.54));
    outline.bezierCurveTo(S(0.83), S(0.34), S(0.76), S(0.2), S(0.64), S(0.14));
    outline.quadraticCurveTo(S(0.4), S(-0.035), S(0.18), S(-0.07));
    outline.lineTo(S(0.165), S(-0.2));
    body.closePath();
    const lines = new Path2D(); // arms, sleeves and collar, drawn in light
    for (const sg of [-1, 1]) {
      lines.moveTo(S(-0.5 * sg), S(0.18)); lines.quadraticCurveTo(S(-0.6 * sg), S(0.33), S(-0.605 * sg), S(0.6));
      lines.quadraticCurveTo(S(-0.615 * sg), S(1.3), S(-0.62 * sg), S(2.62));
      if (!gk) { lines.moveTo(S(-0.607 * sg), S(1.02)); lines.lineTo(S(-0.818 * sg), S(0.98)); }
    }
    const collar = new Path2D();
    collar.moveTo(S(-0.195), S(-0.06)); collar.quadraticCurveTo(S(0), S(0.13), S(0.195), S(-0.06));
    const top = new Path2D(); // the lit tops of the shoulders
    for (const sg of [-1, 1]) {
      top.moveTo(S(0.19 * sg), S(-0.07)); top.quadraticCurveTo(S(0.4 * sg), S(-0.035), S(0.64 * sg), S(0.14));
      top.bezierCurveTo(S(0.76 * sg), S(0.2), S(0.83 * sg), S(0.34), S(0.825 * sg), S(0.54));
    }
    const fadeFoot = (g) => {
      g.setTransform(1, 0, 0, 1, 0, 0);
      g.globalCompositeOperation = 'destination-in';
      const ff = g.createLinearGradient(0, oy + S(1.75), 0, oy + S(2.45));
      ff.addColorStop(0, 'rgba(0,0,0,1)'); ff.addColorStop(1, 'rgba(0,0,0,0)');
      g.fillStyle = ff; g.fillRect(0, 0, Wc, Hc);
      g.globalCompositeOperation = 'source-over';
    };

    // the normal layer: a translucent blue body, scan lines, the crest, a halo round the head, then the face
    const c = mk(Wc, Hc), g = c.getContext('2d');
    g.translate(ox, oy);
    g.save(); g.clip(body);
    const bg = g.createLinearGradient(0, S(-0.36), 0, S(2.5));
    bg.addColorStop(0, 'rgba(55,115,230,0.6)'); bg.addColorStop(0.15, 'rgba(35,90,215,0.52)'); bg.addColorStop(0.45, 'rgba(18,60,175,0.34)'); bg.addColorStop(0.75, 'rgba(12,45,150,0.16)'); bg.addColorStop(1, 'rgba(8,32,120,0.05)');
    g.fillStyle = bg; g.fillRect(S(-1), S(-0.4), S(2), S(3.1));
    const eg = g.createLinearGradient(S(-0.83), 0, S(0.83), 0);
    eg.addColorStop(0, 'rgba(150,210,255,0.3)'); eg.addColorStop(0.16, 'rgba(150,210,255,0.06)'); eg.addColorStop(0.5, 'rgba(150,210,255,0)');
    eg.addColorStop(0.84, 'rgba(150,210,255,0.06)'); eg.addColorStop(1, 'rgba(150,210,255,0.3)');
    g.fillStyle = eg; g.fillRect(S(-1), S(-0.4), S(2), S(3.1));
    g.fillStyle = 'rgba(175,225,255,0.14)';
    for (let y = -0.4 * P; y < 2.7 * P; y += 4) g.fillRect(S(-1), y, S(2), 1.4);
    g.restore();
    const cim = IMG[side];
    if (cim) { const cs = S(0.22); g.globalAlpha = 0.9; g.drawImage(cim, S(0.3) - cs / 2, S(0.47) - cs / 2, cs, cs); g.globalAlpha = 1; }
    let face = null;
    if (im) {
      face = mk(P, P);
      const pg = face.getContext('2d');
      pg.imageSmoothingQuality = 'high';
      pg.drawImage(im, 0, 0, P, P);
      pg.globalCompositeOperation = 'destination-in';
      const fg = pg.createLinearGradient(0, P * 0.8, 0, P * 0.975);
      fg.addColorStop(0, 'rgba(0,0,0,1)'); fg.addColorStop(1, 'rgba(0,0,0,0)');
      pg.fillStyle = fg; pg.fillRect(0, 0, P, P);
      const halo = mk(P + 60, P + 60), hl = halo.getContext('2d');
      hl.filter = 'blur(12px)'; hl.drawImage(face, 30, 30); hl.filter = 'none';
      hl.globalCompositeOperation = 'source-in'; hl.fillStyle = 'rgb(55,110,225)'; hl.fillRect(0, 0, P + 60, P + 60);
      g.globalAlpha = 0.75; g.drawImage(halo, -P / 2 - 30, -P - 30); g.globalAlpha = 1;
      g.drawImage(face, -P / 2, -P, P, P);
    }
    fadeFoot(g);

    // the light layer (added on screen): the outline and its glow, the arm and sleeve lines, the collar, lit shoulders
    const add = mk(Wc, Hc), ag = add.getContext('2d');
    ag.translate(ox, oy);
    ag.lineJoin = 'round'; ag.lineCap = 'round';
    ag.filter = 'blur(7px)'; ag.lineWidth = S(0.05); ag.strokeStyle = 'rgba(40,95,220,0.75)'; ag.stroke(outline); ag.filter = 'none';
    ag.lineWidth = S(0.011); ag.strokeStyle = 'rgba(170,200,250,0.95)'; ag.stroke(outline);
    ag.filter = 'blur(3px)'; ag.lineWidth = S(0.03); ag.strokeStyle = 'rgba(90,170,255,0.55)'; ag.stroke(lines); ag.filter = 'none';
    ag.lineWidth = S(0.008); ag.strokeStyle = 'rgba(170,225,255,0.7)'; ag.stroke(lines);
    ag.filter = 'blur(4px)'; ag.lineWidth = S(0.045); ag.strokeStyle = 'rgba(120,195,255,0.6)'; ag.stroke(collar); ag.filter = 'none';
    ag.lineWidth = S(0.016); ag.strokeStyle = 'rgba(225,246,255,0.95)'; ag.stroke(collar);
    ag.filter = 'blur(6px)'; ag.lineWidth = S(0.06); ag.strokeStyle = 'rgba(170,220,255,0.5)'; ag.stroke(top); ag.filter = 'none';
    if (cim) { const cs = S(0.3); ag.filter = 'blur(6px)'; ag.globalAlpha = 0.5; ag.drawImage(cim, S(0.3) - cs / 2, S(0.47) - cs / 2, cs, cs); ag.globalAlpha = 1; ag.filter = 'none'; }
    if (face) { ag.setTransform(1, 0, 0, 1, 0, 0); ag.globalCompositeOperation = 'destination-out'; ag.drawImage(face, ox - P / 2, oy - P, P, P); ag.globalCompositeOperation = 'source-over'; }
    fadeFoot(ag);

    // the hologram copy for the beam-in: the whole figure, face and all, in blue light with scan lines
    const h = mk(Wc, Hc), hg = h.getContext('2d');
    hg.drawImage(c, 0, 0); hg.drawImage(add, 0, 0);
    if (face) { hg.globalAlpha = 1; hg.drawImage(face, ox - P / 2, oy - P, P, P); }
    hg.globalCompositeOperation = 'source-atop';
    const hgr = hg.createLinearGradient(0, 0, 0, Hc);
    hgr.addColorStop(0, 'rgb(140,185,250)'); hgr.addColorStop(0.4, 'rgb(55,110,225)'); hgr.addColorStop(1, 'rgb(20,55,190)');
    hg.fillStyle = hgr; hg.fillRect(0, 0, Wc, Hc);
    hg.globalCompositeOperation = 'luminosity'; hg.globalAlpha = 0.5; hg.drawImage(c, 0, 0); hg.globalAlpha = 1;
    hg.globalCompositeOperation = 'destination-out';
    hg.fillStyle = 'rgba(0,0,0,0.5)';
    for (let y = 0; y < Hc; y += 4) hg.fillRect(0, y, Wc, 1.6);
    hg.globalCompositeOperation = 'source-over';
    const gl = mk(Math.round(Wc / 2), Math.round(Hc / 2)), glg = gl.getContext('2d');
    glg.filter = 'blur(9px)'; glg.drawImage(h, 0, 0, gl.width, gl.height); glg.filter = 'none';
    glg.globalCompositeOperation = 'lighter'; glg.drawImage(gl, 0, 0);
    return (FIG[key] = { c, add, holo: h, glow: gl, ox, oy, P });
  }

  function figRect(F, s) {
    if (F.kind === 'photo') return [s.x - s.hd / 2, s.top, s.hd, s.hd];
    const k = s.P / F.P;
    return [s.x - F.ox * k, s.ny - F.oy * k, F.c.width * k, F.c.height * k];
  }
  function drawFigure(g, F, s, a, fl = 1) {
    if (a <= 0.003) return;
    const [X, Y, w, hh] = figRect(F, s);
    g.save(); g.globalAlpha = a;
    g.drawImage(F.c, X, Y, w, hh);
    if (F.add) { g.globalCompositeOperation = 'lighter'; g.globalAlpha = a * fl; g.drawImage(F.add, X, Y, w, hh); }
    g.restore();
  }
  function drawHolo(g, F, s, a, u) {
    if (a <= 0.003) return;
    const [X, Y, w, hh] = figRect(F, s);
    g.save(); g.globalAlpha = Math.min(1, a); g.globalCompositeOperation = 'lighter';
    const strips = 18, sh = F.c.height / strips, k = hh / F.c.height;
    for (let i = 0; i < strips; i++) {
      const off = Math.sin(u * 37 + i * 1.9) * 7 * (1 - clamp(u));
      g.drawImage(F.holo, 0, i * sh, F.c.width, sh, X + off, Y + i * sh * k, w, sh * k + 1);
    }
    g.restore();
  }
  function drawGlow(g, F, s, a) {
    if (a <= 0.003) return;
    const [X, Y, w, hh] = figRect(F, s);
    g.save(); g.globalAlpha = Math.min(1, a); g.globalCompositeOperation = 'lighter';
    g.drawImage(F.glow, X - 6, Y - 6, w + 12, hh + 12);
    g.restore();
  }

  // ---------------------------------------------------------------------------------------------------- light effects
  function sparkle(g, x, y, k, size, rot = 0) {
    if (k <= 0.003) return;
    g.save(); g.globalCompositeOperation = 'lighter';
    const r = size * (0.3 + 0.7 * k);
    const gr = g.createRadialGradient(x, y, 0, x, y, r);
    gr.addColorStop(0, `rgba(255,255,255,${(0.95 * k).toFixed(3)})`); gr.addColorStop(0.12, `rgba(160,195,255,${(0.85 * k).toFixed(3)})`);
    gr.addColorStop(0.4, `rgba(45,95,225,${(0.38 * k).toFixed(3)})`); gr.addColorStop(1, 'rgba(15,45,190,0)');
    g.fillStyle = gr; g.beginPath(); g.arc(x, y, r, 0, TAU); g.fill();
    for (const [ang, L, wd] of [[0, 3.0, 0.07], [PI / 2, 2.0, 0.06], [PI / 4, 1.0, 0.04], [-PI / 4, 1.0, 0.04]]) {
      const len = r * L * k, w = Math.max(1.5, r * wd);
      g.save(); g.translate(x, y); g.rotate(ang + rot);
      const lg = g.createLinearGradient(-len, 0, len, 0);
      lg.addColorStop(0, 'rgba(70,120,230,0)'); lg.addColorStop(0.5, `rgba(225,238,255,${(0.92 * k).toFixed(3)})`); lg.addColorStop(1, 'rgba(70,120,230,0)');
      g.fillStyle = lg; g.fillRect(-len, -w / 2, 2 * len, w);
      g.restore();
    }
    g.restore();
  }
  function particles(g, x, ny, P, u, seed, mode) {
    const N = 64;
    g.save(); g.globalCompositeOperation = 'lighter';
    for (let i = 0; i < N; i++) {
      const r1 = rnd(seed + i * 7.13), r2 = rnd(seed + i * 3.31 + 1), r3 = rnd(seed + i * 5.71 + 2), r4 = rnd(seed + i * 1.93 + 3);
      let tx, ty;
      if (r1 < 0.28) { tx = x + (r2 - 0.5) * 0.55 * P; ty = ny - P * (0.15 + 0.75 * r3); }
      else { tx = x + (r2 - 0.5) * 1.6 * P; ty = ny + P * (0.05 + 1.8 * r3); }
      let px, py, a;
      if (mode === 'in') {
        const cx = x, cy = ny + 0.45 * P, ang = r4 * TAU, rad = P * (0.35 + 1.0 * r2);
        const sx = cx + Math.cos(ang) * rad, sy = cy + Math.sin(ang) * rad * 0.75;
        const d0 = 0.05 + 0.4 * r3, d1 = d0 + 0.6;
        const k = (u - d0) / (d1 - d0);
        if (k <= 0 || k >= 1) continue;
        const e = eInOut3(k);
        px = lerp(sx, tx, e) + Math.sin(e * PI) * (r1 - 0.5) * P * 0.35; py = lerp(sy, ty, e);
        a = Math.sin(k * PI) * 0.95;
      } else {
        const d0 = 0.02 + 0.2 * r3, d1 = d0 + 0.5;
        const k = (u - d0) / (d1 - d0);
        if (k <= 0 || k >= 1) continue;
        const e = eOut3(k);
        px = tx + (r2 - 0.5) * P * 0.6 * e; py = ty - P * (0.3 + 0.9 * r4) * e;
        a = (1 - k) * 0.9;
      }
      const s = 1.3 + 2.8 * r2;
      g.fillStyle = `rgba(${(120 + 80 * r1) | 0},${(160 + 50 * r2) | 0},255,${a.toFixed(3)})`;
      g.beginPath(); g.arc(px, py, s, 0, TAU); g.fill();
    }
    g.restore();
  }
  // a band of light swept across the screen (beam-outs and wipes)
  function sweep(g, k, y, h, a = 1) {
    if (k <= 0 || k >= 1) return;
    const x = lerp(-420, W + 420, eInOut3(k));
    g.save(); g.globalCompositeOperation = 'lighter';
    const lg = g.createLinearGradient(x - 420, 0, x + 120, 0);
    lg.addColorStop(0, 'rgba(25,60,190,0)'); lg.addColorStop(0.7, `rgba(55,105,225,${(0.55 * a).toFixed(3)})`);
    lg.addColorStop(0.93, `rgba(215,230,255,${(0.95 * a).toFixed(3)})`); lg.addColorStop(1, 'rgba(215,230,255,0)');
    g.fillStyle = lg;
    const vg = g.createLinearGradient(0, y - h, 0, y + h);
    g.fillRect(x - 420, y - h / 2, 540, h);
    g.fillStyle = `rgba(130,170,240,${(0.35 * a).toFixed(3)})`;
    g.fillRect(x - 420, y - 2, 540, 4);
    sparkle(g, x + 90, y, 0.9 * a, h * 1.2);
    g.restore();
  }

  // ---------------------------------------------------------------------------------------------------- tabs, titles, bar
  function drawTab(g, num, name, cx, cy, h, a = 1, slide = 0, flash = 0) {
    if (a <= 0.003) return;
    const { fsN, fsT, lsT } = tabType(h);
    const nw = Math.max(h * 0.92, cmeasure(g, num, 700, fsN, 0) + h * 0.34);
    const tw = cmeasure(g, name, 600, fsT, lsT) + h * 0.52;
    const gap = h * 0.07, Wt = nw + gap + tw;
    const x0 = cx - Wt / 2 + slide, y0 = cy - h / 2;
    g.save(); g.globalAlpha *= a;
    shadowOn(g, h * 0.35, 0.5, h * 0.07);
    g.beginPath(); g.roundRect(x0, y0, nw, h, h * 0.07);
    const ng = g.createLinearGradient(0, y0, 0, y0 + h); ng.addColorStop(0, '#ffffff'); ng.addColorStop(0.55, '#e8edf3'); ng.addColorStop(1, '#b9c4d1');
    g.fillStyle = ng; g.fill();
    g.beginPath(); g.roundRect(x0 + nw + gap, y0, tw, h, h * 0.07);
    const tg = g.createLinearGradient(0, y0, 0, y0 + h); tg.addColorStop(0, '#1b3a80'); tg.addColorStop(0.46, '#0f2459'); tg.addColorStop(1, '#061234');
    g.fillStyle = tg; g.fill();
    shadowOff(g);
    g.lineWidth = Math.max(1.2, h * 0.04); g.strokeStyle = 'rgba(105,145,220,0.9)'; g.stroke();
    g.save(); g.clip();
    const gl = g.createLinearGradient(0, y0, 0, y0 + h * 0.5); gl.addColorStop(0, 'rgba(255,255,255,0.22)'); gl.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gl; g.fillRect(x0 + nw + gap, y0, tw, h * 0.5);
    g.restore();
    ctxt(g, num, x0 + nw / 2, cy + capK(700, fsN) / 2, { w: 700, s: fsN, al: 'center', c: '#0a0e18' });
    ctxt(g, name, x0 + nw + gap + h * 0.26, cy + capK(600, fsT) / 2, { w: 600, s: fsT, ls: lsT, c: '#ffffff' });
    if (flash > 0) {
      g.globalCompositeOperation = 'lighter';
      const fx = x0 + Wt * flash;
      const fg = g.createLinearGradient(fx - h * 2, 0, fx + h * 0.4, 0);
      fg.addColorStop(0, 'rgba(80,130,220,0)'); fg.addColorStop(0.85, 'rgba(190,212,250,0.62)'); fg.addColorStop(1, 'rgba(190,212,250,0)');
      g.fillStyle = fg; g.fillRect(Math.max(x0, fx - h * 2), y0, Math.min(h * 2.4, x0 + Wt - (fx - h * 2)), h);
    }
    g.restore();
  }
  const TITLES = {};
  function chromeTitle(s) {
    if (TITLES[s]) return TITLES[s];
    const fs = 91, ls = 0;
    const w = Math.ceil(cmeasure(ctx, s, 700, fs, ls) * TITLE_SX) + 60, h = 140;
    const c = mk(w, h), g = c.getContext('2d');
    const bx = 30, by = 104;
    g.translate(bx, 0); g.scale(TITLE_SX, 1); g.translate(-bx, 0);
    cfont(g, 700, fs, ls); g.textAlign = 'left'; g.textBaseline = 'alphabetic';
    g.shadowColor = 'rgba(0,0,0,0.6)'; g.shadowBlur = 10; g.shadowOffsetY = 5;
    g.lineJoin = 'round'; g.lineWidth = 8; g.strokeStyle = '#061029'; g.strokeText(s, bx, by);
    g.shadowColor = 'transparent'; g.shadowBlur = 0; g.shadowOffsetY = 0;
    const top = by - capK(700, fs);
    const gr = g.createLinearGradient(0, top, 0, by);
    gr.addColorStop(0, '#ffffff'); gr.addColorStop(0.28, '#e6ecf3'); gr.addColorStop(0.47, '#a8b4c2'); gr.addColorStop(0.5, '#56657a');
    gr.addColorStop(0.62, '#8996a8'); gr.addColorStop(0.86, '#e2e8ef'); gr.addColorStop(1, '#ffffff');
    g.fillStyle = gr; g.fillText(s, bx, by);
    g.lineWidth = 1.3; g.strokeStyle = 'rgba(255,255,255,0.6)'; g.strokeText(s, bx, by - 1);
    TITLES[s] = { c, w, h, bx, by };
    return TITLES[s];
  }
  function drawTitle(g, s, cx, base, a, glint) {
    if (a <= 0.003 || !s) return;
    const T = chromeTitle(s);
    const x = cx - (T.w - 60) / 2 - T.bx, y = base - T.by;
    g.save(); g.globalAlpha = a; g.drawImage(T.c, x, y); g.restore();
    if (glint > 0 && glint < 1) {
      const tmp = TMP_C, tg = TMP_G;
      tg.clearRect(0, 0, tmp.width, tmp.height);
      tg.globalCompositeOperation = 'source-over'; tg.drawImage(T.c, 0, 0);
      tg.globalCompositeOperation = 'source-atop';
      const gx = lerp(-120, T.w + 120, glint);
      const lg = tg.createLinearGradient(gx - 90, 0, gx + 90, 0);
      lg.addColorStop(0, 'rgba(255,255,255,0)'); lg.addColorStop(0.5, 'rgba(255,255,255,0.95)'); lg.addColorStop(1, 'rgba(255,255,255,0)');
      tg.fillStyle = lg; tg.fillRect(0, 0, T.w, T.h);
      tg.globalCompositeOperation = 'source-over';
      g.save(); g.globalAlpha = a * 0.85; g.globalCompositeOperation = 'lighter';
      g.drawImage(tmp, 0, 0, T.w, T.h, x, y, T.w, T.h);
      g.restore();
    }
  }
  const TMP_C = mk(1400, 160), TMP_G = TMP_C.getContext('2d');

  function wordmark(g, x, cy, s, a = 1) {
    g.save(); g.globalAlpha *= a;
    setFont(g, 800, s, 0); g.textAlign = 'left'; g.textBaseline = 'alphabetic';
    const base = cy + s * 0.33;
    const t1 = 'whitebrook road', w1 = g.measureText(t1).width;
    shadowOn(g, s * 0.45, 0.45, s * 0.06);
    g.fillStyle = '#ffffff'; g.fillText(t1, x, base);
    setFont(g, 800, s * 0.9, 0);
    const t2 = 'sports', w2 = g.measureText(t2).width;
    const tx = x + w1 + s * 0.3, th = s * 1.14, tw = w2 + s * 0.52;
    const rg = g.createLinearGradient(0, cy - th / 2, 0, cy + th / 2); rg.addColorStop(0, '#E10A1E'); rg.addColorStop(1, '#B0061A');
    g.beginPath(); g.roundRect(tx, cy - th / 2, tw, th, s * 0.14); g.fillStyle = rg; g.fill();
    shadowOff(g);
    g.fillStyle = '#ffffff'; g.fillText(t2, tx + s * 0.26, base - s * 0.03);
    g.restore();
    return tx + tw - x;
  }

  // crest coins: a plain dark-blue circle with the club's crest and a thin, brighter blue outline (no metal)
  const COINS = {};
  function coinFace(side, R) {
    const key = side + ':' + R;
    if (COINS[key]) return COINS[key];
    const S2 = Math.ceil(R * 2 + 14), c = mk(S2, S2), g = c.getContext('2d'), cx = S2 / 2, cy = S2 / 2;
    const dg = g.createRadialGradient(cx, cy - R * 0.3, R * 0.1, cx, cy, R);
    dg.addColorStop(0, '#142c6e'); dg.addColorStop(1, '#0a1842');
    g.beginPath(); g.arc(cx, cy, R, 0, TAU); g.fillStyle = dg; g.fill();
    g.lineWidth = Math.max(2.2, R * 0.022); g.strokeStyle = '#3f72dc'; g.stroke();
    const im = IMG[side];
    if (im) { const cs = R * 1.3; g.save(); g.shadowColor = 'rgba(0,0,0,0.45)'; g.shadowBlur = R * 0.06; g.shadowOffsetY = R * 0.02; g.drawImage(im, cx - cs / 2, cy - cs / 2, cs, cs); g.restore(); }
    return (COINS[key] = c);
  }
  function drawCoin(g, side, cx, cy, R, big, turn, a = 1) {
    // turn: the coin's angle about its upright axis (0 face on); its edge is the same dark blue
    const base = big ? 330 : 86;
    const face = coinFace(side, base);
    const k = Math.cos(turn), sc = R / base;
    const thick = R * 0.07 * Math.abs(Math.sin(turn));
    g.save(); g.globalAlpha *= a;
    if (thick > 0.5) {
      const eg = g.createLinearGradient(cx - R, 0, cx + R, 0);
      eg.addColorStop(0, '#081536'); eg.addColorStop(0.5, '#1a3478'); eg.addColorStop(1, '#081536');
      g.fillStyle = eg;
      g.beginPath(); g.ellipse(cx + Math.sign(Math.sin(turn)) * thick * 0.5, cy, Math.max(0.5, Math.abs(k) * R) + thick * 0.5, R, 0, 0, TAU); g.fill();
    }
    g.translate(cx, cy); g.scale(Math.max(0.02, Math.abs(k)), 1);
    if (k < 0) g.scale(-1, 1);
    shadowOn(g, R * 0.2, 0.5, R * 0.05);
    g.drawImage(face, -face.width / 2 * sc, -face.height / 2 * sc, face.width * sc, face.height * sc);
    g.restore();
  }

  function drawBar(g, yOff, side, title, prevTitle, tc, t, glintT) {
    if (yOff >= H - BAR_Y) return;
    g.save(); g.translate(0, yOff);
    const bg = g.createLinearGradient(0, BAR_Y, 0, H);
    bg.addColorStop(0, '#1c3d8c'); bg.addColorStop(0.05, '#112a66'); bg.addColorStop(0.42, '#0a1c4a'); bg.addColorStop(1, '#050e2c');
    g.fillStyle = bg; g.fillRect(0, BAR_Y, W, H - BAR_Y);
    // a soft gloss and a lit top edge
    const gl = g.createLinearGradient(0, BAR_Y, 0, BAR_Y + 46); gl.addColorStop(0, 'rgba(255,255,255,0.11)'); gl.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gl; g.fillRect(0, BAR_Y, W, 46);
    const el = g.createLinearGradient(0, 0, W, 0);
    el.addColorStop(0, 'rgba(80,120,200,0.3)'); el.addColorStop(0.5, 'rgba(150,185,240,0.95)'); el.addColorStop(1, 'rgba(80,120,200,0.3)');
    g.fillStyle = el; g.fillRect(0, BAR_Y - 2, W, 3);
    g.save(); g.globalCompositeOperation = 'lighter';
    const eg = g.createLinearGradient(0, BAR_Y - 26, 0, BAR_Y + 8); eg.addColorStop(0, 'rgba(40,80,190,0)'); eg.addColorStop(1, 'rgba(50,95,200,0.26)');
    g.fillStyle = eg; g.fillRect(0, BAR_Y - 26, W, 34);
    g.restore();
    // the brand on the left
    wordmark(g, 64, 980, 30);
    // the title, crossfading with a glint when it changes
    const u = t - tc;
    const aNew = prevTitle ? sstep(0.08, 0.34, u) : 1, aOld = prevTitle ? 1 - sstep(0, 0.2, u) : 0;
    if (aOld > 0) drawTitle(g, prevTitle, 960, 1004, aOld, 0);
    drawTitle(g, title, 960, 1004, aNew, prevTitle ? ramp(0.14, 0.7, u) : 0);
    // the crest coin
    drawCoin(g, side, 1592, 962, 86, false, 0, 1);
    g.restore();
  }

  // ---------------------------------------------------------------------------------------------------- the formation board
  // the pitch on the ground seen from behind the near goal: the team attacks towards you, the right-back on your left
  const BCAM = { h: 55, d: 25, th: 45 * DEG, F: 1550, cx: 960, cy: 760 };
  function bproj(X, Z) {
    const rel = Z + BCAM.d, s = Math.sin(BCAM.th), c = Math.cos(BCAM.th);
    const yc = -BCAM.h * c + rel * s, zc = BCAM.h * s + rel * c;
    return [BCAM.cx + (BCAM.F * X) / zc, BCAM.cy - (BCAM.F * yc) / zc, zc];
  }
  let BOARD = null;
  function buildBoard() {
    const c = mk(W, H), g = c.getContext('2d');
    const PW = 68, PL = 105;
    const quad = (x0, z0, x1, z1) => [bproj(x0, z0), bproj(x1, z0), bproj(x1, z1), bproj(x0, z1)];
    const poly = (pts) => { g.beginPath(); g.moveTo(pts[0][0], pts[0][1]); for (let i = 1; i < pts.length; i++) g.lineTo(pts[i][0], pts[i][1]); g.closePath(); };
    // the slab's sides and front, then its top
    const top = quad(-PW / 2 - 2.5, -3, PW / 2 + 2.5, PL + 3);
    const drop = 26;
    g.fillStyle = '#050d2b';
    for (const [a, b] of [[0, 3], [1, 2]]) {
      g.beginPath(); g.moveTo(top[a][0], top[a][1]); g.lineTo(top[b][0], top[b][1]); g.lineTo(top[b][0], top[b][1] + drop * 0.35); g.lineTo(top[a][0], top[a][1] + drop); g.closePath(); g.fill();
    }
    poly(top);
    const bg = g.createLinearGradient(0, top[2][1], 0, top[0][1]);
    bg.addColorStop(0, '#0c2257'); bg.addColorStop(0.5, '#11306f'); bg.addColorStop(1, '#163c86');
    g.fillStyle = bg; g.fill();
    // mown stripes, very faint
    for (let i = 0; i < 14; i++) {
      if (i % 2) continue;
      poly(quad(-PW / 2, (i * PL) / 14, PW / 2, ((i + 1) * PL) / 14));
      g.fillStyle = 'rgba(255,255,255,0.045)'; g.fill();
    }
    // a soft vignette and a sheen
    poly(top); g.save(); g.clip();
    const rg = g.createRadialGradient(960, 620, 120, 960, 620, 1100);
    rg.addColorStop(0, 'rgba(255,255,255,0.07)'); rg.addColorStop(1, 'rgba(0,5,30,0.35)');
    g.fillStyle = rg; g.fillRect(0, 0, W, H);
    g.restore();
    // the markings
    const line = (pts, w) => {
      g.beginPath(); pts.forEach((p, i) => { const q = bproj(p[0], p[1]); i ? g.lineTo(q[0], q[1]) : g.moveTo(q[0], q[1]); });
      const zc = bproj(pts[0][0], pts[0][1])[2];
      g.lineWidth = Math.max(1.2, (w * 260) / zc); g.strokeStyle = 'rgba(185,210,245,0.72)'; g.stroke();
    };
    const rect = (x0, z0, x1, z1) => line([[x0, z0], [x1, z0], [x1, z1], [x0, z1], [x0, z0]], 1);
    const arc = (cx, cz, r, a0, a1) => { const pts = []; for (let i = 0; i <= 48; i++) { const a = lerp(a0, a1, i / 48); pts.push([cx + Math.cos(a) * r, cz + Math.sin(a) * r]); } line(pts, 1); };
    rect(-PW / 2, 0, PW / 2, PL);
    line([[-PW / 2, PL / 2], [PW / 2, PL / 2]], 1);
    arc(0, PL / 2, 9.15, 0, TAU);
    for (const [z0, sg] of [[0, 1], [PL, -1]]) {
      rect(-20.15, z0, 20.15, z0 + sg * 16.5);
      rect(-9.16, z0, 9.16, z0 + sg * 5.5);
      const a0 = sg > 0 ? 0.65 : PI + 0.65, a1 = sg > 0 ? PI - 0.65 : TAU - 0.65;
      arc(0, z0 + sg * 11, 9.15, a0, a1);
      const ps = bproj(0, z0 + sg * 11); g.beginPath(); g.arc(ps[0], ps[1], Math.max(2, 600 / ps[2]), 0, TAU); g.fillStyle = 'rgba(185,210,245,0.72)'; g.fill();
    }
    const cs = bproj(0, PL / 2); g.beginPath(); g.arc(cs[0], cs[1], 3, 0, TAU); g.fill();
    // the slab's lit rim
    poly(top); g.lineWidth = 3; g.strokeStyle = 'rgba(100,140,215,0.9)'; g.stroke();
    g.save(); g.globalCompositeOperation = 'lighter'; g.filter = 'blur(6px)'; poly(top); g.lineWidth = 8; g.strokeStyle = 'rgba(45,85,190,0.4)'; g.stroke(); g.restore();
    BOARD = c;
  }
  function boardSpot(p) {
    const u = 1 - p.x;                 // the right-back (x near 1) on the left
    const X = (u - 0.5) * 68 * 0.86;
    const Z = 14 + (1 - p.y) * 86;     // the keeper far away, the forwards nearest
    return bproj(X, Z);
  }
  function drawBand(g, side, a) {
    if (a <= 0.003) return;
    const y0 = 984;
    g.save(); g.globalAlpha = a;
    const bg = g.createLinearGradient(0, 0, W, 0);
    bg.addColorStop(0, 'rgba(2,4,10,0.96)'); bg.addColorStop(0.42, 'rgba(5,11,32,0.95)'); bg.addColorStop(1, 'rgba(9,26,78,0.95)');
    g.fillStyle = bg; g.fillRect(0, y0, W, H - y0);
    const el = g.createLinearGradient(0, 0, W, 0); el.addColorStop(0, 'rgba(80,120,200,0.2)'); el.addColorStop(0.6, 'rgba(140,175,235,0.9)'); el.addColorStop(1, 'rgba(80,120,200,0.4)');
    g.fillStyle = el; g.fillRect(0, y0 - 2, W, 3);
    shadowOn(g, 10, 0.6, 3);
    const tname = DATA[side].name.toUpperCase(), bls = 92 * 0.693 * 0.04;
    g.save(); g.translate(118, 1058); g.transform(1, 0, -BAND_SLANT, 1, 0, 0); g.scale(BAND_SX, 1);
    ctxt(g, tname, 0, 0, { w: 700, s: 92, ls: bls });
    g.restore();
    shadowOff(g);
    const nw = cmeasure(g, tname, 700, 92, bls) * BAND_SX;
    ctxt(g, DATA[side].formation, 118 + nw + 34, 1050, { w: 600, s: 41, ls: 2, c: 'rgba(200,225,255,0.85)' });
    wordmark(g, 1430, 1032, 30);
    g.restore();
  }

  // ---------------------------------------------------------------------------------------------------- the plan
  const T_BEAM = 1.15, T_GAP = 2.05;
  const SLOT_SPACING = { 1: 0, 2: 400, 3: 360, 4: 330, 5: 330 };
  const SUB_SP = 270;
  // pictures: their top and size (the waist goes behind the bar); fallbacks: the face's size and neck
  const LAYOUT = {
    front: { top: 262, hd: 670, fbP: 290, fbNy: 562 },
    back: { top: 254, hd: 670, fbP: 284, fbNy: 550 },
    subBack: { top: 150, hd: 470, fbP: 205, fbNy: 359 },
    subFront: { top: 370, hd: 560, fbP: 245, fbNy: 619 },
  };
  function placeSlot(s, row) {
    const L = LAYOUT[row];
    if (PL_IDS.has(String(s.p.id))) { s.top = L.top; s.hd = L.hd; s.ny = L.top + 0.29 * L.hd; s.P = 0.3 * L.hd; }
    else { s.P = L.fbP; s.ny = L.fbNy; }
    return s;
  }
  function teamBlock(side, t0) {
    const B = { side, t0, groups: [], titles: [], cues: [] };
    B.titles.push({ t: t0, s: 'GOALKEEPER', first: true });
    let t = t0 + 0.55;
    const groups = groupsOf(side);
    groups.forEach((gr, gi) => {
      const n = gr.players.length, sp = SLOT_SPACING[n] || 250;
      const slots = gr.players.map((p, i) => {
        const back = n > 1 && i % 2 === 1;
        return placeSlot({ p, side, x: 960 + (i - (n - 1) / 2) * sp, back, tabY: back ? 742 : 826, start: t + i * T_GAP }, back ? 'back' : 'front');
      });
      slots.forEach((s) => (s.solid = s.start + T_BEAM));
      const last = slots[slots.length - 1].solid;
      const G = { key: gr.key, title: gr.title, slots, first: slots[0].start };
      if (gr.key !== 'att') {
        G.out = last + (gr.key === 'gk' ? 2.1 : 2.0);
        G.outMode = 'beam';
        B.titles.push({ t: G.out, s: DATA[side].name.toUpperCase() });
        B.titles.push({ t: G.out + 0.55 + T_BEAM - 0.55, s: groups[gi + 1].title });
        t = G.out + 0.1;
      } else {
        B.glint = last + 2.5;
        B.coin1 = B.glint + 0.45;
        G.out = B.coin1 + 0.5; G.outMode = 'coin';
        B.titles.push({ t: B.coin1 + 0.35, s: DATA[side].name.toUpperCase() });
      }
      B.groups.push(G);
    });
    // the substitutes: the keeper at the front left, the rest in two rows
    const subs = DATA[side].subs.slice();
    const gkSub = subs.find((p) => p.gk), others = subs.filter((p) => !p.gk);
    const backRow = others.slice(0, 4), frontRow = (gkSub ? [gkSub] : []).concat(others.slice(4, gkSub ? 8 : 9));
    B.subsT = B.coin1 + 0.62;
    B.subs = [];
    backRow.forEach((p, i) => B.subs.push(placeSlot({ p, side, x: 960 + (i - (backRow.length - 1) / 2) * SUB_SP, row: 0 }, 'subBack')));
    frontRow.forEach((p, i) => B.subs.push(placeSlot({ p, side, x: 960 + (i - (frontRow.length - 1) / 2) * SUB_SP, row: 1 }, 'subFront')));
    B.titles.push({ t: B.subsT + 1.6, s: 'SUBSTITUTES' });
    B.coin2 = B.subsT + 6.4;
    B.boardT = B.coin2 + 0.62;
    B.popT = B.boardT + 0.42;
    B.boardEnd = B.boardT + 6.6;
    B.tEnd = B.boardEnd + 0.55;
    // the board's players, far ones first
    B.board = DATA[side].xi.map((p, i) => ({ p, side, spot: boardSpot(p), pop: B.popT + i * 0.06 })).sort((a, b) => b.spot[2] - a.spot[2]);
    // cues for the sound
    const C = B.cues;
    C.push({ t: t0 + 0.0, k: 'barin' });
    for (const G of B.groups) {
      for (const s of G.slots) {
        C.push({ t: s.start, k: 'sparkle', pan: (s.x - 960) / 960 * 0.7 });
        C.push({ t: s.solid - 0.2, k: 'tab', pan: (s.x - 960) / 960 * 0.7 });
        C.push({ t: s.solid, k: 'solid', pan: (s.x - 960) / 960 * 0.7, line: ['gk', 'def', 'mid', 'att'].indexOf(G.key) });
      }
      if (G.outMode === 'beam') C.push({ t: G.out, k: 'out' });
    }
    for (const T of B.titles) if (!T.first) C.push({ t: T.t + 0.1, k: 'title' });
    C.push({ t: B.glint, k: 'glint' });
    C.push({ t: B.coin1, k: 'coin' });
    C.push({ t: B.subsT, k: 'subs' });
    C.push({ t: B.coin2, k: 'coin' });
    C.push({ t: B.boardT, k: 'board' });
    B.board.forEach((b, i) => C.push({ t: b.pop, k: 'pop', pan: (b.spot[0] - 960) / 960 * 0.6, i }));
    C.push({ t: B.boardEnd, k: 'boardout' });
    return B;
  }
  const BLOCKS = [];
  BLOCKS.push(teamBlock('home', 0.0));
  BLOCKS.push(teamBlock('away', BLOCKS[0].boardEnd + 0.3));
  const END_T = BLOCKS[1].boardEnd + 0.1;
  const DUR = END_T + 3.0;
  const SECTIONS = BLOCKS.map((B) => ({ side: B.side, t0: B.t0, bassOut: B.coin1, bassIn: B.boardT, end: B.boardEnd }));

  function titleState(B, t) {
    let cur = null, prev = null;
    for (const T of B.titles) { if (T.t <= t) { prev = cur; cur = T; } }
    return { s: cur ? cur.s : '', prev: prev ? prev.s : null, tc: cur ? cur.t : 0, first: cur && cur.first };
  }

  // ---------------------------------------------------------------------------------------------------- one frame
  let BGC = null, CHC = null;
  function drawSlot(g, s, t, outT, outMode, pass) {
    const F = buildFigure(s.side, s.p);
    const u = t - s.start;
    if (u < 0) return;
    const seed = (s.p.id % 9973) * 1.37;
    let k = 1, holo = 0, glow = 0;
    // beaming in
    const solidK = eOut3(ramp(0.8, 1.15, u));
    holo = sstep(0.3, 0.72, u) * (1 - sstep(0.88, 1.2, u));
    glow = Math.max(0, 1 - Math.abs(u - 1.06) / 0.26) * 0.5;
    // leaving
    let ou = -1;
    if (outT !== undefined && t >= outT) {
      ou = t - outT;
      k = 1 - sstep(0.08, outMode === 'coin' ? 0.3 : 0.42, ou);
      holo = Math.max(holo, Math.sin(clamp(ou / 0.45) * PI) * 0.75);
      if (ou > 0.6) return;
    }
    if (pass === 'body') {
      drawHolo(g, F, s, holo * (ou >= 0 ? 1 : 0.95), u);
      drawFigure(g, F, s, solidK * k, 0.88 + 0.12 * Math.sin(t * 7.3 + s.x * 0.01));
      drawGlow(g, F, s, glow * k + (ou >= 0 ? Math.sin(clamp(ou / 0.4) * PI) * 0.5 : 0));
    } else if (pass === 'fx') {
      const sk = u < 0.62 ? Math.sin(clamp(u / 0.62) * PI) : 0;
      sparkle(g, s.x, s.ny + 0.42 * s.P, sk, 150, u * 0.6);
      if (u < 1.3) particles(g, s.x, s.ny, s.P, u, seed, 'in');
      if (ou >= 0) particles(g, s.x, s.ny, s.P, ou, seed + 11, 'out');
      // a beam of light at the foot while the figure forms
      const bk = Math.sin(clamp((u - 0.15) / 1.0) * PI) * (u < 1.15 ? 1 : 0);
      if (bk > 0) {
        g.save(); g.globalCompositeOperation = 'lighter';
        const bw = s.P * 1.1, by = BAR_Y - 6;
        const lg = g.createRadialGradient(s.x, by, 4, s.x, by, bw);
        lg.addColorStop(0, `rgba(185,210,255,${(0.75 * bk).toFixed(3)})`); lg.addColorStop(0.3, `rgba(50,100,215,${(0.4 * bk).toFixed(3)})`); lg.addColorStop(1, 'rgba(25,60,200,0)');
        g.fillStyle = lg; g.fillRect(s.x - bw, by - bw * 0.5, bw * 2, bw * 0.7);
        g.restore();
      }
    } else if (pass === 'tab') {
      const tk = eOut3(ramp(0.82, 1.12, u)) * k;
      if (tk > 0) drawTab(g, String(s.p.n), NAMES[s.side][s.p.id], s.x, s.tabY, 58, tk, (1 - eOut3(ramp(0.82, 1.12, u))) * -70, ramp(0.95, 1.35, u));
    }
  }
  function drawGroups(g, B, t) {
    for (const G of B.groups) {
      if (t < G.first - 0.01 || t > G.out + 0.65) continue;
      const order = G.slots.slice().sort((a, b) => (b.back ? 1 : 0) - (a.back ? 1 : 0));
      for (const s of order) drawSlot(g, s, t, G.out, G.outMode, 'body');
      for (const s of G.slots) drawSlot(g, s, t, G.out, G.outMode, 'fx');
      for (const s of G.slots) drawSlot(g, s, t, G.out, G.outMode, 'tab');
      if (G.outMode === 'beam' && t >= G.out) {
        sweep(g, (t - G.out) / 0.42, 784, 120, 1);
      }
    }
  }
  function drawSubs(g, B, t) {
    if (t < B.subsT - 0.2 || t > B.coin2 + 0.65) return;
    const u = t - B.subsT, ou = t - (B.coin2 + 0.5);
    const kIn = eOut3(ramp(0, 0.35, u)), kOut = ou > 0 ? 1 - sstep(0, 0.3, ou) : 1;
    const a = kIn * kOut;
    if (a <= 0) return;
    for (const row of [0, 1]) for (const s of B.subs) if (s.row === row) {
      const F = buildFigure(s.side, s.p);
      drawHolo(g, F, s, Math.max(0, 1 - u / 0.4) * 0.9, u + 0.6);
      drawFigure(g, F, s, a);
    }
    // a dark rise from the bar behind the names
    g.save(); g.globalAlpha = a;
    const dg = g.createLinearGradient(0, 560, 0, BAR_Y);
    dg.addColorStop(0, 'rgba(4,10,30,0)'); dg.addColorStop(0.55, 'rgba(4,10,30,0.55)'); dg.addColorStop(1, 'rgba(4,10,30,0.88)');
    g.fillStyle = dg; g.fillRect(0, 560, W, BAR_Y - 560);
    g.restore();
    // names in two staggered rows, fitted to the room
    for (const row of [0, 1]) {
      const rs = B.subs.filter((s) => s.row === row);
      let h = 44;
      const fits = (hh) => rs.every((s) => {
        const w = tabWidth(g, String(s.p.n), NAMES[s.side][s.p.id], hh);
        return w <= SUB_SP - 12;
      });
      while (h > 30 && !fits(h)) h -= 2;
      rs.forEach((s, i) => {
        const tk = eOut3(ramp(0.15 + i * 0.05, 0.45 + i * 0.05, u)) * kOut;
        drawTab(g, String(s.p.n), NAMES[s.side][s.p.id], s.x, row === 0 ? 782 : 852, h, tk, 0, ramp(0.3 + i * 0.05, 0.8 + i * 0.05, u));
      });
    }
    if (u < 0.6) sparkle(g, 960, 600, Math.sin(clamp(u / 0.6) * PI) * 0.5, 420, 0);
  }
  function drawBoard(g, B, t) {
    if (t < B.boardT - 0.01 || t > B.boardEnd + 0.6) return;
    const u = t - B.boardT, ou = t - B.boardEnd;
    const kIn = eOut3(ramp(0, 0.4, u)), kOut = ou > 0 ? 1 - sstep(0.05, 0.45, ou) : 1;
    const a = kIn * kOut;
    // dim the ground a little under the board
    g.save(); g.globalAlpha = 0.32 * a; g.fillStyle = '#030714'; g.fillRect(0, 0, W, H); g.restore();
    g.save(); g.globalAlpha = a;
    const lift = (1 - kIn) * 60;
    g.drawImage(BOARD, 0, lift);
    g.restore();
    for (const b of B.board) {
      const pu = t - b.pop;
      if (pu < 0) continue;
      const F = buildFigure(b.side, b.p);
      const [sx, sy, zc] = b.spot;
      const bh = 128 * Math.pow(76 / zc, 0.62);           // bust height on screen
      const Pb = bh / 1.66;                                 // bust shows head (P) and 0.66 P of chest
      const pk = eOutBack(clamp(pu / 0.3), 1.4) * kOut;
      if (pk <= 0) continue;
      const ny = sy - 0.62 * Pb + lift * 0;
      g.save(); g.globalAlpha = Math.min(1, pk) * a;
      const cx = sx, scale = 0.6 + 0.4 * Math.min(1, pk);
      g.translate(cx, sy); g.scale(scale, scale); g.translate(-cx, -sy);
      shadowOn(g, 16, 0.45, 5);
      if (F.kind === 'photo') {
        // head, shoulders and the top of the chest, its soft foot under the name
        const hb = bh * 1.12, wb = hb * (F.bust.width / F.bust.height);
        g.drawImage(F.bust, cx - wb / 2, sy + bh * 0.12 - hb, wb, hb);
      } else {
        // draw the top of the figure only: head to chest
        const s = Pb / F.P, srcH = (F.oy + 0.66 * F.P);
        g.drawImage(F.c, 0, 0, F.c.width, srcH, cx - F.ox * s, ny - F.oy * s, F.c.width * s, srcH * s);
        g.globalCompositeOperation = 'lighter';
        g.drawImage(F.add, 0, 0, F.c.width, srcH, cx - F.ox * s, ny - F.oy * s, F.c.width * s, srcH * s);
        g.globalCompositeOperation = 'source-over';
      }
      shadowOff(g);
      g.restore();
      if (pu < 0.4) sparkle(g, sx, sy - bh * 0.4, Math.sin(clamp(pu / 0.4) * PI) * 0.7 * a, 60, 0);
      const th = Math.max(22, 34 * Math.pow(76 / zc, 0.5));
      drawTab(g, String(b.p.n), NAMES[b.side][b.p.id], sx, sy + th * 0.42, th, eOut3(clamp((pu - 0.08) / 0.25)) * a, 0, 0);
    }
    drawBand(g, B.side, a);
    if (ou > 0 && ou < 0.6) sweep(g, ou / 0.5, 560, 260, 1);
  }
  function drawBigCoin(g, B, t, t0) {
    const u = t - t0;
    if (u < 0 || u > 1.5) return;
    let x, turn, R, a = 1;
    if (u < 0.55) { const k = eOut3(u / 0.55); x = lerp(2250, 1060, k); turn = lerp(1.35, 0, k); R = lerp(250, 330, k); }
    else if (u < 0.85) { x = 1060 - (u - 0.55) * 60; turn = Math.sin((u - 0.55) * 6) * 0.06; R = 330; }
    else { const k = eInOut3((u - 0.85) / 0.65); x = lerp(1042, -380, k); turn = lerp(0, -1.4, k); R = lerp(330, 380, k); a = 1 - sstep(0.75, 1, k); }
    drawCoin(g, B.side, x, 520, R, true, turn, a);
    // a flash under the coin as the scene changes
    const fk = Math.max(0, 1 - Math.abs(u - 0.62) / 0.22);
    if (fk > 0) { g.save(); g.globalCompositeOperation = 'lighter'; g.fillStyle = `rgba(40,85,200,${(0.28 * fk).toFixed(3)})`; g.fillRect(0, 0, W, H); g.restore(); }
  }
  function drawHint(g, t) {
    const a = sstep(0.4, 0.9, t) * (1 - sstep(6.5, 7.2, t));
    if (a <= 0) return;
    g.save(); g.globalAlpha = a * 0.92;
    shadowOn(g, 12, 0.5, 2);
    g.beginPath(); g.roundRect(56, 46, 96, 40, 9); g.fillStyle = 'rgba(8,14,34,0.72)'; g.fill();
    shadowOff(g);
    g.lineWidth = 1.5; g.strokeStyle = 'rgba(255,255,255,0.55)'; g.stroke();
    ctxt(g, 'BACK', 104, 74, { w: 700, s: 25, ls: 1.5, al: 'center', c: '#fff' });
    shadowOn(g, 8, 0.7, 2);
    ctxt(g, 'Close line-ups', 166, 75, { w: 600, s: 28, ls: 0.6, c: '#fff' });
    shadowOff(g);
    g.restore();
  }

  function drawFrame(t) {
    const g = ctx;
    g.setTransform(1, 0, 0, 1, 0, 0);
    g.globalAlpha = 1; g.globalCompositeOperation = 'source-over'; g.filter = 'none';
    // the ground: one still, slowly drifting
    const kk = t / DUR, z = 1.03 + 0.04 * kk, dx = lerp(-16, 16, kk);
    g.drawImage(BGC, (W - W * z) / 2 + dx, (H - H * z) / 2 + 8, W * z, H * z);
    for (const B of BLOCKS) {
      if (t < B.t0 - 0.01 || t > B.tEnd + 0.05) continue;
      drawSubs(g, B, t);
      drawGroups(g, B, t);
      drawBoard(g, B, t);
      // the bar: up at the start, down for the board
      const inK = eOut3(ramp(B.t0, B.t0 + 0.45, t)), outK = sstep(B.coin2 + 0.45, B.coin2 + 0.8, t);
      const yOff = (1 - inK) * (H - BAR_Y + 10) + outK * (H - BAR_Y + 10);
      const ts = titleState(B, t);
      drawBar(g, yOff, B.side, ts.s, ts.first ? null : ts.prev, ts.tc, t, B.glint);
      drawBigCoin(g, B, t, B.coin1);
      drawBigCoin(g, B, t, B.coin2);
    }
    drawHint(g, t);
    // back to the channel
    if (t >= END_T) {
      const u = t - END_T, k = eInOut3(clamp(u / 0.7));
      const edge = lerp(-260, W + 260, k);
      const z2 = 1.0 + 0.02 * clamp(u / 3);
      g.save();
      g.beginPath(); g.moveTo(-300, 0); g.lineTo(edge + 140, 0); g.lineTo(edge - 140, H); g.lineTo(-300, H); g.closePath(); g.clip();
      if (window.OVERLAY) { g.clearRect(0, 0, W, H); }   // live: the channel itself shows through
      else g.drawImage(CHC, (W - W * z2) / 2, (H - H * z2) / 2, W * z2, H * z2);
      g.restore();
      if (k < 1) {
        g.save(); g.globalCompositeOperation = 'lighter';
        const gr = g.createLinearGradient(edge - 200, 0, edge + 200, 0);
        gr.addColorStop(0, 'rgba(25,70,200,0)'); gr.addColorStop(0.5, 'rgba(110,155,235,0.85)'); gr.addColorStop(1, 'rgba(25,70,200,0)');
        g.fillStyle = gr; g.beginPath(); g.moveTo(edge + 40, 0); g.lineTo(edge + 220, 0); g.lineTo(edge - 40, H); g.lineTo(edge - 220, H); g.closePath(); g.fill();
        g.restore();
      }
      const ca = window.OVERLAY ? 0 : sstep(0.9, 1.4, u);
      if (ca > 0) {
        g.save(); g.globalAlpha = ca * 0.75; shadowOn(g, 8, 0.7, 2);
        txt(g, "Back to the channel's own picture (stand-in)", 96, 1030, { w: 500, s: 22, ls: 0.3, c: '#fff' });
        shadowOff(g); g.restore();
      }
    }
    // the first instant comes up from the channel's picture
    if (t < 0.35) {
      g.save(); g.globalAlpha = 1 - t / 0.35;
      if (window.OVERLAY) { g.globalCompositeOperation = 'destination-out'; g.fillRect(0, 0, W, H); }   // live: up from the channel
      else g.drawImage(CHC, 0, 0);
      g.restore();
    }
    // live: Back fades it all away to the channel at once
    if (window.OVERLAY && window.FADE_FROM != null) {
      const f = clamp((t - window.FADE_FROM) / 0.5);
      if (f > 0) { g.save(); g.globalAlpha = f; g.globalCompositeOperation = 'destination-out'; g.fillRect(0, 0, W, H); g.restore(); }
    }
  }

  function buildCues() {
    const cues = [];
    for (const B of BLOCKS) cues.push(...B.cues);
    cues.push({ t: END_T, k: 'wipe' });
    cues.push({ t: END_T + 0.5, k: 'end' });
    cues.sort((a, b) => a.t - b.t);
    return cues;
  }

  async function init() {
    const t0 = performance.now();
    for (const w of [500, 600, 700, 800]) await document.fonts.load(`${w} 40px Outfit`);
    for (const f of ['600 40px KhandM', '700 40px KhandM']) await document.fonts.load(f, 'AZaz09&ĆćØøÖöÃãÉé');
    await document.fonts.ready;
    for (const side of ['home', 'away']) {
      // a crest that will not come leaves a plain coin rather than stopping the line-ups
      const url = window.ASSET_URLS[side];
      if (url) { try { const im = new Image(); im.src = url; await im.decode(); IMG[side] = im; } catch (e) { /* plain coin */ } }
    }
    const pl = window.ASSET_URLS.plphotos || {};
    for (const id of Object.keys(pl)) {
      const im = new Image(); im.src = pl[id];
      try { await im.decode(); if (im.width > 0) PLPHOTO[id] = im; } catch (e) { /* face and hologram instead */ }
    }
    window.PL_COUNT = Object.keys(PLPHOTO).length;
    const photos = window.ASSET_URLS.photos || {};
    for (const id of Object.keys(photos)) {
      const im = new Image(); im.src = photos[id];
      try { await im.decode(); if (im.width > 0) PHOTO[id] = im; } catch (e) { /* drawn without */ }
    }
    window.PHOTO_COUNT = Object.keys(PHOTO).length;
    GL = groundLight();
    buildCrowd();
    buildSky();
    // the ground, once: the wide shot behind the players, softened a touch; and the channel's stand-in picture
    const sc = mk(W, H), sg = sc.getContext('2d');
    renderScene(sg, beautyCam(12), 12);
    grade(sg); bloomOn(sg, sc);
    BGC = mk(W, H);
    const bgc = BGC.getContext('2d');
    bgc.filter = 'blur(1.6px) saturate(1.05)'; bgc.drawImage(sc, 0, 0); bgc.filter = 'none';
    if (!window.OVERLAY) {   // the mock-up's stand-in for the channel; live, the channel itself
      const cc = mk(W, H), cg = cc.getContext('2d');
      renderScene(cg, channelCam(0.6), 40);
      grade(cg); bloomOn(cg, cc);
      CHC = cc;
    }
    buildBoard();
    for (const side of ['home', 'away']) for (const p of DATA[side].xi.concat(DATA[side].subs)) buildFigure(side, p);
    for (const B of BLOCKS) for (const T of B.titles) chromeTitle(T.s);
    window.CUES = buildCues();
    window.SECTIONS = SECTIONS;
    window.DUR = DUR;
    window.SOL = SOL;
    window.BLOCKS_INFO = BLOCKS.map((B) => ({ side: B.side, t0: B.t0, groups: B.groups.map((G) => ({ key: G.key, first: G.first, out: G.out, solids: G.slots.map((s) => +s.solid.toFixed(2)) })), glint: B.glint, coin1: B.coin1, subs: B.subsT, coin2: B.coin2, board: B.boardT, boardEnd: B.boardEnd }));
    window.INIT_MS = performance.now() - t0;
    window.drawFrame = drawFrame;
    window.READY = true;
    if (window.onBroadcastReady) window.onBroadcastReady();
  }
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
  init().catch((e) => { window.INIT_ERR = String(e && e.stack || e); if (window.onBroadcastFailed) window.onBroadcastFailed(window.INIT_ERR); });
})();
