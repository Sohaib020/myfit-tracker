"""Round 13 launcher icon variants: refined versions of the ring + heartbeat mark.
Renders with headless Chromium (full SVG filter support). Outputs out/<id>.png (1024) and contact sheets."""
import math, io, base64, os, sys
import numpy as np
from PIL import Image
from playwright.sync_api import sync_playwright

OUT = os.path.join(os.path.dirname(__file__), "out"); os.makedirs(OUT, exist_ok=True)
C = 512; R = 250; A0, A1 = 236, 150 + 360        # arc from upper-left terminal, clockwise to lower-left terminal
PULSE = [(322,512),(432,512),(464,410),(510,624),(550,468),(574,512),(694,512)]

def pt(a, r=R, c=C): return (c + r*math.cos(math.radians(a)), c + r*math.sin(math.radians(a)))
def arc(r=R, a0=A0, a1=A1):
    x0,y0 = pt(a0,r); x1,y1 = pt(a1,r); large = 1 if (a1-a0) > 180 else 0
    return f"M{x0:.1f},{y0:.1f} A{r},{r} 0 {large} 1 {x1:.1f},{y1:.1f}"
def pulse(pts=PULSE, dx=0, dy=0): return "M" + " L".join(f"{x+dx:.0f},{y+dy:.0f}" for x,y in pts)

def hexrgb(h): h=h.lstrip('#'); return np.array([int(h[i:i+2],16) for i in (0,2,4)], float)
def conic(stops, size=1024):
    """stops: [(angle_deg, '#hex')] increasing, clockwise from +x (screen coords). Returns data URI."""
    y,x = np.mgrid[0:size,0:size]; a = (np.degrees(np.arctan2(y-size/2, x-size/2)) - stops[0][0]) % 360 + stops[0][0]
    angs = np.array([s[0] for s in stops], float); cols = np.array([hexrgb(s[1]) for s in stops])
    img = np.stack([np.interp(a, angs, cols[:,k]) for k in range(3)], -1).astype(np.uint8)
    b = io.BytesIO(); Image.fromarray(img).save(b, "PNG"); return "data:image/png;base64," + base64.b64encode(b.getvalue()).decode()

BRAND = [(236,'#D2F35A'),(300,'#9BF06A'),(360,'#3FE6A8'),(420,'#33C6F0'),(470,'#3D8BFF'),(510,'#4A7CFF'),(596,'#D2F35A')]
SOFT  = [(236,'#C9EC6A'),(330,'#6FE3A6'),(420,'#4FC1E8'),(510,'#5B8DF0'),(596,'#C9EC6A')]
DEEP  = [(236,'#B8F03C'),(320,'#2EE0A0'),(410,'#16B8E8'),(510,'#2F6BFF'),(596,'#B8F03C')]
_cache = {}
def ring_img(stops):
    k = str(stops)
    if k not in _cache: _cache[k] = conic(stops)
    return _cache[k]

def svg(body, defs=""):
    return f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 1024 1024" width="1024" height="1024"><defs>{defs}</defs>{body}</svg>'

def ringmask(id, w, r=R, a0=A0, a1=A1):
    return f'<mask id="{id}" maskUnits="userSpaceOnUse" x="0" y="0" width="1024" height="1024"><rect width="1024" height="1024"/><path d="{arc(r,a0,a1)}" fill="none" stroke="#fff" stroke-width="{w}" stroke-linecap="round"/></mask>'
def conic_ring(id, stops, w, opacity=1):
    return ringmask(id, w), f'<image href="{ring_img(stops)}" width="1024" height="1024" mask="url(#{id})" opacity="{opacity}"/>'
def pstroke(color, w, extra=""): return f'<path d="{pulse()}" fill="none" stroke="{color}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round" {extra}/>'
def bg_radial(a, b, cx="38%", cy="28%"):
    return f'<radialGradient id="bg" cx="{cx}" cy="{cy}" r="85%"><stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{b}"/></radialGradient>', '<rect width="1024" height="1024" fill="url(#bg)"/>'
GRAIN = '<filter id="grain" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" seed="7"/><feColorMatrix values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 .05 0"/></filter>'
GRAIN_USE = '<rect width="1024" height="1024" filter="url(#grain)"/>'

I = []   # (id, family, name, note, svg)
# ---------------------------------------------------------------- A · crafted vector, subtle depth
def A(id, name, note, bgdefs, bgbody, stops, w=112, pulse_col="#F4FFF8", pw=40, hl=.22, under=.0, ink=None):
    d, b = [bgdefs, GRAIN, '<filter id="ab" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="20"/></filter>'], [bgbody]
    m, img = conic_ring("rm", stops, w); d.append(m)
    if under: b.append(f'<path d="{arc(R+6)}" fill="none" stroke="#000" stroke-opacity="{under}" stroke-width="{w}" stroke-linecap="round" transform="translate(0,20)" filter="url(#ab)"/>')
    b.append(img)
    # one consistent light source (top-left): a thin inner highlight on the upper half, a soft shade on the outer lower half
    d.append(f'<linearGradient id="hl" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff" stop-opacity="{hl}"/><stop offset=".55" stop-color="#fff" stop-opacity="0"/></linearGradient>')
    b.append(f'<path d="{arc(R - w*0.24, A0+8, 400)}" fill="none" stroke="url(#hl)" stroke-width="{w*0.12:.0f}" stroke-linecap="round"/>')
    d.append('<linearGradient id="sh" x1="0" y1="0" x2="1" y2="1"><stop offset=".45" stop-color="#062022" stop-opacity="0"/><stop offset="1" stop-color="#062022" stop-opacity=".22"/></linearGradient>')
    b.append(f'<rect width="1024" height="1024" fill="url(#sh)" mask="url(#rm)"/>')
    b.append(pstroke(pulse_col, pw))
    b.append(GRAIN_USE)
    I.append((id, "A", name, note, svg("".join(b), "".join(d))))

A("a1","Teal Night","Closest to today · calmer colour, one light source", *bg_radial("#123A3C","#06191B"), BRAND)
A("a2","Midnight","Navy ground, deeper blue tail", *bg_radial("#16234A","#070C1E"), DEEP, pulse_col="#E9FFF4")
A("a3","Graphite","Near-black, colour does all the work", *bg_radial("#22272A","#0B0D0E"), BRAND, hl=.18)
A("a4","Paper","Light theme · soft lift under the ring", *bg_radial("#FFFFFF","#E9EEE8"), SOFT, pulse_col="#123033", pw=38, under=.16, hl=.35)
A("a5","Forest","Deep green, lime-forward", *bg_radial("#14382B","#061611"), [(236,'#E3F55A'),(330,'#8EEB6A'),(420,'#36D3A6'),(510,'#2DA6D8'),(596,'#E3F55A')], pulse_col="#F2FFE0")
A("a6","Slim","Thinner ring, finer pulse, more air", *bg_radial("#0F3133","#051315"), BRAND, w=84, pw=32)

# ---------------------------------------------------------------- B · flat minimal
def B(id, name, note, bg, ring, pcol, w=104, pw=40, segs=None, mono=False):
    d, b = [], [f'<rect width="1024" height="1024" fill="{bg}"/>']
    if segs:   # flat colour segments with hairline gaps
        n = len(segs); span = (A1-A0)/n; gap = 2.2
        for i,col in enumerate(segs):
            a0 = A0 + i*span + (gap if i else 0); a1 = A0 + (i+1)*span - (gap if i < n-1 else 0)
            cap = "round"
            b.append(f'<path d="{arc(R,a0,a1)}" fill="none" stroke="{col}" stroke-width="{w}" stroke-linecap="butt"/>')
        # round only the two outer terminals
        for a,col in ((A0,segs[0]),(A1,segs[-1])):
            x,y = pt(a); b.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{w/2}" fill="{col}"/>')
    elif ring.startswith("lin:"):
        a,c2 = ring[4:].split(",")
        d.append(f'<linearGradient id="lg" x1=".3" y1="0" x2=".5" y2="1"><stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{c2}"/></linearGradient>')
        b.append(f'<path d="{arc()}" fill="none" stroke="url(#lg)" stroke-width="{w}" stroke-linecap="round"/>')
    else:
        b.append(f'<path d="{arc()}" fill="none" stroke="{ring}" stroke-width="{w}" stroke-linecap="round"/>')
    if mono:   # pulse grows out of the ring's right side
        pts = PULSE[:-1] + [(C + R, 512)]
        b.append(f'<path d="{pulse(pts)}" fill="none" stroke="{pcol}" stroke-width="{pw}" stroke-linecap="round" stroke-linejoin="round"/>')
    else:
        b.append(pstroke(pcol, pw))
    I.append((id, "B", name, note, svg("".join(b), "".join(d))))

B("b1","Tricolour","Three flat segments on white", "#FFFFFF", None, "#13292B", segs=["#C6EE4E","#3EDFA6","#3D8BFF"])
B("b2","Mint Mono","One colour on black, nothing extra", "#0C0F10", "#41E6AE", "#FFFFFF", pw=36)
B("b3","Lime Block","Bold lime field, dark mark", "#C8F04E", "#0E2C2D", "#0E2C2D")
B("b4","Cobalt","Blue field, white ring, lime pulse", "#2F6BFF", "#FFFFFF", "#D4F75C")
B("b5","Line","Monoline: equal weights, flat gradient", "#0B2426", "lin:#C9F25A,#3D8BFF", "#FFFFFF", w=64, pw=40)
B("b6","Joined","Pulse runs into the ring · one mark", "#F3F6F1", "lin:#B5E640,#2F7BFF", "#14302F", w=96, pw=40, mono=True)

# ---------------------------------------------------------------- C · rich 3D, crafted
def C3(id, name, note, bgdefs, bgbody, stops, w=124, track=None, glass=False, bloom=.0, pulse_col="#FFFFFF", light_bg=False, gloss=.75):
    d, b = [bgdefs, GRAIN], [bgbody]
    d.append('<filter id="blur8" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="8"/></filter>')
    d.append('<filter id="blur22" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="22"/></filter>')
    d.append('<filter id="blur3" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="2.5"/></filter>')
    d.append('<filter id="blur40" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="46"/></filter>')
    m, img = conic_ring("rm", stops, w); d.append(m)
    if bloom:   # coloured light the ring throws on the background (low, wide, not a halo)
        b.append(f'<g filter="url(#blur40)" opacity="{bloom}" transform="translate(0,34)"><image href="{ring_img(stops)}" width="1024" height="1024" mask="url(#rm)"/></g>')
    if track:   # recessed groove the tube sits in
        b.append(f'<circle cx="{C}" cy="{C}" r="{R}" fill="none" stroke="{track}" stroke-width="{w+34}"/>')
        b.append(f'<g mask="url(#trk)"><circle cx="{C}" cy="{C-10}" r="{R}" fill="none" stroke="#000" stroke-opacity=".5" stroke-width="{w+34}" filter="url(#blur8)"/></g>')
        d.append(f'<mask id="trk"><rect width="1024" height="1024"/><circle cx="{C}" cy="{C}" r="{R}" fill="none" stroke="#fff" stroke-width="{w+34}"/></mask>')
    # contact shadow
    b.append(f'<path d="{arc()}" fill="none" stroke="#000" stroke-opacity="{.14 if light_bg else .32}" stroke-width="{w*0.9:.0f}" stroke-linecap="round" filter="url(#blur22)" transform="translate(0,{26 if light_bg else 22})"/>')
    b.append(img if not glass else img.replace('opacity="1"','opacity=".55"'))
    # tube form: dark on the outer-lower side, light on the inner-upper side (masked to the ring)
    d.append('<radialGradient id="form" cx="512" cy="512" r="330" gradientUnits="userSpaceOnUse">'
             f'<stop offset="{(R-w/2)/330:.3f}" stop-color="#fff" stop-opacity=".22"/>'
             f'<stop offset="{R/330:.3f}" stop-color="#fff" stop-opacity="0"/>'
             f'<stop offset="{(R+w/2)/330:.3f}" stop-color="#001018" stop-opacity=".38"/></radialGradient>')
    b.append(f'<rect width="1024" height="1024" fill="url(#form)" mask="url(#rm)"/>')
    d.append('<linearGradient id="dir" x1="0" y1="0" x2="1" y2="1"><stop offset=".35" stop-color="#001018" stop-opacity="0"/><stop offset="1" stop-color="#001018" stop-opacity=".28"/></linearGradient>')
    b.append(f'<rect width="1024" height="1024" fill="url(#dir)" mask="url(#rm)"/>')
    # specular: one crisp streak on the upper arc + a small one near the lower-left terminal
    d.append(f'<linearGradient id="spec" x1="0" y1="0" x2="1" y2=".6"><stop offset="0" stop-color="#fff" stop-opacity="{gloss}"/><stop offset=".6" stop-color="#fff" stop-opacity="0"/></linearGradient>')
    b.append(f'<path d="{arc(R - w*0.2, A0+10, 330)}" fill="none" stroke="url(#spec)" stroke-width="{w*0.13:.0f}" stroke-linecap="round" filter="url(#blur3)"/>')
    x,y = pt(A1-14, R - w*0.18); b.append(f'<ellipse cx="{x:.0f}" cy="{y:.0f}" rx="{w*0.11:.0f}" ry="{w*0.06:.0f}" fill="#fff" fill-opacity="{gloss*.7:.2f}" transform="rotate(-30 {x:.0f} {y:.0f})" filter="url(#blur3)"/>')
    if glass:   # bright rim on both edges
        for rr in (R - w/2 + 3, R + w/2 - 3):
            b.append(f'<path d="{arc(rr)}" fill="none" stroke="#fff" stroke-opacity=".55" stroke-width="3"/>')
    # rim light on the outer lower edge (reflected light)
    b.append(f'<path d="{arc(R + w*0.40, 20, 140)}" fill="none" stroke="#fff" stroke-opacity=".16" stroke-width="{w*0.06:.0f}" stroke-linecap="round" filter="url(#blur3)"/>')
    # pulse: raised, with a soft shadow and a top bevel
    b.append(pstroke("#000", 42, f'stroke-opacity="{.18 if light_bg else .4}" filter="url(#blur8)" transform="translate(0,10)"'))
    b.append(pstroke(pulse_col, 42))
    b.append(f'<path d="{pulse(dy=-6)}" fill="none" stroke="#fff" stroke-opacity=".28" stroke-width="8" stroke-linecap="round" stroke-linejoin="round" filter="url(#blur3)"/>' if pulse_col != "#FFFFFF" else "")
    b.append(GRAIN_USE)
    I.append((id, "C", name, note, svg("".join(b), "".join(d))))

C3("c1","Studio","Current look, rebuilt: real tube light, no fake glow", *bg_radial("#13383A","#051416"), BRAND, bloom=.22)
C3("c2","Glass","Translucent tube with bright rims", *bg_radial("#1B3F55","#06121C"), BRAND, glass=True, bloom=.3)
C3("c3","Ceramic","Satin finish on a light ground", *bg_radial("#FFFFFF","#E4EAE3"), SOFT, light_bg=True, pulse_col="#16393B", gloss=.6)
C3("c4","Groove","Tube set into a dark track", *bg_radial("#1A2B30","#080F11"), BRAND, track="#0B1517", bloom=.12)
C3("c5","Aurora","Night sky, coloured light spilling down", *bg_radial("#14234A","#04081A"), DEEP, bloom=.45, pulse_col="#EFFFF7")
C3("c6","Candy","Thicker, glossier · playful", *bg_radial("#1C1F4A","#090A1E"), DEEP, w=140, bloom=.28, gloss=.9)

def render():
    with sync_playwright() as p:
        br = p.chromium.launch(); pg = br.new_page(viewport={"width":1024,"height":1024})
        for id, fam, name, note, s in I:
            pg.set_content(f'<html><body style="margin:0;background:#000">{s}</body></html>'); pg.wait_for_timeout(60)
            pg.screenshot(path=f"{OUT}/{id}.png", clip={"x":0,"y":0,"width":1024,"height":1024})
        br.close()

def sheets():
    from PIL import ImageDraw, ImageFont
    def font(sz, bold=False):
        for f in (["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"] if bold else ["/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]):
            if os.path.exists(f): return ImageFont.truetype(f, sz)
        return ImageFont.load_default()
    titles = {"A":"1 · Crafted vector (subtle depth)","B":"2 · Flat minimal","C":"3 · Rich 3D, crafted"}
    T = 300; pad = 60
    for fam in "ABC":
        items = [x for x in I if x[1]==fam]
        W = pad + 3*(T+pad); H = 140 + 2*(T+190)
        sh = Image.new("RGB",(W,H),"#F2F3F1"); dr = ImageDraw.Draw(sh)
        dr.text((pad,48), titles[fam], fill="#111", font=font(40,True))
        for i,(id,_,name,note,_) in enumerate(items):
            col,row = i%3, i//3; x = pad + col*(T+pad); y = 140 + row*(T+190)
            ic = Image.open(f"{OUT}/{id}.png").convert("RGBA").resize((T,T), Image.LANCZOS)
            m = Image.new("L",(T*4,T*4),0); ImageDraw.Draw(m).rounded_rectangle((0,0,T*4-1,T*4-1), radius=T*4*0.23, fill=255)
            sh.paste(ic,(x,y),m.resize((T,T),Image.LANCZOS))
            # small sizes: home-screen 72px and notification-ish 40px
            for k,sz in enumerate((72,40)):
                s2 = ic.resize((sz,sz),Image.LANCZOS); mm = Image.new("L",(sz*4,sz*4),0)
                ImageDraw.Draw(mm).ellipse((0,0,sz*4-1,sz*4-1),fill=255)
                sh.paste(s2,(x+T-72-(0 if k==0 else 72+16+0)+(0 if k==0 else 32), y+T+80+(0 if k==0 else 16)), mm.resize((sz,sz),Image.LANCZOS))
            dr.text((x,y+T+16), f"{id.upper()}  {name}", fill="#111", font=font(26,True))
            # wrap note
            words, line, ly = note.split(), "", y+T+52
            for w_ in words:
                if dr.textlength(line+" "+w_, font=font(19)) > T-110: dr.text((x,ly), line.strip(), fill="#555", font=font(19)); ly += 24; line = ""
                line += " "+w_
            dr.text((x,ly), line.strip(), fill="#555", font=font(19))
        sh.save(f"{OUT}/sheet_{fam}.png")

if __name__ == "__main__":
    render(); sheets(); print("done", len(I))
