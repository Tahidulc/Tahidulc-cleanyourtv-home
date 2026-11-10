
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
