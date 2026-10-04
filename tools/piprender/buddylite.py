"""Bundled "lite" buddy packs: 9 key moves at Pip's resolution (448 px), short loops, talk frames and a sharp
portrait — no look grid. ~3 MB per character. Units are (character, clip) balanced across shards.
env: WHO, SHARD, SHARDS, OUT, SAMPLES (14), RES (512), SIZE (448)"""
import sys, os, math, time, shutil, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pipgen as P, anims as A, cast as C
from PIL import Image

WHO = [w for w in os.environ.get('WHO', '').split(',') if w] or [w for w in C.CAST if w != 'pip']
OUT = os.environ.get('OUT', 'out') + '/'
SIZE = int(os.environ.get('SIZE', '448'))
# clip -> (frames per loop, ms per frame). Fewer frames over the same motion = shorter loops, same sharpness.
CLIPS = {'idle': (30, 60), 'wave': (26, 50), 'celebrate': (28, 50), 'thinking': (24, 60), 'love': (26, 50),
         'sleepy': (28, 70), 'letsgo': (24, 50), 'train': (28, 50)}
VIEW = math.radians(-24)

def units():
    u = []
    for w in WHO:
        u.append((w, 'portrait', 1)); u.append((w, 'talk', 6))
        for n, (nf, _) in CLIPS.items(): u.append((w, n, nf))
    return u

def assign(us, nsh):
    load = [0] * nsh; out = [[] for _ in range(nsh)]
    for x in sorted(us, key=lambda t: -t[2]):
        k = min(range(nsh), key=lambda i: load[i]); out[k].append(x); load[k] += x[2]
    return out

def frames(clip):
    if clip == 'portrait': return [('p', C.portrait_pose())]
    if clip == 'talk': return [(str(i), dict(A.idle(0.0), mouth='talk', open=lv, blink=0.0)) for i, lv in enumerate((0.0, 0.2, 0.4, 0.6, 0.8, 1.0))]
    fn, _ = A.ANIMS[clip]; nf, _ = CLIPS[clip]
    return [('%03d' % i, fn(i / nf)) for i in range(nf)]

def load(p): return Image.open(p).convert('RGBA').resize((SIZE, SIZE), Image.LANCZOS)

def main():
    sh, nsh = int(os.environ.get('SHARD', '0')), int(os.environ.get('SHARDS', '1'))
    mine = sorted(assign(units(), nsh)[sh]); t0 = time.time(); cur = None; tmp = '/tmp/bl/'
    for who, clip, _ in mine:
        d = OUT + who + '/'; os.makedirs(d, exist_ok=True)
        if cur != who:
            P.build(); C.skin(who); cur = who
            P.S.cycles.samples = int(os.environ.get('SAMPLES', '14'))
            rs = int(os.environ.get('RES', '512')); P.S.render.resolution_x = P.S.render.resolution_y = rs
        shutil.rmtree(tmp, ignore_errors=True); os.makedirs(tmp)
        fl = frames(clip)
        for k, pz in fl:
            P.pose(dict(pz)); C.after_pose()
            if clip == 'portrait': P.OBJ['Root'].rotation_euler.z += math.radians(C.VIEWS[who]) if who in C.VIEWS else VIEW
            P.render(tmp + k + '.png')
        if clip == 'portrait':
            load(tmp + 'p.png').save(d + 'portrait.webp', 'WEBP', quality=86, method=6)
        elif clip == 'talk':
            os.makedirs(d + 'talk', exist_ok=True)
            for k, _ in fl: load(tmp + k + '.png').save(d + 'talk/' + k + '.webp', 'WEBP', quality=80, method=6)
        else:
            ims = [load(tmp + k + '.png') for k, _ in fl]
            ims[0].save(d + clip + '.webp', 'WEBP', save_all=True, append_images=ims[1:], duration=CLIPS[clip][1], loop=0,
                        quality=72, method=6, minimize_size=True, kmax=len(ims), alpha_quality=70)
        print('P', who, clip, len(fl), round(time.time() - t0), flush=True)
    print('ALLDONE', flush=True)

if __name__ == '__main__': main()
