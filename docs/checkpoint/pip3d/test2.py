import sys; sys.path.insert(0, '.')
import pipgen as P, math
P.build()
O='/tmp/claude-0/-home-claude-myfit-tracker/a3bd9ddc-9b2d-5774-bc12-e1c6a3a44cb1/scratchpad/pip3d/'
P.pose({'armR': 130, 'fwdR': 20, 'mouth': 'open', 'tilt': -6, 'yaw': 10})
P.render(O+'a.png')
P.pose({'eyes': 'happy', 'mouth': 'open', 'armL': 150, 'armR': 150, 'hop': 0.25, 'blush': 1.0})
P.render(O+'b.png')
cam=P.OBJ['Cam']; cam.location=(0,-5.2,1.75); cam.rotation_euler=(math.radians(90),0,0)
P.pose({'mouth': 'open'})
P.render(O+'c.png')
