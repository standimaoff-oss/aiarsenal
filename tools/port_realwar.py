#!/usr/bin/env python3
"""Ports the decompiled Real War 0.4.0 mod into the aiarsenal project (one mod).

  * package com.realwar -> ru.aiforge.realwar
  * SRG names (m_12345_ / f_12345_) -> Mojang names via ForgeGradle createSrgToMcp output
  * namespace realwar -> aiarsenal; translation keys: registry keys (item/block/entity/effect/itemGroup/death)
    -> "<type>.aiarsenal.<id>", every other key -> "<type>.aiarsenal.rw.<rest>" (no clashes with existing keys)
  * colliding ids renamed: items fab500/fab1500 -> fab500m54/fab1500m54, entities missile/rocket -> rw_missile/rw_rocket,
    sounds nuke_blast/nuke_far/bomb_whistle -> rw_*; network channel aiarsenal:realwar; configs aiarsenal-realwar-*.toml
  * resources assets/realwar + data/realwar merged into assets/aiarsenal + data/aiarsenal (lang + sounds.json merged)
usage: port_realwar.py <decompiled src dir> <unzipped jar dir> <mod dir> <srg file>
"""
import json, os, re, shutil, sys

SRC, JAR, MOD, SRG = sys.argv[1:5]
JAVA_OUT = os.path.join(MOD, 'src/main/java/ru/aiforge/realwar')
RES = os.path.join(MOD, 'src/main/resources')
REG_TYPES = ('item', 'block', 'entity', 'effect', 'itemGroup', 'death', 'biome', 'enchantment', 'container')
ID_RENAME = {'fab500': 'fab500m54', 'fab1500': 'fab1500m54'}
SOUND_RENAME = {'nuke_blast': 'rw_nuke_blast', 'nuke_far': 'rw_nuke_far', 'bomb_whistle': 'rw_bomb_whistle'}
ENTITY_RENAME = {'missile': 'rw_missile', 'rocket': 'rw_rocket'}

# ---------------------------------------------------------------- SRG map
srg = {}
for line in open(SRG):
    if line.startswith('FD:'):
        a, b = line.split()[1:3]
        srg[a.rsplit('/', 1)[1]] = b.rsplit('/', 1)[1]
    elif line.startswith('MD:'):
        p = line.split()
        srg[p[1].rsplit('/', 1)[1]] = p[3].rsplit('/', 1)[1]
SRG_RE = re.compile(r'\b([mf]_\d+_)\b')


def conv_key(k):
    """Translation key conversion (works on full keys and on prefixes like 'hud.realwar.')."""
    if k.startswith('key.categories.realwar'):
        return 'key.categories.aiarsenal_rw' + k[len('key.categories.realwar'):]
    m = re.match(r'^([A-Za-z_]+(?:\.[A-Za-z_]+)?)\.realwar(\.|$)(.*)$', k)
    if not m:
        return None
    typ, dot, rest = m.groups()
    first = typ.split('.')[0]
    if first in REG_TYPES:
        if first == 'item':
            for a, b in ID_RENAME.items():
                if rest == a:
                    rest = b
        if first == 'entity' and rest in ENTITY_RENAME:
            rest = ENTITY_RENAME[rest]
        return f'{typ}.aiarsenal{dot}{rest}'
    if first == 'subtitles' and rest in SOUND_RENAME:
        rest = SOUND_RENAME[rest]
    return f'{typ}.aiarsenal.rw{dot}{rest}'


def conv_string(s):
    """Converts the contents of one Java string literal."""
    if s == 'realwar':
        return 'aiarsenal'
    ck = conv_key(s)
    if ck is not None:
        return ck
    s = s.replace('realwar:', 'aiarsenal:').replace('/assets/realwar/', '/assets/aiarsenal/')
    s = re.sub(r'^realwar_', 'aiarsenal_rw_', s)
    return s


STR_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')


def port_java(text, rel):
    text = text.replace('com.realwar', 'ru.aiforge.realwar')
    text = SRG_RE.sub(lambda m: srg.get(m.group(1), m.group(1)), text)
    # keep the /realwar command literal (its own tests call "realwar ..."), convert everything else
    text = re.sub(r'Commands\.literal\(\s*(?:\(String\))?"realwar"\s*\)', 'Commands.literal("REALWAR_CMD")', text)
    text = STR_RE.sub(lambda m: '"' + conv_string(m.group(1)) + '"', text)
    text = text.replace('"REALWAR_CMD"', '"realwar"')
    # colliding registry ids
    for a, b in ID_RENAME.items():
        text = re.sub(r'"%s"' % a, '"%s"' % b, text)
    if rel.endswith('registry/ModSounds.java'):
        for a, b in SOUND_RENAME.items():
            text = text.replace('reg("%s")' % a, 'reg("%s")' % b)
    if rel.endswith('registry/ModS3.java') or rel.endswith('registry/ModEntities.java'):
        for a, b in ENTITY_RENAME.items():
            text = re.sub(r'ENTITIES\.register\("%s"' % a, 'ENTITIES.register("%s"' % b, text)
    if rel.endswith('network/ModNetwork.java'):
        text = text.replace('new ResourceLocation("aiarsenal", "main")', 'new ResourceLocation("aiarsenal", "realwar")')
    return text


# ---------------------------------------------------------------- java
if os.path.isdir(JAVA_OUT):
    shutil.rmtree(JAVA_OUT)
n = 0
base = os.path.join(SRC, 'com/realwar')
for root, _, files in os.walk(base):
    for f in files:
        if not f.endswith('.java'):
            continue
        p = os.path.join(root, f)
        rel = os.path.relpath(p, base)
        out = os.path.join(JAVA_OUT, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        open(out, 'w').write(port_java(open(p, encoding='utf-8').read(), rel))
        n += 1
left = 0
for root, _, files in os.walk(JAVA_OUT):
    for f in files:
        left += len(SRG_RE.findall(open(os.path.join(root, f)).read()))
print('java files', n, 'unmapped srg', left)


# ---------------------------------------------------------------- resources
def ren_file(name):
    stem, ext = os.path.splitext(name)
    if stem in ID_RENAME:
        return ID_RENAME[stem] + ext
    return name


def conv_json_text(t):
    t = t.replace('realwar:', 'aiarsenal:')
    for a, b in ID_RENAME.items():
        t = re.sub(r'aiarsenal:(item/|block/)?%s\b' % a, lambda m: 'aiarsenal:' + (m.group(1) or '') + b, t)
    return t


def copy_tree(src, dst, sound_dir=False):
    for root, _, files in os.walk(src):
        for f in files:
            p = os.path.join(root, f)
            rel = os.path.relpath(p, src)
            d, name = os.path.split(rel)
            if sound_dir and os.path.splitext(name)[0] in SOUND_RENAME:
                name = SOUND_RENAME[os.path.splitext(name)[0]] + os.path.splitext(name)[1]
            name = ren_file(name)
            out = os.path.join(dst, d, name)
            os.makedirs(os.path.dirname(out), exist_ok=True)
            if os.path.exists(out):
                print('  !! exists, skipped:', out)
                continue
            if f.endswith('.json') or f.endswith('.mcmeta'):
                open(out, 'w', encoding='utf-8').write(conv_json_text(open(p, encoding='utf-8').read()))
            else:
                shutil.copyfile(p, out)


A = os.path.join(JAR, 'assets/realwar')
OA = os.path.join(RES, 'assets/aiarsenal')
for sub in os.listdir(A):
    if sub in ('lang', 'sounds.json'):
        continue
    copy_tree(os.path.join(A, sub), os.path.join(OA, sub), sound_dir=(sub == 'sounds'))
# glb models of the realwar bombs follow the item rename
for a, b in ID_RENAME.items():
    g = os.path.join(OA, 'models/glb')
    if os.path.exists(os.path.join(g, a + '.glb')):
        os.rename(os.path.join(g, a + '.glb'), os.path.join(g, b + '.glb'))
# sounds.json
sj = json.load(open(os.path.join(A, 'sounds.json')))
osj = json.load(open(os.path.join(OA, 'sounds.json')))
for k, v in sj.items():
    nk = SOUND_RENAME.get(k, k)
    v = json.loads(conv_json_text(json.dumps(v)))
    for s in v.get('sounds', []):
        if isinstance(s, dict) and 'name' in s:
            nm = s['name'].split(':', 1)[1]
            s['name'] = 'aiarsenal:' + SOUND_RENAME.get(nm, nm)
    if 'subtitle' in v:
        v['subtitle'] = conv_key(v['subtitle'].replace('aiarsenal', 'realwar')) or v['subtitle']
    if nk in osj:
        print('  !! sound clash', nk)
    osj[nk] = v
json.dump(osj, open(os.path.join(OA, 'sounds.json'), 'w'), indent=1, ensure_ascii=False)
# lang
for f in ('en_us.json', 'ru_ru.json'):
    src = json.load(open(os.path.join(A, 'lang', f), encoding='utf-8'))
    dst = json.load(open(os.path.join(OA, 'lang', f), encoding='utf-8'))
    clash = 0
    for k, v in src.items():
        nk = conv_key(k) or k
        if nk in dst and dst[nk] != v:
            clash += 1
        dst[nk] = v
    json.dump(dict(sorted(dst.items())), open(os.path.join(OA, 'lang', f), 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
    print(f, 'keys', len(src), 'overwritten', clash)
# data
D = os.path.join(JAR, 'data')
for ns in os.listdir(D):
    copy_tree(os.path.join(D, ns), os.path.join(RES, 'data', 'aiarsenal' if ns == 'realwar' else ns))
# damage types: message ids realwar.x -> aiarsenal.x (lang death.attack keys converted by conv_key)
for root, _, files in os.walk(os.path.join(RES, 'data/aiarsenal/damage_type')):
    for f in files:
        p = os.path.join(root, f)
        t = open(p).read().replace('"realwar.', '"aiarsenal.')
        open(p, 'w').write(t)
print('done')
