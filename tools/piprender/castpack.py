import os, sys
from PIL import Image
O = sys.argv[1] + '/'; D = sys.argv[2] + '/'; os.makedirs(D, exist_ok=True)
STEP = {'cheer': 2, 'run': 2, 'sad': 3, 'wave': 2}
for who in sorted(os.listdir(O)):
    for clip in sorted(os.listdir(O + who)):
        fr = sorted(f for f in os.listdir(O + who + '/' + clip) if f.endswith('.png'))
        ims = [Image.open(O + who + '/' + clip + '/' + f).convert('RGBA') for f in fr]
        if clip == 'portrait':
            ims[0].resize((384, 384), Image.LANCZOS).save(D + who + '.webp', 'WEBP', quality=86, method=6); continue
        ims = [i.resize((288, 288), Image.LANCZOS) for i in ims]
        out = D + '%s_%s.webp' % (who, clip)
        ims[0].save(out, 'WEBP', save_all=True, append_images=ims[1:], duration=50 * STEP.get(clip, 2), loop=0, quality=78, method=4)
        print(out, len(ims), os.path.getsize(out) // 1024, 'KB')
