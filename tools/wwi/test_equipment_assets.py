"""Offline equipment resource contract checks; gameplay is covered by WWIEquipmentTests."""
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
if __name__=='__main__': unittest.main()
