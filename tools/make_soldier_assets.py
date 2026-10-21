#!/usr/bin/env python3
"""Reproducible textures for the AI soldier units (tools convention: byte-identical output).

* skin_steve.png            64x64  base soldier skin in the classic Steve palette
                            (the player's own head and body shape is the base).
* uniform_<class>.png       64x64  transparent uniform overlay drawn OVER the skin
                            (tunic, sleeves, trousers, boots, headgear per class).
* egg_<class>.png           16x16  summon-egg item icons, one colour per class.

Run:  python3 tools/make_soldier_assets.py
"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'mods/aiarsenal/src/main/resources/assets/aiarsenal/textures'


def write_png(path: Path, w: int, h: int, px):
    """px: list of rows, each row = list of (r,g,b,a). 8-bit RGBA, byte-identical."""
    def chunk(tag: bytes, data: bytes) -> bytes:
        out = struct.pack('>I', len(data)) + tag + data
        return out + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b''.join(b'\x00' + bytes(c for p in row for c in p) for row in px)
    data = (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9))
            + chunk(b'IEND', b''))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


# --------------------------------------------------------------------------- skin
# Classic Steve palette.
HAIR = (92, 64, 41, 255)
SKIN = (196, 148, 104, 255)
SKIN_SHADE = (178, 128, 88, 255)
WHITE = (255, 255, 255, 255)
EYE = (70, 110, 170, 255)
MOUTH = (120, 84, 58, 255)
SHIRT = (0, 150, 145, 255)
PANTS = (80, 60, 130, 255)
SHOES = (110, 110, 110, 255)


def blank(w, h, color=(0, 0, 0, 0)):
    return [[list(color) for _ in range(w)] for _ in range(h)]


def fill(img, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[y][x] = list(color)


def skin_steve():
    """64x64 humanoid skin, classic Steve."""
    img = blank(64, 64)
    # head: top (0,0,8,8), front (8,8,8,8), right (16,8,8,8), left (0,8,8,8), back (24,8,8,8)
    for region in [(0, 0, 8, 8), (8, 8, 16, 16), (16, 8, 24, 16), (0, 8, 8, 16), (24, 8, 32, 16)]:
        fill(img, *region, SKIN)
    # hair: whole head top + fringe rows 0-1 on front/sides/back + sideburns
    fill(img, 0, 0, 8, 8, HAIR)                      # top
    fill(img, 8, 8, 16, 10, HAIR)                    # front fringe
    fill(img, 16, 8, 24, 12, HAIR)                   # right side
    fill(img, 0, 8, 8, 12, HAIR)                     # left side
    fill(img, 24, 8, 32, 12, HAIR)                   # back
    # face on the front (8..16, 8..16): eyes rows 10-11, nose row 12-13, mouth row 13
    fill(img, 10, 10, 12, 12, WHITE)
    fill(img, 13, 10, 15, 12, WHITE)
    fill(img, 11, 10, 12, 12, EYE)
    fill(img, 13, 10, 14, 12, EYE)
    fill(img, 11, 12, 13, 13, SKIN_SHADE)            # nose
    fill(img, 11, 13, 13, 14, MOUTH)                 # mouth
    # body: top (16,16,8,2), bottom (24,16,8,2), front (16,18,8,6), right (24,18,8,6),
    #       left (32,18,8,6), back (40,18,8,6)
    for region in [(16, 16, 24, 18), (24, 16, 32, 18), (16, 18, 24, 24),
                   (24, 18, 32, 24), (32, 18, 40, 24), (40, 18, 48, 24)]:
        fill(img, *region, SHIRT)
    # arms (front/sides/back 12 tall, top 1): right arm (44,16..), left arm (36,48..)
    for x0, y0 in [(44, 16), (36, 48)]:
        fill(img, x0, y0, x0 + 4, y0 + 1, SKIN)                       # top
        fill(img, x0 + 12, y0, x0 + 16, y0 + 1, SKIN)                 # bottom
        for dx in (0, 4, 8, 12):                                      # front/right/left/back
            fill(img, x0 + dx, y0 + 1, x0 + dx + 4, y0 + 3, SHIRT)    # sleeve
            fill(img, x0 + dx, y0 + 3, x0 + dx + 4, y0 + 13, SKIN)    # forearm
    # legs: right leg (4,40..), left leg (20,32..)
    for x0, y0 in [(4, 40), (20, 32)]:
        fill(img, x0, y0, x0 + 4, y0 + 1, PANTS)                      # top
        fill(img, x0 + 12, y0, x0 + 16, y0 + 1, SHOES)                # bottom (sole)
        for dx in (0, 4, 8, 12):                                      # front/right/left/back
            fill(img, x0 + dx, y0 + 1, x0 + dx + 4, y0 + 11, PANTS)   # trousers
            fill(img, x0 + dx, y0 + 11, x0 + dx + 4, y0 + 13, SHOES)  # shoes
    return img


# --------------------------------------------------------------------------- uniforms
# Overlay palette per class: (tunic, sleeve_edge, trousers, boots, headgear, headgear_dark)
UNIFORMS = {
    'commander': dict(
        tunic=(168, 158, 112, 255), trim=(96, 88, 58, 255),
        pants=(120, 116, 88, 255), boots=(70, 60, 45, 255),
        hat=(64, 68, 74, 255), hat_brim=(38, 40, 46, 255)),
    'tanker': dict(
        tunic=(94, 100, 58, 255), trim=(74, 78, 46, 255),
        pants=(88, 94, 54, 255), boots=(85, 70, 50, 255),
        hat=(110, 116, 86, 255), hat_brim=(92, 96, 72, 255)),
    'assault': dict(
        tunic=(109, 111, 94, 255), trim=(86, 88, 74, 255),
        pants=(96, 98, 84, 255), boots=(70, 66, 54, 255),
        hat=(86, 90, 70, 255), hat_brim=(70, 74, 58, 255)),
    'medic': dict(
        tunic=(232, 232, 228, 255), trim=(200, 200, 194, 255),
        pants=(224, 224, 218, 255), boots=(160, 160, 156, 255),
        hat=(240, 240, 236, 255), hat_brim=(206, 206, 200, 255)),
}


def uniform(class_id: str):
    c = UNIFORMS[class_id]
    img = blank(64, 64)  # fully transparent: the Steve skin shows through
    # tunic: body front/back/left/right + a darker trim row at the hem
    for region in [(16, 18, 24, 24), (24, 18, 32, 24), (32, 18, 40, 24), (40, 18, 48, 24)]:
        fill(img, *region, c['tunic'])
    for x0 in (16, 24, 32, 40):
        fill(img, x0, 23, x0 + 8, 24, c['trim'])
    if class_id == 'commander':  # belt
        for x0 in (16, 24, 32, 40):
            fill(img, x0, 21, x0 + 8, 22, c['trim'])
    # sleeves: top 4 rows of every arm side (hands stay as skin)
    for x0, y0 in [(44, 16), (36, 48)]:
        for dx in (0, 4, 8, 12):
            fill(img, x0 + dx, y0 + 1, x0 + dx + 4, y0 + 5, c['tunic'])
    # trousers: leg sides down to the boots
    for x0, y0 in [(4, 40), (20, 32)]:
        fill(img, x0, y0, x0 + 4, y0 + 1, c['pants'])                 # top
        for dx in (0, 4, 8, 12):
            fill(img, x0 + dx, y0 + 1, x0 + dx + 4, y0 + 12, c['pants'])
            fill(img, x0 + dx, y0 + 12, x0 + dx + 4, y0 + 13, c['boots'])
        fill(img, x0 + 12, y0, x0 + 16, y0 + 1, c['boots'])
    # headgear over the hair: head top + 3 rows on front/sides/back
    fill(img, 0, 0, 8, 8, c['hat'])
    fill(img, 8, 8, 16, 11, c['hat'])
    fill(img, 16, 8, 24, 11, c['hat'])
    fill(img, 0, 8, 8, 11, c['hat'])
    fill(img, 24, 8, 32, 11, c['hat'])
    fill(img, 8, 10, 16, 11, c['hat_brim'])
    if class_id == 'medic':  # red cross on the front of the helmet + left arm band
        fill(img, 12, 8, 13, 10, (198, 46, 46, 255))   # vertical bar
        fill(img, 11, 8, 14, 9, (198, 46, 46, 255))    # horizontal bar
        fill(img, 38, 53, 39, 57, (198, 46, 46, 255))  # left arm: vertical
        fill(img, 37, 54, 40, 55, (198, 46, 46, 255))  # left arm: horizontal
    return img


# --------------------------------------------------------------------------- eggs
EGG_COLORS = {
    'commander': (176, 166, 118, 255),
    'tanker': (122, 132, 82, 255),
    'assault': (146, 150, 124, 255),
    'medic': (238, 238, 232, 255),
}


def egg(class_id: str):
    base = EGG_COLORS[class_id]
    dark = tuple(max(0, int(v * 0.62)) for v in base[:3]) + (255,)
    light = tuple(min(255, int(v * 0.6 + 92)) for v in base[:3]) + (255,)
    img = blank(16, 16)
    # egg: 8 wide, 10 tall, centred (x 4..12, y 3..13), pointier at the top
    for y in range(3, 14):
        t = (y - 3) / 9.0
        hw = 3.4 + 1.2 * (t ** 0.7)  # half width
        x0, x1 = 8 - hw, 8 + hw
        for x in range(int(x0), int(x1) + 1):
            if not (4 <= x < 12 and 3 <= y < 14):
                continue
            if abs(x - 8) > hw:
                continue
            col = base
            if y == 13 or abs(x - 8) >= hw - 0.2:
                col = dark
            elif y <= 4 and x <= 7:
                col = light
            img[y][x] = list(col)
    if class_id == 'medic':
        fill(img, 7, 8, 9, 11, (198, 46, 46, 255))
        fill(img, 8, 7, 9, 12, (198, 46, 46, 255))
    return img


def main():
    ent = OUT / 'entity/soldier'
    write_png(ent / 'skin_steve.png', 64, 64, skin_steve())
    for cls in UNIFORMS:
        write_png(ent / f'uniform_{cls}.png', 64, 64, uniform(cls))
    for cls in EGG_COLORS:
        write_png(OUT / 'item' / f'egg_{cls}.png', 16, 16, egg(cls))
    print('Soldier textures written to', ent)


if __name__ == '__main__':
    main()
