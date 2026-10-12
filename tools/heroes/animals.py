"""Animal champions on the Quaternius bodies: modelled animal heads + real particle fur + fighter kit tiers.
Species: jaguar (King-style), bull, wolf, snowleopard (female). The human head is masked out; the animal head rides the
Head bone; fur strands grow from the (deforming) body so they follow every animation."""
import bpy, bmesh, math
from mathutils import Vector
import heroes as H, outfit as O
from outfit import mat, shell, boundary_ring, blob, torus, tube, bone_head, bone_tail
import designs as D

SPEC = {
    'jaguar':      dict(base='#D2862A', belly='#F3E2BC', spot='#140F0B', pattern='rosette', fur_len=0.011, head_fur=0.01, bulk=0.009, scale=15),
    'snowleopard': dict(base='#E4DFD4', belly='#F7F5F0', spot='#343940', pattern='rosette', fur_len=0.013, head_fur=0.012, bulk=0.0, scale=13),
    'wolf':        dict(base='#8C9199', belly='#DEE1E5', spot='#33373E', pattern='saddle', fur_len=0.02, head_fur=0.022, bulk=0.007, scale=8),
    'bull':        dict(base='#2B1E17', belly='#3B2B21', spot='#140E0B', pattern='plain', fur_len=0.006, head_fur=0.008, bulk=0.016, scale=10),
}

# ------------------------------------------------------------------ fur colour (shared by skin + strands)
def coat_nodes(nt, sp, out_socket):
    n = nt.nodes; L = nt.links
    tc = n.new('ShaderNodeTexCoord')
    mp = n.new('ShaderNodeMapping'); L.new(tc.outputs['Object'], mp.inputs['Vector'])
    sep = n.new('ShaderNodeSeparateXYZ'); L.new(tc.outputs['Object'], sep.inputs[0])
    # belly / chest lighter toward the front-lower body
    belly = n.new('ShaderNodeMapRange'); belly.inputs['From Min'].default_value = -0.02; belly.inputs['From Max'].default_value = -0.12
    L.new(sep.outputs['Y'], belly.inputs['Value'])
    base = n.new('ShaderNodeMix'); base.data_type = 'RGBA'; L.new(belly.outputs['Result'], base.inputs[0])
    base.inputs[6].default_value = H.srgb(sp['base']); base.inputs[7].default_value = H.srgb(sp['belly'])
    col = base.outputs[2]
    if sp['pattern'] == 'rosette':
        v = n.new('ShaderNodeTexVoronoi'); v.feature = 'F1'; v.inputs['Scale'].default_value = sp['scale']; v.inputs['Randomness'].default_value = 1.0
        L.new(mp.outputs['Vector'], v.inputs['Vector'])
        nz = n.new('ShaderNodeTexNoise'); nz.inputs['Scale'].default_value = sp['scale'] * 2.2; L.new(mp.outputs['Vector'], nz.inputs['Vector'])
        dist = n.new('ShaderNodeMath'); dist.operation = 'ADD'; L.new(v.outputs['Distance'], dist.inputs[0])
        sc = n.new('ShaderNodeMath'); sc.operation = 'MULTIPLY'; sc.inputs[1].default_value = 0.12; L.new(nz.outputs['Fac'], sc.inputs[0]); L.new(sc.outputs[0], dist.inputs[1])
        r = n.new('ShaderNodeValToRGB'); r.color_ramp.interpolation = 'EASE'; e = r.color_ramp.elements
        e[0].position = 0.0; e[0].color = (1, 1, 1, 1); e[1].position = 0.22; e[1].color = (1, 1, 1, 1)
        for pos, c in ((0.28, sp['spot']), (0.38, sp['spot']), (0.44, '#FFFFFF')):
            el = r.color_ramp.elements.new(pos); el.color = H.srgb(c) if c != '#FFFFFF' else (1, 1, 1, 1)
        L.new(dist.outputs[0], r.inputs['Fac'])
        # rosette interior a touch darker than the base coat
        inner = n.new('ShaderNodeValToRGB'); ie = inner.color_ramp.elements
        ie[0].position = 0.0; ie[0].color = (0.82, 0.78, 0.74, 1); ie[1].position = 0.27; ie[1].color = (1, 1, 1, 1)
        L.new(dist.outputs[0], inner.inputs['Fac'])
        m1 = n.new('ShaderNodeMix'); m1.data_type = 'RGBA'; m1.blend_type = 'MULTIPLY'; m1.inputs[0].default_value = 1
        L.new(col, m1.inputs[6]); L.new(r.outputs['Color'], m1.inputs[7])
        m2 = n.new('ShaderNodeMix'); m2.data_type = 'RGBA'; m2.blend_type = 'MULTIPLY'; m2.inputs[0].default_value = 1
        L.new(m1.outputs[2], m2.inputs[6]); L.new(inner.outputs['Color'], m2.inputs[7]); col = m2.outputs[2]
    elif sp['pattern'] == 'saddle':
        # darker saddle down the back and over the shoulders, soft noise variation
        back = n.new('ShaderNodeMapRange'); back.inputs['From Min'].default_value = 0.02; back.inputs['From Max'].default_value = 0.12
        L.new(sep.outputs['Y'], back.inputs['Value'])
        m1 = n.new('ShaderNodeMix'); m1.data_type = 'RGBA'; L.new(back.outputs['Result'], m1.inputs[0])
        L.new(col, m1.inputs[6]); m1.inputs[7].default_value = H.srgb(sp['spot']); col = m1.outputs[2]
    nz2 = n.new('ShaderNodeTexNoise'); nz2.inputs['Scale'].default_value = 60; nz2.inputs['Detail'].default_value = 6; L.new(mp.outputs['Vector'], nz2.inputs['Vector'])
    vr = n.new('ShaderNodeMapRange'); vr.inputs['To Min'].default_value = 0.85; vr.inputs['To Max'].default_value = 1.1; L.new(nz2.outputs['Fac'], vr.inputs['Value'])
    m3 = n.new('ShaderNodeMix'); m3.data_type = 'RGBA'; m3.blend_type = 'MULTIPLY'; m3.inputs[0].default_value = 1
    L.new(col, m3.inputs[6]); L.new(vr.outputs['Result'], m3.inputs[7])
    L.new(m3.outputs[2], out_socket)

def skin_mat(sp, name):
    m = bpy.data.materials.new(name); m.use_nodes = True; b = m.node_tree.nodes['Principled BSDF']
    b.inputs['Roughness'].default_value = 0.85; b.inputs['Sheen Weight'].default_value = 0.6
    coat_nodes(m.node_tree, sp, b.inputs['Base Color']); return m

def strand_mat(sp, name):
    m = bpy.data.materials.new(name); m.use_nodes = True; nt = m.node_tree
    for nd in list(nt.nodes):
        if nd.type == 'BSDF_PRINCIPLED': nt.nodes.remove(nd)
    hb = nt.nodes.new('ShaderNodeBsdfHairPrincipled'); hb.parametrization = 'COLOR'
    hb.inputs['Roughness'].default_value = 0.42; hb.inputs['Radial Roughness'].default_value = 0.5; hb.inputs['Coat'].default_value = 0.05
    coat_nodes(nt, sp, hb.inputs['Color'])
    nt.links.new(hb.outputs[0], nt.nodes['Material Output'].inputs['Surface']); return m

def add_fur(ob, sp, length, count, mat_index, density_group=None, align=-1.6, clump=0.35, rough=0.006):
    ps_mod = ob.modifiers.new('fur', 'PARTICLE_SYSTEM'); ps = ps_mod.particle_system; st = ps.settings
    st.type = 'HAIR'; st.count = count; st.hair_length = length; st.use_advanced_hair = True
    st.emit_from = 'FACE'; st.use_even_distribution = True
    # advanced hair: strand length comes from the emission factors, so scale them to the wanted length
    st.normal_factor = length * 0.75; st.object_align_factor = (0, 0, align * length * 0.45); st.factor_random = length * 0.08
    st.child_type = 'INTERPOLATED'; st.rendered_child_count = 6; st.child_length = 1.0
    st.clump_factor = clump; st.clump_shape = 0.2; st.roughness_1 = rough; st.roughness_1_size = 0.6; st.roughness_endpoint = rough * 0.5
    st.root_radius = 1.0; st.tip_radius = 0.0; st.radius_scale = 0.0011; st.use_close_tip = True
    st.material = mat_index + 1
    st.display_step = 3; st.render_step = 4
    if density_group: ps.vertex_group_density = density_group
    return ps

# ------------------------------------------------------------------ heads (metaball volumes -> mesh)
def _mb_head(name, elements, m):
    mb = bpy.data.metaballs.new(name); mb.resolution = 0.02; mb.render_resolution = 0.006; mb.threshold = 0.6
    o = bpy.data.objects.new(name, mb); H.S.collection.objects.link(o)
    K = 1.0 / 0.574          # radius -> surface distance (threshold 0.6, stiffness 2), as in Pip's generator
    for kind, co, size, r in elements:
        e = mb.elements.new(type=kind); e.co = co; e.stiffness = 2.0
        if kind == 'ELLIPSOID': e.radius = K; e.size_x, e.size_y, e.size_z = size          # size = semi-axes in metres
        elif kind == 'CAPSULE': e.radius = r * K; e.size_x = size[0]                       # r = tube radius, size_x = half length
        else: e.radius = r * K
    bpy.context.view_layer.update()
    bpy.ops.object.select_all(action='DESELECT'); o.select_set(True); bpy.context.view_layer.objects.active = o
    bpy.ops.object.convert(target='MESH'); me_o = bpy.context.active_object; me_o.name = name
    for p in me_o.data.polygons: p.use_smooth = True
    sd = me_o.modifiers.new('sm', 'SMOOTH'); sd.factor = 0.5; sd.iterations = 3
    me_o.data.materials.append(m)
    return me_o

def _eye(name, loc, r, iris_col, slit, bone, rot=(0, 0, 0)):
    eyem = bpy.data.materials.get('eye_' + iris_col)
    if not eyem:
        eyem = bpy.data.materials.new('eye_' + iris_col); eyem.use_nodes = True; nt = eyem.node_tree; b = nt.nodes['Principled BSDF']
        b.inputs['Roughness'].default_value = 0.05; b.inputs['Coat Weight'].default_value = 1.0
        tc = nt.nodes.new('ShaderNodeTexCoord'); g = nt.nodes.new('ShaderNodeTexGradient'); g.gradient_type = 'SPHERICAL'
        mpn = nt.nodes.new('ShaderNodeMapping'); mpn.inputs['Scale'].default_value = (1.6, 1.6, 1.6)
        nt.links.new(tc.outputs['Object'], mpn.inputs['Vector']); nt.links.new(mpn.outputs['Vector'], g.inputs['Vector'])
        r_ = nt.nodes.new('ShaderNodeValToRGB'); e = r_.color_ramp.elements
        e[0].position = 0.0; e[0].color = H.srgb('#3A2A08'); e[1].position = 0.55; e[1].color = H.srgb(iris_col)
        el = r_.color_ramp.elements.new(0.85); el.color = H.srgb('#0A0A0A')
        nt.links.new(g.outputs['Fac'], r_.inputs['Fac']); nt.links.new(r_.outputs['Color'], b.inputs['Base Color'])
    o = blob(name, loc, (r, r * 0.6, r * 0.85), eyem, bone, rot=rot)
    pup = mat('pupil', '#050505', rough=0.1, coat=1.0)
    blob(name + '_pupil', (loc[0], loc[1] - r * 0.55, loc[2]), (r * (0.18 if slit else 0.42), r * 0.1, r * (0.75 if slit else 0.42)), pup, bone)
    blob(name + '_glint', (loc[0] + r * 0.25, loc[1] - r * 0.62, loc[2] + r * 0.3), (r * 0.12, r * 0.05, r * 0.12), mat('glint', '#FFFFFF', emit=3.0), bone)
    return o

def cat_head(sp, p, female=False):
    k = 1.0 if female else 1.1                      # heroic head size relative to the human head
    hc = p['head'] + Vector((0, -0.005, 0.07))
    V = lambda x, y, z: hc + Vector((x * k, y * k, z * k))
    E = [('ELLIPSOID', V(0, 0.015, 0.02), (0.078 * k, 0.088 * k, 0.074 * k), 0),              # cranium
         ('ELLIPSOID', V(0, -0.085, -0.035), (0.046 * k, 0.05 * k, 0.034 * k), 0),            # muzzle, projecting forward
         ('ELLIPSOID', V(0.045, -0.04, -0.015), (0.04 * k, 0.04 * k, 0.034 * k), 0),          # cheekbones
         ('ELLIPSOID', V(-0.045, -0.04, -0.015), (0.04 * k, 0.04 * k, 0.034 * k), 0),
         ('ELLIPSOID', V(0, -0.068, -0.078), (0.034 * k, 0.04 * k, 0.022 * k), 0),            # chin / lower jaw
         ('CAPSULE', V(0, -0.068, 0.036), (0.035 * k, 0, 0), 0.026 * k),                      # brow ridge
         ('ELLIPSOID', V(0, 0.03, -0.1), (0.062 * k, 0.064 * k, 0.075 * k), 0),               # neck
         ('ELLIPSOID', p['neck'] + Vector((0, 0.025, 0.0)), (0.075 * k, 0.07 * k, 0.06 * k), 0)]   # neck base into the traps
    head = _mb_head('AnimalHead', E, skin_mat(sp, 'headskin'))
    head.data.materials.append(strand_mat(sp, 'headfur'))
    nosec = '#B9716C' if sp['pattern'] == 'rosette' and sp['base'].startswith('#D') else '#C8848E'
    blob('NosePad', V(0, -0.133, -0.016), (0.024 * k, 0.012 * k, 0.016 * k), mat('nosepad', nosec, rough=0.35, coat=0.4), 'Head', rot=(math.radians(-25), 0, 0))
    lipm = mat('lip', '#1A1210', rough=0.4)
    tube('Mouth', [V(-0.028, -0.108, -0.066), V(0, -0.118, -0.056), V(0.028, -0.108, -0.066)], 0.0032 * k, lipm, 'Head')
    tube('Philtrum', [V(0, -0.131, -0.03), V(0, -0.12, -0.054)], 0.0026 * k, lipm, 'Head')
    line = mat('furline', sp['spot'])
    for s in (1, -1):
        _eye('AEye%d' % s, V(s * 0.036, -0.074, 0.016), 0.015 * k, '#D9A93A' if sp['base'].startswith('#D') else '#9FD0E2', True, 'Head', rot=(0, 0, math.radians(s * -14)))
        # heavy upper lid / brow shadow gives the fierce, focused look
        tube('Lid%d' % s, [V(s * 0.017, -0.086, 0.026), V(s * 0.037, -0.091, 0.031), V(s * 0.057, -0.08, 0.02)], 0.0055 * k, line, 'Head')
        tube('Tear%d' % s, [V(s * 0.024, -0.088, 0.004), V(s * 0.027, -0.11, -0.022), V(s * 0.033, -0.122, -0.044)], 0.003 * k, line, 'Head')
        ear = blob('AEar%d' % s, V(s * 0.06, 0.03, 0.07), (0.024 * k, 0.01 * k, 0.025 * k), skin_mat(sp, 'earskin'), 'Head', rot=(math.radians(15), math.radians(s * -30), 0))
        blob('AEarIn%d' % s, V(s * 0.06, 0.021, 0.068), (0.016 * k, 0.004 * k, 0.017 * k), mat('earin', '#2E2420' if sp['base'].startswith('#D') else '#B9AFAA', rough=0.8), 'Head', rot=(math.radians(15), math.radians(s * -30), 0))
        for kk in range(3):
            tube('Whisk%d%d' % (s, kk), [V(s * 0.03, -0.12, -0.035 - kk * 0.006), V(s * 0.075, -0.118, -0.032 - kk * 0.011), V(s * 0.115, -0.095, -0.025 - kk * 0.016)], 0.0005, mat('whisk', '#F4F2EC', rough=0.3), 'Head')
    return head

def wolf_head(sp, p):
    hc = p['head'] + Vector((0, 0, 0.1))
    E = [('ELLIPSOID', hc + Vector((0, 0.02, 0.025)), (0.1, 0.115, 0.095), 0.12),
         ('CAPSULE', hc + Vector((0, -0.11, -0.03)), (0.055, 0, 0), 0.055),                        # long muzzle (rotated below)
         ('ELLIPSOID', hc + Vector((0, -0.09, -0.075)), (0.045, 0.08, 0.03), 0.06),               # lower jaw
         ('ELLIPSOID', hc + Vector((0.07, -0.01, -0.03)), (0.07, 0.06, 0.08), 0.09),              # cheek ruff L
         ('ELLIPSOID', hc + Vector((-0.07, -0.01, -0.03)), (0.07, 0.06, 0.08), 0.09),             # cheek ruff R
         ('CAPSULE', hc + Vector((0, -0.06, 0.05)), (0.04, 0, 0), 0.045),                          # brow
         ('ELLIPSOID', hc + Vector((0, 0.03, -0.13)), (0.1, 0.095, 0.11), 0.12)]                   # thick neck mane
    mb_rot = Vector((0, 1, 0))
    head = _mb_head('AnimalHead', E, skin_mat(sp, 'headskin'))
    head.data.materials.append(strand_mat(sp, 'headfur'))
    # the capsule lies along X by default: rebuild the muzzle explicitly as a tapered cone pointing forward
    bpy.ops.mesh.primitive_cone_add(vertices=24, radius1=0.055, radius2=0.03, depth=0.13, location=hc + Vector((0, -0.14, -0.035)), rotation=(math.radians(90), 0, 0))
    mz = bpy.context.active_object; mz.name = 'Muzzle'; mz.scale = (1.0, 0.85, 1.0)
    for pp in mz.data.polygons: pp.use_smooth = True
    sd = mz.modifiers.new('s', 'SUBSURF'); sd.levels = 2; sd.render_levels = 2; mz.data.materials.append(skin_mat(sp, 'muzzleskin')); O._ride(mz, 'Head')
    blob('NosePad', hc + Vector((0, -0.21, -0.02)), (0.022, 0.016, 0.016), mat('wnose', '#0E0E10', rough=0.25, coat=0.5), 'Head')
    tube('Mouth', [hc + Vector((-0.04, -0.12, -0.075)), hc + Vector((-0.01, -0.19, -0.065)), hc + Vector((0.01, -0.19, -0.065)), hc + Vector((0.04, -0.12, -0.075))], 0.0035, mat('lip', '#151012'), 'Head')
    for s in (1, -1):
        _eye('AEye%d' % s, hc + Vector((s * 0.042, -0.085, 0.03)), 0.019, '#E8B23A', False, 'Head', rot=(0, 0, math.radians(s * -18)))
        tube('Lid%d' % s, [hc + Vector((s * 0.022, -0.1, 0.035)), hc + Vector((s * 0.045, -0.105, 0.05)), hc + Vector((s * 0.065, -0.09, 0.04))], 0.0045, mat('furline', sp['spot']), 'Head')
        # tall pointed ears
        bpy.ops.mesh.primitive_cone_add(vertices=16, radius1=0.04, radius2=0.004, depth=0.11, location=hc + Vector((s * 0.065, 0.03, 0.135)), rotation=(math.radians(-8), math.radians(s * 14), 0))
        ear = bpy.context.active_object; ear.name = 'AEar%d' % s; ear.scale = (1.0, 0.45, 1.0)
        for pp in ear.data.polygons: pp.use_smooth = True
        ear.data.materials.append(skin_mat(sp, 'earskin')); O._ride(ear, 'Head')
        blob('AEarIn%d' % s, hc + Vector((s * 0.065, 0.014, 0.125)), (0.022, 0.005, 0.045), mat('earin', '#2E2A28', rough=0.8), 'Head', rot=(math.radians(-8), math.radians(s * 14), 0))
    return head

def bull_head(sp, p):
    hc = p['head'] + Vector((0, 0, 0.1))
    E = [('ELLIPSOID', hc + Vector((0, 0.01, 0.03)), (0.125, 0.12, 0.1), 0.13),
         ('ELLIPSOID', hc + Vector((0, -0.1, -0.05)), (0.085, 0.075, 0.075), 0.11),               # broad muzzle
         ('ELLIPSOID', hc + Vector((0, -0.075, -0.11)), (0.07, 0.06, 0.04), 0.08),                # jaw
         ('ELLIPSOID', hc + Vector((0.07, -0.04, -0.02)), (0.06, 0.06, 0.06), 0.08),
         ('ELLIPSOID', hc + Vector((-0.07, -0.04, -0.02)), (0.06, 0.06, 0.06), 0.08),
         ('CAPSULE', hc + Vector((0, -0.07, 0.06)), (0.06, 0, 0), 0.055),                          # heavy brow
         ('ELLIPSOID', hc + Vector((0, 0.03, -0.15)), (0.13, 0.11, 0.13), 0.14)]                   # massive neck
    head = _mb_head('AnimalHead', E, skin_mat(sp, 'headskin'))
    head.data.materials.append(strand_mat(sp, 'headfur'))
    snout = mat('snout', '#5A4A44', rough=0.45, coat=0.25)
    blob('Snout', hc + Vector((0, -0.165, -0.06)), (0.075, 0.03, 0.05), snout, 'Head')
    for s in (1, -1):
        blob('Nostril%d' % s, hc + Vector((s * 0.032, -0.19, -0.055)), (0.014, 0.01, 0.01), mat('nostril', '#120D0C', rough=0.3), 'Head')
        _eye('AEye%d' % s, hc + Vector((s * 0.06, -0.085, 0.025)), 0.018, '#5A2A12', False, 'Head', rot=(0, 0, math.radians(s * -25)))
        tube('Lid%d' % s, [hc + Vector((s * 0.04, -0.1, 0.04)), hc + Vector((s * 0.062, -0.105, 0.05)), hc + Vector((s * 0.082, -0.09, 0.04))], 0.006, mat('furline', '#120C0A'), 'Head')
        # ears out to the side under the horns
        blob('AEar%d' % s, hc + Vector((s * 0.13, 0.02, 0.06)), (0.05, 0.018, 0.025), skin_mat(sp, 'earskin'), 'Head', rot=(0, math.radians(s * 15), math.radians(s * 20)))
        # horns: thick base, sweeping out, up and forward, ivory to dark tip
        hp = [hc + Vector((s * 0.1, 0.0, 0.1)), hc + Vector((s * 0.17, -0.01, 0.12)), hc + Vector((s * 0.23, -0.04, 0.17)), hc + Vector((s * 0.24, -0.08, 0.23))]
        hm = bpy.data.materials.get('horn')
        if not hm:
            hm = bpy.data.materials.new('horn'); hm.use_nodes = True; nt = hm.node_tree; b = nt.nodes['Principled BSDF']
            b.inputs['Roughness'].default_value = 0.35; b.inputs['Coat Weight'].default_value = 0.6
            sp_ = nt.nodes.new('ShaderNodeTexCoord'); sep = nt.nodes.new('ShaderNodeSeparateXYZ'); nt.links.new(sp_.outputs['Object'], sep.inputs[0])
            mr = nt.nodes.new('ShaderNodeMapRange'); mr.inputs['From Min'].default_value = hc.z + 0.12; mr.inputs['From Max'].default_value = hc.z + 0.24
            nt.links.new(sep.outputs['Z'], mr.inputs['Value'])
            r = nt.nodes.new('ShaderNodeValToRGB'); e = r.color_ramp.elements; e[0].color = H.srgb('#E8DCC2'); e[1].color = H.srgb('#2A2420')
            nt.links.new(mr.outputs['Result'], r.inputs['Fac']); nt.links.new(r.outputs['Color'], b.inputs['Base Color'])
        t = tube('Horn%d' % s, hp, 0.03, hm, 'Head'); t.data.bevel_resolution = 6
        taper = bpy.data.curves.new('htaper%d' % s, 'CURVE'); taper.dimensions = '2D'; spl = taper.splines.new('POLY'); spl.points.add(2)
        for pt, xy in zip(spl.points, ((0, 1.0), (0.6, 0.6), (1.0, 0.05))): pt.co = (xy[0], xy[1], 0, 1)
        to = bpy.data.objects.new('htaperobj%d' % s, taper); H.S.collection.objects.link(to); to.hide_render = True; t.data.taper_object = to
        torus('HornRing%d' % s, hp[1], 0.03, 0.007, mat('gold', O.GOLD, rough=0.22, metal=1.0, coat=0.5), 'Head', rot=(0, math.radians(90), 0))
    return head

# ------------------------------------------------------------------ body prep
def hide_human_head():
    body = H.BODY; g = body.vertex_groups.get('nohead') or body.vertex_groups.new(name='nohead')
    hi = body.vertex_groups['Head'].index; ni = body.vertex_groups['neck_01'].index
    keep = []
    for v in body.data.vertices:
        w = sum(e.weight for e in v.groups if e.group == hi)
        if w < 0.5: keep.append(v.index)
    g.add(keep, 1.0, 'REPLACE')
    mk = body.modifiers.new('mask', 'MASK'); mk.vertex_group = 'nohead'
    while list(body.modifiers).index(mk) > 0:
        bpy.context.view_layer.objects.active = body; bpy.ops.object.modifier_move_up(modifier=mk.name)
    for o in H.S.objects:
        if o.type == 'MESH' and o.name.startswith(('Eyes', 'Eyebrows', 'Face')): o.hide_render = True

def fur_density(lv, p):
    """Fur grows where skin shows at this tier (not under shorts, boots, belts or jackets)."""
    body = H.BODY; g = body.vertex_groups.get('furdens') or body.vertex_groups.new(name='furdens')
    hipz = p['hip'].z; kn = p['kn_l'].z; boot = p['ft_l'].z + 0.2
    for v in body.data.vertices:
        c = body.matrix_world @ v.co; w = 1.0
        if kn + 0.12 < c.z < hipz + 0.09: w = 0.0                                   # shorts
        if c.z < boot: w = 0.0                                                       # boots
        if lv >= 3 and c.z > hipz - 0.02 and c.y > -0.04 and abs(c.x) < p['sh_l'].x + 0.02: w = 0.0   # open jacket covers back/sides
        if abs(c.x) > p['wr_l'].x - 0.01: w = 0.15                                  # hands: short sparse fur
        if w: g.add([v.index], w, 'REPLACE')
    return 'furdens'

def fighter_kit(lv, spec, p, female):
    M = D.mats(spec['accent'], '#14151A'); gold = M['gold']; trim = gold if lv >= 2 else M['acc']
    shorts = shell('FightShorts', [((0, 0, p['hip'].z + 0.08), (0, 0, -1)), ((0, 0, p['kn_l'].z + 0.11), (0, 0, 1))], 0.024, 0.007, M['accd'] if lv >= 2 else M['acc'], rim=trim, drape=3)
    boundary_ring(shorts, (0, 0.04, p['hip'].z + 0.08), (0, 0, 1), trim, minor=0.016, bone='pelvis')
    for s, ft, cf in ((1, 'ft_l', 'calf_l'), (-1, 'ft_r', 'calf_r')):    # wrestling boots
        f0 = p[ft]; top = f0.z + 0.2
        bt = shell('Boot' + cf, [((0, 0, top), (0, 0, -1)), ((f0.x - s * 0.1, 0, 0), (s, 0, 0)), ((f0.x + s * 0.1, 0, 0), (-s, 0, 0))], 0.014, 0.006, M['shoe'], rim=trim)
        boundary_ring(bt, (f0.x, f0.y, top), (0, 0, 1), trim, minor=0.012, bone=cf)
        blob('BootSole' + cf, (f0.x, (f0.y + p['ball_l' if s > 0 else 'ball_r'].y) / 2 - 0.015, 0.012), (0.06, 0.15, 0.016), gold if lv >= 2 else M['dark'], 'foot_l' if s > 0 else 'foot_r')
    D.hand_wraps(M, p, lv, M['wrap'] if lv == 1 else M['dark'])
    if lv >= 2:
        for s, la in ((1, 'lowerarm_l'), (-1, 'lowerarm_r')):
            w0 = p['wr_l' if s > 0 else 'wr_r']
            torus('Cuff' + la, w0 + Vector((-s * 0.06, 0, 0)), 0.045, 0.012, gold, la, rot=(0, math.radians(90), 0))
        D.chain(M, p, female)
    if lv == 2:
        for s, ua in ((1, 'upperarm_l'), (-1, 'upperarm_r')):
            u0 = bone_head(ua)
            shell('Pad' + ua, [((u0.x - s * 0.04, 0, 0), (s, 0, 0)), ((u0.x + s * 0.09, 0, 0), (-s, 0, 0)), ((0, 0, u0.z - 0.01), (0, 0, 1))], 0.032, 0.01, M['dark'], rim=gold)
    if lv >= 3:
        D.champ_belt(M, p, M['glow'])
        # sleeveless open long-line jacket (keeps the furred arms and chest on show)
        D.jacket('LuxeVest', M, p, p['hip'].z - 0.04, p['sh_l'].x + 0.01, mat('luxe', spec.get('luxe', '#111'), rough=0.55, sheen=0.4, weave=0.2), gold, gap=0.11, lift=0.05, collar=True)

def build(lv, spec):
    sp = dict(SPEC[spec['species']]); p = D.P(); female = spec['body'] == 'female'
    body = H.BODY
    body.data.materials.clear(); body.data.materials.append(skin_mat(sp, 'bodyskin')); body.data.materials.append(strand_mat(sp, 'bodyfur'))
    hide_human_head()
    if sp['bulk'] > 0 and 'muscle' in body.vertex_groups:
        d = body.modifiers.new('speciesbulk', 'DISPLACE'); d.strength = sp['bulk']; d.mid_level = 0; d.vertex_group = 'muscle'
        arm_i = next(i for i, m in enumerate(body.modifiers) if m.type == 'ARMATURE')
        while list(body.modifiers).index(d) > arm_i + 1:
            bpy.context.view_layer.objects.active = body; bpy.ops.object.modifier_move_up(modifier=d.name)
        O.STATE['bulk'] = O.STATE.get('bulk', 0) + sp['bulk']
    dens = fur_density(lv, p)
    add_fur(body, sp, sp['fur_len'], 9000, 1, density_group=dens)
    head = {'jaguar': lambda: cat_head(sp, p), 'snowleopard': lambda: cat_head(sp, p, female=True), 'wolf': lambda: wolf_head(sp, p), 'bull': lambda: bull_head(sp, p)}[spec['species']]()
    O._ride(head, 'Head')
    add_fur(head, sp, sp['head_fur'] * 0.55, 5000, 1, align=-0.6, clump=0.2)
    if spec['species'] in ('jaguar', 'snowleopard', 'wolf'):
        D.claws(mat('claw', '#EDE6D8', rough=0.35), p, mat('claw', '#EDE6D8', rough=0.35)) if False else D.claws(None, p, mat('claw', '#EDE6D8', rough=0.35))
    if spec['species'] in ('jaguar', 'snowleopard'):
        D.tail(None, skin_mat(sp, 'tailskin'), p)
    fighter_kit(lv, spec, p, female)
    H.S.cycles_curves.shape = 'RIBBONS'
