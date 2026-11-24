package com.usman.musicplayer.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.usman.musicplayer.model.ListeningMood
import com.usman.musicplayer.model.MoodNextRecommendation
import com.usman.musicplayer.model.PlaybackProgress
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.model.SongMoodProfile
import com.usman.musicplayer.model.StreamingQuality
import com.usman.musicplayer.ui.screens.NowPlayingScreen
import com.usman.musicplayer.ui.theme.UsmanMusicPlayerTheme
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NowPlayingMoodRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun song(id: Long, title: String, isOnline: Boolean = true) = Song(
        id = id,
        title = title,
        artist = "Arijit Singh",
        album = "Soulful Nights",
        durationMs = 240_000L,
        filePath = "saavn_$id",
        contentUriString = if (isOnline) "https://aac.saavncdn.com/$id.mp4" else "/sdcard/music/$id.mp3",
        isOnline = isOnline,
        playbackType = PlaybackType.FULL,
        language = "hindi",
        sourceProvider = if (isOnline) "JioSaavn" else "Local",
        isStreamable = isOnline
    )

    @Test
    fun moodPillIsCompletelyRemovedFromUiWhileBackgroundMoodIntelligenceRemainsActive() {
        val current = song(1L, "Channa Mereya Sad")
        val next = song(2L, "Agar Tum Saath Ho")
        var toggleCalls = 0

        val uiState = MusicPlayerUiState(
            currentSong = current,
            isPlaying = true,
            selectedStreamingQuality = StreamingQuality.LOW,
            activeStreamQualityLabel = "160 kbps AAC • Standard",
            isMoodAutoplayEnabled = true,
            currentSongMoodProfile = SongMoodProfile(
                primaryMood = ListeningMood.SAD_HEARTBREAK,
                secondaryMood = ListeningMood.EMOTIONAL,
                confidence = 0.78f,
                isLyricsAnalyzed = false,
                isProviderMetadataUsed = false
            ),
            upcomingAutoplaySong = MoodNextRecommendation(
                song = next,
                score = 12.5f,
                targetMood = ListeningMood.SAD_HEARTBREAK,
                sourceMood = ListeningMood.SAD_HEARTBREAK,
                confidence = 0.80f,
                reason = "Sad & Heartbreak • Hindi vibe match"
            )
        )

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                NowPlayingScreen(
                    uiState = uiState,
                    playbackProgressFlow = MutableStateFlow(
                        PlaybackProgress(currentPositionMs = 20_000L, durationMs = 240_000L, isPlaying = true)
                    ),
                    isExpanded = true,
                    onBackClick = {},
                    onPlayPauseToggle = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onSeekTo = {},
                    onToggleShuffle = {},
                    onToggleRepeat = {},
                    onToggleFavorite = {},
                    onVolumeChange = {},
                    onToggleMoodAutoplay = { toggleCalls++ }
                )
            }
        }

        composeRule.waitForIdle()

        // 1. Verify mood pill is completely absent from Now Playing UI
        composeRule.onNodeWithTag("now_playing_mood_pill").assertDoesNotExist()

        // 2. Verify background mood state was preserved and untouched
        assertNotNull(uiState.currentSongMoodProfile)
        assertEquals(ListeningMood.SAD_HEARTBREAK, uiState.currentSongMoodProfile?.primaryMood)
        assertNotNull(uiState.upcomingAutoplaySong)
        assertEquals("Agar Tum Saath Ho", uiState.upcomingAutoplaySong?.song?.title)

        // 3. Verify options menu toggle works cleanly for background autoplay
        composeRule.onNodeWithContentDescription("Options").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mood_autoplay_toggle_option").assertIsDisplayed().performClick()
        assertEquals(1, toggleCalls)
    }

    @Test
    fun dynamicQualityLabel_updatesImmediatelyWithSettingsTiersAndHidesBitrates() {
        val onlineSong = song(10L, "Kesariya", isOnline = true)
        val initialUiState = MusicPlayerUiState(
            currentSong = onlineSong,
            isPlaying = true,
            selectedStreamingQuality = StreamingQuality.DATA_SAVER,
            activeStreamQualityLabel = "96 kbps AAC",
            activeStreamFallbackNotice = null
        )

        val stateFlow = MutableStateFlow(initialUiState)

        composeRule.setContent {
            val state = stateFlow.collectAsState().value
            UsmanMusicPlayerTheme {
                NowPlayingScreen(
                    uiState = state,
                    isExpanded = true,
                    onBackClick = {},
                    onPlayPauseToggle = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onSeekTo = {},
                    onToggleShuffle = {},
                    onToggleRepeat = {},
                    onToggleFavorite = {},
                    onVolumeChange = {}
                )
            }
        }

        val qualityNode = composeRule.onNodeWithTag("now_playing_quality_label")

        // 1. Data Saver selection
        composeRule.waitForIdle()
        qualityNode.assertIsDisplayed().assertTextEquals("Data Saver")

        // 2. Low Quality selection
        stateFlow.value = stateFlow.value.copy(
            selectedStreamingQuality = StreamingQuality.LOW,
            activeStreamQualityLabel = "160 kbps AAC"
        )
        composeRule.waitForIdle()
        qualityNode.assertIsDisplayed().assertTextEquals("Low Quality")

        // 3. HD Quality selection
        stateFlow.value = stateFlow.value.copy(
            selectedStreamingQuality = StreamingQuality.HD,
            activeStreamQualityLabel = "320 kbps AAC"
        )
        composeRule.waitForIdle()
        qualityNode.assertIsDisplayed().assertTextEquals("HD")

        // 4. Fallback scenario (requested HD, provider supplied 160 kbps -> truthfully shows Low Quality)
        stateFlow.value = stateFlow.value.copy(
            selectedStreamingQuality = StreamingQuality.HD,
            activeStreamQualityLabel = "160 kbps",
            activeStreamFallbackNotice = "Requested High Quality (HD) • Provider supplied 160 kbps"
        )
        composeRule.waitForIdle()
        qualityNode.assertIsDisplayed().assertTextEquals("Low Quality")

        // 5. Local MP3 playback (not claiming streaming quality)
        val localSong = song(20L, "Offline Track", isOnline = false)
        stateFlow.value = stateFlow.value.copy(
            currentSong = localSong,
            selectedStreamingQuality = StreamingQuality.HD,
            activeStreamQualityLabel = "Local Audio",
            activeStreamFallbackNotice = null
        )
        composeRule.waitForIdle()
        qualityNode.assertIsDisplayed().assertTextEquals("LOCAL AUDIO")
    }
}
