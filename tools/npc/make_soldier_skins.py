#!/usr/bin/env python3
"""Generates the AI-soldier skins (64x64, vanilla player UV layout).

The soldier's base is the classic Steve skin — his head, face, hair and hands stay exactly Steve's.
On top of that base every army wears its own field clothing: tunic over the shirt, trousers over the
jeans, boots over the shoes. The mod's UniformLayer draws the real garment pieces over this skin.

Re-run after changing a palette:  python3 tools/npc/make_soldier_skins.py
"""
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "..",
                   "src", "main", "resources", "assets", "aiarsenal", "textures", "entity", "soldier")

W = H = 64

# ---- classic Steve -------------------------------------------------------
STEVE_SKIN = (184, 128, 86)
STEVE_SKIN_DARK = (164, 110, 70)
STEVE_HAIR = (62, 36, 18)
STEVE_HAIR_DARK = (48, 27, 13)
STEVE_SHIRT = (0, 168, 168)
STEVE_PANTS = (62, 62, 172)
STEVE_SHOES = (104, 104, 104)
EYE_WHITE = (255, 255, 255)
EYE_IRIS = (70, 52, 168)
MOUTH = (140, 92, 58)

PALETTES = {
    "nato":       dict(tunic=(96, 105, 74), trousers=(84, 92, 66), boots=(38, 34, 30), belt=(44, 40, 34)),
    "russia":     dict(tunic=(104, 116, 78), trousers=(92, 102, 70), boots=(34, 32, 28), belt=(40, 38, 32)),
    "ussr":       dict(tunic=(112, 108, 78), trousers=(96, 94, 70), boots=(40, 36, 28), belt=(46, 40, 32)),
    "germany":    dict(tunic=(92, 96, 84), trousers=(76, 78, 70), boots=(30, 30, 28), belt=(34, 34, 30)),
    "france":     dict(tunic=(58, 74, 130), trousers=(228, 224, 208), boots=(34, 30, 26), belt=(52, 48, 44)),
    "rus_empire": dict(tunic=(52, 78, 58), trousers=(226, 222, 206), boots=(32, 28, 24), belt=(50, 46, 40)),
}


def blank():
    return [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]


def rect(px, x0, y0, x1, y1, col):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[y][x] = (col[0], col[1], col[2], 255)


def dot(px, x, y, col):
    px[y][x] = (col[0], col[1], col[2], 255)


def steve(px):
    """The full classic Steve skin in the 64x64 player layout."""
    # head (8x8x8 at 0,0): hair on top/back/sides, Steve's face in front
    rect(px, 8, 0, 16, 8, STEVE_HAIR)            # top
    rect(px, 16, 0, 24, 8, STEVE_HAIR_DARK)      # bottom
    rect(px, 0, 8, 32, 16, STEVE_SKIN)           # all sides skin first
    rect(px, 0, 8, 32, 10, STEVE_HAIR)           # hair line
    rect(px, 8, 8, 16, 16, STEVE_SKIN)           # face
    rect(px, 8, 8, 16, 10, STEVE_HAIR)           # fringe
    for x in (9, 10, 13, 14):                    # eyes: white + iris
        dot(px, x, 12, EYE_WHITE)
    dot(px, 10, 12, EYE_IRIS); dot(px, 13, 12, EYE_IRIS)
    rect(px, 11, 13, 13, 14, STEVE_SKIN_DARK)    # nose shadow
    rect(px, 11, 14, 13, 15, MOUTH)              # mouth
    # torso (20,16): teal shirt front/back/sides
    rect(px, 20, 16, 28, 20, STEVE_SHIRT)
    rect(px, 28, 16, 36, 20, STEVE_SHIRT)
    rect(px, 16, 20, 20, 32, STEVE_SHIRT)
    rect(px, 20, 20, 32, 32, STEVE_SHIRT)
    rect(px, 32, 20, 40, 32, STEVE_SHIRT)
    # right arm (40,16) and left arm (32,48): teal sleeve, skin hand
    for (ax, ay) in ((40, 16), (32, 48)):
        rect(px, ax + 4, ay, ax + 8, ay + 4, STEVE_SHIRT)
        rect(px, ax + 8, ay, ax + 12, ay + 4, STEVE_SKIN)
        rect(px, ax, ay + 4, ax + 4, ay + 16, STEVE_SHIRT)
        rect(px, ax + 4, ay + 4, ax + 12, ay + 16, STEVE_SHIRT)
        rect(px, ax + 12, ay + 4, ax + 16, ay + 16, STEVE_SHIRT)
        rect(px, ax + 4, ay + 13, ax + 12, ay + 16, STEVE_SKIN)   # hands stay Steve's
    # legs (4,16) and (20,48): blue jeans, grey shoes at the bottom
    for (lx, ly) in ((4, 16), (20, 48)):
        rect(px, lx + 4, ly, lx + 8, ly + 4, STEVE_PANTS)
        rect(px, lx + 8, ly, lx + 12, ly + 4, STEVE_SHOES)
        rect(px, lx, ly + 4, lx + 16, ly + 16, STEVE_PANTS)
        rect(px, lx, ly + 13, lx + 16, ly + 16, STEVE_SHOES)


def uniform(px, p):
    """Field clothing over Steve: tunic, belt, trousers, boots. Head and hands untouched."""
    tunic, trousers, boots, belt = p["tunic"], p["trousers"], p["boots"], p["belt"]
    # torso: tunic everywhere, belt line at the waist
    rect(px, 20, 16, 28, 20, tunic)
    rect(px, 28, 16, 36, 20, tunic)
    rect(px, 16, 20, 20, 32, tunic)
    rect(px, 20, 20, 32, 32, tunic)
    rect(px, 32, 20, 40, 32, tunic)
    rect(px, 16, 29, 20, 31, belt)
    rect(px, 20, 29, 32, 31, belt)
    rect(px, 32, 29, 40, 31, belt)
    # sleeves down to the wrist; Steve's hands stay bare below
    for (ax, ay) in ((40, 16), (32, 48)):
        rect(px, ax + 4, ay, ax + 8, ay + 4, tunic)
        rect(px, ax, ay + 4, ax + 4, ay + 13, tunic)
        rect(px, ax + 4, ay + 4, ax + 12, ay + 13, tunic)
        rect(px, ax + 12, ay + 4, ax + 16, ay + 13, tunic)
    # trousers and boots
    for (lx, ly) in ((4, 16), (20, 48)):
        rect(px, lx + 4, ly, lx + 8, ly + 4, trousers)
        rect(px, lx + 8, ly, lx + 12, ly + 4, boots)
        rect(px, lx, ly + 4, lx + 16, ly + 13, trousers)
        rect(px, lx, ly + 13, lx + 16, ly + 16, boots)


def write_png(px, path):
    raw = b""
    for y in range(H):
        raw += b"\x00" + b"".join(struct.pack("BBBB", *px[y][x]) for x in range(W))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, pal in PALETTES.items():
        px = blank()
        steve(px)
        uniform(px, pal)
        write_png(px, os.path.join(OUT, name + ".png"))
        print("wrote", name)


if __name__ == "__main__":
    main()
