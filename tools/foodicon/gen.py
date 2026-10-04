"""Generates raw 512px food icon renders with FLUX.1-schnell via stable-diffusion.cpp for one shard.
usage: gen.py <shard> <nshards> <sd-binary> <models-dir>"""
import json, os, subprocess, sys, time, zlib
shard, nshards, sd, models = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3], sys.argv[4]
H = os.environ.get("ICON_DIR") or os.path.dirname(os.path.abspath(__file__))
def load(n, default):
    p = os.path.join(H, n)
    return json.load(open(p)) if os.path.exists(p) else default
desc = load("desc.json", {})
seeds = load("seeds.json", {})
STYLE = load("style.json", {})
only = []
if os.path.exists(os.path.join(H, "only.txt")):
    only = [l.strip() for l in open(os.path.join(H, "only.txt")) if l.strip() and not l.startswith("#")]
ids = only or sorted(desc)
ids = [i for k, i in enumerate(ids) if k % nshards == shard]
os.makedirs("raw", exist_ok=True)
print("shard", shard, "items", len(ids), flush=True)
fails = 0
BUDGET = float(os.environ.get("BUDGET_MIN", "150")) * 60
start = time.time()
for n, i in enumerate(ids):
    if os.path.exists(f"raw/{i}.png"): continue
    if time.time() - start > BUDGET:
        print(f"::warning::shard {shard} stopped at the time budget after {n} items", flush=True); break
    d = desc[i]
    prompt = STYLE["prefix"] + d + STYLE["suffix"]
    seed = seeds.get(i, zlib.crc32(i.encode()) % 100000)
    t = time.time()
    size = str(STYLE.get("size", 512)); wd = str(STYLE.get("w", size)); ht = str(STYLE.get("h", size))
    cmd = [sd, "--diffusion-model", f"{models}/flux.gguf", "--vae", f"{models}/ae.safetensors",
           "--clip_l", f"{models}/clip_l.safetensors", "--t5xxl", f"{models}/t5xxl.gguf",
           "-p", prompt, "--cfg-scale", "1.0", "--sampling-method", "euler", "--steps", str(STYLE.get("steps", 4)),
           "-W", wd, "-H", ht, "--vae-tiling", "--seed", str(seed), "-t", str(max(1, (os.cpu_count() or 4) - 1)), "-o", f"raw/{i}.png"]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0 or not os.path.exists(f"raw/{i}.png"):
        print("FAIL", i, r.stdout[-2000:], r.stderr[-2000:], flush=True)
        fails += 1
        if fails <= 2:  # annotations are readable without log access
            msg = (r.stderr[-700:] or r.stdout[-700:]).replace("\n", " | ")
            print(f"::error::shard {shard} {i}: {msg}", flush=True)
        if fails >= 3 and n == fails - 1:
            sys.exit("first three items failed — stopping this shard")
    else:
        dt = time.time() - t
        if n == 0: print(f"::notice::shard {shard} first icon {i} took {dt:.0f}s", flush=True)
        print(f"ok {i} seed={seed} {dt:.0f}s", flush=True)
