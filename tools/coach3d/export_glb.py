"""
Export one coach as a single real-time GLB for the in-app 3D viewer (Filament).
  python export_glb.py <glb_dir> <ual.glb> <who> <out.glb> [max_tex=1024] [max_tris=60000]

 - Same canonical skeleton + hand-keyed moves as the clip renders, one glTF animation per clip (name = clip name).
 - Muscle bulk (per-bone lateral scale with inherit_scale NONE) can't be expressed in glTF, so it is baked into the
   rest mesh; IK contacts (planted feet) are baked to plain rotation keys.
 - Clips that need props (plate / bottle / tablet / dumbbell) and floor moves are left out: the viewer is for
   idle, talk and tap reactions.
 - Textures capped at max_tex, written as JPEG; mesh decimated above max_tris.
"""
import sys, os, bpy
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rig, clips

glb, ual, who, out = sys.argv[1:5]
max_tex = int(sys.argv[5]) if len(sys.argv) > 5 else 1024
max_tris = int(sys.argv[6]) if len(sys.argv) > 6 else 60000

REALTIME = {
    'trainer': ['idle', 'talk', 'wave', 'thumbsup', 'cheer', 'fistpump', 'victory', 'dance'],
    'nutri': ['idle', 'talk', 'wave', 'thumbsup', 'cheer', 'fistpump', 'think', 'dance'],
}

arm, body = rig.load(who, glb)
rig.bulk(arm, who)
scene = bpy.context.scene

# ---- bake the bulk into the rest mesh, then drop it from the skeleton
for pb in arm.pose.bones:
    pb.rotation_mode = 'QUATERNION'; pb.location = (0, 0, 0)
bpy.context.view_layer.update()
dg = bpy.context.evaluated_depsgraph_get()
ev = body.evaluated_get(dg).to_mesh()
mw_inv = body.matrix_world.inverted()  # evaluated coords are in object space already
coords = [v.co.copy() for v in ev.vertices]
body.evaluated_get(dg).to_mesh_clear()
if body.data.shape_keys is None:
    for v, co in zip(body.data.vertices, coords): v.co = co
for pb in arm.pose.bones: pb.scale = (1, 1, 1)
for b in arm.data.bones: b.inherit_scale = 'FULL'
bpy.context.view_layer.update()

# ---- decimate heavy meshes (phones: keep the skinned mesh light)
tris = sum(len(p.vertices) - 2 for p in body.data.polygons)
if tris > max_tris:
    mod_arm = [m for m in body.modifiers if m.type == 'ARMATURE']
    d = body.modifiers.new('dec', 'DECIMATE'); d.ratio = max_tris / tris; d.use_collapse_triangulate = True
    # decimate must run before the armature modifier
    while body.modifiers.find('dec') > 0:
        with bpy.context.temp_override(object=body): bpy.ops.object.modifier_move_up(modifier='dec')
    with bpy.context.temp_override(object=body, active_object=body): bpy.ops.object.modifier_apply(modifier='dec')
    print('decimated', tris, '->', sum(len(p.vertices) - 2 for p in body.data.polygons))

# ---- textures
for img in bpy.data.images:
    if img.size[0] > max_tex or img.size[1] > max_tex:
        k = max_tex / max(img.size[0], img.size[1])
        img.scale(max(1, int(img.size[0] * k)), max(1, int(img.size[1] * k)))
for m in bpy.data.materials:
    if m.node_tree:
        for n in m.node_tree.nodes:
            if n.type == 'BSDF_PRINCIPLED':
                n.inputs['Metallic'].default_value = 0.0

# ---- one baked action per clip
keep = {arm.name, body.name}
kind = 'nutri' if who.startswith('nutri') else 'trainer'
made = []
for name in REALTIME[kind]:
    c = clips.build(who, name, arm, glb, ual)
    f1 = c.frames - (1 if c.loop else 0)
    scene.frame_start, scene.frame_end = 0, f1
    for o in bpy.context.view_layer.objects: o.select_set(False)
    arm.select_set(True); bpy.context.view_layer.objects.active = arm
    src = arm.animation_data.action
    with bpy.context.temp_override(active_object=arm, object=arm, selected_objects=[arm], selected_editable_objects=[arm]):
        bpy.ops.object.mode_set(mode='POSE')
        bpy.ops.pose.select_all(action='SELECT')
        bpy.ops.nla.bake(frame_start=0, frame_end=f1, only_selected=False, visual_keying=True, clear_constraints=True,
                         use_current_action=False, bake_types={'POSE'})
        bpy.ops.object.mode_set(mode='OBJECT')
    act = arm.animation_data.action
    if src is not None and src != act: bpy.data.actions.remove(src)
    act.name = name; act.use_fake_user = True
    # remove props / IK helpers this clip created
    for o in list(bpy.data.objects):
        if o.name not in keep: bpy.data.objects.remove(o, do_unlink=True)
    # put the armature back in rest for the next clip
    arm.animation_data.action = None
    for pb in arm.pose.bones: pb.rotation_quaternion = (1, 0, 0, 0); pb.location = (0, 0, 0); pb.scale = (1, 1, 1)
    made.append((name, f1 + 1, c.loop))
    print('baked', name, f1 + 1)

# drop any stray actions (imported mocap etc.)
names = {m[0] for m in made}
for a in list(bpy.data.actions):
    if a.name not in names: bpy.data.actions.remove(a)
arm.animation_data.action = bpy.data.actions['idle']
scene.render.fps = 24

for o in bpy.context.view_layer.objects: o.select_set(o.name in keep)
bpy.ops.export_scene.gltf(
    filepath=out, export_format='GLB', use_selection=True,
    export_animations=True, export_animation_mode='ACTIONS', export_force_sampling=True,
    export_optimize_animation_size=True, export_anim_single_armature=True,
    export_def_bones=True, export_skins=True, export_all_influences=False,
    export_image_format='JPEG', export_jpeg_quality=85, export_apply=False,
    export_yup=True, export_cameras=False, export_lights=False,
)
with open(os.path.splitext(out)[0] + '.txt', 'w') as fh:
    for n, f, loop in made: fh.write(f'{n} {f} {"loop" if loop else "once"}\n')
print('EXPORT_DONE', out, os.path.getsize(out))
