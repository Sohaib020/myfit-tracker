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

# ---- 0. rebuild as ONE watertight surface (TRELLIS gives ~100 overlapping shells that tear when posed),
#         then bake the original colours onto it
def rebuild(src_ob, voxel=float(os.environ.get("VOXEL", "0.0075")), faces=int(os.environ.get("FACES", "60000")), tex=int(os.environ.get("BAKE", "2048"))):
    new = src_ob.copy(); new.data = src_ob.data.copy(); sc.collection.objects.link(new); new.name = "body"
    new.data.materials.clear()
    for m_ in list(new.modifiers): new.modifiers.remove(m_)
    bpy.ops.object.select_all(action="DESELECT"); new.select_set(True); bpy.context.view_layer.objects.active = new
    so = new.modifiers.new("thick", "SOLIDIFY"); so.thickness = voxel * 2.2; so.offset = 0; so.use_even_offset = True
    bpy.ops.object.modifier_apply(modifier="thick")
    rm = new.modifiers.new("vox", "REMESH"); rm.mode = "VOXEL"; rm.voxel_size = voxel; rm.use_smooth_shade = True
    bpy.ops.object.modifier_apply(modifier="vox")
    n0 = len(new.data.polygons)
    if n0 > faces:
        dc = new.modifiers.new("dec", "DECIMATE"); dc.ratio = faces / n0; bpy.ops.object.modifier_apply(modifier="dec")
    for uv in list(new.data.uv_layers): new.data.uv_layers.remove(uv)
    new.data.uv_layers.new(name="UVMap")
    bpy.ops.object.mode_set(mode="EDIT"); bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.003); bpy.ops.object.mode_set(mode="OBJECT")
    img = bpy.data.images.new("baked", tex, tex); img.use_fake_user = True
    mat = bpy.data.materials.new("baked"); mat.use_nodes = True; nt_ = mat.node_tree
    ti = nt_.nodes.new("ShaderNodeTexImage"); ti.image = img; nt_.nodes.active = ti
    nt_.links.new(ti.outputs[0], nt_.nodes["Principled BSDF"].inputs["Base Color"]); new.data.materials.append(mat)
    sc.render.engine = "CYCLES"; sc.cycles.samples = 1; sc.cycles.device = "CPU"
    sc.render.bake.use_selected_to_active = True; sc.render.bake.cage_extrusion = 0.03; sc.render.bake.max_ray_distance = 0.0
    sc.render.bake.use_pass_direct = False; sc.render.bake.use_pass_indirect = False; sc.render.bake.margin = 6
    bpy.ops.object.select_all(action="DESELECT"); src_ob.select_set(True); new.select_set(True); bpy.context.view_layer.objects.active = new
    bpy.ops.object.bake(type="DIFFUSE", pass_filter={"COLOR"})
    print("REBUILT faces", n0, "->", len(new.data.polygons))
    bpy.data.objects.remove(src_ob)
    return new
if os.environ.get("REBUILD", "0") == "1":   # experimental: bake picks up inner shells on TRELLIS meshes
    ob = rebuild(ob)

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
# detector sometimes reads the figure as facing away (animals): character's left must be on +X
if J["shoulder.L"].x < J["shoulder.R"].x:
    for k in [k for k in list(J) if k.endswith(".L") or k.endswith(".L_vis")]:
        r_ = k.replace(".L", ".R"); J[k], J[r_] = J[r_], J[k]
    print("SWAP left/right")
# anatomy sanity: upper and lower limb segments are roughly equal; fur/loose sleeves can fool the detector
for a_, b_, c_ in (("shoulder", "elbow", "wrist"), ("hip", "knee", "ankle")):
    for sd in ("L", "R"):
        A, B, C = J[f"{a_}.{sd}"], J[f"{b_}.{sd}"], J[f"{c_}.{sd}"]
        r_ = (B - A).length / max((C - B).length, 1e-6)
        unsure = min(J.get(f"{b_}.{sd}_vis", 1), J.get(f"{c_}.{sd}_vis", 1)) < 0.8
        if unsure and not 0.45 < r_ < 2.2:
            m_ = A.lerp(C, 0.5); m_.y = depth(m_.x, m_.z); J[f"{b_}.{sd}"] = m_
            print("ANATOMY fix", b_, sd, "ratio", round(r_, 2))
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
# ---- weight clean-up: arms must not pull the head/hood; distance skinning if heat weighting left gaps
def bone_dist_weights(target, power=4.0):
    bones = [(b.name, Vector(b.head_local), Vector(b.tail_local)) for b in arm.data.bones]
    for g in list(target.vertex_groups): target.vertex_groups.remove(g)
    groups = {n: target.vertex_groups.new(name=n) for n, _, _ in bones}
    for v in target.data.vertices:
        p_ = v.co; ds = []
        for n, h_, t_ in bones:
            ab = t_ - h_; tt = max(0.0, min(1.0, (p_ - h_).dot(ab) / max(ab.length_squared, 1e-9)))
            ds.append((n, (h_ + ab * tt - p_).length + 1e-4))
        ds.sort(key=lambda x: x[1]); near = ds[:3]
        ws = [1 / d ** power for _, d in near]; tot = sum(ws)
        for (n, _), w in zip(near, ws): groups[n].add([v.index], w / tot, "REPLACE")
gap = sum(1 for v in ob.data.vertices if not any(g.weight > 0.01 for g in v.groups)) / len(ob.data.vertices)
if gap > 0.03:
    print("FALLBACK distance skinning, gap", round(gap, 3)); bone_dist_weights(ob)
    if not any(m.type == "ARMATURE" for m in ob.modifiers): am = ob.modifiers.new("arm", "ARMATURE"); am.object = arm
    ob.parent = arm
shz = (J["shoulder.L"].z + J["shoulder.R"].z) / 2; shw = abs(J["shoulder.L"].x - J["shoulder.R"].x)
ARMG = {g.index for g in ob.vertex_groups if g.name.split(".")[0] in ("upper_arm", "forearm", "hand")}
headg = ob.vertex_groups.get("head") or ob.vertex_groups.get("neck")
fixed = 0
for v in ob.data.vertices:
    if v.co.z > shz + 0.06 * H and abs(v.co.x) < shw * 0.42:
        aw = sum(g.weight for g in v.groups if g.group in ARMG)
        if aw > 0.01:
            for g in v.groups:
                if g.group in ARMG: g.weight = 0.0
            headg.add([v.index], aw, "ADD"); fixed += 1
print("HEAD_CLEAN verts", fixed)
# loose fragments (coat tails, trims) copy the weights of the nearest point on the main body
def islands(me_):
    adj = [[] for _ in me_.vertices]
    for e in me_.edges: a1, b1 = e.vertices; adj[a1].append(b1); adj[b1].append(a1)
    lab = [-1] * len(me_.vertices); out = []
    for i in range(len(lab)):
        if lab[i] >= 0: continue
        st = [i]; lab[i] = len(out); cur = []
        while st:
            k = st.pop(); cur.append(k)
            for w in adj[k]:
                if lab[w] < 0: lab[w] = len(out); st.append(w)
        out.append(cur)
    return out
isl = sorted(islands(ob.data), key=len, reverse=True)
NV = len(ob.data.vertices)
others = [x for x in isl if len(x) < 0.02 * NV]
big = [x for x in isl if len(x) >= 0.02 * NV]
if os.environ.get("SKIN") == "dist": others = isl
if others:
    bones = [(bb.name, Vector(bb.head_local), Vector(bb.tail_local)) for bb in arm.data.bones]
    groups = {g.name: g for g in ob.vertex_groups}
    for n, _, _ in bones:
        if n not in groups: groups[n] = ob.vertex_groups.new(name=n)
    def seg_d(p_, h_, t_):
        ab = t_ - h_; tt = max(0.0, min(1.0, (p_ - h_).dot(ab) / max(ab.length_squared, 1e-9))); return (h_ + ab * tt - p_).length + 1e-4
    RIGID = os.environ.get("PIECE_MODE", "rigid") == "rigid"
    for part in others:
        c_ = sum((ob.data.vertices[i].co for i in part), Vector()) / len(part)
        for g in ob.vertex_groups: g.remove(part)
        for i in part:
            p_ = c_ if RIGID else ob.data.vertices[i].co
            ds = sorted(((n, seg_d(p_, h_, t_)) for n, h_, t_ in bones), key=lambda x: x[1])[:2]
            ws = [1 / d ** 4 for _, d in ds]; tot = sum(ws)
            for (n, _), w in zip(ds, ws): groups[n].add([i], w / tot, "REPLACE")
    pass
hipz = (J["hip.L"].z + J["hip.R"].z) / 2
ARMG2 = {g.index for g in ob.vertex_groups if g.name.split(".")[0] in ("upper_arm", "forearm", "hand", "shoulder")}
spg = ob.vertex_groups.get("spine"); hpg = ob.vertex_groups.get("hips"); tg = 0
for v in ob.data.vertices:
    if hipz - 0.02 < v.co.z < shz - 0.03 and abs(v.co.x) < shw * 0.5 * 0.92:
        aw = sum(g.weight for g in v.groups if g.group in ARMG2 and not ob.vertex_groups[g.group].name.startswith("shoulder"))
        if aw > 0.01:
            for g in v.groups:
                if g.group in ARMG2 and not ob.vertex_groups[g.group].name.startswith("shoulder"): g.weight = 0.0
            (spg if v.co.z > hipz + 0.35 * (shz - hipz) else hpg).add([v.index], aw, "ADD"); tg += 1
print("TORSO_GUARD verts", tg)
if others:
    print("PIECES distance-weighted", len(others), "of", len(isl), "islands; main", len(big))
sc.camera = None; bpy.data.objects.remove(cam)
json.dump({k: list(v) for k, v in J.items() if isinstance(v, Vector)}, open(pre + "_joints.json", "w"))
bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(pre + ".blend"))
print("RIG_OK")
