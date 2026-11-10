// Shared by the live overlays (kickoff.html, lineups.html): the app's bridge, the canvas fitted to the screen.
// The app (TV launcher or phone) loads a page, waits for ready(), then calls BROADCAST.start(json).
(function () {
  const none = () => {};
  const bridge = window.AndroidBroadcast || { ready: none, started: none, done: none, sound: none, log: (m) => console.log(m) };
  const call = (f, ...a) => { try { bridge[f](...a); } catch (e) { /* the app has gone */ } };
  window.BX = {
    ready: () => call('ready'),
    started: () => call('started'),
    done: () => call('done'),
    sound: (name, gain, pan) => call('sound', String(name), +(gain == null ? 1 : gain), +(pan || 0)),
    log: (m) => call('log', String(m)),
    // the 1920x1080 drawing, as large as fits, in the middle (the rest stays see-through)
    fit(c) {
      const vw = window.innerWidth || 1920, vh = window.innerHeight || 1080, k = Math.min(vw / 1920, vh / 1080);
      Object.assign(c.style, { width: 1920 * k + 'px', height: 1080 * k + 'px', left: (vw - 1920 * k) / 2 + 'px', top: (vh - 1080 * k) / 2 + 'px' });
    },
    load: (src) => new Promise((res, rej) => {
      const s = document.createElement('script');
      s.src = src; s.onload = res; s.onerror = () => rej(new Error('could not load ' + src));
      document.body.appendChild(s);
    }),
    up: (s) => String(s || '').toUpperCase(),
  };
  window.addEventListener('error', (e) => BX.log('error: ' + e.message + ' @ ' + e.filename + ':' + e.lineno));
})();
