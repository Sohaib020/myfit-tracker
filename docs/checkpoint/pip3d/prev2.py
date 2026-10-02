import sys, os; sys.path.insert(0, '.')
import pipgen as P, anims as A
P.build(); P.S.cycles.samples = 10; P.S.render.resolution_x = P.S.render.resolution_y = 300
O = 'prev2/'; os.makedirs(O, exist_ok=True)
jobs = [('hydrate', 0.56), ('hydrate', 0.45), ('jumpingjacks', 0.17), ('jog', 0.08), ('squat', 0.25), ('stretch', 0.5), ('clap', 0.5), ('shrug', 0.5),
        ('shy', 0.5), ('sad', 0.5), ('pout', 0.5), ('yawn', 0.4), ('blowkiss', 0.35), ('point', 0.5), ('look_00', None), ('look_22', None)]
for n, u in jobs:
    pz = A.LOOKS[n] if u is None else A.ANIMS[n][0](u)
    P.pose(pz); P.render(O + '%s_%s.png' % (n, u))
