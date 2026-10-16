import bpy, sys, glob, os, math, json
from mathutils import Vector
out = sys.argv[-1]; meta = {}
for f in sorted(glob.glob('/tmp/claude-0/bud/glb/*.glb')):
    name = os.path.basename(f)[9:-12]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=f)
    ms = [o for o in bpy.data.objects if o.type == 'MESH']
    pts = [o.matrix_world @ Vector(c) for o in ms for c in o.bound_box]
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts))); hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    c = (lo + hi) / 2
    s = bpy.context.scene
    s.render.engine = 'BLENDER_WORKBENCH'; s.display.shading.light = 'STUDIO'; s.display.shading.color_type = 'TEXTURE'
    W, H = 800, 1200; s.render.resolution_x = W; s.render.resolution_y = H; s.render.film_transparent = False
    s.world = bpy.data.worlds.new('w'); s.world.color = (0.85, 0.85, 0.85)
    s.display.shading.background_type = 'VIEWPORT'; s.display.shading.background_color = (0.85, 0.85, 0.85)
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam')); s.collection.objects.link(cam); s.camera = cam
    scale = (hi.z - lo.z) * 1.08            # vertical span (H > W so ortho_scale = vertical extent)
    cam.data.type = 'ORTHO'; cam.data.ortho_scale = scale
    cam.location = (c.x, lo.y - 5, c.z); cam.rotation_euler = (math.radians(90), 0, 0)
    s.render.filepath = f'{out}/{name}.png'; bpy.ops.render.render(write_still=True)
    meta[name] = {'cx': c.x, 'cz': c.z, 'scale': scale, 'W': W, 'H': H, 'lo': list(lo), 'hi': list(hi)}
    print('F', name, flush=True)
json.dump(meta, open(f'{out}/meta.json', 'w'), indent=1)
