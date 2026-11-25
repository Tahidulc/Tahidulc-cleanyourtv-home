#!/usr/bin/env python3
"""Goal sounds made for the new goal animations (GoalReveal: Club animations and Generic), from numpy maths only (no
samples, nobody's audio), with goal_synth.py's parts.

    python3 -I reveal_goal_synth.py <outdir>

Each is 5.2 s, 48 kHz stereo, timed to GoalReveal's stages so its hits land on them: the sweep in from the scorers'
name (0-0.52 s), the crest turning to face us (0.52-1.1 s), the sweep off as the strip goes dark (1.25 s), the motif
coming in (1.6 s), GOAL!'s letters pulling together (2.85 s) and its burst (3.3 s), the sweep along again (3.95 s) and
off with the new score (4.45-4.9 s). Matched in loudness to the board's other goal sounds (their loudest second, as
heard: the deep bass left out of the measure), peaks under -1 dB. The app has them as goal-<key>.ogg (Sfx.GOALS).
"""
import os
import sys

import numpy as np
from scipy import signal
from scipy.io import wavfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from goal_synth import (SR, Mix, air_swipe, chord_ring, crack, crowd_roar, db, drum_kick, norm, pan_gains, ping,  # noqa: E402
                        shaped_noise, smooth, snare, sos, stab, sub_boom, sub_drop, tt, whoosh, horn)

# GoalReveal's timeline, in seconds
SWEEP_IN, CREST_EDGE, CREST_FACED, DARK_FROM = 0.0, 0.52, 1.10, 1.25
MOTIF_FROM, LETTERS_FROM, BURST, SWEEP2, OUT = 1.60, 2.85, 3.30, 3.95, 4.45

C4, D4, E4, F4, G4, A4, B4 = 261.63, 293.66, 329.63, 349.23, 392.0, 440.0, 493.88


# ---- parts of our own ------------------------------------------------------------------------------------------------

def coin(f=2637.0, d=1.0, seed=1):
    """A coin turning: a bright metallic ting whose shimmer quickens as it settles (the crest turning to face us)."""
    t = tt(d)
    rate = 9 + 30 * (t / d) ** 1.5
    trem = .55 + .45 * np.cos(2 * np.pi * np.cumsum(rate) / SR)
    v = sum(a * np.sin(2 * np.pi * f * m * t) * np.exp(-t / dd) for m, a, dd in ((1, 1, .45), (2.71, .45, .22), (5.1, .18, .12)))
    return norm(v * trem * (1 - np.exp(-t / .0015)))


def tick(f=1800.0, seed=2):
    t = tt(.25)
    return norm(np.sin(2 * np.pi * f * t) * np.exp(-t / .03) + .3 * crack(seed, .004, 2500, .25)[:len(t)])


def punch():
    """A punchy hit for GOAL!: kick, snare's crack and a short bright chord."""
    k = drum_kick()
    n = max(len(k), int(.9 * SR))
    out = np.zeros(n)
    out[:len(k)] += k
    sn = snare(31)
    out[:len(sn)] += .7 * sn
    return norm(out)


def brass(freqs, d, swell=0.0, seed=3):
    """A brass section: detuned saws through a low-pass that opens as it swells (swell: how long it builds, s)."""
    t = tt(d)
    out = np.zeros((2, len(t)))
    for k, f in enumerate(freqs):
        for c in range(2):
            vib = 1 + .005 * np.sin(2 * np.pi * 5.4 * t + k + c) * np.clip(t / .4, 0, 1)
            ph = 2 * np.pi * np.cumsum(f * vib * (1 + (c - .5) * .002)) / SR
            out[c] += sum(np.sin(h * ph) / h for h in range(1, 16))
    env = np.clip(t / max(.03, swell), 0, 1) ** (2.2 if swell > .1 else 1) * np.clip((d - t) / .25, 0, 1)
    # the filter opening with the swell, frame by frame
    y = np.zeros_like(out)
    hop = 480
    zi = [np.zeros((1, 2)) for _ in range(2)]
    for s in range(0, len(t), hop):
        e = env[min(s, len(env) - 1)]
        sf = sos('low', 500 + 4200 * e)
        for c in range(2):
            y[c, s:s + hop], zi[c] = signal.sosfilt(sf, out[c, s:s + hop], zi=zi[c])
    return norm(np.tanh(norm(y) * 1.8) * env[None, :])


def riser(d, f0, f1, seed=4):
    """An electronic riser: a saw sweeping up an octave or two with noise rising under it, cut dead at its end."""
    t = tt(d)
    p = t / d
    f = f0 * (f1 / f0) ** (p ** 1.6)
    out = np.zeros((2, len(t)))
    for c, det in ((0, -1), (1, 1)):
        ph = 2 * np.pi * np.cumsum(f * 2 ** (det * 8 / 1200)) / SR
        out[c] = sum(np.sin(h * ph) / h for h in range(1, 10))
    out = np.vstack([signal.sosfilt(sos('low', 6000), out[c]) for c in range(2)])
    nz = shaped_noise(d, lambda s: 600 * (12 ** np.clip(s / d, 0, 1)), .8, seed)
    env = p ** 2.2 * np.clip((1 - p) / .02, 0, 1)
    return norm((norm(out) + .7 * nz[None, :]) * env[None, :])


def zap(seed=5):
    """A short electronic zap (the crest turning): a sine falling fast with a little grit."""
    t = tt(.35)
    f = 300 + 2400 * np.exp(-t / .05)
    return norm(np.tanh(2 * np.sin(2 * np.pi * np.cumsum(f) / SR)) * np.exp(-t / .09))


def bell(f, d=3.0, decay=1.4):
    """A tubular bell: its inharmonic partials, the hum below."""
    t = tt(d)
    v = sum(a * np.sin(2 * np.pi * f * m * t) * np.exp(-t / (decay * dd)) for m, a, dd in (
        (0.5, .35, 1.2), (1, 1, 1), (2.0, .6, .7), (2.76, .45, .5), (5.40, .25, .3), (8.93, .12, .2)))
    return norm(v * (1 - np.exp(-t / .002)))


def chimes(notes, step=.06, d=1.6):
    """A run of small chimes."""
    out = np.zeros(int((step * len(notes) + d) * SR))
    for i, f in enumerate(notes):
        b = bell(f, d, .5)
        a = int(i * step * SR)
        out[a:a + len(b)] += b * (.7 + .3 * (i % 2))
    return norm(out)


def thunder(d=3.2, seed=6):
    """A thunder clap: a crack, then a long low rumble rolling round."""
    t = tt(d)
    r = np.random.default_rng(seed)
    out = []
    for c in range(2):
        rumble = signal.sosfilt(sos('low', 180, 4), r.standard_normal(len(t)))
        rumble = norm(rumble) * smooth(len(t), 6, .4, 1.3, seed + c) * np.exp(-t / 1.1) * (1 - np.exp(-t / .03))
        cl = signal.sosfilt(sos('band', [300, 4000]), r.standard_normal(len(t))) * np.exp(-t / .05)
        out.append(norm(rumble) + .6 * norm(cl))
    return norm(np.vstack(out))


def sparkle(d=1.6, n=26, seed=7):
    """A sparkle: high bright pings scattered, thinning out."""
    out = np.zeros((2, int(d * SR) + SR // 2))
    r = np.random.default_rng(seed)
    for i in range(n):
        at = d * (i / n) ** 1.4
        f = r.uniform(3000, 7000)
        v = ping(f, .35, seed + i)
        g = (1 - i / n) * r.uniform(.5, 1)
        gl, gr = pan_gains(r.uniform(-.8, .8))
        a = int(at * SR)
        out[0, a:a + len(v)] += v * g * gl
        out[1, a:a + len(v)] += v * g * gr
    return norm(out)


def murmur(d, seed=8):
    """A crowd's murmur rising: bands of noise and many voices, swelling toward its end."""
    t = tt(d)
    x = crowd_roar(seed, d, rise=d * .9, peak=d, fall=3.0)
    env = .2 + .8 * (t / d) ** 1.8
    return norm(x * env[None, :])


# ---- the options -----------------------------------------------------------------------------------------------------

def opt_ting():
    m = Mix()
    m.add(whoosh(.55, 500, 6500, 101, -.7, .5), SWEEP_IN, -9, send=.2)
    m.add(coin(2637, 1.0), CREST_EDGE + .02, -11, pan=.0, send=.5)
    m.add(coin(3520, .7), CREST_FACED - .04, -18, send=.6)
    m.add(whoosh(.45, 4000, 900, 102, .5, -.3), DARK_FROM, -17)
    m.add(tick(1760), MOTIF_FROM + .05, -19, pan=.4, send=.4)
    m.add(riser(.45, 400, 1600, 103), BURST - .45, -20)
    m.add(punch(), BURST, -4, send=.25)
    m.add(chord_ring([C4 * 2, E4 * 2, G4 * 2, C4 * 4], 2.0, .6, bell=.8), BURST, -13, send=.5)
    m.add(whoosh(.5, 600, 6000, 104, -.6, .6), SWEEP2, -13, send=.2)
    m.add(sub_drop(90, 42, .7, .25), OUT, -14)
    return m.done(1.2)


def opt_brass():
    m = Mix()
    m.add(air_swipe(.26, 5500, 15000, 111, -.6, .5), SWEEP_IN, -12, send=.2)
    m.add(brass([C4 / 2, G4 / 2], .8, .05), CREST_EDGE, -16, send=.35)
    m.add(brass([C4 / 2, G4 / 2, C4, E4], BURST - MOTIF_FROM + .05, BURST - MOTIF_FROM), MOTIF_FROM, -9, send=.35)
    m.add(brass([C4, E4, G4, C4 * 2], .9, .02), BURST, -4, send=.45)
    m.add(stab([C4, E4, G4, C4 * 2], 1.2), BURST, -12, send=.4)
    m.add(sub_drop(100, 44, .9, .3), BURST, -8)
    m.add(air_swipe(.24, 5500, 15000, 112, .5, -.6), SWEEP2, -13, send=.2)
    m.add(brass([G4 / 2, D4, G4], .7, .02), OUT, -17, send=.4)
    return m.done(1.3)


def opt_crowd():
    m = Mix()
    m.add(whoosh(.5, 400, 4000, 121, -.6, .4), SWEEP_IN, -14)
    m.add(murmur(BURST - .2, 122), .2, -10, send=.25)
    m.add(coin(2093, .8), CREST_EDGE + .02, -22, send=.5)
    m.add(crowd_roar(123, 2.0, .12, .35, 1.0), BURST - .05, -3, send=.3)
    m.add(sub_drop(95, 40, 1.0, .35), BURST, -8)
    m.add(crack(124, .015), BURST, -16)
    m.add(whoosh(.5, 3200, 700, 125, .3, -.6), SWEEP2, -16)
    return m.done(1.4)


def opt_riser():
    m = Mix()
    m.add(air_swipe(.28, 5000, 15000, 131, -.7, .5), SWEEP_IN, -10, send=.2)
    m.add(zap(132), CREST_EDGE, -15, pan=-.1, send=.3)
    m.add(riser(BURST - MOTIF_FROM, 110, 880, 133), MOTIF_FROM, -9, send=.2)
    m.add(sub_boom(44, 1.6, .8, 134), BURST, -3)
    m.add(stab([A4 / 2, C4, E4, A4], 1.1), BURST, -10, send=.4)
    m.add(crack(135, .01), BURST, -14)
    m.add(air_swipe(.26, 5000, 15000, 136, .5, -.7), SWEEP2, -13, send=.2)
    m.add(zap(137), OUT, -20, pan=.2)
    return m.done(1.0)


def opt_bells():
    m = Mix()
    m.add(chimes([E4 * 4, G4 * 4, B4 * 4, E4 * 8], .07, 1.2), SWEEP_IN, -14, send=.6)
    m.add(bell(B4 * 2, 1.8, .8), CREST_EDGE + .02, -13, pan=-.2, send=.6)
    m.add(bell(E4 * 2, 2.0, .9), MOTIF_FROM, -15, pan=.2, send=.6)
    for f, g in ((E4, -6), (G4, -9), (B4, -10), (E4 * 2, -11)):
        m.add(bell(f, 2.0, 1.1), BURST, g, send=.55)
    m.add(sub_drop(82, 41, .8, .3), BURST, -12)
    m.add(chimes([E4 * 8, B4 * 4, G4 * 4, E4 * 4], .06, 1.0), SWEEP2, -16, send=.6)
    return m.done(1.6)


def opt_thunder():
    m = Mix()
    m.add(sub_boom(40, 1.6, .9, 141), SWEEP_IN, -6)
    m.add(air_swipe(.26, 6000, 15000, 142, -.6, .5), SWEEP_IN, -14, send=.2)
    m.add(ping(2349, .9), CREST_EDGE + .05, -20, send=.6)
    m.add(thunder(1.6, 147), MOTIF_FROM, -17, send=.3)
    m.add(thunder(2.0, 143), BURST - .02, -3, send=.25)
    m.add(sub_boom(38, 1.8, 1.0, 144), BURST, -5)
    m.add(sparkle(1.3, 24, 145), BURST + .15, -14, send=.6)
    m.add(air_swipe(.24, 6000, 15000, 146, .5, -.6), SWEEP2, -15, send=.2)
    return m.done(1.3)


OPTIONS = [
    ('ting', 'Whoosh and ting', opt_ting),
    ('brass', 'Brass swell', opt_brass),
    ('crowd', 'Crowd rise', opt_crowd),
    ('riser', 'Riser and drop', opt_riser),
    ('bells', 'Bells', opt_bells),
    ('thunder', 'Thunder and sparkle', opt_thunder),
]

# the board's other goal sounds' loudest second, as heard (high-passed at 150 Hz), about this: -19 dB
TARGET_DB = -19.0


def loudest(x):
    hp = signal.sosfilt(sos('high', 150, 2), x, axis=1)
    e = np.convolve(np.mean(hp ** 2, axis=0), np.ones(SR) / SR, mode='valid')
    return 10 * np.log10(np.max(e))


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for key, name, fn in OPTIONS:
        x = fn()
        x = x * db(TARGET_DB - loudest(x))
        pk = np.max(np.abs(x))
        if pk > db(-1):
            x = x * db(-1) / pk
        wavfile.write(os.path.join(out, f'goal-{key}.wav'), SR, (x.T * 32767).astype(np.int16))
        print(key, name, 'peak %.2f dB, loudest second %.2f dB' % (20 * np.log10(np.max(np.abs(x))), loudest(x)))


if __name__ == '__main__':
    main()
