"""Import a GLB, apply a cel/toon look (keeps the baked texture, 3-step light ramp + inverted-hull outline), render
4 views on a dark backdrop. usage: blender -b -P turntable.py -- in.glb out_prefix"""
import bpy, sys, math, os
a = sys.argv[sys.argv.index("--") + 1:]
src, pre = a[0], a[1]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=src)
meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
# normalise: stand on floor, ~1.8 tall
import mathutils
mn = mathutils.Vector((1e9,) * 3); mx = -mn
for o in meshes:
    for c in o.bound_box:
        w = o.matrix_world @ mathutils.Vector(c); mn = mathutils.Vector(map(min, mn, w)); mx = mathutils.Vector(map(max, mx, w))
root = bpy.data.objects.new("root", None); bpy.context.scene.collection.objects.link(root)
for o in bpy.context.scene.objects:
    if o.parent is None and o != root: o.parent = root
s = 1.8 / max(mx.z - mn.z, 1e-3); root.scale = (s, s, s)
root.location = (-(mn.x + mx.x) / 2 * s, -(mn.y + mx.y) / 2 * s, -mn.z * s)
print("TURN bbox", mn, mx)
# toon: wrap each material's base colour into a shader-to-RGB ramp
for o in meshes:
    for sl in o.material_slots:
        m = sl.material
        if not m or not m.use_nodes: continue
        nt = m.node_tree; bs = next((n for n in nt.nodes if n.type == "BSDF_PRINCIPLED"), None)
        out = next((n for n in nt.nodes if n.type == "OUTPUT_MATERIAL"), None)
        if not bs or not out: continue
        col = bs.inputs["Base Color"].links[0].from_socket if bs.inputs["Base Color"].links else None
        dif = nt.nodes.new("ShaderNodeBsdfDiffuse"); s2r = nt.nodes.new("ShaderNodeShaderToRGB"); nt.links.new(dif.outputs[0], s2r.inputs[0])
        ramp = nt.nodes.new("ShaderNodeValToRGB"); ramp.color_ramp.interpolation = "CONSTANT"; e = ramp.color_ramp.elements
        e[0].position = 0.0; e[0].color = (0.45, 0.45, 0.55, 1); e[1].position = 0.22; e[1].color = (0.8, 0.8, 0.85, 1)
        el = e.new(0.55); el.color = (1.0, 1.0, 1.0, 1)
        nt.links.new(s2r.outputs[0], ramp.inputs[0])
        mul = nt.nodes.new("ShaderNodeMix"); mul.data_type = "RGBA"; mul.blend_type = "MULTIPLY"; mul.inputs[0].default_value = 1
        if col: nt.links.new(col, mul.inputs[6])
        else: mul.inputs[6].default_value = bs.inputs["Base Color"].default_value
        nt.links.new(ramp.outputs[0], mul.inputs[7])
        emi = nt.nodes.new("ShaderNodeEmission"); nt.links.new(mul.outputs[2], emi.inputs[0])
        nt.links.new(emi.outputs[0], out.inputs["Surface"])
    # outline: inverted hull
    sol = o.modifiers.new("outline", "SOLIDIFY"); sol.thickness = -0.006 / s; sol.use_flip_normals = True; sol.material_offset = len(o.material_slots)
    om = bpy.data.materials.new("ink"); om.use_nodes = True; om.use_backface_culling = True
    onodes = om.node_tree; onodes.nodes.clear(); e2 = onodes.nodes.new("ShaderNodeEmission"); e2.inputs[0].default_value = (0.02, 0.02, 0.03, 1)
    oo = onodes.nodes.new("ShaderNodeOutputMaterial"); onodes.links.new(e2.outputs[0], oo.inputs[0]); o.data.materials.append(om)
sc = bpy.context.scene
sc.render.engine = "BLENDER_EEVEE_NEXT" if os.environ.get("EEVEE") else "CYCLES"
if sc.render.engine == "CYCLES": sc.cycles.samples = 24; sc.cycles.device = "CPU"
sc.render.resolution_x = 512; sc.render.resolution_y = 640; sc.view_settings.view_transform = "Standard"
w = bpy.data.worlds.new("w"); sc.world = w; w.use_nodes = True; w.node_tree.nodes["Background"].inputs[0].default_value = (0.03, 0.035, 0.03, 1)
L = bpy.data.lights.new("sun", "SUN"); L.energy = 3; lo = bpy.data.objects.new("sun", L); sc.collection.objects.link(lo); lo.rotation_euler = (math.radians(50), math.radians(15), math.radians(30))
cd = bpy.data.cameras.new("c"); cd.lens = 50; cam = bpy.data.objects.new("c", cd); sc.collection.objects.link(cam); sc.camera = cam
t = bpy.data.objects.new("t", None); t.location = (0, 0, 0.92); sc.collection.objects.link(t)
tc = cam.constraints.new("TRACK_TO"); tc.target = t; tc.track_axis = "TRACK_NEGATIVE_Z"; tc.up_axis = "UP_Y"
for i, yaw in enumerate((0, 90, 180, 270)):
    a_ = math.radians(-90 + yaw); cam.location = (4.2 * math.cos(a_), 4.2 * math.sin(a_), 1.1)
    sc.render.filepath = f"{pre}_{i}.png"; bpy.ops.render.render(write_still=True)
print("TURN done")
