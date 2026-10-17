"""Napoleonic arms, modelled only inside live Blender/MCP. Millimetres via the weapon library.
Representative Charleville 1777/An IX, Russian 1808, An XIII / Russian 1809 pistols,
French 6-pounder An XI and Russian 12-pounder 1805; game stylisations, not museum scans.
"""
import bpy,sys,importlib.util,math
from pathlib import Path
from mathutils import Vector,Quaternion
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('prior',ROOT/'tools/blender/weapons_stage1c.py')
prior=importlib.util.module_from_spec(spec);spec.loader.exec_module(prior)
w=prior.w;Z=(0,0,0);MODELS={}
w.mat('Paper1812','T_Paper1812',.95)

def gun(name,pistol=False,russian=False):
 sc=w.new_scene(name.upper());w.U0=120 if pistol else 420
 length=(430 if russian else 350) if pistol else (1450 if russian else 1520)
 g=name+'.body';cock=name+'.cock'
 if pistol:
  stock=[(0,-105),(25,-135),(64,-122),(120,-45),(length-35,-33),(length-25,-12),(114,-9),(75,-24)]
  w.prism(g,stock,-20,20,'Walnut',2)
  w.lathe(g,[(115,0),(115,17),(150,16),(length,11),(length,8),(length-12,8)],'Steel',20)
  w.box(g,length-70,length-50,-35,-8,-22,22,'Brass',1)
  w.box(g,8,48,-133,-119,-23,23,'Brass',2)
  grip=w.P(66,-68);support=w.P(165,-30)
 else:
  stock=[(0,-90),(12,-155),(170,-138),(330,-63),(465,-43),(length-120,-29),(length-105,-9),(400,-8),(210,-41),(40,-52)]
  w.prism(g,stock,-24,24,'Walnut',2)
  w.lathe(g,[(430,0),(430,19),(600,17),(length,11),(length,8.5),(length-18,8.5)],'Steel',20)
  for u in [650,1000,length-150]:
   w.ring(g,u,u+22,20,24,'Brass' if russian else 'Steel',16,0,-5)
  w.box(g,0,14,-148,-60,-26,26,'Brass',1)
  for u in [680,length-200]:w.ring_rod_loop(g,w.P(u,-37),17,3,'Steel','x',12)
  grip=w.P(360,-57);support=w.P(820,-45)
 # Flintlock: external lock plate, S-shaped cock with clamped flint, frizzen and pan on the right.
 lock=145 if pistol else 480
 w.prism(g,[(lock-75,-18),(lock+55,-18),(lock+65,8),(lock-65,12)],-28,-24,'Steel',.6)
 for u in [lock-48,lock+38]:w.cylx(g,u,-4,-30,-28,4,'Brass',10)
 for a,b in [((lock-45,0),(lock-65,20)),((lock-65,20),(lock-45,45)),((lock-45,45),(lock-20,48))]:
  w.rod(cock,w.P(a[0],a[1],-31),w.P(b[0],b[1],-31),5,4,'Steel',10)
 w.box(cock,lock-32,lock-10,42,48,-39,-23,'BlackPaint',.3)
 w.box(cock,lock-36,lock-6,48,52,-40,-22,'Steel',.4)
 w.box(g,lock+12,lock+24,0,39,-32,-27,'Steel',.5)
 w.box(g,lock-4,lock+30,-2,6,-42,-27,'Brass',.5)
 # Trigger and curved guard; no anachronistic detachable magazine or bolt action.
 u=100 if pistol else 410
 w.rod(g,w.P(u,-25),w.P(u-8,-49),3,3,'Steel',8)
 pts=[(u-29,-23),(u-33,-57),(u+35,-58),(u+45,-24)]
 for a,b in zip(pts,pts[1:]):w.rod(g,w.P(*a),w.P(*b),4,4,'Brass',10)
 w.box(g,length-30,length-21,10,20,-2,2,'Brass',.2)
 w.lathe(g,[(length+.1,0),(length+.1,8)],'BlackPaint',16)
 # Rammer in its channel + separate moving rammer, paper charge, hand and cock animations.
 for suffix in ['rod_stowed','ramrod']:
  w.rod(name+'.'+suffix,w.P(length*.45,-27),w.P(length-45,-27),3,4,'Steel',12)
 w.lathe(name+'.charge',[(length-20,0),(length-20,9),(length+25,9),(length+25,0)],'Paper1812',12)
 w.flash(name+'.flash',length+3,0,90 if pistol else 160,35)
 groups={'body':None,'cock':w.P(lock-45,0,-31),'ramrod':w.P(length-45,-27),'rod_stowed':None,'charge':w.P(length,0),'flash':w.P(length,0)}
 root=prior.assemble(name,groups,w.P(lock-90,21),w.P(length,0),grip,support)
 total=144 if pistol else 192
 for clip in ['_reload','_reload_empty']:
  anim=name+clip
  w.clip(anim,root,[(0,Z,Z),(18,(0,.03,-.06),(-52,0,-12)),(total-20,(0,.03,-.06),(-52,0,-12)),(total,Z,Z)])
  rod=bpy.data.objects[name+'.ramrod'];charge=bpy.data.objects[name+'.charge'];hand=bpy.data.objects[name+'.arm_l']
  w.clip(anim,rod,[(0,Z,Z),(total*.25,(.06,-.25,.08),Z),(total*.45,(0,-.36,.027),Z),(total*.58,(0,.05,.027),Z),(total*.68,(0,-.25,.027),Z),(total*.78,(0,.05,.027),Z),(total*.88,(.06,-.25,.08),Z),(total,Z,Z)])
  w.clip(anim,charge,[(0,(.08,0,.1),Z),(total*.28,(.06,-.1,.08),Z),(total*.40,Z,Z),(total,Z,Z)])
  w.clip(anim,hand,[(0,Z,Z),(total*.25,(.06,-.2,.05),Z),(total*.45,(.04,-.35,.03),Z),(total*.58,(.04,-.05,.03),Z),(total*.68,(.04,-.3,.03),Z),(total*.78,(.04,-.05,.03),Z),(total,Z,Z)])
  w.clip(anim,bpy.data.objects[cock],[(0,Z,Z),(total*.85,Z,(0,0,0)),(total*.94,Z,(30,0,0)),(total,Z,Z)])
 return sc

def cannon(name,russian):
 sc=w.new_scene(name.upper());w.U0=0
 body=name+'.body';barrel=name+'.barrel';wood='Walnut' if russian else 'RpgGreen';length=1700 if russian else 1450
 for x in [-220,220]:
  w.prism(body,[(-1900,160),(-1850,320),(-350,960),(360,960),(360,540),(-800,320)],x-65,x+65,wood,8)
 for x in [-220,220]:
  w.box(body,-135,135,910,1020,x-70,x+70,wood,4)
  w.box(body,-105,105,1000,1024,x-76,x+76,'Steel',2)
 w.box(body,-150,150,540,700,-950,950,'Walnut',6)
 w.box(body,-1500,-1340,240,350,-220,220,wood,5)
 for x in [-850,850]:
  w.cylx(body,0,700,x-140,x+140,100,'Walnut',16)
  w.cylx(body,0,700,x-150,x+150,40,'Steel',16)
  for i in range(32):
   a=i*math.tau/32;b=(i+1)*math.tau/32
   for r,thick,mat in [(665,42,'Walnut'),(708,17,'Steel')]:
    w.rod(body,w.P(math.cos(a)*r,700+math.sin(a)*r,x),w.P(math.cos(b)*r,700+math.sin(b)*r,x),thick,thick,mat,8)
  for i in range(12):
   a=i*math.tau/12
   w.rod(body,w.P(math.cos(a)*95,700+math.sin(a)*95,x),w.P(math.cos(a)*630,700+math.sin(a)*630,x),31,20,'Walnut',8)
 bore=1050;fat=150 if russian else 125
 w.lathe(barrel,[(-500,0),(-500,65),(-430,75),(-390,fat),(-310,fat+12),(-265,fat),(-100,fat),(100,fat-10),(length-150,78),(length-100,99),(length,99),(length,55 if russian else 45),(length-110,55 if russian else 45)],'Brass',24,0,bore)
 w.cylx(barrel,0,bore,-350,350,65,'Brass',16)
 for u in [-290,-80,length-110]:w.ring(barrel,u,u+22,fat if u<0 else 96,(fat if u<0 else 96)+8,'Brass',24,0,bore)
 w.lathe(barrel,[(length-105,0),(length-105,54 if russian else 44)],'BlackPaint',16,0,bore)
 for x in [-100,100]:w.rod(barrel,w.P(-160,bore+110,x),w.P(20,bore+160,x),18,18,'Brass',12)
 w.empty(name,Z);root=bpy.data.objects[name]
 w.join_node(body,root);w.join_node(barrel,root,w.P(0,bore))
 w.empty(name+'.muzzle',w.P(length,bore),bpy.data.objects[barrel])
 return sc

def sabre(name,russian):
 sc=w.new_scene(name.upper());w.U0=120;g=name+'.body'
 length=1000 if russian else 780
  # Distributed curvature, rather than a straight blade with a hooked last segment.
 lower=[];upper=[]
 for i in range(13):
  t=i/13;u=190+(length-190)*t;curve=(90 if russian else 65)*t*t;width=30*(1-.45*t)
  lower.append((u,curve-width/2));upper.append((u,curve+width/2))
 w.prism(g,lower+[(length,90 if russian else 65)]+list(reversed(upper)),-3,3,'ClipSteel',.3)
 w.lathe(g,[(20,0),(20,17),(40,14),(165,14),(180,21),(190,21),(190,0)],'Walnut' if russian else 'Brass',14)
 for u in range(45,165,12):w.ring(g,u,u+3,14,16,'Brass',12)
 points=[(20,0),(18,-58),(70,-79),(172,-65),(195,-22),(195,30)]
 for a,b in zip(points,points[1:]):w.rod(g,w.P(*a),w.P(*b),6,6,'Brass',10)
 w.join_node(g);return sc

def ammo(name):
 sc=w.new_scene(name.upper());w.U0=0;g=name+'.body'
 if name=='cannonball':w.sphere(g,w.P(0,0),65,'Steel',seg=16)
 else:
  cannon=name.startswith('charge');r=(45 if name=='charge_ri12' else 38) if cannon else (7 if name=='pistol_cartridge' else 8);l=(170 if name=='charge_ri12' else 140) if cannon else (43 if name=='pistol_cartridge' else 58)
  w.lathe(g,[(0,0),(0,r*.7),(8,r),(l-8,r),(l,r*.5),(l,0)],'Paper1812',16)
  for u in [12,l-12]:w.ring(g,u,u+3,r,r+1,'Walnut',16)
 w.join_node(g);return sc

def icons(name):
 sc=MODELS[name];bpy.context.window.scene=sc
 mesh=[ob for ob in sc.objects if ob.type=='MESH' and not ob.name.endswith(('.flash','.ramrod','.charge'))]
 for ob in sc.objects:ob.hide_render=ob.type=='MESH' and ob not in mesh
 pts=[ob.matrix_world@Vector(p) for ob in mesh for p in ob.bound_box]
 lo=Vector(tuple(min(p[i] for p in pts) for i in range(3)));hi=Vector(tuple(max(p[i] for p in pts) for i in range(3)));center=(lo+hi)/2
 bpy.ops.object.camera_add(location=center+Vector((3,1,2)));cam=bpy.context.object;cam.rotation_euler=(center-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.type='ORTHO';cam.data.ortho_scale=max(hi-lo)*1.35;sc.camera=cam
 sc.render.engine='BLENDER_WORKBENCH';sc.display.shading.light='STUDIO';sc.display.shading.color_type='TEXTURE';sc.display.shading.show_cavity=True
 sc.render.film_transparent=True;sc.render.image_settings.file_format='PNG';sc.render.image_settings.color_mode='RGBA';sc.render.resolution_x=128;sc.render.resolution_y=128;sc.render.resolution_percentage=100
 sc.render.filepath=str(ROOT/'mods/aiarsenal/src/main/resources/assets/aiarsenal/textures/item'/f'{name}.png');bpy.ops.render.render(write_still=True);bpy.data.objects.remove(cam,do_unlink=True)
 for ob in sc.objects:ob.hide_render=False

def build():
 for material in bpy.data.materials:
  if material.node_tree:
   for node in material.node_tree.nodes:
    if node.type=='TEX_IMAGE':material.node_tree.nodes.active=node;break
 for name,pistol,russian in [('musket_1777',False,False),('musket_1808',False,True),('pistol_an13',True,False),('pistol_1809',True,True)]:MODELS[name]=gun(name,pistol,russian)
 for name,ru in [('cannon_fr6',False),('cannon_ri12',True)]:MODELS[name]=cannon(name,ru)
 for name,ru in [('sabre_briquet',False),('sabre_1809',True)]:MODELS[name]=sabre(name,ru)
 for name in ['paper_cartridge','pistol_cartridge','charge_fr6','charge_ri12','cannonball']:MODELS[name]=ammo(name)
 for name,sc in MODELS.items():w.export(sc,name,name.startswith(('musket','pistol_')));icons(name)
 for sc in list(bpy.data.scenes):
  if sc.name not in w.SCENES:bpy.data.scenes.remove(sc)
 for image in bpy.data.images:
  if not image.packed_file:image.pack()
 bpy.context.window.scene=MODELS['musket_1777'];(ROOT/'models').mkdir(exist_ok=True)
 bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'models/napoleonic_arms.blend'),compress=True)
 print('NAPOLEONIC MODELS PASS',list(MODELS))

def viewport(name):
 bpy.context.window.scene=MODELS[name]
 for ob in bpy.context.scene.objects:
  hidden=ob.name.endswith(('.flash','.ramrod','.charge'));ob.hide_set(hidden);ob.select_set(ob.type=='MESH' and not hidden)
 for area in bpy.context.screen.areas:
  if area.type=='VIEW_3D':
   area.spaces.active.shading.type='SOLID';area.spaces.active.shading.color_type='TEXTURE';area.spaces.active.overlay.show_overlays=False
   region=next(r for r in area.regions if r.type=='WINDOW')
   with bpy.context.temp_override(area=area,region=region):bpy.ops.view3d.view_selected(use_all_regions=False)
   area.spaces.active.region_3d.view_rotation=Quaternion((.8,.35,.25,.4)).normalized();area.spaces.active.region_3d.view_distance*=1.15
