#!/usr/bin/env python3
"""r16 building blocks part 3: doors, hatches, multi-block gates, camouflage screens / canopies / bushes.
Generates blockstates, block/item models, loot tables, tool tags, lang (en/ru), recipes, the BUILDING creative tab line
and the Java registration (between >>> r16 doors markers). Idempotent: python3 tools/gen_doors_r16.py"""
import json, os, re, collections
M = "/home/user/mods/aiarsenal/src/main"
A = M + "/resources/assets/aiarsenal"
D = M + "/resources/data"
J = M + "/java/ru/aiforge/realwar"
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
DIRS = ["north", "east", "south", "west"]
DK = "ru.aiforge.realwar.deco."
TEXMAP = json.load(open(A + "/models/block/door/door_texmap.json"))
B = collections.OrderedDict()
K = {}


def texs(particle, over=None):
    t = {k: "aiarsenal:block/" + v for k, v in TEXMAP.items()}
    for k, v in (over or {}).items():
        t[k] = "aiarsenal:block/" + v
    t["particle"] = "aiarsenal:block/" + particle
    return t


def obj_model(name, obj, particle, rt="minecraft:cutout", over=None):
    m = {"parent": "minecraft:block/block", "loader": "forge:obj", "model": "aiarsenal:models/block/door/%s.obj" % obj,
         "flip_v": True, "automatic_culling": False, "shade_quads": True, "emissive_ambient": True,
         "mtl_override": "aiarsenal:models/block/door/door.mtl", "textures": texs(particle, over), "render_type": rt}
    json.dump(m, open(A + "/models/block/%s.json" % name, "w"), indent=2)
    return "aiarsenal:block/" + name


def empty_model(name, particle):
    json.dump({"textures": {"particle": "aiarsenal:block/" + particle}, "elements": []}, open(A + "/models/block/%s.json" % name, "w"), indent=2)
    return "aiarsenal:block/" + name


def _bbox(obj):
    vs = [list(map(float, l.split()[1:4])) for l in open(A + "/models/block/door/%s.obj" % obj) if l.startswith("v ")]
    return [min(v[i] for v in vs) for i in range(3)], [max(v[i] for v in vs) for i in range(3)]


def _rot(rx, ry, v):
    """JOML rotationXYZ(rx, ry, 0) applied to v (ItemTransform order), degrees."""
    import math
    x, y, z = v
    c, s_ = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    x, z = x * c + z * s_, -x * s_ + z * c
    c, s_ = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    y, z = y * c - z * s_, y * s_ + z * c
    return x, y, z


def _disp(lo, hi, rot, base, extra=(0, 0, 0)):
    """Scale so the largest model dimension reads like a 1-block item, and translate so the bbox centre is centred."""
    dim = max(hi[i] - lo[i] for i in range(3))
    s = base / max(1.0, dim)
    c = [(lo[i] + hi[i]) / 2 - 0.5 for i in range(3)]
    r = _rot(rot[0], rot[1], c)
    t = [round(max(-80, min(80, -16 * s * r[0] + extra[0])), 3), round(max(-80, min(80, -16 * s * r[1] + extra[1])), 3), extra[2]]
    return {"rotation": list(rot), "translation": t, "scale": [round(s, 4)] * 3}


def item_obj(bid, obj, scale, particle, rt="minecraft:cutout", ty=0.0, over=None, rot=(30, 225, 0)):
    lo, hi = _bbox(obj)
    gui_base = 1.0 if max(hi[i] - lo[i] for i in range(3)) > 1.2 else 0.68
    m = {"parent": "minecraft:block/block", "loader": "forge:obj", "model": "aiarsenal:models/block/door/%s.obj" % obj, "flip_v": True,
         "automatic_culling": False, "mtl_override": "aiarsenal:models/block/door/door.mtl", "textures": texs(particle, over), "render_type": rt,
         "display": {"gui": _disp(lo, hi, rot, gui_base),
                     "ground": _disp(lo, hi, (0, 0, 0), 0.3, (0, 3, 0)),
                     "fixed": _disp(lo, hi, (0, 180, 0), 0.8),
                     "thirdperson_righthand": _disp(lo, hi, (75, 45, 0), 0.375, (0, 2.5, 0)),
                     "firstperson_righthand": _disp(lo, hi, (0, 45, 0), 0.4),
                     "firstperson_lefthand": _disp(lo, hi, (0, 225, 0), 0.4)}}
    json.dump(m, open(A + "/models/item/%s.json" % bid, "w"), indent=2)


def bs(bid, data):
    json.dump(data, open(A + "/blockstates/%s.json" % bid, "w"), indent=2)


def rot(model, y, x=0):
    v = {"model": model}
    if y:
        v["y"] = y
    if x:
        v["x"] = x
    return v


P_DOOR = 'BlockBehaviour.Properties.of().mapColor(MapColor.%s).strength(%sf, %sf).sound(SoundType.%s).noOcclusion()%s'
P_GATE = 'BlockBehaviour.Properties.of().mapColor(MapColor.%s).strength(%sf, %sf).sound(SoundType.%s).noOcclusion().dynamicShape().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)'
P_NET = 'BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.4f).sound(SoundType.WOOL).noOcclusion().ignitedByLava()'
P_CAN = 'BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.3f).sound(SoundType.WOOL).noOcclusion().noCollission().ignitedByLava()'
P_BUSH = 'BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2f).sound(SoundType.GRASS).noOcclusion().noCollission().offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava()'
IRON = ".requiresCorrectToolForDrops()"

# ---------------------------------------------------------------- doors (frame in the blockstate, leaf by the BER)
DOORS = [
    ("door_steel", "steel", "STEEL", "COLOR_GREEN", 6, 30, "METAL", "door_steel_olive", "cutout", True,
     "Armoured steel door", "Стальная бронедверь",
     "Riveted 7 cm olive leaf in a steel frame. Opens by hand or redstone.", "Клёпаное полотно 7 см в стальной коробке. Открывается рукой или редстоуном."),
    ("door_bunker", "bunker", "BUNKER", "COLOR_GRAY", 12, 120, "NETHERITE_BLOCK", "door_steel_grey", "cutout", True,
     "Bunker pressure door", "Гермодверь бункера",
     "16 cm sealed leaf with clamp dogs: the wheel spins first, then the heavy leaf swings (2 s). Very blast-resistant.",
     "Герметичное полотно 16 см с задрайками: сначала вращается штурвал, затем тяжело открывается створка (2 с). Очень стойкая к взрывам."),
    ("door_bars", "bars", "BARS", "METAL", 5, 30, "METAL", "door_dark_steel", "cutout", True,
     "Bar door", "Решётчатая дверь", "Steel bar door for cells, cages and armouries.", "Решётчатая стальная дверь: камеры, клетки, оружейные."),
    ("door_wood_army", "wood", "WOOD", "COLOR_GREEN", 3, 3, "WOOD", "door_wood_green", "translucent", False,
     "Army barracks door", "Армейская деревянная дверь", "Green-painted plank door with a four-light window.", "Дощатая дверь, крашенная в зелёный, с окном на 4 стекла."),
    ("door_lab", "lab", "LAB", "SNOW", 5, 20, "METAL", "door_lab_white", "translucent", True,
     "Laboratory door", "Лабораторная дверь", "White steel door with a porthole, hazard kick band and push bar.",
     "Белая стальная дверь с иллюминатором, сигнальной полосой и штангой."),
]
for bid, key, kind, col, hard, res, snd, part, rt, iron, en, ru, den, dru in DOORS:
    fl = obj_model("door/%s_frame" % key if False else "r16_%s_frame" % key, "door_%s_frame" % key, part)
    fr = obj_model("r16_%s_frame_r" % key, "door_%s_frame_r" % key, part)
    obj_model("r16_%s_leaf_l" % key, "door_%s_leaf_l" % key, part, "minecraft:" + rt)
    obj_model("r16_%s_leaf_r" % key, "door_%s_leaf_r" % key, part, "minecraft:" + rt)
    emp = empty_model("r16_empty_" + key, part)
    var = {}
    for f in DIRS:
        for h in ("left", "right"):
            var["facing=%s,half=lower,hinge=%s" % (f, h)] = rot(fl if h == "left" else fr, ROT[f])
            var["facing=%s,half=upper,hinge=%s" % (f, h)] = {"model": emp}
    bs(bid, {"variants": var})
    item_obj(bid, "door_%s_item" % key, 0, part, "minecraft:" + rt, rot=(15, 210, 0))
    snds = "IRON" if snd != "WOOD" else "OAK"
    B[bid] = dict(ctor=f"new {DK}AnimDoorBlock({DK}DoorKind.{kind}, {P_DOOR % (col, hard, res, snd, IRON if iron else '')})",
                  en=en, ru=ru, den=den, dru=dru, tool="pickaxe" if iron else "axe", iron=iron, door=True)
if True:
    obj_model("r16_bunker_wheel", "door_bunker_wheel", "door_paint_red")

# ---------------------------------------------------------------- hatches (frame in the blockstate, lid by the BER)
HATCHES = [
    ("hatch_steel", "steel", "HATCH_STEEL", "METAL", 5, 30, "door_dark_steel", "Steel floor hatch", "Стальной люк",
     "Chequer-plate hatch: floors, decks, cellars. Opens by hand or redstone.", "Рифлёный стальной люк: пол, палуба, погреб. Рукой или редстоуном."),
    ("hatch_bunker", "bunker", "HATCH_BUNKER", "COLOR_GRAY", 10, 120, "door_steel_grey", "Round bunker hatch", "Круглый люк бункера",
     "Sealed hatch with a handwheel: the wheel turns, then the lid lifts.", "Герметичный люк со штурвалом: сначала штурвал, затем поднимается крышка."),
    ("hatch_grate", "grate", "HATCH_GRATE", "METAL", 4, 20, "door_dark_steel", "Grating hatch", "Решётчатый люк",
     "Steel bar grating: drains, vents, shafts.", "Решётка из стальной полосы: ливнёвки, вентиляция, шахты."),
]
for bid, key, kind, col, hard, res, part, en, ru, den, dru in HATCHES:
    frm = obj_model("r16_hatch_%s_frame" % key, "hatch_%s_frame" % key, part)
    obj_model("r16_hatch_%s_lid" % key, "hatch_%s_lid" % key, part)
    var = {}
    for f in DIRS:
        var["facing=%s,half=bottom" % f] = rot(frm, ROT[OPP[f]])
        var["facing=%s,half=top" % f] = rot(frm, ROT[OPP[f]], 180)
    bs(bid, {"variants": var})
    item_obj(bid, "hatch_%s_item" % key, 0.6, part, ty=1.5)
    B[bid] = dict(ctor=f"new {DK}AnimHatchBlock({DK}DoorKind.{kind}, {P_DOOR % (col, hard, res, 'METAL', IRON)})",
                  en=en, ru=ru, den=den, dru=dru, tool="pickaxe", iron=True, door=True)
obj_model("r16_hatch_bunker_wheel", "hatch_bunker_wheel", "door_paint_red")

# ---------------------------------------------------------------- gates (master + invisible filler parts of the same block)
GATES = [
    ("gate_sliding", "sliding", "SLIDING", 5, 2, ["leaf"], "METAL", 5, 30, "door_dark_steel", 0.2, -1.0,
     "Sliding gate (5x2)", "Откатные ворота (5×2)",
     "Cantilever gate with chain-link and barbed wire; needs 5 free blocks to the left to roll into. Right-click or redstone on any part.",
     "Консольные ворота с сеткой-рабицей и колючей проволокой; откатываются влево (нужно 5 свободных блоков). ПКМ или редстоун по любой части."),
    ("gate_swing", "swing", "SWING", 4, 3, ["leaf_l", "leaf_r"], "COLOR_GREEN", 6, 30, "door_steel_olive", 0.22, -2.0,
     "Unit gates with red stars (4x3)", "Ворота воинской части со звёздами (4×3)",
     "Two leaves on concrete posts; they swing away from you.", "Две створки на бетонных столбах, открываются от вас."),
    ("gate_boom", "boom", "BOOM", 5, 1, ["arm"], "COLOR_RED", 3, 10, "gate_boom", 0.2, 2.0,
     "Boom barrier", "Шлагбаум", "Checkpoint barrier with a 4.4 m striped arm. The arm swings up.", "Шлагбаум КПП со стрелой 4,4 м, стрела поднимается вверх."),
    ("gate_blast", "blast", "BLAST", 3, 3, ["leaf_l", "leaf_r"], "COLOR_GRAY", 14, 200, "door_steel_grey", 0.26, -2.0,
     "Blast gate (3x3)", "Противовзрывные ворота (3×3)",
     "40 cm steel halves slide into the walls (4 s). Very blast-resistant.", "Стальные половины 40 см уходят в стены (4 с). Очень стойкие к взрывам."),
]
for bid, key, kind, W, H, parts, col, hard, res, part, sc, ty, en, ru, den, dru in GATES:
    st = obj_model("r16_gate_%s_static" % key, "gate_%s_static" % key, part)
    for pn in parts:
        obj_model("r16_gate_%s_%s" % (key, pn), "gate_%s_%s" % (key, pn), part)
    emp = empty_model("r16_empty_gate_" + key, part)
    mp = [{"when": {"facing": f, "ox": "0", "oy": "0"}, "apply": rot(st, ROT[f])} for f in DIRS]
    bs(bid, {"multipart": mp})
    item_obj(bid, "gate_%s_item" % key, 0, part, rot=(20, 200, 0))
    B[bid] = dict(ctor=f"new {DK}GateBlock({DK}GateKind.{kind}, {P_GATE % (col, hard, res, 'METAL')}{IRON})",
                  en=en, ru=ru, den=den, dru=dru, tool="pickaxe", iron=True, gate=True)

# ---------------------------------------------------------------- camouflage
CAMO = [("forest", "Forest", "лесная", "camo_net_forest", "camo_leaf_forest"), ("desert", "Desert", "пустынная", "camo_net_desert", "camo_leaf_dry"),
        ("winter", "Winter", "зимняя", "camo_net_winter", "camo_leaf_snow")]
for c, cen, cru, ntex, ltex in CAMO:
    over = {"net": ntex, "leaf": ltex}
    post = obj_model("r16_net_post_" + c, "net_post", ntex, over=over)
    side = obj_model("r16_net_side_" + c, "net_side", ntex, over=over)
    poles = {"OR": [{"north": "false", "east": "false"}, {"north": "false", "west": "false"}, {"south": "false", "east": "false"}, {"south": "false", "west": "false"}]}
    mp = [{"when": poles, "apply": {"model": post}}]
    for f in DIRS:
        mp.append({"when": {f: "true"}, "apply": rot(side, ROT[f])})
    iso = {"north": "false", "east": "false", "south": "false", "west": "false"}
    mp.append({"when": iso, "apply": rot(side, 0)})
    mp.append({"when": iso, "apply": rot(side, 180)})
    bid = "camo_screen_" + c
    bs(bid, {"multipart": mp})
    item_obj(bid, "net_item", 0.75, ntex, over=over, rot=(30, 135, 0))
    B[bid] = dict(ctor=f"new {DK}CamoNetBlock({P_NET})", en="Camouflage screen (%s)" % cen.lower(), ru="Маскировочная сеть-завеса (%s)" % cru,
                  den="Hangs between poles and links up like a fence; stops line of sight, not bullets.",
                  dru="Натягивается между шестами и соединяется как забор; скрывает обзор, но не защищает.", tool="hoe")
    cen_m = obj_model("r16_canopy_center_" + c, "canopy_center", ntex, over=over)
    fr = obj_model("r16_canopy_fringe_" + c, "canopy_fringe", ntex, over=over)
    mp = [{"apply": {"model": cen_m}}] + [{"when": {f: "false"}, "apply": rot(fr, ROT[f])} for f in DIRS]
    bid = "camo_canopy_" + c
    bs(bid, {"multipart": mp})
    item_obj(bid, "canopy_item", 0.75, ntex, over=over, ty=-2.0)
    B[bid] = dict(ctor=f"new {DK}CamoCanopyBlock({P_CAN})", en="Camouflage canopy (%s)" % cen.lower(), ru="Маскировочный навес (%s)" % cru,
                  den="Place it in the air at roof height: neighbours join into one sagging net, a fringe hangs on the open edges. You can walk under and through it.",
                  dru="Ставится в воздухе на высоте крыши: соседние блоки сливаются в одну провисающую сеть, по открытым краям свисает бахрома. Под ней и сквозь неё можно ходить.",
                  tool="hoe")
    bm = obj_model("r16_bush_" + c, "bush", ltex, over=over)
    bid = "camo_bush_" + {"forest": "forest", "desert": "dry", "winter": "snow"}[c]
    bs(bid, {"variants": {"": [rot(bm, y) for y in (0, 90, 180, 270)]}})
    item_obj(bid, "bush", 0.9, ltex, over=over, ty=0.5)
    B[bid] = dict(ctor=f"new {DK}CamoBushBlock({P_BUSH})",
                  en={"forest": "Camouflage bush", "desert": "Camouflage bush (dry)", "winter": "Camouflage bush (snow)"}[c],
                  ru={"forest": "Маскировочный куст", "desert": "Маскировочный куст (сухой)", "winter": "Маскировочный куст (заснеженный)"}[c],
                  den="Artificial bush for hiding positions and vehicles; you can walk through it and crouch inside.",
                  dru="Искусственный куст для маскировки позиций и техники; сквозь него можно пройти и спрятаться присев.", tool="hoe")

# ---------------------------------------------------------------- loot, tags, lang
for bid, v in B.items():
    pool = {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": "aiarsenal:" + bid}]}
    if v.get("door"):
        if bid.startswith("door"):
            pool["conditions"].append({"condition": "minecraft:block_state_property", "block": "aiarsenal:" + bid, "properties": {"half": "lower"}})
    if v.get("gate"):
        pool["conditions"].append({"condition": "minecraft:block_state_property", "block": "aiarsenal:" + bid, "properties": {"ox": "0", "oy": "0"}})
    json.dump({"type": "minecraft:block", "pools": [pool]}, open(D + "/aiarsenal/loot_tables/blocks/%s.json" % bid, "w"), indent=2)
TAGS = [("minecraft", "blocks/mineable/pickaxe", lambda v: v["tool"] == "pickaxe"), ("minecraft", "blocks/mineable/axe", lambda v: v["tool"] == "axe"),
        ("minecraft", "blocks/mineable/hoe", lambda v: v["tool"] == "hoe"), ("minecraft", "blocks/needs_iron_tool", lambda v: v.get("iron")),
        ("minecraft", "blocks/doors", lambda v: v.get("door") and "hatch" not in "x"), ]
for ns, tag, sel in TAGS[:4]:
    p = D + "/%s/tags/%s.json" % (ns, tag)
    t = json.load(open(p)) if os.path.exists(p) else {"replace": False, "values": []}
    for bid, v in B.items():
        if sel(v) and "aiarsenal:" + bid not in t["values"]:
            t["values"].append("aiarsenal:" + bid)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(t, open(p, "w"), indent=2)
for bid, v in B.items():
    K["block.aiarsenal." + bid] = (v["en"], v["ru"])
    K["block.aiarsenal." + bid + ".desc1"] = (v["den"], v["dru"])
K["msg.aiarsenal.gate.blocked"] = ("Not enough room for the gate: it needs %s x %s free blocks to the right and up.",
                                   "Не хватает места для ворот: нужно %s×%s свободных блоков вправо и вверх.")
for i, f in enumerate(("en_us.json", "ru_ru.json")):
    d = json.load(open(A + "/lang/" + f), object_pairs_hook=collections.OrderedDict)
    for k, v in K.items():
        d[k] = v[i]
    json.dump(d, open(A + "/lang/" + f, "w"), ensure_ascii=False, indent=2)
    open(A + "/lang/" + f, "a").write("\n")

# ---------------------------------------------------------------- recipes
I = lambda x: {"item": x}
dye = lambda c: I("minecraft:%s_dye" % c)
OUTR = D + "/aiarsenal/recipes"
S, IN, N, BAR, GP = I("aiarsenal:steel_plate"), I("minecraft:iron_ingot"), I("minecraft:iron_nugget"), I("minecraft:iron_bars"), I("minecraft:glass_pane")
REC = {
    "door_steel": (["SS", "SN", "SS"], {"S": S, "N": N}, 1),
    "door_bunker": (["SS", "SR", "SS"], {"S": S, "R": dye("red")}, 1),
    "door_bars": (["BB", "BN", "BB"], {"B": BAR, "N": N}, 1),
    "door_wood_army": (["PG", "PD", "PP"], {"P": I("minecraft:spruce_planks"), "G": GP, "D": dye("green")}, 1),
    "door_lab": (["IG", "IW", "IY"], {"I": IN, "G": GP, "W": dye("white"), "Y": dye("yellow")}, 1),
    "hatch_steel": (["SNS", "SSS"], {"S": S, "N": N}, 2),
    "hatch_bunker": (["SRS", "SSS"], {"S": S, "R": dye("red")}, 1),
    "hatch_grate": (["BNB", "BBB"], {"B": BAR, "N": N}, 2),
    "gate_sliding": (["WWW", "BBB", "IBI"], {"W": I("aiarsenal:barbed_wire"), "B": BAR, "I": IN}, 1),
    "gate_swing": (["SRS", "SSS", "C C"], {"S": S, "R": dye("red"), "C": I("minecraft:smooth_stone")}, 1),
    "gate_boom": (["RWR", "I  ", "C  "], {"R": dye("red"), "W": dye("white"), "I": IN, "C": I("minecraft:smooth_stone")}, 1),
    "gate_blast": (["SYS", "SOS", "SYS"], {"S": S, "Y": dye("yellow"), "O": I("minecraft:obsidian")}, 1),
}
NET = I("aiarsenal:camo_net")
SHAPELESS = {}
for c, d in (("forest", "green"), ("desert", "yellow"), ("winter", "white")):
    SHAPELESS["camo_screen_" + c] = ([NET, I("minecraft:stick"), dye(d)], 2)
    SHAPELESS["camo_canopy_" + c] = ([NET, I("minecraft:string"), dye(d)], 2)
SHAPELESS["camo_bush_forest"] = ([I("minecraft:oak_leaves"), I("minecraft:stick"), dye("green")], 2)
SHAPELESS["camo_bush_dry"] = ([I("minecraft:oak_leaves"), I("minecraft:stick"), dye("brown")], 2)
SHAPELESS["camo_bush_snow"] = ([I("minecraft:oak_leaves"), I("minecraft:stick"), I("minecraft:snowball")], 2)
lang = json.load(open(A + "/lang/en_us.json"))
sig = set()
for rid, (pat, key, cnt) in REC.items():
    assert set("".join(pat)) - {" "} == set(key), rid
    assert len({len(r) for r in pat}) == 1, rid
    s = json.dumps([[key.get(ch, None) for ch in row] for row in pat], sort_keys=True)
    assert s not in sig, ("dup", rid)
    sig.add(s)
    json.dump({"type": "minecraft:crafting_shaped", "category": "building", "pattern": pat, "key": key,
               "result": {"item": "aiarsenal:" + rid, "count": cnt}}, open(OUTR + "/%s.json" % rid, "w"), indent=2)
for rid, (ing, cnt) in SHAPELESS.items():
    s = json.dumps(sorted(json.dumps(x, sort_keys=True) for x in ing))
    assert s not in sig, ("dup", rid)
    sig.add(s)
    for x in ing:
        if x["item"].startswith("aiarsenal:"):
            n = x["item"].split(":")[1]
            assert n in B or "item.aiarsenal." + n in lang or "block.aiarsenal." + n in lang, n
    json.dump({"type": "minecraft:crafting_shapeless", "category": "building", "ingredients": ing,
               "result": {"item": "aiarsenal:" + rid, "count": cnt}}, open(OUTR + "/%s.json" % rid, "w"), indent=2)
for f in os.listdir(OUTR):
    rid = f[:-5]
    if rid in REC or rid in SHAPELESS:
        continue
    r = json.load(open(OUTR + "/" + f))
    if r.get("type") == "minecraft:crafting_shaped":
        s = json.dumps([[r["key"].get(ch, None) for ch in row] for row in r["pattern"]], sort_keys=True)
        assert s not in sig, ("clash with existing", rid)
    elif r.get("type") == "minecraft:crafting_shapeless":
        s = json.dumps(sorted(json.dumps(x, sort_keys=True) for x in r["ingredients"]))
        assert s not in sig, ("clash with existing", rid)

# ---------------------------------------------------------------- Java registration
const = lambda b: b.upper()
blk = ["    // >>> r16 doors (tools/gen_doors_r16.py)"]
for bid, v in B.items():
    blk.append('    public static final RegistryObject<Block> %s = BLOCKS.register("%s", () -> %s);' % (const(bid), bid, v["ctor"]))
blk.append("    // <<< r16 doors")
itm = ["    // >>> r16 doors (tools/gen_doors_r16.py)"]
for bid, v in B.items():
    itm.append('    public static final RegistryObject<Item> %s = ModItems.blockItem("%s", ModBlocks.%s);' % (const(bid), bid, const(bid)))
itm.append("    // <<< r16 doors")


def put(path, anchor, lines):
    s = open(path).read()
    s = re.sub(r"    // >>> r16 doors.*?// <<< r16 doors\n", "", s, flags=re.S)
    assert s.count(anchor) == 1, (path, anchor)
    s = s.replace(anchor, anchor + "\n".join(lines) + "\n")
    open(path, "w").write(s)


put(J + "/registry/ModBlocks.java", "    // <<< r14 deco\n", blk)
put(J + "/registry/ModItems.java", "    // <<< r14 deco\n", itm)
doors = [b for b, v in B.items() if v.get("door")]
gates = [b for b, v in B.items() if v.get("gate")]
be = ["    // >>> r16 doors (tools/gen_doors_r16.py)",
      '    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.AnimDoorBlockEntity>> ANIM_DOOR = BLOCK_ENTITIES.register("anim_door", () -> BlockEntityType.Builder.of(ru.aiforge.realwar.deco.AnimDoorBlockEntity::new, new Block[]{%s}).build(null));'
      % ", ".join("ModBlocks.%s.get()" % const(b) for b in doors),
      '    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.GateBlockEntity>> GATE = BLOCK_ENTITIES.register("gate", () -> BlockEntityType.Builder.of(ru.aiforge.realwar.deco.GateBlockEntity::new, new Block[]{%s}).build(null));'
      % ", ".join("ModBlocks.%s.get()" % const(b) for b in gates),
      "    // <<< r16 doors"]
bep = J + "/registry/ModBlockEntities.java"
s = open(bep).read()
s = re.sub(r"    // >>> r16 doors.*?// <<< r16 doors\n", "", s, flags=re.S)
anchor = "    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.SearchlightBlockEntity>> SEARCHLIGHT"
assert s.count(anchor) == 1
i = s.index(anchor)
j = s.index("\n", i) + 1
s = s[:j] + "\n".join(be) + "\n" + s[j:]
open(bep, "w").write(s)
tp = J + "/registry/ModTabs.java"
s = open(tp).read()
s = re.sub(r"        // >>> r16 doors.*?// <<< r16 doors\n", "", s, flags=re.S)
anchor = "        ModTabs.put(Cat.WEAPONS,"
assert s.count(anchor) == 1
ids = ", ".join('"%s"' % b for b in B)
s = s.replace(anchor, "        // >>> r16 doors\n        ModTabs.put(Cat.BUILDING, %s);\n        // <<< r16 doors\n%s" % (ids, anchor))
open(tp, "w").write(s)
print("r16 blocks", len(B), "lang", len(K), "recipes", len(REC) + len(SHAPELESS))
