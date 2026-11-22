package com.usman.musicplayer.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.usman.musicplayer.data.listentogether.AuthoritativePlaybackState
import com.usman.musicplayer.data.listentogether.ListenTogetherConnectionStatus
import com.usman.musicplayer.data.listentogether.ListenTogetherControlMode
import com.usman.musicplayer.data.listentogether.ListenTogetherParticipant
import com.usman.musicplayer.data.listentogether.ListenTogetherRoomState
import com.usman.musicplayer.data.listentogether.ListenTogetherTrack
import com.usman.musicplayer.data.listentogether.ListenTogetherUiState
import com.usman.musicplayer.data.listentogether.ParticipantConnectionState
import com.usman.musicplayer.model.HomeLanguageFilter
import com.usman.musicplayer.model.HomeQuickAccessCard
import com.usman.musicplayer.model.HomeQuickAccessType
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.model.StreamingQuality
import com.usman.musicplayer.ui.components.SongOptionsBottomSheet
import com.usman.musicplayer.ui.screens.HomeScreen
import com.usman.musicplayer.ui.screens.ListenTogetherScreen
import com.usman.musicplayer.ui.screens.SearchScreen
import com.usman.musicplayer.ui.screens.SettingsScreen
import com.usman.musicplayer.ui.theme.UsmanMusicPlayerTheme
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import com.usman.musicplayer.viewmodel.SearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListenTogetherUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_placesListenTogetherCardDirectlyBelowLanguageChipsAndAboveCategoryCards() {
        var openedListenTogether = false
        val quickCards = listOf(
            HomeQuickAccessCard("telugu", HomeQuickAccessType.TELUGU_HITS, "Telugu Hits", "Trending", null, 12, HomeLanguageFilter.TELUGU),
            HomeQuickAccessCard("tamil", HomeQuickAccessType.TAMIL_HITS, "Tamil Hits", "Trending", null, 12, HomeLanguageFilter.TAMIL),
            HomeQuickAccessCard("english", HomeQuickAccessType.ENGLISH_HITS, "English Hits", "Trending", null, 12, HomeLanguageFilter.ENGLISH),
            HomeQuickAccessCard("hindi", HomeQuickAccessType.HINDI_HITS, "Hindi Hits", "Trending", null, 12, HomeLanguageFilter.HINDI)
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                HomeScreen(
                    uiState = MusicPlayerUiState(
                        selectedHomeLanguage = HomeLanguageFilter.ALL,
                        homeQuickCards = quickCards
                    ),
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
                    onOpenListenTogether = { openedListenTogether = true }
                )
            }
        }

        val chipsNode = composeRule.onNodeWithTag("home_lang_chip_all")
        val cardNode = composeRule.onNodeWithTag("home_listen_together_card")
        val quickCardNode = composeRule.onNodeWithTag("home_quick_card_telugu")

        chipsNode.assertIsDisplayed()
        cardNode.assertIsDisplayed()
        quickCardNode.assertIsDisplayed()

        composeRule.onNodeWithText("Listen Together").assertIsDisplayed()
        composeRule.onNodeWithText("Listen in sync with your friends").assertIsDisplayed()
        composeRule.onNodeWithText("NEW").assertIsDisplayed()

        val chipsBounds = chipsNode.getUnclippedBoundsInRoot()
        val cardBounds = cardNode.getUnclippedBoundsInRoot()
        val quickCardBounds = quickCardNode.getUnclippedBoundsInRoot()

        assertTrue(
            "Listen Together card (${cardBounds.top}) must be below language chips (${chipsBounds.bottom})",
            cardBounds.top >= chipsBounds.bottom - androidx.compose.ui.unit.Dp(4f)
        )
        assertTrue(
            "Listen Together card (${cardBounds.bottom}) must be above Home category cards (${quickCardBounds.top})",
            cardBounds.bottom <= quickCardBounds.top + androidx.compose.ui.unit.Dp(4f)
        )

        cardNode.performClick()
        assertTrue(openedListenTogether)
    }

    @Test
    fun listenTogetherLobby_displaysCreateJoinQrAndIndependentQualitySelectors() {
        var selectedQuality = StreamingQuality.HD
        composeRule.setContent {
            UsmanMusicPlayerTheme {
                ListenTogetherScreen(
                    state = ListenTogetherUiState(
                        serverUrl = "wss://sync.example.com/ws",
                        isServerConfigured = true,
                        displayName = "Phone A",
                        selectedQuality = StreamingQuality.HD
                    ),
                    onBack = {},
                    onCreateRoom = { _, _ -> },
                    onJoinRoom = { _, _ -> },
                    onLeaveRoom = {},
                    onSetControlMode = {},
                    onSetQuality = { selectedQuality = it },
                    onUpdateDisplayName = {},
                    onUpdateServerUrl = { Result.success(it) },
                    onTogglePlayPause = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onSeekTo = {},
                    onRequestResync = {},
                    onPlayQueueIndex = {},
                    onRemoveQueueIndex = {},
                    onAddTrackToQueue = { _, _ -> },
                    onPlayTrackNow = {},
                    onSearchSongs = { emptyList() }
                )
            }
        }

        val lobbyList = composeRule.onNodeWithTag("listen_together_lobby_list")
        lobbyList.performScrollToNode(hasTestTag("listen_together_create_room_button"))
        composeRule.onNodeWithTag("listen_together_create_room_button").assertIsDisplayed()

        lobbyList.performScrollToNode(hasTestTag("listen_together_code_input"))
        composeRule.onNodeWithTag("listen_together_code_input").assertIsDisplayed()
        composeRule.onNodeWithTag("listen_together_join_room_button").assertIsDisplayed()
        composeRule.onNodeWithTag("listen_together_scan_qr_button").assertIsDisplayed()

        lobbyList.performScrollToNode(hasTestTag("quality_option_data_saver"))
        composeRule.onNodeWithTag("quality_option_data_saver").performClick()
        assertEquals(StreamingQuality.DATA_SAVER, selectedQuality)
    }

    @Test
    fun listenTogetherActiveRoom_enforcesHostOnlyControlsForGuest() {
        val sampleTrack = ListenTogetherTrack(
            trackId = "track_1",
            title = "Kesariya",
            artist = "Arijit Singh",
            durationMs = 260_000L
        )
        val room = ListenTogetherRoomState(
            roomId = "room-1",
            roomCode = "A7K9P2",
            createdAt = 1000L,
            expiresAt = 999999L,
            hostId = "host-1",
            controlMode = ListenTogetherControlMode.HOST_ONLY,
            currentIndex = 0,
            currentTrack = sampleTrack,
            queue = listOf(sampleTrack),
            participants = listOf(
                ListenTogetherParticipant("host-1", "Phone A", true, ParticipantConnectionState.CONNECTED, 1000L, "HD"),
                ListenTogetherParticipant("guest-2", "Phone B", false, ParticipantConnectionState.CONNECTED, 1005L, "Data Saver")
            ),
            playback = AuthoritativePlaybackState(isPlaying = true, basePositionMs = 15_000L)
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                ListenTogetherScreen(
                    state = ListenTogetherUiState(
                        connectionStatus = ListenTogetherConnectionStatus.CONNECTED,
                        serverUrl = "wss://sync.example.com/ws",
                        isServerConfigured = true,
                        displayName = "Phone B",
                        participantId = "guest-2",
                        room = room,
                        selectedQuality = StreamingQuality.DATA_SAVER,
                        activeStreamQualityLabel = "96 kbps • AAC"
                    ),
                    onBack = {},
                    onCreateRoom = { _, _ -> },
                    onJoinRoom = { _, _ -> },
                    onLeaveRoom = {},
                    onSetControlMode = {},
                    onSetQuality = {},
                    onUpdateDisplayName = {},
                    onUpdateServerUrl = { Result.success(it) },
                    onTogglePlayPause = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onSeekTo = {},
                    onRequestResync = {},
                    onPlayQueueIndex = {},
                    onRemoveQueueIndex = {},
                    onAddTrackToQueue = { _, _ -> },
                    onPlayTrackNow = {},
                    onSearchSongs = { emptyList() }
                )
            }
        }

        composeRule.onNodeWithTag("listen_together_room_code_text").assertIsDisplayed()
        composeRule.onNodeWithText("Kesariya").assertIsDisplayed()
        composeRule.onNodeWithTag("listen_together_next_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("listen_together_prev_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("listen_together_active_list")
            .performScrollToNode(hasTestTag("listen_together_active_quality_label"))
        composeRule.onNodeWithTag("listen_together_active_quality_label").assertIsDisplayed()
    }

    @Test
    fun searchScreen_replacesExternalLinkWithThreeDotMenuWithoutTriggeringPlayback() {
        var playedSong: Song? = null
        var optionsSong: Song? = null
        val testSong = Song(
            id = 42L,
            title = "Naatu Naatu",
            artist = "Rahul Sipligunj",
            album = "RRR",
            durationMs = 215_000L,
            filePath = "jiosaavn_42",
            isOnline = true,
            isStreamable = true,
            sourceProvider = "JioSaavn",
            playbackType = PlaybackType.FULL
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                SearchScreen(
                    uiState = MusicPlayerUiState(
                        searchQuery = "Naatu",
                        searchSource = SearchSource.ONLINE,
                        onlineSearchResults = listOf(testSong)
                    ),
                    onSearchQueryChange = {},
                    onSearchSourceChange = {},
                    onPlaySong = { playedSong = it },
                    onOpenService = { _, _, _ -> },
                    onOpenSongOptions = { optionsSong = it }
                )
            }
        }

        // Old external-link action must not exist on song rows
        composeRule.onNodeWithContentDescription("Official streaming options").assertDoesNotExist()

        // Three-dot options button must exist with >= 48dp touch target
        val moreBtn = composeRule.onNodeWithTag("song_options_button_42")
        moreBtn.assertIsDisplayed()
        moreBtn.assertWidthIsAtLeast(48.dp)
        moreBtn.assertHeightIsAtLeast(48.dp)

        // Tapping three-dot opens options WITHOUT triggering song playback
        moreBtn.performClick()
        assertEquals(testSong, optionsSong)
        assertFalse("Tapping three-dot must not trigger playback", playedSong != null)
    }

    @Test
    fun songOptionsBottomSheet_displaysAllTenRequiredActions() {
        val testSong = Song(
            id = 77L,
            title = "Chaleya",
            artist = "Arijit Singh",
            album = "Jawan",
            durationMs = 200_000L,
            filePath = "jiosaavn_77",
            isOnline = true,
            isStreamable = true,
            sourceProvider = "JioSaavn",
            playbackType = PlaybackType.FULL
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                SongOptionsBottomSheet(
                    song = testSong,
                    playlists = emptyList(),
                    isInListenTogetherRoom = false,
                    canControlListenTogether = false,
                    selectedStreamingQuality = StreamingQuality.HD,
                    activeStreamQualityLabel = "320 kbps HD • AAC",
                    onDismiss = {},
                    onPlayNow = {},
                    onPlayNext = { _, _ -> },
                    onAddToQueue = { _, _ -> },
                    onAddToPlaylist = { _, _, _, _ -> },
                    onCreatePlaylist = { _, _, _ -> },
                    onDownloadOffline = {},
                    onOpenLyrics = {},
                    onOpenEqualizer = {},
                    onOpenListenTogether = {},
                    onAddToListenTogetherQueue = {},
                    onPlayInListenTogetherRoom = {}
                )
            }
        }

        val requiredTags = listOf(
            "song_option_play_now",
            "song_option_play_next",
            "song_option_add_to_queue",
            "song_option_add_to_playlist",
            "song_option_download_offline",
            "song_option_lyrics",
            "song_option_equalizer",
            "song_option_info",
            "song_option_share",
            "song_option_listen_together"
        )
        requiredTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun settingsScreen_displaysStreamingQualityOptionsAndFallbackNotice() {
        var pickedQuality: StreamingQuality? = null
        composeRule.setContent {
            UsmanMusicPlayerTheme {
                SettingsScreen(
                    uiState = MusicPlayerUiState(
                        selectedStreamingQuality = StreamingQuality.HD,
                        activeStreamQualityLabel = "160 kbps • AAC",
                        activeStreamFallbackNotice = "Requested HD (320 kbps) • Provider supplied 160 kbps"
                    ),
                    onSelectStreamingQuality = { pickedQuality = it }
                )
            }
        }

        composeRule.onNodeWithTag("settings_streaming_quality_card").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_active_quality_label").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_quality_fallback_notice").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_quality_option_data_saver").performClick()
        assertEquals(StreamingQuality.DATA_SAVER, pickedQuality)
    }
}
