// The kick-off strap in the scoreboard's own look (the first design, scratchpad kickoff-intro/intro.html), live: drawn
// as page elements over a see-through stage (the mock-up was built that way: CSS moves and clips, no canvas). The strap
// comes up over the channel's own lower band, holds about four seconds, then folds into the look's board where the
// app's real board sits (window.STRAP_CFG.board, 1920 x 1080), holds it a moment and fades: the app's own board then
// shows. For a restart or the second half the board is up first, steps aside as the strap comes in, and the strap folds
// back into it. The mock-up's own times are kept (T): the page's second t is the mock-up's t + LEAD.
(function () {
  const C = window.STRAP_CFG;
  const LEAD = 0.20;                                   // the page starts 0.2 s into the mock-up's timeline
  const $ = (id) => document.getElementById(id);
  const up = (s) => String(s || '').toUpperCase();
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const hexOk = (h, d) => (/^#[0-9a-f]{6}$/i.test(h || '') ? h : d);

  // ---------- the match and the moment ----------
  // the look (one this page does not know is drawn as Classic: init)
  let STYLE = String(C.look || 'FROSTED').toLowerCase();
  const MOMENT = C.kind === 'restart' || C.kind === 'second' ? C.kind : 'kickoff';
  const M = {
    home: esc(up(C.home.name)), away: esc(up(C.away.name)),
    hs: esc(up(C.home.short || C.home.name).slice(0, 3)), as: esc(up(C.away.short || C.away.name).slice(0, 3)),
    hc: hexOk(C.home.color, '#C8102E'), ac: hexOk(C.away.color, '#1B3F8F'),
    hcrest: C.home.crest || '', acrest: C.away.crest || '', compW: C.compLogo || '',
    // the competition's logo for light grounds, and as the look's board cuts it (its emblem: the app cuts it, as CompBug does)
    compL: C.compLight || '', compM: C.compMark || '',
    ground: esc(up(C.stadium)),
  };
  const score = MOMENT === 'kickoff' ? ['0', '0'] : [String((C.score || [0, 0])[0] | 0), String((C.score || [0, 0])[1] | 0)];
  const weather = esc(up(C.weather || ''));
  const scorer = esc(up([C.scorer, C.goalMin].filter(Boolean).join(' ')));
  const live = M.ground ? 'LIVE FROM ' + M.ground : '';
  const join = (...a) => a.filter(Boolean).join('  ·  ');
  const MO = {
    kickoff: { label: 'KICK-OFF', sub: join(live, weather), extra: weather },
    restart: { label: 'RESTART', sub: join(scorer, live), extra: scorer },
    second: { label: 'SECOND HALF', sub: join(live, weather), extra: weather },
  }[MOMENT];
  MO.score = score;
  // the match clock, running from the page's start: the app's own reading if it gave one, else from the moment
  const minute = parseInt(String(C.goalMin || '').replace(/[^0-9]/g, ''), 10);
  const CLOCK_AT0 = C.clock != null && isFinite(+C.clock) ? +C.clock
    : MOMENT === 'second' ? 45 * 60 : MOMENT === 'restart' ? (isFinite(minute) ? Math.max(0, minute - 1) * 60 + 50 : 0) : 0;

  // ---------- the timeline (the mock-up's seconds, T): in at 0.5, the fold 4.5-5.15, the board, then gone ----------
  const IN0 = 0.50, IN1 = 1.25, SUB0 = 1.20, SUB1 = 1.65, OUT0 = 4.50, OUT1 = 5.15;
  const END_T = OUT1 + 1.40;                          // the board held, its sheen past
  const FADE = 0.25;
  window.STRAP_LEN = END_T + FADE - LEAD;             // in the page's own seconds (about 6.6)
  const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
  const prog = (t, a, b) => clamp((t - a) / (b - a));
  const eo = (x) => 1 - Math.pow(1 - x, 3);
  const ei = (x) => x * x * x;
  const eio = (x) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
  const back = (x) => { const c1 = 1.5, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); };
  const lerp = (a, b, x) => a + (b - a) * x;
  const pad = (n) => String(n).padStart(2, '0');
  const clockText = (T) => { const s = Math.max(0, Math.floor(CLOCK_AT0 + Math.max(0, T - LEAD))); return pad(Math.floor(s / 60)) + ':' + pad(s % 60); };

  // Homefield's mark (the house with the play cut through it), in its icon's 108 units.
  let markN = 0;
  function markSvg(px, house, cut) {
    const id = 'mg' + (markN++);
    const fill = house === 'grad' ? 'url(#' + id + ')' : house;
    return '<svg class="mark" width="' + px + '" height="' + px + '" viewBox="26 26 56 54"><defs><linearGradient id="' + id +
      '" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6D28F2"/><stop offset="1" stop-color="#FF6B6B"/></linearGradient></defs>' +
      '<path d="M31 75 L31 50 L54 31 L77 50 L77 75 Z" fill="' + fill + '" stroke="' + fill + '" stroke-width="5" stroke-linejoin="round"/>' +
      '<path d="M49 53 L49 69 L63 61 Z" fill="' + cut + '"/></svg>';
  }
  // a crest, or a plain coin in the club's colour when it will not come
  function crest(home, px, style) {
    const src = home ? M.hcrest : M.acrest, col = home ? M.hc : M.ac;
    if (src) return '<img class="crest" style="width:' + px + 'px;height:' + px + 'px;' + (style || '') + '" src="' + src + '">';
    return '<i class="crest coin" style="width:' + Math.round(px * 0.86) + 'px;height:' + Math.round(px * 0.86) + 'px;' + (style || '') +
      'background:radial-gradient(circle at 35% 30%,rgba(255,255,255,.35),rgba(255,255,255,0) 60%),' + col + '"></i>';
  }
  // the competition's logo (white on the dark blocks), left out when there is none
  const comp = (px) => (M.compW ? '<img style="width:' + px + 'px" src="' + M.compW + '">' : '');

  // ---------- shared pieces ----------
  const S = {};
  const W = 1300, H = 124, X = (1920 - W) / 2, Y = 806;   // the strap, over the channel's own lower band
  const BH = 54;                                       // the block boards' own height before they are sized to the app's
  const R = C.board || {};
  const RECT = { x: +R.x || 64, y: +R.y || 46, w: +R.w || 380, h: +R.h || 49 };
  let BXd = RECT.x, BYd = RECT.y, BS = 1;              // where the drawn board sits, and its size against its own
  const SHEEN = '<i class="bsheen"></i>';
  let sheenAt = OUT1 + 0.05;

  function subHTML(bg, ink, markHouse, markCut, extra) {
    return '<div class="sub row" style="height:44px;padding:0 22px;gap:14px;border-radius:0 0 10px 10px;font-size:18px;background:' + bg + ';color:' + ink + (extra || '') + '">' +
      markSvg(26, markHouse, markCut) + '<span style="opacity:.9">WHITEBROOK ROAD SPORTS</span>' +
      (MO.sub ? '<span style="opacity:.45">|</span><span>' + MO.sub + '</span>' : '') + '</div>';
  }
  function subRender(t, left, width) {
    const sl = $('subline'); if (!sl.firstElementChild) return;
    sl.style.left = left + 'px'; sl.style.top = (Y + H) + 'px'; sl.style.height = '50px'; sl.style.overflow = 'hidden';
    if (width) sl.style.width = width + 'px';
    const pin = eo(prog(t, SUB0, SUB1)), pout = eio(prog(t, OUT0 - 0.34, OUT0 - 0.04));
    sl.firstElementChild.style.transform = 'translateY(' + (-56 * (1 - pin) - 56 * pout) + 'px)';
    sl.style.opacity = pin <= 0 || pout >= 1 ? 0 : 1;
  }
  // A block board: [comp][strip crest code][score][code crest strip][clock]
  function blockBoard(o) {
    const h = BH, cr = Math.round(h * 0.66);
    const side = (home) => '<div class="row" style="height:100%;gap:' + Math.round(h * 0.16) + 'px;padding:0 ' + Math.round(h * 0.2) + 'px;' + (o.sideBg ? 'background:' + (home ? o.sideBg[0] : o.sideBg[1]) : '') + '">' +
      (home && o.strips ? '<i style="width:' + Math.round(h * 0.1) + 'px;height:' + Math.round(h * 0.62) + 'px;border-radius:9px;background:' + M.hc + '"></i>' : '') +
      (home ? crest(true, cr) : '') +
      '<span class="nm" style="font-size:' + Math.round(h * 0.46) + 'px;color:' + o.ink + '">' + (home ? M.hs : M.as) + '</span>' +
      (!home ? crest(false, cr) : '') +
      (!home && o.strips ? '<i style="width:' + Math.round(h * 0.1) + 'px;height:' + Math.round(h * 0.62) + 'px;border-radius:9px;background:' + M.ac + '"></i>' : '') + '</div>';
    return '<div class="row" style="height:' + h + 'px;background:' + o.bg + '">' +
      '<div style="width:' + h + 'px;height:100%;display:grid;place-items:center;background:' + o.comp + '">' + comp(Math.round(h * 0.7)) + '</div>' +
      side(true) +
      '<div class="row num" style="height:100%;padding:0 ' + Math.round(h * 0.36) + 'px;font-size:' + Math.round(h * 0.56) + 'px;color:' + (o.scoreInk || '#fff') + ';background:' + o.score + '">' + MO.score[0] + '–' + MO.score[1] + '</div>' +
      side(false) +
      '<div class="row num bclock" style="height:100%;padding:0 ' + Math.round(h * 0.34) + 'px;font-size:' + Math.round(h * 0.44) + 'px;color:' + (o.clockInk || '#E6E9F2') + ';background:' + o.clock + '">00:00</div></div>';
  }
  // the drawn board where the app's sits: its height matched, left-aligned (right-aligned for a board on the right); a
  // board the family made wider than its words need (Move and resize's Width) is as wide here, its blocks spread along
  // it as the app's fills that box, so the strap folds into the place and the size they set
  function placeBoard(radius) {
    const b = $('board'), el = b.firstElementChild;
    let nw = el.offsetWidth || 400;
    const nh = el.offsetHeight || BH;
    BS = RECT.h / nh;
    const want = C.board ? RECT.w / BS : 0;
    if (want > nw * 1.03) {
      el.style.width = want + 'px';
      el.style.justifyContent = 'space-between';
      nw = el.offsetWidth || want;
    }
    BXd = RECT.x + RECT.w / 2 > 960 ? RECT.x + RECT.w - nw * BS : RECT.x;
    BYd = RECT.y;
    b.style.left = BXd + 'px'; b.style.top = BYd + 'px'; b.style.borderRadius = radius + 'px';
    b.style.transformOrigin = '0 0'; b.style.transform = 'scale(' + BS + ')';
  }
  const bw0 = () => $('board').firstElementChild.offsetWidth;   // the drawn board's own width, before sizing
  function openBoard(t, t0, t1) {
    const b = $('board'), el = b.firstElementChild, p = prog(t, t0, t1);
    if (p <= 0) { b.style.opacity = 0; return; }
    b.style.opacity = 1;
    const w = el.offsetWidth, first = el.firstElementChild.offsetWidth, r = eo(p), shown = first + (w - first) * r;
    b.style.clipPath = 'inset(0 ' + (w - shown) + 'px 0 0)';
    const e = $('bedge'); if (e) { e.style.left = (shown - 3) + 'px'; e.style.opacity = r > 0 && r < 1 ? 1 : 0; }
  }
  function landOn(t, st, sw, sh, sx, sy, inOp) {
    const fly = eio(prog(t, OUT0, OUT1));
    const b = $('board').firstElementChild, bw = b.offsetWidth * BS, bh = b.offsetHeight * BS;
    const s = Math.min(bw / sw, bh / sh);
    const tx = BXd + (bw - sw * s) / 2 - sx, ty = BYd + (bh - sh * s) / 2 - sy;
    st.style.transformOrigin = '0 0';
    st.style.transform = fly > 0 ? 'translate(' + tx * fly + 'px,' + ty * fly + 'px) scale(' + lerp(1, s, fly) + ')' : 'none';
    st.style.opacity = inOp * (1 - prog(t, OUT1 - 0.12, OUT1 + 0.02));
    $('board').style.opacity = prog(t, OUT1 - 0.14, OUT1); $('board').style.clipPath = 'none';
  }
  function boardSheen(t) {
    const s = document.querySelector('#board .bsheen'); if (!s) return;
    const bw = bw0(), p = prog(t, sheenAt, sheenAt + 0.6);
    s.style.left = lerp(-150, bw + 60, eio(p)) + 'px'; s.style.opacity = p > 0 && p < 1 ? 1 : 0;
  }

  // -- Classic: navy, the score on blue -----------------------------------------------------------------------------------
  S.classic = {
    build() {
      $('strap').innerHTML = '<div class="row" style="width:' + W + 'px;height:' + H + 'px;background:#0B1640;border-radius:12px 12px 0 0;overflow:hidden;position:relative">' +
        '<div style="width:' + H + 'px;height:100%;flex:none;display:grid;place-items:center;background:#070C26">' + comp(86) + '</div>' +
        '<div class="row fitrow" style="flex:1;height:100%;gap:20px;padding-right:24px;min-width:0"><i style="width:10px;height:100%;flex:none;background:' + M.hc + '"></i>' + crest(true, 84) + '<span class="nm" style="font-size:44px">' + M.home + '</span></div>' +
        '<div style="width:236px;height:100%;flex:none;display:flex;flex-direction:column;align-items:center;justify-content:center;background:linear-gradient(#2443B4,#142A78)">' +
        '<span class="lab" style="font-size:16px;color:#c9d3ff">' + MO.label + '</span><span class="num" style="font-size:62px;line-height:1">' + MO.score[0] + ' – ' + MO.score[1] + '</span></div>' +
        '<div class="row fitrow" style="flex:1;height:100%;gap:20px;justify-content:flex-end;padding-left:24px;min-width:0"><span class="nm" style="font-size:44px">' + M.away + '</span>' + crest(false, 84) + '<i style="width:10px;height:100%;flex:none;background:' + M.ac + '"></i></div>' +
        '<div class="row num sslot" style="width:150px;height:100%;flex:none;justify-content:center;font-size:34px;background:#050816">00:00</div>' +
        '<i id="sedge" style="position:absolute;top:0;bottom:0;width:4px;background:#fff;box-shadow:0 0 18px 4px rgba(255,255,255,.7)"></i>' +
        '<i id="ssheen" style="position:absolute;top:0;bottom:0;width:180px;background:linear-gradient(90deg,rgba(255,255,255,0),rgba(255,255,255,.16),rgba(255,255,255,0));transform:skewX(-20deg)"></i></div>';
      $('subline').innerHTML = subHTML('#070C26', '#E6E9F2', 'grad', '#070C26');
      $('board').innerHTML = blockBoard({ bg: '#0B1640', comp: '#070C26', score: 'linear-gradient(#2443B4,#142A78)', clock: '#050816', ink: '#E6E9F2', strips: true }) +
        '<i id="bedge" style="position:absolute;top:0;bottom:0;width:3px;background:#fff;box-shadow:0 0 12px 3px rgba(255,255,255,.7)"></i>' + SHEEN;
      placeBoard(8);
      sheenAt = OUT1 + 0.5;
    },
    render(t) {
      const st = $('strap'); st.style.left = X + 'px'; st.style.top = Y + 'px';
      const first = eo(prog(t, IN0, IN0 + 0.22));
      let rest = eo(prog(t, IN0 + 0.2, IN1));
      if (t > OUT0) rest = 1 - eio(prog(t, OUT0, OUT0 + 0.3));
      const fly = eio(prog(t, OUT0 + 0.26, OUT1));
      const half = H / 2 * (1 - first), shown = H + (W - H) * rest;
      st.style.clipPath = 'inset(' + half + 'px ' + (W - shown) + 'px ' + half + 'px 0)';
      st.style.opacity = t < IN0 ? 0 : 1 - prog(t, OUT1 - 0.04, OUT1);
      $('sedge').style.left = (shown - 4) + 'px'; $('sedge').style.opacity = rest > 0.002 && rest < 0.998 ? 1 : 0;
      const sp = prog(t, 1.7, 2.5); $('ssheen').style.left = lerp(-260, W + 80, sp) + 'px'; $('ssheen').style.opacity = sp > 0 && sp < 1 ? 1 : 0;
      st.style.transformOrigin = '0 0';
      st.style.transform = fly > 0 ? 'translate(' + (BXd - X) * fly + 'px,' + (BYd - Y) * fly + 'px) scale(' + lerp(1, BH * BS / H, fly) + ')' : 'none';
      subRender(t, X + 40);
      openBoard(t, OUT1 - 0.04, OUT1 + 0.5);
    },
  };

  // -- A custom board (the family's own, the apps' BoardSpec): Classic's strap and board in its colours, corners,
  // border, finish and lettering, folding into it as Classic's does --------------------------------------------------------
  const CU = C.custom && typeof C.custom === 'object' ? C.custom : null;
  const cuCol = (v, d) => (typeof v === 'string' && /^(#[0-9a-f]{6}|rgba\([0-9., ]+\))$/i.test(v) ? v : d);
  S['custom'] = {   // keyed apart: the family's own board, not one of the boards' named looks (StrapLooksTest counts those)
    fonts: CU && CU.font ? [(CU.weight || 800) + " 40px '" + CU.font + "'"] : [],
    build() {
      const c = CU || {};
      const panel = cuCol(c.panel, '#0B1640'), foot = cuCol(c.panelFoot, panel), compBg = cuCol(c.comp, '#070C26');
      const score = 'linear-gradient(' + cuCol(c.score, '#2443B4') + ',' + cuCol(c.scoreFoot, '#142A78') + ')';
      const clock = cuCol(c.clock, '#050816'), ink = cuCol(c.ink, '#E6E9F2'), sInk = cuCol(c.scoreInk, '#FFFFFF');
      const cInk = cuCol(c.clockInk, '#E6E9F2'), dim = cuCol(c.dim, '#c9d3ff'), accent = cuCol(c.accent, '#F5C518');
      const rad = Math.max(0, Math.min(0.5, +c.radius || 0));
      const bw = +c.border > 0 ? Math.max(1, Math.round(H * Math.min(0.1, +c.border))) : 0;
      const ring = bw ? 'box-shadow:inset 0 0 0 ' + bw + 'px ' + cuCol(c.borderColour, 'rgba(255,255,255,.55)') + ';' : '';
      const face = c.font ? "font-family:'" + String(c.font).replace(/[^A-Za-z ]/g, '') + "',Outfit,sans-serif;" : '';
      const sideBg = (home) => (c.club ? 'background:' + (home ? M.hc : M.ac) + ';' : '');
      const strip = (home) => (c.strips ? '<i style="width:10px;height:100%;flex:none;background:' + (home ? M.hc : M.ac) + '"></i>' : '');
      const line = c.accentLine ? '<i style="position:absolute;left:0;right:0;bottom:0;height:6px;background:' + accent + '"></i>' : '';
      const gloss = c.sheen ? '<i style="position:absolute;inset:0 0 52% 0;background:linear-gradient(rgba(255,255,255,.18),rgba(255,255,255,0))"></i>' : '';
      const r = Math.round(H * rad * 0.5);
      $('strap').innerHTML = '<div class="row" style="width:' + W + 'px;height:' + H + 'px;background:linear-gradient(' + panel + ',' + foot + ');' + face + 'color:' + ink + ';border-radius:' + r + 'px ' + r + 'px 0 0;overflow:hidden;position:relative;' + ring + '">' + gloss +
        '<div style="width:' + H + 'px;height:100%;flex:none;display:grid;place-items:center;background:' + compBg + '">' + comp(86) + '</div>' +
        '<div class="row fitrow" style="flex:1;height:100%;gap:20px;padding-right:24px;min-width:0;' + sideBg(true) + '">' + strip(true) + crest(true, 84) + '<span class="nm" style="font-size:44px">' + M.home + '</span></div>' +
        '<div style="width:236px;height:100%;flex:none;display:flex;flex-direction:column;align-items:center;justify-content:center;background:' + score + ';color:' + sInk + '">' +
        '<span class="lab" style="font-size:16px;color:' + dim + '">' + MO.label + '</span><span class="num" style="font-size:62px;line-height:1">' + MO.score[0] + ' – ' + MO.score[1] + '</span></div>' +
        '<div class="row fitrow" style="flex:1;height:100%;gap:20px;justify-content:flex-end;padding-left:24px;min-width:0;' + sideBg(false) + '"><span class="nm" style="font-size:44px">' + M.away + '</span>' + crest(false, 84) + strip(false) + '</div>' +
        '<div class="row num sslot" style="width:150px;height:100%;flex:none;justify-content:center;font-size:34px;background:' + clock + ';color:' + cInk + '">00:00</div>' + line +
        '<i id="sedge" style="position:absolute;top:0;bottom:0;width:4px;background:#fff;box-shadow:0 0 18px 4px rgba(255,255,255,.7)"></i>' +
        '<i id="ssheen" style="position:absolute;top:0;bottom:0;width:180px;background:linear-gradient(90deg,rgba(255,255,255,0),rgba(255,255,255,.16),rgba(255,255,255,0));transform:skewX(-20deg)"></i></div>';
      $('subline').innerHTML = subHTML(compBg, ink, 'grad', compBg);
      $('board').innerHTML = blockBoard({ bg: 'linear-gradient(' + panel + ',' + foot + ')', comp: compBg, score: score, clock: clock, ink: ink, scoreInk: sInk, clockInk: cInk,
        strips: !!c.strips, sideBg: c.club ? [M.hc, M.ac] : null }) +
        '<i id="bedge" style="position:absolute;top:0;bottom:0;width:3px;background:#fff;box-shadow:0 0 12px 3px rgba(255,255,255,.7)"></i>' + SHEEN;
      if (face) { $('board').style.cssText += ';' + face; }
      if (bw) $('board').firstElementChild.style.boxShadow = 'inset 0 0 0 ' + Math.max(1, Math.round(BH * Math.min(0.1, +c.border))) + 'px ' + cuCol(c.borderColour, 'rgba(255,255,255,.55)');
      placeBoard(Math.round(BH * rad));
      sheenAt = OUT1 + 0.5;
    },
    render(t) { S.classic.render(t); },
  };

  // -- Glass: smoked glass, a pill that becomes the board ------------------------------------------------------------------
  S.glass = {
    build() {
      $('strap').innerHTML = '<div id="gp" style="position:relative;overflow:hidden;background:rgba(21,27,54,.9);border:1.5px solid rgba(255,255,255,.2);box-shadow:0 20px 50px -20px rgba(0,0,0,.6)">' +
        '<div id="gin" class="row" style="position:absolute;left:0;top:0;width:' + W + 'px;height:' + H + 'px;padding:0 26px 0 30px;box-sizing:border-box">' +
        comp(76) +
        '<div class="row fitrow" style="flex:1;gap:18px;justify-content:flex-end;padding-right:22px;min-width:0"><span class="nm" style="font-size:46px">' + M.home + '</span><div style="width:96px;height:96px;flex:none;border-radius:50%;background:rgba(255,255,255,.1);display:grid;place-items:center">' + crest(true, 72) + '</div></div>' +
        '<div style="padding:10px 34px;border-radius:44px;background:rgba(255,255,255,.22);display:flex;flex-direction:column;align-items:center;flex:none"><span class="lab" style="font-size:15px;color:#d6dcf3">' + MO.label + '</span><span class="num" style="font-size:58px;line-height:1">' + MO.score[0] + ' – ' + MO.score[1] + '</span></div>' +
        '<div class="row fitrow" style="flex:1;gap:18px;padding-left:22px;min-width:0"><div style="width:96px;height:96px;flex:none;border-radius:50%;background:rgba(255,255,255,.1);display:grid;place-items:center">' + crest(false, 72) + '</div><span class="nm" style="font-size:46px">' + M.away + '</span></div>' +
        '<div class="row num sslot" style="padding:14px 22px;border-radius:40px;background:rgba(0,0,0,.3);font-size:30px;flex:none">00:00</div></div>' +
        '<i style="position:absolute;inset:0 0 50% 0;background:linear-gradient(rgba(255,255,255,.2),rgba(255,255,255,0))"></i>' +
        '<i id="gsweep" style="position:absolute;top:-20%;height:140%;width:220px;background:linear-gradient(90deg,rgba(255,255,255,0),rgba(255,255,255,.32),rgba(255,255,255,0));transform:skewX(-24deg)"></i></div>';
      $('subline').innerHTML = subHTML('rgba(21,27,54,.84)', '#E6E9F2', 'grad', '#151B36', ';border-radius:0 0 22px 22px;justify-content:center');
      $('board').innerHTML = '<div class="row" style="height:' + BH + 'px;padding:0 6px 0 10px;gap:10px;border-radius:27px;background:rgba(21,27,54,.95);border:1.5px solid rgba(255,255,255,.2);position:relative;overflow:hidden;box-sizing:border-box">' +
        '<i style="position:absolute;inset:0 0 50% 0;background:linear-gradient(rgba(255,255,255,.2),rgba(255,255,255,0))"></i>' +
        crest(true, 34) + '<span class="nm" style="font-size:25px">' + M.hs + '</span>' +
        '<span class="num" style="font-size:28px;padding:4px 14px;border-radius:20px;background:rgba(255,255,255,.22)">' + MO.score[0] + '–' + MO.score[1] + '</span>' +
        '<span class="nm" style="font-size:25px">' + M.as + '</span>' + crest(false, 34) +
        '<span class="num bclock" style="font-size:24px;padding:6px 14px;border-radius:20px;background:rgba(0,0,0,.3)">00:00</span></div>' + SHEEN;
      placeBoard(27);
    },
    render(t) {
      const st = $('strap'), gp = $('gp');
      const pin = eo(prog(t, IN0, IN1)), morph = eio(prog(t, OUT0, OUT1));
      const bw = (bw0() || 470) * BS;
      const x = lerp(X, BXd, morph), y = lerp(Y, BYd, morph), w = lerp(W, bw, morph), h = lerp(H, BH * BS, morph);
      st.style.left = x + 'px'; st.style.top = y + 'px';
      gp.style.width = w + 'px'; gp.style.height = h + 'px'; gp.style.borderRadius = (h / 2) + 'px';
      st.style.opacity = t < IN0 ? 0 : (t > OUT1 ? 0 : 1) * pin;
      st.style.transformOrigin = '50% 50%'; st.style.transform = 'scale(' + lerp(0.9, 1, pin) + ')';
      st.style.filter = pin < 1 ? 'blur(' + (16 * (1 - pin)) + 'px)' : 'none';
      $('gin').style.opacity = 1 - prog(t, OUT0, OUT0 + 0.25);
      const sw = prog(t, IN0 + 0.48, IN0 + 1.05); $('gsweep').style.left = lerp(-300, W + 100, sw) + 'px';
      subRender(t, X + 120, W - 240);
      $('board').style.opacity = prog(t, OUT1 - 0.22, OUT1); $('board').style.clipPath = 'none';
    },
  };

  // -- Light: soft grey, navy words; it rises in, then flies whole to the corner -------------------------------------------
  S.light = {
    build() {
      $('strap').innerHTML = '<div id="lp" class="row" style="width:' + W + 'px;height:' + H + 'px;background:linear-gradient(#EEF1F6,#DCE1EA);border-radius:12px 12px 0 0;overflow:hidden;color:#0B1640;box-shadow:0 18px 40px -18px rgba(0,0,0,.6);position:relative">' +
        '<div style="width:' + H + 'px;height:100%;flex:none;display:grid;place-items:center;background:#0B1640">' + comp(86) + '</div>' +
        '<div class="row fitrow" style="flex:1;height:100%;gap:20px;padding:0 24px;min-width:0">' + crest(true, 84) + '<span class="nm" style="font-size:44px">' + M.home + '</span></div>' +
        '<div style="width:236px;height:100%;flex:none;display:flex;flex-direction:column;align-items:center;justify-content:center;background:#0B1640;color:#fff"><span class="lab" style="font-size:16px;color:#aab6e6">' + MO.label + '</span><span class="num" style="font-size:62px;line-height:1">' + MO.score[0] + ' – ' + MO.score[1] + '</span></div>' +
        '<div class="row fitrow" style="flex:1;height:100%;gap:20px;justify-content:flex-end;padding:0 24px;min-width:0"><span class="nm" style="font-size:44px">' + M.away + '</span>' + crest(false, 84) + '</div>' +
        '<div class="row num sslot" style="width:140px;height:100%;flex:none;justify-content:center;font-size:34px;background:#D0D6E2">00:00</div>' +
        '<i id="lline" style="position:absolute;left:0;bottom:0;height:6px;background:linear-gradient(90deg,' + M.hc + ',#0B1640 50%,' + M.ac + ')"></i></div>';
      $('subline').innerHTML = subHTML('#0B1640', '#fff', 'grad', '#0B1640');
      $('board').innerHTML = blockBoard({ bg: '#E3E7EE', comp: '#0B1640', score: '#0B1640', clock: '#D0D6E2', ink: '#0B1640', clockInk: '#0B1640', strips: true }) + SHEEN;
      placeBoard(8);
    },
    render(t) {
      const st = $('strap'); st.style.left = X + 'px'; st.style.top = Y + 'px';
      const p = prog(t, IN0, IN0 + 0.6);
      $('lp').style.transform = 'translateY(' + (150 * (1 - back(p))) + 'px)';
      $('lline').style.width = (W * eo(prog(t, IN0 + 0.5, IN0 + 1))) + 'px';
      landOn(t, st, W, H, X, Y, t < IN0 ? 0 : Math.min(1, p * 3));
      subRender(t, X + 40);
    },
  };

  // -- Club colours: each half in its club's colour, meeting in the middle -------------------------------------------------
  S.club = {
    build() {
      const half = (home) => '<div id="' + (home ? 'ch' : 'ca') + '" class="row fitrow" style="position:absolute;top:0;height:' + H + 'px;width:' + (W / 2 + 40) + 'px;' + (home ? 'left:0' : 'right:0') +
        ';background:' + (home ? M.hc : M.ac) + ';clip-path:polygon(' + (home ? '0 0,100% 0,calc(100% - 60px) 100%,0 100%' : '60px 0,100% 0,100% 100%,0 100%') + ');' +
        (home ? 'padding-left:150px;padding-right:170px;gap:20px' : 'justify-content:flex-end;padding-right:40px;padding-left:170px;gap:20px') + ';box-sizing:border-box">' +
        (home ? crest(true, 84) + '<span class="nm" style="font-size:48px;color:#fff">' + M.home + '</span>'
          : '<span class="nm" style="font-size:48px;color:#fff">' + M.away + '</span>' + crest(false, 84)) + '</div>';
      $('strap').innerHTML = '<div style="position:relative;width:' + W + 'px;height:' + H + 'px">' + half(true) + half(false) +
        '<div id="ccomp" style="position:absolute;left:0;top:0;width:' + H + 'px;height:' + H + 'px;display:grid;place-items:center;background:#000">' + comp(84) + '</div>' +
        '<div id="cs" style="position:absolute;left:' + (W / 2 - 120) + 'px;top:-10px;width:240px;height:' + (H + 20) + 'px;background:#0E0E10;display:flex;flex-direction:column;align-items:center;justify-content:center;border-radius:6px;box-shadow:0 0 0 3px rgba(255,255,255,.12)">' +
        '<span class="lab" style="font-size:16px;color:#bbb">' + MO.label + '</span><span class="num" style="font-size:64px;line-height:1;color:#fff">' + MO.score[0] + ' – ' + MO.score[1] + '</span></div>' +
        '<i id="cflash" style="position:absolute;left:' + (W / 2 - 80) + 'px;top:-40px;width:160px;height:' + (H + 80) + 'px;background:radial-gradient(closest-side,rgba(255,255,255,.95),rgba(255,255,255,0))"></i></div>';
      $('subline').innerHTML = subHTML('#000', '#fff', 'grad', '#000');
      $('board').innerHTML = blockBoard({ bg: '#0E0E10', comp: '#000', score: 'linear-gradient(#1A1A1C,#000)', clock: '#000', ink: '#fff', strips: false, sideBg: [M.hc, M.ac] }) + SHEEN;
      placeBoard(8);
    },
    render(t) {
      const st = $('strap'); st.style.left = X + 'px'; st.style.top = Y + 'px';
      const p = eo(prog(t, IN0, IN0 + 0.45));
      $('ch').style.transform = 'translateX(' + (-1400 * (1 - p)) + 'px)';
      $('ca').style.transform = 'translateX(' + (1400 * (1 - p)) + 'px)';
      const f = prog(t, IN0 + 0.42, IN0 + 0.62); $('cflash').style.opacity = f > 0 ? Math.sin(Math.PI * f) : 0;
      const sp = back(prog(t, IN0 + 0.45, IN0 + 0.75)); $('cs').style.transform = 'scale(' + Math.max(0, sp) + ')';
      $('ccomp').style.opacity = prog(t, IN0 + 0.55, IN0 + 0.8);
      landOn(t, st, W, H, X, Y, t < IN0 ? 0 : 1);
      subRender(t, X + 40);
    },
  };

  // -- Minimal: words only, on a soft shade; they track out and the board writes itself in ---------------------------------
  S.minimal = {
    build() {
      // the shade is a soft-edged gradient (the mock-up blurred a block: too dear to draw on a slower TV)
      $('strap').innerHTML = '<div id="mn" style="position:relative;width:1560px;height:300px">' +
        '<i id="mshade" style="position:absolute;left:40px;top:10px;width:1480px;height:280px;border-radius:140px;background:radial-gradient(closest-side,rgba(6,8,16,.97) 72%,rgba(6,8,16,.6) 86%,rgba(6,8,16,0))"></i>' +
        '<div id="mtext" style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px">' +
        '<div class="row" style="gap:14px">' + comp(40) + '<span id="ml" class="lab" style="font-size:20px;color:#cfd5ea">' + MO.label + '</span></div>' +
        '<div id="mt" class="row nm" style="font-size:66px;gap:26px;color:#fff">' + crest(true, 66) + M.home +
        '<span class="num" style="font-weight:300;color:#cfd5ea">' + MO.score[0] + '–' + MO.score[1] + '</span>' + M.away + crest(false, 66) + '</div>' +
        '<div class="sub row" style="gap:12px;font-size:17px;color:#cfd5ea">' + markSvg(22, 'grad', '#070912') + '<span style="opacity:.85">WHITEBROOK ROAD SPORTS</span>' +
        (MO.sub ? '<span style="opacity:.4">|</span><span>' + MO.sub + '</span>' : '') + '</div></div></div>';
      $('subline').innerHTML = '';
      $('board').innerHTML = '<div class="row" style="height:' + BH + 'px;padding:0 20px;gap:14px;background:#070912;font-size:26px">' +
        '<span class="nm">' + M.hs + '</span><span class="num" style="font-weight:400">' + MO.score[0] + '–' + MO.score[1] + '</span><span class="nm">' + M.as + '</span>' +
        '<span style="opacity:.4">·</span><span class="num bclock" style="font-weight:500;color:#DDE3FF">00:00</span></div>';
      placeBoard(14);
    },
    render(t) {
      const st = $('strap'); st.style.left = '180px'; st.style.top = (878 - 150) + 'px';
      const p = eo(prog(t, IN0, IN1 + 0.1)), q = eio(prog(t, OUT0, OUT0 + 0.45));
      st.style.opacity = t < IN0 ? 0 : 1;
      $('mshade').style.opacity = prog(t, IN0, IN0 + 0.45) * (1 - eio(prog(t, OUT0 + 0.1, OUT0 + 0.55)));
      $('mshade').style.transform = 'scaleX(' + lerp(0.55, 1, eo(prog(t, IN0, IN0 + 0.6))) + ')';
      const half = 50 * (1 - p);
      $('mtext').style.clipPath = 'inset(0 ' + half + '% 0 ' + half + '%)';
      $('mt').style.letterSpacing = (lerp(0.3, 0.02, p) + 0.22 * q) + 'em';
      $('ml').style.letterSpacing = (lerp(0.7, 0.32, p) + 0.3 * q) + 'em';
      $('mtext').style.opacity = 1 - q; $('mtext').style.filter = q > 0 ? 'blur(' + (8 * q) + 'px)' : 'none';
      const b = $('board'), bo = eo(prog(t, OUT0 + 0.35, OUT1 + 0.25));
      b.style.opacity = bo > 0 ? 1 : 0;
      b.style.clipPath = 'inset(0 ' + (100 - 100 * bo) + '% 0 0)';
    },
  };

  // -- Crests and time: the crests large, popping out with rings -----------------------------------------------------------
  S.crests = {
    build() {
      const cr = (home) => '<div style="position:relative;width:150px;height:150px;flex:none;display:grid;place-items:center;margin-top:-26px">' +
        '<i id="' + (home ? 'rh' : 'ra') + '" style="position:absolute;inset:0;border-radius:50%;border:4px solid ' + (home ? M.hc : '#5f86e8') + '"></i>' +
        '<div id="' + (home ? 'kh' : 'ka') + '" style="width:150px;height:150px;border-radius:50%;background:radial-gradient(circle,#1b2a6b,#0A1238 70%);display:grid;place-items:center;box-shadow:0 0 0 3px rgba(255,255,255,.15)">' +
        crest(home, 112) + '</div></div>';
      $('strap').innerHTML = '<div id="cr" class="row" style="width:' + W + 'px;height:' + H + 'px;background:#0A1238;border-radius:12px 12px 0 0">' +
        '<div style="width:' + H + 'px;height:100%;flex:none;display:grid;place-items:center;background:#070C26;border-radius:12px 0 0 0">' + comp(86) + '</div>' +
        '<div class="fitrow" style="flex:1;min-width:0;text-align:right;padding-right:24px;overflow:visible"><span class="nm" style="font-size:44px">' + M.home + '</span></div>' + cr(true) +
        '<div style="width:250px;height:100%;flex:none;display:flex;flex-direction:column;align-items:center;justify-content:center;background:linear-gradient(#2443B4,#142A78);margin:0 18px"><span class="lab" style="font-size:16px;color:#c9d3ff">' + MO.label + '</span><span id="cnum" class="num" style="font-size:64px;line-height:1">' + MO.score[0] + ' – ' + MO.score[1] + '</span></div>' +
        cr(false) + '<div class="fitrow" style="flex:1;min-width:0;padding-left:24px;overflow:visible"><span class="nm" style="font-size:44px">' + M.away + '</span></div>' +
        '<div class="row num sslot" style="width:140px;height:100%;flex:none;justify-content:center;font-size:34px;background:#050816;border-radius:0 12px 0 0">00:00</div></div>';
      $('subline').innerHTML = subHTML('#070C26', '#E6E9F2', 'grad', '#070C26');
      $('board').innerHTML = '<div class="row" style="height:' + BH + 'px;background:#0A1238">' +
        '<div style="width:54px;height:100%;display:grid;place-items:center;background:#070C26">' + comp(38) + '</div>' +
        '<div style="padding:0 12px">' + crest(true, 40) + '</div>' +
        '<div class="num" style="height:100%;display:grid;place-items:center;padding:0 18px;font-size:30px;background:linear-gradient(#2443B4,#142A78)">' + MO.score[0] + '–' + MO.score[1] + '</div>' +
        '<div style="padding:0 12px">' + crest(false, 40) + '</div>' +
        '<div class="num bclock" style="height:100%;display:grid;place-items:center;padding:0 18px;font-size:24px;background:#050816">00:00</div></div>' + SHEEN;
      placeBoard(16);
    },
    render(t) {
      const st = $('strap'); st.style.left = X + 'px'; st.style.top = Y + 'px';
      const ex = eo(prog(t, IN0, IN0 + 0.32)); $('cr').style.transform = 'scaleX(' + Math.max(0.001, ex) + ')';
      for (const [k, r, t0] of [['kh', 'rh', IN0 + 0.3], ['ka', 'ra', IN0 + 0.38]]) {
        const pp = prog(t, t0, t0 + 0.32); $(k).style.transform = 'scale(' + Math.max(0, back(pp)) + ')';
        const rp = prog(t, t0 + 0.12, t0 + 0.7); $(r).style.transform = 'scale(' + lerp(1, 1.9, eo(rp)) + ')'; $(r).style.opacity = rp > 0 && rp < 1 ? (1 - rp) : 0;
      }
      $('cnum').style.transform = 'translateY(' + (-30 * (1 - eo(prog(t, IN0 + 0.45, IN0 + 0.7)))) + 'px)';
      $('cnum').style.opacity = prog(t, IN0 + 0.45, IN0 + 0.6);
      landOn(t, st, W, H, X, Y, t < IN0 ? 0 : 1);
      subRender(t, X + 40);
    },
  };

  // -- Stacked: the clubs one above the other; rows flip down, and flip away into the board's own rows --------------------
  S.stacked = {
    build() {
      const rowH = 66;
      const row = (home) => '<div id="' + (home ? 'sr1' : 'sr2') + '" class="row fitrow" style="height:' + rowH + 'px;gap:16px;background:#0E1330;transform-origin:50% ' + (home ? '0' : '100%') + ';' + (home ? 'border-bottom:2px solid rgba(255,255,255,.1);box-sizing:border-box' : '') + '">' +
        '<i style="width:10px;height:100%;flex:none;background:' + (home ? M.hc : M.ac) + '"></i>' + crest(home, 50) +
        '<span class="nm" style="font-size:36px;flex:1;min-width:0">' + (home ? M.home : M.away) + '</span>' +
        '<div class="num" style="width:76px;height:100%;flex:none;display:grid;place-items:center;font-size:40px;background:#1E2A66">' + (home ? MO.score[0] : MO.score[1]) + '</div></div>';
      $('strap').innerHTML = '<div class="row" style="width:' + W + 'px;height:' + (rowH * 2) + 'px;border-radius:10px 10px 0 0;overflow:hidden">' +
        '<div id="scomp2" style="width:132px;height:100%;flex:none;display:grid;place-items:center;background:#070C26">' + comp(90) + '</div>' +
        '<div style="flex:1;min-width:0;display:flex;flex-direction:column;perspective:900px">' + row(true) + row(false) + '</div>' +
        '<div id="sright" style="width:200px;height:100%;flex:none;background:#050816;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px">' +
        '<span class="lab" style="font-size:16px;color:#c9d3ff">' + MO.label + '</span><span class="num sslot" style="font-size:36px">00:00</span></div>' +
        '<div id="svenue" style="width:360px;height:100%;flex:none;box-sizing:border-box;padding:0 28px;background:linear-gradient(#111a48,#070C26);display:flex;flex-direction:column;justify-content:center;gap:6px;overflow:hidden">' +
        '<div class="row sub" style="gap:10px;font-size:14px;color:#aeb8e6">' + markSvg(22, 'grad', '#070C26') + (M.ground ? 'LIVE FROM' : 'WHITEBROOK ROAD SPORTS') + '</div>' +
        '<span id="svname" class="nm" style="font-size:30px;color:#fff">' + (M.ground || esc(up(C.compName))) + '</span>' +
        '<span class="sub" style="font-size:15px;color:#c9d3ff">' + MO.extra + '</span></div></div>';
      $('subline').innerHTML = '';
      $('board').innerHTML = '<div class="row" style="height:84px">' +
        '<div id="bcomp" style="width:72px;height:100%;display:grid;place-items:center;background:#070C26">' + comp(48) + '</div>' +
        '<div style="display:flex;flex-direction:column;perspective:500px">' +
        ['h', 'a'].map((s) => '<div id="brow' + (s === 'h' ? 1 : 2) + '" class="row" style="height:42px;gap:10px;background:#0E1330;transform-origin:50% ' + (s === 'h' ? '0' : '100%') + '"><i style="width:6px;height:100%;background:' + (s === 'h' ? M.hc : M.ac) + '"></i>' + crest(s === 'h', 30) + '<span class="nm" style="font-size:22px;width:56px">' + (s === 'h' ? M.hs : M.as) + '</span><span class="num" style="width:40px;height:100%;display:grid;place-items:center;font-size:24px;background:#1E2A66">' + (s === 'h' ? MO.score[0] : MO.score[1]) + '</span></div>').join('') +
        '</div><div id="bclk" class="num bclock" style="height:100%;display:grid;place-items:center;padding:0 18px;font-size:24px;background:#050816">00:00</div></div>' + SHEEN;
      placeBoard(7);
      sheenAt = OUT0 + 0.95;
    },
    render(t) {
      const st = $('strap'), sy = Y - 8; st.style.left = X + 'px'; st.style.top = sy + 'px'; st.style.opacity = t < IN0 || t > OUT0 + 0.52 ? 0 : 1;
      const cIn = eo(prog(t, IN0, IN0 + 0.22)), cOut = ei(prog(t, OUT0 + 0.3, OUT0 + 0.5));
      $('scomp2').style.transform = 'translateX(' + (-160 * (1 - cIn) - 160 * cOut) + 'px)';
      $('scomp2').style.opacity = prog(t, IN0, IN0 + 0.15) * (1 - cOut);
      const r1 = eo(prog(t, IN0 + 0.15, IN0 + 0.45)) - eio(prog(t, OUT0 + 0.12, OUT0 + 0.36));
      const r2 = eo(prog(t, IN0 + 0.3, IN0 + 0.6)) - eio(prog(t, OUT0 + 0.2, OUT0 + 0.44));
      $('sr1').style.transform = 'rotateX(' + (-90 * (1 - r1)) + 'deg)';
      $('sr2').style.transform = 'rotateX(' + (90 * (1 - r2)) + 'deg)';
      const k = eo(prog(t, IN0 + 0.5, IN0 + 0.75)) - eio(prog(t, OUT0 + 0.08, OUT0 + 0.28));
      $('sright').style.clipPath = 'inset(0 ' + (100 * (1 - k)) + '% 0 0)';
      const v = eo(prog(t, IN0 + 0.62, IN0 + 0.95)) - eio(prog(t, OUT0, OUT0 + 0.2));
      $('svenue').style.clipPath = 'inset(0 ' + (100 * (1 - v)) + '% 0 0)';
      const b = $('board'); b.style.clipPath = 'none'; b.style.opacity = t >= OUT0 + 0.38 ? 1 : 0;
      const bc = eo(prog(t, OUT0 + 0.4, OUT0 + 0.58));
      $('bcomp').style.transform = 'translateX(' + (-80 * (1 - bc)) + 'px)'; $('bcomp').style.opacity = bc;
      $('brow1').style.transform = 'rotateX(' + (-90 * (1 - eo(prog(t, OUT0 + 0.48, OUT0 + 0.72)))) + 'deg)';
      $('brow2').style.transform = 'rotateX(' + (90 * (1 - eo(prog(t, OUT0 + 0.56, OUT0 + 0.8)))) + 'deg)';
      $('bclk').style.clipPath = 'inset(0 ' + (100 * (1 - eo(prog(t, OUT0 + 0.7, OUT0 + 0.9)))) + '% 0 0)';
    },
  };

  // -- Card: white panels, its own blue in the middle; the panels fold behind the blue, which flies to the corner ----------
  const CM = 236, PW = (W - CM) / 2;           // the strap's blue middle, and each panel
  const BCM = 160, BCX = 7 + 92;               // the board's blue middle, and where it starts
  S.card = {
    build() {
      const panel = (home) => '<div style="position:absolute;top:0;height:' + H + 'px;width:' + PW + 'px;overflow:hidden;' + (home ? 'left:0' : 'right:0') + '">' +
        '<div id="' + (home ? 'cph' : 'cpa') + '" class="row fitrow" style="width:' + PW + 'px;height:' + H + 'px;background:linear-gradient(#F1F3F7,#D3D8E1);color:#0B1640;gap:22px;box-sizing:border-box;' + (home ? 'padding-right:16px' : 'flex-direction:row-reverse;padding-left:16px') + '">' +
        '<i style="width:12px;height:100%;flex:none;background:' + (home ? M.hc : M.ac) + '"></i>' + crest(home, 96) +
        '<span class="nm" style="font-size:46px">' + (home ? M.home : M.away) + '</span></div></div>';
      $('strap').innerHTML = '<div style="position:relative;width:' + W + 'px;height:' + H + 'px;border-radius:10px 10px 0 0;overflow:hidden">' + panel(true) + panel(false) +
        '<div id="cmid" style="position:absolute;left:' + PW + 'px;top:0;width:' + CM + 'px;height:' + H + 'px;background:linear-gradient(#2A3DC9,#0F1B7E);display:flex;flex-direction:column;align-items:center;justify-content:center;color:#fff">' +
        '<div class="row" style="gap:9px">' + comp(28) + '<span class="lab" style="font-size:14px;color:#C3CBF4">' + MO.label + '</span></div>' +
        '<span class="num" style="font-size:60px;line-height:1.02">' + MO.score[0] + ' - ' + MO.score[1] + '</span></div></div>';
      $('subline').innerHTML = subHTML('#0F1B7E', '#fff', 'grad', '#0F1B7E');
      $('board').innerHTML = '<div class="row" style="height:84px;background:linear-gradient(#F1F3F7,#D3D8E1)">' +
        '<i style="width:7px;height:100%;background:' + M.hc + '"></i><div style="width:92px;display:grid;place-items:center">' + crest(true, 62) + '</div>' +
        '<div style="width:' + BCM + 'px;height:100%;background:linear-gradient(#2A3DC9,#0F1B7E);display:flex;flex-direction:column;align-items:center;justify-content:center;color:#fff"><span class="num" style="font-size:34px;line-height:1">' + MO.score[0] + ' - ' + MO.score[1] + '</span><span class="num bclock" style="font-size:22px;font-weight:600">00:00</span></div>' +
        '<div style="width:92px;display:grid;place-items:center">' + crest(false, 62) + '</div><i style="width:7px;height:100%;background:' + M.ac + '"></i></div>' + SHEEN;
      placeBoard(7);
      sheenAt = OUT1 + 0.4;
    },
    render(t) {
      const st = $('strap'); st.style.left = X + 'px'; st.style.top = Y + 'px';
      st.style.opacity = t < IN0 ? 0 : 1 - prog(t, OUT1 - 0.04, OUT1);
      const m = eo(prog(t, IN0, IN0 + 0.28)); $('cmid').style.transform = 'scaleX(' + Math.max(0.001, m) + ')';
      const out = eo(prog(t, IN0 + 0.22, IN1)), inn = eio(prog(t, OUT0, OUT0 + 0.28));
      const off = PW * (1 - out + inn);
      $('cph').style.transform = 'translateX(' + off + 'px)'; $('cpa').style.transform = 'translateX(' + (-off) + 'px)';
      const fly = eio(prog(t, OUT0 + 0.22, OUT1));
      st.style.transformOrigin = PW + 'px 0';
      st.style.transform = fly > 0 ? 'translate(' + (BXd + BCX * BS - X - PW) * fly + 'px,' + (BYd - Y) * fly + 'px) scale(' + lerp(1, 84 * BS / H, fly) + ')' : 'none';
      subRender(t, X + 40);
      const b = $('board'), p = prog(t, OUT1 - 0.04, OUT1 + 0.45);
      b.style.opacity = p > 0 ? 1 : 0;
      const bw = b.firstElementChild.offsetWidth, c = eo(p);
      b.style.clipPath = 'inset(0 ' + ((bw - BCX - BCM) * (1 - c)) + 'px 0 ' + (BCX * (1 - c)) + 'px)';
    },
  };

  // -- Frosted glass: silvery frost, a sheen, a fine white edge; it crystallises, and becomes the board --------------------
  S.frosted = {
    build() {
      $('strap').innerHTML = '<div id="fp" style="position:relative;overflow:hidden;background:linear-gradient(180deg,#9AA5BA,#5D677D);box-shadow:0 22px 50px -24px rgba(0,0,0,.7)">' +
        '<div id="fin" class="row" style="position:absolute;left:0;top:0;width:' + W + 'px;height:' + H + 'px;padding:0 24px 0 26px;box-sizing:border-box;color:#fff;text-shadow:0 1px 2px rgba(0,0,0,.25)">' +
        comp(80) +
        '<div class="row fitrow" style="flex:1;gap:18px;justify-content:flex-end;padding-right:24px;min-width:0"><span class="nm" style="font-size:46px">' + M.home + '</span>' + crest(true, 90) + '</div>' +
        '<div style="height:100%;padding:0 38px;flex:none;background:rgba(255,255,255,.24);display:flex;flex-direction:column;align-items:center;justify-content:center"><span class="lab" style="font-size:15px;color:#eef1f7">' + MO.label + '</span><span class="num" style="font-size:60px;line-height:1">' + MO.score[0] + ' – ' + MO.score[1] + '</span></div>' +
        '<div class="row fitrow" style="flex:1;gap:18px;padding-left:24px;min-width:0">' + crest(false, 90) + '<span class="nm" style="font-size:46px">' + M.away + '</span></div>' +
        '<div class="row num sslot" style="padding:14px 20px;border-radius:14px;flex:none;background:rgba(0,0,0,.14);font-size:30px">00:00</div></div>' +
        '<i style="position:absolute;inset:0 0 52% 0;background:linear-gradient(rgba(255,255,255,.32),rgba(255,255,255,0))"></i>' +
        '<i id="fsweep" style="position:absolute;top:-20%;height:140%;width:260px;background:linear-gradient(90deg,rgba(255,255,255,0),rgba(255,255,255,.55),rgba(255,255,255,0));transform:skewX(-22deg)"></i>' +
        '<i id="fedge" style="position:absolute;inset:0;border:2px solid rgba(255,255,255,.62);box-sizing:border-box;border-radius:inherit"></i></div>';
      $('subline').innerHTML = subHTML('linear-gradient(180deg,#6f7a90,#4e586d)', '#fff', '#fff', '#5D677D', ';border:1.5px solid rgba(255,255,255,.45);border-top:0;justify-content:center');
      $('board').innerHTML = '<div class="row" style="height:' + BH + 'px;padding:0 0 0 14px;gap:12px;border-radius:16px;overflow:hidden;position:relative;background:linear-gradient(180deg,#9AA5BA,#5D677D);border:2px solid rgba(255,255,255,.55);color:#fff;box-sizing:border-box">' +
        '<i style="position:absolute;inset:0 0 52% 0;background:linear-gradient(rgba(255,255,255,.3),rgba(255,255,255,0))"></i>' +
        crest(true, 38) + '<span class="num" style="font-size:28px;padding:0 16px;height:100%;display:grid;place-items:center;background:rgba(255,255,255,.24)">' + MO.score[0] + '–' + MO.score[1] + '</span>' +
        crest(false, 38) + '<span class="num bclock" style="font-size:24px;padding:0 16px;height:100%;display:grid;place-items:center;background:rgba(0,0,0,.14)">00:00</span></div>' + SHEEN;
      placeBoard(16);
    },
    render(t) {
      const st = $('strap'), fp = $('fp');
      const pin = eo(prog(t, IN0, IN1)), morph = eio(prog(t, OUT0, OUT1));
      const bw = (bw0() || 330) * BS;
      const x = lerp(X, BXd, morph), y = lerp(Y, BYd, morph), w = lerp(W, bw, morph), h = lerp(H, BH * BS, morph);
      st.style.left = x + 'px'; st.style.top = y + 'px';
      const rad = lerp(37, 16 * BS, morph);
      fp.style.width = w + 'px'; fp.style.height = h + 'px'; fp.style.borderRadius = rad + 'px';
      st.style.opacity = t < IN0 ? 0 : (t > OUT1 ? 0 : 1);
      const half = 50 * (1 - pin);
      fp.style.clipPath = 'inset(' + (half * 0.9) + '% ' + half + '% ' + (half * 0.9) + '% ' + half + '% round ' + rad + 'px)';
      st.style.filter = pin < 1 ? 'blur(' + (14 * (1 - pin)) + 'px) brightness(' + lerp(1.6, 1, pin) + ')' : 'none';
      $('fin').style.opacity = (1 - prog(t, OUT0, OUT0 + 0.22)) * prog(t, IN0 + 0.3, IN1);
      const sw = prog(t, IN0 + 0.55, IN0 + 1.15); $('fsweep').style.left = lerp(-320, W + 120, eio(sw)) + 'px';
      $('fedge').style.opacity = prog(t, IN0 + 0.4, IN0 + 0.8);
      subRender(t, X + 60, W - 120);
      $('board').style.opacity = prog(t, OUT1 - 0.22, OUT1); $('board').style.clipPath = 'none';
    },
  };

  // ==== the 26 more looks: begin ====
  // ======================================================================================================================
  // 26 more looks, as many as the boards have: the competitions' (16), the retro boards (4), the Scoreboard Lab's (5) and
  // the 3D kits. The apps draw those boards with CompBug (overlays/java/.../CompBug.java; the 3D kits' row with
  // OverlayBug/BoardBug); here each board is drawn the same way, as one SVG at the app's own size and place (CB below: its
  // blocks laid out from its row's height as CompBug.segs lays them, in the same lettering, stretched to the width the app
  // gave), and each strap is drawn in its look's own style with the same pieces, the competition's logo as its board has it.
  // Page elements and SVG only: no canvas but a one-off for a logo's tint or edge, no filters.
  // ======================================================================================================================
  const NS = 'http://www.w3.org/2000/svg';
  const nn = (v) => Math.round(v * 100) / 100;
  const h2 = (v) => (v < 16 ? '0' : '') + v.toString(16);
  // a colour as CompBug writes it (0xAARRGGBB) as CSS
  const A = (c) => {
    if (typeof c === 'string') return c;
    c >>>= 0;
    const a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
    return a === 255 ? '#' + h2(r) + h2(g) + h2(b) : 'rgba(' + r + ',' + g + ',' + b + ',' + (a / 255).toFixed(3) + ')';
  };
  const argb = (hex, d) => (0xFF000000 | parseInt((/^#[0-9a-f]{6}$/i.test(hex || '') ? hex : d).slice(1), 16)) >>> 0;
  const solid = (c) => (0xFF000000 | (c & 0xFFFFFF)) >>> 0;
  const lumi = (c) => Math.floor((((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114) / 1000);
  const INK = 0xFFF5F6FA, DARK = 0xFF111418, RED = 0xFFD7192F;
  const VIOLET = 0xFF6D28F2, CORAL = 0xFFFF6B6B, FLOOD = 0xFFF6F7FA, FLOOD_INK = 0xFF0B0E16, AMBER = 0xFFFFB21E,
    LED_RED = 0xFFFF5A3C, LAB_DARK = 0xFF0B0E16, YELLOW = 0xFFFFD21E;
  const inkOn = (g) => (lumi(g) > 160 ? DARK : INK);
  const inkHi = (g) => (lumi(g) > 205 ? DARK : INK);
  const darker = (c, by) => {
    const k = 1 - Math.max(0, Math.min(1, by));
    return (0xFF000000 | Math.round(((c >> 16) & 255) * k) << 16 | Math.round(((c >> 8) & 255) * k) << 8 | Math.round((c & 255) * k)) >>> 0;
  };
  const lighter = (c, by) => {
    const k = Math.max(0, Math.min(1, by)), r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
    return (0xFF000000 | Math.round(r + (255 - r) * k) << 16 | Math.round(g + (255 - g) * k) << 8 | Math.round(b + (255 - b) * k)) >>> 0;
  };
  const faded = (c, a) => ((Math.round((c >>> 24) * Math.max(0, Math.min(1, a))) << 24 | (c & 0xFFFFFF)) >>> 0);

  // the lettering, [family, weight] (strap.html has the faces: the same files CompBug draws with; Outfit's weight is its
  // wght axis, as CompBug sets it; monospace is the system's, as CompBug's Typeface.MONOSPACE is)
  const F = { mont5: ['CBMont', 500], mont6: ['CBMont', 600], mont7: ['CBMont', 700], mont8: ['CBMont', 800], inter5: ['CBInter', 500],
    inter6: ['CBInter', 600], rubik6: ['CBRubik', 600], bcond8: ['CBBarlowC', 800], bsemi7: ['CBBarlowS', 700], outfit: ['Outfit', 760],
    mono: ['monospace', 700] };
  const W760 = 760, W800 = 820, W600 = 620;
  // words measured as they will be drawn (an SVG text out of sight: the same shaping, figures and spacing)
  let meter = null, capCtx = null;
  const caps = {};
  // while a board is drawn, its words are laid out as Android lays CompBug's (droidText)
  let droid = false;
  const asBoard = (fn) => { const was = droid; droid = true; try { return fn(); } finally { droid = was; } };
  function meterFor(s, size, f, wght, em) {
    if (!meter) {
      const svg = document.createElementNS(NS, 'svg');
      svg.setAttribute('class', 'cbsvg');
      svg.setAttribute('width', '10'); svg.setAttribute('height', '10');
      svg.style.cssText = 'position:absolute;left:0;top:0;visibility:hidden';
      meter = document.createElementNS(NS, 'text');
      svg.appendChild(meter);
      $('stage').appendChild(svg);
    }
    meter.setAttribute('font-family', f[0]);
    meter.setAttribute('font-weight', String(wght || f[1]));
    meter.setAttribute('font-size', String(size));
    meter.setAttribute('letter-spacing', String(em || 0));
    meter.textContent = String(s);
    return meter;
  }
  // a word's glyphs as Android sets CompBug's (Minikin without linear metrics: each glyph's advance, its kerning in it,
  // rounded to a whole pixel, and the letter spacing too, half of it before each letter): {xs: each glyph's x, w}
  function droidText(s, size, f, wght, em) {
    // the system's monospace has no bold: Android makes it up keeping the regular advances (the page's made-up bold adds
    // to them), so it is measured regular and drawn bold
    const m = meterFor(s, size, f, f === F.mono ? 400 : wght, 0), n = String(s).length, total = m.getComputedTextLength(), pos = [];
    for (let i = 0; i < n; i++) pos.push(m.getStartPositionOfChar(i).x);
    const ls = Math.round((em || 0) * size), half = Math.floor(ls / 2), xs = [];
    let x = 0;
    for (let i = 0; i < n; i++) {
      xs.push(x + half);
      x += Math.round((i < n - 1 ? pos[i + 1] : total) - pos[i]) + ls;
    }
    return { xs, w: x };
  }
  function tw(s, size, f, wght, em) {
    if (s == null || s === '') return 0;
    if (droid) return droidText(s, size, f, wght, em).w;
    return meterFor(s, size, f, wght, (em || 0) * size).getComputedTextLength();
  }
  // the height of a face's capitals at size ("H8", as CompBug.words sets its words' middle on it: whole pixels, as
  // Android's text bounds are)
  function capOf(size, f, wght) {
    const w = Math.round((wght || f[1]) / 100) * 100, key = f[0] + w;
    if (caps[key] == null) {
      if (!capCtx) capCtx = document.createElement('canvas').getContext('2d');
      capCtx.font = w + ' 200px ' + f[0];
      const m = capCtx.measureText('H8');
      caps[key] = m.actualBoundingBoxAscent > 1 ? m.actualBoundingBoxAscent / 200 : 0.7;
    }
    return Math.ceil(caps[key] * size);
  }

  // ---------- pictures: decoded once (init), tinted or edged once ----------
  const IMG = {};
  const TINTS = {};
  function tinted(url, css) {
    const im = url && IMG[url];
    if (!im || !css) return url;
    const key = css + '|' + url;
    if (TINTS[key]) return TINTS[key];
    const k = Math.min(1, 320 / Math.max(im.w, im.h)), c = document.createElement('canvas');
    c.width = Math.max(1, Math.round(im.w * k));
    c.height = Math.max(1, Math.round(im.h * k));
    const g = c.getContext('2d');
    g.drawImage(im.el, 0, 0, c.width, c.height);
    g.globalCompositeOperation = 'source-in';
    g.fillStyle = css;
    g.fillRect(0, 0, c.width, c.height);
    try { TINTS[key] = c.toDataURL('image/png'); } catch (e) { TINTS[key] = url; }
    IMG[TINTS[key]] = { el: c, w: im.w, h: im.h };
    return TINTS[key];
  }
  // a picture w by h edged in white o thick all round (Serie A's logo over its board): (w + 2o) by (h + 2o), made once
  function edged(url, w, h, o) {
    const im = url && IMG[url];
    if (!im) return url;
    const k = 2, c = document.createElement('canvas'), s = document.createElement('canvas');
    c.width = Math.ceil((w + 2 * o) * k); c.height = Math.ceil((h + 2 * o) * k);
    s.width = Math.max(1, Math.round(w * k)); s.height = Math.max(1, Math.round(h * k));
    const sg = s.getContext('2d');
    sg.drawImage(im.el, 0, 0, s.width, s.height);
    sg.globalCompositeOperation = 'source-in';
    sg.fillStyle = '#fff';
    sg.fillRect(0, 0, s.width, s.height);
    const g = c.getContext('2d');
    for (let i = 0; i < 12; i++) {
      const a = i * Math.PI / 6;
      g.drawImage(s, (o + Math.cos(a) * o) * k, (o + Math.sin(a) * o) * k);
    }
    g.drawImage(im.el, o * k, o * k, w * k, h * k);
    try { return c.toDataURL('image/png'); } catch (e) { return url; }
  }
  // the picture fitted in its box (pad all round), centred across, down: 0 at the box's top to 1 its foot
  function fit(url, l, t, r, b, pad, down) {
    const im = url && IMG[url];
    if (!im || im.w < 1 || im.h < 1) return null;
    const bw = r - l - 2 * pad, bh = b - t - 2 * pad;
    if (bw <= 1 || bh <= 1) return null;
    const k = Math.min(bw / im.w, bh / im.h), w = im.w * k, h = im.h * k;
    return { x: l + pad + (bw - w) / 2, y: t + pad + (bh - h) * (down == null ? 0.5 : down), w, h };
  }

  // ---------- an SVG drawn as CompBug draws on its canvas ----------
  let gid = 0;
  // the gradients made so far (one each, kept to the page's few): by direction and colours, across the shape's own box
  const GRADS = {}, gradDefs = [];
  // the gradients, once, for every drawing on the page (after the look is built)
  function sharedDefs() {
    if (!gradDefs.length) return;
    const s = document.createElementNS(NS, 'svg');
    s.setAttribute('width', '0'); s.setAttribute('height', '0');
    s.style.cssText = 'position:absolute;left:0;top:0';
    s.innerHTML = '<defs>' + gradDefs.join('') + '</defs>';
    $('stage').appendChild(s);
  }
  function Svg() { this.o = []; this.d = []; }
  Svg.prototype = {
    f(c) { return typeof c === 'number' ? A(c) : c; },
    // a gradient down (or across) the shape it fills, from its top (left) to its foot (right), as CompBug's are laid
    grad(x1, y1, x2, y2, cols) {
      const across = x2 !== x1, key = (across ? 'h' : 'v') + cols.map((c) => A(c)).join(',');
      if (!GRADS[key]) {
        GRADS[key] = 'cg' + (gid++);
        gradDefs.push('<linearGradient id="' + GRADS[key] + '" x1="0" y1="0" x2="' + (across ? 1 : 0) + '" y2="' + (across ? 0 : 1) + '">' +
          cols.map((c, i) => '<stop offset="' + (cols.length > 1 ? nn(i / (cols.length - 1)) : 0) + '" stop-color="' + A(c) + '"/>').join('') + '</linearGradient>');
      }
      return 'url(#' + GRADS[key] + ')';
    },
    rgrad(cx, cy, r, cols) {
      const id = 'cg' + (gid++);
      this.d.push('<radialGradient id="' + id + '" gradientUnits="userSpaceOnUse" cx="' + nn(cx) + '" cy="' + nn(cy) + '" r="' + nn(r) + '">' +
        cols.map((c, i) => '<stop offset="' + nn(i / (cols.length - 1)) + '" stop-color="' + A(c) + '"/>').join('') + '</radialGradient>');
      return 'url(#' + id + ')';
    },
    rect(l, t, r, b, fill, extra) {
      if (r - l > 0.01 && b - t > 0.01) {
        this.o.push('<rect x="' + nn(l) + '" y="' + nn(t) + '" width="' + nn(r - l) + '" height="' + nn(b - t) + '" fill="' + this.f(fill) + '"' + (extra || '') + '/>');
      }
    },
    round(l, t, r, b, rad, fill, extra) {
      if (r - l <= 0.01 || b - t <= 0.01) return;
      const k = Math.max(0, Math.min(rad, (r - l) / 2, (b - t) / 2));
      this.o.push('<rect x="' + nn(l) + '" y="' + nn(t) + '" width="' + nn(r - l) + '" height="' + nn(b - t) + '" rx="' + nn(k) + '" fill="' + this.f(fill) + '"' + (extra || '') + '/>');
    },
    vgrad(l, t, r, b, top, foot) { this.rect(l, t, r, b, this.grad(0, t, 0, b, [top, foot])); },
    hgrad(l, t, r, b, cols) { this.rect(l, t, r, b, this.grad(l, 0, r, 0, cols)); },
    path(d, fill, extra) { this.o.push('<path d="' + d + '" fill="' + this.f(fill) + '"' + (extra || '') + '/>'); },
    circle(cx, cy, r, fill) { this.o.push('<circle cx="' + nn(cx) + '" cy="' + nn(cy) + '" r="' + nn(r) + '" fill="' + this.f(fill) + '"/>'); },
    image(href, x, y, w, h, extra) {
      if (href && w > 0 && h > 0) {
        this.o.push('<image href="' + href + '" x="' + nn(x) + '" y="' + nn(y) + '" width="' + nn(w) + '" height="' + nn(h) + '" preserveAspectRatio="none"' + (extra || '') + '/>');
      }
    },
    text(s, x, y, size, f, wght, em, fill, anchor, cls, extra) {
      this.o.push('<text x="' + nn(x) + '" y="' + nn(y) + '" font-family="' + f[0] + '" font-weight="' + (wght || f[1]) + '" font-size="' + nn(size) + '"' +
        (em ? ' letter-spacing="' + nn(em * size) + '"' : '') + ' fill="' + this.f(fill) + '"' + (anchor && anchor !== 'start' ? ' text-anchor="' + anchor + '"' : '') +
        (cls ? ' class="' + cls + '"' : '') + (extra || '') + '>' + esc(s) + '</text>');
    },
    raw(s) { this.o.push(s); },
    // the SVG for the box (x, y, w, h) of its drawing (its drawing a group named id, for another to show part of it)
    svg(x, y, w, h, extra, id) {
      return '<svg class="cbsvg" xmlns="' + NS + '" width="' + nn(w) + '" height="' + nn(h) + '" viewBox="' + nn(x) + ' ' + nn(y) + ' ' + nn(w) + ' ' + nn(h) + '"' +
        (extra || '') + '>' + (this.d.length ? '<defs>' + this.d.join('') + '</defs>' : '') + (id ? '<g id="' + id + '">' : '') + this.o.join('') +
        (id ? '</g>' : '') + '</svg>';
    },
  };
  const P = (pts) => 'M' + pts.map((p) => nn(p[0]) + ' ' + nn(p[1])).join('L') + 'Z';
  // a block with its left and/or right corners rounded
  function blockD(l, t, r, b, rad, rl, rr) {
    const k = Math.max(0, Math.min(rad, (r - l) / 2, (b - t) / 2)), L = rl ? k : 0, R = rr ? k : 0;
    const arc = (x, y) => 'A' + nn(k) + ' ' + nn(k) + ' 0 0 1 ' + nn(x) + ' ' + nn(y);
    return 'M' + nn(l + L) + ' ' + nn(t) + 'H' + nn(r - R) + (R ? arc(r, t + R) : '') + 'V' + nn(b - R) + (R ? arc(r - R, b) : '') +
      'H' + nn(l + L) + (L ? arc(l, b - L) : '') + 'V' + nn(t + L) + (L ? arc(l + L, t) : '') + 'Z';
  }
  function block(g, l, t, r, b, colour, rad, rl, rr) {
    if (!rl && !rr) g.rect(l, t, r, b, colour);
    else g.path(blockD(l, t, r, b, rad, rl, rr), colour);
  }

  // words about (x, cy) as CompBug.words sets them: centred (align 0), from x (-1) or up to x (1), at size or smaller to fit
  // maxW (0: any width), their capitals' middle on cy. o: {w: Outfit's weight, em: letter spacing, cls, halo: a glow's colour,
  // min: the smallest size a long name is set at (the straps' names: narrower then, as the first nine's are)}
  function words(g, s, x, cy, size, maxW, colour, f, align, o) {
    if (s == null || s === '' || size <= 1) return 0;
    s = String(s);
    o = o || {};
    const wg = o.w || f[1], em = o.em || 0;
    if (droid) {
      // on a board: each glyph where Android puts it (droidText), at the size that fits, from its left
      let L = droidText(s, size, f, wg, em), sz = size;
      if (maxW > 1 && L.w > maxW) { sz = size * maxW / L.w; L = droidText(s, sz, f, wg, em); }
      const left = align < 0 ? x : align > 0 ? x - L.w : x - L.w / 2, base = cy + capOf(sz, f, wg) / 2;
      const xs = ' x="' + L.xs.map((v) => nn(left + v)).join(' ') + '"';
      const at = (stroke, cls) => '<text' + xs + ' y="' + nn(base) + '" font-family="' + f[0] + '" font-weight="' + wg + '" font-size="' + nn(sz) + '"' +
        stroke + (cls ? ' class="' + cls + '"' : '') + '>' + esc(s) + '</text>';
      if (o.halo) {
        const hc = (o.cls ? o.cls + ' ' : '') + 'halo';
        g.raw(at(' fill="none" stroke="' + A(o.halo) + '" stroke-opacity=".18" stroke-width="' + nn(sz * 0.3) + '" stroke-linejoin="round"', hc));
        g.raw(at(' fill="none" stroke="' + A(o.halo) + '" stroke-opacity=".32" stroke-width="' + nn(sz * 0.14) + '" stroke-linejoin="round"', hc));
      }
      g.raw(at(' fill="' + A(colour) + '"' + (o.extra || ''), o.cls));
      return L.w;
    }
    let w = tw(s, size, f, wg, em), sz = size, squeeze = '';
    if (maxW > 1 && w > maxW) {
      sz = size * maxW / w;
      if (o.min && sz < o.min) { sz = o.min; squeeze = ' textLength="' + nn(maxW) + '" lengthAdjust="spacingAndGlyphs"'; }
      w = maxW;
    }
    const base = cy + capOf(sz, f, wg) / 2;
    // Android sets half a letter's spacing before each letter, the page all after it
    const ax = x + em * sz / 2, anchor = align < 0 ? 'start' : align > 0 ? 'end' : 'middle';
    if (o.halo) {
      // a light's glow round the words (the LED board's): the same words in wide soft strokes under them, no blur (none on
      // a slower TV: strap.html's flat)
      const hc = (o.cls ? o.cls + ' ' : '') + 'halo';
      g.text(s, ax, base, sz, f, wg, em, 'none', anchor, hc, squeeze + ' stroke="' + A(o.halo) + '" stroke-opacity=".18" stroke-width="' + nn(sz * 0.3) + '" stroke-linejoin="round"');
      g.text(s, ax, base, sz, f, wg, em, 'none', anchor, hc, squeeze + ' stroke="' + A(o.halo) + '" stroke-opacity=".32" stroke-width="' + nn(sz * 0.14) + '" stroke-linejoin="round"');
    }
    g.text(s, ax, base, sz, f, wg, em, colour, anchor, o.cls, squeeze + (o.extra || ''));
    return w;
  }


  // ---------- the boards, as CompBug draws them (draw(), segs() and each look's own drawing, in its order) ----------
  const KIND = { PL_WORLD: 'PL', PL_US: 'PL_US', FA_CUP: 'FACUP', EFL: 'EFL', UCL: 'UCL', UEL: 'UEL', UECL: 'UECL', SPFL: 'SPFL',
    LALIGA: 'LALIGA', SERIE_A: 'SERIEA', BUNDESLIGA: 'BUNDESLIGA', LIGUE_1: 'LIGUE1', EREDIVISIE: 'EREDIVISIE', LIGA_PORTUGAL: 'LIGAPORTUGAL',
    MLS: 'MLS', LIGA_MX: 'LIGAMX', RETRO_2004: 'R2004', RETRO_2008: 'R2008', RETRO_2012: 'R2012', RETRO_2016: 'R2016',
    FLOODLIGHT: 'FLOODLIGHT', SPLITKIT: 'SPLITKIT', TICKER: 'TICKER', DUO: 'DUO', LED: 'LED' };
  // how much taller than one row each is drawn (CompBug.Kind's tall)
  const TALL = { PL: 2.25, PL_US: 1.55, FACUP: 3.1, LALIGA: 2, SERIEA: 1.63, BUNDESLIGA: 2, LIGUE1: 2, LIGAPORTUGAL: 2, LIGAMX: 1.95,
    R2004: 1.9, R2008: 1.95, R2012: 1.95, R2016: 3.25, FLOODLIGHT: 1.6 };
  const PL_TOP = 0.4, FA_TOP = 1.6, SA_TOP = 0.17, MX_TOP = 0.88, R8_TOP = 0.08, R16_TOP = 1.5;
  const tallOf = (k) => TALL[k] || 1;
  // its main row in a board drawn h high ({top, bottom}; CompBug.bar)
  function barOf(k, h) {
    const u = h / tallOf(k);
    switch (k) {
      case 'PL': return [u * PL_TOP, u * (PL_TOP + 1)];
      case 'PL_US': return [h - u, h];
      case 'FACUP': return [u * FA_TOP, u * (FA_TOP + 1)];
      case 'SERIEA': return [u * SA_TOP, u * (SA_TOP + 1)];
      case 'LIGAMX': return [u * MX_TOP, u * (MX_TOP + 1)];
      case 'R2004': return [u * 0.2, u * 1.22];
      case 'R2008': case 'R2012': return [u * R8_TOP, u * (R8_TOP + 1)];
      case 'R2016': return [u * R16_TOP, u * (R16_TOP + 1)];
      case 'FLOODLIGHT': return [0, u];
      default: return [0, h];
    }
  }
  const scoresOf = (s) => {
    if (s == null || String(s).indexOf(':') >= 0) return null;
    const m = String(s).match(/\d+/g);
    return m && m.length >= 2 ? [m[0], m[1]] : null;
  };
  const clockSample = (d) => (!d.clock || d.clock.length <= 5 ? '88:88' : d.clock);
  const scoreSample = (d) => { const sc = scoresOf(d.score); return sc ? sc[0] + '-' + sc[1] : '0-0'; };
  const scoreText = (d) => { const sc = scoresOf(d.score); return sc ? sc[0] + '–' + sc[1] : (d.score || ''); };
  const ledScore = (d) => { const sc = scoresOf(d.score); return sc ? sc[0] + '-' + sc[1] : (d.score || ''); };
  const minuteMark = (c) => {
    const s = String(c || '').trim(), m = s.match(/^(\d+):\d\d$/);
    if (m) return m[1] + "'";
    if (/^\d+\s*\+\s*\d+$/.test(s)) return s.replace(/\s+/g, '') + "'";
    return s;
  };
  const splitClock = (d, sample) => {
    let c = d.clock || '';
    if (!c && !scoresOf(d.score) && d.score) c = d.score;
    return sample && c.length <= 5 ? '88:88' : c;
  };
  const codes = (d, size, f) => Math.max(tw(d.home, size, f), tw(d.away, size, f));
  const names = (d, size, f) => Math.max(tw(d.homeName, size, f), tw(d.awayName, size, f));
  const lw = (s, size, f, w, em) => tw(s, size, f, w, em);
  const labCodes = (d, size, w, em) => Math.max(lw(d.home, size, F.outfit, w, em), lw(d.away, size, F.outfit, w, em));
  function numW(d, size) {
    const sc = scoresOf(d.score);
    let w = lw('0', size, F.outfit, W800, 0);
    if (sc) w = Math.max(w, lw(sc[0], size, F.outfit, W800, 0), lw(sc[1], size, F.outfit, W800, 0));
    return w;
  }
  function floodTab(d, u) {
    const cp = d.compShort, clock = !!d.clock;
    const cw = clock ? lw(clockSample(d), u * 0.36, F.mono, W760, 0) : 0, pw = cp ? lw(cp, u * 0.3, F.outfit, W600, 0.1) : 0;
    return cw <= 0 && pw <= 0 ? 0 : u * 0.56 + cw + (cw > 0 && pw > 0 ? u * 0.22 : 0) + pw;
  }
  // the room for the competition's logo in a row u high, as wide as its shape needs (Liga MX's slot)
  function logoSlot(url, u) {
    const im = url && IMG[url];
    if (!im || !im.h) return u * 0.9;
    return Math.max(u * 0.9, Math.min(u * 2.2, u * 0.62 * (im.w / im.h) + u * 0.3));
  }
  // the picture a look draws for the competition (CompBug: its emblem, as the app cut it: d.mark; else the logo itself)
  function logoOf(k, d) {
    const light = d.logoLight || d.logo;
    switch (k) {
      case 'UCL': case 'UEL': case 'UECL': case 'LALIGA': case 'LIGUE1': case 'LIGAMX': case 'FACUP': case 'BUNDESLIGA': return d.mark || d.logo;
      case 'SERIEA': case 'PL': case 'LIGAPORTUGAL': case 'R2008': case 'R2012': case 'R2016': return d.mark || light;
      case 'SPFL': return light;
      default: return '';
    }
  }

  // the blocks along a look, in pixels, for a row u high: the same sums as CompBug.segs, for its width and its drawing
  function segs(k, d, u) {
    const c = clockSample(d), crests = !!(d.hcrest || d.acrest);
    switch (k) {
      case 'UCL': {
        const code = codes(d, u * 0.661, F.mont5) + u;
        return [u, Math.max(u * 2.6, tw(c, u * 0.661, F.mont5) + u * 1.35), u * 0.16, u * 0.26, code, u * 1.15, u * 1.15, code, u * 0.06, u * 0.26];
      }
      case 'UEL': case 'UECL': {
        const code = codes(d, u * 0.672, F.mont7) + u * 1.1;
        return [u * 1.05, u * 0.14, Math.max(u * 2.6, tw(c, u * 0.672, F.mont7) + u * 1.2), code, u * 1.45, u * 0.07, u * 1.45, code];
      }
      case 'SERIEA': {
        const code = codes(d, u * 0.642, F.mont6) + u;
        return [u * 1.35, Math.max(u * 2.4, tw(c, u * 0.642, F.mont6) + u * 0.85), code, Math.max(u * 1.7, tw(scoreSample(d), u * 0.642, F.mont6) + u * 0.8), code, u * 0.34];
      }
      case 'SPFL': {
        const code = Math.max(u * 2.3, codes(d, u * 0.763, F.inter6) + u);
        return [u * 1.65, code, u * 2.3, code, Math.max(u * 2.6, tw(c, u * 0.713, F.inter6) + u * 0.8)];
      }
      case 'EFL': {
        const code = Math.max(u * 3, codes(d, u * 0.732, F.rubik6) + u * 1.6);
        return [code, Math.max(u * 3, tw(scoreSample(d), u * 0.756, F.rubik6) + u * 1.6), code, Math.max(u * 3, tw(c, u * 0.683, F.rubik6) + u * 1.4)];
      }
      case 'MLS': {
        const code = codes(d, u * 0.725, F.inter5) + u * 1.2;
        return [Math.max(u * 2.6, tw(c, u * 0.7, F.inter5) + u), u * 1.05, code, u * 0.95, u * 0.95, code, u];
      }
      case 'PL_US': {
        const code = Math.max(u * 3, (crests ? u : 0) + codes(d, u * 0.775, F.mont6) + u);
        return [code, u * 3, code, Math.max(u * 2.9, tw(c, u * 0.725, F.mont5) + u * 1.3)];
      }
      case 'PL': {
        const code = Math.max(u * 3, codes(d, u * 0.88, F.mont8) + u * 1.3);
        return [code, u * 0.45, u * 1.05, u * 1.3, u * 1.05, u * 0.45, code];
      }
      case 'FACUP': {
        const code = Math.max(u * 2, codes(d, u * 0.744, F.mont7) + u * 0.9);
        return [u * 0.16, code, u * 1.05, Math.max(u * 3.1, tw(c, u * 0.72, F.mont7) + u * 1.3), u * 1.05, code, u * 0.16];
      }
      case 'EREDIVISIE': {
        const code = codes(d, u * 0.762, F.inter6);
        return [Math.max(u * 3.2, code + u * 1.9), u * 1.35, u * 1.3, u * 0.32, Math.max(u * 2.7, code + u * 1.6), u * 0.32,
          Math.max(u * 2.9, tw(c, u * 0.762, F.inter6) + u * 1.2)];
      }
      case 'LIGAMX': {
        const blk = Math.max(u * 4.6, (crests ? u * 0.95 : 0) + codes(d, u * 0.717, F.mont5) + u * 2.2);
        return [u * 0.15, logoSlot(logoOf(k, d), u), blk, u * 0.12, blk, Math.max(u * 2.3, tw(c, u * 0.768, F.mont7) + u * 0.9), u * 0.15];
      }
      case 'LALIGA': return [Math.max(u * 1.5, tw(c, u * 0.577, F.bcond8) + u * 0.5), Math.max(u, codes(d, u * 0.521, F.bcond8) + u * 0.45), u * 0.13, u * 1.1];
      case 'LIGAPORTUGAL': return [u * 1.55, Math.max(u * 1.55, codes(d, u * 0.34, F.mont8) + u * 0.6), u * 0.8, u * 0.16];
      case 'BUNDESLIGA': {
        const col = Math.max(u * 1.6, codes(d, u * 0.42, F.bsemi7) + u * 0.5);
        return [Math.max(u * 1.65, tw(c, u * 0.42, F.bsemi7) + u * 0.5), col, col];
      }
      case 'R2004': return [u * 1.28, Math.max(u * 2, names(d, u * 0.45, F.bsemi7) + u * 0.55), u * 0.62];
      case 'R2008': case 'R2012': {
        const name = names(d, u * 0.775, F.bsemi7);
        return [u * 2.75, name + u * 0.6, u * 0.7, Math.max(u * 1.9, tw(scoreSample(d), u * 0.868, F.bsemi7) + u), u * 0.55, name + u * 0.6, u * 0.45];
      }
      case 'R2016': {
        const code = Math.max(u * 2.3, codes(d, u * 0.78, F.rubik6) + u * 1.2);
        return [u * 0.14, code, u * 2.35, code, u * 0.14];
      }
      case 'FLOODLIGHT': {
        const code = Math.max(u * 1.1, labCodes(d, u * 0.44, W800, 0.04) + u * 0.76);
        const score = Math.max(u * 1.5, lw(scoreText(d), u * 0.44, F.outfit, W800, 0.04) + u * 0.76);
        return [code, score, code, Math.max(0, floodTab(d, u) - code * 2 - score)];
      }
      case 'SPLITKIT': {
        const code = labCodes(d, u * 0.42, W800, 0.04), num = numW(d, u * 0.55);
        return [u * 0.5 + code + u * 0.2 + num + u * 0.7, u * 0.4 + num + u * 0.2 + code + u * 0.5,
          Math.max(u * 1.6, lw(splitClock(d, true), u * 0.36, F.mono, W760, 0) + u * 0.7)];
      }
      case 'TICKER': {
        const cp = d.compShort, pad = u * 0.9;
        const comp = cp ? lw(cp, u * 0.5, F.outfit, W800, 0.06) + pad : u * 1.05;
        const code = labCodes(d, u * 0.5, W760, 0.06) + pad;
        const score = Math.max(u * 1.4, lw(scoreText(d), u * 0.5, F.outfit, W800, 0.06) + pad);
        const clk = Math.max(lw("88'", u * 0.46, F.mono, W760, 0), lw(minuteMark(d.clock), u * 0.46, F.mono, W760, 0)) + pad;
        return [comp, code, score, code, clk];
      }
      case 'DUO': {
        const code = labCodes(d, u * 0.4, W760, 0.04), pill = u * 0.35 + code + u * 0.2 + u * 0.78 + u * 0.11;
        return [pill, u * 0.15, Math.max(u * 1.5, lw(splitClock(d, true), u * 0.36, F.mono, W760, 0) + u * 0.6), u * 0.15, pill];
      }
      case 'LED': {
        const pad = u * 0.26, gap = u * 0.3;
        const code = Math.max(lw(d.home, u * 0.44, F.mono, W760, 0.12), lw(d.away, u * 0.44, F.mono, W760, 0.12));
        const score = lw(ledScore(d), u * 0.62, F.mono, W760, 0.12);
        const clk = Math.max(lw("88'", u * 0.44, F.mono, W760, 0.12), lw(minuteMark(d.clock), u * 0.44, F.mono, W760, 0.12));
        return [pad + code + gap / 2, gap + score, gap + code, gap / 2 + clk + pad];
      }
      case 'LIGUE1': default:
        return [Math.max(u * 1.4, tw(c, u * 0.42, F.mont7) + u * 0.45), Math.max(u * 2.2, codes(d, u * 0.46, F.mont7) + u * 1.3), u * 0.05, u * 0.1];
    }
  }
  const sum = (a) => a.reduce((x, y) => x + y, 0);
  // its width for a height h (the whole look's), as its words need (CompBug.natural)
  const natural = (k, d, h) => asBoard(() => sum(segs(k, d, h / tallOf(k))));

  // ---- small parts, as CompBug's ----
  function chevron(g, x, u, dir, colour) {
    const a = u * 0.12 * dir, tip = u * 0.08 * dir, th = u * 0.07 * dir;
    g.path(P([[x - a, u * 0.2], [x + tip, u * 0.5], [x - a, u * 0.8], [x - a - th, u * 0.8], [x + tip - th, u * 0.5], [x - a - th, u * 0.2]]), colour);
  }
  function chevronBand(g, x, t, b, tip, thick, dir, top, foot) {
    const cy = (t + b) / 2;
    g.path(P([[x, t], [x + thick * dir, t], [x + (thick + tip) * dir, cy], [x + thick * dir, b], [x, b], [x + tip * dir, cy]]), g.grad(0, t, 0, b, [top, foot]));
  }
  function para(g, l, t, r, b, s, top, foot) { g.path(P([[l + s, t], [r + s, t], [r, b], [l, b]]), g.grad(0, t, 0, b, [top, foot])); }
  // a path lit from its top (CompBug.fillPath)
  const lit = (g, d, colour, t, b) => g.path(d, g.grad(0, t, 0, b, [lighter(colour, 0.12), colour]));
  function team(g, code, l, t, r, b, colour, size, f) {
    g.vgrad(l, t, r, b, lighter(colour, 0.1), darker(colour, 0.12));
    words(g, code, (l + r) / 2, (t + b) / 2, size, r - l - (b - t) * 0.3, inkOn(colour), f, 0);
  }
  // the clock in its box (ground 0: none), ticking
  function clock(g, d, l, t, r, b, ground, ink, size, f) {
    if (ground) g.rect(l, t, r, b, ground);
    words(g, d.clock, (l + r) / 2, (t + b) / 2, size, (r - l) * 0.88, ink, f, 0, { cls: 'bclock' });
  }
  const scoreOne = (g, s, cx, cy, size, ink, f, o) => words(g, s, cx, cy, size, 0, ink, f, 0, o);
  function score2(g, d, sc, l, r, t, b, size, ink, f) {
    const cy = (t + b) / 2;
    if (!sc) { words(g, d.score, (l + r) / 2, cy, size * 0.8, r - l, ink, f, 0); return; }
    const m = (l + r) / 2;
    scoreOne(g, sc[0], (l + m) / 2, cy, size, ink, f);
    scoreOne(g, sc[1], (m + r) / 2, cy, size, ink, f);
  }
  function stackScores(g, d, sc, cx, half, size, ink, f) {
    if (!sc) { words(g, d.score, cx, half, size * 0.5, half * 1.8, ink, f, 0); return; }
    scoreOne(g, sc[0], cx, half / 2, size, ink, f);
    scoreOne(g, sc[1], cx, half * 1.5, size, ink, f);
  }
  function logoIn(g, url, l, t, r, b, pad, tint) {
    const at = fit(url, l, t, r, b, pad, 0.5);
    if (at) g.image(tint ? tinted(url, tint) : url, at.x, at.y, at.w, at.h);
  }
  function crestIn(g, url, l, t, size) {
    const at = fit(url, l, t, l + size, t + size, 0, 0.5);
    if (at) g.image(url, at.x, at.y, at.w, at.h);
  }
  // the Lab's scores: both figures with a dash between them, centred in l..r; the score as it is before kick-off
  function digits(g, d, sc, l, r, cy, size, ink, f, w, em, dash, dashAlpha, o) {
    const mid = (l + r) / 2;
    if (!sc) { words(g, d.score, mid, cy, size * 0.85, r - l - size * 0.4, ink, f, 0, Object.assign({ w, em }, o)); return; }
    const dw = lw(dash, size, f, w, em), a = lw(sc[0], size, f, w, em), b = lw(sc[1], size, f, w, em);
    const gap = size * 0.1, start = mid - (a + dw + b + gap * 2) / 2;
    scoreOne(g, sc[0], start + a / 2, cy, size, ink, f, Object.assign({ w, em }, o));
    words(g, dash, start + a + gap + dw / 2, cy, size, 0, dashAlpha >= 1 ? ink : faded(ink, dashAlpha), f, 0, Object.assign({ w, em }, o));
    scoreOne(g, sc[1], start + a + gap * 2 + dw + b / 2, cy, size, ink, f, Object.assign({ w, em }, o));
  }
  // the family's house-and-play mark in a size-square at (l, t) (LabMarks.house)
  function houseMark(g, l, t, size, ink, cut) {
    const k = size / 52, p = (x, y) => [l + (x - 28) * k, t + (y - 28) * k];
    g.path(P([p(31, 75), p(31, 50), p(54, 31), p(77, 50), p(77, 75)]), ink);
    g.path(P([p(49, 53), p(49, 69), p(63, 61)]), cut);
  }
  // CompBug's small capitals: each word's first letter full height, the rest smaller (the 2004 board's names)
  function smallCaps(g, s, x, cy, size, maxW, colour, f) {
    if (!s) return;
    const upS = String(s).toUpperCase(), small = size * 0.8;
    // runs of letters at one size: each word's first letter, then the rest of it (and what parts the words)
    const runs = [];
    let start = true;
    for (const ch of upS) {
      const big = start && /\p{L}/u.test(ch);
      if (runs.length && runs[runs.length - 1][1] === big) runs[runs.length - 1][0] += ch; else runs.push([ch, big]);
      start = !/\p{L}/u.test(ch);
    }
    let w = 0;
    for (const [r, big] of runs) w += tw(r, big ? size : small, f);
    const k = maxW > 1 && w > maxW ? maxW / w : 1, base = cy + capOf(size * k, f) / 2;
    const html = runs.map(([r, big]) => '<tspan font-size="' + nn((big ? size : small) * k) + '">' + esc(r) + '</tspan>').join('');
    g.raw('<text x="' + nn(x) + '" y="' + nn(base) + '" font-family="' + f[0] + '" font-weight="' + f[1] + '" fill="' + A(colour) + '">' + html + '</text>');
  }

  // ---- the looks (CompBug's own, block by block) ----
  const L = {};
  L.UCL = (g, d, x, u, h, sc) => {
    g.vgrad(x[0], 0, x[1], u, 0xFF3448EC, 0xFF2133CF);
    logoIn(g, logoOf('UCL', d), x[0], 0, x[1], u, u * 0.17, '#fff');
    g.vgrad(x[1], 0, x[2], u, 0xFFFFFFFF, 0xFFE7EAF3);
    clock(g, d, x[1], 0, x[2], u, 0, 0xFF0D1854, u * 0.661, F.mont5);
    g.rect(x[3], 0, x[4], u, solid(d.homeColour));
    g.hgrad(x[4], 0, x[8], u, [0xFF141B8C, 0xFF2A31C0, 0xFF141B8C]);
    words(g, d.home, (x[4] + x[5]) / 2, u / 2, u * 0.661, x[5] - x[4] - u * 0.3, INK, F.mont5, 0);
    chevron(g, x[5], u, 1, 0x40FFFFFF);
    chevron(g, x[7], u, -1, 0x40FFFFFF);
    if (sc) {
      scoreOne(g, sc[0], (x[5] + x[6]) / 2, u / 2, u * 0.661, INK, F.mont5);
      scoreOne(g, sc[1], (x[6] + x[7]) / 2, u / 2, u * 0.661, INK, F.mont5);
    } else words(g, d.score, (x[5] + x[7]) / 2, u / 2, u * 0.566, x[7] - x[5], INK, F.mont5, 0);
    words(g, d.away, (x[7] + x[8]) / 2, u / 2, u * 0.661, x[8] - x[7] - u * 0.3, INK, F.mont5, 0);
    g.rect(x[9], 0, x[10], u, solid(d.awayColour));
  };
  function uefa(g, d, x, u, sc, accent, k) {
    logoIn(g, logoOf(k, d), x[0], 0, x[1], u, u * 0.03, null);
    const right = x[x.length - 1], rad = u * 0.07;
    block(g, x[2], 0, x[3], u, 0xF20B0B0B, rad, true, false);
    block(g, x[3], 0, x[4], u, 0xF2262626, rad, false, false);
    block(g, x[5], 0, x[6], u, 0xF21A1A1A, rad, false, false);
    block(g, x[7], 0, right, u, 0xF2262626, rad, false, true);
    g.rect(x[2] + rad * 0.3, u * 0.925, x[3], u, accent);
    clock(g, d, x[2], 0, x[3], u * 0.93, 0, INK, u * 0.672, F.mont7);
    words(g, d.home, (x[3] + x[4]) / 2, u / 2, u * 0.672, x[4] - x[3] - u * 0.3, INK, F.mont7, 0);
    words(g, d.away, (x[7] + right) / 2, u / 2, u * 0.672, right - x[7] - u * 0.3, INK, F.mont7, 0);
    const edge = u * 0.12;
    g.vgrad(x[4], 0, x[5], u, 0xFFF7F7F7, 0xFFE4E4E4);
    g.vgrad(x[6], 0, x[7], u, 0xFFF7F7F7, 0xFFE4E4E4);
    g.rect(x[4], 0, x[4] + edge, u, solid(d.homeColour));
    g.rect(x[7] - edge, 0, x[7], u, solid(d.awayColour));
    if (sc) {
      scoreOne(g, sc[0], (x[4] + edge + x[5]) / 2, u / 2, u * 0.72, DARK, F.mont7);
      scoreOne(g, sc[1], (x[6] + x[7] - edge) / 2, u / 2, u * 0.72, DARK, F.mont7);
    } else words(g, d.score, (x[4] + x[7]) / 2, u / 2, u * 0.528, x[7] - x[4], DARK, F.mont7, 0);
  }
  L.UEL = (g, d, x, u, h, sc) => uefa(g, d, x, u, sc, 0xFFF26A1B, 'UEL');
  L.UECL = (g, d, x, u, h, sc) => uefa(g, d, x, u, sc, 0xFF27B34B, 'UECL');
  L.SERIEA = (g, d, x, u, h, sc) => {
    const bt = u * SA_TOP, bb = bt + u, cy = (bt + bb) / 2, s = u * 0.3;
    g.vgrad(x[0] + u * 0.6, bt, x[4] + s, bb, 0xFFFFFFFF, 0xFFE4E9F1);
    para(g, x[2], bt, x[3], bb, s, 0xFF15348A, 0xFF0A1E59);
    para(g, x[4], bt, x[5], bb, s, 0xFF15348A, 0xFF0A1E59);
    clock(g, d, x[1], bt, x[2] + s * 0.5, bb, 0, 0xFF0B2A6C, u * 0.642, F.mont6);
    words(g, d.home, (x[2] + x[3] + s) / 2, cy, u * 0.642, x[3] - x[2] - u * 0.3, INK, F.mont6, 0);
    const mid = (x[3] + x[4] + s) / 2;
    if (sc) {
      const half = tw(sc[0] + '-', u * 0.642, F.mont6) * 0.5 + u * 0.06;
      scoreOne(g, sc[0], mid - half, cy, u * 0.642, 0xFF0B2A6C, F.mont6);
      words(g, '-', mid, cy, u * 0.642, 0, 0xFF0B2A6C, F.mont6, 0);
      scoreOne(g, sc[1], mid + half, cy, u * 0.642, 0xFF0B2A6C, F.mont6);
    } else words(g, d.score, mid, cy, u * 0.535, x[4] - x[3], 0xFF0B2A6C, F.mont6, 0);
    words(g, d.away, (x[4] + x[5] + s) / 2, cy, u * 0.642, x[5] - x[4] - u * 0.3, INK, F.mont6, 0);
    serieMark(g, logoOf('SERIEA', d), x[0], 0, x[1] + u * 0.05, h, u * 0.04, u * 0.055);
  };
  // Serie A's logo edged in white all round, standing over the row
  function serieMark(g, url, l, t, r, b, pad, o) {
    const at = fit(url, l, t, r, b, pad + o, 0.5);
    if (at) g.image(edged(url, at.w, at.h, o), at.x - o, at.y - o, at.w + 2 * o, at.h + 2 * o);
  }
  L.SPFL = (g, d, x, u, h, sc) => {
    g.rect(x[0], 0, x[1], u, 0xF2ECEDEE);
    logoIn(g, logoOf('SPFL', d), x[0], 0, x[1], u, u * 0.1, null);
    team(g, d.home, x[1], 0, x[2], u, solid(d.homeColour), u * 0.62, F.inter6);
    g.rect(x[2], 0, x[3], u, 0xEB1A1F1C);
    score2(g, d, sc, x[2], x[3], 0, u, u * 0.62, INK, F.inter6);
    team(g, d.away, x[3], 0, x[4], u, solid(d.awayColour), u * 0.62, F.inter6);
    g.rect(x[4], 0, x[5], u, 0xEB151917);
    clock(g, d, x[4], 0, x[5], u, 0, INK, u * 0.713, F.inter6);
  };
  L.EFL = (g, d, x, u, h, sc) => {
    const hc = solid(d.homeColour), ac = solid(d.awayColour);
    g.rect(x[0], 0, x[1], u, hc);
    words(g, d.home, (x[0] + x[1]) / 2, u / 2, u * 0.732, x[1] - x[0] - u * 0.4, inkOn(hc), F.rubik6, 0);
    g.rect(x[1], 0, x[2], u, 0xFFFFFFFF);
    const mid = (x[1] + x[2]) / 2;
    if (sc) {
      const half = tw(sc[0], u * 0.756, F.rubik6) * 0.5 + u * 0.42;
      scoreOne(g, sc[0], mid - half, u / 2, u * 0.756, 0xFF141B5E, F.rubik6);
      words(g, '-', mid, u / 2, u * 0.756, 0, 0xFF141B5E, F.rubik6, 0);
      scoreOne(g, sc[1], mid + half, u / 2, u * 0.756, 0xFF141B5E, F.rubik6);
    } else words(g, d.score, mid, u / 2, u * 0.61, x[2] - x[1], 0xFF141B5E, F.rubik6, 0);
    g.rect(x[2], 0, x[3], u, ac);
    words(g, d.away, (x[2] + x[3]) / 2, u / 2, u * 0.732, x[3] - x[2] - u * 0.4, inkOn(ac), F.rubik6, 0);
    g.rect(x[3], 0, x[4], u, 0xFFFFFFFF);
    clock(g, d, x[3], 0, x[4], u, 0, 0xFF161616, u * 0.683, F.rubik6);
  };
  L.MLS = (g, d, x, u, h, sc) => {
    g.rect(x[0], 0, x[1], u, 0xF22B2D31);
    clock(g, d, x[0], 0, x[1], u, 0, INK, u * 0.7, F.inter5);
    const hc = solid(d.homeColour), ac = solid(d.awayColour);
    g.vgrad(x[1], 0, x[2], u, hc, darker(hc, 0.1));
    g.vgrad(x[2], 0, x[6], u, 0xFFF8F8F8, 0xFFD9DADC);
    g.vgrad(x[6], 0, x[7], u, ac, darker(ac, 0.1));
    words(g, d.home, (x[2] + x[3]) / 2, u / 2, u * 0.725, x[3] - x[2] - u * 0.3, 0xFF141414, F.inter5, 0);
    if (sc) {
      scoreOne(g, sc[0], (x[3] + x[4]) / 2, u / 2, u * 0.75, 0xFF141414, F.inter5);
      scoreOne(g, sc[1], (x[4] + x[5]) / 2, u / 2, u * 0.75, 0xFF141414, F.inter5);
    } else words(g, d.score, x[4], u / 2, u * 0.6, x[5] - x[3], 0xFF141414, F.inter5, 0);
    g.rect(x[4] - u * 0.012, u * 0.1, x[4] + u * 0.012, u * 0.9, 0xFFA2A4A8);
    words(g, d.away, (x[5] + x[6]) / 2, u / 2, u * 0.725, x[6] - x[5] - u * 0.3, 0xFF141414, F.inter5, 0);
  };
  L.PL_US = (g, d, x, u, h, sc, w) => {
    const top = h - u, tab = u * 0.47, cy = top + u / 2;
    const name = d.title || 'PREMIER LEAGUE', ts = u * 0.38, tabW = Math.min(w, tw(name, ts, F.mont6) + u * 0.5);
    g.rect(0, 0, tabW, tab, 0xF0121212);
    words(g, name, u * 0.25, tab / 2, ts, tabW - u * 0.4, INK, F.mont6, -1);
    const hc = solid(d.homeColour), ac = solid(d.awayColour), crests = !!(d.hcrest || d.acrest);
    g.vgrad(x[0], top, x[1], h, hc, darker(hc, 0.12));
    let hx = x[0];
    if (crests) { crestIn(g, d.hcrest, x[0] + u * 0.06, top + u * 0.06, u * 0.88); hx += u; }
    words(g, d.home, (hx + x[1]) / 2, cy, u * 0.775, x[1] - hx - u * 0.2, inkOn(hc), F.mont6, 0);
    const mid = (x[1] + x[2]) / 2;
    g.vgrad(x[1], top, mid, h, darker(hc, 0.2), darker(hc, 0.3));
    g.vgrad(mid, top, x[2], h, darker(ac, 0.32), darker(ac, 0.42));
    if (sc) {
      scoreOne(g, sc[0], (x[1] + mid) / 2, cy, u * 0.8, INK, F.mont6);
      words(g, '-', mid, cy, u * 0.8, 0, INK, F.mont6, 0);
      scoreOne(g, sc[1], (mid + x[2]) / 2, cy, u * 0.8, INK, F.mont6);
    } else words(g, d.score, mid, cy, u * 0.625, x[2] - x[1], INK, F.mont6, 0);
    g.vgrad(x[2], top, x[3], h, ac, darker(ac, 0.12));
    let ae = x[3];
    if (crests) { crestIn(g, d.acrest, x[3] - u * 0.94, top + u * 0.06, u * 0.88); ae -= u; }
    words(g, d.away, (x[2] + ae) / 2, cy, u * 0.775, ae - x[2] - u * 0.2, inkOn(ac), F.mont6, 0);
    g.vgrad(x[3], top, x[4], h, 0xFFF3F3F3, 0xFFDCDCDC);
    clock(g, d, x[3], top, x[4], h, 0, 0xFF1E1E1E, u * 0.725, F.mont5);
  };
  function bigDigit(g, s, cx, cy, size) {
    words(g, s, cx + size * 0.02, cy + size * 0.025, size, 0, 0x66000000, F.mont8, 0);
    scoreOne(g, s, cx, cy, size, INK, F.mont8);
  }
  L.PL = (g, d, x, u, h, sc) => {
    const bt = u * PL_TOP, bb = bt + u, cy = (bt + bb) / 2, tip = u * 0.3, right = x[x.length - 1];
    const hc = darker(solid(d.homeColour), 0.35), ac = darker(solid(d.awayColour), 0.35);
    lit(g, P([[x[0], bt], [x[1], bt], [x[1] + tip, cy], [x[1], bb], [x[0], bb]]), hc, bt, bb);
    lit(g, P([[right, bt], [x[6], bt], [x[6] - tip, cy], [x[6], bb], [right, bb]]), ac, bt, bb);
    chevronBand(g, x[1] + u * 0.1, bt, bb, tip, u * 0.13, 1, 0xFFF6F8FA, 0xFFAFB9C6);
    chevronBand(g, x[1] + u * 0.27, bt, bb, tip, u * 0.08, 1, 0xB3DCE5EF, 0x80AFC0D4);
    chevronBand(g, x[6] - u * 0.1, bt, bb, tip, u * 0.13, -1, 0xFFF6F8FA, 0xFFAFB9C6);
    chevronBand(g, x[6] - u * 0.27, bt, bb, tip, u * 0.08, -1, 0xB3DCE5EF, 0x80AFC0D4);
    words(g, d.home, (x[0] + x[1]) / 2, cy, u * 0.88, x[1] - x[0] - u * 0.35, INK, F.mont8, 0);
    words(g, d.away, (x[6] + right) / 2, cy, u * 0.88, right - x[6] - u * 0.35, INK, F.mont8, 0);
    g.rect(x[2] - u * 0.1, bb - u * 0.02, x[5] + u * 0.1, bb + u * 0.82, 0xFF37003C);
    words(g, d.clock, (x[2] + x[5]) / 2, bb + u * 0.4, u * 0.66, x[5] - x[2] - u * 0.3, INK, F.mont7, 0, { cls: 'bclock' });
    if (sc) {
      bigDigit(g, sc[0], (x[2] + x[3]) / 2, cy - u * 0.06, u * 1.85);
      bigDigit(g, sc[1], (x[4] + x[5]) / 2, cy - u * 0.06, u * 1.85);
    } else words(g, d.score, (x[2] + x[5]) / 2, cy, u * 0.5, x[5] - x[2], INK, F.mont8, 0);
    logoIn(g, logoOf('PL', d), x[3] - u * 0.22, 0, x[4] + u * 0.22, bb + u * 0.12, 0, null);
  };
  L.FACUP = (g, d, x, u, h, sc) => {
    const bt = u * FA_TOP, bb = bt + u, cy = (bt + bb) / 2, mid = (x[3] + x[4]) / 2;
    g.rect(x[0], bt, x[1], bb, solid(d.homeColour));
    g.vgrad(x[1], bt, x[2], bb, 0xFFF0F0F0, 0xFFD8D8D8);
    words(g, d.home, (x[1] + x[2]) / 2, cy, u * 0.744, x[2] - x[1] - u * 0.3, 0xFF3A3D41, F.mont7, 0);
    g.vgrad(x[2], bt, x[3], bb, 0xFF383B3E, 0xFF25282A);
    g.vgrad(x[4], bt, x[5], bb, 0xFF383B3E, 0xFF25282A);
    g.vgrad(x[5], bt, x[6], bb, 0xFFF0F0F0, 0xFFD8D8D8);
    words(g, d.away, (x[5] + x[6]) / 2, cy, u * 0.744, x[6] - x[5] - u * 0.3, 0xFF3A3D41, F.mont7, 0);
    g.rect(x[6], bt, x[7], bb, solid(d.awayColour));
    if (sc) {
      scoreOne(g, sc[0], (x[2] + x[3]) / 2, cy, u * 0.768, INK, F.mont7);
      scoreOne(g, sc[1], (x[4] + x[5]) / 2, cy, u * 0.768, INK, F.mont7);
    }
    faTrophy(g, d, mid, u, bt);
    g.round(x[3] - u * 0.05, bt + u * 0.55, x[4] + u * 0.05, bb + u * 0.5, u * 0.05, 0xFFC8102E);
    words(g, d.clock, mid, (bt + u * 0.55 + bb + u * 0.5) / 2, u * 0.744, x[4] - x[3], INK, F.mont7, 0, { cls: 'bclock' });
  };
  // the FA Cup's trophy (its logo's white trophy, cut by the app) standing on the clock's tab, its shadow under it
  function faTrophy(g, d, mid, u, bt) {
    const tt = bt + u * 0.55;
    if (d.mark) {
      const at = fit(d.mark, mid - u * 0.9, 0, mid + u * 0.9, tt + u * 0.02, 0, 1);
      if (at) {
        g.image(tinted(d.mark, 'rgba(0,0,0,0.349)'), at.x + u * 0.03, at.y + u * 0.03, at.w, at.h);
        g.image(d.mark, at.x, at.y, at.w, at.h);
      }
    } else logoIn(g, d.logo, mid - u * 0.9, 0, mid + u * 0.9, tt, 0, null);
  }
  L.EREDIVISIE = (g, d, x, u, h, sc) => {
    const hc = solid(d.homeColour), ac = solid(d.awayColour);
    g.hgrad(x[0], 0, x[1], u, [((darker(hc, 0.55) & 0xFFFFFF) | 0x8C000000) >>> 0, hc]);
    g.rect(x[1] - u * 0.28, 0, x[1], u, lighter(hc, 0.15));
    words(g, d.home, (x[0] + x[1] - u * 0.28) / 2, u / 2, u * 0.762, x[1] - x[0] - u * 0.8, INK, F.inter6, 0);
    g.vgrad(x[1], 0, x[2], u, 0xFFE3E3E3, 0xFFC9C9C9);
    g.vgrad(x[2], 0, x[3], u, 0xFFD4D4D4, 0xFFBBBBBB);
    if (sc) {
      scoreOne(g, sc[0], (x[1] + x[2]) / 2, u / 2, u * 0.813, 0xFF121212, F.inter6);
      scoreOne(g, sc[1], (x[2] + x[3]) / 2, u / 2, u * 0.813, 0xFF121212, F.inter6);
    } else words(g, d.score, x[2], u / 2, u * 0.61, x[3] - x[1], 0xFF121212, F.inter6, 0);
    g.rect(x[3], 0, x[4], u, lighter(ac, 0.15));
    g.hgrad(x[4], 0, x[5], u, [ac, ((darker(ac, 0.55) & 0xFFFFFF) | 0x8C000000) >>> 0]);
    words(g, d.away, (x[4] + x[5]) / 2, u / 2, u * 0.762, x[5] - x[4] - u * 0.6, INK, F.inter6, 0);
    g.vgrad(x[6], 0, x[7], u, 0xFFF7F7F7, 0xFFDEDEDE);
    clock(g, d, x[6], 0, x[7], u, 0, 0xFF121212, u * 0.762, F.inter6);
  };
  // Liga MX's title: its year and half ("2026 LIGA MX APERTURA") when it is Liga MX that is on, else the competition's
  function mxTitle(d) {
    const t = d.title || 'LIGA MX';
    if (t !== 'LIGA MX') return t;
    const now = new Date();
    return now.getFullYear() + ' LIGA MX ' + (now.getMonth() + 1 >= 7 ? 'APERTURA' : 'CLAUSURA');
  }
  L.LIGAMX = (g, d, x, u, h, sc) => {
    const bt = u * MX_TOP, bb = bt + u, cy = (bt + bb) / 2, right = x[x.length - 1], inn = u * 0.07;
    g.round(0, 0, right, h, u * 0.12, 0xF0232427);
    g.rect(u * 0.12, u * 0.03, right - u * 0.12, u * 0.075, 0xFF8E9196);
    words(g, mxTitle(d), right / 2, u * 0.47, u * 0.538, right - u * 0.6, 0xFFE3E4E7, F.mont6, 0);
    logoIn(g, logoOf('LIGAMX', d), x[1], bt + u * 0.1, x[2], bb - u * 0.1, 0, null);
    const crests = !!(d.hcrest || d.acrest), hc = solid(d.homeColour), ac = solid(d.awayColour);
    for (let side = 0; side < 2; side++) {
      const l = side === 0 ? x[2] : x[4], rr = side === 0 ? x[3] : x[5], colour = side === 0 ? hc : ac;
      g.rect(l, bt + inn, rr, bb - inn, colour);
      let tx = l + u * 0.25;
      if (crests) {
        crestIn(g, side === 0 ? d.hcrest : d.acrest, l + u * 0.08, bt + inn + u * 0.04, u - 2 * inn - u * 0.08);
        tx = l + u * 0.98;
      }
      const ik = inkHi(colour);
      words(g, side === 0 ? d.home : d.away, tx, cy, u * 0.717, rr - tx - u * 1.1, ik, F.mont5, -1);
      if (sc) scoreOne(g, sc[side], rr - u * 0.5, cy, u * 0.768, ik, F.mont6);
    }
    if (!sc) words(g, d.score, x[4], cy, u * 0.563, u * 1.5, INK, F.mont6, 0);
    clock(g, d, x[5], bt + inn, x[6], bb - inn, 0, INK, u * 0.768, F.mont7);
  };
  L.LALIGA = (g, d, x, uu, h, sc) => {
    const u = h / 2;
    g.rect(x[0], 0, x[1], u, 0xFFEE4B3B);
    logoIn(g, logoOf('LALIGA', d), x[0], 0, x[1], u, u * 0.17, '#fff');
    g.rect(x[0], u, x[1], h, 0xFFF4F4F4);
    clock(g, d, x[0], u, x[1], h, 0, 0xFF111111, u * 0.577, F.bcond8);
    g.rect(x[1], 0, x[2], h, 0xFF0E0F1F);
    words(g, d.home, (x[1] + x[2]) / 2, u / 2, u * 0.521, x[2] - x[1] - u * 0.15, INK, F.bcond8, 0);
    words(g, d.away, (x[1] + x[2]) / 2, u * 1.5, u * 0.521, x[2] - x[1] - u * 0.15, INK, F.bcond8, 0);
    g.rect(x[2], 0, x[3], u, solid(d.homeColour));
    g.rect(x[2], u, x[3], h, solid(d.awayColour));
    g.rect(x[3], 0, x[4], h, 0xFFEFEFEF);
    g.rect(x[3] + u * 0.14, u - u * 0.012, x[4] - u * 0.14, u + u * 0.012, 0xFF3C3C3C);
    stackScores(g, d, sc, (x[3] + x[4]) / 2, u, u * 0.67, 0xFF111111, F.bcond8);
  };
  L.LIGAPORTUGAL = (g, d, x, uu, h, sc) => {
    const u = h / 2, rh = u * 0.8, low = rh * 2;
    g.rect(x[0], 0, x[1], low, 0xFFFFFFFF);
    logoIn(g, logoOf('LIGAPORTUGAL', d), x[0], 0, x[1], low, u * 0.14, null);
    g.rect(x[0], low, x[1], h, 0xFFE30613);
    words(g, d.title || 'LIGA PORTUGAL', (x[0] + x[1]) / 2, (low + h) / 2, (h - low) * 0.5, x[1] - x[0] - u * 0.14, INK, F.mont8, 0);
    g.vgrad(x[1], 0, x[2], low, 0xFF0D2E73, 0xFF08225A);
    g.rect(x[1] + u * 0.12, rh - u * 0.01, x[2] - u * 0.12, rh + u * 0.01, 0xFF3F5DA6);
    words(g, d.home, (x[1] + x[2]) / 2, rh / 2, u * 0.34, x[2] - x[1] - u * 0.2, INK, F.mont8, 0);
    words(g, d.away, (x[1] + x[2]) / 2, rh * 1.5, u * 0.34, x[2] - x[1] - u * 0.2, INK, F.mont8, 0);
    g.rect(x[1], low, x[2], h, 0xFFFFFFFF);
    clock(g, d, x[1], low, x[2], h, 0, 0xFF0D2E73, (h - low) * 0.72, F.mont8);
    g.rect(x[2], 0, x[3], low, 0xFFFFFFFF);
    g.rect(x[2] + u * 0.1, rh - u * 0.01, x[3] - u * 0.1, rh + u * 0.01, 0xFFB9C3D6);
    if (sc) {
      scoreOne(g, sc[0], (x[2] + x[3]) / 2, rh / 2, u * 0.5, 0xFF0D2E73, F.mont7);
      scoreOne(g, sc[1], (x[2] + x[3]) / 2, rh * 1.5, u * 0.5, 0xFF0D2E73, F.mont7);
    } else words(g, d.score, (x[2] + x[3]) / 2, rh, u * 0.3, x[3] - x[2], 0xFF0D2E73, F.mont7, 0);
    g.rect(x[3], 0, x[4], rh, solid(d.homeColour));
    g.rect(x[3], rh, x[4], low, solid(d.awayColour));
  };
  L.BUNDESLIGA = (g, d, x, uu, h, sc) => {
    const u = h / 2, split = u * 0.62, hc = solid(d.homeColour), ac = solid(d.awayColour);
    g.rect(x[0], 0, x[1], split, 0xEB2C2C2C);
    clock(g, d, x[0], 0, x[1], split, 0, INK, split * 0.68, F.bsemi7);
    g.rect(x[0], split, x[1], h, 0xFFE3E3E3);
    // the league's player cut out of its logo by the app, in black; the logo as it is when it could not be
    logoIn(g, logoOf('BUNDESLIGA', d), x[0], split, x[1], h, u * 0.14, d.mark ? '#151515' : null);
    g.rect(x[1], 0, x[2], h, hc);
    g.rect(x[2], 0, x[3], h, ac);
    g.rect(x[1], h - u * 0.06, x[2], h, darker(hc, 0.3));
    g.rect(x[2], h - u * 0.06, x[3], h, darker(ac, 0.25));
    words(g, d.home, (x[1] + x[2]) / 2, split / 2, split * 0.66, x[2] - x[1] - u * 0.2, ((inkOn(hc) & 0xFFFFFF) | 0xE6000000) >>> 0, F.bsemi7, 0);
    words(g, d.away, (x[2] + x[3]) / 2, split / 2, split * 0.66, x[3] - x[2] - u * 0.2, ((inkOn(ac) & 0xFFFFFF) | 0xE6000000) >>> 0, F.bsemi7, 0);
    const cy = split + (h - split) / 2 - u * 0.03, size = (h - split) * 0.95;
    if (sc) {
      scoreOne(g, sc[0], (x[1] + x[2]) / 2, cy, size, inkOn(hc), F.bsemi7);
      scoreOne(g, sc[1], (x[2] + x[3]) / 2, cy, size, inkOn(ac), F.bsemi7);
    } else words(g, d.score, (x[1] + x[3]) / 2, cy, size * 0.45, x[3] - x[1], inkOn(hc), F.bsemi7, 0);
  };
  L.LIGUE1 = (g, d, x, uu, h, sc) => {
    const u = h / 2, rh = u * 0.7, low = rh * 2;
    g.vgrad(x[0], 0, x[1], low, 0xFF2766FF, 0xFF1A50DD);
    logoIn(g, logoOf('LIGUE1', d), x[0], 0, x[1], low, u * 0.24, '#fff');
    g.rect(x[0], low, x[1], h, 0xFFF4F4F4);
    clock(g, d, x[0], low, x[1], h, 0, 0xFF111111, (h - low) * 0.72, F.mont7);
    g.rect(x[1], 0, x[2], low, 0xF2222428);
    g.rect(x[1] + u * 0.15, rh - u * 0.008, x[2] - u * 0.1, rh + u * 0.008, 0xFF3D4046);
    words(g, d.home, x[1] + u * 0.22, rh / 2, u * 0.46, x[2] - x[1] - u, INK, F.mont7, -1);
    words(g, d.away, x[1] + u * 0.22, rh * 1.5, u * 0.46, x[2] - x[1] - u, INK, F.mont7, -1);
    const sx = x[2] - u * 0.38;
    if (sc) {
      scoreOne(g, sc[0], sx, rh / 2, u * 0.48, INK, F.mont7);
      scoreOne(g, sc[1], sx, rh * 1.5, u * 0.48, INK, F.mont7);
    } else words(g, d.score, sx, rh, u * 0.3, u * 0.7, INK, F.mont7, 0);
    g.rect(x[3], 0, x[4], rh, solid(d.homeColour));
    g.rect(x[3], rh, x[4], low, solid(d.awayColour));
  };
  // an arrow pointing right down the board's height: its back's corners at a and b, its point at tip, its inner corner at in
  function arrowD(x0, h, a, b, tip, inn) {
    const t = h * 0.03, bt = h * 0.97, cy = h / 2;
    return P([[x0 + a, t], [x0 + b, t], [x0 + tip, cy], [x0 + b, bt], [x0 + a, bt], [x0 + inn, cy]]);
  }
  L.R2004 = (g, d, x, u, h, sc) => {
    const rows = [u * 0.2, u * 0.68, u * 0.74, u * 1.22], nl = x[0] + u * 0.9;
    for (let i = 0; i < 2; i++) {
      const t = rows[i * 2], b = rows[i * 2 + 1], cy = (t + b) / 2;
      g.vgrad(nl, t, x[2], b, 0xFF5E636A, 0xFF2B2E33);
      g.rect(nl, t, x[2], t + u * 0.02, 0x59FFFFFF);
      smallCaps(g, i === 0 ? d.homeName : d.awayName, x[1] + u * 0.1, cy, u * 0.45, x[2] - x[1] - u * 0.2, INK, F.bsemi7);
      g.rect(x[2] + u * 0.04, t, x[3], b, 0xFF2B2E33);
      g.vgrad(x[2] + u * 0.07, t + u * 0.03, x[3] - u * 0.03, b - u * 0.03, 0xFFFFFFFF, 0xFFE2E4E8);
      if (sc) scoreOne(g, sc[i], (x[2] + x[3]) / 2 + u * 0.02, cy, u * 0.475, 0xFF111111, F.bsemi7);
    }
    if (!sc) words(g, d.score, (x[2] + x[3]) / 2, (rows[0] + rows[3]) / 2, u * 0.3, x[3] - x[2], INK, F.bsemi7, 0);
    const cw = Math.max(u * 1.34, tw(d.clock, u * 0.475, F.bsemi7) + u * 0.5), cl = x[0] + u * 0.85, ct = u * 1.3, cb = u * 1.84;
    g.vgrad(cl, ct, cl + cw, cb, 0xFFF4F5F7, 0xFFC8CCD2);
    words(g, d.clock, cl + cw / 2, (ct + cb) / 2, u * 0.475, cw - u * 0.2, 0xFF1E1E1E, F.bsemi7, 0, { cls: 'bclock' });
    g.path(arrowD(x[0], h, 0, u * 0.4, u * 1.02, u * 0.62), g.grad(x[0], 0, x[0] + u * 1.02, 0, [0xFFFFFFFF, 0xFFEAD9A6, 0xFFA3ABB7]));
    g.path(arrowD(x[0], h, u * 0.42, u * 0.66, u * 1.28, u * 1.04), g.grad(x[0] + u * 0.42, 0, x[0] + u * 1.28, 0, [0xFF1D2A69, 0xFF2F45A3]));
  };
  function slashes(g, l, t, b, colour, light) {
    const sl = (b - t) * 0.35, sw = (b - t) * 0.12, gap = (b - t) * 0.07;
    for (let i = 0; i < 2; i++) {
      const a = l + i * (sw + gap);
      g.path(P([[a, t], [a + sw, t], [a + sw + sl, b], [a + sl, b]]), i === 0 ? colour : light ? 0xFF9AA6BC : 0xFFFFFFFF);
    }
  }
  function swoop(g, x0, t, b, colour, dir) {
    const hgt = b - t, wdt = hgt * 0.28, mid = (t + b) / 2, s0 = x0 + wdt * (dir > 0 ? 1.2 : 0), s1 = x0 + wdt * (dir > 0 ? 1.7 : 0.5);
    g.path('M' + nn(s0) + ' ' + nn(t) + 'Q' + nn(x0 - wdt * dir * 0.6) + ' ' + nn(mid) + ' ' + nn(s0) + ' ' + nn(b) + 'L' + nn(s1) + ' ' + nn(b) +
      'Q' + nn(x0 + wdt * dir * -0.05) + ' ' + nn(mid) + ' ' + nn(s1) + ' ' + nn(t) + 'Z', colour);
  }
  // 2008's and 2012's blade at the start, with the ball that holds the logo
  function blade(g, d, x0, bt, bb, u) {
    g.path(P([[x0, bt], [x0 + u * 1.95, bt], [x0 + u * 1.95, bb + u * 0.6], [x0 + u * 0.95, bb + u * 0.6]]), g.grad(x0, 0, x0 + u * 1.95, 0, [0xFF2D3E8F, 0xFF121C4E]));
    const bx = x0 + u * 1.78, by = (bt + bb + u * 0.6) / 2, br = u * 0.84;
    g.circle(bx, by, br, g.rgrad(bx - br * 0.3, by - br * 0.3, br * 1.4, [0xFFFFFFFF, 0xFFE3EDF8, 0xFF9DB8DF]));
    logoIn(g, logoOf('R2008', d), bx - br * 0.68, by - br * 0.68, bx + br * 0.68, by + br * 0.68, 0, null);
  }
  function r2008(g, d, x, u, sc, y2012) {
    const bt = u * R8_TOP, bb = bt + u, cy = (bt + bb) / 2, right = x[x.length - 1], hc = solid(d.homeColour), ac = solid(d.awayColour);
    const barD = P([[x[0] + u * 1.7, bt], [right, bt], [right - u * 0.4, bb], [x[0] + u * 1.7, bb]]);
    g.path(barD, y2012 ? g.grad(0, bt, 0, bb, [0xFFFCFDFE, 0xFFE9ECF1, 0xFFC9CFD9]) : g.grad(0, bt, 0, bb, [0xFF28377A, 0xFF101A48]));
    const ink = y2012 ? 0xFF16245C : INK;
    words(g, d.homeName, (x[1] + x[2]) / 2, cy, u * 0.775, x[2] - x[1] - u * 0.3, ink, F.bsemi7, 0);
    words(g, d.awayName, (x[5] + x[6]) / 2, cy, u * 0.775, x[6] - x[5] - u * 0.3, ink, F.bsemi7, 0);
    slashes(g, x[2] + u * 0.08, bt, bb, hc, y2012);
    slashes(g, x[4] + u * 0.04, bt, bb, ac, y2012);
    g.round(x[3], bt + u * 0.1, x[4], bb - u * 0.1, y2012 ? u * 0.16 : u * 0.03, g.grad(0, bt + u * 0.1, 0, bb - u * 0.1, [0xFFD42A38, 0xFF931521]));
    const mid = (x[3] + x[4]) / 2;
    if (sc) {
      const half = tw(sc[0] + '-', u * 0.868, F.bsemi7) * 0.5 + u * 0.05;
      scoreOne(g, sc[0], mid - half, cy, u * 0.868, INK, F.bsemi7);
      words(g, '-', mid, cy, u * 0.868, 0, INK, F.bsemi7, 0);
      scoreOne(g, sc[1], mid + half, cy, u * 0.868, INK, F.bsemi7);
    } else words(g, d.score, mid, cy, u * 0.62, x[4] - x[3], INK, F.bsemi7, 0);
    if (y2012) {
      swoop(g, x[1] - u * 0.05, bt, bb, 0xFFD42A38, 1);
      swoop(g, right - u * 0.28, bt, bb, 0xFF2E5FC9, -1);
    }
    blade(g, d, x[0], bt, bb, u);
    const cw = Math.max(u * 1.9, tw(d.clock, u * 0.775, F.bsemi7) + u * 0.7), cl = x[1] - u * 0.05;
    g.round(cl, bb - u * 0.08, cl + cw, bb + u * 0.78, u * 0.3, 0xFF16224F);
    g.rect(cl, bb - u * 0.08, cl + cw, bb + u * 0.2, 0xFF16224F);
    words(g, d.clock, cl + cw / 2, bb + u * 0.36, u * 0.775, cw - u * 0.3, INK, F.bsemi7, 0, { cls: 'bclock' });
  }
  L.R2008 = (g, d, x, u, h, sc) => r2008(g, d, x, u, sc, false);
  L.R2012 = (g, d, x, u, h, sc) => r2008(g, d, x, u, sc, true);
  L.R2016 = (g, d, x, u, h, sc) => {
    const bt = u * R16_TOP, bb = bt + u, cy = (bt + bb) / 2, right = x[x.length - 1], edge = u * 0.06;
    g.rect(x[0], bt, x[0] + edge, bb, solid(d.homeColour));
    g.rect(x[0] + edge, bt, x[1], bb, 0xFFFFFFFF);
    g.rect(x[1], bt, x[2], bb, 0xFF38003C);
    words(g, d.home, (x[1] + x[2]) / 2, cy, u * 0.78, x[2] - x[1] - u * 0.3, INK, F.rubik6, 0);
    g.rect(x[2], bt, x[3], bb, 0xFF00FF85);
    const mid = (x[2] + x[3]) / 2;
    if (sc) {
      const half = tw(sc[0] + '-', u * 0.84, F.rubik6) * 0.5 + u * 0.04;
      scoreOne(g, sc[0], mid - half, cy, u * 0.84, 0xFF136B36, F.rubik6);
      words(g, '-', mid, cy, u * 0.84, 0, 0xFF136B36, F.rubik6, 0);
      scoreOne(g, sc[1], mid + half, cy, u * 0.84, 0xFF136B36, F.rubik6);
    } else words(g, d.score, mid, cy, u * 0.63, x[3] - x[2], 0xFF136B36, F.rubik6, 0);
    g.rect(x[3], bt, x[4], bb, 0xFF38003C);
    words(g, d.away, (x[3] + x[4]) / 2, cy, u * 0.78, x[4] - x[3] - u * 0.3, INK, F.rubik6, 0);
    g.rect(x[4], bt, right - edge, bb, 0xFFFFFFFF);
    g.rect(right - edge, bt, right, bb, solid(d.awayColour));
    logoIn(g, logoOf('R2016', d), x[2], 0, x[3], bt + u * 0.04, 0, null);
    g.rect(x[2], bb, x[3], bb + u * 0.6, 0xFFFFFFFF);
    words(g, d.clock, mid, bb + u * 0.3, u * 0.66, x[3] - x[2] - u * 0.3, 0xFF1A1A1A, F.rubik6, 0, { cls: 'bclock' });
  };
  // a soft shadow falling under a block's foot (t), depth deep
  function softShadow(g, l, t, r, depth) {
    if (depth > 0) g.rect(l + depth * 0.3, t, r - depth * 0.3, t + depth, g.grad(0, t, 0, t + depth, [0x47000000, 0x00000000]));
  }
  L.FLOODLIGHT = (g, d, x, u, h, sc) => {
    const rowR = x[3], rad = u * 0.11;
    softShadow(g, x[0], u, rowR, u * 0.22);
    g.round(x[0], 0, rowR, u, rad, FLOOD);
    g.rect(x[1], 0, x[2], u, LAB_DARK);
    for (let side = 0; side < 2; side++) {
      const l = side === 0 ? x[0] : x[2], rr = side === 0 ? x[1] : x[3], cw = rr - l;
      words(g, side === 0 ? d.home : d.away, (l + rr) / 2, u * 0.48, u * 0.44, cw - u * 0.3, FLOOD_INK, F.outfit, 0, { w: W800, em: 0.04 });
      g.rect(l + cw * 0.18, u - u * 0.085, rr - cw * 0.18, u, solid(side === 0 ? d.homeColour : d.awayColour));
    }
    digits(g, d, scoresOf(d.score), x[1], x[2], u / 2, u * 0.44, INK, F.outfit, W800, 0.04, '–', 0.7);
    const cp = d.compShort, ck = d.clock || '', tt = u * 1.08, tb = u * 1.6, pad = u * 0.28;
    const cw = ck ? lw(clockSample(d), u * 0.36, F.mono, W760, 0) : 0, pw = cp ? lw(cp, u * 0.3, F.outfit, W600, 0.1) : 0;
    if (cw <= 0 && pw <= 0) return;
    const tabW = pad * 2 + cw + (cw > 0 && pw > 0 ? u * 0.22 : 0) + pw;
    g.info = { tab: [x[0], tt, Math.min(x[x.length - 1], x[0] + tabW), tb] };
    g.round(x[0], tt, Math.min(x[x.length - 1], x[0] + tabW), tb, rad, 0xD1080A10);
    const cy = (tt + tb) / 2;
    let at = x[0] + pad;
    if (cw > 0) { words(g, ck, at, cy, u * 0.36, cw * 1.05, INK, F.mono, -1, { w: W760, cls: 'bclock' }); at += cw + u * 0.22; }
    if (pw > 0) words(g, cp, at, cy, u * 0.3, 0, YELLOW, F.outfit, -1, { w: W600, em: 0.1 });
  };
  L.SPLITKIT = (g, d, x, u, h, sc) => {
    const cut = u * 0.35, rad = u * 0.15, cy = u / 2, num = numW(d, u * 0.55), hc = solid(d.homeColour), ac = solid(d.awayColour);
    lit(g, 'M' + nn(x[0] + rad) + ' 0L' + nn(x[1]) + ' 0L' + nn(x[1] - cut) + ' ' + nn(u) + 'L' + nn(x[0] + rad) + ' ' + nn(u) +
      'Q' + nn(x[0]) + ' ' + nn(u) + ' ' + nn(x[0]) + ' ' + nn(u - rad) + 'L' + nn(x[0]) + ' ' + nn(rad) + 'Q' + nn(x[0]) + ' 0 ' + nn(x[0] + rad) + ' 0Z', hc, 0, u);
    lit(g, P([[x[1], 0], [x[2], 0], [x[2], u], [x[1] - cut, u]]), ac, 0, u);
    block(g, x[2], 0, x[3], u, LAB_DARK, rad, false, true);
    const hi = inkOn(hc), ai = inkOn(ac), hn = x[1] - u * 0.7 - num / 2, an = x[1] + u * 0.4 + num / 2;
    words(g, d.home, x[0] + u * 0.5, cy, u * 0.42, hn - num / 2 - x[0] - u * 0.7, hi, F.outfit, -1, { w: W800, em: 0.04 });
    words(g, d.away, x[2] - u * 0.5, cy, u * 0.42, x[2] - u * 0.7 - (an + num / 2), ai, F.outfit, 1, { w: W800, em: 0.04 });
    if (sc) {
      scoreOne(g, sc[0], hn, cy, u * 0.55, hi, F.outfit, { w: W800 });
      scoreOne(g, sc[1], an, cy, u * 0.55, ai, F.outfit, { w: W800 });
    }
    words(g, splitClock(d, false), (x[2] + x[3]) / 2, cy, u * 0.36, x[3] - x[2] - u * 0.3, INK, F.mono, 0, { w: W760, cls: 'bclock' });
  };
  L.TICKER = (g, d, x, u, h, sc) => {
    const right = x[5], rad = u * 0.1, cy = u / 2;
    g.round(x[0], 0, right, u, rad, 0xDB080A10);
    g.path(blockD(x[0], 0, x[1], u, rad, true, false), g.grad(x[0], 0, x[1], 0, [VIOLET, CORAL]));
    const cp = d.compShort;
    if (cp) words(g, cp, (x[0] + x[1]) / 2, cy, u * 0.5, x[1] - x[0] - u * 0.3, INK, F.outfit, 0, { w: W800, em: 0.06 });
    else houseMark(g, (x[0] + x[1]) / 2 - u * 0.28, cy - u * 0.28, u * 0.56, INK, 0xFF8C3FB0);
    words(g, d.home, (x[1] + x[2]) / 2, cy, u * 0.5, x[2] - x[1] - u * 0.3, INK, F.outfit, 0, { w: W760, em: 0.06 });
    g.rect(x[2], 0, x[3], u, 0xFFFFFFFF);
    digits(g, d, scoresOf(d.score), x[2], x[3], cy, u * 0.5, LAB_DARK, F.outfit, W800, 0.06, '–', 1);
    words(g, d.away, (x[3] + x[4]) / 2, cy, u * 0.5, x[4] - x[3] - u * 0.3, INK, F.outfit, 0, { w: W760, em: 0.06 });
    words(g, minuteMark(d.clock), (x[4] + right) / 2, cy, u * 0.46, right - x[4] - u * 0.3, 0xFFCFD5E4, F.mono, 0, { w: W760, cls: 'bmin' });
  };
  L.DUO = (g, d, x, u, h, sc) => {
    const hc = solid(d.homeColour), ac = solid(d.awayColour), disc = u * 0.78, cy = u / 2;
    for (let side = 0; side < 2; side++) {
      const l = side === 0 ? x[0] : x[4], rr = side === 0 ? x[1] : x[5], colour = side === 0 ? hc : ac;
      g.round(l, 0, rr, u, u / 2, 0xD1080A10);
      const dcx = side === 0 ? rr - u * 0.11 - disc / 2 : l + u * 0.11 + disc / 2;
      g.circle(dcx, cy, disc / 2, colour);
      if (side === 0) words(g, d.home, l + u * 0.35, cy, u * 0.4, dcx - disc / 2 - l - u * 0.5, INK, F.outfit, -1, { w: W760, em: 0.04 });
      else words(g, d.away, rr - u * 0.35, cy, u * 0.4, rr - u * 0.5 - (dcx + disc / 2), INK, F.outfit, 1, { w: W760, em: 0.04 });
      if (sc) scoreOne(g, sc[side], dcx, cy, u * 0.44, inkOn(colour), F.outfit, { w: W800 });
    }
    const ch = u * 0.76, ct = (u - ch) / 2;
    g.round(x[2], ct, x[3], ct + ch, ch / 2, 0xFFFFFFFF);
    words(g, splitClock(d, false), (x[2] + x[3]) / 2, cy, u * 0.36, x[3] - x[2] - u * 0.3, LAB_DARK, F.mono, 0, { w: W760, cls: 'bclock' });
  };
  // a fine dark dot matrix over l..r, t..b (round-cornered as the panel): clear dots in a dark grid, its cell as CompBug's
  function dotMatrix(g, l, t, r, b, u, rad) {
    const cell = Math.max(3, Math.round(u / 15)), id = 'dm' + (gid++), c = cell / 2, rr = 0.555 * cell / Math.SQRT2;
    g.d.push('<pattern id="' + id + '" width="' + cell + '" height="' + cell + '" patternUnits="userSpaceOnUse"><path fill-rule="evenodd" fill="rgba(0,0,0,.549)" d="M0 0H' +
      cell + 'V' + cell + 'H0Z' + 'M' + nn(c - rr) + ' ' + nn(c) + 'a' + nn(rr) + ' ' + nn(rr) + ' 0 1 0 ' + nn(2 * rr) + ' 0a' + nn(rr) + ' ' + nn(rr) +
      ' 0 1 0 ' + nn(-2 * rr) + ' 0Z"/></pattern>');
    g.round(l, t, r, b, rad, 'url(#' + id + ')');
  }
  L.LED = (g, d, x, u, h, sc) => {
    const right = x[4], rad = u * 0.13, edge = Math.max(1, u * 0.045), cy = u / 2, gap = u * 0.3, pad = u * 0.26;
    g.round(x[0], 0, right, u, rad, 0xFF1A1A1A);
    g.round(x[0] + edge, edge, right - edge, u - edge, rad - edge, 0xFF050505);
    const amber = { w: W760, em: 0.12, halo: AMBER };
    words(g, d.home, (x[0] + pad + x[1] - gap / 2) / 2, cy, u * 0.44, 0, AMBER, F.mono, 0, amber);
    digits(g, d, scoresOf(d.score), x[1] + gap / 2, x[2] - gap / 2, cy, u * 0.62, AMBER, F.mono, W760, 0.12, '-', 1, { halo: AMBER });
    words(g, d.away, (x[2] + gap / 2 + x[3] - gap / 2) / 2, cy, u * 0.44, 0, AMBER, F.mono, 0, amber);
    words(g, minuteMark(d.clock), (x[3] + gap / 2 + right - pad) / 2, cy, u * 0.44, right - x[3] - pad, LED_RED, F.mono, 0,
      { w: W760, em: 0.12, halo: LED_RED, cls: 'bmin' });
    dotMatrix(g, x[0], 0, right, u, u, rad);
  };

  // the board in look k with d, w by h (its own grounds), as one SVG; with its blocks' edges (x) and row's height (u)
  function drawBoard(k, d, w, h) {
    return asBoard(() => {
      const u = h / tallOf(k), s = segs(k, d, u), total = sum(s), kx = total > 0 ? w / total : 1, x = [0];
      for (let i = 0; i < s.length; i++) x.push(x[i] + s[i] * kx);
      const g = new Svg();
      (L[k] || L.LIGUE1)(g, d, x, u, h, scoresOf(d.score), w);
      return { g, x, u, w, h, k };
    });
  }


  // ---------- the 3D kits' board (OverlayBug/BoardBug.kitsRow), its shirts flat in their kit colours (KitSheets.flatShirt) ----------
  const KIT_NAVY = 0xFF10185A, KIT_COMP0 = 0xFF1B2A8F, KIT_COMP1 = 0xFF0A1250, KIT_CELL0 = 0xFF24359E, KIT_CELL1 = 0xFF0D1660,
    KIT_CODE = 0xFFF4F5FB, KIT_INK = 0xFF0E1550, KIT_CLOCK = 0xFF070B2C, KIT_WHITE = 0xFFE6E9F2;
  // the board's lettering: the TV's Outfit 800; the phone's board draws its own in the system's bold ("sans")
  const kitFont = () => (C.boardFont === 'sans' ? ['sans-serif', 700] : ['Outfit', 800]);
  const metrics = {};
  // OverlayBug.words: centred on cx, in the middle of a row h high by the face's ascent and descent
  function kitWords(g, s, cx, h, size, colour, cls) {
    if (!s || size <= 1) return;
    const f = kitFont(), key = f[0] + f[1];
    if (!metrics[key]) {
      if (!capCtx) capCtx = document.createElement('canvas').getContext('2d');
      capCtx.font = f[1] + ' 200px ' + f[0];
      const m = capCtx.measureText('H');
      metrics[key] = [(m.fontBoundingBoxAscent || 200) / 200, (m.fontBoundingBoxDescent || 52) / 200];
    }
    const [a, de] = metrics[key], base = h / 2 + (a - de) * size / 2;
    if (!droid) { g.text(s, cx, base, size, f, f[1], 0, colour, 'middle', cls); return; }
    // on the board: each glyph where Android puts it, centred on cx as its Paint.Align.CENTER sets it
    const L = droidText(s, size, f, f[1], 0), left = cx - L.w / 2;
    g.raw('<text x="' + L.xs.map((v) => nn(left + v)).join(' ') + '" y="' + nn(base) + '" font-family="' + f[0] + '" font-weight="' + f[1] +
      '" font-size="' + nn(size) + '" fill="' + A(colour) + '"' + (cls ? ' class="' + cls + '"' : '') + '>' + esc(s) + '</text>');
  }
  function kitParts(d, h) {
    const f = kitFont();
    const code = Math.max(h * 1.5, Math.max(tw(d.home, h * 0.5, f), tw(d.away, h * 0.5, f)) + h * 0.5);
    const clk = !d.clock ? h * 1.2 : Math.max(h * 1.75, tw('88:88', h * 0.48, f) + h * 0.6);
    return { comp: h, kit: h * 1.45, code, score: h * 2, clock: clk };
  }
  const kitsNatural = (d, h) => asBoard(() => { const p = kitParts(d, h); return p.comp + 2 * p.kit + 2 * p.code + p.score + p.clock; });
  // a shirt drawn flat (KitSheets.flatShirt's outline): its body in the kit's colour, edged in its trim; turned a little
  function shirtD(cx, cy, size, turn) {
    const w = size * 0.92 * Math.max(0.12, Math.abs(Math.cos(turn))), h = size * 0.78, x0 = cx - w / 2, y0 = cy - h / 2;
    const pts = [[0.40, 0], [0.6, 0], [0.79, 0.05], [1, 0.27], [0.9, 0.39], [0.76, 0.29], [0.76, 1], [0.24, 1], [0.24, 0.29], [0.1, 0.39], [0, 0.27], [0.21, 0.05]];
    return P(pts.map((p) => [x0 + p[0] * w, y0 + p[1] * h]));
  }
  function flatShirt(g, cx, cy, size, body, trim, turn) {
    g.path(shirtD(cx, cy, size, turn), body, ' stroke="' + A(trim) + '" stroke-width="' + nn(Math.max(1, size * 0.04)) + '" stroke-linejoin="round"');
  }
  function kitsBoard(d, w, h) {
    return asBoard(() => kitsRow(d, w, h));
  }
  function kitsRow(d, w, h) {
    const p = kitParts(d, h), need = kitsNatural(d, h), k = need > w + 1 ? w / need : 1;
    const compW = h * k, kit = h * 1.45 * k, code = p.code * k, score = h * 2 * k, clk = Math.max(0, w - (compW + 2 * kit + 2 * code + score));
    const g = new Svg(), id = 'kc' + (gid++), rad = Math.min(h * 0.08, C.boardFont === 'sans' ? 8 * h / 30 : h);
    g.d.push('<clipPath id="' + id + '"><rect width="' + nn(w) + '" height="' + nn(h) + '" rx="' + nn(rad) + '"/></clipPath>');
    g.raw('<g clip-path="url(#' + id + ')">');
    g.rect(0, 0, w, h, KIT_NAVY);
    let x = 0;
    g.vgrad(x, 0, x + compW, h, KIT_COMP0, KIT_COMP1);
    const ls = Math.min(compW, h) * 0.72;
    if (d.logo) logoIn(g, d.logo, x + compW * 0.14, h * 0.14, x + compW * 0.14 + ls, h * 0.14 + ls, 0, null);
    else kitWords(g, d.compShort, x + compW / 2, h, h * 0.3, KIT_WHITE);
    x += compW;
    const homeAt = x;
    g.vgrad(x, 0, x + kit, h, KIT_CELL0, KIT_CELL1);
    x += kit;
    g.rect(x, 0, x + code, h, KIT_CODE);
    kitWords(g, d.home, x + code / 2, h, h * 0.5 * k, KIT_INK);
    x += code;
    g.rect(x, 0, x + score, h, KIT_NAVY);
    kitWords(g, d.score.replace('-', '–'), x + score / 2, h, h * 0.56 * k, KIT_WHITE);
    x += score;
    g.rect(x, 0, x + code, h, KIT_CODE);
    kitWords(g, d.away, x + code / 2, h, h * 0.5 * k, KIT_INK);
    x += code;
    const awayAt = x;
    g.vgrad(x, 0, x + kit, h, KIT_CELL0, KIT_CELL1);
    x += kit;
    if (clk > 0) { g.rect(x, 0, x + clk, h, KIT_CLOCK); kitWords(g, d.clock, x + clk / 2, h, h * 0.44 * k, KIT_WHITE, 'bclock'); }
    // the shirts at rest in their cells: turned a little towards the middle, each its own way (kitRest)
    flatShirt(g, homeAt + kit / 2, h / 2, h * 1.22 * 0.8, d.homeBody, d.homeTrim, 0.35);
    flatShirt(g, awayAt + kit / 2, h / 2, h * 1.22 * 0.8, d.awayBody, d.awayTrim, -0.35);
    g.raw('</g>');
    return { g, x: [0, compW, compW + kit], u: h, w, h, k: 'KITS' };
  }

  // ---------- the new looks' shared parts: the board where the app's sits, and the ways in and out ----------
  // what the boards show: the clubs' codes (and the retro boards' names), colours, crests, the logos, the clock
  let BD = null, BB = null;
  // a club's colour on the board: the app's own (it sends it), else as the phone's board makes it from the club's two
  function boardColour(t) {
    if (t.board && /^#[0-9a-f]{6}$/i.test(t.board)) return argb(t.board, '#FFFFFF');
    const dk = (c) => lumi(c) < 50;
    const c = /^#[0-9a-f]{6}$/i.test(t.color || '') ? argb(t.color, '#000000') : 0, a = /^#[0-9a-f]{6}$/i.test(t.color2 || '') ? argb(t.color2, '#000000') : 0;
    if ((!c || dk(c)) && a && !dk(a)) return a;
    return !c ? 0xFFFFFFFF : dk(c) ? 0xFFDDE3FF : c;
  }
  function boardData() {
    const code = (t) => up(t.short || t.name).replace(/[^A-Z0-9À-Ý]/g, '').slice(0, 3);
    return {
      home: code(C.home), away: code(C.away), homeName: up(C.home.retro || C.home.name), awayName: up(C.away.retro || C.away.name),
      score: MO.score[0] + '-' + MO.score[1], clock: clockText(LEAD), comp: C.compName || '', compShort: C.compShort || '', title: C.compTitle || '',
      homeColour: boardColour(C.home), awayColour: boardColour(C.away), hcrest: M.hcrest, acrest: M.acrest, logo: M.compW, logoLight: M.compL,
      mark: M.compM, homeBody: argb(C.home.color, '#C8102E'), homeTrim: argb(C.home.color2, '#FFFFFF'), awayBody: argb(C.away.color, '#1B3F8F'),
      awayTrim: argb(C.away.color2, '#FFFFFF'),
    };
  }
  // the board in look k at the app's own size and place (else the standard place, 38 a row high, as wide as its words need)
  function placeBoard2(k) {
    BD = boardData();
    const tall = k === 'KITS' ? 1 : tallOf(k);
    const h = C.board ? RECT.h : Math.round(38 * tall);
    const w = C.board ? RECT.w : Math.round(k === 'KITS' ? kitsNatural(BD, h) : natural(k, BD, h));
    const B = k === 'KITS' ? kitsBoard(BD, w, h) : drawBoard(k, BD, w, h);
    const b = $('board');
    // (its drawing named, so a strap can show its first block large without drawing it twice)
    b.innerHTML = B.g.svg(0, 0, w, h, '', 'cbg') + '<i id="bedge" style="position:absolute;top:0;bottom:0;width:3px;background:#fff;box-shadow:0 0 12px 3px rgba(255,255,255,.7);opacity:0"></i>';
    BS = 1; BXd = RECT.x; BYd = RECT.y;
    b.style.left = BXd + 'px'; b.style.top = BYd + 'px'; b.style.width = w + 'px'; b.style.height = h + 'px';
    b.style.transformOrigin = '0 0'; b.style.transform = 'none'; b.style.borderRadius = '0';
    BB = { k, x: B.x, u: B.u, w, h, first: B.x[1], bar: k === 'KITS' ? [0, h] : barOf(k, h), info: B.g.info || {} };
  }
  // the board opening out of its first block behind a bright edge (as the app's own does: BoardBug.shownPart)
  function openBoard2(t, t0, t1) {
    const b = $('board'), p = prog(t, t0, t1);
    if (p <= 0) { b.style.opacity = 0; return; }
    b.style.opacity = 1;
    const r = eo(p), shown = BB.first + (BB.w - BB.first) * r;
    b.style.clipPath = r < 1 ? 'inset(0 ' + (BB.w - shown) + 'px 0 0)' : 'none';
    const e = $('bedge');
    if (e) { e.style.left = (shown - 3) + 'px'; e.style.opacity = r > 0 && r < 1 ? 1 : 0; }
  }
  const boardRest = (t) => {
    // before its moment the board waits unseen (a restart's and the second half's step aside in drawFrame first)
    const b = $('board');
    b.style.opacity = 0; b.style.transform = 'none'; b.style.clipPath = 'none';
  };
  // Classic's way for a strap that starts with its board's own first block (seed wide, the strap sh high, its top at y):
  // that block opens out of a line and the rest wipes out of it behind a bright edge; to fold, the rest wipes back into it,
  // it flies onto the board's own first block and the board opens out of it the same way
  function collapse(t, seed, sh, y) {
    const st = $('strap');
    st.style.left = X + 'px'; st.style.top = y + 'px';
    const first = eo(prog(t, IN0, IN0 + 0.22));
    let rest = eo(prog(t, IN0 + 0.2, IN1));
    if (t > OUT0) rest = 1 - eio(prog(t, OUT0, OUT0 + 0.3));
    const fly = eio(prog(t, OUT0 + 0.26, OUT1)), half = sh / 2 * (1 - first), shown = seed + (W - seed) * rest;
    st.style.clipPath = 'inset(' + half + 'px ' + (W - shown) + 'px ' + half + 'px 0)';
    st.style.opacity = t < IN0 ? 0 : 1 - prog(t, OUT1 - 0.04, OUT1);
    const e = $('sedge');
    if (e) { e.style.left = (shown - 4) + 'px'; e.style.opacity = rest > 0.002 && rest < 0.998 ? 1 : 0; }
    st.style.transformOrigin = '0 0';
    st.style.transform = fly > 0 ? 'translate(' + (BXd - X) * fly + 'px,' + (BYd - y) * fly + 'px) scale(' + lerp(1, BB.h / sh, fly) + ')' : 'none';
    if (t < OUT1 - 0.04) boardRest(t);
    else openBoard2(t, OUT1 - 0.04, OUT1 + 0.5);
  }
  // the strap squeezing in to its board's shape as the board grows in its place (its row on the strap's row, row: [top,
  // bottom] on the stage, about the strap's middle), then flying home; returns how much of the strap is still to be seen
  function growOut(t, row) {
    const st = $('strap'), b = $('board');
    const sq = eio(prog(t, OUT0, OUT0 + 0.32)), fly = eio(prog(t, OUT0 + 0.26, OUT1));
    const rowB = BB.bar[1] - BB.bar[0], S0 = Math.min((row[1] - row[0]) / rowB, W * 0.86 / BB.w);
    const x0 = X + W / 2 - BB.w * S0 / 2, y0 = (row[0] + row[1]) / 2 - (BB.bar[0] + rowB / 2) * S0;
    if (t >= OUT0) {
      const s = lerp(S0, 1, fly), bx = lerp(x0, BXd, fly), by = lerp(y0, BYd, fly);
      b.style.transform = 'translate(' + (bx - BXd) + 'px,' + (by - BYd) + 'px) scale(' + s + ')';
      b.style.opacity = prog(t, OUT0 + 0.08, OUT0 + 0.24);
      b.style.clipPath = 'none';
    } else boardRest(t);
    const side = Math.max(0, (W - lerp(W, BB.w * S0, sq)) / 2);
    st.style.clipPath = sq > 0 ? 'inset(-400px ' + side + 'px -400px ' + side + 'px)' : 'none';
    return 1 - prog(t, OUT0 + 0.12, OUT0 + 0.3);
  }
  // a panel of the strap becoming one of the board's: its box (on the stage) moves and shrinks onto the board's part
  // (bx, by, bw, bh in the board, radius br), its words gone first; returns how far it is (0 to 1)
  function morph(t, el, from, bx, by, bw, bh, br) {
    const m = eio(prog(t, OUT0, OUT1));
    const x = lerp(from.x, BXd + bx, m), y = lerp(from.y, BYd + by, m), w = lerp(from.w, bw, m), h = lerp(from.h, bh, m);
    el.style.left = x + 'px'; el.style.top = y + 'px'; el.style.width = w + 'px'; el.style.height = h + 'px';
    el.style.borderRadius = lerp(from.r, br, m) + 'px';
    return m;
  }
  // the board seen as the strap's panels land on it
  function boardIn(t, t0, t1) {
    const b = $('board');
    b.style.transform = 'none'; b.style.clipPath = 'none';
    b.style.opacity = prog(t, t0, t1);
  }
  // the moment's words for the straps: the clubs' full names, the moment, the score
  const FULL = { home: up(C.home.name), away: up(C.away.name) };
  // a strap's own drawing (its parts each an SVG over the box l, t, w, h of the strap's own coordinates)
  function svgPart(id, g, l, t, w, h, style) {
    return '<div id="' + id + '" class="part" style="left:' + nn(l) + 'px;top:' + nn(t) + 'px;width:' + nn(w) + 'px;height:' + nn(h) + 'px;' + (style || '') + '">' +
      g.svg(l, t, w, h) + '</div>';
  }
  const EDGE = '<i id="sedge" style="position:absolute;top:0;bottom:0;width:4px;background:#fff;box-shadow:0 0 18px 4px rgba(255,255,255,.7);opacity:0"></i>';
  // a light passing over a part once (a soft white streak, moved across it)
  const sweepHTML = (id, h, op) => '<i id="' + id + '" style="position:absolute;top:-10%;height:120%;width:200px;background:linear-gradient(90deg,rgba(255,255,255,0),rgba(255,255,255,' +
    (op || 0.22) + '),rgba(255,255,255,0));transform:skewX(-20deg);opacity:0;pointer-events:none"></i>';
  function sweep(id, t, t0, t1, w) {
    const e = $(id), p = prog(t, t0, t1);
    if (!e) return;
    e.style.left = lerp(-260, w + 80, eio(p)) + 'px';
    e.style.opacity = p > 0 && p < 1 ? 1 : 0;
  }
  // the label and the score set in a block: the moment small and spaced over the score
  function labelScore(g, cx, l, r, top, bot, f, labelInk, scoreInk, labelSize, scoreSize, o) {
    const span = bot - top, ly = top + span * 0.27, sy = top + span * 0.64;
    words(g, MO.label, cx, ly, labelSize, r - l - 16, labelInk, o && o.lf ? o.lf : f, 0, { em: 0.2, w: o && o.lw });
    words(g, MO.score[0] + (o && o.dash ? o.dash : ' – ') + MO.score[1], cx, sy, scoreSize, r - l - 20, scoreInk, f, 0, { w: o && o.sw, em: o && o.sem });
  }
  // the label and the ticking clock set in a block
  function labelClock(g, cx, l, r, top, bot, f, labelInk, clockInk, labelSize, clockSize, o) {
    const span = bot - top;
    words(g, MO.label, cx, top + span * 0.3, labelSize, r - l - 14, labelInk, o && o.lf ? o.lf : f, 0, { em: 0.2, w: o && o.lw });
    words(g, clockText(LEAD), cx, top + span * 0.66, clockSize, r - l - 14, clockInk, o && o.cf ? o.cf : f, 0, { cls: 'sslot', w: o && o.cw });
  }
  // the subline under a strap whose foot is not at the standard place
  function subAt(t, left, width, top) {
    subRender(t, left, width);
    const sl = $('subline');
    if (top != null) sl.style.top = top + 'px';
  }


  // ---------- the straps' own pieces ----------
  // a crest, or a coin in the club's colour where it will not come (as the first nine's)
  function crestOr(g, home, l, t, size) {
    const url = home ? M.hcrest : M.acrest;
    if (url && IMG[url]) { crestIn(g, url, l, t, size); return; }
    const c = home ? BD.homeColour : BD.awayColour, r = size * 0.43;
    g.circle(l + size / 2, t + size / 2, r, g.rgrad(l + size * 0.35, t + size * 0.3, r * 1.4, [lighter(c, 0.35), c, darker(c, 0.15)]));
    g.raw('<circle cx="' + nn(l + size / 2) + '" cy="' + nn(t + size / 2) + '" r="' + nn(r - 1) + '" fill="none" stroke="rgba(255,255,255,.35)" stroke-width="2"/>');
  }
  // a club's full name from x (align -1), about x (0) or up to x (1), narrower when long (never under 24 px, as the first nine's)
  const nameAt = (g, home, x, cy, size, room, ink, f, align, o) => words(g, home ? FULL.home : FULL.away, x, cy, size, room, ink, f, align, Object.assign({ min: 24 }, o));
  // the competition's trophy or emblem standing (its foot at b), with a soft shadow when it is a cut-out
  function standing(g, url, l, t, r, b, shadow) {
    const at = fit(url, l, t, r, b, 0, 1);
    if (!at) return;
    if (shadow) g.image(tinted(url, 'rgba(0,0,0,0.349)'), at.x + shadow, at.y + shadow, at.w, at.h);
    g.image(url, at.x, at.y, at.w, at.h);
  }
  const FLASH = (x) => '<i id="sflash" style="position:absolute;left:' + nn(x - 80) + 'px;top:-40px;width:160px;height:' + (H + 80) +
    'px;background:radial-gradient(closest-side,rgba(255,255,255,.95),rgba(255,255,255,0));opacity:0"></i>';
  // ways in: the first block out of a line, the rest wiped out of it behind a bright edge
  function wipeIn(t, id, first) {
    const e = $(id), f = eo(prog(t, IN0, IN0 + 0.22)), r = eo(prog(t, IN0 + 0.2, IN1));
    const half = H / 2 * (1 - f), shown = first + (W - first) * r;
    e.style.clipPath = 'inset(' + half + 'px ' + (W - shown) + 'px ' + half + 'px 0)';
    const ed = $('sedge');
    if (ed) { ed.style.left = (shown - 4) + 'px'; ed.style.opacity = r > 0.002 && r < 0.998 ? 1 : 0; }
  }
  // halves sliding in from either side to meet with a flash, the middle popping in after them
  function halvesIn(t) {
    const p = eo(prog(t, IN0, IN0 + 0.45));
    $('ph').style.transform = 'translateX(' + (-1400 * (1 - p)) + 'px)';
    $('pa').style.transform = 'translateX(' + (1400 * (1 - p)) + 'px)';
    const f = prog(t, IN0 + 0.42, IN0 + 0.62), fl = $('sflash');
    if (fl) fl.style.opacity = f > 0 ? Math.sin(Math.PI * f) : 0;
    const pm = $('pm');
    if (pm) pm.style.transform = 'scale(' + Math.max(0, back(prog(t, IN0 + 0.45, IN0 + 0.75))) + ')';
  }
  // rising in with a little bounce
  function riseIn(t, id) {
    const p = prog(t, IN0, IN0 + 0.6), e = $(id);
    e.style.transform = 'translateY(' + (150 * (1 - back(p))) + 'px)';
    e.style.opacity = Math.min(1, p * 3);
  }
  const popIn = (t, id, t0) => { $(id).style.transform = 'scale(' + Math.max(0, back(prog(t, t0, t0 + 0.35))) + ')'; };

  // A look whose strap starts with its board's own first block, shown large (the board's own drawing, not drawn again;
  // or, for the retro boards, drawn by the strap with the board's own pieces): Classic's way in and out (collapse)
  function f1(k, o) {
    return {
      moves: true, fonts: o.fonts,
      build() {
        placeBoard2(k);
        const sh = o.h || H, g = new Svg(), seed = o.ownSeed ? o.ownSeed(sh) : BB.first * sh / BB.h;
        if (!o.ownSeed) {
          g.raw('<svg x="0" y="0" width="' + nn(seed) + '" height="' + sh + '" viewBox="0 0 ' + nn(BB.first) + ' ' + nn(BB.h) +
            '" preserveAspectRatio="none" overflow="hidden"><use href="#cbg"/></svg>');
        }
        o.draw(g, seed, sh);
        const st = $('strap');
        st.innerHTML = '<div style="position:relative;width:' + W + 'px;height:' + sh + 'px;overflow:hidden;' + (o.round ? 'border-radius:' + o.round + 'px' : '') + '">' +
          g.svg(0, 0, W, sh) + (o.noSweep ? '' : sweepHTML('ssw', sh)) + EDGE + '</div>';
        st.style.width = W + 'px'; st.style.height = sh + 'px';
        $('subline').innerHTML = subHTML.apply(null, o.sub);
        this.seed = seed; this.sh = sh;
      },
      render(t) {
        const y = Y + H - this.sh;
        collapse(t, this.seed, this.sh, y);
        sweep('ssw', t, 1.7, 2.5, W);
        if (o.anim) o.anim(t);
        subAt(t, X + 40, 0, y + this.sh);
      },
    };
  }
  // A look whose board grows out of its strap as the strap squeezes in to it, then flies home (growOut)
  function f2(k, o) {
    return {
      moves: true, fonts: o.fonts,
      build() {
        placeBoard2(k);
        const st = $('strap');
        st.innerHTML = o.html();
        st.style.width = W + 'px'; st.style.height = H + 'px';
        $('subline').innerHTML = subHTML.apply(null, o.sub);
      },
      render(t) {
        const st = $('strap');
        st.style.left = X + 'px'; st.style.top = Y + 'px';
        o.in(t);
        st.style.opacity = t < IN0 ? 0 : growOut(t, [Y, Y + H]);
        subAt(t, X + 40, 0, Y + H + (o.bot || 0));
      },
    };
  }
  // A look whose strap's own panels move and shrink onto the board's (Glass's way): its words go first, the board shows in them
  function f3(k, o) {
    return {
      moves: true, fonts: o.fonts,
      build() {
        placeBoard2(k);
        this.panels = o.panels();
        const st = $('strap');
        st.innerHTML = this.panels.map((p) => '<div id="' + p.id + '" class="part" style="' + p.style + '">' + (p.inner || '') + '</div>').join('') + o.html();
        st.style.width = W + 'px'; st.style.height = H + 'px';
        $('subline').innerHTML = subHTML.apply(null, o.sub);
      },
      render(t) {
        const st = $('strap');
        st.style.left = X + 'px'; st.style.top = Y + 'px';
        st.style.opacity = t < IN0 ? 0 : 1;
        const m = eio(prog(t, OUT0, OUT1)), gone = 1 - prog(t, OUT1 - 0.02, OUT1 + 0.1);
        for (const p of this.panels) {
          const e = $(p.id), to = p.to(), f = p.from;
          const x = lerp(f.x, BXd - X + to[0], m), y = lerp(f.y, BYd - Y + to[1], m);
          e.style.left = x + 'px'; e.style.top = y + 'px';
          e.style.width = lerp(f.w, to[2], m) + 'px'; e.style.height = lerp(f.h, to[3], m) + 'px';
          const rr = lerp(f.r, to[4], m);
          e.style.borderRadius = p.square ? rr + 'px 0 0 ' + rr + 'px' : rr + 'px';
          if (p.edge) e.style.borderWidth = lerp(p.edge[0], p.edge[1], m) + 'px';
        }
        o.in(t);
        for (const p of this.panels) if (t >= OUT0) $(p.id).style.opacity = gone;
        const c = $('sc');
        if (c && t >= OUT0) c.style.opacity = 1 - prog(t, OUT0, OUT0 + 0.2);
        if (t < OUT1 - 0.16) boardRest(t);
        else boardIn(t, OUT1 - 0.16, OUT1 + 0.02);
        subAt(t, X + 40, 0, Y + H + (o.bot || 0));
      },
    };
  }

  // ---------- the competitions' looks ----------
  // Champions League: its starball's blue block (the board's own first block, large), the clock and the moment on white,
  // each club's crest and name on the deep blue bar between its colours, the score between the chevrons
  S.ucl = f1('UCL', {
    fonts: ['500 40px CBMont', '600 40px CBMont'], sub: ['#2133CF', '#fff', 'grad', '#2133CF'],
    draw(g, s, u) {
      const c1 = s + 150;
      g.vgrad(s, 0, c1, u, 0xFFFFFFFF, 0xFFE7EAF3);
      labelClock(g, (s + c1) / 2, s, c1, 0, u, F.mont5, 0xFF2133CF, 0xFF0D1854, 14, 42, { lf: F.mont6 });
      const a = c1 + u * 0.16, b0 = a + u * 0.26, b1 = W - u * 0.32, mid = (b0 + b1) / 2;
      g.rect(a, 0, b0, u, solid(BD.homeColour));
      g.hgrad(b0, 0, b1, u, [0xFF141B8C, 0xFF2A31C0, 0xFF141B8C]);
      crestOr(g, true, b0 + 18, (u - 80) / 2, 80);
      nameAt(g, true, b0 + 112, u / 2, 42, mid - 104 - (b0 + 112) - 14, INK, F.mont5, -1);
      chevron(g, mid - 92, u, 1, 0x40FFFFFF);
      chevron(g, mid + 92, u, -1, 0x40FFFFFF);
      scoreOne(g, MO.score[0], mid - 42, u / 2, 64, INK, F.mont5);
      scoreOne(g, MO.score[1], mid + 42, u / 2, 64, INK, F.mont5);
      nameAt(g, false, b1 - 112, u / 2, 42, b1 - 112 - (mid + 104) - 14, INK, F.mont5, 1);
      crestOr(g, false, b1 - 98, (u - 80) / 2, 80);
      g.rect(b1 + u * 0.06, 0, W, u, solid(BD.awayColour));
    },
  });
  // Europa and Conference League: the trophy in its ring standing free, the clock on black over the competition's line,
  // the clubs on dark grey, the scores in white boxes edged in the clubs' colours
  const uefaStrap = (accent) => function (g, s, u) {
    const rad = u * 0.07, c0 = s + u * 0.14, c1 = c0 + 176, box = 124, gm = 12, mid = (c1 + W) / 2;
    block(g, c0, 0, c1, u, 0xF20B0B0B, rad, true, false);
    g.rect(c0 + rad * 0.3, u * 0.925, c1, u, accent);
    labelClock(g, (c0 + c1) / 2, c0, c1, 0, u * 0.93, F.mont7, lighter(accent, 0.3), INK, 14, 44);
    const hb0 = mid - gm / 2 - box, ab1 = mid + gm / 2 + box, edge = u * 0.12;
    g.rect(c1, 0, hb0, u, 0xF2262626);
    crestOr(g, true, c1 + 18, (u - 76) / 2, 76);
    nameAt(g, true, c1 + 108, u / 2, 40, hb0 - 18 - (c1 + 108), INK, F.mont7, -1);
    g.vgrad(hb0, 0, mid - gm / 2, u, 0xFFF7F7F7, 0xFFE4E4E4);
    g.rect(hb0, 0, hb0 + edge, u, solid(BD.homeColour));
    g.rect(mid - gm / 2, 0, mid + gm / 2, u, 0xF21A1A1A);
    g.vgrad(mid + gm / 2, 0, ab1, u, 0xFFF7F7F7, 0xFFE4E4E4);
    g.rect(ab1 - edge, 0, ab1, u, solid(BD.awayColour));
    scoreOne(g, MO.score[0], (hb0 + edge + mid - gm / 2) / 2, u / 2, 84, DARK, F.mont7);
    scoreOne(g, MO.score[1], (mid + gm / 2 + ab1 - edge) / 2, u / 2, 84, DARK, F.mont7);
    block(g, ab1, 0, W, u, 0xF2262626, rad, false, true);
    nameAt(g, false, W - 108, u / 2, 40, W - 108 - 18 - ab1, INK, F.mont7, 1);
    crestOr(g, false, W - 94, (u - 76) / 2, 76);
  };
  S.uel = f1('UEL', { fonts: ['700 40px CBMont'], sub: ['#141517', '#fff', 'grad', '#141517'], noSweep: true, draw: uefaStrap(0xFFF26A1B) });
  S.uecl = f1('UECL', { fonts: ['700 40px CBMont'], sub: ['#141517', '#fff', 'grad', '#141517'], noSweep: true, draw: uefaStrap(0xFF27B34B) });
  // Scottish Premiership: the logo on its pale block, the clubs on their colours, the score and the clock on dark
  S.spfl = f1('SPFL', {
    fonts: ['600 40px CBInter'], sub: ['#151917', '#fff', 'grad', '#151917'],
    draw(g, s, u) {
      const ck = 170, c0 = W - ck, sw = 210, mid = (s + c0) / 2, hc = solid(BD.homeColour), ac = solid(BD.awayColour);
      g.vgrad(s, 0, mid - sw / 2, u, lighter(hc, 0.1), darker(hc, 0.12));
      crestOr(g, true, s + 18, (u - 80) / 2, 80);
      nameAt(g, true, s + 114, u / 2, 42, mid - sw / 2 - 18 - (s + 114), inkOn(hc), F.inter6, -1);
      g.rect(mid - sw / 2, 0, mid + sw / 2, u, 0xEB1A1F1C);
      labelScore(g, mid, mid - sw / 2, mid + sw / 2, 0, u, F.inter6, 0xFFB0C0C8, INK, 14, 62);
      g.vgrad(mid + sw / 2, 0, c0, u, lighter(ac, 0.1), darker(ac, 0.12));
      nameAt(g, false, c0 - 114, u / 2, 42, c0 - 114 - 18 - (mid + sw / 2), inkOn(ac), F.inter6, 1);
      crestOr(g, false, c0 - 98, (u - 80) / 2, 80);
      g.rect(c0, 0, W, u, 0xEB151917);
      words(g, clockText(LEAD), (c0 + W) / 2, u / 2, 46, ck - 20, INK, F.inter6, 0, { cls: 'sslot' });
    },
  });
  // LaLiga: the logo on coral over the clock on white (the board's first block), the clubs one above the other on near
  // black, each edged in its colour, the scores on white, the moment on coral
  S.laliga = f1('LALIGA', {
    fonts: ['800 40px CBBarlowC'], sub: ['#0E0F1F', '#fff', 'grad', '#0E0F1F'],
    draw(g, s, sh) {
      const u = sh / 2, lab = 160, sc1 = W - lab, sc0 = sc1 - u * 1.3, e0 = sc0 - u * 0.16;
      g.rect(s, 0, e0, sh, 0xFF0E0F1F);
      for (let i = 0; i < 2; i++) {
        crestOr(g, i === 0, s + 16, i * u + 7, u - 14);
        nameAt(g, i === 0, s + u + 14, u * (i + 0.5), u * 0.6, e0 - 16 - (s + u + 14), INK, F.bcond8, -1);
      }
      g.rect(e0, 0, sc0, u, solid(BD.homeColour));
      g.rect(e0, u, sc0, sh, solid(BD.awayColour));
      g.rect(sc0, 0, sc1, sh, 0xFFEFEFEF);
      g.rect(sc0 + u * 0.14, u - u * 0.012, sc1 - u * 0.14, u + u * 0.012, 0xFF3C3C3C);
      scoreOne(g, MO.score[0], (sc0 + sc1) / 2, u / 2, u * 0.67, 0xFF111111, F.bcond8);
      scoreOne(g, MO.score[1], (sc0 + sc1) / 2, u * 1.5, u * 0.67, 0xFF111111, F.bcond8);
      g.rect(sc1, 0, W, sh, 0xFFEE4B3B);
      words(g, MO.label, (sc1 + W) / 2, sh / 2, 30, lab - 24, 0xFFFFFFFF, F.bcond8, 0, { em: 0.06 });
    },
  });
  // Liga Portugal: the logo on white over the competition on red (the board's first block), the clubs on navy with the
  // moment and the clock on white under them, the scores on white, each row's end in its club's colour
  S.liga_portugal = f1('LIGAPORTUGAL', {
    fonts: ['800 40px CBMont', '700 40px CBMont'], sub: ['#0D2E73', '#fff', 'grad', '#0D2E73'],
    draw(g, s, sh) {
      const u = sh / 2, rh = u * 0.8, low = rh * 2, e0 = W - u * 0.26, sc1 = e0, sc0 = sc1 - u * 1.7;
      g.vgrad(s, 0, sc0, low, 0xFF0D2E73, 0xFF08225A);
      g.rect(s + u * 0.12, rh - u * 0.01, sc0 - u * 0.12, rh + u * 0.01, 0xFF3F5DA6);
      for (let i = 0; i < 2; i++) {
        crestOr(g, i === 0, s + 14, i * rh + 6, rh - 12);
        nameAt(g, i === 0, s + rh + 16, rh * (i + 0.5), rh * 0.56, sc0 - 16 - (s + rh + 16), INK, F.mont8, -1);
      }
      g.rect(s, low, sc0, sh, 0xFFFFFFFF);
      words(g, MO.label, s + 16, (low + sh) / 2, (sh - low) * 0.5, 0, 0xFF0D2E73, F.mont8, -1, { em: 0.2 });
      words(g, clockText(LEAD), sc0 - 16, (low + sh) / 2, (sh - low) * 0.72, 0, 0xFF0D2E73, F.mont8, 1, { cls: 'sslot' });
      g.rect(sc0, 0, sc1, low, 0xFFFFFFFF);
      g.rect(sc0 + u * 0.1, rh - u * 0.01, sc1 - u * 0.1, rh + u * 0.01, 0xFFB9C3D6);
      scoreOne(g, MO.score[0], (sc0 + sc1) / 2, rh / 2, u * 0.62, 0xFF0D2E73, F.mont7);
      scoreOne(g, MO.score[1], (sc0 + sc1) / 2, rh * 1.5, u * 0.62, 0xFF0D2E73, F.mont7);
      g.rect(e0, 0, W, rh, solid(BD.homeColour));
      g.rect(e0, rh, W, low, solid(BD.awayColour));
    },
  });
  // Bundesliga: the clock over the league's player (the board's first block), each club's column in its colour, its crest
  // and name over its score, the moment between them
  S.bundesliga = f1('BUNDESLIGA', {
    fonts: ['700 40px CBBarlowS'], sub: ['#1A1A1A', '#fff', 'grad', '#1A1A1A'],
    draw(g, s, sh) {
      const u = sh / 2, band = 44, mid = (s + W) / 2, hc = solid(BD.homeColour), ac = solid(BD.awayColour);
      g.rect(s, 0, mid, sh, hc);
      g.rect(mid, 0, W, sh, ac);
      g.rect(s, sh - u * 0.06, mid, sh, darker(hc, 0.3));
      g.rect(mid, sh - u * 0.06, W, sh, darker(ac, 0.25));
      for (let i = 0; i < 2; i++) {
        const l = i === 0 ? s : mid, r = i === 0 ? mid : W, c = i === 0 ? hc : ac;
        crestOr(g, i === 0, l + 14, (band - 34) / 2 + 2, 34);
        nameAt(g, i === 0, (l + 58 + r - 14) / 2, band / 2 + 2, 28, r - 14 - (l + 58), ((inkOn(c) & 0xFFFFFF) | 0xE6000000) >>> 0, F.bsemi7, 0);
        scoreOne(g, MO.score[i], (l + r) / 2 + (i === 0 ? -40 : 40), band + (sh - band) / 2 - 2, (sh - band) * 0.9, inkOn(c), F.bsemi7);
      }
      g.round(mid - 90, band + 16, mid + 90, sh - 18, 4, 0xEB2C2C2C);
      words(g, MO.label, mid, (band + 16 + sh - 18) / 2, 16, 160, INK, F.bsemi7, 0, { em: 0.2 });
    },
  });
  // Ligue 1: the logo on its blue square over the clock on white (the board's first block), the clubs and their scores on
  // dark, each row's end in its colour, the moment on blue under them
  S.ligue_1 = f1('LIGUE1', {
    fonts: ['700 40px CBMont'], sub: ['#1A50DD', '#fff', 'grad', '#1A50DD'], noSweep: true,
    draw(g, s, sh) {
      const u = sh / 2, rh = u * 0.7, low = rh * 2, e0 = W - u * 0.1;
      g.rect(s, 0, e0, low, 0xF2222428);
      g.rect(s + u * 0.15, rh - u * 0.008, e0 - u * 0.1, rh + u * 0.008, 0xFF3D4046);
      for (let i = 0; i < 2; i++) {
        crestOr(g, i === 0, s + 12, i * rh + 5, rh - 10);
        nameAt(g, i === 0, s + rh + 14, rh * (i + 0.5), u * 0.46, e0 - 74 - (s + rh + 14), INK, F.mont7, -1);
        scoreOne(g, MO.score[i], e0 - 36, rh * (i + 0.5), u * 0.5, INK, F.mont7);
      }
      g.rect(e0, 0, W, rh, solid(BD.homeColour));
      g.rect(e0, rh, W, low, solid(BD.awayColour));
      g.vgrad(s, low, s + 280, sh, 0xFF2766FF, 0xFF1A50DD);
      words(g, MO.label, s + 140, (low + sh) / 2, 16, 250, 0xFFFFFFFF, F.mont7, 0, { em: 0.2 });
    },
  });
  // Serie A: the logo large, edged in white, standing over the strap's start; the clock and the moment on white, each club
  // on its navy slant, the score on white
  S.serie_a = f2('SERIEA', {
    fonts: ['600 40px CBMont'], sub: ['#0A1E59', '#fff', 'grad', '#0A1E59'],
    html() {
      const g = new Svg(), u = H, s = u * 0.3, lx = 186, c0 = lx, c1 = c0 + 176;
      g.vgrad(lx * 0.55, 0, W, u, 0xFFFFFFFF, 0xFFE4E9F1);
      labelClock(g, (c0 + c1) / 2, c0, c1, 0, u, F.mont6, 0xFF0B2A6C, 0xFF0B2A6C, 15, 44);
      const sw = 200, end = W - 30 - s, mid = (c1 + end + s) / 2, h1 = mid - sw / 2 - s, a0 = mid + sw / 2 - s;
      para(g, c1, 0, h1, u, s, 0xFF15348A, 0xFF0A1E59);
      crestOr(g, true, c1 + s + 6, (u - 76) / 2, 76);
      nameAt(g, true, c1 + s + 94, u / 2, 40, h1 - (c1 + s + 94) - 4, INK, F.mont6, -1);
      const half = tw(MO.score[0] + '-', 64, F.mont6) * 0.5 + 8;
      scoreOne(g, MO.score[0], mid - half, u / 2, 64, 0xFF0B2A6C, F.mont6);
      words(g, '-', mid, u / 2, 64, 0, 0xFF0B2A6C, F.mont6, 0);
      scoreOne(g, MO.score[1], mid + half, u / 2, 64, 0xFF0B2A6C, F.mont6);
      para(g, a0, 0, end, u, s, 0xFF15348A, 0xFF0A1E59);
      nameAt(g, false, end + s - 96, u / 2, 40, end + s - 96 - (a0 + s) - 8, INK, F.mont6, 1);
      crestOr(g, false, end + s - 86, (u - 76) / 2, 76);
      const m = new Svg();
      serieMark(m, logoOf('SERIEA', BD), 0, -46, lx, u + 14, 2, 7);
      return svgPart('sab', g, 0, 0, W, u) + EDGE + svgPart('sal', m, -10, -56, lx + 20, u + 76, 'transform-origin:50% 60%');
    },
    in(t) {
      popIn(t, 'sal', IN0);
      const r = eo(prog(t, IN0 + 0.18, IN1 + 0.1)), shown = 100 + (W - 100) * r;
      $('sab').style.clipPath = 'inset(0 ' + (W - shown) + 'px 0 0)';
      const ed = $('sedge');
      ed.style.left = (shown - 4) + 'px'; ed.style.opacity = r > 0.002 && r < 0.998 ? 1 : 0;
    },
  });
  // EFL: four even blocks: the home club on its colour, the score on white, the away club, the clock on white (the
  // competition's logo on its navy first)
  S.efl = f2('EFL', {
    fonts: ['600 40px CBRubik'], sub: ['#12163A', '#fff', 'grad', '#12163A'],
    html() {
      const u = H, lg = 124, ck = 170, sw = 250, mid = (lg + W - ck) / 2, hc = solid(BD.homeColour), ac = solid(BD.awayColour);
      const gh = new Svg(), ga = new Svg(), gm = new Svg();
      gh.rect(0, 0, lg, u, 0xFF12163A);
      logoIn(gh, M.compW, 0, 0, lg, u, 20, null);
      gh.rect(lg, 0, mid - sw / 2, u, hc);
      crestOr(gh, true, lg + 18, (u - 80) / 2, 80);
      nameAt(gh, true, lg + 114, u / 2, 42, mid - sw / 2 - 18 - (lg + 114), inkOn(hc), F.rubik6, -1);
      ga.rect(mid + sw / 2, 0, W - ck, u, ac);
      nameAt(ga, false, W - ck - 114, u / 2, 42, W - ck - 114 - 18 - (mid + sw / 2), inkOn(ac), F.rubik6, 1);
      crestOr(ga, false, W - ck - 98, (u - 80) / 2, 80);
      ga.rect(W - ck, 0, W, u, 0xFFFFFFFF);
      words(ga, clockText(LEAD), W - ck / 2, u / 2, 44, ck - 20, 0xFF161616, F.rubik6, 0, { cls: 'sslot' });
      gm.rect(mid - sw / 2, 0, mid + sw / 2, u, 0xFFFFFFFF);
      labelScore(gm, mid, mid - sw / 2, mid + sw / 2, 0, u, F.rubik6, 0xFF141B5E, 0xFF141B5E, 14, 62, { dash: ' - ' });
      return svgPart('ph', gh, 0, 0, mid, u) + svgPart('pa', ga, mid, 0, W - mid, u) + svgPart('pm', gm, mid - sw / 2, 0, sw, u, 'transform-origin:50% 50%') + FLASH(mid);
    },
    in: halvesIn,
  });
  // MLS: the clock on charcoal (the logo and the moment with it), then one pale bar between the clubs' colours, each club's
  // name with its score beside the line between them
  S.mls = f2('MLS', {
    fonts: ['500 40px CBInter'], sub: ['#2B2D31', '#fff', 'grad', '#2B2D31'],
    html() {
      const g = new Svg(), u = H, c1 = 250, hs = c1 + 132, as = W - 132, mid = (hs + as) / 2, hc = solid(BD.homeColour), ac = solid(BD.awayColour);
      g.rect(0, 0, c1, u, 0xF22B2D31);
      logoIn(g, M.compW, 12, 16, 104, u - 16, 0, null);
      labelClock(g, (110 + c1) / 2, 110, c1, 0, u, F.inter5, 0xFFB8BEC8, INK, 13, 40);
      g.vgrad(c1, 0, hs, u, hc, darker(hc, 0.1));
      crestOr(g, true, c1 + 26, (u - 80) / 2, 80);
      g.vgrad(hs, 0, as, u, 0xFFF8F8F8, 0xFFD9DADC);
      nameAt(g, true, (hs + mid - 100) / 2, u / 2, 40, mid - 100 - hs - 24, 0xFF141414, F.inter5, 0);
      scoreOne(g, MO.score[0], mid - 50, u / 2, 66, 0xFF141414, F.inter5);
      g.rect(mid - 1.5, u * 0.1, mid + 1.5, u * 0.9, 0xFFA2A4A8);
      scoreOne(g, MO.score[1], mid + 50, u / 2, 66, 0xFF141414, F.inter5);
      nameAt(g, false, (mid + 100 + as) / 2, u / 2, 40, as - (mid + 100) - 24, 0xFF141414, F.inter5, 0);
      g.vgrad(as, 0, W, u, ac, darker(ac, 0.1));
      crestOr(g, false, as + 26, (u - 80) / 2, 80);
      return svgPart('pw', g, 0, 0, W, u) + EDGE;
    },
    in(t) { wipeIn(t, 'pw', 250); },
  });
  // Premier League, US style: the competition's name (with its logo and the moment) in a black tab over the row; each
  // club's crest and name on its colour, the score on both clubs' colours darkened, the clock on light grey
  S.pl_us = f2('PL_US', {
    fonts: ['600 40px CBMont', '500 40px CBMont'], sub: ['#121212', '#fff', 'grad', '#121212'],
    html() {
      const g = new Svg(), u = H, ck = 180, c0 = W - ck, sw = 300, hc = solid(BD.homeColour), ac = solid(BD.awayColour);
      const name = BD.title || 'PREMIER LEAGUE', tab = 46, ts = 22, nw = tw(name, ts, F.mont6);
      const tabW = 64 + nw + 26 + tw(MO.label, 15, F.mont6, 0, 0.2) + 26;
      g.rect(0, -tab, tabW, 0, 0xF0121212);
      logoIn(g, M.compW, 8, -tab + 6, 52, -6, 0, null);
      words(g, name, 60, -tab / 2, ts, 0, INK, F.mont6, -1);
      words(g, MO.label, 60 + nw + 26, -tab / 2, 15, 0, 0xFFB9BCC4, F.mont6, -1, { em: 0.2 });
      const mid = c0 / 2, h1 = mid - sw / 2, a0 = mid + sw / 2;
      g.vgrad(0, 0, h1, u, hc, darker(hc, 0.12));
      crestOr(g, true, 18, (u - 84) / 2, 84);
      nameAt(g, true, 116, u / 2, 44, h1 - 18 - 116, inkOn(hc), F.mont6, -1);
      g.vgrad(h1, 0, mid, u, darker(hc, 0.2), darker(hc, 0.3));
      g.vgrad(mid, 0, a0, u, darker(ac, 0.32), darker(ac, 0.42));
      scoreOne(g, MO.score[0], (h1 + mid) / 2, u / 2, 72, INK, F.mont6);
      words(g, '-', mid, u / 2, 72, 0, INK, F.mont6, 0);
      scoreOne(g, MO.score[1], (mid + a0) / 2, u / 2, 72, INK, F.mont6);
      g.vgrad(a0, 0, c0, u, ac, darker(ac, 0.12));
      nameAt(g, false, c0 - 116, u / 2, 44, c0 - 116 - 18 - a0, inkOn(ac), F.mont6, 1);
      crestOr(g, false, c0 - 102, (u - 84) / 2, 84);
      g.vgrad(c0, 0, W, u, 0xFFF3F3F3, 0xFFDCDCDC);
      words(g, clockText(LEAD), (c0 + W) / 2, u / 2, 46, ck - 24, 0xFF1E1E1E, F.mont5, 0, { cls: 'sslot' });
      return svgPart('pr', g, 0, -tab, W, u + tab);
    },
    in(t) { riseIn(t, 'pr'); },
  });
  // Premier League, world feed: each club's crest and name on its colour darkened, pointing in to the middle behind its
  // chevrons; the two scores large either side of the lion standing over; the moment and the clock in purple under them
  S.pl_world = f2('PL', {
    fonts: ['800 40px CBMont', '700 40px CBMont'], sub: ['#37003C', '#fff', 'grad', '#37003C'], bot: 58,
    html() {
      const u = H, tip = u * 0.3, b1 = 412, a0 = W - 412, mid = W / 2;
      const hc = darker(solid(BD.homeColour), 0.35), ac = darker(solid(BD.awayColour), 0.35);
      const gh = new Svg(), ga = new Svg(), gm = new Svg();
      lit(gh, P([[0, 0], [b1, 0], [b1 + tip, u / 2], [b1, u], [0, u]]), hc, 0, u);
      chevronBand(gh, b1 + u * 0.1, 0, u, tip, u * 0.13, 1, 0xFFF6F8FA, 0xFFAFB9C6);
      chevronBand(gh, b1 + u * 0.27, 0, u, tip, u * 0.08, 1, 0xB3DCE5EF, 0x80AFC0D4);
      crestOr(gh, true, 22, (u - 84) / 2, 84);
      nameAt(gh, true, 124, u / 2, 46, b1 - 20 - 124, INK, F.mont8, -1);
      lit(ga, P([[W, 0], [a0, 0], [a0 - tip, u / 2], [a0, u], [W, u]]), ac, 0, u);
      chevronBand(ga, a0 - u * 0.1, 0, u, tip, u * 0.13, -1, 0xFFF6F8FA, 0xFFAFB9C6);
      chevronBand(ga, a0 - u * 0.27, 0, u, tip, u * 0.08, -1, 0xB3DCE5EF, 0x80AFC0D4);
      nameAt(ga, false, W - 124, u / 2, 46, W - 124 - 20 - a0, INK, F.mont8, 1);
      crestOr(ga, false, W - 106, (u - 84) / 2, 84);
      gm.rect(mid - 200, u - 2, mid + 200, u + 56, 0xFF37003C);
      words(gm, MO.label, mid - 100, u + 27, 16, 170, 0xFFD9B8E0, F.mont7, 0, { em: 0.2 });
      words(gm, clockText(LEAD), mid + 100, u + 27, 36, 170, INK, F.mont7, 0, { cls: 'sslot' });
      bigDigit(gm, MO.score[0], mid - 140, u / 2 - 4, 140);
      bigDigit(gm, MO.score[1], mid + 140, u / 2 - 4, 140);
      logoIn(gm, logoOf('PL', BD), mid - 84, -96, mid + 84, u + 14, 0, null);
      return svgPart('ph', gh, 0, 0, mid, u) + svgPart('pa', ga, mid, 0, W - mid, u) +
        svgPart('pm', gm, mid - 260, -110, 520, u + 170, 'transform-origin:50% 60%') + FLASH(mid);
    },
    in: halvesIn,
  });
  // FA Cup: each club's name on light grey with its colour at the end, its score on charcoal; the trophy standing over the
  // middle on the red tab with the moment and the clock
  S.fa_cup = f2('FACUP', {
    fonts: ['700 40px CBMont'], sub: ['#25282A', '#fff', 'grad', '#25282A'], bot: 64,
    html() {
      const g = new Svg(), u = H, mid = W / 2, e = 20, scw = 120, mw = 220;
      const h2 = mid - mw / 2 - scw, h3 = mid - mw / 2, a0 = mid + mw / 2, a1 = a0 + scw, a2 = W - e, tt = u * 0.45;
      g.rect(0, 0, e, u, solid(BD.homeColour));
      g.vgrad(e, 0, h2, u, 0xFFF0F0F0, 0xFFD8D8D8);
      crestOr(g, true, e + 16, (u - 80) / 2, 80);
      nameAt(g, true, e + 110, u / 2, 40, h2 - 16 - (e + 110), 0xFF3A3D41, F.mont7, -1);
      g.vgrad(h2, 0, h3, u, 0xFF383B3E, 0xFF25282A);
      scoreOne(g, MO.score[0], (h2 + h3) / 2, u / 2, 76, INK, F.mont7);
      g.vgrad(a0, 0, a1, u, 0xFF383B3E, 0xFF25282A);
      scoreOne(g, MO.score[1], (a0 + a1) / 2, u / 2, 76, INK, F.mont7);
      g.vgrad(a1, 0, a2, u, 0xFFF0F0F0, 0xFFD8D8D8);
      nameAt(g, false, a2 - 110, u / 2, 40, a2 - 110 - 16 - a1, 0xFF3A3D41, F.mont7, 1);
      crestOr(g, false, a2 - 96, (u - 80) / 2, 80);
      g.rect(a2, 0, W, u, solid(BD.awayColour));
      g.round(h3 - 6, tt, a0 + 6, u + 62, 6, 0xFFC8102E);
      words(g, MO.label, mid, tt + 30, 15, mw - 30, 0xFFFFD9DE, F.mont7, 0, { em: 0.2 });
      words(g, clockText(LEAD), mid, u + 24, 46, mw - 30, INK, F.mont7, 0, { cls: 'sslot' });
      const m = new Svg();
      if (M.compM) standing(m, M.compM, mid - 120, -122, mid + 120, tt + 4, 4);
      else logoIn(m, M.compW, mid - 110, -116, mid + 110, tt, 0, null);
      return svgPart('pr', g, 0, 0, W, u + 64) + svgPart('pp', m, mid - 130, -126, 260, tt + 132, 'transform-origin:50% 100%');
    },
    in(t) { riseIn(t, 'pr'); popIn(t, 'pp', IN0 + 0.4); },
  });
  // Eredivisie: each club's name on its colour fading out from the score, the scores on two greys under the moment, the
  // clock apart on white (the competition's logo standing free at the start)
  S.eredivisie = f2('EREDIVISIE', {
    fonts: ['600 40px CBInter'], sub: ['#1C1F25', '#fff', 'grad', '#1C1F25'],
    html() {
      const u = H, lg = 130, ck = 190, gap = 34, sw = 150, mid = (lg + W - ck - gap) / 2, hc = solid(BD.homeColour), ac = solid(BD.awayColour);
      const gh = new Svg(), ga = new Svg(), gm = new Svg(), fade = (c) => ((darker(c, 0.55) & 0xFFFFFF) | 0x8C000000) >>> 0, end = u * 0.28;
      logoIn(gh, M.compW, 0, 0, lg, u, 14, null);
      gh.hgrad(lg, 0, mid - sw, u, [fade(hc), hc]);
      gh.rect(mid - sw - end, 0, mid - sw, u, lighter(hc, 0.15));
      crestOr(gh, true, lg + 16, (u - 80) / 2, 80);
      nameAt(gh, true, lg + 108, u / 2, 42, mid - sw - end - 14 - (lg + 108), INK, F.inter6, -1);
      ga.rect(mid + sw, 0, mid + sw + end, u, lighter(ac, 0.15));
      ga.hgrad(mid + sw + end, 0, W - ck - gap, u, [ac, fade(ac)]);
      nameAt(ga, false, W - ck - gap - 108, u / 2, 42, W - ck - gap - 108 - 14 - (mid + sw + end), INK, F.inter6, 1);
      crestOr(ga, false, W - ck - gap - 94, (u - 80) / 2, 80);
      ga.vgrad(W - ck, 0, W, u, 0xFFF7F7F7, 0xFFDEDEDE);
      words(ga, clockText(LEAD), W - ck / 2, u / 2, 46, ck - 24, 0xFF121212, F.inter6, 0, { cls: 'sslot' });
      gm.vgrad(mid - sw, 0, mid, u, 0xFFE3E3E3, 0xFFC9C9C9);
      gm.vgrad(mid, 0, mid + sw, u, 0xFFD4D4D4, 0xFFBBBBBB);
      words(gm, MO.label, mid, 24, 13, sw * 2 - 24, 0x99121212, F.inter6, 0, { em: 0.2 });
      scoreOne(gm, MO.score[0], mid - sw / 2, 74, 72, 0xFF121212, F.inter6);
      scoreOne(gm, MO.score[1], mid + sw / 2, 74, 72, 0xFF121212, F.inter6);
      return svgPart('ph', gh, 0, 0, mid - sw, u) + svgPart('pa', ga, mid + sw, 0, W - mid - sw, u) +
        svgPart('pm', gm, mid - sw, 0, sw * 2, u, 'transform-origin:50% 50%') + FLASH(mid);
    },
    in: halvesIn,
  });
  // Liga MX: one dark panel, the competition's title (its year and half) along its top under a light line with the moment;
  // the logo, then each club's crest, name and score on its colour, then the clock
  S.liga_mx = f3('LIGAMX', {
    fonts: ['500 40px CBMont', '600 40px CBMont', '700 40px CBMont'], sub: ['#232427', '#fff', 'grad', '#232427'],
    panels: () => [{ id: 'mxp', style: 'background:rgba(35,36,39,.94)', from: { x: 0, y: -58, w: W, h: H + 58, r: 16 }, to: () => [0, 0, BB.w, BB.h, BB.u * 0.12] }],
    html() {
      const g = new Svg(), u = H, top = -58, inn = 9, ck = 170, c0 = W - ck - 10, mid = (150 + c0) / 2;
      g.rect(16, top + 6, W - 16, top + 10, 0xFF8E9196);
      words(g, mxTitle(BD), W / 2, top + 33, 22, W - 520, 0xFFE3E4E7, F.mont6, 0);
      words(g, MO.label, 26, top + 33, 15, 0, 0xFFB9BCC4, F.mont7, -1, { em: 0.2 });
      logoIn(g, logoOf('LIGAMX', BD), 12, 12, 140, u - 12, 0, null);
      const hc = solid(BD.homeColour), ac = solid(BD.awayColour);
      for (let i = 0; i < 2; i++) {
        const l = i === 0 ? 150 : mid + 6, r = i === 0 ? mid - 6 : c0, c = i === 0 ? hc : ac, ik = inkHi(c);
        g.rect(l, inn, r, u - inn, c);
        crestOr(g, i === 0, l + 12, inn + 8, u - 2 * inn - 16);
        nameAt(g, i === 0, l + u - 2 * inn + 10, u / 2, 38, r - 80 - (l + u - 2 * inn + 10), ik, F.mont5, -1);
        scoreOne(g, MO.score[i], r - 40, u / 2, 62, ik, F.mont6);
      }
      words(g, clockText(LEAD), (c0 + W) / 2, u / 2, 46, ck - 20, INK, F.mont7, 0, { cls: 'sslot' });
      return svgPart('sc', g, 0, top, W, u - top);
    },
    in(t) {
      const p = eo(prog(t, IN0, IN0 + 0.4)), e = $('mxp');
      if (t < OUT0) e.style.clipPath = 'inset(0 ' + (W / 2 * (1 - p)) + 'px 0 ' + (W / 2 * (1 - p)) + 'px round 16px)';
      else e.style.clipPath = 'none';
      if (t < OUT0) $('sc').style.opacity = prog(t, IN0 + 0.25, IN0 + 0.55);
    },
  });

  // ---------- the retro boards ----------
  // 2004: the silver and gold arrow with its navy inner arrow (the board's first block, large), the clubs' names in small
  // capitals on dark grey with their scores in white boxes, the clock and the moment in the pale tab under them
  S.retro_2004 = f1('R2004', {
    h: 190, fonts: ['700 40px CBBarlowS'], sub: ['#2B2E33', '#fff', 'grad', '#2B2E33'], noSweep: true, ownSeed: (sh) => sh / 1.9 * 1.28,
    draw(g, s, sh) {
      const u = sh / 1.9, rows = [u * 0.2, u * 0.68, u * 0.74, u * 1.22], x2 = W - u * 0.62, nl = u * 0.9;
      for (let i = 0; i < 2; i++) {
        const t = rows[i * 2], b = rows[i * 2 + 1], cy = (t + b) / 2;
        g.vgrad(nl, t, x2, b, 0xFF5E636A, 0xFF2B2E33);
        g.rect(nl, t, x2, t + u * 0.02, 0x59FFFFFF);
        smallCaps(g, i === 0 ? FULL.home : FULL.away, s + u * 0.1, cy, u * 0.45, x2 - s - u * 0.2, INK, F.bsemi7);
        g.rect(x2 + u * 0.04, t, W, b, 0xFF2B2E33);
        g.vgrad(x2 + u * 0.07, t + u * 0.03, W - u * 0.03, b - u * 0.03, 0xFFFFFFFF, 0xFFE2E4E8);
        scoreOne(g, MO.score[i], (x2 + W) / 2 + u * 0.02, cy, u * 0.475, 0xFF111111, F.bsemi7);
      }
      const ct = u * 1.3, cb = u * 1.84, cr = s + 420;
      g.vgrad(u * 0.85, ct, cr, cb, 0xFFF4F5F7, 0xFFC8CCD2);
      words(g, MO.label, s + 16, (ct + cb) / 2, u * 0.3, 0, 0xFF1E1E1E, F.bsemi7, -1, { em: 0.12 });
      words(g, clockText(LEAD), cr - 16, (ct + cb) / 2, u * 0.475, 0, 0xFF1E1E1E, F.bsemi7, 1, { cls: 'sslot' });
      // the board's own first block, its arrows over the names' start
      g.path(arrowD(0, sh, 0, u * 0.4, u * 1.02, u * 0.62), g.grad(0, 0, u * 1.02, 0, [0xFFFFFFFF, 0xFFEAD9A6, 0xFFA3ABB7]));
      g.path(arrowD(0, sh, u * 0.42, u * 0.66, u * 1.28, u * 1.04), g.grad(u * 0.42, 0, u * 1.28, 0, [0xFF1D2A69, 0xFF2F45A3]));
    },
  });
  // 2008 (and 2012, light): the logo in its ball on the navy blade (the board's first block, large); the clubs' names either
  // side of the score, set off by slanted stripes in their colours; the clock and the moment in the navy tab under them
  const r08Strap = (y2012) => function (g, s, sh) {
    const u = sh / 1.95, bt = u * 0.08, bb = bt + u, cy = (bt + bb) / 2;
    g.path(P([[u * 1.7, bt], [W, bt], [W - u * 0.4, bb], [u * 1.7, bb]]),
      y2012 ? g.grad(0, bt, 0, bb, [0xFFFCFDFE, 0xFFE9ECF1, 0xFFC9CFD9]) : g.grad(0, bt, 0, bb, [0xFF28377A, 0xFF101A48]));
    const ink = y2012 ? 0xFF16245C : INK, sw = Math.max(u * 1.9, tw(MO.score[0] + '-' + MO.score[1], u * 0.868, F.bsemi7) + u);
    const nameW = (W - s - u * 0.7 - sw - u * 0.55 - u * 0.45) / 2;
    const x2 = s + nameW, x3 = x2 + u * 0.7, x4 = x3 + sw, x5 = x4 + u * 0.55, x6 = x5 + nameW;
    words(g, BD.homeName, (s + x2) / 2, cy, u * 0.775, nameW - u * 0.3, ink, F.bsemi7, 0, { min: 26 });
    words(g, BD.awayName, (x5 + x6) / 2, cy, u * 0.775, nameW - u * 0.3, ink, F.bsemi7, 0, { min: 26 });
    slashes(g, x2 + u * 0.08, bt, bb, solid(BD.homeColour), y2012);
    slashes(g, x4 + u * 0.04, bt, bb, solid(BD.awayColour), y2012);
    g.round(x3, bt + u * 0.1, x4, bb - u * 0.1, y2012 ? u * 0.16 : u * 0.03, g.grad(0, bt + u * 0.1, 0, bb - u * 0.1, [0xFFD42A38, 0xFF931521]));
    const mid = (x3 + x4) / 2, half = tw(MO.score[0] + '-', u * 0.868, F.bsemi7) * 0.5 + u * 0.05;
    scoreOne(g, MO.score[0], mid - half, cy, u * 0.868, INK, F.bsemi7);
    words(g, '-', mid, cy, u * 0.868, 0, INK, F.bsemi7, 0);
    scoreOne(g, MO.score[1], mid + half, cy, u * 0.868, INK, F.bsemi7);
    if (y2012) {
      swoop(g, s - u * 0.05, bt, bb, 0xFFD42A38, 1);
      swoop(g, W - u * 0.28, bt, bb, 0xFF2E5FC9, -1);
    }
    const cl = s - u * 0.05, cw = Math.max(u * 3.4, tw(MO.label, u * 0.34, F.bsemi7, 0, 0.12) + tw('88:88', u * 0.7, F.bsemi7) + u * 0.9);
    g.round(cl, bb - u * 0.08, cl + cw, bb + u * 0.78, u * 0.3, 0xFF16224F);
    g.rect(cl, bb - u * 0.08, cl + cw, bb + u * 0.2, 0xFF16224F);
    words(g, MO.label, cl + u * 0.3, bb + u * 0.36, u * 0.34, 0, 0xFFB5C2E2, F.bsemi7, -1, { em: 0.12 });
    words(g, clockText(LEAD), cl + cw - u * 0.3, bb + u * 0.36, u * 0.7, 0, INK, F.bsemi7, 1, { cls: 'sslot' });
    // the board's own first block: the blade and the ball with the logo
    blade(g, BD, 0, bt, bb, u);
  };
  const r08Seed = (sh) => sh / 1.95 * 2.75;
  S.retro_2008 = f1('R2008', { h: 175, ownSeed: r08Seed, fonts: ['700 40px CBBarlowS'], sub: ['#16224F', '#fff', 'grad', '#16224F'], noSweep: true, draw: r08Strap(false) });
  S.retro_2012 = f1('R2012', { h: 175, ownSeed: r08Seed, fonts: ['700 40px CBBarlowS'], sub: ['#16224F', '#fff', 'grad', '#16224F'], noSweep: true, draw: r08Strap(true) });
  // 2016: the clubs' names on purple either side of the score on green, the lion standing over it, the clock and the moment
  // in white under it
  S.retro_2016 = f2('R2016', {
    fonts: ['600 40px CBRubik'], sub: ['#38003C', '#fff', 'grad', '#38003C'], bot: 70,
    html() {
      const g = new Svg(), u = H, e = u * 0.06, wh = u * 0.08, sw = 290, mid = W / 2;
      const h1 = e + wh, h2 = mid - sw / 2, a1 = mid + sw / 2, a2 = W - e - wh;
      g.rect(0, 0, e, u, solid(BD.homeColour));
      g.rect(e, 0, h1, u, 0xFFFFFFFF);
      g.rect(h1, 0, h2, u, 0xFF38003C);
      crestOr(g, true, h1 + 16, (u - 80) / 2, 80);
      nameAt(g, true, h1 + 110, u / 2, 46, h2 - 16 - (h1 + 110), INK, F.rubik6, -1);
      g.rect(h2, 0, a1, u, 0xFF00FF85);
      const half = tw(MO.score[0] + '-', 86, F.rubik6) * 0.5 + 6;
      scoreOne(g, MO.score[0], mid - half, u / 2, 86, 0xFF136B36, F.rubik6);
      words(g, '-', mid, u / 2, 86, 0, 0xFF136B36, F.rubik6, 0);
      scoreOne(g, MO.score[1], mid + half, u / 2, 86, 0xFF136B36, F.rubik6);
      g.rect(a1, 0, a2, u, 0xFF38003C);
      nameAt(g, false, a2 - 110, u / 2, 46, a2 - 110 - 16 - a1, INK, F.rubik6, 1);
      crestOr(g, false, a2 - 96, (u - 80) / 2, 80);
      g.rect(a2, 0, W - e, u, 0xFFFFFFFF);
      g.rect(W - e, 0, W, u, solid(BD.awayColour));
      g.rect(h2, u, a1, u + 66, 0xFFFFFFFF);
      words(g, MO.label, mid, u + 18, 14, sw - 30, 0xFF38003C, F.rubik6, 0, { em: 0.2 });
      words(g, clockText(LEAD), mid, u + 45, 34, sw - 30, 0xFF1A1A1A, F.rubik6, 0, { cls: 'sslot' });
      const m = new Svg();
      logoIn(m, logoOf('R2016', BD), h2 + 30, -106, a1 - 30, 4, 0, null);
      return svgPart('pr', g, 0, 0, W, u + 70) + svgPart('pp', m, h2, -110, sw, 116, 'transform-origin:50% 100%');
    },
    in(t) { riseIn(t, 'pr'); popIn(t, 'pp', IN0 + 0.45); },
  });

  // ---------- the Scoreboard Lab's looks ----------
  // Floodlight: a crisp white plate, each club's crest and name underlined in its colour, the moment and the score in
  // white on near black between them; the clock and the competition (in yellow) in a slim dark tab tucked under its start.
  // It comes on as floodlights do, flickering up; the plate and its tab become the board's.
  S.floodlight = f3('FLOODLIGHT', {
    fonts: ['820 40px Outfit', '620 40px Outfit', '700 40px monospace'], sub: ['#0B0E16', '#fff', 'grad', '#0B0E16'], bot: 72,
    panels: () => [
      { id: 'flp', style: 'background:#F6F7FA;box-shadow:0 18px 30px -18px rgba(0,0,0,.5);overflow:hidden',
        inner: '<i id="fsw" style="position:absolute;top:-10%;height:120%;width:240px;background:linear-gradient(90deg,rgba(150,175,255,0),rgba(150,175,255,.3),rgba(150,175,255,0));transform:skewX(-20deg);opacity:0"></i>',
        from: { x: 0, y: 0, w: W, h: H, r: 14 }, to: () => [BB.x[0], 0, BB.x[3] - BB.x[0], BB.u, BB.u * 0.11] },
      { id: 'flt', style: 'background:rgba(8,10,16,.82)', from: { x: 0, y: H + 10, w: 380, h: 58, r: 14 },
        to: () => { const tb = BB.info.tab || [0, BB.u * 1.08, BB.u * 2, BB.u * 1.6]; return [tb[0], tb[1], tb[2] - tb[0], tb[3] - tb[1], BB.u * 0.11]; } },
    ],
    html() {
      const g = new Svg(), u = H, sw = 300, mid = W / 2, h1 = mid - sw / 2, a0 = mid + sw / 2, und = u * 0.085;
      g.rect(h1, 0, a0, u, LAB_DARK);
      crestOr(g, true, 22, (u - 82) / 2 - 4, 82);
      nameAt(g, true, 122, u * 0.48, 44, h1 - 22 - 122, FLOOD_INK, F.outfit, -1, { w: W800, em: 0.04 });
      g.rect(h1 * 0.18, u - und, h1 * 0.82, u, solid(BD.homeColour));
      crestOr(g, false, W - 104, (u - 82) / 2 - 4, 82);
      nameAt(g, false, W - 122, u * 0.48, 44, W - 122 - 22 - a0, FLOOD_INK, F.outfit, 1, { w: W800, em: 0.04 });
      g.rect(a0 + (W - a0) * 0.18, u - und, W - (W - a0) * 0.18, u, solid(BD.awayColour));
      words(g, MO.label, mid, 34, 14, sw - 30, 0xFFB7BDCC, F.outfit, 0, { w: W600, em: 0.2 });
      digits(g, BD, MO.score, h1, a0, 78, 64, INK, F.outfit, W800, 0.04, '–', 0.7);
      const tt = u + 10, cy = tt + 29;
      words(g, clockText(LEAD), 24, cy, 34, 0, INK, F.mono, -1, { w: W760, cls: 'sslot' });
      if (C.compShort) words(g, C.compShort, 24 + tw('88:88', 34, F.mono, W760) + 22, cy, 28, 120, YELLOW, F.outfit, -1, { w: W600, em: 0.1 });
      logoIn(g, M.compW, 300, tt + 8, 370, tt + 50, 0, null);
      return svgPart('sc', g, 0, 0, W, u + 72);
    },
    in(t) {
      // floodlights striking up: on, a dip, on again, a flicker, then full; a light passing over the plate
      const k = t - IN0, steps = [[0, 0], [0.06, 1], [0.12, 0.25], [0.2, 1], [0.26, 0.55], [0.34, 1]];
      let a = 0;
      for (const [at, v] of steps) if (k >= at) a = v;
      if (t < OUT0) {
        $('flp').style.opacity = a;
        $('flt').style.opacity = prog(t, IN0 + 0.3, IN0 + 0.55);
        $('flt').style.transform = 'translateY(' + (-30 * (1 - eo(prog(t, IN0 + 0.3, IN0 + 0.6)))) + 'px)';
        $('sc').style.opacity = a * (k < 0.34 ? 0.85 : 1);
      } else $('flt').style.transform = 'none';
      sweep('fsw', t, IN0 + 0.45, IN0 + 1.05, W);
    },
  });
  // Split kit: each half in its club's colour, cut on an angle where they meet, each club's crest and name with its score
  // beside the cut; the moment and the clock on near black at the end. The halves slide in to meet with a flash.
  S.splitkit = f2('SPLITKIT', {
    fonts: ['820 40px Outfit', '620 40px Outfit', '700 40px monospace'], sub: ['#0B0E16', '#fff', 'grad', '#0B0E16'],
    html() {
      const u = H, cut = u * 0.35, rad = u * 0.15, ck = 190, x2 = W - ck, x1 = (x2 + cut) / 2 + 10, hc = solid(BD.homeColour), ac = solid(BD.awayColour);
      const gh = new Svg(), ga = new Svg();
      lit(gh, 'M' + rad + ' 0L' + nn(x1) + ' 0L' + nn(x1 - cut) + ' ' + u + 'L' + rad + ' ' + u + 'Q0 ' + u + ' 0 ' + nn(u - rad) + 'L0 ' + rad + 'Q0 0 ' + rad + ' 0Z', hc, 0, u);
      crestOr(gh, true, 24, (u - 82) / 2, 82);
      nameAt(gh, true, 124, u / 2, 44, x1 - cut - 150 - 124, inkOn(hc), F.outfit, -1, { w: W800, em: 0.04 });
      scoreOne(gh, MO.score[0], x1 - cut / 2 - 74, u / 2, 80, inkOn(hc), F.outfit, { w: W800 });
      lit(ga, P([[x1, 0], [x2, 0], [x2, u], [x1 - cut, u]]), ac, 0, u);
      scoreOne(ga, MO.score[1], x1 - cut / 2 + 64, u / 2, 80, inkOn(ac), F.outfit, { w: W800 });
      nameAt(ga, false, x2 - 124, u / 2, 44, x2 - 124 - (x1 - cut / 2 + 130), inkOn(ac), F.outfit, 1, { w: W800, em: 0.04 });
      crestOr(ga, false, x2 - 106, (u - 82) / 2, 82);
      block(ga, x2, 0, W, u, LAB_DARK, rad, false, true);
      labelClock(ga, (x2 + W) / 2, x2, W, 0, u, F.outfit, 0xFFB3B9C9, INK, 14, 40, { lw: W600, cf: F.mono, cw: W760 });
      return svgPart('ph', gh, 0, 0, x1, u) + svgPart('pa', ga, x1 - cut, 0, W - x1 + cut, u) + FLASH(x1 - cut / 2);
    },
    in: halvesIn,
  });
  // Ticker: one slim line: the competition first on the family's violet to coral, each club's crest and name, the score on
  // white, the minute last; the moment on a coral tab over it. It draws itself across, then ticks its words in.
  const TK = 76;
  S.ticker = f3('TICKER', {
    fonts: ['820 40px Outfit', '760 40px Outfit', '700 40px monospace'], sub: ['#080A10', '#fff', 'grad', '#080A10'],
    panels: () => [
      { id: 'tkp', style: 'background:rgba(8,10,16,.86)', from: { x: 0, y: H - TK, w: W, h: TK, r: 8 }, to: () => [BB.x[0], 0, BB.x[5] - BB.x[0], BB.u, BB.u * 0.1] },
      { id: 'tkg', style: 'background:linear-gradient(90deg,#6D28F2,#FF6B6B)', square: true,
        from: { x: 0, y: H - TK, w: 190, h: TK, r: 8 }, to: () => [BB.x[0], 0, BB.x[1] - BB.x[0], BB.u, BB.u * 0.1] },
      { id: 'tks', style: 'background:#fff;border-radius:0', from: { x: W / 2 - 90, y: H - TK, w: 180, h: TK, r: 0 }, to: () => [BB.x[2], 0, BB.x[3] - BB.x[2], BB.u, 0] },
    ],
    html() {
      const g = new Svg(), t0 = H - TK, cy = t0 + TK / 2, mid = W / 2, mk = 110;
      if (C.compShort) words(g, C.compShort, 95, cy, 38, 160, INK, F.outfit, 0, { w: W800, em: 0.06 });
      else houseMark(g, 95 - 21, cy - 21, 42, INK, 0xFF8C3FB0);
      const lw0 = tw(MO.label, 16, F.outfit, W800, 0.2) + 36;
      g.rect(190, t0 - 30, 190 + lw0, t0, CORAL);
      words(g, MO.label, 190 + 18, t0 - 15, 16, 0, INK, F.outfit, -1, { w: W800, em: 0.2 });
      crestOr(g, true, 206, t0 + 12, TK - 24);
      nameAt(g, true, 206 + TK - 10, cy, 34, mid - 90 - 20 - (206 + TK - 10), INK, F.outfit, -1, { w: W760, em: 0.06 });
      digits(g, BD, MO.score, mid - 90, mid + 90, cy, 40, LAB_DARK, F.outfit, W800, 0.06, '–', 1);
      nameAt(g, false, W - mk - TK + 4, cy, 34, W - mk - TK + 4 - (mid + 110), INK, F.outfit, 1, { w: W760, em: 0.06 });
      crestOr(g, false, W - mk - TK + 12, t0 + 12, TK - 24);
      words(g, minuteMark(clockText(LEAD)), W - mk / 2, cy, 34, mk - 20, 0xFFCFD5E4, F.mono, 0, { w: W760, cls: 'bmin' });
      return svgPart('sc', g, 0, t0 - 30, W, TK + 30);
    },
    in(t) {
      if (t >= OUT0) {
        for (const id of ['tkp', 'tkg', 'tks']) $(id).style.clipPath = 'none';
        return;
      }
      // the line drawing itself across: each panel seen up to the line's end
      const shown = W * eo(prog(t, IN0, IN0 + 0.5));
      for (const p of S.ticker.panels) $(p.id).style.clipPath = 'inset(0 ' + Math.max(0, Math.min(p.from.w, p.from.x + p.from.w - shown)) + 'px 0 0)';
      const w = eo(prog(t, IN0 + 0.4, IN0 + 0.85));
      $('sc').style.opacity = w;
      $('sc').style.transform = 'translateX(' + (70 * (1 - w)) + 'px)';
    },
  });
  // Duo pills: each side a dark pill of its own (its crest, name and its score on a disc in its colour), the moment and the
  // clock on a white pill between them. The pills come in from either side and each lands on the board's own.
  S.duo = f3('DUO', {
    fonts: ['760 40px Outfit', '820 40px Outfit', '700 40px monospace'], sub: ['#080A10', '#fff', 'grad', '#080A10'],
    panels: () => [
      { id: 'dh', style: 'background:rgba(8,10,16,.82)', from: { x: 0, y: 0, w: 540, h: H, r: H / 2 }, to: () => [BB.x[0], 0, BB.x[1] - BB.x[0], BB.u, BB.u / 2] },
      { id: 'dc', style: 'background:#fff', from: { x: 560, y: 12, w: 180, h: H - 24, r: (H - 24) / 2 },
        to: () => { const ch = BB.u * 0.76; return [BB.x[2], (BB.u - ch) / 2, BB.x[3] - BB.x[2], ch, ch / 2]; } },
      { id: 'da', style: 'background:rgba(8,10,16,.82)', from: { x: 760, y: 0, w: 540, h: H, r: H / 2 }, to: () => [BB.x[4], 0, BB.x[5] - BB.x[4], BB.u, BB.u / 2] },
    ],
    html() {
      const gh = new Svg(), ga = new Svg(), gc = new Svg(), u = H, disc = u * 0.78, hc = solid(BD.homeColour), ac = solid(BD.awayColour);
      crestOr(gh, true, 30, (u - 78) / 2, 78);
      nameAt(gh, true, 122, u / 2, 40, 540 - 11 - disc - 16 - 122, INK, F.outfit, -1, { w: W760, em: 0.04 });
      gh.circle(540 - u * 0.11 - disc / 2, u / 2, disc / 2, hc);
      scoreOne(gh, MO.score[0], 540 - u * 0.11 - disc / 2, u / 2, 52, inkOn(hc), F.outfit, { w: W800 });
      ga.circle(760 + u * 0.11 + disc / 2, u / 2, disc / 2, ac);
      scoreOne(ga, MO.score[1], 760 + u * 0.11 + disc / 2, u / 2, 52, inkOn(ac), F.outfit, { w: W800 });
      nameAt(ga, false, W - 122, u / 2, 40, W - 122 - (760 + 11 + disc + 16), INK, F.outfit, 1, { w: W760, em: 0.04 });
      crestOr(ga, false, W - 108, (u - 78) / 2, 78);
      labelClock(gc, 650, 560, 740, 12, u - 12, F.outfit, 0x99080A10, LAB_DARK, 12, 34, { lw: W800, cf: F.mono, cw: W760 });
      return '<div id="sc" class="part">' + svgPart('sch', gh, 0, 0, 540, u) + svgPart('scc', gc, 560, 0, 180, u) + svgPart('sca', ga, 760, 0, 540, u) + '</div>';
    },
    in(t) {
      if (t >= OUT0) return;
      const p = eo(prog(t, IN0, IN0 + 0.5)), c = back(prog(t, IN0 + 0.4, IN0 + 0.7));
      for (const [id, dx] of [['dh', -900], ['sch', -900], ['da', 900], ['sca', 900]]) $(id).style.transform = 'translateX(' + dx * (1 - p) + 'px)';
      for (const id of ['dc', 'scc']) { $(id).style.transform = 'scale(' + Math.max(0, c) + ')'; $(id).style.transformOrigin = '50% 50%'; }
    },
  });
  // Stadium LED: a ground's big screen: amber letters glowing on black under a fine dot matrix, the score bigger, the
  // moment and the minute in red-orange. It powers on row by row; the screen becomes the board's.
  S.led = f3('LED', {
    fonts: ['700 40px monospace'], sub: ['#050505', '#FFB21E', '#FFB21E', '#050505'],
    panels: () => [{ id: 'ledp', style: 'background:#050505;border:6px solid #1A1A1A;box-sizing:border-box', edge: [6, Math.max(1, BB.u * 0.045)],
      from: { x: 0, y: 0, w: W, h: H, r: 16 }, to: () => [BB.x[0], 0, BB.x[4] - BB.x[0], BB.u, BB.u * 0.13] }],
    html() {
      const g = new Svg(), u = H, amber = { w: W760, em: 0.12, halo: AMBER, min: 24 }, rb = 230, mid = (W - rb) / 2;
      nameAt(g, true, (40 + mid - 120) / 2, u / 2, 46, mid - 120 - 40 - 20, AMBER, F.mono, 0, amber);
      digits(g, BD, MO.score, mid - 110, mid + 110, u / 2, 70, AMBER, F.mono, W760, 0.12, '-', 1, { halo: AMBER });
      nameAt(g, false, (mid + 120 + W - rb) / 2, u / 2, 46, W - rb - mid - 120 - 20, AMBER, F.mono, 0, amber);
      words(g, MO.label, W - rb / 2, 40, 20, rb - 30, LED_RED, F.mono, 0, { w: W760, em: 0.12, halo: LED_RED });
      words(g, minuteMark(clockText(LEAD)), W - rb / 2, 84, 40, rb - 30, LED_RED, F.mono, 0, { w: W760, em: 0.12, halo: LED_RED, cls: 'bmin' });
      dotMatrix(g, 6, 6, W - 6, u - 6, u, 10);
      return svgPart('sc', g, 0, 0, W, u);
    },
    in(t) {
      if (t >= OUT0) return;
      const on = prog(t, IN0, IN0 + 0.08), rows = eo(prog(t, IN0 + 0.08, IN0 + 0.5)), k = t - IN0;
      $('ledp').style.opacity = on;
      const flick = k > 0.5 && k < 0.58 ? 0.45 : 1;
      $('sc').style.clipPath = 'inset(0 0 ' + (H * (1 - rows)) + 'px 0)';
      $('sc').style.opacity = flick;
    },
  });

  // ---------- the 3D kits ----------
  // its navy board's own first block (the competition, large), each club's shirt drawn flat in the kit colours it is given
  // (no maker's marks, no sponsor) turning in in its cell, the names on white, the moment and the score on navy, the clock
  S.kits = f1('KITS', {
    fonts: ['800 40px Outfit', '700 40px Outfit'], sub: ['#0A1250', '#fff', 'grad', '#0A1250'], round: 10,
    draw(g, s, u) {
      const kit = 140, ck = 140, sw = 220, plate = (W - s - 2 * kit - ck - sw) / 2, kf = kitFont();
      const p0 = s + kit, sc0 = p0 + plate, sc1 = sc0 + sw, p1 = sc1 + plate, k1 = p1 + kit;
      g.vgrad(s, 0, p0, u, KIT_CELL0, KIT_CELL1);
      g.vgrad(p1, 0, k1, u, KIT_CELL0, KIT_CELL1);
      g.rect(p0, 0, sc0, u, KIT_CODE);
      g.rect(sc1, 0, p1, u, KIT_CODE);
      words(g, FULL.home, (p0 + sc0) / 2, u / 2, 38, plate - 24, KIT_INK, kf, 0, { min: 22 });
      words(g, FULL.away, (sc1 + p1) / 2, u / 2, 38, plate - 24, KIT_INK, kf, 0, { min: 22 });
      g.rect(sc0, 0, sc1, u, KIT_NAVY);
      labelScore(g, (sc0 + sc1) / 2, sc0, sc1, 0, u, kf, 0xFFA9B1D6, KIT_WHITE, 14, 62, { dash: '–' });
      g.rect(k1, 0, W, u, KIT_CLOCK);
      words(g, clockText(LEAD), (k1 + W) / 2, u / 2, 44, ck - 20, KIT_WHITE, kf, 0, { cls: 'sslot' });
      for (const [id, cx, body, trim, turn] of [['kh', (s + p0) / 2, BD.homeBody, BD.homeTrim, 0.35], ['ka', (p1 + k1) / 2, BD.awayBody, BD.awayTrim, -0.35]]) {
        g.raw('<g id="' + id + '" style="transform-box:fill-box;transform-origin:center">');
        flatShirt(g, cx, u / 2, u * 0.9, body, trim, turn);
        // larger than the board's: its collar and cuffs in the trim (still no maker's mark, no sponsor)
        const w = u * 0.9 * 0.92 * Math.abs(Math.cos(turn)), hh = u * 0.9 * 0.78, x0 = cx - w / 2, y0 = u / 2 - hh / 2;
        const p = (x, y) => [x0 + x * w, y0 + y * hh];
        g.path(P([p(0.40, 0), p(0.5, 0.15), p(0.6, 0), p(0.56, 0), p(0.5, 0.085), p(0.44, 0)]), trim);
        g.path(P([p(1, 0.27), p(0.9, 0.39), p(0.865, 0.355), p(0.965, 0.235)]), trim);
        g.path(P([p(0, 0.27), p(0.1, 0.39), p(0.135, 0.355), p(0.035, 0.235)]), trim);
        g.raw('</g>');
      }
    },
    anim(t) {
      // the shirts turning in once as the strap opens, as the board's do
      for (const [id, t0] of [['kh', IN0 + 0.35], ['ka', IN0 + 0.45]]) {
        const e = $(id);
        if (!e) continue;
        const p = prog(t, t0, t0 + 1.0), c = Math.cos((1 - Math.pow(1 - p, 4)) * Math.PI * 2);
        e.style.transform = p > 0 && p < 1 ? 'scaleX(' + (Math.sign(c) || 1) * Math.max(0.12, Math.abs(c)) + ')' : 'none';
      }
    },
  });
  // ==== the 26 more looks: end ====

  // ---------- a long club name: smaller (to 24 px), then narrower, until it fits its room ----------
  // (the text itself is measured, not its box: a right-aligned name spills out to the left, where no scroll width shows)
  function fitNames() {
    const k = $('stage').getBoundingClientRect().width / 1920 || 1;
    const textW = (el) => { const r = document.createRange(); r.selectNodeContents(el); return r.getBoundingClientRect().width / k; };
    const px = (v) => parseFloat(v) || 0;
    function roomOf(row, nm) {
      if (px(getComputedStyle(nm).flexGrow) > 0) return nm.clientWidth;   // its own share of the row
      const rs = getComputedStyle(row);
      const kids = [...row.children].filter((e) => getComputedStyle(e).position !== 'absolute');
      let room = row.clientWidth - px(rs.paddingLeft) - px(rs.paddingRight) - px(rs.columnGap) * Math.max(0, kids.length - 1);
      for (const e of kids) if (e !== nm) { const cs = getComputedStyle(e); room -= e.offsetWidth + px(cs.marginLeft) + px(cs.marginRight); }
      return room;
    }
    function fit(nm, room, min) {
      let fs = px(nm.style.fontSize) || 44, guard = 0;
      while (textW(nm) > room && fs > min && guard++ < 60) { fs -= 1; nm.style.fontSize = fs + 'px'; }
      const w = textW(nm);
      if (w > room && w > 0) {
        const q = Math.max(0.55, room / w);
        nm.style.display = 'inline-block';
        if (px(getComputedStyle(nm).flexGrow) > 0) { nm.style.transformOrigin = '0 50%'; }
        else { nm.style.transformOrigin = '50% 50%'; nm.style.marginLeft = nm.style.marginRight = (-(1 - q) * nm.offsetWidth / 2) + 'px'; }
        nm.style.transform = 'scaleX(' + q + ')';
      }
    }
    for (const row of document.querySelectorAll('#strap .fitrow')) {
      const nm = row.querySelector('.nm'); if (nm) fit(nm, roomOf(row, nm), 24);
    }
    const mt = $('mt');
    if (mt) { let fs = 66, guard = 0; while (mt.scrollWidth > 1380 && fs > 34 && guard++ < 40) { fs -= 2; mt.style.fontSize = fs + 'px'; } }
    const sv = $('svname');
    if (sv) fit(sv, 304, 16);
  }

  // ---------- the frame at the page's second t ----------
  function drawFrame(tp) {
    const t = tp + LEAD;
    S[STYLE].render(t);
    const b = $('board');
    // a restart or the second half: the board is up first, and steps down into the strap as it comes in
    if (MOMENT !== 'kickoff' && t < IN0 + 0.2) {
      const p = eio(prog(t, IN0 - 0.15, IN0 + 0.12));
      b.style.opacity = 1 - p; b.style.clipPath = 'none';
      b.style.transform = 'scale(' + (BS * lerp(1, 0.92, p)) + ')';
      for (const e of b.querySelectorAll('[id]')) { e.style.transform = 'none'; e.style.opacity = e.id === 'bedge' ? 0 : 1; e.style.clipPath = 'none'; }
    } else if (!S[STYLE].moves) b.style.transform = 'scale(' + BS + ')';
    const ct = clockText(t), mm = minuteMark(ct);
    for (const c of document.querySelectorAll('.bclock, .sslot')) if (c.textContent !== ct) c.textContent = ct;
    // the minute alone, where a look shows it so (the Ticker's and the LED board's: "12'")
    for (const c of document.querySelectorAll('.bmin')) if (c.textContent !== mm) c.textContent = mm;
    boardSheen(t);
    // the end: the drawn board fades as the app's own comes back
    const f = prog(t, END_T, END_T + FADE);
    if (f > 0) b.style.opacity = String((+b.style.opacity || 0) * (1 - f));
  }

  // ---------- set up once the lettering and pictures are in ----------
  async function init() {
    const t0 = performance.now();
    if (!S[STYLE]) STYLE = 'classic';
    for (const w of [300, 500, 600, 700, 800]) await document.fonts.load(w + ' 40px Outfit');
    // the lettering the look's board draws with (the competitions' and the Lab's)
    for (const f of S[STYLE].fonts || []) { try { await document.fonts.load(f); } catch (e) { /* the face falls back */ } }
    await document.fonts.ready;
    // a picture that will not come is left out: a plain coin for a crest, nothing for the competition's logo (each kept
    // decoded, with its size, for the drawn boards)
    const ok = async (url) => {
      if (!url) return '';
      try {
        const im = new Image(); im.src = url; await im.decode();
        if (!(im.width > 0)) return '';
        IMG[url] = { el: im, w: im.naturalWidth || im.width, h: im.naturalHeight || im.height };
        return url;
      } catch (e) { return ''; }
    };
    M.hcrest = await ok(M.hcrest); M.acrest = await ok(M.acrest); M.compW = await ok(M.compW);
    M.compL = await ok(M.compL); M.compM = await ok(M.compM);
    S[STYLE].build();
    sharedDefs();
    fitNames();
    drawFrame(0);
    window.drawFrame = drawFrame;
    window.INIT_MS = performance.now() - t0;
    window.READY = true;
    if (window.onBroadcastReady) window.onBroadcastReady();
  }
  init().catch((e) => { window.INIT_ERR = String(e && e.stack || e); if (window.onBroadcastFailed) window.onBroadcastFailed(window.INIT_ERR); });
})();
