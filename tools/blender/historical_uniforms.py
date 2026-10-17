"""Stage2: real wearable meshes in a LIVE Blender/MCP session. Metres, Z-up, front -Y.
Absolute vertices and named skin-bone pieces match UniformLayer. No external addon in saved blend.
"""
import bpy, math, sys
from pathlib import Path
from mathutils import Vector, Quaternion
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools'))
from historical_uniforms import SETS
A=ROOT/'mods/aiarsenal/src/main/resources/assets/aiarsenal'
MATS={};CURRENT='';SERIAL=0

def material(key,color=None):
 if key in MATS:return MATS[key]
 m=bpy.data.materials.new(key);m.use_nodes=True
 color=color or SETS[key]['color'];c=tuple(x/255 for x in color)+(1,)
 m.diffuse_color=c;bs=m.node_tree.nodes.get('Principled BSDF');bs.inputs['Base Color'].default_value=c;bs.inputs['Roughness'].default_value=.8
 if key in SETS:
  im=bpy.data.images.load(str(A/'textures/models/historical'/f'{key}.png'));im.pack()
  tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=im;m.node_tree.links.new(tex.outputs['Color'],bs.inputs['Base Color'])
 MATS[key]=m;return m

def finish(ob,piece,mat):
 global SERIAL
 SERIAL+=1;ob.name=f'{CURRENT}.{piece}.{SERIAL:04d}';ob.data.materials.append(mat)
 bpy.ops.object.transform_apply(location=True,rotation=True,scale=True)
 col=bpy.data.collections.get(CURRENT)
 if col is None:col=bpy.data.collections.new(CURRENT);bpy.context.scene.collection.children.link(col)
 for c in list(ob.users_collection):c.objects.unlink(ob)
 col.objects.link(ob)
 return ob

def box(piece,loc,size,mat,rot=0,bevel=0):
 bpy.ops.mesh.primitive_cube_add(size=1,location=loc);o=bpy.context.object;o.dimensions=size;o.rotation_euler[1]=math.radians(rot)
 bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
 if bevel:
  mod=o.modifiers.new('sewn edge','BEVEL');mod.width=bevel;mod.segments=1
  bpy.ops.object.modifier_apply(modifier=mod.name)
 return finish(o,piece,mat)

def cyl(piece,loc,radius,depth,mat,vertices=12,front=False,r2=None):
 bpy.ops.mesh.primitive_cone_add(vertices=vertices,radius1=radius,radius2=radius if r2 is None else r2,depth=depth,location=loc)
 o=bpy.context.object
 if front:o.rotation_euler[0]=math.pi/2
 return finish(o,piece,mat)

def tube(piece,a,b,r,mat):
 a,b=Vector(a),Vector(b);v=b-a
 bpy.ops.mesh.primitive_cylinder_add(vertices=8,radius=r,depth=v.length,location=(a+b)/2)
 o=bpy.context.object;o.rotation_euler=v.to_track_quat('Z','Y').to_euler();return finish(o,piece,mat)

def helmet(mat,de):
 # Open-bottom dome; does not cover the wearer's face with a solid box.
 profile=[(.35 if de else .33,1.88),(.36,1.94),(.32,2.08),(.20,2.16),(.02,2.20)]
 vs=[(r*math.cos(i*math.tau/16),r*math.sin(i*math.tau/16),z) for r,z in profile for i in range(16)]
 faces=[]
 for j in range(len(profile)-1):
  for i in range(16):faces.append((j*16+i,j*16+(i+1)%16,(j+1)*16+(i+1)%16,(j+1)*16+i))
 mesh=bpy.data.meshes.new('open helmet shell');mesh.from_pydata(vs,[],faces);mesh.update()
 o=bpy.data.objects.new('helmet',mesh);bpy.context.collection.objects.link(o);bpy.context.view_layer.objects.active=o;o.select_set(True)
 # UVs on the dome, keeping the material usable for resource packs.
 uv=mesh.uv_layers.new()
 for poly in mesh.polygons:
  for li in poly.loop_indices:
   co=mesh.vertices[mesh.loops[li].vertex_index].co;uv.data[li].uv=(co.x+.5,co.y+.5)
 return finish(o,'head',mat)

def build_set(name):
 global CURRENT
 CURRENT=name;s=SETS[name];army=s['army'];role=s['role'];nap=army.startswith('nap');su=army.endswith('su');fr=army.endswith('fr');off=role in ('officer','general')
 cloth=material(name);black=material('black',(28,29,29));leather=material('leather',(66,43,28));white=material('linen',(219,215,192));red=material('red',(155,33,30));gold=material('brass',(191,149,63));silver=material('silver',(155,160,154))
 trim=gold if off else red if nap or su else silver
 pants=white if nap and role!='general' else material('blue_trousers',(47,58,83)) if su and off else cloth
 box('body',(0,0,1.13),(.535,.285,.75),cloth,bevel=.007)
 for sign,side in [(-1,'r'),(1,'l')]:
  x=sign*.375
  box('arm_'+side,(x,0,1.185),(.265,.275,.61),cloth,bevel=.006)
  box('arm_'+side,(x,-.004,.90),(.27,.28,.06),red if nap else cloth)
  box('leg_'+side,(sign*.125,0,.415),(.253,.275,.67),pants,bevel=.005)
  bootheight=.36 if not off else .44
  box('boot_'+side,(sign*.125,-.015,bootheight/2),(.26,.30,bootheight),black,bevel=.012)
  box('boot_'+side,(sign*.125,-.07,.045),(.27,.39,.09),black,bevel=.016)
  box('boot_'+side,(sign*.125,-.065,.013),(.275,.40,.025),material('sole',(14,15,15)))
  # Shoulder pieces belong to the arm, not the stationary torso.
  box('arm_'+side,(x,-.005,1.498),(.215,.19,.026),trim,bevel=.004)
  if off and nap:
   for i in range(5):box('arm_'+side,(sign*.494,-.075+i*.036,1.468),(.018,.015,.065),gold)
  if nap:
   box('leg_'+side,(sign*.125,.163,.64),(.24,.045,.37),cloth,bevel=.006)
   box('leg_'+side,(sign*.125,.188,.48),(.20,.012,.045),red)
  if role=='general':box('leg_'+side,(sign*.252,0,.48),(.012,.28,.51),red)
 # Collar, belt, buckle, real flap/pocket silhouettes.
 for x in [-.10,.10]:box('body',(x,-.105,1.484),(.17,.11,.07),red if nap or role=='general' else cloth,rot=(-12 if x<0 else 12))
 box('body',(0,0,.855),(.55,.305,.055),white if nap else leather)
 box('body',(0,-.166,.855),(.08,.025,.06),gold if nap or su else silver,bevel=.006)
 if nap:
  if fr and role=='infantry':box('body',(0,-.15,1.15),(.29,.018,.48),white)
  for x in [-.09,.09]:
   for z in [1.04,1.15,1.26,1.37]:cyl('body',(x,-.168,z),.012,.008,gold,front=True)
  if role=='infantry':
   for ang in [-25,25]:box('body',(0,-.184,1.16),(.055,.018,.67),white,rot=ang)
   box('body',(.20,.195,.91),(.17,.12,.16),black,bevel=.01)
  else:
   box('body',(0,-.164,.91),(.52,.026,.07),gold)
   tube('body',(-.22,-.16,1.42),(.22,-.17,.95),.013,gold)
 else:
  for x in [-.14,.14]:
   for z in ([1.27] if su and role in ('infantry','tanker') else [1.27,.98]):
    box('body',(x,-.157,z),(.15,.032,.15),cloth,bevel=.006)
    box('body',(x,-.177,z+.052),(.157,.019,.042),cloth,bevel=.004)
    cyl('body',(x,-.191,z+.045),.009,.006,gold if su else silver,front=True)
  for z in ([1.22,1.31,1.40] if su and role=='infantry' else [.97,1.08,1.19,1.30,1.40]):cyl('body',(0,-.158,z),.009,.007,gold if su else silver,front=True)
  if off:box('body',(0,-.18,1.17),(.035,.014,.64),leather,rot=29)
  if role=='tanker':box('body',(.04,-.18,1.22),(.035,.02,.54),cloth,rot=24)
 # Headwear distinguishes branches and epochs in silhouette, not just recolouring.
 if nap and role=='infantry':
  cyl('head',(0,0,2.095),.32,.37,black,16,r2=.35)
  box('head',(0,-.27,1.928),(.44,.26,.025),black,bevel=.012)
  cyl('head',(0,-.345,2.06),.065,.012,gold,front=True)
  cyl('head',(0,-.35,2.20),.048,.015,red if fr else gold,front=True)
  cyl('head',(0,-.36,2.20),.033,.015,white if fr else black,front=True)
  cyl('head',(0,-.37,2.20),.018,.015,material('cockade_blue',(38,66,132)) if fr else gold,front=True)
  cyl('head',(0,-.07,2.38),.055,.22,red if fr else black,r2=.032)
  for a,b in [((-.27,-.22,2.14),(0,-.35,2.0)),((0,-.35,2.0),(.27,-.22,2.14))]:tube('head',a,b,.012,white if fr else gold)
 elif nap:
  # Transverse bicorne, open beneath the brim.
  poly=[(-.43,1.97),(-.32,2.19),(-.13,2.25),(0,2.15),(.13,2.25),(.32,2.19),(.43,1.97)]
  vs=[(x,y,z) for y in [-.13,.13] for x,z in poly];n=len(poly)
  faces=[tuple(range(n-1,-1,-1)),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)]
  me=bpy.data.meshes.new('bicorne');me.from_pydata(vs,[],faces);me.update();o=bpy.data.objects.new('bicorne',me);bpy.context.collection.objects.link(o);finish(o,'head',black)
  for (x,z),(xx,zz) in zip(poly,poly[1:]):tube('head',(x,-.145,z),(xx,-.145,zz),.012,gold)
  cyl('head',(.13,-.16,2.14),.048,.01,red if fr else gold,front=True);cyl('head',(.13,-.17,2.14),.033,.01,white if fr else black,front=True)
  if fr:cyl('head',(.13,-.18,2.14),.018,.01,material('cockade_blue',(38,66,132)),front=True)
 elif role=='infantry':helmet(material('helmet_de' if army.endswith('de') else 'helmet_su',(65,73,65) if not su else (79,88,57)),not su)
 elif role=='tanker' and not su:
  # German Panzer crew wore a black field cap in 1943–45, not a Soviet padded tanker helmet.
  box('head',(0,0,1.98),(.53,.53,.13),black,bevel=.025)
  box('head',(0,.015,2.055),(.46,.48,.065),black,bevel=.02)
  box('head',(0,-.285,1.935),(.35,.16,.024),black,bevel=.012)
  cyl('head',(0,-.276,1.995),.022,.009,silver,front=True)
 elif role in ('tanker','pilot'):

  helmet(leather if role=='pilot' else black,False)
  for x in [-.27,.27]:box('head',(x,.025,1.78),(.07,.23,.26),leather if role=='pilot' else black,bevel=.024)
  for x in [-.18,0,.18]:box('head',(x,.02,2.14),(.045,.38,.035),leather if role=='pilot' else black,bevel=.01)
  if role=='pilot':
   for x in [-.10,.10]:
    cyl('head',(x,-.31,1.96),.073,.035,black,front=True)
    cyl('head',(x,-.333,1.96),.055,.012,material('goggle_glass',(81,124,145)),front=True)
 else:
  cyl('head',(0,0,1.97),.29,.075,red if role=='general' else cloth,16)
  cyl('head',(0,0,2.045),.34,.085,cloth,16,r2=.33)
  box('head',(0,-.28,1.934),(.43,.27,.024),black,bevel=.012)
  cyl('head',(0,-.296,1.98),.035,.012,gold,front=True)
  tube('head',(-.2,-.235,1.95),(.2,-.235,1.95),.009,gold if off else leather)
 if role=='general':
  for x in [-.15,-.09,-.03]:box('body',(x,-.18,1.32),(.036,.014,.017),red if x<-.1 else gold)

def build():
 bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
 for name in SETS:build_set(name)
 for ob in bpy.context.scene.objects:ob.select_set(True)
 bpy.ops.export_scene.gltf(filepath=str(A/'models/glb/historical_uniforms.glb'),export_format='GLB',use_selection=True,export_apply=True,export_animations=False)
 bpy.context.scene.render.engine='BLENDER_WORKBENCH'
 bpy.context.scene.display.shading.light='STUDIO';bpy.context.scene.display.shading.color_type='MATERIAL'
 bpy.context.scene.display.shading.show_shadows=True;bpy.context.scene.display.shading.show_cavity=True
 bpy.context.scene.render.image_settings.file_format='PNG';bpy.context.scene.render.image_settings.color_mode='RGBA'
 bpy.context.scene.render.film_transparent=True
 bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'models/historical_uniforms.blend'),compress=True)

def icons(name):
 sc=bpy.context.scene
 for slot,pieces in {'head':['head'],'chest':['body','arm_l','arm_r'],'legs':['leg_l','leg_r'],'feet':['boot_l','boot_r']}.items():
  shown=[]
  for ob in sc.objects:
   if ob.type!='MESH':continue
   visible=ob.name.startswith(name+'.') and ob.name.split('.')[1] in pieces
   ob.hide_render=not visible
   if visible:shown.append(ob)
  points=[Vector(v) for ob in shown for v in ob.bound_box];lo=Vector(tuple(min(p[i] for p in points) for i in range(3)));hi=Vector(tuple(max(p[i] for p in points) for i in range(3)));center=(lo+hi)/2
  bpy.ops.object.camera_add(location=center+Vector((2,-5,2.2)));cam=bpy.context.object;cam.rotation_euler=(center-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.type='ORTHO';cam.data.ortho_scale=max(hi-lo)*1.5;sc.camera=cam
  sc.render.resolution_x=96;sc.render.resolution_y=96;sc.render.resolution_percentage=100
  sc.render.filepath=str(A/'textures/item'/f'uniform_{name}_{slot}.png');bpy.ops.render.render(write_still=True)
  bpy.data.objects.remove(cam,do_unlink=True)
 for ob in sc.objects:ob.hide_render=False

def viewport(name):
 for ob in bpy.context.scene.objects:ob.hide_set(not ob.name.startswith(name+'.'))
 for area in bpy.context.screen.areas:
  if area.type=='VIEW_3D':
   area.spaces.active.shading.type='SOLID';area.spaces.active.shading.color_type='MATERIAL'
   r=area.spaces.active.region_3d;r.view_distance=3.3;r.view_location=Vector((0,0,1.18));r.view_rotation=__import__('mathutils').Euler((math.radians(73),0,math.radians(-22)),'XYZ').to_quaternion()
   area.spaces.active.overlay.show_floor=False;area.spaces.active.overlay.show_extras=False
