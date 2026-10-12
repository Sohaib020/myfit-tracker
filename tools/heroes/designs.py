"""Hero designs: per-character outfits at 3 tiers on the Quaternius bodies, with real garment details.
Characters: blaze (male), nova (female), valor (female, hijab/modest), frost_armour / frost_hybrid (snow-leopard champion).
Every garment is a fitted shell (outfit.shell) or a rigid detail riding one bone, so all library animations work."""
import bpy, bmesh, math, random
from mathutils import Vector
import heroes as H, outfit as O
from outfit import mat, shell, boundary_ring, blob, torus, tube, bone_head, bone_tail

GOLD, LIME = O.GOLD, O.LIME

def P():   # key anatomy points (rest T-pose)
    b = bone_head
    return dict(neck=b('neck_01'), head=b('Head'), hip=b('pelvis'), sh_l=b('upperarm_l'), sh_r=b('upperarm_r'), el_l=b('lowerarm_l'), el_r=b('lowerarm_r'),
                wr_l=b('hand_l'), wr_r=b('hand_r'), kn_l=b('calf_l'), kn_r=b('calf_r'), ft_l=b('foot_l'), ft_r=b('foot_r'),
                ball_l=bone_tail('ball_l'), ball_r=bone_tail('ball_r'), chest=b('spine_03'))

def mats(accent, second='#14151A'):
    M = dict(
        acc=mat('acc', accent, rough=0.72, sheen=0.12, weave=0.15),
        accd=mat('accd', accent, rough=0.35, coat=0.6),
        fab=mat('fab', '#0C0D10', rough=0.9, sheen=0.22, weave=0.25),
        fab2=mat('fab2', '#2B2E35', rough=0.9, sheen=0.25, weave=0.25),
        fab3=mat('fab3', second, rough=0.9, sheen=0.2, weave=0.25),
        rib=mat('rib', '#1D1F24', rough=0.8, weave=0.6),
        seam=mat('seam', '#3A3D45', rough=0.7),
        gold=mat('gold', GOLD, rough=0.22, metal=1.0, coat=0.5),
        glow=mat('glow', LIME, emit=8.0),
        plate=mat('plate', '#8E1023', rough=0.28, metal=0.6, coat=1.0),
        dark=mat('darkplate', '#191B20', rough=0.32, metal=0.5, coat=1.0),
        white=mat('white', '#EDEDEA', rough=0.5),
        wrap=mat('wrap', '#E9E5DC', rough=0.85, weave=0.5),
        shoe=mat('shoe', '#0D0E10', rough=0.55, coat=0.15),
        mid=mat('midsole', '#F2F2EF', rough=0.45),
    )
    return M

# ------------------------------------------------------------------ shared garments
def joggers(M, p, lv, trim, legging=False, lift=None):
    ank = p['ft_l'].z + 0.05
    jog = shell('Joggers', [((0, 0, p['hip'].z + 0.07), (0, 0, -1)), ((0, 0, ank), (0, 0, 1))],
                lift or (0.008 if legging else 0.02), 0.006, M['fab2'], rim=trim, drape=0 if legging else 3)
    for s, ft, cf in ((1, 'ft_l', 'calf_l'), (-1, 'ft_r', 'calf_r')):
        a = p[ft]; boundary_ring(jog, (a.x, a.y, ank), (0, 0, 1), M['rib'] if lv == 1 else trim, minor=0.012, bone=cf)
    boundary_ring(jog, (0, 0.04, p['hip'].z + 0.07), (0, 0, 1), M['rib'], minor=0.014, bone='pelvis')       # waistband
    if not legging:   # drawstrings
        for sx in (1, -1):
            tube('Draw%d' % sx, [Vector((sx * 0.02, -0.13, p['hip'].z + 0.065)), Vector((sx * 0.03, -0.14, p['hip'].z + 0.0)), Vector((sx * 0.025, -0.135, p['hip'].z - 0.06))], 0.0035, M['white'] if lv == 1 else M['gold'], 'pelvis')
    # side seams down each leg (subtle) and knee panel seams
    for s, th in ((1, 'thigh_l'), (-1, 'thigh_r')):
        x = bone_head(th).x
        tube('LegSeam' + th, [Vector((x + s * 0.083, 0.035, p['hip'].z - 0.02)), Vector((x + s * 0.085, 0.035, p['hip'].z - 0.2)), Vector((x + s * 0.072, 0.04, p['hip'].z - 0.4))], 0.0018, trim if lv >= 2 else M['seam'], th)
    return jog

def sneakers(M, p, lv, accent_m):
    ank = p['ft_l'].z + 0.05; sole = M['mid'] if lv == 1 else M['gold']
    for s, ft, bl in ((1, 'foot_l', 'ball_l'), (-1, 'foot_r', 'ball_r')):
        f0 = bone_head(ft); b0 = p[bl]
        shell('Shoe' + ft, [((0, 0, ank + 0.035), (0, 0, -1)), ((f0.x - s * 0.09, 0, 0), (s, 0, 0)), ((f0.x + s * 0.09, 0, 0), (-s, 0, 0))], 0.016, 0.006, M['shoe'], rim=accent_m)
        cx, cy = (f0.x + b0.x) / 2, (f0.y + b0.y) / 2
        blob('Mid' + ft, (cx, cy - 0.015, 0.016), (0.06, 0.152, 0.018), sole, ft)
        blob('Out' + ft, (cx, cy - 0.015, 0.004), (0.058, 0.15, 0.008), M['dark'], ft)
        blob('Heel' + ft, (f0.x, f0.y + 0.055, 0.075), (0.03, 0.012, 0.035), accent_m, ft)
        blob('Panel' + ft, (f0.x + s * 0.054, cy + 0.01, 0.048), (0.007, 0.06, 0.02), M['glow'] if lv >= 2 else accent_m, ft)
        # laces: zig-zag over the instep
        pts = []
        for k in range(6):
            y = f0.y - 0.035 - k * 0.018; z = 0.088 - k * 0.0085
            pts.append(Vector((f0.x + (0.02 if k % 2 else -0.02), y, z)))
        tube('Laces' + ft, pts, 0.0028, M['white'] if lv == 1 else M['gold'], ft)
        blob('Tongue' + ft, (f0.x, f0.y - 0.02, 0.1), (0.028, 0.03, 0.012), M['shoe'], ft, rot=(math.radians(-35), 0, 0))

def hand_wraps(M, p, lv, color_m):
    for s, wr, hb in ((1, 'wr_l', 'hand_l'), (-1, 'wr_r', 'hand_r')):
        w = p[wr]
        ob = shell('Wrap' + hb, [((w.x - s * 0.035, 0, 0), (s, 0, 0)), ((w.x + s * 0.075, 0, 0), (-s, 0, 0))], 0.004, 0.003, color_m, sub=1)
        if lv >= 2:   # gold knuckle plate
            blob('Knuckle' + hb, (w.x + s * 0.07, w.y - 0.005, w.z + 0.022), (0.022, 0.035, 0.009), M['gold'], hb)

def top_tee(M, p, accent_m, trim, long=False, lift=0.012, female=False):
    sleeve = p['wr_l'].x - 0.04 if long else p['sh_l'].x + (p['el_l'].x - p['sh_l'].x) * 0.55
    tee = shell('Tee', [((0, 0, p['hip'].z - (0.06 if long else 0.03)), (0, 0, 1)), ((sleeve, 0, 0), (-1, 0, 0)), ((-sleeve, 0, 0), (1, 0, 0)),
                        ((0, -0.08, p['neck'].z - 0.03), (0, 0.45, -1))], lift, 0.006, M['fab'], rim=M['fab'], drape=2)
    for s in (1, -1):
        boundary_ring(tee, (s * sleeve, p['sh_l'].y, p['sh_l'].z), (1, 0, 0), M['rib'] if trim is None else trim, minor=0.008, bone='lowerarm_l' if long and s > 0 else ('lowerarm_r' if long else ('upperarm_l' if s > 0 else 'upperarm_r')))
    boundary_ring(tee, (0, 0.0, p['hip'].z - (0.06 if long else 0.03)), (0, 0, 1), M['rib'], minor=0.01, bone='pelvis')
    # ribbed crew collar = trim ring fitted to the neckline edge itself
    boundary_ring(tee, (0, -0.02, p['neck'].z - 0.05), (0, 0.35, 1), M['rib'], minor=0.007, bone='spine_03', grow=0.002)
    # raglan accent panels from collar to underarm + shoulder seams
    shell('Raglan', [((0, 0, p['sh_l'].z - 0.015), (0, 0, 1)), ((sleeve - 0.01, 0, 0), (-1, 0, 0)), ((-sleeve + 0.01, 0, 0), (1, 0, 0)),
                     ((0, -0.08, p['neck'].z - 0.03), (0, 0.45, -1))], lift + 0.004, 0.003, accent_m, rim=trim or M['rib'], drape=2)
    return tee

def zip_vest(M, p, lv, accent_m):
    v = shell('Vest', [((0, 0, p['hip'].z - 0.02), (0, 0, 1)), ((p['sh_l'].x + 0.01, 0, 0), (-1, 0, 0)), ((p['sh_r'].x - 0.01, 0, 0), (1, 0, 0)),
                       ((0, -0.08, p['neck'].z + 0.0), (0, 0.35, -1))], 0.026, 0.008, accent_m, rim=M['gold'], drape=4)
    for s in (1, -1):   # arm holes trimmed in gold
        boundary_ring(v, (s * (p['sh_l'].x + 0.01), p['sh_l'].y, p['sh_l'].z - 0.06), (1, 0, 0), M['gold'], minor=0.007, bone='spine_03')
    torus('VestCollar', (0, 0.0, p['neck'].z - 0.02), 0.088, 0.016, accent_m, 'spine_03', rot=(math.radians(-10), 0, 0), scale=(1.0, 0.9, 1.0))
    # centre zip, pull, chest emblem
    tube('Zip', [Vector((0, -0.135, p['neck'].z - 0.07)), Vector((0, -0.16, p['chest'].z)), Vector((0, -0.145, p['hip'].z + 0.02))], 0.0035, M['gold'], 'spine_02')
    blob('ZipPull', (0, -0.15, p['neck'].z - 0.1), (0.008, 0.004, 0.016), M['gold'], 'spine_03')
    blob('Emblem', (0.075, -0.16, p['sh_l'].z - 0.11), (0.026, 0.006, 0.026), M['gold'], 'spine_03')
    blob('EmblemCore', (0.075, -0.166, p['sh_l'].z - 0.11), (0.013, 0.003, 0.013), M['glow'], 'spine_03')
    # shoulder pads
    for s, ua in ((1, 'upperarm_l'), (-1, 'upperarm_r')):
        u0 = bone_head(ua)
        shell('Pad' + ua, [((u0.x - s * 0.04, 0, 0), (s, 0, 0)), ((u0.x + s * 0.09, 0, 0), (-s, 0, 0)), ((0, 0, u0.z - 0.01), (0, 0, 1))], 0.03, 0.01, M['dark'], rim=M['gold'])

def armour(M, p, female=False):
    sh = p['sh_l']; neck = p['neck']; hip = p['hip'].z; ank = p['ft_l'].z + 0.05
    shell('Suit', [((0, 0, hip - 0.02), (0, 0, 1)), ((p['wr_l'].x - 0.03, 0, 0), (-1, 0, 0)), ((p['wr_r'].x + 0.03, 0, 0), (1, 0, 0)),
                   ((0, -0.06, neck.z - 0.01), (0, 0.4, -1))], 0.006, 0.004, M['fab'], sub=1)
    shell('ChestPlate', [((0, 0, sh.z - (0.25 if female else 0.27)), (0, 0, 1)), ((0, 0, neck.z - 0.05), (0, 0.25, -1)), ((0, 0.0, 0), (0, -1, 0)),
                         ((sh.x - 0.03, 0, 0), (-1, 0, 0)), ((-sh.x + 0.03, 0, 0), (1, 0, 0))], 0.03, 0.012, M['plate'], rim=M['gold'])
    shell('BackPlate', [((0, 0, sh.z - 0.3), (0, 0, 1)), ((0, 0, neck.z - 0.04), (0, -0.25, -1)), ((0, 0.04, 0), (0, 1, 0)),
                        ((sh.x - 0.04, 0, 0), (-1, 0, 0)), ((-sh.x + 0.04, 0, 0), (1, 0, 0))], 0.026, 0.01, M['dark'], rim=M['gold'])
    shell('AbPlate', [((0, 0, hip + 0.09), (0, 0, 1)), ((0, 0, sh.z - 0.29), (0, 0, -1)), ((0, -0.02, 0), (0, -1, 0)),
                      ((0.12, 0, 0), (-1, 0, 0)), ((-0.12, 0, 0), (1, 0, 0))], 0.022, 0.01, M['dark'], rim=M['gold'])
    for s, ua, la, cf in ((1, 'upperarm_l', 'lowerarm_l', 'calf_l'), (-1, 'upperarm_r', 'lowerarm_r', 'calf_r')):
        u0 = bone_head(ua); l0 = bone_head(la); w0 = bone_head('hand_l' if s > 0 else 'hand_r'); k0 = bone_head(cf)
        shell('Pauldron' + ua, [((u0.x - s * 0.06, 0, 0), (s, 0, 0)), ((u0.x + s * 0.13, 0, 0), (-s, 0, 0)), ((0, 0, u0.z - 0.035), (0, 0, 1))], 0.042, 0.014, M['plate'], rim=M['gold'])
        shell('Pauldron2' + ua, [((u0.x + s * 0.02, 0, 0), (s, 0, 0)), ((u0.x + s * 0.15, 0, 0), (-s, 0, 0)), ((0, 0, u0.z - 0.06), (0, 0, 1))], 0.028, 0.01, M['dark'], rim=M['gold'])
        shell('Gaunt' + la, [((l0.x + s * 0.09, 0, 0), (s, 0, 0)), ((w0.x - s * 0.03, 0, 0), (-s, 0, 0))], 0.022, 0.008, M['gold'], rim=M['dark'])
        shell('Greave' + cf, [((0, 0, k0.z - 0.06), (0, 0, -1)), ((0, 0, ank + 0.02), (0, 0, 1)), ((0, k0.y + 0.01, 0), (0, -1, 0))], 0.022, 0.009, M['dark'], rim=M['gold'])
        shell('Thigh' + cf, [((0, 0, k0.z + 0.08), (0, 0, 1)), ((0, 0, hip - 0.12), (0, 0, -1)), ((0, k0.y, 0), (0, -1, 0)),
                             ((k0.x + s * 0.02, 0, 0), (s, 0, 0))], 0.02, 0.008, M['plate'], rim=M['gold'])
        blob('Knee' + cf, (k0.x, k0.y - 0.075, k0.z), (0.055, 0.035, 0.065), M['gold'], cf)
        tube('Rune' + la, [l0 + Vector((s * 0.12, -0.05, 0.0)), l0 + Vector((s * 0.17, -0.055, 0.0)), l0 + Vector((s * 0.22, -0.05, 0.0))], 0.003, M['glow'], la)
        tube('Fin' + la, [l0 + Vector((s * 0.1, 0.0, 0.05)), l0 + Vector((s * 0.15, 0.0, 0.075)), l0 + Vector((s * 0.2, 0.0, 0.05))], 0.006, M['gold'], la)
    torus('Belt', (0, 0.0, hip + 0.06), 0.165 if not female else 0.15, 0.016, M['gold'], 'pelvis', scale=(1.0, 0.75, 1.0))
    blob('Buckle', (0, -0.125, hip + 0.06), (0.045, 0.014, 0.035), M['gold'], 'pelvis')
    blob('Core', (0, -0.152, sh.z - 0.08), (0.03, 0.01, 0.03), M['glow'], 'spine_03')
    torus('CoreRing', (0, -0.149, sh.z - 0.08), 0.038, 0.007, M['gold'], 'spine_03', rot=(math.radians(90), 0, 0))
    torus('Gorget', (0, 0.0, neck.z - 0.025), 0.085 if not female else 0.075, 0.016, M['gold'], 'spine_03', scale=(1.0, 0.9, 1.0))

# ------------------------------------------------------------------ characters
def blaze(lv, spec):
    p = P(); M = mats(spec['accent']); trim = M['gold'] if lv >= 2 else M['acc']
    joggers(M, p, lv, trim)
    if lv == 1:
        top_tee(M, p, M['acc'], M['rib']); hand_wraps(M, p, lv, M['wrap'])
    elif lv == 2:
        top_tee(M, p, M['acc'], M['gold']); zip_vest(M, p, lv, M['accd']); hand_wraps(M, p, lv, M['fab'])
        torus('Chain', (0, -0.04, p['neck'].z - 0.075), 0.115, 0.006, M['gold'], 'spine_03', rot=(math.radians(-40), 0, 0), scale=(1.0, 0.9, 1.0))
    else:
        armour(M, p); hand_wraps(M, p, lv, M['dark'])
    sneakers(M, p, lv, trim)

def nova(lv, spec):
    p = P(); M = mats(spec['accent']); trim = M['gold'] if lv >= 2 else M['acc']
    joggers(M, p, lv, trim, legging=True)
    if lv == 1:
        top_tee(M, p, M['acc'], M['rib'], female=True); hand_wraps(M, p, lv, M['wrap'])
    elif lv == 2:
        top_tee(M, p, M['acc'], M['gold'], female=True); zip_vest(M, p, lv, M['accd']); hand_wraps(M, p, lv, M['fab'])
    else:
        armour(M, p, female=True); hand_wraps(M, p, lv, M['dark'])
    sneakers(M, p, lv, trim)

def hijab(M, p, color_m, trim_m):
    """Sports hijab moulded to the head with a clean oval face opening, falling over the neck and upper chest."""
    hc = p['head']; nose_y = -0.11
    eye_z = hc.z + 0.105; face_c = Vector((0, nose_y, eye_z - 0.02))
    def face_cut(bm):
        kill = []
        for f in bm.faces:
            c = f.calc_center_median()
            if c.y < hc.y - 0.03 and ((c.x / 0.062) ** 2 + ((c.z - face_c.z) / 0.088) ** 2) < 1.0: kill.append(f)
        bmesh.ops.delete(bm, geom=kill, context='FACES')
    h = shell('Hijab', [((0, 0, p['neck'].z - 0.11), (0, 0, 1)), ((0.17, 0, 0), (-1, 0, 0)), ((-0.17, 0, 0), (1, 0, 0))], 0.02, 0.006, color_m, rim=trim_m, drape=4, extra=face_cut)
    # volume: gathered hair under the fabric at the back of the head, soft fold over the crown
    blob('HijabVol', (0, hc.y + 0.075, hc.z + 0.12), (0.085, 0.075, 0.08), color_m, 'Head')
    blob('HijabCrown', (0, hc.y + 0.01, hc.z + 0.175), (0.092, 0.095, 0.05), color_m, 'Head')
    # front drape falling from under the chin over the upper chest
    shell('HijabDrape', [((0, 0, p['sh_l'].z - 0.13), (0, 0, 1)), ((0, 0, p['neck'].z - 0.02), (0, 0, -1)), ((0.15, 0, 0), (-1, 0, 0)), ((-0.15, 0, 0), (1, 0, 0)),
                          ((0, -0.02, 0), (0, -1, 0))], 0.034, 0.006, color_m, rim=trim_m, drape=6)
    # under-cap band visible across the forehead
    blob('Cap', (0, hc.y - 0.07, eye_z + 0.07), (0.066, 0.026, 0.014), trim_m, 'Head')
    return h

def valor(lv, spec):
    p = P(); M = mats(spec['accent']); trim = M['gold'] if lv >= 2 else M['acc']
    joggers(M, p, lv, trim)
    hj = mat('hijab', spec.get('hijab', '#121318'), rough=0.85, sheen=0.3, weave=0.3)
    hijab(M, p, hj, trim)
    if lv == 1:
        top_tee(M, p, M['acc'], M['rib'], long=True, female=True)
    elif lv == 2:
        top_tee(M, p, M['acc'], M['gold'], long=True, female=True); zip_vest(M, p, lv, M['accd'])
    else:
        armour(M, p, female=True)
    sneakers(M, p, lv, trim)

# ---- snow leopard champion
def fur(name='fur', base='#E9ECEF', spot='#4F555E', scale=34.0, rough=0.8):
    """White-grey fur with dark rosettes (object-space Voronoi rings) and a soft fibre bump."""
    m = bpy.data.materials.get(name)
    if m: return m
    m = bpy.data.materials.new(name); m.use_nodes = True; nt = m.node_tree; n = nt.nodes; b = n['Principled BSDF']
    b.inputs['Roughness'].default_value = rough; b.inputs['Sheen Weight'].default_value = 0.8; b.inputs['Sheen Roughness'].default_value = 0.5
    tc = n.new('ShaderNodeTexCoord'); v = n.new('ShaderNodeTexVoronoi'); v.feature = 'DISTANCE_TO_EDGE'; v.inputs['Scale'].default_value = scale
    v2 = n.new('ShaderNodeTexVoronoi'); v2.inputs['Scale'].default_value = scale; v2.feature = 'F1'
    nt.links.new(tc.outputs['Generated'], v.inputs['Vector']); nt.links.new(tc.outputs['Generated'], v2.inputs['Vector'])
    r = n.new('ShaderNodeValToRGB'); r.color_ramp.interpolation = 'EASE'; e = r.color_ramp.elements
    e[0].position = 0.0; e[0].color = H.srgb(spot); e[1].position = 0.06; e[1].color = H.srgb(base)
    nt.links.new(v.outputs['Distance'], r.inputs['Fac'])
    r2 = n.new('ShaderNodeValToRGB'); e2 = r2.color_ramp.elements; e2[0].position = 0.0; e2[0].color = (1, 1, 1, 1); e2[1].position = 0.18; e2[1].color = (1, 1, 1, 1)
    el = r2.color_ramp.elements.new(0.32); el.color = H.srgb('#C9CDD3')     # soft grey centre in each rosette
    nt.links.new(v2.outputs['Distance'], r2.inputs['Fac'])
    mul = n.new('ShaderNodeMix'); mul.data_type = 'RGBA'; mul.blend_type = 'MULTIPLY'; mul.inputs[0].default_value = 1
    nt.links.new(r.outputs['Color'], mul.inputs[6]); nt.links.new(r2.outputs['Color'], mul.inputs[7]); nt.links.new(mul.outputs[2], b.inputs['Base Color'])
    nz = n.new('ShaderNodeTexNoise'); nz.inputs['Scale'].default_value = 900; nz.inputs['Detail'].default_value = 10
    bm = n.new('ShaderNodeBump'); bm.inputs['Strength'].default_value = 0.35; bm.inputs['Distance'].default_value = 0.002
    nt.links.new(nz.outputs['Fac'], bm.inputs['Height']); nt.links.new(bm.outputs['Normal'], b.inputs['Normal'])
    return m

def leopard_head(M, p, furm, helm=False):
    """Snow-leopard head built over the human head: moulded skull shell, muzzle, nose, ears, eyes, whisker pads."""
    hc = p['head']; top = hc.z + 0.2
    head_m = M['plate'] if helm else furm
    skull = shell('LeoSkull', [((0, 0, p['neck'].z - 0.01), (0, 0, 1)), ((0.13, 0, 0), (-1, 0, 0)), ((-0.13, 0, 0), (1, 0, 0))],
                  0.03 if not helm else 0.024, 0.008, head_m, rim=M['gold'] if helm else furm, drape=10)
    mz = M['dark'] if helm else mat('muzzle', '#F4F4F2', rough=0.85, sheen=0.6)
    blob('Muzzle', (0, hc.y - 0.135, hc.z + 0.05), (0.06, 0.05, 0.045), mz, 'Head')
    for s in (1, -1):
        blob('Pad%d' % s, (s * 0.032, hc.y - 0.16, hc.z + 0.045), (0.032, 0.03, 0.026), mz, 'Head')
        # ears: rounded, set wide on the skull, dark backs
        blob('Ear%d' % s, (s * 0.085, hc.y + 0.01, top - 0.005), (0.04, 0.018, 0.042), M['gold'] if helm else furm, 'Head', rot=(0, math.radians(s * -20), 0))
        blob('EarIn%d' % s, (s * 0.085, hc.y - 0.006, top - 0.008), (0.026, 0.008, 0.028), M['dark'] if helm else mat('earin', '#E7B9C0', rough=0.8), 'Head', rot=(0, math.radians(s * -20), 0))
        # eyes: almond, ice blue, glowing when armoured
        eye_m = mat('leoeye', '#7FE3FF', emit=6.0) if helm else mat('leoeye_g', '#8FD3F0', rough=0.1, coat=1.0)
        blob('LeoEye%d' % s, (s * 0.04, hc.y - 0.122, hc.z + 0.115), (0.022, 0.009, 0.013), eye_m, 'Head', rot=(0, math.radians(s * 12), math.radians(s * -10)))
        blob('Pupil%d' % s, (s * 0.04, hc.y - 0.13, hc.z + 0.115), (0.005, 0.004, 0.011), mat('pupil', '#06080A', rough=0.2), 'Head')
        # brow ridges and dark facial lines
        tube('Brow%d' % s, [Vector((s * 0.015, hc.y - 0.13, hc.z + 0.135)), Vector((s * 0.045, hc.y - 0.125, hc.z + 0.142)), Vector((s * 0.07, hc.y - 0.105, hc.z + 0.13))], 0.006, M['dark'] if not helm else M['gold'], 'Head')
        if not helm:
            tube('Tear%d' % s, [Vector((s * 0.025, hc.y - 0.13, hc.z + 0.1)), Vector((s * 0.03, hc.y - 0.15, hc.z + 0.07)), Vector((s * 0.04, hc.y - 0.165, hc.z + 0.045))], 0.003, mat('furline', '#3E434B'), 'Head')
            for k in range(3):   # whiskers
                tube('Whisk%d%d' % (s, k), [Vector((s * 0.05, hc.y - 0.17, hc.z + 0.05 - k * 0.008)), Vector((s * 0.1, hc.y - 0.17, hc.z + 0.055 - k * 0.015)), Vector((s * 0.14, hc.y - 0.15, hc.z + 0.06 - k * 0.022))], 0.0007, M['white'], 'Head')
    blob('Nose', (0, hc.y - 0.183, hc.z + 0.07), (0.016, 0.01, 0.011), mat('leonose', '#C8848E', rough=0.35) if not helm else M['gold'], 'Head')
    if helm:   # crest + glowing visor line
        tube('Crest', [Vector((0, hc.y - 0.13, hc.z + 0.16)), Vector((0, hc.y - 0.05, top + 0.03)), Vector((0, hc.y + 0.09, hc.z + 0.12))], 0.008, M['gold'], 'Head')
        tube('Visor', [Vector((-0.07, hc.y - 0.11, hc.z + 0.1)), Vector((0, hc.y - 0.145, hc.z + 0.098)), Vector((0.07, hc.y - 0.11, hc.z + 0.1))], 0.0035, mat('leoeye', '#7FE3FF', emit=6.0), 'Head')
    return skull

def claws(M, p, color_m):
    for s, wr, hb in ((1, 'wr_l', 'hand_l'), (-1, 'wr_r', 'hand_r')):
        w = p[wr]
        for k in range(4):
            y = w.y - 0.03 + k * 0.02
            bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=0.006, radius2=0.0, depth=0.03, location=(w.x + s * 0.105, y, w.z + 0.012), rotation=(0, math.radians(s * 90 + s * 15), 0))
            c = bpy.context.active_object; c.name = 'Claw%d%d' % (s, k); c.data.materials.append(color_m); O._ride(c, hb)

def tail(M, furm, p):
    """Long thick tail sweeping down and out behind the pelvis (rides the pelvis; gentle curve)."""
    h = p['hip']
    pts = [Vector((0, h.y + 0.1, h.z - 0.02)), Vector((0.05, h.y + 0.3, h.z - 0.25)), Vector((0.18, h.y + 0.42, h.z - 0.55)), Vector((0.34, h.y + 0.36, h.z - 0.72)), Vector((0.42, h.y + 0.22, h.z - 0.66))]
    t = tube('Tail', pts, 0.045, furm, 'pelvis')
    t.data.bevel_resolution = 6
    taper = bpy.data.curves.new('taper', 'CURVE'); taper.dimensions = '2D'; sp = taper.splines.new('POLY'); sp.points.add(2)
    for pt, xy in zip(sp.points, ((0, 0.8), (0.6, 1.0), (1.0, 0.75))): pt.co = (xy[0], xy[1], 0, 1)
    to = bpy.data.objects.new('taperobj', taper); H.S.collection.objects.link(to); to.hide_render = True; t.data.taper_object = to
    return t

def leopard_helm(M, p):
    """Sleek snow-leopard battle helm: smooth gunmetal shell, swept ear fins, glowing visor slit, rosette-engraved cheek guards."""
    hc = p['head']; top = hc.z + 0.2
    gun = mat('gunmetal', '#2A2E36', rough=0.25, metal=0.9, coat=1.0)
    engr = fur('engrave', base='#C9CED6', spot='#2A2E36', scale=26.0, rough=0.3)
    engr.node_tree.nodes['Principled BSDF'].inputs['Metallic'].default_value = 0.9
    helm = shell('Helm', [((0, 0, p['neck'].z + 0.005), (0, 0, 1)), ((0.13, 0, 0), (-1, 0, 0)), ((-0.13, 0, 0), (1, 0, 0))], 0.026, 0.01, gun, rim=M['gold'], drape=14)
    # cheek / jaw guards with rosette engraving, following the face sides
    shell('Cheeks', [((0, 0, p['neck'].z + 0.01), (0, 0, 1)), ((0, 0, hc.z + 0.1), (0, 0, -1)), ((0, hc.y - 0.01, 0), (0, -1, 0)),
                     ((0.13, 0, 0), (-1, 0, 0)), ((-0.13, 0, 0), (1, 0, 0))], 0.036, 0.008, engr, rim=M['gold'], drape=10)
    visor = mat('visor', '#7FE3FF', emit=7.0)
    tube('Visor', [Vector((-0.072, hc.y - 0.09, hc.z + 0.112)), Vector((-0.035, hc.y - 0.132, hc.z + 0.104)), Vector((0, hc.y - 0.14, hc.z + 0.1)),
                   Vector((0.035, hc.y - 0.132, hc.z + 0.104)), Vector((0.072, hc.y - 0.09, hc.z + 0.112))], 0.006, visor, 'Head')
    tube('Crest', [Vector((0, hc.y - 0.135, hc.z + 0.15)), Vector((0, hc.y - 0.06, top + 0.035)), Vector((0, hc.y + 0.08, top + 0.0)), Vector((0, hc.y + 0.13, hc.z + 0.1))], 0.007, M['gold'], 'Head')
    for s in (1, -1):   # swept ear fins
        bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=0.035, radius2=0.0, depth=0.085, location=(s * 0.075, hc.y + 0.02, top - 0.0), rotation=(math.radians(-25), math.radians(s * -28), math.radians(45)))
        c = bpy.context.active_object; c.name = 'EarFin%d' % s; c.scale = (1.0, 0.35, 1.0)
        bv = c.modifiers.new('b', 'BEVEL'); bv.width = 0.004; bv.segments = 2; c.data.materials.append(gun); O._ride(c, 'Head')
        bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=0.022, radius2=0.0, depth=0.06, location=(s * 0.075, hc.y + 0.008, top - 0.004), rotation=(math.radians(-25), math.radians(s * -28), math.radians(45)))
        c2 = bpy.context.active_object; c2.name = 'EarFinIn%d' % s; c2.scale = (1.0, 0.35, 1.0); c2.data.materials.append(visor); O._ride(c2, 'Head')
    return helm

def frost_armour(lv, spec):
    """Champion in snow-leopard armour: sculpted helm, pelt mantle, claw gauntlets — on the athlete body."""
    p = P(); M = mats(spec['accent']); furm = fur()
    joggers(M, p, 3, M['gold'])
    armour(M, p)
    leopard_helm(M, p)
    # pelt mantle over the shoulders and upper back
    shell('Mantle', [((0, 0, p['sh_l'].z - 0.13), (0, 0, 1)), ((p['sh_l'].x + 0.08, 0, 0), (-1, 0, 0)), ((p['sh_r'].x - 0.08, 0, 0), (1, 0, 0)),
                     ((0, 0.0, 0), (0, 1, 0))], 0.05, 0.016, furm, rim=furm, drape=6)
    claws(M, p, M['gold']); sneakers(M, p, 3, M['gold'])

def frost_hybrid(lv, spec):
    """Full snow-leopard champion: leopard head, rosette fur skin, tail, claws; tiered kit on top."""
    p = P(); M = mats(spec['accent']); furm = fur()
    H.BODY.data.materials.clear(); H.BODY.data.materials.append(furm)
    for o in list(H.S.objects):     # no human hair/brows/eyes on the hybrid
        if o.type == 'MESH' and o is not H.BODY and o.parent == H.RIG and o.parent_type != 'BONE' and not o.name.startswith(('Joggers',)):
            if o.name.startswith(('Eyebrow', 'Eyes', 'Face')): o.hide_render = True
    leopard_head(M, p, furm, helm=False)
    tail(M, furm, p); claws(M, p, mat('clawbone', '#EDE6D8', rough=0.4))
    trim = M['gold'] if lv >= 2 else M['acc']
    shell('Shorts', [((0, 0, p['hip'].z + 0.07), (0, 0, -1)), ((0, 0, p['kn_l'].z + 0.12), (0, 0, 1))], 0.02, 0.006, M['fab2'], rim=trim, drape=3)
    if lv == 2:
        zip_vest(M, p, lv, M['accd'])
    elif lv == 3:
        armour(M, p)
        for o in H.S.objects:     # keep the fur arms/legs visible: drop the undersuit
            if o.name == 'Suit': o.hide_render = True

BUILD = dict(blaze=blaze, nova=nova, valor=valor, frost_armour=frost_armour, frost_hybrid=frost_hybrid)
