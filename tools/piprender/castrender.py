"""Renders the Arena cast. env: OUT, SHARD, SHARDS, SAMPLES, ONLY (comma list of jobs like zara:portrait)."""
import sys, os, math, time; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pipgen as P, anims as A, cast as C
O = os.environ.get('OUT', 'out') + '/'
VIEW = math.radians(float(os.environ.get('VIEW', '-24')))
CLIPS = {'cheer': ('cheer', 2), 'run': ('jog', 2), 'sad': ('sad', 3), 'wave': ('wave', 2)}   # clip -> (anim, frame step)
jobs = []
for who in C.CAST:
    jobs.append((who, 'portrait', 0, C.portrait_pose()))
    for clip, (an, st) in CLIPS.items():
        fn, fr = A.ANIMS[an]
        for i in range(0, fr, st): jobs.append((who, clip, i // st, fn(i / fr)))
only = [x for x in os.environ.get('ONLY', '').split(',') if x]
if only: jobs = [j for j in jobs if '%s:%s' % (j[0], j[1]) in only and (j[2] == 0 or os.environ.get('ALLFR'))]
sh, nsh = int(os.environ.get('SHARD', '0')), int(os.environ.get('SHARDS', '1'))
mine = [j for k, j in enumerate(jobs) if k % nsh == sh]
t0 = time.time(); cur = None
for who, clip, i, pz in mine:
    f = O + '%s/%s/%03d.png' % (who, clip, i)
    if os.path.exists(f): continue
    if cur != who:
        P.build(); C.skin(who); cur = who
        P.S.cycles.samples = int(os.environ.get('SAMPLES', '20'))
        rs = int(os.environ.get('RES', '512')); P.S.render.resolution_x = P.S.render.resolution_y = rs
    os.makedirs(os.path.dirname(f), exist_ok=True)
    P.pose(dict(pz)); C.after_pose(); P.OBJ['Root'].rotation_euler.z += math.radians(C.VIEWS[who]) if who in C.VIEWS else VIEW
    P.render(f); print('P', who, clip, i, round(time.time() - t0), flush=True)
print('ALLDONE', len(jobs), flush=True)
