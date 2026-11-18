// The studio line-ups (page "studio"), the third style of the line-ups before kick-off: each team in turn beside a side
// panel in its competition's colours and logo (the club's crest, the position and its name up the panel), a title card
// with the club's kit turning in 3D (kits/kit3d.js, the kit it wears: kits/designs.js), then a page a position, the
// players coming in with their pictures, rolling numbers, flags and names, a swish as each page comes; the substitutes and
// the manager; the side going out and the other team coming in. Made our own from the fan-made "Sky Sports version" of
// the Premier League's line-ups: no broadcaster's graphics, lettering or sound.
// Drawn as compositor layers (host/layers.js: every part drawn once while it gets ready, a frame only placing them); the
// 3D shirt is drawn live while its title card is up. Everything runs on a beat (124 a minute) so that one piece of
// music (intros/sound-studio.m4a, tools/studio_music.py) fits any formation: each team 45 beats (in, the title at 2,
// the pages from 8, a page 4 beats and one a player, the subs, out at 43), the away team from beat 46, the end at 93.
// window.STUDIO = {init(cfg) -> Promise<{len, cues}>, frame(t)}: cfg {home, away: {name, short, formation, coach, crest,
// color, color2, espnId, kitType, players: [{n, first, last, cap, group, country, photo, face, x}], subs: [{n, first,
// last}]}, comp: {key, name, logo}, stadium, ko, q: {shirt (its size, px)}}.
import { stage } from '../kits/kit3d.js';
import { kitFor } from '../kits/designs.js';

export const BEAT = 60 / 124;
const B = (n) => n * BEAT;
export const AWAY_AT = 46, END_AT = 93;
const W = 1920, H = 1080;
const NAVY = '#10185a', INK = '#0e1550';
const SIDE_X = 45, SIDE_W = 188, MAIN_X = 233, MAIN_W = 1635, MAIN_C = MAIN_X + MAIN_W / 2;
const ORDER = ['GOALKEEPER', 'DEFENDERS', 'MIDFIELDERS', 'FORWARDS'];

// the side panel's colours by the competition (BroadcastData.mark's keys); navy for any other
const PANELS = {
  pl: ['#00166b', '#030f43', '#000723'], ucl: ['#13246b', '#0a0f3d', '#03041a'], uel: ['#e8590f', '#9e3606', '#2e0f02'],
  uecl: ['#1b9b48', '#0e5e2b', '#04230f'], facup: ['#8a1022', '#5a0a17', '#26040a'], leaguecup: ['#147a46', '#0b4a2a', '#031a0e'],
  championship: ['#13276b', '#0b1747', '#03081d'], league1: ['#13276b', '#0b1747', '#03081d'], league2: ['#13276b', '#0b1747', '#03081d'],
  efltrophy: ['#13276b', '#0b1747', '#03081d'], laliga: ['#e8332c', '#9c1d18', '#2f0806'], copadelrey: ['#8a1022', '#5a0a17', '#26040a'],
  supercopa: ['#8a1022', '#5a0a17', '#26040a'], seriea: ['#0a3a8c', '#06245a', '#020b1f'], coppaitalia: ['#0a3a8c', '#06245a', '#020b1f'],
  bundesliga: ['#d20515', '#8f040e', '#2e0105'], dfbpokal: ['#d20515', '#8f040e', '#2e0105'], ligue1: ['#0c2d48', '#071c2e', '#020a12'],
  coupedefrance: ['#0c2d48', '#071c2e', '#020a12'], eredivisie: ['#e8590f', '#9e3606', '#2e0f02'], ligaportugal: ['#0b3a6b', '#072445', '#020c18'],
  spfl: ['#2b1d5e', '#1b1240', '#0a0619'], scotchamp: ['#2b1d5e', '#1b1240', '#0a0619'], mls: ['#20252d', '#11151b', '#030405'],
  nations: ['#0b1f4d', '#071434', '#020713'], wsl: ['#3a0d5e', '#25083d', '#0d0216'], saudipro: ['#0b5a3a', '#073826', '#02140d'],
};

// ---- helpers --------------------------------------------------------------------------------------------------------
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a, b, k) => a + (b - a) * k;
const prog = (t, a, b) => clamp((t - a) / (b - a));
const eOut = (k) => 1 - Math.pow(1 - k, 3);
const eOut4 = (k) => 1 - Math.pow(1 - k, 4);
const eIn = (k) => k * k * k;
const eInOut = (k) => (k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2);
const eBack = (k) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(k - 1, 3) + c1 * Math.pow(k - 1, 2); };
const load = (src) => new Promise((ok) => { if (!src) return ok(null); const i = new Image(); i.onload = () => ok(i.width > 0 ? i : null); i.onerror = () => ok(null); i.src = src; });
const font = (weight, size, fam = 'Mont') => `${weight} ${size}px "${fam}", "Montserrat", sans-serif`;
function canvas(w, h) { const c = document.createElement('canvas'); c.width = Math.max(1, Math.ceil(w)); c.height = Math.max(1, Math.ceil(h)); return c; }
let scratch = null;
function measure(text, f, spacing) {
  const g = scratch || (scratch = canvas(4, 4).getContext('2d'));
  g.font = f; g.letterSpacing = spacing || '0px';
  return g.measureText(text).width;
}
function fitSize(text, weight, size, maxW) { let s = size; while (s > 10 && measure(text, font(weight, s)) > maxW) s -= 1; return s; }
function fitIn(im, w, h) { const k = Math.min(w / im.width, h / im.height); return [im.width * k, im.height * k]; }
/** Words as a sprite, anchored on their baseline at the left, middle or right. */
function words(str, weight, size, colour, opts = {}) {
  const f = font(weight, size), sp = opts.spacing || '0px';
  const tw = measure(str, f, sp);
  const pad = Math.ceil(size * 0.28) + Math.ceil(opts.stroke || 0);
  const c = canvas(tw + pad * 2 + 4, size * 1.32 + pad * 2);
  const g = c.getContext('2d');
  g.font = f; g.letterSpacing = sp; g.textBaseline = 'alphabetic';
  const by = pad + size;
  if (opts.stroke) { g.lineJoin = 'round'; g.lineWidth = opts.stroke; g.strokeStyle = colour; g.strokeText(str, pad, by); }
  else { g.fillStyle = colour; g.fillText(str, pad, by); }
  const ax = opts.align === 'center' ? pad + tw / 2 : opts.align === 'right' ? pad + tw : pad;
  return { c, ax, ay: by, tw };
}
/** A sprite placed with its anchor at (x, y), scaled about it. */
function put(s, x, y, a = 1, sx = 1, sy = sx, blend) {
  if (!s || a <= 0.002) return;
  LK.put(s.c, [sx, 0, 0, sy, x - s.ax * sx, y - s.ay * sy], a, blend);
}

// ---- the plan: when everything comes, on the beat --------------------------------------------------------------------
let cfg = null, plans = [], CUES = [], LEN = B(END_AT);
let big = null, bigSize = 720;
const SP = {};

function plan(team, b0, side) {
  const t0 = B(b0);
  const groups = ORDER.map((g) => ({ group: g, players: team.players.filter((p) => p.group === g) })).filter((x) => x.players.length);
  // a page: 4 beats and one a player, from beat 8; squeezed to end by beat 35 when there are more
  const want = groups.reduce((s, x) => s + 4 + x.players.length, 0), room = 27;
  const k = want > room ? room / want : 1;
  let b = 8;
  const pages = groups.map((x) => {
    const len = (4 + x.players.length) * k;
    const pg = { group: x.group, players: x.players, t0: t0 + B(b), t1: t0 + B(b + len) };
    b += len;
    return pg;
  });
  const p = { team, side, t0, title: t0 + B(2), titleEnd: t0 + B(8), pages, subs: t0 + B(b), exit: t0 + B(43), end: t0 + B(45) };
  CUES.push({ at: t0, k: 'in', side }, { at: p.title + 0.2, k: 'spin', side });
  pages.forEach((pg) => {
    CUES.push({ at: pg.t0 - 0.42, k: 'swish', side }, { at: pg.t0, k: 'stab', side });
    pg.players.forEach((_, i) => CUES.push({ at: pg.t0 + 0.24 + i * 0.09, k: 'pluck', side, i }));
  });
  CUES.push({ at: p.subs - 0.42, k: 'swish', side }, { at: p.subs, k: 'stab', side }, { at: p.exit, k: 'out', side });
  return p;
}

// ---- making the parts, once -----------------------------------------------------------------------------------------------
function makeSide(comp, logo) {
  const col = PANELS[comp.key] || PANELS.pl;
  const bg = canvas(SIDE_W, H), g = bg.getContext('2d');
  const gr = g.createLinearGradient(0, 0, 0, H);
  gr.addColorStop(0, col[0]); gr.addColorStop(0.5, col[1]); gr.addColorStop(1, col[2]);
  g.fillStyle = gr; g.fillRect(0, 0, SIDE_W, H);
  // two fine diagonal lines
  g.strokeStyle = 'rgba(120,150,255,0.16)'; g.lineWidth = 2;
  g.beginPath(); g.moveTo(-40, 250); g.lineTo(SIDE_W + 40, 520); g.stroke();
  g.beginPath(); g.moveTo(-40, 640); g.lineTo(SIDE_W + 40, 360); g.stroke();
  SP.sideBg = { c: bg, ax: 0, ay: 0 };
  // the light along its edge as it lands
  const fl = canvas(60, H), fg = fl.getContext('2d');
  const lg = fg.createLinearGradient(0, 0, 60, 0);
  lg.addColorStop(0, 'rgba(200,220,255,0)'); lg.addColorStop(1, 'rgba(200,220,255,0.7)');
  fg.fillStyle = lg; fg.fillRect(0, 0, 60, H);
  SP.sideFlash = { c: fl, ax: 0, ay: 0 };
  if (logo) {
    const [w, h] = fitIn(logo, 122, 122);
    const c = canvas(w, h); c.getContext('2d').drawImage(logo, 0, 0, w, h);
    SP.comp = { c, ax: w / 2, ay: h / 2 };
  }
  // the ring of light round the crest as it lands (drawn at 140 px, scaled)
  const r = canvas(300, 300), rg = r.getContext('2d');
  rg.strokeStyle = 'rgb(190,210,255)'; rg.lineWidth = 6;
  rg.beginPath(); rg.arc(150, 150, 140, 0, Math.PI * 2); rg.stroke();
  SP.ring = { c: r, ax: 150, ay: 150 };
  SP.labels = {};
  for (const l of [...ORDER, 'SUBSTITUTES']) SP.labels[l] = words(l, 800, 21, '#ffffff', { spacing: '3px', align: 'center' });
  const dot = (rad, alpha) => { const c = canvas(14, 14), x = c.getContext('2d'); x.fillStyle = `rgba(255,255,255,${alpha})`; x.beginPath(); x.arc(7, 7, rad, 0, Math.PI * 2); x.fill(); return { c, ax: 7, ay: 7 }; };
  SP.dotOn = dot(5, 1); SP.dotOff = dot(4, 0.28);
}

function makeMain() {
  const bg = canvas(MAIN_W, H), g = bg.getContext('2d');
  const gr = g.createLinearGradient(0, 0, 0, H);
  gr.addColorStop(0, '#e6e6e6'); gr.addColorStop(0.5, '#b8bed6'); gr.addColorStop(1, '#8696c7');
  g.fillStyle = gr; g.fillRect(0, 0, MAIN_W, H);
  SP.mainBg = { c: bg, ax: 0, ay: 0 };
  // streaks leaning like the original's (they drift as it plays): drawn from stage x -400
  const st = canvas(2400, H), sg = st.getContext('2d');
  const S = [[180, 120, 'rgba(255,255,255,0.30)'], [420, 70, 'rgba(255,255,255,0.22)'], [640, 160, 'rgba(40,52,120,0.06)'],
    [930, 90, 'rgba(255,255,255,0.26)'], [1180, 140, 'rgba(255,255,255,0.18)'], [1420, 60, 'rgba(40,52,120,0.05)'],
    [1560, 110, 'rgba(255,255,255,0.24)'], [1820, 90, 'rgba(255,255,255,0.2)']];
  for (const [x0, w, col] of S) {
    const x = MAIN_X + x0 - 200 + 400;
    sg.fillStyle = col;
    sg.beginPath(); sg.moveTo(x, 0); sg.lineTo(x + w, 0); sg.lineTo(x + w - 560, H); sg.lineTo(x - 560, H); sg.closePath(); sg.fill();
  }
  SP.streaks = { c: st, ax: 400, ay: 0 };
  // a sheet of light across, as a page turns (its middle at the anchor)
  const sw = canvas(1100, H), wg = sw.getContext('2d');
  const x = 800, lgr = wg.createLinearGradient(x - 220, 0, x + 220, 0);
  lgr.addColorStop(0, 'rgba(255,255,255,0)'); lgr.addColorStop(0.5, 'rgba(255,255,255,0.55)'); lgr.addColorStop(1, 'rgba(255,255,255,0)');
  wg.fillStyle = lgr;
  wg.beginPath(); wg.moveTo(x - 220, 0); wg.lineTo(x + 220, 0); wg.lineTo(x - 340, H); wg.lineTo(x - 780, H); wg.closePath(); wg.fill();
  SP.sweep = { c: sw, ax: x, ay: 0 };
  SP.wm = {};
  for (const w of [...ORDER, 'SUBS']) SP.wm[w] = words(w, 800, 236, 'rgb(16,24,90)');
  // the 3D shirt's glow and its shadow on the floor
  const gl = canvas(860, 860), gg = gl.getContext('2d');
  const rg = gg.createRadialGradient(430, 430, 0, 430, 430, 430);
  rg.addColorStop(0, 'rgba(255,255,255,0.75)'); rg.addColorStop(1, 'rgba(255,255,255,0)');
  gg.fillStyle = rg; gg.fillRect(0, 0, 860, 860);
  SP.glow = { c: gl, ax: 430, ay: 430 };
  const sh = canvas(520, 100), shg = sh.getContext('2d');
  shg.translate(260, 50); shg.scale(1, 0.18);
  const srg = shg.createRadialGradient(0, 0, 0, 0, 0, 260);
  srg.addColorStop(0, 'rgba(10,16,60,0.32)'); srg.addColorStop(1, 'rgba(10,16,60,0)');
  shg.fillStyle = srg; shg.fillRect(-260, -260, 520, 520);
  SP.shadow = { c: sh, ax: 260, ay: 50 };
  // the captain's band
  const cb = canvas(40, 40), cg = cb.getContext('2d');
  cg.fillStyle = NAVY; cg.beginPath(); cg.arc(20, 20, 18, 0, Math.PI * 2); cg.fill();
  cg.fillStyle = '#fff'; cg.font = font(800, 22); cg.textAlign = 'center'; cg.fillText('C', 20, 28);
  SP.cap = { c: cb, ax: 20, ay: 20 };
}

/**
 * A player's picture, S square, standing on its foot: the league's cut-out, faded at its foot; else FotMob's face as a
 * round portrait (the picture cut into a disc on the club's colour, nothing drawn under the head); else the club's crest
 * large over the player's initials on that disc. No drawn figure in place of a face.
 */
function picture(im, isFace, S, crest, colour, who) {
  const c = canvas(S, S), x = c.getContext('2d');
  if (im && !isFace) {
    x.drawImage(im, 0, 0, S, S);
    x.globalCompositeOperation = 'destination-out';
    const gr = x.createLinearGradient(0, S * 0.8, 0, S);
    gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(1, 'rgba(0,0,0,1)');
    x.fillStyle = gr; x.fillRect(0, 0, S, S);
    return { c, ax: S / 2, ay: S };
  }
  const r = S * 0.37, cx = S / 2, cy = S - r - S * 0.05;
  const m = /^#?([0-9a-f]{6})$/i.exec(colour || ''), v = m ? parseInt(m[1], 16) : 0x10185a;
  const rgb = [(v >> 16) & 255, (v >> 8) & 255, v & 255], deep = rgb.map((n) => Math.round(n * 0.55));
  x.save();
  x.shadowColor = 'rgba(10,16,60,0.28)'; x.shadowBlur = S * 0.05; x.shadowOffsetY = S * 0.015;
  const bg = x.createLinearGradient(0, cy - r, 0, cy + r);
  bg.addColorStop(0, `rgb(${rgb.join(',')})`); bg.addColorStop(1, `rgb(${deep.join(',')})`);
  x.beginPath(); x.arc(cx, cy, r, 0, Math.PI * 2); x.fillStyle = bg; x.fill();
  x.restore();
  x.save();
  x.beginPath(); x.arc(cx, cy, r, 0, Math.PI * 2); x.clip();
  const lg = x.createRadialGradient(cx, cy - r * 0.6, 0, cx, cy - r * 0.6, r * 1.5);
  lg.addColorStop(0, 'rgba(255,255,255,0.24)'); lg.addColorStop(1, 'rgba(255,255,255,0)');
  x.fillStyle = lg; x.fillRect(0, 0, S, S);
  x.imageSmoothingQuality = 'high';
  if (im) {
    // the face filling the disc, its neck cut hidden at the disc's foot
    const d = r * 2 * 1.06, h = d * im.height / im.width;
    x.drawImage(im, cx - d / 2, cy - r - (d - 2 * r) / 2 + r * 0.09, d, h);
  } else {
    const light = (0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]) / 255 > 0.6;
    const label = String(who || '').split(/[\s-]+/).filter(Boolean).map((w) => w[0]).slice(0, 2).join('').toUpperCase();
    if (crest) {
      const [w, h] = fitIn(crest, r * 0.92, r * 0.92);
      x.drawImage(crest, cx - w / 2, cy - r * 0.18 - h / 2, w, h);
    }
    if (label) {
      const fs = Math.round(r * (crest ? 0.34 : 0.78));
      x.font = font(800, fs); x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillStyle = light ? INK : '#ffffff';
      x.fillText(label, cx, crest ? cy + r * 0.6 : cy);
    }
  }
  x.restore();
  x.beginPath(); x.arc(cx, cy, r - 1, 0, Math.PI * 2); x.lineWidth = Math.max(2, r * 0.025); x.strokeStyle = 'rgba(255,255,255,0.85)'; x.stroke();
  return { c, ax: S / 2, ay: S };
}

function layout(n) {
  return n >= 6 ? { col: 268, S: 360 } : n === 5 ? { col: 318, S: 420 } : n === 4 ? { col: 392, S: 470 }
    : n === 3 ? { col: 470, S: 540 } : n === 2 ? { col: 540, S: 600 } : { col: 560, S: 640 };
}

function flagSprite(im) {
  if (!im) return null;
  const w = 64, h = Math.round(w * 26 / 36), s = w / 36;
  const c = canvas(w, h), x = c.getContext('2d');
  x.drawImage(im, 0, -5 * s, 36 * s, 36 * s);
  return { c, ax: 0, ay: h / 2 };
}

async function makeTeam(team, side, other) {
  const T = { side };
  // the crest in the side panel, the club's name up it
  const crest = await load(team.crest);
  T.crestImg = crest;
  if (crest) { const [w, h] = fitIn(crest, 128, 128); const c = canvas(w, h); c.getContext('2d').drawImage(crest, 0, 0, w, h); T.crest = { c, ax: w / 2, ay: h / 2 }; }
  const name = team.short || team.name || '';
  const ns = fitSize(name, 800, 76, 540), tw = measure(name, font(800, ns));
  const pad = 12, vc = canvas(ns * 1.1 + pad * 2, tw + pad * 2), vg = vc.getContext('2d');
  const xb = ns * 0.8 + pad;
  vg.translate(xb, tw + pad); vg.rotate(-Math.PI / 2);
  vg.font = font(800, ns); vg.fillStyle = '#ffffff'; vg.textBaseline = 'alphabetic';
  vg.fillText(name, 0, 0);
  T.name = { c: vc, ax: xb, ay: tw + pad };
  // the title card: STARTING XI, the club's name letter by letter, the formation, the fixture
  const tag = canvas(300, 60), tg = tag.getContext('2d');
  tg.fillStyle = NAVY; tg.beginPath(); tg.roundRect ? tg.roundRect(0, 0, 300, 60, 6) : tg.rect(0, 0, 300, 60); tg.fill();
  tg.font = font(800, 30); tg.fillStyle = '#fff'; tg.letterSpacing = '4px'; tg.fillText('STARTING XI', 22, 42);
  T.tag = { c: tag, ax: 0, ay: 0 };
  const wordsOf = String(team.name || name).toUpperCase().split(/\s+/).filter(Boolean);
  const lines = wordsOf.length > 2 ? [wordsOf.slice(0, -1).join(' '), wordsOf[wordsOf.length - 1]]
    : wordsOf.length === 2 && wordsOf.join(' ').length > 11 ? [wordsOf[0], wordsOf[1]] : [wordsOf.join(' ')];
  T.letters = [];
  let y = 450;
  lines.forEach((line, li) => {
    const size = fitSize(line, 800, 132, 780);
    let x = 326;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (ch !== ' ') T.letters.push({ s: words(ch, 800, size, INK), x, y, at: 0.25 + li * 0.12 + i * 0.03 });
      x += measure(ch, font(800, size));
    }
    y += size * 0.98;
  });
  T.formation = team.formation ? words(team.formation, 800, 84, NAVY, { stroke: 3 }) : null;
  T.formationLabel = words('FORMATION', 600, 30, 'rgba(14,21,80,0.8)');
  const fx = [(side === 'home' ? 'v ' : 'at ') + (other.name || other.short || ''), cfg.stadium, cfg.ko].filter(Boolean).join('  ·  ');
  T.fixture = words(fx, 600, Math.min(34, fitSize(fx, 600, 34, 760)), 'rgba(14,21,80,0.85)');
  T.titleY = y;
  // the 3D shirt: the kit it wears, its back with the captain's name and number
  const cap = team.players.find((p) => p.cap) || team.players[0] || {};
  const kit = Object.assign(kitFor(team), { crest, name: String(cap.last || '').toUpperCase(), number: String(cap.n || '') });
  if (big) T.shirt = big.add(kit);
  // each player: the picture, the number's digits, the flag, the names
  T.players = new Map();
  for (const pg of ORDER) {
    const ps = team.players.filter((p) => p.group === pg), L = layout(ps.length);
    for (const p of ps) {
      const im = await load(p.photo || p.face);
      const P = { pic: picture(im, !p.photo && !!p.face, L.S, crest, team.color, [p.first, p.last || p.name].filter(Boolean).join(' ')) };
      const num = String(p.n || '');
      P.digits = [];
      for (const d of num) {
        const prev = /[0-9]/.test(d) ? String((+d + 7) % 10) : d;
        P.digits.push({ now: words(d, 800, 66, INK), was: words(prev, 800, 66, INK), w: measure(d, font(800, 66)) });
      }
      P.flag = flagSprite(await load(p.country ? 'flags/' + String(p.country).toUpperCase() + '.svg' : null));
      P.first = p.first ? words(p.first, 600, fitSize(p.first, 600, 34, L.col - 30), INK, { align: 'center' }) : null;
      const last = p.last || p.name || '';
      P.last = words(last, 800, fitSize(last, 800, 52, L.col - 24), INK, { align: 'center' });
      T.players.set(p, P);
    }
  }
  // the substitutes (nine at most) and the manager
  T.subs = team.subs.slice(0, 9).map((s) => {
    const cw = 440, chh = 92, c = canvas(cw + 40, chh + 40), x = c.getContext('2d');
    x.shadowColor = 'rgba(10,16,60,0.18)'; x.shadowBlur = 18; x.shadowOffsetY = 6;
    x.fillStyle = 'rgba(255,255,255,0.92)';
    x.beginPath(); x.roundRect ? x.roundRect(20, 14, cw, chh, 8) : x.rect(20, 14, cw, chh); x.fill();
    x.shadowColor = 'transparent';
    x.fillStyle = NAVY; x.beginPath(); x.roundRect ? x.roundRect(20, 14, chh, chh, [8, 0, 0, 8]) : x.rect(20, 14, chh, chh); x.fill();
    x.fillStyle = '#fff'; x.font = font(800, 40); x.textAlign = 'center';
    x.fillText(String(s.n || ''), 20 + chh / 2, 14 + chh / 2 + 14);
    x.fillStyle = INK; x.textAlign = 'left';
    const first = s.first || '', last = s.last || s.name || '';
    if (first) { x.font = font(600, fitSize(first, 600, 24, cw - chh - 40)); x.fillText(first, 20 + chh + 22, 14 + 38); }
    x.font = font(800, fitSize(last, 800, 34, cw - chh - 40));
    x.fillText(last, 20 + chh + 22, 14 + (first ? 76 : 60));
    return { c, ax: 20, ay: 14 };
  });
  if (team.coach) {
    const ms = fitSize(team.coach, 800, 44, 860);
    const c = canvas(900, 96), x = c.getContext('2d');
    x.fillStyle = NAVY; x.fillRect(0, 0, 8, 74);
    x.fillStyle = 'rgba(14,21,80,0.75)'; x.font = font(700, 26); x.letterSpacing = '3px';
    x.fillText('MANAGER', 28, 26);
    x.letterSpacing = '0px';
    x.fillStyle = INK; x.font = font(800, ms);
    x.fillText(team.coach, 28, 70);
    T.coach = { c, ax: 0, ay: 0 };
  }
  return T;
}

// ---- the frame ------------------------------------------------------------------------------------------------------------
function mainReveal(p, t) {
  const open = eInOut(prog(t - p.t0, 0.15, 0.72)), close = eInOut(prog(t - p.exit, 0.12, 0.55));
  return clamp(open - close);
}

function frameSide(p, T, t, label, labelAt, dots) {
  const lt = t - p.t0, et = t - p.exit;
  const inK = eOut(prog(lt, 0, 0.45)), outK = eIn(prog(et, 0.45, 0.8));
  if (inK <= 0 || outK >= 1) return;
  const dx = -280 * (1 - inK) - 280 * outK;
  put(SP.sideBg, SIDE_X + dx, 0);
  const flash = lt > 0.3 ? Math.max(0, 1 - prog(lt, 0.35, 0.9)) : 0;
  if (flash > 0) put(SP.sideFlash, SIDE_X + SIDE_W - 60 + dx, 0, flash);
  const cx = SIDE_X + SIDE_W / 2 + dx;
  const lk = eBack(prog(lt, 0.3, 0.6));
  if (lk > 0 && SP.comp) put(SP.comp, cx, 100, 1, lk);
  const ck = eBack(prog(lt, 0.45, 0.8));
  if (ck > 0 && T.crest) {
    put(T.crest, cx, 262, 1, ck);
    const ring = prog(lt, 0.6, 1.1);
    if (ring > 0 && ring < 1) put(SP.ring, cx, 262, 0.8 * (1 - ring), (70 + ring * 70) / 140);
  }
  if (label && SP.labels[label]) {
    const k = eOut(prog(t, labelAt, labelAt + 0.35));
    LK.clip('studio-lab-' + p.side, [1, 0, 0, 1, SIDE_X + dx, 360], SIDE_W, 90);
    put(SP.labels[label], cx + 3, 408 + 30 * (1 - k), k);
    LK.endClip();
    if (dots) for (let i = 0; i < dots[1]; i++) put(i === dots[0] ? SP.dotOn : SP.dotOff, cx - (dots[1] - 1) * 9 + i * 18, 436);
  }
  const nk = eOut(prog(lt, 0.6, 1.05));
  if (nk > 0) {
    LK.clip('studio-name-' + p.side, [1, 0, 0, 1, SIDE_X + dx, 470], SIDE_W, H - 470);
    put(T.name, cx + 22, 1040 + 300 * (1 - nk));
    LK.endClip();
  }
}

function frameTitle(p, T, t) {
  const t0 = p.title, t1 = p.titleEnd;
  const out = eIn(prog(t, t1 - 0.1, t1 + 0.32));
  if (t < t0 || out >= 1) return;
  const lt = t - t0, ox = -260 * out, fade = 1 - out;
  const pk = eOut(prog(lt, 0.1, 0.4));
  put(T.tag, 330 - 120 * (1 - pk) + ox, 262, pk * fade);
  for (const L of T.letters) {
    const k = eOut(prog(lt, L.at, L.at + 0.3));
    if (k > 0) put(L.s, L.x + ox, L.y + 40 * (1 - k), k * fade);
  }
  const fk = eOut(prog(lt, 0.7, 1.1)), y = T.titleY;
  if (T.formation) {
    put(T.formation, 330 + ox, y + 70 + 30 * (1 - fk), fk * fade);
    put(T.formationLabel, 336 + ox, y + 112 + 30 * (1 - fk), fk * fade);
  }
  put(T.fixture, 332 + ox, y + 190, eOut(prog(lt, 0.95, 1.35)) * fade);
  // the kit in 3D, turning in from its back (the captain's name and number) to its front
  if (big && T.shirt) {
    const sk = prog(lt, 0.15, 2.3);
    const spin = (1 - eOut4(sk)) * Math.PI * 3;
    const settle = lt > 2.3 ? Math.sin((lt - 2.3) * 1.6) * 0.12 : 0;
    big.only(T.shirt);
    T.shirt.rotation.set(0.05, 0.28 + spin + settle, 0);
    T.shirt.position.set(0, 0.02 * Math.sin(lt * 2.0), 0);
    const sc = lerp(0.55, 1, eBack(prog(lt, 0.15, 0.75)));
    T.shirt.scale.setScalar(sc);
    big.render();
    const glow = Math.sin(clamp((lt - 0.6) / 1.4) * Math.PI);
    if (glow > 0) put(SP.glow, 1500 + ox, 560, glow * fade);
    put(SP.shadow, 1500 + ox, 905, clamp(sc) * fade);
    put(SP.big, 1100 + ox, 120, fade, 800 / bigSize);
  }
}

function framePage(p, T, pg, t, next, digits) {
  const n = pg.players.length, L = layout(n);
  const tOut = next ? next.t0 : p.subs;
  pg.players.forEach((pl, i) => {
    const P = T.players.get(pl);
    if (!P) return;
    const xs = MAIN_C + (i - (n - 1) / 2) * L.col;
    const ti = pg.t0 + 0.14 + i * 0.09;
    const kin = eOut(prog(t, ti, ti + 0.5));
    const kout = eIn(prog(t, tOut - 0.04 + i * 0.05, tOut + 0.3 + i * 0.05));
    if (kin <= 0 || kout >= 1) return;
    const S = L.S, bottom = 880, a = kin * (1 - kout);
    const dx = 140 * (1 - kin) - 220 * kout, dy = 60 * (1 - kin);
    const sc = 0.96 + 0.04 * kin + 0.008 * Math.sin((t - ti) * 1.3);
    put(P.pic, xs + dx, bottom + dy, a, sc);
    // the number rolling in (drawn after the panel, each digit in a window of its own), the captain's band, the flag
    const left = xs - L.col / 2 + 36 + dx, top = bottom - S + 74 + dy;
    let cx = left;
    P.digits.forEach((d, k) => {
      digits.push({ d, x: cx, y: top, at: ti + 0.1 + k * 0.07, a, key: p.side + '-' + pg.group + '-' + i + '-' + k });
      cx += d.w;
    });
    if (pl.cap) { const ck = eBack(prog(t, ti + 0.4, ti + 0.7)); if (ck > 0) put(SP.cap, cx + 34, top - 30, a, ck); }
    if (P.flag) { const fk = eOut(prog(t, ti + 0.25, ti + 0.5)); if (fk > 0) put(P.flag, left + 2, top + 16 + P.flag.ay, a, 1, fk); }
    const nk = eOut(prog(t, ti + 0.2, ti + 0.55));
    if (P.first) put(P.first, xs + dx, 940 + 22 * (1 - nk), a * nk);
    put(P.last, xs + dx, 996 + 22 * (1 - nk), a * nk);
  });
}

function frameSubs(p, T, t) {
  const t0 = p.subs, t1 = p.exit;
  if (t < t0 || t > t1 + 0.4) return;
  const cw = 440, chh = 92, gap = 26, cols = 3;
  const x0 = MAIN_C - (cols * cw + (cols - 1) * gap) / 2, y0 = 360;
  const kout = eIn(prog(t, t1, t1 + 0.32));
  T.subs.forEach((s, i) => {
    const r = Math.floor(i / cols), c = i % cols, ti = t0 + 0.12 + i * 0.06;
    const k = eOut(prog(t, ti, ti + 0.4));
    if (k <= 0) return;
    put(s, x0 + c * (cw + gap) + 160 * (1 - k) - 240 * kout, y0 + r * (chh + gap), k * (1 - kout));
  });
  const mk = eOut(prog(t, t0 + 0.75, t0 + 1.15));
  if (mk > 0 && T.coach) {
    const rows = Math.max(1, Math.ceil(T.subs.length / cols));
    put(T.coach, x0 - 200 * kout, y0 + rows * (chh + gap) + 40 + 16 * (1 - mk), mk * (1 - kout));
  }
}

function frameTeam(p, t) {
  if (t < p.t0 || t > p.end) return;
  const T = SP[p.side];
  let label = null, labelAt = 0, dots = null, sweepAt = null;
  p.pages.forEach((pg, i) => { if (t >= pg.t0) { label = pg.group; labelAt = pg.t0; dots = [i, p.pages.length]; sweepAt = pg.t0; } });
  if (t >= p.subs) { label = 'SUBSTITUTES'; labelAt = p.subs; dots = null; sweepAt = p.subs; }
  const digits = [];
  const k = mainReveal(p, t);
  if (k > 0) {
    // the main panel opening behind a slanted edge (the clip a parallelogram: nothing of it is left of the panel)
    const L = MAIN_X - 30, right = MAIN_X + (MAIN_W + 220) * k;
    LK.clip('studio-main-' + p.side, [1, 0, -220 / H, 1, L, 0], Math.max(1, right - L), H);
    put(SP.mainBg, MAIN_X, 0);
    put(SP.streaks, (t - p.t0) * 14, 0);
    if (sweepAt !== null) {
      const sk = prog(t, sweepAt, sweepAt + 0.45);
      if (sk > 0 && sk < 1) put(SP.sweep, MAIN_X - 400 + sk * (MAIN_W + 900), 0, Math.sin(sk * Math.PI));
    }
    const marks = p.pages.map((pg, i) => [pg.group, pg.t0, (p.pages[i + 1] || { t0: p.subs }).t0]).concat([['SUBS', p.subs, p.exit]]);
    for (const [w, a0, a1] of marks) {
      const kin = eOut(prog(t, a0 + 0.05, a0 + 0.6)), kout = eIn(prog(t, a1 - 0.05, a1 + 0.3));
      if (kin > 0 && kout < 1) put(SP.wm[w], MAIN_X + 40 + 260 * (1 - kin) - 300 * kout, 300, 0.07 * kin * (1 - kout));
    }
    frameTitle(p, T, t);
    p.pages.forEach((pg, i) => { if (t >= pg.t0 - 0.1) framePage(p, T, pg, t, p.pages[i + 1], digits); });
    frameSubs(p, T, t);
    LK.endClip();
  }
  // the numbers' digits rolling up from below, each in its own window (over the panel)
  for (const g of digits) {
    const kk = eOut(prog(t, g.at, g.at + 0.32));
    const size = 66;
    LK.clip('studio-d-' + g.key, [1, 0, 0, 1, g.x - 2, g.y - size * 0.86], g.d.w + 4, size * 0.98);
    put(g.d.now, g.x, g.y + size * (1 - kk), g.a);
    if (kk < 1) put(g.d.was, g.x, g.y + size * (1 - kk) - size, g.a * 0.35 * (1 - kk));
    LK.endClip();
  }
  frameSide(p, T, t, label, labelAt, dots);
}

/** Draws the moment t (seconds from its start). */
export function frame(t) {
  LK.begin();
  for (const p of plans) frameTeam(p, t);
  LK.end();
}

// ---- getting ready ------------------------------------------------------------------------------------------------------------
export async function init(c) {
  cfg = c;
  CUES = [];
  plans = [];
  LK.mount();
  if (document.fonts && document.fonts.load) {
    for (const w of [600, 700, 800]) { try { await document.fonts.load(font(w, 40)); } catch (e) { /* the fallback face */ } }
    try { await document.fonts.load('800 40px "BarlowC"'); } catch (e) { /* the fallback face */ }
  }
  // the title cards' shirt, drawn live: smaller on a TV that is slow to draw it
  const want = cfg.q && cfg.q.shirt > 0 ? Math.max(320, Math.min(800, cfg.q.shirt | 0)) : 720;
  try {
    bigSize = want;
    big = stage(bigSize, bigSize, { dist: 3.0, fov: 30, fine: bigSize >= 600 ? 0.028 : 0.036, tex: bigSize >= 600 ? 1024 : 512 });
  } catch (e) {
    big = null;   // no WebGL: the title cards without the shirt
  }
  if (big) SP.big = { c: big.canvas, ax: 0, ay: 0 };
  const logo = await load(cfg.comp && cfg.comp.logo);
  makeSide(cfg.comp || { key: '' }, logo);
  makeMain();
  SP.home = await makeTeam(cfg.home, 'home', cfg.away);
  SP.away = await makeTeam(cfg.away, 'away', cfg.home);
  // how long the shirt takes to draw here: a slow one is drawn again smaller
  if (big && !(cfg.q && cfg.q.shirt > 0) && SP.home.shirt) {
    big.only(SP.home.shirt);
    const ms = [], gl = big.renderer.getContext(), px = new Uint8Array(4);
    for (let i = 0; i < 3; i++) {
      const a = performance.now();
      SP.home.shirt.rotation.set(0.05, i, 0);
      big.render();
      // a pixel read back waits for the drawing to be done (finish() may not)
      try { gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px); } catch (e) { /* no matter */ }
      ms.push(performance.now() - a);
    }
    ms.sort((a, b) => a - b);
    if (ms[1] > 26) {
      big.renderer.setSize(512, 512, false);
      big.canvas.width = 512; big.canvas.height = 512;
      bigSize = 512;
      SP.big = { c: big.canvas, ax: 0, ay: 0 };
    }
    SP.shirtMs = ms[1];
  }
  plans = [plan(cfg.home, 0, 'home'), plan(cfg.away, AWAY_AT, 'away')];
  CUES.sort((a, b) => a.at - b.at);
  frame(0);
  return { len: LEN, cues: CUES, shirtMs: SP.shirtMs || 0, shirtSize: bigSize };
}

window.STUDIO = { init, frame, BEAT, get len() { return LEN; }, get cues() { return CUES; } };
