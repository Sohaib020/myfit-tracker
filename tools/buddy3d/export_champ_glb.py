"""Real-time GLB for one champion: rigged mesh + every move of its tier as glTF animations (Filament viewer).
  python export_champ_glb.py <rigged.glb> <motion.json.gz> <tier> <out.glb>"""
import sys, os, bpy
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'coach3d')); sys.path.insert(0, HERE)
import load, motion
glb, mpack, tier, out = sys.argv[1:5]
pack = motion.load_pack(mpack)
arm, body = load.load_rigged(glb)
for a in list(bpy.data.actions): bpy.data.actions.remove(a)
made = []
for name in motion.moves_for(tier):
    act, n, loop = motion.apply(arm, pack, name)
    act.name = name; act.use_fake_user = True; made.append((name, n + (0 if loop else 1), loop))
    arm.animation_data.action = None
    for pb in arm.pose.bones: pb.rotation_quaternion = (1, 0, 0, 0); pb.location = (0, 0, 0)
arm.animation_data.action = bpy.data.actions['idle']
bpy.context.scene.render.fps = 24
for o in bpy.context.view_layer.objects: o.select_set(o in (arm, body))
bpy.ops.export_scene.gltf(filepath=out, export_format='GLB', use_selection=True, export_animations=True,
    export_animation_mode='ACTIONS', export_force_sampling=True, export_optimize_animation_size=True,
    export_anim_single_armature=True, export_skins=True, export_all_influences=False, export_image_format='JPEG',
    export_jpeg_quality=85, export_yup=True, export_cameras=False, export_lights=False)
open(os.path.splitext(out)[0] + '.txt', 'w').write(''.join(f'{n} {f} {"loop" if l else "once"}\n' for n, f, l in made))
print('EXPORT_DONE', out, os.path.getsize(out), len(made))
