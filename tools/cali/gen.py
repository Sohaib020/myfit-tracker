"""Build calisthenics.json + new catalogue entries from data.py, and render a preview sheet.

  python3 tools/cali/gen.py [--preview out.png] [--write]
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from data import P, CHAINS, PLACEMENT
from figure import joints, full

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
KNOWN_MUSCLES = {'Glutes', 'Neck', 'Quadriceps', 'Biceps', 'Chest', 'Triceps', 'Forearms', 'Calves', 'Traps', 'Lats', 'Abductors',
                 'Abdominals', 'Shoulders', 'Hamstrings', 'Adductors', 'Lower Back', 'Middle Back'}


def layout(p, props):
    """Joint positions plus prop geometry in body units (same rules as CaliFigure.kt)."""
    J = joints(p)
    pts = list(J.values())
    hang = ("bar" in props or "rings" in props) and "floor" not in props
    shapes = []
    floor = max(y for _, y in pts) + (0.0 if True else 0)
    # head bottom counts for floor contact
    floor = max(floor, J["head"][1] + 0.105)
    if "bar" in props:
        wx, wy = J["wr1"]
        shapes.append(("bar", wx, wy))
    if "rings" in props:
        wx, wy = J["wr1"]; shapes.append(("rings", wx, wy))
    if "pbars" in props:
        wx, wy = J["wr1"]; floor = max(floor, wy + 0.95)
        shapes.append(("pbars", wx, wy))
    if "plt" in props:
        wx, wy = J["wr1"]; shapes.append(("plt", wx, wy)); floor = wy + 0.06
    if "band" in props:
        shapes.append(("band", J["wr1"], J["kn1"]))
    if hang:
        floor = None
    for b, jn in (("box_h", "wr1"), ("box_f", "to1"), ("box_f2", "an2"), ("box_hip", "hip")):
        if b in props:
            x, y = J[jn]
            if b == "box_hip": shapes.append(("boxat", J["an1"][0] - 0.38, 0.45))   # fixed seat behind the standing foot
            else: shapes.append(("box", x, y))
    if floor is not None and any(s[0] == "box" for s in shapes):
        # with a box, the floor sits at the lowest non-boxed contact
        boxed = {"box_h": ["wr1", "wr2"], "box_f": ["to1", "to2", "an1", "an2"], "box_f2": ["an2", "to2", "kn2"]}
        skip = {j for b, js in boxed.items() if b in props for j in js}
        floor = max(y for k, (x, y) in J.items() if k not in skip)
    if "wall_f" in props:
        shapes.append(("wall", max(x for x, _ in pts) + 0.02))
    if "wall_b" in props:
        shapes.append(("wall", min(x for x, _ in pts) - 0.02))
    return J, shapes, floor


def render(path):
    from PIL import Image, ImageDraw
    steps = [(c, s) for c in CHAINS for s in c["steps"]]
    cw, ch = 300, 260; cols = 6
    rows = (len(steps) + cols - 1) // cols
    img = Image.new("RGB", (cw * cols, ch * rows), (14, 16, 22))
    dr = ImageDraw.Draw(img)
    for n, (c, s) in enumerate(steps):
        ox, oy = (n % cols) * cw, (n // cols) * ch
        col = tuple(int(c["color"][i:i + 2], 16) for i in (1, 3, 5))
        dr.text((ox + 6, oy + 4), f'{c["id"]} {n}: {s["n"]}', fill=(220, 220, 230))
        for fi, pk in enumerate(s["p"]):
            J, shapes, floor = layout(P[pk], s["pr"])
            xs = [x for x, _ in J.values()]; ys = [y for _, y in J.values()]
            minx, maxx = min(xs) - 0.2, max(xs) + 0.2
            miny, maxy = min(ys) - 0.25, (floor if floor is not None else max(ys)) + 0.08
            sc = min((cw / 2 - 10) / (maxx - minx), (ch - 30) / (maxy - miny))
            bx = ox + fi * cw / 2 + 5; by = oy + 22
            T = lambda x, y: (bx + (x - minx) * sc, by + (y - miny) * sc)
            if floor is not None: dr.line([T(minx, floor), T(maxx, floor)], fill=(80, 80, 95), width=2)
            for sh in shapes:
                if sh[0] == "bar": x, y = T(sh[1], sh[2]); dr.ellipse([x - 5, y - 5, x + 5, y + 5], outline=(200, 200, 210), width=2)
                if sh[0] == "rings": x, y = T(sh[1], sh[2]); dr.ellipse([x - 7, y - 7, x + 7, y + 7], outline=(200, 170, 120), width=2); dr.line([x, y - 7, x, by], fill=(150, 150, 150))
                if sh[0] == "pbars": x, y = T(sh[1], sh[2]); dr.line([x - 30, y, x + 30, y], fill=(200, 200, 210), width=4); dr.line([x, y, x, T(0, floor)[1]], fill=(150, 150, 160), width=3)
                if sh[0] == "plt": x, y = T(sh[1], sh[2]); dr.line([x - 12, y, x + 12, y], fill=(200, 200, 210), width=4)
                if sh[0] == "box": x, y = T(sh[1], sh[2]); fy = T(0, floor)[1] if floor else y + 40; dr.rectangle([x - 28, min(y, fy), x + 28, max(y, fy)], outline=(150, 150, 160), width=2)
                if sh[0] == "boxat": x, _ = T(sh[1], 0); fy = T(0, floor)[1]; dr.rectangle([x - 28, fy - sh[2] * sc, x + 28, fy], outline=(150, 150, 160), width=2)
                if sh[0] == "wall": x, _ = T(sh[1], 0); dr.line([x, by, x, T(0, floor)[1] if floor else by + ch - 30], fill=(150, 150, 160), width=4)
                if sh[0] == "band": a, b = T(*sh[1]), T(*sh[2]); dr.line([a, b], fill=(255, 120, 120), width=2)
            far = tuple(int(v * 0.5) for v in col)
            def seg(a, b, w, color): dr.line([T(*J[a]), T(*J[b])], fill=color, width=w)
            for i, colr in (("2", far), ("1", col)):
                seg("hip", "kn" + i, 9, colr); seg("kn" + i, "an" + i, 8, colr); seg("an" + i, "to" + i, 6, colr)
                if i == "2": seg("sh", "el2", 7, colr); seg("el2", "wr2", 6, colr)
                if i == "1": seg("hip", "sh", 14, col)
            seg("sh", "el1", 7, (255, 255, 255)); seg("el1", "wr1", 6, (255, 255, 255))
            hx, hy = T(*J["head"]); r = 0.105 * sc
            dr.ellipse([hx - r, hy - r, hx + r, hy + r], fill=col)
    img.save(path)


def write():
    out = {"chains": [], "placement": {k: {"by": v[0], "at": [[a, b] for a, b in v[1]]} for k, v in PLACEMENT.items()}, "poses": {}}
    used = set()
    for c in CHAINS:
        cc = {k: c[k] for k in ("id", "name", "sub", "color")}
        cc["steps"] = [{k: s[k] for k in ("k", "n", "t", "start", "goal", "eq", "pr", "p", "cue")} for s in c["steps"]]
        for s in c["steps"]: used.update(s["p"])
        out["chains"].append(cc)
    for k in sorted(used):
        f = full(P[k]); out["poses"][k] = [f[x] for x in ("t", "hd", "a1", "f1", "a2", "f2", "l1", "s1", "l2", "s2", "ft1", "ft2")]
    with open(os.path.join(ASSETS, "calisthenics.json"), "w") as fh:
        json.dump(out, fh, ensure_ascii=False, separators=(",", ":"))
    cat_path = os.path.join(ASSETS, "exercise_catalog.json")
    cat = json.load(open(cat_path))
    have = {x["k"] for x in cat}
    for c in CHAINS:
        for s in c["steps"]:
            if not s["k"].startswith("cali_"):
                assert s["k"] in have, s["k"]; continue
            e = {"k": s["k"], "n": s["n"], "g": s["g"], "pm": s["pm"], "s": [m for m in s["s"] if m in KNOWN_MUSCLES and m != s["pm"]],
                 "e": "body only" if s["eq"] in ("None", "Wall", "Open floor") else "other",
                 "m": "DURATION" if s["t"] == "hold" else "BODYWEIGHT_REPS", "c": "strength", "l": s["l"],
                 "mc": "compound", "f": "static" if s["t"] == "hold" else ("pull" if c["id"] in ("pull", "row", "hinge") else "push"),
                 "i": s["i"], "fr": 0}
            cat = [x for x in cat if x["k"] != s["k"]] + [e]
    with open(cat_path, "w") as fh:
        json.dump(cat, fh, ensure_ascii=False, separators=(",", ":"))
    print("chains", len(out["chains"]), "steps", sum(len(c["steps"]) for c in out["chains"]), "catalog", len(cat))


if __name__ == "__main__":
    a = sys.argv[1:]
    if "--preview" in a: render(a[a.index("--preview") + 1])
    if "--write" in a: write()
