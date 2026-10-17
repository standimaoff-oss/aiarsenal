"""r21: remove the complex nuclear crafting chain (machines, centrifuges, enrichment) - workbench only."""
import json, os, re, shutil, glob
M = '/home/user/mods/aiarsenal/src/main'
J = M + '/java/ru/aiforge/realwar'
R = M + '/resources'
ITEMS = ['yellowcake', 'uf6_cylinder', 'heu_uf6_cylinder', 'heu_ingot', 'irradiated_uranium', 'plutonium_ingot',
         'polonium_capsule', 'raw_bismuth', 'bismuth_ingot', 'fluorite', 'rds_initiator', 'rds_pusher',
         'rds_implosion_assembly', 'rds_tail', 'lb_projectile', 'lb_target', 'lb_gun_assembly', 'lb_tamper', 'lb_tail',
         'explosive_lens']
BLOCKS = ['fluorite_ore', 'deepslate_fluorite_ore', 'bismuth_ore', 'deepslate_bismuth_ore', 'centrifuge',
          'research_reactor', 'radiochem_plant']
RECIPES = ['centrifuge', 'centrifuge_enrichment', 'research_reactor', 'reactor_irradiation', 'reactor_polonium',
           'radiochem_plant', 'radiochem_plutonium', 'uf6_cylinder', 'heu_ingot_blasting', 'yellowcake_smelting',
           'yellowcake_blasting', 'bismuth_ingot_smelting', 'bismuth_ingot_blasting', 'rds_initiator', 'rds_pusher',
           'rds_implosion_assembly', 'rds_tail', 'lb_projectile', 'lb_target', 'lb_gun_assembly', 'lb_tamper', 'lb_tail',
           'explosive_lens']
gone = []
def rm(p):
    if os.path.isdir(p): shutil.rmtree(p); gone.append(p)
    elif os.path.exists(p): os.remove(p); gone.append(p)
# ---- java
rm(J + '/machine'); rm(J + '/client/screen/MachineScreen.java'); rm(J + '/registry/ModMenus.java'); rm(J + '/registry/ModRecipes.java')
def edit(path, fn):
    s = open(path, encoding='utf-8').read(); t = fn(s)
    assert t != s, path
    open(path, 'w', encoding='utf-8').write(t)
def drop_lines(pat):
    return lambda s: '\n'.join(l for l in s.split('\n') if not re.search(pat, l))
upper = '|'.join(x.upper() for x in ITEMS + BLOCKS)
edit(J + '/registry/ModItems.java', drop_lines(r'RegistryObject<Item> (%s) =' % upper))
edit(J + '/registry/ModBlocks.java', drop_lines(r'RegistryObject<Block> (%s) =|import ru\.aiforge\.realwar\.machine\.' % upper))
def blocks_machine_fn(s):
    i = s.index('    private static Supplier<Block> machine(MachineKind kind) {')
    j = s.index('    }\n', i) + 6
    return s[:i] + s[j:]
edit(J + '/registry/ModBlocks.java', blocks_machine_fn)
edit(J + '/registry/ModBlockEntities.java', drop_lines(r'MachineBlockEntity'))
edit(J + '/RealWar.java', drop_lines(r'ModMenus|ModRecipes'))
edit(J + '/client/ClientModEvents.java', drop_lines(r'MachineScreen|ModMenus'))
def tabs(s):
    for x in ITEMS + BLOCKS:
        s = s.replace(', "%s"' % x, '')
    return s.replace('ModBlocks.CENTRIFUGE.get()', 'ModItems.URANIUM_INGOT.get()')
edit(J + '/registry/ModTabs.java', tabs)
def tests(s):
    s = re.sub(r'\n    @GameTest\(template="arena", batch="rw_basic"[^\n]*\)\n    public static void (centrifuge_processes_uf6|reactor_and_radiochem_chain)\(.*?\n    }\n', '\n', s, flags=re.S)
    s = s.replace('''        h.assertTrue(rm.getAllRecipesFor(MachineKind.CENTRIFUGE.recipeType()).size() >= 1, "\\u043d\\u0435\\u0442 \\u0440\\u0435\\u0446\\u0435\\u043f\\u0442\\u043e\\u0432 \\u0446\\u0435\\u043d\\u0442\\u0440\\u0438\\u0444\\u0443\\u0433\\u0438");\n''', '')
    s = re.sub(r'        h\.assertTrue\(rm\.getAllRecipesFor\(MachineKind\.[A-Z]+\.recipeType\(\)\)[^\n]*\n', '', s)
    s = s.replace('    public static void machine_recipes_loaded(', '    public static void nuke_recipes_loaded(')
    s = re.sub(r'import ru\.aiforge\.realwar\.machine\.[A-Za-z]+;\n', '', s)
    return s
edit(J + '/gametest/RealWarTests.java', tests)
# ---- resources
for x in RECIPES: rm(R + '/data/aiarsenal/recipes/%s.json' % x)
for x in ITEMS + BLOCKS:
    rm(R + '/assets/aiarsenal/models/item/%s.json' % x)
    rm(R + '/assets/aiarsenal/blockstates/%s.json' % x)
    rm(R + '/data/aiarsenal/loot_tables/blocks/%s.json' % x)
    for p in glob.glob(R + '/assets/aiarsenal/models/block/%s.json' % x) + glob.glob(R + '/assets/aiarsenal/models/block/%s_on.json' % x):
        rm(p)
    for p in glob.glob(R + '/assets/aiarsenal/textures/*/%s*.png' % x):
        rm(p)
for x in ['bismuth_ore', 'fluorite_ore']:
    rm(R + '/data/aiarsenal/worldgen/configured_feature/%s.json' % x); rm(R + '/data/aiarsenal/worldgen/placed_feature/%s.json' % x)
for p in ['blocks/ores/bismuth', 'blocks/ores/fluorite', 'items/gems/fluorite', 'items/ingots/bismuth', 'items/ingots/plutonium',
          'items/ores/bismuth', 'items/ores/fluorite', 'items/raw_materials/bismuth']:
    rm(R + '/data/forge/tags/%s.json' % p)
def jfix(path, fn):
    d = json.load(open(path, encoding='utf-8')); fn(d)
    json.dump(d, open(path, 'w', encoding='utf-8'), indent=2, ensure_ascii=False); open(path, 'a').write('\n')
def bad(v):
    return isinstance(v, str) and any(re.search(r'(aiarsenal:|#forge:(ores|ingots|gems|raw_materials)/)%s\b' % re.escape(x), v) for x in ITEMS + BLOCKS + ['bismuth', 'fluorite', 'plutonium'])
for p in ['data/forge/tags/blocks/ores.json', 'data/forge/tags/items/ores.json', 'data/minecraft/tags/blocks/mineable/pickaxe.json',
          'data/minecraft/tags/blocks/needs_iron_tool.json', 'data/minecraft/tags/blocks/needs_stone_tool.json']:
    jfix(R + '/' + p, lambda d: d.__setitem__('values', [v for v in d['values'] if not bad(v if isinstance(v, str) else v.get('id'))]))
jfix(R + '/data/aiarsenal/forge/biome_modifier/add_ores.json', lambda d: d.__setitem__('features', ['aiarsenal:uranium_ore']))
def loot(d):
    for pool in d['pools']:
        pool['entries'] = [e for e in pool['entries'] if e.get('name') != 'aiarsenal:yellowcake']
jfix(R + '/data/aiarsenal/loot_tables/crates/secret.json', loot)
# recipes: workbench only
def W(name, d): json.dump(d, open(R + '/data/aiarsenal/recipes/%s.json' % name, 'w'), indent=2)
I = lambda i: {'item': i}
W('uranium_ingot_smelting', {'type': 'minecraft:smelting', 'category': 'misc', 'ingredient': I('aiarsenal:raw_uranium'), 'result': 'aiarsenal:uranium_ingot', 'experience': 0.7, 'cookingtime': 200})
W('uranium_ingot_blasting', {'type': 'minecraft:blasting', 'category': 'misc', 'ingredient': I('aiarsenal:raw_uranium'), 'result': 'aiarsenal:uranium_ingot', 'experience': 0.7, 'cookingtime': 100})
W('rds_core', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['UUU', 'UTU', 'UUU'], 'key': {'U': I('aiarsenal:uranium_ingot'), 'T': I('minecraft:tnt')}, 'result': {'item': 'aiarsenal:rds_core', 'count': 1}})
W('rds_casing', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['PPP', 'I I', 'PPP'], 'key': {'P': I('aiarsenal:steel_plate'), 'I': I('aiarsenal:steel_ingot')}, 'result': {'item': 'aiarsenal:rds_casing', 'count': 1}})
W('lb_casing', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['PPP', 'P P', 'PPP'], 'key': {'P': I('aiarsenal:steel_plate')}, 'result': {'item': 'aiarsenal:lb_casing', 'count': 1}})
SL = lambda name, ing: W(name, {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [I('aiarsenal:' + x) for x in ing], 'result': {'item': 'aiarsenal:' + name, 'count': 1}})
SL('little_boy', ['rds_core', 'lb_casing', 'barometric_fuze'])
SL('rds_4', ['rds_core', 'rds_casing', 'barometric_fuze'])
SL('rds1', ['rds_core', 'rds_core', 'rds_casing', 'barometric_fuze'])
SL('an602', ['rds_core', 'rds_core', 'rds_core', 'rds_core', 'rds_casing', 'rds_casing', 'barometric_fuze'])
# lang
for lang in ['ru_ru', 'en_us']:
    p = R + '/assets/aiarsenal/lang/%s.json' % lang
    d = json.load(open(p, encoding='utf-8'))
    for k in list(d):
        if re.match(r'(item|block|tooltip)\.aiarsenal\.(%s)(\.|$)' % '|'.join(ITEMS + BLOCKS), k) or 'container.aiarsenal.' in k and any(x in k for x in ['centrifuge', 'reactor', 'radiochem']):
            del d[k]
    if lang == 'ru_ru':
        d['item.aiarsenal.rds_core'] = 'Ядро ядерной бомбы'
        d['item.aiarsenal.rds_core.desc'] = 'Верстак: 8 урановых слитков + динамит. Ядро + корпус + барометрический взрыватель = бомба.'
        d['item.aiarsenal.rds_casing'] = 'Корпус ядерной бомбы (РДС)'
        d['item.aiarsenal.rds_casing.desc'] = 'Для РДС-4 (1 ядро), РДС-1 (2 ядра) и АН602 (4 ядра, 2 корпуса).'
        d['item.aiarsenal.lb_casing.desc'] = 'Для Little Boy: ядро + корпус + барометрический взрыватель.'
    else:
        d['item.aiarsenal.rds_core'] = 'Nuclear Bomb Core'
        d['item.aiarsenal.rds_core.desc'] = 'Workbench: 8 uranium ingots + TNT. Core + casing + barometric fuze = bomb.'
        d['item.aiarsenal.rds_casing'] = 'Nuclear Bomb Casing (RDS)'
        d['item.aiarsenal.rds_casing.desc'] = 'For RDS-4 (1 core), RDS-1 (2 cores) and AN602 (4 cores, 2 casings).'
        d['item.aiarsenal.lb_casing.desc'] = 'For Little Boy: core + casing + barometric fuze.'
    json.dump(d, open(p, 'w', encoding='utf-8'), indent=2, ensure_ascii=False); open(p, 'a').write('\n')
print(len(gone), 'removed')
