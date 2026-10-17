"""Portable resource generator; meshes/icons are made separately in live Blender."""
import json,random
from PIL import Image,ImageDraw
from historical_uniforms import ROOT,SETS
A=ROOT/'mods/aiarsenal/src/main/resources/assets/aiarsenal';D=ROOT/'mods/aiarsenal/src/main/resources/data/aiarsenal'
def put(path,obj):path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
SHAPES={'head':['YWY','WXW'],'chest':['YXW','WWW','WWW'],'legs':['WYW','WXW','W W'],'feet':['YXY','W W']}
cloth={'ww2_su':'brown_wool','ww2_de':'gray_wool','nap_fr':'blue_wool','nap_ri':'green_wool'}
emblem={'infantry':'iron_nugget','officer':'gold_nugget','tanker':'black_dye','pilot':'feather','general':'gold_ingot'}
for lang in ['ru_ru','en_us']:
 labels=json.loads((A/'lang'/f'{lang}.json').read_text())
 for name,s in SETS.items():
  for slot in SHAPES:
   part=({'head':'Головной убор','chest':'Мундир','legs':'Брюки','feet':'Сапоги'} if lang=='ru_ru' else {'head':'Headgear','chest':'Tunic','legs':'Trousers','feet':'Boots'})[slot]
   labels['item.aiarsenal.uniform_'+name+'_'+slot]=part+' — '+s['ru' if lang=='ru_ru' else 'en']
  labels['tooltip.aiarsenal.uniform.'+name]=s['ru' if lang=='ru_ru' else 'en']+(' · историческая стилизация, не бронежилет' if lang=='ru_ru' else ' · historical stylization, not ballistic armour')
 labels['itemGroup.aiarsenal.napoleonic_clothing']='1812 · Форма' if lang=='ru_ru' else '1812 · Uniforms'
 put(A/'lang'/f'{lang}.json',labels)
for name,s in SETS.items():
 rng=random.Random(name);im=Image.new('RGB',(128,128));pix=im.load()
 for y in range(128):
  for x in range(128):
   n=rng.randrange(-6,7)+(2 if (x+y)%4==0 else -1)
   pix[x,y]=tuple(max(0,min(255,c+n)) for c in s['color'])
 # Visible fabric twill, stitched seam and slight worn edge, not an untextured flat colour.
 draw=ImageDraw.Draw(im);draw.line((5,0,5,127),fill=tuple(min(255,c+15) for c in s['color']),width=1)
 for y in range(0,128,6):draw.line((3,y,7,y),fill=tuple(max(0,c-14) for c in s['color']))
 path=A/'textures/models/historical'/f'{name}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
 for slot,shape in SHAPES.items():
  item='uniform_'+name+'_'+slot
  put(A/'models/item'/f'{item}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'aiarsenal:item/'+item}})
  put(D/'recipes'/f'{item}.json',{'type':'minecraft:crafting_shaped','pattern':shape,'key':{'W':{'item':'minecraft:'+cloth[s['army']]},'X':{'item':'minecraft:'+emblem[s['role']]},'Y':{'item':'minecraft:'+{'ww2_su':'red_dye','ww2_de':'white_dye','nap_fr':'blue_dye','nap_ri':'green_dye'}[s['army']]}},'result':{'item':'aiarsenal:'+item}})
# Every slot/army/rank has a unique shaped recipe (cloth + emblem + shape).
java=ROOT/'mods/aiarsenal/src/main/java/ru/aiforge/realwar/item/HistoricalUniforms.java'
java.write_text('''package ru.aiforge.realwar.item;
/** 1943–45 and 1812 representative uniforms; common-side catalogue. Generated from tools/historical_uniforms.py. */
public final class HistoricalUniforms {
    private HistoricalUniforms() {}
    public static final String[] SETS = {\n'''+',\n'.join('        "'+x+'"' for x in SETS)+'''
    };
    public static boolean contains(String set) { return java.util.Arrays.asList(SETS).contains(set); }
}
''')
print('Generated',len(SETS),'sets /',len(SETS)*4,'pieces')
