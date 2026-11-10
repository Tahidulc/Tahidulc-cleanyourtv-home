// The matchday intro's small shared pieces: easing, a seeded random, the uniforms every shader shares, and glows (many
// soft billboards of light in one draw: lamps, glare, the ball's glow, sparkles).
import * as THREE from '../vendor/three.module.js';

export const TAU = Math.PI * 2;
export const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
export const lerp = (a, b, x) => a + (b - a) * x;
export const ss = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
export const eio = (x) => { x = clamp(x); return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2; };
export const eo = (x) => 1 - Math.pow(1 - clamp(x), 3);
export const ei = (x) => Math.pow(clamp(x), 3);
export const V = (x, y, z) => new THREE.Vector3(x, y, z);
export const vlerp = (a, b, x) => new THREE.Vector3().lerpVectors(a, b, x);
export function rng(seed) {
  let a = seed >>> 0;
  return () => { a = (a + 0x6D2B79F5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
// a pulse after time t0: 0 before, 1 at t0, falling with time constant k
export const pulse = (t, t0, k) => (t < t0 ? 0 : Math.exp(-(t - t0) / k));

// uniforms shared by reference: the clock, the drawing's half height in pixels (point sizes), the haze
export const U = {
  uTime: { value: 0 },
  uHalfH: { value: 540 },
  uPx: { value: 1 },                       // pixels per 1080p pixel
  uFogCol: { value: new THREE.Color(0x0a0f1e) },
  uFogD: { value: 0.0045 },
};
// the end of each of our fragment shaders, so they come out tone-mapped and in sRGB as three's own materials do
export const OUT = '\n#include <tonemapping_fragment>\n#include <colorspace_fragment>\n';
export const FOG = 'uniform vec3 uFogCol; uniform float uFogD;\nvec3 fogged(vec3 c, float d) { float f = 1.0 - exp(-uFogD * uFogD * d * d); return mix(c, uFogCol, f); }\n';
export const HASH = 'float hash1(float n) { return fract(sin(n) * 43758.5453123); }\nfloat hash2(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }\n';

export function shader(o) {
  return new THREE.ShaderMaterial(Object.assign({ uniforms: {}, transparent: false, depthWrite: true }, o, {
    uniforms: Object.assign({}, U, o.uniforms || {}),
  }));
}
export const additive = (o) => shader(Object.assign({ transparent: true, depthWrite: false, blending: THREE.AdditiveBlending }, o));

// ---------------------------------------------------------------- glows
// n soft billboards in one mesh. Each: centre (world, or the parent's space), size x/y (world units), colour, intensity,
// kind (0 round, 1 round with a four-point star, 2 a long horizontal streak, 3 a soft ring)
export function glows(n, opts = {}) {
  const g = new THREE.BufferGeometry();
  const c = new Float32Array(n * 12), corner = new Float32Array(n * 8), size = new Float32Array(n * 8), col = new Float32Array(n * 16), kind = new Float32Array(n * 4);
  const idx = [];
  for (let i = 0; i < n; i++) {
    corner.set([-1, -1, 1, -1, 1, 1, -1, 1], i * 8);
    idx.push(i * 4, i * 4 + 1, i * 4 + 2, i * 4, i * 4 + 2, i * 4 + 3);
  }
  g.setAttribute('position', new THREE.BufferAttribute(c, 3));
  g.setAttribute('aCorner', new THREE.BufferAttribute(corner, 2));
  g.setAttribute('aSize', new THREE.BufferAttribute(size, 2));
  g.setAttribute('aColor', new THREE.BufferAttribute(col, 4));
  g.setAttribute('aKind', new THREE.BufferAttribute(kind, 1));
  g.setIndex(idx);
  const mat = additive({
    depthTest: opts.depthTest !== false,
    uniforms: { uGain: { value: 1 } },
    vertexShader: `
      attribute vec2 aCorner; attribute vec2 aSize; attribute vec4 aColor; attribute float aKind;
      varying vec2 vUv; varying vec4 vCol; varying float vKind;
      void main() {
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        mv.xy += aCorner * aSize;
        gl_Position = projectionMatrix * mv;
        vUv = aCorner; vCol = aColor; vKind = aKind;
      }`,
    fragmentShader: `
      uniform float uGain;
      varying vec2 vUv; varying vec4 vCol; varying float vKind;
      void main() {
        float r2 = dot(vUv, vUv);
        float edge = 1.0 - smoothstep(0.55, 1.0, r2);
        float g;
        if (vKind < 0.5) g = exp(-r2 * 7.0) + 0.18 * exp(-r2 * 2.2);
        else if (vKind < 1.5) {
          float sp = exp(-abs(vUv.y) * 70.0) * exp(-abs(vUv.x) * 3.0) + exp(-abs(vUv.x) * 70.0) * exp(-abs(vUv.y) * 3.0);
          g = exp(-r2 * 18.0) * 1.4 + 0.12 * exp(-r2 * 3.0) + sp * 0.9;
        } else if (vKind < 2.5) g = exp(-abs(vUv.y) * 9.0) * pow(1.0 - min(abs(vUv.x), 1.0), 2.0) + 0.6 * exp(-vUv.y * vUv.y * 400.0) * (1.0 - abs(vUv.x));
        else { float r = sqrt(r2); g = exp(-pow((r - 0.7) * 9.0, 2.0)) * 0.6 + exp(-r2 * 30.0) * 0.4; }
        vec3 c = vCol.rgb * vCol.a * g * edge * uGain;
        gl_FragColor = vec4(c, 1.0);
        ${OUT}
      }`,
  });
  const mesh = new THREE.Mesh(g, mat);
  mesh.frustumCulled = false;
  mesh.renderOrder = opts.order || 5;
  const api = {
    mesh, n,
    // set(i, centre, sx, sy, colour (THREE.Color or [r,g,b]), intensity, kind)
    set(i, p, sx, sy, colr, a, k = 0) {
      for (let v = 0; v < 4; v++) {
        c.set([p.x, p.y, p.z], (i * 4 + v) * 3);
        size.set([sx, sy], (i * 4 + v) * 2);
        const cr = colr.isColor ? [colr.r, colr.g, colr.b] : colr;
        col.set([cr[0], cr[1], cr[2], a], (i * 4 + v) * 4);
        kind[i * 4 + v] = k;
      }
    },
    alpha(i, a) { for (let v = 0; v < 4; v++) col[(i * 4 + v) * 4 + 3] = a; },
    pos(i, p) { for (let v = 0; v < 4; v++) c.set([p.x, p.y, p.z], (i * 4 + v) * 3); },
    sz(i, sx, sy) { for (let v = 0; v < 4; v++) size.set([sx, sy == null ? sx : sy], (i * 4 + v) * 2); },
    touch() { for (const k of ['position', 'aSize', 'aColor', 'aKind']) g.attributes[k].needsUpdate = true; },
  };
  return api;
}
