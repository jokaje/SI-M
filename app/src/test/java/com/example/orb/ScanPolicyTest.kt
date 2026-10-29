package com.example.orb

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ScanPolicyTest {
    @Test fun `merged manifest asserts nonlocation scan and legacy location bound`() {
        val candidates = listOf(
            java.io.File("build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"),
            java.io.File("app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml")
        )
        val merged = candidates.firstOrNull { it.isFile } ?: error("Merged debug manifest missing")
        val xml = merged.readText()
        val scan = Regex("""<uses-permission\s+android:name="android.permission.BLUETOOTH_SCAN"[^>]*>""")
            .find(xml)?.value ?: error("BLUETOOTH_SCAN missing from merged manifest")
        val location = Regex("""<uses-permission\s+android:name="android.permission.ACCESS_FINE_LOCATION"[^>]*>""")
            .find(xml)?.value ?: error("Legacy location missing")
        assertTrue(scan.contains("""android:usesPermissionFlags="neverForLocation""""))
        assertTrue(location.contains("""android:maxSdkVersion="30""""))
        assertTrue(xml.contains("android.permission.BLUETOOTH_CONNECT"))
    }

    @Test fun `start scan reports platform return and bounded outcome without private identifiers`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = OrbSetupClient(app)
        Shadows.shadowOf(app).grantPermissions(*client.permissions())
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        Shadows.shadowOf(adapter).setEnabled(true)
        client.onHostStarted()
        client.startScan()
        assertTrue(client.state.value.scanDiagnostic.contains("stage=platform_return_ok"))
        assertTrue(client.state.value.scanDiagnostic.contains("setup=0"))
        assertTrue(client.state.value.scanDiagnostic.contains("companion=0"))
        assertFalse(client.state.value.scanDiagnostic.contains("address="))
        client.onHostStopped()
        assertTrue(client.state.value.scanDiagnostic.contains("foreground_cancel"))
        client.close()
    }

    @Test fun `production scanner counts both services without copying advertisement contents`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = OrbSetupClient(app)
        Shadows.shadowOf(app).grantPermissions(*client.permissions())
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        Shadows.shadowOf(adapter).setEnabled(true)
        client.onHostStarted()
        client.startScan()
        val scanner = Shadows.shadowOf(adapter.bluetoothLeScanner)
        fun receive(uuid: java.util.UUID) {
            val record = org.mockito.Mockito.mock(android.bluetooth.le.ScanRecord::class.java)
            org.mockito.Mockito.`when`(record.serviceUuids).thenReturn(listOf(android.os.ParcelUuid(uuid)))
            val device = org.mockito.Mockito.mock(android.bluetooth.BluetoothDevice::class.java)
            org.mockito.Mockito.`when`(device.address).thenReturn("11:22:33:44:55:66")
            val result = org.mockito.Mockito.mock(android.bluetooth.le.ScanResult::class.java)
            org.mockito.Mockito.`when`(result.scanRecord).thenReturn(record)
            org.mockito.Mockito.`when`(result.device).thenReturn(device)
            scanner.scanCallbacks.single().onScanResult(android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        receive(SetupProtocol.SERVICE)
        receive(CompanionProtocol.SERVICE)
        val diagnostic = client.state.value.scanDiagnostic
        assertTrue(diagnostic, diagnostic.contains("callbacks=2 setup=1 companion=1 unknown=0"))
        assertFalse(diagnostic.contains("11:22"))
        assertEquals(1, client.state.value.devices.size)
        client.close()
    }

    @Test fun `platform failure retains numeric code but no false timeout`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = OrbSetupClient(app)
        Shadows.shadowOf(app).grantPermissions(*client.permissions())
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        Shadows.shadowOf(adapter).setEnabled(true)
        client.onHostStarted()
        client.startScan()
        Shadows.shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single().onScanFailed(2)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(client.state.value.scanDiagnostic.contains("stage=scan_failed"))
        assertTrue(client.state.value.scanDiagnostic.contains("scan_failed=2"))
        assertFalse(client.state.value.scanning)
        client.close()
    }

    @Test fun `empty timed scan is distinguishable from permission or scanner failure`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = OrbSetupClient(app)
        Shadows.shadowOf(app).grantPermissions(*client.permissions())
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        Shadows.shadowOf(adapter).setEnabled(true)
        client.onHostStarted()
        client.startScan()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(12))
        assertTrue(client.state.value.scanDiagnostic.contains("stage=timeout"))
        assertTrue(client.state.value.status.contains("keine passenden BLE-Anzeigen"))
        assertFalse(client.state.value.scanning)
        client.close()
    }

    @Test fun `foreground preflight rejects scan with a visible typed reason`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = OrbSetupClient(app)
        client.startScan()
        assertTrue(client.state.value.scanDiagnostic.contains("foreground_blocked"))
        assertTrue(client.state.value.status.contains("Vordergrund"))
        client.close()
    }
}
