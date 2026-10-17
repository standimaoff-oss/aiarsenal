import unittest,json
from PIL import Image
from historical_uniforms import ROOT,SETS
from check_ppsh import load_glb,vectors
A=ROOT/'mods/aiarsenal/src/main/resources/assets/aiarsenal'; D=ROOT/'mods/aiarsenal/src/main/resources/data/aiarsenal'
class HistoricalUniforms(unittest.TestCase):
 def test_catalogue_and_recipes(self):
  self.assertEqual(len(SETS),16)
  recipes={p.stem:json.loads(p.read_text()) for p in (D/'recipes').glob('*.json')}
  def signature(r):
   if r.get('type')!='minecraft:crafting_shaped':return None
   return tuple(tuple(json.dumps(r['key'].get(c,{}),sort_keys=True) for c in row) for row in r['pattern'])
  for name in SETS:
   for slot in ['head','chest','legs','feet']:
    id='uniform_'+name+'_'+slot;r=recipes[id];self.assertEqual(r['result']['item'],'aiarsenal:'+id)
    matches=[k for k,v in recipes.items() if signature(v)==signature(r)]
    self.assertEqual(matches,[id],matches)
    self.assertTrue((A/'models/item'/f'{id}.json').exists())
    for lang in ['ru_ru','en_us']:self.assertIn('item.aiarsenal.'+id,json.loads((A/'lang'/f'{lang}.json').read_text()))
 def test_packed_blender_and_real_glb(self):
  self.assertTrue((ROOT/'models/historical_uniforms.blend').exists())
  doc,blob=load_glb(A/'models/glb/historical_uniforms.glb'); names=[n.get('name','') for n in doc['nodes']]
  for name in SETS:
   for part in ['head','body','arm_r','arm_l','leg_r','leg_l','boot_r','boot_l']:
    self.assertTrue(any(n.startswith(name+'.'+part+'.') for n in names),(name,part))
   for slot in ['head','chest','legs','feet']:
    icon=A/'textures/item'/f'uniform_{name}_{slot}.png'
    self.assertGreater(icon.stat().st_size,100)
    with Image.open(icon) as image:self.assertIsNotNone(image.getchannel('A').getbbox(),str(icon))
  self.assertGreaterEqual(len(doc.get('images',[])),16)
  for image in doc['images']:self.assertIn('bufferView',image);self.assertNotIn('uri',image)
 def test_sleeves_leave_the_palms_exposed(self):
  doc,blob=load_glb(A/'models/glb/historical_uniforms.glb')
  for name in SETS:
   for side in ['arm_r','arm_l']:
    ys=[]
    for n in doc['nodes']:
     if n.get('name','').startswith(name+'.'+side+'.') and 'mesh' in n:
      for p in doc['meshes'][n['mesh']]['primitives']:
       ys.extend(v[1] for v in vectors(doc,blob,p['attributes']['POSITION']))
    self.assertTrue(ys)
    self.assertGreater(min(ys),.84,(name,side,min(ys)))
    self.assertLess(max(ys),1.54,(name,side,max(ys)))
 def test_grip_not_inventory_normalization(self):
  table=json.loads((A/'handheld_view.json').read_text())
  for name in ['c96','tt33','ppsh41','mosin_pu','k98k_zf39','ak12','m4a1']:
   for hand in ['right','left']:
    v=table[name+'.thirdperson_'+hand+'hand'];self.assertEqual(v[7],0);self.assertGreaterEqual(v[6],1);self.assertEqual(v[4],0)
if __name__=='__main__':unittest.main(verbosity=2)
