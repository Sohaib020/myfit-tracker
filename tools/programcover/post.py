"""raw/<id>.png → out/programs/<id>.webp (768×448) + out/sheet.jpg contact sheet."""
import os, glob
from PIL import Image, ImageDraw
os.makedirs('out/programs', exist_ok=True)
fs = sorted(glob.glob('raw/*.png'))
th = []
for f in fs:
    i = os.path.basename(f)[:-4]
    im = Image.open(f).convert('RGB').resize((768, 448), Image.LANCZOS)
    im.save(f'out/programs/{i}.webp', 'WEBP', quality=78, method=6)
    t = im.resize((384, 224)); ImageDraw.Draw(t).text((6, 6), i, fill=(255, 255, 0)); th.append(t)
cols = 4; rows = (len(th) + cols - 1) // cols
sheet = Image.new('RGB', (cols * 384, max(1, rows) * 224), 'white')
for k, t in enumerate(th): sheet.paste(t, ((k % cols) * 384, (k // cols) * 224))
sheet.save('out/sheet.jpg', quality=80)
print(len(fs), 'covers')
