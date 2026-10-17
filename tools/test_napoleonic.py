"""Checks actual Blender exports and packaged resources; not a visual acceptance substitute."""
import unittest,json,math,hashlib
from pathlib import Path
from check_ppsh import load_glb,vectors
from gen_napoleonic_resources import NAMES
R=Path(__file__).resolve().parents[1];A=R/'mods/aiarsenal/src/main/resources/assets/aiarsenal';D=A.parents[1]/'data/aiarsenal/recipes'
class Napoleonic(unittest.TestCase):
 def test_resources_recipes_and_labels(self):
  recipes={p.stem:json.loads(p.read_text()) for p in D.glob('*.json')}
  signature=lambda r:sorted(json.dumps(i,sort_keys=True) for i in r['ingredients'])
  for name in NAMES:
   self.assertTrue((A/'models/item'/f'{name}.json').exists())
   for lang in ['ru_ru','en_us']:self.assertIn('item.aiarsenal.'+name,json.loads((A/'lang'/f'{lang}.json').read_text()));self.assertIn('hud.aiarsenal.gun.name.'+name,json.loads((A/'lang'/f'{lang}.json').read_text()))
   self.assertEqual([k for k,v in recipes.items() if v.get('type')=='minecraft:crafting_shapeless' and signature(v)==signature(recipes[name])],[name])
 def test_models_have_packed_textures_finite_geometry_and_nodes(self):
  self.assertTrue((R/'models/napoleonic_arms.blend').exists())
  for name in NAMES:
   doc,data=load_glb(A/'models/glb'/f'{name}.glb');nodes={n['name'] for n in doc['nodes']}
   self.assertIn(name+'.body',nodes)
   for image in doc['images']:self.assertIn('bufferView',image);self.assertNotIn('uri',image)
   for mesh in doc['meshes']:
    for p in mesh['primitives']:
     self.assertIn('TEXCOORD_0',p['attributes']);self.assertTrue(all(math.isfinite(x) for v in vectors(doc,data,p['attributes']['POSITION']) for x in v))
   if name.startswith('cannon_'):self.assertTrue({name+'.barrel',name+'.muzzle'}<=nodes)
   if name.startswith('musket') or name in ['pistol_an13','pistol_1809']:
    self.assertTrue({name+'.'+n for n in ['cock','ramrod','rod_stowed','charge','muzzle','sight','arm_r','arm_l']}<=nodes)
    self.assertTrue({name+'_reload',name+'_reload_empty'}<={a['name'] for a in doc['animations']})
 def test_distinct_models_and_original_sound_headroom(self):
  import soundfile as sf,numpy as np
  for a,b in [('musket_1777','musket_1808'),('pistol_an13','pistol_1809'),('cannon_fr6','cannon_ri12'),('sabre_briquet','sabre_1809')]:
   self.assertNotEqual((A/'models/glb'/f'{a}.glb').read_bytes(),(A/'models/glb'/f'{b}.glb').read_bytes())
  for name in ['musket_fire','flintlock_fire','powder_cannon','paper_load','ramrod']:
   data,rate=sf.read(A/'sounds'/f'{name}.ogg');self.assertEqual(rate,44100);self.assertGreater(np.max(abs(data)),.02);self.assertLess(np.max(abs(data)),.8)
 def test_no_shell_explosion_or_nuclear_replacement(self):
  java=R/'mods/aiarsenal/src/main/java/ru/aiforge'
  ball=(java/'realwar/napoleonic/CannonballEntity.java').read_text();self.assertNotIn('.explode(',ball);self.assertIn('damageSources().thrown',ball)
  shader=(A/'shaders/volpyro/arsenal_smoke.glsl').read_text();self.assertIn('arsenalSmokeField',shader);self.assertIn('arsenalSmokeAlbedo',shader)
  effects=(java/'arsenal/volumetric/client/VolumetricEffects.java').read_text();self.assertIn('VolumetricFxManager.addNuke(',effects);self.assertIn('VolumetricFxManager.arsenalBlast(',effects)
if __name__=='__main__':unittest.main(verbosity=2)
