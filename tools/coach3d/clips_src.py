"""Mocap sources: Quaternius UAL (CC0) and the coaches' own Meshy clips, mapped to the canonical skeleton."""
import bpy, os

UAL = {'Hips': 'pelvis', 'Spine': 'spine_01', 'Chest': 'spine_02', 'UpperChest': 'spine_03', 'Neck': 'neck_01', 'Head': 'Head'}
for s, x in (('L', 'l'), ('R', 'r')):
    UAL.update({f'Shoulder.{s}': f'clavicle_{x}', f'UpperArm.{s}': f'upperarm_{x}', f'ForeArm.{s}': f'lowerarm_{x}', f'Hand.{s}': f'hand_{x}',
                f'Fingers1.{s}': f'middle_01_{x}', f'Fingers2.{s}': f'middle_02_{x}', f'Fingers3.{s}': f'middle_03_{x}',
                f'Thumb1.{s}': f'thumb_01_{x}', f'Thumb2.{s}': f'thumb_02_{x}', f'Thumb3.{s}': f'thumb_03_{x}',
                f'UpLeg.{s}': f'thigh_{x}', f'Leg.{s}': f'calf_{x}', f'Foot.{s}': f'foot_{x}', f'Toe.{s}': f'ball_{x}'})


def import_source(path):
    """Imports a rig + its actions; returns (armature, {short_name: action}). Meshes are removed."""
    before_o = set(bpy.data.objects); before_a = set(bpy.data.actions)
    bpy.ops.import_scene.gltf(filepath=path)
    new_o = [o for o in bpy.data.objects if o not in before_o]
    arm = next(o for o in new_o if o.type == 'ARMATURE')
    for o in new_o:
        if o.type == 'MESH': bpy.data.objects.remove(o, do_unlink=True)
    acts = {}
    for a in bpy.data.actions:
        if a in before_a: continue
        short = a.name.replace('_target_character', '').replace('_Armature', '').split('|')[-1].split('.')[0]
        acts[short] = a
    arm.animation_data_create()
    return arm, acts


def remove(arm):
    bpy.data.objects.remove(arm, do_unlink=True)
