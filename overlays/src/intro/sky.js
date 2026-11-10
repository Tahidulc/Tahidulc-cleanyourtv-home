// The night sky: a field of stars (one draw), the Milky Way painted once into a texture on a sphere, and the
// constellation: a footballer striking a ball drawn in stars, each star lit on a note of the music. The sky group
// follows the camera (it is at infinity); the constellation hangs in it towards CDIR.
import * as THREE from '../vendor/three.module.js';
import { U, OUT, TAU, rng, additive, clamp } from './kit.js';

export const SKY_R = 900;
// where the constellation hangs: straight ahead of the main cameras (towards -z), 38 degrees up
export const CDIR = new THREE.Vector3(0, Math.sin(0.66), -Math.cos(0.66));
const C_DIST = 600, C_SCALE = 300;
const C_RIGHT = new THREE.Vector3(1, 0, 0), C_UP = new THREE.Vector3().crossVectors(C_RIGHT, CDIR).normalize();
// the plane's 2D figure units to the sky group's space
export const cpoint = (x, y) => CDIR.clone().multiplyScalar(C_DIST).addScaledVector(C_RIGHT, x * C_SCALE).addScaledVector(C_UP, (y - 0.45) * C_SCALE);

// the galactic plane's pole (the Milky Way is the great circle round it)
const GPOLE = new THREE.Vector3(0.55, 0.35, 0.76).normalize();
const GE1 = new THREE.Vector3().crossVectors(GPOLE, new THREE.Vector3(0, 1, 0)).normalize();
const GE2 = new THREE.Vector3().crossVectors(GPOLE, GE1).normalize();

export function makeSky(Q, T) {
  const group = new THREE.Group();
  const R = rng(11);

  // ---------------------------------------------------------------- the Milky Way, painted once
  const tw = Q.low ? 1024 : 2048, th = tw / 2;
  const cv = document.createElement('canvas'); cv.width = tw; cv.height = th;
  const g = cv.getContext('2d');
  const sky = g.createLinearGradient(0, 0, 0, th);
  sky.addColorStop(0, '#02030a'); sky.addColorStop(0.36, '#050816'); sky.addColorStop(0.47, '#0b1330'); sky.addColorStop(0.5, '#141c3c'); sky.addColorStop(0.56, '#05070f'); sky.addColorStop(1, '#020308');
  g.fillStyle = sky; g.fillRect(0, 0, tw, th);
  const tints = ['120,110,190', '150,120,220', '90,120,200', '200,150,190', '170,170,210', '230,170,150'];
  const blob = (d, rad, colr, a) => {
    const theta = Math.acos(clamp(d.y, -1, 1)), phi = (Math.atan2(d.z, -d.x) + TAU) % TAU;
    const px = (phi / TAU) * tw, py = (theta / Math.PI) * th;
    const sx = Math.min(6, 1 / Math.max(0.15, Math.sin(theta))), r = rad * th;
    for (const ox of [-tw, 0, tw]) {
      if (px + ox + r * sx < 0 || px + ox - r * sx > tw) continue;
      g.save(); g.translate(px + ox, py); g.scale(sx, 1);
      const rg = g.createRadialGradient(0, 0, 0, 0, 0, r);
      rg.addColorStop(0, `rgba(${colr},${a})`); rg.addColorStop(1, `rgba(${colr},0)`);
      g.fillStyle = rg; g.fillRect(-r, -r, 2 * r, 2 * r); g.restore();
    }
  };
  g.globalCompositeOperation = 'lighter';
  for (let i = 0; i < (Q.low ? 700 : 1400); i++) {
    const a = R() * TAU, sc = (R() + R() + R() - 1.5) * 0.16 * (0.6 + 0.4 * Math.sin(a * 2 + 1));
    const d = GE1.clone().multiplyScalar(Math.cos(a)).addScaledVector(GE2, Math.sin(a)).addScaledVector(GPOLE, sc).normalize();
    const core = Math.exp(-Math.pow(Math.sin((a - 1.2) / 2), 2) * 3);   // brighter towards its core
    blob(d, 0.012 + R() * 0.05, tints[Math.floor(R() * tints.length)], (0.02 + R() * 0.05) * (0.45 + core));
  }
  // dark lanes of dust along it
  g.globalCompositeOperation = 'source-over';
  for (let i = 0; i < (Q.low ? 500 : 1000); i++) {
    const a = R() * TAU, sc = 0.018 * Math.sin(a * 3 + 1) + (R() - 0.5) * 0.012;
    const d = GE1.clone().multiplyScalar(Math.cos(a)).addScaledVector(GE2, Math.sin(a)).addScaledVector(GPOLE, sc).normalize();
    blob(d, 0.004 + R() * 0.01, '4,5,12', 0.07);
  }
  // filtered as it was painted (sRGB), then made linear, with a little dither: no bands or grid where it is magnified
  const tex = new THREE.CanvasTexture(cv); tex.colorSpace = THREE.NoColorSpace;
  const milkyMat = new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false,
    uniforms: { map: { value: tex }, color: { value: new THREE.Color(1, 1, 1) } },
    vertexShader: 'varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }',
    fragmentShader: `uniform sampler2D map; uniform vec3 color; varying vec2 vUv;
      void main() {
        vec3 c = pow(texture2D(map, vUv).rgb, vec3(2.2)) * color;
        c += (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) * 0.0025;
        gl_FragColor = vec4(max(c, 0.0), 1.0);
        ${OUT}
      }`,
  });
  const milky = new THREE.Mesh(new THREE.SphereGeometry(SKY_R + 40, 48, 24), milkyMat);
  milky.renderOrder = -10;
  group.add(milky);

  // ---------------------------------------------------------------- the stars
  const N = Q.low ? 2600 : 5200;
  const pos = new Float32Array(N * 3), size = new Float32Array(N), colr = new Float32Array(N * 3), twk = new Float32Array(N * 2);
  for (let i = 0; i < N; i++) {
    let d;
    if (R() < 0.42) {   // along the Milky Way
      const a = R() * TAU, sc = (R() + R() - 1) * 0.14;
      d = GE1.clone().multiplyScalar(Math.cos(a)).addScaledVector(GE2, Math.sin(a)).addScaledVector(GPOLE, sc).normalize();
    } else {
      const z = R() * 2 - 1, a = R() * TAU, r = Math.sqrt(1 - z * z);
      d = new THREE.Vector3(r * Math.cos(a), z, r * Math.sin(a));
    }
    pos.set([d.x * SKY_R, d.y * SKY_R, d.z * SKY_R], i * 3);
    const m = Math.pow(R(), 7);
    size[i] = 1.3 + 7.5 * m + R() * 0.8;
    const k = R();
    const c = k < 0.25 ? [0.72, 0.8, 1.0] : k < 0.8 ? [0.95, 0.96, 1.0] : [1.0, 0.86, 0.7];
    const b = 0.35 + 0.65 * Math.sqrt(m) + 0.25 * R();
    colr.set([c[0] * b, c[1] * b, c[2] * b], i * 3);
    twk.set([R() * TAU, 0.6 + R() * 3.2], i * 2);
  }
  const sg = new THREE.BufferGeometry();
  sg.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  sg.setAttribute('aSize', new THREE.BufferAttribute(size, 1));
  sg.setAttribute('aColor', new THREE.BufferAttribute(colr, 3));
  sg.setAttribute('aTw', new THREE.BufferAttribute(twk, 2));
  const starMat = additive({
    uniforms: { uFade: { value: 1 }, uSizeK: { value: 1 } },
    vertexShader: `
      attribute float aSize; attribute vec3 aColor; attribute vec2 aTw;
      uniform float uTime, uPx, uFade, uSizeK;
      varying vec3 vCol;
      void main() {
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        gl_Position = projectionMatrix * mv;
        float tw = 0.62 + 0.38 * sin(uTime * aTw.y + aTw.x);
        float s = aSize * uSizeK * uPx;
        gl_PointSize = max(1.5, s);
        vCol = aColor * tw * uFade * min(1.0, s / 1.5);
      }`,
    fragmentShader: `
      varying vec3 vCol;
      void main() {
        vec2 p = gl_PointCoord * 2.0 - 1.0; float r = dot(p, p);
        float a = exp(-r * 9.0) + 0.22 * exp(-r * 3.0);
        a *= 1.0 - smoothstep(0.7, 1.0, r);
        gl_FragColor = vec4(vCol * a * 1.6, 1.0);
        ${OUT}
      }`,
  });
  const stars = new THREE.Points(sg, starMat);
  stars.frustumCulled = false; stars.renderOrder = -5;
  group.add(stars);

  // ---------------------------------------------------------------- the constellation
  const C = makeConstellation(T);
  group.add(C.group);

  return { group, stars, starGeo: sg, starMat, milky, milkyMat, C };
}

// ---------------------------------------------------------------- the footballer in stars
// 2D figure units: about 1.1 high, the striker side-on, his right foot through the ball
const FIG = {
  head: [0.05, 1.02], neck: [0.06, 0.88], shR: [0.2, 0.83], elR: [0.38, 0.92], hR: [0.52, 1.02],
  shL: [-0.1, 0.83], elL: [-0.28, 0.72], hL: [-0.4, 0.6], hip: [0.0, 0.48],
  kS: [-0.04, 0.24], fS: [-0.14, -0.02], kK: [0.24, 0.36], fK: [0.47, 0.22],
};
const ORDER = ['head', 'neck', 'shR', 'elR', 'hR', 'shL', 'elL', 'hL', 'hip', 'kS', 'fS', 'kK', 'fK'];
const BONES = [['head', 'neck'], ['neck', 'shR'], ['shR', 'elR'], ['elR', 'hR'], ['neck', 'shL'], ['shL', 'elL'], ['elL', 'hL'], ['neck', 'hip'], ['hip', 'kS'], ['kS', 'fS'], ['hip', 'kK'], ['kK', 'fK']];
export const BALL_C = [0.66, 0.2], BALL_R = 0.085;

function makeConstellation(T) {
  const group = new THREE.Group();
  const lit = {};   // star -> time lit
  const st = T.stars;
  // the figure's thirteen stars, one an eighth note, then the ball's in a quick glitter
  ORDER.forEach((k, i) => { lit[k] = st.t0 + i * st.step; });
  const ballPts = [];
  for (let i = 0; i < 8; i++) { const a = (i / 8) * TAU + 0.2; ballPts.push([BALL_C[0] + BALL_R * Math.cos(a), BALL_C[1] + BALL_R * Math.sin(a)]); }
  for (let i = 0; i < 5; i++) { const a = (i / 5) * TAU + Math.PI / 2; ballPts.push([BALL_C[0] + BALL_R * 0.45 * Math.cos(a), BALL_C[1] + BALL_R * 0.45 * Math.sin(a)]); }
  const bt0 = st.ball[0], bstep = (st.ball[1] - st.ball[0]) / ballPts.length;
  // the stars
  const pts = [], times = [], sizes = [];
  ORDER.forEach((k) => { pts.push(FIG[k]); times.push(lit[k]); sizes.push(k === 'head' ? 15 : 11); });
  ballPts.forEach((p, i) => { pts.push(p); times.push(bt0 + i * bstep); sizes.push(i < 8 ? 9 : 7); });
  const sp = new Float32Array(pts.length * 3), sa = new Float32Array(pts.length * 2);
  pts.forEach((p, i) => { const w = cpoint(p[0], p[1]); sp.set([w.x, w.y, w.z], i * 3); sa.set([times[i], sizes[i]], i * 2); });
  const sgeo = new THREE.BufferGeometry();
  sgeo.setAttribute('position', new THREE.BufferAttribute(sp, 3));
  sgeo.setAttribute('aLit', new THREE.BufferAttribute(sa, 2));
  const smat = additive({
    uniforms: { uA: { value: 1 }, uBoost: { value: 0 } },
    vertexShader: `
      attribute vec2 aLit; uniform float uTime, uPx, uA, uBoost;
      varying float vB; varying float vF;
      void main() {
        vec4 mv = modelViewMatrix * vec4(position, 1.0); gl_Position = projectionMatrix * mv;
        float since = uTime - aLit.x;
        float on = smoothstep(0.0, 0.05, since);
        float pop = since > 0.0 ? exp(-since / 0.35) : 0.0;
        vB = (0.12 + 0.88 * on) * uA * (0.85 + 0.15 * sin(uTime * 3.0 + aLit.x * 7.0)) + uBoost;
        vF = pop;
        gl_PointSize = aLit.y * uPx * (1.0 + 1.6 * pop + 0.4 * uBoost) * (0.55 + 0.45 * on);
      }`,
    fragmentShader: `
      varying float vB; varying float vF;
      void main() {
        vec2 p = gl_PointCoord * 2.0 - 1.0; float r = dot(p, p);
        float core = exp(-r * 14.0) * 1.6 + 0.3 * exp(-r * 3.5);
        float sp = (exp(-abs(p.y) * 30.0) * exp(-abs(p.x) * 2.5) + exp(-abs(p.x) * 30.0) * exp(-abs(p.y) * 2.5)) * (0.5 + vF);
        float a = (core + sp) * (1.0 - smoothstep(0.75, 1.0, r));
        vec3 c = mix(vec3(0.8, 0.88, 1.0), vec3(1.0, 0.95, 0.8), vF);
        gl_FragColor = vec4(c * a * (vB + vF * 1.5), 1.0);
        ${OUT}
      }`,
  });
  const sObj = new THREE.Points(sgeo, smat); sObj.frustumCulled = false; sObj.renderOrder = 2;
  group.add(sObj);

  // the lines, as thin glowing quads in the constellation's plane, drawn from star to star
  const segs = [];
  BONES.forEach(([a, b]) => segs.push([FIG[a], FIG[b], Math.max(lit[a], lit[b]), 0.28]));
  for (let i = 0; i < 8; i++) segs.push([ballPts[i], ballPts[(i + 1) % 8], bt0 + 0.25 + i * 0.03, 0.2]);
  for (let i = 0; i < 5; i++) segs.push([ballPts[8 + i], ballPts[8 + ((i + 2) % 5)], bt0 + 0.4 + i * 0.03, 0.2]);
  const lp = [], la = [], idx = [];
  const width = 0.0085;
  segs.forEach(([a, b, t0, dur], i) => {
    const dx = b[0] - a[0], dy = b[1] - a[1], L = Math.hypot(dx, dy), nx = -dy / L, ny = dx / L;
    const sh = 0.03;   // the line stops short of each star
    const a2 = [a[0] + dx * sh / L * 0.9, a[1] + dy * sh / L * 0.9], b2 = [b[0] - dx * sh / L * 0.9, b[1] - dy * sh / L * 0.9];
    const corners = [[a2, -1, 0], [a2, 1, 0], [b2, 1, 1], [b2, -1, 1]];
    corners.forEach(([p, side, along]) => {
      const w = cpoint(p[0] + nx * width * side, p[1] + ny * width * side);
      lp.push(w.x, w.y, w.z); la.push(along, side, t0, dur);
    });
    const o = i * 4; idx.push(o, o + 1, o + 2, o, o + 2, o + 3);
  });
  const lgeo = new THREE.BufferGeometry();
  lgeo.setAttribute('position', new THREE.Float32BufferAttribute(lp, 3));
  lgeo.setAttribute('aLine', new THREE.Float32BufferAttribute(la, 4));
  lgeo.setIndex(idx);
  const lmat = additive({
    side: THREE.DoubleSide,
    uniforms: { uA: { value: 1 } },
    vertexShader: `
      attribute vec4 aLine; varying vec4 vL;
      void main() { vL = aLine; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: `
      uniform float uTime, uA; varying vec4 vL;
      void main() {
        float prog = clamp((uTime - vL.z) / vL.w, 0.0, 1.0);
        float drawn = 1.0 - smoothstep(prog - 0.04, prog, vL.x);
        if (prog >= 1.0) drawn = 1.0;
        float x = vL.y;
        float a = exp(-x * x * 14.0) * 0.9 + exp(-x * x * 2.5) * 0.25;
        float head = prog < 1.0 ? exp(-pow((vL.x - prog) * 14.0, 2.0)) * 1.5 : 0.0;
        vec3 c = vec3(0.62, 0.74, 1.0) * a * drawn * uA * (0.7 + head) + vec3(1.0, 0.9, 0.7) * head * a * uA;
        gl_FragColor = vec4(c, 1.0);
        ${OUT}
      }`,
  });
  const lObj = new THREE.Mesh(lgeo, lmat); lObj.frustumCulled = false; lObj.renderOrder = 1;
  group.add(lObj);
  return { group, starMat: smat, lineMat: lmat, ballStar: cpoint(BALL_C[0], BALL_C[1]), centre: cpoint(0.2, 0.5) };
}
