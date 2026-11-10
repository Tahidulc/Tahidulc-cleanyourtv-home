// The first design's pre-match line-ups over the ground (stadium.html), live: BROADCAST.start({home, away, stadium, city,
// lat, lon, ko, kickoffAt, cond, compName, referee, weather, sound, volume, q, hold}) with each side {name, short,
// formation, coach, coachId, crest, color, color2, xi: [{n, name, last, x, y, pos, gk, cap, id, photo, face}], subs:
// [{n, name, last, id, gk, photo, face}]} as the classic line-ups have them (x turned round: the right-back near 1);
// weather either "9° · light rain" or {temp, text, wind, icon}. The ground is drawn on the device, lit for the place, the
// date and the kick-off; the ident, the title, both elevens with their pictures, the referee and a live countdown, then a
// brand wipe back to the channel and done(). BROADCAST.back() fades it away at once (the app's Back).
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
      const team = (t) => ({ name: t.name || '', formation: t.formation || '', coach: t.coach || '', coachId: t.coachId, color: t.color, color2: t.color2,
        short: t.short, xi: (t.xi || []).map((p) => player(p, true)), subs: (t.subs || []).map((p) => player(p, false)) });
      window.PLAN = { cond: cfg.cond === 'night' ? 'night' : cfg.cond === 'sunset' ? 'sunset' : 'day', ko: cfg.ko || '15:00', at: cfg.kickoffAt || 0 };
      window.DATA = { home: team(cfg.home), away: team(cfg.away), stadium: cfg.stadium || '', city: cfg.city || '',
        lat: cfg.lat == null ? 51.5 : +cfg.lat, lon: cfg.lon == null ? -0.1 : +cfg.lon, league: cfg.compName || '',
        referee: cfg.referee || '', weather: cfg.weather || '' };
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
      BX.load('stadium.js').catch((e) => { BX.log(e); finish(); });
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
  // its sounds, soft under the channel's own: the ident, the swell as each team comes, the board landing, a tick for each
  // player (higher line by line, keeper to forwards), the subs' shimmer, whooshes and the wipe back
  const GAIN = { ident: 0.55, whoosh: 0.35, land: 0.45, swell: 0.4, board: 0.5, tick: 0.4, soft: 0.22, shimmer: 0.3, wipe: 0.45 };
  function cueSound(c) {
    if (!cfg || cfg.sound === false) return;
    const v = (cfg.volume == null ? 1 : cfg.volume) * (GAIN[c.k] || 0.4);
    const name = c.k === 'swell' ? 'stadium-swell-' + (c.team === 'away' ? 'away' : 'home')
      : c.k === 'tick' ? 'stadium-tick' + (c.line | 0) : c.k === 'soft' ? 'stadium-tick0' : 'stadium-' + c.k;
    BX.sound(name, v, c.pan || 0);
  }
  function play() {
    if (finished || began) return;
    began = true;
    cues = (window.CUES || []).filter((c) => c.k !== 'end').map((c) => ({ ...c, at: c.t })).sort((a, b) => a.at - b.at);
    endAt = window.DUR || 40;
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
