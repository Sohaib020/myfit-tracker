"""
R24 champion motion pack: every Mixamo move stored ONCE as skeleton-independent motion, applied to any champion.

Why: retargeting needs the source rig, but the owner's Mixamo FBX files can't go in the (public) repo. The retarget in
tools/coach3d/anim.py only uses, from the source, (a) each bone's WORLD rotation relative to its rest, (b) the source
rest bone directions and (c) the hip offset. Those are stored here per frame at 24 fps (motion.json.gz, ~1-2 MB for
all moves), and apply() redoes the retarget onto a champion exactly as anim.retarget would.

  python motion.py build <fbx_dir> [<fbx_dir2> ...] <out.json.gz>      # owner's machine / this session only
  (apply() is imported by the clip renderer and the GLB exporter)
"""
import sys, os, json, gzip, math
import bpy
from mathutils import Quaternion, Vector
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'coach3d'))
import rig, anim

OUT_FPS = 24.0
OWN_REST = ('Hips', 'Spine', 'Chest', 'UpperChest', 'Neck', 'Head')

# app clip name -> (Mixamo file, loop, max seconds, options). Names match Pip's moves so the app needs no mapping.
MOVES = {
    'idle': ('weight shift', True, 8.0, {}),        # R26: natural idle (Happy Idle bounced)
    'talk': ('Talking', True, None, {}),
    'wave': ('Waving', False, None, {}),
    'celebrate': ('Cheering', False, None, {'level': False}),
    'thinking': ('Thinking', False, None, {}),
    'love': ('Blow A Kiss', False, None, {}),
    'sleepy': ('Yawn', False, 6.0, {}),
    'letsgo': ('Fist Pump', False, None, {}),
    'train': ('Air Squat', True, None, {}),
    'dance': ('Hip Hop Dancing', True, 7.0, {'travel': 0.15}),
    'clap': ('Clapping', False, None, {}),
    'laugh': ('Laughing', False, 5.0, {}),
    'salute': ('Salute', False, None, {}),
    'bow': ('Quick Informal Bow', False, None, {}),
    'point': ('Pointing', False, None, {}),
    'shrug': ('Shrugging', False, None, {}),
    'surprised': ('Surprised', False, None, {}),
    'sad': ('Sad Idle', True, 6.0, {}),
    'jumpingjacks': ('Jumping Jacks', True, None, {}),
    'jog': ('Jog Forward', True, None, {'travel': 0.0}),
    'thumbsup': ('Standing Thumbs Up', False, None, {}),
    'yes': ('head nod yes', False, None, {}),
    'no': ('shaking head no', False, None, {}),
    'stretch': ('Arm Stretching', True, 7.0, {}),
    # tier specials (same for every champion of that tier and above)
    'cocky': ('being cocky', False, None, {'tier': 'epic'}),
    'victory': ('Victory', False, None, {'tier': 'epic', 'level': False}),
    'combo': ('Punch To Elbow Combo', False, None, {'tier': 'legendary', 'travel': 0.0}),
    'rally': ('Rallying', False, None, {'tier': 'legendary'}),
}
TIER_RANK = {'street': 0, 'epic': 1, 'legendary': 2}


# what the clip packs carry (phones without 3D): the moves Pip's code plays most + talk + a few reactions; anything
# else falls back to the closest of these (Buddy.FALLBACK). The 3D GLBs carry every move.
CLIP_SET = ['idle', 'wave', 'celebrate', 'thinking', 'love', 'sleepy', 'letsgo', 'train', 'talk', 'dance', 'clap', 'thumbsup', 'yes',
            'cocky', 'victory', 'combo', 'rally']


def moves_for(tier):
    return [m for m, (_, _, _, o) in MOVES.items() if TIER_RANK[o.get('tier', 'street')] <= TIER_RANK[tier]]


# ---------------------------------------------------------------- build (needs the FBX files)

def _find(dirs, stem):
    for d in dirs:
        p = os.path.join(d, stem + '.fbx')
        if os.path.exists(p): return p
    return None


def build(dirs, out):
    import mocap  # tools/coach3d — FBX import helper
    pack = {'fps': OUT_FPS, 'rest': None, 'moves': {}}
    for name, (stem, loop, max_s, opt) in MOVES.items():
        path = _find(dirs, stem)
        if not path: print('MISSING', stem); continue
        bpy.ops.wm.read_factory_settings(use_empty=True)
        src, act = mocap._import_fbx(path)
        src.animation_data_create(); src.animation_data.action = act
        M = rig.MIXAMO
        bones = [b for b in M if M[b] in src.data.bones and not b.endswith('End') and b != 'HeadTop']
        mw = src.matrix_world
        rest_q = {b: (mw.to_quaternion() @ src.data.bones[M[b]].matrix_local.to_quaternion()) for b in bones}
        if pack['rest'] is None:
            hip = src.data.bones[M['Hips']]
            pack['rest'] = {
                'hip_h': (mw @ hip.head_local).z,
                'dirs': {b: list((mw.to_3x3() @ (src.data.bones[M[b]].tail_local - src.data.bones[M[b]].head_local)).normalized()) for b in bones},
            }
        hip_rest = mw @ src.data.bones[M['Hips']].head_local
        f0, f1 = act.frame_range
        start = opt.get('start', 0.0) * 30.0
        dur = (f1 - f0 - start) / 30.0
        if max_s: dur = min(dur, max_s)
        n = int(round(dur * OUT_FPS)); step = 30.0 / OUT_FPS
        frames = []
        sc = bpy.context.scene
        for i in range(n + 1):
            f = f0 + start + i * step
            sc.frame_set(int(f), subframe=f - int(f))
            rot = {}
            for b in bones:
                Ws = (mw @ src.pose.bones[M[b]].matrix).to_quaternion()
                q = Ws @ rest_q[b].inverted()
                rot[b] = [round(x, 5) for x in q]
            hp = mw @ src.pose.bones[M['Hips']].head
            frames.append({'r': rot, 'h': [round(x, 5) for x in (hp - hip_rest) / pack['rest']['hip_h']]})
        pack['moves'][name] = {'loop': loop, 'n': n, 'opt': opt, 'frames': frames}
        print('MOVE', name, n)
    with gzip.open(out, 'wt') as fh: json.dump(pack, fh, separators=(',', ':'))
    print('WROTE', out, os.path.getsize(out))


# ---------------------------------------------------------------- apply (CI / renderer)

_PACK = {}


def load_pack(path):
    if path not in _PACK:
        with gzip.open(path, 'rt') as fh: _PACK[path] = json.load(fh)
    return _PACK[path]


def apply(arm, pack, name, level_head=True):
    """Keys the move onto [arm] (canonical skeleton) exactly like anim.retarget; returns (action, frames, loop)."""
    mv = pack['moves'][name]; rest = pack['rest']
    act = bpy.data.actions.new('mx_' + name)
    arm.animation_data_create(); arm.animation_data.action = act
    for pb in arm.pose.bones: pb.rotation_mode = 'QUATERNION'
    order = [b.name for b in arm.data.bones]
    tw = arm.matrix_world.to_3x3()
    C = {}
    for b in order:
        if b not in rest['dirs']: continue
        if b in OWN_REST: C[b] = Quaternion(); continue
        tb = arm.data.bones[b]
        td = (tw @ (tb.tail_local - tb.head_local)).normalized()
        C[b] = td.rotation_difference(Vector(rest['dirs'][b]))
    th = (arm.matrix_world @ arm.data.bones['Hips'].head_local).z
    travel = mv['opt'].get('travel', 1.0)
    h0 = Vector(mv['frames'][0]['h'])
    Bh = anim._rest_q(arm, 'Hips').to_matrix().inverted()
    for i, fr in enumerate(mv['frames']):
        world = {b: Quaternion(fr['r'][b]) @ C[b] for b in C if b in fr['r']}
        for b in order:
            if b not in world: continue
            p = arm.data.bones[b].parent
            while p is not None and p.name not in world: p = p.parent
            pw = world[p.name] if p is not None else Quaternion()
            B = anim._rest_q(arm, b)
            pb = arm.pose.bones[b]
            pb.rotation_quaternion = (B.inverted() @ (pw.inverted() @ world[b]) @ B).normalized()
            pb.keyframe_insert('rotation_quaternion', frame=i)
        h = Vector(fr['h'])
        if travel != 1.0:
            h = Vector((h0.x + (h.x - h0.x) * travel - h0.x, h0.y + (h.y - h0.y) * travel - h0.y, h.z))
        hb = arm.pose.bones['Hips']; hb.location = Bh @ (h * th); hb.keyframe_insert('location', frame=i)
    n = mv['n']
    import mocap
    if level_head and mv['opt'].get('level', True): mocap._level_head(arm, act, n)
    if mv['loop']: mocap._loop_blend(act, n)
    act['frames'] = n; act['loop'] = int(mv['loop'])
    return act, n, mv['loop']


if __name__ == '__main__' and sys.argv[1] == 'build':
    build(sys.argv[2:-1], sys.argv[-1])
