"""PNG frames -> side-by-side-alpha MP4 per clip (R26, 720 x 1280 class, 50 fps; see vid.py), cropped tight to the coach.
Standing clips share ONE crop box per coach (union of every frame of every standing clip), so the coach keeps the same
size and position whichever move plays; floor moves (pushup, plank) get their own box.  python pack.py <frames_dir> <out>"""
import sys, os, glob
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vid

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
FLOOR = {'pushup', 'plank'}
H_OUT = 1280                      # output height for standing clips (px): 720p portrait
clips = sorted(d for d in os.listdir(src) if os.path.isdir(os.path.join(src, d)) and d != 'portrait')


def frames(c): return sorted(glob.glob(os.path.join(src, c, '0*.png')))


def union_box(cs):
    box = None
    for c in cs:
        for f in frames(c)[::2]:
            b = Image.open(f).getchannel('A').point(lambda v: 255 if v > 10 else 0).getbbox()
            if not b: continue
            box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    return box


def pad(b, W, H, m=0.03):
    w, h = b[2] - b[0], b[3] - b[1]; px, py = int(w * m) + 4, int(h * m) + 4
    return (max(0, b[0] - px), max(0, b[1] - py), min(W, b[2] + px), min(H, b[3] + py))


stand = [c for c in clips if c not in FLOOR]
sample = Image.open(frames(stand[0])[0]); W, H = sample.size
sbox = pad(union_box(stand), W, H)
total = 0
for c in clips:
    fs = frames(c)
    if not fs: continue
    fps = int(open(os.path.join(src, c, 'meta.txt')).read().split()[2])
    box = sbox if c not in FLOOR else pad(union_box([c]), W, H)
    bw, bh = box[2] - box[0], box[3] - box[1]
    if c in FLOOR:
        ow = int((sbox[2] - sbox[0]) * H_OUT / (sbox[3] - sbox[1]) * 1.6); oh = max(2, int(ow * bh / bw))
    else:
        oh = H_OUT; ow = max(2, int(bw * H_OUT / bh))
    p = os.path.join(out, f'{c}.mp4')
    ow, oh = vid.encode(fs, p, fps, box, (ow, oh))
    total += os.path.getsize(p)
    print(c, len(fs), f'{ow}x{oh}', os.path.getsize(p) // 1024, 'KB')
pd = os.path.join(src, 'portrait')
if os.path.isdir(pd):
    st = Image.open(os.path.join(pd, 'stand.png')).convert('RGBA')
    b = pad(st.getchannel('A').point(lambda v: 255 if v > 10 else 0).getbbox(), *st.size)
    st = st.crop(b); st = st.resize((int(st.width * H_OUT / st.height), H_OUT), Image.LANCZOS)
    st.save(os.path.join(out, 'stand.webp'), quality=88, method=6)
print('TOTAL', total // 1024, 'KB')
