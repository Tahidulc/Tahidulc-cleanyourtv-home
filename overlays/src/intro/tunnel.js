// The tunnel: a low concrete corridor, lit strips overhead, and at its end the opening onto the floodlit pitch, so bright it
// is only light. The players walk up it in slow motion as silhouettes, rimmed by the light they walk into.
import * as THREE from '../vendor/three.module.js';
import { U, OUT, FOG, rng, shader, additive, glows } from './kit.js';
import { rimMaterial, makeFigure, walkPose } from './figure.js';

export const TUN = { W: 4.2, H: 3.3, L: 46, END: -46 };

export function makeTunnel(Q) {
  const scene = new THREE.Scene();
  const R = rng(5);
  const wallMat = shader({
    side: THREE.BackSide,
    vertexShader: `varying vec3 vW; varying vec3 vN; varying float vD; void main() { vec4 wp = modelMatrix * vec4(position, 1.0); vW = wp.xyz; vN = normalize(mat3(modelMatrix) * normal); vec4 mv = viewMatrix * wp; vD = length(mv.xyz); gl_Position = projectionMatrix * mv; }`,
    fragmentShader: `
      ${FOG}
      varying vec3 vW; varying vec3 vN; varying float vD;
      void main() {
        // light from the opening (z = ${TUN.END}), falling off back down the tunnel; strips of light overhead every 5 m
        float toEnd = max(0.0, vW.z - (${TUN.END.toFixed(1)}));
        float open = exp(-toEnd * 0.07) * 2.6;
        float strip = exp(-pow(fract(vW.z / 5.0) - 0.5, 2.0) * 60.0) * 0.35;
        vec3 base = vec3(0.025, 0.028, 0.035);
        float floorK = smoothstep(0.2, -0.5, vW.y - 0.0) ;
        vec3 c = base * (0.4 + open + strip * step(${(TUN.H - 0.4).toFixed(2)}, vW.y) * 3.0 + strip);
        c += vec3(0.5, 0.6, 0.8) * open * 0.08;
        // the floor shines: the opening's light in it
        float gl = (vW.y < 0.05) ? exp(-abs(vW.x) * 1.5) * open * 0.25 : 0.0;
        c += vec3(0.7, 0.8, 1.0) * gl;
        // panel joints along the walls
        float j = 1.0 - smoothstep(0.0, 0.03, abs(fract(vW.z / 2.5) - 0.5) - 0.47);
        c *= 1.0 - 0.3 * j * step(0.06, vW.y);
        gl_FragColor = vec4(fogged(c, vD), 1.0);
        ${OUT}
      }`,
  });
  const box = new THREE.Mesh(new THREE.BoxGeometry(TUN.W, TUN.H, TUN.L, 1, 1, 24), wallMat);
  box.position.set(0, TUN.H / 2, TUN.END / 2);
  scene.add(box);
  // the opening: pure light, with a halo spilling round it
  const open = new THREE.Mesh(new THREE.PlaneGeometry(TUN.W * 0.98, TUN.H * 0.98), shader({
    vertexShader: `varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: `varying vec2 vUv; void main() { vec2 p = abs(vUv - 0.5) * 2.0; float k = 1.0 - 0.25 * max(p.x, p.y); gl_FragColor = vec4(vec3(3.2, 3.4, 3.8) * k, 1.0); ${OUT} }`,
  }));
  open.position.set(0, TUN.H / 2, TUN.END + 0.05);
  scene.add(open);
  const halo = glows(3, { order: 9 });
  halo.set(0, new THREE.Vector3(0, TUN.H / 2, TUN.END + 1), 9, 7, [0.75, 0.82, 1.0], 1.2, 0);
  halo.set(1, new THREE.Vector3(0, TUN.H / 2, TUN.END + 1.2), 26, 1.2, [0.6, 0.75, 1.0], 0.9, 2);
  halo.set(2, new THREE.Vector3(0, TUN.H / 2, TUN.END + 1.5), 4, 4, [1, 1, 1], 1.5, 1);
  halo.touch();
  scene.add(halo.mesh);
  // shafts of light coming in
  const shaftMat = additive({
    side: THREE.DoubleSide,
    vertexShader: `varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: `varying vec2 vUv; void main() { float a = pow(vUv.y, 2.2) * exp(-pow((vUv.x - 0.5) * 4.0, 2.0)); gl_FragColor = vec4(vec3(0.6, 0.7, 0.95) * a * 0.12, 1.0); ${OUT} }`,
  });
  for (let i = 0; i < 5; i++) {
    const sh = new THREE.Mesh(new THREE.PlaneGeometry(1.4 + R() * 1.5, 30), shaftMat);
    sh.rotation.x = -Math.PI / 2 + 0.05; sh.rotation.z = (R() - 0.5) * 0.3; sh.rotation.y = (R() - 0.5) * 0.6;
    sh.position.set((R() - 0.5) * 2.5, 0.6 + R() * 2.2, TUN.END + 15);
    scene.add(sh);
  }
  // dust in the light
  const NM = 400, mp = new Float32Array(NM * 3), mr = new Float32Array(NM * 2);
  for (let i = 0; i < NM; i++) { mp.set([(R() - 0.5) * TUN.W, R() * TUN.H, TUN.END + R() * 30], i * 3); mr.set([R(), R()], i * 2); }
  const mg = new THREE.BufferGeometry(); mg.setAttribute('position', new THREE.BufferAttribute(mp, 3)); mg.setAttribute('aRnd', new THREE.BufferAttribute(mr, 2));
  const motes = new THREE.Points(mg, additive({
    vertexShader: `attribute vec2 aRnd; uniform float uTime, uPx, uHalfH; varying float vA;
      void main() { vec3 p = position + vec3(0.15 * sin(uTime * 0.3 + aRnd.x * 20.0), 0.1 * sin(uTime * 0.2 + aRnd.y * 20.0), 0.0);
        vec4 mv = modelViewMatrix * vec4(p, 1.0); gl_Position = projectionMatrix * mv;
        gl_PointSize = clamp(0.012 * projectionMatrix[1][1] * uHalfH / -mv.z, 1.0, 12.0 * uPx);
        vA = exp(-(p.z - (${TUN.END.toFixed(1)})) * 0.08) * (0.4 + 0.6 * aRnd.y); }`,
    fragmentShader: `varying float vA; void main() { vec2 q = gl_PointCoord * 2.0 - 1.0; float g = exp(-dot(q, q) * 4.0); gl_FragColor = vec4(vec3(0.9, 0.95, 1.0) * g * vA * 0.9, 1.0); ${OUT} }`,
  }));
  motes.frustumCulled = false;
  scene.add(motes);
  // the players: two lines walking out
  const mat = rimMaterial();
  mat.uniforms.uC1.value.setRGB(2.2, 2.35, 2.7);
  mat.uniforms.uC2.value.setRGB(0.5, 0.55, 0.7);
  const figs = [];
  for (let i = 0; i < 8; i++) {
    const f = makeFigure(mat);
    const line = i % 2, k = Math.floor(i / 2);
    f.base = { x: line ? 0.72 : -0.72, z: -8 - k * 3.0 - line * 1.4, ph: R(), s: 0.97 + R() * 0.08 };
    f.root.scale.setScalar(f.base.s);
    scene.add(f.root); figs.push(f);
  }
  // walkers at time t (slow motion: walk speed in m/s, stride phase)
  function walk(t, speed) {
    for (const f of figs) {
      const z = f.base.z - t * speed;
      const ph = f.base.ph + (t * speed) / 1.6;
      f.pose(Object.assign(walkPose(ph), { x: f.base.x, z, ry: Math.PI }));
    }
  }
  return { scene, mat, figs, walk };
}
