"""Pip's animations: each is f(u) -> pose dict, u in [0,1). All start and end on the idle pose."""
import math
TAU = 2 * math.pi
def sm(x): x = max(0.0, min(1.0, x)); return x * x * (3 - 2 * x)
def env(u, a=0.18, b=0.18):
    return sm(u / a) if u < a else (sm((1 - u) / b) if u > 1 - b else 1.0)
def lerp(a, b, t): return a + (b - a) * t

def idle(u):
    blink = max(0.0, 1 - abs(u - 0.86) / 0.035) if 0.82 < u < 0.9 else 0.0
    return dict(breath=math.sin(TAU * u), yaw=5 * math.sin(TAU * u), tilt=3 * math.sin(TAU * u + 1.0),
                armL=14 + 4 * math.sin(TAU * u), armR=14 + 4 * math.sin(TAU * u + 0.8), fwdL=10, fwdR=10,
                ant=8 * math.sin(2 * TAU * u), blink=blink, eyes='open', mouth='smile', brows='normal', blush=0.7)

NUM = ('crouch', 'spread', 'breath', 'yaw', 'tilt', 'nod', 'armL', 'armR', 'fwdL', 'fwdR', 'ant', 'antx', 'blink', 'blush', 'hop', 'lean', 'spin',
       'squash', 'x', 'eyebig', 'open', 'stepL', 'stepR')

def mix(base, tgt, w, discrete_at=0.35):
    """Blend numeric keys; switch discrete keys (eyes, mouth, props, hand targets) once w passes a threshold."""
    out = dict(base)
    defaults = dict(crouch=0.0, spread=0.0, squash=1.0, eyebig=1.0, hop=0.0, lean=0.0, spin=0.0, nod=0.0, x=0.0, open=0.0, stepL=0.0, stepR=0.0, antx=0.0)
    for k, v in tgt.items():
        if k in NUM:
            out[k] = lerp(base.get(k, defaults.get(k, 0.0)), v, w)
        elif k in ('handL', 'handR'):
            if w > 0.02:
                out[k] = v; out['_hw' + k[-1]] = w
        elif w > discrete_at:
            out[k] = v
    return out

def once(fn, u, a=0.18, b=0.18):
    return mix(idle(u), fn(u), env(u, a, b))

# ---------------------------------------------------------------- reactions
SH = {'L': (0.50, -0.06, 0.92), 'R': (-0.50, -0.06, 0.92)}

def wave(u):
    return once(lambda u: dict(armR=128 + 22 * math.sin(TAU * 3 * u), fwdR=24, yaw=12, tilt=-7, mouth='open', eyes='open', blush=0.9,
                               ant=14 * math.sin(TAU * 3 * u)), u)

def train(u):
    c = 0.5 - 0.5 * math.cos(TAU * 2 * u)        # two curls
    hand = (-0.66, -0.62, 0.62 + 0.50 * c)
    return once(lambda u: dict(handR=hand, armL=22, fwdL=15, eyes='determined', brows='determined', mouth='grin', lean=-2, yaw=-8,
                               prop='Dumbbell', prop_at='R', prop_off=(0, -0.05, 0), prop_rot=(0, 70 - 25 * c, 0), blush=0.8, nod=4), u, 0.15, 0.15)

def celebrate(u):
    j = abs(math.sin(TAU * u))                    # two jumps
    land = max(0.0, 1 - j * 6) * (0.5 - 0.5 * math.cos(TAU * 2 * u))
    return once(lambda u: dict(hop=0.24 * j, squash=1.0 - 0.08 * land + 0.04 * j, armL=135 + 12 * math.sin(TAU * 4 * u), armR=135 - 12 * math.sin(TAU * 4 * u),
                               fwdL=38, fwdR=38, eyes='happy', mouth='open', blush=1.0, stepL=0.12 * j, stepR=0.06 * j, ant=20 * math.sin(TAU * 4 * u)), u, 0.12, 0.15)

def thinking(u):
    return dict(idle(u), handR=(-0.18, -0.88, 1.30), armL=16, yaw=-10, tilt=9, nod=-6, look=(-0.5, 0.7), brows='think', mouth='small',
                ant=10 * math.sin(TAU * u) + 10, blink=idle(u)['blink'])

def love(u):
    sway = math.sin(TAU * u)
    return dict(idle(u), handL=(0.30, -0.74, 0.78), handR=(-0.30, -0.74, 0.78), prop='Heart', prop_at='C', prop_off=(0, -0.08, 0.04),
                prop_rot=(0, 0, 0), prop_scale=1.0 + 0.04 * math.sin(TAU * 2 * u), eyes='happy', mouth='smile', blush=1.0, tilt=6 * sway, yaw=4 * sway, blink=0.0)

def hydrate(u):
    sip = sm((u - 0.38) / 0.14) * (1 - sm((u - 0.74) / 0.12))
    hand = (-0.62 + 0.22 * sip, -0.50 - 0.16 * sip, 0.80 + 0.30 * sip)
    t = dict(handR=hand, prop='Bottle', prop_aim=('mouth' if sip > 0.02 else None), prop_mix=sip, prop_scale=1.15, prop_at='R',
             prop_off=(0, -0.04, -0.10), prop_rot=(-8, 0, -12),
             eyes='happy' if sip > 0.5 else 'wink', mouth='o' if sip > 0.5 else 'tongue', nod=14 * sip, lean=-3 * sip, blush=0.9, armL=18)
    return once(lambda u: t, u, 0.14, 0.14)

def fuel(u):
    b = abs(math.sin(TAU * 2 * u))
    return once(lambda u: dict(handL=(0.36, -0.66, 0.72), handR=(-0.36, -0.66, 0.72), prop='Bowl', prop_at='C', prop_off=(0, -0.10, 0.06),
                               eyes='happy', mouth='open', blush=1.0, hop=0.05 * b, tilt=5 * math.sin(TAU * 2 * u)), u, 0.15, 0.15)

def sleepy(u):
    br = math.sin(TAU * u)
    return dict(idle(u), handL=(0.28, -0.70, 0.86), handR=(-0.28, -0.70, 0.86), prop='Pillow', prop_at='C', prop_off=(-0.05, -0.08, 0.12),
                prop_rot=(0, 0, 12), eyes='closed', mouth='small', tilt=14 + 2 * br, nod=8 + 3 * br, breath=1.6 * br, yaw=-6, blush=0.8, ant=-22, antx=10, blink=0.0)

def letsgo(u):
    return once(lambda u: dict(handR=(-0.78, -0.62, 1.0), prop='Thumb', prop_scale=1.4, prop_at='R', prop_off=(0, -0.08, 0.10), prop_rot=(0, -15, 0),
                               eyes='wink', mouth='tongue', blush=1.0, tilt=-8, yaw=8, hop=0.06 * abs(math.sin(TAU * u)), armL=20), u, 0.14, 0.16)

def laugh(u):
    sh = math.sin(TAU * 7 * u)
    return once(lambda u: dict(handL=(0.36, -0.60, 0.62), handR=(-0.36, -0.60, 0.62), eyes='happy', mouth='open', open=0.6 + 0.4 * abs(sh), blush=1.0,
                               nod=-10, x=0.025 * sh, squash=1.0 + 0.025 * sh, tilt=4 * sh, ant=18 * sh), u, 0.12, 0.15)

def surprised(u):
    j = math.sin(math.pi * min(1.0, u / 0.35))
    return once(lambda u: dict(hop=0.16 * j, armL=78, armR=78, fwdL=30, fwdR=30, eyes='big', eyebig=1.22, brows='raised', mouth='o', open=0.5,
                               blush=0.6, nod=-6, ant=-12), u, 0.08, 0.2)

def dance(u):
    s = math.sin(TAU * 2 * u)
    return dict(idle(u), lean=10 * s, yaw=14 * s, stepL=max(0.0, s) * 0.14, stepR=max(0.0, -s) * 0.14, armL=90 + 55 * s, armR=90 - 55 * s, fwdL=20, fwdR=20,
                eyes='happy', mouth='open', blush=1.0, hop=0.05 * abs(s), ant=20 * s, blink=0.0)

def spin(u):
    return once(lambda u: dict(spin=360 * sm(u / 0.85) if u < 0.85 else 360.0, armL=82, armR=82, fwdL=0, fwdR=0, eyes='happy', mouth='open',
                               hop=0.12 * math.sin(math.pi * min(1.0, u / 0.85))), u, 0.08, 0.12)

def flex(u):
    pump = 0.5 + 0.5 * math.sin(TAU * 2 * u)
    return once(lambda u: dict(handL=(0.84, -0.30, 1.12 + 0.06 * pump), handR=(-0.84, -0.30, 1.12 + 0.06 * pump), eyes='determined', brows='determined',
                               mouth='grin', blush=0.9, squash=1.0 + 0.02 * pump, nod=-4), u, 0.15, 0.15)

def concerned(u):
    return dict(idle(u), handL=(0.13, -0.66, 0.74), handR=(-0.13, -0.66, 0.74), brows='worried', mouth='worry', tilt=7, nod=5, look=(0.0, -0.3),
                yaw=3 * math.sin(TAU * u))

def curious(u):
    return once(lambda u: dict(tilt=15, yaw=16, nod=-5, eyebig=1.1, brows='raised', mouth='o', open=0.0, armR=32, armL=14, ant=16), u, 0.2, 0.2)

def wink(u):
    return once(lambda u: dict(eyes='wink', mouth='tongue', tilt=9, yaw=6, armR=48, fwdR=30, blush=1.0, ant=12), u, 0.15, 0.2)

# ---------------------------------------------------------------- v2 clips
def jumpingjacks(u):
    j = 0.5 - 0.5 * math.cos(TAU * 3 * u)
    return once(lambda u: dict(armL=20 + 125 * j, armR=20 + 125 * j, fwdL=12 + 18 * j, fwdR=12 + 18 * j, spread=0.14 * j, hop=0.10 * math.sin(math.pi * ((3 * u) % 1.0)),
                               eyes='happy', mouth='open', blush=0.9, ant=16 * math.sin(TAU * 3 * u)), u, 0.1, 0.12)

def jog(u):
    s = math.sin(TAU * 3 * u)
    return once(lambda u: dict(fwdL=10 + 45 * s, fwdR=10 - 45 * s, armL=22, armR=22, stepL=max(0.0, s) * 0.16, stepR=max(0.0, -s) * 0.16,
                               hop=0.04 * abs(s), lean=-3, nod=4, eyes='determined', brows='determined', mouth='grin', blush=0.8, ant=-14 + 10 * s), u, 0.1, 0.12)

def squat(u):
    c = 0.5 - 0.5 * math.cos(TAU * 2 * u)
    return once(lambda u: dict(crouch=0.20 * c, squash=1.0 - 0.05 * c, armL=20 + 70 * c, armR=20 + 70 * c, fwdL=10 + 70 * c, fwdR=10 + 70 * c,
                               eyes='determined', brows='determined', mouth='grin', nod=4 * c, blush=0.8), u, 0.1, 0.12)

def stretch(u):
    s = math.sin(TAU * u)
    return once(lambda u: dict(armL=148, armR=148, fwdL=32, fwdR=32, lean=12 * s, tilt=8 * s, squash=1.04, eyes='closed', mouth='o', open=0.3, blush=0.7, ant=10 * s), u, 0.2, 0.2)

def clap(u):
    c = 0.5 + 0.5 * math.cos(TAU * 4 * u)
    return once(lambda u: dict(handL=(0.06 + 0.30 * c, -0.74, 0.98), handR=(-0.06 - 0.30 * c, -0.74, 0.98), eyes='happy', mouth='open', blush=1.0,
                               hop=0.03 * (1 - c), ant=12 * math.sin(TAU * 4 * u)), u, 0.12, 0.15)

def shrug(u):
    return once(lambda u: dict(armL=58, armR=58, fwdL=30, fwdR=30, tilt=10, brows='raised', mouth='flat', squash=1.03, eyes='open', look=(0.3, 0.2), ant=12), u, 0.2, 0.2)

def yes(u):
    return once(lambda u: dict(nod=10 * math.sin(TAU * 3 * u) + 4, eyes='happy', mouth='smile', blush=0.9, ant=10 * math.sin(TAU * 3 * u)), u, 0.12, 0.15)

def no(u):
    return once(lambda u: dict(yaw=22 * math.sin(TAU * 3 * u), mouth='flat', brows='normal', eyes='open', ant=-14 * math.sin(TAU * 3 * u)), u, 0.12, 0.15)

def shy(u):
    s = math.sin(TAU * u)
    return once(lambda u: dict(handL=(0.10, -0.64, 0.66), handR=(-0.10, -0.64, 0.66), nod=12, tilt=10 + 5 * s, yaw=-8, eyes='happy', mouth='small', blush=1.0, ant=-10), u, 0.2, 0.2)

def sad(u):
    s = math.sin(TAU * u)
    return dict(idle(u), armL=4, armR=4, fwdL=4, fwdR=4, brows='worried', mouth='frown', eyes='open', look=(0.0, -0.6), nod=12, tilt=5 * s, ant=-26, antx=12, blush=0.4, blink=0.0)

def pout(u):
    st = abs(math.sin(TAU * 2 * u))
    return once(lambda u: dict(handL=(-0.12, -0.66, 0.80), handR=(0.12, -0.68, 0.84), brows='determined', mouth='frown', eyes='open', yaw=-14, tilt=-6,
                               hop=0.03 * st, blush=1.0, ant=-8), u, 0.15, 0.15)

def yawn(u):
    y = sm((u - 0.15) / 0.25) * (1 - sm((u - 0.7) / 0.2))
    return once(lambda u: dict(armL=20 + 120 * y, armR=20 + 120 * y, fwdL=10 + 20 * y, fwdR=10 + 20 * y, eyes='closed', mouth='o', open=1.0 * y, eyebig=1.0, nod=-12 * y, squash=1.0 + 0.04 * y, ant=-16 * y), u, 0.08, 0.12)

def blowkiss(u):
    k = sm((u - 0.2) / 0.2); out = sm((u - 0.5) / 0.15)
    hand = (-0.30 - 0.25 * out, -0.70 - 0.25 * out, 1.08 - 0.05 * out)
    return once(lambda u: dict(handR=hand, eyes='wink', mouth='o' if out < 0.5 else 'smile', blush=1.0, tilt=-6, ant=10), u, 0.12, 0.15)

def point(u):
    a = sm((u - 0.15) / 0.15)
    return once(lambda u: dict(armR=80 + 5 * a, fwdR=38, eyes='big', eyebig=1.08, brows='raised', mouth='open', yaw=-8, look=(-0.4, 0.0), blush=0.8, ant=14), u, 0.15, 0.18)


# ---------------------------------------------------------------- round-4 emotes
def thumbsup(u):
    a = sm((u - 0.12) / 0.18)
    return once(lambda u: dict(handR=(-0.42, -0.78, 1.02 + 0.04 * math.sin(TAU * 2 * u)), prop='Thumb', prop_at='R', prop_off=(0, -0.02, 0.06), prop_rot=(0, 0, 0),
                               eyes='wink', mouth='grin', blush=0.9, tilt=-6, yaw=-6, ant=12 * a), u, 0.15, 0.18)

def salute(u):
    a = sm((u - 0.1) / 0.2)
    return once(lambda u: dict(handR=(-0.40, -0.70, 1.62), eyes='determined', brows='determined', mouth='small', nod=-4, lean=-2, ant=8, blush=0.6), u, 0.18, 0.2)

def facepalm(u):
    return once(lambda u: dict(handR=(-0.10, -0.86, 1.52), eyes='closed', mouth='flat', brows='worried', nod=14, tilt=6, ant=-18, antx=10, blush=0.3), u, 0.15, 0.2)

def cheer(u):
    sh = math.sin(TAU * 4 * u); j = abs(math.sin(TAU * 2 * u))
    return once(lambda u: dict(armL=150 + 15 * sh, armR=150 - 15 * sh, fwdL=20, fwdR=20, hop=0.10 * j, eyes='happy', mouth='open', blush=1.0,
                               ant=22 * sh, squash=1.0 + 0.03 * j), u, 0.1, 0.15)

def grumpy(u):
    s = math.sin(TAU * u)
    return dict(idle(u), handL=(-0.18, -0.66, 0.86), handR=(0.18, -0.66, 0.86), brows='determined', mouth='frown', eyes='open', look=(0.5, 0.0),
                yaw=14 + 3 * s, tilt=-6, ant=-12, blush=0.3, blink=0.0)

def peekaboo(u):
    cover = 1.0 - sm((u - 0.45) / 0.12)
    if cover > 0.5:
        return once(lambda u: dict(handL=(0.26, -0.84, 1.36), handR=(-0.26, -0.84, 1.36), eyes='closed', mouth='smile', nod=6, blush=0.9, ant=6), u, 0.12, 0.05)
    return once(lambda u: dict(armL=110, armR=110, fwdL=30, fwdR=30, eyes='big', eyebig=1.12, mouth='open', brows='raised', hop=0.05, blush=1.0, ant=18), u, 0.05, 0.15)

def highfive(u):
    hit = sm((u - 0.35) / 0.1) * (1 - sm((u - 0.55) / 0.12))
    return once(lambda u: dict(armR=135, fwdR=45, eyes='happy' if hit > 0.3 else 'open', mouth='grin', hop=0.06 * hit, yaw=-10, lean=-3 * hit, blush=0.9, ant=16 * hit), u, 0.15, 0.18)

def bow(u):
    b = sm((u - 0.15) / 0.25) * (1 - sm((u - 0.65) / 0.2))
    return once(lambda u: dict(nod=26 * b, lean=0, crouch=0.04 * b, armL=8, armR=8, fwdL=20 * b, fwdR=20 * b, eyes='closed', mouth='smile', blush=0.8, ant=-10 * b), u, 0.05, 0.1)

def meditate(u):
    s = math.sin(TAU * u)
    return dict(idle(u), crouch=0.10, spread=0.06, armL=38, armR=38, fwdL=24, fwdR=24, eyes='closed', mouth='small', brows='normal', breath=s * 2.0,
                hop=0.01 * s, ant=4 * s, blush=0.6, blink=0.0, yaw=0, tilt=0)

def dizzy(u):
    w = math.sin(TAU * 2 * u)
    return once(lambda u: dict(yaw=18 * w, tilt=14 * math.cos(TAU * 2 * u), eyes='closed', mouth='worry', brows='worried', lean=4 * w, ant=26 * w, antx=14 * math.cos(TAU * 2 * u), blush=0.4), u, 0.12, 0.15)

def sneeze(u):
    build = sm(u / 0.45); burst = sm((u - 0.45) / 0.06) * (1 - sm((u - 0.62) / 0.2))
    return once(lambda u: dict(nod=-12 * build * (1 - burst) + 18 * burst, eyes='closed', mouth='o' if burst < 0.3 else 'open', open=0.4 + 0.6 * build,
                               squash=1.0 + 0.04 * build - 0.07 * burst, handR=(-0.08, -0.84, 1.42) if burst > 0.3 else None, ant=-20 * burst + 10 * build, blush=0.7), u, 0.05, 0.12)

def hearteyes(u):
    s = math.sin(TAU * 2 * u)
    return once(lambda u: dict(handL=(0.30, -0.74, 0.78), handR=(-0.30, -0.74, 0.78), prop='Heart', prop_at='C', prop_off=(0, -0.08, 0.06 + 0.04 * s),
                               eyes='happy', mouth='open', blush=1.0, tilt=8 * s, hop=0.03 * abs(s), ant=14 * s), u, 0.12, 0.15)

ANIMS = {
    # name: (fn, frames)
    'idle': (idle, 48), 'wave': (wave, 40), 'train': (train, 48), 'celebrate': (celebrate, 36), 'thinking': (thinking, 48),
    'love': (love, 48), 'hydrate': (hydrate, 48), 'fuel': (fuel, 40), 'sleepy': (sleepy, 60), 'letsgo': (letsgo, 36),
    'laugh': (laugh, 36), 'surprised': (surprised, 28), 'dance': (dance, 48), 'spin': (spin, 28), 'flex': (flex, 36),
    'concerned': (concerned, 48), 'curious': (curious, 40), 'wink': (wink, 28),
    'jumpingjacks': (jumpingjacks, 44), 'jog': (jog, 40), 'squat': (squat, 44), 'stretch': (stretch, 44), 'clap': (clap, 36),
    'shrug': (shrug, 32), 'yes': (yes, 32), 'no': (no, 32), 'shy': (shy, 40), 'sad': (sad, 48), 'pout': (pout, 36),
    'yawn': (yawn, 44), 'blowkiss': (blowkiss, 36), 'point': (point, 32),
    'thumbsup': (thumbsup, 36), 'salute': (salute, 36), 'facepalm': (facepalm, 36), 'cheer': (cheer, 40), 'grumpy': (grumpy, 48),
    'peekaboo': (peekaboo, 40), 'highfive': (highfive, 36), 'bow': (bow, 40), 'meditate': (meditate, 60), 'dizzy': (dizzy, 40),
    'sneeze': (sneeze, 36), 'hearteyes': (hearteyes, 40),
}
# look-at stills (row 0 = finger above Pip, col 0 = finger to the left)
LOOKS = {}
N = 13  # 13×13 look grid: wider range and 4× the poses, so neighbouring frames are close and cross-fades are invisible
for r in range(N):
    nod = -14 + 26 * r / (N - 1)
    for c in range(N):
        yaw = -32 + 64 * c / (N - 1)
        LOOKS['look_%02d_%02d' % (r, c)] = dict(yaw=yaw, nod=nod, tilt=-yaw * 0.12, look=(yaw / 32 * 0.7, -nod / 14 * 0.55), eyes='open', mouth='smile', brows='normal', blush=0.8,
                                         armL=14, armR=14, ant=-yaw * 0.4)
