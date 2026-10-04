"""Real photo covers for every program from a free stock library, picked automatically.
Uses PIXABAY_API_KEY (preferred) or PEXELS_API_KEY from the environment (GitHub secrets); falls back to Openverse.
out: out/programs/<id>.webp (768x448), out/credits.json, out/sheet.jpg (+ cand/ for alternatives)"""
import json, os, time, urllib.request, urllib.parse, io
from PIL import Image, ImageDraw, ImageOps
Q = json.load(open(os.path.join(os.path.dirname(__file__), 'queries.json')))
PIX, PEX = os.environ.get('PIXABAY_API_KEY', '').strip(), os.environ.get('PEXELS_API_KEY', '').strip()
os.makedirs('out/programs', exist_ok=True); os.makedirs('cand', exist_ok=True)
UA = {'User-Agent': 'MyFitTracker-covers/1.0 (github.com/Sohaib020/myfit-tracker)'}
def get(url, headers=None, t=40):
    return urllib.request.urlopen(urllib.request.Request(url, headers={**UA, **(headers or {})}), timeout=t).read()

def search(q):
    """[(image_url, credit dict)] best first"""
    if PIX:
        out = []
        for cat in ('sports', 'health', ''):
            args = {'key': PIX, 'q': q, 'image_type': 'photo', 'orientation': 'horizontal', 'per_page': 12, 'safesearch': 'true', 'min_width': 1200, 'order': 'popular'}
            if cat: args['category'] = cat
            r = json.loads(get('https://pixabay.com/api/?' + urllib.parse.urlencode(args)))
            out += [(h['largeImageURL'], dict(source='Pixabay', author=h.get('user'), page=h.get('pageURL'), license='Pixabay Content License', id=h.get('id'))) for h in r.get('hits', [])]
            if len(out) >= 6: break
        return out
    if PEX:
        r = json.loads(get('https://api.pexels.com/v1/search?' + urllib.parse.urlencode({'query': q, 'orientation': 'landscape', 'per_page': 12}), {'Authorization': PEX}))
        return [(p['src']['large2x'], dict(source='Pexels', author=p.get('photographer'), page=p.get('url'), license='Pexels License')) for p in r.get('photos', [])]
    r = json.loads(get('https://api.openverse.org/v1/images/?' + urllib.parse.urlencode({'q': q, 'license': 'cc0,pdm,by', 'size': 'large', 'page_size': 12})))
    return [(x['url'], dict(source='Openverse/' + (x.get('source') or ''), author=x.get('creator'), page=x.get('foreign_landing_url'), license=(x.get('license') or '') + ' ' + (x.get('license_version') or ''))) for x in r.get('results', [])]

credits, thumbs = {}, []
used = set()
for pid, q in Q.items():
    try: res = search(q)
    except Exception as e: print('ERR', pid, e); time.sleep(5); continue
    picked = None
    for k, (url, cr) in enumerate(res[:8]):
        key = cr.get('id') or url
        if key in used: continue
        try: im = Image.open(io.BytesIO(get(url, t=40))).convert('RGB')
        except Exception: continue
        if im.width < 900 or im.width / im.height < 1.15: continue
        if picked is None:
            picked = (im, cr); used.add(key)
        if k < 4: im.copy().resize((240, int(240 * im.height / im.width))).save(f'cand/{pid}_{k}.jpg', quality=80)
    if picked is None: print('none', pid); continue
    im, cr = picked
    cover = ImageOps.fit(im, (768, 448), Image.LANCZOS, centering=(0.5, 0.42))
    cover.save(f'out/programs/{pid}.webp', 'WEBP', quality=80, method=6)
    credits[pid] = cr
    t = cover.resize((256, 149)); ImageDraw.Draw(t).text((4, 4), pid, fill=(255, 255, 0)); thumbs.append(t)
    print('ok', pid, cr['source'], flush=True)
    time.sleep(1.2 if (PIX or PEX) else 3.5)
json.dump(credits, open('out/credits.json', 'w'), indent=1)
cols = 4; rows = (len(thumbs) + cols - 1) // cols
sheet = Image.new('RGB', (cols * 256, max(1, rows) * 149), 'white')
for i, t in enumerate(thumbs): sheet.paste(t, ((i % cols) * 256, (i // cols) * 149))
sheet.save('out/sheet.jpg', quality=82)
print('covers', len(credits), '/', len(Q))
