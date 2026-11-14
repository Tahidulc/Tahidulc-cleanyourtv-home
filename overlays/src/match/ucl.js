// The match intro's Champions League style (for its games): a night of purple and blue after the competition's own
// broadcast look, made our own: its mark among chrome ribbons, a white star in a diamond among star-panelled balls, a
// hall of pointed arches with the trophy at its end, drapes for each club's crest and star, the two clubs either side of
// the star, a dark rise up the rocks to the trophy (our own model) on its stone, and Whitebrook Road Sports with it. No
// footage: where the competition's films have its goals, the clubs of the game have their own moments.
import * as THREE from '../vendor/three.module.js';
import * as K from './kit.js';
import { trophyKit } from '../kickoff/trophy-models.js';
import { camera, aim, picture, chrome, crest3d, diamondLines, neonLine, glow, fade, word, GEO, DIST } from './shots.js';

export const UCL = { glow: '#7a8cff', panel: '#160d55', mid: '#33208f', deep: '#05031a', ink: '#c9d4ff', violet: '#a23cff', blue: '#2e48ff' };
const U = {};

function lightsU(scene, k = 1) {
  scene.add(new THREE.AmbientLight(0x8890ff, 0.35 * k));
  const v = new THREE.PointLight(K.col('#b04bff'), 5 * k, 0, 0); v.position.set(1600, 500, 900); scene.add(v);
  const b = new THREE.PointLight(K.col('#3b5bff'), 5 * k, 0, 0); b.position.set(-1600, 300, 900); scene.add(b);
  const w = new THREE.DirectionalLight(0xffffff, 1.1 * k); w.position.set(0, 2, 3); scene.add(w);
}
const BACKS = new Map();
function backdrop(scene, top, mid, z = -6000, seed = 3) {
  const key = top + '|' + mid + '|' + seed;
  const c = BACKS.get(key) || backCanvas(top, mid, seed);
  BACKS.set(key, c);
  const p = picture(c); p.scale.set(-z * 2.4, -z * 1.35, 1); p.position.z = z; scene.add(p);
  return p;
}
function backCanvas(top, mid, seed) {
  const c = K.canvas(1024, 576), g = c.getContext('2d'), R = K.rng(seed);
  const gr = g.createRadialGradient(512, 330, 20, 512, 300, 700);
  gr.addColorStop(0, mid); gr.addColorStop(1, top);
  g.fillStyle = gr; g.fillRect(0, 0, 1024, 576);
  for (let i = 0; i < 120; i++) { g.globalAlpha = 0.15 + R() * 0.5; g.fillStyle = '#d8dcff'; g.fillRect(R() * 1024, R() * 576, 1.2, 1.2); }
  g.globalAlpha = 1;
  return c;
}

// ---- the pictures: a star-panelled ball, the white star, the trophy's outline, the brand -----------------------------
let BALL = null;
function ballTex() {
  if (BALL) return BALL;
  const w = 1024, h = 512, c = K.canvas(w, h), g = c.getContext('2d');
  g.fillStyle = '#f2f4fb'; g.fillRect(0, 0, w, h);
  const star = (x, y, r, sx, rot, fill) => {
    g.beginPath();
    for (let i = 0; i < 10; i++) {
      const a = rot + (i * Math.PI) / 5, rr = i % 2 ? r * 0.44 : r;
      g.lineTo(x + Math.cos(a) * rr * sx, y + Math.sin(a) * rr);
    }
    g.closePath(); g.fillStyle = fill; g.fill();
  };
  const band = (lat, n, r, off) => {
    const y = h / 2 - (lat / 90) * (h / 2), sx = 1 / Math.max(0.25, Math.cos((lat * Math.PI) / 180));
    for (let i = 0; i < n; i++) {
      const x = ((i + off) / n) * w;
      for (const dx of [0, w, -w]) {
        star(x + dx, y, r, sx, -Math.PI / 2, '#16207a');
        star(x + dx, y, r * 0.62, sx, -Math.PI / 2, '#3550e6');
        star(x + dx, y, r * 0.3, sx, -Math.PI / 2, '#8aa0ff');
      }
    }
  };
  band(0, 8, 58, 0); band(48, 6, 46, 0.5); band(-48, 6, 46, 0.25);
  g.strokeStyle = 'rgba(30,40,110,0.25)'; g.lineWidth = 2;
  for (let i = 0; i < 16; i++) { g.beginPath(); g.moveTo((i / 16) * w, 0); g.lineTo((i / 16) * w + 30, h); g.stroke(); }
  BALL = K.tex(c);
  BALL.wrapS = THREE.RepeatWrapping;
  return BALL;
}
function starCanvas(n = 512) {
  const c = K.canvas(n, n), g = c.getContext('2d'), cx = n / 2, cy = n / 2 + n * 0.03, r = n * 0.36;
  const pts = [];
  for (let i = 0; i < 10; i++) { const a = -Math.PI / 2 + (i * Math.PI) / 5, rr = i % 2 ? r * 0.43 : r; pts.push([cx + Math.cos(a) * rr, cy + Math.sin(a) * rr]); }
  g.save(); g.shadowColor = 'rgba(170,190,255,1)'; g.shadowBlur = n * 0.09;
  g.beginPath(); pts.forEach(([x, y], i) => g[i ? 'lineTo' : 'moveTo'](x, y)); g.closePath(); g.fillStyle = '#ffffff'; g.fill(); g.restore();
  // its facets: each point's two halves a little apart in light
  for (let i = 0; i < 10; i++) {
    const a = pts[i], b = pts[(i + 1) % 10];
    g.beginPath(); g.moveTo(cx, cy); g.lineTo(a[0], a[1]); g.lineTo(b[0], b[1]); g.closePath();
    g.fillStyle = i % 2 ? 'rgba(205,214,245,0.9)' : 'rgba(255,255,255,1)'; g.fill();
  }
  return c;
}
function trophyIcon(n = 256) {
  const c = K.canvas(n, n * 1.3), g = c.getContext('2d'), s = n / 100;
  g.save(); g.shadowColor = 'rgba(200,210,255,1)'; g.shadowBlur = 16 * s; g.fillStyle = '#ffffff'; g.strokeStyle = '#ffffff';
  // the cup, its stem and foot
  g.beginPath();
  g.moveTo(30 * s, 18 * s); g.lineTo(70 * s, 18 * s);
  g.bezierCurveTo(70 * s, 52 * s, 62 * s, 70 * s, 54 * s, 76 * s);
  g.lineTo(54 * s, 96 * s); g.lineTo(64 * s, 104 * s); g.lineTo(64 * s, 118 * s); g.lineTo(36 * s, 118 * s); g.lineTo(36 * s, 104 * s);
  g.lineTo(46 * s, 96 * s); g.lineTo(46 * s, 76 * s);
  g.bezierCurveTo(38 * s, 70 * s, 30 * s, 52 * s, 30 * s, 18 * s); g.closePath(); g.fill();
  // the big handles
  g.lineWidth = 7 * s;
  for (const sd of [-1, 1]) {
    g.beginPath();
    const x0 = 50 + sd * 18;
    g.moveTo(x0 * s, 22 * s);
    g.bezierCurveTo((50 + sd * 46) * s, 4 * s, (50 + sd * 50) * s, 46 * s, (50 + sd * 16) * s, 62 * s);
    g.stroke();
  }
  g.restore();
  return c;
}
/** The Whitebrook Road Sports wordmark as it is on its ending: the house mark, the name, the red pill. */
export function brandLockup(k = 1) {
  const size = 100 * k, font = `700 ${Math.round(92 * k)}px Outfit, sans-serif`;
  const m = K.canvas(4, 4).getContext('2d'); m.font = font;
  const nameW = m.measureText('whitebrook road').width, pillW = m.measureText('sports').width + 48 * k;
  const P = 40 * k, gap = 30 * k, Wd = P * 2 + size + gap + nameW + gap + pillW, Ht = P * 2 + size * 1.25;
  const c = K.canvas(Wd, Ht), g = c.getContext('2d'), cy = Ht / 2;
  // the house mark
  const x0 = P, y0 = cy - size / 2, rr = size * 0.22;
  const rrect = (x, y, w, h, r) => { g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r); g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath(); };
  g.save(); g.shadowColor = 'rgba(160,80,255,0.9)'; g.shadowBlur = 30 * k;
  const ig = g.createLinearGradient(x0, y0, x0 + size, y0 + size); ig.addColorStop(0, '#8b3dff'); ig.addColorStop(1, '#ff5d8f');
  rrect(x0, y0, size, size, rr); g.fillStyle = ig; g.fill(); g.restore();
  const hs = size / 100;
  g.beginPath(); g.moveTo(x0 + 50 * hs, y0 + 20 * hs); g.lineTo(x0 + 80 * hs, y0 + 45 * hs); g.lineTo(x0 + 80 * hs, y0 + 80 * hs);
  g.lineTo(x0 + 20 * hs, y0 + 80 * hs); g.lineTo(x0 + 20 * hs, y0 + 45 * hs); g.closePath(); g.fillStyle = '#ffffff'; g.fill();
  g.beginPath(); g.moveTo(x0 + 43 * hs, y0 + 50 * hs); g.lineTo(x0 + 43 * hs, y0 + 72 * hs); g.lineTo(x0 + 62 * hs, y0 + 61 * hs); g.closePath();
  g.fillStyle = '#b44bff'; g.fill();
  // the name
  g.font = font; g.textBaseline = 'middle'; g.fillStyle = '#ffffff';
  g.save(); g.shadowColor = 'rgba(150,90,255,0.8)'; g.shadowBlur = 24 * k; g.fillText('whitebrook road', x0 + size + gap, cy + 4 * k); g.restore();
  g.fillText('whitebrook road', x0 + size + gap, cy + 4 * k);
  // the red pill
  const px = x0 + size + gap + nameW + gap, ph = size * 0.98;
  g.save(); g.shadowColor = 'rgba(255,40,60,0.7)'; g.shadowBlur = 24 * k;
  rrect(px, cy - ph / 2, pillW, ph, 16 * k); g.fillStyle = '#d8101f'; g.fill(); g.restore();
  g.fillStyle = '#ffffff'; g.fillText('sports', px + 24 * k, cy + 2 * k);
  return c;
}

function ballMesh(env) {
  return new THREE.Mesh(U.sphere || (U.sphere = new THREE.SphereGeometry(1, 48, 32)),
    new THREE.MeshStandardMaterial({ map: ballTex(), roughness: 0.36, metalness: 0.05, envMap: env, envMapIntensity: 0.9 }));
}
function rock(seed, r, color = '#17122e') {
  const geo = new THREE.IcosahedronGeometry(r, 1), R = K.rng(seed), p = geo.attributes.position, v = new THREE.Vector3();
  const seen = new Map();
  for (let i = 0; i < p.count; i++) {
    v.fromBufferAttribute(p, i);
    const key = v.x.toFixed(2) + ',' + v.y.toFixed(2) + ',' + v.z.toFixed(2);
    if (!seen.has(key)) seen.set(key, 0.72 + R() * 0.5);
    v.multiplyScalar(seen.get(key)); p.setXYZ(i, v.x, v.y, v.z);
  }
  geo.computeVertexNormals();
  return new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ color: K.col(color), roughness: 0.92, metalness: 0.05, flatShading: true }));
}
function tubeOf(points, r, env, color = '#93a8ff') {
  const curve = new THREE.CatmullRomCurve3(points.map((p) => new THREE.Vector3(...p)), false, 'catmullrom', 0.5);
  return new THREE.Mesh(new THREE.TubeGeometry(curve, 140, r, 16, false),
    new THREE.MeshStandardMaterial({ color: K.col(color), metalness: 1, roughness: 0.16, envMap: env, envMapIntensity: 1.8 }));
}

// ---- the competition's mark among chrome ribbons ----------------------------------------------------------------------
U.open = (env, o) => {
  const scene = new THREE.Scene(), cam = camera();
  scene.background = K.col(UCL.deep);
  scene.fog = new THREE.Fog(K.col(UCL.deep), 3500, 14000);
  lightsU(scene);
  backdrop(scene, '#04021a', '#2a1580', -9000, 4);
  const tubes = [
    [[-2600, -900, -1400], [-900, -300, -500], [-300, 500, -900], [400, 900, -2200], [1700, 600, -3000]],
    [[2400, -1000, -600], [900, -600, 200], [300, 100, 500], [-700, 700, 300], [-2200, 1000, -900]],
    [[-1800, 1400, -2600], [-600, 700, -1500], [700, -200, -1400], [1500, -900, -1900], [2600, -1200, -2800]],
    [[-3000, 200, -3500], [-1200, -500, -2600], [0, -700, -2200], [1400, -200, -2700], [3000, 700, -3600]],
  ].map((pts, i) => { const t = tubeOf(pts, i % 2 ? 34 : 46, env, i % 2 ? '#9fb1ff' : '#7f8fe8'); scene.add(t); return t; });
  const back = glow('#7b4dff', 2600, 0.6); back.position.z = -1200; scene.add(back);
  const st = picture(starCanvas(300)); st.position.set(0, 120, 0); scene.add(st);
  let mark = null;
  if (o.mark) {
    const mk = K.fitted(o.mark, 760); mark = picture(mk); mark.position.set(0, -110, 0); scene.add(mark);
  } else {
    const t = picture(K.lettering('CHAMPIONS', { size: 120, weight: 700, family: 'Barlow7', spacing: 22, fill: '#ffffff' })); t.position.set(0, -110, 0); scene.add(t); mark = t;
  }
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d, a = K.lerp(-0.5, 0.35, K.eInOut(f));
      aim(cam, Math.sin(a) * DIST * 1.1, K.lerp(260, -60, f), Math.cos(a) * DIST * 1.1, 0, 0, 0, K.lerp(0.05, -0.03, f));
      fade(st, K.ss(0.3, 1.0, u)); fade(mark, K.ss(0.6, 1.4, u));
      st.rotation.z = Math.sin(u * 0.8) * 0.05; st.quaternion.copy(cam.quaternion);
      mark.quaternion.copy(cam.quaternion);
      tubes.forEach((t, i) => { t.rotation.z = Math.sin(u * 0.3 + i) * 0.06; });
    },
  };
};

// ---- the white star in its diamond, the balls round it, and the rush into it ------------------------------------
U.star = (env, o) => {
  const scene = new THREE.Scene(), cam = camera();
  scene.background = K.col('#0a0630');
  lightsU(scene, 1.2);
  backdrop(scene, '#07052a', '#4a1fa8', -7000, 9);
  const dia = diamondLines(430, '#b9c6ff', '#b9c6ff', 34); dia.position.z = -60; scene.add(dia);
  const dia2 = diamondLines(470, '#7d6bff', '#7d6bff', 16); dia2.position.z = -90; scene.add(dia2);
  const st = picture(starCanvas(620)); scene.add(st);
  const R = K.rng(21), balls = [];
  const spots = [[-760, -300, 300, 330], [-430, -40, -200, 120], [-260, -210, 200, 70], [430, -60, -300, 110], [620, 120, -700, 80],
    [300, -260, 120, 64], [-560, 260, -900, 70], [760, -330, -300, 70], [140, 300, -1200, 60], [-120, -380, -500, 54], [900, 300, -1400, 70], [-980, 60, -1500, 64]];
  for (const [x, y, z, r] of (o.noBig ? spots.slice(1) : spots)) {
    const b = ballMesh(env); b.scale.setScalar(r); b.position.set(x, y, z); b.userData.spin = (R() - 0.5) * 1.4; b.userData.y0 = y; b.userData.ph = R() * 6;
    b.rotation.set(R() * 3, R() * 3, 0); scene.add(b); balls.push(b);
  }
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d, rush = K.eIn(K.ramp(d - 0.6, d, u));
      dia.userData.grow(K.eOut(K.ramp(0, 0.8, u))); dia2.userData.grow(K.eOut(K.ramp(0.15, 0.9, u)));
      st.scale.set(620 * K.lerp(0.7, 1, K.eOutBack(K.ramp(0.1, 0.8, u))), 620 * K.lerp(0.7, 1, K.eOutBack(K.ramp(0.1, 0.8, u))), 1);
      balls.forEach((b) => { b.rotation.y = u * b.userData.spin; b.position.y = b.userData.y0 + Math.sin(u * 0.9 + b.userData.ph) * 18; });
      const z = DIST * K.lerp(1.08, 0.98, K.eOut(f)) - rush * DIST * 0.93;
      aim(cam, K.lerp(80, -40, f) * (1 - rush), K.lerp(-30, 20, f) * (1 - rush), z, 0, 0, -400, K.lerp(0.03, -0.02, f));
    },
  };
};

// ---- a hall of pointed arches, the trophy (or a club's crest) at its end ---------------------------------------------
function arch(w, h, t) {
  const s = new THREE.Shape(), a = w / 2, sp = h * 0.42;
  s.moveTo(-a, 0); s.lineTo(-a, sp); s.quadraticCurveTo(-a, h * 0.9, 0, h); s.quadraticCurveTo(a, h * 0.9, a, sp); s.lineTo(a, 0);
  s.lineTo(a - t, 0); s.lineTo(a - t, sp); s.quadraticCurveTo(a - t, h * 0.9 - t * 1.1, 0, h - t * 1.7); s.quadraticCurveTo(-a + t, h * 0.9 - t * 1.1, -a + t, sp);
  s.lineTo(-a + t, 0); s.closePath();
  return new THREE.ExtrudeGeometry(s, { depth: 34, bevelEnabled: false, curveSegments: 18 });
}
U.arches = (env, o) => {
  const scene = new THREE.Scene(), cam = camera();
  scene.background = K.col('#03021a');
  scene.fog = new THREE.Fog(K.col('#03021a'), 1200, 8200);
  lightsU(scene, 0.8);
  const geo = arch(2300, 2600, 70);
  const mat = new THREE.MeshStandardMaterial({ color: K.col('#1b2a86'), metalness: 0.55, roughness: 0.34, envMap: env, emissive: K.col('#0b0f3a') });
  for (let i = 0; i < 26; i++) {
    const m = new THREE.Mesh(geo, mat); m.position.set(0, -1500, -i * 320); scene.add(m);
  }
  // the purple sails at the side
  const sail = (x, y, z, ry, rz, w, h, c) => {
    const cv = K.canvas(256, 256), g = cv.getContext('2d'), gr = g.createLinearGradient(0, 0, 256, 256);
    gr.addColorStop(0, c); gr.addColorStop(1, '#2a0a5e'); g.fillStyle = gr; g.fillRect(0, 0, 256, 256);
    const p = picture(cv); p.scale.set(w, h, 1); p.position.set(x, y, z); p.rotation.set(0, ry, rz); scene.add(p); return p;
  };
  sail(980, -260, 300, -0.9, 0.5, 900, 1400, '#9b3dff'); sail(-1020, 380, 100, 0.8, -0.4, 700, 1100, '#2f3fff');
  const end = new THREE.Group(); end.position.set(0, -60, -3000); scene.add(end);
  end.add(glow('#9aa8ff', 1300, 0.55));
  let item;
  if (o.crest) { item = crest3d(o.crest, 10, 4); item.scale.setScalar(0.62 * (o.crestScale || 1)); }
  else { item = picture(trophyIcon(220)); }
  end.add(item);
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      aim(cam, Math.sin(u * 0.7) * 40, K.lerp(40, -20, f), K.lerp(1800, 600, K.eOut(f)), 0, -60, -3000, K.lerp(-0.06, 0.04, f));
      if (item.userData.face) { item.rotation.y = Math.sin(u * 1.2) * 0.3; item.userData.face.userData.sheen.sweep.value = K.lerp(-0.5, 1.5, K.ramp(0.3, 1.6, u)); }
      else fade(item, K.ss(0.1, 0.6, u));
    },
  };
};

// ---- drapes in the competition's blues, a club's crest before them ---------------------------------------------------
function drapes(scene) {
  const geo = new THREE.PlaneGeometry(6400, 3800, 240, 1), p = geo.attributes.position;
  const x0 = new Float32Array(p.count);
  for (let i = 0; i < p.count; i++) x0[i] = p.getX(i);
  const mat = new THREE.MeshStandardMaterial({ color: K.col('#2a3bc4'), roughness: 0.5, metalness: 0.2 });
  const m = new THREE.Mesh(geo, mat); m.position.z = -900; scene.add(m);
  m.userData.wave = (u) => {
    for (let i = 0; i < p.count; i++) {
      const x = x0[i];
      p.setZ(i, 90 * Math.sin(x * 0.0105 + u * 0.7) + 40 * Math.sin(x * 0.027 - u * 1.1) + 14 * Math.sin(x * 0.061 + u * 0.4));
    }
    p.needsUpdate = true; geo.computeVertexNormals();
  };
  return m;
}
U.drapes = (env, o) => {
  const scene = new THREE.Scene(), cam = camera();
  scene.background = K.col('#05041c');
  scene.add(new THREE.AmbientLight(0x5a60ff, 0.25));
  const v = new THREE.PointLight(K.col('#b54dff'), 9, 0, 0); v.position.set(1400, 200, 300); scene.add(v);
  const b = new THREE.PointLight(K.col('#3f63ff'), 8, 0, 0); b.position.set(-1300, -100, 200); scene.add(b);
  const top = new THREE.SpotLight(0xffffff, 6, 0, 0.5, 0.6, 0); top.position.set(0, 1600, 800); top.target.position.set(0, 0, -900); scene.add(top); scene.add(top.target);
  const dr = drapes(scene);
  const T = o.team;
  const gl = glow(T && T.lk ? T.lk.glow : '#8f7bff', 1500, 0.55); gl.position.z = -200; scene.add(gl);
  let cr = null;
  if (T && T.chrome) { cr = crest3d(T.chrome, 12, 4); cr.scale.setScalar(0.92 * (T.crestScale || 1)); cr.position.y = 40; scene.add(cr); }
  const nm = T ? picture(K.lettering(T.name, { size: 46, weight: 700, family: 'Barlow7', spacing: 24, fill: '#ffffff' })) : null;
  if (nm) { nm.position.set(0, -390, 40); scene.add(nm); }
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      dr.userData.wave(u + (o.seed || 0));
      if (cr) {
        const c = K.eOutExpo(K.ramp(0, 0.7, u));
        cr.scale.setScalar(0.92 * (T.crestScale || 1) * K.lerp(0.75, 1, c));
        cr.rotation.y = K.lerp(1.1, 0, c) + Math.sin(u * 1.3) * 0.06;
        cr.userData.face.userData.sheen.sweep.value = K.lerp(-0.5, 1.5, K.ramp(0.4, 1.6, u));
      }
      if (nm) fade(nm, K.ss(0.4, 0.8, u));
      aim(cam, K.lerp(-120, 80, f), 0, DIST * K.lerp(1.05, 0.96, K.eOut(f)), 0, 0, 0, 0);
    },
  };
};

// ---- a club's star before a great purple disc, his name ---------------------------------------------------------------
function discCanvas(seed) {
  const n = 1024, c = K.canvas(n, n), g = c.getContext('2d'), R = K.rng(seed);
  const gr = g.createRadialGradient(n / 2, n / 2, n * 0.05, n / 2, n / 2, n / 2);
  gr.addColorStop(0, '#d455ff'); gr.addColorStop(0.55, '#8b2cf0'); gr.addColorStop(0.92, '#4b1bb0'); gr.addColorStop(1, 'rgba(60,20,160,0)');
  g.fillStyle = gr; g.beginPath(); g.arc(n / 2, n / 2, n / 2, 0, Math.PI * 2); g.fill();
  // the stadium's lights through it
  g.globalCompositeOperation = 'lighter';
  for (let i = 0; i < 9; i++) {
    const a = -Math.PI / 2 + (R() - 0.5) * 1.6, l = n * (0.35 + R() * 0.2);
    g.strokeStyle = `rgba(190,170,255,${0.08 + R() * 0.12})`; g.lineWidth = 2 + R() * 6;
    g.beginPath(); g.moveTo(n / 2, n * 0.62); g.lineTo(n / 2 + Math.cos(a) * l, n * 0.62 + Math.sin(a) * l); g.stroke();
  }
  for (let i = 0; i < 160; i++) { g.fillStyle = `rgba(230,220,255,${R() * 0.5})`; g.fillRect(R() * n, n * 0.55 + R() * n * 0.12, 3, 2); }
  return c;
}
U.player = (env, o) => {
  const scene = new THREE.Scene(), cam = camera(), P = o.player, T = o.team;
  scene.background = K.col('#070524');
  backdrop(scene, '#05031c', '#1b1170', -5000, 13);
  const disc = picture(discCanvas(o.seed || 5)); disc.scale.set(1250, 1250, 1); disc.position.set(260, 30, -400); scene.add(disc);
  const ring1 = new THREE.Mesh(new THREE.RingGeometry(700, 708, 128), new THREE.MeshBasicMaterial({ color: K.col('#b7a2ff'), transparent: true, opacity: 0.6, toneMapped: false }));
  ring1.position.set(260, 30, -420); scene.add(ring1);
  const ring2 = new THREE.Mesh(new THREE.RingGeometry(800, 804, 128), new THREE.MeshBasicMaterial({ color: K.col('#7f8cff'), transparent: true, opacity: 0.35, toneMapped: false }));
  ring2.position.set(260, 30, -460); scene.add(ring2);
  const pl = new THREE.Group(); scene.add(pl);
  if (P && P.lit) {
    const tall = P.lit.face ? 760 : 1180, ph = picture(P.lit), k = tall / P.lit.h; ph.scale.multiplyScalar(k);
    ph.position.set(P.lit.face ? -40 : 0, P.lit.face ? 30 : -540 + tall / 2 - 40, 0); pl.add(ph);
  }
  pl.position.x = 300;
  const txt = new THREE.Group(); txt.position.set(-820, -70, 40); scene.add(txt);
  const last = word(P ? P.last : T.name, { size: 250, sy: 1.08, fill: K.WHITE_FADE, glow: { blur: 18, c: 'rgba(20,0,60,0.6)', a: 1 } });
  const kk = Math.min(1, 820 / Math.max(1, last.userData.w)); last.scale.setScalar(kk); txt.add(last);
  const first = P && P.first ? picture(K.lettering(P.first, { size: 64, weight: 700, family: 'Barlow7', spacing: 3, fill: '#c9b8ff' })) : null;
  if (first) { first.position.set(first.userData.w / 2 - 4, 250 * 1.08 * 0.72 * kk + 48, 0); txt.add(first); }
  const nmT = P ? `${P.num || ''}${P.num && P.pos ? '  ' : ''}${P.pos || ''}`.trim() : '';
  const nm = nmT ? picture(K.lettering(nmT, { size: 66, weight: 800, fill: '#9fb4ff' })) : null;
  if (nm) { nm.position.set(nm.userData.w / 2 + 6, -62, 0); txt.add(nm); }
  let mini = null;
  if (T && T.plain) { mini = picture(T.plain); mini.scale.multiplyScalar(110 / mini.userData.h); mini.position.set(-760, 330, 30); scene.add(mini); }
  const order = last.userData.letters.map((_, i) => i);
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      disc.scale.setScalar(1250 * K.lerp(0.85, 1, K.eOut(K.ramp(0, 0.8, u)))); disc.scale.z = 1;
      ring1.rotation.z = u * 0.2; ring2.rotation.z = -u * 0.15;
      pl.position.x = 300 + (1 - K.eOutExpo(K.ramp(0, 0.6, u))) * 300;
      fade(pl, K.ramp(0, 0.2, u));
      last.userData.letters.forEach((L, i) => {
        const t0 = 0.25 + order[i] * 0.05, a = K.ramp(t0, t0 + 0.1, u);
        fade(L, a); L.position.y = (1 - K.eOut(a)) * -40 + (L.userData.y0 == null ? (L.userData.y0 = L.position.y) : L.userData.y0);
      });
      if (first) fade(first, K.ss(0.2, 0.5, u));
      if (nm) fade(nm, K.ss(0.7, 1.0, u));
      if (mini) fade(mini, K.ss(0.3, 0.7, u));
      aim(cam, K.lerp(50, -30, f), 0, DIST * K.lerp(1.04, 0.96, f), 0, 0, 0, 0);
    },
  };
};

// ---- the two clubs either side of the star ----------------------------------------------------------------------------
U.versus = (env, o) => {
  const s = U.star(env, { noBig: true });
  const { home, away } = o;
  const crs = [home, away].map((T, i) => {
    const sd = i ? 1 : -1;
    if (!T.chrome) return null;
    const cr = crest3d(T.chrome, 12, 4); cr.scale.setScalar(0.8 * (T.crestScale || 1)); cr.position.set(sd * 600, 0, 140);
    const gl = glow(T.lk.glow, 1200, 0.5); gl.position.set(sd * 640, 0, 0);
    s.scene.add(gl); s.scene.add(cr);
    return { cr, sd };
  }).filter(Boolean);
  const vs = picture(K.lettering('v', { size: 120, weight: 700, family: 'Barlow7', fill: '#0b0a2a' }));
  vs.position.set(0, -30, 30); s.scene.add(vs);
  const up = s.update;
  return {
    scene: s.scene, camera: s.camera,
    update(u, d) {
      up(u, d + 0.6);   // no rush into the star here
      crs.forEach(({ cr, sd }) => {
        const c = K.eOutExpo(K.ramp(0.1, 0.8, u));
        cr.position.x = sd * K.lerp(1500, 600, c);
        cr.rotation.y = -sd * K.lerp(1.2, 0.25, c);
        cr.userData.face.userData.sheen.sweep.value = K.lerp(-0.5, 1.5, K.ramp(0.8, 2, u));
      });
      fade(vs, K.ss(0.7, 1.0, u));
    },
  };
};

// ---- up through the dark rocks -----------------------------------------------------------------------------------------
U.cave = (env, o) => {
  const scene = new THREE.Scene(), cam = camera();
  scene.background = K.col('#04031a');
  scene.fog = new THREE.Fog(K.col('#04031a'), 600, 5200);
  scene.add(new THREE.AmbientLight(0x7a6cff, 0.75));
  const v = new THREE.PointLight(K.col('#b05bff'), 14, 0, 0); v.position.set(700, 2600, -500); scene.add(v);
  const b = new THREE.PointLight(K.col('#4c6bff'), 12, 0, 0); b.position.set(-900, 900, 200); scene.add(b);
  const top = new THREE.PointLight(K.col('#e0d4ff'), 10, 0, 0); top.position.set(0, 4200, -1400); scene.add(top);
  const R = K.rng(31);
  for (let i = 0; i < 26; i++) {
    const r = rock(100 + i, 220 + R() * 460, i % 3 ? '#2a2350' : '#3a2f6e'), a = R() * Math.PI * 2, d = 650 + R() * 900;
    r.position.set(Math.cos(a) * d, -700 + (i / 26) * 4000 + R() * 300, -700 + Math.sin(a) * d * 0.5); r.rotation.set(R() * 3, R() * 3, R() * 3); scene.add(r);
  }
  const shaft = picture(K.streak(1024, 128), { add: true, color: '#9c7bff', opacity: 0.5 }); shaft.scale.set(5200, 900, 1); shaft.rotation.z = Math.PI / 2 - 0.2;
  shaft.position.set(200, 2600, -1600); shaft.userData.a = 0.5; scene.add(shaft);
  return {
    scene, camera: cam,
    update(u, d) {
      const f = K.eInOut(u / d);
      aim(cam, K.lerp(-200, 120, f), K.lerp(-500, 2300, f), 1500, 0, K.lerp(600, 3400, f), -1400, K.lerp(0.12, -0.05, f));
      fade(shaft, 0.3 + 0.4 * K.ramp(0.3, 1, u / d));
    },
  };
};

// ---- the trophy on its stone, the balls at its foot, Whitebrook Road Sports and the game ----------------------------
let KIT = null;
U.trophy = (env, o) => {
  const scene = new THREE.Scene(), cam = camera();
  scene.background = K.col('#060421');
  scene.environment = env;
  scene.fog = new THREE.Fog(K.col('#060421'), 3000, 11000);
  scene.add(new THREE.AmbientLight(0x7a70ff, 0.35));
  const key = new THREE.DirectionalLight(0xffffff, 2.2); key.position.set(-2, 3, 3); scene.add(key);
  const rim = new THREE.PointLight(K.col('#b25bff'), 8, 0, 0); rim.position.set(900, 600, -500); scene.add(rim);
  const rim2 = new THREE.PointLight(K.col('#4e6bff'), 7, 0, 0); rim2.position.set(-900, 400, -400); scene.add(rim2);
  backdrop(scene, '#05031a', '#2a1488', -7000, 17);
  // the room: pillars, the floor
  const pm = new THREE.MeshStandardMaterial({ color: K.col('#1d2a8a'), metalness: 0.4, roughness: 0.4, envMap: env, emissive: K.col('#0a0d35') });
  for (const [x, z, w] of [[-1600, -1600, 300], [-950, -2000, 220], [950, -2000, 220], [1600, -1600, 300], [-2400, -1200, 380], [2400, -1200, 380]]) {
    const p = new THREE.Mesh(new THREE.BoxGeometry(w, 4200, w), pm); p.position.set(x, 900, z); scene.add(p);
  }
  const floor = new THREE.Mesh(new THREE.PlaneGeometry(20000, 20000), new THREE.MeshStandardMaterial({ color: K.col('#0a0828'), metalness: 0.7, roughness: 0.35, envMap: env }));
  floor.rotation.x = -Math.PI / 2; floor.position.y = -620; scene.add(floor);
  // the neon circle behind
  const ring = new THREE.Mesh(new THREE.TorusGeometry(820, 5, 8, 160, Math.PI * 1.25), new THREE.MeshBasicMaterial({ color: K.col('#c9a2ff'), toneMapped: false }));
  ring.position.set(0, 120, -700); ring.rotation.z = Math.PI * 0.6; scene.add(ring);
  // the stone and the trophy
  const stone = rock(7, 420, '#1c1838'); stone.scale.set(1.2, 0.55, 0.9); stone.position.set(0, -470, -100); scene.add(stone);
  const holder = new THREE.Group(); holder.position.set(0, -330, -100); scene.add(holder);
  try {
    KIT = KIT || trophyKit();
    const T = KIT.BUILD.ucl();
    const box = new THREE.Box3().setFromObject(T), h = box.max.y - box.min.y || 1;
    const s = 640 / h; T.scale.setScalar(s); T.position.y = -box.min.y * s;
    holder.add(T);
  } catch (e) {
    const ic = picture(trophyIcon(300)); ic.position.y = 330; holder.add(ic);
  }
  for (const [x, z, r] of [[-640, -40, 70], [600, 0, 64], [-860, 120, 52], [820, 80, 56]]) {
    const bl = ballMesh(env); bl.scale.setScalar(r); bl.position.set(x, -620 + r, z); bl.rotation.set(1, x, 0.4); scene.add(bl);
  }
  const shafts = [];
  for (let i = 0; i < 3; i++) {
    const s = picture(K.streak(1024, 128), { add: true, color: '#a98bff', opacity: 0.25 }); s.scale.set(4200, 700, 1);
    s.rotation.z = Math.PI / 2 + (i - 1) * 0.25; s.position.set((i - 1) * 500, 1500, -1800); s.userData.a = 0.25; scene.add(s); shafts.push(s);
  }
  // Whitebrook Road Sports, and the game under it
  const brand = picture(brandLockup(0.95)); brand.position.set(0, -335, 420); scene.add(brand);
  const fx = o.fixture ? picture(K.lettering(o.fixture, { size: 84, sy: 1.05, fill: K.WHITE_FADE, glow: { blur: 18, c: 'rgba(120,80,255,0.9)', a: 1 } })) : null;
  if (fx) { fx.scale.multiplyScalar(Math.min(1, 1300 / fx.userData.w)); fx.position.set(0, -442, 420); scene.add(fx); }
  const l2 = o.line2 ? picture(K.lettering(o.line2, { size: 30, weight: 700, family: 'Barlow7', spacing: 8, fill: '#c9b8ff' })) : null;
  if (l2) { l2.position.set(0, -506, 420); scene.add(l2); }
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      holder.rotation.y = K.lerp(-0.9, 0.5, K.eOut(f));
      ring.rotation.z = Math.PI * 0.6 + u * 0.18;
      shafts.forEach((s, i) => fade(s, 0.6 + 0.4 * Math.sin(u * 1.1 + i)));
      const b = K.ramp(3.0, 3.8, u);
      fade(brand, K.ss(0, 1, b)); brand.position.y = -335 - (1 - K.eOut(b)) * 40;
      if (fx) fade(fx, K.ss(4.0, 4.7, u));
      if (l2) fade(l2, K.ss(4.4, 5.0, u));
      // up from the floor to the trophy, then back to take in the room and the words
      const c = K.eInOut(K.ramp(0, 3.2, u)), back = K.eInOut(K.ramp(2.6, 4.6, u));
      aim(cam, K.lerp(-300, 0, c), K.lerp(-380, 60, c) - back * 60, K.lerp(1300, 1700, c) + back * 520,
        0, K.lerp(-200, 40, c) - back * 150, -100, K.lerp(0.05, 0, c));
    },
  };
};

export { U as UCL_SHOTS };
