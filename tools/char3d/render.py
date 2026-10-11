"""
R19 test: a realistic 3D gym athlete with MPFB (MakeHuman Plugin For Blender, CC0 assets), rigged, posed and rendered
in a dark arena with neon rim lights. Run: blender -b -P render.py -- <outdir>
First pass = quality check: 4 stills (idle, flex, victory, three-quarter) + an asset inventory.
"""
import bpy, sys, os, math, json, importlib
from mathutils import Euler, Vector

OUT = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else "out"
os.makedirs(OUT, exist_ok=True)

def imp(pkg, key):
    for m in list(sys.modules):
        if m.endswith(pkg):
            return getattr(importlib.import_module(m), key)
    raise ImportError(pkg)

HumanService = imp("mpfb.services.humanservice", "HumanService")
TargetService = imp("mpfb.services.targetservice", "TargetService")
AssetService = imp("mpfb.services.assetservice", "AssetService")
Props = imp("mpfb.entities.objectproperties", "HumanObjectProperties")

# ---- inventory of installed assets (to pick clothes/skins by name)
inv = {}
for sub in ("skins", "clothes", "hair", "eyebrows", "eyelashes", "eyes", "teeth", "tongue", "proxymeshes"):
    try:
        inv[sub] = sorted(os.path.basename(p) for p in AssetService.list_mhclo_assets(sub)) if sub not in ("skins",) else sorted(os.path.basename(p) for p in AssetService.list_mhmat_assets(sub))
    except Exception as e:
        inv[sub] = ["ERR " + str(e)]
json.dump(inv, open(os.path.join(OUT, "inventory.json"), "w"), indent=1)
print("INVENTORY", {k: len(v) for k, v in inv.items()})

def pick(sub, *words, avoid=()):
    for w in words:
        for n in inv.get(sub, []):
            ln = n.lower()
            if w in ln and not any(a in ln for a in avoid):
                return n
    return None

# ---- the athlete: male powerlifter, South Asian-leaning ethnicity mix
bpy.ops.wm.read_factory_settings(use_empty=True)
human = HumanService.create_human()
for k, v in {"gender": 1.0, "age": 0.5, "muscle": 1.0, "weight": 0.72, "height": 0.62, "proportions": 0.8,
             "african": 0.12, "asian": 0.38, "caucasian": 0.50}.items():
    try: Props.set_value(k, v, entity_reference=human)
    except Exception as e: print("macro", k, e)
TargetService.reapply_macro_details(human)

def asset(sub, name, atype):
    if not name: return None
    p = AssetService.find_asset_absolute_path(name, asset_subdir=sub)
    if not p: print("missing", sub, name); return None
    try:
        return HumanService.add_mhclo_asset(p, human, asset_type=atype, subdiv_levels=0)
    except Exception as e:
        print("asset fail", name, e); return None

skin = pick("skins", "young_caucasian_male2", "young_caucasian_male", "male", avoid=("female", "special", "old"))
if skin:
    try: HumanService.set_character_skin(AssetService.find_asset_absolute_path(skin, asset_subdir="skins"), human, skin_type="ENHANCED_SSS")
    except Exception as e: print("skin", e)
print("SKIN", skin)

# warm the skin toward a South Asian tone: multiply every diffuse texture by a tan colour
TAN = (0.80, 0.58, 0.44, 1)
for slot in human.material_slots:
    m = slot.material
    if not m or not m.use_nodes: continue
    nt = m.node_tree
    for n in list(nt.nodes):
        if n.type == "TEX_IMAGE" and n.image and "diffuse" in n.image.name.lower():
            for l in list(n.outputs["Color"].links):
                mix = nt.nodes.new("ShaderNodeMix"); mix.data_type = "RGBA"; mix.blend_type = "MULTIPLY"
                mix.inputs["Factor"].default_value = 1.0; mix.inputs[7].default_value = TAN
                nt.links.new(n.outputs["Color"], mix.inputs[6]); nt.links.new(mix.outputs[2], l.to_socket); nt.links.remove(l)
            print("TANNED", m.name)

for sub, words, at in (("eyes", ("high-poly", "brown"), "Eyes"), ("eyebrows", ("eyebrow010", "eyebrow001", "eyebrow"), "Eyebrows"),
                       ("eyelashes", ("eyelashes01", "eyelash"), "Eyelashes"), ("hair", ("short04", "short02", "short"), "Hair")):
    asset(sub, pick(sub, *words), at)
shoes = asset("clothes", pick("clothes", "shoes02", "shoes01", "shoes"), "Clothes")

rig = HumanService.add_builtin_rig(human, "default")
pb = rig.pose.bones
print("BONES", len(pb), [b.name for b in pb][:80])
bpy.context.view_layer.update()

# ---- gym kit cut from the body itself (compression tank + shorts), so it follows every pose perfectly
def bone_z(n): return (rig.matrix_world @ rig.data.bones[n].head_local).z
hip = bone_z("upperleg01.L"); knee = bone_z("lowerleg01.L"); neck = bone_z("neck01")
waist = hip + 0.30 * (neck - hip); thigh = hip - 0.50 * (hip - knee)
dg = bpy.context.evaluated_depsgraph_get()
rest = bpy.data.meshes.new_from_object(human.evaluated_get(dg), preserve_all_data_layers=True, depsgraph=dg)
gi = {g.name: g.index for g in human.vertex_groups}
_gs = {}
def wsum(v, prefixes):
    ids = _gs.get(prefixes)
    if ids is None: ids = _gs[prefixes] = {i for k, i in gi.items() if k.startswith(prefixes)}
    return sum(g.weight for g in v.groups if g.group in ids)

def garment(name, keep, color, trim=None, push=0.006):
    import bmesh
    me = rest.copy(); me.name = name
    ob = bpy.data.objects.new(name, me); sc0 = bpy.context.scene; sc0.collection.objects.link(ob)
    ob.matrix_world = human.matrix_world.copy()
    for g in human.vertex_groups: ob.vertex_groups.new(name=g.name)
    sel = [keep(v) for v in me.vertices]
    bm = bmesh.new(); bm.from_mesh(me); bm.verts.ensure_lookup_table()
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if not all(sel[v.index] for v in f.verts)], context="FACES")
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_faces], context="VERTS")
    bm.to_mesh(me); bm.free()
    for p in me.polygons: p.use_smooth = True
    ob.modifiers.new("arm", "ARMATURE").object = rig
    d = ob.modifiers.new("push", "DISPLACE"); d.strength = push; d.mid_level = 0
    s = ob.modifiers.new("thick", "SOLIDIFY"); s.thickness = 0.003; s.offset = 1
    m = bpy.data.materials.new(name); m.use_nodes = True; b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = color; b.inputs["Roughness"].default_value = 0.42
    b.inputs["Sheen Weight"].default_value = 0.4
    if trim:   # neon trim band near the garment's edge, driven by height
        nt = m.node_tree; geo = nt.nodes.new("ShaderNodeNewGeometry"); sep = nt.nodes.new("ShaderNodeSeparateXYZ")
        ramp = nt.nodes.new("ShaderNodeValToRGB"); nt.links.new(geo.outputs["Position"], sep.inputs[0]); nt.links.new(sep.outputs[2], ramp.inputs[0])
        lo, hi = trim[0]; e = ramp.color_ramp.elements
        e[0].position = 0; e[0].color = (0, 0, 0, 1); e[1].position = 1; e[1].color = (0, 0, 0, 1)
        for pos, c in ((lo - 0.001, (0, 0, 0, 1)), (lo, (1, 1, 1, 1)), (hi, (1, 1, 1, 1)), (hi + 0.001, (0, 0, 0, 1))):
            el = e.new(max(0, min(1, pos))); el.color = c
        ramp.color_ramp.interpolation = "CONSTANT"
        mix = nt.nodes.new("ShaderNodeMix"); mix.data_type = "RGBA"; nt.links.new(ramp.outputs[0], mix.inputs["Factor"])
        mix.inputs[6].default_value = color; mix.inputs[7].default_value = trim[1]
        nt.links.new(mix.outputs[2], b.inputs["Base Color"])
        b.inputs["Emission Color"].default_value = trim[1]; nt.links.new(ramp.outputs[0], b.inputs["Emission Strength"])
    me.materials.append(m)
    return ob

def z(v): return (human.matrix_world @ v.co).z
ARM, HEAD = ("upperarm", "lowerarm", "hand", "finger", "wrist"), ("neck", "head", "jaw")
tank = garment("tank", lambda v: waist - 0.03 < z(v) < neck + 0.02 and wsum(v, ARM) < 0.30 and wsum(v, HEAD) < 0.25,
               (0.015, 0.015, 0.018, 1))
shorts = garment("shorts", lambda v: thigh < z(v) < waist + 0.02 and wsum(v, ARM) < 0.5,
                 (0.02, 0.004, 0.008, 1))
print("KIT", len(tank.data.polygons), len(shorts.data.polygons), "hip", hip, "waist", waist, "neck", neck)

# ---- posing by aiming bones at world directions (independent of bone roll)
from mathutils import Matrix
def aim(name, d):
    if name not in pb: print("nobone", name); return
    p = pb[name]
    bpy.context.view_layer.update()
    cur = p.matrix.copy()
    yv = cur.to_3x3().col[1].normalized()
    q = yv.rotation_difference(Vector(d).normalized())
    rot = q.to_matrix() @ cur.to_3x3()
    m = rot.to_4x4(); m.translation = cur.translation
    p.matrix = m
    bpy.context.view_layer.update()

def reset():
    for b in pb: b.rotation_mode = "QUATERNION"; b.rotation_quaternion = (1, 0, 0, 0); b.location = (0, 0, 0)
    bpy.context.view_layer.update()

FINGERS = [b.name for b in pb if b.name.startswith("finger") and not b.name.startswith("finger1")]
def fists(amount=70):
    # curl each finger segment toward the palm: pick the local-X sign that moves the tip toward the body midline/down
    for n in FINGERS:
        p = pb[n]; best = None
        for sgn in (1, -1):
            p.rotation_quaternion = Euler((math.radians(amount * sgn), 0, 0)).to_quaternion()
            bpy.context.view_layer.update()
            tip = rig.matrix_world @ p.tail
            score = abs(tip.x) + tip.z * 0.5
            if best is None or score < best[0]: best = (score, sgn)
        p.rotation_quaternion = Euler((math.radians(amount * best[1]), 0, 0)).to_quaternion()
    bpy.context.view_layer.update()

def arms(up_l, lo_l, up_r=None, lo_r=None):
    mir = lambda d: (-d[0], d[1], d[2])
    aim("upperarm01.L", up_l); aim("lowerarm01.L", lo_l)
    aim("upperarm01.R", up_r or mir(up_l)); aim("lowerarm01.R", lo_r or mir(lo_l))

S = 1 if (rig.matrix_world @ rig.data.bones["upperarm01.L"].head_local).x > 0 else -1
print("LEFT SIDE SIGN", S)
def P(x, y, zz): return (x * S, y, zz)

def pose(name):
    reset(); fists(65 if name != "victory" else 80)
    if name == "idle":      arms(P(0.22, 0.04, -1), P(0.12, -0.22, -1))
    elif name == "flex":    # double biceps
        aim("spine01", (0, 0.06, 1)); arms(P(1, 0.08, 0.18), P(0.12, 0.06, 1))
    elif name == "victory": aim("head", (0, -0.25, 1)); arms(P(0.55, 0.05, 1), P(0.3, 0, 1))
    elif name == "hero":    # fists on hips
        arms(P(0.75, 0.25, -0.62), P(-0.55, -0.35, -0.45))
# ---- arena: dark floor, haze-free, coloured rim lights
sc = bpy.context.scene
sc.render.engine = "CYCLES"; sc.cycles.device = "CPU"; sc.cycles.samples = int(os.environ.get("SAMPLES", "48"))
sc.cycles.use_denoising = True; sc.cycles.denoiser = "OPENIMAGEDENOISE"
sc.render.resolution_x = 512; sc.render.resolution_y = 704; sc.render.film_transparent = False
sc.view_settings.view_transform = "AgX"
try: sc.view_settings.look = "AgX - Punchy"
except Exception: pass
w = bpy.data.worlds.new("w"); sc.world = w; w.use_nodes = True
w.node_tree.nodes["Background"].inputs[0].default_value = (0.004, 0.005, 0.01, 1); w.node_tree.nodes["Background"].inputs[1].default_value = 1.0
bpy.ops.mesh.primitive_plane_add(size=40); floor = bpy.context.object
fm = bpy.data.materials.new("floor"); fm.use_nodes = True; fb = fm.node_tree.nodes["Principled BSDF"]
fb.inputs["Base Color"].default_value = (0.01, 0.01, 0.012, 1); fb.inputs["Roughness"].default_value = 0.35; floor.data.materials.append(fm)
# back wall with a soft gradient glow
bpy.ops.mesh.primitive_plane_add(size=40, location=(0, 6, 10), rotation=(math.radians(90), 0, 0)); wall = bpy.context.object
wm = bpy.data.materials.new("wall"); wm.use_nodes = True; wb = wm.node_tree.nodes["Principled BSDF"]
wb.inputs["Base Color"].default_value = (0.02, 0.02, 0.03, 1); wall.data.materials.append(wm)

def light(kind, loc, energy, color, size=1.0, rot=(0, 0, 0)):
    d = bpy.data.lights.new(kind + str(len(bpy.data.lights)), kind); d.energy = energy; d.color = color
    if kind == "AREA": d.size = size
    o = bpy.data.objects.new(d.name, d); o.location = loc; o.rotation_euler = rot; sc.collection.objects.link(o)
    tr = o.constraints.new("TRACK_TO"); tr.target = human; tr.track_axis = "TRACK_NEGATIVE_Z"; tr.up_axis = "UP_Y"
light("AREA", (2.2, -3.0, 3.8), 520, (1.0, 0.94, 0.88), 1.4)          # key (small = more contrast)
light("AREA", (0, 0.4, 4.2), 380, (1.0, 0.98, 0.95), 0.8)            # top light carves abs/shoulders
light("AREA", (-3.2, 2.0, 2.6), 1900, (0.95, 0.12, 0.22), 1.0)       # crimson rim
light("AREA", (3.2, 2.2, 2.4), 1500, (0.55, 1.0, 0.15), 1.2)         # lime rim
light("AREA", (0, -4, 0.6), 50, (0.4, 0.6, 1.0), 4.0)               # cool fill from below

# camera framing the whole figure
dims = human.dimensions; h = max(dims.z, 1.6)
cam_d = bpy.data.cameras.new("cam"); cam_d.lens = 70
cam = bpy.data.objects.new("cam", cam_d); sc.collection.objects.link(cam); sc.camera = cam
target = bpy.data.objects.new("tgt", None); target.location = (0, 0, h * 0.52); sc.collection.objects.link(target)
tc = cam.constraints.new("TRACK_TO"); tc.target = target; tc.track_axis = "TRACK_NEGATIVE_Z"; tc.up_axis = "UP_Y"

def shot(name, yaw_deg=0):
    r = h * 2.7
    a = math.radians(-90 + yaw_deg)
    cam.location = (r * math.cos(a), r * math.sin(a), h * 0.30)
    sc.render.filepath = os.path.join(OUT, name + ".png")
    bpy.ops.render.render(write_still=True)
    print("RENDERED", name)

for n, yaw in (("idle", 0), ("flex", 0), ("victory", 0), ("hero", 32)):
    try: pose(n)
    except Exception as e: import traceback; traceback.print_exc()
    shot(n, yaw)
print("ALLDONE")
