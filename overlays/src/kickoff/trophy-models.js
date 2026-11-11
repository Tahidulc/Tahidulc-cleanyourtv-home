// The trophies themselves: our own models of each competition's trophy, shaped from photos, public descriptions and
// published measurements (heights and widths), in polished silver and gold. No emblem or lettering of a competition on any
// of them (the Premier League's ribbons carry its name, as the real ones do). Shared by the kick-off pop-up's trophy
// (trophy3d.js, which lights and turns one for the pop-up).
// trophyKit() makes the materials and returns { BUILD: { kind() -> THREE.Group }, ribbonsFor(kind), shapeRibbon(mesh, t) }.
import * as THREE from '../vendor/three.module.js';

export function trophyKit() {

// ---------------------------------------------------------------- materials
const silver = new THREE.MeshPhysicalMaterial({ color: 0xeef1f4, metalness: 1, roughness: 0.13, clearcoat: 0.4, clearcoatRoughness: 0.1 });
const silverSatin = new THREE.MeshPhysicalMaterial({ color: 0xd9dde2, metalness: 1, roughness: 0.32 });
const gold = new THREE.MeshPhysicalMaterial({ color: 0xffc85a, metalness: 1, roughness: 0.18, clearcoat: 0.3 });
const ebony = new THREE.MeshPhysicalMaterial({ color: 0x0d0e10, metalness: 0.1, roughness: 0.28, clearcoat: 1, clearcoatRoughness: 0.08 });
function stoneTexture(kind) {
  const c = document.createElement('canvas'); c.width = 1024; c.height = 256;
  const g = c.getContext('2d');
  let seed = kind === 'malachite' ? 7 : 19;
  const R = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
  if (kind === 'malachite') {
    g.fillStyle = '#0d5a3e'; g.fillRect(0, 0, 1024, 256);
    for (let i = 0; i < 70; i++) { // concentric bands, the stone's rings
      const cx = R() * 1024, cy = R() * 256, rr = 20 + R() * 120;
      for (let k = 0; k < 9; k++) {
        g.beginPath(); g.ellipse(cx, cy, rr * (1 - k * 0.1) * 1.6, rr * (1 - k * 0.1), R() * 0.4, 0, Math.PI * 2);
        g.strokeStyle = k % 2 ? `rgba(4,40,26,${0.35 + R() * 0.3})` : `rgba(60,170,120,${0.25 + R() * 0.3})`;
        g.lineWidth = 2 + R() * 5; g.stroke();
      }
    }
  } else { // yellow marble
    const gr = g.createLinearGradient(0, 0, 1024, 256); gr.addColorStop(0, '#d9b65a'); gr.addColorStop(0.5, '#e8c977'); gr.addColorStop(1, '#cfa64a');
    g.fillStyle = gr; g.fillRect(0, 0, 1024, 256);
    for (let i = 0; i < 40; i++) {
      g.beginPath(); let x = R() * 1024, y = R() * 256; g.moveTo(x, y);
      for (let k = 0; k < 12; k++) { x += (R() - 0.3) * 60; y += (R() - 0.5) * 30; g.lineTo(x, y); }
      g.strokeStyle = `rgba(${R() < 0.5 ? '120,80,30' : '250,235,190'},${0.15 + R() * 0.25})`; g.lineWidth = 0.6 + R() * 2; g.stroke();
    }
  }
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.wrapS = t.wrapT = THREE.RepeatWrapping;
  return t;
}
const malachite = new THREE.MeshPhysicalMaterial({ map: stoneTexture('malachite'), roughness: 0.22, metalness: 0, clearcoat: 1, clearcoatRoughness: 0.05 });
const marble = new THREE.MeshPhysicalMaterial({ map: stoneTexture('marble'), roughness: 0.3, metalness: 0, clearcoat: 1, clearcoatRoughness: 0.08 });

// ---------------------------------------------------------------- shape helpers (units: the trophy is 1 tall)
function profile(pts, n = 160) { // a smooth outline through [radius, height] points
  const curve = new THREE.SplineCurve(pts.map(([r, y]) => new THREE.Vector2(r, y)));
  return curve.getPoints(n).map((p) => new THREE.Vector2(Math.max(0.0005, p.x), p.y));
}
function lathe(pts, mat, seg = 128, opts = {}) {
  const geo = new THREE.LatheGeometry(opts.raw ? pts.map(([r, y]) => new THREE.Vector2(r, y)) : profile(pts, opts.n || 160), seg);
  if (opts.flute) { // vertical flutes: the radius rises and falls round the body within a height band
    const p = geo.attributes.position;
    for (let i = 0; i < p.count; i++) {
      const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
      if (y < opts.flute.y0 || y > opts.flute.y1) continue;
      const a = Math.atan2(z, x), k = Math.sin(Math.PI * (y - opts.flute.y0) / (opts.flute.y1 - opts.flute.y0));
      const s = 1 + opts.flute.depth * k * Math.cos(a * opts.flute.n);
      p.setX(i, x * s); p.setZ(i, z * s);
    }
    geo.computeVertexNormals();
  }
  if (opts.squash) geo.scale(1, 1, opts.squash);
  return new THREE.Mesh(geo, mat);
}
function tube(points3, radius, mat, opts = {}) {
  const curve = new THREE.CatmullRomCurve3(points3.map(([x, y, z]) => new THREE.Vector3(x, y, z || 0)), false, 'catmullrom', 0.5);
  const geo = new THREE.TubeGeometry(curve, opts.seg || 120, radius, opts.radial || 16, false);
  const m = new THREE.Mesh(geo, mat);
  if (opts.flat) m.scale.z = opts.flat;
  return m;
}
function ring(r, y, tube_, mat) {
  const m = new THREE.Mesh(new THREE.TorusGeometry(r, tube_, 16, 128), mat);
  m.rotation.x = Math.PI / 2; m.position.y = y; return m;
}
function sphere(r, mat) { return new THREE.Mesh(new THREE.SphereGeometry(r, 32, 20), mat); }
function mirrorX(obj) { const o = obj.clone(); o.scale.x *= -1; return o; }

// a small seated lion in gold, about 0.1 high, facing +x
function lion() {
  const L = new THREE.Group();
  const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.022, 0.05, 8, 16), gold); body.rotation.z = Math.PI / 2.6; body.position.set(-0.008, 0.035, 0); L.add(body);
  const haunch = sphere(0.028, gold); haunch.position.set(-0.03, 0.025, 0); L.add(haunch);
  const chest = sphere(0.026, gold); chest.position.set(0.018, 0.055, 0); L.add(chest);
  const mane = new THREE.Mesh(new THREE.IcosahedronGeometry(0.03, 1), gold); mane.position.set(0.03, 0.082, 0); L.add(mane);
  const head = sphere(0.019, gold); head.position.set(0.045, 0.085, 0); L.add(head);
  const muzzle = sphere(0.011, gold); muzzle.scale.set(1.2, 0.8, 1); muzzle.position.set(0.062, 0.078, 0); L.add(muzzle);
  for (const s of [-1, 1]) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.007, 0.008, 0.05, 10), gold); leg.position.set(0.03, 0.025, s * 0.012); L.add(leg);
    const ear = sphere(0.006, gold); ear.position.set(0.04, 0.104, s * 0.013); L.add(ear);
  }
  const tail = tube([[-0.05, 0.02], [-0.07, 0.04], [-0.065, 0.075], [-0.05, 0.085]], 0.004, gold, { seg: 24, radial: 8 }); L.add(tail);
  return L;
}

// a lion lying down with its head up, about 0.11 long, facing +x
function couchant() {
  const L = new THREE.Group();
  const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.02, 0.06, 8, 16), gold); body.rotation.z = Math.PI / 2; body.position.set(-0.01, 0.022, 0); body.scale.set(1, 1, 1.1); L.add(body);
  const haunch = sphere(0.024, gold); haunch.position.set(-0.045, 0.026, 0); L.add(haunch);
  const chest = sphere(0.024, gold); chest.position.set(0.028, 0.03, 0); L.add(chest);
  const mane = new THREE.Mesh(new THREE.IcosahedronGeometry(0.028, 1), gold); mane.position.set(0.04, 0.056, 0); L.add(mane);
  const head = sphere(0.018, gold); head.position.set(0.055, 0.06, 0); L.add(head);
  const muzzle = sphere(0.01, gold); muzzle.scale.set(1.3, 0.85, 1); muzzle.position.set(0.071, 0.053, 0); L.add(muzzle);
  for (const z of [-0.012, 0.012]) {
    const paw = new THREE.Mesh(new THREE.CapsuleGeometry(0.006, 0.035, 6, 10), gold); paw.rotation.z = Math.PI / 2; paw.position.set(0.06, 0.007, z); L.add(paw);
    const ear = sphere(0.006, gold); ear.position.set(0.048, 0.078, z); L.add(ear);
  }
  L.add(tube([[-0.065, 0.02], [-0.085, 0.015], [-0.095, 0.03], [-0.09, 0.045]], 0.0035, gold, { seg: 24, radial: 8 }));
  return L;
}

// ---------------------------------------------------------------- the trophies
const BUILD = {
  // Premier League: from the reference photo - a tall silver vase on a flared foot with a malachite ring, two gold
  // lions lying on its shoulders above long slim handles, and an open gold crown of zig-zag points with gold balls
  pl() {
    const T = new THREE.Group();
    T.add(lathe([[0.176, 0], [0.178, 0.012], [0.176, 0.042], [0.172, 0.05]], malachite, 128, { raw: true }));
    T.add(ring(0.177, 0.003, 0.004, silver)); T.add(ring(0.173, 0.05, 0.005, silver));
    T.add(lathe([[0.17, 0.05], [0.165, 0.065], [0.15, 0.08], [0.115, 0.1], [0.085, 0.125], [0.068, 0.15], [0.06, 0.17]], silver));
    T.add(ring(0.16, 0.072, 0.005, silver)); T.add(ring(0.1, 0.11, 0.005, silver));
    T.add(lathe([[0.06, 0.17], [0.055, 0.19], [0.062, 0.21], [0.085, 0.27], [0.11, 0.35], [0.13, 0.44], [0.142, 0.53], [0.146, 0.6], [0.142, 0.66], [0.13, 0.7], [0.115, 0.725], [0.09, 0.745], [0.074, 0.765], [0.068, 0.79], [0.07, 0.83], [0.078, 0.845]], silver, 192));
    T.add(ring(0.07, 0.8, 0.006, silver)); T.add(ring(0.062, 0.2, 0.006, silver));
    // the crown: an open band whose top runs in six points, each with a gold ball
    const crown = new THREE.Group();
    const segs = 240, pos = [], idx = [];
    const yB = 0.845, yLow = 0.885, yTip = 0.985, nPts = 6;
    for (const [rOff, flip] of [[0, false], [-0.006, true]]) {
      const start = pos.length / 3;
      for (let i = 0; i <= segs; i++) {
        const a = (i / segs) * Math.PI * 2, ph = ((a * nPts) / (Math.PI * 2)) % 1, tri = 1 - Math.abs(ph * 2 - 1);
        const yTop = yLow + (yTip - yLow) * tri;
        const r0 = 0.084 + rOff, r1 = 0.084 + (yTop - yB) * 0.5 + rOff;
        pos.push(r0 * Math.cos(a), yB, r0 * Math.sin(a), r1 * Math.cos(a), yTop, r1 * Math.sin(a));
      }
      for (let i = 0; i < segs; i++) {
        const a = start + i * 2, b = a + 1, c = a + 2, d = a + 3;
        if (flip) idx.push(a, c, b, b, c, d); else idx.push(a, b, c, b, d, c);
      }
    }
    const cg = new THREE.BufferGeometry(); cg.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); cg.setIndex(idx); cg.computeVertexNormals();
    crown.add(new THREE.Mesh(cg, new THREE.MeshPhysicalMaterial({ color: 0xffc85a, metalness: 1, roughness: 0.17, clearcoat: 0.3, side: THREE.DoubleSide })));
    crown.add(ring(0.085, yB + 0.004, 0.007, gold)); crown.add(ring(0.098, yB + 0.03, 0.005, gold));
    for (let i = 0; i < nPts; i++) {
      const a = ((i + 0.5) / nPts) * Math.PI * 2, r = 0.084 + (yTip - yB) * 0.5;
      const b = sphere(0.013, gold); b.position.set(r * Math.cos(a), yTip + 0.01, r * Math.sin(a)); crown.add(b);
    }
    crown.rotation.y = Math.PI / 12;
    T.add(crown);
    // long slim handles from the shoulders down to the foot
    for (const s of [-1, 1]) {
      T.add(tube([[s * 0.12, 0.715], [s * 0.2, 0.725], [s * 0.245, 0.7], [s * 0.25, 0.55], [s * 0.245, 0.33], [s * 0.2, 0.24], [s * 0.11, 0.21]], 0.0085, silver, { radial: 10, seg: 160 }));
    }
    // the lions, lying on the shoulders and looking out
    for (const s of [-1, 1]) {
      const l = couchant(); l.position.set(s * 0.14, 0.722, 0.012); l.scale.set(s * 1.0, 1.0, 1.0); l.rotation.y = s * -0.35; T.add(l);
    }
    return T;
  },
  // FA Cup: the 1911 design: lidded silver cup with a tall finial and two looped handles, on a black plinth (61.5 cm)
  facup() {
    const T = new THREE.Group();
    T.add(lathe([[0.18, 0], [0.18, 0.06], [0.176, 0.075]], ebony, 128, { raw: true }));
    T.add(ring(0.177, 0.075, 0.006, silver));
    T.add(lathe([[0.172, 0.078], [0.165, 0.1], [0.135, 0.15], [0.095, 0.2], [0.07, 0.235], [0.06, 0.25]], silver));
    T.add(lathe([[0.06, 0.25], [0.085, 0.265], [0.088, 0.28], [0.06, 0.295], [0.055, 0.31]], silver));
    T.add(lathe([[0.055, 0.31], [0.11, 0.33], [0.155, 0.37], [0.162, 0.42], [0.14, 0.47], [0.122, 0.5]], silver, 160, { flute: { y0: 0.31, y1: 0.49, n: 14, depth: 0.06 } }));
    T.add(lathe([[0.12, 0.5], [0.125, 0.56], [0.14, 0.64], [0.158, 0.71], [0.168, 0.745], [0.175, 0.755]], silver));
    T.add(ring(0.13, 0.5, 0.008, silver)); T.add(ring(0.172, 0.756, 0.007, silver));
    T.add(lathe([[0.17, 0.758], [0.168, 0.775], [0.145, 0.805], [0.1, 0.835], [0.06, 0.852], [0.045, 0.86]], silver, 160, { flute: { y0: 0.765, y1: 0.85, n: 16, depth: 0.05 } }));
    T.add(lathe([[0.045, 0.86], [0.03, 0.875], [0.042, 0.9], [0.028, 0.93], [0.032, 0.96], [0.018, 0.985], [0.001, 1.0]], silver));
    const hd = tube([[0.15, 0.725], [0.2, 0.75], [0.245, 0.72], [0.25, 0.64], [0.21, 0.58], [0.165, 0.555], [0.14, 0.54]], 0.011, silver);
    T.add(hd); T.add(mirrorX(hd));
    for (const s of [-1, 1]) { const curl = tube([[s * 0.2, 0.75], [s * 0.215, 0.77], [s * 0.232, 0.765], [s * 0.232, 0.748]], 0.008, silver, { seg: 30 }); T.add(curl); }
    return T;
  },
  // League Cup: a wide three-handled silver cup with a lid and finial on a stepped black base
  leaguecup() {
    const T = new THREE.Group();
    T.add(lathe([[0.21, 0], [0.21, 0.05], [0.19, 0.06], [0.19, 0.1], [0.17, 0.11]], ebony, 128, { raw: true }));
    T.add(ring(0.2, 0.05, 0.006, silver)); T.add(ring(0.18, 0.105, 0.006, silver));
    T.add(lathe([[0.165, 0.11], [0.15, 0.13], [0.1, 0.17], [0.07, 0.2], [0.06, 0.24]], silver));
    T.add(lathe([[0.06, 0.24], [0.08, 0.255], [0.06, 0.27], [0.065, 0.29]], silver));
    T.add(lathe([[0.065, 0.29], [0.13, 0.32], [0.2, 0.38], [0.235, 0.46], [0.24, 0.55], [0.25, 0.62], [0.262, 0.64]], silver, 160, { flute: { y0: 0.3, y1: 0.42, n: 18, depth: 0.04 } }));
    T.add(ring(0.258, 0.64, 0.007, silver));
    T.add(lathe([[0.245, 0.64], [0.235, 0.6], [0.2, 0.5], [0.12, 0.4], [0.06, 0.34]], silverSatin, 128, { raw: false }));
    for (let i = 0; i < 3; i++) {
      const g = new THREE.Group(); g.rotation.y = (i / 3) * Math.PI * 2 + Math.PI / 2;
      g.add(tube([[0.235, 0.6], [0.3, 0.62], [0.335, 0.57], [0.31, 0.48], [0.25, 0.44], [0.2, 0.42]], 0.012, silver));
      g.add(tube([[0.3, 0.62], [0.32, 0.65], [0.345, 0.64], [0.34, 0.615]], 0.008, silver, { seg: 30 }));
      T.add(g);
    }
    return T;
  },
  // Champions League: the tall silver vase with the big ears (73.5 cm)
  ucl() {
    const T = new THREE.Group();
    T.add(lathe([[0.165, 0], [0.168, 0.008], [0.16, 0.018], [0.11, 0.03], [0.075, 0.045]], silver));
    for (const [y, r] of [[0.055, 0.07], [0.075, 0.062], [0.095, 0.07], [0.115, 0.06]]) T.add(ring(r, y, 0.009, silver));
    T.add(lathe([[0.075, 0.045], [0.05, 0.06], [0.045, 0.13], [0.05, 0.15], [0.07, 0.17]], silver));
    T.add(lathe([[0.07, 0.17], [0.1, 0.21], [0.14, 0.3], [0.175, 0.42], [0.195, 0.55], [0.198, 0.63], [0.188, 0.69], [0.165, 0.73], [0.152, 0.745], [0.155, 0.765], [0.168, 0.79], [0.172, 0.8]], silver));
    T.add(ring(0.17, 0.8, 0.006, silver)); T.add(ring(0.153, 0.745, 0.005, silver));
    const ear = tube([[0.09, 0.22], [0.16, 0.3], [0.235, 0.45], [0.275, 0.62], [0.29, 0.8], [0.285, 0.94], [0.255, 0.995], [0.215, 0.985], [0.195, 0.93], [0.21, 0.88], [0.2, 0.83], [0.17, 0.8]], 0.016, silver, { flat: 0.45, seg: 200 });
    T.add(ear); T.add(mirrorX(ear));
    return T;
  },
  // Europa League: no handles; a broad fluted silver cup on a slim stem and a yellow marble plinth (65 x 33 x 23 cm)
  uel() {
    const T = new THREE.Group();
    const plinth = new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.12, 0.25, 1, 1, 1), marble); plinth.position.y = 0.06; T.add(plinth);
    T.add(lathe([[0.14, 0.12], [0.13, 0.135], [0.09, 0.16], [0.06, 0.18]], silver, 128, { squash: 0.75 }));
    T.add(lathe([[0.06, 0.18], [0.05, 0.22], [0.045, 0.27], [0.055, 0.31]], silver, 96, { squash: 0.75 }));
    T.add(ring(0.05, 0.29, 0.011, silver));
    T.add(lathe([[0.055, 0.31], [0.1, 0.36], [0.15, 0.44], [0.19, 0.56], [0.22, 0.7], [0.24, 0.84], [0.25, 0.95], [0.252, 0.99]], silver, 192, { flute: { y0: 0.33, y1: 0.98, n: 20, depth: 0.045 }, squash: 0.7 }));
    const lip = ring(0.25, 0.99, 0.008, silver); lip.scale.y = 0.7; T.add(lip);
    return T;
  },
  // Conference League: an open vase of 32 hexagonal silver spines on a round base (57.5 cm)
  uecl() {
    const T = new THREE.Group();
    T.add(lathe([[0.16, 0], [0.16, 0.04], [0.14, 0.06], [0.08, 0.07]], silver));
    for (let i = 0; i < 32; i++) {
      const a0 = (i / 32) * Math.PI * 2, pts = [];
      for (let k = 0; k <= 10; k++) {
        const y = 0.07 + k * 0.093, r = 0.07 + 0.17 * Math.pow(k / 10, 1.4) - 0.02 * Math.sin(Math.PI * k / 10), a = a0 + 0.5 * (k / 10);
        pts.push([r * Math.cos(a), y, r * Math.sin(a)]);
      }
      T.add(tube(pts, 0.0105, silver, { radial: 6, seg: 60 }));
    }
    T.add(ring(0.24, 1.0, 0.01, silver)); T.add(ring(0.075, 0.075, 0.012, silver));
    return T;
  },
  // any other competition: a plain two-handled silver cup on a black base
  generic() {
    const T = new THREE.Group();
    T.add(lathe([[0.17, 0], [0.17, 0.08], [0.155, 0.09]], ebony, 96, { raw: true }));
    T.add(lathe([[0.15, 0.09], [0.1, 0.14], [0.06, 0.2], [0.05, 0.3], [0.08, 0.36], [0.16, 0.48], [0.2, 0.62], [0.215, 0.75], [0.225, 0.8]], silver));
    const hd = tube([[0.2, 0.72], [0.28, 0.72], [0.3, 0.6], [0.24, 0.5], [0.17, 0.47]], 0.013, silver);
    T.add(hd); T.add(mirrorX(hd));
    return T;
  },
  // ---- the other leagues' trophies: our own shapes from their public descriptions, no emblem or lettering on any of them
  // La Liga: a broad silver cup, its lower bowl fluted and a gold band round its widest part, on a slim knopped stem and a
  // stepped round foot, no handles
  laliga() {
    const T = new THREE.Group();
    T.add(lathe([[0.2, 0], [0.202, 0.03], [0.195, 0.045], [0.17, 0.06], [0.165, 0.085], [0.13, 0.11], [0.09, 0.14], [0.07, 0.17]], silver));
    T.add(ring(0.2, 0.02, 0.006, silver)); T.add(ring(0.168, 0.075, 0.006, gold));
    T.add(lathe([[0.07, 0.17], [0.05, 0.21], [0.045, 0.27], [0.05, 0.32], [0.07, 0.36]], silver));
    T.add(ring(0.05, 0.255, 0.014, silver));
    T.add(lathe([[0.07, 0.36], [0.13, 0.385], [0.2, 0.43], [0.255, 0.51], [0.28, 0.61], [0.282, 0.71], [0.268, 0.81], [0.255, 0.88], [0.262, 0.94], [0.285, 0.99]], silver, 192, { flute: { y0: 0.37, y1: 0.6, n: 24, depth: 0.05 } }));
    T.add(ring(0.283, 0.66, 0.007, gold));
    T.add(ring(0.284, 0.99, 0.008, silver));
    T.add(lathe([[0.276, 0.99], [0.25, 0.93], [0.25, 0.85], [0.2, 0.62], [0.08, 0.42]], silverSatin, 128));
    return T;
  },
  // Serie A: a chalice: a tall trumpet bowl in silver girdled with gold beads, on a gold-knopped stem and a round foot
  seriea() {
    const T = new THREE.Group();
    T.add(lathe([[0.17, 0], [0.172, 0.025], [0.16, 0.04], [0.15, 0.06], [0.11, 0.085], [0.075, 0.11], [0.06, 0.13]], silver));
    T.add(ring(0.168, 0.014, 0.007, gold)); T.add(ring(0.151, 0.056, 0.005, silver));
    T.add(lathe([[0.06, 0.13], [0.042, 0.18], [0.036, 0.26], [0.04, 0.33], [0.055, 0.37]], silver));
    const knop = sphere(0.05, gold); knop.scale.set(1, 0.6, 1); knop.position.y = 0.3; T.add(knop);
    T.add(lathe([[0.055, 0.37], [0.075, 0.4], [0.1, 0.45], [0.125, 0.53], [0.15, 0.63], [0.18, 0.74], [0.22, 0.85], [0.26, 0.94], [0.29, 0.99], [0.3, 1.0]], silver, 192));
    T.add(ring(0.299, 1.0, 0.007, gold));
    T.add(lathe([[0.292, 0.998], [0.25, 0.93], [0.2, 0.83], [0.15, 0.68], [0.1, 0.5]], silverSatin, 128));
    // two girdles of gold beads round the bowl, each on a fine gold band
    for (const [y, r, n, rb] of [[0.63, 0.15, 24, 0.011], [0.48, 0.112, 18, 0.009]]) {
      T.add(ring(r, y, 0.004, gold));
      for (let i = 0; i < n; i++) {
        const a = (i / n) * Math.PI * 2, b = sphere(rb, gold);
        b.position.set((r + rb * 0.5) * Math.cos(a), y, (r + rb * 0.5) * Math.sin(a)); T.add(b);
      }
    }
    return T;
  },
  // Bundesliga: the champions' dish (59 cm across): a shallow silver bowl stood on its edge, plain bands where the
  // champions' names run, a gold inner ring set with five green stones and a raised boss in the middle
  bundesliga() {
    const T = new THREE.Group(), D = new THREE.Group();   // made lying face up (+y), then stood up to face +z
    // one closed outline: out along the underside, over the lip, back in along the face (so each side faces out)
    D.add(lathe([[0.001, -0.012], [0.08, -0.008], [0.2, 0.004], [0.27, 0.016], [0.293, 0.03], [0.301, 0.044], [0.297, 0.052], [0.282, 0.046],
      [0.25, 0.031], [0.2, 0.022], [0.12, 0.018], [0.08, 0.024], [0.064, 0.032], [0.001, 0.034]], silver, 192, { n: 220 }));
    const faceY = (r) => (r < 0.12 ? 0.018 + (0.12 - r) * 0.15 : r < 0.2 ? 0.018 + (r - 0.12) * 0.05 : 0.022 + (r - 0.2) * 0.18);
    for (const [r0, r1] of [[0.128, 0.152], [0.162, 0.186], [0.196, 0.222], [0.232, 0.258]]) {   // the bands of names, satin
      D.add(lathe([[r1, faceY(r1) + 0.0016], [r0, faceY(r0) + 0.0016]], silverSatin, 192, { raw: true }));
    }
    D.add(ring(0.087, 0.03, 0.008, gold));
    const stone = new THREE.MeshPhysicalMaterial({ color: 0x0c7a42, roughness: 0.05, metalness: 0, clearcoat: 1, clearcoatRoughness: 0.03, emissive: 0x02180b });
    for (let i = 0; i < 5; i++) {
      const a = (i / 5) * Math.PI * 2 + Math.PI / 2, s = new THREE.Mesh(new THREE.IcosahedronGeometry(0.014, 1), stone);
      s.scale.set(1, 0.6, 1); s.position.set(0.087 * Math.cos(a), 0.04, 0.087 * Math.sin(a)); D.add(s);
    }
    D.add(lathe([[0.066, 0.033], [0.058, 0.046], [0.035, 0.052], [0.001, 0.053]], silver, 128));
    D.rotation.x = Math.PI / 2 - 0.14;
    T.add(D);
    return T;
  },
  // Ligue 1: a hexagonal lantern: six silver blades that twist a sixth of a turn as they rise from a narrow waist to a wide
  // hexagonal mouth, on a stepped hexagonal base (the country's own shape)
  ligue1() {
    const T = new THREE.Group();
    const b1 = new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.215, 0.05, 6), silverSatin); b1.position.y = 0.025; T.add(b1);
    const b2 = new THREE.Mesh(new THREE.CylinderGeometry(0.155, 0.18, 0.05, 6), silver); b2.position.y = 0.075; T.add(b2);
    const blade = new THREE.MeshPhysicalMaterial({ color: 0xeef1f4, metalness: 1, roughness: 0.12, clearcoat: 0.4, clearcoatRoughness: 0.1, side: THREE.DoubleSide });
    const N = 60, y0 = 0.1, y1 = 1.0, rad = (s) => 0.155 - 0.075 * Math.sin(Math.PI * Math.min(1, s * 1.3)) + 0.16 * s * s;
    const corner = (i, s) => { const a = (i / 6) * Math.PI * 2 + (s * Math.PI) / 3 + Math.PI / 6, r = rad(s); return [r * Math.cos(a), y0 + s * (y1 - y0), r * Math.sin(a)]; };
    for (let i = 0; i < 6; i++) {
      const pos = [], idx = [], edgeA = [], edgeB = [];
      for (let k = 0; k <= N; k++) {
        const s = k / N, A = corner(i, s), B = corner(i + 1, s);
        const q0 = A.map((v, j) => v + (B[j] - v) * 0.07), q1 = A.map((v, j) => v + (B[j] - v) * 0.93);
        pos.push(...q0, ...q1);
        if (k % 4 === 0) { edgeA.push(q0); edgeB.push(q1); }
        if (k < N) { const a = k * 2; idx.push(a, a + 2, a + 1, a + 1, a + 2, a + 3); }
      }
      const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); g.setIndex(idx); g.computeVertexNormals();
      T.add(new THREE.Mesh(g, blade));
      T.add(tube(edgeA, 0.0055, silver, { radial: 8, seg: 60 })); T.add(tube(edgeB, 0.0055, silver, { radial: 8, seg: 60 }));
    }
    const top = new THREE.Mesh(new THREE.TorusGeometry(rad(1) * 0.93, 0.01, 10, 6), silver);
    top.rotation.x = Math.PI / 2; top.rotation.z = Math.PI / 6 + Math.PI / 3; top.position.y = y1; T.add(top);
    const waist = new THREE.Mesh(new THREE.TorusGeometry(rad(0) * 0.93, 0.009, 10, 6), gold);
    waist.rotation.x = Math.PI / 2; waist.rotation.z = Math.PI / 6; waist.position.y = y0 + 0.002; T.add(waist);
    return T;
  },
};

// ---------------------------------------------------------------- ribbons, as English trophies wear them
const RIBBON_TEX = {};
function shadeHex(hex, k) {
  const c = new THREE.Color(hex); c.multiplyScalar(k); return '#' + c.getHexString();
}
function ribbonTexture(color, text, ink) {
  const key = color + '|' + (text || '') + '|' + (ink || '');
  if (RIBBON_TEX[key]) return RIBBON_TEX[key];
  const c = document.createElement('canvas'); c.width = 128; c.height = 2048;
  const g = c.getContext('2d');
  const gr = g.createLinearGradient(0, 0, 128, 0); // satin: darker at the edges, a soft sheen just off centre
  gr.addColorStop(0, shadeHex(color, 0.72)); gr.addColorStop(0.3, color); gr.addColorStop(0.5, shadeHex(color, 1.15)); gr.addColorStop(0.75, color); gr.addColorStop(1, shadeHex(color, 0.7));
  g.fillStyle = gr; g.fillRect(0, 0, 128, 2048);
  if (text) {
    g.save(); g.translate(66, 0); g.rotate(Math.PI / 2);
    g.font = "700 76px 'Barlow Condensed', 'Arial Narrow', sans-serif"; g.fillStyle = ink || '#fff'; g.textBaseline = 'middle';
    for (let y = 30; y < 2048; y += 560) g.fillText(text, y, 0);
    g.restore();
  }
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.wrapS = THREE.ClampToEdgeWrapping; t.wrapT = THREE.RepeatWrapping; t.anisotropy = 4;
  RIBBON_TEX[key] = t; return t;
}
const RIBBONS = [];
function ribbon(p0, opts, tex) {
  const N = 56;
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(new Float32Array((N + 1) * 6), 3));
  geo.setAttribute('uv', new THREE.BufferAttribute(new Float32Array((N + 1) * 4), 2));
  const idx = []; for (let i = 0; i < N; i++) { const a = i * 2; idx.push(a, a + 2, a + 1, a + 1, a + 2, a + 3); }
  geo.setIndex(idx);
  const mat = new THREE.MeshPhysicalMaterial({ map: tex, roughness: 0.42, metalness: 0, sheen: 1, sheenRoughness: 0.35, sheenColor: new THREE.Color(0xffffff), side: THREE.DoubleSide });
  const m = new THREE.Mesh(geo, mat);
  m.userData.rib = Object.assign({ p0, N, width: 0.032, len: 0.6, out: 0.06, side: 1, zd: 0.03, tw0: 0, tw: 0.5, ph: 0 }, opts);
  shapeRibbon(m, 0);
  RIBBONS.push(m);
  return m;
}
function shapeRibbon(m, time) {
  const R = m.userData.rib, pos = m.geometry.attributes.position.array, uv = m.geometry.attributes.uv.array;
  const vrep = R.len / (R.width * 16);
  for (let i = 0; i <= R.N; i++) {
    const s = i / R.N;
    const y = R.p0[1] - s * R.len;
    const sway = R.out * s * s + 0.014 * s * Math.sin(s * 5 + time * 1.7 + R.ph);
    const x = R.p0[0] + R.side * sway;
    const z = R.p0[2] + R.zd * s + 0.012 * s * Math.sin(s * 4 + time * 1.3 + R.ph * 1.3);
    const tw = R.tw0 + R.tw * Math.sin(s * 3.1 + R.ph + time * 0.9) * Math.min(1, s * 3);
    const wx = (Math.cos(tw) * R.width) / 2, wz = (Math.sin(tw) * R.width) / 2;
    pos.set([x - wx, y, z - wz, x + wx, y, z + wz], i * 6);
    uv.set([0, 1 - s * vrep, 1, 1 - s * vrep], i * 4);
  }
  m.geometry.attributes.position.needsUpdate = true; m.geometry.attributes.uv.needsUpdate = true;
  m.geometry.computeVertexNormals();
}
function ribbonsFor(kind) {
  const G = new THREE.Group();
  const cols = window.RIBBON_COLORS || ['#d0101e', '#f5a623'];
  if (kind === 'pl') { // the Premier League's red ribbons with the competition's name running down them, three a side
    const tex = ribbonTexture('#c8102e', 'Premier League', '#ffffff');
    for (const s of [-1, 1]) [[0.14, 0.035, 0.64, 0.04, 0.0], [0.175, 0.0, 0.68, 0.07, 1.7], [0.21, -0.03, 0.62, 0.1, 3.1]].forEach(([x, z, len, out, ph]) => {
      G.add(ribbon([s * x, 0.735, z], { side: s, len, out, ph: ph + (s > 0 ? 0.6 : 0), tw0: s * 0.15, tw: 0.4, width: 0.05 }, tex));
    });
  } else if (kind === 'facup' || kind === 'leaguecup' || kind === 'english') { // cup ribbons in both teams' colours
    const top = kind === 'facup' ? [0.21, 0.745] : kind === 'leaguecup' ? [0.3, 0.6] : [0.27, 0.72];
    const len = kind === 'leaguecup' ? 0.52 : 0.64;
    for (const s of [-1, 1]) [[0, 0.025, 0.0], [0.035, -0.02, 2.2]].forEach(([dx, z, ph], j) => {
      const tex = ribbonTexture(cols[(j + (s > 0 ? 1 : 0)) % 2]);
      G.add(ribbon([s * (top[0] + dx), top[1], z], { side: s, len: len - j * 0.04, out: 0.07 + j * 0.03, ph: ph + (s > 0 ? 0.9 : 0), tw0: s * 0.2, tw: 0.6, width: 0.036 }, tex));
    });
  }
  return G;
}

return { BUILD, ribbonsFor, shapeRibbon, materials: { silver, silverSatin, gold, ebony } };
}
