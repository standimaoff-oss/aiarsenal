#!/usr/bin/env python3
"""Procedural small-arms sounds for reply 22 (no downloaded audio):
   rifle_ak (5.45x39 from a 415 mm barrel + muzzle brake), rifle_m4 (5.56x45, 14.5" barrel, A2 birdcage),
   mag_out, mag_in, bolt_rack, dry_fire, selector."""
import os, subprocess, wave
import numpy as np
from scipy import signal
SR = 44100
OUT = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/sounds"
TMP = "/home/user/.cache/snd_r22"
os.makedirs(TMP, exist_ok=True)
rng = np.random.default_rng(2222)


def t_(d): return np.arange(int(d * SR)) / SR
def noise(d): return rng.standard_normal(int(d * SR))


def lp(x, fc, o=4):
    b, a = signal.butter(o, min(fc / (SR / 2), 0.99), 'low'); return signal.lfilter(b, a, x)


def hp(x, fc, o=2):
    b, a = signal.butter(o, fc / (SR / 2), 'high'); return signal.lfilter(b, a, x)


def bp(x, f1, f2, o=2):
    b, a = signal.butter(o, [f1 / (SR / 2), min(f2 / (SR / 2), 0.99)], 'band'); return signal.lfilter(b, a, x)


def norm(x, p=0.95): return x / (np.max(np.abs(x)) + 1e-9) * p


def env(d, a, dec):
    t = t_(d); return (1 - np.exp(-t / max(a, 1e-5))) * np.exp(-t / dec)


def write(name, x, q=4):
    wav = os.path.join(TMP, name + ".wav")
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype('<i2').tobytes())
    ogg = os.path.join(OUT, name + ".ogg")
    subprocess.run(["oggenc", "-Q", "-q", str(q), "-o", ogg, wav], check=True)
    print(f"{name:12s} {len(x) / SR:5.2f}s {os.path.getsize(ogg) / 1024:6.1f} KB")


def shot(D, crack_hz, boom_hz, brake, seed_mix):
    # supersonic crack (N-wave, ~0.3 ms rise), muzzle blast (low boom), brake side-blast (mid-band burst),
    # action clatter 30 ms later, and a reverberant tail
    n = int(D * SR); x = np.zeros(n)
    L = int(0.0009 * SR)
    nw = np.linspace(1, -1, L)
    x[:L] += nw * 1.0
    x += hp(np.concatenate([noise(0.004) * env(0.004, 0.0001, 0.0012), np.zeros(n - int(0.004 * SR))]), crack_hz) * 0.9
    boom = lp(noise(D), boom_hz, 3) * env(D, 0.0008, 0.045) * 3.5
    x += boom
    x += bp(noise(D), 600, 2600) * env(D, 0.0005, 0.03 if brake else 0.02) * (1.1 if brake else 0.6)
    c = np.zeros(n); j = int(0.028 * SR)
    clat = bp(noise(0.02), 1800, 6000) * env(0.02, 0.0002, 0.004)
    c[j:j + len(clat)] += clat * 0.25
    x += c
    tail = lp(noise(D), 900, 2) * env(D, 0.02, 0.35) * 0.35 + bp(noise(D), 200, 600) * env(D, 0.01, 0.6) * 0.25
    x += tail * seed_mix
    x = np.tanh(norm(x) * 2.2)
    return norm(x, 0.97)


write("rifle_ak", shot(0.9, 2500, 420, True, 1.0))
write("rifle_m4", shot(0.9, 3200, 520, False, 0.9))


def click(f1, f2, dec, amp=1.0, d=0.06):
    return bp(noise(d), f1, f2) * env(d, 0.0002, dec) * amp


def seq(parts, D):
    n = int(D * SR); x = np.zeros(n)
    for at, s in parts:
        j = int(at * SR); e = min(n, j + len(s)); x[j:e] += s[:e - j]
    return x


# magazine out: catch click, polymer slide scrape, release
write("mag_out", norm(seq([(0.0, click(2000, 7000, 0.004)), (0.02, bp(noise(0.12), 800, 3500) * env(0.12, 0.01, 0.05) * 0.4),
                           (0.12, click(500, 2500, 0.01, 0.6))], 0.3), 0.8))
# magazine in: scrape + front lug hook + rock-back click (AK) / slap (AR)
write("mag_in", norm(seq([(0.0, bp(noise(0.1), 700, 3000) * env(0.1, 0.01, 0.04) * 0.4), (0.09, click(300, 2000, 0.012, 0.9)),
                          (0.16, click(2500, 8000, 0.005, 1.0))], 0.35), 0.85))
# bolt / charging handle rack: pull (spring scrape) + slam home
write("bolt_rack", norm(seq([(0.0, click(1500, 6000, 0.006, 0.7)), (0.01, bp(noise(0.12), 1200, 5000) * env(0.12, 0.02, 0.06) * 0.45),
                             (0.16, click(400, 3500, 0.02, 1.0)), (0.165, click(3000, 9000, 0.006, 0.6))], 0.4), 0.9))
write("dry_fire", norm(click(2500, 9000, 0.004, 1.0, 0.05) + click(800, 3000, 0.008, 0.4, 0.05), 0.6))
write("selector", norm(seq([(0.0, click(1800, 7000, 0.004)), (0.04, click(2200, 8000, 0.003, 0.7))], 0.1), 0.6))
