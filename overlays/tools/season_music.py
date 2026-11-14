"""The season intros' music, made here from nothing (no samples, no one else's tune): python3 season_music.py KEY N OUT.wav

120 beats a minute, a bar (two seconds) for each club, as src/season/season.js draws them: two bars of opening (a swell,
the season's slam on the second, a roll up into the first club), N bars of the clubs (four on the floor, claps, a driving
bass, chords, a plucked run, a brass hit and a low boom on every club's downbeat), two bars of the close (a big chord,
a harp-like run up as the crests land, a roll), ending clean on the last beat for the Whitebrook Road Sports ending.
Each league in its own key: pl A minor, laliga E (with a Spanish turn), seriea C minor, bundesliga D minor, ligue1 G minor.
"""
import sys
import wave

import numpy as np
from scipy.signal import butter, fftconvolve, sosfilt

SR = 44100
BEAT = 0.5
BAR = 2.0
RNG = np.random.default_rng(2026)

NOTE = {'C': 0, 'C#': 1, 'Db': 1, 'D': 2, 'Eb': 3, 'E': 4, 'F': 5, 'F#': 6, 'G': 7, 'Ab': 8, 'A': 9, 'Bb': 10, 'B': 11}
KEYS = {
    'pl': [('A', 'm'), ('F', ''), ('C', ''), ('G', '')],
    'laliga': [('E', 'm'), ('C', ''), ('D', ''), ('B', '')],
    'seriea': [('C', 'm'), ('Ab', ''), ('Eb', ''), ('Bb', '')],
    'bundesliga': [('D', 'm'), ('Bb', ''), ('F', ''), ('C', '')],
    'ligue1': [('G', 'm'), ('Eb', ''), ('Bb', ''), ('F', '')],
}


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def chord(root, q, octave=4):
    r = 12 * (octave + 1) + NOTE[root]
    return [r, r + (3 if q == 'm' else 4), r + 7]


def lp(x, f, order=2):
    return sosfilt(butter(order, min(f, SR * 0.45), 'low', fs=SR, output='sos'), x)


def hp(x, f, order=2):
    return sosfilt(butter(order, f, 'high', fs=SR, output='sos'), x)


def bp(x, a, b, order=2):
    return sosfilt(butter(order, [a, min(b, SR * 0.45)], 'band', fs=SR, output='sos'), x)


def saw(f, n, ph=0.0):
    t = np.arange(n) / SR
    return 2.0 * ((f * t + ph) % 1.0) - 1.0


def env(n, a, d, s, r, hold=None):
    """attack, decay to sustain, held, released over the last r seconds (all seconds)"""
    e = np.zeros(n)
    na, nd, nr = int(a * SR), int(d * SR), int(r * SR)
    nh = n - na - nd - nr if hold is None else int(hold * SR)
    nh = max(0, min(nh, n - na - nd - nr))
    i = 0
    if na: e[i:i + na] = np.linspace(0, 1, na, endpoint=False); i += na
    if nd: e[i:i + nd] = np.linspace(1, s, nd, endpoint=False); i += nd
    e[i:i + nh] = s; i += nh
    rest = n - i
    if rest > 0: e[i:] = np.linspace(s, 0, rest)
    return e


class Mix:
    def __init__(self, seconds):
        self.n = int(seconds * SR) + SR * 3
        self.dry = np.zeros((self.n, 2))
        self.wet = np.zeros((self.n, 2))

    def add(self, sig, at, gain=1.0, pan=0.0, send=0.0):
        i = int(at * SR)
        if i >= self.n: return
        sig = sig[: self.n - i]
        l, r = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
        if sig.ndim == 1:
            st = np.stack([sig * l, sig * r], 1) * gain
        else:
            st = sig * gain
        self.dry[i:i + len(st)] += st
        if send: self.wet[i:i + len(st)] += st * send


# ---- instruments ---------------------------------------------------------------------------------------------------
def kick(big=1.0):
    n = int(0.45 * SR); t = np.arange(n) / SR
    f = 46 + 120 * np.exp(-t / 0.028)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / (0.32 * big))
    click = hp(RNG.standard_normal(n), 2500) * np.exp(-t / 0.004) * 0.35
    return np.tanh((body + click) * 1.6) * 0.9


def clap():
    n = int(0.35 * SR); t = np.arange(n) / SR
    nz = bp(RNG.standard_normal(n), 900, 6000)
    e = np.zeros(n)
    for k, d in enumerate([0.0, 0.011, 0.022]):
        i = int(d * SR); e[i:] += np.exp(-(t[: n - i]) / (0.012 if k < 2 else 0.16))
    tone = np.sin(2 * np.pi * 190 * t) * np.exp(-t / 0.05) * 0.4
    return (nz * e * 0.7 + tone) * 0.8


def hat(open_=False):
    n = int((0.3 if open_ else 0.06) * SR); t = np.arange(n) / SR
    return hp(RNG.standard_normal(n), 7500) * np.exp(-t / (0.09 if open_ else 0.018)) * 0.5


def tom(f0=140, f1=90, dec=0.35):
    n = int(0.6 * SR); t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-t / 0.06)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / dec) * 0.9


def boom(dec=1.1):
    n = int(2.2 * SR); t = np.arange(n) / SR
    f = 34 + 40 * np.exp(-t / 0.12)
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / dec)
    nz = lp(RNG.standard_normal(n), 1800) * np.exp(-t / 0.22) * 0.5
    return np.tanh((sub + nz) * 1.3) * 0.9


def supersaw(notes, dur, cutoff=2400, a=0.04, r=0.35):
    n = int((dur + r) * SR)
    out = np.zeros((n, 2))
    for m in notes:
        for k, cents in enumerate([-14, -7, 0, 7, 14]):
            f = hz(m) * 2 ** (cents / 1200)
            v = saw(f, n, RNG.random())
            pan = (k - 2) / 2.5
            out[:, 0] += v * np.cos((pan + 1) * np.pi / 4)
            out[:, 1] += v * np.sin((pan + 1) * np.pi / 4)
    e = env(n, a, 0.2, 0.85, r)
    out[:, 0] = lp(out[:, 0], cutoff) * e
    out[:, 1] = lp(out[:, 1], cutoff) * e
    return out / (len(notes) * 5) * 2.2


def pluck(m, dur=0.22, bright=4200):
    n = int(dur * SR); t = np.arange(n) / SR
    f = hz(m)
    v = saw(f, n) * 0.6 + np.sign(np.sin(2 * np.pi * f * t)) * 0.4
    return lp(v, bright) * np.exp(-t / 0.07) * 0.6


def bass(m, dur):
    n = int(dur * SR); t = np.arange(n) / SR
    f = hz(m)
    v = lp(saw(f, n), 520) * 0.7 + np.sin(2 * np.pi * f / 2 * t) * 0.55
    return v * env(n, 0.004, 0.08, 0.75, 0.03)


def brass(notes, dur=0.42):
    n = int(dur * SR); t = np.arange(n) / SR
    v = np.zeros(n)
    for m in notes:
        for c in (-6, 6):
            v += saw(hz(m) * 2 ** (c / 1200), n, RNG.random())
    bright, dark = lp(v, 5200), lp(v, 900)
    k = np.exp(-t / 0.06)
    return (bright * k + dark * (1 - k)) * env(n, 0.008, 0.12, 0.55, 0.15) / len(notes) * 0.9


def bell(m, dur=1.2):
    n = int(dur * SR); t = np.arange(n) / SR
    f = hz(m)
    v = np.sin(2 * np.pi * f * t) + 0.4 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t / 0.25) + 0.2 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t / 0.1)
    return v * np.exp(-t / 0.55) * 0.35


def riser(dur):
    n = int(dur * SR); t = np.arange(n) / SR
    nz = RNG.standard_normal(n)
    centers = np.geomspace(250, 9000, 12)
    pos = np.log(250) + (np.log(9000) - np.log(250)) * (t / dur) ** 1.6
    out = np.zeros(n)
    for c in centers:
        band = bp(nz, c / 1.3, c * 1.3)
        w = np.exp(-((np.log(c) - pos) ** 2) / 0.18)
        out += band * w
    sweep = np.sin(2 * np.pi * np.cumsum(300 * (5 ** (t / dur))) / SR) * 0.15
    return (out * 0.5 + sweep) * (t / dur) ** 2


def reverse_cymbal(dur):
    n = int(dur * SR); t = np.arange(n) / SR
    return hp(RNG.standard_normal(n), 4000) * (t / dur) ** 3 * 0.6


def reverb_ir(sec=1.9):
    n = int(sec * SR); t = np.arange(n) / SR
    ir = np.stack([lp(RNG.standard_normal(n), 6000), lp(RNG.standard_normal(n), 6000)], 1)
    ir *= np.exp(-t / (sec / 5))[:, None]
    return ir / np.sqrt((ir ** 2).sum(0))


# ---- the piece -----------------------------------------------------------------------------------------------------
def compose(key, n_clubs):
    prog = KEYS[key]
    open_bars, close_bars = 2, 2
    total = (open_bars + n_clubs + close_bars) * BAR
    mx = Mix(total)
    pads, beds = Mix(total), Mix(total)   # sidechained afterwards

    # -- the opening: a swell, the mark's shimmer, the season's slam on bar 2, a roll up
    r0, q0 = prog[0]
    pads.add(supersaw(chord(r0, q0, 4) + [12 * 5 + NOTE[r0]], 2.0, cutoff=900, a=1.6, r=0.3), 0.0, 0.8)
    for i, m in enumerate(chord(r0, q0, 5) + [chord(r0, q0, 5)[0] + 12]):
        mx.add(bell(m, 1.6), 0.15 + i * 0.09, 0.5, pan=(i - 1.5) / 3, send=0.5)
    mx.add(riser(4.0), 0.0, 0.32, send=0.3)
    mx.add(boom(1.4), 2.0, 1.0, send=0.25)
    mx.add(kick(1.3), 2.0, 0.9)
    mx.add(brass(chord(r0, q0, 4) + [chord(r0, q0, 4)[0] + 12]), 2.0, 0.85, send=0.3)
    pads.add(supersaw(chord(r0, q0, 4), 2.0, cutoff=1800, a=0.01, r=0.2), 2.0, 0.9)
    rolls = [(2.0 + k * 0.25, 0.3) for k in range(4)] + [(3.0 + k * 0.125, 0.4) for k in range(4)] + [(3.5 + k * 0.0625, 0.5 + k * 0.04) for k in range(8)]
    for at, gn in rolls:
        mx.add(clap(), at, gn, send=0.2)
    mx.add(reverse_cymbal(1.2), 4.0 - 1.2, 0.6, send=0.2)

    # -- the clubs
    for b in range(n_clubs):
        t0 = (open_bars + b) * BAR
        r, q = prog[b % 4]
        tri = chord(r, q, 4)
        # a boom and the brass on the club's downbeat (a bigger one every fourth)
        mx.add(boom(0.9 if b % 4 else 1.3), t0, 0.55 if b % 4 else 0.8, send=0.2)
        mx.add(brass(tri + [tri[0] + 12]), t0, 0.55, send=0.3)
        mx.add(brass(tri + [tri[0] + 12], 0.25), t0 + 1.25, 0.35, send=0.3)
        for k in range(4):
            mx.add(kick(), t0 + k * BEAT, 0.95)
        for k in (1, 3):
            mx.add(clap(), t0 + k * BEAT, 0.5, send=0.25)
        for k in range(16):
            mx.add(hat(open_=k % 4 == 2), t0 + k * BEAT / 4, (0.22 if k % 2 else 0.13) * (1.4 if k % 4 == 2 else 1), pan=0.25)
        if b % 4 == 3:   # a tom fill into the next four
            for k, f in enumerate([180, 150, 130, 110]):
                mx.add(tom(f, f * 0.7), t0 + 1.5 + k * 0.125, 0.45, pan=(k - 1.5) / 3, send=0.2)
        root2 = 12 * 3 + NOTE[r]
        pat = [0, 0, 12, 0, 0, 12, 0, 7]
        for k in range(8):
            beds.add(bass(root2 + pat[k], 0.24), t0 + k * BEAT / 2, 0.75)
        pads.add(supersaw(tri + [tri[0] + 12], 2.0, cutoff=2600), t0, 0.75)
        if b >= 2:   # the plucked run, from the third club on
            run = [tri[0] + 12, tri[1] + 12, tri[2] + 12, tri[0] + 24, tri[2] + 12, tri[1] + 12, tri[0] + 12, tri[1] + 12]
            for k in range(16):
                mx.add(pluck(run[k % 8]), t0 + k * BEAT / 4, 0.36, pan=0.35 if k % 2 else -0.35, send=0.35)
        if b >= n_clubs - 4:   # the last four: a high line over it
            top = [tri[2] + 12, tri[0] + 24, tri[1] + 24, tri[0] + 24]
            for k in range(4):
                mx.add(bell(top[k], 0.6), t0 + k * BEAT, 0.3, send=0.5)

    # -- the close: a big chord, the crests' run up, a roll, clean on the last beat
    tc = (open_bars + n_clubs) * BAR
    tri = chord(r0, q0, 4)
    mx.add(boom(1.6), tc, 1.0, send=0.3)
    mx.add(kick(1.3), tc, 0.9)
    mx.add(brass(tri + [tri[0] + 12, tri[2] + 12], 0.9), tc, 0.7, send=0.4)
    pads.add(supersaw(tri + [tri[0] + 12, tri[0] - 12], 3.4, cutoff=3200, a=0.02, r=0.5), tc, 0.95)
    scale = [0, 3, 5, 7, 10] if q0 == 'm' else [0, 2, 4, 7, 9]
    base = 12 * 5 + NOTE[r0]
    for i in range(n_clubs):   # each crest landing (0.02 + i * 0.033 s): one note higher
        m = base + scale[i % 5] + 12 * (i // 5)
        mx.add(bell(m, 0.9), tc + 0.02 + i * 0.033, 0.22, pan=(i / max(1, n_clubs - 1)) * 1.4 - 0.7, send=0.5)
    for k in range(16):
        mx.add(tom(110, 80, 0.25), tc + 2.0 + k * 0.1, 0.18 + k * 0.025, send=0.2)
    mx.add(reverse_cymbal(1.4), tc + 4.0 - 1.4, 0.55, send=0.2)
    mx.add(brass(tri + [tri[0] + 12], 0.3), tc + 3.5, 0.4, send=0.3)

    # sidechain: the pads and the bass duck under every kick of the clubs' bars
    n = mx.n
    duck = np.ones(n)
    for b in range(n_clubs):
        for k in range(4):
            i = int(((open_bars + b) * BAR + k * BEAT) * SR)
            m = min(n - i, int(0.3 * SR))
            if m > 0: duck[i:i + m] = np.minimum(duck[i:i + m], 1 - 0.6 * np.exp(-np.arange(m) / SR / 0.09))
    mx.dry += pads.dry * duck[:, None] + beds.dry * duck[:, None]
    mx.wet += pads.wet + pads.dry * 0.25

    ir = reverb_ir()
    wet = np.stack([fftconvolve(mx.wet[:, 0], ir[:, 0])[:n], fftconvolve(mx.wet[:, 1], ir[:, 1])[:n]], 1)
    out = mx.dry + wet * 0.5
    out = np.stack([hp(out[:, 0], 28), hp(out[:, 1], 28)], 1)
    end = int(total * SR)
    fade = int(0.05 * SR)
    out = out[:end]
    out[-fade:] *= np.linspace(1, 0, fade)[:, None]
    # as loud as the other intros' music (about -16 dB on average), the peaks rounded off under -1 dB
    out = out / np.max(np.abs(out))
    out *= 10 ** (-15.5 / 20) / np.sqrt(np.mean(out ** 2))
    a = np.abs(out)
    out = np.where(a < 0.8, out, np.sign(out) * (0.8 + 0.09 * np.tanh((a - 0.8) / 0.09)))
    return out, total


def main():
    key, n, path = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    out, total = compose(key, n)
    pcm = (np.clip(out, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(key, n, 'clubs', f'{total:.1f}s ->', path)


if __name__ == '__main__':
    main()
