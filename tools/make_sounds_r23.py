#!/usr/bin/env python3
"""Procedural sounds for reply 23 (no downloaded audio): rifle_t5000 (.338 Lapua Magnum, 700 mm barrel + 3-port brake,
heavy boom and long tail), mg_pkp (7.62x54R from the 658 mm barrel, slotted flash hider), bolt_cycle (T-5000 bolt: lift,
pull back with the case ejecting, push, lock down)."""
import importlib.util, sys
sys.argv = [sys.argv[0]]
src = open('/home/user/tools/make_sounds_r22.py').read().split('write("rifle_ak"')[0]
exec(src)
write("rifle_t5000", shot(1.6, 1900, 300, True, 1.5))
write("mg_pkp", shot(0.8, 2300, 380, False, 0.9))


def click(f1, f2, dec, amp=1.0, d=0.06):
    return bp(noise(d), f1, f2) * env(d, 0.0002, dec) * amp


def seq(parts, D):
    n = int(D * SR); x = np.zeros(n)
    for at, s in parts:
        j = int(at * SR); e = min(n, j + len(s)); x[j:e] += s[:e - j]
    return x


write("bolt_cycle", norm(seq([(0.0, click(900, 4000, 0.01, 0.8)),                                     # handle up
                              (0.18, bp(noise(0.16), 900, 4500) * env(0.16, 0.01, 0.07) * 0.5),        # pull back
                              (0.33, click(2500, 8000, 0.01, 0.5)),                                    # case tinkles out
                              (0.40, click(3500, 9000, 0.02, 0.25)),
                              (0.50, bp(noise(0.12), 900, 4500) * env(0.12, 0.01, 0.05) * 0.45),       # push forward
                              (0.66, click(500, 3000, 0.015, 1.0)), (0.72, click(800, 3500, 0.01, 0.8))], 0.85), 0.9))
