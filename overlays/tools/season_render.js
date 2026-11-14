// node season_render.js KEY TEAMS_JSON CREST_DIR EMOJI_DIR OUT_DIR [fps] [stills: t,t,t] [part: from-to seconds]
// Renders one league's season intro (src/season/season.js) to JPEG frames in OUT_DIR, 1920 x 1080, in headless Chromium.
// KEY: pl, laliga, seriea, bundesliga or ligue1 (its mark from assets/broadcast/marks). TEAMS_JSON: ESPN's teams for the
// league (site.api.espn.com/apis/site/v2/sports/soccer/<slug>/teams): the season's clubs, in alphabetical order. CREST_DIR:
// <ESPN id>.png for each. EMOJI_DIR: the Twemoji pictures (<code>.svg, CC-BY 4.0) season_clubs.json names. With stills,
// only those moments, as PNG (for a look).
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
(async () => {
  const [key, teamsPath, crests, emoji, out, fpsA = '30', stills = '', part = ''] = process.argv.slice(2);
  const fps = +fpsA;
  const clubs = JSON.parse(fs.readFileSync(path.join(__dirname, 'season_clubs.json'), 'utf8'));
  const league = JSON.parse(fs.readFileSync(teamsPath, 'utf8')).sports[0].leagues[0];
  const year = league.season && league.season.year ? +league.season.year : new Date().getFullYear();
  const season = `${year}/${String(year + 1).slice(2)}`;
  const b64 = (p, type) => `data:${type};base64,` + fs.readFileSync(p).toString('base64');
  const pics = {}, want = new Set();
  const list = [];
  for (const { team } of league.teams) {
    const c = clubs[team.id];
    if (!c) throw new Error('no scene for ' + team.displayName + ' (' + team.id + ')');
    const crest = path.join(crests, team.id + '.png');
    list.push({ ...c, crest: fs.existsSync(crest) ? b64(crest, 'image/png') : null });
    for (const f of c.fx) if (f.m.startsWith('e:')) want.add(f.m);
    if (c.fx.some((f) => f.m === 's:musicians')) ['e:1facf', 'e:1f415', 'e:1f408', 'e:1f413'].forEach((m) => want.add(m));
  }
  list.sort((a, b) => a.name.localeCompare(b.name, 'en'));
  for (const m of want) {
    const p = path.join(emoji, m.slice(2) + '.svg');
    if (!fs.existsSync(p)) throw new Error('no picture ' + p);
    // drawn crisp at any size: the set's pictures carry only a viewBox
    const svg = fs.readFileSync(p, 'utf8').replace('<svg ', '<svg width="512" height="512" ');
    pics[m] = 'data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64');
  }
  const mark = path.resolve(__dirname, '../assets/broadcast/marks', key + '.png');
  const cfg = { season, mark: fs.existsSync(mark) ? b64(mark, 'image/png') : null, clubs: list, pics };
  fs.mkdirSync(out, { recursive: true });
  const src = path.resolve(__dirname, '../src'), fonts = path.resolve(__dirname, '../assets/broadcast/fonts');
  const page = path.join(out, 'season.html');
  fs.writeFileSync(page, `<!doctype html><html><head><meta charset="utf-8"><style>
    @font-face { font-family: 'Outfit'; src: url('file://${fonts}/outfit.ttf') format('truetype'); font-weight: 100 900; }
    @font-face { font-family: 'BarlowC'; src: url('file://${fonts}/BarlowCondensed-800.ttf') format('truetype'); font-weight: 800; }
    html,body{margin:0;background:#000} canvas{display:block}</style></head><body><canvas id="season"></canvas>
    <script src="file://${src}/season/season.js"></script></body></html>`);
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  const errs = []; p.on('pageerror', (e) => errs.push(e.message));
  await p.goto('file://' + page);
  const info = await p.evaluate((c) => window.SEASON.init(c), cfg);
  const t0 = Date.now();
  if (stills) {
    const ts = stills.split(',').map(Number);
    for (const t of ts) {
      const d = await p.evaluate((t) => { window.SEASON.draw(t); return document.getElementById('season').toDataURL('image/png'); }, t);
      fs.writeFileSync(path.join(out, 'still_' + t.toFixed(2) + '.png'), Buffer.from(d.slice(d.indexOf(',') + 1), 'base64'));
    }
    console.log(key, season, list.length, 'clubs,', info.len + ' s,', ts.length, 'stills', errs.length ? errs.slice(0, 3) : 'no errors');
  } else {
    const n = Math.round(info.len * fps), [p0, p1] = part ? part.split('-').map(Number) : [0, info.len];
    for (let f = Math.round(p0 * fps); f < Math.min(n, Math.round(p1 * fps)); f++) {
      const d = await p.evaluate((t) => { window.SEASON.draw(t); return document.getElementById('season').toDataURL('image/jpeg', 0.93); }, f / fps);
      fs.writeFileSync(path.join(out, String(f).padStart(5, '0') + '.jpg'), Buffer.from(d.slice(d.indexOf(',') + 1), 'base64'));
    }
    console.log(key, season, list.length, 'clubs,', n, 'frames', ((Date.now() - t0) / n).toFixed(0) + ' ms/frame', errs.length ? errs.slice(0, 3) : 'no errors');
  }
  fs.writeFileSync(path.join(out, 'info.json'), JSON.stringify({ key, season, clubs: list.map((c) => c.name), len: info.len }));
  await b.close();
})().catch((e) => { console.error(e); process.exit(1); });
