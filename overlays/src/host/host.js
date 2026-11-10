// Shared by the live overlays (kickoff.html, lineups.html): the app's bridge, the canvas fitted to the screen.
// The app (TV launcher or phone) loads a page, waits for ready(), then calls BROADCAST.start(json).
(function () {
  const none = () => {};
  const bridge = window.AndroidBroadcast || { ready: none, started: none, prepared: none, done: none, sound: none, log: (m) => console.log(m) };
  const call = (f, ...a) => { try { bridge[f](...a); } catch (e) { /* the app has gone */ } };
  window.BX = {
    ready: () => call('ready'),
    started: () => call('started'),
    prepared: () => call('prepared'),
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
    // how it ran, for the app's log: frames drawn, frames a second, the drawing's own time each frame, the GPU
    meter(ms, now) {
      const m = BX._m || (BX._m = { n: 0, sum: 0, max: 0, first: now, last: now, slow: 0 });
      m.n++; m.sum += ms; m.max = Math.max(m.max, ms); if (now - m.last > 50) m.slow++; m.last = now;
    },
    meterWords() {
      const m = BX._m; if (!m || m.n < 2) return 'no frames';
      const secs = (m.last - m.first) / 1000;
      let gpu = '?';
      try {
        const gl = document.createElement('canvas').getContext('webgl');
        const ext = gl && gl.getExtension('WEBGL_debug_renderer_info');
        gpu = gl ? (ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER)) : 'no webgl';
      } catch (e) { gpu = 'error'; }
      return 'frames ' + m.n + ' in ' + secs.toFixed(1) + ' s (' + (m.n / Math.max(0.01, secs)).toFixed(1) + ' a second, ' + m.slow +
        ' over 50 ms), drawing ' + (m.sum / m.n).toFixed(1) + ' ms a frame (most ' + m.max.toFixed(0) + '), trophy ' + (window.T3MS ? (window.T3MS.sum / window.T3MS.n).toFixed(1) + ' ms' : 'none') + ', GPU ' + gpu;
    },
    // how long this TV's chip takes over a full-screen fill with a shadowed bar (ms; the TCL's is the yardstick)
    bench2d() {
      try {
        const c = document.createElement('canvas'); c.width = 1920; c.height = 1080;
        const g = c.getContext('2d'), probe = document.createElement('canvas').getContext('2d', { willReadFrequently: true });
        probe.canvas.width = probe.canvas.height = 1;
        const once = (i) => {
          const gr = g.createLinearGradient(0, 0, 1920, 1080); gr.addColorStop(0, '#0a1842'); gr.addColorStop(1, 'rgb(' + (i * 40) + ',80,160)');
          g.fillStyle = gr; g.fillRect(0, 0, 1920, 1080);
          g.shadowColor = 'rgba(0,0,0,0.5)'; g.shadowBlur = 16; g.fillStyle = '#1b3a80'; g.fillRect(200, 600, 1500, 90); g.shadowBlur = 0;
          probe.drawImage(c, 0, 0, 1, 1); probe.getImageData(0, 0, 1, 1);
        };
        once(0);
        const t0 = performance.now();
        for (let i = 1; i <= 4; i++) once(i);
        return (performance.now() - t0) / 4;
      } catch (e) { return 99; }
    },
    // no shadows or blurs on any canvas (the costliest parts to draw on a slower TV's chip)
    flat() {
      const P = CanvasRenderingContext2D.prototype;
      for (const k of ['shadowBlur', 'filter']) Object.defineProperty(P, k, { configurable: true, get() { return k === 'filter' ? 'none' : 0; }, set() { } });
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
