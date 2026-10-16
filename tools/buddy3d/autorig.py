"""
R23 Buddies (champions) — auto-rig an unrigged Meshy A-pose model onto the canonical coach skeleton.

  python autorig.py <model.glb> <landmarks.json> <name> <out_rigged.glb> [preview.png]

landmarks.json: {name: {"sh_l": [x, z, vis], ...}} — 2D joints from MediaPipe Pose on a front orthographic render,
already converted to world x/z (see detect step in tools/buddy3d/README.md). Depth (y) comes from the mesh: the middle
of the body cross-section at that joint. Hands, feet, spine, head and the tail come from mesh geometry.

The canonical skeleton is the coaches' one (tools/coach3d/rig.py), so every coach move and mocap clip plays on every
champion. Animal champions get an extra 4-bone tail (Tail1..Tail4, parented to Hips).
"""
import sys, os, json, math, bpy
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'coach3d'))
from mathutils import Vector
from mathutils.kdtree import KDTree
import rig

SIDES = rig.SIDES


def _import(path):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=path)
    meshes = [o for o in bpy.data.objects if o.type == 'MESH']
    mesh = max(meshes, key=lambda o: len(o.data.vertices))
    for o in list(bpy.data.objects):
        if o is not mesh: bpy.data.objects.remove(o, do_unlink=True)
    # bake the import transform into the vertices so world == local
    bpy.context.view_layer.objects.active = mesh; mesh.select_set(True)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    # Meshy splits vertices along UV seams; welded, the skin can't tear open at seams (UVs live on loops, so they survive)
    bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.mesh.remove_doubles(threshold=0.0005)
    bpy.ops.object.mode_set(mode='OBJECT')
    return mesh


class Body:
    """Vertex cloud helpers for picking joint depth and extremities."""
    def __init__(self, mesh):
        self.P = [v.co.copy() for v in mesh.data.vertices]
        self.kd = KDTree(len(self.P))
        for i, p in enumerate(self.P): self.kd.insert(p, i)
        self.kd.balance()
        zs = [p.z for p in self.P]; self.z0, self.z1 = min(zs), max(zs); self.H = self.z1 - self.z0

    def box(self, x, z, rx, rz, xside=None):
        return [p for p in self.P if abs(p.x - x) < rx and abs(p.z - z) < rz and (xside is None or p.x * xside > 0)]

    def mid_y(self, x, z, r=0.035, xside=None, bias=0.0):
        pts = self.box(x, z, r, r, xside)
        if len(pts) < 6: pts = self.box(x, z, r * 2, r * 2, xside)
        if not pts: return 0.0
        y0, y1 = min(p.y for p in pts), max(p.y for p in pts)
        return y0 + (y1 - y0) * (0.5 + bias)

    def half_width(self, z, band=0.015):
        pts = [p for p in self.P if abs(p.z - z) < band]
        return max(abs(p.x) for p in pts) if pts else 0.0


def joints(mesh, L):
    b = Body(mesh); H = b.H
    g = lambda k: Vector((L[k][0], L[k][1]))          # (x, z)
    J = {}
    side_key = {'L': 'l', 'R': 'r'}                    # MediaPipe LEFT = person's left = +x here (faces -Y)
    for s in SIDES:
        k = side_key[s]; sx = 1 if s == 'L' else -1
        hip, kn, an, el, wr, sh = g('hip_' + k), g('kn_' + k), g('an_' + k), g('el_' + k), g('wr_' + k), g('sh_' + k)
        # legs: MediaPipe hips sit a little low/outside the ball joint
        hipj = Vector((hip.x * 0.9, hip.y + 0.012 * H))
        J[f'UpLeg.{s}'] = Vector((hipj.x, b.mid_y(hipj.x, hipj.y, xside=sx), hipj.y))
        J[f'Leg.{s}'] = Vector((kn.x, b.mid_y(kn.x, kn.y, xside=sx, bias=-0.1), kn.y))
        az = max(an.y, b.z0 + 0.045 * H)
        J[f'Foot.{s}'] = Vector((an.x, b.mid_y(an.x, az, xside=sx), az))
        # toes: the front-most foot vertex near the floor
        foot = [p for p in b.P if p.z < b.z0 + 0.06 * H and p.x * sx > 0 and abs(p.x - an.x) < 0.12]
        tip = min(foot, key=lambda p: p.y) if foot else J[f'Foot.{s}'] + Vector((0, -0.15, 0))
        heel_y = max(p.y for p in foot) if foot else J[f'Foot.{s}'].y + 0.05
        J[f'ToeEnd.{s}'] = Vector((tip.x, tip.y + 0.01, b.z0 + 0.025 * H))
        J[f'Toe.{s}'] = Vector((tip.x * 0.6 + an.x * 0.4, heel_y + (tip.y - heel_y) * 0.72, b.z0 + 0.025 * H))
        # arms: push the shoulder joint out to the deltoid (MediaPipe sits on the chest edge on bulky bodies)
        hw = b.half_width(sh.y - 0.01 * H)
        shx = sx * max(abs(sh.x), 0.80 * hw) if hw > 0 else sh.x
        J[f'UpperArm.{s}'] = Vector((shx, b.mid_y(shx, sh.y, xside=sx), sh.y))
        J[f'ForeArm.{s}'] = Vector((el.x, b.mid_y(el.x, el.y, xside=sx, bias=0.1), el.y))
        J[f'Hand.{s}'] = Vector((wr.x, b.mid_y(wr.x, wr.y, r=0.03, xside=sx), wr.y))
        # fingertips: furthest vertex past the wrist along the forearm direction
        d = (J[f'Hand.{s}'] - J[f'ForeArm.{s}']).normalized()
        cand = [p for p in b.P if (p - J[f'Hand.{s}']).dot(d) > 0 and ((p - J[f'Hand.{s}']) - d * (p - J[f'Hand.{s}']).dot(d)).length < 0.09
                and p.x * sx > 0]
        tipf = max(cand, key=lambda p: (p - J[f'Hand.{s}']).dot(d)) if cand else J[f'Hand.{s}'] + d * 0.18
        hl = (tipf - J[f'Hand.{s}']).dot(d)
        base = J[f'Hand.{s}']
        J[f'Fingers1.{s}'] = base + d * hl * 0.48
        J[f'Fingers2.{s}'] = base + d * hl * 0.66
        J[f'Fingers3.{s}'] = base + d * hl * 0.82
        J[f'FingersEnd.{s}'] = base + d * hl
        for n in ('Fingers1', 'Fingers2', 'Fingers3', 'FingersEnd'):
            p = J[f'{n}.{s}']; J[f'{n}.{s}'] = Vector((p.x, b.mid_y(p.x, p.z, r=0.025, xside=sx), p.z))
        rig._synth_thumb(J, s)
    # spine: hips centre up to the neck base between the shoulders
    hips = (J['UpLeg.L'] + J['UpLeg.R']) / 2
    shz = (L['sh_l'][1] + L['sh_r'][1]) / 2
    neck_z = shz + 0.022 * H
    nose_z = L['nose'][1]
    head_z = min(nose_z - 0.012 * H, neck_z + 0.07 * H)
    top_z = min(b.z1, head_z + 0.135 * H)               # horns/ears can stick up: cap the skull height
    hz = hips.z + 0.02 * H
    def cy(z, bias=0.0): return b.mid_y(0.0, z, r=0.05, bias=bias)
    J['Hips'] = Vector((0.0, cy(hz), hz))
    for n, f in (('Spine', 0.24), ('Chest', 0.50), ('UpperChest', 0.76)):
        z = hz + (neck_z - hz) * f; J[n] = Vector((0.0, cy(z, 0.05), z))
    J['Neck'] = Vector((0.0, cy(neck_z, 0.1), neck_z))
    J['Head'] = Vector((0.0, cy(head_z, 0.1), head_z))
    J['HeadTop'] = Vector((0.0, J['Head'].y, top_z))
    for s in SIDES:
        sx = 1 if s == 'L' else -1
        ua = J[f'UpperArm.{s}']
        J[f'Shoulder.{s}'] = Vector((sx * max(0.03, abs(ua.x) * 0.22), (J['UpperChest'].y + ua.y) / 2, ua.z + 0.025 * H))
    return J, b


def find_tail(b, J):
    """Tail = vertices behind the body between the legs / below the hips, near x=0. Returns 5 points root->tip or None."""
    hz = J['Hips'].z; crotch = min(J['UpLeg.L'].z, J['UpLeg.R'].z) - 0.05 * b.H
    legx = min(abs(J['Leg.L'].x), abs(J['Leg.R'].x)) * 0.55
    back = max(p.y for p in b.box(0.0, hz, 0.12, 0.03)) if b.box(0.0, hz, 0.12, 0.03) else 0.1
    cand = [p for p in b.P if abs(p.x) < max(0.05, legx) and p.z < crotch and p.y > J['Hips'].y]
    cand += [p for p in b.P if abs(p.x) < 0.09 and crotch <= p.z < hz and p.y > back - 0.01]
    if len(cand) < 150: return None
    # order along the tail: from the root (behind the hips) by distance through the cloud
    root = Vector((0.0, back - 0.03, hz - 0.06 * b.H))
    tip = max(cand, key=lambda p: (p - root).length)
    if (tip - root).length < 0.25: return None
    # polyline through slab centroids between root and tip
    pts = [root]
    axis = tip - root
    for f in (0.25, 0.5, 0.75):
        c = root + axis * f
        near = [p for p in cand if abs((p - root).dot(axis.normalized()) - axis.length * f) < 0.05]
        pts.append(sum(near, Vector()) / len(near) if near else c)
    pts.append(tip)
    return pts


def build(mesh, J, tail):
    arm = rig.build_armature(J, 'buddy')
    if tail:
        bpy.context.view_layer.objects.active = arm
        bpy.ops.object.mode_set(mode='EDIT')
        eb = arm.data.edit_bones
        prev = 'Hips'
        for i in range(4):
            b = eb.new(f'Tail{i + 1}'); b.head = tail[i]; b.tail = tail[i + 1]
            b.align_roll(Vector((1, 0, 0))); b.parent = eb[prev]; b.use_connect = i > 0; prev = b.name
        bpy.ops.object.mode_set(mode='OBJECT')
    # automatic (bone-heat) weights
    for o in bpy.context.view_layer.objects: o.select_set(False)
    mesh.select_set(True); arm.select_set(True); bpy.context.view_layer.objects.active = arm
    bpy.ops.object.parent_set(type='ARMATURE_AUTO')
    # soften weight borders a little (bone heat can leave hard steps on thin cloth)
    bpy.context.view_layer.objects.active = mesh
    for o in bpy.context.view_layer.objects: o.select_set(o is mesh)
    bpy.ops.object.mode_set(mode='WEIGHT_PAINT')
    bpy.ops.object.vertex_group_smooth(group_select_mode='ALL', factor=0.5, repeat=2, expand=0.0)
    bpy.ops.object.mode_set(mode='OBJECT')
    nz = sum(1 for v in mesh.data.vertices if not any(g.weight > 0.01 for g in v.groups))
    return arm, nz / max(1, len(mesh.data.vertices))


def nearest_bone_fallback(mesh, arm, only_unweighted=True):
    """Bone heat can fail on non-manifold parts (separate hair/shoe shells): give those vertices the nearest bone."""
    segs = [(b.name, b.head_local.copy(), b.tail_local.copy()) for b in arm.data.bones if not b.name.startswith('Fingers') or True]
    groups = {g.name: g for g in mesh.vertex_groups}
    fixed = 0
    for v in mesh.data.vertices:
        if only_unweighted and any(g.weight > 0.01 for g in v.groups): continue
        p = v.co
        best, bd = None, 1e9
        for n, h, t in segs:
            ab = t - h; u = max(0.0, min(1.0, (p - h).dot(ab) / max(1e-8, ab.dot(ab))))
            d = (h + ab * u - p).length
            if d < bd: best, bd = n, d
        g = groups.get(best) or mesh.vertex_groups.new(name=best); groups[best] = g
        g.add([v.index], 1.0, 'REPLACE'); fixed += 1
    return fixed


def main():
    src, lmk, name, out = sys.argv[1:5]
    prev = sys.argv[5] if len(sys.argv) > 5 else None
    L = json.load(open(lmk))[name]
    animal = '--animal' in sys.argv
    mesh = _import(src)
    J, b = joints(mesh, L)
    tail = find_tail(b, J) if animal else None
    arm, miss = build(mesh, J, tail)
    fixed = nearest_bone_fallback(mesh, arm) if miss > 0.0005 else 0
    arm['joints'] = {k: list(v) for k, v in J.items()}
    arm['tail'] = int(bool(tail))
    print(f'RIG {name} tail={bool(tail)} unweighted={miss:.4f} fixed={fixed} verts={len(mesh.data.vertices)}')
    for o in bpy.context.view_layer.objects: o.select_set(o in (mesh, arm))
    bpy.ops.export_scene.gltf(filepath=out, export_format='GLB', use_selection=True, export_skins=True, export_animations=False,
                              export_image_format='AUTO', export_yup=True)
    json.dump({k: list(v) for k, v in J.items()} | {'_tail': [list(p) for p in tail] if tail else None},
              open(os.path.splitext(out)[0] + '.joints.json', 'w'))
    print('RIG_DONE', out)


if __name__ == '__main__':
    main()
