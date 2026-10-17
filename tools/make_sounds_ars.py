#!/usr/bin/env python3
"""Procedural sounds for the AI Arsenal drones / missiles / nukes (no downloaded audio).
Loops are periodic (integer cycles per loop length) and filtered circularly so the seam is inaudible.
usage: python3 tools/make_sounds_ars.py"""
import sys
import numpy as np

sys.path.insert(0, "/home/user/tools")
import make_sounds as ms  # noqa: E402
from make_sounds import SR, t_, lp, hp, bp, norm, fade, env_exp, crackle  # noqa: E402

ms.OUT = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/sounds"
ms.TMP = "/home/user/.cache/air/snd"
write = ms.write
rng = np.random.default_rng(2026)


def pnoise(d):
    return rng.standard_normal(int(d * SR))


def circ(x, fn):
    y = fn(np.concatenate([x, x, x]))
    return y[len(x):2 * len(x)]


def shahed_engine():
    """MD-550 4-cylinder 2-stroke boxer driving a 2-blade prop: ~6000 rpm -> 100 Hz firing pulses (2 per rev x 2 cyl pairs),
    blade passing 200 Hz, raspy exhaust -> the infamous 'moped' buzz."""
    d = 2.0
    t = t_(d)
    f0 = 100.0
    pulses = np.zeros_like(t)
    ph = (t * f0) % 1.0
    pulses += np.exp(-ph * 18.0)                       # sharp exhaust pulse per firing
    pulses += 0.6 * np.exp(-((t * f0 + 0.5) % 1.0) * 22.0)
    pulses = circ(pulses, lambda s: bp(s, 70, 2600))
    harm = sum(a * np.sin(2 * np.pi * f0 * h * t + rng.uniform(0, 6)) for h, a in ((1, 1.0), (2, 0.7), (3, 0.5), (4, 0.35), (6, 0.2)))
    prop = 0.5 * np.sign(np.sin(2 * np.pi * 200 * t)) * 0.3
    rasp = circ(pnoise(d), lambda s: bp(s, 1800, 5200)) * (0.6 + 0.4 * np.exp(-ph * 10))
    x = norm(pulses) * 0.8 + norm(harm) * 0.45 + prop * 0.25 + norm(rasp) * 0.22
    write("shahed_engine", norm(circ(x, lambda s: lp(s, 6000, 2)), 0.85), q=3)


def jet_loop():
    """Small turbojet (TJ-150 / TRDD-50 class): high compressor whine + broadband exhaust roar."""
    d = 2.0
    t = t_(d)
    roar = circ(pnoise(d), lambda s: lp(hp(s, 120), 3500))
    hiss = circ(pnoise(d), lambda s: bp(s, 3000, 9000))
    whine = sum(a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) for f, a in ((4500, 0.5), (9000, 0.15), (2250, 0.25)))
    x = norm(roar) * 0.8 + norm(hiss) * 0.25 + whine * 0.18
    write("jet_loop", norm(x, 0.8), q=3)


def turboprop_loop():
    """MQ-9 TPE331 turboprop / Orion piston pusher: blade passing 3-blade at ~2000 rpm -> 100 Hz drone + turbine whine."""
    d = 2.0
    t = t_(d)
    f0 = 50.0
    x = sum(a * np.sin(2 * np.pi * f0 * h * t + rng.uniform(0, 6)) for h, a in ((2, 1.0), (4, 0.5), (6, 0.3), (8, 0.15), (1, 0.3)))
    slap = circ((np.sin(2 * np.pi * 100 * t) > 0.9).astype(float), lambda s: lp(s, 1500))
    turb = circ(pnoise(d), lambda s: bp(s, 1500, 5000))
    whine = 0.1 * np.sin(2 * np.pi * 6000 * t)
    y = norm(x) * 0.7 + norm(slap) * 0.3 + norm(turb) * 0.2 + whine
    write("turboprop_loop", norm(circ(y, lambda s: lp(s, 5000, 2)), 0.8), q=3)


def fpv_motor():
    """4 brushless motors at ~25k rpm with 3-blade props: whine at 1250 Hz with beating between motors + prop wash."""
    d = 2.0
    t = t_(d)
    x = np.zeros_like(t)
    for m, f in enumerate((1250.0, 1262.5, 1237.5, 1275.0)):
        x += np.sin(2 * np.pi * f * t + m) + 0.4 * np.sin(2 * np.pi * f * 2 * t + m) + 0.3 * np.sin(2 * np.pi * f / 3 * t)
    wash = circ(pnoise(d), lambda s: bp(s, 300, 4000))
    y = norm(x) * 0.55 + norm(wash) * 0.45
    write("fpv_motor", norm(y, 0.8), q=3)


def booster():
    """Solid rocket booster: loud crackling roar (loop)."""
    d = 2.0
    roar = circ(pnoise(d), lambda s: lp(hp(s, 40), 2500))
    rumble = circ(pnoise(d), lambda s: lp(s, 120))
    cr = circ(crackle(d, 300, 0.0015), lambda s: hp(s, 800))
    x = norm(roar) * 0.7 + norm(rumble) * 0.6 + norm(cr) * 0.3
    write("booster", norm(x, 0.85), q=3)


def launch_rail():
    """RATO ignition on the rail + whoosh."""
    d = 2.5
    t = t_(d)
    x = norm(lp(ms.noise(d), 3000)) * env_exp(d, 0.9, 0.01)
    bang = norm(lp(ms.noise(d), 400)) * env_exp(d, 0.15)
    write("launch_rail", norm(fade(x * 0.7 + bang * 0.6), 0.9), q=4)


def launch_cold():
    """ICBM cold launch: gas generator 'pop' ejecting the missile from the canister, then first-stage ignition roar."""
    d = 6.0
    t = t_(d)
    pop = norm(lp(ms.noise(d), 250)) * env_exp(d, 0.25)
    ign = np.where(t > 0.9, 1.0, 0.0) * (1 - np.exp(-(t - 0.9).clip(0) * 4))
    roar = norm(lp(hp(ms.noise(d), 30), 1800)) * ign * env_exp(d, 4.0)
    cr = norm(hp(crackle(d, 250, 0.002), 700)) * ign * 0.3
    write("launch_cold", norm(fade(pop * 0.9 + roar * 0.8 + cr), 0.95), q=4)


def nuke_blast():
    """Nuclear detonation heard nearby: enormous crack, then a long rolling rumble with debris (~9 s)."""
    d = 9.0
    t = t_(d)
    crack = norm(hp(ms.noise(d), 200)) * env_exp(d, 0.08)
    body = norm(lp(ms.noise(d), 300)) * env_exp(d, 2.5, 0.02)
    sub = norm(lp(ms.noise(d), 60)) * env_exp(d, 4.5, 0.05)
    roll = norm(lp(ms.noise(d), 900)) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.7 * t) ** 2) * env_exp(d, 3.0, 0.3)
    deb = norm(hp(crackle(d, 60, 0.004), 1500)) * env_exp(d, 2.0, 0.4) * 0.25
    x = crack * 0.9 + body * 0.9 + sub * 1.0 + roll * 0.4 + deb
    write("nuke_blast", norm(fade(x, 0.001, 1.5), 0.98), q=4)


def nuke_far():
    """Distant nuclear detonation: low-passed thunder, long tail."""
    d = 8.0
    t = t_(d)
    x = norm(lp(ms.noise(d), 140)) * env_exp(d, 3.5, 0.15) + 0.4 * norm(lp(ms.noise(d), 40)) * env_exp(d, 5.0, 0.4)
    write("nuke_far", norm(fade(x, 0.01, 2.0), 0.95), q=4)


def geiger():
    """Geiger counter clicks (1 s loop, ~25 random clicks)."""
    d = 1.0
    n = int(d * SR)
    x = np.zeros(n)
    for k in rng.integers(0, n - 400, 25):
        x[k:k + 300] += np.exp(-np.arange(300) / 25.0) * np.sin(np.arange(300) * 0.9)
    write("geiger", norm(hp(x, 1500), 0.8), q=3)


def beep():
    d = 0.25
    t = t_(d)
    x = np.sin(2 * np.pi * 1760 * t) * (t < 0.08) + np.sin(2 * np.pi * 2350 * t) * (t > 0.12) * (t < 0.2)
    write("tablet_beep", norm(fade(x, 0.002, 0.02), 0.6), q=3)


def detonator():
    """Detonator: switch clack + alarm chirp."""
    d = 0.6
    t = t_(d)
    clk = norm(hp(ms.noise(d), 2000)) * env_exp(d, 0.008)
    chirp = np.sin(2 * np.pi * (1200 + 1800 * t) * t) * (t > 0.08) * (t < 0.5)
    write("detonator", norm(fade(clk + 0.4 * chirp, 0.001, 0.05), 0.85), q=3)


def static_noise():
    """Analog FPV video static hiss (loop)."""
    d = 1.0
    x = circ(pnoise(d), lambda s: hp(s, 500))
    write("fpv_static", norm(x, 0.5), q=3)


def siren():
    """Air-raid siren (rising/falling, 6 s loop)."""
    d = 6.0
    t = t_(d)
    f = 380 + 260 * (0.5 - 0.5 * np.cos(2 * np.pi * t / d))
    ph = 2 * np.pi * np.cumsum(f) / SR
    ph *= np.round(ph[-1] / (2 * np.pi)) * 2 * np.pi / ph[-1]
    x = np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph)
    write("siren", norm(x, 0.7), q=3)


for fn in (shahed_engine, jet_loop, turboprop_loop, fpv_motor, booster, launch_rail, launch_cold, nuke_blast, nuke_far, geiger, beep,
           detonator, static_noise, siren):
    fn()
print("done")
