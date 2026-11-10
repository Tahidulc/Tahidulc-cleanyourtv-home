// A stylised player: a low-poly figure of capsules (no one's likeness), drawn as a dark silhouette with rim light from
// two lights behind it. Posed by joint angles; the animations are functions of time (run, walk, the strike, the dive).
// The figure faces +z, its left on +x. Angles: a thigh or arm swung forward is negative x, a knee bent positive x, an elbow
// bent negative x, toes pointed positive x; z spreads a limb outwards (mirrored for the right side).
import * as THREE from '../vendor/three.module.js';
import { shader, OUT, FOG, clamp, lerp } from './kit.js';

export function rimMaterial() {
  return shader({
    uniforms: {
      uL1: { value: new THREE.Vector3(0, 0, -1) }, uC1: { value: new THREE.Color(1.6, 1.7, 2.0) },
      uL2: { value: new THREE.Vector3(1, 0, 0) }, uC2: { value: new THREE.Color(0.9, 0.65, 0.45) },
      uBase: { value: new THREE.Color(0.006, 0.007, 0.011) }, uFill: { value: 0.04 },
    },
    vertexShader: `
      varying vec3 vN; varying vec3 vV;
      void main() {
        vec4 wp = modelMatrix * vec4(position, 1.0);
        #ifdef USE_INSTANCING
        wp = modelMatrix * instanceMatrix * vec4(position, 1.0);
        #endif
        vec4 mv = viewMatrix * wp;
        vN = normalize(normalMatrix * normal);
        vV = -mv.xyz;
        gl_Position = projectionMatrix * mv;
      }`,
    fragmentShader: `
      uniform vec3 uL1, uL2, uC1, uC2, uBase; uniform float uFill;
      ${FOG}
      varying vec3 vN; varying vec3 vV;
      void main() {
        vec3 n = normalize(vN), v = normalize(vV);
        float ndv = max(dot(n, v), 0.0);
        float fr = pow(1.0 - ndv, 3.5);
        // light from behind outlines the whole figure, strongest on the side the light is on
        float l1 = dot(n, uL1), l2 = dot(n, uL2);
        float b1 = clamp(dot(uL1, -v) * 0.5 + 0.5, 0.0, 1.0), b2 = clamp(dot(uL2, -v) * 0.5 + 0.5, 0.0, 1.0);
        vec3 c = uBase * (1.0 + uFill * 10.0 * (0.5 + 0.5 * n.y));
        c += uC1 * fr * clamp(l1 + 0.55, 0.0, 1.0) * (0.12 + 1.7 * b1 * b1) + uC1 * pow(max(l1, 0.0), 4.0) * 0.05;
        c += uC2 * fr * clamp(l2 + 0.45, 0.0, 1.0) * (0.08 + 1.0 * b2 * b2) * 0.8;
        gl_FragColor = vec4(fogged(c, length(vV)), 1.0);
        ${OUT}
      }`,
  });
}

// the parts, shared by every figure
const GEO = {};
function geos() {
  if (GEO.thigh) return GEO;
  const cap = (r, l) => new THREE.CapsuleGeometry(r, l, 3, 9);
  GEO.thigh = cap(0.068, 0.34).translate(0, -0.21, 0);
  GEO.shin = cap(0.05, 0.36).translate(0, -0.22, 0);
  GEO.foot = cap(0.045, 0.14).rotateX(Math.PI / 2).translate(0, -0.035, 0.065);
  GEO.upper = cap(0.046, 0.21).translate(0, -0.145, 0);
  GEO.fore = cap(0.04, 0.2).translate(0, -0.135, 0);
  GEO.hand = new THREE.SphereGeometry(0.046, 8, 6).translate(0, -0.29, 0);
  GEO.torso = new THREE.CylinderGeometry(0.2, 0.15, 0.48, 10, 1).scale(1, 1, 0.62).translate(0, 0.27, 0);
  GEO.chest = cap(0.09, 0.3).rotateZ(Math.PI / 2).translate(0, 0.47, 0);
  GEO.hips = cap(0.092, 0.17).rotateZ(Math.PI / 2).translate(0, 0.0, 0);
  GEO.shorts = new THREE.CylinderGeometry(0.145, 0.16, 0.2, 10, 1).scale(1, 1, 0.72).translate(0, -0.04, 0);
  GEO.neck = new THREE.CylinderGeometry(0.05, 0.055, 0.12, 8).translate(0, 0.05, 0);
  GEO.head = new THREE.SphereGeometry(0.108, 12, 9).scale(0.92, 1.1, 1.0).translate(0, 0.17, 0.01);
  GEO.glove = new THREE.SphereGeometry(0.066, 8, 6).scale(1, 1.25, 0.6).translate(0, -0.3, 0);
  return GEO;
}

export function makeFigure(mat, opts = {}) {
  const G = geos();
  const m = (geo) => { const o = new THREE.Mesh(geo, mat); o.frustumCulled = false; return o; };
  const root = new THREE.Group();
  const pelvis = new THREE.Group(); pelvis.position.y = 0.98; root.add(pelvis);
  pelvis.add(m(G.hips)); pelvis.add(m(G.shorts));
  const spine = new THREE.Group(); spine.position.y = 0.06; pelvis.add(spine);
  spine.add(m(G.torso)); spine.add(m(G.chest));
  const neck = new THREE.Group(); neck.position.y = 0.55; spine.add(neck);
  neck.add(m(G.neck)); neck.add(m(G.head));
  const J = { root, pelvis, spine, neck };
  for (const [s, sx] of [['L', 1], ['R', -1]]) {
    const sh = new THREE.Group(); sh.position.set(0.215 * sx, 0.48, 0); spine.add(sh);
    sh.add(m(G.upper));
    const el = new THREE.Group(); el.position.y = -0.29; sh.add(el);
    el.add(m(G.fore)); el.add(m(opts.gloves ? G.glove : G.hand));
    const hip = new THREE.Group(); hip.position.set(0.1 * sx, -0.02, 0); pelvis.add(hip);
    hip.add(m(G.thigh));
    const kn = new THREE.Group(); kn.position.y = -0.44; hip.add(kn);
    kn.add(m(G.shin));
    const an = new THREE.Group(); an.position.y = -0.45; kn.add(an);
    an.add(m(G.foot));
    J['sh' + s] = sh; J['el' + s] = el; J['hip' + s] = hip; J['kn' + s] = kn; J['an' + s] = an;
  }
  J.footR = J.anR; J.footL = J.anL;
  return { root, J, pose: (p) => applyPose(J, p) };
}

// a pose: every angle optional (0)
export const P0 = { x: 0, y: 0, z: 0, ry: 0, py: 0, pitch: 0, roll: 0, lean: 0, twist: 0, side: 0, head: 0, headY: 0,
  thL: 0, thLz: 0, knL: 0, anL: 0, thR: 0, thRz: 0, knR: 0, anR: 0, arL: 0, arLz: 0.12, elL: -0.25, arR: 0, arRz: 0.12, elR: -0.25 };
export function applyPose(J, q) {
  const p = Object.assign({}, P0, q);
  J.root.position.set(p.x, p.y, p.z); J.root.rotation.set(0, p.ry, 0);
  J.pelvis.position.y = 0.98 + p.py; J.pelvis.rotation.set(p.pitch, 0, p.roll);
  J.spine.rotation.set(p.lean, p.twist, p.side);
  J.neck.rotation.set(p.head, p.headY, 0);
  J.hipL.rotation.set(p.thL, 0, p.thLz); J.knL.rotation.x = p.knL; J.anL.rotation.x = p.anL;
  J.hipR.rotation.set(p.thR, 0, -p.thRz); J.knR.rotation.x = p.knR; J.anR.rotation.x = p.anR;
  J.shL.rotation.set(p.arL, 0, p.arLz); J.elL.rotation.x = p.elL;
  J.shR.rotation.set(p.arR, 0, -p.arRz); J.elR.rotation.x = p.elR;
}

// Catmull-Rom through keyed poses: keys [[time, pose], ...] (every key gives the same angles)
export function keyed(keys, t) {
  if (t <= keys[0][0]) return Object.assign({}, keys[0][1]);
  const n = keys.length;
  if (t >= keys[n - 1][0]) return Object.assign({}, keys[n - 1][1]);
  let i = 0; while (keys[i + 1][0] < t) i++;
  const k0 = keys[Math.max(0, i - 1)], k1 = keys[i], k2 = keys[i + 1], k3 = keys[Math.min(n - 1, i + 2)];
  const u = (t - k1[0]) / (k2[0] - k1[0]);
  const out = {};
  for (const name in k1[1]) {
    const p0 = k0[1][name] ?? k1[1][name], p1 = k1[1][name], p2 = k2[1][name] ?? p1, p3 = k3[1][name] ?? p2;
    const u2 = u * u, u3 = u2 * u;
    out[name] = 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
  }
  return out;
}

// running, phase in cycles (one cycle = two strides)
export function runPose(ph, amp = 1) {
  const a = ph * Math.PI * 2, s = Math.sin(a), c = Math.cos(a);
  return { lean: 0.22, py: -0.03 + 0.05 * Math.abs(c),
    thL: -0.75 * s * amp, knL: (0.35 + 0.9 * Math.max(0, Math.sin(a + 1.2))) * amp, anL: 0.25,
    thR: 0.75 * s * amp, knR: (0.35 + 0.9 * Math.max(0, Math.sin(a + 1.2 + Math.PI))) * amp, anR: 0.25,
    arL: 0.7 * s * amp, elL: -1.3, arR: -0.7 * s * amp, elR: -1.3, arLz: 0.18, arRz: 0.18, twist: -0.12 * s * amp };
}
// walking out: slow, upright
export function walkPose(ph) {
  const a = ph * Math.PI * 2, s = Math.sin(a), c = Math.cos(a);
  return { lean: 0.03, py: -0.015 + 0.02 * Math.abs(c), head: -0.05,
    thL: -0.38 * s, knL: 0.12 + 0.45 * Math.max(0, Math.sin(a + 1.4)), anL: 0.08,
    thR: 0.38 * s, knR: 0.12 + 0.45 * Math.max(0, Math.sin(a + 1.4 + Math.PI)), anR: 0.08,
    arL: 0.32 * s, elL: -0.3, arR: -0.32 * s, elR: -0.3, arLz: 0.12, arRz: 0.12, twist: -0.06 * s };
}

// The strike, by the action's own time E (seconds; 0 is the boot on the ball). Root moves along local +z (forward).
const RUN_A = { lean: 0.24, py: -0.03, thL: -0.7, knL: 0.35, anL: 0.2, thR: 0.55, knR: 1.3, anR: 0.3, arL: 0.6, elL: -1.3, arR: -0.6, elR: -1.2, arLz: 0.18, arRz: 0.18, side: 0, twist: 0.1, head: 0, pz: -2.95, thLz: 0, thRz: 0 };
const KICK = [
  [-0.62, Object.assign({}, RUN_A, { thL: 0.5, knL: 1.2, thR: -0.6, knR: 0.35, arL: -0.6, arR: 0.6, twist: -0.1, pz: -3.6 })],
  [-0.48, RUN_A],
  [-0.34, Object.assign({}, RUN_A, { thL: 0.35, knL: 1.0, thR: -0.45, knR: 0.5, arL: -0.45, arR: 0.45, py: 0.05, twist: -0.1, pz: -2.1 })],
  [-0.2, Object.assign({}, RUN_A, { thL: 0.55, knL: 0.9, thR: -0.55, knR: 0.25, arL: -0.3, arR: 0.5, arLz: 0.6, py: -0.07, lean: 0.18, pz: -1.3 })],
  [-0.09, { lean: 0.05, py: -0.09, side: -0.18, twist: 0.25, head: 0.25, thL: -0.38, knL: 0.42, anL: 0.0, thR: 0.95, knR: 1.85, anR: 0.6,
    arL: -0.25, elL: -0.4, arLz: 1.05, arR: 0.55, elR: -0.7, arRz: 0.45, pz: -0.55, thLz: 0.05, thRz: 0.05 }],
  [-0.035, { lean: 0.0, py: -0.11, side: -0.24, twist: 0.15, head: 0.35, thL: -0.22, knL: 0.48, anL: 0.0, thR: 0.25, knR: 1.35, anR: 0.65,
    arL: -0.2, elL: -0.4, arLz: 1.25, arR: 0.4, elR: -0.6, arRz: 0.55, pz: -0.38, thLz: 0.05, thRz: 0.02 }],
  [0.0, { lean: -0.1, py: -0.1, side: -0.28, twist: 0.0, head: 0.4, thL: -0.06, knL: 0.45, anL: 0.0, thR: -0.5, knR: 0.32, anR: 0.75,
    arL: -0.15, elL: -0.35, arLz: 1.35, arR: 0.3, elR: -0.5, arRz: 0.6, pz: -0.3, thLz: 0.05, thRz: 0.0 }],
  [0.1, { lean: -0.2, py: -0.02, side: -0.25, twist: -0.2, head: 0.25, thL: 0.05, knL: 0.3, anL: -0.2, thR: -1.35, knR: 0.12, anR: 0.6,
    arL: 0.1, elL: -0.4, arLz: 1.2, arR: 0.0, elR: -0.6, arRz: 0.7, pz: -0.18, thLz: 0.05, thRz: 0.0 }],
  [0.3, { lean: 0.1, py: -0.04, side: -0.1, twist: -0.1, head: 0.1, thL: 0.45, knL: 0.7, anL: 0.2, thR: -0.45, knR: 0.35, anR: 0.3,
    arL: 0.3, elL: -0.8, arLz: 0.6, arR: -0.3, elR: -0.9, arRz: 0.4, pz: 0.55, thLz: 0, thRz: 0 }],
  [0.6, Object.assign({}, RUN_A, { lean: 0.12, pz: 2.1 })],
];
export function kickPose(E) {
  const k = keyed(KICK, E);
  k.pz = k.pz || 0;
  return k;
}

// the keeper: set, then the dive to his right (towards his local -x), by the action's E (the ball arrives about 0.6)
const SET = { py: -0.2, lean: 0.32, head: -0.2, thL: -0.42, knL: 0.8, anL: -0.3, thLz: 0.3, thR: -0.42, knR: 0.8, anR: -0.3, thRz: 0.3,
  arL: -0.55, elL: -0.6, arLz: 0.55, arR: -0.55, elR: -0.6, arRz: 0.55, roll: 0, side: 0, lat: 0, rise: 0, twist: 0 };
const DIVE = [
  [0.0, SET],
  [0.2, Object.assign({}, SET, { py: -0.26, lat: 0.15, side: 0.05 })],
  [0.32, Object.assign({}, SET, { py: -0.3, roll: 0.35, lat: -0.35, thL: -0.2, knL: 0.5, thR: -0.6, knR: 1.1, arL: -1.2, arR: -1.4, arRz: 0.9 })],
  [0.44, { py: 0.1, lean: 0.1, head: -0.3, roll: 1.05, lat: -1.0, rise: 0.25, thL: 0.2, knL: 0.25, anL: 0.5, thLz: 0.35, thR: -0.4, knR: 0.9, anR: 0.4, thRz: 0.1,
    arL: -2.5, elL: -0.2, arLz: 0.2, arR: -2.6, elR: -0.15, arRz: 0.25, side: 0.15, twist: 0.0 }],
  [0.6, { py: 0.25, lean: 0.0, head: -0.4, roll: 1.38, lat: -1.7, rise: 0.45, thL: 0.25, knL: 0.15, anL: 0.6, thLz: 0.4, thR: 0.05, knR: 0.35, anR: 0.5, thRz: 0.15,
    arL: -2.95, elL: -0.05, arLz: 0.15, arR: -3.0, elR: -0.05, arRz: 0.12, side: 0.2, twist: 0.0 }],
  [0.85, { py: 0.0, lean: 0.1, head: -0.2, roll: 1.5, lat: -2.1, rise: 0.1, thL: 0.3, knL: 0.4, anL: 0.5, thLz: 0.3, thR: -0.1, knR: 0.6, anR: 0.4, thRz: 0.1,
    arL: -2.7, elL: -0.3, arLz: 0.3, arR: -2.8, elR: -0.3, arRz: 0.3, side: 0.15, twist: 0.0 }],
];
export function keeperPose(E) { return keyed(DIVE, E); }
export const keeperSet = (breath) => Object.assign({}, SET, { py: SET.py + 0.012 * Math.sin(breath), arLz: SET.arLz + 0.04 * Math.sin(breath * 1.3), arRz: SET.arRz + 0.04 * Math.sin(breath * 1.3 + 1) });

// standing tall: chest out, arms a little away, looking up into the lights
export const heroPose = (t) => ({ lean: -0.08, head: -0.32 - 0.04 * Math.sin(t), headY: 0.15, py: 0, thL: -0.05, thLz: 0.08, knL: 0.06, thR: 0.05, thRz: 0.08, knR: 0.06,
  arL: 0.05, arLz: 0.32 + 0.03 * Math.sin(t * 1.2), elL: -0.25, arR: 0.05, arRz: 0.32 + 0.03 * Math.sin(t * 1.2 + 1), elR: -0.25, twist: 0.1 });

// where the keeper's root goes for his dive: lat metres along his local +x (negative: to his right), and up
export function keeperRoot(base, ry, k) {
  const lat = k.lat || 0;
  return new THREE.Vector3(base.x + lat * Math.cos(ry), base.y + (k.rise || 0), base.z - lat * Math.sin(ry));
}
export { clamp, lerp };
