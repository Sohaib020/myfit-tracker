import os, sys
from PIL import Image
O = sys.argv[1] + '/'; D = sys.argv[2] + '/'; SIZE = 448
def load(p):
    im = Image.open(p).convert('RGBA'); return im.resize((SIZE, SIZE), Image.LANCZOS)
for n in sorted(os.listdir(O)):
    fr = sorted(f for f in os.listdir(O + n) if f.endswith('.png'))
    if n == 'look':
        os.makedirs(D + 'look', exist_ok=True)
        for f in fr: load(O + n + '/' + f).save(D + 'look/' + f[:-4] + '.webp', 'WEBP', quality=82, method=4)
        continue
    ims = [load(O + n + '/' + f) for f in fr]
    ims[0].save(D + n + '.webp', 'WEBP', save_all=True, append_images=ims[1:], duration=50, loop=0, quality=80, method=4)
    print(n, len(ims), os.path.getsize(D + n + '.webp') // 1024, 'KB')
