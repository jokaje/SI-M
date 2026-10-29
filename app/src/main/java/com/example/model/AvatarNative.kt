package com.example.model

/** The shared C11 engine is the sole generator and 64×64 renderer. */
object AvatarNative {
    init { System.loadLibrary("sim_avatar") }
    external fun generate(seed: Int): ByteArray?
    external fun seal(edited: ByteArray): ByteArray?
    external fun frame(dna: ByteArray, mode: Int, seconds: Float): IntArray?
}
