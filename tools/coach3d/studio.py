"""Studio lighting + camera for coach renders (transparent background, soft key, rim lights, Khronos PBR Neutral)."""
import bpy, math
from mathutils import Vector


def setup(res=512, samples=32, transparent=True):
    s = bpy.context.scene
    s.render.engine = 'CYCLES'; s.cycles.device = 'CPU'; s.cycles.samples = samples
    s.cycles.use_denoising = True
    try: s.cycles.denoiser = 'OPENIMAGEDENOISE'
    except Exception: pass
    s.render.resolution_x = res; s.render.resolution_y = res; s.render.film_transparent = transparent
    try: s.view_settings.view_transform = 'Khronos PBR Neutral'
    except Exception: s.view_settings.view_transform = 'Standard'
    w = bpy.data.worlds.new('w'); s.world = w; w.use_nodes = True
    bg = w.node_tree.nodes['Background']; bg.inputs[0].default_value = (0.9, 0.92, 0.95, 1); bg.inputs[1].default_value = 0.45
    def light(name, kind, loc, energy, color=(1, 1, 1), size=1.0):
        L = bpy.data.lights.new(name, kind); L.energy = energy; L.color = color
        if kind == 'AREA': L.size = size
        o = bpy.data.objects.new(name, L); s.collection.objects.link(o); o.location = loc
        d = Vector((0, 0, 1.0)) - Vector(loc); o.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
        return o
    light('key', 'AREA', (-2.2, -3.0, 3.0), 650, (1, 0.97, 0.93), 2.5)
    light('fill', 'AREA', (2.6, -2.4, 1.6), 220, (0.92, 0.95, 1), 3.0)
    light('rimL', 'AREA', (-2.0, 2.6, 2.4), 380, (1, 1, 1), 1.5)
    light('rimR', 'AREA', (2.2, 2.4, 2.2), 380, (1, 1, 1), 1.5)
    return s


def camera(target=(0, 0, 0.92), dist=4.2, yaw=-18, pitch=4, lens=60, frame_h=None):
    s = bpy.context.scene
    cam = s.camera
    if cam is None:
        cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam')); s.collection.objects.link(cam); s.camera = cam
    cam.data.lens = lens
    t = Vector(target)
    y, p = math.radians(yaw), math.radians(pitch)
    cam.location = t + Vector((math.sin(y) * math.cos(p) * dist, -math.cos(y) * math.cos(p) * dist, math.sin(p) * dist))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()
    return cam


def render(path):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
