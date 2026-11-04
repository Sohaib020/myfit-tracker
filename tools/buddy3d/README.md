# Champions (buddies) — 3D pipeline

Owner's Meshy models (15 champions × Street / Epic / Legendary; TITAN Epic and SABA Legendary still to come) are
unrigged A-pose meshes. This folder turns them into champions that play every coach move.

1. `front_render.py` — orthographic front render of each model + camera metadata (`meta.json`).
2. `detect.py` — MediaPipe Pose (bundled model) on those renders → 2D joints converted to world x/z
   (`landmarks.json`). Spotted jaguars failed on the textured render and were detected on a plain grey one.
3. `autorig.py` — fits the shared coach skeleton (tools/coach3d/rig.py) to each mesh: joint depth from the body
   cross-section, shoulders pushed out to the deltoid, fingers/toes/head/spine from geometry, 4-bone tail for the
   animals; welds Meshy's UV-seam splits first (otherwise the skin tears), bone-heat weights, smoothed, nearest-bone
   fallback for any left-over vertices.
4. `load.py` — loads a rigged champion for the coach pipeline (clips, mocap, GLB export).

`roster.json` maps each Meshy file to `<champion>_<tier>` (matched by signature colour/outfit; tiers by trims).
