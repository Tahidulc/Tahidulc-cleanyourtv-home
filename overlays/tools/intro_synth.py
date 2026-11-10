#!/usr/bin/env python3
"""The matchday intro's music, made from numpy maths only (no samples, no one else's music), on the intro's own tempo map.

    python3 -I intro_synth.py <outdir>
    ffmpeg -i <outdir>/intro-music.wav -c:a libvorbis -q:a 4 ../src/sfx/intro-music.ogg

Epic hybrid-orchestral in D minor at 96 BPM, 51.5 s, 48 kHz stereo, timed to src/intro/tempo.json (the page reads the same
file, so every cut, flash and hit lands on these beats):

  0-10    the sky: a dark string pad, a sub drone; a celesta note for each star of the constellation as it lights, a glitter
          run as the ball's stars appear, a bloom, a riser and a tom pickup into
  10-20   the ground: taiko groove, low-string ostinato in eighths, the hook on low brass (Dm Bb F C); a shimmer as the
          floodlights flare (12.2)
  20-30   the strike, in slow motion: half-time drums, the hook on high strings; a hit on the strike (22.5), the ball's
          whooshes, the net (28.9)
  30-40   the build: drums in eighths, ostinato in sixteenths, the hook again rising over Gm and A, a snare roll and a riser to
          the ball becoming a star (39.375), a hush
  40-45   the climax: a deep brass "braam" and a hit as the star bursts, the full hook (brass, strings, choir), a ring as the
          trophy forms (41.25) and is whole (42.5)
  45-51.5 the end card: a D major sting (the minor key turned major), its tail ringing out under the fade
"""
import json
import os
import sys

import numpy as np
from scipy import signal
from scipy.io import wavfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from goal_synth import SR, db, norm, tt, sos, pan_gains, shaped_noise, whoosh, sub_drop, crack, cymbal, sub_boom, ping, snare  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
TEMPO = json.load(open(os.path.join(HERE, '..', 'src', 'intro', 'tempo.json')))
BEAT, BAR, LEN = TEMPO['beat'], TEMPO['bar'], TEMPO['len']
N = int(SR * (LEN + 0.5))
RNG = np.random.default_rng(96)


def mf(m):
    return 440.0 * 2 ** ((m - 69) / 12)


CHORDS = {   # voicings (MIDI): the pad's notes, and the bass
    'Dm': ([50, 53, 57, 62, 65], 38), 'Bb': ([46, 50, 53, 58, 62], 34), 'F': ([48, 53, 57, 60, 65], 41),
    'C': ([48, 52, 55, 60, 64], 36), 'Gm': ([43, 50, 55, 58, 62], 43), 'A': ([45, 52, 57, 61, 64], 33),
    'D': ([50, 54, 57, 62, 66], 38),
}
# each bar's chord, from the tempo map's sections
BARS = []
for sec in TEMPO['sections']:
    n = int(round((sec['t1'] - sec['t0']) / BAR))
    for i in range(max(1, n)):
        BARS.append((sec['t0'] + i * BAR, sec['chords'][min(i, len(sec['chords']) - 1)], sec['name']))


def chord_at(t):
    c = BARS[0][1]
    for t0, ch, _ in BARS:
        if t0 <= t + 1e-6:
            c = ch
    return c


class Mix:
    """Two buses: the music (ducked in the hush before the climax) and what plays through it; one hall reverb."""
    def __init__(self):
        self.bus = {k: np.zeros((2, N)) for k in ('main', 'free')}
        self.send = np.zeros((2, N))
        self.stems = {}

    def add(self, x, at, gain_db, pan=0.0, send=0.0, bus='main', stem='other'):
        x = np.asarray(x, dtype=float)
        if x.ndim == 1:
            gl, gr = pan_gains(pan)
            x = np.vstack([x * gl, x * gr])
        i = int(round(at * SR))
        if i < 0:
            x, i = x[:, -i:], 0
        j = min(N, i + x.shape[1])
        if j <= i:
            return
        seg = x[:, :j - i] * db(gain_db)
        self.bus[bus][:, i:j] += seg
        if send:
            self.send[:, i:j] += seg * send
        st = self.stems.setdefault(stem, np.zeros((2, N)))
        st[:, i:j] += seg

    def done(self, t60=2.6):
        n = int(SR * t60)
        t = np.arange(n) / SR
        r = np.random.default_rng(7)
        ir = r.standard_normal((2, n)) * np.exp(-6.91 * t / t60)[None, :]
        ir[:, :int(0.012 * SR)] = 0                        # a little pre-delay
        ir = signal.sosfilt(sos('low', 5200), ir, axis=1)
        ir = signal.sosfilt(sos('high', 120), ir, axis=1)
        ir /= np.sqrt(np.sum(ir ** 2, axis=1, keepdims=True))
        wet = np.vstack([signal.fftconvolve(self.send[c], ir[c])[:N] for c in range(2)])
        # the hush: everything but the free bus drops away for the breath before the climax
        h0, h1 = TEMPO['hush']
        tn = np.arange(N) / SR
        duck = 1 - 0.94 * np.clip((tn - h0) / 0.08, 0, 1) * (tn < h1)
        x = (self.bus['main'] + 0.42 * wet) * duck[None, :] + self.bus['free']
        # a gentle glue: soft saturation, then the end faded with the picture
        x = np.tanh(x * 0.9) / 0.9
        f0, f1 = TEMPO['fadeOut']
        x *= np.clip((LEN - 0.1 - tn) / (LEN - 0.1 - f0), 0, 1)[None, :] ** 1.5
        return x


# ---------------------------------------------------------------- instruments

def saws(f, d, voices=(-9, 0, 9), vib=0.0, vib_hz=5.2, seed=0, glide=None):
    """Detuned saws (a naive saw per voice: the filters after them take the aliasing off), one row per voice."""
    t = tt(d)
    r = np.random.default_rng(seed)
    out = []
    for c in voices:
        ff = f * 2 ** (c / 1200) * (1 + vib * np.sin(2 * np.pi * vib_hz * t + r.uniform(0, 6)) * np.clip(t / 0.4, 0, 1))
        if glide is not None:
            ff = ff * glide
        ph = np.cumsum(ff) / SR + r.uniform(0, 1)
        out.append(2 * (ph % 1) - 1)
    return np.array(out)


def adsr(d, a, r, s=1.0, dec=0.0):
    t = tt(d)
    env = np.clip(t / max(a, 1e-4), 0, 1) ** 1.5
    if dec:
        env *= s + (1 - s) * np.exp(-np.maximum(t - a, 0) / dec)
    env *= np.clip((d - t) / max(r, 1e-4), 0, 1)
    return env


def strings(notes, d, a=0.5, r=0.9, cut=2400, bright=0.0, seed=0, spread=0.7):
    """An ensemble: each note three detuned saws with vibrato, through a low-pass, spread left and right."""
    out = np.zeros((2, int(d * SR)))
    env = adsr(d, a, r)
    for k, m in enumerate(notes):
        v = saws(mf(m), d, (-11, 0, 10), vib=0.0018, seed=seed * 31 + k)
        y = signal.sosfilt(sos('low', cut + bright * 2000, 2), v.sum(0))
        p = spread * (((k * 0.618) % 1) * 2 - 1)
        gl, gr = pan_gains(p)
        out += np.vstack([y * gl, y * gr])
    return out * env[None, :] / max(1, len(notes))


def choir(notes, d, a=0.8, r=1.2, seed=0):
    """Voices on 'ah': saws through two formant bands."""
    out = np.zeros((2, int(d * SR)))
    env = adsr(d, a, r)
    for k, m in enumerate(notes):
        v = saws(mf(m), d, (-14, -4, 6, 15), vib=0.004, vib_hz=4.6, seed=seed * 17 + k).sum(0)
        y = signal.sosfilt(sos('band', [600, 1000], 2), v) + 0.6 * signal.sosfilt(sos('band', [1050, 1500], 2), v) + 0.25 * signal.sosfilt(sos('band', [2500, 3200], 2), v)
        gl, gr = pan_gains(((k * 0.37) % 1) * 1.4 - 0.7)
        out += np.vstack([y * gl, y * gr])
    return out * env[None, :] / max(1, len(notes))


def brass(m, d, seed=0, dark=700, bright=3600, drive=1.6):
    """A brass-like note: its brightness opens with the attack and settles (a dark and a bright copy crossfaded)."""
    v = saws(mf(m), d, (-7, 0, 6), vib=0.0025, vib_hz=5.0, seed=seed).sum(0)
    lo = signal.sosfilt(sos('low', dark, 2), v)
    hi = signal.sosfilt(sos('low', bright, 2), v)
    t = tt(d)
    b = np.clip(t / 0.07, 0, 1) * (0.55 + 0.45 * np.exp(-t / 0.35))
    y = np.tanh(drive * norm(lo * (1 - b) + hi * b))
    return norm(y) * adsr(d, 0.04, min(0.25, d * 0.4))


def celesta(m, d=2.2):
    t = tt(d)
    f = mf(m)
    v = sum(a * np.sin(2 * np.pi * f * k * t + k) * np.exp(-t / dd) for k, a, dd in ((1, 1, 1.1), (2, 0.25, 0.5), (3.0, 0.08, 0.25), (4.01, 0.12, 0.18), (6.3, 0.04, 0.08)))
    return norm(v * (1 - np.exp(-t / 0.002)))


def taiko(f0=62, d=1.4, skin=0.5, seed=0):
    t = tt(d)
    ff = f0 * (1 + 1.3 * np.exp(-t / 0.025))
    body = np.sin(2 * np.pi * np.cumsum(ff) / SR) * np.exp(-t / 0.42)
    nz = signal.sosfilt(sos('band', [140, 900], 2), np.random.default_rng(seed).standard_normal(len(t))) * np.exp(-t / 0.05)
    # its harmonics, so a TV's small speakers still give the thump
    up = signal.sosfilt(sos('band', [110, 360], 2), np.tanh(3 * body))
    return norm(norm(body) + skin * norm(nz) + 0.45 * norm(up)) * (1 - np.exp(-t / 0.002))


def ka(seed=0):
    t = tt(0.25)
    nz = signal.sosfilt(sos('band', [900, 4500], 2), np.random.default_rng(seed).standard_normal(len(t))) * np.exp(-t / 0.03)
    tone = np.sin(2 * np.pi * 420 * t) * np.exp(-t / 0.04)
    return norm(nz + 0.5 * tone)


def riser(d, f0=300, f1=5000, seed=0, tone=True):
    """Noise through a band rising to the hit, with a rising tone in octaves, swelling."""
    t = tt(d)
    fc = lambda s: f0 * (f1 / f0) ** np.clip(s / d, 0, 1) ** 1.6
    x = np.vstack([shaped_noise(d, fc, 0.8, seed), shaped_noise(d, fc, 0.8, seed + 1)])
    env = (t / d) ** 2.4
    if tone:
        sw = 110 * 2 ** (3.2 * (t / d) ** 1.4)
        ph = 2 * np.pi * np.cumsum(sw) / SR
        y = (np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.3 * np.sin(4 * ph + 1)) * 0.5
        x = x + np.vstack([y, y]) * 0.6
    return norm(x * env[None, :])


def braam(root=26, d=3.6, seed=0):
    """The deep brass blast at the climax: a cluster of driven saws opening up."""
    t = tt(d)
    v = sum(saws(mf(m), d, (-12, 0, 12), seed=seed + k).sum(0) for k, m in enumerate([root, root + 12, root + 19, root + 24]))
    lo = signal.sosfilt(sos('low', 260, 2), v)
    hi = signal.sosfilt(sos('low', 1800, 2), v)
    b = np.clip(t / 0.12, 0, 1) * np.exp(-t / 1.2)
    y = np.tanh(2.5 * norm(lo * (1 - b) + hi * b))
    env = np.clip(t / 0.02, 0, 1) * np.exp(-t / 1.5)
    return norm(y * env)


def shimmer(d, seed=0):
    """Light: high noise in a gleaming band and a cluster of high partials, swelling and fading."""
    t = tt(d)
    nz = np.vstack([signal.sosfilt(sos('band', [5000, 12000], 2), np.random.default_rng(seed + c).standard_normal(len(t))) for c in range(2)])
    tones = sum(np.sin(2 * np.pi * f * t + i) * (0.6 + 0.4 * np.sin(2 * np.pi * (3 + i) * t)) for i, f in enumerate((2349.3, 2793.8, 3520.0, 4698.6)))
    env = np.sin(np.pi * np.clip(t / d, 0, 1)) ** 2
    return norm((norm(nz) * 0.6 + np.vstack([tones, tones]) * 0.08) * env[None, :])


# ---------------------------------------------------------------- the score

HOOK = [(0, 1.5, 62), (1.5, 0.5, 65), (2, 1, 69), (3, 0.5, 67), (3.5, 0.5, 65),
        (4, 1, 65), (5, 0.5, 62), (5.5, 0.5, 60), (6, 2, 62),
        (8, 1.5, 69), (9.5, 0.5, 72), (10, 1, 74), (11, 0.5, 72), (11.5, 0.5, 69),
        (12, 1, 67), (13, 0.5, 69), (13.5, 0.5, 67), (14, 2, 64)]
RISE = [(8, 1, 67), (9, 1, 70), (10, 1, 74), (11, 1, 79), (12, 1, 69), (13, 1, 73), (14, 1, 76), (15, 1, 81)]   # over Gm and A


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    m = Mix()
    T = TEMPO

    # ---- pads, a chord a bar, across the whole piece (the card's own chord rings out longer)
    for i, (t0, ch, sec) in enumerate(BARS):
        notes, bass = CHORDS[ch]
        d = BAR + 0.9 if sec != 'card' else LEN - t0
        lvl = {'sky': -17, 'arrival': -16, 'strike': -16, 'build': -13, 'climax': -12, 'card': -13}[sec]
        a = 0.9 if sec in ('sky', 'card') else 0.35
        m.add(strings(notes, d, a=a, r=0.9 if sec != 'card' else 4.5, cut=1500 if sec == 'sky' else 2400, seed=i), t0 - 0.05, lvl, send=0.55, stem='strings')
        # the bass: cello and bass, an octave apart
        bl = signal.sosfilt(sos('low', 400, 2), saws(mf(bass), d, (-6, 6), seed=i + 50).sum(0) + 0.6 * saws(mf(bass + 12), d, (-5, 5), seed=i + 60).sum(0))
        m.add(norm(bl) * adsr(d, a, 0.8), t0 - 0.05, lvl - 4 + (0 if sec != 'sky' else -4), send=0.2, stem='bass')
        if sec in ('climax', 'card'):
            m.add(choir([n + 12 for n in notes[1:4]], d, a=0.5 if sec == 'climax' else 0.15, r=1.5 if sec == 'climax' else 4.5, seed=i), t0, lvl - 3, send=0.6, stem='choir')

    # ---- the sky: a sub drone, the stars' notes, the glitter, the bloom, the riser, the pickup
    t = tt(10.5)
    drone = np.sin(2 * np.pi * mf(26) * t) * np.clip(t / 3, 0, 1) * np.clip((10.4 - t) / 0.6, 0, 1)
    drone = drone + 0.35 * signal.sosfilt(sos('band', [90, 260], 2), np.tanh(2.5 * drone))
    m.add(drone, 0, -15, stem='sub')
    st = T['stars']
    STAR_NOTES = [74, 81, 77, 81, 76, 81, 74, 81, 77, 81, 84, 86, 88, 89]
    for k in range(st['n'] + 1):
        if k >= len(STAR_NOTES):
            break
        m.add(celesta(STAR_NOTES[k]), st['t0'] + k * st['step'], -25 + (k / 14) * 3, pan=((k * 0.41) % 1) * 1.2 - 0.6, send=0.7, stem='celesta')
    b0, b1 = st['ball']
    run = [81, 84, 86, 88, 89, 93, 96, 98, 100, 101, 98, 101, 105]
    for k, nt in enumerate(run):
        m.add(celesta(nt, 1.4), b0 + k * (b1 - b0) / len(run), -29, pan=-0.6 + 1.2 * k / len(run), send=0.8, stem='celesta')
    m.add(shimmer(2.4, 3), b0 - 0.4, -22, send=0.6, stem='fx')
    m.add(sub_boom(44, 2.2, 1.0, 11), b1, -12, send=0.25, stem='hits')                       # the bloom
    m.add(riser(2.5, 250, 7000, 13), 7.5, -17, send=0.3, stem='fx')
    m.add(cymbal(14, 2.5)[:, ::-1], 10 - 2.5, -24, send=0.3, stem='fx')
    for k in range(4):                                                                          # the tom pickup
        m.add(taiko(110 + 18 * k, 0.8, 0.6, 20 + k), 10 - BEAT + k * BEAT / 4, -14 + k * 1.5, pan=-0.4 + 0.27 * k, send=0.3, stem='drums')

    # ---- drums: patterns in sixteenths, a bar each
    BIG, MID, KA = 'B', 'M', 'K'
    groove = {0: BIG, 4: KA, 6: MID, 8: MID, 10: BIG, 12: MID, 13: KA, 14: MID, 15: KA}
    half = {0: BIG, 8: BIG, 14: KA}
    drive = {0: BIG, 2: MID, 4: KA, 6: MID, 8: BIG, 10: MID, 11: KA, 12: MID, 14: MID, 15: KA}
    seed = 100
    for t0, ch, sec in BARS:
        pat = {'arrival': groove, 'strike': half, 'build': drive, 'climax': groove}.get(sec)
        if not pat:
            continue
        for step, kind in pat.items():
            at = t0 + step * BEAT / 4
            seed += 1
            if kind == BIG:
                m.add(taiko(58, 1.6, 0.45, seed), at, -6 if sec != 'strike' else -5, send=0.3, stem='drums')
            elif kind == MID:
                m.add(taiko(96, 1.0, 0.6, seed), at, -12 + (2 if sec == 'build' else 0), pan=(0.35 if step % 4 else -0.35), send=0.3, stem='drums')
            else:
                m.add(ka(seed), at, -19 + (2 if sec == 'build' else 0), pan=(-0.5 if step % 2 else 0.5), send=0.25, stem='drums')

    # ---- the ostinato: low strings in eighths (sixteenths in the build), the chord's root and fifth
    for t0, ch, sec in BARS:
        if sec not in ('arrival', 'build', 'climax'):
            continue
        notes, bass = CHORDS[ch]
        sub = 4 if sec == 'build' else 2
        root = bass + 12 if bass < 40 else bass
        for k in range(4 * sub):
            nt = root + (7 if k % 4 == 3 else 12 if k % 8 == 6 else 0)
            d = BEAT / sub * 0.85
            y = signal.sosfilt(sos('low', 1100, 2), saws(mf(nt), d, (-8, 8), seed=k).sum(0)) * adsr(d, 0.005, 0.04, 0.4, 0.07)
            acc = 1.0 if k % sub == 0 else 0.7
            m.add(norm(y) * acc, t0 + k * BEAT / sub, -15 if sec != 'build' else -12, pan=0.25 if k % 2 else -0.25, send=0.2, stem='ostinato')

    # ---- the hook
    def phrase(notes, t0, lvl, inst, octave=0, pan=0.0, send=0.35, stem='brass', legato=1.0):
        for b, d, nt in notes:
            dur = d * BEAT * legato
            if inst == 'brass':
                y = brass(nt + octave, dur + 0.12, seed=int(b * 8))
                m.add(np.vstack([y, y]) * 0.7, t0 + b * BEAT, lvl, send=send, stem=stem)
            else:
                m.add(strings([nt + octave, nt + octave - 12], dur + 0.25, a=0.12, r=0.35, cut=4200, seed=int(b * 8)), t0 + b * BEAT, lvl, pan=pan, send=send, stem=stem)

    phrase(HOOK, 10.0, -12, 'brass', octave=-12)                                   # the ground: low brass
    phrase(HOOK, 20.0, -17.5, 'strings', octave=12, send=0.6, stem='hook-strings', legato=1.15)   # the strike: high strings
    phrase(HOOK[:9], 30.0, -9, 'brass', octave=0)                                  # the build: the hook, then rising
    phrase(RISE, 30.0, -8.5, 'brass', octave=0)
    phrase(RISE, 30.0, -16, 'strings', octave=12, stem='hook-strings')
    phrase(HOOK[:9], 40.0, -9, 'brass', octave=0, send=0.45)                       # the climax: everything
    phrase(HOOK[:9], 40.0, -12, 'brass', octave=-12, send=0.45)
    phrase(HOOK[:9], 40.0, -12, 'strings', octave=12, send=0.6, stem='hook-strings', legato=1.1)

    # ---- hits, whooshes and the moments of the picture
    m.add(sub_drop(95, 30, 2.0, 0.7), 10.0, -6, stem='sub')
    m.add(sub_boom(48, 1.6, 0.6, 21), 10.0, -7, send=0.3, stem='hits')
    m.add(cymbal(22, 3.5), 10.0, -20, send=0.4, stem='hits')
    m.add(shimmer(1.6, 23), T['glare'] - 0.9, -17, send=0.6, stem='fx')            # the floodlights' glare
    for c in [s for s in TEMPO['shots'] if 14 < s['t0'] < 40]:                     # a breath of air on each cut
        m.add(whoosh(0.45, 600, 4500, int(c['t0'] * 10), -0.5, 0.5), c['t0'] - 0.3, -24, send=0.15, stem='fx')
    m.add(sub_drop(90, 32, 1.8, 0.6), 20.0, -8, stem='sub')
    m.add(sub_boom(46, 1.8, 0.8, 31), 20.0, -9, send=0.3, stem='hits')
    s0 = T['strike']                                                               # the strike
    m.add(cymbal(32, 2.2)[:, ::-1], s0 - 2.2, -22, send=0.3, stem='fx')
    m.add(sub_boom(42, 2.4, 1.2, 33), s0, -4, send=0.35, stem='hits')
    m.add(crack(34, 0.02, 2000), s0, -13, send=0.5, stem='hits')
    m.add(cymbal(35, 4.0), s0, -16, send=0.5, stem='hits')
    m.add(whoosh(2.2, 160, 1400, 36, -0.4, 0.7, shape=1.0), s0 + 0.1, -16, send=0.4, stem='fx')   # the ball, slowed
    m.add(whoosh(0.9, 500, 5000, 37, -0.7, 0.7), 26.25 - 0.1, -15, send=0.3, stem='fx')
    m.add(whoosh(0.8, 300, 2600, 38, 0.6, -0.6), 27.5 + 0.2, -16, send=0.3, stem='fx')          # the keeper
    n0 = T['net']                                                                  # the net
    m.add(sub_boom(55, 1.4, 0.5, 39), n0, -9, send=0.3, stem='hits')
    m.add(whoosh(0.7, 2500, 400, 40, 0.0, 0.0, shape=0.8), n0, -18, send=0.4, stem='fx')
    m.add(shimmer(2.0, 41), n0, -20, send=0.6, stem='fx')
    m.add(sub_boom(46, 1.6, 0.7, 42), 30.0, -7, send=0.3, stem='hits')
    m.add(cymbal(43, 3.0), 30.0, -19, send=0.4, stem='hits')
    for k, (t0, y, a) in enumerate(TEMPO['streaks']):                              # the light streaks
        if t0 < 45:
            m.add(whoosh(0.5, 900, 7000, 50 + k, -0.8, 0.8), t0 - 0.05, -21, send=0.2, stem='fx')
    # the build's snare roll: sixteenths, then thirty-seconds, rising
    for k in range(32):
        at = 35.0 + k * BEAT / 4
        m.add(snare(60 + k), at, -24 + k * 0.4, pan=-0.3 if k % 2 else 0.3, send=0.3, stem='drums')
    for k in range(int(round((T['hush'][0] - 37.5) / (BEAT / 8)))):
        at = 37.5 + k * BEAT / 8
        m.add(snare(100 + k), at, -18 + k * 0.35, pan=-0.3 if k % 2 else 0.3, send=0.3, stem='drums')
    m.add(riser(4.4, 200, 9000, 70), 35.0, -12, send=0.3, stem='fx')
    m.add(celesta(98, 3.0), T['star'], -12, send=0.8, bus='free', stem='celesta')   # the ball becomes a star
    m.add(celesta(105, 3.0), T['star'] + 0.02, -18, send=0.8, bus='free', stem='celesta')
    # the hush, and the breath in before the climax (played through the hush)
    h0, h1 = T['hush']
    m.add(cymbal(71, 1.4)[:, ::-1] * np.linspace(0, 1, int(1.4 * SR))[None, :], h1 - 1.4, -15, bus='free', stem='fx')
    m.add(riser(h1 - h0 + 0.05, 1500, 12000, 72, tone=False), h0, -18, bus='free', stem='fx')

    # ---- the climax
    c0 = T['climax']
    m.add(braam(26, 4.0, 80), c0, -6, send=0.35, stem='hits')
    m.add(sub_drop(110, 28, 2.6, 0.9), c0, -4, stem='sub')
    m.add(sub_boom(40, 2.6, 1.4, 81), c0, -4, send=0.35, stem='hits')
    m.add(crack(82, 0.03, 1500), c0, -10, send=0.5, stem='hits')
    m.add(cymbal(83, 5.0), c0, -12, send=0.5, stem='hits')
    r0, r1 = T['reveal']
    m.add(celesta(93, 2.5), r0, -14, pan=-0.3, send=0.8, stem='celesta')            # the outline complete
    m.add(celesta(98, 2.5), r0 + 0.03, -17, pan=0.3, send=0.8, stem='celesta')
    m.add(riser(r1 - r0, 800, 9000, 84, tone=False), r0, -20, send=0.4, stem='fx')   # forming
    m.add(sub_boom(50, 1.6, 0.6, 85), r1, -9, send=0.35, stem='hits')                 # whole
    m.add(ping(1174.7, 2.5, 86), r1, -14, pan=-0.2, send=0.7, stem='celesta')
    m.add(ping(1760.0, 2.5, 87), r1 + 0.04, -16, pan=0.2, send=0.7, stem='celesta')
    m.add(shimmer(2.5, 88), r1, -18, send=0.6, stem='fx')
    for k in range(4):                                                                  # pickup into the card
        m.add(taiko(120 + 15 * k, 0.8, 0.6, 90 + k), T['card'] - BEAT + k * BEAT / 4, -12 + k, pan=-0.4 + 0.27 * k, send=0.3, stem='drums')
    m.add(cymbal(94, 1.25)[:, ::-1], T['card'] - 1.25, -20, send=0.3, stem='fx')

    # ---- the end card: the sting
    k0 = T['card']
    for nt in (62, 66, 69, 74):
        y = brass(nt, 2.6, seed=nt, bright=4200)
        m.add(np.vstack([y, y]) * 0.6, k0, -10, send=0.6, stem='brass')
    for nt in (38, 50):
        y = brass(nt, 3.0, seed=nt, dark=400, bright=1500)
        m.add(np.vstack([y, y]) * 0.7, k0, -10, send=0.4, stem='brass')
    m.add(sub_boom(44, 3.0, 1.6, 95), k0, -3, send=0.4, stem='hits')
    m.add(crack(96, 0.02, 1800), k0, -12, send=0.5, stem='hits')
    m.add(cymbal(97, 5.5), k0, -13, send=0.6, stem='hits')
    m.add(ping(1174.7, 4.0, 98), k0, -13, pan=-0.25, send=0.8, stem='celesta')
    m.add(ping(1480.0, 4.0, 99), k0 + 0.05, -15, pan=0.25, send=0.8, stem='celesta')
    m.add(celesta(86, 2.0), T['letters'], -20, pan=-0.3, send=0.8, stem='celesta')     # MATCHDAY
    m.add(celesta(93, 2.0), T['teams'], -21, pan=0.3, send=0.8, stem='celesta')       # the teams
    m.add(shimmer(4.5, 100), k0 + 0.6, -22, send=0.6, stem='fx')

    x = m.done()
    # loudness: the climax at about -14 dBFS RMS (as heard: the deep bass, which TV speakers hardly give, left out of the measure)
    heard = signal.sosfilt(sos('high', 150, 2), x, axis=1)
    c = slice(int(40 * SR), int(45 * SR))
    rms = np.sqrt(np.mean(heard[:, c] ** 2))
    x = x * (db(-14) / rms)
    pk = np.max(np.abs(x))
    if pk > db(-1):
        x = x * db(-1) / pk
    wavfile.write(os.path.join(out, 'intro-music.wav'), SR, (np.clip(x, -1, 1).T * 32767).astype(np.int16))
    # the levels as they come, a second at a time, and each stem's share (for checking the mix)
    lv = [20 * np.log10(np.sqrt(np.mean(heard[:, int(s * SR):int((s + 1) * SR)] ** 2)) * (db(-14) / rms) + 1e-9) for s in range(int(LEN))]
    print('peak %.2f dBFS' % (20 * np.log10(np.max(np.abs(x)))))
    print('dB by second:', ' '.join('%d' % v for v in lv))
    secs = [(sc['name'], sc['t0'], sc['t1']) for sc in TEMPO['sections']]
    print('  %-13s ' % 'stem (dB)' + ' '.join('%8s' % n for n, _, _ in secs))
    for k, v in sorted(m.stems.items()):
        hv = signal.sosfilt(sos('high', 150, 2), v, axis=1) * (db(-14) / rms)
        print('  %-13s ' % k + ' '.join('%8.1f' % (20 * np.log10(np.sqrt(np.mean(hv[:, int(a * SR):int(b * SR)] ** 2)) + 1e-9)) for _, a, b in secs))


if __name__ == '__main__':
    main()
