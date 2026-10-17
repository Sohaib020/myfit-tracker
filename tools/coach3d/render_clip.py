"""
Render one coach clip to PNG frames (transparent) for CI.
  python render_clip.py <glb_dir> <ual.glb> <who> <clip|portrait> <out_dir> [fps=20] [samples=40]
Camera: one fixed 3/4 framing for every standing clip (so the coach never jumps in size between moves), a wider side
framing for floor moves. Frame size 600x1080 (portrait 512x512); pack.py crops tight to the coach.
"""
import sys, os, bpy, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rig, clips, studio, look

glb, ual, who, clip, out = sys.argv[1:6]
fps = int(sys.argv[6]) if len(sys.argv) > 6 else 20
samples = int(sys.argv[7]) if len(sys.argv) > 7 else 40
K = float(os.environ.get('COACH_RES', '1.55'))   # R26: frames 930x1674 so the 1280-px-tall video is sharp
FW, FH = int(600 * K) // 2 * 2, int(1080 * K) // 2 * 2
os.makedirs(out, exist_ok=True)

arm, body = rig.load(who, glb)
look.refine(body, subdiv=0)
rig.bulk(arm, who)
s = studio.setup(res=FH, samples=samples)
s.cycles.seed = 7; s.cycles.use_animated_seed = False   # same noise pattern every frame: no denoiser shimmer at 50 fps
s.render.image_settings.file_format = 'PNG'; s.render.image_settings.color_mode = 'RGBA'

if clip == 'portrait':
    c = clips.build(who, 'idle', arm, glb, ual)
    s.frame_set(0)
    s.render.resolution_x = 512; s.render.resolution_y = 512
    head = arm.matrix_world @ arm.pose.bones['Head'].head
    studio.camera(target=(head.x, head.y, head.z + 0.02), dist=1.75, yaw=-14, pitch=3, lens=70)
    studio.render(os.path.join(out, 'portrait.png'))
    # full-figure still (APK fallback while the animated pack downloads)
    s.render.resolution_x = FW; s.render.resolution_y = FH
    studio.camera(target=(0, 0, 1.05), dist=4.3, yaw=-16, pitch=4, lens=60)
    studio.render(os.path.join(out, 'stand.png'))
    print('RENDER_DONE portrait'); sys.exit(0)

c = clips.build(who, clip, arm, glb, ual)
s.render.resolution_x = FW; s.render.resolution_y = FH
floor = clip in ('pushup', 'plank')
if floor:
    studio.camera(target=(0, -0.72, 0.40), dist=6.2, yaw=-62, pitch=8, lens=60)
else:
    studio.camera(target=(0, 0, 1.05), dist=4.3, yaw=-16, pitch=4, lens=60)
n_src = c.frames if c.loop else c.frames + 1
n = max(2, round(n_src * fps / 24.0))
for i in range(min(n, int(os.environ.get('MAXF', n)))):
    f = i * (n_src / n)
    s.frame_set(int(f), subframe=f - int(f))
    s.render.filepath = os.path.join(out, f'{i:04d}.png')
    bpy.ops.render.render(write_still=True)
open(os.path.join(out, 'meta.txt'), 'w').write(f'{clip} {n} {fps} {"loop" if c.loop else "once"}\n')
print('RENDER_DONE', clip, n)
