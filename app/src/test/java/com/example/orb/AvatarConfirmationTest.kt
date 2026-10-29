package com.example.orb

import org.junit.Assert.*
import org.junit.Test

class AvatarConfirmationTest {
    private val dna = ByteArray(48).apply {
        "AVD1".toByteArray().copyInto(this); this[4] = 7
        val bytes = this
        val crc = java.util.zip.CRC32().apply { update(bytes, 0, 44) }.value
        for (i in 0..3) this[44+i] = (crc ushr (i*8)).toByte()
    }.joinToString("") { "%02x".format(it) }

    @Test fun `write acknowledgement alone is never confirmation`() {
        assertFalse(AvatarConfirmation.accept(dna, null, null, true))
    }
    @Test fun `both DNA and selection must match within same authorized window`() {
        val get = SetupReply(2, "done", avatarPresent = true, avatarDna = dna)
        val selected = SetupReply(3, "done", selected = "custom")
        assertTrue(AvatarConfirmation.accept(dna, get, selected, true))
        assertFalse(AvatarConfirmation.accept(dna, get.copy(avatarDna = ""), selected, true))
        assertFalse(AvatarConfirmation.accept(dna, get, selected.copy(selected = "flame"), true))
        assertFalse(AvatarConfirmation.accept(dna, get, selected, false))
    }
    @Test fun `delete preserves noncustom selection and requires empty slot`() {
        val empty = SetupReply(2, "done", avatarPresent = false, avatarDna = "")
        assertTrue(AvatarConfirmation.accept(null, empty, SetupReply(3, "done", selected = "flame"), true, "custom"))
        assertTrue(AvatarConfirmation.accept(null, empty, SetupReply(3, "done", selected = "fox_spirit"), true, "fox_spirit"))
        assertFalse(AvatarConfirmation.accept(null, empty, SetupReply(3, "done", selected = "custom"), true, "custom"))
    }
}
