#!/usr/bin/env python3
"""Procedural sounds for reply 26 (no downloaded audio): smg_ppsh (7.62x25 TT, supersonic: sharp crack, short
open-bolt clack), smg_mp40 (9x19, ~400 m/s: almost no crack, rounder lower thump, heavier bolt), rifle_mosin
(7.62x54R from a 730 mm barrel: hard crack, big boom, long tail), rifle_k98 (7.92x57, 600 mm barrel: a bit
louder blast, slightly shorter), clip_in (stripper clip in the guides + thumb pushes 5 rounds down + clip pops out)."""
import sys
import numpy as np
sys.argv = [sys.argv[0]]
src = open('/home/user/tools/make_sounds_r22.py').read().split('write("rifle_ak"')[0]
exec(src)


def smg(D, crack, crack_hz, boom_hz, bolt_hz, bolt_amp):
    n = int(D * SR); x = np.zeros(n)
    if crack > 0:
        L = int(0.0007 * SR)
        x[:L] += np.linspace(1, -1, L) * crack
    x += hp(np.concatenate([noise(0.003) * env(0.003, 0.0001, 0.001), np.zeros(n - int(0.003 * SR))]), crack_hz) * 0.6
    x += lp(noise(D), boom_hz, 3) * env(D, 0.0008, 0.03) * 3.0
    x += bp(noise(D), 500, 2200) * env(D, 0.0005, 0.018) * 0.7
    c = np.zeros(n); j = int(0.018 * SR)                                # open bolt slams home after the shot
    cl = bp(noise(0.02), bolt_hz, bolt_hz * 3.5) * env(0.02, 0.0002, 0.005)
    c[j:j + len(cl)] += cl * bolt_amp
    x += c
    x += lp(noise(D), 800, 2) * env(D, 0.015, 0.22) * 0.3 + bp(noise(D), 180, 550) * env(D, 0.01, 0.35) * 0.2
    return norm(np.tanh(norm(x) * 2.2), 0.95)


def rifle(D, crack_hz, boom_hz, tail):
    n = int(D * SR); x = np.zeros(n)
    L = int(0.0009 * SR)
    x[:L] += np.linspace(1, -1, L) * 1.1
    x += hp(np.concatenate([noise(0.004) * env(0.004, 0.0001, 0.0012), np.zeros(n - int(0.004 * SR))]), crack_hz) * 0.9
    x += lp(noise(D), boom_hz, 3) * env(D, 0.0008, 0.055) * 4.0
    x += bp(noise(D), 500, 2400) * env(D, 0.0005, 0.025) * 0.8
    x += (lp(noise(D), 800, 2) * env(D, 0.025, 0.5) * 0.4 + bp(noise(D), 150, 500) * env(D, 0.01, 0.9) * 0.3) * tail
    return norm(np.tanh(norm(x) * 2.4), 0.97)


def click(f1, f2, dec, amp=1.0, d=0.06):
    return bp(noise(d), f1, f2) * env(d, 0.0002, dec) * amp


def seq(parts, D):
    n = int(D * SR); x = np.zeros(n)
    for at, s in parts:
        j = int(at * SR); e = min(n, j + len(s)); x[j:e] += s[:e - j]
    return x


write("smg_ppsh", smg(0.55, 0.8, 2800, 480, 1500, 0.35))
write("smg_mp40", smg(0.6, 0.0, 1800, 330, 900, 0.45))
write("rifle_mosin", rifle(1.4, 2400, 360, 1.25))
write("rifle_k98", rifle(1.3, 2600, 400, 1.15))
parts = [(0.0, click(1200, 5000, 0.01, 0.8))]                          # clip seats in the charger guides
for i in range(5):                                                      # rounds stripped down one after another
    parts.append((0.06 + i * 0.045, bp(noise(0.04), 900, 4200) * env(0.04, 0.004, 0.015) * 0.45))
    parts.append((0.09 + i * 0.045, click(2200, 7000, 0.006, 0.35)))
parts.append((0.36, click(2500, 8000, 0.01, 0.6)))                      # empty clip flicked out
write("clip_in", norm(seq(parts, 0.5), 0.85))
print("SND_R26_DONE")
