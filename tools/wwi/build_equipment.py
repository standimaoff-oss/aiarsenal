#!/usr/bin/env python3
"""Reproducible Minecraft item geometry for the WWI equipment set (no external assets)."""
from pathlib import Path
import json, random, sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from glb_builder import png_rgba
ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'mods/aiarsenal/src/main/resources/assets/aiarsenal'
DATA=ROOT/'mods/aiarsenal/src/main/resources/data/aiarsenal'
def save(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
def box(lo,hi,tex):
 return {'from':lo,'to':hi,'faces':{f:{'texture':'#'+tex,'uv':[0,0,16,16]} for f in ('up','down','north','south','east','west')}}
for name,base in [('olive',(95,105,65)),('steel',(95,104,111)),('wood',(132,90,50)),('brass',(178,141,65))]:
 rng=random.Random(name);pix=[]
 for y in range(32):
  for x in range(32):
   n=rng.randint(-6,6)+(int(4*__import__('math').sin(x*.6+y*.08)) if name=='wood' else 0)
   pix.append(tuple(max(0,min(255,c+n)) for c in base)+(255,))
 p=ASSETS/f'textures/item/wwi/{name}.png';p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(png_rgba(32,32,pix))
models={}
# Cast segmented oval body, safety lever and the top pin boss.
e=[box([5.5,3,5.5],[10.5,4,10.5],'olive'),box([5,4,5],[11,11,11],'olive'),box([5.5,11,5.5],[10.5,12,10.5],'olive')]
for y in (4,6,8,10):
 for x in (5,7.1,9.2): e.append(box([x,y,4.7],[x+1.8,y+1.7,11.3],'olive'))
e += [box([6.5,12,6.5],[9.5,13,9.5],'steel'),box([9.7,4,6.8],[10.3,13.5,7.5],'steel'),box([8.5,12.8,7],[10.3,13.4,8],'steel')]
models['mills_bomb']=e
models['stielhandgranate']=[box([7,0,7],[9,10,9],'wood'),box([6.8,0,6.8],[9.2,1.2,9.2],'steel'),box([5.5,9.5,5.5],[10.5,15,10.5],'olive'),box([5.3,14.6,5.3],[10.7,15.2,10.7],'steel'),box([5.3,9,5.3],[10.7,9.8,10.7],'steel')]
# Handheld game representation of the Wex projector, with integrated fuel pack.
models['wex']=[box([4,2,9],[12,12,14],'olive'),box([5,3,8.5],[11,11,14.5],'olive'),box([6,6,-10],[10,9,9],'steel'),box([5.5,5.5,-11],[10.5,9.5,-9.5],'steel'),box([7.3,4,3],[8.7,7,8],'wood'),box([7.2,4,-10],[8.8,5,1],'brass'),box([3.5,3,8],[4.5,11,12],'steel'),box([6,12,10],[10,13,13],'brass')]
models['wwi_fuel']=[box([4,2,5],[12,12,11],'olive'),box([5,12,6],[11,13,10],'olive'),box([5,13,7],[7,14,9],'steel'),box([8,13,7],[11,14,9],'steel')]
for name,elements in models.items():
 display={
  'gui':{'rotation':[25,140,0],'translation':[0,0,0],'scale':[.8,.8,.8]},
  'firstperson_righthand':{'rotation':[0,180,0],'translation':[1,0,0],'scale':[.7,.7,.7]},
  'thirdperson_righthand':{'rotation':[0,180,0],'translation':[0,1,0],'scale':[.6,.6,.6]},
  'ground':{'translation':[0,2,0],'scale':[.4,.4,.4]}}
 save(ASSETS/f'models/item/{name}.json',{'gui_light':'side','textures':{k:f'aiarsenal:item/wwi/{k}' for k in ['olive','steel','wood','brass']}|{'particle':'aiarsenal:item/wwi/olive'},'elements':elements,'display':display})
 ingredients={'mills_bomb':['minecraft:iron_ingot','minecraft:gunpowder'],'stielhandgranate':['minecraft:iron_ingot','minecraft:gunpowder','minecraft:stick'],'wex':['minecraft:iron_ingot','minecraft:iron_ingot','minecraft:copper_ingot','minecraft:leather'],'wwi_fuel':['minecraft:coal','minecraft:blaze_powder']}[name]
 save(DATA/f'recipes/{name}.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':i} for i in ingredients],'result':{'item':f'aiarsenal:{name}'}})
for language,labels in [('en_us',{'mills_bomb':'Mills bomb','stielhandgranate':'Stick grenade (1915)','wex':'Wex flame projector','wwi_fuel':'Flamethrower fuel'}),('ru_ru',{'mills_bomb':'Граната Миллса','stielhandgranate':'Ручная граната обр. 1915 г.','wex':'Огнемёт Wex','wwi_fuel':'Топливо для огнемёта'})]:
 p=ASSETS/f'lang/{language}.json';d=json.loads(p.read_text());d.update({f'item.aiarsenal.{k}':v for k,v in labels.items()});save(p,d)
print('WWI equipment: 4 item models, 4 textures, recipes and translations generated.')
