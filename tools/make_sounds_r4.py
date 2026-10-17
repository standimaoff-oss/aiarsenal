#!/usr/bin/env python3
"""Procedural sounds for reply 4 (no downloaded audio): tank gun, howitzer, incoming shell whistle."""
import os, subprocess, wave
import numpy as np
from scipy import signal
SR = 22050
OUT = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/sounds"
TMP = "/home/user/.cache/snd_r4"
os.makedirs(TMP, exist_ok=True)
rng = np.random.default_rng(2026)
def t_(dur): return np.arange(int(dur * SR)) / SR
def noise(dur): return rng.standard_normal(int(dur * SR))
def lp(x, fc, order=4):
    b, a = signal.butter(order, min(fc / (SR / 2), 0.99), 'low'); return signal.lfilter(b, a, x)
def hp(x, fc, order=2):
    b, a = signal.butter(order, fc / (SR / 2), 'high'); return signal.lfilter(b, a, x)
def bp(x, f1, f2, order=2):
    b, a = signal.butter(order, [f1 / (SR / 2), min(f2 / (SR / 2), 0.99)], 'band'); return signal.lfilter(b, a, x)
def env_exp(dur, tau, attack=0.002):
    t = t_(dur); e = np.exp(-t / tau); a = np.clip(t / attack, 0, 1); return e * a
def norm(x, peak=0.95): return x / (np.max(np.abs(x)) + 1e-9) * peak
def fade(x, fin=0.005, fout=0.05):
    n = len(x); a = int(fin * SR); b = int(fout * SR); x = x.copy()
    if a: x[:a] *= np.linspace(0, 1, a)
    if b: x[-b:] *= np.linspace(1, 0, b)
    return x
def write(name, x, q=3):
    wav = os.path.join(TMP, name + ".wav")
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype('<i2').tobytes())
    ogg = os.path.join(OUT, name + ".ogg")
    subprocess.run(["oggenc", "-Q", "-q", str(q), "-o", ogg, wav], check=True)
    print(f"{name:16s} {len(x) / SR:5.2f}s {os.path.getsize(ogg) / 1024:6.1f} KB")

def gun(dur, crack_fc, boom_fc, boom_tau, rumble_tau, name, crack_gain):
    n = int(dur * SR)
    crack = hp(noise(dur), crack_fc) * env_exp(dur, 0.012, 0.0004) * crack_gain
    boom = lp(noise(dur), boom_fc, 4) * env_exp(dur, boom_tau, 0.003) * 3.0
    thump = np.sin(2 * np.pi * 38 * t_(dur) * (1 - 0.3 * t_(dur))) * env_exp(dur, boom_tau * 0.8, 0.002) * 0.9
    rumble = lp(noise(dur), 260, 2) * env_exp(dur, rumble_tau, 0.05) * 1.2
    # ground / treeline echoes
    x = crack + boom + thump + rumble
    for d, g in ((0.21, 0.35), (0.47, 0.22), (0.9, 0.12)):
        k = int(d * SR); e = np.zeros(n); e[k:] = lp(x[:n - k], 900, 2) * g; x = x + e
    write(name, fade(norm(np.tanh(norm(x) * 1.6)), 0.0005, 0.5))

gun(3.2, 1800, 420, 0.09, 0.9, "cannon_tank", 1.4)
gun(4.2, 1200, 260, 0.16, 1.4, "cannon_howitzer", 0.9)
# incoming shell: descending whistle with doppler + air rush, ~2.6 s
dur = 2.6; t = t_(dur)
f = 1150 - 520 * (t / dur) ** 1.3
ph = 2 * np.pi * np.cumsum(f) / SR
tone = np.sin(ph) * 0.5 + np.sin(2 * ph) * 0.12
rush = bp(noise(dur), 600, 3500) * 0.35
amp = (0.15 + 0.85 * (t / dur) ** 2)
write("shell_whistle", fade(norm((tone + rush) * amp, 0.85), 0.2, 0.05))
