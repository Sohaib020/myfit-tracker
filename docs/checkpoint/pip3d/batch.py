import sys, os, time; sys.path.insert(0, '.')
import pipgen as P, anims as A
P.build()
P.S.cycles.samples = 20
O = '/tmp/claude-0/-home-claude-myfit-tracker/a3bd9ddc-9b2d-5774-bc12-e1c6a3a44cb1/scratchpad/pip3d/out/'
order = sys.argv[1:] or ['idle', 'talk'] + [n for n in A.ANIMS if n != 'idle']
t0 = time.time(); done = 0
for n in order:
    os.makedirs(O + n, exist_ok=True)
    if n in A.LOOKS:
        os.makedirs(O + 'look', exist_ok=True)
        f = O + 'look/%s.png' % n
        if not os.path.exists(f): P.pose(dict(A.LOOKS[n])); P.render(f)
        print('PROGRESS', n, 0, round(time.time() - t0), flush=True)
        continue
    if n == 'talk':
        jobs = [(i, dict(A.idle(0.0), mouth='talk', open=lv, blink=0.0)) for i, lv in enumerate((0.0, 0.2, 0.4, 0.6, 0.8, 1.0))]
    else:
        fn, fr = A.ANIMS[n]
        jobs = [(i, fn(i / fr)) for i in range(fr)]
    for i, pz in jobs:
        f = O + n + '/%03d.png' % i
        if os.path.exists(f): continue
        P.pose(pz); P.render(f); done += 1
        print('PROGRESS', n, i, round(time.time() - t0), flush=True)
print('ALLDONE', flush=True)
