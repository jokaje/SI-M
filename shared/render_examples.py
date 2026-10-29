"""Host-only PNG/GIF evidence from the actual shared C engine (Pillow)."""
import ctypes as C
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
lib = C.CDLL(str(ROOT / 'liborb_avatar.so'))
U8 = C.c_uint8
U16 = C.c_uint16
class Engine(C.Structure):
    _fields_ = [('dna', U8 * 48), ('ready', C.c_bool)]
class Pose(C.Structure):
    _fields_ = [('mode', C.c_int), ('expression', C.c_int), ('t', C.c_float),
                ('mode_t', C.c_float), ('level', C.c_float), ('blink', C.c_float),
                ('gaze_x', C.c_float), ('gaze_y', C.c_float), ('happy', C.c_float)]
lib.orb_avatar_dna_generate.argtypes = [C.c_uint32, C.POINTER(U8)]
lib.orb_avatar_dna_generate.restype = C.c_bool
lib.orb_avatar_engine_reset.argtypes = [C.POINTER(Engine)]
lib.orb_avatar_engine_set_dna.argtypes = [C.POINTER(Engine), C.POINTER(U8)]
lib.orb_avatar_engine_set_dna.restype = C.c_bool
lib.orb_avatar_engine_render.argtypes = [C.POINTER(Engine), C.POINTER(Pose), C.POINTER(U8), C.POINTER(U8), C.POINTER(U16)]
lib.orb_avatar_engine_render.restype = C.c_bool
names = ['BOOT', 'IDLE', 'LISTENING', 'THINKING', 'SPEAKING', 'ERROR', 'OFF']
seeds = [1, 2, 3, 42, 4092, 987654321]
OUT = ROOT / 'previews'
OUT.mkdir(exist_ok=True)

def render(engine, pose):
    frame, mask, palette = (U8 * 4096)(), (U8 * 4096)(), (U16 * 32)()
    if not lib.orb_avatar_engine_render(C.byref(engine), C.byref(pose), frame, mask, palette):
        raise RuntimeError('C renderer rejected pose')
    im = Image.new('RGB', (64, 64))
    colors = []
    for color in palette:
        colors.append(((color >> 11) * 255 // 31, ((color >> 5) & 63) * 255 // 63, (color & 31) * 255 // 31))
    im.putdata([colors[index] for index in frame])
    return im, hashlib.sha256(bytes(frame) + bytes(palette)).hexdigest()

sheet = Image.new('RGB', (len(names) * 192, len(seeds) * 216), '#101020')
draw = ImageDraw.Draw(sheet)
manifest = {'source': 'liborb_avatar.so compiled from orb_avatar_dna.c + orb_avatar_engine.c', 'frames': []}
for row, seed in enumerate(seeds):
    dna = (U8 * 48)()
    if not lib.orb_avatar_dna_generate(seed, dna): raise RuntimeError(seed)
    engine = Engine()
    lib.orb_avatar_engine_reset(C.byref(engine))
    if not lib.orb_avatar_engine_set_dna(C.byref(engine), dna): raise RuntimeError(seed)
    for col, name in enumerate(names):
        pose = Pose(mode=col, expression=0, t=1.45, mode_t=1.25, level=.7 if col == 4 else 0,
                    blink=0, gaze_x=0, gaze_y=0, happy=0)
        im, digest = render(engine, pose)
        sheet.paste(im.resize((192, 192), Image.Resampling.NEAREST), (col * 192, row * 216 + 20))
        draw.text((col * 192 + 4, row * 216 + 3), f'{seed} {name}', fill='white')
        manifest['frames'].append({'seed': seed, 'mode': name, 'pose_t': 1.45, 'mode_t': 1.25, 'index_palette_sha256': digest})
sheet.save(OUT / 'contact_sheet.png')
frames = []
engine = Engine()
dna = (U8 * 48)()
lib.orb_avatar_dna_generate(42, dna)
lib.orb_avatar_engine_set_dna(C.byref(engine), dna)
for i in range(70):
    t = i * .04
    pose = Pose(mode=4 if i >= 35 else 1, expression=0, t=t, mode_t=t if i < 35 else t - 1.4,
                level=.5 + .5 * __import__('math').sin(t * 12) if i >= 35 else 0,
                blink=1 if 25 <= i <= 27 else 0, gaze_x=0, gaze_y=0, happy=0)
    im, _ = render(engine, pose)
    frames.append(im.resize((256, 256), Image.Resampling.NEAREST))
frames[0].save(OUT / 'seed42_idle_speaking.gif', save_all=True, append_images=frames[1:], duration=40, loop=0, optimize=False)
(OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(f'C frames: {len(manifest["frames"])} stills, {len(frames)} GIF frames; {OUT}')
