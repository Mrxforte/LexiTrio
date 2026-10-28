package com.example

import com.example.data.preferences.AppPreferences
import com.example.ui.navigation.Screen
import com.example.ui.screens.onboarding.ElingoColors
import com.example.ui.screens.onboarding.defaultAppLanguages
import com.example.ui.screens.onboarding.defaultNativeLanguages
import com.example.ui.screens.onboarding.defaultTargetLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElingoScreensUnitTest {

    @Test
    fun appPreferences_hasOnboardingCompletedFlag() {
        val defaultPrefs = AppPreferences()
        assertFalse(defaultPrefs.isOnboardingCompleted)

        val completedPrefs = defaultPrefs.copy(isOnboardingCompleted = true)
        assertTrue(completedPrefs.isOnboardingCompleted)
    }

    @Test
    fun navRoutes_includeAllElingoScreens() {
        assertEquals("elingo_splash", Screen.Splash.route)
        assertEquals("elingo_welcome", Screen.OnboardingWelcome.route)
        assertEquals("elingo_app_language", Screen.OnboardingAppLanguage.route)
        assertEquals("elingo_target_language", Screen.OnboardingTargetLanguage.route)
    }

    @Test
    fun elingoLanguages_containsRequiredLanguages() {
        val supported = setOf("en", "ru", "uz")
        assertEquals(supported, defaultAppLanguages.map { it.code }.toSet())
        assertEquals(supported, defaultTargetLanguages.map { it.code }.toSet())
        assertEquals(supported, defaultNativeLanguages.map { it.code }.toSet())
    }

    @Test
    fun elingoColors_areValid() {
        assertNotNull(ElingoColors.Purple)
        assertNotNull(ElingoColors.PurpleLight)
        assertNotNull(ElingoColors.TextDark)
        assertNotNull(ElingoColors.TextGray)
        assertNotNull(ElingoColors.BorderLight)
    }
}
