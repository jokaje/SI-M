package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AgentConfig
import com.example.model.AgentType
import com.example.model.CreatureDna
import com.example.model.CreatureEmotion
import com.example.model.HiddenIntelligence
import com.example.model.InternetConfig
import com.example.model.InternetConnectionType
import com.example.model.OrbDevice
import com.example.model.OrbSettings
import com.example.model.ScreenTimeout
import com.example.model.SoundProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    SPLASH,
    AUTH,
    DASHBOARD,
    PAIRING,
    ONBOARDING,
    CAMERA_EYE
}

data class SIMUiState(
    val currentScreen: AppScreen = AppScreen.SPLASH,
    val splashProgress: Float = 0f,
    val splashStatus: String = "Starte SI-M Core...",
    val isAuthenticated: Boolean = false,
    val userEmail: String = "",
    val pairedOrb: OrbDevice? = null,
    val isSearchingDevices: Boolean = false,
    val discoveredDevices: List<OrbDevice> = emptyList(),
    val isPairingInProgress: Boolean = false,
    // Characters
    val characters: List<CreatureDna> = listOf(CreatureDna.defaultCreature()),
    val activeCharacterIndex: Int = 0,
    // Guided Onboarding after pairing
    val onboardingStep: Int = 1, // 1: Avatar, 2: Internet, 3: Agent/Name, 4: Done
    val draftCharacter: CreatureDna = CreatureDna.defaultCreature(),
    val randomDiceRollsLeft: Int = 3, // strictly max 3 attempts as requested!
    // Internet & Agent config
    val internetConfig: InternetConfig = InternetConfig(),
    val agentConfig: AgentConfig = AgentConfig(),
    // Settings
    val orbSettings: OrbSettings = OrbSettings(),
    val activeTab: Int = 0, // 0 = Dashboard/System, 1 = Neuer Charakter (Labor, gesperrt)
    val statusToast: String? = null
) {
    val activeCharacter: CreatureDna
        get() = characters.getOrNull(activeCharacterIndex) ?: draftCharacter
}

class SIMViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SIMUiState())
    val uiState: StateFlow<SIMUiState> = _uiState.asStateFlow()

    init {
        runSplashScreen()
    }

    private fun runSplashScreen() {
        viewModelScope.launch {
            _uiState.update { it.copy(splashProgress = 0.15f, splashStatus = "Initialisiere SI-M Mobile Engine...") }
            delay(400)
            _uiState.update { it.copy(splashProgress = 0.45f, splashStatus = "Lade 64×64 Muse Grafik-Treiber...") }
            delay(450)
            _uiState.update { it.copy(splashProgress = 0.8f, splashStatus = "Prüfe Bluetooth & Orb-Konnektivität...") }
            delay(500)
            _uiState.update { it.copy(splashProgress = 1.0f, splashStatus = "Bereit!") }
            delay(300)

            // Direct to Auth or Dashboard based on session
            _uiState.update { state ->
                state.copy(
                    currentScreen = if (state.isAuthenticated) AppScreen.DASHBOARD else AppScreen.AUTH
                )
            }
        }
    }

    // --- Authentication ---
    fun login(email: String, pass: String) {
        val user = if (email.isBlank()) "pilot@si-m.internal" else email
        _uiState.update {
            it.copy(
                isAuthenticated = true,
                userEmail = user,
                currentScreen = AppScreen.DASHBOARD,
                statusToast = "Erfolgreich angemeldet"
            )
        }
    }

    fun quickGuestLogin() {
        _uiState.update {
            it.copy(
                isAuthenticated = true,
                userEmail = "guest@si-m.ai",
                currentScreen = AppScreen.DASHBOARD,
                statusToast = "Als Gast eingeloggt"
            )
        }
    }

    fun logout() {
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                currentScreen = AppScreen.AUTH,
                statusToast = "Abgemeldet"
            )
        }
    }

    // --- Pairing Mode ---
    fun startPairingMode() {
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.PAIRING,
                isSearchingDevices = true,
                discoveredDevices = emptyList()
            )
        }

        viewModelScope.launch {
            delay(900)
            val demoOrb = OrbDevice(
                id = "orb-si-m-4092",
                name = "SI-M Orb #4092",
                macAddress = "E4:5F:01:8A:2B:9C",
                isPaired = false,
                isConnected = false,
                batteryPercent = 91,
                signalStrengthDbm = -44
            )
            val demoOrb2 = OrbDevice(
                id = "orb-si-m-1088",
                name = "SI-M Orb #1088 [Standby]",
                macAddress = "DC:A6:32:41:99:12",
                isPaired = false,
                isConnected = false,
                batteryPercent = 74,
                signalStrengthDbm = -71
            )
            _uiState.update {
                it.copy(
                    isSearchingDevices = false,
                    discoveredDevices = listOf(demoOrb, demoOrb2)
                )
            }
        }
    }

    fun connectDevice(device: OrbDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPairingInProgress = true) }
            delay(1200) // Simulated secure BLE handshake & credential exchange
            val paired = device.copy(isPaired = true, isConnected = true)
            _uiState.update {
                it.copy(
                    pairedOrb = paired,
                    isPairingInProgress = false,
                    currentScreen = AppScreen.ONBOARDING,
                    onboardingStep = 1,
                    draftCharacter = CreatureDna.createRandom(),
                    randomDiceRollsLeft = 3,
                    statusToast = "Orb erfolgreich gekoppelt!"
                )
            }
        }
    }

    // --- Guided Onboarding Flow ---
    fun rollRandomAvatar() {
        val currentLeft = _uiState.value.randomDiceRollsLeft
        if (currentLeft <= 0) {
            _uiState.update { it.copy(statusToast = "Maximale Versuche (3x) erreicht!") }
            return
        }

        val newDna = CreatureDna.createRandom()
        _uiState.update {
            it.copy(
                draftCharacter = newDna,
                randomDiceRollsLeft = currentLeft - 1,
                statusToast = "Avatar gewürfelt (${currentLeft - 1} übrig)"
            )
        }
    }

    fun confirmAvatar() {
        // Move to step 2: Internet Connection
        _uiState.update { it.copy(onboardingStep = 2) }
    }

    fun setInternetMode(type: InternetConnectionType, ssid: String, pass: String) {
        _uiState.update {
            it.copy(
                internetConfig = it.internetConfig.copy(
                    connectionType = type,
                    ssid = if (type == InternetConnectionType.HOTSPOT) "Handy-Hotspot (Aktiv)" else ssid,
                    isConnected = true
                )
            )
        }
    }

    fun confirmInternetAndProceed() {
        // Move to step 3: Intelligence / Agent Config
        _uiState.update { it.copy(onboardingStep = 3) }
    }

    fun saveAgentConfig(
        type: AgentType,
        agentName: String,
        address: String,
        token: String,
        llmProvider: String,
        apiKey: String
    ) {
        // If self configured, generate random personality and speech style in background!
        val hiddenIntel = if (type == AgentType.SELF_CONFIGURED) {
            HiddenIntelligence.generateRandom()
        } else {
            _uiState.value.draftCharacter.hiddenIntelligence
        }

        val updatedCharacter = _uiState.value.draftCharacter.copy(
            name = if (type == AgentType.SELF_CONFIGURED && agentName.isNotBlank()) agentName else _uiState.value.draftCharacter.name,
            hiddenIntelligence = hiddenIntel
        )

        val updatedAgentConfig = AgentConfig(
            agentType = type,
            agentName = if (agentName.isNotBlank()) agentName else "SI-M Core",
            serverAddress = address,
            bearerToken = token,
            llmProvider = llmProvider,
            apiKey = apiKey,
            isConnected = true
        )

        val list = _uiState.value.characters.toMutableList()
        list.add(0, updatedCharacter)

        _uiState.update {
            it.copy(
                draftCharacter = updatedCharacter,
                agentConfig = updatedAgentConfig,
                characters = list,
                activeCharacterIndex = 0,
                onboardingStep = 4, // Completion celebration
                statusToast = "SI-M Konfiguration abgeschlossen!"
            )
        }
    }

    fun finishOnboardingToDashboard() {
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.DASHBOARD,
                activeTab = 0
            )
        }
    }

    // --- Dashboard & System Actions ---
    fun setActiveCharacter(index: Int) {
        if (index in _uiState.value.characters.indices) {
            _uiState.update { it.copy(activeCharacterIndex = index) }
        }
    }

    fun setEmotion(emotion: CreatureEmotion) {
        val current = _uiState.value.activeCharacter
        val updated = current.copy(emotion = emotion)
        val list = _uiState.value.characters.toMutableList()
        val idx = _uiState.value.activeCharacterIndex
        if (idx in list.indices) {
            list[idx] = updated
            _uiState.update { it.copy(characters = list) }
        }
    }

    fun setActiveTab(tabIndex: Int) {
        _uiState.update { it.copy(activeTab = tabIndex) }
    }

    fun openCameraEye() {
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.CAMERA_EYE,
                orbSettings = it.orbSettings.copy(cameraEyeActive = true)
            )
        }
    }

    fun closeCameraEye() {
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.DASHBOARD,
                orbSettings = it.orbSettings.copy(cameraEyeActive = false)
            )
        }
    }

    fun updateVolume(vol: Float) {
        _uiState.update { it.copy(orbSettings = it.orbSettings.copy(volume = vol)) }
    }

    fun updateBrightness(brightness: Float) {
        _uiState.update { it.copy(orbSettings = it.orbSettings.copy(brightness = brightness)) }
    }

    fun updateSoundProfile(profile: SoundProfile) {
        _uiState.update { it.copy(orbSettings = it.orbSettings.copy(soundProfile = profile)) }
    }

    fun updateScreenTimeout(timeout: ScreenTimeout) {
        _uiState.update { it.copy(orbSettings = it.orbSettings.copy(screenTimeout = timeout)) }
    }

    fun toggleAdaptiveBrightness() {
        val curr = _uiState.value.orbSettings.adaptiveBrightness
        _uiState.update { it.copy(orbSettings = it.orbSettings.copy(adaptiveBrightness = !curr)) }
    }

    fun toggleWifi() {
        val curr = _uiState.value.orbSettings.wifiEnabled
        _uiState.update { it.copy(orbSettings = it.orbSettings.copy(wifiEnabled = !curr)) }
    }

    fun toggleBluetooth() {
        val curr = _uiState.value.orbSettings.bluetoothEnabled
        _uiState.update { it.copy(orbSettings = it.orbSettings.copy(bluetoothEnabled = !curr)) }
    }

    fun togglePixelGrid() {
        val curr = _uiState.value.orbSettings.pixelGridHardware
        _uiState.update { it.copy(orbSettings = it.orbSettings.copy(pixelGridHardware = !curr)) }
    }

    fun disconnectOrb() {
        _uiState.update {
            it.copy(
                pairedOrb = null,
                statusToast = "Orb getrennt"
            )
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(statusToast = null) }
    }

    fun navigateBack(): Boolean {
        return when (_uiState.value.currentScreen) {
            AppScreen.CAMERA_EYE -> {
                closeCameraEye()
                true
            }
            AppScreen.PAIRING -> {
                _uiState.update { it.copy(currentScreen = AppScreen.DASHBOARD) }
                true
            }
            AppScreen.ONBOARDING -> {
                val step = _uiState.value.onboardingStep
                if (step > 1) {
                    _uiState.update { it.copy(onboardingStep = step - 1) }
                    true
                } else {
                    _uiState.update { it.copy(currentScreen = AppScreen.DASHBOARD) }
                    true
                }
            }
            else -> false
        }
    }
}
