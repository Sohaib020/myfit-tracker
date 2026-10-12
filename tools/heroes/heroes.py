"""Heroes: Quaternius Universal Base Characters (CC0) + Universal Animation Library (CC0) on one 65-bone skeleton.
Loads a base body, hair/brows, skin variant; applies UAL actions; studio-lit renders.
usage (bpy): import heroes as H; H.load(base_dir, anim_glb, 'male'); H.act('Idle_Loop', 12); H.render(path)"""
import bpy, math, os
from mathutils import Vector

S = None; RIG = None; BODY = None

def srgb(h):
    h = h.lstrip('#'); c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c) + (1.0,)

def load(base, anim_glb, sex='male', hair=None, skin=None):
    global S, RIG, BODY
    bpy.ops.wm.read_factory_settings(use_empty=True); S = bpy.context.scene
    gl = os.path.join(base, 'Base Characters', 'Godot - UE', 'Superhero_%s_FullBody.gltf' % ('Male' if sex == 'male' else 'Female'))
    bpy.ops.import_scene.gltf(filepath=gl)
    RIG = next(o for o in S.objects if o.type == 'ARMATURE'); RIG.name = 'hero_rig'
    BODY = next(o for o in S.objects if o.type == 'MESH' and o.name.lower().startswith('superhero'))
    for o in list(S.objects):
        if o.type == 'MESH' and o.name.startswith('Icosphere'): bpy.data.objects.remove(o)
    if hair:
        hp = os.path.join(base, 'Hairstyles', 'Origin at 0', 'glTF (Godot)', hair + '.gltf')
        before = set(S.objects); bpy.ops.import_scene.gltf(filepath=hp)
        for o in set(S.objects) - before:
            if o.type == 'MESH':
                _attach_to_head(o)
            elif o.type == 'ARMATURE': bpy.data.objects.remove(o)
    if skin:
        tex = os.path.join(base, 'Base Characters', 'Textures', skin)
        for sl in BODY.material_slots:
            for n in sl.material.node_tree.nodes:
                if n.type == 'TEX_IMAGE' and 'BaseColor' in (n.image.name if n.image else '') or (n.type == 'TEX_IMAGE' and n.image and ('Ligh' in n.image.name or 'Dark' in n.image.name)):
                    n.image = bpy.data.images.load(tex)
    # animations: import the UAL armature, keep its actions, drop its objects
    before = set(S.objects); bpy.ops.import_scene.gltf(filepath=anim_glb)
    for o in set(S.objects) - before: bpy.data.objects.remove(o, do_unlink=True)
    RIG.animation_data_create()
    return RIG

def _attach_to_head(o):
    """Hair pieces in the 'Origin at 0' set sit in rest-pose space: parent to the Head bone keeping their transform."""
    mw = o.matrix_world.copy(); o.parent = RIG; o.parent_type = 'BONE'; o.parent_bone = 'Head'
    bpy.context.view_layer.update(); o.matrix_world = mw

def actions(): return sorted(a.name.replace('_Armature', '') for a in bpy.data.actions)

def act(name, frame=None, t=None):
    a = bpy.data.actions.get(name + '_Armature') or bpy.data.actions.get(name)
    RIG.animation_data.action = a
    f0, f1 = a.frame_range
    f = frame if frame is not None else (f0 + (f1 - f0) * (t or 0.0))
    S.frame_set(int(f), subframe=f - int(f)); return a

def studio(res=(560, 700), samples=32, bg=(0.012, 0.014, 0.012)):
    sc = S; sc.render.engine = 'CYCLES'; sc.cycles.device = 'CPU'; sc.cycles.samples = samples; sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = res; sc.view_settings.view_transform = 'Khronos PBR Neutral'
    w = bpy.data.worlds.new('w'); sc.world = w; w.use_nodes = True
    w.node_tree.nodes['Background'].inputs[0].default_value = (*bg, 1); w.node_tree.nodes['Background'].inputs[1].default_value = 1.0
    hgt = max((BODY.matrix_world @ Vector(c)).z for c in BODY.bound_box)
    def area(name, loc, energy, size, color):
        d = bpy.data.lights.new(name, 'AREA'); d.energy = energy; d.size = size; d.color = color
        o = bpy.data.objects.new(name, d); sc.collection.objects.link(o); o.location = (loc[0] * hgt, loc[1] * hgt, loc[2] * hgt)
        o.rotation_euler = (Vector((0, 0, hgt * 0.55)) - o.location).to_track_quat('-Z', 'Y').to_euler()
    k = hgt * hgt
    area('key', (-1.2, -1.9, 1.9), 260 * k, 1.2 * hgt, (1.0, 0.96, 0.92))
    area('fill', (1.6, -1.4, 0.9), 70 * k, 1.6 * hgt, (0.85, 0.9, 1.0))
    area('rimL', (-1.3, 1.2, 1.4), 240 * k, 0.6 * hgt, (0.72, 1.0, 0.3))
    area('rimR', (1.3, 1.1, 1.3), 190 * k, 0.6 * hgt, (1.0, 0.25, 0.32))
    bpy.ops.mesh.primitive_plane_add(size=40 * hgt); fl = bpy.context.object
    fm = bpy.data.materials.new('floor'); fm.use_nodes = True; fm.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = srgb('#060706')
    fm.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value = 0.8; fl.data.materials.append(fm)
    cd = bpy.data.cameras.new('cam'); cd.lens = 60; cam = bpy.data.objects.new('cam', cd); sc.collection.objects.link(cam); sc.camera = cam
    t = bpy.data.objects.new('t', None); t.location = (0, 0, hgt * 0.52); sc.collection.objects.link(t)
    tc = cam.constraints.new('TRACK_TO'); tc.target = t; tc.track_axis = 'TRACK_NEGATIVE_Z'; tc.up_axis = 'UP_Y'
    S['hgt'] = hgt
    return hgt

def render(path, yaw=-20, dist=2.3, z=0.6):
    h = S['hgt']; a = math.radians(-90 + yaw); S.camera.location = (dist * h * math.cos(a), dist * h * math.sin(a), z * h)
    S.render.filepath = path; bpy.ops.render.render(write_still=True)

# ------------------------------------------------------------------ gym moves authored on top of the real animations
def _aim_partial(n, d, w):
    p = RIG.pose.bones[n]; bpy.context.view_layer.update()
    cur = p.matrix.copy(); y = cur.to_3x3().col[1].normalized(); tgt = y.lerp(Vector(d).normalized(), w).normalized()
    q = y.rotation_difference(tgt); m = (q.to_matrix() @ cur.to_3x3()).to_4x4(); m.translation = cur.translation; p.matrix = m
    bpy.context.view_layer.update()

GYM = {   # character-left directions (right mirrored): upper arm, forearm
    'flex':    ((1.0, 0.12, 0.08), (0.08, 0.15, 1.0)),
    'victory': ((0.5, 0.05, 1.0), (0.22, 0.0, 1.0)),
}
def gym(name, w, pump=0.0):
    """Blend the current (animated) pose toward a gym pose by weight w; pump adds a squeeze."""
    if w <= 0: return
    up, lo = GYM[name]
    if name == 'flex': lo = (lo[0] + 0.25 * pump, lo[1], lo[2])
    if name == 'victory': up = (up[0] - 0.15 * pump, up[1], up[2]); lo = (lo[0] - 0.2 * pump, lo[1] - 0.1 * pump, lo[2])
    for side, sg in (('l', 1), ('r', -1)):
        _aim_partial('upperarm_' + side, (sg * up[0], up[1], up[2]), w)
        _aim_partial('lowerarm_' + side, (sg * lo[0], lo[1], lo[2]), w)

def freeze():
    """Bake the current animated frame into the bones and detach the action, so manual posing sticks."""
    bpy.context.view_layer.update()
    basis = {b.name: b.matrix_basis.copy() for b in RIG.pose.bones}
    RIG.animation_data.action = None
    for b in RIG.pose.bones: b.matrix_basis = basis[b.name]
    bpy.context.view_layer.update()

def _ease(t): return t * t * (3 - 2 * t)
def clip_frame(clip, u):
    """Pose the rig for clip at phase u in [0,1). Returns nothing; idle-based clips loop back to idle."""
    import math as _m
    if clip == 'idle': act('Idle_Loop', t=u); return
    if clip == 'punch':
        if u < 0.5: act('Punch_Jab', t=u * 2)
        else: act('Punch_Cross', t=(u - 0.5) * 2)
        return
    act('Idle_Loop', t=u); freeze()
    k = _ease(min(1, u / 0.28)) * (1 - _ease(max(0, (u - 0.78) / 0.22)))
    pump = (0.5 + 0.5 * _m.sin((u - 0.28) / 0.5 * 4 * _m.pi)) if 0.28 < u < 0.78 else 0.0
    gym(clip, k, pump * k)
