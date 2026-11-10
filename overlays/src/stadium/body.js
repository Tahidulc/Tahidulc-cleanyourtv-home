
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

  function drawBoard(g, side, s, u, kin, kout) {
    const team = DATA[side], K = TEAMS[side], T = xiTimes(s);
    const ka = eOut3(ramp(T.boardIn, T.boardLand, u));
    const B = { cx: boardCx(side) + kout * (side === 'away' ? 60 : -60), cy: 492 + (1 - ka) * 36, F: 1800, D: 220, a: lerp(80, 38, ka) * DEG };
    const al = clamp(ka * 1.6) * (1 - kout);
    if (al <= 0) return;
    g.save(); g.globalAlpha = al;
    const corner = (u2, v2) => boardProj(B, u2, v2);
    const c00 = corner(-36.5, -55), c10 = corner(36.5, -55), c11 = corner(36.5, 55), c01 = corner(-36.5, 55);
    shadowOn(g, 50, 0.45, 18);
    polyPath(g, [c00, c10, c11, c01]);
    g.fillStyle = 'rgba(6,9,18,0.62)'; g.fill();
    shadowOff(g);
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
    const flash = Math.max(0, 1 - Math.abs(u - T.boardLand) / 0.35);
    g.strokeStyle = `rgba(255,255,255,${0.3 + 0.4 * flash})`; g.lineWidth = 2; g.lineJoin = 'round';
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
    const top = corner(0, 55);
    g.restore();

    const placed = team.xi.map((p, i) => {
      const [pu, pv] = formUV(p);
      const q = boardProj(B, pu, pv);
      return { p, i, x: q[0], y: q[1], q: q[2], tp: T.pop0 + i * T.popGap };
    });
    placed.sort((a, b) => a.y - b.y);
    for (const P of placed) {
      const kp = ramp(P.tp, P.tp + 0.42, u);
      if (kp <= 0) continue;
      const sc = eOutBack(kp, 2.0);
      const R0 = PHOTO_R * P.q, r = R0 * sc;
      g.save(); g.globalAlpha = al * clamp(kp * 4);
      const kr = ramp(P.tp, P.tp + 0.7, u);
      if (kr < 1) { g.beginPath(); g.arc(P.x, P.y, R0 * (1 + 0.8 * eOut3(kr)), 0, TAU); g.strokeStyle = `rgba(255,255,255,${0.5 * (1 - kr)})`; g.lineWidth = 2; g.stroke(); }
      const hasPic = headshot(g, side, P.p, P.x, P.y, r, { px: Math.round(2 * R0), rim: 3 * P.q * sc, glow: 8 * P.q });
      if (hasPic && r >= 4) {
        const bx = P.x + r * 0.74, by = P.y + r * 0.74, br = 12 * P.q * sc;
        g.beginPath(); g.arc(bx, by, br, 0, TAU); g.fillStyle = P.p.gk ? NIGHT : K.c1; g.fill();
        g.lineWidth = 1.6; g.strokeStyle = 'rgba(255,255,255,0.92)'; g.stroke();
        txt(g, P.p.n, bx, by + 0.5, { w: 800, s: Math.round(13 * P.q * sc), al: 'center', bl: 'middle', c: P.p.gk ? '#fff' : K.ink });
      }
      if (P.p.cap && r >= 4) {
        const bx = P.x + r * 0.72, by = P.y - r * 0.72;
        g.beginPath(); g.arc(bx, by, 10.5 * P.q * sc, 0, TAU); g.fillStyle = LILAC; g.fill(); g.lineWidth = 1.5; g.strokeStyle = NIGHT; g.stroke();
        txt(g, 'C', bx, by + 0.5, { w: 800, s: Math.round(13 * P.q * sc), al: 'center', bl: 'middle', c: NIGHT });
      }
      const kn = eOut3(ramp(P.tp + 0.08, P.tp + 0.45, u));
      g.globalAlpha = al * kn;
      shadowOn(g, 8, 0.85, 2);
      const LB = labelLayout(side)[P.i];
      txt(g, P.p.last, P.x, P.y + R0 + 23 * P.q + LB.dy + (1 - kn) * 8, { w: 700, s: LB.size, al: 'center' });
      shadowOff(g);
      g.restore();
    }
    if (team.formation) {
      g.save(); g.globalAlpha = al;
      shadowOn(g, 10, 0.6, 2);
      txt(g, 'FORMATION', top[0], top[1] - 52, { w: 700, s: 14, ls: 5, al: 'center', c: 'rgba(255,255,255,0.7)' });
      txt(g, team.formation, top[0], top[1] - 16, { w: 800, s: 34, ls: 2, al: 'center' });
      shadowOff(g);
      g.restore();
    }
  }

  function drawPanel(g, side, s, u, kin, kout) {
    const team = DATA[side], K = TEAMS[side], T = xiTimes(s);
    const mir = side === 'away';
    const x = mir ? 96 : 1040, y = 160, w = 784, h = 728;
    const dx = (1 - kin) * (mir ? -110 : 110) + kout * (mir ? -70 : 70);
    const al = clamp(kin * 1.3) * (1 - kout);
    if (al <= 0) return;
    g.save(); g.globalAlpha = al; g.translate(dx, 0);
    glass(g, x, y, w, h, 24);
    g.save(); g.beginPath(); g.roundRect(x, y, w, h, 24); g.clip();
    const bg = g.createLinearGradient(x, 0, x + w, 0); bg.addColorStop(0, K.c1); bg.addColorStop(0.6, K.c2); bg.addColorStop(1, rgba(hex(K.c2), 0));
    g.fillStyle = bg; g.fillRect(x, y, w, 7);
    const hg = g.createLinearGradient(0, y, 0, y + 150); hg.addColorStop(0, rgba(hex(K.c1), 0.22)); hg.addColorStop(1, rgba(hex(K.c1), 0));
    g.fillStyle = hg; g.fillRect(x, y, w, 150);
    g.restore();
    const kc = eOutBack(ramp(0.15, 0.75, u), 1.6);
    g.save(); g.translate(x + 82, y + 76); g.scale(kc, kc); crest(g, side, 0, 0, 100); g.restore();
    // the manager on the right; the club's name sized to the room left of him
    let mgrW = 0;
    if (team.coach) {
      const cw = Math.max(measure(g, team.coach, 600, 25, 0), measure(g, 'MANAGER', 700, 13, 4));
      mgrW = 62 + 18 + cw;
      headshot(g, side, { id: team.coachId, name: team.coach }, x + w - 30 - 31, y + 76, 31, { initials: true, glow: 8 });
      txt(g, 'MANAGER', x + w - 30 - 62 - 18, y + 63, { w: 700, s: 13, ls: 4, al: 'right', c: 'rgba(255,255,255,0.6)' });
      txt(g, team.coach, x + w - 30 - 62 - 18, y + 94, { w: 600, s: 25, al: 'right' });
    }
    const nameUp = team.name.toUpperCase();
    const nz = fitSize(nameUp, 800, 54, 1, w - 150 - 30 - (mgrW ? mgrW + 28 : 0), 30);
    txt(g, nameUp, x + 150, y + 84, { w: 800, s: nz, ls: 1 });
    txt(g, 'STARTING XI', x + 152, y + 116, { w: 700, s: 17, ls: 5, c: LILAC });
    g.fillStyle = 'rgba(255,255,255,0.12)'; g.fillRect(x + 28, y + 146, w - 56, 1.5);
    const y0 = y + 158, rh = 46.5;
    team.xi.forEach((p, i) => {
      const tp = T.pop0 + i * T.popGap;
      const kr = eOut3(ramp(tp - 0.04, tp + 0.32, u));
      if (kr <= 0) return;
      const yy = y0 + i * rh;
      g.save(); g.globalAlpha = al * kr; g.translate((1 - kr) * (mir ? -26 : 26), 0);
      const fl = Math.max(0, 1 - (u - tp) / 0.7);
      if (fl > 0) { const fg = g.createLinearGradient(x, 0, x + w, 0); fg.addColorStop(0, rgba(hex(K.c1), 0.42 * fl)); fg.addColorStop(1, rgba(hex(K.c1), 0)); g.fillStyle = fg; g.fillRect(x + 14, yy + 3, w - 28, rh - 6); }
      g.beginPath(); g.roundRect(x + 30, yy + 8, 48, rh - 16, 8);
      if (p.gk) { g.fillStyle = 'rgba(7,9,15,0.85)'; g.fill(); g.lineWidth = 2; g.strokeStyle = K.c1; g.stroke(); }
      else { g.fillStyle = K.c1; g.fill(); }
      txt(g, p.n, x + 54, yy + rh / 2 + 1, { w: 800, s: 21, al: 'center', bl: 'middle', c: p.gk ? '#fff' : K.ink });
      headshot(g, side, p, x + 105, yy + rh / 2, 19.5, { initials: true, rim: 1.5, glow: 4, shadow: 6 });
      const last = p.last || p.name;
      let first = p.name && p.name.endsWith(last) ? p.name.slice(0, p.name.length - last.length).trim() : '';
      if (p.name === last) first = '';
      // the room before the position tag: the first names go first if the line is too long
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
      g.restore();
    });
    g.restore();
  }

  function drawSubs(g, side, s, u, kout) {
    const team = DATA[side], K = TEAMS[side], T = xiTimes(s);
    if (!team.subs.length) return;
    const ks = eOutExpo(ramp(T.subs, T.subs + 0.7, u));
    if (ks <= 0) return;
    const x = 96, y = 918 + (1 - ks) * 40 + kout * 30, w = 1728, h = 62;
    const al = clamp(ks * 1.3) * (1 - kout);
    g.save(); g.globalAlpha = al;
    glass(g, x, y, w, h, 18);
    const lw = measure(g, 'SUBSTITUTES', 800, 15, 4);
    g.beginPath(); g.roundRect(x + 12, y + 12, lw + 34, h - 24, 10); g.fillStyle = K.c1; g.fill();
    txt(g, 'SUBSTITUTES', x + 12 + (lw + 34) / 2, y + h / 2 + 1, { w: 800, s: 15, ls: 4, al: 'center', bl: 'middle', c: K.ink });
    let sz = 23;
    const avail = w - (lw + 34) - 60;
    const PH = 32, PG = 9, GAP = 20;
    const widthAt = (z) => team.subs.reduce((acc, sb) => acc + PH + PG + measure(g, sb.n, 800, z) + 7 + measure(g, sb.last, 500, z) + GAP, 0) - GAP;
    while (widthAt(sz) > avail && sz > 13) sz -= 1;
    const gap = (avail - widthAt(sz)) / Math.max(1, team.subs.length - 1) + GAP;
    let ex = x + 12 + lw + 34 + 22;
    team.subs.forEach((sb, i) => {
      const ke = eOut3(ramp(T.subs + 0.15 + i * 0.05, T.subs + 0.5 + i * 0.05, u));
      g.save(); g.globalAlpha = al * ke;
      headshot(g, side, { id: sb.id, n: sb.n, name: sb.last }, ex + PH / 2, y + h / 2, PH / 2, { initials: true, rim: 1.5, glow: 4, shadow: 5 });
      const w1 = txt(g, sb.n, ex + PH + PG, y + h / 2 + sz * 0.36, { w: 800, s: sz, c: side === 'away' ? K.c1 : '#FF5A5F' });
      const w2 = txt(g, sb.last, ex + PH + PG + w1 + 7, y + h / 2 + sz * 0.36, { w: 500, s: sz, c: 'rgba(255,255,255,0.92)' });
      g.restore();
      ex += PH + PG + w1 + 7 + w2 + gap;
    });
    g.restore();
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

  function pageXI(g, s, u, side) {
    const T = xiTimes(s);
    const kin = eOutExpo(ramp(0, 0.85, u));
    const kout = eIn3(ramp(T.out0, T.out1, u));
    drawBoard(g, side, s, u, kin, kout);
    drawPanel(g, side, s, u, kin, kout);
    drawSubs(g, side, s, u, kout);
    drawBackHint(g, clamp(ramp(0.6, 1.2, u)) * (1 - kout));
  }

  function pageIdent(g, s, u) {
    const D = s.d;
    const kout = eIn3(ramp(D - 0.7, D, u));
    const a = 1 - kout;
    if (a <= 0) return;
    const cx = 960, cy = 488;
    const ts = 158;
    const w1 = measure(g, 'WHITEBROOK ROAD', 800, 84, 3), w2 = measure(g, 'SPORTS', 300, 34, 22);
    const tb = Math.max(w1, w2);
    const total = ts + 38 + tb;
    const x0 = cx - total / 2;
    const sc0 = 1 - 0.05 * kout;
    g.save();
    g.translate(cx, cy - kout * 18); g.scale(sc0, sc0); g.translate(-cx, -cy);
    const kg = sstep(0.1, 1.2, u) * a;
    const rg = g.createRadialGradient(cx, cy, 0, cx, cy, 760);
    rg.addColorStop(0, `rgba(109,40,242,${0.28 * kg})`); rg.addColorStop(0.5, `rgba(255,107,107,${0.08 * kg})`); rg.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = rg; g.fillRect(0, 0, W, H);
    const kt = ramp(0.12, 0.85, u);
    if (kt > 0) {
      const sc = 0.55 + 0.45 * eOutBack(kt, 2.0);
      g.save(); g.globalAlpha = a * clamp(kt * 2.5);
      g.translate(x0 + ts / 2, cy); g.rotate((1 - eOut3(kt)) * -12 * DEG); g.scale(sc, sc);
      tile(g, -ts / 2, -ts / 2, ts, { shadow: true, shine: ramp(0.8, 1.5, u) });
      g.restore();
    }
    const kw = eOut3(ramp(0.45, 1.3, u));
    if (kw > 0) {
      const tx = x0 + ts + 38;
      g.save(); g.globalAlpha = a;
      g.beginPath(); g.rect(tx - 12, cy - 130, (tb + 60) * kw, 260); g.clip();
      shadowOn(g, 24, 0.45, 4);
      txt(g, 'WHITEBROOK ROAD', tx - (1 - kw) * 34, cy + 4, { w: 800, s: 84, ls: 3 });
      txt(g, 'SPORTS', tx - (1 - kw) * 34 + 4, cy + 58, { w: 300, s: 34, ls: 22, c: LILAC });
      shadowOff(g);
      g.restore();
    }
    const kr = eInOut3(ramp(0.9, 1.6, u));
    if (kr > 0) {
      const rw = total * kr;
      const gr = g.createLinearGradient(cx - rw / 2, 0, cx + rw / 2, 0);
      gr.addColorStop(0, 'rgba(109,40,242,0)'); gr.addColorStop(0.25, VIOLET); gr.addColorStop(0.75, CORAL); gr.addColorStop(1, 'rgba(255,107,107,0)');
      g.globalAlpha = a; g.fillStyle = gr; g.fillRect(cx - rw / 2, cy + 128, rw, 3);
    }
    const ks = eOut3(ramp(1.1, 1.8, u));
    if (ks > 0) {
      g.globalAlpha = a * ks;
      shadowOn(g, 14, 0.5, 2);
      txt(g, 'TEAM LINE-UPS', cx, cy + 186, { w: 600, s: 24, ls: lerp(18, 10, ks), al: 'center', c: 'rgba(255,255,255,0.9)' });
      shadowOff(g);
    }
    const kf = ramp(0.55, 1.5, u);
    if (kf > 0 && kf < 1) {
      g.globalCompositeOperation = 'lighter';
      const fx = lerp(-200, W + 200, eInOut3(kf));
      const fg = g.createRadialGradient(fx, cy, 0, fx, cy, 520);
      fg.addColorStop(0, `rgba(255,220,235,${0.28 * a})`); fg.addColorStop(1, 'rgba(255,220,235,0)');
      g.save(); g.translate(fx, cy); g.scale(1, 0.035); g.translate(-fx, -cy);
      g.fillStyle = fg; g.fillRect(fx - 520, cy - 520, 1040, 1040);
      g.restore();
      g.globalCompositeOperation = 'source-over';
    }
    g.restore();
  }

  function drawBug(g, t) {
    const ti = seg('title'), ou = seg('outro');
    if (!ti) return;
    const kin = eOut3(ramp(ti.a + 0.25, ti.a + 0.85, t));
    const kout = ou ? ramp(ou.a + 0.1, ou.a + 0.5, t) : 0;
    const a = kin * (1 - kout);
    if (a <= 0) return;
    const s = 50, x1 = 1824, y = 54;
    const w1 = measure(g, 'WHITEBROOK ROAD', 800, 21, 1.5), w2 = measure(g, 'SPORTS', 400, 12, 7);
    const tw = Math.max(w1, w2);
    const x0 = x1 - tw - 14 - s;
    g.save(); g.globalAlpha = a * 0.96; g.translate((1 - kin) * 26, 0);
    tile(g, x0, y, s, { shadow: true });
    shadowOn(g, 12, 0.55, 2);
    txt(g, 'WHITEBROOK ROAD', x0 + s + 14, y + 26, { w: 800, s: 21, ls: 1.5 });
    txt(g, 'SPORTS', x0 + s + 15, y + 44, { w: 400, s: 12, ls: 7, c: LILAC });
    shadowOff(g);
    g.restore();
  }

  function pageTitle(g, s, u) {
    const D = s.d;
    const kout = eIn3(ramp(D - 0.55, D, u));
    const A = 1 - kout;
    if (A <= 0) return;
    const sh = g.createLinearGradient(0, 560, 0, H);
    sh.addColorStop(0, 'rgba(3,5,10,0)'); sh.addColorStop(1, `rgba(3,5,10,${0.55 * A * sstep(0, 0.6, u)})`);
    g.fillStyle = sh; g.fillRect(0, 560, W, H - 560);
    const kc = eOut3(ramp(0.15, 0.75, u));
    if (kc > 0 && DATA.league) {
      const label = DATA.league.toUpperCase();
      const lw = measure(g, label, 700, 18, 5);
      const cw = lw + 56, cx0 = 960 - cw / 2, cy0 = 150 - (1 - kc) * 16;
      g.save(); g.globalAlpha = A * kc;
      glass(g, cx0, cy0, cw, 46, 23);
      txt(g, label, 960, cy0 + 23 + 1, { w: 700, s: 18, ls: 5, al: 'center', bl: 'middle' });
      g.restore();
    }
    const kx = eOutExpo(ramp(0.12, 1.05, u));
    const hx = lerp(560, 676, kx) - kout * 60, ax = lerp(1360, 1244, kx) + kout * 60, cy = 418;
    g.save(); g.globalAlpha = A * clamp(kx * 1.5);
    crest(g, 'home', hx, cy, 236);
    crest(g, 'away', ax, cy, 236);
    g.restore();
    const kv = eOutBack(ramp(0.5, 1.0, u), 2);
    if (kv > 0) {
      g.save(); g.globalAlpha = A * clamp(kv);
      g.translate(960, cy); g.scale(kv, kv);
      g.beginPath(); g.arc(0, 0, 36, 0, TAU); g.lineWidth = 2;
      const vg = g.createLinearGradient(-36, -36, 36, 36); vg.addColorStop(0, VIOLET); vg.addColorStop(1, CORAL);
      g.strokeStyle = vg; g.stroke();
      g.fillStyle = 'rgba(7,9,15,0.45)'; g.fill();
      shadowOn(g, 10, 0.5, 2);
      txt(g, 'v', 0, -3, { w: 300, s: 44, al: 'center', bl: 'middle' });
      shadowOff(g);
      g.restore();
    }
    const kn = eOut3(ramp(0.45, 1.15, u));
    if (kn > 0) {
      g.save(); g.globalAlpha = A * kn;
      shadowOn(g, 22, 0.6, 4);
      for (const [side, x] of [['home', hx], ['away', ax]]) {
        const nm = DATA[side].name.toUpperCase();
        txt(g, nm, x, 640 + (1 - kn) * 26, { w: 800, s: fitSize(nm, 800, 60, 2, 520, 30), ls: 2, al: 'center' });
      }
      shadowOff(g);
      g.restore();
    }
    const kvn = eOut3(ramp(0.75, 1.4, u));
    if (kvn > 0 && DATA.stadium) {
      g.save(); g.globalAlpha = A * kvn;
      shadowOn(g, 16, 0.6, 3);
      txt(g, 'LIVE FROM', 960, 712 + (1 - kvn) * 14, { w: 700, s: 17, ls: 7, al: 'center', c: LILAC });
      const st = DATA.stadium.toUpperCase();
      txt(g, st, 960, 760 + (1 - kvn) * 14, { w: 700, s: fitSize(st, 700, 42, 3, 1500, 24), ls: 3, al: 'center' });
      shadowOff(g);
      g.restore();
    }
    const n = CELLS.length, bw = n === 3 ? 1240 : n === 2 ? 860 : 440, bx = 960 - bw / 2, by = 818, bh = 116, cw = bw / n;
    const kb = eOutExpo(ramp(0.95, 1.6, u));
    if (kb > 0) {
      g.save(); g.globalAlpha = A * clamp(kb * 1.4); g.translate(0, (1 - kb) * 30 + kout * 20);
      glass(g, bx, by, bw, bh, 22);
      for (let i = 1; i < n; i++) { g.fillStyle = 'rgba(255,255,255,0.12)'; g.fillRect(bx + i * cw, by + 22, 1.5, bh - 44); }
      CELLS.forEach((c, i) => {
        const ki = eOut3(ramp(1.05 + i * 0.12, 1.5 + i * 0.12, u));
        g.save(); g.globalAlpha = A * ki; g.translate(0, (1 - ki) * 10);
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
        g.restore();
      });
      g.restore();
    }
  }

  function pageOfficials(g, s, u) {
    const D = s.d;
    const kin = eOutExpo(ramp(0, 0.85, u)), kout = eIn3(ramp(D - 0.5, D, u));
    const al = clamp(kin * 1.3) * (1 - kout);
    if (al <= 0) return;
    const w = 1160, h = 182, x = 960 - w / 2, y = 806 + (1 - kin) * 50 + kout * 34;
    g.save(); g.globalAlpha = al;
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
    const kw = txt(g, PLAN.ko, kx, y + 160, { w: 800, s: 30 });
    if (countdownOn()) {
      const left = Math.max(0, Math.round((KO_AT - Date.now()) / 1000));
      const mm = Math.floor(left / 60), ss = String(left % 60).padStart(2, '0');
      txt(g, `in ${mm}:${ss}`, kx + kw + 16, y + 160, { w: 600, s: 24, c: PEACH });
    }
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
  function buildBackground() {
    const sc = mk(W, H), sg = sc.getContext('2d');
    BGCAM = beautyCam(9);
    renderScene(sg, BGCAM, 9);
    grade(sg); bloomOn(sg, sc);
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
  function drawBackground(g, t, blur, dim) {
    const k = clamp(t / DUR), z = 1.02 + 0.035 * k, dx = lerp(-14, 14, k);
    const x = (W - W * z) / 2 + dx, y = (H - H * z) / 2;
    g.drawImage(BGSHARP, x, y, W * z, H * z);
    if (blur > 0.01) { g.globalAlpha = clamp(blur); g.drawImage(BGSOFT, x, y, W * z, H * z); g.globalAlpha = 1; }
    if (dim > 0) { g.fillStyle = `rgba(4,6,12,${dim.toFixed(3)})`; g.fillRect(0, 0, W, H); }
    if (CFG.rain) drawRain(g, BGCAM, t);
  }

  // ================================================================ the frame
  function drawFrame(t) {
    const g = ctx;
    const RS = window.RENDER_SCALE || 1;   // a slower TV draws into a smaller canvas, stretched to the screen
    g.save();
    g.setTransform(RS, 0, 0, RS, 0, 0);
    g.globalAlpha = 1; g.globalCompositeOperation = 'source-over'; g.filter = 'none';
    g.clearRect(0, 0, W, H);
    const ou = seg('outro');
    const uo = ou ? t - ou.a : -1;
    const wipe = ou ? eInOut3(ramp(0.3, 1.1, uo)) : 0;
    if (wipe < 1) {
      const bp = bgParams(t);
      drawBackground(g, t, bp.blur, bp.dim);
      for (const s of SEQ) {
        if (t < s.a - 0.001 || t > s.b + 0.001) continue;
        const u = t - s.a;
        if (s.n === 'ident') pageIdent(g, s, u);
        else if (s.n === 'title') pageTitle(g, s, u);
        else if (s.n === 'home' || s.n === 'away') pageXI(g, s, u, s.n);
        else if (s.n === 'officials') pageOfficials(g, s, u);
      }
      drawBug(g, t);
      // live: up from the channel at the start
      const fin = 1 - sstep(0, 0.9, t);
      if (fin > 0) { g.save(); g.globalAlpha = fin; g.globalCompositeOperation = 'destination-out'; g.fillRect(0, 0, W, H); g.restore(); }
      // back to the channel: the brand wipe uncovers its own picture
      if (wipe > 0) {
        const edge = lerp(-260, W + 260, wipe);
        g.save();
        g.globalCompositeOperation = 'destination-out';
        g.beginPath(); g.moveTo(-300, 0); g.lineTo(edge + 140, 0); g.lineTo(edge - 140, H); g.lineTo(-300, H); g.closePath(); g.fill();
        g.restore();
        const gr = g.createLinearGradient(edge - 180, 0, edge + 180, 0);
        gr.addColorStop(0, 'rgba(109,40,242,0)'); gr.addColorStop(0.42, VIOLET); gr.addColorStop(0.58, CORAL); gr.addColorStop(1, 'rgba(255,107,107,0)');
        g.fillStyle = gr;
        g.beginPath(); g.moveTo(edge + 40, 0); g.lineTo(edge + 180, 0); g.lineTo(edge - 40, H); g.lineTo(edge - 180, H); g.closePath(); g.fill();
      }
    }
    // Back: it all fades away to the channel at once
    if (window.FADE_FROM != null) {
      const f = clamp((t - window.FADE_FROM) / 0.5);
      if (f > 0) { g.globalAlpha = f; g.globalCompositeOperation = 'destination-out'; g.fillRect(0, 0, W, H); }
    }
    g.restore();
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
    GL = groundLight();
    buildCrowd();
    buildSky();
    buildBackground();
    for (const side of ['home', 'away']) labelLayout(side);
    window.CUES = buildCues();
    window.DUR = DUR;
    window.SEQ = SEQ;
    window.SOL = SOL;
    window.INIT_MS = performance.now() - t0;
    window.drawFrame = drawFrame;
    window.READY = true;
    if (window.onBroadcastReady) window.onBroadcastReady();
  }
  init().catch((e) => { window.INIT_ERR = String(e && e.stack || e); if (window.onBroadcastFailed) window.onBroadcastFailed(window.INIT_ERR); });
})();
