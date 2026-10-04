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
    # ---- round 6: Pakistani wildlife
    'chakor':  ('#C9B7A0', '#EFE6D8', '#3B2F26', '#C8102E', '#FFFFFF'),   # chukar partridge (national bird)
    'bulhan':  ('#A7B4C2', '#F2D9DE', '#2E3B4E', '#00A3A3', '#FFFFFF'),   # Indus river dolphin
    'bhalu':   ('#8A5A3C', '#D8B48F', '#3A2416', '#F28C28', '#FFFFFF'),   # Himalayan brown bear
    'sakeen':  ('#B59870', '#EEE2CC', '#4A3622', '#6A4BC4', '#FFFFFF'),   # Himalayan ibex
    'mor':     ('#1F6FD1', '#7FD3F7', '#1B2A44', '#2DBE60', '#FFE36A'),   # peacock
    'lomri':   ('#E2752C', '#FFF4E8', '#2A2A2A', '#2C7BE5', '#FFFFFF'),   # red fox
    'monal':   ('#2E8B7A', '#C7612E', '#1F2430', '#8E44AD', '#FFD54A'),   # Himalayan monal
    'bhoori':  ('#4A4E57', '#8C8F96', '#1E2024', '#E94B3C', '#FFFFFF'),   # water buffalo
    'kala':    ('#4A3226', '#FFFFFF', '#241811', '#F2B42E', '#FFFFFF'),   # blackbuck
    'sehi':    ('#7A6250', '#D9C2A8', '#2B2119', '#16A085', '#FFFFFF'),   # porcupine
    'ullu':    ('#9C7A55', '#EBDCC4', '#3A2A1C', '#34495E', '#F2C14E'),   # owl
    'gogi':    ('#6B8F4E', '#D9E4B5', '#2C3A20', '#D35400', '#FFFFFF'),   # gharial
    'nevla':   ('#A89B85', '#E8E0D0', '#3B342B', '#C0392B', '#FFFFFF'),   # mongoose
    'khargosh':('#D6B98C', '#FBF3E4', '#4B3A25', '#E84393', '#FFFFFF'),   # desert hare
    'yaku':    ('#4E3B30', '#7A6252', '#1E1712', '#1ABC9C', '#FFFFFF'),   # yak
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


def _pointy_ears(body, inner='#F7B6C2', size=1.0, spread=0.70, el=1.00, h=0.36):
    pink = P.mat_plush('earin2', inner, sheen=0.6, bump=0.1)
    for s in (1, -1):
        _on_head('Ear%d' % s, s * spread, el, 0.06 * size, (0.15 * size, 0.09 * size, h * size), body, roll=s * -0.25, tilt=0.6)
        _on_head('EarIn%d' % s, s * spread, el - 0.03, 0.10 * size, (0.09 * size, 0.05 * size, h * 0.7 * size), pink, roll=s * -0.25, tilt=0.6)

def _long_ears(body, inner='#F7B6C2'):
    pink = P.mat_plush('earin3', inner, sheen=0.6, bump=0.1)
    for s in (1, -1):
        _on_head('Ear%d' % s, s * 0.32, 1.15, 0.30, (0.14, 0.08, 0.55), body, roll=s * -0.12, tilt=1.4)
        _on_head('EarIn%d' % s, s * 0.32, 1.15, 0.33, (0.08, 0.05, 0.42), pink, roll=s * -0.12, tilt=1.4)

def _curve_horns(color, mode, bevel=0.10):
    """'back' = ibex scimitar sweep, 'side' = buffalo, 'spiral' = blackbuck (straight twisted), 'yak' = up-and-out."""
    horn = P.mat_gloss('horn2', color, rough=0.4, coat=0.3)
    hb = P.OBJ['HeadRig']
    for s in (1, -1):
        if mode == 'back':
            base, _ = P.head_point(s * 0.28, 0.85, -0.05)
            pts = [tuple(base + Vector((s * 0.06 * t, 0.55 * t * t, 0.75 * math.sin(t * 1.9)))) for t in [i / 9 for i in range(10)]]
        elif mode == 'side':
            base, _ = P.head_point(s * 0.55, 0.70, -0.05)
            pts = [tuple(base + Vector((s * 0.55 * t, 0.10 * t, 0.35 * math.sin(t * math.pi) - 0.05 * t))) for t in [i / 9 for i in range(10)]]
        elif mode == 'yak':
            base, _ = P.head_point(s * 0.55, 0.62, -0.05)
            pts = [tuple(base + Vector((s * (0.42 * t), 0.04 * t, 0.45 * t * t))) for t in [i / 9 for i in range(10)]]
        else:  # spiral
            base, _ = P.head_point(s * 0.24, 0.88, -0.05)
            pts = []
            for i in range(16):
                t = i / 15; a = t * math.tau * 3
                r = 0.05 * (1 - 0.6 * t)
                pts.append(tuple(base + Vector((s * (0.18 * t + r * math.cos(a)), 0.10 * t + r * math.sin(a), 0.85 * t))))
        c = P.curve('Horn%d' % s, pts, bevel, horn, parent=hb, res=8)
        sp = c.data.splines[0].bezier_points
        for k, bp in enumerate(sp): bp.radius = 1.0 - 0.75 * k / (len(sp) - 1)

def _beak(color, length=0.24, tip=None, hooked=False):
    b = P.mat_gloss('beak2', color, rough=0.25, coat=0.6)
    _on_head('Beak', 0.0, -0.17, -0.05, (0.12, 0.10, length), b, tilt=-0.9)
    if tip: _on_head('BeakTip', 0.0, -0.26, 0.02, (0.05, 0.04, 0.06), P.mat_gloss('tip2', tip, rough=0.3), tilt=-1.2)
    HIDE.extend(('MouthLine', 'MouthOpen', 'Tongue'))

def _long_snout(mat, length=0.55, width=0.16, teeth=False):
    _on_head('Snout', 0.0, -0.28, length * 0.55, (width, width * 0.8, length), mat, tilt=-0.25)
    if teeth:
        w = P.mat_gloss('tooth', '#FFFFFF', rough=0.3)
        for k in range(5):
            for s in (1, -1):
                _on_head('Tooth%d%d' % (k, s), s * 0.10, -0.33 - k * 0.005, length * 0.35 + k * 0.11, (0.018, 0.018, 0.035), w, tilt=-0.6)
    HIDE.extend(('MouthLine', 'MouthOpen', 'Tongue'))

def _crest(mat, n=3, tall=0.26, tilt=0.8, ball=None):
    for k in range(n):
        az = (k - (n - 1) / 2) * 0.16
        o = _on_head('Crest%d' % k, az, 1.22, -0.03, (0.045, 0.045, tall - abs(az) * 0.4), mat, tilt=tilt)
        if ball is not None:   # fan-shaped tip at the end of the feather (local +Z of the feather)
            tip = P.sphere('CrestTip%d' % k, (0, 0, 0), (1, 1, 1), ball); tip.parent = P.OBJ['HeadRig']
            tip.matrix_basis = o.matrix_basis @ Matrix.Translation((0, 0, 0.92)) @ Matrix.Diagonal((1.9, 0.8, 0.28, 1))

def _bushy_tail(mat, tipmat=None, side=1):
    pts = [(0.10 * side, 0.45, 0.45), (0.55 * side, 0.70, 0.35), (0.85 * side, 0.85, 0.75), (0.75 * side, 0.70, 1.20)]
    t = _tail(mat, pts, 0.22)
    if tipmat is not None:
        P.sphere('TailTip', (0.72 * side, 0.68, 1.30), (0.20, 0.20, 0.22), tipmat, parent=P.OBJ['Root'])
    return t

def _fan_tail(colors):
    eye = P.mat_gloss('peye', '#0B3D91', rough=0.3); ring = P.mat_gloss('pring', '#E8C33A', rough=0.3)
    for k in range(9):
        a = math.radians(-80 + k * 20)
        x = math.sin(a) * 1.15; z = 1.05 + math.cos(a) * 1.05
        m = P.mat_plush('fan%d' % k, colors[k % len(colors)], sheen=0.6, bump=0.15)
        f = P.sphere('Fan%d' % k, (x * 0.85, 0.62, z * 0.95), (0.20, 0.05, 0.62), m, parent=P.OBJ['Root'])
        f.rotation_euler = (math.radians(-10), a, 0)
        P.sphere('FanRing%d' % k, (x * 1.05, 0.58, 1.05 + math.cos(a) * 1.45), (0.14, 0.04, 0.16), ring, parent=P.OBJ['Root']).rotation_euler = (math.radians(-10), a, 0)
        P.sphere('FanEye%d' % k, (x * 1.05, 0.555, 1.05 + math.cos(a) * 1.45), (0.08, 0.03, 0.10), eye, parent=P.OBJ['Root']).rotation_euler = (math.radians(-10), a, 0)

def _quills(dark='#3A2C22', light='#F3EAD9'):
    d = P.mat_gloss('quill', dark, rough=0.5); l = P.mat_gloss('quillt', light, rough=0.5)
    for i in range(7):
        for j in range(5):
            az = math.radians(-70 + i * 23); el = 0.10 + j * 0.22
            x = math.sin(az) * 0.78 * math.cos(el); y = 0.30 + math.cos(az) * 0.34 * math.cos(el); z = 0.80 + 0.70 * math.sin(el) + j * 0.14
            q = P.sphere('Q%d_%d' % (i, j), (x, y + 0.20, z), (0.045, 0.045, 0.58 - j * 0.04), d if (i + j) % 3 else l, parent=P.OBJ['Root'])
            q.rotation_euler = (math.radians(-60 + j * 6), math.radians(math.degrees(az) * 0.9), 0)

def _fins(mat):
    P.sphere('Dorsal', (0.0, 0.50, 1.25), (0.05, 0.30, 0.28), mat, parent=P.OBJ['Root']).rotation_euler = (math.radians(30), 0, 0)
    for s in (1, -1):
        P.sphere('Fluke%d' % s, (s * 0.25, 0.62, 0.22), (0.26, 0.08, 0.10), mat, parent=P.OBJ['Root']).rotation_euler = (0, 0, math.radians(s * 25))

def _ridges(mat):
    for k in range(6):
        P.sphere('Ridge%d' % k, (0.0, 0.40 + k * 0.03, 1.3 - k * 0.17), (0.06, 0.08, 0.07), mat, parent=P.OBJ['Root'])
    _tail(mat, [(0.0, 0.45, 0.40), (0.15, 0.85, 0.25), (0.35, 1.20, 0.18)], 0.16)

def _shag(mat):
    for s in (1, -1):
        for k in range(3):
            _on_head('Cheek%d%d' % (s, k), s * (0.95 + 0.12 * k), -0.20 - 0.14 * k, 0.02, (0.16, 0.14, 0.20), mat, tilt=-0.5)
    for s in (1, -1):
        for k in range(3):
            P.sphere('Skirt%d%d' % (s, k), (s * (0.40 + 0.08 * k), -0.10 + 0.15 * k, 0.55), (0.14, 0.14, 0.26), mat, parent=P.OBJ['Root'])


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
    elif name == 'chakor':
        _beak('#D7262E', 0.26, None)
        ring = P.mat_soft_disc('cring', '#D7262E', 0.95)
        black = disc('cbar', '#1E1E1E')
        for s in (1, -1):
            _decal('Ring%d' % s, ring, s * 0.36, -0.02, 0.24, 0.29, out=0.008)
            _decal('Mask%d' % s, black, s * 0.52, -0.06, 0.20, 0.05, roll=s * -0.15)   # stripe through the eye
        _decal('Throat', disc('cthroat', '#F5EBDD'), 0.0, -0.52, 0.34, 0.20)
        _decal('Necklace', black, 0.0, -0.70, 0.46, 0.05)
        bars = disc('flank', '#3A2D24')
        for s in (1, -1):
            for k in range(4):
                o = P.sphere('Bar%d%d' % (s, k), (s * 0.58, -0.05 + k * 0.08, 0.95 - k * 0.12), (0.04, 0.10, 0.03), P.mat_plush('barm', '#2E2620', bump=0.1), parent=P.OBJ['Root'])
                o.rotation_euler = (0, math.radians(s * 25), math.radians(s * 30))
    elif name == 'bulhan':
        _long_snout(P.mat_plush('dsnout', '#B9C5D1', bump=0.1), 0.62, 0.11, teeth=False)
        _fins(M['mint'])
        _on_head('Blowhole', 0.0, 1.25, 0.0, (0.06, 0.06, 0.02), P.mat_gloss('bh', '#6B7785'), tilt=0.5)
    elif name == 'bhalu':
        _ears('round', M['mint'], inner='#6B4430', size=0.8)
        _on_head('Snout', 0.0, -0.28, 0.0, (0.26, 0.18, 0.18), M['cream'], tilt=-0.2)
        _on_head('Nose', 0.0, -0.20, 0.17, (0.08, 0.05, 0.05), P.mat_gloss('nose', '#1B1B1B', rough=0.3))
        HIDE.extend(('MouthLine',))
    elif name == 'sakeen':
        _ears('leaf', M['mint'])
        _curve_horns('#6E5A45', 'back', 0.14)
        _decal('Muzzle', disc('muz', '#EEE2CC'), 0.0, -0.27, 0.30, 0.22)
        _on_head('Nose', 0.0, -0.14, 0.0, (0.07, 0.045, 0.04), P.mat_gloss('nose', '#3B2416', rough=0.3))
        beard = P.sphere('Beard', (0, 0, 0), (0.11, 0.09, 0.20), P.mat_plush('beard', '#5E4632', bump=0.5)); beard.parent = P.OBJ['HeadRig']; beard.location = (0, -0.60, -0.62)
    elif name == 'mor':
        _beak('#C9B48A', 0.17, None)
        _crest(P.mat_plush('crest', '#1F6FD1'), 5, 0.36, 0.9, ball=P.mat_gloss('cball', '#1BA3E0', rough=0.3))
        white = disc('mwhite', '#FFFFFF')
        for s in (1, -1):
            _decal('Stripe%d' % s, white, s * 0.40, 0.16, 0.20, 0.05, roll=s * 0.2)
        _fan_tail(['#1E8F4E', '#2BAA5E', '#178045'])
    elif name == 'lomri':
        _pointy_ears(M['mint'], '#2A2A2A', 1.0)
        _decal('Muzzle', disc('muz', '#FFFFFF'), 0.0, -0.30, 0.38, 0.26)
        _on_head('Snout', 0.0, -0.24, 0.0, (0.15, 0.12, 0.16), M['cream'], tilt=-0.2)
        _on_head('Nose', 0.0, -0.18, 0.15, (0.06, 0.04, 0.04), P.mat_gloss('nose', '#1B1B1B', rough=0.3))
        _bushy_tail(M['mint'], P.mat_plush('ttip', '#FFFFFF'))
        black = P.mat_plush('sock', '#2A2A2A')
        for k in ('LegL', 'LegR'): P.OBJ[k].data.materials[0] = black
        HIDE.extend(('MouthLine',))
    elif name == 'monal':
        _beak('#4A4038', 0.17)
        _crest(P.mat_gloss('mcrest', '#2E8B7A', rough=0.3), 3, 0.28, 0.7, ball=P.mat_gloss('mball', '#36C2A8', rough=0.25))
        ring = disc('mring', '#2FA6E0')
        for s in (1, -1): _decal('Ring%d' % s, ring, s * 0.36, -0.02, 0.24, 0.29, out=0.008)
        sheen = P.MAT['mint'].node_tree.nodes['Principled BSDF']; sheen.inputs['Coat Weight'].default_value = 0.8; sheen.inputs['Roughness'].default_value = 0.35
        tail = P.mat_plush('mtail', '#C7612E', bump=0.2)
        P.sphere('TailFan', (0, 0.55, 0.55), (0.40, 0.08, 0.32), tail, parent=P.OBJ['Root']).rotation_euler = (math.radians(-50), 0, 0)
    elif name == 'bhoori':
        _ears('leaf', M['mint'], inner='#7A6E6E')
        _curve_horns('#2B2B2E', 'side', 0.12)
        _on_head('Snout', 0.0, -0.30, 0.0, (0.30, 0.20, 0.20), P.mat_plush('bsnout', '#6E727B', bump=0.2), tilt=-0.2)
        for s in (1, -1): _on_head('Nostril%d' % s, s * 0.09, -0.27, 0.19, (0.04, 0.02, 0.025), P.mat_gloss('nos', '#1E1E1E'), roll=s * 0.5)
        HIDE.extend(('MouthLine',))
    elif name == 'kala':
        _ears('leaf', M['mint'], inner='#E9D7CB')
        _curve_horns('#1E1A18', 'spiral', 0.06)
        white = disc('kwhite', '#FFFFFF')
        for s in (1, -1): _decal('Ring%d' % s, white, s * 0.36, -0.02, 0.26, 0.31, out=0.006)
        _decal('Muzzle', white, 0.0, -0.30, 0.28, 0.20)
        _on_head('Nose', 0.0, -0.15, 0.0, (0.06, 0.04, 0.04), P.mat_gloss('nose', '#1B1B1B', rough=0.3))
    elif name == 'sehi':
        _ears('round', M['mint'], inner='#C99A86', size=0.6)
        _on_head('Snout', 0.0, -0.26, 0.0, (0.16, 0.13, 0.16), M['cream'], tilt=-0.2)
        _on_head('Nose', 0.0, -0.20, 0.15, (0.06, 0.04, 0.04), P.mat_gloss('nose', '#3B2A20', rough=0.3))
        _quills()
        HIDE.extend(('MouthLine',))
    elif name == 'ullu':
        disk = disc('fdisc', '#EBDCC4', 0.99, 0.10)
        for s in (1, -1): _decal('Disc%d' % s, disk, s * 0.34, -0.04, 0.34, 0.40, out=0.004)
        _beak('#5A4A3A', 0.14)
        tuft = M['mint']
        for s in (1, -1): _on_head('Tuft%d' % s, s * 0.62, 0.95, 0.02, (0.08, 0.08, 0.26), tuft, roll=s * -0.3, tilt=0.6)
        _spots(M['cream'], '#B49A78', 8.0)
    elif name == 'gogi':
        _long_snout(P.mat_plush('gsnout', '#7FA35E', bump=0.25), 0.66, 0.10, teeth=True)
        _ridges(P.mat_plush('ridge', '#4E6E38', bump=0.4))
        for s in (1, -1): _on_head('Brow%d' % s, s * 0.36, 0.32, 0.0, (0.15, 0.10, 0.10), M['mint'])
    elif name == 'nevla':
        _ears('round', M['mint'], inner='#C9B9A6', size=0.55)
        _on_head('Snout', 0.0, -0.22, 0.0, (0.16, 0.12, 0.20), M['cream'], tilt=-0.2)
        _on_head('Nose', 0.0, -0.16, 0.19, (0.05, 0.035, 0.035), P.mat_gloss('nose', '#3B2A20', rough=0.3))
        _tail(M['mint'], [(0.10, 0.45, 0.40), (0.60, 0.80, 0.30), (0.95, 0.95, 0.55), (1.05, 0.80, 0.95)], 0.12)
        _spots(M['mint'], '#8E826E', 18.0)
        HIDE.extend(('MouthLine',))
    elif name == 'khargosh':
        _long_ears(M['mint'], '#F4B9C6')
        _decal('Muzzle', disc('muz', '#FFFFFF'), 0.0, -0.28, 0.26, 0.20)
        _on_head('Nose', 0.0, -0.15, 0.0, (0.05, 0.035, 0.035), P.mat_gloss('nose', '#E58A9A', rough=0.3))
        P.sphere('Puff', (0, 0.58, 0.45), (0.18, 0.16, 0.18), P.mat_plush('puff', '#FFFFFF', bump=0.5), parent=P.OBJ['Root'])
    elif name == 'yaku':
        _curve_horns('#E8E2D4', 'yak', 0.13)
        _shag(P.mat_plush('shag', '#3E2E25', bump=0.8))
        _on_head('Snout', 0.0, -0.30, 0.0, (0.26, 0.18, 0.18), M['cream'], tilt=-0.2)
        for s in (1, -1): _on_head('Nostril%d' % s, s * 0.08, -0.27, 0.17, (0.035, 0.02, 0.02), P.mat_gloss('nos', '#1E1E1E'), roll=s * 0.5)
        HIDE.extend(('MouthLine',))
    return name

VIEWS = {'kami': -38, 'bulhan': -34, 'mor': -12, 'lomri': -30, 'sehi': -34, 'gogi': -40, 'nevla': -30, 'monal': -30}   # per-character 3/4 angle (camel turned more so the hump shows)

def portrait_pose():
    return dict(eyes='open', mouth='smile', brows='normal', blush=0.8, armL=16, armR=16, fwdL=10, fwdR=10, yaw=6, tilt=3)
