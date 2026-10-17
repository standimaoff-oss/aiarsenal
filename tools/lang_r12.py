"""r12 language keys (en_us, ru_ru). Idempotent: python3 tools/lang_r12.py"""
import json, collections
R = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/lang/"
K = {
 "entity.aiarsenal.su57": ("Sukhoi Su-57", "Су-57"),
 "item.aiarsenal.su57_plane": ("Sukhoi Su-57", "Су-57"),
 "craft.aiarsenal.weapon.gsh301": ("GSh-30-1 30 mm", "ГШ-30-1 30 мм"),
 "craft.aiarsenal.weapon.kh38": ("Kh-38ML guided missile", "Ракета Х-38МЛ"),
 "craft.aiarsenal.weapon.fab500s": ("FAB-500M-62", "ФАБ-500М-62"),
 "craft.aiarsenal.load_su57_0": ("Load: universal - 2 S-8 pods, 2 Kh-38, 2 FAB-500 in the bays", "Нагрузка: универсальная — 2 блока С-8, 2 Х-38, 2 ФАБ-500 в отсеках"),
 "craft.aiarsenal.load_su57_1": ("Load: bomber - 6 FAB-500 (4 internal), 2 Kh-38", "Нагрузка: бомбовая — 6 ФАБ-500 (4 во внутренних отсеках), 2 Х-38"),
 "craft.aiarsenal.load_su57_2": ("Load: anti-tank - 6 Kh-38", "Нагрузка: противотанковая — 6 Х-38"),
 "craft.aiarsenal.load_su57_3": ("Load: rockets - 4 S-8 pods (80), 4 FAB-500 internal", "Нагрузка: НАР — 4 блока С-8 (80 шт.), 4 ФАБ-500 внутри"),
 "hud.aiarsenal.craft.mnv_on": ("MANOEUVRE (TVC, no AoA limit)", "МАНЁВР (УВТ, без огр. угла атаки)"),
 "hud.aiarsenal.craft.mnv_off": ("FBW: AoA limit 24°", "СДУ: огр. угла атаки 24°"),
 "hud.aiarsenal.craft.afterburner": ("AFTERBURNER", "ФОРСАЖ"),
 "hud.aiarsenal.craft.hint_plane2_jet": ("LMB fire  G weapon  V gear  N manoeuvre mode  W>86% afterburner  R load (ground)  B flares  Shift exit",
                                         "ЛКМ огонь  G оружие  V шасси  N режим манёвра  W>86% форсаж  R нагрузка (на земле)  B ЛТЦ  Shift выход"),
}
EXTRA = "/home/user/tools/lang_r12_extra.json"
try:
    for k, v in json.load(open(EXTRA)).items(): K[k] = tuple(v)
except FileNotFoundError:
    pass
for i, f in enumerate(("en_us.json", "ru_ru.json")):
    d = json.load(open(R + f), object_pairs_hook=collections.OrderedDict)
    for k, v in K.items(): d[k] = v[i]
    json.dump(d, open(R + f, "w"), ensure_ascii=False, indent=2)
    open(R + f, "a").write("\n")
print("lang keys", len(K))
