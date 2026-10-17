#!/usr/bin/env python3
"""Procedural sounds for the Tu-95MS / Tu-160 mod (no downloaded audio). Loops are made seamless by building them
from periodic components and filtering circularly.
usage: python3 tools/make_sounds_tu.py"""
import sys
import numpy as np

sys.path.insert(0, "/home/user/tools")
import make_sounds as ms  # noqa: E402
from make_sounds import SR, t_, lp, hp, bp, norm, fade, env_exp, crackle  # noqa: E402

ms.OUT = "/home/user/mods/tubombers/src/main/resources/assets/tubombers/sounds"
ms.TMP = "/home/user/.cache/air/snd"
write = ms.write
rng = np.random.default_rng(1952)


def pnoise(d):
    """periodic white noise of length d seconds"""
    return rng.standard_normal(int(d * SR))


def circ(x, fn):
    """apply a causal filter circularly so the loop seam is continuous"""
    y = fn(np.concatenate([x, x, x]))
    return y[len(x):2 * len(x)]


def tu95_engine():
    """4x NK-12MP with AV-60K contra-rotating props: 735 rpm -> blade passing 49 Hz per row, the two rows beat
    against each other and the four engines drift slightly in phase -> the famous deep pulsing growl."""
    d = 4.0
    t = t_(d)
    x = np.zeros_like(t)
    for eng in range(4):
        f0 = 49.0 + (eng - 1.5) * 0.25          # 0.25 Hz steps -> 1 full cycle per loop: periodic
        ph = rng.uniform(0, 6.28, 8)
        for h, a in ((1, 1.0), (2, 0.8), (3, 0.45), (4, 0.3), (5, 0.18), (6, 0.12), (8, 0.06)):
            x += a * np.sin(2 * np.pi * f0 * h * t + ph[h % 8]) * (0.8 + 0.2 * np.sin(2 * np.pi * 0.5 * t + eng))
    # blade slap: sharp pulses at the combined passing rate of the two rows (98 Hz) -> rasp
    slap = (np.sin(2 * np.pi * 98 * t) > 0.92).astype(float)
    slap = circ(slap, lambda s: lp(s, 900))
    turb = circ(pnoise(d), lambda s: bp(s, 900, 4200))          # turbine and exhaust hiss
    whine = 0.15 * np.sin(2 * np.pi * 3125 * t)                 # compressor tone (integer cycles)
    x = norm(x) * 0.8 + norm(slap) * 0.35 + norm(turb) * 0.18 + whine * 0.25
    x = circ(x, lambda s: lp(s, 3000, 2))
    write("tu95_engine", norm(x, 0.8), q=3)


def tu160_engine():
    """4x NK-32 turbofans: broadband jet roar, buzz-saw fan tones and low rumble."""
    d = 4.0
    t = t_(d)
    roar = circ(pnoise(d), lambda s: lp(hp(s, 60), 1800))
    rumble = circ(pnoise(d), lambda s: lp(s, 140))
    fan = np.zeros_like(t)
    for f, a in ((1250, 0.5), (2500, 0.25), (625, 0.3), (312.5, 0.25)):
        fan += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6))
    mod = 1 + 0.12 * np.sin(2 * np.pi * 0.75 * t)
    x = norm(roar) * 0.8 * mod + norm(rumble) * 0.6 + fan * 0.12
    write("tu160_engine", norm(x, 0.8), q=3)


def afterburner():
    """reheat: deep tearing roar with crackle."""
    d = 3.0
    roar = circ(pnoise(d), lambda s: lp(s, 700))
    crack = crackle(d, 260, 0.0015)
    crack = circ(crack, lambda s: lp(hp(s, 200), 3500))
    rumble = circ(pnoise(d), lambda s: lp(s, 70))
    x = norm(roar) * 0.7 + norm(crack) * 0.45 + norm(rumble) * 0.7
    write("afterburner", norm(x, 0.85), q=3)


def tu95_cabin():
    """inside the Tu-95: overwhelming low-frequency drone filtered by the fuselage, airframe vibration, air hiss."""
    d = 4.0
    t = t_(d)
    x = np.zeros_like(t)
    for f, a in ((49, 1.0), (98, 0.9), (147, 0.5), (196, 0.35), (24.5, 0.4)):
        x += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) * (0.75 + 0.25 * np.sin(2 * np.pi * 0.5 * t + f))
    hiss = circ(pnoise(d), lambda s: bp(s, 300, 2200))
    x = circ(norm(x) * 0.9 + norm(hiss) * 0.12, lambda s: lp(s, 900, 2))
    write("tu95_cabin", norm(x, 0.75), q=3)


def tu160_cabin():
    """Tu-160 crew cabin: air-conditioning hiss, distant engine rumble."""
    d = 4.0
    t = t_(d)
    hiss = circ(pnoise(d), lambda s: bp(s, 1200, 6000))
    rumble = circ(pnoise(d), lambda s: lp(s, 180))
    tone = 0.08 * np.sin(2 * np.pi * 400 * t)
    x = norm(hiss) * 0.35 + norm(rumble) * 0.8 + tone
    write("tu160_cabin", norm(x, 0.65), q=3)


def wind():
    d = 4.0
    t = t_(d)
    a = circ(pnoise(d), lambda s: bp(s, 250, 1800))
    b = circ(pnoise(d), lambda s: bp(s, 1500, 5000))
    g = 0.75 + 0.25 * np.sin(2 * np.pi * 0.25 * t) * np.sin(2 * np.pi * 0.75 * t + 1)
    x = (norm(a) * 0.8 + norm(b) * 0.25) * g
    write("wind", norm(x, 0.7), q=2)


def hydraulic_whine(d, f0, f1):
    t = t_(d)
    f = f0 + (f1 - f0) * np.minimum(1, t / 0.6)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.3 * norm(bp(ms.noise(d), 600, 3000))


def clank(d, modes, tau):
    t = t_(d)
    x = np.zeros_like(t)
    for f, a in modes:
        x += a * np.sin(2 * np.pi * f * t) * np.exp(-t / (tau * (300 / f) ** 0.3))
    return x * env_exp(d, tau * 2, 0.0005) + 0.4 * norm(hp(ms.noise(d), 1500)) * env_exp(d, 0.01)


def gear():
    """undercarriage cycle: pump whine, strut rumble, uplock thump."""
    d = 6.0
    t = t_(d)
    w = hydraulic_whine(d, 380, 520) * np.minimum(1, t / 0.3) * np.clip((5.6 - t) / 0.4, 0, 1)
    rum = lp(ms.noise(d), 160) * np.sin(np.pi * np.clip(t / 5.6, 0, 1)) ** 0.5
    thump = np.zeros_like(t)
    c = clank(0.8, ((95, 1.0), (240, 0.6), (610, 0.3)), 0.06)
    i = int(5.5 * SR)
    thump[i:i + len(c)] += c[:len(thump) - i]
    x = norm(w) * 0.35 + norm(rum) * 0.5 + norm(thump) * 0.9
    write("gear", fade(norm(x, 0.8), 0.05, 0.1))


def bay_doors():
    d = 2.5
    t = t_(d)
    w = hydraulic_whine(d, 450, 600) * np.clip((2.1 - t) / 0.2, 0, 1)
    thump = np.zeros_like(t)
    c = clank(0.5, ((140, 1.0), (380, 0.7), (900, 0.3)), 0.04)
    i = int(2.0 * SR)
    thump[i:i + len(c)] += c[:len(thump) - i]
    x = norm(w) * 0.4 + norm(thump) * 0.9
    write("bay_doors", fade(norm(x, 0.8), 0.05, 0.05))


def bomb_release():
    """bomb rack lock (KD-type holder) opening: sharp metallic clack and a short rattle."""
    d = 0.6
    a = clank(d, ((520, 1.0), (1240, 0.6), (2700, 0.35), (180, 0.5)), 0.03)
    b = np.zeros_like(a)
    c = clank(0.3, ((800, 0.7), (1900, 0.4)), 0.02)
    i = int(0.07 * SR)
    b[i:i + len(c)] += c[:len(b) - i]
    write("bomb_release", fade(norm(a + 0.6 * b, 0.9), 0.0005, 0.05))


def bomb_whistle():
    """falling bomb: airflow over the stabiliser box -> tonal whistle on broadband rush (loop; pitch set in game)."""
    d = 2.0
    t = t_(d)
    f = 1100 * (1 + 0.004 * np.sin(2 * np.pi * 6 * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    ph = ph - (ph[-1] - np.round(ph[-1] / (2 * np.pi)) * 2 * np.pi) * t / d  # close the phase over the loop
    tone = np.sin(ph) + 0.2 * np.sin(2 * ph)
    rush = circ(pnoise(d), lambda s: bp(s, 400, 4000))
    x = norm(tone) * 0.55 + norm(rush) * 0.45
    write("bomb_whistle", norm(x, 0.8), q=2)


def warning():
    """two-tone cockpit warning (RI-65 voice warning box replaced by tones)."""
    d = 0.5
    t = t_(d)
    f = np.where(t < 0.22, 1000, 750)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * ((t % 0.25) < 0.2)
    write("warning", fade(norm(lp(x, 4000), 0.6), 0.003, 0.02))


if __name__ == "__main__":
    for fn in (tu95_engine, tu160_engine, afterburner, tu95_cabin, tu160_cabin, wind, gear, bay_doors,
               bomb_release, bomb_whistle, warning):
        fn()
