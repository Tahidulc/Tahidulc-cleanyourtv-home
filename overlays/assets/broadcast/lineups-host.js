// The classic line-ups, live: BROADCAST.start({home, away, stadium, city, lat, lon, ko, kickoffAt, cond, sound, volume})
// with each side {name, formation, crest, color, color2, short, xi: [{n, name, last, x, y, pos, gk, cap, id, photo,
// face}], subs: [{n, name, last, id, gk, photo, face}]}; x and y as FotMob's vertical layout gives them, x turned
// round (1 - x: the right-back near 1). Both teams play through, then it wipes back to the channel and calls done().
// BROADCAST.back() fades it away at once (the app's Back).
(function () {
  const cvs = document.getElementById('c');
  BX.fit(cvs);
  window.addEventListener('resize', () => BX.fit(cvs));
  window.OVERLAY = true;
  let cfg = null, raf = 0, finished = false, began = false, t0 = 0, cues = [], ci = 0, endAt = 1e9, going = false, readyNow = false;
  const now = () => (performance.now() - t0) / 1000;
  window.BROADCAST = {
    start(c) {
      if (cfg) return;
      cfg = typeof c === 'string' ? JSON.parse(c) : c;
      const player = (p, starter) => ({ n: String(p.n == null ? '' : p.n), name: p.name || p.last || '', last: p.last || p.name || '',
        id: p.id, gk: !!p.gk, ...(starter ? { x: +p.x, y: +p.y, pos: p.pos | 0, cap: !!p.cap } : {}) });
      const team = (t) => ({ name: t.name || '', formation: t.formation || '', coach: t.coach || '', color: t.color, color2: t.color2,
        short: t.short, xi: (t.xi || []).map((p) => player(p, true)), subs: (t.subs || []).map((p) => player(p, false)) });
      window.PLAN = { cond: cfg.cond === 'night' ? 'night' : 'day', ko: cfg.ko || '15:00', at: cfg.kickoffAt || 0 };
      window.DATA = { home: team(cfg.home), away: team(cfg.away), stadium: cfg.stadium || '', city: cfg.city || '',
        lat: cfg.lat == null ? 51.5 : +cfg.lat, lon: cfg.lon == null ? -0.1 : +cfg.lon, league: cfg.compName || '' };
      const photos = {}, plphotos = {};
      for (const s of ['home', 'away']) for (const p of [...(cfg[s].xi || []), ...(cfg[s].subs || [])]) {
        if (p.photo) plphotos[p.id] = p.photo;
        if (p.face) photos[p.id] = p.face;
      }
      window.ASSET_URLS = { home: cfg.home.crest, away: cfg.away.crest, photos, plphotos };
      window.onBroadcastReady = () => {
        quality(cfg.q && Object.keys(cfg.q).length ? cfg.q : auto());
        readyNow = true;
        if (cfg.hold && !going) BX.prepared(); else play();
      };
      window.onBroadcastFailed = (e) => { BX.log('could not start: ' + e); finish(); };
      BX.load('lineups.js').catch((e) => { BX.log(e); finish(); });
    },
    back() {
      if (!began) { finish(); return; }
      if (window.FADE_FROM == null) { window.FADE_FROM = now(); endAt = Math.min(endAt, window.FADE_FROM + 0.55); }
    },
    go() {
      going = true;
      if (readyNow) play();
    },
    stop: () => finish(),
  };
  // How much this TV can draw (BX.bench2d, measured now): all of it, three quarters of the canvas, or half without
  // shadows on a TV as slow as the TCL. q: {scale (0.4-1), flat}, given by the app to compare, else this choice.
  function auto() {
    const ms = BX.bench2d();
    BX.log('fill ' + ms.toFixed(1) + ' ms here');
    return ms < 8 ? {} : ms < 16 ? { scale: 0.75 } : { scale: 0.5, flat: true };
  }
  function quality(q) {
    const s = Math.max(0.4, Math.min(1, +q.scale || 1));
    if (s < 1) { cvs.width = Math.round(1920 * s); cvs.height = Math.round(1080 * s); window.RENDER_SCALE = s; }
    if (q.flat) BX.flat();
    BX.log('quality ' + JSON.stringify(q));
  }
  function cueSound(c) {
    if (!cfg || cfg.sound === false) return;
    const v = cfg.volume == null ? 1 : cfg.volume;
    if (c.k === 'tab') BX.sound('swish-tab', v, c.pan || 0);
    else if (c.k === 'out') BX.sound('swish-out', v, 0);
    else if (c.k === 'coin') BX.sound('swish-coin', v, 0);
  }
  function play() {
    if (finished || began) return;
    began = true;
    // each swish at its moment: a tab's as it comes, a change of part's just before, the coin's as it spins through
    cues = (window.CUES || []).filter((c) => c.k === 'tab' || c.k === 'out' || c.k === 'coin')
      .map((c) => ({ ...c, at: c.k === 'out' ? c.t - 0.05 : c.k === 'coin' ? c.t + 0.45 : c.t })).sort((a, b) => a.at - b.at);
    const end = (window.CUES || []).find((c) => c.k === 'end');
    endAt = end ? end.t + 0.4 : (window.DUR || 90) - 2;
    t0 = performance.now();
    let first = true;
    const frame = () => {
      if (finished) return;
      const t = now();
      while (ci < cues.length && cues[ci].at <= t) {
        if (window.FADE_FROM == null && t - cues[ci].at < 0.25) cueSound(cues[ci]);   // late ones are skipped, never bunched
        ci++;
      }
      BX.fit(cvs);
      const d0 = performance.now();
      window.drawFrame(Math.min(t, endAt));
      BX.meter(performance.now() - d0, performance.now());
      if (first) { first = false; BX.started(); BX.log(BX.view()); }
      if (t >= endAt) { finish(); return; }
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
  }
  function finish() {
    if (finished) return;
    finished = true;
    cancelAnimationFrame(raf);
    BX.log(BX.meterWords());
    const g = cvs.getContext('2d');
    g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, cvs.width, cvs.height);
    BX.done();
  }
  BX.ready();
})();
