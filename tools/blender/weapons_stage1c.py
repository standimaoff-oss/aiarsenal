"""Executed via execute_code in a LIVE Blender GUI + Blender-MCP session.
Dimensions in mm. Reuses stock geometry helpers, not existing weapon GLBs.
No geometry generated outside Blender; each scene gets viewport review and packed .blend.
"""
import bpy, sys, importlib.util, os
from pathlib import Path
from mathutils import Vector, Quaternion, Matrix
import math
ROOT=Path(__file__).resolve().parents[2]
sys.argv.append('--library-only')
spec=importlib.util.spec_from_file_location('weapon_library',ROOT/'tools/blender/weapons_r22.py')
w=importlib.util.module_from_spec(spec);spec.loader.exec_module(w)
Z=(0,0,0)
MODELS={}


def assemble(name, groups, sight, muzzle, grip, support):
    root=w.empty(name,Z);gun=w.empty(name+'.gun',Z,root)
    for suffix,origin in groups.items():w.join_node(name+'.'+suffix,gun,origin)
    w.empty(name+'.sight',sight,gun);w.empty(name+'.muzzle',muzzle,gun)
    w.empty(name+'.arm_r',grip,gun);w.empty(name+'.arm_l',support,gun)
    return gun


def scope(name, pu):
    g=name+'.optic';v=55 if pu else 58;rear=350 if pu else 300;front=519 if pu else 575
    w.lathe(g,[(rear,0),(rear,15),(rear+27,15),(rear+32,12),(front-24,12),(front-20,16),(front,16),(front,0)],'Blued',20,0,v)
    w.lathe(g,[(rear-0.5,0),(rear-0.5,12.5)],'LensSolid',20,0,v)
    w.lathe(g,[(front+0.5,0),(front+0.5,13.5)],'LensSolid',20,0,v)
    for u in [rear+42,front-42]:
        w.ring(g,u-5,u+5,12,15,'Blued',20,0,v)
        w.box(g,u-9,u+9,17,v-11,-9,9,'Blued',0.6)
        for side in [-1,1]:w.cylx(g,u,v-5,side*14,side*17,3,'Steel',10)
    # PU side bracket / ZF claw mount bases are separate silhouette features.
    if pu:
        w.box(g,390,510,2,14,19,26,'Blued',0.7)
        w.box(g,394,410,9,45,16,24,'Blued',0.6)
        for u in [409,490]:w.cylx(g,u,9,26,28,4,'Steel',10)
    else:
        for u in [rear+42,front-42]:w.box(g,u-13,u+13,15,22,-17,17,'Blued',0.8)
    u=rear+(front-rear)*0.53
    w.cylz(g,u,0,v+9,v+22,10,'Blued',16)
    w.cylx(g,u,v,-23,-10,9,'Blued',16)
    return w.P(rear-8,v)


def sniper(name,pu):
    sc=w.new_scene(name.upper());w.U0=432 if pu else 445
    (w.mosin_body if pu else w.k98_body)(name+'.body')
    b=name+'.bolt';o=w.P(450,0)
    w.lathe(b,[(380,0),(380,10),(400,12),(404,9),(530,9),(530,0)],'Steel',18)
    # Both sniper variants have a bent-down handle, unlike the straight infantry Mosin.
    w.rod(b,w.P(420,0,-9),w.P(420,-12,-30),4,4,'Steel',12)
    w.rod(b,w.P(420,-12,-30),w.P(405,-40,-35),4,4,'Steel',12)
    w.sphere(b,w.P(405,-43,-35),8,'Blued',seg=12)
    w.prism(name+'.trigger',[(428,-44),(434,-44),(438,-54),(434,-62),(430,-60)],-3,3,'Steel',0.4)
    sight=scope(name,pu);end=1233 if pu else 1111
    w.flash(name+'.flash',end,0,90,32)
    w.cartridge(name+'.round',478,0,54 if pu else 57,6,6,5,4,77 if pu else 80,4,0,12)
    gun=assemble(name,{'body':None,'bolt':o,'optic':None,'trigger':None,'round':w.P(500,0),'flash':w.P(end,0)},sight,w.P(end,0),w.P(360,-50),w.P(750,-30))
    bolt=bpy.data.objects[b];arm=bpy.data.objects[name+'.arm_r'];round_=bpy.data.objects[name+'.round']
    w.clip(name+'_bolt',bolt,[(0,Z,Z),(4,Z,(0,65,0)),(12,(0,.09,0),(0,65,0)),(20,Z,(0,65,0)),(27,Z,Z),(30,Z,Z)])
    for anim,total in [(name+'_reload',29),(name+'_reload_empty',38)]:
        def f(n):return round(n*total/29)
        w.clip(anim,gun,[(0,Z,Z),(f(5),Z,(0,0,-18)),(f(24),Z,(0,0,-18)),(total,Z,Z)])
        w.clip(anim,bolt,[(0,Z,Z),(f(4),Z,(0,65,0)),(f(8),(0,.09,0),(0,65,0)),(f(21),(0,.09,0),(0,65,0)),(f(25),Z,(0,65,0)),(total,Z,Z)])
        # One cartridge approached from the right side, never a stripper clip through the optic.
        w.clip(anim,round_,[(0,Z,Z),(f(5),(-.06,0,.04),Z),(f(14),(-.035,0,.025),Z),(f(20),Z,Z),(total,Z,Z)])
        w.clip(anim,arm,[(0,Z,Z),(f(5),(-.09,-.10,.06),Z),(f(20),(-.07,-.12,.035),Z),(total,Z,Z)])
    return sc


def tt_mag(g):
    w.prism(g,[(20,-18),(44,-18),(29,-113),(5,-113)],-10,10,'Blued',.6)
    w.box(g,3,32,-116,-112,-12,12,'Blued',.5)
    for v in [-40,-55,-70,-85,-100]:w.cylx(g,22+(v+40)*.13,v,-10.6,-10.1,2,'BlackPaint',8)


def c96_clip(g):
    for i in range(10):w.cartridge(g,135,-57+i*7.9,25,4.5,4.7,4.2,3.9,35,3.8,0,10)
    w.box(g,132,136,-61,20,-6,6,'ClipSteel',.3)


def pistol(name,c96):
    sc=w.new_scene(name.upper());w.U0=95 if c96 else 60
    g=name+'.body';b=name+'.bolt';length=312 if c96 else 196
    if c96:
        # Long exposed barrel, box magazine IN FRONT of trigger, broomhandle grip and ring hammer.
        w.lathe(g,[(169,12),(183,11),(192,8),(312,7),(312,0)],'Blued',20)
        w.box(g,66,179,-18,8,-14,14,'Blued',1)
        w.prism(g,[(112,-17),(164,-17),(164,-60),(111,-60)],-13,13,'Blued',1)
        w.prism(g,[(65,-14),(86,-17),(68,-122),(35,-128),(27,-115),(48,-32)],-14,14,'Walnut',2)
        for v in range(-107,-37,9):w.box(g,35+(v+107)*.18,66+(v+107)*.18,v,v+2,-14.4,14.4,'Walnut',.3)
        w.box(g,78,113,-51,-46,-5,5,'Blued',.5);w.box(g,108,114,-48,-17,-5,5,'Blued',.5)
        # Ring hammer bore is transverse, not parallel to the barrel (review correction).
        hammer=w.ring(g,29,36,7,10,'Blued',16,0,5)
        centre=w.P(32.5,5)
        hammer.data.transform(Matrix.Translation(centre) @ Matrix.Rotation(math.pi/2,4,'Z') @ Matrix.Translation(-centre))
        w.box(b,42,123,-4,11,-11,11,'Blued',.7)
        w.box(b,45,52,9,17,-12,-2,'Blued',.2);w.box(b,45,52,9,17,2,12,'Blued',.2)
        w.box(g,299,304,6,16,-1,1,'Blued',.2)
        c96_clip(name+'.clip');ammo='clip';origin=w.P(150,-20)
        grip=w.P(51,-78);support=w.P(117,-45);sight=w.P(42,16)
    else:
        # TT-33: 196 mm slide, 116 mm barrel, short exposed hammer, narrow 8-round grip.
        w.box(g,22,176,-21,-8,-14,14,'Blued',1)
        w.prism(g,[(31,-18),(67,-18),(48,-115),(12,-115)],-14,14,'Blued',1)
        for side in [-1,1]:
            w.prism(g,[(32,-34),(59,-34),(44,-108),(18,-108)],side*14,side*16,'Bakelite',.8)
            for v in range(-100,-38,6):w.box(g,23+(v+100)*.15,44+(v+100)*.15,v,v+1,side*16,side*16.4,'BlackPaint',0)
            w.cylx(g,37,-70,side*16.4,side*17,4,'Steel',12)
        w.box(g,66,99,-49,-44,-5,5,'Blued',.6);w.box(g,94,100,-45,-20,-5,5,'Blued',.6)
        w.box(b,13,196,-8,15,-13,13,'Blued',1.5)
        w.box(b,102,134,14.7,15.4,-9,5,'BlackPaint',.2) # ejection port surface
        w.lathe(g,[(90,0),(90,6),(197,6),(197,0)],'Steel',16)
        for u in range(20,52,4):w.box(b,u,u+1,-3,11,-13.5,13.5,'BlackPaint',.1)
        w.box(g,4,18,-2,8,-5,5,'Blued',.5) # hammer
        w.box(b,184,190,15,20,-1,1,'Blued',.2)
        w.box(b,23,29,15,21,-7,-1.5,'Blued',.2);w.box(b,23,29,15,21,1.5,7,'Blued',.2)
        tt_mag(name+'.mag');ammo='mag';origin=w.P(30,-60)
        grip=w.P(35,-72);support=w.P(51,-75);sight=w.P(22,20)
    w.prism(name+'.trigger',[(88 if c96 else 76,-22),(94 if c96 else 82,-23),(92 if c96 else 80,-39),(87 if c96 else 75,-38)],-3,3,'Steel',.4)
    w.lathe(g,[(length+.5,0),(length+.5,3.7)],'BlackPaint',12)
    w.flash(name+'.flash',length+1,0,65,24)
    gun=assemble(name,{'body':None,'bolt':w.P(50,0),'trigger':None,ammo:origin,'flash':w.P(length+1,0)},sight,w.P(length+1,0),grip,support)
    for anim,total in [(name+'_reload',72 if c96 else 46),(name+'_reload_empty',72 if c96 else 58)]:
        m=bpy.data.objects[name+'.'+ammo];arm=bpy.data.objects[name+'.arm_l'];bolt=bpy.data.objects[b]
        drop=(0,.02,.13) if c96 else (0,.02,-.18)
        w.clip(anim,gun,[(0,Z,Z),(8,Z,(0,0,-24)),(total-8,Z,(0,0,-24)),(total,Z,Z)])
        w.clip(anim,m,[(0,Z,Z),(12,drop,Z),(total*.55,drop,Z),(total*.75,Z,Z),(total,Z,Z)])
        hand_drop=(.04,.02,.11) if c96 else (.04,.02,-.16)
        w.clip(anim,arm,[(0,Z,Z),(12,hand_drop,Z),(total*.55,hand_drop,Z),(total*.75,Z,Z),(total,Z,Z)])
        if c96 or anim.endswith('_empty'):
            w.clip(anim,bolt,[(0,Z,Z),(8,(0,.032,0),Z),(total-10,(0,.032,0),Z),(total-3,Z,Z),(total,Z,Z)])
    return sc


def build():
    for name,sc in [('mosin_pu',sniper('mosin_pu',True)),('k98k_zf39',sniper('k98k_zf39',False)),('c96',pistol('c96',True)),('tt33',pistol('tt33',False))]:
        MODELS[name]=sc;w.export(sc,name)
    for name,kind in [('round_762x54r','r'),('round_792x57','k'),('clip_c96','c'),('mag_tt33','t')]:
        sc=w.new_scene(name.upper());w.U0=0
        if kind=='c':c96_clip(name)
        elif kind=='t':tt_mag(name)
        else:w.cartridge(name,0,0,54 if kind=='r' else 57,6,6,5,4,77 if kind=='r' else 80,4,0,12)
        w.join_node(name);w.export(sc,name,False)
    for sc in list(bpy.data.scenes):
        if sc.name not in w.SCENES:bpy.data.scenes.remove(sc)
    for material in bpy.data.materials:
        if material.node_tree:
            for node in material.node_tree.nodes:
                if node.type == 'TEX_IMAGE': material.node_tree.nodes.active=node;break
    for image in bpy.data.images:
        if not image.packed_file:image.pack()
    (ROOT/'models').mkdir(exist_ok=True)
    bpy.context.window.scene=MODELS['mosin_pu']
    bpy.ops.wm.save_as_mainfile(filepath=str(ROOT/'models/weapons_stage1c.blend'),compress=True)
    print('STAGE1C_MODELS_EXPORTED')


def viewport(name,front=False):
    bpy.context.window.scene=MODELS[name]
    for ob in bpy.context.scene.objects:
        ob.select_set(ob.type=='MESH' and not ob.name.endswith(('.flash','.round','.clip')))
        ob.hide_viewport=ob.name.endswith(('.flash','.round','.clip'))
    for area in bpy.context.screen.areas:
        if area.type!='VIEW_3D':continue
        space=area.spaces.active;space.shading.type='SOLID'
        space.shading.color_type='TEXTURE';space.overlay.show_overlays=False
        region=next(r for r in area.regions if r.type=='WINDOW')
        with bpy.context.temp_override(area=area,region=region):bpy.ops.view3d.view_selected(use_all_regions=False)
        # Three-quarter side view: show wood, receiver, optic mounts, and bent bolt handle.
        space.region_3d.view_rotation=Quaternion((0.80,0.35,0.25,0.40)).normalized()
        space.region_3d.view_distance*=1.2
