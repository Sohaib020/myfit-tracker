"""Card portraits from the owner's reference images: background removed (flood fill from the border on the near-white
studio backdrop, feathered edge), cropped head-to-waist, square.  python ref_portrait.py ref.png out.webp"""
import sys
import numpy as np
from PIL import Image, ImageFilter
from collections import deque

im = Image.open(sys.argv[1]).convert('RGB'); a = np.asarray(im).astype(np.int16)
H, W, _ = a.shape
bg = np.median(np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]]), axis=0)
dist = np.abs(a - bg).sum(2)
near = dist < 38
mask = np.zeros((H, W), bool); q = deque()
for x in range(W):
    for y in (0, H - 1):
        if near[y, x] and not mask[y, x]: mask[y, x] = True; q.append((y, x))
for y in range(H):
    for x in (0, W - 1):
        if near[y, x] and not mask[y, x]: mask[y, x] = True; q.append((y, x))
while q:
    y, x = q.popleft()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ny, nx = y + dy, x + dx
        if 0 <= ny < H and 0 <= nx < W and near[ny, nx] and not mask[ny, nx]: mask[ny, nx] = True; q.append((ny, nx))
alpha = Image.fromarray(np.where(mask, 0, 255).astype(np.uint8)).filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(1.2))
rgba = im.convert('RGBA'); rgba.putalpha(alpha)
ys, xs = np.where(np.asarray(alpha) > 128)
top, bot = ys.min(), ys.max(); cx = int(np.median(xs[ys < top + (bot - top) * 0.12]))   # centre on the head
h = bot - top
y0 = max(0, top - int(h * 0.03)); y1 = top + int(h * 0.48)                                   # head to waist
side = y1 - y0
x0 = max(0, cx - side // 2); x1 = min(W, x0 + side)
crop = rgba.crop((x0, y0, x1, y1))
sq = Image.new('RGBA', (side, side), (0, 0, 0, 0)); sq.paste(crop, ((side - crop.width) // 2, 0))
sq.resize((384, 384), Image.LANCZOS).save(sys.argv[2], quality=90, method=6)
print('ok', sys.argv[2])
