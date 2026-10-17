#!/usr/bin/env python3
# reply 9 strings: single-seat control hints, Ratte secondary turret / flak HUD, updated descriptions (idempotent)
import json, collections
R = '/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/lang/'
S = {
    'hud.aiarsenal.rw.g.mouse': ('Mouse', 'Мышь'),
    'hud.aiarsenal.rw.g.aim': ('aim', 'прицеливание'),
    'hud.aiarsenal.rw.ratte.sec': ('12.8 cm rear turret', 'Кормовая башня 12,8 см'),
    'hud.aiarsenal.rw.ratte.flak': ('Flak 38', 'ПВО Flak 38'),
    'hud.aiarsenal.rw.ratte.no_air': ('no air targets', 'воздушных целей нет'),
    'hud.aiarsenal.rw.ratte.engaging': ('engaging', 'огонь по'),
    'item.aiarsenal.pz2.desc': (
        'German light tank (1941), 9.5 t, 4.81 x 2.28 x 2.15 m. 2 cm KwK 30 (10-round magazines, HE / AP) + coaxial MG 34. One crewman: WASD drive, mouse aim, LMB fire, RMB sight.',
        'Немецкий лёгкий танк (1941), 9,5 т, 4,81 × 2,28 × 2,15 м. 2-см KwK 30 (магазины по 10, ОФ / ББ) + спаренный MG 34. Управляет один: WASD — ход, мышь — наводка, ЛКМ — выстрел, ПКМ — прицел.'),
    'item.aiarsenal.ratte.desc': (
        'German land cruiser project, 1000 t, 35 x 14 x 11 m, plain panzer grey. Twin 28 cm SK C/34 (fire in turn) under your mouse; the aft 12.8 cm turret and eight Flak 38 in sunken tubs fight on their own. Side door + stair: walk through the driver room, turret basket, magazine and engine room.',
        'Проект немецкого сухопутного крейсера, 1000 т, 35 × 14 × 11 м, серый без камуфляжа. Две 28-см SK C/34 (поочерёдно) наводятся мышью; кормовая башня 12,8 см и восемь Flak 38 в утопленных гнёздах воюют сами. Бортовая дверь с трапом: можно пройти рубку, боевое отделение, погреб и моторный отсек.'),
    'hud.aiarsenal.craft.hint_boat2': ('Mouse aims the guns, LMB fire, RMB sight  G weapon  Z zoom', 'Мышь — наводка, ЛКМ огонь, ПКМ прицел  G оружие  Z зум'),
}
for lang, idx in (('en_us', 0), ('ru_ru', 1)):
    p = R + lang + '.json'
    d = json.load(open(p, encoding='utf-8'), object_pairs_hook=collections.OrderedDict)
    for k, v in S.items():
        d[k] = v[idx]
    json.dump(d, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
    print(lang, len(d))
