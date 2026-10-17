#!/usr/bin/env python3
"""Build a textured Mark I Male tank GLB and matching LOD from source geometry.

The source is a deterministic Blender-free approximation of the 1916 British lozenge tank:
full-length tracks, fixed male sponsons, separate left/right 6-pdr yaw/pitch groups, and six
rotating road wheels per side. It is not represented as a user-approved reference model.

Run from any working directory: python tools/wwi/build_mark_i.py
Then generate its walkable collision asset: python tools/deck/gen_boxes.py mark_i
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from glb_builder import GlbBuilder, Mesh, axis_quaternion, png_rgba, solid_texture

ASSETS = ROOT / "mods/aiarsenal/src/main/resources/assets/aiarsenal"
MODEL_DIR = ASSETS / "models/glb"
TEXTURE_DIR = ASSETS / "textures/entity/mark_i"
ITEM_DIR = ASSETS / "textures/item"

# Model space (+X left, +Y up, +Z forward), matching VehicleSpec and DeckShapes.
TRACK_PATH = [(0.13, -2.45), (0.82, -3.28), (2.28, -3.28), (2.28, 3.28),
              (0.82, 3.28), (0.13, 2.45)]
TRACK_LINKS = 56
TRACK_HALF_THICKNESS = 0.048
# Place running gear tangent to the inside face of the flat lower/upper runs, not visibly floating inside the belt.
ROAD_WHEEL_RADIUS = 0.305
END_WHEEL_RADIUS = 0.385
RETURN_ROLLER_RADIUS = 0.115
ROAD_WHEEL_CENTER_Y = TRACK_PATH[0][0] + TRACK_HALF_THICKNESS + ROAD_WHEEL_RADIUS
RETURN_ROLLER_CENTER_Y = TRACK_PATH[2][0] - TRACK_HALF_THICKNESS - RETURN_ROLLER_RADIUS


def atlas() -> bytes:
    """British khaki enamel with deterministic, subtle brushed grain and weathering specks."""
    w = h = 128
    rng = random.Random(1916)
    pixels = []
    for y in range(h):
        for x in range(w):
            # Restrained khaki enamel: no large checkerboard patches on armour panels.
            grain = rng.randrange(-4, 5) + int(2*math.sin(x*.11+y*.04))
            pixels.append(tuple(max(0,min(255,c+grain)) for c in (126,123,84))+(255,))
    return png_rgba(w, h, pixels)


def icon_mark_i() -> bytes:
    """A hand-drawn, side-on item icon (not a rendered screenshot)."""
    w = h = 64
    p = [(0, 0, 0, 0)] * (w * h)

    def poly(points, color):
        for y in range(max(0, int(min(q[1] for q in points))), min(h, int(max(q[1] for q in points)) + 1)):
            for x in range(max(0, int(min(q[0] for q in points))), min(w, int(max(q[0] for q in points)) + 1)):
                inside = False
                j = len(points) - 1
                for i, (xi, yi) in enumerate(points):
                    xj, yj = points[j]
                    if (yi > y + 0.5) != (yj > y + 0.5) and x + 0.5 < (xj - xi) * (y + 0.5 - yi) / ((yj - yi) or 1e-9) + xi:
                        inside = not inside
                    j = i
                if inside:
                    p[y * w + x] = color

    def rect(x0, y0, x1, y1, color):
        poly(((x0,y0),(x1,y0),(x1,y1),(x0,y1)), color)

    ink=(34,39,31,255); track=(65,68,54,255); khaki=(109,116,76,255)
    light=(144,143,99,255); steel=(103,109,101,255); dark=(48,54,47,255)
    poly(((4,43),(12,28),(50,28),(60,43),(51,54),(12,54)),ink)
    poly(((7,42),(15,31),(48,31),(56,42),(48,51),(15,51)),track)
    poly(((14,39),(19,33),(45,33),(51,39),(51,44),(44,49),(19,49),(13,44)),khaki)
    poly(((21,31),(24,27),(41,27),(45,31),(45,35),(21,35)),light)
    rect(23,35,43,42,khaki); rect(39,37,56,39,steel); rect(52,36,57,40,light)
    for x in range(12,56,5):
        rect(x,42,x+1,44,(132,127,94,255)); rect(x,49,x+1,51,(132,127,94,255))
    for x in (18,25,32,39,46):
        rect(x,43,x+2,47,dark); rect(x,44,x+1,46,light)
    return png_rgba(w,h,p)


def icon_shell() -> bytes:
    w=h=16
    p=[(0,0,0,0)]*(w*h)
    def fill(points,color):
        for y in range(h):
            for x in range(w):
                inside=False; j=len(points)-1
                for i,(xi,yi) in enumerate(points):
                    xj,yj=points[j]
                    if (yi>y+.5)!=(yj>y+.5) and x+.5<(xj-xi)*(y+.5-yi)/((yj-yi) or 1e-9)+xi: inside=not inside
                    j=i
                if inside:p[y*w+x]=color
    fill(((6,14),(11,14),(11,10),(6,10)),(151,112,46,255))
    fill(((7,10),(10,10),(10,5),(7,5)),(106,116,111,255))
    fill(((7,5),(10,5),(9,3),(8,2)),(183,188,171,255))
    fill(((6,11),(11,11),(11,10),(6,10)),(218,176,71,255))
    return png_rgba(w,h,p)


def loop_length(path=TRACK_PATH) -> float:
    return sum(math.hypot(path[(i+1)%len(path)][0]-p[0], path[(i+1)%len(path)][1]-p[1]) for i,p in enumerate(path))


def loop_point(distance: float, path=TRACK_PATH):
    """Position and tangent at arclength distance around the closed side-profile path."""
    lengths=[math.hypot(path[(i+1)%len(path)][0]-p[0], path[(i+1)%len(path)][1]-p[1]) for i,p in enumerate(path)]
    total=sum(lengths)
    d=distance%total
    for i,length in enumerate(lengths):
        if d<=length or i==len(lengths)-1:
            a=path[i]; b=path[(i+1)%len(path)]
            u=0.0 if length<=1e-9 else d/length
            dy=(b[0]-a[0])/length; dz=(b[1]-a[1])/length
            y=a[0]+(b[0]-a[0])*u; z=a[1]+(b[1]-a[1])*u
            # Local +Z is rotated about X to align to the path tangent (dy,dz).
            rx=math.atan2(-dy,dz)
            return y,z,rx
        d-=length
    raise AssertionError("unreachable loop point")


def add_rivets(mesh: Mesh, material: int, side: int, positions, radius=0.022) -> None:
    for y,z in positions:
        mesh.cylinder(material,(side*1.118,y,z),(side*1.151,y,z),radius,6,material)


def hull_mesh(paint: int, highlight: int, steel: int, dark: int, brass: int, lod=False) -> Mesh:
    m=Mesh()
    # Low central hull, armour belt, roof, angular front command cabin and rear engine deck.
    m.box(paint,(-1.15,0.43,-2.32),(1.15,1.29,2.25))
    m.box(highlight,(-0.94,1.18,-2.16),(0.94,1.77,2.08))
    m.box(paint,(-0.78,1.48,1.20),(0.78,2.12,2.18))
    m.box(paint,(-0.84,1.39,-2.14),(0.84,1.80,-1.31))
    # Raised access plates, hatch rings, engine louvers and the flat roof around the cabin.
    m.box(steel,(-0.36,1.775,-1.92),(0.36,1.815,-1.30))
    m.box(dark,(-0.28,1.817,-1.83),(0.28,1.825,-1.38))
    for i in range(7):
        x=-0.55+i*0.18
        m.box(dark,(x,1.812,-1.10),(x+0.07,1.845,-0.45))
    for z in (-0.30,0.25):
        m.box(steel,(-0.32,1.79,z-0.19),(0.32,1.83,z+0.19))
        m.box(dark,(-0.24,1.833,z-0.13),(0.24,1.84,z+0.13))
    # Driver vision slits and armoured covers; simple, low-profile plates rather than modern glass optics.
    for x in (-0.48,-0.16,0.16,0.48):
        m.box(dark,(x-0.09,1.82,2.185),(x+0.09,1.91,2.205))
        m.box(steel,(x-0.10,1.91,2.185),(x+0.10,1.94,2.215))
    m.box(steel,(-0.69,1.35,2.205),(0.69,1.39,2.23))
    # Rear exhausts and protected air intakes.
    for x in (-0.58,0.58):
        m.cylinder(steel,(x,1.78,-2.07),(x,1.95,-2.07),0.075,10,steel)
        m.cylinder(dark,(x,1.95,-2.07),(x,1.99,-2.07),0.055,10,dark)
    # Fixed male side-sponson armour: the gun groups rotate inside the ports, not the entire pods.
    for side in (-1,1):
        m.box(paint,(side*1.41-0.40,0.80,-0.87),(side*1.41+0.40,1.66,0.87))
        m.box(highlight,(side*1.41-0.405,0.91,-0.68),(side*1.41+0.405,1.51,0.68))
        m.box(dark,(side*1.41-0.412,1.10,-0.24),(side*1.41+0.412,1.34,0.24))
        # Port rim on the outward face. The centre remains open around the traversing 6-pdr barrel.
        x0=side*1.82
        for z in (-0.32,0.32):
            m.box(steel,(x0-0.025,1.04,z-0.055),(x0+0.025,1.40,z+0.055))
        for y in (1.03,1.41):
            m.box(steel,(x0-0.025,y-0.03,-0.32),(x0+0.025,y+0.03,0.32))
        # Sponson door hinge, handle and lower towing eye.
        m.box(steel,(side*1.82,0.90,-0.62),(side*1.86,1.02,-0.50))
        m.cylinder(brass,(side*1.82,1.22,-0.52),(side*1.88,1.22,-0.52),0.035,8,brass)
        m.cylinder(steel,(side*1.15,0.54,-1.95),(side*1.85,0.54,-1.95),0.045,8,steel)
    # Rivet rows on hull plates: external low-poly heads, not a texture-only substitute.
    if not lod:
        zs=[-2.05,-1.72,-1.39,-1.06,-0.73,-0.40,-0.07,0.26,0.59,0.92,1.25,1.58]
        ys=(0.56,0.91,1.26,1.60)
        for side in (-1,1):
            add_rivets(m,brass,side,[(y,z) for y in ys for z in zs],0.018)
            # Sponson perimeter rivets on its visible plate.
            for z in (-0.73,-0.46,-0.19,0.19,0.46,0.73):
                for y in (0.91,1.55):
                    m.cylinder(brass,(side*1.805,y,z),(side*1.833,y,z),0.018,6,brass)
    else:
        for side in (-1,1):
            add_rivets(m,brass,side,[(0.58,z) for z in (-1.8,-0.8,0.2,1.2)],0.022)
    # Roof bolts, cabin-front fasteners and low towing hooks.
    for x in (-0.70,-0.35,0.35,0.70):
        for z in (-1.8,1.65):
            m.cylinder(brass,(x,1.78,z),(x,1.81,z),0.022,6,brass)
    if not lod:
        # A restrained pair of rivet rows on the driver cab plate makes the front silhouette read at gameplay scale.
        for y in (1.59,1.98):
            for i in range(8):
                x=-0.63+i*0.18
                m.cylinder(brass,(x,y,2.18),(x,y,2.215),0.022,8,brass)
    for side in (-1,1):
        m.cylinder(steel,(side*0.76,0.48,2.18),(side*0.76,0.48,2.42),0.055,8,steel)
        m.cylinder(steel,(side*0.76,0.48,-2.25),(side*0.76,0.48,-2.48),0.055,8,steel)
    return m


def wheel_mesh(steel: int, rubber: int, radius=0.31) -> Mesh:
    m=Mesh()
    m.cylinder(rubber,(-0.105,0,0),(0.105,0,0),radius,12,rubber)
    m.cylinder(steel,(-0.125,0,0),(0.125,0,0),radius*0.70,12,steel)
    m.cylinder(steel,(-0.137,0,0),(0.137,0,0),radius*0.24,10,steel)
    return m


def gun_mesh(steel: int, dark: int, brass: int, lod=False) -> tuple[int, int]:
    """Two separate local meshes: a stationary breech and a recoil-translating QF 6-pdr barrel."""
    breech=Mesh()
    breech.box(steel,(-0.13,-0.12,-0.26),(0.13,0.12,0.12))
    breech.box(dark,(-0.15,-0.15,-0.13),(0.15,0.15,-0.03))
    breech.cylinder(steel,(0,-0.02,-0.06),(0,0.02,0.14),0.105,12,steel)
    breech.box(brass,(-0.04,0.12,-0.20),(0.04,0.16,-0.08))
    barrel=Mesh()
    barrel.cylinder(steel,(0,0,0.04),(0,0,1.18 if not lod else 1.12),0.043,16,steel)
    barrel.cylinder(dark,(0,0,1.14 if not lod else 1.08),(0,0,1.23 if not lod else 1.17),0.058,16,dark)
    # Iron front blade and rear notch; no modern optic.
    barrel.box(steel,(-0.025,0.044,0.96),(0.025,0.092,1.01))
    barrel.box(steel,(-0.07,0.065,0.08),(0.07,0.085,0.14))
    if not lod:
        barrel.cylinder(brass,(0,-0.055,0.19),(0,-0.055,0.25),0.025,8,brass)
    return (breech,barrel)


def build(path: Path, lod=False) -> tuple[int,float]:
    b=GlbBuilder("British Mark I Male (1916), LOD1" if lod else "British Mark I Male (1916)")
    tex=atlas()
    b.add_texture(tex,"mark_i_khaki_paint")
    steel_tex=b.add_texture(solid_texture(128,128,(155,161,163),57,4),"steel")
    paint=b.add_material("British khaki enamel",(0.96,0.98,0.91,1),0.10,0.83)
    highlight=b.add_material("sun-faded khaki plate",(1.0,1.0,0.83,1),0.08,0.82)
    steel=b.add_material("blued steel",(0.69,0.74,0.72,1),0.73,0.36,steel_tex)
    dark=b.add_material("dark steel",(0.40,0.44,0.41,1),0.66,0.44,steel_tex)
    track=b.add_material("cast track steel",(0.52,0.54,0.48,1),0.56,0.72,steel_tex)
    grouser=b.add_material("raised tread cleat",(0.75,0.76,0.66,1),0.58,0.60,steel_tex)
    rubber=b.add_material("wheel rubber and shadow",(0.31,0.34,0.30,1),0.02,0.96)
    brass=b.add_material("aged brass fasteners",(0.91,0.72,0.36,1),0.66,0.37)

    hull_i=b.add_mesh("mark_i_hull",hull_mesh(paint,highlight,steel,dark,brass,lod))
    wheel_i=b.add_mesh("road_wheel",wheel_mesh(steel,rubber,ROAD_WHEEL_RADIUS))
    sprocket_i=b.add_mesh("end_wheel",wheel_mesh(steel,rubber,END_WHEEL_RADIUS))
    return_roller_i=b.add_mesh("return_roller",wheel_mesh(steel,rubber,RETURN_ROLLER_RADIUS))
    breech,barrel=gun_mesh(steel,dark,brass,lod)
    breech_i=b.add_mesh("qf_6pdr_breech",breech)
    barrel_i=b.add_mesh("qf_6pdr_recoiling_barrel",barrel)

    root=b.add_node("mark_i")
    hull=b.add_node("hull",hull_i)
    children=[hull]

    # Individual links follow a closed lozenge path; each link's translation and tangent rotation are animated
    # one pitch per clip cycle, so a complete cycle permutes identical shoes with no visible reset/pop.
    track_animations=[]
    for side,name in ((1,"L"),(-1,"R")):
        children_side=[]
        channels=[]
        pitch=loop_length()/TRACK_LINKS
        poses=[]
        for i in range(TRACK_LINKS):
            y,z,rx=loop_point(i*pitch)
            poses.append((side*1.50,y,z,rx))
        for i,(x,y,z,rx) in enumerate(poses):
            shoe=Mesh()
            shoe.oriented_box(track,(0,0,0),(0.25,0,0),(0,TRACK_HALF_THICKNESS,0),(0,0,pitch*0.46))
            if not lod:
                shoe.oriented_box(grouser,(0,0,0.017),(0.038,0,0),(0,0.022,0),(0,0,pitch*0.31))
                shoe.oriented_box(grouser,(0,0,-0.017),(0.038,0,0),(0,0.022,0),(0,0,pitch*0.31))
            shoe_i=b.add_mesh(f"track_shoe_{name}_{i:02d}",shoe)
            link=b.add_node(f"MarkI_link_{name}_{i:02d}",shoe_i,(x,y,z),axis_quaternion((1,0,0),rx))
            children_side.append(link)
            y2,z2,rx2=loop_point((i+1)*pitch)
            channels.extend([
                (link,"translation",[0.0,1.0],[x,y,z,x,y2,z2]),
                (link,"rotation",[0.0,1.0],[*axis_quaternion((1,0,0),rx),*axis_quaternion((1,0,0),rx2)]),
            ])
        group=b.add_node("track_links_"+name,children=children_side)
        children.append(group)
        track_animations.append(("track_"+name,channels))

    # Riveted track-frame armour: early British tanks enclose the running gear,
    # not exposed modern road wheels floating between a narrow belt and the hull.
    for side, name in ((1, "L"), (-1, "R")):
        frame = Mesh()
        profile = [(0.26,-2.40),(0.87,-3.12),(2.13,-3.12),
                   (2.13,3.12),(0.87,3.12),(0.26,2.40)]
        frame.prism(paint, profile, "x", side*1.25, side*1.31, 2.0)
        children.append(b.add_node("track_frame_"+name, b.add_mesh("track_frame_"+name,frame)))

    # Six road wheels plus the driven rear sprocket and front idler, animated by the existing tracked-vehicle pose.
    for side,name in ((1,"L"),(-1,"R")):
        for i in range(6):
            z=-1.78+i*0.70
            children.append(b.add_node(f"rw_{name}_{i}",wheel_i,(side*1.38,ROAD_WHEEL_CENTER_Y,z)))
        for i,z in enumerate((-2.0,0.0,2.0)):
            children.append(b.add_node(f"rr_{name}_{i}",return_roller_i,(side*1.38,RETURN_ROLLER_CENTER_Y,z)))
        children.append(b.add_node("spk_"+name,sprocket_i,(side*1.38,1.20,-3.20)))
        children.append(b.add_node("idl_"+name,sprocket_i,(side*1.38,1.20,3.20)))
    # Independent side-sponson pivots are fixed outward by +/-90 deg; the runtime counter-rotates both to the aimed line.
    yaw_right=b.add_node("launcher_yaw",translation=(-1.41,1.22,0.0),rotation=axis_quaternion((0,1,0),-math.pi/2))
    pitch_right=b.add_node("launcher_pitch")
    breech_right=b.add_node("gun_breech",breech_i)
    barrel_right=b.add_node("barrel",barrel_i)
    sight_right=b.add_node("iron_sight",translation=(0,0.04,0.17))
    b.set_children(pitch_right,[breech_right,barrel_right,sight_right])
    b.set_children(yaw_right,[pitch_right])
    yaw_left=b.add_node("launcher_yaw_left",translation=(1.41,1.22,0.0),rotation=axis_quaternion((0,1,0),math.pi/2))
    pitch_left=b.add_node("launcher_pitch_left")
    breech_left=b.add_node("gun_breech_left",breech_i)
    barrel_left=b.add_node("barrel_left",barrel_i)
    sight_left=b.add_node("iron_sight_left",translation=(0,0.04,0.17))
    b.set_children(pitch_left,[breech_left,barrel_left,sight_left])
    b.set_children(yaw_left,[pitch_left])
    children.extend([yaw_right,yaw_left])
    b.set_children(root,children)
    for name,channels in track_animations:
        b.add_animation(name,channels)
    b.save(path,[root])
    # Independent stats for logs and test budgets.
    tri=sum(b.accessors[p["indices"]]["count"]//3 for m in b.meshes for p in m["primitives"])
    return tri,loop_length()/TRACK_LINKS


def main() -> None:
    TEXTURE_DIR.mkdir(parents=True,exist_ok=True)
    ITEM_DIR.mkdir(parents=True,exist_ok=True)
    paint=atlas()
    (TEXTURE_DIR/"paint.png").write_bytes(paint)
    (ITEM_DIR/"mark_i_vehicle.png").write_bytes(icon_mark_i())
    (ITEM_DIR/"shells_57.png").write_bytes(icon_shell())
    for name,lod in (("mark_i.glb",False),("mark_i_lod1.glb",True)):
        tris,pitch=build(MODEL_DIR/name,lod)
        print(f"{MODEL_DIR/name}: { (MODEL_DIR/name).stat().st_size:,} bytes; {tris:,} triangles; links={TRACK_LINKS} pitch={pitch:.4f} m; LOD1={lod}")
    print("Regenerate walkable collision: python tools/deck/gen_boxes.py mark_i")


if __name__=="__main__":
    main()
