package com.example.orb

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import java.security.SecureRandom
import java.security.MessageDigest
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SetupDevice(val address: String, val rssi: Int)
data class WriteOutcome(val request: Long, val session: Long, val kind: String, val confirmed: Boolean)
data class SetupState(
    val devices: List<SetupDevice> = emptyList(),
    val scanning: Boolean = false, val connecting: Boolean = false,
    val connected: Boolean = false, val needsPhysicalConfirmation: Boolean = false,
    val authorized: Boolean = false, val busy: Boolean = false,
    val status: String = "Am Orb: Einstellungen → Orb koppeln → Setup starten. Danach hier nach Geräten suchen.",
    val aps: List<AccessPoint> = emptyList(), val savedNetworks: List<String> = emptyList(),
    val currentNetwork: String = "", val wifiConnected: Boolean = false,
    val wifiStatusKnown: Boolean = false, val savedNetworksKnown: Boolean = false,
    val settings: DeviceSettings? = null, val deviceLanguage: String? = null, val languageUnsupported: Boolean = false,
    val languageGeneration: Long = 0, val languageError: String? = null,
    val characters: List<CharacterOption> = emptyList(), val selectedCharacter: String? = null,
    val catalogUnsupported: Boolean = false, val characterStatus: String = "Figuren noch nicht abgerufen.",
    val avatarSupported: Boolean = false, val avatarDna: String? = null,
    val avatarStatus: String = "Orb-Avatarfähigkeit noch nicht geprüft.",
    val pairingTrace: String = "",
    val scanDiagnostic: String = "",
    val companionSupported: Boolean = false, val trusted: Boolean = false,
    val setupOpen: Boolean = false, val companionCharacterId: String? = null,
    val companionMode: String? = null, val managementError: String? = null,
    val agentProfile: AgentProfile? = null, val agentUnsupported: Boolean = false,
    val agentStatus: String = "Agent/Sprachdienst noch nicht gelesen.",
    val writeOutcome: WriteOutcome? = null
)

/** Dedicated SI-M orb provisioning client. Never reuses MeshCore transport or agent responses. */
@SuppressLint("MissingPermission") // every entry is runtime-gated; revocation tears down the session
class OrbSetupClient(context: Context, private val trust: OrbTrustStore = TrustedOrbPeer(context.applicationContext)) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val session = SetupSession()

    val trustedPeerAddress: String? get() = trust.address
    var afterSetupReadCompletion: (() -> Unit)? = null // deterministic main-looper race probe
    var beforeCommandCancellationHandling: (() -> Unit)? = null // deterministic window-reopen race probe
    private val _state = MutableStateFlow(SetupState())
    private val trace = PairingTrace(SystemClock::elapsedRealtime)
    private var foreground = false
    private var reconnectArmed = false
    private var retryGeneration = 0L
    private var retryAttempt = 0
    private var retryJob: Job? = null
    private var trustedReconnect = false
    private var companionCharacteristic: BluetoothGattCharacteristic? = null
    private var enrollCharacteristic: BluetoothGattCharacteristic? = null
    private var authCharacteristic: BluetoothGattCharacteristic? = null
    private var proofSession: CompanionProofSession? = null
    private var secureRead: CompletableDeferred<ByteArray>? = null
    private var secureReadUuid: java.util.UUID? = null
    private var secureWrite: CompletableDeferred<Unit>? = null
    private var companionRead: CompletableDeferred<ByteArray>? = null
    private var companionReadProofRevision = -1L
    private var enrollWrite: CompletableDeferred<Unit>? = null
    private var companionRevision = 0L
    private var windowEpoch = 0L
    private var pendingBootstrapEpoch: Long? = null
    private fun mark(stage: PairingTrace.Stage, code: Int? = null) {
        trace.record(stage, code)
        _state.update { it.copy(pairingTrace = trace.snapshot()) }
    }
    val state: StateFlow<SetupState> = _state
    fun currentSessionGeneration(): Long = session.generation
    /** The system's consent interstitial or passkey dialog may briefly cross ON_STOP. */
    fun onHostStopped() {
        foreground = false
        retryGeneration++
        retryJob?.cancel(); retryJob = null
        mark(PairingTrace.Stage.LIFECYCLE_STOP)
        if (scan != null) scanStage("foreground_cancel")
        // A system passkey dialog may temporarily own the display. Keep only its
        // bounded enrollment attempt; never run owner retries in the background.
        if (!PairingTrace.mayContinueDuringSystemPairing(_state.value.connecting,
                session.passkeyObserved, session.authorized, session.consentPending)) {
            stop("Hintergrundwechsel: Verbindung beendet.", disarm = false)
        }
    }
    fun onHostStarted() {
        foreground = true
        mark(PairingTrace.Stage.LIFECYCLE_RESUME)
        if (reconnectArmed && !_state.value.connected && !_state.value.connecting &&
            !session.consentPending && !session.passkeyObserved) {
            retryAttempt = 0
            scheduleReconnect(immediate = true)
        }
    }
    /** Only a saved owner may be retried; each timer is invalidated at background/stop. */
    private fun scheduleReconnect(immediate: Boolean = false) {
        retryJob?.cancel(); retryJob = null
        if (!reconnectArmed || !foreground || closed || trust.address == null ||
            _state.value.connected || _state.value.connecting || retryAttempt >= 4) return
        val generation = ++retryGeneration
        val delayMs = if (immediate) 0L else (1_000L shl retryAttempt)
        retryJob = scope.launch {
            delay(delayMs)
            if (generation != retryGeneration || !foreground || !reconnectArmed || closed ||
                _state.value.connected || _state.value.connecting) return@launch
            retryAttempt++
            connectTrustedAttempt()
        }
    }
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var scan: ScanCallback? = null
    private var scanDiagnostic: ScanDiagnostic? = null
    private fun scanStage(stage: String, transform: (ScanDiagnostic) -> ScanDiagnostic = { it }): Unit {
        scanDiagnostic = scanDiagnostic?.let { transform(it).copy(stage = stage) }
        _state.update { it.copy(scanDiagnostic = scanDiagnostic?.snapshot(com.example.BuildConfig.VERSION_NAME).orEmpty()) }
    }
    private fun newScanDiagnostic(stage: String) {
        val scanGranted = ContextCompat.checkSelfPermission(context, if (Build.VERSION.SDK_INT >= 31)
            Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val connectGranted = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val bluetooth = if (connectGranted) runCatching { adapter?.isEnabled == true }.getOrDefault(false) else false
        val location = if (Build.VERSION.SDK_INT >= 28) runCatching {
            context.getSystemService(LocationManager::class.java)?.isLocationEnabled
        }.getOrNull() else null
        scanDiagnostic = ScanDiagnostic(stage, Build.VERSION.SDK_INT, scanGranted, connectGranted, bluetooth, location)
        scanStage(stage)
    }
    private var scanTimer: Job? = null
    private var connectJob: Job? = null
    private var commandJob: Job? = null
    private var agentOperation: Job? = null
    private var keepAlive: Job? = null
    private var consentTimer: Job? = null
    private var gatt: BluetoothGatt? = null
    private var device: BluetoothDevice? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var mtu = 23
    private var receiverRegistered = false
    private var closed = false
    private var mutationPending = false
    private var writeRequest = 0L
    private var activeWrite: WriteOutcome? = null
    private fun finishWrite(request: WriteOutcome?, confirmed: Boolean) {
        if (request == null || activeWrite?.request != request.request) return
        activeWrite = null
        if (session.accepts(request.session) && foreground)
            _state.update { it.copy(writeOutcome = request.copy(confirmed = confirmed)) }
    }
    private var lastWriteAck = 0L
    private val operations = Mutex()
    private val commands = Mutex()
    private val statusHint = Channel<Unit>(Channel.CONFLATED)
    private var connectionDone: CompletableDeferred<Unit>? = null
    private var mtuDone: CompletableDeferred<Int>? = null
    private var discoveryDone: CompletableDeferred<Unit>? = null
    private var descriptorDone: CompletableDeferred<Unit>? = null
    private var readDone: CompletableDeferred<ByteArray>? = null
    private var setupRead: CompletableDeferred<ByteArray>? = null
    private var setupReadEpoch = -1L
    private var setupReadProofRevision = -1L
    private var writeDone: CompletableDeferred<Unit>? = null
    private var reliableDone: CompletableDeferred<Unit>? = null
    private class GattFailure(val code: Int) : IllegalStateException("Bluetooth-GATT-Fehler ($code).")
    private class StaleBond : IllegalStateException()
    private class FirmwareFailure(val code: Int) : IllegalStateException("Orb meldet Fehler $code.")
    private class ResetCapabilityUnavailable : IllegalStateException()

    fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun allowed(): Boolean = permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    fun reportPermissionDenied() { mark(PairingTrace.Stage.PERMISSION_DENIED); stop("Bluetooth-Berechtigung fehlt. Bitte Zugriff auf Geräte in der Nähe erlauben.") }
    fun enabled(): Boolean = runCatching { allowed() && adapter?.isEnabled == true }.getOrDefault(false)

    private fun active(g: BluetoothGatt, block: () -> Unit) {
        main.post { if (!closed && g === gatt) block() }
    }
    private fun complete(done: CompletableDeferred<Unit>?, code: Int) {
        if (code == BluetoothGatt.GATT_SUCCESS) done?.complete(Unit) else done?.completeExceptionally(GattFailure(code))
    }
    // Android callbacks carry GATT + UUID but no operation token. A timeout must
    // retire the entire link before a late callback could satisfy a replacement.
    private suspend fun <T> awaitGatt(timeoutMs: Long, block: suspend () -> T): T = try {
        withTimeout(timeoutMs) { block() }
    } catch (e: TimeoutCancellationException) {
        val wasTrusted = trustedReconnect
        disconnect("Bluetooth-GATT-Zeitüberschreitung. Verbindung neu aufbauen.")
        if (wasTrusted) scheduleReconnect()
        throw e
    }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, code: Int, newState: Int) = active(g) {
            mark(PairingTrace.Stage.GATT_CALLBACK, code)
            if (code == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) connectionDone?.complete(Unit)
            else {
                val wasTrusted = trustedReconnect
                disconnect(if (wasTrusted) "Orb momentan nicht erreichbar. Verbindung im Vordergrund wird begrenzt erneut versucht." else
                    "Orb getrennt ($code). Am Gerät das Bluetooth-Fenster erneut öffnen.")
                if (wasTrusted) scheduleReconnect()
            }
        }
        override fun onMtuChanged(g: BluetoothGatt, value: Int, code: Int) = active(g) {
            mark(PairingTrace.Stage.MTU_CALLBACK, code)
            mtuDone?.complete(if (code == BluetoothGatt.GATT_SUCCESS) value else 23)
        }
        override fun onServicesDiscovered(g: BluetoothGatt, code: Int) = active(g) {
            mark(PairingTrace.Stage.DISCOVERY_CALLBACK, code)
            complete(discoveryDone, code)
        }
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, code: Int) = active(g) {
            if (descriptor.uuid == SetupProtocol.CCCD) {
                mark(PairingTrace.Stage.CCCD_CALLBACK, code)
                complete(descriptorDone, code)
            }
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, code: Int) = active(g) {
            if (characteristic.uuid == SetupProtocol.COMMAND) complete(writeDone, code)
            if (characteristic.uuid == CompanionProtocol.ENROLL) complete(enrollWrite, code)
            if (characteristic.uuid == CompanionProtocol.AUTH) complete(secureWrite, code)
        }
        override fun onReliableWriteCompleted(g: BluetoothGatt, code: Int) = active(g) { complete(reliableDone, code) }
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, code: Int) {
            val snapshot = value.copyOf()
            main.post {
                if (closed || g !== gatt) { snapshot.fill(0); return@post }
                if (characteristic.uuid == CompanionProtocol.ENROLL || characteristic.uuid == CompanionProtocol.AUTH) {
                    if (characteristic.uuid != secureReadUuid) { snapshot.fill(0); return@post }
                    val pending = secureRead
                    if (code == BluetoothGatt.GATT_SUCCESS && pending?.isActive == true) {
                        if (!pending.complete(snapshot)) snapshot.fill(0)
                    } else {
                        snapshot.fill(0)
                        if (code != BluetoothGatt.GATT_SUCCESS) pending?.completeExceptionally(GattFailure(code))
                    }
                    return@post
                }
                if (characteristic.uuid == CompanionProtocol.STATUS) {
                    mark(PairingTrace.Stage.COMPANION_READ_CALLBACK, code)
                    val pending = companionRead
                    if (code == BluetoothGatt.GATT_SUCCESS && pending?.isActive == true) {
                        companionReadProofRevision = if (session.passkeyObserved && !session.consentPending)
                            session.inputRevision else -1L
                        if (!pending.complete(snapshot)) snapshot.fill(0)
                    } else {
                        snapshot.fill(0)
                        if (code != BluetoothGatt.GATT_SUCCESS) pending?.completeExceptionally(GattFailure(code))
                    }
                    return@post
                }
                if (characteristic.uuid != SetupProtocol.STATUS) { snapshot.fill(0); return@post }
                mark(PairingTrace.Stage.PROTECTED_READ_CALLBACK, code)
                val pending = readDone
                if (code == BluetoothGatt.GATT_SUCCESS && pending != null && pending.isActive) {
                    // Credit only at the serialized callback boundary, never after await():
                    // a later PIN event cannot retroactively prove this completed read.
                    if (pending === setupRead && session.accepts(setupReadEpoch) && session.passkeyObserved) {
                        session.readProtected(setupReadEpoch)
                        setupReadProofRevision = session.inputRevision
                    }
                    if (!pending.complete(snapshot)) snapshot.fill(0)
                } else {
                    snapshot.fill(0)
                    if (code != BluetoothGatt.GATT_SUCCESS) pending?.completeExceptionally(GattFailure(code))
                }
            }
        }
        @Deprecated("Android 13 callback")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, code: Int) {
            if (Build.VERSION.SDK_INT < 33) onCharacteristicRead(g, characteristic, characteristic.value ?: byteArrayOf(), code)
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) = active(g) {
            if (characteristic.uuid == SetupProtocol.STATUS) statusHint.trySend(Unit) // truncated notification is only a hint
            if (characteristic.uuid == CompanionProtocol.STATUS) {
                // Notification is an invalidation hint, never authorization or a parsed status.
                companionRevision++
                scope.launch { refreshCompanion() }
            }
        }
        @Deprecated("Android 13 callback")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) onCharacteristicChanged(g, characteristic, byteArrayOf())
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) stop("Bluetooth wurde ausgeschaltet.")
                return
            }
            val peer = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            if (peer == null || peer != device || closed) return
            if (intent.action == BluetoothDevice.ACTION_PAIRING_REQUEST) {
                if (trustedReconnect) { disconnect("Vertrauensbindung verloren. Neue Kopplung nur nach physischem Setup."); return }
                val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                mark(PairingTrace.Stage.PAIRING_VARIANT, variant)
                if (!_state.value.connecting || _state.value.connected || !session.pairing(session.generation, variant)) {
                    mark(PairingTrace.Stage.UNSUPPORTED_PAIRING, variant)
                    disconnect("Nicht unterstützte Android-Kopplungsart ($variant). Nur die Eingabe des sechsstelligen Display-Codes ist zulässig; am Orb Setup neu starten.")
                } else if (variant == SetupSession.CONSENT) {
                    // AOSP routes BLE security requests to CONSENT before the separate passkey request.
                    // Consent is NEVER authorization. Close if no PIN/passkey input follows promptly.
                    mark(PairingTrace.Stage.CONSENT_PENDING)
                    consentTimer?.cancel()
                    val epoch = session.generation
                    consentTimer = scope.launch {
                        delay(30_000)
                        if (session.accepts(epoch) && session.consentPending) {
                            mark(PairingTrace.Stage.CONSENT_TIMEOUT)
                            disconnect("Android meldete nur Kopplungszustimmung, keine Display-Code-Eingabe. Unsichere Kopplung nicht übernommen; Orb-Setup neu starten.")
                        }
                    }
                    _state.update { it.copy(status = "Android-Kopplungsanfrage: nur fortfahren, wenn danach die Eingabe des sechsstelligen Orb-Display-Codes erscheint. Zustimmung allein genügt nicht.") }
                } else {
                    consentTimer?.cancel(); consentTimer = null
                    _state.update { it.copy(status = "Den sechsstelligen Code vom Orb jetzt im Android-Kopplungsdialog eingeben.") }
                }
            }
        }
    }
    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    companion object {
        /** Android combines these service filters with OR semantics; neither is proof of ownership. */
        internal fun discoveryFilters(): List<ScanFilter> = listOf(
            SetupProtocol.SERVICE, CompanionProtocol.SERVICE
        ).map { ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build() }
    }

    fun startScan() {
        if (closed) return
        if (!foreground) {
            newScanDiagnostic("foreground_blocked")
            _state.update { it.copy(status = "Suche nur im Vordergrund möglich. Kopplungsansicht öffnen und erneut suchen.") }
            return
        }
        stop()
        newScanDiagnostic("start_requested")
        if (!allowed()) { scanStage("permission_blocked"); reportPermissionDenied(); return }
        val scanner = runCatching { adapter?.bluetoothLeScanner }.getOrNull()
        if (scanner == null) {
            scanStage("bluetooth_blocked")
            _state.update { it.copy(status = "Bluetooth einschalten, dann erneut suchen.") }
            return
        }
        try { registerReceiver() } catch (_: Exception) {
            scanStage("receiver_blocked")
            stop("Bluetooth-Ereignisse konnten nicht registriert werden. Berechtigungen prüfen.")
            return
        }
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                main.post {
                    if (scan !== this || closed) return@post
                    val uuids = result.scanRecord?.serviceUuids.orEmpty()
                    scanStage("callback", { d -> d.copy(callbackCount = d.callbackCount + 1,
                        setupCount = d.setupCount + if (uuids.contains(ParcelUuid(SetupProtocol.SERVICE))) 1 else 0,
                        companionCount = d.companionCount + if (uuids.contains(ParcelUuid(CompanionProtocol.SERVICE))) 1 else 0,
                        unknownCount = d.unknownCount + if (uuids.none { it.uuid == SetupProtocol.SERVICE || it.uuid == CompanionProtocol.SERVICE }) 1 else 0) })
                    try {
                        val item = SetupDevice(result.device.address, result.rssi)
                        _state.update { it.copy(devices = (it.devices.filterNot { d -> d.address == item.address } + item).sortedByDescending { d -> d.rssi }) }
                    } catch (_: SecurityException) { scanStage("permission_revoked"); reportPermissionDenied() }
                }
            }
            override fun onScanFailed(code: Int) { main.post { if (scan === this) {
                scanStage("scan_failed") { it.copy(failureCode = code) }
                stopScan()
                _state.update { it.copy(status = "BLE-Suche vom Android-System abgewiesen ($code). Bluetooth prüfen, dann Suche erneut starten.") }
            } } }
        }
        scan = cb
        _state.update { SetupState(scanning = true, status = "Suche nach SI-M Orb …", pairingTrace = trace.snapshot(), scanDiagnostic = scanDiagnostic?.snapshot(com.example.BuildConfig.VERSION_NAME).orEmpty()) }
        try {
            scanner.startScan(discoveryFilters(),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
            if (scan === cb) scanStage("platform_return_ok") // Android may still reject asynchronously.
            scanTimer = scope.launch {
                delay(12_000)
                if (scan === cb) {
                    scanStage("timeout")
                    stopScan()
                    _state.update { it.copy(status = if (it.devices.isEmpty())
                        "Suche beendet: keine passenden BLE-Anzeigen empfangen. Diagnose kopieren; Bluetooth/Standortstatus und Scan-Start prüfen."
                    else "Orb gefunden. Auswählen; für einen bereits zugeordneten Orb ist ein SI-M-Eigentumsnachweis nötig.") }
                }
            }
        } catch (_: Exception) {
            scanStage("platform_start_exception")
            stop("BLE-Suche konnte nicht gestartet werden. Berechtigungen und Bluetooth prüfen.")
        }
    }
    private fun stopScan() {
        val old = scan; scan = null
        scanTimer?.cancel(); scanTimer = null
        if (old != null) runCatching { adapter?.bluetoothLeScanner?.stopScan(old) }
        _state.update { it.copy(scanning = false) }
    }

    fun connect(address: String) = connectInternal(address, false)

    /** No discovery and no createBond: only the persisted, bonded owner is a retry candidate. */
    fun connectTrusted(): Boolean {
        val address = trust.address ?: return false
        if (!foreground || !allowed() || !enabled() || closed) return false
        reconnectArmed = true
        retryAttempt = 0
        retryGeneration++
        retryJob?.cancel(); retryJob = null
        connectInternal(address, true)
        return true
    }
    private fun connectTrustedAttempt() {
        val address = trust.address ?: return
        if (foreground && allowed() && enabled() && !closed) connectInternal(address, true)
    }

    private fun connectInternal(address: String, trusted: Boolean) {
        trace.reset()
        mark(PairingTrace.Stage.TAP)
        if (closed || !foreground) return
        if (!allowed()) { reportPermissionDenied(); return }
        if (!trusted && _state.value.devices.none { it.address == address }) {
            stop("Gerät nicht mehr in der aktuellen Suche. Bitte erneut suchen.")
            return
        }
        mark(PairingTrace.Stage.PERMISSION_OK)
        stopScan(); disconnect("Verbinde mit Orb …")
        trustedReconnect = trusted
        // Publish before dispatch: even a stalled/cancelled job is visible at the tap boundary.
        _state.update { it.copy(connecting = true, status = "Verbinde mit Orb … Code am Display bereithalten.") }
        val epoch = session.generation
        connectJob = scope.launch {
            mark(PairingTrace.Stage.CONNECT_JOB)
            var trustedRoute = trusted
            try {
                val peer = adapter?.getRemoteDevice(address) ?: error("Bluetooth einschalten.")
                if (trusted && !trust.matches(peer)) throw StaleBond()
                if (!trusted && peer.bondState == BluetoothDevice.BOND_BONDED && !trust.matches(peer)) {
                    mark(PairingTrace.Stage.STALE_BOND)
                    throw StaleBond()
                }
                // A manual scan tap may select the persisted bonded owner. It must
                // take the same mutual-proof path, never fresh enrollment.
                trustedRoute = trusted || trust.matches(peer)
                trustedReconnect = trustedRoute
                device = peer; registerReceiver()
                withTimeout(if (trustedRoute) 12_000 else 150_000) {
                    connectionDone = CompletableDeferred()
                    mark(PairingTrace.Stage.GATT_START)
                    gatt = peer.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                    val g = gatt ?: error("Bluetooth-Verbindung konnte nicht gestartet werden.")
                    connectionDone!!.await(); connectionDone = null
                    discoveryDone = CompletableDeferred()
                    mark(PairingTrace.Stage.DISCOVERY_START)
                    check(g.discoverServices()) { "Service-Suche konnte nicht gestartet werden." }
                    discoveryDone!!.await(); discoveryDone = null
                    val companionService = g.getService(CompanionProtocol.SERVICE)
                    if (companionService != null) mark(PairingTrace.Stage.COMPANION_DISCOVERED)
                    companionCharacteristic = companionService?.getCharacteristic(CompanionProtocol.STATUS)
                    enrollCharacteristic = companionService?.getCharacteristic(CompanionProtocol.ENROLL)
                    authCharacteristic = companionService?.getCharacteristic(CompanionProtocol.AUTH)
                    if (trustedRoute) {
                        mark(PairingTrace.Stage.TRUSTED_RECONNECT)
                        check(companionCharacteristic != null && authCharacteristic != null &&
                            companionCharacteristic!!.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 &&
                            companionCharacteristic!!.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) { "Vertrauens-Service fehlt oder ist unvollständig." }
                        negotiateProofMtu(g)
                        val key = trust.key() ?: error("Gespeicherter Owner-Proof fehlt.")
                        try { proofSession = mutualProof(key) } finally { key.fill(0) }
                        val proof = readCompanion()
                        mark(PairingTrace.Stage.COMPANION_READ)
                        check(proof.owned && trust.matches(peer) && proofSession?.accept(proof) == true) { "Orb-Besitz nicht bestätigt." }
                        mark(PairingTrace.Stage.STATUS_VERIFIED)
                        publishCompanion(proof, true)
                        reconnectArmed = true
                        retryAttempt = 0
                        if (proof.setupOpen) bindLegacyChannels(g)
                        enableCompanionNotifications(g)
                        return@withTimeout
                    }
                    val service = g.getService(SetupProtocol.SERVICE) ?: error("Dieses Gerät bietet nicht den SI-M-Setup-Service.")
                    commandCharacteristic = service.getCharacteristic(SetupProtocol.COMMAND)
                    statusCharacteristic = service.getCharacteristic(SetupProtocol.STATUS)
                    val cmd = commandCharacteristic ?: error("Orb-Schreibkanal fehlt.")
                    val status = statusCharacteristic ?: error("Orb-Statuskanal fehlt.")
                    check(cmd.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 &&
                        status.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 &&
                        status.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) { "Unpassende Orb-GATT-Eigenschaften." }
                    mark(PairingTrace.Stage.SERVICE_FOUND)
                    if (companionCharacteristic == null && !session.passkeyObserved && peer.bondState == BluetoothDevice.BOND_NONE) {
                        mark(PairingTrace.Stage.BOND_START)
                        check(peer.createBond()) { "Android-Kopplung konnte nicht gestartet werden." }
                    }
                    if (companionCharacteristic != null) {
                        check(enrollCharacteristic != null && authCharacteristic != null) { "Enrollment/Auth-Kanal fehlt." }
                        // SC firmware triggers system pairing at the first authenticated read.
                        var proof: CompanionStatus? = null
                        while (proof == null) {
                            try { proof = readCompanion(120_000) }
                            catch (e: GattFailure) {
                                if (e.code != BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION &&
                                    e.code != BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) throw e
                                delay(250)
                            }
                        }
                        mark(PairingTrace.Stage.COMPANION_READ)
                        check(proof.setupOpen && !proof.owned && proof.seq == 0 && session.passkeyObserved &&
                            companionReadProofRevision == session.inputRevision &&
                            peer.bondState == BluetoothDevice.BOND_BONDED) { "Frische sichere Kopplung und offenes Setup fehlen." }
                        // Unsigned capability probe cannot display untrusted character/mode.
                        _state.update { it.copy(companionSupported = true, setupOpen = true) }
                        enableCompanionNotifications(g)
                        return@withTimeout
                    }
                    while (true) {
                        try {
                            mark(PairingTrace.Stage.PROTECTED_READ_START)
                            setupReadProofRevision = -1L
                            val initial = readStatus(120_000, epoch) // Android system dialog and human passkey entry
                            try { if (initial.isNotEmpty()) decodeStatus(initial) }
                            finally { initial.fill(0) }
                            break
                        } catch (e: GattFailure) {
                            if (e.code != BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION && e.code != BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) throw e
                            delay(250)
                        }
                    }
                    check(session.accepts(epoch) && session.passkeyObserved && session.protectedReadSucceeded &&
                        setupReadProofRevision == session.inputRevision) { "Keine frische sichere Passkey-Eingabe vor Statusantwort beobachtet. Orb-Setup neu öffnen und erneut koppeln." }
                    mtuDone = CompletableDeferred()
                    mark(PairingTrace.Stage.MTU_START)
                    mtu = if (g.requestMtu(517)) withTimeout(5_000) { mtuDone!!.await() } else 23
                    mtuDone = null
                    val cccd = status.getDescriptor(SetupProtocol.CCCD) ?: error("Orb-Statusbenachrichtigung fehlt.")
                    check(g.setCharacteristicNotification(status, true)) { "Orb-Statusbenachrichtigung nicht aktiviert." }
                    operations.withLock {
                        descriptorDone = CompletableDeferred()
                        mark(PairingTrace.Stage.CCCD_START)
                        val started = if (Build.VERSION.SDK_INT >= 33)
                            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                        else { @Suppress("DEPRECATION") cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; @Suppress("DEPRECATION") g.writeDescriptor(cccd) }
                        check(started) { "Statusbenachrichtigung konnte nicht eingerichtet werden." }
                        awaitGatt(5_000) { descriptorDone!!.await() }; descriptorDone = null
                    }
                }
                if (!foreground) {
                    disconnect("Kopplungsdialog verlassen. Zur Sicherheit Einrichtung beendet; am Orb neu starten.")
                    return@launch
                }
                mark(PairingTrace.Stage.CONNECTED)
                _state.update { it.copy(connecting = false, connected = true,
                    needsPhysicalConfirmation = !trustedRoute, status = if (trustedRoute) "Vertrauensstatus gelesen. Steuerung nur im physischen Setup-Fenster." else
                        "Statuszugriff erfolgreich. Bestätige unten, dass du den Code von diesem Orb im Androiddialog eingegeben hast.") }
                if (trustedRoute && _state.value.authorized) refreshManagement()
                keepAlive = scope.launch {
                    while (session.accepts(epoch) && gatt != null) {
                        delay(10_000)
                        if (!commands.isLocked) try {
                            if (companionCharacteristic != null) refreshCompanion() else readStatus().fill(0)
                        } catch (_: Exception) {
                            if (session.accepts(epoch)) {
                                val wasTrusted = trustedReconnect
                                disconnect("Statuszugriff verloren.")
                                if (wasTrusted) scheduleReconnect()
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (session.accepts(epoch)) {
                    if (e is TimeoutCancellationException) mark(PairingTrace.Stage.TIMEOUT)
                    disconnect(if (e is TimeoutCancellationException) if (trustedRoute) "Orb momentan nicht erreichbar." else
                        "Kopplung nach 150 Sekunden abgelaufen. Letzte Stufe in Diagnose ansehen; Orb-Setup neu starten." else "Einrichtung abgebrochen.")
                    if (trustedRoute && e is TimeoutCancellationException) scheduleReconnect()
                }
                throw e
            } catch (e: Exception) {
                if (session.accepts(epoch)) {
                    mark(PairingTrace.Stage.FAILED)
                    disconnect(if (e is StaleBond) if (trusted) "Gespeicherte Vertrauensbindung nicht mehr vorhanden. Neues physisches Enrollment erforderlich." else "Android-Kopplung vorhanden, aber SI-M-Owner-Proof fehlt. Löschen am Telefon entfernt einen möglichen Orb-Owner nicht; am Orb die bestätigte Kopplungs-Wiederherstellung nutzen." else
                        if (e is SecurityException) "Bluetooth-Berechtigung während der Verbindung verloren." else
                        if (e is GattFailure) "Bluetooth-GATT-Fehler (${e.code}). Letzte Stufe in Diagnose ansehen." else
                            "Bluetooth-Verbindung fehlgeschlagen. Letzte Stufe in Diagnose ansehen.")
                    if (trustedRoute && e !is StaleBond && e !is SecurityException) scheduleReconnect()
                }
            }
        }
    }

    fun confirmPhysicalPairing() {
        if (!foreground || !_state.value.connected || !session.passkeyObserved ||
            (companionCharacteristic == null && !session.protectedReadSucceeded)) return
        if (companionCharacteristic != null) {
            val epoch = session.generation
            commandJob = scope.launch {
                try {
                    val before = readCompanion()
                    check(session.accepts(epoch) && before.setupOpen && !before.owned && before.seq == 0 &&
                    companionReadProofRevision == session.inputRevision &&
                    device?.bondState == BluetoothDevice.BOND_BONDED)
                    operations.withLock {
                        val g = gatt ?: error("Verbindung beendet.")
                        val attr = enrollCharacteristic ?: error("Enrollment-Kanal fehlt.")
                        enrollWrite = CompletableDeferred()
                        val started = if (Build.VERSION.SDK_INT >= 33)
                            g.writeCharacteristic(attr, CompanionProtocol.CONFIRM_OWNER, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                        else {
                            @Suppress("DEPRECATION") attr.value = CompanionProtocol.CONFIRM_OWNER
                            @Suppress("DEPRECATION") g.writeCharacteristic(attr)
                        }
                        check(started) { "Enrollment konnte nicht gestartet werden." }
                        mark(PairingTrace.Stage.OWNER_ENROLL_WRITE)
                        awaitGatt(5_000) { enrollWrite!!.await() }
                        enrollWrite = null
                    }
                    negotiateProofMtu(gatt ?: error("Verbindung beendet."))
                    val key = secureAttributeRead(enrollCharacteristic ?: error("Enrollment-Kanal fehlt."))
                    try {
                        check(key.size == 32 && session.accepts(epoch) && before.setupOpen)
                        proofSession = mutualProof(key)
                        val after = readCompanion()
                        val peer = device ?: error("Verbindung beendet.")
                        check(session.accepts(epoch) && after.owned && after.setupOpen &&
                            peer.bondState == BluetoothDevice.BOND_BONDED && proofSession?.accept(after) == true)
                        mark(PairingTrace.Stage.STATUS_VERIFIED)
                        check(trust.enroll(peer, key)) { "Owner konnte nicht gespeichert werden." }
                        reconnectArmed = true
                        retryAttempt = 0
                        mark(PairingTrace.Stage.OWNER_VERIFIED)
                        publishCompanion(after, true)
                        _state.update { it.copy(needsPhysicalConfirmation = false, status = "Owner bestätigt; physisches Setup offen.") }
                        bindLegacyChannels(gatt ?: error("Verbindung beendet."))
                        perform(SetupCommand.Networks, followSettings = true)
                    } finally { key.fill(0) }
                } catch (_: Exception) {
                    if (session.accepts(epoch)) disconnect("Enrollment nicht bestätigt. Orb-Setup erneut öffnen; keine Vertrauensbindung übernommen.")
                }
            }
            return
        }
        session.confirmPhysicalCode(session.generation)
        _state.update { it.copy(authorized = session.authorized, needsPhysicalConfirmation = false, status = "Einrichtung freigegeben. WLAN und Geräteeinstellungen abrufen.") }
        perform(SetupCommand.Networks, followSettings = true)
    }

    private suspend fun negotiateProofMtu(g: BluetoothGatt) {
        mtuDone = CompletableDeferred()
        try {
            check(g.requestMtu(128)) { "Orb-Proof benötigt MTU ≥68." }
            mtu = awaitGatt(5_000) { mtuDone!!.await() }
            check(mtu >= 68) { "Orb-Proof benötigt MTU ≥68." }
        } finally { mtuDone = null }
    }

    private suspend fun secureAttributeRead(attr: BluetoothGattCharacteristic): ByteArray = operations.withLock {
        val g = gatt ?: error("Verbindung beendet.")
        val pending = CompletableDeferred<ByteArray>(); secureRead = pending; secureReadUuid = attr.uuid
        try {
            check(g.readCharacteristic(attr)) { "Geschützter Read konnte nicht gestartet werden." }
            awaitGatt(5_000) { pending.await() }
        } finally {
            if (secureRead === pending) { secureRead = null; secureReadUuid = null }
            if (!pending.isCompleted) pending.cancel()
        }
    }

    private suspend fun secureAttributeWrite(attr: BluetoothGattCharacteristic, bytes: ByteArray) = operations.withLock {
        val g = gatt ?: error("Verbindung beendet.")
        val pending = CompletableDeferred<Unit>(); secureWrite = pending
        try {
            val started = if (Build.VERSION.SDK_INT >= 33)
                g.writeCharacteristic(attr, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            else {
                @Suppress("DEPRECATION") attr.value = bytes
                @Suppress("DEPRECATION") g.writeCharacteristic(attr)
            }
            check(started) { "Geschützter Write konnte nicht gestartet werden." }
            awaitGatt(5_000) { pending.await() }
        } finally { if (secureWrite === pending) secureWrite = null }
    }

    private suspend fun mutualProof(key: ByteArray): CompanionProofSession {
        val attr = authCharacteristic ?: error("Auth-Kanal fehlt.")
        val appNonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
        var deviceNonce: ByteArray? = null
        try {
            secureAttributeWrite(attr, byteArrayOf(1) + appNonce)
            mark(PairingTrace.Stage.PROOF_CHALLENGE)
            val reply = secureAttributeRead(attr)
            try {
                check(reply.size == 65 && reply[0] == 1.toByte()) { "Ungültige Auth-Antwort." }
                deviceNonce = reply.copyOfRange(1, 33)
                val expected = OrbProof.role(key, "KI-M-ORB-PROOF-V1", appNonce, deviceNonce!!)
                try { check(MessageDigest.isEqual(expected, reply.copyOfRange(33, 65))) { "Orb-Proof ungültig." } }
                finally { expected.fill(0) }
            } finally { reply.fill(0) }
            val clientProof = OrbProof.role(key, "KI-M-PHONE-PROOF-V1", appNonce, deviceNonce!!)
            try { secureAttributeWrite(attr, byteArrayOf(2) + clientProof) } finally { clientProof.fill(0) }
            mark(PairingTrace.Stage.PROOF_VERIFIED)
            return CompanionProofSession(key.copyOf(), appNonce.copyOf(), deviceNonce!!.copyOf())
        } finally { appNonce.fill(0); deviceNonce?.fill(0) }
    }

    private fun bindLegacyChannels(g: BluetoothGatt) {
        val service = g.getService(SetupProtocol.SERVICE) ?: return
        commandCharacteristic = service.getCharacteristic(SetupProtocol.COMMAND)
        statusCharacteristic = service.getCharacteristic(SetupProtocol.STATUS)
    }

    private suspend fun readCompanion(timeoutMs: Long = 5_000): CompanionStatus = operations.withLock {
        val g = gatt ?: error("Orb nicht verbunden.")
        val attr = companionCharacteristic ?: error("Companion-Status fehlt.")
        val pending = CompletableDeferred<ByteArray>(); companionRead = pending
        companionReadProofRevision = -1L
        try {
            mark(PairingTrace.Stage.COMPANION_READ_START)
            check(g.readCharacteristic(attr)) { "Companion-Read konnte nicht gestartet werden." }
            val raw = awaitGatt(timeoutMs) { pending.await() }
            try { CompanionProtocol.decode(raw) } finally { raw.fill(0) }
        } finally {
            if (companionRead === pending) companionRead = null
            if (!pending.isCompleted) pending.cancel()
        }
    }

    private suspend fun enableCompanionNotifications(g: BluetoothGatt) {
        val attr = companionCharacteristic ?: error("Companion-Status fehlt.")
        val cccd = attr.getDescriptor(SetupProtocol.CCCD) ?: error("Companion-CCCD fehlt.")
        check(g.setCharacteristicNotification(attr, true))
        operations.withLock {
            descriptorDone = CompletableDeferred()
            val started = if (Build.VERSION.SDK_INT >= 33)
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            else {
                @Suppress("DEPRECATION") cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION") g.writeDescriptor(cccd)
            }
            check(started)
            try { awaitGatt(5_000) { descriptorDone!!.await() } } finally { descriptorDone = null }
        }
    }

    private fun publishCompanion(proof: CompanionStatus, owner: Boolean) {
        val accepted = owner && proofSession != null && proof.owned && trust.address != null && device?.let(trust::matches) == true
        val open = accepted && proof.setupOpen && foreground
        val closing = !open && session.authorized
        val opening = open && !session.authorized
        if (closing) {
            windowEpoch++
            pendingBootstrapEpoch = null
            mark(PairingTrace.Stage.WINDOW_CLOSED)
            companionRevision++
        } else if (opening) {
            windowEpoch++
        }
        session.setTrustedWindow(open)
        if (!open) { commandCharacteristic = null; statusCharacteristic = null }
        _state.update { it.copy(companionSupported = true, trusted = accepted, setupOpen = proof.setupOpen,
            companionCharacterId = proof.characterId, companionMode = proof.mode, authorized = open,
            languageGeneration = if (closing || opening) it.languageGeneration + 1 else it.languageGeneration,
            deviceLanguage = if (closing || opening) null else it.deviceLanguage,
            languageUnsupported = if (closing || opening) false else it.languageUnsupported,
            languageError = if (closing || opening) null else it.languageError,
            aps = if (open) it.aps else emptyList(), savedNetworks = if (open) it.savedNetworks else emptyList(),
            currentNetwork = if (open) it.currentNetwork else "", wifiConnected = open && it.wifiConnected,
            wifiStatusKnown = open && it.wifiStatusKnown, savedNetworksKnown = open && it.savedNetworksKnown,
            settings = if (open) it.settings else null, characters = if (open) it.characters else emptyList(),
            avatarSupported = open && it.avatarSupported, avatarDna = if (open) it.avatarDna else null,
            avatarStatus = if (open) it.avatarStatus else "Nur letzte bestätigte Auswahl; Avatar-DNA nach Öffnen erneut lesen.",
            managementError = if (open) it.managementError else null,
            agentProfile = if (open) it.agentProfile else null,
            agentStatus = if (open) it.agentStatus else "Agent/Sprachdienst nur bei offener Orb Kopplung lesbar.",
            status = if (!open && accepted) "Orb verbunden · nur lesbar. Am Orb physische Orb Kopplung starten (kein Reset, keine erneute Android-Kopplung)." else it.status) }
        if (closing) {
            val oldCommand = commandJob
            commandJob = null
            oldCommand?.cancel()
        }
    }

    private suspend fun refreshCompanion() {
        val epoch = session.generation
        if (!foreground || companionCharacteristic == null || gatt == null || proofSession == null) return
        try {
            val proof = readCompanion()
            if (!session.accepts(epoch)) return
            if (proofSession?.accept(proof) != true) return
            val owner = device?.let(trust::matches) == true
            if (owner && !proof.owned) { disconnect("Owner-Status widerrufen."); return }
            val opening = owner && proof.setupOpen && !_state.value.authorized
            publishCompanion(proof, owner)
            if (owner && proof.setupOpen && commandCharacteristic == null) bindLegacyChannels(gatt ?: return)
            if (opening && _state.value.connected) {
                pendingBootstrapEpoch = windowEpoch
                drainPendingBootstrap()
            }
        } catch (e: Exception) {
            if (session.accepts(epoch)) {
                val recoverable = trustedReconnect && (e is GattFailure || e is TimeoutCancellationException)
                disconnect("Companion-Status nicht mehr verifizierbar.")
                if (recoverable) scheduleReconnect()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun readStatus(timeoutMs: Long = 5_000, setupEpoch: Long? = null): ByteArray = operations.withLock {
        val g = gatt ?: error("Orb nicht verbunden.")
        val status = statusCharacteristic ?: error("Statuskanal fehlt.")
        val pending = CompletableDeferred<ByteArray>(); readDone = pending
        if (setupEpoch != null) { setupRead = pending; setupReadEpoch = setupEpoch }
        var delivered = false
        try {
            check(g.readCharacteristic(status)) { "Status-Read konnte nicht gestartet werden." }
            val result = awaitGatt(timeoutMs) { pending.await() }
            // Test probe sits after callback completion but before connect() resumes.
            if (setupEpoch != null) afterSetupReadCompletion?.invoke()
            delivered = true
            result
        } finally {
            if (setupRead === pending) { setupRead = null; setupReadEpoch = -1L }
            if (readDone === pending) readDone = null
            if (!delivered && pending.isCompleted && !pending.isCancelled) pending.getCompleted().fill(0)
            if (!pending.isCompleted) pending.cancel()
        }
    }

    private suspend fun write(bytes: ByteArray) = operations.withLock {
        val g = gatt ?: error("Orb nicht verbunden.")
        check(foreground && session.authorized) { "Physisches Setup-Fenster geschlossen." }
        val characteristic = commandCharacteristic ?: error("Schreibkanal fehlt.")
        val longWrite = bytes.size > mtu - 3
        val pending = CompletableDeferred<Unit>(); writeDone = pending
        try {
            if (longWrite) check(g.beginReliableWrite()) { "Atomarer Long-Write wird nicht unterstützt. Höheres MTU oder kürzere WLAN-Daten erforderlich." }
            val started = if (Build.VERSION.SDK_INT >= 33)
                g.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            else {
                @Suppress("DEPRECATION") characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION") characteristic.value = bytes
                @Suppress("DEPRECATION") g.writeCharacteristic(characteristic)
            }
            check(started) { "Orb-Befehl konnte nicht gesendet werden." }
            awaitGatt(5_000) { pending.await() }
            if (longWrite) {
                reliableDone = CompletableDeferred()
                check(g.executeReliableWrite()) { "Long-Write konnte nicht abgeschlossen werden." }
                awaitGatt(5_000) { reliableDone!!.await() }
            }
            lastWriteAck = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            if (longWrite) runCatching { g.abortReliableWrite() }
            throw e
        } finally {
            if (writeDone === pending) writeDone = null
            reliableDone = null
            if (Build.VERSION.SDK_INT < 33) { @Suppress("DEPRECATION") characteristic.value = byteArrayOf() }
        }
    }

    private suspend fun send(command: SetupCommand): SetupReply = commands.withLock {
        check(session.authorized && _state.value.connected) { "Orb zuerst mit frischem Display-Code koppeln und bestätigen." }
        val epoch = session.generation
        val operationWindow = windowEpoch
        if (companionCharacteristic != null) {
            val proof = readCompanion()
            val verifier = proofSession ?: error("Companion-Proof fehlt.")
            verifier.accept(proof) // false means a verified duplicate; malformed/stale proofs throw
            if (!proof.owned || !proof.setupOpen || device?.let(trust::matches) != true) {
                publishCompanion(proof, false)
                error("Physisches Setup-Fenster geschlossen.")
            }
            publishCompanion(proof, true)
        }
        val admissionRevision = companionRevision
        val id = session.begin()
        var bytes: ByteArray? = null
        try {
            val payload = SetupProtocol.encode(id, command)
            bytes = payload
            delay((250 - (SystemClock.elapsedRealtime() - lastWriteAck)).coerceAtLeast(0))
            check(session.accepts(epoch) && session.authorized && operationWindow == windowEpoch && admissionRevision == companionRevision && foreground)
            mutationPending = command is SetupCommand.Save || command is SetupCommand.Forget ||
                command is SetupCommand.Volume || command is SetupCommand.Brightness || command is SetupCommand.Sleep ||
                command is SetupCommand.CharacterSet || command is SetupCommand.LanguageSet || command is SetupCommand.AvatarSet || command == SetupCommand.AvatarDelete
            withTimeout(20_000) {
                check(session.authorized && operationWindow == windowEpoch && admissionRevision == companionRevision && foreground)
                write(payload)
                _state.update { it.copy(status = "Orb hat den Befehl angenommen; warte auf tatsächliches Ergebnis …") }
                while (true) {
                    val raw = readStatus()
                    val reply = try { decodeStatus(raw) } finally { raw.fill(0) }
                    if (session.authorized && operationWindow == windowEpoch && admissionRevision == companionRevision && session.acceptsReply(epoch, reply)) {
                        if (reply.state == "error") throw FirmwareFailure(checkNotNull(reply.code))
                        if (reply.state == "done") {
                            SetupProtocol.validateReply(command, reply)
                            return@withTimeout reply
                        }
                    }
                    withTimeoutOrNull(500) { statusHint.receive() }
                }
                @Suppress("UNREACHABLE_CODE") error("No terminal status")
            }
        } finally {
            bytes?.fill(0)
            session.finish(epoch, id)
        }
    }

    /** Additive optional operation; a rejected read must never disconnect legacy setup. */
    private suspend fun sendAgent(command: AgentCommand): AgentReply = commands.withLock {
        check(foreground && session.authorized && _state.value.connected)
        val epoch = session.generation
        val window = windowEpoch
        if (companionCharacteristic != null) {
            val proof = readCompanion()
            val verifier = proofSession ?: error("Orb-Proof fehlt.")
            verifier.accept(proof)
            if (!proof.owned || !proof.setupOpen || device?.let(trust::matches) != true) {
                publishCompanion(proof, false)
                error("Physisches Setup-Fenster geschlossen.")
            }
            publishCompanion(proof, true)
        }
        val revision = companionRevision
        val id = session.begin()
        var bytes: ByteArray? = null
        try {
            bytes = AgentProtocol.encode(id, command)
            delay((250 - (SystemClock.elapsedRealtime() - lastWriteAck)).coerceAtLeast(0))
            check(foreground && session.accepts(epoch) && session.authorized && window == windowEpoch && revision == companionRevision)
            if (command != AgentCommand.Get && command != AgentCommand.Caps) mutationPending = true
            withTimeout(20_000) {
                write(checkNotNull(bytes))
                while (true) {
                    val raw = readStatus()
                    val reply = try { AgentProtocol.decode(raw) } catch (_: Exception) {
                        throw IllegalStateException("Ungültiger Orb-Agent-Status.")
                    } finally { raw.fill(0) }
                    if (foreground && session.authorized && session.accepts(epoch) && window == windowEpoch &&
                        revision == companionRevision && reply.id == session.pendingId) {
                        if (reply.state == "error") throw FirmwareFailure(checkNotNull(reply.code))
                        if (reply.state == "done") {
                            check(when (command) {
                                AgentCommand.Get -> reply.profile != null && reply.resetUnconfigures == null
                                AgentCommand.Caps -> reply.resetUnconfigures == true && reply.profile == null
                                else -> reply.profile == null && reply.resetUnconfigures == null
                            }) { "Agent-Antwort unvollständig." }
                            return@withTimeout reply
                        }
                    }
                    withTimeoutOrNull(500) { statusHint.receive() }
                }
                @Suppress("UNREACHABLE_CODE") error("No terminal agent status")
            }
        } finally {
            bytes?.fill(0)
            session.finish(epoch, id)
        }
    }

    fun performAgent(command: AgentCommand): Long? {
        if (closed || !foreground || !session.authorized || !_state.value.connected || _state.value.busy ||
            (command != AgentCommand.Get && _state.value.agentUnsupported)) return null
        if (command == AgentCommand.Reset && !AgentProtocol.canRemove(_state.value.agentProfile)) {
            _state.update { it.copy(agentStatus = "Alter Firmware-Standard: Entfernen erfordert ein Firmware-Update. Kein Reset gesendet.") }
            return null
        }
        if (!allowed()) { reportPermissionDenied(); return null }
        try { AgentProtocol.encode(1, command).fill(0) }
        catch (_: IllegalArgumentException) {
            _state.update { it.copy(agentStatus = "Ungültige Agent-Konfiguration (HTTPS-Bridge, Route oder Zugangsdaten prüfen).") }
            return null
        }
        _state.update { it.copy(busy = true, agentStatus = "Warte auf Gerätebestätigung …") }
        val epoch = session.generation
        val window = windowEpoch
        val write = if (command is AgentCommand.Set) WriteOutcome(++writeRequest, epoch, "agent", false).also { activeWrite = it } else null
        val operation = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (command == AgentCommand.Reset) {
                    try {
                        check(sendAgent(AgentCommand.Caps).resetUnconfigures == true)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { throw ResetCapabilityUnavailable() }
                    // A new signed opening cannot inherit authority from this capability read.
                    check(session.accepts(epoch) && window == windowEpoch && foreground && session.authorized)
                }
                val reply = sendAgent(command)
                val profile = if (command == AgentCommand.Get) checkNotNull(reply.profile)
                    else checkNotNull(sendAgent(AgentCommand.Get).profile)
                if (command is AgentCommand.Set) check(profile.source == "custom" && profile.url == command.url) { "Agent-Konfiguration nicht bestätigt." }
                if (command == AgentCommand.Reset) check(profile.source == "none" && !profile.configured && profile.url.isEmpty()) { "Entfernung nicht bestätigt." }
                if (session.accepts(epoch) && window == windowEpoch && foreground && session.authorized) {
                    mutationPending = false
                    finishWrite(write, true)
                    _state.update { it.copy(agentProfile = profile, agentUnsupported = false,
                        agentStatus = when (command) {
                            AgentCommand.Get -> "Agent-Profil vom Orb gelesen."
                            AgentCommand.Reset -> "Agent-Anbindung entfernt und als nicht eingerichtet vom Orb zurückgelesen."
                            else -> "Gespeichert, Sprachtest ausstehend. Vom Orb zurückgelesen."
                        }) }
                }
            } catch (e: CancellationException) {
                finishWrite(write, false)
                beforeCommandCancellationHandling?.invoke()
                throw e
            }
            catch (e: Exception) {
                finishWrite(write, false)
                if (session.accepts(epoch) && window == windowEpoch && foreground && session.authorized) {
                    val unsupported = e is FirmwareFailure && command == AgentCommand.Get
                    _state.update { it.copy(agentUnsupported = unsupported || it.agentUnsupported,
                        agentStatus = if (e is ResetCapabilityUnavailable) "Firmware-Update erforderlich: Entfernen nicht sicher bestätigt. Kein Reset gesendet; WLAN und Einstellungen bleiben nutzbar."
                            else if (unsupported) "Agent-Konfiguration von dieser Firmware nicht unterstützt. WLAN und Figuren bleiben nutzbar."
                            else "Agent-Konfiguration nicht bestätigt. Gerätestatus erneut lesen.") }
                }
            } finally {
                if (agentOperation === coroutineContext[Job]) {
                    agentOperation = null
                    if (session.accepts(epoch)) {
                        _state.update { it.copy(busy = false) }
                        drainPendingBootstrap()
                    }
                }
            }
        }
        agentOperation = operation
        commandJob = operation
        operation.start()
        return write?.request
    }

    private fun decodeStatus(raw: ByteArray): SetupReply = try { SetupProtocol.decode(raw) } catch (_: Exception) {
        // Parser exceptions may contain the raw JSON. Never surface or log that input.
        throw IllegalStateException("Orb-Status unvollständig oder ungültig. Keine Erfolgsmeldung übernommen.")
    }

    private fun publishAvatarReadback(command: SetupCommand, installed: SetupReply, selected: SetupReply,
                                      epoch: Long, commandWindowEpoch: Long, priorSelection: String?): Boolean {
        val expected = (command as? SetupCommand.AvatarSet)?.dna
        if (!AvatarConfirmation.accept(expected, installed, selected,
                session.accepts(epoch) && commandWindowEpoch == windowEpoch && foreground &&
                    _state.value.authorized, priorSelection)) return false
        _state.update { it.copy(avatarDna = expected, selectedCharacter = selected.selected,
            avatarStatus = if (expected == null) "Avatar auf dem Gerät gelöscht; lokale Favoriten bleiben erhalten." else
                "Orb bestätigt: DNA und aktive Auswahl zurückgelesen.") }
        return true
    }

    fun perform(command: SetupCommand, followSettings: Boolean = false, managementRefresh: Boolean = false): Long? {
        if (closed || !foreground || _state.value.busy || !session.authorized) return null
        if (!allowed()) { reportPermissionDenied(); return null }
        if ((command is SetupCommand.AvatarSet || command == SetupCommand.AvatarDelete) && !_state.value.avatarSupported) {
            _state.update { it.copy(avatarStatus = "Orb unterstützt keinen bestätigten Avatar-Slot. Entwurf bleibt lokal.") }
            return null
        }
        if (command is SetupCommand.CharacterSet &&
            (_state.value.catalogUnsupported || _state.value.characters.none { it.id == command.characterId })) {
            _state.update { it.copy(characterStatus = "Figur nicht im gelesenen Gerätekatalog. Katalog erneut abrufen.") }
            return null
        }
        try { SetupProtocol.encode(1, command).fill(0) } catch (e: IllegalArgumentException) {
            _state.update { it.copy(status = e.message ?: "Ungültige Eingabe.") }
            return null
        }
        _state.update { it.copy(busy = true, wifiStatusKnown = if (command == SetupCommand.Networks || command is SetupCommand.Save || command is SetupCommand.Forget) false else it.wifiStatusKnown,
            savedNetworksKnown = if (command == SetupCommand.Networks || command is SetupCommand.Save || command is SetupCommand.Forget) false else it.savedNetworksKnown,
            characterStatus = when (command) {
            SetupCommand.Catalog, SetupCommand.CharacterGet -> "Figuren werden vom Gerät abgerufen …"
            is SetupCommand.CharacterSet -> "Auswahl wird auf dem Gerät gespeichert und aktiviert …"
            is SetupCommand.AvatarSet, SetupCommand.AvatarDelete -> "Orb-Avatar wird geschrieben; lese DNA und Auswahl anschließend zurück …"
            SetupCommand.AvatarCaps, SetupCommand.AvatarGet -> "Orb-Avatar wird gelesen …"
            else -> it.characterStatus
        }) }
        val epoch = session.generation
        val commandWindowEpoch = windowEpoch
        val priorSelection = _state.value.selectedCharacter
        val write = when (command) {
            is SetupCommand.Save -> WriteOutcome(++writeRequest, epoch, "wifi", false)
            is SetupCommand.AvatarSet -> WriteOutcome(++writeRequest, epoch, "avatar", false)
            is SetupCommand.CharacterSet -> WriteOutcome(++writeRequest, epoch, "character", false)
            is SetupCommand.LanguageSet -> WriteOutcome(++writeRequest, epoch, "language", false)
            else -> null
        }?.also { activeWrite = it }
        commandJob = scope.launch {
            try {
                val primary = send(command)
                // A mutation reply is only acknowledgement, never confirmed appearance.
                if (command !is SetupCommand.AvatarSet && command != SetupCommand.AvatarDelete) applyReply(command, primary)
                if (command !is SetupCommand.AvatarSet && command != SetupCommand.AvatarDelete) mutationPending = false
                if (command is SetupCommand.AvatarSet || command == SetupCommand.AvatarDelete) {
                    val installed = send(SetupCommand.AvatarGet)
                    val selected = send(SetupCommand.CharacterGet)
                    check(publishAvatarReadback(command, installed, selected, epoch, commandWindowEpoch, priorSelection)) {
                        "Avatar-DNA oder Orb-Auswahl nicht zurückgelesen."
                    }
                    mutationPending = false
                }
                // Every mutation is followed by an actual device read, never optimistic slider state.
                val readback = when (command) {
                    is SetupCommand.Save, is SetupCommand.Forget -> SetupCommand.Networks
                    is SetupCommand.Volume, is SetupCommand.Brightness, is SetupCommand.Sleep -> SetupCommand.Settings
                    is SetupCommand.CharacterSet -> SetupCommand.CharacterGet
                    is SetupCommand.LanguageSet -> SetupCommand.LanguageGet
                    else -> null
                }
                if (readback != null) {
                    applyReply(readback, send(readback))
                    if (command is SetupCommand.Save) check(command.ssid in _state.value.savedNetworks) { "WLAN nicht zurückgelesen." }
                    if (command is SetupCommand.LanguageSet) check(_state.value.deviceLanguage == command.language) { "Gerätesprache nicht bestätigt." }
                    if (command is SetupCommand.CharacterSet) check(_state.value.selectedCharacter == command.characterId) { "Figur nicht bestätigt." }
                }
                if (write != null) check(session.accepts(epoch) && commandWindowEpoch == windowEpoch &&
                    _state.value.connected && _state.value.authorized && foreground) { "Orb-Sitzung nicht mehr gültig." }
                finishWrite(write, true)
                if (followSettings) applyReply(SetupCommand.Settings, send(SetupCommand.Settings))
                if (followSettings) {
                    try { applyReply(SetupCommand.Catalog, send(SetupCommand.Catalog)) }
                    catch (e: FirmwareFailure) {
                        if (!session.accepts(epoch) || !_state.value.connected) throw e
                        _state.update { it.copy(catalogUnsupported = true, characters = emptyList(), selectedCharacter = null,
                            characterStatus = "Figurenkatalog nicht verfügbar. WLAN und Einstellungen bleiben nutzbar.") }
                    }
                    try {
                        val caps = send(SetupCommand.AvatarCaps)
                        check(caps.avatarCaps == true)
                        val avatar = send(SetupCommand.AvatarGet)
                        val selected = send(SetupCommand.CharacterGet)
                        check(avatar.avatarPresent != null && (selected.selected != "custom" || avatar.avatarPresent))
                        check(session.accepts(epoch) && commandWindowEpoch == windowEpoch && _state.value.authorized)
                        _state.update { it.copy(avatarSupported = true, avatarDna = avatar.avatarDna?.takeIf(String::isNotEmpty),
                            selectedCharacter = selected.selected, avatarStatus = "Orb-Avatarfähigkeit und Auswahl gelesen.") }
                    } catch (e: Exception) {
                        if (e is CancellationException || !session.accepts(epoch) || commandWindowEpoch != windowEpoch) throw e
                        if (e !is FirmwareFailure && !(e is GattFailure && e.code == BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH)) throw e
                        _state.update { it.copy(avatarSupported = false, avatarDna = null,
                            avatarStatus = "Ältere Firmware: Avatar-Transfer nicht verfügbar; lokale Entwürfe bleiben nutzbar.") }
                    }
                }
            } catch (e: CancellationException) {
                finishWrite(write, false)
                beforeCommandCancellationHandling?.invoke()
                if (session.accepts(epoch) && commandWindowEpoch == windowEpoch && _state.value.authorized)
                    disconnect("Einrichtung unterbrochen. Gespeicherte Daten nach neuer Kopplung prüfen.")
                throw e
            } catch (e: Exception) {
                finishWrite(write, false)
                if (session.accepts(epoch) && commandWindowEpoch == windowEpoch) {
                    if (e is FirmwareFailure) mutationPending = false
                    if (managementRefresh && _state.value.connected && _state.value.trusted && !_state.value.authorized) {
                        // The verified read-only link survives a physical window closing mid-read.
                    } else if (managementRefresh && _state.value.connected && _state.value.authorized) _state.update {
                        it.copy(wifiStatusKnown = false, savedNetworksKnown = false,
                            managementError = "WLAN, Einstellungen oder Figuren konnten nicht vollständig gelesen werden. Erneut abrufen.",
                            status = "Orb verbunden · Setup offen, Abruf fehlgeschlagen. Erneut abrufen.")
                    } else if ((command == SetupCommand.LanguageGet || command is SetupCommand.LanguageSet) && e is FirmwareFailure && _state.value.connected) _state.update {
                        it.copy(languageUnsupported = languageFailureUnsupported(e.code), deviceLanguage = null,
                            languageError = if (languageFailureUnsupported(e.code)) null else "Orb meldet Fehler ${e.code}. Sprache nicht gespeichert; erneut versuchen.")
                    } else if (command == SetupCommand.Catalog && e is FirmwareFailure && _state.value.connected) _state.update {
                        it.copy(catalogUnsupported = true, characters = emptyList(), selectedCharacter = null,
                            characterStatus = "Figurenkatalog nicht verfügbar (${e.message}). WLAN und Einstellungen bleiben nutzbar.")
                    } else if ((command is SetupCommand.AvatarSet || command == SetupCommand.AvatarDelete || command == SetupCommand.AvatarGet || command == SetupCommand.AvatarCaps) && _state.value.connected) _state.update {
                        val oldUnsupported = command == SetupCommand.AvatarCaps &&
                            (e is FirmwareFailure || (e is GattFailure && e.code == BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH))
                        it.copy(avatarDna = null, selectedCharacter = if (command is SetupCommand.AvatarSet || command == SetupCommand.AvatarDelete) null else it.selectedCharacter,
                            avatarSupported = if (oldUnsupported) false else it.avatarSupported,
                            avatarStatus = if (oldUnsupported) "Ältere Firmware: Avatar-Transfer nicht verfügbar; lokale Entwürfe bleiben nutzbar." else
                                "Orb-Avatar nicht bestätigt (${e.message}). Lokalen Entwurf behalten und erneut lesen.")
                    } else if ((command is SetupCommand.CharacterSet || command == SetupCommand.CharacterGet) && e is FirmwareFailure && _state.value.connected) _state.update {
                        it.copy(characterStatus = "Figurenbefehl fehlgeschlagen: ${e.message}. Auswahl erneut abrufen.", selectedCharacter = null)
                    } else disconnect(e.message ?: "Orb-Befehl fehlgeschlagen.")
                }
            } finally {
                if (commandJob === coroutineContext[Job]) {
                    commandJob = null
                    _state.update { it.copy(busy = false) }
                    drainPendingBootstrap()
                    if (session.accepts(epoch) && managementRefresh && commandWindowEpoch == windowEpoch && _state.value.authorized &&
                        _state.value.managementError == null && pendingBootstrapEpoch == null && !_state.value.busy)
                        performAgent(AgentCommand.Get)
                }
            }
        }
        return write?.request
    }
    fun retryLanguage() {
        if (_state.value.connected && _state.value.authorized && !_state.value.busy && !_state.value.languageUnsupported)
            _state.update { it.copy(languageError = null, deviceLanguage = null) }
    }
    /** A signed opening waits for the previous job, but never retries after a failed read. */
    private fun drainPendingBootstrap() {
        val pending = pendingBootstrapEpoch ?: return
        if (pending != windowEpoch || !foreground || !_state.value.connected || !_state.value.authorized ||
            !session.authorized || gatt == null) { pendingBootstrapEpoch = null; return }
        if (_state.value.busy || commandCharacteristic == null || statusCharacteristic == null) return
        pendingBootstrapEpoch = null
        refreshManagement()
    }
    /** One bounded read sequence per verified opening; explicit retry is the only repeat. */
    fun refreshManagement() {
        if (!foreground || !_state.value.connected || !_state.value.authorized || _state.value.busy ||
            commandCharacteristic == null || statusCharacteristic == null) return
        _state.update { it.copy(managementError = null, settings = null, characters = emptyList(),
            selectedCharacter = null, savedNetworks = emptyList(), aps = emptyList(),
            currentNetwork = "", wifiConnected = false, wifiStatusKnown = false, savedNetworksKnown = false,
            status = "WLAN, Einstellungen und Figuren werden vom Orb gelesen …") }
        perform(SetupCommand.Networks, followSettings = true, managementRefresh = true)
    }
    private fun applyReply(command: SetupCommand, reply: SetupReply) {
        if (command == SetupCommand.LanguageGet && reply.language != null) _state.update { it.copy(deviceLanguage = reply.language, languageUnsupported = false, languageError = null) }
        _state.update {
            when (command) {
                SetupCommand.Scan -> it.copy(aps = checkNotNull(reply.accessPoints) { "Orb-Suchantwort unvollständig." }, status = "WLAN-Suche abgeschlossen (Firmware: bis zu vier Treffer).")
                SetupCommand.Networks -> it.copy(savedNetworks = checkNotNull(reply.savedNetworks) { "Orb-Netzwerkantwort unvollständig." },
                    currentNetwork = checkNotNull(reply.currentNetwork), wifiConnected = checkNotNull(reply.wifiConnected),
                    wifiStatusKnown = true, savedNetworksKnown = true, status = "Gespeicherte WLAN-Namen abgerufen.")
                SetupCommand.LanguageGet -> it
                is SetupCommand.LanguageSet -> it
                SetupCommand.Settings -> it.copy(settings = checkNotNull(reply.settings) { "Orb-Einstellungen unvollständig." }, status = "Geräteeinstellungen abgerufen.")
                SetupCommand.Catalog -> it.copy(characters = checkNotNull(reply.characters), selectedCharacter = checkNotNull(reply.selected),
                    catalogUnsupported = false, characterStatus = "Installierte Figuren vom Orb abgerufen.")
                SetupCommand.CharacterGet -> it.copy(selectedCharacter = SetupProtocol.selectedInCatalog(checkNotNull(reply.selected), it.characters),
                    characterStatus = "Aktuelle Figur vom Gerät bestätigt.")
                SetupCommand.AvatarCaps -> it.copy(avatarSupported = true, avatarStatus = "Orb-Avatar-Slot bestätigt.")
                SetupCommand.AvatarGet -> it.copy(avatarDna = reply.avatarDna?.takeIf(String::isNotEmpty),
                    avatarStatus = "Orb-Avatar-DNA gelesen.")
                is SetupCommand.AvatarSet, SetupCommand.AvatarDelete -> it // only published after both DNA and selection readbacks
                is SetupCommand.CharacterSet -> it.copy(selectedCharacter = checkNotNull(reply.selected),
                    characterStatus = "Gerät bestätigt: Figur gespeichert und aktiviert.")
                is SetupCommand.Save -> it.copy(status = "WLAN auf dem Orb gespeichert. Das bestätigt noch keine WLAN-Verbindung; Netzwerkstatus abrufen.")
                is SetupCommand.Forget -> it.copy(savedNetworks = it.savedNetworks.filterNot { name -> name == command.ssid }, status = "Dieses WLAN wurde auf dem Orb entfernt.")
                else -> it.copy(status = "Einstellung auf dem Orb gespeichert. Einstellungen erneut abrufen, um den Stand zu sehen.")
            }
        }
    }

    private fun disconnect(message: String) {
        val unknown = mutationPending
        consentTimer?.cancel(); consentTimer = null
        session.invalidate()
        windowEpoch++; pendingBootstrapEpoch = null
        keepAlive?.cancel(); keepAlive = null
        val failure = IllegalStateException("Orb-Verbindung beendet.")
        connectionDone?.completeExceptionally(failure); mtuDone?.completeExceptionally(failure)
        discoveryDone?.completeExceptionally(failure); descriptorDone?.completeExceptionally(failure)
        readDone?.completeExceptionally(failure); writeDone?.completeExceptionally(failure); reliableDone?.completeExceptionally(failure)
        companionRead?.completeExceptionally(failure); enrollWrite?.completeExceptionally(failure)
        secureRead?.completeExceptionally(failure); secureWrite?.completeExceptionally(failure)
        secureRead = null; secureReadUuid = null; secureWrite = null
        proofSession?.clear(); proofSession = null; authCharacteristic = null
        companionRead = null; enrollWrite = null; companionCharacteristic = null; enrollCharacteristic = null
        trustedReconnect = false; companionRevision++
        connectionDone = null; mtuDone = null; discoveryDone = null; descriptorDone = null; readDone = null; setupRead = null; setupReadEpoch = -1L; setupReadProofRevision = -1L; writeDone = null; reliableDone = null
        val old = gatt; gatt = null; device = null
        runCatching { old?.disconnect() }; runCatching { old?.close() }
        commandCharacteristic = null; statusCharacteristic = null; mtu = 23; mutationPending = false
        activeWrite = null
        _state.update { SetupState(devices = it.devices, status = message + if (unknown) " Ergebnis unbekannt: Der Befehl könnte bereits gespeichert sein. Am Orb Setup stoppen und neu starten, neu koppeln und den Stand abrufen." else "",
            pairingTrace = trace.snapshot(), scanDiagnostic = it.scanDiagnostic, writeOutcome = it.writeOutcome) }
    }
    fun stop(message: String = "Einrichtung beendet. Am Orb Bluetooth → Start für eine neue Verbindung öffnen.", disarm: Boolean = true) {
        if (disarm) { reconnectArmed = false; retryAttempt = 0 }
        retryGeneration++
        retryJob?.cancel(); retryJob = null
        mark(PairingTrace.Stage.STOPPED)
        disconnect(message)
        connectJob?.cancel(); connectJob = null
        commandJob?.cancel(); commandJob = null
        stopScan()
        if (receiverRegistered) { runCatching { context.unregisterReceiver(receiver) }; receiverRegistered = false }
    }
    fun close() { stop(); closed = true; scope.cancel(); statusHint.close() }
}
