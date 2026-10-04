"""Anatomical mannequin for MyFit's exercise visuals.
A smooth metaball body (segments parented to joint empties, so it can be posed) plus every muscle group as separate
sculpted ellipsoids with their own material. Used for (1) front/back muscle-map overlays and (2) 3D exercise loops."""
import bpy, math
from mathutils import Vector, Matrix, Euler

S = None
OBJ = {}
MAT = {}
GROUPS = ['abdominals', 'abductors', 'adductors', 'biceps', 'calves', 'chest', 'forearms', 'glutes', 'hamstrings',
          'lats', 'lower back', 'middle back', 'neck', 'quadriceps', 'shoulders', 'traps', 'triceps']
MUSCLES = {g: [] for g in GROUPS}

def srgb(h):
    h = h.lstrip('#'); c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(((x + 0.055) / 1.055) ** 2.4 if x > 0.04045 else x / 12.92 for x in c) + (1.0,)

def mat(name, color, rough=0.55, sss=0.03, coat=0.0, fibre=False):
    m = bpy.data.materials.new(name); m.use_nodes = True
    n = m.node_tree.nodes; l = m.node_tree.links; b = n['Principled BSDF']
    b.inputs['Base Color'].default_value = srgb(color); b.inputs['Roughness'].default_value = rough
    b.inputs['Subsurface Weight'].default_value = sss; b.inputs['Subsurface Radius'].default_value = (0.2, 0.2, 0.2)
    b.inputs['Subsurface Scale'].default_value = 0.02; b.inputs['Coat Weight'].default_value = coat
    if fibre:   # fine striations along the muscle's long axis: colour variation + bump
        tc = n.new('ShaderNodeTexCoord'); w = n.new('ShaderNodeTexWave'); w.wave_type = 'BANDS'; w.bands_direction = 'X'
        w.inputs['Scale'].default_value = 9.0; w.inputs['Distortion'].default_value = 3.0; w.inputs['Detail'].default_value = 4.0
        mp = n.new('ShaderNodeMapping'); mp.inputs['Rotation'].default_value = (0, math.radians(90), 0)
        l.new(tc.outputs['Object'], mp.inputs['Vector']); l.new(mp.outputs['Vector'], w.inputs['Vector'])
        ramp = n.new('ShaderNodeValToRGB'); e = ramp.color_ramp.elements
        c = srgb(color); e[0].color = (c[0] * 0.72, c[1] * 0.72, c[2] * 0.72, 1); e[1].color = c
        l.new(w.outputs['Fac'], ramp.inputs['Fac']); l.new(ramp.outputs['Color'], b.inputs['Base Color'])
        bm = n.new('ShaderNodeBump'); bm.inputs['Strength'].default_value = 0.35; bm.inputs['Distance'].default_value = 0.002
        l.new(w.outputs['Fac'], bm.inputs['Height']); l.new(bm.outputs['Normal'], b.inputs['Normal'])
    MAT[name] = m; return m

def link(o):
    S.collection.objects.link(o); return o

def empty(name, loc, parent=None):
    o = bpy.data.objects.new(name, None); link(o); o.location = loc
    if parent: o.parent = parent; o.location = Vector(loc) - parent.matrix_world.translation
    OBJ[name] = o; return o

# ---------------------------------------------------------------- skeleton (rest pose, Z up, facing -Y)
J = {
    'pelvis': (0, 0, 0.98), 'waist': (0, 0, 1.12), 'chest': (0, 0, 1.32), 'neck': (0, 0, 1.50), 'head': (0, 0.0, 1.655),
    'hipL': (0.095, 0, 0.94), 'kneeL': (0.105, -0.005, 0.52), 'ankleL': (0.11, 0.02, 0.085), 'toeL': (0.115, -0.11, 0.02),
    'shL': (0.195, 0.0, 1.445), 'elL': (0.265, 0.01, 1.17), 'wrL': (0.30, -0.01, 0.93), 'handL': (0.31, -0.02, 0.85),
}
for k in list(J):
    if k.endswith('L'): x, y, z = J[k]; J[k[:-1] + 'R'] = (-x, y, z)
CHAIN = {  # joint: parent
    'waist': 'pelvis', 'chest': 'waist', 'neck': 'chest', 'head': 'neck',
    'hipL': 'pelvis', 'kneeL': 'hipL', 'ankleL': 'kneeL', 'toeL': 'ankleL',
    'hipR': 'pelvis', 'kneeR': 'hipR', 'ankleR': 'kneeR', 'toeR': 'ankleR',
    'shL': 'chest', 'elL': 'shL', 'wrL': 'elL', 'handL': 'wrL',
    'shR': 'chest', 'elR': 'shR', 'wrR': 'elR', 'handR': 'wrR',
}
ORDER = ['pelvis', 'waist', 'chest', 'neck', 'head', 'hipL', 'kneeL', 'ankleL', 'toeL', 'hipR', 'kneeR', 'ankleR', 'toeR',
         'shL', 'elL', 'wrL', 'handL', 'shR', 'elR', 'wrR', 'handR']

def build_skeleton():
    root = empty('Root', (0, 0, 0))
    for j in ORDER:
        p = OBJ[CHAIN[j]] if j in CHAIN else root
        o = bpy.data.objects.new('J_' + j, None); link(o); o.parent = p
        o.location = Vector(J[j]) - (Vector(J[CHAIN[j]]) if j in CHAIN else Vector((0, 0, 0)))
        OBJ[j] = o
    return root

# ---------------------------------------------------------------- body (metaballs per segment so it poses smoothly)
K = 1.0 / 0.574
def seg_ball(mb, j, a, b, r, kind='CAPSULE', size=None):
    """Capsule from joint a to b, stored in joint a's local frame."""
    pa, pb = Vector(J[a]), Vector(J[b])
    e = mb.elements.new(type=kind)
    e.co = (pa + pb) / 2 - pa if kind == 'CAPSULE' else Vector((0, 0, 0))
    e.radius = r * K; e.stiffness = 2.0
    if kind == 'CAPSULE':
        d = pb - pa; e.size_x = d.length / 2
        e.rotation = Vector((1, 0, 0)).rotation_difference(d)
    elif kind == 'ELLIPSOID':
        e.size_x, e.size_y, e.size_z = size
    return e

def mball(name, joint, m):
    mb = bpy.data.metaballs.new(name); mb.resolution = 0.02; mb.render_resolution = 0.008; mb.threshold = 0.6
    o = bpy.data.objects.new(name, mb); link(o); o.parent = OBJ[joint]; o.data.materials.append(m); OBJ[name] = o
    return mb

def build_body():
    skin = MAT['skin']
    # torso family: chest/abdomen/pelvis blend into one smooth trunk (all parented to waist so it bends as one)
    t = mball('Torso', 'waist', skin)
    for co, sz in (((0, 0.005, 0.22), (0.185, 0.105, 0.14)), ((0, 0.0, 0.02), (0.128, 0.09, 0.13)), ((0, 0.005, -0.15), (0.148, 0.10, 0.10))):
        e = t.elements.new(type='ELLIPSOID'); e.co = co; e.radius = 1.0 * K; e.stiffness = 2.0
        e.size_x, e.size_y, e.size_z = sz
    nk = mball('Neck', 'neck', skin)
    e = nk.elements.new(type='CAPSULE'); e.co = (0, 0.005, 0.05); e.radius = 0.048 * K; e.size_x = 0.05
    e.rotation = Vector((1, 0, 0)).rotation_difference(Vector((0, 0, 1)))
    hd = mball('Head', 'head', skin)
    e = hd.elements.new(type='ELLIPSOID'); e.co = (0, 0, 0); e.radius = 1.0 * K; e.size_x, e.size_y, e.size_z = (0.078, 0.092, 0.105)
    e = hd.elements.new(type='ELLIPSOID'); e.co = (0, -0.035, -0.055); e.radius = 1.0 * K; e.size_x, e.size_y, e.size_z = (0.055, 0.06, 0.05)
    for s in 'LR':
        seg = [('hip', 'knee', 0.068), ('knee', 'ankle', 0.047), ('sh', 'el', 0.043), ('el', 'wr', 0.036)]
        for a, b, r in seg:
            mb = mball('B_' + a + s, a + s, skin); seg_ball(mb, a + s, a + s, b + s, r)
        ft = mball('B_foot' + s, 'ankle' + s, skin)
        e = ft.elements.new(type='ELLIPSOID'); e.co = (0, -0.06, -0.045); e.radius = 1.0 * K; e.size_x, e.size_y, e.size_z = (0.045, 0.11, 0.035)
        hn = mball('B_hand' + s, 'wr' + s, skin)
        e = hn.elements.new(type='ELLIPSOID'); e.co = (0.005 * (1 if s == 'L' else -1), -0.01, -0.07); e.radius = 1.0 * K; e.size_x, e.size_y, e.size_z = (0.022, 0.045, 0.065)

# ---------------------------------------------------------------- muscles
def muscle(group, joint, center, radii, rot=(0, 0, 0), mirror=True):
    """Ellipsoid muscle; center in world rest coords; parented to [joint] (side suffix added for mirrored)."""
    sides = ('L', 'R') if mirror else ('',)
    for s in sides:
        sx = -1 if s == 'R' else 1
        c = Vector((center[0] * sx, center[1] * 0.93, center[2]))   # sit a little deeper so muscles read as definition
        jn = joint[:-1] + 'R' if (s == 'R' and joint.endswith('L')) else joint
        bpy.ops.mesh.primitive_uv_sphere_add(segments=40, ring_count=20, location=(0, 0, 0))
        o = bpy.context.active_object; o.name = 'M_%s_%d' % (group, len(MUSCLES[group]))
        bpy.ops.object.shade_smooth()
        o.data.materials.append(MAT['muscle'])
        r = Vector(radii); o.scale = (r.x, r.y * 0.8, r.z)
        o.rotation_euler = Euler((math.radians(rot[0]), math.radians(rot[1] * sx), math.radians(rot[2] * sx)), 'XYZ')
        o.location = c
        mw = o.matrix_world.copy(); bpy.context.view_layer.update()
        p = OBJ[jn]; o.parent = p; o.matrix_parent_inverse = p.matrix_world.inverted()
        MUSCLES[group].append(o)

def build_muscles():
    # ---- front
    muscle('chest', 'chest', (0.075, -0.098, 1.365), (0.082, 0.032, 0.062), (0, 8, -12))
    muscle('shoulders', 'shL', (0.205, -0.005, 1.425), (0.055, 0.058, 0.075), (0, 12, 0))
    muscle('biceps', 'shL', (0.238, -0.035, 1.29), (0.034, 0.036, 0.085), (0, 14, 0))
    muscle('triceps', 'shL', (0.245, 0.032, 1.30), (0.037, 0.034, 0.098), (0, 14, 0))
    muscle('forearms', 'elL', (0.285, -0.012, 1.06), (0.033, 0.032, 0.095), (0, 8, 0))
    for x, z in ((0.03, 1.24), (0.03, 1.175), (0.03, 1.11), (0.028, 1.045)):
        muscle('abdominals', 'waist', (x, -0.098, z), (0.028, 0.02, 0.03))
    muscle('abdominals', 'waist', (0.105, -0.07, 1.13), (0.034, 0.03, 0.085), (0, -6, 0))      # obliques
    muscle('quadriceps', 'hipL', (0.105, -0.068, 0.75), (0.045, 0.04, 0.14), (0, 3, 0))
    muscle('quadriceps', 'hipL', (0.148, -0.035, 0.71), (0.038, 0.042, 0.14), (0, 4, 0))
    muscle('quadriceps', 'hipL', (0.07, -0.05, 0.61), (0.036, 0.034, 0.07), (0, -6, 0))
    muscle('adductors', 'hipL', (0.058, -0.015, 0.80), (0.032, 0.038, 0.11), (0, -10, 0))
    muscle('abductors', 'pelvis', (0.165, 0.0, 0.915), (0.04, 0.052, 0.07), (0, 10, 0))
    muscle('neck', 'neck', (0.032, -0.035, 1.54), (0.018, 0.02, 0.06), (18, 0, 12))
    muscle('traps', 'chest', (0.095, 0.015, 1.485), (0.07, 0.032, 0.035), (0, -14, 0))
    # ---- back
    muscle('traps', 'chest', (0.0, 0.085, 1.38), (0.075, 0.03, 0.10), mirror=False)
    muscle('middle back', 'chest', (0.065, 0.088, 1.33), (0.045, 0.022, 0.06), (0, 10, 0))
    muscle('lats', 'chest', (0.115, 0.07, 1.24), (0.068, 0.032, 0.12), (0, -14, 0))
    muscle('lower back', 'waist', (0.033, 0.088, 1.11), (0.026, 0.022, 0.09))
    muscle('glutes', 'pelvis', (0.075, 0.075, 0.925), (0.072, 0.052, 0.075))
    muscle('hamstrings', 'hipL', (0.105, 0.05, 0.70), (0.048, 0.038, 0.15))
    muscle('calves', 'kneeL', (0.112, 0.045, 0.36), (0.044, 0.042, 0.10))
    muscle('calves', 'kneeL', (0.112, 0.020, 0.30), (0.035, 0.035, 0.09))

def lights_camera(back=False):
    def area(name, loc, energy, size):
        ld = bpy.data.lights.new(name, 'AREA'); ld.energy = energy; ld.size = size
        lo = bpy.data.objects.new(name, ld); link(lo); lo.location = loc
        lo.rotation_euler = (Vector((0, 0, 1.0)) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
    area('Key', (-2.5, -4.0, 3.5), 520, 3); area('Fill', (3.0, -3.0, 1.5), 160, 5); area('Rim', (0, 4.0, 3.0), 380, 2)
    area('Key2', (2.5, 4.0, 3.5), 300, 3)
    cd = bpy.data.cameras.new('Cam'); cd.type = 'ORTHO'; cd.ortho_scale = 2.05
    cam = bpy.data.objects.new('Cam', cd); link(cam); S.camera = cam; OBJ['Cam'] = cam
    set_view(back)

def set_view(back):
    cam = OBJ['Cam']
    if back: cam.location = (0, 6, 0.93); cam.rotation_euler = (math.radians(90), 0, math.radians(180))
    else: cam.location = (0, -6, 0.93); cam.rotation_euler = (math.radians(90), 0, 0)

def build(res=(512, 1024), samples=24):
    global S
    bpy.ops.wm.read_factory_settings(use_empty=True)
    S = bpy.context.scene
    S.render.engine = 'CYCLES'; S.cycles.device = 'CPU'; S.cycles.samples = samples; S.cycles.use_denoising = True
    S.render.film_transparent = True; S.render.resolution_x, S.render.resolution_y = res
    S.render.image_settings.file_format = 'PNG'; S.render.image_settings.color_mode = 'RGBA'
    S.view_settings.view_transform = 'Khronos PBR Neutral'
    w = bpy.data.worlds.new('W'); S.world = w; w.use_nodes = True
    w.node_tree.nodes['Background'].inputs['Color'].default_value = (0.8, 0.8, 0.82, 1); w.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.25
    mat('skin', '#B9B9BC', rough=0.55); mat('muscle', '#B4B4B8', rough=0.5, fibre=True); mat('red', '#E0442A', rough=0.45, coat=0.15, fibre=True)
    mat('hold', '#000000')
    build_skeleton(); build_body(); build_muscles(); lights_camera()

def render(path):
    S.render.filepath = path; bpy.ops.render.render(write_still=True)

def highlight(groups, how='red'):
    """Colour some muscle groups red (others grey)."""
    for g, objs in MUSCLES.items():
        for o in objs: o.data.materials[0] = MAT['red'] if g in groups else MAT['muscle']

def overlay_mode(group):
    """Only [group] visible (red), everything else held out → transparent overlay image."""
    for o in S.objects:
        if o.type in ('MESH', 'META'): o.is_holdout = True
    for o in MUSCLES[group]: o.is_holdout = False; o.data.materials[0] = MAT['red']

def normal_mode():
    for o in S.objects:
        if o.type in ('MESH', 'META'): o.is_holdout = False
    highlight([])
