"""Re-render a champion portrait framed from the top of the model (ears/horns included) down to the chest.
  python portrait.py <rigged.glb> <out.png>"""
import sys, os, bpy
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'coach3d')); sys.path.insert(0, HERE)
import load, studio, look
glb, out = sys.argv[-2:]
arm, body = load.load_rigged(glb)
look.refine(body, subdiv=0)
s = studio.setup(res=512, samples=24)
mw = body.matrix_world
top = max((mw @ v.co).z for v in body.data.vertices)
H = top
span = 0.47 * H                                       # top of head .. upper chest
D = span / 0.514                                      # 70 mm lens, square frame
studio.camera(target=(0, 0, top - span * 0.40), dist=D, yaw=-12, pitch=2, lens=70)
studio.render(out)
print('PORTRAIT_DONE')
