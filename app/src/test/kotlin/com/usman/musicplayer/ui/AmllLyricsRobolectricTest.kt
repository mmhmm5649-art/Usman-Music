package com.usman.musicplayer.ui

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.usman.musicplayer.model.LyricsParser
import com.usman.musicplayer.model.PlaybackProgress
import com.usman.musicplayer.model.Song
import com.usman.musicplayer.ui.components.AmllLyricLineItem
import com.usman.musicplayer.ui.components.AmllSyncQualityBadge
import com.usman.musicplayer.ui.theme.AlbumPalette
import com.usman.musicplayer.ui.theme.UsmanMusicPlayerTheme
import com.usman.musicplayer.ui.tv.TvFullLyricsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AmllLyricsRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun testPalette() = AlbumPalette(
        darkBackground = androidx.compose.ui.graphics.Color(0xFF0A1020),
        primary = androidx.compose.ui.graphics.Color(0xFF1E3A8A),
        secondary = androidx.compose.ui.graphics.Color(0xFF3B82F6),
        tertiary = androidx.compose.ui.graphics.Color(0xFF60A5FA),
        accent = androidx.compose.ui.graphics.Color(0xFF38BDF8),
        surfaceGlass = androidx.compose.ui.graphics.Color(0x331E293B)
    )

    @Test
    fun amllSyncQualityBadgeDistinguishesSyllableWordAndLineSyncHonestly() {
        val syllableLyrics = LyricsParser.parse("[1000,2800]Nee (1000,600)ko(1600,300)sa(1900,300)me (2200,300)naa (2500,500)pranam(3000,800)")
        val lineOnlyLyrics = LyricsParser.parse("[00:10.00]Standard line-synced lyric")

        val showLineOnly = androidx.compose.runtime.mutableStateOf(false)

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                AmllSyncQualityBadge(
                    parsedLyrics = if (showLineOnly.value) lineOnlyLyrics else syllableLyrics,
                    palette = testPalette(),
                    isTv = false
                )
            }
        }

        composeRule.onNodeWithText("SYLLABLE SYNC").assertIsDisplayed()

        composeRule.runOnIdle { showLineOnly.value = true }
        composeRule.onNodeWithText("LINE SYNC").assertIsDisplayed()
    }

    @Test
    fun amllLyricLineRendersInterludeDotsTranslationsAndHandlesSeekTap() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:08.000" end="00:12.000">
                    <span begin="00:08.000" end="00:08.700">నీ</span> <span begin="00:08.700" end="00:09.600">కోసమే</span> <span begin="00:09.600" end="00:10.200">నా</span> <span begin="00:10.200" end="00:11.500">ప్రాణం</span>
                    <span ttm:role="x-roman">Nee kosame naa pranam</span>
                    <span ttm:role="x-translation">My life is for you</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = LyricsParser.parse(ttml)
        val line = parsed.lines.single()
        val drawClock = mutableFloatStateOf(2500f) // Inside the 0..8000ms instrumental intro (>5000ms gap)
        var seekedMs = -1L

        composeRule.setContent {
            UsmanMusicPlayerTheme {
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
                    onSeekTo = { seekedMs = it }
                )
            }
        }

        // During 0..8000ms instrumental intro, the 3-dot AMLL interlude indicator is visible
        composeRule.onNodeWithTag("amll_interlude_dots", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Nee kosame naa pranam", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("My life is for you", useUnmergedTree = true).assertIsDisplayed()

        // Advance clock into singing range (9000ms) -> interlude dots disappear, word highlight progresses
        composeRule.runOnIdle { drawClock.floatValue = 9000f }
        composeRule.onNodeWithTag("amll_lyric_line_0").performClick()
        assertEquals(8000L, seekedMs)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h720dp-land")
    fun tvFullLyricsScreenRendersSyllableBadgeAndHandlesSeek() {
        val syllableLyrics = LyricsParser.parse(
            "[1000,2800]Nee (1000,600)ko(1600,300)sa(1900,300)me (2200,300)naa (2500,500)pranam(3000,800)"
        )
        var seekedTo = -1L

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvFullLyricsScreen(
                    song = Song(
                        id = 1L,
                        title = "Nee Kosame",
                        artist = "Sid Sriram",
                        album = "Telugu Hits",
                        durationMs = 180_000L,
                        filePath = "saavn_1"
                    ),
                    parsedLyrics = syllableLyrics,
                    playbackProgress = PlaybackProgress(
                        currentPositionMs = 1800L,
                        durationMs = 180_000L,
                        isPlaying = false
                    ),
                    isFetching = false,
                    fetchError = null,
                    palette = testPalette(),
                    onSeekTo = { seekedTo = it },
                    onRetryFetch = {},
                    onCloseLyrics = {}
                )
            }
        }

        composeRule.onNodeWithText("SYLLABLE-SYNCED LIVE LYRICS").assertIsDisplayed()
        composeRule.onNodeWithTag("amll_lyric_line_0").performClick()
        assertEquals(1000L, seekedTo)
    }

    @Test
    fun interactiveScriptSelectorSwitchesBetweenOriginalAndRomanizedWithoutInterruptingPlayback() {
        com.usman.musicplayer.ui.components.LyricsScriptPreferences.setScriptMode(
            null,
            com.usman.musicplayer.model.LyricsScriptMode.ORIGINAL
        )

        val multilingualInput = """
            [00:10.00]నువ్వే నా ప్రాణం
            [00:15.00]तुम ही हो
        """.trimIndent()

        val parsed = LyricsParser.parse(multilingualInput, totalDurationMs = 180_000L)

        composeRule.setContent {
            UsmanMusicPlayerTheme {
                TvFullLyricsScreen(
                    song = Song(
                        id = 2L,
                        title = "Multilingual Track",
                        artist = "USMAN MUSIC",
                        album = "Studio Sessions",
                        durationMs = 262_000L,
                        filePath = "saavn_2"
                    ),
                    parsedLyrics = parsed,
                    playbackProgress = PlaybackProgress(
                        currentPositionMs = 11400L,
                        durationMs = 262_000L,
                        isPlaying = false
                    ),
                    isFetching = false,
                    fetchError = null,
                    palette = testPalette(),
                    onSeekTo = {},
                    onRetryFetch = {},
                    onCloseLyrics = {}
                )
            }
        }

        // 1. Initially in Original Script mode
        composeRule.onNodeWithTag("lyrics_script_selector").assertIsDisplayed()
        composeRule.onNodeWithText("నువ్వే నా ప్రాణం", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("तुम ही हो", useUnmergedTree = true).assertIsDisplayed()

        // 2. User taps Romanized (English Letters) button -> immediately converts both Telugu & Hindi lines
        composeRule.onNodeWithTag("lyrics_script_romanized_btn").performClick()
        composeRule.onNodeWithText("Nuvve naa pranam", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Tum hi ho", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(
            com.usman.musicplayer.model.LyricsScriptMode.ROMANIZED,
            com.usman.musicplayer.ui.components.LyricsScriptPreferences.getScriptMode(null)
        )

        // 3. User taps Original Script button -> immediately restores Original Script
        composeRule.onNodeWithTag("lyrics_script_original_btn").performClick()
        composeRule.onNodeWithText("నువ్వే నా ప్రాణం", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("तुम ही हो", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(
            com.usman.musicplayer.model.LyricsScriptMode.ORIGINAL,
            com.usman.musicplayer.ui.components.LyricsScriptPreferences.getScriptMode(null)
        )
    }
}
