"""Photoreal tiles for the Train screen: muscle-group tiles and activity tiles.

Builds the MakeHuman CC0 body (muscular male target), gives it a realistic skin shader with studio
lighting, and renders each tile with the camera framed on the region of interest:

  * muscle tiles   - the target group is shaded as red muscle fibre, everything else is skin.
                     The camera frames the group's own vertex bounding box, so every tile is a tight,
                     anatomically correct crop of the same body.
  * activity tiles - the body is posed (run, row, box, ...) and framed as a full figure standing on a
                     studio floor, so it casts a real contact shadow.

Outputs PNG (lossless) into OUT; a later step converts them to WebP.
env: OUT, RES, SAMPLES, ONLY (comma keys), SHARD, SHARDS
"""
import sys, os, math, time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
from mathutils import Vector
import mh as M
import pose as P

OUT = os.environ.get('OUT', 'tiles') + '/'
RES = int(os.environ.get('RES', '512'))
SAMPLES = int(os.environ.get('SAMPLES', '48'))


# ------------------------------------------------------------------ material

def make_tile_material():
    """Realistic skin + red muscle fibre, driven by the m_<group> attributes that mh.paint() writes.

    Node names (attr0-3, sec0-2, mode) match mh.paint() so the existing paint() API drives this shader.
    """
    m = bpy.data.materials.new('body')
    m.use_nodes = True
    nt = m.node_tree
    n, l = nt.nodes, nt.links
    out = n['Material Output']
    n.remove(n['Principled BSDF'])

    tc = n.new('ShaderNodeTexCoord')

    # ---- skin ------------------------------------------------------------
    skin = n.new('ShaderNodeBsdfPrincipled')
    skin.inputs['Base Color'].default_value = M.srgb('#C08A6B')
    skin.inputs['Roughness'].default_value = 0.42
    skin.inputs['Subsurface Weight'].default_value = 0.28
    skin.inputs['Subsurface Radius'].default_value = (0.42, 0.16, 0.10)
    skin.inputs['Subsurface Scale'].default_value = 0.014
    skin.inputs['Specular IOR Level'].default_value = 0.45

    # large-scale tonal variation so the skin is never a flat colour
    tone = n.new('ShaderNodeTexNoise')
    tone.inputs['Scale'].default_value = 5.5
    tone.inputs['Detail'].default_value = 3.0
    tone.inputs['Roughness'].default_value = 0.55
    l.new(tc.outputs['Object'], tone.inputs['Vector'])
    tramp = n.new('ShaderNodeValToRGB')
    tramp.color_ramp.elements[0].color = M.srgb('#B07F62')
    tramp.color_ramp.elements[1].color = M.srgb('#C99475')
    l.new(tone.outputs['Fac'], tramp.inputs['Fac'])
    l.new(tramp.outputs['Color'], skin.inputs['Base Color'])

    # pores / micro-relief
    nz = n.new('ShaderNodeTexNoise')
    nz.inputs['Scale'].default_value = 320.0
    nz.inputs['Detail'].default_value = 4.0
    nz.inputs['Roughness'].default_value = 0.65
    l.new(tc.outputs['Object'], nz.inputs['Vector'])
    bump = n.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 0.12
    bump.inputs['Distance'].default_value = 0.0009
    l.new(nz.outputs['Fac'], bump.inputs['Height'])
    l.new(bump.outputs['Normal'], skin.inputs['Normal'])

    # roughness breakup - skin is never uniformly glossy
    nz2 = n.new('ShaderNodeTexNoise')
    nz2.inputs['Scale'].default_value = 11.0
    nz2.inputs['Detail'].default_value = 2.0
    l.new(tc.outputs['Object'], nz2.inputs['Vector'])
    rmap = n.new('ShaderNodeMapRange')
    rmap.inputs['To Min'].default_value = 0.34
    rmap.inputs['To Max'].default_value = 0.54
    l.new(nz2.outputs['Fac'], rmap.inputs['Value'])
    l.new(rmap.outputs['Result'], skin.inputs['Roughness'])

    # ---- red muscle (primary) -------------------------------------------
    red = n.new('ShaderNodeBsdfPrincipled')
    red.inputs['Roughness'].default_value = 0.34
    red.inputs['Subsurface Weight'].default_value = 0.20
    red.inputs['Subsurface Radius'].default_value = (0.32, 0.11, 0.08)
    red.inputs['Subsurface Scale'].default_value = 0.010
    red.inputs['Coat Weight'].default_value = 0.18
    red.inputs['Coat Roughness'].default_value = 0.22
    wv = n.new('ShaderNodeTexWave')
    wv.wave_type = 'BANDS'
    wv.bands_direction = 'Z'
    wv.inputs['Scale'].default_value = 62.0
    wv.inputs['Distortion'].default_value = 6.0
    wv.inputs['Detail'].default_value = 3.0
    l.new(tc.outputs['Object'], wv.inputs['Vector'])
    rr = n.new('ShaderNodeValToRGB')
    e = rr.color_ramp.elements
    c = M.srgb('#A62A1E')
    e[0].color = (c[0] * 0.62, c[1] * 0.62, c[2] * 0.62, 1)
    e[1].color = c
    l.new(wv.outputs['Fac'], rr.inputs['Fac'])
    l.new(rr.outputs['Color'], red.inputs['Base Color'])
    bm = n.new('ShaderNodeBump')
    bm.inputs['Strength'].default_value = 0.34
    bm.inputs['Distance'].default_value = 0.0009
    l.new(wv.outputs['Fac'], bm.inputs['Height'])
    l.new(bm.outputs['Normal'], red.inputs['Normal'])

    # ---- light red (secondary) ------------------------------------------
    pink = n.new('ShaderNodeBsdfPrincipled')
    pink.inputs['Base Color'].default_value = M.srgb('#DE8B78')
    pink.inputs['Roughness'].default_value = 0.40
    pink.inputs['Subsurface Weight'].default_value = 0.18
    pink.inputs['Subsurface Radius'].default_value = (0.32, 0.13, 0.10)

    # ---- attribute plumbing (names must match mh.paint) ------------------
    def attrs(prefix, count):
        mx = None
        for k in range(count):
            at = n.new('ShaderNodeAttribute')
            at.name = prefix + str(k)
            at.attribute_name = 'none'
            if mx is None:
                mx = at.outputs['Fac']
            else:
                mm = n.new('ShaderNodeMath')
                mm.operation = 'MAXIMUM'
                l.new(mx, mm.inputs[0])
                l.new(at.outputs['Fac'], mm.inputs[1])
                mx = mm.outputs[0]
        sharp = n.new('ShaderNodeMapRange')
        sharp.inputs['From Min'].default_value = 0.30
        sharp.inputs['From Max'].default_value = 0.62
        l.new(mx, sharp.inputs['Value'])
        return sharp.outputs['Result']

    primary = attrs('attr', 4)
    secondary = attrs('sec', 3)

    mix1 = n.new('ShaderNodeMixShader')
    l.new(secondary, mix1.inputs[0])
    l.new(skin.outputs[0], mix1.inputs[1])
    l.new(pink.outputs[0], mix1.inputs[2])
    mix2 = n.new('ShaderNodeMixShader')
    l.new(primary, mix2.inputs[0])
    l.new(mix1.outputs[0], mix2.inputs[1])
    l.new(red.outputs[0], mix2.inputs[2])

    hold = n.new('ShaderNodeHoldout')
    mode = n.new('ShaderNodeValue')
    mode.name = 'mode'
    mode.outputs[0].default_value = 0.0
    inv = n.new('ShaderNodeMath')
    inv.operation = 'SUBTRACT'
    inv.inputs[0].default_value = 1.0
    l.new(primary, inv.inputs[1])
    hf = n.new('ShaderNodeMath')
    hf.operation = 'MULTIPLY'
    l.new(inv.outputs[0], hf.inputs[0])
    l.new(mode.outputs[0], hf.inputs[1])
    fin = n.new('ShaderNodeMixShader')
    l.new(hf.outputs[0], fin.inputs[0])
    l.new(mix2.outputs[0], fin.inputs[1])
    l.new(hold.outputs[0], fin.inputs[2])
    l.new(fin.outputs[0], out.inputs[0])
    return m


# ------------------------------------------------------------------ scene

BACKDROP = None
FLOOR = None
RAMP = None          # backdrop gradient MapRange - remapped per tile so the gradient always fits the frame
CAM = None


def setup_scene():
    """Opaque soft-gradient backdrop, studio floor, perspective camera, four-point studio light."""
    global BACKDROP, FLOOR, RAMP, CAM
    S = bpy.context.scene
    S.render.film_transparent = False
    # mh.build() adds its own orthographic camera and four area lights - remove them so the
    # scene is lit only by the studio rig below.
    for ob in list(S.collection.objects):
        if ob.type in ('LIGHT', 'CAMERA'):
            S.collection.objects.unlink(ob)
            for c in list(ob.users_collection):
                c.objects.unlink(ob)
    S.world.node_tree.nodes['Background'].inputs['Color'].default_value = (0.86, 0.87, 0.90, 1)
    S.world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.42

    def area(name, loc, energy, size):
        ld = bpy.data.lights.new(name, 'AREA')
        ld.energy = energy
        ld.size = size
        lo = bpy.data.objects.new(name, ld)
        S.collection.objects.link(lo)
        lo.location = loc
        lo.rotation_euler = (Vector((0, 0, 1.0)) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()

    area('Key', (-1.9, -3.0, 2.6), 620, 1.8)
    area('Fill', (2.6, -2.4, 0.9), 170, 3.2)
    area('Rim', (1.4, 3.2, 2.6), 430, 1.4)
    area('Top', (0.0, 0.0, 3.6), 260, 2.5)

    cd = bpy.data.cameras.new('Cam')
    cd.type = 'PERSP'
    cd.lens = 85.0
    cd.sensor_width = 36.0
    cam = bpy.data.objects.new('Cam', cd)
    S.collection.objects.link(cam)
    S.camera = cam
    M.OBJ['Cam'] = cam
    CAM = cam

    # ---- backdrop (a billboard that always faces the camera) -------------
    bpy.ops.mesh.primitive_plane_add(size=40.0)
    BACKDROP = bpy.context.active_object
    BACKDROP.name = 'Backdrop'
    bm = bpy.data.materials.new('backdrop')
    bm.use_nodes = True
    nt = bm.node_tree
    bn, bl = nt.nodes, nt.links
    bo = bn['Material Output']
    bn.remove(bn['Principled BSDF'])
    diff = bn.new('ShaderNodeBsdfDiffuse')
    diff.inputs['Roughness'].default_value = 1.0
    geo = bn.new('ShaderNodeNewGeometry')
    sep = bn.new('ShaderNodeSeparateXYZ')
    bl.new(geo.outputs['Position'], sep.inputs['Vector'])
    mr = bn.new('ShaderNodeMapRange')
    mr.inputs['From Min'].default_value = -0.6
    mr.inputs['From Max'].default_value = 1.6
    bl.new(sep.outputs['Z'], mr.inputs['Value'])
    RAMP = mr
    ramp = bn.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].color = M.srgb('#C9CDD4')
    ramp.color_ramp.elements[1].color = M.srgb('#F8F8FA')
    bl.new(mr.outputs['Result'], ramp.inputs['Fac'])
    bl.new(ramp.outputs['Color'], diff.inputs['Color'])
    bl.new(diff.outputs[0], bo.inputs[0])
    BACKDROP.data.materials.append(bm)

    # ---- studio floor (so full-body tiles get a real contact shadow) ----
    bpy.ops.mesh.primitive_plane_add(size=60.0)
    FLOOR = bpy.context.active_object
    FLOOR.name = 'Floor'
    fm = bpy.data.materials.new('floor')
    fm.use_nodes = True
    fnt = fm.node_tree
    fn, fl = fnt.nodes, fnt.links
    fbsdf = fn['Principled BSDF']
    fbsdf.inputs['Base Color'].default_value = M.srgb('#BFC3CA')
    fbsdf.inputs['Roughness'].default_value = 0.85
    fgeo = fn.new('ShaderNodeNewGeometry')
    fsep = fn.new('ShaderNodeSeparateXYZ')
    fl.new(fgeo.outputs['Position'], fsep.inputs['Vector'])
    fmr = fn.new('ShaderNodeMapRange')
    fmr.inputs['From Min'].default_value = 0.0
    fmr.inputs['From Max'].default_value = 6.0
    fl.new(fsep.outputs['Z'], fmr.inputs['Value'])
    framp = fn.new('ShaderNodeValToRGB')
    framp.color_ramp.elements[0].color = M.srgb('#9EA3AB')
    framp.color_ramp.elements[1].color = M.srgb('#C6CAD1')
    fl.new(fmr.outputs['Result'], framp.inputs['Fac'])
    fl.new(framp.outputs['Color'], fbsdf.inputs['Base Color'])
    FLOOR.data.materials.append(fm)


# ------------------------------------------------------------------ camera maths

def view_dir(az, el):
    """az = 0 looks at the front of the body (the body faces -Y); el = elevation in degrees."""
    a, e = math.radians(az), math.radians(el)
    return Vector((math.sin(a) * math.cos(e), -math.cos(a) * math.cos(e), math.sin(e))).normalized()


def frame(center, radius, az, el, pad=1.6, lens=85.0):
    """Place the camera so a sphere of `radius` around `center` fits the frame."""
    cam = CAM
    cam.data.lens = lens
    fov = 2 * math.atan((cam.data.sensor_width * 0.5) / lens)
    dist = (radius * pad) / math.tan(fov * 0.5)
    d = view_dir(az, el)
    cam.location = center + d * dist
    cam.rotation_euler = (center - cam.location).to_track_quat('-Z', 'Y').to_euler()
    if BACKDROP:
        # the plane's normal is its local +Z, so it must point back along the view direction
        bd = dist * 0.35 + 2.0
        BACKDROP.location = center - d * bd
        BACKDROP.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
        # remap the gradient so it always spans the visible height at the backdrop distance
        half = bd * math.tan(fov * 0.5)
        RAMP.inputs['From Min'].default_value = center.z - half
        RAMP.inputs['From Max'].default_value = center.z + half
    return dist


def evaluated_mesh():
    dg = bpy.context.evaluated_depsgraph_get()
    ob = M.OBJ['Body'].evaluated_get(dg)
    return ob, ob.to_mesh()


def group_bbox(groups, thresh=0.15):
    """Bounding box of the vertices belonging to the given muscle groups, in world space.

    Read from the base mesh (the m_<group> float attributes always live there) and evaluated in
    rest pose, which is the pose every muscle tile is rendered in.
    """
    ob = M.OBJ['Body']
    me = ob.data
    vs = []
    for g in groups:
        a = me.attributes.get('m_' + g.replace(' ', '_'))
        if not a:
            continue
        for i, v in enumerate(me.vertices):
            if a.data[i].value > thresh:
                vs.append(ob.matrix_world @ v.co)
    if not vs:
        return None
    lo = Vector((min(v.x for v in vs), min(v.y for v in vs), min(v.z for v in vs)))
    hi = Vector((max(v.x for v in vs), max(v.y for v in vs), max(v.z for v in vs)))
    return (lo + hi) / 2, (hi - lo).length / 2


def body_bbox():
    ob, me = evaluated_mesh()
    try:
        vs = [ob.matrix_world @ v.co for v in me.vertices]
        lo = Vector((min(v.x for v in vs), min(v.y for v in vs), min(v.z for v in vs)))
        hi = Vector((max(v.x for v in vs), max(v.y for v in vs), max(v.z for v in vs)))
        return (lo + hi) / 2, (hi - lo).length / 2
    finally:
        ob.to_mesh_clear()


def lowest_point():
    ob, me = evaluated_mesh()
    try:
        return min((ob.matrix_world @ v.co).z for v in me.vertices)
    finally:
        ob.to_mesh_clear()


# ------------------------------------------------------------------ the tile sets

# key -> (muscle groups to highlight, azimuth, elevation, padding)
MUSCLE_TILES = {
    'chest':        (['chest'], -18, 4, 2.5),
    'back':         (['lats', 'middle back', 'traps'], 158, 6, 2.0),
    'shoulders':    (['shoulders'], -30, 16, 2.2),
    'biceps':       (['biceps'], -38, 3, 2.6),
    'triceps':      (['triceps'], 142, 3, 2.6),
    'core':         (['abdominals'], -8, 0, 2.4),
    'glutes':       (['glutes'], 176, -6, 2.4),
    'legs':         (['quadriceps', 'calves'], -14, -2, 2.4),
    'quads':        (['quadriceps'], -14, -2, 2.4),
    'forearms':     (['forearms'], -34, 0, 2.4),
    'hamstrings':   (['hamstrings'], 166, -2, 2.4),
    'lower_back':   (['lower back'], 180, 2, 2.4),
    'obliques':     (['abdominals', 'adductors'], -46, 2, 2.6),
    'traps':        (['traps'], 150, 26, 2.4),
    'calves':       (['calves'], 160, -6, 2.4),
}

# key -> (pose, azimuth, elevation)
ACTIVITY_TILES = {
    'cardio': (
        dict(torso=14, neck=-6, armL=(26, -28), armR=(26, -28), foreL=(16, 62), foreR=(16, 62),
             legL=(4, 18), legR=(4, -26), shinL=(4, -22), shinR=(4, 6),
             pitch=9, ground=False, lift=0.04),
        -34, 4),
    'running': (
        dict(torso=14, neck=-6, armL=(26, -28), armR=(26, -28), foreL=(16, 62), foreR=(16, 62),
             legL=(4, 18), legR=(4, -26), shinL=(4, -22), shinR=(4, 6),
             pitch=9, ground=False, lift=0.04),
        -34, 4),
    'other': (
        dict(torso=10, neck=-6, armL=(62, 34), armR=(62, 34), foreL=(58, 44), foreR=(58, 44),
             legL=(8, 14), legR=(8, -10), shinL=(8, 8), shinR=(8, 16),
             ground=False, lift=0.02),
        -38, 4),
    'cycling': (
        dict(torso=26, neck=-14, armL=(30, 22), armR=(30, 22), foreL=(24, 78), foreR=(24, 78),
             legL=(6, 44), legR=(6, 12), shinL=(6, 66), shinR=(6, 30),
             ground=False, lift=0.30),
        -38, 6),
    'swimming': (
        dict(torso=74, neck=-18, armL=(18, 96), armR=(18, 96), foreL=(14, 150), foreR=(14, 150),
             legL=(4, -10), legR=(4, -18), shinL=(4, 8), shinR=(4, 8),
             pitch=-24, ground=False, lift=0.16),
        -46, 2),
    'rowing': (
        dict(torso=34, neck=-8, armL=(16, 74), armR=(16, 74), foreL=(12, 108), foreR=(12, 108),
             legL=(6, 76), legR=(6, 76), shinL=(6, 84), shinR=(6, 84),
             ground=False, lift=0.10),
        -40, 4),
    'hiit': (
        dict(torso=16, neck=-8, armL=(78, 18), armR=(78, 18), foreL=(70, 40), foreR=(70, 40),
             legL=(12, 16), legR=(12, 16), shinL=(12, 34), shinR=(12, 34),
             pitch=8, ground=False, lift=0.18),
        -36, 8),
    'jump_rope': (
        dict(torso=10, neck=-4, armL=(48, 26), armR=(48, 26), foreL=(44, 76), foreR=(44, 76),
             legL=(5, 8), legR=(5, 8), shinL=(5, 24), shinR=(5, 24),
             tiptoe=1, ground=False, lift=0.30),
        -40, 2),
    'stair_climbing': (
        dict(torso=20, neck=-8, armL=(22, -14), armR=(22, -14), foreL=(18, 54), foreR=(18, 54),
             legL=(5, 34), legR=(5, -8), shinL=(5, 52), shinR=(5, 8),
             pitch=10, ground=False, lift=0.06),
        -36, 2),
    'walking': (
        dict(torso=8, neck=-4, armL=(12, -16), armR=(12, -16), foreL=(10, 34), foreR=(10, 34),
             legL=(4, 14), legR=(4, -12), shinL=(4, 4), shinR=(4, 12),
             ground=False, lift=0.02),
        -32, 2),
    'boxing': (
        dict(torso=14, neck=-10, armL=(34, 62), armR=(34, 62), foreL=(30, 120), foreR=(30, 120),
             legL=(12, 24), legR=(12, 2), shinL=(12, 30), shinR=(12, 16),
             pitch=6, ground=False, lift=0.04),
        -42, 4),
    'yoga_stretching': (
        dict(torso=12, neck=-6, armL=(96, 20), armR=(96, 20), foreL=(90, 30), foreR=(90, 30),
             legL=(6, 10), legR=(6, 10), shinL=(6, 6), shinR=(6, 6),
             ground=True),
        -34, 6),
}


# ------------------------------------------------------------------ main

def main():
    t0 = time.time()
    body, arm = M.build((RES, RES), SAMPLES)
    mat = make_tile_material()
    M.MAT['body'] = mat
    body.data.materials.clear()
    body.data.materials.append(mat)
    setup_scene()
    P.init()
    P.props_init()

    os.makedirs(OUT, exist_ok=True)
    only = [k for k in os.environ.get('ONLY', '').split(',') if k]
    sh, nsh = int(os.environ.get('SHARD', '0')), int(os.environ.get('SHARDS', '1'))

    jobs = []
    for k, (groups, az, el, pad) in MUSCLE_TILES.items():
        if only and k not in only:
            continue
        jobs.append(('muscle', k, groups, az, el, pad))
    for k, (pose, az, el) in ACTIVITY_TILES.items():
        if only and k not in only:
            continue
        jobs.append(('activity', k, None, az, el, 1.28))

    jobs = jobs[sh::nsh]
    for kind, key, groups, az, el, pad in jobs:
        if kind == 'muscle':
            M.paint(groups)
            b = group_bbox(groups)
            if b is None:
                print('SKIP', key, flush=True)
                continue
            center, radius = b
            FLOOR.hide_render = True          # close-ups: no floor in frame
            frame(center, radius, az, el, pad)
        else:
            M.paint([])
            P.apply(dict(ACTIVITY_TILES[key][0]))
            bpy.context.view_layer.update()
            center, radius = body_bbox()
            FLOOR.hide_render = False
            FLOOR.location = (0.0, 0.0, lowest_point() - 0.004)
            frame(center, radius, az, el, pad)
        M.render(OUT + key + '.png')
        print('P', key, round(time.time() - t0), flush=True)
    print('ALLDONE', flush=True)


if __name__ == '__main__':
    main()
