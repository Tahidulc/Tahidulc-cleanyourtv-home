
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

  // ---------------------------------------------------------------------------------------------------- one frame
  // As compositor layers (layers.js), as the strap page is drawn: every part is drawn once into a canvas of its own (a
  // sprite, its shadow baked in) when the line-ups are made ready, and a frame only places the sprites by transform and
  // opacity. What changes as it plays (a hologram forming, a glint along a title, the light sweeping a tab, sparkles and
  // their particles) goes in small canvases of its own, drawn again only while it changes.
  let BGC = null;
  const Lmul = LK.mul, LT = LK.T;
  const SPR = {};
  const bake = (x, y, w, h, draw) => {
    const c = mk(Math.max(1, Math.ceil(w)), Math.max(1, Math.ceil(h))), g = c.getContext('2d');
    g.translate(-x, -y); draw(g);
    return { c, x, y };
  };
  const at = (s, M, a, blend, top) => { if (s) LK.put(s.c, Lmul(M, LT(s.x, s.y)), a, blend, top); };
  const fillAll = (col, a, blend) => LK.put(LK.fill(col), LK.rect(0, 0, W, H, 4, 4), a, blend);

  // ---- the figures: the picture (and the fallback's lines of light), its glow, and its hologram as it forms
  function figureLayers(F, s, a, fl = 1) {
    if (a <= 0.003) return;
    const [X, Y, w, hh] = figRect(F, s);
    LK.put(F.c, [w / F.c.width, 0, 0, hh / F.c.height, X, Y], a);
    if (F.add) LK.put(F.add, [w / F.c.width, 0, 0, hh / F.c.height, X, Y], a * fl, 'lighter');
  }
  function glowLayer(F, s, a) {
    if (a <= 0.003) return;
    const [X, Y, w, hh] = figRect(F, s);
    LK.put(F.glow, [(w + 12) / F.glow.width, 0, 0, (hh + 12) / F.glow.height, X - 6, Y - 6], Math.min(1, a), 'lighter');
  }
  // the hologram in its strips, each shaken sideways as it forms: drawn (at the size shown) only while it shows
  function holoLayer(key, F, s, a, u) {
    if (a <= 0.003) return;
    const [X, Y, w, hh] = figRect(F, s);
    const pad = 8, c = LK.dyn('holo:' + key, w + 2 * pad, hh + 2), g = c._g;
    const strips = 18, sh = F.c.height / strips, k = hh / F.c.height;
    g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
    g.clearRect(0, 0, c.width, c.height);
    g.globalCompositeOperation = 'lighter';
    const fx = X - Math.floor(X) + pad, fy = Y - Math.floor(Y);
    for (let i = 0; i < strips; i++) {
      const off = Math.sin(u * 37 + i * 1.9) * 7 * (1 - clamp(u));
      g.drawImage(F.holo, 0, i * sh, F.c.width, sh, fx + off, fy + i * sh * k, w, sh * k + 1);
    }
    LK.put(c, LT(Math.floor(X) - pad, Math.floor(Y)), Math.min(1, a), 'lighter');
  }

  // ---- light: sparkles (a small one in its own canvas; a big one as its glow and its four rays, each a sprite), the
  // particles of a beam-in or a beam-out (their own canvas while they fly), the beam at a figure's foot, the sweeps
  const RAY = (function () {   // a ray of a sparkle: 512 x 8, its light along it
    const c = mk(512, 8), g = c.getContext('2d');
    const lg = g.createLinearGradient(0, 0, 512, 0);
    lg.addColorStop(0, 'rgba(70,120,230,0)'); lg.addColorStop(0.5, 'rgba(225,238,255,0.92)'); lg.addColorStop(1, 'rgba(70,120,230,0)');
    g.fillStyle = lg; g.fillRect(0, 0, 512, 8);
    return c;
  })();
  const GLINT = (function () {   // a sparkle's round glow, radius 256
    const c = mk(512, 512), g = c.getContext('2d');
    const gr = g.createRadialGradient(256, 256, 0, 256, 256, 256);
    gr.addColorStop(0, 'rgba(255,255,255,0.95)'); gr.addColorStop(0.12, 'rgba(160,195,255,0.85)');
    gr.addColorStop(0.4, 'rgba(45,95,225,0.38)'); gr.addColorStop(1, 'rgba(15,45,190,0)');
    g.fillStyle = gr; g.beginPath(); g.arc(256, 256, 256, 0, TAU); g.fill();
    return c;
  })();
  function sparkleLayer(key, x, y, k, size, rot = 0, top) {
    if (k <= 0.003) return;
    if (size >= 120) {
      const r = size * (0.3 + 0.7 * k);
      LK.put(GLINT, [r / 256, 0, 0, r / 256, x - r, y - r], k, 'lighter', top);
      for (const [ang, L, wd] of [[0, 3.0, 0.07], [PI / 2, 2.0, 0.06], [PI / 4, 1.0, 0.04], [-PI / 4, 1.0, 0.04]]) {
        const len = r * L * k, w = Math.max(1.5, r * wd);
        const M = Lmul(Lmul(LT(x, y), LK.R(ang + rot)), [2 * len / 512, 0, 0, w / 8, -len, -w / 2]);
        LK.put(RAY, M, k, 'lighter', top);
      }
      return;
    }
    const ext = Math.ceil(size * 3 + 4), c = LK.dyn('spk:' + key, 2 * ext, 2 * ext), g = c._g;
    g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
    g.clearRect(0, 0, c.width, c.height);
    const ox = Math.floor(x) - ext, oy = Math.floor(y) - ext;
    g.translate(-ox, -oy);
    sparkle(g, x, y, k, size, rot);
    LK.put(c, LT(ox, oy), 1, 'lighter', top);
  }
  function particleLayer(key, s, uIn, seedIn, uOut, seedOut) {
    const inOn = uIn > 0.05 && uIn < 1.3, outOn = uOut > 0.02 && uOut < 0.72;
    if (!inOn && !outOn) return;
    const P = s.P, x0 = Math.floor(s.x - 1.65 * P), y0 = Math.floor(s.ny - 2.25 * P);
    const c = LK.dyn('pt:' + key, 3.3 * P + 2, 4.25 * P + 2), g = c._g;
    g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
    g.clearRect(0, 0, c.width, c.height);
    g.translate(-x0, -y0);
    if (inOn) particles(g, s.x, s.ny, P, uIn, seedIn, 'in');
    if (outOn) particles(g, s.x, s.ny, P, uOut, seedOut, 'out');
    LK.put(c, LT(x0, y0), 1, 'lighter');
  }
  const FOOT = {};
  function footBake(P) {
    const bw = P * 1.1, key = bw.toFixed(2);
    return FOOT[key] || (FOOT[key] = bake(-bw - 1, -bw * 0.5 - 1, bw * 2 + 2, bw * 0.7 + 2, (g) => {
      const lg = g.createRadialGradient(0, 0, 4, 0, 0, bw);
      lg.addColorStop(0, 'rgba(185,210,255,0.75)'); lg.addColorStop(0.3, 'rgba(50,100,215,0.4)'); lg.addColorStop(1, 'rgba(25,60,200,0)');
      g.fillStyle = lg; g.fillRect(-bw, -bw * 0.5, bw * 2, bw * 0.7);
    }));
  }
  // a band of light swept across the screen (beam-outs and wipes): the band and its line, and its sparkle
  const SWEEP = {};
  function sweepLayers(k, y, h, a = 1) {
    if (k <= 0 || k >= 1) return;
    const x = lerp(-420, W + 420, eInOut3(k));
    const key = y + ':' + h;
    const S = SWEEP[key] || (SWEEP[key] = bake(-420, y - h / 2, 540, h, (g) => {
      const lg = g.createLinearGradient(-420, 0, 120, 0);
      lg.addColorStop(0, 'rgba(25,60,190,0)'); lg.addColorStop(0.7, `rgba(55,105,225,${(0.55 * a).toFixed(3)})`);
      lg.addColorStop(0.93, `rgba(215,230,255,${(0.95 * a).toFixed(3)})`); lg.addColorStop(1, 'rgba(215,230,255,0)');
      g.fillStyle = lg; g.fillRect(-420, y - h / 2, 540, h);
      g.globalCompositeOperation = 'lighter';
      g.fillStyle = `rgba(130,170,240,${(0.35 * a).toFixed(3)})`; g.fillRect(-420, y - 2, 540, 4);
    }));
    at(S, LT(x, 0), 1, 'lighter');
    sparkleLayer('sweep', x + 90, y, 0.9 * a, h * 1.2, 0);
  }

  // ---- tabs: each player's (number and name) drawn once with its shadow; the light that sweeps it as it lands, its own
  const TABS = {};
  function tabBake(num, name, cx, cy, h) {
    const key = num + '|' + name + '|' + h + '|' + cx + '|' + cy;
    if (TABS[key]) return TABS[key];
    const { fsN, fsT, lsT } = tabType(h);
    const g0 = TMP_G;
    const nw = Math.max(h * 0.92, cmeasure(g0, num, 700, fsN, 0) + h * 0.34);
    const tw = cmeasure(g0, name, 600, fsT, lsT) + h * 0.52;
    const Wt = nw + h * 0.07 + tw, m = Math.ceil(h * 0.8) + 6;
    const s = bake(Math.floor(cx - Wt / 2) - m, Math.floor(cy - h / 2) - m, Wt + 2 * m + 2, h + 2 * m + 2, (g) => drawTab(g, num, name, cx, cy, h, 1, 0, 0));
    s.Wt = Wt; s.h = h;
    return (TABS[key] = s);
  }
  function tabLayers(key, num, name, cx, cy, h, a, slide, flash) {
    if (a <= 0.003) return;
    const s = tabBake(num, name, cx, cy, h);
    at(s, LT(slide, 0), a);
    if (flash > 0) {
      const Wt = s.Wt, x0 = cx - Wt / 2 + slide, y0 = cy - h / 2;
      const c = LK.dyn('tf:' + key, Wt + 4, h + 2), g = c._g;
      const ox = Math.floor(x0) - 1, oy = Math.floor(y0);
      const k = flash.toFixed(4) + ':' + (x0 - ox).toFixed(3);
      if (c._k !== k) {
        c._k = k;
        g.setTransform(1, 0, 0, 1, 0, 0); g.globalAlpha = 1; g.globalCompositeOperation = 'source-over';
        g.clearRect(0, 0, c.width, c.height);
        g.translate(-ox, -oy);
        const fx = x0 + Wt * flash;
        const fg = g.createLinearGradient(fx - h * 2, 0, fx + h * 0.4, 0);
        fg.addColorStop(0, 'rgba(80,130,220,0)'); fg.addColorStop(0.85, 'rgba(190,212,250,0.62)'); fg.addColorStop(1, 'rgba(190,212,250,0)');
        g.fillStyle = fg; g.fillRect(Math.max(x0, fx - h * 2), y0, Math.min(h * 2.4, x0 + Wt - (fx - h * 2)), h);
      }
      LK.put(c, LT(ox, oy), a, 'lighter');
    }
  }

  // ---- the bar: its body, its glow, the brand, the titles (and the glint along a new one), the crest coin
  function titleLayers(s, cx, base, a, glint) {
    if (a <= 0.003 || !s) return;
    const T = chromeTitle(s);
    const x = cx - (T.w - 60) / 2 - T.bx, y = base - T.by;
    LK.put(T.c, LT(x, y), a);
    if (glint > 0 && glint < 1) {
      const c = LK.dyn('glint:' + s, T.w, T.h), tg = c._g;
      tg.setTransform(1, 0, 0, 1, 0, 0); tg.globalAlpha = 1;
      tg.globalCompositeOperation = 'source-over'; tg.clearRect(0, 0, T.w, T.h); tg.drawImage(T.c, 0, 0);
      tg.globalCompositeOperation = 'source-atop';
      const gx = lerp(-120, T.w + 120, glint);
      const lg = tg.createLinearGradient(gx - 90, 0, gx + 90, 0);
      lg.addColorStop(0, 'rgba(255,255,255,0)'); lg.addColorStop(0.5, 'rgba(255,255,255,0.95)'); lg.addColorStop(1, 'rgba(255,255,255,0)');
      tg.fillStyle = lg; tg.fillRect(0, 0, T.w, T.h);
      tg.globalCompositeOperation = 'source-over';
      LK.put(c, LT(x, y), a * 0.85, 'lighter');
    }
  }
  function bakeBar() {
    SPR.barBody = bake(0, BAR_Y - 2, W, H - BAR_Y + 2, (g) => {
      const bg = g.createLinearGradient(0, BAR_Y, 0, H);
      bg.addColorStop(0, '#1c3d8c'); bg.addColorStop(0.05, '#112a66'); bg.addColorStop(0.42, '#0a1c4a'); bg.addColorStop(1, '#050e2c');
      g.fillStyle = bg; g.fillRect(0, BAR_Y, W, H - BAR_Y);
      const gl = g.createLinearGradient(0, BAR_Y, 0, BAR_Y + 46); gl.addColorStop(0, 'rgba(255,255,255,0.11)'); gl.addColorStop(1, 'rgba(255,255,255,0)');
      g.fillStyle = gl; g.fillRect(0, BAR_Y, W, 46);
      const el = g.createLinearGradient(0, 0, W, 0);
      el.addColorStop(0, 'rgba(80,120,200,0.3)'); el.addColorStop(0.5, 'rgba(150,185,240,0.95)'); el.addColorStop(1, 'rgba(80,120,200,0.3)');
      g.fillStyle = el; g.fillRect(0, BAR_Y - 2, W, 3);
    });
    SPR.barGlow = bake(0, BAR_Y - 26, W, 34, (g) => {
      const eg = g.createLinearGradient(0, BAR_Y - 26, 0, BAR_Y + 8); eg.addColorStop(0, 'rgba(40,80,190,0)'); eg.addColorStop(1, 'rgba(50,95,200,0.26)');
      g.fillStyle = eg; g.fillRect(0, BAR_Y - 26, W, 34);
    });
    SPR.barMark = bake(30, 940, 520, 80, (g) => wordmark(g, 64, 980, 30));
    for (const side of ['home', 'away']) SPR['barCoin:' + side] = bake(1592 - 93 - 40, 962 - 93 - 40, 186 + 80, 186 + 80, (g) => drawCoin(g, side, 1592, 962, 86, false, 0, 1));
    SPR.hint = bake(36, 26, 360, 84, (g) => drawHint(g, 3));
    SPR.subsShade = bake(0, 560, W, BAR_Y - 560, (g) => {
      const dg = g.createLinearGradient(0, 560, 0, BAR_Y);
      dg.addColorStop(0, 'rgba(4,10,30,0)'); dg.addColorStop(0.55, 'rgba(4,10,30,0.55)'); dg.addColorStop(1, 'rgba(4,10,30,0.88)');
      g.fillStyle = dg; g.fillRect(0, 560, W, BAR_Y - 560);
    });
    // the wipe's light, at edge 0 (it moves with the edge)
    SPR.wipe = bake(-230, 0, 460, H, (g) => {
      const gr = g.createLinearGradient(-200, 0, 200, 0);
      gr.addColorStop(0, 'rgba(25,70,200,0)'); gr.addColorStop(0.5, 'rgba(110,155,235,0.85)'); gr.addColorStop(1, 'rgba(25,70,200,0)');
      g.fillStyle = gr; g.beginPath(); g.moveTo(40, 0); g.lineTo(220, 0); g.lineTo(-40, H); g.lineTo(-220, H); g.closePath(); g.fill();
    });
  }
  function barLayers(B, t) {
    const inK = eOut3(ramp(B.t0, B.t0 + 0.45, t)), outK = sstep(B.coin2 + 0.45, B.coin2 + 0.8, t);
    const yOff = (1 - inK) * (H - BAR_Y + 10) + outK * (H - BAR_Y + 10);
    if (yOff >= H - BAR_Y) return;
    const M = LT(0, yOff);
    at(SPR.barBody, M, 1); at(SPR.barGlow, M, 1, 'lighter'); at(SPR.barMark, M, 1);
    const ts = titleState(B, t), prevTitle = ts.first ? null : ts.prev, u = t - ts.tc;
    const aNew = prevTitle ? sstep(0.08, 0.34, u) : 1, aOld = prevTitle ? 1 - sstep(0, 0.2, u) : 0;
    if (aOld > 0) titleLayers(prevTitle, 960, 1004 + yOff, aOld, 0);
    titleLayers(ts.s, 960, 1004 + yOff, aNew, prevTitle ? ramp(0.14, 0.7, u) : 0);
    at(SPR['barCoin:' + B.side], M, 1);
  }

  // ---- the big coin between the parts: its face (with its shadow) turned by a transform; its edge an ellipse of the
  // edge's gradient, clipped round
  const EDGE = (function () {   // the edge's gradient across the coin (-R..R over x 32..288), its dark ends beyond
    const c = mk(320, 4), g = c.getContext('2d');
    const eg = g.createLinearGradient(32, 0, 288, 0);
    eg.addColorStop(0, '#081536'); eg.addColorStop(0.5, '#1a3478'); eg.addColorStop(1, '#081536');
    g.fillStyle = eg; g.fillRect(0, 0, 320, 4);
    return c;
  })();
  function coinBake(side, base) {
    const key = 'coin:' + side + ':' + base;
    if (SPR[key]) return SPR[key];
    const face = coinFace(side, base), m = Math.ceil(base * 0.5);
    return (SPR[key] = bake(-face.width / 2 - m, -face.height / 2 - m, face.width + 2 * m, face.height + 2 * m, (g) => {
      shadowOn(g, base * 0.2, 0.5, base * 0.05);
      g.drawImage(face, -face.width / 2, -face.height / 2);
      shadowOff(g);
    }));
  }
  function bigCoinLayers(B, t, t0) {
    const u = t - t0;
    if (u < 0 || u > 1.5) return;
    let x, turn, R, a = 1;
    if (u < 0.55) { const k = eOut3(u / 0.55); x = lerp(2250, 1060, k); turn = lerp(1.35, 0, k); R = lerp(250, 330, k); }
    else if (u < 0.85) { x = 1060 - (u - 0.55) * 60; turn = Math.sin((u - 0.55) * 6) * 0.06; R = 330; }
    else { const k = eInOut3((u - 0.85) / 0.65); x = lerp(1042, -380, k); turn = lerp(0, -1.4, k); R = lerp(330, 380, k); a = 1 - sstep(0.75, 1, k); }
    const cy = 520, k = Math.cos(turn), sc = R / 330, thick = R * 0.07 * Math.abs(Math.sin(turn));
    if (a > 0.003) {
      if (thick > 0.5) {
        const rx = Math.max(0.5, Math.abs(k) * R) + thick * 0.5, ecx = x + Math.sign(Math.sin(turn)) * thick * 0.5;
        LK.clip('edge:' + B.side + ':' + t0, LT(ecx - rx, cy - R), 2 * rx, 2 * R, true);
        LK.put(EDGE, [2 * R / 256, 0, 0, 2 * R / 4, x - R - 32 * (2 * R / 256), cy - R], a);
        LK.endClip();
      }
      const sx = Math.max(0.02, Math.abs(k)) * (k < 0 ? -1 : 1);
      at(coinBake(B.side, 330), [sx * sc, 0, 0, sc, x, cy], a);
    }
    // a flash under the coin as the scene changes
    const fk = Math.max(0, 1 - Math.abs(u - 0.62) / 0.22);
    if (fk > 0) fillAll('rgb(40,85,200)', 0.28 * fk, 'lighter');
  }

  // ---- the parts of a team's block
  function slotLayers(s, t, outT, outMode, pass) {
    const F = buildFigure(s.side, s.p);
    const u = t - s.start;
    if (u < 0) return;
    const seed = (s.p.id % 9973) * 1.37, id = s.side + ':' + s.p.id;
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
      holoLayer(id, F, s, holo * (ou >= 0 ? 1 : 0.95), u);
      figureLayers(F, s, solidK * k, 0.88 + 0.12 * Math.sin(t * 7.3 + s.x * 0.01));
      glowLayer(F, s, glow * k + (ou >= 0 ? Math.sin(clamp(ou / 0.4) * PI) * 0.5 : 0));
    } else if (pass === 'fx') {
      const sk = u < 0.62 ? Math.sin(clamp(u / 0.62) * PI) : 0;
      sparkleLayer(id, s.x, s.ny + 0.42 * s.P, sk, 150, u * 0.6);
      particleLayer(id, s, u < 1.3 ? u : -1, seed, ou, seed + 11);
      // a beam of light at the foot while the figure forms
      const bk = Math.sin(clamp((u - 0.15) / 1.0) * PI) * (u < 1.15 ? 1 : 0);
      if (bk > 0) at(footBake(s.P), LT(s.x, BAR_Y - 6), bk, 'lighter');
    } else if (pass === 'tab') {
      const tk = eOut3(ramp(0.82, 1.12, u)) * k;
      if (tk > 0) tabLayers(id + ':s', String(s.p.n), NAMES[s.side][s.p.id], s.x, s.tabY, 58, tk, (1 - eOut3(ramp(0.82, 1.12, u))) * -70, ramp(0.95, 1.35, u));
    }
  }
  function groupLayers(B, t) {
    for (const G of B.groups) {
      if (t < G.first - 0.01 || t > G.out + 0.65) continue;
      const order = G.slots.slice().sort((a, b) => (b.back ? 1 : 0) - (a.back ? 1 : 0));
      for (const s of order) slotLayers(s, t, G.out, G.outMode, 'body');
      for (const s of G.slots) slotLayers(s, t, G.out, G.outMode, 'fx');
      for (const s of G.slots) slotLayers(s, t, G.out, G.outMode, 'tab');
      if (G.outMode === 'beam' && t >= G.out) sweepLayers((t - G.out) / 0.42, 784, 120, 1);
    }
  }
  // the subs' name tabs: the height that fits each row, worked out once
  function subsRows(B) {
    if (B.rows) return B.rows;
    B.rows = [0, 1].map((row) => {
      const rs = B.subs.filter((s) => s.row === row);
      let h = 44;
      const fits = (hh) => rs.every((s) => tabWidth(TMP_G, String(s.p.n), NAMES[s.side][s.p.id], hh) <= SUB_SP - 12);
      while (h > 30 && !fits(h)) h -= 2;
      return { rs, h };
    });
    return B.rows;
  }
  function subsLayers(B, t) {
    if (t < B.subsT - 0.2 || t > B.coin2 + 0.65) return;
    const u = t - B.subsT, ou = t - (B.coin2 + 0.5);
    const kIn = eOut3(ramp(0, 0.35, u)), kOut = ou > 0 ? 1 - sstep(0, 0.3, ou) : 1;
    const a = kIn * kOut;
    if (a <= 0) return;
    for (const row of [0, 1]) for (const s of B.subs) if (s.row === row) {
      const F = buildFigure(s.side, s.p);
      holoLayer(s.side + ':' + s.p.id + ':sub', F, s, Math.max(0, 1 - u / 0.4) * 0.9, u + 0.6);
      figureLayers(F, s, a);
    }
    // a dark rise from the bar behind the names
    at(SPR.subsShade, LK.I, a);
    // names in two staggered rows, fitted to the room
    subsRows(B).forEach(({ rs, h }, row) => rs.forEach((s, i) => {
      const tk = eOut3(ramp(0.15 + i * 0.05, 0.45 + i * 0.05, u)) * kOut;
      tabLayers(s.side + ':' + s.p.id + ':sub', String(s.p.n), NAMES[s.side][s.p.id], s.x, row === 0 ? 782 : 852, h, tk, 0, ramp(0.3 + i * 0.05, 0.8 + i * 0.05, u));
    }));
    if (u < 0.6) sparkleLayer('subs', 960, 600, Math.sin(clamp(u / 0.6) * PI) * 0.5, 420, 0);
  }
  // the board's busts, each at the size it lands at, with its shadow (the fallback's lines of light on their own)
  function bustBake(b) {
    if (b.sprite) return b.sprite;
    const F = buildFigure(b.side, b.p);
    const [sx, sy, zc] = b.spot;
    const bh = 128 * Math.pow(76 / zc, 0.62), Pb = bh / 1.66, ny = sy - 0.62 * Pb;
    let c, add = null;
    if (F.kind === 'photo') {
      const hb = bh * 1.12, wb = hb * (F.bust.width / F.bust.height);
      c = bake(Math.floor(sx - wb / 2) - 40, Math.floor(sy + bh * 0.12 - hb) - 40, wb + 82, hb + 82, (g) => {
        shadowOn(g, 16, 0.45, 5); g.drawImage(F.bust, sx - wb / 2, sy + bh * 0.12 - hb, wb, hb); shadowOff(g);
      });
    } else {
      const s = Pb / F.P, srcH = F.oy + 0.66 * F.P, X = sx - F.ox * s, Y = ny - F.oy * s, w = F.c.width * s, hh = srcH * s;
      c = bake(Math.floor(X) - 40, Math.floor(Y) - 40, w + 82, hh + 82, (g) => {
        shadowOn(g, 16, 0.45, 5); g.drawImage(F.c, 0, 0, F.c.width, srcH, X, Y, w, hh); shadowOff(g);
      });
      add = bake(Math.floor(X) - 1, Math.floor(Y) - 1, w + 3, hh + 3, (g) => g.drawImage(F.add, 0, 0, F.c.width, srcH, X, Y, w, hh));
    }
    return (b.sprite = { c, add, bh });
  }
  const BANDS = {};
  function boardLayers(B, t) {
    if (t < B.boardT - 0.01 || t > B.boardEnd + 0.6) return;
    const u = t - B.boardT, ou = t - B.boardEnd;
    const kIn = eOut3(ramp(0, 0.4, u)), kOut = ou > 0 ? 1 - sstep(0.05, 0.45, ou) : 1;
    const a = kIn * kOut;
    // dim the ground a little under the board
    fillAll('#030714', 0.32 * a);
    const lift = (1 - kIn) * 60;
    at(SPR.board, LT(0, lift), a);
    for (const b of B.board) {
      const pu = t - b.pop;
      if (pu < 0) continue;
      const [sx, sy, zc] = b.spot;
      const pk = eOutBack(clamp(pu / 0.3), 1.4) * kOut;
      if (pk <= 0) continue;
      const bs = bustBake(b), scale = 0.6 + 0.4 * Math.min(1, pk);
      const M = [scale, 0, 0, scale, sx - sx * scale, sy - sy * scale];
      at(bs.c, M, Math.min(1, pk) * a);
      if (bs.add) at(bs.add, M, Math.min(1, pk) * a, 'lighter');
      if (pu < 0.4) sparkleLayer('b:' + b.side + ':' + b.p.id, sx, sy - bs.bh * 0.4, Math.sin(clamp(pu / 0.4) * PI) * 0.7 * a, 60, 0);
      const th = Math.max(22, 34 * Math.pow(76 / zc, 0.5));
      tabLayers(b.side + ':' + b.p.id + ':b', String(b.p.n), NAMES[b.side][b.p.id], sx, sy + th * 0.42, th, eOut3(clamp((pu - 0.08) / 0.25)) * a, 0, 0);
    }
    at(BANDS[B.side] || (BANDS[B.side] = bake(0, 950, W, H - 950, (g) => drawBand(g, B.side, 1))), LK.I, a);
    if (ou > 0 && ou < 0.6) sweepLayers(ou / 0.5, 560, 260, 1);
  }
  function hintLayers(t) {
    const a = sstep(0.4, 0.9, t) * (1 - sstep(6.5, 7.2, t));
    if (a > 0) at(SPR.hint, LK.I, a);
  }

  function drawFrame(t) {
    LK.begin();
    // the ground: one still, slowly drifting
    const kk = t / DUR, z = 1.03 + 0.04 * kk, dx = lerp(-16, 16, kk);
    LK.put(BGC, [z, 0, 0, z, (W - W * z) / 2 + dx, (H - H * z) / 2 + 8]);
    if (SPR.credit) at(SPR.credit, LK.I, 1);
    for (const B of BLOCKS) {
      if (t < B.t0 - 0.01 || t > B.tEnd + 0.05) continue;
      subsLayers(B, t);
      groupLayers(B, t);
      boardLayers(B, t);
      barLayers(B, t);
      bigCoinLayers(B, t, B.coin1);
      bigCoinLayers(B, t, B.coin2);
    }
    hintLayers(t);
    // back to the channel: a slanted wipe uncovers it, a band of light on its edge
    if (t >= END_T) {
      const u = t - END_T, k = eInOut3(clamp(u / 0.7));
      const edge = lerp(-260, W + 260, k);
      LK.wipe(edge, 140);
      if (k < 1) at(SPR.wipe, LT(edge, 0), 1, 'lighter', true);
    } else LK.wipe(null);
    // the first instant comes up from the channel's picture; Back fades it all away to the channel at once
    let f = t < 0.35 ? Math.max(0, t / 0.35) : 1;
    if (window.FADE_FROM != null) f *= 1 - clamp((t - window.FADE_FROM) / 0.5);
    LK.fade(f);
    LK.end();
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
    // the ground: the stadium's own photograph when the app sends one (cfg.ground), graded for the sun at kick-off;
    // else drawn here, lit for the place and the time. Either way the wide shot behind the players, softened a touch.
    let photo = null;
    if (window.ASSET_URLS.ground) {
      try { const im = new Image(); im.src = window.ASSET_URLS.ground; await im.decode(); if (im.width > 0) photo = im; } catch (e) { /* the drawn ground */ }
    }
    BGC = mk(W, H);
    const bgc = BGC.getContext('2d');
    if (photo) {
      const gp = groundPhoto(photo);
      bgc.filter = 'blur(1px)'; bgc.drawImage(gp, 0, 0); bgc.filter = 'none';
      if (CFG.rain) rainStreaks(bgc, 12);
      SPR.credit = groundCredit(window.GROUND_CREDIT, W - 28, 44, 'right');
    } else {
      GL = groundLight();
      buildCrowd();
      buildSky();
      const sc = mk(W, H), sg = sc.getContext('2d');
      renderScene(sg, beautyCam(12), 12);
      grade(sg); bloomOn(sg, sc);
      bgc.filter = 'blur(1.6px) saturate(1.05)'; bgc.drawImage(sc, 0, 0); bgc.filter = 'none';
    }
    window.GROUND_USED = !!photo;
    buildBoard();
    SPR.board = cropTo(BOARD);
    for (const side of ['home', 'away']) for (const p of DATA[side].xi.concat(DATA[side].subs)) buildFigure(side, p);
    for (const B of BLOCKS) for (const T of B.titles) chromeTitle(T.s);
    // every part drawn now, once: the frames only place them
    LK.mount();
    bakeBar();
    for (const B of BLOCKS) {
      for (const G of B.groups) for (const s of G.slots) tabBake(String(s.p.n), NAMES[s.side][s.p.id], s.x, s.tabY, 58);
      subsRows(B).forEach(({ rs, h }, row) => rs.forEach((s) => tabBake(String(s.p.n), NAMES[s.side][s.p.id], s.x, row === 0 ? 782 : 852, h)));
      for (const b of B.board) {
        bustBake(b);
        const [sx, sy, zc] = b.spot, th = Math.max(22, 34 * Math.pow(76 / zc, 0.5));
        tabBake(String(b.p.n), NAMES[b.side][b.p.id], sx, sy + th * 0.42, th);
      }
      coinBake(B.side, 330);
      BANDS[B.side] = bake(0, 950, W, H - 950, (g) => drawBand(g, B.side, 1));
    }
    window.CUES = buildCues();
    window.SECTIONS = SECTIONS;
    window.DUR = DUR;
    window.SOL = SOL;
    window.BLOCKS_INFO = BLOCKS.map((B) => ({ side: B.side, t0: B.t0, groups: B.groups.map((G) => ({ key: G.key, first: G.first, out: G.out, solids: G.slots.map((s) => +s.solid.toFixed(2)) })), glint: B.glint, coin1: B.coin1, subs: B.subsT, coin2: B.coin2, board: B.boardT, boardEnd: B.boardEnd }));
    settle(drawFrame, DUR);
    window.INIT_MS = performance.now() - t0;
    window.drawFrame = drawFrame;
    window.READY = true;
    if (window.onBroadcastReady) window.onBroadcastReady();
  }
  // a full-screen canvas cut to what is drawn in it (a sprite placed where it was)
  function cropTo(c) {
    const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
    let x0 = c.width, y0 = c.height, x1 = -1, y1 = -1;
    for (let y = 0; y < c.height; y++) for (let x = 0; x < c.width; x++) if (d[(y * c.width + x) * 4 + 3] > 0) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
    if (x1 < 0) return null;
    const o = mk(x1 - x0 + 1, y1 - y0 + 1); o.getContext('2d').drawImage(c, -x0, -y0);
    return { c: o, x: x0, y: y0 };
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
