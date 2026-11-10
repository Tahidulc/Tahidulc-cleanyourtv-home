#!/usr/bin/env python3
"""Goal sound options for the scoreboard's goal animation, made from numpy maths only (no samples, nobody's audio).

    python3 -I goal_synth.py <outdir>

Each option is 5.2 s, 48 kHz stereo, timed to BoardBug's goal: the white hit at 0, the GOAL band sweeping in 0.18-0.68 s,
held, sweeping out 3.3-3.8 s, the new score's glow to 4.8 s.
"""
import os
import sys

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 48000
DUR = 5.2
N = int(SR * DUR)
BAND_IN, BAND_OUT = 0.18, 3.30


def db(x):
    return 10 ** (x / 20)


def norm(x):
    m = np.max(np.abs(x))
    return x / m if m > 0 else x


def tt(d):
    return np.arange(int(d * SR)) / SR


def sos(kind, f, order=2):
    return signal.butter(order, f, kind, fs=SR, output='sos')


def pan_gains(p):
    a = (np.asarray(p) + 1) * np.pi / 4
    return np.cos(a) * np.sqrt(2), np.sin(a) * np.sqrt(2)


def shaped_noise(d, fc, bw_oct, seed):
    n = int(d * SR)
    x = np.random.default_rng(seed).standard_normal(n)
    f, frames, z = signal.stft(x, SR, nperseg=1024, noverlap=768)
    c = np.maximum(fc(np.clip(frames, 0, d)), 20.0)
    mask = np.exp(-0.5 * ((np.log2(np.maximum(f, 10.0))[:, None] - np.log2(c)[None, :]) / bw_oct) ** 2)
    _, y = signal.istft(z * mask, SR, nperseg=1024, noverlap=768)
    y = y[:n]
    return norm(np.pad(y, (0, n - len(y))))


def smooth(n, per_sec, lo, hi, seed):
    from scipy.interpolate import CubicSpline
    r = np.random.default_rng(seed)
    k = int(n / SR * per_sec) + 4
    return np.clip(CubicSpline(np.linspace(0, n, k), r.uniform(lo, hi, k))(np.arange(n)), lo * .8, hi * 1.1)


class Mix:
    def __init__(self):
        self.out = np.zeros((2, N))
        self.send = np.zeros((2, N))

    def add(self, x, at, gain_db, pan=0.0, send=0.0):
        x = np.asarray(x, dtype=float)
        if x.ndim == 1:
            gl, gr = pan_gains(pan)
            x = np.vstack([x * gl, x * gr])
        i = int(round(at * SR))
        j = min(N, i + x.shape[1])
        seg = x[:, :j - i] * db(gain_db)
        self.out[:, i:j] += seg
        if send:
            self.send[:, i:j] += seg * send

    def done(self, t60=1.4):
        n = int(SR * t60)
        t = np.arange(n) / SR
        ir = np.random.default_rng(5).standard_normal((2, n)) * np.exp(-6.91 * t / t60)[None, :]
        ir = signal.sosfilt(sos('low', 6000), ir, axis=1)
        ir /= np.sqrt(np.sum(ir ** 2, axis=1, keepdims=True))
        wet = np.vstack([signal.fftconvolve(self.send[c], ir[c])[:N] for c in range(2)])
        x = self.out + .55 * wet
        fade = int(.25 * SR)
        x[:, -fade:] *= np.linspace(1, 0, fade)
        return x


# ---- parts -------------------------------------------------------------------------------------------------------

def whoosh(d, f0, f1, seed, pan0=0.0, pan1=0.0, shape=1.5):
    t = tt(d)
    p = t / d
    fc = lambda s: f0 * (f1 / f0) ** np.clip(s / d, 0, 1)
    env = np.sin(np.pi * p) ** shape
    x = np.vstack([shaped_noise(d, fc, .7, seed) * env, shaped_noise(d, fc, .7, seed + 3) * env])
    gl, gr = pan_gains(pan0 + (pan1 - pan0) * p)
    return norm(np.vstack([x[0] * gl, x[1] * gr]))


def sub_drop(f0=110, f1=42, d=1.2, decay=.45):
    t = tt(d)
    f = f1 + (f0 - f1) * np.exp(-t / .05)
    return norm(np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / decay) * (1 - np.exp(-t / .003)))


def crack(seed, decay=.02, hp=2500, d=.6):
    t = tt(d)
    return norm(signal.sosfilt(sos('high', hp), np.random.default_rng(seed).standard_normal(len(t))) * np.exp(-t / decay))


def chord_ring(freqs, d=3.0, decay=1.1, bell=0.0, seed=1):
    t = tt(d)
    out = np.zeros((2, len(t)))
    for k, fr in enumerate(freqs):
        for c, det in ((0, -1), (1, 1)):
            f2 = fr * 2 ** (det * 4 / 1200)
            v = np.sin(2 * np.pi * f2 * t) + .4 * np.sin(4 * np.pi * f2 * t + .5) + .15 * np.sin(6 * np.pi * f2 * t + 1)
            if bell:
                for ratio, amp in ((2.76, .35), (5.40, .18), (8.93, .09)):
                    v = v + bell * amp * np.sin(2 * np.pi * f2 * ratio * t) * np.exp(-t / .5)
            out[c] += v * np.exp(-t / (decay + .15 * k)) * (1 - np.exp(-t / .004))
    return norm(out)


def crowd_roar(seed, d=5.0, rise=.18, peak=.9, fall=2.8):
    t = tt(d)
    n = len(t)
    r = np.random.default_rng(seed)
    out = np.zeros((2, n))
    env = np.clip(t / rise, 0, 1) ** 1.5 * np.where(t < peak, 1, np.exp(-(t - peak) / fall))
    for c in range(2):
        for k, (lo, hi, g) in enumerate(((180, 600, .9), (500, 1600, 1.0), (1400, 4200, .55))):
            b = norm(signal.sosfilt(sos('band', [lo, hi]), r.standard_normal(n)))
            out[c] += b * g * smooth(n, 4, .65, 1.35, seed * 10 + c * 3 + k)
        # voices: many detuned "ah" vowels swelling with the roar
        for v in range(10):
            f = r.uniform(180, 420)
            vib = 1 + .02 * np.sin(2 * np.pi * r.uniform(4, 6) * t + r.uniform(0, 6))
            ph = 2 * np.pi * np.cumsum(f * vib) / SR
            saw = sum(np.sin(h * ph) / h for h in range(1, 8))
            form = signal.sosfilt(sos('band', [650, 1300]), saw)
            out[c] += .08 * norm(form) * smooth(n, 2, .3, 1, seed * 31 + v + c * 17)
    return norm(out * env[None, :])


def horn(freqs, d=1.9, seed=3):
    t = tt(d)
    out = np.zeros((2, len(t)))
    env = np.clip(t / .06, 0, 1) * np.clip((d - t) / .35, 0, 1)
    for k, f in enumerate(freqs):
        for c in range(2):
            vib = 1 + .004 * np.sin(2 * np.pi * 5.2 * t + k)
            ph = 2 * np.pi * np.cumsum(f * vib * (1 + (c - .5) * .0006)) / SR
            saw = sum(np.sin(h * ph) / h for h in range(1, 18))
            out[c] += saw
    out = np.vstack([signal.sosfilt(sos('low', 2400), out[c]) for c in range(2)])
    out = np.tanh(norm(out) * 2.2)
    return norm(out * env[None, :])


def drum_kick():
    t = tt(.6)
    f = 48 + 110 * np.exp(-t / .03)
    return norm(np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / .22) + .3 * crack(9, .004, 3000)[:len(t)])


def snare(seed):
    t = tt(.4)
    body = np.sin(2 * np.pi * 190 * t) * np.exp(-t / .06)
    nz = signal.sosfilt(sos('band', [1500, 8000]), np.random.default_rng(seed).standard_normal(len(t))) * np.exp(-t / .12)
    return norm(.6 * body + norm(nz))


def tom(f, seed):
    t = tt(.5)
    ff = f * (1 + .5 * np.exp(-t / .04))
    return norm(np.sin(2 * np.pi * np.cumsum(ff) / SR) * np.exp(-t / .2) + .15 * crack(seed, .01, 1500)[:len(t)])


def cymbal(seed, d=3.2):
    t = tt(d)
    r = np.random.default_rng(seed)
    out = []
    for c in range(2):
        nz = signal.sosfilt(sos('high', 4500), r.standard_normal(len(t)))
        metal = sum(np.sin(2 * np.pi * f * t + r.uniform(0, 6)) for f in (3110, 4320, 5870, 7230, 8910, 10400))
        out.append((norm(nz) + .25 * norm(metal)) * np.exp(-t / 1.1) * (1 - np.exp(-t / .002)))
    return norm(np.vstack(out))


def stab(freqs, d=1.6, cutoff0=6000, cutoff1=500, seed=2):
    t = tt(d)
    out = np.zeros((2, len(t)))
    for f in freqs:
        for c, det in ((0, -1), (1, 1)):
            for dt in (-7, 0, 7):
                ph = 2 * np.pi * f * 2 ** ((dt + det * 3) / 1200) * t
                out[c] += 2 * ((ph / (2 * np.pi)) % 1) - 1
    # a falling low-pass, frame by frame
    y = np.zeros_like(out)
    hop = 480
    zi = [signal.sosfilt_zi(sos('low', cutoff0)) * 0 for _ in range(2)]
    for s in range(0, len(t), hop):
        cut = cutoff1 + (cutoff0 - cutoff1) * np.exp(-(s / SR) / .18)
        sf = sos('low', cut)
        for c in range(2):
            y[c, s:s + hop], zi[c] = signal.sosfilt(sf, out[c, s:s + hop], zi=zi[c])
    env = (1 - np.exp(-t / .003)) * np.exp(-t / .55)
    return norm(y * env[None, :])


def arp(notes, step=.07, seed=4):
    d = step * len(notes) + .4
    t = tt(d)
    out = np.zeros(len(t))
    for i, f in enumerate(notes):
        tt0 = tt(.35)
        v = (np.sin(2 * np.pi * f * tt0) + .3 * np.sin(4 * np.pi * f * tt0)) * np.exp(-tt0 / .12) * (1 - np.exp(-tt0 / .002))
        a = int(i * step * SR)
        out[a:a + len(v)] += v
    return norm(out)


def pluck(f, d=1.2):
    t = tt(d)
    v = sum(np.sin(2 * np.pi * f * h * t) * np.exp(-t / (.45 / h)) / h for h in range(1, 6))
    return norm(v * (1 - np.exp(-t / .002)))


def swish_tab(seed=21):
    """As the line-ups' tab swish: a quick air sweep up then down, a little tonal edge."""
    return whoosh(.42, 700, 5200, seed, -.35, .35, shape=1.2)


# ---- the options ------------------------------------------------------------------------------------------------

C4, E4, G4, A4, B4 = 261.63, 329.63, 392.0, 440.0, 493.88


def opt_roar():
    m = Mix()
    m.add(crowd_roar(11), 0.02, -4, send=.3)
    m.add(sub_drop(95, 40, 1.0, .35), 0.0, -6)
    m.add(crack(12, .015), 0.0, -14)
    m.add(whoosh(.55, 400, 3800, 13, -.6, .4), BAND_IN - .05, -15)
    m.add(whoosh(.5, 3200, 600, 14, .3, -.6), BAND_OUT, -18)
    return m.done()


def opt_hit():
    m = Mix()
    m.add(sub_drop(120, 38, 1.4, .5), 0.0, -3)
    m.add(crack(21, .02), 0.0, -9)
    m.add(chord_ring([C4 * 2, E4 * 2, G4 * 2, C4 * 4], 3.6, 1.2, bell=.6), 0.0, -7, send=.5)
    m.add(whoosh(.5, 500, 6000, 22, -.7, .5), BAND_IN - .04, -12, send=.2)
    m.add(chord_ring([G4 * 4, C4 * 8], 1.5, .4, bell=1.0), BAND_IN + .48, -20, send=.6)
    m.add(whoosh(.5, 4000, 700, 23, .4, -.7), BAND_OUT, -14)
    m.add(chord_ring([E4 * 4, G4 * 4], 1.4, .45, bell=1), BAND_OUT + .5, -22, send=.6)
    return m.done()


def opt_horn():
    m = Mix()
    m.add(horn([C4 / 2, G4 / 2, E4], 1.9), 0.0, -5, send=.35)
    m.add(sub_drop(80, 36, 1.0, .4), 0.0, -9)
    m.add(crowd_roar(31, 5.0, .3, 1.4, 2.5), 0.25, -13, send=.2)
    m.add(whoosh(.5, 3500, 700, 33, .4, -.6), BAND_OUT, -18)
    return m.done()


def opt_swish():
    m = Mix()
    m.add(swish_tab(41), BAND_IN - .12, -6, send=.25)
    m.add(chord_ring([E4 * 2, G4 * 2, B4 * 2, E4 * 4], 2.6, .8, bell=1.0), BAND_IN + .3, -12, send=.6)
    m.add(sub_drop(90, 45, .7, .25), BAND_IN + .3, -14)
    m.add(swish_tab(42)[:, ::-1], BAND_OUT - .02, -10, send=.25)
    m.add(pluck(B4 * 2), BAND_OUT + .45, -20, send=.5)
    return m.done()


def opt_drums():
    m = Mix()
    for i, (f, at) in enumerate(((210, -0.0), (160, .07), (120, .14))):
        m.add(tom(f, 50 + i), at + .0, -9, pan=(-.4 + .4 * i), send=.2)
    m.add(drum_kick(), .22, -4)
    m.add(snare(55), .22, -8, send=.3)
    m.add(cymbal(56), .22, -12, send=.35)
    m.add(whoosh(.5, 600, 5000, 57, -.6, .5), BAND_IN, -18)
    m.add(drum_kick(), BAND_OUT + .02, -10)
    m.add(snare(58), BAND_OUT + .02, -14, send=.3)
    return m.done()


def opt_stab():
    m = Mix()
    m.add(stab([C4, E4, G4, C4 * 2]), 0.0, -6, send=.4)
    m.add(sub_drop(100, 45, .9, .3), 0.0, -7)
    m.add(arp([C4 * 2, E4 * 2, G4 * 2, C4 * 4, E4 * 4, G4 * 4]), BAND_IN + .1, -15, pan=.2, send=.5)
    m.add(stab([A4 / 2, C4, E4, A4], 1.0), BAND_OUT, -14, send=.4)
    m.add(whoosh(.45, 3500, 700, 63, .4, -.6), BAND_OUT, -18)
    return m.done()


# ---- in the style measured in the family's kick-off clip (its graphics: a quick bright swipe of air, 6-15 kHz, about
# 0.2 s, then a deep sub boom, 35-110 Hz, with a short rumble; no tune). Made new: none of the clip's sound is used.

def air_swipe(d=.24, lo=6000, hi=15000, seed=71, pan0=-.5, pan1=.5):
    t = tt(d)
    p = t / d
    n = np.random.default_rng(seed).standard_normal(len(t))
    y = signal.sosfilt(sos('band', [lo, hi], 4), n)
    env = np.clip(p / .25, 0, 1) ** 1.5 * np.clip((1 - p) / .75, 0, 1) ** 2
    y = norm(y) * env
    gl, gr = pan_gains(pan0 + (pan1 - pan0) * p)
    return norm(np.vstack([y * gl, y * gr]))


def sub_boom(f=46, d=1.4, rumble=.6, seed=72):
    t = tt(d)
    ff = f * (1 + 1.2 * np.exp(-t / .04))
    tone = np.sin(2 * np.pi * np.cumsum(ff) / SR) * np.exp(-t / .5) * (1 - np.exp(-t / .004))
    r = signal.sosfilt(sos('low', 110, 4), np.random.default_rng(seed).standard_normal(len(t)))
    r = norm(r) * np.exp(-t / rumble) * (1 - np.exp(-t / .05))
    click = signal.sosfilt(sos('band', [900, 3000]), np.random.default_rng(seed + 1).standard_normal(len(t))) * np.exp(-t / .006)
    # its harmonics too (90-300 Hz), so a TV's small speakers, which barely go below 100 Hz, still give the thump
    body = signal.sosfilt(sos('band', [90, 300], 2), np.tanh(3 * norm(tone)))
    return norm(norm(tone) + .45 * r + .55 * norm(body) + .12 * norm(click))


def ping(f, d=1.6, seed=73):
    t = tt(d)
    v = sum(a * np.sin(2 * np.pi * f * m * t) * np.exp(-t / dd) for m, a, dd in ((1, 1, .7), (2.0, .35, .4), (3.01, .15, .25), (4.2, .08, .15)))
    return norm(v * (1 - np.exp(-t / .002)))


def opt_boom():
    m = Mix()
    m.add(sub_boom(46, 1.4, .55), 0.0, -3)
    m.add(air_swipe(.22, 6000, 15000, 81, -.6, .4), BAND_IN - .06, -9, send=.15)
    m.add(air_swipe(.2, 6500, 15000, 82, .4, -.6), BAND_OUT - .04, -12, send=.15)
    m.add(sub_boom(52, 1.0, .35, 83), BAND_OUT + .16, -10)
    return m.done(1.0)


def opt_rumble():
    m = Mix()
    m.add(air_swipe(.28, 5500, 15000, 91, -.7, .3), 0.0, -8, send=.2)
    m.add(sub_boom(42, 2.2, 1.2, 92), .30, -3)
    m.add(air_swipe(.16, 8000, 15000, 93, .3, .6), .62, -16, send=.3)
    m.add(air_swipe(.24, 5500, 15000, 94, .5, -.7), BAND_OUT - .06, -11, send=.2)
    m.add(sub_boom(50, 1.2, .5, 95), BAND_OUT + .2, -11)
    return m.done(1.0)


def opt_sting():
    m = Mix()
    m.add(sub_boom(48, 1.3, .5, 101), 0.0, -4)
    m.add(air_swipe(.2, 6000, 15000, 102, -.5, .5), BAND_IN - .05, -11)
    m.add(ping(987.77), BAND_IN + .32, -12, pan=-.15, send=.55)
    m.add(ping(1318.5), BAND_IN + .47, -12, pan=.15, send=.55)
    m.add(air_swipe(.2, 6000, 15000, 103, .5, -.5), BAND_OUT - .04, -13)
    m.add(sub_boom(54, .9, .3, 104), BAND_OUT + .14, -12)
    return m.done(1.2)


OPTIONS = [
    ('A', 'roar', 'Stadium roar', opt_roar),
    ('B', 'hit', 'Broadcast hit', opt_hit),
    ('C', 'horn', 'Goal horn', opt_horn),
    ('D', 'swish', 'Swish and chime', opt_swish),
    ('E', 'drums', 'Drum hit', opt_drums),
    ('F', 'stab', 'Synth stab', opt_stab),
    ('G', 'boom', 'Broadcast boom', opt_boom),
    ('H', 'rumble', 'Swipe and rumble', opt_rumble),
    ('I', 'sting', 'Score sting', opt_sting),
]


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    mixes = {}
    for letter, key, name, fn in OPTIONS:
        x = fn()
        mixes[key] = x
    # all at a similar loudness (RMS over the first two seconds), peaks under -1 dB
    ref = None
    for letter, key, name, fn in OPTIONS:
        x = mixes[key]
        # as loud as each other as heard (the deep bass, which TV speakers hardly give, left out of the measure)
        heard = signal.sosfilt(sos('high', 150, 2), x[:, :2 * SR], axis=1)
        rms = np.sqrt(np.mean(heard ** 2))
        x = x * (0.10 / rms)
        pk = np.max(np.abs(x))
        if pk > db(-1):
            x = x * db(-1) / pk
        wavfile.write(os.path.join(out, f'goal-{key}.wav'), SR, (x.T * 32767).astype(np.int16))
        print(letter, key, name, 'peak %.2f dB' % (20 * np.log10(np.max(np.abs(x)))))


if __name__ == '__main__':
    main()
