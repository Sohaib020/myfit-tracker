"""Outfit tiers for Heroes, fitted to the sculpted Quaternius body.
Garments are cut from the body surface with exact planes (clean seams), lifted off the skin and thickened; they keep the
body's skin weights, so they follow every animation. Hard pieces (chains, rings, plates) ride single bones.
Tiers: 1 streetwear (hoodie, joggers, sneakers), 2 epic (gold trims, chain, glow seams), 3 legendary (moulded armour)."""
import bpy, bmesh, math
from mathutils import Vector, Matrix
import heroes as H

GOLD, LIME, INK = '#E3B24A', '#B6FF3B', '#16171B'

# ------------------------------------------------------------------ materials
def mat(name, color, rough=0.5, metal=0.0, coat=0.0, sheen=0.0, emit=0.0, weave=0.0):
    m = bpy.data.materials.get(name)
    if m: return m
    m = bpy.data.materials.new(name); m.use_nodes = True; nt = m.node_tree; b = nt.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = H.srgb(color); b.inputs['Roughness'].default_value = rough
    b.inputs['Metallic'].default_value = metal; b.inputs['Coat Weight'].default_value = coat
    if sheen: b.inputs['Sheen Weight'].default_value = sheen; b.inputs['Sheen Roughness'].default_value = 0.45
    if emit: b.inputs['Emission Color'].default_value = H.srgb(color); b.inputs['Emission Strength'].default_value = emit
    if weave:   # fine knit/weave bump
        tc = nt.nodes.new('ShaderNodeTexCoord'); w = nt.nodes.new('ShaderNodeTexWave'); w.inputs['Scale'].default_value = 900
        w.inputs['Distortion'].default_value = 2.0; nt.links.new(tc.outputs['Object'], w.inputs['Vector'])
        bm = nt.nodes.new('ShaderNodeBump'); bm.inputs['Strength'].default_value = weave; bm.inputs['Distance'].default_value = 0.0015
        nt.links.new(w.outputs['Fac'], bm.inputs['Height']); nt.links.new(bm.outputs['Normal'], b.inputs['Normal'])
    return m

def tint_mat(m, color, strength=1.0):
    nt = m.node_tree; b = next(n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED')
    src = b.inputs['Base Color'].links[0].from_socket if b.inputs['Base Color'].links else None
    mix = nt.nodes.new('ShaderNodeMix'); mix.data_type = 'RGBA'; mix.blend_type = 'MULTIPLY'; mix.inputs[0].default_value = strength
    if src: nt.links.new(src, mix.inputs[6])
    else: mix.inputs[6].default_value = b.inputs['Base Color'].default_value
    mix.inputs[7].default_value = H.srgb(color); nt.links.new(mix.outputs[2], b.inputs['Base Color'])

def tint(obj, color, strength=1.0):
    """Multiply an object's base colour (texture) by a tint — skin tones, hair colour."""
    for sl in obj.material_slots:
        m = sl.material; nt = m.node_tree; b = next(n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED')
        src = b.inputs['Base Color'].links[0].from_socket if b.inputs['Base Color'].links else None
        mix = nt.nodes.new('ShaderNodeMix'); mix.data_type = 'RGBA'; mix.blend_type = 'MULTIPLY'; mix.inputs[0].default_value = strength
        if src: nt.links.new(src, mix.inputs[6])
        else: mix.inputs[6].default_value = b.inputs['Base Color'].default_value
        mix.inputs[7].default_value = H.srgb(color); nt.links.new(mix.outputs[2], b.inputs['Base Color'])

# ------------------------------------------------------------------ muscle growth per tier
MUSCLE = {'spine_03': 1.0, 'clavicle_l': 1.0, 'clavicle_r': 1.0, 'upperarm_l': 1.0, 'upperarm_r': 1.0, 'lowerarm_l': 0.7, 'lowerarm_r': 0.7,
          'thigh_l': 0.75, 'thigh_r': 0.75, 'calf_l': 0.45, 'calf_r': 0.45, 'spine_02': 0.45, 'neck_01': 0.35}
BULK = {1: 0.0, 2: 0.005, 3: 0.011}          # metres of growth along the surface (~8% / ~16% bulk)
STATE = {'bulk': 0.0}

def prep_muscle(level):
    """Add a 'muscle' weight map to the body (copied into every garment) and a bulk layer for this tier."""
    body = H.BODY; amt = BULK.get(level, 0.0); STATE['bulk'] = amt
    g = body.vertex_groups.get('muscle') or body.vertex_groups.new(name='muscle')
    idx = {vg.index: MUSCLE.get(vg.name, 0.0) for vg in body.vertex_groups}
    for v in body.data.vertices:
        w = min(1.0, sum(e.weight * idx.get(e.group, 0.0) for e in v.groups))
        if w > 0.01: g.add([v.index], w, 'REPLACE')
    if amt > 0: _bulk_mod(body)

def _bulk_mod(ob):
    amt = STATE['bulk']
    if amt <= 0 or 'muscle' not in ob.vertex_groups: return
    d = ob.modifiers.new('bulk', 'DISPLACE'); d.strength = amt; d.mid_level = 0.0; d.vertex_group = 'muscle'
    # keep it right after the armature so garments lifted on top inherit the bigger silhouette
    order = [m.name for m in ob.modifiers]
    if 'arm' in order or any(m.type == 'ARMATURE' for m in ob.modifiers):
        arm_i = next(i for i, m in enumerate(ob.modifiers) if m.type == 'ARMATURE')
        while list(ob.modifiers).index(d) > arm_i + 1:
            bpy.context.view_layer.objects.active = ob; bpy.ops.object.modifier_move_up(modifier=d.name)

# ------------------------------------------------------------------ fitted shells
def shell(name, planes, lift, thick, m, rim=None, sub=1, extra=None, drape=0):
    """Copy the body, keep the part on the positive side of every (point, normal) plane, lift it off the skin."""
    body = H.BODY
    me = body.data.copy(); ob = bpy.data.objects.new(name, me); H.S.collection.objects.link(ob)
    ob.matrix_world = body.matrix_world.copy(); ob.parent = H.RIG; ob.matrix_parent_inverse = body.matrix_parent_inverse.copy()
    for g in body.vertex_groups:
        if g.name not in ob.vertex_groups: ob.vertex_groups.new(name=g.name)
    bm = bmesh.new(); bm.from_mesh(me)
    for co, no in planes:
        geom = bm.verts[:] + bm.edges[:] + bm.faces[:]
        bmesh.ops.bisect_plane(bm, geom=geom, dist=1e-5, plane_co=Vector(co), plane_no=Vector(no), clear_inner=True)
    if extra: extra(bm)
    bmesh.ops.dissolve_degenerate(bm, dist=0.0015, edges=bm.edges[:])
    # weld the cut edge: merge near-coincident verts created by successive planes
    bmesh.ops.remove_doubles(bm, verts=bm.verts[:], dist=0.0015)
    tiny = [f for f in bm.faces if f.calc_area() < 1e-9]
    if tiny: bmesh.ops.delete(bm, geom=tiny, context='FACES')
    wire = [e for e in bm.edges if not e.link_faces]
    if wire: bmesh.ops.delete(bm, geom=wire, context='EDGES')
    loose = [v for v in bm.verts if not v.link_faces]
    if loose: bmesh.ops.delete(bm, geom=loose, context='VERTS')
    # drop slivers of 1-2 faces left dangling by cuts (non-manifold fans crash the renderer)
    for _ in range(2):
        lone = [f for f in bm.faces if sum(1 for e in f.edges if len(e.link_faces) > 1) <= 1]
        if lone: bmesh.ops.delete(bm, geom=lone, context='FACES')
        loose = [v for v in bm.verts if not v.link_faces]
        if loose: bmesh.ops.delete(bm, geom=loose, context='VERTS')
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    bm.to_mesh(me); bm.free()
    me.materials.clear(); me.materials.append(m)
    if rim: me.materials.append(rim)
    for p in me.polygons: p.use_smooth = True; p.material_index = 0
    a = ob.modifiers.new('arm', 'ARMATURE'); a.object = H.RIG
    _bulk_mod(ob)
    d = ob.modifiers.new('lift', 'DISPLACE'); d.strength = lift; d.mid_level = 0.0
    if drape:   # fabric doesn't show every muscle: relax the surface after lifting it
        sm = ob.modifiers.new('drape', 'SMOOTH'); sm.factor = 0.9; sm.iterations = drape
    so = ob.modifiers.new('thick', 'SOLIDIFY'); so.thickness = thick; so.offset = -1; so.use_rim = True; so.use_even_offset = False; so.use_quality_normals = True
    if rim: so.material_offset_rim = 1
    if sub:
        sd = ob.modifiers.new('sub', 'SUBSURF'); sd.levels = 0; sd.render_levels = sub
    return ob

def boundary_ring(ob, near, axis, m, minor=0.012, scale=1.0, bone=None, grow=0.008):
    """A rib/trim ring fitted to the shell's open edge closest to [near] (rest pose), riding [bone]."""
    me = ob.data; bm = bmesh.new(); bm.from_mesh(me)
    edge_verts = {v.index: v.co.copy() for e in bm.edges if e.is_boundary for v in e.verts}
    bm.free()
    near = Vector(near); pts = [c for c in edge_verts.values() if (c - near).length < 0.14]
    if len(pts) < 6: return None
    c = sum(pts, Vector()) / len(pts)
    ax = Vector(axis).normalized()
    # radii in the ring plane
    u = ax.orthogonal().normalized(); w = ax.cross(u).normalized()
    ru = max(abs((p - c).dot(u)) for p in pts) + grow; rw = max(abs((p - c).dot(w)) for p in pts) + grow
    bpy.ops.mesh.primitive_torus_add(major_radius=1.0, minor_radius=minor / max(min(ru, rw), 1e-3), major_segments=64, minor_segments=12)
    r = bpy.context.active_object; r.name = ob.name + '_ring'
    rot = Matrix((u, w, ax)).transposed().to_4x4()
    r.matrix_world = Matrix.Translation(c) @ rot @ Matrix.Diagonal((ru * scale, rw * scale, min(ru, rw), 1.0))
    for p in r.data.polygons: p.use_smooth = True
    r.data.materials.append(m); _ride(r, bone); return r

def _ride(o, bone):
    bpy.context.view_layer.update(); mw = o.matrix_world.copy(); o.parent = H.RIG; o.parent_type = 'BONE'; o.parent_bone = bone
    bpy.context.view_layer.update(); o.matrix_world = mw

def blob(name, loc, scale, m, bone, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, location=loc, rotation=rot)
    o = bpy.context.active_object; o.name = name; o.scale = scale
    for p in o.data.polygons: p.use_smooth = True
    o.data.materials.append(m); sd = o.modifiers.new('s', 'SUBSURF'); sd.render_levels = 1; _ride(o, bone); return o

def torus(name, loc, r, minor, m, bone, rot=(0, 0, 0), scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_torus_add(major_radius=r, minor_radius=minor, major_segments=72, minor_segments=12, location=loc, rotation=rot)
    o = bpy.context.active_object; o.name = name; o.scale = scale
    for p in o.data.polygons: p.use_smooth = True
    o.data.materials.append(m); _ride(o, bone); return o

def tube(name, pts, bevel, m, bone):
    cu = bpy.data.curves.new(name, 'CURVE'); cu.dimensions = '3D'; cu.bevel_depth = bevel; cu.bevel_resolution = 4; cu.use_fill_caps = True
    sp = cu.splines.new('BEZIER'); sp.bezier_points.add(len(pts) - 1)
    for p, q in zip(sp.bezier_points, pts): p.co = q; p.handle_left_type = p.handle_right_type = 'AUTO'
    o = bpy.data.objects.new(name, cu); H.S.collection.objects.link(o); o.data.materials.append(m); _ride(o, bone); return o

def bone_head(n): return H.RIG.matrix_world @ H.RIG.data.bones[n].head_local
def bone_tail(n): return H.RIG.matrix_world @ H.RIG.data.bones[n].tail_local

# ------------------------------------------------------------------ the tiers
def dress(level, accent='#C8102E', female=False, hijab=False):
    acc = mat('acc', accent, rough=0.75, sheen=0.1, weave=0.15)
    fab = mat('fab', '#0C0D10', rough=0.9, sheen=0.22, weave=0.25)
    fab2 = mat('fab2', '#2B2E35', rough=0.9, sheen=0.25, weave=0.25)
    gold = mat('gold', GOLD, rough=0.22, metal=1.0, coat=0.5)
    glow = mat('glow', LIME, emit=8.0)
    plate = mat('plate', '#8E1023', rough=0.28, metal=0.6, coat=1.0)
    dark = mat('darkplate', '#191B20', rough=0.32, metal=0.5, coat=1.0)
    white = mat('white', '#EDEDEA', rough=0.5)
    trim = gold if level >= 2 else acc
    wristL, wristR = bone_head('hand_l'), bone_head('hand_r')
    neck = bone_head('neck_01'); hipz = bone_head('pelvis').z
    ankle = bone_head('foot_l').z + 0.05
    # ---- joggers: waist to ankle, side stripe
    jog = shell('Joggers', [((0, 0, hipz + 0.07), (0, 0, -1)), ((0, 0, ankle), (0, 0, 1))], 0.02, 0.006, fab2, rim=trim, drape=3)
    for s, nm in ((1, 'calf_l'), (-1, 'calf_r')):
        a = bone_head('foot_l' if s > 0 else 'foot_r')
        boundary_ring(jog, (a.x, a.y, ankle), (0, 0, 1), trim, minor=0.011, bone=nm)
    # ---- top
    if level <= 2:
        ua_l, ua_r = bone_head('upperarm_l'), bone_head('upperarm_r'); la_l = bone_head('lowerarm_l')
        cut = ua_l.x + (la_l.x - ua_l.x) * 0.55          # sleeve ends just above the elbow
        tee = shell('Tee', [((0, 0, hipz - 0.03), (0, 0, 1)), ((cut, 0, 0), (-1, 0, 0)), ((-cut, 0, 0), (1, 0, 0)),
                            ((0, -0.08, neck.z - 0.03), (0, 0.45, -1))], 0.012, 0.006, fab, rim=fab, drape=2)
        boundary_ring(tee, (cut, ua_l.y, ua_l.z), (1, 0, 0), trim, minor=0.009, bone='upperarm_l')
        boundary_ring(tee, (-cut, ua_r.y, ua_r.z), (1, 0, 0), trim, minor=0.009, bone='upperarm_r')
        # shoulder panels in the accent colour (yoke) + a small chest emblem block
        shell('Yoke', [((0, 0, ua_l.z - 0.02), (0, 0, 1)), ((cut - 0.01, 0, 0), (-1, 0, 0)), ((-cut + 0.01, 0, 0), (1, 0, 0)),
                       ((0, -0.08, neck.z - 0.03), (0, 0.45, -1))], 0.016, 0.004, mat('yoke', accent, rough=0.75, sheen=0.1, weave=0.2), rim=trim, drape=2)
        blob('Emblem', (0.075, -0.125, ua_l.z - 0.1), (0.022, 0.006, 0.022), trim, 'spine_03')
        if level == 2:
            torus('Chain', (0, -0.04, neck.z - 0.07), 0.12, 0.007, gold, 'spine_03', rot=(math.radians(-38), 0, 0), scale=(1.0, 0.9, 1.0))
            blob('Medal', (0, -0.16, neck.z - 0.17), (0.032, 0.009, 0.032), gold, 'spine_03')
            blob('MedalGem', (0, -0.168, neck.z - 0.17), (0.016, 0.004, 0.016), glow, 'spine_03')
            for sx in (1, -1):
                tube('Seam%d' % sx, [Vector((sx * 0.15, -0.12, neck.z - 0.12)), Vector((sx * 0.17, -0.13, neck.z - 0.3)), Vector((sx * 0.14, -0.12, hipz + 0.06))], 0.0035, glow, 'spine_02')
    else:
        shell('Suit', [((0, 0, hipz - 0.02), (0, 0, 1)), ((wristL.x - 0.03, 0, 0), (-1, 0, 0)), ((wristR.x + 0.03, 0, 0), (1, 0, 0)),
                       ((0, -0.06, neck.z - 0.01), (0, 0.4, -1))], 0.006, 0.004, fab, sub=1)
        # moulded armour: chest, abdomen, pauldrons, gauntlets, greaves (cut from the anatomy, lifted, thick, gold rim)
        sh = bone_head('upperarm_l')
        shell('ChestPlate', [((0, 0, sh.z - 0.27), (0, 0, 1)), ((0, 0, neck.z - 0.05), (0, 0.25, -1)), ((0, 0.0, 0), (0, -1, 0)),
                             ((sh.x - 0.03, 0, 0), (-1, 0, 0)), ((-sh.x + 0.03, 0, 0), (1, 0, 0))], 0.03, 0.012, plate, rim=gold)
        shell('AbPlate', [((0, 0, hipz + 0.09), (0, 0, 1)), ((0, 0, sh.z - 0.29), (0, 0, -1)), ((0, -0.02, 0), (0, -1, 0)),
                          ((0.13, 0, 0), (-1, 0, 0)), ((-0.13, 0, 0), (1, 0, 0))], 0.022, 0.01, dark, rim=gold)
        for s, ua, la, cf in ((1, 'upperarm_l', 'lowerarm_l', 'calf_l'), (-1, 'upperarm_r', 'lowerarm_r', 'calf_r')):
            u0 = bone_head(ua); l0 = bone_head(la); w0 = bone_head('hand_l' if s > 0 else 'hand_r')
            shell('Pauldron' + ua, [((u0.x - s * 0.06, 0, 0), (s, 0, 0)), ((u0.x + s * 0.13, 0, 0), (-s, 0, 0)), ((0, 0, u0.z - 0.035), (0, 0, 1))], 0.04, 0.014, plate, rim=gold)
            shell('Gaunt' + la, [((l0.x + s * 0.09, 0, 0), (s, 0, 0)), ((w0.x - s * 0.03, 0, 0), (-s, 0, 0))], 0.022, 0.008, gold, rim=dark)
            k0 = bone_head(cf)
            shell('Greave' + cf, [((0, 0, k0.z - 0.06), (0, 0, -1)), ((0, 0, ankle + 0.02), (0, 0, 1)), ((0, k0.y + 0.01, 0), (0, -1, 0))], 0.022, 0.009, dark, rim=gold)
            blob('Knee' + cf, (k0.x, k0.y - 0.075, k0.z), (0.055, 0.035, 0.065), gold, cf)
            tube('Rune' + la, [l0 + Vector((s * 0.12, -0.05, 0.0)), l0 + Vector((s * 0.17, -0.055, 0.0)), l0 + Vector((s * 0.22, -0.05, 0.0))], 0.003, glow, la)
        torus('Belt', (0, 0.0, hipz + 0.06), 0.165, 0.016, gold, 'pelvis', scale=(1.0, 0.75, 1.0))
        blob('Buckle', (0, -0.125, hipz + 0.06), (0.045, 0.014, 0.035), gold, 'pelvis')
        blob('Core', (0, -0.15, sh.z - 0.08), (0.03, 0.01, 0.03), glow, 'spine_03')
        torus('CoreRing', (0, -0.147, sh.z - 0.08), 0.038, 0.007, gold, 'spine_03', rot=(math.radians(90), 0, 0))
        torus('Gorget', (0, 0.0, neck.z - 0.025), 0.085, 0.016, gold, 'spine_03', scale=(1.0, 0.9, 1.0))
    # ---- sneakers: foot shell + white/gold sole + accent swoosh-free side panel
    sole = white if level == 1 else gold
    for s, ft in ((1, 'foot_l'), (-1, 'foot_r')):
        f0 = bone_head(ft)
        sk = shell('Shoe' + ft, [((0, 0, ankle + 0.035), (0, 0, -1)), ((f0.x - s * 0.09, 0, 0), (s, 0, 0)), ((f0.x + s * 0.09, 0, 0), (-s, 0, 0))],
                   0.016, 0.006, mat('shoe', '#0D0E10', rough=0.55, coat=0.15), rim=trim)
        b0 = bone_tail('ball_l' if s > 0 else 'ball_r')
        blob('Sole' + ft, ((f0.x + b0.x) / 2, (f0.y + b0.y) / 2 - 0.015, 0.012), (0.058, 0.15, 0.016), sole, ft)
        blob('Panel' + ft, (f0.x + s * 0.052, (f0.y + b0.y) / 2 + 0.01, 0.05), (0.008, 0.06, 0.022), glow if level >= 2 else trim, ft)
    # hide body areas fully covered (feet poke through otherwise)
    return True
