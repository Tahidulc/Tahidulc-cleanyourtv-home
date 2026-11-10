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
  const STYLE = String(C.look || 'FROSTED').toLowerCase();
  const MOMENT = C.kind === 'restart' || C.kind === 'second' ? C.kind : 'kickoff';
  const M = {
    home: esc(up(C.home.name)), away: esc(up(C.away.name)),
    hs: esc(up(C.home.short || C.home.name).slice(0, 3)), as: esc(up(C.away.short || C.away.name).slice(0, 3)),
    hc: hexOk(C.home.color, '#C8102E'), ac: hexOk(C.away.color, '#1B3F8F'),
    hcrest: C.home.crest || '', acrest: C.away.crest || '', compW: C.compLogo || '',
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
  // the drawn board where the app's sits: its height matched, left-aligned (right-aligned for a board on the right)
  function placeBoard(radius) {
    const b = $('board'), el = b.firstElementChild;
    const nw = el.offsetWidth || 400, nh = el.offsetHeight || BH;
    BS = RECT.h / nh;
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
    } else b.style.transform = 'scale(' + BS + ')';
    const ct = clockText(t);
    for (const c of document.querySelectorAll('.bclock, .sslot')) if (c.textContent !== ct) c.textContent = ct;
    boardSheen(t);
    // the end: the drawn board fades as the app's own comes back
    const f = prog(t, END_T, END_T + FADE);
    if (f > 0) b.style.opacity = String((+b.style.opacity || 0) * (1 - f));
  }

  // ---------- set up once the lettering and pictures are in ----------
  async function init() {
    const t0 = performance.now();
    for (const w of [300, 500, 600, 700, 800]) await document.fonts.load(w + ' 40px Outfit');
    await document.fonts.ready;
    // a picture that will not come is left out: a plain coin for a crest, nothing for the competition's logo
    const ok = async (url) => { if (!url) return ''; try { const im = new Image(); im.src = url; await im.decode(); return im.width > 0 ? url : ''; } catch (e) { return ''; } };
    M.hcrest = await ok(M.hcrest); M.acrest = await ok(M.acrest); M.compW = await ok(M.compW);
    if (!S[STYLE]) throw new Error('no look ' + STYLE);
    S[STYLE].build();
    fitNames();
    drawFrame(0);
    window.drawFrame = drawFrame;
    window.INIT_MS = performance.now() - t0;
    window.READY = true;
    if (window.onBroadcastReady) window.onBroadcastReady();
  }
  init().catch((e) => { window.INIT_ERR = String(e && e.stack || e); if (window.onBroadcastFailed) window.onBroadcastFailed(window.INIT_ERR); });
})();
