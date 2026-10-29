package com.example.viewmodel

import org.junit.Assert.*
import org.junit.Test

class OnboardingBackTest {
    @Test fun firstStepCannotCompleteOrExit() {
        assertEquals(1, onboardingBackStep(1))
        assertEquals(1, onboardingBackStep(2))
        assertEquals(3, onboardingBackStep(4))
    }
    @Test fun unfinishedRelaunchGatesToPairingRatherThanDashboard() {
        assertEquals(AppScreen.PAIRING, initialAuthenticatedRoute(false, false))
        assertEquals(AppScreen.ONBOARDING, initialAuthenticatedRoute(false, true))
        assertEquals(AppScreen.DASHBOARD, initialAuthenticatedRoute(true, false))
    }
}
