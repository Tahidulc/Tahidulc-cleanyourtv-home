// The matchday intro, live: BROADCAST.start({comp, compName, trophy, home: {name}, away: {name}, sound, volume, music, q,
// hold}) builds its scenes, compiles every shader and draws a moment of each shot out of sight, then, held, calls
// prepared() and waits for BROADCAST.go(); else it plays at once. It covers the whole screen (the channel's last advert
// break), about 51 s, and fades back to the channel at the end, then done(). back() fades it away early.
// The music (sfx/intro-music.ogg, 51 s) plays from the page's own <audio>: the app's SoundPool keeps only a few seconds of
// decoded sound (about 1 MB), too little for it. The frames follow the music's own clock, so the cuts stay on its beats.
// music: 'page' (the default), 'app' (BX.sound('intro-music'), for an app that plays long sounds itself) or 'none'.
// q: {scale: 0.3-1 (the drawing's resolution, else chosen from a quick measure), aa, low (fewer stars, a smaller crowd)}.
// footage: true (or a path): the first footageEnd seconds (40) are the real-footage film (footage/intro-footage.mp4, made in
// step with the same music) in a <video>, and three.js draws only from then on (the trophy and the card). When the film is
// missing or will not play, the whole intro is drawn as before. A film of the family's own (a file:// address) may be
// shorter: startAt (seconds into the music) begins the music there so that the film ends on the flash into the trophy;
// footageFallback: the built-in film, played instead (from the music's start) when the own one will not load.
(function () {
  const MUSIC = 'sfx/intro-music.ogg';
  let cfg = null, raf = 0, finished = false, began = false, going = false, ready = false, audio = null, backAt = -1, video = null, FOOT = 40, T0 = 0;
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
      if (cfg.footage) {
        FOOT = +cfg.footageEnd > 0 ? +cfg.footageEnd : 40;
        T0 = Math.max(0, Math.min(FOOT - 1, +cfg.startAt || 0));
        try {
          video = document.createElement('video');
          video.muted = true; video.playsInline = true; video.preload = 'auto'; video.setAttribute('playsinline', '');
          video.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;object-fit:cover;opacity:0;background:#000;';
          video.src = typeof cfg.footage === 'string' ? cfg.footage : 'footage/intro-footage.mp4';
          document.body.appendChild(video);
          video.load();
        } catch (e) { video = null; BX.log('no film: ' + e); }
      }
      const t0 = performance.now();
      BX.load('intro.js')
        .then(() => window.INTRO.init(cfg))
        .then((info) => { BX.log('built in ' + (performance.now() - t0).toFixed(0) + ' ms: ' + JSON.stringify(info) + ' ' + BX.view()); return warm(!(cfg.q && +cfg.q.scale > 0)); })
        .then(() => audioReady())
        .then(() => filmReady())
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
    let times = I.warm();
    // with the film, only the shots after it are drawn here
    if (video) { const late = times.filter((t) => t >= FOOT - 0.5); if (late.length) times = late; }
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
  function dropFilm() {
    if (!video) return;
    try { video.pause(); video.removeAttribute('src'); video.load(); video.remove(); } catch (e) { /* gone */ }
    video = null;
  }
  // the film loaded enough to play through (it is in the app's own files); an own film that will not load gives way to
  // the built-in one (from the music's start); else the intro is drawn whole
  function filmReady() {
    if (!video) return Promise.resolve();
    const once = () => new Promise((res) => {
      if (video.readyState >= 3) return res(true);
      let to = 0;
      const ok = () => { clearTimeout(to); res(true); };
      const bad = () => { clearTimeout(to); res(false); };
      to = setTimeout(bad, 8000);
      video.addEventListener('canplaythrough', ok, { once: true });
      video.addEventListener('error', bad, { once: true });
    });
    return once().then((ok) => {
      if (ok || !video) return;
      if (cfg.footageFallback) {
        BX.log('own film would not load, the built-in one instead');
        T0 = 0;
        try { video.src = cfg.footageFallback; video.load(); } catch (e) { dropFilm(); return; }
        return once().then((ok2) => { if (!ok2) { BX.log('film failed to load, drawing the whole intro'); dropFilm(); } });
      }
      BX.log('film failed to load, drawing the whole intro');
      dropFilm();
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
    if (audio) { try { audio.currentTime = T0; const p = audio.play(); if (p && p.catch) p.catch((e) => { BX.log('music blocked: ' + e); audio = null; }); } catch (e) { audio = null; } }
    if (video) {
      try { video.currentTime = 0; const p = video.play(); if (p && p.catch) p.catch((e) => { BX.log('film blocked: ' + e); dropFilm(); }); } catch (e) { dropFilm(); }
    }
    tStart = performance.now() - T0 * 1000;
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
      // the fade in from where the music began (a shorter own film starts part way in)
      const lead = T0 > 0 ? Math.max(0, Math.min(1, (t - T0) / 0.35)) : 1;
      if (video && t < FOOT) {
        // the film, kept with the music (a stall or a late start put right); three.js idle meanwhile
        const vt = t - T0;
        if (!video.seeking && Math.abs(video.currentTime - vt) > 0.3 && vt < (video.duration || 1e9) - 0.1) { try { video.currentTime = Math.max(0, vt); } catch (e) { /* next frame */ } }
        video.style.opacity = String(I.opacity(t) * lead * fade);
      } else {
        if (video) { dropFilm(); BX.log('film over at ' + t.toFixed(2) + ' s'); }
        const op = I.opacity(t) * lead * fade;
        I.draw(Math.min(t, I.LEN), op < 1 ? op : undefined);
      }
      BX.meter(performance.now() - d0, now);
      if (first) { first = false; BX.started(); BX.log(BX.view()); }
      // a TV that cannot keep up: fewer pixels (a frame over 45 ms, 20 times running)
      if (video) slowN = 0; else if (last && now - last > 45) { if (++slowN >= 20 && I.getScale() > 0.42) { I.setScale(I.getScale() * 0.85); BX.log('slow: now at ' + I.getScale().toFixed(2) + ' at ' + t.toFixed(1) + ' s'); slowN = 0; } } else slowN = Math.max(0, slowN - 1);
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
    dropFilm();
    BX.log(BX.meterWords());
    const c = document.getElementById('intro'); if (c) c.style.opacity = '0';
    BX.done();
  }
  BX.ready();
})();
