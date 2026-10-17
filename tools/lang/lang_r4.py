#!/usr/bin/env python3
"""Lang entries for reply 4 (gun vehicles, shells). Merges into en_us / ru_ru (idempotent)."""
import json, collections
B = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/lang/"
V = {  # id: (en name, ru name, en desc, ru desc)
 "t90m": ("T-90M Proryv", "Т-90М «Прорыв»", "Main battle tank, 125 mm 2A46M-4, autoloader 22 rounds, Relikt ERA. Driver + gunner (switch seats with the seat key).", "Основной боевой танк: 125-мм 2А46М-4, автомат заряжания на 22 выстрела, ДЗ «Реликт». Механик-водитель + наводчик (смена места — клавиша места)."),
 "abrams": ("M1A2 SEPv3 Abrams", "M1A2 SEPv3 «Абрамс»", "US main battle tank, 120 mm M256, 34 ready rounds in the bustle, gas-turbine 1500 hp.", "Американский ОБТ: 120-мм M256, 34 выстрела в кормовой нише башни, ГТД 1500 л.с."),
 "bmp3": ("BMP-3", "БМП-3", "Infantry fighting vehicle: 100 mm 2A70 launcher-gun + coaxial 30 mm 2A72 autocannon (switch with the lock key).", "Боевая машина пехоты: 100-мм орудие-пусковая 2А70 + спаренная 30-мм автоматическая пушка 2А72 (переключение — клавиша захвата)."),
 "msta": ("2S19 Msta-S", "2С19 «Мста-С»", "152 mm self-propelled howitzer 2A64, 50 rounds, range ~24.7 km. Map target / designator auto-laying.", "152-мм самоходная гаубица 2А64, 50 выстрелов, дальность ~24,7 км. Автонаведение по цели на карте / целеуказателю."),
 "d30": ("D-30 (2A18) howitzer", "Гаубица Д-30 (2А18)", "122 mm towed howitzer on a three-trail carriage, all-round traverse, range ~15.4 km.", "122-мм буксируемая гаубица на трёхстанинном лафете, круговой обстрел, дальность ~15,4 км."),
 "m777": ("M777A2 howitzer", "Гаубица M777A2", "155 mm lightweight towed howitzer (titanium), +-22.5 deg traverse, range ~24 km.", "155-мм лёгкая буксируемая гаубица (титан), сектор ±22,5°, дальность ~24 км."),
}
SH = {  # item: (en, ru, en desc, ru desc)
 "shells_125": ("125 mm tank rounds", "Выстрелы 125 мм", "For the T-90M: HE-FRAG 3OF26 and APFSDS 3BM60 (selected in the vehicle).", "Для Т-90М: ОФС 3ОФ26 и БОПС 3БМ60 (тип выбирается в машине)."),
 "shells_120": ("120 mm tank rounds", "Выстрелы 120 мм", "For the M1A2: M830A1 MPAT and M829A4 APFSDS.", "Для M1A2: M830A1 MPAT и БОПС M829A4."),
 "shells_100": ("100 mm rounds 3UOF19", "Выстрелы 100 мм 3УОФ19", "For the BMP-3 2A70 gun (HE-FRAG 3OF32). Reloading also tops up the 30 mm belt.", "Для орудия 2А70 БМП-3 (ОФС 3ОФ32). При заряжании пополняется и лента 30 мм."),
 "shells_122": ("122 mm shells OF-462", "Снаряды 122 мм ОФ-462", "For the D-30 howitzer, 21.8 kg HE-FRAG.", "Для гаубицы Д-30, осколочно-фугасный 21,8 кг."),
 "shells_152": ("152 mm shells 3OF45", "Снаряды 152 мм 3ОФ45", "For the 2S19 Msta-S, 43.6 kg HE-FRAG.", "Для 2С19 «Мста-С», осколочно-фугасный 43,6 кг."),
 "shells_155": ("155 mm shells M795", "Снаряды 155 мм M795", "For the M777, 46.7 kg HE.", "Для M777, фугасный 46,7 кг."),
}
EN = {"hud.aiarsenal.rw.g.gun_fire": "Fire", "hud.aiarsenal.rw.g.gun_fire_coax": "Fire (100 mm / 30 mm)", "hud.aiarsenal.rw.g.gun_sel": "Switch round",
      "hud.aiarsenal.rw.g.gun_auto": "Auto-lay on target", "hud.aiarsenal.rw.gun.loading": "LOADING", "hud.aiarsenal.rw.gun.laser": "LRF",
      "hud.aiarsenal.rw.gun.auto": "AUTO", "hud.aiarsenal.rw.gun.direct": "Direct laying", "hud.aiarsenal.rw.gun.no_target": "No target: set it on the map or with the designator",
      "subtitles.aiarsenal.rw.cannon_tank": "Tank gun fires", "subtitles.aiarsenal.rw.cannon_howitzer": "Howitzer fires", "subtitles.aiarsenal.rw.shell_whistle": "Shell whistles", "entity.aiarsenal.rw_shell": "Shell"}
RU = {"hud.aiarsenal.rw.g.gun_fire": "Выстрел", "hud.aiarsenal.rw.g.gun_fire_coax": "Огонь (100 мм / 30 мм)", "hud.aiarsenal.rw.g.gun_sel": "Сменить снаряд",
      "hud.aiarsenal.rw.g.gun_auto": "Автонаведение на цель", "hud.aiarsenal.rw.gun.loading": "ЗАРЯЖАНИЕ", "hud.aiarsenal.rw.gun.laser": "ДАЛЬНОМЕР",
      "hud.aiarsenal.rw.gun.auto": "АВТО", "hud.aiarsenal.rw.gun.direct": "Прямая наводка", "hud.aiarsenal.rw.gun.no_target": "Нет цели: задайте её на карте или целеуказателем",
      "subtitles.aiarsenal.rw.cannon_tank": "Выстрел танковой пушки", "subtitles.aiarsenal.rw.cannon_howitzer": "Выстрел гаубицы", "subtitles.aiarsenal.rw.shell_whistle": "Свист снаряда", "entity.aiarsenal.rw_shell": "Снаряд"}
ROUNDS = {"t125_he": ("HE-FRAG 3OF26", "ОФС 3ОФ26"), "t125_ap": ("APFSDS 3BM60", "БОПС 3БМ60"), "t120_he": ("MPAT M830A1", "M830A1 MPAT"),
          "t120_ap": ("APFSDS M829A4", "БОПС M829A4"), "b100_he": ("100 mm HE-FRAG 3OF32", "100 мм ОФС 3ОФ32"), "coax30": ("30 mm 2A72", "30 мм 2А72"),
          "d30_he": ("122 mm OF-462", "122 мм ОФ-462"), "msta_he": ("152 mm 3OF45", "152 мм 3ОФ45"), "m777_he": ("155 mm M795", "155 мм M795")}
for k, (e, r) in ROUNDS.items():
    EN["hud.aiarsenal.rw.gun.round." + k] = e; RU["hud.aiarsenal.rw.gun.round." + k] = r
    EN["hud.aiarsenal.rw.gun.sel." + k] = "Round: " + e; RU["hud.aiarsenal.rw.gun.sel." + k] = "Снаряд: " + r
for vid, (en, ru, ed, rd) in V.items():
    EN["entity.aiarsenal." + vid] = en; RU["entity.aiarsenal." + vid] = ru
    EN["item.aiarsenal.%s_vehicle" % vid] = en; RU["item.aiarsenal.%s_vehicle" % vid] = ru
    EN["item.aiarsenal.%s_vehicle.desc" % vid] = ed + " Right-click the ground to place."; RU["item.aiarsenal.%s_vehicle.desc" % vid] = rd + " ПКМ по земле — поставить технику."
for iid, (en, ru, ed, rd) in SH.items():
    EN["item.aiarsenal." + iid] = en; RU["item.aiarsenal." + iid] = ru
    EN["item.aiarsenal.%s.desc" % iid] = ed; RU["item.aiarsenal.%s.desc" % iid] = rd
for fn, add in (("en_us.json", EN), ("ru_ru.json", RU)):
    d = json.load(open(B + fn), object_pairs_hook=collections.OrderedDict)
    d.update(add)
    json.dump(d, open(B + fn, "w"), ensure_ascii=False, indent=1)
    print(fn, len(d))
S = "/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/sounds.json"
d = json.load(open(S), object_pairs_hook=collections.OrderedDict)
for n in ("cannon_tank", "cannon_howitzer", "shell_whistle"):
    d[n] = {"sounds": [{"name": "aiarsenal:" + n}], "subtitle": "subtitles.aiarsenal.rw." + n}
json.dump(d, open(S, "w"), ensure_ascii=False, indent=1)
