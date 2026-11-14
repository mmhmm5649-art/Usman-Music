package com.usman.musicplayer.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.usman.musicplayer.model.HomeDiscoverySection
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.AppDestination
import com.usman.musicplayer.ui.screens.HomeScreen
import com.usman.musicplayer.ui.screens.SearchScreen
import com.usman.musicplayer.ui.theme.UsmanMusicPlayerTheme
import com.usman.musicplayer.ui.tv.TvSearchScreen
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import com.usman.musicplayer.viewmodel.SearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeAndSearchNavigationRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleSong(id: Long, title: String) = Song(
        id = id,
        title = title,
        artist = "Arijit Singh",
        album = "Latest Hits",
        durationMs = 215_000L,
        filePath = "saavn_$id",
        folderName = "JioSaavn",
        albumArtUri = "https://c.saavncdn.com/test_$id.jpg",
        contentUriString = "https://aac.saavncdn.com/$id.mp4",
        isOnline = true,
        playbackType = PlaybackType.FULL,
        language = "telugu",
        sourceProvider = "JioSaavn",
        isStreamable = true,
        isFullTrackVerified = true
    )

    @Test
    fun phoneHomeAndSearchScrollStatePreservedAcrossNavigation() {
        val sampleSongs = (1..25).map { idx -> sampleSong(idx.toLong(), "Track $idx") }
        val section = HomeDiscoverySection(
            id = "discovery_trending_now",
            title = "Trending Now",
            subtitle = "Instant Shelf",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.SONGS,
            songs = sampleSongs,
            hasAttemptedLoad = true
        )
        val extraSections = (1..10).map { idx ->
            HomeDiscoverySection(
                id = "extra_$idx",
                title = "Shelf $idx",
                subtitle = "Subtitle $idx",
                language = HomeLanguageFilter.ALL,
                contentType = HomeSectionContentType.SONGS,
                songs = sampleSongs,
                hasAttemptedLoad = true
            )
        }
        val state = MusicPlayerUiState(
            homeSections = listOf(section) + extraSections,
            isHomeInitialLoading = false,
            searchSource = SearchSource.ONLINE,
            searchQuery = "Pushpa",
            onlineSearchResults = sampleSongs.take(5),
            isOnlineSearching = true
        )

        var destination by mutableStateOf(AppDestination.HOME)
        lateinit var capturedHomeListState: LazyListState

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                val homeListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                val searchListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                capturedHomeListState = homeListState

                if (destination == AppDestination.HOME) {
                    HomeScreen(
                        uiState = state,
                        onNavigateToSearch = { destination = AppDestination.SEARCH },
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
                        listState = homeListState
                    )
                } else {
                    SearchScreen(
                        uiState = state,
                        onSearchQueryChange = {},
                        onSearchSourceChange = {},
                        onPlaySong = {},
                        onOpenService = { _, _, _ -> },
                        listState = searchListState
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_lazy_column").performScrollToIndex(8)
        composeRule.waitForIdle()
        val scrolledIdxBeforeNav = capturedHomeListState.firstVisibleItemIndex
        assertTrue("Expected Home to scroll past index 0, was $scrolledIdxBeforeNav", scrolledIdxBeforeNav >= 4)

        destination = AppDestination.SEARCH
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Track 1").assertIsDisplayed()

        destination = AppDestination.HOME
        composeRule.waitForIdle()
        assertEquals(
            "Home scroll position must be preserved across Home <-> Search navigation",
            scrolledIdxBeforeNav,
            capturedHomeListState.firstVisibleItemIndex
        )
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
    fun tvSearchTypingDoesNotBypassDebounceAndPreservesProgressiveResults() {
        val sampleSongs = (1..8).map { idx -> sampleSong(idx.toLong(), "TV Hit $idx") }
        val typedQueries = mutableListOf<String>()
        val immediateTriggers = AtomicInteger(0)
        var playedTitle = ""

        val tvState = MusicPlayerUiState(
            searchSource = SearchSource.ONLINE,
            searchQuery = "Anirudh",
            onlineSearchResults = sampleSongs,
            isOnlineSearching = true
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                val tvGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
                TvSearchScreen(
                    uiState = tvState,
                    onSearchQueryChange = { typedQueries.add(it) },
                    onTriggerOnlineSearch = { immediateTriggers.incrementAndGet() },
                    onSearchSourceChange = {},
                    onPlaySong = { playedTitle = it.title },
                    gridState = tvGridState
                )
            }
        }

        composeRule.onNodeWithText("TV Hit 1").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_search_lazy_grid").assertIsDisplayed()

        composeRule.onNodeWithText("Anirudh").performTextInput(" ")
        composeRule.waitForIdle()
        assertTrue(typedQueries.isNotEmpty())
        assertEquals(0, immediateTriggers.get())

        composeRule.onNodeWithText("TV Hit 1").performClick()
        assertEquals("TV Hit 1", playedTitle)
    }
}
