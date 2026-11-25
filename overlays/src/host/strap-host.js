// The kick-off strap in the scoreboard's own look (the first design), live: BROADCAST.start({kind ('kickoff', 'restart': the
// score after a goal, then its scorer; 'second'; 'full': full time), look (the board's, as BroadcastData.strapLook names
// it: CLASSIC GLASS LIGHT CLUB MINIMAL CRESTS STACKED CARD FROSTED KITS, the Lab's FLOODLIGHT SPLITKIT TICKER DUO LED, the
// competitions' PL_WORLD ... LIGA_MX, the retro RETRO_2004 ... RETRO_2016), home, away ({name, short, crest, color, color2,
// board (its colour on the board), retro (its name on the retro boards)}), comp, compName, compLogo, compLight (the logo for
// light grounds), compMark (the logo as the look's board cuts it), compShort, compTitle, sfx (the look's sound), stadium,
// weather, score, scorer, goalMin, goalMark ('og', 'pen'; a goal's, 'restart' only: the clock runs on from its minute),
// clock (seconds on the match clock as it starts, optional), board ({x, y, w, h}: where the app's board sits, in the
// 1920 x 1080 the page is fitted to), sound, volume, q, hold}) plays it once over a see-through page, then calls done().
// With hold it gets everything ready, calls prepared() and waits for BROADCAST.go(). The strap is page elements, not a
// canvas (as the mock-up was): the stage is fitted to the screen every frame; q.scale has no part (the screen's own pixels
// are drawn), q.flat takes every blur and shadow away.
(function () {
  const stage = document.getElementById('stage');
  // the 1920 x 1080 stage, as large as fits, in the middle (as BX.fit does for a canvas), looked at every frame
  function fit() {
    const de = document.documentElement;
    const vw = de.clientWidth || window.innerWidth || 1920, vh = de.clientHeight || window.innerHeight || 1080;
    if (stage._fitW === vw && stage._fitH === vh) return;
    stage._fitW = vw; stage._fitH = vh;
    const k = Math.min(vw / 1920, vh / 1080);
    stage.style.transform = 'translate(' + (vw - 1920 * k) / 2 + 'px,' + (vh - 1080 * k) / 2 + 'px) scale(' + k + ')';
  }
  fit();
  window.addEventListener('resize', fit);
  let cfg = null, raf = 0, finished = false, began = false, going = false, readyNow = false, len = 6.6;
  window.BROADCAST = {
    start(c) {
      if (cfg) return;
      cfg = typeof c === 'string' ? JSON.parse(c) : c;
      // any look the page draws (strap.js falls back to Classic for one it does not know)
      const look = String(cfg.look || '').toUpperCase().replace(/[^A-Z0-9_]/g, '');
      const side = (t) => ({ name: (t && t.name) || '', short: (t && t.short) || '', crest: (t && t.crest) || '', color: t && t.color,
        color2: t && t.color2, board: t && t.board, retro: (t && t.retro) || '' });
      window.STRAP_CFG = {
        kind: cfg.kind, look: look || 'CLASSIC', home: side(cfg.home), away: side(cfg.away),
        comp: cfg.comp || '', compName: cfg.compName || '', compLogo: cfg.compLogo || '', compLight: cfg.compLight || '',
        compMark: cfg.compMark || '', compShort: cfg.compShort || '', compTitle: cfg.compTitle || '', stadium: cfg.stadium || '',
        weather: cfg.weather || '', score: cfg.score || [0, 0], scorer: cfg.scorer || '', goalMin: cfg.goalMin || '',
        goalMark: cfg.goalMark || '',
        clock: cfg.clock, board: cfg.board || null, boardFont: cfg.boardFont || '',
      };
      window.onBroadcastReady = () => {
        quality(cfg.q && Object.keys(cfg.q).length ? cfg.q : auto());
        len = window.STRAP_LEN || len;
        readyNow = true;
        if (cfg.hold && !going) BX.prepared(); else play();
      };
      window.onBroadcastFailed = (e) => { BX.log('could not start: ' + e); finish(); };
      BX.load('strap.js').catch((e) => { BX.log(e); finish(); });
    },
    go() {
      going = true;
      if (readyNow) play();
    },
    stop: () => finish(),
  };
  // How much this TV can draw (BX.bench2d, measured now, the line-ups' yardstick): on a TV as slow as the TCL, no blurs
  // or shadows. The canvas pages also draw a smaller canvas there; this page has none to shrink.
  function auto() {
    const ms = BX.bench2d();
    BX.log('fill ' + ms.toFixed(1) + ' ms here');
    return ms < 8 ? {} : ms < 16 ? { scale: 0.75 } : { scale: 0.5, flat: true };
  }
  function quality(q) {
    if (q.flat) document.documentElement.classList.add('flat');
    BX.log('quality ' + JSON.stringify(q) + (q.scale ? ' (scale: none here, the page draws at the screen\'s own size)' : ''));
  }
  function play() {
    if (finished || began) return;
    began = true;
    // the look's own sound, or the one of the first nine it shares (BroadcastData.strapSound names it)
    const sfx = /^strap-[a-z0-9_]+$/.test(cfg.sfx || '') ? cfg.sfx : 'strap-' + window.STRAP_CFG.look.toLowerCase();
    if (cfg.sound !== false) BX.sound(sfx, cfg.volume == null ? 1 : cfg.volume, 0);
    const t0 = performance.now();
    let first = true;
    const frame = (now) => {
      if (finished) return;
      const t = (now - t0) / 1000;
      fit();
      const d0 = performance.now();
      window.drawFrame(Math.max(0, t));
      BX.meter(performance.now() - d0, now);
      if (first) { first = false; BX.started(); BX.log(BX.view()); }
      if (t >= len) { finish(); return; }
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
  }
  function finish() {
    if (finished) return;
    finished = true;
    cancelAnimationFrame(raf);
    BX.log(BX.meterWords());
    stage.style.visibility = 'hidden';
    BX.done();
  }
  BX.ready();
})();
