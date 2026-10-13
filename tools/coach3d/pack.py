"""PNG frames -> animated WebP (looping), trimmed to the union bounding box of all clips of a coach so every clip
shares the same canvas.  python pack.py <coach_frames_dir> <out_dir>"""
import sys, os, glob
from PIL import Image

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
clips = sorted(d for d in os.listdir(src) if os.path.isdir(os.path.join(src, d)) and d != 'portrait')
total = 0
for c in clips:
    fs = sorted(glob.glob(os.path.join(src, c, '0*.png')))
    if not fs: continue
    meta = open(os.path.join(src, c, 'meta.txt')).read().split()
    fps = int(meta[2])
    ims = [Image.open(f).convert('RGBA') for f in fs]
    # 400x720 render -> 300x540 (sharper than rendering small; keeps file size sane)
    ims = [im.resize((300, 540), Image.LANCZOS) for im in ims]
    p = os.path.join(out, f'{c}.webp')
    ims[0].save(p, save_all=True, append_images=ims[1:], duration=round(1000 / fps), loop=0, quality=78, method=6, lossless=False)
    total += os.path.getsize(p)
    print(c, len(ims), os.path.getsize(p) // 1024, 'KB')
pd = os.path.join(src, 'portrait')
if os.path.isdir(pd):
    Image.open(os.path.join(pd, 'portrait.png')).convert('RGBA').resize((320, 320), Image.LANCZOS).save(os.path.join(out, 'portrait.webp'), quality=88, method=6)
    Image.open(os.path.join(pd, 'stand.png')).convert('RGBA').resize((300, 540), Image.LANCZOS).save(os.path.join(out, 'stand.webp'), quality=85, method=6)
print('TOTAL', total // 1024, 'KB')
