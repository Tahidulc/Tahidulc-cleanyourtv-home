// The match intro's shots, each its own small 3D scene drawn for any moment u (seconds into the shot): the open
// (a football comet past a tower, a rush down a tunnel, the Whitebrook Road lockup), each club's city, its star's name
// card, its crest in a neon diamond, a neon tunnel, its chant; the other clubs' tunnels and crests; players face to
// face; the two clubs face to face; the end. Measures are screen pixels at z 0 (the camera at DIST looks at 1:1).
import * as THREE from '../vendor/three.module.js';
import * as K from './kit.js';

export const W = 1920, H = 1080, FOV = 30;
export const DIST = (H / 2) / Math.tan(THREE.MathUtils.degToRad(FOV / 2));
const GEO = new THREE.PlaneGeometry(1, 1);
const BOX = new THREE.BoxGeometry(1, 1, 1);

export function camera() { return new THREE.PerspectiveCamera(FOV, W / H, 20, 80000); }
export function aim(cam, x, y, z, tx, ty, tz, roll = 0) {
  cam.position.set(x, y, z);
  cam.up.set(Math.sin(roll), Math.cos(roll), 0);
  cam.lookAt(tx, ty, tz);
}

// ---- pieces ------------------------------------------------------------------------------------------------------------
const texOf = new WeakMap();
function textureFor(c) {
  if (!texOf.has(c)) texOf.set(c, K.tex(c));
  return texOf.get(c);
}
/** A flat picture (a canvas, or lettering's result) as a plane its own size. */
export function picture(src, o = {}) {
  const c = src.canvas || src;
  const m = new THREE.MeshBasicMaterial({
    map: o.texture || textureFor(c), transparent: true, depthWrite: false, toneMapped: false, fog: false,
    blending: o.add ? THREE.AdditiveBlending : THREE.NormalBlending, color: o.color ? K.col(o.color) : 0xffffff,
    opacity: o.opacity == null ? 1 : o.opacity,
  });
  const mesh = new THREE.Mesh(GEO, m);
  const s = o.scale || 1;
  mesh.scale.set(c.width * s, c.height * s, 1);
  mesh.userData.w = c.width * s; mesh.userData.h = c.height * s;
  return mesh;
}
/** A picture with a moving sheen (polished metal): set its .sweep (-0.5 to 1.5 crosses it). */
export function chrome(src, o = {}) {
  const c = src.canvas || src;
  const m = new THREE.ShaderMaterial({
    uniforms: { map: { value: o.texture || textureFor(c) }, sweep: { value: -1 }, gain: { value: o.gain || 0.85 }, opacity: { value: 1 } },
    vertexShader: 'varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }',
    fragmentShader: `uniform sampler2D map; uniform float sweep, gain, opacity; varying vec2 vUv;
      void main() {
        vec4 c = texture2D(map, vUv);
        float d = vUv.x * 0.78 + (1.0 - vUv.y) * 0.32 - sweep;
        float h = exp(-d * d / 0.003) * gain + exp(-d * d / 0.04) * gain * 0.12;
        gl_FragColor = vec4(c.rgb + h * c.a, c.a * opacity);
        #include <colorspace_fragment>
      }`,
    transparent: true, depthWrite: false,
  });
  const mesh = new THREE.Mesh(GEO, m);
  const s = o.scale || 1;
  mesh.scale.set(c.width * s, c.height * s, 1);
  mesh.userData.w = c.width * s; mesh.userData.h = c.height * s;
  mesh.userData.sheen = m.uniforms;
  return mesh;
}
const fade = (mesh, a) => {
  mesh.traverse((o) => {
    if (!o.material) return;
    if (o.material.uniforms && o.material.uniforms.opacity) o.material.uniforms.opacity.value = a * (o.userData.a == null ? 1 : o.userData.a);
    else o.material.opacity = a * (o.userData.a == null ? 1 : o.userData.a);
    o.visible = a > 0.002;
  });
};
/** A crest in polished metal with depth: its side stacked behind its face. */
function crest3d(cc, layers = 10, step = 3) {
  const g = new THREE.Group(), side = textureFor(cc.side);
  for (let i = layers; i >= 1; i--) {
    const p = picture(cc.side, { texture: side });
    p.position.z = -i * step;
    g.add(p);
  }
  const face = chrome(cc.canvas, { gain: 0.5 });
  g.add(face);
  g.userData.face = face; g.userData.w = face.userData.w; g.userData.h = face.userData.h;
  return g;
}
/** A word with a plane per letter (each can come on by itself); left-aligned at x 0, baseline at y 0. */
function word(str, o) {
  const g = new THREE.Group(), m = K.canvas(4, 4).getContext('2d');
  const size = o.size || 100;
  m.font = `${o.weight || 800} ${size}px ${o.family || 'BarlowC'}, 'Barlow Condensed', sans-serif`;
  let x = 0;
  g.userData.letters = [];
  for (const ch of [...String(str)]) {
    const adv = m.measureText(ch).width;
    if (ch.trim()) {
      const L = K.lettering(ch, o);
      // the letter's origin in its canvas: lettering puts it at (pad + its lean's room, pad + ascent)
      const k = Math.tan(o.skew || 0), sy = o.sy || 1;
      const mm = m.measureText(ch), desc = Math.max(0, mm.actualBoundingBoxDescent || 0);
      const ox = L.pad + (k > 0 ? k * desc * sy : 0), oy = L.pad + L.ascent;
      const p = picture(L);
      p.position.set(x - ox + L.w / 2, oy - L.h / 2, 0);
      g.add(p); g.userData.letters.push(p);
    }
    x += adv + (o.spacing || 0);
  }
  g.userData.w = x - (o.spacing || 0);
  return g;
}
/** A neon line from a to b (screen pixels), its glow `th` wide: drawn on by its .grow (0 to 1) from a. */
function neonLine(ax, ay, bx, by, color, th = 46, core = true) {
  const g = new THREE.Group(), len = Math.hypot(bx - ax, by - ay);
  g.position.set(ax, ay, 0);
  g.rotation.z = Math.atan2(by - ay, bx - ax);
  const bar = new THREE.Group(); g.add(bar);
  const glowT = BAR || (BAR = K.tex(K.barGlow(64), false));
  const mk = (h, c, a) => {
    const m = new THREE.Mesh(GEO, new THREE.MeshBasicMaterial({ map: glowT, color: K.col(c), transparent: true, depthWrite: false,
      blending: THREE.AdditiveBlending, toneMapped: false, fog: false, opacity: a }));
    m.scale.set(len, h, 1); m.position.x = len / 2; m.userData.a = a;
    bar.add(m);
  };
  mk(th, color, 1);
  if (core) mk(th * 0.22, '#ffffff', 0.9);
  g.userData.grow = (f) => { bar.scale.x = Math.max(0.0001, f); };
  g.userData.grow(1);
  return g;
}
let BAR = null;
function diamondLines(D, colorL, colorR, th) {
  const g = new THREE.Group();
  const top = [0, D], right = [D, 0], bot = [0, -D], left = [-D, 0];
  const ls = [neonLine(...top, ...left, colorL, th), neonLine(...bot, ...left, colorL, th), neonLine(...top, ...right, colorR, th), neonLine(...bot, ...right, colorR, th)];
  for (const l of ls) g.add(l);
  g.userData.grow = (f) => ls.forEach((l) => l.userData.grow(f));
  return g;
}
function glow(color, size, a = 1) {
  const p = picture(DOT || (DOT = K.glowDot(256)), { add: true, color, opacity: a });
  p.scale.set(size, size, 1); p.userData.a = a;
  return p;
}
let DOT = null;
/** A building's material: its face and lights (shared by every shot with the same club, kind and light: a TV's memory). */
const MATS = new Map();
function slabMat(lk, seed, kind, env, em = 1.4) {
  const key = [lk.glow, lk.panel, kind, seed % 2, em, env ? env.uuid : ''].join('|');
  if (MATS.has(key)) return MATS.get(key);
  const f = K.facade(lk, seed % 2, kind);
  const m = new THREE.MeshStandardMaterial({
    color: 0xffffff, map: K.tex(f.map), emissive: 0xffffff, emissiveMap: K.tex(f.em), emissiveIntensity: em,
    metalness: 0.65, roughness: 0.3, envMap: env, envMapIntensity: 1.1,
  });
  MATS.set(key, m);
  return m;
}
/** A night sky (shared the same way). */
const SKIES = new Map();
function skyOf(top, low, seed) {
  const key = top + '|' + low + '|' + (seed % 2);
  if (!SKIES.has(key)) SKIES.set(key, K.sky(top, low, seed % 2));
  return SKIES.get(key);
}
function blocks(list, mat) {
  const inst = new THREE.InstancedMesh(BOX, mat, list.length);
  const m4 = new THREE.Matrix4(), q = new THREE.Quaternion(), e = new THREE.Euler(), p = new THREE.Vector3(), s = new THREE.Vector3();
  list.forEach((b, i) => {
    p.set(b.x, b.y, b.z); s.set(b.w, b.h, b.d); e.set(b.rx || 0, b.ry || 0, b.rz || 0); q.setFromEuler(e);
    m4.compose(p, q, s); inst.setMatrixAt(i, m4);
  });
  inst.instanceMatrix.needsUpdate = true;
  return inst;
}
function lights(scene, lk) {
  scene.add(new THREE.AmbientLight(0xffffff, 0.35));
  const key = new THREE.DirectionalLight(0xffffff, 1.4); key.position.set(-1, 2, 3); scene.add(key);
  const c = new THREE.PointLight(K.col(lk.glow), 3.5, 0, 0); c.position.set(0, 400, 1200); scene.add(c);
}
const SHOTS = {};

// ---- the open: a football comet past a tower of the kick-off time ---------------------------------------------------
SHOTS.comet = (env, o) => {
  const lk = { glow: '#38d4ff', panel: '#0a1630', mid: '#17305c', deep: '#02050d', ink: '#bfefff' };
  const scene = new THREE.Scene(), cam = camera();
  scene.background = K.col('#02040b');
  scene.fog = new THREE.Fog(K.col('#030812'), 6000, 30000);
  lights(scene, lk);
  const skyP = picture(skyOf('#01030a', '#0b2452', 7)); skyP.scale.set(52000, 30000, 1); skyP.position.set(0, 4000, -26000); scene.add(skyP);
  // the tower, and the city far off
  const tower = new THREE.Mesh(BOX, slabMat(lk, 3, 0, env, 1.6));
  tower.scale.set(1100, 16000, 1100); tower.position.set(900, 3000, -3200); tower.rotation.y = -0.42; scene.add(tower);
  const face = new THREE.Group(); face.position.copy(tower.position); face.rotation.y = tower.rotation.y; scene.add(face);
  const [hh, mm] = String(o.ko || '20:00').split(':');
  [[hh, 3600], [mm, 900]].forEach(([s, y]) => {
    const L = K.lettering(s, { size: 1000, sy: 1.12, fill: K.CHROME, extrude: { n: 22, dx: 1.4, dy: 2.2 }, stroke: { w: 4, c: '#10141c' } });
    const p = chrome(L); p.position.set(0, y - 3000, 560); face.add(p); p.userData.sheen.sweep.value = 0.4;
  });
  const R = K.rng(11), far = [];
  for (let i = 0; i < 40; i++) {
    const x = (R() - 0.5) * 30000, z = -9000 - R() * 14000, h = 3000 + R() * 12000;
    far.push({ x, y: -4000 + h / 2, z, w: 900 + R() * 1600, h, d: 900 + R() * 1600, ry: R() });
  }
  scene.add(blocks(far, slabMat(lk, 5, 1, env, 1.2)));
  // the comet: the ball, its halo, its tail
  const ball = picture(K.football(256)); ball.scale.set(190, 190, 1);
  const halo = glow('#9fe9ff', 900, 0.9), core = glow('#ffffff', 260, 1);
  const tailC = K.canvas(1024, 96), tg = tailC.getContext('2d');
  const tgr = tg.createLinearGradient(0, 0, 1024, 0);
  tgr.addColorStop(0, 'rgba(120,220,255,0)'); tgr.addColorStop(0.75, 'rgba(160,235,255,0.55)'); tgr.addColorStop(1, 'rgba(255,255,255,1)');
  tg.fillStyle = tgr; tg.beginPath(); tg.moveTo(0, 48); tg.lineTo(1024, 8); tg.lineTo(1024, 88); tg.closePath(); tg.fill();
  const tail = picture(tailC, { add: true }); tail.scale.set(3400, 150, 1);
  const tail2 = picture(K.streak(1024, 128), { add: true, color: '#7fdcff', opacity: 0.6 }); tail2.scale.set(5200, 420, 1);
  for (const p of [tail2, tail, halo, core, ball]) scene.add(p);
  const A = new THREE.Vector3(-3400, 5600, -7000), B = new THREE.Vector3(1300, 300, -600);
  const pos = (f) => A.clone().lerp(B, f);
  const v0 = new THREE.Vector3(), v1 = new THREE.Vector3(), right = new THREE.Vector3(), up = new THREE.Vector3();
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      // the camera: low, looking up the tower, then down along it with the comet
      const c = K.eInOut(f);
      const k = K.ramp(0.08, 0.98, f) * 0.85 + K.eInOut(K.ramp(0.08, 0.98, f)) * 0.15;
      const p = pos(k);
      // looking up the tower, then down it with the comet (the view leans to the ball as it comes)
      const tx = K.lerp(500, 600, c), ty = K.lerp(4600, 1400, c), tz = K.lerp(-3200, -2400, c);
      aim(cam, K.lerp(-700, -150, c), K.lerp(-1300, 500, c), K.lerp(3900, 2900, c),
        K.lerp(tx, p.x, 0.32), K.lerp(ty, p.y, 0.32), K.lerp(tz, p.z, 0.32), K.lerp(0.16, -0.08, c));
      cam.updateMatrixWorld();
      for (const m of [ball, halo, core]) m.position.copy(p);
      for (const m of [ball, halo, core, tail, tail2]) m.quaternion.copy(cam.quaternion);
      ball.rotateZ(-u * 9);
      // the tail along the comet's path as seen
      v0.copy(pos(Math.max(0, k - 0.02))).project(cam); v1.copy(p).project(cam);
      const ang = Math.atan2((v1.y - v0.y) * H, (v1.x - v0.x) * W);
      right.setFromMatrixColumn(cam.matrixWorld, 0); up.setFromMatrixColumn(cam.matrixWorld, 1);
      const dir = right.multiplyScalar(Math.cos(ang)).add(up.multiplyScalar(Math.sin(ang)));
      tail.rotateZ(ang); tail2.rotateZ(ang);
      tail.position.copy(p).addScaledVector(dir, -1700 * 0.98);
      tail2.position.copy(p).addScaledVector(dir, -2000);
      const on = K.ramp(0.06, 0.14, f);
      fade(tail, on); fade(tail2, on * 0.6); fade(halo, on * 0.9); fade(core, on); fade(ball, on);
    },
  };
};

// ---- a tunnel: hexagon rings in a club's colours, its crest at the end in neon -------------------------------------
SHOTS.tunnel = (env, o) => {
  const lk = o.lk, scene = new THREE.Scene(), cam = camera();
  scene.background = K.col(lk.deep);
  scene.fog = new THREE.Fog(K.col(lk.deep), 1500, o.portal ? 14000 : 10500);
  lights(scene, lk);
  const N = 30, S = 400, R0 = 860, n = 6, side = 2 * R0 * Math.tan(Math.PI / n), tw = o.twist == null ? 0.05 : o.twist;
  const ring = [], strips = [];
  const Rr = K.rng(o.seed || 1);
  for (let i = 0; i < N; i++) {
    for (let k = 0; k < n; k++) {
      const a = (k / n) * Math.PI * 2 + i * tw + Math.PI / 6;
      ring.push({ x: Math.cos(a) * R0, y: Math.sin(a) * R0, z: -i * S, w: side * 1.03, h: 120, d: 300, rz: a + Math.PI / 2 });
      if (Rr() < 0.55) strips.push({ x: Math.cos(a) * (R0 - 64), y: Math.sin(a) * (R0 - 64), z: -i * S + 140, w: side * (0.3 + Rr() * 0.6), h: 9, d: 9, rz: a + Math.PI / 2 });
    }
  }
  scene.add(blocks(ring, slabMat(lk, (o.seed || 1) + 4, 2, env, 1.3)));
  scene.add(blocks(strips, new THREE.MeshBasicMaterial({ color: K.col(lk.glow).multiplyScalar(2.2), toneMapped: false })));
  // light trails along the walls, rushing past
  const trails = [];
  for (let i = 0; i < 12; i++) {
    const a = Rr() * Math.PI * 2, r = R0 * (0.7 + Rr() * 0.2);
    const m = new THREE.Mesh(BOX, new THREE.MeshBasicMaterial({ color: K.col(i % 3 ? lk.glow : '#ffffff').multiplyScalar(1.5), toneMapped: false, transparent: true, opacity: 0.85, blending: THREE.AdditiveBlending, depthWrite: false }));
    m.scale.set(7, 7, 1800 + Rr() * 2600); m.position.set(Math.cos(a) * r, Math.sin(a) * r, 0);
    m.userData.z0 = -Rr() * N * S; m.userData.v = 9000 + Rr() * 9000;
    scene.add(m); trails.push(m);
  }
  const endZ = -(N - 5) * S;
  const end = new THREE.Group(); end.position.z = endZ; scene.add(end);
  end.add(glow(lk.glow, 3600, o.portal ? 0.75 : 0.4));
  if (o.portal) end.add(glow('#ffffff', 2200, 1));
  const echoes = [];
  if (o.crest) {
    const face = crest3d(o.crest.chrome, 8, 4); face.scale.setScalar((o.crestScale || 1) * 1.25); end.add(face);
    end.userData.face = face;
    for (let i = 1; i <= 3; i++) {
      const e = picture(o.crest.neon, { add: true });
      e.scale.multiplyScalar((o.crestScale || 1) * 1.25 * (1 + i * 0.5)); e.position.z = i * 380; e.userData.a = 0.9 - i * 0.22;
      end.add(e); echoes.push(e);
    }
    const n0 = picture(o.crest.neon, { add: true }); n0.scale.multiplyScalar((o.crestScale || 1) * 1.25 * 1.02); n0.position.z = 6; end.add(n0); echoes.unshift(n0);
  }
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      const z0 = o.short ? endZ + 4600 : 900, z1 = o.short ? endZ + 2200 : endZ + (o.portal ? 600 : 2900);
      const c = o.portal ? K.eIn(f) * 0.6 + f * 0.4 : K.eOut(f) * 0.75 + f * 0.25;
      const z = K.lerp(z0, z1, c);
      aim(cam, Math.sin(u * 1.3) * 60, Math.cos(u * 1.1) * 40, z, 0, 0, z - 4000, K.lerp(o.roll0 == null ? -0.25 : o.roll0, o.roll1 == null ? 0.18 : o.roll1, K.eInOut(f)));
      for (const m of trails) m.position.z = ((m.userData.z0 + u * m.userData.v) % (N * S)) - N * S * 0.8 + z;
      echoes.forEach((e, i) => {
        const pulse = 0.65 + 0.35 * Math.sin(u * 9 - i * 0.9);
        fade(e, K.ramp(0, 0.25, f) * pulse);
      });
      if (end.userData.face) {
        end.userData.face.rotation.y = Math.sin(u * 1.6) * 0.25;
        end.userData.face.userData.face.userData.sheen.sweep.value = K.lerp(-0.4, 1.4, K.ramp(0.2, 0.9, f));
      }
    },
  };
};

// ---- a club's city: a dive between its buildings, its name up their sides -------------------------------------------
SHOTS.city = (env, o) => {
  const lk = o.lk, scene = new THREE.Scene(), cam = camera();
  scene.background = K.col(lk.deep);
  scene.fog = new THREE.Fog(K.col(lk.deep), 3500, 24000);
  lights(scene, lk);
  const R = K.rng(o.seed || 3), a = [], b = [];
  for (const s of [-1, 1]) {
    for (let i = 0; i < 16; i++) {
      const h = 4000 + R() * 9000, w = 600 + R() * 900, d = 600 + R() * 900;
      (R() < 0.5 ? a : b).push({ x: s * (900 + w / 2 + R() * 900), y: -4500 + h / 2, z: 1500 - i * 1250 - R() * 500, w, h, d });
    }
  }
  for (let i = 0; i < 10; i++) {
    const h = 6000 + R() * 9000;
    b.push({ x: (R() - 0.5) * 3000, y: -4500 + h / 2, z: -21000 - R() * 4000, w: 900 + R() * 1200, h, d: 900 });
  }
  scene.add(blocks(a, slabMat(lk, (o.seed || 3) + 1, 0, env, 1.5)));
  scene.add(blocks(b, slabMat(lk, (o.seed || 3) + 2, 1, env, 1.5)));
  // the name, up the inner sides of three buildings
  const name = K.lettering(o.name, { size: 520, sy: 1.05, fill: [[0, '#ffffff'], [0.2, lk.ink], [1, lk.ink]], glow: { blur: 40, c: lk.glow, a: 1 } });
  const inner = (s, i) => (s < 0 ? a.concat(b) : a.concat(b)).filter((q) => Math.sign(q.x) === s && q.z > -16000).sort((p, q) => q.z - p.z)[i];
  [[-1, 2], [1, 4], [-1, 6], [1, 8]].forEach(([s, i]) => {
    const q = inner(s, i); if (!q) return;
    const p = picture(name);
    p.rotation.set(0, -s * Math.PI / 2, Math.PI / 2);
    p.position.set(q.x - s * (q.w / 2 + 4), Math.min(2600, q.y + q.h / 2 - name.w / 2 - 300), q.z);
    scene.add(p);
  });
  const fl = new THREE.Mesh(new THREE.PlaneGeometry(40000, 60000), new THREE.MeshStandardMaterial({ color: K.col(lk.deep), metalness: 0.8, roughness: 0.35, envMap: env }));
  fl.rotation.x = -Math.PI / 2; fl.position.set(0, -4500, -15000); scene.add(fl);
  const s0 = o.side || 1;
  return {
    scene, camera: cam,
    update(u, d) {
      const f = K.eInOut(u / d), g = u / d;
      aim(cam, s0 * K.lerp(300, -200, f), K.lerp(6200, 300, f), K.lerp(3200, -6500, g),
        s0 * K.lerp(-200, 100, f), K.lerp(-1800, 900, f), K.lerp(-5500, -15000, g), s0 * K.lerp(0.38, -0.06, f));
    },
  };
};

// ---- a star's name card: the player in the club's city, the crest above, the name ---------------------------------
SHOTS.card = (env, o) => {
  const lk = o.lk, scene = new THREE.Scene(), cam = camera(), P = o.player;
  scene.background = K.col(lk.deep);
  lights(scene, lk);
  const bg = picture(skyOf(lk.deep, lk.panel, o.seed || 5)); bg.scale.set(9000, 5200, 1); bg.position.set(0, 400, -4200); scene.add(bg);
  const R = K.rng((o.seed || 5) + 9), slabs = [];
  for (let i = 0; i < 14; i++) {
    const s = i % 2 ? 1 : -1, h = 1500 + R() * 2600;
    slabs.push({ x: s * (300 + R() * 2000), y: -1400 + h / 2, z: -1100 - R() * 2400, w: 240 + R() * 420, h, d: 240 + R() * 300, ry: (R() - 0.5) * 0.6 });
  }
  scene.add(blocks(slabs, slabMat(lk, (o.seed || 5) + 2, 0, env, 1.3)));
  const all = new THREE.Group(); scene.add(all);
  // the tall slab at the right with the club's name up it
  const tall = new THREE.Mesh(BOX, slabMat(lk, (o.seed || 5) + 3, 2, env, 1.1));
  tall.scale.set(330, 3400, 260); tall.position.set(900, 0, -330); tall.rotation.y = -0.38; all.add(tall);
  const tn = K.lettering(o.name, { size: 250, sy: 1.08, fill: [[0, K.hsl(0, 0, 1)], [0.15, lk.ink], [1, lk.ink]] });
  const tnp = picture(tn); tnp.rotation.z = Math.PI / 2;
  const tnScale = Math.min(1, 1100 / tn.w); tnp.scale.multiplyScalar(tnScale);
  const tnG = new THREE.Group(); tnG.position.set(900, -60, -330); tnG.rotation.y = -0.38; tnG.add(tnp); tnp.position.z = 136; all.add(tnG);
  // the crest, big at the top left
  const cr = crest3d(o.crest, 12, 3.5); cr.position.set(-790, 545, -380); cr.scale.setScalar(0.86 * (o.crestScale || 1)); all.add(cr);
  const sk = picture(K.skyline(lk, (o.seed || 5) + 4, 1500, 280)); sk.position.set(-430, -390, -260); all.add(sk);
  // the player, the glow behind him
  const gl = glow(lk.glow, 1500, 0.55); gl.position.set(330, 60, -200); all.add(gl);
  const pl = new THREE.Group(); all.add(pl);
  if (P && P.lit) {
    const tall = P.lit.face ? 700 : 1180, ph = picture(P.lit), k = tall / P.lit.h; ph.scale.multiplyScalar(k);
    ph.position.set(0, P.lit.face ? 30 : -540 + tall / 2 - 40, 0); pl.add(ph);
  }
  pl.position.set(o.playerX == null ? 330 : o.playerX, 0, 0);
  // the name: first name small in the club's ink, the last name huge, the number and place under it
  const txt = new THREE.Group(); txt.position.set(-800, -120, 40); all.add(txt);
  const last = word(P ? P.last : o.name, { size: 290, sy: 1.08, fill: K.WHITE_FADE, glow: { blur: 18, c: 'rgba(0,0,0,0.55)', a: 1 } });
  const fitK = Math.min(1, 900 / Math.max(1, last.userData.w)); last.scale.setScalar(fitK); txt.add(last);
  const first = P && P.first ? picture(K.lettering(P.first, { size: 70, weight: 700, family: 'Barlow7', spacing: 2, fill: lk.ink, glow: { blur: 12, c: 'rgba(0,0,0,0.75)', a: 1 } })) : null;
  if (first) { first.position.set(first.userData.w / 2 - 6, 290 * 1.08 * 0.72 * fitK + 52, 0); txt.add(first); }
  const nmTxt = P ? `${P.num || ''}${P.num && P.pos ? '  ' : ''}${P.pos || ''}` : '';
  const nm = nmTxt.trim() ? picture(K.lettering(nmTxt, { size: 74, weight: 800, fill: [[0, '#ffffff'], [0.25, lk.ink], [1, lk.ink]], glow: { blur: 12, c: 'rgba(0,0,0,0.7)', a: 1 } })) : null;
  if (nm) { nm.position.set(last.userData.w * fitK - nm.userData.w / 2 + 8, -66, 0); txt.add(nm); }
  const order = last.userData.letters.map((_, i) => i).sort(() => 0).map((i) => i);
  const R2 = K.rng((o.seed || 5) + 21);
  for (let i = order.length - 1; i > 0; i--) { const j = Math.floor(R2() * (i + 1)); [order[i], order[j]] = [order[j], order[i]]; }
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      const inX = (1 - K.eOutExpo(u / 0.45)) * 520;
      all.position.x = inX;
      pl.position.x = (o.playerX == null ? 330 : o.playerX) + (1 - K.eOut(u / 0.9)) * 120;
      pl.scale.setScalar(1 + (1 - K.eOut(u / 1.2)) * 0.04);
      cr.rotation.y = K.lerp(1.0, 0.42, K.eOut(u / 0.9)) + Math.sin(u * 1.1) * 0.03;
      cr.userData.face.userData.sheen.sweep.value = K.lerp(-0.5, 1.5, K.ramp(0.35, 1.3, u));
      const zc = DIST * K.lerp(1.05, 0.965, K.eOut(f));
      aim(cam, K.lerp(60, -40, f), K.lerp(-20, 10, f), zc, K.lerp(30, -20, f), 0, 0, K.lerp(0.012, -0.008, f));
      if (first) { fade(first, K.ss(0.25, 0.5, u)); first.position.x = first.userData.w / 2 - 6 - (1 - K.eOut(K.ramp(0.25, 0.6, u))) * 30; }
      last.userData.letters.forEach((L, i) => {
        const t0 = 0.32 + order.indexOf(i) * 0.065;
        const a = K.ramp(t0, t0 + 0.07, u), fl = u < t0 + 0.12 && u > t0 + 0.05 ? 0.6 : 1;
        fade(L, a * fl);
        L.scale.set(L.userData.w * (1 + (1 - a) * 0.25), L.userData.h * (1 + (1 - a) * 0.25), 1);
      });
      if (nm) fade(nm, K.ss(0.85, 1.1, u));
    },
  };
};

// ---- the crest in a neon diamond, the club's name above and below --------------------------------------------------
SHOTS.diamond = (env, o) => {
  const lk = o.lk, scene = new THREE.Scene(), cam = camera();
  scene.background = K.col(lk.deep);
  const bg = picture(K.grid(lk.deep, K.rgba(lk.glow, 0.35), lk.panel)); bg.scale.set(4400, 4400, 1); bg.position.z = -900; scene.add(bg);
  const outer = diamondLines(1380, lk.glow, lk.glow, 70); outer.position.z = -300; scene.add(outer);
  const mid = diamondLines(560, lk.glow, lk.glow, 52); mid.position.z = -120; scene.add(mid);
  const mid2 = diamondLines(610, lk.glow, lk.glow, 26); mid2.position.z = -140; scene.add(mid2);
  const cr = crest3d(o.crest, 12, 3.5); cr.scale.setScalar(o.crestScale || 1); scene.add(cr);
  const nmC = K.lettering(o.name, { size: 38, weight: 700, family: 'Barlow7', spacing: 30, fill: '#ffffff' });
  const top = picture(nmC), bot = picture(nmC);
  top.position.set(0, 430, 0); bot.position.set(0, -430, 0); scene.add(top); scene.add(bot);
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      outer.userData.grow(K.eOut(K.ramp(0, 0.5, u)));
      mid.userData.grow(K.eOut(K.ramp(0.08, 0.5, u))); mid2.userData.grow(K.eOut(K.ramp(0.14, 0.55, u)));
      const c = K.eOutExpo(K.ramp(0.05, 0.6, u));
      cr.scale.setScalar((o.crestScale || 1) * K.lerp(0.78, 1, c));
      cr.rotation.y = K.lerp(0.9, 0, c) + Math.sin(u * 1.4) * 0.05;
      cr.userData.face.userData.sheen.sweep.value = K.lerp(-0.5, 1.5, K.ramp(0.4, 1.3, u));
      fade(top, K.ss(0.3, 0.6, u)); fade(bot, K.ss(0.35, 0.65, u));
      aim(cam, 0, 0, DIST * K.lerp(1.06, 0.97, K.eOut(f)), 0, 0, 0, K.lerp(-0.03, 0.02, f));
    },
  };
};

// ---- the chant, three times, on angled panels -----------------------------------------------------------------------
SHOTS.chant = (env, o) => {
  const lk = o.lk, scene = new THREE.Scene(), cam = camera();
  scene.background = K.col(lk.deep);
  scene.fog = new THREE.Fog(K.col(lk.deep), 2500, 9000);
  lights(scene, lk);
  const R = K.rng(o.seed || 7), pan = [];
  for (const s of [-1, 1]) for (let i = 0; i < 7; i++) {
    pan.push({ x: s * (1000 + R() * 700), y: (R() - 0.5) * 1600, z: -300 - i * 650, w: 900, h: 260 + R() * 340, d: 70, ry: -s * (0.55 + R() * 0.3), rz: s * (R() - 0.5) * 0.3 });
  }
  scene.add(blocks(pan, slabMat(lk, (o.seed || 7) + 1, 2, env, 2.2)));
  const back = picture(skyOf(lk.deep, lk.panel, (o.seed || 7) + 2)); back.scale.set(9000, 6000, 1); back.position.z = -5000; scene.add(back);
  const txt = new THREE.Group(); txt.rotation.y = -0.2; txt.position.set(40, 30, 0); scene.add(txt);
  const ink2 = K.hsl(0, 0, 1);
  const L = K.lettering(o.chant, { size: 250, sy: 1.12, skew: 0.2, fill: [[0, ink2], [0.08, lk.ink], [1, lk.ink]], glow: { blur: 26, c: K.rgba(lk.glow, 0.7), a: 1 } });
  const kk = Math.min(1, 1500 / L.w);
  const ghost = picture(K.lettering(o.chant, { size: 320, sy: 1.12, skew: 0.2, fill: null, outline: { w: 3, c: K.rgba(lk.ink, 0.5) } }));
  ghost.scale.multiplyScalar(kk); ghost.position.set(-60, -40, -420); ghost.userData.a = 0.55; txt.add(ghost);
  const lines = [[300, 1], [20, 1.06], [-300, 1]].map(([y, s], i) => {
    const p = picture(L); p.scale.multiplyScalar(kk * s); p.position.set(i === 1 ? -20 : 20, y, i === 1 ? 30 : 0); txt.add(p); return p;
  });
  const small = picture(K.lettering(`//   ${o.name}   //`, { size: 34, weight: 700, family: 'Barlow7', spacing: 16, fill: '#ffffff' }));
  small.position.set(0, -138, 20); txt.add(small);
  const mini = o.crestPlain ? [picture(o.crestPlain), picture(o.crestPlain)] : [];
  mini.forEach((m, i) => { const k2 = 64 / m.userData.h; m.scale.multiplyScalar(k2); m.position.set((i ? 1 : -1) * (small.userData.w / 2 + 60), -138, 20); txt.add(m); });
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      lines.forEach((p, i) => {
        const t0 = i * 0.08, c = K.eOutExpo(K.ramp(t0, t0 + 0.5, u));
        p.position.x = (i === 1 ? -20 : 20) + (1 - c) * 1600;
        fade(p, K.ramp(t0, t0 + 0.15, u));
      });
      fade(ghost, K.ss(0.2, 0.6, u)); fade(small, K.ss(0.35, 0.6, u)); mini.forEach((m) => fade(m, K.ss(0.4, 0.65, u)));
      aim(cam, K.lerp(260, -160, f), K.lerp(40, -30, f), DIST * K.lerp(1.02, 0.94, f), K.lerp(120, -60, f), 0, 0, K.lerp(0.04, -0.03, f));
    },
  };
};

// ---- another club, quick: its crest in metal before its buildings --------------------------------------------------
SHOTS.parade = (env, o) => {
  const lk = o.lk, scene = new THREE.Scene(), cam = camera();
  scene.background = K.col(lk.deep);
  scene.fog = new THREE.Fog(K.col(lk.deep), 2500, 9000);
  lights(scene, lk);
  const R = K.rng(o.seed || 9), sl = [];
  for (let i = 0; i < 16; i++) {
    const s = i % 2 ? 1 : -1, h = 1800 + R() * 3000;
    sl.push({ x: s * (500 + R() * 1700), y: -1300 + h / 2, z: -600 - R() * 2600, w: 260 + R() * 500, h, d: 300, ry: (R() - 0.5) * 0.8 });
  }
  scene.add(blocks(sl, slabMat(lk, (o.seed || 9) + 1, R() < 0.5 ? 0 : 1, env, 1.6)));
  const back = picture(skyOf(lk.deep, lk.mid, (o.seed || 9) + 2)); back.scale.set(9000, 6000, 1); back.position.z = -5000; scene.add(back);
  const gl = glow(lk.glow, 2000, 0.7); gl.position.z = -300; scene.add(gl);
  const cr = crest3d(o.crest, 14, 4); cr.scale.setScalar(1.55 * (o.crestScale || 1)); scene.add(cr);
  const s0 = o.side || 1;
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      cr.rotation.y = s0 * K.lerp(0.75, 0.15, K.eOut(f));
      cr.rotation.z = s0 * K.lerp(-0.08, 0.02, f);
      cr.position.x = s0 * K.lerp(-90, 40, K.eOut(f));
      cr.userData.face.userData.sheen.sweep.value = K.lerp(-0.3, 1.4, f);
      aim(cam, s0 * K.lerp(240, -100, f), K.lerp(-120, 60, f), DIST * K.lerp(0.95, 0.86, f), 0, 0, 0, s0 * K.lerp(0.05, -0.02, f));
    },
  };
};

// ---- two players face to face: each in his club's panel, split down the middle --------------------------------------
function panelCanvas(lk, crestImg, seed, w = 1000, h = 1180) {
  const R = K.rng(seed), c = K.canvas(w, h), g = c.getContext('2d');
  const gr = g.createLinearGradient(0, 0, w * 0.3, h);
  gr.addColorStop(0, lk.mid); gr.addColorStop(0.55, lk.panel); gr.addColorStop(1, lk.deep);
  g.fillStyle = gr; g.fillRect(0, 0, w, h);
  for (let i = 0; i < 18; i++) {
    const x = R() * w, bw = 20 + R() * 90;
    g.fillStyle = K.rgba(i % 3 ? '#000000' : lk.glow, i % 3 ? 0.18 : 0.12); g.fillRect(x, 0, bw, h);
  }
  if (crestImg) {
    const s = w * 0.78, k = s / Math.max(crestImg.width, crestImg.height);
    g.save(); g.globalAlpha = 0.9; g.translate(w * 0.06, -h * 0.06); g.rotate(-0.12);
    g.drawImage(crestImg, -s * 0.15, 0, crestImg.width * k, crestImg.height * k); g.restore();
    g.fillStyle = K.rgba(lk.deep, 0.35); g.fillRect(0, 0, w, h);
  }
  const bot = g.createLinearGradient(0, h * 0.62, 0, h);
  bot.addColorStop(0, 'rgba(0,0,0,0)'); bot.addColorStop(1, K.rgba(lk.deep, 0.95));
  g.fillStyle = bot; g.fillRect(0, h * 0.62, w, h * 0.38);
  g.fillStyle = lk.glow; g.fillRect(0, 0, 6, h); g.fillRect(w - 6, 0, 6, h);
  return c;
}
let SHADE = null;
function shade(w, h) {
  if (!SHADE) {
    const c = K.canvas(8, 256), g = c.getContext('2d'), gr = g.createLinearGradient(0, 0, 0, 256);
    gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(0.55, 'rgba(0,0,0,0.62)'); gr.addColorStop(1, 'rgba(0,0,0,0.88)');
    g.fillStyle = gr; g.fillRect(0, 0, 8, 256); SHADE = c;
  }
  const p = picture(SHADE); p.scale.set(w, h, 1); return p;
}
function label(P, lk, scale = 1) {
  const g = new THREE.Group();
  const num = P.num ? picture(K.lettering(String(P.num), { size: 150 * scale, sy: 1.08, fill: lk.ink })) : null;
  const last = picture(K.lettering(P.last, { size: 92 * scale, sy: 1.08, fill: K.WHITE_FADE }));
  const first = P.first ? picture(K.lettering(P.first, { size: 38 * scale, weight: 700, family: 'Barlow7', spacing: 1, fill: '#ffffff' })) : null;
  const pos = P.pos ? picture(K.lettering(P.pos, { size: 34 * scale, weight: 700, family: 'Barlow7', fill: lk.ink })) : null;
  let x = 0;
  if (pos) { pos.position.set(x + pos.userData.w / 2, -28 * scale, 0); x += pos.userData.w - 6 * scale; g.add(pos); }
  if (num) { num.position.set(x + num.userData.w / 2, 0, 0); x += num.userData.w - 4 * scale; g.add(num); }
  last.position.set(x + last.userData.w / 2, -16 * scale, 0); g.add(last);
  if (first) { first.position.set(x + first.userData.w / 2 + 4, 52 * scale, 0); g.add(first); }
  x += last.userData.w;
  g.children.forEach((m) => { m.position.x -= x / 2; });
  g.userData.w = x;
  return g;
}
SHOTS.duel = (env, o) => {
  const scene = new THREE.Scene(), cam = camera();
  scene.background = K.col('#03050a');
  const sides = o.pair.map((q, i) => {
    const s = i ? 1 : -1, g = new THREE.Group();
    g.position.set(s * 490, 0, 0); g.rotation.y = -s * 0.1; scene.add(g);
    const bg = picture(panelCanvas(q.lk, q.crestImg, (o.seed || 1) + i * 7)); bg.position.z = -40; g.add(bg);
    const gl = glow(q.lk.glow, 1100, 0.45); gl.position.set(0, 80, -20); g.add(gl);
    const pl = new THREE.Group(); g.add(pl);
    if (q.player.lit) {
      const f = q.player.lit.face, tall = f ? 600 : 920, ph = picture(q.player.lit), k = tall / q.player.lit.h; ph.scale.multiplyScalar(k);
      ph.position.set(0, f ? 20 : -590 + 460, 0); pl.add(ph);
    }
    const sh = shade(1000, 380); sh.position.set(0, -400, 10); g.add(sh);
    const lb = label(q.player, q.lk, 1); lb.position.set(0, -420, 30);
    const kk = Math.min(1, 860 / lb.userData.w); lb.scale.setScalar(kk); g.add(lb);
    return { g, pl, lb, s };
  });
  const seam = neonLine(0, -700, 0, 700, '#ffffff', 40); seam.position.z = 60; scene.add(seam);
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      sides.forEach(({ g, pl, lb, s }) => {
        const c = K.eOutExpo(K.ramp(0, 0.5, u));
        g.position.y = (1 - c) * 1250 * -s;
        pl.position.y = (1 - K.eOut(K.ramp(0.1, 0.9, u))) * -60;
        pl.position.x = s * K.lerp(0, -16, f);
        fade(lb, K.ss(0.3, 0.6, u));
        lb.position.x = (1 - K.eOut(K.ramp(0.3, 0.7, u))) * -s * 40;
      });
      seam.userData.grow(K.eOut(K.ramp(0.15, 0.5, u)));
      aim(cam, 0, 0, DIST * K.lerp(1.0, 0.95, f), 0, 0, 0, 0);
    },
  };
};

// ---- the two clubs face to face: their crests in metal, each in its club's light ----------------------------------
function split(home, away, seed) {
  const w = 2048, h = 1152, c = K.canvas(w, h), g = c.getContext('2d'), R = K.rng(seed);
  for (const [lk, x0, x1] of [[home, 0, w * 0.56], [away, w * 0.44, w]]) {
    g.save(); g.beginPath();
    if (x0 === 0) { g.moveTo(0, 0); g.lineTo(w * 0.56, 0); g.lineTo(w * 0.44, h); g.lineTo(0, h); }
    else { g.moveTo(w * 0.56, 0); g.lineTo(w, 0); g.lineTo(w, h); g.lineTo(w * 0.44, h); }
    g.clip();
    const cx = x0 === 0 ? w * 0.22 : w * 0.78, gr = g.createRadialGradient(cx, h * 0.45, 0, cx, h * 0.5, w * 0.55);
    gr.addColorStop(0, lk.mid); gr.addColorStop(0.5, lk.panel); gr.addColorStop(1, lk.deep);
    g.fillStyle = gr; g.fillRect(0, 0, w, h);
    for (let i = 0; i < 12; i++) { g.fillStyle = K.rgba(lk.glow, 0.05 + R() * 0.06); const x = x0 + R() * (x1 - x0); g.fillRect(x, 0, 10 + R() * 60, h); }
    g.restore();
  }
  g.strokeStyle = 'rgba(255,255,255,0.9)'; g.lineWidth = 4; g.beginPath(); g.moveTo(w * 0.56, 0); g.lineTo(w * 0.44, h); g.stroke();
  return c;
}
SHOTS.faceoff = (env, o) => {
  const scene = new THREE.Scene(), cam = camera();
  const bg = picture(split(o.home.lk, o.away.lk, o.seed || 3)); bg.scale.set(3600, 2025, 1); bg.position.z = -900; scene.add(bg);
  const crs = [o.home, o.away].map((q, i) => {
    const s = i ? 1 : -1;
    const gl = glow(q.lk.glow, 1500, 0.8); gl.position.set(s * 470, 0, -200); scene.add(gl);
    const cr = crest3d(q.chrome, 14, 4); cr.scale.setScalar(q.crestScale || 1); scene.add(cr);
    return { cr, s, gl };
  });
  const flash = glow('#ffffff', 900, 1); flash.position.z = 50; scene.add(flash);
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      crs.forEach(({ cr, s }) => {
        const c = K.eOutExpo(K.ramp(0, 0.65, u));
        cr.position.x = s * K.lerp(1500, 450, c);
        cr.rotation.y = -s * K.lerp(1.3, 0.3, c) - s * Math.sin(u * 1.2) * 0.04;
        cr.userData.face.userData.sheen.sweep.value = K.lerp(-0.5, 1.5, K.ramp(0.55 + (s > 0 ? 0.15 : 0), 1.4, u));
      });
      fade(flash, Math.max(0, 1 - Math.abs(u - 0.55) / 0.18) * 0.9);
      aim(cam, K.lerp(0, 0, f), K.lerp(-30, 20, f), DIST * K.lerp(1.04, 0.93, K.eOut(f)), 0, 0, 0, 0);
    },
  };
};

// ---- the two clubs' short names, a neon diamond between --------------------------------------------------------------
SHOTS.abbr = (env, o) => {
  const scene = new THREE.Scene(), cam = camera(), { home, away } = o;
  const bgC = K.canvas(2048, 1152), g = bgC.getContext('2d');
  g.fillStyle = '#05060a'; g.fillRect(0, 0, 2048, 1152);
  for (const [lk, x] of [[home.lk, 300], [away.lk, 1748]]) {
    const gr = g.createRadialGradient(x, 576, 0, x, 576, 1200);
    gr.addColorStop(0, K.rgba(lk.glow, 0.55)); gr.addColorStop(0.4, K.rgba(lk.panel, 0.6)); gr.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 2048, 1152);
  }
  g.globalAlpha = 0.25; g.strokeStyle = '#ffffff';
  for (let i = -1152; i < 2048; i += 30) { g.beginPath(); g.moveTo(i, 1152); g.lineTo(i + 1152, 0); g.stroke(); }
  const bg = picture(bgC); bg.scale.set(3800, 2140, 1); bg.position.z = -800; scene.add(bg);
  const corner = (x, y, dx, dy, c) => { const l = neonLine(x, y, x + dx, y + dy, c, 60); l.position.z = -200; scene.add(l); return l; };
  const cl = [corner(-1500, 380, 600, 600, home.lk.glow), corner(-1500, -380, 600, -600, home.lk.glow), corner(1500, 380, -600, 600, away.lk.glow), corner(1500, -380, -600, -600, away.lk.glow)];
  const dia = diamondLines(250, home.lk.glow, away.lk.glow, 46); scene.add(dia);
  const dia2 = diamondLines(205, home.lk.glow, away.lk.glow, 22); scene.add(dia2);
  const big = (q, s) => {
    const lt = K.hsl(0, 0, 1);
    const L = K.lettering(q.abbr, { size: 430, sy: 1.12, fill: [[0, lt], [0.12, q.lk.glow], [0.7, q.lk.glow], [1, q.lk.mid]], extrude: { n: 14, dx: 1.1, dy: 1.8, top: q.lk.panel, bottom: q.lk.deep } });
    const p = picture(L); const k = Math.min(1, 600 / L.w); p.scale.multiplyScalar(k);
    p.position.set(s * (215 + p.userData.w * k / 2), 0, 20); scene.add(p);
    const nm = K.lettering(q.name, { size: 34, weight: 700, family: 'Barlow7', spacing: 26, fill: '#ffffff' });
    const t1 = picture(nm), t2 = picture(nm);
    t1.position.set(p.position.x, 390, 0); t2.position.set(p.position.x, -390, 0); scene.add(t1); scene.add(t2);
    return { p, t1, t2, x: p.position.x, s };
  };
  const bs = [big(home, -1), big(away, 1)];
  
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      bs.forEach((b) => {
        const c = K.eOutExpo(K.ramp(0, 0.5, u));
        b.p.position.x = b.x + b.s * (1 - c) * 1200;
        fade(b.t1, K.ss(0.35, 0.65, u)); fade(b.t2, K.ss(0.4, 0.7, u));
      });
      dia.userData.grow(K.eOut(K.ramp(0.12, 0.55, u))); dia2.userData.grow(K.eOut(K.ramp(0.2, 0.6, u)));
      cl.forEach((l, i) => l.userData.grow(K.eOut(K.ramp(0.05 + i * 0.04, 0.5 + i * 0.04, u))));
      aim(cam, 0, 0, DIST * K.lerp(1.05, 0.95, K.eOut(f)), 0, 0, 0, K.lerp(0.015, -0.01, f));
    },
  };
};

// ---- three of a club's players under its crest ------------------------------------------------------------------------
SHOTS.trio = (env, o) => {
  const lk = o.lk, scene = new THREE.Scene(), cam = camera();
  scene.background = K.col(lk.deep);
  lights(scene, lk);
  const bg = picture(panelCanvas(lk, null, (o.seed || 4) + 1, 2048, 1152)); bg.scale.set(3800, 2140, 1); bg.position.z = -1200; scene.add(bg);
  const cr = crest3d(o.crest, 12, 4); cr.position.set(0, 300, -500); cr.scale.setScalar(1.35 * (o.crestScale || 1)); scene.add(cr);
  const R = K.rng(o.seed || 4), sl = [];
  for (let i = 0; i < 10; i++) { const s = i % 2 ? 1 : -1, h = 2400 + R() * 1600; sl.push({ x: s * (900 + R() * 900), y: -1400 + h / 2, z: -900 - R() * 900, w: 300 + R() * 300, h, d: 260, ry: s * 0.4 }); }
  scene.add(blocks(sl, slabMat(lk, (o.seed || 4) + 2, 0, env, 1.4)));
  const ps = o.players.slice(0, 3);
  const spots = ps.length === 3 ? [[-600, 0.86, -40], [0, 1, 40], [600, 0.86, -40]] : ps.length === 2 ? [[-330, 0.95, 0], [330, 0.95, 0]] : [[0, 1, 0]];
  const order = ps.length === 3 ? [1, 0, 2] : ps.map((_, i) => i);
  const items = order.map((pi, i) => {
    const P = ps[pi], [x, s, z] = spots[i], g = new THREE.Group(); g.position.set(x, 0, z); scene.add(g);
    const panel = picture(K.glowDot(64), { color: lk.glow, opacity: 0.0 });
    const fr = new THREE.Mesh(GEO, new THREE.MeshBasicMaterial({ color: K.col(lk.panel), transparent: true, opacity: 0.55, depthWrite: false, toneMapped: false }));
    fr.scale.set(520 * s, 880 * s, 1); fr.position.set(0, -60, -30); fr.rotation.y = -x / 2600; fr.userData.a = 0.55; g.add(fr);
    const e1 = neonLine(-260 * s, -500, -260 * s, 380 * s, lk.glow, 22, false), e2 = neonLine(260 * s, -500, 260 * s, 380 * s, lk.glow, 22, false);
    e1.position.z = -20; e2.position.z = -20; g.add(e1); g.add(e2);
    const ph = new THREE.Group(); g.add(ph);
    if (P.lit) {
      const tall = (P.lit.face ? 520 : 860) * s, p = picture(P.lit), k = tall / P.lit.h; p.scale.multiplyScalar(k);
      p.position.set(0, P.lit.face ? -40 : -540 + tall / 2 - 30, 0); ph.add(p);
    }
    const sh = shade(560 * s, 300); sh.position.set(0, -420, 20); g.add(sh);
    const lb = label(P, lk, 0.78 * s); lb.position.set(0, -410, 40); lb.scale.setScalar(Math.min(1, 520 * s / lb.userData.w)); g.add(lb);
    return { g, ph, lb, fr, i, panel, e1, e2 };
  });
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      items.forEach(({ ph, lb, fr, i, e1, e2 }) => {
        const t0 = i * 0.07, c = K.eOutExpo(K.ramp(t0, t0 + 0.45, u));
        ph.position.y = (1 - c) * -420;
        fade(ph, K.ramp(t0, t0 + 0.12, u)); fade(fr, K.ramp(t0, t0 + 0.2, u)); fade(lb, K.ss(t0 + 0.25, t0 + 0.5, u));
        e1.userData.grow(c); e2.userData.grow(c);
      });
      cr.rotation.y = Math.sin(u * 1.5) * 0.12;
      cr.userData.face.userData.sheen.sweep.value = K.lerp(-0.4, 1.4, K.ramp(0.2, 1.1, u));
      aim(cam, 0, K.lerp(-10, 10, f), DIST * K.lerp(1.0, 0.95, f), 0, 0, 0, 0);
    },
  };
};

// ---- the Whitebrook Road lockup: chrome blades, the show's name, the competition's mark; and the end --------------
function blade(len, wid, curve) {
  const s = new THREE.Shape();
  s.moveTo(0, 0);
  s.quadraticCurveTo(len * 0.5, wid * 0.2 + curve, len, wid * 1.2 + curve * 1.8);
  s.lineTo(len * 0.985, wid * 1.2 + curve * 1.8 - 10);
  s.quadraticCurveTo(len * 0.5, -wid * 0.55 + curve * 0.9, 0, -wid);
  s.closePath();
  return new THREE.ExtrudeGeometry(s, { depth: 26, bevelEnabled: true, bevelThickness: 7, bevelSize: 5, bevelSegments: 2, curveSegments: 14 });
}
SHOTS.lockup = (env, o) => {
  const lk = { glow: '#37d6ff', panel: '#071a38', mid: '#0f2e5c', deep: '#010510', ink: '#bdefff' };
  const scene = new THREE.Scene(), cam = camera();
  scene.background = K.col('#01040c');
  scene.fog = new THREE.Fog(K.col('#020814'), 3500, 16000);
  lights(scene, lk);
  // the hall behind: a dark sky with a glow low down, two rows of tall fins leading away, light shafts, a gloss floor
  const skyB = picture(skyOf('#010309', '#0a2a5e', 19)); skyB.scale.set(26000, 15000, 1); skyB.position.set(0, 1500, -12000); scene.add(skyB);
  const fins = [];
  for (const s of [-1, 1]) for (let i = 0; i < 9; i++) {
    fins.push({ x: s * (980 + i * 380), y: 300, z: -500 - i * 760, w: 120, h: 3600 + i * 260, d: 1100, ry: s * 0.42 });
  }
  scene.add(blocks(fins, slabMat(lk, 41, 1, env, 1.25)));
  const ribs = [];
  for (let i = 0; i < 12; i++) ribs.push({ x: 0, y: 2050 + i * 25, z: -900 - i * 650, w: 5200 + i * 300, h: 26, d: 26 });
  scene.add(blocks(ribs, new THREE.MeshBasicMaterial({ color: K.col(lk.glow).multiplyScalar(1.4), toneMapped: false })));
  const shafts = [];
  for (let i = 0; i < 5; i++) {
    const s = picture(K.streak(1024, 128), { add: true, color: lk.glow, opacity: 0.22 });
    s.scale.set(4200, 620, 1); s.rotation.z = Math.PI / 2 + (i - 2) * 0.2; s.position.set((i - 2) * 560, 1100, -2600); s.userData.a = 0.22;
    scene.add(s); shafts.push(s);
  }
  const floor = new THREE.Mesh(new THREE.PlaneGeometry(30000, 30000), new THREE.MeshStandardMaterial({ color: K.col('#020814'), metalness: 0.9, roughness: 0.22, envMap: env }));
  floor.rotation.x = -Math.PI / 2; floor.position.y = -760; scene.add(floor);
  const lane = [];
  for (let i = 0; i < 14; i++) lane.push({ x: (i % 2 ? 1 : -1) * (300 + Math.floor(i / 2) * 260), y: -755, z: -300 - Math.floor(i / 2) * 900, w: 16, h: 4, d: 700 });
  scene.add(blocks(lane, new THREE.MeshBasicMaterial({ color: K.col(lk.glow).multiplyScalar(1.6), toneMapped: false })));
  const back = glow(lk.glow, 3600, 0.5); back.position.set(0, 40, -900); scene.add(back);
  const back2 = glow('#ffffff', 1500, 0.25); back2.position.set(0, 30, -700); scene.add(back2);
  // the blades
  const metal = new THREE.MeshStandardMaterial({ color: K.col('#f2f5fa'), metalness: 1, roughness: 0.12, envMap: env, envMapIntensity: 2.4, emissive: K.col('#1c2a3c') });
  const blades = [];
  for (const sx of [-1, 1]) for (const sy of [1, -1]) {
    const geo = blade(sy > 0 ? 1050 : 900, sy > 0 ? 46 : 34, sy > 0 ? 110 : 60);
    const m = new THREE.Mesh(geo, metal);
    const g = new THREE.Group(); g.add(m);
    m.scale.set(1, sy, 1); m.rotation.x = -sy * 0.42;
    const gl = neonLine(40, sy * -16, sy > 0 ? 1000 : 860, sy * (sy > 0 ? 160 : 92), lk.glow, 44); gl.position.z = 44; g.add(gl);
    g.scale.x = sx;
    g.userData.home = { x: sx * (sy > 0 ? 560 : 520), y: sy > 0 ? 125 : -95 };
    g.userData.sx = sx; g.userData.sy = sy;
    scene.add(g); blades.push(g);
  }
  const title = K.lettering(o.title || 'MATCH NIGHT', { size: 236, sy: 1.0, skew: 0.12, fill: K.CHROME, stroke: { w: 3, c: '#121722' }, extrude: { n: 16, dx: 0.9, dy: 1.5 } });
  const tP = chrome(title, { gain: 1 }); const tk = Math.min(1, 1040 / title.w); tP.scale.multiplyScalar(tk); tP.position.set(0, 20, 30); scene.add(tP);
  const sub = picture(K.lettering(o.sub || 'WHITEBROOK ROAD SPORTS', { size: 54, weight: 700, family: 'Barlow7', spacing: 15, fill: K.CHROME, stroke: { w: 1.5, c: '#10141c' } }));
  sub.position.set(0, -145, 30); scene.add(sub);
  const mark = chrome(K.brandMark(240, lk.glow)); mark.position.set(0, 255, 20); scene.add(mark);
  let comp = null;
  if (o.mark) {
    const mk = K.fitted(o.mark, 250), plate = K.canvas(mk.width + 120, mk.height + 120), pg = plate.getContext('2d');
    const halo = K.tint(mk, '#ffffff');
    pg.filter = 'blur(16px)'; pg.drawImage(halo, 60, 60); pg.drawImage(halo, 60, 60); pg.filter = 'blur(5px)'; pg.drawImage(halo, 60, 60);
    pg.filter = 'none'; pg.drawImage(mk, 60, 60);
    comp = picture(plate); comp.position.set(0, -290, 30); scene.add(comp);
  }
  const fx = [];
  if (o.fixture) {
    const f1 = picture(K.lettering(o.fixture, { size: 92, sy: 1.06, fill: K.WHITE_FADE, glow: { blur: 22, c: K.rgba(lk.glow, 0.8), a: 1 } }));
    const fk = Math.min(1, 1500 / f1.userData.w); f1.scale.multiplyScalar(fk); f1.position.set(0, -270, 40); scene.add(f1); fx.push(f1);
    if (o.line2) {
      const f2 = picture(K.lettering(o.line2, { size: 38, weight: 700, family: 'Barlow7', spacing: 9, fill: lk.ink }));
      f2.position.set(0, -362, 40); scene.add(f2); fx.push(f2);
    }
    if (comp) comp.position.y = -455;
  }
  const sparks = [];
  const R = K.rng(77);
  for (let i = 0; i < 26; i++) {
    const s = glow(i % 4 ? lk.glow : '#ffffff', 30 + R() * 60, 1);
    s.position.set((R() - 0.5) * 2600, (R() - 0.5) * 1300, 100 + R() * 600); s.userData.ph = R() * 6; s.userData.sp = 2 + R() * 4;
    scene.add(s); sparks.push(s);
  }
  return {
    scene, camera: cam,
    update(u, d) {
      const f = u / d;
      blades.forEach((g, i) => {
        const c = K.eOutExpo(K.ramp(0.02 + (i % 2) * 0.06, 0.62 + (i % 2) * 0.06, u)), h = g.userData.home;
        g.position.set(h.x + g.userData.sx * (1 - c) * 2400, h.y + g.userData.sy * (1 - c) * 300, (1 - c) * 600);
        g.rotation.z = g.userData.sx * g.userData.sy * (1 - c) * 0.5;
      });
      const tc = K.eOutExpo(K.ramp(0, 0.5, u));
      tP.scale.set(title.w * tk * K.lerp(1.4, 1, tc), title.h * tk * K.lerp(1.4, 1, tc), 1);
      fade(tP, K.ramp(0, 0.18, u));
      tP.userData.sheen.sweep.value = u < 2 ? K.lerp(-0.6, 1.6, K.ramp(0.45, 1.35, u)) : K.lerp(-0.6, 1.6, K.ramp(2.2, 3.1, u));
      fade(mark, K.ss(0.2, 0.5, u)); mark.position.y = 255 + (1 - K.eOut(K.ramp(0.2, 0.7, u))) * 80;
      mark.userData.sheen.sweep.value = K.lerp(-0.5, 1.5, K.ramp(0.6, 1.4, u));
      fade(sub, K.ss(0.4, 0.8, u));
      if (comp) fade(comp, K.ss(o.fixture ? 1.4 : 0.6, o.fixture ? 1.8 : 1.0, u));
      fx.forEach((p, i) => { fade(p, K.ss(0.9 + i * 0.3, 1.3 + i * 0.3, u)); p.position.y += 0; });
      fade(back, 0.5 * K.ramp(0, 0.4, u)); fade(back2, 0.25 * K.ramp(0, 0.4, u));
      shafts.forEach((s, i) => fade(s, (0.6 + 0.4 * Math.sin(u * 1.3 + i)) * K.ramp(0.1, 0.6, u)));
      sparks.forEach((s) => fade(s, Math.max(0, Math.sin(u * s.userData.sp + s.userData.ph)) * K.ramp(0.3, 0.8, u)));
      aim(cam, K.lerp(150, -60, K.eOut(f)), K.lerp(-60, 20, f), DIST * K.lerp(1.16, 1.0, K.eOut(Math.min(1, u / Math.min(d, 3.5)))), 0, 0, 0, K.lerp(-0.02, 0.0, f));
    },
  };
};

export { SHOTS, skyOf, crest3d, word, neonLine, diamondLines, glow, slabMat, blocks, lights, label, shade, panelCanvas, fade, GEO, BOX };
