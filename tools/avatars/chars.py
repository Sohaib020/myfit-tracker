"""Character avatars from the bundled cast art: 20 buddy portraits + 16 Pip poses (middle frame), on soft gradients."""
import os, json, numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__)); AS = os.path.abspath(os.path.join(HERE, "../../app/src/main/assets"))
OUT = os.path.join(AS, "avatars"); TMP = os.path.join(HERE, "out")
BGS = [("#FFE28A","#FFB547"),("#B9F5D8","#5FD6A4"),("#BFE3FF","#6AAEFF"),("#E2D4FF","#A88BFF"),("#FFD1E6","#FF8DBB"),("#9FE8E2","#2FB8B0"),
       ("#FFB4A2","#FF7B6B"),("#E4FA9C","#A6E04B"),("#FFE0C2","#FFAE73"),("#4B5C9E","#1E2A5A")]
def hexc(h): return np.array([int(h[i:i+2],16) for i in (1,3,5)], float)
def grad(a, b, S=512):
    y,x = np.mgrid[0:S,0:S]; d = np.sqrt((x-.35*S)**2 + (y-.25*S)**2)/(0.9*S*1.1); d = np.clip(d,0,1)[...,None]
    return Image.fromarray((hexc(a)*(1-d) + hexc(b)*d).astype(np.uint8)).convert("RGBA")
def place(subj, bg, box_h=0.86, y_bottom=1.04):
    S = bg.size[0]; bb = subj.split()[3].getbbox(); subj = subj.crop(bb)
    h = int(S*box_h); w = int(subj.width*h/subj.height)
    if w > S*1.25: w = int(S*1.25); h = int(subj.height*w/subj.width)
    subj = subj.resize((w,h), Image.LANCZOS)
    out = bg.copy(); out.alpha_composite(subj, ((S-w)//2, int(S*y_bottom)-h)); return out
BUDDIES = ["motu","kami","chakor","taj","khargosh","zara","bhalu","lomri","shaheen","nevla","bulhan","kala","ullu","sakeen","bhoori","sehi","gogi","monal","mor","yaku"]
POSES = ["flex","cheer","celebrate","hearteyes","laugh","meditate","sleepy","jog","salute","shy","dance","letsgo","hydrate","peekaboo","highfive","love"]
ids = []
for i,b in enumerate(BUDDIES):
    p = f"{AS}/buddy/{b}/portrait.webp"
    if not os.path.exists(p): continue
    im = place(Image.open(p).convert("RGBA"), grad(*BGS[i % len(BGS)]), 1.14, 1.20)
    id = "ch_" + b; im.convert("RGB").resize((192,192), Image.LANCZOS).save(f"{OUT}/{id}.webp", quality=88, method=6); im.save(f"{TMP}/{id}.png"); ids.append(id)
for i,n in enumerate(POSES):
    p = f"{AS}/pip/{n}.webp"
    if not os.path.exists(p): continue
    a = Image.open(p); a.seek(int(a.n_frames*0.55)); fr = a.convert("RGBA")
    im = place(fr, grad(*BGS[(i+3) % len(BGS)]), 1.06, 1.08)
    id = "ch_pip_" + n; im.convert("RGB").resize((192,192), Image.LANCZOS).save(f"{OUT}/{id}.webp", quality=88, method=6); im.save(f"{TMP}/{id}.png"); ids.append(id)
cat = json.load(open(f"{OUT}/catalog.json")); cat = {"characters": [x for x in ids if x.startswith("ch_pip")] + [x for x in ids if not x.startswith("ch_pip")], **{k:v for k,v in cat.items() if k != "characters"}}
json.dump(cat, open(f"{OUT}/catalog.json","w"), indent=0)
T=96; cols=9; rows=(len(ids)+cols-1)//cols; sh=Image.new("RGB",(cols*(T+10)+10, rows*(T+10)+10),"#F2F3F1")
for k,id in enumerate(ids):
    im=Image.open(f"{TMP}/{id}.png").resize((T,T),Image.LANCZOS); m=Image.new("L",(T*4,T*4),0); ImageDraw.Draw(m).ellipse((0,0,T*4-1,T*4-1),fill=255)
    sh.paste(im,(10+(k%cols)*(T+10),10+(k//cols)*(T+10)),m.resize((T,T),Image.LANCZOS))
sh.save(f"{TMP}/sheet_chars.png"); print(len(ids), {k:len(v) for k,v in cat.items()})
