"""The studio line-ups' sound: python3 -I tools/studio_music.py MUSIC.m4a SFX_DIR

Made here from nothing but the project's own instruments (season_music.py: no samples, no one else's tune), 124 beats a
minute in F minor (Fm, Db, Ab, Eb), the away team a tone higher. The page (src/studio/studio.js) runs on the same beat,
so this one piece fits any formation: each team 45 beats from its start (beat 0 for the home side, 46 for the away),
- a riser as the side panel comes in, into the title card's hit at beat 2 (a boom, a crash, the brass, bells climbing);
- a light beat under the title card, the full beat from beat 8 under the positions' pages;
- the substitutes (from beat 35, at the latest) over a breakdown: a pad and a plucked arpeggio, a roll back in;
- out at beat 43: into the away side with a riser and a roll; after the away side a last hit ringing out to beat 93.

The page plays its own moments over it (SFX_DIR, studio-*.ogg): the family's swish as each page comes (their own pick,
score-swish.ogg, copied), a brass stab and a pluck a player in each team's key, a whoosh as the panel comes in, as the
shirt turns and as the side goes out.
"""
import os
import shutil
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy.signal import fftconvolve

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from season_music import (SR, NOTE, Mix, chord, kick, clap, hat, boom, supersaw, pluck, bass, brass, bell, riser,  # noqa: E402
                          reverse_cymbal, reverb_ir, hp, bp, RNG)

BEAT = 60 / 124
BAR = BEAT * 4
AWAY, END = 46, 93
PROG = [('F', 'm'), ('Db', ''), ('Ab', ''), ('Eb', '')]


def whoosh(dur, up=True):
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


def B(n):
    return n * BEAT


def block(mx, pads, beds, b0, shift, last):
    """one team's 45 beats from beat b0, a tone higher for the away side"""
    tri = [m + shift for m in chord('F', 'm', 4)]
    t0, title = B(b0), B(b0 + 2)
    # in: a riser into the title's hit
    mx.add(riser(B(2)), t0, 0.3, send=0.3)
    mx.add(reverse_cymbal(B(2) - 0.05), t0 + 0.05, 0.45, send=0.2)
    mx.add(boom(0.9), t0 + 0.3, 0.45, send=0.25)
    # the title card: a hit with bells climbing
    mx.add(boom(1.4), title, 0.95, send=0.3)
    mx.add(kick(1.4), title, 0.9)
    mx.add(crash(1.6), title, 0.65, send=0.3)
    mx.add(brass(tri + [tri[0] + 12, tri[0] - 12], 0.9), title + 0.02, 0.7, send=0.35)
    for k, m in enumerate([tri[0] + 12, tri[1] + 12, tri[2] + 12, tri[0] + 24]):
        mx.add(bell(m, 1.1), title + 0.25 + k * 0.11, 0.25, pan=(k - 1.5) * 0.35, send=0.6)

    def beat(b_from, b_to, busy, kick_on=True, claps=True, cutoff=None):
        """bars from the title on: the chord by the bar, a kick each beat, claps on two and four, hats, the bass"""
        for b in range(b_from, b_to):
            t = B(b0 + b)
            rel = b - 2                       # beats since the title
            bar = rel // 4
            r, q = PROG[bar % 4]
            root = 12 * 2 + NOTE[r] + shift
            if kick_on: mx.add(kick(), t, 0.9 if busy else 0.75)
            if claps and rel % 2 == 1: mx.add(clap(), t, 0.5 if busy else 0.35, send=0.25)
            steps = 4 if busy else 2
            for s in range(steps):
                mx.add(hat(open_=(s == steps // 2 and busy)), t + s * BEAT / steps, 0.15 if s % 2 else 0.1, pan=0.25)
            for s in range(2):
                beds.add(bass(root + 12 + (12 if (rel % 4 == 3 and s == 1) else 0), BEAT / 2 * 0.9), t + s * BEAT / 2, 0.75 if busy else 0.6)
            if rel % 4 == 0 or b == b_from:
                n = min(4 - rel % 4, b_to - b)
                notes = [m + shift for m in chord(r, q, 4)]
                pads.add(supersaw(notes + [notes[0] + 12], B(n), cutoff=cutoff or (2100 if busy else 1300)), t, 0.6)
    beat(2, 8, busy=False)
    beat(8, 35, busy=True)
    # the substitutes: a breakdown, a pad and a plucked arpeggio, the kick back for the last bar with a roll
    for b in range(35, 43):
        t = B(b0 + b)
        rel = b - 2
        r, q = PROG[(rel // 4) % 4]
        notes = [m + shift for m in chord(r, q, 5)]
        if (rel % 4 == 0) or b == 35:
            n = min(4 - rel % 4, 43 - b)
            pads.add(supersaw([m - 12 for m in notes] + [notes[0]], B(n), cutoff=900, a=0.2), t, 0.55)
        for s in range(2):
            mx.add(pluck(notes[(b * 2 + s) % 3] + (12 if s else 0), 0.2), t + s * BEAT / 2, 0.16, pan=0.35 if s else -0.35, send=0.4)
        if b >= 39: mx.add(kick(0.8), t, 0.55)
    for k in range(16):
        mx.add(clap(), B(b0 + 41) + k * BEAT / 8, 0.1 + k * 0.025, send=0.2)
    if not last:
        # out, into the away side: a riser and a boom as its panel comes
        mx.add(riser(B(3)), B(b0 + 43), 0.32, send=0.3)
        mx.add(kick(1.2), B(b0 + 43), 0.7)
        mx.add(crash(1.2), B(b0 + 43), 0.4, send=0.3)
    else:
        # the end: a last hit, the chord ringing out
        t = B(b0 + 43)
        mx.add(boom(2.0), t, 0.9, send=0.35)
        mx.add(kick(1.4), t, 0.85)
        mx.add(crash(2.4), t, 0.7, send=0.35)
        mx.add(brass(tri + [tri[0] + 12, tri[0] - 12], 2.2), t, 0.75, send=0.4)
        pads.add(supersaw(tri + [tri[0] + 12, tri[0] - 12], B(END) - t, cutoff=2600, a=0.05, r=0.9), t, 0.85)
        for k, m in enumerate([tri[0] + 12, tri[1] + 12, tri[2] + 12, tri[0] + 24, tri[1] + 24, tri[2] + 24]):
            mx.add(bell(m, 1.6), t + 0.4 + k * 0.12, 0.25, pan=(k / 5) * 1.2 - 0.6, send=0.6)


def master(mx, pads, beds, length):
    n = mx.n
    duck = np.ones(n)
    for t in np.arange(0, length, BEAT):
        i = int(t * SR); m = min(n - i, int(0.26 * SR))
        if m > 0: duck[i:i + m] = np.minimum(duck[i:i + m], 1 - 0.5 * np.exp(-np.arange(m) / SR / 0.09))
    mx.dry += pads.dry * duck[:, None] + beds.dry * duck[:, None]
    mx.wet += pads.wet + pads.dry * 0.25
    ir = reverb_ir(2.0)
    wet = np.stack([fftconvolve(mx.wet[:, 0], ir[:, 0])[:n], fftconvolve(mx.wet[:, 1], ir[:, 1])[:n]], 1)
    out = mx.dry + wet * 0.5
    out = np.stack([hp(out[:, 0], 28), hp(out[:, 1], 28)], 1)
    out = out[:int(length * SR)]
    fade = int(1.2 * SR)
    out[-fade:] *= np.linspace(1, 0, fade)[:, None] ** 1.5
    out = out / np.max(np.abs(out))
    out *= 10 ** (-16.0 / 20) / np.sqrt(np.mean(out ** 2))
    a = np.abs(out)
    return np.where(a < 0.8, out, np.sign(out) * (0.8 + 0.09 * np.tanh((a - 0.8) / 0.09)))


def music():
    length = B(END)
    mx, pads, beds = Mix(length), Mix(length), Mix(length)
    block(mx, pads, beds, 0, 0, False)
    block(mx, pads, beds, AWAY, 2, True)
    return master(mx, pads, beds, length)


def one(sound, length=None, rev=0.0):
    """a short sound alone, as a stereo clip with a touch of room, peak at -3 dB"""
    n = len(sound) if length is None else int(length * SR)
    mx = Mix(n / SR + 0.6)
    mx.add(sound, 0.0, 1.0, send=rev)
    out = mx.dry.copy()
    if rev > 0:
        ir = reverb_ir(1.2)
        wet = np.stack([fftconvolve(mx.wet[:, 0], ir[:, 0])[:mx.n], fftconvolve(mx.wet[:, 1], ir[:, 1])[:mx.n]], 1)
        out = out + wet * 0.5
    end = len(out)
    while end > SR // 10 and np.max(np.abs(out[end - SR // 20:end])) < 1e-4: end -= SR // 20
    out = out[:end]
    tail = min(len(out), int(0.03 * SR))
    out[-tail:] *= np.linspace(1, 0, tail)[:, None]
    return out / max(1e-9, np.max(np.abs(out))) * 10 ** (-3 / 20)


def write_wav(path, out):
    pcm = (np.clip(out, -1, 1) * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def encode(wav, path, args):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav] + args + [path], check=True, stdin=subprocess.DEVNULL)


def main():
    m4a, sfx = sys.argv[1], sys.argv[2]
    os.makedirs(sfx, exist_ok=True)
    tmp = tempfile.mkdtemp()
    try:
        wav = os.path.join(tmp, 'music.wav')
        write_wav(wav, music())
        encode(wav, m4a, ['-c:a', 'aac', '-b:a', '160k', '-movflags', '+faststart'])
        sounds = {
            'studio-in': one(whoosh(0.5, True), rev=0.2),
            'studio-whoosh': one(whoosh(1.1, True), rev=0.25),
            'studio-out': one(whoosh(0.7, False), rev=0.2),
        }
        for key, shift in (('h', 0), ('a', 2)):
            tri = [m + shift for m in chord('F', 'm', 4)]
            sounds['studio-stab-' + key] = one(brass([tri[0], tri[0] + 7, tri[0] + 12], 0.24), rev=0.3)
            tri5 = [m + shift for m in chord('F', 'm', 5)]
            for k in range(6):
                sounds['studio-pluck-%s%d' % (key, k)] = one(pluck(tri5[k % 3] + (12 if k >= 3 else 0), 0.25), rev=0.35)
        for name, out in sounds.items():
            w = os.path.join(tmp, name + '.wav')
            write_wav(w, out)
            encode(w, os.path.join(sfx, name + '.ogg'), ['-c:a', 'libvorbis', '-q:a', '4'])
        # the family's swish as each page comes: their own pick, as the scoreboard has it
        shutil.copyfile(os.path.join(sfx, 'score-swish.ogg'), os.path.join(sfx, 'studio-swish.ogg'))
        print('music', f'{B(END):.1f}s ->', m4a, '; sounds:', ', '.join(sorted(sounds)) + ', studio-swish')
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == '__main__':
    main()
