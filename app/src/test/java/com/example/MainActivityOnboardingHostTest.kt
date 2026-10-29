package com.example

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppPrefs
import com.example.viewmodel.AppScreen
import com.example.viewmodel.SIMUiState
import com.example.viewmodel.SIMViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SIMApplication::class)
class MainActivityOnboardingHostTest {
    @Test fun actualActivityBackPreservesMiddleProgressAcrossRecreationAndReconnect() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val vm = ViewModelProvider(activity)[SIMViewModel::class.java]
        val prefs = AppPrefs(activity)
        prefs.onboardingCompleted = false
        prefs.onboardingStep = 3
        val field = SIMViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val state = field.get(vm) as MutableStateFlow<SIMUiState>
        state.value = state.value.copy(currentScreen = AppScreen.ONBOARDING, onboardingStep = 3,
            auth = state.value.auth.copy(signedIn = true))
        shadowOf(Looper.getMainLooper()).idle()
        state.value = state.value.copy(onboardingBusy = true)
        shadowOf(Looper.getMainLooper()).idle()
        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(3, state.value.onboardingStep)
        state.value = state.value.copy(onboardingBusy = false)
        shadowOf(Looper.getMainLooper()).idle()
        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, state.value.onboardingStep)
        assertEquals(2, prefs.onboardingStep)
        controller.pause().stop().destroy()
        val second = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val resumed = ViewModelProvider(second)[SIMViewModel::class.java]
        assertEquals(2, resumed.uiState.value.onboardingStep)
        assertFalse(resumed.uiState.value.onboardingCompleted)
        assertNotEquals(AppScreen.DASHBOARD, resumed.uiState.value.currentScreen)
        second.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotEquals(AppScreen.DASHBOARD, resumed.uiState.value.currentScreen)
    }
}
