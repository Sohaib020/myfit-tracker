"""raw/<id>.png → out/maps/<id>.webp (768×448) + out/sheet.jpg contact sheet."""
import os, glob
from PIL import Image, ImageDraw
os.makedirs('out/maps', exist_ok=True)
fs = sorted(glob.glob('raw/*.png'))
th = []
for f in fs:
    i = os.path.basename(f)[:-4]
    im = Image.open(f).convert('RGB').resize((768, 896), Image.LANCZOS)
    im.save(f'out/maps/{i}.webp', 'WEBP', quality=78, method=6)
    t = im.resize((256, 299)); ImageDraw.Draw(t).text((6, 6), i, fill=(255, 255, 0)); th.append(t)
cols = 4; rows = (len(th) + cols - 1) // cols
sheet = Image.new('RGB', (cols * 256, max(1, rows) * 299), 'white')
for k, t in enumerate(th): sheet.paste(t, ((k % cols) * 256, (k // cols) * 299))
sheet.save('out/sheet.jpg', quality=80)
print(len(fs), 'covers')
