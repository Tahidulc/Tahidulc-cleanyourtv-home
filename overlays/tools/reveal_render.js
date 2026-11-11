// node reveal_render.js KEY LOGO_DIR OUT_DIR [fixture|nofixture] [mode full|tail] [fps]
// Renders one competition's intro (src/reveal/reveal.js, its colours and logo from reveal_comps.json) to JPEG frames in
// OUT_DIR, 1920 x 1080, in headless Chromium. "nofixture" leaves the teams out (the app writes them over the video).
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
(async () => {
  const [key, logos, out, fx = 'fixture', mode = 'full', fpsA = '30'] = process.argv.slice(2);
  const fps = +fpsA, comps = JSON.parse(fs.readFileSync(path.join(__dirname, 'reveal_comps.json'), 'utf8')), c = comps[key];
  if (!c) throw new Error('no such competition: ' + key);
  const data = (f) => (f && fs.existsSync(path.join(logos, f + '.png')) ? 'data:image/png;base64,' + fs.readFileSync(path.join(logos, f + '.png')).toString('base64') : null);
  const cfg = { mode, name: c.name, emblem: c.emblem || 'logo', logo: data(c.logo), logoLight: data(c.logoLight), c1: c.c1, c2: c.c2, bg: c.bg, ink: c.ink,
    title: c.title, fixture: fx === 'fixture' ? c.sample : '' };
  fs.mkdirSync(out, { recursive: true });
  const src = path.resolve(__dirname, '../src');
  const page = path.join(out, 'reveal.html');
  fs.writeFileSync(page, `<!doctype html><html><head><meta charset="utf-8"><style>
    @font-face { font-family: 'Outfit'; src: url('file://${src}/fonts/outfit.ttf') format('truetype'); font-weight: 100 900; }
    html,body{margin:0;background:#000} canvas{display:block}</style></head><body><canvas id="reveal"></canvas>
    <script src="file://${src}/reveal/reveal.js"></script></body></html>`);
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  const errs = []; p.on('pageerror', (e) => errs.push(e.message));
  await p.goto('file://' + page);
  const info = await p.evaluate((k) => window.REVEAL.init(k), cfg);
  const n = Math.round(info.len * fps), t0 = Date.now();
  for (let f = 0; f < n; f++) {
    const d = await p.evaluate((t) => { window.REVEAL.draw(t); return document.getElementById('reveal').toDataURL('image/jpeg', 0.93); }, f / fps);
    fs.writeFileSync(path.join(out, String(f).padStart(5, '0') + '.jpg'), Buffer.from(d.slice(d.indexOf(',') + 1), 'base64'));
  }
  console.log(key, mode, n, 'frames', ((Date.now() - t0) / n).toFixed(0) + ' ms/frame', errs.length ? errs.slice(0, 3) : 'no errors');
  await b.close();
})().catch((e) => { console.error(e); process.exit(1); });
