// The matchday intro, live: BROADCAST.start({comp, compName, trophy, home: {name}, away: {name}, sound, volume, music, q,
// hold}) builds its scenes, compiles every shader and draws a moment of each shot out of sight, then, held, calls
// prepared() and waits for BROADCAST.go(); else it plays at once. It covers the whole screen (the channel's last advert
// break), about 51 s, and fades back to the channel at the end, then done(). back() fades it away early.
// The music (sfx/intro-music.ogg, 51 s) plays from the page's own <audio>: the app's SoundPool keeps only a few seconds of
// decoded sound (about 1 MB), too little for it. The frames follow the music's own clock, so the cuts stay on its beats.
// music: 'page' (the default), 'app' (BX.sound('intro-music'), for an app that plays long sounds itself) or 'none'.
// q: {scale: 0.3-1 (the drawing's resolution, else chosen from a quick measure), aa, low (fewer stars, a smaller crowd)}.
(function () {
  const MUSIC = 'sfx/intro-music.ogg';
  let cfg = null, raf = 0, finished = false, began = false, going = false, ready = false, audio = null, backAt = -1;
  window.BROADCAST = {
    start(c) {
      if (cfg) return;
      cfg = typeof c === 'string' ? JSON.parse(c) : c;
      const vol = Math.max(0, Math.min(1, cfg.volume == null ? 1 : +cfg.volume));
      const music = cfg.sound === false ? 'none' : cfg.music || 'page';
      cfg._music = music;
      if (music === 'page') {
        try { audio = new Audio(MUSIC); audio.preload = 'auto'; audio.volume = vol; audio.load(); } catch (e) { audio = null; BX.log('no audio: ' + e); }
      }
      const t0 = performance.now();
      BX.load('intro.js')
        .then(() => window.INTRO.init(cfg))
        .then((info) => { BX.log('built in ' + (performance.now() - t0).toFixed(0) + ' ms: ' + JSON.stringify(info) + ' ' + BX.view()); return warm(!(cfg.q && +cfg.q.scale > 0)); })
        .then(() => audioReady())
        .then(() => {
          if (finished) return;
          ready = true;
          BX.log('ready in ' + (performance.now() - t0).toFixed(0) + ' ms at ' + window.INTRO.getScale().toFixed(2));
          if (cfg.hold && !going) BX.prepared(); else play();
        })
        .catch((e) => { BX.log('could not start: ' + (e && e.stack || e)); finish(); });
    },
    go() {
      going = true;
      if (ready) play();   // else as soon as it is ready
    },
    back() { if (began && backAt < 0) backAt = clock(); else if (!began) finish(); },
    stop: () => finish(),
  };
  const raf2 = () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
  // every shot drawn once out of sight (its shaders compiled, its buffers on the GPU), each timed to the GPU's finish;
  // the drawing's resolution lowered while a frame takes too long (unless the app set it)
  async function warm(auto) {
    const I = window.INTRO, gl = I.gl(), px = new Uint8Array(4);
    const times = I.warm();
    const cost = () => {
      const ms = [];
      for (const t of times) { const a = performance.now(); I.draw(t, 0); gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px); ms.push(performance.now() - a); }
      ms.sort((a, b) => a - b);
      return { med: ms[ms.length >> 1], max: ms[ms.length - 1] };
    };
    for (const t of times) { I.draw(t, 0); await raf2(); if (finished) return; }
    let c = cost();
    BX.log('a frame takes ' + c.med.toFixed(1) + ' ms (most ' + c.max.toFixed(0) + ') at ' + I.getScale().toFixed(2));
    while (auto && c.med > 22 && I.getScale() > 0.42) {
      I.setScale(I.getScale() * 0.8);
      await raf2();
      c = cost();
      BX.log('  at ' + I.getScale().toFixed(2) + ': ' + c.med.toFixed(1) + ' ms (most ' + c.max.toFixed(0) + ')');
    }
    I.draw(0, 0);
  }
  function audioReady() {
    if (!audio) return Promise.resolve();
    return new Promise((res) => {
      if (audio.readyState >= 4) return res();
      const done = () => { clearTimeout(to); res(); };
      const to = setTimeout(() => { BX.log('music not ready, playing anyway'); res(); }, 5000);
      audio.addEventListener('canplaythrough', done, { once: true });
      audio.addEventListener('error', () => { BX.log('music failed to load'); audio = null; done(); }, { once: true });
    });
  }
  let tStart = 0;
  // seconds into the intro: the music's own time while it plays, else the page's clock
  function clock() {
    const now = performance.now();
    let t = (now - tStart) / 1000;
    if (audio && !audio.paused && audio.currentTime > 0) {
      const ta = audio.currentTime;
      if (Math.abs(ta - t) > 0.05) { tStart = now - ta * 1000; t = ta; }
    }
    return t;
  }
  function play() {
    if (finished || began) return;
    began = true;
    const I = window.INTRO;
    if (cfg._music === 'app') BX.sound('intro-music', cfg.volume == null ? 1 : cfg.volume, 0);
    if (audio) { try { audio.currentTime = 0; const p = audio.play(); if (p && p.catch) p.catch((e) => { BX.log('music blocked: ' + e); audio = null; }); } catch (e) { audio = null; } }
    tStart = performance.now();
    let first = true, slowN = 0, last = 0;
    const frame = (now) => {
      if (finished) return;
      const t = clock();
      const d0 = performance.now();
      let fade = 1;
      if (backAt >= 0) {
        fade = 1 - (t - backAt) / 0.6;
        if (audio) audio.volume = Math.max(0, fade) * Math.max(0, Math.min(1, cfg.volume == null ? 1 : +cfg.volume));
        if (fade <= 0) { finish(); return; }
      }
      I.draw(Math.min(t, I.LEN), fade < 1 ? I.opacity(t) * fade : undefined);
      BX.meter(performance.now() - d0, now);
      if (first) { first = false; BX.started(); BX.log(BX.view()); }
      // a TV that cannot keep up: fewer pixels (a frame over 45 ms, 20 times running)
      if (last && now - last > 45) { if (++slowN >= 20 && I.getScale() > 0.42) { I.setScale(I.getScale() * 0.85); BX.log('slow: now at ' + I.getScale().toFixed(2) + ' at ' + t.toFixed(1) + ' s'); slowN = 0; } } else slowN = Math.max(0, slowN - 1);
      last = now;
      if (t >= I.LEN) { finish(); return; }
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
  }
  function finish() {
    if (finished) return;
    finished = true;
    cancelAnimationFrame(raf);
    if (audio) { try { audio.pause(); } catch (e) { /* gone */ } }
    BX.log(BX.meterWords());
    const c = document.getElementById('intro'); if (c) c.style.opacity = '0';
    BX.done();
  }
  BX.ready();
})();
