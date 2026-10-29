package com.example.orb

/** Content-free, single-attempt scan record. No peer identifiers or advertisement data. */
internal data class ScanDiagnostic(
    val stage: String = "idle",
    val api: Int,
    val scanGranted: Boolean,
    val connectGranted: Boolean,
    val bluetoothEnabled: Boolean,
    val locationEnabled: Boolean?,
    val callbackCount: Int = 0,
    val setupCount: Int = 0,
    val companionCount: Int = 0,
    val unknownCount: Int = 0,
    val failureCode: Int? = null
) {
    fun snapshot(version: String): String = buildString {
        append("SCANDIAG v1 app=").append(version)
        append(" api=").append(api)
        append(" scan_permission=").append(scanGranted)
        append(" connect_permission=").append(connectGranted)
        append(" bluetooth=").append(bluetoothEnabled)
        append(" location=").append(locationEnabled?.toString() ?: "unknown")
        append(" stage=").append(stage)
        append(" callbacks=").append(callbackCount)
        append(" setup=").append(setupCount)
        append(" companion=").append(companionCount)
        append(" unknown=").append(unknownCount)
        append(" scan_failed=").append(failureCode?.toString() ?: "none")
    }
}
