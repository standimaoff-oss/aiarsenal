#!/usr/bin/env python3
"""r16 QA scene: doors in a concrete wall, floor hatches, the four gates, camouflage screens / canopy / bushes.
   python3 tools/scene_doors.py build | open | close   (open/close = redstone blocks under the ground)"""
import sys, subprocess
R = lambda c: subprocess.run(["python3", "/home/user/tools/rcon.py", c], capture_output=True, text=True).stdout.strip()
Y = -60
DOORS = [("door_steel", 0), ("door_bunker", 3), ("door_bars", 6), ("door_wood_army", 9), ("door_lab", 12)]
HATCH = [("hatch_steel", 16), ("hatch_bunker", 18), ("hatch_grate", 20)]
GATES = [("gate_sliding", 0, 5, 2), ("gate_swing", 9, 4, 3), ("gate_boom", 16, 5, 1), ("gate_blast", 25, 3, 3)]
ZD, ZG, ZC = 40, 50, 60


def build():
    R("kill @e[type=!player]")
    R("forceload add -16 24 48 80")
    R("fill -12 -63 30 40 -62 72 minecraft:dirt")
    R("fill -12 -61 30 40 -61 72 minecraft:grass_block")
    for y in range(-60, -45, 3):                                  # fill limit 32768 blocks
        R(f"fill -12 {y} 30 40 {y+2} 72 minecraft:air")
    R("weather clear"); R("gamerule doDaylightCycle false"); R("time set 6000"); R("gamerule doMobSpawning false")
    # doors in a concrete wall
    for bid, x in DOORS:
        R(f"setblock {x} {Y} {ZD} air"); R(f"setblock {x} {Y+1} {ZD} air")
        R(f"setblock {x} {Y} {ZD} aiarsenal:{bid}[facing=north,half=lower,hinge=left,open=false]")
        R(f"setblock {x} {Y+1} {ZD} aiarsenal:{bid}[facing=north,half=upper,hinge=left,open=false]")
    print(R(f"fill -2 {Y} {ZD} 14 {Y+2} {ZD} minecraft:light_gray_concrete replace minecraft:air"))
    for bid, x in HATCH:
        R(f"setblock {x} {Y} {ZD - 3} aiarsenal:{bid}[facing=north,half=bottom,open=false]")
    # gates (facing north = extends east)
    for bid, x0, w, h in GATES:
        for i in range(w):
            for j in range(h):
                R(f"setblock {x0 + i} {Y + j} {ZG} aiarsenal:{bid}[facing=north,ox={i},oy={j},open=false,powered=false]")
    R(f"fill 22 {Y} {ZG} 24 {Y+2} {ZG} minecraft:gray_concrete")
    R(f"fill 28 {Y} {ZG} 30 {Y+2} {ZG} minecraft:gray_concrete")
    R(f"fill 25 {Y+3} {ZG} 27 {Y+3} {ZG} minecraft:gray_concrete")
    # camouflage: three screen lines (E-W), a 4x3 canopy on fence posts, bushes
    for k, (c, x0) in enumerate((("forest", 0), ("desert", 5), ("winter", 10))):
        for i in range(4):
            e, w = "true" if i < 3 else "false", "true" if i > 0 else "false"
            R(f"setblock {x0 + i} {Y} {ZC} aiarsenal:camo_screen_{c}[east={e},west={w},north=false,south=false]")
    cx0, cz0 = 16, ZC - 1
    for i in range(4):
        for j in range(3):
            st = "north=%s,south=%s,west=%s,east=%s" % (str(j > 0).lower(), str(j < 2).lower(), str(i > 0).lower(), str(i < 3).lower())
            R(f"setblock {cx0 + i} {Y + 2} {cz0 + j} aiarsenal:camo_canopy_forest[{st}]")
    for (x, z) in ((cx0, cz0), (cx0 + 3, cz0), (cx0, cz0 + 2), (cx0 + 3, cz0 + 2)):
        R(f"fill {x} {Y} {z} {x} {Y+1} {z} minecraft:spruce_fence")
    R(f"fill {cx0+1} {Y} {cz0+1} {cx0+2} {Y} {cz0+1} aiarsenal:sandbags")
    for (x, z, b) in ((22, 59, "forest"), (23, 60, "forest"), (22, 61, "dry"), (24, 59, "dry"), (25, 61, "snow"), (24, 61, "snow"), (23, 62, "forest")):
        R(f"setblock {x} {Y} {z} aiarsenal:camo_bush_{b}")
    print("SCENE_OK")


def power(on):
    """Photo helper: switch the blocks through their state (the redstone path is covered by the gametests)."""
    o = "true" if on else "false"
    for bid, x in DOORS[1:]:
        R(f"setblock {x} {Y+1} {ZD} minecraft:air")              # halves copy each other on placement -> lower first
        R(f"setblock {x} {Y} {ZD} aiarsenal:{bid}[facing=north,half=lower,hinge=left,open={o}]")
        R(f"setblock {x} {Y+1} {ZD} aiarsenal:{bid}[facing=north,half=upper,hinge=left,open={o}]")
    for bid, x in HATCH:
        R(f"setblock {x} {Y} {ZD - 3} aiarsenal:{bid}[facing=north,half=bottom,open={o}]")
    for bid, x0, w, h in GATES:
        for i in range(w):
            for j in range(h):
                R(f"setblock {x0 + i} {Y + j} {ZG} aiarsenal:{bid}[facing=north,ox={i},oy={j},open={o},powered=false]")
    print("POWER", on)


{"build": build, "open": lambda: power(True), "close": lambda: power(False)}[sys.argv[1]]()
