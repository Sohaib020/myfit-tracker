"""Free auto-rig for an image-to-3D humanoid (GLB from TRELLIS), using the bpy module + MediaPipe.
 1. load + normalise to 1.8 m, render a flat orthographic front view
 2. MediaPipe Pose finds shoulders/elbows/wrists/hips/knees/ankles; depth = middle of the mesh at that point
 3. build a simple humanoid armature and skin it with Blender's automatic (heat) weights
    (falls back to a voxel-remeshed proxy + weight transfer when heat weighting fails on messy meshes)
 4. save <out>.blend + a joints overlay for checking
usage: python autorig.py in.glb out_prefix"""
import sys, os, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy, bmesh, numpy as np, mathutils
from mathutils import Vector
import toon

src, pre = sys.argv[-2], sys.argv[-1]
H = 1.8
ob = toon.load(src, H)
sc = bpy.context.scene

# ---- 1. orthographic front render (flat lit) for joint detection
RES = 768
cd = bpy.data.cameras.new("ortho"); cd.type = "ORTHO"; cd.ortho_scale = H * 1.1
cam = bpy.data.objects.new("ortho", cd); sc.collection.objects.link(cam); sc.camera = cam
cam.location = (0, -6, H / 2); cam.rotation_euler = (math.radians(90), 0, 0)
sc.render.engine = "CYCLES"; sc.cycles.samples = 8; sc.cycles.device = "CPU"
sc.render.resolution_x = sc.render.resolution_y = RES; sc.view_settings.view_transform = "Standard"
w = bpy.data.worlds.new("w"); sc.world = w; w.use_nodes = True; w.node_tree.nodes["Background"].inputs[0].default_value = (0.55, 0.55, 0.55, 1)
w.node_tree.nodes["Background"].inputs[1].default_value = 2.2
sc.render.filepath = pre + "_front.png"; bpy.ops.render.render(write_still=True)

def px_to_world(u, v):        # normalised image coords -> world x, z
    return (u - 0.5) * cd.ortho_scale, H / 2 - (v - 0.5) * cd.ortho_scale

# ---- 2. joints
import mediapipe as mp
from PIL import Image, ImageDraw
img = np.array(Image.open(pre + "_front.png").convert("RGB"))
with mp.solutions.pose.Pose(static_image_mode=True, model_complexity=2 if os.environ.get("HEAVY") else 1) as p:
    r = p.process(img)
if not r.pose_landmarks: print("RIG_FAIL no pose"); sys.exit(2)
lm = r.pose_landmarks.landmark
verts = np.array([v.co[:] for v in ob.data.vertices])
def depth(x, z, rad=0.05):
    d = np.hypot(verts[:, 0] - x, verts[:, 2] - z); sel = verts[d < rad]
    if len(sel) < 5: sel = verts[np.argsort(d)[:40]]
    return float((sel[:, 1].min() + sel[:, 1].max()) / 2)
J = {}
names = {0: "nose", 11: "shoulder.L", 12: "shoulder.R", 13: "elbow.L", 14: "elbow.R", 15: "wrist.L", 16: "wrist.R",
         19: "index.L", 20: "index.R", 23: "hip.L", 24: "hip.R", 25: "knee.L", 26: "knee.R", 27: "ankle.L", 28: "ankle.R",
         31: "toe.L", 32: "toe.R"}
for i, n in names.items():
    x, z = px_to_world(lm[i].x, lm[i].y); J[n] = Vector((x, depth(x, z), z)); J[n + "_vis"] = lm[i].visibility
top = float(verts[:, 2].max())
print("JOINTS", {k: (tuple(round(c, 3) for c in v) if isinstance(v, Vector) else round(v, 2)) for k, v in J.items()})
ov = Image.open(pre + "_front.png").convert("RGB"); dr = ImageDraw.Draw(ov)
for i in names: dr.ellipse((lm[i].x * RES - 5, lm[i].y * RES - 5, lm[i].x * RES + 5, lm[i].y * RES + 5), fill=(255, 40, 80))
ov.save(pre + "_joints.png")

# ---- 3. armature
def mid(a, b): return (a + b) / 2
pel = mid(J["hip.L"], J["hip.R"]); pel.z += 0.02
sho = mid(J["shoulder.L"], J["shoulder.R"])
neck = sho + Vector((0, 0, 0.035 * H)); head_top = Vector((neck.x, neck.y, top))
spine_mid = pel.lerp(sho, 0.45); chest_top = pel.lerp(sho, 0.92)
bpy.ops.object.armature_add(location=(0, 0, 0)); arm = bpy.context.object; arm.name = "rig"
bpy.ops.object.mode_set(mode="EDIT"); eb = arm.data.edit_bones; eb.remove(eb[0])
def bone(n, h, t, parent=None, connect=False):
    b = eb.new(n); b.head = h; b.tail = t
    if parent: b.parent = eb[parent]; b.use_connect = connect
    return b
bone("hips", pel, spine_mid)
bone("spine", spine_mid, chest_top, "hips", True)
bone("neck", chest_top, neck, "spine", True)
bone("head", neck, head_top, "neck", True)
for s in ("L", "R"):
    shj = J["shoulder." + s]; el = J["elbow." + s]; wr = J["wrist." + s]; ix = J["index." + s]
    hand_end = wr + (ix - wr).normalized() * max((ix - wr).length * 1.3, 0.07)
    bone("shoulder." + s, chest_top.lerp(shj, 0.25), shj, "spine")
    bone("upper_arm." + s, shj, el, "shoulder." + s, True)
    bone("forearm." + s, el, wr, "upper_arm." + s, True)
    bone("hand." + s, wr, hand_end, "forearm." + s, True)
    hp = J["hip." + s]; kn = J["knee." + s]; an = J["ankle." + s]
    an.z = max(an.z, 0.06)
    toe = Vector((an.x, an.y - 0.13, 0.02))
    bone("thigh." + s, hp, kn, "hips"); bone("shin." + s, kn, an, "thigh." + s, True); bone("foot." + s, an, toe, "shin." + s, True)
for b in eb: b.roll = 0
bpy.ops.object.mode_set(mode="OBJECT")

def skin(target):
    bpy.ops.object.select_all(action="DESELECT"); target.select_set(True); arm.select_set(True)
    bpy.context.view_layer.objects.active = arm
    bpy.ops.object.parent_set(type="ARMATURE_AUTO")
    n = len(target.data.vertices)
    un = sum(1 for v in target.data.vertices if not any(g.weight > 0.01 for g in v.groups))
    return un / max(n, 1)

miss = skin(ob)
print("UNWEIGHTED", round(miss, 3))
if miss > 0.03:
    # proxy route: watertight voxel copy gets heat weights, then they are transferred to the real mesh
    for m in list(ob.modifiers):
        if m.type == "ARMATURE": ob.modifiers.remove(m)
    ob.vertex_groups.clear(); ob.parent = None
    px = ob.copy(); px.data = ob.data.copy(); sc.collection.objects.link(px)
    rm = px.modifiers.new("vox", "REMESH"); rm.mode = "VOXEL"; rm.voxel_size = 0.012
    bpy.context.view_layer.objects.active = px; bpy.ops.object.select_all(action="DESELECT"); px.select_set(True)
    bpy.ops.object.modifier_apply(modifier="vox")
    pm = skin(px); print("PROXY UNWEIGHTED", round(pm, 3))
    for g in px.vertex_groups: ob.vertex_groups.new(name=g.name)
    dt = ob.modifiers.new("dt", "DATA_TRANSFER"); dt.object = px; dt.use_vert_data = True
    dt.data_types_verts = {"VGROUP_WEIGHTS"}; dt.vert_mapping = "POLYINTERP_NEAREST"
    dt.layers_vgroup_select_src = "ALL"; dt.layers_vgroup_select_dst = "NAME"
    bpy.context.view_layer.objects.active = ob; bpy.ops.object.select_all(action="DESELECT"); ob.select_set(True)
    bpy.ops.object.modifier_apply(modifier="dt")
    bpy.data.objects.remove(px)
    ob.parent = arm; am = ob.modifiers.new("arm", "ARMATURE"); am.object = arm
    print("UNWEIGHTED after transfer", round(sum(1 for v in ob.data.vertices if not v.groups) / len(ob.data.vertices), 3))
sc.camera = None; bpy.data.objects.remove(cam)
json.dump({k: list(v) for k, v in J.items() if isinstance(v, Vector)}, open(pre + "_joints.json", "w"))
bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(pre + ".blend"))
print("RIG_OK")
