#!/usr/bin/env python3
"""Capture real queued-worker status bytes without changing firmware source."""
import hashlib
import json
import pathlib
import shutil
import subprocess
import tempfile

ANDROID = pathlib.Path(__file__).resolve().parents[1]
ROOT = ANDROID.parent / 'firmware/source/esp32'
MUSE = ROOT / 'components/muse'
CJSON = ROOT / 'managed_components/espressif__cjson/cJSON'
SHARED = ANDROID.parent / 'shared'
OUT = ANDROID / 'app/src/test/resources/firmware-avatar-replies.json'
FAKE = ROOT / 'tests/orb_control/adapter_fake'
NAMES = ('orb_ble.h','orb_character.h','orb_avatar_storage.h','muse_settings.h','muse_wifi.h',
         'muse_link.h','muse_state.h','muse_ble.h','esp_err.h','esp_random.h','esp_nimble_hci.h',
         'nimble/nimble_port.h','host/ble_hs.h','host/ble_gap.h','host/ble_gatt.h','host/ble_sm.h',
         'services/gap/ble_svc_gap.h','services/gatt/ble_svc_gatt.h','freertos/FreeRTOS.h',
         'freertos/queue.h','freertos/task.h')
# Match specific lines by source substring, insert only at unique targets.
def capture():
    with tempfile.TemporaryDirectory() as tmp:
        folder = pathlib.Path(tmp)
        shutil.copyfile(MUSE / 'orb_ble.c', folder / 'orb_ble.c')
        for name in ('orb_protocol.h','orb_window.h','orb_agent.h'):
            shutil.copyfile(MUSE / name, folder / name)
        for name in NAMES:
            path = folder / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('#include "common.h"\n')
        shutil.copyfile(FAKE / 'common.h', folder / 'common.h')
        harness = ROOT / 'tests/orb_control/adapter_harness.c'
        lines = harness.read_text().splitlines(keepends=True)
        inserts = {272:'caps',283:'set',298:'get',301:'character',304:'delete',307:'empty'}
        for n, label in inserts.items():
            if 'CHECK(' not in lines[n-1] and 'strstr(reply' not in lines[n-1]:
                raise RuntimeError('Harness drift near capture ' + label)
        body = ''.join(line + (f' fprintf(stderr,"FIXTURE:{inserts[n]}:%s\\n",reply);\n' if n in inserts else '')
                       for n,line in enumerate(lines,1))
        (folder / 'adapter_harness.c').write_text(body)
        exe = folder / 'harness'
        subprocess.run(['cc','-std=c11','-Wall','-Wextra','-Werror','-Wno-unused-const-variable',
                        '-fsanitize=address,undefined','-fno-omit-frame-pointer','-DNDEBUG','-pthread',
                        '-I'+str(folder),'-I'+str(CJSON),'-I'+str(SHARED),
                        str(folder/'adapter_harness.c'),str(MUSE/'orb_window.c'),
                        str(MUSE/'orb_protocol.c'),str(MUSE/'orb_agent_validation.c'),
                        str(SHARED/'orb_avatar_dna.c'),str(CJSON/'cJSON.c'),'-lm','-o',str(exe)],check=True)
        result = subprocess.run([str(exe)],capture_output=True,text=True,check=True,timeout=20)
        fixtures = dict(line.removeprefix('FIXTURE:').split(':',1) for line in result.stderr.splitlines()
                        if line.startswith('FIXTURE:'))
        if set(fixtures) != set(inserts.values()):
            raise RuntimeError('Missing real worker replies: '+str(fixtures.keys()))
        return {'provenance': {'harness_sha256': hashlib.sha256(harness.read_bytes()).hexdigest(),
                               'ble_sha256': hashlib.sha256((MUSE/'orb_ble.c').read_bytes()).hexdigest(),
                               'parser_sha256': hashlib.sha256((MUSE/'orb_protocol.c').read_bytes()).hexdigest()},
                'replies': fixtures}
if __name__ == '__main__':
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_text(json.dumps(capture(),sort_keys=True,indent=2)+'\n')
    print('Captured',OUT)
