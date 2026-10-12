"""Side-by-side tier preview GIF from packed previews: python ladder.py <pub_dir> <out.gif>"""
import sys, os
from PIL import Image, ImageDraw, ImageFont
pub, out = sys.argv[1], sys.argv[2]
try: F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20)
except Exception: F = ImageFont.load_default()
LAB = {1: 'LV1 · Street', 2: 'LV2 · Epic', 3: 'LV3 · Legendary'}
def frames(d):
    fr = []
    for c in ('idle', 'combo', 'run', 'jump', 'dance'):
        p = os.path.join(d, 'preview_%s.gif' % c)
        if not os.path.exists(p): continue
        g = Image.open(p)
        for i in range(g.n_frames): g.seek(i); fr.append(g.convert('RGB').copy())
    return fr
seqs = [(l, frames(os.path.join(pub, 'l%d' % l))) for l in (1, 2, 3)]
seqs = [(l, s) for l, s in seqs if s]
if seqs:
    n = min(len(s) for _, s in seqs); W = 320; outf = []
    for i in range(n):
        im = Image.new('RGB', (W * len(seqs), W + 32), (10, 12, 9)); d = ImageDraw.Draw(im)
        for k, (l, s) in enumerate(seqs):
            im.paste(s[i].resize((W, W)), (k * W, 32)); d.text((k * W + 10, 6), LAB[l], font=F, fill=(200, 255, 90))
        outf.append(im)
    outf[0].save(out, save_all=True, append_images=outf[1:], duration=55, loop=0); print('ladder', n, 'frames')
