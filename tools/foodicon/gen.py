"""Generates raw 512px food icon renders with FLUX.1-schnell via stable-diffusion.cpp for one shard.
usage: gen.py <shard> <nshards> <sd-binary> <models-dir>"""
import json, os, subprocess, sys, time, zlib
shard, nshards, sd, models = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3], sys.argv[4]
H = os.path.dirname(os.path.abspath(__file__))
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
for i in ids:
    d = desc[i]
    prompt = STYLE["prefix"] + d + STYLE["suffix"]
    seed = seeds.get(i, zlib.crc32(i.encode()) % 100000)
    t = time.time()
    size = str(STYLE.get("size", 512))
    cmd = [sd, "--diffusion-model", f"{models}/flux.gguf", "--vae", f"{models}/ae.safetensors",
           "--clip_l", f"{models}/clip_l.safetensors", "--t5xxl", f"{models}/t5xxl.gguf",
           "-p", prompt, "--cfg-scale", "1.0", "--sampling-method", "euler", "--steps", str(STYLE.get("steps", 4)),
           "-W", size, "-H", size, "--seed", str(seed), "-t", str(os.cpu_count()), "-o", f"raw/{i}.png"]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0 or not os.path.exists(f"raw/{i}.png"):
        print("FAIL", i, r.stdout[-2000:], r.stderr[-2000:], flush=True)
    else:
        print(f"ok {i} seed={seed} {time.time()-t:.0f}s", flush=True)
