// The match intro (page "match"): an open in the style of a big network's football show, made our own: Whitebrook Road
// Sports' lockup, then the two clubs of the match about to be watched (each one's city, its star's name card, its crest
// in a neon diamond, a neon tunnel, its chant), the competition's other clubs and their stars, the two clubs face to
// face, three of each club's players, and the end with the fixture. Drawn live in WebGL; draw(t) gives the same frame
// for any moment t (seconds), so the TV plays it as it goes and tools/match_render.js records it for a look.
// window.MATCH3D = {init(cfg) -> Promise<{len}>, draw(t), len, setRes(f), canvas}.
// cfg: {home, away: {name, abbr, base, deep, accent (or color, color2), crest (picture URL), chant?, players: [{first,
// last, num, pos, photo}]}, others: [clubs as above, the round's other clubs], duels: [[{first, last, num, pos, photo,
// team}, {...}]], comp, compName, mark (the competition's logo), fixture ('NEWCASTLE v ARSENAL'), ko ('17:30'), ground,
// res (0.5 to 1: the drawing's size), record (keep each frame to be read back)}.
import * as THREE from '../vendor/three.module.js';
import * as K from './kit.js';
import { SHOTS, W, H } from './shots.js';
import { UCL_SHOTS } from './ucl.js';
// each club's own colours by ESPN's id ({id: [base, deep, accent]}: tools/season_clubs.json's, the same as the season intros')
import CLUBS from './clubs.json';

export const LEN = 48, LEN_UCL = 38;
let len = LEN, BIG = [];

// ---- what the fans sing: a few clubs' own words, else the cheer of the competition's country ----------------------
const CHANTS = [
  ['arsenal', 'UP THE ARSENAL!'], ['aston villa', 'UP THE VILLA!'], ['bournemouth', 'UP THE CHERRIES!'], ['brentford', 'COME ON YOU BEES!'],
  ['brighton', 'UP THE ALBION!'], ['chelsea', 'COME ON CHELSEA!'], ['crystal palace', 'COME ON PALACE!'], ['everton', 'COME ON YOU BLUES!'],
  ['fulham', 'COME ON FULHAM!'], ['ipswich', 'COME ON YOU BLUES!'], ['leeds', 'MARCHING ON TOGETHER!'], ['liverpool', 'COME ON YOU REDS!'],
  ['manchester city', 'COME ON CITY!'], ['man city', 'COME ON CITY!'], ['manchester united', 'COME ON UNITED!'], ['man utd', 'COME ON UNITED!'],
  ['newcastle', 'HOWAY THE LADS!'], ['nottingham forest', 'COME ON YOU REDS!'], ['forest', 'COME ON YOU REDS!'], ['sunderland', "HA'WAY THE LADS!"],
  ['tottenham', 'COME ON YOU SPURS!'], ['spurs', 'COME ON YOU SPURS!'], ['west ham', 'COME ON YOU IRONS!'], ['wolverhampton', 'UP THE WOLVES!'],
  ['wolves', 'UP THE WOLVES!'], ['coventry', 'PLAY UP SKY BLUES!'], ['hull', 'UP THE TIGERS!'], ['burnley', 'UP THE CLARETS!'],
  ['leicester', 'COME ON LEICESTER!'], ['southampton', 'OH WHEN THE SAINTS!'], ['celtic', 'COME ON THE HOOPS!'], ['rangers', 'WE ARE THE PEOPLE!'],
  ['real madrid', '¡HALA MADRID!'], ['barcelona', 'VISCA EL BARÇA!'], ['atletico', '¡AÚPA ATLETI!'], ['atlético', '¡AÚPA ATLETI!'],
  ['athletic', 'AUPA ATHLETIC!'], ['sevilla', '¡VAMOS MI SEVILLA!'], ['betis', '¡VIVA EL BETIS!'], ['valencia', 'AMUNT VALÈNCIA!'],
  ['bayern', 'MIA SAN MIA!'], ['dortmund', 'ECHTE LIEBE!'], ['paris saint-germain', "ICI C'EST PARIS!"], ['psg', "ICI C'EST PARIS!"],
  ['marseille', 'DROIT AU BUT!'], ['juventus', 'FINO ALLA FINE!'], ['inter', 'FORZA INTER!'], ['milan', 'FORZA MILAN!'],
  ['napoli', 'FORZA NAPOLI!'], ['roma', 'FORZA ROMA!'], ['lazio', 'FORZA LAZIO!'],
];
const CHEER = { laliga: '¡VAMOS %!', seriea: 'FORZA %!', bundesliga: "AUF GEHT'S %!", ligue1: 'ALLEZ %!' };
export function chantFor(team, comp) {
  if (team.chant) return String(team.chant).toUpperCase();
  const n = String((team.full || '') + ' ' + (team.name || '')).toLowerCase();
  for (const [k, v] of CHANTS) if (n.includes(k)) return v;
  return (CHEER[comp] || 'COME ON %!').replace('%', String(team.name || '').toUpperCase());
}

// ---- the plan: which shot when (cuts on the music's beats, 120 a minute) -------------------------------------------
export function plan(cfg) {
  const hp = (cfg.home.players || []).filter((p) => p.lit), ap = (cfg.away.players || []).filter((p) => p.lit);
  const others = (cfg.others || []).filter((t) => t.img);
  const duels = (cfg.duels || []).filter((d) => d[0].lit && d[1].lit).map((d) => d.map((p) => ({ player: p, team: p.teamRef })));
  // the match's own players face to face where the round's other stars are not known
  for (let i = 2; duels.length < 2 && i < Math.min(hp.length, ap.length); i++) duels.push([{ player: hp[i], team: cfg.home }, { player: ap[i], team: cfg.away }]);
  const S = [];
  const at = (kind, t0, t1, o = {}) => S.push({ kind, t0, t1, o });
  at('comet', 0, 3.0);
  at('tunnel', 3.0, 4.0, { portal: true, lk: { glow: '#37d6ff', panel: '#0a2448', mid: '#13386a', deep: '#010812', ink: '#bdefff' }, seed: 2, roll0: 0.1, roll1: -0.5, twist: 0.02 });
  at('lockup', 4.0, 7.5, {});
  const team = (side, t0) => {
    const T = cfg[side], ps = side === 'home' ? hp : ap, sd = side === 'home' ? 1 : -1;
    at('city', t0, t0 + 1.5, { team: T, side: sd, seed: sd > 0 ? 3 : 13 });
    at('card', t0 + 1.5, t0 + 4.5, { team: T, player: ps[0] || null, seed: sd > 0 ? 5 : 15 });
    at('diamond', t0 + 4.5, t0 + 6.0, { team: T });
    at('tunnel', t0 + 6.0, t0 + 7.5, { team: T, seed: sd > 0 ? 6 : 16 });
    at('chant', t0 + 7.5, t0 + 9.5, { team: T, seed: sd > 0 ? 7 : 17 });
  };
  team('home', 7.5);
  team('away', 17.0);
  // the competition's other clubs: three tunnels, four crests, two pairs of their stars
  const pick = (i) => others.length ? others[i % others.length] : (i % 2 ? cfg.away : cfg.home);
  [0, 1, 2].forEach((i) => at('tunnel', 26.5 + i * 0.5, 27.0 + i * 0.5, { team: pick(i), short: true, seed: 30 + i, roll0: (i % 2 ? 0.3 : -0.3), roll1: 0 }));
  [3, 4, 5, 6].forEach((i, k) => at('parade', 28.0 + k * 0.5, 28.5 + k * 0.5, { team: pick(i), side: k % 2 ? -1 : 1, seed: 40 + k }));
  if (duels.length >= 2) { at('duel', 30.0, 32.0, { pair: duels[0], seed: 51 }); at('duel', 32.0, 34.0, { pair: duels[1], seed: 53 }); }
  else { [7, 8, 9, 10, 11, 12, 13, 14].forEach((i, k) => at('parade', 30.0 + k * 0.5, 30.5 + k * 0.5, { team: pick(i), side: k % 2 ? -1 : 1, seed: 60 + k })); }
  // the match: face to face, the short names, their stars, three of each
  at('faceoff', 34.0, 36.5, {});
  at('abbr', 36.5, 38.5, {});
  if (hp.length && ap.length) at('duel', 38.5, 40.5, { pair: [{ player: hp[Math.min(1, hp.length - 1)], team: cfg.home }, { player: ap[Math.min(1, ap.length - 1)], team: cfg.away }], seed: 57 });
  else at('faceoff', 38.5, 40.5, { again: true });
  const trio = (ps, T) => ps.length >= 3 ? [ps[2], ps[0], ps[3] || ps[1]] : ps.slice(0, 3);
  if (hp.length) at('trio', 40.5, 41.75, { team: cfg.home, players: trio(hp, cfg.home), seed: 61 }); else at('parade', 40.5, 41.75, { team: cfg.home, side: 1, seed: 61 });
  if (ap.length) at('trio', 41.75, 43.0, { team: cfg.away, players: trio(ap, cfg.away), seed: 62 }); else at('parade', 41.75, 43.0, { team: cfg.away, side: -1, seed: 62 });
  at('lockup', 43.0, LEN, { end: true });
  return S;
}
// cuts that open a part get the big flash
const BIG_NET = [4.0, 7.5, 17.0, 26.5, 34.0, 43.0], BIG_UCL = [4.0, 8.0, 11.0, 21.0, 27.0, 30.0];

/** Whether a game has the Champions League's own style (its games, unless the network's is asked for). */
export const uclStyle = (cfg) => cfg.style === 'ucl' || (cfg.comp === 'ucl' && cfg.style !== 'network');

/** The Champions League's style: 38 s, its own shots, the two clubs where its films have their goals. */
export function planUcl(cfg) {
  const hp = (cfg.home.players || []).filter((p) => p.lit), ap = (cfg.away.players || []).filter((p) => p.lit);
  const S = [];
  const at = (kind, t0, t1, o = {}) => S.push({ kind, t0, t1, o });
  at('uclOpen', 0, 4.0);
  at('uclStar', 4.0, 8.0);
  at('uclArches', 8.0, 11.0, {});
  at('uclDrapes', 11.0, 13.0, { team: cfg.home, seed: 0 });
  if (hp.length) at('uclPlayer', 13.0, 16.0, { team: cfg.home, player: hp[0], seed: 5 }); else at('uclArches', 13.0, 16.0, { team: cfg.home });
  at('uclDrapes', 16.0, 18.0, { team: cfg.away, seed: 3 });
  if (ap.length) at('uclPlayer', 18.0, 21.0, { team: cfg.away, player: ap[0], seed: 7 }); else at('uclArches', 18.0, 21.0, { team: cfg.away });
  at('uclVersus', 21.0, 24.5, {});
  if (hp.length > 1 && ap.length > 1) at('duel', 24.5, 27.0, { pair: [{ player: hp[1], team: cfg.home }, { player: ap[1], team: cfg.away }], seed: 71 });
  else at('uclArches', 24.5, 27.0, { team: cfg.away });
  at('uclCave', 27.0, 30.0, {});
  at('uclTrophy', 30.0, LEN_UCL, {});
  return S;
}

// ---- loading -----------------------------------------------------------------------------------------------------------
function picture(src) {
  if (!src) return Promise.resolve(null);
  return new Promise((res) => {
    const i = new Image();
    i.onload = () => res(i.naturalWidth > 1 ? i : null);
    i.onerror = () => res(null);
    i.src = src;
  });
}
function envFor(renderer, glowCss) {
  const s = new THREE.Scene();
  s.add(new THREE.Mesh(new THREE.BoxGeometry(10, 10, 10), new THREE.MeshBasicMaterial({ color: 0x080a10, side: THREE.BackSide })));
  const panel = (w, h, x, y, z, c, k) => {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({ color: new THREE.Color(c).multiplyScalar(k), side: THREE.DoubleSide }));
    m.position.set(x, y, z); m.lookAt(0, 0, 0); s.add(m);
  };
  panel(7, 1.4, 0, 4.6, 0, '#ffffff', 7);
  panel(1.3, 7, -4.7, 0, -1, glowCss, 5);
  panel(1.3, 7, 4.7, 0.5, -0.5, glowCss, 4);
  panel(8, 2.6, 0, 1.2, 4.8, '#ffffff', 3.2);
  panel(8, 1.2, 0, -1.6, 4.8, glowCss, 2.2);
  panel(6, 0.35, 0, -2.2, -4.8, '#ffffff', 2);
  panel(3, 3, 0, -4.8, 0, glowCss, 1.2);
  const pm = new THREE.PMREMGenerator(renderer);
  const t = pm.fromScene(s, 0.02).texture;
  pm.dispose();
  return t;
}

let renderer = null, plan_ = [], ov = null, built = false, res = 1;
const api = { LEN, len: LEN, init, draw, setRes, plan, chantFor, uclStyle, canvas: null, renderer: null };
window.MATCH3D = api;

async function init(cfg) {
  cfg = typeof cfg === 'string' ? JSON.parse(cfg) : cfg;
  try { await Promise.all(['800 100px BarlowC', '700 100px Barlow7', '700 100px Outfit'].map((f) => document.fonts.load(f))); } catch (e) { /* the fallback face */ }
  const clubs = [cfg.home, cfg.away, ...(cfg.others || [])];
  const duelPlayers = [];
  (cfg.duels || []).forEach((d) => d.forEach((p) => { if (p && p.team) { p.teamRef = p.team; duelPlayers.push(p); } }));
  const duelClubs = duelPlayers.map((p) => p.teamRef);
  await Promise.all([...clubs, ...duelClubs].map(async (t) => { t.img = await picture(t.crest); }));
  const ps = [...(cfg.home.players || []), ...(cfg.away.players || []), ...duelPlayers];
  await Promise.all(ps.map(async (p) => { p.img = await picture(p.photo); }));
  const mark = await picture(cfg.markImg || (/^(data:|file:|https?:|\/)/.test(String(cfg.mark || '')) ? cfg.mark : null));
  // each club's look, its crest in metal and in neon, its players lit
  for (const t of [...clubs, ...duelClubs]) {
    const own = t.espn && CLUBS[t.espn];
    if (own && !t.base) { t.base = own[0]; t.deep = own[1]; t.accent = own[2]; }
    t.lk = K.look(t);
    t.abbr = String(t.abbr || t.name || '').toUpperCase().slice(0, 4);
    t.name = String(t.name || '').toUpperCase();
  }
  const big = [cfg.home, cfg.away];
  for (const t of [...clubs, ...duelClubs]) {
    if (!t.img) continue;
    const size = big.includes(t) ? 600 : 440;
    if (!t.chrome) t.chrome = K.chromeCrest(t.img, size, t.lk.glow);
    if (!t.neon) t.neon = K.neonCrest(t.img, size * 0.9, t.lk.glow);
    if (!t.plain) t.plain = K.fitted(t.img, 160);
    // a wide crest is made a little smaller, a tall one a little larger (the same weight on screen)
    const r = t.img.naturalWidth / Math.max(1, t.img.naturalHeight);
    t.crestScale = r > 1.15 ? 0.9 : r < 0.8 ? 1.06 : 1;
  }
  for (const p of ps) {
    if (!p.img) continue;
    const team = cfg.home.players && cfg.home.players.includes(p) ? cfg.home : cfg.away.players && cfg.away.players.includes(p) ? cfg.away : p.teamRef;
    const gl = (team && team.lk ? team.lk : K.look({})).glow;
    p.lit = p.face ? K.medallion(p.img, gl) : K.litPlayer(p.img, gl);
    p.first = String(p.first || '').toUpperCase(); p.last = String(p.last || '').toUpperCase();
  }
  // the renderer
  const canvas = api.canvas || document.createElement('canvas');
  api.canvas = canvas;
  renderer = new THREE.WebGLRenderer({ canvas, antialias: cfg.aa !== false, powerPreference: 'high-performance', preserveDrawingBuffer: !!cfg.record, alpha: false });
  renderer.setPixelRatio(1);
  res = K.clamp(cfg.res || 1, 0.4, 1);
  renderer.setSize(Math.round(W * res), Math.round(H * res), false);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.0;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  api.renderer = renderer;
  const envs = new Map();
  const env = (glow) => { if (!envs.has(glow)) envs.set(glow, envFor(renderer, glow)); return envs.get(glow); };
  // the shots
  const ucl = uclStyle(cfg);
  plan_ = ucl ? planUcl(cfg) : plan(cfg);
  len = ucl ? LEN_UCL : LEN; BIG = ucl ? BIG_UCL : BIG_NET; api.len = len;
  const comp = cfg.comp || '';
  const night = (() => { const h = parseInt(String(cfg.ko || '').split(':')[0], 10); return !(h < 17); })();
  for (const s of plan_) {
    const o = s.o, T = o.team;
    const glowOf = T ? T.lk.glow : '#37d6ff';
    const e = env(glowOf);
    let shot;
    switch (s.kind) {
      case 'comet': shot = SHOTS.comet(e, { ko: cfg.ko }); break;
      case 'tunnel': shot = SHOTS.tunnel(e, { ...o, lk: o.lk || T.lk, crest: T && T.chrome ? { chrome: T.chrome, neon: T.neon } : null, crestScale: T && T.crestScale }); break;
      case 'lockup': shot = SHOTS.lockup(e, o.end
        ? { title: night ? 'MATCH NIGHT' : 'MATCHDAY', mark, fixture: cfg.fixture, line2: [cfg.ground, cfg.ko ? 'KICK-OFF ' + cfg.ko : ''].filter(Boolean).join('   ·   ').toUpperCase() }
        : { title: night ? 'MATCH NIGHT' : 'MATCHDAY', mark }); break;
      case 'city': shot = SHOTS.city(e, { lk: T.lk, name: T.name, side: o.side, seed: o.seed }); break;
      case 'card': shot = SHOTS.card(e, { lk: T.lk, name: T.name, crest: T.chrome || K.chromeCrest(K.canvas(4, 4), 10, T.lk.glow), crestScale: T.crestScale, player: o.player, seed: o.seed }); break;
      case 'diamond': shot = SHOTS.diamond(e, { lk: T.lk, name: T.name, crest: T.chrome, crestScale: T.crestScale }); break;
      case 'chant': shot = SHOTS.chant(e, { lk: T.lk, name: T.name, chant: chantFor(T, comp), crestPlain: T.plain, seed: o.seed }); break;
      case 'parade': shot = SHOTS.parade(e, { lk: T.lk, crest: T.chrome, crestScale: T.crestScale, side: o.side, seed: o.seed }); break;
      case 'duel': shot = SHOTS.duel(e, { pair: o.pair.map((q) => ({ lk: q.team.lk, crestImg: q.team.img, player: q.player })), seed: o.seed }); break;
      case 'faceoff': shot = SHOTS.faceoff(e, { home: { lk: cfg.home.lk, chrome: cfg.home.chrome, crestScale: cfg.home.crestScale }, away: { lk: cfg.away.lk, chrome: cfg.away.chrome, crestScale: cfg.away.crestScale }, seed: o.again ? 9 : 3 }); break;
      case 'abbr': shot = SHOTS.abbr(e, { home: cfg.home, away: cfg.away }); break;
      case 'trio': shot = SHOTS.trio(e, { lk: T.lk, crest: T.chrome, crestScale: T.crestScale, players: o.players, seed: o.seed }); break;
      case 'uclOpen': shot = UCL_SHOTS.open(env('#7a8cff'), { mark }); break;
      case 'uclStar': shot = UCL_SHOTS.star(env('#7a8cff'), {}); break;
      case 'uclArches': shot = UCL_SHOTS.arches(env('#7a8cff'), T ? { crest: T.chrome, crestScale: T.crestScale } : {}); break;
      case 'uclDrapes': shot = UCL_SHOTS.drapes(env('#7a8cff'), { team: T, seed: o.seed }); break;
      case 'uclPlayer': shot = UCL_SHOTS.player(env('#7a8cff'), { team: T, player: o.player, seed: o.seed }); break;
      case 'uclVersus': shot = UCL_SHOTS.versus(env('#7a8cff'), { home: cfg.home, away: cfg.away }); break;
      case 'uclCave': shot = UCL_SHOTS.cave(env('#7a8cff'), {}); break;
      case 'uclTrophy': shot = UCL_SHOTS.trophy(env('#9a7bff'), { fixture: cfg.fixture,
        line2: [cfg.ground, cfg.ko ? 'KICK-OFF ' + cfg.ko : ''].filter(Boolean).join('   ·   ').toUpperCase() }); break;
      default: continue;
    }
    s.shot = shot;
  }
  plan_ = plan_.filter((s) => s.shot);
  // over everything: the flashes and streaks at the cuts, a vignette, the fade in and out
  ov = { scene: new THREE.Scene(), camera: new THREE.OrthographicCamera(-W / 2, W / 2, H / 2, -H / 2, -10, 10) };
  const quad = (c, add, color) => {
    const m = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ map: c ? K.tex(c, false) : null, color: color || 0xffffff, transparent: true, depthWrite: false, depthTest: false, blending: add ? THREE.AdditiveBlending : THREE.NormalBlending, toneMapped: false }));
    ov.scene.add(m); return m;
  };
  const vg = K.canvas(512, 288), vgg = vg.getContext('2d'), vgr = vgg.createRadialGradient(256, 144, 60, 256, 144, 300);
  vgr.addColorStop(0, 'rgba(0,0,0,0)'); vgr.addColorStop(0.65, 'rgba(0,0,0,0.12)'); vgr.addColorStop(1, 'rgba(0,0,0,0.6)');
  vgg.fillStyle = vgr; vgg.fillRect(0, 0, 512, 288);
  ov.vig = quad(vg, false); ov.vig.scale.set(W, H, 1);
  ov.flash = quad(null, true, 0xffffff); ov.flash.scale.set(W, H, 1);
  ov.streak = quad(K.streak(1024, 128), true, 0xffffff); ov.streak.scale.set(W * 1.4, 300, 1);
  ov.streak2 = quad(K.streak(1024, 128), true, new THREE.Color('#9fe7ff')); ov.streak2.scale.set(W * 1.2, 120, 1);
  ov.black = quad(null, false, 0x000000); ov.black.scale.set(W, H, 1);
  // every shader made now, not on its first frame
  for (const s of plan_) { try { s.shot.update(0.001, s.t1 - s.t0); renderer.compile(s.shot.scene, s.shot.camera); } catch (e) { /* drawn later then */ } }
  renderer.compile(ov.scene, ov.camera);
  built = true;
  return { len, shots: plan_.length, style: ucl ? 'ucl' : 'network' };
}

function setRes(f) {
  if (!renderer) return;
  res = K.clamp(f || 1, 0.4, 1);
  renderer.setSize(Math.round(W * res), Math.round(H * res), false);
}

let last = null;
function draw(t) {
  if (!built) return;
  t = K.clamp(t, 0, len - 0.001);
  let s = plan_[plan_.length - 1];
  for (const q of plan_) if (t >= q.t0 && t < q.t1) { s = q; break; }
  s.shot.update(t - s.t0, s.t1 - s.t0);
  renderer.autoClear = true;
  renderer.render(s.shot.scene, s.shot.camera);
  // the cut's flash and streak
  let fl = 0, st = -1, big = false;
  for (const q of plan_) {
    if (q.t0 <= 0.01) continue;
    const dt = t - q.t0, isBig = BIG.some((b) => Math.abs(b - q.t0) < 0.01), amt = isBig ? 0.95 : q.t1 - q.t0 <= 0.6 ? 0.3 : 0.5;
    if (dt >= -0.05 && dt < 0.5) fl = Math.max(fl, amt * (dt < 0 ? 1 + dt / 0.05 : Math.exp(-dt / 0.085)));
    if (dt >= -0.18 && dt < 0.22) { st = (dt + 0.18) / 0.4; big = isBig; }
  }
  ov.flash.material.opacity = Math.min(1, fl);
  ov.flash.visible = fl > 0.003;
  ov.streak.visible = ov.streak2.visible = st >= 0;
  if (st >= 0) {
    const x = K.lerp(-W * 1.1, W * 1.1, K.eInOut(st)), a = Math.sin(st * Math.PI);
    ov.streak.position.set(x, K.lerp(120, -80, st), 0); ov.streak.rotation.z = 0.12; ov.streak.material.opacity = a * (big ? 1 : 0.6);
    ov.streak2.position.set(-x * 0.8, K.lerp(-200, 160, st), 0); ov.streak2.rotation.z = -0.08; ov.streak2.material.opacity = a * (big ? 0.8 : 0.4);
  }
  const fade = Math.max(1 - K.ramp(0, 0.35, t), K.ramp(len - 0.9, len - 0.05, t));
  ov.black.material.opacity = fade; ov.black.visible = fade > 0.002;
  renderer.autoClear = false;
  renderer.render(ov.scene, ov.camera);
  last = s;
}
