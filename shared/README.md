# Shared Orb Avatar DNA v1

Portable C11 runtime renderer derived from the uploaded browser creator's procedural body, trait silhouettes, Bayer/outline palette style and facial animations, **not** the browser's fixed-`#define` C export. The C implementation is the single renderer for firmware and JNI Android previews. No device, network, or global mutable frame state.

## Build and run

```sh
cd shared
cc -std=c11 -O1 -g -Wall -Wextra -Werror -fsanitize=address,undefined -fno-sanitize-recover=all test_avatar.c orb_avatar_dna.c orb_avatar_engine.c -lm -o ./avatar_test
ASAN_OPTIONS=halt_on_error=1 UBSAN_OPTIONS=halt_on_error=1 ./avatar_test
cc -std=c11 -O2 -Wall -Wextra -Werror -fPIC -shared orb_avatar_dna.c orb_avatar_engine.c -lm -o liborb_avatar.so
python3 render_examples.py  # Pillow; host-only PNG/GIF evidence
```

Firmware can compile the same two C sources and bind `orb_avatar_engine_render()` to its existing UI-owned 4096-byte indexed frame and 4096-byte mask; pass a caller-owned 32-word RGB565 palette and use its own scaling or `orb_avatar_scale_strip()`. The engine object is 49 bytes on the tested host (48 DNA bytes + ready flag). Caller serializes selection, render and strip writes. Android JNI can allocate matching frame/mask/palette then expand to RGB565, or call the bounded strip converter; source code and DNA do not diverge. Palette RGB565 words are native-endian; BLE hex remains byte-canonical lowercase.

A pose specifies absolute seconds, time in operational mode, audio level, blink, gaze and happy envelope. The renderer is stateless per frame for reproducible previews and avoids hidden PRNG/history. Operational modes 0..6 correspond exactly to existing firmware states. Optional expressions 0..5 are neutral, happy, sleepy, love, angry, surprised; only idle/thinking honor optional expression, and boot/error/off/speech/listening take precedence. Expressions are local renderer requests, not sentiment inference. Outputs are clipped to 64×64.

Generator uses xorshift32 (13,17,5) from nonzero seed. A first draw chooses one of eight coherent silhouette families: fox, dragon, axolotl, owl, flame, lop, antenna/fairy, bare/fairy. Crown, eye, particle, palette and proportions draw separately; all eight palette sets come directly from the upload. This reduces random mixed-species accidents while leaving all validated manual trait edits renderable (no hidden coupling in validation). Seed reproduces initial 48 bytes; manually edited DNA remains authoritative, and `orb_avatar_dna_seal` recomputes its CRC. Quantized proportions decode via the exact parent contract formulas. The full 48 bytes are validated including zero reserved and CRC32/IEEE. `from_hex` requires exactly 96 lowercase digits and does not alter output on error.

`previews/contact_sheet.png` is six seeds × seven modes; `previews/seed42_idle_speaking.gif` is 70 frames at 25 fps, from the actual compiled shared C library. These are host-rendered, not physical-display acceptance. The browser's generated C and JS have documented divergences; this engine is an adaptation, not pixel-identical to either. The five accepted characters are intentionally not represented by generated DNA and must retain their actual separate C renderers and preview assets.
