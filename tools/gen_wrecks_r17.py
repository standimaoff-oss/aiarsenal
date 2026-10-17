#!/usr/bin/env python3
"""r17 building blocks part 4: vehicle wrecks (multi-block, per-cell OBJ chunk models), loot crates (tiers), traps,
field consumables and worldgen data. Generates blockstates, block/item models, block + crate loot tables, tool tags,
lang (en/ru), recipes, worldgen JSON, the generated WreckKind / CrateKind enums and the Java registration between
the  // >>> r17  markers. Idempotent: python3 tools/gen_wrecks_r17.py"""
import json, os, re, collections
M = "/home/user/mods/aiarsenal/src/main"
A = M + "/resources/assets/aiarsenal"
D = M + "/resources/data"
J = M + "/java/ru/aiforge/realwar"
W = A + "/models/block/wreck"
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
DIRS = ["north", "east", "south", "west"]
TEXMAP = json.load(open(W + "/wreck_texmap.json"))
CELLS = json.load(open(W + "/wreck_cells.json"))
B = collections.OrderedDict()          # block id -> info
IT = collections.OrderedDict()         # plain item id -> info
K = {}
DK = "ru.aiforge.realwar.deco."
FK = "ru.aiforge.realwar.field."
for f in os.listdir(A + "/models/block"):
    if f.startswith("r17_") and f.endswith(".json"):
        os.remove(A + "/models/block/" + f)


def texs(particle):
    t = {k: "aiarsenal:block/" + v for k, v in TEXMAP.items()}
    t["particle"] = "aiarsenal:block/" + particle
    return t


def obj_model(name, obj, particle, rt="minecraft:cutout"):
    m = {"parent": "minecraft:block/block", "loader": "forge:obj", "model": "aiarsenal:models/block/wreck/%s.obj" % obj,
         "flip_v": True, "automatic_culling": False, "shade_quads": True, "emissive_ambient": True,
         "mtl_override": "aiarsenal:models/block/wreck/wreck.mtl", "textures": texs(particle), "render_type": rt}
    json.dump(m, open(A + "/models/block/%s.json" % name, "w"), indent=1)
    return "aiarsenal:block/" + name


def _bbox(obj):
    vs = [list(map(float, l.split()[1:4])) for l in open(W + "/%s.obj" % obj) if l.startswith("v ")]
    return [min(v[i] for v in vs) for i in range(3)], [max(v[i] for v in vs) for i in range(3)]


def _rot(rx, ry, v):
    import math
    x, y, z = v
    c, s_ = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    x, z = x * c + z * s_, -x * s_ + z * c
    c, s_ = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    y, z = y * c - z * s_, y * s_ + z * c
    return x, y, z


def _disp(lo, hi, rot, base, extra=(0, 0, 0)):
    dim = max(hi[i] - lo[i] for i in range(3))
    s = base / max(1.0, dim)
    c = [(lo[i] + hi[i]) / 2 - 0.5 for i in range(3)]
    r = _rot(rot[0], rot[1], c)
    t = [round(max(-80, min(80, -16 * s * r[0] + extra[0])), 3), round(max(-80, min(80, -16 * s * r[1] + extra[1])), 3), extra[2]]
    return {"rotation": list(rot), "translation": t, "scale": [round(s, 4)] * 3}


def item_obj(bid, obj, particle, rot=(30, 225, 0), rt="minecraft:cutout"):
    lo, hi = _bbox(obj)
    gui_base = 1.0 if max(hi[i] - lo[i] for i in range(3)) > 1.2 else 0.68
    m = {"parent": "minecraft:block/block", "loader": "forge:obj", "model": "aiarsenal:models/block/wreck/%s.obj" % obj, "flip_v": True,
         "automatic_culling": False, "mtl_override": "aiarsenal:models/block/wreck/wreck.mtl", "textures": texs(particle), "render_type": rt,
         "display": {"gui": _disp(lo, hi, rot, gui_base),
                     "ground": _disp(lo, hi, (0, 0, 0), 0.3, (0, 3, 0)),
                     "fixed": _disp(lo, hi, (0, 180, 0), 0.8),
                     "thirdperson_righthand": _disp(lo, hi, (75, 45, 0), 0.375, (0, 2.5, 0)),
                     "firstperson_righthand": _disp(lo, hi, (0, 45, 0), 0.4),
                     "firstperson_lefthand": _disp(lo, hi, (0, 225, 0), 0.4)}}
    json.dump(m, open(A + "/models/item/%s.json" % bid, "w"), indent=1)


def bs(bid, data):
    json.dump(data, open(A + "/blockstates/%s.json" % bid, "w"), indent=1)


def rot(model, y):
    v = {"model": model}
    if y:
        v["y"] = y
    return v


IRON = ".requiresCorrectToolForDrops()"
P_WRECK = ('BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(5f, 20f).sound(SoundType.NETHERITE_BLOCK)'
           '.noOcclusion().dynamicShape().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)'
           '.pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)' + IRON)

# ---------------------------------------------------------------- wrecks
WRECKS = [
    ("tank_hull", "TANK_HULL", "wreck_tank_paint", "Burnt T-72B hull", "Сгоревший корпус Т-72Б",
     "Hull of a T-72B after an ammunition fire: the turret is gone, one track has come off. Real size: 6.9 x 3.5 m.",
     "Корпус Т-72Б после детонации боекомплекта: башню сорвало, одна гусеница слетела. Реальный размер 6,9 × 3,5 м."),
    ("tank_turret", "TANK_TURRET", "wreck_tank_paint", "Torn-off T-72B turret", "Сорванная башня Т-72Б",
     "The turret thrown off by the ammo-rack explosion, lying on its side, with the bent 2A46 125 mm gun.",
     "Башня, сорванная взрывом боеукладки, лежит на боку; погнутая пушка 2А46 125 мм."),
    ("tank_track", "TANK_TRACK", "wreck_track", "Broken tank track", "Разорванная гусеница",
     "A run of track torn off the running gear, with a loose road wheel.", "Сорванная гусеничная лента и откатившийся опорный каток."),
    ("heli_body", "HELI_BODY", "wreck_heli_paint", "Mi-24V wreck (fuselage)", "Обломки Ми-24В (фюзеляж)",
     "Forward fuselage of a downed Mi-24V: crazed tandem canopies, engines, stub wing with a B-8V20 pod. Real size.",
     "Передняя часть сбитого Ми-24В: разбитые фонари кабин, двигатели, крыло с блоком Б-8В20. В реальном размере."),
    ("heli_tail", "HELI_TAIL", "wreck_heli_paint", "Mi-24V wreck (tail boom)", "Обломки Ми-24В (хвостовая балка)",
     "Tail boom broken off at the fuselage: fin, stabilisers and the remains of the tail rotor.",
     "Хвостовая балка, отломанная у фюзеляжа: киль, стабилизатор и остатки рулевого винта."),
    ("heli_rotor", "HELI_ROTOR", "wreck_burnt_steel", "Mi-24 main rotor blade", "Лопасть несущего винта Ми-24",
     "Bent 8.6 m main rotor blade with its root fitting and a broken piece of another blade.",
     "Погнутая лопасть 8,6 м с комлевым узлом и обломок второй лопасти."),
    ("jet_body", "JET_BODY", "wreck_plane_paint", "MiG-29 wreck (fuselage)", "Обломки МиГ-29 (фюзеляж)",
     "Belly-landed forward fuselage of a MiG-29: shattered canopy, empty cockpit (the seat ejected), LERX and intakes.",
     "Носовая часть МиГ-29 на брюхе: разбитый фонарь, пустая кабина (кресло катапультировано), наплывы и воздухозаборники."),
    ("jet_wing", "JET_WING", "wreck_plane_paint", "MiG-29 wreck (wing)", "Обломки МиГ-29 (крыло)",
     "Torn-off outer wing lying upside down: pylons, an APU-73 rail and half an R-73 missile.",
     "Оторванная консоль крыла лежит вверх ногами: пилоны, направляющая АПУ-73 и половина ракеты Р-73."),
    ("jet_tail", "JET_TAIL", "wreck_plane_paint", "MiG-29 wreck (tail)", "Обломки МиГ-29 (хвост)",
     "Tail section with a fin, a stabilator and the heat-blued RD-33 nozzle.", "Хвостовая часть: киль, стабилизатор и побежалое сопло РД-33."),
]
WK = []
for wid, enum, part, en, ru, den, dru in WRECKS:
    bid = "wreck_" + wid
    info = CELLS["wrecks"][wid]
    Wd, Hd, Ld = info["w"], info["h"], info["l"]
    assert Wd <= 6 and Hd <= 4 and Ld <= 10, wid
    mp = []
    for c in info["cells"]:
        mn = obj_model("r17_%s_c%d_%d_%d" % (wid, c["x"], c["y"], c["z"]), "wreck_%s_c%d_%d_%d" % (wid, c["x"], c["y"], c["z"]), part)
        for f in DIRS:
            mp.append({"when": {"facing": f, "ox": str(c["x"]), "oy": str(c["y"]), "oz": str(c["z"])}, "apply": rot(mn, ROT[f])})
    bs(bid, {"multipart": mp})
    item_obj(bid, "wreck_%s_item" % wid, part, rot=(30, 205, 0))
    # anchor: ground cell nearest the rear centre (where the player clicks)
    gc = [c for c in info["cells"] if c["y"] == 0]
    anc = min(gc, key=lambda c: ((c["x"] - (Wd - 1) / 2) ** 2 + 2.5 * c["z"] ** 2, c["x"]))
    cells = []
    for c in info["cells"]:
        bx = list(c["box"])
        if bx[1] < 4:
            bx[1] = 0.0
        for i in range(3):
            if bx[i + 3] - bx[i] < 1.0:
                m_ = (bx[i] + bx[i + 3]) / 2
                bx[i], bx[i + 3] = max(0, m_ - 0.5), min(16, m_ + 0.5)
        cells.append((c["x"], c["y"], c["z"], bx, c["solid"]))
    # smoke sources: up to 5 highest cell tops, canonical block space relative to the origin cell corner
    tops = sorted(cells, key=lambda c: -(c[1] * 16 + c[3][4]))
    smoke = []
    for c in tops:
        pt = (round(c[0] + (c[3][0] + c[3][3]) / 32, 2), round(c[1] + c[3][4] / 16, 2), round(-c[2] + (c[3][2] + c[3][5]) / 32, 2))
        if all(abs(pt[0] - q[0]) + abs(pt[2] - q[2]) > 1.6 for q in smoke):
            smoke.append(pt)
        if len(smoke) == 5:
            break
    WK.append((enum, wid, Wd, Hd, Ld, (anc["x"], anc["z"]), cells, smoke))
    B[bid] = dict(ctor=f"new {DK}WreckBlock({DK}WreckKind.{enum}, {P_WRECK})", en=en, ru=ru, den=den, dru=dru, tool="pickaxe", iron=True,
                  wreck=(anc["x"], anc["z"]), tab="BUILDING")

# ---------------------------------------------------------------- loot crates
P_CRATE = 'BlockBehaviour.Properties.of().mapColor(MapColor.%s).strength(%sf, %sf).sound(SoundType.%s).noOcclusion()'
CRATES = [
    ("ammo", "AMMO", "crate_wood_green", "COLOR_GREEN", 2.0, 3.0, "WOOD", "axe", "common",
     "Ammunition box", "Ящик с боеприпасами", "Green wooden ammo box with rope handles.", "Зелёный деревянный ящик для боеприпасов с верёвочными ручками."),
    ("medical", "MEDICAL", "crate_white", "SNOW", 2.0, 4.0, "METAL", "pickaxe", "common",
     "Medical box", "Медицинский ящик", "White steel box with a red cross: bandages, first aid kits, rations.", "Белый стальной ящик с красным крестом: бинты, аптечки, сухпайки."),
    ("military", "MILITARY", "crate_olive_metal", "COLOR_GREEN", 3.0, 6.0, "METAL", "pickaxe", "uncommon",
     "Military supply case", "Армейский ящик снабжения", "Olive steel case with corner protectors.", "Оливковый стальной кейс с усиленными углами."),
    ("weapons", "WEAPONS", "crate_fiberglass", "COLOR_GREEN", 3.0, 6.0, "METAL", "pickaxe", "rare",
     "Weapons case", "Оружейный кейс", "Long fibreglass case with four latches.", "Длинный стеклопластиковый кейс с четырьмя замками."),
    ("secret", "SECRET", "crate_black", "COLOR_BLACK", 4.0, 12.0, "NETHERITE_BLOCK", "pickaxe", "epic",
     "Classified case", "Секретный кейс", "Black case with a keypad and a red-star band. Something valuable inside.",
     "Чёрный кейс с кодовым замком и полосой с красной звездой. Внутри что-то ценное."),
]
CK = []
for key, enum, part, col, hard, res, snd, tool, rarity, en, ru, den, dru in CRATES:
    bid = "crate_" + key
    body = obj_model("r17_crate_%s_body" % key, "crate_%s_body" % key, part)
    obj_model("r17_crate_%s_lid" % key, "crate_%s_lid" % key, part)
    bs(bid, {"variants": {"facing=%s" % f: rot(body, ROT[f]) for f in DIRS}})
    item_obj(bid, "crate_%s_item" % key, part, rot=(30, 210, 0))
    c = CELLS["crates"][key]
    CK.append((enum, key, c["pivot_y"], c["pivot_z"], [round(v * 16, 2) for v in c["box"]], rarity))
    B[bid] = dict(ctor=f"new {DK}CrateBlock({DK}CrateKind.{enum}, {P_CRATE % (col, hard, res, snd)})", en=en, ru=ru,
                  den=den + " Placed in creative it comes filled with loot.", dru=dru + " Поставленный в творческом режиме — сразу с добычей.",
                  tool=tool, iron=False, crate=True, tab="BUILDING")
    K["container.aiarsenal." + bid] = (en, ru)

# ---------------------------------------------------------------- traps
P_TRAP = 'BlockBehaviour.Properties.of().mapColor(MapColor.%s).strength(%sf, %sf).sound(SoundType.%s).noOcclusion()%s'
pm = obj_model("r17_trap_punji_open", "trap_punji_open", "trap_bamboo")
pc = obj_model("r17_trap_punji_covered", "trap_punji_covered", "trap_leafmat")
bs("trap_punji", {"variants": {"covered=false": {"model": pm}, "covered=true": {"model": pc}}})
item_obj("trap_punji", "trap_punji_covered", "trap_bamboo")
B["trap_punji"] = dict(ctor=f"new {FK}PunjiTrapBlock({P_TRAP % ('PLANT', 0.8, 1.0, 'GRASS', '.noCollission()')})",
                       en="Punji pit", ru="Волчья яма с кольями",
                       den="Dig a hole and place it: sharpened bamboo stakes under a leaf mat. Whoever steps on it falls in and is wounded and slowed.",
                       dru="Выкопайте яму и поставьте: заострённые колья бамбука под настилом из листьев. Наступивший проваливается, ранится и замедляется.",
                       tool="axe", tab="FORTIFICATION")
sm = obj_model("r17_trap_spike_strip", "trap_spike_strip", "trap_spikes")
bs("trap_spike_strip", {"variants": {"facing=%s" % f: rot(sm, ROT[f]) for f in DIRS}})
item_obj("trap_spike_strip", "trap_spike_strip", "trap_spikes")
B["trap_spike_strip"] = dict(ctor=f"new {FK}SpikeStripBlock({P_TRAP % ('METAL', 1.5, 6.0, 'METAL', '.noCollission()')})",
                             en="Spike strip", ru="Шипованная лента «Ёж»",
                             den="Laid across a road: wounds and slows people, shreds the tyres of vehicles driving over it.",
                             dru="Кладётся поперёк дороги: ранит и замедляет людей, рвёт колёса проезжающей технике.",
                             tool="pickaxe", tab="FORTIFICATION")
fm = obj_model("r17_trap_flare", "trap_flare", "trap_olive")
fs = obj_model("r17_trap_flare_spent", "trap_flare_spent", "trap_olive")
bs("trap_flare", {"variants": {"facing=%s,spent=%s" % (f, s): rot(fs if s == "true" else fm, ROT[f]) for f in DIRS for s in ("false", "true")}})
item_obj("trap_flare", "trap_flare", "trap_olive")
B["trap_flare"] = dict(ctor=f"new {FK}FlareTrapBlock({P_TRAP % ('COLOR_GREEN', 0.5, 1.0, 'WOOD', '.noCollission()')})",
                       en="Signal flare trap", ru="Сигнальная мина",
                       den="Non-lethal. A tripwire to the right fires a red flare, alerts players within 64 blocks and makes intruders glow. Re-arm with gunpowder.",
                       dru="Нелетальная. Растяжка вправо запускает красную ракету, предупреждает игроков в радиусе 64 блоков и подсвечивает нарушителей. Перезарядка — порохом.",
                       tool="axe", tab="FORTIFICATION")
im = obj_model("r17_trap_ied", "trap_ied", "trap_shell")
bs("trap_ied", {"variants": {"facing=%s" % f: rot(im, ROT[f]) for f in DIRS}})
item_obj("trap_ied", "trap_ied", "trap_shell")
B["trap_ied"] = dict(ctor=f"new {FK}IedBlock({P_TRAP % ('COLOR_BROWN', 1.0, 1.0, 'METAL', '.noCollission()')})",
                     en="IED (152 mm shell)", ru="СВУ (снаряд 152 мм)",
                     den="Improvised device, ~3 kg TNT. Right-click toggles PRESSURE / REMOTE; link a remote detonator with sneak + right-click. Shears disarm.",
                     dru="Самодельное взрывное устройство, ~3 кг ТНТ. ПКМ — режим НАЖИМНОЙ / ПО РАДИО; пульт привязывается Shift+ПКМ. Ножницы обезвреживают.",
                     tool="pickaxe", tab="FORTIFICATION")
ep = obj_model("r17_trap_efence_post", "trap_efence_post", "trap_insulator")
es = obj_model("r17_trap_efence_side", "trap_efence_side", "trap_insulator")
mp = [{"apply": {"model": ep}}]
for f in DIRS:
    mp.append({"when": {f: "true"}, "apply": rot(es, ROT[f])})
bs("trap_electric_fence", {"multipart": mp})
item_obj("trap_electric_fence", "trap_efence_item", "trap_insulator")
B["trap_electric_fence"] = dict(ctor=f"new {FK}ElectricFenceBlock({P_TRAP % ('METAL', 2.0, 6.0, 'METAL', IRON)})",
                                en="Electric fence", ru="Электрозабор",
                                den="Connects like a fence. Live while any section gets a redstone signal: shocks and throws back whoever touches it.",
                                dru="Соединяется как забор. Под напряжением, пока любая секция получает сигнал редстоуна: бьёт током и отбрасывает.",
                                tool="pickaxe", iron=True, tab="FORTIFICATION")
K["msg.aiarsenal.ied.mode_pressure"] = ("IED: PRESSURE trigger", "СВУ: НАЖИМНОЙ взрыватель")
K["msg.aiarsenal.ied.mode_remote"] = ("IED: REMOTE (link a detonator with sneak + right-click)", "СВУ: ПО РАДИО (привяжите пульт Shift+ПКМ)")
K["msg.aiarsenal.flare.alert"] = ("Signal flare tripped at %s, %s, %s!", "Сработала сигнальная мина: %s, %s, %s!")
K["msg.aiarsenal.wreck.blocked"] = ("Not enough room for the wreck: it needs %s x %s x %s blocks.", "Не хватает места для обломков: нужно %s × %s × %s блоков.")
K["msg.aiarsenal.wreck.smoke_on"] = ("The wreck is smouldering", "Обломки дымятся")
K["msg.aiarsenal.wreck.smoke_off"] = ("Fire put out", "Огонь потушен")

# ---------------------------------------------------------------- consumables (item/generated)
CONS = [
    ("ration_irp", "Ration (IRP)", "Сухпаёк ИРП", "Individual ration pack: restores a lot of hunger.", "Индивидуальный рацион питания: хорошо утоляет голод.",
     'new ru.aiforge.realwar.item.TooltipItem(new Item.Properties().stacksTo(16).food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(10).saturationMod(0.8f).build()))'),
    ("bandage", "Bandage", "Бинт", "Hold right-click for 1 s: +3 health.", "Удерживайте ПКМ 1 с: +3 здоровья.",
     f"new {FK}MedicalItem(false, new Item.Properties().stacksTo(16))"),
    ("first_aid_kit", "First aid kit", "Аптечка", "Hold right-click for 2 s: Regeneration II for 6 s, cures poison.",
     "Удерживайте ПКМ 2 с: Регенерация II на 6 с, снимает отравление.", f"new {FK}MedicalItem(true, new Item.Properties().stacksTo(8))"),
]
for iid, en, ru, den, dru, ctor in CONS:
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "aiarsenal:item/" + iid}}, open(A + "/models/item/%s.json" % iid, "w"), indent=1)
    IT[iid] = dict(ctor=ctor, en=en, ru=ru, den=den, dru=dru)

# ---------------------------------------------------------------- block loot, tags, lang
for bid, v in B.items():
    pool = {"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": "aiarsenal:" + bid}]}
    if v.get("wreck"):
        ax, az = v["wreck"]
        pool["conditions"].append({"condition": "minecraft:block_state_property", "block": "aiarsenal:" + bid,
                                   "properties": {"ox": str(ax), "oy": "0", "oz": str(az)}})
    json.dump({"type": "minecraft:block", "pools": [pool]}, open(D + "/aiarsenal/loot_tables/blocks/%s.json" % bid, "w"), indent=1)
TAGS = [("blocks/mineable/pickaxe", lambda v: v["tool"] == "pickaxe"), ("blocks/mineable/axe", lambda v: v["tool"] == "axe"),
        ("blocks/needs_iron_tool", lambda v: v.get("iron"))]
for tag, sel in TAGS:
    p = D + "/minecraft/tags/%s.json" % tag
    t = json.load(open(p)) if os.path.exists(p) else {"replace": False, "values": []}
    for bid, v in B.items():
        if sel(v) and "aiarsenal:" + bid not in t["values"]:
            t["values"].append("aiarsenal:" + bid)
    json.dump(t, open(p, "w"), indent=2)
for bid, v in B.items():
    K["block.aiarsenal." + bid] = (v["en"], v["ru"])
    K["block.aiarsenal." + bid + ".desc1"] = (v["den"], v["dru"])
for iid, v in IT.items():
    K["item.aiarsenal." + iid] = (v["en"], v["ru"])
    K["item.aiarsenal." + iid + ".desc"] = (v["den"], v["dru"])
for i, f in enumerate(("en_us.json", "ru_ru.json")):
    d = json.load(open(A + "/lang/" + f), object_pairs_hook=collections.OrderedDict)
    for k, v in K.items():
        d[k] = v[i]
    json.dump(d, open(A + "/lang/" + f, "w"), ensure_ascii=False, indent=2)
    open(A + "/lang/" + f, "a").write("\n")

# ---------------------------------------------------------------- crate loot tables (moderate on purpose)
def it(name, w, lo=1, hi=1, extra=None):
    e = {"type": "minecraft:item", "name": name if ":" in name else "aiarsenal:" + name, "weight": w}
    fn = []
    if hi > 1 or lo != 1:
        fn.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if extra:
        fn += extra
    if fn:
        e["functions"] = fn
    return e


def pool(lo, hi, entries):
    return {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "bonus_rolls": 0.0, "entries": entries}


UNI = lambda rank: [it("uniform_%s_%s_%s" % (s, rank, p), 1) for s in ("ru", "us") for p in ("head", "chest", "legs", "feet")]
LOOT = {
    "ammo": [pool(3, 5, [it("shells_125", 18, 1, 4), it("shells_152", 8, 1, 2), it("shells_20", 14, 2, 8), it("shells_25", 10, 2, 6),
                         it("flak_ammo_box", 5), it("minecraft:gunpowder", 18, 2, 6), it("minecraft:iron_nugget", 14, 3, 9),
                         it("minecraft:arrow", 10, 4, 12), it("pg7v", 3), it("minecraft:tnt", 3, 1, 2), it("ration_irp", 8, 1, 2)])],
    "medical": [pool(3, 5, [it("bandage", 30, 1, 4), it("first_aid_kit", 10), it("ration_irp", 18, 1, 3), it("potassium_iodide", 10, 1, 2),
                            it("minecraft:honey_bottle", 6), it("minecraft:paper", 8, 1, 4), it("minecraft:bread", 10, 1, 3),
                            it("minecraft:potion", 3, extra=[{"function": "minecraft:set_potion", "id": "minecraft:healing"}]),
                            it("minecraft:golden_carrot", 3, 1, 2)])],
    "military": [pool(3, 6, [it("steel_ingot", 14, 1, 3), it("steel_plate", 8, 1, 2), it("minecraft:iron_ingot", 14, 1, 4), it("ration_irp", 14, 1, 2),
                             it("bandage", 12, 1, 3), it("shells_125", 10, 1, 3), it("shells_20", 8, 2, 6), it("mine_pmn2", 5, 1, 2),
                             it("tripwire_f1", 4), it("minecraft:compass", 3), it("minecraft:spyglass", 2), it("minecraft:lead", 3)] +
                         UNI("private") + UNI("sergeant"))],
    "weapons": [pool(3, 5, [it("rpg7", 2), it("pg7v", 10, 1, 3), it("igla_missile", 2), it("shells_125", 10, 1, 3), it("shells_152", 6, 1, 2),
                            it("mine_tm62", 4), it("mine_ozm72", 3), it("mine_pmn2", 6, 1, 2), it("minecraft:tnt", 6, 1, 3),
                            it("steel_ingot", 10, 1, 3), it("minecraft:crossbow", 3), it("minecraft:iron_sword", 3), it("minecraft:gunpowder", 12, 2, 6)])],
    "secret": [pool(1, 1, [it("minecraft:diamond", 1, 1, 2)]),
               pool(2, 4, [it("minecraft:gold_ingot", 20, 2, 5), it("minecraft:emerald", 14, 1, 3), it("minecraft:experience_bottle", 18, 2, 6),
                           it("minecraft:book", 10, extra=[{"function": "minecraft:enchant_randomly"}]), it("geiger_counter", 5),
                           it("hazmat_helmet", 2), it("hazmat_chestplate", 2), it("hazmat_leggings", 2), it("hazmat_boots", 2),
                           it("yellowcake", 4, 1, 2), it("minecraft:paper", 10, 2, 6), it("minecraft:map", 4)] + UNI("captain"))],
}
os.makedirs(D + "/aiarsenal/loot_tables/crates", exist_ok=True)
lang = json.load(open(A + "/lang/en_us.json"))
for k, pools in LOOT.items():
    for p_ in pools:
        for e in p_["entries"]:
            n = e["name"]
            if n.startswith("aiarsenal:"):
                s = n.split(":")[1]
                assert "item.aiarsenal." + s in lang or "block.aiarsenal." + s in lang or s in IT, ("unknown loot item", s)
            assert n not in ("minecraft:netherite_ingot", "minecraft:netherite_scrap", "aiarsenal:little_boy", "aiarsenal:rds_4")
    if k in ("ammo", "medical", "military", "weapons"):
        assert all(e["name"] != "minecraft:diamond" for p_ in pools for e in p_["entries"]), k
    json.dump({"type": "minecraft:chest", "pools": pools}, open(D + "/aiarsenal/loot_tables/crates/%s.json" % k, "w"), indent=1)

# ---------------------------------------------------------------- recipes
I = lambda x: {"item": x}
dye = lambda c: I("minecraft:%s_dye" % c)
OUTR = D + "/aiarsenal/recipes"
S, IN, N = I("aiarsenal:steel_plate"), I("minecraft:iron_ingot"), I("minecraft:iron_nugget")
REC = {
    "crate_ammo": (["PRP", "PGP"], {"P": I("minecraft:spruce_planks"), "R": I("minecraft:string"), "G": dye("green")}, 1),
    "crate_medical": (["INI", "IRI"], {"I": IN, "N": N, "R": dye("red")}, 1),
    "crate_military": (["SNS", "SGS"], {"S": S, "N": N, "G": dye("green")}, 1),
    "crate_weapons": (["SNS", "SLS"], {"S": S, "N": N, "L": dye("lime")}, 1),
    "crate_secret": (["SNS", "SBS"], {"S": S, "N": I("minecraft:gold_nugget"), "B": dye("black")}, 1),
    "trap_punji": (["BBB", "BLB"], {"B": I("minecraft:bamboo"), "L": I("minecraft:oak_leaves")}, 1),
    "trap_spike_strip": (["NNN", "SSS"], {"N": N, "S": S}, 2),
    "trap_flare": (["G", "R", "T"], {"G": I("minecraft:gunpowder"), "R": dye("red"), "T": I("minecraft:tripwire_hook")}, 2),
    "trap_ied": (["R", "T", "S"], {"R": I("minecraft:redstone"), "T": I("minecraft:tnt"), "S": I("aiarsenal:shells_152")}, 1),
    "trap_electric_fence": (["BRB", "BRB"], {"B": I("minecraft:iron_bars"), "R": I("minecraft:redstone")}, 6),
}
SHAPELESS = {
    "ration_irp": ([I("minecraft:bread"), I("minecraft:cooked_beef"), I("minecraft:paper")], 2),
    "bandage": ([I("minecraft:white_wool"), I("minecraft:string")], 3),
    "first_aid_kit": ([I("aiarsenal:bandage"), I("aiarsenal:bandage"), I("minecraft:golden_carrot"), dye("red")], 1),
}
sig = set()
for rid, (pat, key, cnt) in REC.items():
    assert set("".join(pat)) - {" "} == set(key), rid
    s = json.dumps([[key.get(ch, None) for ch in row] for row in pat], sort_keys=True)
    assert s not in sig, ("dup", rid)
    sig.add(s)
    json.dump({"type": "minecraft:crafting_shaped", "category": "building", "pattern": pat, "key": key,
               "result": {"item": "aiarsenal:" + rid, "count": cnt}}, open(OUTR + "/%s.json" % rid, "w"), indent=1)
for rid, (ing, cnt) in SHAPELESS.items():
    s = json.dumps(sorted(json.dumps(x, sort_keys=True) for x in ing))
    assert s not in sig, ("dup", rid)
    sig.add(s)
    json.dump({"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ing,
               "result": {"item": "aiarsenal:" + rid, "count": cnt}}, open(OUTR + "/%s.json" % rid, "w"), indent=1)
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

# ---------------------------------------------------------------- worldgen
for fid, chance in (("wreck_site", 160), ("supply_cache", 120)):
    json.dump({"type": "aiarsenal:" + fid, "config": {}}, open(D + "/aiarsenal/worldgen/configured_feature/%s.json" % fid, "w"), indent=1)
    json.dump({"feature": "aiarsenal:" + fid, "placement": [{"type": "minecraft:rarity_filter", "chance": chance},
                                                          {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                                                          {"type": "minecraft:biome"}]},
              open(D + "/aiarsenal/worldgen/placed_feature/%s.json" % fid, "w"), indent=1)
json.dump({"type": "forge:add_features", "biomes": "#minecraft:is_overworld", "features": ["aiarsenal:wreck_site", "aiarsenal:supply_cache"],
           "step": "surface_structures"}, open(D + "/aiarsenal/forge/biome_modifier/add_r17_sites.json", "w"), indent=1)

# ---------------------------------------------------------------- generated Java: WreckKind / CrateKind
jf = lambda v: ("%.3f" % v).rstrip("0").rstrip(".") + "f"
lines = []
for enum, wid, Wd, Hd, Ld, (ax, az), cells, smoke in WK:
    ci = ", ".join("%d, %d, %d, %d" % (c[0], c[1], c[2], 1 if c[4] else 0) for c in cells)
    bx = ", ".join(", ".join(jf(v) for v in c[3]) for c in cells)
    sp = ", ".join(", ".join(jf(v) for v in p) for p in smoke)
    lines.append('    %s("%s", %d, %d, %d, %d, %d,\n            new int[]{%s},\n            new float[]{%s},\n            new float[]{%s})'
                 % (enum, wid, Wd, Hd, Ld, ax, az, ci, bx, sp))
wk = """package ru.aiforge.realwar.deco;

/** GENERATED by tools/gen_wrecks_r17.py from models/block/wreck/wreck_cells.json - do not edit.
 *  r17 vehicle wrecks: footprint W x H x L blocks (canonical facing north: +x = right, oz grows forwards = -z),
 *  occupied cells (ox, oy, oz, solid), per-cell collision box in pixels (canonical, cell local) and smoke sources
 *  (canonical block space relative to the origin cell corner). The anchor (ax, az, oy 0) is the block the player clicks. */
public enum WreckKind {
%s;

    public final String id;
    public final int w, h, l, ax, az;
    public final int[] cells;
    public final float[] boxes, smoke;

    WreckKind(String id, int w, int h, int l, int ax, int az, int[] cells, float[] boxes, float[] smoke) {
        this.id = id;
        this.w = w;
        this.h = h;
        this.l = l;
        this.ax = ax;
        this.az = az;
        this.cells = cells;
        this.boxes = boxes;
        this.smoke = smoke;
    }

    public int count() {
        return cells.length / 4;
    }

    public int index(int ox, int oy, int oz) {
        for (int i = 0; i < count(); i++)
            if (cells[i * 4] == ox && cells[i * 4 + 1] == oy && cells[i * 4 + 2] == oz) return i;
        return -1;
    }
}
""" % ",\n".join(lines)
open(J + "/deco/WreckKind.java", "w").write(wk)
cl = []
for enum, key, py, pz, box, rarity in CK:
    cl.append('    %s("%s", %s, %s, new double[]{%s}, "%s")' % (enum, key, jf(py), jf(pz), ", ".join("%.2f" % v for v in box), rarity))
ck = """package ru.aiforge.realwar.deco;

import net.minecraft.resources.ResourceLocation;

/** GENERATED by tools/gen_wrecks_r17.py - do not edit. r17 loot crates: lid hinge (y, z) at the back top edge,
 *  canonical (north) shape in pixels, loot table aiarsenal:crates/<id>. */
public enum CrateKind {
%s;

    public final String id, rarity;
    public final float pivotY, pivotZ;
    public final double[] box;

    CrateKind(String id, float pivotY, float pivotZ, double[] box, String rarity) {
        this.id = id;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.box = box;
        this.rarity = rarity;
    }

    public String lid() {
        return "r17_crate_" + id + "_lid";
    }

    public ResourceLocation loot() {
        return new ResourceLocation("aiarsenal", "crates/" + id);
    }
}
""" % ",\n".join(cl)
open(J + "/deco/CrateKind.java", "w").write(ck)

# ---------------------------------------------------------------- Java registration
const = lambda b: b.upper()
blk = ["    // >>> r17 (tools/gen_wrecks_r17.py)"]
for bid, v in B.items():
    blk.append('    public static final RegistryObject<Block> %s = BLOCKS.register("%s", () -> %s);' % (const(bid), bid, v["ctor"]))
blk.append("    // <<< r17")
itm = ["    // >>> r17 (tools/gen_wrecks_r17.py)"]
for bid, v in B.items():
    itm.append('    public static final RegistryObject<Item> %s = ModItems.blockItem("%s", ModBlocks.%s);' % (const(bid), bid, const(bid)))
for iid, v in IT.items():
    itm.append('    public static final RegistryObject<Item> %s = ITEMS.register("%s", () -> %s);' % (const(iid), iid, v["ctor"]))
itm.append("    // <<< r17")


def put(path, anchor, lines):
    s = open(path).read()
    s = re.sub(r"    // >>> r17 .*?// <<< r17\n", "", s, flags=re.S)
    assert s.count(anchor) == 1, (path, anchor)
    s = s.replace(anchor, anchor + "\n".join(lines) + "\n")
    open(path, "w").write(s)


put(J + "/registry/ModBlocks.java", "    // <<< r16 doors\n", blk)
put(J + "/registry/ModItems.java", "    // <<< r16 doors\n", itm)
wrecks = [b for b, v in B.items() if v.get("wreck")]
crates = [b for b, v in B.items() if v.get("crate")]
be = ["    // >>> r17 (tools/gen_wrecks_r17.py)",
      '    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.WreckBlockEntity>> WRECK = BLOCK_ENTITIES.register("wreck", () -> BlockEntityType.Builder.of(ru.aiforge.realwar.deco.WreckBlockEntity::new, new Block[]{%s}).build(null));'
      % ", ".join("ModBlocks.%s.get()" % const(b) for b in wrecks),
      '    public static final RegistryObject<BlockEntityType<ru.aiforge.realwar.deco.CrateBlockEntity>> CRATE = BLOCK_ENTITIES.register("crate", () -> BlockEntityType.Builder.of(ru.aiforge.realwar.deco.CrateBlockEntity::new, new Block[]{%s}).build(null));'
      % ", ".join("ModBlocks.%s.get()" % const(b) for b in crates),
      "    // <<< r17"]
put(J + "/registry/ModBlockEntities.java", "    // <<< r16 doors\n", be)
tp = J + "/registry/ModTabs.java"
s = open(tp).read()
s = re.sub(r"        // >>> r17.*?// <<< r17\n", "", s, flags=re.S)
anchor = "        ModTabs.put(Cat.WEAPONS,"
assert s.count(anchor) == 1
q = lambda ids: ", ".join('"%s"' % b for b in ids)
tabs = "        // >>> r17\n"
tabs += "        ModTabs.put(Cat.BUILDING, %s);\n" % q([b for b, v in B.items() if v["tab"] == "BUILDING"])
tabs += "        ModTabs.put(Cat.FORTIFICATION, %s);\n" % q([b for b, v in B.items() if v["tab"] == "FORTIFICATION"])
tabs += "        ModTabs.put(Cat.PROTECTION, %s);\n" % q(list(IT))
tabs += "        // <<< r17\n"
s = s.replace(anchor, tabs + anchor)
open(tp, "w").write(s)
print("r17 blocks", len(B), "items", len(IT), "lang", len(K), "recipes", len(REC) + len(SHAPELESS),
      "cells", sum(len(w[6]) for w in WK), "anchors", [(w[1], w[5]) for w in WK])
