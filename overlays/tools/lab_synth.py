#!/usr/bin/env python3
"""The Scoreboard Lab moments' sounds, made from numpy maths only (no samples, nobody's audio).

    python3 -I lab_synth.py <outdir>

Short and quiet on purpose: each peaks about 3 dB under the line-ups' tab swish (swish-tab.ogg, -16 dBFS) and the apps
play them softer still (Sfx.QUIET), so they sit under the commentary and never get in the way. 48 kHz stereo:

    lab-net    the net shivering after a goal: a soft, fluttering hush, the ball's dull thud in it
    lab-burst  the scorer's crest popping with rings: a round low pop and an airy bloom rising out of it
    lab-roll   the score rolling up like an odometer: soft ticks quickening, a light clack as it lands
    lab-flip   the new digit flipping in: a card's flick and a small knock as it settles
    lab-card   the red card swinging up: a short swish and a soft tap as it stops
    lab-var    VAR checking: two soft, high blips with a faint sweep between, like a scan
    lab-swap   a substitution: two soft whooshes crossing, one each way
"""
import os
import subprocess
import sys

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 48000
# The peak each sound is mastered to, in dBFS (the tab swish peaks at -16).
PEAK_DB = -19.0


def tt(d):
    return np.arange(int(d * SR)) / SR


def sos(kind, f, order=2):
    return signal.butter(order, f, kind, fs=SR, output='sos')


def noise(n, seed):
    return np.random.default_rng(seed).standard_normal(n)


def band(x, lo, hi, order=2):
    return signal.sosfilt(sos('bandpass', [lo, hi], order), x)


def env_ad(n, attack, decay, curve=3.0):
    """Attack (s) to 1, then an exponential fall with time constant decay (s)."""
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1) ** 1.5
    d = np.exp(-np.clip(t - attack, 0, None) / max(decay, 1e-4))
    return a * d ** (curve / 3.0)


def fade(x, ms_in=2.0, ms_out=30.0):
    n = len(x)
    i, o = int(SR * ms_in / 1000), int(SR * ms_out / 1000)
    w = np.ones(n)
    if i > 0:
        w[:i] = np.linspace(0, 1, i)
    if o > 0:
        w[-o:] *= np.linspace(1, 0, o)
    return x * w


def place(total, part, at):
    out = np.zeros(int(total * SR))
    s = int(at * SR)
    e = min(len(out), s + len(part))
    out[s:e] += part[:e - s]
    return out


def stereo(mono, pan=0.0, width=0.0, seed=0):
    """Mono to stereo at pan (-1 left .. 1 right), with a little decorrelated width."""
    a = (pan + 1) * np.pi / 4
    l, r = np.cos(a) * mono, np.sin(a) * mono
    if width > 0:
        d = int(SR * 0.0007)
        r = (1 - width) * r + width * np.concatenate([np.zeros(d), r[:-d]])
    return np.stack([l, r], axis=1)


def moving_pan(mono, p0, p1):
    n = len(mono)
    p = np.linspace(p0, p1, n)
    a = (p + 1) * np.pi / 4
    return np.stack([np.cos(a) * mono, np.sin(a) * mono], axis=1)


def master(st, trim=0.0):
    """To PEAK_DB, less trim dB for the fuller ones (so each is about as loud as the others, all under the swish)."""
    st = np.asarray(st, dtype=np.float64)
    m = np.max(np.abs(st))
    if m > 0:
        st = st / m * (10 ** ((PEAK_DB - trim) / 20))
    return st


# ---- the sounds -----------------------------------------------------------------------------------------------------

def net():
    d = 0.95
    n = int(d * SR)
    t = tt(d)
    # The hush of the net: mid-high noise with a quick flutter that slows as it settles.
    hush = band(noise(n, 11), 1800, 7500)
    flutter = 0.55 + 0.45 * np.sin(2 * np.pi * (26 - 14 * t / d) * t) ** 2
    hush *= env_ad(n, 0.012, 0.26) * flutter
    # The ball's dull thud into it, low and soft.
    th = tt(0.18)
    thud = np.sin(2 * np.pi * (95 - 40 * th / 0.18) * th) * np.exp(-th / 0.05)
    thud = place(d, thud, 0.0) * 0.6
    mono = fade(hush + thud, 1.5, 120)
    return master(stereo(mono, 0.0, 0.35))


def burst():
    d = 1.1
    n = int(d * SR)
    # A round, low pop.
    tp = tt(0.22)
    pop = np.sin(2 * np.pi * (210 - 130 * (tp / 0.22) ** 0.6) * tp) * np.exp(-tp / 0.07)
    pop = place(d, pop, 0.0)
    # The rings blooming: airy noise opening upwards, and two soft partials ringing out.
    air = noise(n, 21)
    lo = signal.sosfilt(sos('highpass', 900), air)
    rise = np.clip(np.arange(n) / (0.35 * SR), 0, 1)
    air = band(lo, 1500, 9000) * (rise ** 2) * np.exp(-np.arange(n) / (0.45 * SR))
    tr = tt(d - 0.04)
    ring = (np.sin(2 * np.pi * 660 * tr) + 0.6 * np.sin(2 * np.pi * 990 * tr)) * np.exp(-tr / 0.32) * 0.18
    ring = place(d, ring, 0.04)
    mono = fade(pop * 0.9 + air * 0.55 + ring, 1.0, 160)
    return master(stereo(mono, 0.0, 0.5), 4.0)


def tick(seed, bright=1.0, d=0.03):
    n = int(d * SR)
    x = band(noise(n, seed), 2200, 9000 * bright) * np.exp(-np.arange(n) / (0.006 * SR))
    tb = tt(d)
    body = np.sin(2 * np.pi * 1450 * tb) * np.exp(-tb / 0.008) * 0.5
    return x + body


def roll():
    d = 0.9
    out = np.zeros(int(d * SR))
    # Ticks quickening as the digits run over, then one lighter clack as the new number lands.
    at, gap = 0.0, 0.11
    for k in range(7):
        out += place(d, tick(31 + k) * (0.55 + 0.05 * k), at)
        at += gap
        gap *= 0.82
    clack = tick(40, 0.8, 0.05) * 1.1
    tc = tt(0.12)
    knock = np.sin(2 * np.pi * 420 * tc) * np.exp(-tc / 0.03) * 0.5
    out += place(d, clack, at + 0.04) + place(d, knock, at + 0.04)
    mono = fade(out, 0.5, 80)
    return master(stereo(mono, 0.0, 0.25))


def flip():
    d = 0.32
    n = int(d * SR)
    # A card's flick: a short airy burst, then a small knock as the digit settles.
    fl = signal.sosfilt(sos('highpass', 2500), noise(int(0.05 * SR), 51)) * np.exp(-np.arange(int(0.05 * SR)) / (0.012 * SR))
    tk = tt(0.14)
    knock = np.sin(2 * np.pi * (520 - 160 * tk / 0.14) * tk) * np.exp(-tk / 0.035) * 0.8
    out = place(d, fl, 0.0) + place(d, knock, 0.07)
    mono = fade(out, 0.5, 60)
    return master(stereo(mono, 0.0, 0.2))


def card():
    d = 0.55
    n = int(d * SR)
    t = np.arange(n) / SR
    # A short swish rising as the card swings up, then a soft tap as it stops.
    sw_n = int(0.36 * SR)
    sw = noise(sw_n, 61)
    f = np.linspace(900, 3800, sw_n)
    # A swept band: filter in short blocks so its centre moves.
    out_sw = np.zeros(sw_n)
    blk = 960
    for i in range(0, sw_n, blk):
        c = f[min(i, sw_n - 1)]
        seg = sw[i:i + blk]
        out_sw[i:i + blk] = signal.sosfilt(sos('bandpass', [c * 0.6, c * 1.6]), seg)
    shape = np.sin(np.pi * np.clip(np.arange(sw_n) / sw_n, 0, 1)) ** 1.6
    out_sw *= shape
    ttap = tt(0.12)
    tap = np.sin(2 * np.pi * 300 * ttap) * np.exp(-ttap / 0.025) * 0.7 + band(noise(len(ttap), 62), 1500, 5000) * np.exp(-ttap / 0.01) * 0.3
    out = place(d, out_sw * 0.8, 0.0) + place(d, tap, 0.33)
    mono = fade(out, 2.0, 70)
    return master(moving_pan(mono, -0.15, 0.15), 3.5)


def var():
    d = 0.6
    out = np.zeros(int(d * SR))

    def blip(f, dur=0.075):
        tb = tt(dur)
        e = np.sin(np.pi * np.clip(tb / dur, 0, 1)) ** 2
        return (np.sin(2 * np.pi * f * tb) + 0.25 * np.sin(2 * np.pi * 2 * f * tb)) * e

    out += place(d, blip(1320) * 0.7, 0.0)
    out += place(d, blip(990) * 0.7, 0.2)
    # The faint sweep of the scan between them.
    sn = int(0.3 * SR)
    sweep = band(noise(sn, 71), 3000, 9000) * (np.sin(np.pi * np.arange(sn) / sn) ** 2) * 0.25
    out += place(d, sweep, 0.05)
    mono = fade(out, 1.0, 90)
    return master(moving_pan(mono, -0.35, 0.35), 4.5)


def whoosh(d, seed, lo, hi):
    n = int(d * SR)
    x = band(noise(n, seed), lo, hi)
    return x * np.sin(np.pi * np.arange(n) / n) ** 2


def swap():
    d = 0.75
    # One whoosh left to right (on), the other right to left (off), overlapping as they cross.
    a = moving_pan(whoosh(0.42, 81, 700, 3200), -0.6, 0.5)
    b = moving_pan(whoosh(0.42, 82, 900, 4200), 0.6, -0.5)
    out = np.zeros((int(d * SR), 2))
    s1, s2 = 0, int(0.24 * SR)
    out[s1:s1 + len(a)] += a
    out[s2:s2 + len(b)] += b * 0.9
    for ch in range(2):
        out[:, ch] = fade(out[:, ch], 2.0, 90)
    return master(out, 3.5)


SOUNDS = {
    'lab-net': net,
    'lab-burst': burst,
    'lab-roll': roll,
    'lab-flip': flip,
    'lab-card': card,
    'lab-var': var,
    'lab-swap': swap,
}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    os.makedirs(out, exist_ok=True)
    for name, make in SOUNDS.items():
        st = make()
        wav = os.path.join(out, name + '.wav')
        wavfile.write(wav, SR, (st * 32767).astype(np.int16))
        ogg = os.path.join(out, name + '.ogg')
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', ogg], check=True)
        os.remove(wav)
        print(name, '%.2f s' % (len(st) / SR))


if __name__ == '__main__':
    main()
