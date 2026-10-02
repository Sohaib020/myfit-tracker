"""Writes tools/foodicon/desc.json for EVERY food in foods_pk.json: one short, concrete visual description per
dish (what it looks like, colours, vessel, garnish) for the icon generator. Hand-written entries are kept.
Uses Gemini (GEMINI_API_KEY) in batches; anything it misses falls back to a name-based description."""
import json, os, sys, time, urllib.request, urllib.error
H = os.path.dirname(os.path.abspath(__file__))
foods = json.load(open("app/src/main/assets/foods_pk.json"))
desc = json.load(open(os.path.join(H, "desc.json")))
key = os.environ.get("GEMINI_API_KEY", "").strip()
examples = "\n".join(f'"{k}": "{v}"' for k, v in list(desc.items())[:5])
MODELS = ["gemini-2.5-flash-lite", "gemini-2.0-flash", "gemini-2.5-flash"]

def ask(batch):
    lines = "\n".join(f'- {f["id"]}: {f["name"]} (category: {f["category"]}; serving: {f["serving"]} {f["unit"]}; also called: {", ".join(f.get("aliases", [])[:4])})' for f in batch)
    prompt = f"""You write image-generation descriptions for food icons in a Pakistani fitness app.
For each food below write ONE description (25-45 words) of exactly how a typical serving LOOKS, so a 3D artist can draw it and people instantly recognise it:
the vessel (plate, bowl, glass, cup, paper wrap, skewer...), main shapes, real colours and textures, and the usual garnish. Be specific to Pakistani/South Asian versions where relevant.
Make every description visually distinct from the others (different vessel, colour or garnish where honest). No brand names, no text, no people.
Examples:
{examples}
Foods:
{lines}
Reply ONLY with a JSON object mapping each id to its description."""
    body = json.dumps({"contents": [{"role": "user", "parts": [{"text": prompt}]}],
                       "generationConfig": {"responseMimeType": "application/json", "temperature": 0.4, "thinkingConfig": {"thinkingBudget": 0}}}).encode()
    for m in MODELS:
        for attempt in range(3):
            try:
                req = urllib.request.Request(f"https://generativelanguage.googleapis.com/v1beta/models/{m}:generateContent?key={key}",
                                             data=body, headers={"Content-Type": "application/json"})
                with urllib.request.urlopen(req, timeout=90) as r:
                    d = json.load(r)
                txt = d["candidates"][0]["content"]["parts"][0]["text"]
                return json.loads(txt[txt.index("{"): txt.rindex("}") + 1])
            except urllib.error.HTTPError as e:
                print("http", m, e.code, flush=True)
                if e.code in (404, 400): break
                time.sleep(10 * (attempt + 1))
            except Exception as e:
                print("err", m, e, flush=True); time.sleep(5)
    return {}

todo = [f for f in foods if f["id"] not in desc]
print("to describe:", len(todo), flush=True)
if key:
    from concurrent.futures import ThreadPoolExecutor
    batches = [todo[n:n + 30] for n in range(0, len(todo), 30)]
    with ThreadPoolExecutor(5) as ex:
        for b, got in zip(batches, ex.map(ask, batches)):
            for f in b:
                v = got.get(f["id"])
                if isinstance(v, str) and len(v) > 20: desc[f["id"]] = v.strip()
            print(f"batch: {sum(1 for f in b if f['id'] in desc)}/{len(b)}", flush=True)
missing = [f["id"] for f in foods if f["id"] not in desc]
for f in foods:
    if f["id"] not in desc:
        desc[f["id"]] = f'{f["name"]}, a typical single serving ({f["serving"]} {f["unit"]}) as eaten in Pakistan, shown clearly so it is instantly recognisable'
print("fallback (review these):", missing)
json.dump(desc, open(os.path.join(H, "desc.json"), "w"), indent=0, ensure_ascii=False)
print("total", len(desc))
