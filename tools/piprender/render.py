import sys, os, time; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pipgen as P, anims as A
P.build()
P.S.cycles.samples = int(os.environ.get('SAMPLES', '20'))
O = os.environ.get('OUT', 'out') + '/'
NEW = ['thumbsup', 'salute', 'facepalm', 'cheer', 'grumpy', 'peekaboo', 'highfive', 'bow', 'meditate', 'dizzy', 'sneeze', 'hearteyes']
jobs = []
which = sys.argv[1:] or (['looks'] + NEW)
for n in which:
    if n == 'looks':
        jobs += [('look', k, A.LOOKS[k]) for k in sorted(A.LOOKS) if k.startswith('look_')]
    else:
        fn, fr = A.ANIMS[n]; jobs += [(n, '%03d' % i, fn(i / fr)) for i in range(fr)]
sh, nsh = int(os.environ.get('SHARD', '0')), int(os.environ.get('SHARDS', '1'))
t0 = time.time()
for j, (d, k, pz) in enumerate(jobs):
    if j % nsh != sh: continue
    os.makedirs(O + d, exist_ok=True); f = O + d + '/' + k + '.png'
    if os.path.exists(f): continue
    P.pose(dict(pz)); P.render(f)
    print('P', d, k, round(time.time() - t0), flush=True)
print('ALLDONE', len(jobs), flush=True)
