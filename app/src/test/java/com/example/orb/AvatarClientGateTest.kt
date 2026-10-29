package com.example.orb

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AvatarClientGateTest {
    private val trust = object : OrbTrustStore {
        override val address: String? = null
        override fun key(): ByteArray? = null
        override fun matches(peer: android.bluetooth.BluetoothDevice) = false
        override fun enroll(peer: android.bluetooth.BluetoothDevice, proofKey: ByteArray) = false
    }
    @Test fun `offline draft never enters command channel`() {
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), trust)
        client.onHostStarted()
        assertNull(client.perform(SetupCommand.AvatarDelete))
        assertNull(client.state.value.selectedCharacter)
        client.close()
    }
    @Test fun `older firmware capability absence disables transfer without touching selected character`() {
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), trust)
        client.onHostStarted()
        org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(*client.permissions())
        val session = OrbSetupClient::class.java.getDeclaredField("session").apply { isAccessible = true }.get(client) as SetupSession
        session.setTrustedWindow(true)
        (client.state as MutableStateFlow).value = SetupState(connected = true, trusted = true,
            authorized = true, setupOpen = true, selectedCharacter = "fox_spirit", avatarSupported = false)
        assertNull(client.perform(SetupCommand.AvatarDelete))
        assertEquals("fox_spirit", client.state.value.selectedCharacter)
        assertFalse(client.state.value.avatarSupported)
        client.close()
    }
    @Test fun `signed window closure invalidates previously read DNA`() {
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), trust)
        client.onHostStarted()
        (client.state as MutableStateFlow).value = SetupState(connected = true, trusted = true,
            authorized = true, setupOpen = true, selectedCharacter = "custom", avatarSupported = true, avatarDna = "cached")
        val publish = OrbSetupClient::class.java.getDeclaredMethod("publishCompanion", CompanionStatus::class.java,
            Boolean::class.javaPrimitiveType!!).apply { isAccessible = true }
        publish.invoke(client, CompanionStatus("custom", "idle", false, true, 1, "0".repeat(64)), true)
        assertNull(client.state.value.avatarDna)
        assertFalse(client.state.value.avatarSupported)
        assertFalse(client.state.value.authorized)
        client.close()
    }
}
