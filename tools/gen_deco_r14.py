#!/usr/bin/env python3
"""r14 building blocks part 2: flags, flagpole, lamps, searchlight, armour plates.
Generates blockstates, block/item models, loot tables, tool tags, lang (en/ru), recipes, the BUILDING creative tab
and the Java registration (between >>> r14 deco markers). Idempotent: python3 tools/gen_deco_r14.py"""
import json, os, re, collections
M = "/home/user/mods/aiarsenal/src/main"
A = M + "/resources/assets/aiarsenal"
D = M + "/resources/data"
J = M + "/java/ru/aiforge/realwar"
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
H4 = ("facing", ["north", "east", "south", "west"])
DK = "ru.aiforge.realwar.deco."

# ---------------------------------------------------------------- texture map for the deco OBJ material set
FIELD_TEX = ["sandbag", "sandbag_olive", "concrete", "concrete_dark", "steel", "wire", "wood", "camo_net", "hazard",
             "plank", "tin", "earth", "drum", "drum_top", "olive", "rubber", "rust", "mon50", "sign"]
FORT = {"sandbag", "sandbag_olive", "concrete", "concrete_dark", "steel", "wire", "wood", "camo_net", "hazard"}
DECO_TEX = ["pole", "brass", "rope", "paint_green", "paint_grey", "paint_olive", "black", "chrome", "glass", "glass_red",
            "glass_orange", "frost", "led", "flame", "lens", "fins", "cloth"]
OFF = {"flame": "deco_none", "frost": "deco_frost_off", "led": "deco_led_off", "lens": "deco_lens_off"}


def tex_map(off=False, particle="deco_paint_grey"):
    t = {}
    for k in FIELD_TEX:
        t[k] = "aiarsenal:block/" + ("fort_" + k if k in FORT else "field_" + k)
    t["sign"] = "aiarsenal:block/field_sign_mines"
    t["drum"] = "aiarsenal:block/field_drum_olive"
    for k in DECO_TEX:
        t[k] = "aiarsenal:block/deco_" + k
    t["cloth"] = "aiarsenal:block/deco_none"
    if off:
        for k, v in OFF.items():
            t[k] = "aiarsenal:block/" + v
    t["particle"] = "aiarsenal:block/" + particle
    return t


LIT_TEX = {"deco_lantern_hurricane": {"glass": "deco_glass_warm"}, "deco_lantern_hurricane_hanging": {"glass": "deco_glass_warm"}}


def obj_model(name, obj, off=False, rt=None, particle="deco_paint_grey"):
    m = {"parent": "minecraft:block/block", "loader": "forge:obj", "model": "aiarsenal:models/block/deco/%s.obj" % obj,
         "flip_v": True, "automatic_culling": False, "shade_quads": True, "emissive_ambient": True,
         "mtl_override": "aiarsenal:models/block/deco/%s" % ("deco.mtl" if off or "reflector" in name else "deco_lit.mtl"),
         "textures": tex_map(off, particle)}
    for k, v in LIT_TEX.get(name, {}).items():
        m["textures"][k] = "aiarsenal:block/" + v
    if rt:
        m["render_type"] = rt
    json.dump(m, open(A + "/models/block/%s.json" % name, "w"), indent=2)
    return "aiarsenal:block/" + name


def item_obj(bid, obj, scale, rt=None, ty=0.0, particle="deco_paint_grey"):
    m = {"parent": "minecraft:block/block", "loader": "forge:obj", "model": "aiarsenal:models/block/deco/%s.obj" % obj, "flip_v": True,
         "automatic_culling": False, "mtl_override": "aiarsenal:models/block/deco/deco.mtl", "textures": tex_map(False, particle),
         "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, ty, 0], "scale": [scale] * 3},
                     "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3] * 3},
                     "fixed": {"rotation": [0, 0, 0], "translation": [0, ty, 0], "scale": [scale * 0.8] * 3},
                     "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
                     "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
                     "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4] * 3}}}
    if rt:
        m["render_type"] = rt
    json.dump(m, open(A + "/models/item/%s.json" % bid, "w"), indent=2)


def item_flat(bid, tex):
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "aiarsenal:item/" + tex}},
              open(A + "/models/item/%s.json" % bid, "w"), indent=2)


def item_parent(bid, model):
    json.dump({"parent": model}, open(A + "/models/item/%s.json" % bid, "w"), indent=2)


def variants(fn, props):
    out = {}

    def rec(i, cur):
        if i == len(props):
            v = fn(dict(cur))
            if v.get("y") == 0:
                v.pop("y")
            if v.get("x") == 0:
                v.pop("x")
            out[",".join("%s=%s" % kv for kv in cur)] = v
            return
        n, vals = props[i]
        for x in vals:
            rec(i + 1, cur + [(n, x)])
    rec(0, [])
    return {"variants": out}


def bs(bid, data):
    json.dump(data, open(A + "/blockstates/%s.json" % bid, "w"), indent=2)


LIT = ("lit", ["false", "true"])
PW = ("powered", ["false", "true"])
B = collections.OrderedDict()     # id -> dict(ctor, en, ru, den, dru, tool, item=True)
K = {}                            # extra lang keys
P_POLE = 'BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.LANTERN).noOcclusion()'
P_LAMP = 'BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8f, 3.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(s -> s.getValue(ru.aiforge.realwar.deco.LampBlock.LIT) ? %d : 0)'
P_SL = 'BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(3.0f, 8.0f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(ru.aiforge.realwar.deco.SearchlightBlock.LIT) ? 15 : 0)'
P_SPOT = 'BlockBehaviour.Properties.of().replaceable().noCollission().noLootTable().air().lightLevel(s -> 15)'
P_ARM = 'BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(9.0f, 60.0f).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK)'

# ---------------------------------------------------------------- flagpole + flags
pole = obj_model("deco_flagpole_pole", "flagpole_pole", particle="deco_pole")
fin = obj_model("deco_flagpole_finial", "flagpole_finial", particle="deco_pole")
base = obj_model("deco_flagpole_base", "flagpole_base", particle="deco_pole")
fit = obj_model("deco_flag_fittings", "flag_fittings", particle="deco_pole")


def pole_parts():
    return [{"apply": {"model": pole}}, {"when": {"top": "true"}, "apply": {"model": fin}}, {"when": {"bottom": "true"}, "apply": {"model": base}}]


B["flagpole"] = dict(ctor=f"new {DK}FlagpoleBlock({P_POLE})", en="Flagpole", ru="Флагшток",
                     den="Stack it as high as you like; the top gets a gilded finial. Put a flag on top.",
                     dru="Ставится столбом любой высоты, наверху — позолоченное навершие. Сверху ставится флаг.", tool="pickaxe")
bs("flagpole", {"multipart": pole_parts()})
item_flat("flagpole", "flagpole")

FLAGS = [("russia", "Flag of Russia", "Флаг России"), ("ussr", "Flag of the USSR", "Флаг СССР"),
         ("germany", "Flag of Germany", "Флаг Германии"), ("usa", "Flag of the USA", "Флаг США"),
         ("uk", "Flag of the United Kingdom", "Флаг Великобритании"), ("france", "Flag of France", "Флаг Франции"),
         ("china", "Flag of China", "Флаг Китая"), ("japan", "Flag of Japan", "Флаг Японии"),
         ("balkenkreuz", "German WW2 flag (Balkenkreuz, conventional)", "Флаг Германии ВМВ (балочный крест, условный)"),
         ("vdv", "Flag of the Russian Airborne Troops (VDV)", "Флаг ВДВ"),
         ("vmf", "St Andrew's ensign (Russian Navy)", "Андреевский флаг (ВМФ)"),
         ("vks", "Flag of the Russian Aerospace Forces", "Флаг ВКС России"),
         ("nato", "Flag of NATO", "Флаг НАТО"), ("un", "Flag of the United Nations", "Флаг ООН"),
         ("white", "White flag", "Белый флаг"), ("victory", "Victory Banner (1945)", "Знамя Победы (1945)")]
for fid, en, ru in FLAGS:
    bid = "flag_" + fid
    B[bid] = dict(ctor=f'new {DK}FlagBlock("{fid}", {P_POLE})', en=en, ru=ru,
                  den="Waves in the wind (stronger in rain). Place it on top of a flagpole; the front faces you.",
                  dru="Развевается на ветру (в дождь — сильнее). Ставится на флагшток, лицевой стороной к вам.", tool="pickaxe")
    parts = pole_parts() + [{"when": {"facing": f}, "apply": ({"model": fit, "y": ROT[f]} if ROT[f] else {"model": fit})} for f in ROT]
    bs(bid, {"multipart": parts})
    item_flat(bid, "flag_" + fid)

# ---------------------------------------------------------------- lamps
LAMPS = [
    ("lantern_hurricane", f"new {DK}HangingLampBlock(1, {P_LAMP % 14})", "Hurricane lantern", "Керосиновый фонарь «летучая мышь»",
     "Stands on the floor, or hangs on a chain when placed under a ceiling. Right-click: light / put out.",
     "Ставится на пол, а под потолком — висит на цепочке. ПКМ — зажечь / погасить."),
    ("lamp_bulkhead", f"new {DK}DirectionalLampBlock(0, {P_LAMP % 15})", "Caged bulkhead lamp", "Армейский плафон в решётке",
     "Screws to a floor, wall or ceiling. Right-click or redstone switches it.", "Крепится на пол, стену или потолок. ПКМ или редстоун — вкл/выкл."),
    ("lamp_street", f"new {DK}FacingLampBlock(true, {P_LAMP % 15})", "Street light", "Уличный фонарь",
     "Cobra-head LED light on a wall bracket: click the side of a wall or post.", "Светодиодный консольный светильник на кронштейне: ставится на стену или столб."),
    ("lamp_flood", f"new {DK}FacingLampBlock(false, {P_LAMP % 15})", "Tripod floodlight", "Прожектор на треноге",
     "LED work light on a tripod, shines where you looked when placing it.", "Светодиодный прожектор на треноге, светит туда, куда вы смотрели при установке."),
    ("beacon_red", f"new {DK}BeaconLampBlock(false, {P_LAMP % 7})", "Obstruction light (red)", "Заградительный огонь (красный)",
     "Blinks once a second. Put it on masts, towers and roofs.", "Мигает раз в секунду. Для мачт, вышек и крыш."),
    ("beacon_rotating", f"new {DK}BeaconLampBlock(true, {P_LAMP % 10})", "Rotating warning beacon", "Проблесковый маячок",
     "Orange rotating mirror beacon - gates, checkpoints, service vehicles. Redstone switches it.",
     "Оранжевый маячок с вращающимся зеркалом — ворота, КПП, техника. Включается редстоуном."),
]
for bid, ctor, en, ru, den, dru in LAMPS:
    B[bid] = dict(ctor=ctor, en=en, ru=ru, den=den, dru=dru, tool="pickaxe")

TR = "minecraft:translucent"
on = {}
for o in ("lantern_hurricane", "lantern_hurricane_hanging", "lamp_bulkhead", "beacon_red", "beacon_rotating"):
    on[o] = (obj_model("deco_" + o, o, rt=TR), obj_model("deco_" + o + "_off", o, off=True, rt=TR))
for o in ("lamp_street", "lamp_flood"):
    on[o] = (obj_model("deco_" + o, o, rt="minecraft:cutout"), obj_model("deco_" + o + "_off", o, off=True, rt="minecraft:cutout"))
pick = lambda o, c: on[o][0] if c["lit"] == "true" else on[o][1]
bs("lantern_hurricane", variants(lambda c: {"model": pick("lantern_hurricane_hanging" if c["hanging"] == "true" else "lantern_hurricane", c)},
                                 [("hanging", ["false", "true"]), LIT, PW]))
DIR6 = {"up": (0, 0), "down": (180, 0), "north": (90, 0), "south": (90, 180), "east": (90, 90), "west": (90, 270)}
bs("lamp_bulkhead", variants(lambda c: {"model": pick("lamp_bulkhead", c), "x": DIR6[c["facing"]][0], "y": DIR6[c["facing"]][1]},
                             [("facing", ["down", "up", "north", "south", "west", "east"]), LIT, PW]))
for o in ("lamp_street", "lamp_flood"):
    bs(o, variants(lambda c, o=o: {"model": pick(o, c), "y": ROT[c["facing"]]}, [H4, LIT, PW]))
for o in ("beacon_red", "beacon_rotating"):
    bs(o, variants(lambda c, o=o: {"model": pick(o, c)}, [LIT, PW]))
item_obj("lantern_hurricane", "lantern_hurricane", 1.25, TR, ty=1.5)
item_obj("lamp_bulkhead", "lamp_bulkhead", 1.3, TR, ty=2.0)
item_obj("lamp_street", "lamp_street", 0.8, "minecraft:cutout", ty=-2.0)
item_obj("lamp_flood", "lamp_flood", 0.62, "minecraft:cutout")
item_obj("beacon_red", "beacon_red", 1.9, TR, ty=3.0)
item_obj("beacon_rotating", "beacon_rotating", 1.6, TR, ty=2.5)

# ---------------------------------------------------------------- searchlight (+ BER parts) and its invisible light spot
B["searchlight"] = dict(ctor=f"new {DK}SearchlightBlock({P_SL})", en="Searchlight", ru="Прожектор (60 см)",
                        den="Right-click: off / fixed / sweep. Sneak + right-click with an empty hand: aim where you look. Lights up where the beam lands; detected hostiles and strangers glow, redstone output while it sees someone; in sweep mode it locks on and follows.",
                        dru="ПКМ: выкл / неподвижно / обзор. Shift + ПКМ пустой рукой — навести туда, куда смотрите. Освещает место, куда падает луч; обнаруженные враги и чужие игроки подсвечиваются, пока цель видна — сигнал редстоуна; в режиме обзора захватывает цель и ведёт её.",
                        tool="pickaxe")
slb = obj_model("deco_searchlight_base", "searchlight_base", particle="deco_paint_olive")
obj_model("deco_searchlight_yoke", "searchlight_yoke", particle="deco_paint_olive")
obj_model("deco_searchlight_drum", "searchlight_drum", particle="deco_paint_olive")
obj_model("deco_searchlight_drum_off", "searchlight_drum", off=True, particle="deco_paint_olive")
obj_model("deco_beacon_reflector", "beacon_reflector")
bs("searchlight", variants(lambda c: {"model": slb, "y": ROT[c["facing"]]}, [H4, LIT]))
item_obj("searchlight", "searchlight_item", 0.46, "minecraft:cutout", ty=-1.0, particle="deco_paint_olive")
B["spotlight"] = dict(ctor=f"new {DK}SpotLightBlock({P_SPOT})", en="Searchlight spot", ru="Пятно прожектора", den="", dru="", tool=None, item=False)
json.dump({"textures": {"particle": "aiarsenal:block/deco_none"}, "elements": []}, open(A + "/models/block/deco_empty.json", "w"), indent=2)
bs("spotlight", {"variants": {"": {"model": "aiarsenal:block/deco_empty"}}})

# ---------------------------------------------------------------- armour plates
ARM = [("armor_plate", None, "Armour plate", "Бронеплита", "Riveted rolled armour steel. Very blast-resistant.", "Клёпаная катаная броневая сталь. Очень стойкая к взрывам."),
       ("armor_plate_hazard", None, "Armour plate (hazard stripes)", "Бронеплита (сигнальная разметка)", "Yellow-black chevrons for gates and barriers.", "Жёлто-чёрная разметка для ворот и шлагбаумов."),
       ("armor_plate_star_red", "star_red", "Armour plate: red star", "Бронеплита: красная звезда", "Soviet red star with a white edge.", "Советская красная звезда с белой окантовкой."),
       ("armor_plate_balkenkreuz", "balkenkreuz", "Armour plate: Balkenkreuz", "Бронеплита: балочный крест", "German vehicle cross, 1940s.", "Немецкий опознавательный крест, 1940-е."),
       ("armor_plate_us_star", "us_star", "Armour plate: US star", "Бронеплита: звезда США", "Allied white star in a circle.", "Белая звезда союзников в круге."),
       ("armor_plate_roundel", "roundel", "Armour plate: RAF roundel", "Бронеплита: кокарда ВВС Великобритании", "Blue-white-red roundel.", "Сине-бело-красная кокарда."),
       ("armor_plate_vdv", "vdv", "Armour plate: VDV emblem", "Бронеплита: эмблема ВДВ", "Parachute and aircraft.", "Парашют и самолёты."),
       ("armor_plate_vmf", "vmf", "Armour plate: Navy anchor", "Бронеплита: якорь ВМФ", "Admiralty anchor.", "Адмиралтейский якорь.")]
for bid, em, en, ru, den, dru in ARM:
    if em is None:
        B[bid] = dict(ctor=f"new net.minecraft.world.level.block.Block({P_ARM})", en=en, ru=ru, den=den, dru=dru, tool="pickaxe", iron=True)
        tex = "deco_armor" if bid == "armor_plate" else "deco_armor_hazard"
        json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": "aiarsenal:block/" + tex}}, open(A + "/models/block/%s.json" % bid, "w"), indent=2)
        bs(bid, {"variants": {"": {"model": "aiarsenal:block/" + bid}}})
    else:
        B[bid] = dict(ctor=f"new {DK}ArmorPlateBlock({P_ARM})", en=en, ru=ru, den=den, dru=dru, tool="pickaxe", iron=True)
        json.dump({"parent": "minecraft:block/orientable", "textures": {"front": "aiarsenal:block/deco_armor_" + em, "side": "aiarsenal:block/deco_armor_side",
                                                                      "top": "aiarsenal:block/deco_armor"}}, open(A + "/models/block/%s.json" % bid, "w"), indent=2)
        bs(bid, variants(lambda c, b=bid: {"model": "aiarsenal:block/" + b, "y": ROT[c["facing"]]}, [H4]))
    item_parent(bid, "aiarsenal:block/" + bid)

# ---------------------------------------------------------------- loot, tags, lang
for bid, v in B.items():
    if v.get("item", True):
        json.dump({"type": "minecraft:block", "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": "aiarsenal:" + bid}]}]},
                  open(D + "/aiarsenal/loot_tables/blocks/%s.json" % bid, "w"), indent=2)
for tag, sel in (("mineable/pickaxe", lambda v: v["tool"] == "pickaxe"), ("needs_iron_tool", lambda v: v.get("iron"))):
    p = D + "/minecraft/tags/blocks/%s.json" % tag
    t = json.load(open(p)) if os.path.exists(p) else {"replace": False, "values": []}
    for bid, v in B.items():
        if sel(v) and "aiarsenal:" + bid not in t["values"]:
            t["values"].append("aiarsenal:" + bid)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(t, open(p, "w"), indent=2)

for bid, v in B.items():
    K["block.aiarsenal." + bid] = (v["en"], v["ru"])
    if v["den"]:
        K["block.aiarsenal." + bid + ".desc1"] = (v["den"], v["dru"])
K["itemGroup.aiarsenal.building"] = ("AI Arsenal: Buildings & decor", "AI Arsenal: Постройки и декор")
K["msg.aiarsenal.searchlight.mode.0"] = ("Searchlight: OFF", "Прожектор: выключен")
K["msg.aiarsenal.searchlight.mode.1"] = ("Searchlight: ON, fixed beam", "Прожектор: включён, луч неподвижен")
K["msg.aiarsenal.searchlight.mode.2"] = ("Searchlight: ON, sweep + lock-on", "Прожектор: включён, обзор + захват цели")
K["msg.aiarsenal.searchlight.aimed"] = ("Searchlight aimed: azimuth %s, elevation %s", "Прожектор наведён: азимут %s°, угол места %s°")
for i, f in enumerate(("en_us.json", "ru_ru.json")):
    d = json.load(open(A + "/lang/" + f), object_pairs_hook=collections.OrderedDict)
    for k, v in K.items():
        d[k] = v[i]
    json.dump(d, open(A + "/lang/" + f, "w"), ensure_ascii=False, indent=2)
    open(A + "/lang/" + f, "a").write("\n")

# ---------------------------------------------------------------- recipes
I = lambda x: {"item": x}
T = lambda x: {"tag": x}
dye = lambda c: I("minecraft:%s_dye" % c)
OUTR = D + "/aiarsenal/recipes"
REC = {}
SHAPELESS = {}
REC["flagpole"] = (["N", "I", "I"], {"N": I("minecraft:gold_nugget"), "I": I("minecraft:iron_ingot")}, 3)
FD = {"russia": ["blue", "red"], "ussr": ["red", "red", "yellow"], "germany": ["black", "red", "yellow"], "usa": ["red", "blue", "white"],
      "uk": ["red", "blue", "blue"], "france": ["blue", "red", "white", "white"], "china": ["red", "yellow", "yellow"], "japan": ["red"],
      "balkenkreuz": ["gray", "black"], "vdv": ["light_blue", "green"], "vmf": ["blue"], "vks": ["light_blue", "yellow"],
      "nato": ["blue", "white"], "un": ["light_blue", "white"], "white": [], "victory": ["red", "yellow", "PAPER"]}
for fid, ds in FD.items():
    ing = [I("aiarsenal:flagpole"), I("minecraft:white_wool")] + [I("minecraft:paper") if x == "PAPER" else dye(x) for x in ds]
    SHAPELESS["flag_" + fid] = (ing, 1)
N, IN, GP, GL, RS = I("minecraft:iron_nugget"), I("minecraft:iron_ingot"), I("minecraft:glass_pane"), I("minecraft:glowstone_dust"), I("minecraft:redstone")
REC["lantern_hurricane"] = ([" N ", "GTG", " I "], {"N": N, "G": GP, "T": I("minecraft:torch"), "I": IN}, 1)
REC["lamp_bulkhead"] = (["NNN", "NGN", "IRI"], {"N": N, "G": GL, "R": RS, "I": IN}, 2)
REC["lamp_street"] = (["III", "  G"], {"I": IN, "G": I("minecraft:glowstone")}, 1)
REC["lamp_flood"] = (["IGI", "IRI", "N N"], {"I": IN, "G": I("minecraft:glowstone"), "R": RS, "N": N}, 1)
REC["beacon_red"] = (["D", "G", "N"], {"D": dye("red"), "G": GL, "N": N}, 2)
REC["beacon_rotating"] = (["D", "G", "R"], {"D": dye("orange"), "G": GL, "R": RS}, 1)
REC["searchlight"] = (["ILI", "GRG", "III"], {"I": IN, "L": I("minecraft:glowstone"), "G": I("minecraft:glass"), "R": RS}, 1)
REC["armor_plate"] = (["SSS", "SOS", "SSS"], {"S": I("aiarsenal:steel_plate"), "O": I("minecraft:obsidian")}, 8)
AD = {"armor_plate_hazard": ["yellow", "black"], "armor_plate_star_red": ["red", "white"], "armor_plate_balkenkreuz": ["black", "white"],
      "armor_plate_us_star": ["white", "white"], "armor_plate_roundel": ["blue", "white", "red"], "armor_plate_vdv": ["light_blue", "white"],
      "armor_plate_vmf": ["blue", "white"]}
for bid, ds in AD.items():
    SHAPELESS[bid] = ([I("aiarsenal:armor_plate")] + [dye(x) for x in ds], 1)
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
# no clash with the existing recipe set (same shaped grid)
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
blk = ["    // >>> r14 deco (tools/gen_deco_r14.py)"]
for bid, v in B.items():
    blk.append('    public static final RegistryObject<Block> %s = BLOCKS.register("%s", () -> %s);' % (const(bid), bid, v["ctor"]))
blk.append("    // <<< r14 deco")
itm = ["    // >>> r14 deco (tools/gen_deco_r14.py)"]
for bid, v in B.items():
    if v.get("item", True):
        itm.append('    public static final RegistryObject<Item> %s = ModItems.blockItem("%s", ModBlocks.%s);' % (const(bid), bid, const(bid)))
itm.append("    // <<< r14 deco")


def put(path, anchor, lines):
    s = open(path).read()
    s = re.sub(r"    // >>> r14 deco.*?// <<< r14 deco\n", "", s, flags=re.S)
    assert s.count(anchor) == 1, (path, anchor)
    s = s.replace(anchor, anchor + "\n".join(lines) + "\n")
    open(path, "w").write(s)


put(J + "/registry/ModBlocks.java", "    // <<< r12 field blocks\n", blk)
put(J + "/registry/ModItems.java", "    // <<< r12 field blocks\n", itm)
tp = J + "/registry/ModTabs.java"
s = open(tp).read()
ids = ", ".join('"%s"' % b for b, v in B.items() if v.get("item", True))
s = re.sub(r"        ModTabs\.put\(Cat\.BUILDING, [^;]*\);\n", "", s)
anchor = "        ModTabs.put(Cat.WEAPONS,"
assert s.count(anchor) == 1
s = s.replace(anchor, "        ModTabs.put(Cat.BUILDING, %s);\n%s" % (ids, anchor))
if "BUILDING;" not in s and "BUILDING," not in s.split("enum Cat")[1]:
    s = s.replace("        WEAPONS;\n", "        WEAPONS,\n        BUILDING;\n")
if "public static final RegistryObject<CreativeModeTab> BUILDING;" not in s:
    s = s.replace("    public static final RegistryObject<CreativeModeTab> WEAPONS;\n",
                  "    public static final RegistryObject<CreativeModeTab> WEAPONS;\n    public static final RegistryObject<CreativeModeTab> BUILDING;\n")
if "BUILDING = ModTabs.tab(" not in s:
    s = s.replace('        FORTIFICATION = ModTabs.tab("fortification"',
                  '        BUILDING = ModTabs.tab("building", Cat.BUILDING, () -> new ItemStack((ItemLike)ModItems.SEARCHLIGHT.get()));\n        FORTIFICATION = ModTabs.tab("fortification"')
open(tp, "w").write(s)
print("deco blocks", len(B), "lang", len(K), "recipes", len(REC) + len(SHAPELESS))
