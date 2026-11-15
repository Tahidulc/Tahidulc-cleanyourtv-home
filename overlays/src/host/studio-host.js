// The studio line-ups (studio.html), live: BROADCAST.start({home, away, stadium, ko, comp: {key, name, logo}, sound,
// volume, q, hold}) with each side as the classic line-ups have it ({name, short, formation, coach, crest, color, color2,
// xi: [{n, name, first, last, gk, cap, x, y, pos, role, country, photo, face}], subs: [...]}) and the kit it wears
// (espnId, kitType). studio3d.js (src/studio) makes every part ready; held, it says prepared() and waits for go(); then it
// plays: the music (intros/sound-studio.m4a, played by the app), a swish as each position's page comes, a pluck as each
// player does, and done() at the end. BROADCAST.back() fades it away at once (the app's Back).
(function () {
  window.OVERLAY = true;
  let cfg = null, raf = 0, finished = false, began = false, going = false, readyNow = false, t0 = 0, cues = [], ci = 0, len = 45, backAt = -1;
  const ORDER = ['GOALKEEPER', 'DEFENDERS', 'MIDFIELDERS', 'FORWARDS'];

  /** Each starter's page: the keeper; else FotMob's usual position; else the formation's lines (defence, middle, attack). */
  function grouping(team) {
    const xi = team.xi || [];
    const lines = String(team.formation || '').split(/[^0-9]+/).map(Number).filter((n) => n > 0);
    const outfield = xi.filter((p) => !p.gk).slice().sort((a, b) => (+a.y || 0) - (+b.y || 0));
    const byLine = new Map();
    if (lines.length >= 2 && lines.reduce((s, n) => s + n, 0) === outfield.length) {
      let i = 0;
      lines.forEach((n, li) => {
        for (let k = 0; k < n; k++) byLine.set(outfield[i++], li === 0 ? 'DEFENDERS' : li === lines.length - 1 ? 'FORWARDS' : 'MIDFIELDERS');
      });
    }
    return (p) => {
      if (p.gk) return 'GOALKEEPER';
      const r = p.role == null ? -1 : +p.role;
      if (r >= 1 && r <= 3) return ORDER[r];
      return byLine.get(p) || 'MIDFIELDERS';
    };
  }
  const firstOf = (p) => {
    if (p.first) return p.first;
    const name = String(p.name || ''), last = String(p.last || '');
    return last && name.endsWith(last) ? name.slice(0, name.length - last.length).trim() : '';
  };
  function side(t) {
    const group = grouping(t);
    const players = (t.xi || []).map((p) => ({ n: String(p.n == null ? '' : p.n), first: firstOf(p), last: p.last || p.name || '', name: p.name || '',
      cap: !!p.cap, group: group(p), country: p.country || '', photo: p.photo || '', face: p.face || '', x: +p.x || 0 }));
    // each page left to right as the side lines up: the right-back first (x turned round: the right near 1)
    players.sort((a, b) => b.x - a.x);
    return { name: t.name || '', short: t.short || t.name || '', formation: t.formation || '', coach: t.coach || '', crest: t.crest || '',
      color: t.color || '', color2: t.color2 || '', espnId: t.espnId || '', kitType: t.kitType || '', players,
      subs: (t.subs || []).map((s) => ({ n: String(s.n == null ? '' : s.n), first: firstOf(s), last: s.last || s.name || '', name: s.name || '' })) };
  }
  // the sounds, under the music: the family's swish, a stab and a pluck a player in the team's key, whooshes
  const GAIN = { in: 0.45, spin: 0.42, swish: 0.75, stab: 0.42, pluck: 0.24, out: 0.45 };
  function cueSound(c) {
    if (!cfg || cfg.sound === false) return;
    const v = (cfg.volume == null ? 1 : cfg.volume) * (GAIN[c.k] || 0.4), key = c.side === 'away' ? 'a' : 'h';
    const name = c.k === 'in' ? 'studio-in' : c.k === 'spin' ? 'studio-whoosh' : c.k === 'swish' ? 'studio-swish'
      : c.k === 'stab' ? 'studio-stab-' + key : c.k === 'pluck' ? 'studio-pluck-' + key + ((c.i | 0) % 6) : 'studio-out';
    BX.sound(name, v, c.k === 'pluck' ? ((c.i | 0) % 2 ? 0.35 : -0.35) : c.k === 'in' || c.k === 'out' ? -0.3 : 0);
  }
  window.BROADCAST = {
    start(c) {
      if (cfg) return;
      cfg = typeof c === 'string' ? JSON.parse(c) : c;
      if (cfg.sound !== false) BX.musicLoad('intros/sound-studio.m4a', cfg.volume == null ? 1 : cfg.volume);
      const a = performance.now();
      BX.load('studio3d.js').then(() => window.STUDIO.init({ home: side(cfg.home || {}), away: side(cfg.away || {}), comp: cfg.comp || { key: '', name: cfg.compName || '' },
        stadium: cfg.stadium || '', ko: cfg.ko || '', q: cfg.q || {} })).then((info) => {
        if (finished) return;
        len = info.len;
        cues = info.cues.slice();
        readyNow = true;
        BX.log('studio line-ups ready in ' + (performance.now() - a).toFixed(0) + ' ms: shirt ' + info.shirtSize + ' px, '
          + (info.shirtMs || 0).toFixed(1) + ' ms a frame');
        if (cfg.hold && !going) BX.prepared(); else play();
      }).catch((e) => { BX.log('studio line-ups failed: ' + e); finish(); });
    },
    back() {
      if (!began) { finish(); return; }
      if (backAt < 0) backAt = performance.now();
    },
    go() {
      going = true;
      if (readyNow) play();
    },
    stop: () => finish(),
  };
  function play() {
    if (finished || began) return;
    began = true;
    t0 = performance.now();
    BX.music();
    let first = true;
    const frame = () => {
      if (finished) return;
      const t = (performance.now() - t0) / 1000;
      while (ci < cues.length && cues[ci].at <= t) {
        if (backAt < 0 && t - cues[ci].at < 0.25) cueSound(cues[ci]);   // late ones are skipped, never bunched
        ci++;
      }
      const d0 = performance.now();
      window.STUDIO.frame(Math.min(t, len));
      let op = 1;
      if (backAt >= 0) op = 1 - (performance.now() - backAt) / 500;
      else if (t > len - 0.5) op = (len - t) / 0.5;
      LK.fade(Math.max(0, op));
      BX.meter(performance.now() - d0, performance.now());
      if (first) { first = false; BX.started(); BX.log(BX.view()); }
      if (t >= len || op <= 0) { finish(); return; }
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
  }
  function finish() {
    if (finished) return;
    finished = true;
    cancelAnimationFrame(raf);
    BX.musicStop();
    if (began) BX.log('studio line-ups: ' + BX.meterWords());
    if (window.LK) LK.clear();
    BX.done();
  }
  BX.ready();
})();
