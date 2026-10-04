"""Find real, openly licensed photos for each program on Openverse (CC0 / public domain / CC BY) and save candidates.
out: cand/<id>_<k>.jpg, cand/meta.json, cand/sheet_<n>.jpg"""
import json, os, time, urllib.request, urllib.parse, io
from PIL import Image, ImageDraw
Q = json.load(open(os.path.join(os.path.dirname(__file__), 'queries.json')))
os.makedirs('cand', exist_ok=True)
meta = {}
UA = {'User-Agent': 'MyFitTracker-cover-picker/1.0 (github.com/Sohaib020/myfit-tracker)'}
def get(url, t=40):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=t).read()
for pid, q in Q.items():
    url = 'https://api.openverse.org/v1/images/?' + urllib.parse.urlencode({'q': q, 'license': 'cc0,pdm,by', 'size': 'large', 'page_size': 20, 'mature': 'false'})
    try: res = json.loads(get(url))['results']
    except Exception as e: print('ERR', pid, e); time.sleep(4); continue
    k = 0
    for r in res:
        if k >= 6: break
        w, h = r.get('width') or 0, r.get('height') or 0
        if w and h and (w < 900 or w / max(h, 1) < 1.05): continue
        try:
            im = Image.open(io.BytesIO(get(r['url'], 30))).convert('RGB')
        except Exception: continue
        if im.width < 800: continue
        im.thumbnail((1400, 1400)); im.save(f'cand/{pid}_{k}.jpg', quality=88)
        meta[f'{pid}_{k}'] = dict(title=r.get('title'), creator=r.get('creator'), license=r.get('license'), license_version=r.get('license_version'),
                                  landing=r.get('foreign_landing_url'), source=r.get('source'))
        k += 1
    print(pid, k, flush=True); time.sleep(3.2)
json.dump(meta, open('cand/meta.json', 'w'), indent=1)
ids = list(Q)
for n in range(0, len(ids), 9):
    sub = ids[n:n + 9]; W, H = 220, 128
    sheet = Image.new('RGB', (W * 6, (H + 14) * len(sub)), 'white'); d = ImageDraw.Draw(sheet)
    for r, pid in enumerate(sub):
        for k in range(6):
            p = f'cand/{pid}_{k}.jpg'
            if os.path.exists(p):
                im = Image.open(p); im.thumbnail((W, H)); sheet.paste(im, (k * W, r * (H + 14) + 14))
            d.text((k * W + 2, r * (H + 14)), f'{pid}_{k}', fill='black')
    sheet.save(f'cand/sheet_{n // 9}.jpg', quality=82)
print('done', len(meta))
