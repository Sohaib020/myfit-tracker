"""Shrink a rigged champion GLB for storage/CI: ≤60k triangles (weights kept), textures ≤1024 px as JPEG.
  python compact.py <in.glb> <out.glb>"""
import bpy, sys
src, out = sys.argv[-2:]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=src)
mesh = max((o for o in bpy.data.objects if o.type == 'MESH'), key=lambda o: len(o.data.vertices))
tris = sum(len(p.vertices) - 2 for p in mesh.data.polygons)
if tris > 60000:
    d = mesh.modifiers.new('dec', 'DECIMATE'); d.ratio = 60000 / tris; d.use_collapse_triangulate = True
    while mesh.modifiers.find('dec') > 0:
        with bpy.context.temp_override(object=mesh): bpy.ops.object.modifier_move_up(modifier='dec')
    with bpy.context.temp_override(object=mesh, active_object=mesh): bpy.ops.object.modifier_apply(modifier='dec')
for img in bpy.data.images:
    if max(img.size) > 1024:
        k = 1024 / max(img.size); img.scale(int(img.size[0] * k), int(img.size[1] * k))
for o in bpy.context.view_layer.objects: o.select_set(o.type in ('MESH', 'ARMATURE'))
bpy.ops.export_scene.gltf(filepath=out, export_format='GLB', use_selection=True, export_skins=True, export_animations=False,
                          export_image_format='JPEG', export_jpeg_quality=88)
print('COMPACT', out, tris)
