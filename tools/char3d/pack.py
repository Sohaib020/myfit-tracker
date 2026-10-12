"""Pack rendered athlete frames like Pip's lite packs (448 px animated WebP + portrait) and make a preview GIF on the
app's Carbon & Lime backdrop so the clips can be reviewed in chat. usage: python pack.py <frames_dir> <out_dir>"""
import sys, os, glob
from PIL import Image, ImageDraw
src, out = sys.argv[1], sys.argv[2]
SIZE = 448
CLIPS = {"idle": 60, "flex": 50, "victory": 50, "punch": 45}      # ms per frame (missing clips are skipped)
os.makedirs(out, exist_ok=True)
def ld(p): return Image.open(p).convert("RGBA").resize((SIZE, SIZE), Image.LANCZOS)
def bg():   # Carbon & Lime-like: near-black with a soft lime floor glow
    import numpy as np
    y, x = np.mgrid[0:SIZE, 0:SIZE].astype(np.float32)
    d = np.sqrt(((x - SIZE / 2) / (SIZE * 0.55)) ** 2 + ((y - SIZE * 0.86) / (SIZE * 0.22)) ** 2)
    g = np.clip(1 - d, 0, 1) ** 1.6
    base = np.array([10, 12, 9], np.float32); lime = np.array([110, 160, 30], np.float32)
    im = base + g[..., None] * (lime - base) * 0.45
    return Image.fromarray(np.dstack([im, np.full((SIZE, SIZE), 255, np.float32)]).astype("uint8"), "RGBA")
B = bg()
total = 0
p = sorted(glob.glob(os.path.join(src, "portrait", "*.png")))
if p: ld(p[0]).save(os.path.join(out, "portrait.webp"), "WEBP", quality=86, method=6)
sheet = []
for c, ms in CLIPS.items():
    fs = sorted(glob.glob(os.path.join(src, c, "*.png")))
    if not fs: continue
    ims = [ld(f) for f in fs]
    ims[0].save(os.path.join(out, c + ".webp"), "WEBP", save_all=True, append_images=ims[1:], duration=ms, loop=0, quality=80, method=6)
    pv = [Image.alpha_composite(B, im).convert("RGB") for im in ims]
    pv[0].save(os.path.join(out, "preview_" + c + ".gif"), save_all=True, append_images=pv[1:], duration=ms, loop=0)
    sheet.append(pv)
for f in sorted(os.listdir(out)):
    sz = os.path.getsize(os.path.join(out, f)); print(f"{f}: {sz/1024:.0f} KB")
    if f.endswith(".webp"): total += sz
print(f"PACK webp total {total/1024/1024:.2f} MB")
# one combined preview: three clips side by side, looping on the longest
if sheet:
    n = max(len(s) for s in sheet); W = SIZE * len(sheet)
    frames = []
    for i in range(n):
        im = Image.new("RGB", (W, SIZE))
        for k, s in enumerate(sheet): im.paste(s[i % len(s)], (k * SIZE, 0))
        frames.append(im.resize((W * 3 // 4, SIZE * 3 // 4), Image.LANCZOS))
    frames[0].save(os.path.join(out, "preview_all.gif"), save_all=True, append_images=frames[1:], duration=55, loop=0)
