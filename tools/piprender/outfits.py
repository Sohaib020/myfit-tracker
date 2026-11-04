"""Buddy outfit tiers on Pip's rig (same skeleton, same animations): every garment is a clean procedural shape
that rides the arm/leg metaballs, so it follows every pose. Levels: 1 streetwear, 2 epic, 3 legendary, 4 champion.
usage (bpy): import pipgen as P, cast as C, outfits as O; P.build(); C.skin('zara'); O.dress(3); ... P.pose(p); O.update()"""
import math, bpy
from mathutils import Vector, Matrix, Quaternion
import pipgen as P

STATE = {"level": 0, "parts": []}
GOLD, CRIMSON, LIME, INK = '#E9B949', '#C8102E', '#B6FF3B', '#16171B'

def _mats(accent):
    M = {}
    M['fab'] = P.mat_plush('o_fab', INK, sheen=0.55, rough=0.85, bump=0.4, sss=0.0)
    M['fab2'] = P.mat_plush('o_fab2', '#23252B', sheen=0.5, rough=0.85, bump=0.4, sss=0.0)
    M['rib'] = P.mat_plush('o_rib', accent, sheen=0.4, rough=0.7, bump=0.6, sss=0.0)
    M['white'] = P.mat_plush('o_white', '#F4F4F2', sheen=0.3, rough=0.6, bump=0.1, sss=0.0)
    M['gold'] = P.mat_gloss('o_gold', GOLD, rough=0.22, coat=0.8)
    nt = M['gold'].node_tree.nodes['Principled BSDF']; nt.inputs['Metallic'].default_value = 1.0
    M['plate'] = P.mat_gloss('o_plate', '#7A0F22', rough=0.28, coat=1.0)
    M['plate'].node_tree.nodes['Principled BSDF'].inputs['Metallic'].default_value = 0.6
    M['dark'] = P.mat_gloss('o_dark', '#1B1D22', rough=0.35, coat=0.8)
    M['glow'] = P.mat_emit('o_glow', LIME, 6.0)
    M['gem'] = P.mat_emit('o_gem', LIME, 9.0)
    M['cape'] = P.mat_plush('o_cape', '#9E0B24', sheen=1.0, rough=0.8, bump=0.25, sss=0.0)
    return M

def _keep(o):
    STATE["parts"].append(o); return o

def _sphere(name, loc, scale, mat, parent):
    return _keep(P.sphere(name, loc, scale, mat, parent=parent))

def _torus(name, loc, scale, minor, mat, parent, rot=(0, 0, 0), seg=72):
    bpy.ops.mesh.primitive_torus_add(major_radius=1.0, minor_radius=minor, major_segments=seg, minor_segments=16, location=(0, 0, 0))
    o = bpy.context.active_object; o.name = name; bpy.ops.object.shade_smooth(); o.data.materials.append(mat)
    o.parent = parent; o.location = loc; o.scale = scale; o.rotation_euler = rot
    return _keep(o)

def _mball(name, mat, r):
    mb = bpy.data.metaballs.new(name); mb.resolution = 0.06; mb.render_resolution = 0.022; mb.threshold = 0.6
    o = bpy.data.objects.new(name, mb); P.link(o); o.parent = P.OBJ['Root']; o.data.materials.append(mat)
    e = mb.elements.new(type='CAPSULE'); e.radius = r / 0.574; e.stiffness = 2.0
    P.OBJ[name] = o; _keep(o)
    return e

def dress(level, accent=None):
    """Build the outfit for [level] (1..4). Call after C.skin(); then call update() after every P.pose()."""
    STATE.update(level=level, parts=[], el={})
    if level <= 0: return
    root, head = P.OBJ['Root'], P.OBJ['HeadRig']
    M = _mats(accent or (GOLD if level >= 2 else CRIMSON)); STATE['M'] = M
    E = P.OBJ['E']
    # physique: higher tiers get chunkier arms and fists
    bulk = {1: 1.0, 2: 1.08, 3: 1.16, 4: 1.2}[level]
    for s in 'LR':
        E['arm' + s].radius *= bulk; E['hand' + s].radius *= (1 + (bulk - 1) * 0.6)
    if 'Belly' in P.OBJ: P.OBJ['Belly'].hide_render = True
    else:
        b = bpy.data.objects.get('Belly')
        if b: b.hide_render = True
    # shoes: black uppers, white (L1) / gold (L2+) soles, lime laces
    P.MAT['navy'].node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = P.srgb('#15161A')
    P.MAT['sole'].node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = P.srgb(GOLD if level >= 2 else '#F4F4F2')
    for o in bpy.data.objects:
        if o.name.startswith('Lace'):
            o.data.materials.clear(); o.data.materials.append(M['glow'] if level >= 2 else M['rib'])
    # headband in kit colours
    from cast import _band
    _band('#15161A', GOLD if level >= 3 else (LIME if level == 2 else CRIMSON))

    torso = M['fab'] if level <= 2 else P.mat_plush('o_suit', '#121317', sheen=0.4, rough=0.8, bump=0.3, sss=0.0)
    # ---- torso shell (hoodie / armour undersuit)
    _sphere('O_Torso', (0, 0, 0.80), (0.665, 0.605, 0.585), torso, root)
    hem = M['gold'] if level >= 2 else M['rib']
    _torus('O_Hem', (0, 0, 0.30), (0.42, 0.385, 1.0), 0.055, hem, root)
    # ---- sleeves + leggings follow the limb metaballs
    STATE['el'] = {}
    for s in 'LR':
        STATE['el']['sleeve' + s] = _mball('O_Sleeve' + s, torso, 0.17 * bulk + 0.022)
        STATE['el']['leg' + s] = _mball('O_Leg' + s, M['fab2'] if level <= 2 else torso, 0.212)
        STATE['el']['cuff' + s] = _torus('O_Cuff' + s, (0, 0, 0), (0.2, 0.2, 0.2), 0.32, M['gold'] if level >= 2 else M['rib'], root)
        _torus('O_Ankle' + s, ((1 if s == 'L' else -1) * 0.28, -0.02, 0.17), (0.215, 0.215, 1.0), 0.05, M['glow'] if level >= 2 else M['rib'], root)
    if level <= 2:
        # hood bunched behind the neck + collar, kangaroo pocket, drawstrings
        _sphere('O_Hood', (0, 0.36, 1.13), (0.46, 0.24, 0.26), M['fab'], root)
        _torus('O_Collar', (0, 0.02, 1.20), (0.50, 0.45, 1.0), 0.075, M['fab'], root, rot=(math.radians(8), 0, 0))
        _sphere('O_Pocket', (0, -0.565, 0.56), (0.31, 0.075, 0.15), M['fab2'], root)
        for sx in (1, -1):
            c = P.curve('O_String%d' % sx, [(sx * 0.10, -0.53, 1.13), (sx * 0.11, -0.60, 1.00), (sx * 0.12, -0.615, 0.86)], 0.016, M['white'], parent=root); _keep(c)
            _sphere('O_Aglet%d' % sx, (sx * 0.12, -0.62, 0.84), (0.024, 0.024, 0.04), M['gold'] if level >= 2 else M['white'], root)
    if level == 2:
        # epic: gold chain + medallion with a lime gem, glowing seam lines
        _torus('O_Chain', (0, -0.08, 1.10), (0.44, 0.42, 1.0), 0.028, M['gold'], root, rot=(math.radians(-22), 0, 0))
        _sphere('O_Medal', (0, -0.60, 0.93), (0.10, 0.03, 0.10), M['gold'], root)
        _sphere('O_MedalGem', (0, -0.625, 0.93), (0.05, 0.015, 0.05), M['gem'], root)
        for sx in (1, -1):
            c = P.curve('O_Seam%d' % sx, [(sx * 0.46, -0.37, 1.12), (sx * 0.56, -0.33, 0.85), (sx * 0.44, -0.37, 0.48)], 0.012, M['glow'], parent=root); _keep(c)
    if level >= 3:
        # legendary: sculpted chest plate with gold rim + glowing core, gold belt, knee guards; pauldrons + gauntlets follow arms
        _sphere('O_Chest', (0, -0.48, 0.90), (0.42, 0.13, 0.28), M['plate'], root)
        _torus('O_ChestRim', (0, -0.565, 0.90), (0.40, 0.27, 0.40), 0.07, M['gold'], root, rot=(math.radians(90), 0, 0))
        _sphere('O_Core', (0, -0.615, 0.94), (0.085, 0.03, 0.085), M['gem'], root)
        _torus('O_CoreRing', (0, -0.625, 0.94), (0.11, 0.11, 0.11), 0.22, M['gold'], root, rot=(math.radians(90), 0, 0))
        _torus('O_Belt', (0, 0, 0.42), (0.55, 0.50, 1.0), 0.06, M['gold'], root)
        for sx in (1, -1):
            _sphere('O_Knee%d' % sx, (sx * 0.29, -0.20, 0.36), (0.13, 0.07, 0.12), M['gold'], root)
            for k, (z, w) in enumerate(((1.05, 0.30), (0.72, 0.36))):
                c = P.curve('O_Rune%d%d' % (sx, k), [(sx * 0.18, -0.60, z), (sx * w, -0.55, z - 0.06), (sx * (w + 0.12), -0.48, z - 0.02)], 0.011, M['glow'], parent=root); _keep(c)
        for s in 'LR':
            STATE['el']['paul' + s] = _sphere('O_Paul' + s, (0, 0, 0), (0.27, 0.27, 0.21), M['plate'], root)
            STATE['el']['paulrim' + s] = _torus('O_PaulRim' + s, (0, 0, 0), (0.25, 0.25, 0.25), 0.16, M['gold'], root)
            STATE['el']['gaunt' + s] = _torus('O_Gaunt' + s, (0, 0, 0), (0.2, 0.2, 0.2), 0.42, M['gold'], root)
    if level >= 4:
        # champion: cape, championship belt plate, laurel crown, aura ring + motes
        cape = _cape(M); _keep(cape)
        _sphere('O_TitlePlate', (0, -0.64, 0.42), (0.30, 0.06, 0.15), M['gold'], root)
        _sphere('O_TitleGem', (0, -0.70, 0.42), (0.09, 0.02, 0.07), M['gem'], root)
        for sx in (1, -1):
            _sphere('O_TitleSide%d' % sx, (sx * 0.40, -0.52, 0.42), (0.12, 0.07, 0.10), M['gold'], root)
        _crown(M, head)
        _torus('O_Aura', (0, 0, 0.012), (1.05, 1.05, 1.0), 0.018, M['glow'], root, seg=128)
        import random
        rnd = random.Random(7)
        for k in range(14):
            a = rnd.random() * math.tau; r = 0.85 + rnd.random() * 0.35; z = 0.1 + rnd.random() * 1.9
            _sphere('O_Mote%d' % k, (r * math.cos(a), r * math.sin(a) + 0.15, z), (0.022,) * 3, M['glow'], root)

def _cape(M):
    """A gently curved cloth panel hanging from the shoulders behind the body."""
    import bmesh
    me = bpy.data.meshes.new('O_Cape'); bm = bmesh.new()
    W, H, NX, NY = 1.30, 1.10, 14, 12
    grid = []
    for j in range(NY + 1):
        row = []
        for i in range(NX + 1):
            u = i / NX - 0.5; v = j / NY
            w = W * (0.62 + 0.38 * v)                       # flares toward the hem
            x = u * w; z = 1.20 - v * H
            y = 0.46 + 0.30 * v + 0.10 * math.cos(u * math.pi) * v + 0.035 * math.sin(u * 9) * v   # folds
            row.append(bm.verts.new((x, y, z)))
        grid.append(row)
    for j in range(NY):
        for i in range(NX):
            bm.faces.new((grid[j][i], grid[j][i + 1], grid[j + 1][i + 1], grid[j + 1][i]))
    bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new('O_Cape', me); P.link(o); o.parent = P.OBJ['Root']
    for p in me.polygons: p.use_smooth = True
    o.data.materials.append(M['cape']); o.data.materials.append(M['gold'])
    sol = o.modifiers.new('t', 'SOLIDIFY'); sol.thickness = 0.03; sol.use_rim = True; sol.material_offset_rim = 1
    sub = o.modifiers.new('s', 'SUBSURF'); sub.levels = 2; sub.render_levels = 2
    # gold clasps at the shoulders
    for sx in (1, -1): _sphere('O_Clasp%d' % sx, (sx * 0.36, -0.30, 1.16), (0.08, 0.05, 0.08), M['gold'], P.OBJ['Root'])
    return o

def _crown(M, head):
    """Small laurel-style gold crown sitting on the headband."""
    for k in range(7):
        a = math.radians(-60 + k * 20)
        p, n = P.head_point(a, 0.62, 0.02)
        o = P.sphere('O_Leaf%d' % k, (0, 0, 0), (0.055, 0.03, 0.11), M['gold'], parent=None); o.parent = head
        o.location = p; P.orient_to(o, n); _keep(o)
    gem = P.sphere('O_CrownGem', (0, 0, 0), (0.05, 0.03, 0.05), M['gem'], parent=None); gem.parent = head
    p, n = P.head_point(0.0, 0.66, 0.06); gem.location = p; _keep(gem)

def update():
    """Re-seat the limb-following pieces after P.pose()."""
    if STATE["level"] <= 0: return
    E = P.OBJ['E']; el = STATE['el']
    for s in 'LR':
        e = E['arm' + s]; q = e.rotation.copy() if hasattr(e.rotation, 'copy') else e.rotation
        d = (q @ Vector((1, 0, 0))).normalized(); half = e.size_x
        sh = Vector(e.co) - d * half; L = half * 2
        sl = el['sleeve' + s]
        sl.co = sh + d * (L * 0.40); sl.size_x = L * 0.40; sl.rotation = q
        lg = el['leg' + s]; le = E['leg' + s]
        lg.co = le.co; lg.size_x = le.size_x; lg.rotation = le.rotation
        cf = el['cuff' + s]
        cf.location = sh + d * (L * 0.80); cf.rotation_euler = Vector((0, 0, 1)).rotation_difference(d).to_euler()
        if 'paul' + s in el:
            pa = el['paul' + s]; pa.location = sh + d * 0.02 + Vector((0, 0, 0.04))
            pa.rotation_euler = Vector((0, 0, 1)).rotation_difference(d).to_euler()
            pr = el['paulrim' + s]; pr.location = sh + d * 0.11 + Vector((0, 0, 0.02)); pr.rotation_euler = pa.rotation_euler
            ga = el['gaunt' + s]; ga.location = sh + d * (L * 0.92); ga.rotation_euler = cf.rotation_euler
            cf.hide_render = True
