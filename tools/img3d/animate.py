"""Pose + render a rigged image-to-3D character (from autorig.py) as Pip-style clips.
usage: python animate.py rig.blend out_dir [stills|clips] [shard shards]"""
import sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy, toon
from mathutils import Vector, Euler

blend, OUT = sys.argv[1], sys.argv[2]
MODE = sys.argv[3] if len(sys.argv) > 3 else "stills"
SH, NSH = (int(sys.argv[4]), int(sys.argv[5])) if len(sys.argv) > 5 else (0, 1)
os.makedirs(OUT, exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=blend)
arm = bpy.data.objects["rig"]; ob = next(o for o in bpy.data.objects if o.type == "MESH")
toon.toonify(ob)
pb = arm.pose.bones
REST = {b.name: (b.tail_local - b.head_local).normalized() for b in arm.data.bones}

def reset():
    for b in pb: b.rotation_mode = "QUATERNION"; b.rotation_quaternion = (1, 0, 0, 0)
    bpy.context.view_layer.update()

def aim(n, d):
    p = pb[n]; bpy.context.view_layer.update()
    cur = p.matrix.copy(); y = cur.to_3x3().col[1].normalized()
    q = y.rotation_difference(Vector(d).normalized())
    m = (q.to_matrix() @ cur.to_3x3()).to_4x4(); m.translation = cur.translation; p.matrix = m
    bpy.context.view_layer.update()

def rest_dir(n): return REST[n]
def mir(d): return (-d[0], d[1], d[2])
def lerp(a, b, t): return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
POSE = {   # left side (+X); right side mirrored
    "idle":    {"up": (0.22, 0.03, -1), "lo": (0.10, -0.25, -1)},
    "flex":    {"up": (1, 0.1, -0.3), "lo": (0.05, 0.1, 1)},
    "victory": {"up": (0.55, 0.05, 1), "lo": (0.3, 0, 1)},
}
def apply(sp, breath=0.0, sway=0.0):
    reset()
    for n, k in (("spine", 1.5), ("hips", 0.6)):
        pb[n].rotation_quaternion = Euler((math.radians(-k * breath), 0, 0)).to_quaternion()
    aim("upper_arm.L", sp["up"]); aim("forearm.L", sp["lo"])
    aim("upper_arm.R", mir(sp["up"])); aim("forearm.R", mir(sp["lo"]))
    if sway: pb["head"].rotation_quaternion = Euler((0, 0, math.radians(2.5 * sway))).to_quaternion()
    bpy.context.view_layer.update()
def blend_(a, b, t): return {k: lerp(POSE[a][k], POSE[b][k], t) for k in POSE[a]}
def ease(t): return t * t * (3 - 2 * t)
def seg(t, a, b): return ease(max(0.0, min(1.0, (t - a) / (b - a))))
def clip(c, t):
    br = math.sin(t * 2 * math.pi)
    if c == "idle": return blend_("idle", "idle", 0), br, math.sin(t * 2 * math.pi + 1) * 0.5
    if c == "flex":
        k = seg(t, 0, 0.32) * (1 - seg(t, 0.78, 1)); sp = blend_("idle", "flex", k)
        sq = (math.sin((t - 0.32) / 0.46 * 4 * math.pi) * 0.5 + 0.5) if 0.32 < t < 0.78 else 0
        sp["lo"] = lerp(sp["lo"], (0.25, 0.2, 0.95), 0.35 * sq * k); return sp, br * 0.4, 0
    if c == "victory":
        k = seg(t, 0, 0.25) * (1 - seg(t, 0.8, 1)); sp = blend_("idle", "victory", k)
        pump = abs(math.sin((t - 0.25) / 0.55 * 2 * math.pi)) if 0.25 < t < 0.8 else 0
        sp["up"] = lerp(sp["up"], (0.4, 0.02, 1), 0.5 * pump * k); sp["lo"] = lerp(sp["lo"], (0.05, -0.1, 1), 0.6 * pump * k)
        return sp, 0, 0

if MODE == "stills":
    cam = toon.scene((448, 560), 16)
    for n in ("rest", "idle", "flex", "victory"):
        if n == "rest": reset()
        else: apply(POSE[n])
        toon.shoot(cam, os.path.join(OUT, n + ".png"), 15, 3.6, 1.0)
else:
    cam = toon.scene((560, 560), int(os.environ.get("SAMPLES", "16")), transparent=True)
    bpy.data.cameras["c"].lens = 45
    CL = {"idle": 30, "flex": 28, "victory": 28}
    jobs = [("portrait", 0, 1)] + [(c, i, n) for c, n in CL.items() for i in range(n)]
    for j, (c, i, n) in enumerate(jobs):
        if j % NSH != SH: continue
        if c == "portrait": apply(POSE["idle"])
        else: sp, br, sw = clip(c, i / n); apply(sp, br, sw)
        os.makedirs(os.path.join(OUT, c), exist_ok=True)
        toon.shoot(cam, os.path.join(OUT, c, "%03d.png" % i), 18 if c == "portrait" else 12, 3.7, 1.0)
        print("FRAME", c, i)
print("ANIM_DONE")
