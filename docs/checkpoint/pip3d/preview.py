import sys; sys.path.insert(0, '.')
import pipgen as P, anims as A
P.build()
P.S.cycles.samples = 10
P.S.render.resolution_x = P.S.render.resolution_y = 320
O = '/tmp/claude-0/-home-claude-myfit-tracker/a3bd9ddc-9b2d-5774-bc12-e1c6a3a44cb1/scratchpad/pip3d/prev/'
import os; os.makedirs(O, exist_ok=True)
names = sys.argv[1:] or list(A.ANIMS)
for n in names:
    fn, fr = A.ANIMS[n]
    u = float(os.environ.get('U', '0.45'))
    P.pose(fn(u)); P.render(O + n + '.png')
