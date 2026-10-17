#!/usr/bin/env python3
"""Static resource and actual exported GLB regression checks; not visual acceptance."""
import json, unittest,math
from pathlib import Path
from check_ppsh import load_glb,vectors
R=Path(__file__).resolve().parents[1];A=R/'mods/aiarsenal/src/main/resources/assets/aiarsenal';D=R/'mods/aiarsenal/src/main/resources/data'
NAMES=['mosin_pu','k98k_zf39','c96','tt33']
class Stage1cTests(unittest.TestCase):
 def test_retired_data_has_no_uranium(self):
  for p in D.rglob('*.json'):
   self.assertNotIn('uranium',p.read_text(),str(p))
  for p in ['forge/biome_modifier/add_ores.json','worldgen/placed_feature/uranium_ore.json']:
   self.assertFalse((D/'aiarsenal'/p).exists())
 def test_new_shapeless_recipes_do_not_collide(self):
  recipes={p.stem:json.loads(p.read_text()) for p in (D/'aiarsenal/recipes').glob('*.json')}
  signature=lambda r:tuple(sorted(json.dumps(x,sort_keys=True) for x in r['ingredients']))
  for name in NAMES+['round_762x54r','round_792x57','clip_c96','mag_tt33']:
   recipe=recipes[name];self.assertEqual(recipe['type'],'minecraft:crafting_shapeless')
   matching=[other for other,r in recipes.items() if r.get('type')==recipe['type'] and signature(r)==signature(recipe)]
   self.assertEqual(matching,[name],f'ambiguous recipe: {name}: {matching}')
 def test_weapons_resources(self):
  for lang in ['ru_ru','en_us']:
   labels=json.loads((A/'lang'/f'{lang}.json').read_text())
   for name in NAMES:
    self.assertIn('item.aiarsenal.'+name,labels)
    self.assertIn('tooltip.aiarsenal.gun.'+name,labels)
    self.assertTrue((A/'models/item'/f'{name}.json').exists())
    self.assertTrue((D/'aiarsenal/recipes'/f'{name}.json').exists())
 def test_exported_models_textures_nodes_and_animation(self):
  for name in NAMES:
   doc,bin=load_glb(A/'models/glb'/f'{name}.glb')
   nodes={n['name'] for n in doc['nodes']}
   for part in ['body','gun','bolt','muzzle','sight','arm_l','arm_r']:self.assertIn(name+'.'+part,nodes)
   if name in NAMES[:2]:self.assertIn(name+'.optic',nodes);self.assertNotIn(name+'.clip',nodes)
   anim={a['name'] for a in doc.get('animations',[])}
   self.assertTrue({name+'_reload',name+'_reload_empty'}<=anim,(name,anim))
   if name in NAMES[:2]:self.assertIn(name+'_bolt',anim)
   self.assertGreater(len(doc.get('images',[])),1)
   for image in doc['images']:self.assertIn('bufferView',image);self.assertNotIn('uri',image)
   for mesh in doc['meshes']:
    for prim in mesh['primitives']:
     self.assertIn('TEXCOORD_0',prim['attributes'])
     self.assertTrue(all(math.isfinite(v) for point in vectors(doc,bin,prim['attributes']['POSITION']) for v in point))
 def test_ammo_models_and_blend(self):
  for name in ['round_762x54r','round_792x57','clip_c96','mag_tt33']:
   doc,_=load_glb(A/'models/glb'/f'{name}.glb');self.assertTrue(doc['meshes'])
  self.assertTrue((R/'models/weapons_stage1c.blend').exists())
if __name__=='__main__':unittest.main(verbosity=2)
