"""Look refinement for the Meshy coaches: smoother silhouettes (subdivision), skin-like subsurface on skin-coloured
texels only, soft fabric sheen, sharper normal detail. Keeps the original textures (they are the reference look)."""
import bpy


def refine(mesh, subdiv=1):
    if subdiv:
        m = mesh.modifiers.new('subd', 'SUBSURF'); m.levels = 0; m.render_levels = subdiv; m.quality = 3
        # armature must deform first, then subdivide
        mods = mesh.modifiers
        while mods[0].name != 'Armature' and any(x.name == 'Armature' for x in mods):
            bpy.context.view_layer.objects.active = mesh
            bpy.ops.object.modifier_move_up(modifier='Armature')
    for mat in mesh.data.materials:
        if not mat or not mat.use_nodes: continue
        nt = mat.node_tree; N = nt.nodes; Lk = nt.links
        bsdf = next((n for n in N if n.type == 'BSDF_PRINCIPLED'), None)
        if bsdf is None: continue
        base = bsdf.inputs['Base Color'].links[0].from_node if bsdf.inputs['Base Color'].is_linked else None
        # skin mask from the texture itself: warm, mid-bright, moderately saturated texels
        if base is not None:
            sep = N.new('ShaderNodeSeparateColor'); sep.mode = 'HSV'
            Lk.new(base.outputs['Color'], sep.inputs['Color'])
            def band(inp, lo, hi, soft):
                a = N.new('ShaderNodeMapRange'); a.inputs['From Min'].default_value = lo - soft; a.inputs['From Max'].default_value = lo
                a.inputs['To Min'].default_value = 0; a.inputs['To Max'].default_value = 1; Lk.new(inp, a.inputs['Value'])
                b = N.new('ShaderNodeMapRange'); b.inputs['From Min'].default_value = hi; b.inputs['From Max'].default_value = hi + soft
                b.inputs['To Min'].default_value = 1; b.inputs['To Max'].default_value = 0; Lk.new(inp, b.inputs['Value'])
                m = N.new('ShaderNodeMath'); m.operation = 'MULTIPLY'; Lk.new(a.outputs[0], m.inputs[0]); Lk.new(b.outputs[0], m.inputs[1])
                return m.outputs[0]
            hue = band(sep.outputs[0], 0.015, 0.11, 0.03)      # orange-red hues
            sat = band(sep.outputs[1], 0.18, 0.62, 0.08)
            val = band(sep.outputs[2], 0.30, 0.98, 0.10)
            m1 = N.new('ShaderNodeMath'); m1.operation = 'MULTIPLY'; Lk.new(hue, m1.inputs[0]); Lk.new(sat, m1.inputs[1])
            m2 = N.new('ShaderNodeMath'); m2.operation = 'MULTIPLY'; Lk.new(m1.outputs[0], m2.inputs[0]); Lk.new(val, m2.inputs[1])
            sss = N.new('ShaderNodeMath'); sss.operation = 'MULTIPLY'; sss.inputs[1].default_value = 0.35
            Lk.new(m2.outputs[0], sss.inputs[0]); Lk.new(sss.outputs[0], bsdf.inputs['Subsurface Weight'])
            bsdf.inputs['Subsurface Radius'].default_value = (1.0, 0.35, 0.2)
            bsdf.inputs['Subsurface Scale'].default_value = 0.012
            # fabric: a little sheen where it is NOT skin
            inv = N.new('ShaderNodeMath'); inv.operation = 'SUBTRACT'; inv.inputs[0].default_value = 1.0; Lk.new(m2.outputs[0], inv.inputs[1])
            sh = N.new('ShaderNodeMath'); sh.operation = 'MULTIPLY'; sh.inputs[1].default_value = 0.35
            Lk.new(inv.outputs[0], sh.inputs[0]); Lk.new(sh.outputs[0], bsdf.inputs['Sheen Weight'])
            bsdf.inputs['Sheen Roughness'].default_value = 0.5
            # fabric is matte: lift roughness on non-skin texels (Meshy roughness maps read too glossy)
            rin = bsdf.inputs['Roughness']
            src = rin.links[0].from_socket if rin.is_linked else None
            mx = N.new('ShaderNodeMath'); mx.operation = 'MAXIMUM'
            if src is not None: Lk.new(src, mx.inputs[0])
            else: mx.inputs[0].default_value = rin.default_value
            floor_ = N.new('ShaderNodeMath'); floor_.operation = 'MULTIPLY'; floor_.inputs[1].default_value = 0.82
            Lk.new(inv.outputs[0], floor_.inputs[0]); Lk.new(floor_.outputs[0], mx.inputs[1])
            Lk.new(mx.outputs[0], rin)
            if bsdf.inputs['Metallic'].is_linked: Lk.remove(bsdf.inputs['Metallic'].links[0])
            bsdf.inputs['Metallic'].default_value = 0.0
        nm = bsdf.inputs['Normal'].links[0].from_node if bsdf.inputs['Normal'].is_linked else None
        if nm is not None and nm.type == 'NORMAL_MAP': nm.inputs['Strength'].default_value = 1.25
        bsdf.inputs['Specular IOR Level'].default_value = 0.35
