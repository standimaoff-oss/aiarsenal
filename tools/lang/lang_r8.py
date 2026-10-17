#!/usr/bin/env python3
# reply 8 strings: WW2 vehicles, rounds, ammo items (idempotent)
import json, collections
R = '/home/user/mods/aiarsenal/src/main/resources/assets/aiarsenal/lang/'
S = {}
V = {  # id: (en name, ru name, en desc, ru desc)
 'pz2': ('Pz.Kpfw. II Ausf. F', 'Pz.Kpfw. II Ausf. F',
         'German light tank (1941), 9.5 t, 4.81 x 2.28 x 2.15 m. 2 cm KwK 30 (10-round magazines, HE / AP) + coaxial MG 34. Driver + commander-gunner.',
         'Немецкий лёгкий танк (1941), 9,5 т, 4,81 × 2,28 × 2,15 м. 2-см KwK 30 (магазины по 10, ОФ / ББ) + спаренный MG 34. Механик + командир-наводчик.'),
 'pz4': ('Pz.Kpfw. IV Ausf. H', 'Pz.Kpfw. IV Ausf. H',
         'German medium tank (1943), 25 t, 7.02 x 2.88 x 2.68 m, Schürzen. 7.5 cm KwK 40 L/48 (HE / Pzgr 39) + MG 34.',
         'Немецкий средний танк (1943), 25 т, 7,02 × 2,88 × 2,68 м, экраны. 7,5-см KwK 40 L/48 (ОФ / Pzgr 39) + MG 34.'),
 'tiger': ('Tiger I Ausf. E', 'Тигр I Ausf. E',
           'German heavy tank, 57 t, 8.45 x 3.70 x 3.00 m, interleaved road wheels. 8.8 cm KwK 36 L/56 (HE / Pzgr 39) + MG 34. Slow hydraulic turret traverse.',
           'Немецкий тяжёлый танк, 57 т, 8,45 × 3,70 × 3,00 м, шахматная ходовая. 8,8-см KwK 36 L/56 (ОФ / Pzgr 39) + MG 34. Медленный гидропривод башни.'),
 'sturmtiger': ('Sturmtiger', 'Штурмтигр',
                '38 cm Sturmmörser on the Tiger chassis, 65 t, 6.28 m. RW 61 rocket mortar: 345 kg round, 135 kg charge, up to 5.6 km. Map / designator auto-laying, crane reload.',
                '38-см штурмовой мортирный танк на шасси «Тигра», 65 т, 6,28 м. Реактивный миномёт RW 61: мина 345 кг, ВВ 135 кг, до 5,6 км. Наведение по карте / целеуказателю, заряжание краном.'),
 'maus': ('Pz.Kpfw. VIII Maus', 'Pz.Kpfw. VIII «Маус»',
          'German super-heavy tank, 188 t, 10.09 x 3.67 x 3.63 m. 12.8 cm KwK 44 L/55 (HE / AP) + coaxial 7.5 cm KwK 44 L/36.5 + MG 34. 20 km/h.',
          'Немецкий сверхтяжёлый танк, 188 т, 10,09 × 3,67 × 3,63 м. 12,8-см KwK 44 L/55 (ОФ / ББ) + спаренная 7,5-см KwK 44 L/36.5 + MG 34. 20 км/ч.'),
 'ratte': ('Landkreuzer P.1000 Ratte', 'Landkreuzer P.1000 «Ратте»',
           'German land cruiser project, 1000 t, 35 x 14 x 11 m, three tracks per side. Twin 28 cm SK C/34 naval guns (fire in turn), rear 12.8 cm turret, Flak 38, MG 151/15.',
           'Проект немецкого сухопутного крейсера, 1000 т, 35 × 14 × 11 м, по три гусеницы на борт. Две 28-см корабельные SK C/34 (стреляют поочерёдно), кормовая башня 12,8 см, Flak 38, MG 151/15.'),
 't34': ('T-34-85', 'Т-34-85',
         'Soviet medium tank (1944), 32 t, 8.15 x 3.00 x 2.72 m, Christie suspension. 85 mm ZiS-S-53 (O-365 / BR-365) + coaxial DT, 55 km/h.',
         'Советский средний танк (1944), 32 т, 8,15 × 3,00 × 2,72 м, подвеска Кристи. 85-мм ЗиС-С-53 (О-365 / БР-365) + спаренный ДТ, 55 км/ч.'),
 'kv2': ('KV-2', 'КВ-2',
         'Soviet heavy assault tank (1940), 52 t, 6.95 x 3.32 x 3.24 m, 152 mm M-10T howitzer in a tall turret (OF-530), separate loading.',
         'Советский тяжёлый штурмовой танк (1940), 52 т, 6,95 × 3,32 × 3,24 м, 152-мм гаубица М-10Т в высокой башне (ОФ-530), раздельное заряжание.'),
 'is2': ('IS-2 (1944)', 'ИС-2 (1944)',
         'Soviet heavy breakthrough tank, 46 t, 9.90 x 3.07 x 2.73 m. 122 mm D-25T (OF-471 / BR-471), 2-3 rounds/min, + coaxial DT.',
         'Советский тяжёлый танк прорыва, 46 т, 9,90 × 3,07 × 2,73 м. 122-мм Д-25Т (ОФ-471 / БР-471), 2–3 выстр./мин, + спаренный ДТ.'),
 'kv5': ('KV-5 (Object 225)', 'КВ-5 (Объект 225)',
         'Soviet super-heavy tank project (1941), 100 t, 4.0 m wide, driver tower and MG turret. 107 mm ZiS-6 (OF-420) + DT.',
         'Проект советского сверхтяжёлого танка (1941), 100 т, ширина 4,0 м, башенка механика и пулемётная башня. 107-мм ЗиС-6 (ОФ-420) + ДТ.'),
 'bm13': ('BM-13N Katyusha', 'БМ-13Н «Катюша»',
          'Soviet rocket launcher on the Studebaker US6 6x6: 16 M-13 132 mm rockets on 8 rails, salvo in 7-10 s, up to 8.5 km. Lower the jacks before firing.',
          'Советская реактивная установка на «Студебеккере» US6 6×6: 16 ракет М-13 132 мм на 8 направляющих, залп за 7–10 с, до 8,5 км. Перед стрельбой опустить упоры.'),
}
place_en, place_ru = ' Right-click the ground to place.', ' ПКМ по земле — поставить технику.'
for k, (en, ru, de, dr) in V.items():
    S['entity.aiarsenal.' + k] = (en, ru)
    S['item.aiarsenal.%s_vehicle' % k] = (en, ru)
    S['item.aiarsenal.%s_vehicle.desc' % k] = (de + place_en, dr + place_ru)
RD = {  # profile key: (en, ru)
 'kw20_he': ('2 cm Sprgr HE', '2 см Sprgr ОФ'), 'kw20_ap': ('2 cm Pzgr 40 AP', '2 см Pzgr 40 ББ'),
 'k75_he': ('7.5 cm Sprgr 34 HE', '7,5 см Sprgr 34 ОФ'), 'k75_ap': ('7.5 cm Pzgr 39 APCBC', '7,5 см Pzgr 39 ББ'),
 'k88_he': ('8.8 cm Sprgr HE', '8,8 см Sprgr ОФ'), 'k88_ap': ('8.8 cm Pzgr 39 APCBC', '8,8 см Pzgr 39 ББ'),
 's85_he': ('85 mm O-365 HE', '85 мм О-365 ОФ'), 's85_ap': ('85 mm BR-365 AP', '85 мм БР-365 ББ'),
 's107_he': ('107 mm OF-420 HE', '107 мм ОФ-420'),
 'k128_he': ('12.8 cm Sprgr HE', '12,8 см Sprgr ОФ'), 'k128_ap': ('12.8 cm Pzgr 43 AP', '12,8 см Pzgr 43 ББ'),
 'k75s_he': ('7.5 cm KwK 44 (second gun)', '7,5 см KwK 44 (вторая пушка)'),
 'sk280_he': ('28 cm Sprgr L/4.4', '28 см Sprgr L/4.4'),
 'kv2_he': ('152 mm OF-530 HE', '152 мм ОФ-530'), 'is2_he': ('122 mm OF-471 HE', '122 мм ОФ-471'), 'is2_ap': ('122 mm BR-471 AP', '122 мм БР-471 ББ'),
 'rw61': ('38 cm R-Sprgr 4581', '38 см R-Sprgr 4581'), 'coax_mg': ('Coaxial MG', 'Спаренный пулемёт'),
}
for k, (en, ru) in RD.items():
    S['hud.aiarsenal.rw.gun.round.' + k] = (en, ru)
    S['hud.aiarsenal.rw.gun.sel.' + k] = ('Weapon: ' + en, 'Оружие: ' + ru)
AM = {
 'shells_20': ('2 cm KwK 30 magazines', 'Магазины 2 см KwK 30', 'For the Pz.II: 10-round magazines, HE 0.148 kg / AP.', 'Для Pz.II: магазины по 10 выстрелов, ОФ 0,148 кг / ББ.'),
 'shells_75': ('7.5 cm rounds', 'Выстрелы 7,5 см', 'For the Pz.IV (KwK 40 L/48) and the Maus second gun.', 'Для Pz.IV (KwK 40 L/48) и второй пушки «Мауса».'),
 'shells_88': ('8.8 cm KwK 36 rounds', 'Выстрелы 8,8 см KwK 36', 'For the Tiger I: Sprgr 9.5 kg / Pzgr 39 10.2 kg.', 'Для «Тигра»: Sprgr 9,5 кг / Pzgr 39 10,2 кг.'),
 'shells_85': ('85 mm rounds', 'Выстрелы 85 мм', 'For the T-34-85: O-365 / BR-365.', 'Для Т-34-85: О-365 / БР-365.'),
 'shells_107': ('107 mm rounds', 'Выстрелы 107 мм', 'For the KV-5: OF-420 18.8 kg.', 'Для КВ-5: ОФ-420 18,8 кг.'),
 'shells_128': ('12.8 cm KwK 44 rounds', 'Выстрелы 12,8 см KwK 44', 'For the Maus: separate loading, 28 kg projectile.', 'Для «Мауса»: раздельное заряжание, снаряд 28 кг.'),
 'shells_280': ('28 cm SK C/34 shells', 'Снаряды 28 см SK C/34', 'For the Ratte: 315 kg HE shell + bagged charges.', 'Для «Ратте»: фугасный снаряд 315 кг + картузы.'),
 'rocket_380': ('38 cm RW 61 rocket mortar round', 'Реактивная мина 38 см RW 61', 'For the Sturmtiger: 345 kg, 135 kg charge.', 'Для «Штурмтигра»: 345 кг, ВВ 135 кг.'),
 'rocket_m13': ('M-13 132 mm rocket', 'Реактивный снаряд М-13 132 мм', 'For the BM-13: 42.5 kg, 4.9 kg TNT, range 8.5 km.', 'Для БМ-13: 42,5 кг, ВВ 4,9 кг, дальность 8,5 км.'),
}
for k, (en, ru, de, dr) in AM.items():
    S['item.aiarsenal.' + k] = (en, ru)
    S['item.aiarsenal.%s.desc' % k] = (de, dr)
for lang, idx in (('en_us', 0), ('ru_ru', 1)):
    p = R + lang + '.json'
    d = json.load(open(p, encoding='utf-8'), object_pairs_hook=collections.OrderedDict)
    for k, v in S.items():
        d[k] = v[idx]
    json.dump(d, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
    print(lang, len(d))
