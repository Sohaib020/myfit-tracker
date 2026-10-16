"""
Render one champion's buddy pack (CI job): every move for its tier as 448x448 PNG frames + portrait + stand.
  python render_champ.py <rigged.glb> <motion.json.gz> <tier> <out_dir> [fps=15] [samples=16] [moves=a,b,...]

Same square framing for every move (the buddy is drawn in a square, like Pip). pack_champ.py turns the frames into
animated WebPs named exactly like Pip's clips, so the app's buddy code plays them unchanged.
"""
import sys, os, bpy
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'coach3d')); sys.path.insert(0, HERE)
import load, motion, studio, look

glb, mpack, tier, out = sys.argv[1:5]
fps = int(sys.argv[5]) if len(sys.argv) > 5 else 12
samples = int(sys.argv[6]) if len(sys.argv) > 6 else 16
only = sys.argv[7].split(',') if len(sys.argv) > 7 and sys.argv[7] else None
os.makedirs(out, exist_ok=True)

pack = motion.load_pack(mpack)
arm, body = load.load_rigged(glb)
look.refine(body, subdiv=0)
s = studio.setup(res=448, samples=samples)
s.render.image_settings.file_format = 'PNG'; s.render.image_settings.color_mode = 'RGBA'
s.render.resolution_x = 448; s.render.resolution_y = 448
H = max((body.matrix_world @ v.co).z for i, v in enumerate(body.data.vertices) if i % 9 == 0)
FULL = dict(target=(0, 0, H * 0.52), dist=H * 2.45, yaw=-14, pitch=3, lens=60)   # whole body + headroom for jumps

# portrait (bundled in the APK) and a still for the chooser
arm.animation_data_create(); arm.animation_data.action = None
s.frame_set(0)
head = arm.matrix_world @ arm.pose.bones['Head'].head
studio.camera(target=(head.x, head.y, head.z - H * 0.03), dist=H * 0.95, yaw=-12, pitch=3, lens=70)
studio.render(os.path.join(out, 'portrait.png'))
studio.camera(**FULL)

for name in motion.moves_for(tier):
    if name not in (only or motion.CLIP_SET): continue
    act, n, loop = motion.apply(arm, pack, name)
    d = os.path.join(out, name); os.makedirs(d, exist_ok=True)
    n_src = n if loop else n + 1
    k = max(2, round(n_src * fps / 24.0))
    for i in range(k):
        f = i * (n_src / k)
        s.frame_set(int(f), subframe=f - int(f))
        s.render.filepath = os.path.join(d, f'{i:04d}.png')
        bpy.ops.render.render(write_still=True)
    open(os.path.join(d, 'meta.txt'), 'w').write(f'{name} {k} {fps} {"loop" if loop else "once"}\n')
    if name == 'idle':
        s.frame_set(0); studio.render(os.path.join(out, 'stand.png'))
    print('CLIP', name, k, flush=True)
print('RENDER_DONE')
