// The end card: the Whitebrook Road Sports wordmark as the line-ups and the pop-up draw it (the house tile, "whitebrook
// road" in Outfit 800, "sports" on the red tab), MATCHDAY spaced out beneath in the same family, the two teams small under
// that. Each piece drawn once into a canvas; the frames only place them (screen layer, 1920 x 1080, y up).
import * as THREE from '../vendor/three.module.js';
import { shader, clamp, eo, eio, ss, lerp } from './kit.js';

const VIOLET = '#6D28F2', CORAL = '#FF6B6B', RED = '#E10A1E', RED_DEEP = '#B0061A';
const HOUSE = 'M31 75 L31 50 L54 31 L77 50 L77 75 Z', PLAY = 'M49 53 L49 69 L63 61 Z';

function mk(w, h) { const c = document.createElement('canvas'); c.width = Math.ceil(w); c.height = Math.ceil(h); return c; }
function font(g, w, s, ls) { g.font = `${w} ${s}px Outfit`; g.letterSpacing = `${ls || 0}px`; }

// the house tile, as head.js tile() draws it
function tile(g, x, y, s) {
  g.save();
  const gr = g.createLinearGradient(x, y, x + s, y + s); gr.addColorStop(0, VIOLET); gr.addColorStop(1, CORAL);
  g.beginPath(); g.roundRect(x, y, s, s, s * 0.24);
  g.shadowColor = 'rgba(0,0,0,0.45)'; g.shadowBlur = s * 0.35; g.shadowOffsetY = s * 0.08;
  g.fillStyle = gr; g.fill();
  g.shadowColor = 'transparent'; g.shadowBlur = 0; g.shadowOffsetY = 0;
  const rg = g.createRadialGradient(x + 0.28 * s, y + 0.18 * s, 0, x + 0.28 * s, y + 0.18 * s, 0.6 * s);
  rg.addColorStop(0, 'rgba(255,255,255,0.2)'); rg.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = rg; g.fill();
  const wd = s * 0.62, k = wd / 51, mx = x + (s - wd) / 2, my = y + (s - 49 * k) / 2;
  g.translate(mx - 28.5 * k, my - 28.5 * k); g.scale(k, k);
  g.lineJoin = 'round';
  const H = new Path2D(HOUSE), P = new Path2D(PLAY);
  g.fillStyle = '#fff'; g.strokeStyle = '#fff'; g.lineWidth = 5; g.fill(H); g.stroke(H);
  const gx0 = (x - mx) / k + 28.5, gy0 = (y - my) / k + 28.5;
  const cg = g.createLinearGradient(gx0, gy0, gx0 + s / k, gy0 + s / k); cg.addColorStop(0, VIOLET); cg.addColorStop(1, CORAL);
  g.fillStyle = cg; g.strokeStyle = cg; g.lineWidth = 2.6; g.fill(P); g.stroke(P);
  g.restore();
}

// the wordmark at text size S: [tile] whitebrook road [sports]
function wordmarkCanvas(S) {
  const probe = mk(10, 10).getContext('2d');
  font(probe, 800, S, 0); const w1 = probe.measureText('whitebrook road').width;
  font(probe, 800, S * 0.9, 0); const w2 = probe.measureText('sports').width;
  const ts = S * 1.18, gap = S * 0.3, pillW = w2 + S * 0.52, pillH = S * 1.14;
  const pad = S * 0.6, W = ts + gap + w1 + S * 0.3 + pillW + pad * 2, H = Math.max(ts, pillH) + pad * 2;
  const c = mk(W, H), g = c.getContext('2d');
  const cy = H / 2;
  tile(g, pad, cy - ts / 2, ts);
  const x = pad + ts + gap, base = cy + S * 0.33;
  g.shadowColor = 'rgba(0,0,0,0.5)'; g.shadowBlur = S * 0.4; g.shadowOffsetY = S * 0.06;
  font(g, 800, S, 0); g.textAlign = 'left'; g.textBaseline = 'alphabetic'; g.fillStyle = '#fff';
  g.fillText('whitebrook road', x, base);
  const tx = x + w1 + S * 0.3;
  const rg = g.createLinearGradient(0, cy - pillH / 2, 0, cy + pillH / 2); rg.addColorStop(0, RED); rg.addColorStop(1, RED_DEEP);
  g.beginPath(); g.roundRect(tx, cy - pillH / 2, pillW, pillH, S * 0.14); g.fillStyle = rg; g.fill();
  g.shadowColor = 'transparent'; g.shadowBlur = 0; g.shadowOffsetY = 0;
  font(g, 800, S * 0.9, 0); g.fillStyle = '#fff'; g.fillText('sports', tx + S * 0.26, base - S * 0.03);
  return { c, W, H };
}

function textCanvas(s, w, size, ls, col, pad = 24) {
  const probe = mk(10, 10).getContext('2d'); font(probe, w, size, ls);
  const tw = probe.measureText(s).width;
  const c = mk(tw + pad * 2, size * 1.6 + pad), g = c.getContext('2d');
  font(g, w, size, ls); g.textAlign = 'left'; g.textBaseline = 'middle'; g.fillStyle = col;
  g.shadowColor = 'rgba(0,0,0,0.5)'; g.shadowBlur = size * 0.35; g.shadowOffsetY = size * 0.05;
  g.fillText(s, pad, c.height / 2 + size * 0.04);
  return { c, W: c.width, H: c.height, tw };
}

function quad(canvas, order, sweep) {
  const tex = new THREE.CanvasTexture(canvas); tex.colorSpace = THREE.SRGBColorSpace; tex.anisotropy = 4;
  tex.minFilter = THREE.LinearMipmapLinearFilter; tex.generateMipmaps = true;
  const mat = shader({
    transparent: true, depthTest: false, depthWrite: false, toneMapped: false, side: THREE.DoubleSide,
    uniforms: { map: { value: tex }, uA: { value: 0 }, uSweep: { value: -1 } },
    vertexShader: `varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
    fragmentShader: `uniform sampler2D map; uniform float uA, uSweep; varying vec2 vUv;
      void main() {
        vec4 c = texture2D(map, vUv);
        ${sweep ? 'float b = exp(-pow((vUv.x - uSweep + (vUv.y - 0.5) * 0.25) * 9.0, 2.0)); c.rgb = mix(c.rgb, vec3(1.0), b * 0.75 * step(0.5, c.a) * 0.9);' : ''}
        gl_FragColor = vec4(c.rgb, c.a * uA);
        #include <colorspace_fragment>
      }`,
  });
  const m = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), mat);
  m.renderOrder = order; m.visible = false;
  return m;
}

export function makeCard(cfg) {
  const group = new THREE.Group();
  const WM = wordmarkCanvas(118);
  const word = quad(WM.c, 40, true); word.userData.size = [WM.W, WM.H]; group.add(word);
  // MATCHDAY, a letter each (they close up into place)
  const letters = [];
  const LS = 74, SP = 0.5 * LS;
  const probe = mk(10, 10).getContext('2d'); font(probe, 700, LS, 0);
  const advs = 'MATCHDAY'.split('').map((ch) => probe.measureText(ch).width);
  const total = advs.reduce((a, b) => a + b, 0) + SP * 7;
  let x = -total / 2;
  'MATCHDAY'.split('').forEach((ch, i) => {
    const T = textCanvas(ch, 700, LS, 0, '#ffffff', 18);
    const q = quad(T.c, 41, false); q.userData = { size: [T.W, T.H], cx: x + advs[i] / 2, i };
    group.add(q); letters.push(q);
    x += advs[i] + SP;
  });
  // the rules either side
  const ruleC = mk(512, 8), rg = ruleC.getContext('2d');
  const lg = rg.createLinearGradient(0, 0, 512, 0); lg.addColorStop(0, 'rgba(255,255,255,0)'); lg.addColorStop(1, 'rgba(255,255,255,0.85)');
  rg.fillStyle = lg; rg.fillRect(0, 3, 512, 2);
  const ruleL = quad(ruleC, 41, false), ruleR = quad(ruleC, 41, false);
  group.add(ruleL); group.add(ruleR);
  // the teams
  const home = String((cfg.home && cfg.home.name) || '').toUpperCase(), away = String((cfg.away && cfg.away.name) || '').toUpperCase();
  let teams = null;
  if (home && away) {
    const T = textCanvas(home + '   v   ' + away, 600, 40, 5, 'rgba(214,220,240,0.92)');
    teams = quad(T.c, 41, false); teams.userData.size = [T.W, T.H]; group.add(teams);
  }
  // the glow behind, in the tile's two colours
  const glowC = mk(512, 256), gg = glowC.getContext('2d');
  for (const [cx, col] of [[190, '109,40,242'], [330, '255,107,107']]) {
    const r = gg.createRadialGradient(cx, 128, 0, cx, 128, 200); r.addColorStop(0, `rgba(${col},0.55)`); r.addColorStop(1, `rgba(${col},0)`);
    gg.fillStyle = r; gg.fillRect(0, 0, 512, 256);
  }
  const glow = quad(glowC, 39, false); group.add(glow);

  const Y = { word: 1080 - 462, md: 1080 - 606, teams: 1080 - 700 };
  // lt: seconds since the card began; fade: the whole card's own fade at the end (1 shown)
  function frame(lt) {
    group.visible = lt >= 0;
    if (lt < 0) return;
    const grow = 1 + 0.025 * eio(clamp((lt - 1.5) / 5));
    // the wordmark: up out of the flash, scaling down into place, a light passing over it
    const a = eo(clamp(lt / 0.45)), sc = lerp(1.12, 1, eo(clamp(lt / 0.7))) * grow;
    const [ww, wh] = word.userData.size, kw = 1.0;
    word.visible = true; word.material.uniforms.uA.value = a;
    word.scale.set(ww * kw * sc, wh * kw * sc, 1); word.position.set(960, Y.word, 0);
    word.material.uniforms.uSweep.value = lerp(-0.3, 1.3, clamp((lt - 0.35) / 0.9));
    // MATCHDAY: the letters close up from wide apart, one after another, on the beat
    const l0 = 0.625;
    letters.forEach((q) => {
      const { size, cx, i } = q.userData;
      const u = eo(clamp((lt - l0 - i * 0.045) / 0.75));
      q.visible = u > 0;
      q.material.uniforms.uA.value = u;
      q.scale.set(size[0] * grow, size[1] * grow, 1);
      q.position.set(960 + cx * lerp(2.1, 1, u) * grow, Y.md, 0);
    });
    const ru = eio(clamp((lt - 1.0) / 0.8));
    const half = 380 * grow;
    for (const [m, s] of [[ruleL, -1], [ruleR, 1]]) {
      m.visible = ru > 0; m.material.uniforms.uA.value = ru * 0.8;
      const len = 300 * ru;
      m.scale.set(len * s * -1, 8, 1); m.position.set(960 + s * (half + len / 2), Y.md + 2, 0);
    }
    if (teams) {
      const tu = eo(clamp((lt - 1.25) / 0.6));
      teams.visible = tu > 0; teams.material.uniforms.uA.value = tu;
      teams.scale.set(teams.userData.size[0] * grow, teams.userData.size[1] * grow, 1);
      teams.position.set(960, Y.teams - 14 * (1 - tu), 0);
    }
    glow.visible = true; glow.material.uniforms.uA.value = 0.55 * eo(clamp(lt / 1.2));
    glow.scale.set(1500 * grow, 760 * grow, 1); glow.position.set(960, 1080 - 540, 0);
  }
  return { group, frame };
}
