"""Arena cast: Pip's plush rig re-skinned as five Pakistani animals (same 3D style, same poses/animations).
usage (bpy): import pipgen as P, cast as C; P.build(); C.skin('zara')"""
import math, bpy
from mathutils import Vector, Matrix
import pipgen as P

CAST = {
    #        body        belly       shoes      band base  band stripe
    'zara':    ('#E9ECEF', '#FFFFFF', '#2F3A45', '#2E6FD8', '#FFFFFF'),   # snow leopard
    'taj':     ('#C99A6B', '#F3E3C8', '#4A2E1A', '#0E8F4A', '#FFFFFF'),   # markhor
    'kami':    ('#E3B977', '#F6E4C2', '#7A4A20', '#E0662B', '#FFF4D6'),   # camel
    'shaheen': ('#7D93AC', '#F4EEDF', '#26303C', '#F2B42E', '#1E2A38'),   # falcon
    'motu':    ('#F7F7F5', '#FFFFFF', '#1B1B1B', '#E2445C', '#FFFFFF'),   # panda
}

def _color(m, hexc):
    m.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = P.srgb(hexc)

def _band(base, stripe):
    E = next(n for n in P.MAT['band'].node_tree.nodes if n.type == 'VALTORGB').color_ramp.elements
    E[0].color = P.srgb(base); E[1].color = P.srgb(stripe); E[2].color = P.srgb(base)

def _spots(m, spot='#6E747C', scale=5.5):
    """Leopard rosettes: Voronoi cells → dark rings mixed over the base colour (object space, so they ride the body)."""
    nt = m.node_tree; n = nt.nodes; l = nt.links; b = n['Principled BSDF']
    base = tuple(b.inputs['Base Color'].default_value)
    tc = n.new('ShaderNodeTexCoord'); v = n.new('ShaderNodeTexVoronoi'); v.feature = 'F1'; v.inputs['Scale'].default_value = scale
    v.inputs['Randomness'].default_value = 0.9
    l.new(tc.outputs['Object'], v.inputs['Vector'])
    r = n.new('ShaderNodeValToRGB'); r.color_ramp.interpolation = 'EASE'
    e = r.color_ramp.elements; e[0].position = 0.24; e[0].color = P.srgb(spot); e[1].position = 0.30; e[1].color = base
    l.new(v.outputs['Distance'], r.inputs['Fac']); l.new(r.outputs['Color'], b.inputs['Base Color'])

def disc(name, color, alpha=0.97, edge=0.12):
    """Crisp decal material (soft only in the outer [edge] of the radius) — for patches, muzzles, stripes."""
    m = P.mat_soft_disc(name, color, alpha)
    r = next(n for n in m.node_tree.nodes if n.type == 'VALTORGB')
    r.color_ramp.elements[1].position = edge
    m.node_tree.nodes['Principled BSDF'].inputs['Sheen Weight'].default_value = 0.2
    return m

def _decal(name, mat, az, el, sx, sy, out=0.015, roll=0.0):
    """Flat soft disc lying on the head surface (eye patches, muzzle, malar stripes)."""
    bpy.ops.mesh.primitive_circle_add(vertices=40, radius=1.0, fill_type='NGON', location=(0, 0, 0))
    o = bpy.context.active_object; o.name = name; o.data.materials.append(mat); o.parent = P.OBJ['HeadRig']
    p, n = P.head_point(az, el, out)
    z = n.normalized(); x = Vector((0, 0, 1)).cross(z).normalized(); y = z.cross(x)
    m = Matrix((x, y, z)).transposed().to_4x4() @ Matrix.Rotation(roll, 4, 'Z')
    o.matrix_basis = Matrix.Translation(p) @ m @ Matrix.Diagonal((sx, sy, 1, 1))
    P.OBJ[name] = o
    return o

def _on_head(name, az, el, out, scale, mat, roll=0.0, tilt=0.0):
    """Ellipsoid sitting on the head, its local Z along the surface normal."""
    o = P.sphere(name, (0, 0, 0), (1, 1, 1), mat); o.parent = P.OBJ['HeadRig']
    p, n = P.head_point(az, el, out)
    nn = (n + Vector((0, 0, tilt))).normalized()
    z = nn; x = Vector((0, 0, 1)).cross(z)
    if x.length < 1e-4: x = Vector((1, 0, 0))
    x.normalize(); y = z.cross(x)
    m = Matrix((x, y, z)).transposed().to_4x4() @ Matrix.Rotation(roll, 4, 'Z')
    o.matrix_basis = Matrix.Translation(p) @ m @ Matrix.Diagonal((scale[0], scale[1], scale[2], 1))
    return o

def _ears(kind, body, inner='#F7B6C2', size=1.0):
    pink = P.mat_plush('earin', inner, sheen=0.6, bump=0.1)
    for s in (1, -1):
        if kind == 'round':      # cat / panda
            _on_head('Ear%d' % s, s * 0.66, 1.02, 0.02, (0.30 * size, 0.30 * size, 0.16 * size), body, tilt=0.35)
            _on_head('EarIn%d' % s, s * 0.66, 1.02, 0.14, (0.17 * size, 0.17 * size, 0.06 * size), pink, tilt=0.35)
        elif kind == 'leaf':     # goat: long ears out to the side
            _on_head('Ear%d' % s, s * 1.25, 0.05, 0.06, (0.36, 0.14, 0.08), body, roll=s * -0.35)
            _on_head('EarIn%d' % s, s * 1.25, 0.05, 0.12, (0.25, 0.08, 0.035), pink, roll=s * -0.35)
        elif kind == 'small':    # camel: little rounded ears high on the sides
            _on_head('Ear%d' % s, s * 0.78, 1.10, 0.0, (0.13, 0.22, 0.10), body, roll=s * 0.4, tilt=0.4)
            _on_head('EarIn%d' % s, s * 0.78, 1.10, 0.08, (0.07, 0.13, 0.04), pink, roll=s * 0.4, tilt=0.4)

def _horns():
    horn = P.mat_gloss('horn', '#5C3A22', rough=0.35, coat=0.4)
    hb = P.OBJ['HeadRig']
    for s in (1, -1):
        base, n = P.head_point(s * 0.30, 0.82, -0.05)
        pts = []
        for i in range(18):                      # tight corkscrew, up and outward
            t = i / 17; a = t * math.tau * 2.4
            r = 0.11 * (1 - 0.5 * t)
            pts.append(tuple(base + Vector((s * (0.05 + 0.30 * t + r * math.cos(a)), r * math.sin(a), 0.02 + 0.78 * t))))
        c = P.curve('Horn%d' % s, pts, 0.10, horn, parent=hb, res=8)
        # taper the bevel toward the tip
        for k, bp in enumerate(c.data.splines[0].bezier_points): bp.radius = 1.0 - 0.8 * k / 17

def _tail(mat, pts, bevel=0.13, tip=None):
    c = P.curve('Tail', pts, bevel, mat, parent=P.OBJ['Root'], res=10)
    for k, bp in enumerate(c.data.splines[0].bezier_points): bp.radius = 1.0 - 0.25 * k / max(1, len(pts) - 1)
    return c

HIDE = []   # objects the face rig re-shows on every pose but this species shouldn't have

def after_pose():
    for k in HIDE: P.OBJ[k].hide_render = True

def skin(name):
    HIDE.clear()
    body, belly, shoes, band, stripe = CAST[name]
    M = P.MAT
    _color(M['mint'], body); _color(M['cream'], belly); _color(M['navy'], shoes); _band(band, stripe)
    P.OBJ['Antenna'].hide_render = True
    if name == 'zara':
        _spots(M['mint'], '#59606A', 3.2)
        _ears('round', M['mint'], size=0.85)
        _decal('Muzzle', disc('muz', '#FFFFFF'), 0.0, -0.26, 0.30, 0.22)
        _on_head('Nose', 0.0, -0.13, 0.0, (0.07, 0.05, 0.045), P.mat_gloss('nose', '#E58A9A', rough=0.3))
        _tail(M['mint'], [(0.10, 0.45, 0.45), (0.45, 0.75, 0.40), (0.75, 0.80, 0.85), (0.70, 0.65, 1.30)], 0.14)
    elif name == 'taj':
        _ears('leaf', M['mint'])
        _horns()
        _decal('Muzzle', disc('muz', '#F3E3C8'), 0.0, -0.27, 0.30, 0.22)
        _on_head('Nose', 0.0, -0.14, 0.0, (0.07, 0.045, 0.04), P.mat_gloss('nose', '#3B2416', rough=0.3))
        beard = P.sphere('Beard', (0, -0.62, 1.10), (0.13, 0.10, 0.20), P.mat_plush('beard', '#8A5E3A', bump=0.5), parent=P.OBJ['HeadRig'])
        beard.parent = P.OBJ['HeadRig']; beard.location = (0, -0.60, -0.58); beard.scale = (0.10, 0.08, 0.16)
    elif name == 'kami':
        _ears('small', M['mint'])
        for s in (1, -1):
            _on_head('Nostril%d' % s, s * 0.085, -0.22, 0.27, (0.045, 0.02, 0.022), P.mat_gloss('nos', '#6B4426', rough=0.4), roll=s * 0.5)
            # long lashes above each eye
            pts = [P.head_point(s * 0.36 + d, 0.215 + abs(d) * -0.3, 0.05)[0] for d in (-0.12, 0.0, 0.12)]
            P.curve('Lash%d' % s, [tuple(p) for p in pts], 0.026, M['brow'], parent=P.OBJ['HeadRig'])
        tuft = P.mat_plush('tuft', '#C99A5A', bump=0.6)
        for k, (az, el) in enumerate(((0.0, 1.22), (0.22, 1.12), (-0.22, 1.12))):
            _on_head('Tuft%d' % k, az, el, -0.02, (0.13, 0.13, 0.15), tuft)
        P.sphere('Hump', (0.30, 0.38, 1.30), (0.38, 0.36, 0.42), M['mint'], parent=P.OBJ['Root'])
        _on_head('Snout', 0.0, -0.30, -0.02, (0.34, 0.24, 0.30), M['cream'], tilt=-0.15)
        P.curve('SnoutSmile', [tuple(P.head_point(a, b, 0.26)[0]) for a, b in ((-0.12, -0.42), (0.0, -0.47), (0.12, -0.42))], 0.02, M['brow'], parent=P.OBJ['HeadRig'])
        HIDE.extend(('MouthLine', 'MouthOpen', 'Tongue'))
    elif name == 'shaheen':
        beak = P.mat_gloss('beak', '#F2B42E', rough=0.25, coat=0.6)
        b = _on_head('Beak', 0.0, -0.17, -0.05, (0.13, 0.11, 0.24), beak, tilt=-0.9)
        _on_head('BeakTip', 0.0, -0.26, 0.02, (0.05, 0.04, 0.06), P.mat_gloss('tip', '#2A2A2A', rough=0.3), tilt=-1.2)
        dark = disc('malar', '#2B3442')
        for s in (1, -1):
            _decal('Malar%d' % s, dark, s * 0.44, -0.30, 0.07, 0.17, roll=s * 0.25)
            _decal('Ring%d' % s, P.mat_soft_disc('ring', '#F2C64A', 0.9), s * 0.36, -0.02, 0.24, 0.29, out=0.008)
        crest = M['mint']
        for k, az in enumerate((-0.12, 0.0, 0.12)):
            _on_head('Crest%d' % k, az * 1.4, 1.22, -0.03, (0.08, 0.08, 0.24 - abs(az)), crest, tilt=0.8)
        # streaky chest
        _spots(M['cream'], '#C9B9A0', 9.0)
        tail = P.mat_plush('tailf', '#5E7189', bump=0.3)
        for k, x in enumerate((-0.14, 0.0, 0.14)):
            P.sphere('Feather%d' % k, (x, 0.52, 0.42), (0.10, 0.05, 0.30), tail, parent=P.OBJ['Root']).rotation_euler = (math.radians(-55), 0, math.radians(x * 120))
        HIDE.extend(('MouthLine', 'MouthOpen', 'Tongue'))
    elif name == 'motu':
        black = P.mat_plush('black', '#1E1E1E', sheen=0.8, bump=0.3)
        for k in ('ArmL', 'ArmR', 'LegL', 'LegR'):
            P.OBJ[k].data.materials[0] = black
        _ears('round', black, inner='#2A2A2A', size=0.9)
        patch = disc('patch', '#171717', 0.99, 0.08)
        for s in (1, -1):
            _decal('Patch%d' % s, patch, s * 0.39, -0.07, 0.29, 0.36, out=0.012, roll=s * -0.5)
        _on_head('Nose', 0.0, -0.15, 0.0, (0.08, 0.05, 0.05), P.mat_gloss('nose', '#1B1B1B', rough=0.3))
    return name

VIEWS = {'kami': -38}   # per-character 3/4 angle (camel turned more so the hump shows)

def portrait_pose():
    return dict(eyes='open', mouth='smile', brows='normal', blush=0.8, armL=16, armR=16, fwdL=10, fwdR=10, yaw=6, tilt=3)
