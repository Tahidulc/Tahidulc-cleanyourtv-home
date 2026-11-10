// The matchday intro: about 51 seconds drawn live in three.js, every shot from our own scenes. Night sky and a footballer
// in stars; down through the floodlights into the ground; the walk-out; a strike in slow motion, the keeper beaten, the
// net; quicker cuts on the beat; the ball up into the night to become a star; the stars gather into the competition's
// trophy; then Whitebrook Road Sports, MATCHDAY, the two teams, and it fades back to the channel.
// Deterministic: draw(t) draws second t, from nothing carried between frames, so it can be played live or captured.
//   window.INTRO.init(cfg, {capture}) -> Promise; INTRO.draw(t); INTRO.opacity(t); INTRO.setScale(k); INTRO.LEN
import * as THREE from '../vendor/three.module.js';
import TEMPO from './tempo.json';
import { U, clamp, lerp, ss, eio, eo, V, pulse, TAU, glows } from './kit.js';
import { makeSky } from './sky.js';
import { makeStadium, MASTS, BALL0 } from './stadium.js';
import { rimMaterial, makeFigure, kickPose, keeperPose, keeperSet, heroPose, keeperRoot } from './figure.js';
import { makeBall, makeTrail, makeSpray, makeHud } from './fx.js';
import { makeTrophyStage } from './trophy-stage.js';
import { makeCard } from './card.js';
import { makeTunnel, TUN } from './tunnel.js';

export const TROPHY = { pl: 'pl', facup: 'facup', leaguecup: 'leaguecup', ucl: 'ucl', uel: 'uel', uecl: 'uecl', laliga: 'laliga', seriea: 'seriea', bundesliga: 'bundesliga', ligue1: 'ligue1' };
const W = 1920, H = 1080;
let Q, renderer, canvas, world, cam, sky, st, striker, keeper, figMat, ball, trail, spray, bglow, tunnel, stage, hud, card, scale = 1;

// ---------------------------------------------------------------- the action: the strike, by its own time E (s)
const SRY = Math.PI / 2 - 0.3;                                   // the striker's run, towards the far goal
const FWD = V(Math.sin(SRY), 0, Math.cos(SRY)), RIGHT = V(-Math.cos(SRY), 0, Math.sin(SRY));
const B0 = BALL0.clone(), BN = V(54.35, 2.15, -3.3), BC = V(45.4, 2.55, -3.4), TF = 0.62;   // the ball: spot, net, curl
const BDIR = BN.clone().sub(BC).normalize();
const K0 = V(51.6, 0, -0.3), KRY = -Math.PI / 2;               // the keeper on his line, facing out
let R0 = V(0, 0, 0);                                             // the striker's root at pz = 0
const RISE_OFF = V(-22, 0, 4), RISE_ROOT = V(0.05, 0, -1.25).add(RISE_OFF); let RISE_P0 = V(0, 0.11, -1.5);
function bez(a, b, c, s) { const u = 1 - s; return V(u * u * a.x + 2 * u * s * b.x + s * s * c.x, u * u * a.y + 2 * u * s * b.y + s * s * c.y, u * u * a.z + 2 * u * s * b.z + s * s * c.z); }
function ballAt(E) {
  if (E <= 0) return B0.clone();
  if (E <= TF) return bez(B0, BC, BN, E / TF);
  const a = E - TF, p = BN.clone().addScaledVector(BDIR, 0.5 * (1 - Math.exp(-a * 7)));
  p.y = Math.max(0.11, BN.y - 0.4 * a - 4 * a * a);
  return p;
}
function strikerAt(E, extra) {
  const k = kickPose(E);
  striker.pose(Object.assign(k, { x: R0.x + FWD.x * k.pz, z: R0.z + FWD.z * k.pz, ry: SRY }, extra || {}));
}
function keeperAt(E) {
  const k = E < 0 ? keeperSet(E * 6) : keeperPose(E);
  const p = keeperRoot(K0, KRY, k);
  keeper.pose(Object.assign(k, { x: p.x, y: p.y, z: p.z, ry: KRY }));
}
function spinBall(E, axis) { ball.mesh.quaternion.setFromAxisAngle(axis || V(0, 0, 1).cross(FWD).normalize().negate(), E * 55); }
// the ball's trail: its positions back over dE seconds of the action
function trailFor(E, dE, w0, w1, a) {
  const pts = [];
  for (let k = 0; k <= 40; k++) pts.push(ballAt(Math.max(0, E - (dE * k) / 40)));
  trail.update(pts, cam.position, w0, w1);
  trail.mat.uniforms.uA.value = a * clamp(E / 0.03);
}

// ---------------------------------------------------------------- set-up
function detectGpu() {
  try {
    const gl = document.createElement('canvas').getContext('webgl2') || document.createElement('canvas').getContext('webgl');
    const ext = gl && gl.getExtension('WEBGL_debug_renderer_info');
    return gl ? String(ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER)) : 'none';
  } catch (e) { return 'error'; }
}
export async function init(cfg, opts = {}) {
  const q = cfg.q || {};
  const gpu = detectGpu();
  const weak = /PowerVR|Rogue|GE8|GE9|Mali-[0-9T]|Mali-G[0-9]{2}\b|Adreno \(TM\) [345]/i.test(gpu);
  Q = { gpu, weak, low: q.low != null ? !!q.low : weak, aa: q.aa != null ? !!q.aa : !weak, capture: !!opts.capture || !!q.capture };
  scale = +q.scale > 0 ? Math.min(1, +q.scale) : weak ? 0.5 : 1;
  canvas = document.createElement('canvas');
  canvas.id = 'intro';
  canvas.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;opacity:0;';
  (opts.parent || document.body).appendChild(canvas);
  renderer = new THREE.WebGLRenderer({ canvas, antialias: Q.aa, alpha: true, premultipliedAlpha: true, preserveDrawingBuffer: Q.capture, powerPreference: 'high-performance', stencil: false });
  renderer.setPixelRatio(1);
  setScale(scale);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.autoClear = false;
  renderer.localClippingEnabled = true;
  renderer.setClearColor(0x03050b, 1);
  fit();
  // the lettering first: the wordmark and the Premier League's ribbons are drawn into canvases
  if (document.fonts && document.fonts.load) {
    await Promise.race([Promise.all(['800 118px Outfit', '700 74px Outfit', '600 40px Outfit', '700 76px "Barlow Condensed"'].map((f) => document.fonts.load(f))), new Promise((r) => setTimeout(r, 3000))]).catch(() => {});
  }
  world = new THREE.Scene();
  cam = new THREE.PerspectiveCamera(50, W / H, 0.03, 3000);
  sky = makeSky(Q, TEMPO); world.add(sky.group);
  st = makeStadium(Q); world.add(st.group);
  figMat = rimMaterial();
  striker = makeFigure(figMat); keeper = makeFigure(figMat, { gloves: true });
  world.add(striker.root); world.add(keeper.root);
  ball = makeBall(); world.add(ball.mesh);
  trail = makeTrail(); world.add(trail.mesh);
  spray = makeSpray(Q); world.add(spray.group);
  bglow = glows(3, { order: 10 }); world.add(bglow.mesh);
  // where the striker must start so that his right boot meets the ball at E = 0
  strikerAt(0); striker.root.updateMatrixWorld(true);
  const contact = striker.J.anR.localToWorld(V(0, -0.035, 0.09));
  R0 = B0.clone().addScaledVector(FWD, -0.11).sub(contact).add(R0); R0.y = 0;
  // and for the kick up into the night: on the centre spot, facing the far stand
  const k0 = kickPose(0);
  striker.pose(Object.assign(k0, { x: RISE_ROOT.x, z: RISE_ROOT.z - k0.pz, ry: Math.PI })); striker.root.updateMatrixWorld(true);
  RISE_P0 = striker.J.anR.localToWorld(V(0, -0.035, 0.09)).add(V(0, 0, -0.11)); RISE_P0.y = 0.11;
  tunnel = makeTunnel(Q);
  const kind = TROPHY[cfg.trophy] || TROPHY[cfg.comp] || 'generic';
  stage = makeTrophyStage(renderer, kind, sky, Q);
  hud = makeHud();
  card = makeCard(cfg); hud.scene.add(card.group);
  return { gpu, weak, low: Q.low, aa: Q.aa, scale, kind };
}

// the drawing's resolution: k of 1920 x 1080
export function setScale(k) {
  scale = clamp(k, 0.3, 1);
  const w = Math.round(W * scale), h = Math.round(H * scale);
  renderer.setSize(w, h, false);
  U.uHalfH.value = h / 2; U.uPx.value = h / H;
}
// full screen, covering it (an odd shape is cropped rather than letting the channel show at the sides)
export function fit() {
  const de = document.documentElement;
  const vw = de.clientWidth || window.innerWidth || W, vh = de.clientHeight || window.innerHeight || H;
  if (canvas._vw === vw && canvas._vh === vh) return;
  canvas._vw = vw; canvas._vh = vh;
  const k = Math.max(vw / W, vh / H);
  Object.assign(canvas.style, { width: W * k + 'px', height: H * k + 'px', left: (vw - W * k) / 2 + 'px', top: (vh - H * k) / 2 + 'px' });
}
export function opacity(t) {
  return ss(TEMPO.fadeIn[0], TEMPO.fadeIn[1], t) * (1 - ss(TEMPO.fadeOut[0], TEMPO.fadeOut[1], t));
}

// ---------------------------------------------------------------- helpers for the shots
function look(pos, target, fov, roll = 0) {
  cam.position.copy(pos); cam.up.set(0, 1, 0); cam.lookAt(target);
  if (roll) cam.rotateZ(roll);
  cam.fov = fov; cam.updateProjectionMatrix(); cam.updateMatrixWorld(true);
}
function show(o) {
  sky.group.visible = o.sky !== false;
  sky.C.group.visible = !!o.constellation;
  st.group.visible = o.stadium !== false;
  st.blades.visible = !!o.blades;
  st.beams.visible = !!o.beams;
  st.hazeG.mesh.visible = !!o.haze;
  st.goals.forEach((g) => { g.G.visible = o.goals !== false; });
  striker.root.visible = !!o.striker; keeper.root.visible = !!o.keeper;
  ball.mesh.visible = !!o.ball; trail.mesh.visible = !!o.trail; spray.group.visible = !!o.spray;
  bglow.mesh.visible = !!o.ball || !!o.bglow;
}
const tv = new THREE.Vector3();
// rim lights on a material from two lights (world positions), seen from this camera
function backLights(from) {
  const v = from.clone().sub(cam.position).normalize();
  return MASTS.slice().sort((a, b) => b.clone().sub(from).normalize().dot(v) - a.clone().sub(from).normalize().dot(v));
}
function rims(mat, from, l1, l2, k1 = 1, k2 = 1) {
  if (!l1) { const b = backLights(from); l1 = b[0]; l2 = b[1]; }
  mat.uniforms.uL1.value.copy(l1).sub(from).normalize().transformDirection(cam.matrixWorldInverse);
  mat.uniforms.uL2.value.copy(l2).sub(from).normalize().transformDirection(cam.matrixWorldInverse);
  mat.uniforms.uC1.value.setRGB(1.6 * k1, 1.7 * k1, 2.0 * k1);
  mat.uniforms.uC2.value.setRGB(0.95 * k2, 0.7 * k2, 0.5 * k2);
}
function ballLight(from, l1) {
  ball.mat.uniforms.uL1.value.copy(l1).sub(from).normalize().transformDirection(cam.matrixWorldInverse);
}
// the glare of the masts as this camera sees them, for the lens ghosts
function mastFlares(k = 1) {
  return st.masts.map((M) => { tv.copy(cam.position).sub(M.glarePos).normalize(); return [M.glarePos, Math.pow(Math.max(0, M.fwd.dot(tv)), 5) * k]; });
}
function env(o = {}) {
  U.uFogD.value = o.fog ?? 0.0045;
  U.uFogCol.value.setRGB(...(o.fogCol || [0.035, 0.05, 0.1]));
  sky.starMat.uniforms.uFade.value = o.stars ?? 0.55;
  sky.milkyMat.uniforms.color.value.setScalar(o.milky ?? 0.5);
  sky.C.starMat.uniforms.uA.value = o.cA ?? 1;
  sky.C.lineMat.uniforms.uA.value = o.cA ?? 1;
  sky.C.starMat.uniforms.uBoost.value = o.cBoost ?? 0;
  st.pitchMat.uniforms.uLit.value = o.pitch ?? 1;
  st.standMat.uniforms.uLit.value = o.standsLit ?? 1;
  st.crowdMat.uniforms.uBright.value = o.crowd ?? 1;
  st.flashMat.uniforms.uFlash.value = o.flash ?? 0.06;
  st.flashMat.uniforms.uRate.value = o.rate ?? 3;
  st.beamMat.uniforms.uA.value = o.beam ?? 0.05;
  renderer.toneMappingExposure = o.exposure ?? 1.0;
  hud.vig.material.uniforms.uA.value = o.vig ?? 0.6;
  hud.vig.material.uniforms.uDim.value = o.dim ?? 0;
}

// ---------------------------------------------------------------- the shots: each sets the camera and the scene for second t
const C_SKY = V(0, 45, 62);
const S = {
  sky(t, lt) {
    show({ stadium: false, constellation: true });
    env({ stars: ss(0, 1.8, t), milky: 0.85 * ss(0.3, 3, t), cBoost: 0.7 * pulse(t, TEMPO.stars.ball[1], 0.6), fog: 0, exposure: 1.0, vig: 0.7 });
    const c = sky.C.centre.clone(), b = sky.C.ballStar.clone();
    const p1 = eio(lt / 6.25), p2 = eio((lt - 6.25) / 3.75);
    const tgt = c.clone().add(V(-30 + 30 * p1, 45 * (1 - p1), 0)).lerp(b, p2 * 0.85);
    look(C_SKY, C_SKY.clone().add(tgt), lerp(lerp(52, 43, p1), 31, p2), 0.03 * Math.sin(t * 0.3) - 0.02);
    return [world, cam];
  },
  tilt(t, lt) {
    show({ constellation: true, beams: true, goals: true });
    const p = eio(lt / 3.9), drift = eio(lt / 5);
    const pos = V(lerp(0, -16, drift), lerp(45, 31, drift), lerp(62, 74, drift));
    const dA = sky.C.ballStar.clone().normalize(), dB = V(6, 0, -10).sub(pos).normalize();
    const dir = dA.lerp(dB, p).normalize();
    look(pos, pos.clone().add(dir), lerp(31, 60, eio((lt - 0.2) / 3.4)), 0.04 * (1 - p) - 0.015);
    env({ stars: 0.95 - 0.55 * ss(1.4, 3.4, lt), milky: 0.85 - 0.45 * ss(1.4, 3.4, lt), cA: 1 - ss(1.0, 2.4, lt), flash: 0.04, beam: 0.045, fog: 0.0035, exposure: 1.0 });
    st.lights(cam, 1, 0.9);
    return [world, cam, { flares: mastFlares(1.1) }];
  },
  tunnel(t, lt) {
    const z = -2 - 1.3 * lt;
    look(V(0.35, 0.55 + 0.08 * lt, z), V(0, 1.45, TUN.END), lerp(44, 38, lt / 2.5), 0.02);
    tunnel.walk(2.2 + lt * 0.55, 1.3);
    env({ fog: 0.012, fogCol: [0.03, 0.035, 0.05], exposure: 1.05, vig: 0.75 });
    const L = V(0, 1.6, TUN.END);
    tunnel.mat.uniforms.uL1.value.copy(L).sub(V(0, 1, -16)).normalize().transformDirection(cam.matrixWorldInverse);
    tunnel.mat.uniforms.uL2.value.set(0, 1, 0).transformDirection(cam.matrixWorldInverse);
    return [tunnel.scene, cam];
  },
  mast(t, lt) {
    show({ beams: true, goals: true });
    const M = MASTS[0];
    const pos = V(34, 2.0, -22).lerp(M, 0.06 * lt);
    look(pos, M.clone().add(V(0, -2, 0)), lerp(36, 31, lt / 1.25), 0.12 - 0.04 * lt);
    env({ flash: 0.12, rate: 4, beam: 0.06, stars: 0.4, exposure: 1.0 });
    st.lights(cam, 1, 0.55, 0.6, 0.7);
    return [world, cam, { flares: mastFlares(0.8) }];
  },
  crowd(t, lt) {
    show({ goals: false });
    const x = -34 + lt * 7;
    look(V(x, 3.2, 22), V(x + 10, 13, -60), 22, -0.03);
    env({ flash: 0.3, rate: 4.5, crowd: 1.25, fog: 0.004, exposure: 1.05 });
    st.lights(cam, 1, 1);
    return [world, cam, { flares: mastFlares(0.8) }];
  },
  runup(t, lt) {
    show({ striker: true, ball: true, blades: true, goals: true });
    const E = -0.62 + lt * 0.236;
    strikerAt(E);
    const root = striker.root.position.clone();
    const pos = B0.clone().addScaledVector(FWD, 2.7).addScaledVector(RIGHT, -1.0 + 0.25 * lt).add(V(0, 0.16 + 0.05 * lt, 0));
    look(pos, root.clone().add(V(0, 1.05, 0)).addScaledVector(FWD, 0.5), 46, 0.03);
    env({ fog: 0.004, exposure: 1.05, flash: 0.05, beam: 0.04 });
    st.lights(cam, 1, 1.2);
    rims(figMat, root, null, null, 1.2, 0.9);
    ball.mesh.position.copy(B0); spinBall(0); ballLight(B0, MASTS[1]);
    bglow.set(0, B0, 0, 0, [0, 0, 0], 0, 0); bglow.touch();
    return [world, cam, { flares: mastFlares(0.9) }];
  },
  strike(t, lt) {
    show({ striker: true, ball: true, trail: true, spray: true, blades: true, keeper: true, goals: true, beams: true });
    const E = lt < 0.7 ? 0.02 * lt : 0.014 + 0.02 * (lt - 0.7) + 0.062 * (lt - 0.7) * (lt - 0.7);
    strikerAt(E); keeperAt(E);
    const bp = ballAt(E);
    const pos = B0.clone().addScaledVector(RIGHT, 3.3 + 0.5 * lt).addScaledVector(FWD, 0.5 - 0.3 * lt).add(V(0, 0.3 + 0.1 * lt, 0));
    const tgt = B0.clone().add(V(0, 0.75, 0)).addScaledVector(FWD, -0.3).lerp(bp.clone().add(V(0, 0.2, 0)), ss(0.5, 2.3, lt));
    look(pos, tgt, lerp(40, 46, ss(0.5, 2.5, lt)), -0.04 * ss(0.5, 2.5, lt));
    env({ fog: 0.004, exposure: 1.05, flash: 0.05, beam: 0.05 });
    st.lights(cam, 1, 1.2);
    rims(figMat, striker.root.position, null, null, 1.2, 1.0);
    ball.mesh.position.copy(bp); spinBall(E); ballLight(bp, MASTS[0]);
    trailFor(E, 0.14, 0.055, 0.008, 1.2);
    spray.set(E, B0.clone().addScaledVector(FWD, -0.06).setY(0.02), SRY - Math.PI / 2);
    bglow.set(0, bp, 0.45, 0.45, [1.0, 0.85, 0.6], 0.6 * clamp(E / 0.02), 0);
    bglow.set(1, B0.clone().addScaledVector(FWD, -0.08), 0.7, 0.7, [1, 0.95, 0.85], 1.0 * pulse(t, TEMPO.strike, 0.2), 1);
    bglow.touch();
    return [world, cam, { flares: mastFlares(1) }];
  },
  boot(t, lt) {
    show({ striker: true, ball: true, spray: true, blades: true });
    const E = -0.035 + lt * 0.06;
    strikerAt(E);
    const bp = ballAt(E);
    const pos = B0.clone().addScaledVector(FWD, 0.95 + 0.05 * lt).addScaledVector(RIGHT, 0.8).add(V(0, 0.1, 0));
    look(pos, B0.clone().addScaledVector(FWD, -0.22).add(V(0, 0.2, 0)), 32, 0.05);
    env({ fog: 0.004, exposure: 1.15, flash: 0.04 });
    st.lights(cam, 1, 0.8);
    rims(figMat, B0, null, null, 1.4, 1.0);
    ball.mesh.position.copy(bp); spinBall(E); ballLight(bp, MASTS[1]);
    spray.set(E, B0.clone().addScaledVector(FWD, -0.06).setY(0.02), SRY - Math.PI / 2);
    bglow.set(0, bp, 0, 0, [0, 0, 0], 0, 0);
    bglow.set(1, B0.clone().addScaledVector(FWD, -0.1).add(V(0, 0.05, 0)), 0.3, 0.3, [1, 0.95, 0.85], 1.1 * (E > 0 ? Math.exp(-E / 0.01) : 0), 1);
    bglow.touch();
    return [world, cam];
  },
  flight(t, lt) {
    show({ ball: true, trail: true, keeper: true, goals: true, beams: true });
    const E = 0.2 + lt * 0.19;
    keeperAt(E);
    const bp = ballAt(E), dir = ballAt(E + 0.01).sub(bp).normalize();
    const side = V(0, 1, 0).cross(dir).normalize();
    const pos = ballAt(E - 0.06).add(V(0, 0.32, 0)).addScaledVector(side, 0.45);
    look(pos, bp.clone().addScaledVector(dir, 8).add(V(0, -0.2, 0)), 52, -0.05);
    env({ fog: 0.004, exposure: 1.05, flash: 0.1, rate: 4, beam: 0.05 });
    st.lights(cam, 1, 1.2);
    rims(figMat, keeper.root.position, null, null, 1.3, 0.9);
    ball.mesh.position.copy(bp); spinBall(E); ballLight(bp, MASTS[0]);
    trailFor(E, 0.12, 0.05, 0.006, 1.3);
    bglow.set(0, bp, 0.5, 0.5, [1.0, 0.85, 0.6], 0.7, 0); bglow.set(1, bp, 0, 0, [0, 0, 0], 0, 1); bglow.touch();
    return [world, cam, { flares: mastFlares(1) }];
  },
  keeper(t, lt) {
    show({ ball: true, trail: true, keeper: true, goals: true, beams: true });
    const E = 0.36 + lt * 0.24;
    keeperAt(E);
    const bp = ballAt(E);
    look(V(47.4 + 0.3 * lt, 0.34, -1.4), V(51.6, 1.6, -2.0), 52, 0.05);
    env({ fog: 0.004, exposure: 1.0, flash: 0.08, beam: 0.06 });
    st.lights(cam, 1, 1.3);
    rims(figMat, keeper.root.position, null, null, 1.5, 1.0);
    ball.mesh.position.copy(bp); spinBall(E); ballLight(bp, MASTS[0]);
    trailFor(E, 0.1, 0.045, 0.006, 1.3);
    bglow.set(0, bp, 0.5, 0.5, [1.0, 0.85, 0.6], 0.7, 0); bglow.set(1, bp, 0, 0, [0, 0, 0], 0, 1); bglow.touch();
    return [world, cam, { flares: mastFlares(1.2) }];
  },
  net(t, lt) {
    show({ ball: true, trail: true, keeper: true, goals: true });
    const E = 0.6 + lt * 0.133;
    keeperAt(E);
    const bp = ballAt(E);
    look(V(56.5 - 0.15 * lt, 1.3, -1.9), V(53.6, 1.95, -3.1), 50, -0.04);
    env({ fog: 0.004, exposure: 1.05, flash: 0.22, rate: 5, beam: 0.05 });
    st.lights(cam, 1, 1);
    st.netMat.uniforms.uHit.value.set(0, BN.y, BN.z, E - TF);
    st.netMat.uniforms.uAmp.value = E > TF ? 1 : 0;
    rims(figMat, keeper.root.position, null, null, 1.2, 0.9);
    ball.mesh.position.copy(bp); spinBall(E); ballLight(bp, MASTS[1]);
    trailFor(Math.min(E, TF), 0.1, 0.045, 0.006, 1.0 * (1 - ss(TF, TF + 0.06, E)));
    bglow.set(0, bp, 0.5, 0.5, [1.0, 0.85, 0.6], 0.6 * (1 - ss(TF, TF + 0.1, E)), 0);
    bglow.set(1, bp, 1.1, 1.1, [1, 0.95, 0.85], 0.7 * (E > TF ? Math.exp(-(E - TF) / 0.025) : 0), 1); bglow.touch();
    return [world, cam];
  },
  lights(t, lt) {
    show({ goals: false, beams: true });
    const M = st.masts[1], tgt = M.m.clone();
    const away = V(0, 0, 0).sub(M.m).normalize();
    const pos = M.m.clone().addScaledVector(away, 70 - 8 * lt).add(V(-8, -8, 0));
    look(pos, tgt, 34, 0.1 - 0.06 * lt);
    // the banks come on a row each sixteenth note, the glare blooms on the second beat
    const t0 = TEMPO.flashes[4][0];
    st.lights(cam, (mi, row) => (mi === 1 ? (t >= t0 + row * 0.156 ? 1 : 0.0) : 1), ss(t0 + 0.55, t0 + 0.75, t) * 0.75 + 0.1, 0.7, 0.75);
    env({ beam: 0.04 * ss(t0 + 0.5, t0 + 0.8, t), exposure: 1.0, flash: 0.08, stars: 0.6 });
    return [world, cam, { flares: mastFlares(1.4 * ss(t0 + 0.55, t0 + 0.75, t)) }];
  },
  hero(t, lt) {
    show({ striker: true, goals: true, beams: true });
    const base = V(-6, 0, 8);
    striker.pose(Object.assign(heroPose(t), { x: base.x, z: base.z, ry: 0.5 }));
    const a = 0.7 + 0.22 * lt;
    const pos = base.clone().add(V(Math.sin(a) * 2.4, 0.22, Math.cos(a) * 2.4));
    look(pos, base.clone().add(V(0, 1.55, 0)), 44, -0.05);
    env({ exposure: 1.0, flash: 0.08, beam: 0.06, fog: 0.004 });
    st.lights(cam, 1, 1.3);
    rims(figMat, base, null, null, 1.6, 1.1);
    return [world, cam, { flares: mastFlares(1.2) }];
  },
  bowl(t, lt) {
    show({ goals: true, beams: true });
    const a = 2.25 + 0.17 * lt;
    look(V(Math.cos(a) * 112, 72 - 4 * lt, Math.sin(a) * 92), V(0, -4, 0), 52, 0.03);
    env({ flash: 0.25, rate: 4, beam: 0.04, exposure: 1.0, stars: 0.5 });
    st.lights(cam, 1, 1);
    return [world, cam, { flares: mastFlares(0.8) }];
  },
  gk(t, lt) {
    show({ keeper: true, goals: true, beams: true });
    keeperAt(-1 + lt * 0.2);
    look(V(46.6 + 0.6 * lt, 0.42, 1.8), V(51.6, 1.15, -0.3), 40, -0.04);
    env({ exposure: 1.0, flash: 0.08, beam: 0.06 });
    st.lights(cam, 1, 1.3);
    rims(figMat, K0, null, null, 1.5, 1.0);
    return [world, cam, { flares: mastFlares(1.2) }];
  },
  boot2(t, lt) {
    show({ striker: true, ball: true, spray: true, blades: true });
    const E = -0.02 + lt * 0.07;
    strikerAt(E);
    const bp = ballAt(E);
    const pos = B0.clone().addScaledVector(FWD, 0.85).addScaledVector(RIGHT, -0.75).add(V(0, 0.08, 0));
    look(pos, B0.clone().addScaledVector(FWD, -0.2).add(V(0, 0.22, 0)), 30, -0.06);
    env({ fog: 0.004, exposure: 1.15 });
    st.lights(cam, 1, 0.8);
    rims(figMat, B0, null, null, 1.4, 1.0);
    ball.mesh.position.copy(bp); spinBall(E); ballLight(bp, MASTS[1]);
    spray.set(E, B0.clone().addScaledVector(FWD, -0.06).setY(0.02), SRY - Math.PI / 2);
    bglow.set(0, bp, 0, 0, [0, 0, 0], 0, 0);
    bglow.set(1, B0.clone().addScaledVector(FWD, -0.1).add(V(0, 0.05, 0)), 0.3, 0.3, [1, 0.95, 0.85], 1.1 * (E > 0 ? Math.exp(-E / 0.01) : 0), 1); bglow.touch();
    return [world, cam];
  },
  whip(t, lt) {
    show({ ball: true, trail: true, goals: true, beams: true });
    const E = 0.27 + lt * 0.23;
    const bp = ballAt(E), at = ballAt(0.36), dir = ballAt(0.37).sub(at).normalize(), side = V(0, 1, 0).cross(dir).normalize();
    const pos = at.clone().addScaledVector(side, 1.1).add(V(0, 0.15, 0));
    look(pos, pos.clone().addScaledVector(dir, -1).addScaledVector(side, -0.25).add(V(0, 0.08, 0)), 50, 0.12);
    env({ fog: 0.004, exposure: 1.05, flash: 0.1 });
    st.lights(cam, 1, 1.1);
    ball.mesh.position.copy(bp); spinBall(E); ballLight(bp, MASTS[1]);
    trailFor(E, 0.13, 0.05, 0.006, 1.5);
    bglow.set(0, bp, 0.5, 0.5, [1.0, 0.85, 0.6], 0.8, 0); bglow.set(1, bp, 0, 0, [0, 0, 0], 0, 1); bglow.touch();
    return [world, cam, { flares: mastFlares(1) }];
  },
  tunnel2(t, lt) {
    look(V(-0.5, 1.25, -7.5 - lt * 2), V(0.2, 1.5, TUN.END), 34, -0.03);
    tunnel.walk(3.6 + lt * 0.6, 1.3);
    env({ fog: 0.012, fogCol: [0.03, 0.035, 0.05], exposure: 1.1, vig: 0.75 });
    tunnel.mat.uniforms.uL1.value.copy(V(0, 1.6, TUN.END)).sub(V(0, 1, -16)).normalize().transformDirection(cam.matrixWorldInverse);
    tunnel.mat.uniforms.uL2.value.set(0, 1, 0).transformDirection(cam.matrixWorldInverse);
    return [tunnel.scene, cam];
  },
  spin(t, lt) {
    // round the striker on the centre spot, fast and low, the stands and their flashes wheeling past
    show({ striker: true, ball: true, goals: true, beams: true });
    const base = V(-0.25, 0, 0.45);
    striker.pose(Object.assign(heroPose(t), { x: base.x, z: base.z, ry: 2.6, head: -0.1 }));
    ball.mesh.position.set(0.05, 0.11, -0.1); spinBall(0);
    const a = 1.2 + eio(lt / 0.625) * 1.9, r = 3.4 - 0.8 * lt;
    look(base.clone().add(V(Math.sin(a) * r, 0.45 + 0.5 * lt, Math.cos(a) * r)), base.clone().add(V(0, 1.25, 0)), 52, 0.1);
    env({ flash: 0.3, rate: 5, exposure: 1.0, fog: 0.004 });
    st.lights(cam, 1, 1);
    rims(figMat, base, null, null, 1.4, 1.0);
    ballLight(V(0, 0, 0), MASTS[0]);
    bglow.set(0, V(0, 0, 0), 0, 0, [0, 0, 0], 0, 0); bglow.set(1, V(0, 0, 0), 0, 0, [0, 0, 0], 0, 1); bglow.touch();
    return [world, cam, { flares: mastFlares(1) }];
  },
  rise(t, lt) {
    show({ striker: true, ball: true, trail: true, constellation: true, goals: true, beams: true });
    const pos = V(0.2, 0.38, 2.4).add(RISE_OFF);
    const starDir = sky.C.ballStar.clone().normalize();
    const low = V(0, 1.1, -6).add(RISE_OFF).sub(pos).normalize();
    let dir = low.clone();
    // the kick, at full speed, then the ball away towards its star
    const Ek = (lt - 0.12) * 0.9;
    const k = kickPose(Ek);
    const root0 = RISE_ROOT;
    striker.pose(Object.assign(k, { x: root0.x, z: root0.z - k.pz, ry: Math.PI }));
    const p0 = RISE_P0;
    const target = pos.clone().add(sky.C.ballStar);
    const D = target.clone().sub(p0);
    const rf = (l) => (l < 0.12 ? 0 : Math.pow(clamp((l - 0.12) / 1.75), 2.4));
    const f = rf(lt);
    const bp = p0.clone().addScaledVector(D, f * 0.985);
    ball.mesh.position.copy(bp); spinBall(lt * 0.3);
    // the camera after the ball, then on its star
    const bd = bp.clone().sub(pos).normalize();
    dir = low.clone().lerp(bd, ss(0.15, 0.7, lt)).lerp(starDir, ss(1.2, 1.9, lt)).normalize();
    look(pos, pos.clone().add(dir), lerp(56, 28, eio(clamp((lt - 0.3) / 1.8))), 0.0);
    const dist = bp.distanceTo(pos);
    // its trail: back along its own path
    const pts = [];
    for (let i = 0; i <= 40; i++) { const fb = rf(lt - (i / 40) * 0.22); pts.push(p0.clone().addScaledVector(D, fb * 0.985)); }
    trail.update(pts, cam.position, 0.06 + dist * 0.0035, 0.004 + dist * 0.0006);
    trail.mat.uniforms.uA.value = 1.3 * ss(0.12, 0.2, lt) * (1 - ss(1.75, 1.95, lt));
    const tStar = TEMPO.star;
    bglow.set(0, bp, 0.35 + dist * 0.006, 0.35 + dist * 0.006, [1.0, 0.9, 0.7], 0.9 * ss(0.12, 0.25, lt), 0);
    bglow.set(1, bp, dist * 0.09, dist * 0.09, [1, 0.95, 0.85], 2.2 * pulse(t, tStar, 0.5) + 0.5 * ss(tStar, tStar + 0.2, t), 1);
    bglow.set(2, bp, dist * 0.35, dist * 0.012, [0.7, 0.8, 1.0], 1.4 * pulse(t, tStar, 0.6), 2);
    bglow.touch();
    const hush = ss(TEMPO.hush[0], TEMPO.hush[1], t);
    env({ stars: 0.75 + 0.25 * ss(0.8, 1.8, lt), milky: 0.55, cA: 0.25 + 0.75 * ss(tStar - 0.3, tStar + 0.2, t), fog: 0.004, flash: 0.15 * (1 - hush), rate: 4, exposure: 1.0 - 0.35 * hush, dim: 0.55 * hush, beam: 0.05 });
    st.lights(cam, 1, 0.3 * (1 - hush * 0.7), 0.8, 0.45);
    rims(figMat, root0, null, null, 1.3, 1.0);
    ballLight(bp, MASTS[1]);
    return [world, cam, { flares: mastFlares(0.35 * (1 - hush)).concat([[bp, 1.6 * pulse(t, tStar, 0.5)]]) }];
  },
  trophy(t, lt) {
    stage.frame(lt, t);
    env({ stars: 0.75, exposure: 1.0, vig: 0.65 });
    return [stage.scene, stage.cam];
  },
  card(t, lt) {
    show({ stadium: false });
    const a = 0.2 + 0.012 * lt;
    look(V(0, 0, 0), V(Math.sin(a) * 10, 6.5 + 0.05 * lt, -Math.cos(a) * 10), 50, 0.08);
    env({ stars: 0.8, milky: 0.7, fog: 0, exposure: 1.0, vig: 0.45 });
    card.frame(lt);
    return [world, cam];
  },
};
const SHOTS = TEMPO.shots;
export function shotAt(t) { for (const s of SHOTS) if (t < s.t1) return s; return SHOTS[SHOTS.length - 1]; }

// ---------------------------------------------------------------- one frame
export function draw(t, op) {
  fit();
  renderer.info.autoReset = false; renderer.info.reset();   // a frame's totals, both passes
  U.uTime.value = t;
  const sh = shotAt(t);
  card.group.visible = sh.id === 'card';
  const [scene, camera, extra] = S[sh.id](t, t - sh.t0, (t - sh.t0) / (sh.t1 - sh.t0));
  if (scene === world) { sky.group.position.copy(camera.position); }
  renderer.clear();
  renderer.render(scene, camera);
  // the screen layer: flashes on the hits, streaks on the beat, lens ghosts
  let fa = 0;
  for (const [t0, k] of TEMPO.flashes) if (t >= t0) fa += k * Math.exp(-(t - t0) / (k > 0.8 ? 0.2 : 0.12));
  hud.flashMat.uniforms.uA.value = Math.min(1.2, fa);
  let si = 0;
  TEMPO.streaks.forEach(([t0, y, ang], i) => {
    const u = (t - t0) / 0.45;
    if (u < 0 || u > 1 || si > 2) return;
    hud.streak(si++, u, y * H, ang, 1.2 * Math.sqrt(Math.sin(Math.PI * u)), i % 2 ? [0.55, 0.75, 1.0] : [1.0, 0.8, 0.5]);
  });
  for (; si < 3; si++) hud.streaks[si].visible = false;
  hud.flare(camera, (extra && extra.flares) || []);
  renderer.render(hud.scene, hud.cam);
  canvas.style.opacity = (op == null ? opacity(t) : op).toFixed(3);
}

export function info() {
  const i = renderer.info;
  return { calls: i.render.calls, tris: i.render.triangles, points: i.render.points, geos: i.memory.geometries, tex: i.memory.textures, scale, w: renderer.domElement.width, h: renderer.domElement.height };
}
// a moment of each shot drawn once (shaders compiled, buffers and textures sent to the GPU) before it is seen
export function warm(cb) {
  renderer.compile(world, cam); renderer.compile(tunnel.scene, cam); renderer.compile(stage.scene, stage.cam); renderer.compile(hud.scene, hud.cam);
  return SHOTS.map((s) => (s.t0 + s.t1) / 2);
}
export const gl = () => renderer.getContext();
window.INTRO = { init, draw, opacity, setScale, info, warm, gl, LEN: TEMPO.len, TEMPO, TROPHY, getScale: () => scale, parts: () => ({ st, sky, stage, tunnel }) };
