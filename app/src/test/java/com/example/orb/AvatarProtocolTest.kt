package com.example.orb

import org.junit.Assert.*
import org.junit.Test
import java.util.zip.CRC32
import kotlinx.serialization.json.jsonObject

class AvatarProtocolTest {
    private val dna = ByteArray(48).apply {
        "AVD1".toByteArray().copyInto(this)
        this[4] = 1
        val bytes = this
        val crc = CRC32().apply { update(bytes, 0, 44) }.value
        for (i in 0..3) this[44 + i] = (crc ushr (8 * i)).toByte()
    }.joinToString("") { "%02x".format(it) }

    @Test fun rejectsMalformedDnaBeforeWire() {
        listOf("0".repeat(96), dna.uppercase(), dna.dropLast(2), dna.replaceRange(16, 18, "ff"),
            dna.replaceRange(72, 74, "01")).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { SetupProtocol.encode(2, SetupCommand.AvatarSet(bad)) }
        }
    }

    @Test fun boundedOperationsAndStrictReplies() {
        val request = String(SetupProtocol.encode(3, SetupCommand.AvatarSet(dna)))
        assertTrue(request.length <= 512)
        assertEquals("avatar_set", kotlinx.serialization.json.Json.parseToJsonElement(request).jsonObject.getValue("op").toString().trim('"'))
        val caps = SetupProtocol.decode("""{"v":1,"id":3,"state":"done","avatar_caps":{"v":1,"slots":1,"dna_bytes":48,"renderer":1}}""".toByteArray())
        SetupProtocol.validateReply(SetupCommand.AvatarCaps, caps)
        val get = SetupProtocol.decode("""{"v":1,"id":3,"state":"done","avatar":{"v":1,"present":true,"dna":"$dna"}}""".toByteArray())
        SetupProtocol.validateReply(SetupCommand.AvatarGet, get)
        assertEquals(dna, get.avatarDna)
        listOf("""{"v":1,"id":3,"state":"done","avatar":{"v":1,"present":true,"dna":"${dna.uppercase()}"}}""",
            """{"v":1,"id":3,"state":"done","avatar":{"v":1,"present":false,"dna":"$dna"}}""",
            """{"v":1,"id":3,"state":"done","avatar_caps":{"v":1,"slots":2,"dna_bytes":48,"renderer":1}}""").forEach { bad ->
            assertThrows(Exception::class.java) { SetupProtocol.decode(bad.toByteArray()) }
        }
    }

    @Test fun customStatusAcceptedWithoutChangingProof() {
        assertTrue(CompanionProtocol.acceptedCharacterId("custom"))
        assertFalse(CompanionProtocol.acceptedCharacterId("not_an_owner"))
    }
}
