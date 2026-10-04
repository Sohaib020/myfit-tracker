"""CI diagnostics for the 3D loops: evaluated mesh bounds per pose + small stills (env OUT, ONLY)."""
import sys, os, json, platform
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mh as M, pose as P, moves as MV, loops as L, bpy, numpy
from mathutils import Vector
OUT = os.environ.get('OUT', 'diag') + '/'; os.makedirs(OUT, exist_ok=True)
log = open(OUT + 'diag.txt', 'w')
def w(*a): s = ' '.join(str(x) for x in a); print(s, flush=True); log.write(s + '\n'); log.flush()
w('python', platform.python_version(), 'numpy', numpy.__version__, 'bpy', bpy.app.version_string, 'cpu', platform.processor(), os.cpu_count())
M.build((160, 160), 4); P.init(); P.props_init()
def bounds():
    dg = bpy.context.evaluated_depsgraph_get(); o = M.OBJ['Body'].evaluated_get(dg); me = o.to_mesh()
    import numpy as np
    co = np.empty(len(me.vertices) * 3); me.vertices.foreach_get('co', co); co = co.reshape(-1, 3)
    mw = np.array(M.OBJ['Body'].matrix_world); co = co @ mw[:3, :3].T + mw[:3, 3]
    o.to_mesh_clear(); return co.min(0).round(2).tolist(), co.max(0).round(2).tolist(), bool(np.isnan(co).any())
w('rest', bounds())
for k in os.environ.get('ONLY', 'Barbell_Curl,Bodyweight_Squat,Air_Bike,Barbell_Bench_Press_-_Medium_Grip').split(','):
    mv = MV.MOVES[k]; L.set_camera(mv.get('view', {}))
    for t in (0.0, 0.5, 1.0):
        P.apply(L.lerp_pose(mv['A'], mv['B'], t)); P.place_props(mv['props'])
        arm = M.OBJ['Rig']
        bad = [pb.name for pb in arm.pose.bones if abs(pb.matrix.to_3x3().determinant() - 1) > 0.05 or any(abs(x) > 50 for x in pb.matrix.translation)]
        w(k, t, 'bounds', bounds(), 'badbones', bad[:8], 'rig', [round(x, 2) for x in arm.matrix_world.translation])
        M.render(OUT + '%s_%d.png' % (k, int(t * 10)))
w('cam', [round(x, 2) for x in M.OBJ['Cam'].location])
