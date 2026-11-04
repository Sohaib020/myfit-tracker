"""
Builds every coach clip on a loaded character: hand-keyed moves (moves.py) + contacts/props, and mocap
(Quaternius UAL idle/talk, the coaches' own Meshy dances). Each builder leaves the scene ready to render frames
0..clip.frames-1 and returns the clip.
"""
import bpy, math, os
from mathutils import Vector, Matrix, Quaternion, Euler
import anim, moves, props, clips_src as cs, rig
from anim import Clip, both, side, merge

TRAINER = ['idle', 'wave', 'talk', 'thumbsup', 'cheer', 'squat', 'pushup', 'lunge', 'press', 'plank', 'stretch', 'dance', 'victory', 'fistpump',
           'clap', 'rally', 'nod', 'jump']
NUTRI = ['idle', 'wave', 'talk', 'thumbsup', 'cheer', 'plate', 'bottle', 'tablet', 'think', 'dance', 'victory', 'fistpump',
         'clap', 'nod', 'happy']
# R22: these come from the owner's Mixamo mocap (tools/coach3d/mocap.py → <assets>/../mocap/<who>.blend); the rest stay
# hand-keyed (press, pushup, lunge, plank, plate — no mocap for them, plank/push-up need exact floor contact)
MOCAP_CLIPS = {'idle', 'idle2', 'talk', 'talk2', 'talk3', 'think', 'tablet', 'dance', 'dance2', 'dance3', 'wave', 'thumbsup', 'cheer',
               'fistpump', 'victory', 'jump', 'clap', 'rally', 'happy', 'nod', 'acknowledge', 'headshake', 'sigh', 'cocky', 'bottle',
               'squat', 'stretch'}
# props held during a mocap move: (prop, hand bone, fraction of the clip where the grip is placed)
MOCAP_PROPS = {'bottle': [('bottle', 'Hand.L', 0.05)], 'tablet': [('phone', 'Hand.L', 0.3)]}


class MocapClip:
    def __init__(self, name, act):
        self.name, self.frames, self.loop, self.fps = name, int(act['frames']), bool(act['loop']), 24
        self.keys, self.props = [], []


def mocap_blend(who, assets):
    return os.path.join(os.path.dirname(os.path.abspath(assets)), 'mocap', who + '.blend')
CLIPS = {'trainer_m': TRAINER, 'trainer_f': TRAINER, 'nutri_m': NUTRI, 'nutri_f': NUTRI}
# whose Meshy dance each coach performs (the female nutritionist borrows one)
DANCE = {'trainer_m': ('trainer_m_rig.glb', 'All_Night_Dance', rig.RIGIFY), 'trainer_f': ('trainer_f_rig.glb', 'All_Night_Dance', rig.MIXAMO),
         'nutri_m': ('nutri_m_rig.glb', 'Boom_Dance', rig.MIXAMO), 'nutri_f': ('nutri_m_rig.glb', 'You_Groove', rig.MIXAMO)}


def J(arm, k):
    return Vector(arm['joints'][k])


def _clear_contacts(arm):
    for pb in arm.pose.bones:
        for c in list(pb.constraints): pb.constraints.remove(c)
    for o in list(bpy.data.objects):
        if o.type == 'EMPTY' or o.get('prop'): bpy.data.objects.remove(o, do_unlink=True)


def _empty(name, loc, rot=None):
    e = bpy.data.objects.new(name, None); bpy.context.scene.collection.objects.link(e); e.location = loc
    if rot is not None: e.rotation_mode = 'QUATERNION'; e.rotation_quaternion = rot
    return e


def _ik(arm, chain_bone, target, pole=None, pole_angle=-90, count=2):
    c = arm.pose.bones[chain_bone].constraints.new('IK')
    c.target = target; c.chain_count = count; c.use_tail = True
    if pole is not None: c.pole_target = pole; c.pole_angle = math.radians(pole_angle)
    return c


def _rest_world_rot(arm, bone):
    return (arm.matrix_world.to_quaternion() @ arm.data.bones[bone].matrix_local.to_quaternion()).normalized()


def plant_feet(arm, sides=('L', 'R'), flat=True):
    """Feet stay exactly where they stand in rest pose (knees point forward)."""
    out = {}
    for s in sides:
        ank = arm.matrix_world @ arm.data.bones[f'Foot.{s}'].head_local
        knee = arm.matrix_world @ arm.data.bones[f'Leg.{s}'].head_local
        t = _empty(f'foot_{s}', ank, _rest_world_rot(arm, f'Foot.{s}'))
        p = _empty(f'knee_{s}', knee + Vector((0, -0.6, 0)))
        _ik(arm, f'Leg.{s}', t, p, pole_angle=-90)
        if flat:
            cr = arm.pose.bones[f'Foot.{s}'].constraints.new('COPY_ROTATION'); cr.target = t
        out[s] = t
    return out


def build(who, name, arm, assets, ual_path):
    _clear_contacts(arm)
    if arm.animation_data: arm.animation_data.action = None
    for pb in arm.pose.bones: pb.location = (0, 0, 0); pb.rotation_mode = 'QUATERNION'; pb.rotation_quaternion = Quaternion()
    if name in MOCAP_CLIPS:
        import mocap
        act = mocap.load(mocap_blend(who, assets), name)
        if act is not None:
            arm.animation_data_create(); arm.animation_data.action = act
            c = MocapClip(name, act); _scene_range(c)
            for pname, bone, at in MOCAP_PROPS.get(name, []):
                props.attach(pname, arm, bone, frame=int(c.frames * at))
            return c
    if name == 'dance':
        f, a, m = DANCE[who]
        src, acts = cs.import_source(os.path.join(assets, f))
        act, n = anim.retarget(src, m, acts[a], arm, name, step=1.0, travel=0.15)
        cs.remove(src)
        c = Clip(name, min(n, 168), loop=True); _scene_range(c); return c
    fn = {'thumbsup': moves.thumbs_up, 'fistpump': moves.fist_pump, 'press': moves.shoulder_press, 'plate': moves.hold_plate,
          'bottle': moves.hold_bottle, 'tablet': moves.hold_tablet, 'victory': victory}.get(name) or getattr(moves, name)
    c = fn()
    if getattr(c, 'floor', None):
        return floor_move(arm, c)
    anim.bake(arm, c)
    if getattr(c, 'legs_planted', False):
        feet = plant_feet(arm)
        if name == 'lunge': _lunge_step(arm, c, feet)
    hold_f = c.keys[1][0] if len(c.keys) > 1 else 0       # props are placed at the first held pose
    for pname, bone in c.props:
        props.attach(pname, arm, bone, frame=0 if name == 'press' else int(hold_f) if name != 'bottle' else 0)
    return c


def _scene_range(c):
    s = bpy.context.scene; s.frame_start = 0; s.frame_end = c.frames - 1; s.render.fps = c.fps


def _lunge_step(arm, c, feet):
    """Right foot steps forward ~0.62 m and comes back; the back (left) heel lifts at depth."""
    t = feet['R']; base = t.location.copy()
    for f, dy, dz in ((0, 0, 0), (8, -0.10, 0.06), (18, -0.62, 0.02), (24, -0.62, 0), (52, -0.62, 0), (60, -0.40, 0.06), (68, 0, 0.0), (79, 0, 0)):
        t.location = base + Vector((0, dy * _h(arm), dz)); t.keyframe_insert('location', frame=f)
    l = feet['L']; lb = l.location.copy()
    # back foot: heel up while deep (release the flat-foot lock and lift the ankle a little)
    cr = arm.pose.bones['Foot.L'].constraints.get('Copy Rotation')
    for f, inf, dz in ((0, 1, 0), (18, 1, 0), (28, 0, 0.07), (52, 0, 0.07), (62, 1, 0), (79, 1, 0)):
        if cr: cr.influence = inf; cr.keyframe_insert('influence', frame=f)
        l.location = lb + Vector((0, 0, dz)); l.keyframe_insert('location', frame=f)


def _h(arm):
    return J(arm, 'Hips').z / 0.93


def victory():
    """Celebration jump: deep load, explode up with arms in a V, tuck the feet, land soft, bounce once."""
    c = Clip('victory', 60); c.lag = {'Head': 2, 'Hand.L': 2, 'Hand.R': 2, 'ForeArm.L': 1, 'ForeArm.R': 1}
    P = moves.P
    load = P(both('UpperArm', x=20, y=8), both('ForeArm', x=-30), {'Spine': (22, 0, 0), 'Head': (8, 0, 0)},
             both('UpLeg', x=-60), both('Leg', x=95), both('Foot', x=-35), moves.fist('L', .6), moves.fist('R', .6))
    air = P(both('UpperArm', x=-20, y=-150), both('ForeArm', x=-10), both('Hand', x=18), {'Spine': (-8, 0, 0), 'Chest': (-6, 0, 0), 'Head': (-14, 0, 0)},
            both('UpLeg', x=-28), both('Leg', x=55), both('Foot', x=30), moves.open_hand('L', 1.2), moves.open_hand('R', 1.2))
    land = P(both('UpperArm', x=-10, y=-62, tw=-70), both('ForeArm', x=-115), {'Spine': (14, 0, 0), 'Head': (-6, 0, 0)}, both('UpLeg', x=-50), both('Leg', x=80), both('Foot', x=-25),
             moves.fist('L', .5), moves.fist('R', .5))
    c.key(0, P()); c.key(10, load, hips=(0, 0.04, -0.26), ease='in')
    c.key(16, air, hips=(0, 0, 0.30), ease='out')
    c.key(22, air, hips=(0, 0, 0.36))
    c.key(28, merge(air, both('Leg', x=-30), both('UpLeg', x=20)), hips=(0, 0, 0.12), ease='in')
    c.key(32, land, hips=(0, 0.03, -0.20), ease='out')
    c.key(40, merge(land, {'Spine': (-10, 0, 0)}, both('UpLeg', x=40), both('Leg', x=-60), both('Foot', x=20)), hips=(0, 0.01, -0.03))
    c.key(50, P(both('ForeArm', x=-25)), hips=(0, 0, 0))
    return c


def floor_move(arm, c):
    """Push-up / forearm plank: the body is a straight line pivoting on the balls of the feet; hands (or forearms)
    are IK-planted under the shoulders; elbows track back at ~45°."""
    h = _h(arm)
    toe = (J(arm, 'Toe.L') + J(arm, 'Toe.R')) / 2
    toe.z = 0.0
    hips0 = J(arm, 'Hips'); sh0 = (J(arm, 'UpperArm.L') + J(arm, 'UpperArm.R')) / 2
    ua = (J(arm, 'ForeArm.L') - J(arm, 'UpperArm.L')).length; fa = (J(arm, 'Hand.L') - J(arm, 'ForeArm.L')).length

    def body_at(theta):                       # theta = forward tilt from upright (deg), around the toe line
        R = Matrix.Rotation(math.radians(theta), 3, 'X')
        hp = toe + R @ (hips0 - toe)
        return hp - hips0, R

    P = moves.P
    base = merge(both('UpperArm', x=-6, y=6), {'Neck': (-18, 0, 0), 'Head': (-14, 0, 0)}, both('Foot', x=-62), both('Toe', x=55),
                 moves.relaxed_hands(.3))
    if c.floor == 'pushup':
        up_t, dn_t = 66.0, 80.0               # body ~24° above the floor at the top, ~10° at the bottom
        keys = [(0, up_t), (6, up_t), (26, dn_t), (30, dn_t), (46, up_t), (63, up_t)]
        eases = ['smooth', 'smooth', 'in', 'smooth', 'out', 'smooth']
        for (f, th), e in zip(keys, eases):
            d, R = body_at(th)
            c.key(f, base, hips=tuple(d), hips_rot=(th, 0, 0), ease=e)
        c.abs_hips = True
        anim.bake(arm, c)
        # hands planted under the shoulders at the top position, a little wider than shoulders
        d, R = body_at(up_t)
        for s in ('L', 'R'):
            shp = toe + R @ (J(arm, f'UpperArm.{s}') - toe)
            wrist = Vector((shp.x * 1.25, shp.y - 0.02, 0.045))
            t = _empty(f'hand_{s}', wrist, Quaternion((1, 0, 0), math.radians(-90)) @ Quaternion())
            pole = _empty(f'elbow_{s}', Vector((shp.x * 2.2, shp.y + 0.45, 0.35)))
            _ik(arm, f'ForeArm.{s}', t, pole, pole_angle=-90)
    else:
        # forearm plank: shoulders one upper-arm length above the floor; slow breathing hold with tiny shakes
        theta = math.degrees(math.acos(min(0.99, (ua + 0.03) / max(0.3, (sh0 - toe).length)))) if False else 0
        best = 80.0
        for th in [x * 0.25 for x in range(280, 360)]:
            R = Matrix.Rotation(math.radians(th), 3, 'X')
            shp = toe + R @ (sh0 - toe)
            if shp.z <= ua + 0.04: best = th; break
        for f, extra, e in ((0, 0, 'smooth'), (24, 0.6, 'smooth'), (48, -0.2, 'smooth'), (72, 0.5, 'smooth'), (95, 0, 'smooth')):
            th = best + extra * 0.4
            d, R = body_at(th)
            c.key(f, merge(base, {'Spine': (extra * 1.2, 0, 0)}), hips=tuple(d), hips_rot=(th, 0, 0), ease=e)
        c.abs_hips = True
        anim.bake(arm, c)
        d, R = body_at(best)
        for s in ('L', 'R'):
            shp = toe + R @ (J(arm, f'UpperArm.{s}') - toe)
            wrist = Vector((shp.x * 0.75, shp.y - fa * 0.98, 0.05))
            t = _empty(f'hand_{s}', wrist)
            pole = _empty(f'elbow_{s}', Vector((shp.x * 1.1, shp.y + 0.1, -0.5)))
            _ik(arm, f'ForeArm.{s}', t, pole, pole_angle=-90)
    return c
