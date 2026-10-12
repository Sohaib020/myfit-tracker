"""
Animation engine for the coaches (canonical skeleton from rig.py).

Poses are written in the character's own anatomical frame, measured in the REST pose:
  X = lateral (+X = character's left), Y = back (+) / front (-), Z = up.
  pose[bone] = (rx, ry, rz) degrees, applied X then Y then Z, relative to the parent (so 'ForeArm.L': (-90,0,0) bends the
  elbow 90° no matter where the upper arm points).  For .R bones use mirror() or the 'side' helpers.
Clips are keyposes on a timeline; the evaluator splines every angle (Catmull-Rom with per-key easing), adds follow-through
lag on chosen bones and breathing, then bakes one quaternion key per frame. Contacts (feet/hands on the floor, a plate in
the hand) use Blender IK / Child Of constraints so they stay perfectly planted.
"""
import bpy, math
from mathutils import Quaternion, Vector, Matrix, Euler

SIDES = ('L', 'R')


def mirror(p):
    """Mirror a pose dict across the body: .L ↔ .R, flipping Y/Z rotations."""
    out = {}
    for k, (x, y, z) in p.items():
        if k.endswith('.L'): k2 = k[:-2] + '.R'
        elif k.endswith('.R'): k2 = k[:-2] + '.L'
        else: k2 = k
        out[k2] = (x, -y, -z)
    return out


def both(name, x=0, y=0, z=0):
    """Same anatomical move on both sides (Y/Z mirrored)."""
    return {f'{name}.L': (x, y, z), f'{name}.R': (x, -y, -z)}


def side(name, s, x=0, y=0, z=0):
    return {f'{name}.{s}': (x, y if s == 'L' else -y, z if s == 'L' else -z)}


def merge(*ps):
    out = {}
    for p in ps:
        for k, v in p.items():
            if k in out: out[k] = tuple(a + b for a, b in zip(out[k], v))
            else: out[k] = tuple(v)
    return out


def _rest_q(arm, name):
    return (arm.matrix_world.to_quaternion() @ arm.data.bones[name].matrix_local.to_quaternion()).normalized()


def to_local(arm, name, rx, ry, rz):
    """World-axis rest-frame rotation → pose-bone local quaternion."""
    qw = Quaternion((0, 0, 1), math.radians(rz)) @ Quaternion((0, 1, 0), math.radians(ry)) @ Quaternion((1, 0, 0), math.radians(rx))
    B = _rest_q(arm, name)
    return (B.inverted() @ qw @ B).normalized()


# ---------------------------------------------------------------- spline evaluation

def _ease(u, kind):
    if kind == 'linear': return u
    if kind == 'in': return u * u * u
    if kind == 'out': return 1 - (1 - u) ** 3
    if kind == 'snap': return 1 - (1 - u) ** 5                    # fast attack, soft settle (punches, waves)
    return u * u * (3 - 2 * u)                                       # smooth (default)


def _catmull(p0, p1, p2, p3, u, tension=0.5):
    m1 = (p2 - p0) * tension; m2 = (p3 - p1) * tension
    u2, u3 = u * u, u * u * u
    return (2 * u3 - 3 * u2 + 1) * p1 + (u3 - 2 * u2 + u) * m1 + (-2 * u3 + 3 * u2) * p2 + (u3 - u2) * m2


class Clip:
    """keys: list of (frame, pose_dict, extras) where extras may hold 'hips' (x,y,z offset m), 'ease', 'props' etc."""

    def __init__(self, name, frames, loop=True, fps=24):
        self.name, self.frames, self.loop, self.fps = name, frames, loop, fps
        self.keys = []
        self.lag = {}           # bone -> frames of follow-through delay
        self.breath = 1.0       # breathing amplitude
        self.noise = 1.0        # tiny idle sway
        self.ik = []            # contact setups, see contacts()
        self.props = []         # (prop_name, bone, local offset matrix)
        self.cam = {}

    def key(self, f, pose, hips=(0, 0, 0), ease='smooth', hips_rot=(0, 0, 0)):
        self.keys.append((f, pose, Vector(hips), ease, Vector(hips_rot)))
        self.keys.sort(key=lambda k: k[0])
        return self

    def _seg(self, f):
        ks = self.keys
        if self.loop:
            n = len(ks); F = self.frames
            f = f % F
            for i in range(n):
                a, b = ks[i], ks[(i + 1) % n]
                fa, fb = a[0], b[0] + (F if i == n - 1 else 0)
                if fa <= f < fb or (i == n - 1 and (f >= fa or f < ks[0][0])):
                    ff = f if f >= fa else f + F
                    u = (ff - fa) / max(1e-6, fb - fa)
                    return ks[(i - 1) % n], a, b, ks[(i + 2) % n], u, b[3]
        f = max(ks[0][0], min(ks[-1][0], f))
        for i in range(len(ks) - 1):
            a, b = ks[i], ks[i + 1]
            if a[0] <= f <= b[0]:
                u = (f - a[0]) / max(1e-6, b[0] - a[0])
                return ks[max(0, i - 1)], a, b, ks[min(len(ks) - 1, i + 2)], u, b[3]
        k = ks[-1]; return k, k, k, k, 0.0, 'linear'

    def bones(self):
        s = set()
        for k in self.keys: s |= set(k[1].keys())
        return s

    def eval(self, f):
        """Returns ({bone: (rx,ry,rz)}, hips_offset, hips_rot) at frame f (float)."""
        out = {}
        for b in self.bones():
            ff = f - self.lag.get(b, 0)
            p0, p1, p2, p3, u, ease = self._seg(ff)
            ue = _ease(u, ease)
            g = lambda k: Vector(k[1].get(b, (0, 0, 0)))
            out[b] = tuple(_catmull(g(p0), g(p1), g(p2), g(p3), ue))
        p0, p1, p2, p3, u, ease = self._seg(f)
        ue = _ease(u, ease)
        hips = _catmull(p0[2], p1[2], p2[2], p3[2], ue)
        hr = _catmull(p0[4], p1[4], p2[4], p3[4], ue)
        # breathing: chest rises/expands ~4 s cycle; idle micro-sway so nothing is ever frozen
        t = f / self.fps
        br = math.sin(t * 2 * math.pi / 3.8) * self.breath
        for b, v in (('Chest', (-1.2 * br, 0, 0)), ('UpperChest', (-0.8 * br, 0, 0)), ('Neck', (0.9 * br, 0, 0)),
                     ('Shoulder.L', (0, 0, 0.8 * br)), ('Shoulder.R', (0, 0, -0.8 * br))):
            out[b] = tuple(a + c for a, c in zip(out.get(b, (0, 0, 0)), v))
        sw = self.noise
        out['Spine'] = tuple(a + c for a, c in zip(out.get('Spine', (0, 0, 0)), (0, 0.6 * sw * math.sin(t * 0.9), 0.5 * sw * math.sin(t * 0.7 + 1))))
        out['Head'] = tuple(a + c for a, c in zip(out.get('Head', (0, 0, 0)), (0.7 * sw * math.sin(t * 0.8 + 2), 0, 0.9 * sw * math.sin(t * 0.55))))
        return out, hips, hr


def bake(arm, clip, scale=1.0):
    """Writes one quaternion key per frame for every bone the clip touches (+ hips location/rotation)."""
    act = bpy.data.actions.new(clip.name)
    arm.animation_data_create(); arm.animation_data.action = act
    for pb in arm.pose.bones: pb.rotation_mode = 'QUATERNION'
    hip_h = arm.data.bones['Hips'].head_local.z
    k = hip_h / 0.93 * scale         # offsets are authored for a ~0.93 m hip height and scaled per character
    B = arm.data.bones['Hips']
    Bq = _rest_q(arm, 'Hips'); Bm = Bq.to_matrix()
    for f in range(clip.frames + (0 if clip.loop else 1)):
        pose, hips, hr = clip.eval(float(f))
        for b, (x, y, z) in pose.items():
            if b not in arm.pose.bones: continue
            pb = arm.pose.bones[b]
            pb.rotation_quaternion = to_local(arm, b, x, y, z)
            pb.keyframe_insert('rotation_quaternion', frame=f)
        hb = arm.pose.bones['Hips']
        # hips location is in the bone's rest frame: convert the world offset
        hb.location = Bm.inverted() @ (Vector(hips) * k)
        hb.keyframe_insert('location', frame=f)
        if hr.length > 1e-6:
            hb.rotation_quaternion = to_local(arm, 'Hips', *hr) @ hb.rotation_quaternion
            hb.keyframe_insert('rotation_quaternion', frame=f)
    s = bpy.context.scene; s.frame_start = 0; s.frame_end = clip.frames - (1 if clip.loop else 0); s.render.fps = clip.fps
    return act


# ---------------------------------------------------------------- contacts (IK)

def plant(arm, bone, chain, world_pos, pole=None, name=None, rot=None, influence=1.0):
    """IK constraint keeping [bone]'s tail... (we target the bone head of the end effector's child via its own head).
    world_pos: where the end bone's head (ankle / wrist) should be; pole: knee/elbow direction point."""
    tgt = bpy.data.objects.new(name or f'ik_{bone}', None); bpy.context.scene.collection.objects.link(tgt)
    tgt.location = world_pos
    pb = arm.pose.bones[chain]
    c = pb.constraints.new('IK'); c.target = tgt; c.chain_count = 2; c.influence = influence
    c.use_tail = True
    if pole is not None:
        pt = bpy.data.objects.new((name or bone) + '_pole', None); bpy.context.scene.collection.objects.link(pt); pt.location = pole
        c.pole_target = pt; c.pole_angle = math.radians(-90)
    if rot is not None:
        cr = arm.pose.bones[bone].constraints.new('COPY_ROTATION'); cr.target = tgt; cr.mix_mode = 'REPLACE'
        tgt.rotation_euler = rot
    return tgt


def world_head(arm, bone):
    return arm.matrix_world @ arm.data.bones[bone].head_local


# ---------------------------------------------------------------- retargeting mocap

def retarget(src_arm, src_map, src_action, tgt_arm, name, frames=None, step=1.0, hip_scale=None, fix=None):
    """Copies a mocap action from another rig onto the canonical skeleton by matching WORLD bone directions frame by
    frame (rest poses may differ: T vs A pose). src_map: canonical -> source bone name. fix(f, pose) may add offsets."""
    src_arm.animation_data_create(); src_arm.animation_data.action = src_action
    s = bpy.context.scene
    f0, f1 = src_action.frame_range
    n = frames or int(round((f1 - f0) / step))
    act = bpy.data.actions.new(name)
    tgt_arm.animation_data_create(); tgt_arm.animation_data.action = act
    for pb in tgt_arm.pose.bones: pb.rotation_mode = 'QUATERNION'
    order = [b.name for b in tgt_arm.data.bones]      # parents before children
    sh = (src_arm.matrix_world @ src_arm.data.bones[src_map['Hips']].head_local).z
    th = (tgt_arm.matrix_world @ tgt_arm.data.bones['Hips'].head_local).z
    hs = hip_scale or (th / sh)
    # rest correction: rotation taking the target rest bone direction onto the source rest bone direction
    C = {}
    for b in order:
        sb = src_map.get(b)
        if not sb or sb not in src_arm.data.bones: continue
        sd = (src_arm.matrix_world.to_3x3() @ (src_arm.data.bones[sb].tail_local - src_arm.data.bones[sb].head_local)).normalized()
        td = (tgt_arm.matrix_world.to_3x3() @ (tgt_arm.data.bones[b].tail_local - tgt_arm.data.bones[b].head_local)).normalized()
        C[b] = td.rotation_difference(sd)
    src_rest = {b: (src_arm.matrix_world.to_quaternion() @ src_arm.data.bones[src_map[b]].matrix_local.to_quaternion()) for b in C}
    hip0 = None
    for i in range(n + 1):
        f = f0 + i * step
        s.frame_set(int(f), subframe=f - int(f))
        dl = bpy.context.evaluated_depsgraph_get()
        world = {}
        for b in order:
            if b not in C:
                continue
            spb = src_arm.pose.bones[src_map[b]]
            Ws = (src_arm.matrix_world @ spb.matrix).to_quaternion()
            delta = Ws @ src_rest[b].inverted()            # source world rotation relative to its rest
            world[b] = delta @ C[b]                         # target world rotation relative to target rest
        for b in order:
            if b not in world: continue
            par = tgt_arm.data.bones[b].parent
            pw = Quaternion()
            p = par
            while p is not None and p.name not in world: p = p.parent
            if p is not None: pw = world[p.name]
            local_world = pw.inverted() @ world[b]
            B = _rest_q(tgt_arm, b)
            pb = tgt_arm.pose.bones[b]
            pb.rotation_quaternion = (B.inverted() @ local_world @ B).normalized()
            pb.keyframe_insert('rotation_quaternion', frame=i)
        # hips translation (scaled)
        hp = src_arm.matrix_world @ src_arm.pose.bones[src_map['Hips']].head
        if hip0 is None: hip0 = hp.copy()
        d = (hp - (src_arm.matrix_world @ src_arm.data.bones[src_map['Hips']].head_local)) * hs
        hb = tgt_arm.pose.bones['Hips']
        hb.location = _rest_q(tgt_arm, 'Hips').to_matrix().inverted() @ d
        hb.keyframe_insert('location', frame=i)
    tgt_arm.animation_data.action = act
    return act, n
