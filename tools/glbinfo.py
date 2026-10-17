#!/usr/bin/env python3
"""Inspect a GLB: nodes (TRS), meshes/primitives, materials, images, animations."""
import json, struct, sys
b = open(sys.argv[1], 'rb').read()
magic, ver, total = struct.unpack('<III', b[:12])
l = struct.unpack('<I', b[12:16])[0]
j = json.loads(b[20:20 + l])
print(f"GLB v{ver} {total} bytes; json {l}")
print("scenes:", len(j.get('scenes', [])), "nodes:", len(j['nodes']), "meshes:", len(j['meshes']), "materials:", len(j.get('materials', [])),
      "textures:", len(j.get('textures', [])), "images:", len(j.get('images', [])), "animations:", len(j.get('animations', [])), "skins:", len(j.get('skins', [])))
tris = 0
for m in j['meshes']:
    for p in m['primitives']:
        tris += j['accessors'][p['indices']]['count'] // 3
print("triangles (unique meshes):", tris)
verbose = '-v' in sys.argv
for i, n in enumerate(j['nodes']):
    if verbose or not n['name'].startswith('Rocket_'):
        print(f"  node {i:3d} {n['name']:<18} mesh={n.get('mesh','-')} ch={n.get('children',[])[:6]}{'...' if len(n.get('children',[]))>6 else ''} "
              f"t={[round(x,3) for x in n.get('translation',[0,0,0])]} r={[round(x,3) for x in n.get('rotation',[0,0,0,1])]}")
for m in j.get('materials', []):
    pbr = m.get('pbrMetallicRoughness', {})
    print("  mat", m['name'], 'tex' if 'baseColorTexture' in pbr else '-', pbr.get('baseColorFactor'), m.get('alphaMode', ''), 'emis' if m.get('emissiveFactor') else '')
for a in j.get('animations', []):
    tmax = max(j['accessors'][s['input']]['max'][0] for s in a['samplers'])
    print("  anim", a['name'], "channels", len(a['channels']), "len", round(tmax, 3), "s",
          sorted(set(j['nodes'][c['target']['node']]['name'] + '.' + c['target']['path'] for c in a['channels'])))
