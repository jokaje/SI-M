#include <jni.h>
#include <stdint.h>
#include <string.h>
#include "orb_avatar_dna.h"
#include "orb_avatar_engine.h"

JNIEXPORT jbyteArray JNICALL Java_com_example_model_AvatarNative_generate(JNIEnv *env, jobject unused, jint seed) {
    (void)unused;
    uint8_t dna[48];
    if (seed == 0 || !orb_avatar_dna_generate((uint32_t)seed, dna)) return NULL;
    jbyteArray out = (*env)->NewByteArray(env, 48);
    if (out) (*env)->SetByteArrayRegion(env, out, 0, 48, (const jbyte *)dna);
    memset(dna, 0, sizeof(dna));
    return out;
}
JNIEXPORT jbyteArray JNICALL Java_com_example_model_AvatarNative_seal(JNIEnv *env, jobject unused, jbyteArray edited) {
    (void)unused;
    if (!edited || (*env)->GetArrayLength(env, edited) != 48) return NULL;
    uint8_t dna[48];
    (*env)->GetByteArrayRegion(env, edited, 0, 48, (jbyte *)dna);
    if (!orb_avatar_dna_seal(dna) || !orb_avatar_dna_validate(dna)) { memset(dna, 0, sizeof(dna)); return NULL; }
    jbyteArray out = (*env)->NewByteArray(env, 48);
    if (out) (*env)->SetByteArrayRegion(env, out, 0, 48, (const jbyte *)dna);
    memset(dna, 0, sizeof(dna));
    return out;
}
JNIEXPORT jintArray JNICALL Java_com_example_model_AvatarNative_frame(JNIEnv *env, jobject unused, jbyteArray raw, jint mode, jfloat seconds) {
    (void)unused;
    if (!raw || (*env)->GetArrayLength(env, raw) != 48 || mode < 0 || mode > 6 || seconds < 0 || seconds > 1000000) return NULL;
    uint8_t dna[48], frame[4096], mask[4096]; uint16_t palette[32], pixels[4096];
    (*env)->GetByteArrayRegion(env, raw, 0, 48, (jbyte *)dna);
    orb_avatar_engine_t engine;
    orb_avatar_engine_reset(&engine);
    orb_avatar_pose_t pose = {0}; pose.mode = (orb_avatar_mode_t)mode; pose.t = seconds; pose.mode_t = seconds;
    uint32_t seed=(uint32_t)dna[4]|((uint32_t)dna[5]<<8)|
        ((uint32_t)dna[6]<<16)|((uint32_t)dna[7]<<24);
    if (!orb_avatar_eye_animation(seconds, seed, pose.mode, &pose.blink, &pose.gaze_x, &pose.gaze_y) ||
        !orb_avatar_engine_set_dna(&engine, dna) || !orb_avatar_engine_render(&engine, &pose, frame, mask, palette) ||
        !orb_avatar_expand_rgb565(frame, palette, pixels)) return NULL;
    jint rgba[4096];
    for (int i=0; i<4096; ++i) {
        unsigned c = pixels[i];
        rgba[i] = (jint)(0xff000000u | ((c >> 11 & 31u) * 255u / 31u << 16) |
            ((c >> 5 & 63u) * 255u / 63u << 8) | ((c & 31u) * 255u / 31u));
    }
    jintArray out = (*env)->NewIntArray(env, 4096);
    if (out) (*env)->SetIntArrayRegion(env, out, 0, 4096, rgba);
    return out;
}
