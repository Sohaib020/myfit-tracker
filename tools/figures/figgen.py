"""Collectible figures: premium articulated 3D athletes built from clean procedural parts on a simple armature.
Every part is a rigid piece bound to one bone (like a high-end action figure), so animation never stretches or tears.
usage (bpy): import figgen as F; F.build(spec); F.dress(spec, level); F.pose('flex'); F.render(path)"""
import bpy, math
from mathutils import Vector, Matrix, Euler

S = None
M = {}
PARTS = []

def srgb(h):
    h = h.lstrip('#'); c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c) + (1.0,)

# ------------------------------------------------------------------ materials
def mat(name, color, rough=0.45, metal=0.0, coat=0.0, sss=0.0, sheen=0.0, emit=0.0, bump=0.0, bscale=200):
    m = bpy.data.materials.new(name); m.use_nodes = True; nt = m.node_tree; b = nt.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = srgb(color); b.inputs['Roughness'].default_value = rough
    b.inputs['Metallic'].default_value = metal; b.inputs['Coat Weight'].default_value = coat
    if sss: b.inputs['Subsurface Weight'].default_value = sss; b.inputs['Subsurface Radius'].default_value = (0.9, 0.35, 0.2); b.inputs['Subsurface Scale'].default_value = 0.02
    if sheen: b.inputs['Sheen Weight'].default_value = sheen; b.inputs['Sheen Roughness'].default_value = 0.4
    if emit: b.inputs['Emission Color'].default_value = srgb(color); b.inputs['Emission Strength'].default_value = emit
    if bump:
        tx = nt.nodes.new('ShaderNodeTexNoise'); tx.inputs['Scale'].default_value = bscale; tx.inputs['Detail'].default_value = 8
        bm = nt.nodes.new('ShaderNodeBump'); bm.inputs['Strength'].default_value = bump; bm.inputs['Distance'].default_value = 0.004
        nt.links.new(tx.outputs['Fac'], bm.inputs['Height']); nt.links.new(bm.outputs['Normal'], b.inputs['Normal'])
    M[name] = m; return m

def stripe_mat(name, base, stripe, axis='X', at=0.9, width=0.06):
    """Fabric with a racing stripe along a limb (object-space coordinate band)."""
    m = mat(name, base, rough=0.8, sheen=0.5, bump=0.25, bscale=420)
    nt = m.node_tree; n = nt.nodes; b = n['Principled BSDF']
    tc = n.new('ShaderNodeTexCoord'); sep = n.new('ShaderNodeSeparateXYZ'); nt.links.new(tc.outputs['Object'], sep.inputs[0])
    r = n.new('ShaderNodeValToRGB'); r.color_ramp.interpolation = 'CONSTANT'; e = r.color_ramp.elements
    e[0].position = 0.0; e[0].color = srgb(base); e[1].position = 0.5 + (at - width) / 2; e[1].color = srgb(stripe)
    e2 = r.color_ramp.elements.new(0.5 + at / 2); e2.color = srgb(base)
    mp = n.new('ShaderNodeMapRange'); mp.inputs['From Min'].default_value = -1; mp.inputs['From Max'].default_value = 1
    nt.links.new(sep.outputs[axis], mp.inputs['Value']); nt.links.new(mp.outputs['Result'], r.inputs['Fac'])
    nt.links.new(r.outputs['Color'], b.inputs['Base Color'])
    return m

# ------------------------------------------------------------------ primitives (all smooth + subdivided)
def _finish(o, m, sub=2, bevel=0.0):
    if bevel:
        bv = o.modifiers.new('bev', 'BEVEL'); bv.width = bevel; bv.segments = 3; bv.limit_method = 'ANGLE'
    if sub:
        sd = o.modifiers.new('sub', 'SUBSURF'); sd.levels = 1; sd.render_levels = sub
    for p in o.data.polygons: p.use_smooth = True
    o.data.materials.append(m); PARTS.append(o); return o

def blob(name, loc, scale, m, rot=(0, 0, 0), seg=32, sub=1):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=seg // 2, location=loc, rotation=rot)
    o = bpy.context.active_object; o.name = name; o.scale = scale; return _finish(o, m, sub)

def taper(name, a, b, ra, rb, m, flat=1.0, sub=2, verts=24):
    """Rounded tapered limb segment from point a to point b (radius ra at a, rb at b)."""
    a, b = Vector(a), Vector(b); d = b - a
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=ra, radius2=rb, depth=d.length, location=(a + b) / 2)
    o = bpy.context.active_object; o.name = name
    o.rotation_mode = 'QUATERNION'; o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
    o.scale = (1.0, flat, 1.0)
    return _finish(o, m, sub, bevel=min(ra, rb) * 0.6)

def ring(name, loc, r, minor, m, rot=(0, 0, 0), scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_torus_add(major_radius=r, minor_radius=minor, major_segments=64, minor_segments=14, location=loc, rotation=rot)
    o = bpy.context.active_object; o.name = name; o.scale = scale; return _finish(o, m, 0)

def tube(name, pts, bevel, m):
    cu = bpy.data.curves.new(name, 'CURVE'); cu.dimensions = '3D'; cu.bevel_depth = bevel; cu.bevel_resolution = 4; cu.use_fill_caps = True
    sp = cu.splines.new('POLY' if len(pts) < 3 else 'BEZIER')
    if sp.type == 'BEZIER':
        sp.bezier_points.add(len(pts) - 1)
        for p, q in zip(sp.bezier_points, pts): p.co = q; p.handle_left_type = p.handle_right_type = 'AUTO'
    else:
        sp.points.add(len(pts) - 1)
        for p, q in zip(sp.points, pts): p.co = (*q, 1)
    o = bpy.data.objects.new(name, cu); S.collection.objects.link(o); o.data.materials.append(m); PARTS.append(o); return o

# ------------------------------------------------------------------ skeleton (heroic chunky proportions, ~4 heads tall)
J = {}
def joints(spec):
    w = spec.get('build', 1.0); hgt = spec.get('height', 1.0)
    sx = 0.36 * w if spec.get('male', True) else 0.31 * w
    hx = 0.17 if spec.get('male', True) else 0.19
    z = lambda v: v * hgt
    J.update(foot_L=Vector((0.15, 0.0, z(0.13))), knee_L=Vector((0.14, -0.02, z(0.54))), hip_L=Vector((hx, 0.0, z(0.95))),
             pelvis=Vector((0, 0, z(1.0))), chest=Vector((0, 0, z(1.30))), neck=Vector((0, 0, z(1.50))), head=Vector((0, -0.01, z(1.62))),
             shoulder_L=Vector((sx, 0.0, z(1.42))), elbow_L=Vector((sx + 0.12, 0.02, z(1.10))), wrist_L=Vector((sx + 0.20, -0.02, z(0.82))))
    for k in list(J):
        if k.endswith('_L'): J[k[:-2] + '_R'] = Vector((-J[k].x, J[k].y, J[k].z))

BONES = [('pelvis', 'pelvis', 'chest', None), ('spine', 'chest', 'neck', 'pelvis'), ('neck', 'neck', 'head', 'spine'),
         ('head', 'head', None, 'neck')]
for s in 'LR':
    BONES += [('upper_arm.' + s, 'shoulder_' + s, 'elbow_' + s, 'spine'), ('forearm.' + s, 'elbow_' + s, 'wrist_' + s, 'upper_arm.' + s),
              ('hand.' + s, 'wrist_' + s, None, 'forearm.' + s), ('thigh.' + s, 'hip_' + s, 'knee_' + s, 'pelvis'),
              ('shin.' + s, 'knee_' + s, 'foot_' + s, 'thigh.' + s), ('foot.' + s, 'foot_' + s, None, 'shin.' + s)]

def make_rig():
    bpy.ops.object.armature_add(location=(0, 0, 0)); rig = bpy.context.object; rig.name = 'rig'
    bpy.ops.object.mode_set(mode='EDIT'); eb = rig.data.edit_bones; eb.remove(eb[0])
    for n, h, t, p in BONES:
        b = eb.new(n); b.head = J[h]
        if t: b.tail = J[t]
        elif n == 'head': b.tail = J[h] + Vector((0, 0, 0.3))
        elif n.startswith('hand'): b.tail = J[h] + (J[h] - J['elbow_' + n[-1]]).normalized() * 0.12
        else: b.tail = J[h] + Vector((0, -0.18, -0.08))
        if p: b.parent = eb[p]
        b.roll = 0
    bpy.ops.object.mode_set(mode='OBJECT'); return rig

def bind(o, bone):
    rig = bpy.data.objects['rig']; mw = o.matrix_world.copy()
    o.parent = rig; o.parent_type = 'BONE'; o.parent_bone = bone
    bpy.context.view_layer.update(); o.matrix_world = mw

# ------------------------------------------------------------------ the figure
def build(spec):
    global S
    bpy.ops.wm.read_factory_settings(use_empty=True); S = bpy.context.scene; M.clear(); PARTS.clear()
    joints(spec)
    skin = mat('skin', spec['skin'], rough=0.42, sss=0.12, coat=0.15)
    hair = mat('hair', spec.get('hair_col', '#14110F'), rough=0.5, coat=0.3, bump=0.15, bscale=90)
    eye = mat('eye', '#0B0B0D', rough=0.12, coat=1.0); white = mat('eyew', '#F2EFEA', rough=0.3)
    glint = mat('glint', '#FFFFFF', emit=4.0); brow = mat('brow', spec.get('hair_col', '#14110F'), rough=0.6)
    lip = mat('lip', spec.get('lip', '#8E5444'), rough=0.5)
    rig = make_rig()
    B = {}   # parts per bone
    def add(o, bone): B.setdefault(bone, []).append(o)
    # head: sculpted from overlapping smooth volumes (cranium, jaw, cheekbones) + features
    hc = J['head'] + Vector((0, 0, 0.21))
    add(blob('cranium', hc + Vector((0, 0.01, 0.03)), (0.205, 0.225, 0.23), skin), 'head')
    jaw = 0.17 if spec.get('male', True) else 0.15
    add(blob('jaw', hc + Vector((0, -0.045, -0.10)), (jaw, 0.165, 0.15), skin), 'head')
    for s in (1, -1):
        add(blob('cheek%d' % s, hc + Vector((s * 0.095, -0.13, -0.035)), (0.07, 0.06, 0.055), skin), 'head')
        add(blob('ear%d' % s, hc + Vector((s * 0.205, 0.02, -0.01)), (0.03, 0.05, 0.065), skin), 'head')
        # eyes: almond, set into the face, slight glint; heavy determined brows
        add(blob('eyew%d' % s, hc + Vector((s * 0.078, -0.192, 0.025)), (0.046, 0.02, 0.026), white), 'head')
        add(blob('iris%d' % s, hc + Vector((s * 0.074, -0.205, 0.023)), (0.026, 0.012, 0.025), eye), 'head')
        add(blob('glint%d' % s, hc + Vector((s * 0.068, -0.215, 0.034)), (0.007, 0.004, 0.007), glint), 'head')
        add(tube('brow%d' % s, [hc + Vector((s * 0.03, -0.205, 0.058)), hc + Vector((s * 0.085, -0.208, 0.07)), hc + Vector((s * 0.128, -0.19, 0.062))], 0.014, brow), 'head')
    add(blob('nose', hc + Vector((0, -0.215, -0.02)), (0.028, 0.035, 0.05), skin, rot=(math.radians(-15), 0, 0)), 'head')
    add(tube('mouth', [hc + Vector((-0.045, -0.198, -0.105)), hc + Vector((0, -0.204, -0.112)), hc + Vector((0.048, -0.196, -0.1))], 0.0085, lip), 'head')
    # hair: chunky sculpted shapes
    hs = spec.get('hairstyle', 'undercut')
    if hs == 'undercut':
        add(blob('hair_top', hc + Vector((0, 0.0, 0.17)), (0.2, 0.21, 0.1), hair, rot=(math.radians(12), 0, 0)), 'head')
        add(blob('hair_quiff', hc + Vector((0.02, -0.12, 0.17)), (0.15, 0.1, 0.08), hair, rot=(math.radians(-25), 0, math.radians(8))), 'head')
        add(blob('hair_back', hc + Vector((0, 0.07, 0.08)), (0.21, 0.19, 0.16), hair), 'head')
    if spec.get('beard'):
        add(blob('beard', hc + Vector((0, -0.07, -0.14)), (jaw * 1.02, 0.15, 0.1), hair), 'head')
    add(taper('neck', J['neck'] - Vector((0, 0, 0.04)), J['head'] + Vector((0, 0, 0.09)), 0.095, 0.085, skin), 'neck')
    # body base (skin) — covered by outfits but present for sleeveless looks
    add(taper('torso', J['pelvis'] + Vector((0, 0, 0.06)), J['neck'], 0.2, 0.33 if spec.get('male', True) else 0.27, skin, flat=0.62), 'spine')
    add(blob('pelvisb', J['pelvis'], (0.22, 0.15, 0.13), skin), 'pelvis')
    for s in 'LR':
        sg = 1 if s == 'L' else -1
        add(blob('delt' + s, J['shoulder_' + s] + Vector((sg * 0.02, 0, 0.0)), (0.12, 0.11, 0.11), skin), 'upper_arm.' + s)
        add(taper('uarm' + s, J['shoulder_' + s], J['elbow_' + s], 0.1, 0.075, skin), 'upper_arm.' + s)
        add(blob('elbow' + s, J['elbow_' + s], (0.072,) * 3, skin), 'forearm.' + s)
        add(taper('farm' + s, J['elbow_' + s], J['wrist_' + s], 0.08, 0.06, skin), 'forearm.' + s)
        # chunky fist
        d = (J['wrist_' + s] - J['elbow_' + s]).normalized()
        add(blob('fist' + s, J['wrist_' + s] + d * 0.07, (0.075, 0.065, 0.085), skin), 'hand.' + s)
        add(blob('thumb' + s, J['wrist_' + s] + d * 0.05 + Vector((-sg * 0.03, -0.05, 0)), (0.03, 0.03, 0.045), skin), 'hand.' + s)
        add(taper('thigh' + s, J['hip_' + s], J['knee_' + s], 0.13, 0.09, skin), 'thigh.' + s)
        add(blob('knee' + s, J['knee_' + s], (0.09,) * 3, skin), 'shin.' + s)
        add(taper('shin' + s, J['knee_' + s], J['foot_' + s], 0.09, 0.065, skin), 'shin.' + s)
    S['B'] = 1
    globals()['_B'] = B
    _lights_cam()
    return B

def bind_all():
    for bone, objs in _B.items():
        for o in objs: bind(o, bone)

def sneakers(upper, sole, accent, B):
    up = mat('sn_up', upper, rough=0.5, coat=0.2); so = mat('sn_sole', sole, rough=0.4); ac = mat('sn_ac', accent, rough=0.35, coat=0.4)
    for s in 'LR':
        f = J['foot_' + s]
        B.setdefault('foot.' + s, []).extend([
            blob('shoe' + s, f + Vector((0, -0.07, -0.03)), (0.1, 0.2, 0.09), up),
            blob('collar' + s, f + Vector((0, 0.01, 0.035)), (0.085, 0.085, 0.06), up),
            blob('sole' + s, f + Vector((0, -0.075, -0.1)), (0.11, 0.215, 0.04), so),
            blob('swirl' + s, f + Vector(((1 if s == 'L' else -1) * 0.085, -0.06, -0.04)), (0.02, 0.12, 0.035), ac),
            blob('toe' + s, f + Vector((0, -0.2, -0.06)), (0.085, 0.07, 0.045), so)])

# ------------------------------------------------------------------ outfits (level 1 streetwear, 2 epic, 3 legendary)
def dress(spec, level):
    B = _B; acc = spec.get('accent', '#C8102E')
    GOLD = '#E3B24A'; LIME = '#B6FF3B'
    fab = mat('fab', '#17181C', rough=0.85, sheen=0.5, bump=0.3, bscale=380)
    fab2 = mat('fab2', '#202228', rough=0.85, sheen=0.5, bump=0.3, bscale=380)
    accm = mat('acc', acc, rough=0.7, sheen=0.4, bump=0.2, bscale=380)
    gold = mat('gold', GOLD, rough=0.22, metal=1.0, coat=0.6)
    glow = mat('glow', LIME, emit=7.0)
    plate = mat('plate', '#8E1023', rough=0.25, metal=0.55, coat=1.0)
    dark = mat('darkplate', '#1A1C21', rough=0.3, metal=0.4, coat=1.0)
    trim = gold if level >= 2 else accm
    def add(o, bone): B.setdefault(bone, []).append(o)
    jog = stripe_mat('jog', '#15161A', LIME if level >= 2 else acc, axis='X', at=0.92, width=0.12)
    # joggers (all levels) — tapered fabric over the legs with a side stripe, cuffs at the ankle
    for s in 'LR':
        sg = 1 if s == 'L' else -1
        add(taper('jogT' + s, J['hip_' + s] + Vector((0, 0, 0.06)), J['knee_' + s], 0.15, 0.115, jog), 'thigh.' + s)
        add(taper('jogS' + s, J['knee_' + s], J['foot_' + s] + Vector((0, 0, 0.05)), 0.115, 0.085, jog), 'shin.' + s)
        add(blob('jogK' + s, J['knee_' + s], (0.115,) * 3, jog), 'shin.' + s)
        add(ring('cuffL' + s, J['foot_' + s] + Vector((0, 0, 0.07)), 0.085, 0.025, trim), 'shin.' + s)
    add(blob('shorts', J['pelvis'] + Vector((0, 0, 0.02)), (0.25, 0.17, 0.15), fab2), 'pelvis')
    male = spec.get('male', True)
    if level <= 2:
        # hoodie: torso shell, hood rolled behind the neck, kangaroo pocket, drawstrings, ribbed hem; long sleeves
        add(taper('hoodie', J['pelvis'] + Vector((0, 0, 0.0)), J['neck'] + Vector((0, 0, 0.0)), 0.25, 0.37 if male else 0.31, fab, flat=0.66), 'spine')
        add(ring('hem', J['pelvis'] + Vector((0, 0, 0.02)), 0.24, 0.04, trim, scale=(1.0, 0.68, 1.0)), 'spine')
        add(blob('hood', J['neck'] + Vector((0, 0.11, -0.01)), (0.2, 0.12, 0.11), fab), 'spine')
        add(ring('collar', J['neck'] + Vector((0, 0.0, 0.0)), 0.13, 0.045, fab, scale=(1.0, 0.85, 1.0)), 'spine')
        add(blob('pocket', J['pelvis'] + Vector((0, -0.165, 0.13)), (0.17, 0.04, 0.08), fab2), 'spine')
        for sx in (1, -1):
            add(tube('string%d' % sx, [J['neck'] + Vector((sx * 0.05, -0.15, -0.02)), J['neck'] + Vector((sx * 0.055, -0.2, -0.12)), J['neck'] + Vector((sx * 0.06, -0.21, -0.22))], 0.008, gold if level >= 2 else mat('wht', '#EDEDED', rough=0.5)), 'spine')
        for s in 'LR':
            add(blob('hdelt' + s, J['shoulder_' + s], (0.135, 0.125, 0.125), fab), 'upper_arm.' + s)
            add(taper('sleeveU' + s, J['shoulder_' + s], J['elbow_' + s], 0.115, 0.095, fab), 'upper_arm.' + s)
            add(blob('sleeveE' + s, J['elbow_' + s], (0.092,) * 3, fab), 'forearm.' + s)
            add(taper('sleeveF' + s, J['elbow_' + s], J['wrist_' + s], 0.095, 0.078, fab), 'forearm.' + s)
            add(ring('wcuff' + s, J['wrist_' + s], 0.072, 0.022, trim, rot=Vector((0, 0, 1)).rotation_difference(J['wrist_' + s] - J['elbow_' + s]).to_euler()), 'forearm.' + s)
        if level == 2:
            add(ring('chain', J['neck'] + Vector((0, -0.05, -0.06)), 0.16, 0.012, gold, rot=(math.radians(-30), 0, 0), scale=(1.0, 0.9, 1.0)), 'spine')
            add(blob('medal', J['neck'] + Vector((0, -0.215, -0.17)), (0.045, 0.014, 0.045), gold), 'spine')
            add(blob('medalgem', J['neck'] + Vector((0, -0.228, -0.17)), (0.022, 0.006, 0.022), glow), 'spine')
            for sx in (1, -1):
                add(tube('seam%d' % sx, [J['chest'] + Vector((sx * 0.2, -0.2, 0.12)), J['chest'] + Vector((sx * 0.25, -0.19, -0.05)), J['pelvis'] + Vector((sx * 0.2, -0.17, 0.06))], 0.006, glow), 'spine')
    else:
        # legendary armour: fitted black undersuit, sculpted chest plate with gold rim + glowing core, pauldrons, gauntlets, greaves, belt
        add(taper('suit', J['pelvis'], J['neck'], 0.23, 0.345 if male else 0.29, fab, flat=0.64), 'spine')
        add(blob('chestplate', J['chest'] + Vector((0, -0.12, 0.05)), (0.27 if male else 0.23, 0.11, 0.18), plate), 'spine')
        add(blob('abplate', J['chest'] + Vector((0, -0.13, -0.15)), (0.17, 0.09, 0.1), dark), 'spine')
        add(ring('chestrim', J['chest'] + Vector((0, -0.19, 0.05)), 0.24, 0.016, gold, rot=(math.radians(90), 0, 0), scale=(1.0 if male else 0.85, 0.7, 1.0)), 'spine')
        add(blob('core', J['chest'] + Vector((0, -0.235, 0.07)), (0.04, 0.012, 0.04), glow), 'spine')
        add(ring('corering', J['chest'] + Vector((0, -0.232, 0.07)), 0.05, 0.01, gold, rot=(math.radians(90), 0, 0)), 'spine')
        add(ring('belt', J['pelvis'] + Vector((0, 0, 0.06)), 0.23, 0.03, gold, scale=(1.0, 0.72, 1.0)), 'pelvis')
        add(blob('buckle', J['pelvis'] + Vector((0, -0.175, 0.06)), (0.06, 0.02, 0.045), gold), 'pelvis')
        add(ring('gorget', J['neck'] + Vector((0, 0.0, -0.01)), 0.13, 0.03, gold, scale=(1.0, 0.85, 1.0)), 'spine')
        for sx in (1, -1):
            add(tube('rune%d' % sx, [J['chest'] + Vector((sx * 0.08, -0.215, -0.06)), J['chest'] + Vector((sx * 0.18, -0.2, -0.02)), J['chest'] + Vector((sx * 0.24, -0.16, 0.08))], 0.0055, glow), 'spine')
        for s in 'LR':
            sg = 1 if s == 'L' else -1
            sh = J['shoulder_' + s]; el_ = J['elbow_' + s]; wr = J['wrist_' + s]
            add(blob('paul' + s, sh + Vector((sg * 0.04, 0, 0.05)), (0.17, 0.15, 0.12), plate), 'upper_arm.' + s)
            add(blob('paul2' + s, sh + Vector((sg * 0.08, 0, -0.02)), (0.14, 0.13, 0.09), dark), 'upper_arm.' + s)
            add(ring('paulrim' + s, sh + Vector((sg * 0.05, 0, 0.0)), 0.15, 0.014, gold, rot=(0, math.radians(sg * 60), 0)), 'upper_arm.' + s)
            add(taper('armU' + s, sh, el_, 0.105, 0.085, fab), 'upper_arm.' + s)
            add(blob('armE' + s, el_, (0.085,) * 3, dark), 'forearm.' + s)
            add(taper('gaunt' + s, el_.lerp(wr, 0.3), wr, 0.092, 0.08, gold), 'forearm.' + s)
            add(taper('armF' + s, el_, wr, 0.085, 0.07, fab), 'forearm.' + s)
            add(tube('grune' + s, [el_.lerp(wr, 0.4) + Vector((0, -0.09, 0)), el_.lerp(wr, 0.6) + Vector((0, -0.088, 0)), el_.lerp(wr, 0.8) + Vector((0, -0.084, 0))], 0.005, glow), 'forearm.' + s)
            add(blob('kneeplate' + s, J['knee_' + s] + Vector((0, -0.08, 0.0)), (0.09, 0.06, 0.1), gold), 'shin.' + s)
            add(taper('greave' + s, J['knee_' + s].lerp(J['foot_' + s], 0.25), J['foot_' + s] + Vector((0, 0, 0.06)), 0.105, 0.092, dark), 'shin.' + s)
    sneakers('#111214' if level < 3 else '#1A1C21', '#F2F2F0' if level == 1 else GOLD, acc if level == 1 else LIME, B)
    # hide skin pieces that are fully covered
    covered = {'torso', 'pelvisb'} | {n + s for s in 'LR' for n in ('delt', 'uarm', 'elbow', 'farm', 'thigh', 'knee', 'shin')}
    for o in PARTS:
        if o.name in covered: o.hide_render = True

# ------------------------------------------------------------------ posing
def _pb(): return bpy.data.objects['rig'].pose.bones
def reset():
    for b in _pb(): b.rotation_mode = 'QUATERNION'; b.rotation_quaternion = (1, 0, 0, 0)
    bpy.context.view_layer.update()

def aim(n, d):
    p = _pb()[n]; bpy.context.view_layer.update()
    cur = p.matrix.copy(); y = cur.to_3x3().col[1].normalized()
    q = y.rotation_difference(Vector(d).normalized())
    m = (q.to_matrix() @ cur.to_3x3()).to_4x4(); m.translation = cur.translation; p.matrix = m
    bpy.context.view_layer.update()

def lrot(n, x=0, y=0, z=0):
    _pb()[n].rotation_quaternion = Euler((math.radians(x), math.radians(y), math.radians(z))).to_quaternion()

POSES = {   # left-side directions for upper arm / forearm (right mirrored), plus extras
    'idle': dict(up=(0.32, 0.02, -1), lo=(0.22, -0.3, -1)),
    'flex': dict(up=(1, 0.1, 0.05), lo=(0.05, 0.12, 1), head=-6),
    'victory': dict(up=(0.55, 0.05, 1), lo=(0.25, 0, 1), head=-12),
    'hero': dict(up=(0.62, 0.3, -0.75), lo=(-0.62, -0.35, -0.35)),
}
def pose(name, breath=0.0, sway=0.0, spec=None):
    reset(); P_ = spec or POSES[name]
    lrot('spine', -1.5 * breath); lrot('pelvis', 0, 0, sway * 2)
    aim('upper_arm.L', P_['up']); aim('forearm.L', P_['lo'])
    aim('upper_arm.R', (-P_['up'][0], P_['up'][1], P_['up'][2])); aim('forearm.R', (-P_['lo'][0], P_['lo'][1], P_['lo'][2]))
    if P_.get('head'): lrot('head', P_['head'])
    bpy.context.view_layer.update()

# ------------------------------------------------------------------ lights, camera, render
def _lights_cam():
    sc = S
    sc.render.engine = 'CYCLES'; sc.cycles.device = 'CPU'; sc.cycles.samples = 32; sc.cycles.use_denoising = True
    sc.render.resolution_x = 560; sc.render.resolution_y = 700; sc.view_settings.view_transform = 'AgX'
    try: sc.view_settings.look = 'AgX - Medium High Contrast'
    except Exception: pass
    w = bpy.data.worlds.new('w'); sc.world = w; w.use_nodes = True
    w.node_tree.nodes['Background'].inputs[0].default_value = (0.012, 0.014, 0.012, 1); w.node_tree.nodes['Background'].inputs[1].default_value = 1.0
    def area(name, loc, energy, size, color):
        d = bpy.data.lights.new(name, 'AREA'); d.energy = energy; d.size = size; d.color = color
        o = bpy.data.objects.new(name, d); sc.collection.objects.link(o); o.location = loc
        o.rotation_euler = (Vector((0, 0, 1.1)) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
    area('key', (-2.2, -3.4, 3.4), 520, 2.2, (1.0, 0.96, 0.92))
    area('fill', (3.0, -2.6, 1.6), 140, 3.0, (0.85, 0.9, 1.0))
    area('rimL', (-2.4, 2.2, 2.6), 650, 1.2, (0.72, 1.0, 0.3))
    area('rimR', (2.4, 2.0, 2.4), 520, 1.2, (1.0, 0.25, 0.32))
    area('top', (0, 0.4, 4.2), 160, 1.5, (1, 1, 1))
    bpy.ops.mesh.primitive_plane_add(size=30); fl = bpy.context.object; fm = mat('floor', '#0B0C0B', rough=0.55); fl.data.materials.append(fm)
    fl.is_shadow_catcher = False
    cd = bpy.data.cameras.new('cam'); cd.lens = 62; cam = bpy.data.objects.new('cam', cd); sc.collection.objects.link(cam); sc.camera = cam
    t = bpy.data.objects.new('t', None); t.location = (0, 0, 1.05); sc.collection.objects.link(t)
    tc = cam.constraints.new('TRACK_TO'); tc.target = t; tc.track_axis = 'TRACK_NEGATIVE_Z'; tc.up_axis = 'UP_Y'

def render(path, yaw=-18, dist=5.2, z=1.25):
    cam = S.camera; a = math.radians(-90 + yaw); cam.location = (dist * math.cos(a), dist * math.sin(a), z)
    S.render.filepath = path; bpy.ops.render.render(write_still=True)
