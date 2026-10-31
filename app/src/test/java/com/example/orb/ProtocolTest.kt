package com.example.orb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupProtocolTest {
    @Test fun `language wire contract and readback`() {
        assertEquals("""{"v":1,"id":7,"op":"language_set","language":"en"}""",
            SetupProtocol.encode(7, SetupCommand.LanguageSet("en")).toString(Charsets.UTF_8))
        val reply = SetupProtocol.decode("""{"v":1,"id":7,"state":"done","language":"en"}""".toByteArray())
        SetupProtocol.validateReply(SetupCommand.LanguageGet, reply)
        assertEquals("en", reply.language)
        assertTrue(runCatching { SetupProtocol.encode(7, SetupCommand.LanguageSet("fr")) }.isFailure)
        assertTrue(runCatching { SetupProtocol.decode("""{"v":1,"id":7,"state":"done","language":"fr"}""".toByteArray()) }.isFailure)
        assertTrue(runCatching { SetupProtocol.decode("""{"v":1,"id":7,"state":"done","language":"en","volume":30}""".toByteArray()) }.isFailure)
    }


    @Test
    fun `scan command is a valid versioned request`() {
        val raw = SetupProtocol.encode(7, SetupCommand.Scan).toString(Charsets.UTF_8)
        assertEquals("""{"v":1,"id":7,"op":"scan"}""", raw)
    }

    @Test
    fun `save command carries credentials but never prints them`() {
        val command = SetupCommand.Save("HomeWLAN", "supersecret")
        val raw = SetupProtocol.encode(1, command).toString(Charsets.UTF_8)
        assertTrue(raw.contains("\"password\":\"supersecret\""))
        assertFalse(command.toString().contains("supersecret"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `open WLAN passwords are rejected`() {
        SetupProtocol.encode(1, SetupCommand.Save("HomeWLAN", ""))
    }

    @Test
    fun `done settings reply decodes`() {
        val reply = SetupProtocol.decode(
            """{"v":1,"id":3,"state":"done","volume":55,"brightness":70,"sleep_s":120}"""
                .toByteArray(Charsets.UTF_8)
        )
        assertEquals("done", reply.state)
        assertEquals(DeviceSettings(55, 70, 120), reply.settings)
        SetupProtocol.validateReply(SetupCommand.Settings, reply)
    }

    @Test
    fun `catalog reply decodes and validates`() {
        val reply = SetupProtocol.decode(
            """{"v":1,"id":4,"state":"done","characters":[{"id":"flame","name":"Flamme"}],"selected":"flame"}"""
                .toByteArray(Charsets.UTF_8)
        )
        assertEquals(listOf(CharacterOption("flame", "Flamme")), reply.characters)
        assertEquals("flame", reply.selected)
        SetupProtocol.validateReply(SetupCommand.Catalog, reply)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate json keys are rejected`() {
        SetupProtocol.decode(
            """{"v":1,"v":1,"id":2,"state":"done"}""".toByteArray(Charsets.UTF_8)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown status field is rejected`() {
        SetupProtocol.decode(
            """{"v":1,"id":2,"state":"done","secret":"x"}""".toByteArray(Charsets.UTF_8)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reply that does not match the command is rejected`() {
        val reply = SetupProtocol.decode(
            """{"v":1,"id":2,"state":"done","saved":[],"current":"","connected":false}"""
                .toByteArray(Charsets.UTF_8)
        )
        SetupProtocol.validateReply(SetupCommand.Scan, reply)
    }

    @Test
    fun `error reply carries the firmware code`() {
        val reply = SetupProtocol.decode(
            """{"v":1,"id":9,"state":"error","code":42}""".toByteArray(Charsets.UTF_8)
        )
        assertEquals(42, reply.code)
        assertNull(reply.settings)
    }
}

class AgentProtocolTest {

    private val validUrl = "https://bridge.example.com/v1/turn-stream"
    private val validToken = "0123456789abcdef0123456789abcdef"

    @Test
    fun `https bridge url is accepted`() {
        assertTrue(AgentProtocol.validUrl(validUrl))
        assertTrue(AgentProtocol.validUrl("https://example.com:8443/api/v1/turn-stream"))
    }

    @Test
    fun `insecure or malformed urls are rejected`() {
        assertFalse(AgentProtocol.validUrl("http://example.com/v1/turn-stream"))
        assertFalse(AgentProtocol.validUrl("https://example.com/v1/other"))
        assertFalse(AgentProtocol.validUrl("https://example.com/v1/turn-stream?x=1"))
        assertFalse(AgentProtocol.validUrl("https://user@example.com/v1/turn-stream"))
        assertFalse(AgentProtocol.validUrl("https://exa mple.com/v1/turn-stream"))
    }

    @Test
    fun `token length and charset are enforced`() {
        assertTrue(AgentProtocol.validToken(validToken))
        assertFalse(AgentProtocol.validToken("short"))
        assertFalse(AgentProtocol.validToken("$validToken space"))
    }

    @Test
    fun `set command is encoded without leaking the token in toString`() {
        val command = AgentCommand.Set(validUrl, validToken)
        val raw = AgentProtocol.encode(5, command).toString(Charsets.UTF_8)
        assertTrue(raw.contains("\"op\":\"agent_set\""))
        assertTrue(raw.contains("\"url\":\"$validUrl\""))
        assertFalse(command.toString().contains(validToken))
    }

    @Test
    fun `custom profile reply decodes`() {
        val reply = AgentProtocol.decode(
            """{"v":1,"id":6,"state":"done","agent":{"v":1,"source":"custom","configured":true,"adapter":"kim-stream-v1","url":"$validUrl"}}"""
                .toByteArray(Charsets.UTF_8)
        )
        assertEquals("custom", reply.profile?.source)
        assertTrue(reply.profile?.configured == true)
        assertTrue(AgentProtocol.canRemove(reply.profile))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown adapter is rejected`() {
        AgentProtocol.decode(
            """{"v":1,"id":6,"state":"done","agent":{"v":1,"source":"custom","configured":true,"adapter":"other-v1","url":"$validUrl"}}"""
                .toByteArray(Charsets.UTF_8)
        )
    }

    @Test
    fun `builtin profile cannot be removed`() {
        assertFalse(AgentProtocol.canRemove(AgentProfile("builtin", true, "kim-stream-v1", "")))
        assertFalse(AgentProtocol.canRemove(null))
    }

    @Test
    fun `capability reply is parsed`() {
        val reply = AgentProtocol.decode(
            """{"v":1,"id":2,"state":"done","agent_caps":{"v":1,"reset_unconfigures":true}}"""
                .toByteArray(Charsets.UTF_8)
        )
        assertEquals(true, reply.resetUnconfigures)
    }
}

class OrbProofTest {

    @Test
    fun `role proof is deterministic and role separated`() {
        val key = ByteArray(32) { it.toByte() }
        val app = ByteArray(32) { (it + 1).toByte() }
        val device = ByteArray(32) { (it + 2).toByte() }
        val orb = OrbProof.role(key, "KI-M-ORB-PROOF-V1", app, device)
        val phone = OrbProof.role(key, "KI-M-PHONE-PROOF-V1", app, device)
        assertArrayEquals(orb, OrbProof.role(key, "KI-M-ORB-PROOF-V1", app, device))
        assertFalse(orb.contentEquals(phone))
        assertEquals(64, OrbProof.hex(orb).length)
        assertArrayEquals(orb, OrbProof.unhex(OrbProof.hex(orb)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown role label is rejected`() {
        OrbProof.role(ByteArray(32), "SOMETHING-ELSE", ByteArray(32), ByteArray(32))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `short keys are rejected`() {
        OrbProof.mac(ByteArray(16), byteArrayOf(1))
    }
}
