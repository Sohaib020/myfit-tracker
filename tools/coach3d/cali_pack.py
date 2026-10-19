"""R32: rendered calisthenics frames -> per-step side-by-side-alpha MP4 loop (rep steps) + a WebP still (every step).
  python cali_pack.py <frames_dir> <out_dir>      (frames_dir/<key>/0000.png… + meta.txt from cali_render.py)"""
import sys, os, glob
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vid

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
SIZE = 480
tot = 0
for key in sorted(os.listdir(src)):
    d = os.path.join(src, key)
    fs = sorted(glob.glob(os.path.join(d, '0*.png')))
    if not fs: continue
    meta = open(os.path.join(d, 'meta.txt')).read().split()
    thumb = int(meta[-1]) if meta[-2] == 'thumb' else 0
    im = Image.open(fs[min(thumb, len(fs) - 1)]).convert('RGBA').resize((360, 360), Image.LANCZOS)
    im.save(os.path.join(out, f'{key}.webp'), quality=82, method=6, alpha_quality=90)
    if len(fs) > 1:
        vid.encode(fs, os.path.join(out, f'{key}.mp4'), 30, None, (SIZE, SIZE), crf=24)
    sz = sum(os.path.getsize(os.path.join(out, f)) for f in (f'{key}.webp', f'{key}.mp4') if os.path.exists(os.path.join(out, f)))
    tot += sz; print(key, len(fs), sz // 1024, 'KB')
print('TOTAL', tot // 1024, 'KB')
