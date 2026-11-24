package com.usman.musicplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.usman.musicplayer.model.LibraryTab
import com.usman.musicplayer.model.Playlist
import com.usman.musicplayer.model.RecommendationPreference
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.AppDestination
import com.usman.musicplayer.ui.screens.NowPlayingScreen
import com.usman.musicplayer.ui.theme.UsmanMusicPlayerTheme
import com.usman.musicplayer.ui.tv.TvHomeScreen
import com.usman.musicplayer.ui.tv.TvLibraryScreen
import com.usman.musicplayer.ui.tv.TvNavigationRail
import com.usman.musicplayer.ui.tv.TvSearchScreen
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
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class TvParityRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleSong = Song(
        id = 701L,
        title = "Kesariya",
        artist = "Arijit Singh",
        album = "Brahmastra",
        durationMs = 268_000L,
        filePath = "saavn_701",
        contentUriString = "https://aac.saavncdn.com/701.mp4",
        isOnline = true,
        language = "hindi",
        sourceProvider = "JioSaavn",
        isStreamable = true
    )

    @Test
    fun tvNavigationRailAndHome_exposeListenTogetherShortcut() {
        var openedListenTogether = 0
        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvNavigationRail(
                    currentDestination = AppDestination.HOME,
                    onNavigate = {},
                    onOpenNowPlaying = {},
                    hasCurrentSong = true,
                    onOpenListenTogether = { openedListenTogether++ }
                )
            }
        }

        composeRule.onNodeWithTag("tv_rail_listen_together").assertIsDisplayed().performClick()
        assertEquals(1, openedListenTogether)
    }

    @Test
    fun tvHomeScreen_rendersListenTogetherHeaderAction() {
        var opened = false
        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvHomeScreen(
                    uiState = MusicPlayerUiState(),
                    onNavigateToSearch = {},
                    onSearchCategory = {},
                    onPlaySong = {},
                    onTabGenreSelected = {},
                    onOpenSettings = {},
                    onOpenListenTogether = { opened = true },
                    listenTogetherRoomCode = "AB12CD"
                )
            }
        }

        composeRule.onNodeWithTag("tv_home_listen_together_button").assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun tvSearchScreen_onScreenKeyboardTypesAndSubmitsQueries() {
        var currentQuery = ""
        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvSearchScreen(
                    uiState = MusicPlayerUiState(searchQuery = currentQuery),
                    onSearchQueryChange = { currentQuery = it },
                    onTriggerOnlineSearch = {},
                    onSearchSourceChange = {},
                    onPlaySong = {}
                )
            }
        }

        composeRule.onNodeWithTag("tv_keyboard_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_key_a").performClick()
        assertEquals("a", currentQuery)
    }

    @Test
    fun tvLibraryScreen_exposesFavoritesRecentAlbumsArtistsPlaylistsDownloadsTabs() {
        val state = MusicPlayerUiState(
            songs = listOf(sampleSong),
            favoriteSongs = listOf(sampleSong),
            recentlyPlayed = listOf(sampleSong),
            playlists = listOf(Playlist(id = 1L, name = "Late Night Chill", songCount = 1)),
            activeLibraryTab = LibraryTab.ALL_SONGS
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvLibraryScreen(
                    uiState = state,
                    onTabSelected = {},
                    onPlaySong = {}
                )
            }
        }

        composeRule.onNodeWithTag("tv_lib_tab_all_songs").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_lib_tab_favorites").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Kesariya").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_lib_tab_playlists").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Late Night Chill").assertIsDisplayed()
    }

    @Test
    fun tvSettingsScreen_showsListenTogetherAndAiMoodControlsWithoutGoogleSignIn() {
        var selectedPref: RecommendationPreference? = null
        var openedListenTogether = false

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvSettingsScreen(
                    onOpenEqualizer = {},
                    onOpenPlugins = {},
                    onRescanLibrary = {},
                    onOpenListenTogether = { openedListenTogether = true },
                    onSelectRecommendationPreference = { selectedPref = it }
                )
            }
        }

        composeRule.onNodeWithTag("tv_settings_profile_card").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_settings_listen_together_card").assertIsDisplayed().performClick()
        assertTrue(openedListenTogether)
        composeRule.onNodeWithTag("tv_settings_ai_mood_card").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_setting_rec_pref_discover_new").performClick()
        assertEquals(RecommendationPreference.DISCOVER_NEW, selectedPref)

        // Strictly no Google Sign-In UI on TV Settings
        assertEquals(0, composeRule.onAllNodesWithText("Google", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    }

    @Test
    fun nowPlayingScreen_onTvHidesRainbowAudioVisualizer() {
        composeRule.setContent {
            UsmanMusicPlayerTheme {
                NowPlayingScreen(
                    uiState = MusicPlayerUiState(currentSong = sampleSong, isPlaying = true),
                    onBackClick = {},
                    onPlayPauseToggle = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onSeekTo = {},
                    onToggleShuffle = {},
                    onToggleRepeat = {},
                    onToggleFavorite = {},
                    onVolumeChange = {},
                    isTvOverride = true
                )
            }
        }

        assertEquals(
            "RainbowAudioVisualizer must NOT render on Android TV",
            0,
            composeRule.onAllNodesWithTag("audio_rainbow_visualizer").fetchSemanticsNodes().size
        )
    }
}
