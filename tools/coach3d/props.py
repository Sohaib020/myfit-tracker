"""Small props the coaches hold, built procedurally (no outside assets, no brand marks): dumbbell, meal plate,
water bottle, tablet. attach() parents a prop to a hand bone with a hand-tuned offset."""
import bpy, math
from mathutils import Vector, Matrix, Euler


def _mat(name, color, rough=0.5, metal=0.0, transmission=0.0, emission=None):
    m = bpy.data.materials.get(name)
    if m: return m
    m = bpy.data.materials.new(name); m.use_nodes = True
    b = m.node_tree.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (*color, 1); b.inputs['Roughness'].default_value = rough; b.inputs['Metallic'].default_value = metal
    if transmission: b.inputs['Transmission Weight'].default_value = transmission
    if emission:
        b.inputs['Emission Color'].default_value = (*emission, 1); b.inputs['Emission Strength'].default_value = 1.2
    return m


def _join(objs, name):
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs: o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join(); o = bpy.context.active_object; o.name = name; o['prop'] = 1
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)   # attach() sets the full matrix later
    bpy.ops.object.shade_smooth()
    return o


def _cyl(r, d, loc, rot=(0, 0, 0), mat=None, verts=32):
    bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=d, location=loc, rotation=rot, vertices=verts)
    o = bpy.context.active_object
    if mat: o.data.materials.append(mat)
    bev = o.modifiers.new('b', 'BEVEL'); bev.width = min(r, d) * 0.12; bev.segments = 3
    bpy.ops.object.modifier_apply(modifier='b')
    return o


def dumbbell():
    steel = _mat('steel', (0.62, 0.64, 0.68), 0.28, 1.0); rubber = _mat('rubber', (0.05, 0.05, 0.055), 0.6)
    parts = [_cyl(0.016, 0.16, (0, 0, 0), (0, math.pi / 2, 0), steel)]
    for x in (-0.11, 0.11):
        bpy.ops.mesh.primitive_cylinder_add(radius=0.055, depth=0.07, vertices=6, location=(x, 0, 0), rotation=(0, math.pi / 2, 0))
        o = bpy.context.active_object; o.data.materials.append(rubber)
        bev = o.modifiers.new('b', 'BEVEL'); bev.width = 0.006; bev.segments = 3; bpy.ops.object.modifier_apply(modifier='b')
        parts.append(o)
    return _join(parts, 'dumbbell')


def plate():
    cer = _mat('ceramic', (0.95, 0.95, 0.93), 0.18); rice = _mat('rice', (0.96, 0.93, 0.84), 0.7)
    greens = _mat('greens', (0.22, 0.55, 0.18), 0.6); chick = _mat('chicken', (0.78, 0.48, 0.22), 0.55); tom = _mat('tomato', (0.85, 0.15, 0.1), 0.35)
    parts = [_cyl(0.13, 0.016, (0, 0, 0), mat=cer, verts=48)]
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.055, location=(-0.035, 0.02, 0.018)); r = bpy.context.active_object; r.scale.z = 0.45; r.data.materials.append(rice); parts.append(r)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.045, location=(0.05, 0.03, 0.02)); g = bpy.context.active_object; g.scale = (1, 1.2, 0.5); g.data.materials.append(greens); parts.append(g)
    for i, (x, y) in enumerate(((0.02, -0.055), (0.06, -0.035), (-0.02, -0.06))):
        bpy.ops.mesh.primitive_cube_add(size=0.035, location=(x, y, 0.02), rotation=(0, 0, i)); k = bpy.context.active_object; k.scale = (1.2, 0.8, 0.5); k.data.materials.append(chick)
        bev = k.modifiers.new('b', 'BEVEL'); bev.width = 0.006; bev.segments = 3; bpy.ops.object.modifier_apply(modifier='b'); parts.append(k)
    for x, y in ((0.08, 0.0), (0.07, 0.065)):
        bpy.ops.mesh.primitive_uv_sphere_add(radius=0.012, location=(x, y, 0.02)); t = bpy.context.active_object; t.data.materials.append(tom); parts.append(t)
    return _join(parts, 'plate')


def bottle():
    clear = _mat('bottle', (0.75, 0.9, 1.0), 0.05, transmission=0.85); cap = _mat('cap', (0.1, 0.75, 0.6), 0.35)
    water = _mat('water', (0.55, 0.8, 0.95), 0.05, transmission=0.6)
    return _join([_cyl(0.034, 0.2, (0, 0, 0), mat=clear), _cyl(0.031, 0.15, (0, 0, -0.02), mat=water), _cyl(0.022, 0.03, (0, 0, 0.115), mat=cap)], 'bottle')


def tablet():
    body = _mat('tablet', (0.12, 0.13, 0.15), 0.35, 0.6); screen = _mat('screen', (0.05, 0.12, 0.1), 0.2, emission=(0.1, 0.55, 0.42))
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 0)); b = bpy.context.active_object; b.scale = (0.17, 0.24, 0.008); b.data.materials.append(body)
    bev = b.modifiers.new('b', 'BEVEL'); bev.width = 0.004; bev.segments = 4; bpy.ops.object.modifier_apply(modifier='b')
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 0, 0.0042)); s = bpy.context.active_object; s.scale = (0.155, 0.22, 1); s.data.materials.append(screen)
    return _join([b, s], 'tablet')


def phone():
    body = _mat('phone', (0.08, 0.09, 0.1), 0.3, 0.6); screen = _mat('pscreen', (0.05, 0.12, 0.1), 0.2, emission=(0.1, 0.55, 0.42))
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 0)); b = bpy.context.active_object; b.scale = (0.072, 0.15, 0.008); b.data.materials.append(body)
    bev = b.modifiers.new('b', 'BEVEL'); bev.width = 0.006; bev.segments = 4; bpy.ops.object.modifier_apply(modifier='b')
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 0, 0.0042)); s = bpy.context.active_object; s.scale = (0.064, 0.138, 1); s.data.materials.append(screen)
    return _join([b, s], 'phone')


MAKERS = {'dumbbell': dumbbell, 'plate': plate, 'bottle': bottle, 'tablet': tablet, 'phone': phone}

# world-space placement at the clip's hold frame: (palm point along the hand 0..1, offset from it in world metres,
# world rotation euler deg). The prop is then parented to the hand, so it follows every later move naturally.
PLACE = {
    'dumbbell': (0.55, (0, 0, 0), None),          # None = follow the hand's own orientation (grip axis across the fist)
    'plate': (0.62, (0, 0, 0.035), (0, 0, 0)),    # lying flat on the palm
    'bottle': (0.8, (0, 0, 0), 'grip'),           # mocap drinking: bottle axis through the fist, follows the hand
    'phone': (0.7, (0, 0, 0), 'face'),            # mocap texting: in the hand, screen turned to her eyes
    'tablet': (0.7, (0, -0.02, 0.05), (-58, 0, 0)),
}


import os
GRIP_AXIS = os.environ.get('GRIP_AXIS', 'z')   # z: upright in the fist, tips to the lips (checked on the Drinking mocap)
PALM_OFF = {'bottle': 0.0, 'phone': float(os.environ.get('PHONE_OFF', '-0.03'))}       # metres along the hand's z axis (sign chosen from the preview)


def attach(name, arm, bone, frame=0):
    o = MAKERS[name]()
    o['prop'] = 1
    scene = bpy.context.scene
    scene.frame_set(frame)
    pb = arm.pose.bones[bone]
    hm = arm.matrix_world @ pb.matrix
    head = hm.translation; tail = arm.matrix_world @ pb.tail
    along, off, rot = PLACE[name]
    pos = head.lerp(tail, along) + Vector(off)
    if rot == 'face':
        H = hm.to_quaternion().to_matrix(); y = H.col[1]
        eye = arm.matrix_world @ arm.pose.bones['Head'].head + Vector((0, -0.08, 0.06))
        zz = (eye - pos).normalized()                       # screen normal looks at the face
        yy = (y - zz * y.dot(zz)).normalized()               # long side along the hand, square to the screen
        M = Matrix.Identity(3); M.col[0] = yy.cross(zz); M.col[1] = yy; M.col[2] = zz
        R = M.to_4x4()
        pos = pos + zz * 0.012
    elif rot in ('grip', 'palm'):
        H = hm.to_quaternion().to_matrix()
        x, y, z = H.col[0], H.col[1], H.col[2]          # hand bone: y along the hand, x/z across it
        if rot == 'grip':                                # prop Z -> across the fist (bone x), prop Y -> along the hand
            M = Matrix.Identity(3)
            ax = {'x': x, '-x': -x, 'z': z, '-z': -z}[GRIP_AXIS]; M.col[2] = ax; M.col[1] = y; M.col[0] = y.cross(ax).normalized()
            # (columns = where the prop's X, Y, Z axes go)
        else:                                            # prop Z -> palm normal (bone z), prop Y -> along the fingers
            M = Matrix.Identity(3); M.col[0] = x; M.col[1] = y; M.col[2] = z
        R = M.to_4x4()
        pos = pos + (z * PALM_OFF.get(name, 0.0))
    elif rot is None:
        R = hm.to_quaternion().to_matrix().to_4x4()
        # dumbbell handle runs across the fist (perpendicular to the hand bone and the palm normal)
        R = R @ Matrix.Rotation(math.radians(90), 4, 'Z')
    else:
        R = Euler([math.radians(a) for a in rot]).to_matrix().to_4x4()
    o.matrix_world = Matrix.Translation(pos) @ R
    bpy.context.view_layer.update()
    W = o.matrix_world.copy()
    o.parent = arm; o.parent_type = 'BONE'; o.parent_bone = bone
    bone_tail_world = arm.matrix_world @ pb.matrix @ Matrix.Translation((0, pb.bone.length, 0))
    o.matrix_parent_inverse = bone_tail_world.inverted()
    o.matrix_basis = W
    return o
