"""Render a grid of single poses for one coach (pose-authoring aid). python3 posetest.py who out.jpg  (poses from POSES)"""
import sys, bpy, math
sys.path.insert(0, __file__.rsplit('/', 1)[0])
import rig, anim, studio
from mathutils import Vector


def render_poses(who, assets, poses, out, res=260, yaw=-25, pitch=6, dist=4.6, target=(0, 0, 0.9), track=None):
    arm, body = rig.load(who, assets)
    studio.setup(res=res, samples=8)
    studio.camera(target=target, dist=dist, yaw=yaw, pitch=pitch)
    files = []
    for i, (name, pose, hips, hr) in enumerate(poses):
        clip = anim.Clip('t', 2, loop=False); clip.breath = 0; clip.noise = 0
        clip.key(0, pose, hips, hips_rot=hr); clip.key(1, pose, hips, hips_rot=hr)
        anim.bake(arm, clip)
        bpy.context.scene.frame_set(0)
        if track:
            pb = arm.pose.bones[track]; tp = arm.matrix_world @ pb.tail
            studio.camera(target=tuple(tp), dist=dist, yaw=yaw, pitch=pitch)
        f = f'/tmp/claude-0/c3w/pt_{i:02d}.png'; studio.render(f); files.append((name, f))
    from PIL import Image, ImageDraw
    ims = [Image.open(f).convert('RGBA') for _, f in files]
    cols = min(6, len(ims)); rows = (len(ims) + cols - 1) // cols
    W = Image.new('RGBA', (res * cols, (res + 14) * rows), (255, 255, 255, 255)); d = ImageDraw.Draw(W)
    for i, ((n, _), im) in enumerate(zip(files, ims)):
        x, y = (i % cols) * res, (i // cols) * (res + 14)
        W.alpha_composite(im, (x, y + 14)); d.text((x + 4, y + 1), n, fill=(0, 0, 0, 255))
    W.convert('RGB').save(out)
