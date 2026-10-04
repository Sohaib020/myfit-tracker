"""Builds app/src/main/assets/programs.json — the multi-week programs in Train → Programs.
Exercises are catalog keys (exercise_catalog.json 'k'); the script fails if any key is missing.
Item = (key, sets, reps, rest_s[, 'm']) — reps '8-12' | '10' | '30s' / '10m' (duration) | 'max';
'm' marks a main lift whose reps follow the current phase's rep scheme."""
import json, os, sys
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
CAT = {o['k'] for o in json.load(open(os.path.join(ROOT, 'app/src/main/assets/exercise_catalog.json')))}
MASCOTS = {'pip', 'motu', 'kami', 'chakor', 'taj', 'khargosh', 'zara', 'bhalu', 'lomri', 'shaheen', 'nevla', 'bulhan', 'kala',
           'ullu', 'sakeen', 'bhoori', 'sehi', 'gogi', 'monal', 'mor', 'yaku'}


def I(k, s, r, rest, *f):
    if k not in CAT: sys.exit('missing key ' + k)
    d = dict(k=k, s=s, r=r, rest=rest)
    if 'm' in f: d['m'] = 1
    return d


def D(name, focus, *items): return dict(name=name, focus=focus, items=[I(*i) for i in items])


def d(day, name=None, focus=None):
    """Re-label a shared block for one program."""
    x = dict(day); x['items'] = [dict(i) for i in day['items']]
    if name: x['name'] = name
    if focus: x['focus'] = focus
    return x


# ------------------------------------------------------------------ weekly progressions
def hyp(w):
    if w <= 6:
        return [dict(w=[1, w - 2], label='Base', note='Own the technique. Stop each set with 2 reps in reserve; add weight when you hit the top of the range.'),
                dict(w=[w - 1, w - 1], label='Push', note='One extra set on every exercise. Last set close to failure.', sets=1),
                dict(w=[w, w], label='Deload', note='Recovery week: one set less, about 60% effort.', sets=-1)]
    return [dict(w=[1, 3], label='Base', note='Own the technique. Stop each set with 2 reps in reserve; add weight when you hit the top of the range.'),
            dict(w=[4, w - 2], label='Build', note='One extra set on every exercise. Push the last set to 1 rep in reserve.', sets=1),
            dict(w=[w - 1, w - 1], label='Peak', note='Heaviest week: main lifts drop to 6–8 reps — chase rep PRs.', sets=1, reps='6-8'),
            dict(w=[w, w], label='Deload', note='Recovery week: one set less, about 60% effort. You come back stronger.', sets=-1)]


def strn(w):
    a = max(1, w // 3)
    return [dict(w=[1, a], label='Volume', note='Main lifts for 5 reps. Add 2.5 kg every session you complete all reps.', reps='5'),
            dict(w=[a + 1, w - 2], label='Intensity', note='Main lifts 3–5 reps, heavier. Rest fully (3–5 min).', reps='3-5'),
            dict(w=[w - 1, w - 1], label='Peak', note='Main lifts: doubles and triples near your best. Perfect form only.', reps='2-3'),
            dict(w=[w, w], label='Deload', note='Recovery week: one set less, light and fast.', sets=-1, reps='5')]


def fat(w):
    return [dict(w=[1, 2], label='Base', note='Steady pace. Walk 8–10k steps a day alongside the plan.'),
            dict(w=[3, w - 1], label='Density', note='Rests 20% shorter and one extra set. Keep protein high.', rest=0.8, sets=1),
            dict(w=[w, w], label='Test', note='Repeat week 1 — compare reps, rest and how you feel.')]


def base(w, note='Repeat each week, adding a rep or a little weight when every set feels solid.'):
    return [dict(w=[1, w], label='Progress', note=note)]


P = []


def prog(id, name, tag, desc, level, gender, equip, goals, weeks, mins, mascot, days, phases):
    assert mascot in MASCOTS, mascot
    P.append(dict(id=id, name=name, tag=tag, desc=desc, level=level, gender=gender, equip=equip, goals=goals,
                  dpw=len(days), weeks=weeks, mins=mins, mascot=mascot, phases=phases, days=days))


# ------------------------------------------------------------------ blocks: gym
PUSH = D('Push', 'Chest · Shoulders · Triceps',
         ('Barbell_Bench_Press_-_Medium_Grip', 4, '6-10', 150, 'm'), ('Incline_Dumbbell_Press', 3, '8-12', 120),
         ('Seated_Dumbbell_Press', 3, '8-12', 90), ('Side_Lateral_Raise', 3, '12-15', 60), ('Cable_Crossover', 3, '12-15', 60),
         ('Triceps_Pushdown_-_Rope_Attachment', 3, '10-15', 60))
PULL = D('Pull', 'Back · Rear delts · Biceps',
         ('Pullups', 4, '5-10', 120, 'm'), ('Bent_Over_Barbell_Row', 3, '6-10', 120), ('Wide-Grip_Lat_Pulldown', 3, '8-12', 90),
         ('Seated_Cable_Rows', 3, '10-12', 90), ('Face_Pull', 3, '12-15', 60), ('Barbell_Curl', 3, '8-12', 60), ('Hammer_Curls', 2, '10-12', 60))
LEGS = D('Legs', 'Quads · Hamstrings · Glutes · Calves',
         ('Barbell_Squat', 4, '5-8', 180, 'm'), ('Romanian_Deadlift', 3, '8-10', 150), ('Leg_Press', 3, '10-12', 120),
         ('Lying_Leg_Curls', 3, '10-12', 90), ('Leg_Extensions', 2, '12-15', 60), ('Standing_Calf_Raises', 4, '10-15', 60))
PUSH2 = D('Push B', 'Shoulders · Upper chest · Triceps',
          ('Standing_Military_Press', 4, '5-8', 150, 'm'), ('Barbell_Incline_Bench_Press_-_Medium_Grip', 3, '8-10', 120),
          ('Dips_-_Chest_Version', 3, '8-12', 90), ('Cable_Seated_Lateral_Raise', 3, '12-15', 60), ('Incline_Dumbbell_Flyes', 3, '12-15', 60),
          ('Standing_Dumbbell_Triceps_Extension', 3, '10-12', 60))
PULL2 = D('Pull B', 'Back thickness · Biceps',
          ('Barbell_Deadlift', 3, '3-5', 180, 'm'), ('T-Bar_Row_with_Handle', 3, '8-10', 120), ('Close-Grip_Front_Lat_Pulldown', 3, '10-12', 90),
          ('One-Arm_Dumbbell_Row', 3, '10-12', 75), ('Reverse_Flyes', 3, '12-15', 60), ('Incline_Dumbbell_Curl', 3, '10-12', 60))
LEGS2 = D('Legs B', 'Glutes · Hamstrings · Quads',
          ('Front_Barbell_Squat', 4, '6-8', 150, 'm'), ('Barbell_Hip_Thrust', 3, '8-12', 120), ('Barbell_Walking_Lunge', 3, '10-12', 90),
          ('Seated_Leg_Curl', 3, '10-12', 75), ('Seated_Calf_Raise', 4, '12-15', 60), ('Hanging_Leg_Raise', 3, '10-15', 60))
UPPER = D('Upper', 'Chest · Back · Shoulders · Arms',
          ('Barbell_Bench_Press_-_Medium_Grip', 4, '6-8', 150, 'm'), ('Bent_Over_Barbell_Row', 4, '6-8', 120, 'm'),
          ('Dumbbell_Shoulder_Press', 3, '8-12', 90), ('Wide-Grip_Lat_Pulldown', 3, '8-12', 90), ('EZ-Bar_Curl', 2, '10-12', 60),
          ('Triceps_Pushdown', 2, '10-12', 60))
LOWER = D('Lower', 'Quads · Hamstrings · Glutes · Core',
          ('Barbell_Squat', 4, '6-8', 180, 'm'), ('Romanian_Deadlift', 3, '8-10', 150), ('Dumbbell_Lunges', 3, '10-12', 90),
          ('Lying_Leg_Curls', 3, '10-12', 75), ('Standing_Calf_Raises', 3, '12-15', 60), ('Plank', 3, '45s', 45))
UPPER2 = D('Upper B', 'Shoulders · Back · Arms',
           ('Standing_Military_Press', 4, '6-8', 150, 'm'), ('Pullups', 4, '6-10', 120, 'm'), ('Incline_Dumbbell_Press', 3, '8-12', 90),
           ('Seated_Cable_Rows', 3, '10-12', 90), ('Side_Lateral_Raise', 3, '12-15', 60), ('Hammer_Curls', 2, '10-12', 60),
           ('Lying_Triceps_Press', 2, '10-12', 60))
LOWER2 = D('Lower B', 'Posterior chain · Glutes',
           ('Barbell_Deadlift', 3, '4-6', 180, 'm'), ('Leg_Press', 3, '10-12', 120), ('Barbell_Hip_Thrust', 3, '8-12', 90),
           ('Seated_Leg_Curl', 3, '10-12', 75), ('Seated_Calf_Raise', 3, '12-15', 60), ('Cable_Crunch', 3, '12-15', 60))
FB_A = D('Full body A', 'Squat · Press · Row',
         ('Barbell_Squat', 3, '6-10', 150, 'm'), ('Barbell_Bench_Press_-_Medium_Grip', 3, '6-10', 120, 'm'), ('Seated_Cable_Rows', 3, '8-12', 90),
         ('Lying_Leg_Curls', 2, '10-12', 60), ('Side_Lateral_Raise', 2, '12-15', 60), ('Plank', 3, '30s', 45))
FB_B = D('Full body B', 'Hinge · Overhead · Pull',
         ('Romanian_Deadlift', 3, '8-10', 150, 'm'), ('Dumbbell_Shoulder_Press', 3, '8-12', 90), ('Wide-Grip_Lat_Pulldown', 3, '8-12', 90),
         ('Leg_Press', 3, '10-12', 90), ('Dumbbell_Bicep_Curl', 2, '10-12', 60), ('Triceps_Pushdown', 2, '10-12', 60))
FB_C = D('Full body C', 'Lunge · Incline · Row',
         ('Goblet_Squat', 3, '10-12', 90), ('Incline_Dumbbell_Press', 3, '8-12', 90), ('One-Arm_Dumbbell_Row', 3, '10-12', 75),
         ('Barbell_Hip_Thrust', 3, '10-12', 90), ('Face_Pull', 2, '12-15', 60), ('Crunches', 3, '15-20', 45))
CHEST = D('Chest', 'Chest · Front delts',
          ('Barbell_Bench_Press_-_Medium_Grip', 4, '6-10', 150, 'm'), ('Incline_Dumbbell_Press', 3, '8-12', 90), ('Decline_Barbell_Bench_Press', 3, '8-10', 90),
          ('Butterfly', 3, '12-15', 60), ('Dips_-_Chest_Version', 3, 'max', 75))
BACK = D('Back', 'Lats · Mid-back · Rear delts',
         ('Barbell_Deadlift', 3, '4-6', 180, 'm'), ('Pullups', 4, '6-10', 120), ('T-Bar_Row_with_Handle', 3, '8-10', 90),
         ('One_Arm_Lat_Pulldown', 3, '10-12', 60), ('Straight-Arm_Pulldown', 3, '12-15', 60))
SHOULDERS = D('Shoulders', 'Front · Side · Rear delts · Traps',
              ('Seated_Barbell_Military_Press', 4, '6-10', 120, 'm'), ('Arnold_Dumbbell_Press', 3, '10-12', 75), ('Side_Lateral_Raise', 4, '12-15', 45),
              ('Reverse_Flyes', 3, '12-15', 45), ('Upright_Barbell_Row', 3, '10-12', 60), ('Barbell_Shrug', 3, '10-12', 60))
ARMS = D('Arms', 'Biceps · Triceps · Forearms',
         ('Close-Grip_Barbell_Bench_Press', 3, '6-10', 120, 'm'), ('Barbell_Curl', 3, '8-12', 75), ('EZ-Bar_Skullcrusher', 3, '10-12', 60),
         ('Preacher_Curl', 3, '10-12', 60), ('Cable_Rope_Overhead_Triceps_Extension', 3, '12-15', 45), ('Cable_Hammer_Curls_-_Rope_Attachment', 3, '12-15', 45))
LEGS_BB = D('Legs', 'Quads · Hamstrings · Calves',
            ('Barbell_Squat', 4, '6-10', 180, 'm'), ('Hack_Squat', 3, '8-12', 120), ('Romanian_Deadlift', 3, '8-10', 120),
            ('Leg_Extensions', 3, '12-15', 60), ('Lying_Leg_Curls', 3, '10-12', 60), ('Standing_Calf_Raises', 4, '10-15', 45))
SL_A = D('Workout A', 'Squat · Bench · Row',
         ('Barbell_Squat', 5, '5', 180, 'm'), ('Barbell_Bench_Press_-_Medium_Grip', 5, '5', 180, 'm'), ('Bent_Over_Barbell_Row', 5, '5', 150, 'm'))
SL_B = D('Workout B', 'Squat · Press · Deadlift',
         ('Barbell_Squat', 5, '5', 180, 'm'), ('Standing_Military_Press', 5, '5', 180, 'm'), ('Barbell_Deadlift', 1, '5', 180, 'm'))
PL_SQ = D('Squat day', 'Squat focus',
          ('Barbell_Squat', 5, '3-5', 240, 'm'), ('Box_Squat', 3, '3', 180), ('Leg_Press', 3, '8-10', 120), ('Glute_Ham_Raise', 3, '8', 90), ('Plank', 3, '45s', 45))
PL_BP = D('Bench day', 'Bench focus',
          ('Barbell_Bench_Press_-_Medium_Grip', 5, '3-5', 240, 'm'), ('Close-Grip_Barbell_Bench_Press', 3, '5-8', 150), ('Floor_Press', 3, '5', 150),
          ('Bent_Over_Barbell_Row', 4, '6-8', 120), ('Face_Pull', 3, '15', 60))
PL_DL = D('Deadlift day', 'Deadlift focus',
          ('Barbell_Deadlift', 5, '2-5', 240, 'm'), ('Sumo_Deadlift', 3, '3-5', 180), ('Good_Morning', 3, '6-8', 120), ('Pullups', 3, '6-10', 90),
          ('Hyperextensions_Back_Extensions', 3, '12', 60))
PL_OHP = D('Press day', 'Overhead + accessories',
           ('Standing_Military_Press', 5, '3-5', 180, 'm'), ('Push_Press', 3, '3-5', 150), ('Dumbbell_Bench_Press', 3, '8-10', 90),
           ('Wide-Grip_Lat_Pulldown', 3, '8-12', 75), ('Farmers_Walk', 3, '30s', 90))
GLUTE_A = D('Glutes A', 'Glutes · Hamstrings',
            ('Barbell_Hip_Thrust', 4, '8-12', 120, 'm'), ('Romanian_Deadlift', 3, '8-10', 120), ('Split_Squat_with_Dumbbells', 3, '10-12', 75),
            ('Glute_Kickback', 3, '12-15', 45), ('Seated_Leg_Curl', 3, '12-15', 60), ('Monster_Walk', 2, '20', 30))
GLUTE_B = D('Glutes B', 'Glutes · Quads',
            ('Sumo_Deadlift', 4, '6-8', 150, 'm'), ('Dumbbell_Step_Ups', 3, '10-12', 75), ('Goblet_Squat', 3, '10-12', 75),
            ('Single_Leg_Glute_Bridge', 3, '12-15', 45), ('Hip_Lift_with_Band', 3, '15-20', 45), ('Standing_Calf_Raises', 3, '15', 45))
TONE_UP = D('Upper tone', 'Arms · Shoulders · Back',
            ('Dumbbell_Shoulder_Press', 3, '10-12', 60, 'm'), ('Wide-Grip_Lat_Pulldown', 3, '10-12', 60), ('Dumbbell_Bench_Press', 3, '10-12', 60),
            ('Seated_Cable_Rows', 3, '12', 60), ('Side_Lateral_Raise', 3, '12-15', 45), ('Triceps_Pushdown_-_Rope_Attachment', 3, '12-15', 45),
            ('Dumbbell_Bicep_Curl', 3, '12', 45))
CORE = D('Core', 'Abs · Obliques · Lower back',
         ('Hanging_Leg_Raise', 3, '10-15', 60), ('Cable_Crunch', 3, '12-15', 45), ('Russian_Twist', 3, '20', 45),
         ('Plank', 3, '45s', 45), ('Side_Bridge', 3, '30s', 30), ('Hyperextensions_Back_Extensions', 3, '12-15', 45))
HIIT = D('HIIT', 'Intervals · Conditioning',
         ('Rowing_Stationary', 1, '10m', 60), ('Kettlebell_Thruster', 4, '12', 45), ('One-Arm_Kettlebell_Swings', 4, '15', 45),
         ('Mountain_Climbers', 4, '30s', 30), ('Star_Jump', 4, '30s', 30))
CARDIO_Z2 = D('Zone 2 cardio', 'Easy steady cardio', ('Walking_Treadmill', 1, '35m', 0), ('Bicycling_Stationary', 1, '15m', 0))
ATH_A = D('Power', 'Jump · Sprint · Lift',
          ('Front_Box_Jump', 4, '5', 90), ('Barbell_Squat', 4, '4-6', 180, 'm'), ('Push_Press', 3, '4-6', 150), ('Dumbbell_Lunges', 3, '8', 75),
          ('Medicine_Ball_Chest_Pass', 3, '8', 60))
ATH_B = D('Speed & core', 'Agility · Rotation',
          ('Lateral_Bound', 4, '6', 60), ('Single-Cone_Sprint_Drill', 4, '20s', 60), ('Trap_Bar_Deadlift', 4, '4-6', 180, 'm'),
          ('Pullups', 3, 'max', 90), ('Medicine_Ball_Full_Twist', 3, '10', 45), ('Side_Bridge', 3, '30s', 30))
CRICKET_A = D('Bowler power', 'Hips · Rotation · Shoulders',
              ('Trap_Bar_Deadlift', 4, '4-6', 150, 'm'), ('Split_Squat_with_Dumbbells', 3, '8', 75), ('Medicine_Ball_Full_Twist', 4, '8', 45),
              ('Face_Pull', 3, '15', 45), ('Side_Bridge', 3, '30s', 30), ('Lateral_Bound', 3, '6', 60))
CRICKET_B = D('Batter & fielder', 'Speed · Agility · Grip',
              ('Single-Cone_Sprint_Drill', 6, '15s', 60), ('Front_Box_Jump', 3, '5', 75), ('Dumbbell_Bench_Press', 3, '8-10', 90),
              ('One-Arm_Dumbbell_Row', 3, '10', 60), ('Farmers_Walk', 3, '30s', 60), ('Russian_Twist', 3, '20', 30))
SENIOR = D('Gentle strength', 'Balance · Legs · Posture',
           ('Bodyweight_Squat', 3, '8-10', 60), ('Incline_Push-Up', 3, '8-10', 60), ('Butt_Lift_Bridge', 3, '10-12', 45),
           ('Seated_Cable_Rows', 3, '10-12', 60), ('Standing_Calf_Raises', 2, '12', 45), ('Cat_Stretch', 2, '45s', 0))
TREK = D('Trek legs', 'Uphill strength',
         ('Dumbbell_Step_Ups', 4, '12', 75, 'm'), ('Barbell_Walking_Lunge', 3, '12', 90), ('Stairmaster', 1, '20m', 0),
         ('Single_Leg_Glute_Bridge', 3, '12', 45), ('Standing_Calf_Raises', 4, '15', 45))
TREK2 = D('Pack carry', 'Back · Core · Grip',
          ('Farmers_Walk', 4, '40s', 90), ('Bent_Over_Two-Dumbbell_Row', 3, '10-12', 75), ('Hyperextensions_Back_Extensions', 3, '12', 60),
          ('Plank', 3, '60s', 45), ('Walking_Treadmill', 1, '40m', 0))

# ------------------------------------------------------------------ blocks: dumbbells / home / bodyweight / cardio
DB_UP = D('Dumbbell upper', 'Chest · Back · Shoulders · Arms',
          ('Dumbbell_Bench_Press', 4, '8-12', 90, 'm'), ('One-Arm_Dumbbell_Row', 4, '8-12', 75, 'm'), ('Seated_Dumbbell_Press', 3, '8-12', 75),
          ('Dumbbell_Flyes', 3, '12-15', 60), ('Alternate_Hammer_Curl', 3, '10-12', 60), ('Tricep_Dumbbell_Kickback', 3, '12-15', 60))
DB_LO = D('Dumbbell lower', 'Legs · Glutes · Core',
          ('Goblet_Squat', 4, '10-12', 90, 'm'), ('Stiff-Legged_Dumbbell_Deadlift', 4, '10-12', 90, 'm'), ('Split_Squat_with_Dumbbells', 3, '10-12', 75),
          ('Dumbbell_Step_Ups', 3, '10-12', 60), ('Single_Leg_Glute_Bridge', 3, '12-15', 45), ('Russian_Twist', 3, '20', 45))
DB_FB = D('Dumbbell full body', 'Everything, efficiently',
          ('Dumbbell_Squat', 3, '10-12', 75, 'm'), ('Dumbbell_Bench_Press', 3, '8-12', 75), ('Bent_Over_Two-Dumbbell_Row', 3, '10-12', 75),
          ('Dumbbell_Lunges', 3, '10-12', 60), ('Arnold_Dumbbell_Press', 3, '10-12', 60), ('Plank', 3, '40s', 45))
DB_PUSH = D('Dumbbell push', 'Chest · Shoulders · Triceps',
            ('Dumbbell_Bench_Press', 4, '8-12', 90, 'm'), ('Incline_Dumbbell_Press', 3, '10-12', 75), ('Arnold_Dumbbell_Press', 3, '10-12', 75),
            ('Side_Lateral_Raise', 3, '12-15', 45), ('Standing_Dumbbell_Triceps_Extension', 3, '10-12', 60))
DB_PULL = D('Dumbbell pull', 'Back · Biceps',
            ('Bent_Over_Two-Dumbbell_Row', 4, '8-12', 90, 'm'), ('Dumbbell_Incline_Row', 3, '10-12', 75),
            ('Bent_Over_Dumbbell_Rear_Delt_Raise_With_Head_On_Bench', 3, '12-15', 45),
            ('Dumbbell_Shrug', 3, '12-15', 45), ('Dumbbell_Bicep_Curl', 3, '10-12', 60), ('Concentration_Curls', 2, '12', 45))
DB_LEGS = D('Dumbbell legs', 'Quads · Glutes · Hamstrings',
            ('Goblet_Squat', 4, '10-12', 90, 'm'), ('Stiff-Legged_Dumbbell_Deadlift', 4, '10-12', 90), ('Dumbbell_Lunges', 3, '10-12', 75),
            ('Plie_Dumbbell_Squat', 3, '12-15', 60), ('Standing_Calf_Raises', 3, '15-20', 45))
BW_A = D('Bodyweight A', 'Push · Squat · Core',
         ('Pushups', 4, 'max', 75, 'm'), ('Bodyweight_Squat', 4, '15-20', 60), ('Incline_Push-Up', 3, '10-15', 60),
         ('Split_Squats', 3, '10-12', 60), ('Plank', 3, '45s', 45), ('Mountain_Climbers', 3, '30s', 45))
BW_B = D('Bodyweight B', 'Pull · Hinge · Core',
         ('Inverted_Row', 4, '8-12', 75, 'm'), ('Butt_Lift_Bridge', 4, '15-20', 45), ('Superman', 3, '12-15', 45),
         ('Bodyweight_Walking_Lunge', 3, '12', 60), ('Bench_Dips', 3, '10-15', 60), ('Reverse_Crunch', 3, '12-15', 45))
BW_C = D('Bodyweight C', 'Conditioning circuit',
         ('Star_Jump', 3, '30s', 30), ('Push-Up_Wide', 3, 'max', 45), ('Bodyweight_Squat', 3, '20', 30), ('Mountain_Climbers', 3, '30s', 30),
         ('Side_Bridge', 3, '30s', 30), ('Air_Bike', 3, '30s', 30))
CORE_HOME = D('Core at home', 'Abs · Obliques',
              ('Crunches', 3, '15-20', 30), ('Reverse_Crunch', 3, '12-15', 30), ('Russian_Twist', 3, '20', 30), ('Flat_Bench_Lying_Leg_Raise', 3, '12-15', 30),
              ('Plank', 3, '45s', 30), ('Side_Bridge', 3, '30s', 30), ('Dead_Bug', 3, '12', 30))
MOBILITY = D('Mobility', 'Hips · Spine · Shoulders',
             ('Worlds_Greatest_Stretch', 2, '60s', 0), ('Cat_Stretch', 2, '45s', 0), ('Kneeling_Hip_Flexor', 2, '45s', 0),
             ('Round_The_World_Shoulder_Stretch', 2, '45s', 0), ('Childs_Pose', 2, '60s', 0), ('Inchworm', 2, '45s', 0))
DESK = D('Desk reset', 'Posture · Hips · Upper back',
         ('Face_Pull', 3, '15', 45), ('Butt_Lift_Bridge', 3, '15', 45), ('Superman', 3, '12', 30), ('Kneeling_Hip_Flexor', 2, '45s', 0),
         ('Round_The_World_Shoulder_Stretch', 2, '45s', 0), ('Worlds_Greatest_Stretch', 2, '45s', 0))
SHORT = D('20-minute express', 'Full body in 20',
          ('Goblet_Squat', 3, '12', 30), ('Pushups', 3, 'max', 30), ('Bent_Over_Two-Dumbbell_Row', 3, '12', 30), ('Dumbbell_Lunges', 2, '10', 30),
          ('Mountain_Climbers', 2, '30s', 20))
KB = D('Kettlebell flow', 'Total body',
       ('One-Arm_Kettlebell_Swings', 5, '15', 45, 'm'), ('Goblet_Squat', 4, '12', 60), ('Kettlebell_Thruster', 3, '10', 60),
       ('Kettlebell_Sumo_High_Pull', 3, '12', 45), ('Kettlebell_One-Legged_Deadlift', 3, '8', 45), ('Russian_Twist', 3, '20', 30))
RUN_EZ = D('Easy run', 'Aerobic base', ('Jogging_Treadmill', 1, '30m', 0), ('Worlds_Greatest_Stretch', 2, '45s', 0))
RUN_INT = D('Intervals', 'Speed', ('Jogging_Treadmill', 1, '10m', 0), ('Running_Treadmill', 6, '60s', 90), ('Walking_Treadmill', 1, '10m', 0))
RUN_LONG = D('Long run', 'Endurance', ('Jogging_Treadmill', 1, '45m', 0), ('Kneeling_Hip_Flexor', 2, '45s', 0))
RUN_STR = D('Runner strength', 'Legs · Core',
            ('Goblet_Squat', 3, '10', 75), ('Single_Leg_Glute_Bridge', 3, '12', 45), ('Dumbbell_Step_Ups', 3, '10', 60),
            ('Standing_Calf_Raises', 3, '15', 45), ('Plank', 3, '45s', 30), ('Side_Bridge', 3, '30s', 30))

# ------------------------------------------------------------------ programs
G, DB, HOME, BW, KBQ = 'gym', 'dumbbells', 'home', 'bodyweight', 'kettlebell'
MUS, STR, FAT, TONE, END, ATH, MOB, HEALTH = 'muscle', 'strength', 'fatloss', 'tone', 'endurance', 'athletic', 'mobility', 'health'

prog('ppl6', 'Push Pull Legs', 'The classic 6-day split', 'Each muscle twice a week with two different sessions for push, pull and legs. The go-to plan for serious muscle gain.', 'intermediate', 'all', G, [MUS], 12, 70, 'taj', [PUSH, PULL, LEGS, PUSH2, PULL2, LEGS2], hyp(12))
prog('ppl3', 'Push Pull Legs Lite', 'Three days, every muscle covered', 'The same proven split once a week. Ideal if you can only train three days.', 'beginner', 'all', G, [MUS], 8, 60, 'motu', [PUSH, PULL, LEGS], hyp(8))
prog('ul4', 'Upper / Lower', 'Four days, balanced and efficient', 'Two upper and two lower days. Heavy main lifts first, pump work after.', 'intermediate', 'all', G, [MUS, STR], 10, 65, 'shaheen', [UPPER, LOWER, UPPER2, LOWER2], hyp(10))
prog('fb3', 'Full Body Foundations', 'Your first real gym plan', 'Three full-body sessions a week to learn the big lifts and build a base.', 'beginner', 'all', G, [MUS, STR, HEALTH], 8, 55, 'pip', [FB_A, FB_B, FB_C], hyp(8))
prog('fb2', 'Busy Week Full Body', 'Two sessions that still work', 'Two efficient full-body workouts — enough to keep progressing on a packed schedule.', 'beginner', 'all', G, [MUS, HEALTH], 8, 50, 'kami', [FB_A, FB_B], hyp(8))
prog('golden', 'Golden Era Split', 'Old-school 6-day bodybuilding', 'Chest + back, shoulders + arms and legs — twice a week. High volume for experienced lifters.', 'advanced', 'men', G, [MUS], 8, 80, 'bhoori',
     [d(CHEST, 'Chest + Back A'), d(SHOULDERS, 'Shoulders + Arms A'), d(LEGS_BB, 'Legs A'), d(BACK, 'Chest + Back B'), d(ARMS, 'Shoulders + Arms B'), d(LEGS2, 'Legs B')], hyp(8))
prog('bro5', 'Body Part Split', 'One muscle, one day', 'Chest, back, shoulders, legs and arms on their own days for maximum focus per session.', 'intermediate', 'men', G, [MUS], 8, 60, 'bhalu', [CHEST, BACK, SHOULDERS, LEGS_BB, ARMS], hyp(8))
prog('five5', 'Classic 5×5', 'Get strong on three lifts a day', 'Alternate workout A and B three times a week. Add weight every session — simple, brutal, effective.', 'beginner', 'all', G, [STR], 12, 50, 'motu', [SL_A, SL_B, d(SL_A, 'Workout A (again)')], strn(12))
prog('pl4', 'Powerlifting Peak', 'Squat, bench, deadlift — maxed', 'Four focused days that peak your three competition lifts over a 12-week block.', 'advanced', 'all', G, [STR], 12, 75, 'bhalu', [PL_SQ, PL_BP, PL_DL, PL_OHP], strn(12))
prog('str3', 'Strength Basics', 'Heavy compounds, three days', 'Squat, bench, deadlift and press with smart accessories. Strength first, size follows.', 'intermediate', 'all', G, [STR, MUS], 10, 60, 'yaku', [PL_SQ, PL_BP, PL_DL], strn(10))
prog('powbuild', 'Power Building', 'Strong like a lifter, built like a bodybuilder', 'Heavy main lifts followed by bodybuilding volume, four days a week.', 'intermediate', 'all', G, [STR, MUS], 12, 75, 'kala',
     [d(PL_SQ, 'Squat + Legs'), d(PL_BP, 'Bench + Push'), d(PL_DL, 'Deadlift + Pull'), d(UPPER2, 'Upper pump')], strn(12))
prog('arms6', 'Arm Day Specialist', 'Bigger arms in 6 weeks', 'An upper/lower base with two dedicated arm days.', 'intermediate', 'men', G, [MUS], 6, 60, 'taj', [UPPER, LOWER, ARMS, d(ARMS, 'Arms B')], hyp(6))
prog('shoulders', 'Boulder Shoulders', 'Wider frame, better posture', 'Upper/lower with an extra shoulder day for capped delts and healthy joints.', 'intermediate', 'all', G, [MUS], 8, 60, 'shaheen', [UPPER, LOWER, SHOULDERS, LOWER2], hyp(8))
prog('chestback', 'Chest & Back Builder', 'Thick upper body', 'Focused chest and back days plus a leg day and an upper pump day.', 'intermediate', 'men', G, [MUS], 8, 65, 'bulhan', [CHEST, BACK, LEGS_BB, d(UPPER2, 'Upper pump')], hyp(8))
prog('mass', 'Lean Bulk', 'Add size, stay lean', 'High-volume five-day split built for a small calorie surplus.', 'intermediate', 'men', G, [MUS], 12, 70, 'bhalu', [PUSH, PULL, LEGS, UPPER, LOWER2], hyp(12))
prog('hard', 'Hardgainer Plan', 'For when nothing seems to work', 'Three heavy compound days with lower volume — eat big, sleep well, grow.', 'beginner', 'men', G, [MUS, STR], 12, 55, 'yaku', [SL_A, SL_B, FB_C], strn(12))
prog('beg_men', 'Gym Starter', 'Build the habit and the base', 'Three full-body sessions with the big compound lifts and easy progression.', 'beginner', 'men', G, [MUS, STR], 6, 50, 'motu', [FB_A, FB_B, FB_C], hyp(6))
prog('glute4', 'Glute Builder', 'Shape and strength from the hips', 'Two glute-focused days, an upper day and a full lower day — for a stronger, rounder posterior.', 'intermediate', 'women', G, [MUS, TONE], 10, 60, 'zara', [GLUTE_A, TONE_UP, GLUTE_B, LOWER], hyp(10))
prog('glute3', 'Glutes & Core', 'Three days to a strong base', 'Hip thrusts, hinges and core work. Great for posture and a sculpted lower body.', 'beginner', 'women', G, [TONE, MUS], 8, 50, 'mor', [GLUTE_A, CORE, GLUTE_B], hyp(8))
prog('toned', 'Strong & Toned', 'Lean, defined, confident', 'Full-body strength with moderate reps and short rests. Builds shape without bulk.', 'beginner', 'women', G, [TONE, FAT], 8, 50, 'zara', [TONE_UP, GLUTE_A, FB_C], fat(8))
prog('lowbody', 'Lower Body Sculpt', 'Legs and glutes, four days', 'Alternating quad- and glute-focused sessions, plus one upper day.', 'intermediate', 'women', G, [TONE, MUS], 8, 55, 'monal', [LOWER, GLUTE_A, TONE_UP, GLUTE_B], hyp(8))
prog('ul_women', 'Upper / Lower for Her', 'Strength without the guesswork', 'Four days that balance a strong upper body with glute and leg focus.', 'intermediate', 'women', G, [MUS, TONE], 10, 60, 'zara', [TONE_UP, GLUTE_A, UPPER2, GLUTE_B], hyp(10))
prog('ppl_women', 'PPL Sculpt', 'Push, pull, legs — your way', 'A five-day split with an extra glute day.', 'intermediate', 'women', G, [MUS, TONE], 8, 60, 'monal', [PUSH, PULL, GLUTE_A, d(TONE_UP), GLUTE_B], hyp(8))
prog('beg_women', 'Gym Confidence', 'Your first month, sorted', 'Three simple machine and dumbbell sessions to learn the gym with zero pressure.', 'beginner', 'women', G, [HEALTH, TONE], 4, 45, 'mor', [FB_C, TONE_UP, GLUTE_A], base(4))
prog('shred', 'Shaadi Shred', 'Look sharp for the big day', 'Eight weeks of full-body lifting, conditioning and core. Pair it with a calorie deficit.', 'intermediate', 'all', G, [FAT, TONE], 8, 55, 'mor', [FB_A, HIIT, FB_B, CORE], fat(8))
prog('cut4', 'Lean Cut', 'Keep muscle, lose fat', 'Upper/lower lifting to hold strength plus a conditioning day.', 'intermediate', 'all', G, [FAT, MUS], 8, 60, 'lomri', [UPPER, LOWER, d(HIIT, 'Conditioning'), UPPER2], fat(8))
prog('fatburn3', 'Fat Burn Starter', 'Move more, feel better', 'Circuits and steady cardio to kick-start fat loss without overwhelming you.', 'beginner', 'all', G, [FAT, HEALTH], 6, 45, 'khargosh', [FB_C, CARDIO_Z2, d(HIIT, 'Circuit')], fat(6))
prog('hiit', 'HIIT Engine', 'Short, sharp, sweaty', 'Interval sessions plus core. Builds fitness and burns calories fast.', 'intermediate', 'all', G, [FAT, END], 6, 35, 'chakor', [HIIT, CORE, d(HIIT, 'HIIT B')], fat(6))
prog('recomp', 'Body Recomp', 'Build muscle while losing fat', 'Four hypertrophy days plus a cardio day — for when you want both.', 'intermediate', 'all', G, [MUS, FAT], 12, 60, 'nevla', [UPPER, LOWER, UPPER2, LOWER2, CARDIO_Z2], hyp(12))
prog('db_ul', 'Dumbbell Upper / Lower', 'Just a pair of dumbbells', 'A complete four-day plan with nothing but adjustable dumbbells and a bench.', 'beginner', 'all', DB, [MUS], 8, 50, 'kami',
     [DB_UP, DB_LO, d(DB_UP, 'Dumbbell upper B'), d(DB_LO, 'Dumbbell lower B')], hyp(8))
prog('db_ppl', 'Dumbbell PPL', 'Push, pull, legs at home', 'The classic split adapted for dumbbells.', 'intermediate', 'all', DB, [MUS], 8, 50, 'taj', [DB_PUSH, DB_PULL, DB_LEGS], hyp(8))
prog('db_fb', 'Dumbbell Full Body', 'Three days, one pair of dumbbells', 'Efficient full-body sessions for home or a hotel gym.', 'beginner', 'all', DB, [MUS, HEALTH], 6, 45, 'pip',
     [DB_FB, d(DB_FB, 'Full body B'), d(DB_FB, 'Full body C')], hyp(6))
prog('db_tone', 'Home Tone-Up', 'Dumbbells and a mat', 'Lower-body sculpting and upper-body toning you can do in your room.', 'beginner', 'women', DB, [TONE, FAT], 8, 40, 'mor', [DB_LO, DB_PUSH, DB_FB], fat(8))
prog('bw3', 'Bodyweight Basics', 'No equipment, no excuses', 'Push-ups, squats, rows and core. Three sessions, anywhere.', 'beginner', 'all', BW, [HEALTH, TONE], 6, 35, 'pip', [BW_A, BW_B, BW_C],
     base(6, 'Add 1–2 reps per set each week, or slow the lowering to 3 seconds.'))
prog('bw_shred', 'Bodyweight Shred', 'Sweat at home', 'High-rep bodyweight circuits with minimal rest. Perfect for small spaces.', 'intermediate', 'all', BW, [FAT, END], 6, 30, 'khargosh',
     [BW_C, BW_A, d(BW_C, 'Circuit B'), BW_B], fat(6))
prog('cali', 'Calisthenics Strength', 'Master your bodyweight', 'Pull-ups, dips and push-up progressions for real upper-body strength.', 'intermediate', 'men', BW, [STR, MUS], 10, 45, 'sakeen',
     [D('Pull + core', 'Back · Biceps · Core', ('Pullups', 5, 'max', 120, 'm'), ('Chin-Up', 3, 'max', 90), ('Inverted_Row', 3, '10-12', 60), ('Hanging_Leg_Raise', 3, '10-15', 60)),
      D('Push + legs', 'Chest · Triceps · Legs', ('Dips_-_Triceps_Version', 5, 'max', 120, 'm'), ('Decline_Push-Up', 3, 'max', 75), ('Plyo_Push-up', 3, '6-8', 75),
        ('Split_Squats', 3, '12', 60), ('Bodyweight_Walking_Lunge', 3, '12', 60)),
      BW_B], base(10, 'Add a rep to every set each week. When a move gets easy, slow it down or wear a loaded backpack.'))
prog('home20', '20-Minute Home', 'For the busiest days', 'Four express sessions. Short enough to always fit.', 'beginner', 'all', DB, [HEALTH, FAT], 6, 20, 'chakor',
     [SHORT, CORE_HOME, d(SHORT, '20-minute express B'), BW_C], fat(6))
prog('abs', 'Six-Pack Core', 'Core strength that shows', 'Three core sessions alongside your normal training. Abs are built here and revealed by diet.', 'beginner', 'all', HOME, [TONE], 6, 25, 'lomri',
     [CORE_HOME, CORE, d(CORE_HOME, 'Core at home B')], base(6, 'Add 2–3 reps or 10 seconds to each exercise every week.'))
prog('kb', 'Kettlebell Conditioning', 'One bell, total body', 'Swings, squats and thrusters for strength and stamina together.', 'intermediate', 'all', KBQ, [END, FAT], 6, 35, 'gogi',
     [KB, CORE_HOME, d(KB, 'Kettlebell flow B')], fat(6))
prog('athlete', 'Athletic Performance', 'Faster, more explosive', 'Jumps, sprints and heavy lifts to build speed and power for any sport.', 'advanced', 'all', G, [ATH, STR], 8, 60, 'shaheen',
     [ATH_A, ATH_B, d(ATH_A, 'Power B'), d(CORE, 'Core & mobility')], strn(8))
prog('cricket', 'Cricket Conditioning', 'Bowl faster, run between wickets', 'Rotational power, sprint speed and resilient shoulders for every role in the team.', 'intermediate', 'all', G, [ATH], 8, 55, 'chakor',
     [CRICKET_A, CRICKET_B, d(MOBILITY, 'Recovery & mobility')], hyp(8))
prog('football', 'Football Fitness', 'Ninety minutes of engine', 'Agility, repeat-sprint ability and lower-body strength.', 'intermediate', 'all', G, [ATH, END], 8, 55, 'lomri', [ATH_B, LOWER, RUN_INT], fat(8))
prog('kabaddi', 'Kabaddi & Wrestling Base', 'Grip, hips and grit', 'Strength-endurance for contact sports: carries, hinges, pulls and power.', 'intermediate', 'men', G, [ATH, STR], 8, 60, 'bhoori',
     [d(TREK2, 'Grip & carry'), d(PL_DL, 'Hinge power'), ATH_A], strn(8))
prog('k2', 'K2 Trek Prep', 'Ready for the mountains', 'Step-ups, loaded carries and long walks to prepare for treks in the northern areas.', 'intermediate', 'all', G, [END, ATH], 10, 60, 'sakeen',
     [TREK, TREK2, RUN_LONG, MOBILITY], base(10, 'Add 5 minutes to cardio or 2 kg to carries each week. Week 10 is a lighter taper.'))
prog('run5k', 'Couch to 5K', 'From walking to running', 'Easy runs, intervals and strength to run your first 5K in eight weeks.', 'beginner', 'all', HOME, [END, HEALTH], 8, 35, 'khargosh',
     [RUN_EZ, RUN_STR, RUN_INT], base(8, 'Each week add 5 minutes to easy runs or one more interval — never both in the same week.'))
prog('run10k', '10K Builder', 'Faster and further', 'Intervals, long runs and runner strength for your next 10K.', 'intermediate', 'all', HOME, [END], 10, 45, 'chakor',
     [RUN_INT, RUN_STR, RUN_EZ, RUN_LONG], base(10, 'Long run +5 min each week; every 4th week shorten everything by a third.'))
prog('endur', 'Hybrid Athlete', 'Strong and fit', 'Lifting plus running in one week — strength and endurance without compromise.', 'advanced', 'all', G, [END, STR], 10, 60, 'kala',
     [UPPER, RUN_INT, LOWER, RUN_LONG, d(HIIT, 'Conditioning')], base(10, 'Add weight to lifts and 5 minutes to the long run each week; ease off every fourth week.'))
prog('z2', 'Heart Health Cardio', 'Zone 2, every week', 'Steady cardio plus light strength — the habit that adds years to your life.', 'beginner', 'all', G, [HEALTH, END], 8, 45, 'ullu',
     [CARDIO_Z2, SENIOR, d(CARDIO_Z2, 'Zone 2 cardio B')], base(8, 'Add 5 minutes to each cardio session every other week. You should be able to talk the whole time.'))
prog('ramadan', 'Ramadan Strong', 'Keep your gains while fasting', 'Short sessions for after iftar or before suhoor. Maintenance volume, low fatigue.', 'beginner', 'all', G, [MUS, HEALTH], 4, 40, 'ullu',
     [D('After iftar A', 'Full body · Maintain', ('Barbell_Squat', 3, '6-8', 120, 'm'), ('Barbell_Bench_Press_-_Medium_Grip', 3, '6-8', 120, 'm'),
        ('Seated_Cable_Rows', 3, '8-10', 90), ('Side_Lateral_Raise', 2, '12-15', 45)),
      D('After iftar B', 'Full body · Maintain', ('Romanian_Deadlift', 3, '6-8', 120, 'm'), ('Dumbbell_Shoulder_Press', 3, '8-10', 90),
        ('Wide-Grip_Lat_Pulldown', 3, '8-10', 90), ('Dumbbell_Bicep_Curl', 2, '10-12', 45)),
      d(MOBILITY, 'Light mobility')], base(4, 'Keep weights steady — the goal is to maintain. Hydrate well between iftar and suhoor; skip a session if you feel dizzy.'))
prog('desk', 'Desk Worker Reset', 'Undo the sitting', 'Short posture sessions for tight hips, rounded shoulders and a stiff back.', 'beginner', 'all', HOME, [MOB, HEALTH], 4, 20, 'ullu',
     [DESK, MOBILITY, d(DESK, 'Desk reset B')], base(4))
prog('mobility', 'Daily Mobility', 'Move freely again', 'Gentle stretching flows for hips, spine and shoulders. Great on rest days.', 'beginner', 'all', BW, [MOB], 4, 20, 'sehi',
     [MOBILITY, DESK, d(MOBILITY, 'Mobility B')], base(4, 'Hold each stretch a little longer each week and breathe slowly.'))
prog('senior', 'Strong at 50+', 'Strength for life', 'Gentle strength, balance and mobility to stay independent and active.', 'beginner', 'all', G, [HEALTH, MOB], 8, 40, 'ullu',
     [SENIOR, MOBILITY, d(SENIOR, 'Gentle strength B')], base(8, 'Add a rep or a small weight only when every set feels comfortable.'))
prog('teen', 'First Steps in the Gym', 'Technique before weight', 'Light, technique-first full-body sessions for brand-new lifters.', 'beginner', 'all', G, [HEALTH, MUS], 6, 45, 'pip', [FB_C, FB_A],
     base(6, 'Master the movement first. Add weight only when every rep looks the same.'))
prog('back_health', 'Healthy Back', 'Strong core, happy spine', 'Core stability, hip strength and posture work to support your back.', 'beginner', 'all', G, [HEALTH, MOB], 6, 35, 'sehi',
     [D('Stability', 'Core · Glutes', ('Dead_Bug', 3, '10', 30), ('Butt_Lift_Bridge', 3, '12', 45), ('Side_Bridge', 3, '20s', 30), ('Superman', 3, '10', 30), ('Cat_Stretch', 2, '45s', 0)),
      D('Strength', 'Posterior chain', ('Hyperextensions_Back_Extensions', 3, '10-12', 60), ('Goblet_Squat', 3, '10', 60), ('Seated_Cable_Rows', 3, '10-12', 60),
        ('Plank', 3, '30s', 30), ('Kneeling_Hip_Flexor', 2, '45s', 0)),
      MOBILITY], base(6, 'Progress slowly — reps before weight. Stop any movement that causes sharp pain and see a professional.'))

# ------------------------------------------------------------------ write
assert len({x['id'] for x in P}) == len(P)
for p in P:
    for ph in p['phases']: assert 1 <= ph['w'][0] <= ph['w'][1] <= p['weeks'], (p['id'], ph)
out = os.path.join(ROOT, 'app/src/main/assets/programs.json')
json.dump(P, open(out, 'w'), separators=(',', ':'), ensure_ascii=False)
print(len(P), 'programs', sum(len(x['days']) for x in P), 'days', os.path.getsize(out), 'bytes')
