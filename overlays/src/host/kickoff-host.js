// The kick-off pop-up, live: BROADCAST.start({kind ('kickoff', 'restart': the score alone after a goal, 'second', 'full':
// full time), home, away, comp, compName, compLogo, mark, markImg, stadium, score, kicksOff, sound, volume, q, hold}) plays
// it once over a see-through page, then calls done(). With hold it gets everything ready, calls prepared() and waits for
// BROADCAST.go() (the app shows it on the moment, without the wait).
// It is drawn as compositor layers (layers.js): every part drawn once while it gets ready, the frames only placing them.
(function () {
  const LEAD = 0.15;   // the sound starts this much before the first frame (its thump lands as the crests do)
  const LEN = { kickoff: 6.6, restart: 4.4, second: 4.4, full: 4.4 };
  // each moment's sound (sfx/): after a goal and at full time the second half's, the tab's soft change in it
  const SOUND = { kickoff: 'popup-kickoff', restart: 'popup-second', second: 'popup-second', full: 'popup-second' };
  // the whole pop-up's length as the page plays it (slower and up longer than LEN, its design's own times: kickoff.js)
  const length = () => (window.POP_LEN && window.POP_LEN(kind)) || LEN[kind] + 0.62;
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
        match: { home: BX.up(cfg.home.name), away: BX.up(cfg.away.name), stadium: BX.up(cfg.stadium), comp, compName: cfg.compName || '', mark: cfg.mark || '',
          trophy: TROPHY[cfg.trophy] || TROPHY[comp] || 'generic' },
        moments: [{ n: 1, kind, t0: 0, kicksOff: cfg.kicksOff === 'away' ? 'away' : 'home', score: cfg.score || [0, 0] }],
      };
      // the competition's own mark (its logo, then its name in its own lettering) comes inline as markImg
      window.ASSET_URLS = { home: cfg.home.crest, away: cfg.away.crest, logos: cfg.compLogo ? { [comp]: cfg.compLogo } : {}, mark: cfg.markImg || '' };
      // always the full look; q only when the app gives it, to compare: {flat (no shadows), plus (added light), t3res (0.3-1: the trophy
      // drawn smaller, then scaled up)}
      const q = cfg.q && Object.keys(cfg.q).length ? cfg.q : {};
      if (q.flat) BX.flat();
      // the glows laid over as they are (q.plus: added light, as drawn before, to compare)
      window.LK_PLUS = !!q.plus;
      window.POP.q = q;
      BX.log('quality ' + JSON.stringify(q));
      window.onBroadcastReady = () => {
        BX.log('ready in ' + (window.INIT_MS || 0).toFixed(0) + ' ms');
        rehearse().then(() => {
          if (finished) return;
          if (cfg.hold && !going) BX.prepared(); else play();
        });
      };
      window.onBroadcastFailed = (e) => { BX.log('could not start: ' + e); finish(); };
      BX.load('kickoff.js').catch((e) => { BX.log(e); finish(); });
    },
    go() {
      going = true;
      if (window.READY) play();   // else as soon as it is ready
    },
    stop: () => finish(),
  };
  // The whole pop-up played through once out of sight (the stage at 0.4%, in a window still unseen when it is held), every
  // quarter second: each part's first showing costs the GPU (on the Streamer's PowerVR the first frames took one to two
  // seconds each), and that is paid here. Nothing is parked meanwhile or after (it is eight to ten seconds long).
  async function rehearse() {
    if (!window.LK || !window.drawFrame) return;
    const r0 = performance.now(), raf2 = () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
    LK.park(Infinity);
    LK.root.style.opacity = '0.004';
    for (let t = -LEAD; t <= length() + 0.13 && !finished; t += 0.25) { window.drawFrame(t); await raf2(); }
    LK.hideAll();
    await raf2();
    LK.root.style.opacity = '';
    BX.log('rehearsed in ' + (performance.now() - r0).toFixed(0) + ' ms');
  }
  function play() {
    if (finished || began) return;
    began = true;
    if (cfg.sound !== false) BX.sound(SOUND[kind], cfg.volume == null ? 1 : cfg.volume, 0);
    const t0 = performance.now();
    let first = true;
    const frame = (now) => {
      if (finished) return;
      const t = (now - t0) / 1000 - LEAD;
      const d0 = performance.now();
      window.drawFrame(t);
      BX.meter(performance.now() - d0, now);
      if (first) { first = false; BX.started(); BX.log(BX.view()); }
      if (t > length() + 0.13) { finish(); return; }
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
  }
  function finish() {
    if (finished) return;
    finished = true;
    cancelAnimationFrame(raf);
    BX.log(BX.meterWords());
    if (window.LK) LK.clear();
    BX.done();
  }
  BX.ready();
})();
