"""Pakistani 7-day meal plans in household units, built from assets/foods_pk.json.

Each day template lists meals with items (food id, quantity in the catalog's serving, step size, min, max).
For every calorie band the generator nudges the adjustable portions (roti, rice, curry, daal, fruit, nuts) in
household-sized steps until the day lands within +-60 kcal of the band. Output: assets/meal_plans.json.
"""
import json, os
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
F = {x['id']: x for x in json.load(open(os.path.join(ROOT, 'app/src/main/assets/foods_pk.json')))}

# (id, qty, step, min, max)  qty in "servings" of the catalog entry; step 0 = fixed
def I(i, q, step=0, lo=None, hi=None): return [i, q, step, q if lo is None else lo, q if hi is None else hi]

DAYS = [
  {"name": "Day 1", "meals": {
    "BREAKFAST": [I("omelette", 1), I("roti", 1, 1, 1, 3), I("tea_sugar", 1)],
    "LUNCH": [I("daal_masoor", 1, 0.5, 0.5, 1.5), I("roti", 2, 1, 1, 6), I("salad", 1), I("raita", 1)],
    "SNACK": [I("guava", 1), I("ublay_chanay", 0.5, 0.5, 0, 1), I("kachi_lassi", 1)],
    "DINNER": [I("chicken_karahi", 0.5, 0.5, 0.5, 1.5), I("roti", 1, 1, 1, 5), I("salad", 1)]}},
  {"name": "Day 2", "meals": {
    "BREAKFAST": [I("anda_channay", 0.5, 0.5, 0.5, 1), I("roti", 1, 1, 1, 3), I("tea_sugar", 1)],
    "LUNCH": [I("chana_masala", 1, 0.5, 0.5, 1.5), I("rice_white", 1, 0.5, 0.5, 3), I("salad", 1)],
    "SNACK": [I("yogurt", 1), I("dates", 1, 1, 0, 2), I("peanuts", 0, 0.5, 0, 1.5)],
    "DINNER": [I("fish_curry", 1, 0.5, 0.5, 1.5), I("phulka", 2, 1, 1, 6), I("bhindi", 0.5, 0.5, 0, 1)]}},
  {"name": "Day 3", "meals": {
    "BREAKFAST": [I("daliya", 1, 0.5, 0.5, 1.5), I("egg_boiled", 1, 1, 1, 3), I("guava", 1)],
    "LUNCH": [I("chicken_handi", 0.5, 0.5, 0.5, 1.5), I("roti", 2, 1, 1, 6), I("salad", 1)],
    "SNACK": [I("chaat", 0.5, 0.5, 0.5, 1), I("lassi_salty", 0, 1, 0, 1)],
    "DINNER": [I("daal_moong", 1, 0.5, 0.5, 1.5), I("lauki", 0.5, 0.5, 0, 1), I("roti", 1, 1, 1, 5), I("raita", 1)]}},
  {"name": "Day 4", "meals": {
    "BREAKFAST": [I("paratha_plain", 0.5, 0.5, 0.5, 1), I("omelette", 1), I("tea_sugar", 1)],
    "LUNCH": [I("lobia", 1, 0.5, 0.5, 1.5), I("rice_white", 1, 0.5, 0.5, 3), I("salad", 1)],
    "SNACK": [I("orange", 1), I("peanuts", 0.5, 0.5, 0, 1.5), I("milk_whole", 0, 1, 0, 1)],
    "DINNER": [I("chicken_tikka", 1, 1, 1, 2), I("roti", 1, 1, 1, 5), I("raita", 1), I("salad", 1)]}},
  {"name": "Day 5", "meals": {
    "BREAKFAST": [I("yogurt", 1), I("egg_boiled", 2, 1, 1, 3), I("roti", 1, 1, 1, 2), I("tea_sugar", 1)],
    "LUNCH": [I("keema_matar", 1, 0.5, 0.5, 1.5), I("roti", 2, 1, 1, 6), I("salad", 1)],
    "SNACK": [I("sattu", 1), I("almonds", 0, 0.5, 0, 1), I("banana", 0, 1, 0, 1)],
    "DINNER": [I("saag", 0.5, 0.5, 0.5, 1), I("daal_mash", 0.5, 0.5, 0.5, 1), I("phulka", 2, 1, 1, 6)]}},
  {"name": "Day 6", "meals": {
    "BREAKFAST": [I("egg_boiled", 2, 1, 2, 3), I("roti", 1, 1, 1, 3), I("milk_low", 1)],
    "LUNCH": [I("chana_pulao", 1, 0.5, 0.5, 1.5), I("raita", 1), I("salad", 1)],
    "SNACK": [I("banana", 1), I("almonds", 0.5, 0.5, 0, 1.5), I("lassi_salty", 0, 1, 0, 1)],
    "DINNER": [I("chicken_boti", 1, 0.5, 1, 2), I("roti", 1, 1, 1, 5), I("salad", 1)]}},
  {"name": "Day 7", "meals": {
    "BREAKFAST": [I("anda_paratha", 0.5, 0.5, 0.5, 1), I("tea_sugar", 1)],
    "LUNCH": [I("chicken_biryani", 0.5, 0.5, 0.5, 1.5), I("raita", 1), I("salad", 1)],
    "SNACK": [I("guava", 1), I("yogurt", 1), I("gur_chanay", 0, 0.5, 0, 1)],
    "DINNER": [I("daal_chana", 1, 0.5, 0.5, 1.5), I("baingan_bharta", 0.5, 0.5, 0, 1), I("roti", 1, 1, 1, 5)]}},
]
BANDS = [1400, 1600, 1800, 2000, 2200, 2500, 2800, 3200]
# when adding, prefer carbs/protein portions; when trimming, prefer extras first
ADD_ORDER = ["roti", "phulka", "rice_white", "chicken_karahi", "chicken_handi", "chicken_tikka", "chicken_boti", "fish_curry",
             "keema_matar", "daal_masoor", "daal_moong", "daal_chana", "chana_masala", "lobia", "egg_boiled", "almonds", "peanuts",
             "dates", "banana", "ublay_chanay", "paratha_plain", "anda_paratha", "anda_channay", "chana_pulao", "chicken_biryani", "daliya", "saag", "daal_mash", "lauki",
             "bhindi", "baingan_bharta", "chaat", "milk_whole", "lassi_salty", "gur_chanay"]

def kcal(items): return sum(F[i]['kcal'] * q for i, q, *_ in items)
def prot(items): return sum(F[i]['p'] * q for i, q, *_ in items)

def fit(day, target):
    meals = {m: [list(x) for x in items] for m, items in day["meals"].items()}
    allitems = [x for m in meals.values() for x in m]
    for _ in range(200):
        k = kcal(allitems)
        if abs(k - target) <= 60: break
        up = k < target
        cands = [x for x in allitems if x[2] > 0 and (x[1] + x[2] <= x[4] + 1e-9 if up else x[1] - x[2] >= x[3] - 1e-9)]
        if not cands: break
        order = ADD_ORDER if up else list(reversed(ADD_ORDER))
        cands.sort(key=lambda x: order.index(x[0]) if x[0] in order else 99)
        # pick the candidate whose step gets closest to target, among the first few in priority
        best = min(cands[:6], key=lambda x: abs((k + (1 if up else -1) * F[x[0]]['kcal'] * x[2]) - target))
        best[1] = round(best[1] + (best[2] if up else -best[2]), 2)
        # spread: rotate so the same item isn't always grown
        ADD_ORDER.append(ADD_ORDER.pop(ADD_ORDER.index(best[0]))) if up and best[0] in ADD_ORDER else None
    return meals

UNIT = {"piece": ("", ""), "bowl": ("katori", "katori"), "katori": ("katori", "katori"), "skewer": ("seekh", "seekhs"), "cup": ("cup", "cups"), "plate": ("plate", "plates"), "glass": ("glass", "glasses"),
        "medium": ("", ""), "large": ("", ""), "serving": ("serving", "servings"), "slice": ("slice", "slices"), "tbsp": ("tbsp", "tbsp"), "scoop": ("scoop", "scoops")}

def qty_text(q):
    whole = int(q); frac = q - whole
    f = {0.5: "½", 0.25: "¼", 0.75: "¾"}.get(round(frac, 2), "")
    return (str(whole) if whole else "") + f if (whole or f) else str(q)

def label(i, q):
    x = F[i]; n = q * x['serving']; u = x['unit']
    name = x['name'].split(' (')[0].replace("Orange / kinnow", "Kinnow")
    if u == "g": return f"{int(round(n))} g {name.lower()}"
    if u == "pieces": return f"{qty_text(n)} {name.lower()}"
    s, p = UNIT.get(u, (u, u + "s"))
    PL = {"egg": "eggs", "banana": "bananas", "apple": "apples", "guava": "guavas"}
    nm = name.lower()
    if not s: return f"{qty_text(n)} {PL.get(nm, nm) if n > 1 else nm}"
    return f"{qty_text(n)} {s if n <= 1 else p} {name.lower()}"

out = {"note": "Typical home portions. 1 katori ≈ 200–250 g; 1 cup cooked rice ≈ 160 g; roti ≈ 40 g (6-inch).", "plans": []}
for b in BANDS:
    days = []
    for d in DAYS:
        meals = fit(d, b)
        md = []
        for m, items in meals.items():
            its = [{"id": i, "q": q, "label": label(i, q), "kcal": round(F[i]['kcal'] * q), "p": round(F[i]['p'] * q, 1)} for i, q, *_ in items if q > 0]
            md.append({"type": m, "items": its, "kcal": sum(x["kcal"] for x in its), "p": round(sum(x["p"] for x in its), 1)})
        days.append({"name": d["name"], "meals": md, "kcal": sum(m["kcal"] for m in md), "p": round(sum(m["p"] for m in md), 1)})
    out["plans"].append({"kcal": b, "days": days})
    print(b, [dd["kcal"] for dd in days], [dd["p"] for dd in days])
json.dump(out, open(os.path.join(ROOT, 'app/src/main/assets/meal_plans.json'), 'w'), ensure_ascii=False, separators=(',', ':'))
