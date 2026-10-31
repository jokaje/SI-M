package com.example.ui.components

import com.example.ui.tr
import android.content.Context

/** Assets are immutable RGB565LE frames captured from accepted C, never re-drawn in Kotlin. */
object AcceptedAvatarFrames {
    val ids = setOf("flame", "fox_spirit", "crystal_dragon", "celestial_owl", "starlight_axolotl")
    private val cache = mutableMapOf<String, ByteArray>()
    fun frameOrNull(context: Context, id: String, mode: Int, frame: Int): IntArray? {
        if (id !in ids || mode !in 0..2 || frame !in 0..3) return null
        return frame(context, id, mode, frame)
    }
    fun frame(context: Context, id: String, mode: Int, frame: Int): IntArray {
        require(id in ids && mode in 0..2 && frame in 0..3)
        val bytes = synchronized(cache) { cache.getOrPut(id) {
            context.assets.open("avatars/$id.rgb565").use { it.readBytes() }.also { raw ->
                check(raw.size == 12 * 4096 * 2) { tr("Unvollständiger Figuren-Frame.") }
            }
        } }
        val offset = (mode * 4 + frame) * 8192
        return IntArray(4096) { pixel ->
            val pos = offset + pixel * 2
            val color = (bytes[pos].toInt() and 0xff) or ((bytes[pos + 1].toInt() and 0xff) shl 8)
            val r = color shr 11 and 31
            val g = color shr 5 and 63
            val b = color and 31
            (0xff shl 24) or (((r shl 3) or (r shr 2)) shl 16) or
                (((g shl 2) or (g shr 4)) shl 8) or ((b shl 3) or (b shr 2))
        }
    }
}
