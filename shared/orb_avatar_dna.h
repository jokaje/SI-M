#ifndef ORB_AVATAR_DNA_H
#define ORB_AVATAR_DNA_H
#include <stdbool.h>
#include <stdint.h>
#define ORB_AVATAR_DNA_BYTES 48
#define ORB_AVATAR_DNA_HEX_CHARS 96
/* AVD1, nonzero LE seed, eight traits at 8..15, RGB888 palette at 16..30,
 * five quantized proportions at 31..35, reserved zero at 36..43,
 * LE CRC32/IEEE over 0..43 at 44..47. See parent CONTRACT.md. */
bool orb_avatar_dna_validate(const uint8_t dna[48]);
bool orb_avatar_dna_generate(uint32_t seed, uint8_t out[48]);
bool orb_avatar_dna_from_hex(const char *hex, uint8_t out[48]);
bool orb_avatar_dna_to_hex(const uint8_t dna[48], char out[97]);
/* Recompute LE CRC after an intentional edit to a valid DNA. Returns false
 * for NULL; validation of edited trait ranges remains caller's obligation. */
bool orb_avatar_dna_seal(uint8_t dna[48]);
#endif
