"""
Frames -> buddy pack: <dir>/<move>/*.png -> <move>.webp (animated, looping), portrait.webp (256), stand.webp.
  python pack_champ.py <frames_dir> <out_dir>
Pip's buddy code needs idle.webp to accept a pack; every other clip name matches a Pip move.
"""
import sys, os, glob
from PIL import Image

src, out = sys.argv[1:3]
os.makedirs(out, exist_ok=True)
for d in sorted(glob.glob(os.path.join(src, '*/'))):
    name = os.path.basename(d.rstrip('/'))
    fs = sorted(glob.glob(os.path.join(d, '*.png')))
    if not fs: continue
    meta = open(os.path.join(d, 'meta.txt')).read().split() if os.path.exists(os.path.join(d, 'meta.txt')) else [name, len(fs), 15, 'loop']
    fps = int(meta[2])
    ims = [Image.open(f).convert('RGBA') for f in fs]
    ims[0].save(os.path.join(out, name + '.webp'), save_all=True, append_images=ims[1:], duration=int(1000 / fps), loop=0,
                quality=72, method=4, lossless=False)
    print('WEBP', name, len(ims), os.path.getsize(os.path.join(out, name + '.webp')) // 1024, 'KB')
for still, size in (('portrait', 256), ('stand', 448)):
    p = os.path.join(src, still + '.png')
    if os.path.exists(p):
        Image.open(p).convert('RGBA').resize((size, size), Image.LANCZOS).save(os.path.join(out, still + '.webp'), quality=85, method=6)
