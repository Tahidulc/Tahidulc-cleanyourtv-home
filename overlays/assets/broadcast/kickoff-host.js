// The kick-off pop-up, live: BROADCAST.start({kind, home, away, comp, compName, compLogo, stadium, score, scorer,
// goalMin, kicksOff, sound, volume, q, hold}) plays it once over a see-through page, then calls done(). With hold it
// gets everything ready, calls prepared() and waits for BROADCAST.go() (the app shows it on the moment, without the wait).
(function () {
  const cvs = document.getElementById('c');
  BX.fit(cvs);
  window.addEventListener('resize', () => BX.fit(cvs));
  const LEAD = 0.15;   // the sound starts this much before the first frame (its thump lands as the crests do)
  const LEN = { kickoff: 6.6, restart: 4.4, second: 4.4 };
  const TROPHY = { pl: 'pl', facup: 'facup', leaguecup: 'leaguecup', ucl: 'ucl', uel: 'uel', uecl: 'uecl' };
  let cfg = null, kind = 'kickoff', raf = 0, finished = false, began = false, going = false;
  window.BROADCAST = {
    start(c) {
      if (cfg) return;
      cfg = typeof c === 'string' ? JSON.parse(c) : c;
      kind = LEN[cfg.kind] ? cfg.kind : 'kickoff';
      const side = (t) => ({ name: t.name || '', color: t.color, color2: t.color2, short: t.short, xi: [], subs: [] });
      const comp = cfg.comp || 'other';
      window.PLAN = { cond: 'day', ko: '15:00', seq: [], dur: 12 };
      window.DATA = { home: side(cfg.home), away: side(cfg.away), lat: 51.5, lon: -0.1 };
      window.POP = {
        background: 'none', captions: false, cuts: [], dur: 12,
        match: { home: BX.up(cfg.home.name), away: BX.up(cfg.away.name), stadium: BX.up(cfg.stadium), comp, compName: cfg.compName || '',
          trophy: TROPHY[cfg.trophy] || TROPHY[comp] || 'generic' },
        moments: [{ n: 1, kind, t0: 0, kicksOff: cfg.kicksOff === 'away' ? 'away' : 'home', score: cfg.score || [0, 0],
          goalMin: cfg.goalMin || '', scorer: BX.up(cfg.scorer) }],
      };
      window.ASSET_URLS = { home: cfg.home.crest, away: cfg.away.crest, logos: cfg.compLogo ? { [comp]: cfg.compLogo } : {} };
      window.onBroadcastReady = () => {
        quality(cfg.q && Object.keys(cfg.q).length ? cfg.q : auto());
        if (cfg.hold && !going) BX.prepared(); else play();
      };
      window.onBroadcastFailed = (e) => { BX.log('could not start: ' + e); finish(); };
      BX.load('kickoff.js').catch((e) => { BX.log(e); finish(); });
    },
    go() {
      going = true;
      if (window.READY && quality.done) play();   // else as soon as it is ready
    },
    stop: () => finish(),
  };
  // How much this TV can draw, from the trophy drawn a few times now (out of sight: the first also builds it and
  // compiles its shaders, which took 0.6 s on the TCL and froze the pop-up's start): all of it; a smaller canvas with the
  // trophy every other frame; or, on a TV as slow as the TCL (46 ms a trophy), half the canvas, no shadows and the
  // trophy smaller (from 6 frames a second to 23 there).
  function auto() {
    const T = window.TROPHY3D;
    if (!T) return {};
    try {
      const kind3 = POP.match.trophy, probe = document.createElement('canvas').getContext('2d', { willReadFrequently: true });
      probe.canvas.width = probe.canvas.height = 1;
      const draw = (r) => { probe.drawImage(T.render(kind3, r, 0, 0), 0, 0, 1, 1); probe.getImageData(0, 0, 1, 1); };
      draw(-1.25);
      const b0 = performance.now();
      for (const r of [-1.0, -0.6, -0.2]) draw(r);
      const ms = (performance.now() - b0) / 3;
      BX.log('trophy ' + ms.toFixed(1) + ' ms a frame here, fill ' + BX.bench2d().toFixed(1) + ' ms');
      return ms < 12 ? {} : ms < 25 ? { scale: 0.75, t3every: 2, t3res: 0.75 } : { scale: 0.5, flat: true, t3every: 2, t3res: 0.5 };
    } catch (e) {
      return { scale: 0.5, flat: true };
    }
  }
  // how much to draw, for a slower TV: q {scale (0.4-1: the canvas's size), flat (no shadows or blurs), t3every (the
  // trophy drawn again every n frames), t3res (0.3-1: the trophy's canvas)}
  function quality(q) {
    const s = Math.max(0.4, Math.min(1, +q.scale || 1));
    if (s < 1) { cvs.width = Math.round(1920 * s); cvs.height = Math.round(1080 * s); window.RENDER_SCALE = s; }
    if (q.flat) BX.flat();
    if (+q.t3every > 1) window.T3_EVERY = Math.round(+q.t3every);
    if (+q.t3res > 0 && +q.t3res < 1 && window.TROPHY3D && window.TROPHY3D.setRes) window.TROPHY3D.setRes(+q.t3res);
    BX.log('quality ' + JSON.stringify(q));
    quality.done = true;
  }
  function play() {
    if (finished || began) return;
    began = true;
    if (cfg.sound !== false) BX.sound('popup-' + kind, cfg.volume == null ? 1 : cfg.volume, 0);
    const t0 = performance.now();
    let first = true;
    const frame = (now) => {
      if (finished) return;
      const t = (now - t0) / 1000 - LEAD;
      BX.fit(cvs);
      const d0 = performance.now();
      window.drawFrame(t);
      BX.meter(performance.now() - d0, now);
      if (first) { first = false; BX.started(); BX.log(BX.view()); }
      if (t > LEN[kind] + 0.75) { finish(); return; }
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
