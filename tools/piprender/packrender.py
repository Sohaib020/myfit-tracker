"""Renders full "buddy packs" so any Arena character can replace Pip: every Pip animation, talk frames and a 7x7 look grid.
Work is split into (character, clip) units balanced across shards; each shard writes finished WebPs (no PNGs leave the runner).
env: WHO (comma list), SHARD, SHARDS, OUT, SAMPLES (12), RES (384), SIZE (320), BUDGET_MIN"""
import sys, os, math, time, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pipgen as P, anims as A, cast as C
from PIL import Image

WHO = [w for w in os.environ.get('WHO', '').split(',') if w] or list(C.CAST)
OUT = os.environ.get('OUT', 'out') + '/'
SIZE = int(os.environ.get('SIZE', '320'))
N = 7
LOOK7 = {}
for r in range(N):
    nod = -14 + 26 * r / (N - 1)
    for c in range(N):
        yaw = -32 + 64 * c / (N - 1)
        LOOK7['look_%02d_%02d' % (r, c)] = dict(yaw=yaw, nod=nod, tilt=-yaw * 0.12, look=(yaw / 32 * 0.7, -nod / 14 * 0.55), eyes='open', mouth='smile', brows='normal', blush=0.8, armL=14, armR=14, ant=-yaw * 0.4)

def units():
    u = []
    for w in WHO:
        u.append((w, 'look', len(LOOK7)))
        u.append((w, 'talk', 6))
        for n, (fn, fr) in A.ANIMS.items(): u.append((w, n, fr))
    return u

def assign(us, nsh):
    load = [0] * nsh; out = [[] for _ in range(nsh)]
    for x in sorted(us, key=lambda t: -t[2]):
        k = min(range(nsh), key=lambda i: load[i]); out[k].append(x); load[k] += x[2]
    return out

def frames(clip):
    if clip == 'look': return [(k, dict(v)) for k, v in sorted(LOOK7.items())]
    if clip == 'talk': return [(str(i), dict(A.idle(0.0), mouth='talk', open=lv, blink=0.0)) for i, lv in enumerate((0.0, 0.2, 0.4, 0.6, 0.8, 1.0))]
    fn, fr = A.ANIMS[clip]
    return [('%03d' % i, fn(i / fr)) for i in range(fr)]

def load(p): return Image.open(p).convert('RGBA').resize((SIZE, SIZE), Image.LANCZOS)

def main():
    sh, nsh = int(os.environ.get('SHARD', '0')), int(os.environ.get('SHARDS', '1'))
    mine = sorted(assign(units(), nsh)[sh])          # sorted → same character's units are consecutive (fewer rebuilds)
    budget = float(os.environ.get('BUDGET_MIN', '330')) * 60; t0 = time.time(); cur = None
    tmp = '/tmp/pk/'
    for who, clip, _ in mine:
        d = OUT + who + '/'
        target = d + (clip + '.webp' if clip not in ('look', 'talk') else clip + '/')
        if os.path.exists(target) and clip not in ('look', 'talk'): continue
        if time.time() - t0 > budget: print('::warning::shard %d stopped at budget' % sh, flush=True); break
        if cur != who:
            P.build(); C.skin(who); cur = who
            P.S.cycles.samples = int(os.environ.get('SAMPLES', '12'))
            rs = int(os.environ.get('RES', '384')); P.S.render.resolution_x = P.S.render.resolution_y = rs
        shutil.rmtree(tmp, ignore_errors=True); os.makedirs(tmp)
        fl = frames(clip)
        for k, pz in fl:
            P.pose(dict(pz)); C.after_pose(); P.render(tmp + k + '.png')
        os.makedirs(d, exist_ok=True)
        if clip in ('look', 'talk'):
            os.makedirs(d + clip, exist_ok=True)
            for k, _ in fl: load(tmp + k + '.png').save(d + clip + '/' + k + '.webp', 'WEBP', quality=80, method=4)
        else:
            ims = [load(tmp + k + '.png') for k, _ in fl]
            ims[0].save(d + clip + '.webp', 'WEBP', save_all=True, append_images=ims[1:], duration=50, loop=0, quality=72, method=4)
        print('P', who, clip, len(fl), round(time.time() - t0), flush=True)
    print('ALLDONE', flush=True)

if __name__ == '__main__': main()
