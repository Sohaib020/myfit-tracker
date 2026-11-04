"""
R26 clip video: RGBA frames -> one H.264 MP4 with the transparency packed beside the colour ("side-by-side alpha").
  left half  = colour, premultiplied over black
  right half = alpha as grey
The app (AlphaVideoView) samples both halves in a shader and draws the character with real transparency. H.264 stores
only what changes between frames, so 720p/50 fps costs ~1/3 of an animated WebP and the phone's video chip decodes it.

  from vid import encode;  encode(frames, out_mp4, fps, box=None, size=None)
"""
import subprocess
from PIL import Image

CRF = 21
MAX_W, MAX_H = 1920, 1280
MAX_PX = 1920 * 1080


def _even(v): return max(2, int(v) // 2 * 2)


def encode(frames, out, fps, box=None, size=None, crf=CRF):
    """frames: list of PNG paths (RGBA). box: crop (l, t, r, b). size: (w, h) output size of the character."""
    first = Image.open(frames[0])
    if box is None: box = (0, 0) + first.size
    if size is None: size = (box[2] - box[0], box[3] - box[1])
    w, h = size
    # stay inside what every phone's decoder takes: 1920 wide, 1280 tall and no more pixels than 1080p (8160 macroblocks)
    k = min(1.0, MAX_W / (2.0 * w), MAX_H / float(h), (MAX_PX / (2.0 * w * h)) ** 0.5)
    w, h = _even(w * k), _even(h * k)
    cmd = ['ffmpeg', '-loglevel', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{w * 2}x{h}', '-r', str(fps), '-i', '-',
           '-c:v', 'libx264', '-preset', 'slow', '-crf', str(crf), '-pix_fmt', 'yuv420p', '-profile:v', 'high', '-level', '4.2',
           '-g', str(int(fps * 2)), '-bf', '2', '-movflags', '+faststart', '-an', out]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for f in frames:
        im = Image.open(f).convert('RGBA').crop(box).resize((w, h), Image.LANCZOS)
        a = im.getchannel('A')
        col = Image.new('RGB', (w, h), (0, 0, 0)); col.paste(im.convert('RGB'), mask=a)
        sbs = Image.new('RGB', (w * 2, h)); sbs.paste(col, (0, 0)); sbs.paste(Image.merge('RGB', (a, a, a)), (w, 0))
        p.stdin.write(sbs.tobytes())
    p.stdin.close()
    if p.wait() != 0: raise RuntimeError('ffmpeg failed for ' + out)
    return w, h
