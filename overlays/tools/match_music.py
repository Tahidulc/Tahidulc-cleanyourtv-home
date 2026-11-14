"""The match intro's music, made here from nothing (no samples, no one else's tune): python3 match_music.py OUT.wav [ucl]
(ucl: the Champions League style's, 38 s: a choir's anthem of our own, not the competition's).

48 seconds at 120 beats a minute, with src/match/match.js's cuts on its beats: a dark swell under the comet, a boom into
the tunnel, the big hit on the Whitebrook Road lockup (4 s), the driving beat through each club's part (7.5 s and 17 s:
four on the floor with a pushed kick, claps, an 808 bass, brass stabs on every cut), quick stabs under the other clubs
(26.5 s), the drums dropping to half time for the two clubs face to face (34 s), a roll into the end (43 s) and the last
chord ringing out by 48 s. E minor (Em, C, G, D). Uses season_music.py's instruments.
"""
import os
import sys
import wave

import numpy as np
from scipy.signal import fftconvolve

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from season_music import (SR, BEAT, BAR, NOTE, Mix, chord, kick, clap, hat, tom, boom, supersaw, pluck, bass, brass, bell,  # noqa: E402
                          riser, reverse_cymbal, reverb_ir, hp, lp, bp, RNG)

LEN = 48.0
PROG = [('E', 'm'), ('C', ''), ('G', ''), ('D', '')]
# the cuts (src/match/match.js plan), and the ones that open a part
CUTS = [3.0, 4.0, 7.5, 9.0, 12.0, 13.5, 15.0, 17.0, 18.5, 21.5, 23.0, 24.5, 26.5, 27.0, 27.5, 28.0, 28.5, 29.0, 29.5, 30.0,
        32.0, 34.0, 36.5, 38.5, 40.5, 41.75, 43.0]
BIG = [4.0, 7.5, 17.0, 26.5, 34.0, 43.0]


def whoosh(dur, up=True):
    """a rush of air: noise in three bands, crossfaded low to high (or high to low), swelling and fading"""
    n = int(dur * SR)
    k = np.linspace(0, 1, n)
    nz = RNG.standard_normal(n + 4096)
    bands = [bp(nz, 250, 900)[4096:], bp(nz, 900, 3200)[4096:], bp(nz, 3200, 11000)[4096:]]
    x = k if up else 1 - k
    w = [np.clip(1 - np.abs(x * 2 - c), 0, 1) for c in (0.0, 1.0, 2.0)]
    out = sum(bb * ww for bb, ww in zip(bands, w))
    return out * np.sin(np.pi * k) ** 1.5 * 0.7


def crash(dec=1.6):
    n = int(2.5 * SR); t = np.arange(n) / SR
    return hp(RNG.standard_normal(n), 4500) * np.exp(-t / dec) * 0.35


def stab(m):
    """a short brass hit an octave doubled"""
    return brass([m, m + 7, m + 12], 0.22)


def compose():
    mx, pads, beds = Mix(LEN), Mix(LEN), Mix(LEN)
    r0, q0 = PROG[0]
    em = chord(r0, q0, 4)
    # -- the comet: a dark swell, the riser, the comet's whoosh; a boom into the tunnel
    pads.add(supersaw(chord(r0, q0, 3) + [12 * 4 + NOTE[r0]], 3.0, cutoff=700, a=1.8, r=0.4), 0.0, 0.9)
    mx.add(riser(3.0), 0.0, 0.3, send=0.3)
    mx.add(whoosh(1.6, True), 0.9, 0.55, pan=-0.3, send=0.3)
    for i, m in enumerate([em[0] + 12, em[2] + 12, em[1] + 24]):
        mx.add(bell(m, 1.4), 0.4 + i * 0.6, 0.28, pan=(i - 1) * 0.5, send=0.6)
    mx.add(boom(1.0), 3.0, 0.8, send=0.25)
    mx.add(kick(1.2), 3.0, 0.8)
    # -- the tunnel: a roll and a reverse cymbal into the lockup
    for k in range(8):
        mx.add(tom(170 - k * 6, 110, 0.2), 3.0 + k * 0.125, 0.25 + k * 0.04, pan=(k % 2) * 0.4 - 0.2, send=0.2)
    mx.add(reverse_cymbal(1.0), 3.0, 0.7, send=0.2)
    mx.add(whoosh(0.9, True), 3.1, 0.5, send=0.2)
    # -- the lockup: the big hit, the brass held, bells sparkling, a fill into the first club
    mx.add(boom(1.6), 4.0, 1.0, send=0.3)
    mx.add(kick(1.4), 4.0, 0.95)
    mx.add(crash(1.8), 4.0, 0.8, pan=0.1, send=0.3)
    mx.add(brass(em + [em[0] + 12, em[0] - 12], 1.6), 4.0, 0.85, send=0.35)
    pads.add(supersaw(em + [em[0] + 12], 3.4, cutoff=2200, a=0.02, r=0.4), 4.0, 0.9)
    run = [em[0] + 12, em[1] + 12, em[2] + 12, em[0] + 24, em[1] + 24, em[2] + 24]
    for i, m in enumerate(run):
        mx.add(bell(m, 1.0), 4.3 + i * 0.12, 0.3, pan=(i / 5) * 1.2 - 0.6, send=0.6)
    for k in range(4):
        mx.add(kick(), 5.0 + k * 0.5, 0.6)
    mx.add(clap(), 5.5, 0.4, send=0.25); mx.add(clap(), 6.5, 0.4, send=0.25)
    for k in range(8):
        mx.add(clap(), 7.0 + k * 0.0625, 0.25 + k * 0.05, send=0.2)
    mx.add(reverse_cymbal(0.9), 7.5 - 0.9, 0.5, send=0.2)

    # -- the beat: from the first club to the face to face (7.5 s to 34 s), a chord a bar
    def groove(t0, t1, busy=1.0, half=False):
        b = 0
        t = t0
        while t < t1 - 1e-6:
            r, q = PROG[b % 4]
            tri = chord(r, q, 4)
            bar_end = min(t + BAR, t1)
            kicks = [0, 1.5, 2, 3.5] if not half else [0, 2.5]
            claps = [1, 3] if not half else [2]
            for k in kicks:
                if t + k * BEAT < bar_end: mx.add(kick(), t + k * BEAT, 0.95)
            for k in claps:
                if t + k * BEAT < bar_end: mx.add(clap(), t + k * BEAT, 0.55, send=0.25)
            for k in range(16 if busy else 8):
                at = t + k * BEAT / (4 if busy else 2)
                if at < bar_end: mx.add(hat(open_=k % 8 == 6), at, (0.2 if k % 2 else 0.12), pan=0.25)
            if busy and b % 2 == 1:   # a hat roll at the end of every other bar
                for k in range(6):
                    at = t + 3.5 * BEAT + k * BEAT / 12
                    if at < bar_end: mx.add(hat(), at, 0.1 + k * 0.02, pan=0.3)
            root = 12 * 2 + NOTE[r]
            for k in kicks:   # the 808 under the kicks
                if t + k * BEAT < bar_end: beds.add(bass(root + 12, 0.42), t + k * BEAT, 0.8)
            pads.add(supersaw(tri + [tri[0] + 12], min(BAR, bar_end - t), cutoff=2400), t, 0.7)
            if busy:
                lead = [tri[0] + 12, tri[2] + 12, tri[1] + 12, tri[0] + 24, tri[2] + 12, tri[1] + 12, tri[0] + 12, tri[2]]
                for k in range(8):
                    at = t + k * BEAT / 2
                    if at < bar_end: mx.add(pluck(lead[k]), at, 0.3, pan=0.35 if k % 2 else -0.35, send=0.35)
            b += 1
            t += BAR

    groove(7.5, 26.5)
    groove(26.5, 34.0, busy=1.0)
    groove(34.0, 43.0, busy=0, half=True)
    # every cut its hit: a stab (and on the big ones the boom and the crash)
    for c in CUTS:
        if c in (3.0, 4.0): continue
        bi = int(((c - 7.5) // BAR) % 4) if c >= 7.5 else 0
        r, q = PROG[bi]
        tri = chord(r, q, 4)
        if c in BIG:
            mx.add(boom(1.3), c, 0.85, send=0.3)
            mx.add(crash(1.4), c, 0.6, send=0.25)
            mx.add(brass(tri + [tri[0] + 12, tri[0] - 12], 0.5), c, 0.75, send=0.35)
            mx.add(whoosh(0.5, True), c - 0.5, 0.45, send=0.2)
        else:
            mx.add(stab(tri[0]), c, 0.5 if c < 26.5 or c >= 30 else 0.42, send=0.3)
            mx.add(whoosh(0.3, False), c - 0.15, 0.25, pan=0.3)
    # the other clubs (26.5 to 30): a stab on every crest, rising
    for i, c in enumerate([26.5, 27.0, 27.5, 28.0, 28.5, 29.0, 29.5]):
        mx.add(bell(chord('E', 'm', 5)[i % 3] + 12 * (i // 3), 0.5), c, 0.3, send=0.5)
    # the face to face: big toms on the half-time beat, a long build into the end
    for c in (34.0, 36.5, 38.5):
        for k, f in enumerate([120, 100]):
            mx.add(tom(f, f * 0.6, 0.5), c + k * 0.25, 0.5, send=0.25)
    for k in range(16):
        mx.add(tom(160 - k * 3, 100, 0.18), 42.0 + k * 0.0625, 0.2 + k * 0.03, pan=(k % 2) * 0.5 - 0.25, send=0.2)
    mx.add(riser(2.0), 41.0, 0.3, send=0.3)
    mx.add(reverse_cymbal(1.2), 43.0 - 1.2, 0.6, send=0.2)
    # -- the end: the last hit, the chord held, a run up, ringing out
    mx.add(boom(2.0), 43.0, 1.0, send=0.35)
    mx.add(kick(1.4), 43.0, 0.95)
    mx.add(crash(2.4), 43.0, 0.85, send=0.35)
    mx.add(brass(em + [em[0] + 12, em[0] - 12, em[2] + 12], 2.4), 43.0, 0.85, send=0.4)
    pads.add(supersaw(em + [em[0] + 12, em[0] - 12], 4.6, cutoff=3000, a=0.02, r=0.9), 43.0, 0.95)
    for i, m in enumerate([em[0] + 12, em[1] + 12, em[2] + 12, em[0] + 24, em[1] + 24, em[2] + 24, em[0] + 36]):
        mx.add(bell(m, 1.4), 43.4 + i * 0.1, 0.28, pan=(i / 6) * 1.2 - 0.6, send=0.6)
    mx.add(brass(em + [em[0] + 12], 0.35), 45.0, 0.5, send=0.4)
    mx.add(boom(1.4), 45.0, 0.6, send=0.3)
    mx.add(bell(em[0] + 24, 2.5), 45.5, 0.3, send=0.7)

    # sidechain: pads and bass duck under the kicks of the beat
    n = mx.n
    duck = np.ones(n)
    for t in np.arange(7.5, 43.0, BEAT):
        i = int(t * SR); m = min(n - i, int(0.28 * SR))
        if m > 0: duck[i:i + m] = np.minimum(duck[i:i + m], 1 - 0.55 * np.exp(-np.arange(m) / SR / 0.09))
    mx.dry += pads.dry * duck[:, None] + beds.dry * duck[:, None]
    mx.wet += pads.wet + pads.dry * 0.25
    ir = reverb_ir(2.2)
    wet = np.stack([fftconvolve(mx.wet[:, 0], ir[:, 0])[:n], fftconvolve(mx.wet[:, 1], ir[:, 1])[:n]], 1)
    out = mx.dry + wet * 0.5
    out = np.stack([hp(out[:, 0], 28), hp(out[:, 1], 28)], 1)
    end = int(LEN * SR)
    out = out[:end]
    fade = int(1.2 * SR)
    out[-fade:] *= np.linspace(1, 0, fade)[:, None] ** 1.5
    out = out / np.max(np.abs(out))
    out *= 10 ** (-15.5 / 20) / np.sqrt(np.mean(out ** 2))
    a = np.abs(out)
    out = np.where(a < 0.8, out, np.sign(out) * (0.8 + 0.09 * np.tanh((a - 0.8) / 0.09)))
    return out


# ---- the Champions League's style: a choir's anthem of our own (38 s) ------------------------------------------------
LEN_UCL = 38.0
UCL_CUTS = [4.0, 8.0, 11.0, 13.0, 16.0, 18.0, 21.0, 24.5, 27.0, 30.0]
UCL_BIG = [4.0, 8.0, 11.0, 21.0, 27.0, 30.0]
UPROG = [('D', 'm'), ('Bb', ''), ('F', ''), ('C', '')]


def choir(notes, dur, a=0.6, r=0.8, vowel='ah'):
    """voices singing 'ah': detuned saws with a slow vibrato through the vowel's three formants"""
    from season_music import hz, saw
    n = int((dur + r) * SR); t = np.arange(n) / SR
    src = np.zeros(n)
    for m in notes:
        for det in (-0.08, 0.0, 0.09):
            f = hz(m + det) * (1 + 0.006 * np.sin(2 * np.pi * (4.8 + det) * t + m))
            ph = np.cumsum(f) / SR
            src += 2 * (ph % 1) - 1
    src /= max(1, len(notes) * 3)
    fm = {'ah': [(700, 1.0), (1150, 0.6), (2600, 0.25)], 'oh': [(450, 1.0), (800, 0.6), (2830, 0.15)]}[vowel]
    out = sum(bp(src, f * 0.86, f * 1.16) * g for f, g in fm)
    e = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    e[:na] = np.linspace(0, 1, na) ** 1.6
    e[-nr:] *= np.linspace(1, 0, nr)
    return out * e * 2.4


def timpani(f0=73.4, dec=0.9):
    n = int(2.0 * SR); t = np.arange(n) / SR
    body = np.sin(2 * np.pi * f0 * t) * np.exp(-t / dec) + 0.5 * np.sin(2 * np.pi * f0 * 1.5 * t) * np.exp(-t / (dec * 0.5))
    hit = lp(RNG.standard_normal(n), 900) * np.exp(-t / 0.03) * 0.6
    return np.tanh((body + hit) * 1.2) * 0.9


def strings(notes, dur, a=0.25, r=0.5, cutoff=3200):
    return supersaw(notes, dur, cutoff=cutoff, a=a, r=r)


def compose_ucl():
    mx, pads, beds = Mix(LEN_UCL), Mix(LEN_UCL), Mix(LEN_UCL)
    dm = chord('D', 'm', 4)
    # -- the open: the choir rising out of nothing, bells turning over it
    pads.add(choir(chord('D', 'm', 3) + [dm[0] + 12], 4.0, a=2.5, r=0.6), 0.0, 0.8, send=0.6)
    for i, m in enumerate([dm[0] + 24, dm[2] + 12, dm[1] + 24, dm[0] + 24, dm[2] + 24, dm[1] + 24]):
        mx.add(bell(m, 1.6), 0.5 + i * 0.5, 0.22, pan=(i % 2) * 0.6 - 0.3, send=0.7)
    mx.add(riser(4.0), 0.0, 0.22, send=0.4)
    # -- the star: the hit, the choir's chords, the rush into the star
    for c in (4.0,):
        mx.add(boom(1.4), c, 0.8, send=0.35); mx.add(timpani(73.4, 1.2), c, 0.9, send=0.3)
    for k, (r, q) in enumerate(UPROG[:2]):
        tri = chord(r, q, 4)
        pads.add(choir(tri + [tri[0] + 12, tri[0] - 12], 2.0, a=0.3, r=0.4), 4.0 + k * 2, 0.95, send=0.6)
        beds.add(strings(tri, 2.0, a=0.15), 4.0 + k * 2, 0.5)
    for k in range(8):
        mx.add(timpani(73.4 + k * 3, 0.25), 6.0 + k * 0.25, 0.25 + k * 0.05, send=0.2)
    mx.add(reverse_cymbal(1.2), 8.0 - 1.2, 0.6, send=0.2)
    # -- the hall of arches: the big chord, low strings pulsing
    mx.add(boom(1.8), 8.0, 1.0, send=0.4); mx.add(timpani(73.4, 1.6), 8.0, 1.0, send=0.3)
    pads.add(choir(dm + [dm[0] + 12, dm[0] - 12], 3.0, a=0.05, r=0.6), 8.0, 1.0, send=0.6)
    mx.add(brass(dm + [dm[0] + 12], 1.2), 8.0, 0.7, send=0.4)
    for k in range(12):
        beds.add(bass(12 * 2 + NOTE['D'] + 12, 0.2), 8.0 + k * 0.25, 0.6)
    # -- the clubs: a march of timpani and strings under the choir, a hit on every cut
    t = 11.0
    b = 0
    while t < 27.0 - 1e-6:
        r, q = UPROG[b % 4]
        tri = chord(r, q, 4)
        end = min(t + BAR, 27.0)
        for k in (0, 2):
            if t + k * BEAT < end: mx.add(timpani(hz_of(r) / 2, 0.6), t + k * BEAT, 0.75, send=0.25)
        for k in (1, 3):
            if t + k * BEAT < end: mx.add(clap(), t + k * BEAT, 0.3, send=0.35)
        for k in range(8):
            at = t + k * BEAT / 2
            if at < end: mx.add(pluck(tri[k % 3] + 12), at, 0.28, pan=0.3 if k % 2 else -0.3, send=0.35)
        pads.add(choir(tri + [tri[0] + 12], min(BAR, end - t), a=0.12, r=0.35), t, 0.8, send=0.55)
        beds.add(strings(tri + [tri[0] - 12], min(BAR, end - t), a=0.08), t, 0.45)
        root = 12 * 2 + NOTE[r] + 12
        for k in range(4):
            if t + k * BEAT < end: beds.add(bass(root, 0.42), t + k * BEAT, 0.6)
        b += 1
        t += BAR
    for c in UCL_CUTS:
        if c in (4.0, 8.0) or c >= 27.0: continue
        if c in UCL_BIG:
            mx.add(boom(1.2), c, 0.75, send=0.3); mx.add(brass(dm + [dm[0] + 12], 0.6), c, 0.6, send=0.4)
        else:
            mx.add(brass([dm[0], dm[2], dm[0] + 12], 0.3), c, 0.45, send=0.35)
        mx.add(timpani(73.4, 0.8), c, 0.8, send=0.3)
    # -- the cave: a low drone, a heart beating, the rise
    pads.add(choir([NOTE['D'] + 12 * 3, NOTE['A'] + 12 * 3], 3.0, a=0.8, r=0.5, vowel='oh'), 27.0, 0.7, send=0.5)
    for k in range(6):
        mx.add(kick(0.8), 27.0 + k * 0.5, 0.35 + k * 0.08)
    mx.add(riser(3.0), 27.0, 0.3, send=0.4)
    mx.add(reverse_cymbal(1.3), 30.0 - 1.3, 0.6, send=0.25)
    # -- the trophy: the whole choir and the brass, the chords turning to the end, the ending's own hit
    mx.add(boom(2.2), 30.0, 1.0, send=0.4); mx.add(timpani(73.4, 2.0), 30.0, 1.0, send=0.35); mx.add(crash(2.4), 30.0, 0.7, send=0.35)
    ends = [('D', 'm', 30.0, 2.0), ('Bb', '', 32.0, 1.0), ('C', '', 33.0, 1.5), ('D', 'm', 34.5, 3.5)]
    for r, q, at, dur in ends:
        tri = chord(r, q, 4)
        pads.add(choir(tri + [tri[0] + 12, tri[0] - 12], dur, a=0.08, r=0.6 if at < 34 else 1.4), at, 1.0, send=0.6)
        mx.add(brass(tri + [tri[0] + 12], min(dur, 1.4)), at, 0.6, send=0.45)
        beds.add(strings(tri + [tri[0] - 12], dur, a=0.05, r=0.8), at, 0.5)
    mx.add(boom(1.4), 33.0, 0.6, send=0.3); mx.add(timpani(73.4, 1.2), 33.0, 0.7, send=0.3)
    for i, m in enumerate([dm[0] + 12, dm[1] + 12, dm[2] + 12, dm[0] + 24, dm[1] + 24, dm[2] + 24]):
        mx.add(bell(m, 1.4), 33.1 + i * 0.12, 0.26, pan=(i / 5) * 1.2 - 0.6, send=0.6)
    mx.add(timpani(73.4, 1.8), 34.5, 0.9, send=0.35); mx.add(boom(2.0), 34.5, 0.7, send=0.35)
    n = mx.n
    mx.dry += pads.dry + beds.dry * 0.9
    mx.wet += pads.wet + pads.dry * 0.3 + beds.dry * 0.15
    ir = reverb_ir(2.8)
    wet = np.stack([fftconvolve(mx.wet[:, 0], ir[:, 0])[:n], fftconvolve(mx.wet[:, 1], ir[:, 1])[:n]], 1)
    out = mx.dry + wet * 0.6
    out = np.stack([hp(out[:, 0], 28), hp(out[:, 1], 28)], 1)
    out = out[:int(LEN_UCL * SR)]
    fade = int(1.6 * SR)
    out[-fade:] *= np.linspace(1, 0, fade)[:, None] ** 1.5
    out = out / np.max(np.abs(out))
    out *= 10 ** (-15.5 / 20) / np.sqrt(np.mean(out ** 2))
    a = np.abs(out)
    out = np.where(a < 0.8, out, np.sign(out) * (0.8 + 0.09 * np.tanh((a - 0.8) / 0.09)))
    return out


def hz_of(name):
    from season_music import hz
    return hz(12 * 3 + NOTE[name])


def main():
    path = sys.argv[1]
    out = compose_ucl() if len(sys.argv) > 2 and sys.argv[2] == 'ucl' else compose()
    pcm = (np.clip(out, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(f'{len(out) / SR:.1f}s ->', path)


if __name__ == '__main__':
    main()
