// The kit pictures' page (kits.html), out of sight: the app (KitSheets) sends jobs, one at a time; each comes back as a
// sheet of frames. KITS.job(json) with {id, team, size, angles, tilt, back, webp} (sheet.js); the answer is
// AndroidKits.sheet(id, url, cols, rows, size, n, ms), or AndroidKits.failed(id, why).
(function () {
  const none = () => {};
  const bridge = window.AndroidKits || { ready: none, sheet: none, failed: none, log: (m) => console.log(m) };
  const call = (f, ...a) => { try { bridge[f](...a); } catch (e) { /* the app has gone */ } };
  const queue = [];
  let busy = false;
  async function next() {
    if (busy || !queue.length) return;
    busy = true;
    const job = queue.shift();
    try {
      const r = await window.KITSHEET.render(job);
      call('sheet', String(job.id), r.url, r.cols, r.rows, r.size, r.n, r.ms);
    } catch (e) {
      call('failed', String(job.id), String(e && e.message || e));
    }
    busy = false;
    setTimeout(next, 0);
  }
  window.KITS = {
    job(j) {
      const job = typeof j === 'string' ? JSON.parse(j) : j;
      queue.push(job);
      next();
    },
    dispose() { if (window.KITSHEET) window.KITSHEET.dispose(); },
  };
  window.addEventListener('error', (e) => call('log', 'error: ' + e.message + ' @ ' + e.filename + ':' + e.lineno));
  // ready once the drawing has loaded
  const s = document.createElement('script');
  s.src = 'kits3d.js';
  s.onload = () => call('ready');
  s.onerror = () => call('log', 'could not load kits3d.js');
  document.body.appendChild(s);
})();
