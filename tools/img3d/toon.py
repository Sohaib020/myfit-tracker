"""Shared Blender helpers for image-to-3D characters: load + normalise a GLB, anime cel look, scene + render."""
import bpy, math, os, mathutils

def load(src, height=1.8):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=src)
    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    bpy.ops.object.select_all(action="DESELECT")
    for o in meshes: o.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    if len(meshes) > 1: bpy.ops.object.join()
    ob = bpy.context.view_layer.objects.active
    for o in list(bpy.context.scene.objects):          # drop empties left by the importer, keep world transform baked
        if o.type != "MESH": bpy.data.objects.remove(o)
    ob.parent = None
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    vs = [v.co for v in ob.data.vertices]
    mn = mathutils.Vector((min(v.x for v in vs), min(v.y for v in vs), min(v.z for v in vs)))
    mx = mathutils.Vector((max(v.x for v in vs), max(v.y for v in vs), max(v.z for v in vs)))
    s = height / max(mx.z - mn.z, 1e-6)
    for v in ob.data.vertices: v.co = ((v.co.x - (mn.x + mx.x) / 2) * s, (v.co.y - (mn.y + mx.y) / 2) * s, (v.co.z - mn.z) * s)
    ob.data.update()
    return ob

RAMP = ((0.0, (0.50, 0.48, 0.62)), (0.25, (0.82, 0.80, 0.88)), (0.55, (1.0, 1.0, 1.0)))

def toonify(ob, outline=0.006, ramp=RAMP, lift=1.25):
    for sl in ob.material_slots:
        m = sl.material
        if not m or not m.use_nodes: continue
        nt = m.node_tree; bs = next((n for n in nt.nodes if n.type == "BSDF_PRINCIPLED"), None)
        out = next((n for n in nt.nodes if n.type == "OUTPUT_MATERIAL"), None)
        if not bs or not out: continue
        col = bs.inputs["Base Color"].links[0].from_socket if bs.inputs["Base Color"].links else None
        N = nt.nodes.new; L = nt.links.new
        dif = N("ShaderNodeBsdfDiffuse"); s2r = N("ShaderNodeShaderToRGB"); L(dif.outputs[0], s2r.inputs[0])
        r = N("ShaderNodeValToRGB"); r.color_ramp.interpolation = "CONSTANT"; e = r.color_ramp.elements
        e[0].position, e[0].color = ramp[0][0], (*ramp[0][1], 1); e[1].position, e[1].color = ramp[1][0], (*ramp[1][1], 1)
        for p, c in ramp[2:]: el = e.new(p); el.color = (*c, 1)
        L(s2r.outputs[0], r.inputs[0])
        mul = N("ShaderNodeMix"); mul.data_type = "RGBA"; mul.blend_type = "MULTIPLY"; mul.inputs[0].default_value = 1
        if col: L(col, mul.inputs[6])
        else: mul.inputs[6].default_value = bs.inputs["Base Color"].default_value
        L(r.outputs[0], mul.inputs[7])
        # rim light: fresnel band in the accent colour
        fr = N("ShaderNodeLayerWeight"); fr.inputs[0].default_value = 0.18
        rimr = N("ShaderNodeValToRGB"); rimr.color_ramp.interpolation = "CONSTANT"; re = rimr.color_ramp.elements
        re[0].position, re[0].color = 0.0, (0, 0, 0, 1); re[1].position, re[1].color = 0.75, (0.55, 1.0, 0.25, 1)
        L(fr.outputs["Facing"], rimr.inputs[0])
        add = N("ShaderNodeMix"); add.data_type = "RGBA"; add.blend_type = "ADD"; add.inputs[0].default_value = 0.35
        L(mul.outputs[2], add.inputs[6]); L(rimr.outputs[0], add.inputs[7])
        br = N("ShaderNodeMix"); br.data_type = "RGBA"; br.blend_type = "MULTIPLY"; br.inputs[0].default_value = 1
        L(add.outputs[2], br.inputs[6]); br.inputs[7].default_value = (lift, lift, lift, 1)
        emi = N("ShaderNodeEmission"); L(br.outputs[2], emi.inputs[0]); L(emi.outputs[0], out.inputs["Surface"])
    if outline:
        sol = ob.modifiers.new("outline", "SOLIDIFY"); sol.thickness = -outline; sol.use_flip_normals = True
        sol.material_offset = len(ob.material_slots); sol.use_rim = False
        om = bpy.data.materials.new("ink"); om.use_nodes = True; nt = om.node_tree; nt.nodes.clear(); N = nt.nodes.new
        e2 = N("ShaderNodeEmission"); e2.inputs[0].default_value = (0.015, 0.015, 0.025, 1)
        tr = N("ShaderNodeBsdfTransparent"); geo = N("ShaderNodeNewGeometry"); mx = N("ShaderNodeMixShader")
        nt.links.new(geo.outputs["Backfacing"], mx.inputs[0]); nt.links.new(e2.outputs[0], mx.inputs[1]); nt.links.new(tr.outputs[0], mx.inputs[2])
        o = N("ShaderNodeOutputMaterial"); nt.links.new(mx.outputs[0], o.inputs[0]); ob.data.materials.append(om)

def scene(res=(512, 640), samples=16, transparent=False, bg=(0.03, 0.035, 0.03)):
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"; sc.cycles.samples = samples; sc.cycles.device = "CPU"
    sc.cycles.use_denoising = True
    sc.render.resolution_x, sc.render.resolution_y = res; sc.view_settings.view_transform = "Standard"
    sc.render.film_transparent = transparent
    w = bpy.data.worlds.new("w"); sc.world = w; w.use_nodes = True; w.node_tree.nodes["Background"].inputs[0].default_value = (*bg, 1)
    L = bpy.data.lights.new("sun", "SUN"); L.energy = 3.2; L.angle = 0.2
    lo = bpy.data.objects.new("sun", L); sc.collection.objects.link(lo); lo.rotation_euler = (math.radians(48), math.radians(12), math.radians(28))
    cd = bpy.data.cameras.new("c"); cd.lens = 50; cam = bpy.data.objects.new("c", cd); sc.collection.objects.link(cam); sc.camera = cam
    t = bpy.data.objects.new("t", None); t.location = (0, 0, 0.92); sc.collection.objects.link(t)
    tc = cam.constraints.new("TRACK_TO"); tc.target = t; tc.track_axis = "TRACK_NEGATIVE_Z"; tc.up_axis = "UP_Y"
    return cam

def shoot(cam, path, yaw=0, dist=4.2, z=1.1):
    a = math.radians(-90 + yaw); cam.location = (dist * math.cos(a), dist * math.sin(a), z)
    bpy.context.scene.render.filepath = path; bpy.ops.render.render(write_still=True)
