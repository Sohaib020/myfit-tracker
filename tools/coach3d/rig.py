"""
R21 coach characters — load a Meshy model and rebind it to ONE shared skeleton ("canonical"), so every move is
authored once and plays on all four coaches.

Canonical bones (A-pose rest, character faces -Y, Z up, metres):
  Hips Spine Chest UpperChest Neck Head | Shoulder/UpperArm/ForeArm/Hand/Thumb1-3/Fingers1-3 .L/.R | UpLeg/Leg/Foot/Toe .L/.R
Fingers are one chain (index..pinky curl together) + a separate thumb, which every gesture we need can do.

Usage (bpy 4.2 module):  import rig; arm, body = rig.load('trainer_m', ASSETS)
"""
import bpy, mathutils, os
from mathutils import Vector, Matrix

SIDES = ('L', 'R')
CHAIN = ['Hips', 'Spine', 'Chest', 'UpperChest', 'Neck', 'Head']
ARM = ['Shoulder', 'UpperArm', 'ForeArm', 'Hand']
LEG = ['UpLeg', 'Leg', 'Foot', 'Toe']

# source joint names → canonical (mixamo = Meshy default rig, rigify = male trainer's rig)
MIXAMO = {'Hips': 'mixamorig:Hips', 'Spine': 'mixamorig:Spine', 'Chest': 'mixamorig:Spine1', 'UpperChest': 'mixamorig:Spine2',
          'Neck': 'mixamorig:Neck', 'Head': 'mixamorig:Head', 'HeadTop': 'mixamorig:HeadTop_End'}
for s, S in (('L', 'Left'), ('R', 'Right')):
    MIXAMO.update({f'Shoulder.{s}': f'mixamorig:{S}Shoulder', f'UpperArm.{s}': f'mixamorig:{S}Arm', f'ForeArm.{s}': f'mixamorig:{S}ForeArm',
                   f'Hand.{s}': f'mixamorig:{S}Hand', f'UpLeg.{s}': f'mixamorig:{S}UpLeg', f'Leg.{s}': f'mixamorig:{S}Leg',
                   f'Foot.{s}': f'mixamorig:{S}Foot', f'Toe.{s}': f'mixamorig:{S}ToeBase', f'ToeEnd.{s}': f'mixamorig:{S}Toe_End',
                   f'Fingers1.{s}': f'mixamorig:{S}HandMiddle1', f'Fingers2.{s}': f'mixamorig:{S}HandMiddle2', f'Fingers3.{s}': f'mixamorig:{S}HandMiddle3',
                   f'FingersEnd.{s}': f'mixamorig:{S}HandMiddle4', f'Thumb1.{s}': f'mixamorig:{S}HandThumb1', f'Thumb2.{s}': f'mixamorig:{S}HandThumb2',
                   f'Thumb3.{s}': f'mixamorig:{S}HandThumb3', f'ThumbEnd.{s}': f'mixamorig:{S}HandThumb4'})
RIGIFY = {'Hips': 'spine', 'Spine': 'spine.001', 'Chest': 'spine.002', 'UpperChest': 'spine.003', 'Neck': 'spine.004', 'Head': 'spine.005',
          'HeadTop': 'mixamorig:HeadTop_End'}
for s in SIDES:
    RIGIFY.update({f'Shoulder.{s}': f'shoulder.{s}', f'UpperArm.{s}': f'upper_arm.{s}', f'ForeArm.{s}': f'forearm.{s}', f'Hand.{s}': f'hand.{s}',
                   f'UpLeg.{s}': f'thigh.{s}', f'Leg.{s}': f'shin.{s}', f'Foot.{s}': f'foot.{s}', f'Toe.{s}': f'toe.{s}',
                   f'ToeEnd.{s}': 'mixamorig:%sToe_End' % ('Left' if s == 'L' else 'Right'),
                   f'Fingers1.{s}': f'f_middle.01.{s}', f'Fingers2.{s}': f'f_middle.02.{s}', f'Fingers3.{s}': f'f_middle.03.{s}',
                   f'FingersEnd.{s}': f'f_middle.03.{s}_end', f'Thumb1.{s}': f'thumb.01.{s}', f'Thumb2.{s}': f'thumb.02.{s}',
                   f'Thumb3.{s}': f'thumb.03.{s}', f'ThumbEnd.{s}': f'thumb.03.{s}_end'})


def group_map(name):
    """Source vertex group → canonical bone that should own its weights (None = drop)."""
    n = name
    if n.startswith('mixamorig:'):
        n = n[10:]
        side = 'L' if n.startswith('Left') else 'R' if n.startswith('Right') else None
        base = n[4:] if side == 'L' else n[5:] if side == 'R' else n
        direct = {'Hips': 'Hips', 'Spine': 'Spine', 'Spine1': 'Chest', 'Spine2': 'UpperChest', 'Neck': 'Neck', 'Head': 'Head', 'HeadTop_End': 'Head'}
        if side is None: return direct.get(base)
        m = {'Shoulder': 'Shoulder', 'Arm': 'UpperArm', 'ForeArm': 'ForeArm', 'Hand': 'Hand', 'UpLeg': 'UpLeg', 'Leg': 'Leg', 'Foot': 'Foot',
             'ToeBase': 'Toe', 'Toe_End': 'Toe', 'HandMiddle1': 'Fingers1', 'HandMiddle2': 'Fingers2', 'HandMiddle3': 'Fingers3', 'HandMiddle4': 'Fingers3',
             'HandThumb1': 'Thumb1', 'HandThumb2': 'Thumb2', 'HandThumb3': 'Thumb3', 'HandThumb4': 'Thumb3'}.get(base)
        return f'{m}.{side}' if m else None
    if n == 'headfront': return 'Head'
    if n in ('Bone_035', 'Bone_045'): return 'Fingers3.L'
    if n in ('Bone_036', 'Bone_046'): return 'Fingers3.R'
    rig = {'spine': 'Hips', 'spine.001': 'Spine', 'spine.002': 'Chest', 'spine.003': 'UpperChest', 'spine.004': 'Neck', 'spine.005': 'Head'}
    if n in rig: return rig[n]
    for s in SIDES:
        for a, b in (('shoulder', 'Shoulder'), ('upper_arm', 'UpperArm'), ('forearm', 'ForeArm'), ('hand', 'Hand'), ('thigh', 'UpLeg'),
                     ('shin', 'Leg'), ('foot', 'Foot'), ('toe', 'Toe')):
            if n == f'{a}.{s}': return f'{b}.{s}'
        if n.startswith('f_') and n.endswith(f'.{s}') or n.startswith('f_') and n.endswith(f'.{s}_end'):
            seg = n.split('.')[1]
            return f'Fingers{min(int(seg), 3)}.{s}'
        if n.startswith('thumb.') and (n.endswith(f'.{s}') or n.endswith(f'.{s}_end')):
            return f'Thumb{min(int(n.split(".")[1]), 3)}.{s}'
    if 'Toe_End' in n: return 'Toe.' + ('L' if 'Left' in n else 'R')
    return None


def _clear():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def _import(path):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    new = [o for o in bpy.data.objects if o not in before]
    arm = next((o for o in new if o.type == 'ARMATURE'), None)
    mesh = max((o for o in new if o.type == 'MESH'), key=lambda o: len(o.data.vertices))   # GLBs also carry a tiny helper sphere
    return arm, mesh, new


def _joints(arm, names):
    """World positions of every canonical joint from a source armature (rest pose)."""
    J = {}
    bones = arm.data.bones
    mw = arm.matrix_world
    for k, src in names.items():
        b = bones.get(src)
        if b is not None: J[k] = mw @ b.head_local
    # tails for chain ends
    def tail(src): b = bones.get(src); return (mw @ b.tail_local) if b else None
    if 'HeadTop' not in J: J['HeadTop'] = tail(names['Head'])
    for s in SIDES:
        if f'ToeEnd.{s}' not in J: J[f'ToeEnd.{s}'] = tail(names[f'Toe.{s}'])
        if f'FingersEnd.{s}' not in J and names[f'Fingers3.{s}'] in bones: J[f'FingersEnd.{s}'] = tail(names[f'Fingers3.{s}'])
    return J


def _hand_frame(J, s):
    """Orthonormal frame of the hand: x = along the hand (wrist→knuckles), y = palm normal-ish, z = thumb side."""
    a = (J[f'Fingers1.{s}'] - J[f'Hand.{s}']).normalized()
    fwd = Vector((0, -1, 0))
    z = (fwd - a * a.dot(fwd)).normalized()
    y = z.cross(a)
    return Matrix((a, y, z)).transposed()


def _synth_thumb(J, s, ref=None):
    """Thumb joints for rigs without a thumb: placed from a reference hand (male nutritionist) in hand space."""
    hand = J[f'Hand.{s}']; L = (J[f'Fingers1.{s}'] - hand).length
    F = _hand_frame(J, s)
    sgn = 1 if s == 'L' else -1
    # (along hand, palm normal, toward front) in hand lengths — measured on a Meshy thumb rig
    local = [Vector((0.42, 0.0, 0.40)), Vector((0.75, 0.0, 0.62)), Vector((1.05, 0.0, 0.78)), Vector((1.32, 0.0, 0.90))]
    pts = [hand + F @ (v * L) for v in local]
    J[f'Thumb1.{s}'], J[f'Thumb2.{s}'], J[f'Thumb3.{s}'], J[f'ThumbEnd.{s}'] = pts


def build_armature(J, name='coach'):
    """Creates the canonical armature from joint positions. Bone rolls come from a stable side vector so local axes
    match across characters (animation is authored with world-axis helpers in anim.py anyway)."""
    ad = bpy.data.armatures.new(name)
    arm = bpy.data.objects.new(name, ad)
    bpy.context.scene.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    bpy.ops.object.mode_set(mode='EDIT')
    eb = ad.edit_bones

    def bone(n, h, t, parent=None, roll_axis=Vector((1, 0, 0)), connect=False):
        b = eb.new(n); b.head = h; b.tail = t
        if (b.tail - b.head).length < 1e-4: b.tail = b.head + Vector((0, 0, 0.02))
        b.align_roll(roll_axis)
        if parent: b.parent = eb[parent]; b.use_connect = connect
        return b

    seq = CHAIN + ['HeadTop']
    for i, n in enumerate(CHAIN):
        bone(n, J[n], J[seq[i + 1]], CHAIN[i - 1] if i else None, Vector((0, -1, 0)), connect=i > 1 and n != 'Neck')
    for s in SIDES:
        sv = Vector((0, -1, 0))
        bone(f'Shoulder.{s}', J[f'Shoulder.{s}'], J[f'UpperArm.{s}'], 'UpperChest', sv)
        bone(f'UpperArm.{s}', J[f'UpperArm.{s}'], J[f'ForeArm.{s}'], f'Shoulder.{s}', sv, True)
        bone(f'ForeArm.{s}', J[f'ForeArm.{s}'], J[f'Hand.{s}'], f'UpperArm.{s}', sv, True)
        bone(f'Hand.{s}', J[f'Hand.{s}'], J[f'Fingers1.{s}'], f'ForeArm.{s}', sv, True)
        bone(f'Fingers1.{s}', J[f'Fingers1.{s}'], J[f'Fingers2.{s}'], f'Hand.{s}', sv, True)
        bone(f'Fingers2.{s}', J[f'Fingers2.{s}'], J[f'Fingers3.{s}'], f'Fingers1.{s}', sv, True)
        bone(f'Fingers3.{s}', J[f'Fingers3.{s}'], J[f'FingersEnd.{s}'], f'Fingers2.{s}', sv, True)
        bone(f'Thumb1.{s}', J[f'Thumb1.{s}'], J[f'Thumb2.{s}'], f'Hand.{s}', sv)
        bone(f'Thumb2.{s}', J[f'Thumb2.{s}'], J[f'Thumb3.{s}'], f'Thumb1.{s}', sv, True)
        bone(f'Thumb3.{s}', J[f'Thumb3.{s}'], J[f'ThumbEnd.{s}'], f'Thumb2.{s}', sv, True)
        lv = Vector((1, 0, 0))
        bone(f'UpLeg.{s}', J[f'UpLeg.{s}'], J[f'Leg.{s}'], 'Hips', lv)
        bone(f'Leg.{s}', J[f'Leg.{s}'], J[f'Foot.{s}'], f'UpLeg.{s}', lv, True)
        bone(f'Foot.{s}', J[f'Foot.{s}'], J[f'Toe.{s}'], f'Leg.{s}', lv, True)
        bone(f'Toe.{s}', J[f'Toe.{s}'], J[f'ToeEnd.{s}'], f'Foot.{s}', lv, True)
    bpy.ops.object.mode_set(mode='OBJECT')
    return arm


def rebind(mesh, arm):
    """Merges the source vertex groups into canonical groups and parents the mesh to the new armature."""
    old = {g.index: g.name for g in mesh.vertex_groups}
    acc = {}
    for v in mesh.data.vertices:
        for g in v.groups:
            tgt = group_map(old[g.group])
            if tgt and g.weight > 0: acc.setdefault(tgt, {}).setdefault(v.index, 0.0); acc[tgt][v.index] += g.weight
    for g in list(mesh.vertex_groups): mesh.vertex_groups.remove(g)
    for tgt, ws in acc.items():
        vg = mesh.vertex_groups.new(name=tgt)
        for vi, w in ws.items(): vg.add([vi], min(1.0, w), 'REPLACE')
    for m in list(mesh.modifiers):
        if m.type == 'ARMATURE': mesh.modifiers.remove(m)
    mw = mesh.matrix_world.copy()
    mesh.parent = arm; mesh.matrix_world = mw
    mod = mesh.modifiers.new('Armature', 'ARMATURE'); mod.object = arm
    return mesh


def split_thumb(mesh, J, s):
    """For hands rigged without a thumb: vertices on the thumb side of the hand move from Hand/Fingers to Thumb1-3."""
    F = _hand_frame(J, s).inverted()
    hand = J[f'Hand.{s}']; L = (J[f'Fingers1.{s}'] - hand).length
    idx = {g.name: g for g in mesh.vertex_groups}
    tg = [idx.get(f'Thumb{i}.{s}') or mesh.vertex_groups.new(name=f'Thumb{i}.{s}') for i in (1, 2, 3)]
    src = [idx[n] for n in (f'Hand.{s}', f'Fingers1.{s}', f'Fingers2.{s}', f'Fingers3.{s}') if n in idx]
    mw = mesh.matrix_world
    for v in mesh.data.vertices:
        ws = {g.group: g.weight for g in v.groups}
        hw = sum(ws.get(g.index, 0) for g in src)
        if hw < 0.5: continue
        p = F @ ((mw @ v.co) - hand) / L          # hand space in hand lengths
        # the thumb sits toward the front (+z) and starts ~0.3 hand lengths from the wrist
        if p.z > 0.28 and 0.25 < p.x < 1.45 and abs(p.y) < 0.45:
            seg = 0 if p.x < 0.65 else 1 if p.x < 1.0 else 2
            for g in src: g.remove([v.index])
            tg[seg].add([v.index], hw, 'REPLACE')


CHARS = {
    'trainer_m': ('trainer_m_rig.glb', RIGIFY),
    'trainer_f': ('trainer_f_rig.glb', MIXAMO),
    'nutri_m': ('nutri_m_rig.glb', MIXAMO),
    'nutri_f': ('nutri_f.glb', None),          # no rig: weights transferred from trainer_f
}


def load(who, assets):
    """Returns (armature, mesh) for a coach on the canonical skeleton, in rest pose, feet on z=0, facing -Y."""
    _clear()
    file, names = CHARS[who]
    if names is None:
        return _load_unrigged(who, assets)
    src_arm, mesh, objs = _import(os.path.join(assets, file))
    J = _joints(src_arm, names)
    for s in SIDES:
        if f'Thumb1.{s}' not in J: _synth_thumb(J, s)
    arm = build_armature(J, who)
    rebind(mesh, arm)
    for s in SIDES:
        if not any(g.name == f'Thumb1.{s}' for g in mesh.vertex_groups) or sum(1 for v in mesh.data.vertices for g in v.groups
                                                                              if mesh.vertex_groups[g.group].name == f'Thumb1.{s}') < 20:
            split_thumb(mesh, J, s)
    for o in objs:
        if o not in (mesh,): bpy.data.objects.remove(o, do_unlink=True)
    for a in list(bpy.data.actions):
        a.use_fake_user = True                    # keep Meshy mocap clips for retargeting
    mesh.name = who + '_body'
    arm['joints'] = {k: list(v) for k, v in J.items()}
    return normalize(arm, mesh)


def normalize(arm, mesh):
    """Feet on z=0, body centred on x=y=0 (moves the armature object; joints are stored in the new world space)."""
    bpy.context.view_layer.update()
    mw = mesh.matrix_world
    pts = [mw @ v.co for i, v in enumerate(mesh.data.vertices) if i % 7 == 0]
    off = Vector((-(min(p.x for p in pts) + max(p.x for p in pts)) / 2, -(min(p.y for p in pts) + max(p.y for p in pts)) / 2, -min(p.z for p in pts)))
    arm.location = arm.location + off
    arm['joints'] = {k: list(Vector(v) + off) for k, v in arm['joints'].items()}
    arm['offset'] = list(off)
    bpy.context.view_layer.update()
    return arm, mesh


def _load_unrigged(who, assets):
    """Female nutritionist: fit the female trainer's skeleton to her height, then copy skin weights by proximity."""
    ref_arm, ref_mesh = load('trainer_f', assets)
    J = {k: Vector(v) for k, v in ref_arm['joints'].items()}
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=os.path.join(assets, CHARS[who][0]))
    mesh = max((o for o in bpy.data.objects if o not in before and o.type == 'MESH'), key=lambda o: len(o.data.vertices))
    for o in [o for o in bpy.data.objects if o not in before and o is not mesh]: bpy.data.objects.remove(o, do_unlink=True)
    bpy.context.view_layer.update()
    def zr(o):
        zs = [(o.matrix_world @ v.co).z for v in o.data.vertices]; return min(zs), max(zs)
    a0, a1 = zr(ref_mesh); b0, b1 = zr(mesh)
    k = (b1 - b0) / (a1 - a0)
    J2 = {n: Vector((p.x * k, p.y * k, (p.z - a0) * k + b0)) for n, p in J.items()}
    arm = build_armature(J2, who)
    # temporarily scale the reference to her height so nearest-surface weights line up
    ref_mesh.parent = None
    ref_mesh.scale = (k, k, k); ref_mesh.location.z = b0 - a0 * k
    bpy.context.view_layer.update()
    for g in ref_mesh.vertex_groups: mesh.vertex_groups.new(name=g.name)
    dt = mesh.modifiers.new('wt', 'DATA_TRANSFER')
    dt.object = ref_mesh; dt.use_vert_data = True; dt.data_types_verts = {'VGROUP_WEIGHTS'}
    dt.vert_mapping = 'POLYINTERP_NEAREST'; dt.layers_vgroup_select_src = 'ALL'; dt.layers_vgroup_select_dst = 'NAME'
    bpy.context.view_layer.objects.active = mesh
    bpy.ops.object.modifier_apply(modifier=dt.name)
    _fix_long_hair(mesh, J2)
    for o in (ref_mesh, ref_arm): bpy.data.objects.remove(o, do_unlink=True)
    mw = mesh.matrix_world.copy(); mesh.parent = arm; mesh.matrix_world = mw
    mod = mesh.modifiers.new('Armature', 'ARMATURE'); mod.object = arm
    mesh.name = who + '_body'
    arm['joints'] = {k: list(v) for k, v in J2.items()}
    return normalize(arm, mesh)


def _fix_long_hair(mesh, J):
    """Her long hair falls past the shoulders: anything above the armpits and behind/beside the neck follows the head
    and neck, not the shoulders/arms it happens to be nearest to."""
    idx = {g.name: g for g in mesh.vertex_groups}
    head, neck = idx['Head'], idx['Neck']
    zc = J['UpperChest'].z - 0.03
    arm_groups = [idx[n] for n in idx if n.split('.')[0] in ('Shoulder', 'UpperArm', 'ForeArm', 'Hand', 'Chest', 'UpperChest', 'Spine')]
    hx = (J['Shoulder.L'].x - J['Shoulder.R'].x) / 2 * 0.95
    mw = mesh.matrix_world
    for v in mesh.data.vertices:
        p = mw @ v.co
        if p.z < zc - 0.10 or abs(p.x) > hx + 0.06: continue
        # hair is behind or at the sides of the neck column; the face/collar are in front of it
        behind = p.y > J['Neck'].y + 0.02
        side = abs(p.x) > 0.07 and p.z > zc
        if not (behind or side): continue
        if p.z < zc and not behind: continue
        for g in arm_groups: g.remove([v.index])
        t = max(0.0, min(1.0, (p.z - (zc - 0.10)) / 0.25))
        head.add([v.index], 0.3 + 0.7 * t, 'REPLACE'); neck.add([v.index], 0.7 - 0.7 * t + 0.01, 'REPLACE')
