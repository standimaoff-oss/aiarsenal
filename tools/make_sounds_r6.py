#!/usr/bin/env python3
"""Procedural sounds for reply 6 (no downloaded audio): rotor blade slap, turbines, boat diesel, 30 mm cannon,
heavy MG, torpedo launch, flares, sonar ping, missile warning."""
import os, subprocess, wave, json
import numpy as np
from scipy import signal
SR = 22050
OUT = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/sounds"
TMP = "/home/user/.cache/snd_r6"
os.makedirs(TMP, exist_ok=True)
rng = np.random.default_rng(606)
def t_(d): return np.arange(int(d * SR)) / SR
def noise(d): return rng.standard_normal(int(d * SR))
def lp(x, fc, o=4):
    b, a = signal.butter(o, min(fc / (SR / 2), 0.99), 'low'); return signal.lfilter(b, a, x)
def hp(x, fc, o=2):
    b, a = signal.butter(o, fc / (SR / 2), 'high'); return signal.lfilter(b, a, x)
def bp(x, f1, f2, o=2):
    b, a = signal.butter(o, [f1 / (SR / 2), min(f2 / (SR / 2), 0.99)], 'band'); return signal.lfilter(b, a, x)
def env_exp(d, tau, att=0.002):
    t = t_(d); return np.exp(-t / tau) * np.clip(t / att, 0, 1)
def norm(x, p=0.95): return x / (np.max(np.abs(x)) + 1e-9) * p
def fade(x, fi=0.005, fo=0.05):
    x = x.copy(); a = int(fi * SR); b = int(fo * SR)
    if a: x[:a] *= np.linspace(0, 1, a)
    if b: x[-b:] *= np.linspace(1, 0, b)
    return x
def loopify(x, xf=0.25):
    """Crossfade the tail into the head so the loop is seamless."""
    n = int(xf * SR); body = x[:-n].copy(); tail = x[-n:]
    w = np.linspace(0, 1, n); body[:n] = body[:n] * w + tail * (1 - w); return body
def write(name, x, q=3):
    wav = os.path.join(TMP, name + ".wav")
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype('<i2').tobytes())
    ogg = os.path.join(OUT, name + ".ogg")
    subprocess.run(["oggenc", "-Q", "-q", str(q), "-o", ogg, wav], check=True)
    print(f"{name:16s} {len(x) / SR:5.2f}s {os.path.getsize(ogg) / 1024:6.1f} KB")

# --- rotor: blade-vortex "wop-wop" slaps at ~13 Hz (3+3 blades coaxial / 4 blades AH-64 at ~4.8 rev/s)
D = 2.25; n = int(D * SR); x = np.zeros(n)
rate = 12.5
for k in range(int(D * rate) + 1):
    i = int(k / rate * SR + rng.normal(0, 0.0015) * SR)
    if i >= n: break
    L = int(0.07 * SR); seg = lp(noise(0.07), 700, 2) * env_exp(0.07, 0.012, 0.0015) * (0.8 + 0.4 * rng.random())
    th = np.sin(2 * np.pi * 55 * t_(0.07)) * env_exp(0.07, 0.02, 0.002) * 0.9
    seg = seg + th; e = min(n, i + L); x[i:e] += seg[:e - i]
wash = lp(noise(D), 400, 2) * 0.35 + bp(noise(D), 300, 1800) * 0.08
write("rotor", norm(loopify(np.tanh(norm(x + wash) * 1.4)), 0.9))
# --- turbines: whine (two spools) + hiss, integer cycles for clean looping
D = 2.25; t = t_(D)
wh = sum(a * np.sin(2 * np.pi * f * t) for f, a in ((2200, 0.5), (3300, 0.18), (1460, 0.22), (740, 0.12)))
wh *= 1 + 0.03 * np.sin(2 * np.pi * 3 * t)
hiss = bp(noise(D), 2000, 7000) * 0.35 + lp(noise(D), 250) * 0.25
write("rotor_turbine", norm(loopify(wh * 0.5 + hiss), 0.7))
# --- boat diesel: V8 firing pulses ~45 Hz + harmonics + water slap
D = 2.25; t = t_(D); n = len(t)
fire = np.zeros(n); per = int(SR / 45)
for i in range(0, n, per):
    L = min(per, n - i); fire[i:i + L] += np.exp(-np.arange(L) / (0.004 * SR)) * (0.7 + 0.3 * rng.random())
diesel = lp(fire, 500, 3) * 2.0 + lp(noise(D), 150) * 0.4 + np.sin(2 * np.pi * 45 * t) * 0.3
water = bp(noise(D), 400, 2500) * (0.12 + 0.08 * np.sin(2 * np.pi * 0.9 * t) ** 2)
write("boat_engine", norm(loopify(np.tanh(norm(diesel + water) * 1.3)), 0.85))
# --- 30 mm cannon: hard crack + short boom
D = 0.6
crack = hp(noise(D), 1500) * env_exp(D, 0.006, 0.0003) * 1.5
boom = lp(noise(D), 500, 4) * env_exp(D, 0.05, 0.002) * 2.5 + np.sin(2 * np.pi * 70 * t_(D)) * env_exp(D, 0.04) * 0.8
write("craft_cannon", fade(norm(np.tanh(norm(crack + boom) * 1.8)), 0.0003, 0.2))
# --- heavy MG
D = 0.4
crack = hp(noise(D), 2200) * env_exp(D, 0.004, 0.0002) * 1.4
boom = lp(noise(D), 900, 4) * env_exp(D, 0.03, 0.001) * 2.0
write("craft_mg", fade(norm(np.tanh(norm(crack + boom) * 1.7)), 0.0002, 0.15))
# --- torpedo launch: compressed-air slam, muffled, then whoosh / bubbles
D = 2.0; t = t_(D)
slam = lp(noise(D), 300, 4) * env_exp(D, 0.08, 0.002) * 3 + np.sin(2 * np.pi * 42 * t) * env_exp(D, 0.15) * 1.2
air = lp(noise(D), 900, 2) * np.clip(t / 0.1, 0, 1) * np.exp(-t / 0.6) * 1.0
bub = np.zeros(len(t))
for k in range(60):
    i = int(rng.uniform(0.1, 1.8) * SR); L = int(0.03 * SR); f = rng.uniform(300, 900)
    if i + L < len(t): bub[i:i + L] += np.sin(2 * np.pi * f * (1 + 2 * t_(0.03)) * t_(0.03)) * env_exp(0.03, 0.01) * 0.3
write("torpedo_launch", fade(norm(slam + air + bub, 0.9), 0.001, 0.3))
# --- flares: several pops + burning hiss
D = 1.2; n = int(D * SR); x = np.zeros(n)
for k in range(4):
    i = int((0.02 + k * 0.09) * SR); seg = hp(noise(0.08), 400) * env_exp(0.08, 0.01, 0.0005)
    x[i:i + len(seg)] += seg
x += bp(noise(D), 1500, 6000) * np.clip(t_(D) / 0.05, 0, 1) * np.exp(-t_(D) / 0.5) * 0.25
write("flare_pop", fade(norm(x, 0.85), 0.0005, 0.3))
# --- sonar ping with reverb tail
D = 3.2; t = t_(D)
ping = np.sin(2 * np.pi * 3500 * t) * np.clip(t / 0.01, 0, 1) * (t < 0.35)
y = ping.copy()
for d, g in ((0.11, 0.5), (0.23, 0.35), (0.41, 0.25), (0.7, 0.16), (1.1, 0.1), (1.6, 0.06)):
    k = int(d * SR); e = np.zeros(len(t)); e[k:] = ping[:len(t) - k] * g; y += e
y = lp(y, 5000) * np.exp(-t / 1.4)
write("sonar_ping", fade(norm(y, 0.6), 0.002, 0.4))
# --- missile warning: fast two-tone beep
D = 0.24; t = t_(D)
f = np.where(t < 0.12, 1250, 1650)
write("missile_warn", fade(norm(np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * 0.4 + np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.6, 0.6), 0.003, 0.02))

# sounds.json entries
p = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/sounds.json"
d = json.load(open(p))
for nm, stream in (("rotor", False), ("rotor_turbine", False), ("boat_engine", False), ("craft_cannon", False), ("craft_mg", False),
                   ("torpedo_launch", False), ("flare_pop", False), ("sonar_ping", False), ("missile_warn", False)):
    d[nm] = {"sounds": [{"name": "aiarsenal:" + nm, "stream": stream}], "subtitle": "subtitles.aiarsenal." + nm}
json.dump(d, open(p, "w"), indent=2)
print("sounds.json", len(d))
