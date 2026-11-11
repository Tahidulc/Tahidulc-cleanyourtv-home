"""The competition intros' sound, made here from nothing (no recordings): reveal_synth.py OUT_DIR
Writes reveal-full.wav (20 s: the competition's part, then Whitebrook Road Sports) and reveal-tail.wav (10 s: only the
Whitebrook Road part, after the Premier League's own video). Timed to src/reveal/reveal.js: the burst at 0, the outline
gathering to 4.0 (the logo lands), the name at 7.0-9.0, the flash to white at 9.95, the Whitebrook Road part from 11.4
(the wordmark lands 3.0 s in, MATCHDAY 3.0-3.8, the fixture 3.8), out over the last 0.8 s.
"""
import sys, os, wave
import numpy as np
from scipy import signal

SR = 48000
rng = np.random.default_rng(5)


def env_exp(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def place(buf, t, x, pan=0.0):
    i = int(t * SR)
    if i >= buf.shape[1] or i + x.shape[-1] <= 0:
        return
    x = x if x.ndim == 2 else np.vstack([x * np.sqrt(0.5 * (1 - pan)), x * np.sqrt(0.5 * (1 + pan))])
    j0 = max(0, -i)
    n = min(x.shape[1] - j0, buf.shape[1] - max(i, 0))
    buf[:, max(i, 0):max(i, 0) + n] += x[:, j0:j0 + n]


def boom(dur=1.8, f0=62, f1=30, amp=0.9):
    n = int(dur * SR); t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-t * 5)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) * env_exp(n, 0.45) * amp
    click = signal.lfilter(*signal.butter(2, 2500 / (SR / 2), 'low'), rng.standard_normal(n)) * env_exp(n, 0.012) * 0.6 * amp
    return np.tanh((x + click) * 1.6) / 1.2


def noise_band(n, lo, hi):
    b, a = signal.butter(2, [lo / (SR / 2), min(hi, SR / 2 - 100) / (SR / 2)], 'band')
    return signal.lfilter(b, a, rng.standard_normal(n))


def whoosh(dur, lo0, hi0, lo1, hi1, amp, shape='swell'):
    """filtered noise whose band moves from (lo0, hi0) to (lo1, hi1), in 40 ms steps"""
    n = int(dur * SR); out = np.zeros(n); step = int(0.04 * SR); win = np.hanning(step * 2)
    for k, i in enumerate(range(0, n, step)):
        u = i / n
        lo = lo0 * (lo1 / lo0) ** u; hi = hi0 * (hi1 / hi0) ** u
        seg = noise_band(step * 2, lo, hi) * win
        m = min(step * 2, n - i); out[i:i + m] += seg[:m]
    u = np.arange(n) / n
    e = np.sin(np.pi * u) ** 1.5 if shape == 'swell' else (u ** 2.2 if shape == 'rise' else (1 - u) ** 2.5)
    return out / (np.max(np.abs(out)) + 1e-9) * e * amp


def riser(dur, f0, f1, amp):
    n = int(dur * SR); t = np.arange(n) / SR; u = t / dur
    f = f0 * (f1 / f0) ** u
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = (np.sin(ph) + 0.5 * np.sin(2 * ph + 0.3) + 0.25 * np.sin(3 * ph)) * 0.35
    tone += 0.3 * np.sin(ph * 1.007)
    nz = whoosh(dur, 200, 800, 2500, 9000, 1.0, 'rise')
    return (tone * u ** 2.0 + nz * 0.8) * amp


def shimmer(dur, amp, lo=2200, hi=7500, count=26, rise=False):
    n = int(dur * SR); t = np.arange(n) / SR; u = t / dur
    x = np.zeros(n)
    for _ in range(count):
        f = rng.uniform(lo, hi); trem = 0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(3, 11) * t + rng.uniform(0, 6))
        x += np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) * trem
    e = (u ** 1.8) if rise else np.sin(np.pi * u) ** 1.2
    return x / count * e * amp


def hit(f=180, dur=2.2, amp=0.5):
    n = int(dur * SR); t = np.arange(n) / SR
    x = sum(np.sin(2 * np.pi * f * r * t) * np.exp(-t * d) * w for r, d, w in [(1, 2.2, 1), (2.76, 3.4, 0.6), (5.4, 5, 0.35), (8.93, 7, 0.2)])
    return x * amp


def chime(notes, start_gap, amp):
    total = int((start_gap * len(notes) + 2.5) * SR); x = np.zeros(total)
    for k, f in enumerate(notes):
        n = int(2.4 * SR); t = np.arange(n) / SR
        b = (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(2 * np.pi * f * 2.0 * t) + 0.2 * np.sin(2 * np.pi * f * 3.01 * t)) * np.exp(-t * 2.6)
        i = int(k * start_gap * SR); x[i:i + n] += b[:total - i]
    return x * amp


def pad(dur, freqs, amp, attack=1.2, release=1.5):
    n = int(dur * SR); t = np.arange(n) / SR
    x = np.zeros(n)
    for f in freqs:
        for d in (-0.004, 0.0, 0.005):
            x += signal.sawtooth(2 * np.pi * f * (1 + d) * t + rng.uniform(0, 6))
    x = signal.lfilter(*signal.butter(2, 1400 / (SR / 2), 'low'), x)
    e = np.minimum(1, t / attack) * np.minimum(1, (dur - t) / release)
    return x / (3 * len(freqs)) * np.clip(e, 0, 1) * amp


def kick(amp=0.6):
    n = int(0.45 * SR); t = np.arange(n) / SR
    f = 45 + 70 * np.exp(-t * 30)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9) * amp


def reverb(x, secs=2.3, wet=0.28):
    n = int(secs * SR); t = np.arange(n) / SR
    out = np.zeros_like(x)
    for ch in range(2):
        ir = rng.standard_normal(n) * np.exp(-t * 3.0 / secs * 2.2)
        ir = signal.lfilter(*signal.butter(1, 5000 / (SR / 2), 'low'), ir); ir /= np.sqrt(np.sum(ir ** 2))
        out[ch] = signal.fftconvolve(x[ch], ir)[:x.shape[1]]
    return x * (1 - wet) + out * wet * 1.6


def brand(buf, t0, dur):
    """the Whitebrook Road part, from t0: in out of the white, the swarm gathering, the wordmark, MATCHDAY, the pad"""
    place(buf, t0 - 0.55, whoosh(0.75, 300, 1500, 2500, 9000, 0.55, 'rise'))      # the swell into it
    place(buf, t0, boom(1.4, 70, 34, 0.55))
    place(buf, t0 + 0.05, whoosh(1.1, 3000, 9000, 500, 2000, 0.4, 'fall'), 0.3)
    place(buf, t0 + 0.6, shimmer(2.5, 0.22, rise=True), -0.2)
    place(buf, t0 + 0.7, riser(2.3, 180, 720, 0.32))
    place(buf, t0 + 3.0, boom(2.0, 58, 28, 0.85))
    place(buf, t0 + 3.0, hit(196, 2.6, 0.32))
    place(buf, t0 + 3.0, chime([784, 988, 1175, 1568], 0.12, 0.16), 0.25)
    place(buf, t0 + 3.8, chime([2349], 0.1, 0.07), -0.3)
    place(buf, t0 + 3.0, pad(dur - 3.0, [130.8, 196.0, 261.6, 329.6, 392.0], 0.32, attack=0.8, release=1.6))
    place(buf, t0 + 5.0, shimmer(1.0, 0.07, 4000, 9000), 0.4)                     # the shine across the wordmark


def full():
    L = 20.0; buf = np.zeros((2, int(L * SR)))
    place(buf, 0.0, boom(2.0, 66, 30, 0.95))
    place(buf, 0.0, whoosh(1.4, 2000, 9000, 400, 1500, 0.5, 'fall'))
    place(buf, 0.05, shimmer(1.6, 0.26), 0.15)
    place(buf, 1.3, riser(2.75, 140, 620, 0.38))
    for t, p in ((1.9, -0.6), (3.0, 0.6)):
        place(buf, t, whoosh(0.7, 500, 2000, 1500, 7000, 0.35), p)                # the streak passing
    place(buf, 4.0, boom(2.2, 60, 28, 1.0))
    place(buf, 4.0, hit(164.8, 3.0, 0.38))
    place(buf, 4.0, pad(3.4, [110.0, 164.8, 220.0, 261.6], 0.3))
    for k in range(5):
        place(buf, 4.0 + 0.75 * (k + 1), kick(0.42 - 0.04 * k))                   # a heartbeat under the logo
    place(buf, 5.3, shimmer(1.2, 0.08, 4000, 9000), -0.4)                          # its shine
    place(buf, 6.9, whoosh(0.9, 400, 1500, 2500, 8000, 0.5), -0.5)                 # off to the left, the name in
    place(buf, 7.6, riser(2.35, 200, 900, 0.34))
    place(buf, 7.6, chime([659, 784, 988, 1319], 0.28, 0.08), 0.4)                 # the letters
    place(buf, 9.95, boom(1.6, 72, 36, 0.8))
    place(buf, 9.95, whoosh(1.2, 3000, 9000, 800, 3000, 0.45, 'fall'))
    place(buf, 9.95, shimmer(1.5, 0.2, 3000, 8000))
    place(buf, 10.0, pad(1.6, [220.0, 277.2, 329.6], 0.16, attack=0.2, release=0.9))
    brand(buf, 11.4, L - 11.4)
    return buf


def tail():
    L = 10.0; buf = np.zeros((2, int(L * SR)))
    brand(buf, 0.0, L)
    return buf


def master(buf, fade_out=0.8):
    x = reverb(buf)
    n = x.shape[1]; f = int(fade_out * SR)
    x[:, n - f:] *= np.linspace(1, 0, f) ** 1.5
    x = np.tanh(x * 1.3) / np.tanh(1.3)
    x *= 0.89 / (np.max(np.abs(x)) + 1e-9)
    return x


def write(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype('<i2').T.copy()
    with wave.open(path, 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())


if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    write(os.path.join(out, 'reveal-full.wav'), master(full()))
    write(os.path.join(out, 'reveal-tail.wav'), master(tail()))
    print('ok')
