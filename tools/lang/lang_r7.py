#!/usr/bin/env python3
# reply 7 strings (idempotent)
import json, collections
R = '/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/lang/'
S = {
 'craft.aiarsenal.weapon.kalibr': ('3M-14 Kalibr cruise missile', 'КР 3М-14 «Калибр»'),
 'hud.aiarsenal.craft.hint_sub2': ('LMB fire (submerged)  G torpedo / Kalibr  Z periscope  X seat  Shift exit',
                                   'ЛКМ пуск (под водой)  G торпеда / «Калибр»  Z перископ  X место  Shift выход'),
 'hud.aiarsenal.craft.hint_sub1': ('W/S telegraph  A/D rudder  Space blow  C flood  V depth hold  H engines',
                                   'W/S телеграф  A/D руль  Space продувка  C приём балласта  V глубина  H двигатели'),
 'hud.aiarsenal.craft.kalibr_target': ('Kalibr target: %s m', 'Цель «Калибра»: %s м'),
}
for lang, idx in (('en_us', 0), ('ru_ru', 1)):
    p = R + lang + '.json'
    d = json.load(open(p, encoding='utf-8'), object_pairs_hook=collections.OrderedDict)
    for k, v in S.items():
        d[k] = v[idx]
    json.dump(d, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
    print(lang, len(d))
