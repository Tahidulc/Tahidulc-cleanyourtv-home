// The ground at night, all procedural: a striped pitch with its lines (one shader), a two-tier bowl of stands round a
// rounded rectangle with a short roof, the crowd as points with camera flashes, LED boards, four floodlight masts with
// their lamps, glare and beams, both goals with nets that ripple, and a patch of grass blades for the close shots.
// World: the pitch's centre at the origin, its length along x (goals at x = +-52.5), the main stand's side +z, metres.
import * as THREE from '../vendor/three.module.js';
import { U, OUT, FOG, HASH, TAU, rng, shader, additive, glows, clamp } from './kit.js';

export const PITCH = { L: 52.5, W: 34 };
const A = 59.5, B = 40, PE = 5;             // the stands' inner edge: a rounded rectangle (superellipse power PE)
export const MASTS = [[70, -78], [-70, -78], [70, 78], [-70, 78]].map(([x, z]) => new THREE.Vector3(x, 63, z));
export const BALL0 = new THREE.Vector3(36.5, 0.11, -5.5);         // where the strike is taken
export const GOAL_X = 52.5;

// the inner edge, resampled evenly by length: [{x, z, nx, nz}], and its length
function edgePath(n) {
  const dense = [], M = 4000;
  for (let i = 0; i < M; i++) {
    const a = (i / M) * TAU, c = Math.cos(a), s = Math.sin(a);
    const x = A * Math.sign(c) * Math.pow(Math.abs(c), 2 / PE), z = B * Math.sign(s) * Math.pow(Math.abs(s), 2 / PE);
    dense.push([x, z]);
  }
  const cum = [0];
  for (let i = 1; i <= M; i++) { const p = dense[i % M], q = dense[i - 1]; cum.push(cum[i - 1] + Math.hypot(p[0] - q[0], p[1] - q[1])); }
  const L = cum[M], out = [];
  let j = 0;
  for (let k = 0; k < n; k++) {
    const target = (k / n) * L;
    while (cum[j + 1] < target) j++;
    const f = (target - cum[j]) / (cum[j + 1] - cum[j]), p = dense[j], q = dense[(j + 1) % M];
    const x = p[0] + (q[0] - p[0]) * f, z = p[1] + (q[1] - p[1]) * f;
    // the outward normal, from the gradient of |x/A|^PE + |z/B|^PE
    let nx = (PE / A) * Math.pow(Math.abs(x / A), PE - 1) * Math.sign(x), nz = (PE / B) * Math.pow(Math.abs(z / B), PE - 1) * Math.sign(z);
    const nl = Math.hypot(nx, nz); nx /= nl; nz /= nl;
    out.push({ x, z, nx, nz });
  }
  return { pts: out, len: L };
}

// a band round the ground: the section [[offset outwards, height], ...] swept along the inner edge
function sweep(path, sec, closed = true) {
  const P = path.pts, n = P.length, m = sec.length;
  const pos = [], aS = [], aSec = [], idx = [];
  const secLen = [0];
  for (let j = 1; j < m; j++) secLen.push(secLen[j - 1] + Math.hypot(sec[j][0] - sec[j - 1][0], sec[j][1] - sec[j - 1][1]));
  for (let i = 0; i <= n; i++) {
    const p = P[i % n];
    for (let j = 0; j < m; j++) {
      pos.push(p.x + p.nx * sec[j][0], sec[j][1], p.z + p.nz * sec[j][0]);
      aS.push(i / n); aSec.push(secLen[j]);
    }
  }
  for (let i = 0; i < n; i++) for (let j = 0; j < m - 1; j++) {
    const a = i * m + j, b = (i + 1) * m + j;
    idx.push(a, b, a + 1, a + 1, b, b + 1);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('aS', new THREE.Float32BufferAttribute(aS, 1));
  g.setAttribute('aSec', new THREE.Float32BufferAttribute(aSec, 1));
  g.setIndex(idx);
  g.computeVertexNormals();
  return g;
}

const VS_WORLD = `
  attribute float aS; attribute float aSec;
  varying vec3 vW; varying vec3 vN; varying float vS; varying float vSec; varying float vD;
  void main() {
    vec4 wp = modelMatrix * vec4(position, 1.0);
    vW = wp.xyz; vN = normalize(mat3(modelMatrix) * normal); vS = aS; vSec = aSec;
    vec4 mv = viewMatrix * wp; vD = length(mv.xyz);
    gl_Position = projectionMatrix * mv;
  }`;

export function makeStadium(Q) {
  const group = new THREE.Group();
  const R = rng(23);
  const path = edgePath(Q.low ? 160 : 240);
  const per = path.len;

  // ---------------------------------------------------------------- the pitch
  const pitchMat = shader({
    uniforms: { uLit: { value: 1 } },
    extensions: {},
    vertexShader: `
      varying vec3 vW; varying float vD;
      void main() { vec4 wp = modelMatrix * vec4(position, 1.0); vW = wp.xyz; vec4 mv = viewMatrix * wp; vD = length(mv.xyz); gl_Position = projectionMatrix * mv; }`,
    fragmentShader: `
      uniform float uLit;
      ${FOG}
      ${HASH}
      varying vec3 vW; varying float vD;
      float sdBox(vec2 p, vec2 b) { vec2 d = abs(p) - b; return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0); }
      float lines(vec2 p) {
        float d = abs(sdBox(p, vec2(52.5, 34.0)));
        d = min(d, length(vec2(p.x, max(abs(p.y) - 34.0, 0.0))));
        d = min(d, abs(length(p) - 9.15));
        vec2 q = vec2(abs(p.x), p.y);
        d = min(d, abs(sdBox(q - vec2(44.25, 0.0), vec2(8.25, 20.16))));
        d = min(d, abs(sdBox(q - vec2(49.75, 0.0), vec2(2.75, 9.16))));
        float arc = abs(length(q - vec2(41.5, 0.0)) - 9.15); d = min(d, q.x < 36.0 ? arc : 1e3);
        vec2 cq = abs(p) - vec2(52.5, 34.0); float ca = abs(length(cq) - 1.0); d = min(d, (cq.x < 0.0 && cq.y < 0.0) ? ca : 1e3);
        d = min(d, max(0.0, length(p) - 0.12));
        d = min(d, max(0.0, length(q - vec2(41.5, 0.0)) - 0.12));
        return d;
      }
      void main() {
        vec2 p = vW.xz;
        float stripe = mod(floor((p.x + 52.5) / 5.25), 2.0);
        vec3 g1 = vec3(0.075, 0.2, 0.07), g2 = vec3(0.1, 0.26, 0.09);
        vec3 col = mix(g1, g2, stripe);
        // close up: tufts and blades (faded where a pixel covers more than a few centimetres)
        float px = length(fwidth(p));
        float fine = clamp(1.0 - px * 12.0, 0.0, 1.0);
        float n1 = hash2(floor(p * 40.0)), n2 = hash2(floor(p * 9.0) + 7.0);
        col *= 1.0 + fine * (n1 - 0.5) * 0.45 + (n2 - 0.5) * 0.12 * clamp(1.0 - px * 2.0, 0.0, 1.0);
        // the floodlights' throw: even, a touch brighter in the middle, darker beyond the touchlines
        float lit = 1.05 - 0.25 * smoothstep(30.0, 70.0, length(p * vec2(0.8, 1.2)));
        col *= lit * uLit * 1.5;
        float d = lines(p), w = 0.06, aa = max(px * 0.7, 0.004);
        float m = 1.0 - smoothstep(w - aa, w + aa, d);
        m *= clamp(0.12 / max(px, 0.001), 0.25, 1.0);
        col = mix(col, vec3(0.78, 0.82, 0.8) * uLit * 1.3, m * 0.92);
        gl_FragColor = vec4(fogged(col, vD), 1.0);
        ${OUT}
      }`,
  });
  const pitch = new THREE.Mesh(new THREE.PlaneGeometry(170, 140).rotateX(-Math.PI / 2), pitchMat);
  pitch.frustumCulled = false;
  group.add(pitch);

  // ---------------------------------------------------------------- the stands
  const LOWER = [[2, 0.6], [4, 1.6], [27, 14.5]], UPPER = [[27.6, 17.4], [48, 35], [48.4, 39]];
  const standMat = shader({
    uniforms: { uPerim: { value: per }, uLit: { value: 1 } },
    vertexShader: VS_WORLD,
    fragmentShader: `
      uniform float uPerim, uLit;
      ${FOG}
      varying vec3 vW; varying vec3 vN; varying float vS; varying float vSec; varying float vD;
      void main() {
        vec3 n = normalize(vN);
        float r = vSec / 0.82;
        float aa = fwidth(r);
        float row = fract(r);
        float steps = mix(0.55 + 0.45 * smoothstep(0.1, 0.25, row) * (1.0 - smoothstep(0.75, 0.9, row)), 0.75, clamp(aa * 1.5, 0.0, 1.0));
        float u = vS * uPerim / 13.0;
        float ai = abs(fract(u) - 0.5);
        float aisle = 1.0 - smoothstep(0.035, 0.045 + fwidth(u), 0.5 - ai);
        vec3 toC = normalize(vec3(-vW.x, 0.0, -vW.z) + vec3(0.0, 1.3, 0.0));
        float lit = 0.3 + 0.7 * max(dot(n, toC), 0.0);
        lit *= mix(1.25, 0.6, clamp(vW.y / 38.0, 0.0, 1.0));
        vec3 seat = vec3(0.045, 0.05, 0.075);
        vec3 col = seat * steps * lit * 1.4 + vec3(0.07, 0.075, 0.09) * aisle * lit;
        gl_FragColor = vec4(fogged(col * uLit, vD), 1.0);
        ${OUT}
      }`,
  });
  const stands = new THREE.Group();
  stands.add(new THREE.Mesh(sweep(path, LOWER), standMat));
  stands.add(new THREE.Mesh(sweep(path, UPPER), standMat));
  // the roof: a short cantilever, dark underneath, its front edge lit
  const roofMat = shader({
    vertexShader: VS_WORLD,
    fragmentShader: `${FOG}
      varying vec3 vW; varying vec3 vN; varying float vS; varying float vSec; varying float vD;
      void main() { vec3 c = vec3(0.02, 0.022, 0.03) + vec3(0.5, 0.55, 0.7) * smoothstep(17.9, 18.4, vSec) * 0.22; gl_FragColor = vec4(fogged(c, vD), 1.0); ${OUT} }`,
    side: THREE.DoubleSide,
  });
  stands.add(new THREE.Mesh(sweep(path, [[48.4, 39], [48.6, 40.6], [30.5, 41.6], [30.2, 41.0]]), roofMat));
  // LED ribbons: the upper tier's front and the boards round the pitch (abstract light, no lettering)
  const ledMat = (k) => shader({
    side: THREE.DoubleSide,
    uniforms: { uPerim: { value: per }, uLed: { value: 1 } },
    vertexShader: VS_WORLD,
    fragmentShader: `
      uniform float uPerim, uLed, uTime;
      ${FOG}
      varying vec3 vW; varying vec3 vN; varying float vS; varying float vSec; varying float vD;
      void main() {
        float u = vS * uPerim, h = vSec;
        float wave = 0.5 + 0.5 * sin(u * ${k ? '0.09' : '0.22'} - uTime * ${k ? '1.6' : '2.4'});
        vec3 c = mix(vec3(0.05, 0.12, 0.55), vec3(0.2, 0.32, 0.75), wave);
        float chev = step(0.72, fract((u + abs(h - ${k ? '1.4' : '0.45'}) * 2.0) * ${k ? '0.06' : '0.18'} - uTime * 0.7));
        c = mix(c, vec3(0.85, 0.9, 1.0), chev * ${k ? '0.3' : '0.55'});
        float edge = smoothstep(0.0, 0.06, h) * (1.0 - smoothstep(${k ? '2.7' : '0.86'}, ${k ? '2.8' : '0.92'}, h));
        gl_FragColor = vec4(fogged(c * uLed * edge * ${k ? '0.3' : '0.85'}, vD), 1.0);
        ${OUT}
      }`,
  });
  const boardsMat = ledMat(0), fasciaMat = ledMat(1);
  stands.add(new THREE.Mesh(sweep(path, [[-2.6, 0.0], [-2.6, 0.92]]), boardsMat));
  stands.add(new THREE.Mesh(sweep(path, [[27.05, 14.5], [27.05, 17.3]]), fasciaMat));
  stands.children.forEach((c) => { c.frustumCulled = false; });
  group.add(stands);

  // ---------------------------------------------------------------- the crowd, and their camera flashes
  const P = path.pts, n = P.length;
  const at = (s, off, tier) => {
    const f = s * n, i = Math.floor(f) % n, k = f - Math.floor(f), p = P[i], q = P[(i + 1) % n];
    const x = p.x + (q.x - p.x) * k, z = p.z + (q.z - p.z) * k, nx = p.nx + (q.nx - p.nx) * k, nz = p.nz + (q.nz - p.nz) * k;
    const T = tier ? UPPER : LOWER, a = tier ? T[0] : T[1], b = tier ? T[1] : T[2];
    const u = (off - a[0]) / (b[0] - a[0]), h = a[1] + (b[1] - a[1]) * u;
    return [x + nx * off, h, z + nz * off];
  };
  const NC = Q.low ? 9000 : 22000;
  const cp = new Float32Array(NC * 3), cc = new Float32Array(NC * 3), cr = new Float32Array(NC * 4);
  const palette = [[0.06, 0.065, 0.08], [0.1, 0.1, 0.11], [0.04, 0.045, 0.06], [0.26, 0.06, 0.07], [0.06, 0.09, 0.24], [0.42, 0.42, 0.44], [0.3, 0.26, 0.1], [0.16, 0.14, 0.13]];
  let c = 0;
  while (c < NC) {
    const s = R(), tier = R() < 0.55 ? 0 : 1;
    if (Math.abs(((s * per) / 13) % 1 - 0.5) > 0.455) continue;   // the aisles stay clear
    const T = tier ? UPPER : LOWER, a = tier ? T[0][0] : T[1][0], b = tier ? T[1][0] : T[2][0];
    const off = a + 0.4 + R() * (b - a - 0.8);
    const w = at(s, off, tier);
    cp.set([w[0], w[1] + 0.5 + R() * 0.15, w[2]], c * 3);
    const k = R() < 0.62 ? Math.floor(R() * 3) : 3 + Math.floor(R() * 5), pc = palette[k], v = 0.75 + R() * 0.5;
    cc.set([pc[0] * v, pc[1] * v, pc[2] * v], c * 3);
    cr.set([R(), R(), R(), R()], c * 4);
    c++;
  }
  const cg = new THREE.BufferGeometry();
  cg.setAttribute('position', new THREE.BufferAttribute(cp, 3));
  cg.setAttribute('aColor', new THREE.BufferAttribute(cc, 3));
  cg.setAttribute('aRnd', new THREE.BufferAttribute(cr, 4));
  const crowdMat = shader({
    uniforms: { uBright: { value: 1 }, uBob: { value: 1 } },
    vertexShader: `
      attribute vec3 aColor; attribute vec4 aRnd;
      uniform float uTime, uHalfH, uBright, uBob;
      varying vec3 vCol; varying vec3 vSkin; varying float vBig; varying float vD;
      void main() {
        vec3 p = position;
        p.y += 0.07 * uBob * sin(uTime * (2.0 + aRnd.x * 4.0) + aRnd.y * 40.0);
        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_Position = projectionMatrix * mv;
        float s = 0.62 * projectionMatrix[1][1] * uHalfH / -mv.z;
        gl_PointSize = clamp(s, 1.0, 48.0);
        float lit = mix(1.25, 0.6, clamp(p.y / 36.0, 0.0, 1.0));
        vCol = aColor * uBright * lit * min(1.0, s * s);
        vSkin = mix(vec3(0.3, 0.21, 0.16), vec3(0.12, 0.08, 0.06), aRnd.z) * uBright * lit;
        vBig = smoothstep(3.0, 7.0, s);
        vD = length(mv.xyz);
      }`,
    fragmentShader: `
      ${FOG}
      varying vec3 vCol; varying vec3 vSkin; varying float vBig; varying float vD;
      void main() {
        vec2 q = gl_PointCoord * 2.0 - 1.0;
        // near enough, a person: a head over shoulders; far off, a soft dot
        float head = length(q - vec2(0.0, -0.52)) - 0.27;
        float body = length((q - vec2(0.0, 0.5)) * vec2(0.6, 0.85)) - 0.42;
        float d = mix(length(q) - 0.85, min(head, body), vBig);
        if (d > 0.0) discard;
        vec3 c = mix(vCol, vSkin, step(head, body) * vBig) * (1.0 - 0.3 * max(q.y, 0.0));
        gl_FragColor = vec4(fogged(c, vD), 1.0);
        ${OUT}
      }`,
  });
  const crowd = new THREE.Points(cg, crowdMat); crowd.frustumCulled = false;
  group.add(crowd);
  // the flashes: a share of the crowd again, drawn only in the instant each one flashes (else no size at all)
  const NF = Q.low ? 1600 : 4000;
  const fp = new Float32Array(NF * 3), fr = new Float32Array(NF * 2);
  for (let i = 0; i < NF; i++) { const j = Math.floor(R() * NC); fp.set([cp[j * 3], cp[j * 3 + 1] + 0.3, cp[j * 3 + 2]], i * 3); fr.set([R(), R()], i * 2); }
  const fg = new THREE.BufferGeometry();
  fg.setAttribute('position', new THREE.BufferAttribute(fp, 3));
  fg.setAttribute('aRnd', new THREE.BufferAttribute(fr, 2));
  const flashMat = additive({
    uniforms: { uFlash: { value: 0.1 }, uRate: { value: 3 } },
    vertexShader: `
      attribute vec2 aRnd;
      uniform float uTime, uHalfH, uPx, uFlash, uRate;
      varying float vF;
      void main() {
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        gl_Position = projectionMatrix * mv;
        float k = uTime * uRate * (0.6 + aRnd.x) + aRnd.y * 91.0;
        float slot = floor(k), ph = fract(k) / (uRate * (0.6 + aRnd.x));
        float h = fract(sin(slot * 12.9898 + aRnd.x * 437.585) * 43758.5453);
        float on = step(1.0 - uFlash, h) * exp(-ph / 0.035);
        vF = on;
        float s = 2.6 * projectionMatrix[1][1] * uHalfH / -mv.z;
        gl_PointSize = on > 0.02 ? clamp(s, 7.0 * uPx, 70.0 * uPx) : 0.0;
      }`,
    fragmentShader: `
      varying float vF;
      void main() {
        vec2 q = gl_PointCoord * 2.0 - 1.0; float r = dot(q, q);
        float g = exp(-r * 22.0) * 2.5 + 0.25 * exp(-r * 5.0);
        g += (exp(-abs(q.y) * 40.0) * exp(-abs(q.x) * 3.0) + exp(-abs(q.x) * 40.0) * exp(-abs(q.y) * 3.0)) * 0.8;
        g *= 1.0 - smoothstep(0.7, 1.0, r);
        gl_FragColor = vec4(vec3(1.0, 0.97, 0.92) * g * vF * 1.6, 1.0);
        ${OUT}
      }`,
  });
  const flashes = new THREE.Points(fg, flashMat); flashes.frustumCulled = false; flashes.renderOrder = 6;
  group.add(flashes);

  // ---------------------------------------------------------------- the floodlight masts
  const darkMat = shader({
    uniforms: { uCol: { value: new THREE.Color(0.03, 0.033, 0.04) } },
    vertexShader: `varying float vD; varying vec3 vN; void main() { vec4 mv = modelViewMatrix * vec4(position, 1.0); vD = length(mv.xyz); vN = normalize(normalMatrix * normal); gl_Position = projectionMatrix * mv; }`,
    fragmentShader: `uniform vec3 uCol; ${FOG} varying float vD; varying vec3 vN; void main() { vec3 c = uCol * (0.7 + 0.5 * abs(vN.x)); gl_FragColor = vec4(fogged(c, vD), 1.0); ${OUT} }`,
  });
  const LAMP_COLS = 7, LAMP_ROWS = 4;
  const lampG = glows(MASTS.length * (LAMP_COLS * LAMP_ROWS), { order: 7 });
  const glareG = glows(MASTS.length * 2, { order: 8, depthTest: false });
  const masts = [];
  MASTS.forEach((m, mi) => {
    const col = new THREE.Mesh(new THREE.CylinderGeometry(0.6, 1.3, m.y, 6), darkMat); col.position.set(m.x, m.y / 2 - 1, m.z); group.add(col);
    const head = new THREE.Group(); head.position.copy(m); head.lookAt(0, 0, 0); group.add(head);
    const frame = new THREE.Mesh(new THREE.BoxGeometry(15, 8.5, 0.7), darkMat); head.add(frame);
    const lamps = [];
    for (let r = 0; r < LAMP_ROWS; r++) for (let k = 0; k < LAMP_COLS; k++) {
      const lp = new THREE.Vector3(-6.3 + k * 2.1, 2.9 - r * 1.95, 0.5);
      head.updateMatrixWorld(true);
      lamps.push({ p: head.localToWorld(lp.clone()), row: r, col: k });
    }
    head.updateMatrixWorld(true);
    const fwd = new THREE.Vector3(0, 0, 1).applyQuaternion(head.quaternion);
    masts.push({ m, head, fwd, lamps, glarePos: m.clone().addScaledVector(fwd, 2) });
  });
  // the beams: faint cones of light from each head down onto the pitch
  const beamMat = additive({
    side: THREE.DoubleSide,
    uniforms: { uA: { value: 0.05 } },
    vertexShader: `varying vec3 vN; varying vec3 vV; varying vec2 vUv; void main() { vec4 mv = modelViewMatrix * vec4(position, 1.0); vN = normalize(normalMatrix * normal); vV = normalize(-mv.xyz); vUv = uv; gl_Position = projectionMatrix * mv; }`,
    fragmentShader: `uniform float uA; varying vec3 vN; varying vec3 vV; varying vec2 vUv;
      void main() { float f = pow(abs(dot(normalize(vN), vV)), 2.0); float along = pow(vUv.y, 1.6); gl_FragColor = vec4(vec3(0.8, 0.86, 1.0) * f * along * uA, 1.0); ${OUT} }`,
  });
  const beams = new THREE.Group();
  masts.forEach((M) => {
    const target = new THREE.Vector3(M.m.x * 0.25, 0, M.m.z * 0.15), L = M.m.distanceTo(target);
    const cone = new THREE.Mesh(new THREE.CylinderGeometry(5, 34, L, 24, 1, true), beamMat);
    cone.position.lerpVectors(M.m, target, 0.5);
    cone.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), M.m.clone().sub(target).normalize());
    beams.add(cone);
  });
  beams.renderOrder = 4;
  group.add(beams);
  group.add(lampG.mesh); group.add(glareG.mesh);
  // the haze of light over the bowl, as seen from above
  const hazeG = glows(1, { order: 3 });
  hazeG.set(0, new THREE.Vector3(0, 25, 0), 190, 110, [0.35, 0.42, 0.7], 0.35, 0);
  hazeG.touch();
  group.add(hazeG.mesh);

  // lamps on (row by row) and their glare, for this camera; on: 0..1 for each mast's rows, or a number for all
  const tmp = new THREE.Vector3();
  function lights(cam, on = 1, glareK = 1, sizeK = 1, lampK = 1) {
    let li = 0;
    masts.forEach((M, mi) => {
      tmp.copy(cam.position).sub(M.glarePos).normalize();
      const face = Math.max(0, M.fwd.dot(tmp));
      const vis = clamp((M.fwd.dot(tmp) + 0.15) / 0.35);
      for (const L of M.lamps) {
        const o = typeof on === 'function' ? on(mi, L.row, L.col) : on;
        lampG.set(li++, L.p, 0.95, 0.95, [1.0, 0.96, 0.9], (1.2 + 2.2 * Math.pow(face, 3)) * o * vis * lampK, 0);
      }
      const o = typeof on === 'function' ? on(mi, 1, 3) : on;
      glareG.set(mi * 2, M.glarePos, 30 * sizeK, 30 * sizeK, [0.85, 0.9, 1.0], Math.pow(face, 4) * 1.3 * o * glareK, 1);
      glareG.set(mi * 2 + 1, M.glarePos, 110 * sizeK, 2.6 * sizeK, [0.6, 0.75, 1.0], Math.pow(face, 6) * 1.1 * o * glareK, 2);
    });
    lampG.touch(); glareG.touch();
  }

  // ---------------------------------------------------------------- the goals and their nets
  const postMat = shader({
    uniforms: { uCol: { value: new THREE.Color(1.6, 1.6, 1.65) } },
    vertexShader: `varying float vD; varying vec3 vN; void main() { vec4 mv = modelViewMatrix * vec4(position, 1.0); vD = length(mv.xyz); vN = normalize(normalMatrix * normal); gl_Position = projectionMatrix * mv; }`,
    fragmentShader: `uniform vec3 uCol; ${FOG} varying float vD; varying vec3 vN; void main() { vec3 c = uCol * (0.45 + 0.55 * max(vN.y, 0.0) + 0.3 * abs(vN.x)); gl_FragColor = vec4(fogged(c, vD), 1.0); ${OUT} }`,
  });
  const makeNetMat = () => shader({
    transparent: true, depthWrite: false, side: THREE.DoubleSide,
    uniforms: { uHit: { value: new THREE.Vector4(0, 1.5, -2.8, 99) }, uAmp: { value: 0 } },
    vertexShader: `
      attribute vec3 aNet;   // (u, v) in metres on its panel, panel (0 back, 1 top, 2 side)
      uniform vec4 uHit; uniform float uAmp;
      varying vec2 vUv; varying float vD;
      void main() {
        vec3 p = position;
        vec3 hit = vec3(2.0, uHit.y, uHit.z);
        float d = length(p - hit), age = uHit.w;
        float push = (1.0 - exp(-age * 16.0)) * exp(-age * 2.6) * 0.95;
        float wave = 0.14 * sin(d * 5.0 - age * 15.0) * exp(-d * 0.55) * exp(-age * 1.8) * step(0.0, age);
        float k = exp(-d * d / 0.9) * push + wave;
        vec3 dir = aNet.z < 0.5 ? vec3(1.0, 0.0, 0.0) : aNet.z < 1.5 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, sign(p.z));
        float pin = aNet.z < 0.5 ? smoothstep(0.0, 0.5, p.y) * smoothstep(0.0, 0.4, 3.66 - abs(p.z)) * smoothstep(0.0, 0.3, 2.44 - p.y + 0.3) : 0.35 * smoothstep(0.0, 1.0, p.x);
        p += dir * k * uAmp * pin;
        vUv = aNet.xy;
        vec4 mv = modelViewMatrix * vec4(p, 1.0); vD = length(mv.xyz);
        gl_Position = projectionMatrix * mv;
      }`,
    fragmentShader: `
      ${FOG}
      varying vec2 vUv; varying float vD;
      void main() {
        vec2 q = mat2(0.7071, 0.7071, -0.7071, 0.7071) * vUv / 0.13;
        vec2 f = abs(fract(q) - 0.5);
        vec2 w = fwidth(q) * 0.9 + 0.035;
        float line = max(1.0 - smoothstep(0.0, w.x, 0.5 - f.x), 1.0 - smoothstep(0.0, w.y, 0.5 - f.y));
        float fade = clamp(0.1 / max(fwidth(q.x), 0.01), 0.3, 1.0);
        gl_FragColor = vec4(fogged(vec3(0.85, 0.88, 0.92) * 1.2, vD), line * 0.85 * fade + 0.03);
        ${OUT}
      }`,
  });
  const netMat = makeNetMat();
  function netGeo() {
    const pos = [], an = [], idx = [];
    const panel = (nu, nv, f, part) => {
      const o = pos.length / 3;
      for (let j = 0; j <= nv; j++) for (let i = 0; i <= nu; i++) { const [p, uv] = f(i / nu, j / nv); pos.push(...p); an.push(uv[0], uv[1], part); }
      for (let j = 0; j < nv; j++) for (let i = 0; i < nu; i++) { const a = o + j * (nu + 1) + i, b = a + nu + 1; idx.push(a, b, a + 1, a + 1, b, b + 1); }
    };
    panel(40, 14, (u, v) => [[2.0, v * 2.44, -3.66 + u * 7.32], [u * 7.32, v * 2.44]], 0);
    panel(40, 10, (u, v) => [[v * 2.0, 2.44, -3.66 + u * 7.32], [u * 7.32, v * 2.0]], 1);
    for (const s of [-1, 1]) panel(10, 14, (u, v) => [[u * 2.0, v * 2.44, s * 3.66], [u * 2.0, v * 2.44]], 2);
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('aNet', new THREE.Float32BufferAttribute(an, 3));
    g.setIndex(idx);
    return g;
  }
  const ng = netGeo();
  const goals = [];
  for (const sx of [1, -1]) {
    const G = new THREE.Group(); G.position.set(sx * GOAL_X, 0, 0); G.rotation.y = sx > 0 ? 0 : Math.PI;
    const r = 0.062;
    for (const z of [-3.66, 3.66]) { const p = new THREE.Mesh(new THREE.CylinderGeometry(r, r, 2.44 + r, 10), postMat); p.position.set(0, 1.22, z); G.add(p); }
    const bar = new THREE.Mesh(new THREE.CylinderGeometry(r, r, 7.32 + 2 * r, 10), postMat); bar.rotation.x = Math.PI / 2; bar.position.set(0, 2.44, 0); G.add(bar);
    const net = new THREE.Mesh(ng, sx > 0 ? netMat : makeNetMat()); net.frustumCulled = false; net.renderOrder = 3; G.add(net);
    group.add(G);
    goals.push({ G, net });
  }

  // ---------------------------------------------------------------- grass blades round the spot the strike is taken from
  const NB = Q.low ? 2000 : 14000;
  const blade = new THREE.BufferGeometry();
  blade.setAttribute('position', new THREE.Float32BufferAttribute([-0.0035, 0, 0, 0.0035, 0, 0, 0.0, 1, 0], 3));
  blade.setAttribute('normal', new THREE.Float32BufferAttribute([0, 0, 1, 0, 0, 1, 0, 0, 1], 3));
  blade.setAttribute('aH', new THREE.Float32BufferAttribute([0, 0, 1], 1));
  const bladeMat = shader({
    side: THREE.DoubleSide,
    uniforms: { uL: { value: new THREE.Vector3(0, 0.3, -1) } },
    vertexShader: `
      attribute float aH; uniform float uTime;
      varying float vH; varying float vD; varying vec3 vW;
      void main() {
        vec4 wp = modelMatrix * instanceMatrix * vec4(position, 1.0);
        wp.x += aH * aH * 0.012 * sin(uTime * 1.3 + wp.z * 3.0 + wp.x);
        vH = aH; vW = wp.xyz;
        vec4 mv = viewMatrix * wp; vD = length(mv.xyz);
        gl_Position = projectionMatrix * mv;
      }`,
    fragmentShader: `
      ${FOG}
      varying float vH; varying float vD; varying vec3 vW;
      void main() {
        float k = fract(sin(dot(floor(vW.xz * 50.0), vec2(12.9898, 78.233))) * 43758.5453);
        vec3 c = mix(vec3(0.02, 0.05, 0.02), mix(vec3(0.12, 0.3, 0.09), vec3(0.18, 0.36, 0.12), k), vH);
        c += vec3(0.5, 0.6, 0.55) * pow(vH, 8.0) * 0.45;
        gl_FragColor = vec4(fogged(c * 1.25, vD), 1.0);
        ${OUT}
      }`,
  });
  const blades = new THREE.InstancedMesh(blade, bladeMat, NB);
  const mtx = new THREE.Matrix4(), q = new THREE.Quaternion(), e = new THREE.Euler();
  for (let i = 0; i < NB; i++) {
    const r = (Q.low ? 3.0 : 4.5) * Math.sqrt(R()), a = R() * TAU;
    const h = 0.025 + R() * R() * 0.07;
    e.set((R() - 0.5) * 0.8, R() * TAU, (R() - 0.5) * 0.8); q.setFromEuler(e);
    mtx.compose(new THREE.Vector3(BALL0.x + r * Math.cos(a), 0, BALL0.z + r * Math.sin(a)), q, new THREE.Vector3(0.7 + R() * 0.8, h, 1));
    blades.setMatrixAt(i, mtx);
  }
  blades.frustumCulled = false;
  group.add(blades);

  return {
    group, pitch, pitchMat, stands, standMat, boardsMat, fasciaMat, crowd, crowdMat, flashes, flashMat, masts, lights,
    lampG, glareG, beams, beamMat, hazeG, goals, netMat, blades, darkMat, perimeter: per,
  };
}
