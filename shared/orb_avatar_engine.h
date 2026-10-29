#ifndef ORB_AVATAR_ENGINE_H
#define ORB_AVATAR_ENGINE_H
#include <stdbool.h>
#include <stdint.h>
#include "orb_avatar_dna.h"
#define ORB_AVATAR_W 64
#define ORB_AVATAR_H 64
#define ORB_AVATAR_COLORS 32
/* The seven existing operational modes, in firmware muse_mode_t order. */
typedef enum { ORB_AVATAR_BOOT=0, ORB_AVATAR_IDLE=1,
    ORB_AVATAR_LISTENING=2, ORB_AVATAR_THINKING=3,
    ORB_AVATAR_SPEAKING=4, ORB_AVATAR_ERROR=5, ORB_AVATAR_OFF=6 } orb_avatar_mode_t;
/* Optional expressions are separate from operational mode. */
typedef enum { ORB_AVATAR_NEUTRAL=0, ORB_AVATAR_HAPPY=1,
    ORB_AVATAR_SLEEPY=2, ORB_AVATAR_LOVE=3,
    ORB_AVATAR_ANGRY=4, ORB_AVATAR_SURPRISED=5 } orb_avatar_expression_t;
typedef struct {
    orb_avatar_mode_t mode;
    orb_avatar_expression_t expression;
    float t;       /* absolute monotonic seconds since renderer reset, >= 0 */
    float mode_t;  /* seconds since mode transition, >= 0 */
    float level;   /* actual input level [0,1], never synthesized */
    float blink;   /* eye closure [0,1], explicitly supplied by caller */
    float gaze_x;  /* normalized [-1,1] */
    float gaze_y;  /* normalized [-1,1] */
    float happy;   /* pet reaction envelope [0,1], not a mode */
} orb_avatar_pose_t;
/* State is caller-owned; no framebuffer, mask, or LUT globals. No heap.
 * Prefer binding frame/mask to UI's existing shared scratch (4096 each).
 * Palette is caller-owned 32 RGB565 entries; frame is 4096 indices 0..31.
 * CPU-only Android may convert with orb_avatar_expand_rgb565(). */
typedef struct {
    uint8_t dna[48];
    bool ready;
} orb_avatar_engine_t;
/* Shared eye policy for production callers. Stateless; time is monotonic
 * seconds since renderer start, seed is DNA bytes 4..7 (little endian).
 * OFF/ERROR/BOOT suppress autonomous eye motion; mode remains authoritative.
 * Valid finite time [0,1000000] and seven modes required. */
bool orb_avatar_eye_animation(float seconds, uint32_t seed, orb_avatar_mode_t mode,
    float *blink, float *gaze_x, float *gaze_y);
void orb_avatar_engine_reset(orb_avatar_engine_t *engine);
bool orb_avatar_engine_set_dna(orb_avatar_engine_t *engine, const uint8_t dna[48]);
/* Returns false on invalid state/pose/NULL; on success overwrites exactly
 * frame[4096], mask[4096], palette[32]. Buffers must not overlap. */
bool orb_avatar_engine_render(const orb_avatar_engine_t *engine,
    const orb_avatar_pose_t *pose, uint8_t frame[4096], uint8_t mask[4096],
    uint16_t palette[32]);
/* Exactly 4096 RGB565 words, host-endian words, from indexed frame/palette. */
bool orb_avatar_expand_rgb565(const uint8_t frame[4096],
    const uint16_t palette[32], uint16_t out[4096]);
/* Bounds-checked nearest-neighbor strip conversion, inclusive destination
 * rectangle [x0,x1] × [y0,y1] in a square size<=512 canvas. dst points to
 * rectangle's top left, stride >= width. No per-renderer scale map or copy. */
bool orb_avatar_scale_strip(const uint8_t frame[4096], const uint16_t palette[32],
    uint16_t *dst, int stride, int size, int x0, int x1, int y0, int y1);
#endif
