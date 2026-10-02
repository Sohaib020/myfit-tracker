import os, sys
from PIL import Image
O = 'out/'; D = '/home/claude/myfit-tracker/app/src/main/assets/pip/'
os.makedirs(D + 'talk', exist_ok=True)
SIZE = int(os.environ.get('SIZE', '448'))
def load(p):
    im = Image.open(p).convert('RGBA')
    return im.resize((SIZE, SIZE), Image.LANCZOS) if SIZE != im.width else im
names = sys.argv[1:] or sorted(os.listdir(O))
tot = 0
for n in names:
    fr = sorted(f for f in os.listdir(O + n) if f.endswith('.png'))
    if not fr: continue
    if n == 'look':
        os.makedirs(D + 'look', exist_ok=True)
        for f in fr: load(O + n + '/' + f).save(D + 'look/' + f[:-4] + '.webp', 'WEBP', quality=85, method=4)
        continue
    if n == 'talk':
        for i, f in enumerate(fr):
            load(O + n + '/' + f).save(D + 'talk/%d.webp' % i, 'WEBP', quality=85, method=6)
        continue
    ims = [load(O + n + '/' + f) for f in fr]
    out = D + n + '.webp'
    ims[0].save(out, 'WEBP', save_all=True, append_images=ims[1:], duration=50, loop=0, quality=80, method=4)
    sz = os.path.getsize(out); tot += sz
    print(n, len(ims), sz // 1024, 'KB')
print('total', tot // 1024, 'KB')
