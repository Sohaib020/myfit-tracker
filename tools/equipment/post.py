"""raw/<id>.png → out/equipment/<id>.webp (384²) + out/sheet.jpg."""
import os, glob
from PIL import Image, ImageDraw
os.makedirs('out/equipment', exist_ok=True)
fs = sorted(glob.glob('raw/*.png')); th = []
for f in fs:
    i = os.path.basename(f)[:-4]
    im = Image.open(f).convert('RGB').resize((384, 384), Image.LANCZOS)
    im.save(f'out/equipment/{i}.webp', 'WEBP', quality=82, method=6)
    t = im.resize((200, 200)); ImageDraw.Draw(t).text((6, 6), i, fill=(220, 0, 0)); th.append(t)
cols = 6; rows = (len(th) + cols - 1) // cols
sheet = Image.new('RGB', (cols * 200, max(1, rows) * 200), 'white')
for k, t in enumerate(th): sheet.paste(t, ((k % cols) * 200, (k // cols) * 200))
sheet.save('out/sheet.jpg', quality=85); print(len(fs))
