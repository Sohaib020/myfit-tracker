"""raw/<id>.png → out/body/<id>.webp: background removed, character scaled so head-to-feet = 92 % of a 360×640 canvas,
feet on a common baseline, centred — so body-fat levels line up for the morph. Plus out/sheet.jpg."""
import os, glob
from PIL import Image, ImageDraw
from rembg import remove, new_session
os.makedirs('out/body', exist_ok=True)
sess = new_session('isnet-general-use')
W, H = 360, 640
fs = sorted(glob.glob('raw/*.png')); th = []
for f in fs:
    i = os.path.basename(f)[:-4]
    src = Image.open(f).convert('RGB')
    cut = remove(src, session=sess).convert('RGBA')
    bb = cut.getbbox()
    if not bb: continue
    fig = cut.crop(bb)
    s = (H * 0.92) / fig.height
    fig = fig.resize((max(1, int(fig.width * s)), max(1, int(fig.height * s))), Image.LANCZOS)
    if fig.width > W:
        k = W / fig.width; fig = fig.resize((W, int(fig.height * k)), Image.LANCZOS)
    can = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    can.alpha_composite(fig, ((W - fig.width) // 2, H - int(H * 0.02) - fig.height))
    can.save(f'out/body/{i}.webp', 'WEBP', quality=84, method=6)
    t = Image.new('RGB', (180, 320), (200, 200, 205)); t.paste(can.resize((180, 320)), (0, 0), can.resize((180, 320)))
    ImageDraw.Draw(t).text((4, 4), i, fill=(255, 0, 0)); th.append(t)
cols = 7; rows = (len(th) + cols - 1) // cols
sheet = Image.new('RGB', (cols * 180, max(1, rows) * 320), 'white')
for k, t in enumerate(th): sheet.paste(t, ((k % cols) * 180, (k // cols) * 320))
sheet.save('out/sheet.jpg', quality=82)
print(len(fs), 'body renders')
