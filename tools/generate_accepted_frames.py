#!/usr/bin/env python3
"""Regenerate/verify the exact accepted C 64x64 RGB565 preview frames."""
import hashlib
import json
import pathlib
import argparse
import subprocess
import sys
import tempfile

ANDROID = pathlib.Path(__file__).resolve().parents[1]
OUT = ANDROID / 'app/src/main/assets/avatars'
IDS = {'flame': 'flame', 'fox_spirit': 'fox', 'crystal_dragon': 'dragon',
       'celestial_owl': 'owl', 'starlight_axolotl': 'axolotl'}
def sources(muse):
    result = [muse / ('avatar/muse_pixel_' + kind + '.c') for kind in IDS.values()]
    result += [muse / 'avatar/orb_render_scratch.c', muse / 'orb_character.c']
    return result
CHECK = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()

def run(accepted, verify=False):
    accepted = accepted.resolve()
    muse = accepted / "esp32/components/muse"
    source_files = sources(muse)
    with tempfile.TemporaryDirectory() as tmp:
        binary = pathlib.Path(tmp) / 'accepted_frames'
        cmd = ['cc', '-std=c11', '-O1', '-g', '-Wall', '-Wextra', '-Werror',
               '-fsanitize=address,undefined', '-fno-sanitize-recover=all', '-DNDEBUG',
               '-I' + str(muse), str(ANDROID / 'tools/accepted_frames.c'),
               *map(str, source_files), '-lm', '-o', str(binary)]
        subprocess.run(cmd, check=True)
        data = {}
        for name in IDS:
            raw = subprocess.run([str(binary), name], check=True, capture_output=True).stdout
            if len(raw) != 12 * 64 * 64 * 2:
                raise RuntimeError(f'{name}: invalid output size {len(raw)}')
            data[name] = raw
    manifest = {'format': 'RGB565LE', 'width': 64, 'height': 64,
                'modes': ['idle', 'listening', 'speaking'], 'frames_per_mode': 4,
                'source': 'external accepted firmware C sources (supply --accepted-root)', 'source_sha256': {str(p.relative_to(accepted)): CHECK(p) for p in source_files},
                'driver_sha256': CHECK(ANDROID / 'tools/accepted_frames.c'),
                'characters': {name: {'file': name + '.rgb565', 'sha256': hashlib.sha256(raw).hexdigest(),
                                       'frame_sha256': [hashlib.sha256(raw[i*8192:(i+1)*8192]).hexdigest() for i in range(12)]}
                               for name, raw in data.items()}}
    if verify:
        saved = json.loads((OUT / 'manifest.json').read_text())
        if saved != manifest:
            raise RuntimeError('Manifest differs from accepted renderer reproduction')
        for name, raw in data.items():
            if (OUT / (name + '.rgb565')).read_bytes() != raw:
                raise RuntimeError(name + ': C frame parity mismatch')
    else:
        OUT.mkdir(parents=True, exist_ok=True)
        for name, raw in data.items():
            (OUT / (name + '.rgb565')).write_bytes(raw)
        (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n')
    print(('Verified' if verify else 'Generated'), len(data), 'accepted renderers,', sum(len(x) for x in data.values()), 'bytes')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--accepted-root', type=pathlib.Path, required=True, help='Local checkout containing esp32/components/muse')
    parser.add_argument('--verify', action='store_true')
    args = parser.parse_args()
    run(args.accepted_root, args.verify)
