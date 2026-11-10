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
    // the 1920x1080 drawing, as large as fits, in the middle (the rest stays see-through). Looked at again on every
    // frame (refit): a WebView can learn its real size after the page has started, without a resize event.
    fit(c) {
      const de = document.documentElement;
      const vw = de.clientWidth || window.innerWidth || 1920, vh = de.clientHeight || window.innerHeight || 1080;
      if (c._fitW === vw && c._fitH === vh) return;
      c._fitW = vw; c._fitH = vh;
      const k = Math.min(vw / 1920, vh / 1080);
      Object.assign(c.style, { width: 1920 * k + 'px', height: 1080 * k + 'px', left: (vw - 1920 * k) / 2 + 'px', top: (vh - 1080 * k) / 2 + 'px' });
    },
    // what the page sees, for the app's log (a wrong size shows here first)
    view: () => 'view ' + document.documentElement.clientWidth + 'x' + document.documentElement.clientHeight + ' (inner ' + window.innerWidth + 'x' + window.innerHeight + ') at ' + (window.devicePixelRatio || 1),
    load: (src) => new Promise((res, rej) => {
      const s = document.createElement('script');
      s.src = src; s.onload = res; s.onerror = () => rej(new Error('could not load ' + src));
      document.body.appendChild(s);
    }),
    up: (s) => String(s || '').toUpperCase(),
  };
  window.addEventListener('error', (e) => BX.log('error: ' + e.message + ' @ ' + e.filename + ':' + e.lineno));
})();
