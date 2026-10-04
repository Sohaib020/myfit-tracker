"""Renders 3D how-to loops for the exercises in moves.MOVES (A→B→A, eased), with primary muscles red and secondary light red.
env: SHARD, SHARDS, OUT, SAMPLES, RES, FRAMES, ONLY (comma keys), POSE (A|B → single still for previews)"""
import sys, os, math, json, time, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mh as M, pose as P, moves as MV, bpy
from mathutils import Vector
from PIL import Image

CAT = {x['k']: x for x in json.load(open(os.environ.get('CATALOG', os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../app/src/main/assets/exercise_catalog.json'))))}
OUT = os.environ.get('OUT', 'loops') + '/'
RES = int(os.environ.get('RES', '360')); FR = int(os.environ.get('FRAMES', '24'))
NUM = ('pitch', 'roll', 'yaw', 'lift', 'torso', 'twist', 'neck', 'side', 'shrug', 'tiptoe', 'x', 'y')
PAIR = ('armL', 'armR', 'foreL', 'foreR', 'legL', 'legR', 'shinL', 'shinR', 'handL', 'handR')

def lerp_pose(a, b, t):
    out = dict(a)
    for k in set(a) | set(b):
        va, vb = a.get(k), b.get(k)
        if k in NUM:
            out[k] = (va or 0.0) + ((vb or 0.0) - (va or 0.0)) * t
        elif k in PAIR:
            da = va or vb; db = vb or va
            out[k] = (da[0] + (db[0] - da[0]) * t, da[1] + (db[1] - da[1]) * t)
        else:
            out[k] = (vb if t >= 0.5 else va) if (va is not None and vb is not None) else (va if va is not None else vb)
    return out

def set_camera(v):
    cam = M.OBJ['Cam']; az = math.radians(v.get('az', -35)); el = math.radians(v.get('el', 6)); r = 6
    cz = v.get('cz', 0.02)
    cam.location = (-r * math.sin(az) * math.cos(el), -r * math.cos(az) * math.cos(el), cz + r * math.sin(el))
    cam.rotation_euler = (math.radians(90) - el, 0, -az); cam.data.ortho_scale = v.get('scale', 2.15)

def main():
    M.build((RES, RES), int(os.environ.get('SAMPLES', '16')))
    P.init(); P.props_init()
    keys = [k for k in MV.MOVES if k in CAT]
    only = [k for k in os.environ.get('ONLY', '').split(',') if k]
    if only: keys = [k for k in keys if k in only]
    sh, nsh = int(os.environ.get('SHARD', '0')), int(os.environ.get('SHARDS', '1'))
    keys = keys[sh::nsh]
    os.makedirs(OUT, exist_ok=True); t0 = time.time()
    for k in keys:
        mv = MV.MOVES[k]; c = CAT[k]
        prim = [c['pm'].lower()]; sec = [m.lower() for m in c['s']][:3]
        M.paint(prim, secondary=sec); set_camera(mv.get('view', {}))
        if os.environ.get('POSE'):
            P.apply(dict(mv[os.environ['POSE']])); P.place_props(mv['props']); M.render(OUT + k + '.png'); print('P', k, flush=True); continue
        tmp = '/tmp/lp/' + k + '/'; shutil.rmtree(tmp, ignore_errors=True); os.makedirs(tmp)
        n = FR if not mv.get('hold') else 12
        frames = []
        for i in range(n):
            u = i / n; t = 0.5 - 0.5 * math.cos(2 * math.pi * u)          # 0→1→0, eased
            P.apply(lerp_pose(mv['A'], mv['B'], t)); P.place_props(mv['props'])
            f = tmp + '%03d.png' % i; M.render(f); frames.append(f)
        ims = [Image.open(f).convert('RGBA') for f in frames]
        bg = [Image.alpha_composite(Image.new('RGBA', im.size, (255, 255, 255, 255)), im).convert('RGB') for im in ims]
        bg[0].save(OUT + k + '.webp', 'WEBP', save_all=True, append_images=bg[1:], duration=int(1600 / n), loop=0, quality=74, method=4)
        print('P', k, n, round(time.time() - t0), flush=True)
    print('ALLDONE', flush=True)

if __name__ == '__main__': main()
