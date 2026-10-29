"""Exercise production JNI through a real host JVM and compare shared-C oracle."""
import base64
import ctypes as C
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ANDROID = Path(__file__).resolve().parents[1]
SHARED = ANDROID.parent / 'shared'

class Pose(C.Structure):
    _fields_ = [('mode', C.c_int), ('expression', C.c_int), ('t', C.c_float),
                ('mode_t', C.c_float), ('level', C.c_float), ('blink', C.c_float),
                ('gaze_x', C.c_float), ('gaze_y', C.c_float), ('happy', C.c_float)]

class Engine(C.Structure):
    _fields_ = [('dna', C.c_uint8 * 48), ('ready', C.c_bool)]

class JniBoundaryTest(unittest.TestCase):
    def test_real_jni_blink_reopen_and_gaze(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            javac = Path(subprocess.check_output(['which', 'javac'], text=True).strip()).resolve()
            jdk = javac.parents[1]
            sources = [str(SHARED / x) for x in ('orb_avatar_dna.c', 'orb_avatar_engine.c')]
            lib = root / 'libsim_avatar.so'
            jni = ANDROID/'app/src/main/cpp/avatar_jni.c'
            if os.environ.get('AVATAR_JNI_RED') == '1':
                old = root/'avatar_jni_old.c'
                source = jni.read_text()
                start = source.index('    uint32_t seed=(uint32_t)dna[4]')
                end = source.index('        !orb_avatar_engine_set_dna(&engine, dna)', start)
                old.write_text(source[:start] + '    if (' + source[end:])
                jni = old
            subprocess.run(['cc', '-std=c11', '-O1', '-Wall', '-Wextra', '-Werror', '-fPIC',
                '-shared', '-I'+str(jdk/'include'), '-I'+str(jdk/'include/linux'),
                '-I'+str(SHARED), str(jni),
                *sources, '-lm', '-o', str(lib)], check=True)
            java = root / 'AvatarNative.java'
            java.write_text('''package com.example.model;
import java.util.Base64;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
public class AvatarNative {
  static { System.loadLibrary("sim_avatar"); }
  public native byte[] generate(int seed);
  public native int[] frame(byte[] dna, int mode, float seconds);
  public static void main(String[] args) {
    AvatarNative a=new AvatarNative(); byte[] dna=a.generate(11);
    if(dna==null) throw new AssertionError("generation");
    for(float t : new float[]{1f,2.12f,2.5f}) {
      int[] rgba=a.frame(dna,1,t);
      if(rgba==null || rgba.length!=4096) throw new AssertionError("frame");
      ByteBuffer b=ByteBuffer.allocate(4096*4).order(ByteOrder.LITTLE_ENDIAN);
      for(int v:rgba) b.putInt(v);
      System.out.println(Base64.getEncoder().encodeToString(b.array()));
    }
  }
}''')
            subprocess.run(['javac', '-d', str(root), str(java)], check=True)
            result = subprocess.check_output(['java', '-Djava.library.path='+str(root),
                '-cp', str(root), 'com.example.model.AvatarNative'], text=True)
            actual = [base64.b64decode(line) for line in result.splitlines()]
            self.assertEqual(len(actual), 3)
            oracle = C.CDLL(str(lib))
            dna = (C.c_uint8 * 48)()
            oracle.orb_avatar_dna_generate.argtypes = [C.c_uint32, C.c_void_p]
            self.assertTrue(oracle.orb_avatar_dna_generate(11, dna))
            engine = Engine()
            oracle.orb_avatar_engine_reset.argtypes = [C.POINTER(Engine)]
            oracle.orb_avatar_engine_set_dna.argtypes = [C.POINTER(Engine), C.c_void_p]
            oracle.orb_avatar_engine_render.argtypes = [C.POINTER(Engine), C.POINTER(Pose), C.c_void_p, C.c_void_p, C.c_void_p]
            oracle.orb_avatar_expand_rgb565.argtypes = [C.c_void_p, C.c_void_p, C.c_void_p]
            oracle.orb_avatar_engine_reset(C.byref(engine))
            self.assertTrue(oracle.orb_avatar_engine_set_dna(C.byref(engine), dna))
            expected = []
            for t, blink, gaze in ((1., 0., 0.), (2.12, 1., 0.), (2.5, 0., 1.)):
                pose = Pose(1, 0, t, t, 0, blink, gaze, 0, 0)
                frame = (C.c_uint8 * 4096)()
                mask = (C.c_uint8 * 4096)()
                pal = (C.c_uint16 * 32)()
                pixels = (C.c_uint16 * 4096)()
                self.assertTrue(oracle.orb_avatar_engine_render(C.byref(engine), C.byref(pose), frame, mask, pal))
                self.assertTrue(oracle.orb_avatar_expand_rgb565(frame, pal, pixels))
                rgba = bytearray()
                for v in pixels:
                    red = ((v >> 11) & 31) * 255 // 31
                    green = ((v >> 5) & 63) * 255 // 63
                    blue = (v & 31) * 255 // 31
                    rgba += (0xff000000 | red << 16 | green << 8 | blue).to_bytes(4, 'little')
                expected.append(bytes(rgba))
            self.assertEqual(actual, expected)
            self.assertNotEqual(actual[0], actual[1])
            self.assertNotEqual(actual[0], actual[2])

if __name__ == '__main__':
    unittest.main()
