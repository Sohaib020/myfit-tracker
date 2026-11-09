"""Photoreal tiles for the Train screen: muscle-group tiles and activity tiles.

Builds the MakeHuman CC0 body (muscular male target), gives it a realistic skin shader under a
low-key "dark gym" studio rig, and renders each tile with the camera framed on the region of
interest:

  * muscle tiles   - the target group is shaded as red fibre *under* the skin, everything else is
                     natural skin. The camera frames the group's own vertices measured in the
                     camera's axes, so every tile is a tight, anatomically correct crop.
  * activity tiles - the body is posed (running, cycling, ...) and framed as a full figure on a
                     dark studio floor, so it casts a real contact shadow.

Also re-renders the anatomy map (front/back base bodies + one red overlay per muscle group) with
the same photoreal skin, so the big muscle diagram matches the tiles.

Outputs PNG (lossless) into OUT; tiles_post.py converts them to WebP.
env: OUT, OUT_ANAT, RES, SAMPLES, ONLY (comma keys), SHARD, SHARDS, DRY=1 (no render)
"""
import sys, os, math, time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
from mathutils import Vector
import mh as M
import pose as P

OUT = os.environ.get('OUT', 'tiles') + '/'
OUT_ANAT = os.environ.get('OUT_ANAT', 'anat') + '/'
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
    skin.inputs['Base Color'].default_value = M.srgb('#A9704F')
    skin.inputs['Roughness'].default_value = 0.40
    skin.inputs['Subsurface Weight'].default_value = 0.32
    skin.inputs['Subsurface Radius'].default_value = (0.42, 0.15, 0.09)
    skin.inputs['Subsurface Scale'].default_value = 0.012
    skin.inputs['Specular IOR Level'].default_value = 0.48
    skin.inputs['Specular Tint'].default_value = (1.0, 0.86, 0.78, 1.0)

    # large-scale tonal variation so the skin is never a flat colour
    tone = n.new('ShaderNodeTexNoise')
    tone.inputs['Scale'].default_value = 6.0
    tone.inputs['Detail'].default_value = 4.0
    tone.inputs['Roughness'].default_value = 0.60
    l.new(tc.outputs['Object'], tone.inputs['Vector'])
    tramp = n.new('ShaderNodeValToRGB')
    tramp.color_ramp.elements[0].position = 0.35
    tramp.color_ramp.elements[0].color = M.srgb('#7A4527')
    tramp.color_ramp.elements[1].position = 0.68
    tramp.color_ramp.elements[1].color = M.srgb('#BC7F5C')
    l.new(tone.outputs['Fac'], tramp.inputs['Fac'])
    l.new(tramp.outputs['Color'], skin.inputs['Base Color'])

    # pores / micro-relief
    nz = n.new('ShaderNodeTexNoise')
    nz.inputs['Scale'].default_value = 340.0
    nz.inputs['Detail'].default_value = 5.0
    nz.inputs['Roughness'].default_value = 0.70
    l.new(tc.outputs['Object'], nz.inputs['Vector'])
    bump = n.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 0.14
    bump.inputs['Distance'].default_value = 0.0008
    l.new(nz.outputs['Fac'], bump.inputs['Height'])
    l.new(bump.outputs['Normal'], skin.inputs['Normal'])

    # roughness breakup - skin is never uniformly glossy
    nz2 = n.new('ShaderNodeTexNoise')
    nz2.inputs['Scale'].default_value = 12.0
    nz2.inputs['Detail'].default_value = 3.0
    l.new(tc.outputs['Object'], nz2.inputs['Vector'])
    rmap = n.new('ShaderNodeMapRange')
    rmap.inputs['To Min'].default_value = 0.30
    rmap.inputs['To Max'].default_value = 0.52
    l.new(nz2.outputs['Fac'], rmap.inputs['Value'])
    l.new(rmap.outputs['Result'], skin.inputs['Roughness'])

    # a touch of sweat sheen on the upper body reads as "after the workout"
    sheen = n.new('ShaderNodeMapRange')
    sheen.inputs['From Min'].default_value = 0.35
    sheen.inputs['From Max'].default_value = 0.75
    l.new(tone.outputs['Fac'], sheen.inputs['Value'])
    coat = n.new('ShaderNodeMath')
    coat.operation = 'MULTIPLY'
    coat.inputs[1].default_value = 0.22
    l.new(sheen.outputs['Result'], coat.inputs[0])
    l.new(coat.outputs[0], skin.inputs['Coat Weight'])
    skin.inputs['Coat Roughness'].default_value = 0.30

    # ---- red muscle (primary) -------------------------------------------
    red = n.new('ShaderNodeBsdfPrincipled')
    red.inputs['Roughness'].default_value = 0.36
    red.inputs['Subsurface Weight'].default_value = 0.30
    red.inputs['Subsurface Radius'].default_value = (0.34, 0.10, 0.07)
    red.inputs['Subsurface Scale'].default_value = 0.010
    red.inputs['Specular IOR Level'].default_value = 0.40

    # fibre banding along the body's vertical axis
    wv = n.new('ShaderNodeTexWave')
    wv.wave_type = 'BANDS'
    wv.bands_direction = 'Z'
    wv.inputs['Scale'].default_value = 68.0
    wv.inputs['Distortion'].default_value = 7.0
    wv.inputs['Detail'].default_value = 4.0
    l.new(tc.outputs['Object'], wv.inputs['Vector'])
    rr = n.new('ShaderNodeValToRGB')
    e = rr.color_ramp.elements
    c = M.srgb('#C4362A')
    e[0].color = (c[0] * 0.58, c[1] * 0.58, c[2] * 0.58, 1)
    e[1].color = c
    l.new(wv.outputs['Fac'], rr.inputs['Fac'])
    l.new(rr.outputs['Color'], red.inputs['Base Color'])
    bm = n.new('ShaderNodeBump')
    bm.inputs['Strength'].default_value = 0.38
    bm.inputs['Distance'].default_value = 0.0009
    l.new(wv.outputs['Fac'], bm.inputs['Height'])
    l.new(bm.outputs['Normal'], red.inputs['Normal'])

    # ---- light red (secondary) ------------------------------------------
    pink = n.new('ShaderNodeBsdfPrincipled')
    pink.inputs['Base Color'].default_value = M.srgb('#D8816E')
    pink.inputs['Roughness'].default_value = 0.40
    pink.inputs['Subsurface Weight'].default_value = 0.24
    pink.inputs['Subsurface Radius'].default_value = (0.34, 0.12, 0.09)
    pink.inputs['Subsurface Scale'].default_value = 0.010

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

    # The primary mask is scaled down so ~30% of the skin's own colour still diffuses over the
    # muscle. That is what makes the fibre read as red tissue *under* skin rather than flat paint
    # on top of it.
    depth = n.new('ShaderNodeMath')
    depth.operation = 'MULTIPLY'
    depth.inputs[1].default_value = 0.72
    l.new(primary, depth.inputs[0])

    mix1 = n.new('ShaderNodeMixShader')
    l.new(secondary, mix1.inputs[0])
    l.new(skin.outputs[0], mix1.inputs[1])
    l.new(pink.outputs[0], mix1.inputs[2])

    # muscle over skin
    mix_m = n.new('ShaderNodeMixShader')
    l.new(depth.outputs[0], mix_m.inputs[0])
    l.new(mix1.outputs[0], mix_m.inputs[1])
    l.new(red.outputs[0], mix_m.inputs[2])

    # a thin skin veil back over the muscle, so skin specular/subsurface still read on top
    veil = n.new('ShaderNodeMath')
    veil.operation = 'MULTIPLY'
    veil.inputs[1].default_value = 0.30
    l.new(primary, veil.inputs[0])
    mix2 = n.new('ShaderNodeMixShader')
    l.new(veil.outputs[0], mix2.inputs[0])
    l.new(mix_m.outputs[0], mix2.inputs[1])
    l.new(skin.outputs[0], mix2.inputs[2])

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


def set_transparent(on):
    """Transparent film (for the muscle-map overlays) vs an opaque studio backdrop."""
    bpy.context.scene.render.film_transparent = on
    if BACKDROP:
        BACKDROP.hide_render = on
    if FLOOR:
        FLOOR.hide_render = on


def setup_scene():
    """Dark low-key backdrop, studio floor, perspective camera, dramatic gym light rig."""
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
    S.world.node_tree.nodes['Background'].inputs['Color'].default_value = (0.05, 0.055, 0.07, 1)
    S.world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.16

    def area(name, loc, energy, size, color=(1.0, 1.0, 1.0)):
        ld = bpy.data.lights.new(name, 'AREA')
        ld.energy = energy
        ld.size = size
        ld.color = color
        lo = bpy.data.objects.new(name, ld)
        S.collection.objects.link(lo)
        lo.location = loc
        lo.rotation_euler = (Vector((0, 0, 1.0)) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()

    # low-key "dark gym": one warm hard key, cool rims from behind, a faint cool fill
    area('Key', (-2.1, -2.8, 2.9), 900, 1.2, (1.0, 0.93, 0.84))
    area('Rim', (1.8, 3.0, 2.8), 1400, 0.9, (0.72, 0.83, 1.0))
    area('Rim2', (-1.4, 3.2, 2.2), 700, 0.8, (0.78, 0.86, 1.0))
    area('Fill', (2.8, -2.2, 0.8), 90, 3.4, (0.80, 0.86, 1.0))

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
    ramp.color_ramp.elements[0].color = M.srgb('#05070A')
    ramp.color_ramp.elements[1].color = M.srgb('#1C2129')
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
    fbsdf.inputs['Base Color'].default_value = M.srgb('#0C0F14')
    fbsdf.inputs['Roughness'].default_value = 0.42
    fbsdf.inputs['Specular IOR Level'].default_value = 0.6
    fgeo = fn.new('ShaderNodeNewGeometry')
    fsep = fn.new('ShaderNodeSeparateXYZ')
    fl.new(fgeo.outputs['Position'], fsep.inputs['Vector'])
    fmr = fn.new('ShaderNodeMapRange')
    fmr.inputs['From Min'].default_value = 0.0
    fmr.inputs['From Max'].default_value = 6.0
    fl.new(fsep.outputs['Z'], fmr.inputs['Value'])
    framp = fn.new('ShaderNodeValToRGB')
    framp.color_ramp.elements[0].color = M.srgb('#05070A')
    framp.color_ramp.elements[1].color = M.srgb('#171C23')
    fl.new(fmr.outputs['Result'], framp.inputs['Fac'])
    fl.new(framp.outputs['Color'], fbsdf.inputs['Base Color'])
    FLOOR.data.materials.append(fm)


# ------------------------------------------------------------------ camera maths

def view_dir(az, el):
    """az = 0 looks at the front of the body (the body faces -Y); el = elevation in degrees."""
    a, e = math.radians(az), math.radians(el)
    return Vector((math.sin(a) * math.cos(e), -math.cos(a) * math.cos(e), math.sin(e))).normalized()


def frame(center, radius, az, el, pad=1.6, lens=85.0):
    """Place the camera so a sphere of `radius` around `center` fits the frame.

    Blender's default sensor fit is AUTO, so the long axis gets the full sensor width. The
    framing maths therefore has to use the long axis, not the horizontal one, or a portrait
    render would be cropped.
    """
    cam = CAM
    cam.data.lens = lens
    S = bpy.context.scene
    long_axis = max(S.render.resolution_x, S.render.resolution_y)
    short_axis = min(S.render.resolution_x, S.render.resolution_y)
    # with AUTO fit the sensor width maps to the long axis
    fov_long = 2 * math.atan((cam.data.sensor_width * 0.5) / lens)
    fov_short = 2 * math.atan((cam.data.sensor_width * 0.5 * (short_axis / long_axis)) / lens)
    # the body is taller than it is wide, so the vertical (short) axis is the binding one
    fov = min(fov_long, fov_short) if S.render.resolution_y >= S.render.resolution_x else fov_short
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
        S = bpy.context.scene
        if S.render.resolution_y >= S.render.resolution_x:
            half = bd * math.tan(fov * 0.5)
        else:
            half = bd * math.tan(fov * 0.5) * (S.render.resolution_x / S.render.resolution_y)
        RAMP.inputs['From Min'].default_value = center.z - half
        RAMP.inputs['From Max'].default_value = center.z + half
    return dist


def evaluated_mesh():
    dg = bpy.context.evaluated_depsgraph_get()
    ob = M.OBJ['Body'].evaluated_get(dg)
    return ob, ob.to_mesh()


def group_verts(groups, thresh=0.15):
    """World-space vertices belonging to the given muscle groups.

    Read from the base mesh (the m_<group> float attributes always live there); muscle tiles are
    rendered in rest pose, so the base mesh positions are the ones that matter.
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
    return vs


def view_axes(az, el):
    """Right and up vectors of a camera at the given azimuth/elevation."""
    d = view_dir(az, el)                      # camera -> subject is -d
    fwd = -d
    right = fwd.cross(Vector((0, 0, 1.0)))
    if right.length < 1e-6:
        right = Vector((1, 0, 0))
    right.normalize()
    up = right.cross(fwd).normalized()
    return right, up


def fit_to_view(vs, az, el, pad=1.0):
    """Centre and radius of a set of points, measured in the camera's own axes.

    Using the view axes (rather than a 3D bounding sphere) keeps the crop tight: the depth of the
    torso does not widen the frame when looking at the chest from the front.
    """
    if not vs:
        return None
    right, up = view_axes(az, el)
    rx = [(v - vs[0]).dot(right) for v in vs]
    uy = [(v - vs[0]).dot(up) for v in vs]
    cx = (min(rx) + max(rx)) / 2
    cy = (min(uy) + max(uy)) / 2
    rad = max(max(rx) - cx, max(uy) - cy) / 2
    center = vs[0] + right * cx + up * cy
    return center, rad * pad


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

# The first eight keys are the muscle-group cards on the Train screen and are named to match the
# MuscleGroup constants (lower-cased) so the app can look a tile up by muscle name.
# key -> (muscle groups to highlight, azimuth, elevation, padding)
# pad = extra breathing room around the tight fit of the group
MUSCLE_TILES = {
    'chest':        (['chest'], -18, 4, 1.45),
    'back':         (['lats', 'middle back', 'traps'], 158, 6, 1.30),
    'shoulders':    (['shoulders'], -30, 16, 1.40),
    'biceps':       (['biceps'], -38, 3, 1.55),
    'triceps':      (['triceps'], 142, 3, 1.55),
    'core':         (['abdominals'], -8, 0, 1.40),
    'glutes':       (['glutes'], 176, -6, 1.40),
    'legs':         (['quadriceps', 'calves'], -14, -2, 1.30),
    # the rest of the muscle map, so every group has a matching photo
    'forearms':     (['forearms'], -34, 0, 1.45),
    'hamstrings':   (['hamstrings'], 166, -2, 1.40),
    'lower_back':   (['lower back'], 180, 2, 1.40),
    'obliques':     (['abdominals', 'adductors'], -46, 2, 1.50),
    'traps':        (['traps'], 150, 26, 1.40),
    'calves':       (['calves'], 160, -6, 1.45),
    'quads':        (['quadriceps'], -14, -2, 1.45),
    'abs_core':     (['abdominals'], -8, 0, 1.40),
}

# key -> (pose, azimuth, elevation)
ACTIVITY_TILES = {
    # Cardio: a running athlete, mid-stride.
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
    # Other: an athletic bodyweight move (arms overhead in a press).
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
             legL=(12, 24), legR=(12, 2), shinL=(12, 30), shinR=(12, 16),
             pitch=6, ground=False, lift=0.04),
        -42, 4),
    'boxing': (
        dict(torso=20, neck=-12, armL=(34, 62), armR=(34, 62), foreL=(30, 120), foreR=(30, 120),
             legL=(12, 24), legR=(12, 2), shinL=(12, 30), shinR=(12, 16),
             pitch=6, ground=False, lift=0.04),
        -42, 4),
    'yoga_stretching': (
        dict(torso=12, neck=-6, armL=(96, 20), armR=(96, 20), foreL=(90, 30), foreR=(90, 30),
             legL=(6, 10), legR=(6, 10), shinL=(6, 6), shinR=(6, 6),
             ground=True),
        -34, 6),
    'rowing': (
        dict(torso=22, neck=-10, armL=(28, 70), armR=(28, 70), foreL=(24, 108), foreR=(24, 108),
             legL=(10, 40), legR=(10, 20), shinL=(10, 56), shinR=(10, 40),
             ground=False, lift=0.18),
        -40, 6),
    'hiit': (
        dict(torso=30, neck=-14, armL=(50, 40), armR=(50, 40), foreL=(46, 86), foreR=(46, 86),
             legL=(12, 30), legR=(12, -6), shinL=(12, 22), shinR=(12, 10),
             pitch=8, ground=False, lift=0.06),
        -36, 4),
    'jump_rope': (
        dict(torso=10, neck=-6, armL=(40, 18), armR=(40, 18), foreL=(34, 46), foreR=(34, 46),
             legL=(4, 10), legR=(4, 6), shinL=(4, 8), shinR=(4, 6),
             ground=False, lift=0.10),
        -34, 4),
    'stair_climbing': (
        dict(torso=16, neck=-8, armL=(30, 20), armR=(30, 20), foreL=(26, 54), foreR=(26, 54),
             legL=(6, 34), legR=(6, 10), shinL=(6, 52), shinR=(6, 34),
             ground=False, lift=0.12),
        -36, 4),
    'walking': (
        dict(torso=6, neck=-4, armL=(14, 6), armR=(14, 6), foreL=(12, 24), foreR=(12, 24),
             legL=(4, 14), legR=(4, -8), shinL=(4, 6), shinR=(4, 12),
             ground=False, lift=0.02),
        -34, 4),
}


# ------------------------------------------------------------------ anatomy map

# The muscle-map view (Train -> a muscle -> the big body diagram) also uses grey mannequin art.
# These renders replace the front/back base bodies with the same photoreal skin, and regenerate
# the red per-muscle overlays with the SAME camera, so the two layers still line up exactly.
ANAT_RES = (int(os.environ.get('ANAT_W', '600')), int(os.environ.get('ANAT_H', '1000')))
ANAT_VIEWS = {'front': 0.0, 'back': 180.0}


def anatomy_frame(view):
    """Frame the whole body for an anatomy view; identical for the base and every overlay."""
    center, radius = body_bbox()
    # the base/overlay pair must use the exact same camera, so the framing is computed once
    return frame(center, radius * 1.02, ANAT_VIEWS[view], 0.0, pad=1.06, lens=85.0)


def set_res(w, h):
    S = bpy.context.scene
    S.render.resolution_x, S.render.resolution_y = w, h


def render_anatomy_base(view):
    set_res(*ANAT_RES)
    anatomy_frame(view)
    M.paint([])
    set_transparent(True)      # cut-out body on transparent, exactly like the current art
    M.render(OUT_ANAT + view + '.png')
    set_res(RES, RES)
    print('P', 'anat_' + view, flush=True)


def render_anatomy_overlay(view, group):
    set_res(*ANAT_RES)
    anatomy_frame(view)
    M.paint([group], holdout_rest=True)
    set_transparent(True)
    M.render(OUT_ANAT + view + '_' + group.replace(' ', '_') + '.png')
    set_res(RES, RES)
    print('P', 'anat_' + view + '_' + group, flush=True)


# ------------------------------------------------------------------ main

def main():
    t0 = time.time()
    dry = os.environ.get('DRY', '') == '1'
    body, arm = M.build((RES, RES), 2 if dry else SAMPLES)
    mat = make_tile_material()
    M.MAT['body'] = mat
    body.data.materials.clear()
    body.data.materials.append(mat)
    setup_scene()
    P.init()
    P.props_init()

    os.makedirs(OUT, exist_ok=True)
    os.makedirs(OUT_ANAT, exist_ok=True)
    only = [k for k in os.environ.get('ONLY', '').split(',') if k]
    sh, nsh = int(os.environ.get('SHARD', '0')), int(os.environ.get('SHARDS', '1'))

    jobs = []
    # anatomy map: 2 base bodies + one overlay per muscle group per view
    for view in ANAT_VIEWS:
        jobs.append(('anat_base', view, None, 0, 0, 0))
        for g in M.GROUPS:
            jobs.append(('anat_ovl', view, g, 0, 0, 0))
    # train-screen tiles
    for k, (groups, az, el, pad) in MUSCLE_TILES.items():
        if only and k not in only:
            continue
        jobs.append(('muscle', k, groups, az, el, pad))
    for k, (pose, az, el) in ACTIVITY_TILES.items():
        if only and k not in only:
            continue
        jobs.append(('activity', k, None, az, el, 1.18))

    jobs = jobs[sh::nsh]

    if dry:
        # a dry run exercises every code path (materials, poses, framing) but renders nothing
        jobs = [j for j in jobs if j[0] != 'anat_ovl' or j[2] in ('chest', 'lats', 'abdominals')]
        for kind, key, groups, az, el, pad in jobs:
            if kind == 'muscle':
                P.reset(); bpy.context.view_layer.update()
                M.paint(groups)
                b = fit_to_view(group_verts(groups), az, el)
                if b is None:
                    print('SKIP', key, flush=True); continue
                set_transparent(False); FLOOR.hide_render = True
                frame(b[0], b[1], az, el, pad)
            elif kind == 'activity':
                M.paint([]); P.apply(dict(ACTIVITY_TILES[key][0])); bpy.context.view_layer.update()
                c, r = body_bbox(); set_transparent(False); FLOOR.hide_render = False
                FLOOR.location = (0.0, 0.0, lowest_point() - 0.004)
                frame(c, r, az, el, pad)
            elif kind == 'anat_base':
                set_res(*ANAT_RES); anatomy_frame(key); M.paint([]); set_transparent(True)
                set_res(RES, RES)
            else:
                set_res(*ANAT_RES); anatomy_frame(key); M.paint([groups], holdout_rest=True); set_transparent(True)
                set_res(RES, RES)
            print('OK', kind, key, flush=True)
        print('ALLDONE', flush=True)
        return

    for kind, key, groups, az, el, pad in jobs:
        if kind == 'muscle':
            P.reset()                     # a previous activity tile may have left the body posed
            bpy.context.view_layer.update()
            M.paint(groups)
            vs = group_verts(groups)
            b = fit_to_view(vs, az, el)
            if b is None:
                print('SKIP', key, flush=True)
                continue
            center, radius = b
            set_transparent(False)
            FLOOR.hide_render = True          # close-ups: no floor in frame
            frame(center, radius, az, el, pad)
            M.render(OUT + key + '.png')
        elif kind == 'activity':
            M.paint([])
            P.apply(dict(ACTIVITY_TILES[key][0]))
            bpy.context.view_layer.update()
            center, radius = body_bbox()
            set_transparent(False)
            FLOOR.hide_render = False
            FLOOR.location = (0.0, 0.0, lowest_point() - 0.004)
            frame(center, radius, az, el, pad)
            M.render(OUT + key + '.png')
        elif kind == 'anat_base':
            render_anatomy_base(key)
            continue
        else:
            render_anatomy_overlay(key, groups)
            continue
        print('P', key, round(time.time() - t0), flush=True)
    print('ALLDONE', flush=True)


if __name__ == '__main__':
    main()
