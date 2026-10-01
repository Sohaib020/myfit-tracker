"""Fetches each food's Wikipedia lead photo (CC-licensed, via Wikimedia Commons) into 256 px WebP
tiles plus a credits file, and renders a labelled contact sheet for review. Runs in CI."""
import json, os, re, sys, time, urllib.parse, urllib.request, io
from PIL import Image, ImageDraw, ImageFont

UA = {"User-Agent": "MyFitTracker/1.0 (https://github.com/Sohaib020/myfit-tracker; personal fitness app) python-urllib"}
foods = json.load(open("app/src/main/assets/foods_pk.json"))
out = "out/foodimg"; os.makedirs(out, exist_ok=True)

def get(url, binary=False):
    for i in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                b = r.read()
                return b if binary else json.loads(b)
        except Exception as e:
            if i == 3: raise
            time.sleep(1.5 * (i + 1))

def slug(t): return re.sub(r"[^a-z0-9]+", "_", t.lower()).strip("_")

def clean(html):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", html or "")).strip()[:120]

titles = sorted({f["wiki"] for f in foods if f.get("wiki")})
credits, failed = {}, []
for t in titles:
    s = slug(t)
    try:
        q = urllib.parse.urlencode({"action": "query", "format": "json", "formatversion": 2, "redirects": 1,
                                    "prop": "pageimages", "piprop": "original|name", "titles": t})
        page = get("https://en.wikipedia.org/w/api.php?" + q)["query"]["pages"][0]
        fname = page.get("pageimage")
        if not fname: failed.append((t, "no page image")); continue
        q2 = urllib.parse.urlencode({"action": "query", "format": "json", "formatversion": 2, "prop": "imageinfo",
                                     "iiprop": "url|extmetadata|mime", "iiurlwidth": 512, "titles": "File:" + fname})
        info = get("https://en.wikipedia.org/w/api.php?" + q2)["query"]["pages"][0]["imageinfo"][0]
        if not info.get("mime", "").startswith("image/") or info.get("mime") == "image/svg+xml":
            failed.append((t, "not a photo: " + info.get("mime", ""))); continue
        md = info.get("extmetadata", {})
        lic = md.get("LicenseShortName", {}).get("value", "")
        if not lic: failed.append((t, "no license info")); continue
        img = Image.open(io.BytesIO(get(info["thumburl"], binary=True))).convert("RGB")
        w, h = img.size; m = min(w, h)
        img = img.crop(((w - m) // 2, (h - m) // 2, (w - m) // 2 + m, (h - m) // 2 + m)).resize((256, 256), Image.LANCZOS)
        img.save(f"{out}/{s}.webp", "WEBP", quality=78, method=6)
        credits[s] = {"wiki": t, "file": fname, "artist": clean(md.get("Artist", {}).get("value")), "license": lic,
                      "url": info.get("descriptionurl", "")}
        time.sleep(0.15)
    except Exception as e:
        failed.append((t, str(e)[:80]))
json.dump(credits, open(f"{out}/credits.json", "w"), ensure_ascii=False, indent=0)
print("ok", len(credits), "failed", len(failed))
for f in failed: print("FAIL", *f)

# contact sheet: tile + wiki title + the food names that use it
keys = sorted(credits)
cols, tw, th = 8, 256, 300
rows = (len(keys) + cols - 1) // cols
sheet = Image.new("RGB", (cols * tw, max(1, rows) * th), (20, 20, 20))
d = ImageDraw.Draw(sheet)
try: font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15)
except Exception: font = ImageFont.load_default()
for i, k in enumerate(keys):
    x, y = (i % cols) * tw, (i // cols) * th
    sheet.paste(Image.open(f"{out}/{k}.webp"), (x, y))
    users = [f["name"] for f in foods if f.get("wiki") and slug(f["wiki"]) == k]
    d.text((x + 4, y + 258), credits[k]["wiki"][:30], fill=(255, 220, 120), font=font)
    d.text((x + 4, y + 278), (", ".join(users))[:32], fill=(220, 220, 220), font=font)
sheet.save("out/contact.jpg", quality=80)
open("out/failed.txt", "w").write("\n".join(f"{a}\t{b}" for a, b in failed))
