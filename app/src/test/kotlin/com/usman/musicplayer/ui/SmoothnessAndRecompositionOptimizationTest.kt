package com.usman.musicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.usman.musicplayer.model.LyricsParser
import com.usman.musicplayer.model.PlaybackProgress
import com.usman.musicplayer.model.PlaybackType
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.AmllLyricLineItem
import com.usman.musicplayer.ui.components.CompactNowPlayingBar
import com.usman.musicplayer.ui.screens.NowPlayingScreen
import com.usman.musicplayer.ui.theme.AlbumPalette
import com.usman.musicplayer.ui.theme.UsmanMusicPlayerTheme
import com.usman.musicplayer.viewmodel.MusicPlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
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
class SmoothnessAndRecompositionOptimizationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun testSong() = Song(
        id = 101L,
        title = "Fluidity Anthem",
        artist = "USMAN Studio",
        album = "120Hz Sessions",
        durationMs = 210_000L,
        filePath = "saavn_101",
        isOnline = true,
        isStreamable = true,
        sourceProvider = "JioSaavn",
        playbackType = PlaybackType.FULL
    )

    private fun testPalette() = AlbumPalette(
        darkBackground = androidx.compose.ui.graphics.Color(0xFF0A1020),
        primary = androidx.compose.ui.graphics.Color(0xFF1E3A8A),
        secondary = androidx.compose.ui.graphics.Color(0xFF3B82F6),
        tertiary = androidx.compose.ui.graphics.Color(0xFF60A5FA),
        accent = androidx.compose.ui.graphics.Color(0xFF38BDF8),
        surfaceGlass = androidx.compose.ui.graphics.Color(0x331E293B)
    )

    @Test
    fun playbackProgressFlowUpdatesDoNotRecomposeRootOrSiblingComposables() {
        val song = testSong()
        val uiState = MusicPlayerUiState(currentSong = song, isPlaying = false)
        val progressFlow = MutableStateFlow(
            PlaybackProgress(currentPositionMs = 1_000L, durationMs = 210_000L, isPlaying = false)
        )

        val rootRecompositions = AtomicInteger(0)
        val siblingRecompositions = AtomicInteger(0)

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                SideEffect { rootRecompositions.incrementAndGet() }
                Column(modifier = Modifier.fillMaxSize()) {
                    Box {
                        SideEffect { siblingRecompositions.incrementAndGet() }
                        Text("Home Feed Shelf")
                    }
                    CompactNowPlayingBar(
                        currentSong = song,
                        isPlaying = false,
                        playbackProgressFlow = progressFlow,
                        onBarClick = {},
                        onPlayPauseToggle = {},
                        onSkipPrevious = {},
                        onSkipNext = {}
                    )
                    NowPlayingScreen(
                        uiState = uiState,
                        playbackProgressFlow = progressFlow,
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
        }

        composeRule.waitForIdle()
        val initialRoot = rootRecompositions.get()
        val initialSibling = siblingRecompositions.get()

        repeat(20) { tick ->
            composeRule.runOnIdle {
                progressFlow.value = PlaybackProgress(
                    currentPositionMs = 2_000L + tick * 50L,
                    durationMs = 210_000L,
                    isPlaying = false
                )
            }
        }
        composeRule.waitForIdle()

        println("Measured root recompositions across 20 progress ticks: before=$initialRoot after=${rootRecompositions.get()}")
        println("Measured sibling recompositions across 20 progress ticks: before=$initialSibling after=${siblingRecompositions.get()}")

        assertEquals(initialRoot, rootRecompositions.get())
        assertEquals(initialSibling, siblingRecompositions.get())
    }

    @Test
    fun amllLyricLineItemDoesNotRecomposeOn120HzDrawClockFrameTicks() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:02.000" end="00:06.000">
                    <span begin="00:02.000" end="00:03.000">Smooth</span>
                    <span begin="00:03.000" end="00:04.200">frame</span>
                    <span begin="00:04.200" end="00:05.800">pacing</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        val line = LyricsParser.parse(ttml, totalDurationMs = 180_000L).lines.single()
        val drawClock = mutableFloatStateOf(2100f)
        val lineHostRecompositions = AtomicInteger(0)

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                Box {
                    AmllLyricLineItem(
                        line = line,
                        prevLineEndMs = 0L,
                        isActive = true,
                        isPast = false,
                        isFuture = false,
                        isSynchronized = true,
                        drawClockState = drawClock,
                        palette = testPalette(),
                        isTv = false,
                        onSeekTo = {},
                        modifier = Modifier.testTag("bench_lyric_line")
                    )
                    SideEffect { lineHostRecompositions.incrementAndGet() }
                }
            }
        }

        composeRule.waitForIdle()
        val baseline = lineHostRecompositions.get()

        // Simulate 30 consecutive 120Hz frame ticks inside active singing span
        repeat(30) { frame ->
            composeRule.runOnIdle {
                drawClock.floatValue = 2200f + frame * 8.33f
            }
        }
        composeRule.waitForIdle()

        println("Measured AmllLyricLineItem recompositions across 30 draw ticks: baseline=$baseline after=${lineHostRecompositions.get()}")
        assertEquals(baseline, lineHostRecompositions.get())
    }

    @Test
    fun emptyAreaTapOnNowPlayingDoesNotTriggerTrackSkip() {
        val song = testSong()
        var nextCalls = 0
        var prevCalls = 0

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                NowPlayingScreen(
                    uiState = MusicPlayerUiState(currentSong = song, isPlaying = false),
                    playbackProgressFlow = MutableStateFlow(
                        PlaybackProgress(currentPositionMs = 10_000L, durationMs = 210_000L, isPlaying = false)
                    ),
                    isExpanded = true,
                    onBackClick = {},
                    onPlayPauseToggle = {},
                    onSkipNext = { nextCalls++ },
                    onSkipPrevious = { prevCalls++ },
                    onSeekTo = {},
                    onToggleShuffle = {},
                    onToggleRepeat = {},
                    onToggleFavorite = {},
                    onVolumeChange = {},
                    modifier = Modifier.testTag("now_playing_root")
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("now_playing_root").performClick()
        composeRule.waitForIdle()

        assertEquals(0, nextCalls)
        assertEquals(0, prevCalls)
    }

    @Test
    fun lyricsParserBenchmarkAndFindActiveIndexAccuracy() {
        val yrcPayload = buildString {
            repeat(60) { i ->
                val start = 2000L + i * 3000L
                append("[$start,2600]Line($start,600) $i(${start + 600},500) sync(${start + 1100},700) check(${start + 1800},600)\n")
            }
        }

        val startNs = System.nanoTime()
        val parsed = LyricsParser.parse(yrcPayload, totalDurationMs = 200_000L)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
        println("Measured 60-line YRC LyricsParser.parse durationMs=$elapsedMs")

        assertEquals(60, parsed.lines.size)
        assertTrue(parsed.isSynchronized)
        assertEquals(0, LyricsParser.findActiveIndex(parsed.lines, 2500L))
        assertEquals(10, LyricsParser.findActiveIndex(parsed.lines, 32_500L))
        assertEquals(59, LyricsParser.findActiveIndex(parsed.lines, 180_000L))
    }
}
