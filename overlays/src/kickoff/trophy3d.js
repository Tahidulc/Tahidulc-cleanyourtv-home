// 3D trophies for the kick-off caption mock-up: our own models of each competition's trophy, shaped from photos and
// published measurements (heights and widths), in polished silver and gold with studio reflections.
// window.TROPHY3D.render(kind, t, size) returns a transparent canvas of the trophy turned and lit for that moment.
import * as THREE from '../vendor/three.module.js';
import { RoomEnvironment } from '../vendor/RoomEnvironment.js';
import { trophyKit } from './trophy-models.js';

// Live overlays: without WebGL (or if anything here fails) the pop-up draws its flat trophies instead.
try { boot(); } catch (e) { console.warn('3D trophies off: ' + e); }
window.TROPHY3D_READY = true;

function boot() {

const W = 680, H = 620;           // the canvas the trophy is drawn into
const canvas = document.createElement('canvas');
canvas.width = W; canvas.height = H;
const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true, preserveDrawingBuffer: true, premultipliedAlpha: false });
renderer.setPixelRatio(1);
renderer.setSize(W, H, false);
renderer.setClearColor(0x000000, 0);
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.05;
renderer.outputColorSpace = THREE.SRGBColorSpace;

const scene = new THREE.Scene();
const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.03).texture;
const key = new THREE.DirectionalLight(0xffffff, 2.2); key.position.set(-2.5, 3, 3); scene.add(key);
const rim = new THREE.DirectionalLight(0xdfe8ff, 1.6); rim.position.set(3, 1.5, -2.5); scene.add(rim);
const fill = new THREE.DirectionalLight(0xfff2dd, 0.6); fill.position.set(2, -1, 3); scene.add(fill);
const camera = new THREE.PerspectiveCamera(20, W / H, 0.1, 50);

// the models (trophy-models.js): materials, the trophies, their ribbons
const { BUILD, ribbonsFor, shapeRibbon } = trophyKit();

const MODELS = {};
function model(kind) {
  if (!MODELS[kind]) {
    const T = (BUILD[kind] || BUILD.generic)();
    const box0 = new THREE.Box3().setFromObject(T);
    T.add(ribbonsFor(kind));
    // centre it: base at y 0, height 1 (measured before the ribbons, which only hang)
    T.userData.h = box0.max.y - box0.min.y;
    T.position.y = -box0.min.y;
    const pivot = new THREE.Group(); pivot.add(T); pivot.visible = false; scene.add(pivot);
    MODELS[kind] = pivot;
  }
  return MODELS[kind];
}

// frame: the trophy's full height fills `fill` of the canvas height, its base at the canvas's foot
function render(kind, rotY, tilt = 0, time = 0) {
  for (const k in MODELS) MODELS[k].visible = false;
  const m = model(kind); m.visible = true;
  m.traverse((o) => { if (o.userData && o.userData.rib) { o.visible = !window.TROPHY_NO_RIBBONS; shapeRibbon(o, time); } });   // (no ribbons: for measuring only)
  m.rotation.set(tilt, rotY, 0);
  const h = m.children[0].userData.h || 1;
  const dist = 4.2;
  const vfov = THREE.MathUtils.degToRad(camera.fov);
  const viewH = 2 * dist * Math.tan(vfov / 2);  // world height visible at the trophy
  const fill = 0.9;
  const s = (viewH * fill) / h;
  m.scale.setScalar(s);
  camera.position.set(0, viewH * 0.48, dist);
  camera.lookAt(0, viewH * 0.48, 0);
  m.position.y = viewH * 0.48 - viewH / 2 + viewH * 0.02;
  renderer.render(scene, camera);
  return canvas;
}

// setRes(f): the trophy drawn into a canvas f times the size each way (a slower TV draws fewer pixels)
function setRes(f) { const k = Math.max(0.3, Math.min(1, f || 1)); renderer.setSize(Math.round(W * k), Math.round(H * k), false); }
window.TROPHY3D = { render, setRes, W, H, kinds: Object.keys(BUILD) };
window.TROPHY3D_READY = true;
}
