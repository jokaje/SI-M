package com.example.orb

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.AcceptedAvatarFrames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class FirmwareAvatarIntegrationTest {
    private val replies by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("firmware-avatar-replies.json")!!.bufferedReader().readText()
        Json.parseToJsonElement(text).jsonObject.getValue("replies").jsonObject
    }
    @Test fun productionFirmwareRepliesDecodeAndCorrelate() {
        val cases = mapOf("caps" to SetupCommand.AvatarCaps, "set" to SetupCommand.AvatarSet(
            Json.parseToJsonElement(replies.getValue("get").jsonPrimitive.content).jsonObject.getValue("avatar").jsonObject.getValue("dna").jsonPrimitive.content),
            "get" to SetupCommand.AvatarGet, "character" to SetupCommand.CharacterGet,
            "delete" to SetupCommand.AvatarDelete, "empty" to SetupCommand.AvatarGet)
        cases.forEach { (label, command) ->
            val actual = SetupProtocol.decode(replies.getValue(label).jsonPrimitive.content.toByteArray())
            SetupProtocol.validateReply(command, actual)
            assertEquals("done", actual.state)
            assertTrue(actual.id > 0)
        }
    }
    @Test fun realClientReadbackPublishesOnlyMatchingCurrentGenerationAndRetainsRetryDraft() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val trust = object : OrbTrustStore {
            override val address: String? = null
            override fun key(): ByteArray? = null
            override fun matches(peer: android.bluetooth.BluetoothDevice) = false
            override fun enroll(peer: android.bluetooth.BluetoothDevice, proofKey: ByteArray) = false
        }
        val client = OrbSetupClient(app, trust)
        client.onHostStarted()
        val session = OrbSetupClient::class.java.getDeclaredField("session").apply { isAccessible = true }.get(client) as SetupSession
        val window = OrbSetupClient::class.java.getDeclaredField("windowEpoch").apply { isAccessible = true }
        val commit = OrbSetupClient::class.java.getDeclaredMethod("publishAvatarReadback",
            SetupCommand::class.java, SetupReply::class.java, SetupReply::class.java,
            Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, String::class.java).apply { isAccessible = true }
        val get = SetupProtocol.decode(replies.getValue("get").jsonPrimitive.content.toByteArray())
        val selected = SetupProtocol.decode(replies.getValue("character").jsonPrimitive.content.toByteArray())
        val draft = checkNotNull(get.avatarDna)
        session.setTrustedWindow(true)
        (client.state as kotlinx.coroutines.flow.MutableStateFlow).value = SetupState(connected = true, trusted = true,
            authorized = true, avatarSupported = true, selectedCharacter = "flame")
        fun attempt(read: SetupReply = get, selection: SetupReply = selected, epoch: Long = session.generation,
                    win: Long = window.getLong(client)): Boolean = commit.invoke(client, SetupCommand.AvatarSet(draft),
                        read, selection, epoch, win, "flame") as Boolean
        assertFalse(attempt(selection = selected.copy(selected = "flame")))
        assertNull(client.state.value.avatarDna)
        assertTrue(attempt())
        assertEquals(draft, client.state.value.avatarDna)
        assertEquals("custom", client.state.value.selectedCharacter)
        // A new local draft is independent of the confirmed device state and survives a failed retry.
        val retryDraft = draft.replaceRange(8, 10, "ff")
        assertFalse(attempt(read = get.copy(avatarDna = retryDraft)))
        assertEquals(draft, client.state.value.avatarDna)
        window.setLong(client, window.getLong(client) + 1)
        assertFalse(attempt(win = 0))
        session.invalidate()
        assertFalse(attempt(epoch = 0))
        client.close()
    }
    @Test fun eachAcceptedIdHasExactFramesAndNearestNeighbourPixels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifest = Json.parseToJsonElement(context.assets.open("avatars/manifest.json").bufferedReader().readText()).jsonObject
        val characters = manifest.getValue("characters").jsonObject
        assertEquals(setOf("flame", "fox_spirit", "crystal_dragon", "celestial_owl", "starlight_axolotl"), characters.keys)
        characters.forEach { (id, value) ->
            val raw = context.assets.open("avatars/$id.rgb565").readBytes()
            assertEquals(12 * 64 * 64 * 2, raw.size)
            val expected = value.jsonObject.getValue("sha256").jsonPrimitive.content
            assertEquals(expected, MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) })
            for (mode in 0..2) for (frame in 0..3) {
                val pixels = AcceptedAvatarFrames.frame(context, id, mode, frame)
                assertEquals(4096, pixels.size)
                val index = (mode * 4 + frame) * 8192
                val frameHash = MessageDigest.getInstance("SHA-256").digest(raw.copyOfRange(index, index + 8192))
                    .joinToString("") { "%02x".format(it) }
                assertEquals(value.jsonObject.getValue("frame_sha256").jsonArray[mode * 4 + frame].jsonPrimitive.content, frameHash)
                for (p in pixels.indices) {
                    val at = index + p * 2
                    val rgb565 = (raw[at].toInt() and 255) or ((raw[at + 1].toInt() and 255) shl 8)
                    val r = rgb565 shr 11 and 31
                    val g = rgb565 shr 5 and 63
                    val b = rgb565 and 31
                    val argb = (0xff shl 24) or (((r shl 3) or (r shr 2)) shl 16) or
                        (((g shl 2) or (g shr 4)) shl 8) or ((b shl 3) or (b shr 2))
                    assertEquals("$id/$mode/$frame/$p", argb, pixels[p])
                }
            }
            assertNull(AcceptedAvatarFrames.frameOrNull(context, "not_a_figure", 0, 0))
        }
    }
}
