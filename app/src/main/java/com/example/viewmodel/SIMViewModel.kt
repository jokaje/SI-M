package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppPrefs
import com.example.data.LocalAccountStore
import com.example.model.CreatureDna
import com.example.model.CreatureEmotion
import com.example.model.AvatarDraft
import com.example.model.AvatarFavorite
import com.example.orb.AvatarDnaWire
import java.security.SecureRandom
import com.example.orb.AgentCommand
import com.example.orb.AgentProtocol
import com.example.orb.OrbSetupClient
import com.example.orb.SetupCommand
import com.example.orb.SetupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppScreen {
    SPLASH,
    AUTH,
    DASHBOARD,
    PAIRING,
    ONBOARDING,
    CAMERA_EYE
}

data class AuthUiState(
    val hasAccount: Boolean = false,
    val signedIn: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val error: String? = null,
    val busy: Boolean = false
)

data class SIMUiState(
    val currentScreen: AppScreen = AppScreen.SPLASH,
    val splashProgress: Float = 0f,
    val splashStatus: String = "Starte SI-M …",
    val auth: AuthUiState = AuthUiState(),
    val avatar: CreatureDna = CreatureDna.defaultCreature(),
    val avatarDraft: String? = null, val avatarName: String = "Neues Wesen",
    val favorites: List<AvatarFavorite> = emptyList(),
    val onboardingStep: Int = 1,
    val onboardingCompleted: Boolean = false,
    val onboardingChoice: String? = null, val onboardingBusy: Boolean = false,
    val onboardingError: String? = null,
    val statusToast: String? = null
)

internal fun onboardingBackStep(step: Int): Int = (step - 1).coerceAtLeast(1)
internal fun initialAuthenticatedRoute(completed: Boolean, connected: Boolean): AppScreen = when {
    completed -> AppScreen.DASHBOARD
    connected -> AppScreen.ONBOARDING
    else -> AppScreen.PAIRING
}

class SIMViewModel(application: Application) : AndroidViewModel(application) {

    private val account = LocalAccountStore(application)
    private val prefs = AppPrefs(application)

    val client = (application as com.example.SIMApplication).orbClient
    val orbState: StateFlow<SetupState> = client.state

    private val _uiState = MutableStateFlow(SIMUiState())
    val uiState: StateFlow<SIMUiState> = _uiState.asStateFlow()

    private var onboardingAfterPairing = false
    private var draftRevision = 0L
    private var pendingAvatar: PendingAvatar? = null
    private data class PendingAvatar(val request: Long, val session: Long, val revision: Long, val choice: String, val name: String)

    init {
        val favorites = prefs.loadFavorites()
        val firstDna = favorites.firstOrNull()?.dna
        _uiState.update {
            it.copy(
                avatar = prefs.loadAvatar() ?: CreatureDna.defaultCreature(),
                avatarDraft = firstDna, avatarName = favorites.firstOrNull()?.name ?: "Neues Wesen",
                favorites = favorites,
                onboardingCompleted = prefs.onboardingCompleted,
                onboardingStep = if (prefs.onboardingCompleted) 1 else prefs.onboardingStep,
                auth = it.auth.copy(
                    hasAccount = account.hasAccount(),
                    email = account.email(),
                    displayName = account.displayName()
                )
            )
        }
        observePairing()
        runSplashScreen()
    }

    private fun observePairing() {
        viewModelScope.launch {
            client.state.collect { state ->
                if (onboardingAfterPairing && state.connected && state.authorized) {
                    onboardingAfterPairing = false
                    if (prefs.onboardingCompleted) {
                        _uiState.update { it.copy(currentScreen = AppScreen.DASHBOARD) }
                    } else {
                        _uiState.update {
                            it.copy(
                                currentScreen = AppScreen.ONBOARDING,
                                onboardingStep = prefs.onboardingStep
                            )
                        }
                    }
                }
                val pending = pendingAvatar
                if (pending != null) {
                    val result = state.writeOutcome
                    when {
                        result != null && result.request == pending.request && result.session == pending.session -> {
                            pendingAvatar = null
                            if (result.confirmed && state.connected && state.authorized && state.setupOpen &&
                                _uiState.value.onboardingStep == 1 && draftRevision == pending.revision &&
                                _uiState.value.onboardingChoice == pending.choice && _uiState.value.avatarName == pending.name) {
                                val named = _uiState.value.avatar.copy(name = pending.name.trim().take(32))
                                prefs.saveAvatar(named)
                                prefs.onboardingStep = 2
                                _uiState.update { it.copy(avatar = named, onboardingStep = 2, onboardingBusy = false, onboardingError = null) }
                            } else _uiState.update { it.copy(onboardingBusy = false,
                                onboardingError = if (result.confirmed) "Auswahl geändert. Bitte erneut bestätigen." else "Orb-Auswahl nicht bestätigt. Bitte erneut versuchen.") }
                        }
                        !state.connected || !state.authorized || !state.setupOpen -> {
                            pendingAvatar = null
                            _uiState.update { it.copy(onboardingBusy = false, onboardingError = "Orb-Verbindung oder Setup-Fenster verloren. Erneut verbinden und versuchen.") }
                        }
                        !state.busy -> {
                            pendingAvatar = null
                            _uiState.update { it.copy(onboardingBusy = false, onboardingError = "Gerätebestätigung ausgeblieben. Bitte erneut versuchen.") }
                        }
                    }
                }
            }
        }
    }

    private fun runSplashScreen() {
        viewModelScope.launch {
            _uiState.update { it.copy(splashProgress = 0.2f, splashStatus = "Lade Avatar-Engine …") }
            delay(350)
            _uiState.update { it.copy(splashProgress = 0.5f, splashStatus = "Prüfe lokales Profil …") }
            delay(350)
            _uiState.update { it.copy(splashProgress = 0.8f, splashStatus = "Bereite Orb-Verbindung vor …") }
            delay(350)
            _uiState.update { it.copy(splashProgress = 1f, splashStatus = "Bereit") }
            delay(250)
            _uiState.update { it.copy(currentScreen = AppScreen.AUTH) }
        }
    }

    // ---------------------------------------------------------------- profile

    fun authenticate(email: String, name: String, passcode: String, register: Boolean) {
        val current = _uiState.value.auth
        if (current.busy) return

        if (!account.available) {
            _uiState.update { it.copy(auth = it.auth.copy(error = "Lokaler Speicher ist auf diesem Gerät nicht verfügbar.")) }
            return
        }
        if (passcode.length < LocalAccountStore.MIN_PASSCODE) {
            _uiState.update {
                it.copy(auth = it.auth.copy(error = "Das Passwort braucht mindestens ${LocalAccountStore.MIN_PASSCODE} Zeichen."))
            }
            return
        }
        if (!account.validEmail(email)) {
            _uiState.update { it.copy(auth = it.auth.copy(error = "Bitte eine gültige E-Mail-Adresse eingeben.")) }
            return
        }
        if (register && name.isBlank()) {
            _uiState.update { it.copy(auth = it.auth.copy(error = "Bitte einen Namen eingeben.")) }
            return
        }

        _uiState.update { it.copy(auth = it.auth.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                if (register) account.register(email, name, passcode) else account.verify(email, passcode)
            }
            if (!ok) {
                _uiState.update {
                    it.copy(auth = it.auth.copy(
                        busy = false,
                        error = if (register) {
                            "Profil konnte nicht angelegt werden. Prüfe die Eingaben."
                        } else {
                            "E-Mail oder Passwort stimmen nicht."
                        }
                    ))
                }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    currentScreen = initialAuthenticatedRoute(prefs.onboardingCompleted, orbState.value.connected && orbState.value.authorized),
                    auth = state.auth.copy(
                        hasAccount = true,
                        signedIn = true,
                        busy = false,
                        email = account.email(),
                        displayName = account.displayName(),
                        error = null
                    )
                )
            }
            if (!prefs.onboardingCompleted) onboardingAfterPairing = true
            reconnectTrustedIfAvailable()
        }
    }

    fun logout() {
        client.stop("Verbindung getrennt.")
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.AUTH,
                auth = it.auth.copy(signedIn = false, error = null, busy = false)
            )
        }
    }

    // ---------------------------------------------------------------- pairing

    fun openPairing() {
        _uiState.update { it.copy(currentScreen = AppScreen.PAIRING) }
    }

    fun startScan() = client.startScan()

    fun connect(address: String) = client.connect(address)

    fun confirmPhysicalPairing() {
        onboardingAfterPairing = true
        client.confirmPhysicalPairing()
    }

    fun leavePairing() {
        onboardingAfterPairing = false
        client.stop("Kopplung verlassen.")
        _uiState.update { it.copy(currentScreen = if (prefs.onboardingCompleted) AppScreen.DASHBOARD else AppScreen.PAIRING) }
    }

    fun reconnectTrustedIfAvailable() {
        if (client.trustedPeerAddress != null && !orbState.value.connected && !orbState.value.connecting) {
            client.connectTrusted()
        }
    }

    fun disconnectOrb() = client.stop("Orb getrennt.")

    // ------------------------------------------------------------- onboarding

    private fun newSeed(): Int = SecureRandom().nextInt().let { if (it == 0) 1 else it }

    fun rollAvatar() {
        draftRevision++
        _uiState.update { it.copy(avatarDraft = AvatarDraft.generate(newSeed()), avatarName = "Neues Wesen",
            statusToast = "Neuer lokaler Entwurf – unbegrenzt würfeln") }
    }

    fun nameAvatar(value: String) { draftRevision++; _uiState.update { it.copy(avatarName = value.take(32)) } }
    fun chooseOnboardingAvatar(choice: String) {
        draftRevision++
        _uiState.update { it.copy(onboardingChoice = choice, onboardingError = null) }
    }
    fun confirmOnboardingAvatar() {
        val ui = _uiState.value
        val orb = orbState.value
        if (ui.onboardingBusy || ui.onboardingStep != 1 || !ui.auth.signedIn) return
        val choice = ui.onboardingChoice ?: return
        if (ui.avatarName.isBlank() || !orb.connected || !orb.authorized || !orb.setupOpen || !orb.trusted) return
        val command = if (choice == "random") {
            if (!orb.avatarSupported) return
            SetupCommand.AvatarSet(ui.avatarDraft ?: return)
        } else SetupCommand.CharacterSet(choice)
        val revision = draftRevision
        _uiState.update { it.copy(onboardingBusy = true, onboardingError = null) }
        val request = client.perform(command)
        if (request == null) {
            _uiState.update { it.copy(onboardingBusy = false, onboardingError = "Anfrage abgelehnt. Orb-Status prüfen und erneut versuchen.") }
            return
        }
        pendingAvatar = PendingAvatar(request, orbState.value.writeOutcome?.session ?: client.currentSessionGeneration(), revision, choice, ui.avatarName)
    }
    fun editAvatarTrait(index: Int, value: Int) {
        _uiState.value.avatarDraft?.let { dna ->
            runCatching { AvatarDraft.editTrait(dna, index, value) }
                .onSuccess { edited -> _uiState.update { it.copy(avatarDraft = edited) } }
        }
    }
    fun editAvatarColor(index: Int, value: String) {
        _uiState.value.avatarDraft?.let { dna ->
            runCatching { AvatarDraft.editColor(dna, index, value) }
                .onSuccess { edited -> _uiState.update { it.copy(avatarDraft = edited) } }
        }
    }
    fun saveFavorite() {
        val current = _uiState.value
        val dna = current.avatarDraft ?: return
        val name = current.avatarName.trim()
        if (name.isEmpty() || current.favorites.size >= 32) {
            _uiState.update { it.copy(statusToast = "Name erforderlich; maximal 32 Favoriten.") }; return
        }
        val entries = current.favorites + AvatarDraft.favorite(name, dna)
        runCatching { prefs.saveFavorites(entries) }.onSuccess {
            _uiState.update { it.copy(favorites = entries, statusToast = "Lokal gespeichert – nicht auf dem Orb.") }
        }.onFailure { _uiState.update { it.copy(statusToast = "Favorit konnte nicht gespeichert werden.") } }
    }
    fun selectFavorite(id: String) {
        _uiState.value.favorites.firstOrNull { it.id == id }?.let { favorite ->
            _uiState.update { it.copy(avatarDraft = favorite.dna, avatarName = favorite.name) }
        }
    }
    fun deleteFavorite(id: String) {
        val entries = _uiState.value.favorites.filterNot { it.id == id }
        runCatching { prefs.saveFavorites(entries) }.onSuccess { _uiState.update { it.copy(favorites = entries) } }
    }
    fun installAvatar() {
        _uiState.value.avatarDraft?.let { dna ->
            if (orbState.value.connected && orbState.value.authorized && orbState.value.avatarSupported)
                client.perform(SetupCommand.AvatarSet(dna))
            else _uiState.update { it.copy(statusToast = "Transfer benötigt Orb-Verbindung, Eigentümernachweis, offenes Setup-Fenster und Avatar-Fähigkeit.") }
        }
    }
    fun deleteDeviceAvatar() { client.perform(SetupCommand.AvatarDelete) }
    fun refreshDeviceAvatar() { client.perform(SetupCommand.AvatarCaps) }

    fun confirmAvatar() {
        val named = _uiState.value.avatar.copy(name = _uiState.value.avatarName.trim().take(32))
        prefs.saveAvatar(named)
        _uiState.update { it.copy(avatar = named) }
        prefs.onboardingStep = 2
        _uiState.update { it.copy(onboardingStep = 2) }
        client.refreshManagement()
    }

    fun setOnboardingStep(step: Int) {
        if (_uiState.value.onboardingBusy || (!prefs.onboardingCompleted && step > _uiState.value.onboardingStep && _uiState.value.onboardingStep == 1)) return
        prefs.onboardingStep = step.coerceIn(1, 4)
        _uiState.update { it.copy(onboardingStep = prefs.onboardingStep) }
    }

    fun finishOnboarding() {
        if (_uiState.value.onboardingStep != 4) return
        prefs.onboardingCompleted = true
        prefs.onboardingStep = 1
        _uiState.update { it.copy(onboardingCompleted = true, currentScreen = AppScreen.DASHBOARD) }
    }

    /** Reopens the setup flow from the dashboard without re-pairing. */
    fun resumeOnboarding() {
        _uiState.update {
            it.copy(currentScreen = AppScreen.ONBOARDING, onboardingStep = 1)
        }
        client.refreshManagement()
    }

    // --------------------------------------------------------------- orb data

    fun refreshManagement() = client.refreshManagement()

    fun refreshSettings() = client.perform(SetupCommand.Settings)

    fun refreshNetworks() = client.perform(SetupCommand.Networks)

    fun scanWifi() = client.perform(SetupCommand.Scan)

    fun saveWifi(ssid: String, password: String) = client.perform(SetupCommand.Save(ssid, password))

    fun forgetWifi(ssid: String) = client.perform(SetupCommand.Forget(ssid))

    fun refreshCatalog() = client.perform(SetupCommand.Catalog)

    fun selectFigure(id: String) = client.perform(SetupCommand.CharacterSet(id))

    fun setVolume(value: Int) = client.perform(SetupCommand.Volume(value.coerceIn(10, 80)))

    fun setBrightness(value: Int) = client.perform(SetupCommand.Brightness(value.coerceIn(10, 100)))

    fun setSleepSeconds(value: Int) = client.perform(SetupCommand.Sleep(value))

    fun readAgent() = client.performAgent(AgentCommand.Get)

    fun saveAgent(url: String, token: String) = client.performAgent(AgentCommand.Set(url.trim(), token))

    fun removeAgent() = client.performAgent(AgentCommand.Reset)

    fun validAgentUrl(url: String): Boolean = AgentProtocol.validUrl(url)

    fun validAgentToken(token: String): Boolean = AgentProtocol.validToken(token)

    // ------------------------------------------------------------------ misc

    fun setEmotion(emotion: CreatureEmotion) {
        val updated = _uiState.value.avatar.copy(emotion = emotion)
        prefs.saveAvatar(updated)
        _uiState.update { it.copy(avatar = updated) }
    }

    fun openCameraEye() {
        _uiState.update { it.copy(currentScreen = AppScreen.CAMERA_EYE) }
    }

    fun closeCameraEye() {
        _uiState.update { it.copy(currentScreen = AppScreen.DASHBOARD) }
    }

    fun clearToast() {
        _uiState.update { it.copy(statusToast = null) }
    }

    fun onHostStarted() = client.onHostStarted()

    fun onHostStopped() = client.onHostStopped()

    override fun onCleared() {
        // Application owns the connection across activity replacement.
        super.onCleared()
    }
}
