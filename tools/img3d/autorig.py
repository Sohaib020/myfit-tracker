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

args = [a for a in sys.argv[1:] if not a.startswith("-")]
src, pre = args[0], args[1]
SRCIMG = args[2] if len(args) > 2 else None
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
# ---- 4. project the sharp concept image onto front-facing surfaces (TRELLIS bakes only ~1K textures)
if SRCIMG:
    sc.render.film_transparent = True; sc.render.filepath = pre + "_alpha.png"
    for m in list(ob.modifiers):
        if m.type == "ARMATURE": m.show_render = False
    bpy.ops.render.render(write_still=True)
    for m in list(ob.modifiers):
        if m.type == "ARMATURE": m.show_render = True
    si_img = Image.open(SRCIMG).convert("RGB"); si = np.array(si_img).astype(np.int32); SH_, SW_ = si.shape[:2]
    with mp.solutions.pose.Pose(static_image_mode=True, model_complexity=1) as p2:
        r2 = p2.process(np.array(si_img))
    if not r2.pose_landmarks: print("PROJ skip: no pose in source"); SRCIMG = None
if SRCIMG:
    sl = r2.pose_landmarks.landmark
    def fit(ids):   # least-squares similarity (scale, tx, ty) from mesh-render px -> source px
        P = np.array([[lm[i].x * RES, lm[i].y * RES] for i in ids]); Q = np.array([[sl[i].x * SW_, sl[i].y * SH_] for i in ids])
        pc, qc = P.mean(0), Q.mean(0); s_ = np.sqrt(((Q - qc) ** 2).sum() / max(((P - pc) ** 2).sum(), 1e-9))
        return s_, qc - s_ * pc
    HEADF = fit([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]); BODYF = fit([11, 12, 23, 24, 25, 26, 27, 28, 0])
    print("PROJ fits head", HEADF, "body", BODYF)
    corner = np.median(np.concatenate([si[:8, :8].reshape(-1, 3), si[:8, -8:].reshape(-1, 3)]), axis=0)
    fg = np.abs(si - corner).sum(axis=2) > 40
    from PIL import ImageFilter
    fg = np.array(Image.fromarray((fg * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(7))) > 128   # erode: keep away from edges
    me = ob.data
    uvl = me.uv_layers.new(name="proj")
    co = np.array([v.co[:] for v in me.vertices]); nrm = np.array([v.normal[:] for v in me.vertices])
    neck_z = (J["shoulder.L"].z + J["shoulder.R"].z) / 2 + 0.04 * H
    u_ = (co[:, 0] / cd.ortho_scale + 0.5) * RES; v_ = (0.5 - (co[:, 2] - H / 2) / cd.ortho_scale) * RES
    t = np.clip((co[:, 2] - (neck_z - 0.03)) / 0.06, 0, 1)            # 0 body … 1 head
    px = (1 - t) * (BODYF[0] * u_ + BODYF[1][0]) + t * (HEADF[0] * u_ + HEADF[1][0])
    py = (1 - t) * (BODYF[0] * v_ + BODYF[1][1]) + t * (HEADF[0] * v_ + HEADF[1][1])
    inside = fg[np.clip(py.astype(int), 0, SH_ - 1), np.clip(px.astype(int), 0, SW_ - 1)] & (px >= 0) & (px < SW_) & (py >= 0) & (py < SH_)
    UV = np.stack([px / SW_, 1 - py / SH_], 1)
    for poly in me.polygons:
        for li in poly.loop_indices:
            uvl.data[li].uv = UV[me.loops[li].vertex_index]
    # weight: faces the camera AND is the front-most surface at that spot (1 cm grid depth test)
    gx = np.floor(co[:, 0] / 0.01).astype(int); gz = np.floor(co[:, 2] / 0.01).astype(int)
    front = {}
    for i, k in enumerate(zip(gx, gz)):
        if co[i, 1] < front.get(k, 9): front[k] = co[i, 1]
    vis = np.array([1.0 if co[i, 1] - front[(gx[i], gz[i])] < 0.02 else 0.0 for i in range(len(co))])
    facing = np.clip((-nrm[:, 1] - 0.35) / 0.45, 0, 1)
    w_ = vis * facing * inside
    at = me.attributes.new("projw", "FLOAT", "POINT"); at.data.foreach_set("value", w_.astype(np.float32))
    img = bpy.data.images.load(os.path.abspath(SRCIMG)); img.pack(); img.name = "concept"; img.use_fake_user = True
    base_uv = me.uv_layers[0]; me.uv_layers.active = base_uv; base_uv.active_render = True
    print("PROJ weighted verts", round(float((w_ > 0.5).mean()), 3))
sc.camera = None; bpy.data.objects.remove(cam)
json.dump({k: list(v) for k, v in J.items() if isinstance(v, Vector)}, open(pre + "_joints.json", "w"))
bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(pre + ".blend"))
print("RIG_OK")
