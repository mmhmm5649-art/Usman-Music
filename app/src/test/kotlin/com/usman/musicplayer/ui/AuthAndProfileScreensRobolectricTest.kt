package com.usman.musicplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.usman.musicplayer.model.AuthGateDestination
import com.usman.musicplayer.model.HomeLanguagePolicy
import com.usman.musicplayer.model.UserProfileState
import com.usman.musicplayer.ui.screens.SettingsScreen
import com.usman.musicplayer.ui.theme.UsmanMusicPlayerTheme
import com.usman.musicplayer.ui.tv.TvHomeScreen
import com.usman.musicplayer.ui.tv.TvSettingsScreen
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthAndProfileScreensRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun userProfileState_defaultsDirectlyToHomeWithoutLoginGate() {
        val defaultProfile = UserProfileState()
        assertEquals(AuthGateDestination.HOME_GUEST, defaultProfile.gateDestination)
        assertTrue(defaultProfile.isGuestMode)
    }

    @Test
    fun settingsScreen_displaysListenerProfileWithoutGoogleSignInOrStartListening() {
        val state = MusicPlayerUiState(
            userProfile = UserProfileState(
                gateDestination = AuthGateDestination.HOME_GUEST,
                isSignedIn = false,
                isGuestMode = true
            )
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                SettingsScreen(
                    uiState = state,
                    onRescanClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("profile_settings_card").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_account_name").assertTextEquals("USMAN MUSIC LISTENER")
        composeRule.onNodeWithTag("profile_account_subtitle").assertTextEquals("Local & Streaming Audio Ready")

        // Ensure no Google Sign-In, Start Listening, or Sign Out controls exist
        assertTrue(
            composeRule.onAllNodesWithTag("profile_auth_button").fetchSemanticsNodes().isEmpty()
        )
        listOf(
            "Continue with Google",
            "Sign in with Google",
            "Sign Out",
            "Start Listening",
            "Display Name"
        ).forEach { removedText ->
            assertTrue(
                "Removed text '$removedText' should not appear in SettingsScreen",
                composeRule.onAllNodesWithText(removedText, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes().isEmpty()
            )
        }
    }

    @Test
    fun settingsScreen_containsOnlyProfileAndEqualizerSections() {
        val state = MusicPlayerUiState()

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                SettingsScreen(uiState = state)
            }
        }

        // Required two sections present
        composeRule.onNodeWithTag("settings_section_profile").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_section_equalizer").assertIsDisplayed()
        composeRule.onNodeWithText("Master Audio Processing").assertIsDisplayed()
        composeRule.onNodeWithText("Graphic Equalizer").assertExists()

        // Removed sections must NOT appear anywhere in SettingsScreen
        listOf(
            "THEME & VISUALS",
            "Liquid Glass & Glow Effects",
            "Rainbow Audio Spectrum",
            "MUSIC SOURCES",
            "LIBRARY SCANNING",
            "Scan Music Library Now",
            "MUSIC EXTENSIONS & PLUGINS",
            "Manage Music Extensions",
            "Import Provider Package",
            "APP INFORMATION",
            "USMAN MUSIC PLAYER"
        ).forEach { removedText ->
            assertTrue(
                "Removed text '$removedText' should not appear in SettingsScreen",
                composeRule.onAllNodesWithText(removedText, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes().isEmpty()
            )
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
    fun tvSettingsScreen_showsListenerProfileWithoutGoogleSignInOrSignOut() {
        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvSettingsScreen(
                    onOpenEqualizer = {},
                    onOpenPlugins = {},
                    onRescanLibrary = {},
                    userProfile = UserProfileState()
                )
            }
        }

        composeRule.onNodeWithTag("tv_settings_profile_card").assertIsDisplayed().performClick()
        assertTrue(
            composeRule.onAllNodesWithTag("tv_profile_auth_button").fetchSemanticsNodes().isEmpty()
        )
        listOf(
            "Continue with Google",
            "Sign in with Google",
            "Sign Out",
            "Start Listening"
        ).forEach { removedText ->
            assertTrue(
                "Removed text '$removedText' should not appear in TvSettingsScreen",
                composeRule.onAllNodesWithText(removedText, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes().isEmpty()
            )
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
    fun tvHomeScreen_displaysPlainTimeOfDayGreetingWithoutUserName() {
        val expectedGreeting = HomeLanguagePolicy.timeOfDayGreeting()
        val state = MusicPlayerUiState(
            userProfile = UserProfileState(
                gateDestination = AuthGateDestination.HOME_AUTHENTICATED,
                isSignedIn = true,
                uid = "uid_tv",
                googleDisplayName = "Usman Ali",
                email = "usman@example.com"
            )
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvHomeScreen(
                    uiState = state,
                    onNavigateToSearch = {},
                    onSearchCategory = {},
                    onPlaySong = {},
                    onTabGenreSelected = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithTag("tv_home_greeting_text").assertTextEquals(expectedGreeting)
        assertTrue(
            composeRule.onAllNodesWithText("Usman Ali", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun phoneHomeHeader_displaysPlainTimeOfDayGreetingWithoutUserName() {
        val expectedGreeting = HomeLanguagePolicy.timeOfDayGreeting()
        composeRule.setContent {
            UsmanMusicPlayerTheme {
                com.usman.musicplayer.ui.screens.SpotifyStyleHomeHeader(
                    onSearchClick = {},
                    onProfileSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_greeting_text").assertTextEquals(expectedGreeting)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
    fun studioAudioEffectsPanel_switchesAllModuleTabsAndQuickPresetsInCompose() {
        com.usman.musicplayer.playback.AudioEffectsManager.resetToDefaults()
        composeRule.setContent {
            UsmanMusicPlayerTheme {
                com.usman.musicplayer.ui.components.StudioAudioEffectsPanel()
            }
        }

        composeRule.onNodeWithTag("studio_master_card").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_preset_studio_feel").performClick()
        assertEquals(
            "Studio Feel",
            com.usman.musicplayer.playback.AudioEffectsManager.effectsState.value.activeQuickPresetName
        )

        composeRule.onNodeWithTag("studio_tab_bass").performClick()
        composeRule.onNodeWithTag("studio_bass_card").assertIsDisplayed()
        composeRule.onNodeWithText("Deep Punchy Bass Engine").assertIsDisplayed()

        composeRule.onNodeWithTag("studio_tab_hall").performClick()
        composeRule.onNodeWithTag("studio_hall_card").assertIsDisplayed()
        composeRule.onNodeWithText("Pure Concert Hall Effect").assertIsDisplayed()

        composeRule.onNodeWithTag("studio_tab_surround").performClick()
        composeRule.onNodeWithTag("studio_surround_card").assertIsDisplayed()
        composeRule.onNodeWithText("3D Surround & Spatial Audio").assertIsDisplayed()

        composeRule.onNodeWithTag("studio_tab_crystal").performClick()
        composeRule.onNodeWithTag("studio_crystal_card").assertIsDisplayed()
        composeRule.onNodeWithText("HD Crystal Sound Engine").assertIsDisplayed()

        composeRule.onNodeWithTag("studio_tab_pro").performClick()
        composeRule.onNodeWithTag("studio_pro_card").assertIsDisplayed()
        composeRule.onNodeWithText("Master Output, Balance & Dynamics").assertIsDisplayed()
    }
}
