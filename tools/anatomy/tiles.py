"""Post-process rendered tiles: PNG -> WebP in app assets, plus a contact sheet for review.

The muscle tiles land in app/src/main/assets/muscles/ so the Train screen finds them by the same
names it already uses (chest, biceps, core, cardio, ...)."""
import os, glob
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '../..'))
SRC = os.environ.get('SRC', os.path.join(HERE, 'out'))
DST = os.environ.get('DST', os.path.join(ROOT, 'app/src/main/assets/muscles'))
SHEET = os.environ.get('SHEET', os.path.join(HERE, 'out/sheet.png'))
SIZE = int(os.environ.get('SIZE', '512'))
QUALITY = int(os.environ.get('QUALITY', '86'))

os.makedirs(DST, exist_ok=True)
files = sorted(glob.glob(os.path.join(SRC, '*.png')))
thumbs = []
for f in files:
    key = os.path.basename(f)[:-4]
    im = Image.open(f).convert('RGB')
    if im.size[0] != SIZE:
        im = im.resize((SIZE, SIZE), Image.LANCZOS)
    out = os.path.join(DST, key + '.webp')
    im.save(out, 'WEBP', quality=QUALITY, method=6)
    t = im.resize((190, 190), Image.LANCZOS)
    ImageDraw.Draw(t).text((6, 6), key, fill=(200, 0, 0))
    thumbs.append(t)
    print('WROTE', out)

if thumbs:
    cols = 6
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * 190, rows * 190), 'white')
    for i, t in enumerate(thumbs):
        sheet.paste(t, ((i % cols) * 190, (i // cols) * 190))
    sheet.save(SHEET)
    print('SHEET', SHEET, len(thumbs))
print('TILESDONE', len(files))
