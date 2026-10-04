"""raw/<id>.png → out/journey/<id>.webp (720×960) + out/sheet.jpg contact sheet."""
import os, glob
from PIL import Image, ImageDraw
os.makedirs('out/journey', exist_ok=True)
fs = sorted(glob.glob('raw/*.png'))
th = []
for f in fs:
    i = os.path.basename(f)[:-4]
    im = Image.open(f).convert('RGB').resize((720, 960), Image.LANCZOS)
    im.save(f'out/journey/{i}.webp', 'WEBP', quality=80, method=6)
    t = im.resize((240, 320)); ImageDraw.Draw(t).text((6, 6), i, fill=(255, 255, 0)); th.append(t)
cols = 6; rows = (len(th) + cols - 1) // cols
sheet = Image.new('RGB', (cols * 240, max(1, rows) * 320), 'white')
for k, t in enumerate(th): sheet.paste(t, ((k % cols) * 240, (k // cols) * 320))
sheet.save('out/sheet.jpg', quality=82)
print(len(fs), 'images')
