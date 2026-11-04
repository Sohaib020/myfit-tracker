"""Side-view pose figure model shared (by spec) with the app's CaliFigure.kt.

Angles are degrees in screen space: 0 = pointing down, 90 = forward (right), 180 = up, 270 = back (left).
A pose gives the world angle of each segment; the hip is the root. Missing far-side limbs copy the near side;
a missing foot is perpendicular to its shin (flat foot), 'hd' (head) defaults to the torso angle.
"""
import math
L = dict(t=0.50, neck=0.05, head=0.105, a=0.29, f=0.27, l=0.44, s=0.42, ft=0.13)

def d(a):
    r = math.radians(a); return (math.sin(r), math.cos(r))

def full(p):
    p = dict(p)
    for k in ("a", "f", "l", "s", "ft"):
        if k + "1" not in p and k in p: p[k + "1"] = p[k]
    for k in ("a", "f", "l", "s"):
        p.setdefault(k + "2", p[k + "1"])
    p.setdefault("ft1", p["s1"] + 90); p.setdefault("ft2", p["s2"] + 90 if "ft" not in p else p["ft1"])
    p.setdefault("hd", p["t"])
    return p

def joints(p):
    p = full(p)
    J = {"hip": (0.0, 0.0)}
    def go(frm, ang, ln): x, y = J[frm]; dx, dy = d(ang); return (x + dx * ln, y + dy * ln)
    J["sh"] = go("hip", p["t"], L["t"])
    hx, hy = d(p["hd"]); J["head"] = (J["sh"][0] + hx * (L["neck"] + L["head"]), J["sh"][1] + hy * (L["neck"] + L["head"]))
    for i in "12":
        J["el" + i] = go("sh", p["a" + i], L["a"]); J["wr" + i] = go("el" + i, p["f" + i], L["f"])
        J["kn" + i] = go("hip", p["l" + i], L["l"]); J["an" + i] = go("kn" + i, p["s" + i], L["s"]); J["to" + i] = go("an" + i, p["ft" + i], L["ft"])
    return J
