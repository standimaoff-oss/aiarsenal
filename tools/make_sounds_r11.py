#!/usr/bin/env python3
"""Procedural sounds for reply 11 (no downloaded audio): piston aero engine loop, slipstream loop."""
import os, subprocess, wave
import numpy as np
from scipy import signal
SR = 22050
OUT = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/sounds"
TMP = "/home/user/.cache/snd_r11"
os.makedirs(TMP, exist_ok=True)
rng = np.random.default_rng(1111)
def t_(d): return np.arange(int(d * SR)) / SR
def noise(d): return rng.standard_normal(int(d * SR))
def lp(x, fc, o=4):
    b, a = signal.butter(o, min(fc / (SR / 2), 0.99), 'low'); return signal.lfilter(b, a, x)
def bp(x, f1, f2, o=2):
    b, a = signal.butter(o, [f1 / (SR / 2), min(f2 / (SR / 2), 0.99)], 'band'); return signal.lfilter(b, a, x)
def norm(x, p=0.95): return x / (np.max(np.abs(x)) + 1e-9) * p
def loopify(x, xf=0.25):
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

# --- V12 / radial at cruise: 40 rev/s crank, 6 firings per rev -> 240 Hz pulses with cycle-to-cycle jitter,
#     exhaust stub crackle, 3-blade prop passage at 0.6 gear ratio (72 Hz) beating against the firing
D = 2.5; n = int(D * SR); x = np.zeros(n)
fire = 120.0     # recorded at low rpm; the game raises the pitch with rpm
per = SR / fire
i = 0.0; k = 0
while i < n:
    j = int(i + rng.normal(0, 0.02) * per)
    if 0 <= j < n:
        L = int(per * 1.6); seg = np.exp(-np.arange(L) / (per * 0.25)) * (0.75 + 0.5 * rng.random()) * (1.15 if k % 6 == 0 else 1.0)
        e = min(n, j + L); x[j:e] += seg[:e - j]
    i += per; k += 1
x = lp(x - np.mean(x), 900, 2)
t = t_(D)
prop = 0.35 * np.sign(np.sin(2 * np.pi * 36 * t)) * (0.5 + 0.5 * np.sin(2 * np.pi * 36 * t) ** 2)
prop = lp(prop, 300, 2)
crack = bp(noise(D), 900, 3500) * (np.abs(lp(noise(D), 40)) * 2.5) * 0.25
rumble = lp(noise(D), 120, 2) * 0.6
y = norm(x) * 0.9 + prop * 0.7 + crack + rumble
write("prop_piston", norm(loopify(np.tanh(norm(y) * 1.6)), 0.9))
# --- slipstream: broadband wind, slowly modulated
D = 2.5; t = t_(D)
w = bp(noise(D), 250, 2500) * (1 + 0.25 * np.sin(2 * np.pi * 0.8 * t)) + lp(noise(D), 180) * 0.6 + bp(noise(D), 3000, 7000) * 0.12
write("air_rush", norm(loopify(w), 0.7))
