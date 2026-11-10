// Compositor layers for the live canvas overlays (kickoff.html, lineups.html, stadium.html), as the strap page's elements
// are: every element is drawn once into a canvas of its own (a sprite: its shadows and glows baked in), and each frame only
// places the sprites, by transform and opacity, so a frame costs the compositor a few textured quads and no drawing at
// all. A frame: LK.begin(); LK.put(sprite, m, alpha, blend) for each thing on the screen, back to front; LK.end() (what
// was not put this frame is hidden). m maps the sprite's own pixels to the 1920x1080 stage, as a canvas transform
// [a, b, c, d, e, f] does; the stage is fitted to the screen here, re-read every frame. What changes as it plays (a
// glint passing, a clock) goes in a small canvas of its own (LK.dyn), drawn again only when it changes.
(function () {
  const W = 1920, H = 1080;
  const root = document.createElement('div');
  // no transform, opacity or z-index on the stage: it is no stacking context, so a layer's blend (the 'lighter' glows) works
  // on what is under it, with no off-screen copy of the whole screen
  root.id = 'stage';
  root.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;overflow:hidden;pointer-events:none;';
  const inner = document.createElement('div');
  inner.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;transform-origin:0 0;';
  root.appendChild(inner);
  // above the stage and outside its wipe and fade: what must stay as the stage is wiped away (the wipe's own light)
  const over = document.createElement('div');
  over.style.cssText = 'position:absolute;left:0;top:0;width:100%;height:100%;overflow:hidden;pointer-events:none;';
  const PLUS = (window.CSS && CSS.supports && CSS.supports('mix-blend-mode', 'plus-lighter')) ? 'plus-lighter' : 'screen';

  const I = [1, 0, 0, 1, 0, 0];
  const mul = (A, B) => [A[0] * B[0] + A[2] * B[1], A[1] * B[0] + A[3] * B[1], A[0] * B[2] + A[2] * B[3], A[1] * B[2] + A[3] * B[3],
    A[0] * B[4] + A[2] * B[5] + A[4], A[1] * B[4] + A[3] * B[5] + A[5]];
  const inv = (A) => { const d = A[0] * A[3] - A[1] * A[2]; return [A[3] / d, -A[1] / d, -A[2] / d, A[0] / d, (A[2] * A[5] - A[3] * A[4]) / d, (A[1] * A[4] - A[0] * A[5]) / d]; };
  const T = (x, y) => [1, 0, 0, 1, x, y];
  const S = (sx, sy) => [sx, 0, 0, sy == null ? sx : sy, 0, 0];
  const R = (a) => [Math.cos(a), Math.sin(a), -Math.sin(a), Math.cos(a), 0, 0];
  const n4 = (v) => Math.round(v * 1e5) / 1e5, n2 = (v) => Math.round(v * 100) / 100;
  const css = (m) => `matrix(${n4(m[0])},${n4(m[1])},${n4(m[2])},${n4(m[3])},${n2(m[4])},${n2(m[5])})`;

  let F = I, fitW = -1, fitH = -1, frame = 0, order = 0, refit = false;
  let used = [], usedNow = [], parked = [];
  const PARK = 45;   // frames hidden before a sprite leaves the compositor altogether (display none)
  let group = null;                   // the clip group being filled
  const groups = {};

  function fit() {
    const de = document.documentElement;
    const vw = de.clientWidth || window.innerWidth || W, vh = de.clientHeight || window.innerHeight || H;
    if (vw === fitW && vh === fitH) return;
    fitW = vw; fitH = vh;
    const k = Math.min(vw / W, vh / H);
    F = [k, 0, 0, k, (vw - W * k) / 2, (vh - H * k) / 2];
    refit = true;
  }
  function rec(el) {
    if (!el._lk) {
      el._lk = { m: '', o: -1, z: -1, b: '', f: -1, p: null, h: 0, d: false };
      const s = el.style;
      s.position = 'absolute'; s.left = '0'; s.top = '0'; s.transformOrigin = '0 0'; s.willChange = 'transform, opacity';
      s.opacity = '0'; s.pointerEvents = 'none';
      if (el.tagName === 'CANVAS') { s.width = el.width + 'px'; s.height = el.height + 'px'; }
    }
    return el._lk;
  }
  // a sprite on the screen this frame: m (sprite pixels to stage), alpha 0..1, blend '' or 'lighter' (added light);
  // top: above everything, outside the wipe and the fade
  function put(el, m, alpha, blend, top) {
    const a = alpha == null ? 1 : Math.max(0, Math.min(1, alpha));
    if (!el || a <= 0.002 || (group && group.off && !top)) return;
    const r = rec(el);
    const g0 = top ? null : group;
    const parent = top ? over : g0 ? g0.el : inner;
    if (r.p !== parent) { parent.appendChild(el); r.p = parent; r.m = ''; r.z = -1; }
    if (r.f === frame) {   // the same sprite twice in one frame: a copy for the second
      const c = el._copy || (el._copy = cloneCanvas(el));
      return put(c, m, a, blend, top);
    }
    r.f = frame;
    if (r.d) { el.style.display = ''; r.d = false; }
    const M = g0 ? mul(g0.inv, m) : mul(F, m);
    const t = css(M);
    if (t !== r.m || refit) { el.style.transform = t; r.m = t; }
    const o = Math.round(a * 1000) / 1000;
    if (o !== r.o) { el.style.opacity = String(o); r.o = o; }
    // added light as a blend of the compositor's own, unless the page turned it off (LK_PLUS false): each such layer is an
    // offscreen surface of its own, and the Streamer's PowerVR took a second a frame over the pop-up's dozen of them
    const b = blend === 'lighter' ? (window.LK_PLUS === false ? '' : PLUS) : blend || '';
    if (b !== r.b) { el.style.mixBlendMode = b || 'normal'; r.b = b; }
    const z = top ? 100000 + order++ : g0 ? g0.n++ : order++;
    if (z !== r.z) { el.style.zIndex = String(z); r.z = z; }
    usedNow.push(el);
  }
  function cloneCanvas(c) { const d = document.createElement('canvas'); d.width = c.width; d.height = c.height; d.getContext('2d').drawImage(c, 0, 0); return d; }
  // a clip: what is put until endClip() shows only inside the rectangle (0, 0, w, h) under m (an ellipse with round).
  // Kept compositor-only: the clip box has a fixed size and is scaled to the rectangle, its contents scaled back.
  function clip(key, m, w, h, round) {
    const G = groups[key] || (groups[key] = { el: document.createElement('div'), z: -1, m: '', f: -1, n: 0, shown: false });
    if (!G.made) {
      G.made = true;
      G.el.style.cssText = 'position:absolute;left:0;top:0;width:1000px;height:1000px;overflow:hidden;transform-origin:0 0;pointer-events:none;display:none;' + (round ? 'border-radius:50%;' : '');
      inner.appendChild(G.el);
    }
    if (!(w > 0.5 && h > 0.5)) { group = { off: true }; return; }
    const B = mul(m, S(w / 1000, h / 1000));   // the box's own pixels to the stage
    const t = css(mul(F, B));
    if (t !== G.m || refit) { G.el.style.transform = t; G.m = t; }
    if (!G.shown) { G.el.style.display = ''; G.shown = true; }
    const z = order++;
    if (z !== G.z) { G.el.style.zIndex = String(z); G.z = z; }
    G.f = frame; G.n = 0;
    group = { el: G.el, inv: inv(B), n: 0 };
  }
  function endClip() { group = null; }
  function begin() { frame++; order = 0; usedNow = []; refit = false; fit(); }
  function end() {
    group = null;
    for (const el of used) if (el._lk.f !== frame && el._lk.o !== 0) { el.style.opacity = '0'; el._lk.o = 0; el._lk.h = frame; parked.push(el); }
    // a sprite hidden a while is taken out of the compositor's way (shown again, it is put back at once)
    if (parked.length) {
      parked = parked.filter((el) => {
        const r = el._lk;
        if (r.f >= r.h) return false;            // shown again since
        if (frame - r.h < PARK) return true;
        el.style.display = 'none'; r.d = true;
        return false;
      });
    }
    for (const k in groups) { const G = groups[k]; if (G.f !== frame && G.shown) { G.el.style.display = 'none'; G.shown = false; } }
    used = usedNow;
  }
  // a small canvas of its own, for what changes as it plays: made once at this size
  const dyns = {};
  function dyn(key, w, h) {
    let c = dyns[key];
    if (!c) { c = dyns[key] = document.createElement('canvas'); c.width = Math.max(1, Math.ceil(w)); c.height = Math.max(1, Math.ceil(h)); c._g = c.getContext('2d'); }
    return c;
  }
  // a canvas of one colour (stretched over any rectangle)
  const fills = {};
  function fill(col) {
    if (fills[col]) return fills[col];
    const c = document.createElement('canvas'); c.width = c.height = 4;
    const g = c.getContext('2d'); g.fillStyle = col; g.fillRect(0, 0, 4, 4);
    return (fills[col] = c);
  }
  // the matrix that stretches a w x h sprite over the rectangle (x, y, rw, rh); the 4 px fill's edge texels are skipped
  const rect = (x, y, rw, rh, w, h) => [rw / w, 0, 0, rh / h, x, y];
  // the whole stage faded (1: no change). An off-screen copy only while it is below 1.
  let fadeA = 1;
  function fade(a) {
    a = Math.max(0, Math.min(1, a));
    if (Math.abs(a - fadeA) < 0.001) return;
    fadeA = a;
    inner.style.opacity = a >= 0.999 ? '' : String(Math.round(a * 1000) / 1000);
  }
  // the brand wipe: only right of a slanted edge stays (the edge through (x + dx, 0) and (x - dx, H)); null: no wipe.
  // A skewed clip of the stage and its contents skewed back, so nothing is drawn again.
  let wiping = false;
  function wipe(x, dx) {
    if (x == null) {
      if (wiping) { root.style.transform = ''; inner.style.transform = ''; wiping = false; }
      return;
    }
    wiping = true;
    // the stage's own box (in stage pixels from its left edge) skewed so that its left edge is the slanted line:
    // stage x = u + x + sk * (v - H/2), y = v; the stage box is the screen's, scaled to stage pixels
    const sk = -2 * dx / H, k = F[0];
    const Bm = [1, 0, sk, 1, x - sk * H / 2, 0];
    const Rm = mul(F, mul(Bm, S(1 / k, 1 / k)));
    root.style.transformOrigin = '0 0';
    root.style.transform = css(Rm);
    inner.style.transform = css(inv(Rm));
  }
  function hideAll() {
    for (const el of used) { el.style.opacity = '0'; el._lk.o = 0; el._lk.h = frame; parked.push(el); }
    used = [];
    for (const k in groups) { const G = groups[k]; if (G.shown) { G.el.style.display = 'none'; G.shown = false; } }
  }
  function clear() {
    hideAll(); wipe(null); fade(1);
    for (const el of [inner, over]) while (el.firstChild) el.removeChild(el.firstChild);
    parked = [];
    for (const k in groups) delete groups[k];
  }
  function mount() { if (!root.parentNode) { document.body.appendChild(root); document.body.appendChild(over); } fit(); }
  window.LK = { W, H, put, clip, endClip, begin, end, dyn, fill, rect, fade, wipe, hideAll, clear, mount, mul, inv, T, S, R, I, fitNow: () => { fitW = -1; fit(); }, root, inner, PLUS };
})();
