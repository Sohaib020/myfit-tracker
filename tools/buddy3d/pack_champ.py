"""R28: champion frames (already cropped tight by render_champ) -> one side-by-side-alpha MP4 per move (~1000 px tall, 60 fps) + stills.
  python pack_champ.py <frames_dir> <out_dir>"""
import sys, os, glob
from PIL import Image
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'coach3d'))
import vid

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
for d in sorted(glob.glob(os.path.join(src, '*/'))):
    name = os.path.basename(d.rstrip('/'))
    fs = sorted(glob.glob(os.path.join(d, '*.png')))
    if not fs: continue
    mf = os.path.join(d, 'meta.txt')
    fps = int(open(mf).read().split()[2]) if os.path.exists(mf) else 60
    p = os.path.join(out, name + '.mp4')
    w, h = vid.encode(fs, p, fps)
    print('MP4', name, len(fs), f'{w}x{h}', os.path.getsize(p) // 1024, 'KB')
for still, size in (('portrait', 256), ('stand', 720)):
    p = os.path.join(src, still + '.png')
    if os.path.exists(p):
        Image.open(p).convert('RGBA').resize((size, size), Image.LANCZOS).save(os.path.join(out, still + '.webp'), quality=86, method=6)
