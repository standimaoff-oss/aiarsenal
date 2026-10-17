"""r19: item models, recipes and lang for the six era/branch uniform sets (idempotent). Icons are rendered by
tools/blender/uniforms.py -- icons. Recipe = gen_uniforms_r15 shapes with a set-specific cloth (W) and emblem (X)."""
import json, collections

R = "/home/user/mods/aiarsenal/src/main/resources"
SHAPES = {"head": ["WWW", "WXW"], "chest": ["WXW", "WWW", "WWW"], "legs": ["WWW", "WXW", "W W"], "feet": ["WXW", "W W"]}
D = {  # set: (cloth, emblem, set en, set ru, tip en, tip ru, {slot: (en, ru)})
    "su_afghan": ("yellow_wool", "red_dye", "Soviet Army, Afghanistan", "Советская армия, Афганистан",
                  "1980s M88 'afghanka' in khaki, panama hat with a red star, 'СА' field shoulder boards, kirza boots.",
                  "Полевая форма образца 1988 г. «афганка», панама со звёздочкой, полевые погоны «СА», кирзовые сапоги.",
                  {"head": ("Panama hat", "Панама"), "chest": ("'Afghanka' jacket", "Куртка «афганка»"),
                   "legs": ("'Afghanka' trousers", "Брюки «афганка»"), "feet": ("Kirza boots", "Кирзовые сапоги")}),
    "su_tanker": ("black_wool", "leather", "Soviet tank crew", "советский танкист",
                  "Black-blue tank crew overall and padded tank helmet with headphones, kirza boots.",
                  "Тёмно-синий танковый комбинезон, танковый шлемофон с наушниками, кирзовые сапоги.",
                  {"head": ("Tank helmet (shlemofon)", "Шлемофон"), "chest": ("Tank overall, top", "Комбинезон танкиста, верх"),
                   "legs": ("Tank overall, trousers", "Комбинезон танкиста, брюки"), "feet": ("Kirza boots", "Кирзовые сапоги")}),
    "us_vietnam": ("green_wool", "vine", "US Army, Vietnam", "армия США, Вьетнам",
                   "OG-107 olive fatigues, M1 helmet with a 'Mitchell' cover and elastic band, jungle boots.",
                   "Оливковая полевая форма OG-107, шлем M1 с чехлом «Митчелл» и резинкой, ботинки для джунглей.",
                   {"head": ("M1 helmet, Mitchell cover", "Шлем M1 с чехлом «Митчелл»"), "chest": ("OG-107 jacket", "Куртка OG-107"),
                    "legs": ("OG-107 trousers", "Брюки OG-107"), "feet": ("Jungle boots", "Ботинки для джунглей")}),
    "us_desert": ("yellow_wool", "sand", "US Army, Desert Storm", "армия США, «Буря в пустыне»",
                  "DCU three-colour desert uniform, PASGT helmet with desert cover and dust goggles, desert boots.",
                  "Трёхцветная пустынная форма DCU, шлем PASGT в пустынном чехле с очками, пустынные ботинки.",
                  {"head": ("PASGT helmet with goggles", "Шлем PASGT с очками"), "chest": ("DCU coat", "Куртка DCU"),
                   "legs": ("DCU trousers", "Брюки DCU"), "feet": ("Desert boots", "Пустынные ботинки")}),
    "ru_vdv": ("green_wool", "light_blue_dye", "Russian Airborne (VDV)", "ВДВ России",
               "'Gorka' suit with reinforcements, telnyashka, sky-blue beret, VDV sleeve patch, black boots.",
               "Костюм «Горка» с усилениями, тельняшка, голубой берет, нарукавный знак ВДВ, чёрные берцы.",
               {"head": ("Blue beret", "Голубой берет"), "chest": ("'Gorka' jacket with telnyashka", "Куртка «Горка» с тельняшкой"),
                "legs": ("'Gorka' trousers", "Брюки «Горка»"), "feet": ("Black boots", "Чёрные берцы")}),
    "ru_pilot": ("gray_wool", "feather", "Russian Aerospace Forces pilot", "лётчик ВКС России",
                 "Flight coverall with harness, PPK anti-g suit, ZSh-7 helmet with smoke visor and KM-34 oxygen mask.",
                 "Лётный комбинезон с подвесной системой, противоперегрузочный костюм ППК, шлем ЗШ-7 со светофильтром и маской КМ-34.",
                 {"head": ("ZSh-7 helmet with mask", "Шлем ЗШ-7 с маской"), "chest": ("Flight coverall, top", "Лётный комбинезон, верх"),
                  "legs": ("Anti-g trousers", "Противоперегрузочные брюки"), "feet": ("Flight boots", "Лётные ботинки")}),
}
lang = {"en_us": {}, "ru_ru": {}}
n = 0
for s, (w, x, sen, sru, ten, tru, names) in D.items():
    lang["en_us"]["tooltip.aiarsenal.uniform." + s] = ten
    lang["ru_ru"]["tooltip.aiarsenal.uniform." + s] = tru
    for sl in ("head", "chest", "legs", "feet"):
        iid = "uniform_%s_%s" % (s, sl)
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "aiarsenal:item/" + iid}},
                  open("%s/assets/aiarsenal/models/item/%s.json" % (R, iid), "w"), indent=2)
        json.dump({"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": SHAPES[sl],
                   "key": {"W": {"item": "minecraft:" + w}, "X": {"item": "minecraft:" + x}},
                   "result": {"item": "aiarsenal:" + iid}},
                  open("%s/data/aiarsenal/recipes/%s.json" % (R, iid), "w"), indent=2)
        lang["en_us"]["item.aiarsenal." + iid] = "%s (%s)" % (names[sl][0], sen)
        lang["ru_ru"]["item.aiarsenal." + iid] = "%s (%s)" % (names[sl][1], sru)
        n += 1
for l, d in lang.items():
    p = "%s/assets/aiarsenal/lang/%s.json" % (R, l)
    j = json.load(open(p, encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    j.update(d)
    json.dump(j, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("r19 uniform items", n)
