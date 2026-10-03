"""Background removal + crop/center + 192px WebP, and labelled contact sheets for review.
Reads raw/*.png, writes out/foodicon/*.webp, out/rawjpg/*.jpg, out/sheet_NN.jpg."""
import glob, json, os
from PIL import Image, ImageDraw, ImageFont
from rembg import remove, new_session
foods = {f["id"]: f for f in json.load(open("app/src/main/assets/foods_pk.json"))}
S = 192
os.makedirs("out/foodicon", exist_ok=True); os.makedirs("out/rawjpg", exist_ok=True)
sess = new_session(os.environ.get("REMBG_MODEL", "isnet-general-use"))
done = []
for p in sorted(glob.glob("raw/*.png")):
    i = os.path.basename(p)[:-4]
    im = Image.open(p).convert("RGB")
    im.save(f"out/rawjpg/{i}.jpg", quality=90)
    cut = remove(im, session=sess, post_process_mask=True)
    a = cut.getchannel("A").point(lambda v: 0 if v < 16 else v)
    cut.putalpha(a)
    bb = a.getbbox()
    if not bb:
        print("empty", i); continue
    cut = cut.crop(bb)
    w, h = cut.size; side = int(max(w, h) / 0.92)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(cut, ((side - w) // 2, (side - h) // 2))
    canvas = canvas.resize((S, S), Image.LANCZOS)
    canvas.save(f"out/foodicon/{i}.webp", "WEBP", quality=80, method=6, alpha_quality=85)
    done.append(i)
print("icons", len(done))

def sheets(ids, prefix):
    try: font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
    except Exception: font = ImageFont.load_default()
    C, R, cw, ch = 6, 5, 210, 252
    for n in range(0, len(ids), C * R):
        page = ids[n:n + C * R]
        sh = Image.new("RGB", (C * cw, R * ch), (236, 236, 238))
        d = ImageDraw.Draw(sh)
        for k, i in enumerate(page):
            x, y = (k % C) * cw, (k // C) * ch
            d.rectangle([x + 9, y + 4, x + 9 + 96, y + 196], fill=(38, 42, 50))  # dark half reveals halos
            ic = Image.open(f"out/foodicon/{i}.webp").convert("RGBA")
            sh.paste(ic, (x + 9, y + 4), ic)
            name = foods.get(i, {}).get("name", i)
            d.text((x + 6, y + 200), f"{n + k + 1}. {i}"[:32], fill=(170, 0, 0), font=font)
            d.text((x + 6, y + 217), name[:30], fill=(20, 20, 20), font=font)
            d.text((x + 6, y + 233), name[30:60], fill=(20, 20, 20), font=font)
        sh.save(f"out/{prefix}_{n // (C * R) + 1:02d}.jpg", quality=85)

sheets(sorted(done), "sheet")
