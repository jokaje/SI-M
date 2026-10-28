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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            SIMTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                                    onLoginSuccess = { email, pass -> viewModel.login(email, pass) },
                                    onGuestLogin = { viewModel.quickGuestLogin() }
                                )
                            }
                            AppScreen.PAIRING -> {
                                PairingScreen(
                                    isSearching = uiState.isSearchingDevices,
                                    isPairingInProgress = uiState.isPairingInProgress,
                                    devices = uiState.discoveredDevices,
                                    onConnectDevice = { device -> viewModel.connectDevice(device) },
                                    onBack = { viewModel.navigateBack() },
                                    onRefreshScan = { viewModel.startPairingMode() }
                                )
                            }
                            AppScreen.ONBOARDING -> {
                                OnboardingScreen(
                                    currentStep = uiState.onboardingStep,
                                    draftCharacter = uiState.draftCharacter,
                                    diceRollsLeft = uiState.randomDiceRollsLeft,
                                    internetConfig = uiState.internetConfig,
                                    agentConfig = uiState.agentConfig,
                                    onRollDice = { viewModel.rollRandomAvatar() },
                                    onConfirmAvatar = { viewModel.confirmAvatar() },
                                    onSelectInternetMode = { mode, ssid, pass ->
                                        viewModel.setInternetMode(mode, ssid, pass)
                                    },
                                    onConfirmInternet = { viewModel.confirmInternetAndProceed() },
                                    onSaveAgentConfig = { type, name, addr, token, prov, key ->
                                        viewModel.saveAgentConfig(type, name, addr, token, prov, key)
                                    },
                                    onFinishToDashboard = { viewModel.finishOnboardingToDashboard() },
                                    onBack = { viewModel.navigateBack() }
                                )
                            }
                            AppScreen.CAMERA_EYE -> {
                                CameraEyeScreen(
                                    activeCharacter = uiState.activeCharacter,
                                    onClose = { viewModel.closeCameraEye() }
                                )
                            }
                            AppScreen.DASHBOARD -> {
                                DashboardScreen(
                                    pairedOrb = uiState.pairedOrb,
                                    characters = uiState.characters,
                                    activeCharacterIndex = uiState.activeCharacterIndex,
                                    orbSettings = uiState.orbSettings,
                                    internetConfig = uiState.internetConfig,
                                    activeTab = uiState.activeTab,
                                    onStartPairing = { viewModel.startPairingMode() },
                                    onSelectCharacter = { idx -> viewModel.setActiveCharacter(idx) },
                                    onSetEmotion = { emotion -> viewModel.setEmotion(emotion) },
                                    onOpenCameraEye = { viewModel.openCameraEye() },
                                    onUpdateVolume = { vol -> viewModel.updateVolume(vol) },
                                    onUpdateBrightness = { b -> viewModel.updateBrightness(b) },
                                    onUpdateSoundProfile = { p -> viewModel.updateSoundProfile(p) },
                                    onUpdateScreenTimeout = { t -> viewModel.updateScreenTimeout(t) },
                                    onToggleAdaptiveBrightness = { viewModel.toggleAdaptiveBrightness() },
                                    onToggleWifi = { viewModel.toggleWifi() },
                                    onToggleBluetooth = { viewModel.toggleBluetooth() },
                                    onTogglePixelGrid = { viewModel.togglePixelGrid() },
                                    onDisconnectOrb = { viewModel.disconnectOrb() },
                                    onSelectTab = { tab -> viewModel.setActiveTab(tab) },
                                    onLogout = { viewModel.logout() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
