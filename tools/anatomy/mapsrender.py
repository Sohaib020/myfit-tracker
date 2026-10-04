"""Muscle-map assets: front/back base bodies + a transparent red overlay per muscle group per view."""
import sys, os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mh as M, bpy
from mathutils import Vector
OUT = sys.argv[sys.argv.index('--') + 1] if '--' in sys.argv else os.environ.get('OUT', 'maps')
os.makedirs(OUT, exist_ok=True)
o, arm = M.build((600, 1000), int(os.environ.get('SAMPLES', '32')))
bpy.context.view_layer.update()
bb = [o.matrix_world @ Vector(c) for c in o.bound_box]; zs = [v.z for v in bb]
cz = (min(zs) + max(zs)) / 2; M.OBJ['Cam'].data.ortho_scale = (max(zs) - min(zs)) * 1.04
for view in ('front', 'back'):
    M.set_view(view == 'back', cz)
    if not os.environ.get('ONLY'): M.paint([]); M.render(f'{OUT}/{view}.png')
    for g in [x for x in M.GROUPS if not os.environ.get('ONLY') or x in os.environ['ONLY'].split(',')]:
        M.paint([g], holdout_rest=True); M.render(f'{OUT}/{view}_{g.replace(" ", "_")}.png')
print('MAPSDONE')
