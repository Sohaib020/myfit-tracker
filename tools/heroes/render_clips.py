"""Render a Hero's app clips (transparent, Pip-style) or preview stills.
usage: python render_clips.py <assets_dir> <level> <out_dir> [shard nshards] [--stills]"""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import heroes as H, outfit as O, bpy
args = [a for a in sys.argv[1:] if not a.startswith('--')]
AS, lv, OUT = args[0], int(args[1]), args[2]
SH, NSH = (int(args[3]), int(args[4])) if len(args) > 4 else (0, 1)
STILLS = '--stills' in sys.argv
ROSTER = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'roster.json')))
who = os.environ.get('HERO', 'blaze'); spec = ROSTER[who]
H.load(os.path.join(AS, 'ubc'), os.path.join(AS, 'ual', 'UAL1_Standard.glb'), spec['body'], hair=spec.get('hair'))
O.tint(H.BODY, spec['skin'])
for o in H.S.objects:
    if o.type == 'MESH' and ('Hair' in o.name or 'Eyebrow' in o.name or o.name.startswith('Face')): O.tint(o, spec.get('hair_col', '#2A211C'))
O.dress(lv, accent=spec['accent'])
if STILLS:
    H.studio(res=(560, 700), samples=32)
    os.makedirs(OUT, exist_ok=True)
    for c, u in (('idle', 0.2), ('flex', 0.5), ('victory', 0.5), ('punch', 0.75)):
        H.clip_frame(c, u); H.render(os.path.join(OUT, c + '.png'))
else:
    H.studio(res=(560, 560), samples=int(os.environ.get('SAMPLES', '16')))
    H.S.render.film_transparent = True
    for o in H.S.objects:
        if o.type == 'MESH' and o.name.startswith('Plane'): o.is_shadow_catcher = True
    CL = {'idle': 30, 'flex': 28, 'victory': 28, 'punch': 24}
    jobs = [('portrait', 0, 1)] + [(c, i, n) for c, n in CL.items() for i in range(n)]
    for k, (c, i, n) in enumerate(jobs):
        if k % NSH != SH: continue
        f = os.path.join(OUT, c, '%03d.png' % i); os.makedirs(os.path.dirname(f), exist_ok=True)
        H.clip_frame('idle' if c == 'portrait' else c, 0.15 if c == 'portrait' else i / n)
        H.render(f, yaw=-16, dist=2.15, z=0.58); print('FRAME', c, i, flush=True)
print('RENDER_DONE')
