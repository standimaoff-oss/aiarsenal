#!/usr/bin/env python3
"""Generates the AI-soldier skins (64x64, vanilla player UV layout).

The NPC renderer uses the vanilla player model, so each army only needs one flat skin: helmet,
tunic, trousers and boots in the army's field colours, plus a face. Re-run after changing a palette:

    python3 tools/npc/make_soldier_skins.py
"""
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "..",
                   "src", "main", "resources", "assets", "aiarsenal", "textures", "entity", "soldier")

W = H = 64

PALETTES = {
    "nato":     dict(helmet=(58, 74, 48), tunic=(96, 105, 74), trousers=(84, 92, 66), boots=(38, 34, 30),
                     skin=(198, 152, 114), accent=(52, 66, 44), belt=(44, 40, 34)),
    "russia":   dict(helmet=(70, 82, 52), tunic=(104, 116, 78), trousers=(92, 102, 70), boots=(34, 32, 28),
                     skin=(206, 164, 126), accent=(60, 72, 46), belt=(40, 38, 32)),
    "ussr":     dict(helmet=(74, 80, 52), tunic=(112, 108, 78), trousers=(96, 94, 70), boots=(40, 36, 28),
                     skin=(204, 160, 120), accent=(130, 46, 40), belt=(46, 40, 32)),
    "germany":  dict(helmet=(62, 66, 58), tunic=(92, 96, 84), trousers=(76, 78, 70), boots=(30, 30, 28),
                     skin=(206, 166, 130), accent=(44, 46, 42), belt=(34, 34, 30)),
    "france":   dict(helmet=(38, 46, 78), tunic=(58, 74, 130), trousers=(228, 224, 208), boots=(34, 30, 26),
                     skin=(204, 162, 124), accent=(196, 176, 74), belt=(52, 48, 44)),
    "rus_empire": dict(helmet=(40, 46, 44), tunic=(52, 78, 58), trousers=(226, 222, 206), boots=(32, 28, 24),
                       skin=(204, 162, 124), accent=(186, 158, 70), belt=(50, 46, 40)),
}


def blank():
    return [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]


def rect(px, x0, y0, x1, y1, col):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[y][x] = (col[0], col[1], col[2], 255)


def px_set(px, x, y, col):
    px[y][x] = (col[0], col[1], col[2], 255)


def skin(p):
    px = blank()
    helmet, tunic, trousers, boots = p["helmet"], p["tunic"], p["trousers"], p["boots"]
    skin_c, accent, belt = p["skin"], p["accent"], p["belt"]

    # --- head: helmet shell, face on the front
    rect(px, 8, 0, 16, 8, helmet)          # top
    rect(px, 16, 0, 24, 8, (30, 28, 26))   # underside
    rect(px, 0, 8, 8, 16, helmet)          # right side
    rect(px, 16, 8, 24, 16, helmet)        # left side
    rect(px, 24, 8, 32, 16, helmet)        # back
    rect(px, 8, 8, 16, 16, skin_c)         # face
    rect(px, 8, 8, 16, 10, accent)         # cap band across the brow
    px_set(px, 10, 11, (28, 26, 24))
    px_set(px, 13, 11, (28, 26, 24))
    rect(px, 10, 14, 14, 15, (150, 96, 84))  # mouth
    # helmet brim (hat overlay layer, front only)
    rect(px, 40, 8, 48, 11, (max(0, helmet[0] - 14), max(0, helmet[1] - 14), max(0, helmet[2] - 14)))

    # --- torso: tunic with a belt and a chest strap
    rect(px, 16, 20, 20, 32, tunic)        # right side
    rect(px, 20, 20, 28, 32, tunic)        # front
    rect(px, 28, 20, 32, 32, tunic)        # left side
    rect(px, 32, 20, 40, 32, tunic)        # back
    rect(px, 20, 16, 28, 20, tunic)        # shoulders (top face)
    rect(px, 28, 16, 36, 20, (60, 56, 48))  # neck / underside
    rect(px, 20, 29, 28, 32, belt)          # belt, front
    rect(px, 32, 29, 40, 32, belt)          # belt, back
    rect(px, 22, 21, 24, 29, accent)        # chest strap
    rect(px, 26, 21, 27, 29, accent)

    # --- arms (right 40..56/16..32, left 32..48/48..64)
    for ax, ay in ((40, 16), (32, 48)):
        rect(px, ax, ay, ax + 16, ay + 16, tunic)
        rect(px, ax + 4, ay, ax + 12, ay + 4, tunic)      # shoulder top
        rect(px, ax + 8, ay, ax + 12, ay + 4, (60, 56, 48))
        rect(px, ax, ay + 12, ax + 16, ay + 16, skin_c)   # hands
        rect(px, ax + 4, ay + 4, ax + 8, ay + 12, accent) if ax == 40 else None

    # --- legs (right 0..16/16..32, left 16..32/48..64)
    for lx, ly in ((0, 16), (16, 48)):
        rect(px, lx, ly, lx + 16, ly + 16, trousers)
        rect(px, lx + 4, ly, lx + 12, ly + 4, trousers)
        rect(px, lx + 8, ly, lx + 12, ly + 4, (60, 56, 48))
        rect(px, lx, ly + 13, lx + 16, ly + 16, boots)
        rect(px, lx + 4, ly + 4, lx + 8, ly + 13, (max(0, trousers[0] - 12), max(0, trousers[1] - 12),
                                                   max(0, trousers[2] - 12)))
    return px


def write_png(path, px):
    raw = bytearray()
    for row in px:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, palette in PALETTES.items():
        write_png(os.path.join(OUT, name + ".png"), skin(palette))
        print("wrote", os.path.join(OUT, name + ".png"))


if __name__ == "__main__":
    main()
