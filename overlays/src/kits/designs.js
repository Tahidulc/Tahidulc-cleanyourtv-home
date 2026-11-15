// The kits the 3D shirts wear (kit3d.js paintKit's settings). The Premier League's twenty clubs have their 2026/27 home
// shirts as the league's own player pictures show them: colours, stripes, hoops, a sash, sleeves, collar and cuffs; no
// maker's marks and no sponsor. A few other clubs known for a pattern (stripes, hoops) have it in their colours. Any
// other kit, or a club's away or third kit, is made from the colours ESPN gives for the kit worn that day: its body, a
// trim for the collar, the cuffs and the number.
// kitFor({espnId, name, kitType ('home', 'away', 'third', ... or ''), color ('#rrggbb', the kit worn), color2 (its trim)}).

// symmetric vertical stripes w wide with gaps g, a stripe in the middle (centre) or a gap
function sym(w, g, centre) {
  const out = [];
  if (centre) {
    out.push([-w / 2, w / 2]);
    for (let x = w / 2 + g; x < 0.7; x += w + g) out.push([x, x + w], [-x - w, -x]);
  } else {
    for (let x = g / 2; x < 0.7; x += w + g) out.push([x, x + w], [-x - w, -x]);
  }
  return out;
}
// hoops w deep, one every pitch, over the whole shirt
function hoops(w, pitch, from = -0.7) {
  const out = [];
  for (let y = from; y < 0.7; y += pitch) out.push([y, y + w]);
  return out;
}

const WHITE = '#f6f7fa';

/** By ESPN's id: the Premier League's clubs, 2026/27 home. looks: the colours a kit of theirs is known by. */
export const PL = {
  // Arsenal: red with white raglan sleeves, a dark red collar, dark red piping along the raglan seam and cuffs
  359: { body: '#dc2332', sleeves: '#fafcff', raglan: true, trim: '#931b2b', piping: '#931b2b', collar: '#8f1726', cuff: '#931b2b',
    cuffEdge: '#931b2b', numberColour: '#ffffff', looks: ['#e20520', '#dc2332'] },
  // Aston Villa: claret, a sky blue collar and cuffs
  362: { body: '#6b1a36', trim: '#9fcfee', piping: null, collar: '#9fcfee', cuff: '#9fcfee', cuffEdge: '#6b1a36', numberColour: '#ffffff',
    looks: ['#660e36', '#6b1a36'] },
  // Bournemouth: red and black stripes, black collar and cuffs
  349: { body: '#d6202a', stripes: sym(0.11, 0.11, true), stripeColour: '#151515', trim: '#151515', piping: null, collar: '#151515',
    cuff: '#151515', cuffEdge: '#151515', numberColour: '#ffffff', looks: ['#f42727', '#d6202a', '#151515'] },
  // Brentford: red and white stripes, a black collar and cuffs edged in yellow
  337: { body: WHITE, stripes: sym(0.1, 0.1, false), stripeColour: '#d8232a', trim: '#151515', piping: null, collar: '#151515',
    collarEdge: '#f2c400', cuff: '#151515', cuffEdge: '#f2c400', numberColour: '#151515', numberEdge: '#ffffff', looks: ['#f42727', '#d8232a'] },
  // Brighton: blue with fine white pinstripes, a white collar
  331: { body: '#0f5fd3', stripes: sym(0.007, 0.056, false), stripeColour: '#e3edff', trim: '#f4f6fb', piping: null, collar: '#f4f6fb',
    cuff: '#0f5fd3', cuffEdge: '#f4f6fb', numberColour: '#ffffff', looks: ['#0606fa', '#0f5fd3'] },
  // Chelsea: royal blue, a blue collar and cuffs with a gold edge
  363: { body: '#1846c2', trim: '#e5c157', piping: null, collar: '#1846c2', collarEdge: '#e5c157', cuff: '#1846c2', cuffEdge: '#e5c157',
    numberColour: '#ffffff', looks: ['#144992', '#1846c2'] },
  // Coventry City: sky blue and white stripes, a navy collar
  388: { body: WHITE, stripes: sym(0.085, 0.05, true), stripeColour: '#5aaee5', trim: '#14294f', piping: null, collar: '#14294f',
    cuff: '#14294f', cuffEdge: '#5aaee5', numberColour: '#14294f', numberEdge: '#ffffff', looks: ['#87cced', '#5aaee5'] },
  // Crystal Palace: white with a red and blue sash, a blue collar
  384: { body: WHITE, sash: { from: [0.36, 0.52], to: [-0.34, -0.66], bands: [[-0.118, -0.082, '#1d3c96'], [-0.062, 0.062, '#dc2b34'],
    [0.082, 0.118, '#1d3c96']] }, trim: '#1d3c96', piping: '#dc2b34', collar: '#1d3c96', cuff: '#1d3c96', cuffEdge: '#dc2b34',
    numberColour: '#1d3c96', looks: ['#0202fb', '#1d3c96', '#dc2b34', '#ffffff'] },
  // Everton: royal blue, a gold collar
  368: { body: '#2154b5', trim: '#f0b22c', piping: null, collar: '#f0b22c', cuff: '#2154b5', cuffEdge: '#f0b22c', numberColour: '#ffffff',
    looks: ['#0606fa', '#2154b5'] },
  // Fulham: white, a black collar and cuffs
  370: { body: WHITE, trim: '#141414', piping: null, collar: '#141414', cuff: '#141414', cuffEdge: '#141414', numberColour: '#141414',
    looks: ['#ffffff'] },
  // Hull City: amber and black stripes on the body, amber sleeves, a white collar, black cuffs
  306: { body: '#f3a21c', stripes: [[-0.085, 0.085], [0.2, 0.31], [-0.31, -0.2]], stripeColour: '#151515', sleeves: '#f3a21c',
    trim: '#151515', piping: null, collar: '#f6f6f6', cuff: '#151515', cuffEdge: '#151515', numberColour: '#ffffff', numberEdge: '#151515',
    looks: ['#f28800', '#f3a21c'] },
  // Ipswich Town: blue, the collar and cuffs edged in white
  373: { body: '#1f4fbf', trim: '#ffffff', piping: null, collar: '#1f4fbf', collarEdge: '#ffffff', cuff: '#1f4fbf', cuffEdge: '#ffffff',
    numberColour: '#ffffff', looks: ['#0000fa', '#1f4fbf'] },
  // Leeds United: white with fine blue hoops, a yellow collar edged in blue, blue cuffs
  357: { body: WHITE, hoops: hoops(0.006, 0.07), hoopColour: '#2a56b0', trim: '#1e4aa8', piping: '#f5c518', collar: '#f5c518',
    collarEdge: '#1e4aa8', cuff: '#1e4aa8', cuffEdge: '#f5c518', numberColour: '#1e4aa8', looks: ['#ffffff'] },
  // Liverpool: red, the collar and cuffs edged in white
  364: { body: '#c8102e', trim: '#ffffff', piping: null, collar: '#c8102e', collarEdge: '#ffffff', cuff: '#c8102e', cuffEdge: '#ffffff',
    numberColour: '#ffffff', looks: ['#d11317', '#c8102e'] },
  // Manchester City: sky blue, the collar and cuffs edged in white, navy numbers
  382: { body: '#9ccbee', trim: '#ffffff', piping: null, collar: '#9ccbee', collarEdge: '#ffffff', cuff: '#9ccbee', cuffEdge: '#ffffff',
    numberColour: '#1c2c5b', looks: ['#99c5ea', '#9ccbee'] },
  // Manchester United: red, a red collar and cuffs edged in white
  360: { body: '#d8211d', trim: '#ffffff', piping: null, collar: '#d8211d', collarEdge: '#ffffff', cuff: '#d8211d', cuffEdge: '#ffffff',
    numberColour: '#ffffff', looks: ['#da020e', '#d8211d'] },
  // Newcastle United: white with black stripes of uneven width (a wide one down the middle), a black collar edged in sky
  // blue, sky blue piping on the shoulders, black cuffs
  361: { body: '#f6f9fc', stripeColour: '#1c1c20',
    stripes: [[-0.66, -0.57], [-0.335, -0.28], [-0.248, -0.238], [-0.215, -0.15], [-0.122, -0.112], [-0.09, -0.08],
      [-0.055, 0.055], [0.08, 0.09], [0.112, 0.122], [0.15, 0.215], [0.238, 0.248], [0.28, 0.335], [0.57, 0.66]],
    sleeveStripes: [[-0.09, -0.07], [-0.04, 0.0], [0.03, 0.04], [0.065, 0.11]],
    trim: '#74bceb', piping: '#74bceb', collar: '#1c1c20', collarEdge: '#74bceb', cuff: '#1c1c20', cuffEdge: '#74bceb',
    numberColour: '#1c1c20', numberEdge: '#ffffff', raglan: false, looks: ['#000000', '#f6f9fc', '#1c1c20'] },
  // Nottingham Forest: red, a white collar, the cuffs edged in white
  393: { body: '#cf1f2e', trim: '#f6f6f6', piping: null, collar: '#f6f6f6', cuff: '#cf1f2e', cuffEdge: '#f6f6f6', numberColour: '#ffffff',
    looks: ['#c8102e', '#cf1f2e'] },
  // Tottenham Hotspur: white, a navy collar, the cuffs edged in navy
  367: { body: WHITE, trim: '#132257', piping: null, collar: '#132257', cuff: WHITE, cuffEdge: '#132257', numberColour: '#132257',
    looks: ['#ffffff'] },
  // Sunderland: red and white stripes, a black collar edged in white, black cuffs
  366: { body: WHITE, stripes: sym(0.06, 0.05, true), stripeColour: '#d6202c', trim: '#141414', piping: null, collar: '#141414',
    collarEdge: '#f7f7f7', cuff: '#141414', cuffEdge: '#f7f7f7', numberColour: '#141414', numberEdge: '#ffffff', looks: ['#eb172b', '#d6202c'] },
};

/** Clubs elsewhere known for a pattern, by name: their usual colours (not this season's own design). */
const PATTERNS = [
  [/barcelona/, { body: '#a50044', stripes: sym(0.11, 0.11, true), stripeColour: '#1b3f8f', trim: '#1b3f8f', collar: '#1b3f8f', looks: ['#a50044', '#1b3f8f'] }],
  [/inter(nazionale)?\b|inter milan/, { body: '#1e5bd8', stripes: sym(0.09, 0.09, true), stripeColour: '#111111', trim: '#111111', collar: '#111111', looks: ['#1e5bd8', '#111111', '#0068a8'] }],
  [/juventus/, { body: WHITE, stripes: sym(0.1, 0.1, true), stripeColour: '#111111', trim: '#111111', collar: '#111111', numberColour: '#111111', numberEdge: '#ffffff', looks: ['#ffffff', '#111111', '#000000'] }],
  [/\bmilan\b(?!.*inter)/, { body: '#d6202a', stripes: sym(0.1, 0.1, true), stripeColour: '#111111', trim: '#111111', collar: '#111111', looks: ['#d6202a', '#fb090b', '#111111'] }],
  [/atl[eé]tico/, { body: WHITE, stripes: sym(0.1, 0.1, true), stripeColour: '#cb3524', trim: '#1c2c5b', collar: '#1c2c5b', numberColour: '#1c2c5b', numberEdge: '#ffffff', looks: ['#cb3524', '#ffffff'] }],
  [/athletic (club|bilbao)/, { body: WHITE, stripes: sym(0.1, 0.1, true), stripeColour: '#e30613', trim: '#111111', collar: '#111111', numberColour: '#111111', numberEdge: '#ffffff', looks: ['#e30613', '#ffffff'] }],
  [/celtic/, { body: WHITE, hoops: hoops(0.11, 0.22, -0.66), hoopColour: '#018749', trim: '#018749', collar: '#018749', numberColour: '#018749', looks: ['#018749', '#ffffff', '#00843d'] }],
  [/sporting (cp|lisbon|clube)/, { body: WHITE, hoops: hoops(0.11, 0.22, -0.66), hoopColour: '#00805a', trim: '#00805a', collar: '#00805a', numberColour: '#00805a', looks: ['#00805a', '#ffffff'] }],
  [/\bporto\b/, { body: WHITE, stripes: sym(0.1, 0.1, true), stripeColour: '#1c4ea8', trim: '#1c4ea8', collar: '#1c4ea8', numberColour: '#1c4ea8', numberEdge: '#ffffff', looks: ['#1c4ea8', '#ffffff'] }],
  [/\bpsv\b/, { body: WHITE, stripes: sym(0.1, 0.1, false), stripeColour: '#e21b23', trim: '#111111', collar: '#111111', numberColour: '#111111', numberEdge: '#ffffff', looks: ['#e21b23', '#ffffff'] }],
  [/\bajax\b/, { body: WHITE, stripes: [[-0.16, 0.16]], stripeColour: '#d2122e', sleeves: WHITE, trim: '#d2122e', collar: '#d2122e', numberColour: '#111111', looks: ['#d2122e', '#ffffff'] }],
];

const rgb = (h) => { const s = String(h || '').replace('#', ''); if (!/^[0-9a-f]{6}$/i.test(s)) return null; return [0, 2, 4].map((i) => parseInt(s.slice(i, i + 2), 16)); };
const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
const lum = (c) => (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / 255;
const hex = (c) => '#' + c.map((v) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('');

/** The design for a club (ESPN's id, else its name), or null. */
export function designOf(espnId, name) {
  const id = parseInt(espnId, 10);
  if (PL[id]) return PL[id];
  const n = String(name || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  for (const [re, d] of PATTERNS) if (re.test(n)) return d;
  return null;
}

/** A plain kit in the colours given: the body, and a trim for the collar, the cuffs' edge and (when it reads) the number. */
export function plain(body, trim) {
  const b = rgb(body) || [200, 16, 46];
  const light = lum(b) > 0.62;
  let t = rgb(trim);
  if (!t || dist(t, b) < 70) t = light ? [26, 36, 102] : [244, 245, 250];
  const tr = hex(t), bh = hex(b);
  // the number in the trim when it stands out from the body, else white on dark, navy on light
  const num = dist(t, b) > 150 ? tr : light ? '#1a2466' : '#ffffff';
  return { body: bh, trim: tr, piping: null, collar: tr, cuff: bh, cuffEdge: tr, numberColour: num };
}

/**
 * The kit a side wears, for the 3D shirt: its club's own design when it wears its home kit (or no kit is named and the
 * colours given are the design's own), else a plain one in the colours given.
 */
export function kitFor(team) {
  const t = team || {};
  const d = designOf(t.espnId, t.name);
  const type = String(t.kitType || '').toLowerCase();
  const worn = rgb(t.color);
  if (d) {
    const own = !worn || (d.looks || [d.body]).some((c) => { const r = rgb(c); return r && dist(r, worn) < 120; });
    if (type === 'home' || (!type && own)) {
      const k = Object.assign({}, d);
      delete k.looks;
      if (!k.numberColour) k.numberColour = lum(rgb(k.body)) > 0.62 ? '#1a2466' : '#ffffff';
      return k;
    }
  }
  return plain(t.color || (d && d.body) || '#c8102e', t.color2 || (d && d.trim));
}
