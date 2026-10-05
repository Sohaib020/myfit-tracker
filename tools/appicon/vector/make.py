import math, cairosvg
S=1024
def arc(cx,cy,r,a0,a1):
    p=lambda a:(cx+r*math.cos(math.radians(a)), cy+r*math.sin(math.radians(a)))
    x0,y0=p(a0); x1,y1=p(a1); large=1 if (a1-a0)%360>180 else 0
    return f"M{x0:.1f},{y0:.1f} A{r},{r} 0 {large} 1 {x1:.1f},{y1:.1f}", (x1,y1)
I={}
# 1 Orbit
d,(kx,ky)=arc(512,512,262,-90,215)
I['orbit']=("Orbit","Progress arc + knob · electric mint",f'''
<defs><radialGradient id="b" cx="35%" cy="25%" r="90%"><stop offset="0" stop-color="#17306B"/><stop offset="1" stop-color="#040814"/></radialGradient>
<linearGradient id="a" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#00F5B4"/><stop offset="1" stop-color="#00A8FF"/></linearGradient></defs>
<rect width="1024" height="1024" fill="url(#b)"/>
<circle cx="512" cy="512" r="262" fill="none" stroke="#fff" stroke-opacity=".07" stroke-width="84"/>
<path d="{d}" fill="none" stroke="url(#a)" stroke-opacity=".25" stroke-width="130" stroke-linecap="round"/>
<path d="{d}" fill="none" stroke="url(#a)" stroke-width="84" stroke-linecap="round"/>
<circle cx="{kx:.1f}" cy="{ky:.1f}" r="30" fill="#fff"/>
<path d="M452,560 L512,490 L572,560" fill="none" stroke="#fff" stroke-width="44" stroke-linecap="round" stroke-linejoin="round"/>''')
# 2 Pulse M
pm="M292,712 V318 L412,548 H446 L486,372 L528,692 L562,548 H600 L732,318 V712"
I['pulsem']=("Pulse M","Monogram with a heartbeat · violet",f'''
<defs><linearGradient id="b" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#5B6CFF"/><stop offset="1" stop-color="#B23BFF"/></linearGradient>
<radialGradient id="g" cx="30%" cy="20%" r="70%"><stop offset="0" stop-color="#fff" stop-opacity=".28"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient></defs>
<rect width="1024" height="1024" fill="url(#b)"/><rect width="1024" height="1024" fill="url(#g)"/>
<path d="{pm}" fill="none" stroke="#fff" stroke-opacity=".18" stroke-width="110" stroke-linejoin="round" stroke-linecap="round"/>
<path d="{pm}" fill="none" stroke="#fff" stroke-width="62" stroke-linejoin="round" stroke-linecap="round"/>''')
# 3 Noir Gold
hexp=" ".join(f"{512+300*math.cos(math.radians(a-90)):.1f},{512+300*math.sin(math.radians(a-90)):.1f}" for a in range(0,360,60))
I['noir']=("Noir Gold","Luxury hexagon + stride M · black & gold",f'''
<defs><linearGradient id="b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1A1A1C"/><stop offset="1" stop-color="#050506"/></linearGradient>
<linearGradient id="au" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FCEBA5"/><stop offset=".5" stop-color="#C9952F"/><stop offset="1" stop-color="#F5D98A"/></linearGradient></defs>
<rect width="1024" height="1024" fill="url(#b)"/>
<polygon points="{hexp}" fill="none" stroke="url(#au)" stroke-width="16" stroke-linejoin="round"/>
<path d="M372,640 L452,412 L512,548 L572,412 L652,640" fill="none" stroke="url(#au)" stroke-width="46" stroke-linecap="round" stroke-linejoin="round"/>''')
# 4 Prism Leaf
I['prism']=("Prism Leaf","Faceted leaf · health & growth",'''
<defs><linearGradient id="b" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0F2A2E"/><stop offset="1" stop-color="#07161A"/></linearGradient></defs>
<rect width="1024" height="1024" fill="url(#b)"/>
<polygon points="512,236 340,470 512,500" fill="#C4F55E"/><polygon points="340,470 512,500 360,640" fill="#7FDB5F"/>
<polygon points="360,640 512,500 512,760" fill="#3FC37A"/><polygon points="512,236 684,470 512,500" fill="#8CE867"/>
<polygon points="684,470 512,500 664,640" fill="#3CCB86"/><polygon points="664,640 512,500 512,760" fill="#1E9E8A"/>
<path d="M512,500 V800" stroke="#0A1F22" stroke-width="10" stroke-linecap="round" stroke-opacity=".35"/>''')
# 5 mf ligature
I['mf']=("mf Script","Continuous mf monogram · sunset coral",'''
<defs><linearGradient id="b" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FF9A4A"/><stop offset="1" stop-color="#FF3D6E"/></linearGradient></defs>
<rect width="1024" height="1024" fill="url(#b)"/>
<path d="M268,694 V516 C268,432 384,432 384,516 V694 M384,516 C384,432 500,432 500,516 V694 M604,694 V420 C604,338 668,316 752,334 M548,520 H712" fill="none" stroke="#fff" stroke-width="58" stroke-linecap="round" stroke-linejoin="round"/>''')
# 6 Dusk Summit (full bleed)
I['summit']=("Dusk Summit","Layered peaks at sunrise · journeys",'''
<defs><linearGradient id="b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFB86B"/><stop offset=".55" stop-color="#FF6F6F"/><stop offset="1" stop-color="#5B3A9C"/></linearGradient></defs>
<rect width="1024" height="1024" fill="url(#b)"/>
<circle cx="512" cy="470" r="138" fill="#FFF0BE"/>
<path d="M0,760 L230,520 L380,640 L560,430 L760,620 L880,540 L1024,680 V1024 H0 Z" fill="#8A5BC2" fill-opacity=".85"/>
<path d="M0,1024 V820 L300,560 L470,720 L640,540 L1024,860 V1024 Z" fill="#2B1C4E"/>
<path d="M640,540 L700,590 L672,600 L640,580 L606,604 L590,575 Z" fill="#fff" fill-opacity=".9"/>''')
# 7 Fire & Water drop
I['drop']=("Burn & Hydrate","Half water, half flame · balance",'''
<defs><linearGradient id="w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5ED3FF"/><stop offset="1" stop-color="#1F5BFF"/></linearGradient>
<linearGradient id="f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFC94A"/><stop offset="1" stop-color="#FF3D3D"/></linearGradient>
<clipPath id="dr"><path d="M512,226 C512,226 318,470 318,612 A194,194 0 0 0 706,612 C706,470 512,226 512,226 Z"/></clipPath>
<radialGradient id="b" cx="50%" cy="40%" r="75%"><stop offset="0" stop-color="#1A2140"/><stop offset="1" stop-color="#070A16"/></radialGradient></defs>
<rect width="1024" height="1024" fill="url(#b)"/>
<g clip-path="url(#dr)"><rect x="300" y="200" width="430" height="640" fill="url(#f)"/>
<path d="M300,200 H512 C470,340 560,440 512,560 C470,660 540,740 512,840 H300 Z" fill="url(#w)"/></g>
<ellipse cx="430" cy="520" rx="34" ry="70" fill="#fff" fill-opacity=".35" transform="rotate(20 430 520)"/>''')
# 8 Heart (light)
I['heart']=("Soft Heart","Gradient heart line · light & clean",'''
<defs><linearGradient id="b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#E7F1EE"/></linearGradient>
<linearGradient id="h" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FF4F7B"/><stop offset="1" stop-color="#FF9F5A"/></linearGradient></defs>
<rect width="1024" height="1024" fill="url(#b)"/>
<path d="M512,724 C312,596 262,474 308,388 C354,302 466,306 512,384 C558,306 670,302 716,388 C762,474 712,596 512,724 Z" fill="none" stroke="url(#h)" stroke-width="72" stroke-linejoin="round"/>
<path d="M388,500 H452 L484,446 L528,566 L560,500 H636" fill="none" stroke="url(#h)" stroke-width="30" stroke-linecap="round" stroke-linejoin="round"/>''')
# 9 Crescent Pulse
I['crescent']=("Crescent Pulse","Moon + heartbeat · emerald & gold",'''
<defs><linearGradient id="b" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0E4A37"/><stop offset="1" stop-color="#04221A"/></linearGradient>
<linearGradient id="au" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#FFE29A"/><stop offset="1" stop-color="#E7B04A"/></linearGradient></defs>
<rect width="1024" height="1024" fill="url(#b)"/>
<path d="M487.6,262.3 A250,250 0 1 0 701.6,659.8 A226,226 0 0 1 487.6,262.3 Z" fill="#F4FFF9"/>
<path d="M520,610 H586 L620,520 L666,690 L700,590 H770" fill="none" stroke="url(#au)" stroke-width="34" stroke-linecap="round" stroke-linejoin="round"/>''')
# 10 Rise chevrons
I['rise']=("Level Up","Ascending chevrons · neon on black",'''
<defs><linearGradient id="b" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0D0D18"/><stop offset="1" stop-color="#030306"/></linearGradient></defs>
<rect width="1024" height="1024" fill="url(#b)"/>
<path d="M332,724 L512,584 L692,724" fill="none" stroke="#3D7BFF" stroke-width="72" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M332,574 L512,434 L692,574" fill="none" stroke="#9B4DFF" stroke-width="72" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M332,424 L512,284 L692,424" fill="none" stroke="#FF4FA3" stroke-width="72" stroke-linecap="round" stroke-linejoin="round"/>''')
import json
meta=[]
for k,(t,s,body) in I.items():
    svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">{body}</svg>'
    open(f"{k}.svg","w").write(svg); cairosvg.svg2png(bytestring=svg.encode(),write_to=f"{k}.png",output_width=512,output_height=512)
    meta.append((k,t,s))
json.dump(meta,open("meta.json","w"))
print(len(meta))
