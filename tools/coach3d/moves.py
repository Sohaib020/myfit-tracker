"""
Hand-keyed coach moves (R21). Conventions: see anim.py.  Left-side helpers (mirrored for .R):
  fwd(UpperArm/UpLeg) x<0 raises forward · abd(UpperArm) y<0 lifts sideways · elbow flex ForeArm x<0 · knee Leg x>0
  spine/head forward x>0 · turn to character's left z>0 · finger curl y>0 (L)
Every clip starts and ends in STANCE so loops are seamless; timing uses anticipation → action → overshoot → settle,
with follow-through lag on hands, head and fingers.
"""
import math
from anim import Clip, both, side, merge, mirror

S = ('L', 'R')


def relaxed_hands(curl=1.0):
    return merge(both('Fingers1', y=14 * curl), both('Fingers2', y=18 * curl), both('Fingers3', y=12 * curl),
                 both('Thumb1', x=-6 * curl), both('Thumb2', y=8 * curl))


def fist(s, k=1.0, thumb_out=False):
    p = merge(side('Fingers1', s, y=85 * k), side('Fingers2', s, y=95 * k), side('Fingers3', s, y=75 * k))
    if not thumb_out: p = merge(p, side('Thumb1', s, y=25 * k), side('Thumb2', s, y=40 * k), side('Thumb3', s, y=30 * k))
    else: p = merge(p, side('Thumb1', s, x=-10 * k, y=-15 * k))
    return p


def open_hand(s, spread=1.0):
    return merge(side('Fingers1', s, y=-5 * spread), side('Fingers2', s, y=0), side('Fingers3', s, y=0), side('Thumb1', s, y=-18 * spread))


# natural standing: arms in from the A-pose, soft elbows, weight a touch on one leg
STANCE = merge(both('UpperArm', x=-4, y=11), both('ForeArm', x=-14), both('Hand', x=-4), relaxed_hands(),
               {'Spine': (2, 0, 0), 'Chest': (-2, 0, 0), 'Neck': (2, 0, 0), 'Head': (-2, 0, 0)},
               side('UpLeg', 'L', x=-2, y=1), side('Leg', 'L', x=4), side('UpLeg', 'R', y=-1))


def P(*ps):
    """Stance + additions."""
    return merge(STANCE, *ps)


# ---------------------------------------------------------------- core

def wave():
    """Friendly wave: arm swings up (external rotation so the forearm stands up), forearm sways from the elbow,
    hand trails a couple of frames behind, body leans into it."""
    c = Clip('wave', 72); c.lag = {'Hand.R': 2, 'Fingers1.R': 3, 'Fingers2.R': 4, 'Head': 3, 'ForeArm.R': 1}
    def up(sway):
        return P(side('UpperArm', 'R', x=-14, y=-84, tw=-80 + sway), side('ForeArm', 'R', x=-98), side('Hand', 'R', x=-sway * 0.4), open_hand('R'),
                 {'Spine': (0, 2, -5), 'Chest': (0, 3, -4), 'Head': (-3, -4, -8)})
    c.key(0, P())
    c.key(8, P(side('UpperArm', 'R', x=-8, y=-24, tw=-20), side('ForeArm', 'R', x=-40), {'Spine': (1, 0, -2)}), hips=(0.01, 0, -0.005), ease='in')
    c.key(16, up(16), hips=(0.015, 0, 0), ease='out')
    c.key(24, up(-16), hips=(0.015, 0, 0))
    c.key(32, up(16), hips=(0.015, 0, 0))
    c.key(40, up(-16), hips=(0.015, 0, 0))
    c.key(48, up(8), hips=(0.015, 0, 0))
    c.key(60, P(side('UpperArm', 'R', x=-6, y=-12), side('ForeArm', 'R', x=-24)), hips=(0.005, 0, 0))
    return c


def thumbs_up():
    c = Clip('thumbsup', 64); c.lag = {'Hand.R': 2, 'Head': 3, 'Thumb1.R': 2}
    th = P(side('UpperArm', 'R', x=-12, y=-14), side('ForeArm', 'R', x=-78), side('Hand', 'R', x=-6), fist('R', thumb_out=True),
           {'Spine': (0, 0, -4), 'Chest': (0, 1, -3), 'Head': (4, 6, -6)})
    c.key(0, P())
    c.key(10, P(side('UpperArm', 'R', x=-12, y=-4), side('ForeArm', 'R', x=-55), fist('R', 0.6)), ease='in')
    c.key(18, merge(th, side('ForeArm', 'R', x=-10)), hips=(0, -0.008, 0), ease='snap')        # pop forward (overshoot)
    c.key(24, th, hips=(0, -0.004, 0))
    c.key(30, merge(th, {'Head': (6, 2, 0)}))                                                    # little nod
    c.key(44, th)
    c.key(56, P(side('ForeArm', 'R', x=-20)))
    return c


def cheer():
    c = Clip('cheer', 56); c.lag = {'Hand.L': 2, 'Hand.R': 2, 'Head': 2, 'ForeArm.L': 1, 'ForeArm.R': 1}
    upA = P(both('UpperArm', x=-18, y=-150), both('ForeArm', x=-12), fist('L'), fist('R'),
            {'Spine': (-4, 0, 0), 'Chest': (-6, 0, 0), 'Head': (-12, 0, 0)}, both('UpLeg', x=-6), both('Leg', x=10))
    c.key(0, P())
    c.key(8, P(both('UpperArm', x=-10, y=-10), both('ForeArm', x=-60), fist('L', .7), fist('R', .7), {'Spine': (8, 0, 0), 'Head': (6, 0, 0)},
               both('UpLeg', x=-22), both('Leg', x=40), both('Foot', x=-16)), hips=(0, 0.02, -0.07), ease='in')        # crouch
    c.key(15, upA, hips=(0, 0, 0.03), ease='snap')                                                 # explode up
    c.key(22, merge(upA, both('UpperArm', y=8)), hips=(0, 0, 0.0))
    c.key(28, merge(upA, both('ForeArm', x=-40), both('UpperArm', y=12)), hips=(0, 0, 0.02))      # pump
    c.key(34, upA, hips=(0, 0, 0))
    c.key(46, P(both('ForeArm', x=-30)), hips=(0, 0, -0.01))
    return c


def fist_pump():
    c = Clip('fistpump', 56); c.lag = {'Hand.R': 1, 'Head': 2}
    pull = P(side('UpperArm', 'R', x=-8, y=-28), side('ForeArm', 'R', x=-120), fist('R'),
             {'Spine': (10, -3, -10), 'Chest': (6, 0, -6), 'Head': (8, 0, -6)}, both('UpLeg', x=-12), both('Leg', x=22), both('Foot', x=-10))
    c.key(0, P())
    c.key(8, P(side('UpperArm', 'R', x=-40, y=-55), side('ForeArm', 'R', x=-70), fist('R'), {'Spine': (-3, 0, 4)}), ease='in')
    c.key(15, pull, hips=(0, 0.01, -0.05), ease='snap')           # "yes!" pull down
    c.key(22, merge(pull, side('ForeArm', 'R', x=10)), hips=(0, 0.01, -0.04))
    c.key(28, pull, hips=(0, 0.01, -0.05), ease='snap')           # second smaller pump
    c.key(44, P(side('ForeArm', 'R', x=-30)), hips=(0, 0, 0))
    return c


# ---------------------------------------------------------------- trainer demos (contacts in clips.py)

def squat():
    """Bodyweight squat: hips back and down, chest up, arms forward for balance. Feet are IK-planted."""
    c = Clip('squat', 72); c.lag = {'Head': 2, 'Hand.L': 2, 'Hand.R': 2}
    down = P(both('UpperArm', x=-85, y=8), both('ForeArm', x=-10), both('Hand', x=0), {'Spine': (30, 0, 0), 'Chest': (6, 0, 0), 'Neck': (-10, 0, 0), 'Head': (-16, 0, 0)},
             both('UpLeg', x=-88, y=-6), both('Leg', x=95), both('Foot', x=-18))
    c.key(0, P())
    c.key(6, P(both('UpperArm', x=-25)), hips=(0, 0.0, -0.01), ease='in')
    c.key(26, down, hips=(0, 0.17, -0.40), ease='smooth')
    c.key(32, merge(down, {'Spine': (28, 0, 0)}), hips=(0, 0.17, -0.41))          # brief pause at depth
    c.key(52, P(both('UpperArm', x=-30)), hips=(0, 0.0, 0.0), ease='out')       # drive up
    c.key(60, P(), hips=(0, 0, 0.005))
    c.legs_planted = True
    return c


def lunge():
    """Forward lunge, right leg steps out and back. Planted foot = left; right foot target animated in clips.py."""
    c = Clip('lunge', 80); c.lag = {'Head': 2}
    down = P(both('UpperArm', x=-6, y=6), both('ForeArm', x=-80), fist('L', .5), fist('R', .5), {'Spine': (-2, 0, 0), 'Head': (-4, 0, 0)},
             side('UpLeg', 'R', x=-80), side('Leg', 'R', x=82), side('Foot', 'R', x=-4),
             side('UpLeg', 'L', x=12), side('Leg', 'L', x=88), side('Foot', 'L', x=40))
    c.key(0, P())
    c.key(10, P(both('ForeArm', x=-60), side('UpLeg', 'R', x=-35), side('Leg', 'R', x=40)), hips=(0, -0.10, 0.0), ease='in')
    c.key(30, down, hips=(0, -0.30, -0.38), ease='smooth')
    c.key(36, down, hips=(0, -0.30, -0.39))
    c.key(56, P(both('ForeArm', x=-50), side('UpLeg', 'R', x=-30), side('Leg', 'R', x=30)), hips=(0, -0.08, 0.0), ease='out')
    c.key(68, P(), hips=(0, 0, 0))
    c.legs_planted = True
    return c


def shoulder_press():
    """Standing dumbbell press: rack at shoulders → press overhead (slight lean, exhale) → controlled lower."""
    c = Clip('press', 72); c.lag = {'Head': 2}
    rack = P(both('UpperArm', x=-14, y=-78, tw=-82), both('ForeArm', x=-112), fist('L'), fist('R'), {'Chest': (-3, 0, 0)})
    top = P(both('UpperArm', x=-10, y=-162, tw=-82), both('ForeArm', x=-14), fist('L'), fist('R'), {'Chest': (-6, 0, 0), 'Head': (-4, 0, 0)})
    c.key(0, rack); c.key(6, merge(rack, {'Spine': (2, 0, 0)}), hips=(0, 0, -0.01), ease='in')
    c.key(22, top, hips=(0, 0, 0.005), ease='out')
    c.key(28, top)
    c.key(52, rack, ease='smooth')
    c.key(64, rack)
    c.props = [('dumbbell', 'Hand.L'), ('dumbbell', 'Hand.R')]
    c.legs_planted = True
    return c


def stretch():
    """Overhead side stretch, both sides, then a calm breath."""
    c = Clip('stretch', 120); c.lag = {'Head': 3, 'Hand.L': 3, 'Hand.R': 3}
    reachL = P(side('UpperArm', 'R', x=-15, y=-165), side('ForeArm', 'R', x=-15), side('UpperArm', 'L', y=4), side('ForeArm', 'L', x=-20),
               {'Spine': (0, 14, 0), 'Chest': (0, 12, 0), 'Neck': (0, 6, 0), 'Head': (0, 4, 0)}, open_hand('R'))
    c.key(0, P())
    c.key(18, P(both('UpperArm', x=-20, y=-150), both('ForeArm', x=-15), {'Chest': (-6, 0, 0), 'Head': (-8, 0, 0)}), hips=(0, 0, 0.005))
    c.key(38, reachL, hips=(-0.03, 0, 0))
    c.key(50, reachL, hips=(-0.03, 0, 0))
    c.key(64, P(both('UpperArm', x=-20, y=-150), both('ForeArm', x=-15)), hips=(0, 0, 0.005))
    c.key(84, mirror(reachL), hips=(0.03, 0, 0))
    c.key(96, mirror(reachL), hips=(0.03, 0, 0))
    c.key(110, P(), hips=(0, 0, 0))
    c.legs_planted = True
    return c


def pushup():
    """Push-up from a high plank; body pivots on the toes, hands planted (IK). Geometry is filled in by clips.py."""
    c = Clip('pushup', 64); c.breath = 0.4; c.noise = 0.3
    c.floor = 'pushup'
    return c


def plank():
    c = Clip('plank', 96); c.breath = 0.6; c.noise = 0.4
    c.floor = 'plank'
    return c


# ---------------------------------------------------------------- nutritionist props

def hold_plate():
    c = Clip('plate', 96); c.lag = {'Head': 3}
    hold = P(side('UpperArm', 'L', x=-28, y=6), side('ForeArm', 'L', x=-88, tw=-35), side('Hand', 'L', y=10), open_hand('L', .4),
             side('UpperArm', 'R', x=-20, y=-10), side('ForeArm', 'R', x=-70), side('Hand', 'R', x=-10, z=-20), open_hand('R'),
             {'Spine': (0, 0, 4), 'Head': (6, 0, 6)})
    present = merge(hold, side('UpperArm', 'R', x=-38, y=-30), side('ForeArm', 'R', x=-40), {'Head': (-2, 0, -4)})
    c.key(0, hold); c.key(30, merge(hold, {'Head': (10, 2, 10)})); c.key(48, present, ease='out'); c.key(70, present); c.key(88, hold)
    c.props = [('plate', 'Hand.L')]
    return c


def hold_bottle():
    c = Clip('bottle', 96); c.lag = {'Head': 3}
    hold = P(side('UpperArm', 'R', x=-25, y=-6), side('ForeArm', 'R', x=-95), fist('R', .75), {'Head': (2, 0, -4)})
    raise_ = merge(hold, side('UpperArm', 'R', x=-45, y=-25), side('ForeArm', 'R', x=-80), {'Head': (-6, 0, -6), 'Chest': (-3, 0, 0)})
    c.key(0, hold); c.key(26, raise_, ease='out'); c.key(36, merge(raise_, side('Hand', 'R', x=12))); c.key(46, merge(raise_, side('Hand', 'R', z=-10)))
    c.key(56, raise_); c.key(84, hold)
    c.props = [('bottle', 'Hand.R')]
    return c


def hold_tablet():
    c = Clip('tablet', 96); c.lag = {'Head': 3, 'Hand.R': 2}
    hold = P(side('UpperArm', 'L', x=-22, y=8), side('ForeArm', 'L', x=-95, tw=-60), side('Hand', 'L', x=-8), open_hand('L', .3),
             {'Head': (16, 0, 4), 'Neck': (6, 0, 0)})
    tap = merge(hold, side('UpperArm', 'R', x=-22, y=6), side('ForeArm', 'R', x=-100), side('Hand', 'R', x=-15),
                side('Fingers1', 'R', y=10), side('Fingers2', 'R', y=60), side('Fingers3', 'R', y=50))
    look = merge(hold, {'Head': (-4, 0, -2), 'Neck': (0, 0, 0)})
    c.key(0, hold); c.key(16, tap); c.key(22, merge(tap, side('Hand', 'R', x=-5))); c.key(28, tap); c.key(44, look, ease='out'); c.key(70, look); c.key(86, hold)
    c.props = [('tablet', 'Hand.L')]
    return c


def think():
    c = Clip('think', 96); c.lag = {'Head': 4}
    t = P(side('UpperArm', 'R', x=-30, y=-4), side('ForeArm', 'R', x=-140, tw=-20), side('Hand', 'R', x=-20), fist('R', .55),
          side('UpperArm', 'L', x=-12, y=0), side('ForeArm', 'L', x=-90, tw=-50),
          {'Head': (-4, -8, 14), 'Spine': (0, 0, 4)})
    c.key(0, P()); c.key(18, t, ease='out'); c.key(40, merge(t, {'Head': (-8, -4, 22)})); c.key(62, merge(t, {'Head': (0, -10, 8)})); c.key(80, t); c.key(90, P())
    return c


ALL = {f.__name__: f for f in (wave, thumbs_up, cheer, fist_pump, squat, lunge, shoulder_press, stretch, pushup, plank,
                               hold_plate, hold_bottle, hold_tablet, think)}
