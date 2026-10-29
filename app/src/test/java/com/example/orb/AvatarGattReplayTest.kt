package com.example.orb

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.util.concurrent.TimeUnit

/** The real perform -> send -> GATT callback -> decode -> readback path, with fake radio only. */
@RunWith(RobolectricTestRunner::class)
class AvatarGattReplayTest {
    private val fixture by lazy {
        val raw = javaClass.classLoader!!.getResourceAsStream("firmware-avatar-replies.json")!!.bufferedReader().readText()
        Json.parseToJsonElement(raw).jsonObject.getValue("replies").jsonObject
    }
    private val trust = object : OrbTrustStore {
        override val address: String? = null
        override fun key(): ByteArray? = null
        override fun matches(peer: android.bluetooth.BluetoothDevice) = false
        override fun enroll(peer: android.bluetooth.BluetoothDevice, proofKey: ByteArray) = false
    }
    private fun set(client: OrbSetupClient, name: String, value: Any?) {
        OrbSetupClient::class.java.getDeclaredField(name).apply { isAccessible = true }.set(client, value)
    }
    private fun replay(mismatch: Boolean = false, closeOnReadback: Boolean = false, unsupported: Boolean = false,
                       retryAfterMismatch: Boolean = false): SetupState {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = OrbSetupClient(app, trust)
        client.onHostStarted()
        Shadows.shadowOf(app).grantPermissions(*client.permissions())
        val session = OrbSetupClient::class.java.getDeclaredField("session").apply { isAccessible = true }.get(client) as SetupSession
        session.setTrustedWindow(true)
        val gatt = mock(BluetoothGatt::class.java)
        val write = mock(BluetoothGattCharacteristic::class.java)
        val status = mock(BluetoothGattCharacteristic::class.java)
        `when`(write.uuid).thenReturn(SetupProtocol.COMMAND)
        `when`(status.uuid).thenReturn(SetupProtocol.STATUS)
        set(client, "gatt", gatt)
        set(client, "commandCharacteristic", write)
        set(client, "statusCharacteristic", status)
        set(client, "mtu", 517)
        (client.state as MutableStateFlow).value = SetupState(connected = true, authorized = true,
            trusted = true, setupOpen = true, avatarSupported = true, selectedCharacter = "flame")
        val callback = OrbSetupClient::class.java.getDeclaredField("callback").apply { isAccessible = true }.get(client) as BluetoothGattCallback
        var response = ""
        var commands = 0
        fun replyFor(payload: ByteArray) {
            val request = Json.parseToJsonElement(payload.decodeToString()).jsonObject
            val id = request.getValue("id").jsonPrimitive.content
            val operation = request.getValue("op").jsonPrimitive.content
            val key = when (operation) {
                "avatar_set" -> "set"; "avatar_get" -> "get"; "character_get" -> "character"
                "avatar_caps" -> "caps"
                else -> error("Unexpected command $operation")
            }
            response = fixture.getValue(key).jsonPrimitive.content.replace(Regex("\\\"id\\\":\\d+"), "\"id\":$id")
            if (mismatch && key == "character" && commands <= 2) response = response.replace("\"custom\"", "\"flame\"")
            commands++
            callback.onCharacteristicWrite(gatt, write, if (unsupported) BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH else BluetoothGatt.GATT_SUCCESS)
        }
        @Suppress("DEPRECATION")
        `when`(gatt.writeCharacteristic(write)).thenAnswer {
            replyFor(write.value)
            true
        }
        `when`(gatt.writeCharacteristic(org.mockito.ArgumentMatchers.eq(write),
            org.mockito.ArgumentMatchers.any(ByteArray::class.java), org.mockito.ArgumentMatchers.anyInt())).thenAnswer {
            replyFor(it.getArgument(1))
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }
        `when`(gatt.readCharacteristic(status)).thenAnswer {
            if (closeOnReadback && commands == 2) {
                client.stop("Setup-Fenster geschlossen")
                false
            } else {
                callback.onCharacteristicRead(gatt, status, response.toByteArray(), BluetoothGatt.GATT_SUCCESS)
                true
            }
        }
        val dna = SetupProtocol.decode(fixture.getValue("get").jsonPrimitive.content.toByteArray()).avatarDna!!
        val request = client.perform(if (unsupported) SetupCommand.AvatarCaps else SetupCommand.AvatarSet(dna))
        if (unsupported) assertNull(request) else assertNotNull(request)
        assertTrue("admission: ${client.state.value}", client.state.value.busy)
        assertTrue("connected before replay", client.state.value.connected)
        fun drain() {
            for (attempt in 0 until 35) {
                Shadows.shadowOf(Looper.getMainLooper()).runToNextTask()
                Thread.sleep(20)
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
                if (!client.state.value.busy) break
            }
            assertFalse("Operation did not complete", client.state.value.busy)
        }
        drain()
        if (retryAfterMismatch) {
            assertNull(client.state.value.avatarDna)
            assertTrue(client.state.value.connected)
            assertNotNull(client.perform(SetupCommand.AvatarSet(dna)))
            drain()
        }
        val result = client.state.value
        if (!closeOnReadback) assertEquals("state=${result.status} avatar=${result.avatarStatus}",
            if (unsupported) 1 else if (retryAfterMismatch) 6 else 3, commands)
        client.close()
        return result
    }
    @Test fun olderParserRejectsOptionalCapsWithoutDroppingLegacyConnection() {
        val state = replay(unsupported = true)
        assertTrue(state.connected)
        assertTrue(state.authorized)
        assertFalse(state.avatarSupported)
        assertEquals("flame", state.selectedCharacter)
    }
    @Test fun acceptedFirmwareSetAndTwoReadbacksConfirmOnActualClient() {
        val state = replay()
        assertEquals("custom", state.selectedCharacter)
        assertNotNull(state.avatarDna)
        assertTrue(state.avatarStatus.contains("zurückgelesen"))
    }
    @Test fun mismatchedSelectedReadbackCannotPublishSuccess() {
        val state = replay(mismatch = true)
        assertNull(state.avatarDna)
        assertNull(state.selectedCharacter)
    }
    @Test fun retryAfterMismatchedReadbackCanConfirmRetainedDraft() {
        val state = replay(mismatch = true, retryAfterMismatch = true)
        assertEquals("custom", state.selectedCharacter)
        assertNotNull(state.avatarDna)
    }
    @Test fun closedWindowDuringReadbackCannotPublishSuccess() {
        val state = replay(closeOnReadback = true)
        assertNull(state.avatarDna)
        assertFalse(state.authorized)
    }
}
