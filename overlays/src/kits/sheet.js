// The kit pictures for the apps' scoreboards (page kits.html, built into kits3d.js): a side's shirt in 3D (kit3d.js, in
// the kit it wears: designs.js) turned through the angles asked for, each a square frame, all the frames on one sheet,
// handed back as a picture. The scoreboard's "3D kits" look draws its frames beside each side's letters; the goal's
// shirt turn has the scorer's name and number on its back.
// window.KITSHEET.render(job) -> Promise<{url, cols, rows, size, n, ms}>: job {team: {espnId, name, kitType, color,
// color2, crest (picture URL)}, size (each frame, px), angles: [radians round the upright: 0 the front, pi the back],
// tilt (towards the viewer, radians), back: {name, number} or null, webp (the sheet as WebP rather than PNG)}.
import { stage } from './kit3d.js';
import { kitFor } from './designs.js';

const load = (src) => new Promise((ok) => {
  if (!src) return ok(null);
  const i = new Image();
  i.onload = () => ok(i);
  i.onerror = () => ok(null);
  i.src = src;
});

let st = null, stSize = 0;

async function render(job) {
  const t0 = performance.now();
  const size = Math.max(32, Math.min(512, job.size | 0 || 128));
  const angles = Array.isArray(job.angles) && job.angles.length ? job.angles.slice(0, 120) : [0];
  // one stage at a time, made again only for another size (the small frames' mesh is coarser: quicker to draw)
  if (!st || stSize !== size) {
    if (st) st.dispose();
    st = stage(size, size, { dist: 2.9, fov: 30, fine: size <= 200 ? 0.045 : 0.034, tex: size <= 200 ? 512 : 1024 });
    stSize = size;
  }
  const crest = await load(job.team && job.team.crest);
  const kit = Object.assign(kitFor(job.team || {}), { crest });
  if (job.back && job.back.number) { kit.name = String(job.back.name || '').toUpperCase(); kit.number = String(job.back.number); }
  if (document.fonts && document.fonts.load) {
    try { await document.fonts.load('800 40px "BarlowC"'); } catch (e) { /* the fallback face then */ }
  }
  const shirt = st.add(kit);
  st.only(shirt);
  const n = angles.length, cols = Math.ceil(Math.sqrt(n)), rows = Math.ceil(n / cols);
  const sheet = document.createElement('canvas');
  sheet.width = cols * size; sheet.height = rows * size;
  const g = sheet.getContext('2d');
  const tilt = job.tilt == null ? 0.08 : +job.tilt;
  angles.forEach((a, i) => {
    shirt.rotation.set(tilt, +a, 0);
    st.render();
    g.drawImage(st.canvas, (i % cols) * size, Math.floor(i / cols) * size);
  });
  st.drop(shirt);
  let url = job.webp ? sheet.toDataURL('image/webp', 0.92) : '';
  if (!url.startsWith('data:image/webp')) url = sheet.toDataURL('image/png');
  return { url, cols, rows, size, n, ms: Math.round(performance.now() - t0) };
}

function dispose() {
  if (st) st.dispose();
  st = null; stSize = 0;
}

window.KITSHEET = { render, dispose };
