package com.example.orb

import android.os.ParcelUuid
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import android.os.Looper
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class OrbDiscoveryFiltersTest {
    @Test fun `production startScan installs both service filters at the Android scanner`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = OrbSetupClient(app)
        Shadows.shadowOf(app).grantPermissions(*client.permissions())
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        Shadows.shadowOf(adapter).setEnabled(true)
        client.onHostStarted()
        client.startScan()
        val scans = Shadows.shadowOf(adapter.bluetoothLeScanner).activeScans
        assertEquals(1, scans.size)
        assertEquals(setOf(ParcelUuid(SetupProtocol.SERVICE), ParcelUuid(CompanionProtocol.SERVICE)),
            scans.single().scanFilters().map { it.serviceUuid }.toSet())
        client.close()
    }
    @Test fun `setup and owned companion advertisements are admitted but unrelated services are not`() {
        val filters = OrbSetupClient.discoveryFilters()
        assertEquals(2, filters.size)
        fun admitted(uuid: java.util.UUID): Boolean {
            val record = mock(ScanRecord::class.java)
            `when`(record.serviceUuids).thenReturn(listOf(ParcelUuid(uuid)))
            val result = mock(ScanResult::class.java)
            `when`(result.scanRecord).thenReturn(record)
            return filters.any { it.matches(result) }
        }
        assertTrue(admitted(SetupProtocol.SERVICE))
        assertTrue(admitted(CompanionProtocol.SERVICE))
        assertFalse(admitted(java.util.UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")))
        assertTrue(filters.all { it.serviceUuid != null && it.deviceName == null && it.deviceAddress == null })
    }
}
