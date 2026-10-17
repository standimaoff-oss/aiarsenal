#!/usr/bin/env python3
"""r12: recipes for the field blocks (part 1 of the building blocks). Idempotent; validates keys and ids."""
import json, os, re
R = '/home/user/mods/aiarsenal/src/main/resources'
OUT = R + '/data/aiarsenal/recipes'
lang = json.load(open(R + '/assets/aiarsenal/lang/en_us.json'))
I = lambda x: {'item': x}
T = lambda x: {'tag': x}
N, IN, ST, PL = I('minecraft:iron_nugget'), I('minecraft:iron_ingot'), I('minecraft:stick'), T('minecraft:planks')
TNT, FZ, BW = I('minecraft:tnt'), I('aiarsenal:contact_fuze'), I('aiarsenal:barbed_wire')
rec = {
    'trench_wall':      (['PSP', 'PDP'], {'P': PL, 'S': ST, 'D': I('minecraft:dirt')}, 4),
    'trench_wall_tin':  (['ISI', 'IDI'], {'I': IN, 'S': ST, 'D': I('minecraft:dirt')}, 6),
    'duckboard':        (['P P', 'SSS'], {'P': PL, 'S': ST}, 6),
    'firing_step':      (['P  ', 'DP ', 'DDP'], {'P': PL, 'D': I('minecraft:dirt')}, 4),
    'egoza':            (['BIB', 'I I', 'BIB'], {'B': BW, 'I': IN}, 4),
    'bruno_spiral':     (['BNB', 'N N', 'BNB'], {'B': BW, 'N': N}, 4),
    'mine_pmn2':        (['F', 'T', 'P'], {'F': FZ, 'T': TNT, 'P': I('minecraft:stone_pressure_plate')}, 2),
    'mine_tm62':        (['IFI', 'TTT'], {'I': IN, 'F': FZ, 'T': TNT}, 1),
    'mine_mon50':       (['NNN', 'TFT'], {'N': N, 'T': TNT, 'F': FZ}, 2),
    'mine_ozm72':       (['NFN', 'NTN', 'GNG'], {'N': N, 'F': FZ, 'T': TNT, 'G': I('minecraft:gunpowder')}, 2),
    'tripwire_f1':      (['HST'], {'H': I('minecraft:tripwire_hook'), 'S': I('minecraft:string'), 'T': TNT}, 2),
    'bear_trap':        (['I I', 'NPN', 'III'], {'I': IN, 'N': N, 'P': I('minecraft:stone_pressure_plate')}, 1),
    'entrenching_tool': (['IN', ' S', ' S'], {'I': IN, 'N': N, 'S': ST}, 1),
}
for c, d in (('olive', 'green_dye'), ('red', 'red_dye'), ('blue', 'blue_dye')):
    rec['fuel_drum_' + c] = (['IDI', 'ICI', 'III'], {'I': IN, 'D': I('minecraft:' + d), 'C': I('minecraft:coal')}, 2)
for s, d in (('mines', 'red_dye'), ('radiation', 'yellow_dye'), ('voltage', 'redstone'), ('biohazard', 'orange_dye'),
             ('checkpoint', 'black_dye'), ('noentry', 'white_dye')):
    rec['warning_sign_' + s] = (['NNN', 'NDN', ' S '], {'N': N, 'D': I('minecraft:' + d), 'S': ST}, 3)

sigs = {}
for rid, (pat, key, cnt) in rec.items():
    used = set(''.join(pat)) - {' '}
    assert used == set(key), (rid, used, set(key))
    assert len({len(r) for r in pat}) == 1, rid
    for v in key.values():
        if 'item' in v and v['item'].startswith('aiarsenal:'):
            n = v['item'].split(':')[1]
            assert 'item.aiarsenal.' + n in lang or 'block.aiarsenal.' + n in lang, n
    assert 'item.aiarsenal.' + rid in lang or 'block.aiarsenal.' + rid in lang, rid
    sig = json.dumps([pat, sorted((k, json.dumps(v)) for k, v in key.items())])
    assert sig not in sigs, (rid, sigs.get(sig))
    sigs[sig] = rid
    j = {'type': 'minecraft:crafting_shaped', 'category': 'equipment' if rid == 'entrenching_tool' else 'building',
         'pattern': pat, 'key': key, 'result': {'item': 'aiarsenal:' + rid, 'count': cnt}}
    open(f'{OUT}/{rid}.json', 'w').write(json.dumps(j, indent=2) + '\n')
print('FIELD RECIPES', len(rec))
