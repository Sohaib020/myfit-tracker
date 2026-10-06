"""Ready-made profile pictures (sports, creatures, faces/food/icons). Writes app/src/main/assets/avatars/<id>.webp
(192 px, square full-bleed; the app clips them to a circle) + catalog.json, and a contact sheet for review."""
import math, os, json
from PIL import Image
from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, "../../app/src/main/assets/avatars")); os.makedirs(OUT, exist_ok=True)
TMP = os.path.join(HERE, "out"); os.makedirs(TMP, exist_ok=True)
A = []   # (id, category, svg)

def wrap(body, bg=("#FFD36B", "#FF9F43"), shadow=True, extra_defs=""):
    sh = '<ellipse cx="50" cy="90" rx="26" ry="5" fill="#000" opacity=".12"/>' if shadow else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="512" height="512"><defs>'
            f'<radialGradient id="bg" cx="35%" cy="25%" r="90%"><stop offset="0" stop-color="{bg[0]}"/><stop offset="1" stop-color="{bg[1]}"/></radialGradient>{extra_defs}</defs>'
            f'<rect width="100" height="100" fill="url(#bg)"/>{sh}{body}</svg>')
def add(id, cat, body, bg, **kw): A.append((id, cat, wrap(body, bg, **kw)))

# ------------------------------------------------------------------ palettes
BG = {
 "sun": ("#FFE28A", "#FFB547"), "coral": ("#FFB4A2", "#FF7B6B"), "mint": ("#B9F5D8", "#5FD6A4"), "sky": ("#BFE3FF", "#6AAEFF"),
 "lilac": ("#E2D4FF", "#A88BFF"), "pink": ("#FFD1E6", "#FF8DBB"), "teal": ("#9FE8E2", "#2FB8B0"), "navy": ("#4B5C9E", "#1E2A5A"),
 "lime": ("#E4FA9C", "#A6E04B"), "peach": ("#FFE0C2", "#FFAE73"), "slate": ("#C9D3DE", "#8293A8"), "night": ("#3A3F73", "#141735"),
 "berry": ("#FFB3C7", "#D94C7A"), "forest": ("#9ED9A9", "#3E9E62"), "sand": ("#F6E7C8", "#E2BE7E"), "ocean": ("#8FD3FF", "#2D7FD8"),
}
INK = "#2B2440"

# ------------------------------------------------------------------ SPORTS: bold pictogram athletes
def limb(a, b, w=7.5, c="#FFFFFF"): return f'<line x1="{a[0]}" y1="{a[1]}" x2="{b[0]}" y2="{b[1]}" stroke="{c}" stroke-width="{w}" stroke-linecap="round"/>'
def poly(pts, w=7.5, c="#FFFFFF"): return f'<polyline points="{" ".join(f"{x},{y}" for x,y in pts)}" fill="none" stroke="{c}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>'
def fig(p, c="#FFFFFF", w=7.5):
    """p: head, neck, hip, lh(and), le(lbow), rh, re, lk(nee), lf(oot), rk, rf. Back limbs drawn slightly darker."""
    back = "#000000"; s = []
    s.append(poly([p["neck"], p["le"], p["lh"]], w, c).replace('stroke="'+c, 'stroke-opacity=".55" stroke="'+c))
    s.append(poly([p["hip"], p["lk"], p["lf"]], w+.5, c).replace('stroke="'+c, 'stroke-opacity=".55" stroke="'+c))
    s.append(limb(p["neck"], p["hip"], w+3, c))
    s.append(poly([p["hip"], p["rk"], p["rf"]], w+.5, c))
    s.append(poly([p["neck"], p["re"], p["rh"]], w, c))
    hx, hy = p["head"]; s.append(f'<circle cx="{hx}" cy="{hy}" r="6.8" fill="{c}"/>')
    return "".join(s)
def P(**k): return {kk: tuple(v) for kk, v in k.items()}

SPORTS = [
 ("run", "coral", P(head=(56,22), neck=(53,31), hip=(47,52), le=(62,40), lh=(70,34), re=(42,40), rh=(36,48), lk=(60,64), lf=(56,80), rk=(38,62), rf=(26,70)), ""),
 ("sprint", "sun", P(head=(64,30), neck=(57,37), hip=(42,50), le=(66,48), lh=(72,58), re=(48,46), rh=(40,58), lk=(54,64), lf=(50,80), rk=(30,58), rf=(22,72)), ""),
 ("lift", "slate", P(head=(50,30), neck=(50,38), hip=(50,58), le=(36,30), lh=(32,18), re=(64,30), rh=(68,18), lk=(42,70), lf=(38,84), rk=(58,70), rf=(62,84)),
   '<line x1="16" y1="18" x2="84" y2="18" stroke="#2B2440" stroke-width="3" stroke-linecap="round"/><rect x="12" y="10" width="6" height="16" rx="2" fill="#2B2440"/><rect x="82" y="10" width="6" height="16" rx="2" fill="#2B2440"/>'),
 ("squat", "lilac", P(head=(48,30), neck=(48,38), hip=(42,58), le=(60,40), lh=(70,38), re=(60,44), rh=(70,42), lk=(60,64), lf=(54,82), rk=(58,62), rf=(48,82)), ""),
 ("cricket", "mint", P(head=(46,22), neck=(46,30), hip=(48,52), le=(58,30), lh=(64,22), re=(56,36), rh=(64,24), lk=(40,66), lf=(36,82), rk=(58,66), rf=(62,82)),
   '<line x1="64" y1="23" x2="82" y2="6" stroke="#E9C27A" stroke-width="5" stroke-linecap="round"/><line x1="64" y1="23" x2="68" y2="19" stroke="#8A5A2B" stroke-width="5" stroke-linecap="round"/>'),
 ("bowler", "forest", P(head=(52,24), neck=(52,32), hip=(48,54), le=(62,24), lh=(66,12), re=(40,40), rh=(34,46), lk=(60,66), lf=(66,80), rk=(40,66), rf=(32,78)),
   '<circle cx="68" cy="9" r="4" fill="#E2453C"/><path d="M65,7 q3,2 6,0" stroke="#fff" stroke-width=".8" fill="none"/>'),
 ("football", "lime", P(head=(44,22), neck=(46,30), hip=(48,52), le=(34,36), lh=(28,46), re=(58,36), rh=(66,30), lk=(40,66), lf=(36,82), rk=(62,60), rf=(72,66)),
   '<circle cx="80" cy="70" r="7" fill="#fff" stroke="#2B2440" stroke-width="1.4"/><polygon points="80,66.5 83,68.7 82,72.2 78,72.2 77,68.7" fill="#2B2440"/>'),
 ("goalie", "teal", P(head=(50,30), neck=(50,38), hip=(50,58), le=(36,30), lh=(26,22), re=(64,30), rh=(74,22), lk=(38,68), lf=(30,82), rk=(62,68), rf=(70,82)),
   '<circle cx="24" cy="20" r="4" fill="#FFB547"/><circle cx="76" cy="20" r="4" fill="#FFB547"/>'),
 ("cycle", "sky", P(head=(52,24), neck=(48,32), hip=(36,48), le=(56,40), lh=(62,46), re=(54,42), rh=(62,47), lk=(48,56), lf=(42,68), rk=(44,60), rf=(50,66)),
   '<circle cx="26" cy="70" r="12" fill="none" stroke="#2B2440" stroke-width="3"/><circle cx="72" cy="70" r="12" fill="none" stroke="#2B2440" stroke-width="3"/><polyline points="26,70 42,52 60,52 72,70 46,68 42,52" fill="none" stroke="#2B2440" stroke-width="2.6" stroke-linejoin="round"/><line x1="60" y1="52" x2="62" y2="44" stroke="#2B2440" stroke-width="2.6"/>'),
 ("swim", "ocean", P(head=(64,48), neck=(56,52), hip=(36,56), le=(66,36), lh=(78,34), re=(46,48), rh=(40,60), lk=(26,54), lf=(16,52), rk=(26,60), rf=(16,62)),
   '<path d="M6,64 q8,-5 16,0 t16,0 t16,0 t16,0 t16,0 t16,0 V100 H6 Z" fill="#fff" fill-opacity=".35"/><path d="M0,72 q8,-5 16,0 t16,0 t16,0 t16,0 t16,0 t16,0 V100 H0 Z" fill="#fff" fill-opacity=".25"/>', False),
 ("yoga", "pink", P(head=(50,22), neck=(50,30), hip=(50,52), le=(40,18), lh=(50,8), re=(60,18), rh=(50,8), lk=(36,62), lf=(48,58), rk=(50,68), rf=(50,84)), ""),
 ("meditate", "lilac", P(head=(50,28), neck=(50,36), hip=(50,58), le=(36,48), lh=(30,60), re=(64,48), rh=(70,60), lk=(32,68), lf=(58,72), rk=(68,68), rf=(42,72)),
   '<circle cx="50" cy="28" r="12" fill="none" stroke="#fff" stroke-opacity=".5" stroke-width="1.5"/>'),
 ("boxing", "berry", P(head=(46,24), neck=(46,32), hip=(48,54), le=(58,40), lh=(70,32), re=(36,42), rh=(40,32), lk=(56,68), lf=(62,84), rk=(40,68), rf=(34,84)),
   '<circle cx="72" cy="31" r="6" fill="#E2453C"/><circle cx="41" cy="30" r="6" fill="#E2453C"/>'),
 ("basket", "peach", P(head=(48,26), neck=(48,34), hip=(48,56), le=(58,24), lh=(62,14), re=(38,24), rh=(42,14), lk=(42,70), lf=(40,86), rk=(56,70), rf=(58,86)),
   '<circle cx="52" cy="9" r="7" fill="#F08A2C" stroke="#2B2440" stroke-width="1.2"/><path d="M45,9 h14 M52,2 v14" stroke="#2B2440" stroke-width="1"/>'),
 ("tennis", "lime", P(head=(42,24), neck=(44,32), hip=(48,54), le=(56,34), lh=(66,30), re=(34,40), rh=(28,32), lk=(40,68), lf=(36,84), rk=(58,68), rf=(64,82)),
   '<line x1="66" y1="30" x2="74" y2="22" stroke="#2B2440" stroke-width="2.4" stroke-linecap="round"/><ellipse cx="80" cy="15" rx="7" ry="9" transform="rotate(40 80 15)" fill="none" stroke="#2B2440" stroke-width="2.4"/><circle cx="26" cy="22" r="3.5" fill="#E4FA5C" stroke="#2B2440" stroke-width=".8"/>'),
 ("badminton", "sun", P(head=(50,26), neck=(50,34), hip=(50,56), le=(60,24), lh=(66,14), re=(40,40), rh=(34,46), lk=(44,70), lf=(40,86), rk=(58,70), rf=(62,86)),
   '<line x1="66" y1="14" x2="72" y2="6" stroke="#2B2440" stroke-width="2" stroke-linecap="round"/><ellipse cx="75" cy="3" rx="4" ry="5" fill="none" stroke="#2B2440" stroke-width="2"/><path d="M28,14 l6,6 M28,14 l-2,8 l8,-2 z" fill="#fff" stroke="#2B2440" stroke-width="1"/>'),
 ("hike", "forest", P(head=(50,22), neck=(49,30), hip=(46,52), le=(58,38), lh=(66,44), re=(40,40), rh=(34,48), lk=(56,64), lf=(58,80), rk=(40,66), rf=(32,80)),
   '<rect x="30" y="28" width="14" height="20" rx="4" fill="#E58A2E"/><line x1="66" y1="44" x2="72" y2="84" stroke="#2B2440" stroke-width="2.2" stroke-linecap="round"/>'),
 ("rope", "coral", P(head=(50,20), neck=(50,28), hip=(50,50), le=(40,38), lh=(32,46), re=(60,38), rh=(68,46), lk=(44,62), lf=(46,76), rk=(56,62), rf=(54,76)),
   '<path d="M32,46 C20,90 80,90 68,46" fill="none" stroke="#2B2440" stroke-width="2"/>'),
 ("pushup", "slate", P(head=(76,52), neck=(68,56), hip=(42,62), le=(70,66), lh=(70,76), re=(66,66), rh=(64,76), lk=(30,66), lf=(16,72), rk=(30,68), rf=(18,74)), ""),
 ("pullup", "navy", P(head=(50,28), neck=(50,36), hip=(50,58), le=(36,24), lh=(36,12), re=(64,24), rh=(64,12), lk=(46,72), lf=(42,84), rk=(56,72), rf=(58,84)),
   '<line x1="14" y1="12" x2="86" y2="12" stroke="#FFD36B" stroke-width="3" stroke-linecap="round"/>'),
 ("kettle", "teal", P(head=(50,24), neck=(50,32), hip=(50,54), le=(46,44), lh=(50,56), re=(54,44), rh=(50,56), lk=(40,68), lf=(36,84), rk=(60,68), rf=(64,84)),
   '<path d="M45,58 a5,5 0 0 1 10,0" fill="none" stroke="#2B2440" stroke-width="2.4"/><circle cx="50" cy="66" r="8" fill="#2B2440"/>'),
 ("row", "ocean", P(head=(40,40), neck=(42,48), hip=(52,64), le=(30,56), lh=(22,58), re=(32,58), rh=(24,60), lk=(40,58), lf=(30,70), rk=(42,60), rf=(32,72)),
   '<path d="M8,74 h84 l-8,8 h-68 z" fill="#2B2440"/><line x1="22" y1="59" x2="6" y2="88" stroke="#E9C27A" stroke-width="2.6" stroke-linecap="round"/>', False),
 ("stretch", "mint", P(head=(42,30), neck=(46,37), hip=(56,56), le=(36,28), lh=(28,20), re=(52,26), rh=(60,16), lk=(68,68), lf=(80,82), rk=(48,70), rf=(36,84)), ""),
 ("dance", "pink", P(head=(54,22), neck=(52,30), hip=(48,52), le=(64,24), lh=(70,14), re=(40,38), rh=(32,30), lk=(58,64), lf=(66,76), rk=(42,66), rf=(40,84)),
   '<path d="M18,20 v-8 l6,-2 v8" fill="none" stroke="#fff" stroke-width="1.6"/><circle cx="16.5" cy="20" r="2" fill="#fff"/><circle cx="22.5" cy="18" r="2" fill="#fff"/>'),
]
for s in SPORTS:
    id, bg, pose, prop = s[0], s[1], s[2], s[3]; sh = s[4] if len(s) > 4 else True
    add("sp_" + id, "sports", prop + fig(pose), BG[bg], shadow=sh)

# ------------------------------------------------------------------ CREATURES: kawaii heads
def eyes(y=52, dx=11, r=3.6, style="dot", c=INK):
    L, R = 50 - dx, 50 + dx
    if style == "dot": return f'<circle cx="{L}" cy="{y}" r="{r}" fill="{c}"/><circle cx="{R}" cy="{y}" r="{r}" fill="{c}"/><circle cx="{L+1.2}" cy="{y-1.3}" r="1.1" fill="#fff"/><circle cx="{R+1.2}" cy="{y-1.3}" r="1.1" fill="#fff"/>'
    if style == "happy": return f'<path d="M{L-3.5},{y+1} q3.5,-5 7,0 M{R-3.5},{y+1} q3.5,-5 7,0" stroke="{c}" stroke-width="2.2" fill="none" stroke-linecap="round"/>'
    if style == "big": return (f'<circle cx="{L}" cy="{y}" r="{r+2.4}" fill="#fff"/><circle cx="{R}" cy="{y}" r="{r+2.4}" fill="#fff"/>'
                               f'<circle cx="{L+.8}" cy="{y+.5}" r="{r}" fill="{c}"/><circle cx="{R+.8}" cy="{y+.5}" r="{r}" fill="{c}"/><circle cx="{L+2}" cy="{y-1}" r="1.2" fill="#fff"/><circle cx="{R+2}" cy="{y-1}" r="1.2" fill="#fff"/>')
    if style == "sleepy": return f'<path d="M{L-3.5},{y} q3.5,3.5 7,0 M{R-3.5},{y} q3.5,3.5 7,0" stroke="{c}" stroke-width="2.2" fill="none" stroke-linecap="round"/>'
    return ""
def mouth(y=61, kind="smile", c=INK):
    if kind == "smile": return f'<path d="M46,{y} q4,4 8,0" stroke="{c}" stroke-width="2" fill="none" stroke-linecap="round"/>'
    if kind == "cat": return f'<path d="M46,{y} q2,3 4,0 q2,3 4,0" stroke="{c}" stroke-width="1.8" fill="none" stroke-linecap="round"/>'
    if kind == "open": return f'<path d="M45,{y-1} q5,9 10,0 z" fill="{c}"/><path d="M47.5,{y+2.5} q2.5,2 5,0" fill="#FF7A8A"/>'
    if kind == "o": return f'<ellipse cx="50" cy="{y+1}" rx="2.6" ry="3.2" fill="{c}"/>'
    if kind == "fang": return f'<path d="M45,{y} q5,4 10,0" stroke="{c}" stroke-width="2" fill="none" stroke-linecap="round"/><path d="M47,{y+1.2} l1.4,3 l1.4,-2.4" fill="#fff"/>'
    return ""
def cheeks(y=59, c="#FF8FA3", o=.55): return f'<ellipse cx="33" cy="{y}" rx="4.5" ry="2.8" fill="{c}" opacity="{o}"/><ellipse cx="67" cy="{y}" rx="4.5" ry="2.8" fill="{c}" opacity="{o}"/>'
def head(c, rx=30, ry=27, cy=54): return f'<ellipse cx="50" cy="{cy}" rx="{rx}" ry="{ry}" fill="{c}"/><ellipse cx="44" cy="{cy-ry*0.55}" rx="{rx*0.45}" ry="{ry*0.22}" fill="#fff" opacity=".18"/>'
def body(c): return f'<path d="M24,100 C24,80 76,80 76,100 Z" fill="{c}"/>'

C = []
def cr(id, bg, svg_): C.append((id, bg, svg_))
cr("cat", "peach", body("#F2A65A")+'<path d="M24,44 L28,18 L44,32 Z M76,44 L72,18 L56,32 Z" fill="#F2A65A"/><path d="M29,36 L30,24 L39,31 Z M71,36 L70,24 L61,31 Z" fill="#FFC9C9"/>'+head("#F2A65A")+'<path d="M50,27 v8 M44,28 l2,7 M56,28 l-2,7" stroke="#D9843A" stroke-width="2" stroke-linecap="round"/>'+eyes()+'<path d="M48,57 h4 l-2,2.4 z" fill="#E86A7A"/>'+mouth(60,"cat")+cheeks()+'<path d="M22,56 h10 M22,61 l10,-2 M78,56 h-10 M78,61 l-10,-2" stroke="#fff" stroke-width="1" opacity=".8"/>')
cr("dog", "sky", body("#C9925E")+'<ellipse cx="22" cy="52" rx="8" ry="16" fill="#8A5A3B" transform="rotate(15 22 52)"/><ellipse cx="78" cy="52" rx="8" ry="16" fill="#8A5A3B" transform="rotate(-15 78 52)"/>'+head("#E0B080")+'<ellipse cx="50" cy="63" rx="13" ry="9" fill="#FFF3E2"/>'+eyes(50)+'<ellipse cx="50" cy="58" rx="4.2" ry="3" fill="'+INK+'"/>'+mouth(63,"open")+'<ellipse cx="38" cy="47" rx="6" ry="7" fill="#8A5A3B" opacity=".5"/>')
cr("bear", "sand", body("#9C6B47")+'<circle cx="27" cy="31" r="9" fill="#9C6B47"/><circle cx="73" cy="31" r="9" fill="#9C6B47"/><circle cx="27" cy="31" r="4.5" fill="#E7B98E"/><circle cx="73" cy="31" r="4.5" fill="#E7B98E"/>'+head("#9C6B47")+'<ellipse cx="50" cy="62" rx="11" ry="8" fill="#E7B98E"/>'+eyes(51)+'<ellipse cx="50" cy="58.5" rx="3.6" ry="2.6" fill="'+INK+'"/>'+mouth(62,"cat"))
cr("panda", "mint", body("#2E2A36")+'<circle cx="27" cy="31" r="9" fill="#2E2A36"/><circle cx="73" cy="31" r="9" fill="#2E2A36"/>'+head("#FFFFFF")+'<ellipse cx="38" cy="52" rx="7" ry="8.5" fill="#2E2A36" transform="rotate(-25 38 52)"/><ellipse cx="62" cy="52" rx="7" ry="8.5" fill="#2E2A36" transform="rotate(25 62 52)"/>'+eyes(52, c="#fff", r=2.6).replace('fill="#fff"/><circle', 'fill="#2E2A36"/><circle',0)+'<ellipse cx="50" cy="60" rx="3.4" ry="2.4" fill="#2E2A36"/>'+mouth(63,"cat","#2E2A36")+cheeks(62))
cr("fox", "coral", body("#F07A3A")+'<path d="M22,46 L26,14 L46,32 Z M78,46 L74,14 L54,32 Z" fill="#F07A3A"/><path d="M27,34 L28,22 L38,30 Z M73,34 L72,22 L62,30 Z" fill="#3A2A2A"/>'+head("#F07A3A", 30, 26)+'<path d="M20,56 C34,58 42,70 50,74 C58,70 66,58 80,56 C78,74 64,82 50,82 C36,82 22,74 20,56 Z" fill="#FFF1E6"/>'+eyes(51)+'<ellipse cx="50" cy="62" rx="3.4" ry="2.4" fill="'+INK+'"/>'+mouth(65,"smile"))
cr("bunny", "pink", body("#F4EEF6")+'<ellipse cx="38" cy="20" rx="7" ry="20" fill="#F4EEF6" transform="rotate(-8 38 20)"/><ellipse cx="62" cy="20" rx="7" ry="20" fill="#F4EEF6" transform="rotate(8 62 20)"/><ellipse cx="38" cy="22" rx="3.4" ry="14" fill="#FFB3C9" transform="rotate(-8 38 22)"/><ellipse cx="62" cy="22" rx="3.4" ry="14" fill="#FFB3C9" transform="rotate(8 62 22)"/>'+head("#F4EEF6", 28, 25, 58)+eyes(56, style="big", r=2.8)+'<path d="M48,62 h4 l-2,2.2 z" fill="#FF8FA3"/>'+mouth(64.5,"cat")+cheeks(63))
cr("koala", "slate", body("#9AA3AE")+'<circle cx="22" cy="40" r="14" fill="#9AA3AE"/><circle cx="78" cy="40" r="14" fill="#9AA3AE"/><circle cx="22" cy="40" r="8" fill="#E8E1EA"/><circle cx="78" cy="40" r="8" fill="#E8E1EA"/>'+head("#B4BCC6")+eyes(50)+'<ellipse cx="50" cy="59" rx="6" ry="8" fill="#3A3A48"/>'+mouth(69,"smile"))
cr("tiger", "sun", body("#F59A2C")+'<circle cx="27" cy="32" r="8" fill="#F59A2C"/><circle cx="73" cy="32" r="8" fill="#F59A2C"/><circle cx="27" cy="32" r="4" fill="#FFF1D6"/><circle cx="73" cy="32" r="4" fill="#FFF1D6"/>'+head("#F59A2C")+'<path d="M50,28 v9 M42,29 l2,7 M58,29 l-2,7 M20,50 h8 M21,57 h7 M80,50 h-8 M79,57 h-7" stroke="#3A2A2A" stroke-width="2.4" stroke-linecap="round"/><ellipse cx="50" cy="64" rx="14" ry="10" fill="#FFF1D6"/>'+eyes(51)+'<path d="M47,59 h6 l-3,3 z" fill="#E86A7A"/>'+mouth(63,"cat"))
cr("lion", "peach", body("#E8A84A")+''.join(f'<circle cx="{50+33*math.cos(math.radians(a))}" cy="{54+31*math.sin(math.radians(a))}" r="11" fill="#B5622E"/>' for a in range(0,360,30))+head("#F2C063", 27, 25)+eyes(51)+'<ellipse cx="50" cy="60" rx="9" ry="6" fill="#FFE7B5"/><path d="M47,57.5 h6 l-3,3 z" fill="'+INK+'"/>'+mouth(62,"cat"))
cr("monkey", "forest", body("#8A5A3B")+'<circle cx="20" cy="54" r="9" fill="#8A5A3B"/><circle cx="80" cy="54" r="9" fill="#8A5A3B"/><circle cx="20" cy="54" r="5" fill="#F2C9A0"/><circle cx="80" cy="54" r="5" fill="#F2C9A0"/>'+head("#8A5A3B")+'<path d="M30,52 C30,38 46,38 50,46 C54,38 70,38 70,52 C70,72 30,72 30,52 Z" fill="#F2C9A0"/>'+eyes(51)+'<circle cx="47.5" cy="59" r="1.1" fill="'+INK+'"/><circle cx="52.5" cy="59" r="1.1" fill="'+INK+'"/>'+mouth(63,"smile"))
cr("pig", "pink", body("#FFA8BE")+'<path d="M26,40 L24,22 L40,32 Z M74,40 L76,22 L60,32 Z" fill="#FF8EAA"/>'+head("#FFB8CA")+eyes(49)+'<ellipse cx="50" cy="60" rx="9" ry="6.5" fill="#FF8EAA"/><ellipse cx="46.5" cy="60" rx="1.6" ry="2.2" fill="#C2526E"/><ellipse cx="53.5" cy="60" rx="1.6" ry="2.2" fill="#C2526E"/>'+cheeks(58))
cr("cow", "lime", body("#FFFFFF")+'<path d="M28,32 q-8,-10 -4,-16 M72,32 q8,-10 4,-16" stroke="#E9D2A8" stroke-width="4" stroke-linecap="round" fill="none"/><ellipse cx="20" cy="44" rx="9" ry="5" fill="#fff"/><ellipse cx="80" cy="44" rx="9" ry="5" fill="#fff"/>'+head("#FFFFFF")+'<path d="M58,30 C70,30 78,38 76,48 C68,46 60,40 58,30 Z" fill="#2E2A36"/>'+eyes(49)+'<ellipse cx="50" cy="65" rx="15" ry="10" fill="#FFB8CA"/><ellipse cx="45" cy="64" rx="1.8" ry="2.4" fill="#C2526E"/><ellipse cx="55" cy="64" rx="1.8" ry="2.4" fill="#C2526E"/>')
cr("frog", "teal", body("#5BC46A")+'<circle cx="34" cy="36" r="11" fill="#5BC46A"/><circle cx="66" cy="36" r="11" fill="#5BC46A"/>'+head("#5BC46A", 32, 24, 58)+'<circle cx="34" cy="35" r="6.5" fill="#fff"/><circle cx="66" cy="35" r="6.5" fill="#fff"/><circle cx="35" cy="36" r="3.6" fill="'+INK+'"/><circle cx="67" cy="36" r="3.6" fill="'+INK+'"/><path d="M36,62 q14,10 28,0" stroke="'+INK+'" stroke-width="2.2" fill="none" stroke-linecap="round"/>'+cheeks(62))
cr("owl", "night", body("#8B6A55")+'<path d="M22,40 L28,20 L40,32 Z M78,40 L72,20 L60,32 Z" fill="#8B6A55"/>'+head("#A07D63")+'<ellipse cx="50" cy="66" rx="16" ry="12" fill="#E7D2B8"/><circle cx="38" cy="50" r="10" fill="#FFF3D6"/><circle cx="62" cy="50" r="10" fill="#FFF3D6"/><circle cx="38" cy="50" r="5" fill="'+INK+'"/><circle cx="62" cy="50" r="5" fill="'+INK+'"/><circle cx="40" cy="48" r="1.6" fill="#fff"/><circle cx="64" cy="48" r="1.6" fill="#fff"/><path d="M46,58 L54,58 L50,65 Z" fill="#F5A623"/>')
cr("penguin", "ocean", body("#2E2A36")+head("#2E2A36", 30, 28)+'<path d="M50,40 C66,36 74,52 70,64 C66,76 34,76 30,64 C26,52 34,36 50,40 Z" fill="#FFFFFF"/>'+eyes(53)+'<path d="M45,58 L55,58 L50,63 Z" fill="#F5A623"/>'+cheeks(60))
cr("chick", "sun", body("#FFD84D")+'<path d="M50,26 q-4,-8 2,-10 M50,26 q6,-6 8,0" stroke="#F5A623" stroke-width="2.4" fill="none" stroke-linecap="round"/>'+head("#FFD84D")+eyes(52, style="big", r=2.6)+'<path d="M45,59 L55,59 L50,65 Z" fill="#F57C23"/>'+cheeks(61))
cr("mouse", "slate", body("#B9B4C6")+'<circle cx="24" cy="32" r="14" fill="#B9B4C6"/><circle cx="76" cy="32" r="14" fill="#B9B4C6"/><circle cx="24" cy="32" r="8" fill="#FFC1D2"/><circle cx="76" cy="32" r="8" fill="#FFC1D2"/>'+head("#C9C4D6", 26, 25, 58)+eyes(56)+'<circle cx="50" cy="63" r="2.6" fill="#FF8FA3"/><path d="M30,62 h12 M30,67 l12,-3 M70,62 h-12 M70,67 l-12,-3" stroke="#7D7890" stroke-width="1"/>')
cr("raccoon", "sand", body("#8C8A99")+'<path d="M24,42 L26,20 L42,32 Z M76,42 L74,20 L58,32 Z" fill="#8C8A99"/>'+head("#A9A7B6")+'<path d="M24,52 C30,42 44,44 50,50 C56,44 70,42 76,52 C70,60 58,58 50,54 C42,58 30,60 24,52 Z" fill="#2E2A36"/>'+eyes(51, c="#fff", r=2.4)+'<path d="M32,60 C40,74 60,74 68,60 C62,66 38,66 32,60 Z" fill="#F2EEF6"/><ellipse cx="50" cy="61" rx="3.2" ry="2.3" fill="#2E2A36"/>')
cr("hamster", "peach", body("#F1B26C")+'<circle cx="28" cy="32" r="7" fill="#F1B26C"/><circle cx="72" cy="32" r="7" fill="#F1B26C"/>'+head("#F1B26C", 32, 27)+'<path d="M24,58 C30,72 70,72 76,58 C72,80 28,80 24,58 Z" fill="#FFF3E2"/><circle cx="26" cy="62" r="8" fill="#FFF3E2"/><circle cx="74" cy="62" r="8" fill="#FFF3E2"/>'+eyes(50)+'<path d="M48.5,57 h3 l-1.5,1.8 z" fill="#E86A7A"/>'+mouth(60,"cat")+cheeks(58))
cr("sheep", "sky", body("#F6F2EA")+''.join(f'<circle cx="{50+30*math.cos(math.radians(a))}" cy="{50+26*math.sin(math.radians(a))}" r="10" fill="#F6F2EA"/>' for a in range(0,360,36))+'<ellipse cx="50" cy="58" rx="18" ry="19" fill="#3E3848"/>'+eyes(55, c="#fff", r=2.4)+mouth(66,"smile","#fff")+'<ellipse cx="34" cy="50" rx="7" ry="3.5" fill="#3E3848" transform="rotate(25 34 50)"/><ellipse cx="66" cy="50" rx="7" ry="3.5" fill="#3E3848" transform="rotate(-25 66 50)"/>')
cr("unicorn", "lilac", body("#FFFFFF")+'<path d="M50,8 L45,30 L55,30 Z" fill="#FFD36B"/><path d="M46.5,24 l7,-2 M47.5,18 l5,-1.5" stroke="#F5A623" stroke-width="1.2"/><path d="M26,40 L28,22 L40,32 Z M74,40 L72,22 L60,32 Z" fill="#FFFFFF"/>'+head("#FFFFFF")+'<path d="M58,28 C72,26 82,38 78,56 C74,46 66,40 56,36 Z" fill="#FF8DBB"/><path d="M60,32 C70,34 76,42 76,50" stroke="#8BD3FF" stroke-width="3" fill="none"/>'+eyes(53, style="happy")+mouth(62,"smile")+cheeks(60))
cr("dragon", "berry", body("#5BC46A")+'<path d="M30,34 L22,14 L40,28 Z M70,34 L78,14 L60,28 Z" fill="#FFD36B"/><path d="M20,52 L8,46 L16,58 Z M80,52 L92,46 L84,58 Z" fill="#3E9E62"/>'+head("#5BC46A")+'<path d="M38,30 l4,-6 l4,6 M54,30 l4,-6 l4,6" fill="#3E9E62"/><ellipse cx="50" cy="64" rx="16" ry="9" fill="#B6EBA0"/>'+eyes(50, style="big", r=2.8)+'<circle cx="45" cy="62" r="1.3" fill="#2B5A3A"/><circle cx="55" cy="62" r="1.3" fill="#2B5A3A"/>'+mouth(66,"fang"))
cr("dino", "lime", body("#7AC8E8")+''.join(f'<path d="M{x},{y} l5,-9 l5,9 z" fill="#FF8C5A"/>' for x,y in [(30,32),(40,27),(50,25),(60,27)])+head("#7AC8E8", 30, 26)+eyes(50)+mouth(61,"open")+cheeks(58)+'<circle cx="66" cy="44" r="2.4" fill="#5AA8C8"/><circle cx="34" cy="64" r="2" fill="#5AA8C8"/>')
cr("robot", "slate", '<rect x="26" y="80" width="48" height="24" rx="6" fill="#8E9AAF"/><line x1="50" y1="28" x2="50" y2="14" stroke="#5A6478" stroke-width="2.5"/><circle cx="50" cy="12" r="4.5" fill="#FF6B6B"/><rect x="22" y="28" width="56" height="50" rx="14" fill="#C8D2E0"/><rect x="16" y="44" width="6" height="16" rx="3" fill="#8E9AAF"/><rect x="78" y="44" width="6" height="16" rx="3" fill="#8E9AAF"/><rect x="29" y="38" width="42" height="26" rx="10" fill="#2B3448"/><circle cx="40" cy="51" r="5" fill="#5FF2D0"/><circle cx="60" cy="51" r="5" fill="#5FF2D0"/><rect x="40" y="68" width="20" height="4" rx="2" fill="#8E9AAF"/>')
cr("alien", "night", body("#8BE27A")+'<path d="M38,30 L30,12 M62,30 L70,12" stroke="#8BE27A" stroke-width="3" stroke-linecap="round"/><circle cx="30" cy="12" r="4" fill="#FFE066"/><circle cx="70" cy="12" r="4" fill="#FFE066"/><path d="M50,24 C76,24 82,46 78,60 C72,80 28,80 22,60 C18,46 24,24 50,24 Z" fill="#8BE27A"/><ellipse cx="38" cy="52" rx="8" ry="11" fill="'+INK+'" transform="rotate(-20 38 52)"/><ellipse cx="62" cy="52" rx="8" ry="11" fill="'+INK+'" transform="rotate(20 62 52)"/><circle cx="36" cy="48" r="2" fill="#fff"/><circle cx="60" cy="48" r="2" fill="#fff"/>'+mouth(68,"smile"))
cr("ghost", "lilac", '<path d="M26,92 V50 C26,24 74,24 74,50 V92 L66,86 L58,92 L50,86 L42,92 L34,86 Z" fill="#FFFFFF"/><ellipse cx="42" cy="34" rx="10" ry="5" fill="#fff" opacity=".6"/>'+eyes(52, style="big", r=3)+mouth(64,"o")+cheeks(60, "#B9A5FF", .7), )
cr("cyclops", "coral", body("#B57CFF")+'<path d="M32,32 L26,14 L42,28 Z M68,32 L74,14 L58,28 Z" fill="#FFE066"/>'+head("#B57CFF", 31, 28)+'<circle cx="50" cy="48" r="12" fill="#fff"/><circle cx="51" cy="49" r="6.5" fill="'+INK+'"/><circle cx="53.5" cy="46.5" r="2" fill="#fff"/>'+'<path d="M38,64 q12,10 24,0 z" fill="'+INK+'"/><path d="M42,64.5 l2,3 l2,-3 M54,64.5 l2,3 l2,-3" fill="#fff"/>')
cr("slime", "mint", '<path d="M18,88 C14,62 30,30 50,30 C70,30 86,62 82,88 C70,94 30,94 18,88 Z" fill="#5FD6A4"/><ellipse cx="38" cy="44" rx="9" ry="5" fill="#fff" opacity=".45"/><circle cx="72" cy="70" r="3" fill="#fff" opacity=".35"/>'+eyes(62, style="dot", r=3.4)+mouth(72,"smile")+cheeks(69))
cr("yeti", "sky", body("#EAF3FF")+''.join(f'<circle cx="{50+30*math.cos(math.radians(a))}" cy="{52+28*math.sin(math.radians(a))}" r="9" fill="#EAF3FF"/>' for a in range(0,360,30))+'<path d="M30,28 L24,10 L40,24 Z M70,28 L76,10 L60,24 Z" fill="#9BB4D6"/><ellipse cx="50" cy="56" rx="19" ry="17" fill="#9BB4D6"/>'+eyes(52, c=INK, r=3)+mouth(62,"fang"))
cr("octopus", "ocean", ''.join(f'<path d="M{x},70 C{x-4},86 {x+6},92 {x+2},98" stroke="#FF7E9A" stroke-width="7" stroke-linecap="round" fill="none"/>' for x in (26,38,50,62,74))+'<ellipse cx="50" cy="50" rx="30" ry="28" fill="#FF7E9A"/><ellipse cx="42" cy="34" rx="12" ry="6" fill="#fff" opacity=".3"/><circle cx="68" cy="40" r="3" fill="#E85A7A"/><circle cx="32" cy="42" r="2.2" fill="#E85A7A"/>'+eyes(54, style="big", r=2.8)+mouth(64,"smile")+cheeks(62, "#fff", .4), )
cr("axolotl", "pink", body("#FFC4DA")+''.join(f'<path d="M{x},{y} q{dx},-6 {dx*1.6},2" stroke="#FF6FA0" stroke-width="3.5" stroke-linecap="round" fill="none"/>' for x,y,dx in [(24,42,-8),(23,52,-9),(25,62,-8),(76,42,8),(77,52,9),(75,62,8)])+head("#FFC4DA", 30, 25)+eyes(52, r=3.2)+mouth(61,"smile")+cheeks(59))
cr("sloth", "forest", body("#A88B6E")+head("#A88B6E", 29, 27)+'<ellipse cx="50" cy="56" rx="24" ry="19" fill="#E9D7BF"/><ellipse cx="38" cy="52" rx="8" ry="5" fill="#5A4636" transform="rotate(-20 38 52)"/><ellipse cx="62" cy="52" rx="8" ry="5" fill="#5A4636" transform="rotate(20 62 52)"/>'+eyes(52, style="happy", c="#fff")+'<ellipse cx="50" cy="60" rx="3.4" ry="2.4" fill="#3A2E26"/>'+mouth(64,"smile","#3A2E26"))
cr("hedgehog", "sand", ''.join(f'<path d="M{50+28*math.cos(math.radians(a))},{54+26*math.sin(math.radians(a))} L{50+42*math.cos(math.radians(a+6))},{54+40*math.sin(math.radians(a+6))} L{50+28*math.cos(math.radians(a+12))},{54+26*math.sin(math.radians(a+12))} Z" fill="#7A5A44"/>' for a in range(180,372,14))+body("#7A5A44")+head("#E9C9A0", 26, 24, 58)+eyes(55)+'<circle cx="50" cy="63" r="2.8" fill="'+INK+'"/>'+cheeks(62))
cr("bee", "sun", body("#2E2A36")+'<ellipse cx="30" cy="22" rx="10" ry="7" fill="#fff" opacity=".85" transform="rotate(-30 30 22)"/><ellipse cx="70" cy="22" rx="10" ry="7" fill="#fff" opacity=".85" transform="rotate(30 70 22)"/><path d="M42,30 q-4,-10 -10,-12 M58,30 q4,-10 10,-12" stroke="#2E2A36" stroke-width="2" fill="none"/>'+head("#FFD84D")+'<path d="M22,62 C34,58 66,58 78,62 L76,70 C64,66 36,66 24,70 Z" fill="#2E2A36"/>'+eyes(50)+mouth(57,"smile")+cheeks(56))
for i,(id,bg,s) in enumerate(C): add("cr_" + id, "creatures", s, BG[bg], shadow=False)

# ------------------------------------------------------------------ FACES · FOOD · ICONS
F = []
def blob(c, extra=""): return f'<path d="M50,18 C76,18 84,38 84,54 C84,76 70,86 50,86 C30,86 16,76 16,54 C16,38 24,18 50,18 Z" fill="{c}"/><ellipse cx="40" cy="30" rx="12" ry="5" fill="#fff" opacity=".25"/>' + extra
F += [
 ("face_smile", "sky", blob("#FFD84D") + eyes(48, r=4) + '<path d="M38,62 q12,12 24,0" stroke="'+INK+'" stroke-width="3" fill="none" stroke-linecap="round"/>' + cheeks(60)),
 ("face_wink", "coral", blob("#FFD84D") + '<circle cx="39" cy="48" r="4" fill="'+INK+'"/><path d="M56,49 q5,-5 10,0" stroke="'+INK+'" stroke-width="3" fill="none" stroke-linecap="round"/>' + '<path d="M38,62 q12,12 24,0 z" fill="'+INK+'"/>' + cheeks(60)),
 ("face_cool", "teal", blob("#FFD84D") + '<path d="M26,44 h48 v4 q-2,10 -12,10 q-9,0 -11,-8 h-2 q-2,8 -11,8 q-10,0 -12,-10 z" fill="'+INK+'"/><path d="M33,46 l6,0" stroke="#fff" stroke-width="1.5" opacity=".6"/>' + '<path d="M40,66 q10,6 20,0" stroke="'+INK+'" stroke-width="3" fill="none" stroke-linecap="round"/>'),
 ("face_stars", "lilac", blob("#FFD84D") + ''.join(f'<polygon points="{" ".join(f"{cx+6*math.cos(math.radians(-90+i*72))},{48+6*math.sin(math.radians(-90+i*72))} {cx+2.6*math.cos(math.radians(-54+i*72))},{48+2.6*math.sin(math.radians(-54+i*72))}" for i in range(5))}" fill="#FF6B6B"/>' for cx in (38,62)) + '<path d="M38,62 q12,14 24,0 z" fill="'+INK+'"/>'),
 ("face_laugh", "sun", blob("#FFD84D") + eyes(48, style="happy") .replace('stroke-width="2.2"','stroke-width="3"') + '<path d="M34,58 q16,22 32,0 z" fill="'+INK+'"/><path d="M40,66 q10,8 20,0" fill="#FF7A8A"/>'),
 ("face_fire", "berry", blob("#FFD84D") + '<path d="M32,42 l12,4 M68,42 l-12,4" stroke="'+INK+'" stroke-width="3" stroke-linecap="round"/>' + eyes(51, r=3.6) + '<path d="M40,66 h20" stroke="'+INK+'" stroke-width="3" stroke-linecap="round"/>'),
 ("face_sleepy", "night", blob("#FFD84D") + eyes(50, style="sleepy").replace('stroke-width="2.2"','stroke-width="3"') + mouth(64,"o") + '<text x="66" y="30" font-family="Arial" font-weight="700" font-size="12" fill="#fff">z</text><text x="74" y="22" font-family="Arial" font-weight="700" font-size="9" fill="#fff">z</text>'),
 ("face_love", "pink", blob("#FFD84D") + ''.join(f'<path d="M{cx},{54} C{cx-10},{46} {cx-6},{38} {cx},{43} C{cx+6},{38} {cx+10},{46} {cx},{54} Z" fill="#FF4F7B"/>' for cx in (38,62)) + '<path d="M40,64 q10,10 20,0" stroke="'+INK+'" stroke-width="3" fill="none" stroke-linecap="round"/>'),
 ("blob_mint", "navy", blob("#5FF2C0") + eyes(52, style="big", r=3)),
 ("blob_violet", "sun", blob("#A88BFF") + eyes(52, style="big", r=3) + mouth(66,"smile")),
]
FOOD = [
 ("apple", "mint", '<path d="M50,32 C38,22 18,30 20,52 C22,76 38,88 50,82 C62,88 78,76 80,52 C82,30 62,22 50,32 Z" fill="#F0453A"/><path d="M50,32 C50,24 52,18 56,14" stroke="#6B4226" stroke-width="3" stroke-linecap="round" fill="none"/><path d="M54,22 C62,12 74,14 76,18 C68,24 60,26 54,22 Z" fill="#5BC46A"/><ellipse cx="34" cy="46" rx="5" ry="9" fill="#fff" opacity=".35" transform="rotate(20 34 46)"/>'),
 ("banana", "sky", '<path d="M22,30 C26,62 52,82 82,72 C84,68 82,66 78,66 C54,70 36,56 30,28 Z" fill="#FFD84D"/><path d="M30,28 C36,56 54,70 78,66" stroke="#E5B020" stroke-width="2" fill="none"/><path d="M20,30 l4,-8 l6,4 z" fill="#6B4226"/>'),
 ("watermelon", "lime", '<path d="M14,40 A36,36 0 0 0 86,40 Z" fill="#3E9E62"/><path d="M19,40 A31,31 0 0 0 81,40 Z" fill="#E9F7D2"/><path d="M23,40 A27,27 0 0 0 77,40 Z" fill="#FF5A6E"/>' + ''.join(f'<ellipse cx="{x}" cy="{y}" rx="1.6" ry="2.6" fill="{INK}"/>' for x,y in [(36,48),(50,54),(64,48),(43,58),(57,58),(50,45)])),
 ("mango", "teal", '<path d="M56,22 C82,26 86,60 66,78 C50,92 22,84 20,62 C18,44 34,40 42,32 C46,26 50,22 56,22 Z" fill="#FFB020"/><path d="M56,22 C80,28 82,58 66,76" stroke="#FF7A2A" stroke-width="8" stroke-opacity=".35" fill="none" stroke-linecap="round"/><path d="M56,22 C60,14 66,12 70,14" stroke="#6B4226" stroke-width="3" fill="none" stroke-linecap="round"/><path d="M62,16 C70,6 82,10 84,14 C76,20 68,20 62,16 Z" fill="#5BC46A"/>'),
 ("orange", "navy", '<circle cx="50" cy="54" r="30" fill="#FF9A2E"/><circle cx="50" cy="54" r="30" fill="none" stroke="#E57A10" stroke-width="2" stroke-dasharray="1 4"/><ellipse cx="40" cy="42" rx="8" ry="5" fill="#fff" opacity=".35"/><path d="M50,24 C56,14 66,14 70,18 C62,24 56,26 50,24 Z" fill="#5BC46A"/>'),
 ("cherries", "pink", '<path d="M36,62 C40,40 50,24 64,16 M64,62 C62,40 64,26 64,16" stroke="#3E9E62" stroke-width="3" fill="none" stroke-linecap="round"/><circle cx="36" cy="66" r="13" fill="#D7263D"/><circle cx="64" cy="66" r="13" fill="#E53950"/><circle cx="31" cy="61" r="3" fill="#fff" opacity=".5"/><circle cx="59" cy="61" r="3" fill="#fff" opacity=".5"/><path d="M64,16 C72,10 82,14 82,18 C74,22 68,20 64,16 Z" fill="#5BC46A"/>'),
 ("avocado", "sand", '<path d="M50,14 C66,14 80,48 80,62 C80,80 66,90 50,90 C34,90 20,80 20,62 C20,48 34,14 50,14 Z" fill="#3E7A3A"/><path d="M50,20 C63,20 74,50 74,62 C74,76 63,84 50,84 C37,84 26,76 26,62 C26,50 37,20 50,20 Z" fill="#CFE88A"/><circle cx="50" cy="64" r="12" fill="#8A5A3B"/><circle cx="46" cy="60" r="3" fill="#fff" opacity=".3"/>'),
 ("strawberry", "lilac", '<path d="M50,88 C30,76 18,56 22,42 C26,30 40,30 50,34 C60,30 74,30 78,42 C82,56 70,76 50,88 Z" fill="#F0453A"/>' + ''.join(f'<ellipse cx="{x}" cy="{y}" rx="1.2" ry="1.8" fill="#FFE08A"/>' for x,y in [(36,46),(50,48),(64,46),(42,58),(58,58),(50,68),(34,60),(66,60),(46,76),(54,76)]) + '<path d="M50,36 L38,24 L46,30 L50,18 L54,30 L62,24 Z" fill="#5BC46A"/>'),
 ("samosa", "peach", '<path d="M50,16 L84,80 C84,84 82,86 78,86 L22,86 C18,86 16,84 16,80 Z" fill="#E8A040"/><path d="M50,16 L84,80 C84,84 82,86 78,86 L50,86 Z" fill="#C97F2A"/><path d="M50,16 L50,86" stroke="#B36A1E" stroke-width="1.5"/><circle cx="38" cy="66" r="1.4" fill="#8A5A2B"/><circle cx="62" cy="58" r="1.4" fill="#7A4A1E"/><circle cx="44" cy="76" r="1.4" fill="#8A5A2B"/><path d="M28,72 l8,-14" stroke="#fff" stroke-width="2" stroke-linecap="round" opacity=".35"/>'),
 ("chai", "coral", '<path d="M70,46 C82,46 82,66 68,66" stroke="#FFFFFF" stroke-width="5" fill="none"/><path d="M24,40 H74 L68,80 C67,84 64,86 60,86 H38 C34,86 31,84 30,80 Z" fill="#FFFFFF"/><ellipse cx="49" cy="40" rx="25" ry="5" fill="#C98A4E"/><path d="M40,30 C36,24 44,20 40,12 M52,30 C48,24 56,20 52,12 M64,30 C60,24 68,20 64,12" stroke="#fff" stroke-width="2.4" fill="none" stroke-linecap="round" opacity=".8"/><path d="M30,56 H68" stroke="#FF8C5A" stroke-width="3"/>'),
 ("biryani", "teal", '<path d="M14,52 C14,80 86,80 86,52 Z" fill="#C0392B"/><path d="M14,52 C14,80 86,80 86,52" stroke="#922B21" stroke-width="2" fill="none"/>' + ''.join(f'<ellipse cx="{50+r*math.cos(a)}" cy="{48-abs(r*math.sin(a))*.45}" rx="3" ry="1.4" fill="{c}" transform="rotate({int(a*57)%180} {50+r*math.cos(a)} {48-abs(r*math.sin(a))*.45})"/>' for r,a,c in [(r,a,c) for r in (6,14,22,30) for a,c in zip([i*0.7 for i in range(9)], ["#FFF3D6","#FFC94D","#FFF3D6","#F5A623","#FFF3D6","#FFC94D","#fff","#F5A623","#FFF3D6"])]) + '<path d="M18,52 C30,36 70,36 82,52 Z" fill="#FFE7A8" opacity=".7"/><circle cx="40" cy="44" r="4" fill="#8A4A1E"/><circle cx="60" cy="46" r="3.6" fill="#8A4A1E"/><path d="M48,40 l5,-6 l3,4" stroke="#3E9E62" stroke-width="2.2" fill="none"/>'),
 ("jalebi", "sun", '<path d="M50,52 m-4,0 a4,4 0 1,1 8,0 a8,8 0 1,1 -16,0 a12,12 0 1,1 24,0 a16,16 0 1,1 -32,0 a20,20 0 1,1 40,0 a24,24 0 1,1 -48,0" fill="none" stroke="#F57C23" stroke-width="6" stroke-linecap="round"/><path d="M50,52 m-4,0 a4,4 0 1,1 8,0 a8,8 0 1,1 -16,0 a12,12 0 1,1 24,0 a16,16 0 1,1 -32,0 a20,20 0 1,1 40,0" fill="none" stroke="#FFD36B" stroke-width="2" stroke-linecap="round" opacity=".8"/>'),
 ("kebab", "forest", '<line x1="18" y1="82" x2="82" y2="18" stroke="#C9A27A" stroke-width="3" stroke-linecap="round"/>' + ''.join(f'<ellipse cx="{x}" cy="{100-x}" rx="9" ry="7" fill="{c}" transform="rotate(-45 {x} {100-x})"/>' for x,c in [(30,"#8A4A1E"),(42,"#5BC46A"),(54,"#A0522D"),(66,"#E53950"),(76,"#8A4A1E")])),
 ("paratha", "slate", '<circle cx="50" cy="54" r="32" fill="#E8B060"/><circle cx="50" cy="54" r="32" fill="none" stroke="#C98A3E" stroke-width="2"/>' + ''.join(f'<ellipse cx="{x}" cy="{y}" rx="{rx}" ry="{rx*.6}" fill="#B9742A" opacity=".55"/>' for x,y,rx in [(38,44,4),(60,48,5),(46,64,4.5),(64,66,3),(34,60,3),(52,36,3)]) + '<path d="M50,54 m-20,0 a20,20 0 0,1 40,0" fill="none" stroke="#F6D08A" stroke-width="2" opacity=".6"/>'),
]
ICONS = [
 ("moon", "night", '<path d="M58,18 C38,20 24,36 24,54 C24,74 40,88 60,88 C70,88 78,84 84,78 C62,80 44,64 44,44 C44,32 50,22 58,18 Z" fill="#FFE28A"/><polygon points="72,30 74.5,37 82,37 76,41.5 78,48.5 72,44 66,48.5 68,41.5 62,37 69.5,37" fill="#FFE28A"/>'),
 ("mountain", "sky", '<path d="M8,86 L38,34 L52,56 L62,42 L92,86 Z" fill="#4B5C9E"/><path d="M38,34 L46,48 L40,46 L34,52 L30,48 Z" fill="#fff"/><path d="M62,42 L68,52 L62,50 L58,54 Z" fill="#fff"/><circle cx="74" cy="22" r="8" fill="#FFE28A"/>', False),
 ("sun", "coral", ''.join(f'<line x1="{50+26*math.cos(math.radians(a))}" y1="{52+26*math.sin(math.radians(a))}" x2="{50+36*math.cos(math.radians(a))}" y2="{52+36*math.sin(math.radians(a))}" stroke="#FFE28A" stroke-width="5" stroke-linecap="round"/>' for a in range(0,360,45)) + '<circle cx="50" cy="52" r="20" fill="#FFE28A"/>' + eyes(50, r=2.6) + mouth(58,"smile")),
 ("bolt", "navy", '<path d="M56,10 L24,58 H46 L40,92 L76,40 H54 Z" fill="#FFD84D"/><path d="M56,10 L24,58 H46 Z" fill="#FFF0A0"/>'),
 ("heart", "pink", '<path d="M50,86 C20,64 12,48 16,36 C20,22 40,18 50,34 C60,18 80,22 84,36 C88,48 80,64 50,86 Z" fill="#FF4F7B"/><ellipse cx="32" cy="38" rx="6" ry="4" fill="#fff" opacity=".5" transform="rotate(-30 32 38)"/>'),
 ("flame", "night", '<path d="M50,10 C62,30 80,40 78,62 C76,80 64,90 50,90 C36,90 22,80 22,62 C22,48 32,40 36,30 C40,40 44,42 48,42 C46,30 46,20 50,10 Z" fill="#FF7A2A"/><path d="M50,46 C58,56 66,62 64,74 C62,84 56,88 50,88 C44,88 36,84 36,74 C36,64 46,60 50,46 Z" fill="#FFD84D"/>'),
 ("leaf", "mint", '<path d="M22,80 C20,44 44,20 82,18 C82,56 60,80 22,80 Z" fill="#3E9E62"/><path d="M22,80 C40,62 56,46 76,24" stroke="#B6EBA0" stroke-width="2.4" fill="none"/>'),
 ("trophy", "teal", '<path d="M30,18 H70 V40 C70,54 60,62 50,62 C40,62 30,54 30,40 Z" fill="#FFC94D"/><path d="M30,24 H18 C18,40 26,46 32,46 M70,24 H82 C82,40 74,46 68,46" stroke="#FFC94D" stroke-width="5" fill="none"/><rect x="46" y="60" width="8" height="12" fill="#E5A520"/><rect x="34" y="72" width="32" height="10" rx="3" fill="#8A5A3B"/><path d="M40,24 V40" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".55"/>'),
 ("dumbbell", "lilac", '<rect x="30" y="47" width="40" height="6" rx="3" fill="#2B2440"/><rect x="16" y="34" width="10" height="32" rx="4" fill="#2B2440"/><rect x="26" y="38" width="8" height="24" rx="3" fill="#4B4560"/><rect x="74" y="34" width="10" height="32" rx="4" fill="#2B2440"/><rect x="66" y="38" width="8" height="24" rx="3" fill="#4B4560"/>'),
 ("crown", "berry", '<path d="M16,72 L20,30 L36,48 L50,22 L64,48 L80,30 L84,72 Z" fill="#FFC94D"/><rect x="16" y="72" width="68" height="10" rx="3" fill="#E5A520"/><circle cx="50" cy="60" r="5" fill="#E53950"/><circle cx="32" cy="62" r="3.5" fill="#4C8DFF"/><circle cx="68" cy="62" r="3.5" fill="#2FD37A"/>'),
 ("rocket", "navy", '<path d="M50,10 C66,22 70,44 64,68 H36 C30,44 34,22 50,10 Z" fill="#F2F2F7"/><circle cx="50" cy="38" r="7" fill="#4C8DFF" stroke="#2B2440" stroke-width="2"/><path d="M36,56 L24,72 L36,70 Z M64,56 L76,72 L64,70 Z" fill="#E53950"/><path d="M40,68 C40,80 46,86 50,92 C54,86 60,80 60,68 Z" fill="#FF9A2E"/><path d="M45,68 C45,76 48,80 50,84 C52,80 55,76 55,68 Z" fill="#FFE066"/>'),
 ("star", "sky", '<polygon points="50,12 60,38 88,40 66,58 74,86 50,70 26,86 34,58 12,40 40,38" fill="#FFD84D" stroke="#F5A623" stroke-width="2" stroke-linejoin="round"/>' + eyes(52, r=2.6) + mouth(60,"smile")),
 ("rainbow", "sky", ''.join(f'<path d="M{14+i*6},76 A{36-i*6},{36-i*6} 0 0 1 {86-i*6},76" stroke="{c}" stroke-width="6" fill="none"/>' for i,c in enumerate(["#FF5A6E","#FF9A2E","#FFD84D","#5BC46A","#4C8DFF"])) + '<ellipse cx="22" cy="78" rx="12" ry="7" fill="#fff"/><ellipse cx="78" cy="78" rx="12" ry="7" fill="#fff"/>', False),
 ("drop", "ocean", '<path d="M50,12 C50,12 24,46 24,62 C24,78 36,88 50,88 C64,88 76,78 76,62 C76,46 50,12 50,12 Z" fill="#8FD3FF"/><path d="M38,58 C36,68 42,76 50,78" stroke="#fff" stroke-width="3" fill="none" stroke-linecap="round" opacity=".7"/>' + eyes(62, r=2.6) + mouth(70,"smile")),
]
for id,bg,s in F: add("fc_" + id.replace("face_","").replace("blob_","blob_"), "faces", s, BG[bg], shadow=False)
for id,bg,s in FOOD: add("fd_" + id, "food", s, BG[bg])
for t in ICONS:
    id,bg,s = t[0],t[1],t[2]; add("ic_" + id, "icons", s, BG[bg], shadow=(t[3] if len(t) > 3 else True))

def render():
    cats = {}
    with sync_playwright() as p:
        br = p.chromium.launch(); pg = br.new_page(viewport={"width":512,"height":512})
        for id, cat, s in A:
            pg.set_content(f'<html><body style="margin:0">{s}</body></html>'); pg.wait_for_timeout(20)
            pg.screenshot(path=f"{TMP}/{id}.png", clip={"x":0,"y":0,"width":512,"height":512})
            Image.open(f"{TMP}/{id}.png").convert("RGB").resize((192,192), Image.LANCZOS).save(f"{OUT}/{id}.webp", quality=88, method=6)
            cats.setdefault(cat, []).append(id)
        br.close()
    json.dump(cats, open(f"{OUT}/catalog.json","w"), indent=0)
    # sheet
    from PIL import ImageDraw
    n = len(A); cols = 10; T = 96; rows = (n + cols - 1)//cols
    sh = Image.new("RGB", (cols*(T+12)+12, rows*(T+12)+12), "#F2F3F1")
    for i,(id,_,_) in enumerate(A):
        im = Image.open(f"{TMP}/{id}.png").resize((T,T), Image.LANCZOS)
        m = Image.new("L",(T*4,T*4),0); ImageDraw.Draw(m).ellipse((0,0,T*4-1,T*4-1),fill=255)
        sh.paste(im,(12+(i%cols)*(T+12), 12+(i//cols)*(T+12)), m.resize((T,T),Image.LANCZOS))
    sh.save(f"{TMP}/sheet.png"); print(n, {k: len(v) for k,v in cats.items()})

if __name__ == "__main__": render()
