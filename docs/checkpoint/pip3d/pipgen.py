"""Pip 3D plush — procedural Blender model + pose/expression system + renderer."""
import bpy, bmesh, math
from mathutils import Vector, Euler, Matrix

S = bpy.context.scene
OBJ = {}
MAT = {}

def srgb(h):
    h = h.lstrip('#')
    c = [int(h[i:i+2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c) + (1.0,)

def reset():
    global S
    bpy.ops.wm.read_factory_settings(use_empty=True)
    S = bpy.context.scene

# ---------------------------------------------------------------- materials
def mat_plush(name, color, sheen=1.0, rough=0.9, bump=0.25, sss=0.06):
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; n = nt.nodes; l = nt.links
    b = n['Principled BSDF']
    b.inputs['Base Color'].default_value = srgb(color)
    b.inputs['Roughness'].default_value = rough
    b.inputs['Sheen Weight'].default_value = sheen
    b.inputs['Sheen Roughness'].default_value = 0.35
    b.inputs['Sheen Tint'].default_value = (1, 1, 1, 1)
    b.inputs['Subsurface Weight'].default_value = sss
    b.inputs['Subsurface Radius'].default_value = (0.3, 0.3, 0.3)
    b.inputs['Subsurface Scale'].default_value = 0.05
    tex = n.new('ShaderNodeTexNoise'); tex.inputs['Scale'].default_value = 260; tex.inputs['Detail'].default_value = 6
    bm = n.new('ShaderNodeBump'); bm.inputs['Strength'].default_value = bump; bm.inputs['Distance'].default_value = 0.01
    l.new(tex.outputs['Fac'], bm.inputs['Height']); l.new(bm.outputs['Normal'], b.inputs['Normal'])
    MAT[name] = m
    return m

def mat_gloss(name, color, rough=0.05, coat=1.0):
    m = bpy.data.materials.new(name); m.use_nodes = True
    b = m.node_tree.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = srgb(color)
    b.inputs['Roughness'].default_value = rough
    b.inputs['Coat Weight'].default_value = coat
    b.inputs['Specular IOR Level'].default_value = 0.8
    MAT[name] = m
    return m

def mat_emit(name, color, strength=2.0):
    m = bpy.data.materials.new(name); m.use_nodes = True
    n = m.node_tree.nodes; l = m.node_tree.links
    out = n['Material Output']; n.remove(n['Principled BSDF'])
    e = n.new('ShaderNodeEmission'); e.inputs['Color'].default_value = srgb(color); e.inputs['Strength'].default_value = strength
    l.new(e.outputs[0], out.inputs[0])
    MAT[name] = m
    return m

def mat_soft_disc(name, color, alpha=0.75):
    """Soft-edged blush decal: colour fades to transparent toward the edge."""
    m = bpy.data.materials.new(name); m.use_nodes = True
    n = m.node_tree.nodes; l = m.node_tree.links
    out = n['Material Output']; b = n['Principled BSDF']
    b.inputs['Base Color'].default_value = srgb(color); b.inputs['Roughness'].default_value = 0.9
    b.inputs['Sheen Weight'].default_value = 0.6
    tc = n.new('ShaderNodeTexCoord'); g = n.new('ShaderNodeTexGradient'); g.gradient_type = 'SPHERICAL'
    mp = n.new('ShaderNodeMapping'); mp.inputs['Location'].default_value = (0, 0, 0)
    l.new(tc.outputs['Object'], mp.inputs['Vector']); l.new(mp.outputs['Vector'], g.inputs['Vector'])
    ramp = n.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = 0.0; ramp.color_ramp.elements[0].color = (0, 0, 0, 1)
    ramp.color_ramp.elements[1].position = 0.75; ramp.color_ramp.elements[1].color = (alpha, alpha, alpha, 1)
    l.new(g.outputs['Fac'], ramp.inputs['Fac'])
    tr = n.new('ShaderNodeBsdfTransparent'); mix = n.new('ShaderNodeMixShader')
    l.new(ramp.outputs['Color'], mix.inputs['Fac']); l.new(tr.outputs[0], mix.inputs[1]); l.new(b.outputs[0], mix.inputs[2])
    l.new(mix.outputs[0], out.inputs[0])
    MAT[name] = m
    return m

def mat_band(name):
    """Navy sweatband with a cream stripe, by object-space height."""
    m = bpy.data.materials.new(name); m.use_nodes = True
    n = m.node_tree.nodes; l = m.node_tree.links
    b = n['Principled BSDF']
    b.inputs['Roughness'].default_value = 0.95; b.inputs['Sheen Weight'].default_value = 1.0; b.inputs['Sheen Roughness'].default_value = 0.3
    tc = n.new('ShaderNodeTexCoord'); sep = n.new('ShaderNodeSeparateXYZ')
    l.new(tc.outputs['Object'], sep.inputs[0])
    ramp = n.new('ShaderNodeValToRGB'); ramp.color_ramp.interpolation = 'CONSTANT'
    E = ramp.color_ramp.elements
    E[0].position = 0.0; E[0].color = srgb('#173F55')
    E[1].position = 0.60; E[1].color = srgb('#F3EEE2')
    e3 = E.new(0.72); e3.color = srgb('#173F55')
    mr = n.new('ShaderNodeMapRange'); mr.inputs['From Min'].default_value = -0.16; mr.inputs['From Max'].default_value = 0.16
    l.new(sep.outputs['Z'], mr.inputs['Value']); l.new(mr.outputs['Result'], ramp.inputs['Fac'])
    l.new(ramp.outputs['Color'], b.inputs['Base Color'])
    tex = n.new('ShaderNodeTexNoise'); tex.inputs['Scale'].default_value = 300
    wave = n.new('ShaderNodeTexWave'); wave.inputs['Scale'].default_value = 60; wave.wave_type = 'BANDS'; wave.bands_direction = 'Z'
    bm = n.new('ShaderNodeBump'); bm.inputs['Strength'].default_value = 0.25
    mx = n.new('ShaderNodeMath'); mx.operation = 'ADD'
    l.new(tex.outputs['Fac'], mx.inputs[0]); l.new(wave.outputs['Fac'], mx.inputs[1])
    l.new(mx.outputs[0], bm.inputs['Height']); l.new(bm.outputs['Normal'], b.inputs['Normal'])
    MAT[name] = m
    return m

# ---------------------------------------------------------------- helpers
def link(o):
    if o.name not in S.collection.objects: S.collection.objects.link(o)
    return o

def sphere(name, loc, scale, mat, seg=48, ring=24, smooth=True, parent=None):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=ring, location=loc)
    o = bpy.context.active_object; o.name = name; o.scale = scale
    if smooth: bpy.ops.object.shade_smooth()
    o.data.materials.append(mat)
    if parent: set_parent(o, parent)
    OBJ[name] = o
    return o

def set_parent(o, p):
    mw = o.matrix_world.copy(); o.parent = p; o.matrix_world = mw

def curve(name, pts, bevel, mat, closed=False, res=12, parent=None, fill_caps=True):
    cd = bpy.data.curves.new(name, 'CURVE'); cd.dimensions = '3D'; cd.bevel_depth = bevel; cd.bevel_resolution = 6
    cd.use_fill_caps = fill_caps; cd.resolution_u = res
    sp = cd.splines.new('BEZIER'); sp.bezier_points.add(len(pts) - 1)
    for bp, p in zip(sp.bezier_points, pts):
        bp.co = p; bp.handle_left_type = bp.handle_right_type = 'AUTO'
    sp.use_cyclic_u = closed
    o = bpy.data.objects.new(name, cd); link(o); o.data.materials.append(mat)
    if parent: o.parent = parent
    OBJ[name] = o
    return o

def set_curve(o, pts):
    sp = o.data.splines[0]
    if len(sp.bezier_points) != len(pts):
        o.data.splines.clear(); sp = o.data.splines.new('BEZIER'); sp.bezier_points.add(len(pts) - 1)
    for bp, p in zip(sp.bezier_points, pts):
        bp.co = p; bp.handle_left_type = bp.handle_right_type = 'AUTO'

# ---------------------------------------------------------------- geometry constants
HEAD_C = Vector((0, 0, 1.78))      # head centre (in Root space)
HEAD_R = Vector((0.96, 0.86, 0.80))  # approx semi-axes of the head surface
NECK = Vector((0, 0, 1.15))         # head pivot
SHOULDER = Vector((0.50, -0.06, 0.92))
HIP = Vector((0.30, -0.02, 0.36))

MINT = '#A9E9CF'

def head_point(az, el, out=0.0):
    """Point on the head ellipsoid (head-rig space, origin = head centre). az: + = Pip's left (screen right)."""
    d = Vector((math.sin(az) * math.cos(el), -math.cos(az) * math.cos(el), math.sin(el)))
    p = Vector((d.x * HEAD_R.x, d.y * HEAD_R.y, d.z * HEAD_R.z))
    n = Vector((p.x / HEAD_R.x ** 2, p.y / HEAD_R.y ** 2, p.z / HEAD_R.z ** 2)).normalized()
    return p + n * out, n

def orient_to(o, n, up=Vector((0, 0, 1)), roll=0.0):
    """Rotate object so its local +Z points along n (normal), local Y roughly up."""
    z = n.normalized(); x = up.cross(z)
    if x.length < 1e-4: x = Vector((1, 0, 0))
    x.normalize(); y = z.cross(x)
    m = Matrix((x, y, z)).transposed()
    o.rotation_euler = (m.to_4x4() @ Matrix.Rotation(roll, 4, 'Z')).to_euler()

def build():
    reset()
    S.render.engine = 'CYCLES'
    S.cycles.device = 'CPU'
    S.cycles.samples = 24
    S.cycles.use_denoising = True
    S.cycles.denoiser = 'OPENIMAGEDENOISE'
    S.cycles.max_bounces = 6
    S.cycles.diffuse_bounces = 3
    S.cycles.glossy_bounces = 3
    S.cycles.transmission_bounces = 4
    S.render.film_transparent = True
    S.render.resolution_x = 512; S.render.resolution_y = 512
    S.render.image_settings.file_format = 'PNG'; S.render.image_settings.color_mode = 'RGBA'
    S.view_settings.view_transform = 'Khronos PBR Neutral'
    S.render.use_persistent_data = True

    mat_plush('mint', MINT)
    mat_plush('cream', '#F7F0DC', sheen=0.8, bump=0.3)
    mat_band('band')
    mat_plush('navy', '#1C4157', sheen=0.7, rough=0.8, bump=0.35)
    mat_plush('sole', '#FFFFFF', sheen=0.4, rough=0.6, bump=0.1, sss=0.0)
    mat_gloss('eye', '#04120C', rough=0.16, coat=0.7)
    mat_emit('glint', '#FFFFFF', 6.0)
    mat_gloss('brow', '#1D4A3C', rough=0.6, coat=0.0)
    mat_soft_disc('blush', '#FF9AAE', 0.85)
    mat_gloss('mouth', '#5A1A2B', rough=0.4, coat=0.3)
    mat_plush('tongue', '#FF7F96', sheen=0.3, rough=0.5, bump=0.05)
    mat_gloss('white', '#FFFFFF', rough=0.3, coat=0.2)

    # world: soft neutral studio light
    w = bpy.data.worlds.new('W'); S.world = w; w.use_nodes = True
    bg = w.node_tree.nodes['Background']; bg.inputs['Color'].default_value = (0.75, 0.78, 0.8, 1); bg.inputs['Strength'].default_value = 0.6

    root = bpy.data.objects.new('Root', None); link(root); OBJ['Root'] = root
    head = bpy.data.objects.new('HeadRig', None); link(head); head.parent = root; head.location = HEAD_C; OBJ['HeadRig'] = head

    # ---- head + body share one metaball family (smooth neck); arms and legs are their own metaballs
    # so they never web into the body, but are sunk into it like a sewn plush limb.
    K = 1.0 / 0.574          # metaball radius -> surface distance (threshold 0.6, stiffness 2)
    E = {}
    def mball(name):
        mb = bpy.data.metaballs.new(name); mb.resolution = 0.06; mb.render_resolution = 0.022; mb.threshold = 0.6
        o = bpy.data.objects.new(name, mb); link(o); o.parent = root; o.data.materials.append(MAT['mint']); OBJ[name] = o
        return mb
    def el(mb, name, t, co, axes=(1, 1, 1), r=1.0, stiff=2.0):
        e = mb.elements.new(type=t); e.co = co; e.radius = r * K; e.stiffness = stiff
        if t == 'ELLIPSOID': e.size_x, e.size_y, e.size_z = axes
        if t == 'CAPSULE': e.size_x = axes[0]
        E[name] = e; return e
    hm = mball('Head')
    el(hm, 'head', 'ELLIPSOID', HEAD_C, HEAD_R)
    bm_ = mball('Body')
    el(bm_, 'body', 'ELLIPSOID', (0, 0, 0.80), (0.64, 0.58, 0.56))
    for side in ('L', 'R'):
        am = mball('Arm' + side)
        el(am, 'arm' + side, 'CAPSULE', (0, 0, 0), (0.2, 0, 0), r=0.17)
        el(am, 'hand' + side, 'BALL', (0, 0, 0), r=0.20)
        lm = mball('Leg' + side)
        el(lm, 'leg' + side, 'CAPSULE', (0, 0, 0.3), (0.1, 0, 0), r=0.19)
        E['leg' + side].rotation = Vector((1, 0, 0)).rotation_difference(Vector((0, 0, 1)))
    OBJ['E'] = E

    # ---- cream belly patch (raised oval on the tummy)
    sphere('Belly', (0, -0.49, 0.66), (0.41, 0.15, 0.35), MAT['cream'], parent=root)

    # ---- sweatband (thick rounded band around the upper head)
    bpy.ops.mesh.primitive_torus_add(major_radius=1.0, minor_radius=0.17, major_segments=96, minor_segments=24, location=(0, 0, 0))
    band = bpy.context.active_object; band.name = 'Band'; bpy.ops.object.shade_smooth()
    band.data.materials.append(MAT['band']); band.parent = head
    OBJ['Band'] = band
    # band object space z spans ±0.17*scale_z; stripe drawn via object Z
    band.location = (0, 0.0, 0.36); band.scale = (0.84, 0.75, 1.25); band.rotation_euler = (math.radians(-6), 0, 0)

    # ---- curly antenna
    ab = bpy.data.objects.new('AntBase', None); link(ab); ab.parent = head; ab.location = (0, 0, 0.70); OBJ['AntBase'] = ab
    curve('Antenna', [(0, 0.0, -0.06), (0.02, 0.0, 0.20), (0.14, 0.0, 0.34), (0.26, 0.0, 0.26), (0.20, 0.0, 0.16), (0.12, 0.0, 0.22)], 0.075, MAT['mint'], parent=ab)

    # ---- face
    for s, side in ((1, 'L'), (-1, 'R')):
        eye = sphere('Eye' + side, (0, 0, 0), (0.175, 0.07, 0.215), MAT['eye'], parent=None); eye.parent = head
        g1 = sphere('Glint' + side, (0, 0, 0), (0.055, 0.02, 0.06), MAT['glint'], seg=16, ring=8); g1.parent = head
        g2 = sphere('Glint2' + side, (0, 0, 0), (0.025, 0.01, 0.025), MAT['glint'], seg=12, ring=6); g2.parent = head
        lid = curve('Lid' + side, [(0, 0, 0)] * 3, 0.03, MAT['brow'], parent=head)       # closed-eye arc
        brow = curve('Brow' + side, [(0, 0, 0)] * 3, 0.026, MAT['brow'], parent=head)
        bpy.ops.mesh.primitive_circle_add(vertices=32, radius=1.0, fill_type='NGON', location=(0, 0, 0))
        bl = bpy.context.active_object; bl.name = 'Blush' + side; bl.data.materials.append(MAT['blush']); bl.parent = head; OBJ[bl.name] = bl
    # mouth: dark open shape + tongue, and a line mouth for smiles
    me = bpy.data.meshes.new('MouthOpen'); bmm = bmesh.new()
    N = 20; ring = []
    for i in range(N + 1):
        a = math.pi * i / N
        ring.append((-math.cos(a), 0.0, -math.sin(a) * (1.0 - 0.15 * math.sin(a) ** 4)))
    top = [bmm.verts.new((x, -0.5, z)) for x, _, z in ring]
    bot = [bmm.verts.new((x, 0.5, z)) for x, _, z in ring]
    bmm.faces.new(top); bmm.faces.new(list(reversed(bot)))
    for i in range(N + 1):
        j = (i + 1) % (N + 1)
        bmm.faces.new((top[i], top[j], bot[j], bot[i]))
    bmm.to_mesh(me); bmm.free()
    mo = bpy.data.objects.new('MouthOpen', me); link(mo); mo.parent = head; mo.data.materials.append(MAT['mouth']); OBJ['MouthOpen'] = mo
    bv = mo.modifiers.new('bev', 'BEVEL'); bv.width = 0.08; bv.segments = 3
    for p_ in me.polygons: p_.use_smooth = True
    sphere('Tongue', (0, 0, 0), (0.09, 0.03, 0.05), MAT['tongue'], parent=None).parent = head
    curve('MouthLine', [(0, 0, 0)] * 3, 0.022, MAT['brow'], parent=head)

    # ---- sneakers
    for s, side in ((1, 'L'), (-1, 'R')):
        shoe = bpy.data.objects.new('Shoe' + side, None); link(shoe); shoe.parent = root; OBJ['Shoe' + side] = shoe
        up = sphere('Upper' + side, (0, -0.04, 0.13), (0.24, 0.32, 0.15), MAT['navy']); up.parent = shoe
        so = sphere('Sole' + side, (0, -0.05, 0.05), (0.26, 0.35, 0.07), MAT['sole']); so.parent = shoe
        toe = sphere('Toe' + side, (0, -0.25, 0.08), (0.18, 0.12, 0.07), MAT['sole']); toe.parent = shoe
        for k, z in enumerate((0.20, 0.15)):
            c = curve('Lace%d%s' % (k, side), [(-0.12, -0.1 - k * 0.07, z + 0.02), (0.0, -0.2 - k * 0.06, z + 0.04), (0.12, -0.1 - k * 0.07, z + 0.02)], 0.018, MAT['sole'], parent=shoe)
        shoe.location = (s * 0.30, -0.02, 0.0)

    # ---- props (hidden until used)
    build_props(root)


    # ---- lights
    def area(name, loc, energy, size, color=(1, 1, 1)):
        ld = bpy.data.lights.new(name, 'AREA'); ld.energy = energy; ld.size = size; ld.color = color
        lo = bpy.data.objects.new(name, ld); link(lo); lo.location = loc
        d = Vector((0, 0, 1.2)) - Vector(loc); lo.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
        return lo
    area('Key', (-3.5, -5.0, 5.0), 900, 5.0, (1.0, 0.97, 0.93))
    area('Fill', (4.5, -4.0, 2.5), 350, 6.0, (0.92, 0.96, 1.0))
    area('Rim', (0.5, 5.0, 4.5), 700, 3.0, (1.0, 1.0, 1.0))
    area('Top', (0, -1.0, 7.0), 250, 4.0)

    # ---- camera
    cd = bpy.data.cameras.new('Cam'); cd.lens = 85; cd.sensor_width = 36
    cam = bpy.data.objects.new('Cam', cd); link(cam); S.camera = cam
    cam.location = (0, -8.6, 1.95); cam.rotation_euler = (math.radians(87.2), 0, 0)
    OBJ['Cam'] = cam

def build_props(root):
    m = MAT
    mat_gloss('metal', '#3B4652', rough=0.3, coat=0.5)
    mat_gloss('plate', '#1E2A33', rough=0.35, coat=0.6)
    mat_gloss('heart', '#FF5E78', rough=0.35, coat=0.6)
    bm = bpy.data.materials.new('bottle'); bm.use_nodes = True
    b = bm.node_tree.nodes['Principled BSDF']; b.inputs['Base Color'].default_value = srgb('#7FD3FF'); b.inputs['Roughness'].default_value = 0.08
    b.inputs['Transmission Weight'].default_value = 0.85; b.inputs['IOR'].default_value = 1.45; MAT['bottle'] = bm
    mat_plush('pillow', '#FBFBFF', sheen=1.0, bump=0.2)
    mat_gloss('bowl', '#FFFFFF', rough=0.2)
    mat_plush('leaf', '#4CC25D', sheen=0.3, rough=0.6, bump=0.1)
    mat_gloss('tomato', '#FF4F3A', rough=0.15)
    mat_plush('avocado', '#9ACD55', sheen=0.2, rough=0.5, bump=0.05)
    mat_gloss('seed', '#7A4A24', rough=0.2)

    def empty(name):
        e = bpy.data.objects.new(name, None); link(e); e.parent = root; OBJ[name] = e; return e
    # dumbbell (along local X)
    d = empty('Dumbbell')
    bpy.ops.mesh.primitive_cylinder_add(radius=0.035, depth=0.5, location=(0, 0, 0), rotation=(0, math.radians(90), 0)); o = bpy.context.active_object; o.data.materials.append(MAT['metal']); o.parent = d; bpy.ops.object.shade_smooth()
    for s in (-1, 1):
        bpy.ops.mesh.primitive_cylinder_add(radius=0.14, depth=0.12, location=(s * 0.2, 0, 0), rotation=(0, math.radians(90), 0)); o = bpy.context.active_object
        o.data.materials.append(MAT['plate']); o.parent = d
        bev = o.modifiers.new('b', 'BEVEL'); bev.width = 0.03; bev.segments = 4; bpy.ops.object.shade_smooth()
    # bottle (along local Z)
    bt = empty('Bottle')
    bpy.ops.mesh.primitive_cylinder_add(radius=0.11, depth=0.42, location=(0, 0, 0.21)); o = bpy.context.active_object
    o.data.materials.append(MAT['bottle']); o.parent = bt; bev = o.modifiers.new('b', 'BEVEL'); bev.width = 0.05; bev.segments = 6; bpy.ops.object.shade_smooth()
    bpy.ops.mesh.primitive_cylinder_add(radius=0.07, depth=0.08, location=(0, 0, 0.46)); o = bpy.context.active_object; o.data.materials.append(MAT['navy']); o.parent = bt
    bev = o.modifiers.new('b', 'BEVEL'); bev.width = 0.02; bev.segments = 3; bpy.ops.object.shade_smooth()
    # heart (two spheres + cone, joined visually)
    h = empty('Heart')
    for s in (-1, 1):
        sphere('HeartLobe%d' % s, (s * 0.13, 0, 0.08), (0.17, 0.11, 0.17), MAT['heart']).parent = h
    bpy.ops.mesh.primitive_cone_add(radius1=0.27, radius2=0.0, depth=0.36, location=(0, 0, -0.13), rotation=(math.radians(180), 0, 0))
    o = bpy.context.active_object; o.scale = (1.0, 0.42, 1.0); o.data.materials.append(MAT['heart']); o.parent = h
    bev = o.modifiers.new('b', 'BEVEL'); bev.width = 0.06; bev.segments = 6; sub = o.modifiers.new('s', 'SUBSURF'); sub.levels = 2; bpy.ops.object.shade_smooth()
    # salad bowl
    bw = empty('Bowl')
    bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=0.34, location=(0, 0, 0)); o = bpy.context.active_object
    bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.select_all(action='DESELECT'); bpy.ops.object.mode_set(mode='OBJECT')
    for v in o.data.vertices: v.select = v.co.z > 0.0
    bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.delete(type='VERT'); bpy.ops.object.mode_set(mode='OBJECT')
    sol = o.modifiers.new('sol', 'SOLIDIFY'); sol.thickness = 0.03
    o.scale = (1, 1, 0.75); o.data.materials.append(MAT['bowl']); o.parent = bw; bpy.ops.object.shade_smooth()
    import random; rnd = random.Random(3)
    for i in range(14):
        a = rnd.random() * 6.28; r = rnd.random() * 0.24
        sphere('Leaf%d' % i, (math.cos(a) * r, math.sin(a) * r * 0.8, 0.02 + rnd.random() * 0.05), (0.09, 0.08, 0.04), MAT['leaf'], seg=16, ring=8).parent = bw
    for i, (x, y) in enumerate(((0.1, -0.08), (-0.12, 0.02))):
        sphere('Tom%d' % i, (x, y, 0.07), (0.06, 0.06, 0.06), MAT['tomato'], seg=16, ring=8).parent = bw
    sphere('Avo', (-0.02, -0.14, 0.07), (0.1, 0.07, 0.04), MAT['avocado'], seg=24, ring=12).parent = bw
    sphere('Seed', (-0.02, -0.15, 0.1), (0.04, 0.03, 0.025), MAT['seed'], seg=12, ring=6).parent = bw
    # pillow
    pl = empty('Pillow')
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 0)); o = bpy.context.active_object; o.scale = (0.85, 0.3, 0.55)
    sub = o.modifiers.new('s', 'SUBSURF'); sub.levels = 3; o.data.materials.append(MAT['pillow']); o.parent = pl; bpy.ops.object.shade_smooth()
    th = empty('Thumb')
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=12, location=(0, 0, 0.10)); o = bpy.context.active_object
    o.scale = (0.075, 0.075, 0.13); o.data.materials.append(MAT['mint']); o.parent = th; bpy.ops.object.shade_smooth()
    for name in ('Dumbbell', 'Bottle', 'Heart', 'Bowl', 'Pillow', 'Thumb'):
        show(name, False)

def show(name, on):
    o = OBJ[name]
    for c in [o] + list(o.children_recursive):
        c.hide_render = not on; c.hide_viewport = not on

# ---------------------------------------------------------------- posing
ARM_L = 0.40

def face_frame(n, up=Vector((0, 0, 1))):
    y = (-n).normalized()
    z = (up - up.dot(y) * y)
    if z.length < 1e-4: z = Vector((0, 0, 1))
    z.normalize(); x = y.cross(z)
    return Matrix((x, y, z)).transposed()   # columns = local axes

def place_face(o, p, n, scale):
    m = face_frame(n)
    o.matrix_basis = Matrix.Translation(p) @ m.to_4x4() @ Matrix.Diagonal((scale[0], scale[1], scale[2], 1.0))

def surf(az, el, out=0.0):
    return head_point(az, el, out)

def arm_dir(side, a, f):
    s = 1 if side == 'L' else -1
    ar, fr = math.radians(a), math.radians(f)
    return Vector((s * math.sin(ar) * math.cos(fr), -math.sin(fr), -math.cos(ar) * math.cos(fr))).normalized()

def pose(P):
    """Apply a pose dict. Missing keys use neutral defaults."""
    E = OBJ['E']; root = OBJ['Root']; head = OBJ['HeadRig']
    g = lambda k, d: P.get(k, d)
    # ---- root (whole body)
    cr = g('crouch', 0.0)
    root.location = (g('x', 0.0), 0.0, g('hop', 0.0) - cr)
    root.rotation_euler = (0.0, math.radians(g('lean', 0.0)), math.radians(g('spin', 0.0)))
    sq = g('squash', 1.0)
    root.scale = (1.0 / math.sqrt(sq), 1.0 / math.sqrt(sq), sq)
    # ---- head (pivot at neck)
    R = Euler((math.radians(g('nod', 0.0)), math.radians(g('tilt', 0.0)), math.radians(g('yaw', 0.0))), 'XYZ').to_matrix()
    hc = NECK + R @ (HEAD_C - NECK)
    head.location = hc; head.rotation_euler = R.to_euler()
    E['head'].co = hc; E['head'].rotation = R.to_quaternion()
    # ---- body breathing
    br = g('breath', 0.0)
    E['body'].co = (0, 0, 0.80 + br * 0.01); E['body'].size_z = 0.56 * (1 + br * 0.02)
    # ---- arms
    hands = {}
    for side, s in (('L', 1), ('R', -1)):
        sh = Vector((s * SHOULDER.x, SHOULDER.y, SHOULDER.z))
        tgt = P.get('hand' + side)
        d = arm_dir(side, g('arm' + side, 18.0), g('fwd' + side, 10.0)); L = ARM_L
        if tgt is not None:
            v = Vector(tgt) - sh; L1 = max(0.18, min(ARM_L + 0.1, v.length)); d1 = v.normalized()
            w = P.get('_hw' + side, 1.0)
            d = d.lerp(d1, w).normalized(); L = L + (L1 - L) * w
        e = E['arm' + side]
        e.co = sh + d * (L / 2); e.size_x = L / 2
        e.rotation = Vector((1, 0, 0)).rotation_difference(d)
        hp = sh + d * (L + 0.02)
        E['hand' + side].co = hp
        hands[side] = hp
    # ---- legs / shoes (dance steps)
    for side, s in (('L', 1), ('R', -1)):
        lift = g('step' + side, 0.0)
        sp = g('spread', 0.0)
        E['leg' + side].co = (s * (0.28 + sp), -0.02, 0.28 + lift + cr * 0.6)
        OBJ['Shoe' + side].location = (s * (0.30 + sp), -0.02, (lift + cr) / sq)
        OBJ['Shoe' + side].rotation_euler = (math.radians(-lift * 40), 0, math.radians(s * -8))
    OBJ['AntBase'].rotation_euler = (math.radians(g('antx', 0.0)), math.radians(g('ant', 0.0)), 0)
    face(P)
    props(P, hands)

def face(P):
    g = lambda k, d: P.get(k, d)
    eyes = g('eyes', 'open'); blink = g('blink', 0.0); lx, ly = g('look', (0.0, 0.0))
    big = g('eyebig', 1.0)
    for side, s in (('L', 1), ('R', -1)):
        az = s * 0.36 + lx * 0.12; el = -0.02 + ly * 0.10
        style = eyes
        if eyes == 'wink': style = 'happy' if side == 'R' else 'open'      # Pip winks its right eye (screen left)
        eye, glint, glint2, lid = OBJ['Eye' + side], OBJ['Glint' + side], OBJ['Glint2' + side], OBJ['Lid' + side]
        openish = style in ('open', 'big', 'determined')
        h = 0.215 * big * (1.0 - 0.92 * blink) * (0.8 if style == 'determined' else 1.0)
        p, n = surf(az, el, -0.035)
        if openish and h > 0.03:
            place_face(eye, p, n, (0.175 * big, 0.075, h))
            m = face_frame(n)
            X, Z = m.col[0], m.col[2]
            gp = p + n * 0.10 + X * (-0.055 * big) + Z * (0.08 * big * (h / 0.215))
            place_face(glint, gp, n, (0.068 * big, 0.02, 0.072 * big * max(0.3, h / 0.215)))
            gp2 = p + n * 0.09 + X * (0.06 * big) + Z * (-0.07 * big * (h / 0.215))
            place_face(glint2, gp2, n, (0.024 * big, 0.01, 0.024 * big))
            for o in (eye, glint, glint2): o.hide_render = False
            vis_lid = blink > 0.7
        else:
            for o in (eye, glint, glint2): o.hide_render = True
            vis_lid = True
        # closed-eye arcs: happy (^) or sleeping (u) or blink line
        if vis_lid:
            up = 0.085 if style == 'happy' or (style == 'wink') else (-0.06 if style == 'closed' else 0.0)
            if openish: up = -0.02
            pts = [surf(az - 0.13, el - 0.01, 0.012)[0], surf(az, el + up, 0.012)[0], surf(az + 0.13, el - 0.01, 0.012)[0]]
            set_curve(lid, pts); lid.hide_render = False
        else:
            lid.hide_render = True
        # brows
        brow = OBJ['Brow' + side]
        bs = g('brows', 'normal')
        inner = -s  # direction toward the face centre in az
        by = 0.33 + (0.06 if bs == 'raised' else 0.0)
        tilt = {'determined': -0.07, 'worried': 0.07, 'think': (0.06 if side == 'L' else -0.03)}.get(bs, 0.0)
        if bs == 'none':
            brow.hide_render = True
        else:
            a0 = az + inner * 0.11; a1 = az - inner * 0.11
            arch = 0.03 if bs in ('normal', 'raised') else 0.0
            pts = [surf(a0, el + by + tilt, 0.012)[0], surf(az, el + by + arch + tilt * 0.4, 0.012)[0], surf(a1, el + by - tilt * 0.3, 0.012)[0]]
            set_curve(brow, pts); brow.hide_render = False
        # blush
        bl = OBJ['Blush' + side]
        bp, bn = surf(s * 0.66, -0.20, 0.008)
        m = face_frame(bn)
        # circle lies in local XY; we need it facing out: rotate so circle normal (local Z) = n
        z = bn.normalized(); x = Vector((0, 0, 1)).cross(z).normalized(); y = z.cross(x)
        mm = Matrix((x, y, z)).transposed()
        amt = g('blush', 0.7)
        bl.matrix_basis = Matrix.Translation(bp) @ mm.to_4x4() @ Matrix.Diagonal((0.17 * (0.8 + 0.3 * amt), 0.11 * (0.8 + 0.3 * amt), 1, 1))
        bl.hide_render = amt <= 0.05
    # ---- mouth
    mo, tg, ml = OBJ['MouthOpen'], OBJ['Tongue'], OBJ['MouthLine']
    mouth = g('mouth', 'smile'); op = g('open', 0.0)
    mp, mn = surf(0.0, -0.30, -0.02)
    m = face_frame(mn); X, Z = m.col[0], m.col[2]
    if mouth in ('open', 'grin', 'tongue', 'o', 'talk'):
        if mouth == 'o':
            w, h = 0.075, 0.085 + 0.02 * op
        elif mouth == 'talk':
            w, h = 0.10 + 0.03 * op, 0.03 + 0.10 * op
        elif mouth == 'grin':
            w, h = 0.15, 0.07
        else:
            w, h = 0.17 + 0.02 * op, 0.16 + 0.05 * op
        c = mp + Z * (h * 0.35)            # top (flat) edge of the D shape
        if mouth == 'o':
            c = mp + Z * (h * 0.55)
        place_face(mo, c, mn, (w, 0.05, h)); mo.hide_render = False
        show_t = mouth in ('open', 'tongue', 'talk') and h > 0.05
        tp = c + mn * 0.045 - Z * (h * 0.70) + (X * 0.035 if mouth == 'tongue' else Vector())
        place_face(tg, tp, mn, (w * 0.58, 0.03, h * 0.30)); tg.hide_render = not show_t
        ml.hide_render = True
    else:
        mo.hide_render = True; tg.hide_render = True
        if mouth == 'smile':
            pts = [surf(-0.12, -0.27, 0.012)[0], surf(0.0, -0.34, 0.012)[0], surf(0.12, -0.27, 0.012)[0]]
        elif mouth == 'flat':
            pts = [surf(-0.08, -0.31, 0.012)[0], surf(0.0, -0.315, 0.012)[0], surf(0.08, -0.31, 0.012)[0]]
        elif mouth == 'worry':
            pts = [surf(-0.09, -0.34, 0.012)[0], surf(-0.03, -0.30, 0.012)[0], surf(0.03, -0.33, 0.012)[0], surf(0.09, -0.30, 0.012)[0]]
        elif mouth == 'frown':
            pts = [surf(-0.09, -0.34, 0.012)[0], surf(0.0, -0.30, 0.012)[0], surf(0.09, -0.34, 0.012)[0]]
        else:  # small smile
            pts = [surf(-0.07, -0.29, 0.012)[0], surf(0.0, -0.33, 0.012)[0], surf(0.07, -0.29, 0.012)[0]]
        set_curve(ml, pts); ml.hide_render = False

def props(P, hands):
    which = P.get('prop')
    for name in ('Dumbbell', 'Bottle', 'Heart', 'Bowl', 'Pillow', 'Thumb'):
        show(name, name == which)
    if not which: return
    o = OBJ[which]
    off = Vector(P.get('prop_off', (0, 0, 0)))
    rot = P.get('prop_rot', (0, 0, 0))
    at = P.get('prop_at', 'R')
    base = hands['R'] if at == 'R' else hands['L'] if at == 'L' else (hands['L'] + hands['R']) / 2
    o.location = base + off
    q0 = Euler(tuple(math.radians(v) for v in rot), 'XYZ').to_quaternion()
    if P.get('prop_aim') == 'mouth':
        head = OBJ['HeadRig']
        mp, _ = head_point(0.0, -0.30, 0.14)
        target = head.matrix_basis @ mp
        d = (target - o.location).normalized()
        q1 = Vector((0, 0, 1)).rotation_difference(d)
        q0 = q0.slerp(q1, P.get('prop_mix', 1.0))
    o.rotation_euler = q0.to_euler()
    sc = P.get('prop_scale', 1.0); o.scale = (sc, sc, sc)

def render(path):
    S.render.filepath = path
    bpy.ops.render.render(write_still=True)
