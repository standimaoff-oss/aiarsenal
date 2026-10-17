#!/usr/bin/env python3
"""Generic CFR-artifact fixes for the ported Real War sources (pattern based + driven by javac error lines)."""
import os, re, sys
ROOT = sys.argv[1]; LOG = sys.argv[2] if len(sys.argv) > 2 else None
G = [
    (r'case [A-Z]\w*\.([A-Z_][A-Z0-9_]*)\s*->', r'case \1 ->'),
    (r'\(GuiEventListener\)', ''),
    (r'super\.render\(\(Entity\)e,', 'super.render(e,'),
    (r'\(Comparable\)', ''),
    (r'\(RegistryObject<Item>\)ITEMS\.register\(', 'ITEMS.<Item>register('),
    (r'\(RegistryObject<RecipeType<MachineRecipe>>\)TYPES\.register\(', 'TYPES.<RecipeType<MachineRecipe>>register('),
    (r'super\(\(AbstractContainerMenu\)menu,', 'super(menu,'),
]
for root, _, fs in os.walk(ROOT):
    for f in fs:
        p = os.path.join(root, f); s = open(p).read(); o = s
        for a, b in G:
            s = re.sub(a, b, s)
        if s != o:
            open(p, 'w').write(s)
if LOG:
    L = open(LOG).read().split('\n')
    edits = {}
    for i, l in enumerate(L):
        m = re.match(r'(.*\.java):(\d+): error: (.*)', l)
        if m:
            edits.setdefault(m.group(1), []).append((int(m.group(2)), m.group(3)))
    for p, es in edits.items():
        lines = open(p).read().split('\n')
        for n, msg in es:
            s = lines[n - 1]
            if 'EntityDataAccessor<Boolean>,int' in msg or 'bad type in conditional' in msg:
                s = re.sub(r'(\.set\((?:\w+, )?)\((.*) \? 1 : 0\)\);', r'\1\2);', s)
            m2 = re.match(r'Object cannot be converted to (\w+)', msg.replace('incompatible types: ', ''))
            if m2:
                s = re.sub(r'for \((\w+) (\w+) : (.*)\) \{$', lambda m: f'for ({m.group(1)} {m.group(2)} : (Iterable<{m.group(1)}>)(Iterable<?>)({m.group(3)})) {{', s)
            lines[n - 1] = s
        open(p, 'w').write('\n'.join(lines))
print('ok')
