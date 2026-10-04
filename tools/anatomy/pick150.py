"""Pick the ~150 most common catalog exercises and map each to a 3D motion pattern."""
import json, re, sys
d = json.load(open(sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/assets/exercise_catalog.json'))
# (pattern, regex on name, quota) — order matters (first match wins)
RULES = [
    ('skullcrusher', r'lying triceps press|skull ?crusher|ez-bar skullcrusher', 3),
    ('pushdown', r'triceps pushdown|tricep(s)? pushdown|rope pushdown', 4),
    ('oh_ext', r'overhead triceps|triceps extension|tricep dumbbell kickback|kickback', 5),
    ('dip', r'^dips|dip(s)? -|bench dips', 3),
    ('curl', r'curl(?!.*leg)', 12),
    ('legcurl', r'leg curl', 3),
    ('legext', r'leg extension', 2),
    ('legpress', r'leg press', 3),
    ('calf', r'calf raise|calf press', 5),
    ('hipthrust', r'hip thrust|glute bridge|butt lift|bridge', 4),
    ('rdl', r'romanian|stiff[- ]leg|good morning', 5),
    ('deadlift', r'deadlift', 5),
    ('lunge', r'lunge|split squat|step[- ]?up', 9),
    ('squat', r'squat', 12),
    ('bench', r'bench press|chest press|floor press', 9),
    ('fly', r'fly|flye|crossover|pec deck', 7),
    ('pushup', r'push-?up', 6),
    ('ohp', r'military press|shoulder press|overhead press|arnold|push press|seated barbell press', 8),
    ('lateral', r'lateral raise|side lateral', 4),
    ('frontraise', r'front (dumbbell |plate |barbell |cable )?raise', 3),
    ('reardelt', r'rear delt|reverse fly|face pull', 4),
    ('shrug', r'shrug', 3),
    ('uprightrow', r'upright row', 2),
    ('pulldown', r'pulldown|pull-?down', 5),
    ('pullup', r'pull-?up|chin-?up', 5),
    ('row', r'row', 10),
    ('swing', r'kettlebell swing|swing', 2),
    ('crunch', r'crunch|sit-?up', 7),
    ('legraise', r'leg raise|knee raise|leg lift', 4),
    ('plank', r'plank', 3),
    ('twist', r'russian twist|wood ?chop|oblique', 3),
    ('climber', r'mountain climber', 1),
    ('jacks', r'jumping jack|star jump', 1),
    ('burpee', r'burpee', 1),
    ('hyper', r'hyperextension|back extension|superman', 3),
]
out = []; used = set()
for pat, rx, q in RULES:
    c = [x for x in d if x['k'] not in used and re.search(rx, x['n'], re.I) and x['e'] in ('barbell', 'dumbbell', 'cable', 'body only', 'machine', 'kettlebells', 'e-z curl bar', 'other')]
    c.sort(key=lambda x: (x['l'] != 'beginner', len(x['n'])))
    for x in c[:q]: out.append((x['k'], pat, x['e'], x['n'])); used.add(x['k'])
if __name__ == '__main__':
    for k, p, e, n in out: print(p, '|', e, '|', n, '|', k)
    print(len(out), file=sys.stderr)
