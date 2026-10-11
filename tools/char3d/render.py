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

skin = pick("skins", "middleage_asian_male", "young_asian_male", "male", avoid=("female",))
if skin:
    try: HumanService.set_character_skin(AssetService.find_asset_absolute_path(skin, asset_subdir="skins"), human, skin_type="ENHANCED_SSS")
    except Exception as e: print("skin", e)
for sub, words, at in (("eyes", ("brown",), "Eyes"), ("eyebrows", ("eyebrow001", "eyebrow"), "Eyebrows"), ("eyelashes", ("eyelashes01", "eyelash"), "Eyelashes"),
                       ("hair", ("short02", "short", "male"), "Hair"), ("teeth", ("teeth",), "Teeth")):
    asset(sub, pick(sub, *words), at)
top = pick("clothes", "tshirt", "t_shirt", "shirt", avoid=("long", "female"))
bottom = pick("clothes", "short", "jogger", "pants", "trouser", avoid=("female", "skirt"))
shoes = pick("clothes", "shoes", "sneaker", "boot", avoid=("female", "heel"))
worn = [asset("clothes", n, "Clothes") for n in (top, bottom, shoes)]
print("WORN", top, bottom, shoes)

# gym kit colours: black with crimson + neon-lime accents
def recolor(obj, base, accent=None):
    if not obj: return
    for slot in obj.material_slots:
        m = slot.material
        if not m or not m.use_nodes: continue
        for n in m.node_tree.nodes:
            if n.type == "BSDF_PRINCIPLED":
                n.inputs["Base Color"].default_value = base
                n.inputs["Roughness"].default_value = 0.55
                if accent:
                    n.inputs["Emission Color"].default_value = accent
                    n.inputs["Emission Strength"].default_value = 0.0
recolor(worn[0], (0.02, 0.02, 0.025, 1)); recolor(worn[1], (0.03, 0.005, 0.01, 1)); recolor(worn[2], (0.9, 0.9, 0.92, 1))

rig = HumanService.add_builtin_rig(human, "default")
pb = rig.pose.bones
print("BONES", len(pb))

def pose(spec):
    for b in pb: b.rotation_mode = "XYZ"; b.rotation_euler = (0, 0, 0)
    for name, (x, y, z) in spec.items():
        if name in pb: pb[name].rotation_euler = Euler((math.radians(x), math.radians(y), math.radians(z)), "XYZ")
    bpy.context.view_layer.update()

POSES = {
    "idle": {"upperarm01.L": (0, 0, -62), "upperarm01.R": (0, 0, 62), "lowerarm01.L": (12, 0, 0), "lowerarm01.R": (12, 0, 0)},
    "flex": {"upperarm01.L": (0, 0, -5), "upperarm01.R": (0, 0, 5), "lowerarm01.L": (0, 0, -120), "lowerarm01.R": (0, 0, 120),
             "spine03": (-6, 0, 0)},
    "victory": {"upperarm01.L": (0, 0, 55), "upperarm01.R": (0, 0, -55), "lowerarm01.L": (0, 0, -25), "lowerarm01.R": (0, 0, 25), "head": (-12, 0, 0)},
}

# ---- arena: dark floor, haze-free, coloured rim lights
sc = bpy.context.scene
sc.render.engine = "CYCLES"; sc.cycles.device = "CPU"; sc.cycles.samples = int(os.environ.get("SAMPLES", "48"))
sc.cycles.use_denoising = True; sc.cycles.denoiser = "OPENIMAGEDENOISE"
sc.render.resolution_x = 512; sc.render.resolution_y = 704; sc.render.film_transparent = False
sc.view_settings.view_transform = "AgX"; sc.view_settings.look = "AgX - Punchy" if "AgX - Punchy" in [l.name for l in []] else "None"
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
light("AREA", (2.5, -3.5, 3.2), 900, (1.0, 0.96, 0.92), 2.5)          # key
light("AREA", (-3.2, 2.0, 2.6), 1400, (0.95, 0.12, 0.22), 1.2)       # crimson rim
light("AREA", (3.2, 2.2, 2.4), 1100, (0.55, 1.0, 0.15), 1.2)         # lime rim
light("AREA", (0, -4, 0.6), 120, (0.4, 0.6, 1.0), 4.0)               # cool fill from below

# camera framing the whole figure
dims = human.dimensions; h = max(dims.z, 1.6)
cam_d = bpy.data.cameras.new("cam"); cam_d.lens = 55
cam = bpy.data.objects.new("cam", cam_d); sc.collection.objects.link(cam); sc.camera = cam
target = bpy.data.objects.new("tgt", None); target.location = (0, 0, h * 0.52); sc.collection.objects.link(target)
tc = cam.constraints.new("TRACK_TO"); tc.target = target; tc.track_axis = "TRACK_NEGATIVE_Z"; tc.up_axis = "UP_Y"

def shot(name, yaw_deg=0):
    r = h * 2.35
    a = math.radians(-90 + yaw_deg)
    cam.location = (r * math.cos(a), r * math.sin(a), h * 0.55)
    sc.render.filepath = os.path.join(OUT, name + ".png")
    bpy.ops.render.render(write_still=True)
    print("RENDERED", name)

pose(POSES["idle"]); shot("idle")
pose(POSES["flex"]); shot("flex")
pose(POSES["victory"]); shot("victory")
pose(POSES["idle"]); shot("three_quarter", 35)
print("ALLDONE")
