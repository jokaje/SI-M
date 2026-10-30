package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppPrefs
import com.example.data.ThemeMode
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CameraEyeScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.SIMTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SIMViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SIMViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appContext = LocalContext.current.applicationContext
            val prefs = remember(appContext) { AppPrefs(appContext) }
            var themeMode by remember(appContext) { mutableStateOf(prefs.themeMode) }
            val systemDark = isSystemInDarkTheme()

            SIMTheme(darkTheme = themeMode.isDark(systemDark)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val orbState by viewModel.orbState.collectAsStateWithLifecycle()
                    val quickState by viewModel.quickConnect.collectAsStateWithLifecycle()
                    val context = LocalContext.current

                    LaunchedEffect(uiState.statusToast) {
                        uiState.statusToast?.let { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            viewModel.clearToast()
                        }
                    }

                    AnimatedContent(
                        targetState = uiState.currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screenTransition"
                    ) { screen ->
                        when (screen) {
                            AppScreen.SPLASH -> {
                                SplashScreen(
                                    progress = uiState.splashProgress,
                                    statusText = uiState.splashStatus
                                )
                            }
                            AppScreen.AUTH -> {
                                AuthScreen(
                                    hasAccount = uiState.auth.hasAccount,
                                    initialEmail = uiState.auth.email,
                                    initialName = uiState.auth.displayName,
                                    error = uiState.auth.error,
                                    busy = uiState.auth.busy,
                                    onAuthenticate = { email, name, passcode, register ->
                                        viewModel.authenticate(email, name, passcode, register)
                                    }
                                )
                            }
                            AppScreen.PAIRING -> {
                                PairingScreen(
                                    state = orbState,
                                    permissions = viewModel.client.permissions(),
                                    onStartScan = { viewModel.startScan() },
                                    onConnect = { address -> viewModel.connect(address) },
                                    onConfirmPhysicalPairing = { viewModel.confirmPhysicalPairing() },
                                    onBack = { viewModel.leavePairing() },
                                    onStop = { viewModel.disconnectOrb() }
                                )
                            }
                            AppScreen.ONBOARDING -> {
                                OnboardingScreen(
                                    state = orbState,
                                    avatar = uiState.avatar,
                                    draft = uiState.avatarDraft, avatarName = uiState.avatarName, favorites = uiState.favorites,
                                    step = uiState.onboardingStep,
                                    selectedChoice = uiState.onboardingChoice, onChoose = viewModel::chooseOnboardingAvatar,
                                    avatarBusy = uiState.onboardingBusy, avatarError = uiState.onboardingError,
                                    onRollAvatar = { viewModel.rollAvatar() },
                                    onNameAvatar = { viewModel.nameAvatar(it) },
                                    onTrait = { i, value -> viewModel.editAvatarTrait(i, value) },
                                    onColor = { i, value -> viewModel.editAvatarColor(i, value) },
                                    onSaveFavorite = { viewModel.saveFavorite() },
                                    onFavorite = { viewModel.selectFavorite(it) },
                                    onDeleteFavorite = { viewModel.deleteFavorite(it) },
                                    onInstall = { viewModel.installAvatar() },
                                    onDeleteDevice = { viewModel.deleteDeviceAvatar() },
                                    onConfirmAvatar = { viewModel.confirmOnboardingAvatar() },
                                    onSelectFigure = { id -> viewModel.selectFigure(id) },
                                    onSetStep = { step -> viewModel.setOnboardingStep(step) },
                                    onRefreshNetworks = { viewModel.refreshNetworks() },
                                    onScanWifi = { viewModel.scanWifi() },
                                    onSaveWifi = { ssid, pass -> viewModel.saveWifi(ssid, pass) },
                                    onForgetWifi = { ssid -> viewModel.forgetWifi(ssid) },
                                    onReadAgent = { viewModel.readAgent() },
                                    onSaveAgent = { url, token -> viewModel.saveAgent(url, token) },
                                    onFinish = { viewModel.finishOnboarding() }
                                )
                            }
                            AppScreen.CAMERA_EYE -> {
                                CameraEyeScreen(
                                    activeCharacter = uiState.avatar,
                                    onClose = { viewModel.closeCameraEye() }
                                )
                            }
                            AppScreen.DASHBOARD -> {
                                DashboardScreen(
                                    state = orbState,
                                    avatar = uiState.avatar,
                                    draft = uiState.avatarDraft, avatarName = uiState.avatarName, favorites = uiState.favorites,
                                    onRollAvatar = { viewModel.rollAvatar() },
                                    onNameAvatar = { viewModel.nameAvatar(it) },
                                    onTrait = { i, value -> viewModel.editAvatarTrait(i, value) },
                                    onColor = { i, value -> viewModel.editAvatarColor(i, value) },
                                    onSaveFavorite = { viewModel.saveFavorite() },
                                    onFavorite = { viewModel.selectFavorite(it) },
                                    onDeleteFavorite = { viewModel.deleteFavorite(it) },
                                    onInstall = { viewModel.installAvatar() },
                                    onDeleteDevice = { viewModel.deleteDeviceAvatar() },
                                    displayName = uiState.auth.displayName,
                                    trustedAddress = viewModel.client.trustedPeerAddress,
                                    onboardingCompleted = uiState.onboardingCompleted,
                                    onPair = { viewModel.openPairing() },
                                    onReconnect = { viewModel.reconnectTrustedIfAvailable() },
                                    onDisconnect = { viewModel.disconnectOrb() },
                                    onRefreshAll = { viewModel.refreshManagement() },
                                    onRefreshSettings = { viewModel.refreshSettings() },
                                    onVolume = { value -> viewModel.setVolume(value) },
                                    onBrightness = { value -> viewModel.setBrightness(value) },
                                    onSleep = { seconds -> viewModel.setSleepSeconds(seconds) },
                                    onRefreshCatalog = { viewModel.refreshCatalog() },
                                    onSelectFigure = { id -> viewModel.selectFigure(id) },
                                    onRefreshNetworks = { viewModel.refreshNetworks() },
                                    onScanWifi = { viewModel.scanWifi() },
                                    onSaveWifi = { ssid, pass -> viewModel.saveWifi(ssid, pass) },
                                    onForgetWifi = { ssid -> viewModel.forgetWifi(ssid) },
                                    quick = quickState,
                                    onQuickConnect = { viewModel.quickConnect() },
                                    onQuickPassword = { pass -> viewModel.submitQuickPassword(pass) },
                                    onQuickCancel = { viewModel.cancelQuickConnect() },
                                    onReadAgent = { viewModel.readAgent() },
                                    onSaveAgent = { url, token -> viewModel.saveAgent(url, token) },
                                    onRemoveAgent = { viewModel.removeAgent() },
                                    onOpenCamera = { viewModel.openCameraEye() },
                                    onStartOnboarding = { viewModel.resumeOnboarding() },
                                    onLogout = { viewModel.logout() },
                                    themeMode = themeMode,
                                    onThemeMode = { mode ->
                                        themeMode = mode
                                        prefs.themeMode = mode
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
