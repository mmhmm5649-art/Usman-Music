package com.usman.musicplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeDiscoverySection
import com.usman.musicplayer.model.HomeHeroFeature
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeQuickAccessCard
import com.usman.musicplayer.model.HomeQuickAccessType
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.screens.HomeScreen
import com.usman.musicplayer.ui.theme.UsmanMusicPlayerTheme
import com.usman.musicplayer.ui.tv.TvHomeScreen
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
class HomeScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleSongs(): List<Song> = listOf(
        Song(
            id = 1L,
            title = "Sooseki",
            artist = "Shreya Ghoshal",
            album = "Pushpa 2",
            durationMs = 240_000L,
            filePath = "saavn_1",
            contentUriString = "https://aac.saavncdn.com/1.mp4",
            isOnline = true,
            playbackType = PlaybackType.FULL,
            language = "telugu",
            sourceProvider = "JioSaavn",
            isStreamable = true
        )
    )

    private fun sampleUiState(): MusicPlayerUiState {
        val songs = sampleSongs()
        val quickCards = listOf(
            HomeQuickAccessCard("qa_telugu_hits", HomeQuickAccessType.TELUGU_HITS, "Telugu Hits", "20 Tollywood tracks", languageFilter = HomeLanguageFilter.TELUGU),
            HomeQuickAccessCard("qa_tamil_hits", HomeQuickAccessType.TAMIL_HITS, "Tamil Hits", "20 Kollywood tracks", languageFilter = HomeLanguageFilter.TAMIL),
            HomeQuickAccessCard("qa_english_hits", HomeQuickAccessType.ENGLISH_HITS, "English Hits", "20 global tracks", languageFilter = HomeLanguageFilter.ENGLISH),
            HomeQuickAccessCard("qa_hindi_hits", HomeQuickAccessType.HINDI_HITS, "Hindi Hits", "20 Bollywood tracks", languageFilter = HomeLanguageFilter.HINDI),
            HomeQuickAccessCard("qa_recently_played", HomeQuickAccessType.RECENTLY_PLAYED, "Recently Played", "1 track played"),
            HomeQuickAccessCard("qa_favorite_songs", HomeQuickAccessType.FAVORITE_SONGS, "Your Favourite Songs", "1 liked track")
        )
        val sections = listOf(
            HomeDiscoverySection(
                id = "discovery_trending_now",
                title = "Trending Now",
                subtitle = "Top tracks across Telugu, Tamil, Hindi & English",
                language = HomeLanguageFilter.ALL,
                contentType = HomeSectionContentType.SONGS,
                songs = songs,
                hasAttemptedLoad = true
            ),
            HomeDiscoverySection(
                id = "discovery_made_for_you",
                title = "Made For You",
                subtitle = "Curated mixes",
                language = HomeLanguageFilter.ALL,
                contentType = HomeSectionContentType.PLAYLISTS,
                playlists = listOf(CuratedPlaylist("mix_1", "Daily Mix", "Telugu & Hindi hits", songs = songs)),
                hasAttemptedLoad = true
            )
        )
        return MusicPlayerUiState(
            selectedHomeLanguage = HomeLanguageFilter.ALL,
            homeQuickCards = quickCards,
            homeHeroFeature = HomeHeroFeature(
                id = "hero_1",
                badge = "FEATURED DISCOVERY • USMAN MUSIC",
                title = "Sooseki",
                subtitle = "Shreya Ghoshal • Pushpa 2",
                primarySong = songs.first(),
                queueSongs = songs
            ),
            homeSections = sections,
            isHomeInitialLoading = false
        )
    }

    @Test
    fun phoneHomeScreenShowsOnlyFourLanguageChipsQuickCardsAndExcludesUnwantedLanguages() {
        var selectedFilter: HomeLanguageFilter? = null
        var clickedQuickCardId: String? = null

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                HomeScreen(
                    uiState = sampleUiState(),
                    onNavigateToSearch = {},
                    onSearchCategory = {},
                    onNavigateToLibrary = {},
                    onNavigateToNowPlaying = {},
                    onPlaySong = {},
                    onOpenService = { _, _, _ -> },
                    onTabGenreSelected = {},
                    onSelectCategory = {},
                    onClearCategory = {},
                    onRescanClick = {},
                    onRequestPermissionClick = {},
                    onSelectHomeLanguage = { selectedFilter = it },
                    onOpenQuickCard = { clickedQuickCardId = it.id }
                )
            }
        }

        // Verify ONLY ALL | TELUGU | TAMIL | ENGLISH | HINDI filter chips exist
        composeRule.onNodeWithTag("home_lang_chip_all").assertIsDisplayed()
        composeRule.onNodeWithTag("home_lang_chip_telugu").assertIsDisplayed()
        composeRule.onNodeWithTag("home_lang_chip_tamil").assertIsDisplayed()
        composeRule.onNodeWithTag("home_lang_chip_english").assertIsDisplayed()
        composeRule.onNodeWithTag("home_lang_chip_hindi").assertIsDisplayed()

        assertTrue(composeRule.onAllNodesWithText("Malayalam", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Kannada", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Punjabi", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Bengali", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Phonk", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("JioSaavn", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithText("Telugu Hits").assertIsDisplayed()
        composeRule.onNodeWithText("Tamil Hits").assertIsDisplayed()
        composeRule.onNodeWithText("English Hits").assertIsDisplayed()
        composeRule.onNodeWithText("Hindi Hits").assertIsDisplayed()
        composeRule.onNodeWithText("Recently Played").assertIsDisplayed()
        composeRule.onNodeWithText("Your Favourite Songs").assertIsDisplayed()

        composeRule.onNodeWithTag("home_lang_chip_telugu").performClick()
        assertEquals(HomeLanguageFilter.TELUGU, selectedFilter)

        composeRule.onNodeWithTag("home_quick_card_qa_telugu_hits").performClick()
        assertEquals("qa_telugu_hits", clickedQuickCardId)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
    fun tvHomeScreenRendersLanguageChipsQuickCardsAndHeroSpotlight() {
        var selectedFilter: HomeLanguageFilter? = null

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvHomeScreen(
                    uiState = sampleUiState(),
                    onNavigateToSearch = {},
                    onSearchCategory = {},
                    onPlaySong = {},
                    onTabGenreSelected = {},
                    onOpenSettings = {},
                    onSelectHomeLanguage = { selectedFilter = it }
                )
            }
        }

        composeRule.onNodeWithTag("tv_lang_chip_all").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_lang_chip_telugu").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_lang_chip_tamil").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_lang_chip_english").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_lang_chip_hindi").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_hero_play_button").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_quick_card_qa_telugu_hits").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("tv_lang_chip_tamil").performClick()
        assertEquals(HomeLanguageFilter.TAMIL, selectedFilter)
        assertTrue(composeRule.onAllNodesWithText("JioSaavn", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
    }
}
