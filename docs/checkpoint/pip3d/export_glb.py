"""Exports Pip as a GLB for real-time rendering (Filament): neutral pose, head under HeadRig, arms pivoting
at the shoulders, extra face parts (happy lids, open mouth) for expressions. Usage: python3 export_glb.py out.glb"""
import sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy, bmesh
from mathutils import Vector
import pipgen as P, anims as A

OUT = sys.argv[-1]
P.build()
O = P.OBJ
base = dict(A.idle(0.0))
base.update(blink=0.0, yaw=0.0, tilt=0.0, nod=0.0, breath=0.0, armL=18.0, armR=18.0, fwdL=10.0, fwdR=10.0, ant=0.0,
            eyes='open', mouth='smile', brows='normal', blush=0.7, look=(0.0, 0.0))

def dup(o, name):
    n = o.copy(); n.data = o.data.copy(); n.name = name; P.link(n); n.parent = o.parent; n.matrix_basis = o.matrix_basis.copy()
    O[name] = n; return n

# extra expression parts
P.pose(dict(base, eyes='happy'))
for s in 'LR': dup(O['Lid' + s], 'LidHappy' + s)
P.pose(dict(base, mouth='open', open=0.5))
dup(O['MouthOpen'], 'MouthBig'); dup(O['Tongue'], 'TongueBig')
P.pose(base)

# delete props, lights, camera
for name in ('Dumbbell', 'Bottle', 'Heart', 'Bowl', 'Pillow', 'Thumb'):
    o = bpy.data.objects.get(name)
    if o:
        for c in list(o.children_recursive) + [o]: bpy.data.objects.remove(c, do_unlink=True)
for o in list(bpy.data.objects):
    if o.type in ('LIGHT', 'CAMERA'): bpy.data.objects.remove(o, do_unlink=True)
# the old single-eye lids (blink lines) are replaced by scaling the eyes at runtime
for s in 'LR':
    o = bpy.data.objects.get('Lid' + s)
    if o: bpy.data.objects.remove(o, do_unlink=True)

def select_only(o):
    bpy.ops.object.select_all(action='DESELECT'); o.select_set(True); bpy.context.view_layer.objects.active = o

# metaballs -> meshes
for mb in bpy.data.metaballs: mb.resolution = 0.034; mb.render_resolution = 0.034
bpy.context.view_layer.update()
for name in ('Head', 'Body', 'ArmL', 'ArmR', 'LegL', 'LegR'):
    o = bpy.data.objects[name]
    o.hide_viewport = False; o.hide_render = False
    select_only(o); bpy.ops.object.convert(target='MESH')
    m = bpy.context.active_object; m.name = name
    for p in m.data.polygons: p.use_smooth = True
# head mesh follows the head rig
h = bpy.data.objects['Head']; mw = h.matrix_world.copy(); h.parent = O['HeadRig']; h.matrix_world = mw
# arms pivot at the shoulders
for side, s in (('L', 1), ('R', -1)):
    a = bpy.data.objects['Arm' + side]
    sh = O['Root'].matrix_world @ Vector((s * P.SHOULDER.x, P.SHOULDER.y, P.SHOULDER.z))
    bpy.context.scene.cursor.location = sh
    select_only(a); bpy.ops.object.origin_set(type='ORIGIN_CURSOR')

# curves -> meshes
for o in list(bpy.data.objects):
    if o.type == 'CURVE':
        o.hide_viewport = False; o.hide_render = False
        select_only(o); bpy.ops.object.convert(target='MESH')
        for p in bpy.context.active_object.data.polygons: p.use_smooth = True

# sweatband: navy with a cream stripe (object-space height), as two materials
band = bpy.data.objects['Band']
navy = bpy.data.materials.new('band_navy'); navy.use_nodes = True
nb = navy.node_tree.nodes['Principled BSDF']; nb.inputs['Base Color'].default_value = P.srgb('#173F55'); nb.inputs['Roughness'].default_value = 0.9; nb.inputs['Sheen Weight'].default_value = 1.0
cream = bpy.data.materials.new('band_cream'); cream.use_nodes = True
cb = cream.node_tree.nodes['Principled BSDF']; cb.inputs['Base Color'].default_value = P.srgb('#F3EEE2'); cb.inputs['Roughness'].default_value = 0.9; cb.inputs['Sheen Weight'].default_value = 1.0
band.data.materials.clear(); band.data.materials.append(navy); band.data.materials.append(cream)
for poly in band.data.polygons:
    z = sum(band.data.vertices[v].co.z for v in poly.vertices) / len(poly.vertices)
    fac = (z + 0.16) / 0.32
    poly.material_index = 1 if 0.60 <= fac < 0.72 else 0

# blush: soft edge from three stacked discs of falling opacity
for s in 'LR':
    bl = bpy.data.objects['Blush' + s]
    bl.data.materials.clear()
    for k, (sc, a) in enumerate(((1.0, 0.28), (0.78, 0.30), (0.55, 0.34))):
        m = bpy.data.materials.new('blush%d' % k); m.use_nodes = True; m.blend_method = 'BLEND'
        b = m.node_tree.nodes['Principled BSDF']; b.inputs['Base Color'].default_value = P.srgb('#FF9AAE'); b.inputs['Alpha'].default_value = a; b.inputs['Roughness'].default_value = 0.9
        if k == 0: bl.data.materials.append(m); continue
        d = bl.copy(); d.data = bl.data.copy(); d.name = 'Blush%s_%d' % (s, k); P.link(d); d.parent = bl.parent
        d.matrix_basis = bl.matrix_basis.copy(); d.data.materials.clear(); d.data.materials.append(m)
        from mathutils import Matrix
        d.matrix_basis = d.matrix_basis @ Matrix.Translation((0, 0, 0.002 * k)) @ Matrix.Diagonal((sc, sc, 1, 1))

# plush materials: drop the procedural bump (not portable), keep colour, roughness and sheen
for m in bpy.data.materials:
    if not m.use_nodes: continue
    nt = m.node_tree
    for n in list(nt.nodes):
        if n.type in ('BUMP', 'TEX_NOISE', 'TEX_WAVE'): nt.nodes.remove(n)
# the glint emission materials -> unlit white principled with emission (glTF emissive)
for name in ('glint',):
    m = bpy.data.materials.get(name)
    if m:
        nt = m.node_tree; nt.nodes.clear()
        b = nt.nodes.new('ShaderNodeBsdfPrincipled'); out = nt.nodes.new('ShaderNodeOutputMaterial')
        b.inputs['Base Color'].default_value = (1, 1, 1, 1); b.inputs['Emission Color'].default_value = (1, 1, 1, 1); b.inputs['Emission Strength'].default_value = 1.0
        nt.links.new(b.outputs[0], out.inputs[0])

for o in bpy.data.objects:
    o.hide_viewport = False; o.hide_render = False; o.hide_set(False)

tris = 0
for o in bpy.data.objects:
    if o.type == 'MESH':
        o.data.calc_loop_triangles(); tris += len(o.data.loop_triangles)
print('TRIS', tris)
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', use_selection=False, export_apply=True, export_lights=False,
                          export_cameras=False, export_yup=True, export_animations=False)
print('NODES', [o.name for o in bpy.data.objects])
