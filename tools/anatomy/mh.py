"""Realistic, poseable anatomy mannequin from MakeHuman's CC0 assets (base mesh + muscular male target + default
skeleton & weights). Each face is tagged with a muscle group so any group can be shaded red.
Assets are fetched by fetch_mh.sh into tools/anatomy/mh/ (CC0 — see MAKEHUMAN_CC0.txt)."""
import bpy, bmesh, json, math, os
from mathutils import Vector, Matrix

H = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'mh')
S = None
OBJ = {}
MAT = {}
GROUPS = ['abdominals', 'abductors', 'adductors', 'biceps', 'calves', 'chest', 'forearms', 'glutes', 'hamstrings',
          'lats', 'lower back', 'middle back', 'neck', 'quadriceps', 'shoulders', 'traps', 'triceps']
FACE_GROUP = []          # per body face: group name or ''

def srgb(h):
    h = h.lstrip('#'); c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c) + (1.0,)

def conv(x, y, z):        # MakeHuman: decimetres, Y up, facing +Z  →  metres, Z up, facing -Y
    return Vector((x * 0.1, -z * 0.1, y * 0.1))

def load_obj():
    verts, faces, group = [], [], None
    for line in open(os.path.join(H, 'base.obj')):
        if line.startswith('v '):
            _, x, y, z = line.split()[:4]; verts.append([float(x), float(y), float(z)])
        elif line.startswith('g '):
            group = line.split()[1]
        elif line.startswith('f ') and group == 'body':
            faces.append([int(t.split('/')[0]) - 1 for t in line.split()[1:]])
    return verts, faces

def apply_target(verts, name, w):
    if w == 0: return
    for line in open(os.path.join(H, name)):
        if not line.strip() or line.startswith('#'): continue
        i, dx, dy, dz = line.split(); i = int(i)
        verts[i][0] += float(dx) * w; verts[i][1] += float(dy) * w; verts[i][2] += float(dz) * w

def mat(name, color, rough=0.5, fibre=False, holdout=False):
    m = bpy.data.materials.new(name); m.use_nodes = True
    n = m.node_tree.nodes; l = m.node_tree.links
    if holdout:
        n.remove(n['Principled BSDF']); h = n.new('ShaderNodeHoldout'); l.new(h.outputs[0], n['Material Output'].inputs[0])
        MAT[name] = m; return m
    b = n['Principled BSDF']; b.inputs['Base Color'].default_value = srgb(color); b.inputs['Roughness'].default_value = rough
    b.inputs['Subsurface Weight'].default_value = 0.05; b.inputs['Subsurface Radius'].default_value = (0.3, 0.2, 0.2); b.inputs['Subsurface Scale'].default_value = 0.01
    if fibre:
        tc = n.new('ShaderNodeTexCoord'); w = n.new('ShaderNodeTexWave'); w.wave_type = 'BANDS'; w.bands_direction = 'Z'
        w.inputs['Scale'].default_value = 60.0; w.inputs['Distortion'].default_value = 4.0; w.inputs['Detail'].default_value = 3.0
        l.new(tc.outputs['Object'], w.inputs['Vector'])
        r = n.new('ShaderNodeValToRGB'); e = r.color_ramp.elements; c = srgb(color)
        e[0].color = (c[0] * 0.78, c[1] * 0.78, c[2] * 0.78, 1); e[1].color = c
        l.new(w.outputs['Fac'], r.inputs['Fac']); l.new(r.outputs['Color'], b.inputs['Base Color'])
        bm = n.new('ShaderNodeBump'); bm.inputs['Strength'].default_value = 0.25; bm.inputs['Distance'].default_value = 0.001
        l.new(w.outputs['Fac'], bm.inputs['Height']); l.new(bm.outputs['Normal'], b.inputs['Normal'])
    MAT[name] = m; return m

def build_mesh(muscle=1.0):
    verts, faces = load_obj()
    # MakeHuman macro model: male young (ethnic mix) + muscular/lean universal targets
    for eth, w in (('caucasian', 0.34), ('african', 0.33), ('asian', 0.33)): apply_target(verts, eth + '-male-young.target', w)
    apply_target(verts, 'male-maxmuscle.target', muscle * 0.5)
    apply_target(verts, 'male-maxmuscle-minweight.target', muscle * 0.5)
    used = sorted({i for f in faces for i in f}); remap = {g: k for k, g in enumerate(used)}
    me = bpy.data.meshes.new('Body')
    me.from_pydata([conv(*verts[g]) for g in used], [], [[remap[i] for i in f] for f in faces])
    me.update()
    o = bpy.data.objects.new('Body', me); S.collection.objects.link(o)
    for p in me.polygons: p.use_smooth = True
    sub = o.modifiers.new('sub', 'SUBSURF'); sub.levels = 1; sub.render_levels = 2
    OBJ['Body'] = o
    return o, verts, used, remap

def joint_pos(skel, verts, name):
    ids = skel['joints'][name]
    s = Vector((0, 0, 0))
    for i in ids: s += conv(*verts[i])
    return s / len(ids)

def build_armature(o, verts, used, remap):
    skel = json.load(open(os.path.join(H, 'default.mhskel')))
    ad = bpy.data.armatures.new('Rig'); arm = bpy.data.objects.new('Rig', ad); S.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm; bpy.ops.object.mode_set(mode='EDIT')
    eb = {}
    for name, b in skel['bones'].items():
        e = ad.edit_bones.new(name); e.head = joint_pos(skel, verts, b['head']); e.tail = joint_pos(skel, verts, b['tail'])
        if (e.tail - e.head).length < 1e-4: e.tail = e.head + Vector((0, 0, 0.01))
        eb[name] = e
    for name, b in skel['bones'].items():
        if b['parent']: eb[name].parent = eb[b['parent']]
    bpy.ops.object.mode_set(mode='OBJECT')
    w = json.load(open(os.path.join(H, 'default_weights.mhw')))['weights']
    for bone, pairs in w.items():
        vg = o.vertex_groups.new(name=bone)
        for vi, wt in pairs:
            if vi in remap: vg.add([remap[vi]], wt, 'REPLACE')
    o.parent = arm
    m = o.modifiers.new('arm', 'ARMATURE'); m.object = arm
    o.modifiers.move(len(o.modifiers) - 1, 0)          # deform before subdivision
    OBJ['Rig'] = arm
    return arm

def classify(o, arm):
    """Tag each face with a muscle group from bone weights, surface direction and landmarks."""
    me = o.data; bones = arm.data.bones
    J = lambda n, end='head': (arm.matrix_world @ (bones[n].head_local if end == 'head' else bones[n].tail_local))
    shL = J('upperarm01.L'); hipL = J('upperleg01.L'); neck = J('neck01'); pel = J('pelvis.L') if 'pelvis.L' in bones else J('root')
    shZ, shX, hipZ = shL.z, abs(shL.x), hipL.z
    kneeZ = J('lowerleg01.L').z
    chestTop, chestLow = shZ - 0.02, shZ - 0.21
    waist = (shZ + hipZ) / 2 - 0.05
    names = [g.name for g in o.vertex_groups]
    def dom(v):
        best = max(v.groups, key=lambda g: g.weight, default=None)
        return names[best.group] if best else ''
    vg = []
    for v in me.vertices:
        p = v.co; n = v.normal; d = dom(v); ax = abs(p.x); side = 1 if p.x >= 0 else -1
        inward = n.x * side < -0.35; outward = n.x * side > 0.45
        front = n.y < -0.25; back = n.y > 0.25
        g = ''
        if d.startswith('lowerarm'): g = 'forearms'
        elif d.startswith('upperarm'):
            if (p - Vector((side * shX, shL.y, shZ))).length < 0.11 and n.z > -0.3: g = 'shoulders'
            elif front: g = 'biceps'
            elif back or n.z < -0.3: g = 'triceps'
            else: g = 'biceps' if n.y < 0 else 'triceps'
        elif d.startswith('upperleg'):
            if abs(p.z - kneeZ) < 0.035: g = ''
            elif back and p.z > hipZ - 0.09 and ax < 0.17: g = 'glutes'
            elif inward and n.y > -0.35: g = 'adductors'
            elif back: g = 'hamstrings'
            elif outward and p.z > hipZ - 0.12: g = 'abductors'
            else: g = 'quadriceps'
        elif d.startswith('lowerleg'):
            if abs(p.z - kneeZ) < 0.04 or p.z < kneeZ - 0.33: g = ''
            elif back or (outward and n.y > 0.1): g = 'calves'
        elif d.startswith('neck') or d == 'head':
            if p.z < neck.z + 0.09 and not back: g = 'neck'
            elif back and p.z < neck.z + 0.06: g = 'traps'
        elif d.startswith('clavicle') or d.startswith('shoulder') or d.startswith('spine') or d.startswith('breast') or d.startswith('pelvis') or d == 'root':
            # landmark-relative coordinates
            dz = shZ - p.z                                   # metres below the shoulder joints
            pec = ((ax - 0.085) / 0.092) ** 2 + ((p.z - (shZ - 0.105)) / 0.078) ** 2
            trap_w = 0.20 - dz * 0.62                         # trapezius: wide at the top, narrowing to a point mid-back
            if (p - Vector((side * shX, shL.y, shZ))).length < 0.10 and n.z > -0.2 and not (front and ax < shX * 0.6): g = 'shoulders'
            elif front and pec < 1.0 and ax > 0.012: g = 'chest'
            elif (back or n.z > 0.45) and ((-0.10 < dz < 0.10 and ax < 0.21 - max(dz, 0) * 0.5) or (0.10 <= dz < 0.30 and ax < 0.055 - (dz - 0.10) * 0.2)): g = 'traps'
            elif front and hipZ + 0.035 < p.z < shZ - 0.20 and ax < 0.07 and ax > 0.006: g = 'abdominals'
            elif ((outward and n.y < 0.10) or (front and ax >= 0.078)) and hipZ + 0.02 < p.z < shZ - 0.20: g = 'abdominals'      # obliques
            elif back and 0.08 < dz < 0.24 and ax < 0.10: g = 'middle back'
            elif (back or outward) and 0.10 < dz < (shZ - hipZ - 0.10) and ax >= 0.07: g = 'lats'
            elif back and ax < 0.055 and hipZ + 0.05 < p.z <= waist + 0.04: g = 'lower back'
            elif back and p.z <= hipZ + 0.07 and ax < 0.16: g = 'glutes'
            elif outward and abs(p.z - hipZ) < 0.09: g = 'abductors'
        vg.append(g)
    FACE_GROUP.clear()
    # soft masks: one float attribute per group, smoothed over the mesh so edges fade instead of stair-stepping
    nb = [[] for _ in me.vertices]
    for e in me.edges: a_, b_ = e.vertices; nb[a_].append(b_); nb[b_].append(a_)
    for g in GROUPS:
        m = [1.0 if x == g else 0.0 for x in vg]
        for _ in range(3):
            m = [0.5 * m[i] + 0.5 * (sum(m[j] for j in nb[i]) / len(nb[i]) if nb[i] else m[i]) for i in range(len(m))]
        at = me.attributes.new('m_' + g.replace(' ', '_'), 'FLOAT', 'POINT')
        at.data.foreach_set('value', m)
    for poly in me.polygons:
        votes = {}
        for vi in poly.vertices: votes[vg[vi]] = votes.get(vg[vi], 0) + 1
        FACE_GROUP.append(max(votes, key=votes.get))
    me.materials.append(MAT['body'])

def paint(groups, holdout_rest=False):
    """Shade [groups] red with soft edges; holdout_rest → everything else transparent (overlay renders)."""
    nt = MAT['body'].node_tree
    for k in range(4):
        nt.nodes['attr%d' % k].attribute_name = ('m_' + groups[k].replace(' ', '_')) if k < len(groups) else 'none'
    nt.nodes['mode'].outputs[0].default_value = 1.0 if holdout_rest else 0.0

def lights_camera():
    def area(name, loc, energy, size):
        ld = bpy.data.lights.new(name, 'AREA'); ld.energy = energy; ld.size = size
        lo = bpy.data.objects.new(name, ld); S.collection.objects.link(lo); lo.location = loc
        lo.rotation_euler = (Vector((0, 0, 1.0)) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
    area('Key', (-2.2, -4.0, 3.2), 750, 2.2); area('Fill', (3.0, -3.0, 1.0), 160, 4); area('Rim', (1.5, 4.0, 3.0), 520, 1.5); area('Key2', (-2.5, 4.0, 3.5), 420, 2.2)
    cd = bpy.data.cameras.new('Cam'); cd.type = 'ORTHO'; cd.ortho_scale = 2.0
    cam = bpy.data.objects.new('Cam', cd); S.collection.objects.link(cam); S.camera = cam; OBJ['Cam'] = cam
    set_view(False)

def set_view(back, cz=0.90):
    cam = OBJ['Cam']
    if back: cam.location = (0, 6, cz); cam.rotation_euler = (math.radians(90), 0, math.radians(180))
    else: cam.location = (0, -6, cz); cam.rotation_euler = (math.radians(90), 0, 0)

def body_material():
    m = bpy.data.materials.new('body'); m.use_nodes = True; nt = m.node_tree; n = nt.nodes; l = nt.links
    out = n['Material Output']; n.remove(n['Principled BSDF'])
    skin = n.new('ShaderNodeBsdfPrincipled'); skin.inputs['Base Color'].default_value = srgb('#B8B8BD'); skin.inputs['Roughness'].default_value = 0.48
    skin.inputs['Specular IOR Level'].default_value = 0.35
    red = n.new('ShaderNodeBsdfPrincipled'); red.inputs['Roughness'].default_value = 0.42
    # muscle fibres on the red: banded noise along the body's vertical axis
    tc = n.new('ShaderNodeTexCoord'); wv = n.new('ShaderNodeTexWave'); wv.wave_type = 'BANDS'; wv.bands_direction = 'Z'
    wv.inputs['Scale'].default_value = 55.0; wv.inputs['Distortion'].default_value = 5.0; wv.inputs['Detail'].default_value = 3.0
    l.new(tc.outputs['Object'], wv.inputs['Vector'])
    rr = n.new('ShaderNodeValToRGB'); e = rr.color_ramp.elements; c = srgb('#E4472D'); e[0].color = (c[0] * 0.72, c[1] * 0.72, c[2] * 0.72, 1); e[1].color = c
    l.new(wv.outputs['Fac'], rr.inputs['Fac']); l.new(rr.outputs['Color'], red.inputs['Base Color'])
    bm = n.new('ShaderNodeBump'); bm.inputs['Strength'].default_value = 0.22; bm.inputs['Distance'].default_value = 0.0008
    l.new(wv.outputs['Fac'], bm.inputs['Height']); l.new(bm.outputs['Normal'], red.inputs['Normal'])
    mx = None
    for k in range(4):
        at = n.new('ShaderNodeAttribute'); at.name = 'attr%d' % k; at.attribute_name = 'none'
        if mx is None: mx = at.outputs['Fac']
        else:
            mm = n.new('ShaderNodeMath'); mm.operation = 'MAXIMUM'; l.new(mx, mm.inputs[0]); l.new(at.outputs['Fac'], mm.inputs[1]); mx = mm.outputs[0]
    sharp = n.new('ShaderNodeMapRange'); sharp.inputs['From Min'].default_value = 0.30; sharp.inputs['From Max'].default_value = 0.62
    l.new(mx, sharp.inputs['Value'])
    mix = n.new('ShaderNodeMixShader'); l.new(sharp.outputs['Result'], mix.inputs['Fac']); l.new(skin.outputs[0], mix.inputs[1]); l.new(red.outputs[0], mix.inputs[2])
    # overlay mode: everything that isn't red becomes holdout (transparent)
    hold = n.new('ShaderNodeHoldout'); mode = n.new('ShaderNodeValue'); mode.name = 'mode'; mode.outputs[0].default_value = 0.0
    inv = n.new('ShaderNodeMath'); inv.operation = 'SUBTRACT'; inv.inputs[0].default_value = 1.0; l.new(sharp.outputs['Result'], inv.inputs[1])
    hf = n.new('ShaderNodeMath'); hf.operation = 'MULTIPLY'; l.new(inv.outputs[0], hf.inputs[0]); l.new(mode.outputs[0], hf.inputs[1])
    fin = n.new('ShaderNodeMixShader'); l.new(hf.outputs[0], fin.inputs['Fac']); l.new(mix.outputs[0], fin.inputs[1]); l.new(hold.outputs[0], fin.inputs[2])
    l.new(fin.outputs[0], out.inputs[0])
    MAT['body'] = m; return m

def build(res=(512, 1024), samples=24, muscle=1.0):
    global S
    bpy.ops.wm.read_factory_settings(use_empty=True)
    S = bpy.context.scene
    S.render.engine = 'CYCLES'; S.cycles.device = 'CPU'; S.cycles.samples = samples; S.cycles.use_denoising = True
    S.render.film_transparent = True; S.render.resolution_x, S.render.resolution_y = res
    S.render.image_settings.file_format = 'PNG'; S.render.image_settings.color_mode = 'RGBA'
    S.view_settings.view_transform = 'AgX'; S.view_settings.look = 'AgX - Medium High Contrast'
    w = bpy.data.worlds.new('W'); S.world = w; w.use_nodes = True
    w.node_tree.nodes['Background'].inputs['Color'].default_value = (0.8, 0.8, 0.82, 1); w.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.08
    body_material()
    o, verts, used, remap = build_mesh(muscle)
    arm = build_armature(o, verts, used, remap)
    classify(o, arm)
    paint([])
    lights_camera()
    return o, arm

def render(path):
    S.render.filepath = path; bpy.ops.render.render(write_still=True)
