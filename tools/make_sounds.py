#!/usr/bin/env python3
"""Procedural sound synthesis for the BM-21 mod (no downloaded audio).
Writes mono 22.05 kHz OGG Vorbis files into the mod's assets/bm21grad/sounds/.
usage: python3 tools/make_sounds.py"""
import os, subprocess, wave
import numpy as np
from scipy import signal

SR = 22050
OUT = "/home/user/mods/bm21grad/src/main/resources/assets/bm21grad/sounds"
TMP = "/home/user/.cache/bm21/snd"
rng = np.random.default_rng(1963)


def t_(dur):
    return np.arange(int(dur * SR)) / SR


def noise(dur):
    return rng.standard_normal(int(dur * SR))


def lp(x, fc, order=4):
    b, a = signal.butter(order, min(fc / (SR / 2), 0.99), 'low')
    return signal.lfilter(b, a, x)


def hp(x, fc, order=2):
    b, a = signal.butter(order, fc / (SR / 2), 'high')
    return signal.lfilter(b, a, x)


def bp(x, f1, f2, order=2):
    b, a = signal.butter(order, [f1 / (SR / 2), min(f2 / (SR / 2), 0.99)], 'band')
    return signal.lfilter(b, a, x)


def sweep_lp(x, f_start, f_end, blocks=40):
    """time-varying low-pass by crossfading blocks filtered with different cutoffs"""
    n = len(x)
    out = np.zeros(n)
    edges = np.linspace(0, n, blocks + 1).astype(int)
    win = int(0.02 * SR)
    for i in range(blocks):
        f = f_start * (f_end / f_start) ** (i / max(1, blocks - 1))
        a0, a1 = max(0, edges[i] - win), min(n, edges[i + 1] + win)
        seg = lp(x[max(0, a0 - 2000):a1], f)[a0 - max(0, a0 - 2000):]
        w = np.ones(a1 - a0)
        r = min(win, len(w) // 2)
        if i > 0: w[:2 * r] = np.linspace(0, 1, 2 * r)
        if i < blocks - 1: w[-2 * r:] = np.linspace(1, 0, 2 * r)
        out[a0:a1] += seg * w
    return out


def env_exp(dur, tau, attack=0.002):
    t = t_(dur)
    e = np.exp(-t / tau)
    a = int(attack * SR)
    if a > 0: e[:a] *= np.linspace(0, 1, a)
    return e


def norm(x, peak=0.95):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def fade(x, fin=0.005, fout=0.05):
    a, b = int(fin * SR), int(fout * SR)
    if a: x[:a] *= np.linspace(0, 1, a)
    if b: x[-b:] *= np.linspace(1, 0, b)
    return x


def pad(x, n):
    return np.concatenate([x, np.zeros(max(0, n - len(x)))])[:n]


def crackle(dur, rate, decay=0.002):
    n = int(dur * SR)
    out = np.zeros(n)
    k = int(rate * dur)
    pos = rng.integers(0, n, k)
    amp = rng.uniform(0.3, 1.0, k)
    klen = int(decay * 6 * SR)
    kern = np.exp(-np.arange(klen) / (decay * SR)) * rng.choice([-1, 1], klen)
    for p, a in zip(pos, amp):
        e = min(n, p + klen)
        out[p:e] += kern[:e - p] * a
    return out


def write(name, x, q=3):
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(TMP, exist_ok=True)
    x = np.clip(x, -1, 1)
    wav = os.path.join(TMP, name + ".wav")
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype('<i2').tobytes())
    ogg = os.path.join(OUT, name + ".ogg")
    subprocess.run(["oggenc", "-Q", "-q", str(q), "-o", ogg, wav], check=True)
    print(f"{name:16s} {len(x) / SR:5.2f}s {os.path.getsize(ogg) / 1024:6.1f} KB")


# ------------------------------------------------------------------ launch: ignition crack + roaring motor moving away
def launch():
    d = 3.0
    n = int(d * SR)
    t = t_(d)
    crack = pad(hp(noise(0.05), 800) * env_exp(0.05, 0.006), n)
    thump = pad(np.sin(2 * np.pi * 60 * t_(0.25) * (1 - 0.3 * t_(0.25))) * env_exp(0.25, 0.06), n)
    roar_src = noise(d) + 0.6 * crackle(d, 900, 0.0015)
    roar = sweep_lp(roar_src, 5500, 700)
    roar += 0.5 * bp(noise(d), 180, 500)
    e = np.minimum(1, t / 0.04) * np.exp(-np.maximum(0, t - 0.25) / 0.9)
    x = 0.9 * crack + 0.7 * thump + 0.55 * norm(roar) * e
    write("launch", fade(norm(x), 0.001, 0.3))


def rocket_flight():
    d = 3.5
    t = t_(d)
    src = noise(d) + 0.4 * crackle(d, 600, 0.002)
    x = sweep_lp(src, 2500, 500) * np.minimum(1, t / 0.1) * np.exp(-t / 1.6)
    write("rocket_flight", fade(norm(x, 0.8), 0.01, 0.4))


# ------------------------------------------------------------------ explosions
def explosion_near():
    d = 4.0
    n = int(d * SR)
    t = t_(d)
    nwave = pad(np.concatenate([np.linspace(0, 1, 20), np.linspace(1, -0.8, 160), np.linspace(-0.8, 0, 120)]), n)
    burst = hp(noise(d), 100) * env_exp(d, 0.12, 0.001)
    body = lp(noise(d), 1500) * env_exp(d, 0.45, 0.003)
    f = 48 * np.exp(-t / 1.5) + 22
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(d, 0.7, 0.004)
    rumble = lp(noise(d), 180) * (np.minimum(1, t / 0.2) * np.exp(-t / 1.4))
    debris = crackle(d, 60, 0.004) * np.clip((t - 0.25) / 0.2, 0, 1) * np.exp(-t / 1.2)
    x = 0.9 * nwave + 0.45 * norm(burst) + 0.6 * norm(body) + 0.9 * boom + 0.8 * norm(rumble) + 0.25 * norm(debris)
    write("explosion_near", fade(norm(x), 0.0005, 0.5))


def explosion_far():
    d = 4.5
    t = t_(d)
    boom = lp(noise(d), 220) * env_exp(d, 0.5, 0.02)
    f = 35 * np.exp(-t / 2) + 18
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(d, 0.9, 0.03)
    roll = lp(noise(d), 120)
    am = 0.6 + 0.4 * lp(np.abs(noise(d)), 4)
    roll = roll * am * np.minimum(1, t / 0.3) * np.exp(-t / 1.8)
    echo = np.zeros_like(boom)
    for delay, g in ((0.35, 0.45), (0.8, 0.3), (1.4, 0.2)):
        k = int(delay * SR)
        echo[k:] += boom[:-k] * g
    x = norm(boom) + 0.8 * sub + 0.7 * norm(roll) + lp(echo, 150)
    write("explosion_far", fade(norm(x, 0.9), 0.01, 0.6))


def submunition():
    d = 1.6
    n = int(d * SR)
    t = t_(d)
    pop = pad(hp(noise(0.12), 300) * env_exp(0.12, 0.015), n)
    pthump = pad(np.sin(2 * np.pi * 90 * t_(0.2)) * env_exp(0.2, 0.05), n)
    whoosh = bp(noise(d), 400, 3000) * np.exp(-((t - 0.25) / 0.25) ** 2)
    x = norm(pop) + 0.6 * pthump + 0.35 * norm(whoosh)
    write("submunition", fade(norm(x), 0.0005, 0.3))


# ------------------------------------------------------------------ vehicle
def engine():
    """ZIL-375 V8 idle, seamless 2 s loop (48 Hz firing -> 96 cycles)."""
    d = 2.0
    t = t_(d)
    f0 = 48.0
    x = np.zeros_like(t)
    for h, a in ((0.5, 0.35), (1, 1.0), (2, 0.55), (3, 0.3), (4, 0.22), (6, 0.1), (8, 0.06)):
        x += a * np.sin(2 * np.pi * f0 * h * t + rng.uniform(0, 6.28))
    # combustion roughness: per-cycle amplitude jitter, periodic over the loop
    cyc = (t * f0).astype(int) % 96
    jit = rng.uniform(0.75, 1.15, 96)[cyc]
    x *= jit
    nz = bp(noise(d + 0.5), 150, 1800)[int(0.5 * SR):]
    x = x + 0.25 * norm(nz) * (0.6 + 0.4 * np.sin(2 * np.pi * f0 * t) ** 2)
    x = lp(np.concatenate([x, x, x]), 1400)[len(x):2 * len(x)]  # filter circularly
    # crossfade the noise part at the seam
    write("engine", norm(x, 0.7), q=2)


def reload():
    d = 1.3
    n = int(d * SR)
    t = t_(d)

    def clank(dur, modes, tau):
        tt = t_(dur)
        s = sum(a * np.sin(2 * np.pi * f * tt) * np.exp(-tt / (tau * k)) for f, a, k in modes)
        return s + 0.4 * hp(noise(dur), 1500) * env_exp(dur, 0.01)

    c1 = pad(clank(0.8, [(173, 1, 1), (412, 0.7, 0.7), (951, 0.5, 0.5), (1733, 0.35, 0.35), (2610, 0.2, 0.3)], 0.12), n)
    c2 = np.zeros(n)
    k = int(0.42 * SR)
    c2[k:] = pad(clank(0.5, [(620, 1, 1), (1480, 0.6, 0.6), (3150, 0.3, 0.4)], 0.05), n - k)
    slide = bp(noise(d), 300, 1500) * np.exp(-((t - 0.15) / 0.12) ** 2) * 0.3
    x = norm(c1) + 0.55 * norm(c2) + slide
    write("reload", fade(norm(x), 0.001, 0.2))


def hydraulics():
    d = 0.6
    t = t_(d)
    f = 110 + 10 * np.sin(2 * np.pi * 3 * t)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = signal.sawtooth(ph) * 0.5 + 0.3 * np.sin(4 * ph) + 0.25 * bp(noise(d), 1500, 4000)
    x = lp(x, 2500)
    e = np.sin(np.pi * t / d) ** 0.6
    write("hydraulics", norm(x * e, 0.6))


def incoming():
    d = 2.6
    t = t_(d)
    f = 1900 * (650 / 1900) ** (t / d)
    f = f * (1 + 0.006 * np.sin(2 * np.pi * 7 * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) + 0.25 * np.sin(2 * ph)
    air = bp(noise(d), 500, 3500)
    amp = (0.15 + 0.85 * (t / d) ** 1.6)
    x = (norm(tone) * 0.8 + norm(air) * 0.35) * amp
    write("incoming", fade(norm(x, 0.85), 0.2, 0.03))


if __name__ == "__main__":
    for fn in (launch, rocket_flight, explosion_near, explosion_far, submunition, engine, reload, hydraulics, incoming):
        fn()
