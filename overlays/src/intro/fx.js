// The ball (its panels drawn by the shader), its trail of light, the grass and dew it throws up, and the layer drawn over
// every shot in screen space: flashes on the hits, light streaks sweeping across on the beat, lens ghosts from bright
// lights, and a vignette. All functions of time: nothing carried from one frame to the next.
import * as THREE from '../vendor/three.module.js';
import { U, OUT, FOG, TAU, rng, shader, additive, glows, clamp } from './kit.js';

// ---------------------------------------------------------------- the ball
export function makeBall() {
  const ico = [];
  const p = (1 + Math.sqrt(5)) / 2;
  for (const [a, b] of [[1, p], [1, -p], [-1, p], [-1, -p]]) ico.push([0, a, b], [a, b, 0], [b, 0, a]);
  const iv = ico.map((v) => { const l = Math.hypot(...v); return `vec3(${(v[0] / l).toFixed(5)}, ${(v[1] / l).toFixed(5)}, ${(v[2] / l).toFixed(5)})`; });
  const mat = shader({
    uniforms: { uL1: { value: new THREE.Vector3(0, 0.5, -1) }, uC1: { value: new THREE.Color(1.5, 1.6, 1.8) }, uGlow: { value: 0 } },
    vertexShader: `
      varying vec3 vO; varying vec3 vN; varying vec3 vV;
      void main() { vO = normalize(position); vN = normalize(normalMatrix * normal); vec4 mv = modelViewMatrix * vec4(position, 1.0); vV = -mv.xyz; gl_Position = projectionMatrix * mv; }`,
    fragmentShader: `
      uniform vec3 uL1, uC1; uniform float uGlow;
      ${FOG}
      varying vec3 vO; varying vec3 vN; varying vec3 vV;
      const vec3 IV[12] = vec3[12](${iv.join(', ')});
      void main() {
        float m = 0.0;
        for (int i = 0; i < 12; i++) m = max(m, dot(vO, IV[i]));
        float pent = smoothstep(0.928, 0.94, m);
        vec3 base = mix(vec3(0.85, 0.86, 0.9), vec3(0.03, 0.04, 0.09), pent);
        vec3 n = normalize(vN), v = normalize(vV);
        float l = max(dot(n, normalize(uL1)), 0.0), fr = pow(1.0 - max(dot(n, v), 0.0), 2.5);
        vec3 c = base * (0.12 + 0.5 * max(n.y, 0.0) + 0.9 * l) + uC1 * fr * smoothstep(0.0, 0.5, l) + vec3(1.0, 0.9, 0.7) * uGlow;
        gl_FragColor = vec4(fogged(c, length(vV)), 1.0);
        ${OUT}
      }`,
  });
  const mesh = new THREE.Mesh(new THREE.IcosahedronGeometry(0.11, 4), mat);
  mesh.frustumCulled = false;
  return { mesh, mat };
}

// ---------------------------------------------------------------- a ribbon of light behind something moving
export function makeTrail(M = 56) {
  const g = new THREE.BufferGeometry();
  const pos = new Float32Array((M + 1) * 6), uv = new Float32Array((M + 1) * 4), idx = [];
  for (let k = 0; k <= M; k++) { uv.set([k / M, -1, k / M, 1], k * 4); if (k < M) { const a = k * 2; idx.push(a, a + 2, a + 1, a + 1, a + 2, a + 3); } }
  g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  g.setAttribute('aUv', new THREE.BufferAttribute(uv, 2));
  g.setIndex(idx);
  const mat = additive({
    side: THREE.DoubleSide,
    uniforms: { uA: { value: 1 }, uCore: { value: new THREE.Color(1.0, 0.95, 0.85) }, uEdge: { value: new THREE.Color(1.0, 0.62, 0.25) } },
    vertexShader: `attribute vec2 aUv; varying vec2 vUv; void main() { vUv = aUv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: `
      uniform float uA; uniform vec3 uCore, uEdge; varying vec2 vUv;
      void main() {
        float along = pow(1.0 - vUv.x, 1.4), x = vUv.y;
        float core = exp(-x * x * 18.0), glow = exp(-x * x * 2.5) * 0.45;
        vec3 c = (uCore * core * 1.6 + uEdge * glow) * along * uA;
        gl_FragColor = vec4(c, 1.0);
        ${OUT}
      }`,
  });
  const mesh = new THREE.Mesh(g, mat);
  mesh.frustumCulled = false; mesh.renderOrder = 9;
  const t = new THREE.Vector3(), s = new THREE.Vector3(), v = new THREE.Vector3();
  // pts: newest first; w0 head width, w1 tail width (world units)
  function update(pts, camPos, w0, w1) {
    const n = pts.length;
    for (let k = 0; k <= M; k++) {
      const i = Math.min(n - 1, Math.round((k / M) * (n - 1)));
      const a = pts[Math.max(0, i - 1)], b = pts[Math.min(n - 1, i + 1)], p = pts[i];
      t.subVectors(a, b); if (t.lengthSq() < 1e-10) t.set(1, 0, 0);
      v.subVectors(camPos, p);
      s.crossVectors(t, v).normalize();
      const w = w0 + (w1 - w0) * (k / M);
      pos.set([p.x - s.x * w, p.y - s.y * w, p.z - s.z * w, p.x + s.x * w, p.y + s.y * w, p.z + s.z * w], k * 6);
    }
    g.attributes.position.needsUpdate = true;
  }
  return { mesh, mat, update };
}

// ---------------------------------------------------------------- grass and dew thrown up by the boot
export function makeSpray(Q) {
  const R = rng(41);
  const make = (n, dew) => {
    const p0 = new Float32Array(n * 3), vel = new Float32Array(n * 3), rnd = new Float32Array(n * 2);
    for (let i = 0; i < n; i++) {
      const a = (R() - 0.5) * 2.4, up = 1.2 + R() * 3.2, fwd = 1.0 + R() * 4.5;
      p0.set([(R() - 0.5) * 0.12, R() * 0.03, (R() - 0.5) * 0.12], i * 3);
      vel.set([fwd * (dew ? 1.2 : 1), up * (dew ? 1.1 : 1), a * (dew ? 1.6 : 1.0)], i * 3);
      rnd.set([R(), R()], i * 2);
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.BufferAttribute(p0, 3));
    g.setAttribute('aVel', new THREE.BufferAttribute(vel, 3));
    g.setAttribute('aRnd', new THREE.BufferAttribute(rnd, 2));
    return g;
  };
  const vs = (dew) => `
    attribute vec3 aVel; attribute vec2 aRnd;
    uniform float uE, uHalfH, uPx; uniform mat3 uRot; uniform vec3 uOrigin;
    varying float vA; varying float vD;
    void main() {
      float e = max(uE - aRnd.x * 0.02, 0.0);
      vec3 p = uOrigin + uRot * (position + aVel * e) + vec3(0.0, -4.9 * e * e, 0.0);
      vec4 mv = viewMatrix * vec4(p, 1.0);
      gl_Position = projectionMatrix * mv;
      float s = ${dew ? '0.012' : '0.022'} * (0.6 + aRnd.y) * projectionMatrix[1][1] * uHalfH / -mv.z;
      float on = step(0.0, uE) * step(0.0, p.y) * (1.0 - smoothstep(0.6, 1.2, e));
      gl_PointSize = on > 0.0 ? clamp(s, 1.0, 40.0 * uPx) : 0.0;
      vA = on; vD = length(mv.xyz);
    }`;
  const uni = () => ({ uE: { value: -1 }, uRot: { value: new THREE.Matrix3() }, uOrigin: { value: new THREE.Vector3() } });
  const clumpMat = shader({
    uniforms: uni(),
    vertexShader: vs(false),
    fragmentShader: `${FOG} varying float vA; varying float vD;
      void main() { vec2 q = gl_PointCoord * 2.0 - 1.0; if (dot(q, q) > 1.0) discard; gl_FragColor = vec4(fogged(vec3(0.07, 0.2, 0.06) * (1.2 - 0.5 * q.y), vD), 1.0); ${OUT} }`,
  });
  const dewMat = additive({
    uniforms: uni(),
    vertexShader: vs(true),
    fragmentShader: `varying float vA; varying float vD;
      void main() { vec2 q = gl_PointCoord * 2.0 - 1.0; float r = dot(q, q); float g = exp(-r * 6.0) * (1.0 - smoothstep(0.6, 1.0, r)); gl_FragColor = vec4(vec3(0.85, 0.95, 1.0) * g * vA * 1.8, 1.0); ${OUT} }`,
  });
  const clumps = new THREE.Points(make(Q.low ? 160 : 320, false), clumpMat);
  const dew = new THREE.Points(make(Q.low ? 160 : 360, true), dewMat);
  clumps.frustumCulled = dew.frustumCulled = false;
  const group = new THREE.Group(); group.add(clumps); group.add(dew);
  // E: seconds since the contact; origin: where; yaw: the direction of the strike
  function set(E, origin, yaw) {
    const m = new THREE.Matrix3().set(Math.cos(yaw), 0, Math.sin(yaw), 0, 1, 0, -Math.sin(yaw), 0, Math.cos(yaw));
    for (const M of [clumpMat, dewMat]) { M.uniforms.uE.value = E; M.uniforms.uOrigin.value.copy(origin); M.uniforms.uRot.value.copy(m); }
  }
  return { group, set };
}

// ---------------------------------------------------------------- the screen layer
export function makeHud() {
  const scene = new THREE.Scene();
  const cam = new THREE.OrthographicCamera(0, 1920, 1080, 0, -10, 10);
  const plane = new THREE.PlaneGeometry(1, 1);
  // the vignette, then the flash on top
  const vig = new THREE.Mesh(plane, shader({
    transparent: true, depthTest: false, depthWrite: false,
    uniforms: { uA: { value: 0.6 }, uDim: { value: 0 } },
    vertexShader: `varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: `uniform float uA, uDim; varying vec2 vUv; void main() { vec2 p = (vUv - 0.5) * vec2(1.6, 1.0); float v = smoothstep(0.35, 0.95, length(p)); gl_FragColor = vec4(0.0, 0.0, 0.0, max(v * uA, uDim)); }`,
  }));
  vig.scale.set(1920, 1080, 1); vig.position.set(960, 540, 0); vig.renderOrder = 1;
  const flashMat = additive({ depthTest: false, uniforms: { uA: { value: 0 }, uCol: { value: new THREE.Color(1, 0.97, 0.92) } },
    vertexShader: `void main() { gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: `uniform float uA; uniform vec3 uCol; void main() { gl_FragColor = vec4(uCol * uA, 1.0); }` });
  const flash = new THREE.Mesh(plane, flashMat);
  flash.scale.set(1920, 1080, 1); flash.position.set(960, 540, 0); flash.renderOrder = 50;
  // light streaks: a bright thin line with a soft glow, tapering at both ends
  const streakMat = () => additive({ depthTest: false, uniforms: { uA: { value: 0 }, uCol: { value: new THREE.Color(1, 0.85, 0.6) } },
    vertexShader: `varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: `uniform float uA; uniform vec3 uCol; varying vec2 vUv;
      void main() { float y = (vUv.y - 0.5) * 2.0, x = (vUv.x - 0.5) * 2.0; float taper = pow(max(0.0, 1.0 - x * x), 1.5);
        float g = (exp(-y * y * 600.0) * 1.8 + exp(-y * y * 30.0) * 0.35 + exp(-y * y * 4.0) * 0.08) * taper;
        gl_FragColor = vec4(mix(uCol, vec3(1.0), exp(-y * y * 900.0)) * g * uA, 1.0); }` });
  const streaks = [0, 1, 2].map(() => { const m = new THREE.Mesh(plane, streakMat()); m.renderOrder = 20; m.visible = false; scene.add(m); return m; });
  const ghosts = glows(10, { order: 30, depthTest: false });
  scene.add(vig); scene.add(flash); scene.add(ghosts.mesh);
  const tmp = new THREE.Vector3();
  const GH = [[0.55, 70, [0.4, 0.6, 1.0], 0.08, 3], [0.2, 26, [0.6, 1.0, 0.7], 0.12, 0], [-0.3, 50, [1.0, 0.55, 0.3], 0.07, 0], [-0.75, 120, [0.5, 0.4, 1.0], 0.05, 3], [-1.05, 34, [0.9, 0.8, 0.6], 0.1, 0]];
  return {
    scene, cam, vig, flash, flashMat, streaks, ghosts,
    // streak i across the screen: x0..x1 (px) as u goes 0..1 at height y (px from the top), angle, intensity, colour
    streak(i, u, y, ang, a, col) {
      const m = streaks[i]; m.visible = a > 0.001;
      const len = 1300, x = -len * 0.5 + u * (1920 + len);
      m.position.set(x, 1080 - y, 0); m.rotation.z = ang; m.scale.set(len, 90, 1);
      m.material.uniforms.uA.value = a; if (col) m.material.uniforms.uCol.value.setRGB(col[0], col[1], col[2]);
    },
    // ghosts of a bright source (world position) through the lens's centre; k its strength
    flare(camera, srcs) {
      let i = 0;
      for (const [p, k] of srcs) {
        tmp.copy(p).project(camera);
        if (tmp.z > 1 || Math.abs(tmp.x) > 1.4 || Math.abs(tmp.y) > 1.4 || k < 0.01) continue;
        const sx = (tmp.x * 0.5 + 0.5) * 1920, sy = (tmp.y * 0.5 + 0.5) * 1080;
        for (const [f, s, c, a, kind] of GH) {
          if (i >= 10) break;
          ghosts.set(i++, new THREE.Vector3(960 + (sx - 960) * f, 540 + (sy - 540) * f, 0), s, s, c, a * k, kind);
        }
      }
      for (; i < 10; i++) ghosts.alpha(i, 0);
      ghosts.touch();
    },
  };
}
