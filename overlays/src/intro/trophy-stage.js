// The climax: the competition's trophy (our own model, trophy-models.js) on a dark stage under the stars. The ball's star
// bursts; its light gathers into the trophy's outline (points sampled on the model where it turns away from the camera);
// the trophy forms from the foot up behind a line of light; it turns, sparkles, with rays of light behind it.
import * as THREE from '../vendor/three.module.js';
import { RoomEnvironment } from '../vendor/RoomEnvironment.js';
import { trophyKit } from '../kickoff/trophy-models.js';
import { U, OUT, TAU, rng, shader, additive, glows, clamp, ss, eo, eio, lerp } from './kit.js';

export const TROPHY_H = 2.0;

export function makeTrophyStage(renderer, kind, sky, Q) {
  const scene = new THREE.Scene();
  const pmrem = new THREE.PMREMGenerator(renderer);
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.03).texture;
  pmrem.dispose();
  scene.environmentIntensity = 0.85;
  const key = new THREE.DirectionalLight(0xffffff, 2.0); key.position.set(-2.5, 3, 3); scene.add(key);
  const rim = new THREE.DirectionalLight(0xcfe0ff, 2.4); rim.position.set(3, 1.8, -2.5); scene.add(rim);
  const rim2 = new THREE.DirectionalLight(0xffd9a8, 1.4); rim2.position.set(-3, 0.5, -2.5); scene.add(rim2);
  const fill = new THREE.DirectionalLight(0xfff2dd, 0.5); fill.position.set(2, -1, 3); scene.add(fill);
  const cam = new THREE.PerspectiveCamera(26, 16 / 9, 0.1, 2000);

  // the stars behind (the sky's own stars and their material)
  const stars = new THREE.Points(sky.starGeo, sky.starMat); stars.frustumCulled = false; stars.renderOrder = -5;
  scene.add(stars);

  // ---------------------------------------------------------------- the trophy
  const kit = trophyKit();
  const T = (kit.BUILD[kind] || kit.BUILD.generic)();
  const box0 = new THREE.Box3().setFromObject(T);
  T.add(kit.ribbonsFor(kind));
  const h = box0.max.y - box0.min.y, w = Math.max(box0.max.x - box0.min.x, box0.max.z - box0.min.z);
  const s = TROPHY_H / Math.max(h, w * 0.92);
  const inner = new THREE.Group(); inner.add(T); T.position.y = -box0.min.y;
  inner.scale.setScalar(s);
  const pivot = new THREE.Group(); pivot.add(inner); scene.add(pivot);
  const HT = h * s;     // its height in the world
  const clip = new THREE.Plane(new THREE.Vector3(0, -1, 0), 10);
  const ribbons = [];
  T.traverse((o) => {
    if (o.isMesh) { o.material.clippingPlanes = [clip]; o.frustumCulled = false; }
    if (o.userData && o.userData.rib) ribbons.push(o);
  });
  const sway = kind === 'bundesliga';
  const ry0 = sway ? 0 : -0.5;

  // ---------------------------------------------------------------- the outline, sampled where the surface turns away
  pivot.rotation.y = ry0; pivot.updateMatrixWorld(true);
  const view = new THREE.Vector3(0, 0, 1);    // towards the camera, in the world
  const inv = new THREE.Matrix4().copy(T.matrixWorld).invert();
  const viewT = view.clone().transformDirection(inv);
  const R = rng(77);
  const edge = [], body = [], facing = [];
  const pv = new THREE.Vector3(), nv = new THREE.Vector3(), nm = new THREE.Matrix3();
  T.traverse((o) => {
    if (!o.isMesh || o.userData.rib) return;
    const M = new THREE.Matrix4().multiplyMatrices(inv, o.matrixWorld); nm.getNormalMatrix(M);
    const P = o.geometry.attributes.position, N = o.geometry.attributes.normal;
    const step = Math.max(1, Math.floor(P.count / 3000));
    for (let i = 0; i < P.count; i += step) {
      pv.fromBufferAttribute(P, i).applyMatrix4(M);
      if (N) { nv.fromBufferAttribute(N, i).applyMatrix3(nm).normalize(); const d = nv.dot(viewT); if (Math.abs(d) < 0.16) edge.push(pv.clone()); else if (d > 0.75) facing.push(pv.clone()); }
      body.push(pv.clone());
    }
  });
  const pick = (arr, n) => { const out = []; for (let i = 0; i < n && arr.length; i++) out.push(arr[Math.floor(R() * arr.length)]); return out; };
  const NE = Q.low ? 520 : 900, NBODY = Q.low ? 160 : 300;
  const targets = pick(edge, NE).concat(pick(body, NBODY));
  // where the burst starts: the ball's star, up and behind (world), in the trophy's own space
  const origin = new THREE.Vector3(0, HT * 0.9, -2.5).applyMatrix4(inv);
  const NP = targets.length;
  const aT = new Float32Array(NP * 3), aB = new Float32Array(NP * 3), aR = new Float32Array(NP * 4);
  targets.forEach((p, i) => {
    aT.set([p.x, p.y, p.z], i * 3);
    const z = R() * 2 - 1, a = R() * TAU, r = Math.sqrt(1 - z * z), rad = (1.2 + R() * 3.2) / s;
    aB.set([r * Math.cos(a) * rad, z * rad * 0.7, r * Math.sin(a) * rad], i * 3);
    aR.set([R(), R(), R(), R()], i * 4);
  });
  const cg = new THREE.BufferGeometry();
  cg.setAttribute('position', new THREE.BufferAttribute(aT, 3));
  cg.setAttribute('aBurst', new THREE.BufferAttribute(aB, 3));
  cg.setAttribute('aRnd', new THREE.BufferAttribute(aR, 4));
  const convMat = additive({
    uniforms: { uT: { value: 0 }, uOrigin: { value: origin }, uOut: { value: 0 } },
    vertexShader: `
      attribute vec3 aBurst; attribute vec4 aRnd;
      uniform float uT, uPx, uOut, uTime; uniform vec3 uOrigin;
      varying float vB; varying float vPop;
      void main() {
        float b = 1.0 - exp(-uT * 2.6);
        vec3 burst = uOrigin + aBurst * b;
        float k0 = 0.12 + aRnd.x * 0.38, k1 = k0 + 0.55 + aRnd.y * 0.22;
        float k = clamp((uT - k0) / (k1 - k0), 0.0, 1.0); k = k * k * (3.0 - 2.0 * k);
        vec3 p = mix(burst, position, k);
        float sw = (1.0 - k) * (1.4 + aRnd.z);       // a swirl round the upright as they gather
        float cs = cos(sw), sn = sin(sw);
        p.xz = mat2(cs, -sn, sn, cs) * p.xz;
        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_Position = projectionMatrix * mv;
        float arrive = uT - k1;
        vPop = arrive > 0.0 ? exp(-arrive / 0.25) : 0.0;
        float fo = clamp((uOut - aRnd.w * 0.5) / 0.5, 0.0, 1.0);
        vB = (0.55 + 0.45 * sin(uTime * (3.0 + aRnd.z * 5.0) + aRnd.w * 30.0)) * (1.0 - fo) * smoothstep(0.0, 0.15, uT);
        gl_PointSize = uPx * (4.0 + 5.0 * aRnd.z + 10.0 * vPop + (1.0 - k) * 4.0 * b) * (1.0 - fo * 0.5);
      }`,
    fragmentShader: `
      varying float vB; varying float vPop;
      void main() {
        vec2 q = gl_PointCoord * 2.0 - 1.0; float r = dot(q, q);
        float g = exp(-r * 10.0) * 1.4 + 0.2 * exp(-r * 3.0);
        g += (exp(-abs(q.y) * 30.0) * exp(-abs(q.x) * 3.0) + exp(-abs(q.x) * 30.0) * exp(-abs(q.y) * 3.0)) * vPop;
        g *= 1.0 - smoothstep(0.7, 1.0, r);
        vec3 c = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.86, 0.55), vPop);
        gl_FragColor = vec4(c * g * (vB * 1.5 + vPop * 1.8), 1.0);
        ${OUT}
      }`,
  });
  const conv = new THREE.Points(cg, convMat); conv.frustumCulled = false; conv.renderOrder = 6;
  T.add(conv);

  // sparkles on the faces towards the camera; the light line that forms it; the burst's own flash
  const sparkPts = pick(facing.length ? facing : body, 36);
  const sparks = glows(sparkPts.length + 3, { order: 8 });
  scene.add(sparks.mesh);
  const SR = sparkPts.map(() => [R() * TAU, 0.8 + R() * 1.6]);

  // rays behind it, the pool of light it stands in
  const rays = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), additive({
    uniforms: { uA: { value: 0 } },
    vertexShader: `varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: `uniform float uA, uTime; varying vec2 vUv;
      void main() {
        vec2 p = vUv * 2.0 - 1.0; float r = length(p), a = atan(p.y, p.x);
        float rays = pow(0.5 + 0.5 * sin(a * 13.0 + sin(a * 5.0 + uTime * 0.4) * 1.5 + uTime * 0.15), 5.0) + 0.6 * pow(0.5 + 0.5 * sin(a * 23.0 - uTime * 0.25), 9.0);
        float fall = exp(-r * 2.4) * (1.0 - smoothstep(0.7, 1.0, r));
        vec3 c = vec3(1.0, 0.82, 0.5) * rays * fall * 0.3 + vec3(0.55, 0.62, 1.0) * exp(-r * r * 5.0) * 0.22;
        gl_FragColor = vec4(c * uA, 1.0); ${OUT}
      }`,
  }));
  rays.renderOrder = -2; scene.add(rays);
  const pool = new THREE.Mesh(new THREE.CircleGeometry(3.2, 48).rotateX(-Math.PI / 2), additive({
    uniforms: { uA: { value: 0 } },
    vertexShader: `varying vec2 vP; void main() { vP = position.xz; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: `uniform float uA; varying vec2 vP; void main() { float r = length(vP) / 3.2; float g = exp(-r * r * 6.0) * 0.5 + exp(-r * r * 40.0) * 0.4; gl_FragColor = vec4(vec3(0.75, 0.8, 1.0) * g * uA, 1.0); ${OUT} }`,
  }));
  pool.position.y = 0.002; scene.add(pool);
  // motes drifting in the light
  const NM = 220, mp = new Float32Array(NM * 3), mr = new Float32Array(NM * 2);
  for (let i = 0; i < NM; i++) { mp.set([(R() - 0.5) * 6, R() * 3.2, (R() - 0.5) * 3 - 0.5], i * 3); mr.set([R(), R()], i * 2); }
  const mg = new THREE.BufferGeometry(); mg.setAttribute('position', new THREE.BufferAttribute(mp, 3)); mg.setAttribute('aRnd', new THREE.BufferAttribute(mr, 2));
  const motes = new THREE.Points(mg, additive({
    uniforms: { uA: { value: 0 } },
    vertexShader: `attribute vec2 aRnd; uniform float uTime, uPx, uA; varying float vA;
      void main() { vec3 p = position; p.y = mod(p.y + uTime * (0.05 + aRnd.x * 0.08), 3.2); p.x += 0.1 * sin(uTime * 0.5 + aRnd.y * 9.0);
        vec4 mv = modelViewMatrix * vec4(p, 1.0); gl_Position = projectionMatrix * mv; gl_PointSize = uPx * (1.5 + aRnd.x * 2.5); vA = uA * (0.3 + 0.7 * aRnd.y) * smoothstep(0.0, 0.4, p.y) * (1.0 - smoothstep(2.6, 3.2, p.y)); }`,
    fragmentShader: `varying float vA; void main() { vec2 q = gl_PointCoord * 2.0 - 1.0; float g = exp(-dot(q, q) * 5.0); gl_FragColor = vec4(vec3(1.0, 0.92, 0.8) * g * vA * 0.6, 1.0); ${OUT} }`,
  }));
  motes.frustumCulled = false; scene.add(motes);

  const tmp = new THREE.Vector3();
  // lt: seconds into the climax (0 = the burst)
  function frame(lt, t) {
    const turn = sway ? 0.55 * Math.sin(lt * 0.55 - 0.4) : ry0 + 0.34 * lt;
    pivot.rotation.y = turn;
    // the camera: low, pushing in, a slow arc
    const a = 0.1 * Math.sin(lt * 0.25) - 0.04 * lt;
    const dist = lerp(7.3, 5.95, eio(lt / 5)), camY = lerp(1.1, 0.5, eio((lt - 1.5) / 3.5));
    cam.position.set(Math.sin(a) * dist, camY, Math.cos(a) * dist);
    cam.lookAt(0, HT * 0.5, 0);
    // forming: a plane of light rising from the foot
    const rv = eio(clamp((lt - 1.25) / 1.25));
    const hClip = lerp(-0.02, HT * 1.04, rv);
    clip.constant = rv >= 1 ? 1e3 : hClip;
    pivot.visible = true;
    // the stars of the outline go as it forms
    convMat.uniforms.uT.value = lt;
    convMat.uniforms.uOut.value = clamp((lt - 2.3) / 1.4);
    conv.visible = lt < 4.6;
    // sparkles once formed
    let i = 0;
    T.updateMatrixWorld(true);
    sparkPts.forEach((p, k) => {
      tmp.copy(p).applyMatrix4(T.matrixWorld);
      const tw = Math.pow(Math.max(0, Math.sin(t * SR[k][1] * 2.2 + SR[k][0])), 14) * ss(2.4, 3.0, lt);
      sparks.set(i++, tmp, 0.13, 0.13, [1.0, 0.95, 0.85], tw * 1.6, 1);
    });
    // the line of light where it forms; the burst
    const w = 0.9 * s * Math.max(0.3, box0.max.x - box0.min.x);
    sparks.set(i++, new THREE.Vector3(0, hClip, 0), w * 1.4, 0.2, [1.0, 0.85, 0.6], rv > 0 && rv < 1 ? 1.6 : 0, 2);
    sparks.set(i++, new THREE.Vector3(0, hClip, 0), w * 0.5, w * 0.5, [1.0, 0.9, 0.7], rv > 0 && rv < 1 ? 0.25 : 0, 0);
    sparks.set(i++, new THREE.Vector3(0, HT * 0.9, -2.5), 3.5, 3.5, [1.0, 0.95, 0.85], Math.exp(-lt / 0.18) * 2.0, 1);
    sparks.touch();
    rays.position.set(0, HT * 0.55, -1.8); rays.scale.setScalar(9); rays.quaternion.copy(cam.quaternion);
    rays.material.uniforms.uA.value = ss(1.2, 2.6, lt);
    pool.material.uniforms.uA.value = ss(1.0, 2.5, lt);
    motes.material.uniforms.uA.value = ss(1.5, 3.0, lt);
    ribbons.forEach((o) => { if (window.TROPHY_NO_RIBBONS) o.visible = false; });
    for (const o of ribbons) kit.shapeRibbon(o, t);
  }
  return { scene, cam, frame, stars, HT, kind, pivot };
}
