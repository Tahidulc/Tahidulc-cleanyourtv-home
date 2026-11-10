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

  // ---------------------------------------------------------------- its layers (layers.js)
  // Every part is drawn once, when the pop-up is made ready, into a canvas of its own with its shadow and its glow (a
  // sprite); a frame then only places the sprites (a transform and an opacity each), so it is the compositor's work
  // alone, as the strap page's is. The trophy is drawn ready turned for each moment it is seen (a sprite each, the frame
  // only picks one); what changes as it plays (the crests' gold light, the tab's light) goes in small canvases of its own.
  const Lmul = LK.mul, LT = LK.T;
  const SPR = {};
  const bake = (x, y, w, h, draw) => {
    const c = mk(Math.max(1, Math.ceil(w)), Math.max(1, Math.ceil(h))), g = c.getContext('2d');
    g.translate(-x, -y); draw(g);
    return { c, x, y };
  };
  const at = (s, M, a, blend) => { if (s) LK.put(s.c, Lmul(M, LT(s.x, s.y)), a, blend); };
  // a flare's three parts (the round glow and its two streaks), each at full strength: the frame sets how strong
  function flareBake(size, warm) {
    const key = 'fl:' + size + ':' + warm;
    if (SPR[key]) return SPR[key];
    const c0 = warm ? [255, 236, 190] : [235, 245, 255], c1 = warm ? [255, 176, 60] : [140, 190, 255];
    const rad = bake(-size - 1, -size - 1, 2 * size + 2, 2 * size + 2, (g) => {
      const rg = g.createRadialGradient(0, 0, 0, 0, 0, size);
      rg.addColorStop(0, `rgba(${c0},1)`); rg.addColorStop(0.18, `rgba(${c1},0.55)`); rg.addColorStop(1, `rgba(${c1},0)`);
      g.fillStyle = rg; g.fillRect(-size, -size, size * 2, size * 2);
    });
    const hw = size * 3.2, hh = size * 0.035, vw = size * 0.035, vh = size * 1.6;
    const hs = bake(-hw - 1, -hh - 1, 2 * hw + 2, 2 * hh + 2, (g) => {
      const lg = g.createLinearGradient(-hw, 0, hw, 0);
      lg.addColorStop(0, `rgba(${c0},0)`); lg.addColorStop(0.5, `rgba(${c0},0.85)`); lg.addColorStop(1, `rgba(${c0},0)`);
      g.fillStyle = lg; g.fillRect(-hw, -hh, 2 * hw, 2 * hh);
    });
    const vs = bake(-vw - 1, -vh - 1, 2 * vw + 2, 2 * vh + 2, (g) => {
      const vg = g.createLinearGradient(0, -vh, 0, vh);
      vg.addColorStop(0, `rgba(${c0},0)`); vg.addColorStop(0.5, `rgba(${c0},0.85)`); vg.addColorStop(1, `rgba(${c0},0)`);
      g.fillStyle = vg; g.fillRect(-vw, -vh, 2 * vw, 2 * vh);
    });
    return (SPR[key] = [rad, hs, vs]);
  }
  function flareL(x, y, size, a, warm = true) {
    if (a <= 0.003) return;
    for (const s of flareBake(size, warm)) at(s, LT(x, y), a, 'lighter');
  }
  const sparkleBake = (s) => SPR['sk:' + s] || (SPR['sk:' + s] = bake(-s - 2, -s - 2, 2 * s + 4, 2 * s + 4, (g) => sparkle(g, 0, 0, s, 1)));
  // the gold halo behind a crest, at its strongest (glow 1.2); the frame sets how strong
  function haloBake() {
    const r = R_CREST;
    return SPR.halo || (SPR.halo = bake(-r * 1.6 - 1, -r * 1.6 - 1, r * 3.2 + 2, r * 3.2 + 2, (g) => {
      const hg = g.createRadialGradient(0, 0, r * 0.7, 0, 0, r * 1.55);
      hg.addColorStop(0, 'rgba(255,205,90,0.66)'); hg.addColorStop(0.45, 'rgba(255,170,40,0.336)'); hg.addColorStop(1, 'rgba(255,150,20,0)');
      g.fillStyle = hg; g.fillRect(-r * 1.6, -r * 1.6, r * 3.2, r * 3.2);
    }));
  }
  // a crest's coin with the gold over it (a warm wash from the light's side, a glint passing): its own small canvas,
  // drawn again only when the light on it changes
  function medalCoin(side, sheen, tilt, glow) {
    const base = medallion(side), sz = base.width, r = R_CREST;
    const c = LK.dyn('medal-' + side, sz, sz), g = c._g;
    const key = sheen.toFixed(3) + '|' + tilt.toFixed(4) + '|' + glow.toFixed(3);
    if (c._key === key) return c;
    c._key = key;
    g.setTransform(1, 0, 0, 1, 0, 0); g.globalCompositeOperation = 'source-over'; g.globalAlpha = 1;
    g.clearRect(0, 0, sz, sz);
    g.drawImage(base, 0, 0);
    g.save(); g.translate(sz / 2, sz / 2);
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
    return c;
  }
  // tilt: the circle turned about its upright axis (radians); glow: the gold light behind and over it (0..1.2);
  // sheen: a gold glint passing across (0..1 as it goes, 0 when none)
  function medalLayers(side, x, y, scale, alpha, sheen, tilt, glow) {
    if (alpha <= 0.001 || scale <= 0.01) return;
    if (glow > 0.01) at(haloBake(), [scale, 0, 0, scale, x, y], alpha * glow / 1.2, 'lighter');
    const c = medalCoin(side, sheen, tilt, glow), h = c.width / 2;
    const ct = Math.max(0.08, Math.cos(tilt));
    // turned: narrower, with a slight lean as it would in perspective
    LK.put(c, Lmul([ct, Math.sin(tilt) * 0.16, 0, 1, x, y], [scale, 0, 0, scale, -h * scale, -h * scale]), alpha);
  }
  // the tab: its two ends and a column of its middle (stretched to any width), cut from one drawn at TAB_REF wide
  const TAB_REF = 400, TAB_M = 26;
  function tabBake() {
    if (SPR.tab) return SPR.tab;
    const bot = CY - BAR_H / 2, top = bot - TAB_H, x0 = 960 - TAB_REF / 2, x1 = 960 + TAB_REF / 2;
    const full = bake(x0 - TAB_M, top - TAB_M, TAB_REF + 2 * TAB_M, TAB_H + TAB_M, (g) => drawTab(g, TAB_REF, CY, 1));
    const cut = (sx, w) => {
      const c = mk(w, full.c.height);
      c.getContext('2d').drawImage(full.c, sx - full.x, 0, w, full.c.height, 0, 0, w, full.c.height);
      return { c, x: sx, y: full.y };
    };
    return (SPR.tab = { L: cut(x0 - TAB_M, TAB_M + 12), M: cut(959, 2), R: cut(x1 - 12, TAB_M + 12) });
  }
  function tabLayers(G, tw, rr) {
    const P = tabBake(), M = Lmul(G, LT(0, (1 - rr) * TAB_H));
    const xl = Math.round(960 - tw / 2), xr = xl + Math.round(tw);
    at(P.L, Lmul(M, LT(xl - (960 - TAB_REF / 2), 0)), 1);
    const mw = xr - xl - 24;
    if (mw > 0) LK.put(P.M.c, Lmul(M, [mw / 2, 0, 0, 1, xl + 12, P.M.y]), 1);
    at(P.R, Lmul(M, LT(xr - (960 + TAB_REF / 2), 0)), 1);
  }
  // the tab's words for a page, sharp and in steps of blur (they come in out of a blur on a change)
  const TABW = {};
  function tabContentBake(kind, m, lvl) {
    const key = 'tc:' + kind + ':' + lvl;
    if (SPR[key]) return SPR[key];
    const g0 = mk(8, 8).getContext('2d');
    let w = 0; tabItems(g0, kind, m).forEach((it, i) => { w += it.w + (i ? (it.gap || 14) : 0); });
    const mid = CY - BAR_H / 2 - TAB_H / 2, pad = 26;
    return (SPR[key] = bake(Math.floor(960 - w / 2 - pad), mid - TAB_H / 2 - pad, w + 2 * pad + 1, TAB_H + 2 * pad, (g) => drawTabContent(g, tabItems(g, kind, m), mid, 1, lvl)));
  }
  const blurLvl = (b) => (b > 0.2 ? Math.min(8, Math.round(b)) : 0);
  function tabSweep(G, tw, top, swapK) {
    const TW = 1000, c = LK.dyn('tabsweep', TW, TAB_H), g = c._g, ox = 960 - TW / 2;
    g.setTransform(1, 0, 0, 1, 0, 0); g.globalCompositeOperation = 'source-over'; g.clearRect(0, 0, TW, TAB_H);
    g.translate(-ox, 0);
    const sx = lerp(960 - tw / 2 - 80, 960 + tw / 2 + 80, clamp(swapK / 0.5));
    const sg = g.createLinearGradient(sx - 60, 0, sx + 60, 0); sg.addColorStop(0, 'rgba(255,255,255,0)'); sg.addColorStop(0.5, 'rgba(255,255,255,0.35)'); sg.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = sg; g.fillRect(960 - tw / 2, 0, tw, TAB_H);
    LK.put(c, Lmul(G, LT(ox, top)), 1, 'lighter');
  }
  const barTextBake = (kind, m) => SPR['bt:' + kind] || (SPR['bt:' + kind] = bake(BAR_L, CY - BAR_H / 2, BAR_R - BAR_L, BAR_H, (g) => {
    g.beginPath(); g.rect(BAR_L, CY - BAR_H / 2, BAR_R - BAR_L, BAR_H); g.clip();
    barText(g, kind, m, CY, 0);
  }));
  const centreBake = (kind, m) => SPR['cb:' + kind] || (SPR['cb:' + kind] = bake(960 - 62 - 24, CY - 45 - 24, 124 + 48, 90 + 48, (g) => centreBox(g, kind, m, CY, 1)));
  function bakeFixed() {
    SPR.bar = bake(BAR_L, CY - BAR_H / 2 - 2, BAR_R - BAR_L, BAR_H + 4, (g) => drawBar(g, BAR_L, BAR_R, CY));
    SPR.sheen = bake(-120, CY - BAR_H / 2, 240, BAR_H, (g) => {
      const sg = g.createLinearGradient(-120, 0, 120, 0);
      sg.addColorStop(0, 'rgba(255,255,255,0)'); sg.addColorStop(0.5, 'rgba(160,200,255,0.28)'); sg.addColorStop(1, 'rgba(255,255,255,0)');
      g.fillStyle = sg; g.fillRect(-120, CY - BAR_H / 2, 240, BAR_H);
    });
    const bx = 960 + 64;
    SPR.beamL = bake(bx - 16, 600, 32, 330, (g) => {
      const bg = g.createLinearGradient(bx - 16, 0, bx + 16, 0);
      bg.addColorStop(0, 'rgba(255,255,255,0)'); bg.addColorStop(0.5, 'rgba(255,255,255,0.75)'); bg.addColorStop(1, 'rgba(255,255,255,0)');
      g.fillStyle = bg; g.fillRect(bx - 16, 600, 32, 330);
    });
    SPR.beamC = bake(bx - 3, 600, 6, 330, (g) => {
      const cg = g.createLinearGradient(0, 600, 0, 930); cg.addColorStop(0, 'rgba(255,255,255,0)'); cg.addColorStop(0.35, 'rgba(255,255,255,0.85)'); cg.addColorStop(1, 'rgba(255,255,255,0)');
      g.fillStyle = cg; g.fillRect(bx - 2.5, 600, 5, 330);
    });
    haloBake(); tabBake();
    for (const [s, w] of [[220, true], [120, true], [150, true], [230, true], [90, true]]) flareBake(s, w);
  }
  function bakeMoment(m) {
    const K = KINDS[m.kind];
    const g0 = mk(8, 8).getContext('2d');
    for (const [tabKind, mainKind] of K.pages) {
      TABW[tabKind] = tabWidth(tabItems(g0, tabKind, m));
      for (let l = 0; l <= 8; l++) tabContentBake(tabKind, m, l);
      barTextBake(mainKind, m);
      if (mainKind === 'vs' || mainKind === 'score') centreBake(mainKind, m);
    }
  }

  // ---- the trophy, ready turned: the 3D model drawn once for each moment it is seen (every 1/30 s as it turns in and
  // away, every 1/10 s as it sways, the frame showing the two either side of the moment mixed), each scaled as the frame
  // drew it and cut to the box its pixels keep to; the frame only shows one or two of them
  let TSPR = null;
  const trophyRot = (T, tc) => -1.25 * (1 - eOut3(ramp(0.14, 0.8, T))) + 0.13 * Math.sin(T * 0.75) + 1.0 * eIn3(ramp(tc + 0.12, tc + 0.55, T));
  async function trophySprites(kind3, tc) {
    const T3 = window.TROPHY3D, t0 = performance.now();
    const fit = T3_FIT[kind3] || T3_FIT.generic;
    const times = [];
    for (let i = 0; 0.14 + i / 30 < 0.8 - 1e-6; i++) times.push(0.14 + i / 30);
    for (let i = 0; 0.8 + i * 0.1 < tc + 0.12 - 1e-6; i++) times.push(0.8 + i * 0.1);
    for (let i = 0; tc + 0.12 + i / 30 < tc + 0.55 + 1e-6; i++) times.push(tc + 0.12 + i / 30);
    const render = (T) => T3.render(kind3, trophyRot(T, tc), 0, T);
    let cv = render(times[0]);
    const k = (T3_H * fit[0]) / (cv.height * 0.9), dw = cv.width * k, dh = cv.height * k;
    const X0 = 960 - dw / 2, Y0 = T_TOP - cv.height * 0.08 * k + fit[1];
    // the box: what is drawn at a dozen moments through the turn, a quarter size, with room to spare
    const pw = Math.ceil(dw / 4), ph = Math.ceil(dh / 4);
    const pg = mk(pw, ph).getContext('2d', { willReadFrequently: true });
    let bx0 = pw, by0 = ph, bx1 = -1, by1 = -1;
    for (let j = 0; j < 12; j++) {
      const T = times[Math.round((j * (times.length - 1)) / 11)];
      cv = render(T);
      pg.clearRect(0, 0, pw, ph); pg.drawImage(cv, 0, 0, pw, ph);
      const d = pg.getImageData(0, 0, pw, ph).data;
      for (let y = 0; y < ph; y++) for (let x = 0; x < pw; x++) if (d[(y * pw + x) * 4 + 3] > 1) { if (x < bx0) bx0 = x; if (x > bx1) bx1 = x; if (y < by0) by0 = y; if (y > by1) by1 = y; }
    }
    if (bx1 < 0) { bx0 = 0; by0 = 0; bx1 = pw; by1 = ph; }
    const SX = Math.floor(X0 + Math.max(0, bx0 * 4 - 20)), SY = Math.floor(Y0 + Math.max(0, by0 * 4 - 20));
    // (rising, it only goes down from here: nothing below the screen's foot is ever seen)
    const EX = Math.ceil(X0 + Math.min(dw, bx1 * 4 + 24)), EY = Math.ceil(Math.min(Y0 + Math.min(dh, by1 * 4 + 24), 1080));
    const list = [];
    for (let i = 0; i < times.length; i++) {
      cv = render(times[i]);
      const c = mk(EX - SX, EY - SY), g = c.getContext('2d');
      g.imageSmoothingQuality = 'high';
      g.drawImage(cv, X0 - SX, Y0 - SY, dw, dh);
      list.push({ t: times[i], c, x: SX, y: SY });
      if (i % 6 === 5) await new Promise((r) => setTimeout(r, 0));
    }
    TSPR = { list };
    const ms = performance.now() - t0;
    if (window.BX) BX.log('trophy: ' + list.length + ' turns of ' + (EX - SX) + 'x' + (EY - SY) + ' drawn in ' + ms.toFixed(0) + ' ms');
    window.TROPHY_SPRITES = { n: list.length, w: EX - SX, h: EY - SY, ms };
  }
  // The turns are never on the page themselves: the one (or two, mixed) for the moment is copied into one small canvas,
  // which is all the compositor sees. Each turn a layer of its own (93 of them, about 79 MB) froze the Streamer for
  // seconds at a time as each was handed to its GPU the first time it showed.
  function trophyLayers(T, trA, rise) {
    const L = TSPR.list, M = LT(0, rise);
    let i = 0; while (i < L.length - 1 && L[i + 1].t <= T) i++;
    const A = L[i], B = L[Math.min(i + 1, L.length - 1)];
    let top = A, under = null, f = 0;
    if (B !== A && T > A.t) {
      f = clamp((T - A.t) / (B.t - A.t));
      if (B.t - A.t < 0.06) top = f < 0.5 ? A : B;   // turning: the nearest
      else { under = B; f = Math.round(f * 50) / 50; }   // swaying: the two mixed
    }
    const w = A.c.width, h = A.c.height, c = LK.dyn('trophy:' + w + 'x' + h, w, h), g = c._g;
    const key = L.indexOf(top) + '|' + (under ? f : '-');
    if (c._key !== key) {
      c._key = key;
      g.setTransform(1, 0, 0, 1, 0, 0); g.globalCompositeOperation = 'source-over'; g.globalAlpha = 1;
      g.clearRect(0, 0, w, h);
      if (under) { g.drawImage(under.c, 0, 0); g.globalAlpha = 1 - f; }
      g.drawImage(top.c, 0, 0);
      g.globalAlpha = 1;
    }
    LK.put(c, Lmul(M, LT(A.x, A.y)), trA);
  }

  function popupLayers(m, T) {
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
      if (TSPR) trophyLayers(T, trA, rise);
      else at(SPR.tr2d, LT(0, rise), trA);
      // glints on the trophy's top and shoulders, following its size and its top
      const gl = [[-62, 18, 1.2], [0, 4, 2.4], [62, 18, 3.6], [-48, 140, 5.0], [55, 175, 0.6]];
      const gf = T3_FIT[kind3] || T3_FIT.generic, gs = sparkleBake(26 * Math.max(0.7, gf[2]));
      for (const [x, y, ph] of gl) {
        const a = Math.pow(Math.max(0, Math.sin(T * 2.1 + ph * 1.7)), 14) * 0.9 * trA;
        if (a > 0.01) at(gs, LT(960 + x * gf[2], T_TOP + gf[3] + y * gf[2] + rise), a, 'lighter');
      }
    }
    // the light that comes with it
    flareL(960, CY - 30, 220, 0.85 * Math.sin(PI * ramp(0, 0.5, T)));
    const beam = Math.sin(PI * ramp(0.28, 0.72, T));
    if (beam > 0.01) {
      at(SPR.beamL, LK.I, beam, 'lighter');
      at(SPR.beamC, LK.I, beam);
      flareL(960 + 64, 640 + rise * 0.6, 120, 0.6 * beam, true);
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
    const syc = Math.max(0.02, sy), G = [1, 0, 0, syc, 0, CY - CY * syc];
    // the tab (behind the bar, which hides its foot): on a change its words go, a light sweeps across, the new words come
    // in out of a blur
    let tw = TABW[tabKind], newKind = null;
    if (swapK >= 0) { newKind = K.pages[Math.min(page + 1, last)][0]; tw = lerp(TABW[tabKind], TABW[newKind], eInOut3(clamp((swapK - 0.25) / 0.5))); }
    if (tabGone < 1 && openK > 0.6) {
      const rr = tabRise * (1 - eIn3(tabGone));
      if (rr > 0.01) {
        tabLayers(G, tw, rr);
        const top = CY - BAR_H / 2 - TAB_H + (1 - rr) * TAB_H, MG = Lmul(G, LT(0, (1 - rr) * TAB_H));
        if (swapK >= 0) {
          if (swapK < 0.38) { const a = 1 - swapK / 0.38; if (a > 0.01) at(tabContentBake(tabKind, m, 0), MG, a); }
          else { const k2 = (swapK - 0.38) / 0.62; if (k2 > 0.01) at(tabContentBake(newKind, m, blurLvl(8 * (1 - k2))), MG, k2); }
          tabSweep(G, tw, top, swapK);
        } else if (1 - tabGone > 0.01) at(tabContentBake(tabKind, m, blurLvl(6 * tabGone)), MG, 1 - tabGone);
      }
    }
    // the bar between the crests, opening out: its words, a shade as it flips, a sheen along it as it opens
    const bx0 = Math.max(BAR_L, lx + R_CREST * 0.35), bx1 = Math.min(BAR_R, rx - R_CREST * 0.35);
    const barA = clamp(spread * 6) * (1 - endK);
    if (bx1 - bx0 >= 2 && barA > 0.002) {
      LK.clip('bar', Lmul(G, LT(bx0, CY - BAR_H / 2 - 2)), bx1 - bx0, BAR_H + 4);
      at(SPR.bar, G, barA);
      if (bx1 - bx0 > 4 && barA > 0.01) {
        const ta = (1 - ramp(tc + 0.08, tc + 0.24, T)) * barA;
        at(barTextBake(mainKind, m), Lmul(G, [1, 0, skew, 1, -skew * CY, 0]), ta);
        if (shade > 0) LK.put(LK.fill('rgb(0,0,20)'), Lmul(G, LK.rect(BAR_L, CY - BAR_H / 2, BAR_R - BAR_L, BAR_H, 4, 4)), ta * 0.45 * shade);
        const sw = ramp(0.86, 1.3, T);
        if (sw > 0 && sw < 1) at(SPR.sheen, Lmul(G, LT(lerp(bx0 - 200, bx1 + 200, sw), 0)), ta, 'lighter');
      }
      LK.endClip();
    }
    // the V (or score) between the crests
    const vIn = eOutBack(ramp(0.44, 0.6, T), 1.6) * (1 - ramp(tc + 0.05, tc + 0.2, T));
    if (vIn > 0.01 && (mainKind === 'vs' || mainKind === 'score')) at(centreBake(mainKind, m), Lmul(G, [vIn, 0, 0, vIn, 960 - 960 * vIn, CY - CY * vIn]), 1);
    // crests, in front: they turn in as they fly in, lean as they slide, rock gently while open, turn as they meet
    const inTilt = (1 - eOutBack(ramp(0.06, 0.62, T), 1.4)) * 1.15;
    const slide = Math.sin(PI * clamp(openK)) * 0.42 * (1 - closeK) + Math.sin(PI * clamp(closeK)) * 0.5;
    const rock = 0.3 * Math.sin(T * 1.25) * ramp(0.9, 1.5, T) * (1 - ramp(tc, tc + 0.2, T));
    const outTilt = eIn3(endK) * 1.2;
    const tiltL = -inTilt - slide + rock - outTilt, tiltR = inTilt + slide - rock * 0.9 + outTilt;
    const glow = 0.95 * Math.sin(PI * ramp(0.0, 0.75, T)) + 0.42 + 0.1 * Math.sin(T * 2.3) + 0.9 * Math.sin(PI * ramp(tc + 0.15, tc + 0.6, T));
    const sheenL = ramp(1.35, 2.1, T) + ramp(5.0, 5.75, T), sheenR = ramp(1.5, 2.25, T) + ramp(5.15, 5.9, T);
    medalLayers('home', lx, CY, medScale, medAlpha, sheenL % 1 === 0 ? 0 : sheenL % 1, tiltL, Math.min(1.2, glow));
    medalLayers('away', rx, CY, medScale, medAlpha, sheenR % 1 === 0 ? 0 : sheenR % 1, tiltR, Math.min(1.2, glow));
    // flashes: as they arrive, and where they meet when it closes
    flareL(960, CY, 150, 0.8 * Math.sin(PI * ramp(0.04, 0.3, T)));
    const meet = Math.sin(PI * ramp(tc + 0.28, tc + 0.56, T));
    flareL(960, CY - 10, 230, 0.95 * meet);
    flareL(960, CY - 70, 90, 0.7 * Math.sin(PI * ramp(tc + 0.1, tc + 0.4, T)));
  }

  // ---------------------------------------------------------------- the frame: the layers placed for second t
  function drawFrame(t) {
    LK.begin();
    for (const m of POP.moments) {
      const T = t - m.t0;
      if (T >= -0.001 && T <= popLen(m)) popupLayers(m, T);
    }
    LK.end();
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
    // every part drawn now, once (the frames only place them): the fixed parts, each moment's words, the trophy turned
    LK.mount();
    bakeFixed();
    const Q = POP.q || {};
    for (const m of POP.moments) {
      bakeMoment(m);
      const kind3 = m.trophy || MATCH.trophy || MATCH.comp;
      if (window.TROPHY3D) {
        if (+Q.t3res > 0 && +Q.t3res < 1 && window.TROPHY3D.setRes) window.TROPHY3D.setRes(+Q.t3res);
        await trophySprites(kind3, KINDS[m.kind].close);
      } else {
        const c = trophyCanvas(kind3);
        SPR.tr2d = { c, x: 960 - c.width / 2, y: T_TOP - 10 };
      }
    }
    window.DUR = POP.dur;
    window.SOL = SOL;
    window.INIT_MS = performance.now() - t0;
    window.drawFrame = drawFrame;
    window.READY = true;
    if (window.onBroadcastReady) window.onBroadcastReady();
  }
  init().catch((e) => { window.INIT_ERR = String(e && e.stack || e); if (window.onBroadcastFailed) window.onBroadcastFailed(window.INIT_ERR); });
})();
