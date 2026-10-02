"""Downloads FLUX.1-schnell (Apache-2.0) GGUF weights + encoders for stable-diffusion.cpp."""
import json, os, re, sys, urllib.request, subprocess
D = sys.argv[1] if len(sys.argv) > 1 else "models"; os.makedirs(D, exist_ok=True)

def files(repo):
    try:
        with urllib.request.urlopen(f"https://huggingface.co/api/models/{repo}", timeout=60) as r:
            return [s["rfilename"] for s in json.load(r)["siblings"]]
    except Exception as e:
        print("list fail", repo, e); return []

def pick(cands, name):
    dst = os.path.join(D, name)
    if os.path.exists(dst) and os.path.getsize(dst) > 1000000:
        print("have", name); return
    for repo, pat in cands:
        fs = files(repo)
        print(repo, "->", [f for f in fs if not f.endswith(('.md', '.json', '.gitattributes'))][:40])
        m = [f for f in fs if re.search(pat, f, re.I)]
        if m:
            f = sorted(m, key=len)[0]
            url = f"https://huggingface.co/{repo}/resolve/main/{f}"
            print("download", url, flush=True)
            if subprocess.call(["curl", "-fL", "--retry", "5", "-sS", "-o", dst, url]) == 0:
                print(name, os.path.getsize(dst)); return
    sys.exit(f"no file for {name}")

pick([("leejet/FLUX.1-schnell-gguf", r"q4_0\.gguf$"), ("city96/FLUX.1-schnell-gguf", r"Q4_0\.gguf$"),
      ("second-state/FLUX.1-schnell-GGUF", r"schnell.*Q4_0\.gguf$")], "flux.gguf")
pick([("second-state/FLUX.1-schnell-GGUF", r"^ae\.safetensors$"), ("black-forest-labs/FLUX.1-schnell", r"^ae\.safetensors$")], "ae.safetensors")
pick([("comfyanonymous/flux_text_encoders", r"^clip_l\.safetensors$"), ("second-state/FLUX.1-schnell-GGUF", r"^clip_l\.safetensors$")], "clip_l.safetensors")
pick([("second-state/FLUX.1-schnell-GGUF", r"t5xxl.*Q5_0\.gguf$"), ("second-state/FLUX.1-schnell-GGUF", r"t5xxl.*Q8_0\.gguf$"),
      ("city96/t5-v1_1-xxl-encoder-gguf", r"Q5_K_M\.gguf$")], "t5xxl.gguf")
