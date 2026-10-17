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
RES = int(os.environ.get('CHAMP_RES', '720'))       # R26: 720p clips (were 448)
s = studio.setup(res=RES, samples=samples)
s.cycles.seed = 7; s.cycles.use_animated_seed = False   # same noise every frame: no denoiser shimmer at 50 fps
s.cycles.denoising_prefilter = 'FAST'   # 2.7x faster, same look at 8 samples
s.render.image_settings.file_format = 'PNG'; s.render.image_settings.color_mode = 'RGBA'
s.render.resolution_x = RES; s.render.resolution_y = RES
H = max((body.matrix_world @ v.co).z for i, v in enumerate(body.data.vertices) if i % 9 == 0)
FULL = dict(target=(0, 0, H * 0.52), dist=H * 2.45, yaw=-14, pitch=3, lens=60)   # whole body + headroom for jumps

# portrait (bundled in the APK) and a still for the chooser
arm.animation_data_create(); arm.animation_data.action = None
s.frame_set(0)
head = arm.matrix_world @ arm.pose.bones['Head'].head
studio.camera(target=(head.x, head.y, head.z - H * 0.03), dist=H * 0.95, yaw=-12, pitch=3, lens=70)
studio.render(os.path.join(out, 'portrait.png'))
studio.camera(**FULL)
# full-body still (bundled: the locked-look "glimpse" and the stand-in while a pack downloads)
if os.environ.get('STAND', '1') == '1':
    motion.apply(arm, pack, 'idle'); s.frame_set(0)
    studio.render(os.path.join(out, 'stand.png'))

# R28 tight framing: the union of where the body goes in EVERY clip of this tier (same in every render part, so all
# clips of a look share one crop and the character never jumps in size), rendered with a crop border at a
# resolution where the body is ~TARGET px tall. Square frames left lots of empty space and looked tiny in cards.
def envelope():
    from bpy_extras.object_utils import world_to_camera_view
    cam = s.camera; lo = [1e9, 1e9]; hi = [-1e9, -1e9]
    for name in motion.moves_for(tier):
        if name not in motion.CLIP_SET: continue
        act, n, loop = motion.apply(arm, pack, name)
        for k in range(11):
            s.frame_set(int(n * k / 10))
            dg = bpy.context.evaluated_depsgraph_get()
            ev = body.evaluated_get(dg); me = ev.to_mesh(); mw = ev.matrix_world
            for i in range(0, len(me.vertices), 23):
                c = world_to_camera_view(s, cam, mw @ me.vertices[i].co)
                lo[0] = min(lo[0], c.x); lo[1] = min(lo[1], c.y); hi[0] = max(hi[0], c.x); hi[1] = max(hi[1], c.y)
            ev.to_mesh_clear()
    return lo, hi

TARGET = int(os.environ.get('CHAMP_PX', '960'))
for _ in range(4):            # back the camera off until no move leaves the frame (jumps, arms overhead, horns)
    lo, hi = envelope()
    if min(lo) > 0.01 and max(hi) < 0.99: break
    FULL['dist'] *= 1.2; studio.camera(**FULL)
mx = (hi[0] - lo[0]) * 0.04; my = (hi[1] - lo[1]) * 0.03
lo = [max(0.0, lo[0] - mx), max(0.0, lo[1] - my * 0.5)]; hi = [min(1.0, hi[0] + mx), min(1.0, hi[1] + my)]
R = max(720, min(1800, int(round(TARGET / max(0.2, hi[1] - lo[1]) / 2)) * 2))
s.render.resolution_x = R; s.render.resolution_y = R
s.render.use_border = True; s.render.use_crop_to_border = True
s.render.border_min_x, s.render.border_max_x = lo[0], hi[0]
s.render.border_min_y, s.render.border_max_y = lo[1], hi[1]
print('CROP', R, [round(v, 3) for v in lo + hi], flush=True)

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
    print('CLIP', name, k, flush=True)
print('RENDER_DONE')
