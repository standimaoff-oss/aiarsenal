"""Offline equipment resource contract checks; gameplay is covered by WWIEquipmentTests.

Also covers the GLB primitive winding, which the Mark I armour prisms depend on."""
import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
A=ROOT/'mods/aiarsenal/src/main/resources/assets/aiarsenal'
D=ROOT/'mods/aiarsenal/src/main/resources/data/aiarsenal'
class EquipmentAssets(unittest.TestCase):
 def test_models_reference_existing_textures(self):
  for item in ('mills_bomb','stielhandgranate','wex','wwi_fuel'):
   model=json.loads((A/f'models/item/{item}.json').read_text())
   self.assertGreater(len(model['elements']),1)
   for element in model['elements']:
    self.assertTrue(all(a<b for a,b in zip(element['from'],element['to'])))
    self.assertTrue(all(-16<=v<=32 for v in element['from']+element['to']))
   for value in model['textures'].values():
    self.assertTrue((A/('textures/'+value.split(':',1)[1]+'.png')).exists())
 def test_recipes_and_locales(self):
  for item in ('mills_bomb','stielhandgranate','wex','wwi_fuel'):
   recipe=json.loads((D/f'recipes/{item}.json').read_text())
   self.assertEqual(recipe['result']['item'],'aiarsenal:'+item)
   for lang in ('en_us','ru_ru'):
    self.assertIn('item.aiarsenal.'+item,json.loads((A/f'lang/{lang}.json').read_text()))
class GlbWinding(unittest.TestCase):
 """Materials are single-sided and Minecraft back-face culls, so an inside-out plate renders black."""
 def test_prism_faces_outward_in_every_winding(self):
  import sys
  sys.path.insert(0,str(ROOT/'tools/wwi'))
  from glb_builder import Mesh
  ccw=[(0.0,-1.0),(0.0,1.0),(1.0,1.0),(1.0,-1.0)]   # positive signed area in (y,z)
  cw=list(reversed(ccw))
  for profile,tag in ((ccw,'ccw'),(cw,'cw')):
   for a,b in ((-0.5,0.5),(0.5,-0.5)):
    m=Mesh(); m.prism(0,profile,'x',a,b,1.0)
    verts=m.groups[0]['p']; norms=m.groups[0]['n']
    self.assertEqual(len(verts)%9,0)
    centre=((a+b)/2.0,sum(q[0] for q in profile)/len(profile),sum(q[1] for q in profile)/len(profile))
    for t in range(len(verts)//9):
     tri=[tuple(verts[t*9+k*3:t*9+k*3+3]) for k in range(3)]
     nrm=tuple(norms[t*9+k*3:t*9+k*3+3] for k in range(3))
     face=tuple(sum(v[k] for v in tri)/3.0 for k in range(3))
     out=[face[k]-centre[k] for k in range(3)]
     dot=sum(nrm[0][k]*out[k] for k in range(3))
     self.assertGreater(dot,1e-9,f"{tag} a={a} b={b} triangle {t} faces inward")
if __name__=='__main__': unittest.main()
