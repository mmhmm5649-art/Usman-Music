package com.usman.musicplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import com.usman.musicplayer.model.AlbumSearchResult
import com.usman.musicplayer.model.ArtistDetailsData
import com.usman.musicplayer.model.CuratedPlaylist
import com.usman.musicplayer.model.HomeDiscoverySection
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeSectionContentType
import com.usman.musicplayer.model.OnlineArtistResult
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.screens.ArtistDetailsScreen
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
class ArtistDetailsScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleArtists(): List<OnlineArtistResult> = listOf(
        OnlineArtistResult(
            id = "456323",
            name = "Anirudh Ravichander",
            artworkUri = "https://c.saavncdn.com/anirudh.jpg",
            languages = listOf("Telugu", "Tamil")
        ),
        OnlineArtistResult(
            id = "455125",
            name = "A. R. Rahman",
            artworkUri = "https://c.saavncdn.com/rahman.jpg",
            languages = listOf("Tamil")
        ),
        OnlineArtistResult(
            id = "459320",
            name = "Arijit Singh",
            artworkUri = "https://c.saavncdn.com/arijit.jpg",
            languages = listOf("Hindi")
        ),
        OnlineArtistResult(
            id = "578407",
            name = "Taylor Swift",
            artworkUri = "https://c.saavncdn.com/taylor.jpg",
            languages = listOf("English")
        )
    )

    private fun sampleArtistDetails(): ArtistDetailsData {
        val popular = listOf(
            Song(
                id = 101L,
                title = "Chuttamalle",
                artist = "Anirudh Ravichander, Shilpa Rao",
                album = "Devara Part 1",
                durationMs = 222_000L,
                filePath = "saavn_101",
                contentUriString = "https://aac.saavncdn.com/101.mp4",
                isOnline = true,
                playbackType = PlaybackType.FULL,
                language = "telugu",
                sourceProvider = "JioSaavn",
                releaseYear = "2024",
                isStreamable = true
            )
        )
        val latest = listOf(
            Song(
                id = 102L,
                title = "Coolie Disco",
                artist = "Anirudh Ravichander",
                album = "Coolie",
                durationMs = 195_000L,
                filePath = "saavn_102",
                contentUriString = "https://aac.saavncdn.com/102.mp4",
                isOnline = true,
                playbackType = PlaybackType.FULL,
                language = "tamil",
                sourceProvider = "JioSaavn",
                releaseYear = "2025",
                isStreamable = true
            )
        )
        val albums = listOf(
            AlbumSearchResult(
                id = "alb_devara",
                title = "Devara Part 1",
                artist = "Anirudh Ravichander",
                year = "2024",
                songCount = 6,
                sourceProvider = "JioSaavn",
                language = "telugu"
            )
        )
        val playlists = listOf(
            CuratedPlaylist(
                id = "pl_ani_hits",
                title = "Best of Anirudh",
                description = "Anirudh Ravichander Hits",
                songs = popular + latest
            )
        )
        return ArtistDetailsData(
            id = "456323",
            name = "Anirudh Ravichander",
            languages = listOf("Telugu", "Tamil"),
            popularSongs = popular,
            latestReleases = latest,
            albums = albums,
            playlists = playlists
        )
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h915dp-port")
    fun artistDetailsScreenDisplaysSongsLatestAlbumsPlaylistsAndTriggersPlayback() {
        var playAllCount = 0
        var shuffleCount = 0
        var openedAlbumId: String? = null
        var openedPlaylistId: String? = null
        var backPressed = false

        val details = sampleArtistDetails()

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                ArtistDetailsScreen(
                    artistSummary = OnlineArtistResult(
                        id = "456323",
                        name = "Anirudh Ravichander",
                        languages = listOf("Telugu", "Tamil")
                    ),
                    artistDetails = details,
                    isLoading = false,
                    error = null,
                    currentSong = null,
                    isPlaying = false,
                    isTv = false,
                    onBack = { backPressed = true },
                    onRetry = {},
                    onPlayAll = { playAllCount = it.size },
                    onShuffleAll = { shuffleCount = it.size },
                    onPlaySong = { _, _ -> },
                    onToggleFavorite = {},
                    onDownloadSong = {},
                    onOpenAlbum = { openedAlbumId = it.id },
                    onOpenPlaylist = { openedPlaylistId = it.id },
                    onSaveMixToLibrary = { _, _, cb -> cb(true, "Saved") }
                )
            }
        }

        composeRule.onNodeWithTag("artist_details_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Anirudh Ravichander").assertIsDisplayed()
        composeRule.onNodeWithText("USMAN MUSIC").assertIsDisplayed()
        composeRule.onNodeWithText("Telugu • Tamil").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_play_all_button").assertIsDisplayed()
        composeRule.onNodeWithTag("artist_shuffle_button").assertIsDisplayed()
        composeRule.onNodeWithText("Popular Songs").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("artist_song_row_101").performScrollTo().assertIsDisplayed()

        // Must never show JioSaavn branding or invent rankings/listener/follower stats
        assertTrue(composeRule.onAllNodesWithText("JioSaavn", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("followers", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("listeners", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("#1", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("artist_play_all_button").performClick()
        assertEquals(2, playAllCount)

        composeRule.onNodeWithTag("artist_shuffle_button").performClick()
        assertEquals(2, shuffleCount)

        composeRule.onNodeWithTag("artist_details_lazy_column").performScrollToKey("carousel_latest_releases")
        composeRule.onNodeWithTag("artist_latest_card_102").assertIsDisplayed()

        composeRule.onNodeWithTag("artist_details_lazy_column").performScrollToKey("carousel_artist_albums")
        composeRule.onNodeWithTag("artist_album_card_alb_devara").performClick()
        assertEquals("alb_devara", openedAlbumId)

        composeRule.onNodeWithTag("artist_details_lazy_column").performScrollToKey("carousel_artist_playlists")
        composeRule.onNodeWithTag("artist_playlist_card_pl_ani_hits").performClick()
        assertEquals("pl_ani_hits", openedPlaylistId)

        composeRule.onNodeWithTag("artist_details_lazy_column").performScrollToKey("artist_top_bar")
        composeRule.onNodeWithTag("artist_details_back_button").performClick()
        assertTrue(backPressed)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h915dp-port")
    fun phoneHomePopularArtistsCarouselAndSeeAllGroupByFourLanguages() {
        var clickedArtistName: String? = null
        val artistsSection = HomeDiscoverySection(
            id = "discovery_popular_artists",
            title = "Popular Artists",
            subtitle = "Telugu, Tamil, Hindi & English voices on JioSaavn",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.ARTISTS,
            artists = sampleArtists(),
            hasAttemptedLoad = true
        )
        val uiState = MusicPlayerUiState(
            homeSections = listOf(artistsSection),
            selectedHomeSeeAllSectionId = "discovery_popular_artists"
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                HomeScreen(
                    uiState = uiState,
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
                    onOpenArtist = { clickedArtistName = it.name }
                )
            }
        }

        composeRule.waitForIdle()
        // Verify Home See All sheet organizes artists by the 4 supported languages
        composeRule.onNodeWithTag("see_all_lazy_column").performScrollToKey("header_discovery_popular_artists_telugu")
        composeRule.onNodeWithTag("see_all_artist_group_telugu").assertIsDisplayed()

        composeRule.onNodeWithTag("see_all_lazy_column").performScrollToKey("header_discovery_popular_artists_tamil")
        composeRule.onNodeWithTag("see_all_artist_group_tamil").assertIsDisplayed()

        composeRule.onNodeWithTag("see_all_lazy_column").performScrollToKey("header_discovery_popular_artists_hindi")
        composeRule.onNodeWithTag("see_all_artist_group_hindi").assertIsDisplayed()

        composeRule.onNodeWithTag("see_all_lazy_column").performScrollToKey("all_discovery_popular_artists_english_578407_Taylor Swift")
        composeRule.onNodeWithTag("see_all_artist_group_english").assertIsDisplayed()
        composeRule.onNodeWithTag("home_see_all_artist_english_578407").performClick()
        assertEquals("Taylor Swift", clickedArtistName)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
    fun tvHomePopularArtistsRendersAndOpensArtistDetailsCallback() {
        var openedArtistId: String? = null
        val artistsSection = HomeDiscoverySection(
            id = "discovery_popular_artists",
            title = "Popular Artists",
            subtitle = "Telugu, Tamil, Hindi & English voices on JioSaavn",
            language = HomeLanguageFilter.ALL,
            contentType = HomeSectionContentType.ARTISTS,
            artists = sampleArtists(),
            hasAttemptedLoad = true
        )
        val uiState = MusicPlayerUiState(
            homeSections = listOf(artistsSection)
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvHomeScreen(
                    uiState = uiState,
                    onNavigateToSearch = {},
                    onSearchCategory = {},
                    onPlaySong = {},
                    onTabGenreSelected = {},
                    onOpenSettings = {},
                    onOpenArtist = { openedArtistId = it.id }
                )
            }
        }

        composeRule.onNodeWithTag("tv_artist_card_456323").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("tv_artist_card_456323").performClick()
        assertEquals("456323", openedArtistId)
    }
}
