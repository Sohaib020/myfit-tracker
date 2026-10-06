"""Generates the animated notification icons (frame animations played by a ProgressBar, which RemoteViews allow),
the Live Update tracker icons and the two card layouts. Output: app/src/main/res/{drawable,layout}."""
import os
R = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../app/src/main/res"))
D = os.path.join(R, "drawable"); L = os.path.join(R, "layout"); os.makedirs(D, exist_ok=True); os.makedirs(L, exist_ok=True)
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
W = "#FFFFFF"


def vec(body, size=48):
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<vector {NS} android:width="{size}dp" android:height="{size}dp" '
            f'android:viewportWidth="48" android:viewportHeight="48">\n{body}\n</vector>\n')


def circle(cx, cy, r):
    return f"M{cx-r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,0 {2*r:.2f},0a{r:.2f},{r:.2f} 0 1,0 {-2*r:.2f},0"


def fillp(d, c, a=None):
    al = f' android:fillAlpha="{a}"' if a else ""
    return f'<path android:fillColor="{c}"{al} android:pathData="{d}"/>'


def strokep(d, c, w, a=None):
    al = f' android:strokeAlpha="{a}"' if a else ""
    return f'<path android:strokeColor="{c}" android:strokeWidth="{w}" android:strokeLineCap="round" android:strokeLineJoin="round"{al} android:pathData="{d}"/>'


def bg(c):
    return fillp(circle(24, 24, 24), c) + fillp(circle(24, 18, 17), "#FFFFFF", ".10")


def group(body, rot=0, px=24, py=24, s=1.0, tx=0, ty=0):
    return (f'<group android:rotation="{rot}" android:pivotX="{px}" android:pivotY="{py}" android:scaleX="{s}" android:scaleY="{s}" '
            f'android:translateX="{tx}" android:translateY="{ty}">{body}</group>')


def write(name, xml):
    open(os.path.join(D, name + ".xml"), "w").write(xml)


def anim(name, frames, ms):
    items = "".join(f'<item android:drawable="@drawable/{f}" android:duration="{ms}"/>' for f in frames)
    write(name, f'<?xml version="1.0" encoding="utf-8"?>\n<animation-list {NS} android:oneshot="false">{items}</animation-list>\n')


def fig(p, w=3.6):
    s = lambda pt: (6 + pt[0] * 0.36, 5 + pt[1] * 0.38)
    def pl(*pts): return "M" + " L".join(f"{s(q)[0]:.1f},{s(q)[1]:.1f}" for q in pts)
    o = strokep(pl(p["neck"], p["le"], p["lh"]), W, w, ".6") + strokep(pl(p["hip"], p["lk"], p["lf"]), W, w + .3, ".6")
    o += strokep(pl(p["neck"], p["hip"]), W, w + 1.6) + strokep(pl(p["hip"], p["rk"], p["rf"]), W, w + .3) + strokep(pl(p["neck"], p["re"], p["rh"]), W, w)
    hx, hy = s(p["head"]); o += fillp(circle(hx, hy, 3.6), W)
    return o


def P(**k): return {kk: tuple(v) for kk, v in k.items()}


KINDS = {}
RUN_C = "#22C27A"
run = [
    P(head=(56, 20), neck=(53, 30), hip=(48, 54), le=(64, 38), lh=(72, 32), re=(42, 40), rh=(34, 48), lk=(60, 68), lf=(56, 86), rk=(36, 66), rf=(24, 76)),
    P(head=(55, 18), neck=(52, 28), hip=(48, 52), le=(58, 40), lh=(62, 48), re=(46, 40), rh=(44, 50), lk=(52, 68), lf=(46, 86), rk=(44, 66), rf=(40, 84)),
    P(head=(56, 20), neck=(53, 30), hip=(48, 54), le=(42, 40), lh=(34, 48), re=(64, 38), rh=(72, 32), lk=(36, 66), lf=(24, 76), rk=(60, 68), rf=(56, 86)),
    P(head=(55, 18), neck=(52, 28), hip=(48, 52), le=(46, 40), lh=(44, 50), re=(58, 40), rh=(62, 48), lk=(44, 66), lf=(40, 84), rk=(52, 68), rf=(46, 86)),
]
for i, p in enumerate(run): write(f"nf_run_{i}", vec(bg(RUN_C) + fig(p)))
anim("na_run", [f"nf_run_{i}" for i in range(4)], 140); KINDS["run"] = RUN_C

LIFT_C = "#FF7A2F"


def lift(h):
    by = 22 - 10 * h; ey = 30 - 6 * h
    bar = strokep(f"M9,{by:.1f} L39,{by:.1f}", "#2B2440", 2.4) + fillp(f"M7,{by-5:.1f}h4v10h-4z", "#2B2440") + fillp(f"M37,{by-5:.1f}h4v10h-4z", "#2B2440")
    body = strokep("M24,24 L24,34", W, 5.2) + strokep("M24,34 L20,42 M24,34 L28,42", W, 3.8) + fillp(circle(24, 18.5 if h < .5 else 19, 3.4), W)
    arms = strokep(f"M24,25 L16,{ey:.1f} L16,{by:.1f} M24,25 L32,{ey:.1f} L32,{by:.1f}", W, 3.2)
    return bg(LIFT_C) + arms + body + bar


for i, h in enumerate([0, .5, 1, .5]): write(f"nf_lift_{i}", vec(lift(h)))
anim("na_lift", [f"nf_lift_{i}" for i in range(4)], 220); KINDS["lift"] = LIFT_C

REST_C = "#3F7BFF"
write("nf_rest_0", vec(bg(REST_C) + strokep(circle(24, 24, 12), W, 3.4, ".25") + strokep("M24,12 A12,12 0 0,1 36,24", W, 3.6) + fillp(circle(24, 24, 2.4), W)))
write("na_rest", f'<?xml version="1.0" encoding="utf-8"?>\n<rotate {NS} android:drawable="@drawable/nf_rest_0" android:pivotX="50%" android:pivotY="50%" android:fromDegrees="0" android:toDegrees="360"/>\n')
KINDS["rest"] = REST_C

FL_C = "#FF5A36"


def flame(k):
    t = [0, 1.5, -1.2][k]
    outer = (f"M24,{8+abs(t):.1f} C{29+t:.1f},15 36,20 35,29 C34,36 29.5,40 24,40 C18.5,40 14,36 14,29 C14,23 18,20 19.5,15 "
             f"C21,19 22.5,20 23.5,20 C22.8,15.5 23,12 24,{8+abs(t):.1f} Z")
    inner = f"M24,{22+t*.5:.1f} C27.5,26 30,28.5 29.5,32.5 C29,36 26.5,38 24,38 C21.5,38 19,36 18.5,32.5 C18.5,28.5 22,27 24,{22+t*.5:.1f} Z"
    return bg(FL_C) + fillp(outer, "#FFE7A3") + fillp(inner, "#FFFFFF")


for i in range(3): write(f"nf_flame_{i}", vec(flame(i)))
anim("na_flame", ["nf_flame_0", "nf_flame_1", "nf_flame_2", "nf_flame_1"], 160); KINDS["flame"] = FL_C

DR_C = "#2F9BFF"


def drop(k):
    off = [0, 3, 6][k]
    shape = "M24,8 C24,8 13,21 13,29 C13,35.5 18,40 24,40 C30,40 35,35.5 35,29 C35,21 24,8 24,8 Z"
    wave = f"M{4-off},30 q4,-3 8,0 t8,0 t8,0 t8,0 t8,0 t8,0 V44 H{4-off} Z"
    return bg(DR_C) + fillp(shape, W, ".35") + f'<group><clip-path android:pathData="{shape}"/>{fillp(wave, W)}</group>'


for i in range(3): write(f"nf_drop_{i}", vec(drop(i)))
anim("na_drop", [f"nf_drop_{i}" for i in range(3)], 180); KINDS["drop"] = DR_C

PI_C = "#9B6BFF"


def pill(ty, rot):
    cap = fillp("M16,20 h8 v12 h-8 a6,6 0 0,1 0,-12 Z", W) + fillp("M24,20 h8 a6,6 0 0,1 0,12 h-8 Z", "#FFD1F0")
    return bg(PI_C) + group(cap, rot=rot, ty=ty) + fillp("M17,39 h14 a2,1.4 0 0,1 0,2.8 h-14 a2,1.4 0 0,1 0,-2.8 Z", "#000000", ".15")


for i, (ty, r) in enumerate([(0, -25), (-4, -12), (-6, 0), (-4, 12)]): write(f"nf_pill_{i}", vec(pill(ty, r)))
anim("na_pill", [f"nf_pill_{i}" for i in range(4)], 160); KINDS["pill"] = PI_C

BE_C = "#FFA51F"


def bell(rot):
    b = fillp("M24,10 C17.5,10 15,15 15,21 V28 L12,32 H36 L33,28 V21 C33,15 30.5,10 24,10 Z", W) + fillp(circle(24, 35, 3), W) + fillp(circle(24, 9, 1.8), W)
    return bg(BE_C) + group(b, rot=rot, py=10)


for i, r in enumerate([-16, 0, 16, 0]): write(f"nf_bell_{i}", vec(bell(r)))
anim("na_bell", [f"nf_bell_{i}" for i in range(4)], 120); KINDS["bell"] = BE_C

MO_C = "#1F8F6E"


def moon(s):
    cres = fillp("M27,10 C19,11 13,17.5 13,25 C13,33.5 19.5,40 28,40 C32,40 35.5,38.5 38,36 C29,37 21.5,30 21.5,21.5 C21.5,16.5 23.8,12.5 27,10 Z", "#FFE9A8")
    st = group(fillp("M34,13 L35.2,16.8 L39,18 L35.2,19.2 L34,23 L32.8,19.2 L29,18 L32.8,16.8 Z", W), s=s, px=34, py=18)
    return bg(MO_C) + cres + st


for i, s in enumerate([.6, 1.0, .8]): write(f"nf_moon_{i}", vec(moon(s)))
anim("na_moon", [f"nf_moon_{i}" for i in range(3)], 260); KINDS["moon"] = MO_C

TR_C = "#F2A900"


def trophy(k):
    t = fillp("M16,11 H32 V20 C32,25.5 28.5,29 24,29 C19.5,29 16,25.5 16,20 Z", W) + strokep("M16,14 H11 C11,20 14,22 16.5,22 M32,14 H37 C37,20 34,22 31.5,22", W, 2.4)
    t += fillp("M22,29 h4 v5 h-4z", W) + fillp("M17,34 h14 a1.5,1.5 0 0,1 1.5,1.5 v2.5 h-17 v-2.5 a1.5,1.5 0 0,1 1.5,-1.5 Z", W)
    sp = [(10, 9), (38, 8), (39, 30), (9, 31)]
    sparks = "".join(fillp(f"M{x},{y-2.6} L{x+.8},{y-.8} L{x+2.6},{y} L{x+.8},{y+.8} L{x},{y+2.6} L{x-.8},{y+.8} L{x-2.6},{y} L{x-.8},{y-.8} Z", "#FFF4C2")
                     for j, (x, y) in enumerate(sp) if (j + k) % 2 == 0)
    return bg(TR_C) + t + sparks


for i in range(2): write(f"nf_trophy_{i}", vec(trophy(i)))
anim("na_trophy", ["nf_trophy_0", "nf_trophy_1"], 300); KINDS["trophy"] = TR_C

HE_C = "#FF4F86"


def heart(s):
    return bg(HE_C) + group(fillp("M24,37 C13,29 10,23.5 11.5,19 C13,14 19.5,12.5 24,18.5 C28.5,12.5 35,14 36.5,19 C38,23.5 35,29 24,37 Z", W), s=s, py=25)


for i, s in enumerate([1.0, 1.14, 1.0, 0.94]): write(f"nf_heart_{i}", vec(heart(s)))
anim("na_heart", [f"nf_heart_{i}" for i in range(4)], 150); KINDS["heart"] = HE_C

SU_C = "#E2453C"


def sugar(k):
    r = [8, 13, 18][k]; a = [".55", ".35", ".12"][k]
    return bg(SU_C) + strokep(circle(24, 26, r), W, 2, a) + fillp("M24,13 C24,13 17,22 17,27.5 C17,31.5 20,34.5 24,34.5 C28,34.5 31,31.5 31,27.5 C31,22 24,13 24,13 Z", W)


for i in range(3): write(f"nf_sugar_{i}", vec(sugar(i)))
anim("na_sugar", [f"nf_sugar_{i}" for i in range(3)], 220); KINDS["sugar"] = SU_C

ME_C = "#FF7A59"


def meal(k):
    o = k * 1.2
    steam = "".join(strokep(f"M{x},{20-o:.1f} c-2,-3 2,-4 0,-7 c-2,-3 2,-4 0,-7", W, 2, ".8") for x in (19, 24, 29))
    return bg(ME_C) + steam + fillp("M10,26 H38 C38,33.5 31.7,38 24,38 C16.3,38 10,33.5 10,26 Z", W) + fillp("M8,25 h32 a1.5,1.5 0 0,1 0,3 h-32 a1.5,1.5 0 0,1 0,-3 Z", W)


for i in range(3): write(f"nf_meal_{i}", vec(meal(i)))
anim("na_meal", ["nf_meal_0", "nf_meal_1", "nf_meal_2", "nf_meal_1"], 200); KINDS["meal"] = ME_C

for k, first in [("run", "nf_run_0"), ("lift", "nf_lift_2"), ("rest", "nf_rest_0"), ("flame", "nf_flame_0"), ("drop", "nf_drop_1")]:
    src = open(os.path.join(D, first + ".xml")).read().replace('android:width="48dp" android:height="48dp"', 'android:width="24dp" android:height="24dp"')
    write("nt_" + k, src)

write("notif_progress", f'''<?xml version="1.0" encoding="utf-8"?>
<layer-list {NS}>
  <item android:id="@android:id/background"><shape><corners android:radius="6dp"/><solid android:color="#26808080"/></shape></item>
  <item android:id="@android:id/progress"><clip><shape><corners android:radius="6dp"/><solid android:color="#FFFFFFFF"/></shape></clip></item>
</layer-list>
''')
write("notif_chip_bg", f'<?xml version="1.0" encoding="utf-8"?>\n<shape {NS}><corners android:radius="999dp"/><solid android:color="#26808080"/></shape>\n')

names = list(KINDS.keys())


def bars(sz):
    return "\n".join(f'''      <ProgressBar android:id="@+id/n_anim_{k}" android:layout_width="{sz}dp" android:layout_height="{sz}dp"
          android:indeterminate="true" android:indeterminateDrawable="@drawable/na_{k}" android:indeterminateDuration="900" android:visibility="gone"/>''' for k in names)


small = f'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout {NS} android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="horizontal" android:gravity="center_vertical" android:paddingTop="2dp" android:paddingBottom="2dp">
    <FrameLayout android:layout_width="40dp" android:layout_height="40dp">
{bars(40)}
    </FrameLayout>
    <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
        android:orientation="vertical" android:layout_marginStart="12dp">
        <TextView android:id="@+id/n_title" style="@style/TextAppearance.Compat.Notification.Title" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:maxLines="1" android:ellipsize="end" android:textStyle="bold"/>
        <TextView android:id="@+id/n_text" style="@style/TextAppearance.Compat.Notification" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:maxLines="1" android:ellipsize="end"/>
        <ProgressBar android:id="@+id/n_bar_small" style="@android:style/Widget.ProgressBar.Horizontal" android:layout_width="match_parent"
            android:layout_height="4dp" android:layout_marginTop="5dp" android:max="1000" android:progressDrawable="@drawable/notif_progress" android:visibility="gone"/>
    </LinearLayout>
    <Chronometer android:id="@+id/n_chrono_small" style="@style/TextAppearance.Compat.Notification.Title" android:layout_width="wrap_content"
        android:layout_height="wrap_content" android:layout_marginStart="10dp" android:textSize="18sp" android:textStyle="bold" android:visibility="gone"/>
    <TextView android:id="@+id/n_value_small" style="@style/TextAppearance.Compat.Notification.Title" android:layout_width="wrap_content"
        android:layout_height="wrap_content" android:layout_marginStart="10dp" android:textSize="18sp" android:textStyle="bold" android:visibility="gone"/>
</LinearLayout>
'''
open(os.path.join(L, "notif_card_small.xml"), "w").write(small)


def stat(i):
    return f'''        <LinearLayout android:id="@+id/n_stat{i}" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
            android:orientation="vertical" android:visibility="gone">
            <TextView android:id="@+id/n_stat{i}_v" style="@style/TextAppearance.Compat.Notification.Title" android:layout_width="wrap_content"
                android:layout_height="wrap_content" android:textStyle="bold" android:maxLines="1"/>
            <TextView android:id="@+id/n_stat{i}_l" style="@style/TextAppearance.Compat.Notification.Info" android:layout_width="wrap_content"
                android:layout_height="wrap_content" android:maxLines="1"/>
        </LinearLayout>'''


big = f'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout {NS} android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical"
    android:paddingTop="4dp" android:paddingBottom="6dp">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical">
        <FrameLayout android:layout_width="52dp" android:layout_height="52dp">
{bars(52)}
        </FrameLayout>
        <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginStart="12dp">
            <TextView android:id="@+id/n_title" style="@style/TextAppearance.Compat.Notification.Title" android:layout_width="wrap_content"
                android:layout_height="wrap_content" android:maxLines="1" android:ellipsize="end" android:textStyle="bold" android:textSize="16sp"/>
            <TextView android:id="@+id/n_text" style="@style/TextAppearance.Compat.Notification" android:layout_width="wrap_content"
                android:layout_height="wrap_content" android:maxLines="3" android:ellipsize="end"/>
        </LinearLayout>
        <ImageView android:id="@+id/n_image" android:layout_width="64dp" android:layout_height="64dp" android:layout_marginStart="8dp" android:visibility="gone"/>
    </LinearLayout>
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal"
        android:gravity="bottom" android:layout_marginTop="10dp">
        <Chronometer android:id="@+id/n_chrono" style="@style/TextAppearance.Compat.Notification.Title" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textSize="34sp" android:textStyle="bold" android:visibility="gone"/>
        <TextView android:id="@+id/n_value" style="@style/TextAppearance.Compat.Notification.Title" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:textSize="30sp" android:textStyle="bold" android:visibility="gone"/>
        <FrameLayout android:layout_width="0dp" android:layout_height="1dp" android:layout_weight="1"/>
        <TextView android:id="@+id/n_chip" style="@style/TextAppearance.Compat.Notification.Info" android:layout_width="wrap_content"
            android:layout_height="wrap_content" android:background="@drawable/notif_chip_bg" android:paddingStart="10dp" android:paddingEnd="10dp"
            android:paddingTop="4dp" android:paddingBottom="4dp" android:layout_marginBottom="6dp" android:maxLines="1" android:visibility="gone"/>
    </LinearLayout>
    <ProgressBar android:id="@+id/n_bar" style="@android:style/Widget.ProgressBar.Horizontal" android:layout_width="match_parent"
        android:layout_height="8dp" android:layout_marginTop="8dp" android:max="1000" android:progressDrawable="@drawable/notif_progress" android:visibility="gone"/>
    <ProgressBar android:id="@+id/n_bar_ind" style="@android:style/Widget.ProgressBar.Horizontal" android:layout_width="match_parent"
        android:layout_height="8dp" android:layout_marginTop="8dp" android:indeterminate="true" android:visibility="gone"/>
    <TextView android:id="@+id/n_bar_label" style="@style/TextAppearance.Compat.Notification.Info" android:layout_width="wrap_content"
        android:layout_height="wrap_content" android:layout_marginTop="4dp" android:visibility="gone"/>
    <LinearLayout android:id="@+id/n_stats" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal"
        android:layout_marginTop="10dp" android:visibility="gone">
{stat(1)}
{stat(2)}
{stat(3)}
    </LinearLayout>
</LinearLayout>
'''
open(os.path.join(L, "notif_card_big.xml"), "w").write(big)
print("kinds:", list(KINDS))
