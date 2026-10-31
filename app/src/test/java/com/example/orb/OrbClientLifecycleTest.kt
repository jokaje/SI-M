package com.example.orb

import androidx.test.core.app.ApplicationProvider
import android.app.Application
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class OrbClientLifecycleTest {
    private val trust = object : OrbTrustStore {
        override val address = "02:00:00:00:00:01"
        override fun key(): ByteArray? = null
        override fun matches(peer: android.bluetooth.BluetoothDevice) = false
        override fun enroll(peer: android.bluetooth.BluetoothDevice, proofKey: ByteArray) = false
    }
    private fun field(client: OrbSetupClient, name: String, value: Any?) {
        OrbSetupClient::class.java.getDeclaredField(name).apply { isAccessible = true }.set(client, value)
    }
    @Test fun `NVS failure is retryable but unsupported op is not`() {
        assertFalse(languageFailureUnsupported(-1)) // ESP_FAIL, including NVS commit failures
        assertFalse(languageFailureUnsupported(0x102))
        assertTrue(languageFailureUnsupported(0x106)) // ESP_ERR_NOT_SUPPORTED
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), trust)
        (client.state as kotlinx.coroutines.flow.MutableStateFlow).value = SetupState(
            connected = true, authorized = true, languageGeneration = 7,
            deviceLanguage = "de", languageError = "Orb meldet Fehler -1. Sprache nicht gespeichert; erneut versuchen.")
        client.retryLanguage()
        assertNull(client.state.value.languageError)
        assertNull(client.state.value.deviceLanguage)
        assertEquals(7L, client.state.value.languageGeneration)
        (client.state as kotlinx.coroutines.flow.MutableStateFlow).value = client.state.value.copy(
            authorized = false, languageError = "retry later")
        client.retryLanguage()
        assertEquals("retry later", client.state.value.languageError)
        client.close()
    }
    @Test fun `late foreign GATT disconnect cannot replace current session or queue retry`() {
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), trust)
        val current = mock(BluetoothGatt::class.java)
        val stale = mock(BluetoothGatt::class.java)
        client.onHostStarted()
        field(client, "reconnectArmed", true)
        field(client, "gatt", current)
        field(client, "trustedReconnect", true)
        val cb = OrbSetupClient::class.java.getDeclaredField("callback").apply { isAccessible = true }.get(client) as BluetoothGattCallback
        cb.onConnectionStateChange(stale, 8, BluetoothProfile.STATE_DISCONNECTED)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertSame(current, OrbSetupClient::class.java.getDeclaredField("gatt").apply { isAccessible = true }.get(client))
        cb.onConnectionStateChange(current, 8, BluetoothProfile.STATE_DISCONNECTED)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(OrbSetupClient::class.java.getDeclaredField("gatt").apply { isAccessible = true }.get(client))
        assertNotNull(OrbSetupClient::class.java.getDeclaredField("retryJob").apply { isAccessible = true }.get(client))
        client.onHostStopped()
        assertNull(OrbSetupClient::class.java.getDeclaredField("retryJob").apply { isAccessible = true }.get(client))
        client.close()
    }
    @Test fun `write result from superseded session cannot clear a newer draft`() {
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), trust)
        client.onHostStarted()
        val session = OrbSetupClient::class.java.getDeclaredField("session").apply { isAccessible = true }.get(client) as SetupSession
        val old = WriteOutcome(11, session.generation, "wifi", false)
        field(client, "activeWrite", old)
        val finish = OrbSetupClient::class.java.getDeclaredMethod("finishWrite", WriteOutcome::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        session.invalidate()
        val current = WriteOutcome(12, session.generation, "wifi", false)
        field(client, "activeWrite", current)
        finish.invoke(client, old, true)
        assertNull(client.state.value.writeOutcome)
        finish.invoke(client, current, true)
        assertEquals(12L, client.state.value.writeOutcome?.request)
        assertTrue(client.state.value.writeOutcome?.confirmed == true)
        client.close()
    }
    private fun method(client: OrbSetupClient, name: String, vararg types: Class<*>): java.lang.reflect.Method =
        OrbSetupClient::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }
    private fun session(client: OrbSetupClient): SetupSession =
        OrbSetupClient::class.java.getDeclaredField("session").apply { isAccessible = true }.get(client) as SetupSession
    private fun open(client: OrbSetupClient, enabled: Boolean) {
        method(client, "publishCompanion", CompanionStatus::class.java, Boolean::class.javaPrimitiveType!!)
            .invoke(client, CompanionStatus("flame", "idle", enabled, true, 1, "0".repeat(64)), true)
    }
    @Test fun `closing and reopening invalidates UI bound WLAN until network read succeeds`() {
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), trust)
        client.onHostStarted()
        field(client, "proofSession", CompanionProofSession(ByteArray(32), ByteArray(32), ByteArray(32)))
        field(client, "device", mock(android.bluetooth.BluetoothDevice::class.java))
        (client.state as kotlinx.coroutines.flow.MutableStateFlow).value = SetupState(connected = true,
            currentNetwork = "old-network", wifiConnected = true, wifiStatusKnown = true,
            savedNetworks = listOf("old-network"), savedNetworksKnown = true)
        open(client, true)
        open(client, false)
        assertEquals("", client.state.value.currentNetwork)
        assertFalse(client.state.value.wifiStatusKnown)
        assertFalse(client.state.value.savedNetworksKnown)
        assertEquals("Status unbekannt · erneut laden", WifiPresentation.connection(client.state.value))
        open(client, true)
        assertFalse(client.state.value.wifiStatusKnown)
        assertFalse(client.state.value.savedNetworksKnown)
        assertEquals("Status unbekannt · erneut laden", WifiPresentation.summary(client.state.value))
        client.close()
    }
    @Test fun `language readback expires on signed closure and new window generation`() {
        val matchingTrust = object : OrbTrustStore {
            override val address = "02:00:00:00:00:01"
            override fun key(): ByteArray? = null
            override fun matches(peer: android.bluetooth.BluetoothDevice) = true
            override fun enroll(peer: android.bluetooth.BluetoothDevice, proofKey: ByteArray) = false
        }
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), matchingTrust)
        client.onHostStarted()
        field(client, "proofSession", CompanionProofSession(ByteArray(32), ByteArray(32), ByteArray(32)))
        field(client, "device", mock(android.bluetooth.BluetoothDevice::class.java))
        (client.state as kotlinx.coroutines.flow.MutableStateFlow).value = SetupState(connected = true,
            deviceLanguage = "de", languageUnsupported = true)
        open(client, true)
        val first = client.state.value.languageGeneration
        assertTrue(client.state.value.authorized)
        assertNull(client.state.value.deviceLanguage)
        assertFalse(client.state.value.languageUnsupported)
        (client.state as kotlinx.coroutines.flow.MutableStateFlow).value = client.state.value.copy(deviceLanguage = "en")
        open(client, false)
        assertNull(client.state.value.deviceLanguage)
        assertFalse(client.state.value.authorized)
        open(client, true)
        assertTrue(client.state.value.languageGeneration > first)
        assertNull(client.state.value.deviceLanguage)
        client.close()
    }
    @Test fun `failed refresh cannot retain WLAN from preceding physical window`() {
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), trust)
        client.onHostStarted()
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(*client.permissions())
        session(client).setTrustedWindow(true)
        (client.state as kotlinx.coroutines.flow.MutableStateFlow).value = SetupState(connected = true,
            trusted = true, authorized = true, currentNetwork = "old-network", wifiConnected = true,
            wifiStatusKnown = true, savedNetworks = listOf("old-network"), savedNetworksKnown = true)
        field(client, "gatt", mock(BluetoothGatt::class.java))
        field(client, "commandCharacteristic", mock(android.bluetooth.BluetoothGattCharacteristic::class.java))
        field(client, "statusCharacteristic", mock(android.bluetooth.BluetoothGattCharacteristic::class.java))
        client.refreshManagement()
        assertFalse(client.state.value.wifiStatusKnown)
        assertFalse(client.state.value.savedNetworksKnown)
        assertEquals("Status unbekannt · erneut laden", WifiPresentation.onboarding(client.state.value))
        // A failed command write follows the real refresh job's exception path.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertNotNull(client.state.value.managementError)
        assertFalse(client.state.value.wifiStatusKnown)
        assertFalse(client.state.value.savedNetworksKnown)
        assertEquals("Status unbekannt · erneut laden", WifiPresentation.summary(client.state.value))
        client.close()
    }
    @Test fun `successful networks reply publishes both knownness flags and actual link truth`() {
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), trust)
        val apply = method(client, "applyReply", SetupCommand::class.java, SetupReply::class.java)
        apply.invoke(client, SetupCommand.Networks, SetupReply(1, "done", savedNetworks = emptyList(),
            currentNetwork = "", wifiConnected = false))
        assertTrue(client.state.value.wifiStatusKnown)
        assertTrue(client.state.value.savedNetworksKnown)
        assertEquals("nicht eingerichtet", WifiPresentation.summary(client.state.value))
        apply.invoke(client, SetupCommand.Networks, SetupReply(2, "done", savedNetworks = listOf("home"),
            currentNetwork = "home", wifiConnected = true))
        assertEquals("verbunden", WifiPresentation.connection(client.state.value))
        assertEquals("home (verbunden)", WifiPresentation.summary(client.state.value))
        client.close()
    }
    @Test fun `agent job cancellation releases busy ownership after window reopens and drains pending bootstrap`() {
        val ownerTrust = object : OrbTrustStore {
            override val address = "02:00:00:00:00:01"
            override fun key(): ByteArray? = null
            override fun matches(peer: android.bluetooth.BluetoothDevice) = true
            override fun enroll(peer: android.bluetooth.BluetoothDevice, proofKey: ByteArray) = false
        }
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), ownerTrust)
        client.onHostStarted()
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(*client.permissions())
        field(client, "proofSession", CompanionProofSession(ByteArray(32), ByteArray(32), ByteArray(32)))
        field(client, "device", mock(android.bluetooth.BluetoothDevice::class.java))
        val g = mock(BluetoothGatt::class.java)
        field(client, "gatt", g)
        (client.state as kotlinx.coroutines.flow.MutableStateFlow).value = SetupState(connected = true, trusted = true)
        open(client, true)
        assertTrue(client.state.value.authorized)
        client.performAgent(AgentCommand.Get)
        assertTrue(client.state.value.busy)
        client.beforeCommandCancellationHandling = {
            open(client, true)
            field(client, "commandCharacteristic", mock(android.bluetooth.BluetoothGattCharacteristic::class.java))
            field(client, "statusCharacteristic", mock(android.bluetooth.BluetoothGattCharacteristic::class.java))
            val reopenedWindow = OrbSetupClient::class.java.getDeclaredField("windowEpoch").apply { isAccessible = true }.getLong(client)
            field(client, "pendingBootstrapEpoch", reopenedWindow)
        }
        open(client, false)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(client.state.value.authorized)
        assertNull(OrbSetupClient::class.java.getDeclaredField("agentOperation").apply { isAccessible = true }.get(client))
        assertNull(OrbSetupClient::class.java.getDeclaredField("pendingBootstrapEpoch").apply { isAccessible = true }.get(client))
        assertEquals("", client.state.value.currentNetwork)
        assertFalse(client.state.value.wifiStatusKnown)
        assertFalse(client.state.value.savedNetworksKnown)
        assertEquals("Status unbekannt · erneut laden", WifiPresentation.connection(client.state.value))
        client.close()
    }
    @Test fun `bounded owner retry refuses a fifth attempt and explicit stop cancels a pending timer`() {
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), trust)
        client.onHostStarted()
        field(client, "reconnectArmed", true)
        field(client, "retryAttempt", 4)
        method(client, "scheduleReconnect", Boolean::class.javaPrimitiveType!!).invoke(client, false)
        assertNull(OrbSetupClient::class.java.getDeclaredField("retryJob").apply { isAccessible = true }.get(client))
        field(client, "retryAttempt", 0)
        method(client, "scheduleReconnect", Boolean::class.javaPrimitiveType!!).invoke(client, false)
        assertNotNull(OrbSetupClient::class.java.getDeclaredField("retryJob").apply { isAccessible = true }.get(client))
        client.stop()
        assertNull(OrbSetupClient::class.java.getDeclaredField("retryJob").apply { isAccessible = true }.get(client))
        client.close()
    }
    @Test fun `background stops verified link and explicit stop cannot reconnect`() {
        val client = OrbSetupClient(ApplicationProvider.getApplicationContext<Application>(), trust)
        client.onHostStarted()
        field(client, "reconnectArmed", true)
        client.onHostStopped()
        assertFalse(OrbSetupClient::class.java.getDeclaredField("foreground").apply { isAccessible = true }.getBoolean(client))
        client.stop()
        assertFalse(OrbSetupClient::class.java.getDeclaredField("reconnectArmed").apply { isAccessible = true }.getBoolean(client))
        client.onHostStarted()
        assertFalse(client.state.value.connecting)
        client.close()
    }
}
