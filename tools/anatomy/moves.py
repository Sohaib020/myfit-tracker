"""Motion library: each exercise = start pose A, end pose B (looped A→B→A), props and camera.
Angles in degrees: arm/fore/leg/shin = (abduction, flexion) — see pose.apply."""

SUP_BENCH = dict(pitch=-90, ground=False, lift=-0.18)                 # lying face-up on a flat bench
SUP_FLOOR = dict(pitch=-90, ground=False, lift=-0.69)                 # lying face-up on the floor
PRONE = dict(pitch=86, ground='hands', toes=True)                     # face-down, on hands/forearms + toes
SEAT = dict(ground=False, lift=-0.35, legL=(10, 90), legR=(10, 90), shinL=(8, 0), shinR=(8, 0))
BENCH = 'bench:-0.42:0.25'; SEATP = 'seat'

def M(a, b, props=(), view=None, **kw):
    d = dict(A=a, B=b, props=list(props), view=view or {}); d.update(kw); return d

def j(*ds, **kw):
    out = {}
    for d in ds: out.update(d)
    out.update(kw); return out

# ---------------------------------------------------------------- pattern builders
def squat(bar='back', depth=1.0, wide=False):
    ab = 26 if wide else 14
    arms = {'back': dict(armL=(78, -20), armR=(78, -20), foreL=(25, 160), foreR=(25, 160)),
            'front': dict(armL=(18, 85), armR=(18, 85), foreL=(40, 165), foreR=(40, 165)),
            'goblet': dict(armL=(14, 35), armR=(14, 35), foreL=(10, 150), foreR=(10, 150)),
            'db': dict(armL=(10, 0), armR=(10, 0), foreL=(10, 2), foreR=(10, 2)),
            'none': dict(armL=(12, 90), armR=(12, 90), foreL=(10, 92), foreR=(10, 92))}[bar]
    lean = {'back': 32, 'front': 14, 'goblet': 18, 'db': 26, 'none': 22}[bar] * depth
    a = j(arms, legL=(ab - 4, 0), legR=(ab - 4, 0), shinL=(ab - 6, 0), shinR=(ab - 6, 0))
    b = j(arms, torso=lean, legL=(ab, 95 * depth), legR=(ab, 95 * depth), shinL=(ab - 6, -38 * depth), shinR=(ab - 6, -38 * depth))
    if bar == 'db': b.update(armL=(10, lean), armR=(10, lean), foreL=(10, lean), foreR=(10, lean))
    props = {'back': ['barbell'], 'front': ['barbell'], 'goblet': ['kb'], 'db': ['dumbbells'], 'none': []}[bar]
    return M(a, b, props)

def hinge(kind='rdl', bar='barbell'):
    t = {'rdl': 78, 'stiff': 85, 'dead': 48, 'gm': 80}[kind]
    leg = {'rdl': (8, 18), 'stiff': (8, 8), 'dead': (12, 72), 'gm': (8, 14)}[kind]
    shin = {'rdl': (8, 0), 'stiff': (8, 0), 'dead': (10, -28), 'gm': (8, 0)}[kind]
    if kind == 'gm':
        arms = dict(armL=(78, -20), armR=(78, -20), foreL=(25, 160), foreR=(25, 160))
        return M(j(arms), j(arms, torso=t, legL=leg, legR=leg, shinL=shin, shinR=shin), ['barbell'])
    a = dict(armL=(8, 6), armR=(8, 6), foreL=(8, 6), foreR=(8, 6))
    b = dict(torso=t, legL=leg, legR=leg, shinL=shin, shinR=shin, armframe='world', armL=(8, 0), armR=(8, 0), foreL=(8, 0), foreR=(8, 0))
    return M(a, b, [bar] if bar else [])

def lunge(props=('dumbbells',), step=False):
    hang = dict(armL=(10, 0), armR=(10, 0), foreL=(10, 2), foreR=(10, 2)) if props else dict(armL=(20, 10), armR=(20, 10), foreL=(30, 40), foreR=(30, 40))
    a = j(hang)
    b = j(hang, legL=(6, 82), shinL=(6, -2), legR=(6, -18), shinR=(6, -95))
    if step:
        a = j(hang, legL=(6, 70), shinL=(6, -10)); b = j(hang, tiptoe=0, lift=0.45, ground=False)
        return M(a, b, list(props) + ['step'])
    return M(a, b, list(props))

def bench_press(kind='bar', incline=0, grip=70):
    sup = j(SUP_BENCH, pitch=-90 + incline, legL=(14, 12), legR=(14, 12), shinL=(10, -85), shinR=(10, -85))
    a = j(sup, armL=(12, 90), armR=(12, 90), foreL=(12, 90), foreR=(12, 90))
    b = j(sup, armL=(grip, -4), armR=(grip, -4), foreL=(10, 90), foreR=(10, 90))
    props = (['dumbbells'] if kind == 'db' else ['barbell']) + [BENCH + (':%d' % incline if incline else '')]
    return M(a, b, props, view=dict(az=-62, el=16))

def seated_press(kind='chest'):
    if kind == 'chest':
        return M(j(SEAT, armL=(55, 80), armR=(55, 80), foreL=(15, 90), foreR=(15, 90)), j(SEAT, armL=(18, 88), armR=(18, 88), foreL=(15, 90), foreR=(15, 90)), [SEATP])
    return None

def fly(kind='db'):
    if kind == 'db':
        sup = j(SUP_BENCH, legL=(14, 12), legR=(14, 12), shinL=(10, -85), shinR=(10, -85))
        return M(j(sup, armL=(10, 90), armR=(10, 90), foreL=(8, 95), foreR=(8, 95)), j(sup, armL=(84, 2), armR=(84, 2), foreL=(84, 10), foreR=(84, 10)), ['dumbbells', BENCH], view=dict(az=-62, el=16))
    if kind == 'cable':
        a = dict(torso=14, armL=(10, 58), armR=(10, 58), foreL=(4, 55), foreR=(4, 55))
        b = dict(torso=14, armL=(74, 16), armR=(74, 16), foreL=(80, 24), foreR=(80, 24))
        return M(a, b, ['cable:1.2:0.2:1.0'])
    if kind == 'machine':
        return M(j(SEAT, armL=(86, 4), armR=(86, 4), foreL=(86, 88), foreR=(86, 88)), j(SEAT, armL=(28, 88), armR=(28, 88), foreL=(8, 90), foreR=(8, 90)), [SEATP])
    if kind == 'rear':
        a = dict(torso=72, armframe='world', armL=(8, 0), armR=(8, 0), foreL=(8, 4), foreR=(8, 4))
        b = dict(torso=72, armframe='world', armL=(86, 0), armR=(86, 0), foreL=(86, 4), foreR=(86, 4))
        return M(a, b, ['dumbbells'])

def pushup(wide=False, incline=0):
    ab = 60 if wide else 42
    a = j(PRONE, pitch=68 - incline, armL=(ab - 30, 92), armR=(ab - 30, 92), foreL=(ab - 30, 92), foreR=(ab - 30, 92))
    b = j(PRONE, pitch=80 - incline, armL=(ab, 40), armR=(ab, 40), foreL=(ab - 25, 100), foreR=(ab - 25, 100))
    return M(a, b, [], view=dict(az=-70, el=12, cz=-0.45))

def ohp(kind='bar', seated=False):
    base = SEAT if seated else {}
    a = j(base, armL=(70, 22), armR=(70, 22), foreL=(14, 176), foreR=(14, 176))
    b = j(base, armL=(14, 176), armR=(14, 176), foreL=(10, 178), foreR=(10, 178))
    props = (['dumbbells'] if kind == 'db' else ['barbell'] if kind == 'bar' else []) + ([SEATP] if seated else [])
    return M(a, b, props, view=dict(cz=0.15, scale=2.45))

def raise_(kind='lateral', props=('dumbbells',)):
    a = dict(armL=(8, 6), armR=(8, 6), foreL=(8, 10), foreR=(8, 10))
    b = {'lateral': dict(armL=(88, 10), armR=(88, 10), foreL=(86, 16), foreR=(86, 16)),
         'front': dict(armL=(6, 90), armR=(6, 90), foreL=(6, 92), foreR=(6, 92))}[kind]
    return M(a, b, list(props))

def shrug(bar='barbell'):
    h = dict(armL=(10, 2), armR=(10, 2), foreL=(10, 2), foreR=(10, 2))
    return M(j(h), j(h, shrug=12), [bar])

def upright_row(bar='barbell'):
    return M(dict(armL=(8, 6), armR=(8, 6), foreL=(6, 6), foreR=(6, 6)), dict(armL=(72, 26), armR=(72, 26), foreL=(-10, 168), foreR=(-10, 168)), [bar])

def pulldown(kind='wide'):
    ab = {'wide': 30, 'close': 14, 'under': 18}[kind]
    a = j(SEAT, armL=(ab, 172), armR=(ab, 172), foreL=(ab - 4, 176), foreR=(ab - 4, 176))
    b = j(SEAT, armL=(64, 12), armR=(64, 12), foreL=(14, 172), foreR=(14, 172))
    return M(a, b, [SEATP, 'cable:0:0.1:1.4'])

def straight_pulldown():
    a = dict(torso=18, armL=(10, 120), armR=(10, 120), foreL=(10, 122), foreR=(10, 122))
    b = dict(torso=18, armL=(10, 8), armR=(10, 8), foreL=(10, 10), foreR=(10, 10))
    return M(a, b, ['cable:0:-0.7:1.3'])

def pullup(chin=False):
    ab = 18 if chin else 34
    hang = dict(ground=False, legL=(6, 18), legR=(6, 18), shinL=(6, -60), shinR=(6, -60))
    a = j(hang, lift=0.32, armL=(ab, 176), armR=(ab, 176), foreL=(ab - 4, 178), foreR=(ab - 4, 178))
    b = j(hang, lift=0.74, armL=(62, 6), armR=(62, 6), foreL=(20, 172), foreR=(20, 172))
    return M(a, b, ['pullbar'], view=dict(cz=0.45, scale=2.7))

def row(kind='bar'):
    if kind in ('bar', 'db'):
        a = dict(torso=62, legL=(10, 22), legR=(10, 22), shinL=(10, -6), shinR=(10, -6), armframe='world', armL=(10, 0), armR=(10, 0), foreL=(10, 0), foreR=(10, 0))
        b = j(a, armL=(22, -55), armR=(22, -55), foreL=(10, 0), foreR=(10, 0))
        return M(a, b, ['barbell' if kind == 'bar' else 'dumbbells'], view=dict(az=-75, el=8))
    if kind == 'one':
        a = dict(torso=72, legL=(10, 22), legR=(10, 22), shinL=(10, -6), shinR=(10, -6), armframe='world', armL=(10, 0), armR=(14, 0), foreL=(10, 0), foreR=(14, 0))
        b = j(a, armL=(18, -60), foreL=(8, 0))
        return M(a, b, ['dumbbells'], view=dict(az=-75, el=8))
    if kind == 'cable':
        s = dict(ground=False, lift=-0.66, legL=(10, 82), legR=(10, 82), shinL=(10, 72), shinR=(10, 72))
        return M(j(s, torso=10, armL=(10, 90), armR=(10, 90), foreL=(10, 90), foreR=(10, 90)), j(s, torso=-6, armL=(16, -24), armR=(16, -24), foreL=(10, 90), foreR=(10, 90)), ['cable:0:-1.3:-0.6'], view=dict(az=-80, el=8))
    if kind == 'inverted':
        return M(dict(pitch=-62, ground=True, armL=(30, 90), armR=(30, 90), foreL=(28, 90), foreR=(28, 90)), dict(pitch=-48, ground=True, armL=(60, 10), armR=(60, 10), foreL=(18, 90), foreR=(18, 90)), ['pullbar'], view=dict(az=-80, el=8))

def curl(props=('dumbbells',), preacher=False, seated=False, cable=False):
    base = SEAT if (seated or preacher) else {}
    ua = (10, 40) if preacher else (6, 2)
    a = j(base, armL=ua, armR=ua, foreL=(6, ua[1] + 2), foreR=(6, ua[1] + 2))
    b = j(base, armL=ua, armR=ua, foreL=(6, 145), foreR=(6, 145))
    pr = list(props) + ([SEATP] if (seated or preacher) else []) + (['cable:0:-0.6:-0.7'] if cable else [])
    return M(a, b, pr)

def pushdown(rope=False):
    ua = (6, 6)
    a = dict(torso=10, armL=ua, armR=ua, foreL=(10 if rope else 6, 112), foreR=(10 if rope else 6, 112))
    b = dict(torso=10, armL=ua, armR=ua, foreL=(18 if rope else 6, 6), foreR=(18 if rope else 6, 6))
    return M(a, b, ['cable:0:-0.55:1.2'])

def oh_ext(props=('dumbbells',), cable=False):
    ua = (10, 172)
    a = dict(armL=ua, armR=ua, foreL=(8, 330), foreR=(8, 330))
    b = dict(armL=ua, armR=ua, foreL=(8, 176), foreR=(8, 176))
    return M(a, b, list(props) + (['cable:0:0.7:-0.6'] if cable else []), view=dict(cz=0.15, scale=2.45))

def skull(bar='barbell'):
    sup = j(SUP_BENCH, legL=(14, 12), legR=(14, 12), shinL=(10, -85), shinR=(10, -85))
    a = j(sup, armL=(12, 102), armR=(12, 102), foreL=(10, 102), foreR=(10, 102))
    b = j(sup, armL=(12, 108), armR=(12, 108), foreL=(10, 200), foreR=(10, 200))
    return M(a, b, [bar, BENCH], view=dict(az=-62, el=16))

def kickback():
    a = dict(torso=72, armframe='world', armL=(10, -88), armR=(10, -88), foreL=(10, 0), foreR=(10, 0), legL=(10, 18), legR=(10, 18))
    b = j(a, foreL=(10, -88), foreR=(10, -88))
    return M(a, b, ['dumbbells'], view=dict(az=-80, el=8))

def dip(bench=False):
    if bench:
        s = dict(ground=True, legL=(8, 82), legR=(8, 82), shinL=(8, 60), shinR=(8, 60))
        return M(j(s, lift=0.05, armL=(18, -30), armR=(18, -30), foreL=(16, -30), foreR=(16, -30)), j(s, lift=-0.15, armL=(18, -80), armR=(18, -80), foreL=(14, -10), foreR=(14, -10)), [BENCH.replace('0.25', '0.55')], view=dict(az=-70, el=10))
    s = dict(ground=False, legL=(6, 20), legR=(6, 20), shinL=(6, -70), shinR=(6, -70))
    return M(j(s, lift=0.32, armL=(14, -6), armR=(14, -6), foreL=(12, -4), foreR=(12, -4)), j(s, lift=0.08, torso=10, armL=(16, -62), armR=(16, -62), foreL=(12, 0), foreR=(12, 0)), ['dipbars'], view=dict(az=-60, el=10, cz=0.15, scale=2.4))

def leg_press():
    s = dict(pitch=-48, ground=False, lift=-0.25, armL=(25, 20), armR=(25, 20), foreL=(25, 30), foreR=(25, 30))
    return M(j(s, legL=(14, 140), legR=(14, 140), shinL=(10, 40), shinR=(10, 40)), j(s, legL=(12, 100), legR=(12, 100), shinL=(10, 98), shinR=(10, 98)), ['bench:-0.42:0.22:48'], view=dict(az=-80, el=8))

def leg_ext():
    return M(j(SEAT), j(SEAT, shinL=(8, 86), shinR=(8, 86)), [SEATP], view=dict(az=-70, el=8))

def leg_curl(seated=False):
    if seated: return M(j(SEAT, shinL=(8, 86), shinR=(8, 86)), j(SEAT, shinL=(8, -12), shinR=(8, -12)), [SEATP], view=dict(az=-70, el=8))
    p = dict(pitch=90, ground=False, lift=-0.18, armL=(40, 160), armR=(40, 160), foreL=(30, 175), foreR=(30, 175))
    return M(j(p), j(p, shinL=(6, -105), shinR=(6, -105)), [BENCH.replace('0.25', '-0.1')], view=dict(az=-75, el=14))

def calf(seated=False):
    if seated: return M(j(SEAT), j(SEAT, tiptoe=1.0, toes=True), [SEATP])
    return M(dict(), dict(tiptoe=1.0, toes=True, ground=True), [])

def bridge(thrust=False):
    s = j(SUP_FLOOR, legL=(14, 62), legR=(14, 62), shinL=(10, -62), shinR=(10, -62), armL=(20, 0), armR=(20, 0), foreL=(20, 0), foreR=(20, 0))
    b = j(s, pitch=-68, lift=-0.55, legL=(14, 22), legR=(14, 22), shinL=(10, -88), shinR=(10, -88))
    return M(s, b, (['barbell@hips'] if thrust else []), view=dict(az=-80, el=10, cz=-0.5))

def crunch(sit=False, cable=False):
    if cable:
        k = dict(ground='knees', legL=(8, 0), legR=(8, 0), shinL=(8, -92), shinR=(8, -92), armL=(20, 160), armR=(20, 160), foreL=(30, 200), foreR=(30, 200))
        return M(j(k, torso=20), j(k, torso=70), ['cable:0:-0.3:1.3'], view=dict(az=-75, el=8, cz=-0.3))
    s = j(SUP_FLOOR, legL=(12, 52), legR=(12, 52), shinL=(10, -55), shinR=(10, -55), armL=(55, 150), armR=(55, 150), foreL=(70, 210), foreR=(70, 210))
    return M(s, j(s, torso=78 if sit else 32), [], view=dict(az=-80, el=12, cz=-0.5))

def leg_raise(hanging=False):
    if hanging:
        h = dict(ground=False, lift=0.15, armL=(30, 176), armR=(30, 176), foreL=(26, 178), foreR=(26, 178))
        return M(j(h), j(h, legL=(6, 92), legR=(6, 92), shinL=(6, 92), shinR=(6, 92)), ['pullbar'], view=dict(az=-70, el=6, cz=0.2, scale=2.4))
    s = j(SUP_FLOOR, armL=(20, 0), armR=(20, 0), foreL=(20, 0), foreR=(20, 0))
    return M(j(s), j(s, legL=(5, 88), legR=(5, 88), shinL=(5, 88), shinR=(5, 88)), [], view=dict(az=-80, el=12, cz=-0.5))

def plank(side=False):
    p = j(PRONE, pitch=82, armL=(12, 90), armR=(12, 90), foreL=(10, 178), foreR=(10, 178))
    if side: p = dict(roll=-78, ground='hands', armL=(90, 0), foreL=(90, 90), armR=(10, 0), foreR=(10, 0), toes=True)
    return M(p, j(p, torso=3), [], view=dict(az=-70, el=10, cz=-0.45), hold=True)

def twist(cable=False):
    s = dict(ground=False, lift=-0.62, pitch=-28, legL=(10, 70), legR=(10, 70), shinL=(10, 34), shinR=(10, 34), armL=(20, 60), armR=(20, 60), foreL=(5, 80), foreR=(5, 80))
    return M(j(s, twist=-35), j(s, twist=35), (['cable:1.2:-0.4:-0.5'] if cable else []), view=dict(az=-30, el=12, cz=-0.4))

def climber():
    p = j(PRONE, pitch=68, armL=(18, 92), armR=(18, 92), foreL=(18, 92), foreR=(18, 92))
    return M(j(p, legL=(8, 88), shinL=(8, -30)), j(p, legR=(8, 88), shinR=(8, -30)), [], view=dict(az=-70, el=10, cz=-0.4))

def jacks():
    return M(dict(armL=(10, 0), armR=(10, 0), legL=(4, 0), legR=(4, 0)), dict(armL=(165, 0), armR=(165, 0), foreL=(170, 0), foreR=(170, 0), legL=(26, 0), legR=(26, 0), shinL=(26, 0), shinR=(26, 0), ground=True, lift=0.08), view=dict(cz=0.15, scale=2.45))

def hyper(superman=False):
    if superman:
        p = dict(pitch=90, ground=False, lift=-0.68, armL=(20, 172), armR=(20, 172), foreL=(20, 176), foreR=(20, 176))
        return M(j(p), j(p, torso=-14, legL=(6, -14), legR=(6, -14), shinL=(6, -14), shinR=(6, -14)), [], view=dict(az=-80, el=12, cz=-0.5))
    p = dict(pitch=90, ground=False, lift=-0.2, armL=(50, 150), armR=(50, 150), foreL=(60, 200), foreR=(60, 200))
    return M(j(p, torso=62), j(p, torso=-4), [BENCH.replace('0.25', '-0.15')], view=dict(az=-80, el=10))

def swing():
    a = dict(torso=58, legL=(14, 34), legR=(14, 34), shinL=(12, -14), shinR=(12, -14), armframe='world', armL=(8, -14), armR=(8, -14), foreL=(6, -18), foreR=(6, -18))
    b = dict(armframe='world', armL=(8, 88), armR=(8, 88), foreL=(6, 90), foreR=(6, 90))
    return M(a, b, ['kb'], view=dict(az=-70, el=6))

def side_bend():
    h = dict(armL=(10, 0), armR=(10, 0), foreL=(10, 2), foreR=(10, 2))
    return M(j(h, side=-14), j(h, side=14), ['dumbbells'], view=dict(az=0, el=4))

def thruster(props=('dumbbells',)):
    a = j(squat('front')['B'])
    b = dict(armL=(14, 176), armR=(14, 176), foreL=(10, 178), foreR=(10, 178))
    return M(a, b, list(props), view=dict(cz=0.15, scale=2.45))

# ---------------------------------------------------------------- exercise → motion
MOVES = {
    # chest
    'Barbell_Bench_Press_-_Medium_Grip': bench_press(), 'Barbell_Incline_Bench_Press_-_Medium_Grip': bench_press(incline=30), 'Incline_Dumbbell_Press': bench_press('db', 30),
    'Dumbbell_Bench_Press': bench_press('db'), 'Decline_Barbell_Bench_Press': bench_press(incline=-15), 'Close-Grip_Barbell_Bench_Press': bench_press(grip=32),
    'Dumbbell_Bench_Press_with_Neutral_Grip': bench_press('db', grip=40), 'Smith_Machine_Bench_Press': bench_press(), 'Floor_Press': bench_press(),
    'Machine_Bench_Press': seated_press(), 'Leverage_Chest_Press': seated_press(), 'Cable_Chest_Press': seated_press(),
    'Dumbbell_Flyes': fly(), 'Incline_Dumbbell_Flyes': fly(), 'Cable_Crossover': fly('cable'), 'Low_Cable_Crossover': fly('cable'), 'Butterfly': fly('machine'),
    'Pushups': pushup(), 'Push-Up_Wide': pushup(True), 'Incline_Push-Up': pushup(incline=25), 'Decline_Push-Up': pushup(incline=-10), 'Plyo_Push-up': pushup(),
    'Dips_-_Chest_Version': dip(),
    # back
    'Barbell_Deadlift': hinge('dead'), 'Romanian_Deadlift': hinge('rdl'), 'Sumo_Deadlift': hinge('dead'), 'Stiff-Legged_Dumbbell_Deadlift': hinge('stiff', 'dumbbells'),
    'Trap_Bar_Deadlift': hinge('dead'), 'Good_Morning': hinge('gm'), 'Kettlebell_One-Legged_Deadlift': hinge('rdl', 'kb'),
    'Bent_Over_Barbell_Row': row('bar'), 'Bent_Over_Two-Dumbbell_Row': row('db'), 'One-Arm_Dumbbell_Row': row('one'), 'T-Bar_Row_with_Handle': row('bar'),
    'Seated_Cable_Rows': row('cable'), 'Leverage_Iso_Row': row('cable'), 'Leverage_High_Row': row('cable'), 'Dumbbell_Incline_Row': row('db'), 'Inverted_Row': row('inverted'),
    'Barbell_Rear_Delt_Row': row('bar'),
    'Wide-Grip_Lat_Pulldown': pulldown('wide'), 'Close-Grip_Front_Lat_Pulldown': pulldown('close'), 'Underhand_Cable_Pulldowns': pulldown('under'), 'One_Arm_Lat_Pulldown': pulldown('close'),
    'Straight-Arm_Pulldown': straight_pulldown(), 'Rope_Straight-Arm_Pulldown': straight_pulldown(),
    'Pullups': pullup(), 'Chin-Up': pullup(True), 'V-Bar_Pullup': pullup(True), 'Band_Assisted_Pull-Up': pullup(),
    'Hyperextensions_Back_Extensions': hyper(), 'Superman': hyper(True),
    # shoulders
    'Dumbbell_Shoulder_Press': ohp('db', True), 'Seated_Dumbbell_Press': ohp('db', True), 'Standing_Military_Press': ohp(), 'Seated_Barbell_Military_Press': ohp('bar', True),
    'Arnold_Dumbbell_Press': ohp('db', True), 'Push_Press': ohp(), 'Leverage_Shoulder_Press': ohp('none', True), 'Smith_Machine_Overhead_Shoulder_Press': ohp('bar', True),
    'Side_Lateral_Raise': raise_(), 'Cable_Seated_Lateral_Raise': raise_(props=['cable:0.4:0:-0.7']), 'Front_Dumbbell_Raise': raise_('front'), 'Front_Cable_Raise': raise_('front', ['cable:0:0.5:-0.7']),
    'Face_Pull': M(dict(armL=(30, 90), armR=(30, 90), foreL=(25, 90), foreR=(25, 90)), dict(armL=(80, 40), armR=(80, 40), foreL=(20, 150), foreR=(20, 150)), ['cable:0:-1.2:0.6']),
    'Reverse_Flyes': fly('rear'), 'Bent_Over_Dumbbell_Rear_Delt_Raise_With_Head_On_Bench': fly('rear'),
    'Barbell_Shrug': shrug(), 'Dumbbell_Shrug': shrug('dumbbells'), 'Clean_Shrug': shrug(), 'Upright_Barbell_Row': upright_row(), 'Standing_Dumbbell_Upright_Row': upright_row('dumbbells'),
    # arms
    'Dumbbell_Bicep_Curl': curl(), 'Barbell_Curl': curl(['barbell']), 'EZ-Bar_Curl': curl(['barbell']), 'Hammer_Curls': curl(), 'Alternate_Hammer_Curl': curl(),
    'Preacher_Curl': curl(['barbell'], preacher=True), 'Spider_Curl': curl(['barbell'], preacher=True), 'Machine_Bicep_Curl': curl([], preacher=True),
    'Concentration_Curls': curl(seated=True), 'Incline_Dumbbell_Curl': curl(seated=True), 'Alternate_Incline_Dumbbell_Curl': curl(seated=True),
    'Cable_Hammer_Curls_-_Rope_Attachment': curl([], cable=True),
    'Triceps_Pushdown': pushdown(), 'Triceps_Pushdown_-_Rope_Attachment': pushdown(True), 'Reverse_Grip_Triceps_Pushdown': pushdown(),
    'Lying_Triceps_Press': skull(), 'EZ-Bar_Skullcrusher': skull(), 'Decline_Close-Grip_Bench_To_Skull_Crusher': skull(), 'Cable_Lying_Triceps_Extension': skull('barbell'),
    'Standing_Dumbbell_Triceps_Extension': oh_ext(), 'Cable_Rope_Overhead_Triceps_Extension': oh_ext([], True), 'Low_Cable_Triceps_Extension': oh_ext([], True), 'Machine_Triceps_Extension': oh_ext([]),
    'Tricep_Dumbbell_Kickback': kickback(), 'Dips_-_Triceps_Version': dip(), 'Bench_Dips': dip(True),
    # legs
    'Barbell_Squat': squat('back'), 'Front_Barbell_Squat': squat('front'), 'Goblet_Squat': squat('goblet'), 'Bodyweight_Squat': squat('none'), 'Dumbbell_Squat': squat('db'),
    'Smith_Machine_Squat': squat('back'), 'Hack_Squat': squat('back'), 'Box_Squat': squat('back', 0.8), 'Plie_Dumbbell_Squat': squat('goblet', wide=True),
    'Kettlebell_Thruster': thruster(['kb']), 'Dumbbell_Clean': thruster(),
    'Leg_Press': leg_press(), 'Narrow_Stance_Leg_Press': leg_press(), 'Leg_Extensions': leg_ext(), 'Single-Leg_Leg_Extension': leg_ext(),
    'Lying_Leg_Curls': leg_curl(), 'Seated_Leg_Curl': leg_curl(True), 'Standing_Leg_Curl': leg_curl(),
    'Dumbbell_Lunges': lunge(), 'Barbell_Lunge': lunge(['barbell']), 'Barbell_Walking_Lunge': lunge(['barbell']), 'Bodyweight_Walking_Lunge': lunge(()),
    'Split_Squat_with_Dumbbells': lunge(), 'Split_Squats': lunge(()), 'Dumbbell_Step_Ups': lunge(step=True),
    'Barbell_Hip_Thrust': bridge(True), 'Butt_Lift_Bridge': bridge(), 'Single_Leg_Glute_Bridge': bridge(), 'Hip_Lift_with_Band': bridge(),
    'Glute_Kickback': M(dict(ground='knees', pitch=80, legL=(8, 90), legR=(8, 90), shinL=(8, 0), shinR=(8, 0), armL=(10, 90), armR=(10, 90), foreL=(10, 90), foreR=(10, 90)),
                        dict(ground='knees', pitch=80, legL=(8, -10), legR=(8, 90), shinL=(8, -10), shinR=(8, 0), armL=(10, 90), armR=(10, 90), foreL=(10, 90), foreR=(10, 90)), [], view=dict(az=-80, el=10, cz=-0.4)),
    'Standing_Calf_Raises': calf(), 'Seated_Calf_Raise': calf(True), 'Smith_Machine_Calf_Raise': calf(), 'Calf_Press': calf(),
    # core & cardio
    'Crunches': crunch(), 'Sit-Up': crunch(True), 'Tuck_Crunch': crunch(), 'Oblique_Crunches': crunch(), 'Reverse_Crunch': leg_raise(), 'Decline_Crunch': crunch(),
    'Cable_Crunch': crunch(cable=True), 'Hanging_Leg_Raise': leg_raise(True), 'Flat_Bench_Lying_Leg_Raise': leg_raise(), 'Leg_Lift': leg_raise(),
    'Plank': plank(), 'Side_Bridge': plank(), 'Push_Up_to_Side_Plank': pushup(), 'Russian_Twist': twist(), 'Cable_Russian_Twists': twist(True),
    'Dumbbell_Side_Bend': side_bend(), 'Mountain_Climbers': climber(), 'Star_Jump': jacks(), 'Air_Bike': climber(),
    'One-Arm_Kettlebell_Swings': swing(), 'Kettlebell_Sumo_High_Pull': upright_row('kb'),
}
