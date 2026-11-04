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
def tan_tree(nt, seen, TAN=TAN):
    if nt in seen: return
    seen.add(nt)
    for n in list(nt.nodes):
        if n.type == "GROUP" and n.node_tree: tan_tree(n.node_tree, seen, TAN)
        if n.type == "TEX_IMAGE" and n.image:
            nm = n.image.name.lower()
            print("IMG", nt.name, nm)
            if any(k in nm for k in ("normal", "bump", "spec", "rough", "ao", "sss", "trans")): continue
            for l in list(n.outputs["Color"].links):
                if "color" not in l.to_socket.name.lower() and "base" not in l.to_socket.name.lower(): continue
                mix = nt.nodes.new("ShaderNodeMix"); mix.data_type = "RGBA"; mix.blend_type = "MULTIPLY"
                mix.inputs["Factor"].default_value = 1.0; mix.inputs[7].default_value = TAN
                to = l.to_socket; nt.links.remove(l)
                nt.links.new(n.outputs["Color"], mix.inputs[6]); nt.links.new(mix.outputs[2], to)
                print("TANNED", nt.name, nm, "->", to.name)
_seen = set()
for slot in human.material_slots:
    if slot.material and slot.material.use_nodes: tan_tree(slot.material.node_tree, _seen)

for sub, words, at in (("eyes", ("high-poly", "brown"), "Eyes"), ("eyebrows", ("eyebrow010", "eyebrow001", "eyebrow"), "Eyebrows"),
                       ("eyelashes", ("eyelashes01", "eyelash"), "Eyelashes"), ("hair", ("short02", "short"), "Hair")):
    asset(sub, pick(sub, *words), at)
for o in bpy.data.objects:
    if o.type == "MESH" and any(k in o.name.lower() for k in ("short0", "hair", "eyebrow")):
        for sl in o.material_slots:
            if sl.material and sl.material.use_nodes: tan_tree(sl.material.node_tree, set(), (0.05, 0.035, 0.025, 1))
shoes = asset("clothes", pick("clothes", "shoes02", "shoes01", "shoes"), "Clothes")

rig = HumanService.add_builtin_rig(human, "default")
pb = rig.pose.bones
print("BONES", len(pb), [b.name for b in pb][:80])
bpy.context.view_layer.update()

# ---- gym kit cut from the body itself (compression tank + shorts), so it follows every pose perfectly
def bone_z(n): return (rig.matrix_world @ rig.data.bones[n].head_local).z
hip = bone_z("upperleg01.L"); knee = bone_z("lowerleg01.L"); neck = bone_z("neck01")
waist = hip + 0.30 * (neck - hip); thigh = hip - 0.50 * (hip - knee)
_vis = [(m, m.show_viewport) for m in human.modifiers]
for m, _ in _vis: m.show_viewport = False
bpy.context.view_layer.update()
dg = bpy.context.evaluated_depsgraph_get()
rest = bpy.data.meshes.new_from_object(human.evaluated_get(dg), preserve_all_data_layers=True, depsgraph=dg)
gi = {g.name: g.index for g in human.vertex_groups}
_gs = {}
def wsum(v, prefixes):
    ids = _gs.get(prefixes)
    if ids is None: ids = _gs[prefixes] = {i for k, i in gi.items() if k.startswith(prefixes)}
    return sum(g.weight for g in v.groups if g.group in ids)

for m, v in _vis: m.show_viewport = v
bpy.context.view_layer.update()
RV = rest.vertices
def z(v): return (human.matrix_world @ RV[v.index].co).z
print("VCOUNT", len(RV), len(human.data.vertices))
ARM, HEAD = ("upperarm", "lowerarm", "hand", "finger", "wrist"), ("neck", "head", "jaw")
# per-vertex signed fields (metres-ish; > 0 = inside). Interpolated across faces, so the cut lines come out smooth.
SHX = abs((rig.matrix_world @ rig.data.bones['upperarm01.L'].head_local).x)
print('SHOULDER X', SHX)
me = human.data
att = me.color_attributes.new("kit", "FLOAT_COLOR", "POINT")
for v in me.vertices:
    zz = z(v); aw = wsum(v, ARM); hw = wsum(v, HEAD)
    ax = abs((human.matrix_world @ RV[v.index].co).x)
    side = (SHX * 0.80 - ax) if zz > neck - 0.20 else 1.0                       # racer-back arm holes
    scoop = (neck - 0.045 + 1.4 * max(0.0, ax - 0.055)) - zz                   # scoop neckline
    tank = min(zz - (waist - 0.03), scoop, side, (0.45 - aw) * 0.2, (0.25 - hw) * 0.2)
    shorts = min(zz - thigh, (waist + 0.025) - zz, (0.5 - aw) * 0.2)
    E = lambda f: max(0.0, min(1.0, 0.5 + f * 0.5))   # encode around 0.5: colour attributes clamp negatives
    att.data[v.index].color = (E(tank), E(shorts), E(zz - thigh), E((waist + 0.025) - zz))

def kit_material(m):
    nt = m.node_tree; N = nt.nodes.new; L = nt.links.new
    out = next(n for n in nt.nodes if n.type == "OUTPUT_MATERIAL" and n.is_active_output)
    src = out.inputs["Surface"].links[0].from_socket
    a = N("ShaderNodeAttribute"); a.attribute_name = "kit"
    for l in list(out.inputs["Displacement"].links): nt.links.remove(l)
    sep = N("ShaderNodeSeparateColor"); L(a.outputs["Color"], sep.inputs[0])
    def cmp(sock, op, v):
        mth = N("ShaderNodeMath"); mth.operation = op; L(sock, mth.inputs[0]); mth.inputs[1].default_value = v; return mth.outputs[0]
    def mm(x, y, op="MAXIMUM"):
        mth = N("ShaderNodeMath"); mth.operation = op; L(x, mth.inputs[0]); L(y, mth.inputs[1]); return mth.outputs[0]
    t = cmp(sep.outputs[0], "GREATER_THAN", 0.5); sh = cmp(sep.outputs[1], "GREATER_THAN", 0.5)
    mask = mm(t, sh)
    hem = mm(sh, cmp(sep.outputs[2], "LESS_THAN", 0.507), "MULTIPLY")
    band = mm(sh, cmp(a.outputs["Alpha"], "LESS_THAN", 0.511), "MULTIPLY")
    fab = N("ShaderNodeBsdfPrincipled"); fab.inputs["Roughness"].default_value = 0.38; fab.inputs["Sheen Weight"].default_value = 0.5
    fab.inputs["Specular IOR Level"].default_value = 0.6
    c1 = N("ShaderNodeMix"); c1.data_type = "RGBA"; L(sh, c1.inputs["Factor"]); c1.inputs[6].default_value = (0.012, 0.012, 0.015, 1); c1.inputs[7].default_value = (0.035, 0.004, 0.01, 1)
    CR, LM = (1.0, 0.05, 0.2, 1), (0.6, 1.0, 0.1, 1)
    c2 = N("ShaderNodeMix"); c2.data_type = "RGBA"; L(hem, c2.inputs["Factor"]); L(c1.outputs[2], c2.inputs[6]); c2.inputs[7].default_value = CR
    c3 = N("ShaderNodeMix"); c3.data_type = "RGBA"; L(band, c3.inputs["Factor"]); L(c2.outputs[2], c3.inputs[6]); c3.inputs[7].default_value = LM
    L(c3.outputs[2], fab.inputs["Base Color"])
    em = N("ShaderNodeMix"); em.data_type = "RGBA"; L(band, em.inputs["Factor"]); em.inputs[6].default_value = CR; em.inputs[7].default_value = LM
    L(em.outputs[2], fab.inputs["Emission Color"]); L(mm(hem, band), fab.inputs["Emission Strength"])
    mix = N("ShaderNodeMixShader"); L(mask, mix.inputs[0]); L(src, mix.inputs[1]); L(fab.outputs[0], mix.inputs[2])
    L(mix.outputs[0], out.inputs["Surface"])
print("SLOTS", [(sl.material.name if sl.material else None) for sl in human.material_slots])
for sl in human.material_slots:
    if sl.material and sl.material.use_nodes: kit_material(sl.material); print("KIT on", sl.material.name)

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

def mirror(d): return (-d[0], d[1], d[2])
POSE = {   # left-side directions (mirrored for right); extra bones listed explicitly
    "idle":    {"up": (0.22, 0.04, -1), "lo": (0.12, -0.22, -1), "head": (0, -0.04, 1), "fist": 60},
    "flex":    {"up": (1, 0.12, -0.35), "lo": (0.05, 0.12, 1), "head": (0, -0.08, 1), "fist": 85},
    "victory": {"up": (0.55, 0.05, 1), "lo": (0.3, 0, 1), "head": (0, -0.25, 1), "fist": 85},
    "hero":    {"up": (0.75, 0.25, -0.62), "lo": (-0.55, -0.35, -0.45), "head": (0, -0.05, 1), "fist": 70},
}
def lerp3(a, b, t): return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
def blend(a, b, t):
    A, B = POSE[a] if isinstance(a, str) else a, POSE[b] if isinstance(b, str) else b
    return {k: (lerp3(A[k], B[k], t) if isinstance(A[k], tuple) else A[k] + (B[k] - A[k]) * t) for k in A}
def apply(spec, breath=0.0, sway=0.0):
    reset(); fists(spec["fist"])
    for n, k in (("spine03", 1.6), ("spine02", 1.0)):      # breathing: tiny local chest lift
        if n in pb: pb[n].rotation_quaternion = Euler((math.radians(-k * breath), 0, 0)).to_quaternion()
    up = P(*spec["up"]); lo = P(*spec["lo"])
    up = (up[0] + 0.03 * breath * S, up[1], up[2])
    arms(up, lo)
    if spec["head"][1] < -0.15: aim("head", spec["head"])          # only big head moves (victory look-up)
    elif sway and "head" in pb: pb["head"].rotation_quaternion = Euler((0, 0, math.radians(2.5 * sway))).to_quaternion()
def pose(name): apply(POSE[name])

def ease(t): return t * t * (3 - 2 * t)
def seg(t, a, b): return ease(max(0.0, min(1.0, (t - a) / (b - a))))
def clip_spec(clip, t):
    br = math.sin(t * 2 * math.pi)
    if clip == "idle":
        return blend("idle", "idle", 0), br, math.sin(t * 2 * math.pi + 1.0) * 0.5
    if clip == "flex":         # rise into a double-biceps, two squeezes, back down
        k = seg(t, 0.0, 0.32) * (1 - seg(t, 0.78, 1.0))
        sp = blend("idle", "flex", k)
        sq = math.sin(max(0.0, min(1.0, (t - 0.32) / 0.46)) * 4 * math.pi) * 0.5 + 0.5 if 0.32 < t < 0.78 else 0
        sp["lo"] = lerp3(sp["lo"], P_inv((0.25, 0.2, 0.95)), 0.35 * sq * k); return sp, br * 0.4, 0
    if clip == "victory":      # arms shoot up, two fist pumps, come down
        k = seg(t, 0.0, 0.25) * (1 - seg(t, 0.8, 1.0))
        sp = blend("idle", "victory", k)
        pump = abs(math.sin(max(0.0, min(1.0, (t - 0.25) / 0.55)) * 2 * math.pi)) if 0.25 < t < 0.8 else 0
        sp["up"] = lerp3(sp["up"], (0.4, 0.02, 1), 0.5 * pump * k); sp["lo"] = lerp3(sp["lo"], (0.05, -0.1, 1), 0.6 * pump * k)
        return sp, 0, 0
def P_inv(d): return d
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

MODE = os.environ.get("MODE", "stills")
if MODE == "stills":
    for n, yaw in (("idle", 0), ("flex", 0), ("victory", 0), ("hero", 32)):
        try: pose(n)
        except Exception as e: import traceback; traceback.print_exc()
        shot(n, yaw)
else:
    # app clips like Pip's packs: transparent background + shadow catcher, square frames, sharded by frame
    CLIPS = {"idle": 30, "flex": 28, "victory": 28}
    sh, nsh = int(os.environ.get("SHARD", "0")), int(os.environ.get("SHARDS", "1"))
    sc.render.film_transparent = True; floor.is_shadow_catcher = True; wall.hide_render = True
    rs = int(os.environ.get("RES", "560")); sc.render.resolution_x = sc.render.resolution_y = rs
    cam_d.lens = 58
    jobs = [("portrait", 0, 1)] + [(c, i, n) for c, n in CLIPS.items() for i in range(n)]
    for j, (c, i, n) in enumerate(jobs):
        if j % nsh != sh: continue
        if c == "portrait": pose("hero"); yaw = 20
        else:
            sp, br, sw = clip_spec(c, i / n); apply(sp, br, sw); yaw = 14
        os.makedirs(os.path.join(OUT, c), exist_ok=True)
        r = h * 1.95; a = math.radians(-90 + yaw)
        cam.location = (r * math.cos(a), r * math.sin(a), h * 0.34)
        sc.render.filepath = os.path.join(OUT, c, "%03d.png" % i)
        bpy.ops.render.render(write_still=True); print("FRAME", c, i)
print("ALLDONE")
