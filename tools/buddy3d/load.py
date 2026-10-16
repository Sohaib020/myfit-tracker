"""Load an auto-rigged champion GLB (canonical bone names) ready for the coach pipeline: feet on z=0, centred, joints
stored on the armature like rig.load() does for the coaches."""
import bpy, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'coach3d'))
import rig


def load_rigged(path):
    rig._clear()
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path, bone_heuristic='BLENDER')
    new = [o for o in bpy.data.objects if o not in before]
    arm = next(o for o in new if o.type == 'ARMATURE')
    mesh = max((o for o in new if o.type == 'MESH'), key=lambda o: len(o.data.vertices))
    for o in new:
        if o not in (arm, mesh): bpy.data.objects.remove(o, do_unlink=True)
    for pb in arm.pose.bones: pb.rotation_mode = 'QUATERNION'
    mw = arm.matrix_world
    J = {b.name: list(mw @ b.head_local) for b in arm.data.bones}
    for b in arm.data.bones:
        if b.name == 'Head': J['HeadTop'] = list(mw @ b.tail_local)
        if b.name.startswith('Toe.'): J['ToeEnd.' + b.name[-1]] = list(mw @ b.tail_local)
        if b.name.startswith('Fingers3.'): J['FingersEnd.' + b.name[-1]] = list(mw @ b.tail_local)
    arm['joints'] = J
    return rig.normalize(arm, mesh)
