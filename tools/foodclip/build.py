"""
Builds MyFit's offline food recogniser (R18).

MobileCLIP image encoder → ONNX (int8 if it stays faithful, else fp32), plus one text embedding per catalogue dish
built from its name, aliases and the hand-written visual description used for its 3D icon. On the phone the photo's
embedding is compared with all 1,066 dish embeddings (cosine) — zero-shot, no per-dish training needed.

Outputs (out/): foodclip_image.onnx, dish_emb.bin (int32 n, int32 dim, then n*dim float16), dish_ids.json, meta.json
Sanity check: top-1 / top-5 accuracy on the app's own dish icons.
"""
import json, os, struct, sys, glob
import numpy as np
import torch, open_clip
from PIL import Image

ARCH = os.environ.get("ARCH", "MobileCLIP-S2")
TAG = os.environ.get("TAG", "datacompdr")
ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
OUT = os.path.join(ROOT, "out"); os.makedirs(OUT, exist_ok=True)

model, _, preprocess = open_clip.create_model_and_transforms(ARCH, pretrained=TAG)
model.eval()
tok = open_clip.get_tokenizer(ARCH)
foods = json.load(open(os.path.join(ROOT, "app/src/main/assets/foods_pk.json")))
desc = json.load(open(os.path.join(ROOT, "tools/foodicon/desc.json")))

# ---- text side: average of several prompts per dish
ids, embs = [], []
with torch.no_grad():
    for f in foods:
        name = f["name"].split("(")[0].strip()
        prompts = [f"a photo of {name}, a type of food.", f"a close-up photo of a plate of {name}."]
        prompts += [f"a photo of {a}, a Pakistani dish." for a in (f.get("aliases") or [])[:2]]
        if f["id"] in desc: prompts.append("a photo of " + desc[f["id"]])
        t = model.encode_text(tok(prompts))
        t = t / t.norm(dim=-1, keepdim=True)
        e = t.mean(0); e = e / e.norm()
        ids.append(f["id"]); embs.append(e.numpy())
E_ = np.stack(embs).astype(np.float32)
n, d = E_.shape
with open(os.path.join(OUT, "dish_emb.bin"), "wb") as fh:
    fh.write(struct.pack("<ii", n, d)); fh.write(E_.astype(np.float16).tobytes())
json.dump(ids, open(os.path.join(OUT, "dish_ids.json"), "w"))
print("text embeddings", n, d)

# ---- image side → ONNX
size = 256
for t in preprocess.transforms:
    if hasattr(t, "size"):
        s = t.size; size = s if isinstance(s, int) else s[0]; break
norm = [t for t in preprocess.transforms if t.__class__.__name__ == "Normalize"]
mean = list(map(float, norm[0].mean)) if norm else [0.0, 0.0, 0.0]
std = list(map(float, norm[0].std)) if norm else [1.0, 1.0, 1.0]

class Img(torch.nn.Module):
    def __init__(s, m): super().__init__(); s.m = m
    def forward(s, x):
        v = s.m.encode_image(x); return v / v.norm(dim=-1, keepdim=True)

wrapped = Img(model).eval()
try:
    from mobileclip.modules.common.mobileone import reparameterize_model  # optional
    wrapped = reparameterize_model(wrapped)
except Exception:
    pass
fp32 = os.path.join(OUT, "foodclip_fp32.onnx")
kw = dict(input_names=["pixels"], output_names=["embedding"], opset_version=17, do_constant_folding=True)
try:
    torch.onnx.export(wrapped, torch.randn(1, 3, size, size), fp32, dynamo=False, **kw)   # classic exporter: most compatible
except TypeError:
    torch.onnx.export(wrapped, torch.randn(1, 3, size, size), fp32, **kw)
print("onnx exported", os.path.getsize(fp32) // 1_000_000, "MB")

import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType
q8 = os.path.join(OUT, "foodclip_int8.onnx")
quantize_dynamic(fp32, q8, weight_type=QuantType.QUInt8)

def prep(path):
    im = Image.open(path).convert("RGB")
    w, h = im.size; sc = size / min(w, h)
    im = im.resize((max(size, round(w * sc)), max(size, round(h * sc))), Image.BICUBIC)
    w, h = im.size; l = (w - size) // 2; t = (h - size) // 2
    a = np.asarray(im.crop((l, t, l + size, t + size)), dtype=np.float32) / 255.0
    a = (a - np.array(mean, dtype=np.float32)) / np.array(std, dtype=np.float32)
    return a.transpose(2, 0, 1)[None]

icons = sorted(glob.glob(os.path.join(ROOT, "app/src/main/assets/foodicon/*.webp")))
idx = {k: i for i, k in enumerate(ids)}
icons = [p for p in icons if os.path.basename(p)[:-5] in idx][:400]

def evaluate(path_model):
    s = ort.InferenceSession(path_model, providers=["CPUExecutionProvider"])
    t1 = t5 = 0; vecs = []
    for p in icons:
        v = s.run(None, {"pixels": prep(p)})[0][0]; vecs.append(v)
        sims = E_ @ v; top = np.argsort(-sims)[:5]; gt = idx[os.path.basename(p)[:-5]]
        t1 += int(top[0] == gt); t5 += int(gt in top)
    return t1 / max(1, len(icons)), t5 / max(1, len(icons)), np.stack(vecs)

a1, a5, v32 = evaluate(fp32)
b1, b5, v8 = evaluate(q8)
cos = float(np.mean(np.sum(v32 * v8, axis=1)))
print(f"fp32 top1={a1:.3f} top5={a5:.3f} | int8 top1={b1:.3f} top5={b5:.3f} | cos={cos:.4f} | n={len(icons)}")
use_int8 = cos >= 0.97 and b5 >= a5 - 0.03
final = os.path.join(OUT, "foodclip_image.onnx")
os.replace(q8 if use_int8 else fp32, final)
for p in (fp32, q8):
    if os.path.exists(p): os.remove(p)
meta = {"arch": ARCH, "tag": TAG, "size": size, "mean": mean, "std": std, "dim": d, "count": n,
        "quant": "int8" if use_int8 else "fp32", "icon_top1": b1 if use_int8 else a1, "icon_top5": b5 if use_int8 else a5,
        "temperature": 100.0, "bytes": os.path.getsize(final)}
json.dump(meta, open(os.path.join(OUT, "meta.json"), "w"), indent=1)
print(json.dumps(meta))
