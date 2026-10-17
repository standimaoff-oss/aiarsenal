#!/usr/bin/env python3
"""Procedural paint textures for the handheld weapons (numpy + PIL, drawn by the agent). Output: tools/blender/tex_hand/*.png"""
import os, numpy as np
from PIL import Image
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tex_hand")
os.makedirs(OUT, exist_ok=True)
r = np.random.default_rng(7)

def noise(S, cells, seed):
    g = np.random.default_rng(seed).uniform(-1, 1, (cells, cells))
    y, x = np.mgrid[0:S, 0:S] / S * cells
    x0 = np.floor(x).astype(int); y0 = np.floor(y).astype(int)
    fx = x - x0; fy = y - y0; fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
    x1 = (x0 + 1) % cells; y1 = (y0 + 1) % cells; x0 %= cells; y0 %= cells
    return (g[y0, x0] * (1 - fx) + g[y0, x1] * fx) * (1 - fy) + (g[y1, x0] * (1 - fx) + g[y1, x1] * fx) * fy

def fbm(S, seed):
    return sum(noise(S, 4 * 2 ** i, seed + i) * 0.5 ** i for i in range(5)) / 1.9

def paint(name, col, wear=(70, 70, 66), seed=1, wamt=0.06, S=256):
    n = fbm(S, seed)
    img = np.array(col, np.float32) * (1 + n[..., None] * 0.08)
    chips = fbm(S, seed + 50) > (1 - wamt * 6)
    img[chips] = np.array(wear, np.float32) * (1 + n[chips][..., None] * 0.1)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(os.path.join(OUT, name)); print(name)

paint("T_Olive.png", (86, 92, 58), seed=1)
paint("T_OliveDark.png", (62, 68, 44), seed=2)
paint("T_Tan.png", (150, 136, 100), wear=(96, 90, 72), seed=3)
paint("T_Black.png", (34, 34, 34), wear=(60, 60, 58), seed=4, wamt=0.03)
paint("T_Gunmetal.png", (58, 60, 62), wear=(110, 112, 114), seed=5, wamt=0.08)
paint("T_Glass.png", (40, 52, 70), wear=(70, 90, 110), seed=6, wamt=0.02, S=64)
paint("T_Rubber.png", (26, 26, 24), wear=(40, 40, 38), seed=8, wamt=0.01, S=64)
