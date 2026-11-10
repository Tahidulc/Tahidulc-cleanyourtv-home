// The kick-off pop-up, live: BROADCAST.start({kind, home, away, comp, compName, compLogo, stadium, score, scorer,
// goalMin, kicksOff, sound, volume}) plays it once over a see-through page, then calls done().
(function () {
  const cvs = document.getElementById('c');
  BX.fit(cvs);
  window.addEventListener('resize', () => BX.fit(cvs));
  const LEAD = 0.15;   // the sound starts this much before the first frame (its thump lands as the crests do)
  const LEN = { kickoff: 6.6, restart: 4.4, second: 4.4 };
  const TROPHY = { pl: 'pl', facup: 'facup', leaguecup: 'leaguecup', ucl: 'ucl', uel: 'uel', uecl: 'uecl' };
  let cfg = null, kind = 'kickoff', raf = 0, finished = false, began = false;
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
      window.onBroadcastReady = play;
      BX.load('kickoff.js').catch((e) => { BX.log(e); finish(); });
    },
    stop: () => finish(),
  };
  function play() {
    if (finished || began) return;
    began = true;
    if (cfg.sound !== false) BX.sound('popup-' + kind, cfg.volume == null ? 1 : cfg.volume, 0);
    const t0 = performance.now();
    let first = true;
    const frame = (now) => {
      if (finished) return;
      const t = (now - t0) / 1000 - LEAD;
      window.drawFrame(t);
      if (first) { first = false; BX.started(); }
      if (t > LEN[kind] + 0.75) { finish(); return; }
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
  }
  function finish() {
    if (finished) return;
    finished = true;
    cancelAnimationFrame(raf);
    const g = cvs.getContext('2d');
    g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, cvs.width, cvs.height);
    BX.done();
  }
  BX.ready();
})();
