"""
R22 Mixamo mocap -> the canonical coach skeleton.

The raw Mixamo FBX files are NOT stored in the repo (Mixamo's terms allow using the motions in a project, not
redistributing the files). Instead this script runs locally on the owner's download and saves the moves already
retargeted onto each coach as a small .blend of actions (mocap/<who>.blend on branch coach3d-assets). clips.build()
loads them by name, so CI renders never need the FBX files.

  python mocap.py <glb_dir> <fbx_dir> <out_dir> [who ...]
"""
import sys, os, bpy
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mathutils import Quaternion
import rig, anim

# clip name -> (Mixamo file stem, loop, max seconds or None, options)
MOCAP = {
    # standing loops
    'idle':      ('weight shift', True, 8.0, {}),      # R26: natural weight-shift idle (Happy Idle bounced)
    'idle2':     ('Happy Idle', True, None, {}),
    'talk':      ('Talking', True, None, {}),
    'talk2':     ('Talking (1)', True, None, {}),
    'talk3':     ('Talking (2)', True, None, {}),
    'think':     ('Thinking', True, None, {}),
    'tablet':    ('Texting While Standing', True, 6.0, {'start': 2.0}),
    'dance':     ('Hip Hop Dancing', True, 7.0, {'travel': 0.15}),
    'dance2':    ('Silly Dancing', True, 7.0, {'travel': 0.15}),
    'dance3':    ('Dancing Running Man', True, 7.0, {'travel': 0.15}),
    # one-shot reactions
    'wave':      ('Waving', False, None, {}),
    'thumbsup':  ('Standing Thumbs Up', False, None, {}),
    'cheer':     ('Cheering', False, None, {'level': False}),
    'fistpump':  ('Fist Pump', False, None, {}),
    'victory':   ('Victory', False, None, {'level': False}),
    'jump':      ('Jumping', False, None, {'level': False}),
    'clap':      ('Clapping', False, None, {}),
    'rally':     ('Rallying', False, None, {}),
    'happy':     ('happy hand gesture', False, None, {}),
    'nod':       ('head nod yes', False, None, {}),
    'acknowledge': ('acknowledging', False, None, {}),
    'headshake': ('shaking head no', False, None, {}),
    'sigh':      ('relieved sigh', False, None, {}),
    'cocky':     ('being cocky', False, None, {}),
    'bottle':    ('Drinking', False, None, {}),
    # exercises
    'squat':     ('Air Squat', True, None, {}),
    'stretch':   ('Arm Stretching', True, 9.0, {}),
    'plank':     ('Plank', True, 6.0, {'floor': True}),
}
# left out on purpose: angry gesture, annoyed head shake, dismissing gesture, sarcastic head nod, look away (off-tone for
# a coach); Start/End Plank, Air Squat Bent Arms, extra dances/talks (duplicates).

SRC_FPS, OUT_FPS = 30.0, 24.0
OWN_REST = ('Hips', 'Spine', 'Chest', 'UpperChest', 'Neck', 'Head')
LOOP_BLEND = 10          # frames blended back into frame 0 so loops don't pop


def _import_fbx(path):
    before_o = set(bpy.data.objects); before_a = set(bpy.data.actions)
    bpy.ops.import_scene.fbx(filepath=path, automatic_bone_orientation=False, ignore_leaf_bones=False)
    arm = next(o for o in bpy.data.objects if o not in before_o and o.type == 'ARMATURE')
    for o in [o for o in bpy.data.objects if o not in before_o and o.type != 'ARMATURE']:
        bpy.data.objects.remove(o, do_unlink=True)
    act = next(a for a in bpy.data.actions if a not in before_a)
    return arm, act


def _loop_blend(act, n, k=LOOP_BLEND):
    """Blend the last k frames of every channel toward frame 0 so the loop closes smoothly."""
    by = {}
    for fc in act.fcurves:
        by.setdefault((fc.data_path, fc.array_index), fc)
    paths = {p for p, _ in by}
    for p in paths:
        if p.endswith('rotation_quaternion'):
            fcs = [by.get((p, i)) for i in range(4)]
            if None in fcs: continue
            q0 = Quaternion([fc.evaluate(0) for fc in fcs])
            for j in range(k + 1):
                f = n - k + j; w = j / k; w = w * w * (3 - 2 * w)
                q = Quaternion([fc.evaluate(f) for fc in fcs])
                if q.dot(q0) < 0: q.negate()
                r = q.slerp(q0, w)
                for i, fc in enumerate(fcs): fc.keyframe_points.insert(f, r[i], options={'REPLACE', 'FAST'})
        elif p.endswith('location'):
            for i in range(3):
                fc = by.get((p, i))
                if fc is None: continue
                v0 = fc.evaluate(0)
                for j in range(k + 1):
                    f = n - k + j; w = j / k; w = w * w * (3 - 2 * w)
                    fc.keyframe_points.insert(f, fc.evaluate(f) * (1 - w) + v0 * w, options={'REPLACE', 'FAST'})
    for fc in act.fcurves: fc.update()


def _head_pitch(arm):
    """Head rotation away from rest, + = chin up (characters face -Y)."""
    import math
    from mathutils import Vector
    m = (arm.matrix_world @ arm.pose.bones['Head'].matrix).to_3x3(); r = (arm.matrix_world @ arm.data.bones['Head'].matrix_local).to_3x3()
    f = (m @ r.inverted() @ Vector((0, -1, 0))).normalized()
    return math.degrees(math.atan2(f.z, -f.y))


def _level_head(arm, act, n, keep=4.0):
    """Mocap idles/claps look up ~20 deg (fine on Y Bot, odd for a coach facing you): pitch the neck+head down by the
    clip's mean excess so the face looks at the viewer; the motion on top is untouched."""
    sc = bpy.context.scene
    arm.animation_data.action = act
    ps = []
    for f in range(0, n + 1, 3):
        sc.frame_set(f); ps.append(_head_pitch(arm))
    corr = sum(ps) / len(ps) - keep
    if corr <= 0: return 0.0
    for bone, w in (('Neck', 0.35), ('Head', 0.65)):
        q = anim.to_local(arm, bone, corr * w, 0, 0)
        fcs = [fc for fc in act.fcurves if fc.data_path == f'pose.bones["{bone}"].rotation_quaternion']
        fcs.sort(key=lambda fc: fc.array_index)
        if len(fcs) != 4: continue
        frames = sorted({int(k.co[0]) for k in fcs[0].keyframe_points})
        for f in frames:
            cur = Quaternion([fc.evaluate(f) for fc in fcs])
            new = (q @ cur).normalized()
            for i, fc in enumerate(fcs): fc.keyframe_points.insert(f, new[i], options={'REPLACE', 'FAST'})
        for fc in fcs: fc.update()
    return corr


def bake(who, glb_dir, fbx_dir, out_dir):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    arm, body = rig.load(who, glb_dir)
    made = []
    for name, (stem, loop, max_s, opt) in MOCAP.items():
        path = os.path.join(fbx_dir, stem + '.fbx')
        if not os.path.exists(path): print('MISSING', stem); continue
        src, sact = _import_fbx(path)
        f0, f1 = sact.frame_range
        if opt.get('start'):                      # skip a wind-up (e.g. raising the phone) so loops stay in the pose
            sact.use_frame_range = True; sact.frame_start = f0 + opt['start'] * SRC_FPS; sact.frame_end = f1
            f0, f1 = sact.frame_range
        dur = (f1 - f0) / SRC_FPS
        if max_s: dur = min(dur, max_s)
        step = SRC_FPS / OUT_FPS
        n = int(round(dur * OUT_FPS))
        act, n = anim.retarget(src, rig.MIXAMO, sact, arm, 'mx_' + name, frames=n, step=step, travel=opt.get('travel', 1.0), own_rest=OWN_REST)
        lv = _level_head(arm, act, n) if opt.get('level', True) else 0.0
        if loop: _loop_blend(act, n)
        act['frames'] = n; act['loop'] = int(loop); act['floor'] = int(opt.get('floor', False))
        act.use_fake_user = True
        arm.animation_data.action = None
        bpy.data.objects.remove(src, do_unlink=True); bpy.data.actions.remove(sact)
        made.append((name, n, loop)); print('baked', who, name, n, 'head %+.0f' % -lv if lv else '')
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, who + '.blend')
    bpy.data.libraries.write(out, {a for a in bpy.data.actions if a.name.startswith('mx_')}, fake_user=True, compress=True)
    print('WROTE', out, os.path.getsize(out), len(made))
    return made


def load(blend, name):
    """The baked action for clip [name] (or None)."""
    key = 'mx_' + name
    if key in bpy.data.actions: return bpy.data.actions[key]
    if not os.path.exists(blend): return None
    with bpy.data.libraries.load(blend, link=False) as (src, dst):
        if key not in src.actions: return None
        dst.actions = [key]
    return bpy.data.actions.get(key)


if __name__ == '__main__':
    glb, fbx, out = sys.argv[1:4]
    for w in (sys.argv[4:] or ['trainer_m', 'trainer_f', 'nutri_m', 'nutri_f']):
        bake(w, glb, fbx, out)
