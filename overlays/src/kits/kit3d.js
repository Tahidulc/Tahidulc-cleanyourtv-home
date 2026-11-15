// A football shirt in 3D: the outline of a short-sleeved shirt extruded with rounded edges, finely divided and shaped (a
// chest that curves round to the sides, sleeves set back a little, soft folds), a collar sewn on as a tube, and the kit
// painted on: front and back on one texture (name and number on the back), the club's crest on the chest. The maker's
// marks and the sponsor are left off. Lit by a room's soft light with a key and a rim light.
// Used by the studio line-ups (src/studio: the title card's shirt) and by the kit pictures the apps' scoreboards draw
// (src/kits/sheet.js: a shirt turned through its angles, frame by frame). The kits themselves: designs.js.
import * as THREE from 'three';
import { RoomEnvironment } from '../vendor/RoomEnvironment.js';

export const XS = 0.66, YS = 0.66;   // the texture's half-extent in shirt units (front: left half, back: right half)

function outline() {
  const s = new THREE.Shape();
  s.moveTo(-0.115, 0.505);
  s.quadraticCurveTo(0, 0.415, 0.115, 0.505);
  s.lineTo(0.335, 0.448);
  s.lineTo(0.585, 0.205);
  s.quadraticCurveTo(0.598, 0.178, 0.575, 0.158);
  s.lineTo(0.478, 0.058);
  s.quadraticCurveTo(0.462, 0.044, 0.446, 0.06);
  s.lineTo(0.302, 0.19);
  s.quadraticCurveTo(0.282, -0.08, 0.31, -0.585);
  s.quadraticCurveTo(0.312, -0.622, 0.268, -0.624);
  s.quadraticCurveTo(0, -0.64, -0.268, -0.624);
  s.quadraticCurveTo(-0.312, -0.622, -0.31, -0.585);
  s.quadraticCurveTo(-0.282, -0.08, -0.302, 0.19);
  s.lineTo(-0.446, 0.06);
  s.quadraticCurveTo(-0.462, 0.044, -0.478, 0.058);
  s.lineTo(-0.575, 0.158);
  s.quadraticCurveTo(-0.598, 0.178, -0.585, 0.205);
  s.lineTo(-0.335, 0.448);
  s.lineTo(-0.115, 0.505);
  return s;
}

/** Every triangle split until none of its edges is longer than max (non-indexed positions in, out). */
function tessellate(pos, max) {
  const out = [];
  const stack = [];
  for (let i = 0; i < pos.length; i += 9) stack.push(pos.slice(i, i + 9));
  const d = (t, a, b) => Math.hypot(t[a * 3] - t[b * 3], t[a * 3 + 1] - t[b * 3 + 1], t[a * 3 + 2] - t[b * 3 + 2]);
  while (stack.length) {
    const t = stack.pop();
    const e01 = d(t, 0, 1), e12 = d(t, 1, 2), e20 = d(t, 2, 0);
    const m = Math.max(e01, e12, e20);
    if (m <= max) { for (const v of t) out.push(v); continue; }
    // split the longest edge
    let a, b, c;
    if (m === e01) { a = 0; b = 1; c = 2; } else if (m === e12) { a = 1; b = 2; c = 0; } else { a = 2; b = 0; c = 1; }
    const mid = [(t[a * 3] + t[b * 3]) / 2, (t[a * 3 + 1] + t[b * 3 + 1]) / 2, (t[a * 3 + 2] + t[b * 3 + 2]) / 2];
    const A = t.slice(a * 3, a * 3 + 3), B = t.slice(b * 3, b * 3 + 3), C = t.slice(c * 3, c * 3 + 3);
    stack.push([...A, ...mid, ...C]);
    stack.push([...mid, ...B, ...C]);
  }
  return out;
}

const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };

/** The shirt's body geometry (welded, smooth normals, uv as the texture's front and back halves); fine: the longest edge. */
function bodyGeometry(fine = 0.028) {
  const depth = 0.17, bevel = 0.075;
  const g = new THREE.ExtrudeGeometry(outline(), { depth, bevelEnabled: true, bevelThickness: bevel, bevelSize: 0.05,
    bevelSegments: fine > 0.035 ? 5 : 7, curveSegments: fine > 0.035 ? 16 : 22, steps: 2 });
  g.translate(0, 0, -depth / 2);
  const src = (g.index ? g.toNonIndexed() : g).getAttribute('position').array;
  g.dispose();
  const tri = tessellate(Array.from(src), fine);
  // weld by position
  const key = (x, y, z) => Math.round(x * 2e4) + ',' + Math.round(y * 2e4) + ',' + Math.round(z * 2e4);
  const map = new Map(), verts = [], index = [];
  for (let i = 0; i < tri.length; i += 3) {
    const k = key(tri[i], tri[i + 1], tri[i + 2]);
    let id = map.get(k);
    if (id === undefined) { id = verts.length / 3; map.set(k, id); verts.push(tri[i], tri[i + 1], tri[i + 2]); }
    index.push(id);
  }
  const zmax = depth / 2 + bevel;
  const uv = new Float32Array(verts.length / 3 * 2);
  for (let i = 0; i < verts.length; i += 3) {
    const x = verts[i], y = verts[i + 1], z = verts[i + 2];
    const front = z >= 0;
    // the texture: where this point is on the front (or back) of the shirt
    const u = front ? (x + XS) / (2 * XS) * 0.5 : 0.5 + (XS - x) / (2 * XS) * 0.5;
    uv[i / 3 * 2] = u;
    uv[i / 3 * 2 + 1] = (y + YS) / (2 * YS);
    // shaping: the chest and back rounded, curving away towards the sides; the sleeves set back; soft folds
    const side = Math.abs(x);
    const torso = 1 - smooth(0.26, 0.36, side);
    const k = Math.abs(z) / zmax;   // 1 on the faces, less round the edges
    const round = (0.085 * (1 - Math.pow(Math.min(1, side / 0.34), 2))) * torso * smooth(0.0, 0.6, k);
    const hemTaper = smooth(-0.62, -0.36, y);
    let nz = z + Math.sign(z || 1) * round * (0.55 + 0.45 * hemTaper);
    const sleeve = smooth(0.29, 0.4, side);
    nz -= sleeve * (side - 0.29) * 0.32;
    const ny = y - sleeve * (side - 0.29) * 0.05;
    // folds: a few soft ripples, more at the waist and under the arms
    const fold = 0.0045 * Math.sin(y * 13 + x * 4 + Math.sin(x * 9) * 0.8) * smooth(-0.1, 0.5, -y)
      + 0.003 * Math.sin(x * 19 - y * 8) * smooth(0.2, 0.5, side) + 0.0035 * Math.sin((x + y) * 17) * sleeve;
    nz += Math.sign(z || 1) * fold * smooth(0.3, 0.9, k);
    verts[i + 1] = ny;
    verts[i + 2] = nz;
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(verts, 3));
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  geo.setIndex(index);
  geo.computeVertexNormals();
  return geo;
}

/** The collar: a tube round the neck, lower at the front. */
function collarGeometry(r = 0.02, fine = false) {
  const pts = [];
  for (let i = 0; i < 48; i++) {
    const a = (i / 48) * Math.PI * 2;
    // round the shaped neck: the chest and back stand out (bodyGeometry's rounding) so the ring is deep front to back
    const x = Math.sin(a) * 0.122, z = Math.cos(a) * 0.232;
    const y = 0.502 - 0.074 * Math.pow(Math.abs(Math.cos(a)), 1.6);
    pts.push(new THREE.Vector3(x, y, z));
  }
  return new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts, true), fine ? 96 : 64, r, fine ? 10 : 8, true);
}

// ---- the kits ------------------------------------------------------------------------------------------------------

/**
 * The kit painted on a canvas twice as wide as size (2048 x 1024 at 1024): the front on the left half, the back on the
 * right, both as seen from outside. kit (designs.js): {body, sleeves, trim, collar, collarEdge, cuff, cuffEdge, piping,
 * stripes: [[x0, x1], ...] (on the body, shirt units, stripeColour), sleeveStripes, hoops: [[y0, y1], ...] (hoopColour),
 * sash: {from: [x, y], to: [x, y], bands: [[off0, off1, colour], ...]} (the front only), raglan, crest (an image),
 * name, number, numberColour, numberEdge}.
 */
export function paintKit(kit, size = 1024) {
  const c = document.createElement('canvas');
  c.width = size * 2; c.height = size;
  const g = c.getContext('2d');
  const PX = size / (2 * XS), PY = size / (2 * YS);
  const fx = (x) => (x + XS) * PX, fy = (y) => (YS - y) * PY;
  for (const back of [false, true]) {
    g.save();
    if (back) {
      // the back: the same drawing seen from behind (mirrored), on the right half
      g.translate(size * 2, 0); g.scale(-1, 1);
    }
    g.beginPath(); g.rect(0, 0, size, size); g.clip();
    g.fillStyle = kit.body; g.fillRect(0, 0, size, size);
    // body stripes (vertical), across the torso
    if (kit.stripes) {
      g.fillStyle = kit.stripeColour || '#111';
      for (const [a, b] of kit.stripes) g.fillRect(fx(a), 0, (b - a) * PX, size);
    }
    // hoops (across)
    if (kit.hoops) {
      g.fillStyle = kit.hoopColour || '#111';
      for (const [a, b] of kit.hoops) g.fillRect(0, fy(b), size, (b - a) * PY);
    }
    // a sash across the front, from one shoulder to the other side's hip
    if (kit.sash && !back) {
      const [x0, y0] = kit.sash.from, [x1, y1] = kit.sash.to;
      const ax = fx(x0), ay = fy(y0), bx = fx(x1), by = fy(y1);
      const ang = Math.atan2(by - ay, bx - ax), len = Math.hypot(bx - ax, by - ay);
      g.save();
      g.translate(ax, ay); g.rotate(ang);
      for (const [o0, o1, col] of kit.sash.bands) { g.fillStyle = col; g.fillRect(-len, o0 * PX, len * 3, (o1 - o0) * PX); }
      g.restore();
    }
    // sleeves: their own colour, raglan from the neck to the armpit, their stripes along the arm
    const sleeveRegion = (sideSign) => {
      g.beginPath();
      if (kit.raglan) {
        g.moveTo(fx(sideSign * 0.105), fy(0.53));
        g.quadraticCurveTo(fx(sideSign * 0.2), fy(0.33), fx(sideSign * 0.298), fy(0.192));
      } else {
        g.moveTo(fx(sideSign * 0.33), fy(0.56));
        g.lineTo(fx(sideSign * 0.305), fy(0.2));
      }
      g.lineTo(fx(sideSign * 0.42), fy(-0.04));
      g.lineTo(fx(sideSign * 0.7), fy(0.1));
      g.lineTo(fx(sideSign * 0.7), fy(0.6));
      g.lineTo(fx(sideSign * 0.105), fy(0.6));
      g.closePath();
    };
    for (const s of [-1, 1]) {
      g.save();
      sleeveRegion(s);
      g.clip();
      if (kit.sleeves) { g.fillStyle = kit.sleeves; g.fillRect(0, 0, size, size); }
      if (kit.sleeveStripes) {
        // stripes along the sleeve (it points down and out at about 42 degrees)
        g.translate(fx(s * 0.47), fy(0.19));
        g.rotate(s * -0.75);
        g.fillStyle = kit.stripeColour || '#111';
        for (const [a, b] of kit.sleeveStripes) g.fillRect(a * PX, -size * 0.6, (b - a) * PX, size * 1.2);
      }
      g.restore();
    }
    // piping along the raglan seam (or the shoulder), one line in the trim colour
    const piping = kit.piping === undefined ? kit.trim : kit.piping;
    if (piping) {
      g.strokeStyle = piping; g.lineWidth = 0.014 * PX; g.lineCap = 'round';
      for (const s of [-1, 1]) {
        g.beginPath();
        if (kit.raglan) {
          g.moveTo(fx(s * 0.108), fy(0.51));
          g.quadraticCurveTo(fx(s * 0.2), fy(0.33), fx(s * 0.298), fy(0.192));
        } else {
          g.moveTo(fx(s * 0.12), fy(0.5));
          g.lineTo(fx(s * 0.335), fy(0.446));
          g.lineTo(fx(s * 0.585), fy(0.205));
        }
        g.stroke();
      }
    }
    // cuffs: a band along the end of each sleeve (from its top corner to its bottom), its inner edge in the trim colour
    for (const s of [-1, 1]) {
      const ax = s * 0.588, ay = 0.2, bx = s * 0.465, by = 0.05;
      // in from the sleeve's end, towards the shoulder
      let nx = -(by - ay), ny = (bx - ax);
      if (nx * s > 0) { nx = -nx; ny = -ny; }
      const L = Math.hypot(nx, ny); nx /= L; ny /= L;
      const w = 0.05, o = 0.05;
      g.fillStyle = kit.cuff || kit.trim;
      g.beginPath();
      g.moveTo(fx(ax - nx * o), fy(ay - ny * o));
      g.lineTo(fx(bx - nx * o), fy(by - ny * o));
      g.lineTo(fx(bx + nx * w), fy(by + ny * w));
      g.lineTo(fx(ax + nx * w), fy(ay + ny * w));
      g.closePath();
      g.fill();
      g.strokeStyle = kit.cuffEdge || kit.trim; g.lineWidth = 0.009 * PX;
      g.beginPath(); g.moveTo(fx(bx + nx * w), fy(by + ny * w)); g.lineTo(fx(ax + nx * w), fy(ay + ny * w)); g.stroke();
    }
    // the neckline: the collar's colour where the collar meets the shirt
    g.strokeStyle = kit.collar; g.lineWidth = 0.05 * PX;
    g.beginPath(); g.moveTo(fx(-0.13), fy(0.51)); g.quadraticCurveTo(fx(0), fy(back ? 0.47 : 0.405), fx(0.13), fy(0.51)); g.stroke();
    // a soft sheen down the middle, darker at the sides: the cloth's own light
    const sh = g.createLinearGradient(fx(-0.33), 0, fx(0.33), 0);
    sh.addColorStop(0, 'rgba(0,0,0,0.10)'); sh.addColorStop(0.5, 'rgba(255,255,255,0.05)'); sh.addColorStop(1, 'rgba(0,0,0,0.10)');
    g.fillStyle = sh; g.fillRect(0, 0, size, size);
    g.restore();
  }
  // the crest on the left breast (the right as you look at it), drawn the right way round on the front half
  if (kit.crest && kit.crest.width > 0) {
    const s = 0.15 * PX, cx = fx(0.155), cy = fy(0.3);
    const k = Math.min(s / kit.crest.width, s / kit.crest.height);
    try { g.drawImage(kit.crest, cx - kit.crest.width * k / 2, cy - kit.crest.height * k / 2, kit.crest.width * k, kit.crest.height * k); } catch (e) { /* a broken picture: no crest */ }
  }
  // the back: the name over the number (drawn unmirrored on the right half: x mirrored by hand)
  if (kit.number) {
    const bfx = (x) => size * 2 - (x + XS) * PX;   // the back half, seen from behind
    g.save();
    g.textAlign = 'center'; g.textBaseline = 'alphabetic';
    const edge = kit.numberEdge;
    if (kit.name) {
      let fs = Math.round(0.075 * PY);
      g.font = `800 ${fs}px "BarlowC", "Barlow Condensed", sans-serif`;
      // a long name narrows to the shoulders' width
      while (g.measureText(kit.name).width > 0.5 * PX && fs > 8) { fs -= 1; g.font = `800 ${fs}px "BarlowC", "Barlow Condensed", sans-serif`; }
      if (edge) { g.lineWidth = 0.012 * PX; g.strokeStyle = edge; g.strokeText(kit.name, bfx(0), fy(0.29)); }
      g.fillStyle = kit.numberColour; g.fillText(kit.name, bfx(0), fy(0.29));
    }
    g.font = `800 ${Math.round(0.4 * PY)}px "BarlowC", "Barlow Condensed", sans-serif`;
    if (edge) { g.lineWidth = 0.02 * PX; g.strokeStyle = edge; g.strokeText(kit.number, bfx(0), fy(-0.1)); }
    g.fillStyle = kit.numberColour; g.fillText(kit.number, bfx(0), fy(-0.1));
    g.restore();
  }
  return c;
}

/** A jersey's fine mesh, for the bump map: rows of tiny holes. */
function meshBump() {
  const c = document.createElement('canvas');
  c.width = c.height = 256;
  const g = c.getContext('2d');
  g.fillStyle = '#808080'; g.fillRect(0, 0, 256, 256);
  g.fillStyle = '#5a5a5a';
  for (let y = 0; y < 256; y += 8) for (let x = (y / 8) % 2 ? 4 : 0; x < 256; x += 8) { g.beginPath(); g.arc(x, y, 1.6, 0, Math.PI * 2); g.fill(); }
  return c;
}

/**
 * A stage for shirts: a WebGL canvas w x h (see-through), its camera, lights and environment; add(kit) puts a shirt on
 * it; render() draws. opts: {dist, fov, exposure, rim, fine (the mesh's longest edge: 0.028 for big, 0.045 for small),
 * tex (the kit texture's height: 1024 big, 512 small), antialias}.
 */
export function stage(w, h, opts = {}) {
  const canvas = opts.canvas || document.createElement('canvas');
  canvas.width = w; canvas.height = h;
  const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: opts.antialias !== false, preserveDrawingBuffer: true, premultipliedAlpha: true });
  renderer.setPixelRatio(1);
  renderer.setSize(w, h, false);
  renderer.setClearColor(0x000000, 0);
  renderer.toneMapping = THREE.NeutralToneMapping;
  renderer.toneMappingExposure = opts.exposure || 0.95;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  const scene = new THREE.Scene();
  const pmrem = new THREE.PMREMGenerator(renderer);
  const envRT = pmrem.fromScene(new RoomEnvironment(), 0.04);
  scene.environment = envRT.texture;
  pmrem.dispose();
  const camera = new THREE.PerspectiveCamera(opts.fov || 26, w / h, 0.1, 50);
  camera.position.set(0, 0.05, opts.dist || 3.0);
  camera.lookAt(0, 0, 0);
  const key = new THREE.DirectionalLight(0xffffff, 2.1); key.position.set(-1.6, 2.2, 2.6); scene.add(key);
  const rim = new THREE.DirectionalLight(opts.rim || 0x9fb4ff, 1.4); rim.position.set(2.4, 1.2, -2.0); scene.add(rim);
  const fill = new THREE.DirectionalLight(0xffffff, 0.35); fill.position.set(1.5, -0.6, 2.0); scene.add(fill);
  const fine = opts.fine || 0.028, texSize = opts.tex || 1024;
  const body = bodyGeometry(fine), collar = collarGeometry(0.02, fine < 0.035);
  const bumpC = meshBump();
  const bump = new THREE.CanvasTexture(bumpC);
  bump.wrapS = bump.wrapT = THREE.RepeatWrapping; bump.repeat.set(26, 13);
  const shirts = [];
  function material(kit) {
    const tex = new THREE.CanvasTexture(paintKit(kit, texSize));
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.anisotropy = 4;
    return new THREE.MeshStandardMaterial({ map: tex, roughness: 0.72, metalness: 0.0, bumpMap: bump, bumpScale: 0.6, envMapIntensity: 0.55 });
  }
  function add(kit) {
    const mat = material(kit);
    const shirt = new THREE.Group();
    shirt.add(new THREE.Mesh(body, mat));
    const cm = new THREE.MeshStandardMaterial({ color: new THREE.Color(kit.collar), roughness: 0.6, envMapIntensity: 0.8 });
    shirt.add(new THREE.Mesh(collar, cm));
    const extra = [cm];
    if (kit.collarEdge) {
      const em = new THREE.MeshStandardMaterial({ color: new THREE.Color(kit.collarEdge), roughness: 0.5 });
      const ce = new THREE.Mesh(collarGeometry(0.0065, fine < 0.035), em);
      ce.position.y = 0.019; ce.scale.set(1.07, 1, 1.07);
      shirt.add(ce);
      extra.push(em, ce.geometry);
    }
    scene.add(shirt);
    shirt.userData = { mat, kit, extra };
    shirts.push(shirt);
    return shirt;
  }
  /** A shirt's back made again with this name and number. */
  function reback(shirt, name, number) {
    const kit = Object.assign({}, shirt.userData.kit, { name, number });
    const old = shirt.userData.mat.map;
    shirt.userData.mat.map = material(kit).map;
    shirt.userData.mat.needsUpdate = true;
    if (old) old.dispose();
    shirt.userData.kit = kit;
  }
  /** Only this one shown (null: none). */
  function only(shirt) { for (const s of shirts) s.visible = s === shirt; }
  function render() { renderer.render(scene, camera); return canvas; }
  function free(s) {
    const u = s.userData;
    if (u.mat.map) u.mat.map.dispose();
    u.mat.dispose();
    for (const e of u.extra) e.dispose();
  }
  /** A shirt taken off the stage, its textures let go. */
  function drop(shirt) {
    const i = shirts.indexOf(shirt);
    if (i < 0) return;
    shirts.splice(i, 1);
    scene.remove(shirt);
    free(shirt);
  }
  function dispose() {
    for (const s of shirts) free(s);
    shirts.length = 0;
    body.dispose(); collar.dispose(); bump.dispose(); envRT.dispose();
    renderer.dispose();
    try { renderer.forceContextLoss(); } catch (e) { /* gone already */ }
  }
  return { canvas, scene, camera, add, reback, only, render, renderer, drop, dispose };
}
