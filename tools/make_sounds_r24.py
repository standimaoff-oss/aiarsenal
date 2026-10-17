#!/usr/bin/env python3
"""Procedural sounds for reply 24 (no downloaded audio): shotgun_saiga (12/76 from the 430 mm barrel, slotted flash hider,
gas action clatter), shotgun_870 (12 ga, 18" barrel, deep boom, long tail), pump_action (870: pump back - shell ejected -
pump forward, two hard clacks), shell_in (a shell pushed into the tube / ejection port)."""
import sys
import numpy as np
sys.argv = [sys.argv[0]]
src = open('/home/user/tools/make_sounds_r22.py').read().split('write("rifle_ak"')[0]
exec(src)


def gun12(D, boom_hz, clatter, seed_mix):
    n = int(D * SR); x = np.zeros(n)
    x += hp(np.concatenate([noise(0.003) * env(0.003, 0.0001, 0.001), np.zeros(n - int(0.003 * SR))]), 1800) * 0.35
    x += lp(noise(D), boom_hz, 3) * env(D, 0.0012, 0.07) * 4.2                        # big low muzzle blast
    x += bp(noise(D), 300, 1600) * env(D, 0.0008, 0.035) * 1.2
    if clatter:
        c = np.zeros(n); j = int(0.035 * SR)
        cl = bp(noise(0.03), 1500, 5500) * env(0.03, 0.0002, 0.006)
        c[j:j + len(cl)] += cl * 0.35
        x += c
    x += (lp(noise(D), 700, 2) * env(D, 0.03, 0.45) * 0.4 + bp(noise(D), 120, 450) * env(D, 0.01, 0.7) * 0.3) * seed_mix
    return norm(np.tanh(norm(x) * 2.4), 0.97)


def click(f1, f2, dec, amp=1.0, d=0.06):
    return bp(noise(d), f1, f2) * env(d, 0.0002, dec) * amp


def seq(parts, D):
    n = int(D * SR); x = np.zeros(n)
    for at, s in parts:
        j = int(at * SR); e = min(n, j + len(s)); x[j:e] += s[:e - j]
    return x


write("shotgun_saiga", gun12(1.0, 300, True, 1.0))
write("shotgun_870", gun12(1.3, 240, False, 1.2))
write("pump_action", norm(seq([(0.0, bp(noise(0.07), 700, 3500) * env(0.07, 0.005, 0.03) * 0.5),      # slide back
                               (0.075, click(400, 2600, 0.02, 1.0)),                                   # rear stop clack
                               (0.11, click(2500, 8000, 0.015, 0.35)),                                 # hull ejected
                               (0.20, click(900, 3000, 0.03, 0.3, 0.12)),                              # hull lands
                               (0.24, bp(noise(0.06), 700, 3500) * env(0.06, 0.005, 0.025) * 0.5),     # slide forward
                               (0.30, click(500, 3200, 0.02, 1.0))], 0.45), 0.9))                      # locks
write("shell_in", norm(seq([(0.0, bp(noise(0.05), 1200, 5000) * env(0.05, 0.003, 0.02) * 0.6),
                            (0.05, click(1500, 6000, 0.012, 0.9))], 0.14), 0.8))
