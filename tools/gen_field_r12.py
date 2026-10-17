#!/usr/bin/env python3
"""r12 field blocks: blockstates, block/item models (forge:obj), loot tables, tool tags, lang, tab + Java registration.
Idempotent: python3 tools/gen_field_r12.py"""
import json, os, re, collections
M = "/home/user/mods/aiarsenal/src/main"
A = M + "/resources/assets/aiarsenal"
D = M + "/resources/data"
J = M + "/java/ru/aiforge/realwar"
TEX = ["sandbag", "sandbag_olive", "concrete", "concrete_dark", "steel", "wire", "wood", "camo_net", "hazard",
       "plank", "tin", "earth", "drum", "drum_top", "olive", "rubber", "rust", "mon50", "sign"]
FORT = {"sandbag", "sandbag_olive", "concrete", "concrete_dark", "steel", "wire", "wood", "camo_net", "hazard"}
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
CUT = {"egoza", "bruno_spiral"}

# id: (java ctor, models (obj names), texture overrides, particle, tool, name_en, name_ru, desc_en, desc_ru)
P_EARTH = 'BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.9f, 4.0f).sound(SoundType.ROOTED_DIRT)'
P_WOOD = 'BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0f, 3.0f).sound(SoundType.WOOD).noOcclusion()'
P_DRUM = 'BlockBehaviour.Properties.of().mapColor(MapColor.%s).strength(2.0f, 2.0f).sound(SoundType.METAL).noOcclusion()'
P_WIRE = 'BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f, 4.0f).sound(SoundType.CHAIN).noOcclusion().forceSolidOn()'
P_MINE = 'BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.6f, 0.0f).sound(SoundType.METAL).noOcclusion().noCollission().pushReaction(PushReaction.DESTROY)'
P_TRAP = 'BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 4.0f).sound(SoundType.CHAIN).noOcclusion().noCollission()'
P_SIGN = 'BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.2f, 4.0f).sound(SoundType.LANTERN).noOcclusion()'
F = "ru.aiforge.realwar.field."
B = collections.OrderedDict()
B["trench_wall"] = (f'new {F}TrenchBlock({F}TrenchBlock.Kind.WALL, {P_EARTH})', "facing", ["trench_wall"], {}, "plank", "shovel",
    "Trench wall (boards)", "Стенка траншеи (доски)",
    "Earth block revetted with boards on the trench side. The entrenching tool places them automatically.",
    "Земляной блок, обшитый досками со стороны траншеи. Сапёрная лопатка ставит их автоматически.")
B["trench_wall_tin"] = (f'new {F}TrenchBlock({F}TrenchBlock.Kind.WALL_TIN, {P_EARTH})', "facing", ["trench_wall_tin"], {}, "tin", "shovel",
    "Trench wall (corrugated sheet)", "Стенка траншеи (гофролист)", "Earth block held by corrugated steel and angle-iron pickets.",
    "Земляной блок, укреплённый гофролистом на уголковых кольях.")
B["duckboard"] = (f'new {F}TrenchBlock({F}TrenchBlock.Kind.DUCKBOARD, {P_WOOD})', "facing", ["duckboard"], {}, "plank", "axe",
    "Duckboard", "Настил (решётка)", "Wooden trench grating, keeps boots out of the mud.", "Деревянный настил на дно траншеи.")
B["firing_step"] = (f'new {F}TrenchBlock({F}TrenchBlock.Kind.STEP, {P_EARTH})', "facing", ["firing_step"], {}, "earth", "shovel",
    "Firing step", "Стрелковая ступень", "Half-height earth step with a plank riser for firing over the parapet.",
    "Земляная ступень в полблока с дощатым фасадом — для стрельбы через бруствер.")
for col, mc, kg, fire, en, ru, den, dru in (("olive", "COLOR_GREEN", 12.0, 3, "Diesel drum (200 l)", "Бочка ДТ (200 л)", "diesel", "дизтопливо"),
                                            ("red", "COLOR_RED", 20.0, 5, "Gasoline drum (200 l)", "Бочка бензина (200 л)", "gasoline", "бензин"),
                                            ("blue", "COLOR_BLUE", 6.0, 2, "Oil drum (200 l)", "Бочка масла (200 л)", "engine oil", "моторное масло")):
    B["fuel_drum_" + col] = (f'new {F}FuelDrumBlock({kg}, {fire}, {P_DRUM % mc})', "drum", ["drum_up", "drum_side"], {"drum": "field_drum_" + col}, "drum_top", "pickaxe",
        en, ru, f"200 l of {den}. Sneak to lay it on its side. Blasts, fire and burning arrows make it burst into flames.",
        f"200 л, {dru}. С Shift — положить на бок. Взрывается и горит от взрывов, огня и горящих стрел.")
B["egoza"] = (f'new ru.aiforge.realwar.fort.LinkedFortBlock(ru.aiforge.realwar.fort.LinkedFortBlock.Kind.EGOZA, {P_WIRE})', "linked_post", ["egoza_post", "egoza_side", "egoza_single"], {}, "wire", "pickaxe",
    "Egoza razor barrier", "Барьер «Егоза»", "Flat-pressed razor-tape spiral (ASKL). Joins like a fence, cuts and holds; vehicles crush it.",
    "Плоская спираль из армированной колючей ленты (АСКЛ). Соединяется как забор, режет и держит; техника давит.")
B["bruno_spiral"] = (f'new ru.aiforge.realwar.fort.LinkedFortBlock(ru.aiforge.realwar.fort.LinkedFortBlock.Kind.BRUNO, {P_WIRE})', "linked", ["bruno_side", "bruno_single"], {}, "wire", "pickaxe",
    "Bruno spiral", "Спираль Бруно", "Double razor coil with clipped crossing loops. The nastiest wire obstacle.",
    "Двойная спираль из колючей ленты со скреплёнными перекрещенными витками. Самое неприятное заграждение.")
for mid, kind, en, ru, den, dru in (
        ("mine_pmn2", "PMN2", "PMN-2 anti-personnel mine", "Мина ПМН-2", "Pressure mine, 100 g TNT-class charge. Arms 3 s after placing. Sneak + shears to disarm.",
         "Нажимная противопехотная мина. Взводится через 3 с. Обезвредить — Shift + ножницы."),
        ("mine_tm62", "TM62", "TM-62M anti-tank mine", "Мина ТМ-62М", "7.5 kg TNT, triggered only by vehicles and very heavy creatures.",
         "7,5 кг ТНТ, срабатывает только под техникой и очень тяжёлыми существами."),
        ("mine_mon50", "MON50", "MON-50 directional mine", "Мина МОН-50", "Points where you look. Break-wire 6 blocks ahead; 54° sheaf of 540 steel balls.",
         "Направлена туда, куда вы смотрите. Обрывной датчик на 6 блоков вперёд, сектор 54°, 540 стальных шариков."),
        ("mine_ozm72", "OZM72", "OZM-72 bounding mine", "Мина ОЗМ-72", "Jumps to waist height before bursting — lethal radius 25 blocks.",
         "Выпрыгивает на высоту пояса и рвётся — смертельный радиус 25 блоков."),
        ("tripwire_f1", "TRIPWIRE", "Tripwire with F-1 grenade", "Растяжка с Ф-1", "Wire runs 2 blocks to the right; the fuze burns 3-4 s after the pull.",
         "Проволока на 2 блока вправо; запал горит 3–4 с после срыва.")):
    model = {"mine_pmn2": "mine_pmn2", "mine_tm62": "mine_tm62", "mine_mon50": "mine_mon50", "mine_ozm72": "mine_ozm72", "tripwire_f1": "tripwire_f1"}[mid]
    B[mid] = (f'new {F}MineBlock({F}MineBlock.Kind.{kind}, {P_MINE})', "mine", [model], {}, "olive", None, en, ru, den, dru)
B["bear_trap"] = (f'new {F}BearTrapBlock({P_TRAP})', "trap", ["bear_trap_open", "bear_trap_closed"], {}, "rust", "pickaxe",
    "Bear trap", "Капкан", "Snaps shut on whoever steps on it and holds them. Right-click to open again.",
    "Захлопывается на наступившем и держит его. ПКМ — снова раскрыть.")
for sid, en, ru in (("mines", "Warning sign: MINES", "Знак «Мины»"), ("radiation", "Warning sign: radiation", "Знак «Радиация»"),
                    ("voltage", "Warning sign: high voltage", "Знак «Высокое напряжение»"), ("biohazard", "Warning sign: biohazard", "Знак «Биологическая опасность»"),
                    ("checkpoint", "Sign: STOP, checkpoint", "Знак «СТОП, КПП»"), ("noentry", "Sign: no entry", "Знак «Проход запрещён»")):
    B["warning_sign_" + sid] = (f'new {F}WarningSignBlock({P_SIGN})', "sign", ["sign_post", "sign_wall"], {"sign": "field_sign_" + sid}, "steel", "pickaxe",
        en, ru, "Place on the ground for a post sign, on a wall side for a wall plate.", "На землю — знак на столбе, на стену — табличка.")


def tex_map(over, particle):
    t = {}
    for k in TEX:
        t[k] = "aiarsenal:block/" + ("fort_" + k if k in FORT else "field_" + k)
    for k, v in over.items():
        t[k] = "aiarsenal:block/" + v
    t["particle"] = "aiarsenal:block/" + (over.get(particle) or ("fort_" + particle if particle in FORT else "field_" + particle))
    return t


def block_model(name, obj, over, particle, cut):
    m = {"parent": "minecraft:block/block", "loader": "forge:obj", "model": "aiarsenal:models/block/field/%s.obj" % obj,
         "flip_v": True, "automatic_culling": False, "shade_quads": True, "emissive_ambient": True,
         "mtl_override": "aiarsenal:models/block/field/field.mtl", "textures": tex_map(over, particle)}
    if cut:
        m["render_type"] = "minecraft:cutout"
    json.dump(m, open(A + "/models/block/%s.json" % name, "w"), indent=2)


def item_model(bid, obj, over, particle, cut, scale):
    m = {"parent": "minecraft:block/block", "loader": "forge:obj", "model": "aiarsenal:models/block/field/%s.obj" % obj, "flip_v": True,
         "automatic_culling": False, "mtl_override": "aiarsenal:models/block/field/field.mtl", "textures": tex_map(over, particle),
         "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [scale] * 3},
                     "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25] * 3},
                     "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [scale * 0.8] * 3},
                     "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
                     "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
                     "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4] * 3}}}
    if cut:
        m["render_type"] = "minecraft:cutout"
    json.dump(m, open(A + "/models/item/%s.json" % bid, "w"), indent=2)


def variants(fn, props):
    out = {}
    def rec(i, cur):
        if i == len(props):
            k = ",".join("%s=%s" % kv for kv in cur)
            out[k] = fn(dict(cur))
            return
        n, vals = props[i]
        for v in vals:
            rec(i + 1, cur + [(n, v)])
    rec(0, [])
    return {"variants": out}


FACE = ("facing", ["north", "east", "south", "west"])
GUI_SCALE = {"mine_pmn2": 1.6, "mine_tm62": 1.2, "mine_mon50": 1.4, "mine_ozm72": 1.6, "tripwire_f1": 0.5, "bear_trap": 0.9,
             "duckboard": 0.6, "egoza": 0.55, "bruno_spiral": 0.55}
for bid, (ctor, kind, objs, over, particle, tool, en, ru, den, dru) in B.items():
    cut = bid in CUT
    # one block model per obj (texture variants get their own name)
    names = {}
    for o in objs:
        n = "field_" + (o if not over else bid + "_" + o)
        names[o] = n
        block_model(n, o, over, particle, cut)
    if kind == "facing":
        bs = variants(lambda c: {"model": "aiarsenal:block/" + names[objs[0]], "y": ROT[c["facing"]]}, [FACE])
    elif kind == "drum":
        bs = variants(lambda c: {"model": "aiarsenal:block/" + names["drum_side" if c["lying"] == "true" else "drum_up"], "y": ROT[c["facing"]]},
                      [FACE, ("lying", ["false", "true"])])
    elif kind == "mine":
        bs = variants(lambda c: {"model": "aiarsenal:block/" + names[objs[0]], "y": ROT[c["facing"]]}, [FACE, ("armed", ["false", "true"])])
    elif kind == "trap":
        bs = variants(lambda c: {"model": "aiarsenal:block/" + names["bear_trap_open" if c["open"] == "true" else "bear_trap_closed"], "y": ROT[c["facing"]]},
                      [FACE, ("open", ["false", "true"])])
    elif kind == "sign":
        bs = variants(lambda c: {"model": "aiarsenal:block/" + names["sign_wall" if c["wall"] == "true" else "sign_post"], "y": ROT[c["facing"]]},
                      [FACE, ("wall", ["false", "true"])])
    else:                                        # linked wire
        pre = objs[0].split("_")[0]
        side = "aiarsenal:block/" + names[pre + "_side"]
        parts = [{"when": {"north": "false", "east": "false", "south": "false", "west": "false"}, "apply": {"model": "aiarsenal:block/" + names[pre + "_single"]}}]
        if kind == "linked_post":
            parts.append({"when": {"OR": [{"north": "true"}, {"east": "true"}, {"south": "true"}, {"west": "true"}]},
                          "apply": {"model": "aiarsenal:block/" + names[pre + "_post"]}})
        for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            parts.append({"when": {d: "true"}, "apply": {"model": side, "y": y} if y else {"model": side}})
        bs = {"multipart": parts}
    for v in bs.get("variants", {}).values():
        if v.get("y") == 0:
            del v["y"]
    json.dump(bs, open(A + "/blockstates/%s.json" % bid, "w"), indent=2)
    iobj = {"drum": "drum_up", "trap": "bear_trap_open", "sign": "sign_post", "linked": objs[-1], "linked_post": objs[-1]}.get(kind, objs[0])
    item_model(bid, iobj, over, particle, cut, GUI_SCALE.get(bid, 0.62 if kind != "sign" else 0.5))
    json.dump({"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
               "entries": [{"type": "minecraft:item", "name": "aiarsenal:" + bid}]}]},
              open(D + "/aiarsenal/loot_tables/blocks/%s.json" % bid, "w"), indent=2)

# tool tags
for tool in ("shovel", "pickaxe", "axe"):
    p = D + "/minecraft/tags/blocks/mineable/%s.json" % tool
    t = json.load(open(p)) if os.path.exists(p) else {"replace": False, "values": []}
    for bid, v in B.items():
        if v[5] == tool and "aiarsenal:" + bid not in t["values"]:
            t["values"].append("aiarsenal:" + bid)
    json.dump(t, open(p, "w"), indent=2)

# lang
K = {}
for bid, v in B.items():
    K["block.aiarsenal." + bid] = (v[6], v[7])
    K["block.aiarsenal." + bid + ".desc1"] = (v[8], v[9])
K["item.aiarsenal.entrenching_tool"] = ("MPL-50 entrenching tool", "Сапёрная лопатка МПЛ-50")
K["item.aiarsenal.entrenching_tool.desc1"] = ("Right-click the top of soil: digs a 2-deep trench segment with duckboard and revetted walls.",
                                             "ПКМ по верху грунта: копает сегмент траншеи глубиной 2 с настилом и обшитыми стенками.")
K["item.aiarsenal.entrenching_tool.desc2"] = ("Sneak + right-click: dig a single block.", "Shift + ПКМ: выкопать один блок.")
for i, f in enumerate(("en_us.json", "ru_ru.json")):
    d = json.load(open(A + "/lang/" + f), object_pairs_hook=collections.OrderedDict)
    for k, v in K.items():
        d[k] = v[i]
    json.dump(d, open(A + "/lang/" + f, "w"), ensure_ascii=False, indent=2)
    open(A + "/lang/" + f, "a").write("\n")

# Java registration (between markers, idempotent)
def const(bid):
    return bid.upper()

blk = ["    // >>> r12 field blocks (tools/gen_field_r12.py)"]
for bid, v in B.items():
    blk.append('    public static final RegistryObject<Block> %s = BLOCKS.register("%s", () -> %s);' % (const(bid), bid, v[0]))
blk.append("    // <<< r12 field blocks")
itm = ["    // >>> r12 field blocks (tools/gen_field_r12.py)"]
for bid in B:
    itm.append('    public static final RegistryObject<Item> %s = ModItems.blockItem("%s", ModBlocks.%s);' % (const(bid), bid, const(bid)))
itm.append('    public static final RegistryObject<Item> ENTRENCHING_TOOL = ITEMS.register("entrenching_tool", () -> new ru.aiforge.realwar.field.EntrenchingToolItem(new Item.Properties()));')
itm.append("    // <<< r12 field blocks")


def put(path, anchor, lines):
    s = open(path).read()
    s = re.sub(r"    // >>> r12 field blocks.*?// <<< r12 field blocks\n", "", s, flags=re.S)
    assert s.count(anchor) == 1, (path, anchor)
    s = s.replace(anchor, anchor + "\n".join(lines) + "\n")
    open(path, "w").write(s)


put(J + "/registry/ModBlocks.java", ".noOcclusion().noCollission().ignitedByLava()));\n", blk)
put(J + "/registry/ModItems.java", '    public static final RegistryObject<Item> CAMO_NET = ModItems.blockItem("camo_net", ModBlocks.CAMO_NET);\n', itm)
tp = J + "/registry/ModTabs.java"
s = open(tp).read()
ids = ", ".join('"%s"' % b for b in list(B) + ["entrenching_tool"])
s = re.sub(r'(ModTabs\.put\(Cat\.FORTIFICATION, "sandbags", "barbed_wire", "concertina_wire", "czech_hedgehog", "dragon_teeth", "concrete_barrier", "camo_net", "reinforced_concrete", "steel_block")[^;]*\);',
           r'\1, ' + ids.replace('\\', '\\\\') + ');', s)
open(tp, "w").write(s)
print("field blocks", len(B), "lang", len(K))
