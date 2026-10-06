import os, re, glob, xml.etree.ElementTree as ET, cairosvg
from PIL import Image
A="{http://schemas.android.com/apk/res/android}"
D="../../app/src/main/res/drawable"; OUT="out"; os.makedirs(OUT, exist_ok=True)
def conv(el):
    s=""
    for c in el:
        t=c.tag
        if t=="path":
            g=lambda k,d=None: c.get(A+k,d)
            s+=f'<path d="{g("pathData")}" fill="{g("fillColor","none")}" fill-opacity="{g("fillAlpha","1")}" stroke="{g("strokeColor","none")}" stroke-width="{g("strokeWidth","0")}" stroke-opacity="{g("strokeAlpha","1")}" stroke-linecap="round" stroke-linejoin="round"/>'
        elif t=="group":
            g=lambda k,d="0": float(c.get(A+k,d))
            px,py=g("pivotX"),g("pivotY"); sc=g("scaleX","1")
            tr=f'translate({g("translateX")+px} {g("translateY")+py}) rotate({g("rotation")}) scale({sc}) translate({-px} {-py})'
            clip=c.find("clip-path"); cid=""
            inner=conv(c)
            if clip is not None:
                cid=f'c{abs(hash(clip.get(A+"pathData")))}'; s+=f'<clipPath id="{cid}"><path d="{clip.get(A+"pathData")}"/></clipPath>'
                s+=f'<g transform="{tr}" clip-path="url(#{cid})">{inner}</g>'
            else: s+=f'<g transform="{tr}">{inner}</g>'
    return s
files=sorted(glob.glob(f"{D}/nf_*.xml")); ims=[]
for f in files:
    r=ET.parse(f).getroot(); svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" width="96" height="96">{conv(r)}</svg>'
    p=f"{OUT}/{os.path.basename(f)[:-4]}.png"; cairosvg.svg2png(bytestring=svg.encode(), write_to=p); ims.append(p)
cols=12; sh=Image.new("RGB",(cols*104+8,((len(ims)+cols-1)//cols)*104+8),"#202020")
for i,p in enumerate(ims): sh.paste(Image.open(p).convert("RGBA"),(8+(i%cols)*104,8+(i//cols)*104),Image.open(p).convert("RGBA"))
sh.save(f"{OUT}/sheet.png"); print(len(ims))
