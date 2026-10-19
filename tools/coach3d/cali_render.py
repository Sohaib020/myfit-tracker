"""
R32 calisthenics clips: a 3D coach performs every skill-tree step, posed from the same joint angles the app's
illustrated figure uses (app/src/main/assets/calisthenics.json, side view, 0 = down, 90 = forward, 180 = up).

  python cali_render.py <glb_dir> <who> <chain id | all> <out_dir> [samples=10]

For each step: frames <key>/0000.png… (rep steps: 48-frame loop start → end → start; holds: one frame), with the
contact joint anchored (hands on the bar, feet on the floor) and simple props (bar, parallel bars, box, wall, rings).
"""
import sys, os, json, math, bpy
from mathutils import Vector, Quaternion, Matrix
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rig, studio, look

glb, who, chain_id, out = sys.argv[1:5]
samples = int(sys.argv[5]) if len(sys.argv) > 5 else 10
RES = int(os.environ.get('CALI_RES', '560'))
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
D = json.load(open(os.path.join(ROOT, 'app/src/main/assets/calisthenics.json')))
steps = [(c, s) for c in D['chains'] for s in c['steps'] if chain_id in ('all', c['id'])]
if os.environ.get('ONLY'): steps = [x for x in steps if x[1]['k'] in os.environ['ONLY'].split(',')]

arm, body = rig.load(who, glb)
look.refine(body, subdiv=0)
rig.bulk(arm, who)
s = studio.setup(res=RES, samples=samples)
s.cycles.seed = 7; s.cycles.use_animated_seed = False
s.cycles.denoising_prefilter = 'FAST'
s.render.image_settings.file_format = 'PNG'; s.render.image_settings.color_mode = 'RGBA'
s.render.resolution_x = RES; s.render.resolution_y = RES

MW = arm.matrix_world
ORDER = [b.name for b in arm.data.bones]                       # parents before children
REST_Q = {b.name: (MW.to_quaternion() @ b.matrix_local.to_quaternion()).normalized() for b in arm.data.bones}
REST_D = {b.name: (MW.to_3x3() @ (b.tail_local - b.head_local)).normalized() for b in arm.data.bones}
PARENT = {b.name: (b.parent.name if b.parent else None) for b in arm.data.bones}
for pb in arm.pose.bones: pb.rotation_mode = 'QUATERNION'


def d2(a, lat=0.0):
    r = math.radians(a)
    return Vector((lat, -math.sin(r), -math.cos(r))).normalized()


def rx(deg): return Quaternion((1, 0, 0), math.radians(deg))


def lerp_pose(a, b, t):
    out = []
    for x, y in zip(a, b):
        d = (y - x) % 360
        if d > 180: d -= 360
        out.append(x + d * t)
    return out


HAND = {'mode': 'follow'}


def deltas(p):
    """World rotation (from rest) of every bone for one pose: the torso and head turn about the lateral axis, each limb
    segment is aimed at its 2D direction (a little splay so near and far limbs don't overlap exactly)."""
    t, hd, a1, f1, a2, f2, l1, s1, l2, s2, ft1, ft2 = p
    Rt = rx(180 - t); Rh = rx(180 - hd)
    dl = {}
    for b in ('Hips', 'Spine', 'Chest', 'UpperChest', 'Shoulder.L', 'Shoulder.R'):
        dl[b] = Rt
    dl['Neck'] = Rt.slerp(Rh, 0.5); dl['Head'] = Rh

    def aim(b, target):
        par = dl[PARENT[b]]
        r = par @ REST_D[b]
        dl[b] = (r.rotation_difference(target) @ par).normalized()
    # near side = character's right (camera on the right), far side = left
    for side, (a, f, l, sh, ft) in (('R', (a1, f1, l1, s1, ft1)), ('L', (a2, f2, l2, s2, ft2))):
        lat = -1.0 if side == 'R' else 1.0
        aim(f'UpperArm.{side}', d2(a, lat * 0.16)); aim(f'ForeArm.{side}', d2(f, lat * 0.08))
        if HAND['mode'] == 'floor': aim(f'Hand.{side}', d2(92, lat * 0.12))        # palm flat, fingers forward
        elif HAND['mode'] == 'wall': aim(f'Hand.{side}', d2(178, lat * 0.1))      # palm on the wall, fingers up
        else: dl[f'Hand.{side}'] = dl[f'ForeArm.{side}']
        aim(f'UpLeg.{side}', d2(l, lat * 0.07)); aim(f'Leg.{side}', d2(sh, lat * 0.04))
        aim(f'Foot.{side}', d2(ft)); dl[f'Toe.{side}'] = dl[f'Foot.{side}']
    for b in ORDER:                                    # fingers, toes ends, extras follow their parent
        if b not in dl: dl[b] = dl.get(PARENT[b]) or Quaternion()
    return dl


def apply(p, offset=Vector()):
    dl = deltas(p)
    for b in ORDER:
        pb = arm.pose.bones[b]
        par = PARENT[b]
        pw = dl[par] if par else Quaternion()
        lw = pw.inverted() @ dl[b]
        B = REST_Q[b]
        pb.rotation_quaternion = (B.inverted() @ lw @ B).normalized()
    if HAND['mode'] == 'follow' and HAND.get('grip'):
        # wrap the fingers round the bar (same curl as moves.fist, parent-relative)
        import anim
        for side, m in (('L', 1), ('R', -1)):
            for bn, y in (('Fingers1', 80), ('Fingers2', 90), ('Fingers3', 70), ('Thumb1', 25), ('Thumb2', 40), ('Thumb3', 30)):
                n = f'{bn}.{side}'
                if n in arm.pose.bones: arm.pose.bones[n].rotation_quaternion = anim.to_local(arm, n, 0, y * m, 0)
    hb = arm.pose.bones['Hips']
    hb.location = REST_Q['Hips'].to_matrix().inverted() @ offset
    bpy.context.view_layer.update()


def J(name, tail=False):
    pb = arm.pose.bones[name]
    return MW @ (pb.tail if tail else pb.head)


JOINTS = {'WR1': ('Hand.R', False), 'TO1': ('Toe.R', True), 'AN1': ('Foot.R', False), 'KN1': ('Leg.R', False), 'HIP': ('Hips', False)}
ALLJ = [('Hand.R', False), ('Hand.L', False), ('Toe.R', True), ('Toe.L', True), ('Foot.R', False), ('Foot.L', False), ('Leg.R', False),
        ('Leg.L', False), ('Hips', False), ('Head', True), ('UpperArm.R', False), ('ForeArm.R', False), ('ForeArm.L', False)]


def joints_now():
    return [J(n, t) for n, t in ALLJ]


def anchor_name(pr, p0):
    """Same rule as the app's figure: hands for bar/rings/bars/parallettes/boxed hands, else toes or hands on the floor."""
    if any(x in pr for x in ('bar', 'rings', 'pbars', 'plt', 'box_h')): return 'WR1'
    apply(p0)
    zs = joints_now(); low = min(v.z for v in zs)
    if J('Toe.R', True).z <= low + 0.07: return 'TO1'
    if J('Hand.R').z <= low + 0.07: return 'WR1'
    return 'AN1'


def schedule(n_move=18, n_hold=6):
    """48 frames: hold start, ease to the end pose, hold, ease back (smoothstep)."""
    ts = [0.0] * n_hold
    ts += [(lambda u: u * u * (3 - 2 * u))((i + 1) / n_move) for i in range(n_move)]
    ts += [1.0] * n_hold
    ts += [(lambda u: u * u * (3 - 2 * u))(1 - (i + 1) / n_move) for i in range(n_move)]
    return ts


# ---------------------------------------------------------------- props
def mat(name, rgb, metal=0.0, rough=0.5):
    m = bpy.data.materials.get(name)
    if m: return m
    m = bpy.data.materials.new(name); m.use_nodes = True
    bs = m.node_tree.nodes['Principled BSDF']
    bs.inputs['Base Color'].default_value = (*rgb, 1); bs.inputs['Metallic'].default_value = metal; bs.inputs['Roughness'].default_value = rough
    return m


def cyl(a, b, r, m):
    d = b - a; L = d.length
    bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=L, location=(a + b) / 2, vertices=24)
    o = bpy.context.object; o.rotation_mode = 'QUATERNION'; o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
    o.data.materials.append(m); o['prop'] = 1; return o


def box(center, size, m):
    bpy.ops.mesh.primitive_cube_add(size=1, location=center)
    o = bpy.context.object; o.scale = size; o.data.materials.append(m); o['prop'] = 1
    bev = o.modifiers.new('b', 'BEVEL'); bev.width = 0.015; bev.segments = 3
    return o


def clear_props():
    for o in list(bpy.data.objects):
        if o.get('prop'): bpy.data.objects.remove(o, do_unlink=True)


METAL = None


def props(pr, floor_z):
    """Props placed from the first pose (anchored), like the app's figure."""
    metal = mat('metal', (0.55, 0.57, 0.62), 0.85, 0.28); matte = mat('matte', (0.80, 0.81, 0.84), 0.0, 0.7)
    out = []
    wr, wl = J('Hand.R'), J('Hand.L')
    def palm(side):
        pb = arm.pose.bones[f'Hand.{side}']; h, t = MW @ pb.head, MW @ pb.tail
        return h + (t - h) * 0.75
    if 'bar' in pr:
        pr_, pl_ = palm('R'), palm('L')
        y, z = (pr_.y + pl_.y) / 2, (pr_.z + pl_.z) / 2
        out.append(cyl(Vector((-0.75, y, z)), Vector((0.75, y, z)), 0.022, metal))
    if 'rings' in pr:
        for w in (wr, wl):
            bpy.ops.mesh.primitive_torus_add(major_radius=0.09, minor_radius=0.014, location=(w.x, w.y, w.z + 0.03), rotation=(0, math.pi / 2, 0))
            o = bpy.context.object; o.data.materials.append(mat('wood', (0.55, 0.36, 0.2), 0, 0.5)); o['prop'] = 1; out.append(o)
            out.append(cyl(Vector((w.x, w.y, w.z + 0.12)), Vector((w.x, w.y, w.z + 1.4)), 0.008, matte))
    if 'pbars' in pr:
        for w in (wr, wl):
            x = w.x + (0.05 if w.x > 0 else -0.05)
            z = w.z - 0.03
            out.append(cyl(Vector((x, w.y - 0.55, z)), Vector((x, w.y + 0.55, z)), 0.022, metal))
            if floor_z is not None:
                for yy in (w.y - 0.45, w.y + 0.45): out.append(cyl(Vector((x, yy, floor_z)), Vector((x, yy, z)), 0.02, metal))
    if 'plt' in pr:
        for w in (wr, wl):
            out.append(cyl(Vector((w.x, w.y - 0.12, w.z - 0.03)), Vector((w.x, w.y + 0.12, w.z - 0.03)), 0.016, metal))
    if floor_z is not None:
        def under(p, w=0.5, d=0.42, extra=0.0):
            h = p.z - floor_z + extra
            if h > 0.05: out.append(box(Vector((0, p.y, floor_z + h / 2)), (w, d, h), matte))
        if 'box_h' in pr: under((wr + wl) / 2, 0.7, 0.35, -0.02)
        if 'box_f' in pr: under((J('Toe.R', True) + J('Toe.L', True)) / 2, 0.6, 0.35, -0.02)
        if 'box_f2' in pr: under(J('Foot.L'), 0.45, 0.4, -0.03)
        if 'box_hip' in pr: out.append(box(Vector((0, J('Foot.R').y + 0.38, floor_z + 0.22)), (0.5, 0.4, 0.44), matte))
    return out


def wall(pr, ys):
    if 'wall_f' in pr: y = min(ys) - 0.03; return box(Vector((0, y - 0.03, 1.1)), (1.6, 0.06, 2.6), mat('wall', (0.88, 0.89, 0.91), 0, 0.8))
    if 'wall_b' in pr: y = max(ys) + 0.03; return box(Vector((0, y + 0.03, 1.1)), (1.6, 0.06, 2.6), mat('wall', (0.88, 0.89, 0.91), 0, 0.8))
    return None


def shadow_floor(z):
    bpy.ops.mesh.primitive_plane_add(size=8, location=(0, 0, z))
    o = bpy.context.object; o.is_shadow_catcher = True; o['prop'] = 1
    return o


# ---------------------------------------------------------------- camera fitting (orthographic, 3/4 side view)
YAW, PITCH = -68, 7


def fit_camera(points, yaw=None):
    cam = s.camera
    if cam is None:
        cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam')); s.collection.objects.link(cam); s.camera = cam
    cam.data.type = 'ORTHO'
    y, p = math.radians(YAW if yaw is None else yaw), math.radians(PITCH)
    fwd = -Vector((math.sin(y) * math.cos(p), -math.cos(y) * math.cos(p), math.sin(p)))
    right = fwd.cross(Vector((0, 0, 1))).normalized(); up = right.cross(fwd).normalized()
    xs = [v.dot(right) for v in points]; ys = [v.dot(up) for v in points]; zs = [v.dot(fwd) for v in points]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    span = max(max(xs) - min(xs), max(ys) - min(ys)) * 1.12 + 0.2
    c = right * cx + up * cy + fwd * (min(zs) - 6.0)
    cam.location = c
    cam.rotation_euler = fwd.to_track_quat('-Z', 'Y').to_euler()
    cam.data.ortho_scale = span
    cam.data.clip_end = 50


os.makedirs(out, exist_ok=True)
done = 0
for c, st in steps:
    key = st['k']
    poses = [D['poses'][k] for k in st['p']]
    pr = st['pr']
    hold = poses[0] == poses[-1]
    HAND['grip'] = any(x in pr for x in ('bar', 'rings', 'pbars'))
    HAND['mode'] = 'wall' if 'wall_f' in pr and c['id'] == 'push' else 'follow' if any(x in pr for x in ('bar', 'rings', 'pbars', 'plt')) else 'floor'
    an = anchor_name(pr, poses[0])
    if an != 'WR1' and HAND['mode'] == 'floor' and c['id'] not in ('core', 'hand', 'push', 'dip'): HAND['mode'] = 'follow'   # free hands hang naturally
    jn, jt = {'WR1': ('Hand.R', False), 'TO1': ('Toe.R', True), 'AN1': ('Foot.R', False)}[an]
    apply(poses[0]); a0 = J(jn, jt)
    ts = [0.0] if hold else schedule()
    # pass 1: anchored offsets + bounds over every frame
    offs, pts, low = [], [], 9.0
    for i, t in enumerate(ts):
        p = lerp_pose(poses[0], poses[-1], t)
        apply(p); off = a0 - J(jn, jt); apply(p, off)
        offs.append(off); pts += joints_now()
        if i % 4 == 0:                                   # lowest point of the actual body (soles, fingers) for the floor
            dg = bpy.context.evaluated_depsgraph_get(); ev = body.evaluated_get(dg); me = ev.to_mesh()
            mwb = ev.matrix_world; low = min(low, min((mwb @ v.co).z for v in me.vertices)); ev.to_mesh_clear()
    hanging = any(x in pr for x in ('bar', 'rings')) and 'floor' not in pr
    apply(poses[0], offs[0])
    zs0 = [v.z for v in joints_now()]
    floor_z = None if hanging else (low - 0.004 if 'pbars' not in pr else low - 0.12)
    clear_props()
    made = props(pr, floor_z)
    w = wall(pr, [v.y for v in pts])
    if w: made.append(w)
    if floor_z is not None: shadow_floor(floor_z)
    extra = []
    for o in made:
        bpy.context.view_layer.update()
        extra += [o.matrix_world @ Vector(v) for v in o.bound_box]
    # head volume and a little room under the feet
    # walls are seen edge-on (pure side view) so they never hide the coach
    fit_camera(pts + [v + Vector((0, 0, 0.14)) for v in pts[9::len(ALLJ)]] + (extra if len(extra) < 400 else []),
               yaw=-90 if any(x in pr for x in ('wall_f', 'wall_b')) else None)
    d = os.path.join(out, key); os.makedirs(d, exist_ok=True)
    for i, t in enumerate(ts):
        apply(lerp_pose(poses[0], poses[-1], t), offs[i])
        s.render.filepath = os.path.join(d, f'{i:04d}.png')
        bpy.ops.render.render(write_still=True)
    open(os.path.join(d, 'meta.txt'), 'w').write(f'{len(ts)} frames 30 fps {"hold" if hold else "rep"} thumb {0 if hold else 24}')
    done += 1
    print('STEP_DONE', key, len(ts), flush=True)
print('RENDER_DONE', done)
