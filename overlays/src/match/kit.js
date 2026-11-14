// The match intro's kit: easing, the clubs' colours, and everything drawn once on 2D canvases and made into textures
// (lettering, crests in chrome and in neon, players lit in their club's colour, the panels' faces, glows).
import * as THREE from '../vendor/three.module.js';

// ---- easing and numbers ---------------------------------------------------------------------------------------------
export const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
export const ramp = (a, b, x) => clamp((x - a) / (b - a));
export const ss = (a, b, x) => { const t = ramp(a, b, x); return t * t * (3 - 2 * t); };
export const lerp = (a, b, t) => a + (b - a) * t;
export const eOut = (t) => 1 - Math.pow(1 - clamp(t), 3);
export const eIn = (t) => Math.pow(clamp(t), 3);
export const eInOut = (t) => { t = clamp(t); return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; };
export const eOutExpo = (t) => { t = clamp(t); return t >= 1 ? 1 : 1 - Math.pow(2, -10 * t); };
export const eOutBack = (t, s = 1.3) => { t = clamp(t) - 1; return 1 + (s + 1) * t * t * t + s * t * t; };
export function rng(seed) {
  let s = (Math.imul(seed | 0, 2654435761) >>> 0) || 1;
  return () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; };
}

// ---- colours (worked out in sRGB, as CSS strings) -------------------------------------------------------------------
function rgbOf(hex) {
  const h = String(hex || '').replace('#', '');
  const v = h.length === 3 ? h.split('').map((c) => c + c).join('') : h.padEnd(6, '0').slice(0, 6);
  return [parseInt(v.slice(0, 2), 16) / 255, parseInt(v.slice(2, 4), 16) / 255, parseInt(v.slice(4, 6), 16) / 255];
}
function hslOf(hex) {
  const [r, g, b] = rgbOf(hex), mx = Math.max(r, g, b), mn = Math.min(r, g, b), l = (mx + mn) / 2;
  if (mx === mn) return [0, 0, l];
  const d = mx - mn, s = l > 0.5 ? d / (2 - mx - mn) : d / (mx + mn);
  const h = mx === r ? (g - b) / d + (g < b ? 6 : 0) : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return [h / 6, s, l];
}
export function hsl(h, s, l) {
  const f = (n) => { const k = (n + h * 12) % 12, a = s * Math.min(l, 1 - l); return l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1)); };
  const x = (v) => Math.round(clamp(v) * 255).toString(16).padStart(2, '0');
  return '#' + x(f(0)) + x(f(8)) + x(f(4));
}
export const col = (css) => new THREE.Color(css);
export function rgba(hex, a) { const [r, g, b] = rgbOf(hex); return `rgba(${Math.round(r * 255)},${Math.round(g * 255)},${Math.round(b * 255)},${a})`; }

/**
 * A club's look from its colours ({base, deep, accent}, or {color, color2}): glow (its light, bright), panel (its
 * buildings), deep (the dark behind), ink (the words beside white: first names, numbers, the chant). Black-and-white
 * clubs take their accent as their light when it has colour, else silver.
 */
export function look(t) {
  const base = t.base || t.color || '#3a4660', acc = t.accent || t.color2 || '#ffffff';
  const [h, s, l] = hslOf(base), [ah, as, al] = hslOf(acc);
  const neutral = s < 0.2 || l < 0.1 || l > 0.9;
  const accLit = as > 0.3 && al > 0.2 && al < 0.86;
  let gh, gs, gl;
  if (!neutral) [gh, gs, gl] = [h, Math.max(0.78, s), clamp(l, 0.48, 0.58)];
  else if (accLit) [gh, gs, gl] = [ah, Math.max(0.72, as), clamp(al, 0.5, 0.62)];
  else [gh, gs, gl] = [0.6, 0.25, 0.8];
  const glow = hsl(gh, gs, gl);
  const panel = neutral ? hsl(gh, accLit ? 0.22 : 0.08, 0.12) : hsl(h, Math.min(0.85, s), 0.2);
  const mid = neutral ? hsl(gh, accLit ? 0.3 : 0.1, 0.26) : hsl(h, Math.min(0.9, s), 0.36);
  const deep = neutral ? hsl(gh, accLit ? 0.3 : 0.12, 0.04) : hsl(h, Math.min(0.9, s), 0.055);
  // the words' colour: the accent when it is a colour of its own (not white, not the light again), else a pale light
  let ink = acc;
  const sameHue = Math.min(Math.abs(ah - gh), 1 - Math.abs(ah - gh)) < 0.05;
  if (al > 0.9 || as < 0.2 || (sameHue && !neutral)) ink = neutral ? glow : hsl(gh, 0.95, 0.74);
  if (hslOf(ink)[2] < 0.35) ink = hsl(hslOf(ink)[0], hslOf(ink)[1], 0.6);
  return { glow, panel, mid, deep, ink, neutral };
}

// ---- canvases and textures -----------------------------------------------------------------------------------------
export function canvas(w, h) {
  const c = document.createElement('canvas');
  c.width = Math.max(2, Math.ceil(w)); c.height = Math.max(2, Math.ceil(h));
  return c;
}
export function tex(c, mips = true) {
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.generateMipmaps = mips;
  t.minFilter = mips ? THREE.LinearMipmapLinearFilter : THREE.LinearFilter;
  t.anisotropy = 4;
  t.needsUpdate = true;
  return t;
}
/** A picture's shape filled with one colour. */
export function tint(src, color) {
  const c = canvas(src.width, src.height), g = c.getContext('2d');
  g.drawImage(src, 0, 0);
  g.globalCompositeOperation = 'source-in';
  g.fillStyle = color; g.fillRect(0, 0, c.width, c.height);
  return c;
}
/** A picture's shape grown by r all round (drawn round a ring of offsets), in one colour; returned with r + 2 margin. */
export function grown(src, w, h, r, color) {
  const m = Math.ceil(r) + 2, c = canvas(w + 2 * m, h + 2 * m), g = c.getContext('2d');
  const n = Math.max(12, Math.round(r * 1.6));
  for (let k = 0; k < 2; k++) {
    const rr = k ? r : r * 0.5;
    for (let i = 0; i < n; i++) { const a = (i / n) * Math.PI * 2; g.drawImage(src, m + Math.cos(a) * rr, m + Math.sin(a) * rr, w, h); }
  }
  g.drawImage(src, m, m, w, h);
  g.globalCompositeOperation = 'source-in';
  g.fillStyle = color; g.fillRect(0, 0, c.width, c.height);
  return c;
}
/** A picture scaled to fit inside size x size. */
export function fitted(img, size) {
  const k = size / Math.max(img.width || img.naturalWidth || 1, img.height || img.naturalHeight || 1);
  const w = Math.max(2, Math.round((img.width || img.naturalWidth) * k)), h = Math.max(2, Math.round((img.height || img.naturalHeight) * k));
  const c = canvas(w, h); c.getContext('2d').drawImage(img, 0, 0, w, h);
  return c;
}

// ---- lettering -----------------------------------------------------------------------------------------------------
/**
 * Words on a canvas, cut close to their ink: {size, weight, family, fill (CSS, or [[stop, CSS]...] top to bottom),
 * spacing (px between letters), skew (radians, leaning right), sy (drawn taller), stroke {w, c}, glow {blur, c, a},
 * extrude {n, dx, dy, top, bottom} (a dark side drawn below, for depth), pad}. Returns {canvas, w, h, letters: [{x0, x1}]}
 * (each letter's span, for planes per letter).
 */
export function lettering(str, o = {}) {
  str = String(str || '');
  const size = o.size || 100, family = o.family || 'BarlowC', weight = o.weight || 800;
  const font = `${weight} ${size}px ${family}, 'Barlow Condensed', sans-serif`;
  const m = canvas(4, 4).getContext('2d'); m.font = font;
  const chars = [...str], sp = o.spacing || 0, sy = o.sy || 1;
  const adv = chars.map((ch) => m.measureText(ch).width);
  const width = adv.reduce((a, b) => a + b, 0) + sp * Math.max(0, chars.length - 1);
  const mm = m.measureText(str || 'H'), mh = m.measureText('H');
  const asc = Math.max(mm.actualBoundingBoxAscent || 0, mh.actualBoundingBoxAscent || size * 0.7);
  const desc = Math.max(0, mm.actualBoundingBoxDescent || 0);
  const k = Math.tan(o.skew || 0);
  const ex = o.extrude || null, exW = ex ? Math.abs(ex.dx) * ex.n : 0, exH = ex ? Math.abs(ex.dy) * ex.n : 0;
  const glowPad = o.glow ? o.glow.blur * 1.6 : 0;
  const pad = (o.pad == null ? 8 : o.pad) + glowPad + (o.stroke ? o.stroke.w : 0);
  const th = (asc + desc) * sy;
  const w = width + Math.abs(k) * th + exW + pad * 2, h = th + exH + pad * 2;
  const c = canvas(w, h), g = c.getContext('2d');
  const ox = pad + (k > 0 ? k * desc * sy : -k * asc * sy), oy = pad + asc * sy;
  const xs = []; let x = 0;
  for (let i = 0; i < chars.length; i++) { xs.push(x); x += adv[i] + sp; }
  const put = (dx, dy, how) => {
    g.save();
    g.setTransform(1, 0, -k * sy, sy, ox + dx, oy + dy);
    g.font = font; g.textBaseline = 'alphabetic';
    for (let i = 0; i < chars.length; i++) how(chars[i], xs[i]);
    g.restore();
  };
  const grad = (stops, y0, y1) => {
    if (typeof stops === 'string') return stops;
    const gr = g.createLinearGradient(0, y0, 0, y1);
    for (const [s, cc] of stops) gr.addColorStop(s, cc);
    return gr;
  };
  if (ex) {
    for (let i = ex.n; i >= 1; i--) {
      g.fillStyle = grad(ex.fill || [[0, ex.top || '#3a4252'], [1, ex.bottom || '#0d1118']], -asc, desc);
      put(ex.dx * i, ex.dy * i, (ch, cx) => g.fillText(ch, cx, 0));
    }
  }
  if (o.glow) {
    g.save();
    g.shadowColor = o.glow.c; g.shadowBlur = o.glow.blur;
    g.globalAlpha = o.glow.a == null ? 1 : o.glow.a;
    g.fillStyle = typeof o.fill === 'string' ? o.fill : '#ffffff';
    put(0, 0, (ch, cx) => g.fillText(ch, cx, 0));
    g.restore();
  }
  if (o.stroke) {
    g.lineJoin = 'round'; g.lineWidth = o.stroke.w * 2; g.strokeStyle = o.stroke.c;
    put(0, 0, (ch, cx) => g.strokeText(ch, cx, 0));
  }
  if (o.fill !== null) {
    g.fillStyle = grad(o.fill || '#ffffff', -asc, desc);
    put(0, 0, (ch, cx) => g.fillText(ch, cx, 0));
  }
  if (o.outline) {
    g.lineWidth = o.outline.w; g.strokeStyle = o.outline.c;
    put(0, 0, (ch, cx) => g.strokeText(ch, cx, 0));
  }
  const letters = chars.map((ch, i) => ({ ch, x0: ox + xs[i] - (k > 0 ? 0 : k * asc * sy) - 2, x1: ox + xs[i] + adv[i] + Math.abs(k) * th + exW + 2 }));
  return { canvas: c, w: c.width, h: c.height, letters, ascent: asc * sy, pad };
}

/** Chrome lettering's fill and side (a polished silver face, a dark steel side). */
export const CHROME = [[0, '#ffffff'], [0.38, '#eef2f8'], [0.5, '#a5afbf'], [0.53, '#5f6878'], [0.6, '#dfe6ef'], [1, '#aeb8c6']];
export const WHITE_FADE = [[0, '#ffffff'], [0.62, '#f1f3f7'], [1, '#b9bfca']];

// ---- crests ------------------------------------------------------------------------------------------------------------
/**
 * The crest in polished metal: a silver bevelled rim round its shape (as the network's team logos have), a dark line
 * between, the crest, a sheen from above, and its club's glow behind. Returns {canvas, side} (side: the shape in dark
 * steel, for the depth behind the face).
 */
export function chromeCrest(img, size, glowCss) {
  const face = fitted(img, size), w = face.width, h = face.height;
  const R = Math.max(5, Math.round(size * 0.03)), P = R + 46;
  const c = canvas(w + P * 2, h + P * 2), g = c.getContext('2d');
  const rim = grown(face, w, h, R, '#ffffff'), rm = Math.ceil(R) + 2;
  if (glowCss) {
    g.save(); g.filter = 'blur(26px)'; g.globalAlpha = 0.95; g.drawImage(tint(rim, glowCss), P - rm, P - rm); g.restore();
  }
  // the rim: silver, lit from the top left
  const silver = canvas(rim.width, rim.height), sg = silver.getContext('2d');
  const gr = sg.createLinearGradient(0, 0, rim.width * 0.5, rim.height);
  gr.addColorStop(0, '#ffffff'); gr.addColorStop(0.3, '#d9dfe9'); gr.addColorStop(0.48, '#8a95a6'); gr.addColorStop(0.56, '#f4f7fb');
  gr.addColorStop(0.8, '#b3bdcb'); gr.addColorStop(1, '#6d7787');
  sg.fillStyle = gr; sg.fillRect(0, 0, rim.width, rim.height);
  sg.globalCompositeOperation = 'destination-in'; sg.drawImage(rim, 0, 0);
  // its bevel: a bright edge top left, a dark one bottom right
  const lit = tint(rim, 'rgba(255,255,255,0.9)'), dark = tint(rim, 'rgba(20,26,38,0.9)');
  g.drawImage(dark, P - rm + 2, P - rm + 3);
  g.drawImage(silver, P - rm, P - rm);
  g.save(); g.globalCompositeOperation = 'source-atop'; g.globalAlpha = 0.5; g.drawImage(lit, P - rm - 1.5, P - rm - 2); g.restore();
  const line = Math.max(2, R * 0.28), ln = grown(face, w, h, line, 'rgba(8,11,18,0.9)'), lm = Math.ceil(line) + 2;
  g.drawImage(ln, P - lm, P - lm);
  g.drawImage(face, P, P);
  // a sheen across the top (the light from above)
  const sh = canvas(w, h), shg = sh.getContext('2d');
  shg.drawImage(face, 0, 0); shg.globalCompositeOperation = 'source-in';
  const sgr = shg.createLinearGradient(0, 0, w * 0.25, h);
  sgr.addColorStop(0, 'rgba(255,255,255,0.32)'); sgr.addColorStop(0.42, 'rgba(255,255,255,0.08)'); sgr.addColorStop(0.5, 'rgba(255,255,255,0)');
  sgr.addColorStop(1, 'rgba(0,0,0,0.18)');
  shg.fillStyle = sgr; shg.fillRect(0, 0, w, h);
  g.drawImage(sh, P, P);
  // the side: the rim's shape in dark steel
  const side = canvas(c.width, c.height), sdg = side.getContext('2d');
  const sdr = sdg.createLinearGradient(0, 0, 0, side.height);
  sdr.addColorStop(0, '#4b5568'); sdr.addColorStop(1, '#121722');
  sdg.drawImage(rim, P - rm, P - rm); sdg.globalCompositeOperation = 'source-in'; sdg.fillStyle = sdr; sdg.fillRect(0, 0, side.width, side.height);
  return { canvas: c, side, w: c.width, h: c.height };
}

/**
 * The crest drawn in neon: two glowing outlines round its shape smoothed (a busy crest's every notch would read as
 * noise), the club's light round a white-hot core.
 */
export function neonCrest(img, size, glowCss) {
  const face = fitted(img, size), w = face.width, h = face.height;
  // the shape, smoothed: blurred and cut at half
  const m = Math.ceil(size * 0.08), sm = canvas(w + 2 * m, h + 2 * m), sg = sm.getContext('2d');
  sg.filter = `blur(${Math.max(3, size * 0.022).toFixed(1)}px)`;
  sg.drawImage(tint(face, '#ffffff'), m, m);
  sg.filter = 'none';
  const id = sg.getImageData(0, 0, sm.width, sm.height), d = id.data;
  for (let i = 3; i < d.length; i += 4) d[i] = d[i] > 110 ? 255 : 0;
  sg.putImageData(id, 0, 0);
  const W2 = sm.width, H2 = sm.height;
  const r1 = Math.max(6, size * 0.038), r0 = Math.max(3, size * 0.022), P = 40;
  const ring = (ra, rb) => {
    const a = grown(sm, W2, H2, ra, '#fff'), b = grown(sm, W2, H2, rb, '#fff'), am = Math.ceil(ra) + 2, bm = Math.ceil(rb) + 2;
    const g = a.getContext('2d'); g.globalCompositeOperation = 'destination-out'; g.drawImage(b, am - bm, am - bm);
    return { c: a, m: am };
  };
  const outer = ring(r1, r0), core = ring(r1 - (r1 - r0) * 0.38, r0 + (r1 - r0) * 0.38);
  const c = canvas(W2 + P * 2, H2 + P * 2), g = c.getContext('2d');
  const at = (r) => [P - r.m, P - r.m];
  const glow = tint(outer.c, glowCss);
  g.save(); g.filter = 'blur(10px)'; g.globalAlpha = 0.8; g.drawImage(glow, ...at(outer)); g.filter = 'blur(3px)'; g.globalAlpha = 1; g.drawImage(glow, ...at(outer)); g.restore();
  g.drawImage(glow, ...at(outer));
  g.drawImage(tint(core.c, 'rgba(255,255,255,0.92)'), ...at(core));
  return { canvas: c, w: c.width, h: c.height };
}

/**
 * A player known only by FotMob's head and shoulders: a round portrait (a medallion) in his club's light, with a ring,
 * as a picture of its own rather than a cut-out pretending to be one.
 */
export function medallion(img, glowCss, n = 620) {
  const P = 60, c = canvas(n + P * 2, n + P * 2), g = c.getContext('2d'), cx = c.width / 2, cy = c.height / 2, r = n / 2;
  g.save(); g.shadowColor = glowCss; g.shadowBlur = 50; g.beginPath(); g.arc(cx, cy, r, 0, Math.PI * 2); g.fillStyle = glowCss; g.fill(); g.restore();
  const bg = g.createRadialGradient(cx, cy - r * 0.3, r * 0.1, cx, cy, r);
  bg.addColorStop(0, rgba(glowCss, 1)); bg.addColorStop(1, rgba('#06040f', 1));
  g.save(); g.beginPath(); g.arc(cx, cy, r, 0, Math.PI * 2); g.clip();
  g.fillStyle = bg; g.fillRect(0, 0, c.width, c.height);
  const w = img.width || img.naturalWidth, h = img.height || img.naturalHeight, k = (n * 1.02) / Math.max(w, h);
  g.drawImage(img, cx - (w * k) / 2, cy - (h * k) / 2 + n * 0.06, w * k, h * k);
  g.restore();
  g.lineWidth = n * 0.022; g.strokeStyle = 'rgba(255,255,255,0.92)'; g.beginPath(); g.arc(cx, cy, r - g.lineWidth / 2, 0, Math.PI * 2); g.stroke();
  g.lineWidth = n * 0.01; g.strokeStyle = glowCss; g.beginPath(); g.arc(cx, cy, r + n * 0.03, 0, Math.PI * 2); g.stroke();
  return { canvas: c, w: c.width, h: c.height, face: true };
}

/** A player's cut-out lit as the network lights its players: a glow of the club's colour behind, its light on one
 * edge and white on the other, a little more contrast, and faded out at the waist where the picture ends. */
export function litPlayer(img, glowCss, fadeAt = 0.86) {
  const w = img.width || img.naturalWidth, h = img.height || img.naturalHeight, P = 40;
  const src = canvas(w, h); src.getContext('2d').drawImage(img, 0, 0, w, h);
  const c = canvas(w + P * 2, h + P), g = c.getContext('2d');
  g.save(); g.filter = 'blur(30px)'; g.globalAlpha = 0.65; g.drawImage(tint(src, glowCss), P, P); g.restore();
  g.save(); g.filter = 'contrast(1.08) saturate(1.06)'; g.drawImage(src, P, P); g.restore();
  const edge = (dx, color, a) => {
    const e = canvas(w, h), eg = e.getContext('2d');
    eg.drawImage(src, 0, 0); eg.globalCompositeOperation = 'destination-out'; eg.drawImage(src, dx, Math.abs(dx) * 0.4);
    eg.globalCompositeOperation = 'source-in'; eg.fillStyle = color; eg.fillRect(0, 0, w, h);
    g.save(); g.globalAlpha = a; g.filter = 'blur(2px)'; g.globalCompositeOperation = 'lighter'; g.drawImage(e, P, P); g.restore();
  };
  edge(9, glowCss, 0.95); edge(-7, '#ffffff', 0.55);
  // the waist: faded out over the picture's last eighth
  g.save(); g.globalCompositeOperation = 'destination-out';
  const f = g.createLinearGradient(0, P + h * fadeAt, 0, P + h);
  f.addColorStop(0, 'rgba(0,0,0,0)'); f.addColorStop(1, 'rgba(0,0,0,1)');
  g.fillStyle = f; g.fillRect(0, P + h * fadeAt, c.width, h * (1 - fadeAt) + 2); g.restore();
  return { canvas: c, w: c.width, h: c.height, top: P, side: P };
}

// ---- glows, beams, faces ---------------------------------------------------------------------------------------------
/** A soft round glow (white; tinted by the material). */
export function glowDot(n = 256, hard = 0.0) {
  const c = canvas(n, n), g = c.getContext('2d'), gr = g.createRadialGradient(n / 2, n / 2, 0, n / 2, n / 2, n / 2);
  gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(Math.max(0.05, hard), 'rgba(255,255,255,0.75)');
  gr.addColorStop(0.4, 'rgba(255,255,255,0.18)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = gr; g.fillRect(0, 0, n, n);
  return c;
}
/** A streak of light: a hot thin core, a wide soft glow, fading at both ends. */
export function streak(w = 1024, h = 128) {
  const c = canvas(w, h), g = c.getContext('2d');
  for (const [a, th] of [[0.25, h * 0.45], [0.5, h * 0.16], [1, h * 0.05]]) {
    const gr = g.createLinearGradient(0, h / 2 - th, 0, h / 2 + th);
    gr.addColorStop(0, 'rgba(255,255,255,0)'); gr.addColorStop(0.5, `rgba(255,255,255,${a})`); gr.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = gr; g.fillRect(0, h / 2 - th, w, th * 2);
  }
  g.globalCompositeOperation = 'destination-in';
  const fx = g.createLinearGradient(0, 0, w, 0);
  fx.addColorStop(0, 'rgba(0,0,0,0)'); fx.addColorStop(0.35, 'rgba(0,0,0,1)'); fx.addColorStop(0.65, 'rgba(0,0,0,1)'); fx.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = fx; g.fillRect(0, 0, w, h);
  return c;
}
/** A neon bar's glow across its width (for lines drawn as thin planes): bright core, soft sides. */
export function barGlow(h = 64) {
  const c = canvas(4, h), g = c.getContext('2d'), gr = g.createLinearGradient(0, 0, 0, h);
  gr.addColorStop(0, 'rgba(255,255,255,0)'); gr.addColorStop(0.36, 'rgba(255,255,255,0.22)'); gr.addColorStop(0.47, 'rgba(255,255,255,0.9)');
  gr.addColorStop(0.5, 'rgba(255,255,255,1)'); gr.addColorStop(0.53, 'rgba(255,255,255,0.9)'); gr.addColorStop(0.64, 'rgba(255,255,255,0.22)');
  gr.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = gr; g.fillRect(0, 0, 4, h);
  return c;
}
/**
 * A building's face: its colour darkening upwards with a gloss, bright edges and strips of light in the club's colour
 * (shapes that still read when the face is stretched over a tall block). Two canvases: the face (map) and its lights
 * alone (emissive, in the light's own colour).
 */
export function facade(lk, seed, kind = 0) {
  const R = rng(seed), W = 256, H = 1024;
  const map = canvas(W, H), g = map.getContext('2d'), em = canvas(W, H), e = em.getContext('2d');
  const gr = g.createLinearGradient(0, 0, W * 0.6, H);
  gr.addColorStop(0, lk.mid); gr.addColorStop(0.5, lk.panel); gr.addColorStop(1, lk.deep);
  g.fillStyle = gr; g.fillRect(0, 0, W, H);
  // a gloss down one side
  const gl = g.createLinearGradient(0, 0, W, 0);
  gl.addColorStop(0, 'rgba(255,255,255,0.10)'); gl.addColorStop(0.3, 'rgba(255,255,255,0)'); gl.addColorStop(1, 'rgba(0,0,0,0.25)');
  g.fillStyle = gl; g.fillRect(0, 0, W, H);
  e.fillStyle = '#000'; e.fillRect(0, 0, W, H);
  const light = (x, y, w, h, a, white) => {
    e.globalAlpha = a; e.fillStyle = white ? '#ffffff' : lk.glow; e.fillRect(x, y, w, h);
    g.globalAlpha = a * 0.7; g.fillStyle = white ? '#ffffff' : lk.glow; g.fillRect(x, y, w, h);
    g.globalAlpha = 1; e.globalAlpha = 1;
  };
  if (kind === 0) {
    light(0, 0, 7, H, 1); light(W - 5, 0, 5, H, 0.55);
    for (let i = 0; i < 4; i++) { const y = 80 + R() * (H - 160); light(10, y, W - 20, 4, 0.5 + R() * 0.5); }
    light(W * 0.5 - 1, 0, 2, H, 0.18, true);
  } else if (kind === 1) {
    light(0, 0, 4, H, 0.9); light(W - 4, 0, 4, H, 0.9);
    const x = 40 + R() * (W - 120); light(x, 0, 10 + R() * 18, H, 0.85);
    for (let i = 0; i < 3; i++) light(0, R() * H, W, 3, 0.4, true);
  } else {
    for (let y = 0; y < H; y += 64) light(10, y + 20, W - 20, 2, 0.35 + R() * 0.4);
    light(0, 0, 5, H, 0.9);
  }
  return { map, em };
}
/** The night sky: dark at the top, the club's colour low down, stars, and slow cloud. */
export function sky(top, low, seed) {
  const R = rng(seed), W = 1024, H = 1024, c = canvas(W, H), g = c.getContext('2d');
  const gr = g.createLinearGradient(0, 0, 0, H);
  gr.addColorStop(0, top); gr.addColorStop(0.6, low); gr.addColorStop(1, top);
  g.fillStyle = gr; g.fillRect(0, 0, W, H);
  for (let i = 0; i < 40; i++) {
    const x = R() * W, y = R() * H * 0.8, r = 60 + R() * 220, cg = g.createRadialGradient(x, y, 0, x, y, r);
    cg.addColorStop(0, 'rgba(255,255,255,0.05)'); cg.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = cg; g.fillRect(x - r, y - r, r * 2, r * 2);
  }
  for (let i = 0; i < 260; i++) {
    g.globalAlpha = 0.2 + R() * 0.8; g.fillStyle = '#ffffff';
    const s = R() < 0.08 ? 2.2 : 1.1; g.fillRect(R() * W, R() * H * 0.85, s, s);
  }
  g.globalAlpha = 1;
  return c;
}
/** A diagonal grid on dark (the floor of the logo shots). */
export function grid(deep, line, glowCss) {
  const W = 1024, H = 1024, c = canvas(W, H), g = c.getContext('2d');
  const gr = g.createRadialGradient(W / 2, H * 0.45, 0, W / 2, H / 2, W * 0.75);
  gr.addColorStop(0, glowCss ? rgba(glowCss, 0.55) : line); gr.addColorStop(0.45, deep); gr.addColorStop(1, '#000000');
  g.fillStyle = gr; g.fillRect(0, 0, W, H);
  g.strokeStyle = line; g.lineWidth = 1.2; g.globalAlpha = 0.55;
  for (let i = -H; i < W + H; i += 34) {
    g.beginPath(); g.moveTo(i, 0); g.lineTo(i + H, H); g.stroke();
    g.beginPath(); g.moveTo(i, H); g.lineTo(i + H, 0); g.stroke();
  }
  g.globalAlpha = 1;
  return c;
}
/** A city's skyline in silhouette, windows lit (behind the players' names). */
export function skyline(lk, seed, W = 1400, H = 260) {
  const R = rng(seed), c = canvas(W, H), g = c.getContext('2d');
  let x = 0;
  while (x < W) {
    const w = 30 + R() * 90, h = H * (0.25 + R() * 0.75);
    const gr = g.createLinearGradient(0, H - h, 0, H); gr.addColorStop(0, lk.mid); gr.addColorStop(1, lk.panel);
    g.fillStyle = gr; g.fillRect(x, H - h, w - 4, h);
    g.fillStyle = rgba(lk.glow, 0.7);
    for (let y = H - h + 8; y < H - 6; y += 10) for (let xx = x + 5; xx < x + w - 10; xx += 9) if (R() < 0.35) g.fillRect(xx, y, 4, 3);
    x += w;
  }
  g.globalCompositeOperation = 'destination-in';
  const f = g.createLinearGradient(0, 0, W, 0);
  f.addColorStop(0, 'rgba(0,0,0,0)'); f.addColorStop(0.15, 'rgba(0,0,0,1)'); f.addColorStop(0.85, 'rgba(0,0,0,1)'); f.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = f; g.fillRect(0, 0, W, H);
  return c;
}
/** A football for the comet: white, its panels dark, lit from the top left. */
export function football(n = 256) {
  const c = canvas(n, n), g = c.getContext('2d'), r = n * 0.46, cx = n / 2, cy = n / 2;
  g.save(); g.beginPath(); g.arc(cx, cy, r, 0, Math.PI * 2); g.clip();
  g.fillStyle = '#f4f6fa'; g.fillRect(0, 0, n, n);
  const pent = (x, y, s, rot) => {
    g.beginPath();
    for (let i = 0; i < 5; i++) { const a = rot + (i / 5) * Math.PI * 2; g[i ? 'lineTo' : 'moveTo'](x + Math.cos(a) * s, y + Math.sin(a) * s); }
    g.closePath(); g.fill();
  };
  g.fillStyle = '#1b2230';
  pent(cx, cy, r * 0.3, -Math.PI / 2);
  for (let i = 0; i < 5; i++) { const a = -Math.PI / 2 + (i / 5) * Math.PI * 2 + Math.PI / 5; pent(cx + Math.cos(a) * r * 0.86, cy + Math.sin(a) * r * 0.86, r * 0.3, a + Math.PI); }
  const sh = g.createRadialGradient(cx - r * 0.35, cy - r * 0.4, r * 0.1, cx, cy, r * 1.05);
  sh.addColorStop(0, 'rgba(255,255,255,0.35)'); sh.addColorStop(0.6, 'rgba(0,0,0,0)'); sh.addColorStop(1, 'rgba(0,0,10,0.55)');
  g.fillStyle = sh; g.fillRect(0, 0, n, n);
  g.restore();
  return c;
}
/** The Whitebrook Road mark (a house with the play button in it) in chrome, with a glow of the given colour. */
export function brandMark(n, glowCss) {
  const c = canvas(n, n), g = c.getContext('2d'), s = n / 100;
  const house = (gg) => {
    gg.beginPath();
    gg.moveTo(50 * s, 14 * s); gg.lineTo(86 * s, 44 * s); gg.lineTo(86 * s, 86 * s); gg.lineTo(14 * s, 86 * s); gg.lineTo(14 * s, 44 * s); gg.closePath();
  };
  g.save(); g.shadowColor = glowCss; g.shadowBlur = 18 * s; house(g); g.fillStyle = '#c7d2e3'; g.fill(); g.restore();
  const gr = g.createLinearGradient(0, 14 * s, 0, 86 * s);
  for (const [k, cc] of CHROME) gr.addColorStop(k, cc);
  house(g); g.fillStyle = gr; g.fill();
  g.lineWidth = 2.2 * s; g.strokeStyle = 'rgba(30,36,50,0.9)'; house(g); g.stroke();
  // the play button, cut through to the glow
  g.beginPath(); g.moveTo(41 * s, 50 * s); g.lineTo(41 * s, 76 * s); g.lineTo(63 * s, 63 * s); g.closePath();
  g.fillStyle = glowCss; g.save(); g.shadowColor = glowCss; g.shadowBlur = 10 * s; g.fill(); g.restore();
  g.fillStyle = 'rgba(255,255,255,0.65)'; g.fill();
  return c;
}
