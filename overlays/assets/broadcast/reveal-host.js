// The competition intros, live: films rendered ahead (src/reveal/reveal.js, tools/reveal_render.js) played full screen.
// BROADCAST.start({clips: ['intros/pl.mp4'] (in turn; a family's own video then the tail), sound: 'intros/sound-pl.m4a'
// (played by the app, AndroidBroadcast.music: the page's own media would take the sound from the channel for good),
// soundAt (seconds into the clips it starts at), fixture: 'ARSENAL v EVERTON' (written under the Whitebrook Road Sports
// wordmark at fixtureAt seconds, or fixtureIn seconds into the last clip), firstMax (the first clip cut there), volume,
// hold}) loads them; held, says prepared() and
// waits for go(); then plays, and at the end fades back to the channel and says done(). back() fades it away early.
(function () {
  let cfg = null, vids = [], cur = 0, began = false, finished = false, ready = false, going = false, raf = 0, backAt = -1,
    offset = 0, musicOn = false, total = 0;
  const stage = document.getElementById('stage'), fx = document.getElementById('fixture');
  const ss = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
  window.BROADCAST = {
    start(c) {
      if (cfg) return;
      cfg = typeof c === 'string' ? JSON.parse(c) : c;
      const clips = (cfg.clips || []).filter(Boolean);
      if (!clips.length) { finish(); return; }
      fit();
      for (const src of clips) {
        const v = document.createElement('video');
        v.muted = true; v.playsInline = true; v.preload = 'auto'; v.setAttribute('playsinline', '');
        v.src = src; stage.insertBefore(v, fx); vids.push(v);
        v.addEventListener('ended', next);
      }
      fx.textContent = String(cfg.fixture || '');
      if (cfg.sound) BX.musicLoad(cfg.sound, cfg.volume == null ? 1 : cfg.volume);
      const t0 = performance.now();
      loaded(vids[0], 10000).then((ok) => {
        if (finished) return;
        if (!ok) { BX.log('intro would not load: ' + clips[0]); finish(); return; }
        for (let i = 1; i < vids.length; i++) vids[i].load();
        ready = true;
        BX.log('intro ready in ' + (performance.now() - t0).toFixed(0) + ' ms: ' + clips.join(', '));
        if (cfg.hold && !going) BX.prepared(); else play();
      });
    },
    go() { going = true; if (ready) play(); },
    back() { if (began && backAt < 0) backAt = performance.now(); else if (!began) finish(); },
    stop: () => finish(),
  };
  function loaded(v, ms) {
    return new Promise((res) => {
      if (v.readyState >= 3) return res(true);
      const to = setTimeout(() => res(v.readyState >= 2), ms);
      v.addEventListener('canplaythrough', () => { clearTimeout(to); res(true); }, { once: true });
      v.addEventListener('error', () => { clearTimeout(to); res(false); }, { once: true });
    });
  }
  // the screen covered, the 1920 x 1080 drawing scaled to fill it (looked at again each frame)
  function fit() {
    const de = document.documentElement, vw = de.clientWidth || window.innerWidth || 1920, vh = de.clientHeight || window.innerHeight || 1080;
    if (stage._w === vw && stage._h === vh) return;
    stage._w = vw; stage._h = vh;
    const k = Math.max(vw / 1920, vh / 1080);
    stage.style.transform = `translate(${(vw - 1920 * k) / 2}px, ${(vh - 1080 * k) / 2}px) scale(${k})`;
  }
  // seconds into the whole intro (the clips before this one, then this one's own time)
  const clock = () => offset + (vids[cur] ? Math.min(vids[cur].currentTime, cfg.firstMax && cur === 0 ? cfg.firstMax : 1e9) : 0);
  function startMusic() {
    if (musicOn || !cfg.sound) return;
    musicOn = true;
    BX.music();
  }
  function play() {
    if (finished || began) return;
    began = true;
    const v = vids[0];
    v.style.display = 'block';
    const p = v.play();
    const go = () => {
      BX.started(); BX.log(BX.view());
      if (!(cfg.soundAt > 0)) startMusic();
      raf = requestAnimationFrame(frame);
    };
    if (p && p.then) p.then(go).catch((e) => { BX.log('intro would not play: ' + e); finish(); }); else go();
  }
  function next() {
    if (finished) return;
    const was = vids[cur];
    offset += cur === 0 && cfg.firstMax ? Math.min(was.duration || 0, cfg.firstMax) : (was.duration || 0);
    if (cur + 1 >= vids.length) { total = offset; ending(); return; }
    cur++;
    const v = vids[cur];
    v.style.display = 'block';
    v.play().catch(() => {});
    was.style.display = 'none'; was.pause();
  }
  // the end: the picture fades to the channel (the film itself goes dark meanwhile)
  let endAt = -1;
  function ending() { if (endAt < 0) endAt = performance.now(); }
  function frame() {
    if (finished) return;
    fit();
    const t = clock();
    const v = vids[cur];
    // the first clip cut at firstMax (a family's long video)
    if (cur === 0 && cfg.firstMax && v.currentTime >= cfg.firstMax && vids.length > 1) next();
    if (cfg.soundAt > 0 && t >= cfg.soundAt) startMusic();
    const len = cur === vids.length - 1 ? offset + (v.duration || 20) : 1e9;   // the fade out over the last clip's end
    let op = Math.min(1, t / 0.3) * (1 - ss(len - 0.7, len, t));
    if (endAt >= 0) op = Math.min(op, 1 - (performance.now() - endAt) / 400);
    if (backAt >= 0) op = Math.min(op, 1 - (performance.now() - backAt) / 500);
    stage.style.opacity = String(Math.max(0, op));
    const fxAt = cfg.fixtureIn != null ? (cur === vids.length - 1 ? offset + cfg.fixtureIn : 1e9) : cfg.fixtureAt;
    const fa = cfg.fixture ? ss(fxAt, fxAt + 0.7, t) : 0;
    fx.style.opacity = String(fa);
    fx.style.transform = `translateY(${(10 * (1 - fa)).toFixed(1)}px)`;
    if (op <= 0 && (endAt >= 0 || backAt >= 0)) { finish(); return; }
    raf = requestAnimationFrame(frame);
  }
  function finish() {
    if (finished) return;
    finished = true;
    cancelAnimationFrame(raf);
    for (const v of vids) { try { v.pause(); } catch (e) { /* gone */ } }
    BX.musicStop();
    stage.style.opacity = '0';
    BX.done();
  }
  BX.ready();
})();
