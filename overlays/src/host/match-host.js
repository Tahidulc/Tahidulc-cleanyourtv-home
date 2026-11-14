// The match intro, live: match3d.js (src/match) draws it in WebGL as it goes, the same frame for any moment.
// BROADCAST.start({home, away, others, duels, comp, fixture, ko, ground, markImg, sound (its music, played by the app:
// AndroidBroadcast.music), volume, res (0.4 to 1: the drawing's size; else this TV's own measure), hold}) gets everything
// ready (the pictures, the scenes, every shader); held, says prepared() and waits for go(); then plays, and at its end
// fades back to the channel and says done(). back() fades it away early.
(function () {
  let cfg = null, ready = false, going = false, began = false, finished = false, raf = 0, t0 = 0, backAt = -1, len = 48;
  const stage = document.getElementById('stage');
  // the 1920 x 1080 drawing over the whole screen (looked at again each frame)
  function fit() {
    const de = document.documentElement, vw = de.clientWidth || window.innerWidth || 1920, vh = de.clientHeight || window.innerHeight || 1080;
    if (stage._w === vw && stage._h === vh) return;
    stage._w = vw; stage._h = vh;
    const k = Math.max(vw / 1920, vh / 1080);
    stage.style.transform = `translate(${(vw - 1920 * k) / 2}px, ${(vh - 1080 * k) / 2}px) scale(${k})`;
  }
  // how much this TV can draw: a few frames of the busiest shots timed to the end of the GPU's work
  function measure() {
    const M = window.MATCH3D, gl = M.renderer && M.renderer.getContext();
    const at = [9.5, 14.0, 30.5, 44.0], ms = [];
    for (const t of at) {
      const a = performance.now();
      M.draw(t);
      try { if (gl) gl.finish(); } catch (e) { /* no matter */ }
      ms.push(performance.now() - a);
    }
    ms.sort((a, b) => a - b);
    return ms[Math.floor(ms.length / 2)];
  }
  window.BROADCAST = {
    start(c) {
      if (cfg) return;
      cfg = typeof c === 'string' ? JSON.parse(c) : c;
      const M = window.MATCH3D;
      if (!M) { BX.log('match intro: no drawing'); finish(); return; }
      fit();
      const sound = M.uclStyle && M.uclStyle(cfg) ? (cfg.soundUcl || cfg.sound) : cfg.sound;
      if (sound) BX.musicLoad(sound, cfg.volume == null ? 1 : cfg.volume);
      const a = performance.now();
      M.init(cfg).then((info) => {
        if (finished) return;
        len = info.len || len;
        stage.appendChild(M.canvas);
        if (!(cfg.res > 0)) {
          const ms = measure();
          const res = ms > 40 ? 0.55 : ms > 26 ? 0.7 : ms > 18 ? 0.85 : 1;
          if (res < 1) M.setRes(res);
          BX.log('match intro: ' + ms.toFixed(1) + ' ms a frame at full size, drawn at ' + res);
        }
        M.draw(0);
        ready = true;
        BX.log('match intro ready in ' + (performance.now() - a).toFixed(0) + ' ms: ' + info.shots + ' shots, ' + (info.style || 'network'));
        if (cfg.hold && !going) BX.prepared(); else play();
      }).catch((e) => { BX.log('match intro failed: ' + e); finish(); });
    },
    go() { going = true; if (ready) play(); },
    back() { if (began && backAt < 0) backAt = performance.now(); else if (!began) finish(); },
    stop: () => finish(),
  };
  function play() {
    if (finished || began) return;
    began = true;
    t0 = performance.now();
    BX.music();
    BX.started(); BX.log(BX.view());
    raf = requestAnimationFrame(frame);
  }
  function frame(now) {
    if (finished) return;
    fit();
    const t = (performance.now() - t0) / 1000;
    const a = performance.now();
    window.MATCH3D.draw(t);
    BX.meter(performance.now() - a, performance.now());
    let op = Math.min(1, t / 0.25);
    if (backAt >= 0) op = Math.min(op, 1 - (performance.now() - backAt) / 500);
    stage.style.opacity = String(Math.max(0, op));
    if (t >= len || (backAt >= 0 && op <= 0)) { finish(); return; }
    raf = requestAnimationFrame(frame);
  }
  function finish() {
    if (finished) return;
    finished = true;
    cancelAnimationFrame(raf);
    BX.musicStop();
    if (began) BX.log('match intro: ' + BX.meterWords());
    stage.style.opacity = '0';
    BX.done();
  }
  BX.ready();
})();
