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
    sc.render.resolution_x, sc.render.resolution_y = res; sc.view_settings.view_transform = 'AgX'
    try: sc.view_settings.look = 'AgX - Medium High Contrast'
    except Exception: pass
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
    area('rimL', (-1.3, 1.2, 1.4), 330 * k, 0.6 * hgt, (0.72, 1.0, 0.3))
    area('rimR', (1.3, 1.1, 1.3), 260 * k, 0.6 * hgt, (1.0, 0.25, 0.32))
    bpy.ops.mesh.primitive_plane_add(size=40 * hgt); fl = bpy.context.object
    fm = bpy.data.materials.new('floor'); fm.use_nodes = True; fm.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = srgb('#0B0C0B')
    fm.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value = 0.5; fl.data.materials.append(fm)
    cd = bpy.data.cameras.new('cam'); cd.lens = 60; cam = bpy.data.objects.new('cam', cd); sc.collection.objects.link(cam); sc.camera = cam
    t = bpy.data.objects.new('t', None); t.location = (0, 0, hgt * 0.52); sc.collection.objects.link(t)
    tc = cam.constraints.new('TRACK_TO'); tc.target = t; tc.track_axis = 'TRACK_NEGATIVE_Z'; tc.up_axis = 'UP_Y'
    S['hgt'] = hgt
    return hgt

def render(path, yaw=-20, dist=2.75, z=0.62):
    h = S['hgt']; a = math.radians(-90 + yaw); S.camera.location = (dist * h * math.cos(a), dist * h * math.sin(a), z * h)
    S.render.filepath = path; bpy.ops.render.render(write_still=True)
