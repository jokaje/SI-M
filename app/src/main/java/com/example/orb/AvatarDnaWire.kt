package com.example.orb

import java.util.zip.CRC32

/** Wire-format boundary only. Generation and pixels come from the shared C engine. */
object AvatarDnaWire {
    private val bounds = intArrayOf(7, 4, 2, 3, 5, 4, 3, 2)
    fun decode(hex: String): ByteArray {
        require(hex.matches(Regex("[0-9a-f]{96}"))) { "DNA muss 96 kleine Hex-Zeichen enthalten." }
        val bytes = ByteArray(48) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "AVD1")
        require((4..7).any { bytes[it].toInt() != 0 })
        for (i in bounds.indices) require((bytes[8+i].toInt() and 255) <= bounds[i])
        for (i in 36..43) require(bytes[i].toInt() == 0)
        val crc = CRC32().apply { update(bytes, 0, 44) }.value
        for (i in 0..3) require((bytes[44+i].toInt() and 255) == ((crc ushr (8*i)).toInt() and 255))
        return bytes
    }
}
