"""raw/<id>.png → out/icons/<id>.png (512, plus a squircle-masked preview) + out/sheet.jpg."""
import os, glob
from PIL import Image, ImageDraw
os.makedirs('out/icons', exist_ok=True)
fs = sorted(glob.glob('raw/*.png'))
def squircle(n, r=0.225):
    m = Image.new('L', (n, n), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, n - 1, n - 1), int(n * r), fill=255); return m
th = []
for f in fs:
    i = os.path.basename(f)[:-4]
    im = Image.open(f).convert('RGB').resize((512, 512), Image.LANCZOS)
    im.save(f'out/icons/{i}.png')
    t = Image.new('RGB', (280, 300), (245, 245, 247))
    ic = im.resize((240, 240)); t.paste(ic, (20, 14), squircle(240))
    ImageDraw.Draw(t).text((20, 272), i, fill=(40, 40, 40)); th.append(t)
cols = 4; rows = (len(th) + cols - 1) // cols
sheet = Image.new('RGB', (cols * 280, max(1, rows) * 300), (245, 245, 247))
for k, t in enumerate(th): sheet.paste(t, ((k % cols) * 280, (k // cols) * 300))
sheet.save('out/sheet.jpg', quality=88)
print(len(fs), 'icons')
