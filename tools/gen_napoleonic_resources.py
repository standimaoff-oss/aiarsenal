"""Resource data and original procedural game SFX. Geometry is built separately in live Blender/MCP."""
import json
from pathlib import Path
import numpy as np
import soundfile as sf
from PIL import Image
from make_pistol_sounds import shot,SR
R=Path(__file__).resolve().parents[1];A=R/'mods/aiarsenal/src/main/resources/assets/aiarsenal';D=R/'mods/aiarsenal/src/main/resources/data/aiarsenal/recipes'
NAMES={
'musket_1777':('Франция — мушкет 1777 / An IX','France — 1777 / An IX musket'),
'musket_1808':('РИ — пехотное ружьё обр. 1808','Russia — infantry musket M1808'),
'pistol_an13':('Франция — пистолет An XIII','France — An XIII pistol'),
'pistol_1809':('РИ — кавалерийский пистолет обр. 1809','Russia — cavalry pistol M1809'),
'cannon_fr6':('Франция — 6-фунтовая пушка An XI','France — An XI 6-pounder'),
'cannon_ri12':('РИ — 12-фунтовая пушка обр. 1805','Russia — M1805 12-pounder'),
'sabre_briquet':('Франция — тесак-брикет','France — briquet short sabre'),
'sabre_1809':('РИ — кавалерийская сабля','Russia — cavalry sabre'),
'paper_cartridge':('Бумажный мушкетный патрон','Paper musket cartridge'),
'pistol_cartridge':('Бумажный пистолетный патрон','Paper pistol cartridge'),
'charge_fr6':('6-фунтовый заряд с ядром','6-pounder charge and roundshot'),
'charge_ri12':('12-фунтовый заряд с ядром','12-pounder charge and roundshot'),
'cannonball':('Чугунное ядро','Iron roundshot')}

def write(p,obj):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
def main():
 rng=np.random.default_rng(1812);noise=rng.integers(-9,10,(128,128,1));color=np.clip(np.array([181,166,127])+noise,0,255).astype('uint8');Image.fromarray(color).save(R/'tools/blender/tex_r22/T_Paper1812.png')
 for i,lang in enumerate(['ru_ru','en_us']):
  p=A/'lang'/f'{lang}.json';d=json.loads(p.read_text())
  for name,label in NAMES.items():
   d['item.aiarsenal.'+name]=label[i]
   d['entity.aiarsenal.'+name]=label[i]
   d['hud.aiarsenal.gun.name.'+name]=label[i].split(' — ')[-1]
   if name.startswith(('musket','pistol_')):d['tooltip.aiarsenal.gun.'+name]=('Кремнёвый замок · 1 выстрел · дульное заряжание; игровой темп перезарядки' if i==0 else 'Flintlock · single shot · muzzle loading; gameplay reload timing')
  for key,values in {
   'itemGroup.aiarsenal.napoleonic_weapons':('1812 · Оружие','1812 · Weapons'),
   'itemGroup.aiarsenal.napoleonic_vehicles':('1812 · Артиллерия','1812 · Artillery'),
   'itemGroup.aiarsenal.napoleonic_ammo':('1812 · Боеприпасы','1812 · Ammunition'),
   'tooltip.aiarsenal.gun.keys_flintlock':('ЛКМ — выстрел · ПКМ — прицел · R — бумажный патрон и шомпол','LMB fire · RMB aim · R paper cartridge and ramrod'),
   'tooltip.aiarsenal.powder_cannon':('Заряд: ПКМ патроном. Пустая рука: направление взгляда; Shift+ПКМ: угол. Огниво: выстрел.','Use charge to load. Empty hand: align to look; sneak-use: elevation. Flint and steel: fire.'),
   'msg.aiarsenal.cannon.status':('Угол %s° · %s','Elevation %s° · %s'),
   'msg.aiarsenal.cannon.empty':('Пусто','Empty'),'msg.aiarsenal.cannon.ready':('Готово к выстрелу','Ready'),
   'msg.aiarsenal.cannon.loading':('Заряжание: 8 секунд','Loading: 8 seconds')}.items():d[key]=values[i]
  for name in ['musket_fire','flintlock_fire','powder_cannon']:d['subtitles.aiarsenal.'+name]=('Выстрел порохового оружия' if i==0 else 'Black powder shot')
  write(p,d)
 for name in NAMES:
  custom=not name.startswith(('paper_','pistol_cartridge','charge_','cannonball'))
  write(A/'models/item'/f'{name}.json',{'parent':'builtin/entity' if custom else 'minecraft:item/generated','textures':{'layer0':'aiarsenal:item/'+name,'particle':'aiarsenal:item/'+name}})
 recipes={
 'musket_1777':['iron_ingot','iron_ingot','oak_planks','flint','blue_dye'],
 'musket_1808':['iron_ingot','iron_ingot','oak_planks','flint','green_dye'],
 'pistol_an13':['iron_ingot','oak_planks','flint','blue_dye'],
 'pistol_1809':['iron_ingot','oak_planks','flint','green_dye'],
 'sabre_briquet':['iron_ingot','iron_ingot','gold_nugget','blue_dye'],
 'sabre_1809':['iron_ingot','iron_ingot','gold_nugget','green_dye'],
 'cannon_fr6':['iron_block','copper_block','oak_planks','oak_planks','stick','blue_dye'],
 'cannon_ri12':['iron_block','iron_block','copper_block','oak_planks','stick','green_dye'],
 'paper_cartridge':['paper','gunpowder','iron_nugget'],
 'pistol_cartridge':['paper','gunpowder','iron_nugget','iron_nugget'],
 'cannonball':['iron_ingot','iron_ingot','iron_ingot'],
 'charge_fr6':['aiarsenal:cannonball','gunpowder','paper'],
 'charge_ri12':['aiarsenal:cannonball','gunpowder','gunpowder','paper']}
 for name,ingredients in recipes.items():write(D/f'{name}.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':n if ':' in n else 'minecraft:'+n} for n in ingredients],'result':{'item':'aiarsenal:'+name,'count':4 if name in ['paper_cartridge','pistol_cartridge'] else 1}})
 p=A/'handheld_view.json';d=json.loads(p.read_text())
 for name in list(NAMES)[:8]:
  base='c96' if name.startswith('pistol') else 'mosin'
  for key,value in list(d.items()):
   if key.startswith(base+'.'):d[name+key[len(base):]]=list(value)
  if name.startswith('sabre_'):
   d[name+'.firstperson_righthand']=[-.25,.22,-.12,200,-55,0,.9,0]
   d[name+'.firstperson_lefthand']=[.25,.22,-.12,160,-55,0,.9,0]
  if name.startswith('cannon_'):
   for context in ['thirdperson_righthand','thirdperson_lefthand','firstperson_righthand','firstperson_lefthand']:d[name+'.'+context]=[0,0,.05,180,0,0,.55,1]
  if name.startswith(('musket','pistol')):
   d[name+'.fp']=[.11,-.16,-.48,-3,.28 if name.startswith('pistol') else .06,0]
   for hand in ['right','left']:d[name+'.thirdperson_'+hand+'hand']=[0,-.045,.09,180,0,0,1.35 if name.startswith('pistol') else 1,0]
 write(p,d)
 p=A/'sounds.json';d=json.loads(p.read_text())
 for name,args in [('musket_fire',(1777,.8,480,.07,.04)),('flintlock_fire',(1809,.65,680,.05,.035)),('powder_cannon',(1805,1.5,140,.22,.05))]:
  sf.write(A/'sounds'/f'{name}.ogg',shot(*args)*.42,SR,format='OGG',subtype='VORBIS')
  d[name]={'subtitle':'subtitles.aiarsenal.'+name,'sounds':[{'name':'aiarsenal:'+name}]}
 for name in ['paper_load','ramrod']:
  t=np.arange(int(SR*.4))/SR
  noise=np.random.default_rng(1812).standard_normal(len(t))
  envelope=np.exp(-t/.09)*(1-np.exp(-t/.008))
  x=(noise if name=='paper_load' else np.sin(2*np.pi*850*t)+noise*.25)*envelope
  sf.write(A/'sounds'/f'{name}.ogg',x/max(abs(x))*.14,SR,format='OGG',subtype='VORBIS')
  d[name]={'sounds':[{'name':'aiarsenal:'+name}]}
 write(p,d)
if __name__=='__main__':main()
