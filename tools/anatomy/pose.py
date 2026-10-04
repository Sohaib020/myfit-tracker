"""Posing + props for the MakeHuman mannequin. A pose is a dict of joint angles (degrees) in the body frame;
limbs are aimed bone-by-bone in armature space (minimal twist), feet are re-grounded automatically."""
import bpy, math
from mathutils import Vector, Matrix, Euler, Quaternion
import mh as M

FLOOR = None   # rest-pose foot height (armature space), set by init()

def rad(a): return math.radians(a)

def limb_dir(side, ab, fl, frame):
    """Direction for a limb hanging down: ab = sideways (abduction), fl = forward (flexion), in the given frame."""
    s = 1 if side == 'L' else -1
    a, f = rad(ab), rad(fl)
    d = Vector((s * math.sin(a) * math.cos(f), -math.sin(f), -math.cos(a) * math.cos(f)))
    return (frame @ d).normalized()

def init():
    global FLOOR
    arm = M.OBJ['Rig']
    FLOOR = min(arm.data.bones['foot.L'].tail_local.z, arm.data.bones['lowerleg02.L'].tail_local.z)

def reset():
    arm = M.OBJ['Rig']
    for pb in arm.pose.bones:
        pb.rotation_mode = 'QUATERNION'; pb.rotation_quaternion = (1, 0, 0, 0); pb.location = (0, 0, 0)
    bpy.context.view_layer.update()

def aim(name, d):
    arm = M.OBJ['Rig']; pb = arm.pose.bones[name]
    bpy.context.view_layer.update()
    m = pb.matrix.copy(); cur = Vector(m.col[1][:3]).normalized()
    r = cur.rotation_difference(d.normalized()).to_matrix().to_4x4()
    t = m.translation.copy()
    pb.matrix = Matrix.Translation(t) @ r @ Matrix.Translation(-t) @ m
    bpy.context.view_layer.update()

def rotate(name, axis, deg):
    """Extra rotation of a bone around an armature-space axis (pivot at its head)."""
    arm = M.OBJ['Rig']; pb = arm.pose.bones[name]
    bpy.context.view_layer.update()
    m = pb.matrix.copy(); t = m.translation.copy()
    pb.matrix = Matrix.Translation(t) @ Matrix.Rotation(rad(deg), 4, axis) @ Matrix.Translation(-t) @ m
    bpy.context.view_layer.update()

def apply(P):
    """P keys (deg unless noted): pitch/roll/yaw (whole body), lift (m), torso (forward bend), twist, neck,
    armL/armR = (ab, fl), foreL/foreR = (ab, fl) absolute forearm dir, legL/legR = (ab, fl), shinL/shinR = (ab, fl),
    ground (bool, default True) keeps the lowest foot on the floor."""
    arm = M.OBJ['Rig']; reset()
    g = lambda k, d: P.get(k, d)
    # torso bend (spread over the spine) and twist
    bend = g('torso', 0.0); tw = g('twist', 0.0)
    for k, w in (('spine05', 0.15), ('spine04', 0.2), ('spine03', 0.25), ('spine02', 0.25), ('spine01', 0.15)):
        if bend: rotate(k, 'X', -bend * w)
        if tw: rotate(k, 'Z', tw * w)
    if g('neck', 0.0): rotate('neck01', 'X', -g('neck', 0.0))
    if g('side', 0.0):
        for k, w in (('spine04', 0.3), ('spine03', 0.35), ('spine02', 0.35)): rotate(k, 'Y', g('side', 0.0) * w)
    if g('shrug', 0.0):
        rotate('clavicle.L', 'Y', -g('shrug', 0.0)); rotate('clavicle.R', 'Y', g('shrug', 0.0))
    chest = arm.pose.bones['spine01'].matrix.to_3x3()
    rest_chest = arm.data.bones['spine01'].matrix_local.to_3x3()
    tf = chest @ rest_chest.inverted()                      # torso frame relative to rest
    if g('armframe', 'torso') == 'world': tf = Matrix.Identity(3)
    for s in 'LR':
        ab, fl = g('arm' + s, (12, 4))
        d = limb_dir(s, ab, fl, tf)
        aim('upperarm01.' + s, d); aim('upperarm02.' + s, d)
        fab, ffl = g('fore' + s, (ab * 0.6, fl + 18))
        fd = limb_dir(s, fab, ffl, tf)
        aim('lowerarm01.' + s, fd); aim('lowerarm02.' + s, fd)
        if 'hand' + s in P: aim('wrist.' + s, limb_dir(s, *P['hand' + s], tf))
        lab, lfl = g('leg' + s, (3, 0))
        ld = limb_dir(s, lab, lfl, Matrix.Identity(3))
        aim('upperleg02.' + s, ld)
        sab, sfl = g('shin' + s, (lab, min(lfl, 0)))
        sd = limb_dir(s, sab, sfl, Matrix.Identity(3))
        aim('lowerleg01.' + s, sd); aim('lowerleg02.' + s, sd)
        if g('toes', False): aim('foot.' + s, Vector((0, -0.4, -1)))
        else: aim('foot.' + s, Vector((0, -1, -0.45)))
    # whole-body orientation + grounding
    root = arm.pose.bones['root']
    R = Euler((rad(g('pitch', 0.0)), rad(g('roll', 0.0)), rad(g('yaw', 0.0))), 'XYZ').to_matrix().to_4x4()
    arm.matrix_world = Matrix.Translation((0, 0, 0)) @ R
    bpy.context.view_layer.update()
    lift = g('lift', 0.0)
    gm = g('ground', True)
    if gm:
        pts = ['foot.L', 'foot.R', 'lowerleg02.L', 'lowerleg02.R'] + (['wrist.L', 'wrist.R', 'lowerarm02.L', 'lowerarm02.R'] if gm == 'hands' else [])
        if gm == 'knees': pts = ['lowerleg01.L', 'lowerleg01.R', 'foot.L', 'foot.R']
        low = min((arm.matrix_world @ arm.pose.bones[b].tail).z for b in pts)
        lift += FLOOR - low + (0.02 if gm == 'hands' else 0.0)
    if g('tiptoe', 0.0): lift += 0.07 * g('tiptoe', 0.0)
    arm.matrix_world = Matrix.Translation((g('x', 0.0), g('y', 0.0), lift)) @ R
    bpy.context.view_layer.update()

def hand(side):
    arm = M.OBJ['Rig']; pb = arm.pose.bones['wrist.' + side]
    return arm.matrix_world @ (pb.head + (pb.tail - pb.head) * 2.2)

# ---------------------------------------------------------------- props
PROPS = {}
def _cyl(name, r, depth, mat):
    bpy.ops.mesh.primitive_cylinder_add(vertices=32, radius=r, depth=depth)
    o = bpy.context.active_object; o.name = name; bpy.ops.object.shade_smooth(); o.data.materials.append(mat); return o

def _box(name, size, loc, mat):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc); o = bpy.context.active_object; o.name = name
    o.scale = size; o.data.materials.append(mat)
    bev = o.modifiers.new('b', 'BEVEL'); bev.width = 0.02; bev.segments = 3
    return o

def _join(objs):
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs: o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    bpy.ops.object.join(); o = bpy.context.active_object
    bpy.ops.object.origin_set(type='ORIGIN_GEOMETRY', center='BOUNDS')
    return o

def props_init():
    steel = M.mat('steel', '#8E9196', rough=0.3); black = M.mat('blackp', '#1E1F22', rough=0.5); pad = M.mat('pad', '#2A2B30', rough=0.7)
    frame = M.mat('frame', '#A7AAB0', rough=0.4)
    # barbell: bar + plates joined into one mesh (no parenting surprises)
    parts = [_cyl('Barbell', 0.014, 2.0, steel)]
    for sx in (-1, 1):
        for k, (r, w) in enumerate(((0.22, 0.05), (0.17, 0.035))):
            p = _cyl('Plate%d%d' % (sx, k), r, w, black); p.location = (0, 0, sx * (0.72 + k * 0.045)); parts.append(p)
    PROPS['barbell'] = _join(parts)
    # dumbbells
    for s_ in 'LR':
        parts = [_cyl('DB' + s_, 0.016, 0.16, steel)]
        for sx in (-1, 1):
            p = _cyl('DBp%s%d' % (s_, sx), 0.06, 0.05, black); p.location = (0, 0, sx * 0.09); parts.append(p)
        PROPS['db' + s_] = _join(parts)
    # bench (flat), pull-up bar, cable line, box
    PROPS['bench'] = _join([_box('Bench', (0.32, 1.2, 0.08), (0, 0, 0), pad), _box('BenchLeg', (0.07, 0.07, 0.40), (0, 0.4, -0.22), frame), _box('BenchLeg2', (0.07, 0.07, 0.40), (0, -0.4, -0.22), frame)])
    PROPS['pullbar'] = _cyl('PullBar', 0.016, 1.4, steel); PROPS['pullbar'].rotation_euler = (0, rad(90), 0)
    PROPS['cable'] = _cyl('Cable', 0.004, 1.0, black)
    PROPS['box'] = _box('Box', (0.5, 0.5, 0.45), (0, -0.55, 0), pad)
    PROPS['kb'] = _cyl('KB', 0.09, 0.16, black)
    for s_ in 'LR': PROPS['dip' + s_] = _cyl('Dip' + s_, 0.02, 0.9, steel)
    hide_all()

def hide_all():
    for o in PROPS.values():
        for c in [o] + list(o.children_recursive): c.hide_render = True

def show(name):
    o = PROPS[name]
    for c in [o] + list(o.children_recursive): c.hide_render = False
    return o

def place_props(spec):
    """spec: list of prop names; positions derived from the posed hands."""
    hide_all()
    L, R = hand('L'), hand('R')
    for p in spec:
        if p == 'barbell':
            b = show('barbell'); b.location = (L + R) / 2; d = (L - R).normalized()
            b.rotation_euler = Vector((0, 0, 1)).rotation_difference(d).to_euler()
        elif p == 'dumbbells':
            for s, h in (('L', L), ('R', R)):
                o = show('db' + s); o.location = h; o.rotation_euler = Vector((0, 0, 1)).rotation_difference((L - R).normalized()).to_euler()
        elif p == 'barbell@hips':
            arm = M.OBJ['Rig']; pv = arm.matrix_world @ arm.pose.bones['root'].tail
            b = show('barbell'); b.location = pv + (arm.matrix_world.to_3x3() @ Vector((0, -0.16, 0))); b.rotation_euler = (0, rad(90), 0)
        elif p == 'dipbars':
            for s_, h in (('L', L), ('R', R)):
                o = show('dip' + s_); o.location = h + Vector((0, 0.05, -0.03)); o.rotation_euler = (rad(90), 0, 0)
        elif p == 'kb':
            o = show('kb'); o.location = (L + R) / 2 + Vector((0, 0, -0.08))
        elif p.startswith('bench'):
            parts = p.split(':'); z = float(parts[1]) if len(parts) > 1 else -0.42; y = float(parts[2]) if len(parts) > 2 else 0.25
            ang = float(parts[3]) if len(parts) > 3 else 0.0
            o = show('bench'); o.location = (0, y, z); o.rotation_euler = (rad(ang), 0, 0)
        elif p.startswith('seat'):
            parts = p.split(':'); z = float(parts[1]) if len(parts) > 1 else -0.55; y = float(parts[2]) if len(parts) > 2 else 0.12
            o = show('box'); o.location = (0, y, z); o.scale = (0.5, 0.5, 0.45)
        elif p.startswith('step'):
            o = show('box'); o.location = (0.12, -0.25, -0.58); o.scale = (0.5, 0.5, 0.45)
        elif p.startswith('pullbar'):
            o = show('pullbar'); o.location = ((L + R) / 2) + Vector((0, 0, 0.02))
        elif p.startswith('cable'):
            _, ax, ay, az = p.split(':'); a = Vector((float(ax), float(ay), float(az))); h = (L + R) / 2
            o = show('cable'); o.location = (a + h) / 2; d = h - a; o.scale = (1, 1, d.length)
            o.rotation_euler = Vector((0, 0, 1)).rotation_difference(d.normalized()).to_euler()
        elif p == 'box':
            o = show('box'); o.location = (0, -0.55, 0); o.scale = (0.5, 0.5, 0.45)
