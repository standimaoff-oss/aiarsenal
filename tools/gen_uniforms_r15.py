"""r15: item models, recipes and lang for the uniform sets (idempotent; safe to rerun).
Recipes: RF = green wool, US = brown wool, rank ingredient in the middle (private string, sergeant yellow dye,
captain iron nugget, colonel gold nugget, general gold ingot)."""
import json, collections, os

R = "/home/user/mods/aiarsenal/src/main/resources"
SETS = ["ru_private", "ru_sergeant", "ru_captain", "ru_colonel", "ru_general",
        "us_private", "us_sergeant", "us_captain", "us_colonel", "us_general"]
RANK_EN = {"ru_private": "RF private", "ru_sergeant": "RF sergeant", "ru_captain": "RF captain", "ru_colonel": "RF colonel",
           "ru_general": "RF colonel general", "us_private": "US Army private (PV2)", "us_sergeant": "US Army sergeant (SGT)",
           "us_captain": "US Army captain (CPT)", "us_colonel": "US Army colonel (COL)", "us_general": "US Army general (GEN)"}
RANK_RU = {"ru_private": "рядовой ВС РФ", "ru_sergeant": "сержант ВС РФ", "ru_captain": "капитан ВС РФ", "ru_colonel": "полковник ВС РФ",
           "ru_general": "генерал-полковник ВС РФ", "us_private": "рядовой армии США (PV2)", "us_sergeant": "сержант армии США (SGT)",
           "us_captain": "капитан армии США (CPT)", "us_colonel": "полковник армии США (COL)", "us_general": "генерал армии США (GEN)"}
TIP_EN = {"ru_private": "EMR 'tsifra' field uniform, 6B47 helmet, plain olive shoulder boards.",
          "ru_sergeant": "EMR field uniform, 6B47 helmet; shoulder boards with three gold bars.",
          "ru_captain": "EMR field uniform and field cap; boards: one stripe, four small stars.",
          "ru_colonel": "Olive everyday uniform, peaked cap with a red band; boards: two red stripes, three large stars.",
          "ru_general": "Olive everyday uniform, red trouser stripes, gold-embroidered visor; gold zigzag boards, three large stars.",
          "us_private": "OCP uniform, ACH helmet; PV2 chevron on the chest tab.",
          "us_sergeant": "OCP uniform, ACH helmet; three chevrons on the chest tab.",
          "us_captain": "OCP uniform and patrol cap; two bars on the chest tab and cap.",
          "us_colonel": "Army Green Service Uniform: dark olive coat, taupe trousers, service cap; eagle on the shoulder loops.",
          "us_general": "Army Green Service Uniform; four stars on the shoulder loops, two rows of oak leaves on the visor."}
TIP_RU = {"ru_private": "Полевая форма ЕМР «цифра», шлем 6Б47, погоны без знаков различия.",
          "ru_sergeant": "Полевая форма ЕМР, шлем 6Б47; на погонах три лычки.",
          "ru_captain": "Полевая форма ЕМР и полевое кепи; погоны: один просвет, четыре малые звезды.",
          "ru_colonel": "Повседневная оливковая форма, фуражка с красным околышем; погоны: два красных просвета, три большие звезды.",
          "ru_general": "Повседневная форма, красные лампасы, золотое шитьё на козырьке; погоны с золотым зигзагом, три большие звезды.",
          "us_private": "Форма OCP, шлем ACH; шеврон PV2 на нагрудной нашивке.",
          "us_sergeant": "Форма OCP, шлем ACH; три шеврона на нагрудной нашивке.",
          "us_captain": "Форма OCP и патрульное кепи; две планки на нагрудной нашивке и кепи.",
          "us_colonel": "Служебная форма AGSU: тёмно-оливковый китель, брюки цвета тауп, фуражка; орёл на погонах.",
          "us_general": "Служебная форма AGSU; четыре звезды на погонах, два ряда дубовых листьев на козырьке."}


def piece_names(s):
    ru, field = s.startswith("ru"), not (s.endswith("colonel") or s.endswith("general"))
    cap = s.endswith("captain")
    if ru:
        en = {"head": "Field cap" if cap else ("6B47 helmet" if field else "Peaked cap"),
              "chest": "Ratnik EMR jacket" if field else "Everyday tunic",
              "legs": "Ratnik EMR trousers" if field else ("Trousers with stripes" if s.endswith("general") else "Everyday trousers"),
              "feet": "Combat boots" if field else "Shoes"}
        rr = {"head": "Полевое кепи" if cap else ("Шлем 6Б47" if field else "Фуражка"),
              "chest": "Куртка «Ратник» ЕМР" if field else "Китель повседневный",
              "legs": "Брюки «Ратник» ЕМР" if field else ("Брюки с лампасами" if s.endswith("general") else "Брюки повседневные"),
              "feet": "Берцы" if field else "Туфли"}
    else:
        en = {"head": "OCP patrol cap" if cap else ("ACH helmet" if field else "AGSU service cap"),
              "chest": "OCP coat" if field else "AGSU coat", "legs": "OCP trousers" if field else "AGSU trousers",
              "feet": "Coyote boots" if field else "Brown oxfords"}
        rr = {"head": "Патрульное кепи OCP" if cap else ("Шлем ACH" if field else "Фуражка AGSU"),
              "chest": "Куртка OCP" if field else "Китель AGSU", "legs": "Брюки OCP" if field else "Брюки AGSU",
              "feet": "Ботинки «койот»" if field else "Коричневые туфли"}
    return en, rr


SHAPES = {"head": ["WWW", "WXW"], "chest": ["WXW", "WWW", "WWW"], "legs": ["WWW", "WXW", "W W"], "feet": ["WXW", "W W"]}
RANK_X = {"private": "minecraft:string", "sergeant": "minecraft:yellow_dye", "captain": "minecraft:iron_nugget",
          "colonel": "minecraft:gold_nugget", "general": "minecraft:gold_ingot"}

lang = {"en_us": {"itemGroup.aiarsenal.uniforms": "AI Arsenal: Uniforms"}, "ru_ru": {"itemGroup.aiarsenal.uniforms": "AI Arsenal: Форма"}}
n = 0
for s in SETS:
    en, rr = piece_names(s)
    lang["en_us"]["tooltip.aiarsenal.uniform." + s] = TIP_EN[s]
    lang["ru_ru"]["tooltip.aiarsenal.uniform." + s] = TIP_RU[s]
    for sl in ("head", "chest", "legs", "feet"):
        iid = "uniform_%s_%s" % (s, sl)
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "aiarsenal:item/" + iid}},
                  open("%s/assets/aiarsenal/models/item/%s.json" % (R, iid), "w"), indent=2)
        rec = {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": SHAPES[sl],
               "key": {"W": {"item": "minecraft:green_wool" if s.startswith("ru") else "minecraft:brown_wool"},
                       "X": {"item": RANK_X[s.split("_")[1]]}},
               "result": {"item": "aiarsenal:" + iid}}
        json.dump(rec, open("%s/data/aiarsenal/recipes/%s.json" % (R, iid), "w"), indent=2)
        lang["en_us"]["item.aiarsenal." + iid] = "%s (%s)" % (en[sl], RANK_EN[s])
        lang["ru_ru"]["item.aiarsenal." + iid] = "%s (%s)" % (rr[sl], RANK_RU[s])
        n += 1
for l, d in lang.items():
    p = "%s/assets/aiarsenal/lang/%s.json" % (R, l)
    j = json.load(open(p, encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    j.update(d)
    json.dump(j, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("uniform items", n)
