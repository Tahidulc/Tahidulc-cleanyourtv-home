// node match_render.js CFG_JSON OUT_DIR [fps] [stills: t,t,t] [part: from-to seconds]
// Records the match intro (src/match/*.js, the page "match") frame by frame in headless Chromium (WebGL by SwiftShader),
// 1920 x 1080 JPEGs in OUT_DIR, for a look at it before it goes to the TVs. CFG_JSON: the page's settings (see
// src/match/match.js) with pictures as local paths (crest, photo, mark), made inline here. With stills, only those
// moments, as PNG.
const fs = require('fs'), path = require('path');
const { execFileSync } = require('child_process');
const { chromium } = require('playwright');
(async () => {
  const [cfgPath, out, fpsA = '30', stills = '', part = ''] = process.argv.slice(2);
  const fps = +fpsA;
  fs.mkdirSync(out, { recursive: true });
  const src = path.resolve(__dirname, '../src'), fonts = path.resolve(__dirname, '../assets/broadcast/fonts');
  const bundle = path.join(out, 'match3d.js');
  execFileSync('npx', ['--yes', 'esbuild@0.24.0', path.join(src, 'match/match.js'), '--bundle', '--format=iife', '--target=es2019',
    '--alias:three=' + path.join(src, 'vendor/three.module.js'), '--outfile=' + bundle, '--log-level=warning'], { stdio: 'inherit' });
  const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));
  const inline = (p) => (p && fs.existsSync(p) ? 'data:image/png;base64,' + fs.readFileSync(p).toString('base64') : p);
  const fix = (t) => { if (t) { t.crest = inline(t.crest); (t.players || []).forEach((p) => { p.photo = inline(p.photo); }); } };
  fix(cfg.home); fix(cfg.away); (cfg.others || []).forEach(fix);
  (cfg.duels || []).forEach((d) => d.forEach((p) => { p.photo = inline(p.photo); fix(p.team); }));
  cfg.mark = inline(cfg.mark);
  cfg.record = true;
  if (process.env.RES) cfg.res = +process.env.RES;
  if (process.env.NOAA) cfg.aa = false;
  const page = path.join(out, 'match.html');
  fs.writeFileSync(page, `<!doctype html><html><head><meta charset="utf-8"><style>
    @font-face { font-family: 'BarlowC'; src: url('file://${fonts}/BarlowCondensed-800.ttf') format('truetype'); font-weight: 800; }
    @font-face { font-family: 'Barlow7'; src: url('file://${fonts}/BarlowCondensed-700.ttf') format('truetype'); font-weight: 700; }
    @font-face { font-family: 'Outfit'; src: url('file://${fonts}/outfit.ttf') format('truetype'); font-weight: 100 900; }
    html,body{margin:0;background:#000} canvas{display:block}</style></head><body>
    <script src="file://${bundle}"></script></body></html>`);
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  const errs = []; p.on('pageerror', (e) => errs.push(e.message)); p.on('console', (m) => { if (m.type() === 'error') errs.push(m.text()); });
  await p.goto('file://' + page);
  const t00 = Date.now();
  const info = await p.evaluate(async (c) => { const r = await window.MATCH3D.init(c); document.body.appendChild(window.MATCH3D.canvas); return r; }, cfg);
  console.log('ready in', Date.now() - t00, 'ms', JSON.stringify(info), errs.length ? errs.slice(0, 5) : 'no errors');
  const t0 = Date.now();
  const grab = async (t, type) => p.evaluate(([t, type]) => { window.MATCH3D.draw(t); return window.MATCH3D.canvas.toDataURL(type, 0.93); }, [t, type]);
  if (stills) {
    const ts = stills.split(',').map(Number);
    for (const t of ts) {
      const d = await grab(t, 'image/png');
      fs.writeFileSync(path.join(out, 'still_' + t.toFixed(2) + '.png'), Buffer.from(d.slice(d.indexOf(',') + 1), 'base64'));
    }
    console.log(ts.length, 'stills in', Date.now() - t0, 'ms', errs.length ? errs.slice(0, 5) : 'no errors');
  } else {
    const n = Math.round(info.len * fps), [p0, p1] = part ? part.split('-').map(Number) : [0, info.len];
    let k = 0;
    for (let f = Math.round(p0 * fps); f < Math.min(n, Math.round(p1 * fps)); f++, k++) {
      const d = await grab(f / fps, 'image/jpeg');
      fs.writeFileSync(path.join(out, String(f).padStart(5, '0') + '.jpg'), Buffer.from(d.slice(d.indexOf(',') + 1), 'base64'));
    }
    console.log(k, 'frames', ((Date.now() - t0) / Math.max(1, k)).toFixed(0) + ' ms/frame', errs.length ? errs.slice(0, 5) : 'no errors');
  }
  await b.close();
})().catch((e) => { console.error(e); process.exit(1); });
