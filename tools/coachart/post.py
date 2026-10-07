"""raw/<id>.png → out/coach/<id>.webp (cut out, transparent, 448x640) + out/sheet.jpg contact sheet."""
import os, glob, io
from PIL import Image, ImageDraw
from rembg import remove, new_session
os.makedirs('out/coach', exist_ok=True)
sess = new_session('isnet-general-use')
fs = sorted(glob.glob('raw/*.png')); th = []
for f in fs:
    i = os.path.basename(f)[:-4]
    src = Image.open(f).convert('RGB')
    cut = remove(src, session=sess).convert('RGBA')
    bb = cut.getbbox()
    cut.save(f'out/coach/{i}.webp', 'WEBP', quality=82, method=6)
    t = src.resize((224, 320)); ImageDraw.Draw(t).text((4, 4), i, fill=(255, 0, 0)); th.append(t)
cols = 6; rows = (len(th) + cols - 1) // cols
sheet = Image.new('RGB', (cols * 224, max(1, rows) * 320), 'white')
for k, t in enumerate(th): sheet.paste(t, ((k % cols) * 224, (k // cols) * 320))
sheet.save('out/sheet.jpg', quality=82)
print(len(fs), 'coach renders')
