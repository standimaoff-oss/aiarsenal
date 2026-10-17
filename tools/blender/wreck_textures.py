#!/usr/bin/env python3
"""r17 (building blocks part 4): procedural textures for wrecks, loot crates, traps and the new supply items.
Drawn by the agent with numpy + PIL - no third-party images.
Output: textures/block/wreck_*.png, crate_*.png, trap_*.png (64x64 tileable per block unless noted) and
textures/item/{ration_irp,bandage,first_aid_kit}.png (16x16)."""
import os, math
import numpy as np
from PIL import Image, ImageDraw

SRC = open("/home/user/tools/blender/fort_textures.py").read()
exec(SRC[:SRC.index("def burlap")])          # tnoise, fbm, save (writes into OUT)
ITEM = OUT.replace("/block", "/item")
S = 64


def grid():
    return np.mgrid[0:S, 0:S]


def lines_mask(seed, n, Lmin, Lmax, vertical=0.3, width=1, val=(90, 200)):
    im = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(im)
    r = np.random.default_rng(seed)
    for _ in range(n):
        x, y = r.uniform(0, S, 2)
        a = r.uniform(-0.6, 0.6) + (math.pi / 2 if r.random() < vertical else 0)
        L = r.uniform(Lmin, Lmax)
        for ox in (-S, 0, S):                    # tileable strokes
            for oy in (-S, 0, S):
                d.line([(x + ox, y + oy), (x + ox + math.cos(a) * L, y + oy + math.sin(a) * L)], fill=int(r.uniform(*val)), width=width)
    return np.asarray(im, float) / 255.0


def scorch(img, seed, soot=0.6, rust=0.5, peel=0.4, base_metal=(70, 62, 56)):
    """Fire damage: paint peeled to heat-blued / rusty steel, soot streaks running down (v = up in block UV)."""
    n1 = fbm(S, 4, 4, seed)
    n2 = fbm(S, 8, 3, seed + 7)
    m_peel = np.clip((n1 - (0.35 - peel * 0.45)) * 4, 0, 1)[..., None]
    metal = np.array(base_metal, float) + fbm(S, 16, 2, seed + 3)[..., None] * 14
    img = img * (1 - m_peel) + metal * m_peel
    m_rust = np.clip((n2 - (0.5 - rust * 0.35)) * 4, 0, 1)[..., None] * m_peel
    rustc = np.array([118, 58, 28], float) + fbm(S, 16, 2, seed + 5)[..., None] * 24
    img = img * (1 - m_rust) + rustc * m_rust
    y, x = grid()
    streak = (tnoise(S, 16, seed + 11)[0][None, :].repeat(S, 0) * 0.5 + 0.5)      # vertical soot runs
    streak = np.roll(streak, 0, 0) * (0.6 + 0.4 * (fbm(S, 2, 2, seed + 13) * 0.5 + 0.5))
    m_soot = np.clip((streak - 0.35) * soot * 0.9 + (fbm(S, 4, 3, seed + 17) * 0.5 + 0.1) * soot * 0.7, 0, 1)[..., None]
    img = img * (1 - 0.75 * m_soot) + np.array([18, 16, 15]) * 0.75 * m_soot
    return img


def painted(rgb, seed, grain=9.0):
    img = np.ones((S, S, 3)) * np.array(rgb, float)
    img += fbm(S, 4, 4, seed)[..., None] * grain + tnoise(S, 32, seed + 3)[..., None] * grain * 0.35
    return img


# ================================================================== WRECKS
# tank: Russian green 4BO, ERA blocks, scorched
t = painted((74, 84, 50), 1701)
save(scorch(t, 1702, soot=0.55, rust=0.5, peel=0.45), "wreck_tank_paint.png")
# heavily burnt steel (inside of the hull, turret ring, engine bay)
b = painted((52, 46, 42), 1703, grain=12)
save(scorch(b, 1704, soot=0.75, rust=0.75, peel=1.0, base_metal=(64, 52, 46)), "wreck_burnt_steel.png")
# rust
r = painted((112, 58, 30), 1705, grain=18)
r = r * (1 - np.clip(fbm(S, 8, 3, 1706) * 0.8, 0, 1)[..., None] * 0.4)
r += lines_mask(1707, 10, 3, 9, vertical=0.9)[..., None] * -30
save(r, "wreck_rust.png")
# soot / ash (ground stains, burnt interior)
a = painted((26, 24, 22), 1708, grain=10)
ash = np.clip((fbm(S, 8, 3, 1709) - 0.25) * 3, 0, 1)[..., None]
a = a * (1 - ash * 0.6) + np.array([98, 94, 88]) * ash * 0.6
save(a, "wreck_soot.png")
# Mi-24: two-tone green / sand-brown disruptive camo + scorched
y, x = grid()
camo = fbm(S, 2, 3, 1710) + 0.25 * np.sin(x / S * 2 * math.pi * 1 + y / S * 2 * math.pi)
hp = np.where(camo[..., None] > 0.05, np.array([86, 96, 58], float), np.array([136, 112, 70], float))
hp += fbm(S, 8, 3, 1711)[..., None] * 8
save(scorch(hp, 1712, soot=0.45, rust=0.35, peel=0.35, base_metal=(96, 96, 92)), "wreck_heli_paint.png")
# jet: air-superiority grey-blue with scorch
jp = painted((128, 140, 150), 1713, grain=6)
jp = np.where((fbm(S, 2, 3, 1714) > 0.12)[..., None], jp * 0.86 + np.array([10, 12, 18]), jp)
save(scorch(jp, 1715, soot=0.5, rust=0.25, peel=0.3, base_metal=(150, 150, 146)), "wreck_plane_paint.png")
# track shoes: 4 links per block (along v), centre guide horns, rubber-less steel, rust
tr = np.zeros((S, S, 3))
for k in range(4):
    v0 = k * 16
    tr[v0:v0 + 16] = np.array([58, 52, 46])
    tr[v0 + 1:v0 + 3] = np.array([92, 82, 70])                    # grouser bar highlight
    tr[v0 + 13:v0 + 16] = np.array([30, 28, 26])                  # gap between links
    tr[v0 + 4:v0 + 12, 28:36] = np.array([80, 72, 64])            # guide horn
tr += fbm(S, 8, 3, 1716)[..., None] * 12
rm = np.clip((fbm(S, 8, 3, 1717) - 0.1) * 3, 0, 1)[..., None]
tr = tr * (1 - rm * 0.6) + np.array([110, 56, 28]) * rm * 0.6
save(tr, "wreck_track.png")
# burnt rubber (road wheel tyres, heli tyres)
ru = painted((34, 33, 32), 1718, grain=8)
ru += lines_mask(1719, 12, 2, 6, vertical=0.0)[..., None] * 25
save(ru, "wreck_rubber.png")
# interior: dark grey-green paint, burnt
it = painted((70, 82, 74), 1720, grain=8)
save(scorch(it, 1721, soot=0.7, rust=0.3, peel=0.3, base_metal=(60, 58, 56)), "wreck_interior.png")
# broken glass (alpha): crazed cockpit glazing with holes
g = np.ones((S, S, 3)) * np.array([150, 170, 176], float) + fbm(S, 4, 3, 1722)[..., None] * 14
al = np.full((S, S), 120.0)
cr = lines_mask(1723, 26, 6, 22, vertical=0.4, val=(200, 255))
g = g * (1 - cr[..., None] * 0.5) + 230 * cr[..., None] * 0.5
al = np.maximum(al, cr * 230)
hole = np.clip((fbm(S, 4, 3, 1724) - 0.28) * 8, 0, 1)
al = al * (1 - hole)
soot = np.clip(fbm(S, 4, 3, 1725) * 1.2, 0, 1)
g = g * (1 - soot[..., None] * 0.6)
save(g, "wreck_glass.png", alpha=al)
# heat-blued exhaust / nozzle metal
nz = painted((84, 80, 92), 1726, grain=10)
nz = nz + np.sin(grid()[0] / S * 2 * math.pi * 3)[..., None] * np.array([12, 4, 18])
save(scorch(nz, 1727, soot=0.4, rust=0.2, peel=0.0), "wreck_nozzle.png")

# ================================================================== CRATES
# wooden ammo box: 3 boards per block, Soviet green paint, chipped
cw = np.zeros((S, S, 3))
for k in range(3):
    lo, hi = k * S // 3, (k + 1) * S // 3
    cw[lo:hi] = np.array([70, 88, 52]) + np.random.default_rng(1730 + k).uniform(-6, 6)
cw += fbm(S, 4, 4, 1731)[..., None] * 8
cw += np.sin(grid()[1] / S * 2 * math.pi * 7 + fbm(S, 4, 3, 1732) * 5)[..., None] * 4      # grain along u
for k in range(1, 3):
    cw[k * S // 3] *= 0.5
chip = np.clip((fbm(S, 8, 3, 1733) - 0.45) * 6, 0, 1)[..., None]
cw = cw * (1 - chip) + np.array([132, 112, 84]) * chip
save(cw, "crate_wood_green.png")
# plain bare wood (pallet, weapon case battens)
pw = np.ones((S, S, 3)) * np.array([150, 118, 78], float) + fbm(S, 4, 4, 1734)[..., None] * 10
pw += np.sin(grid()[1] / S * 2 * math.pi * 9 + fbm(S, 4, 3, 1735) * 6)[..., None] * 6
for k in range(1, 4):
    pw[k * 16] *= 0.6
save(pw, "crate_wood_bare.png")
# olive ribbed metal (military transport case)
om = painted((86, 92, 60), 1736, grain=7)
om += (np.sin(grid()[0] / S * 2 * math.pi * 8) > 0.6)[..., None] * 10
om = om * (1 - lines_mask(1737, 8, 3, 8)[..., None] * 0.3)
save(om, "crate_olive_metal.png")
# white enamel (medical)
wm = painted((220, 222, 214), 1738, grain=5)
wm = wm * (1 - lines_mask(1739, 6, 2, 7)[..., None] * 0.25)
save(wm, "crate_white.png")
# green fibreglass (weapons case)
fg = painted((58, 72, 46), 1740, grain=6)
fg += tnoise(S, 64, 1741)[..., None] * 5
save(fg, "crate_fiberglass.png")
# black hard case (secret), pebbled plastic
bk = painted((30, 30, 32), 1742, grain=4)
bk += tnoise(S, 64, 1743)[..., None] * 6
save(bk, "crate_black.png")
# latch / hinge metal
lm = painted((150, 150, 144), 1744, grain=10)
lm = lm * (1 - lines_mask(1745, 10, 2, 6)[..., None] * 0.35)
save(lm, "crate_latch.png")
# rope handle
rp = np.zeros((S, S, 3))
y, x = grid()
tw = (np.sin((x + y * 1.0) / S * 2 * math.pi * 8) * 0.5 + 0.5)
rp[:] = np.array([140, 116, 76])[None, None] * (0.75 + 0.35 * tw[..., None])
save(rp, "crate_rope.png")
# decal sheet (alpha, 128): red cross, stencil numbers, hazard stripe, radiation trefoil, red band
D = 128
dec = Image.new("RGBA", (D, D), (0, 0, 0, 0))
d = ImageDraw.Draw(dec)
d.rectangle([4, 4, 60, 60], fill=(255, 255, 255, 255))                       # (0..0.5, 0.5..1) red cross on white
d.rectangle([24, 10, 40, 54], fill=(196, 28, 30, 255))
d.rectangle([10, 24, 54, 40], fill=(196, 28, 30, 255))
for i, txt in enumerate(["7.62x39", "1x440", "OFS 125"]):                     # (0.5..1, 0.5..1) stencil lines
    yy = 6 + i * 19
    xx = 68
    for ch in txt:                                                            # blocky stencil glyphs
        if ch == " ":
            xx += 5
            continue
        d.rectangle([xx, yy, xx + 3, yy + 11], fill=(232, 226, 200, 235)) if ch in "17I" else \
            d.rectangle([xx, yy, xx + 4, yy + 11], outline=(232, 226, 200, 235), width=1)
        xx += 7
for k in range(-4, 8):                                                        # (0..0.5, 0..0.5) hazard stripes
    d.polygon([(k * 12, 127), (k * 12 + 6, 127), (k * 12 + 6 + 60, 64), (k * 12 + 60, 64)], fill=(230, 180, 20, 255))
d.rectangle([0, 64, 63, 127], outline=None)
hz = Image.new("RGBA", (64, 64), (24, 24, 24, 255))
hz.paste(dec.crop((0, 64, 64, 128)), (0, 0), dec.crop((0, 64, 64, 128)))
dec.paste(hz, (0, 64))
d = ImageDraw.Draw(dec)
d.rectangle([64, 64, 127, 127], fill=(176, 24, 26, 255))                      # (0.5..1, 0..0.5) red band + white star
cx, cy = 96, 96
pts = []
for i in range(10):
    rr = 22 if i % 2 == 0 else 9
    aa = -math.pi / 2 + i * math.pi / 5
    pts.append((cx + rr * math.cos(aa), cy + rr * math.sin(aa)))
d.polygon(pts, fill=(240, 236, 228, 255))
dec.save(os.path.join(OUT, "crate_decals.png"))
print("wrote crate_decals.png")

# ================================================================== TRAPS
# bamboo stakes
bb = np.zeros((S, S, 3))
bb[:] = np.array([176, 160, 96])
y, x = grid()
bb *= (0.85 + 0.15 * np.sin(x / S * 2 * math.pi * 4))[..., None]
for k in range(4):
    bb[k * 16:k * 16 + 2] *= 0.6                                              # nodes
bb += fbm(S, 8, 3, 1750)[..., None] * 10
dirt = np.clip(fbm(S, 4, 3, 1751) - 0.2, 0, 1)[..., None]
bb = bb * (1 - dirt * 0.5) + np.array([70, 52, 34]) * dirt * 0.5
save(bb, "trap_bamboo.png")
# leaf / grass cover mat (alpha edges)
lf = np.ones((S, S, 3)) * np.array([88, 112, 52], float)
lf += fbm(S, 8, 3, 1752)[..., None] * 26
lf = np.where((fbm(S, 16, 2, 1753) > 0.25)[..., None], np.array([118, 96, 54], float) + fbm(S, 8, 2, 1754)[..., None] * 16, lf)
lal = np.where(fbm(S, 16, 2, 1755) > -0.45, 255, 0)
save(lf, "trap_leafmat.png", alpha=lal)
# galvanised spikes / chain
sp = painted((160, 164, 166), 1756, grain=12)
save(sp, "trap_spikes.png")
# IED: rusty artillery shell body
ied = painted((92, 84, 56), 1757, grain=10)
ied = scorch(ied, 1758, soot=0.1, rust=0.8, peel=0.6, base_metal=(96, 70, 50))
save(ied, "trap_shell.png")
# red / blue / yellow wire colours in thirds (u)
wi = np.zeros((S, S, 3))
wi[:, :21] = (180, 30, 26)
wi[:, 21:42] = (40, 70, 180)
wi[:, 42:] = (210, 180, 30)
wi += fbm(S, 8, 2, 1759)[..., None] * 10
save(wi, "trap_wire_colors.png")
# signal mine: olive tube
sm = painted((92, 100, 64), 1760, grain=8)
save(sm, "trap_olive.png")
# ceramic insulator (white glaze)
ins = painted((226, 224, 214), 1761, grain=4)
ins += np.sin(grid()[0] / S * 2 * math.pi * 6)[..., None] * 8
save(ins, "trap_insulator.png")
# yellow "danger high voltage" plate (lightning bolt)
pl = Image.new("RGB", (64, 64), (230, 190, 30))
d = ImageDraw.Draw(pl)
d.rectangle([1, 1, 62, 62], outline=(20, 20, 20), width=3)
d.polygon([(36, 6), (20, 34), (31, 34), (24, 58), (44, 26), (33, 26), (40, 6)], fill=(20, 20, 20))
plv = np.asarray(pl, float) + fbm(S, 8, 2, 1762)[..., None] * 6
save(plv, "trap_voltage_plate.png")

# ================================================================== ITEMS (16x16, alpha)


def icon(name, draw):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw(ImageDraw.Draw(im))
    im.save(os.path.join(ITEM, name))
    print("wrote item", name)


def ration(d):
    d.rectangle([2, 3, 13, 13], fill=(118, 104, 66, 255), outline=(62, 54, 34, 255))   # IRP box
    d.rectangle([2, 3, 13, 5], fill=(92, 80, 50, 255))
    d.rectangle([4, 7, 11, 10], fill=(214, 206, 170, 255))                             # label
    d.line([5, 8, 10, 8], fill=(60, 54, 40, 255))
    d.line([5, 9, 8, 9], fill=(60, 54, 40, 255))
    d.point([12, 12], fill=(160, 140, 90, 255))


def bandage(d):
    d.ellipse([2, 4, 11, 13], fill=(236, 232, 222, 255), outline=(170, 166, 156, 255))
    d.ellipse([5, 7, 8, 10], fill=(200, 196, 186, 255))
    d.polygon([(9, 9), (14, 6), (15, 8), (11, 12)], fill=(236, 232, 222, 255), outline=(170, 166, 156, 255))


def aid(d):
    d.rectangle([2, 4, 13, 13], fill=(70, 86, 50, 255), outline=(40, 50, 30, 255))     # olive pouch
    d.rectangle([5, 2, 10, 4], fill=(40, 50, 30, 255))                                 # handle
    d.rectangle([5, 6, 10, 11], fill=(236, 236, 230, 255))
    d.rectangle([7, 6, 8, 11], fill=(196, 28, 30, 255))
    d.rectangle([5, 8, 10, 9], fill=(196, 28, 30, 255))


icon("ration_irp.png", ration)
icon("bandage.png", bandage)
icon("first_aid_kit.png", aid)
