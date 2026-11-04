"""Import a GLB, anime cel look, render 4 views. usage: blender -b -P turntable.py -- in.glb out_prefix"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import toon
a = sys.argv[sys.argv.index("--") + 1:]
ob = toon.load(a[0]); toon.toonify(ob); cam = toon.scene()
for i, yaw in enumerate((0, 90, 180, 270)): toon.shoot(cam, f"{a[1]}_{i}.png", yaw)
print("TURN done")
