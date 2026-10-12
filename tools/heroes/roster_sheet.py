"""Roster overview: every hero's idle still at each tier. python roster_sheet.py <pub> <out.jpg>"""
import sys, os, json
from PIL import Image, ImageDraw, ImageFont
pub, out = sys.argv[1], sys.argv[2]
R = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'roster.json')))
try: F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 22)
except Exception: F = ImageFont.load_default()
W, Hh = 210, 262; names = list(R)
cols = 4; rows = (len(names) + cols - 1) // cols
sheet = Image.new('RGB', (cols * W * 3, rows * (Hh + 30)), (10, 12, 9)); d = ImageDraw.Draw(sheet)
for i, n in enumerate(names):
    x0 = (i % cols) * W * 3; y0 = (i // cols) * (Hh + 30)
    d.text((x0 + 8, y0 + 4), n.upper(), font=F, fill=(200, 255, 90))
    for l in (1, 2, 3):
        f = os.path.join(pub, n, 'l%d' % l, 'stills', 'idle.png')
        if os.path.exists(f): sheet.paste(Image.open(f).convert('RGB').resize((W, Hh)), (x0 + (l - 1) * W, y0 + 30))
sheet.save(out, quality=88); print('roster sheet', len(names))
